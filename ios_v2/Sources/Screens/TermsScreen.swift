import UIKit

/// 首次启动的《用户须知与免责声明》 —— `doutu/app/src/main/java/com/luoyun/doutu/TermsActivity.java`
/// （387 行）的 1:1 移植。
///
/// ## 参考画布怎么来的（本屏唯一需要判断的地方）
///
/// Java 那边**没有 `W`/`H`**：`TermsActivity` 不是 `NativeView` 那一套「固定画布 + `canvas.scale`」，
/// 而是纯原生 View 布局 —— `FrameLayout`(Java:106) + `LinearLayout`(Java:109) + `ScrollView`(Java:132)，
/// 尺寸全是 dp/sp 常量（Java:111、117、124、136-141、156、177、187），由系统按屏幕密度换算。
///
/// 这里按 **1280×2816** 做参考画布（≈ 9:19.8，与 1080×2340@3x = 360×780dp 同比例），
/// dp → 参考单位的系数取 `1280 / 360 ≈ 3.5556`（以宽度为基准换算字号观感，
/// 与 `ShutdownScreen` 的口径一致）。于是：
///
///   * 1280 / 3.5556 = **360dp** 宽；
///   * 2816 / 3.5556 = **792dp** 高；
///
/// 画布本身就是「360×792dp 的整屏」，**横纵系数完全相同**（`sx == sy`），
/// 所以这一屏不会出现「文字被纵向拉伸」的观感差异 —— 这是 dp 型 Activity 移植时最省事的一种比例。
///
/// ## 滚动 &「必须滑到底」的对应关系
///
/// Java 的 `ScrollView`(Java:132) 只滚**正文卡片内部**，标题 / 提示行 / 两个按钮固定在屏幕上；
/// 而 iOS 宿主的 `state.scroll` 是整页级的。所以：
///
///   * `selfScrolls = true`（默认值）：本屏自己把 `-scroll` 只作用在正文上（Java 的 `ScrollView`），
///     宿主的 `draw` 不再额外 translate，避免双倍滚动；
///   * `maxScroll` 返回「正文高度 − 卡片可视高度」，宿主据此夹取 `state.scroll` 并驱动拖动；
///   * 已知偏差：宿主是**整页**接收纵向拖动的，所以在按钮/标题上按住往下拖也会滚正文，
///     而 Java 里只有卡片内部能滚。这是宿主 `ScreenHostView` 的统一行为，不在本文件可改范围；
///   * 已知偏差：Java:344-349 的 `onBackPressed()` **在未同意前屏蔽返回键**；iOS 的左边缘右滑返回
///     是 `ScreenHostView.touchesMoved` 直接发 `"back"` 的（不经过 `hitTest`），本屏拦不住 ——
///     要堵住这个口子得改宿主或 Router，本文件只能保证自己**不产生** `back` 令牌（Java 也没有返回按钮）。
///
/// ## 三处「表现不同但语义等价」的地方
///   1. Java 的 200ms 心跳 `tick`(Java:65-71) 在这里换成**按当前时间现算**（`State.canAgree(at:)`），
///      由宿主已有的 0.25s 重绘心跳（`DoutuApp.swift:971` 的 `startRedrawTimer()`）推进 ——
///      倒计时文案因此是 4fps 刷新，对「再等待 N 秒」这种整秒文案没有观感影响；
///   2. Java 的 `loadServerTerms()`(Java:250-282) 是网络侧职责（`DoutuApi.config`），
///      本屏把正文放在 `State.bodyText` 里，配置拉回来后由上层替换并重置 `reachedBottom`
///      （Java:276-279 的语义：换了正文就要重新滑到底、重新计时）；
///   3. Java 的 `startMain()`(Java:303-311)、`exitNow()`(Java:336-341)、`onBackPressed()`(Java:343-349)
///      都是 Activity/进程级行为，本屏只产出令牌：`terms:agree` / `terms:reject`，
///      是否真的进主页 / 退进程由 Router 决定（持久化用 `TermsScreen.markAccepted(_:)`）。
///
/// ⚠️ 收尾待接：Router（`ios/Sources/App/DoutuApp.swift`）目前**还没有** `terms:agree` /
/// `terms:reject` 两个分支，`node _ios/lint-swift.mjs` 的「令牌都在 Router 里有分支」一项会因此报缺。
/// 该文件不属本次改动范围，这里只声明令牌。
///
/// ## 图片素材
/// **零素材**：整屏都是纯色块 + 系统字体（Java:106-195 里没有任何 `drawable`/位图资源）。
final class TermsScreen: Screen {
    // MARK: - 参考画布（Java 无 W/H，见文件头）

    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    let canvasSize = CGSize(width: 1280, height: 2816)

    /// 本屏自己滚卡片的正文（Java:132 的 `ScrollView`），宿主不要再整页 translate。
    let selfScrolls = true

    /// dp/sp → 参考单位的系数：1280 / 360dp ≈ 3.5556（见文件头）
    static let dpScale: CGFloat = 1280.0 / 360.0
    private func dp(_ v: CGFloat) -> CGFloat { v * Self.dpScale }

    // MARK: - 常量（Java:41-52）

    /// Java:41 `TERMS_VERSION = "2026-10-15-v1"`
    static let termsVersion = "2026-10-15-v1"
    /// Java:52 `WAIT_MS = 5000L` —— 滑到底后还要等的时间
    static let waitMs: Double = 5000
    /// Java:42 `PREFS = "doutu_terms"` / Java:43 `KEY_ACCEPTED`
    private static let keyAccepted = "doutu_terms.accepted_version"
    /// Java:50 `KEY_ACCEPTED_HASH`
    private static let keyAcceptedHash = "doutu_terms.accepted_hash"

    // MARK: - 文案（Java:116、123、169、183、233、236、240）

    static let titleText = "用户须知与免责声明"
    static let subText = "请完整阅读以下全部条款后再决定是否使用本软件"
    static let rejectText = "拒绝"
    static let agreeText = "同意"
    /// Java:233
    static let hintDone = "已阅读完毕，可以点击「同意」"
    /// Java:236
    static let hintScroll = "请滑动阅读至最底部"

    // MARK: - 调色板（照抄 Java 的 0xAARRGGBB）

    /// Java:107 `root.setBackgroundColor(0xFFF5F6FA)`
    private let pageBg = Draw.color(0xfff5f6fa)
    /// Java:135 `card.setColor(Color.WHITE)`
    private let cardBg = UIColor.white
    /// Java:119 `title.setTextColor(0xFF23232F)`
    private let titleInk = Draw.color(0xff23232f)
    /// Java:125 `sub.setTextColor(0xFF8A8A9A)`
    private let subInk = Draw.color(0xff8a8a9a)
    /// Java:147 `body.setTextColor(0xFF44444F)`
    private let bodyInk = Draw.color(0xff44444f)
    /// Java:157、237、241 `0xFFB4551F`
    private let hintWarn = Draw.color(0xffb4551f)
    /// Java:234 `0xFF2E7D32`
    private let hintOk = Draw.color(0xff2e7d32)
    /// Java:172 `reject.setTextColor(0xFF6B6B7B)`
    private let rejectInk = Draw.color(0xff6b6b7b)
    /// Java:174 `rejectBg.setColor(0xFFE8E8EF)`
    private let rejectBg = Draw.color(0xffe8e8ef)
    /// Java:228 `ok ? 0xFF6F6AE8 : 0xFFC9C6E8`
    private let agreeOn = Draw.color(0xff6f6ae8)
    private let agreeOff = Draw.color(0xffc9c6e8)

    // MARK: - 几何：dp 常量（Java:111、117、124、128、136、138、141、156、161、177、178、187、229）

    /// Java:111 `column.setPadding(dp(20), dp(18), dp(20), dp(14))`
    private var padH: CGFloat { dp(20) }
    private var padTop: CGFloat { dp(18) }
    private var padBottom: CGFloat { dp(14) }
    /// 列宽 = 屏宽 − 左右 padding
    private var columnWidth: CGFloat { Self.W - padH * 2 }

    /// Java:117 `title.setTextSize(19f)`
    private var titleSize: CGFloat { dp(19) }
    /// Java:124 `sub.setTextSize(12f)`
    private var subSize: CGFloat { dp(12) }
    /// Java:128 `subLp.topMargin = dp(6)`
    private var subTopMargin: CGFloat { dp(6) }
    /// Java:141 `scrollLp.topMargin = dp(18)`
    private var scrollTopMargin: CGFloat { dp(18) }

    /// Java:136 `card.setCornerRadius(dp(14))`
    private var cardRadius: CGFloat { dp(14) }
    /// Java:138 `scroller.setPadding(dp(16), dp(14), dp(16), dp(14))`
    private var cardPadH: CGFloat { dp(16) }
    private var cardPadV: CGFloat { dp(14) }

    /// Java:146 `body.setTextSize(14f)`
    private var bodySize: CGFloat { dp(14) }
    /// Java:148 `body.setLineSpacing(dp(6), 1f)` —— 每行额外 6dp
    private var bodyLineExtra: CGFloat { dp(6) }

    /// Java:156 `hint.setTextSize(13f)`
    private var hintSize: CGFloat { dp(13) }
    /// Java:161 `hintLp.topMargin = dp(10)`
    private var hintTopMargin: CGFloat { dp(10) }

    /// Java:177 `rejectLp = new LayoutParams(0, dp(48), 1f)`
    private var buttonH: CGFloat { dp(48) }
    /// Java:178 `rejectLp.rightMargin = dp(10)`
    private var buttonGap: CGFloat { dp(10) }
    /// Java:229 `bg.setCornerRadius(dp(24))`
    private var buttonRadius: CGFloat { dp(24) }
    /// Java:170、184 `setTextSize(16f)`
    private var buttonSize: CGFloat { dp(16) }

    // MARK: - 字体度量（Android 的 TextView 行高 ↔ UIKit 的 lineHeight）

    private func lineHeight(_ size: CGFloat, bold: Bool = false) -> CGFloat {
        Draw.font(size: size, bold: bold).lineHeight
    }

    private func ascender(_ size: CGFloat, bold: Bool = false) -> CGFloat {
        Draw.font(size: size, bold: bold).ascender
    }

    // MARK: - 纵向布局（还原 Java 的 LinearLayout 竖向堆叠，Java:109-166）

    /// 标题顶部 = 列的上内边距（Java:111 `paddingTop = dp(18)`）
    private var titleTop: CGFloat { padTop }
    /// `Draw.text` 的 y 是**基线**（Android `drawText` 同义）
    private var titleBaseline: CGFloat { titleTop + ascender(titleSize, bold: true) }

    /// Java:126-128 `sub` 紧接标题，`topMargin = dp(6)`
    private var subTop: CGFloat { titleTop + lineHeight(titleSize, bold: true) + subTopMargin }
    private var subBaseline: CGFloat { subTop + ascender(subSize) }

    /// Java:139-142 卡片在副标题之后，`topMargin = dp(18)`，高度 = weight 1（吃掉全部剩余空间）
    private var cardTop: CGFloat { subTop + lineHeight(subSize) + scrollTopMargin }

    /// Java:164-166 按钮行贴着列的底部；Java:111 `paddingBottom = dp(14)`
    private var buttonsTop: CGFloat { Self.H - padBottom - buttonH }

    /// Java:159-162 提示行在按钮行上方，`topMargin = dp(10)`
    private var hintTop: CGFloat { buttonsTop - hintTopMargin - lineHeight(hintSize) }
    private var hintBaseline: CGFloat { hintTop + ascender(hintSize) }

    /// 卡片的底 = 提示行上方（weight 1 把剩余高度全给卡片，提示行的 topMargin 先扣掉）
    private var cardBottom: CGFloat { hintTop - hintTopMargin }

    private var cardRect: CGRect {
        CGRect(x: padH, y: cardTop, width: columnWidth,
               height: max(0, cardBottom - cardTop))
    }

    /// 卡片内部可视区（去掉 Java:138 的 padding）
    private var viewportHeight: CGFloat { max(0, cardRect.height - cardPadV * 2) }

    /// Java:144-151 正文的行宽 = 列宽 − 卡片左右 padding
    private var bodyWidth: CGFloat { max(1, columnWidth - cardPadH * 2) }

    /// 正文行距（基线之间的进距）：行高 + Java:148 的额外 6dp
    private var bodyLineAdvance: CGFloat { lineHeight(bodySize) + bodyLineExtra }

    /// Java:177-179 `LayoutParams(0, dp(48), 1f)` + `rightMargin = dp(10)` → 两个等宽按钮
    private var buttonWidth: CGFloat { max(0, (columnWidth - buttonGap) / 2) }

    /// Java:168-180 「拒绝」：左半
    private var rejectRect: CGRect {
        CGRect(x: padH, y: buttonsTop, width: buttonWidth, height: buttonH)
    }

    /// Java:182-187 「同意」：右半
    private var agreeRect: CGRect {
        CGRect(x: padH + buttonWidth + buttonGap, y: buttonsTop,
               width: buttonWidth, height: buttonH)
    }

    /// 按钮文字竖直居中：基线 = 中线 + capHeight/2（TextView 的 `Gravity.CENTER` 等价写法）
    private func buttonBaseline(_ r: CGRect) -> CGFloat {
        r.midY + Draw.font(size: buttonSize).capHeight / 2
    }

    // MARK: - 状态

    /// 页面状态（对应 Java 的字段 `reachedBottom` / `bottomReachedAt` / `displayText`，Java:59-63）
    final class State: ScreenState {
        /// Java:59 `private boolean reachedBottom`
        var reachedBottom = false
        /// Java:60 `private long bottomReachedAt` —— 这里是 `CACurrentMediaTime()` 基准
        /// （对应 Java:214 的 `SystemClock.elapsedRealtime()`）
        var bottomReachedAt: CFTimeInterval = 0
        /// 已点过「同意」（Java 里直接 `startMain()` + `finish()`，iOS 侧由 Router 收尾）
        var agreed = false
        /// Java:62 `displayText` —— 先用内置正文；服务端配置回来后由上层替换（Java:273-275）
        var bodyText: String = TermsScreen.termsText
        /// 折行缓存（按 `bodyText` 做 key；换文案时自动失效，对应 Java:273-279 的重置）
        fileprivate var lineCache: [String] = []
        fileprivate var lineCacheKey = ""

        /// Java:219-221 `canAgree()`：滑到底 + 等满 5 秒
        func canAgree(at now: CFTimeInterval = CACurrentMediaTime()) -> Bool {
            guard reachedBottom else { return false }
            return (now - bottomReachedAt) * 1000 >= TermsScreen.waitMs
        }

        /// Java:239-240 `left = (WAIT_MS - 已等毫秒 + 999) / 1000`，再 `Math.max(1, left)`
        func secondsLeft(at now: CFTimeInterval = CACurrentMediaTime()) -> Int {
            let remain = TermsScreen.waitMs - (now - bottomReachedAt) * 1000
            // Java 的整数除法向零截断（remain 可能为负）
            let whole = ((remain + 999) / 1000).rounded(.towardZero)
            return max(1, Int(whole))
        }
    }

    // MARK: - 正文折行（对应 TextView 的自动换行；中文逐字断行）

    /// 按宽度折行。Java 那边是 `TextView` 自己算的，这里用 `Draw.width` 逐字量宽做等价实现。
    private func wrap(_ value: String, size: CGFloat, width: CGFloat) -> [String] {
        var lines: [String] = []
        var line = ""
        for ch in value {
            let candidate = line + String(ch)
            if !line.isEmpty && Draw.width(candidate, size: size) > width {
                lines.append(line)
                line = String(ch)
            } else {
                line = candidate
            }
        }
        if !line.isEmpty { lines.append(line) }
        return lines
    }

    /// 折行后的正文（按 `\n` 分段，空行保留 —— Java 的 `\n\n` 段落间距就是空行）
    ///
    /// 折行结果缓存在 `State` 里，按正文内容做 key：宿主 0.25s 心跳会反复重绘，
    /// 每帧逐字量 2000+ 个字符太浪费；而 Java:273-279 换文案时缓存会自动失效。
    private func bodyLines(_ s: State) -> [String] {
        if s.lineCacheKey == s.bodyText, !s.lineCache.isEmpty { return s.lineCache }
        var out: [String] = []
        for paragraph in s.bodyText.components(separatedBy: "\n") {
            if paragraph.isEmpty {
                // Java:359-377 的 `\n\n`：空行本身占一个行高
                out.append("")
                continue
            }
            out.append(contentsOf: wrap(paragraph, size: bodySize, width: bodyWidth))
        }
        s.lineCache = out
        s.lineCacheKey = s.bodyText
        return out
    }

    /// 正文总高：行数 × 行高 + 行间额外距（Java:148 的 `setLineSpacing` 加在行与行之间）
    private func bodyHeight(_ s: State) -> CGFloat {
        let count = CGFloat(bodyLines(s).count)
        guard count > 0 else { return 0 }
        return count * lineHeight(bodySize) + (count - 1) * bodyLineExtra
    }

    /// 可滚动距离 = 正文高 − 卡片可视高
    /// （即 Java:211 `child.getBottom() - (scroller.getHeight() + scroller.getScrollY())` 的另一面）
    private func contentOverflow(_ s: State) -> CGFloat {
        max(0, bodyHeight(s) - viewportHeight)
    }

    // MARK: - 滚动（Java:132 的 ScrollView 被搬进卡片内部）

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        // 对应 Java:201 `setOnScrollChangeListener(... -> checkBottom())`
        noteBottom(s)
        return contentOverflow(s)
    }

    /// Java:205-217 `checkBottom()`
    private func noteBottom(_ s: State) {
        guard !s.reachedBottom else { return }
        // Java:210 —— 还没量过尺寸（高度 0）时不算「已到底」，否则首帧会误判
        guard viewportHeight > 0 else { return }
        // Java:211-212 `diff <= dp(8)`（留 8dp 容差，避免永远差一两个像素）
        guard contentOverflow(s) - s.scroll <= dp(8) else { return }
        s.reachedBottom = true
        s.bottomReachedAt = CACurrentMediaTime()
    }

    // MARK: - 绘制（Java:105-243 的 buildUi + updateAgreeState）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        // 布局完成后判一次「是否已到底」：等价 Java:88 的 OnGlobalLayoutListener
        noteBottom(s)

        // Java:107 根容器底色
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // Java:115-120 标题（19sp / 粗体 / #23232F）
        d.text(Self.titleText, x: padH, baseline: titleBaseline, size: titleSize,
               titleInk, bold: true)

        // Java:122-129 副标题（12sp / #8A8A9A），比标题低 dp(6)
        d.text(Self.subText, x: padH, baseline: subBaseline, size: subSize, subInk)

        // Java:134-137 白色圆角卡片（圆角 dp(14)）
        d.round(cardRect, radius: cardRadius, cardBg)

        // Java:144-151 正文：14sp / #44444F / 行距 dp(6)，可滚动、滑到底才算读完
        drawBody(d, s)

        // Java:154-195 提示行 + 两个按钮（固定不滚）
        drawFooter(d, s)
    }

    /// 正文（Java:144-151 的 TextView + Java:132 的 ScrollView）
    private func drawBody(_ d: Draw, _ s: State) {
        // Java:138 卡片内边距：正文从「卡片顶 + dp(14)」开始，再减去滚动量
        let firstBaseline = cardRect.minY + cardPadV + ascender(bodySize) - s.scroll
        let textX = cardRect.minX + cardPadH
        let lines = bodyLines(s)
        // Java:132-138 ScrollView 会把正文裁在自己的内框里 —— 这里裁到卡片圆角
        d.clipped(to: cardRect, radius: cardRadius) {
            var baseline = firstBaseline
            for line in lines {
                // 已经滚出卡片底部了，后面的行不用再画
                if baseline - bodyLineAdvance > cardRect.maxY { break }
                // 行框整体在卡片上方时不画（只跳过，基线的推进不能省）
                if baseline > cardRect.minY, !line.isEmpty {
                    d.text(line, x: textX, baseline: baseline, size: bodySize, bodyInk)
                }
                baseline += bodyLineAdvance
            }
        }
    }

    /// 提示行 + 两个按钮（Java:154-195、Java:223-243）
    private func drawFooter(_ d: Draw, _ s: State) {
        let now = CACurrentMediaTime()

        // Java:232-242：三种状态三种文案三种颜色
        let hint: String
        let hintColor: UIColor
        if s.canAgree(at: now) {
            hint = Self.hintDone
            hintColor = hintOk
        } else if !s.reachedBottom {
            hint = Self.hintScroll
            hintColor = hintWarn
        } else {
            hint = "已阅读完毕，请再等待 \(s.secondsLeft(at: now)) 秒"
            hintColor = hintWarn
        }
        // Java:158 `hint.setGravity(Gravity.CENTER)`
        d.centerText(hint, cx: Self.W / 2, baseline: hintBaseline, size: hintSize, hintColor)

        // Java:168-180 「拒绝」：灰底圆角 + 深灰字
        d.round(rejectRect, radius: buttonRadius, rejectBg)
        d.centerText(Self.rejectText, cx: rejectRect.midX,
                     baseline: buttonBaseline(rejectRect), size: buttonSize, rejectInk)

        // Java:182-195、227-230 「同意」：可点时 #6F6AE8，置灰时 #C9C6E8
        let ok = s.canAgree(at: now)
        d.round(agreeRect, radius: buttonRadius, ok ? agreeOn : agreeOff)
        // Java:186 `agreeBtn.setTextColor(Color.WHITE)`
        d.centerText(Self.agreeText, cx: agreeRect.midX,
                     baseline: buttonBaseline(agreeRect), size: buttonSize, UIColor.white)
    }

    // MARK: - 命中（Java:180、188-195；命中顺序与 Java 的点击监听一致）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 宿主把 `state.scroll` 加进了 y（`ScreenHostView.touchesEnded`），
        // 但本屏只有**正文**滚动，标题/提示/按钮是固定的 —— 所以先用页面坐标判命中。
        let pageY = y - s.scroll
        let point = CGPoint(x: x, y: pageY)

        // Java:188-195 点「同意」：`if (!canAgree()) return;` —— 没到时间是死区
        if agreeRect.contains(point) {
            if s.canAgree() { return "terms:agree" }
            // Java:189 双保险：视觉置灰之外再挡一次 → 不产生动作
            return "noop"
        }

        // Java:180 `reject.setOnClickListener(v -> exitNow())` —— 任何时候都退出
        if rejectRect.contains(point) { return "terms:reject" }

        // 正文卡片是一块静态文本（Java:149-151 明确不给 body 设 MovementMethod），点了不响应
        return "noop"
    }

    // MARK: - 同意状态持久化（Java:41-50、92-93、188-195、284-301）

    /// Java:291-301 `sha256(text)`
    static func sha256(_ text: String) -> String {
        DtwCrypto.sha256Hex(Data(text.utf8))
    }

    /// Java:285-288 `isAccepted(text)` —— 按**内容哈希**判断，与版本号写法无关
    static func isAccepted(_ text: String) -> Bool {
        let saved = UserDefaults.standard.string(forKey: keyAcceptedHash) ?? ""
        guard !saved.isEmpty else { return false }
        return saved == sha256(text)
    }

    /// Java:190-193 记下「已同意」的版本与内容哈希，再把状态标成已同意
    static func markAccepted(_ state: State) {
        UserDefaults.standard.set(termsVersion, forKey: keyAccepted)
        UserDefaults.standard.set(sha256(termsText), forKey: keyAcceptedHash)
        state.agreed = true
    }

    /// Java:380-382 `text()` —— 供「我 → 关于」等界面复用同一份正文
    static func text() -> String { termsText }

    /// Java:384-386 `version()`
    static func version() -> String { termsVersion }

    // MARK: - 正文（Java:357-377，逐字照抄，不截断）

    /// Java:358-377 `TERMS_TEXT`
    ///
    /// 段落之间的 `\n\n` 保留成空行（Java 用字符串拼接，这里用多行字面量，内容完全一致）。
    static let termsText: String = """
        欢迎使用本软件。在您开始使用之前，请务必认真、完整地阅读以下全部条款。本条款是您与本软件开发者之间就使用本软件所订立的协议，自您点击「同意」之时起生效，对双方均具有约束力。您点击「同意」或继续使用本软件，即视为您已完整阅读、充分理解并自愿接受本条款的全部内容；如您不同意本条款的任何内容，请点击「拒绝」并立即停止使用本软件。

        一、软件性质与用途
        1. 本软件仅为技术演示、功能模拟与学习交流之用，不构成任何形式的商业服务承诺，亦不对处理结果作出任何准确性、完整性或可用性保证。
        2. 本软件不存储、不传播、不再分发任何第三方平台的内容，仅按用户本人的指令，对其自行提供的链接或素材在本地设备上进行处理。
        3. 本软件与任何第三方平台均无隶属、代理、合作或授权关系，相关名称、标识的权利归各自权利人所有。

        二、用户责任与后果自担
        1. 您通过本软件进行的一切操作、处理、下载、保存、使用、传播等行为，均为您本人独立意愿的体现，并由您本人独立决定、独立实施、独立承担全部后果。
        2. 您承诺所处理的素材已取得合法授权或归您本人所有，且您的使用行为符合您所在国家或地区的法律法规、公序良俗以及相关平台的服务条款。
        3. 因使用本软件所产生的一切结果、后果、损失、纠纷、索赔、行政处罚及其他法律责任，无论直接或间接，均由您本人承担，与本软件及其开发者无关。

        三、免责范围
        1. 本软件的开发者、运营者、公司及其员工、管理人员、关联方，均不对您使用本软件所产生的任何直接或间接后果承担任何责任，包括但不限于：素材侵权、版权纠纷、账号封禁或限流、数据丢失、设备损坏、预期利益损失、第三方索赔、行政或刑事处罚等。
        2. 本软件按「现状」提供，不对可用性、连续性、准确性、及时性、适用性作出任何明示或默示的保证；因网络故障、设备异常、第三方平台接口变更、政策调整或不可抗力导致的服务中断、处理失败或数据异常，开发者不承担任何责任。
        3. 您不得将本软件用于任何违反法律法规、侵犯他人合法权益或损害公共利益的用途；由此产生的一切后果由您自行承担，开发者保留在发现违法使用时停止提供服务并配合有关机关调查的权利。
        4. 您因使用本软件而作出的任何判断与决定，均由您自行负责；本软件不构成任何形式的法律、财务或其他专业建议。

        四、条款的变更、效力与争议解决
        1. 本条款可能因法律法规调整或软件功能变化而更新，更新后将在软件内公示；公示后继续使用即视为接受更新后的条款。
        2. 本条款的解释、效力与履行适用中华人民共和国法律（不含冲突规范）。因本条款产生的争议，双方应首先友好协商；协商不成的，提交开发者所在地有管辖权的人民法院解决。
        3. 本条款部分内容如被认定无效或不可执行，不影响其余条款的效力；该部分应以最接近原意且合法有效的方式予以替代。

        请您再次确认：您已完整阅读并理解上述全部内容，在知晓并自愿承担使用本软件的全部风险与后果的前提下，继续使用本软件。
        """
}
