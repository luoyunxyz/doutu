import UIKit

/// 「收款详情」页 —— `features/zhi/ui/ReceiptDetailScreen.java` 的 1:1 移植（画布 1280×2816）。
///
/// 结构：头栏（返回 + 收款详情）→ 金额卡（收款金额大字 + 实际到账 + 三行明细 + 更多）
/// → 顾客画像卡（头像 + 付款人 + 消费记录入口 + 三列数字）→ 两个按钮（联系顾客 / 退款）→ 页脚（理赔说明 + 在线帮助）。
///
/// 文字统一 **0.94 横向压缩**（Java 的 `TEXT_SQUEEZE`）。
final class ReceiptDetailScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    final class State: ScreenState {
        var receipt: Receipt?
    }

    private let squeeze: CGFloat = 0.94

    // MARK: - 调色板（Java:34-41）
    private let bg = Draw.color(0xfff5f5f5)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff17181a)
    private let grey = Draw.color(0xff949494)
    private let rowLabel = Draw.color(0xff565656)
    private let rowValue = Draw.color(0xff333333)
    private let separator = Draw.color(0xffeaeaea)
    private let blue = Draw.color(0xff1677ff)

    // MARK: - 几何（Java:43-95）
    private let card1 = CGRect(x: 41, y: 341.7, width: 1239 - 41, height: 1343.5 - 341.7)
    private let card2 = CGRect(x: 41, y: 1373.2, width: 1239 - 41, height: 1940.8 - 1373.2)
    private let button1 = CGRect(x: 41, y: 1969.6, width: 1239 - 41, height: 2135.6 - 1969.6)
    private let button2 = CGRect(x: 41, y: 2163.3, width: 1239 - 41, height: 2331.2 - 2163.3)
    private let cardRadius: CGFloat = 24

    private let titleX: CGFloat = 138
    private let titleBase: CGFloat = 245.9
    private let titleSize: CGFloat = 68

    private let label1Base: CGFloat = 473.9
    private let label1Size: CGFloat = 55
    private let amountBase: CGFloat = 614.9
    private let amountSize: CGFloat = 122
    private let actualBase: CGFloat = 731.9
    private let actualSize: CGFloat = 58

    private let rowLabelX: CGFloat = 85
    private let rowValueRight: CGFloat = 1173
    private let rowLabelSize: CGFloat = 51
    private let rowValueSize: CGFloat = 52
    private let rowBase: [CGFloat] = [937.7, 1030.4, 1122.2]
    private let rowCoin = CGRect(x: 659.4, y: 888.5, width: 719.7 - 659.4, height: 947.9 - 888.5)
    private let row1ValueX: CGFloat = 735
    private let rowChevronTip: CGFloat = 1196
    private let moreBase: CGFloat = 1264.1
    private let moreSize: CGFloat = 56

    private let avatar = CGRect(x: 80.3, y: 1411.4, width: 190.7 - 80.3, height: 1521.8 - 1411.4)
    private let payerX: CGFloat = 218.6
    private let payerBase: CGFloat = 1484
    private let payerSize: CGFloat = 50
    private let consumerRight: CGFloat = 1156

    private let colCx: [CGFloat] = [240, 639, 1040]
    private let colLabels = ["本月消费", "累计消费", "平均单价"]
    private let colLabelBase: CGFloat = 1690
    private let colLabelSize: CGFloat = 50
    private let colValueBase: CGFloat = 1755.7
    private let colValueSize: CGFloat = 62
    private let colCountBase: CGFloat = 1839.1
    private let colCountSize: CGFloat = 50
    private let colSeparator: [CGFloat] = [438, 838]
    private let colSeparatorTop: CGFloat = 1624
    private let colSeparatorBottom: CGFloat = 1842

    private let buttonTextSize: CGFloat = 66
    private let claimBase: CGFloat = 2610.7
    private let claimSize: CGFloat = 51
    private let helpBase: CGFloat = 2714.7
    private let helpSize: CGFloat = 51

    private var coin: UIImage?
    private var fallback: UIImage?

    init() {
        coin = UIImage(named: "rec_coin")
        fallback = UIImage(named: "rec_avatar_sq")
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:113-122）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), bg)
        guard let item = s.receipt else { return }
        let stats = ReceiptStore.shared.stats(item.payer)

        drawHeader(d)
        drawAmountCard(d, item)
        drawPictureCard(d, item, stats)
        drawButtons(d)
        drawFooter(d)
    }

    /// 头栏（Java:124-133）
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
        d.textSqueezed("收款详情", x: titleX, baseline: titleBase, size: titleSize,
                       Draw.color(0xff222222), bold: true, squeeze: squeeze)
    }

    /// 金额卡（Java:135-160）
    private func drawAmountCard(_ d: Draw, _ item: Receipt) {
        d.round(card1, radius: cardRadius, cardColor)
        d.centerTextSqueezed("收款金额", cx: Self.W / 2, baseline: label1Base, size: label1Size,
                             grey, squeeze: squeeze)
        d.centerTextSqueezed(ReceiptStore.yuan(item.amount), cx: Self.W / 2, baseline: amountBase,
                             size: amountSize, Draw.color(0xff2b2b2b), bold: true, squeeze: squeeze)
        d.centerTextSqueezed("实际到账 " + ReceiptStore.yuan(item.actual), cx: Self.W / 2,
                             baseline: actualBase, size: actualSize,
                             Draw.color(0xff2b2b2b), squeeze: squeeze)

        d.textSqueezed("收钱有奖", x: rowLabelX, baseline: rowBase[0], size: rowLabelSize,
                       rowLabel, squeeze: squeeze)
        if let coin { d.art(coin, rowCoin) }
        d.textSqueezed(item.prize, x: row1ValueX, baseline: rowBase[0], size: rowValueSize,
                       rowValue, squeeze: squeeze)
        chevron(d, x: rowChevronTip, cy: rowBase[0] - 15, color: Draw.color(0xff9a9a9a))

        d.textSqueezed("提现免费额度", x: rowLabelX, baseline: rowBase[1], size: rowLabelSize,
                       rowLabel, squeeze: squeeze)
        d.rightTextSqueezed("新增 " + ReceiptStore.money(item.quota), right: rowValueRight,
                            baseline: rowBase[1], size: rowValueSize, rowValue, squeeze: squeeze)
        chevron(d, x: rowChevronTip, cy: rowBase[1] - 15, color: Draw.color(0xff9a9a9a))

        d.textSqueezed("交易说明", x: rowLabelX, baseline: rowBase[2], size: rowLabelSize,
                       rowLabel, squeeze: squeeze)
        d.rightTextSqueezed(item.note, right: 1198, baseline: rowBase[2], size: rowValueSize,
                            rowValue, squeeze: squeeze)

        // 「更多」与它的下箭头一起居中
        let moreWidth = Draw.width("更多", size: moreSize) * squeeze
        let moreLeft = Self.W / 2 - 78
        d.textSqueezed("更多", x: moreLeft, baseline: moreBase, size: moreSize, grey,
                       squeeze: squeeze)
        chevronDown(d, cx: moreLeft + moreWidth + 24, cy: moreBase - 20, color: grey)
    }

    /// 顾客画像卡（Java:162-189）
    private func drawPictureCard(_ d: Draw, _ item: Receipt, _ stats: ReceiptStore.Stats) {
        d.round(card2, radius: cardRadius, cardColor)
        if let picture = coin, false {
            _ = picture
        }
        // 头像：有就用切图占位（用户上传的头像另存，这里没有就用 fallback）
        if let fallback {
            d.clipped(to: avatar, radius: 15) {
                d.art(fallback, avatar)
            }
        }
        d.textSqueezed(item.payer, x: payerX, baseline: payerBase, size: payerSize, ink,
                       bold: true, squeeze: squeeze)
        d.rightTextSqueezed("消费记录", right: consumerRight, baseline: payerBase, size: 50,
                            blue, squeeze: squeeze)
        chevron(d, x: rowChevronTip, cy: payerBase - 15, color: blue)

        for x in colSeparator {
            d.rect(CGRect(x: x, y: colSeparatorTop, width: 2,
                          height: colSeparatorBottom - colSeparatorTop), separator)
        }

        let values = [item.monthSpend ?? stats.monthSpend,
                      item.totalSpend ?? stats.totalSpend,
                      item.avgPrice ?? (stats.totalCount == 0 ? 0
                                        : (item.totalSpend ?? stats.totalSpend)
                                        / Double(item.totalCount ?? stats.totalCount))]
        let counts = [item.monthCount ?? stats.monthCount,
                      item.totalCount ?? stats.totalCount, -1]
        for i in 0..<3 {
            d.centerTextSqueezed(colLabels[i], cx: colCx[i], baseline: colLabelBase,
                                 size: colLabelSize, grey, squeeze: squeeze)
            d.centerTextSqueezed(ReceiptStore.yuan(values[i]), cx: colCx[i],
                                 baseline: colValueBase, size: colValueSize,
                                 Draw.color(0xff2b2b2b), bold: true, squeeze: squeeze)
            if counts[i] >= 0 {
                d.centerTextSqueezed("共\(counts[i])笔", cx: colCx[i], baseline: colCountBase,
                                     size: colCountSize, Draw.color(0xff2b2b2b),
                                     squeeze: squeeze)
            }
        }
    }

    /// 两个按钮（Java:191-196）
    private func drawButtons(_ d: Draw) {
        d.round(button1, radius: cardRadius, cardColor)
        d.centerTextSqueezed("联系顾客", cx: Self.W / 2, baseline: 2077.7, size: buttonTextSize,
                             blue, squeeze: squeeze)
        d.round(button2, radius: cardRadius, cardColor)
        d.centerTextSqueezed("退款", cx: Self.W / 2, baseline: 2272.7, size: buttonTextSize,
                             blue, squeeze: squeeze)
    }

    /// 页脚（Java:198-205）
    private func drawFooter(_ d: Draw) {
        let claim = "收钱码被恶意替换、覆盖可申请理赔"
        let width = Draw.width(claim, size: claimSize) * squeeze
        let cx = Self.W / 2 - 16
        let x = cx - width / 2
        d.textSqueezed(claim, x: x, baseline: claimBase, size: claimSize,
                       Draw.color(0xff939393), squeeze: squeeze)
        chevron(d, x: x + width + 26, cy: claimBase - 15, color: Draw.color(0xff939393))
        d.centerTextSqueezed("在线帮助", cx: Self.W / 2, baseline: helpBase, size: helpSize,
                             blue, squeeze: squeeze)
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

    private func chevronDown(_ d: Draw, cx: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: cx - 14, y: cy - 7))
        d.ctx.addLine(to: CGPoint(x: cx, y: cy + 7))
        d.ctx.addLine(to: CGPoint(x: cx + 14, y: cy - 7))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    // MARK: - 命中（Java:235-262）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < 300 { return x < 130 ? "back" : "noop" }
        if card1.contains(CGPoint(x: x, y: y)) {
            if y < 520 { return "amountLabel" }
            if y < 660 { return "amount" }
            if y < 800 { return "actual" }
            if y < 985 { return "prize" }
            if y < 1078 { return "quota" }
            if y < 1170 { return "note" }
            return "more"
        }
        if card2.contains(CGPoint(x: x, y: y)) {
            if y < 1580 {
                if x < 210 { return "avatar" }
                if x > 940 { return "consumer" }
                return "payer"
            }
            let column = x < colSeparator[0] ? 0 : (x < colSeparator[1] ? 1 : 2)
            return column == 0 ? "month" : (column == 1 ? "total" : "avg")
        }
        if button1.contains(CGPoint(x: x, y: y)) { return "contact" }
        if button2.contains(CGPoint(x: x, y: y)) { return "refund" }
        if y > 2540 && y < 2650 { return "claim" }
        if y > 2650 && y < 2760 { return "help" }
        return "noop"
    }
}
