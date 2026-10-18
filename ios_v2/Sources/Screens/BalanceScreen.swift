import UIKit

/// 「余额」页 —— `features/zhi/ui/BalanceScreen.java` 的 1:1 移植（参考画布 1280×2816，内容高 4520）。
///
/// 结构：浅青渐变底 + 一张白色柔光面板（圆角 40 的竖向渐变，只在上半页）
/// + 18 张按参考坐标贴回的切图 + 11 处可改字段 + 25 个热区。
///
/// 热区的登记顺序有讲究（Java:153-157 的注释）：**整块卡片先登记，卡片里的小数字/文字后登记**，
/// 因为命中是从后往前找 —— 后登记的优先。点在数字上改数字，点在别处不响应。
final class BalanceScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let contentH: CGFloat = 4520

    final class State: ScreenState {
        var nums: [String: Double] = [:]
        var texts: [String: String] = [:]
    }

    // MARK: - 调色板（Java:57-64）
    private let pageBg = Draw.color(0xffedeef2)
    private let ink = Draw.color(0xff26282b)
    private let caption = Draw.color(0xff8d9299)
    private let titleBlue = Draw.color(0xff17407f)
    private let orangeL = Draw.color(0xfffdb433)
    private let orangeR = Draw.color(0xfffea03d)
    private let blueL = Draw.color(0xff87d5fc)
    private let blueR = Draw.color(0xff78cbfd)

    private let panelTop: CGFloat = 1596
    private let panelBottom: CGFloat = 2260

    /// 18 张切图（Java:84-101）
    private static let artNames = [
        "bg": "bal_bg", "headBack": "bal_head_back", "headTitle": "bal_head_title",
        "headTheme": "bal_head_theme", "headDots": "bal_head_dots", "cardTop": "bal_card_top",
        "miniYuebao": "bal_mini_yuebao", "miniHuabei": "bal_mini_huabei", "miniBank": "bal_mini_bank",
        "youthHead": "bal_youth_head", "l1": "bal_l1", "l2": "bal_l2", "l3": "bal_l3",
        "r1": "bal_r1", "r2": "bal_r2", "r3": "bal_r3",
        "service": "bal_service", "disclaimer": "bal_disclaimer",
    ]
    private var art: [String: UIImage] = [:]

    /// 25 个热区（Java:157-196），命中从后往前
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private let hits: [Hit] = [
        Hit(rect: CGRect(x: 28, y: 156, width: 112, height: 130), token: "back"),
        Hit(rect: CGRect(x: 886, y: 156, width: 240, height: 126), token: "noop"),
        Hit(rect: CGRect(x: 1128, y: 156, width: 146, height: 126), token: "noop"),
        Hit(rect: CGRect(x: 60, y: 760, width: 564, height: 152), token: "withdraw"),
        Hit(rect: CGRect(x: 650, y: 760, width: 554, height: 152), token: "noop"),
        Hit(rect: CGRect(x: 60, y: 996, width: 360, height: 70), token: "noop"),
        Hit(rect: CGRect(x: 686, y: 996, width: 344, height: 72), token: "text:bal.changeNote"),
        Hit(rect: CGRect(x: 1030, y: 996, width: 120, height: 72), token: "num:bal.change"),
        Hit(rect: CGRect(x: 60, y: 462, width: 342, height: 170), token: "num:bal.avail"),
        Hit(rect: CGRect(x: 40, y: 1144, width: 552, height: 416), token: "yuebao"),
        Hit(rect: CGRect(x: 598, y: 1144, width: 552, height: 416), token: "huabei"),
        Hit(rect: CGRect(x: 1152, y: 1144, width: 124, height: 416), token: "noop"),
        Hit(rect: CGRect(x: 650, y: 1422, width: 302, height: 84), token: "num:bal.huabei"),
        Hit(rect: CGRect(x: 282, y: 1180, width: 226, height: 58), token: "num:bal.yuebaoProfit"),
        Hit(rect: CGRect(x: 86, y: 1422, width: 194, height: 84), token: "num:bal.yuebaoTotal"),
        Hit(rect: CGRect(x: 104, y: 1796, width: 488, height: 78), token: "text:p.l1title"),
        Hit(rect: CGRect(x: 30, y: 1738, width: 610, height: 338), token: "noop"),
        Hit(rect: CGRect(x: 108, y: 2560, width: 458, height: 144), token: "text:p.l2t"),
        Hit(rect: CGRect(x: 196, y: 2730, width: 276, height: 104), token: "noop"),
        Hit(rect: CGRect(x: 108, y: 3390, width: 458, height: 78), token: "text:p.l3t"),
        Hit(rect: CGRect(x: 196, y: 3480, width: 276, height: 104), token: "noop"),
        Hit(rect: CGRect(x: 760, y: 2190, width: 376, height: 158), token: "text:p.r1t"),
        Hit(rect: CGRect(x: 816, y: 2392, width: 274, height: 104), token: "noop"),
        Hit(rect: CGRect(x: 672, y: 3170, width: 528, height: 130), token: "text:p.r2t"),
        Hit(rect: CGRect(x: 672, y: 3984, width: 546, height: 134), token: "text:p.r3t"),
        Hit(rect: CGRect(x: 500, y: 4216, width: 280, height: 68), token: "noop"),
    ]

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.H) }

    // MARK: - 绘制（Java:109-151）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        drawAt(d, "bg", 0, 0)
        drawPanel(d)

        drawAt(d, "headBack", 40, 168)
        drawAt(d, "headTitle", 108, 170)
        drawAt(d, "headTheme", 896, 162)
        drawAt(d, "headDots", 1136, 162)

        drawAt(d, "cardTop", 40, 324)

        drawAt(d, "miniYuebao", 40, 1144)
        drawAt(d, "miniHuabei", 598, 1144)
        drawAt(d, "miniBank", 1152, 1144)

        drawAt(d, "youthHead", 56, 1626)
        drawAt(d, "l1", 30, 1738)
        drawAt(d, "r1", 644, 1738)
        drawAt(d, "l2", 30, 2082)
        drawAt(d, "r2", 644, 2556)
        drawAt(d, "l3", 30, 2886)
        drawAt(d, "r3", 644, 3320)

        drawAt(d, "service", 500, 4216)
        drawAt(d, "disclaimer", 250, 4366)

        drawEdits(d, s)

        d.ctx.restoreGState()
    }

    /// 按原始尺寸贴图
    private func drawAt(_ d: Draw, _ key: String, _ x: CGFloat, _ y: CGFloat) {
        guard let image = art[key] else { return }
        d.art(image, CGRect(x: x, y: y,
                            width: image.size.width * image.scale,
                            height: image.size.height * image.scale))
    }

    /// 青春广场上面那层白色柔光：圆角 40 的矩形里铺一条「白 → 透明」的竖向渐变（Java:199-211）
    private func drawPanel(_ d: Draw) {
        let box = CGRect(x: 16, y: panelTop, width: Self.W - 32, height: panelBottom - panelTop)
        d.clipped(to: box, radius: 40) {
            d.gradient(box, from: UIColor.white, to: UIColor.white.withAlphaComponent(0),
                       vertical: true)
        }
    }

    /// 11 处可改字段（Java:214-299）
    private func drawEdits(_ d: Draw, _ s: State) {
        if let avail = s.nums["bal.avail"] {
            d.rect(CGRect(x: 60, y: 468, width: 402 - 60, height: 626 - 468), UIColor.white)
            d.text(Self.money(avail), x: 90, baseline: 608, size: 158, ink, bold: true)
        }
        if s.nums["bal.change"] != nil || s.texts["bal.changeNote"] != nil {
            d.rect(CGRect(x: 686, y: 1000, width: 1146 - 686, height: 1064 - 1000), UIColor.white)
            let note = s.texts["bal.changeNote"] ?? "余额宝-自动转入"
            let amount = Self.money(s.nums["bal.change"] ?? -0.01)
            d.text(note + "，" + amount, x: 697, baseline: 1048, size: 39, caption)
        }
        if let profit = s.nums["bal.yuebaoProfit"] {
            d.rect(CGRect(x: 278, y: 1182, width: 506 - 278, height: 1236 - 1182), UIColor.white)
            d.text("昨日收益:", x: 284, baseline: 1223, size: 31, Draw.color(0xff9aa0a6))
            d.text(Self.money(profit), x: 435, baseline: 1223, size: 31, Draw.color(0xff9aa0a6))
        }
        if let total = s.nums["bal.yuebaoTotal"] {
            // 橙卡上是横向渐变，用同色渐变盖住（Java 的 patchGrad）
            d.gradient(CGRect(x: 86, y: 1426, width: 280 - 86, height: 1502 - 1426),
                       from: orangeL, to: orangeR, vertical: false)
            d.text("¥", x: 100, baseline: 1480, size: 40, UIColor.white)
            d.text(Self.money(total), x: 134, baseline: 1489, size: 68, UIColor.white, bold: true)
        }
        if let huabei = s.nums["bal.huabei"] {
            d.gradient(CGRect(x: 650, y: 1426, width: 950 - 650, height: 1502 - 1426),
                       from: blueL, to: blueR, vertical: false)
            d.text("¥", x: 660, baseline: 1480, size: 40, UIColor.white)
            d.text(Self.money(huabei), x: 694, baseline: 1489, size: 68, UIColor.white, bold: true)
        }
        if let title = s.texts["p.l1title"] {
            d.rect(CGRect(x: 104, y: 1798, width: 590 - 104, height: 1872 - 1798), UIColor.white)
            d.text(title, x: 119, baseline: 1854, size: 43, titleBlue, bold: true)
        }
        if let text = s.texts["p.l2t"] {
            d.rect(CGRect(x: 108, y: 2562, width: 566 - 108, height: 2702 - 2562), UIColor.white)
            let lines = Self.splitLines(text, ["开通我的专属零花钱", "续费快乐假期"])
            d.text(lines[0], x: 122, baseline: 2620, size: 44, ink)
            d.text(lines[1], x: 206, baseline: 2684, size: 44, ink)
        }
        if let text = s.texts["p.l3t"] {
            d.rect(CGRect(x: 108, y: 3392, width: 566 - 108, height: 3466 - 3392), UIColor.white)
            d.text(text, x: 122, baseline: 3450, size: 44, ink)
        }
        if let text = s.texts["p.r1t"] {
            d.rect(CGRect(x: 760, y: 2192, width: 1134 - 760, height: 2346 - 2192), UIColor.white)
            let lines = Self.splitLines(text, ["零花钱轻松到手", "表情包帮我开口"])
            d.text(lines[0], x: 783, baseline: 2247, size: 46, ink)
            d.text(lines[1], x: 781, baseline: 2325, size: 46, ink)
        }
        if let text = s.texts["p.r2t"] {
            d.rect(CGRect(x: 672, y: 3172, width: 1198 - 672, height: 3298 - 3172), UIColor.white)
            let lines = Self.splitLines(text, ["奶龙花语秘境皮肤套装，", "有效期180天"])
            d.text(lines[0], x: 683, baseline: 3238, size: 44, ink)
            d.text(lines[1], x: 683, baseline: 3283, size: 44, ink)
        }
        if let text = s.texts["p.r3t"] {
            d.rect(CGRect(x: 672, y: 3986, width: 1216 - 672, height: 4116 - 3986), UIColor.white)
            let lines = Self.splitLines(text, ["聚会分账的3个潜规则，", "99%的人都不知道（附…"])
            d.text(lines[0], x: 683, baseline: 4045, size: 44, ink)
            d.text(lines[1], x: 683, baseline: 4105, size: 44, ink)
        }
    }

    /// 按换行拆成两行，不够就用默认（Java:301-305）
    private static func splitLines(_ value: String, _ def: [String]) -> [String] {
        let parts = value.components(separatedBy: "\n")
        let first = parts.count > 0 ? parts[0] : def[0]
        let second = parts.count > 1 ? parts[1] : def[1]
        return [first, second]
    }

    // MARK: - 命中（Java:314-320：后登记优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let cy = y + s.scroll
        for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: cy)) {
            return hit.token
        }
        return "noop"
    }

    /// 金额：千分位 + 两位小数
    static func money(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.groupingSeparator = ","
        return f.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }
}
