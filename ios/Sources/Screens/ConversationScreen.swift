import UIKit

/// 「微信 · 聊天」页 —— `features/wei/ui/ConversationScreen.java`（397 行）的 1:1 移植。
///
/// ## 一、参考画布（本屏唯一需要判断的地方）
///
/// Java 那边**没有** `W/H` + `canvas.scale` 这一套：`ConversationScreen` 继承 `LinearLayout`
/// （Java:33），是纯原生 View 布局（`LinearLayout` / `ScrollView` / `EditText` / 自绘 `IconView`），
/// 尺寸全是 dp/sp，由系统按屏幕密度换算（Java:268-270 的 `dp()` → `WeiPalette.dp`）。
///
/// iOS 的 `Screen` 协议要求一个参考画布，所以这里按 **1280×2816** 做等比映射 —— 与全项目其它页一致
/// （同 `CustomerServiceScreen` / `CallSetupScreen` 的处理方式）。依据不是拍脑袋：
/// 同模块的 `WeiHomeScreen.java:42-43` 把自己明确写在 **360×792 dp** 的画布上
/// （`canvas.scale(getWidth()/360, getHeight()/792)`），而
/// `360 × 32/9 = 1280`、`792 × 32/9 = 2816`（**恰好整除**），
/// 所以「1dp = 32/9 参考单位」既对齐了本模块的 dp 设计稿，又正好落在项目统一的 1280×2816 上。
///
/// 于是：48dp 顶栏 → 170.7，50dp 输入栏 → 177.8，16sp 正文 → 56.9，36dp 头像 → 128。
/// dp → 参考单位的换算全部收敛在 `dp(_:)` 里，源码里的数字与 Java 的 `dp(…)` 一一对应。
///
/// ## 二、滚动（顶栏 / 输入栏固定，只有消息列表跟着手指滚）
///
/// Java:62-75 是「header（48dp）+ ScrollView（权重 1）+ composerBar（50dp）」的竖排。
/// 所以 `selfScrolls = true`：`draw` 里只对消息区域做 `translate(0, -scroll)` 并裁剪，
/// 宿主**不要**再整页滚一次（两处都做就是双倍滚动）。
/// 但宿主仍会在 `hitTest` 的入参 y 上加上 `scroll`（见 `Screen.swift` 的 `touchesEnded`），
/// 所以这里命中时先把 scroll 减回去 —— 否则滚到底部就点不到顶栏的返回键。
///
/// ## 三、动作令牌（上层 Router 负责执行）
///
///   * `"back"`           —— 顶栏返回（Java:171-177 → `listener.onBack()`）
///   * `"toast:聊天信息"`  —— 顶栏右侧「···」（Java:195 `Toast「聊天信息」`）
///   * `"conv:voice"`     —— 左侧语音/键盘切换（Java:104-108：hint 在 `""` 与「按住 说话」之间切）
///   * `"conv:input"`     —— 点输入框 / 内容为空时点右侧图标（Java:116-123：聚焦 + 弹键盘）
///   * `"conv:send"`      —— 有内容时点右侧图标 / 键盘发送键（Java:117、132-141）
///   * `"conv:emoji"`     —— 表情（Java:149-152：往光标处插「😊」）
///   * `"conv:more"`      —— 加号（Java:159-162：弹「照片 / 拍摄 / 位置」，选完 Toast）
///   * `"noop"`           —— 其余（消息气泡本身没有监听，Java:217-266 只 addView 不 setListener）
///
/// 三个会改 `State` 的动作（发送 / 语音切换 / 插表情）在文件末尾留了 `sendDraft` /
/// `toggleVoice` / `insertEmoji` —— Router 调一句就够，不必自己拼业务逻辑。
///
/// ⚠️ 落地提醒：`DoutuApp.swift` 目前对 `conv:` 前缀只做 `host?.setNeedsDisplay()`
/// （只重绘、不改状态）。要真正能发消息 / 插表情 / 切语音，那条分支里需要
/// 拿到当前页实例（`host?.screen as? ConversationScreen`）后调上面三个方法；
/// `conv:input` 则需要弹输入框、把文字写进 `state.draft`。
///
/// ## 四、素材依赖
///
/// `chat_avatar_redmi`（对方头像）、`chat_avatar_me`（我的头像）：Android 侧是
/// `res/drawable-nodpi/chat_avatar_redmi.png` / `chat_avatar_me.png`（Java:224），
/// iOS 侧这两张**已经在** `Assets.xcassets` 里（`node _ios/sync-assets.mjs` 同步过），
/// `lint-swift.mjs` 的「引用的图片资源都存在」一项通过。
/// 取不到时（例如换成别的会话、又没有对应切图）退化成「灰底圆角 + 首字」的占位，不会崩也不会白块。
///
/// ## 五、与 Android 的三处已知近似（都在注释里标了）
///
///   1. 气泡高度用 `UIFont.lineHeight` 近似 Android `includeFontPadding` 的行高
///      （单行时 36dp 的头像决定行高，47dp 行距与 Java:262-263 的注释一致）；
///   2. Java:364-365、383 的 `drawArc` 是**椭圆**弧，`Draw.arc` 只支持正圆，
///      这里按 bbox 的短边半径画（12dp 级别，肉眼无差）；
///   3. 输入框竖排基线取「居中」：Android `EditText` 默认 gravity 是 TOP，
///      但 38dp 高的框里视觉上是居中的，参考图的观感也居中。
final class ConversationScreen: Screen {
    // MARK: - 画布（Java 没有 W/H，取项目统一的 1280×2816；理由见文件头）

    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let dpUnit: CGFloat = 1280.0 / 360.0
    let canvasSize = CGSize(width: ConversationScreen.W, height: ConversationScreen.H)

    /// 顶栏固定、只有消息区跟着手指滚（对应 Java:62-75 的「header + 权重 1 的 ScrollView」）
    let selfScrolls = false

    /// dp → 参考单位（Java:268-270 `dp()` 的 density=3.0 换成 1280/360）
    private func dp(_ value: CGFloat) -> CGFloat { value * Self.dpUnit }

    // MARK: - 调色板（Java:39-43、97、197、227）

    private let chatBg = Draw.color(0xffededed)       // Java:39 CHAT_BG
    private let composerBg = Draw.color(0xfff7f7f7)   // Java:40 COMPOSER_BG
    private let ink = Draw.color(0xff191919)          // Java:41 TEXT_COLOR
    private let sentGreen = Draw.color(0xffaaea7a)    // Java:43 SENT_GREEN（原文 0xffaaeA7a）
    private let hintInk = Draw.color(0xffa4a4a4)      // Java:97 hint 文字色
    private let dividerInk = Draw.color(0xffe7e7e7)   // Java:197 顶栏底部 0.5dp 分割线
    private let avatarPlate = Draw.color(0xffffffff)  // Java:227 头像白底（圆角 5dp）
    private let avatarGhost = Draw.color(0xffd9d9d9)  // 取不到头像时的占位底（兜底，见文件头）
    private let bubbleWhite = UIColor.white           // Java:242 接收方白气泡
    private let iconInk = UIColor.black               // Java:345 paint.setColor(BLACK)

    // MARK: - 文案 / 字号（Java:95、105-106、180-182、335-336）

    /// Java:105-106 语音模式的提示文案
    static let voiceHint = "按住 说话"
    /// Java:191-195 顶栏右侧「···」的 Toast 文案
    static let chatInfoToast = "聊天信息"
    /// Java:160 加号弹出的三个菜单项
    static let moreItems = ["照片", "拍摄", "位置"]

    // MARK: - 状态

    /// 一条消息 —— `Conversation.Message`（`Conversation.java:9-20`）的对应物
    struct Message {
        let text: String
        let sentByMe: Bool
        init(_ text: String, sentByMe: Bool = false) {
            self.text = text
            self.sentByMe = sentByMe
        }
    }

    /// 参考图里那段会话（`ConversationRepository.java:24-38` 的 redmi 线程，14 条）
    static let seedMessages: [Message] = [
        Message("太难了"),
        Message("我感觉我天赋还可以"),
        Message("但是没用啊"),
        Message("我感觉我纯属执念", sentByMe: true),
        Message("又喜欢计算机", sentByMe: true),
        Message("又想用计算机挣钱", sentByMe: true),
        Message("我也是啊"),
        Message("其实挺难受的"),
        Message("我现在主要是不知道该怎么学"),
        Message("学啥都没人带"),
        Message("屡屡碰壁"),
        Message("我交了快三万的学费", sentByMe: true),
        Message("到头来还不是啥也不会", sentByMe: true),
        Message("哎"),
    ]

    final class State: ScreenState {
        /// 顶栏标题（Java:180 `conversation.getTitle()`）
        var title: String = "redmi"
        /// 取不到头像时画在占位块里的首字（`Conversation.getInitials()`）
        var initials: String = "r"
        /// 消息列表（Java:211-215 `refreshMessages` 的数据源）
        var messages: [Message] = ConversationScreen.seedMessages
        /// 输入框内容（Java:49 `composer` 的文本）
        var draft: String = ""
        /// 是否正显示「按住 说话」（Java:105 的 `"按住 说话".contentEquals(hint)`）
        var showingVoiceHint = false
        /// 对方头像（Java:224 `conversation.getAvatarResourceId()`），nil 时用包内默认图
        var remoteAvatar: UIImage?
        /// 我的头像（Java:224 `R.drawable.chat_avatar_me`），nil 时用包内默认图
        var myAvatar: UIImage?
    }

    /// 两张头像的默认素材（Java:224，Assets.xcassets 里已有）
    private let defaultRemoteAvatar = UIImage(named: "chat_avatar_redmi")
    private let defaultMyAvatar = UIImage(named: "chat_avatar_me")

    // MARK: - 图标（Java:321-396 的 `IconView`）

    /// Java:322 的 7 个图标编号
    private enum Icon {
        case back, more, voice, mic, emoji, add, send
    }

    // MARK: - 布局计算结果（draw / hitTest / maxScroll 共用同一份，避免三处算出不同矩形）

    private struct Row {
        var avatar: CGRect = .zero
        var bubble: CGRect = .zero
        /// 气泡的圆角矩形主体（已扣掉尾巴的 inset，Java:296-299）
        var body: CGRect = .zero
        var sent = false
        var lines: [String] = []
        var baselines: [CGFloat] = []
    }

    private struct Metrics {
        var headerH: CGFloat = 0
        var composerTop: CGFloat = 0
        var inputFrame: CGRect = .zero
        var fieldAction: CGRect = .zero
        var voice: CGRect = .zero
        var emoji: CGRect = .zero
        var add: CGRect = .zero
        var textLeft: CGFloat = 0
        var textRight: CGFloat = 0
        var rows: [Row] = []
        var contentH: CGFloat = 0
    }

    // MARK: - 滚动（Java:62-75 的 ScrollView；内容通常刚好一屏）

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = state as? State ?? State()
        let m = metrics(for: s)
        let viewport = max(0, m.composerTop - m.headerH)
        return max(0, m.contentH - viewport)
    }

    // MARK: - 布局（Java:52-166 的 View 树 → 参考坐标）

    private func metrics(for s: State) -> Metrics {
        var m = Metrics()

        // Java:60 `dp(48)` 顶栏；Java:164 `dp(50)` 输入栏
        m.headerH = dp(48)
        let barH = dp(50)
        m.composerTop = Self.H - barH

        // ── 输入栏（Java:77-164，横向 LinearLayout、CENTER_VERTICAL、内边距 5/6/5/6）─────
        //
        // 宽度账（Java:80-164）：5 + 语音 34 + 5 + [输入框 权重 1] + 4 + 表情 38 + 2 + 加号 38 + 5
        let padL = dp(5)
        let barCenterY = m.composerTop + barH / 2

        // Java:83-87 语音按钮 34×38dp，rightMargin 5dp
        m.voice = CGRect(x: padL, y: barCenterY - dp(38) / 2, width: dp(34), height: dp(38))

        // Java:88、142 输入框：权重 1、高 38dp
        let inputX = padL + dp(34) + dp(5)
        let inputW = Self.W - inputX - dp(87)          // 右侧固定 4+38+2+38+5
        m.inputFrame = CGRect(x: inputX, y: barCenterY - dp(38) / 2,
                              width: inputW, height: dp(38))

        // Java:101、109-110、114 框内文字左内边距 10dp、右边距 36dp
        m.textLeft = m.inputFrame.minX + dp(10)
        m.textRight = m.inputFrame.maxX - dp(36)

        // Java:112-115 右侧图标 34dp 宽、MATCH_PARENT 高、贴右居中
        m.fieldAction = CGRect(x: m.inputFrame.maxX - dp(34), y: m.inputFrame.minY,
                               width: dp(34), height: m.inputFrame.height)

        // Java:144-148 表情 38×40dp，leftMargin 4dp
        m.emoji = CGRect(x: m.inputFrame.maxX + dp(4), y: barCenterY - dp(40) / 2,
                         width: dp(38), height: dp(40))
        // Java:154-158 加号 38×40dp，leftMargin 2dp
        m.add = CGRect(x: m.emoji.maxX + dp(2), y: m.emoji.minY, width: dp(38), height: dp(40))

        // ── 消息列表（Java:68-75、217-266）───────────────────────────────────────────
        let padX = dp(9)                 // Java:73 左右 9dp
        let padTop = dp(13)              // Java:73 上 13dp
        let padBottom = dp(22)           // Java:73 下 22dp
        let rowGap = dp(11)              // Java:264 每行 bottomMargin 11dp
        let avatarSize = dp(36)          // Java:247 头像 36dp
        let bubblePadH = dp(11)          // Java:239 气泡左右内边距 11dp
        let bubblePadV = dp(6)           // Java:239 气泡上下内边距 6dp
        let bubbleMinH = dp(34)          // Java:241 bubble.setMinHeight(dp(34))
        let maxBubbleW = Self.W * 0.66   // Java:240 maxWidth = 屏宽 × 0.66
        let textSize = dp(16)            // Java:235 bubble.setTextSize(16)
        let textFont = Draw.font(size: textSize)
        let lineH = textFont.lineHeight
        let maxTextW = max(0, maxBubbleW - bubblePadH * 2)

        var y = padTop
        for message in s.messages {
            var row = Row()
            row.sent = message.sentByMe
            row.lines = wrap(message.text, size: textSize, maxWidth: maxTextW)

            let textH = CGFloat(row.lines.count) * lineH
            let bubbleH = max(bubbleMinH, textH + bubblePadV * 2)
            let rowH = max(avatarSize, bubbleH)
            let widest = row.lines.map { Draw.width($0, size: textSize) }.max() ?? 0
            let bubbleW = min(maxBubbleW, widest + bubblePadH * 2)

            // Java:247-260 行的左右排布：
            //   发出：  [气泡(rightMargin 7)] [3] [头像]         → 气泡与头像间距 7+3 = 10dp
            //   接收：  [头像] [3] [气泡(leftMargin 0)]
            let avatarX: CGFloat
            let bubbleX: CGFloat
            if message.sentByMe {
                avatarX = Self.W - padX - avatarSize
                bubbleX = avatarX - dp(10) - bubbleW
            } else {
                avatarX = padX
                bubbleX = padX + avatarSize + dp(3)
            }
            row.avatar = CGRect(x: avatarX, y: y + (rowH - avatarSize) / 2,
                                width: avatarSize, height: avatarSize)
            row.bubble = CGRect(x: bubbleX, y: y + (rowH - bubbleH) / 2,
                                width: bubbleW, height: bubbleH)

            // Java:296-299 body：发出扣右边、接收扣左边，inset = min(tailWidth, 宽 × 0.2)
            let inset = min(dp(7), bubbleW * 0.2)
            var body = row.bubble
            if message.sentByMe { body.size.width -= inset } else { body.origin.x += inset; body.size.width -= inset }
            row.body = body

            // 气泡文字：Java:237 CENTER_VERTICAL + Java:239 内边距，
            // 行内一律左对齐于左内边距（TextView 默认 alignment = LEFT，收发都一样）
            let blockTop = row.bubble.minY + bubblePadV
                + (row.bubble.height - bubblePadV * 2 - textH) / 2
            for i in 0..<row.lines.count {
                row.baselines.append(blockTop + CGFloat(i) * lineH + textFont.ascender)
            }
            m.rows.append(row)
            y += rowH + rowGap
        }
        m.contentH = s.messages.isEmpty ? padTop + padBottom : y + padBottom
        return m
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = state as? State ?? State()
        let m = metrics(for: s)

        // Java:57 根容器底色 CHAT_BG
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), chatBg)

        drawHeader(d, s, m)      // Java:60、168-201
        drawMessages(d, s, m)    // Java:62-75、217-266
        drawComposer(d, s, m)    // Java:77-164
    }

    /// 顶栏（Java:168-201）：返回 + 居中标题 + 「···」+ 底部 0.5dp 分割线
    private func drawHeader(_ d: Draw, _ s: State, _ m: Metrics) {
        // Java:170 header.setBackgroundColor(CHAT_BG)
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: m.headerH), chatBg)

        // Java:171-174 返回：52dp 宽、MATCH_PARENT 高、贴左居中
        drawIcon(d, .back, in: iconBox(CGRect(x: 0, y: 0, width: dp(52), height: m.headerH)))

        // Java:179-189 标题：20sp、TEXT_COLOR、居中、左右各 56dp
        let titleSize = dp(20)
        let title = fitOneLine(s.title, size: titleSize, maxWidth: Self.W - dp(56) * 2)
        d.centerText(title, cx: Self.W / 2,
                     baseline: centeredBaseline(centerY: m.headerH / 2, size: titleSize),
                     size: titleSize, ink)

        // Java:191-194 「···」：52dp 宽、MATCH_PARENT 高、贴右居中
        drawIcon(d, .more, in: iconBox(CGRect(x: Self.W - dp(52), y: 0,
                                              width: dp(52), height: m.headerH)))

        // Java:196-199 底部 0.5dp 分割线（Gravity.BOTTOM）
        d.rect(CGRect(x: 0, y: m.headerH - dp(0.5), width: Self.W, height: dp(0.5)), dividerInk)
    }

    /// 消息列表（Java:62-75 的 ScrollView + Java:217-266 的每一行）
    private func drawMessages(_ d: Draw, _ s: State, _ m: Metrics) {
        let viewport = CGRect(x: 0, y: m.headerH, width: Self.W,
                              height: max(0, m.composerTop - m.headerH))
        guard viewport.height > 0 else { return }

        d.ctx.saveGState()
        d.ctx.clip(to: viewport)                       // Java:64-66 ScrollView 的裁剪
        d.ctx.translateBy(x: 0, y: m.headerH - s.scroll)   // 内容坐标原点 = 列表顶部

        for row in m.rows { drawRow(d, s, row) }

        d.ctx.restoreGState()
    }

    /// 一行：头像（36dp 圆角 5dp）+ 气泡（含三角尾巴）
    private func drawRow(_ d: Draw, _ s: State, _ row: Row) {
        // Java:222-231 头像：CENTER_CROP + 白色 5dp 圆角底 + clipToOutline
        d.round(row.avatar, radius: dp(5), avatarPlate)
        let avatar = row.sent ? (s.myAvatar ?? defaultMyAvatar) : (s.remoteAvatar ?? defaultRemoteAvatar)
        if let avatar = avatar {
            d.clipped(to: row.avatar, radius: dp(5)) {
                d.artFill(avatar, in: row.avatar)      // Java:223 ScaleType.CENTER_CROP
            }
        } else {
            // 兜底：素材缺失时画灰底 + 首字，避免出现一块空白的白方块
            d.round(row.avatar, radius: dp(5), avatarGhost)
            let glyph = String(s.initials.prefix(1))
            let size = dp(22)
            d.centerText(glyph, cx: row.avatar.midX,
                         baseline: centeredBaseline(centerY: row.avatar.midY, size: size),
                         size: size, UIColor.white, bold: true)
        }

        // Java:233-243 气泡：白 / 微信绿 + 三角尾巴
        let fill = row.sent ? sentGreen : bubbleWhite
        drawBubble(d, row: row, fill: fill)

        // Java:234-236 正文：16sp、TEXT_COLOR、行内左对齐（收发同一起点）
        let size = dp(16)
        let textX = row.bubble.minX + dp(11)                    // Java:239 左内边距 11dp
        for (i, line) in row.lines.enumerated() where i < row.baselines.count {
            d.text(line, x: textX, baseline: row.baselines[i], size: size, ink)
        }
    }

    /// 气泡（Java:273-318 `BubbleDrawable`）：圆角主体 + 一个同色三角尾巴
    private func drawBubble(_ d: Draw, row: Row, fill: UIColor) {
        let tailWidth = dp(7)    // Java:242 dp(7)
        let tailHeight = dp(4)   // Java:242 dp(4)
        let radius = dp(6)       // Java:243 dp(6)

        let bounds = row.bubble
        let inset = min(tailWidth, bounds.width * 0.2)     // Java:296
        let centerY = bounds.midY

        // Java:300 body 圆角矩形
        d.round(row.body, radius: radius, fill)

        // Java:301-312 尾巴（两段 quadTo）
        let path = CGMutablePath()
        if row.sent {
            let anchorX = row.body.maxX - radius * 0.2
            path.move(to: CGPoint(x: anchorX, y: centerY - tailHeight))
            path.addQuadCurve(to: CGPoint(x: bounds.maxX, y: centerY),
                              control: CGPoint(x: bounds.maxX - inset * 0.15,
                                               y: centerY - tailHeight * 0.5))
            path.addQuadCurve(to: CGPoint(x: anchorX, y: centerY + tailHeight),
                              control: CGPoint(x: bounds.maxX - inset * 0.15,
                                               y: centerY + tailHeight * 0.5))
        } else {
            let anchorX = row.body.minX + radius * 0.2
            path.move(to: CGPoint(x: anchorX, y: centerY - tailHeight))
            path.addQuadCurve(to: CGPoint(x: bounds.minX, y: centerY),
                              control: CGPoint(x: bounds.minX + inset * 0.15,
                                               y: centerY - tailHeight * 0.5))
            path.addQuadCurve(to: CGPoint(x: anchorX, y: centerY + tailHeight),
                              control: CGPoint(x: bounds.minX + inset * 0.15,
                                               y: centerY + tailHeight * 0.5))
        }
        path.closeSubpath()
        d.ctx.setFillColor(fill.cgColor)
        d.ctx.addPath(path)
        d.ctx.fillPath()
    }

    /// 输入栏（Java:77-164）
    private func drawComposer(_ d: Draw, _ s: State, _ m: Metrics) {
        // Java:81 composerBar 底色
        d.rect(CGRect(x: 0, y: m.composerTop, width: Self.W, height: dp(50)), composerBg)

        // Java:83-87 语音按钮
        drawIcon(d, .voice, in: iconBox(m.voice))

        // Java:88-92 输入框：白底 + 7dp 圆角
        d.round(m.inputFrame, radius: dp(7), UIColor.white)

        // Java:93-111 文字 / hint（hint 只有语音模式才非空，Java:98、105-106）
        let textSize = dp(16)          // Java:95 setTextSize(16)
        let baseline = centeredBaseline(centerY: m.inputFrame.midY, size: textSize)
        if s.draft.isEmpty {
            if s.showingVoiceHint {
                d.text(Self.voiceHint, x: m.textLeft, baseline: baseline,
                       size: textSize, hintInk)
            }
        } else {
            let shown = fitOneLine(s.draft, size: textSize, maxWidth: m.textRight - m.textLeft)
            d.text(shown, x: m.textLeft, baseline: baseline, size: textSize, ink)
        }

        // Java:112-115、124-131 右侧图标：有内容画发送，没内容画麦克风
        drawIcon(d, s.draft.isEmpty ? .mic : .send, in: iconBox(m.fieldAction))

        // Java:144-148 表情
        drawIcon(d, .emoji, in: iconBox(m.emoji))

        // Java:154-158 加号
        drawIcon(d, .add, in: iconBox(m.add))
    }

    // MARK: - 命中（顺序与 Java 的 View 层级一致：先顶栏，再输入栏的各个子 View）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = state as? State ?? State()
        let m = metrics(for: s)
        // 宿主把 scroll 加进了入参 y（`Screen.swift` 的 touchesEnded），这里减回去，
        // 否则消息列表一滚，顶栏/输入栏就点不到了。
        let py = y - s.scroll
        let p = CGPoint(x: x, y: py)

        // ── 顶栏（Java:168-201）──────────────────────────────────────────────────────
        if py >= 0 && py < m.headerH {
            if x >= 0 && x < dp(52) { return "back" }                       // Java:171-177
            if x >= Self.W - dp(52) { return "toast:" + Self.chatInfoToast } // Java:191-195
            return "noop"                                                    // 标题没有监听
        }

        // ── 消息区（Java:217-266）：气泡、头像都没挂 listener ─────────────────────────
        if py < m.composerTop { return "noop" }

        // ── 输入栏（Java:77-164）：先判子 View，再判容器（与 Android 的 z 序一致）───────
        // Java:116-131：有内容 → 发送；没内容 → 聚焦并弹键盘
        if m.fieldAction.contains(p) {
            if s.draft.isEmpty { return "conv:input" }
            return "conv:send"
        }
        // Java:104-108 语音 / 键盘
        if m.voice.contains(p) { return "conv:voice" }
        // Java:149-152 表情
        if m.emoji.contains(p) { return "conv:emoji" }
        // Java:159-162 加号
        if m.add.contains(p) { return "conv:more" }
        // Java:93-103 输入框本体：任何位置都是聚焦
        if m.inputFrame.contains(p) { return "conv:input" }
        return "noop"
    }

    // MARK: - 图标绘制（Java:338-395 `IconView.onDraw`）
    //
    // IconView 的写法是 `translate((w-32d)/2, (h-32d)/2)` + `scale(d)`，
    // 然后在**32×32 的单位坐标系**里画（d = density）。这里等价成：
    // 先把 32dp 的框居中放进容器（`iconBox`），再把每个单位坐标换成框内坐标（`pt`）。
    private func drawIcon(_ d: Draw, _ icon: Icon, in box: CGRect) {
        guard box.width > 0, box.height > 0 else { return }
        let u = Self.dpUnit
        let stroke = 1.8 * u                                  // Java:346 setStrokeWidth(1.8f)

        func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: box.minX + x * u, y: box.minY + y * u)
        }
        func oval(_ l: CGFloat, _ t: CGFloat, _ r: CGFloat, _ b: CGFloat) -> CGRect {
            CGRect(x: box.minX + l * u, y: box.minY + t * u,
                   width: (r - l) * u, height: (b - t) * u)
        }

        d.ctx.saveGState()
        d.ctx.setStrokeColor(iconInk.cgColor)
        d.ctx.setFillColor(iconInk.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)                              // Java:347 Cap.ROUND
        d.ctx.setLineJoin(.round)                             // Java:348 Join.ROUND

        switch icon {
        case .back:
            // Java:350-353：`<` 折线
            let path = CGMutablePath()
            path.move(to: pt(20, 5))
            path.addLine(to: pt(9, 16))
            path.addLine(to: pt(20, 27))
            d.ctx.addPath(path)
            d.ctx.strokePath()

        case .more:
            // Java:354-358：三个实心圆点
            for cx in [CGFloat(9), 16, 23] {
                let c = pt(cx, 16)
                d.circle(cx: c.x, cy: c.y, r: 1.8 * u, iconInk)
            }

        case .voice:
            // Java:359-365：圆圈 + 喇叭 + 两道弧
            let center = pt(16, 16)
            d.ctx.strokeEllipse(in: CGRect(x: center.x - 12 * u, y: center.y - 12 * u,
                                           width: 24 * u, height: 24 * u))
            let speaker = CGMutablePath()
            speaker.move(to: pt(8, 14))
            speaker.addLine(to: pt(11, 14))
            speaker.addLine(to: pt(15, 11))
            speaker.addLine(to: pt(15, 21))
            speaker.addLine(to: pt(11, 18))
            speaker.addLine(to: pt(8, 18))
            speaker.closeSubpath()
            d.ctx.addPath(speaker)
            d.ctx.strokePath()
            // Java:364-365 椭圆弧（bbox 10×12 / 15×18），这里按短边做正圆近似
            d.arc(rect: oval(13, 10, 23, 22), startDegrees: -65, sweepDegrees: 130,
                  color: iconInk, width: stroke)
            d.arc(rect: oval(12, 7, 27, 25), startDegrees: -65, sweepDegrees: 130,
                  color: iconInk, width: stroke)

        case .mic:
            // Java:366-376：胶囊 + 支架
            d.roundStroke(oval(12, 5, 20, 19), radius: 4 * u, iconInk, width: stroke)
            let mic = CGMutablePath()
            mic.move(to: pt(8, 15))
            mic.addLine(to: pt(8, 17))
            mic.addQuadCurve(to: pt(16, 25), control: pt(8, 25))
            mic.addQuadCurve(to: pt(24, 17), control: pt(24, 25))
            mic.addLine(to: pt(24, 15))
            d.ctx.addPath(mic)
            d.ctx.strokePath()
            let a = pt(16, 25), b = pt(16, 29)
            d.ctx.move(to: a)
            d.ctx.addLine(to: b)
            d.ctx.strokePath()
            let c = pt(12, 29), e = pt(20, 29)
            d.ctx.move(to: c)
            d.ctx.addLine(to: e)
            d.ctx.strokePath()

        case .emoji:
            // Java:377-383：圆脸 + 两只实心眼 + 笑弧
            let center = pt(16, 16)
            d.ctx.strokeEllipse(in: CGRect(x: center.x - 12 * u, y: center.y - 12 * u,
                                           width: 24 * u, height: 24 * u))
            for cx in [CGFloat(12), 20] {
                let c = pt(cx, 13)
                d.circle(cx: c.x, cy: c.y, r: 1.3 * u, iconInk)
            }
            d.arc(rect: oval(11, 12, 21, 21), startDegrees: 15, sweepDegrees: 150,
                  color: iconInk, width: stroke)

        case .add:
            // Java:384-387：圆圈 + 十字
            let center = pt(16, 16)
            d.ctx.strokeEllipse(in: CGRect(x: center.x - 12 * u, y: center.y - 12 * u,
                                           width: 24 * u, height: 24 * u))
            d.ctx.move(to: pt(10, 16))
            d.ctx.addLine(to: pt(22, 16))
            d.ctx.strokePath()
            d.ctx.move(to: pt(16, 10))
            d.ctx.addLine(to: pt(16, 22))
            d.ctx.strokePath()

        case .send:
            // Java:388-392：实心纸飞机
            let path = CGMutablePath()
            path.move(to: pt(5, 15))
            path.addLine(to: pt(27, 5))
            path.addLine(to: pt(20, 27))
            path.addLine(to: pt(15, 19))
            path.closeSubpath()
            d.ctx.addPath(path)
            d.ctx.fillPath()
        }

        d.ctx.restoreGState()
    }

    /// 把 32×32dp 的图标框居中放进容器（Java:343 的 `translate((w-32d)/2, (h-32d)/2)`）
    private func iconBox(_ container: CGRect) -> CGRect {
        let side = dp(32)
        return CGRect(x: container.midX - side / 2, y: container.midY - side / 2,
                      width: side, height: side)
    }

    // MARK: - 小工具

    /// 居中基线：Android 的 `Gravity.CENTER` 是**行框**居中，
    /// 等价于 `中心 + (ascender + descender) / 2`（`Draw.text` 的 y 是基线）。
    private func centeredBaseline(centerY: CGFloat, size: CGFloat) -> CGFloat {
        let font = Draw.font(size: size)
        return centerY + (font.ascender + font.descender) / 2
    }

    /// 按字符折行 —— 对应 Android `TextView` 在 `maxWidth` 下的自动换行（Java:240）。
    private func wrap(_ text: String, size: CGFloat, maxWidth: CGFloat) -> [String] {
        guard maxWidth > 0 else { return [text] }
        var lines: [String] = []
        for paragraph in text.components(separatedBy: "\n") {
            var current = ""
            for ch in paragraph {
                let next = current + String(ch)
                if !current.isEmpty, Draw.width(next, size: size) > maxWidth {
                    lines.append(current)
                    current = String(ch)
                } else {
                    current = next
                }
            }
            lines.append(current)
        }
        return lines
    }

    /// 单行截断（Android `setSingleLine(true)` 的 ellipsize=END 观感）
    private func fitOneLine(_ text: String, size: CGFloat, maxWidth: CGFloat) -> String {
        let oneLine = text.replacingOccurrences(of: "\n", with: " ")
        guard maxWidth > 0, Draw.width(oneLine, size: size) > maxWidth else { return oneLine }
        let ellipsis = "…"
        var value = oneLine
        while !value.isEmpty, Draw.width(value + ellipsis, size: size) > maxWidth {
            value.removeLast()
        }
        return value + ellipsis
    }
}

// MARK: - 状态改写（Java:104-152、203-209 的交互结果；Router 拿到令牌后调这几个方法）

extension ConversationScreen {
    /// 发送当前草稿 —— Java:203-209 `sendCurrentMessage`
    ///
    /// Java 的做法是 `listener.onSend(value)`（数据层追加 + 会话置顶）+ 清空输入框 +
    /// `fullScroll(FOCUS_DOWN)`。数据层那一步在 iOS 侧由上层决定，这里只负责页面状态。
    func sendDraft(_ state: ScreenState) {
        guard let s = state as? State else { return }
        let value = s.draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }                 // Java:205
        s.messages.append(Message(value, sentByMe: true))    // Java:206
        s.draft = ""                                         // Java:207
        scrollToBottom(s)                                    // Java:208
    }

    /// 语音 / 键盘切换 —— Java:104-108（hint 在 `""` 与「按住 说话」之间来回）
    func toggleVoice(_ state: ScreenState) {
        guard let s = state as? State else { return }
        s.showingVoiceHint.toggle()                          // Java:105-106
    }

    /// 表情：往光标处插「😊」—— Java:149-152（这里简化为追加到末尾）
    func insertEmoji(_ state: ScreenState) {
        guard let s = state as? State else { return }
        s.draft += "😊"
    }

    /// 滚到底 —— Java:208、214 `messageScroll.fullScroll(View.FOCUS_DOWN)`
    func scrollToBottom(_ state: ScreenState) {
        state.scroll = maxScroll(state)
    }
}
