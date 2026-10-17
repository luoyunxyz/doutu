import UIKit

/// 「收支分析」页 —— `features/zhi/ui/BillAnalyticsScreen.java` 的 1:1 移植（1280×2816）。
///
/// 三个页签（周度 / 月度 / 年度）各自一整页，顶部固定栏不随滚动移动。
/// 数据层在 `BillAnalyticsModel.swift`（分类聚合、环形图分片、笔数、三优先级取值）。
///
/// 注意：年度页（`drawYear` 之后）尚未移植完 —— 目前只画期间行与大卡，
/// 分类/对比/城市/排行/支出方式/省钱建议待补。周度与月度两页是完整的。
final class BillAnalyticsScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let navHeight: CGFloat = 300

    final class State: ScreenState {
        var tab = 0
        var income = false
        var weekOffset = 0
        var monthOffset = 0
        var yearOffset = 0
        var showAll = false
        var expanded = 0
        var compare = false
        var noticeClosed = false
        var investIncluded = false
    }

    // 调色板
    private let pageBg = Draw.color(0xfff2f3f7)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff2a2c30)
    private let grey = Draw.color(0xff8b929b)
    private let blue = Draw.color(0xff1777ff)
    private let lightBlue = Draw.color(0xffa9c9f6)
    private let tile = Draw.color(0xfff6f7fb)
    private let pill = Draw.color(0xffeff1f5)
    private let hilite = Draw.color(0xffd9e8fd)
    private let green = Draw.color(0xff1a9c72)
    private let slate = Draw.color(0xff3a3f46)
    private let card = Draw.color(0xffeaf2fb)

    private var art: [String: UIImage] = [:]
    private var model = BillAnalyticsModel()
    private struct Hot { let rect: CGRect; let token: ActionToken }
    private var hots: [Hot] = []

    init() {
        let mapping = [
            "mascot": "stat_mascot", "hero_week": "stat_hero_week",
            "hero_month": "stat_hero_month", "hero_year": "stat_hero_year",
            "week_sum": "stat_week_sum", "week_rank": "stat_week_rank",
            "week_cat": "stat_week_cat", "month": "stat_month_art", "year": "stat_year_art",
            "cmb": "stat_bank_cmb", "huabei": "stat_bank_huabei",
            "payHuabei": "stat_pay_huabei", "payCmb": "stat_pay_cmb",
            "phone": "stat_cat_phone", "coin": "stat_more_coin", "moreIn": "stat_more_income",
        ]
        for (key, res) in mapping {
            if let image = UIImage(named: res) { art[key] = image }
        }
        for i in 0..<6 {
            let names = ["stat_food_zhengcan", "stat_food_qita", "stat_food_zhaji",
                         "stat_food_shengxian", "stat_food_kafei", "stat_food_lingshi"]
            if let image = UIImage(named: names[i]) { art["f\(i)"] = image }
        }
    }

    private func hot(_ rect: CGRect, _ token: ActionToken) {
        hots.append(Hot(rect: rect, token: token))
    }

    // MARK: - 内容高度（Java:360-379）

    private func contentHeight(_ s: State) -> CGFloat {
        switch s.tab {
        case 0: return 4030
        case 1: return s.income ? 4230 : 6710 + monthExtra(s)
        default: return s.income ? 4210 : 9380
        }
    }

    private func monthCalendarRows(_ s: State) -> Int {
        guard s.showAll else { return 2 }
        var comps = Calendar.current.dateComponents([.year, .month], from: Date())
        comps.month = (comps.month ?? 1) + s.monthOffset
        guard let first = Calendar.current.date(from: comps),
              let range = Calendar.current.range(of: .day, in: .month, for: first) else { return 2 }
        let firstDow = Calendar.current.component(.weekday, from: first) - 1
        return Int(ceil(Double(firstDow + range.count) / 7.0))
    }

    private func monthExtra(_ s: State) -> CGFloat {
        CGFloat(max(0, monthCalendarRows(s) - 2)) * 166
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return max(0, contentHeight(s) - (Self.H - Self.navHeight))
    }

    // MARK: - 绘制入口（Java:341-354）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        hots.removeAll()
        model = BillAnalyticsModel()
        model.syncLedger(monthOffset: s.monthOffset, yearOffset: s.yearOffset)

        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)
        s.scroll = max(0, min(maxScroll(s), s.scroll))

        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: Self.navHeight, width: Self.W,
                              height: Self.H - Self.navHeight))
        d.ctx.translateBy(x: 0, y: -s.scroll)
        switch s.tab {
        case 0: drawWeek(d, s)
        case 1: drawMonth(d, s)
        default: drawYear(d, s)
        }
        d.ctx.restoreGState()

        drawNav(d, s)
    }

    /// 顶部固定栏（Java:382-408）
    private func drawNav(_ d: Draw, _ s: State) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.navHeight), pageBg)

        d.ctx.saveGState()
        d.ctx.setStrokeColor(ink.cgColor)
        d.ctx.setLineWidth(6)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: 100, y: 146))
        d.ctx.addLine(to: CGPoint(x: 48, y: 178))
        d.ctx.addLine(to: CGPoint(x: 100, y: 210))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        hot(CGRect(x: 30, y: 140, width: 100, height: 90), "back")

        let tabs = ["周度", "月度", "年度"]
        let cx: [CGFloat] = [435, 690, 945]
        for i in 0..<3 {
            let on = s.tab == i
            d.centerText(tabs[i], cx: cx[i], baseline: 250, size: 52, ink, bold: on)
            if on {
                d.round(CGRect(x: cx[i] - 48, y: 274, width: 96, height: 9), radius: 4, ink)
            }
            hot(CGRect(x: cx[i] - 80, y: 160, width: 160, height: 136), "tab:\(i)")
        }
        for i in 0..<3 {
            d.circle(cx: 1160 + CGFloat(i) * 28, cy: 208, r: 8, ink)
        }
        hot(CGRect(x: 1120, y: 165, width: 130, height: 85), "more")
    }

    /// 期间行（Java:411-453）
    private func periodRow(_ d: Draw, _ s: State, top: CGFloat) {
        let mid = top + 62
        let calendar = Calendar.current
        if s.tab == 0 {
            let monday = Self.mondayOf(Date(), offset: s.weekOffset)
            let sunday = calendar.date(byAdding: .day, value: 6, to: monday) ?? monday
            let mc = calendar.dateComponents([.month, .day], from: monday)
            let sc = calendar.dateComponents([.month, .day], from: sunday)
            let label = "本周\(mc.month ?? 1)月\(mc.day ?? 1)日-\(sc.month ?? 1)月\(sc.day ?? 1)日"
            d.circle(cx: 116, cy: mid, r: 40, Draw.color(0xffe4e9f0))
            triangle(d, cx: 116, cy: mid, size: 22, color: Draw.color(0xff3a3f46), right: false)
            hot(CGRect(x: 72, y: mid - 44, width: 88, height: 88), "week:prev")
            d.circle(cx: 669, cy: mid, r: 40, Draw.color(0xffe4e9f0))
            triangle(d, cx: 669, cy: mid, size: 22, color: Draw.color(0xff9aa0a8), right: true)
            hot(CGRect(x: 625, y: mid - 44, width: 88, height: 88), "week:next")
            d.text(label, x: 180, baseline: mid + 16, size: 38, ink, bold: true)
        } else if s.tab == 1 {
            let comps = calendar.dateComponents([.year, .month],
                                                from: calendar.date(byAdding: .month, value: s.monthOffset,
                                                                    to: Date()) ?? Date())
            d.text("\(comps.year ?? 2026)年\(comps.month ?? 1)月", x: 88, baseline: mid + 18,
                   size: 48, ink, bold: true)
            caret(d, cx: 372, cy: mid + 4, color: ink)
            hot(CGRect(x: 80, y: mid - 44, width: 340, height: 88), "month:pick")
        } else {
            let comps = calendar.dateComponents([.year],
                                                from: calendar.date(byAdding: .year, value: s.yearOffset,
                                                                    to: Date()) ?? Date())
            d.text("\(comps.year ?? 2026)年", x: 88, baseline: mid + 18, size: 48, ink, bold: true)
            caret(d, cx: 300, cy: mid + 4, color: ink)
            hot(CGRect(x: 80, y: mid - 44, width: 260, height: 88), "year:pick")
        }

        // 支出 / 收入 开关
        let sw: CGFloat = 170, sh: CGFloat = 104, x0: CGFloat = 880, y0 = mid - 52
        let labels = ["支出", "收入"]
        for i in 0..<2 {
            let on = (i == 1) == s.income
            if on {
                d.round(CGRect(x: x0 + CGFloat(i) * sw, y: y0, width: sw, height: sh),
                        radius: sh / 2, slate)
            }
            d.centerText(labels[i], cx: x0 + CGFloat(i) * sw + sw / 2, baseline: y0 + 64,
                         size: 42, on ? .white : ink, bold: true)
            hot(CGRect(x: x0 + CGFloat(i) * sw, y: y0, width: sw, height: sh),
                "tab:\(s.tab):mode:\(i)")
        }
    }

    /// 大卡片（Java:468-515）
    private func bigCard(_ d: Draw, _ s: State, top: CGFloat) {
        let h: CGFloat = 386, l: CGFloat = 41, r: CGFloat = 1237
        let key = s.tab == 0 ? "hero_week" : (s.tab == 1 ? "hero_month" : "hero_year")
        let box = CGRect(x: l, y: top, width: r - l, height: h)
        if let plateImage = art[key] {
            d.clipped(to: box, radius: 40) { d.art(plateImage, box) }
        } else {
            let colors: [UInt32] = s.tab == 1
                ? [0xfffbe0bb, 0xfff7d6ab, 0xfffdf2e3]
                : [0xffd2e9f9, 0xffe8f4fd, 0xfffbfdff]
            d.clipped(to: box, radius: 40) {
                d.multiGradient(box, colors: colors, stops: [0, 0.45, 1], vertical: true)
            }
        }

        var tot = model.total(tab: Self.tabOf(s.tab))
        var cnt = model.totalCount(tab: Self.tabOf(s.tab))
        if s.income && s.tab == 1 {
            tot = model.value("mi.b.4", 0)
            cnt = model.count("mi.n", 0)
        } else if s.income && s.tab == 2 {
            tot = model.value("yi.total", yearIncomeTotal(s))
            cnt = model.count("yi.n", 9)
        } else {
            tot = model.value("big.total.\(s.tab)", tot)
            cnt = model.count("big.cnt.\(s.tab)", cnt)
        }

        d.text(s.income ? "收入" : "支出", x: 95, baseline: top + 79, size: 42,
               Draw.color(0xff5a7280))
        d.textSqueezed(Self.money(tot), x: 94, baseline: top + 242, size: 80,
                       Draw.color(0xff1b262c), bold: true, squeeze: 0.88)
        d.text("共\(cnt)笔  查看统计设置 ›", x: 95, baseline: top + 320, size: 41,
               Draw.color(0xff768485))

        if s.tab == 0 {
            d.text("较上周", x: 634, baseline: top + 128, size: 40, Draw.color(0xff3a3f46))
            d.textSqueezed("↑" + Self.trim2(model.value("w.prev", 20.00)) + "元", x: 768,
                           baseline: top + 127, size: 40, Draw.color(0xffc43f42),
                           bold: true, squeeze: 0.84)
            hot(CGRect(x: 620, y: top + 86, width: 595, height: 74), "w.prev")
        }
        hot(CGRect(x: 90, y: top + 170, width: 630, height: 106), "big:total")
        hot(CGRect(x: 90, y: top + 284, width: 630, height: 60), "big:cnt")
    }

    private func yearIncomeTotal(_ s: State) -> Double {
        var total = 0.0
        for i in 0..<12 {
            let fallback: Double = i == 3 ? 20.00 : (i == 4 ? 21.48 : (i == 5 ? 1666.00
                                            : (i == 7 ? 1400.01 : 0)))
            total += model.value("yi.m.\(i)", fallback)
        }
        return total
    }

    // MARK: - 周度（Java:520-603）

    private func drawWeek(_ d: Draw, _ s: State) {
        periodRow(d, s, top: 340)
        bigCard(d, s, top: 502)
        let slices = model.slices(tab: .week)
        weekSummary(d, s, top: 944)
        weekRank(d, s, top: 2035)
        weekCategory(d, s, top: 2535, slices: slices)
    }

    private func weekSummary(_ d: Draw, _ s: State, top: CGFloat) {
        plate(d, "week_sum", top: top, height: 999)
        let baseline = top + 670
        d.rect(CGRect(x: 82, y: baseline - 1, width: 1216 - 82, height: 2),
               Draw.color(0xffd5d8dc))

        var current = [model.value("w.c.0", 20.00), 0, 0, 0, 0, 0, 0]
        var mx = 1.0
        for v in current { mx = max(mx, v) }
        let plotH: CGFloat = 425
        for i in 0..<7 {
            let lx = 138.5 + 159.6 * CGFloat(i)
            let dx = lx + 44
            d.circle(cx: lx, cy: baseline, r: 11, Draw.color(0xffa8cbf5))
            if current[i] > 0 {
                let bh = CGFloat(current[i] / mx) * plotH
                d.round(CGRect(x: dx - 15.5, y: baseline - bh, width: 31, height: bh),
                        radius: 15.5, Draw.color(0xff2b86ff))
                hot(CGRect(x: dx - 24, y: baseline - bh - 20, width: 48, height: bh + 20),
                    "w.bar:\(i)")
            } else {
                d.circle(cx: dx, cy: baseline, r: 12, Draw.color(0xff2b86ff))
            }
        }

        let monday = Self.mondayOf(Date(), offset: s.weekOffset)
        let today = Calendar.current.startOfDay(for: Date())
        let pw: CGFloat = 153, step: CGFloat = 163.5, px: CGFloat = 82
        let py = top + 845, phh: CGFloat = 152
        for i in 0..<7 {
            let day = Calendar.current.date(byAdding: .day, value: i, to: monday) ?? monday
            let isToday = Calendar.current.startOfDay(for: day) == today
            let x = px + CGFloat(i) * step
            d.round(CGRect(x: x, y: py, width: pw, height: phh), radius: 26,
                    isToday ? Draw.color(0xffa0c9ff) : Draw.color(0xffecf3fb))
            let comps = Calendar.current.dateComponents([.month, .day], from: day)
            let label = isToday ? "今天"
                : String(format: "%d.%02d", comps.month ?? 1, comps.day ?? 1)
            d.centerText(label, cx: x + pw / 2, baseline: py + 67, size: 40,
                         Draw.color(0xff302f34))
            let amount: String
            if let typed = model.edits["w.day.\(i)"] {
                amount = Self.trim2(typed)
            } else if i == 0 {
                amount = Self.trim2(model.value("w.c.0", 20.00))
            } else if isToday {
                amount = "0.00"
            } else {
                amount = ""
            }
            d.centerText(amount, cx: x + pw / 2, baseline: py + 116, size: 32,
                         Draw.color(0xff656c72))
            hot(CGRect(x: x, y: py, width: pw, height: phh), "w.day:\(i)")
        }
    }

    private func weekRank(_ d: Draw, _ s: State, top: CGFloat) {
        plate(d, "week_rank", top: top, height: 379)
        d.round(CGRect(x: 82, y: top + 163, width: 1197 - 82, height: 202), radius: 26,
                Draw.color(0xfff2f3f7))
        d.centerText("1", cx: 131, baseline: top + 288, size: 48, Draw.color(0xff3a3f46))
        d.text("CAT2610125E01AW", x: 189, baseline: top + 254, size: 42,
               Draw.color(0xff1f2329))
        d.text("其他", x: 189, baseline: top + 328, size: 40, Draw.color(0xff9aa2ad))
        d.rightText(Self.money(model.value("w.rank.0", 20.00)), right: 1155,
                    baseline: top + 265, size: 48, Draw.color(0xff1f2329), bold: true)
        d.rightText("10-12 20:44", right: 1154, baseline: top + 324, size: 40,
                    Draw.color(0xff9aa2ad))
        hot(CGRect(x: 82, y: top + 163, width: 1115, height: 202), "w.rank.0")
    }

    private func weekCategory(_ d: Draw, _ s: State, top: CGFloat,
                              slices: [BillAnalyticsModel.Slice]) {
        plate(d, "week_cat", top: top, height: 1308)
        toggle(d, x: 1101, y: top + 68, on: s.compare)
        hot(CGRect(x: 900, y: top + 42, width: 315, height: 108), "w.compare")
        let cx: CGFloat = 636, cy = top + 615
        donut(d, cx: cx, cy: cy, slices: slices)
        leader(d, cx: cx, cy: cy, slices: slices)
        var ry = top + 1163
        for (i, z) in slices.enumerated() {
            d.text("\(i + 1).\(z.name)  \(Self.pct(z.pct))", x: 84, baseline: ry, size: 42,
                   Draw.color(0xff1f2329), bold: true)
            d.rightText("\(Self.money(z.amount))(\(z.count)笔)", right: 1129, baseline: ry,
                        size: 42, Draw.color(0xff1f2329), bold: true)
            chevron(d, x: 1180, cy: ry - 14, color: Draw.color(0xffc3c8d2))
            hot(CGRect(x: 60, y: ry - 60, width: 1155, height: 90), "w.cat:\(i)")
            ry += 130
        }
    }

    /// 抠出来的圆角卡片底图（Java:606-613）
    private func plate(_ d: Draw, _ tag: String, top: CGFloat, height: CGFloat) {
        guard let image = art[tag] else { return }
        d.clipped(to: CGRect(x: 41, y: top, width: 1237 - 41, height: height), radius: 40) {
            d.art(image, CGRect(x: 41, y: top, width: 1237 - 41, height: height))
        }
    }

    /// 环形图（Java:616-636）
    private func donut(_ d: Draw, cx: CGFloat, cy: CGFloat, slices: [BillAnalyticsModel.Slice]) {
        d.circle(cx: cx, cy: cy, r: 246, lightBlue)
        d.circle(cx: cx, cy: cy, r: 220, .white)
        var angle: CGFloat = -90
        for z in slices {
            let full = z.pct * 3.6
            drawArc(d, cx: cx, cy: cy, radius: 215, from: angle + 0.35,
                    sweep: max(2, full - 0.7), color: Draw.color(z.color))
            angle += full
        }
        d.circle(cx: cx, cy: cy, r: 130, .white)
        if slices.count == 1 {
            d.centerText(slices[0].name, cx: cx, baseline: cy - 10, size: 50, grey)
            d.centerText(Self.pct(slices[0].pct), cx: cx, baseline: cy + 58, size: 62,
                         Draw.color(0xff4a5059), bold: true)
        } else {
            d.centerText("合计", cx: cx, baseline: cy - 10, size: 44, grey)
            d.centerText(Self.pct(100), cx: cx, baseline: cy + 58, size: 58,
                         Draw.color(0xff4a5059), bold: true)
        }
    }

    private func drawArc(_ d: Draw, cx: CGFloat, cy: CGFloat, radius: CGFloat,
                         from: CGFloat, sweep: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setFillColor(color.cgColor)
        d.ctx.move(to: CGPoint(x: cx, y: cy))
        d.ctx.addArc(center: CGPoint(x: cx, y: cy), radius: radius,
                     startAngle: from * .pi / 180, endAngle: (from + sweep) * .pi / 180,
                     clockwise: false)
        d.ctx.closePath()
        d.ctx.fillPath()
        d.ctx.restoreGState()
    }

    /// 引线标签（Java:639-650）
    private func leader(_ d: Draw, cx: CGFloat, cy: CGFloat,
                        slices: [BillAnalyticsModel.Slice]) {
        guard let z = slices.first else { return }
        let y0 = cy + 240, y1 = cy + 255, x1: CGFloat = 1154
        d.ctx.saveGState()
        d.ctx.setStrokeColor(Draw.color(0xff8f959e).cgColor)
        d.ctx.setLineWidth(2.5)
        d.ctx.move(to: CGPoint(x: cx, y: y0))
        d.ctx.addLine(to: CGPoint(x: cx, y: y1))
        d.ctx.addLine(to: CGPoint(x: x1, y: y1))
        d.ctx.addLine(to: CGPoint(x: x1, y: y1 - 30))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        d.rightText("\(z.name) \(Self.pct(z.pct))", right: x1, baseline: y1 - 36, size: 42,
                    Draw.color(0xff5b6470))
    }

    /// 对比开关（Java:653-667）
    private func toggle(_ d: Draw, x: CGFloat, y: CGFloat, on: Bool) {
        d.round(CGRect(x: x, y: y, width: 97, height: 54), radius: 27,
                on ? blue : .white)
        d.roundStroke(CGRect(x: x, y: y, width: 97, height: 54), radius: 27,
                      on ? blue : Draw.color(0xffdadde2), width: 2.5)
        let kx = on ? x + 70 : x + 27
        d.circle(cx: kx, cy: y + 27, r: 26, .white)
        d.ctx.saveGState()
        d.ctx.setStrokeColor((on ? Draw.color(0xff7fb4ff) : Draw.color(0xffdadde2)).cgColor)
        d.ctx.setLineWidth(2.5)
        d.ctx.addEllipse(in: CGRect(x: kx - 26, y: y + 1, width: 52, height: 52))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func caret(_ d: Draw, cx: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: cx - 10, y: cy - 5))
        d.ctx.addLine(to: CGPoint(x: cx, y: cy + 5))
        d.ctx.addLine(to: CGPoint(x: cx + 10, y: cy - 5))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x, y: cy - 10))
        d.ctx.addLine(to: CGPoint(x: x + 14, y: cy))
        d.ctx.addLine(to: CGPoint(x: x, y: cy + 10))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func triangle(_ d: Draw, cx: CGFloat, cy: CGFloat, size: CGFloat,
                          color: UIColor, right: Bool) {
        let dir: CGFloat = right ? 1 : -1
        d.path([CGPoint(x: cx - dir * size / 2, y: cy - size / 1.6),
                CGPoint(x: cx + dir * size / 2, y: cy),
                CGPoint(x: cx - dir * size / 2, y: cy + size / 1.6)], fill: color)
    }

    // MARK: - 月度（Java:672-860）

    private func drawMonth(_ d: Draw, _ s: State) {
        periodRow(d, s, top: 340)
        bigCard(d, s, top: 500)
        if s.income {
            drawMonthIncome(d, s)
            return
        }
        subTabs(d, y: 930, names: ["月度小结", "支出分类", "省钱建议"], active: 0, key: "m")
        let ex = monthExtra(s)
        monthSummary(d, s, top: 1135)
        monthCategory(d, s, top: 3300 + ex, slices: model.slices(tab: .month))
        monthPay(d, s, top: 5400 + ex)
        monthSave(d, s, top: 6010 + ex)
    }

    // MARK: - 月度 · 收入（Java:1178-1283）

    private func drawMonthIncome(_ d: Draw, _ s: State) {
        d.round(CGRect(x: 41, y: 947, width: 1237 - 41, height: 3379 - 947), radius: 40, cardColor)
        cardTitle(d, "cal", "月度小结", x: 87, baseline: 1062, size: 54)
        d.round(CGRect(x: 956, y: 1007, width: 240, height: 74), radius: 37, pill)
        d.centerText("+ 订阅周报", cx: 1076, baseline: 1053, size: 35, Draw.color(0xff5b6470))
        hot(CGRect(x: 946, y: 1002, width: 260, height: 84), "m.subscribe")

        let labels = ["大额收入", "退款", "薪酬"]
        let tx: [CGFloat] = [81, 461, 840]
        for i in 0..<3 {
            d.round(CGRect(x: tx[i], y: 1219, width: 357, height: 227), radius: 28, tile)
            d.textSqueezed(labels[i], x: tx[i] + 30, baseline: 1280, size: 44,
                           Draw.color(0xff4a5059), squeeze: 0.92)
            d.textSqueezed(Self.integer(model.value("mi.k.\(i)", 0)), x: tx[i] + 28,
                           baseline: 1388, size: 80, ink, bold: true, squeeze: 0.85)
            chevron(d, x: tx[i] + 322, cy: 1368, color: Draw.color(0xffc3c8d2))
            hot(CGRect(x: tx[i], y: 1219, width: 357, height: 227), "mi.k:\(i)")
        }

        // 近五月柱状
        let defaults = [1666.00, 0.0, 1400.01, 0.0, 0.0]
        var mv = [Double](repeating: 0, count: 5)
        var ml = [String](repeating: "", count: 5)
        for i in 0..<5 {
            let date = Calendar.current.date(byAdding: .month, value: i - 4, to: Date()) ?? Date()
            ml[i] = "\(Calendar.current.component(.month, from: date))月"
            mv[i] = model.value("mi.b.\(i)", defaults[i])
        }
        let sum = mv.reduce(0, +)
        let mx = max(1, mv.max() ?? 1)
        let axis: CGFloat = 1890, plotH: CGFloat = 305
        let bxc: [CGFloat] = [333, 524.5, 716, 907.5, 1099]
        let ay = axis - CGFloat((sum / 5) / mx) * plotH
        dashLine(d, x1: 96, y: ay, x2: 1197, color: Draw.color(0xffdcdcd2))
        d.text(Self.trim2(sum / 5), x: 96, baseline: ay - 20, size: 28, Draw.color(0xff5b6470))
        for i in 0..<5 {
            let bh = CGFloat(mv[i] / mx) * plotH
            if mv[i] > 0 {
                let box = CGRect(x: bxc[i] - 40.5, y: axis - bh, width: 81, height: bh)
                d.clipped(to: box, radius: 40.5) {
                    d.gradient(box, from: Draw.color(0xff9fc9fd), to: Draw.color(0xffaadeff),
                               vertical: true)
                }
                d.centerText(Self.money(mv[i]), cx: bxc[i], baseline: axis - bh - 20, size: 28,
                             Draw.color(0xff6b7480))
            } else {
                d.centerText(Self.money(0), cx: bxc[i], baseline: axis - 20, size: 28,
                             Draw.color(0xff6b7480))
            }
            d.centerText(ml[i], cx: bxc[i], baseline: axis + 32, size: 30,
                         Draw.color(0xff3a3f46))
            hot(CGRect(x: bxc[i] - 46, y: axis - bh - 62, width: 92, height: bh + 122),
                "mi.b:\(i)")
        }
        d.rect(CGRect(x: 96, y: axis - 1, width: 1197 - 96, height: 2),
               Draw.color(0xffe1e1e1))
        d.centerText("— 月收入均值", cx: 668, baseline: 2072, size: 34, grey)

        calendarFull(d, s, top: 2335)
        d.centerText("收起", cx: 610, baseline: 3315, size: 34, grey)
        chevronUp(d, x: 688, cy: 3303, color: grey)
        hot(CGRect(x: 400, y: 3260, width: 480, height: 85), "m.expand")

        d.round(CGRect(x: 41, y: 3429, width: 1237 - 41, height: 3921 - 3429), radius: 40, cardColor)
        cardTitle(d, "bar", "收入排行", x: 87, baseline: 3525, size: 54)
        d.centerTextSqueezed("暂无数据", cx: 640, baseline: 3751, size: 46,
                             Draw.color(0xffb6bcc4), squeeze: 0.95)
        footer(d, 4157)
    }

    /// 整月日历（Java:1250-1283）
    private func calendarFull(_ d: Draw, _ s: State, top: CGFloat) {
        let weekdays = ["日", "一", "二", "三", "四", "五", "六"]
        let colW: CGFloat = 153, pitch: CGFloat = 163.7, x0: CGFloat = 81
        for i in 0..<7 {
            d.centerText(weekdays[i], cx: x0 + pitch * CGFloat(i) + colW / 2, baseline: top - 82,
                         size: 40, Draw.color(0xff6b7480))
        }
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let base = calendar.date(byAdding: .month, value: s.monthOffset, to: today) ?? today
        var comps = calendar.dateComponents([.year, .month], from: base)
        comps.day = 1
        guard let first = calendar.date(from: comps),
              let range = calendar.range(of: .day, in: .month, for: first) else { return }
        let firstDow = calendar.component(.weekday, from: first) - 1
        let rowH: CGFloat = 150, rowPitch: CGFloat = 182
        let baseComps = calendar.dateComponents([.year, .month], from: base)
        for day in 1...range.count {
            let idx = firstDow + day - 1
            let x = x0 + pitch * CGFloat(idx % 7)
            let y = top + CGFloat(idx / 7) * rowPitch
            let isToday = baseComps.year == calendar.component(.year, from: today)
                && baseComps.month == calendar.component(.month, from: today)
                && day == calendar.component(.day, from: today)
            d.round(CGRect(x: x, y: y, width: colW, height: rowH), radius: 26,
                    isToday ? Draw.color(0xffa0c9ff) : Draw.color(0xffecf3fb))
            d.centerTextSqueezed(isToday ? "今天" : "\(day)", cx: x + colW / 2, baseline: y + 47,
                                 size: 44, Draw.color(0xff302f34), bold: true, squeeze: 0.9)
            if let typed = model.edits["mi.day.\(day)"] {
                d.centerText(Self.trim2(typed), cx: x + colW / 2, baseline: y + 103, size: 30,
                             green, bold: true)
            } else {
                d.centerText("-", cx: x + colW / 2, baseline: y + 103, size: 30,
                             Draw.color(0xff9aa2ad))
            }
            hot(CGRect(x: x, y: y, width: colW, height: rowH), "mi.day:\(day)")
        }
    }

    private func chevronUp(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x, y: cy + 10))
        d.ctx.addLine(to: CGPoint(x: x + 14, y: cy))
        d.ctx.addLine(to: CGPoint(x: x, y: cy - 10))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func subTabs(_ d: Draw, y: CGFloat, names: [String], active: Int, key: String) {
        let third = Self.W / 3
        for (i, name) in names.enumerated() {
            let cx = third * CGFloat(i) + third / 2
            d.centerText(name, cx: cx, baseline: y + 40, size: 40,
                         i == active ? ink : Draw.color(0xff6b7480), bold: i == active)
            if i == active {
                d.round(CGRect(x: cx - 46, y: y + 62, width: 92, height: 7), radius: 4, ink)
            }
            hot(CGRect(x: third * CGFloat(i), y: y - 10, width: third, height: 90),
                "\(key).sect:\(i)")
        }
    }

    private func monthSummary(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h = 2115 + monthExtra(s)
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "cal", "月度小结", x: l + 55, baseline: top + 95, size: 46)

        d.round(CGRect(x: 955, y: top + 60, width: 255, height: 80), radius: 40, pill)
        d.centerText("+ 订阅周报", cx: 1082, baseline: top + 112, size: 36,
                     Draw.color(0xff5b6470))
        hot(CGRect(x: 950, y: top + 55, width: 265, height: 90), "m.subscribe")

        d.text("●", x: 78, baseline: top + 262, size: 40, Draw.color(0xff3a3f46))
        d.text("本月目前支出增长不少，对比上月同周期的支出增加了", x: 108, baseline: top + 262,
               size: 40, Draw.color(0xff3a3f46))
        d.text(Self.money(model.value("m.up", 82.92)) + "元，", x: 108, baseline: top + 340,
               size: 40, Draw.color(0xff3a3f46))
        d.text("设置每月预算", x: 350, baseline: top + 340, size: 40, blue, bold: true)
        hot(CGRect(x: 108, y: top + 300, width: 232, height: 60), "m.up")

        // 四张小卡
        let labels = ["大额消费", "自动扣款", "先用后付", "分期付款"]
        let values = [model.value("m.k.0", 0), Double(model.count("m.k.1", 4)),
                      model.value("m.k.2", 0), model.value("m.k.3", 0)]
        let cw = (1140 - 3 * 20) / 4, cx0: CGFloat = 75, cy0 = top + 495, ch: CGFloat = 250
        for i in 0..<4 {
            let x = cx0 + CGFloat(i) * (cw + 20)
            d.round(CGRect(x: x, y: cy0, width: cw, height: ch), radius: 24, tile)
            d.text(labels[i], x: x + 28, baseline: cy0 + 80, size: 36, Draw.color(0xff4a5059))
            d.text(Self.integer(values[i]), x: x + 28, baseline: cy0 + 195, size: 60, ink,
                   bold: true)
            chevron(d, x: x + cw - 38, cy: cy0 + 175, color: Draw.color(0xffc3c8d2))
            hot(CGRect(x: x, y: cy0, width: cw, height: ch), "m.k:\(i)")
        }

        // 近五月柱状
        var mv = [Double](repeating: 0, count: 5)
        var ml = [String](repeating: "", count: 5)
        let defaults = [3063.44, 1247.21, 1755.18, 182.27,
                        model.value("m.c.0", 50.00) + model.value("m.c.1", 41.19)
                        + model.value("m.c.2", 20.00)]
        for i in 0..<5 {
            let date = Calendar.current.date(byAdding: .month, value: i - 4, to: Date()) ?? Date()
            ml[i] = "\(Calendar.current.component(.month, from: date))月"
            mv[i] = model.value("m.bar.\(i)", defaults[i])
        }
        let bx0: CGFloat = 140, bx1: CGFloat = 1190, bw = (bx1 - bx0) / 5
        let baseline = top + 1215, plotTop = top + 830, maxV: CGFloat = 3400
        let avg = mv.reduce(0, +) / 5
        let ay = baseline - CGFloat(avg / Double(maxV)) * (baseline - plotTop)
        dashLine(d, x1: bx0 - 20, y: ay, x2: bx1 + 20)
        d.text(Self.trim2(avg), x: 95, baseline: ay - 18, size: 34, Draw.color(0xff5b6470))
        for i in 0..<5 {
            let x = bx0 + CGFloat(i) * bw + bw / 2
            let bh = CGFloat(mv[i] / Double(maxV)) * (baseline - plotTop)
            d.round(CGRect(x: x - 26, y: baseline - bh, width: 52, height: bh), radius: 26,
                    i == 4 ? blue : Draw.color(0xffbfd8fb))
            d.centerText("¥" + Self.trim2(mv[i]), cx: x, baseline: baseline - bh - 22, size: 30,
                         Draw.color(0xff5b6470))
            d.centerText(ml[i], cx: x, baseline: baseline + 52, size: 36, Draw.color(0xff3a3f46))
            hot(CGRect(x: x - 30, y: baseline - bh - 40, width: 60, height: bh + 100),
                "m.bar:\(i)")
        }
        d.rect(CGRect(x: bx0 - 20, y: baseline - 1, width: bx1 - bx0 + 40, height: 2),
               Draw.color(0xffe6e9ee))
        d.centerText("— 月支出均值", cx: Self.W / 2, baseline: top + 1390, size: 38, grey)

        monthCalendar(d, s, top: top + 1520)
        d.centerText((s.showAll ? "收起" : "展开全部") + "  ⌄", cx: Self.W / 2, baseline: top + h - 40,
                     size: 38, grey)
        hot(CGRect(x: 400, y: top + h - 110, width: 480, height: 100), "m.expand")
    }

    private func monthCalendar(_ d: Draw, _ s: State, top: CGFloat) {
        let weekdays = ["日", "一", "二", "三", "四", "五", "六"]
        let x0: CGFloat = 75, cw = 1130 / 7
        for i in 0..<7 {
            d.centerText(weekdays[i], cx: x0 + cw * CGFloat(i) + cw / 2, baseline: top + 45,
                         size: 38, Draw.color(0xff6b7480))
        }
        let calendar = Calendar.current
        let base = calendar.date(byAdding: .month, value: s.monthOffset, to: Date()) ?? Date()
        let today = calendar.startOfDay(for: Date())
        var cells: [Int] = []
        if s.showAll {
            let comps = calendar.dateComponents([.year, .month], from: base)
            guard let first = calendar.date(from: comps),
                  let range = calendar.range(of: .day, in: .month, for: first) else { return }
            let firstDow = calendar.component(.weekday, from: first) - 1
            for _ in 0..<firstDow { cells.append(-1) }
            for day in 1...range.count { cells.append(day) }
        } else {
            let dow = calendar.component(.weekday, from: today) - 1
            let start = calendar.date(byAdding: .day, value: -dow - 7, to: today) ?? today
            for i in 0..<14 {
                let day = calendar.date(byAdding: .day, value: i, to: start) ?? start
                cells.append(calendar.component(.day, from: day))
            }
        }
        let cy0 = top + 80, ch: CGFloat = 150
        let baseComps = calendar.dateComponents([.year, .month], from: base)
        for (i, day) in cells.enumerated() where day > 0 {
            let row = i / 7, col = i % 7
            let x = x0 + cw * CGFloat(col) + 6
            let y = cy0 + CGFloat(row) * (ch + 16)
            let isToday = day == calendar.component(.day, from: today)
                && baseComps.month == calendar.component(.month, from: today)
                && baseComps.year == calendar.component(.year, from: today)
            d.round(CGRect(x: x, y: y, width: cw - 12, height: ch), radius: 22,
                    isToday ? hilite : pill)
            d.centerText(isToday ? "今天" : "\(day)", cx: x + (cw - 12) / 2, baseline: y + 72,
                         size: 40, Draw.color(0xff3a3f46))
            if let typed = model.edits["m.day.\(day)"] {
                d.centerText(Self.trim2(typed), cx: x + (cw - 12) / 2, baseline: y + 122,
                             size: 30, green, bold: true)
            } else {
                let amount = day == 8 ? "1.40"
                    : (day == 12 ? Self.money(model.value("w.c.0", 20.00)) : "-")
                d.centerText(amount, cx: x + (cw - 12) / 2, baseline: y + 122, size: 30, grey)
            }
            hot(CGRect(x: x, y: y, width: cw - 12, height: ch), "m.day:\(day)")
        }
    }

    private func monthCategory(_ d: Draw, _ s: State, top: CGFloat,
                               slices: [BillAnalyticsModel.Slice]) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 2050
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "pie", "支出分类", x: l + 45, baseline: top + 95, size: 46)
        d.text("对比上月", x: 1000, baseline: top + 92, size: 38, grey)
        toggle(d, x: 1105, y: top + 56, on: s.compare)
        hot(CGRect(x: 1060, y: top + 40, width: 155, height: 100), "m.compare")

        d.text("●", x: 78, baseline: top + 245, size: 38, Draw.color(0xff3a3f46))
        let top1 = slices.first?.name ?? ""
        d.text("\(top1) 分类消费占比最高，", x: 108, baseline: top + 245, size: 40,
               Draw.color(0xff3a3f46))
        d.text("日用百货", x: 108, baseline: top + 325, size: 40, Draw.color(0xff3a3f46))
        d.text("分类消费较上月变化最大", x: 300, baseline: top + 325, size: 40,
               Draw.color(0xff3a3f46))
        d.rect(CGRect(x: 112, y: top + 256, width: 120, height: 10), hilite)
        d.rect(CGRect(x: 112, y: top + 336, width: 180, height: 10), hilite)

        let cx: CGFloat = 640, cy = top + 770
        donut(d, cx: cx, cy: cy, slices: slices)
        leader(d, cx: cx, cy: cy, slices: slices)

        var ry = top + 1290
        for (i, z) in slices.enumerated() {
            let open = i == s.expanded && i == 0
            d.text("\(i + 1).\(z.name)  \(Self.pct(z.pct))", x: 85, baseline: ry, size: 42,
                   ink, bold: true)
            d.rightText("\(Self.money(z.amount))(\(z.count)笔)", right: 1100, baseline: ry,
                        size: 42, ink, bold: true)
            chevron(d, x: 1175, cy: ry - 14, color: Draw.color(0xffc3c8d2))
            hot(CGRect(x: 60, y: ry - 60, width: 1155, height: 90), "m.cat:\(i)")
            if i == 0 { hot(CGRect(x: 1090, y: ry - 55, width: 125, height: 80), "m.exp:\(i)") }
            if open {
                let sy = ry + 40
                d.round(CGRect(x: 80, y: sy, width: 1120, height: 300), radius: 24, tile)
                if let phone = art["phone"] {
                    d.art(phone, CGRect(x: 120, y: sy + 35, width: 130, height: 130))
                }
                d.text("手机充值 ›", x: 120, baseline: sy + 235, size: 42, ink)
                d.text(Self.money(z.amount), x: 120, baseline: sy + 295, size: 40,
                       Draw.color(0xff5b6470))
                ry += 340
            }
            ry += 190
        }
    }

    private func monthPay(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 560
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "card", "支出方式", x: l + 55, baseline: top + 95, size: 46)
        d.round(CGRect(x: 85, y: top + 180, width: 1110, height: 240), radius: 24, tile)
        if let cmb = art["cmb"] {
            d.art(cmb, CGRect(x: 120, y: top + 235, width: 115, height: 115))
        }
        d.text("招商银行储蓄卡(4646)", x: 275, baseline: top + 310, size: 42, ink)
        d.rightText(Self.money(model.value("m.pay.0", 259.06)) + "(8笔)", right: 1100,
                    baseline: top + 310, size: 42, ink, bold: true)
        chevron(d, x: 1165, cy: top + 296, color: Draw.color(0xffc3c8d2))
        hot(CGRect(x: 85, y: top + 180, width: 1110, height: 240), "m.pay:0")
    }

    private func monthSave(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 500
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "yuan", "省钱建议", x: l + 55, baseline: top + 95, size: 46)
        d.text("本月已省 ›", x: 240, baseline: top + 230, size: 40, Draw.color(0xff4a5059))
        d.text(Self.money(model.value("m.saved", 0.00)), x: 240, baseline: top + 330, size: 66,
               ink, bold: true)
        hot(CGRect(x: 200, y: top + 210, width: 460, height: 170), "m.saved")
        d.text("最省日 ›", x: 820, baseline: top + 230, size: 40, Draw.color(0xff4a5059))
        let comps = Calendar.current.dateComponents([.month],
                                                    from: Calendar.current.date(byAdding: .month,
                                                                                value: s.monthOffset,
                                                                                to: Date()) ?? Date())
        d.text("\(comps.month ?? 1)月1日", x: 820, baseline: top + 330, size: 66, ink, bold: true)
    }

    private func cardTitle(_ d: Draw, _ art: String, _ title: String, x: CGFloat,
                           baseline: CGFloat, size: CGFloat) {
        d.text(title, x: x, baseline: baseline, size: size, ink, bold: true)
    }

    private func dashLine(_ d: Draw, x1: CGFloat, y: CGFloat, x2: CGFloat,
                          color: UIColor = Draw.color(0xffe6dba0)) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(2)
        d.ctx.setLineDash(phase: 0, lengths: [10, 8])
        d.ctx.move(to: CGPoint(x: x1, y: y))
        d.ctx.addLine(to: CGPoint(x: x2, y: y))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 年度页（Java:866-892）
    private func drawYear(_ d: Draw, _ s: State) {
        periodRow(d, s, top: 340)
        bigCard(d, s, top: 500)
        if s.income {
            drawYearIncome(d, s)
            return
        }
        if !s.noticeClosed { noticeBar(d, s, top: 905) }
        var y: CGFloat = s.noticeClosed ? 930 : 1090
        subTabs(d, y: y, names: ["支出分类", "月度对比", "省钱建议"], active: 1, key: "y")
        yearCategory(d, s, top: y + 180, slices: model.slices(tab: .year))
        y = y + 2745 + 50
        yearCompare(d, s, top: y)
        y += 1090 + 50
        yearCity(d, s, top: y)
        y += 1117 + 50
        yearRank(d, s, top: y)
        y += 890 + 50
        yearPay(d, s, top: y)
        y += 880 + 50
        yearSave(d, s, top: y)
        y += 980 + 58
        moreBanner(d, y)
        footer(d, y + 250)
    }

    /// 年度提示条（Java:894-911）
    private func noticeBar(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40
        // 小尖角
        d.path([CGPoint(x: 120, y: top), CGPoint(x: 150, y: top - 22),
                CGPoint(x: 180, y: top)], fill: .white)
        d.round(CGRect(x: l, y: top, width: r - l, height: 175), radius: 20, .white)
        d.text("有投资理财交易默认不计入收支统计。如需加入统计，请", x: 90, baseline: top + 75,
               size: 38, Draw.color(0xff3a3f46))
        d.text(s.investIncluded ? "已计入统计" : "点击修改", x: 90, baseline: top + 135,
               size: 38, blue, bold: true)
        d.rect(CGRect(x: 90, y: top + 145, width: 100, height: 8), hilite)

        d.ctx.saveGState()
        d.ctx.setStrokeColor(Draw.color(0xffb6bcc4).cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: r - 70, y: top + 40))
        d.ctx.addLine(to: CGPoint(x: r - 38, y: top + 72))
        d.ctx.move(to: CGPoint(x: r - 38, y: top + 40))
        d.ctx.addLine(to: CGPoint(x: r - 70, y: top + 72))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        hot(CGRect(x: r - 100, y: top + 10, width: 90, height: 90), "y.notice.x")
        hot(CGRect(x: 80, y: top + 100, width: 180, height: 65), "y.invest")
    }

    /// 年度支出分类（Java:913-950）
    private func yearCategory(_ d: Draw, _ s: State, top: CGFloat,
                              slices: [BillAnalyticsModel.Slice]) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 2565
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "pie", "支出分类", x: l + 45, baseline: top + 100, size: 46)

        let cx: CGFloat = 640, cy = top + 620
        donut(d, cx: cx, cy: cy, slices: slices)
        leader(d, cx: cx, cy: cy, slices: slices)

        var ry = top + 1020
        let subNames = ["正餐", "其他", "炸鸡汉堡", "生鲜水果", "咖啡奶茶", "零食"]
        let subArt = ["f0", "f1", "f2", "f3", "f4", "f5"]
        let subAmt = [model.value("y.t.0", 2013.67), model.value("y.t.1", 644.05),
                      model.value("y.t.2", 243.00), model.value("y.t.3", 20.40),
                      model.value("y.t.4", 8.00), model.value("y.t.5", 4.00)]
        for (i, z) in slices.enumerated() {
            let open = i == s.expanded
            d.text("\(i + 1).\(z.name)  \(Self.pct(z.pct))", x: 85, baseline: ry, size: 42,
                   ink, bold: true)
            d.rightText("\(Self.money(z.amount))(\(z.count)笔)", right: 1100, baseline: ry,
                        size: 42, ink, bold: true)
            chevron(d, x: 1175, cy: ry - 14, color: Draw.color(0xffc3c8d2))
            hot(CGRect(x: 60, y: ry - 60, width: 1155, height: 90), "y.cat:\(i)")
            if i == 0 { hot(CGRect(x: 1090, y: ry - 55, width: 125, height: 80), "y.exp:\(i)") }
            if open && i == 0 && z.amount > 0 {
                let tx: CGFloat = 80, tw = (1120 - 2 * 16) / 3, th: CGFloat = 290
                for k in 0..<6 {
                    let rr2 = k / 3, cc = k % 3
                    let x = tx + CGFloat(cc) * (tw + 16)
                    let yy = ry + 50 + CGFloat(rr2) * (th + 16)
                    d.round(CGRect(x: x, y: yy, width: tw, height: th), radius: 20, tile)
                    if let image = art[subArt[k]] {
                        d.art(image, CGRect(x: x + 35, y: yy + 35, width: 115, height: 115))
                    }
                    d.text(subNames[k] + " ›", x: x + 35, baseline: yy + 225, size: 40, ink)
                    d.text(Self.money(subAmt[k]), x: x + 35, baseline: yy + 275, size: 38,
                           Draw.color(0xff5b6470))
                    hot(CGRect(x: x, y: yy, width: tw, height: th), "y.tile:\(k)")
                }
                ry += 630
            }
            ry += 210
        }
        d.centerText((s.showAll ? "收起" : "展开全部") + "  ⌄", cx: Self.W / 2, baseline: ry + 30,
                     size: 38, grey)
        hot(CGRect(x: 400, y: ry - 30, width: 480, height: 100), "y.expand")
    }

    /// 月度对比（Java:952-987）
    private func yearCompare(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 1090
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "cal", "月度对比", x: l + 45, baseline: top + 100, size: 46)

        let defaults = [520.0, 480.0, 360.0, 760.0, 1700.0, 3063.44, 1150.0, 1697.0,
                        520.0, 470.0, 480.0, 610.0]
        var mv = [Double](repeating: 0, count: 12)
        let maxV = 3400.0
        var sum = 0.0
        var maxIndex = 0
        for i in 0..<12 {
            mv[i] = model.value("y.m.\(i)", defaults[i])
            sum += mv[i]
            if mv[i] > mv[maxIndex] { maxIndex = i }
        }
        let avg = sum / 12
        let baseline = top + 700, plotTop = top + 250
        let x0: CGFloat = 110, x1: CGFloat = 1180, bw = (x1 - x0) / 12
        for i in 0..<12 {
            let x = x0 + bw * CGFloat(i) + bw / 2
            let bh = CGFloat(mv[i] / maxV) * (baseline - plotTop)
            d.round(CGRect(x: x - 16, y: baseline - bh, width: 32, height: bh), radius: 16,
                    i == maxIndex ? blue : Draw.color(0xffbfd8fb))
            d.centerText("\(i + 1)", cx: x, baseline: baseline + 62, size: 40,
                         i == maxIndex ? ink : Draw.color(0xff6b7480), bold: i == maxIndex)
            hot(CGRect(x: x - 25, y: baseline - bh - 30, width: 50, height: bh + 100),
                "y.m:\(i)")
        }
        let ay = baseline - CGFloat(avg / maxV) * (baseline - plotTop)
        dashLine(d, x1: x0 - 40, y: ay, x2: x1 + 30)
        d.text(Self.trim2(avg), x: 70, baseline: ay - 20, size: 36, Draw.color(0xff5b6470))
        d.rect(CGRect(x: x0 - 40, y: baseline - 1, width: x1 + 30 - (x0 - 40), height: 2),
               Draw.color(0xffe6e9ee))

        // 最高月气泡
        let bx = x0 + bw * CGFloat(maxIndex) + bw / 2
        let year = (Calendar.current.component(.year, from: Date())) + s.yearOffset
        let py = baseline - CGFloat(mv[maxIndex] / maxV) * (baseline - plotTop) - 190
        let pw: CGFloat = 330, px = bx - pw / 2
        d.round(CGRect(x: px, y: py, width: pw, height: 150), radius: 26, blue)
        d.path([CGPoint(x: bx - 22, y: py + 150), CGPoint(x: bx, y: py + 185),
                CGPoint(x: bx + 22, y: py + 150)], fill: blue)
        d.centerText("\(year)年\(maxIndex + 1)月 最多", cx: bx, baseline: py + 62, size: 36, .white)
        d.centerText("¥" + Self.trim2(mv[maxIndex]) + "  ▶", cx: bx, baseline: py + 118,
                     size: 40, .white, bold: true)
        hot(CGRect(x: px, y: py, width: pw, height: 185), "y.m:\(maxIndex)")
        d.centerText("— 月支出均值", cx: Self.W / 2, baseline: top + h - 60, size: 38, grey)
    }

    /// 城市解锁（Java:989-1084）
    private func yearCity(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 41, r: CGFloat = 1237, h: CGFloat = 1117
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "map", "城市解锁", x: 86, baseline: top + 108, size: 54)

        let cityCount = Self.integer(model.value("yc.n", 2))
        let city1 = model.text("yc.c1", "恩施土家族苗族…")
        let n1 = Self.integer(model.value("yc.n1", 10))
        let city2 = model.text("yc.c2", "上海")
        let n2 = Self.integer(model.value("yc.n2", 69))

        // 提示语：两行，两处浅蓝高亮
        let size: CGFloat = 46, sq: CGFloat = 0.86, x: CGFloat = 148
        d.text("●", x: 100, baseline: top + 258, size: 44, Draw.color(0xff3a3f46))
        let w1 = Draw.width("已经在", size: size) * sq
        let w2 = Draw.width(cityCount + "个城市", size: size) * sq
        let w3 = Draw.width("使用线下支付，", size: size) * sq
        let w4 = Draw.width(city2, size: size) * sq
        d.textSqueezed("已经在", x: x, baseline: top + 258, size: size,
                       Draw.color(0xff3a3f46), squeeze: sq)
        d.textSqueezed(cityCount + "个城市", x: x + w1, baseline: top + 258, size: size,
                       Draw.color(0xff3a3f46), squeeze: sq)
        d.textSqueezed("使用线下支付，", x: x + w1 + w2, baseline: top + 258, size: size,
                       Draw.color(0xff3a3f46), squeeze: sq)
        d.textSqueezed(city2, x: x + w1 + w2 + w3, baseline: top + 258, size: size,
                       Draw.color(0xff3a3f46), squeeze: sq)
        d.textSqueezed("是你消费最多的城", x: x + w1 + w2 + w3 + w4, baseline: top + 258,
                       size: size, Draw.color(0xff3a3f46), squeeze: sq)
        d.textSqueezed("市", x: x, baseline: top + 320, size: size,
                       Draw.color(0xff3a3f46), squeeze: sq)
        d.rect(CGRect(x: x + w1, y: top + 276, width: w2, height: 10), hilite)
        d.rect(CGRect(x: x + w1 + w2 + w3, y: top + 276, width: w4, height: 10), hilite)
        hot(CGRect(x: x + w1 - 12, y: top + 205, width: w2 + 24, height: 95), "yc.n")
        hot(CGRect(x: x + w1 + w2 + w3 - 12, y: top + 205, width: w4 + 24, height: 95), "yc.c2")

        // 右上角问号
        d.ctx.saveGState()
        d.ctx.setStrokeColor(Draw.color(0xffc3c8d2).cgColor)
        d.ctx.setLineWidth(3)
        d.ctx.addEllipse(in: CGRect(x: 1137, y: top + 214, width: 52, height: 52))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        d.centerText("?", cx: 1163, baseline: top + 254, size: 32, Draw.color(0xffc3c8d2))

        // 城市速写：淡雾状高楼群（渐变色块 + 细竖纹）
        let skyB = top + 838
        let widths: [CGFloat] = [54, 66, 42, 84, 56, 110, 70, 88, 52, 96, 58, 68]
        let heights: [CGFloat] = [140, 230, 100, 290, 200, 380, 250, 340, 160, 300, 210, 130]
        var bx: CGFloat = 132
        for i in 0..<widths.count {
            let topY = skyB - heights[i]
            let right = bx + widths[i]
            let box = CGRect(x: bx, y: topY, width: widths[i], height: heights[i])
            d.clipped(to: box, radius: 6) {
                d.gradient(box, from: Draw.color(0xfffbfdff), to: Draw.color(0xffeaf3fe),
                           vertical: true)
            }
            d.ctx.saveGState()
            d.ctx.setStrokeColor(Draw.color(0xffdfeafb).cgColor)
            d.ctx.setLineWidth(1)
            var xx = bx + 4
            while xx < right - 2 {
                d.ctx.move(to: CGPoint(x: xx, y: topY + 6))
                d.ctx.addLine(to: CGPoint(x: xx, y: skyB - 4))
                xx += 4
            }
            d.ctx.strokePath()
            d.ctx.restoreGState()
            d.roundStroke(box, radius: 6, Draw.color(0xffd5e4f9), width: 1.5)
            bx = right + 16
        }

        // 两个城市：左灰右蓝
        let wrapped = Self.wrapCity(city1, limit: 4)
        d.text(wrapped[0], x: 178, baseline: top + 618, size: 66, Draw.color(0xff6b7480),
               bold: true)
        if !wrapped[1].isEmpty {
            d.text(wrapped[1], x: 178, baseline: top + 718, size: 66, Draw.color(0xff6b7480),
                   bold: true)
        }
        d.centerText(n1 + "笔", cx: 434, baseline: top + 538, size: 38, Draw.color(0xff6b7480))
        hot(CGRect(x: 150, y: top + 540, width: 320, height: 220), "yc.c1")
        hot(CGRect(x: 360, y: top + 485, width: 148, height: 77), "yc.n1")

        let need = Draw.width(city2, size: 162)
        let bigSq = need > 420 ? 420 / need : 1
        d.textSqueezed(city2, x: 523, baseline: top + 717, size: 162, blue, bold: true,
                       squeeze: bigSq)
        d.centerText(n2 + "笔", cx: 813, baseline: top + 556, size: 38, blue)
        hot(CGRect(x: 500, y: top + 540, width: 400, height: 220), "yc.c2")
        hot(CGRect(x: 740, y: top + 500, width: 146, height: 80), "yc.n2")

        // 时间轴
        let tl = top + 1014
        d.rect(CGRect(x: 150, y: tl - 1.5, width: 1058 - 150, height: 3),
               Draw.color(0xffd8dde4))
        d.circle(cx: 415, cy: tl, r: 8, Draw.color(0xffb9bfc8))
        d.circle(cx: 815, cy: tl, r: 8, Draw.color(0xffb9bfc8))
        // 起始定位针
        d.path([CGPoint(x: 101, y: tl - 26), CGPoint(x: 121, y: tl - 26),
                CGPoint(x: 111, y: tl - 2)], fill: Draw.color(0xffc3c8d2))
        d.circle(cx: 111, cy: tl - 30, r: 14, Draw.color(0xffc3c8d2))
        d.circle(cx: 111, cy: tl - 30, r: 6, .white)
        d.centerText(Self.wrapCity(city2, limit: 4)[0], cx: 415, baseline: tl - 80, size: 34, grey)
        d.centerText(model.text("yc.t1d", "01/03"), cx: 415, baseline: tl - 37, size: 32, grey)
        d.centerText(model.text("yc.t2n", "恩施土…"), cx: 802, baseline: tl - 80, size: 34, grey)
        d.centerText(model.text("yc.t2d", "08/17"), cx: 802, baseline: tl - 37, size: 32, grey)
        d.rightText(model.text("yc.year", "2026"), right: 1196, baseline: tl + 16, size: 40, grey)
        hot(CGRect(x: 350, y: tl - 95, width: 130, height: 75), "yc.t1d")
        hot(CGRect(x: 738, y: tl - 110, width: 130, height: 55), "yc.t2n")
        hot(CGRect(x: 738, y: tl - 95, width: 130, height: 75), "yc.t2d")
        hot(CGRect(x: 1090, y: tl - 20, width: 120, height: 50), "yc.year")
    }

    /// 支出排行（Java:1086-1110）
    private func yearRank(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 890
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "bar", "支出排行", x: l + 45, baseline: top + 95, size: 46)
        let names = ["转账-孝文迪(***迪)", "机票", "美团-tomorrow·明日美发(…"]
        let cats = ["转账红包", "交通出行", "美容美发"]
        let times = ["06-19 19:04", "08-10 22:15", "08-15 16:20"]
        let amounts = [1000.00, 669.42, 374.00]
        var y = top + 180
        for i in 0..<3 {
            d.round(CGRect(x: 80, y: y, width: 1120, height: 190), radius: 24,
                    Draw.color(0xfff2f3f7))
            d.centerText("\(i + 1)", cx: 140, baseline: y + 110, size: 40,
                         Draw.color(0xff3a3f46))
            d.text(model.text("y.rn.\(i)", names[i]), x: 195, baseline: y + 85, size: 44, ink)
            d.text(model.text("y.rc.\(i)", cats[i]), x: 195, baseline: y + 150, size: 38, grey)
            d.rightText(Self.money(model.value("y.r.\(i)", amounts[i])), right: 1155,
                        baseline: y + 85, size: 44, ink, bold: true)
            d.rightText(model.text("y.rt.\(i)", times[i]), right: 1155, baseline: y + 150,
                        size: 36, grey)
            hot(CGRect(x: 185, y: y + 22, width: 715, height: 86), "y.rn:\(i)")
            hot(CGRect(x: 185, y: y + 112, width: 715, height: 74), "y.rc:\(i)")
            hot(CGRect(x: 900, y: y + 14, width: 305, height: 94), "y.r:\(i)")
            hot(CGRect(x: 900, y: y + 112, width: 305, height: 76), "y.rt:\(i)")
            y += 210
        }
        d.centerText("展开全部  ⌄", cx: Self.W / 2, baseline: y + 40, size: 38, grey)
    }

    /// 年度支出方式（Java:1112-1136）
    private func yearPay(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 880
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "card", "支出方式", x: l + 55, baseline: top + 95, size: 46)
        let names = ["花呗", "招商银行储蓄卡(4646)", "招商银行储蓄卡(5021)"]
        let amounts = [1902.37, 19526.35, 5.00]
        let counts = [33, 183, 1]
        var y = top + 180
        for i in 0..<3 {
            d.round(CGRect(x: 80, y: y, width: 1120, height: 192), radius: 24, tile)
            let iconBox = CGRect(x: 122, y: y + 49, width: 94, height: 94)
            if let image = art[i == 0 ? "payHuabei" : "payCmb"] { d.art(image, iconBox) }
            d.text(model.text("y.pn.\(i)", names[i]), x: 258, baseline: y + 115, size: 42, ink)
            d.rightText(Self.money(model.value("y.p.\(i)", amounts[i]))
                        + "(\(model.count("y.pc.\(i)", counts[i]))笔)",
                        right: 1120, baseline: y + 115, size: 42, ink, bold: true)
            chevron(d, x: 1180, cy: y + 101, color: Draw.color(0xffc3c8d2))
            hot(CGRect(x: 100, y: y + 35, width: 140, height: 130), "y.pi:\(i)")
            hot(CGRect(x: 250, y: y + 45, width: 570, height: 127), "y.pn:\(i)")
            hot(CGRect(x: 820, y: y + 30, width: 390, height: 146), "y.p:\(i)")
            y += 220
        }
    }

    /// 年度省钱建议（Java:1138-1159）
    private func yearSave(_ d: Draw, _ s: State, top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 980
        d.round(CGRect(x: l, y: top, width: r - l, height: h), radius: 40, cardColor)
        cardTitle(d, "yuan", "省钱建议", x: l + 55, baseline: top + 95, size: 46)
        d.text("今年已省 ›", x: 220, baseline: top + 230, size: 40, Draw.color(0xff4a5059))
        d.text(Self.money(model.value("y.saved", 53.66)), x: 220, baseline: top + 330, size: 66,
               ink, bold: true)
        hot(CGRect(x: 180, y: top + 210, width: 520, height: 170), "y.saved")
        d.text("已省40笔", x: 220, baseline: top + 390, size: 34, grey)
        d.text("最省月份 ›", x: 800, baseline: top + 230, size: 40, Draw.color(0xff4a5059))
        d.text("6月", x: 800, baseline: top + 330, size: 66, ink, bold: true)
        d.text("省下30.49元", x: 800, baseline: top + 390, size: 34, grey)

        d.round(CGRect(x: 80, y: top + 440, width: 1120, height: 440), radius: 24, tile)
        let names = ["千问新客专属立减券", "千问每日必减", "福利抵扣"]
        let values = ["已省17.40元", "已省16.80元", "已省10.00元"]
        var y = top + 500
        for i in 0..<3 {
            d.text("TOP\(i + 1)", x: 120, baseline: y + 50, size: 36,
                   Draw.color(0xff8ea6c4), bold: true)
            d.text(names[i], x: 260, baseline: y + 50, size: 40, Draw.color(0xff3a3f46))
            d.rightText(values[i], right: 1150, baseline: y + 50, size: 40,
                        Draw.color(0xffc0342b))
            y += 120
        }
    }

    /// 「更多分析」横幅（Java:1161-1169）
    private func moreBanner(_ d: Draw, _ top: CGFloat) {
        let l: CGFloat = 40, r = Self.W - 40, h: CGFloat = 200
        let box = CGRect(x: l, y: top, width: r - l, height: h)
        d.clipped(to: box, radius: 24) {
            d.gradient(box, from: Draw.color(0xfffdf3ec), to: Draw.color(0xfffdeee2),
                       vertical: false)
        }
        if let coin = art["coin"] {
            d.art(coin, CGRect(x: r - 280, y: top + 20, width: 260, height: 160))
        }
        d.text("更多分析", x: l + 55, baseline: top + 125, size: 52, ink, bold: true)
        d.text("理财分析", x: l + 280, baseline: top + 122, size: 40, grey)
        hot(box, "more")
    }

    private func footer(_ d: Draw, _ y: CGFloat) {
        d.centerText("- 列表到底了 -", cx: Self.W / 2, baseline: y, size: 38,
                     Draw.color(0xffb6bcc4))
    }

    // MARK: - 年度 · 收入（Java:1288-1396）

    private func drawYearIncome(_ d: Draw, _ s: State) {
        let shift: CGFloat = s.noticeClosed ? -196 : 0
        if !s.noticeClosed { noticeBar(d, s, top: 931) }
        let top = 1181 + shift

        // 月度对比
        d.round(CGRect(x: 41, y: top, width: 1237 - 41, height: 1565), radius: 40, cardColor)
        cardTitle(d, "cal", "月度对比", x: 87, baseline: top + 89, size: 54)

        let defaults = [0.0, 0.0, 0.0, 20.00, 21.48, 1666.00, 0.0, 1400.01, 0.0, 0.0, 0.0, 0.0]
        var mv = [Double](repeating: 0, count: 12)
        var sum = 0.0
        var maxIndex = 0
        for i in 0..<12 {
            mv[i] = model.value("yi.m.\(i)", defaults[i])
            sum += mv[i]
            if mv[i] > mv[maxIndex] { maxIndex = i }
        }
        var elapsed = s.yearOffset == 0
            ? Calendar.current.component(.month, from: Date()) : 12
        if elapsed < 1 { elapsed = 1 }
        let avg = sum / Double(elapsed)
        let mx = max(1.0, mv[maxIndex])

        let axis = top + 1273, plotH: CGFloat = 709
        let x0: CGFloat = 370, pitch: CGFloat = 67.6
        for i in 0..<12 {
            let bx = x0 + pitch * CGFloat(i)
            let bh = CGFloat(mv[i] / mx) * plotH
            if mv[i] > 0 {
                d.round(CGRect(x: bx - 24.5, y: axis - bh, width: 49, height: bh), radius: 24.5,
                        i == maxIndex ? blue : Draw.color(0xffd2e8ff))
            }
            d.centerText("\(i + 1)", cx: bx, baseline: axis + 66, size: 56,
                         i == maxIndex ? ink : Draw.color(0xff6b7480), bold: i == maxIndex)
            hot(CGRect(x: bx - 32, y: axis - bh - 40, width: 64, height: bh + 132), "yi.m:\(i)")
        }
        let ay = axis - CGFloat(avg / mx) * plotH
        dashLine(d, x1: 334, y: ay, x2: 1197, color: Draw.color(0xffdcdcd2))
        d.text(Self.trim2(avg), x: 195, baseline: ay + 1, size: 35, Draw.color(0xff3a3f46))
        d.rect(CGRect(x: 334, y: axis - 1, width: 1197 - 334, height: 2),
               Draw.color(0xffe1e1e1))

        // 最高月气泡 + 牵引线
        let year = Calendar.current.component(.year, from: Date()) + s.yearOffset
        let bx = x0 + pitch * CGFloat(maxIndex)
        let bw: CGFloat = 331, btop = top + 226
        d.round(CGRect(x: bx - bw / 2, y: btop, width: bw, height: 140), radius: 26, blue)
        d.path([CGPoint(x: bx - 22, y: btop + 140), CGPoint(x: bx, y: btop + 175),
                CGPoint(x: bx + 22, y: btop + 140)], fill: blue)
        d.rect(CGRect(x: bx - 1.5, y: btop + 175, width: 3,
                      height: axis - CGFloat(mv[maxIndex] / mx) * plotH - 6 - (btop + 175)),
               Draw.color(0xffdbe9ff))
        d.centerText("\(year)年\(maxIndex + 1)月 最多", cx: bx, baseline: btop + 56, size: 40,
                     .white)
        d.centerText(Self.money(mv[maxIndex]) + "  ▶", cx: bx, baseline: btop + 110, size: 36,
                     .white, bold: true)
        hot(CGRect(x: bx - bw / 2, y: btop, width: bw, height: 180), "yi.m:\(maxIndex)")
        d.centerText("— 月收入均值", cx: 668, baseline: top + 1480, size: 34, grey)

        // 收入排行
        let ry = top + 1614
        d.round(CGRect(x: 41, y: ry, width: 1237 - 41, height: 1020), radius: 40, cardColor)
        cardTitle(d, "bar", "收入排行", x: 87, baseline: ry + 81, size: 54)
        let names = ["转账-转转循环科技有限公司", "转账-染", "转账-转转循环科技有限公司"]
        let times = ["06-27 13:20", "08-13 19:44", "06-27 13:29"]
        let amounts = [model.value("yi.r.0", 1452.00), model.value("yi.r.1", 1400.00),
                       model.value("yi.r.2", 214.00)]
        var y = ry + 161
        for i in 0..<3 {
            d.round(CGRect(x: 82, y: y, width: 1197 - 82, height: 217), radius: 26,
                    Draw.color(0xfff2f3f7))
            d.centerText("\(i + 1)", cx: 132, baseline: y + 113, size: 44,
                         Draw.color(0xff3a3f46))
            d.textSqueezed(names[i], x: 189, baseline: y + 85, size: 44, ink, squeeze: 0.88)
            d.text("转账红包", x: 189, baseline: y + 154, size: 36, grey)
            d.rightText(Self.money(amounts[i]), right: 1155, baseline: y + 90, size: 40, ink,
                        bold: true)
            d.rightText(times[i], right: 1155, baseline: y + 150, size: 32, grey)
            hot(CGRect(x: 82, y: y, width: 1115, height: 217), "yi.r:\(i)")
            y += 246
        }
        d.centerText("展开全部", cx: 610, baseline: ry + 946, size: 36, grey)
        chevron(d, x: 715, cy: ry + 934, color: grey)
        hot(CGRect(x: 400, y: ry + 910, width: 480, height: 80), "yi.rank.expand")

        moreBannerIncome(d, ry + 1065)
        footer(d, ry + 1293)
    }

    /// 收入年度的「更多分析」条（Java:1381-1396）
    private func moreBannerIncome(_ d: Draw, _ top: CGFloat) {
        let l: CGFloat = 41, r: CGFloat = 1237, h: CGFloat = 170
        let box = CGRect(x: l, y: top, width: r - l, height: h)
        d.clipped(to: box, radius: 24) {
            d.gradient(box, from: Draw.color(0xfffff8f5), to: Draw.color(0xfffff3e6),
                       vertical: false)
        }
        if let image = art["moreIn"] {
            d.clipped(to: CGRect(x: r - 330, y: top, width: 330, height: h), radius: 24) {
                d.art(image, CGRect(x: r - 330, y: top, width: 330, height: h))
            }
        }
        d.text("更多分析", x: l + 60, baseline: top + 92, size: 56, ink, bold: true)
        d.text("理财分析", x: l + 298, baseline: top + 90, size: 36, grey)
        hot(box, "more")
    }

    /// 城市名按字数折行（Java 的 `wrapCity`）
    private static func wrapCity(_ name: String, limit: Int) -> [String] {
        let chars = Array(name)
        if chars.count <= limit { return [name, ""] }
        return [String(chars.prefix(limit)), String(chars.dropFirst(limit))]
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 顶部固定栏先算（它不随滚动移动）
        for hot in hots.reversed()
        where hot.rect.contains(CGPoint(x: x, y: y + (hot.rect.minY < Self.navHeight ? s.scroll : 0))) {
            return hot.token
        }
        for hot in hots.reversed()
        where hot.rect.contains(CGPoint(x: x, y: y + s.scroll)) {
            return hot.token
        }
        return "noop"
    }

    // MARK: - 工具

    private static func tabOf(_ index: Int) -> BillAnalyticsModel.Tab {
        index == 0 ? .week : (index == 1 ? .month : .year)
    }

    private static func mondayOf(_ date: Date, offset: Int) -> Date {
        let calendar = Calendar.current
        let shifted = calendar.date(byAdding: .day, value: offset * 7, to: date) ?? date
        let dow = calendar.component(.weekday, from: shifted)   // SUN = 1
        let back = dow == 1 ? 6 : dow - 2
        let monday = calendar.date(byAdding: .day, value: -back, to: shifted) ?? shifted
        return calendar.startOfDay(for: monday)
    }

    /// 两位小数（不补千分位）
    static func trim2(_ value: Double) -> String { String(format: "%.2f", value) }

    /// 千分位两位小数
    static func money(_ value: Double) -> String { ReceiptStore.money(value) }

    /// 整数显示（去掉 .00）
    static func integer(_ value: Double) -> String {
        value == value.rounded() ? String(Int(value)) : String(format: "%.2f", value)
    }

    static func pct(_ value: CGFloat) -> String { String(format: "%.2f%%", value) }
}
