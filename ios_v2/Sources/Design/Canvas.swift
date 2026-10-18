import UIKit

/// 参考画布 → 屏幕的映射。
///
/// Android 端每个页面都在一个**固定的参考画布**上绘制（例如支模块 1280×2816、
/// 首页外壳 1080×2388），再整体映射到设备屏幕。iOS 这边保持完全相同的模型：
/// 所有绘制代码都写参考画布坐标，由本类型统一换算，这样两边的数字可以一一对照。
///
/// ⚠️ 关键：**横纵分别缩放**，与 Android 完全一致：
/// ```java
/// canvas.scale(getWidth() / W, getHeight() / H);
/// ```
/// 早期版本这里只按宽度等比缩放（`scale = w / W` 同时用于 x、y），后果是：
/// 1. 画布高度 × 宽度比例 > 屏幕比例时，**底部（底部导航栏）被切掉**；
/// 2. 文字/图形没有被纵向拉伸，观感与 Android 有差异（Android 是拉伸铺满的）。
///
/// 所以这里提供 `sx` / `sy` 两个比例，`scale` 仅为兼容旧代码保留（等于 sx）。
struct Canvas {
    /// 参考画布尺寸（Android 里的 `W` / `H` 常量）
    let size: CGSize
    /// 实际绘制区域（pt）
    let bounds: CGRect

    init(size: CGSize, bounds: CGRect) {
        self.size = size
        self.bounds = bounds
    }

    /// 横向比例：屏幕宽 / 参考宽（对应 Android `getWidth() / W`）
    var sx: CGFloat { size.width > 0 ? bounds.width / size.width : 1 }

    /// 纵向比例：屏幕高 / 参考高（对应 Android `getHeight() / H`）
    var sy: CGFloat { size.height > 0 ? bounds.height / size.height : 1 }

    /// 兼容旧调用：历史上只有一个 scale（= 横向比例）
    var scale: CGFloat { sx }

    /// 参考坐标点 → 屏幕坐标点
    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: bounds.minX + x * sx, y: bounds.minY + y * sy)
    }

    /// 参考矩形 → 屏幕矩形
    func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGRect {
        CGRect(x: bounds.minX + x * sx, y: bounds.minY + y * sy,
               width: w * sx, height: h * sy)
    }

    /// 参考矩形（用 RectF 风格的 left/top/right/bottom 更贴近 Java 源码）
    func rect(l: CGFloat, t: CGFloat, r: CGFloat, b: CGFloat) -> CGRect {
        rect(l, t, r - l, b - t)
    }

    /// 屏幕坐标点 → 参考坐标点（触摸命中用）
    func design(_ p: CGPoint) -> CGPoint {
        CGPoint(x: (p.x - bounds.minX) / sx, y: (p.y - bounds.minY) / sy)
    }

    /// 字号换算。
    ///
    /// 文字是在**参考坐标系**里画的（`Draw.font(size:)` 直接用设计字号），
    /// 由全局 CTM（sx, sy）负责缩放，所以这里不再乘比例 —— 乘了会变成双重缩放。
    func font(_ size: CGFloat) -> CGFloat { size }
}
