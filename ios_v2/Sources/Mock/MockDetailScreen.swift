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
        let scroll = s.scroll

        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), MockInk.white)

        // 成品页内容（整页白底 + 版式）
        let page = self.page(s)
        d.rect(CGRect(x: 0, y: Self.headH, width: Self.W, height: page.height), MockInk.white)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: Self.headH)
        page.draw(d)
        d.ctx.restoreGState()

        // 固定标题栏
        let y = scroll
        d.rect(CGRect(x: 0, y: y, width: Self.W, height: Self.headH), MockInk.white)
        d.centerText("‹", cx: dp(26), baseline: y + Self.headH / 2 + dp(9),
                     size: dp(28), MockInk.value)
        let size = dp(19)
        d.centerText(title(s), cx: Self.W / 2,
                     baseline: y + Self.headH / 2 + Draw.font(size: size, bold: true).ascender / 2
                        - size * 0.06,
                     size: size, MockInk.title, bold: true)
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
