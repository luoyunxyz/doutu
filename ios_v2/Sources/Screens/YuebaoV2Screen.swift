import UIKit

/// 「余额宝」页 —— `features/zhi/ui/YuebaoV2Screen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 结构：顶部一条橙色底 + 5 张整宽/小切图贴回参考坐标 + 9 处「用户改过才盖掉重画」的数值文字
/// + 26 个热区（含最后登记的七日年化胶囊，压在标题之上）。
///
/// 有一个小机关：**余额不为 0 时**「转出」按钮借用「转入」那张切图点亮（Java:131-141），
/// 余额为 0 就什么都不画，露出位图里「浅底橙字」的常态。
final class YuebaoV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let contentH: CGFloat = 2816

    final class State: ScreenState {
        var nums: [String: Double] = [:]
        var texts: [String: String] = [:]
    }

    // MARK: - 调色板（Java:50-55）
    private let pageBg = Draw.color(0xfff5f5f5)
    private let orange = Draw.color(0xffed7449)
    private let pillBg = Draw.color(0xffee8156)
    private let warm = Draw.color(0xfffcf2e9)
    private let ink = Draw.color(0xff282828)
    private let red = Draw.color(0xffb04044)

    private let topOrangeH: CGFloat = 340

    /// 「转出」按钮本体、「转入」切图的外框（Java:61-66）
    private let btnOut = CGRect(x: 113, y: 1252, width: 626 - 113, height: 1411 - 1252)
    private let btnArt = CGRect(x: 107, y: 1244, width: 638 - 107, height: 1422 - 1244)

    /// 9 张切图（Java:79-87）
    private static let artNames = [
        "main": "yb_main", "licai": "yb_licai", "disc": "yb_disc", "back": "yb_back",
        "title": "yb_title", "pill": "yb_pill", "tshirt": "yb_tshirt", "dots": "yb_dots",
        "btnIn": "yb_btn_in",
    ]
    private var art: [String: UIImage] = [:]

    /// 26 个热区（Java:143-181），命中时后登记优先
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private let hits: [Hit] = [
        Hit(rect: CGRect(x: 26, y: 158, width: 98, height: 124), token: "back"),
        Hit(rect: CGRect(x: 1010, y: 158, width: 118, height: 100), token: "noop"),
        Hit(rect: CGRect(x: 1130, y: 168, width: 142, height: 80), token: "noop"),
        Hit(rect: CGRect(x: 336, y: 452, width: 78, height: 66), token: "noop"),
        Hit(rect: CGRect(x: 418, y: 452, width: 218, height: 66), token: "noop"),
        Hit(rect: CGRect(x: 100, y: 546, width: 302, height: 124), token: "num:yb.total"),
        Hit(rect: CGRect(x: 140, y: 786, width: 262, height: 72), token: "num:yb.avail"),
        Hit(rect: CGRect(x: 665, y: 786, width: 263, height: 72), token: "num:yb.zan"),
        Hit(rect: CGRect(x: 1074, y: 788, width: 88, height: 78), token: "noop"),
        Hit(rect: CGRect(x: 88, y: 998, width: 316, height: 66), token: "noop"),
        Hit(rect: CGRect(x: 88, y: 1088, width: 224, height: 66), token: "text:yb.yes"),
        Hit(rect: CGRect(x: 480, y: 1086, width: 146, height: 64), token: "num:yb.cum"),
        Hit(rect: CGRect(x: 858, y: 998, width: 196, height: 142), token: "noop"),
        Hit(rect: CGRect(x: 96, y: 1232, width: 546, height: 180), token: "noop"),
        Hit(rect: CGRect(x: 650, y: 1232, width: 532, height: 180), token: "noop"),
        Hit(rect: CGRect(x: 486, y: 1528, width: 306, height: 94), token: "noop"),
        Hit(rect: CGRect(x: 80, y: 1636, width: 220, height: 56), token: "noop"),
        Hit(rect: CGRect(x: 1072, y: 1636, width: 132, height: 56), token: "noop"),
        Hit(rect: CGRect(x: 232, y: 1754, width: 512, height: 64), token: "text:yb.gift"),
        Hit(rect: CGRect(x: 1058, y: 1756, width: 146, height: 62), token: "noop"),
        Hit(rect: CGRect(x: 80, y: 1960, width: 220, height: 62), token: "noop"),
        Hit(rect: CGRect(x: 928, y: 1960, width: 284, height: 60), token: "noop"),
        Hit(rect: CGRect(x: 80, y: 2100, width: 240, height: 80), token: "noop"),
        Hit(rect: CGRect(x: 1058, y: 2114, width: 150, height: 58), token: "num:yb.huo"),
        Hit(rect: CGRect(x: 80, y: 2300, width: 260, height: 78), token: "noop"),
        Hit(rect: CGRect(x: 1058, y: 2318, width: 152, height: 58), token: "num:yb.ding"),
        Hit(rect: CGRect(x: 466, y: 244, width: 334, height: 86), token: "num:yb.rate"),
    ]

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.H) }

    // MARK: - 绘制（Java:95-125）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: topOrangeH), orange)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        drawAt(d, "main", 0, 336)
        drawAt(d, "licai", 0, 1878)
        drawAt(d, "disc", 0, 2500)

        drawAt(d, "back", 36, 176)
        drawAt(d, "title", 532, 168)
        drawAt(d, "pill", 438, 246)
        drawAt(d, "tshirt", 1024, 166)
        drawAt(d, "dots", 1138, 184)

        drawEdits(d, s)
        drawButtons(d, s)

        d.ctx.restoreGState()
    }

    /// 按原始尺寸贴图（Java 的 drawBitmap(bmp, x, y)）
    private func drawAt(_ d: Draw, _ key: String, _ x: CGFloat, _ y: CGFloat) {
        guard let image = art[key] else { return }
        let size = CGSize(width: image.size.width * image.scale, height: image.size.height * image.scale)
        d.art(image, CGRect(x: x, y: y, width: size.width, height: size.height))
    }

    /// 余额不为 0 时点亮「转出」（Java:131-141）
    private func drawButtons(_ d: Draw, _ s: State) {
        if (s.nums["yb.total"] ?? 0) == 0 { return }
        if let art = art["btnIn"] {
            d.art(art, btnArt)
        }
        d.centerText("转出", cx: (btnOut.minX + btnOut.maxX) / 2, baseline: 1359,
                     size: 68, UIColor.white)
    }

    /// 9 处可改字段（Java:184-231）
    private func drawEdits(_ d: Draw, _ s: State) {
        if let rate = s.nums["yb.rate"] {
            d.rect(CGRect(x: 465, y: 248, width: 820 - 465, height: 302 - 248), pillBg)
            d.text("七日年化", x: 488, baseline: 290, size: 40, UIColor.white)
            let w = Draw.width("七日年化", size: 40)
            let text = String(format: "%.4f%%", rate)
            d.text(text, x: 488 + w, baseline: 290, size: 40, UIColor.white)
            chevron(d, x: 488 + w + Draw.width(text, size: 40) + 16, top: 270, bottom: 302,
                    color: UIColor.white, stroke: 5)
        }
        if let total = s.nums["yb.total"] {
            d.rect(CGRect(x: 104, y: 546, width: 420 - 104, height: 670 - 546), UIColor.white)
            let text = Self.money(total)
            d.text(text, x: 118, baseline: 649, size: 118, ink, bold: true)
            chevron(d, x: 118 + Draw.width(text, size: 118, bold: true) + 26, top: 586, bottom: 630,
                    color: Draw.color(0xffcccccb), stroke: 8)
        }
        if let avail = s.nums["yb.avail"] {
            d.rect(CGRect(x: 148, y: 786, width: 402 - 148, height: 858 - 786), warm)
            d.text("可用", x: 159, baseline: 844, size: 60, ink, bold: true)
            d.text(Self.money(avail), x: 266, baseline: 844, size: 60, ink, bold: true)
        }
        if let zan = s.nums["yb.zan"] {
            d.rect(CGRect(x: 672, y: 786, width: 928 - 672, height: 858 - 786), warm)
            d.text("攒着", x: 683, baseline: 844, size: 60, ink, bold: true)
            d.text(Self.money(zan), x: 795, baseline: 844, size: 60, ink, bold: true)
        }
        if let cum = s.nums["yb.cum"] {
            d.rect(CGRect(x: 490, y: 1086, width: 626 - 490, height: 1152 - 1086), UIColor.white)
            d.text(Self.money(cum), x: 502, baseline: 1139, size: 60,
                   Draw.color(0xff353535), bold: true)
        }
        if let yes = s.texts["yb.yes"] {
            d.rect(CGRect(x: 92, y: 1088, width: 312 - 92, height: 1154 - 1088), UIColor.white)
            d.text(yes, x: 101, baseline: 1142, size: 56, red, bold: true)
        }
        if let gift = s.texts["yb.gift"] {
            d.rect(CGRect(x: 232, y: 1754, width: 744 - 232, height: 1818 - 1754), UIColor.white)
            d.text(gift, x: 243, baseline: 1804, size: 46, ink)
        }
        if let huo = s.nums["yb.huo"] {
            d.rect(CGRect(x: 1058, y: 2114, width: 1208 - 1058, height: 2172 - 2114), UIColor.white)
            d.text(String(format: "%.2f%%", huo), x: 1077, baseline: 2159, size: 46, red, bold: true)
        }
        if let ding = s.nums["yb.ding"] {
            d.rect(CGRect(x: 1058, y: 2318, width: 1210 - 1058, height: 2376 - 2318), UIColor.white)
            d.text(String(format: "%.2f%%", ding), x: 1077, baseline: 2363, size: 46, red, bold: true)
        }
    }

    /// 粗圆头右箭头（Java:294-309）
    private func chevron(_ d: Draw, x: CGFloat, top: CGFloat, bottom: CGFloat,
                         color: UIColor, stroke: CGFloat) {
        let mid = (top + bottom) / 2
        let w = (bottom - top) * 0.62
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: x, y: top))
        d.ctx.addLine(to: CGPoint(x: x + w, y: mid))
        d.ctx.addLine(to: CGPoint(x: x, y: bottom))
        d.ctx.strokePath()
    }

    // MARK: - 命中（Java:239-245：后登记优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let cy = y + s.scroll
        for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: cy)) {
            return hit.token
        }
        return "noop"
    }

    /// 金额：千分位 + 两位小数（与 Android `AssetScreen.formatMoney` 一致）
    static func money(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.groupingSeparator = ","
        return f.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }
}
