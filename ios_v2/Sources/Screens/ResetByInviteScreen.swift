import UIKit

/// 「邀请码重置密码」页 —— `account/ResetByInviteActivity.java`（354 行）的 1:1 移植。
///
/// ## 参考画布：Java 里**没有** `W` / `H` 常量
///
/// `ResetByInviteActivity` 和 `CustomerServiceActivity` / `ParseActivity` 一样是**纯原生 View 布局**
/// （`onCreate` → `buildUi()` 里用 `ScrollView` + `LinearLayout` + `TextView` / `EditText` 一层层搭，
/// 尺寸全是 `dp(x)` / `setTextSize(sp)`，由系统按屏幕密度换算，见 Java:351-353 的 `dp()`）。
/// 所以本屏没有可以照抄的坐标，只有可照抄的**版式顺序、间距、圆角、颜色、字号、文案**。
///
/// 处理方式与 `ParseScreen` / `CustomerServiceScreen` 完全一致：
///   1. 画布取全项目统一的 **1280×2816**；
///   2. Android 那边是 1080px ÷ density 3 = **360dp 宽**，对应画布宽 1280 →
///      `1dp = 1280/360 ≈ 3.5556` 画布单位（`dp()` / `setTextSize()` 同一个系数，
///      density 3、fontScale 1 时 dp 与 sp 同比例）；
///   3. 每一条间距/字号在源码里都保留 Java 的行号，逐行比对即可。
///
/// ## 两步流程照 Java 搬（Java:253-326）
///
///   - `showStep(1/2)`（Java:253-264）：切步骤、清错误行、进第二步时清空两个密码框；
///   - `verify()`（Java:268-289）：手机号必须是 6-20 位数字、邀请码去掉非 `[0-9A-Z]` 后必须 12 位；
///   - `submitReset()`（Java:291-326）：密码 ≥ 6 位、两次一致、票据还在；
///   - 服务端提示 `friendly()`（Java:345-349）：空串 / `"null"` 都换成「操作失败，请稍后重试」。
///
/// **网络留给上层**（与 `CustomerServiceScreen` / `ParseScreen` 的约定一致）：
/// 本文件只做「本地校验 + 状态迁移」，不 import 任何网络类型。
/// 两个接口在 iOS 侧已有 1:1 对应物 `AuthService.verifyInviteForReset` /
/// `AuthService.resetPassword`（Java:282 / Java:310 的 `DoutuApi.*`），
/// Router 拿到 `beginVerify` / `beginSubmit` 的返回值后调用它们，再把结果回灌给
/// `finishVerify` / `finishReset` 即可：
///
/// ```swift
/// case "reset:submit":
///     if s.step == 1, let q = screen.beginVerify(s) {              // Java:270-281
///         screen.setBusy(s, true)                                  // Java:281
///         Task {
///             let (outcome, ticket) = await AuthService.verifyInviteForReset(
///                 phone: q.phone, inviteCode: q.code)
///             screen.finishVerify(s, ok: outcome.ok, message: outcome.message, ticket: ticket)
///         }
///     } else if s.step == 2, let q = screen.beginSubmit(s) {       // Java:293-309
///         screen.setBusy(s, true)                                  // Java:309
///         Task {
///             let outcome = await AuthService.resetPassword(
///                 ticket: q.ticket, password: q.password, confirm: q.confirm)
///             screen.finishReset(s, ok: outcome.ok, message: outcome.message)
///         }
///     }
/// ```
///
/// ## 动作令牌（`hitTest` 的返回值）
///
///   * `reset:phone`    —— 手机号输入框（Java:152）→ 上层弹输入框写回 `setText`
///   * `reset:invite`   —— 邀请码输入框（Java:153）
///   * `reset:password` —— 新密码输入框（Java:172）
///   * `reset:confirm`  —— 确认新密码输入框（Java:174，Java 里必有的第二个框）
///   * `reset:submit`   —— 两个主按钮**共用**（Java:157「找回密码」/ Java:177「确认修改」），
///                         用 `State.step` 区分走 `beginVerify` 还是 `beginSubmit`
///   * `reset:restart`  —— 第二步那行「← 手机号 / 邀请码填错了，重新填写」（Java:180-187）
///   * `back`           —— 左上「‹ 返回」（Java:94-99）
///   * `noop`           —— 其余区域 / 提交中（按钮 `setEnabled(false)`，Java:328-333）
///
/// ## 两处**故意**与 Java 不同的地方（Java 那边是 bug）
///
///   1. Java:284 `if (!result.ok()) returnError(friendly(result));` **少了 `return`**，
///      于是核验失败也会继续往下走、把错误行清掉、跳到第二步拿一张空票据。
///      这里 `finishVerify` 失败时直接 return，停在第一步并保留错误提示。
///   2. Java:316-317 票据过期时 `showStep(1)`，而 `showStep` 开头就会 `errorLine.setVisibility(GONE)`
///      （Java:254），用户看不到「过期」的提示。这里退回第一步后把提示重新写回。
final class ResetByInviteScreen: Screen {
    /// 参考画布：Java 没有 `W`/`H`（纯 View 布局），按 App 其余各屏约定取 1280×2816。
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    let canvasSize = CGSize(width: 1280, height: 2816)

    /// 滚动由外层 `ScrollView` 做（Java:78-88），所以自己**不**做 translate，
    /// 交给 `ScreenHostView` 统一滚 —— 两边都做就是双倍滚动。
    let selfScrolls = false

    /// 1dp / 1sp → 参考画布单位（1080px ÷ density 3 = 360dp 对应画布宽 1280）
    private static let unit: CGFloat = 1280.0 / 360.0

    // MARK: - 文案（Java 原文，一字不改）

    /// Java:95 「‹ 返回」
    static let backText = "‹ 返回"
    /// Java:118
    static let heroTitle = "邀请码重置密码"
    /// Java:125
    static let slogan = "用注册时的手机号 + 邀请码验证身份，一个邀请码只对应一个账号"
    /// Java:259 `stepTwoHint.setText(...)`（进第二步时写一次，之后不变）
    static let stepTwoHint =
        "手机号和邀请码已核验通过，请设置新密码。\n改完之后旧密码立即作废，请用新密码登录。"
    /// Java:157 / Java:177 两个主按钮的常态文案
    static let stepOneButtonTitle = "找回密码"
    static let stepTwoButtonTitle = "确认修改"
    /// Java:281 / Java:309 `setBusy(true, …, label)` 的文案
    static let verifyingTitle = "核验中…"
    static let submittingTitle = "提交中…"
    /// Java:181
    static let restartTitle = "← 手机号 / 邀请码填错了，重新填写"
    /// Java:323 Toast
    static let resetToast = "密码已重置，请用新密码登录"

    // MARK: - 调色板（Java:37-41、79-80、127、192、216）

    private let brand = Draw.color(0xff4a7bf7)        // Java:37 BRAND
    private let brand2 = Draw.color(0xff6f8cff)       // Java:38 BRAND_2
    private let ink = Draw.color(0xff1b1b22)          // Java:39 INK
    private let sub = Draw.color(0xff8a8f9a)          // Java:40 SUB
    private let fieldBg = Draw.color(0xfff4f6fa)      // Java:41 FIELD_BG
    private let pageTop = Draw.color(0xffe9efff)      // Java:80 页面渐变顶色
    private let pageBottom = Draw.color(0xfffdfdff)   // Java:80 页面渐变底色
    private let sloganInk = Draw.color(0xccffffff)    // Java:127 副标题（80% 白）
    private let errorColor = Draw.color(0xffe24c4c)   // Java:192 errorLine
    private let hintInk = Draw.color(0xffb9bec8)      // Java:216 setHintTextColor
    private let shadowInk = Draw.color(0x2420335c)    // Java:140 `card.setElevation(dp(8))` 的投影

    // MARK: - 状态（Java:43-56 的成员变量）

    /// 页面可变状态。所有字段都给默认值，于是自动继承 `ScreenState.init()`。
    final class State: ScreenState {
        /// 手机号（Java:45 `phoneField`）
        var phone: String = ""
        /// 邀请码（Java:46 `inviteField`，Java:271 提交时才 `toUpperCase()`）
        var invite: String = ""
        /// 新密码（Java:47 `passwordField`）
        var password: String = ""
        /// 确认新密码（Java:48 `confirmField`）
        var confirm: String = ""

        /// 当前步骤：1 = 手机号 + 邀请码，2 = 两次新密码（Java:72 `showStep(1)`）
        var step: Int = 1

        /// 核验通过后服务端给的一次性票据（Java:55）：没有它就进不了第二步
        var resetToken: String = ""
        /// 提交中（Java:56 `busy`）：按钮 `setEnabled(false)` + `setAlpha(0.6f)`（Java:328-333）
        var busy = false

        /// 公共错误行文案（Java:49 `errorLine`）；空串 = `View.GONE`
        var error: String = ""
        /// 改密成功后的 Toast（Java:323），上层弹完请清空
        var toast: String = ""
        /// Java:324 `finish()`：上层据此关页回登录页
        var finished = false
    }

    // MARK: - 单位换算（Java:351-353 `dp()`；sp 在 density 3 / fontScale 1 时同比例）

    private func dp(_ value: CGFloat) -> CGFloat { value * Self.unit }
    private func fs(_ sp: CGFloat) -> CGFloat { sp * Self.unit }

    /// 单行行高：TextView 默认行高约 1.4 倍字号（`includeFontPadding=true` 的实际观感）。
    /// 取 1.4 是为了与同批「纯 View 布局」屏（解析页 / 客服页）的行高保持同一口径。
    private func lineH(_ sp: CGFloat) -> CGFloat { fs(sp) * 1.4 }

    /// 由文字块**顶部**推出**基线**（`Draw.text` 的 y 是基线）
    private func base(_ top: CGFloat, _ sp: CGFloat) -> CGFloat { top + fs(sp) * 1.15 }

    /// 在矩形里垂直居中时的基线（`Gravity.CENTER` 的近似，对应行框居中）
    private func centerBase(_ rect: CGRect, _ sp: CGFloat) -> CGFloat {
        rect.midY + fs(sp) * 0.35
    }

    /// 多行文字块的总高：n 行行高 + (n-1) 个行间距（`setLineSpacing(dp(3), 1f)`）
    private func blockH(count: Int, lineH: CGFloat, gap: CGFloat) -> CGFloat {
        guard count > 0 else { return 0 }
        return CGFloat(count) * lineH + CGFloat(count - 1) * gap
    }

    // MARK: - 布局（Java:77-200 `buildUi` 的 LinearLayout 测量结果；draw 与 hitTest 共用）

    /// 一个输入行（Java:202-230 `field()`）：标签 + 输入框
    private struct Field {
        var label: String
        var hint: String
        var value: String
        var rect: CGRect = .zero
        var captionBaseline: CGFloat = 0
        /// Java:173/175 `TYPE_TEXT_VARIATION_PASSWORD`：Android 显示为圆点
        var secure = false
    }

    private struct Frame {
        var back = CGRect.zero
        var backBaseline: CGFloat = 0
        var hero = CGRect.zero
        var heroTitleBaseline: CGFloat = 0
        var sloganLines: [String] = []
        var sloganBaselines: [CGFloat] = []
        var card = CGRect.zero
        var fields: [Field] = []
        var primaryButton = CGRect.zero
        var buttonTitle = ""
        var hintLines: [String] = []
        var hintBaselines: [CGFloat] = []
        var restart = CGRect.zero
        var restartBaseline: CGFloat = 0
        var errorLines: [String] = []
        var errorBaselines: [CGFloat] = []
        /// 内容总高（含底部 28dp 内边距），滚动上限用
        var contentHeight: CGFloat = 0
    }

    private func layout(_ s: State) -> Frame {
        var f = Frame()

        // root 内边距：左右 20dp、上 24dp、下 28dp（Java:86）
        let padH = dp(20)
        let contentW = Self.W - padH * 2
        var y = dp(24)

        // ① 顶部返回行（Java:91-101）：横向 LinearLayout，只有「‹ 返回」一个子项
        //    padding(0, dp(6), dp(12), dp(6))，15sp BRAND
        f.back = CGRect(x: padH, y: y,
                        width: Draw.width(Self.backText, size: fs(15)) + dp(12),
                        height: dp(6) * 2 + lineH(15))
        f.backBaseline = base(f.back.minY + dp(6), 15)
        y = f.back.maxY

        // ② 渐变小头图（Java:104-130）：topMargin 10dp、圆角 22dp、内边距 22/20/22/22
        let heroY = y + dp(10)
        let heroInnerW = contentW - dp(22) * 2
        var hy = heroY + dp(20)
        // Java:117-122 标题 23sp 粗体白
        f.heroTitleBaseline = base(hy, 23)
        hy += lineH(23)
        // Java:129 `setPadding(0, dp(8), 0, 0)`
        hy += dp(8)
        // Java:124-130 副标题 12.5sp、行距 dp(3)
        f.sloganLines = wrap(Self.slogan, width: heroInnerW, size: fs(12.5))
        let sloganStep = lineH(12.5) + dp(3)
        f.sloganBaselines = (0..<f.sloganLines.count).map {
            base(hy, 12.5) + CGFloat($0) * sloganStep
        }
        let sloganH = blockH(count: f.sloganLines.count, lineH: lineH(12.5), gap: dp(3))
        let heroH = dp(20) + lineH(23) + dp(8) + sloganH + dp(22)
        f.hero = CGRect(x: padH, y: heroY, width: contentW, height: heroH)
        y = f.hero.maxY

        // ③ 白卡（Java:132-145）：圆角 22dp、内边距 20/6/20/22、topMargin **-18dp**（压在头图上）
        let cardY = y - dp(18)
        let innerX = padH + dp(20)
        let innerW = contentW - dp(40)
        var cy = cardY + dp(6)

        if s.step == 1 {
            // ── 第一步：手机号 + 邀请码（Java:147-158）──
            f.fields.append(field(&cy, innerX: innerX, innerW: innerW,
                                  label: "注册时的手机号", hint: "11 位手机号",
                                  value: s.phone, secure: false))                 // Java:152
            f.fields.append(field(&cy, innerX: innerX, innerW: innerW,
                                  label: "邀请码", hint: "12 位字母数字，注册时用的那一个",
                                  value: s.invite, secure: false))                // Java:153-155
            // Java:157 primaryButton("找回密码")
            cy += dp(24)                                                          // topMargin 24dp
            let buttonH = dp(16) * 2 + lineH(17)
            f.primaryButton = CGRect(x: innerX, y: cy, width: innerW, height: buttonH)
            f.buttonTitle = s.busy ? Self.verifyingTitle : Self.stepOneButtonTitle
            cy = f.primaryButton.maxY
        } else {
            // ── 第二步：两次新密码（Java:160-187）──
            // Java:165-170 stepTwoHint：12.5sp SUB、padding(4, 14, 4, 0)
            cy += dp(14)
            f.hintLines = wrap(Self.stepTwoHint, width: innerW - dp(8), size: fs(12.5))
            let hintStep = lineH(12.5) + dp(3)
            f.hintBaselines = (0..<f.hintLines.count).map {
                base(cy, 12.5) + CGFloat($0) * hintStep
            }
            cy += blockH(count: f.hintLines.count, lineH: lineH(12.5), gap: dp(3))

            f.fields.append(field(&cy, innerX: innerX, innerW: innerW,
                                  label: "新密码", hint: "至少 6 位",
                                  value: s.password, secure: true))               // Java:172-173
            f.fields.append(field(&cy, innerX: innerX, innerW: innerW,
                                  label: "确认新密码", hint: "再输入一次新密码",
                                  value: s.confirm, secure: true))                // Java:174-175

            // Java:177 primaryButton("确认修改")
            cy += dp(24)
            let buttonH = dp(16) * 2 + lineH(17)
            f.primaryButton = CGRect(x: innerX, y: cy, width: innerW, height: buttonH)
            f.buttonTitle = s.busy ? Self.submittingTitle : Self.stepTwoButtonTitle
            cy = f.primaryButton.maxY

            // Java:180-187 「← 手机号 / 邀请码填错了，重新填写」12.5sp BRAND、居中、padding top 16dp
            f.restart = CGRect(x: innerX, y: cy + dp(16), width: innerW, height: lineH(12.5))
            f.restartBaseline = base(f.restart.minY, 12.5)
            cy = f.restart.maxY
        }

        // ④ 公共错误行（Java:189-197）：12.5sp 0xFFE24C4C、居中、padding(4, 14, 4, 0)、默认 GONE
        if !s.error.isEmpty {
            cy += dp(14)
            f.errorLines = wrap(s.error, width: innerW - dp(8), size: fs(12.5))
            let errorStep = lineH(12.5) + dp(3)
            f.errorBaselines = (0..<f.errorLines.count).map {
                base(cy, 12.5) + CGFloat($0) * errorStep
            }
            cy += blockH(count: f.errorLines.count, lineH: lineH(12.5), gap: dp(3))
        }

        // 卡片底内边距 22dp（Java:139）
        f.card = CGRect(x: padH, y: cardY, width: contentW, height: cy + dp(22) - cardY)
        f.contentHeight = f.card.maxY + dp(28)
        return f
    }

    /// 一个输入行（Java:202-230）：row paddingTop 16dp → 标签（12.5sp SUB，padding 4/0/0/6）
    /// → 输入框（FIELD_BG、圆角 12dp、内边距 14/13/14/13、16sp 单行）
    private func field(_ cy: inout CGFloat, innerX: CGFloat, innerW: CGFloat,
                       label: String, hint: String, value: String, secure: Bool) -> Field {
        cy += dp(16)                                                    // Java:205
        let captionBaseline = base(cy, 12.5)                             // Java:207-212
        cy += lineH(12.5) + dp(6)                                        // 标签 paddingBottom 6dp
        let boxH = dp(13) * 2 + lineH(16)                                // Java:225 上下各 13dp
        let box = CGRect(x: innerX, y: cy, width: innerW, height: boxH)
        cy = box.maxY
        return Field(label: label, hint: hint, value: value, rect: box,
                     captionBaseline: captionBaseline, secure: secure)
    }

    // MARK: - Screen

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = state as? State ?? State()
        return max(0, layout(s).contentHeight - Self.H)
    }

    // MARK: - 绘制（Java:77-200 `buildUi` 的版式；颜色/字号照抄）

    func draw(_ d: Draw, state: ScreenState) {
        let s = state as? State ?? State()
        let f = layout(s)
        let innerX = f.card.minX + dp(20)
        let innerW = f.card.width - dp(40)

        // Java:79-81 ScrollView 背景：TOP_BOTTOM 渐变 0xFFE9EFFF → 0xFFFDFDFF
        // 整段内容高度都铺上，滚到底也不会露出白底
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: max(Self.H, f.contentHeight)),
                   from: pageTop, to: pageBottom)

        // Java:94-99 「‹ 返回」15sp BRAND
        d.text(Self.backText, x: f.back.minX, baseline: f.backBaseline, size: fs(15), brand)

        // Java:106-110 头图：LEFT_RIGHT 渐变 BRAND → BRAND_2、圆角 22dp
        d.clipped(to: f.hero, radius: dp(22)) {
            d.gradient(f.hero, from: brand, to: brand2, vertical: false)
        }
        // Java:117-122 标题 23sp 粗体白
        d.text(Self.heroTitle, x: f.hero.minX + dp(22), baseline: f.heroTitleBaseline,
               size: fs(23), UIColor.white, bold: true)
        // Java:124-130 副标题 12.5sp 0xCCFFFFFF
        for (i, line) in f.sloganLines.enumerated() where i < f.sloganBaselines.count {
            d.text(line, x: f.hero.minX + dp(22), baseline: f.sloganBaselines[i],
                   size: fs(12.5), sloganInk)
        }

        // Java:132-145 白卡：圆角 22dp + elevation dp(8) 的投影
        d.ctx.saveGState()
        d.ctx.setShadow(offset: CGSize(width: 0, height: dp(2)), blur: dp(8),
                        color: shadowInk.cgColor)
        d.round(f.card, radius: dp(22), UIColor.white)
        d.ctx.restoreGState()

        // ① 输入行（Java:202-230）：标签 + 输入框 + 值/占位
        for item in f.fields {
            d.text(item.label, x: innerX + dp(4), baseline: item.captionBaseline,
                   size: fs(12.5), sub)                                     // Java:207-212
            d.round(item.rect, radius: dp(12), fieldBg)                     // Java:221-225
            let shown = item.value.isEmpty
                ? item.hint
                : (item.secure ? Self.mask(item.value) : item.value)
            d.text(shown, x: item.rect.minX + dp(14),
                   baseline: item.rect.minY + dp(13) + fs(16) * 1.15,
                   size: fs(16), item.value.isEmpty ? hintInk : ink)         // Java:216-220
        }

        // ② 主按钮（Java:232-250）：LEFT_RIGHT 渐变、圆角 14dp、17sp 粗体白居中
        if !f.primaryButton.isEmpty {
            d.clipped(to: f.primaryButton, radius: dp(14)) {
                d.gradient(f.primaryButton, from: brand, to: brand2, vertical: false)
            }
            // Java:331 提交中 `setAlpha(0.6f)`：用一层白纱近似整块变淡
            if s.busy {
                d.round(f.primaryButton, radius: dp(14), UIColor(white: 1, alpha: 0.4))
            }
            d.centerText(f.buttonTitle, cx: f.primaryButton.midX,
                         baseline: centerBase(f.primaryButton, 17),
                         size: fs(17), UIColor.white, bold: true)
        }

        // ③ 第二步的提示（Java:259，两段，中间是 `\n`）
        for (i, line) in f.hintLines.enumerated() where i < f.hintBaselines.count {
            d.text(line, x: innerX + dp(4), baseline: f.hintBaselines[i],
                   size: fs(12.5), sub)
        }

        // ④ 第二步「重新填写」12.5sp BRAND 居中（Java:180-187）
        if s.step == 2 {
            d.centerText(Self.restartTitle, cx: f.restart.midX,
                         baseline: f.restartBaseline, size: fs(12.5), brand)
        }

        // ⑤ 公共错误行 12.5sp 0xFFE24C4C 居中（Java:189-197）
        for (i, line) in f.errorLines.enumerated() where i < f.errorBaselines.count {
            d.centerText(line, cx: innerX + innerW / 2, baseline: f.errorBaselines[i],
                         size: fs(12.5), errorColor)
        }
    }

    // MARK: - 命中（顺序 = 绘制顺序的逆序：后加的 View 先判）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = state as? State ?? State()
        let f = layout(s)
        let p = CGPoint(x: x, y: y)

        // ① 第二步的「重新填写」（Java:180-187 `showStep(1)`）
        if s.step == 2, f.restart.contains(p) { return "reset:restart" }

        // ② 主按钮（Java:157 / Java:177）；提交中按钮 `setEnabled(false)`（Java:329），点了不响应
        if f.primaryButton.contains(p) {
            if s.busy { return "noop" }
            return "reset:submit"
        }

        // ③ 两个输入框（Java:152-155 / Java:172-175）：点一下由上层弹输入框
        if f.fields.count >= 2 {
            if s.step == 1 {
                if f.fields[0].rect.contains(p) { return "reset:phone" }     // Java:152
                if f.fields[1].rect.contains(p) { return "reset:invite" }    // Java:153
            } else {
                if f.fields[0].rect.contains(p) { return "reset:password" }  // Java:172
                if f.fields[1].rect.contains(p) { return "reset:confirm" }   // Java:174
            }
        }

        // ④ 左上「‹ 返回」（Java:99 `finish()`）
        if f.back.contains(p) { return "back" }

        return "noop"
    }

    // MARK: - 交互（Java:253-333 的纯状态迁移；网络与弹窗留给上层）

    /// 输入框回填（对应 `EditText.setText`）。
    ///
    /// 只认四个输入令牌；Java 这边**不 trim**（Java:270-271 是提交时才 trim / toUpperCase），
    /// 所以这里原样写入，保持「用户打进去什么就是什么」。
    func setText(_ token: ActionToken, _ text: String, in s: State) {
        switch token {
        case "reset:phone": s.phone = text
        case "reset:invite": s.invite = text
        case "reset:password": s.password = text
        case "reset:confirm": s.confirm = text
        default: break
        }
    }

    /// 切换两步（Java:253-264）：换步时把上一步的错误清掉；进第二步清空两个密码框。
    func showStep(_ step: Int, _ s: State) {
        s.error = ""                                     // Java:254 errorLine.setVisibility(GONE)
        s.step = step == 1 ? 1 : 2                       // Java:255-257
        if s.step == 2 {
            s.password = ""                              // Java:260
            s.confirm = ""                               // Java:261
        }
    }

    /// 提交中开关（Java:328-333）：按钮文案与 0.6 透明度由 `draw` 按 `busy` 推导。
    func setBusy(_ s: State, _ value: Bool) {
        s.busy = value
    }

    /// 错误行（Java:335-338）；空文案同样会显示一行空白，与 Java 一致。
    func returnError(_ s: State, _ message: String) {
        s.error = message
    }

    /// Java:340-342
    func hideError(_ s: State) {
        s.error = ""
    }

    /// 第一步的本地校验（Java:270-279）。
    ///
    /// 返回 `nil` = 校验没过（错误已写进 `State.error`，停在第一步）；
    /// 返回非 `nil` = 可以发请求，上层拿这两个值调
    /// `AuthService.verifyInviteForReset(phone:inviteCode:)`（Java:282）。
    func beginVerify(_ s: State) -> (phone: String, code: String)? {
        if s.busy { return nil }                                      // Java:269
        let phone = s.phone.trimmingCharacters(in: .whitespacesAndNewlines)          // Java:270
        let code = s.invite.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() // Java:271
        guard Self.isPhoneNumber(phone) else {                        // Java:272-275 `\d{6,20}`
            returnError(s, "请输入注册时填写的手机号")
            return nil
        }
        guard Self.inviteLength(code) == 12 else {                     // Java:276-279
            returnError(s, "邀请码是 12 位字母数字，请核对后重试")
            return nil
        }
        return (phone, code)
    }

    /// 第二步的本地校验（Java:293-307）。
    ///
    /// 返回 `nil` = 校验没过（错误已写进 `State.error`）；票据失效时会自动退回第一步（Java:305）。
    /// 返回非 `nil` = 可以发请求，上层调
    /// `AuthService.resetPassword(ticket:password:confirm:)`（Java:310）。
    func beginSubmit(_ s: State) -> (ticket: String, password: String, confirm: String)? {
        if s.busy { return nil }                                      // Java:292
        if s.password.count < 6 {                                     // Java:295-298
            returnError(s, "密码至少 6 位")
            return nil
        }
        if s.password != s.confirm {                                  // Java:299-302
            returnError(s, "两次输入的密码不一致")
            return nil
        }
        if s.resetToken.isEmpty {                                     // Java:303-307
            returnError(s, "核验已失效，请重新填写手机号和邀请码")
            showStep(1, s)
            return nil
        }
        return (s.resetToken, s.password, s.confirm)
    }

    /// 第一步的结果回灌（Java:284-288）。
    func finishVerify(_ s: State, ok: Bool, message: String, ticket: String) {
        setBusy(s, false)                                             // Java:283
        // Java:284 少了 `return` —— 失败也会跳到第二步，这里补上（见文件头「两处故意不同」）
        guard ok else {
            returnError(s, Self.friendly(message))
            return
        }
        s.resetToken = ticket                                         // Java:285
        guard !s.resetToken.isEmpty else {                            // Java:286
            returnError(s, "核验没通过，请稍后重试")
            return
        }
        showStep(2, s)                                                // Java:287
    }

    /// 第二步的结果回灌（Java:311-325）。
    func finishReset(_ s: State, ok: Bool, message: String) {
        setBusy(s, false)                                             // Java:311
        guard ok else {
            let reason = Self.friendly(message)                       // Java:313
            returnError(s, reason)
            // Java:315-318 票据过期 / 用过了：退回第一步重新核验
            if message.contains("过期") {
                s.resetToken = ""
                showStep(1, s)
                // Java:254 的 showStep 会把错误行清掉，这里把提示写回来（见文件头）
                returnError(s, reason)
            }
            return
        }
        s.resetToken = ""                                             // Java:321
        hideError(s)                                                  // Java:322
        s.toast = Self.resetToast                                     // Java:323
        s.finished = true                                             // Java:324 finish()
    }

    /// 服务端提示优先（Java:344-349）。
    static func friendly(_ message: String) -> String {
        let trimmed = message.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "null" { return "操作失败，请稍后重试" }
        return trimmed
    }

    // MARK: - 小工具

    /// Java:272 `phone.matches("\\d{6,20}")` —— 只认 ASCII 数字（`\d` 在 Java 里是 `[0-9]`）
    private static func isPhoneNumber(_ text: String) -> Bool {
        guard text.count >= 6, text.count <= 20 else { return false }
        for ch in text {
            guard ch >= "0", ch <= "9" else { return false }
        }
        return true
    }

    /// Java:276 `code.replaceAll("[^0-9A-Z]", "").length()` —— 只数 ASCII 数字与大写字母
    /// （`invite` 已经 `toUpperCase()`，见 Java:271）
    private static func inviteLength(_ text: String) -> Int {
        var count = 0
        for ch in text {
            if (ch >= "0" && ch <= "9") || (ch >= "A" && ch <= "Z") { count += 1 }
        }
        return count
    }

    /// 密码框显示为圆点（Java:173/175 `TYPE_TEXT_VARIATION_PASSWORD` 的实际观感）
    private static func mask(_ value: String) -> String {
        String(repeating: "•", count: value.count)
    }

    /// 按字符折行 —— 对应 Android `TextView` 在 `MATCH_PARENT` 宽度下的自动换行。
    /// 中文逐字断行是对的；长英文串会从中间断开（Android 走单词边界），本页文案里没有这种情况。
    private func wrap(_ text: String, width: CGFloat, size: CGFloat) -> [String] {
        var lines: [String] = []
        for paragraph in text.components(separatedBy: "\n") {
            var current = ""
            for ch in paragraph {
                let next = current + String(ch)
                if !current.isEmpty, Draw.width(next, size: size) > width {
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
}
