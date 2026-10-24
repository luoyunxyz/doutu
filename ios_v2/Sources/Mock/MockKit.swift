import UIKit

/// 「模拟」模块（Android `com.luoyun.doutu.features.mock`）的 iOS 移植 —— 共用层。
///
/// 与 Android 的对应关系：
///
/// | Android | iOS |
/// |---|---|
/// | `SimulationActivity`（31 格功能首页） | `MockHome`（画在首页第 4 个 Tab 里） |
/// | `XxxActivity`（编辑页：模板 / 预览 / 生成） | `MockEditorScreen` |
/// | `XxxDetailActivity`（全屏成品页） | `MockDetailScreen` |
/// | `buildStatement()` / `buildQuery()` … | `MockComposer.page(...)` + `MockNye.swift` 等 |
///
/// 单位换算：Android 侧 `dp(v)` 与 `setTextSize(v)` 都在 **1080 宽 / density 3** 的参考
/// 画布上，等价于 `v * 3` 像素；iOS 的画布同样是 1080 宽（见 `Canvas`），所以本文件里的
/// `dp(_:)` 就是 `* 3`，两边数字可以逐条对照。

/// Android `dp()` 的等价物：设计坐标 = dp × 3。
@inline(__always)
func dp(_ v: CGFloat) -> CGFloat { v * 3 }

// MARK: - 调色板（全部取自 Android 源码里的 0xAARRGGBB）

enum MockInk {
    /// 编辑页底色 `0xfff5f6fa`
    static let page = Draw.color(0xfff5f6fa)
    /// 功能首页底色 `0xfff2f3f5`
    static let homePage = Draw.color(0xfff2f3f5)
    static let white = UIColor.white
    /// 标题 / 主文字 `0xff161722`
    static let title = Draw.color(0xff161722)
    /// 行标签 `0xff999999`
    static let label = Draw.color(0xff999999)
    /// 行值 `0xff333333`
    static let value = Draw.color(0xff333333)
    /// 次级文字 `0xff666666`
    static let grey = Draw.color(0xff666666)
    /// 分割线 `0xffeeeeee`
    static let line = Draw.color(0xffeeeeee)
    /// 灰色分带 `0xfff2f2f2`
    static let band = Draw.color(0xfff2f2f2)
    /// 状态块底色 `0xfff5f6f8`
    static let statusBg = Draw.color(0xfff5f6f8)
    /// 成功绿 `0xff00b578`
    static let green = Draw.color(0xff00b578)
    /// 主按钮橙 `0xffffb400`
    static let orange = Draw.color(0xffffb400)
    /// 「查看更多」金 `0xffc9a25a`
    static let gold = Draw.color(0xffc9a25a)
    /// 模板按钮底色 `0xfff2f2f2`
    static let chip = Draw.color(0xfff2f2f2)
    /// 模板按钮选中蓝 `0xff2e9bff`
    static let chipOn = Draw.color(0xff2e9bff)
    /// 无 logo 时的蓝色圆底 `0xff5b8def`
    static let logoFallback = Draw.color(0xff5b8def)
    /// 抽屉遮罩 `0x99000000`
    static let dim = UIColor(white: 0, alpha: 0.6)
    /// 金额黑 `0xff111111`
    static let amount = Draw.color(0xff111111)
    /// 提示块底色 `0xfff7f7f7`
    static let noteBg = Draw.color(0xfff7f7f7)
    /// 提示块标题 `0xff888888`
    static let noteTitle = Draw.color(0xff888888)
    /// 工行表头灰蓝 `0xff6b7b8c`
    static let steel = Draw.color(0xff6b7b8c)
    /// 工行红 `0xfff5434a`
    static let icbcRed = Draw.color(0xfff5434a)
    /// 链接蓝 `0xff2f7ed8`
    static let linkBlue = Draw.color(0xff2f7ed8)
    /// 编辑页输入框深色（与 Android 的 AlertDialog 主题无关，这里统一用系统弹窗）
    static let black = UIColor.black
}

// MARK: - 素材

/// Android `loadDrawable("mock_home/<name>.png")` 的等价物。
///
/// 同步脚本（`_ios/sync-mock-assets.mjs`）把 `assets/mock_home/*` 统一加了 `mock_` 前缀放进
/// `Assets.xcassets`，所以这里只拼前缀。
enum MockAssets {
    static func named(_ name: String) -> UIImage? {
        let asset = "mock_" + name
        return UIImage(named: asset)
    }
}

// MARK: - 六个已交付的银行模块

/// 与 `SimulationActivity.TARGETS` 前 6 项一一对应。
enum MockModule: Int, CaseIterable {
    case nye = 0      // 农业模拟（NyeActivity）
    case icbc = 1     // 工商模拟（IcbcActivity）
    case boc = 2      // 中国模拟（BocActivity）
    case psbc = 3     // 邮政模拟（PsbcActivity）
    case cmb = 4      // 招商模拟（CmbActivity）
    case eank = 5     // 光大模拟（EankActivity）
    case xingye = 6   // 兴业模拟（安卓设备上的版式，无 Java 源码，按截图复刻）
    case jianshe = 7  // 建设模拟（12 个子页，子页名与招商一致）
    case pingan = 8   // 平安模拟（5 个子页，转账交易详情）
    case jiaotong = 9 // 交通模拟（10 个子页，账号转账成功）
    case zhongxin = 10 // 中信模拟（5 个子页，银行卡转账成功）
    case pufa = 11    // 浦发模拟（4 个子页，转账结果）
    case minsheng = 12 // 民生模拟（3 个子页，交易详情）
    case wechat = 13   // 微信模拟（12+ 个子页，支付成功）
    case alipay = 14   // 支付宝模拟（12 个子页，支付成功）
    case huiwang = 15  // 汇旺模拟（3 个子页，账单详情）
    case shuzi = 16    // 数字钱包（12+ 个子页，转钱成功）
    case yunshanfu = 24 // 云闪付（11 个子页，转账成功）—— 宫格索引 24
    case cbi = 23      // c币钱包（1 个子页，交易流水详情）
    case tp = 25       // TP钱包（2 个子页，交易详情）
    case okx = 26      // 欧易模拟（4 个子页，提币详情）
    case douyin = 17   // 抖音（4 个子页，支付成功）
    case yindu = 18    // 印度模拟（4 个子页，Money Sent）

    /// 首页卡片标题（`LABELS[0...5]`）
    var title: String {
        switch self {
        case .nye: return "农业模拟"
        case .icbc: return "工商模拟"
        case .boc: return "中国模拟"
        case .psbc: return "邮政模拟"
        case .cmb: return "招商模拟"
        case .eank: return "光大模拟"
        case .xingye: return "兴业模拟"
        case .jianshe: return "建设模拟"
        case .pingan: return "平安模拟"
        case .jiaotong: return "交通模拟"
        case .zhongxin: return "中信模拟"
        case .pufa: return "浦发模拟"
        case .minsheng: return "民生模拟"
        case .wechat: return "微信模拟"
        case .alipay: return "支付宝"
        case .huiwang: return "汇旺模拟"
        case .shuzi: return "数字钱包"
        case .yunshanfu: return "云闪付"
        case .cbi: return "c币钱包"
        case .tp: return "TP钱包"
        case .okx: return "欧易模拟"
        case .douyin: return "抖音"
        case .yindu: return "印度模拟"
        }
    }

    /// 编辑页右侧抽屉里的子页名（对应各 Activity 的 `SUBS`）
    var subs: [String] {
        switch self {
        case .nye:
            return ["明细转出1", "明细转出2", "明细转入", "支付宝转入", "明细转出3"]
        case .icbc:
            // 工商实际 6 个子页（`IcbcActivity.SUBS`）
            return ["明细转出1", "明细转出2", "明细转入1", "明细支付宝转入", "明细转入2", "明细转入3"]
        case .boc:
            // 中国银行实际 6 个子页（`BocActivity.SUBS`）：0/4 是「操作结果」，其余是「明细」
            return ["明细转出1", "明细转入1", "支付宝转入", "明细转入2", "明细转出2", "支付宝到账"]
        case .psbc:
            // 邮储实际有 8 个子页（`PsbcActivity.SUBS`），不是交接文档里写的 5 个
            return ["明细转出(旧)", "明细转入1(旧)", "明细转入2(旧)", "明细转出1(新)",
                    "明细转出2(新)", "明细转出3(新)", "明细转入(新)", "回单"]
        case .cmb:
            // 招商实际 12 个子页（`CmbActivity.SUBS`），用 `MODES` 归成 5 种版式
            return ["明细转出(旧)", "明细转入(旧)", "明细转出1", "明细支付宝转入",
                    "明细转入", "明细转出2", "微信支付宝转入", "明细转出3",
                    "回单", "余额", "微信转入", "财付通转出"]
        case .eank:
            return ["明细转出", "明细转入", "转账详情"]
        case .xingye:
            // 安卓设备上「兴业模拟」抽屉里的五项（按截图抄，无 Java 源码）
            return ["明细转出", "明细转入", "回单", "转账结果", "明细转出2"]
        case .jianshe:
            // 与招商同名的 12 个子页（安卓设备截图）
            return ["明细转出(旧)", "明细转入(旧)", "明细转出1", "明细支付宝转入",
                    "明细转入", "明细转出2", "微信支付宝转入", "明细转出3",
                    "回单", "余额", "微信转入", "财付通转出"]
        case .pingan:
            // 安卓设备抽屉里的五项（第 2、4 项同名，照抄）
            return ["明细转出(旧)", "明细转出(新)", "明细转入(新)", "明细转出(新)", "转出(新)"]
        case .jiaotong:
            // 安卓设备抽屉里的十项
            return ["明细转出1(新)", "明细转入1", "明细转出2(新)", "支付宝转入2(新)",
                    "明细转入2", "明细转入3", "支付宝转入1", "明细转出3(新)",
                    "回单", "付款结果"]
        case .zhongxin:
            // 安卓设备抽屉里的五项
            return ["明细转出1(旧)", "明细转入1(旧)", "明细转出2(旧)",
                    "明细转出(新)", "明细转入(新)"]
        case .pufa:
            // 安卓设备抽屉里的四项
            return ["明细转出1", "明细转出2", "明细转入", "支付宝转入"]
        case .minsheng:
            // 安卓设备抽屉里的三项
            return ["明细转出", "明细转入", "明细转入支付宝"]
        case .wechat:
            // 安卓设备抽屉里的前 12 项（下面可能还有，后续照截图补）
            return ["扫码转出1", "扫码转出2", "扫码转出2", "明细(转出)",
                    "消息盒子", "收支明细", "扫码捐款", "扫码收款",
                    "赞赏码转出", "赞赏码转出2", "赞赏码转入", "商户转出1"]
        case .alipay:
            // 安卓设备抽屉里的前 12 项
            return ["扫码转出1", "扫码转出2", "对行转出", "对支付宝转出",
                    "话费转出", "明细(转出)", "消息盒子", "收支明细",
                    "扫码收款1", "扫码收款2", "商户转出1", "商户转出2"]
        case .huiwang:
            // 安卓设备抽屉里的三项
            return ["明细转出", "明细转入", "明细转出2"]
        case .shuzi:
            // 安卓设备抽屉里的前 12 项（按银行分，下面还有）
            return ["建设转出1", "建设转出2", "建设转入", "建设存入", "建设转出",
                    "招商结果", "招商转出", "工商转出", "工商转出2",
                    "农业转出", "兴业转出", "邮政转出"]
        case .yunshanfu:
            // 安卓设备抽屉里的十一项
            return ["转账", "转账2", "转出1", "转出2", "转出3", "转出4",
                    "支付宝转入", "转出2", "支付助手", "回单", "通知收款人"]
        case .cbi:
            // 只有一个子页
            return ["c币钱包"]
        case .tp:
            // 只要两个子页
            return ["转入", "转出"]
        case .okx:
            // 四个子页
            return ["链上提币", "请求提币", "内部转账", "转出"]
        case .douyin:
            // 四个子页
            return ["支付", "零钱明细", "支付-零钱", "支付2"]
        case .yindu:
            // 四个子页
            return ["转出1", "转出2", "转出3", "转出4"]
        }
    }

    /// 首页卡片 logo（`LOGOS[0...5]`，资源已同步成 `mock_` 前缀）
    var logo: String {
        switch self {
        case .nye: return "mock_abc"
        case .icbc: return "mock_icbc"
        case .boc: return "mock_boc"
        case .psbc: return "mock_psbc"
        case .cmb: return "mock_cmb"
        case .eank: return "mock_ceb"
        case .xingye: return "mock_cib"
        case .jianshe: return "mock_ccb"
        case .pingan: return ""      // 宫格里也没有 logo（`MockHome.logos[8]` 是空串）
        case .jiaotong: return "mock_bcm"
        case .zhongxin: return "mock_citic"
        case .pufa: return "mock_bufa"
        case .minsheng: return "mock_mingsheng"
        case .wechat: return "mock_wechat"
        case .alipay: return "mock_alipay"
        case .huiwang: return "mock_hw"
        case .shuzi: return "mock_shuzi"
        case .yunshanfu: return ""     // 宫格 logos[24] 也是空串
        case .cbi: return "mock_cbi"
        case .tp: return "mock_cbi"      // 宫格 logos[25] 是空串，这里借用同色的 c币图标
        case .okx: return ""             // 宫格 logos[26] 也是空串
        case .douyin: return "mock_douy"
        case .yindu: return "mock_yindu"
        }
    }

    /// 这一模块的预览版式是否已移植（`MockComposer` 里有没有分支）。
    /// 6 家银行全部接好了。
    var isBuilt: Bool {
        MockComposer.hasLayout(self)
    }

    /// 成品页顶部标题（对应各 `XxxDetailActivity.titleFor()` / `header(...)`）
    func detailTitle(_ sub: Int) -> String {
        switch self {
        case .nye:
            if sub == 0 { return "明细" }
            if sub == 4 { return "收支详情" }
            return "明细查询"
        case .icbc:
            return sub == 0 ? "汇款明细查询" : "明细详情"
        case .boc:
            return (sub == 0 || sub == 4) ? "操作结果" : "明细"
        case .psbc:
            switch sub {
            case 0: return "转账结果"
            case 1, 2, 6: return "交易详情"
            case 3, 5: return "汇款明细查询"
            case 4: return "转账记录"
            default: return "电子受理单"
            }
        case .cmb:
            switch sub {
            case 7: return "账户"
            case 8: return "电子回单"
            case 9: return "余额"
            default: return "明细详情"
            }
        case .eank:
            switch sub {
            case 0: return "转账汇款"
            case 1: return "交易详情"
            default: return "转账详情"
            }
        case .xingye:
            switch sub {
            case 0: return "明细转出"
            case 1: return "明细转入"
            case 2: return "电子回单"
            case 3: return "转账结果"
            default: return "明细"
            }
        case .jianshe:
            switch sub {
            case 0: return "转账成功"
            case 8: return "电子回单"
            case 9: return "余额"
            default: return subs.indices.contains(sub) ? subs[sub] : title
            }
        case .pingan:
            return "转账交易详情"
        case .jiaotong:
            return sub == 0 ? "账号转账" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .zhongxin:
            return sub == 0 ? "银行卡转账" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .pufa:
            return sub == 0 ? "转账结果" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .minsheng:
            return sub == 0 ? "交易详情" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .wechat:
            return sub == 0 ? "支付成功" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .alipay:
            return sub == 0 ? "支付成功" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .huiwang:
            return sub == 0 ? "账单详情" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .shuzi:
            return sub == 0 ? "转钱成功" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .yunshanfu:
            return sub == 0 ? "转账成功" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .cbi:
            return "交易流水详情"
        case .tp:
            return sub == 0 ? "交易详情" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .okx:
            return sub == 0 ? "提币详情" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .douyin:
            return sub == 0 ? "支付成功" : (subs.indices.contains(sub) ? subs[sub] : title)
        case .yindu:
            return sub == 0 ? "Money Sent" : (subs.indices.contains(sub) ? subs[sub] : title)
        default:
            return subs.indices.contains(sub) ? subs[sub] : title
        }
    }
}

// MARK: - 19 个机型模板（与各 Activity 的 `DEVICES` 完全一致）

enum MockDevices {
    static let all: [String] = [
        "本机", "Iphone13以上", "Iphone7", "oppo-1",
        "oppo-2", "vivo-1", "华为-1", "华为-2",
        "小米-1", "小米-2", "红米-1", "红米-2",
        "魅族-1", "IphoneX以上", "vivo-2", "锤子",
        "红魔1", "华为-3", "三星1",
    ]
}

// MARK: - 编辑页 / 成品页的状态

/// 一屏模拟页的全部可变数据（对应 Android 各 Activity 的成员变量 + 那些被 `bind()` 的 TextView）。
///
/// Android 把「用户改过的文字」存在每个 TextView 里，切子页重建视图就丢了；这里改成
/// 按 `标签` 存进字典，切子页、切换机型都不会丢，生成成品时也能带过去。
final class MockState: ScreenState {
    var module: MockModule
    /// 当前子页（`SUBS` 索引）
    var sub = 0
    /// 当前机型模板（`DEVICES` 索引）
    var device = 0
    /// 交易时间：进页面时取一次 `now()`，「恢复默认」重新取（对应 `time = now()`）
    var time: String
    /// 用户改过的字段：键是 Android `bind(v, "标签")` 里的标签
    var fields: [String: String] = [:]
    /// 上一帧各标签实际显示的文案（编辑框预填用）
    private var rendered: [String: String] = [:]
    /// 右侧抽屉是否打开（Android 进页面自动打开一次）
    var drawerOpen = true

    init(module: MockModule, sub: Int = 0) {
        self.module = module
        self.sub = sub
        self.time = MockState.nowString()
        super.init()
    }

    /// `yyyy-MM-dd HH:mm:ss`（对应 Android 的 `SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)`）
    static func nowString() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: Date())
    }

    /// 取值：用户改过就用改过的，否则用版式给的默认值。
    ///
    /// 顺手把「这一帧实际显示的值」记进 `rendered`，这样点开编辑框时能预填用户看到的文字
    /// （Android 是直接读 TextView 的 text，iOS 没有 TextView，只能记一份）。
    func value(_ key: String, _ fallback: String) -> String {
        let result: String
        if key == "交易时间" {
            result = time
        } else if let edited = fields[key], !edited.isEmpty {
            result = edited
        } else {
            result = fallback
        }
        rendered[key] = result
        return result
    }

    /// 上一帧这一行显示的文案（编辑框预填用）
    func currentValue(_ key: String) -> String {
        if key == "交易时间" { return time }
        return rendered[key] ?? fields[key] ?? ""
    }

    /// 写回一个字段（空串表示恢复默认）
    func apply(_ key: String, _ text: String) {
        if text.isEmpty {
            fields.removeValue(forKey: key)
        } else if key == "交易时间" {
            time = text
        } else {
            fields[key] = text
        }
    }
}

// MARK: - 预览版式的两种度量（编辑页 / 成品页）

/// Android 的编辑页与成品页是两份几乎一样的 `buildXxx()`，字号与内边距各差 1~2dp。
/// 这里把差异收进一份度量表，版式代码只写一遍。
struct MockMetrics {
    /// 成品页（字号/内边距都比编辑页大一圈）。各家银行的差异用这个开关各取所需。
    var isDetail: Bool
    // 顶部标题条
    var titleBarH: CGFloat
    var titleSize: CGFloat
    // 通用行
    var rowSize: CGFloat
    var rowPadV: CGFloat
    var rowPadH: CGFloat
    // 状态条
    var statusPadV: CGFloat
    var statusPadH: CGFloat
    var statusSize: CGFloat
    var statusCheck: CGFloat
    var statusGap: CGFloat
    // 明细页：收款人 / 金额
    var nameSize: CGFloat
    var namePadTop: CGFloat
    var amountSize: CGFloat
    var amountPadTop: CGFloat
    var amountPadBottom: CGFloat
    // 明细查询：卡号行 + 交易金额行
    var cardPadV: CGFloat
    var cardPadH: CGFloat
    var cardLogo: CGFloat
    var cardNoSize: CGFloat
    var cardGap: CGFloat
    var amtLabelSize: CGFloat
    var amtSize: CGFloat
    var amtPadV: CGFloat
    var bandH: CGFloat
    // 「查看和TA的转账记录」
    var linkPadV: CGFloat
    var linkPadH: CGFloat
    var linkSize: CGFloat
    // 两个橙色按钮
    var buttonPadV: CGFloat
    var buttonH: CGFloat
    var buttonSize: CGFloat
    // 收支详情
    var kindPadTop: CGFloat
    var kindSize: CGFloat
    var kindIcon: CGFloat
    var ledgerAmountSize: CGFloat
    var ledgerPadTop: CGFloat
    var ledgerPadBottom: CGFloat
    var balSize: CGFloat
    var balPadBottom: CGFloat
    var morePadTop: CGFloat
    var morePadBottom: CGFloat
    var moreSize: CGFloat

    /// 编辑页（`NyeActivity.buildStatement` 等）
    static let editor = MockMetrics(
        isDetail: false,
        titleBarH: dp(52), titleSize: dp(17),
        rowSize: dp(14), rowPadV: dp(10), rowPadH: dp(14),
        statusPadV: dp(12), statusPadH: dp(14), statusSize: dp(14),
        statusCheck: dp(18), statusGap: dp(8),
        nameSize: dp(14), namePadTop: dp(18),
        amountSize: dp(28), amountPadTop: dp(4), amountPadBottom: dp(16),
        cardPadV: dp(12), cardPadH: dp(14), cardLogo: dp(26), cardNoSize: dp(18), cardGap: dp(10),
        amtLabelSize: dp(14), amtSize: dp(26), amtPadV: dp(6), bandH: dp(8),
        linkPadV: dp(14), linkPadH: dp(14), linkSize: dp(14),
        buttonPadV: dp(16), buttonH: dp(46), buttonSize: dp(16),
        kindPadTop: dp(20), kindSize: dp(15), kindIcon: dp(16),
        ledgerAmountSize: dp(30), ledgerPadTop: dp(2), ledgerPadBottom: dp(4),
        balSize: dp(13), balPadBottom: dp(18),
        morePadTop: dp(12), morePadBottom: dp(16), moreSize: dp(13))

    /// 成品页（`NyeDetailActivity`）
    static let detail = MockMetrics(
        isDetail: true,
        // 下面这组是按**安卓设备上的 uni-app 版**逐行量出来的（2026-10-24）：
        // 明细行距实测 103px（≈34dp）＝ 15sp 文字 + 上下各 8dp；
        // 金额块比 Java 原生版松，合计 +11dp。
        titleBarH: dp(44), titleSize: dp(19),
        rowSize: dp(15), rowPadV: dp(8), rowPadH: dp(16),
        statusPadV: dp(20), statusPadH: dp(16), statusSize: dp(15),
        statusCheck: dp(20), statusGap: dp(9),
        nameSize: dp(15), namePadTop: dp(32),
        amountSize: dp(32), amountPadTop: dp(12), amountPadBottom: dp(59),
        cardPadV: dp(14), cardPadH: dp(16), cardLogo: dp(28), cardNoSize: dp(20), cardGap: dp(10),
        amtLabelSize: dp(15), amtSize: dp(28), amtPadV: dp(6), bandH: dp(10),
        linkPadV: dp(16), linkPadH: dp(16), linkSize: dp(15),
        buttonPadV: dp(12), buttonH: dp(35), buttonSize: dp(15),
        kindPadTop: dp(24), kindSize: dp(16), kindIcon: dp(18),
        ledgerAmountSize: dp(32), ledgerPadTop: dp(2), ledgerPadBottom: dp(4),
        balSize: dp(14), balPadBottom: dp(20),
        morePadTop: dp(14), morePadBottom: dp(18), moreSize: dp(14))
}

// MARK: - 绘制指令（一次构建、多次渲染）

/// 一条绘制指令。用「指令数组」而不是直接画，是因为同一份版式要做三件事：
/// 量高度（滚动范围）、画（draw）、命中（可点区域），三者必须完全一致。
enum MockOp {
    case fill(CGRect, UIColor)
    case round(CGRect, CGFloat, UIColor)
    case roundStroke(CGRect, CGFloat, UIColor, CGFloat)
    case circle(CGFloat, CGFloat, CGFloat, UIColor)
    /// 左对齐：文案, x, 基线, 字号, 颜色, 粗体
    case text(String, CGFloat, CGFloat, CGFloat, UIColor, Bool)
    /// 居中：文案, 中心 x, 基线, 字号, 颜色, 粗体
    case center(String, CGFloat, CGFloat, CGFloat, UIColor, Bool)
    /// 右对齐：文案, 右边界 x, 基线, 字号, 颜色, 粗体
    case right(String, CGFloat, CGFloat, CGFloat, UIColor, Bool)
    /// 切图：资源名, 目标矩形
    case image(String, CGRect)
    /// 横线：y, x1, x2, 颜色
    case hairline(CGFloat, CGFloat, CGFloat, UIColor)
    /// 金色向下箭头：中心 x, 中心 y, 尺寸, 颜色
    case chevronDown(CGFloat, CGFloat, CGFloat, UIColor)
    /// 圆角渐变块：矩形, 圆角, 起色, 止色, 是否上下渐变
    case gradient(CGRect, CGFloat, UIColor, UIColor, Bool)

    /// 整体平移（用于把子面板里的指令贴到父页面上）
    static func shifted(_ op: MockOp, dx: CGFloat, dy: CGFloat) -> MockOp {
        switch op {
        case let .fill(rect, color):
            return .fill(rect.offsetBy(dx: dx, dy: dy), color)
        case let .round(rect, radius, color):
            return .round(rect.offsetBy(dx: dx, dy: dy), radius, color)
        case let .roundStroke(rect, radius, color, width):
            return .roundStroke(rect.offsetBy(dx: dx, dy: dy), radius, color, width)
        case let .circle(cx, cy, radius, color):
            return .circle(cx + dx, cy + dy, radius, color)
        case let .text(string, x, baseline, size, color, bold):
            return .text(string, x + dx, baseline + dy, size, color, bold)
        case let .center(string, cx, baseline, size, color, bold):
            return .center(string, cx + dx, baseline + dy, size, color, bold)
        case let .right(string, right, baseline, size, color, bold):
            return .right(string, right + dx, baseline + dy, size, color, bold)
        case let .image(name, rect):
            return .image(name, rect.offsetBy(dx: dx, dy: dy))
        case let .hairline(y, x1, x2, color):
            return .hairline(y + dy, x1 + dx, x2 + dx, color)
        case let .chevronDown(cx, cy, size, color):
            return .chevronDown(cx + dx, cy + dy, size, color)
        case let .gradient(rect, radius, from, to, vertical):
            return .gradient(rect.offsetBy(dx: dx, dy: dy), radius, from, to, vertical)
        }
    }
}

/// 可点区域（内容坐标）。
struct MockHit {
    let rect: CGRect
    let token: ActionToken
}

/// 一屏模拟页的成品描述。
struct MockPage {
    var ops: [MockOp] = []
    var hits: [MockHit] = []
    /// 内容总高度（内容坐标）
    var height: CGFloat = 0

    func draw(_ d: Draw) {
        for op in ops {
            switch op {
            case let .fill(rect, color):
                d.rect(rect, color)
            case let .round(rect, radius, color):
                d.round(rect, radius: radius, color)
            case let .roundStroke(rect, radius, color, width):
                d.roundStroke(rect, radius: radius, color, width: width)
            case let .circle(cx, cy, radius, color):
                d.circle(cx: cx, cy: cy, r: radius, color)
            case let .text(string, x, baseline, size, color, bold):
                d.text(string, x: x, baseline: baseline, size: size, color, bold: bold)
            case let .center(string, cx, baseline, size, color, bold):
                d.centerText(string, cx: cx, baseline: baseline, size: size, color, bold: bold)
            case let .right(string, right, baseline, size, color, bold):
                d.rightText(string, right: right, baseline: baseline, size: size, color, bold: bold)
            case let .image(name, rect):
                d.art(UIImage(named: name), rect)
            case let .hairline(y, x1, x2, color):
                d.hairline(y, from: x1, to: x2, color, width: 1)
            case let .chevronDown(cx, cy, size, color):
                // `chevronDown()`：宽 w、高 h 的 View 里画两条线（Java:596-597）
                let w = size, h = size * 0.8
                d.line(cx - w / 2 + w * 0.15, cy - h / 2 + h * 0.3,
                       cx - w / 2 + w * 0.5, cy - h / 2 + h * 0.7, color, width: dp(1.2))
                d.line(cx - w / 2 + w * 0.5, cy - h / 2 + h * 0.7,
                       cx - w / 2 + w * 0.85, cy - h / 2 + h * 0.3, color, width: dp(1.2))
            case let .gradient(rect, radius, from, to, vertical):
                d.clipped(to: rect, radius: radius) {
                    d.gradient(rect, from: from, to: to, vertical: vertical)
                }
            }
        }
    }

    /// 命中（内容坐标，宿主已把 `state.scroll` 加进来）
    func token(at point: CGPoint) -> ActionToken? {
        for hit in hits.reversed() where hit.rect.contains(point) {
            return hit.token
        }
        return nil
    }
}

// MARK: - 版式构建器

/// 逐块堆叠的构建器：每调一个方法就往下推进 `y`，同时记录可点区域。
///
/// Android 那边是 `LinearLayout` 垂直排列 + `setPadding`，这里保持同样的思路，
/// 这样 `buildStatement()` 之类的代码可以一段一段照抄过来。
final class MockBuilder {
    let width: CGFloat
    let metrics: MockMetrics
    private(set) var ops: [MockOp] = []
    private(set) var hits: [MockHit] = []
    private(set) var y: CGFloat = 0

    init(width: CGFloat, metrics: MockMetrics, startY: CGFloat = 0) {
        self.width = width
        self.metrics = metrics
        self.y = startY
    }

    var result: MockPage { MockPage(ops: ops, hits: hits, height: y) }

    // MARK: 基础量算

    private func font(for size: CGFloat, bold: Bool = false) -> UIFont {
        Draw.font(size: size, bold: bold)
    }

    /// 一行文字需要的垂直空间（对应 `TextView` 的 measured height）
    func lineHeight(_ size: CGFloat, bold: Bool = false) -> CGFloat {
        font(for: size, bold: bold).lineHeight
    }

    private func ascender(_ size: CGFloat, bold: Bool = false) -> CGFloat {
        font(for: size, bold: bold).ascender
    }

    // MARK: 基础块

    func gap(_ h: CGFloat) { y += h }

    /// 铺一条满宽色块
    @discardableResult
    func fill(_ h: CGFloat, _ color: UIColor) -> CGRect {
        let rect = CGRect(x: 0, y: y, width: width, height: h)
        ops.append(.fill(rect, color))
        y += h
        return rect
    }

    /// 细分割线（`thinLine()`：1dp 高、左右各留 padH、上下各 8dp）
    func divider(_ padH: CGFloat) {
        gap(dp(8))
        ops.append(.hairline(y, padH, width - padH, MockInk.line))
        y += dp(1)
        gap(dp(8))
    }

    /// 顶部标题条：`‹` + 居中粗体标题 + 右侧占位（`topTitle()`）
    func titleBar(_ title: String) {
        let h = metrics.titleBarH
        let size = metrics.titleSize
        let baseline = y + h / 2 + ascender(size, bold: true) / 2 - size * 0.06
        ops.append(.text("‹", dp(10), baseline, dp(22), MockInk.value, false))
        ops.append(.center(title, width / 2, baseline, size, MockInk.title, true))
        y += h
    }

    /// 居中的一行大字（收款人 / 金额 / 余额）
    func centerBlock(_ key: String?, _ text: String, size: CGFloat, color: UIColor,
                     bold: Bool = false, padTop: CGFloat, padBottom: CGFloat) {
        let lh = lineHeight(size, bold: bold)
        let baseline = y + padTop + ascender(size, bold: bold)
        let rect = CGRect(x: 0, y: y + padTop, width: width, height: lh)
        ops.append(.center(text, width / 2, baseline, size, color, bold))
        if let key { hits.append(MockHit(rect: rect, token: "mock:field:\(key)")) }
        y += padTop + lh + padBottom
    }

    /// 灰底状态条：绿色圆圈 ✓ + 文案 + 右侧「进度查询 ›」
    func statusBar(_ text: String, trailing: String) {
        let lh = lineHeight(metrics.statusSize)
        let h = max(lh, metrics.statusCheck) + metrics.statusPadV * 2
        let rect = CGRect(x: 0, y: y, width: width, height: h)
        ops.append(.fill(rect, MockInk.statusBg))
        let cy = y + h / 2
        let checkR = metrics.statusCheck / 2
        ops.append(.circle(metrics.statusPadH + checkR, cy, checkR, MockInk.green))
        let checkSize = metrics.statusCheck * 0.66
        let checkBaseline = cy + ascender(checkSize) / 2 - checkSize * 0.06
        ops.append(.center("✓", metrics.statusPadH + checkR, checkBaseline, checkSize,
                           MockInk.white, false))
        let textX = metrics.statusPadH + metrics.statusCheck + metrics.statusGap
        let baseline = cy + ascender(metrics.statusSize) / 2 - metrics.statusSize * 0.06
        ops.append(.text(text, textX, baseline, metrics.statusSize, MockInk.title, false))
        ops.append(.right(trailing, width - metrics.statusPadH, baseline,
                          metrics.statusSize - dp(1), MockInk.grey, false))
        y += h
    }

    /// 通用「标签 —— 值」行：值可点（`bind(v, label)`），键就是标签名
    func row(_ label: String, _ value: String) {
        let size = metrics.rowSize
        let lh = lineHeight(size)
        let h = lh + metrics.rowPadV * 2
        let baseline = y + metrics.rowPadV + ascender(size)
        ops.append(.text(label, metrics.rowPadH, baseline, size, MockInk.label, false))
        ops.append(.right(value, width - metrics.rowPadH, baseline, size, MockInk.value, false))
        hits.append(MockHit(rect: CGRect(x: 0, y: y, width: width, height: h),
                            token: "mock:field:\(label)"))
        y += h
    }

    /// 灰底链接块：「查看和TA的转账记录  ›」
    func linkBlock(_ text: String, trailing: String) {
        let size = metrics.linkSize
        let lh = lineHeight(size)
        let h = lh + metrics.linkPadV * 2
        ops.append(.fill(CGRect(x: 0, y: y, width: width, height: h), MockInk.statusBg))
        let baseline = y + metrics.linkPadV + ascender(size)
        ops.append(.text(text, metrics.linkPadH, baseline, size, MockInk.title, false))
        ops.append(.right(trailing, width - metrics.linkPadH, baseline, size, MockInk.label, false))
        y += h
    }

    /// 底部两个按钮：左边描边、右边实心（都是橙色）
    func twoButtons(_ left: String, _ right: String) {
        y += metrics.buttonPadV
        let h = metrics.buttonH
        let padH = metrics.rowPadH
        let gapW = dp(8)
        let w = (width - padH * 2 - gapW) / 2
        let leftRect = CGRect(x: padH, y: y, width: w, height: h)
        let rightRect = CGRect(x: padH + w + gapW, y: y, width: w, height: h)
        ops.append(.round(leftRect, dp(6), MockInk.white))
        ops.append(.roundStroke(leftRect, dp(6), MockInk.orange, dp(1)))
        ops.append(.round(rightRect, dp(6), MockInk.orange))
        let baseline = y + h / 2 + ascender(metrics.buttonSize) / 2 - metrics.buttonSize * 0.06
        ops.append(.center(left, leftRect.midX, baseline, metrics.buttonSize, MockInk.orange, false))
        ops.append(.center(right, rightRect.midX, baseline, metrics.buttonSize,
                           MockInk.white, false))
        y += h + metrics.buttonPadV
    }

    /// 「消费」类型行：小图标 + 文字，居中（`buildLedger()` 的 kindRow）
    func kindRow(_ key: String, asset: String, text: String) {
        let lh = lineHeight(metrics.kindSize)
        let h = max(lh, metrics.kindIcon)
        let top = y + metrics.kindPadTop
        let textW = Draw.width(text, size: metrics.kindSize)
        let total = metrics.kindIcon + dp(6) + textW
        let startX = (width - total) / 2
        let iconY: CGFloat = top + (h - metrics.kindIcon) / 2
        ops.append(.image(asset, CGRect(x: startX, y: iconY,
                                       width: metrics.kindIcon, height: metrics.kindIcon)))
        let baseline = top + (h - lh) / 2 + ascender(metrics.kindSize)
        let rect = CGRect(x: startX, y: top, width: total, height: h)
        ops.append(.text(text, startX + metrics.kindIcon + dp(6), baseline,
                         metrics.kindSize, MockInk.grey, false))
        hits.append(MockHit(rect: rect, token: "mock:field:\(key)"))
        y = top + h + dp(2)
    }

    /// 明细查询的卡号行：logo + 卡号（`buildQuery()` 的 cardRow）
    func cardRow(_ key: String, logo: String, text: String) {
        let lh = lineHeight(metrics.cardNoSize)
        let h = max(lh, metrics.cardLogo) + metrics.cardPadV * 2
        let top = y + metrics.cardPadV
        let logoY: CGFloat = top + (max(lh, metrics.cardLogo) - metrics.cardLogo) / 2
        ops.append(.image(logo, CGRect(x: metrics.cardPadH, y: logoY,
                                       width: metrics.cardLogo, height: metrics.cardLogo)))
        let baseline = top + (max(lh, metrics.cardLogo) - lh) / 2 + ascender(metrics.cardNoSize)
        let x = metrics.cardPadH + metrics.cardLogo + metrics.cardGap
        ops.append(.text(text, x, baseline, metrics.cardNoSize, MockInk.title, false))
        hits.append(MockHit(rect: CGRect(x: 0, y: y, width: width, height: h),
                            token: "mock:field:\(key)"))
        y += h
    }

    /// 明细查询的「交易金额 ——— 值」行（`buildQuery()` 的 amtRow）
    func amountRow(_ key: String, label: String, value: String) {
        let lh = lineHeight(metrics.amtSize, bold: true)
        let h = max(lh, lineHeight(metrics.amtLabelSize)) + metrics.amtPadV * 2
        let baseline = y + metrics.amtPadV + (lh - lineHeight(metrics.amtLabelSize)) / 2
            + ascender(metrics.amtLabelSize)
        ops.append(.text(label, metrics.cardPadH, baseline, metrics.amtLabelSize, MockInk.value, false))
        let amtBaseline = y + metrics.amtPadV + ascender(metrics.amtSize, bold: true)
        ops.append(.right(value, width - metrics.cardPadH, amtBaseline,
                          metrics.amtSize, MockInk.amount, true))
        hits.append(MockHit(rect: CGRect(x: 0, y: y, width: width, height: h),
                            token: "mock:field:\(key)"))
        y += h
    }

    /// 灰带（`band`）
    func bandBlock() {
        fill(metrics.bandH, MockInk.band)
    }

    /// 「查看更多 ⌄」
    func moreRow(_ text: String) {
        let size = metrics.moreSize
        let lh = lineHeight(size)
        let top = y + metrics.morePadTop
        let w = Draw.width(text, size: size)
        let total = w + dp(3) + dp(10)
        let startX = (width - total) / 2
        let baseline = top + ascender(size)
        ops.append(.text(text, startX, baseline, size, MockInk.gold, false))
        let size2 = dp(10)
        ops.append(.chevronDown(startX + w + dp(3) + size2 / 2,
                                top + lh / 2, size2, MockInk.gold))
        y = top + lh + metrics.morePadBottom
    }

    // MARK: - 通用块（工行 / 中行 / 邮储 / 招商 / 光大 共用）

    /// 表格式「标签：值」行：标签固定宽度、右对齐带冒号，值左对齐。
    /// 工行的「汇款明细查询」与中行的交易明细都是这个版式。
    func kvRow(_ label: String, _ value: String,
               labelWidth: CGFloat, size: CGFloat,
               padH: CGFloat, padV: CGFloat,
               labelColor: UIColor = MockInk.value,
               valueColor: UIColor = MockInk.value,
               editable: Bool = true) {
        let lh = lineHeight(size)
        let h = lh + padV * 2
        let baseline = y + padV + ascender(size)
        ops.append(.right(label + "：", padH + labelWidth, baseline, size, labelColor, false))
        ops.append(.text(value, padH + labelWidth + dp(6), baseline, size, valueColor, false))
        if editable {
            hits.append(MockHit(rect: CGRect(x: 0, y: y, width: width, height: h),
                                token: "mock:field:\(label)"))
        }
        y += h
    }

    /// 左右两端对齐的行（值可指定颜色；工行的 `rowBlue` 用它）。
    /// 标签或值里带 `\n` 时按多行排（中行「付款后\n可用余额」就是这种）。
    func pairRow(_ label: String, _ value: String,
                 size: CGFloat, padH: CGFloat, padV: CGFloat,
                 labelColor: UIColor = MockInk.label,
                 valueColor: UIColor = MockInk.value,
                 editable: Bool = true) {
        let lh = lineHeight(size)
        let labelLines = label.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let valueLines = value.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let rows = max(labelLines.count, valueLines.count)
        let h = lh * CGFloat(rows) + padV * 2
        var baseline = y + padV + ascender(size)
        for index in 0..<rows {
            if index < labelLines.count {
                ops.append(.text(labelLines[index], padH, baseline, size, labelColor, false))
            }
            if index < valueLines.count {
                ops.append(.right(valueLines[index], width - padH, baseline, size, valueColor, false))
            }
            baseline += lh
        }
        if editable {
            hits.append(MockHit(rect: CGRect(x: 0, y: y, width: width, height: h),
                                token: "mock:field:\(label.replacingOccurrences(of: "\n", with: ""))"))
        }
        y += h
    }

    /// 1dp 满宽分割线（中行 / 邮储的 `line()`）
    func hairlineFull() {
        ops.append(.hairline(y, 0, width, MockInk.line))
        y += dp(1)
    }

    /// 左对齐的小节标题（「您可能需要」「更多推荐」）
    func sectionLabel(_ text: String, size: CGFloat, color: UIColor,
                      padH: CGFloat, padTop: CGFloat, padBottom: CGFloat) {
        let lh = lineHeight(size)
        let top = y + padTop
        ops.append(.text(text, padH, top + ascender(size), size, color, false))
        y = top + lh + padBottom
    }

    /// 「文字 + ›」的行
    func chevronRow(_ text: String, size: CGFloat, color: UIColor,
                    padH: CGFloat, padV: CGFloat, chevron: String = "›") {
        let lh = lineHeight(size)
        let h = lh + padV * 2
        let baseline = y + padV + ascender(size)
        ops.append(.text(text, padH, baseline, size, color, false))
        ops.append(.right(chevron, width - padH, baseline, dp(18), MockInk.label, false))
        y += h
    }

    /// 居中的圆形头像（微信「支付成功」页）。没有图片就用蓝底首字兜底。
    func centeredAvatar(_ name: String, asset: String?, diameter: CGFloat, gapTop: CGFloat) {
        y += gapTop
        let cx: CGFloat = width / 2
        let cy: CGFloat = y + diameter / 2
        let radius: CGFloat = diameter / 2
        if let asset {
            ops.append(.image(asset, CGRect(x: cx - radius, y: y, width: diameter, height: diameter)))
        } else {
            ops.append(.circle(cx, cy, radius, MockInk.logoFallback))
            let size = diameter * 0.42
            let baseline = cy + ascender(size) / 2 - size * 0.06
            ops.append(.center(String(name.prefix(1)), cx, baseline, size, MockInk.white, false))
        }
        y += diameter
    }

    /// 居中的绿色圆形 ✓（中行「操作结果」页）
    ///
    /// 注意：`MockOp.circle` 是**枚举 case**，参数没有标签，只能按位置传。
    /// 拆成本地常量再 append，避免 Swift 6 编译器在「4 个 CGFloat 表达式」上超时
    /// （Codemagic 的 Xcode 26.6 上就是这样报的：unable to type-check in reasonable time）。
    func checkBadge(diameter: CGFloat, color: UIColor, gapTop: CGFloat, symbolSize: CGFloat) {
        y += gapTop
        let cx: CGFloat = width / 2
        let cy: CGFloat = y + diameter / 2
        let radius: CGFloat = diameter / 2
        ops.append(.circle(cx, cy, radius, color))
        let baseline = cy + ascender(symbolSize) / 2 - symbolSize * 0.06
        ops.append(.center("✓", width / 2, baseline, symbolSize, MockInk.white, false))
        y += diameter
    }

    /// 左对齐的小标题 + 大金额（中行明细页「收入金额（人民币元）」+「100.00」）
    func leftAmount(_ label: String, labelKey: String?, _ value: String, valueKey: String?,
                    labelSize: CGFloat, amountSize: CGFloat, padH: CGFloat,
                    padTop: CGFloat, padBottom: CGFloat) {
        let lh1 = lineHeight(labelSize)
        let top = y + padTop
        ops.append(.text(label, padH, top + ascender(labelSize), labelSize, MockInk.title, false))
        if let labelKey {
            hits.append(MockHit(rect: CGRect(x: 0, y: top, width: width, height: lh1),
                                token: "mock:field:\(labelKey)"))
        }
        let cursor = top + lh1 + dp(2)
        let lh2 = lineHeight(amountSize, bold: true)
        ops.append(.text(value, padH, cursor + ascender(amountSize, bold: true),
                         amountSize, MockInk.amount, true))
        if let valueKey {
            hits.append(MockHit(rect: CGRect(x: 0, y: cursor, width: width, height: lh2),
                                token: "mock:field:\(valueKey)"))
        }
        y = cursor + lh2 + padBottom
    }

    /// 灰底「标签 / 值」块（中行的「对方账户名称/\n账号」那一块）
    func greyKvBlock(_ label: String, _ value: String,
                     labelWidth: CGFloat, size: CGFloat,
                     padH: CGFloat, padV: CGFloat, editable: Bool = true) {
        let lh = lineHeight(size)
        let labelLines = label.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let rows = max(1, labelLines.count)
        let h = lh * CGFloat(rows) + padV * 2
        ops.append(.fill(CGRect(x: 0, y: y, width: width, height: h), MockInk.band))
        var baseline = y + padV + ascender(size)
        for line in labelLines {
            ops.append(.text(line, padH, baseline, size, MockInk.value, false))
            baseline += lh
        }
        ops.append(.text(value, padH + labelWidth, y + padV + ascender(size), size,
                         MockInk.value, false))
        if editable {
            hits.append(MockHit(rect: CGRect(x: 0, y: y, width: width, height: h),
                                token: "mock:field:\(label.replacingOccurrences(of: "\n", with: ""))"))
        }
        y += h
    }

    /// 居中的一行链接文字（「查看全部 / 查看更多」）
    func centeredLink(_ text: String, size: CGFloat, color: UIColor, padV: CGFloat) {
        let lh = lineHeight(size)
        let top = y + padV
        let baseline = top + ascender(size)
        ops.append(.center(text, width / 2, baseline, size, color, false))
        y = top + lh + padV
    }

    /// 满宽等高的一排文字格（工行「投资理财 / 信用卡还款 / 查看来往」那种）
    func centeredCells(_ titles: [String], height: CGFloat, size: CGFloat, padV: CGFloat) {
        guard !titles.isEmpty else { return }
        y += padV
        let cellW = width / CGFloat(titles.count)
        let baseline = y + height / 2 + ascender(size) / 2 - size * 0.06
        for (index, title) in titles.enumerated() {
            ops.append(.center(title, cellW * (CGFloat(index) + 0.5), baseline, size,
                               MockInk.value, false))
        }
        y += height + padV
    }

    /// 满宽按钮（实心 / 描边）
    func fullButton(_ title: String, filled: Bool, color: UIColor,
                    height: CGFloat, padH: CGFloat, gapTop: CGFloat, size: CGFloat) {
        y += gapTop
        let rect = CGRect(x: padH, y: y, width: width - padH * 2, height: height)
        if filled {
            ops.append(.round(rect, dp(4), color))
        } else {
            ops.append(.round(rect, dp(4), MockInk.white))
            ops.append(.roundStroke(rect, dp(4), color, dp(1)))
        }
        let baseline = rect.midY + ascender(size) / 2 - size * 0.06
        ops.append(.center(title, rect.midX, baseline, size,
                           filled ? MockInk.white : color, false))
        y = rect.maxY
    }

    /// 灰底提示块：「—— 温馨提示 ——」+ 正文（正文里的 `\n` 保留，再按宽度折行）
    func noteBlock(title: String, body: String, size: CGFloat, pad: CGFloat, gapTop: CGFloat) {
        y += gapTop
        let titleH = lineHeight(size)
        let lineH = Draw.font(size: size).lineHeight
        let bodyWidth = width - pad * 2
        var lines: [String] = []
        for paragraph in body.split(separator: "\n", omittingEmptySubsequences: false) {
            lines.append(contentsOf: wrap(String(paragraph), maxWidth: bodyWidth, size: size))
        }
        let height = pad * 2 + titleH + dp(8) + CGFloat(lines.count) * lineH
        ops.append(.fill(CGRect(x: 0, y: y, width: width, height: height), MockInk.noteBg))
        let titleBaseline = y + pad + ascender(size)
        ops.append(.center(title, width / 2, titleBaseline, size, MockInk.noteTitle, false))
        var baseline = titleBaseline + titleH + dp(8)
        for line in lines {
            ops.append(.text(line, pad, baseline, size, MockInk.grey, false))
            baseline += lineH
        }
        y += height
    }

    /// 满宽切图（对应 `adjustViewBounds` + `FIT_XY`：按图片自身宽高比算高度）
    func fullWidthImage(_ name: String, gapTop: CGFloat = 0) {
        y += gapTop
        let image = UIImage(named: name)
        let ratio = image.map { $0.size.height / max(1, $0.size.width) } ?? 0.3
        let h = width * ratio
        ops.append(.image(name, CGRect(x: 0, y: y, width: width, height: h)))
        y += h
    }

    /// 页面头部（`‹` + 居中标题 + 可选右侧切图 / 文字）—— 工行、中行那几张表的卡片头
    func headerBar(_ title: String, color: UIColor,
                   icon: String? = nil, iconSize: CGSize = .zero,
                   trailing: String? = nil) {
        let h = metrics.titleBarH
        let size = dp(16)
        let baseline = y + h / 2 + ascender(size) / 2 - size * 0.06
        ops.append(.text("‹", dp(10), baseline, dp(22), color, false))
        ops.append(.center(title, width / 2, baseline, size, color, false))
        var rightEdge = width - dp(10)
        if let trailing {
            let width = Draw.width(trailing, size: size)
            ops.append(.right(trailing, rightEdge, baseline, size, color, false))
            rightEdge -= width
        }
        if let icon, iconSize != .zero {
            rightEdge -= dp(4)
            ops.append(.image(icon, CGRect(x: rightEdge - iconSize.width,
                                           y: y + (h - iconSize.height) / 2,
                                           width: iconSize.width, height: iconSize.height)))
        }
        y += h
    }

    /// 按宽度折行（逐字累计；中文没有词边界，够了）
    private func wrap(_ text: String, maxWidth: CGFloat, size: CGFloat) -> [String] {
        if text.isEmpty { return [""] }
        var lines: [String] = []
        var current = ""
        for character in text {
            let next = current + String(character)
            if Draw.width(next, size: size) > maxWidth, !current.isEmpty {
                lines.append(current)
                current = String(character)
            } else {
                current = next
            }
        }
        if !current.isEmpty { lines.append(current) }
        return lines
    }

    /// 嵌套面板的量算：内容先用子构建器跑一遍，得到外框与内容起点。
    private func layoutPanel(gapTop: CGFloat, margin: CGFloat, pad: CGFloat,
                             content: (MockBuilder) -> Void)
        -> (outer: CGRect, innerY: CGFloat, innerWidth: CGFloat, child: MockBuilder) {
        y += gapTop
        let innerWidth = width - (margin + pad) * 2
        let child = MockBuilder(width: innerWidth, metrics: metrics)
        content(child)
        let panelHeight = child.y + pad * 2
        let outer = CGRect(x: margin, y: y, width: width - margin * 2, height: panelHeight)
        return (outer, y + pad, innerWidth, child)
    }

    /// 把子构建器画好的东西整体贴进来
    private func merge(_ child: MockBuilder, dx: CGFloat, dy: CGFloat) {
        for op in child.ops {
            ops.append(MockOp.shifted(op, dx: dx, dy: dy))
        }
        for hit in child.hits {
            hits.append(MockHit(rect: hit.rect.offsetBy(dx: dx, dy: dy), token: hit.token))
        }
    }

    /// 嵌套面板：外层色块 + 内层白板，内容先用子构建器量高，再整体贴进来。
    ///
    /// 邮储的「电子受理单」就是这种结构（浅蓝底 + 白纸 + 单据行）。
    func panel(gapTop: CGFloat, margin: CGFloat, pad: CGFloat, bg: UIColor,
               content: (MockBuilder) -> Void) {
        let laid = layoutPanel(gapTop: gapTop, margin: margin, pad: pad, content: content)
        ops.append(.fill(laid.outer, bg))
        ops.append(.fill(CGRect(x: laid.outer.minX + pad, y: laid.innerY,
                                width: laid.innerWidth,
                                height: laid.outer.height - pad * 2),
                         MockInk.white))
        merge(laid.child, dx: laid.outer.minX + pad, dy: laid.innerY)
        y = laid.outer.maxY
    }

    /// 圆角渐变面板（招商的蓝色银行卡 / 促销卡）
    func gradientPanel(gapTop: CGFloat, margin: CGFloat, pad: CGFloat, radius: CGFloat,
                       from: UIColor, to: UIColor, horizontal: Bool = true,
                       content: (MockBuilder) -> Void) {
        let laid = layoutPanel(gapTop: gapTop, margin: margin, pad: pad, content: content)
        ops.append(.gradient(laid.outer, radius, from, to, !horizontal))
        merge(laid.child, dx: laid.outer.minX + pad, dy: laid.innerY)
        y = laid.outer.maxY
    }

    /// 纯色面板（光大的灰底/白底信息块）
    func tintedPanel(gapTop: CGFloat, margin: CGFloat, pad: CGFloat,
                     radius: CGFloat = 0, bg: UIColor,
                     content: (MockBuilder) -> Void) {
        let laid = layoutPanel(gapTop: gapTop, margin: margin, pad: pad, content: content)
        if radius > 0 {
            ops.append(.round(laid.outer, radius, bg))
        } else {
            ops.append(.fill(laid.outer, bg))
        }
        merge(laid.child, dx: laid.outer.minX + pad, dy: laid.innerY)
        y = laid.outer.maxY
    }

    /// 描边圆圈 + 符号（光大「转账成功」那个白底紫圈 ✓）
    func outlinedCircleBadge(diameter: CGFloat, stroke: UIColor, strokeWidth: CGFloat,
                             symbol: String, symbolColor: UIColor, symbolSize: CGFloat,
                             gapTop: CGFloat) {
        y += gapTop
        let cx = width / 2
        let cy = y + diameter / 2
        // 外圈整圆 + 内圈白圆 = 描边圆环（枚举 case 无标签，按位置传）
        let outer: CGFloat = diameter / 2
        let inner: CGFloat = diameter / 2 - strokeWidth
        ops.append(.circle(cx, cy, outer, stroke))
        ops.append(.circle(cx, cy, inner, MockInk.white))
        let baseline = cy + ascender(symbolSize) / 2 - symbolSize * 0.06
        ops.append(.center(symbol, width / 2, baseline, symbolSize, symbolColor, false))
        y += diameter
    }

    /// 左右并排两个按钮：左描边、右实心（光大「查看回单 / 再转一笔」）
    func sideButtons(_ left: String, _ right: String, color: UIColor,
                     height: CGFloat, padH: CGFloat, gapTop: CGFloat, size: CGFloat) {
        y += gapTop
        let gapW = dp(8)
        let w = (width - padH * 2 - gapW) / 2
        let leftRect = CGRect(x: padH, y: y, width: w, height: height)
        let rightRect = CGRect(x: padH + w + gapW, y: y, width: w, height: height)
        ops.append(.round(leftRect, dp(4), MockInk.white))
        ops.append(.roundStroke(leftRect, dp(4), color, dp(1)))
        ops.append(.round(rightRect, dp(4), color))
        let baseline = y + height / 2 + ascender(size) / 2 - size * 0.06
        ops.append(.center(left, leftRect.midX, baseline, size, color, false))
        ops.append(.center(right, rightRect.midX, baseline, size, MockInk.white, false))
        y += height
    }

    /// 左右均分的标签行（「储蓄账户 / 信用卡 / 其他账户」）
    func tabsRow(_ titles: [String], selected: Int, size: CGFloat,
                 padH: CGFloat, padTop: CGFloat, padBottom: CGFloat,
                 selectedColor: UIColor = MockInk.title,
                 normalColor: UIColor = MockInk.grey) {
        guard !titles.isEmpty else { return }
        let lh = lineHeight(size)
        let top = y + padTop
        let cellW = (width - padH * 2) / CGFloat(titles.count)
        let baseline = top + ascender(size)
        for (index, title) in titles.enumerated() {
            let isSelected = index == selected
            let cx: CGFloat = padH + cellW * (CGFloat(index) + 0.5)
            // 枚举 case 的最后一个参数是位置 Bool，不能写 `bold:`
            ops.append(.center(title, cx, baseline, size,
                               isSelected ? selectedColor : normalColor, isSelected))
        }
        y = top + lh + padBottom
    }

    /// 一行等分的文字按钮（招商蓝卡底部「详情 / 明细 / 特约取款 / 转账」）
    func actionCells(_ titles: [String], size: CGFloat, color: UIColor, padTop: CGFloat) {
        guard !titles.isEmpty else { return }
        let lh = lineHeight(size)
        let top = y + padTop
        let cellW = width / CGFloat(titles.count)
        let baseline = top + ascender(size)
        for (index, title) in titles.enumerated() {
            ops.append(.center(title, cellW * (CGFloat(index) + 0.5), baseline, size, color, false))
        }
        y = top + lh
    }

    /// 左对齐的一行（余额页「可用余额(元)」+ 大数字）
    func leftLine(_ text: String, size: CGFloat, color: UIColor, bold: Bool = false,
                  padH: CGFloat, padTop: CGFloat, padBottom: CGFloat,
                  key: String? = nil) {
        let lh = lineHeight(size, bold: bold)
        let top = y + padTop
        ops.append(.text(text, padH, top + ascender(size, bold: bold), size, color, bold))
        if let key {
            hits.append(MockHit(rect: CGRect(x: 0, y: top, width: width, height: lh),
                                token: "mock:field:\(key)"))
        }
        y = top + lh + padBottom
    }

    /// 折行段落（招商「e账户」那段长文案）
    func paragraph(_ text: String, size: CGFloat, color: UIColor,
                   padH: CGFloat, padTop: CGFloat, padBottom: CGFloat) {
        let lh = lineHeight(size)
        let lines = wrap(text, maxWidth: width - padH * 2, size: size)
        var baseline = y + padTop + ascender(size)
        for line in lines {
            ops.append(.text(line, padH, baseline, size, color, false))
            baseline += lh
        }
        // 最后一行占用的底边 = 最后一行的基线 - ascender + 行高
        y = baseline - ascender(size) + lh + padBottom
    }

    /// 有色底头部（招商账户页的深蓝条：`‹` + 标题 + `⋯`）
    func coloredHeaderBar(_ title: String, bg: UIColor, fg: UIColor,
                          trailing: String? = nil) {
        let h = metrics.titleBarH
        let size = dp(16)
        ops.append(.fill(CGRect(x: 0, y: y, width: width, height: h), bg))
        let baseline = y + h / 2 + ascender(size) / 2 - size * 0.06
        ops.append(.text("‹", dp(10), baseline, dp(22), fg, false))
        ops.append(.center(title, width / 2, baseline, size, fg, false))
        if let trailing {
            ops.append(.right(trailing, width - dp(10), baseline, dp(18), fg, false))
        }
        y += h
    }

    /// 只有居中标题的头部（招商「电子回单」那种）
    func centeredHeader(_ title: String, size: CGFloat, color: UIColor,
                        padTop: CGFloat, padBottom: CGFloat) {
        let lh = lineHeight(size)
        let top = y + padTop
        ops.append(.center(title, width / 2, top + ascender(size), size, color, false))
        y = top + lh + padBottom
    }

    /// 一行「左侧整宽切图 + 右侧竖版二维码」的品牌条（受理单顶部）
    func brandRow(logo: String, logoHeight: CGFloat, qr: String,
                  qrSize: CGSize, padH: CGFloat) {
        let h = max(logoHeight, qrSize.height)
        let qrX = width - padH - qrSize.width
        ops.append(.image(qr, CGRect(x: qrX, y: y + (h - qrSize.height) / 2,
                                     width: qrSize.width, height: qrSize.height)))
        let logoWidth = max(1, qrX - padH - dp(8))
        ops.append(.image(logo, CGRect(x: padH, y: y + (h - logoHeight) / 2,
                                       width: logoWidth, height: logoHeight)))
        y += h
    }

    /// 居中的「A   |   B」双链接（邮储转账结果页的「继续转账 | 转账记录」）
    func dualLinks(_ left: String, _ right: String, size: CGFloat,
                   color: UIColor, separatorColor: UIColor, padBottom: CGFloat) {
        let lh = lineHeight(size)
        let leftW = Draw.width(left, size: size)
        let rightW = Draw.width(right, size: size)
        let sepW = Draw.width("   |   ", size: size)
        let total = leftW + sepW + rightW
        let startX = (width - total) / 2
        let baseline = y + ascender(size)
        ops.append(.text(left, startX, baseline, size, color, false))
        ops.append(.text("   |   ", startX + leftW, baseline, size, separatorColor, false))
        ops.append(.text(right, startX + leftW + sepW, baseline, size, color, false))
        y += lh + padBottom
    }
}
