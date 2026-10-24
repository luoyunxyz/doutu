import UIKit

/// 「支付宝 → 转账」编辑页 —— `AliTransferActivity`（591 行）的移植。
///
/// 三段式（与 Java 一致）：
///   · 顶栏 56dp（`#ededed`）：`‹` + 居中「转账」+ 右侧黄色「完成」（74×36、圆角 5）；
///   · 金额卡（白底、内边距 26/22/20/18）：`转账金额` + `¥` 44sp + 金额 48sp + 说明（空时蓝色提示）；
///   · 发送人行（内边距 15/12）：45dp 圆形头像 + 昵称 + `切换发送人 ›`；
///   · 类型行（内边距 15/13）：`转账类型` + 收入 / 支出单选。
///
/// 金额与说明走系统输入框（iOS 没有内嵌输入法），发送人走系统 action sheet。
final class AliTransferScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    private let barBg = Draw.color(0xffededed)
    private let ink = Draw.color(0xff333333)
    private let amountInk = Draw.color(0xff1f1f1f)
    private let yellow = Draw.color(0xffffda43)
    private let noteEmpty = Draw.color(0xff3478f6)
    private let radioOn = Draw.color(0xffdfb91e)
    private let radioOff = Draw.color(0xffbbbbbb)

    static let barH = dp(56)
    static let amountCardTop = dp(56)
    static let amountCardH = dp(22) + dp(18) + dp(20) + dp(60) + dp(18) + dp(21)
    static let senderRowTop = amountCardTop + amountCardH
    static let senderRowH = dp(69)
    static let typeRowTop = senderRowTop + senderRowH
    static let typeRowH = dp(74)

    final class State: ScreenState { }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let store = AliTransferStore.shared
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), Draw.color(0xfff7f7f7))

        drawTopBar(d)
        drawAmountCard(d, store: store)
        drawSenderRow(d, store: store)
        drawTypeRow(d, store: store)
    }

    /// 顶栏（Java:110-147）
    private func drawTopBar(_ d: Draw) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.barH), barBg)
        d.centerText("‹", cx: dp(28), baseline: Self.barH / 2 + dp(11), size: dp(31), ink)

        let titleSize = dp(17)
        d.centerText("转账", cx: Self.W / 2,
                     baseline: Self.barH / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
                     size: titleSize, ink)

        let doneW = dp(74), doneH = dp(36)
        let done = CGRect(x: Self.W - dp(14) - doneW, y: (Self.barH - doneH) / 2,
                          width: doneW, height: doneH)
        d.round(done, radius: dp(5), yellow)
        let doneSize = dp(16)
        d.centerText("完成", cx: done.midX,
                     baseline: done.midY + Draw.font(size: doneSize, bold: true).ascender / 2 - doneSize * 0.06,
                     size: doneSize, Draw.color(0xff202020), bold: true)
    }

    /// 金额卡（Java:149-203）
    private func drawAmountCard(_ d: Draw, store: AliTransferStore) {
        let card = CGRect(x: 0, y: Self.amountCardTop, width: Self.W, height: Self.amountCardH)
        d.rect(card, .white)

        let padH = dp(26)
        let labelSize = dp(14)
        d.text("转账金额", x: padH, baseline: card.minY + dp(22) + Draw.font(size: labelSize).ascender,
               size: labelSize, Draw.color(0xff555555))

        // ¥ + 金额
        let rowTop = card.minY + dp(22) + dp(20) + dp(12)
        let yenSize = dp(44)
        let amountSize = dp(48)
        d.text("¥", x: padH, baseline: rowTop + Draw.font(size: yenSize).ascender,
               size: yenSize, amountInk)
        let amountX = padH + Draw.width("¥", size: yenSize) + dp(10)
        let text = store.amount.isEmpty ? "0.0" : store.amount
        d.text(text, x: amountX, baseline: rowTop + Draw.font(size: amountSize).ascender,
               size: amountSize, amountInk)

        // 转账说明
        let noteSize = dp(15)
        let note = store.note.trimmingCharacters(in: .whitespacesAndNewlines)
        let noteText = note.isEmpty ? "新增转账说明" : note
        let noteBaseline = rowTop + dp(48) + dp(16) + Draw.font(size: noteSize).ascender
        d.text(noteText, x: padH, baseline: noteBaseline, size: noteSize,
               note.isEmpty ? noteEmpty : ink)
    }

    /// 发送人行（Java:205-232）
    private func drawSenderRow(_ d: Draw, store: AliTransferStore) {
        let row = CGRect(x: 0, y: Self.senderRowTop, width: Self.W, height: Self.senderRowH)
        d.rect(row, .white)

        let avatarSide = dp(45)
        let avatarRect = CGRect(x: dp(15), y: row.midY - avatarSide / 2,
                                width: avatarSide, height: avatarSide)
        let sender = store.sender ?? AliTransferStore.Sender(name: "怕你久居她心", asset: nil)
        if let asset = sender.asset, let image = UIImage(named: asset) {
            d.clipped(to: avatarRect, radius: avatarSide / 2) {
                d.art(image, avatarRect)
            }
        } else {
            d.circle(cx: avatarRect.midX, cy: avatarRect.midY, r: avatarSide / 2,
                     Draw.color(0xff7aa7d8))
            let size = avatarSide * 0.42
            d.centerText(String(sender.name.prefix(1)), cx: avatarRect.midX,
                         baseline: avatarRect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                         size: size, .white)
        }

        let nameSize = dp(15)
        let baseline = row.midY + Draw.font(size: nameSize).ascender / 2 - nameSize * 0.06
        d.text(sender.name, x: avatarRect.maxX + dp(13), baseline: baseline, size: nameSize, ink)
        d.rightText("切换发送人 ›", right: Self.W - dp(15), baseline: baseline,
                    size: nameSize, ink)
    }

    /// 类型行（Java:234-279）
    private func drawTypeRow(_ d: Draw, store: AliTransferStore) {
        let row = CGRect(x: 0, y: Self.typeRowTop, width: Self.W, height: Self.typeRowH)
        d.rect(row, .white)

        let size = dp(15)
        let baseline = row.minY + dp(13) + Draw.font(size: size).lineHeight / 2 + dp(4)
        d.text("转账类型", x: dp(15), baseline: baseline, size: size, ink)

        // 两个单选：圆点 + 文字，靠右排
        let radioSize = dp(14)
        let expenseW = Draw.width("支出", size: radioSize) + dp(22)
        let incomeW = Draw.width("收入", size: radioSize) + dp(22)
        let expenseLeft = Self.W - dp(15) - expenseW
        let incomeLeft = expenseLeft - dp(28) - incomeW

        drawRadio(d, left: incomeLeft, centerY: row.midY, title: "收入",
                  selected: store.income, size: radioSize)
        drawRadio(d, left: expenseLeft, centerY: row.midY, title: "支出",
                  selected: !store.income, size: radioSize)
    }

    private func drawRadio(_ d: Draw, left: CGFloat, centerY: CGFloat,
                           title: String, selected: Bool, size: CGFloat) {
        let dot = dp(18)
        let rect = CGRect(x: left, y: centerY - dot / 2, width: dot, height: dot)
        d.circle(cx: rect.midX, cy: rect.midY, r: dot / 2, selected ? radioOn : .white)
        d.roundStroke(rect, radius: dot / 2, selected ? radioOn : radioOff, width: dp(1))
        if selected {
            d.circle(cx: rect.midX, cy: rect.midY, r: dot * 0.22, .white)
        }
        d.text(title, x: rect.maxX + dp(6),
               baseline: centerY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, ink)
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 顶栏
        if y < Self.barH {
            if x < dp(56) { return "ali:back" }
            if x > Self.W - dp(14) - dp(74) { return "ali:transfer:done" }
            return "noop"
        }
        // 金额卡：金额区 / 说明
        if y >= Self.amountCardTop && y < Self.amountCardTop + Self.amountCardH {
            if y < Self.amountCardTop + dp(22) + dp(20) + dp(12) + dp(60) { return "ali:transfer:amount" }
            return "ali:transfer:note"
        }
        // 发送人行
        if y >= Self.senderRowTop && y < Self.senderRowTop + Self.senderRowH {
            return "ali:transfer:sender"
        }
        // 类型行
        if y >= Self.typeRowTop && y < Self.typeRowTop + Self.typeRowH {
            return x < Self.W / 2 ? "ali:transfer:income" : "ali:transfer:expense"
        }
        return "noop"
    }
}
