import UIKit

/// 「模拟来电」设置页 —— `features/call/CallSetupActivity.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// ⚠️ 先说要紧的一点：Java 这一页**不是 Canvas 绘制**，而是一棵 Android View 树
/// （FrameLayout → ScrollView → LinearLayout，所有尺寸都走 `dp(v) = v × density`）。
/// 所以源码里没有 `W` / `H` 常量可抄，画布按任务约定取 **1280×2816**；dp → 画布像素的
/// 换算收敛在 `dpUnit` 里：`1280 ÷ 360 ≈ 3.5556`。取 360 的理由：那是 Android 端最常见的
/// 排版宽度（1080px ÷ density 3 = 360dp），于是 1280×2816 恰好等于 **360×792dp** 的一块版面，
/// Java `buildUi()` 里的 16 / 14 / 24 / 72 这些 dp 数字可以原样写进来，注释只用标行号。
///
/// 布局也照 Java 抄：区块顺序、topMargin、padding、圆角、字号逐条对应 `buildUi()`，
/// 只是把「LinearLayout 自动排版」换成这里显式的 `layout(_:)` —— 每次 draw / hitTest 都重算一遍，
/// 因为版面会随 State 变（待触发条有 / 没有，勾选在哪一行）。
///
/// 三处「Java 里不是本页负责」的东西：
///   1. **数据**在 `CallStore`（每账号一套：名字、头像、铃声、时间档位）、`CallContacts`
///      （通讯录，兜底 5 个人）、`CallScheduler`（待响的闹钟）里，iOS 侧这三个都还不存在，
///      所以由 `State` 承载，宿主接这一屏时从这里读、往这里写即可；
///   2. **滚动**由外层 ScrollView 承担（`selfScrolls = false`），交给 `ScreenHostView` 统一滚 ——
///      所以底色要自己铺满内容高度，否则滚到底会露白；
///   3. **文件选择 / 播放 / 闹钟**都是原生侧的事，这一屏只把动作令牌吐出去（令牌见文件末尾）。
///
/// 滚动区里的「联系人」那一排：Android 是 HorizontalScrollView，可以横着滑。这里把它裁到
/// 内容宽度以内、不做横滑（宿主虽有 `carouselDrag` 钩子，但 Java 这排是自由横滑、没有吸附，
/// 而且宿主首次判定时会把同一个 dx 传两次）—— 见末尾「没做的地方」。
final class CallSetupScreen: Screen {
    /// 参考画布：Java 没有 W/H，按约定取 1280×2816（= 360dp × 792dp）
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// 滚动由宿主的 ScrollView 等价物承担，`draw` 里不自己 translate（Java:122-131）
    let selfScrolls = false

    /// 1dp 在参考画布上的像素数（1280 ÷ 360；见文件头）
    private let dpUnit: CGFloat = 1280.0 / 360.0

    // MARK: - 调色板（Java:56-60、120、166、170、215）

    private let brand = Draw.color(0xff6f6ae8)              // Java:56  BRAND
    private let ink = Draw.color(0xff23232f)                // Java:57  INK
    private let sub = Draw.color(0xff8a8a9a)                // Java:58  SUB
    private let card = Draw.color(0xffffffff)               // Java:59  CARD
    private let line = Draw.color(0xffe7e7f0)               // Java:60  LINE
    private let pageBg = Draw.color(0xfff5f6fa)             // Java:120 根布局底色
    private let pendingBg = Draw.color(0xfffff4e5)          // Java:166 待触发条底色
    private let pendingInk = Draw.color(0xff9a6a16)         // Java:170 待触发条文字
    private let avatarPlaceholder = Draw.color(0xffededf3)  // Java:215 头像框底色
    /// EditText 的 hint 灰（Java:229 只写了 `setHint`，颜色是主题默认值，取 Material 的 #9E9E9E）
    private let hintGray = Draw.color(0xff9e9e9e)

    // MARK: - 状态（Java 里散在 CallStore / CallContacts / CallScheduler）

    /// 一个可选的联系人（Java 的 `CallContact`：id / 显示名 / 头像地址）。
    struct Contact {
        let id: String
        let name: String
        /// 头像图片名。Android 用 assets 里那 7 张 `user-avatar-*` 切图（CallImages.AVATAR_POOL），
        /// iOS 端没有这批图：为空时退化成「圆 + 首字」占位，布局与命中都不受影响。
        var imageName: String? = nil
        /// 从微信通讯录同步来的头像；有值时优先于 imageName。
        var avatar: UIImage? = nil
    }

    /// 兜底通讯录（CallContacts.java:32-38 的 DEFAULTS）：一次都没从页面同步过就用这五位
    static let fallbackContacts: [Contact] = [
        Contact(id: "link", name: "林克"),
        Contact(id: "zelda", name: "老婆"),
        Contact(id: "kirby", name: "星之笨比"),
        Contact(id: "mario", name: "大叔"),
        Contact(id: "doutu", name: "微信用户"),
    ]

    /// 时间三选一（CallStore.MODE_NOW / MODE_DELAY / MODE_AT，CallStore:45）
    static let modeNow = 0
    static let modeDelay = 1
    static let modeAt = 2

    final class State: ScreenState {
        /// 来电人名称（CallStore.name()，Java:390 用它初始化输入框）
        var name = ""
        /// 通讯录那一排（CallContacts.cached()）
        var contacts: [Contact] = CallSetupScreen.fallbackContacts
        /// 头像预览那张图（CallStore.avatarFile() 优先，其次 avatarUrl()，Java:572-588）
        var avatarImage: UIImage?
        /// 本地铃声的显示名；空 = 用内置铃声（CallStore.ringtoneLabel()）
        var ringtoneLabel = ""
        /// 「试听」是否正在放（Java:75 `previewing`）
        var previewing = false
        /// 延迟分钟数（CallStore.delayMinutes()）
        var delayMinutes = 1
        /// 时间档位（CallStore.timeMode()）
        var timeMode = CallSetupScreen.modeNow
        /// 指定时间的小时；-1 表示还没选过（CallStore.atHour()）
        var atHour = -1
        var atMinute = 0
        /// 待触发的来电时刻；nil = 没有（Java:699-708 的 GONE / VISIBLE）
        var pendingAt: Date?
    }

    // MARK: - 画布换算（Java:101-103 的 dp()）

    /// Java `dp(v)`：1dp → 画布像素
    private func dp(_ value: CGFloat) -> CGFloat { value * dpUnit }

    /// 单行文字的行框高度 ≈ 字号 × 1.4。
    ///
    /// Android TextView 的高度是字体上下沿加 includeFontPadding：Roboto 的 ascent 0.927 +
    /// descent 0.244 = 1.171，再算上默认的上下 padding 约 0.2，取 1.4 好算也够准。
    private func lineBox(_ size: CGFloat) -> CGFloat { dp(size * 1.4) }

    /// 行框顶 → 文字基线：字号 × 1.06（ascent 0.927 + includeFontPadding 的顶约 0.13）。
    /// `top` 是画布坐标，`size` 是 dp 字号。
    private func base(_ top: CGFloat, _ size: CGFloat) -> CGFloat { top + dp(size * 1.06) }

    /// 竖直居中时的基线：中心 + 字号 × 0.36（近似大写字母高的一半）
    private func centerBase(_ cy: CGFloat, _ size: CGFloat) -> CGFloat { cy + dp(size * 0.36) }

    // MARK: - 版面

    /// 联系人在那一排里的落位
    private struct ContactSlot {
        let contact: Contact
        let item: CGRect
        let avatar: CGRect
        let label: CGRect
    }

    /// 一屏的版面（全部是参考画布坐标；draw 与 hitTest 共用同一份，保证「画在哪就点在哪」）
    private struct Layout {
        var contentH: CGFloat = 0
        var back = CGRect.zero
        var title = CGRect.zero
        var hint = CGRect.zero
        var pending = CGRect.zero
        var pendingText = CGRect.zero
        var pendingCancel = CGRect.zero
        var callerTitle = CGRect.zero
        var contactClip = CGRect.zero
        var contactItems: [ContactSlot] = []
        var profileTitle = CGRect.zero
        var profileCard = CGRect.zero
        var avatarPreview = CGRect.zero
        var nameField = CGRect.zero
        var avatarHint = CGRect.zero
        var ringTitle = CGRect.zero
        var ringCard = CGRect.zero
        var ringDefaultRow = CGRect.zero
        var ringDivider1 = CGRect.zero
        var ringLocalRow = CGRect.zero
        var ringLocalTitle = CGRect.zero
        var ringLocalLabel = CGRect.zero
        var ringPick = CGRect.zero
        var ringListen = CGRect.zero
        var whenTitle = CGRect.zero
        var whenCard = CGRect.zero
        var whenNowRow = CGRect.zero
        var whenDivider1 = CGRect.zero
        var whenDelayRow = CGRect.zero
        var whenDelayTitle = CGRect.zero
        var whenDelayField = CGRect.zero
        var whenDelayUnit = CGRect.zero
        var whenDivider2 = CGRect.zero
        var whenAtRow = CGRect.zero
        var whenAtTitle = CGRect.zero
        var whenAtField = CGRect.zero
        var start = CGRect.zero
    }

    /// 照着 Java `buildUi()` 从上往下摞一遍（Java:118-395）
    private func layout(_ s: State) -> Layout {
        var out = Layout()
        let left = dp(16)                              // Java:129 paddingLeft
        let right = Self.W - dp(16)                    // Java:129 paddingRight
        let width = right - left
        var y = dp(14)                                 // Java:129 paddingTop

        // ── 标题栏（Java:134-152）：横向 LinearLayout，CENTER_VERTICAL ──────────
        let backTextW = Draw.width("‹ 返回", size: dp(16))                 // Java:140-141
        let barH = max(lineBox(16) + dp(12), lineBox(20))                   // back 的 padding 6+6（Java:143）
        out.back = CGRect(x: left, y: y + dp(6), width: backTextW, height: lineBox(16))
        out.title = CGRect(x: left + backTextW + dp(10),                    // Java:143 paddingRight 10
                           y: y + (barH - lineBox(20)) / 2,
                           width: Draw.width("模拟来电", size: dp(20), bold: true),
                           height: lineBox(20))                             // Java:147-151
        y += barH

        // ── 副标题（Java:154-160）：13sp、topMargin 4 ─────────────────────────
        y += dp(4)                                                          // Java:159
        out.hint = CGRect(x: left, y: y, width: width, height: lineBox(13))
        y += lineBox(13)

        // ── 待触发的来电（Java:163-186）：没有就整条 GONE，不占高度 ────────────
        if s.pendingAt != nil {
            y += dp(12)                                                     // Java:185 topMargin 12
            let cancelW = Draw.width("取消", size: dp(13)) + dp(10) + dp(6)  // Java:177 padding 10/6
            let cancelH = lineBox(13) + dp(4) + dp(4)                        // Java:177 padding 4/4
            let barH2 = dp(10) + max(lineBox(13), cancelH) + dp(10)          // Java:167 padding 10/10
            out.pending = CGRect(x: left, y: y, width: width, height: barH2)
            out.pendingText = CGRect(x: left + dp(14),                       // Java:167 paddingLeft 14
                                     y: y + (barH2 - lineBox(13)) / 2,
                                     width: width - dp(14) - dp(10) - cancelW,
                                     height: lineBox(13))
            out.pendingCancel = CGRect(x: right - dp(10) - cancelW,
                                       y: y + (barH2 - cancelH) / 2,
                                       width: cancelW, height: cancelH)
            y += barH2
        }

        // ── 1. 来电人（Java:189-199）─────────────────────────────────────────
        y += dp(16)                                                         // Java:189 topMargin 16
        out.callerTitle = CGRect(x: left, y: y, width: width, height: lineBox(14))
        y += lineBox(14)
        y += dp(8)                                                          // Java:198 topMargin 8

        // 每一项：padding 6，头像 56，名字 topMargin 6、宽 64（Java:537-556），项右 margin 4（Java:567）
        let itemW = dp(6) + dp(64) + dp(6)
        let itemH = dp(4) + dp(56) + dp(6) + lineBox(12) + dp(4)
        out.contactClip = CGRect(x: left, y: y, width: width, height: itemH)
        var cx = left
        for contact in s.contacts {
            let item = CGRect(x: cx, y: y, width: itemW, height: itemH)
            // 头像在 64 宽的内容里水平居中（Java:536-543，CENTER_HORIZONTAL）
            let avatar = CGRect(x: cx + dp(6) + dp(4), y: y + dp(4),
                                width: dp(56), height: dp(56))
            let label = CGRect(x: cx + dp(6), y: avatar.maxY + dp(6),
                               width: dp(64), height: lineBox(12))
            out.contactItems.append(ContactSlot(contact: contact, item: item,
                                                avatar: avatar, label: label))
            cx += itemW + dp(4)
        }
        y += itemH

        // ── 2. 头像 + 名称（Java:202-239）────────────────────────────────────
        y += dp(18)                                                         // Java:202 topMargin 18
        out.profileTitle = CGRect(x: left, y: y, width: width, height: lineBox(14))
        y += lineBox(14)
        y += dp(8)                                                          // Java:210 topMargin 8

        let profilePad = dp(14)                                             // Java:208 padding 14
        let profileH = profilePad + max(dp(72), lineBox(17) + lineBox(12)) + profilePad
        out.profileCard = CGRect(x: left, y: y, width: width, height: profileH)   // Java:207 圆角 14
        out.avatarPreview = CGRect(x: left + profilePad, y: y + profilePad,
                                   width: dp(72), height: dp(72))            // Java:217
        let textX = out.avatarPreview.maxX + dp(14)                          // Java:223 leftMargin 14
        let textW = right - profilePad - textX
        // profileText 是 VERTICAL、整块被 CENTER_VERTICAL 居中（Java:206、219-224）
        let textTop = y + profilePad + (dp(72) - (lineBox(17) + lineBox(12))) / 2
        out.nameField = CGRect(x: textX, y: textTop, width: textW, height: lineBox(17))
        out.avatarHint = CGRect(x: textX, y: out.nameField.maxY,
                                width: textW, height: lineBox(12))           // Java:235-239
        y += profileH

        // ── 3. 来电铃声（Java:242-302）───────────────────────────────────────
        y += dp(18)                                                         // Java:242 topMargin 18
        out.ringTitle = CGRect(x: left, y: y, width: width, height: lineBox(14))
        y += lineBox(14)
        y += dp(8)                                                          // Java:249 topMargin 8

        let cardPadX = dp(14)                                               // Java:247 paddingLeft/Right
        let cardPadY = dp(6)                                                // Java:247 paddingTop/Bottom
        let innerL = left + cardPadX
        let innerR = right - cardPadX
        let innerW = innerR - innerL
        let ringTop = y
        y += cardPadY

        // 「默认铃声（应用内置）」：一整行可点（Java:253-256 → optionRow Java:433-463）
        let optionH = dp(12) + lineBox(15) + lineBox(12) + dp(12)            // Java:437 padding 12/12
        out.ringDefaultRow = CGRect(x: innerL, y: y, width: innerW, height: optionH)
        y += optionH

        // 分割线（Java:257-260）：高度 max(1, dp(0.7))
        out.ringDivider1 = CGRect(x: innerL, y: y, width: innerW, height: max(1, dp(0.7)))
        y += out.ringDivider1.height

        // 「本地音频」那一行（Java:262-302）：最左是勾（prepareCustomRow Java:483-492），
        // 中间标题 + 副标题，右边「选择」「试听」两个描边胶囊
        let localTextH = lineBox(15) + lineBox(12)
        let localContentH = max(localTextH, dp(36))
        let localH = dp(12) + localContentH + dp(12)                         // Java:265 padding 12/12
        out.ringLocalRow = CGRect(x: innerL, y: y, width: innerW, height: localH)
        let localCy = y + localH / 2
        out.ringListen = CGRect(x: innerR - dp(64), y: localCy - dp(18),
                                width: dp(64), height: dp(36))               // Java:289、298
        out.ringPick = CGRect(x: out.ringListen.minX - dp(8) - dp(64), y: localCy - dp(18),
                              width: dp(64), height: dp(36))                 // Java:289、299
        let localTextX = innerL + dp(20)                                     // 勾的 minWidth 20（Java:489）
        let localTextW = out.ringPick.minX - localTextX
        out.ringLocalTitle = CGRect(x: localTextX,
                                    y: localCy - localTextH / 2,
                                    width: localTextW, height: lineBox(15))  // Java:272-276
        out.ringLocalLabel = CGRect(x: localTextX,
                                    y: out.ringLocalTitle.maxY,
                                    width: localTextW, height: lineBox(12))  // Java:277-280
        y += localH
        out.ringCard = CGRect(x: left, y: ringTop, width: width,
                              height: y - ringTop + cardPadY)                // Java:246 圆角 14
        y += cardPadY

        // ── 4. 什么时候响（Java:305-373）────────────────────────────────────
        y += dp(18)                                                         // Java:305 topMargin 18
        out.whenTitle = CGRect(x: left, y: y, width: width, height: lineBox(14))
        y += lineBox(14)
        y += dp(8)                                                          // Java:312 topMargin 8

        let whenTop = y
        y += cardPadY

        // 「立即」（Java:316-320）
        out.whenNowRow = CGRect(x: innerL, y: y, width: innerW, height: optionH)
        y += optionH
        out.whenDivider1 = CGRect(x: innerL, y: y, width: innerW, height: max(1, dp(0.7)))
        y += out.whenDivider1.height

        // 「多少分钟之后」（Java:323-353）：行 padding 10/10；数字格 56 宽、单位 13sp
        let delayRowH = dp(10) + max(lineBox(15), lineBox(13)) + dp(10)      // Java:326 padding 10/10
        out.whenDelayRow = CGRect(x: innerL, y: y, width: innerW, height: delayRowH)
        let delayCy = y + delayRowH / 2
        let unitW = Draw.width("分钟后响", size: dp(13))                     // Java:348
        out.whenDelayUnit = CGRect(x: innerR - unitW, y: delayCy - lineBox(13) / 2,
                                   width: unitW, height: lineBox(13))
        out.whenDelayField = CGRect(x: out.whenDelayUnit.minX - dp(56),      // Java:345 宽 56
                                    y: delayCy - lineBox(15) / 2,
                                    width: dp(56), height: lineBox(15))
        out.whenDelayTitle = CGRect(x: innerL + dp(20),                      // 勾 minWidth 20
                                    y: delayCy - lineBox(15) / 2,
                                    width: out.whenDelayField.minX - (innerL + dp(20)),
                                    height: lineBox(15))                    // Java:328-333
        y += delayRowH
        out.whenDivider2 = CGRect(x: innerL, y: y, width: innerW, height: max(1, dp(0.7)))
        y += out.whenDivider2.height

        // 「指定时间」（Java:355-373）：右边一个描边胶囊，宽 = 文字 + padding 10/10
        let atText = Self.atText(s)
        let atFieldW = Draw.width(atText, size: dp(15)) + dp(10) + dp(10)    // Java:369 padding 10/10
        let atFieldH = lineBox(15) + dp(6) + dp(6)                           // Java:369 padding 6/6
        let atRowH = dp(10) + max(lineBox(15), atFieldH) + dp(10)            // Java:358 padding 10/10
        out.whenAtRow = CGRect(x: innerL, y: y, width: innerW, height: atRowH)
        out.whenAtField = CGRect(x: innerR - atFieldW, y: y + (atRowH - atFieldH) / 2,
                                 width: atFieldW, height: atFieldH)          // Java:366-372
        out.whenAtTitle = CGRect(x: innerL + dp(20),
                                 y: y + (atRowH - lineBox(15)) / 2,
                                 width: out.whenAtField.minX - (innerL + dp(20)),
                                 height: lineBox(15))                        // Java:360-365
        y += atRowH
        out.whenCard = CGRect(x: left, y: whenTop, width: width,
                              height: y - whenTop + cardPadY)                // Java:309 圆角 14
        y += cardPadY

        // ── 开始来电（Java:376-386）：MATCH_PARENT × 50dp，topMargin 22 ───────
        y += dp(22)                                                         // Java:384
        out.start = CGRect(x: left, y: y, width: width, height: dp(50))       // Java:382-383
        y += dp(50)

        out.contentH = y + dp(24)                                           // Java:129 paddingBottom 24
        return out
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return max(0, layout(s).contentH - Self.H)
    }

    // MARK: - 绘制（Java:118-395 的 buildUi 视图树）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let m = layout(s)

        // 底色（Java:120）：这一页由外层滚动，所以底色得铺满内容高度，否则滚到底会露白
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: max(Self.H, m.contentH)), pageBg)

        // ── 标题栏（Java:134-152）─────────────────────────────────────────
        d.text("‹ 返回", x: m.back.minX, baseline: base(m.back.minY, 16),
               size: dp(16), ink)                                            // Java:140-142
        d.text("模拟来电", x: m.title.minX, baseline: base(m.title.minY, 20),
               size: dp(20), ink, bold: true)                                // Java:148-151

        // ── 副标题（Java:154-160）─────────────────────────────────────────
        d.text("设好来电人和时间，到点了它会自己响起来。",
               x: m.hint.minX, baseline: base(m.hint.minY, 13), size: dp(13), sub)

        // ── 待触发的来电（Java:163-186）────────────────────────────────────
        if let at = s.pendingAt {
            d.round(m.pending, radius: dp(12), pendingBg)                    // Java:166
            let label = ellipsize(Self.pendingLabel(at), size: dp(13),
                                  maxWidth: m.pendingText.width)
            d.text(label, x: m.pendingText.minX, baseline: base(m.pendingText.minY, 13),
                   size: dp(13), pendingInk)                                 // Java:170、706-707
            d.text("取消", x: m.pendingCancel.minX + dp(10),
                   baseline: base(m.pendingCancel.minY + dp(4), 13),
                   size: dp(13), brand)                                      // Java:174-177
        }

        // ── 1. 来电人（Java:189-199、529-570）──────────────────────────────
        d.text("来电人（从通讯录选择）", x: m.callerTitle.minX,
               baseline: base(m.callerTitle.minY, 14), size: dp(14), ink, bold: true)

        d.ctx.saveGState()
        d.ctx.clip(to: m.contactClip)
        for slot in m.contactItems {
            let selected = slot.contact.name == s.name                       // Java:533
            drawContactAvatar(d, slot, selected: selected)
            let color = selected ? brand : ink                               // Java:549
            let text = ellipsize(slot.contact.name, size: dp(12), maxWidth: slot.label.width)
            d.centerText(text, cx: slot.label.midX,
                         baseline: base(slot.label.minY, 12), size: dp(12), color)   // Java:547-555
        }
        d.ctx.restoreGState()

        // ── 2. 头像 + 名称（Java:202-239）─────────────────────────────────
        d.text("头像与名称", x: m.profileTitle.minX,
               baseline: base(m.profileTitle.minY, 14), size: dp(14), ink, bold: true)
        d.round(m.profileCard, radius: dp(14), card)                         // Java:207
        d.round(m.avatarPreview, radius: dp(16), avatarPlaceholder)          // Java:215
        if let image = s.avatarImage {
            // 注意：Java 只把**背景**做成圆角，ImageView 自己不裁剪（CENTER_CROP 铺满方框），
            // 所以这里也照原样画方的 —— 别顺手加圆角，那样就和 Android 的观感对不上了
            d.artFill(image, in: m.avatarPreview)                            // Java:577-583
        }
        if s.name.isEmpty {
            // EditText 的 hint（Java:229）
            d.text("来电人名称", x: m.nameField.minX, baseline: base(m.nameField.minY, 17),
                   size: dp(17), hintGray)
        } else {
            d.text(s.name, x: m.nameField.minX, baseline: base(m.nameField.minY, 17),
                   size: dp(17), ink)                                        // Java:230-232
        }
        d.text("点头像可以从相册换一张", x: m.avatarHint.minX,
               baseline: base(m.avatarHint.minY, 12), size: dp(12), sub)      // Java:236-238

        // ── 3. 来电铃声（Java:242-302）────────────────────────────────────
        d.text("来电铃声", x: m.ringTitle.minX, baseline: base(m.ringTitle.minY, 14),
               size: dp(14), ink, bold: true)
        d.round(m.ringCard, radius: dp(14), card)                            // Java:246

        // 勾画在哪一行（Java:466-475 / 603-607）：label 空 = 内置铃声，否则本地音频
        let ringChosen = s.ringtoneLabel.isEmpty ? "默认铃声（应用内置）" : "本地音频"

        // 「默认铃声（应用内置）」+「使用内置铃声」（Java:253-256、446-455）
        let defaultHeadY = m.ringDefaultRow.minY + dp(12)                     // Java:437 paddingTop
        d.text("默认铃声（应用内置）", x: m.ringDefaultRow.minX,
               baseline: base(defaultHeadY, 15), size: dp(15), ink)
        d.text("使用内置铃声", x: m.ringDefaultRow.minX,
               baseline: base(defaultHeadY + lineBox(15), 12), size: dp(12), sub)
        if ringChosen == "默认铃声（应用内置）" {
            d.rightText("✓", right: m.ringDefaultRow.maxX,
                        baseline: centerBase(m.ringDefaultRow.midY, 15),
                        size: dp(15), brand)                                 // Java:457-462
        }
        d.rect(m.ringDivider1, line)                                         // Java:257-260

        // 「本地音频」（Java:262-302）
        if ringChosen == "本地音频" {
            d.text("✓", x: m.ringLocalRow.minX,
                   baseline: centerBase(m.ringLocalRow.midY, 15),
                   size: dp(15), brand)                                      // Java:486-491
        }
        d.text("本地音频", x: m.ringLocalTitle.minX,
               baseline: base(m.ringLocalTitle.minY, 15), size: dp(15), ink)  // Java:272-276
        let ringLabel = s.ringtoneLabel.isEmpty ? "未选择，使用内置铃声" : s.ringtoneLabel
        d.text(ellipsize(ringLabel, size: dp(12), maxWidth: m.ringLocalLabel.width),
               x: m.ringLocalLabel.minX, baseline: base(m.ringLocalLabel.minY, 12),
               size: dp(12), sub)                                            // Java:277-280、604-605

        d.roundStroke(m.ringPick, radius: dp(20), brand, width: dp(1.2))      // Java:287
        d.centerText("选择", cx: m.ringPick.midX,
                     baseline: centerBase(m.ringPick.midY, 13), size: dp(13), brand)  // Java:283-286
        d.roundStroke(m.ringListen, radius: dp(20), line, width: dp(1.2))     // Java:296
        d.centerText("试听", cx: m.ringListen.midX,
                     baseline: centerBase(m.ringListen.midY, 13), size: dp(13), ink)  // Java:292-295

        // ── 4. 什么时候响（Java:305-373）───────────────────────────────────
        d.text("什么时候响", x: m.whenTitle.minX, baseline: base(m.whenTitle.minY, 14),
               size: dp(14), ink, bold: true)
        d.round(m.whenCard, radius: dp(14), card)                            // Java:309

        // 「立即」/「现在就打过来」（Java:316-320）
        let nowHeadY = m.whenNowRow.minY + dp(12)
        d.text("立即", x: m.whenNowRow.minX, baseline: base(nowHeadY, 15), size: dp(15), ink)
        d.text("现在就打过来", x: m.whenNowRow.minX,
               baseline: base(nowHeadY + lineBox(15), 12), size: dp(12), sub)
        if choseNow(s) {
            d.rightText("✓", right: m.whenNowRow.maxX,
                        baseline: centerBase(m.whenNowRow.midY, 15), size: dp(15), brand)
        }
        d.rect(m.whenDivider1, line)                                         // Java:321

        // 「多少分钟之后」+「分钟后响」（Java:323-353）
        if s.timeMode == Self.modeDelay {
            d.text("✓", x: m.whenDelayRow.minX,
                   baseline: centerBase(m.whenDelayRow.midY, 15), size: dp(15), brand)
        }
        d.text("多少分钟之后", x: m.whenDelayTitle.minX,
               baseline: base(m.whenDelayTitle.minY, 15), size: dp(15), ink)
        d.rightText(String(s.delayMinutes), right: m.whenDelayField.maxX,
                    baseline: base(m.whenDelayField.minY, 15), size: dp(15), ink)   // Java:334-346
        d.text("分钟后响", x: m.whenDelayUnit.minX,
               baseline: base(m.whenDelayUnit.minY, 13), size: dp(13), sub)  // Java:347-351
        d.rect(m.whenDivider2, line)                                         // Java:353

        // 「指定时间」+ 时间胶囊（Java:355-373）
        if s.timeMode == Self.modeAt {
            d.text("✓", x: m.whenAtRow.minX,
                   baseline: centerBase(m.whenAtRow.midY, 15), size: dp(15), brand)
        }
        d.text("指定时间", x: m.whenAtTitle.minX,
               baseline: base(m.whenAtTitle.minY, 15), size: dp(15), ink)
        d.roundStroke(m.whenAtField, radius: dp(18), brand, width: dp(1.2))   // Java:370
        d.centerText(Self.atText(s), cx: m.whenAtField.midX,
                     baseline: centerBase(m.whenAtField.midY, 15),
                     size: dp(15), brand)                                    // Java:367-368、672

        // ── 开始来电（Java:376-386）───────────────────────────────────────
        d.round(m.start, radius: dp(24), brand)                              // Java:381
        d.centerText("开始来电", cx: m.start.midX,
                     baseline: centerBase(m.start.midY, 17),
                     size: dp(17), UIColor.white)                            // Java:377-380
    }

    /// 「立即」那一行是否勾着（Java:673-675 的三选一）
    private func choseNow(_ s: State) -> Bool { s.timeMode == Self.modeNow }

    /// 联系人头像（Java:539-544）：56dp 圆 + 2dp 描边，选中时描边用品牌色。
    ///
    /// 头像本身在 Android 是 assets 里的 `user-avatar-*` 切图；iOS 端没有这批图，
    /// 所以「有 imageName 就贴图，没有就画首字」—— 两种方式的占位尺寸完全一样。
    private func drawContactAvatar(_ d: Draw, _ slot: ContactSlot, selected: Bool) {
        d.circle(cx: slot.avatar.midX, cy: slot.avatar.midY, r: slot.avatar.width / 2,
                 selected ? brand : card)                                    // Java:542 底色
        circleStroke(d, slot.avatar, color: selected ? brand : line, width: dp(2))
        if let avatar = slot.contact.avatar {
            d.clipped(to: slot.avatar, radius: slot.avatar.width / 2) {
                d.artFill(avatar, in: slot.avatar)
            }
            return
        }
        if let imageName = slot.contact.imageName, let image = UIImage(named: imageName) {
            // 与 Java 一致：ImageView 不裁剪，圆只是背景（Java:542-544）
            d.artFill(image, in: slot.avatar)
            return
        }
        let initial = String(slot.contact.name.prefix(1))
        d.centerText(initial, cx: slot.avatar.midX,
                     baseline: centerBase(slot.avatar.midY, 20), size: dp(20),
                     selected ? UIColor.white : sub)
    }

    /// 圆形描边（`Draw` 只提供填充圆）
    private func circleStroke(_ d: Draw, _ box: CGRect, color: UIColor, width: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.strokeEllipse(in: box.insetBy(dx: width / 2, dy: width / 2))
        d.ctx.restoreGState()
    }

    // MARK: - 命中（顺序对应 Java 里 View 的叠放：子控件先于父容器）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let m = layout(s)
        let p = CGPoint(x: x, y: y)

        // 点最上层的先判：Java 的按钮 / 输入框都在行容器之上
        if m.start.contains(p) { return "call:start" }                       // Java:386
        if m.whenAtField.contains(p) { return "call:time:at" }               // Java:371
        if m.whenDelayField.contains(p) { return "call:time:delay" }         // Java:341-344
        if m.whenNowRow.contains(p) { return "call:time:now" }               // Java:316-320
        if m.ringListen.contains(p) { return "call:ring:preview" }           // Java:297
        if m.ringPick.contains(p) { return "call:ring:pick" }                // Java:288
        if m.ringDefaultRow.contains(p) { return "call:ring:default" }       // Java:253-256
        if m.avatarPreview.contains(p) { return "avatar" }                   // Java:216
        if m.nameField.contains(p) { return "call:name" }                    // Java:226-233

        if s.pendingAt != nil {
            if m.pendingCancel.contains(p) { return "call:cancel" }          // Java:178-182
            if m.pending.contains(p) { return "noop" }
        }

        // 点「来电人（从通讯录选择）」标题：弹出完整微信通讯录列表。
        if m.callerTitle.insetBy(dx: -12, dy: -12).contains(p) { return "call:contacts" }

        // 联系人：只认裁到内容宽度以内的那些（超出的部分在 Android 要横滑才看得到）
        if m.contactClip.contains(p) {
            for slot in m.contactItems where slot.item.contains(p) {
                return "call:contact:" + slot.contact.id                     // Java:558-564
            }
        }

        // 返回：Java 的 back 带 padding 6/10（Java:143），热区按它的 padding 放宽一圈
        if m.back.insetBy(dx: -dp(10), dy: -dp(6)).contains(p) { return "back" }

        return "noop"
    }

    // MARK: - 小工具

    /// 单行截断（Android 的 `setMaxLines(1)` + `TruncateAt.END`，Java:550-551）
    private func ellipsize(_ value: String, size: CGFloat, bold: Bool = false,
                          maxWidth: CGFloat) -> String {
        guard maxWidth > 0 else { return "" }
        if Draw.width(value, size: size, bold: bold) <= maxWidth { return value }
        var chars = Array(value)
        while !chars.isEmpty {
            chars.removeLast()
            let candidate = String(chars) + "…"
            if Draw.width(candidate, size: size, bold: bold) <= maxWidth { return candidate }
        }
        return ""
    }

    /// 「指定时间」那一格显示什么（Java:672）：没选过是「选择时间」，选过是 HH:mm
    static func atText(_ s: State) -> String {
        s.atHour < 0 ? "选择时间" : String(format: "%02d:%02d", s.atHour, s.atMinute)
    }

    /// 「已安排：MM-dd HH:mm 来电」（Java:706-707；Locale.US → en_US_POSIX，避免跟系统区域走）
    static func pendingLabel(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "MM-dd HH:mm"
        return "已安排：" + formatter.string(from: date) + " 来电"
    }
}

// MARK: - 动作令牌（这一屏吐出去的 `call:*`，由宿主 / Router 接）
//
//   back                 返回（Java:144 `finish()`）
//   call:cancel          取消待响的来电（Java:178-182 → CallScheduler.cancel）
//   call:contact:<id>    选通讯录里的人（Java:558-564 → CallStore.pickContact）
//   avatar               换头像（Java:216 → pickAvatar；iOS 侧就是已有的 AvatarPicker）
//   name                 改名字（Java:226-233 的 EditText）
//   call:ring:default    用内置铃声（Java:253-256 → CallStore.useDefaultRingtone）
//   call:ring:pick       从手机里挑音频（Java:288 → pickRingtone）
//   call:ring:preview    试听 / 停止试听（Java:297 → togglePreview）
//   call:time:now        立即（Java:316-320）
//   call:time:delay      多少分钟之后（Java:341-344）
//   call:time:at         指定时间（Java:371 → pickTime）
//   call:start           开始来电（Java:386 → start，内含三个档位的分支）
//
// 没做的地方（都在本页之外）：
//   · 联系人那一排的横滑（Android 是 HorizontalScrollView，Java:191-199）；
//   · 相册 / 文件选择、试听的真播放、CallScheduler 的闹钟与通知（Java:590-664、749-824）；
//   · `schedule()` 之后那两个对话框（Java:754-799）以及后台限制引导。
