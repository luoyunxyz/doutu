import UIKit

/// CoreGraphics 绘制助手集合。
///
/// 这些方法一一对应 Android 源码里 `Canvas` / `Paint` 的常用调用，命名刻意保持接近
/// （`text` / `centerText` / `round` / `line` / `art`），这样逐屏移植时可以**对着 Java 抄**，
/// 每一个绘制语句都能找到对应的一行 —— 这是保证 1:1 的做法。
///
/// 与 Android 的关键差异只有两处，都在这里统一处理：
///   1. Android 的 Canvas 以左上角为原点、y 向下，CoreGraphics 默认原点在左下 ——
///      这里在 `flip` 里把坐标系翻正，之后所有代码都可以按 Android 习惯写。
///   2. 文字基线：Android `drawText(text, x, y)` 的 y 就是基线，CoreGraphics 也是基线，
///      所以 `text(_:x:y:)` 直接透传，不需要额外偏移。
struct Draw {
    let ctx: CGContext
    let canvas: Canvas

    // MARK: - 坐标系

    /// 套上参考画布的缩放与原点偏移。
    ///
    /// **不需要翻转 y 轴**：UIKit 在 `UIView.draw(_:)` 里给的上下文本来就是「原点左上、y 向下」，
    /// 与 Android 的 Canvas 一致。所以这里只做 translate + scale，之后所有绘制都能直接
    /// 使用参考画布坐标（`x` 向右、`y` 向下），与 Java 源码的写法完全一样。
    ///
    /// ⚠️ **横纵分别缩放**（sx, sy），对齐 Android 的 `canvas.scale(w / W, h / H)`：
    /// 早期版本 x、y 都用横向比例，导致画布底部（底部导航栏）被切掉、文字也没被纵向拉伸。
    ///
    /// 唯一要小心的是**位图**：`UIImage.draw(in:)` 走 UIKit 的绘制通道，方向正确；
    /// 而 `CGContext.draw(cgImage:)` 在 y 向下的上下文里会上下颠倒，所以那条路径自己做了一次翻转。
    static func flip(_ ctx: CGContext, canvas: Canvas) {
        ctx.saveGState()
        ctx.translateBy(x: canvas.bounds.minX, y: canvas.bounds.minY)
        ctx.scaleBy(x: canvas.sx, y: canvas.sy)
    }

    static func restore(_ ctx: CGContext) {
        ctx.restoreGState()
    }

    // MARK: - 颜色

    /// Android 的 `0xAARRGGBB` 整数 → UIColor（源码里大量常量是这种字面量）
    static func color(_ argb: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((argb >> 16) & 0xFF) / 255.0,
            green: CGFloat((argb >> 8) & 0xFF) / 255.0,
            blue: CGFloat(argb & 0xFF) / 255.0,
            alpha: CGFloat((argb >> 24) & 0xFF) / 255.0
        )
    }

    // MARK: - 基本形状（参考画布坐标）

    /// 填充矩形；传整型颜色时用 `fill(_:color:)`
    func rect(_ r: CGRect, _ color: UIColor) {
        ctx.setFillColor(color.cgColor)
        ctx.fill(r)
    }

    /// 圆角矩形（对应 Android 的 `c.drawRoundRect(box, radius, radius, paint)`）
    func round(_ r: CGRect, radius: CGFloat, _ color: UIColor) {
        ctx.setFillColor(color.cgColor)
        ctx.addPath(UIBezierPath(roundedRect: r, cornerRadius: radius).cgPath)
        ctx.fillPath()
    }

    /// 圆角矩形描边
    func roundStroke(_ r: CGRect, radius: CGFloat, _ color: UIColor, width: CGFloat = 1) {
        ctx.setStrokeColor(color.cgColor)
        ctx.setLineWidth(width)
        ctx.addPath(UIBezierPath(roundedRect: r, cornerRadius: radius).cgPath)
        ctx.strokePath()
    }

    /// 圆 / 椭圆
    func circle(cx: CGFloat, cy: CGFloat, r: CGFloat, _ color: UIColor) {
        ctx.setFillColor(color.cgColor)
        ctx.fillEllipse(in: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
    }

    /// 线段
    func line(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat,
              _ color: UIColor, width: CGFloat = 1) {
        ctx.setStrokeColor(color.cgColor)
        ctx.setLineWidth(width)
        ctx.move(to: CGPoint(x: x1, y: y1))
        ctx.addLine(to: CGPoint(x: x2, y: y2))
        ctx.strokePath()
    }

    /// 1px 分割线（Android 里常用 `drawRect(y, y+hairline)` 画，这里保持同样思路）
    func hairline(_ y: CGFloat, from x: CGFloat, to x2: CGFloat, _ color: UIColor, width: CGFloat = 1) {
        rect(CGRect(x: x, y: y, width: x2 - x, height: width), color)
    }

    /// 线性渐变（对应 `Paint.setShader(new LinearGradient(...))`）
    func gradient(_ r: CGRect, from top: UIColor, to bottom: UIColor, vertical: Bool = true) {
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let grad = CGGradient(colorsSpace: space,
                                    colors: [top.cgColor, bottom.cgColor] as CFArray,
                                    locations: [0, 1]) else { return }
        ctx.saveGState()
        ctx.clip(to: r)
        let start = CGPoint(x: r.minX, y: r.minY)
        let end = vertical ? CGPoint(x: r.minX, y: r.maxY) : CGPoint(x: r.maxX, y: r.minY)
        ctx.drawLinearGradient(grad, start: start, end: end, options: [])
        ctx.restoreGState()
    }

    /// 裁剪到圆角矩形后执行（对应 `c.save(); c.clipPath(roundPath(...)); ...; c.restore()`）
    func clipped(to r: CGRect, radius: CGFloat, _ body: () -> Void) {
        ctx.saveGState()
        ctx.addPath(UIBezierPath(roundedRect: r, cornerRadius: radius).cgPath)
        ctx.clip()
        body()
        ctx.restoreGState()
    }

    // MARK: - 文字

    /// 字体构造（对应 Android 的 `Typeface.DEFAULT / DEFAULT_BOLD`）
    static func font(size: CGFloat, bold: Bool = false) -> UIFont {
        let base = UIFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular)
        return base
    }

    /// 左对齐绘制，`y` 是**基线**（与 Android `drawText` 一致）
    ///
    /// 换算：UIKit 的 `NSAttributedString.draw(at:)` 定位的是**行框左上角**，
    /// 而 Android 给的是基线 → 减去字体上伸部（ascender）即可精确对齐。
    func text(_ string: String, x: CGFloat, baseline y: CGFloat, size: CGFloat,
              _ color: UIColor, bold: Bool = false, sx: CGFloat = 1) {
        guard !string.isEmpty else { return }
        let font = Draw.font(size: size, bold: bold)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let ns = NSAttributedString(string: string, attributes: attrs)
        ctx.saveGState()
        if sx != 1 {
            // Android 用 `paint.setTextScaleX()` 做横向拉伸（MeScreen 的 SX_NAME/SX_PHONE）；
            // Java 那边的写法是 `c.scale(sx,1); c.drawText(text, x/sx, ...)` —— 这里等价实现
            ctx.translateBy(x: x, y: y)
            ctx.scaleBy(x: sx, y: 1)
            ns.draw(at: CGPoint(x: 0, y: -font.ascender))
        } else {
            ns.draw(at: CGPoint(x: x, y: y - font.ascender))
        }
        ctx.restoreGState()
    }

    /// 居中绘制（对应 Android 的 `drawText` + `textAlign=CENTER`）
    func centerText(_ string: String, cx: CGFloat, baseline y: CGFloat, size: CGFloat,
                    _ color: UIColor, bold: Bool = false, sx: CGFloat = 1) {
        guard !string.isEmpty else { return }
        let font = Draw.font(size: size, bold: bold)
        let width = (string as NSString).size(withAttributes: [.font: font]).width * sx
        text(string, x: cx - width / 2, baseline: y, size: size, color, bold: bold, sx: sx)
    }

    /// 带阴影的文字（对应 Android 的 `paint.setShadowLayer(radius, dx, dy, color)`）。
    ///
    /// 首页横幅的主文案没有底框，全靠阴影压在紫色人物图上保证可读 —— 这是参考图上的实际做法。
    func textWithShadow(_ string: String, x: CGFloat, baseline y: CGFloat, size: CGFloat,
                        _ color: UIColor, bold: Bool = false,
                        radius: CGFloat, offsetY: CGFloat, shadow: UIColor) {
        guard !string.isEmpty else { return }
        let font = Draw.font(size: size, bold: bold)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let ns = NSAttributedString(string: string, attributes: attrs)
        ctx.saveGState()
        // CoreGraphics 的文字阴影用绘图状态设置（模糊半径 + 偏移，y 向下为正）
        ctx.setShadow(offset: CGSize(width: 0, height: offsetY), blur: radius, color: shadow.cgColor)
        ns.draw(at: CGPoint(x: x, y: y - font.ascender))
        ctx.restoreGState()
    }

    /// 右对齐绘制（带横向拉伸，对应 Android `textRight` 里的 `setTextScaleX`）
    func rightText(_ string: String, right: CGFloat, baseline y: CGFloat, size: CGFloat,
                   _ color: UIColor, bold: Bool = false, sx: CGFloat = 1) {
        guard !string.isEmpty else { return }
        let width = Draw.width(string, size: size, bold: bold) * sx
        text(string, x: right - width, baseline: y, size: size, color, bold: bold, sx: sx)
    }

    /// 文字宽度（做居中/换行时用）
    static func width(_ string: String, size: CGFloat, bold: Bool = false) -> CGFloat {
        (string as NSString).size(withAttributes: [.font: font(size: size, bold: bold)]).width
    }

    /// 圆弧（对应 Android 的 `c.drawArc(rect, startAngle, sweepAngle, useCenter, paint)`）。
    ///
    /// ⚠️ 角度约定两边不同：Android 以 3 点钟方向为 0°、顺时针为正；
    /// CoreGraphics 的 `addArc` 在**未翻转的坐标系**里以 3 点钟为 0°、逆时针为正。
    /// 由于 UIKit 的绘图上下文 y 向下，这里直接按「顺时针为正」传角度即可对上 Android。
    func arc(rect: CGRect, startDegrees: CGFloat, sweepDegrees: CGFloat,
             color: UIColor, width: CGFloat, fill: Bool = false) {
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        let start = startDegrees * .pi / 180
        let end = (startDegrees + sweepDegrees) * .pi / 180
        ctx.setFillColor(color.cgColor)
        ctx.setStrokeColor(color.cgColor)
        ctx.setLineWidth(width)
        if fill {
            ctx.move(to: center)
            ctx.addArc(center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
            ctx.closePath()
            ctx.fillPath()
        } else {
            ctx.addArc(center: center, radius: radius, startAngle: start, endAngle: end, clockwise: false)
            ctx.strokePath()
        }
    }

    /// 折线闭合路径填充（对应 Android 的 `Path.moveTo/lineTo/close` + `drawPath`）
    func path(points: [(CGFloat, CGFloat)], fill: UIColor) {
        guard points.count > 1 else { return }
        ctx.setFillColor(fill.cgColor)
        ctx.move(to: CGPoint(x: points[0].0, y: points[0].1))
        for point in points.dropFirst() {
            ctx.addLine(to: CGPoint(x: point.0, y: point.1))
        }
        ctx.closePath()
        ctx.fillPath()
    }

    /// 同上，但收 `[CGPoint]`。
    ///
    /// 页面里有两种写法：早期几个屏直接写 `[(1, -17), (-9, 1), …]`（元组数组），
    /// 后来的屏习惯 `[CGPoint(x:y:), …]`。**元组数组和 CGPoint 数组不能互相赋值**，
    /// 所以必须留两个重载 —— 少一个就是一片编译错误。
    func path(_ points: [CGPoint], fill: UIColor) {
        guard points.count > 1 else { return }
        ctx.setFillColor(fill.cgColor)
        ctx.move(to: points[0])
        for point in points.dropFirst() {
            ctx.addLine(to: point)
        }
        ctx.closePath()
        ctx.fillPath()
    }

    /// 等比铺满绘制（对应 Android `AvatarView.coverMatrix`：短边铺满、居中裁切）
    func artFill(_ image: UIImage?, in rect: CGRect) {
        guard let image, image.size.width > 0, image.size.height > 0 else { return }
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let origin = CGPoint(x: rect.midX - drawSize.width / 2, y: rect.midY - drawSize.height / 2)
        image.draw(in: CGRect(origin: origin, size: drawSize))
    }

    /// 描边正六边形（对应 Android 里按 6 个点连成 Path 再描边）
    func strokeHexagon(cx: CGFloat, cy: CGFloat, radius: CGFloat, color: UIColor, width: CGFloat) {
        ctx.setStrokeColor(color.cgColor)
        ctx.setLineWidth(width)
        for i in 0..<6 {
            // Android: a = π/3 * i - π/6
            let a = CGFloat.pi / 3 * CGFloat(i) - CGFloat.pi / 6
            let p = CGPoint(x: cx + cos(a) * radius, y: cy + sin(a) * radius)
            if i == 0 { ctx.move(to: p) } else { ctx.addLine(to: p) }
        }
        ctx.closePath()
        ctx.strokePath()
    }

    /// 多色标渐变（对应 Android `new LinearGradient(x0,y0,x1,y1,int[] colors,float[] stops,...)`）
    func multiGradient(_ r: CGRect, colors: [UIColor], stops: [CGFloat], vertical: Bool = true) {
        guard colors.count >= 2, colors.count == stops.count,
              let space = CGColorSpace(name: CGColorSpace.sRGB),
              let grad = CGGradient(colorsSpace: space,
                                    colors: colors.map { $0.cgColor } as CFArray,
                                    locations: stops.map { $0 }) else { return }
        ctx.saveGState()
        ctx.clip(to: r)
        let start = CGPoint(x: r.minX, y: r.minY)
        let end = vertical ? CGPoint(x: r.minX, y: r.maxY) : CGPoint(x: r.maxX, y: r.minY)
        ctx.drawLinearGradient(grad, start: start, end: end, options: [.drawsBeforeStartLocation,
                                                                      .drawsAfterEndLocation])
        ctx.restoreGState()
    }

    /// 横向压缩的左对齐文字。
    ///
    /// 对应 Android `c.save(); c.scale(sx, 1f, pivotX, 0f); c.drawText(...); c.restore()` ——
    /// **缩放轴在锚点上**（左对齐以 x 为轴），所以文字起点不动、只被压窄。
    func textSqueezed(_ string: String, x: CGFloat, baseline y: CGFloat, size: CGFloat,
                      _ color: UIColor, bold: Bool = false, squeeze: CGFloat = 1) {
        guard !string.isEmpty else { return }
        guard squeeze != 1 else {
            text(string, x: x, baseline: y, size: size, color, bold: bold)
            return
        }
        let font = Draw.font(size: size, bold: bold)
        let ns = NSAttributedString(string: string,
                                    attributes: [.font: font, .foregroundColor: color])
        ctx.saveGState()
        ctx.translateBy(x: x, y: 0)
        ctx.scaleBy(x: squeeze, y: 1)
        ns.draw(at: CGPoint(x: 0, y: y - font.ascender))
        ctx.restoreGState()
    }

    /// 横向压缩的右对齐文字（缩放轴在 right 上）
    func rightTextSqueezed(_ string: String, right: CGFloat, baseline y: CGFloat, size: CGFloat,
                           _ color: UIColor, bold: Bool = false, squeeze: CGFloat = 1) {
        guard !string.isEmpty else { return }
        let width = Draw.width(string, size: size, bold: bold)
        guard squeeze != 1 else {
            text(string, x: right - width, baseline: y, size: size, color, bold: bold)
            return
        }
        let font = Draw.font(size: size, bold: bold)
        let ns = NSAttributedString(string: string,
                                    attributes: [.font: font, .foregroundColor: color])
        ctx.saveGState()
        ctx.translateBy(x: right, y: 0)
        ctx.scaleBy(x: squeeze, y: 1)
        ns.draw(at: CGPoint(x: -width, y: y - font.ascender))
        ctx.restoreGState()
    }

    /// 横向压缩的居中文字（缩放轴在 cx 上）
    func centerTextSqueezed(_ string: String, cx: CGFloat, baseline y: CGFloat, size: CGFloat,
                            _ color: UIColor, bold: Bool = false, squeeze: CGFloat = 1) {
        guard !string.isEmpty else { return }
        let width = Draw.width(string, size: size, bold: bold)
        guard squeeze != 1 else {
            text(string, x: cx - width / 2, baseline: y, size: size, color, bold: bold)
            return
        }
        let font = Draw.font(size: size, bold: bold)
        let ns = NSAttributedString(string: string,
                                    attributes: [.font: font, .foregroundColor: color])
        ctx.saveGState()
        ctx.translateBy(x: cx, y: 0)
        ctx.scaleBy(x: squeeze, y: 1)
        ns.draw(at: CGPoint(x: -width / 2, y: y - font.ascender))
        ctx.restoreGState()
    }

    /// 用**数字字体**（Archivo SemiCondensed Semibold）绘制金额/数字。
    ///
    /// 对应 Android 的 `number()`：参考图里的金额、数字都是这支半窄字体，与汉字混排时比汉字窄一号。
    func text(_ string: String, x: CGFloat, baseline y: CGFloat, size: CGFloat,
              _ color: UIColor, bold: Bool = false, useNumberFace: Bool) {
        guard !string.isEmpty else { return }
        let font = useNumberFace ? Fonts.number(size) : Draw.font(size: size, bold: bold)
        let ns = NSAttributedString(string: string,
                                    attributes: [.font: font, .foregroundColor: color])
        ns.draw(at: CGPoint(x: x, y: y - font.ascender))
    }

    // MARK: - 位图

    /// 把位图拉伸绘制到参考矩形的对应位置（对应 `c.drawBitmap(bmp, null, r, paint)`）
    func art(_ image: UIImage?, _ r: CGRect) {
        guard let image else { return }
        // CoreGraphics 的 draw 会跟随当前坐标系（已翻正），所以直接按参考坐标画即可
        image.draw(in: r)
    }

    /// 带源矩形裁剪的位图绘制（对应 `c.drawBitmap(bmp, srcRect, dstRect, paint)`）
    func art(_ image: UIImage?, src: CGRect?, _ r: CGRect) {
        guard let image else { return }
        let cg = image.cgImage
        guard let cg else { return }
        let crop: CGImage
        if let src, let cropped = cg.cropping(to: src) { crop = cropped } else { crop = cg }
        ctx.saveGState()
        // 位图在翻正后的坐标系里是上下颠倒的，这里再翻一次
        ctx.translateBy(x: 0, y: r.midY * 2)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(crop, in: r)
        ctx.restoreGState()
    }
}
