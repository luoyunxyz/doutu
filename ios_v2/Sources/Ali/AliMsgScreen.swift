import UIKit

/// 「支付宝 → 消息」（公众号消息页）—— `AliMsgActivity.onCreate()`（Java:35-217）的 1:1 移植。
///
/// Android 这一页是纯 View 搭建：顶部「消息 + 搜索条 + ☰ + ⊕」、一张「全部会话」白卡
/// （里面只有一行「消息盒子」）、底部五格。没有数据层，所以照坐标画即可。
///
/// 单位：Android 的 `dp(v)` 在 1080 宽 / density 3 下就是 `v × 3` 像素，
/// 所以全文用 `dp()`（= ×3）书写，数字与 Java 一一对应。
final class AliMsgScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    /// 整页就一屏（一张卡 + 底栏），不滚动
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    private let bg = Draw.color(0xfff2f3f5)
    private let ink = Draw.color(0xff161722)
    private let sub = Draw.color(0xff8a8b90)
    private let line = Draw.color(0xfff0f0f0)
    private let navBlue = Draw.color(0xff2a4d8f)
    private let navActive = Draw.color(0xff1e7cf5)

    final class State: ScreenState {
        /// 消息盒子那一行的可改字段
        var boxName = "消息盒子"
        var boxSub = "蚂蚁财富 | 你的总资产收益已更新"
        var boxTime = "18:01"
    }

    // MARK: - 共用度量（Java 的 dp 值）

    private let barPadH = dp(16)      // 顶栏左右内边距
    private let barPadV = dp(10)      // 顶栏上下内边距
    private let titleSize = dp(24)
    private let pillH = dp(34)
    private let plusSize = dp(30)
    private let menuSize = dp(20)

    /// 顶栏总高：上下内边距 + 最高的那个元素（搜索条 34dp）
    private var barH: CGFloat { barPadV + pillH + barPadV }

    /// 「消息」标题右边界
    private var titleRight: CGFloat {
        barPadH + Draw.width("消息", size: titleSize, bold: true)
    }

    /// ⊕ 的中心
    private var plusCx: CGFloat { Self.W - barPadH - plusSize / 2 }
    /// ☰ 的中心
    private var menuCx: CGFloat { plusCx - plusSize / 2 - dp(12) - menuSize / 2 }
    /// 搜索条（右侧到 ☰ 左边，左侧接标题后面）
    private var pillRect: CGRect {
        let right = menuCx - menuSize / 2 - dp(10)
        let left = titleRight + dp(12)
        return CGRect(x: left, y: barPadV + (pillH - pillH) / 2, width: max(60, right - left), height: pillH)
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), bg)
        drawTopBar(d)
        drawSessionCard(d, s)
        drawBottomNav(d)
    }

    /// 顶栏（Java:49-101）
    private func drawTopBar(_ d: Draw) {
        d.text("消息", x: barPadH, baseline: barPadV + Draw.font(size: titleSize, bold: true).ascender,
               size: titleSize, ink, bold: true)

        let pill = pillRect
        d.round(pill, radius: pill.height / 2, Draw.color(0xffe3e5ea))
        let baseline = pill.midY + Draw.font(size: dp(14)).ascender / 2 - dp(14) * 0.06
        let iconX = pill.minX + dp(12)
        d.text("⌕", x: iconX, baseline: baseline, size: dp(16), sub)
        d.text("搜索好友等", x: iconX + Draw.width("⌕", size: dp(16)) + dp(6),
               baseline: baseline, size: dp(14), sub)

        let textColor = Draw.color(0xff333333)
        d.centerText("☰", cx: menuCx, baseline: barH / 2 + Draw.font(size: menuSize).ascender / 2 - menuSize * 0.1,
                     size: menuSize, textColor)

        let plusRect = CGRect(x: plusCx - plusSize / 2, y: (barH - plusSize) / 2,
                              width: plusSize, height: plusSize)
        d.roundStroke(plusRect, radius: plusSize / 2, textColor, width: dp(1))
        let plusSize2 = dp(22)
        d.centerText("+", cx: plusCx,
                     baseline: plusRect.midY + Draw.font(size: plusSize2).ascender / 2 - plusSize2 * 0.1,
                     size: plusSize2, textColor)
    }

    /// 「全部会话」白卡（Java:104-175）
    private func drawSessionCard(_ d: Draw, _ s: State) {
        // 卡头 12 + 15sp 行高 + 10；分割线 1；消息盒子行 12 + 42 + 14
        let headerH = dp(12) + dp(15) * 1.4 + dp(10)
        let rowH = dp(12) + dp(42) + dp(14)
        let card = CGRect(x: dp(12), y: barH + dp(6), width: Self.W - dp(24),
                          height: headerH + dp(1) + rowH)
        d.round(card, radius: dp(14), .white)

        // 卡头：「全部会话」在左，四个符号从右往左排
        let headerBaseline = card.minY + dp(12) + Draw.font(size: dp(15)).ascender
        d.text("全部会话", x: card.minX + dp(16), baseline: headerBaseline, size: dp(15),
               Draw.color(0xff333333))
        var glyphRight = card.maxX - dp(16)
        for glyph in ["🎁", "¥", "◧", "@"] {
            d.rightText(glyph, right: glyphRight, baseline: headerBaseline, size: dp(14), sub)
            glyphRight -= Draw.width(glyph, size: dp(14)) + dp(14)
        }

        // 分割线
        let lineY = card.minY + headerH
        d.hairline(lineY, from: card.minX, to: card.maxX, line)

        // 消息盒子一行：蓝圆铃铛 + 标题/副标题 + 右侧时间
        let rowTop = lineY + dp(1) + dp(12)
        let bellSize = dp(42)
        let bellCx = card.minX + dp(16) + bellSize / 2
        d.circle(cx: bellCx, cy: rowTop + bellSize / 2, r: bellSize / 2, navActive)
        let bellSize2 = dp(18)
        d.centerText("🔔", cx: bellCx,
                     baseline: rowTop + bellSize / 2 + Draw.font(size: bellSize2).ascender / 2 - bellSize2 * 0.1,
                     size: bellSize2, .white)

        let textX = card.minX + dp(16) + bellSize + dp(12)
        d.text(s.boxName, x: textX, baseline: rowTop + Draw.font(size: dp(16)).ascender,
               size: dp(16), ink)
        d.text(s.boxSub, x: textX, baseline: rowTop + dp(22) + Draw.font(size: dp(13)).ascender,
               size: dp(13), sub)
        d.rightText(s.boxTime, right: card.maxX - dp(16),
                    baseline: rowTop + dp(12) + Draw.font(size: dp(12)).ascender,
                    size: dp(12), Draw.color(0xff9a9b9f))
    }

    /// 底部五格（Java:180-213）
    private var navH: CGFloat { dp(6) + dp(30) + dp(2) + dp(11) * 1.4 + dp(10) }

    private func drawBottomNav(_ d: Draw) {
        let labels = ["首页", "理财", "视频", "消息", "我的"]
        let glyphs = ["支", "¥", "▷", "✉", "人"]
        let selected = 3

        let top = Self.H - navH
        d.rect(CGRect(x: 0, y: top, width: Self.W, height: navH), .white)

        let cellW = Self.W / CGFloat(labels.count)
        for (index, label) in labels.enumerated() {
            let isOn = index == selected
            let cx = cellW * (CGFloat(index) + 0.5)
            let iconRect = CGRect(x: cx - dp(15), y: top + dp(6), width: dp(30), height: dp(30))
            d.circle(cx: iconRect.midX, cy: iconRect.midY, r: dp(15), isOn ? navActive : .white)
            d.roundStroke(iconRect, radius: dp(15), navBlue, width: dp(1))
            let glyphSize = dp(16)
            d.centerText(glyphs[index], cx: iconRect.midX,
                         baseline: iconRect.midY + Draw.font(size: glyphSize).ascender / 2 - glyphSize * 0.1,
                         size: glyphSize, isOn ? .white : navBlue)
            let labelSize = dp(11)
            d.centerText(label, cx: cx,
                         baseline: iconRect.maxY + dp(2) + Draw.font(size: labelSize).ascender,
                         size: labelSize, isOn ? navActive : Draw.color(0xff666666))
        }
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < barH {
            // 用平方距离判圆形热区（`hypot` 对 CGFloat 的重载不保险）
            let dx = x - plusCx
            let dy = y - barH / 2
            if dx * dx + dy * dy <= plusSize * plusSize { return "ali:gzh" }
            let mx = x - menuCx
            if mx * mx + dy * dy <= (menuSize * 1.6) * (menuSize * 1.6) { return "ali:contacts" }
            return "noop"
        }
        let headerH = dp(12) + dp(15) * 1.4 + dp(10)
        let rowH = dp(12) + dp(42) + dp(14)
        let cardTop = barH + dp(6)
        if y >= cardTop + headerH && y < cardTop + headerH + dp(1) + rowH { return "ali:box" }
        if y > Self.H - navH {
            let index = Int(x / (Self.W / 5))
            return "ali:nav:\(index)"
        }
        return "noop"
    }
}
