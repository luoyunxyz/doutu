import UIKit

/// 未开通会员时铺满整页的斜向水印 —— `account/WatermarkOverlay.java` 的 1:1 移植。
///
/// Android 那边的做法与参数（务必保持一致，否则观感会差）：
/// ```java
/// ANGLE = -30f;                       // 斜向角度
/// FILL_COLOR   = 0x5AFFFFFF;          // 白色半透明填充
/// STROKE_COLOR = 0x55000000;          // 深色描边（浅底上也看得清）
/// size  = Math.max(28f, w * 0.055f);  // 字号按屏宽比例
/// stepX = textWidth + size * 1.6f;    // 横向间距
/// stepY = size * 3.2f;                // 纵向行距
/// // 绕中心旋转，按对角线长度铺满整块画布；奇数行错开半格
/// ```
///
/// 三条行为约束：
/// 1. **不吃事件**：`isUserInteractionEnabled = false`，点击照常落到真实页面上；
/// 2. **全局生效**：挂在 keyWindow 上，所以所有页面（含模态的 WebView）都会被覆盖；
/// 3. **登录/注册页不铺**：`suppress` 计数 > 0 时整层隐藏。
final class WatermarkOverlay: UIView {
    /// 当前挂在窗口上的那一层（全局只留一层）
    private static weak var current: WatermarkOverlay?
    /// 抑制计数：登录页等场景临时隐藏
    private static var suppressCount = 0

    private static let angle: CGFloat = -30 * .pi / 180
    private static let fillColor = UIColor(white: 1, alpha: 0x5A / 255)
    private static let strokeColor = UIColor(white: 0, alpha: 0x55 / 255)

    private var text = "开通会员去除水印"

    // MARK: - 挂载 / 摘除

    /// 临时隐藏（登录、重置密码等页面用）。成对调用：beginSuppress / endSuppress。
    static func beginSuppress() {
        suppressCount += 1
        current?.isHidden = true
    }

    static func endSuppress() {
        suppressCount = max(0, suppressCount - 1)
        if suppressCount == 0 {
            attachIfNeeded()
            current?.isHidden = false
        }
    }

    /// 按当前会员状态挂载或摘除水印。**每次页面可见、登录态变化、拉到配置后调一次。**
    ///
    /// - Parameter preferredWindow: 调用方手上的窗口（`view.window`）。
    ///   真机首帧时 `UIApplication` 的窗口列表可能还没就绪，传进来更可靠。
    static func attachIfNeeded(preferredWindow: UIWindow? = nil) {
        guard let window = preferredWindow ?? keyWindow else {
            CrashLog.append("WM skip: no window")
            return
        }

        let activated = Session.shared.isActivated
        let hidden = suppressCount > 0

        CrashLog.append("WM check activated=\(activated) hidden=\(hidden)")
        if activated || hidden {
            current?.removeFromSuperview()
            current = nil
            if hidden, !activated { return }
            return
        }

        let overlay: WatermarkOverlay
        if let existing = current, existing.superview != nil {
            overlay = existing
        } else {
            overlay = WatermarkOverlay(frame: window.bounds)
            overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            overlay.isUserInteractionEnabled = false
            overlay.isOpaque = false
            overlay.backgroundColor = .clear
            window.addSubview(overlay)
            current = overlay
        }
        CrashLog.append("WM attach frame=\(window.bounds) text=\(Session.shared.overlayText)")
        overlay.frame = window.bounds
        overlay.text = Session.shared.overlayText
        window.bringSubviewToFront(overlay)
        overlay.setNeedsDisplay()
    }

    private static var keyWindow: UIWindow? {
        if #available(iOS 13.0, *) {
            let windows = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
            if let key = windows.first(where: { $0.isKeyWindow }) { return key }
            return windows.first
        }
        return UIApplication.shared.keyWindow
    }

    // MARK: - 绘制（参数对齐 Android）

    override func draw(_ rect: CGRect) {
        guard let ctx = UIGraphicsGetCurrentContext() else { return }
        let w = bounds.width, h = bounds.height
        guard w > 0, h > 0, !text.isEmpty else { return }

        let size = max(28, w * 0.055)
        let font = UIFont.boldSystemFont(ofSize: size)
        // 负的 strokeWidth = 既填充又描边，正好对应 Android 的「填充 + 描边」两次绘制
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: Self.fillColor,
            .strokeColor: Self.strokeColor,
            .strokeWidth: -2.0,
        ]
        let attributed = NSAttributedString(string: text, attributes: attrs)
        let textWidth = attributed.size().width
        guard textWidth > 0 else { return }

        let stepX = textWidth + size * 1.6
        let stepY = size * 3.2
        let diagonal = sqrt(w * w + h * h)
        let rows = Int(diagonal / stepY) + 2
        let cols = Int(diagonal / stepX) + 2

        ctx.saveGState()
        ctx.translateBy(x: w / 2, y: h / 2)
        ctx.rotate(by: Self.angle)
        let startX = -CGFloat(cols) * stepX / 2
        let startY = -CGFloat(rows) * stepY / 2
        for row in 0..<rows {
            let offset = (row % 2 == 0) ? 0 : stepX / 2
            for col in 0..<cols {
                let x = startX + CGFloat(col) * stepX + offset
                let y = startY + CGFloat(row) * stepY
                attributed.draw(at: CGPoint(x: x, y: y - font.ascender))
            }
        }
        ctx.restoreGState()
    }
}
