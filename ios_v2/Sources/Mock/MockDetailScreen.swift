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

    /// 各页标题栏高度（中行「操作结果」的标题栏比默认的 52dp 矮得多）
    private func headH(_ s: State) -> CGFloat {
        (s.module == .boc || (s.module == .psbc && s.sub == 0)) ? dp(31.5) : Self.headH
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        guard let s = state as? State else { return 0 }
        // 邮政这 8 页是整页截图贴图，固定一屏
        if s.module == .psbc && (0...7).contains(s.sub) { return 0 }
        if s.module == .cmb && (s.sub == 0 || s.sub == 1) { return 0 }
        if s.module == .eank && (0...2).contains(s.sub) { return 0 }
        if s.module == .pingan && (0...4).contains(s.sub) { return 0 }
        if s.module == .jiaotong && (0...9).contains(s.sub) { return 0 }
        if s.module == .zhongxin && (0...4).contains(s.sub) { return 0 }
        if s.module == .pufa && (0...3).contains(s.sub) { return 0 }
        if s.module == .minsheng && (0...2).contains(s.sub) { return 0 }
        if s.module == .huiwang && (0...2).contains(s.sub) { return 0 }
        if s.module == .jianshe, (0...11).contains(s.sub), let img = UIImage(named: "mock_js_\(s.sub)") {
            return max(0, Self.W * img.size.height / max(1, img.size.width) - Self.H)
        }
        if s.module == .xingye, (0...4).contains(s.sub),
           let img = UIImage(named: "mock_xy_\(s.sub)") {
            return max(0, Self.W * img.size.height / max(1, img.size.width) - Self.H)
        }
        return max(0, headH(s) + page(s).height - Self.H)
    }

    func draw(_ d: Draw, state: ScreenState) {
        guard let s = state as? State else { return }
        // 汇旺三页：水印底图 + 原生文字（改字时用水印层补回纹理）
        if s.module == .huiwang, (0...2).contains(s.sub),
           let img = UIImage(named: "mock_hw_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), MockInk.white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            let wo = UIImage(named: "mock_hw_wo_\(s.sub)")
            for f in MockHuiwang.overlayFields(s.sub) {
                guard let v = s.fields[f.key], !v.isEmpty else { continue }
                d.rect(f.rect, f.bg)
                if let wo, wo.size.width > 0 {
                    let tw = wo.size.width, th = wo.size.height
                    let oy = h - 420
                    d.ctx.saveGState()
                    d.ctx.clip(to: f.rect)
                    var m = Int(floor((f.rect.minY - oy) / th)) - 1
                    while oy + CGFloat(m) * th < f.rect.maxY + th {
                        var x = -tw
                        while x < Self.W + tw {
                            d.art(wo, CGRect(x: x, y: oy + CGFloat(m) * th, width: tw, height: th))
                            x += tw
                        }
                        m += 1
                    }
                    d.ctx.restoreGState()
                }
                let base = f.rect.maxY - 6
                switch f.align {
                case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                }
            }
            return
        }
        // 民生三页：整页贴图 + 数字热区
        if s.module == .minsheng, (0...2).contains(s.sub),
           let img = UIImage(named: "mock_ms_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockMinsheng.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 浦发四页：整页贴图 + 数字热区
        if s.module == .pufa, (0...3).contains(s.sub),
           let img = UIImage(named: "mock_pf_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockPufa.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 中信 明细转出1(旧)：整页贴图 + 数字热区
        if s.module == .zhongxin, (0...4).contains(s.sub), let img = UIImage(named: "mock_zx_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockZhongxin.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 交通十页：整页贴图 + 数字热区
        if s.module == .jiaotong, (0...9).contains(s.sub),
           let img = UIImage(named: "mock_jt_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockJiaotong.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 平安五页：整页贴图 + 数字热区
        if s.module == .pingan, (0...4).contains(s.sub),
           let img = UIImage(named: "mock_pa_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockPingan.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 建设 00 明细转出(旧)：整页贴图 + 数字热区
        if s.module == .jianshe, (0...11).contains(s.sub), let img = UIImage(named: "mock_js_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockJianshe.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 兴业五页：整页贴图 + 数字热区
        if s.module == .xingye, (0...4).contains(s.sub),
           let img = UIImage(named: "mock_xy_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockXingye.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 光大三页：整页贴图 + 数字热区
        if s.module == .eank, (0...2).contains(s.sub),
           let img = UIImage(named: "mock_eank_\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockEank.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 招商两页：整页贴图 + 数字热区
        if s.module == .cmb, (s.sub == 0 || s.sub == 1),
           let img = UIImage(named: s.sub == 0 ? "mock_cmb_out" : "mock_cmb_in") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            for f in MockCmb.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, f.bg)
                    let base = f.rect.maxY - 6
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
        // 邮政 8 页：用户要求「禁止手绘、缺什么抠什么」，整页直接用截图贴图。
        if s.module == .psbc, (0...7).contains(s.sub),
           let img = UIImage(named: "mock_psbc_p\(s.sub)") {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), MockInk.white)
            let h = Self.W * img.size.height / max(1, img.size.width)
            d.art(img, CGRect(x: 0, y: 0, width: Self.W, height: h))
            d.rect(CGRect(x: 0, y: h - dp(40), width: Self.W, height: dp(40)), MockInk.white)
            // 数字热区：改过的值覆盖在原位，没改过保持截图原样
            for f in MockPsbc.overlayFields(s.sub) {
                if let v = s.fields[f.key], !v.isEmpty {
                    d.rect(f.rect, MockInk.white)
                    let mid = f.rect.midY
                    let base = mid + Draw.font(size: f.size).ascender / 2 - f.size * 0.06
                    switch f.align {
                    case 1: d.centerText(v, cx: f.rect.midX, baseline: base, size: f.size, f.color)
                    case 2: d.rightText(v, right: f.rect.maxX, baseline: base, size: f.size, f.color)
                    default: d.text(v, x: f.rect.minX, baseline: base, size: f.size, f.color)
                    }
                }
            }
            return
        }
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
        let barH = headH(s)
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), page.background)

        // 成品页内容
        d.rect(CGRect(x: 0, y: barH, width: Self.W, height: page.height), page.background)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: barH)
        page.draw(d)
        d.ctx.restoreGState()

        // 固定标题栏
        let y = scroll
        let style = headerStyle(s)
        d.rect(CGRect(x: 0, y: y, width: Self.W, height: barH), MockInk.white)
        d.centerText("‹", cx: dp(26), baseline: y + barH / 2 + dp(9),
                     size: dp(28), style.backColor)
        let size = style.titleSize
        d.centerText(title(s), cx: Self.W / 2,
                     baseline: y + barH / 2 + Draw.font(size: size, bold: style.bold).ascender / 2
                        - size * 0.06,
                     size: size, style.titleColor, bold: style.bold)
        // 右上角图标（工行客服 / ⋯ / 下载等，都是从参考图抠出来的）
        for icon in style.icons {
            d.art(UIImage(named: icon.name), icon.rect.offsetBy(dx: 0, dy: y))
        }
        if let line = style.divider {
            d.hairline(y + barH, from: 0, to: Self.W, line,
                       width: style.dividerWidth)
        }
    }

    /// 标题栏右侧的一个切图（坐标是参考画布坐标，`y` 以标题栏顶为 0）
    private struct HeaderIcon {
        let name: String
        let rect: CGRect
    }

    /// 成品页标题栏的差异（各家银行不一样）：
    /// - 农业「明细」：黑色粗体 + 底部 1px 浅灰线；
    /// - 工商「汇款明细查询」：灰蓝常规体 + 耳机 / ⋯ + 底部灰绿线；
    /// - 工商「明细详情」：灰色常规体 + 耳机 / 下载 + 底部浅灰线；
    /// - 其余：黑色粗体，无图标、无分割线。
    private struct HeaderStyle {
        var titleColor: UIColor = MockInk.title
        var titleSize: CGFloat = dp(17)
        var bold = true
        var backColor: UIColor = MockInk.value
        var divider: UIColor? = nil
        var dividerWidth: CGFloat = 1
        var icons: [HeaderIcon] = []
    }

    private func headerStyle(_ s: State) -> HeaderStyle {
        if s.module == .icbc && s.sub == 0 {
            return HeaderStyle(titleColor: MockInk.steel, titleSize: dp(19), bold: false,
                               backColor: MockInk.steel,
                               divider: Draw.color(0xffc8d2ce), dividerWidth: dp(1),
                               icons: [
                                   HeaderIcon(name: "mock_icbc_support",
                                              rect: CGRect(x: 839.4, y: 49.8,
                                                           width: 72.6, height: 56.4)),
                                   HeaderIcon(name: "mock_icbc_more",
                                              rect: CGRect(x: 978.2, y: 70.8,
                                                           width: 61.8, height: 14.4)),
                               ])
        }
        if s.module == .icbc && s.sub == 1 {
            return HeaderStyle(titleColor: Draw.color(0xff909090), titleSize: dp(19), bold: false,
                               backColor: Draw.color(0xff909090),
                               divider: MockInk.line, dividerWidth: dp(1),
                               icons: [
                                   HeaderIcon(name: "mock_icbc_download",
                                              rect: CGRect(x: 995.6, y: 51.5,
                                                           width: 53, height: 53)),
                               ])
        }
        if s.module == .icbc && s.sub == 2 {
            return HeaderStyle(titleColor: MockInk.grey, titleSize: dp(19), bold: false,
                               backColor: MockInk.grey,
                               divider: MockInk.line, dividerWidth: dp(1),
                               icons: [
                                   HeaderIcon(name: "mock_icbc_support2",
                                              rect: CGRect(x: 884, y: 45.5,
                                                           width: 65, height: 65)),
                                   HeaderIcon(name: "mock_icbc_download",
                                              rect: CGRect(x: 989, y: 46,
                                                           width: 60, height: 64)),
                               ])
        }
        if s.module == .boc && s.sub == 0 {
            return HeaderStyle(titleColor: MockInk.title, titleSize: dp(15), bold: true,
                               backColor: MockInk.value,
                               icons: [
                                   HeaderIcon(name: "mock_boc_headset",
                                              rect: CGRect(x: 969.4, y: 17.25,
                                                           width: 62, height: 60)),
                               ])
        }
        if s.module == .boc && (s.sub == 3 || s.sub == 4) {
            return HeaderStyle(titleColor: MockInk.title, titleSize: dp(17), bold: true,
                               backColor: MockInk.value, divider: MockInk.line,
                               dividerWidth: 1,
                               icons: s.sub == 3 ? [
                                   HeaderIcon(name: "mock_boc_headset",
                                              rect: CGRect(x: 969.4, y: 17.25,
                                                           width: 62, height: 60)),
                               ] : [])
        }
        if s.module == .nye && s.sub == 0 {
            return HeaderStyle(divider: MockInk.line)
        }
        return HeaderStyle()
    }

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        guard let s = state as? State else { return "noop" }
        let scroll = s.scroll
        let barH = headH(s)
        if y >= scroll && y < scroll + barH {
            if x < dp(60) { return "back" }
            return "noop"
        }
        // 汇旺整页贴图上的数字热区
        if s.module == .huiwang, (0...2).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockHuiwang.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 民生整页贴图上的数字热区
        if s.module == .minsheng, (0...2).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockMinsheng.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 浦发整页贴图上的数字热区
        if s.module == .pufa, (0...3).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockPufa.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 中信整页贴图上的数字热区
        if s.module == .zhongxin, (0...4).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockZhongxin.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 交通整页贴图上的数字热区
        if s.module == .jiaotong, (0...9).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockJiaotong.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 平安整页贴图上的数字热区
        if s.module == .pingan, (0...4).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockPingan.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 建设整页贴图上的数字热区
        if s.module == .jianshe, (0...11).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockJianshe.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 兴业整页贴图上的数字热区
        if s.module == .xingye, (0...4).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockXingye.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 光大整页贴图上的数字热区
        if s.module == .eank, (0...2).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockEank.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 招商整页贴图上的数字热区
        if s.module == .cmb, (s.sub == 0 || s.sub == 1) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockCmb.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 邮政整页贴图上的数字热区
        if s.module == .psbc, (0...7).contains(s.sub) {
            let p = CGPoint(x: x, y: y - scroll)
            for f in MockPsbc.overlayFields(s.sub) where f.rect.contains(p) {
                return "mock:field:\(f.key)"
            }
        }
        // 成品页里的文字同样可点改（Android 的成品页也 `bind()` 了每一个值）
        let page = self.page(s)
        let local = CGPoint(x: x, y: y - barH)
        if local.y >= 0, let token = page.token(at: local) { return token }
        return "noop"
    }
}
