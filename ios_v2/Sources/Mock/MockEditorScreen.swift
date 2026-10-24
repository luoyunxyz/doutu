import UIKit

/// 「模拟 → 某家银行」的编辑页 —— 对应 Android 的 `NyeActivity` / `IcbcActivity` / …。
///
/// 结构与 Android 完全一致：
///   顶部（固定）：`‹` 返回 + 居中标题 + 右侧「切换」
///   内容（可滚）：19 个机型模板按钮 → 绿色「生成图片」→ 明细预览（点文字即可改）
///   底部（固定）：选择模版 / 保存模版 / 恢复默认 / 打开填写
///   右侧抽屉：本模块全部子页（进页面自动打开一次，与 Android 的 `showDrawer(true)` 一致）
///
/// 预览本身由 `MockComposer` 按模块出图，这一页只负责「外壳 + 交互」。
final class MockEditorScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    /// 滚动交给宿主（`ScreenHostView` 会按 `maxScroll` 夹取并 translate）
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388
    /// 顶部栏高（Java `dp(52)`）
    static let topH = dp(52)
    /// 底部栏高：10 + 40 + 10 的按钮行 + 1dp 分割线
    static let bottomH = dp(10) + dp(40) + dp(10) + dp(1)

    /// 模板按钮
    static let chipH = dp(38)
    static let chipGapH = dp(4)
    static let chipGapV = dp(8)
    static let chipPadH = dp(6)
    static let chipPadTop = dp(10)
    static let chipRows: Int = { (MockDevices.all.count + 3) / 4 }()

    /// 生成按钮
    static let genH = dp(46)
    static let genGapTop = dp(10)
    static let genPadH = dp(8)

    /// 抽屉宽（Java `dp(210)`）
    static let drawerW = dp(210)
    static let drawerTop = dp(120)
    static let drawerRowH = dp(54)

    final class State: ScreenState {
        let module: MockModule
        var sub: Int
        var device = 0
        var time: String
        var fields: [String: String] = [:]
        var drawerOpen = true

        init(module: MockModule, sub: Int = 0) {
            self.module = module
            self.sub = min(max(0, sub), max(0, module.subs.count - 1))
            self.time = MockState.nowString()
            super.init()
        }

        /// 预览版式需要的状态视图。
        ///
        /// 缓存同一个实例（每次只同步可变字段）：`MockState.value()` 会记录「上一帧显示的值」，
        /// 每次新建实例的话，点开编辑框时就没法预填用户看到的文字。
        private var cached: MockState?

        var renderState: MockState {
            let state: MockState
            if let cached {
                state = cached
            } else {
                state = MockState(module: module, sub: sub)
                cached = state
            }
            state.sub = sub
            state.device = device
            state.time = time
            state.fields = fields
            return state
        }

        func value(_ key: String, _ fallback: String) -> String {
            renderState.value(key, fallback)
        }

        /// 编辑框预填用：上一帧实际显示的文字
        func currentValue(_ key: String) -> String {
            renderState.currentValue(key)
        }

        func apply(_ key: String, _ text: String) {
            if text.isEmpty {
                fields.removeValue(forKey: key)
            } else if key == "交易时间" {
                time = text
            } else {
                fields[key] = text
            }
        }
    }

    // MARK: - 版式（画与点共用）

    /// 内容坐标下各块的 y
    private var chipsTop: CGFloat { Self.topH + Self.chipPadTop }
    private var chipsBottom: CGFloat {
        chipsTop + CGFloat(Self.chipRows) * Self.chipH
            + CGFloat(Self.chipRows - 1) * Self.chipGapV
    }
    private var genTop: CGFloat { chipsBottom + Self.genGapTop }
    private var previewTop: CGFloat { genTop + Self.genH }

    private func chipRect(_ index: Int) -> CGRect {
        let row = index / 4
        let col = index % 4
        let cellW = (Self.W - Self.chipPadH * 2 - 3 * Self.chipGapH) / 4
        let x = Self.chipPadH + CGFloat(col) * (cellW + Self.chipGapH)
        let y = chipsTop + CGFloat(row) * (Self.chipH + Self.chipGapV)
        return CGRect(x: x, y: y, width: cellW, height: Self.chipH)
    }

    private var genRect: CGRect {
        CGRect(x: Self.genPadH, y: genTop, width: Self.W - Self.genPadH * 2, height: Self.genH)
    }

    private func page(_ s: State) -> MockPage {
        MockComposer.page(module: s.module, state: s.renderState,
                          metrics: .editor, width: Self.W, showsTopTitle: true)
    }

    // MARK: - 滚动

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State(module: .nye)
        let contentBottom = previewTop + page(s).height + dp(12)
        return max(0, contentBottom - (Self.H - Self.bottomH))
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State(module: .nye)
        let scroll = s.scroll

        // 底色（只铺可见区，滚动时不会露出未绘制的部分）
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), MockInk.page)

        drawChips(d, s)
        drawGenerate(d)

        // 预览：白底卡片 + 版式
        let page = self.page(s)
        d.rect(CGRect(x: 0, y: previewTop, width: Self.W, height: page.height), MockInk.white)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: previewTop)
        page.draw(d)
        d.ctx.restoreGState()

        // 固定层
        drawTopBar(d, s, scroll: scroll)
        drawBottomBar(d, s, scroll: scroll)
        if s.drawerOpen { drawDrawer(d, s, scroll: scroll) }
    }

    private func drawChips(_ d: Draw, _ s: State) {
        let size = dp(13)
        let baselineOffset = Draw.font(size: size).ascender
            + (Self.chipH - Draw.font(size: size).lineHeight) / 2
        for (index, name) in MockDevices.all.enumerated() {
            let rect = chipRect(index)
            d.round(rect, radius: dp(8), index == s.device ? MockInk.chipOn : MockInk.chip)
            d.centerText(name, cx: rect.midX, baseline: rect.minY + baselineOffset,
                         size: size,
                         index == s.device ? MockInk.white : MockInk.value, bold: false)
        }
    }

    private func drawGenerate(_ d: Draw) {
        let rect = genRect
        d.round(rect, radius: dp(6), Draw.color(0xff00b578))
        let size = dp(17)
        let baseline = rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06
        d.centerText("生成图片", cx: rect.midX, baseline: baseline, size: size, MockInk.white)
    }

    private func drawTopBar(_ d: Draw, _ s: State, scroll: CGFloat) {
        let y = scroll
        d.rect(CGRect(x: 0, y: y, width: Self.W, height: Self.topH), MockInk.white)
        // ‹ 返回
        d.centerText("‹", cx: dp(25), baseline: y + Self.topH / 2 + dp(9),
                     size: dp(26), MockInk.value)
        // 居中标题（点子页名也能打开抽屉，与 Android 一致）
        let title = s.module.subs.indices.contains(s.sub) ? s.module.subs[s.sub] : s.module.title
        let titleSize = dp(18)
        d.centerText(title, cx: Self.W / 2,
                     baseline: y + Self.topH / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
                     size: titleSize, MockInk.title)
        // 切换
        let menuSize = dp(13)
        d.text("切换", x: Self.W - dp(60) + (dp(60) - Draw.width("切换", size: menuSize)) / 2,
               baseline: y + Self.topH / 2 + Draw.font(size: menuSize).ascender / 2 - menuSize * 0.06,
               size: menuSize, MockInk.grey)
    }

    private func drawBottomBar(_ d: Draw, _ s: State, scroll: CGFloat) {
        let top = scroll + Self.H - Self.bottomH
        d.hairline(top, from: 0, to: Self.W, MockInk.line)
        d.rect(CGRect(x: 0, y: top + dp(1), width: Self.W, height: Self.bottomH - dp(1)),
               MockInk.white)
        let items = ["选择模版", "保存模版", "恢复默认", "打开填写"]
        let size = dp(12)
        let padH = dp(6)
        let itemW = (Self.W - padH * 2) / CGFloat(items.count)
        for (index, item) in items.enumerated() {
            let cx = padH + itemW * (CGFloat(index) + 0.5)
            let baseline = top + dp(1) + dp(10) + dp(20)
                + Draw.font(size: size).ascender / 2 - size * 0.06
            d.centerText(item, cx: cx, baseline: baseline, size: size, MockInk.value)
        }
    }

    private func drawDrawer(_ d: Draw, _ s: State, scroll: CGFloat) {
        // 遮罩
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), MockInk.dim)
        // 右侧面板
        let panelX = Self.W - Self.drawerW
        d.rect(CGRect(x: panelX, y: scroll, width: Self.drawerW, height: Self.H), MockInk.white)

        let size = dp(14)
        for (index, name) in s.module.subs.enumerated() {
            let top = scroll + Self.drawerTop + CGFloat(index) * (Self.drawerRowH + dp(1))
            let logoSize = dp(18)
            d.art(UIImage(named: s.module.logo),
                  CGRect(x: panelX + dp(14), y: top + (Self.drawerRowH - logoSize) / 2,
                         width: logoSize, height: logoSize))
            let baseline = top + Self.drawerRowH / 2
                + Draw.font(size: size).ascender / 2 - size * 0.06
            d.text(name, x: panelX + dp(14) + logoSize + dp(8), baseline: baseline,
                   size: size, MockInk.title)
            d.hairline(top + Self.drawerRowH, from: panelX, to: Self.W, MockInk.line)
        }
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State(module: .nye)
        let scroll = s.scroll

        // 抽屉在最上层：先判抽屉，再判页面其余部分
        if s.drawerOpen {
            let panelX = Self.W - Self.drawerW
            if x >= panelX {
                let local = y - scroll - Self.drawerTop
                if local >= 0 {
                    let index = Int(local / (Self.drawerRowH + dp(1)))
                    if index >= 0 && index < s.module.subs.count { return "mock:sub:\(index)" }
                }
                return "noop"
            }
            // 点遮罩关闭
            return "mock:drawer:close"
        }

        // 顶部栏（固定在屏幕顶，内容坐标要加 scroll）
        if y >= scroll && y < scroll + Self.topH {
            if x < dp(50) { return "mock:back" }
            if x > Self.W - dp(66) { return "mock:drawer" }
            return "mock:drawer"
        }

        // 底部栏
        let barTop = scroll + Self.H - Self.bottomH
        if y >= barTop {
            let padH = dp(6)
            let itemW = (Self.W - padH * 2) / 4
            let index = Int((x - padH) / itemW)
            switch index {
            case 0: return "mock:template"
            case 1: return "mock:save"
            case 2: return "mock:reset"
            case 3: return "mock:fill"
            default: return "noop"
            }
        }

        // 机型模板
        for index in MockDevices.all.indices where chipRect(index).contains(CGPoint(x: x, y: y)) {
            return "mock:device:\(index)"
        }

        // 生成图片
        if genRect.contains(CGPoint(x: x, y: y)) { return "mock:gen" }

        // 预览里的可改文字
        let page = self.page(s)
        let local = CGPoint(x: x, y: y - previewTop)
        if local.y >= 0, let token = page.token(at: local) { return token }

        return "noop"
    }
}
