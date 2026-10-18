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
}

/// 页面可变状态基类（滚动偏移等）。子类按需扩展。
class ScreenState {
    var scroll: CGFloat = 0
}

/// 承载页面的 UIView：负责缩放、滚动、触摸与重绘。
///
/// 对应 Android 的 `NativeView`（自定义 View + onDraw + onTouchEvent）。
final class ScreenHostView: UIView {
    private(set) var screen: Screen
    var state = ScreenState()
    /// 命中后回调动作令牌，由上层决定跳转/弹窗
    var onAction: ((ActionToken, CGPoint) -> Void)?
    /// 定时重绘（首页横幅的轮播要靠它推进）
    private var redrawTimer: Timer?

    /// 最大滚动距离（参考画布坐标）
    private var maxScroll: CGFloat { max(0, screen.maxScroll(state)) }

    private var dragStartY: CGFloat = 0
    private var dragStartScroll: CGFloat = 0
    private var dragging = false
    private var touchStart = CGPoint.zero
    private var lastPoint = CGPoint.zero
    /// 横向拖动（轮播）是否已接管这次手势
    private var draggingCarousel = false
    /// 手指是否移动过（长按要求「没移动」）
    private var moved = false
    /// 按下时刻（长按判定用）
    private var downAt: Date?
    /// 扫码页底下那层相机预览
    private var cameraView: CameraPreviewView?

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
        syncCameraBackground()
        setNeedsDisplay()
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
        dragStartY = p.y
        dragStartScroll = state.scroll
        dragging = false
        draggingCarousel = false
        moved = false
        downAt = Date()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        let p = t.location(in: self)
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
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
