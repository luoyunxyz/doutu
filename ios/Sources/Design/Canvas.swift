import UIKit

/// 参考画布 → 屏幕的映射。
///
/// Android 端每个页面都在一个**固定的参考画布**上等比绘制（例如支模块 1280×2816、
/// 首页外壳 1080×2388），再整体缩放到设备宽度。iOS 这边保持完全相同的模型：
/// 所有绘制代码都写参考画布坐标，由本类型统一换算，这样两边的数字可以一一对照，
/// 也是做到 1:1 的前提。
///
/// 注意：只按**宽度**等比缩放（高度自然延伸），与 Android 的 `scale = viewW / W` 一致。
struct Canvas {
    /// 参考画布尺寸（Android 里的 `W` / `H` 常量）
    let size: CGSize
    /// 实际绘制区域（像素）
    let bounds: CGRect

    init(size: CGSize, bounds: CGRect) {
        self.size = size
        self.bounds = bounds
    }

    /// 缩放系数：屏幕宽 / 参考宽
    var scale: CGFloat { bounds.width / size.width }

    /// 参考坐标点 → 屏幕坐标点
    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: bounds.minX + x * scale, y: bounds.minY + y * scale)
    }

    /// 参考矩形 → 屏幕矩形
    func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGRect {
        CGRect(x: bounds.minX + x * scale, y: bounds.minY + y * scale,
               width: w * scale, height: h * scale)
    }

    /// 参考矩形（用 RectF 风格的 left/top/right/bottom 更贴近 Java 源码）
    func rect(l: CGFloat, t: CGFloat, r: CGFloat, b: CGFloat) -> CGRect {
        rect(l, t, r - l, b - t)
    }

    /// 屏幕坐标点 → 参考坐标点（触摸命中用）
    func design(_ p: CGPoint) -> CGPoint {
        CGPoint(x: (p.x - bounds.minX) / scale, y: (p.y - bounds.minY) / scale)
    }

    /// 字号换算：参考画布里的 px 字号 → 屏幕 pt
    func font(_ size: CGFloat) -> CGFloat { size * scale }
}
