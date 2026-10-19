import UIKit

/// 页面动作令牌 —— 与 Android 端 `hitTest` 返回的字符串一一对应。
///
/// Android 的写法是返回 `"nav:2"`、`"profile"`、`"bannerClose"` 这样的字符串常量，
/// iOS 这边保留同样的词表（不改成枚举），这样逐屏比对源码时可以直接对照，
/// 也避免了「Java 加了新动作、Swift 忘了加 case」这种漏改。
typealias ActionToken = String

/// 一个可绘制的页面。
///
/// 移植约定（**逐屏 1:1 的三条铁律**）：
///   1. `canvasSize` 与 Java 里的 `W`/`H` 常量完全一致；
///   2. `draw` 里的每一条绘制语句，对应 Java `draw(Canvas, State)` 里的一行，坐标原样照抄；
///   3. `hitTest` 的分支顺序与判定区间与 Java 完全一致（命中顺序会改变交互结果）。
protocol Screen: AnyObject {
    /// 参考画布尺寸（Java 的 W/H）
    var canvasSize: CGSize { get }
    /// 可滚动高度（返回 0 表示不滚动）
    func maxScroll(_ state: ScreenState) -> CGFloat
    /// 绘制（坐标系已翻正并套好缩放，可直接用参考画布坐标）
    func draw(_ draw: Draw, state: ScreenState)
    /// 命中测试：入参是**参考画布坐标**
    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken
    /// 支模块五格底栏的当前选中项（0 首页 / 1 理财 / 2 视频 / 3 消息 / 4 我的）。
    /// 必须写在 protocol 里，否则通过 `Screen` 存在类型访问时永远拿到扩展默认的 nil。
    var zhiTabIndex: Int? { get }
    /// 纵向拖动（财富页下拉展开资产面板）。必须写在 protocol 里才会动态派发。
    func verticalDrag(_ state: ScreenState, dy: CGFloat, startY: CGFloat) -> Bool
    func verticalDragEnd(_ state: ScreenState)
}

extension Screen {
    /// 这一屏的 `draw` 是否**自己**做了 `translate(0, -scroll)`。
    ///
    /// Android 每个页面的 `draw(Canvas, State)` 开头都是
    /// `c.save(); c.translate(0, -s.scroll); …; c.restore()` —— 所以 17 个页面在移植时
    /// 都照抄了这句，默认是 `true`。
    ///
    /// 但另外十几屏（账单列表、消息列表、出行、小荷包…）的 Java 里滚动是由外层做的，
    /// 它们的 `draw` 里没有这句 —— 这些屏必须返回 `false`，由 `ScreenHostView` 统一滚动。
    ///
    /// **两边都做就是双倍滚动**：画面滚得比手指快一倍，而且绘制与命中对不上。
    /// 这是只有真机上才会暴露的那类 bug，所以用这个开关把它摆到明面上。
    var selfScrolls: Bool { true }

    /// 这一屏顶部的背景是不是深色/彩色，需要白色状态栏图标。
    /// Android 各 Activity 通过 `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` 的正反来选；
    /// 这里按同样的页面清单集中判断，避免每个页面各写一遍。
    var lightStatusBar: Bool {
        switch self {
        case is ZhiHomeScreen, is LicaiScreen, is GrowthScreen, is HuabeiScreen,
             is YuebaoScreen, is PaySuccessScreen, is TravelScreen, is ZhimaCreditScreen:
            return true
        default:
            return false
        }
    }

    // MARK: - 手势钩子（默认不处理）

    /// 长按（对应 Android 的 `screen.longPress(state, x, y)`）。
    ///
    /// Android 的行为是：手指按下后**没有移动**、且超过 `LONG_PRESS_MS`(500ms) 才触发，
    /// 返回一个令牌交给 Activity 处理（芝麻信用用它弹出模块菜单）。返回 nil 表示这一屏不关心长按。
    func longPress(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken? { nil }

    /// 横向拖动（对应 Android 的 `draggingCarousel` 分支）。
    ///
    /// 宿主会先把「横向位移明显大于纵向」的手势交给这里；返回 `true` 表示这一屏接管了这次手势
    /// （于是宿主不再拿它当纵向滚动）。`dx` 是相对上一次移动的增量。
    func carouselDrag(_ state: ScreenState, dx: CGFloat) -> Bool { false }

    /// 纵向拖动（对应 Android 财富页下拉展开资产面板）。
    /// `dy` 是相对上一次移动的增量，`startY` 是按下时的参考画布 y。
    /// 返回 true 表示这一屏接管了这次纵向手势，宿主不再滚页面。
    func verticalDrag(_ state: ScreenState, dy: CGFloat, startY: CGFloat) -> Bool { false }
    /// 纵向拖动松手：用于把面板吸附到展开/收起。
    func verticalDragEnd(_ state: ScreenState) {}

    /// 横向拖动松手：吸附到最近的一页（对应 Android 的 `snapCarousel(x - downX)`）。
    func carouselSnap(_ state: ScreenState, totalDx: CGFloat) {}

    /// 横向拖动的范围（页码宽度、卡片步长由各屏自己决定）
    func maxCarousel(_ state: ScreenState) -> CGFloat { 0 }

    /// 这一屏是否要在**画面底下垫一层相机预览**（扫码页用）。
    ///
    /// 返回 `true` 时宿主不再刷白底，改为把 `CameraPreviewView` 放到最底层，
    /// 页面只画自己的浮层（边框、扫描线、按钮）—— 与 Android 的
    /// `TextureView` + `ScanOverlayScreen` 叠放关系一致。
    var usesCameraBackground: Bool { false }

    /// 支模块五格底栏的当前选中项（0 首页 / 1 理财 / 2 视频 / 3 消息 / 4 我的）。
    ///
    /// 每个页面自己画底栏的移植版本经常漏画、被内容盖住、或者选中态不刷新；
    /// 这里由 `ScreenHostView` 统一盖一根底栏，页面只声明自己是第几格。
    /// 返回 nil 表示不是支模块页面，不显示底栏。
    var zhiTabIndex: Int? { nil }
}

/// 页面可变状态基类（滚动偏移等）。子类按需扩展。
class ScreenState {
    var scroll: CGFloat = 0
}

/// 支模块统一的五格底栏 —— 独立 UIView，盖在页面最上面。
///
/// 不能和页面画在同一个 CGContext 里：财富页/理财页的 draw 会 save/restore + clip，
/// 一旦有一个页面 restore 不配对，后面再画的底栏就会被裁掉/移位。独立子视图不受影响。
final class ZhiTabBarView: UIView {
    var selected = 0 { didSet { setNeedsDisplay() } }
    var onSelect: ((Int) -> Void)?

    private let centers: [CGFloat] = [128, 382, 639, 898, 1151]
    private let tint = Draw.color(0xff1677ff)

    /// 底栏颜色跟随理财主题：普通版蓝，成长版/V1/V2/V3 用各自 accent 金色系。
    private var colors: (selected: UIColor, muted: UIColor?) {
        let theme = LicaiVersion.theme()
        if theme.value == LicaiVersion.normal {
            return (tint, nil)
        }
        let accent = Draw.color(theme.accent)
        return (accent, accent.withAlphaComponent(0.52))
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        backgroundColor = .white
        isUserInteractionEnabled = true
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    override func draw(_ rect: CGRect) {
        let w = bounds.width, h = bounds.height
        UIColor.white.setFill()
        UIRectFill(bounds)
        UIColor(white: 0.93, alpha: 1).setFill()
        UIRectFill(CGRect(x: 0, y: 0, width: w, height: 0.5))

        let names = ["lci_nav_home", "lci_nav_licai", "lci_nav_video", "lci_nav_msg", "lci_nav_me"]
        let palette = colors
        for (i, refX) in centers.enumerated() {
            let cx = w * refX / 1280
            if i == 0 {
                if selected == 0 {
                    let side = h * 70 / 256
                    UIImage(named: "lci_nav_zhi")?.withTintColor(palette.selected, renderingMode: .alwaysOriginal)
                        .draw(in: CGRect(x: cx - side / 2, y: h * 16 / 256, width: side, height: side))
                    let font = UIFont.systemFont(ofSize: max(8, h * 26 / 256))
                    let text = "首页" as NSString
                    let tw = text.size(withAttributes: [.font: font]).width
                    text.draw(at: CGPoint(x: cx - tw / 2, y: h * 146 / 256 - font.lineHeight),
                              withAttributes: [.font: font, .foregroundColor: palette.selected])
                } else {
                    // lci_nav_home 是 225×256 的宽图（含图标+首页文字），不能按其它格的
                    // 86×148 拉伸，否则首页图标会被压扁变形。
                    let iw = w * 225 / 1280
                    var image = UIImage(named: "lci_nav_home")
                    if let muted = palette.muted {
                        image = image?.withTintColor(muted, renderingMode: .alwaysOriginal)
                    }
                    image?.draw(in: CGRect(x: cx - iw / 2, y: 0, width: iw, height: h))
                }
                continue
            }
            var image = UIImage(named: names[i])
            if i == selected {
                image = image?.withTintColor(palette.selected, renderingMode: .alwaysOriginal)
            } else if let muted = palette.muted {
                image = image?.withTintColor(muted, renderingMode: .alwaysOriginal)
            }
            let iw = w * 86 / 1280
            let ih = h * 148 / 256
            image?.draw(in: CGRect(x: cx - iw / 2, y: h * 44 / 256, width: iw, height: ih))
        }
        UIColor(white: 0.78, alpha: 1).setFill()
        UIBezierPath(roundedRect: CGRect(x: w * 430 / 1280, y: h * 222 / 256,
                                         width: w * 420 / 1280, height: max(2, h * 8 / 256)),
                     cornerRadius: 2).fill()
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let x = touch.location(in: self).x
        var best = 0
        var bestD = CGFloat.greatestFiniteMagnitude
        for (i, refX) in centers.enumerated() {
            let cx = bounds.width * refX / 1280
            if abs(cx - x) < bestD { bestD = abs(cx - x); best = i }
        }
        onSelect?(best)
    }
}

/// 承载页面的 UIView：负责缩放、滚动、触摸与重绘。
///
/// 对应 Android 的 `NativeView`（自定义 View + onDraw + onTouchEvent）。
final class ScreenHostView: UIView, UIScrollViewDelegate {
    private(set) var screen: Screen
    var state = ScreenState()
    /// 命中后回调动作令牌，由上层决定跳转/弹窗
    var onAction: ((ActionToken, CGPoint) -> Void)?
    /// 定时重绘（首页横幅的轮播要靠它推进）
    private var redrawTimer: Timer?
    /// 支模块统一底栏（独立子视图，永远盖在页面最上面）
    private let zhiTabBar = ZhiTabBarView()

    /// 最大滚动距离（参考画布坐标）
    private var maxScroll: CGFloat { max(0, screen.maxScroll(state)) }

    private var dragStartY: CGFloat = 0
    private var dragStartScroll: CGFloat = 0
    private var dragging = false
    private var touchStart = CGPoint.zero
    private var lastPoint = CGPoint.zero
    private var touchStartDesignY: CGFloat = 0
    private var verticalDragged = false
    /// 横向拖动（轮播）是否已接管这次手势
    private var draggingCarousel = false
    /// 手指是否移动过（长按要求「没移动」）
    private var moved = false
    /// 按下时刻（长按判定用）
    private var downAt: Date?
    /// 左边缘右滑返回：本次手势是否已经触发过（防止一次拖动连发多次）
    private var edgeBackFired = false
    /// 扫码页底下那层相机预览
    private var cameraView: CameraPreviewView?
    /// 《用户须知》专用原生 ScrollView：宿主的 touchesMoved 在这台真机上收不到，
    /// 用真正的 UIScrollView 接管滚动，页面本身仍然完整保留。
    private var termsScrollView: UIScrollView?

    init(screen: Screen) {
        self.screen = screen
        super.init(frame: .zero)
        backgroundColor = .black
        isOpaque = true
        isMultipleTouchEnabled = false
        contentMode = .redraw
        // 理财主题一变就重绘：黑卡主题会换掉「我的」页 / 消息页 / 支首页的配色与图标
        NotificationCenter.default.addObserver(
            self, selector: #selector(themeChanged),
            name: .doutuThemeChanged, object: nil)
        if let index = screen.zhiTabIndex {
            zhiTabBar.isHidden = false
            zhiTabBar.selected = index
        } else {
            zhiTabBar.isHidden = true
        }
        zhiTabBar.onSelect = { [weak self] index in
            self?.onAction?("nav:\(index)", .zero)
        }
        addSubview(zhiTabBar)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let navH = bounds.height * 256 / 2816
        zhiTabBar.frame = CGRect(x: 0, y: bounds.height - navH,
                                 width: bounds.width, height: navH)
        bringSubviewToFront(zhiTabBar)
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        updateTermsScrollView()
    }

    /// 只有在《用户须知》页才挂透明 UIScrollView；它负责真实的拖动，页面绘制仍在
    /// ScreenHostView 里。内容高度按“一屏 + maxScroll”换算，滚动时同步 state.scroll。
    private func updateTermsScrollView() {
        guard screen is TermsScreen, window != nil else {
            termsScrollView?.removeFromSuperview()
            termsScrollView = nil
            return
        }
        if let existing = termsScrollView {
            existing.frame = bounds
            let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
            existing.contentSize = CGSize(width: bounds.width,
                                          height: bounds.height + maxScroll * canvas.sy)
            return
        }
        let scroll = UIScrollView(frame: bounds)
        scroll.backgroundColor = .clear
        scroll.isOpaque = false
        scroll.showsVerticalScrollIndicator = false
        scroll.showsHorizontalScrollIndicator = false
        scroll.bounces = false
        scroll.alwaysBounceVertical = false
        scroll.delaysContentTouches = false
        scroll.canCancelContentTouches = false
        scroll.contentInsetAdjustmentBehavior = .never
        scroll.delegate = self
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
        scroll.contentSize = CGSize(width: bounds.width,
                                    height: bounds.height + maxScroll * canvas.sy)
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTermsTap(_:)))
        scroll.addGestureRecognizer(tap)
        addSubview(scroll)
        termsScrollView = scroll
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard screen is TermsScreen else { return }
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
        state.scroll = min(max(0, scrollView.contentOffset.y / canvas.sy), maxScroll)
        setNeedsDisplay()
    }

    @objc private func handleTermsTap(_ gesture: UITapGestureRecognizer) {
        guard screen is TermsScreen else { return }
        let p = gesture.location(in: self)
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
        var design = canvas.design(p)
        design.y += state.scroll
        let token = screen.hitTest(state, x: design.x, y: design.y)
        onAction?(token, design)
    }

    @objc private func themeChanged() {
        setNeedsDisplay()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    /// 切换页面（保持同一份 View 实例，与 Android 复用 NativeView 的做法一致）
    func setScreen(_ next: Screen, state: ScreenState = ScreenState()) {
        screen = next
        self.state = state
        CrashLog.append("SCREEN \(type(of: next)) zhi=\(String(describing: next.zhiTabIndex)) bounds=\(bounds)")
        if let index = next.zhiTabIndex {
            zhiTabBar.isHidden = false
            zhiTabBar.selected = index
            let navH = bounds.height * 256 / 2816
            zhiTabBar.frame = CGRect(x: 0, y: bounds.height - navH,
                                     width: bounds.width, height: navH)
            bringSubviewToFront(zhiTabBar)
            CrashLog.append("ZHITAB \(type(of: next)) idx=\(index) frame=\(zhiTabBar.frame) bounds=\(bounds)")
        } else {
            zhiTabBar.isHidden = true
        }
        syncCameraBackground()
        updateTermsScrollView()
        setNeedsDisplay()
        // 状态栏图标明暗跟随当前页
        window?.rootViewController?.setNeedsStatusBarAppearanceUpdate()
    }

    /// 按当前屏的需要装上 / 卸下相机预览
    private func syncCameraBackground() {
        if screen.usesCameraBackground {
            guard cameraView == nil else { return }
            let camera = CameraPreviewView(frame: bounds)
            camera.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            insertSubview(camera, at: 0)
            cameraView = camera
        } else if let camera = cameraView {
            camera.stop()
            camera.removeFromSuperview()
            cameraView = nil
        }
    }

    /// 开始定时重绘（横幅轮播用；与 Android 的 bannerTick 心跳等价）
    func startRedrawTimer(interval: TimeInterval = 0.25) {
        redrawTimer?.invalidate()
        redrawTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: true) { [weak self] _ in
            self?.setNeedsDisplay()
        }
    }

    func stopRedrawTimer() {
        redrawTimer?.invalidate()
        redrawTimer = nil
    }

    // MARK: - 绘制

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)

        // 背景：与参考画布同尺寸的底色（页面自己会先铺一层，这里兜底防闪烁）
        // 相机页例外 —— 刷白底会把下面的相机预览盖住
        if !screen.usesCameraBackground {
            ctx.setFillColor(UIColor.white.cgColor)
            ctx.fill(bounds)
        }

        ctx.saveGState()
        Draw.flip(ctx, canvas: canvas)
        // 滚动：自己管滚动的页面（`selfScrolls == true`）内部已经做过 translate，
        // 这里只替那些不自己管的页面滚 —— 两处都做会变成双倍滚动。
        if !screen.selfScrolls {
            ctx.translateBy(x: 0, y: -state.scroll)
        }
        screen.draw(Draw(ctx: ctx, canvas: canvas), state: state)
        ctx.restoreGState()
    }

    // MARK: - 触摸（对照 Android 的 onTouchEvent：横向拖动轮播、纵向滚动、抬起时命中）

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in: self)
        touchStart = p
        lastPoint = p
        let beginCanvas = Canvas(size: screen.canvasSize, bounds: bounds)
        touchStartDesignY = beginCanvas.design(p).y + state.scroll
        dragStartY = p.y
        dragStartScroll = state.scroll
        dragging = false
        draggingCarousel = false
        verticalDragged = false
        moved = false
        edgeBackFired = false
        downAt = Date()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in: self)
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)

        // 左边缘右滑 → 返回。
        //
        // 为什么需要：iOS 侧页面是**同一个 View 里换 Screen**（不是 UINavigationController），
        // 所以没有系统自带的交互式返回手势；而 WebView 页、小支主页这类页面本身又不带返回热区，
        // 结果就是「进去出不来」。这里给所有页面补一个统一的边缘手势。
        if !edgeBackFired,
           touchStart.x <= 30,
           p.x - touchStart.x > 70,
           abs(p.y - touchStart.y) < 90 {
            edgeBackFired = true
            dragging = false
            draggingCarousel = false
            onAction?("back", CGPoint(x: 0, y: 0))
            return
        }
        // 《用户须知》由上面的 UIPanGestureRecognizer 负责滚动，避免两套位移叠加。
        if screen is TermsScreen {
            lastPoint = p
            return
        }

        let dxDesign = (p.x - lastPoint.x) / canvas.sx
        let totalDx = p.x - touchStart.x
        let totalDy = p.y - touchStart.y

        if abs(totalDx) > 6 || abs(totalDy) > 6 { moved = true }

        // 横向明显大于纵向 → 交给页面（轮播）。与 Android 的 1.2 倍阈值一致
        if !draggingCarousel, abs(totalDx) > abs(totalDy) * 1.2 {
            let consumed = screen.carouselDrag(state, dx: dxDesign)
            if consumed { draggingCarousel = true }
        }
        if draggingCarousel {
            _ = screen.carouselDrag(state, dx: dxDesign)
            lastPoint = p
            setNeedsDisplay()
            return
        }

        if abs(totalDy) > abs(totalDx),
           screen.verticalDrag(state, dy: (p.y - lastPoint.y) / canvas.sy,
                               startY: touchStartDesignY) {
            verticalDragged = true
            lastPoint = p
            // 面板收起/展开吃掉的这段纵向位移不要再算进页面滚动，避免收完面板后突然跳一段。
            dragStartY = p.y
            dragStartScroll = state.scroll
            setNeedsDisplay()
            return
        }

        let dy = p.y - dragStartY
        if abs(dy) > 8 { dragging = true }
        if dragging && maxScroll > 0 {
            // 屏幕位移 → 参考坐标位移（纵向用 sy）
            state.scroll = min(max(0, dragStartScroll - dy / canvas.sy), maxScroll)
            setNeedsDisplay()
        }
        lastPoint = p
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        defer { dragging = false; draggingCarousel = false; moved = false }
        guard let t = touches.first else { return }
        let p = t.location(in: self)
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
        // 屏幕点 → 参考坐标（x 用 sx、y 用 sy），再叠加当前滚动量。
        // 注意：本视图是全屏铺满的（SwiftUI 侧 ignoresSafeArea），所以**不再扣安全区**，
        // 否则命中坐标会整体上移，表现就是「点击没反应 / 点错位置」。
        var design = canvas.design(p)
        design.y += state.scroll

        if verticalDragged {
            verticalDragged = false
            screen.verticalDragEnd(state)
            setNeedsDisplay()
            return
        }

        // 横向拖动松手 → 吸附到最近一页（Android 的 snapCarousel）
        if draggingCarousel {
            screen.carouselSnap(state, totalDx: (p.x - touchStart.x) / canvas.sx)
            setNeedsDisplay()
            return
        }
        guard !dragging else { return }
        // 抬起位置与按下位置差太多说明是滑动，不当作点击
        guard abs(p.y - touchStart.y) < 8, abs(p.x - touchStart.x) < 8 else { return }

        // 没移动但按得久 → 长按（Android 的 LONG_PRESS_MS = 500ms）
        if let downAt, Date().timeIntervalSince(downAt) >= 0.5,
           let token = screen.longPress(state, x: design.x, y: design.y) {
            onAction?(token, design)
            return
        }
        let token = screen.hitTest(state, x: design.x, y: design.y)
        onAction?(token, design)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        dragging = false
    }
}
