import AVFoundation
import UIKit

/// 「支付宝到账声音」（模拟收款声音）页 —— `features/sound/AccountSoundActivity.java`（778 行）的移植。
///
/// ## 关于参考画布（本屏唯一需要判断的地方）
///
/// Java 那边**没有设计基准**：这个 Activity 不是 NativeView 那一套「固定 W/H + canvas.scale」，
/// 而是纯原生 View 布局 —— 一棵 `ScrollView > LinearLayout(VERTICAL)` 的卡片栈，
/// 尺寸全是 dp/sp 常量，由 `dp()`（Java:105-107）按屏幕密度换算。
/// 也就是说没有 1280 这类基准可抄，所以这里按 `ShutdownScreen` 定下的同一约定取
/// **1280×2816**（≈ 9:19.8，与 1080×2400@3x = 360×800dp 同比例），
/// dp → 参考单位的系数 **1280 / 360 ≈ 3.5556**（横向，宽度决定字号观感；
/// 纵向 2816/800 = 3.52，差 1%，由 `Canvas.sy` 的拉伸铺满吸收）。
///
/// 于是 `dp(14)` 的左右留白 → 49.8、`sp(19)` 的标题 → 67.6、卡片圆角 `dp(14)` → 49.8。
/// 下文所有几何量都写成 `dp(x)` / `sp(x)`，与 Java 的 `dp(x)` / `setTextSize(x)` 一一对应，
/// 便于逐行比对；每个常量后面标了 Java 行号。
///
/// ## 排版：从「LinearLayout 流式布局」到「静态画布」
///
/// Android 是自上而下的流式布局（wrap_content + marginTop），iOS 这一层只有一块画布，
/// 所以 `Metrics` 里按 Java 的**同一顺序**把纵向游标走一遍，把每个控件的矩形/基线算出来，
/// 绘制与命中都用这一份数据（不会出现「画的」和「点的」不一致）。
///
/// 由此产生三处**已知偏差**（都是形态差异，不是省略）：
///   1. `errorLine`（Java:335-339）与「取消循环播放」（Java:358-368）在 Java 里是
///      `GONE → VISIBLE`，出现时会把下面的控件整体往下推；静态画布里改不了流式高度，
///      所以**始终保留各自那一行的高度**，只是不显示内容。好处是点「开始播报」时画面不会跳。
///   2. 长文案（顶部摘要、若干 hint）在 Java 里会自动换行并把卡片撑高；这里改成
///      **单行自动缩字号**（`fitting(_:maxWidth:size:)`，最小缩到 0.7 倍）。
///   3. 三个模式图标 Java 用的是 `R.drawable.sound_once / sound_loop / sound_infinite`
///      （矢量图），iOS 侧没有对应素材，改为**画出来的图形**（三角 / 缺口圆环带箭头 / 双环），
///      零素材依赖。
///
/// ## 音频（Java 用 MediaPlayer 播 20 个音素 mp3）
///
/// iOS 侧用 `AVAudioPlayer`（需求指定的 AVFoundation 路径）：
///   · **只在这一页的点击里触发**（`hitTest` 命中「开始播报 / 取消循环 / 停止」时），
///     `draw`、`init`、`maxScroll` 一律不碰播放器，`AVAudioSession` 也不动 —— 不抢别人的音频会话；
///   · 素材优先取 `Bundle` 里的 `account_sound_zhi.mp3` / `account_sound_wei.mp3`
///     （现在**没有**打进包里，见文件末尾说明）；没有就退回到**内存里合成的双音「叮—咚」**
///     （`Player.chime()`，现算 WAV 字节，无文件、无素材）；
///   · 全程 `try?` + `guard`，任何一步失败都**静默**（不崩、不弹窗），完成回调照常发出，
///     循环节奏不会被一次失败卡死。
///
/// ## 与 Java 的职责差异
///   · `Toast`（Java:191-193、747）→ 这一层拿不到全局 toast 通道，改为把结果写进状态行
///     （「已暂停循环播报」已经在状态行上，与 Java 同文案）；
///   · `SoundStore`（按账号存 SharedPreferences）→ iOS 侧先落到 `UserDefaults`
///     （`doutu.sound.*`），键与语义照抄 `SoundStore` 的七个字段；
///   · 状态行的小点点（Java:756-770）→ 没有 Handler，改成**按时间算相位**
///     （`State.statusText(at:)`，400ms 一格），由宿主 250ms 的重绘心跳推进
///     （`ScreenHostView.startRedrawTimer()`，见 DoutuApp.swift:971）。
///
/// ## 交互令牌
///
/// `hitTest` 返回：`"back"`（返回）、`"edit:amount"`（金额输入框 → 宿主弹 `EditPrompt`，
/// 写回走本文件的 `EditableState`）、`"soundPlay"` / `"soundStop"`（播报按钮与「取消循环播放」，
/// Router 里这两个分支只做 `setNeedsDisplay`，正好用来立即重绘），其余一律 `"noop"`。
///
/// **状态修改与音频起停都在 `hitTest` 里就地完成**，令牌只表达「宿主还需要做什么」。
/// 设置项之所以返回 `"noop"`：Java 里那些点击只是改 `SoundStore` + `bindState()`，没有跳转；
/// 而 Router 没有它们的通用分支，未登记令牌会落到 `default: break`，还会让
/// `_ios/lint-swift.mjs` 的「令牌必须在 Router 里有分支」判红。将来若要把这些动作接进 Router，
/// 记得**不要**重复执行（尤其不要重复起播，会响两遍）。
final class AccountSoundScreen: Screen {
    // MARK: - 画布（Java 无 W/H，约定见文件头）

    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    let canvasSize = CGSize(width: 1280, height: 2816)

    /// Java **没有** `W`/`H` 常量：本页是纯 View 布局，尺寸由 dp/sp 换算。
    /// 这里取 1280×2816 作参考画布，`dpScale` 就是「1dp 等于多少参考单位」。
    private let dpScale: CGFloat = 1280.0 / 360.0

    /// `draw` 自己做了 `translate(0, -scroll)`（与 Android 一致），宿主不要再滚一次
    let selfScrolls = true

    /// Java:105-107 `dp(float value)` —— dp/sp → 参考单位
    private func dp(_ value: CGFloat) -> CGFloat { value * dpScale }
    /// 字号（Android 的 sp，按 1sp = 1dp 处理）
    private func sp(_ value: CGFloat) -> CGFloat { value * dpScale }

    // MARK: - 常量（Java:47-57）

    private let brand = Draw.color(0xff6f6ae8)          // Java:47 BRAND
    private let brandSoft = Draw.color(0xffedebff)      // Java:48 BRAND_SOFT
    private let ink = Draw.color(0xff23232f)            // Java:49 INK
    private let sub = Draw.color(0xff8a8a9a)            // Java:50 SUB
    private let cardColor = Draw.color(0xffffffff)      // Java:51 CARD
    private let lineColor = Draw.color(0xffededf3)      // Java:52 LINE
    private let chipBg = Draw.color(0xfff4f4f9)         // Java:53 CHIP_BG
    private let danger = Draw.color(0xffe24c4c)         // Java:54 DANGER
    private let pageBg = Draw.color(0xfff4f5f9)         // Java:199 root.setBackgroundColor
    private let fieldBg = Draw.color(0xfff4f4f9)        // Java:291 输入框底色
    private let fieldHint = Draw.color(0xffb4b4c0)      // Java:290 setHintTextColor
    private let stopBg = Draw.color(0xfffff5f5)         // Java:363 「取消循环播放」底色

    /// Java:56 `SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f}`
    static let speeds: [CGFloat] = [0.5, 0.75, 1, 1.25, 1.5]
    /// Java:57 `DELAYS = {0, 2, 5, 10}`
    static let delays: [Int] = [0, 2, 5, 10]

    /// SoundStore.MODE_ONCE / MODE_LOOP / MODE_INFINITE（SoundStore.java:25）
    static let modeOnce = 0
    static let modeLoop = 1
    static let modeInfinite = 2
    /// SoundStore.STYLE_ZHI / STYLE_WEI（SoundStore.java:24）
    static let styleZhi = 0
    static let styleWei = 1
    /// SoundStore.LOOP_GAP_MS（SoundStore.java:27）—— 原包循环间隔 5 秒
    static let loopGapMs = 5000

    // MARK: - 状态

    /// 这一屏的可变状态。
    ///
    /// 对应 Java 里 `SoundStore`（七个持久化字段）+ 页面自己的 `looping / infinite / dotPhase`
    /// + 三个 TextView 的文案（statusLine / errorLine / summaryLine）。
    final class State: ScreenState {
        /// Java:33 `amount`（输入框里的金额原文）
        var amount: String = SoundDefaults.string("amount", "")
        /// Java:30 `style`：0 小支 / 1 小微
        var style: Int = SoundDefaults.int("style", AccountSoundScreen.styleZhi)
        /// Java:31 `randomStyle`
        var randomStyle: Bool = SoundDefaults.bool("randomStyle", false)
        /// Java:37 `mode`：0 一次 / 1 循环 / 2 无限
        var mode: Int = SoundDefaults.int("mode", AccountSoundScreen.modeOnce)
        /// Java:36 `range`：-1 不定额 / 0 10-100 / 1 100-1000 / 2 1000-10000
        var range: Int = SoundDefaults.int("range", -1)
        /// Java:35 `delay`：0 / 2 / 5 / 10 秒
        var delay: Int = SoundDefaults.int("delay", 0)
        /// Java:34 `speed`：0.5 / 0.75 / 1 / 1.25 / 1.5
        var speed: CGFloat = CGFloat(SoundDefaults.double("speed", 1))

        /// Java:76 `looping`
        var looping = false
        /// Java:77 `infinite`
        var infinite = false
        /// Java:71 `errorLine` 的文案（空串 = GONE）
        var error = ""
        /// Java:72 `statusLine` 的文案（不忙的时候用这一句）
        var status = "设置成功，播报一下看看吧"
        /// 播放中（Java 的 `AccountSoundPlayer.isPlaying()`，只用于避免重复起播）
        var playing = false
        /// 进入循环/无限模式的时刻：小点点按时间算相位（Java:766 `handler.postDelayed(this, 400)`）
        var busySince: CFTimeInterval = CACurrentMediaTime()

        /// Java:613 `boolean busy = looping || infinite;`
        var busy: Bool { looping || infinite }

        /// 把七个设置写回 UserDefaults（对应 `SoundStore` 每次 setter 里的 `prefs().edit()...apply()`）
        func save() {
            SoundDefaults.set(amount, "amount")
            SoundDefaults.set(style, "style")
            SoundDefaults.set(randomStyle, "randomStyle")
            SoundDefaults.set(mode, "mode")
            SoundDefaults.set(range, "range")
            SoundDefaults.set(delay, "delay")
            SoundDefaults.set(Double(speed), "speed")
        }

        /// 状态行文案。
        ///
        /// 不忙 → `status`（Java:618「设置成功，播报一下看看吧」/ Java:746「已暂停循环播报」）；
        /// 忙 → Java:765 那一句 + `dotPhase` 个小点。Java 用 Handler 每 400ms 推进一格，
        /// 这里改成按经过时间算，宿主 4fps 重绘时看到的就是同样的动画。
        func statusText(at now: CFTimeInterval) -> String {
            guard busy else { return status }
            let base = infinite ? "无限收款模式已开启" : "正在循环播报中，间隔5秒响一次"
            let elapsed = max(0, now - busySince)
            let phase = (Int(elapsed / 0.4) + 1) % 4
            return base + String(repeating: ".", count: phase)
        }

        /// 顶部那行摘要（Java:632-652 `refreshSummary()`）：这次以谁、多少钱、多快、什么时候响。
        var summary: String {
            var text = randomStyle
                ? "随机小支/小微"
                : (style == AccountSoundScreen.styleWei ? "小微收款" : "小支收款")
            text += " · "
            if let bounds = AccountSoundScreen.rangeBounds(range) {
                text += "随机 \(bounds.0)-\(bounds.1) 元"
            } else {
                let trimmed = amount.trimmingCharacters(in: .whitespacesAndNewlines)
                text += trimmed.isEmpty ? "金额待输入" : trimmed + " 元"
            }
            text += " · " + AccountSoundScreen.speedLabel(speed)
            if delay > 0 { text += " · 延时 \(delay) 秒" }
            if infinite {
                text += " · 无限收款模式"
            } else if looping {
                text += " · 循环播报"
            }
            return text
        }
    }

    // MARK: - 几何（由 Java 的 LinearLayout 栈换算，见 `buildMetrics()`）

    private final class Metrics {
        var contentHeight: CGFloat = 0

        var backRect = CGRect.zero
        var backBaseline: CGFloat = 0
        var titleX: CGFloat = 0
        var titleBaseline: CGFloat = 0

        var summaryRect = CGRect.zero
        var summaryBaseline: CGFloat = 0

        var styleCard = CGRect.zero
        var styleTitleBaseline: CGFloat = 0
        var styleChips: [CGRect] = []
        var styleHintBaseline: CGFloat = 0

        var amountCard = CGRect.zero
        var amountTitleBaseline: CGFloat = 0
        var amountFieldRect = CGRect.zero
        var amountFieldBaseline: CGFloat = 0
        var playRect = CGRect.zero
        var amountHintBaseline: CGFloat = 0
        var errorBaseline: CGFloat = 0
        var modeRects: [CGRect] = []
        var modeLabelBaselines: [CGFloat] = []
        var modeIconCenters: [CGPoint] = []
        var statusBaseline: CGFloat = 0
        var stopRect = CGRect.zero
        var stopBaseline: CGFloat = 0

        var rangeCard = CGRect.zero
        var rangeTitleBaseline: CGFloat = 0
        var rangeHintBaseline: CGFloat = 0
        var rangeRows: [CGRect] = []
        var rangeTitleBaselines: [CGFloat] = []
        var rangeSubBaselines: [CGFloat] = []
        var rangeDividerYs: [CGFloat] = []
        var rangeMarkRight: CGFloat = 0
        var rangeMarkBaselineYs: [CGFloat] = []

        var delayCard = CGRect.zero
        var delayTitleBaseline: CGFloat = 0
        var delayChips: [CGRect] = []
        var delayHintBaseline: CGFloat = 0
        var delayInnerWidth: CGFloat = 0

        var speedCard = CGRect.zero
        var speedTitleBaseline: CGFloat = 0
        var speedChips: [CGRect] = []
        var speedHintBaseline: CGFloat = 0
        var speedInnerWidth: CGFloat = 0

        var footerBaseline: CGFloat = 0
    }

    /// 行高 ≈ 1.17 × 字号（Android TextView 默认 includeFontPadding 的行框）
    private func lineHeight(_ size: CGFloat) -> CGFloat { size * 1.17 }
    /// 基线 ≈ 顶 + ascender（≈ 0.96 × 字号），与 `Draw.text` 的基线语义配合
    private func ascender(_ size: CGFloat) -> CGFloat { size * 0.96 }
    /// chip 宽度：左右 padding 各 dp(14)（Java:177）+ 文字宽
    private func chipWidth(_ text: String, size: CGFloat) -> CGFloat {
        Draw.width(text, size: size) + dp(28)
    }
    /// 居中文字的基线（视觉重心落在矩形中线）
    private func centeredBaseline(in rect: CGRect, size: CGFloat) -> CGFloat {
        rect.midY + size * 0.35
    }

    /// 整页布局：照 Java 的 `buildUi()`（Java:197-474）自顶向下的顺序走一遍纵向游标。
    private func buildMetrics() -> Metrics {
        let m = Metrics()

        let padSide = dp(14)                     // Java:208 column padding 左右 14
        let cardPad = dp(14)                     // Java:139 card padding 左右 14
        let left = padSide
        let right = Self.W - padSide
        let innerL = left + cardPad
        let innerR = right - cardPad
        let innerW = innerR - innerL

        /// Java:177 `chip.setPadding(dp(14), dp(9), dp(14), dp(9))` + 13sp 文字
        let chipH = dp(9) + dp(9) + lineHeight(sp(13))
        let chipSize = sp(13)

        var y = dp(12)                           // Java:208 paddingTop 12

        // ---------------- 标题栏（Java:212-228）----------------
        let backText = "‹ 返回"
        let backSize = sp(16)
        let titleSize = sp(19)
        let backWidth = Draw.width(backText, size: backSize) + dp(8)   // Java:220 paddingRight 8
        let barHeight = max(dp(6) + dp(6) + lineHeight(backSize), lineHeight(titleSize))
        m.backRect = CGRect(x: left, y: y, width: backWidth, height: barHeight)
        m.backBaseline = y + dp(6) + ascender(backSize)                // Java:220 paddingTop 6
        m.titleX = left + backWidth
        m.titleBaseline = y + (barHeight - lineHeight(titleSize)) / 2 + ascender(titleSize)
        y += barHeight

        // ---------------- 摘要（Java:231-238）----------------
        y += dp(10)                              // Java:237 summaryLp.topMargin = dp(10)
        let summaryHeight = dp(9) + dp(9) + lineHeight(sp(13))
        m.summaryRect = CGRect(x: left, y: y, width: right - left, height: summaryHeight)
        m.summaryBaseline = y + dp(9) + ascender(sp(13))
        y += summaryHeight

        // ---------------- ① 收款风格（Java:241-270）----------------
        y += dp(12)                              // Java:243
        let styleTop = y
        var cursor = styleTop + cardPad          // Java:139 paddingTop 12
        m.styleTitleBaseline = cursor + ascender(sp(14))               // Java:147 14sp bold
        cursor += lineHeight(sp(14))
        cursor += dp(10)                         // Java:247 styleRow paddingTop 10

        let styleLabels = ["小支收款", "小微收款", "随机小支/小微"]
        var chipX = innerL
        for (index, label) in styleLabels.enumerated() {
            let width = chipWidth(label, size: chipSize)
            m.styleChips.append(CGRect(x: chipX, y: cursor, width: width, height: chipH))
            // Java:257-263：前两个用 chipParams()（rightMargin 8），第三个直接 addView 无间距
            chipX += width + (index == styleLabels.count - 1 ? 0 : dp(8))
        }
        cursor += chipH
        cursor += dp(8)                          // Java:268 styleHintLp.topMargin = dp(8)
        m.styleHintBaseline = cursor + ascender(sp(12))                // Java:266 12sp
        cursor += lineHeight(sp(12))
        m.styleCard = CGRect(x: left, y: styleTop, width: right - left,
                             height: cursor + cardPad - styleTop)
        y = m.styleCard.maxY

        // ---------------- ② 播报金额（Java:273-368）----------------
        y += dp(12)                              // Java:275
        let amountTop = y
        cursor = amountTop + cardPad
        m.amountTitleBaseline = cursor + ascender(sp(14))              // Java:273 标题「播报金额」
        cursor += lineHeight(sp(14))
        cursor += dp(10)                         // Java:281-282 inputLp.topMargin = dp(10)

        let rowHeight = dp(48)                   // Java:323 playLp = dp(96) × dp(48)
        let playWidth = dp(96)
        let fieldWidth = innerW - playWidth - dp(10)                   // Java:324 leftMargin 10
        m.amountFieldRect = CGRect(x: innerL, y: cursor, width: fieldWidth, height: rowHeight)
        m.amountFieldBaseline = cursor + (rowHeight - lineHeight(sp(19))) / 2 + ascender(sp(19))
        m.playRect = CGRect(x: innerR - playWidth, y: cursor, width: playWidth, height: rowHeight)
        cursor += rowHeight

        cursor += dp(8)                          // Java:331-332
        m.amountHintBaseline = cursor + ascender(sp(12))               // Java:328 12sp
        cursor += lineHeight(sp(12))

        // Java:335-339 errorLine：Java 里 GONE 时高度为 0，这里**始终留一行**（见文件头偏差 ①）
        m.errorBaseline = cursor + ascender(sp(12))
        cursor += lineHeight(sp(12))

        cursor += dp(12)                         // Java:344-345 modeLp.topMargin = dp(12)
        let modeWidth = (innerW - dp(8) * 2) / 3                       // Java:347-349 weight=1 + 8 间距
        // Java:497-501 modeCard 高度 = 上下 padding 11 + 图标 22 + 间距 6 + 文字 12sp
        let modeHeight = dp(11) + dp(22) + dp(6) + lineHeight(sp(12)) + dp(11)
        for index in 0..<3 {
            let x = innerL + CGFloat(index) * (modeWidth + dp(8))
            let rect = CGRect(x: x, y: cursor, width: modeWidth, height: modeHeight)
            m.modeRects.append(rect)
            m.modeIconCenters.append(CGPoint(x: rect.midX, y: rect.minY + dp(11) + dp(11)))
            m.modeLabelBaselines.append(rect.minY + dp(11) + dp(22) + dp(6) + ascender(sp(12)))
        }
        cursor += modeHeight

        cursor += dp(10)                         // Java:354 statusLp.topMargin = dp(10)
        m.statusBaseline = cursor + ascender(sp(13))                   // Java:351-352 13sp
        cursor += lineHeight(sp(13))

        cursor += dp(10)                         // Java:366-367 stopLp.topMargin = dp(10)
        let stopHeight = dp(10) + dp(10) + lineHeight(sp(13))          // Java:364 padding 上下 10
        m.stopRect = CGRect(x: innerL, y: cursor, width: innerW, height: stopHeight)
        m.stopBaseline = cursor + dp(10) + ascender(sp(13))
        cursor += stopHeight

        m.amountCard = CGRect(x: left, y: amountTop, width: right - left,
                              height: cursor + cardPad - amountTop)
        y = m.amountCard.maxY

        // ---------------- ③ 随机金额（Java:371-378、523-560）----------------
        y += dp(12)                              // Java:373
        let rangeTop = y
        cursor = rangeTop + cardPad
        m.rangeTitleBaseline = cursor + ascender(sp(14))               // Java:371 标题 14sp bold
        m.rangeHintBaseline = cursor + ascender(sp(12))                // Java:371 hint 12sp（右对齐）
        cursor += lineHeight(sp(14))

        let rangeTitles = ["不定额（自己输）", "随机￥10-￥100之间",
                           "随机￥100-￥1000之间", "随机￥1000-￥10000之间"]
        for index in rangeTitles.indices {
            if index > 0 {
                // Java:162-170 divider()：topMargin dp(10) + 高 dp(0.7)
                cursor += dp(10)
                m.rangeDividerYs.append(cursor)
                cursor += dp(0.7)
            }
            // Java:530 `box.setPadding(0, dp(11), 0, dp(11))`
            let rowHeight = dp(11) + lineHeight(sp(14)) + lineHeight(sp(11)) + dp(11)
            let rect = CGRect(x: innerL, y: cursor, width: innerW, height: rowHeight)
            m.rangeRows.append(rect)
            m.rangeTitleBaselines.append(cursor + dp(11) + ascender(sp(14)))
            m.rangeSubBaselines.append(cursor + dp(11) + lineHeight(sp(14)) + ascender(sp(11)))
            m.rangeMarkBaselineYs.append(centeredBaseline(in: rect, size: sp(15)))
            cursor += rowHeight
        }
        m.rangeMarkRight = innerR                // Java:552-556 勾在行尾
        m.rangeCard = CGRect(x: left, y: rangeTop, width: right - left,
                             height: cursor + cardPad - rangeTop)
        y = m.rangeCard.maxY

        // ---------------- ④ 延时播放 + 播报倍速 并排（Java:381-461）----------------
        y += dp(12)                              // Java:383-385
        let bottomTop = y
        let halfWidth = (innerW - dp(10)) / 2                          // Java:388-389 / 426-428

        // 延时播放（Java:387-423）
        var delayCursor = bottomTop + cardPad
        m.delayTitleBaseline = delayCursor + ascender(sp(14))          // Java:387
        delayCursor += lineHeight(sp(14))
        delayCursor += dp(10)                    // Java:392 delayWrap paddingTop 10
        var lineX = innerL
        var lineY = delayCursor
        for (index, seconds) in Self.delays.enumerated() {
            if index % 2 == 0 && index > 0 {
                lineX = innerL
                lineY += chipH + dp(8)           // Java:413 nextLp.topMargin = dp(8)
            }
            let label = seconds == 0 ? "立即" : "\(seconds)秒"          // Java:396
            let width = chipWidth(label, size: chipSize)
            m.delayChips.append(CGRect(x: lineX, y: lineY, width: width, height: chipH))
            // Java:407 奇数位用 chipParams()（8），偶数位用 smallChipParams()（4）
            lineX += width + (index % 2 == 0 ? dp(8) : dp(4))
        }
        delayCursor = lineY + chipH
        delayCursor += dp(8)                     // Java:421-422
        m.delayHintBaseline = delayCursor + ascender(sp(11))           // Java:418 11sp
        delayCursor += lineHeight(sp(11))
        m.delayCard = CGRect(x: left, y: bottomTop, width: halfWidth,
                             height: delayCursor + cardPad - bottomTop)
        m.delayInnerWidth = halfWidth - cardPad * 2

        // 播报倍速（Java:425-462）
        let speedLeft = left + halfWidth + dp(10)                      // Java:428 leftMargin 10
        var speedCursor = bottomTop + cardPad
        m.speedTitleBaseline = speedCursor + ascender(sp(14))          // Java:425
        speedCursor += lineHeight(sp(14))
        speedCursor += dp(10)                    // Java:432 speedWrap paddingTop 10
        lineX = speedLeft + cardPad
        lineY = speedCursor
        for (index, value) in Self.speeds.enumerated() {
            if index % 2 == 0 && index > 0 {
                lineX = speedLeft + cardPad
                lineY += chipH + dp(8)           // Java:452 nextLp.topMargin = dp(8)
            }
            let label = Self.speedChipLabel(value)                     // Java:435 replace("倍速","")
            let width = chipWidth(label, size: chipSize)
            m.speedChips.append(CGRect(x: lineX, y: lineY, width: width, height: chipH))
            lineX += width + dp(8)               // Java:446 全部用 chipParams()（8）
        }
        speedCursor = lineY + chipH
        speedCursor += dp(8)                     // Java:459-460
        m.speedHintBaseline = speedCursor + ascender(sp(11))           // Java:456-458 11sp
        speedCursor += lineHeight(sp(11))
        m.speedCard = CGRect(x: speedLeft, y: bottomTop, width: halfWidth,
                             height: speedCursor + cardPad - bottomTop)
        m.speedInnerWidth = halfWidth - cardPad * 2

        y = max(m.delayCard.maxY, m.speedCard.maxY)

        // ---------------- 页脚（Java:464-471）----------------
        y += dp(16)                              // Java:470 footerLp.topMargin = dp(16)
        m.footerBaseline = y + ascender(sp(12))                        // Java:465-467 12sp
        y += lineHeight(sp(12))

        y += dp(26)                              // Java:208 paddingBottom 26
        m.contentHeight = y
        return m
    }

    /// 布局只算一次（`Draw.width` 依赖字体度量，两处调用结果一致）
    private lazy var metrics = buildMetrics()

    /// 卡片圆角 dp(14)（Java:138）
    private var cardRadius: CGFloat { dp(14) }

    // MARK: - 滚动

    /// 参考画布高 2816，内容约 3700 —— 与 Android 上「一屏放不下、可滚动」的形态一致。
    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, metrics.contentHeight - Self.H)
    }

    // MARK: - 绘制（对应 Java:197-474 的界面 + 564-624 的 bindState）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let m = metrics

        // Java:199 根容器底色。
        // 高度取「画布高」与「内容高」的较大者：滚动到底时视口下沿会超过 H（2816），
        // 只铺 0…H 的话那一段会露出宿主的白底。
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: max(Self.H, m.contentHeight)), pageBg)

        // Android 每个页面的 draw 开头都是 `c.save(); c.translate(0, -s.scroll);`
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        drawBar(d, m: m)
        drawSummary(d, m: m, s: s)

        // ① 收款风格（Java:241-270）
        drawCard(d, m.styleCard)
        d.text("收款风格", x: m.styleCard.minX + dp(14), baseline: m.styleTitleBaseline,
               size: sp(14), ink, bold: true)                          // Java:145-149
        drawStyleChips(d, m: m, s: s)
        drawFittedText(d, "随机收款方：将会随机播报小支或小微的收款",
                       x: m.styleCard.minX + dp(14), baseline: m.styleHintBaseline,
                       maxWidth: m.styleCard.width - dp(28), size: sp(12), sub)  // Java:264-267

        // ② 播报金额（Java:273-368）
        drawAmountCard(d, m: m, s: s)

        // ③ 随机金额（Java:371-378）
        drawRangeCard(d, m: m, s: s)

        // ④ 延时播放 + 播报倍速（Java:381-461）
        drawDelayCard(d, m: m, s: s)
        drawSpeedCard(d, m: m, s: s)

        // 页脚（Java:464-471）
        d.centerText("调整好手机音量开始装逼吧~", cx: Self.W / 2, baseline: m.footerBaseline,
                     size: sp(12), sub, sx: 1)

        d.ctx.restoreGState()
    }

    /// 标题栏：左「‹ 返回」+ 标题（Java:212-228）
    private func drawBar(_ d: Draw, m: Metrics) {
        // Java:216-222
        d.text("‹ 返回", x: m.backRect.minX, baseline: m.backBaseline, size: sp(16), ink)
        // Java:223-228
        d.text("支付宝到账声音", x: m.titleX, baseline: m.titleBaseline,
               size: sp(19), ink, bold: true)
    }

    /// 顶部摘要条（Java:231-238 + 632-652）
    private func drawSummary(_ d: Draw, m: Metrics, s: State) {
        d.round(m.summaryRect, radius: dp(10), brandSoft)               // Java:234
        drawFittedText(d, s.summary, x: m.summaryRect.minX + dp(12),
                       baseline: m.summaryBaseline,
                       maxWidth: m.summaryRect.width - dp(24),
                       size: sp(13), brand)
    }

    /// 三个模式图标 + 文字的小方块（Java:342-349、493-521、571-577）
    private func drawAmountCard(_ d: Draw, m: Metrics, s: State) {
        drawCard(d, m.amountCard)
        let cardLeft = m.amountCard.minX + dp(14)

        // 标题「播报金额」（Java:273）
        d.text("播报金额", x: cardLeft, baseline: m.amountTitleBaseline,
               size: sp(14), ink, bold: true)

        // 金额输入框（Java:285-313、598-611）
        let manual = s.range < 0
        let fieldAlpha: CGFloat = manual ? 1 : 0.5                      // Java:610
        d.round(m.amountFieldRect, radius: dp(10), fieldBg.withAlphaComponent(fieldAlpha))
        let trimmed = s.amount.trimmingCharacters(in: .whitespacesAndNewlines)
        if manual && !trimmed.isEmpty {
            // Java:288-289 19sp INK
            d.text(trimmed, x: m.amountFieldRect.minX + dp(14),
                   baseline: m.amountFieldBaseline, size: sp(19),
                   ink.withAlphaComponent(fieldAlpha))
        } else {
            // Java:290 hintTextColor / Java:611 setHint(...)
            let hint = manual ? "请输入金额" : "金额由随机区间决定"
            d.text(hint, x: m.amountFieldRect.minX + dp(14),
                   baseline: m.amountFieldBaseline, size: sp(19),
                   fieldHint.withAlphaComponent(fieldAlpha))
        }

        // 播报按钮（Java:316-325、614）
        d.round(m.playRect, radius: dp(10), brand)                      // Java:321
        d.centerText(s.busy ? "取消循环" : "开始播报",
                     cx: m.playRect.midX,
                     baseline: centeredBaseline(in: m.playRect, size: sp(15)),
                     size: sp(15), UIColor.white)                       // Java:318-320

        // 输入提示（Java:327-333）
        drawFittedText(d, "输入「金额」可快捷播报，装逼必备~",
                       x: cardLeft, baseline: m.amountHintBaseline,
                       maxWidth: m.amountCard.width - dp(28), size: sp(12), sub)

        // 错误行（Java:335-339、695-697）：Java 里 GONE，这里留位但不占内容
        if !s.error.isEmpty {
            drawFittedText(d, s.error, x: cardLeft, baseline: m.errorBaseline,
                           maxWidth: m.amountCard.width - dp(28),
                           size: sp(12), danger)
        }

        // 三个模式小方块（Java:342-349）
        let modeLabels = ["播报一次", "循环播报", "无限收款"]
        for (index, rect) in m.modeRects.enumerated() {
            let on = index == s.mode
            // Java:575 `on ? round(BRAND, dp(10)) : round(0xFFF4F4F9, dp(10))`
            d.round(rect, radius: dp(10), on ? brand : chipBg)
            let tint = on ? UIColor.white : brand                       // 图标是彩色矢量图
            drawModeIcon(d, index: index, center: m.modeIconCenters[index], tint: tint)
            // Java:503-510、576：文字 12sp，选中白色
            d.centerText(modeLabels[index], cx: rect.midX,
                         baseline: m.modeLabelBaselines[index],
                         size: sp(12), on ? UIColor.white : ink)
        }

        // 状态行（Java:351-356、618、677、746、765）
        drawFittedText(d, s.statusText(at: CACurrentMediaTime()),
                       x: cardLeft, baseline: m.statusBaseline,
                       maxWidth: m.amountCard.width - dp(28), size: sp(13), brand)

        // 「取消循环播放」（Java:358-368）：Java 里 busy 才 VISIBLE
        if s.busy {
            d.round(m.stopRect, radius: dp(10), stopBg)                 // Java:363
            d.roundStroke(m.stopRect, radius: dp(10), danger, width: dp(1))
            d.centerText("取消循环播放", cx: m.stopRect.midX,
                         baseline: m.stopBaseline, size: sp(13), danger)
        }
    }

    /// 随机金额四行（Java:371-378、523-560、579-584）
    private func drawRangeCard(_ d: Draw, m: Metrics, s: State) {
        drawCard(d, m.rangeCard)
        let cardLeft = m.rangeCard.minX + dp(14)
        let cardRight = m.rangeCard.maxX - dp(14)

        d.text("随机金额", x: cardLeft, baseline: m.rangeTitleBaseline,
               size: sp(14), ink, bold: true)                           // Java:371
        d.rightText("不定额时按上面输入的金额播", right: cardRight,
                    baseline: m.rangeHintBaseline, size: sp(12), sub)   // Java:371 hint

        let titles = ["不定额（自己输）", "随机￥10-￥100之间",
                      "随机￥100-￥1000之间", "随机￥1000-￥10000之间"]
        let subs = ["请输入金额", "随机播报金额区间10-100元",
                    "随机播报金额区间100-1000元", "随机播报金额区间1000-10000元"]
        let values = [-1, 0, 1, 2]

        for index in titles.indices {
            d.text(titles[index], x: cardLeft, baseline: m.rangeTitleBaselines[index],
                   size: sp(14), ink)                                    // Java:541-545
            drawFittedText(d, subs[index], x: cardLeft,
                           baseline: m.rangeSubBaselines[index],
                           maxWidth: m.rangeRows[index].width - dp(30),
                           size: sp(11), sub)                            // Java:546-550
            // Java:552-556、583 勾：选中那一行显示「✓」
            if values[index] == s.range {
                d.rightText("✓", right: m.rangeMarkRight,
                            baseline: m.rangeMarkBaselineYs[index],
                            size: sp(15), brand)
            }
            // Java:162-170 divider()
            if index > 0 {
                d.rect(CGRect(x: cardLeft, y: m.rangeDividerYs[index - 1],
                              width: cardRight - cardLeft, height: dp(0.7)), lineColor)
            }
        }
    }

    /// 延时播放卡（Java:387-423）
    private func drawDelayCard(_ d: Draw, m: Metrics, s: State) {
        drawCard(d, m.delayCard)
        let cardLeft = m.delayCard.minX + dp(14)
        d.text("延时播放", x: cardLeft, baseline: m.delayTitleBaseline,
               size: sp(14), ink, bold: true)                            // Java:387
        for (index, rect) in m.delayChips.enumerated() {
            let seconds = Self.delays[index]
            let label = seconds == 0 ? "立即" : "\(seconds)秒"
            // Java:587-589 `DELAYS[i] == SoundStore.delay()`
            drawChip(d, rect: rect, text: label, on: seconds == s.delay)
        }
        drawFittedText(d, "选了延时，到点才响", x: cardLeft,
                       baseline: m.delayHintBaseline, maxWidth: m.delayInnerWidth,
                       size: sp(11), sub)                                // Java:417-423
    }

    /// 播报倍速卡（Java:425-462）
    private func drawSpeedCard(_ d: Draw, m: Metrics, s: State) {
        drawCard(d, m.speedCard)
        let cardLeft = m.speedCard.minX + dp(14)
        d.text("播报倍速", x: cardLeft, baseline: m.speedTitleBaseline,
               size: sp(14), ink, bold: true)                            // Java:425
        for (index, rect) in m.speedChips.enumerated() {
            let value = Self.speeds[index]
            // Java:591-593 `SPEEDS[i] == SoundStore.speed()`
            drawChip(d, rect: rect, text: Self.speedChipLabel(value),
                     on: value == s.speed)
        }
        // Java:594-596 `speedHintLine.setText("当前播报倍速是：" + speedLabel(speed))`
        drawFittedText(d, "当前播报倍速是：" + Self.speedLabel(s.speed),
                       x: cardLeft, baseline: m.speedHintBaseline,
                       maxWidth: m.speedInnerWidth, size: sp(11), sub)
    }

    /// 一张白卡（Java:135-160 `card()`：白底 + dp(14) 圆角）
    private func drawCard(_ d: Draw, _ rect: CGRect) {
        d.round(rect, radius: cardRadius, cardColor)
    }

    /// 收款风格三个 chip（Java:249-263、566-569）
    private func drawStyleChips(_ d: Draw, m: Metrics, s: State) {
        let labels = ["小支收款", "小微收款", "随机小支/小微"]
        for (index, rect) in m.styleChips.enumerated() {
            let on: Bool
            switch index {
            case 0: on = !s.randomStyle && s.style == Self.styleZhi
            case 1: on = !s.randomStyle && s.style == Self.styleWei
            default: on = s.randomStyle
            }
            drawChip(d, rect: rect, text: labels[index], on: on)
        }
    }

    /// chip 样式（Java:172-182、626-629 `paintChip`）：选中 BRAND 底白字，未选中 CHIP_BG + 1dp 描边
    private func drawChip(_ d: Draw, rect: CGRect, text: String, on: Bool) {
        let radius = dp(18)
        if on {
            d.round(rect, radius: radius, brand)
        } else {
            d.round(rect, radius: radius, chipBg)
            d.roundStroke(rect, radius: radius, lineColor, width: dp(1))
        }
        d.centerText(text, cx: rect.midX,
                     baseline: centeredBaseline(in: rect, size: sp(13)),
                     size: sp(13), on ? UIColor.white : ink)
    }

    /// 三个模式图标 —— Java 用 `R.drawable.sound_once / sound_loop / sound_infinite`，
    /// iOS 没有对应素材，这里用图形画出来（见文件头偏差 ③）：
    ///   0 播报一次 → 实心三角；1 循环播报 → 缺口圆环 + 箭头；2 无限收款 → 双环。
    private func drawModeIcon(_ d: Draw, index: Int, center: CGPoint, tint: UIColor) {
        let stroke = dp(2.2)
        switch index {
        case 1:
            // 循环：缺口圆环
            let radius = dp(9)
            let box = CGRect(x: center.x - radius, y: center.y - radius,
                             width: radius * 2, height: radius * 2)
            d.arc(rect: box, startDegrees: 40, sweepDegrees: 280,
                  color: tint, width: stroke)
            // 环口的小箭头（指向前进方向）
            let angle = CGFloat(40) * .pi / 180
            let tipX = center.x + cos(angle) * radius
            let tipY = center.y + sin(angle) * radius
            d.path(points: [
                (tipX - dp(5), tipY - dp(4)),
                (tipX + dp(5), tipY + dp(1)),
                (tipX - dp(4), tipY + dp(4)),
            ], fill: tint)
        case 2:
            // 无限：两个并排的环
            let ringW = dp(9)
            let ringH = dp(10)
            let first = CGRect(x: center.x - ringH / 2 - ringH / 2 + dp(0.5),
                               y: center.y - ringH / 2, width: ringW, height: ringH)
            let second = CGRect(x: center.x + ringH / 2 - ringW + ringH / 2,
                                y: center.y - ringH / 2, width: ringW, height: ringH)
            d.roundStroke(first, radius: ringH / 2, tint, width: stroke)
            d.roundStroke(second, radius: ringH / 2, tint, width: stroke)
        default:
            // 播报一次：实心三角（向右）
            d.path(points: [
                (center.x - dp(5), center.y - dp(9)),
                (center.x + dp(9), center.y),
                (center.x - dp(5), center.y + dp(9)),
            ], fill: tint)
        }
    }

    /// 单行文字，超宽时自动缩字号（Java 是换行 + 卡片撑高，见文件头偏差 ②）
    private func drawFittedText(_ d: Draw, _ text: String, x: CGFloat, baseline: CGFloat,
                                maxWidth: CGFloat, size: CGFloat, _ color: UIColor) {
        d.text(text, x: x, baseline: baseline,
               size: fitting(text, maxWidth: maxWidth, size: size), color)
    }

    /// 缩字号：最小缩到 0.7 倍，再窄就让它出界（总比看不见强）
    private func fitting(_ text: String, maxWidth: CGFloat, size: CGFloat) -> CGFloat {
        guard maxWidth > 1, size > 0, !text.isEmpty else { return size }
        let width = Draw.width(text, size: size)
        guard width > maxWidth else { return size }
        return max(size * 0.7, size * maxWidth / width)
    }

    // MARK: - 命中（对应 Java 里各控件的 setOnClickListener）

    /// 命中顺序与 Java 的点击监听一一对应；**命中即执行**（见文件头「交互令牌」）。
    /// 入参已是**内容坐标**（宿主把 `state.scroll` 加回来了），所以不再二次加滚动量。
    ///
    /// 令牌词表（其余一律 `"noop"`）：
    ///   · `"back"`        —— 返回（宿主 pop）
    ///   · `"edit:amount"` —— 金额输入框（宿主弹 `EditPrompt`，写回走 `EditableState`）
    ///   · `"soundPlay"` / `"soundStop"` —— 「开始播报 / 取消循环」与「取消循环播放」。
    ///     Router 里已有这两个分支（只做 `host?.setNeedsDisplay()`，不重复执行动作），
    ///     借用它把「立刻重绘」拿到手：播放状态是用户最需要马上看到反馈的一处。
    ///   · `"noop"`        —— 其余设置项：已经在 `hitTest` 里改完状态，靠宿主 250ms 的
    ///     重绘心跳刷出来（Java 是 `bindState()` 直接改 View，没有这一帧延迟）。
    ///
    /// 为什么设置项不返回 `"sstyle:0"` 这类语义令牌：Java 那边它们只是 `setOnClickListener`
    /// 里改 `SoundStore` + `bindState()`，没有任何跳转；而 Router 里没有它们的分支，
    /// 返回未登记的令牌只会落到 `default: break`，还会被 `_ios/lint-swift.mjs` 的
    /// 「令牌必须在 Router 里有分支」判红。语义记在注释里就够了。
    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        guard let s = state as? State else { return "noop" }
        let m = metrics
        let p = CGPoint(x: x, y: y)

        // Java:221 `back.setOnClickListener(v -> finish())`
        if m.backRect.contains(p) { return "back" }

        // Java:249-263 收款风格三选一（小支 / 小微 / 随机）
        for (index, rect) in m.styleChips.enumerated() where rect.contains(p) {
            if index == 2 {
                s.randomStyle.toggle()                                  // Java:259
            } else {
                // 选具体收款方时必须退出“随机”，否则 randomStyle 仍为 true，
                // 画面上随机 chip 一直亮着，点小支/小微看起来像没反应。
                s.randomStyle = false
                s.style = index == 0 ? Self.styleZhi : Self.styleWei      // Java:250、254
            }
            afterSettingChange(s)                                        // Java:251/255/260 bindState()
            return "noop"
        }

        // Java:285-312 金额输入框：随机额度时被锁（Java:609 setEnabled(manual)）
        if m.amountFieldRect.contains(p) {
            guard s.range < 0 else { return "noop" }
            return "edit:amount"
        }

        // Java:322 `playButton.setOnClickListener(v -> onMainButton())`
        // 同一个按钮两副面孔：不忙时「开始播报」、忙时「取消循环」（Java:613-614）
        if m.playRect.contains(p) {
            let wasBusy = s.busy
            onMainButton(s)
            if wasBusy { return "soundStop" }
            return "soundPlay"
        }

        // Java:512-517 模式卡：正在循环时先停循环再换档
        for (index, rect) in m.modeRects.enumerated() where rect.contains(p) {
            if s.busy { stopLooping(s, tell: false) }                     // Java:514
            s.mode = index                                               // Java:515
            afterSettingChange(s)
            return "noop"
        }

        // Java:365 `stopLoop.setOnClickListener(v -> stopLooping(true))`
        if s.busy && m.stopRect.contains(p) {
            stopLooping(s, tell: true)
            return "soundStop"
        }

        // Java:531-534 随机金额四行
        for (index, rect) in m.rangeRows.enumerated() where rect.contains(p) {
            s.range = index == 0 ? -1 : index - 1                         // Java:532
            afterSettingChange(s)
            return "noop"
        }

        // Java:394-401 延时
        for (index, rect) in m.delayChips.enumerated() where rect.contains(p) {
            s.delay = Self.delays[index]                                  // Java:397
            afterSettingChange(s)
            return "noop"
        }

        // Java:434-440 倍速
        for (index, rect) in m.speedChips.enumerated() where rect.contains(p) {
            s.speed = Self.speeds[index]                                  // Java:436
            afterSettingChange(s)
            return "noop"
        }

        return "noop"
    }

    /// Java:564-624 `bindState()` 里与设置项有关的那一半：清错误行、不忙时把状态行复位。
    /// （选中态与摘要都是按 State 现算的，不需要额外「刷新」。）
    private func afterSettingChange(_ s: State) {
        s.error = ""                                                    // Java:622
        if !s.busy { s.status = "设置成功，播报一下看看吧" }              // Java:616-618
        s.save()
    }

    // MARK: - 播放控制（对应 Java:662-752）

    /// 正在播放的音频（Java 的 `AccountSoundPlayer`）
    private let player = Player()
    /// 播放代次：每次起播/停止都 +1，用来作废已经排上队的延时任务
    private var playbackToken = 0
    /// 排着的延时任务（Java 的 `handler.postDelayed`）
    private var pending: DispatchWorkItem?

    deinit {
        player.stop()
        pending?.cancel()
    }

    /// Java:662-682 `onMainButton()`
    private func onMainButton(_ s: State) {
        if s.busy {
            stopLooping(s, tell: true)                                  // Java:663-666
            return
        }
        if s.mode == Self.modeOnce {
            playOnce(s)                                                 // Java:668-671
            return
        }
        s.looping = s.mode == Self.modeLoop                             // Java:672
        s.infinite = s.mode == Self.modeInfinite                        // Java:673
        s.error = ""                                                    // Java:674
        s.busySince = CACurrentMediaTime()
        s.status = s.infinite ? "无限收款模式已开启" : "即将开始循环播报"  // Java:677
        s.save()
        // Java:681 「延时」在第一次播放前生效
        beginPlayback(s, after: TimeInterval(max(0, s.delay)))
    }

    /// Java:684-701 `playOnce()`
    private func playOnce(_ s: State) {
        let amount = nextAmount(s, randomAlways: false)                  // Java:685
        if let message = Self.errorMessage(for: amount) {                // Java:696-698
            s.error = message
            s.status = "设置成功，播报一下看看吧"
            return
        }
        s.error = ""
        s.playing = true
        playbackToken += 1
        let token = playbackToken
        player.play(amount: amount, speed: s.speed, wechat: nextStyle(s)) { [weak self] in
            guard self?.playbackToken == token else { return }
            s.playing = false
            if !s.busy { s.status = "设置成功，播报一下看看吧" }          // Java:691
        }
    }

    /// Java:704-722 `playRound()`：循环里的每一轮
    private func playRound(_ s: State, token: Int) {
        guard token == playbackToken, s.busy else { return }             // Java:705
        let amount = nextAmount(s, randomAlways: s.infinite)              // Java:706
        if let message = Self.errorMessage(for: amount) {                 // Java:716-720
            s.error = message
            stopLooping(s, tell: false)
            return
        }
        s.error = ""
        s.playing = true
        player.play(amount: amount, speed: s.speed, wechat: nextStyle(s)) { [weak self] in
            guard let self, token == self.playbackToken else { return }
            s.playing = false
            guard s.busy else {                                          // Java:711
                s.status = "设置成功，播报一下看看吧"
                return
            }
            // Java:712 一轮结束后隔 5 秒再来一轮
            self.schedule(after: TimeInterval(Self.loopGapMs) / 1000) { [weak self] in
                self?.playRound(s, token: token)
            }
        }
    }

    /// Java:738-752 `stopLooping(boolean tell)`
    ///
    /// `tell` 对应 Java:747 的 `toast("已取消循环播放")`：这一层没有全局 toast 通道，
    /// 而状态行（Java:746）已经写成「已暂停循环播报」，语义等价，所以 `tell` 只保留在签名里
    /// 以便将来接 toast 通道 —— 行为上不弹任何东西。
    private func stopLooping(_ s: State, tell: Bool) {
        _ = tell
        let was = s.busy
        s.looping = false                                                // Java:740
        s.infinite = false                                               // Java:741
        playbackToken += 1                                               // Java:742 removeCallbacksAndMessages
        pending?.cancel()
        pending = nil
        player.stop()                                                    // Java:744
        s.playing = false
        if was {
            s.status = "已暂停循环播报"                                    // Java:746
        }
        s.save()
    }

    /// Java:681 / 712 的 `handler.postDelayed`
    private func schedule(after delay: TimeInterval, _ body: @escaping () -> Void) {
        pending?.cancel()
        let item = DispatchWorkItem(block: body)
        pending = item
        if delay <= 0 {
            DispatchQueue.main.async(execute: item)
        } else {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
        }
    }

    /// 起播的第一轮（延时已由 Java 的 postDelayed 语义吸收进 `after`）
    private func beginPlayback(_ s: State, after delay: TimeInterval) {
        playbackToken += 1
        let token = playbackToken
        schedule(after: delay) { [weak self] in
            self?.playRound(s, token: token)
        }
    }

    /// Java:725-732 `nextAmount(boolean randomAlways)`
    private func nextAmount(_ s: State, randomAlways: Bool) -> String {
        var bounds = Self.rangeBounds(s.range)
        if bounds == nil && randomAlways {
            bounds = Self.rangeBounds(Int.random(in: 0...2))
        }
        if let bounds {
            let low = bounds.0
            let high = max(low, bounds.1)
            return String(Int.random(in: low...high))
        }
        return Self.normalize(s.amount)
    }

    /// Java:734-736 `nextStyle()`
    private func nextStyle(_ s: State) -> Bool {
        s.randomStyle ? Bool.random() : s.style == Self.styleWei
    }

    // MARK: - 文案与金额（对应 Java:654-658 与 SoundScript / SoundStore）

    /// Java:137-143 `SoundStore.rangeBounds(int)` → 区间两端（元）
    static func rangeBounds(_ index: Int) -> (Int, Int)? {
        switch index {
        case 0: return (10, 100)
        case 1: return (100, 1000)
        case 2: return (1000, 10000)
        default: return nil
        }
    }

    /// Java:654-658 `speedLabel(float)`：`%.2f` 后把 `x.y0` 的末位 0 去掉，再拼「倍速」
    static func speedLabel(_ value: CGFloat) -> String {
        var text = String(format: "%.2f", Double(value))
        let chars = Array(text)
        let count = chars.count
        if count >= 4, chars[count - 1] == "0", chars[count - 2].isNumber, chars[count - 3] == "." {
            text = String(chars[0..<(count - 1)])
        }
        return text + "倍速"
    }

    /// 倍速 chip 上的字（Java:435 `speedLabel(value).replace("倍速", "")`）
    static func speedChipLabel(_ value: CGFloat) -> String {
        speedLabel(value).replacingOccurrences(of: "倍速", with: "")
    }

    /// SoundScript.normalize（SoundScript.java:105-109）：去掉货币符号、千分位与空格
    static func normalize(_ amount: String) -> String {
        amount
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "￥", with: "")
            .replacingOccurrences(of: "¥", with: "")
            .replacingOccurrences(of: ",", with: "")
            .replacingOccurrences(of: " ", with: "")
    }

    /// SoundScript.build 的三条校验（SoundScript.java:67-76）：能播返回 nil，不能播返回给用户看的那句话
    static func errorMessage(for amount: String) -> String? {
        let text = normalize(amount)
        if text.isEmpty { return "没有输入内容" }
        let pattern = "^\\d{1,12}(\\.\\d{1,2})?$"
        guard text.range(of: pattern, options: .regularExpression) != nil else {
            return "输入格式不对"
        }
        return nil
    }

    // MARK: - 音频播放器（Java 的 AccountSoundPlayer，用 AVAudioPlayer 重写）

    /// 一条播报的播放器。
    ///
    /// 只做两件事：放一段声音、放完回调一次。循环/延时/随机节奏都在上一层的 `playRound` 里，
    /// 与 Java「播放器只管放完一条」的分工一致（AccountSoundPlayer.java:19-20）。
    ///
    /// 素材策略（见文件头）：
    ///   1. 包里有 `account_sound_zhi.mp3` / `account_sound_wei.mp3` 就放它（倍速走 `rate`）；
    ///   2. 没有就现算一段双音提示（`chime()`，纯内存 WAV，无素材）；
    ///   3. 连生成都失败（理论上不会）就立刻回调，让循环继续 —— 不崩、不卡死。
    private final class Player: NSObject, AVAudioPlayerDelegate {
        private var audio: AVAudioPlayer?
        private var queue: [AVAudioPlayer] = []
        private var index = 0
        private var onFinish: (() -> Void)?
        private static var chimeCache: Data?

        /// 和 Android AccountSoundPlayer 一样：把一条播报拆成多个音素，按顺序播放。
        func play(amount: String, speed: CGFloat, wechat: Bool,
                  completion: @escaping () -> Void) {
            stop()
            onFinish = completion
            try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
            try? AVAudioSession.sharedInstance().setActive(true)
            queue = Self.sequence(amount: amount, wechat: wechat).compactMap {
                Self.makePlayer($0, speed: speed)
            }
            if queue.isEmpty {
                if let payload = Self.chime(), let fallback = try? AVAudioPlayer(data: payload) {
                    queue = [fallback]
                }
            }
            index = 0
            playNext()
        }

        func stop() {
            audio?.stop()
            audio = nil
            queue.forEach { $0.stop() }
            queue.removeAll()
            index = 0
            onFinish = nil
        }

        private func playNext() {
            guard index < queue.count else {
                finish()
                return
            }
            let next = queue[index]
            index += 1
            next.delegate = self
            _ = next.prepareToPlay()
            _ = next.play()
            audio = next
        }

        private func finish() {
            let done = onFinish
            onFinish = nil
            audio = nil
            queue.removeAll()
            done?()
        }

        func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
            playNext()
        }

        // MARK: 音素序列（对应 Android p.g.b / b.d.q）

        private static func sequence(amount: String, wechat: Bool) -> [String] {
            var out = ["diaoluo_da.mp3",
                       wechat ? "tts_wxsuccess.mp3" : "tts_success.mp3"]
            let cleaned = amount
                .replacingOccurrences(of: "￥", with: "")
                .replacingOccurrences(of: "¥", with: "")
                .replacingOccurrences(of: ",", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let parts = cleaned.split(separator: ".", omittingEmptySubsequences: false)
            guard let first = parts.first, let integer = Int(first) else {
                out.append("tts_0.mp3")
                out.append("tts_yuan.mp3")
                return out
            }
            appendInteger(integer, to: &out)
            if parts.count > 1, !parts[1].isEmpty {
                out.append("tts_dot.mp3")
                for ch in parts[1].prefix(2) {
                    if let digit = ch.wholeNumberValue, (0...9).contains(digit) {
                        out.append("tts_\(digit).mp3")
                    }
                }
            }
            out.append("tts_yuan.mp3")
            return out
        }

        private static func appendInteger(_ value: Int, to out: inout [String]) {
            guard value > 0 else {
                out.append("tts_0.mp3")
                return
            }
            // 亿位（Android 用 tts_ten_million 这个音素名）
            let yi = value / 100_000_000
            if yi > 0 {
                appendGroup(yi, leadingTen: true, to: &out)
                out.append("tts_ten_million.mp3")
            }
            let wan = (value / 10_000) % 10_000
            let rest = value % 10_000
            if wan > 0 {
                if yi > 0 && wan < 1000 { out.append("tts_0.mp3") }
                appendGroup(wan, leadingTen: yi > 0, to: &out)
                out.append("tts_ten_thousand.mp3")
            }
            if rest > 0 || (wan == 0 && yi == 0) {
                if (wan > 0 || yi > 0) && rest < 1000 { out.append("tts_0.mp3") }
                appendGroup(rest, leadingTen: wan > 0 || yi > 0, to: &out)
            }
        }

        private static func appendGroup(_ value: Int, leadingTen: Bool,
                                        to out: inout [String]) {
            let units = [1000, 100, 10, 1]
            let names: [String?] = ["tts_thousand.mp3", "tts_hundred.mp3",
                                    "tts_ten.mp3", nil]
            var pendingZero = false
            var seen = false
            for i in 0..<4 {
                let digit = (value / units[i]) % 10
                if digit == 0 {
                    if seen { pendingZero = true }
                    continue
                }
                if pendingZero {
                    out.append("tts_0.mp3")
                    pendingZero = false
                }
                // 十、十一、十二……前面不加「一」；但整万/整亿的前导组保留。
                if !(i == 2 && digit == 1 && !seen && !leadingTen) {
                    out.append("tts_\(digit).mp3")
                }
                if let name = names[i] { out.append(name) }
                seen = true
            }
        }

        private static func makePlayer(_ name: String, speed: CGFloat) -> AVAudioPlayer? {
            let ns = name as NSString
            let resource = ns.deletingPathExtension
            let ext = ns.pathExtension
            let url = Bundle.main.url(forResource: resource, withExtension: ext,
                                      subdirectory: "web/assets/audio/account_sound")
                ?? Bundle.main.url(forResource: resource, withExtension: ext)
            guard let url, let player = try? AVAudioPlayer(contentsOf: url) else { return nil }
            player.enableRate = true
            player.rate = Float(max(0.5, min(2.0, speed)))
            return player
        }

        /// 合成一段「叮—咚」（880Hz + 1318.5Hz），返回 WAV 字节；全部用安全运算，失败返回 nil。
        private static func chime() -> Data? {
            if let cached = chimeCache { return cached }
            let sampleRate: Double = 22050
            let sampleRateU: UInt32 = 22050
            let tones: [(frequency: Double, seconds: Double)] = [(880.0, 0.16), (1318.5, 0.42)]
            var samples: [Int16] = []
            for tone in tones {
                let count = Int(sampleRate * tone.seconds)
                if count <= 0 { continue }
                for index in 0..<count {
                    let t = Double(index) / sampleRate
                    let envelope = exp(-6.0 * t / max(0.02, tone.seconds))
                    let wave = sin(2 * Double.pi * tone.frequency * t) * envelope * 0.35
                    let clamped = max(-1.0, min(1.0, wave))
                    samples.append(Int16(clamped * 30000))
                }
            }
            guard !samples.isEmpty else { return nil }

            var data = Data()
            let dataBytes = UInt32(samples.count * 2)
            let byteRate = sampleRateU * 2
            data.append(contentsOf: Array("RIFF".utf8))
            appendUInt32(&data, 36 + dataBytes)
            data.append(contentsOf: Array("WAVE".utf8))
            data.append(contentsOf: Array("fmt ".utf8))
            appendUInt32(&data, 16)                 // fmt 块长度
            appendUInt16(&data, 1)                  // PCM
            appendUInt16(&data, 1)                  // 单声道
            appendUInt32(&data, sampleRateU)        // 采样率
            appendUInt32(&data, byteRate)           // 每秒字节数
            appendUInt16(&data, 2)                  // 块对齐
            appendUInt16(&data, 16)                 // 位深
            data.append(contentsOf: Array("data".utf8))
            appendUInt32(&data, dataBytes)
            for sample in samples {
                appendUInt16(&data, UInt16(bitPattern: sample))
            }
            chimeCache = data
            return data
        }

        private static func appendUInt32(_ data: inout Data, _ value: UInt32) {
            data.append(UInt8(value & 0xFF))
            data.append(UInt8((value >> 8) & 0xFF))
            data.append(UInt8((value >> 16) & 0xFF))
            data.append(UInt8((value >> 24) & 0xFF))
        }

        private static func appendUInt16(_ data: inout Data, _ value: UInt16) {
            data.append(UInt8(value & 0xFF))
            data.append(UInt8((value >> 8) & 0xFF))
        }
    }
}

// MARK: - 设置持久化（Java 的 SoundStore → UserDefaults）

/// `SoundStore` 的七个字段落到 `UserDefaults`（Java 是按账号存 SharedPreferences，
/// 见 SoundStore.java:161；iOS 这一层没有账号存档通道，先用标准 defaults，键名同 Java）。
private enum SoundDefaults {
    private static let prefix = "doutu.sound."

    static func string(_ key: String, _ fallback: String) -> String {
        UserDefaults.standard.string(forKey: prefix + key) ?? fallback
    }

    static func int(_ key: String, _ fallback: Int) -> Int {
        (UserDefaults.standard.object(forKey: prefix + key) as? Int) ?? fallback
    }

    static func double(_ key: String, _ fallback: Double) -> Double {
        (UserDefaults.standard.object(forKey: prefix + key) as? Double) ?? fallback
    }

    static func bool(_ key: String, _ fallback: Bool) -> Bool {
        (UserDefaults.standard.object(forKey: prefix + key) as? Bool) ?? fallback
    }

    static func set(_ value: Any, _ key: String) {
        UserDefaults.standard.set(value, forKey: prefix + key)
    }
}

// MARK: - 可编辑金额（Java:285-312 的 EditText）

/// 「播报金额」输入框：Java 用的是 `EditText` + `InputType.TYPE_CLASS_NUMBER | DECIMAL`。
/// iOS 这一层所有输入都走统一的 `EditPrompt`（`EditableState`），
/// 所以只要实现「标题 / 当前值 / 写回」三件事，`hitTest` 的 `"edit:amount"` 就活了。
extension AccountSoundScreen.State: EditableState {
    func editTitle(_ key: String) -> String { "播报金额" }

    func editValue(_ key: String) -> String { amount }

    func applyEdit(_ key: String, _ text: String) {
        amount = text.trimmingCharacters(in: .whitespacesAndNewlines)
        error = ""
        status = "设置成功，播报一下看看吧"
        save()
    }
}
