import UIKit

/// 「视频」模块（Android `features/video`）的共用层。
///
/// 三家平台的页面（抖音 / 快手 / 微视）是同一套结构：黑底视频 + 顶部分类 + 中间上传 +
/// 右侧互动栏 + 左下账号/文案/音乐 + 底部五格。差异只是图标、坐标与文案，
/// 所以共用这里的图标加载、圆形头像、数字气泡与「可编辑字段」协议。
enum VideoKit {
    /// 图标：Android `assets/video_icons/<key>.png` → iOS 资源 `video_<key>`
    static func icon(_ key: String) -> UIImage? {
        let asset = "video_" + key
        return UIImage(named: asset)
    }

    /// 圆形裁切的头像（对应 Java 的 `circleArt`）
    static func circleArt(_ d: Draw, _ image: UIImage?, cx: CGFloat, cy: CGFloat, radius: CGFloat) {
        guard let image else { return }
        let rect = CGRect(x: cx - radius, y: cy - radius, width: radius * 2, height: radius * 2)
        d.clipped(to: rect, radius: radius) {
            d.art(image, rect)
        }
    }

    /// 数字气泡（未读 / 赞数那种圆角小条）
    static func badge(_ d: Draw, text: String, cx: CGFloat, top: CGFloat, height: CGFloat,
                      size: CGFloat, bg: UIColor, color: UIColor,
                      minWidth: CGFloat, pad: CGFloat) {
        let width = max(minWidth, Draw.width(text, size: size) + pad)
        d.round(CGRect(x: cx - width / 2, y: top, width: width, height: height),
                radius: height / 2, bg)
        let baseline = top + height / 2 + Draw.font(size: size).ascender / 2 - size * 0.06
        d.centerText(text, cx: cx, baseline: baseline, size: size, color)
    }

    /// 左下角那行会滚动的音乐文案。
    ///
    /// Android 每帧 `postInvalidateOnAnimation()` 让它从右往左滚；iOS 宿主只按 0.25s
    /// 重绘（4fps），跑起来会一顿一顿。这里取「静止位置」：贴着音符图标开始、裁到屏幕中线，
    /// 与参考截图里看到的一致，不会抖。
    static func musicLine(_ d: Draw, _ text: String, note: String,
                          noteRect: CGRect, clip: CGRect, baseline: CGFloat, size: CGFloat) {
        d.art(icon(note), noteRect)
        d.ctx.saveGState()
        d.ctx.clip(to: clip)
        d.text(text, x: clip.minX, baseline: baseline, size: size, UIColor.white)
        d.ctx.restoreGState()
    }
}

/// 三家视频页共享的状态接口 —— Router 只认这个，不关心是哪个平台。
///
/// 每个 `State` 自己解释字段键（`user` / `content` / `music` / `like` / `comment` /
/// `share` / `msg` / `biao`），这样 Router 不需要为三家各写一遍编辑逻辑。
protocol VideoFeedState: AnyObject {
    /// 点中某个可编辑字段：返回弹窗标题、当前值、是否数字键盘
    func fieldInfo(_ key: String) -> (title: String, value: String, numeric: Bool)?
    func setField(_ key: String, _ value: String)
    func toggleLiked()
    var selectedTab: Int { get set }
}
