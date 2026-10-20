import UIKit

/// 「短视频一键去水印」解析页 —— `account/ParseActivity.java` 的 1:1 移植。
///
/// ## 这一屏为什么和别的屏不一样
///
/// 其它 17 屏的 Java 都是「`draw(Canvas, State)` + W/H 参考画布」，照着坐标抄即可。
/// 而 `ParseActivity` 是一个**纯 View 的 Activity**（`onCreate` 里 `buildUi()` 用
/// `LinearLayout` / `TextView` / `EditText` 一层层搭，尺寸全部是 `dp(x)`，Java 里**没有**
/// `W`/`H` 常量）。所以这一屏的移植分两步：
///
///   1. **画布**：按任务约定用 1280×2816（App 里其余各屏的参考画布）；
///   2. **单位换算**：Android 那边是 1080px / density 3 = **360dp 宽**，
///      对应画布宽 1280 → `1dp = 1280/360 ≈ 3.5556` 画布单位。
///      所有 `dp(n)` / `setTextSize(n)` 都过同一个比例（density 3、fontScale 1 时 dp 与 sp 同比例），
///      这样 Java 里的每一个 `dp(18)`、`setTextSize(14.5f)` 都能原样保留在源码里，
///      注释里标出 Java 行号，逐行比对即可。
///
/// ## 与原版一致的部分
///
///   - 版式顺序、间距、圆角、颜色字面量、字号、全部文案（含占位提示、免责说明）照抄；
///   - 组件可见性（进度卡 / 结果卡 / 图集缩略图条 / 预览区 / 下载按钮 / 下载进度）都由
///     `State` 上的开关决定，默认值与 Java 的 `View.GONE` 一致；
///   - 进度百分比映射 `percentOf`（Java:64）、`setProgress`（Java:544）、`resetResult`（Java:552）、
///     `showResult`（Java:573）、`togglePreview`（Java:637）、下载三段状态（Java:742/857/875）、
///     轮询总时长兜底（Java:61/497）都按 Java 的方法逐条搬成了纯状态迁移函数。
///
/// ## 没有搬过来的部分（需要宿主/Router 或系统能力，画布里做不到）
///
///   - **网络**：`DoutuApi.parse` / `DoutuApi.task` / 封面与图集图片下载 —— 本屏只保留状态字段
///     （`taskNo` / `outUrl` / `coverUrl` / `imageUrls`）与 `applyPollStatus` / `showResult` 这些
///     纯数据入口，抓取交给上层（`ApiClient` + `DtwTransport`）；
///   - **剪贴板**：`pasteFromClipboard`（Java:415）→ 令牌 `paste`；
///   - **EditText 输入**：Java 用真实输入框，画布里没有 —— 点输入框发 `link` 令牌，
///     由 Router 弹输入框把文本写回 `State.link`；
///   - **下载到相册**（Java:736-886，MediaStore + MediaScanner）与**打开已保存**（Java:942）
///     → 令牌 `download` / `openSaved`；
///   - **视频预览**（`VideoView`，Java:657-677）：预览区只画 Java 里那块黑色圆角底（Java:309-312），
///     真播放由 Router 决定是否在上面叠 `AVPlayer`；
///   - 远端位图（封面 / 图集缩略图）不下载，画的是 Java 里 `GradientDrawable` 的占位底色
///     （`0xFFE8EDF9`，Java:269/610）。
///
/// ## 动作令牌（`hitTest` 的返回值）
///
/// `back`（Java:163）/ `link`（Java:182）/ `paste`（Java:206）/ `clear`（Java:208）/
/// `parse`（Java:225）/ `preview`（Java:263）/ `strip:<0..8>`（Java:616）/
/// `download`（Java:344）/ `openSaved`（Java:343）/ `noop`。
final class ParseScreen: Screen {
    /// 参考画布：Java 里没有 `W`/`H` 常量（纯 View 布局），按 App 其余各屏约定取 1280×2816。
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// 这一屏的滚动由外层 `ScrollView` 做（Java:143-147），所以自己**不**做 translate，
    /// 交给 `ScreenHostView` 统一滚 —— 两边都做就是双倍滚动。
    let selfScrolls = false

    /// 1dp / 1sp → 参考画布单位：Android 1080px ÷ density 3 = 360dp 对应画布宽 1280
    private static let unit: CGFloat = 1280.0 / 360.0

    // MARK: - 可变状态（Java:74-110 的成员变量）

    final class State: ScreenState {
        /// 输入框内容（Java:74 `linkField`）
        var link: String = ""
        /// 输入框占位提示（Java:183 `setHint`）
        var linkHint: String =
            "例如：7.22 复制打开抖音，看看【xx的作品】https://v.douyin.com/xxx/"

        /// 解析按钮是否可用 / 透明度（Java:458-459、462-463 `setEnabled(false)` + `setAlpha(0.6f)`）
        var parseEnabled = true
        var parseAlpha: CGFloat = 1

        /// 进度卡（Java:76-79、229-230，默认 `GONE`）
        var progressVisible = false
        var progressPercent: Int = 0
        var progressTitle: String = ""
        var progressHint: String = ""

        /// 结果卡（Java:80-84、251-252，默认 `GONE`）
        var resultVisible = false
        /// 作品标题（Java:108，初值 `"doutu"`）
        var title: String = "doutu"
        /// 结果行两行字（Java:592、598）
        var resultTitle: String = "解析结果"
        var resultMeta: String = ""
        /// 「点击结果可预览」这行提示（Java:297 初值、563 / 627 / 651 / 656 会改写）
        var previewHint: String = "点击结果可预览"

        /// 图集（Java:109-110、603-625）
        var imageMode = false
        var imageUrls: [String] = []
        var stripVisible = false

        /// 预览区（Java:85、308-318，默认 `GONE`）
        var previewVisible = false

        /// 下载按钮与下载进度（Java:88-90、321-354，默认 `GONE`）
        var downloadVisible = false
        var downloadLabel: String = "下载到本地"
        var downloadProgressVisible = false
        var downloadProgressText: String = ""

        /// 已保存到本地的成品（Java:93-95）：`saved == true` 时按钮语义变成「打开」
        var saved = false
        var savedWhere: String = ""
        var downloading = false

        /// 解析任务（Java:100-110、478-481）
        var taskNo: String = ""
        var polling = false
        /// 连续查询失败次数（Java:103）：单次网络抖动不应该让进度条停住
        var pollFailures = 0
        /// 本轮轮询的起点，用于总时长兜底（Java:105）
        var pollStartedAt: Date?
        var outUrl: String = ""
        var coverUrl: String = ""
    }

    // MARK: - 调色板（Java:55-59 的常量 + 各处字面量）

    private let brand = Draw.color(0xff2f6fed)        // Java:55 BRAND
    private let brand2 = Draw.color(0xff6e8bff)       // Java:56 BRAND_2（Java 里定义但本页未使用）
    private let ink = Draw.color(0xff1b1f27)          // Java:57 INK
    private let sub = Draw.color(0xff8a8f9a)          // Java:58 SUB
    private let fieldBg = Draw.color(0xfff4f6fa)      // Java:59 FIELD_BG
    private let pageTop = Draw.color(0xffe9efff)      // Java:144 页面渐变顶色
    private let pageBottom = Draw.color(0xfffdfdff)   // Java:145 页面渐变底色
    private let hintInk = Draw.color(0xffb9bec8)      // Java:184 占位提示色
    private let clearInk = Draw.color(0xff9aa3b2)     // Java:208 「清空」文字色
    private let parseRight = Draw.color(0xff5b84ff)   // Java:217 解析按钮渐变右端
    private let smallChip = Draw.color(0x112f6fed)    // Java:391 小胶囊底色（7% 品牌蓝）
    private let coverBg = Draw.color(0xffe8edf9)      // Java:269 封面 / 缩略图占位底色
    private let downloadStroke = Draw.color(0xff9fb3d9) // Java:328 下载按钮描边
    private let downloadInk = Draw.color(0xff2b3a55)  // Java:340 下载按钮文字
    private let tipInk = Draw.color(0xff9aa1ac)       // Java:358 免责说明
    private let trackBg = Draw.color(0xffe8edf9)      // Java:236 进度条轨道（Android 由系统 style 提供）

    /// 免责说明（Java:361）
    private static let tipText =
        "仅会员可用 · 请只处理你自己创作或拥有版权的内容，成品请在 15 分钟内下载。"

    /// 轮询总时长兜底（Java:61 `POLL_DEADLINE_MS = 8 * 60 * 1000`）
    static let pollDeadline: TimeInterval = 8 * 60

    // MARK: - 单位换算

    /// dp → 参考画布单位（Java 里满屏的 `dp(x)`）
    private func dp(_ value: CGFloat) -> CGFloat { value * Self.unit }

    /// sp → 参考画布单位（Java 里满屏的 `setTextSize(x)`；density 3 / fontScale 1 时与 dp 同比例）
    private func fs(_ sp: CGFloat) -> CGFloat { sp * Self.unit }

    /// 单行行高：TextView 默认行高约 1.4 倍字号（Android `includeFontPadding=true` 的实际观感）
    private func lineH(_ sp: CGFloat) -> CGFloat { fs(sp) * 1.4 }

    /// 由文字块顶部推出**基线**：上伸部约 0.92 倍字号 + 半个行距余量 ≈ 1.15 倍字号。
    ///
    /// 这里刻意保留「基线」这个模型 —— 与 `Draw.text(_:x:baseline:size:)` 的约定一致，
    /// 也和 Java 里 `drawText(text, x, baselineY, paint)` 的习惯对得上。
    private func base(_ top: CGFloat, _ sp: CGFloat) -> CGFloat { top + fs(sp) * 1.15 }

    /// 在一行高内**垂直居中**时的基线（`Gravity.CENTER` 的近似）
    private func centerBase(_ rect: CGRect, _ sp: CGFloat) -> CGFloat {
        rect.midY + fs(sp) * 0.35
    }

    // MARK: - 布局帧（draw 与 hitTest 共用一套，保证「画在哪就点在哪」）

    private struct Frames {
        var header = CGRect.zero
        var back = CGRect.zero

        var inputCard = CGRect.zero
        var labelBaseline: CGFloat = 0
        var field = CGRect.zero
        var paste = CGRect.zero
        var clear = CGRect.zero
        var parseButton = CGRect.zero

        var progressCard = CGRect.zero
        var progressTitleBaseline: CGFloat = 0
        var progressBar = CGRect.zero
        var progressHintBaseline: CGFloat = 0

        var resultCard = CGRect.zero
        var resultLabelBaseline: CGFloat = 0
        var resultRow = CGRect.zero
        var cover = CGRect.zero
        var resultTitleLines: [String] = []
        var resultMetaLines: [String] = []
        var resultTitleBaseline: CGFloat = 0
        var resultMetaBaseline: CGFloat = 0
        var previewHint = CGRect.zero
        var previewHintBaseline: CGFloat = 0
        var strip = CGRect.zero
        var thumbs: [CGRect] = []
        var preview = CGRect.zero
        var download = CGRect.zero
        var downloadLabelBaseline: CGFloat = 0
        var downloadProgressBaseline: CGFloat = 0

        var tipLines: [String] = []
        var tipBaseline: CGFloat = 0
        var contentHeight: CGFloat = 0
    }

    /// 按当前状态把整页竖着排一遍（对应 Java `LinearLayout` 的测量过程，Java:142-365）。
    private func layout(_ s: State) -> Frames {
        var f = Frames()
        let padX = dp(18)                       // root 左右 padding 18dp（Java:151）
        let cardX = padX
        let contentW = Self.W - padX * 2
        let cardPad = dp(16)                    // 卡片内边距 16dp（Java:374）
        let innerX = cardX + cardPad
        let innerW = contentW - cardPad * 2
        let cardGap = dp(12)                    // 卡片 topMargin 12dp（Java:378）

        // 状态栏：Android 内容区在状态栏下方开始（Java:113-116 没设全屏），
        // 而参考画布从屏幕顶端开始 —— 这里补 24dp，免得标题压在刘海/灵动岛下面。
        var y = dp(24) + dp(16)                 // + root paddingTop 16dp（Java:151）

        // ① 标题栏：高 52dp（Java:155-172）
        f.header = CGRect(x: cardX, y: y, width: contentW, height: dp(52))
        f.back = CGRect(x: cardX, y: y, width: dp(52), height: dp(52))
        y = f.header.maxY

        // ② 输入卡（Java:174-226）
        let inputTop = y + cardGap
        var iy = inputTop + cardPad
        f.labelBaseline = base(iy, 13)                        // 标签 13sp（Java:177-180）
        iy += lineH(13) + dp(10)                              // 输入框 topMargin 10dp（Java:198）
        // minLines 3 + 上下 padding 12dp（Java:188-195）
        let fieldH = lineH(14.5) * 3 + dp(12) * 2
        f.field = CGRect(x: innerX, y: iy, width: innerW, height: fieldH)
        iy = f.field.maxY + dp(10)                            // tools 顶部 padding 10dp（Java:204）
        let chipH = dp(8) * 2 + lineH(13.5)                   // 小胶囊上下 padding 8dp（Java:394）
        let pasteW = dp(18) * 2 + Draw.width("粘贴", size: fs(13.5))   // 左右 padding 18dp
        let clearW = dp(18) * 2 + Draw.width("清空", size: fs(13.5))
        f.paste = CGRect(x: innerX, y: iy, width: pasteW, height: chipH)
        f.clear = CGRect(x: f.paste.maxX + dp(10), y: iy, width: clearW, height: chipH)  // space 10dp（Java:207）
        iy += chipH + dp(16)                                  // 解析按钮 topMargin 16dp（Java:223）
        f.parseButton = CGRect(x: innerX, y: iy, width: innerW,
                               height: dp(16) * 2 + lineH(17))  // 上下 padding 16dp（Java:220）
        iy = f.parseButton.maxY + cardPad
        f.inputCard = CGRect(x: cardX, y: inputTop, width: contentW, height: iy - inputTop)
        y = f.inputCard.maxY

        // ③ 进度卡（Java:228-248，默认 GONE）
        if s.progressVisible {
            let top = y + cardGap
            var py = top + cardPad
            f.progressTitleBaseline = base(py, 14)            // 14sp 粗体（Java:231-235）
            py += lineH(14) + dp(10)                          // 进度条 topMargin 10dp（Java:240）
            f.progressBar = CGRect(x: innerX, y: py, width: innerW, height: dp(8))   // 高 8dp（Java:239）
            py = f.progressBar.maxY + dp(8)                   // 提示 topPadding 8dp（Java:246）
            f.progressHintBaseline = base(py, 12.5)
            py += lineH(12.5) + cardPad
            f.progressCard = CGRect(x: cardX, y: top, width: contentW, height: py - top)
            y = f.progressCard.maxY
        }

        // ④ 结果卡（Java:250-354，默认 GONE）
        if s.resultVisible {
            let top = y + cardGap
            var ry = top + cardPad
            f.resultLabelBaseline = base(ry, 13)              // 「解析结果」13sp（Java:253-257）
            ry += lineH(13) + dp(10)                          // 结果行 topPadding 10dp（Java:261）
            let rowTop = ry
            f.cover = CGRect(x: innerX, y: rowTop, width: dp(96), height: dp(96))    // 封面 96dp（Java:272）
            let textW = innerW - dp(96) - dp(12)              // 文字块 leftMargin 12dp（Java:278）
            // 标题最多两行（Java:284 `setMaxLines(2)`）
            f.resultTitleLines = Array(Self.wrap(s.resultTitle, width: textW,
                                                 size: fs(15), bold: true).prefix(2))
            f.resultMetaLines = Array(Self.wrap(s.resultMeta, width: textW,
                                                size: fs(12.5)).prefix(2))
            let titleH = lineH(15) * CGFloat(f.resultTitleLines.count)
            let metaTop = rowTop + titleH + dp(6)             // 信息行 topPadding 6dp（Java:289）
            let metaH = lineH(12.5) * CGFloat(f.resultMetaLines.count)
            f.resultTitleBaseline = base(rowTop, 15)
            f.resultMetaBaseline = base(metaTop, 12.5)
            f.resultRow = CGRect(x: innerX, y: rowTop, width: innerW,
                                 height: max(dp(96), titleH + dp(6) + metaH))
            ry = f.resultRow.maxY + dp(10)                    // 预览提示 topPadding 10dp（Java:296）
            f.previewHint = CGRect(x: innerX, y: ry, width: innerW, height: lineH(12.5))
            f.previewHintBaseline = base(ry, 12.5)
            ry = f.previewHint.maxY

            // 图集缩略图条（Java:301-305、613-614）：64dp 方块 + 右间距 8dp，最多 9 张（Java:605）
            if s.stripVisible {
                ry += dp(10)
                let thumb = dp(64)
                let gap = dp(8)
                let count = min(9, s.imageUrls.count)
                f.thumbs = (0..<count).map { index in
                    CGRect(x: innerX + CGFloat(index) * (thumb + gap), y: ry,
                           width: thumb, height: thumb)
                }
                f.strip = CGRect(x: innerX, y: ry, width: innerW, height: thumb)
                ry = f.strip.maxY
            }

            // 预览区：topMargin 12dp、高 220dp（Java:313-316）
            if s.previewVisible {
                ry += dp(12)
                f.preview = CGRect(x: innerX, y: ry, width: innerW, height: dp(220))
                ry = f.preview.maxY
            }

            // 下载按钮：topMargin 14dp、上下 padding 14dp（Java:331-334、330）
            if s.downloadVisible {
                ry += dp(14)
                let h = dp(14) * 2 + lineH(16.5)
                f.download = CGRect(x: innerX, y: ry, width: innerW, height: h)
                f.downloadLabelBaseline = centerBase(f.download, 16.5)
                ry = f.download.maxY
            }

            // 下载进度：topPadding 8dp（Java:351-353）
            if s.downloadProgressVisible {
                ry += dp(8)
                f.downloadProgressBaseline = base(ry, 12.5)
                ry += lineH(12.5)
            }

            ry += cardPad
            f.resultCard = CGRect(x: cardX, y: top, width: contentW, height: ry - top)
            y = f.resultCard.maxY
        }

        // ⑤ 免责说明（Java:356-362）：topPadding 16dp、行距 1.3、11.5sp
        let tipTop = y + dp(16)
        f.tipLines = Self.wrap(Self.tipText, width: contentW, size: fs(11.5))
        f.tipBaseline = base(tipTop, 11.5)
        let tipBottom = tipTop + lineH(11.5) * 1.3 * CGFloat(f.tipLines.count)
        f.contentHeight = tipBottom + dp(24)                  // root paddingBottom 24dp（Java:151）
        return f
    }

    // MARK: - Screen

    /// 内容高于画布时才能滚（结果卡 + 预览区展开后才会超出，与 Android 的 ScrollView 一致）
    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return max(0, layout(s).contentHeight - Self.H)
    }

    // MARK: - 绘制（Java:142-365 `buildUi` 的版式）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let f = layout(s)

        // ① 页面底色：TOP_BOTTOM 渐变 0xFFE9EFFF → 0xFFFDFDFF（Java:144-147）
        //    整段内容高度都铺上，滚动到最底部也不会露出白底
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: max(Self.H, f.contentHeight)),
                   from: pageTop, to: pageBottom)

        drawHeader(d, f)
        drawInputCard(d, s, f)
        if s.progressVisible { drawProgress(d, s, f) }
        if s.resultVisible { drawResult(d, s, f) }
        drawTip(d, f)
    }

    /// 标题栏：返回「‹」+ 居中标题（Java:154-172）
    private func drawHeader(_ d: Draw, _ f: Frames) {
        // 返回：36sp、居中在 52×52 的方块里（Java:158-164）
        d.centerText("‹", cx: f.back.midX, baseline: centerBase(f.back, 36),
                     size: fs(36), ink)
        // 标题：「短视频去水印」19sp 粗体、居中（Java:165-172）
        d.centerText("短视频去水印", cx: f.header.midX, baseline: centerBase(f.header, 19),
                     size: fs(19), ink, bold: true)
    }

    /// 输入卡：标签 + 输入框 + 两个小胶囊 + 一键解析（Java:174-226）
    private func drawInputCard(_ d: Draw, _ s: State, _ f: Frames) {
        drawCard(d, f.inputCard)

        // 标签「粘贴分享链接或文案」13sp SUB（Java:176-180）
        d.text("粘贴分享链接或文案", x: f.field.minX, baseline: f.labelBaseline,
               size: fs(13), sub)

        // 输入框：FIELD_BG + 圆角 12dp（Java:191-195）
        d.round(f.field, radius: dp(12), fieldBg)
        let tx = f.field.minX + dp(14)                     // 内边距 14/12（Java:195）
        let textW = f.field.width - dp(28)
        let firstBaseline = f.field.minY + dp(12) + fs(14.5) * 1.15
        let shown = s.link.isEmpty ? s.linkHint : s.link
        // 14.5sp、TOP 对齐、最多可见 3 行（minLines 3，Java:185-190）
        let lines = Array(Self.wrap(shown, width: textW, size: fs(14.5)).prefix(3))
        for (i, line) in lines.enumerated() {
            d.text(line, x: tx, baseline: firstBaseline + lineH(14.5) * CGFloat(i),
                   size: fs(14.5), s.link.isEmpty ? hintInk : ink)
        }

        // 两个小胶囊（Java:202-208、384-397）：底色 0x112F6FED、圆角 18dp
        d.round(f.paste, radius: dp(18), smallChip)
        d.centerText("粘贴", cx: f.paste.midX, baseline: centerBase(f.paste, 13.5),
                     size: fs(13.5), brand)
        d.round(f.clear, radius: dp(18), smallChip)
        d.centerText("清空", cx: f.clear.midX, baseline: centerBase(f.clear, 13.5),
                     size: fs(13.5), clearInk)

        // 一键解析：BRAND → 0xFF5B84FF 横向渐变、圆角 14dp（Java:210-226）
        d.clipped(to: f.parseButton, radius: dp(14)) {
            d.gradient(f.parseButton, from: brand, to: parseRight, vertical: false)
        }
        // 提交中 `setAlpha(0.6f)`（Java:459）：用一层白纱近似整块变淡
        if s.parseAlpha < 1 {
            d.round(f.parseButton, radius: dp(14),
                    UIColor(white: 1, alpha: max(0, min(1, 1 - s.parseAlpha))))
        }
        d.centerText("一键解析", cx: f.parseButton.midX, baseline: centerBase(f.parseButton, 17),
                     size: fs(17), UIColor.white, bold: true)
    }

    /// 进度卡：标题 + 进度条 + 提示（Java:228-248）
    private func drawProgress(_ d: Draw, _ s: State, _ f: Frames) {
        drawCard(d, f.progressCard)

        // 进度标题 14sp 粗体 INK（Java:231-235）
        d.text(s.progressTitle, x: f.progressBar.minX, baseline: f.progressTitleBaseline,
               size: fs(14), ink, bold: true)

        // 进度条：轨道 + 已完成段（Java:236-242，高 8dp、max 100）
        d.round(f.progressBar, radius: f.progressBar.height / 2, trackBg)
        let percent = max(2, min(100, s.progressPercent))   // Java:545 `Math.max(2, Math.min(100, p))`
        let filledW = f.progressBar.width * CGFloat(percent) / 100
        if filledW > 0 {
            let filled = CGRect(x: f.progressBar.minX, y: f.progressBar.minY,
                                width: filledW, height: f.progressBar.height)
            d.round(filled, radius: f.progressBar.height / 2, brand)
        }

        // 提示 12.5sp SUB（Java:243-248）
        d.text(s.progressHint, x: f.progressBar.minX, baseline: f.progressHintBaseline,
               size: fs(12.5), sub)
    }

    /// 结果卡：封面占位 + 两行字 + 预览提示 + 图集缩略图 + 预览区 + 下载按钮（Java:250-354）
    private func drawResult(_ d: Draw, _ s: State, _ f: Frames) {
        drawCard(d, f.resultCard)

        // 「解析结果」13sp SUB（Java:253-257）
        d.text("解析结果", x: f.cover.minX, baseline: f.resultLabelBaseline, size: fs(13), sub)

        // 封面 96dp 圆角 10dp（Java:266-272 / 600-601）：
        // Android 是异步下载的远端位图，iOS 侧不下载，画 `GradientDrawable` 的占位底色
        d.round(f.cover, radius: dp(10), coverBg)

        // 标题（15sp 粗体，最多两行）+ 信息行（12.5sp SUB，Java:280-290）
        let tx = f.cover.maxX + dp(12)
        for (i, line) in f.resultTitleLines.enumerated() {
            d.text(line, x: tx, baseline: f.resultTitleBaseline + lineH(15) * CGFloat(i),
                   size: fs(15), ink, bold: true)
        }
        for (i, line) in f.resultMetaLines.enumerated() {
            d.text(line, x: tx, baseline: f.resultMetaBaseline + lineH(12.5) * CGFloat(i),
                   size: fs(12.5), sub)
        }

        // 「点击结果可预览」/「共 n 张图片…」12.5sp BRAND 居中（Java:292-298、627-629）
        d.centerText(s.previewHint, cx: f.previewHint.midX, baseline: f.previewHintBaseline,
                     size: fs(12.5), brand)

        // 图集缩略图：64dp 方块、圆角 8dp（Java:301-305、607-624）
        for (i, rect) in f.thumbs.enumerated() where i < s.imageUrls.count {
            d.round(rect, radius: dp(8), coverBg)
        }

        // 预览区：黑色圆角 12dp（Java:308-318）；视频/图片本身由 Router 叠原生视图
        if s.previewVisible {
            d.round(f.preview, radius: dp(12), UIColor.black)
        }

        // 下载按钮：白底 + 1.6dp 描边 + 圆角 30dp（Java:321-346）
        if s.downloadVisible {
            d.round(f.download, radius: dp(30), UIColor.white)
            d.roundStroke(f.download, radius: dp(30), downloadStroke, width: dp(1.6))
            d.centerText(s.downloadLabel, cx: f.download.midX, baseline: f.downloadLabelBaseline,
                         size: fs(16.5), downloadInk, bold: true)
        }

        // 下载进度 12.5sp 居中 SUB（Java:348-354）
        if s.downloadProgressVisible {
            d.centerText(s.downloadProgressText, cx: f.previewHint.midX,
                         baseline: f.downloadProgressBaseline, size: fs(12.5), sub)
        }
    }

    /// 免责说明：11.5sp、行距 1.3（Java:356-362）
    private func drawTip(_ d: Draw, _ f: Frames) {
        for (i, line) in f.tipLines.enumerated() {
            d.text(line, x: dp(18),
                   baseline: f.tipBaseline + lineH(11.5) * 1.3 * CGFloat(i),
                   size: fs(11.5), tipInk)
        }
    }

    /// 白卡：圆角 16dp + 一点点投影（Java:370-375，`setElevation(dp(4))`）
    private func drawCard(_ d: Draw, _ rect: CGRect) {
        d.ctx.saveGState()
        d.ctx.setShadow(offset: CGSize(width: 0, height: dp(2)), blur: dp(8),
                        color: Draw.color(0x14203a6b).cgColor)
        d.round(rect, radius: dp(16), UIColor.white)
        d.ctx.restoreGState()
    }

    // MARK: - 命中（顺序 = 绘制顺序的逆序：后画的先判）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        guard let s = state as? State else { return "noop" }
        let f = layout(s)
        let p = CGPoint(x: x, y: y)

        // ① 下载按钮（Java:342-346）：已保存 → 打开；否则下载到本地
        if s.downloadVisible, f.download.contains(p) {
            if s.saved { return "openSaved" }
            return "download"
        }

        // ② 图集缩略图（Java:616-621）：点第几张就预览第几张
        for (index, rect) in f.thumbs.enumerated() where rect.contains(p) {
            return "strip:" + String(index)
        }

        // ③ 结果行（Java:262-263 `resultRow.setOnClickListener(togglePreview)`）
        if s.resultVisible, f.resultRow.contains(p) { return "preview" }

        // ④ 一键解析（Java:210-226；提交中不可点，对应 `setEnabled(false)`）
        if f.parseButton.contains(p) { return s.parseEnabled ? "parse" : "noop" }

        // ⑤ 清空 / 粘贴（Java:202-208）
        if f.clear.contains(p) { return "clear" }
        if f.paste.contains(p) { return "paste" }

        // ⑥ 输入框（Java:182-200，EditText）：iOS 侧由 Router 弹输入框写回 `State.link`
        if f.field.contains(p) { return "link" }

        // ⑦ 返回（Java:158-164 `back.setOnClickListener(v -> finish())`）
        if f.back.contains(p) { return "back" }

        return "noop"
    }

    // MARK: - 解析流程（Java:413-548 的纯状态部分）

    /// 状态 → 进度百分比（Java:64-72）
    static func percentOf(_ status: Int) -> Int {
        switch status {
        case 0: return 15
        case 1: return 45
        case 2: return 80
        case 3: return 100
        default: return 0
        }
    }

    /// 写进度三件套（Java:544-548）
    func setProgress(_ s: State, percent: Int, title: String, hint: String) {
        s.progressPercent = max(2, min(100, percent))
        s.progressTitle = title
        s.progressHint = hint
    }

    /// 点「一键解析」后的首屏状态（Java:450-459）：清结果、开进度卡、按钮变淡
    func beginParse(_ s: State) {
        resetResult(s)
        s.taskNo = ""
        s.outUrl = ""
        s.imageUrls = []
        s.imageMode = false

        s.progressVisible = true
        setProgress(s, percent: 8, title: "提交中…", hint: "正在把链接发给服务端")
        s.parseEnabled = false
        s.parseAlpha = 0.6
        s.polling = true
    }

    /// 提交请求超时/失败：不要一直停在 8% 的“提交中”。
    func failParse(_ s: State, message: String) {
        s.polling = false
        s.parseEnabled = true
        s.parseAlpha = 1
        setProgress(s, percent: 0, title: "解析失败", hint: message)
    }

    /// 提交成功、拿到任务号（Java:478-481）
    func attachTask(_ s: State, taskNo: String, status: Int, now: Date = Date()) {
        s.taskNo = taskNo
        s.polling = true
        s.pollFailures = 0
        s.pollStartedAt = now
        setProgress(s, percent: Self.percentOf(status),
                    title: "已提交，排队中…", hint: "任务号 " + taskNo)
    }

    /// 轮询总时长兜底（Java:497-502）
    func pollExpired(_ s: State, now: Date = Date()) -> Bool {
        guard let started = s.pollStartedAt else { return false }
        return now.timeIntervalSince(started) > Self.pollDeadline
    }

    /// 一次查询的结果落到界面上（Java:519-539，网络部分在外层）
    /// - Returns: 是否还需要继续轮询（Java 里的 `main.postDelayed(poll, 1200)`）
    func applyPollStatus(_ s: State, status: Int, statusText: String) -> Bool {
        s.pollFailures = 0
        // 完成（Java:524-529）：100% +「解析完成」，结果由 `showResult` 填
        if status == 3 {
            s.polling = false
            setProgress(s, percent: 100, title: "解析完成",
                        hint: "成品已生成，可在下方预览与下载")
            return false
        }
        // 超时 / 失败（Java:530-536）：收起进度卡
        if status == 4 || status == 5 {
            s.polling = false
            s.progressVisible = false
            return false
        }
        // 排队 / 解析 / 合成（Java:537-539）
        let hint: String
        if status == 1 {
            hint = "正在解析分享链接…"
        } else if status == 2 {
            hint = "正在下载与合成成品…"
        } else {
            hint = "排队中…"
        }
        let percent = Self.percentOf(status)
        setProgress(s, percent: percent,
                    title: statusText + " · " + String(percent) + "%", hint: hint)
        return true
    }

    /// 查询失败计数（Java:507-518）：连续 5 次才放弃，单次抖动继续问
    /// - Returns: 是否还要再等 2 秒重试（Java:511）
    func registerPollFailure(_ s: State) -> Bool {
        s.pollFailures += 1
        if s.pollFailures < 5 { return true }
        s.polling = false
        s.progressVisible = false
        return false
    }

    // MARK: - 结果与预览（Java:550-724 的纯状态部分）

    /// 清空结果区（Java:552-564）：注意 Java 只移除缩略图**视图**，不清 `imageUrls`
    func resetResult(_ s: State) {
        s.resultVisible = false
        s.previewVisible = false
        s.downloadVisible = false
        s.stripVisible = false
        s.downloadProgressVisible = false
        s.previewHint = "点击结果可预览"
    }

    /// 取字符串字段：JSON 里是 null 时不要显示成 "null"（Java:567-571）
    static func str(_ object: [String: Any]?, _ key: String, _ fallback: String) -> String {
        guard let object, let value = object[key], !(value is NSNull) else { return fallback }
        if let text = value as? String { return text == "null" ? fallback : text }
        if let number = value as? NSNumber { return number.stringValue }
        return fallback
    }

    /// 把服务端返回的结果字段铺到界面上（Java:573-635）
    func showResult(_ s: State, _ d: [String: Any]) {
        s.title = Self.str(d, "title", "doutu")
        let platform = Self.str(d, "platform_name", Self.str(d, "platform", ""))
        let size = Self.str(d, "out_size", "")
        let duration = Self.str(d, "duration", "")
        s.outUrl = Self.str(d, "out_url", "")
        let cover = Self.str(d, "cover", "")
        // Java:580 用的是**上一轮的** imageMode（先清封面再重算模式），这里保持一致
        s.coverUrl = s.imageMode ? "" : cover

        var urls: [String] = []
        if let images = d["images"] as? [Any] {
            for item in images {
                let text = (item as? String) ?? ""
                if !text.isEmpty { urls.append(text) }
            }
        }
        s.imageUrls = urls
        let mediaType = (d["media_type"] as? String) ?? "VIDEO"
        s.imageMode = mediaType == "IMAGE" && !urls.isEmpty

        s.resultVisible = true
        s.resultTitle = s.title.isEmpty ? "解析结果" : s.title

        // 信息行：平台 · 时长 · 体积 · 无水印（Java:593-598）
        var meta = ""
        if !platform.isEmpty { meta += platform }
        if !duration.isEmpty {
            meta += meta.isEmpty ? "" : " · "
            meta += duration + " 秒"
        }
        if !size.isEmpty {
            meta += meta.isEmpty ? "" : " · "
            meta += Self.formatSize(size)
        }
        if ((d["watermark"] as? NSNumber)?.intValue ?? 0) == 0 {
            meta += meta.isEmpty ? "" : " · "
            meta += "无水印（会员）"
        }
        s.resultMeta = meta

        // 图集与预览提示（Java:603-629）
        s.stripVisible = s.imageMode
        s.previewHint = s.imageMode
            ? "共 " + String(s.imageUrls.count) + " 张图片，点击缩略图预览"
            : "点击可预览 · 预览下方可下载"
        // 视频：先把下载按钮露出来，预览等用户点了才播（Java:630-634）
        if !s.imageMode {
            s.downloadVisible = true
            s.previewVisible = false
        }
    }

    /// 点击结果行：视频开/关内联预览，图集直接看大图（Java:637-678）
    func togglePreview(_ s: State) {
        if s.outUrl.isEmpty { return }        // Java 这里会 toast「成品地址还没准备好」
        if s.imageMode {
            s.previewVisible = true
            s.downloadVisible = true
            return
        }
        if s.previewVisible {
            s.previewVisible = false
            s.previewHint = "点击可预览 · 预览下方可下载"
            return
        }
        s.previewVisible = true
        s.downloadVisible = true
        s.previewHint = "预览中…"
    }

    /// 体积格式化（Java:716-724）：≥1MB 用 `%.1f MB`，否则整数 KB
    static func formatSize(_ raw: String) -> String {
        guard let bytes = Int64(raw) else { return raw }
        if bytes >= 1024 * 1024 {
            return String(format: "%.1f MB", Double(bytes) / 1048576.0)
        }
        return String(bytes / 1024) + " KB"
    }

    // MARK: - 下载到本地（Java:726-951 的纯状态部分）

    /// 下载开始（Java:742-748）：按钮变「下载中…」、进度行出现
    func beginDownload(_ s: State) {
        if s.downloading { return }           // Java:742
        s.downloading = true
        s.saved = false
        s.downloadLabel = "下载中…"
        s.downloadProgressVisible = true
        s.downloadProgressText = "正在下载…"
    }

    /// 下载进度文案（Java:810-813）：有总长才显示百分比
    func setDownloadProgress(_ s: State, done: Int64, total: Int64) {
        if total > 0 {
            // 百分比走字符串拼接，不用 `%d` 传 Int64（可变参类型对不上会打出乱数）
            let percent = done * 100 / total
            s.downloadProgressText = "正在下载 " + String(percent) + "%（"
                + String(format: "%.1f / %.1f MB",
                         Double(done) / 1048576.0, Double(total) / 1048576.0) + "）"
        } else {
            s.downloadProgressText = String(format: "正在下载 %.1f MB",
                                            Double(done) / 1048576.0)
        }
    }

    /// 下载成功（Java:857-865）：按钮变「打开」，进度行变成保存路径
    func finishDownload(_ s: State, at location: String) {
        s.downloading = false
        s.saved = true
        s.savedWhere = location
        s.downloadLabel = "打开"
        s.downloadProgressVisible = true
        s.downloadProgressText = "已保存到相册：" + location
    }

    /// 下载失败（Java:875-881）：按钮变「重试下载」
    func failDownload(_ s: State, message: String) {
        s.downloading = false
        s.downloadLabel = "重试下载"
        s.downloadProgressVisible = true
        s.downloadProgressText = "下载失败：" + message
    }

    /// 下载文件名：标题里的路径字符要去掉，否则媒体库接不了（Java:889-893）
    static func safeName(_ title: String) -> String {
        let source = title.isEmpty ? "doutu" : title
        let banned = CharacterSet(charactersIn: "\\/:*?\"<>|\r\n")
        let spaces = CharacterSet.whitespacesAndNewlines
        // Java 的 `replaceAll("[\\\\/:*?\"<>|\\r\\n\\s]", "_")`：路径分隔符与空白一律换成下划线
        let mapped = source.map { character -> Character in
            let bad = character.unicodeScalars.contains { scalar in
                banned.contains(scalar) || spaces.contains(scalar)
            }
            return bad ? "_" : character
        }
        let safe = String(mapped)
        let trimmed = safe.isEmpty ? "doutu" : safe
        return trimmed.count > 40 ? String(trimmed.prefix(40)) : trimmed
    }

    // MARK: - 文字折行

    /// 按字宽手工折行（Android 的 `TextView` 自动换行在画布里没有，等价实现）。
    ///
    /// 逐字符贪心累加：中文没有空格分词，按字符断行与 Android 的观感最接近。
    static func wrap(_ text: String, width: CGFloat, size: CGFloat,
                     bold: Bool = false) -> [String] {
        guard !text.isEmpty, width > 0 else { return [""] }
        var lines: [String] = []
        var current = ""
        for character in text {
            let candidate = current + String(character)
            if !current.isEmpty, Draw.width(candidate, size: size, bold: bold) > width {
                lines.append(current)
                current = String(character)
            } else {
                current = candidate
            }
        }
        if !current.isEmpty { lines.append(current) }
        if lines.isEmpty { lines.append("") }
        return lines
    }
}
