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

    /// 首页卡片标题（`LABELS[0...5]`）
    var title: String {
        switch self {
        case .nye: return "农业模拟"
        case .icbc: return "工商模拟"
        case .boc: return "中国模拟"
        case .psbc: return "邮政模拟"
        case .cmb: return "招商模拟"
        case .eank: return "光大模拟"
        }
    }

    /// 编辑页右侧抽屉里的子页名（对应各 Activity 的 `SUBS`）
    var subs: [String] {
        switch self {
        case .nye:
            return ["明细转出1", "明细转出2", "明细转入", "支付宝转入", "明细转出3"]
        case .icbc:
            return ["明细转出1", "明细转出2", "明细转入1", "支付宝转入", "转入2", "转入3"]
        case .boc:
            return ["明细转出", "转出2", "转入1", "支付宝转入", "转入2", "支付宝到账"]
        case .psbc:
            return ["明细转出", "转出2", "转入", "回单", "转账结果"]
        case .cmb:
            return ["明细转出1", "明细转出2", "明细转入1", "明细转入2", "账户", "回单",
                    "明细转出3", "明细转入3", "余额", "信用卡", "理财", "流水"]
        case .eank:
            return ["明细转出", "明细转入", "转账详情"]
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
        }
    }

    /// 这一模块的预览版式是否已移植（`MockComposer` 里有没有分支）。
    var isBuilt: Bool {
        switch self {
        case .nye: return true
        default: return false
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
        titleBarH: dp(52), titleSize: dp(19),
        rowSize: dp(15), rowPadV: dp(12), rowPadH: dp(16),
        statusPadV: dp(14), statusPadH: dp(16), statusSize: dp(15),
        statusCheck: dp(20), statusGap: dp(9),
        nameSize: dp(15), namePadTop: dp(16),
        amountSize: dp(32), amountPadTop: dp(6), amountPadBottom: dp(18),
        cardPadV: dp(14), cardPadH: dp(16), cardLogo: dp(28), cardNoSize: dp(20), cardGap: dp(10),
        amtLabelSize: dp(15), amtSize: dp(28), amtPadV: dp(6), bandH: dp(10),
        linkPadV: dp(16), linkPadH: dp(16), linkSize: dp(15),
        buttonPadV: dp(18), buttonH: dp(48), buttonSize: dp(16),
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
        ops.append(.image(asset, CGRect(x: startX, y: top + (h - metrics.kindIcon) / 2,
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
        ops.append(.image(logo, CGRect(x: metrics.cardPadH, y: top + (max(lh, metrics.cardLogo) - metrics.cardLogo) / 2,
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
}
