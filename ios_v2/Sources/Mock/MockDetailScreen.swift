import UIKit

/// 「生成图片」后的全屏成品页 —— 对应 Android 的 `NyeDetailActivity` / `IcbcDetailActivity` / …。
///
/// 与编辑页的区别（照抄 Android）：
///   · 没有机型模板、没有「生成图片」按钮、没有底部四个按钮；
///   · 顶部是一条固定标题栏（`‹` + 粗体标题）；
///   · 同一份版式换成成品度量（字号 / 内边距各 +1~2dp，见 `MockMetrics.detail`）；
///   · **不再有右上角「保存」**（交接文档里明确要求四类页面都没有）。
final class MockDetailScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false
    /// 成品页是一张「整屏截图」：按参考画布原比例铺满，避免安全区把整页纵向压扁 11.5%
    let keepsReferenceAspect = true

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388
    static let headH = dp(52)

    final class State: ScreenState {
        let module: MockModule
        let sub: Int
        var time: String
        var fields: [String: String]

        init(module: MockModule, sub: Int, time: String, fields: [String: String]) {
            self.module = module
            self.sub = sub
            self.time = time
            self.fields = fields
            super.init()
        }

        private var cached: MockState?

        /// 与编辑页同样的做法：复用同一个 `MockState`，这样「上一帧显示值」不会被重置
        var renderState: MockState {
            let state: MockState
            if let cached {
                state = cached
            } else {
                state = MockState(module: module, sub: sub)
                cached = state
            }
            state.sub = sub
            state.time = time
            state.fields = fields
            return state
        }
    }

    private func page(_ s: State) -> MockPage {
        MockComposer.page(module: s.module, state: s.renderState,
                          metrics: .detail, width: Self.W, showsTopTitle: false)
    }

    /// 成品页标题：`MockModule.detailTitle(sub)`（各家 Java 的 `titleFor()` / `header(...)`）
    private func title(_ s: State) -> String {
        s.module.detailTitle(s.sub)
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        guard let s = state as? State else { return 0 }
        return max(0, Self.headH + page(s).height - Self.H)
    }

    func draw(_ d: Draw, state: ScreenState) {
        guard let s = state as? State else { return }
        // 远程布局优先：电脑上有该页的 spec 时，直接按安卓页面的视觉块重绘
        if let spec = MockRemote.spec(s.module, s.sub) {
            let page = MockRemote.page(from: spec)
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            page.draw(d)
            // 回退箭头在安卓是图标（不在 DOM 文本里），spec 里没有，这里补画，
            // 让「‹ 明细」和参考图一致。
            let barH = MockRemote.headerHeight(spec)
            d.centerText("‹", cx: dp(26), baseline: barH / 2 + dp(9),
                         size: dp(28), MockInk.value)
            return
        }
        let scroll = s.scroll

        // 底板：内容之外露出来的部分用页面自己的底板色（列表类是浅灰）
        let page = self.page(s)
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), page.background)

        // 成品页内容
        d.rect(CGRect(x: 0, y: Self.headH, width: Self.W, height: page.height), page.background)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: Self.headH)
        page.draw(d)
        d.ctx.restoreGState()

        // 固定标题栏
        let y = scroll
        let style = headerStyle(s)
        d.rect(CGRect(x: 0, y: y, width: Self.W, height: Self.headH), MockInk.white)
        d.centerText("‹", cx: dp(26), baseline: y + Self.headH / 2 + dp(9),
                     size: dp(28), style.backColor)
        let size = style.titleSize
        d.centerText(title(s), cx: Self.W / 2,
                     baseline: y + Self.headH / 2 + Draw.font(size: size, bold: style.bold).ascender / 2
                        - size * 0.06,
                     size: size, style.titleColor, bold: style.bold)
        // 工行「汇款明细查询」右上角：耳机客服 + ⋯（都是从参考图抠出来的）
        if let icon = style.supportIcon {
            let w = dp(24.2), h = dp(18.8)
            d.art(UIImage(named: icon),
                  CGRect(x: Self.W - dp(56) - w, y: y + (Self.headH - h) / 2,
                         width: w, height: h))
        }
        if let icon = style.moreIcon {
            let w = dp(20.6), h = dp(4.8)
            d.art(UIImage(named: icon),
                  CGRect(x: Self.W - dp(13.3) - w, y: y + (Self.headH - h) / 2,
                         width: w, height: h))
        }
        if let line = style.divider {
            d.hairline(y + Self.headH, from: 0, to: Self.W, line,
                       width: style.dividerWidth)
        }
    }

    /// 成品页标题栏的差异（各家银行不一样）：
    /// - 农业「明细」：黑色粗体 + 底部 1px 浅灰线；
    /// - 工商「汇款明细查询」：灰蓝常规体 + 耳机 / ⋯ 两个图标 + 底部 1px 灰绿线；
    /// - 其余：黑色粗体，无图标、无分割线。
    private struct HeaderStyle {
        var titleColor: UIColor = MockInk.title
        var titleSize: CGFloat = dp(17)
        var bold = true
        var backColor: UIColor = MockInk.value
        var divider: UIColor? = nil
        var dividerWidth: CGFloat = 1
        var supportIcon: String? = nil
        var moreIcon: String? = nil
    }

    private func headerStyle(_ s: State) -> HeaderStyle {
        if s.module == .icbc && s.sub == 0 {
            return HeaderStyle(titleColor: MockInk.steel, titleSize: dp(19), bold: false,
                               backColor: MockInk.steel,
                               divider: Draw.color(0xffc8d2ce), dividerWidth: dp(1),
                               supportIcon: "mock_icbc_support",
                               moreIcon: "mock_icbc_more")
        }
        if s.module == .nye && s.sub == 0 {
            return HeaderStyle(divider: MockInk.line)
        }
        return HeaderStyle()
    }

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        guard let s = state as? State else { return "noop" }
        let scroll = s.scroll
        if y >= scroll && y < scroll + Self.headH {
            if x < dp(60) { return "back" }
            return "noop"
        }
        // 成品页里的文字同样可点改（Android 的成品页也 `bind()` 了每一个值）
        let page = self.page(s)
        let local = CGPoint(x: x, y: y - Self.headH)
        if local.y >= 0, let token = page.token(at: local) { return token }
        return "noop"
    }
}
