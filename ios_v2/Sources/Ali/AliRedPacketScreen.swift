import UIKit

/// 「支付宝 → 红包」编辑页 —— `AliRedPacketActivity`（465 行）的移植。
///
/// 四段式（Java 的 dp 常量）：
///   · 顶栏 54dp（`#ededed`）：`‹` + 居中「红包」17sp + 右侧黄色「发送」（64×38.8、圆角 8）；
///   · 金额白块 126.8dp：`红包金额` 13sp 在 (32.6, 18.3)，下一行 `￥` 40sp 粗体 + 金额 56sp；
///   · 祝福语白块 100.5dp：祝福语 15sp 在 (15.8, 13.8)；
///   · 发送人白块 60.5dp：39.4dp 头像 + 昵称 15sp + 右侧「发送人 ›」。
///
/// 两块之间各留 9.6dp。点「发送」校验金额 > 0 后进红包详情页（`AliRedPacketDetailScreen`）。
final class AliRedPacketScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    // Java:50-66 的常量
    private let barH = dp(54)
    private let sendW = dp(64)
    private let sendH = dp(38.8)
    private let sendRadius = dp(8)
    private let sendMargin = dp(12.4)
    private let card1H = dp(126.8)
    private let labelTop = dp(18.3)
    private let labelLeft = dp(32.6)
    private let amountTop = dp(44.5)
    private let cardGap = dp(9.6)
    private let card2H = dp(100.5)
    private let blessTop = dp(13.8)
    private let blessLeft = dp(15.8)
    private let card3H = dp(60.5)
    private let avatarSide = dp(39.4)
    private let avatarLeft = dp(15.2)
    private let nameLeft = dp(68.9)

    // Java:42-47 的颜色
    private let barBg = Draw.color(0xffededed)
    private let titleInk = Draw.color(0xff2f2f2f)
    private let textInk = Draw.color(0xff1d1d1d)
    private let subInk = Draw.color(0xffa9a9a9)
    private let yellow = Draw.color(0xffffd84d)

    final class State: ScreenState { }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let store = AliRedPacketStore.shared
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), Draw.color(0xfff7f7f7))
        drawBar(d)
        drawAmountCard(d, store: store)
        drawBlessCard(d, store: store)
        drawSenderCard(d, store: store)
    }

    /// 顶栏（Java:103-146）
    private func drawBar(_ d: Draw) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: barH), barBg)

        // ‹ 用两条线画（11×20）
        let chevX = dp(17)
        let chevY = (barH - dp(20)) / 2
        d.line(chevX + dp(11), chevY, chevX, chevY + dp(10), Draw.color(0xff333333), width: dp(1.6))
        d.line(chevX, chevY + dp(10), chevX + dp(11), chevY + dp(20),
               Draw.color(0xff333333), width: dp(1.6))

        let titleSize = dp(17)
        d.centerText("红包", cx: Self.W / 2,
                     baseline: barH / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
                     size: titleSize, titleInk)

        let send = CGRect(x: Self.W - sendMargin - sendW, y: (barH - sendH) / 2,
                          width: sendW, height: sendH)
        d.round(send, radius: sendRadius, yellow)
        let sendSize = dp(15)
        d.centerText("发送", cx: send.midX,
                     baseline: send.midY + Draw.font(size: sendSize, bold: true).ascender / 2 - sendSize * 0.06,
                     size: sendSize, Draw.color(0xff2b2b2b), bold: true)
    }

    /// 金额白块（Java:150-204）
    private func drawAmountCard(_ d: Draw, store: AliRedPacketStore) {
        let card = CGRect(x: 0, y: barH, width: Self.W, height: card1H)
        d.rect(card, .white)

        let labelSize = dp(13)
        d.text("红包金额", x: labelLeft, baseline: card.minY + labelTop + Draw.font(size: labelSize).ascender,
               size: labelSize, titleInk)

        let yenSize = dp(40)
        let amountSize = dp(56)
        let rowTop = card.minY + amountTop
        d.text("￥", x: labelLeft, baseline: rowTop + Draw.font(size: yenSize, bold: true).ascender,
               size: yenSize, textInk, bold: true)
        let amountX = labelLeft + Draw.width("￥", size: yenSize, bold: true) + dp(6)
        let text = store.amount.isEmpty ? "0" : store.amount
        d.text(text, x: amountX, baseline: rowTop + Draw.font(size: amountSize).ascender,
               size: amountSize, textInk)
    }

    /// 祝福语白块（Java:208-229）
    private func drawBlessCard(_ d: Draw, store: AliRedPacketStore) {
        let top = barH + card1H + cardGap
        d.rect(CGRect(x: 0, y: top, width: Self.W, height: card2H), .white)
        let size = dp(15)
        d.text(store.blessing, x: blessLeft,
               baseline: top + blessTop + Draw.font(size: size).ascender, size: size, textInk)
    }

    /// 发送人白块（Java:233-286）
    private func drawSenderCard(_ d: Draw, store: AliRedPacketStore) {
        let top = barH + card1H + cardGap + card2H + cardGap
        d.rect(CGRect(x: 0, y: top, width: Self.W, height: card3H), .white)

        let avatar = CGRect(x: avatarLeft, y: top + (card3H - avatarSide) / 2,
                            width: avatarSide, height: avatarSide)
        if let asset = store.senderAsset, let image = UIImage(named: asset) {
            d.clipped(to: avatar, radius: avatarSide / 2) { d.art(image, avatar) }
        } else {
            d.circle(cx: avatar.midX, cy: avatar.midY, r: avatarSide / 2, Draw.color(0xff7aa7d8))
            let size = avatarSide * 0.42
            d.centerText(String(store.senderName.prefix(1)), cx: avatar.midX,
                         baseline: avatar.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                         size: size, .white)
        }

        let nameSize = dp(15)
        d.text(store.senderName, x: nameLeft,
               baseline: top + card3H / 2 + Draw.font(size: nameSize).ascender / 2 - nameSize * 0.06,
               size: nameSize, textInk)

        // 右侧「发送人 ›」
        let tipSize = dp(15)
        let tipRight = Self.W - dp(16) - dp(4) - dp(7)
        d.rightText("发送人", right: tipRight,
                    baseline: top + card3H / 2 + Draw.font(size: tipSize).ascender / 2 - tipSize * 0.06,
                    size: tipSize, subInk)
        let ax = tipRight + dp(4)
        let cy = top + card3H / 2
        d.line(ax, cy - dp(4), ax + dp(4), cy, Draw.color(0xffc0c0c0), width: dp(1.4))
        d.line(ax + dp(4), cy, ax, cy + dp(4), Draw.color(0xffc0c0c0), width: dp(1.4))
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < barH {
            if x < dp(56) { return "ali:back" }
            if x > Self.W - sendMargin - sendW { return "ali:rpe:send" }
            return "ali:rpe:title"
        }
        if y < barH + card1H { return "ali:rpe:amount" }
        let blessTopY = barH + card1H + cardGap
        if y < blessTopY + card2H { return "ali:rpe:bless" }
        let senderTopY = blessTopY + card2H + cardGap
        if y < senderTopY + card3H { return "ali:rpe:sender" }
        return "noop"
    }
}
