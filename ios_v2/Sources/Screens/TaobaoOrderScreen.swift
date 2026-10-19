import UIKit

/// 「淘宝订单详情」页 —— `features/order/TaobaoOrderActivity.java`（859 行）的 1:1 移植。
///
/// ## 这一页在 Java 里是什么形态
///
/// 它不是手绘页，而是纯系统控件（`FrameLayout` + `ScrollView` + `TextView` + `ImageView`，
/// Java:150-183）：**没有 `W`/`H` 常量、也没有 `canvas.scale`**。但它有一套自己的设计坐标系：
///
/// ```java
/// private static final float DESIGN = 1380f;                        // Java:65
/// k = getResources().getDisplayMetrics().widthPixels / DESIGN;      // Java:154
/// ```
///
/// 所有数字都是「参考图像素」（一张 1380 宽的整屏截图），`v(x) = round(x * k)`；
/// y 用的是**整屏绝对坐标**（0 就是屏幕最上沿，所以状态栏那 152 像素也在坐标系里）。
///
/// ## 参考画布怎么定（与同批「纯控件页」唯一的不同）
///
/// `CustomerServiceScreen` / `FeedbackScreen` 的尺寸全是 dp，所以按「1280 = 360dp」折算
/// （1dp = 1280/360）。**这一页不是**：它的可见版式全部是 `DESIGN = 1380` 的参考像素，
/// 一个 dp 也没有（dp 只出现在两个弹窗表单的内边距里，Java:792，本文件不画弹窗）。于是这里取
///
///     1 参考像素 = 1280 / 1380 ≈ 0.92754 画布单位
///
/// 这么取是**精确**的：参考图整屏 1380 × 3036（`SCREEN_BOTTOM`，Java:134），
/// 而 1280 × 2816 与它长宽比完全相同（3036/1380 = 2816/1280 = 2.2），
/// 所以整块版图只是等比缩到 1280 宽，没有任何一处被裁掉或拉伸。
///
/// 若日后要补那两个弹窗（收货人 / 推荐位），弹窗内部的 dp 值按 1280/360 折算即可 ——
/// 与 `CustomerServiceScreen`、`FeedbackScreen` 同一口径（本文件用不到，故没有 dp 换算）。
///
/// ## 滚动
///
/// Java 的滚动是外层 `ScrollView` 干的（Java:167-174），顶栏与底栏固定不动。
/// 所以这里 `selfScrolls = true`，`draw` 只把**内容区**做 `translate(0, -scroll)`，
/// 并裁到滚动区边框（Java 的 ScrollView 会裁，不裁的话内容会盖到顶栏上）；
/// `hitTest` 里顶栏要先把宿主加上的 `scroll` 减回去 —— 与 `CustomerServiceScreen` 同一套做法。
///
/// 注意 Java:174 那行 `rect(0, TOOLBAR_H, DESIGN, BOTTOM_BAR_TOP - TOOLBAR_H)` 的高度是
/// `2752 - 207 = 2545`，而滚动区的**绝对**范围是 359..2904 —— 也就是说它比可见区
/// （359..2752）多出 152（正好是一个状态栏）。这是原包的笔误，但底栏是不透明的、
/// 压在它上面，视觉上完全看不出来。这里**照抄**这个数值（滚动上限、裁剪框都用 2545），
/// 这样两端的滚动距离与 Android 一致。
///
/// ## 素材
///
/// Java 用了 13 张 `drawable-nodpi/order_*.png`，iOS 的 `Assets.xcassets` 里**目前一张都没有**
/// （见文件末尾列出的清单）。所以这里按**同名动态查找**（`art(_:)` 里的 `UIImage(named:)`，
/// 名字取自 Android 的 drawable 名，例如 `order_ic_back`）。素材一旦按同名加进 Assets 就自动生效；
/// 没有时画一个矢量近似（形状按原 PNG 的像素分布推出来，颜色取原图的不透明像素均色：
/// `order_ic_*` 基本是近黑的细笔画，`order_ic_coupon` 是淘宝红 #F00030，
/// `order_ic_service` 是橙 #D84000）。商品主图 / 六个推荐位图同理（`order_main`、`order_rec_1..6`）。
///
/// ## 动作令牌
///
/// `"back"` 顶栏返回（Java:335 `finish()`）；其余全部是 `order:` 前缀，一个一个对应
/// Java 里那一条 `setOnClickListener` / `tap(...)`（详见 `hitTest` 里的注释）。
/// 本文件只负责把「点了哪一项」报出去，弹窗 / 相册 / 剪贴板 / 落盘属于上层（Java:372-394、
/// 645-674、729-757、809-828 那些 `AlertDialog` 与 `ClipboardManager` 都不在这里）。
final class TaobaoOrderScreen: Screen {
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// Java 没有 W/H，画布按 1380×3036 的参考图等比缩到 1280×2816（见文件头）
    let canvasSize = CGSize(width: 1280, height: 2816)

    /// 顶栏 / 底栏固定，只有内容区自己 `translate(0, -scroll)`（见文件头「滚动」）
    let selfScrolls = true

    // MARK: - 设计坐标系（Java:65、154、221-230）

    /// Java:65 `DESIGN = 1380f` —— 参考图的宽度
    static let designWidth: CGFloat = 1380
    /// 参考像素 → 画布单位（见文件头：1280 / 1380，与 1380×3036 ↔ 1280×2816 同比例）
    static let unit: CGFloat = 1280.0 / 1380.0

    /// Java:221-223 `v(ref) = Math.round(ref * k)`（这里不取整，交给 CoreGraphics）
    private func px(_ ref: CGFloat) -> CGFloat { ref * Self.unit }

    /// Java:225-230 `rect(left, top, width, height)` 的矩形版本
    private func pxRect(_ left: CGFloat, _ top: CGFloat,
                        _ width: CGFloat, _ height: CGFloat) -> CGRect {
        CGRect(x: px(left), y: px(top), width: px(width), height: px(height))
    }

    // MARK: - 调色板（Java:68-78）

    private let pageBg = Draw.color(0xfff5f6f8)      // Java:68 PAGE_BG
    private let card = Draw.color(0xffffffff)        // Java:69 CARD
    private let ink = Draw.color(0xff000000)         // Java:70 INK
    private let gray = Draw.color(0xff727780)        // Java:71 GRAY
    private let green = Draw.color(0xff2f8f55)       // Java:72 GREEN
    private let orange = Draw.color(0xffff5000)      // Java:73 ORANGE
    private let orangeBtn = Draw.color(0xffff5e05)   // Java:74 ORANGE_BTN
    private let hairline = Draw.color(0xfffafafa)    // Java:75 HAIRLINE
    private let btnGray = Draw.color(0xfff1f2f4)     // Java:76 BTN_GRAY
    private let btnCream = Draw.color(0xffffebd3)    // Java:77 BTN_CREAM
    private let chipOrange = Draw.color(0xfffff0e0)  // Java:78 CHIP_ORANGE

    /// Java:709 底栏图标下面那行小字
    private let tabText = Draw.color(0xff939499)
    /// 原图里有、但这一版还没进 Assets 时，商品图 / 推荐位图的占位底色
    private let photoPlaceholder = Draw.color(0xfff0f1f3)
    /// 矢量兜底用的两个原图主色（取自 `order_ic_coupon` / `order_ic_service` 的不透明像素）
    private let couponRed = Draw.color(0xfff00030)
    private let serviceOrange = Draw.color(0xffd84000)

    // MARK: - 字号（Java:81-115，参考像素）

    private let fTitle: CGFloat = 73              // F_TITLE
    private let fSubtitle: CGFloat = 48           // F_SUBTITLE（副标题被产品去掉，Java:338 注释）
    private let fAddress: CGFloat = 56            // F_ADDRESS
    private let fReceiver: CGFloat = 43           // F_RECEIVER
    private let fShop: CGFloat = 56               // F_SHOP
    private let fShopNote: CGFloat = 44           // F_SHOP_NOTE
    private let fEnter: CGFloat = 48              // F_ENTER
    private let fGood: CGFloat = 45               // F_GOOD
    private let fGoodPrice: CGFloat = 48          // F_GOOD_PRICE
    private let fSpec: CGFloat = 44               // F_SPEC
    private let fRefund: CGFloat = 49             // F_REFUND
    private let fPaidLabel: CGFloat = 47          // F_PAID_LABEL
    private let fPaid: CGFloat = 58               // F_PAID
    private let fPriceDetail: CGFloat = 45        // F_PRICE_DETAIL
    private let fLabel: CGFloat = 56              // F_LABEL
    private let fLabelSmall: CGFloat = 47         // F_LABEL_SMALL
    private let fOrangeValue: CGFloat = 59        // F_ORANGE_VALUE
    private let fPayable: CGFloat = 72            // F_PAYABLE
    private let fSection: CGFloat = 55            // F_SECTION
    private let fSectionSub: CGFloat = 48         // F_SECTION_SUB
    private let fOrderNo: CGFloat = 49            // F_ORDER_NO
    private let fCopy: CGFloat = 48               // F_COPY
    private let fKey: CGFloat = 48                // F_KEY
    private let fValue: CGFloat = 52              // F_VALUE
    private let fTime: CGFloat = 42               // F_TIME（Java 里有定义，这一版没用到）
    private let fGuarantee: CGFloat = 49          // F_GUARANTEE
    private let fGuaranteeSub: CGFloat = 45       // F_GUARANTEE_SUB
    private let fBtn: CGFloat = 47                // F_BTN
    private let fService: CGFloat = 36            // F_SERVICE
    private let fRecTitle: CGFloat = 56           // F_REC_TITLE
    private let fRecPrice: CGFloat = 52           // F_REC_PRICE
    private let fRecTag: CGFloat = 44             // F_REC_TAG
    private let fRecSold: CGFloat = 42            // F_REC_SOLD
    /// Java:115 `REC_TEXT_INSET = 32f`
    private let recTextInset: CGFloat = 32

    // MARK: - 版式（Java:118-136，参考像素；y 是参考图的整屏绝对坐标）

    private let statusBar: CGFloat = 152                   // Java:118 STATUS_BAR
    private let pageLeft: CGFloat = 30                     // Java:119 PAGE_LEFT
    private let pageRight: CGFloat = 1350                  // Java:119 PAGE_RIGHT
    private let cardRadius: CGFloat = 14                   // Java:120 CARD_RADIUS
    private let toolbarH: CGFloat = 207                    // Java:121 TOOLBAR_H

    private let addressTop: CGFloat = 359                  // Java:123 ADDRESS_TOP
    private let addressBottom: CGFloat = 616               // Java:123 ADDRESS_BOTTOM
    private let shopTop: CGFloat = 648                     // Java:124 SHOP_TOP
    private let shopBottom: CGFloat = 869                  // Java:124 SHOP_BOTTOM
    private let goodsTop: CGFloat = 872                    // Java:125 GOODS_TOP
    private let goodsBottom: CGFloat = 1541                // Java:125 GOODS_BOTTOM
    private let priceTop: CGFloat = 1544                   // Java:126 PRICE_TOP
    private let priceBottom: CGFloat = 1859                // Java:126 PRICE_BOTTOM
    private let orderTop: CGFloat = 1890                   // Java:127 ORDER_TOP
    private let orderBottom: CGFloat = 2818                // Java:127 ORDER_BOTTOM
    private let recTop: CGFloat = 2884                     // Java:128 REC_TOP
    private let recSize: CGFloat = 650                     // Java:129 REC_SIZE
    private let recPitch: CGFloat = 945                    // Java:130 REC_PITCH
    private let recMargin: CGFloat = 29                    // Java:131 REC_MARGIN
    private let recGutter: CGFloat = 22                    // Java:132 REC_GUTTER
    private let bottomBarTop: CGFloat = 2752               // Java:133 BOTTOM_BAR_TOP
    private let screenBottom: CGFloat = 3036               // Java:134 SCREEN_BOTTOM
    /// Java:135 `PAGE_BOTTOM = REC_TOP + 3 * REC_PITCH + 200` = 5919
    private var pageBottom: CGFloat { recTop + 3 * recPitch + 200 }

    // MARK: - 基线换算（Java 一行都没有数字，全是 TextView 自己排的）

    /// Java `text()` / `textRight()`（Java:279-299）的落位：行框顶放在 `center - 0.5×字号`
    /// （`setIncludeFontPadding(false)`，所以行框高度 ≈ 字号）。
    /// Android CJK 的 ascent ≈ 0.93×字号 → 基线 = `center + 0.43×字号`。
    ///
    /// 这条换算的出处是 Java:54-55 自己写的那段说明：「墨迹中心约在 top + 0.5×字号」，
    /// 反过来就是「基线 = 墨迹中心 + 0.43×字号」。
    private func inkBaseline(center: CGFloat, size: CGFloat) -> CGFloat { center + size * 0.43 }

    /// `Gravity.CENTER` / `CENTER_VERTICAL`（Java:340、465、711、722）的落位：
    /// 行框（≈1.2×字号）在框里居中 → 基线 = `中心 + 0.33×字号`。
    /// 与 `FeedbackScreen` 的 `baselineOffset = 0.93×字号` / `lineHeight = 1.2×字号` 同一套 metrics。
    private func centeredBaseline(centerY: CGFloat, size: CGFloat) -> CGFloat {
        centerY + size * 0.33
    }

    /// `orderLine()` 右侧那个值（Java:580-588）：`topMargin = valueTop - 0.05×字号`，
    /// 之后是 `setIncludeFontPadding(false)` 的首行 → 基线 = `valueTop + 0.88×字号`。
    private func valueBaseline(top: CGFloat, size: CGFloat) -> CGFloat { top + size * 0.88 }

    /// 折行后的行距：Android 单倍行距 ≈ 1.2×字号（同反馈页的行距系数）
    private func lineAdvance(_ size: CGFloat) -> CGFloat { size * 1.2 }

    // MARK: - 状态

    /// 页面状态 —— 对应 Java 里 `OrderStore` 那一套字段（`OrderStore.java:30-69`）。
    ///
    /// Java 的默认值写在 `OrderStore.defaults()`（店铺 / 商品 / 金额 / 推荐位，不是隐私）
    /// 与 `randomize()`（收货人 / 手机号 / 地址 / 订单号 / 交易号 / 时间，每次随机）；
    /// 这里把前者照抄成默认值，后者给一套**明显是假**的样例，等上层从存档里填。
    final class State: ScreenState {
        /// Java `OrderStore.REC_COUNT = 6`（推荐位 6 个，2 列 × 3 行）
        static let recCount = 6

        /// Java:30 `TITLE`（默认「交易成功」）
        var title = "交易成功"

        /// Java:33 `ADDRESS` —— 顶部卡片上的短地址
        var address = "申汉大厦c座1401"
        /// Java:34 `NAME` —— 收货人
        var name = "何柏玮"
        /// Java:35 `PHONE` —— 11 位，不带 86-
        var phone = "15972450520"
        /// Java:36 `FULL_ADDRESS` —— 订单信息里那一整行地址
        var fullAddress = "上海 上海市 普陀区 长征镇 申汉大厦c1401"

        /// Java:39 `SHOP`
        var shop = "Apple Store 官方旗舰店"
        /// 用户自选的店铺图标；nil 时用内置矢量/素材。
        var shopLogo: UIImage?
        /// Java:40 `SHOP_NOTE`
        var shopNote = "88VIP好评率98%，平均2天退款"

        /// Java:43 `GOOD_TITLE`
        var goodsTitle = "【优惠价】 Apple/苹果 iPhone..."
        /// Java:44 `GOOD_SPEC`
        var goodsSpec = "星宇橙色;256GB"
        /// Java:45 `GOOD_PRICE`（商品原价）
        var goodPrice = "9999"
        /// Java:46 `GOOD_COUNT`
        var goodCount = "1"
        /// Java:47 `PAID_PRICE`（实付价）
        var paidPrice = "9699"
        /// Java:48 `REFUND_NOTE`
        var refundNote = "极速退款 7天无理由退换"

        /// Java:51 `TOTAL_PRICE`
        var totalPrice = "9999"
        /// Java:52 `DISCOUNT`
        var discount = "300"
        /// Java:53 `PAYABLE`
        var payable = "9699"

        /// Java:56 `ORDER_NO`（19 位）
        var orderNo = "1946837102524156907"
        /// Java:57 `INVOICE`
        var invoice = "个人"
        /// Java:58 `TRADE_NO`（28 位：8 位日期 + 2200 + 16 位）
        var tradeNo = "2026101722001234567890123456"
        /// Java:59 `CREATE_TIME`
        var createTime = "2026-10-17 10:24:03"
        /// Java:60 `DEAL_TIME`
        var dealTime = "2026-10-17 10:31:07"
        /// Java:61 `GUARANTEE`
        var guarantee = "依据：2026-10-17下单交易快照"
        /// Java:62 `GUARANTEE_COUNT`
        var guaranteeCount = "2"

        /// 订单信息那一块当前是展开的（Java:142 `orderExpanded = true`，参考图上就是展开、箭头朝上）。
        /// 上层的 `order:toggle` 分支要做的事就是把它翻转再重绘（Java:521-524 是在就地 `render()`）。
        var orderExpanded = true

        /// 六个推荐位（Java:64-69 的 `REC_TITLE/REC_PRICE/REC_TAG/REC_SOLD`，共 6 组）
        var recs: [Rec] = State.recDefaults

        /// Java:803-807 `customImage()`：用户自己换的商品图（存盘 / 相册那套属于上层）。
        /// 非 nil 时盖掉 `order_main`；`order:pickImage` 由上层弹「从相册选择 / 恢复参考图」。
        var customGoodsImage: UIImage?

        /// 一个推荐位的四行字
        struct Rec {
            var title = ""
            var price = ""
            var tag = ""
            var sold = ""

            init(title: String = "", price: String = "", tag: String = "", sold: String = "") {
                self.title = title
                self.price = price
                self.tag = tag
                self.sold = sold
            }
        }

        /// `OrderStore.java:77-84` 的 `REC_DEFAULTS`（照抄）
        static let recDefaults: [Rec] = [
            Rec(title: "枪神ROG 枪神8 Plus超", price: "7399", tag: "券后价", sold: "已售40+"),
            Rec(title: "巧克力脆脆杯饼干脆皮", price: "11.8", tag: "首单价", sold: "已售1万+"),
            Rec(title: "爱兰纯一水肌酸粉300g", price: "18.9", tag: "补贴价", sold: "全网热销6万+"),
            Rec(title: "ChirpyKnight男士内裤", price: "40", tag: "补贴价", sold: "已售3万+"),
            Rec(title: "欧路得美式baggy丹宁", price: "74.3", tag: "直降价", sold: "已售1万+"),
            Rec(title: "【预售开始-立即付】", price: "598", tag: "券后价", sold: "已售5000+"),
        ]

        init() {
            // 隐私信息不使用真实姓名/手机号/地址，每次进入订单详情都随机一套。
            name = Self.randomName()
            phone = Self.randomPhone()
            let area = Self.randomAddress()
            address = area.short
            fullAddress = area.full
            shop = Self.randomShop()
            invoice = Bool.random() ? "个人" : "公司"

            let created = Self.randomDate()
            let dealt = created.addingTimeInterval(TimeInterval(Int.random(in: 180...2400)))
            let fmt = DateFormatter()
            fmt.locale = Locale(identifier: "en_US_POSIX")
            fmt.dateFormat = "yyyy-MM-dd HH:mm:ss"
            createTime = fmt.string(from: created)
            dealTime = fmt.string(from: dealt)
            guarantee = "依据：" + String(createTime.prefix(10)) + "下单交易快照"

            var order = ""
            for _ in 0..<19 { order.append(String(Int.random(in: 0...9))) }
            orderNo = order
            var trade = String(createTime.prefix(10)).replacingOccurrences(of: "-", with: "")
            trade += "2200"
            for _ in 0..<16 { trade.append(String(Int.random(in: 0...9))) }
            tradeNo = trade
            super.init()
        }

        private static func randomName() -> String {
            let surnames = ["张", "李", "王", "刘", "陈", "杨", "赵", "周", "吴", "徐"]
            let gives = ["伟", "娜", "芳", "洋", "晨", "磊", "敏", "昊", "静", "强"]
            return (surnames.randomElement() ?? "张") + (gives.randomElement() ?? "伟")
        }

        private static func randomPhone() -> String {
            let prefixes = ["138", "139", "150", "151", "158", "159", "186", "187", "188", "189"]
            var value = prefixes.randomElement() ?? "138"
            for _ in 0..<8 { value.append(String(Int.random(in: 0...9))) }
            return value
        }

        private static func randomAddress() -> (short: String, full: String) {
            let cities: [(String, String)] = [
                ("上海市", "浦东新区"), ("上海市", "普陀区"), ("北京市", "朝阳区"),
                ("广州市", "天河区"), ("深圳市", "南山区"), ("杭州市", "西湖区"),
            ]
            let city = cities.randomElement() ?? ("上海市", "浦东新区")
            let roads = ["世纪大道", "建国路", "体育西路", "科技园路", "文三路", "中山北路"]
            let road = roads.randomElement() ?? "世纪大道"
            let number = Int.random(in: 8...888)
            let building = ["号", "号A座", "号B座", "号C座"][Int.random(in: 0...3)]
            let room = "\(Int.random(in: 1...30))0\(Int.random(in: 1...9))"
            let province = city.0.replacingOccurrences(of: "市", with: "")
            return ("\(road)\(number)\(building)\(room)",
                    "\(province) \(city.0) \(city.1) \(road)\(number)\(building)\(room)")
        }

        private static func randomShop() -> String {
            let names = ["优品数码旗舰店", "星辰优选旗舰店", "果粉之家官方店",
                         "潮玩生活旗舰店", "元气好物官方店", "极客数码专营店"]
            return names.randomElement() ?? "优品数码旗舰店"
        }

        private static func randomDate() -> Date {
            let now = Date()
            let days = Double(Int.random(in: 1...20)) * 24 * 60 * 60
            let seconds = Double(Int.random(in: 0...86399))
            return now.addingTimeInterval(-days - seconds)
        }

        /// Java:171-181 `maskedPhone()`：「86-159＊＊＊＊0520」。
        ///
        /// 那四个星号在 Java 里是**全角** `\uFF0A`（Java:178-179 的注释：半角星号排出来短一截，
        /// 这一行就和参考图对不上），这里照抄全角。
        var maskedPhone: String {
            guard phone.count >= 7 else { return "86-" + phone }
            return "86-" + String(phone.prefix(3)) + "＊＊＊＊" + String(phone.suffix(4))
        }

        /// Java:367-368 顶部卡片第二行：`NAME + " " + maskedPhone()`
        var receiver: String { name + " " + maskedPhone }

        /// Java:184-186 `receiverLine()`：`NAME，86-PHONE，FULL_ADDRESS`
        var receiverLine: String { name + "，86-" + phone + "，" + fullAddress }

        /// 取第 index 个推荐位（越界给空的一条，绝不 trap）
        func rec(at index: Int) -> Rec {
            guard index >= 0, index < recs.count else { return Rec() }
            return recs[index]
        }
    }

    // MARK: - 素材（见文件头「素材」）

    /// 与 Android drawable 同名的动态查找：素材补进 Assets 后自动生效
    private func art(_ name: String) -> UIImage? { UIImage(named: name) }

    /// 画一张图标：有素材就用素材（Java 的 `ImageView.ScaleType.FIT_XY` 就是拉伸铺满），
    /// 没有就画矢量近似。
    private func drawIcon(_ d: Draw, _ name: String, _ box: CGRect) {
        if let image = art(name) {
            d.art(image, box)          // Java:310-316 `icon()`：FIT_XY
            return
        }
        fallbackIcon(d, name, box)
    }

    /// 素材缺席时的矢量近似。
    ///
    /// 形状是按原 PNG 的 alpha 分布量出来的（`order_ic_*` 都是细笔画 / 粗笔画，
    /// `order_ic_coupon` 是淘宝红的票券，`order_ic_service` 是橙色的圆环，
    /// `order_shop_logo` 是一整块近黑的方图），颜色取原图不透明像素的均色。
    /// 这些只是**过渡**：真正的图一旦按同名进 Assets 就不再走这里。
    private func fallbackIcon(_ d: Draw, _ name: String, _ box: CGRect) {
        switch name {
        case "order_ic_back":
            chevronStroke(d, box, pointsRight: false, width: box.width * 0.28, color: ink)
        case "order_ic_arrow_r":
            chevronStroke(d, box, pointsRight: true, width: box.width * 0.16, color: ink)
        case "order_ic_caret_down":
            caretStroke(d, box, pointsUp: false, width: box.height * 0.17, color: ink)
        case "order_ic_caret_up":
            caretStroke(d, box, pointsUp: true, width: box.height * 0.17, color: ink)
        case "order_ic_dots":
            // 80×24 里三个实心圆点，直径约 14
            let r = box.height * 0.29
            for factor in [0.13, 0.5, 0.87] as [CGFloat] {
                d.circle(cx: box.minX + box.width * factor, cy: box.midY, r: r, ink)
            }
        case "order_ic_pin":
            drawPinIcon(d, box)
        case "order_ic_info":
            drawInfoIcon(d, box)
        case "order_ic_coupon":
            // 83×70、圆角、淘宝红底 + 白色 ¥
            d.round(box, radius: box.height * 0.12, couponRed)
            d.centerText("¥", cx: box.midX, baseline: box.midY + box.height * 0.21,
                         size: box.height * 0.6, card, bold: true)
        case "order_ic_service":
            // 橙色圆环 + 里面两个小竖条（原图就是这副样子，70×70）
            d.ctx.setStrokeColor(serviceOrange.cgColor)
            d.ctx.setLineWidth(box.width * 0.13)
            d.ctx.strokeEllipse(in: box.insetBy(dx: box.width * 0.08, dy: box.height * 0.08))
            let barW = box.width * 0.13
            for factor in [0.38, 0.62] as [CGFloat] {
                let cx = box.minX + box.width * factor
                d.round(CGRect(x: cx - barW / 2, y: box.midY - box.height * 0.13,
                               width: barW, height: box.height * 0.26),
                        radius: barW / 2, serviceOrange)
            }
        case "order_ic_more":
            // 黑色圆环 + 里面三个点（65×64）
            d.ctx.setStrokeColor(ink.cgColor)
            d.ctx.setLineWidth(box.width * 0.075)
            d.ctx.strokeEllipse(in: box.insetBy(dx: box.width * 0.07, dy: box.height * 0.07))
            let dot = box.width * 0.055
            for factor in [0.32, 0.5, 0.68] as [CGFloat] {
                d.circle(cx: box.minX + box.width * factor, cy: box.midY, r: dot, ink)
            }
        case "order_shop_logo":
            // 原图是一整块近黑的方图（130×130 全不透明），中间挖掉一块
            d.round(box, radius: box.width * 0.06, ink)
            d.round(CGRect(x: box.minX + box.width * 0.30, y: box.minY + box.height * 0.30,
                           width: box.width * 0.44, height: box.height * 0.45),
                    radius: box.width * 0.05, card)
        default:
            // 认不出的图标（例如日后新增的）：画一个中性的灰块，不猜形状
            d.rect(box, photoPlaceholder)
        }
    }

    /// 定位针（`order_ic_pin`，57×61 的近黑水滴 + 中间镂空）
    private func drawPinIcon(_ d: Draw, _ box: CGRect) {
        let r = box.width / 2
        let cy = box.minY + r
        d.circle(cx: box.midX, cy: cy, r: r, ink)
        d.path(points: [
            (box.minX + r * 0.30, cy + r * 0.55),
            (box.midX, box.maxY),
            (box.maxX - r * 0.30, cy + r * 0.55),
        ], fill: ink)
        d.circle(cx: box.midX, cy: cy, r: r * 0.52, card)
        d.circle(cx: box.midX, cy: cy, r: r * 0.18, ink)
    }

    /// 信息图标（`order_ic_info`，51×34 的椭圆环 + 中间一个 i）
    private func drawInfoIcon(_ d: Draw, _ box: CGRect) {
        d.ctx.setStrokeColor(ink.cgColor)
        d.ctx.setLineWidth(box.height * 0.13)
        d.ctx.strokeEllipse(in: box.insetBy(dx: box.height * 0.10, dy: box.height * 0.10))
        d.circle(cx: box.midX, cy: box.minY + box.height * 0.32, r: box.height * 0.085, ink)
        d.round(CGRect(x: box.midX - box.height * 0.055, y: box.minY + box.height * 0.44,
                       width: box.height * 0.11, height: box.height * 0.30),
                radius: box.height * 0.055, ink)
    }

    /// 粗圆头 `<` / `>` 折线（返回箭头、右箭头共用）
    private func chevronStroke(_ d: Draw, _ box: CGRect, pointsRight: Bool,
                               width: CGFloat, color: UIColor) {
        let inset = width / 2
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        if pointsRight {
            d.ctx.move(to: CGPoint(x: box.minX + inset, y: box.minY + inset))
            d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.midY))
            d.ctx.addLine(to: CGPoint(x: box.minX + inset, y: box.maxY - inset))
        } else {
            d.ctx.move(to: CGPoint(x: box.maxX - inset, y: box.minY + inset))
            d.ctx.addLine(to: CGPoint(x: box.minX + inset, y: box.midY))
            d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.maxY - inset))
        }
        d.ctx.strokePath()
    }

    /// 细圆头 `⌄` / `⌃`（两个 caret 图标）
    private func caretStroke(_ d: Draw, _ box: CGRect, pointsUp: Bool,
                             width: CGFloat, color: UIColor) {
        let inset = width / 2
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        if pointsUp {
            d.ctx.move(to: CGPoint(x: box.minX + inset, y: box.maxY - inset))
            d.ctx.addLine(to: CGPoint(x: box.midX, y: box.minY + inset))
            d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.maxY - inset))
        } else {
            d.ctx.move(to: CGPoint(x: box.minX + inset, y: box.minY + inset))
            d.ctx.addLine(to: CGPoint(x: box.midX, y: box.maxY - inset))
            d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.minY + inset))
        }
        d.ctx.strokePath()
    }

    // MARK: - 滚动（Java:167-174 的 ScrollView）

    func maxScroll(_ state: ScreenState) -> CGFloat {
        // Java:250-251：page 的高度是 `PAGE_BOTTOM - ADDRESS_TOP`；
        // Java:174：滚动区高度写的是 `BOTTOM_BAR_TOP - TOOLBAR_H`（含那个 152 的笔误，见文件头）。
        let pageHeight = pageBottom - addressTop
        let viewport = bottomBarTop - toolbarH
        return max(0, px(pageHeight - viewport))
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()

        // Java:157 `window.setBackgroundDrawable(new ColorDrawable(PAGE_BG))`、
        // Java:179 `root.setBackgroundColor(PAGE_BG)`
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // ── 滚动区（Java:167-174）：只有这一块跟着手指滚，并裁到滚动区边框
        d.ctx.saveGState()
        d.ctx.clip(to: pxRect(0, addressTop, Self.designWidth, bottomBarTop - toolbarH))
        d.ctx.translateBy(x: 0, y: -s.scroll)
        drawContent(d, s)
        d.ctx.restoreGState()

        // ── 顶栏与底栏固定（Java:165、175-176 把它们和 scroller 并列加在 screen 上）
        drawToolbar(d, s)
        drawBottomBar(d)
    }

    /// Java:248-258 `render()` 的顺序：地址卡 → 店铺行 → 商品行 → 金额 → 订单卡 → 推荐位
    private func drawContent(_ d: Draw, _ s: State) {
        drawAddressCard(d, s)
        drawShopRow(d, s)
        drawGoodsRow(d, s)
        drawPriceBlock(d, s)
        drawOrderCard(d, s)
        drawRecommendation(d, s)
    }

    // MARK: - 顶部标题栏（Java:326-355，参考图 y 152..359）

    private func drawToolbar(_ d: Draw, _ s: State) {
        // Java:330 `bar.setBackgroundColor(PAGE_BG)` —— 补一层，免得滚动内容透上来
        d.rect(pxRect(0, statusBar, Self.designWidth, toolbarH), pageBg)

        // Java:332-336 返回（`rect(53, 74, 38, 62)` 在 bar 内，bar 的绝对顶是 152）
        drawIcon(d, "order_ic_back", pxRect(53, statusBar + 74, 38, 62))

        // Java:339-347 标题：CENTER、左右各 24 的内边距、topMargin 59、高度 1.4×字号
        let titleBoxTop = statusBar + 59
        let titleCenterY = titleBoxTop + fTitle * 0.7
        d.centerText(s.title, cx: px(Self.designWidth / 2),
                     baseline: px(centeredBaseline(centerY: titleCenterY, size: fTitle)),
                     size: px(fTitle), ink, bold: true)

        // Java:350-353 右上角三个点：参考图上就是装饰，不接点击
        drawIcon(d, "order_ic_dots", pxRect(1251, statusBar + 94, 80, 24))
    }

    // MARK: - 地址卡（Java:357-370，参考图 y 359..616）

    private func drawAddressCard(_ d: Draw, _ s: State) {
        // Java:360-361 白卡
        d.round(pxRect(pageLeft, addressTop, pageRight - pageLeft, addressBottom - addressTop),
                radius: px(cardRadius), card)

        // Java:362 定位针
        drawIcon(d, "order_ic_pin", pxRect(74, 423, 57, 61))

        // Java:364 短地址（左 164、墨迹中心 454、56 号、粗体）
        d.text(s.address, x: px(164), baseline: px(inkBaseline(center: 454, size: fAddress)),
               size: px(fAddress), ink, bold: true)

        // Java:367-368 收货人 + 打码手机号（左 161、墨迹中心 538、43 号、灰）
        d.text(s.receiver, x: px(161), baseline: px(inkBaseline(center: 538, size: fReceiver)),
               size: px(fReceiver), gray)
    }

    // MARK: - 店铺行（Java:396-411，参考图 y 648..869）

    private func drawShopRow(_ d: Draw, _ s: State) {
        // Java:399 白底（radius 0 → 直角）
        d.rect(pxRect(pageLeft, shopTop, pageRight - pageLeft, shopBottom - shopTop), card)

        // Java:400 店铺 logo（130×130）
        let logoRect = pxRect(56, 697, 130, 130)
        if let logo = s.shopLogo {
            d.clipped(to: logoRect, radius: 10) { d.art(logo, logoRect) }
        } else {
            drawIcon(d, "order_shop_logo", logoRect)
        }

        // Java:402 店铺名
        d.text(s.shop, x: px(222), baseline: px(inkBaseline(center: 730, size: fShop)),
               size: px(fShop), ink, bold: true)

        // Java:405 店铺说明
        d.text(s.shopNote, x: px(223), baseline: px(inkBaseline(center: 805, size: fShopNote)),
               size: px(fShopNote), gray)

        // Java:408 「进店逛逛」（Java 里没有给它挂点击）
        d.text("进店逛逛", x: px(1085), baseline: px(inkBaseline(center: 762, size: fEnter)),
               size: px(fEnter), gray)

        // Java:409 右箭头
        drawIcon(d, "order_ic_arrow_r", pxRect(1286, 743, 27, 39))

        // Java:410 卡片底部那条分割线（`PAGE_LEFT+30` 起、宽 1260、高 2）
        d.rect(pxRect(pageLeft + 30, 870, pageRight - pageLeft - 60, 2), hairline)
    }

    // MARK: - 商品行（Java:413-477，参考图 y 872..1541）

    private func drawGoodsRow(_ d: Draw, _ s: State) {
        // Java:416 白底
        d.rect(pxRect(pageLeft, goodsTop, pageRight - pageLeft, goodsBottom - goodsTop), card)

        // Java:418-423 商品图（`placeAt(photo, 61, 917, 292, 296)`，CENTER_CROP）
        let photo = pxRect(61, 917, 292, 296)
        if let image = s.customGoodsImage ?? art("order_main") {
            d.clipped(to: photo, radius: 0) { d.artFill(image, in: photo) }
        } else {
            d.rect(photo, photoPlaceholder)
        }

        // Java:425-428 商品标题：单行 + 尾部省略 + 最大宽 665
        let title = ellipsize(s.goodsTitle, size: px(fGood), bold: true, maxWidth: px(665))
        d.text(title, x: px(429), baseline: px(inkBaseline(center: 945, size: fGood)),
               size: px(fGood), ink, bold: true)

        // Java:431 标题右侧的小向下箭头
        drawIcon(d, "order_ic_caret_down", pxRect(1114, 931, 40, 24))

        // Java:433-434 商品原价（右对齐到 1317）
        d.rightText("¥" + s.goodPrice, right: px(1317),
                    baseline: px(inkBaseline(center: 949, size: fGoodPrice)),
                    size: px(fGoodPrice), ink, bold: true)

        // Java:437 规格
        d.text(s.goodsSpec, x: px(401), baseline: px(inkBaseline(center: 1017, size: fSpec)),
               size: px(fSpec), gray)

        // Java:440-441 数量（右对齐到 1313）
        d.rightText("x" + s.goodCount, right: px(1313),
                    baseline: px(inkBaseline(center: 1016, size: fSpec)),
                    size: px(fSpec), gray)

        // Java:444-445 退款说明（绿）
        d.text(s.refundNote, x: px(400), baseline: px(inkBaseline(center: 1080, size: fRefund)),
               size: px(fRefund), green)

        // Java:447 退款说明后面那个小箭头
        drawIcon(d, "order_ic_arrow_r", pxRect(931, 1062, 22, 32))

        // Java:449-462 实付价 / 价格明细 那一行
        drawPaidRow(d, s)

        // Java:464-468 「加入购物车」（奶白底、橙字、10 圆角、295×112）
        let cart = pxRect(1026, 1232, 295, 112)
        d.round(cart, radius: px(10), btnCream)
        d.centerText("加入购物车", cx: cart.midX,
                     baseline: px(centeredBaseline(centerY: 1232 + 56, size: fBtn)),
                     size: px(fBtn), orange)

        // Java:470-471 商品总价
        d.text("商品总价", x: px(62), baseline: px(inkBaseline(center: 1459, size: fLabel)),
               size: px(fLabel), ink)

        // Java:472-473 商品总价的金额（右对齐到 1317）
        d.rightText("¥" + s.totalPrice, right: px(1317),
                    baseline: px(inkBaseline(center: 1464, size: fLabel)),
                    size: px(fLabel), ink, bold: true)

        // Java:476 卡片底部那条分割线
        d.rect(pxRect(pageLeft + 30, 1542, pageRight - pageLeft - 60, 2), hairline)
    }

    /// Java:449-462 的横向流：`实付价` + `¥9699` + `价格明细` + `›`，
    /// `placeAt(paidRow, 400, 1180 - 40, 700, 80)`，`gravity = CENTER_VERTICAL`。
    ///
    /// 四个子项的间距是 `flowParams(0)` / `(4)` / `(18)`（Java:453-461），宽度都按文字量出来，
    /// 所以这里必须自己算宽度 —— 画与命中用的是同一份结果。
    private func paidRow(_ s: State) -> (label: CGRect, value: CGRect, detail: CGRect, arrow: CGRect) {
        let centerY = CGFloat(1180)
        var x = px(400)

        let labelW = Draw.width("实付价", size: px(fPaidLabel))
        let label = centeredBox(x: x, centerY: centerY, width: labelW, size: fPaidLabel)
        x = label.maxX + px(4)

        let valueW = Draw.width("¥" + s.paidPrice, size: px(fPaid), bold: true)
        let value = centeredBox(x: x, centerY: centerY, width: valueW, size: fPaid)
        x = value.maxX + px(18)

        let detailW = Draw.width("价格明细", size: px(fPriceDetail))
        let detail = centeredBox(x: x, centerY: centerY, width: detailW, size: fPriceDetail)
        x = detail.maxX

        let arrow = CGRect(x: x, y: px(centerY) - px(32) / 2, width: px(22), height: px(32))
        return (label, value, detail, arrow)
    }

    private func drawPaidRow(_ d: Draw, _ s: State) {
        let row = paidRow(s)
        // Java:453 「实付价」
        d.text("实付价", x: row.label.minX,
               baseline: px(centeredBaseline(centerY: 1180, size: fPaidLabel)),
               size: px(fPaidLabel), ink)
        // Java:454-456 实付金额
        d.text("¥" + s.paidPrice, x: row.value.minX,
               baseline: px(centeredBaseline(centerY: 1180, size: fPaid)),
               size: px(fPaid), ink, bold: true)
        // Java:457 「价格明细」
        d.text("价格明细", x: row.detail.minX,
               baseline: px(centeredBaseline(centerY: 1180, size: fPriceDetail)),
               size: px(fPriceDetail), gray)
        // Java:458-461 那个小箭头
        drawIcon(d, "order_ic_arrow_r", row.arrow)
    }

    // MARK: - 金额（Java:479-504，参考图 y 1544..1859）

    private func drawPriceBlock(_ d: Draw, _ s: State) {
        // Java:482 白底
        d.rect(pxRect(pageLeft, priceTop, pageRight - pageLeft, priceBottom - priceTop), card)

        // Java:483 券图标（83×70）
        drawIcon(d, "order_ic_coupon", pxRect(46, 1589, 83, 70))

        // Java:485 「平台优惠」
        d.text("平台优惠", x: px(143),
               baseline: px(inkBaseline(center: 1626, size: fLabelSmall)),
               size: px(fLabelSmall), ink)

        // Java:487 「购物券」
        d.text("购物券", x: px(352),
               baseline: px(inkBaseline(center: 1625, size: fLabelSmall)),
               size: px(fLabelSmall), ink)

        // Java:489-490 优惠金额（橙、粗、右对齐到 1268）
        d.rightText("-¥" + s.discount, right: px(1268),
                    baseline: px(inkBaseline(center: 1628, size: fOrangeValue)),
                    size: px(fOrangeValue), orange, bold: true)

        // Java:492 右箭头
        drawIcon(d, "order_ic_arrow_r", pxRect(1286, 1612, 22, 32))

        // Java:494 「应付款」
        d.text("应付款", x: px(62), baseline: px(inkBaseline(center: 1781, size: fLabel)),
               size: px(fLabel), ink, bold: true)

        // Java:496-497 「共减¥300」（橙）
        d.text("共减¥" + s.discount, x: px(253),
               baseline: px(inkBaseline(center: 1788, size: fLabel)),
               size: px(fLabel), orange)

        // Java:500-501 应付款金额（72 号、右对齐到 1261）
        d.rightText("¥" + s.payable, right: px(1261),
                    baseline: px(inkBaseline(center: 1775, size: fPayable)),
                    size: px(fPayable), ink, bold: true)

        // Java:503 收/展开那个小箭头（金额旁边，朝上）
        drawIcon(d, "order_ic_caret_up", pxRect(1279, 1764, 41, 26))
    }

    // MARK: - 订单信息卡（Java:506-589，参考图 y 1890..2818）

    private func drawOrderCard(_ d: Draw, _ s: State) {
        // Java:509-510 白卡
        d.round(pxRect(pageLeft, orderTop, pageRight - pageLeft, orderBottom - orderTop),
                radius: px(cardRadius), card)

        // Java:512 「订单信息」（不可点）
        d.text("订单信息", x: px(63), baseline: px(inkBaseline(center: 1962, size: fSection)),
               size: px(fSection), ink, bold: true)

        // Java:514 「共5项」（不可点）
        d.text("共5项", x: px(293), baseline: px(inkBaseline(center: 1961, size: fSectionSub)),
               size: px(fSectionSub), gray)

        // Java:517-518 展开时朝上、收起时朝下（并且 y 差 2）
        if s.orderExpanded {
            drawIcon(d, "order_ic_caret_up", pxRect(432, 1956, 33, 24))
        } else {
            drawIcon(d, "order_ic_caret_down", pxRect(432, 1958, 33, 24))
        }

        // Java:527-528 订单号
        d.text(s.orderNo, x: px(665), baseline: px(inkBaseline(center: 1963, size: fOrderNo)),
               size: px(fOrderNo), gray)

        // Java:531 「复制」
        d.text("复制", x: px(1228), baseline: px(inkBaseline(center: 1963, size: fCopy)),
               size: px(fCopy), ink)

        // Java:541-553 展开时的五行（收货信息 / 发票 / 支付宝交易号 / 创建时间 / 成交时间）
        if s.orderExpanded {
            drawOrderLine(d, "收货信息", s.receiverLine, left: 63,
                          keyCenter: 2105, valueTop: 2061, font: fValue)
            // Java:543 收货信息右侧那个小信息标
            drawIcon(d, "order_ic_info", pxRect(261, 2089, 51, 34))
            drawOrderLine(d, "发票", s.invoice, left: 62,
                          keyCenter: 2261, valueTop: 2261, font: fKey)
            drawOrderLine(d, "支付宝交易号", s.tradeNo, left: 62,
                          keyCenter: 2381, valueTop: 2381, font: fKey)
            drawOrderLine(d, "创建时间", s.createTime, left: 62,
                          keyCenter: 2500, valueTop: 2501, font: fKey)
            drawOrderLine(d, "成交时间", s.dealTime, left: 62,
                          keyCenter: 2620, valueTop: 2620, font: fKey)

            // Java:552 分割线
            d.rect(pxRect(pageLeft + 30, 2693, pageRight - pageLeft - 60, 2), hairline)
        }

        // Java:555-556 「订单保障」（不可点）
        d.text("订单保障", x: px(61),
               baseline: px(inkBaseline(center: 2762, size: fGuarantee)),
               size: px(fGuarantee), ink, bold: true)

        // Java:557-562 「共2项」橙底小胶囊（`placeAt(chip, 268, 2730, 116, 64)`、6 圆角）
        let chip = pxRect(268, 2730, 116, 64)
        d.round(chip, radius: px(6), chipOrange)
        d.centerText("共" + s.guaranteeCount + "项", cx: chip.midX,
                     baseline: px(centeredBaseline(centerY: 2762, size: fGuaranteeSub)),
                     size: px(fGuaranteeSub), orange)

        // Java:563-564 保障依据
        d.text(s.guarantee, x: px(489),
               baseline: px(inkBaseline(center: 2762, size: fGuaranteeSub)),
               size: px(fGuaranteeSub), gray)

        // Java:566 右箭头
        drawIcon(d, "order_ic_arrow_r", pxRect(1286, 2746, 22, 32))
    }

    /// Java:573-589 `orderLine()`：左边灰标签（墨迹中心 `keyCenter`），
    /// 右边深色值（右对齐到 1321、可换行，`topMargin = valueTop - 0.05×字号`）。
    private func drawOrderLine(_ d: Draw, _ name: String, _ value: String, left: CGFloat,
                               keyCenter: CGFloat, valueTop: CGFloat, font: CGFloat) {
        // Java:575 标签（固定 48 号）
        d.text(name, x: px(left), baseline: px(inkBaseline(center: keyCenter, size: fKey)),
               size: px(fKey), ink)

        // Java:580-588 值：宽度固定 860、右对齐、WRAP_CONTENT 高度
        let lines = wrap(value, size: px(font), maxWidth: px(860))
        let first = valueBaseline(top: valueTop, size: font)
        for (i, line) in lines.enumerated() {
            d.rightText(line, right: px(1321),
                        baseline: px(first + CGFloat(i) * lineAdvance(font)),
                        size: px(font), gray)
        }
    }

    // MARK: - 推荐位（Java:591-643）

    private func drawRecommendation(_ d: Draw, _ s: State) {
        // Java:593-601：2 列 × 3 行，行距 945、列距 650+22
        for rowIndex in 0..<(State.recCount / 2) {
            let top = recTop + CGFloat(rowIndex) * recPitch
            for column in 0..<2 {
                let left = recMargin + CGFloat(column) * (recSize + recGutter)
                drawRecommendItem(d, s, index: rowIndex * 2 + column, left: left, top: top)
            }
        }
    }

    /// Java:603-632 `recommendItem()`：一张 650×650 的图 + 标题 + 一行价格
    private func drawRecommendItem(_ d: Draw, _ s: State, index: Int,
                                   left: CGFloat, top: CGFloat) {
        let rec = s.rec(at: index)

        // Java:604-608 图（CENTER_CROP）
        let photo = pxRect(left, top, recSize, recSize)
        if let image = art("order_rec_" + String(index + 1)) {
            d.clipped(to: photo, radius: 0) { d.artFill(image, in: photo) }
        } else {
            d.rect(photo, photoPlaceholder)
        }

        // Java:610-614 标题：单行 + 尾部省略，宽度 = 650 - 32
        let title = ellipsize(rec.title, size: px(fRecTitle), bold: false,
                              maxWidth: px(recSize - recTextInset))
        d.text(title, x: px(left + recTextInset),
               baseline: px(top + recSize + 31 + 0.93 * fRecTitle),
               size: px(fRecTitle), ink)

        // Java:617-631 「¥ 价格 标签 销量」那一行（top + 754，高 90，CENTER_VERTICAL）
        drawRecPriceRow(d, rec: rec, left: left, centerY: top + recSize + 104 + 45)
    }

    /// Java:617-631 的横向流：`¥` `价格` 标签 销量（`flowParams(0/0/16/14)`）
    private func recPriceRow(_ rec: State.Rec, left: CGFloat, centerY: CGFloat)
        -> (yuan: CGRect, price: CGRect, tag: CGRect, sold: CGRect) {
        var x = px(left + recTextInset)

        let yuanW = Draw.width("¥", size: px(fRecTag), bold: true)
        let yuan = centeredBox(x: x, centerY: centerY, width: yuanW, size: fRecTag)
        x = yuan.maxX

        let priceW = Draw.width(rec.price, size: px(fRecPrice), bold: true)
        let price = centeredBox(x: x, centerY: centerY, width: priceW, size: fRecPrice)
        x = price.maxX + px(16)

        let tagW = Draw.width(rec.tag, size: px(fRecTag))
        let tag = centeredBox(x: x, centerY: centerY, width: tagW, size: fRecTag)
        x = tag.maxX + px(14)

        let soldW = Draw.width(rec.sold, size: px(fRecSold))
        let sold = centeredBox(x: x, centerY: centerY, width: soldW, size: fRecSold)
        return (yuan, price, tag, sold)
    }

    private func drawRecPriceRow(_ d: Draw, rec: State.Rec, left: CGFloat, centerY: CGFloat) {
        let row = recPriceRow(rec, left: left, centerY: centerY)
        d.text("¥", x: row.yuan.minX,
               baseline: px(centeredBaseline(centerY: centerY, size: fRecTag)),
               size: px(fRecTag), orange, bold: true)
        d.text(rec.price, x: row.price.minX,
               baseline: px(centeredBaseline(centerY: centerY, size: fRecPrice)),
               size: px(fRecPrice), orange, bold: true)
        d.text(rec.tag, x: row.tag.minX,
               baseline: px(centeredBaseline(centerY: centerY, size: fRecTag)),
               size: px(fRecTag), gray)
        d.text(rec.sold, x: row.sold.minX,
               baseline: px(centeredBaseline(centerY: centerY, size: fRecSold)),
               size: px(fRecSold), gray)
    }

    // MARK: - 底栏（Java:676-725，参考图 y 2752..3036）
    //
    // Java 的注释（Java:679）：这一条**只是展示，五个都不接点击**。

    private func drawBottomBar(_ d: Draw) {
        // Java:683 白底
        d.rect(pxRect(0, bottomBarTop, Self.designWidth, screenBottom - bottomBarTop), card)

        // Java:685-688 客服 / 更多（图标 72×72，字在图标下面居中）
        drawIcon(d, "order_ic_service", pxRect(52, bottomBarTop + 32, 72, 72))
        drawTabLabel(d, "客服", centerX: 88)
        drawIcon(d, "order_ic_more", pxRect(174, bottomBarTop + 32, 72, 72))
        drawTabLabel(d, "更多", centerX: 210)

        // Java:690-692 三个按钮
        drawPill(d, "闲鱼转卖", left: 327, primary: false)
        drawPill(d, "加入购物车", left: 673, primary: false)
        drawPill(d, "再买一单", left: 1019, primary: true)
    }

    /// Java:704-713 `tabLabel()`：`rect(centerX - 80, 108, 160, 60)`，36 号、`Gravity.CENTER`
    private func drawTabLabel(_ d: Draw, _ value: String, centerX: CGFloat) {
        let box = pxRect(centerX - 80, bottomBarTop + 108, 160, 60)
        // box 已经是画布单位，所以这里直接在画布坐标里加「0.33×字号」
        d.centerText(value, cx: box.midX,
                     baseline: box.midY + px(fService * 0.33),
                     size: px(fService), tabText)
    }

    /// Java:715-725 `pill()`：`rect(left, 33, 316, 127)`、10 圆角；
    /// `primary` 是那个实心橙（白字），其余是浅灰底 + 深字。
    private func drawPill(_ d: Draw, _ value: String, left: CGFloat, primary: Bool) {
        let box = pxRect(left, bottomBarTop + 33, 316, 127)
        d.round(box, radius: px(10), primary ? orangeBtn : btnGray)
        // 同 `drawTabLabel`：box 已是画布单位，直接在画布坐标里加「0.33×字号」
        d.centerText(value, cx: box.midX,
                     baseline: box.midY + px(fBtn * 0.33),
                     size: px(fBtn), primary ? UIColor.white : ink)
    }

    // MARK: - 文本落位小工具

    /// 一个「在 `centerY` 上垂直居中」的框（Java 里 `CENTER_VERTICAL` 的子项都用它测命中）
    private func centeredBox(x: CGFloat, centerY: CGFloat, width: CGFloat,
                             size: CGFloat) -> CGRect {
        let height = px(size * 1.4)
        return CGRect(x: x, y: px(centerY) - height / 2, width: width, height: height)
    }

    /// Java `text()` / `textRight()`（Java:279-299 `placeAt` + 1.4×字号的框）的命中矩形。
    ///
    /// Java 给的宽度是 `WRAP_CONTENT`，所以命中宽度就是量出来的文字宽度；
    /// `maxWidth > 0` 时按截断后的宽度算（商品标题 / 推荐位标题是单行省略的）。
    private func inkBox(_ value: String, left: CGFloat, center: CGFloat, size: CGFloat,
                        bold: Bool = false, maxWidth: CGFloat = 0) -> CGRect {
        var width = Draw.width(value, size: px(size), bold: bold)
        if maxWidth > 0 { width = min(width, maxWidth) }
        return CGRect(x: px(left), y: px(center - size * 0.5),
                      width: width, height: px(size * 1.4))
    }

    /// `textRight()` 的命中矩形（右边界固定）
    private func inkBoxRight(_ value: String, right: CGFloat, center: CGFloat, size: CGFloat,
                             bold: Bool = false) -> CGRect {
        let width = Draw.width(value, size: px(size), bold: bold)
        return CGRect(x: px(right) - width, y: px(center - size * 0.5),
                      width: width, height: px(size * 1.4))
    }

    /// `orderLine()` 右侧值的命中矩形（Java:582-587：宽 860、右对齐到 1321、高度随换行）
    private func valueBox(_ value: String, font: CGFloat, top: CGFloat) -> CGRect {
        let lines = wrap(value, size: px(font), maxWidth: px(860))
        let height = px(CGFloat(lines.count) * lineAdvance(font))
        return CGRect(x: px(1321 - 860), y: px(top - font * 0.05),
                      width: px(860), height: height)
    }

    /// 顶栏标题的命中矩形（Java:339-347）：宽度是文字宽 + 左右各 24 的内边距，
    /// 高度 1.4×字号，整体在 1380 的中间居中。
    private func toolbarTitleBox(_ title: String) -> CGRect {
        let width = Draw.width(title, size: px(fTitle), bold: true) + px(48)
        return CGRect(x: px(Self.designWidth / 2) - width / 2, y: px(statusBar + 59),
                      width: width, height: px(fTitle * 1.4))
    }

    /// 单行尾部省略（Java:426-428、610-614 的 `setSingleLine` + `setEllipsize(END)`）
    private func ellipsize(_ value: String, size: CGFloat, bold: Bool,
                           maxWidth: CGFloat) -> String {
        guard maxWidth > 0 else { return value }
        guard Draw.width(value, size: size, bold: bold) > maxWidth else { return value }
        let mark = "…"
        var result = ""
        for ch in value {
            let candidate = result + String(ch)
            if Draw.width(candidate + mark, size: size, bold: bold) > maxWidth { break }
            result = candidate
        }
        return result + mark
    }

    /// 逐字折行（对应 TextView 的自动换行；`\n` 强制换行）。
    /// 中文逐字断行与 Android 一致；纯英文长串这里会从中间断开（Android 走单词边界）。
    private func wrap(_ value: String, size: CGFloat, bold: Bool = false,
                      maxWidth: CGFloat) -> [String] {
        guard !value.isEmpty, maxWidth > 0 else { return [value] }
        var lines: [String] = []
        for paragraph in value.components(separatedBy: "\n") {
            var line = ""
            for ch in paragraph {
                let candidate = line + String(ch)
                if line.isEmpty || Draw.width(candidate, size: size, bold: bold) <= maxWidth {
                    line = candidate
                } else {
                    lines.append(line)
                    line = String(ch)
                }
            }
            lines.append(line)
        }
        return lines.isEmpty ? [value] : lines
    }

    // MARK: - 命中（Java 里每一条 `tap(...)` / `setOnClickListener` 都对应一个令牌）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let p = CGPoint(x: x, y: y)

        // 顶栏不跟随滚动，而宿主已经把 scroll 加进 y（`Screen.swift` 的 touchesEnded），
        // 所以这里先减回去 —— 滚到中间时返回键照样点得着（同 `CustomerServiceScreen`）。
        let chromeY = y - s.scroll

        // 固定在顶栏上的两个热区：返回（Java:332-336 → `finish()`）与标题（Java:342）。
        // 顺序与绘制一致，后登记的先命中。
        let chromeHits: [(CGRect, ActionToken)] = [
            (pxRect(53, statusBar + 74, 38, 62), "back"),
            (toolbarTitleBox(s.title), "order:title"),
        ]
        for (rect, token) in chromeHits.reversed() where rect.contains(CGPoint(x: x, y: chromeY)) {
            return token
        }

        // 只有落在滚动视口里（屏幕坐标 359..2904，见文件头「滚动」）的触摸才判内容
        guard chromeY >= px(addressTop),
              chromeY <= px(addressTop + bottomBarTop - toolbarH) else {
            return "noop"
        }

        // 登记顺序 = 绘制顺序；最后统一 reversed，所以后画的压在上面先命中。
        var hits: [(CGRect, ActionToken)] = [
            // Java:365 短地址 → edit("收货地址（短）", ADDRESS)
            (inkBox(s.address, left: 164, center: 454, size: fAddress, bold: true),
             "order:address"),
            // Java:369 收货人 → editReceiver()（姓名 + 手机号两个输入框）
            (inkBox(s.receiver, left: 161, center: 538, size: fReceiver), "order:receiver"),
            // 店铺 logo → 相册选择 / 恢复默认
            (pxRect(56, 697, 130, 130), "order:pickLogo"),
            // Java:403 店铺名 → edit("店铺名称", SHOP)
            (inkBox(s.shop, left: 222, center: 730, size: fShop, bold: true), "order:shop"),
            // Java:406 店铺说明 → edit("店铺说明", SHOP_NOTE)
            (inkBox(s.shopNote, left: 223, center: 805, size: fShopNote), "order:shopNote"),
            // Java:423 商品图 → pickImage()（相册 / 恢复参考图）
            (pxRect(61, 917, 292, 296), "order:pickImage"),
            // Java:429 商品标题 → edit("商品标题", GOOD_TITLE)
            (inkBox(s.goodsTitle, left: 429, center: 945, size: fGood, bold: true,
                    maxWidth: px(665)), "order:goodsTitle"),
            // Java:435 商品原价 → edit("商品原价", GOOD_PRICE, numberOnly)
            (inkBoxRight("¥" + s.goodPrice, right: 1317, center: 949, size: fGoodPrice,
                         bold: true), "order:goodPrice"),
            // Java:438 规格 → edit("商品规格", GOOD_SPEC)
            (inkBox(s.goodsSpec, left: 401, center: 1017, size: fSpec), "order:spec"),
            // Java:442 数量 → edit("购买数量", GOOD_COUNT, numberOnly)
            (inkBoxRight("x" + s.goodCount, right: 1313, center: 1016, size: fSpec),
             "order:count"),
            // Java:446 退款说明 → edit("退款说明", REFUND_NOTE)
            (inkBox(s.refundNote, left: 400, center: 1080, size: fRefund), "order:refund"),
            // Java:455 实付价 → edit("实付价", PAID_PRICE, numberOnly)
            (paidRow(s).value, "order:paidPrice"),
            // Java:467 「加入购物车」→ toast("已加入购物车")
            (pxRect(1026, 1232, 295, 112), "order:addCart"),
            // Java:471 商品总价标签 → edit("商品总价", TOTAL_PRICE, numberOnly)
            (inkBox("商品总价", left: 62, center: 1459, size: fLabel), "order:totalPrice"),
            // Java:474 商品总价金额 → 同一个 edit
            (inkBoxRight("¥" + s.totalPrice, right: 1317, center: 1464, size: fLabel,
                         bold: true), "order:totalPrice"),
            // Java:486 平台优惠 → edit("平台优惠金额", DISCOUNT, numberOnly)
            (inkBox("平台优惠", left: 143, center: 1626, size: fLabelSmall), "order:discount"),
            // Java:488 购物券 → 同一个 edit
            (inkBox("购物券", left: 352, center: 1625, size: fLabelSmall), "order:discount"),
            // Java:491 优惠金额 → 同一个 edit
            (inkBoxRight("-¥" + s.discount, right: 1268, center: 1628, size: fOrangeValue,
                         bold: true), "order:discount"),
            // Java:495 应付款标签 → edit("应付款", PAYABLE, numberOnly)
            (inkBox("应付款", left: 62, center: 1781, size: fLabel, bold: true), "order:payable"),
            // Java:498 共减 ¥ → edit("平台优惠金额", DISCOUNT, numberOnly)
            (inkBox("共减¥" + s.discount, left: 253, center: 1788, size: fLabel), "order:discount"),
            // Java:502 应付款金额 → 同一个 edit
            (inkBoxRight("¥" + s.payable, right: 1261, center: 1775, size: fPayable,
                         bold: true), "order:payable"),
            // Java:520-525 「订单信息 共5项 ⌃」那一条 → 收起 / 展开（`placeAt(40, 1930, 460, 70)`）
            (pxRect(40, 1930, 460, 70), "order:toggle"),
            // Java:529 订单号 → edit("订单号", ORDER_NO)
            (inkBox(s.orderNo, left: 665, center: 1963, size: fOrderNo), "order:orderNo"),
            // Java:532 复制 → 写剪贴板 + toast("订单号已复制")
            (inkBox("复制", left: 1228, center: 1963, size: fCopy), "order:copy"),
            // Java:562 「共2项」→ edit("保障项数", GUARANTEE_COUNT, numberOnly)
            (pxRect(268, 2730, 116, 64), "order:guaranteeCount"),
            // Java:565 保障依据 → edit("保障依据", GUARANTEE)
            (inkBox(s.guarantee, left: 489, center: 2762, size: fGuaranteeSub), "order:guarantee"),
        ]

        // Java:541-550 展开时那五行值（点了改整条地址 / 单行字段）。
        // 值的框宽 860、右对齐到 1321（Java:582-587）。
        if s.orderExpanded {
            hits.append((valueBox(s.receiverLine, font: fValue, top: 2061), "order:fullAddress"))
            hits.append((valueBox(s.invoice, font: fKey, top: 2261), "order:invoice"))
            hits.append((valueBox(s.tradeNo, font: fKey, top: 2381), "order:tradeNo"))
            hits.append((valueBox(s.createTime, font: fKey, top: 2501), "order:createTime"))
            hits.append((valueBox(s.dealTime, font: fKey, top: 2620), "order:dealTime"))
        }

        // Java:603-632 六个推荐位：图、标题、价格行三个都挂同一个 editRecommend(index)
        for rowIndex in 0..<(State.recCount / 2) {
            let top = recTop + CGFloat(rowIndex) * recPitch
            for column in 0..<2 {
                let index = rowIndex * 2 + column
                let left = recMargin + CGFloat(column) * (recSize + recGutter)
                let token = "order:rec:\(index)"
                hits.append((pxRect(left, top, recSize, recSize), token))
                hits.append((pxRect(left + recTextInset, top + recSize + 31,
                                    recSize - recTextInset, 80), token))
                hits.append((pxRect(left + recTextInset, top + recSize + 104,
                                    recSize - recTextInset, 90), token))
            }
        }

        for (rect, token) in hits.reversed() where rect.width > 0 && rect.height > 0 {
            if rect.contains(p) { return token }
        }
        return "noop"
    }
}

// MARK: - 还缺的素材（Android `drawable-nodpi/order_*.png`，iOS Assets 里一张都没有）
//
// 本文件用的是同名动态查找（`art(_:)`），所以把下面这些图按**同名**加进
// `ios/Resources/Assets.xcassets` 就自动替换掉矢量兜底，代码不用再改。
// 尺寸是 Android 原图的像素尺寸，进 imageset 时建议按 1x 原图（`Contents.json` 里
// 只填 universal 1x）或按 3x 提供，绘制端只做拉伸，不依赖 scale。
//
//   order_ic_back        38×62   顶栏返回（近黑 `<`）
//   order_ic_dots        80×24   顶栏右上角三个点（装饰，不接点击）
//   order_ic_pin         57×61   地址卡定位针
//   order_shop_logo     130×130  店铺 logo（原图近黑的整块方图）
//   order_main          480×480  商品主图（用户换过图时用 `State.customGoodsImage`）
//   order_ic_caret_down  40×24   商品标题右侧 / 订单信息收起态
//   order_ic_caret_up    41×26   订单信息展开态 / 应付款右侧
//   order_ic_arrow_r     27×39   店铺「进店逛逛」、退款说明、优惠、保障依据四个右箭头
//   order_ic_coupon      83×70   金额区的券图标（淘宝红 #F00030）
//   order_ic_info        51×34   收货信息右侧小信息标
//   order_ic_service     70×70   底栏「客服」（橙 #D84000）
//   order_ic_more        65×64   底栏「更多」
//   order_rec_1..6      480×480  六个推荐位图（`order_rec_1` … `order_rec_6`）
//
// 另：`drawable-nodpi/order_ic_service2.png`（92×78）在这一版 Java 里没被引用，所以这里也没有。

// MARK: - 订单详情可编辑
extension TaobaoOrderScreen.State: EditableState {
    func editTitle(_ key: String) -> String {
        switch key {
        case "title": return "修改交易状态"
        case "address": return "修改收货地址"
        case "fullAddress": return "修改完整收货地址"
        case "shop": return "修改店铺名称"
        case "shopNote": return "修改店铺说明"
        case "goodsTitle": return "修改商品标题"
        case "goodPrice": return "修改商品原价"
        case "spec": return "修改商品规格"
        case "count": return "修改购买数量"
        case "refund": return "修改退款说明"
        case "paidPrice": return "修改实付价"
        case "totalPrice": return "修改商品总价"
        case "discount": return "修改优惠金额"
        case "payable": return "修改应付款"
        case "orderNo": return "修改订单号"
        case "invoice": return "修改发票信息"
        case "tradeNo": return "修改交易号"
        case "createTime": return "修改创建时间"
        case "dealTime": return "修改成交时间"
        case "guarantee": return "修改保障依据"
        case "guaranteeCount": return "修改保障项数"
        default: return "修改订单信息"
        }
    }

    func editValue(_ key: String) -> String {
        switch key {
        case "title": return title
        case "address": return address
        case "fullAddress": return fullAddress
        case "shop": return shop
        case "shopNote": return shopNote
        case "goodsTitle": return goodsTitle
        case "goodPrice": return goodPrice
        case "spec": return goodsSpec
        case "count": return goodCount
        case "refund": return refundNote
        case "paidPrice": return paidPrice
        case "totalPrice": return totalPrice
        case "discount": return discount
        case "payable": return payable
        case "orderNo": return orderNo
        case "invoice": return invoice
        case "tradeNo": return tradeNo
        case "createTime": return createTime
        case "dealTime": return dealTime
        case "guarantee": return guarantee
        case "guaranteeCount": return guaranteeCount
        default: return ""
        }
    }

    func applyEdit(_ key: String, _ text: String) {
        switch key {
        case "title": title = text
        case "address": address = text
        case "fullAddress": fullAddress = text
        case "shop": shop = text
        case "shopNote": shopNote = text
        case "goodsTitle": goodsTitle = text
        case "goodPrice": goodPrice = text
        case "spec": goodsSpec = text
        case "count": goodCount = text
        case "refund": refundNote = text
        case "paidPrice": paidPrice = text
        case "totalPrice": totalPrice = text
        case "discount": discount = text
        case "payable": payable = text
        case "orderNo": orderNo = text
        case "invoice": invoice = text
        case "tradeNo": tradeNo = text
        case "createTime": createTime = text
        case "dealTime": dealTime = text
        case "guarantee": guarantee = text
        case "guaranteeCount": guaranteeCount = text
        default: break
        }
    }
}
