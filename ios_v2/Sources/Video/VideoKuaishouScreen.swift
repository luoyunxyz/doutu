import UIKit

/// 「视频 → 快手」主页 —— `KuaishouActivity.Screen.onDraw()`（Java:351-410）。
///
/// 与抖音同构：黑底 + 顶部「关注 / 发现 / 精选」+ 中间上传 + 右侧互动栏 +
/// 左下账号/内容/音乐 + 底部「首页 / 同城 / 拍摄 / 消息 / 我」。
final class VideoKuaishouScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    /// 单屏页：内容就是一屏，不做滚动（所有交互都是点击）
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    final class State: ScreenState, VideoFeedState {
        var tab = 2
        var userName = "@微笑似年华々"
        var contentText = "请点击编辑内容"
        var musicText = "@唯一创作的音乐原声"
        var likeText = "点赞"
        var commentText = "评论"
        var shareText = "分享"
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

    // MARK: - 绘制（Java:351-410）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .black)

        // 顶部：菜单 + 三个分类 + 搜索（Java:355-362）
        d.art(VideoKit.icon("ks_menu"), CGRect(x: 55, y: 160, width: 70, height: 60))
        d.text("关注", x: 298, baseline: 214, size: 46, Draw.color(0xff949494))
        d.text("发现", x: 488, baseline: 214, size: 46, Draw.color(0xff9b9b9b))
        d.text("精选", x: 680, baseline: 214, size: 46, .white, bold: true)
        d.rect(CGRect(x: 698, y: 228, width: 66, height: 6), .white)
        d.art(VideoKit.icon("ks_search"), CGRect(x: 955, y: 152, width: 75, height: 73))

        // 中间加号（Java:364-369）
        if !s.hasVideo {
            d.art(VideoKit.icon("dy_plus"), CGRect(x: 460, y: 980, width: 160, height: 165))
            d.centerText("上传视频", cx: 540, baseline: 1170, size: 30, Draw.color(0xff9a9a9a))
            d.centerText("可自动合成点赞评论等数据", cx: 540, baseline: 1222, size: 26,
                         Draw.color(0xff8a8a8a))
        }

        // 右侧：头像 / 赞 / 评论 / 分享（Java:371-382）
        d.art(VideoKit.icon("ks_avatar"), CGRect(x: 908, y: 1356, width: 170, height: 156))
        d.art(VideoKit.icon(s.liked ? "dy_heart_red" : "ks_heart"),
              CGRect(x: 945, y: 1568, width: 95, height: 84))
        d.centerText(s.likeText, cx: 1006, baseline: 1691, size: 40, .white)
        d.art(VideoKit.icon("ks_comment"), CGRect(x: 947, y: 1772, width: 93, height: 90))
        d.centerText(s.commentText, cx: 1006, baseline: 1913, size: 40, .white)
        d.art(VideoKit.icon("ks_share"), CGRect(x: 943, y: 1988, width: 97, height: 82))
        d.centerText(s.shareText, cx: 1006, baseline: 2118, size: 40, .white)

        // 左下：账号 / 内容 / 音乐（Java:384-397）
        d.text(s.userName, x: 37, baseline: 1960, size: 46, .white)
        d.text(s.contentText, x: 48, baseline: 2032, size: 42, .white)
        VideoKit.musicLine(d, s.musicText, note: "ks_note",
                           noteRect: CGRect(x: 32, y: 2080, width: 53, height: 55),
                           clip: CGRect(x: 95, y: 2065, width: 445, height: 70),
                           baseline: 2127, size: 42)

        // 底部（Java:399-407）
        d.rect(CGRect(x: 0, y: 2172, width: Self.W, height: Self.H - 2172),
               Draw.color(0xff3e3e3e))
        d.centerText("首页", cx: 108, baseline: 2269, size: 42, .white, bold: true)
        d.centerText("同城", cx: 325, baseline: 2269, size: 42, Draw.color(0xff9a9a9a))
        d.art(VideoKit.icon("ks_camera"), CGRect(x: 480, y: 2192, width: 120, height: 108))
        VideoKit.badge(d, text: s.messageText, cx: 845, top: 2182, height: 50, size: 30,
                       bg: Draw.color(0xffff3b5c), color: .white, minWidth: 80, pad: 40)
        d.centerText("消息", cx: 760, baseline: 2269, size: 42, Draw.color(0xff9a9a9a))
        d.centerText("我", cx: 972, baseline: 2269, size: 42, Draw.color(0xff9a9a9a))
    }

    // MARK: - 命中（Java:421-488）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 左下三行文字
        if x < 540 && y > 1900 && y < 2140 {
            if y < 1975 { return "video:field:user" }
            if y < 2055 { return "video:field:content" }
            return "video:field:music"
        }
        // 右侧头像
        if x > 900 && y > 1350 && y < 1510 { return "video:pickAvatar" }
        // 赞 / 评论 / 分享
        if x > 920 && x < 1080 {
            if y > 1540 && y < 1705 { return "video:like" }
            if y > 1745 && y < 1930 { return "video:field:comment" }
            if y > 1965 && y < 2135 { return "video:field:share" }
        }
        // 消息数
        if x > 680 && x < 880 && y > 2170 && y < 2320 { return "video:field:msg" }
        // 底部
        if y > 2180 {
            if x > 460 && x < 620 { return "video:pick" }
            if x < 180 { return "video:nav:home" }
            if x > 230 && x < 420 { return "video:nav:friend" }
            if x > 890 { return "video:nav:me" }
            return "noop"
        }
        // 中间加号
        let dx = x - 540
        let dy = y - 1061
        if dx * dx + dy * dy < 95 * 95 { return "video:pick" }
        return "noop"
    }
}
