import UIKit

/// 「视频 → 抖音」主页 —— `VideoHomeActivity.Screen.onDraw()`（Java:370-433）的 1:1 移植。
///
/// 画布 1080×2388，黑底；视频本身由原生播放器铺在最底层（iOS 侧暂未接播放器，
/// 未选视频时与 Android 一样只显示中间那个「上传视频」加号）。
///
/// 可点区域与 Java `onTouchEvent`（497-577）逐条对应。
final class VideoDouyinScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    /// 单屏页：内容就是一屏，不做滚动（所有交互都是点击）
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    /// 顶部分类（`tabs` / `tabX`，Java:295-296）
    private let tabs = ["杭州", "关注", "推荐"]
    private let tabX: [CGFloat] = [324, 527, 735]

    final class State: ScreenState, VideoFeedState {
        var tab = 2
        var userName = "@微笑似年华々"
        var contentText = "请点击编辑内容"
        var musicText = "@唯一创作的音乐原声"
        var likeText = "10000.0w"
        var commentText = "10.0w"
        var shareText = "999"
        var messageText = "99"
        var liked = false
        var hasVideo = false

        var selectedTab: Int {
            get { tab }
            set { tab = newValue }
        }

        func fieldInfo(_ key: String) -> (title: String, value: String, numeric: Bool)? {
            switch key {
            case "user": return ("账号名", userName, false)
            case "content": return ("内容", contentText, false)
            case "music": return ("音乐原声", musicText, false)
            case "like": return ("点赞数", likeText, true)
            case "comment": return ("评论数", commentText, true)
            case "share": return ("分享数", shareText, true)
            case "msg": return ("消息数", messageText, true)
            default: return nil
            }
        }

        func setField(_ key: String, _ value: String) {
            switch key {
            case "user": userName = value.isEmpty ? "@微笑似年华々" : value
            case "content": contentText = value
            case "music": musicText = value
            case "like": likeText = value
            case "comment": commentText = value
            case "share": shareText = value
            case "msg": messageText = value
            default: break
            }
        }

        func toggleLiked() { liked.toggle() }
    }

    // MARK: - 绘制（Java:370-433）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        // 视频层在最底下；没有视频时整屏就是黑的
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .black)

        drawTopBar(d, s)
        if !s.hasVideo { drawUploadHint(d) }
        drawRightRail(d, s)
        drawCaptionAndMusic(d, s)
        drawBottomBar(d, s)
    }

    /// 顶部：直播图标 + 三个分类 + 99 气泡 + 搜索（Java:375-387）
    private func drawTopBar(_ d: Draw, _ s: State) {
        d.art(VideoKit.icon("dy_live"), CGRect(x: 45, y: 152, width: 80, height: 88))
        for (index, label) in tabs.enumerated() {
            let selected = index == s.tab
            let color: UIColor = selected ? .white : Draw.color(0xffb5b5b5)
            d.centerText(label, cx: tabX[index], baseline: 224, size: 58, color, bold: selected)
            if selected {
                d.rect(CGRect(x: tabX[index] - 33, y: 240, width: 66, height: 6), .white)
            }
        }
        VideoKit.badge(d, text: "99", cx: 589, top: 132, height: 44, size: 28,
                       bg: Draw.color(0xffffd84d), color: Draw.color(0xff1a1a1a),
                       minWidth: 58, pad: 0)
        d.art(VideoKit.icon("dy_search"), CGRect(x: 950, y: 150, width: 95, height: 90))
    }

    /// 未选视频时的「上传视频」提示（Java:390-394）
    private func drawUploadHint(_ d: Draw) {
        d.art(VideoKit.icon("dy_plus"), CGRect(x: 460, y: 980, width: 160, height: 165))
        d.centerText("上传视频", cx: 540, baseline: 1170, size: 30, Draw.color(0xff9a9a9a))
        d.centerText("可自动合成点赞评论等数据", cx: 540, baseline: 1222, size: 26,
                     Draw.color(0xff8a8a8a))
    }

    /// 右侧：头像 / 赞 / 评论 / 分享 / 唱片（Java:396-404）
    private func drawRightRail(_ d: Draw, _ s: State) {
        VideoKit.circleArt(d, VideoKit.icon("avatar"), cx: 998, cy: 1097, radius: 71)

        d.art(VideoKit.icon(s.liked ? "dy_heart_red" : "dy_heart"),
              CGRect(x: 930, y: 1255, width: 130, height: 120))
        d.centerText(s.likeText, cx: 998, baseline: 1427, size: 34, .white)

        d.art(VideoKit.icon("dy_comment"), CGRect(x: 930, y: 1470, width: 130, height: 125))
        d.centerText(s.commentText, cx: 998, baseline: 1649, size: 34, .white)

        d.art(VideoKit.icon("dy_share"), CGRect(x: 930, y: 1695, width: 130, height: 115))
        d.centerText(s.shareText, cx: 998, baseline: 1870, size: 34, .white)

        // 唱片：深灰底圆 + 头像（Java:490-494）
        d.circle(cx: 1000, cy: 2055, r: 73, Draw.color(0xff2a2a2a))
        VideoKit.circleArt(d, VideoKit.icon("avatar"), cx: 1000, cy: 2055, radius: 48)
    }

    /// 左下：账号 / 内容 / 音乐（Java:406-420）
    private func drawCaptionAndMusic(_ d: Draw, _ s: State) {
        d.text(s.userName, x: 38, baseline: 1938, size: 46, .white)
        d.text(s.contentText, x: 38, baseline: 2012, size: 42, .white)
        VideoKit.musicLine(d, s.musicText, note: "dy_note",
                           noteRect: CGRect(x: 30, y: 2061, width: 41, height: 47),
                           clip: CGRect(x: 72, y: 2035, width: 468, height: 85),
                           baseline: 2100, size: 42)
    }

    /// 底部五格（Java:422-430）
    private func drawBottomBar(_ d: Draw, _ s: State) {
        d.rect(CGRect(x: 0, y: 2154, width: Self.W, height: Self.H - 2154),
               Draw.color(0xff3e3e3e))

        d.centerText("首页", cx: 82, baseline: 2256, size: 42, .white, bold: true)
        d.centerText("朋友", cx: 300, baseline: 2256, size: 42, Draw.color(0xff9a9a9a))

        // 「消息」上的数字气泡（Java:435-441）
        VideoKit.badge(d, text: s.messageText, cx: 820, top: 2190, height: 46, size: 26,
                       bg: Draw.color(0xffffd84d), color: Draw.color(0xff1a1a1a),
                       minWidth: 60, pad: 30)

        d.centerText("消息", cx: 742, baseline: 2256, size: 42, Draw.color(0xff9a9a9a))
        d.centerText("我", cx: 953, baseline: 2256, size: 42, Draw.color(0xff9a9a9a))
        d.art(VideoKit.icon("dy_plus_bottom"), CGRect(x: 470, y: 2180, width: 140, height: 105))
    }

    // MARK: - 命中（逐条对应 Java:497-577）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 左下三行文字
        if x < 540 && y > 1895 && y < 2125 {
            if y < 1965 { return "video:field:user" }
            if y < 2040 { return "video:field:content" }
            return "video:field:music"
        }
        // 右侧头像
        if x > 900 && x < 1075 && y > 1000 && y < 1190 { return "video:pickAvatar" }
        // 赞 / 评论 / 分享
        if x > 920 && x < 1075 {
            if y > 1260 && y < 1395 { return "video:like" }
            if y > 1480 && y < 1615 { return "video:field:comment" }
            if y > 1700 && y < 1835 { return "video:field:share" }
        }
        // 唱片头像
        if x > 920 && x < 1080 && y > 1975 && y < 2135 { return "video:pickAvatar" }
        // 消息数字
        if x > 660 && x < 830 && y > 2200 && y < 2300 { return "video:field:msg" }
        // 底部导航
        if y > 2190 {
            if x > 430 && x < 650 { return "video:pick" }
            if x > 60 && x < 200 { return "video:nav:home" }
            if x > 230 && x < 400 { return "video:nav:friend" }
            if x > 860 { return "video:nav:me" }
            return "noop"
        }
        // 中间加号（圆形热区，Java:562-566）
        let dx = x - 540
        let dy = y - 1061
        if dx * dx + dy * dy < 95 * 95 { return "video:pick" }
        // 顶部分类
        if y < 240 {
            for (index, center) in tabX.enumerated() where abs(x - center) < 70 {
                return "video:tab:\(index)"
            }
        }
        return "noop"
    }
}
