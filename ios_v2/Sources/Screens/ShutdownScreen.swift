import UIKit
// `CACurrentMediaTime()`（闪烁与提示隐藏的时钟）来自 QuartzCore；UIKit 已经再导出，
// 这里显式写一行，避免 CI 上出现「cannot find 'CACurrentMediaTime' in scope」这类缺 import。
import QuartzCore

/// 「模拟手机没电关机」页 —— `features/shutdown/ShutdownActivity.java`（183 行）的 1:1 移植。
///
/// ## 关于参考画布（本屏唯一需要判断的地方）
///
/// Java 那边**没有设计基准**：这个 Activity 不是 NativeView 那一套「固定 W/H + canvas.scale」，
/// 它是纯原生 View 布局 ——
///   * 电池图标自己 `onDraw` 里用 `getWidth()/getHeight()`（Java:158-159）；
///   * 图标尺寸、提示字号、底部边距全是 dp/sp 常量（Java:78-79、93、99），由系统按屏幕密度换算；
///   * 根容器是 `FrameLayout` + `Gravity.CENTER` / `Gravity.BOTTOM`（Java:81、98）。
/// 也就是说它是「按屏幕实际尺寸画」的全屏页，没有 1280 这类基准可抄。
///
/// iOS 的 `Screen` 协议要求一个参考画布，所以这里按 **1280×2816**（≈ 9:19.8，与主流全面屏
/// 1080×2400@3x = 360×800dp 同比例）做等比映射，dp → 参考单位的系数取
/// `1280 / 360 ≈ 3.5556`（横向，因为宽度决定字号的观感；纵向 2816/800 = 3.52，差 1%，
/// 由 `Canvas.sy` 的拉伸铺满吸收 —— 与 Android `match_parent` 黑底的表现一致）。
/// 于是：150dp 图标 → 533.3 参考单位宽，13sp 提示 → 46.2，56dp 底边距 → 199.1。
///
/// ## 与 Android 的三处形态差异（都不是省略，而是宿主职责）
///   * `FLAG_KEEP_SCREEN_ON`（Java:70）→ iOS 由宿主置 `UIApplication.shared.isIdleTimerDisabled`；
///   * `hideSystemBars()`（Java:105、118-137）→ iOS 侧 `ScreenHostController` 已经
///     `prefersStatusBarHidden = true`（见 DoutuApp.swift:1001），本文件不重复处理；
///   * 电池的 `ObjectAnimator` 明暗闪烁（Java:84-89）→ 没有 View 可挂动画，改成
///     **按当前时间算 alpha**（`State.batteryAlpha(at:)`），由宿主的重绘心跳
///     （`ScreenHostView.startRedrawTimer()`，0.25s）推进。帧率因此是 4fps 而不是 60fps，
///     `BLINK_MS` 1500 一个来回下显得偏「跳」；要更顺需要宿主把 interval 调到 ~0.03，
///     但那是别的文件的改动，本屏不动。
///
/// 零图片素材：电池是画出来的（Java:139-181 的注释解释了为什么不是图片）。
final class ShutdownScreen: Screen {
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    let canvasSize = CGSize(width: 1280, height: 2816)

    /// Java 的 `draw` 里没有 `translate(0, -scroll)`（它根本没有滚动），
    /// 所以这里返回 false，避免宿主替它滚一次（本屏 maxScroll 恒为 0，双保险）。
    let selfScrolls = false

    // MARK: - 常量（Java:53-56）

    /// 底部那行提示显示多久（毫秒）—— Java:54 `HINT_MS = 2400`
    static let hintMs: Double = 2400
    /// 电池明暗一个来回的时长（毫秒）—— Java:56 `BLINK_MS = 1500`
    static let blinkMs: Double = 1500
    /// Java:84 `ObjectAnimator.ofFloat(battery, "alpha", 0.10f, 0.80f)`
    static let alphaMin: Double = 0.10
    static let alphaMax: Double = 0.80

    /// Java:92 `hint.setText("点一下屏幕回到豆图")`
    static let hintText = "点一下屏幕回到豆图"

    /// dp → 参考画布的换算系数（依据见文件头注释：1280 / 360dp）
    private let dp: CGFloat = 1280.0 / 360.0

    // MARK: - 几何（Java:78-79、91-100）

    /// Java:78-79 `150 * density` / `72 * density`
    private var iconW: CGFloat { 150 * dp }
    private var iconH: CGFloat { 72 * dp }
    /// Java:93 `hint.setTextSize(13f)`（sp ≈ dp）
    private var hintSize: CGFloat { 13 * dp }
    /// Java:99 `hintLp.bottomMargin = 56 * density`
    private var hintBottomMargin: CGFloat { 56 * dp }
    /// 基线到「行框底」的余量：Java 用的是 TextView（行高 ≈ 1.2 * 字号，descender ≈ 0.21），
    /// 这里取 0.35 倍字号作近似，让文字视觉重心落在 56dp 边距之上。
    private var hintBaselineInset: CGFloat { hintSize * 0.35 }

    // MARK: - 调色板（Java:71、74、150）

    /// Java:71 `ColorDrawable(Color.BLACK)` / Java:74 `root.setBackgroundColor(Color.BLACK)`
    private let pageBg = Draw.color(0xff000000)
    /// Java:150 `paint.setColor(Color.WHITE)`
    private let batteryInk = UIColor.white
    /// Java:94 `0xFF3A3A3A` —— 很暗：不影响「黑屏」的观感
    private let hintColor = Draw.color(0xff3a3a3a)

    // MARK: - 状态

    /// 页面状态：只记「什么时候出现的」，闪烁与提示隐藏都由这个时刻推算
    /// （对应 Java 里 `ObjectAnimator.start()`（Java:89）与 `postDelayed`（Java:107）的起点）。
    final class State: ScreenState {
        /// 本屏出现（= Java `onCreate` 走完）的时刻，`CACurrentMediaTime()` 基准
        var startedAt: CFTimeInterval

        init(startedAt: CFTimeInterval = CACurrentMediaTime()) {
            self.startedAt = startedAt
            super.init()
        }

        /// 出现后过了多久（秒）
        func elapsed(at now: CFTimeInterval) -> Double {
            max(0, now - startedAt)
        }

        /// 底部提示是否还该显示：Java:107 `root.postDelayed(() -> hint.setVisibility(GONE), HINT_MS)`
        func isHintVisible(at now: CFTimeInterval) -> Bool {
            elapsed(at: now) * 1000 < ShutdownScreen.hintMs
        }

        /// 电池当前 alpha。
        ///
        /// Java:84-89：`alpha 0.10 ↔ 0.80`、`BLINK_MS = 1500` 单程、`REVERSE`、`INFINITE`、
        /// `AccelerateDecelerateInterpolator` —— 等价于一条 3 秒周期的三角波再过一次
        /// `(cos((t+1)π) + 1) / 2` 缓动。
        func batteryAlpha(at now: CFTimeInterval) -> CGFloat {
            let legs = elapsed(at: now) / (ShutdownScreen.blinkMs / 1000)   // 每 1 秒走完一趟
            let tri = Self.triangle(legs)                                  // 0→1→0（REVERSE）
            let eased = (cos((tri + 1) * Double.pi) / 2) + 0.5              // 缓动
            let alpha = ShutdownScreen.alphaMin
                + (ShutdownScreen.alphaMax - ShutdownScreen.alphaMin) * eased
            return CGFloat(alpha)
        }

        /// 周期为 2 的三角波，取 0…1…0（`ValueAnimator.REVERSE` 的语义）
        private static func triangle(_ phase: Double) -> Double {
            let wrapped = phase.truncatingRemainder(dividingBy: 2)
            let p = wrapped < 0 ? wrapped + 2 : wrapped
            return p <= 1 ? p : 2 - p
        }
    }

    // MARK: - 滚动（Java 无滚动）

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:74、156-181）

    func draw(_ d: Draw, state: ScreenState) {
        let s = state as? State ?? State()

        // Java:74 根容器黑底（黑色不依赖任何素材，也不受安全区影响）
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        let now = CACurrentMediaTime()

        // Java:77-82 + 155-181 中间那颗空电池，慢慢明暗闪
        drawBattery(d, alpha: s.batteryAlpha(at: now))

        // Java:91-100 底部那行很暗的提示；Java:107 两秒多后收掉
        if s.isHintVisible(at: now) {
            d.centerText(Self.hintText, cx: Self.W / 2,
                         baseline: Self.H - hintBottomMargin - hintBaselineInset,
                         size: hintSize, hintColor)
        }
    }

    /// 没电的电池：空心圆角矩形（电池身）+ 右边一个小凸起（正极）。
    /// 坐标与比例逐行照抄 `BatteryIconView.onDraw`（Java:156-181）。
    private func drawBattery(_ d: Draw, alpha: CGFloat) {
        // Java:158-159 `float w = getWidth(); float h = getHeight();`
        let w = iconW
        let h = iconH
        // Java:81 `batteryLp.gravity = Gravity.CENTER` —— 在屏幕正中
        let originX = (Self.W - w) / 2
        let originY = (Self.H - h) / 2

        // Java:160 描边宽度
        let stroke = h * 0.10
        // Java:164-169 电池身与圆角
        let nubW = h * 0.16
        let bodyLeft = stroke * 0.5
        let bodyRight = w - nubW - h * 0.10
        let top = stroke * 0.5
        let bottom = h - stroke * 0.5
        let radius = min(h * 0.30, (bottom - top) * 0.38)

        // Java:84-89 闪烁的 alpha 压到描边色上（Java 是给整个 View 设 alpha）
        let ink = batteryInk.withAlphaComponent(alpha)

        // Java:171-172 `paint.setStyle(STROKE); canvas.drawRoundRect(...)` —— 空的就是「没电」
        let body = CGRect(x: originX + bodyLeft, y: originY + top,
                          width: bodyRight - bodyLeft, height: bottom - top)
        d.roundStroke(body, radius: radius, ink, width: stroke)

        // Java:174-180 正极：右边那个小圆角块，竖直居中，实心
        let nubTop = h * 0.34
        let nubBottom = h * 0.66
        let nubRadius = nubW * 0.45
        let nub = CGRect(x: originX + bodyRight + h * 0.05, y: originY + nubTop,
                         width: nubW - h * 0.05, height: nubBottom - nubTop)
        d.round(nub, radius: nubRadius, ink)
    }

    // MARK: - 命中（Java:102）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // Java:102 `root.setOnClickListener(v -> finish())` —— 命中区是整个根容器（全屏），
        // 点哪儿都退出；底部那行提示也在根容器里、没有自己的点击监听，所以点它同样是退出。
        // 按约定返回 "back"。
        return "back"
    }
}
