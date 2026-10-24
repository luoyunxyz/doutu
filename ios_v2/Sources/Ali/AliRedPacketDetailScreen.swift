import UIKit

/// 「支付宝 → 红包详情」—— `AliRedPacketDetailActivity`（378 行）的移植。
///
/// 一张红底页面（上 `#ef5136` → 下 `#ce3921` 竖向渐变）+ 六块内容：
/// 顶栏（返回 / 标题 / 口令 / 胶囊）、`rp_art` 主图、白卡（发送人 / 金额 / 祝福语）、
/// 圆形头像、`rp_wave` 波浪、奶黄色「答谢」、底部「查看红包」。
///
/// 数据与编辑页共享 `AliRedPacketStore`，所以两页名字/金额始终一致。
final class AliRedPacketDetailScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false
    /// 红包页整屏红底，刘海那条也要红
    var safeAreaTint: UIColor { UIColor(red: 0xef / 255.0, green: 0x51 / 255.0,
                                        blue: 0x36 / 255.0, alpha: 1) }
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    // Java:34-61 的几何常量（dp）
    private let headerTop: CGFloat = 40
    private let headerH: CGFloat = 40
    private let heroTop: CGFloat = 96
    private let heroH: CGFloat = 303.2
    private let avatarSize: CGFloat = 72
    private let avatarTop: CGFloat = 418
    private let cardLeft: CGFloat = 12.4
    private let cardTop: CGFloat = 435.4
    private let cardW: CGFloat = 335.2
    private let cardH: CGFloat = 210
    private let fromTop: CGFloat = 500
    private let amountTop: CGFloat = 536
    private let blessTop: CGFloat = 611
    private let waveTop: CGFloat = 626
    private let thanksLeft: CGFloat = 108
    private let thanksTop: CGFloat = 675.8
    private let thanksW: CGFloat = 143.4
    private let thanksH: CGFloat = 43.4
    private let linkTop: CGFloat = 729

    // Java:55-61 的颜色
    private let redTop = Draw.color(0xffef5136)
    private let redBottom = Draw.color(0xffce3921)
    private let gray = Draw.color(0xff9e9e9e)
    private let amountColor = Draw.color(0xffd34127)
    private let cream = Draw.color(0xfff7e3b1)
    private let creamText = Draw.color(0xff514830)
    private let linkColor = Draw.color(0xfff2ddb0)

    /// 内容都在 `AliRedPacketStore` 里，页面自身不需要状态
    final class State: ScreenState { }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let store = AliRedPacketStore.shared

        // 红底渐变铺满
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: Self.H),
                   from: redTop, to: redBottom, vertical: true)

        drawHeader(d)

        // 主图（整宽）
        d.art(UIImage(named: "ali_rp_art"),
              CGRect(x: 0, y: dp(heroTop), width: Self.W, height: dp(heroH)))

        drawCard(d, store: store)

        // 圆形头像压在卡片上沿
        let avatarSide = dp(avatarSize)
        let avatarRect = CGRect(x: (Self.W - avatarSide) / 2, y: dp(avatarTop),
                                width: avatarSide, height: avatarSide)
        drawAvatar(d, rect: avatarRect, store: store)

        // 波浪
        d.art(UIImage(named: "ali_rp_wave"),
              CGRect(x: 0, y: dp(waveTop), width: Self.W, height: dp(48)))

        // 答谢（奶黄胶囊）
        let thanks = CGRect(x: dp(thanksLeft), y: dp(thanksTop),
                            width: dp(thanksW), height: dp(thanksH))
        d.round(thanks, radius: dp(thanksH) / 2, cream)
        let thanksSize = dp(17)
        d.centerText("答谢", cx: thanks.midX,
                     baseline: thanks.midY + Draw.font(size: thanksSize).ascender / 2 - thanksSize * 0.06,
                     size: thanksSize, creamText)

        // 查看红包
        let linkSize = dp(15)
        d.centerText("查看红包", cx: Self.W / 2,
                     baseline: dp(linkTop) + Draw.font(size: linkSize).ascender,
                     size: linkSize, linkColor)
    }

    /// 顶栏（Java:101-146）
    private func drawHeader(_ d: Draw) {
        let top = dp(headerTop)
        let height = dp(headerH)
        // 返回：两条线画的 chevron（白色）
        let chevX = dp(16.6)
        let chevY = top + (height - dp(15)) / 2
        d.line(chevX + dp(9), chevY, chevX, chevY + dp(7.5), .white, width: dp(1.7))
        d.line(chevX, chevY + dp(7.5), chevX + dp(9), chevY + dp(15), .white, width: dp(1.7))

        let titleSize = dp(18)
        d.text("红包详情", x: dp(36),
               baseline: top + height / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
               size: titleSize, .white)
        d.art(UIImage(named: "ali_rp_ic_kl"),
              CGRect(x: dp(230), y: top + (height - dp(38)) / 2,
                     width: dp(38), height: dp(38)))
        d.art(UIImage(named: "ali_rp_pill"),
              CGRect(x: dp(270), y: top + dp(2), width: dp(83), height: dp(34)))
    }

    /// 白卡：发送人 / 金额 / 祝福语（Java:159-190）
    private func drawCard(_ d: Draw, store: AliRedPacketStore) {
        let card = CGRect(x: dp(cardLeft), y: dp(cardTop), width: dp(cardW), height: dp(cardH))
        // 只有上面两个圆角（对应 Java 的 setCornerRadii 上 16 下 0）
        let path = UIBezierPath(roundedRect: card, byRoundingCorners: [.topLeft, .topRight],
                                cornerRadii: CGSize(width: dp(16), height: dp(16)))
        d.ctx.setFillColor(UIColor.white.cgColor)
        d.ctx.addPath(path.cgPath)
        d.ctx.fillPath()

        let centerX = card.midX
        let fromSize = dp(13)
        d.centerText("\(store.senderName)送你的红包", cx: centerX,
                     baseline: dp(fromTop) + Draw.font(size: fromSize).ascender,
                     size: fromSize, gray)

        let amountSize = dp(40)
        d.centerText("￥" + store.amount, cx: centerX,
                     baseline: dp(amountTop) + Draw.font(size: amountSize).ascender,
                     size: amountSize, amountColor)

        let blessSize = dp(13)
        d.centerText("“\(store.blessing)”", cx: centerX,
                     baseline: dp(blessTop) + Draw.font(size: blessSize).ascender,
                     size: blessSize, gray)
    }

    /// 发送人头像：有切图用切图，否则画一个带首字的圆（对应 `letterAvatar`）
    private func drawAvatar(_ d: Draw, rect: CGRect, store: AliRedPacketStore) {
        if let asset = store.senderAsset, let image = UIImage(named: asset) {
            d.clipped(to: rect, radius: rect.width / 2) {
                d.art(image, rect)
            }
            return
        }
        // 首字头像：按索引取一个底色
        let palette = [Draw.color(0xff7aa7d8), Draw.color(0xffe0a35c),
                       Draw.color(0xff8fc7a2), Draw.color(0xffb28fd0)]
        let color = palette[abs(store.senderIndex) % palette.count]
        d.circle(cx: rect.midX, cy: rect.midY, r: rect.width / 2, color)
        let name = store.senderName
        let first = name.isEmpty ? "?" : String(name.prefix(1))
        let size = rect.width * 0.42
        d.centerText(first, cx: rect.midX,
                     baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                     size: size, .white)
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let top = dp(headerTop)
        if y >= top && y < top + dp(headerH) {
            if x < dp(40) { return "ali:back" }
            return "ali:rp:noop"
        }
        // 白卡里的三行
        let cardTopY = dp(cardTop)
        if y >= cardTopY && y < cardTopY + dp(cardH) && x >= dp(cardLeft) && x <= dp(cardLeft + cardW) {
            if y < cardTopY + dp(fromTop - cardTop + 20) { return "ali:rp:from" }
            if y < cardTopY + dp(blessTop - cardTop) { return "ali:rp:amount" }
            return "ali:rp:bless"
        }
        // 头像
        let avatarSide = dp(avatarSize)
        let avatarRect = CGRect(x: (Self.W - avatarSide) / 2, y: dp(avatarTop),
                                width: avatarSide, height: avatarSide)
        if avatarRect.contains(CGPoint(x: x, y: y)) { return "ali:rp:sender" }
        // 答谢
        let thanks = CGRect(x: dp(thanksLeft), y: dp(thanksTop),
                            width: dp(thanksW), height: dp(thanksH))
        if thanks.contains(CGPoint(x: x, y: y)) { return "ali:rp:thanks" }
        // 查看红包
        if y >= dp(linkTop) && y < dp(linkTop) + dp(40) { return "ali:rp:open" }
        return "noop"
    }
}
