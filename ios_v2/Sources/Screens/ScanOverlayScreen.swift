import UIKit

/// 扫码页的「覆盖层」—— `features/scan/ui/ScanOverlayScreen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 这一屏是**画在相机预览之上的浮层**：扫描线、顶部返回/更多、提示语、左右两个圆形按钮、
/// 底部黑色页签条。相机预览本身不在这个类里（Android 用 CameraX，iOS 对应 AVCaptureSession），
/// 所以 iOS 侧这里先只移植浮层；将来把真实预览当背景垫在下面即可。
///
/// **扫描线是程序生成的**（`ensureGlow`）：256×102 的 ARGB 位图，
/// 上半 96 行是自尾向首渐强的蓝色拖尾，紧接着 6 行是更亮的「刃口」，
/// 横向套一个 Hann 窗（中间满、两侧淡到 0），然后整张按帧贴到 y 位置。
/// 位置用 smoothstep 缓动在 250→2010 之间来回走，周期 2600ms —— 与 Android 同一个算法。
final class ScanOverlayScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    /// 底部黑条上沿
    static let barTop: CGFloat = 2510

    final class State: ScreenState {
        /// 0 = 扫码，1 = 灵光
        var tab: Int = 0
    }

    private let scanTop: CGFloat = 250
    private let scanBottom: CGFloat = 2010
    /// 拖尾高度
    private let glowH: CGFloat = 114
    /// 刃口高度
    private let coreH: CGFloat = 5
    private let periodMs: Double = 2600

    private let scanBlue = Draw.color(0xff3c78f7)
    private let tabIdle = Draw.color(0xff8c8c8c)
    private let buttonScrim = Draw.color(0x5c000000)

    private let tabCx: [CGFloat] = [422, 853]
    private let tabLabels = ["扫码", "灵光"]
    private let buttonR: CGFloat = 84
    private let buttonCx: [CGFloat] = [165, 1108]
    private let buttonCy: CGFloat = 2283
    private let payRect = CGRect(x: 95, y: 2200, width: 235 - 95, height: 2360 - 2200)
    private let albumRect = CGRect(x: 1045, y: 2200, width: 1175 - 1045, height: 2360 - 2200)

    private var pay: UIImage?
    private var album: UIImage?
    /// 生成一次的扫描线位图
    private var glow: UIImage?

    /// 扫描线进度用一个单调时钟算，保证动画连续（与 Android 的 `State.clock` 同义）
    private let startTime = Date()

    init() {
        pay = UIImage(named: "scan_pay")
        album = UIImage(named: "scan_album")
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:69-75）

    /// 画面底下要垫相机预览（Android 是 `TextureView` + 这一层浮层）
    var usesCameraBackground: Bool { true }

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        // 本页原本靠相机预览当底；相机图层的层级还没理顺（相机 subview 会盖住页面绘制），
        // 所以先铺黑底，让白色返回箭头/扫描线/按钮在无相机时也看得见。
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .black)
        drawScanLine(d)
        drawTopBar(d)
        d.centerText("扫二维码/条码/小程序码", cx: 640, baseline: 2069, size: 49, UIColor.white)
        drawButtons(d)
        drawBottomBar(d, s)
    }

    /// 蓝色扫描线（Java:81-89）：smoothstep 缓动，循环往复
    private func drawScanLine(_ d: Draw) {
        guard let glow = glowImage() else { return }
        let elapsed = Date().timeIntervalSince(startTime) * 1000
        let t = elapsed.truncatingRemainder(dividingBy: periodMs) / periodMs
        let eased = t * t * (3 - 2 * t)
        let y = scanTop + (scanBottom - scanTop) * CGFloat(eased)
        d.art(glow, CGRect(x: 0, y: y - glowH - coreH, width: Self.W, height: glowH + coreH))
    }

    /// 生成扫描线位图：256×102（Java:95-127）
    private func glowImage() -> UIImage? {
        if let cached = glow { return cached }
        let w = 256
        let trail = 96
        let core = 6
        let h = trail + core
        var pixels = [UInt8](repeating: 0, count: w * h * 4)

        // 蓝色拖尾 / 亮刃口的 RGB（忽略 alpha）
        let trailR: CGFloat = 0x3c / 255, trailG: CGFloat = 0x78 / 255, trailB: CGFloat = 0xf7 / 255
        let coreR: CGFloat = 0xe6 / 255, coreG: CGFloat = 0xef / 255, coreB: CGFloat = 0xff / 255

        for y in 0..<h {
            for x in 0..<w {
                let dd = abs((Double(x) + 0.5) / Double(w) - 0.5) * 2
                // Hann 窗：中间满，超过约 0.85 宽度后淡到 0
                let horiz = 0.5 * (1 + cos(Double.pi * min(1, dd)))
                var alpha: Double
                var r: CGFloat, g: CGFloat, b: CGFloat
                if y < trail {
                    let v = (Double(y) + 1) / Double(trail)
                    alpha = 158 * v * horiz
                    r = trailR; g = trailG; b = trailB
                } else {
                    let v = 1 - abs((Double(y) - Double(trail) + 0.5) / Double(core) - 0.5) * 2
                    alpha = 62 * v * horiz
                    r = coreR; g = coreG; b = coreB
                }
                // CoreGraphics 位图是预乘 alpha 的，这里按预乘写入
                let a = alpha / 255
                let i = (y * w + x) * 4
                pixels[i] = UInt8(max(0, min(255, r * 255 * a)))
                pixels[i + 1] = UInt8(max(0, min(255, g * 255 * a)))
                pixels[i + 2] = UInt8(max(0, min(255, b * 255 * a)))
                pixels[i + 3] = UInt8(max(0, min(255, alpha)))
            }
        }

        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cg = CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 32,
                               bytesPerRow: w * 4,
                               space: CGColorSpaceCreateDeviceRGB(),
                               bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                               provider: provider, decode: nil, shouldInterpolate: true,
                               intent: .defaultIntent) else { return nil }
        let image = UIImage(cgImage: cg)
        glow = image
        return image
    }

    /// 顶部返回箭头 + 右侧三个点（Java:129-137）
    private func drawTopBar(_ d: Draw) {
        strokeLine(d, x1: 78, y1: 188, x2: 44, y2: 219, color: .white, width: 7)
        strokeLine(d, x1: 44, y1: 219, x2: 78, y2: 250, color: .white, width: 7)
        for i in 0..<3 {
            d.circle(cx: 1203 + CGFloat(i) * 16, cy: 217, r: 6.5, UIColor.white)
        }
    }

    /// 左右两个圆形底衬 + 按钮切图（Java:143-151）
    private func drawButtons(_ d: Draw) {
        d.circle(cx: buttonCx[0], cy: buttonCy, r: buttonR, buttonScrim)
        d.circle(cx: buttonCx[1], cy: buttonCy, r: buttonR, buttonScrim)
        if let pay { d.art(pay, payRect) }
        if let album { d.art(album, albumRect) }
    }

    /// 底部黑色页签条（Java:153-170）
    private func drawBottomBar(_ d: Draw, _ s: State) {
        d.rect(CGRect(x: 0, y: Self.barTop, width: Self.W, height: Self.H - Self.barTop), .black)
        for (i, label) in tabLabels.enumerated() {
            let active = i == s.tab
            d.centerText(label, cx: tabCx[i], baseline: 2640, size: 52,
                         active ? scanBlue : tabIdle)
            if active {
                d.round(CGRect(x: tabCx[i] - 39, y: 2674, width: 78, height: 11),
                        radius: 6, scanBlue)
            }
        }
    }

    private func strokeLine(_ d: Draw, x1: CGFloat, y1: CGFloat, x2: CGFloat, y2: CGFloat,
                            color: UIColor, width: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x1, y: y1))
        d.ctx.addLine(to: CGPoint(x: x2, y: y2))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    // MARK: - 命中（Java:175-187）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < 260 && x < 140 { return "back" }
        if y >= buttonCy - buttonR && y <= buttonCy + buttonR {
            if abs(x - buttonCx[0]) <= buttonR { return "pay" }
            if abs(x - buttonCx[1]) <= buttonR { return "album" }
        }
        if y >= 2560 && y <= 2700 {
            for (i, cx) in tabCx.enumerated() where abs(x - cx) <= 120 {
                return "tab:\(i)"
            }
        }
        return "noop"
    }
}
