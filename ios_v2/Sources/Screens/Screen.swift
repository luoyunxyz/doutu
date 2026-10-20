import UIKit
// CADisplayLink / CACurrentMediaTime 在 QuartzCore 里。UIKit 虽然会再导出，
// 但 CI 上曾出现过「cannot find ... in scope」，按 ShutdownScreen 的做法显式写一行。
import QuartzCore

/// 页面动作令牌 —— 与 Android 端 `hitTest` 返回的字符串一一对应。
///
/// Android 的写法是返回 `"nav:2"`、`"profile"`、`"bannerClose"` 这样的字符串常量，
/// iOS 这边保留同样的词表（不改成枚举），这样逐屏比对源码时可以直接对照，
/// 也避免了「Java 加了新动作、Swift 忘了加 case」这种漏改。
typealias ActionToken = String

/// 触觉反馈。
///
/// Android 端没有这一层（只有震动权限下的少数场景），但 iOS 用户对「点下去有没有反应」
/// 非常敏感：同样的静态界面，带一次轻微 Taptic 点击就会显得「活」很多。
/// 这里只给**有明确状态变化**的动作配触觉（切页、长按、返回、支付成功），
/// 不给普通列表点击配，避免整机一直在震。
enum Haptics {
    /// 轻选（切页签 / 切底栏）
    static func selection() {
        let generator = UISelectionFeedbackGenerator()
        generator.selectionChanged()
    }

    /// 撞击（返回、长按）
    static func impact(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .light) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.impactOccurred()
    }

    /// 成功（支付 / 提现完成）
    static func success() {
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)
    }
}

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
    /// 长按。必须写在 protocol 里，否则通过 `Screen` 存在类型调用时永远拿到扩展默认的 nil。
    func longPress(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken?
    /// 是否在页面底下垫相机预览（扫码页）。同上。
    var usesCameraBackground: Bool { get }
    /// 这一屏的 draw 是否自己做了 `translate(0, -scroll)`。同上。
    var selfScrolls: Bool { get }
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

    private let labels = ["首页", "理财", "视频", "消息", "我的"]

    private func drawLabel(_ text: String, cx: CGFloat, h: CGFloat,
                           baseline: CGFloat = 146, color: UIColor) {
        let font = UIFont.systemFont(ofSize: max(8, h * 26 / 256))
        let ns = text as NSString
        let tw = ns.size(withAttributes: [.font: font]).width
        ns.draw(at: CGPoint(x: cx - tw / 2, y: h * baseline / 256 - font.lineHeight),
                withAttributes: [.font: font, .foregroundColor: color])
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

        let theme = LicaiVersion.theme()
        if theme.value != LicaiVersion.normal {
            drawBlackThemeNav(h: h, theme: theme)
            return
        }
        let names = ["lci_nav_home", "lci_nav_licai", "lci_nav_video", "lci_nav_msg", "lci_nav_me"]
        let normal = true
        let labelColor = tint
        for (i, refX) in centers.enumerated() {
            let cx = w * refX / 1280
            if i == 0 {
                if selected == 0 {
                    let side = h * 70 / 256
                    let iconColor = normal ? tint : Draw.color(theme.foreground)
                    UIImage(named: "lci_nav_zhi")?.withTintColor(iconColor, renderingMode: .alwaysOriginal)
                        .draw(in: CGRect(x: cx - side / 2, y: h * 16 / 256, width: side, height: side))
                    drawLabel("首页", cx: cx, h: h, color: labelColor)
                } else {
                    // lci_nav_home 是 225×256 的宽图（含图标+首页文字），不能按其它格的
                    // 86×148 拉伸，否则首页图标会被压扁变形。
                    let iw = w * 225 / 1280
                    UIImage(named: "lci_nav_home")?
                        .draw(in: CGRect(x: cx - iw / 2, y: 0, width: iw, height: h))
                }
                continue
            }
            var image = UIImage(named: names[i])
            if i == selected && normal {
                image = image?.withTintColor(tint, renderingMode: .alwaysOriginal)
            }
            let iw = w * 86 / 1280
            let ih = h * 148 / 256
            let imageY = h * 44 / 256
            image?.draw(in: CGRect(x: cx - iw / 2, y: imageY, width: iw, height: ih))
            // 黑卡主题的选中态：图标保持素材里的深色实心，只把文字染成主题金色。
            if i == selected && !normal {
                UIColor.white.setFill()
                UIRectFill(CGRect(x: cx - iw / 2, y: imageY + ih * 0.58,
                                  width: iw, height: ih * 0.42))
                drawLabel(labels[i], cx: cx, h: h, baseline: 184, color: labelColor)
            }
        }
        UIColor(white: 0.78, alpha: 1).setFill()
        UIBezierPath(roundedRect: CGRect(x: w * 430 / 1280, y: h * 222 / 256,
                                         width: w * 420 / 1280, height: max(2, h * 8 / 256)),
                     cornerRadius: 2).fill()
    }

    /// 黑卡主题底栏：Android 用的是 hk_nav_* 的深色实心图标 + 金色选中文字。
    private func drawBlackThemeNav(h: CGFloat, theme: LicaiVersion.Theme) {
        let w = bounds.width
        let icons = ["hk_nav_index", "hk_nav_fortune", "hk_nav_video", "hk_nav_message", "hk_nav_me"]
        let dark = Draw.color(0xff2a3550)
        let accent = Draw.color(theme.accent)
        let size = h * 90 / 256
        let iconY = h * 16 / 256
        for (i, refX) in centers.enumerated() {
            let cx = w * refX / 1280
            let name = icons[i] + (selected == i ? "_active" : "")
            UIImage(named: name)?.draw(in: CGRect(x: cx - size / 2, y: iconY,
                                                  width: size, height: size))
            drawLabel(labels[i], cx: cx, h: h, baseline: 190,
                      color: selected == i ? accent : dark)
        }
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
        // 底栏切换需要「按下去有回应」的感觉，Android 端靠水波纹，iOS 这里用一次轻选。
        Haptics.selection()
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
    /// 左边缘交互式返回
    var canGoBack = false
    /// 上一页的快照，由 Router 在 push 前抓取
    var previousSnapshot: UIView?
    private var edgePreviousContainer: UIView?
    private var edgeProgress: CGFloat = 0
    /// 本次边缘手势是否已经「提交返回」——用于兜底复位，避免误伤下一次手势
    private var edgeCommitted = false
    /// 正在做 push/pop 转场的那张「旧页面」快照
    private weak var transitionSnapshot: UIView?
    /// 扫码页底下那层相机预览
    private var cameraView: CameraPreviewView?

    // MARK: - 滚动惯性 / 边缘回弹
    //
    // Android 的 NativeView 是「手指抬起来就停」，iOS 用户对这一点极其敏感：
    // 同样一屏账单，滑一下不走、得一直贴着手指，会立刻觉得「卡、廉价」。
    // 这里用 CADisplayLink 做两层位移：
    //   · 惯性滑行：速度按 UIScrollView 的 0.998/ms 衰减；
    //   · 回弹：越界后按 iOS 的橡皮筋公式收敛回边界。
    private var scrollLink: CADisplayLink?
    /// 参考画布单位 / 秒（正数表示内容继续向上走）
    private var scrollVelocity: CGFloat = 0
    /// 回弹目标；nil 表示正在惯性滑行
    private var scrollTarget: CGFloat?
    private var lastFrameAt: CFTimeInterval = 0
    /// 手指最后一次移动的时间 / 位置（UITouch.timestamp 与 CACurrentMediaTime 同一时钟）
    private var lastMoveAt: CFTimeInterval = 0
    private var lastMoveY: CGFloat = 0

    // MARK: - 长按
    private var longPressTimer: Timer?
    private var longPressFired = false

    /// 定时重绘的「空闲」间隔；滚动动画与触摸期间由 CADisplayLink 自己驱动，不依赖它。
    private var slowRedrawInterval: TimeInterval = 0.25
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
        // 系统级左边缘右滑返回：比 touchesMoved 可靠，子视图（UIScrollView/WKWebView）也能触发。
        let edgePan = UIScreenEdgePanGestureRecognizer(target: self,
                                                        action: #selector(handleEdgePan(_:)))
        edgePan.edges = .left
        edgePan.cancelsTouchesInView = true
        addGestureRecognizer(edgePan)
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
        // 转场快照必须始终盖在最上面，否则 layout 一跑，新页面的底栏就会从快照里穿出来。
        if let transitionSnapshot {
            bringSubviewToFront(transitionSnapshot)
        } else {
            bringSubviewToFront(zhiTabBar)
        }
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
        scrollLink?.invalidate()
        redrawTimer?.invalidate()
        longPressTimer?.invalidate()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    /// 切换页面（保持同一份 View 实例，与 Android 复用 NativeView 的做法一致）。
    ///
    /// `animated` 只负责「旧页面怎么离场」：新页面先画好，再把旧页面的快照朝左（push）
    /// 或朝右（pop）推出屏幕。这样既有方向感，又不用引入 UINavigationController，
    /// 也不会破坏 27 屏共用一个宿主 View 的结构。
    func setScreen(_ next: Screen, state: ScreenState = ScreenState(),
                   animated: Bool = false, backwards: Bool = false) {
        // 切页前收拾上一屏的临时状态：惯性、长按计时、边缘返回位移。
        // 边缘返回时页面已经被手指推到屏幕外，这里的 reset 与新页面绘制在同一个
        // runloop 里完成，中间不会露出旧页面（之前的「返回闪一下」就是这么来的）。
        stopScrollAnimation()
        longPressTimer?.invalidate()
        longPressTimer = nil
        downAt = nil
        // 注意：不清 `longPressFired`。长按可能触发跳页，抬手事件会落到新页面上；
        // 留着这个标记，才能让那一次抬手不被误当成点击。
        resetEdgeBack()
        // 连续快速 push/pop 时，上一段转场的截图不能留着抢层级。
        transitionSnapshot?.removeFromSuperview()
        transitionSnapshot = nil

        // 先抓旧页面快照，再换 screen —— 顺序反了就会拍到新页面。
        let snapshot = animated ? makeTransitionSnapshot() : nil

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

        guard let snapshot else { return }
        transitionSnapshot = snapshot
        addSubview(snapshot)
        bringSubviewToFront(snapshot)
        let width = max(bounds.width, 1)
        snapshot.layer.shadowColor = UIColor.black.cgColor
        snapshot.layer.shadowOpacity = 0.16
        snapshot.layer.shadowRadius = 10
        // 阴影要落在「即将露出的那一页」上：push 时旧页向左走 → 阴影在右（+4），
        // pop 时旧页向右走 → 阴影在左（-4）。
        snapshot.layer.shadowOffset = CGSize(width: backwards ? -4 : 4, height: 0)
        snapshot.layer.shadowPath = UIBezierPath(rect: snapshot.bounds).cgPath
        UIView.animate(withDuration: 0.3, delay: 0, options: [.curveEaseInOut]) {
            snapshot.transform = CGAffineTransform(translationX: backwards ? width : -width, y: 0)
        } completion: { [weak self] _ in
            snapshot.removeFromSuperview()
            if let self, self.transitionSnapshot === snapshot {
                self.transitionSnapshot = nil
            }
        }
    }

    private func makeTransitionSnapshot() -> UIView? {
        guard window != nil, bounds.width > 1 else { return nil }
        guard let snapshot = snapshotView(afterScreenUpdates: false) else { return nil }
        snapshot.frame = bounds
        // 留在最上层挡住 0.3s 转场期间的点击：否则下层新页面会先收到一次
        // 「用户以为点在旧页上」的命中，容易出现连跳两页。
        snapshot.isUserInteractionEnabled = true
        return snapshot
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

    /// 开始定时重绘（横幅轮播 / 关机页闪烁用；与 Android 的 bannerTick 心跳等价）。
    ///
    /// 原来固定 0.25s（4fps），横幅那 420ms 的滚动只有两帧、关机页电池也在跳。
    /// 现在改成「一次一帧、按当前屏需要决定下一帧间隔」：
    ///   · 关机页：30fps（电池明暗）
    ///   · 首页且横幅正在滚动：30fps
    ///   · 其它：保持原来的 0.25s，不为静态页面白烧电。
    func startRedrawTimer(interval: TimeInterval = 0.25) {
        slowRedrawInterval = interval
        scheduleRedrawTick()
    }

    private func scheduleRedrawTick() {
        redrawTimer?.invalidate()
        redrawTimer = Timer.scheduledTimer(withTimeInterval: currentRedrawInterval(),
                                           repeats: false) { [weak self] _ in
            guard let self else { return }
            self.setNeedsDisplay()
            self.scheduleRedrawTick()
        }
    }

    private func currentRedrawInterval() -> TimeInterval {
        // 关机页只有黑底 + 一个电池，绘制很轻，直接跟满 60fps 才不「跳」。
        if screen is ShutdownScreen { return 1.0 / 60.0 }
        if screen is HomeShellScreen || screen is HomeV2Screen {
            let banner = BannerStore.shared
            if banner.hintFrame().rolling { return 1.0 / 30.0 }
            // 空闲时也不睡满 0.25s：把下一帧对齐到 420ms 滚动窗口的起点。
            return min(slowRedrawInterval, max(0.02, banner.secondsUntilRoll()))
        }
        return slowRedrawInterval
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

    @objc private func handleEdgePan(_ gesture: UIScreenEdgePanGestureRecognizer) {
        switch gesture.state {
        case .began:
            beginEdgeBack(gesture)
        case .changed:
            updateEdgeBack(gesture)
        case .ended, .cancelled:
            finishEdgeBack(gesture)
        case .failed:
            // 被别的系统手势抢走时，别把页面留在半位移状态
            resetEdgeBack()
        default:
            break
        }
    }

    private func beginEdgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard canGoBack else { return }
        edgeProgress = 0
        edgeCommitted = false
        if let snapshot = previousSnapshot {
            let container = UIView(frame: bounds)
            container.backgroundColor = .black
            snapshot.frame = container.bounds
            snapshot.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            container.addSubview(snapshot)
            // 上一页先往左退 30%，跟手时再回到 0 —— iOS 原生返回的「视差」，
            // 比两层一起平移更有纵深，也不会让上一页看起来是贴上去的。
            container.transform = CGAffineTransform(translationX: -bounds.width * 0.3, y: 0)
            superview?.insertSubview(container, belowSubview: self)
            edgePreviousContainer = container
        }
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.18
        layer.shadowRadius = 10
        layer.shadowOffset = CGSize(width: -3, height: 0)
        // 全屏 layer 的阴影如果按 alpha 通道逐帧计算，必然掉帧；给个矩形路径。
        layer.shadowPath = UIBezierPath(rect: bounds).cgPath
    }

    private func updateEdgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard canGoBack else { return }
        let width = max(bounds.width, 1)
        let distance = max(0, min(width, gesture.translation(in: superview).x))
        edgeProgress = distance / width
        transform = CGAffineTransform(translationX: distance, y: 0)
        edgePreviousContainer?.transform =
            CGAffineTransform(translationX: -width * 0.3 * (1 - edgeProgress), y: 0)
    }

    private func finishEdgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard canGoBack else { resetEdgeBack(); return }
        let width = max(bounds.width, 1)
        // 用 superview 的速度：self 正在跟手位移，拿 self 当坐标系会把速度抵消掉。
        let velocity = gesture.velocity(in: superview ?? self).x
        // `.cancelled` 绝不能当成「返回」：系统手势/来电打断时用户并没有完成操作。
        let shouldComplete = gesture.state != .cancelled
            && (edgeProgress > 0.33 || velocity > 650)
        let duration = shouldComplete ? 0.22 : 0.28

        if shouldComplete {
            UIView.animate(withDuration: duration, delay: 0,
                           options: [.curveEaseOut]) {
                self.transform = CGAffineTransform(translationX: width, y: 0)
                self.edgePreviousContainer?.transform = .identity
            } completion: { [weak self] _ in
                guard let self else { return }
                // 注意顺序：先发令牌，让 Router 在同一轮 runloop 里 pop + 重绘，
                // 再由 setScreen 收掉位移。若先 reset 画面会闪回旧页一帧。
                Haptics.impact(.light)
                self.edgeCommitted = true
                self.onAction?("edgeback", .zero)
                // 兜底：万一上层没有消费这个令牌，别把宿主永远留在屏幕外。
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { [weak self] in
                    guard let self, self.edgeCommitted else { return }
                    self.resetEdgeBack()
                }
            }
        } else {
            UIView.animate(withDuration: duration, delay: 0,
                           usingSpringWithDamping: 0.9,
                           initialSpringVelocity: 0.6,
                           options: [.curveEaseOut]) {
                self.transform = .identity
            } completion: { _ in
                self.resetEdgeBack()
            }
        }
    }

    private func resetEdgeBack() {
        transform = .identity
        layer.shadowOpacity = 0
        edgePreviousContainer?.removeFromSuperview()
        edgePreviousContainer = nil
        edgeProgress = 0
        edgeCommitted = false
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        // 手指一落下就停住惯性（与 UIScrollView 一致），否则想点一下会先被带着滑一段。
        stopScrollAnimation()
        // 如果正停在橡皮筋的越界位置，先把内容收回边界，否则这一次点击的
        // 参考坐标会整体偏移，命中到错误的元素。
        if state.scroll < 0 || state.scroll > maxScroll {
            state.scroll = min(max(0, state.scroll), maxScroll)
            setNeedsDisplay()
        }
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
        longPressFired = false
        lastMoveY = p.y
        lastMoveAt = 0
        downAt = Date()
        scheduleLongPress()
    }

    /// 长按在**按住 500ms 时**触发（Android 的 `LONG_PRESS_MS`）。
    ///
    /// 之前是「抬手时看按了多久」：用户必须按住 → 再松手才出菜单，而且松手那一下
    /// 还同时算点击，手感完全是反的。现在按住不动 500ms 立即触发，并给一次中等 Taptic。
    private func scheduleLongPress() {
        longPressTimer?.invalidate()
        longPressTimer = nil
        guard !(screen is TermsScreen) else { return }
        longPressTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: false) { [weak self] _ in
            guard let self, self.downAt != nil, !self.moved, !self.longPressFired else { return }
            let canvas = Canvas(size: self.screen.canvasSize, bounds: self.bounds)
            let start = canvas.design(self.touchStart)
            let x = start.x
            let y = start.y + self.state.scroll
            guard let token = self.screen.longPress(self.state, x: x, y: y) else { return }
            self.longPressFired = true
            Haptics.impact(.medium)
            self.onAction?(token, CGPoint(x: x, y: y))
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let t = touches.first else { return }
        // 长按已经触发：这次手势后面全部交给菜单，不再滚动/命中
        if longPressFired { return }
        let p = t.location(in: self)
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)

        // 左边缘右滑返回由上面的 UIScreenEdgePanGestureRecognizer 统一处理。
        // 《用户须知》由上面的 UIPanGestureRecognizer 负责滚动，避免两套位移叠加。
        if screen is TermsScreen {
            lastPoint = p
            return
        }

        let dxDesign = (p.x - lastPoint.x) / canvas.sx
        let totalDx = p.x - touchStart.x
        let totalDy = p.y - touchStart.y

        if abs(totalDx) > 6 || abs(totalDy) > 6 {
            moved = true
            // 一动就不再是长按
            longPressTimer?.invalidate()
            longPressTimer = nil
        }

        // 横向明显大于纵向 → 交给页面（轮播）。与 Android 的 1.2 倍阈值一致
        if !draggingCarousel, abs(totalDx) > abs(totalDy) * 1.2 {
            // 第一次调用既做「这一屏要不要接管」的判断，也已经在页面里应用了 dx。
            // 之前这里再调一次会把首帧位移算两遍，横向拖动会突然跳一下。
            if screen.carouselDrag(state, dx: dxDesign) {
                draggingCarousel = true
                lastPoint = p
                lastMoveAt = 0
                setNeedsDisplay()
                return
            }
        }
        if draggingCarousel {
            _ = screen.carouselDrag(state, dx: dxDesign)
            lastPoint = p
            lastMoveAt = 0
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
            lastMoveAt = 0
            setNeedsDisplay()
            return
        }

        // 采样手指速度（用 UITouch.timestamp，与 CACurrentMediaTime 同一时钟）。
        // 抬手后用它做惯性；指数平滑一下，避免最后一帧抖动把页面甩飞。
        let now = t.timestamp
        if lastMoveAt > 0, now > lastMoveAt {
            let dt = now - lastMoveAt
            let instant = -(p.y - lastMoveY) / CGFloat(dt) / canvas.sy
            scrollVelocity = scrollVelocity * 0.72 + instant * 0.28
        }
        lastMoveY = p.y
        lastMoveAt = now

        let dy = p.y - dragStartY
        if abs(dy) > 8 { dragging = true }
        if dragging && maxScroll > 0 {
            // 屏幕位移 → 参考坐标位移（纵向用 sy）；越界走橡皮筋，松手再回弹。
            let raw = dragStartScroll - dy / canvas.sy
            state.scroll = rubberBand(raw, upper: maxScroll)
            setNeedsDisplay()
        }
        lastPoint = p
    }

    /// iOS 的橡皮筋公式：越界位移 `x` 只映射成 `x·d·c/(x+d·c)`，永远拉不到 `d·c` 之外。
    ///
    /// 渐近上限压到约 32pt：页面背景是整块画布画出来的，越界时露出的其实是宿主底色；
    /// 如果允许拉出小半屏，深色页面（消息、财富、芝麻）顶部会露出一条很大的白边。
    /// 32pt 的弹性已经能明显感觉到「拉得住」，又不会把配色问题放大。
    private func rubberBand(_ value: CGFloat, upper: CGFloat) -> CGFloat {
        let canvas = Canvas(size: screen.canvasSize, bounds: bounds)
        let visible = max(1, bounds.height / max(0.0001, canvas.sy))
        let limit = min(visible * 0.55, 32 / max(0.0001, canvas.sy))
        if value < 0 {
            let over = -value
            return -over * limit / (over + limit)
        }
        if value > upper {
            let over = value - upper
            return upper + over * limit / (over + limit)
        }
        return value
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressTimer?.invalidate()
        longPressTimer = nil
        downAt = nil
        defer { dragging = false; draggingCarousel = false; moved = false }
        guard let t = touches.first else { return }
        if longPressFired {
            longPressFired = false
            return
        }
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
        // 纵向拖动松手 → 带速度继续滑（惯性），越界则回弹
        if dragging {
            finishDragWithInertia()
            return
        }
        // 抬起位置与按下位置差太多说明是滑动，不当作点击
        guard abs(p.y - touchStart.y) < 8, abs(p.x - touchStart.x) < 8 else { return }

        let token = screen.hitTest(state, x: design.x, y: design.y)
        onAction?(token, design)
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        longPressTimer?.invalidate()
        longPressTimer = nil
        downAt = nil
        dragging = false
    }

    // MARK: - 滚动惯性 / 回弹

    private func finishDragWithInertia() {
        guard maxScroll > 0 else { stopScrollAnimation(); return }
        // 停手超过 120ms 就不给惯性：否则「手指早就停了、页面自己又滑一段」会很怪。
        let idle = CACurrentMediaTime() - lastMoveAt
        var velocity = idle < 0.12 ? scrollVelocity : 0
        if abs(velocity) < 40 { velocity = 0 }

        if state.scroll < 0 {
            startSettle(to: 0)
        } else if state.scroll > maxScroll {
            startSettle(to: maxScroll)
        } else if velocity != 0 {
            scrollVelocity = velocity
            scrollTarget = nil
            startScrollLink()
        }
    }

    private func startSettle(to target: CGFloat) {
        scrollTarget = target
        scrollVelocity = 0
        startScrollLink()
    }

    private func startScrollLink() {
        if scrollLink == nil {
            let link = CADisplayLink(target: self, selector: #selector(stepScroll(_:)))
            link.add(to: .main, forMode: .common)
            scrollLink = link
        }
        lastFrameAt = 0
    }

    @objc private func stepScroll(_ link: CADisplayLink) {
        let now = link.timestamp
        let dt = lastFrameAt == 0 ? link.duration
            : min(0.05, max(1.0 / 240.0, now - lastFrameAt))
        lastFrameAt = now

        if let target = scrollTarget {
            let delta = target - state.scroll
            if abs(delta) <= 0.5 {
                state.scroll = target
                stopScrollAnimation()
                return
            }
            // 越界回弹：指数收敛，接近临界阻尼，不会来回弹。
            state.scroll += delta * CGFloat(min(1, dt * 20))
        } else {
            state.scroll += scrollVelocity * CGFloat(dt)
            // UIScrollView 的 normal 减速：每毫秒 0.998
            scrollVelocity *= CGFloat(pow(0.998, dt * 1000))
            if state.scroll <= 0 {
                state.scroll = 0
                startSettle(to: 0)
            } else if state.scroll >= maxScroll {
                state.scroll = maxScroll
                startSettle(to: maxScroll)
            } else if abs(scrollVelocity) < 12 {
                stopScrollAnimation()
                return
            }
        }
        setNeedsDisplay()
    }

    private func stopScrollAnimation() {
        scrollLink?.invalidate()
        scrollLink = nil
        scrollTarget = nil
        scrollVelocity = 0
        lastFrameAt = 0
    }
}
