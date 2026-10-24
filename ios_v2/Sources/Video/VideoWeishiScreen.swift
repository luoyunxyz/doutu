import UIKit

/// 「视频 → 微视」主页 —— `WeishiActivity.Screen.onDraw()`（Java:353-407）。
///
/// 微视比抖音/快手多两样：右侧顶部的「表个态」猪图标，以及左下角头像 + 「关注」按钮。
final class VideoWeishiScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    /// 单屏页：内容就是一屏，不做滚动（所有交互都是点击）
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    final class State: ScreenState, VideoFeedState {
        var tab = 1
        var userName = "微笑似年华々"
        var contentText = "请点击编辑内容"
        var musicText = "唯一创作的音乐原声"
        var biaoText = "表个态"
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
            case "biao": return ("表个态", biaoText, true)
            case "like": return ("点赞数", likeText, true)
            case "comment": return ("评论数", commentText, true)
            case "share": return ("分享数", shareText, true)
            case "msg": return ("消息数", messageText, true)
            default: return nil
            }
        }

        func setField(_ key: String, _ value: String) {
            switch key {
            case "user": userName = value.isEmpty ? "微笑似年华々" : value
            case "content": contentText = value
            case "music": musicText = value
            case "biao": biaoText = value
            case "like": likeText = value
            case "comment": commentText = value
            case "share": shareText = value
            case "msg": messageText = value
            default: break
            }
        }

        func toggleLiked() { liked.toggle() }
    }

    // MARK: - 绘制（Java:353-407）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), .black)

        // 顶部：直播 + 关注 / 推荐 + 搜索（Java:357-361）
        d.art(VideoKit.icon("ws_live"), CGRect(x: 44, y: 128, width: 102, height: 64))
        d.text("关注", x: 192, baseline: 181, size: 46, Draw.color(0xffb5b5b5))
        d.text("推荐", x: 341, baseline: 181, size: 46, .white, bold: true)
        d.art(VideoKit.icon("ws_search"), CGRect(x: 948, y: 120, width: 80, height: 78))

        // 中间加号（Java:363-368）
        if !s.hasVideo {
            d.art(VideoKit.icon("dy_plus"), CGRect(x: 460, y: 980, width: 160, height: 165))
            d.centerText("上传视频", cx: 540, baseline: 1170, size: 30, Draw.color(0xff9a9a9a))
            d.centerText("可自动合成点赞评论等数据", cx: 540, baseline: 1222, size: 26,
                         Draw.color(0xff8a8a8a))
        }

        // 右侧四栏（Java:370-378）
        d.art(VideoKit.icon("ws_pig"), CGRect(x: 950, y: 1168, width: 110, height: 102))
        d.centerText(s.biaoText, cx: 1006, baseline: 1326, size: 40, .white)
        d.art(VideoKit.icon(s.liked ? "dy_heart_red" : "ws_heart"),
              CGRect(x: 940, y: 1385, width: 105, height: 90))
        d.centerText(s.likeText, cx: 1006, baseline: 1527, size: 40, .white)
        d.art(VideoKit.icon("ws_comment"), CGRect(x: 945, y: 1585, width: 100, height: 90))
        d.centerText(s.commentText, cx: 1006, baseline: 1729, size: 40, .white)
        d.art(VideoKit.icon("ws_share"), CGRect(x: 940, y: 1790, width: 105, height: 90))
        d.centerText(s.shareText, cx: 1006, baseline: 1931, size: 40, .white)

        // 左下：头像 + 昵称 + 关注 + 内容 + 音乐（Java:380-395）
        VideoKit.circleArt(d, VideoKit.icon("ws_avatar"), cx: 89, cy: 1913, radius: 54)
        d.text(s.userName, x: 167, baseline: 1933, size: 44, .white)
        d.art(VideoKit.icon("ws_guanzhu"), CGRect(x: 390, y: 1875, width: 130, height: 80))
        d.text(s.contentText, x: 47, baseline: 2031, size: 42, .white)
        VideoKit.musicLine(d, s.musicText, note: "ws_note",
                           noteRect: CGRect(x: 40, y: 2078, width: 40, height: 43),
                           clip: CGRect(x: 90, y: 2060, width: 450, height: 70),
                           baseline: 2120, size: 42)

        // 底部（Java:397-405）
        d.rect(CGRect(x: 0, y: 2154, width: Self.W, height: Self.H - 2154),
               Draw.color(0xff3e3e3e))
        d.centerText("首页", cx: 108, baseline: 2271, size: 42, .white, bold: true)
        d.centerText("频道", cx: 325, baseline: 2271, size: 42, Draw.color(0xff9a9a9a))
        d.art(VideoKit.icon("ws_plus_bottom"), CGRect(x: 495, y: 2203, width: 88, height: 88))
        VideoKit.badge(d, text: s.messageText, cx: 857, top: 2163, height: 50, size: 30,
                       bg: Draw.color(0xffff3b5c), color: .white, minWidth: 80, pad: 40)
        d.centerText("消息", cx: 774, baseline: 2271, size: 42, Draw.color(0xff9a9a9a))
        d.centerText("我", cx: 972, baseline: 2271, size: 42, Draw.color(0xff9a9a9a))
    }

    // MARK: - 命中（Java:419-490）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 左下文字（Java:426-437：账号名要 x > 150 才算，否则是头像）
        if x < 540 && y > 1885 && y < 2135 {
            if y < 1955 && x > 150 { return "video:field:user" }
            if y < 2050 { return "video:field:content" }
            return "video:field:music"
        }
        // 头像
        if x < 160 && y > 1850 && y < 1980 { return "video:pickAvatar" }
        // 右侧四栏
        if x > 920 && x < 1080 {
            if y > 1150 && y < 1345 { return "video:field:biao" }
            if y > 1380 && y < 1545 { return "video:like" }
            if y > 1580 && y < 1745 { return "video:field:comment" }
            if y > 1785 && y < 1950 { return "video:field:share" }
        }
        // 消息数
        if x > 690 && x < 900 && y > 2150 && y < 2300 { return "video:field:msg" }
        // 底部
        if y > 2190 {
            if x > 470 && x < 610 { return "video:pick" }
            if x < 180 { return "video:nav:home" }
            if x > 230 && x < 420 { return "video:nav:friend" }
            if x > 880 { return "video:nav:me" }
            return "noop"
        }
        // 中间加号
        let dx = x - 540
        let dy = y - 1061
        if dx * dx + dy * dy < 95 * 95 { return "video:pick" }
        return "noop"
    }
}
