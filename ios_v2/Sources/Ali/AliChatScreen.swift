import UIKit

/// 「支付宝 → 单聊」页 —— `AliChatActivity`（Java 377 行）的移植。
///
/// 版式几乎是「原图 + 热区」：
///   · 顶栏 56dp：`‹` + 昵称 20sp + 右侧 `ali_chat_header_right`（82×42dp，左半收款、右半更多）
///   · 中间消息区：气泡右对齐，16sp 深色字、`#d9f0ff` 底、圆角 8、内边距 12/9
///   · 底部 172dp：整块用 `ali_chat_bottom` 原图（快捷短语 / 输入框 / 语音表情加号 / 四个入口都在图里）
///     图上叠透明热区，坐标照 Java 的 `lefts` / `widths` 与 `topMargin` 抄
///
/// 输入：点输入框弹系统输入框（iOS 没有内嵌输入法），确定即发一条；
/// 发完写回 `AliChatStore` 的最后一条与时间（对应 Java `AliChatStore.touch`）。
final class AliChatScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    private let bg = Draw.color(0xffefefef)
    private let ink = Draw.color(0xff222222)
    private let bubbleBg = Draw.color(0xffd9f0ff)

    static let headerH = dp(56)
    static let bottomH = dp(172)

    /// 快捷短语（Java:141-142）。Router 发消息时也用这一份，避免两处文案走偏。
    static let quickPhrases = ["你好", "在干嘛", "我来了", "谢谢老板"]
    private let chipLefts: [CGFloat] = [dp(10), dp(83), dp(172), dp(258)]
    private let chipWidths: [CGFloat] = [dp(68), dp(81), dp(78), dp(96)]

    final class State: ScreenState {
        var title = "飞跃巅峰"
        var avatar = "res:ali_chat_avatar_b"
        /// 已发送的消息（只在内存里，与 Android 一致：进页面是空的）
        var messages: [String] = []
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), bg)
        drawHeader(d, s)
        drawMessages(d, s)
        drawBottomPanel(d)
    }

    /// 顶栏（Java:62-114）
    private func drawHeader(_ d: Draw, _ s: State) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.headerH), Draw.color(0xffefefef))
        d.text("‹", x: dp(14), baseline: Self.headerH / 2 + dp(12), size: dp(34), ink)
        d.text(s.title, x: dp(48), baseline: Self.headerH / 2 + Draw.font(size: dp(20)).ascender / 2 - dp(20) * 0.06,
               size: dp(20), ink)
        // 右侧两格原图：42dp 高，贴右 4dp
        let artW = dp(82)
        d.art(UIImage(named: "ali_chat_header_right"),
              CGRect(x: Self.W - dp(4) - artW, y: (Self.headerH - dp(42)) / 2,
                     width: artW, height: dp(42)))
        d.hairline(Self.headerH, from: 0, to: Self.W, Draw.color(0xffeeeeee))
    }

    /// 消息区：从底部往上排，最后一条贴着输入区
    private func drawMessages(_ d: Draw, _ s: State) {
        guard !s.messages.isEmpty else { return }
        let size = dp(16)
        let padH = dp(12)
        let padV = dp(9)
        let gap = dp(8)
        let lineH = Draw.font(size: size).lineHeight
        let maxWidth = Self.W * 0.68

        var bottom = Self.H - Self.bottomH - dp(8)
        for text in s.messages.reversed() {
            let lines = wrap(text, maxWidth: maxWidth - padH * 2, size: size)
            let width = min(maxWidth, (lines.map { Draw.width($0, size: size) }.max() ?? 0) + padH * 2)
            let height = lineH * CGFloat(lines.count) + padV * 2
            let rect = CGRect(x: Self.W - dp(12) - width, y: bottom - height,
                              width: width, height: height)
            d.round(rect, radius: dp(8), bubbleBg)
            var baseline = rect.minY + padV + Draw.font(size: size).ascender
            for line in lines {
                d.text(line, x: rect.minX + padH, baseline: baseline, size: size, ink)
                baseline += lineH
            }
            bottom = rect.minY - gap
        }
    }

    /// 底部整块原图 + 透明热区（Java:130-221）
    private func drawBottomPanel(_ d: Draw) {
        let top = Self.H - Self.bottomH
        d.rect(CGRect(x: 0, y: top, width: Self.W, height: Self.bottomH), bg)
        d.art(UIImage(named: "ali_chat_bottom"),
              CGRect(x: 0, y: top, width: Self.W, height: Self.bottomH))
    }

    private func wrap(_ text: String, maxWidth: CGFloat, size: CGFloat) -> [String] {
        if text.isEmpty { return [""] }
        var lines: [String] = []
        var current = ""
        for character in text {
            let next = current + String(character)
            if Draw.width(next, size: size) > maxWidth, !current.isEmpty {
                lines.append(current)
                current = String(character)
            } else {
                current = next
            }
        }
        if !current.isEmpty { lines.append(current) }
        return lines
    }

    // MARK: - 命中（热区逐条照 Java）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 顶栏：返回 / 收款 / 更多
        if y < Self.headerH {
            if x < dp(48) { return "ali:back" }
            let artLeft = Self.W - dp(4) - dp(82)
            if x >= artLeft {
                return x < artLeft + dp(41) ? "ali:chat:pay" : "ali:chat:more"
            }
            return "noop"
        }

        let panelTop = Self.H - Self.bottomH
        guard y >= panelTop else { return "ali:chat:noop" }
        let localY = y - panelTop

        // 四个快捷短语（topMargin 8、高 38）
        if localY >= dp(8) && localY <= dp(46) {
            for (index, left) in chipLefts.enumerated()
            where x >= left && x <= left + chipWidths[index] {
                return "ali:chat:chip:\(index)"
            }
        }
        // 输入框（left 38、right 72、top 48、高 36）
        if localY >= dp(48) && localY <= dp(84)
            && x >= dp(38) && x <= Self.W - dp(72) {
            return "ali:chat:input"
        }
        // 语音 / 表情 / 加号（top 45、高 42）
        if localY >= dp(45) && localY <= dp(87) {
            if x <= dp(42) { return "ali:chat:voice" }
            if x >= Self.W - dp(84) && x < Self.W - dp(44) { return "ali:chat:emoji" }
            if x >= Self.W - dp(42) { return "ali:chat:plus" }
        }
        // 四个入口（top 88、高 84、四等分）
        if localY >= dp(88) && localY <= dp(172) {
            let index = Int(x / (Self.W / 4))
            return "ali:chat:action:\(min(3, max(0, index)))"
        }
        return "ali:chat:noop"
    }
}
