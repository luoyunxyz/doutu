import UIKit

/// 「花呗」页 —— `features/zhi/ui/HuabeiV2Screen.java` 的 1:1 移植（参考画布 1280×2816，内容高 5805）。
///
/// 这一页几乎全是**整宽切图**（6 条，从上往下贴着拼），只有 7 处是「用户改过才盖掉重画」的
/// 动态文字。因此移植的重点不是画，而是：
///   · 6 条切图各自的 y 位置（0 / 960 / 1470 / 1935 / 2790 / 3660）
///   · 动态补丁的位置与底色（盖在原图上再画字，与 Java 的 `patch()` 一致）
///   · 热区：Java 在 `registerHits()` 里按顺序登记，命中时**后登记的优先**
final class HuabeiV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    /// 整页内容高度（瀑布流到参考图最下面）
    static let contentH: CGFloat = 5805

    final class State: ScreenState {
        /// 用户改过的数字（键形如 hb.credit）
        var nums: [String: Double] = [:]
        /// 用户改过的文字（键形如 hb.status）
        var texts: [String: String] = [:]
        /// 当前月份（默认取系统日期；参考图是 10 月）
        var month: Int = HuabeiV2Screen.currentMonth()
        var nextMonth: Int = HuabeiV2Screen.nextMonth()
    }

    // MARK: - 调色板（Java:52-58）
    private let pageBg = Draw.color(0xfff2f3f7)
    private let blue = Draw.color(0xff1777ff)
    private let blueText = Draw.color(0xffb7e1ff)
    private let labelText = Draw.color(0xffcfe0ff)
    private let pillText = Draw.color(0xffbce3ff)
    private let yellow = Draw.color(0xffffe956)
    private let yellowInk = Draw.color(0xff4a4a3c)

    /// 参考图里那两处月份文案（当前正好是 10 月时不必盖）
    private static let refLabel = "10月应还(元)"
    private static let refPill = "11月已累计147.88，查看我的账单"

    /// 6 条整宽切图与它们的 y 位置（Java:107-112）
    private static let strips: [(String, CGFloat)] = [
        ("hb_top", 0), ("hb_banner", 960), ("hb_icons", 1470),
        ("hb_qinmi", 1935), ("hb_tabs", 2790), ("hb_feed", 3660),
    ]
    private var art: [String: UIImage] = [:]

    /// 热区（Java:194-231）：按登记顺序，命中时后登记的优先
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private let hits: [Hit] = [
        Hit(rect: CGRect(x: 30, y: 158, width: 98, height: 128), token: "back"),
        Hit(rect: CGRect(x: 1022, y: 170, width: 100, height: 100), token: "noop"),
        Hit(rect: CGRect(x: 1158, y: 166, width: 110, height: 108), token: "noop"),
        Hit(rect: CGRect(x: 506, y: 330, width: 270, height: 60), token: "text:hb.repayLabel"),
        Hit(rect: CGRect(x: 524, y: 398, width: 234, height: 88), token: "text:hb.status"),
        Hit(rect: CGRect(x: 282, y: 548, width: 668, height: 78), token: "num:hb.accrued"),
        Hit(rect: CGRect(x: 88, y: 802, width: 304, height: 52), token: "num:hb.repayDay"),
        Hit(rect: CGRect(x: 514, y: 804, width: 252, height: 52), token: "num:hb.credit"),
        Hit(rect: CGRect(x: 895, y: 802, width: 289, height: 52), token: "num:hb.qinmi"),
        Hit(rect: CGRect(x: 455, y: 1322, width: 355, height: 84), token: "noop"),
        Hit(rect: CGRect(x: 50, y: 1440, width: 1180, height: 470), token: "noop"),
        Hit(rect: CGRect(x: 940, y: 2010, width: 275, height: 80), token: "noop"),
        Hit(rect: CGRect(x: 80, y: 2160, width: 1135, height: 160), token: "noop"),
        Hit(rect: CGRect(x: 430, y: 2590, width: 420, height: 120), token: "noop"),
        Hit(rect: CGRect(x: 60, y: 2790, width: 580, height: 130), token: "noop"),
        Hit(rect: CGRect(x: 645, y: 2790, width: 575, height: 130), token: "noop"),
        Hit(rect: CGRect(x: 40, y: 2985, width: 1190, height: 95), token: "noop"),
        Hit(rect: CGRect(x: 40, y: 3110, width: 1210, height: 300), token: "noop"),
        Hit(rect: CGRect(x: 40, y: 3430, width: 1210, height: 2375), token: "noop"),
        Hit(rect: CGRect(x: 688, y: 3750, width: 296, height: 54), token: "num:hb.followers"),
    ]

    init() {
        for (res, _) in Self.strips {
            if let image = UIImage(named: res) { art[res] = image }
        }
    }

    static func currentMonth() -> Int { Calendar.current.component(.month, from: Date()) }
    static func nextMonth() -> Int {
        let m = currentMonth()
        return m == 12 ? 1 : m + 1
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.H) }

    // MARK: - 绘制（Java:98-120）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)
        for (res, y) in Self.strips {
            guard let image = art[res] else { continue }
            let height = image.size.height * image.scale
            // 整宽贴着放（Java 的 drawBitmap(bmp, x, y) 是原始尺寸，不缩放）
            d.art(image, CGRect(x: 0, y: y, width: image.size.width * image.scale, height: height))
        }
        drawDynamic(d, s)
        d.ctx.restoreGState()
    }

    /// 7 处动态内容（Java:122-177）
    private func drawDynamic(_ d: Draw, _ s: State) {
        // ① 「N月应还(元)」
        let label = s.texts["hb.repayLabel"] ?? "\(s.month)月应还(元)"
        if label != Self.refLabel {
            d.rect(CGRect(x: 300, y: 326, width: 980 - 300, height: 392 - 326), blue)
            d.centerText(label, cx: Self.W / 2, baseline: 381, size: 44, labelText)
        }

        // ② 「已还清」这一行整页居中
        if let status = s.texts["hb.status"] {
            d.rect(CGRect(x: 120, y: 394, width: 1160 - 120, height: 490 - 394), blue)
            d.centerText(status, cx: Self.W / 2, baseline: 474, size: 74, UIColor.white, bold: true)
        }

        // ③ 账单胶囊「M月已累计…」
        let accrued = Self.money(s.nums["hb.accrued"] ?? 147.88)
        let pill = "\(s.nextMonth)月已累计\(accrued)，查看我的账单"
        if pill != Self.refPill {
            d.rect(CGRect(x: 282, y: 556, width: 948 - 282, height: 620 - 556), blue)
            d.text(pill, x: 289, baseline: 606, size: 42, pillText)
        }

        // ④ 还款日每月 N 日
        if let day = s.nums["hb.repayDay"] {
            d.rect(CGRect(x: 88, y: 802, width: 392 - 88, height: 854 - 802), blue)
            let head = "还款日每月"
            d.text(head, x: 97, baseline: 845, size: 41, blueText)
            var x = 97 + Draw.width(head, size: 41)
            let number = Self.plain(day)
            d.text(number, x: x, baseline: 845, size: 41, blueText)
            x += Draw.width(number, size: 41)
            d.text("日", x: x, baseline: 845, size: 41, blueText)
        }

        // ⑤ 总计额度 N 可用
        if let credit = s.nums["hb.credit"] {
            d.rect(CGRect(x: 514, y: 804, width: 766 - 514, height: 856 - 804), blue)
            let value = Self.money(credit)
            d.text(value, x: 524, baseline: 847, size: 44, blueText)
            d.text("可用", x: 524 + Draw.width(value, size: 44), baseline: 847, size: 44, blueText)
        }

        // ⑥ 亲密度 N+权益待解锁
        if let qinmi = s.nums["hb.qinmi"] {
            d.rect(CGRect(x: 895, y: 802, width: 1184 - 895, height: 854 - 802), blue)
            let value = Self.plain(qinmi)
            d.text(value, x: 905, baseline: 845, size: 41, blueText)
            d.text("+权益待解锁", x: 905 + Draw.width(value, size: 41), baseline: 845,
                   size: 41, blueText)
        }

        // ⑦ 吃谷社区关注数
        if let followers = s.nums["hb.followers"] {
            d.rect(CGRect(x: 688, y: 3750, width: 984 - 688, height: 3804 - 3750), yellow)
            let value = Self.plain(followers)
            d.text(value, x: 697, baseline: 3795, size: 41, yellowInk)
            d.text("人已关注", x: 697 + Draw.width(value, size: 41), baseline: 3795,
                   size: 41, yellowInk)
        }
    }

    // MARK: - 命中（Java:185-192：后登记的优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let cy = y + s.scroll
        for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: cy)) {
            return hit.token
        }
        return "noop"
    }

    // MARK: - 数值格式（与 Android `AssetScreen.formatMoney/formatPlain` 一致）

    /// 金额：两位小数 + 千分位
    static func money(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.groupingSeparator = ","
        return f.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }

    /// 整数：千分位、无小数
    static func plain(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.maximumFractionDigits = 0
        f.groupingSeparator = ","
        return f.string(from: NSNumber(value: value)) ?? String(Int(value))
    }
}
