import UIKit

/// 「我 → 求新功能」：意见反馈页 —— `features/feedback/FeedbackActivity.java`（439 行）的 1:1 移植。
///
/// ## 与原页面的形态差异（必须先看这三条）
///
/// 1. **原页面没有参考画布**。Java 用的是系统控件（`ScrollView` + `LinearLayout` + `EditText`
///    + `Button`），既没有 `W`/`H` 常量，也没有一条 `draw(Canvas, State)`（Java:41-49 的注释
///    还专门解释了「为什么用系统控件而不是手绘」）。所以这里按本仓库的逐屏模型把它**重画**
///    到一张 **1280×2816** 的参考画布上（与其它 17 屏一致）—— 这个尺寸是**新定的**，
///    不是从 Java 抄来的。
///
/// 2. **尺寸全部是 `dp()`**（Java:89-91 `dp(v) = v * density`）。这里按「参考画布 1280 宽
///    = 360dp 设计宽」折算：1dp = 1280 / 360 = **32/9 ≈ 3.5556** 参考单位。
///    （1280×2816 的长宽比 0.4545 与 360×792dp 完全相同，所以纵向用同一个系数也对得上。）
///    `textSize` 的 sp 按 fontScale = 1 处理，与 dp 同一系数。
///
/// 3. **基线是新推的**。Java 把排版交给 TextView，源码里没有任何基线数字。这里取
///    Android CJK 的常规 metrics：行高 ≈ `1.2 × 字号`、基线相对行顶 ≈ `0.93 × 字号`
///    （见 `baselineOffset` / `lineHeight`）。字号、颜色、文案、间距则是逐条照抄。
///
/// ## 输入交互
///
/// iOS 侧**不做键盘**。本页只把「用户点了输入框」这件事暴露成令牌（`feedback:focus` /
/// `feedback:contact`），由上层弹系统输入框，再把文本写回 `State.content` / `State.contact`。
/// Java 里每一处 `setOnClickListener` / 可点区域都保留为 `hitTest` 分支（见文件末尾）。
final class FeedbackScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// 这一屏的 `draw` **不自带** `translate(0, -scroll)` —— 内容比画布高时由宿主统一滚，
    /// 与 WithdrawDoneScreen 的做法一致（Java 侧滚动本来是外层 `ScrollView` 干的，Java:97-106）。
    let selfScrolls = false

    /// dp → 参考画布单位（见文件头第 2 条）
    private let unit: CGFloat = 32.0 / 9.0
    private func dp(_ value: CGFloat) -> CGFloat { value * unit }

    // MARK: - 调色板（Java:63-66）

    private let brand = Draw.color(0xff6f6ae8)           // BRAND
    private let brandDisabled = Draw.color(0xffc9c6e8)   // BRAND_DISABLED
    private let ink = Draw.color(0xff23232f)             // INK
    private let sub = Draw.color(0xff8a8a9a)             // SUB
    private let pageBg = Draw.color(0xfff5f6fa)          // Java:95 root.setBackgroundColor
    private let thumbBg = Draw.color(0xffededf3)         // Java:248 缩略图底
    private let removeBg = Draw.color(0xcc000000)        // Java:256 删除角标底
    private let addBorder = Draw.color(0xffc7c3f0)       // Java:281 「添加图片」描边

    // MARK: - 文案（逐字照抄 Java）

    private let backText = "‹ 返回"                                        // Java:115
    private let titleText = "求新功能"                                     // Java:123
    private let hintText = "想要什么功能、哪里用着别扭，都可以告诉我们。支持上传截图，最长 1000 字。"  // Java:130
    private let contentLabel = "你的意见 / 建议"                            // Java:149
    private let contentPlaceholder = "例如：希望增加批量去水印、希望支持小红书图文…"  // Java:152
    private let contactLabel = "联系方式（选填）"                            // Java:179
    private let contactPlaceholder = "手机号 / 微信 / QQ，方便我们回复你"     // Java:182
    private let addImageText = "+ 添加图片"                                // Java:273
    private var imagesLabel: String { "上传图片（选填，最多 \(State.maxImages) 张）" }  // Java:140

    // MARK: - 状态

    /// 页面可变状态 —— 对应 Java 的 `images` / `content` / `contact` / `sending` 四个字段。
    final class State: ScreenState {
        /// 最多上传几张图（Java:53 `MAX_IMAGES`，与服务端 MAX_IMAGES 对齐）
        static let maxImages = 3
        /// 意见正文上限（Java:59 `CONTENT_MAX`，Java:158 用 LengthFilter 截断）
        static let contentMax = 1000

        /// 意见正文（Java:72 `EditText content`）
        var content = ""
        /// 联系方式（Java:73 `EditText contact`），单行
        var contact = ""
        /// 已选好的图片缩略图（Java:69 存的是 base64 data-URL；iOS 侧由宿主解成 UIImage 传进来）
        var images: [UIImage] = []
        /// 是否正在提交（Java:76 `boolean sending`）
        var sending = false
        /// 提交中的按钮文案（Java:399 `label`，nil 时用「提交中…」）
        var busyLabel: String?

        /// 按钮上的字（Java:399 `submit.setText(busy ? (label == null ? "提交中…" : label) : "提交")`）
        var submitTitle: String { sending ? (busyLabel ?? "提交中…") : "提交" }

        /// 按钮着色（Java:400 `busy ? BRAND_DISABLED : BRAND`）
        var submitEnabled: Bool { !sending }

        /// 是否可以真的提交（Java:406-409：trim 后不足 2 个字就 toast 拦住）
        var canSubmit: Bool {
            content.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2
        }

        /// 写入正文，按 `InputFilter.LengthFilter(CONTENT_MAX)` 截断（Java:58、Java:158）
        func setContent(_ text: String) {
            content = String(text.prefix(Self.contentMax))
        }

        /// 还能再加几张图（Java:271 `images.size() < MAX_IMAGES`）
        var canAddImage: Bool { !sending && images.count < Self.maxImages }

        /// 是否可以删图（Java:261 `if (sending) return;`）
        var canRemoveImage: Bool { !sending }

        // 以下是宿主需要的提示文案，原样对应 Java 里的 toast / AlertDialog，
        // 本屏自己不弹（与 Java:407、417、323、425-434 一致）。
        static let needContentMessage = "请先写两句你的意见或建议"                        // Java:407
        static let submitFailMessage = "提交失败，请稍后重试"                            // Java:417
        static let imageReadFailMessage = "这张图片读不出来，换一张试试"                  // Java:323
        static let thanksTitle = "感谢您的反馈！"                                        // Java:427
        static let thanksMessage = "我们已经收到你的建议，会认真阅读每一条。\n你的想法很可能就是下一个版本里的新功能，谢谢你帮我们把它做得更好。"  // Java:428
        static let thanksButton = "好的"                                               // Java:430
        /// 图片压缩目标（Java:54-57）：单张 ≤ 350KB、最长边 1280、JPEG 质量 82 起降
        static let imageTargetBytes = 350 * 1024
        static let imageMaxSide: CGFloat = 1280
        static let imageQuality: CGFloat = 82

        /// 机型 / 系统版本（Java:412-413 `Build.MANUFACTURER + " " + Build.MODEL`、`"Android " + RELEASE`）
        /// —— iOS 侧由宿主填，语义等价。
        var deviceModel = ""
        var osVersion = ""
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return max(0, layout(s).contentHeight - Self.H)
    }

    // MARK: - 排版（Java `buildUi` 的 LinearLayout 顺序堆叠 → 游标累加）

    /// 一次排版算出来的全部几何（`draw` 与 `hitTest` 共用同一份，保证画到哪就点到哪）。
    private struct Layout {
        var back = CGRect.zero
        var backBaseline: CGFloat = 0
        var titleX: CGFloat = 0
        var titleBaseline: CGFloat = 0
        var hintLines: [String] = []
        var hintBaselines: [CGFloat] = []
        var imagesLabelBaseline: CGFloat = 0
        var thumbRects: [CGRect] = []
        var removeRects: [CGRect] = []
        var addRect = CGRect.zero
        var contentLabelBaseline: CGFloat = 0
        var contentBox = CGRect.zero
        var contentLines: [String] = []
        var contentFirstBaseline: CGFloat = 0
        var counterBaseline: CGFloat = 0
        var contactLabelBaseline: CGFloat = 0
        var contactBox = CGRect.zero
        var contactBaseline: CGFloat = 0
        var submitRect = CGRect.zero
        var submitBaseline: CGFloat = 0
        /// 整个 column 的高度（Java 的 wrap_content，用来算可滚动距离）
        var contentHeight: CGFloat = 0
    }

    /// 基线相对行顶的偏移：Android CJK `Paint.FontMetrics.ascent ≈ 0.93 × textSize`
    private func baselineOffset(_ size: CGFloat) -> CGFloat { size * 0.93 }
    /// 单行行高：Android 默认 `1.2 × textSize`
    private func lineHeight(_ size: CGFloat) -> CGFloat { size * 1.2 }

    /// 按宽度折行（对应 TextView 的自动换行；`\n` 强制换行）
    private func wrap(_ text: String, size: CGFloat, bold: Bool = false, maxWidth: CGFloat) -> [String] {
        guard !text.isEmpty else { return [""] }
        guard maxWidth > 0 else { return [text] }
        var lines: [String] = []
        for paragraph in text.components(separatedBy: "\n") {
            var line = ""
            for ch in paragraph {
                let candidate = line + String(ch)
                if line.isEmpty || Draw.width(candidate, size: size, bold: bold) <= maxWidth {
                    line = candidate
                } else {
                    lines.append(line)
                    line = String(ch)
                }
            }
            lines.append(line)
        }
        return lines.isEmpty ? [""] : lines
    }

    private func layout(_ s: State) -> Layout {
        var out = Layout()

        // column.setPadding(dp(18), dp(16), dp(18), dp(20)) —— Java:104
        let colX = dp(18)
        let colW = Self.W - dp(18) - dp(18)
        var y = dp(16)

        // ── 标题栏：‹ 返回 + 求新功能（Java:109-127，横向 LinearLayout + CENTER_VERTICAL）──
        let backSize = dp(16)                                     // back.setTextSize(16f) Java:116
        let backW = Draw.width(backText, size: backSize) + dp(10) // padding(0, 6, 10, 6) Java:118
        let backH = lineHeight(backSize) + dp(6) + dp(6)
        let titleSize = dp(20)                                    // title.setTextSize(20f) Java:124
        let titleH = lineHeight(titleSize)
        let barH = max(backH, titleH)
        let backTop = y + (barH - backH) / 2                       // bar.setGravity(CENTER_VERTICAL) Java:111
        out.back = CGRect(x: colX, y: backTop, width: backW, height: backH)
        out.backBaseline = backTop + dp(6) + baselineOffset(backSize)
        // 标题紧跟返回之后（横向 LinearLayout，无 margin）
        out.titleX = colX + backW
        out.titleBaseline = y + (barH - titleH) / 2 + baselineOffset(titleSize)
        y += barH

        // ── 说明文案（Java:129-137）：13sp + dp(4) 行距，topMargin dp(4) ──
        y += dp(4)
        let hintSize = dp(13)                                     // hint.setTextSize(13f) Java:131
        let hintGap = dp(4)                                       // setLineSpacing(dp(4), 1f) Java:133
        out.hintLines = wrap(hintText, size: hintSize, maxWidth: colW)
        for i in 0..<out.hintLines.count {
            out.hintBaselines.append(y + CGFloat(i) * (lineHeight(hintSize) + hintGap)
                                       + baselineOffset(hintSize))
        }
        let hintRows = CGFloat(out.hintLines.count)
        let hintGaps = CGFloat(max(0, out.hintLines.count - 1))
        y += hintRows * lineHeight(hintSize) + hintGaps * hintGap

        // ── 「上传图片（选填，最多 3 张）」（Java:140，sectionLabel + topMargin dp(16)）──
        let labelSize = dp(14)                                    // label.setTextSize(14f) Java:216
        y += dp(16)
        out.imagesLabelBaseline = y + baselineOffset(labelSize)
        y += lineHeight(labelSize)

        // ── 缩略图一排（Java:142-146 + renderThumbs Java:238-288，topMargin dp(8)）──
        y += dp(8)
        let side = dp(78)                                         // int side = dp(78) Java:241
        let gap = dp(10)                                           // rightMargin = dp(10) Java:267
        let removeSide = dp(22)                                    // removeLp 22×22 Java:257
        var x = colX
        for _ in s.images.prefix(State.maxImages) {
            out.thumbRects.append(CGRect(x: x, y: y, width: side, height: side))
            // 删除角标贴右上角（Java:258 `gravity = TOP | END`）
            out.removeRects.append(CGRect(x: x + side - removeSide, y: y,
                                          width: removeSide, height: removeSide))
            x += side + gap
        }
        if s.images.count < State.maxImages {                      // Java:271
            out.addRect = CGRect(x: x, y: y, width: side, height: side)
        }
        y += side

        // ── 「你的意见 / 建议」（Java:149，topMargin dp(18)）──
        y += dp(18)
        out.contentLabelBaseline = y + baselineOffset(labelSize)
        y += lineHeight(labelSize)

        // ── 意见正文输入框（Java:151-164）：白底 dp(12) 圆角，内边距 14/12/14/12，
        //    minLines 5 / maxLines 9，15sp，topMargin dp(8) ──
        y += dp(8)
        let bodySize = dp(15)                                      // setTextSize(15f) Java:153
        let boxPadH = dp(14)
        let boxPadV = dp(12)
        let innerW = colW - boxPadH * 2
        let rawLines = wrap(s.content.isEmpty ? contentPlaceholder : s.content,
                            size: bodySize, maxWidth: innerW)
        let lineCount = min(max(5, rawLines.count), 9)              // setMinLines(5)/setMaxLines(9) Java:156-157
        out.contentBox = CGRect(x: colX, y: y,
                                width: colW,
                                height: boxPadV * 2 + CGFloat(lineCount) * lineHeight(bodySize))
        out.contentLines = Array(rawLines.prefix(lineCount))
        out.contentFirstBaseline = y + boxPadV + baselineOffset(bodySize)
        y += out.contentBox.height

        // ── 字数「n / 1000」（Java:166-176，12sp、右对齐、topMargin dp(4)）──
        let counterSize = dp(12)
        y += dp(4)
        out.counterBaseline = y + baselineOffset(counterSize)
        y += lineHeight(counterSize)

        // ── 「联系方式（选填）」（Java:179，topMargin dp(14)）──
        y += dp(14)
        out.contactLabelBaseline = y + baselineOffset(labelSize)
        y += lineHeight(labelSize)

        // ── 联系方式输入框（Java:181-188）：单行，其余同上 ──
        y += dp(8)
        out.contactBox = CGRect(x: colX, y: y,
                                width: colW,
                                height: boxPadV * 2 + lineHeight(bodySize))
        out.contactBaseline = y + boxPadV + baselineOffset(bodySize)
        y += out.contactBox.height

        // ── 提交按钮（Java:191-201）：高 dp(48)、圆角 dp(24)、topMargin dp(22) ──
        y += dp(22)
        let buttonSize = dp(16)                                    // submit.setTextSize(16f) Java:193
        out.submitRect = CGRect(x: colX, y: y, width: colW, height: dp(48))
        out.submitBaseline = out.submitRect.midY - lineHeight(buttonSize) / 2 + baselineOffset(buttonSize)
        y += dp(48)

        y += dp(20)                                                // column 底部 padding Java:104
        out.contentHeight = y
        return out
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let l = layout(s)

        // 页面底色 0xFFF5F6FA（Java:95）
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // ── 标题栏（Java:114-127）──
        d.text(backText, x: l.back.minX, baseline: l.backBaseline, size: dp(16), ink)          // Java:115-117
        d.text(titleText, x: l.titleX, baseline: l.titleBaseline, size: dp(20), ink, bold: true) // Java:123-126

        // ── 说明文案（Java:129-137）──
        for (i, line) in l.hintLines.enumerated() where i < l.hintBaselines.count {
            d.text(line, x: dp(18), baseline: l.hintBaselines[i], size: dp(13), sub)
        }

        // ── 「上传图片（选填，最多 3 张）」（Java:140）──
        d.text(imagesLabel, x: dp(18), baseline: l.imagesLabelBaseline, size: dp(14), ink, bold: true)

        // ── 缩略图 + 删除角标 + 「+ 添加图片」（Java:238-288）──
        for (i, rect) in l.thumbRects.enumerated() {
            // view.setBackground(round(0xFFEDEDF3, dp(10))) —— Java:248
            d.round(rect, radius: dp(10), thumbBg)
            if i < s.images.count {
                // ImageView.ScaleType.CENTER_CROP —— Java:246-247
                d.clipped(to: rect, radius: dp(10)) { d.artFill(s.images[i], in: rect) }
            }
            // 删除角标：0xCC000000 + dp(11) 圆角（22dp 方块 → 正圆）+ 白色「×」14sp（Java:251-259）
            if i < l.removeRects.count {
                let badge = l.removeRects[i]
                d.round(badge, radius: dp(11), removeBg)
                d.centerText("×", cx: badge.midX,
                             baseline: badge.midY - lineHeight(dp(14)) / 2 + baselineOffset(dp(14)),
                             size: dp(14), UIColor.white)
            }
        }
        if l.addRect.width > 0 {
            // 白底 + dp(1) 描边 0xFFC7C3F0 + 圆角 dp(10)（Java:277-282）
            d.round(l.addRect, radius: dp(10), UIColor.white)
            d.roundStroke(l.addRect, radius: dp(10), addBorder, width: dp(1))
            d.centerText(addImageText, cx: l.addRect.midX,
                         baseline: l.addRect.midY - lineHeight(dp(12)) / 2 + baselineOffset(dp(12)),
                         size: dp(12), brand)
        }

        // ── 「你的意见 / 建议」（Java:149）──
        d.text(contentLabel, x: dp(18), baseline: l.contentLabelBaseline, size: dp(14), ink, bold: true)

        // ── 意见正文输入框（Java:151-164）──
        d.round(l.contentBox, radius: dp(12), UIColor.white)                     // Java:159
        let bodyEmpty = s.content.isEmpty
        for (i, line) in l.contentLines.enumerated() {
            d.text(line,
                   x: l.contentBox.minX + dp(14),
                   baseline: l.contentFirstBaseline + CGFloat(i) * lineHeight(dp(15)),
                   size: dp(15),
                   bodyEmpty ? sub : ink)                                        // Java:154 正文色 / 152 提示色
        }

        // ── 字数（Java:166-176 + updateCounter Java:230-233，右对齐）──
        d.rightText("\(s.content.count) / \(State.contentMax)",
                    right: l.contentBox.maxX, baseline: l.counterBaseline,
                    size: dp(12), sub)

        // ── 「联系方式（选填）」+ 单行输入框（Java:179-188）──
        d.text(contactLabel, x: dp(18), baseline: l.contactLabelBaseline, size: dp(14), ink, bold: true)
        d.round(l.contactBox, radius: dp(12), UIColor.white)                     // Java:186
        let contactEmpty = s.contact.isEmpty
        d.text(contactEmpty ? contactPlaceholder : s.contact,
               x: l.contactBox.minX + dp(14), baseline: l.contactBaseline,
               size: dp(15), contactEmpty ? sub : ink)

        // ── 提交按钮（Java:191-201 / setBusy Java:395-401）──
        d.round(l.submitRect, radius: dp(24), s.submitEnabled ? brand : brandDisabled)  // Java:196、400
        d.centerText(s.submitTitle, cx: l.submitRect.midX, baseline: l.submitBaseline,
                     size: dp(16), UIColor.white)                                 // Java:192-195、399
    }

    // MARK: - 命中（Java 里所有可点区域；后画的先判）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let l = layout(s)
        let p = CGPoint(x: x, y: y)

        // 登记顺序 = 绘制顺序；最后统一 reversed，所以「缩略图角标」压在「缩略图」之上命中。
        var hits: [(CGRect, ActionToken)] = [
            (l.back, "back"),                       // Java:119 back.setOnClickListener → finish()
            (l.contentBox, "feedback:focus"),       // Java:151 意见正文输入框（iOS 侧交上层弹输入）
            (l.contactBox, "feedback:contact"),     // Java:181 联系方式输入框
            (l.addRect, "feedback:pickImage"),      // Java:283 add.setOnClickListener → pickImage()
            (l.submitRect, "feedback:submit"),      // Java:201 submit.setOnClickListener → send()
        ]
        for (i, rect) in l.removeRects.enumerated() {
            hits.append((rect, "feedback:removeImage:\(i)"))   // Java:260-264 删图（sending 时上层忽略）
        }

        for (rect, token) in hits.reversed() where rect.width > 0 && rect.height > 0 {
            if rect.contains(p) { return token }
        }
        return "noop"
    }
}
