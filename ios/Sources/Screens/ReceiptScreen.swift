import UIKit

/// 「收款记录」列表页 —— `features/zhi/ui/ReceiptScreen.java` 的 1:1 移植（画布 1280×2816）。
///
/// 结构：头栏（返回 / 收款记录 / 全部账单）→ 「今日 + 日期」+ 蓝色「收钱攒积分」胶囊
/// → 按天分组的卡片（每张卡：笔数 / 总额 / 实际到账 / 虚线 / 一行行付款人）→ 页脚提示 + 「查看历史账单」
/// → **常驻的「请问我」助手**（画在滚动区之外，所以永远不动）。
///
/// 命中规则很特别（Java 注释里写明了）：**这一页除了五个明确的点，其余任何位置都触发「批量添加」** ——
/// 因为这一页本来就是用来加记录的，触发器做成整页而不是一条可能被助手挡住的窄条。
final class ReceiptScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    final class State: ScreenState {
    }

    private let squeeze: CGFloat = 0.94

    // MARK: - 调色板（Java:45-55）
    private let bg = Draw.color(0xfff5f5f5)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff17181a)
    private let dark = Draw.color(0xff3f3f3f)
    private let grey = Draw.color(0xff8f8f8f)
    private let greyDim = Draw.color(0xff878787)
    private let hair = Draw.color(0xffe4e4e4)
    private let dashColor = Draw.color(0xffdadada)
    private let blue = Draw.color(0xff1677ff)
    private let pillFill = Draw.color(0xffeef6ff)
    private let pillEdge = Draw.color(0xffcfe4fb)

    // MARK: - 几何（Java:57-148）
    private let cardL: CGFloat = 41
    private let cardR: CGFloat = 1239
    private let cardRadius: CGFloat = 26
    private let cardBase: CGFloat = 672
    private let rowStep: CGFloat = 182
    private let rowFirst: CGFloat = 479

    private let titleBase: CGFloat = 245.9
    private let titleSize: CGFloat = 65
    private let allX: CGFloat = 1229.2
    private let allBase: CGFloat = 240.3
    private let allSize: CGFloat = 53

    private let todayX: CGFloat = 84
    private let todayBase: CGFloat = 435.7
    private let todaySize: CGFloat = 53
    private let pill = CGRect(x: 884, y: 383, width: 1276 - 884, height: 455 - 383)
    private let pillRadius: CGFloat = 36
    private let pillCoin = CGRect(x: 912.6, y: 390.8, width: 965.5 - 912.6, height: 441.8 - 390.8)
    private let pillTextX: CGFloat = 985
    private let pillTextSize: CGFloat = 45
    private let pillChevronX: CGFloat = 1241

    private let firstCardTop: CGFloat = 489

    private let labelBase: CGFloat = 113.9
    private let labelSize: CGFloat = 50
    private let countBase: CGFloat = 226.2
    private let countSize: CGFloat = 78
    private let totalBase: CGFloat = 235.5
    private let totalSize: CGFloat = 94
    private let actualBase: CGFloat = 354.9
    private let actualSize: CGFloat = 51
    private let actualAmountBase: CGFloat = 355.8
    private let actualAmountSize: CGFloat = 52
    private let dashY: CGFloat = 335
    private let dashL: CGFloat = 358
    private let dashR: CGFloat = 972

    private let avatarRect = CGRect(x: 81.1, y: 479.3, width: 210.1 - 81.1, height: 608.2 - 479.3)
    private let nameX: CGFloat = 233
    private let nameBase: CGFloat = 43.3
    private let nameSize: CGFloat = 52
    private let timeX: CGFloat = 235
    private let timeBase: CGFloat = 124.2
    private let timeSize: CGFloat = 44
    private let amountRight: CGFloat = 1198
    private let amountBase: CGFloat = 91.8
    private let amountSize: CGFloat = 62

    private let dayX: CGFloat = 82
    private let daySize: CGFloat = 50
    private let dayGapAbove: CGFloat = 164.7
    private let dayGapBelow: CGFloat = 52.3

    private let notice1Base: CGFloat = 145.7
    private let notice1Size: CGFloat = 52
    private let notice2Base: CGFloat = 204.9
    private let notice2Size: CGFloat = 52
    private let noticeRuleY: CGFloat = 182.5
    private let noticeRuleGap: CGFloat = 10

    private let historyRect = CGRect(x: 434.7, y: 274.1, width: 843.8 - 434.7, height: 376.1 - 274.1)
    private let historyRadius: CGFloat = 14
    private let historySize: CGFloat = 50
    private let historyFill = Draw.color(0xff1a75f2)

    private let ask = CGRect(x: 455, y: 2500, width: 828 - 455, height: 2634 - 2500)
    private let askRadius: CGFloat = 67
    private let tail: CGFloat = 340
    private let askAvatar = CGRect(x: 451.1, y: 2496.1, width: 589.3 - 451.1, height: 2634.3 - 2496.1)
    private let askTextX: CGFloat = 620
    private let askTextBase: CGFloat = 2585
    private let askTextSize: CGFloat = 56

    private var avatar: UIImage?
    private var coin: UIImage?
    private var askAvatarImage: UIImage?

    /// 一天的记录 + 它的卡片位置（Java:165-171）
    private struct DayGroup {
        var day: TimeInterval
        var today = false
        var cardTop: CGFloat = 0
        var cardBottom: CGFloat = 0
        var items: [Receipt] = []
    }

    private var groups: [DayGroup] = []
    private var contentBottom: CGFloat = 0

    init() {
        avatar = UIImage(named: "rec_avatar")
        coin = UIImage(named: "rec_coin")
        askAvatarImage = UIImage(named: "rec_ask_avatar")
    }

    // MARK: - 布局（Java:186-231）

    private func cardHeight(_ rows: Int) -> CGFloat {
        cardBase + max(0, CGFloat(rows - 1)) * rowStep
    }

    private func layout(_ data: [Receipt]) {
        groups = []
        let today = ReceiptStore.startOfDay(Date().timeIntervalSince1970)

        for item in data {
            let day = ReceiptStore.startOfDay(item.time)
            if let index = groups.firstIndex(where: { $0.day == day }) {
                groups[index].items.append(item)
            } else {
                var group = DayGroup(day: day)
                group.today = day == today
                group.items = [item]
                groups.append(group)
            }
        }
        // 「今日」那张卡排最前（它属于页头的一部分）
        if let index = groups.firstIndex(where: { $0.today }) {
            let head = groups.remove(at: index)
            groups.insert(head, at: 0)
        }

        var y: CGFloat = firstCardTop
        for i in groups.indices {
            groups[i].cardTop = groups[i].today
                ? firstCardTop : y + dayGapAbove + dayGapBelow
            groups[i].cardBottom = groups[i].cardTop + cardHeight(groups[i].items.count)
            y = groups[i].cardBottom
        }
        contentBottom = groups.isEmpty ? firstCardTop : y + historyRect.maxY
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        layout(ReceiptStore.shared.records)
        return max(0, contentBottom + tail - Self.H)
    }

    // MARK: - 绘制（Java:235-258）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let data = ReceiptStore.shared.records
        layout(data)

        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), bg)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        drawHeader(d)
        drawToday(d)
        for group in groups {
            if !group.today { drawDayHeading(d, group) }
            drawGroupCard(d, group)
        }
        drawNotice(d)
        d.ctx.restoreGState()

        // 头栏被推走之后，状态栏落在浅色内容上
        if s.scroll > 40 {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: 110), bg)
        }
        drawAsk(d)
    }

    private func drawHeader(_ d: Draw) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(ink.cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: 84.7, y: 190.2))
        d.ctx.addLine(to: CGPoint(x: 49.5, y: 219.9))
        d.ctx.addLine(to: CGPoint(x: 84.7, y: 249.6))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        d.centerTextSqueezed("收款记录", cx: Self.W / 2, baseline: titleBase, size: titleSize,
                             .black, bold: true, squeeze: squeeze)
        d.rightTextSqueezed("全部账单", right: allX, baseline: allBase, size: allSize,
                            greyDim, squeeze: squeeze)
    }

    private func drawToday(_ d: Draw) {
        d.textSqueezed("今日", x: todayX, baseline: todayBase, size: todaySize,
                       Draw.color(0xff8a8a8a), squeeze: squeeze)
        let date = Self.monthDay(Date().timeIntervalSince1970)
        let x = todayX + Draw.width("今日", size: todaySize) * squeeze + 16
        d.textSqueezed(date, x: x, baseline: todayBase, size: todaySize,
                       Draw.color(0xff7d7d7d), squeeze: squeeze)

        d.round(pill, radius: pillRadius, pillFill)
        d.roundStroke(pill, radius: pillRadius, pillEdge, width: 2.4)
        if let coin { d.art(coin, pillCoin) }
        d.textSqueezed("收钱攒积分", x: pillTextX, baseline: 435.1, size: pillTextSize,
                       Draw.color(0xff2b3238), squeeze: squeeze)
        chevron(d, x: pillChevronX, cy: 419, color: blue)
    }

    private func drawDayHeading(_ d: Draw, _ group: DayGroup) {
        d.textSqueezed(ReceiptStore.dayLabel(group.day), x: dayX,
                       baseline: group.cardTop - dayGapBelow, size: daySize,
                       Draw.color(0xff8c8c8c), squeeze: squeeze)
    }

    private func drawGroupCard(_ d: Draw, _ group: DayGroup) {
        let top = group.cardTop
        d.round(CGRect(x: cardL, y: top, width: cardR - cardL, height: group.cardBottom - top),
                radius: cardRadius, cardColor)

        var total: Double = 0
        var actual: Double = 0
        for item in group.items {
            total += item.amount
            actual += item.actual
        }

        d.textSqueezed("收款笔数", x: 84, baseline: top + labelBase, size: labelSize, grey,
                       squeeze: squeeze)
        d.rightTextSqueezed("收款总额", right: 1197, baseline: top + labelBase, size: labelSize,
                            grey, squeeze: squeeze)
        d.textSqueezed("\(group.items.count)笔", x: 82, baseline: top + countBase, size: countSize,
                       ink, bold: true, squeeze: squeeze)
        d.rightTextSqueezed(ReceiptStore.yuan(total), right: 1197, baseline: top + totalBase,
                            size: totalSize, ink, bold: true, squeeze: squeeze)
        d.textSqueezed("实际到账", x: 81, baseline: top + actualBase, size: actualSize, dark,
                       squeeze: squeeze)
        d.rightTextSqueezed("¥ " + ReceiptStore.money(actual), right: 1199,
                            baseline: top + actualAmountBase, size: actualAmountSize, ink,
                            squeeze: squeeze)
        dashed(d, y: top + dashY)

        for (i, item) in group.items.enumerated() {
            let rowTop = top + rowFirst + CGFloat(i) * rowStep
            let box = CGRect(x: avatarRect.minX, y: rowTop, width: avatarRect.width,
                             height: avatarRect.height)
            if let avatar {
                d.clipped(to: box, radius: box.width / 2) {
                    d.art(avatar, box)
                }
            }
            d.textSqueezed(item.payer, x: nameX, baseline: rowTop + nameBase, size: nameSize,
                           ink, bold: true, squeeze: squeeze)
            d.textSqueezed(ReceiptStore.clock(item.time), x: timeX, baseline: rowTop + timeBase,
                           size: timeSize, grey, squeeze: squeeze)
            d.rightTextSqueezed(item.incomeText, right: amountRight,
                                baseline: rowTop + amountBase, size: amountSize, ink,
                                bold: true, squeeze: squeeze)
        }
    }

    /// 页脚（Java:331-343）
    private func drawNotice(_ d: Draw) {
        let base = contentBottom - historyRect.maxY
        let notice1 = "由于功能更新"
        let notice2 = "当前只展示5月4日以后的账单"
        let half = Draw.width(notice2, size: notice2Size) * squeeze / 2 + noticeRuleGap
        d.rect(CGRect(x: cardL, y: base + noticeRuleY, width: Self.W / 2 - half - cardL,
                      height: 2), hair)
        d.rect(CGRect(x: Self.W / 2 + half, y: base + noticeRuleY,
                      width: cardR - (Self.W / 2 + half), height: 2), hair)
        d.centerTextSqueezed(notice1, cx: Self.W / 2, baseline: base + notice1Base,
                             size: notice1Size, Draw.color(0xff7e7e7e), squeeze: squeeze)
        d.centerTextSqueezed(notice2, cx: Self.W / 2, baseline: base + notice2Base,
                             size: notice2Size, Draw.color(0xff7e7e7e), squeeze: squeeze)

        let history = CGRect(x: historyRect.minX, y: base + historyRect.minY,
                             width: historyRect.width, height: historyRect.height)
        d.round(history, radius: historyRadius, historyFill)
        d.centerTextSqueezed("查看历史账单", cx: Self.W / 2, baseline: history.minY + 69,
                             size: historySize, .white, squeeze: squeeze)
    }

    /// 常驻的「请问我」助手（Java:346-360）：画在滚动区之外
    private func drawAsk(_ d: Draw) {
        d.round(ask, radius: askRadius, .white)
        // 描边是一支斜向渐变（紫 → 蓝）
        d.ctx.saveGState()
        d.ctx.addPath(UIBezierPath(roundedRect: ask, cornerRadius: askRadius).cgPath)
        d.ctx.clip()
        d.gradient(ask, from: Draw.color(0xffcdb8f5), to: Draw.color(0xff9dc4ee), vertical: true)
        d.ctx.restoreGState()
        d.ctx.saveGState()
        d.ctx.addPath(UIBezierPath(roundedRect: ask, cornerRadius: askRadius).cgPath)
        d.ctx.setStrokeColor(Draw.color(0xff9dc4ee).cgColor)
        d.ctx.setLineWidth(3.2)
        d.ctx.strokePath()
        d.ctx.restoreGState()
        if let askAvatarImage { d.art(askAvatarImage, askAvatar) }
        d.textSqueezed("请问我", x: askTextX, baseline: askTextBase, size: askTextSize,
                       Draw.color(0xff2b2f36), bold: true, squeeze: squeeze)
    }

    /// 虚线（Java 的 `dashed`）
    private func dashed(_ d: Draw, y: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(dashColor.cgColor)
        d.ctx.setLineWidth(3)
        d.ctx.setLineDash(phase: 0, lengths: [14, 12])
        d.ctx.move(to: CGPoint(x: dashL, y: y))
        d.ctx.addLine(to: CGPoint(x: dashR, y: y))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x, y: cy - 14))
        d.ctx.addLine(to: CGPoint(x: x + 18, y: cy))
        d.ctx.addLine(to: CGPoint(x: x, y: cy + 14))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// `M月d日`（Java 的 `monthDay`）
    private static func monthDay(_ time: TimeInterval) -> String {
        let comps = Calendar.current.dateComponents([.month, .day],
                                                    from: Date(timeIntervalSince1970: time))
        return "\(comps.month ?? 1)月\(comps.day ?? 1)日"
    }

    // MARK: - 命中（Java:376-412）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        layout(ReceiptStore.shared.records)
        let cy = y + s.scroll

        if cy < 300 {
            if x < 130 { return "back" }
            if x > allX - 240 { return "all" }
            return "batch"
        }
        if pill.minY < cy && cy < pill.maxY && x > pill.minX - 20 { return "points" }
        for group in groups {
            if cy < group.cardTop || cy > group.cardBottom { continue }
            if group.items.isEmpty { return "batch" }
            for (i, item) in group.items.enumerated() {
                let rowTop = group.cardTop + rowFirst + CGFloat(i) * rowStep
                if cy >= rowTop - 10 && cy <= rowTop + rowStep - 10 {
                    if x < avatarRect.maxX + 24 { return "avatar:\(item.id)" }
                    return "entry:\(item.id)"
                }
            }
            return "batch"
        }
        let base = contentBottom - historyRect.maxY
        if cy > base + historyRect.minY - 10 && cy < base + historyRect.maxY + 10
            && x > historyRect.minX - 10 && x < historyRect.maxX + 10 {
            return "history"
        }
        // 其余任何位置（含今日标题、卡片间隙、末尾留白、助手本体）都是「批量添加」
        return "batch"
    }
}
