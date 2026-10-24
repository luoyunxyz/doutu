import UIKit

/// 「支付宝 → 年账单」—— `AliYearBillActivity`（138 行）的移植。
///
/// 蓝色渐变头部（`#3e7af6 → #72b0f8`，内边距 16/10/16/20）里放 36dp 的返回条；
/// 下面两张圆角白卡：
///   · 碳减排卡：15sp 正文 + 13sp 灰字说明；
///   · 汇总卡：14sp 灰字 / 26sp 支出金额 / 18sp 收入金额 / 蓝色「收支分析」按钮。
///
/// 内容比一屏高，整页滚动（`selfScrolls = false`，由宿主平移）。
final class AliYearBillScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    private let pageBg = Draw.color(0xfff5f5f5)
    private let blueTop = Draw.color(0xff3e7af6)
    private let blueBottom = Draw.color(0xff72b0f8)
    private let cardPad = dp(16)
    private let cardGap = dp(12)
    private let bodyPad = dp(12)

    final class State: ScreenState { }

    // MARK: - 布局

    private struct Layout {
        var header: CGRect = .zero
        var carbon: CGRect = .zero
        var summary: CGRect = .zero
        var contentHeight: CGFloat = 0
    }

    /// 卡片高度按文字行数算（正文要换行）
    private func layout() -> Layout {
        var out = Layout()
        let headerH = dp(10) + dp(36) + dp(20)
        out.header = CGRect(x: 0, y: 0, width: Self.W, height: headerH)

        let cardW = Self.W - bodyPad * 2
        let innerW = cardW - cardPad * 2

        // 碳减排卡：15sp 正文（行距 6dp）+ 13sp 说明（行距 5dp）+ 中间 10dp
        let c1 = wrap("这一年你在支付宝，至少为世界减少 30.01kg 碳排放。",
                      maxWidth: innerW, size: dp(15))
        let c2 = wrap("你所坚持的爱心公益，累积成一股力量，默默地推动着世界变好，感谢努力生活的你。",
                      maxWidth: innerW, size: dp(13))
        let c1H = Draw.font(size: dp(15)).lineHeight * CGFloat(c1.count) + dp(6) * CGFloat(max(0, c1.count - 1))
        let c2H = Draw.font(size: dp(13)).lineHeight * CGFloat(c2.count) + dp(5) * CGFloat(max(0, c2.count - 1))
        let carbonH = cardPad + c1H + dp(10) + c2H + cardPad
        let carbonTop = headerH + bodyPad
        out.carbon = CGRect(x: bodyPad, y: carbonTop, width: cardW, height: carbonH)

        // 汇总卡：14sp + 顶部 10dp + 26sp + 顶部 10dp + 18sp + 顶部 20dp + 按钮 44dp
        let summaryH = cardPad + Draw.font(size: dp(14)).lineHeight + dp(10)
            + Draw.font(size: dp(26)).lineHeight + dp(10)
            + Draw.font(size: dp(18)).lineHeight + dp(20) + dp(44) + cardPad
        let summaryTop = out.carbon.maxY + cardGap
        out.summary = CGRect(x: bodyPad, y: summaryTop, width: cardW, height: summaryH)

        out.contentHeight = out.summary.maxY + dp(16)
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

        // 宿主已整体平移：铺底从 scroll 开始
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), pageBg)

        // 碳减排卡
        d.round(laid.carbon, radius: dp(10), .white)
        let innerX = laid.carbon.minX + cardPad
        let innerW = laid.carbon.width - cardPad * 2
        var y = laid.carbon.minY + cardPad
        let c1Size = dp(15)
        let c1LineH = Draw.font(size: c1Size).lineHeight + dp(6)
        for line in wrap("这一年你在支付宝，至少为世界减少 30.01kg 碳排放。",
                         maxWidth: innerW, size: c1Size) {
            d.text(line, x: innerX, baseline: y + Draw.font(size: c1Size).ascender,
                   size: c1Size, Draw.color(0xff333333))
            y += c1LineH
        }
        y += dp(10) - dp(6)

        let c2Size = dp(13)
        let c2LineH = Draw.font(size: c2Size).lineHeight + dp(5)
        for line in wrap("你所坚持的爱心公益，累积成一股力量，默默地推动着世界变好，感谢努力生活的你。",
                         maxWidth: innerW, size: c2Size) {
            d.text(line, x: innerX, baseline: y + Draw.font(size: c2Size).ascender,
                   size: c2Size, Draw.color(0xff888888))
            y += c2LineH
        }

        // 汇总卡
        d.round(laid.summary, radius: dp(10), .white)
        var sy = laid.summary.minY + cardPad
        let labelSize = dp(14)
        d.text("这一年你在支付宝", x: innerX, baseline: sy + Draw.font(size: labelSize).ascender,
               size: labelSize, Draw.color(0xff888888))
        sy += Draw.font(size: labelSize).lineHeight + dp(10)

        let paidSize = dp(26)
        d.text("支出了  ¥125,116.68", x: innerX,
               baseline: sy + Draw.font(size: paidSize).ascender, size: paidSize,
               Draw.color(0xff1a1a1a))
        sy += Draw.font(size: paidSize).lineHeight + dp(10)

        let gotSize = dp(18)
        d.text("收到了  ¥0.00", x: innerX,
               baseline: sy + Draw.font(size: gotSize).ascender, size: gotSize,
               Draw.color(0xff333333))
        sy += Draw.font(size: gotSize).lineHeight + dp(20)

        // 收支分析按钮
        let button = CGRect(x: innerX, y: sy, width: laid.summary.width - cardPad * 2, height: dp(44))
        d.round(button, radius: dp(20), blueTop)
        let buttonSize = dp(15)
        d.centerText("收支分析", cx: button.midX,
                     baseline: button.midY + Draw.font(size: buttonSize).ascender / 2 - buttonSize * 0.06,
                     size: buttonSize, .white)

        // 头部最后画、并且偏移 scroll —— 这样它固定不动，滚上来的卡片被它盖住
        // （Android 的头部在 ScrollView 之外，位置本来就是固定的）
        drawHeader(d, scroll: scroll)
    }

    private func drawHeader(_ d: Draw, scroll: CGFloat) {
        let header = layout().header.offsetBy(dx: 0, dy: scroll)
        d.gradient(header, from: blueTop, to: blueBottom, vertical: true)
        d.centerText("‹", cx: dp(16) + dp(16),
                     baseline: header.minY + dp(10) + dp(18) + dp(9), size: dp(26), .white)
        let titleSize = dp(18)
        d.centerText("年账单", cx: Self.W / 2,
                     baseline: header.minY + dp(10) + dp(36) / 2
                        + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
                     size: titleSize, .white)
    }

    // MARK: - 文字换行

    private func wrap(_ text: String, maxWidth: CGFloat, size: CGFloat) -> [String] {
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
        return lines.isEmpty ? [""] : lines
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 顶栏固定在 scroll 处
        if y >= s.scroll && y < s.scroll + dp(10) + dp(36) + dp(20) {
            return x < dp(60) ? "ali:back" : "noop"
        }
        let laid = layout()
        if laid.summary.contains(CGPoint(x: x, y: y)) {
            // 按钮区域（汇总卡底部 44dp）
            if y > laid.summary.maxY - cardPad - dp(44) {
                return "ali:yearbill:analysis"
            }
        }
        return "noop"
    }
}
