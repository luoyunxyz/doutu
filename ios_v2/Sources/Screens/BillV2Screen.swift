import UIKit

/// 「账单」列表页 —— `features/zhi/ui/BillV2Screen.java` 的 1:1 移植（画布 1280×2816，头部固定 462）。
///
/// 结构：固定的顶部条（返回 / 搜索框 / 筛选五页签 / 筛选）+ 可滚动的按月卡片。
/// 每张月卡顶部是「N 月 + 支出/收入两个数字 + 收支分析按钮」；当月那张还会多一条
/// 「本月已省」的淡蓝渐变带 + 存钱插画；下面是一行行账单。
///
/// **文字统一 0.94 横向压缩**（Java 的 `SX`），且所有宽度测量都按压缩后的宽度算 —— 不照抄这点，
/// 省略号会提前出现、右对齐的金额会偏。
final class BillV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 顶部条固定、只有月卡区域跟着滚：draw 里自己做了 `translate(0, headH - scroll)`，
    /// 不能再让宿主整页滚一次（否则顶部条会跟着走、内容双倍位移）。
    let selfScrolls = true
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let headH: CGFloat = 462
    static let rowH: CGFloat = 348

    final class State: ScreenState {
        /// 当前页签：全部 / 支出 / 转账 / 退款 / 订单
        var kind = "全部"
        var query = ""
    }

    private let sx: CGFloat = 0.94

    // MARK: - 几何与调色板（Java:13-17）
    private let cardL: CGFloat = 36
    private let cardR: CGFloat = 1244
    private let cardRadius: CGFloat = 52
    private let textX: CGFloat = 224
    private let valueRight: CGFloat = 1200
    private let markCx: CGFloat = 130
    private let markR: CGFloat = 52

    private let pageBg = Draw.color(0xfff5f5f5)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff333333)
    private let grey = Draw.color(0xffaaaaaa)
    private let blue = Draw.color(0xff1677ff)
    private let divider = Draw.color(0xfffafafa)

    private let chips = ["全部", "支出", "转账", "退款", "订单"]

    /// 8 张品牌图标 + 4 张界面切图（Java:30-36、194-198）
    private var logos: [String: UIImage] = [:]
    private var savingArt: UIImage?
    private var backArt: UIImage?
    private var searchArt: UIImage?
    private var moreArt: UIImage?

    /// 布局结果
    private var laid: [BillMonth] = []
    private var tops: [CGFloat] = []
    private var contentH: CGFloat = 0

    init() {
        for (name, res) in BillLogos.assets {
            if let image = UIImage(named: res) { logos[name] = image }
        }
        savingArt = UIImage(named: "bill_art_saving")
        backArt = UIImage(named: "bill_art_back_list")
        searchArt = UIImage(named: "bill_art_search")
        moreArt = UIImage(named: "bill_art_more")
    }

    // MARK: - 布局（Java:101-127）

    private func isCurrent(_ month: BillMonth) -> Bool {
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        return month.year == now.year && month.month == now.month
    }

    private func header(_ month: BillMonth) -> CGFloat { isCurrent(month) ? 480 : 340 }

    private func layout(_ state: State) {
        let store = BillStore.shared
        if store.months.isEmpty { store.generate() }

        laid = []
        for month in store.months(kind: state.kind) {
            let copy = BillMonth(year: month.year, month: month.month)
            for bill in month.bills where BillStore.matches(bill, query: state.query) {
                copy.bills.append(bill)
            }
            if !copy.bills.isEmpty { laid.append(copy) }
        }
        // 账单被清空后，本月那张卡（连同「收支分析」入口）还是要留着
        if !laid.contains(where: { isCurrent($0) }) {
            let now = Calendar.current.dateComponents([.year, .month], from: Date())
            laid.insert(BillMonth(year: now.year ?? 2026, month: now.month ?? 1), at: 0)
        }
        tops = []
        var top: CGFloat = 16
        for month in laid {
            tops.append(top)
            top += header(month) + CGFloat(month.bills.count) * Self.rowH + 32 + 40
        }
        contentH = top
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        layout(s)
        return max(0, contentH - (Self.H - Self.headH) + 70)
    }

    // MARK: - 绘制（Java:128-136）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        layout(s)
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: Self.headH, width: Self.W, height: Self.H - 36 - Self.headH))
        d.ctx.translateBy(x: 0, y: Self.headH - s.scroll)
        for (i, month) in laid.enumerated() {
            drawMonth(d, month, tops[i])
        }
        let rows = laid.reduce(0) { $0 + $1.bills.count }
        if rows == 0 && !laid.isEmpty {
            d.centerText("没有符合条件的账单", cx: Self.W / 2,
                         baseline: tops[0] + header(laid[0]) + 120, size: 44, grey)
        }
        d.ctx.restoreGState()

        drawBar(d, s)
    }

    /// 一张月卡（Java:137-160）
    private func drawMonth(_ d: Draw, _ month: BillMonth, _ top: CGFloat) {
        let h = header(month)
        d.round(CGRect(x: cardL, y: top, width: cardR - cardL,
                       height: h + CGFloat(month.bills.count) * Self.rowH + 32),
                radius: cardRadius, cardColor)

        let number = "\(month.month)"
        d.textSqueezed(number, x: 86, baseline: top + 142, size: 106, ink,
                       bold: true, squeeze: sx)
        let end = 86 + width(number, 106)
        d.textSqueezed("月", x: end + 5, baseline: top + 145, size: 40, ink, squeeze: sx)
        caret(d, x: end + 84, y: top + 126, half: 15, color: ink)

        d.textSqueezed("支出", x: 96, baseline: top + 223, size: 39, grey, squeeze: sx)
        d.textSqueezed("收入", x: 524, baseline: top + 223, size: 39, grey, squeeze: sx)
        // 金额放不下就自动缩字号，别顶到右边的徽标
        amountText(d, "¥ " + Self.money(month.expense), x: 96, baseline: top + 292,
                   size: 50, maxRight: 505, color: ink)
        amountText(d, "¥ " + Self.money(month.income), x: 524, baseline: top + 292,
                   size: 50, maxRight: 742, color: ink)

        let ay = isCurrent(month) ? top + 340 : top + 198
        if isCurrent(month) {
            let band = CGRect(x: cardL, y: top + 320, width: cardR - cardL, height: 478 - 320)
            d.gradient(band, from: .white, to: Draw.color(0xffeaf6ff), vertical: true)
            d.textSqueezed("本月已省", x: 96, baseline: top + 410, size: 42, ink, squeeze: sx)
            d.textSqueezed("0.00元", x: 285, baseline: top + 410, size: 42,
                           Draw.color(0xffff6666), bold: true, squeeze: sx)
            chevron(d, x: 453, cy: top + 393, color: grey)
            if let art = savingArt { d.art(art, CGRect(x: 750, y: top + 138,
                                                       width: art.size.width * art.scale,
                                                       height: art.size.height * art.scale)) }
            d.textSqueezed("\(Int(BillStore.shared.savedThisYear.rounded()))笔", x: 892,
                           baseline: top + 247, size: 35, Draw.color(0xff385d7c), squeeze: sx)
        }

        d.round(CGRect(x: 950, y: ay, width: 1200 - 950, height: 108), radius: 54,
                Draw.color(0xff168bff))
        d.centerTextSqueezed("收支分析", cx: 1075, baseline: ay + 71, size: 39, .white,
                             squeeze: sx)

        for (i, bill) in month.bills.enumerated() {
            drawRow(d, bill, top + h + CGFloat(i) * Self.rowH, i == month.bills.count - 1)
        }
    }

    /// 一行账单（Java:161-177）
    private func drawRow(_ d: Draw, _ bill: Bill, _ top: CGFloat, _ last: Bool) {
        if let logo = logos[bill.merchant] ?? logos[bill.mark] {
            drawLogo(d, logo, top)
        } else if let mark = Self.markImage(bill) {
            d.art(mark, CGRect(x: markCx - markR, y: top + 48, width: markR * 2, height: markR * 2))
        }

        let amount = (bill.income ? "+" : "-") + Self.money(abs(bill.amount))
        let aw = width(amount, 53)
        textEllipsis(d, bill.merchant, x: textX, baseline: top + 100, size: 47, color: ink,
                     bold: false, limit: valueRight - aw - 40)
        d.textSqueezed(bill.category, x: textX, baseline: top + 185, size: 39, grey, squeeze: sx)
        d.textSqueezed(Self.timeText(bill.time), x: textX, baseline: top + 264, size: 37, grey,
                       squeeze: sx)
        d.rightTextSqueezed(amount, right: valueRight, baseline: top + 100, size: 53,
                            bill.income ? Draw.color(0xffff5364) : ink,
                            bold: true, squeeze: sx)
        if !bill.status.isEmpty {
            d.rightTextSqueezed(bill.status, right: valueRight, baseline: top + 185, size: 38,
                                grey, squeeze: sx)
        }
        if !last {
            d.rect(CGRect(x: textX, y: top + Self.rowH, width: valueRight - textX, height: 2),
                   divider)
        }
    }

    /// 品牌真图标：白底圆角方块 + 等比缩放居中（Java:180-190）
    private func drawLogo(_ d: Draw, _ image: UIImage, _ top: CGFloat) {
        let l = markCx - markR
        let t = top + 48
        let box = CGRect(x: l, y: t, width: markR * 2, height: markR * 2)
        d.round(box, radius: 26, .white)

        let iw = image.size.width * image.scale
        let ih = image.size.height * image.scale
        guard iw > 0, ih > 0 else { return }
        let pad: CGFloat = 9
        let availW = box.width - pad * 2
        let availH = box.height - pad * 2
        let scale = min(availW / iw, availH / ih)
        let w = iw * scale
        let h = ih * scale
        d.art(image, CGRect(x: l + pad + (availW - w) / 2, y: t + pad + (availH - h) / 2,
                            width: w, height: h))
    }

    /// 固定顶部条（Java:191-205）
    private func drawBar(_ d: Draw, _ s: State) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.headH), pageBg)
        if let art = backArt { d.art(art, CGRect(x: 38, y: 186, width: art.size.width * art.scale,
                                                 height: art.size.height * art.scale)) }
        d.round(CGRect(x: 140, y: 166, width: 1098 - 140, height: 278 - 166), radius: 56,
                cardColor)
        if let art = searchArt { d.art(art, CGRect(x: 168, y: 192, width: art.size.width * art.scale,
                                                   height: art.size.height * art.scale)) }
        textEllipsis(d, s.query.isEmpty ? "搜索交易记录" : s.query, x: 244, baseline: 242, size: 47,
                     color: Draw.color(0xffb0b0b0), bold: false, limit: 890)
        d.rect(CGRect(x: 914, y: 184, width: 2, height: 258 - 184), Draw.color(0xffeeeeee))
        d.textSqueezed("搜索", x: 953, baseline: 242, size: 43, Draw.color(0xffaaaaaa), squeeze: sx)
        if let art = moreArt { d.art(art, CGRect(x: 1150, y: 201, width: art.size.width * art.scale,
                                                 height: art.size.height * art.scale)) }

        for (i, chip) in chips.enumerated() {
            let left = 40 + CGFloat(i) * 207
            d.round(CGRect(x: left, y: 333, width: 179, height: 435 - 333), radius: 51, .white)
            d.centerTextSqueezed(chip, cx: left + 89, baseline: 403, size: 45,
                                 s.kind == chip ? blue : Draw.color(0xff999999),
                                 bold: s.kind == chip, squeeze: sx)
        }
        d.textSqueezed("筛选", x: 1088, baseline: 403, size: 43, Draw.color(0xff666666), squeeze: sx)
        caret(d, x: 1220, y: 383, half: 17, color: ink)
    }

    // MARK: - 工具

    /// 压缩后的文字宽度（Java:243-246）
    private func width(_ value: String, _ size: CGFloat) -> CGFloat {
        Draw.width(value, size: size, bold: true) * sx
    }

    /// 金额：放不下就一点点缩字号（Java:268-277）
    private func amountText(_ d: Draw, _ value: String, x: CGFloat, baseline: CGFloat,
                            size: CGFloat, maxRight: CGFloat, color: UIColor) {
        var s = size
        while s > 26 && x + Draw.width(value, size: s, bold: true) * sx > maxRight {
            s -= 2
        }
        d.textSqueezed(value, x: x, baseline: baseline, size: s, color, bold: true, squeeze: sx)
    }

    /// 截断加省略号（Java:280-292）
    private func textEllipsis(_ d: Draw, _ value: String, x: CGFloat, baseline: CGFloat,
                             size: CGFloat, color: UIColor, bold: Bool, limit: CGFloat) {
        if x + Draw.width(value, size: size, bold: bold) * sx <= limit {
            d.textSqueezed(value, x: x, baseline: baseline, size: size, color,
                           bold: bold, squeeze: sx)
            return
        }
        var cut = value
        while !cut.isEmpty && x + Draw.width(cut + "…", size: size, bold: bold) * sx > limit {
            cut.removeLast()
        }
        d.textSqueezed(cut + "…", x: x, baseline: baseline, size: size, color,
                       bold: bold, squeeze: sx)
    }

    /// 右箭头（Java 的 `chevron`）
    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: x, y: cy - 14))
        d.ctx.addLine(to: CGPoint(x: x + 18, y: cy))
        d.ctx.addLine(to: CGPoint(x: x, y: cy + 14))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 下箭头（「筛选」按钮旁，Java 的 `caret`）
    private func caret(_ d: Draw, x: CGFloat, y: CGFloat, half: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x - half, y: y - half / 2))
        d.ctx.addLine(to: CGPoint(x: x, y: y + half / 2))
        d.ctx.addLine(to: CGPoint(x: x + half, y: y - half / 2))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 品牌图标（BillMarks 的那 8 张）
    private static func markImage(_ bill: Bill) -> UIImage? {
        let names: [String: String] = [
            "云猫": "bill_logo_cat", "花呗": "bill_logo_huabei", "闲鱼": "bill_logo_xianyu",
            "移动": "bill_logo_mobile", "公交": "bill_logo_bus", "交管": "bill_logo_traffic",
            "T": "bill_logo_token", "抖音": "bill_logo_douyin",
        ]
        guard let res = names[bill.mark] else { return nil }
        return UIImage(named: res)
    }

    /// 金额格式：千分位两位小数（Java 的 `%,.2f`）
    static func money(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.groupingSeparator = ","
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }

    /// 时间显示：今天 HH:mm / 昨天 HH:mm / MM-dd HH:mm（Java:228-234）
    static func timeText(_ when: TimeInterval) -> String {
        let date = Date(timeIntervalSince1970: when)
        let cal = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        if cal.isDateInToday(date) {
            formatter.dateFormat = "HH:mm"
            return "今天 " + formatter.string(from: date)
        }
        if cal.isDateInYesterday(date) {
            formatter.dateFormat = "HH:mm"
            return "昨天 " + formatter.string(from: date)
        }
        formatter.dateFormat = "MM-dd HH:mm"
        return formatter.string(from: date)
    }

    // MARK: - 命中（Java:206-225）

    /// 取出命中令牌指向的那条账单。
    ///
    /// 必须走和绘制同一套 `layout`（且用**同一个 state**）—— 命中的 `bill:<月>:<行>` 用的是
    /// 过滤、排序之后的下标，直接去 `BillStore.months` 里取会错位。
    func bill(at monthIndex: Int, row: Int, state: ScreenState) -> Bill? {
        let s = (state as? State) ?? State()
        layout(s)
        guard monthIndex >= 0, monthIndex < laid.count else { return nil }
        let bills = laid[monthIndex].bills
        guard row >= 0, row < bills.count else { return nil }
        return bills[row]
    }

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if y < Self.headH {
            if y < 310 {
                return x < 125 ? "back" : (x < 1120 ? "search" : "more")
            }
            for (i, chip) in chips.enumerated() {
                let left = 40 + CGFloat(i) * 207
                if x >= left && x <= left + 179 { return "chip:" + chip }
            }
            return "filter"
        }
        layout(s)
        let cy = y - Self.headH + s.scroll
        for (i, month) in laid.enumerated() {
            let local = cy - tops[i]
            if local < 0 { continue }
            let h = header(month)
            if local < h {
                let ay = isCurrent(month) ? CGFloat(340) : 198
                if local >= ay && local <= ay + 108 && x >= 950 && x <= 1200 {
                    return "analytics"
                }
                return "toast:\(month.month)月支出 ¥\(Self.money(month.expense)) · 收入 ¥\(Self.money(month.income))"
            }
            let row = Int((local - h) / Self.rowH)
            if row < month.bills.count { return "bill:\(i):\(row)" }
        }
        return "noop"
    }
}
