import UIKit

/// 「支付宝 → 提现」—— `AliWithdrawActivity`（234 行）的移植。
///
/// 56dp 灰条（`‹` + 居中「提现」17sp + 右侧「完成」15sp）下是四张白卡（内边距 16/14、卡间距 10）：
///   1. **到账银行卡**：13sp 灰标签 + 36dp 红色「银」方块 + 银行名/卡号 + 右侧蓝色「立即到账」；
///   2. **提现金额**：13sp 灰标签 + `¥` 28sp + 金额 32sp + 右侧蓝色「全部提现」+ 可用余额；
///   3. **到账金额 / 服务费**：两行「名 / 值」14sp + 12sp 灰字「本次提现免费」；
///   4. 添加银行卡红包：13sp 文案 + 蓝色「立即添加」。
///
/// 最后是 48dp 蓝色圆角「预计两小时内到账，确认提现」按钮（左右 16dp、上 16dp）。
final class AliWithdrawScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388
    static let barH = dp(56)

    private let pageBg = Draw.color(0xfff5f5f5)
    private let barBg = Draw.color(0xffededed)
    private let ink = Draw.color(0xff333333)
    private let sub = Draw.color(0xff888888)
    private let blue = Draw.color(0xff3e7af6)
    private let cardPad = dp(16)
    private let cardPadV = dp(14)
    private let cardGap = dp(10)

    final class State: ScreenState {
        var amount = "0.00"
    }

    private enum ItemKind {
        case bankCard
        case amountCard
        case feeCard
        case promoCard
        case confirm
        case allIn
    }

    private struct Item {
        let kind: ItemKind
        let rect: CGRect
    }

    // MARK: - 布局

    private struct Layout {
        var items: [Item] = []
        var contentHeight: CGFloat = 0
    }

    private func layout() -> Layout {
        var out = Layout()
        let cardLeft = dp(12)
        let cardW = Self.W - cardLeft * 2
        let innerX = cardLeft + cardPad
        let innerW = cardW - cardPad * 2
        var y = Self.barH

        // 卡一：到账银行卡（标签 18 + 行 36 + 上下内边距）
        let bankH = cardPadV + dp(18) + dp(10) + dp(36) + cardPadV
        out.items.append(Item(kind: .bankCard,
                              rect: CGRect(x: cardLeft, y: y, width: cardW, height: bankH)))
        y += bankH + cardGap

        // 卡二：提现金额（标签 18 + 行 40 + 可用余额 17 + 上下内边距）
        let amountH = cardPadV + dp(18) + dp(8) + dp(40) + dp(17) + cardPadV
        out.items.append(Item(kind: .amountCard,
                              rect: CGRect(x: cardLeft, y: y, width: cardW, height: amountH)))
        out.items.append(Item(kind: .allIn,
                              rect: CGRect(x: innerX + innerW - dp(60), y: y + cardPadV + dp(18) + dp(8),
                                           width: dp(60), height: dp(40))))
        y += amountH + cardGap

        // 卡三：到账金额 / 服务费（两行各 8+20+8 = 36 + 免服务费 17）
        let feeH = cardPadV + dp(36) * 2 + dp(17) + cardPadV
        out.items.append(Item(kind: .feeCard,
                              rect: CGRect(x: cardLeft, y: y, width: cardW, height: feeH)))
        y += feeH + cardGap

        // 卡四：红包文案（两行 13sp ≈ 20 + 20）
        let promoH = cardPadV + dp(20) * 2 + cardPadV
        out.items.append(Item(kind: .promoCard,
                              rect: CGRect(x: cardLeft, y: y, width: cardW, height: promoH)))
        y += promoH

        // 确认按钮
        y += dp(16)
        out.items.append(Item(kind: .confirm,
                              rect: CGRect(x: dp(16), y: y, width: Self.W - dp(32), height: dp(48))))
        y += dp(48) + dp(16)

        out.contentHeight = y
        return out
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, layout().contentHeight - Self.H)
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let scroll = s.scroll
        let laid = layout()

        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), pageBg)

        for item in laid.items {
            switch item.kind {
            case .bankCard:
                d.rect(item.rect, .white)
                drawBankCard(d, rect: item.rect)
            case .amountCard:
                d.rect(item.rect, .white)
                drawAmountCard(d, rect: item.rect, amount: s.amount)
            case .feeCard:
                d.rect(item.rect, .white)
                drawFeeCard(d, rect: item.rect)
            case .promoCard:
                d.rect(item.rect, .white)
                drawPromoCard(d, rect: item.rect)
            case .confirm:
                d.round(item.rect, radius: dp(6), blue)
                let size = dp(16)
                d.centerText("预计两小时内到账，确认提现", cx: item.rect.midX,
                             baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                             size: size, .white)
            case .allIn:
                let size = dp(13)
                d.rightText("全部提现", right: item.rect.maxX,
                            baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                            size: size, blue)
            }
        }

        drawBar(d, scroll: scroll)
    }

    private func drawBar(_ d: Draw, scroll: CGFloat) {
        let bar = CGRect(x: 0, y: scroll, width: Self.W, height: Self.barH)
        d.rect(bar, barBg)
        let backSize = dp(26)
        d.centerText("‹", cx: dp(22),
                     baseline: bar.minY + bar.height / 2 + Draw.font(size: backSize).ascender / 2 - backSize * 0.06,
                     size: backSize, ink)
        let titleSize = dp(17)
        d.centerText("提现", cx: Self.W / 2,
                     baseline: bar.minY + bar.height / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
                     size: titleSize, ink)
        let doneSize = dp(15)
        d.rightText("完成", right: Self.W - dp(16),
                    baseline: bar.minY + bar.height / 2 + Draw.font(size: doneSize).ascender / 2 - doneSize * 0.06,
                    size: doneSize, ink)
    }

    private func drawBankCard(_ d: Draw, rect: CGRect) {
        let innerX = rect.minX + cardPad
        var y = rect.minY + cardPadV
        let labelSize = dp(13)
        d.text("到账银行卡", x: innerX, baseline: y + Draw.font(size: labelSize).ascender,
               size: labelSize, sub)
        y += Draw.font(size: labelSize).lineHeight + dp(10)

        // 红色「银」方块 36dp
        let icon = CGRect(x: innerX, y: y, width: dp(36), height: dp(36))
        d.round(icon, radius: dp(6), Draw.color(0xffe53935))
        let iconSize = dp(12)
        d.centerText("银", cx: icon.midX,
                     baseline: icon.midY + Draw.font(size: iconSize).ascender / 2 - iconSize * 0.06,
                     size: iconSize, .white)

        let nameSize = dp(15)
        let noSize = dp(12)
        let infoX = icon.maxX + dp(10)
        d.text("中国工商银行", x: infoX,
               baseline: icon.midY - dp(2) + Draw.font(size: nameSize).ascender / 2 - nameSize * 0.06,
               size: nameSize, ink)
        d.text("尾号9988 储蓄卡", x: infoX,
               baseline: icon.midY + dp(14) + Draw.font(size: noSize).ascender / 2 - noSize * 0.06,
               size: noSize, sub)

        let fastSize = dp(12)
        d.rightText("立即到账", right: rect.maxX - cardPad,
                    baseline: icon.midY + Draw.font(size: fastSize).ascender / 2 - fastSize * 0.06,
                    size: fastSize, blue)
    }

    private func drawAmountCard(_ d: Draw, rect: CGRect, amount: String) {
        let innerX = rect.minX + cardPad
        var y = rect.minY + cardPadV
        let labelSize = dp(13)
        d.text("提现金额", x: innerX, baseline: y + Draw.font(size: labelSize).ascender,
               size: labelSize, sub)
        y += Draw.font(size: labelSize).lineHeight + dp(8)

        let yenSize = dp(28)
        let valueSize = dp(32)
        d.text("¥", x: innerX, baseline: y + Draw.font(size: yenSize).ascender,
               size: yenSize, ink)
        let valueX = innerX + Draw.width("¥", size: yenSize) + dp(6)
        d.text(amount, x: valueX, baseline: y + Draw.font(size: valueSize).ascender,
               size: valueSize, ink)
        y += dp(40)

        let availSize = dp(12)
        d.text("可用余额0.10元", x: innerX, baseline: y + Draw.font(size: availSize).ascender,
               size: availSize, sub)
    }

    private func drawFeeCard(_ d: Draw, rect: CGRect) {
        let innerX = rect.minX + cardPad
        var y = rect.minY + cardPadV
        for (name, value) in [("到账金额", "0.00"), ("服务费", "0.00")] {
            let size = dp(14)
            let rowMid = y + dp(20)
            d.text(name, x: innerX,
                   baseline: rowMid + Draw.font(size: size).ascender / 2 - size * 0.06,
                   size: size, ink)
            d.rightText(value, right: rect.maxX - cardPad,
                        baseline: rowMid + Draw.font(size: size).ascender / 2 - size * 0.06,
                        size: size, ink)
            y += dp(36)
        }
        let freeSize = dp(12)
        d.text("本次提现免费", x: innerX, baseline: y + Draw.font(size: freeSize).ascender,
               size: freeSize, sub)
    }

    private func drawPromoCard(_ d: Draw, rect: CGRect) {
        let innerX = rect.minX + cardPad
        var y = rect.minY + cardPadV
        let size = dp(13)
        d.text("添加提现到账银行卡得5-10元红包，", x: innerX,
               baseline: y + Draw.font(size: size).ascender, size: size, ink)
        y += Draw.font(size: size).lineHeight
        d.text("立即添加", x: innerX, baseline: y + Draw.font(size: size).ascender,
               size: size, blue)
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if y >= s.scroll && y < s.scroll + Self.barH {
            if x < dp(44) { return "ali:back" }
            if x > Self.W - dp(60) { return "ali:withdraw:done" }
            return "noop"
        }
        for item in layout().items where item.rect.contains(CGPoint(x: x, y: y)) {
            switch item.kind {
            case .confirm: return "ali:withdraw:confirm"
            case .allIn: return "ali:withdraw:all"
            default: return "noop"
            }
        }
        return "noop"
    }
}
