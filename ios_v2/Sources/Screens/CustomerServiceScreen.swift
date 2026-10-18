import UIKit
import ImageIO

/// 「我 → 人工客服」页 —— `features/service/CustomerServiceActivity.java`（340 行）的 1:1 移植。
///
/// ## 关于参考画布（本屏唯一需要判断的地方）
///
/// Java 那边**没有** `W/H` + `canvas.scale` 这一套：`CustomerServiceActivity` 是纯原生 View
/// 布局（`LinearLayout` / `ScrollView` / `TextView`），尺寸全是 dp/sp，由系统按屏幕密度换算
/// （Java:326-328 的 `dp()`）。iOS 的 `Screen` 协议要求一个参考画布，所以这里按
/// **1280×2816** 做等比映射 —— 与全项目其它页一致，也与「1080×2400@3x = 360×800dp」
/// 同比例；dp → 参考单位的系数取 `1280 / 360 ≈ 3.5556`（横向；纵向 2816/800 = 3.52，
/// 差 1%，由 `Canvas.sy` 的拉伸铺满吸收，和 Android `match_parent` 的拉伸行为一致）。
///
/// 于是：52dp 顶栏 → 184.9，232dp 二维码 → 824.9，15sp 正文 → 53.3。
///
/// ## 三样内容都来自后台「全局配置 → 客服」（Java:38-52）
///   * `notice`   —— 二维码上方那行字（Java:173-174，留空时用默认文案）
///   * `qrUrl`    —— 中间二维码的 http(s) 地址（Java:207-216，留空/加载中/失败各有一句占位）
///   * `contact`  —— 二维码下方手机号 / 微信号，点一下复制（Java:232-262）
///
/// iOS 没有 `Session.csNotice()` 这一套字段，所以值由**上层写进 `State`**；
/// 拉配置 / 落盘 / 刷新（Java:116-131）属于网络层，本文件不碰。
///
/// ## 动作令牌（上层 Router 负责执行）
///   * `"back"`    —— 顶栏返回箭头（Java:143 `finish()`）
///   * `"cs:copy"` —— 点联系方式卡片（Java:249-262：写剪贴板 + Toast「已复制：…」，
///                    并把提示改成「已复制 ✓」；上层执行后请把 `State.copied` 置 `true`）
///   * `"noop"`    —— 其余区域
///
/// Java 这一页**没有**打开外部链接的分支（不是 QQ/微信 跳转，只是展示二维码 + 复制号码），
/// 所以这里没有 `cs:open` 热区；若日后要「点二维码在浏览器里打开 `qrUrl`」，
/// 那属于新增行为，需要同时加热区与 Router 分支。
final class CustomerServiceScreen: Screen {
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// Java 没有设计基准，按 1280×2816 等比映射（原因见文件头注释）
    let canvasSize = CGSize(width: 1280, height: 2816)

    /// 顶栏固定、只有内容区跟着手指滚（对应 Java:91-108 的「toolbar + 权重 1 的 ScrollView」），
    /// 所以 `draw` 自己做 `translate(0, -scroll)`，宿主不要再滚一次。
    let selfScrolls = true

    // MARK: - 常量（Java:61-62）

    /// Java:61 `QR_SIZE_DP = 232`
    static let qrSizeDP: CGFloat = 232
    /// Java:62 `QR_MAX_PX = 720` —— 下载后按这个最长边降采样
    static let qrMaxPixel = 720
    /// Java:174 后台没配 `cs_notice` 时的默认文案
    static let defaultNotice = "开通会员、不会使用等问题均可询问客服"
    /// Java:234 后台没配 `cs_contact` 时的占位
    static let defaultContact = "客服联系方式待后台配置"
    /// Java:242 可复制的提示
    static let copyHint = "点击复制"
    /// Java:256 复制成功后的提示
    static let copiedHint = "已复制 ✓"

    // MARK: - 调色板（Java:54-59）

    private let pageBg = Draw.color(0xfff2f3f7)          // Java:54 PAGE_BG
    private let card = Draw.color(0xffffffff)            // Java:55 CARD
    private let ink = Draw.color(0xff1b1b20)             // Java:56 INK
    private let gray = Draw.color(0xff8a8f99)            // Java:57 GRAY
    private let brand = Draw.color(0xff4a7bf7)           // Java:58 BRAND
    private let placeholderFill = Draw.color(0xfff4f5f8) // Java:59 PLACEHOLDER
    private let dividerLine = Draw.color(0xffedeef3)     // Java:157 顶栏下面那条细线
    /// `R.drawable.order_ic_back`（drawable-nodpi/order_ic_back.png）抠色：近黑的深蓝 #000719
    private let backInk = Draw.color(0xff000719)

    // MARK: - dp → 参考单位（Java:326-328）

    /// 1280 宽的参考画布 ≈ 360dp 宽的手机（1080px ÷ density 3）
    static let dpUnit: CGFloat = 1280.0 / 360.0

    private func dp(_ value: CGFloat) -> CGFloat { value * Self.dpUnit }

    // MARK: - 几何（Java:94-108、135-166、168-191）

    /// Java:95 `dp(52)` 顶栏高度
    private var toolbarH: CGFloat { dp(52) }
    /// Java:163-164 `Math.max(1, dp(0.6f))` —— 1px（density 3）与 0.6dp 取大
    private var dividerH: CGFloat { max(dp(0.6), dp(1.0 / 3.0)) }
    /// Java:142-144 返回图标的尺寸与内边距
    private var backPadX: CGFloat { dp(9) }
    private var backPadY: CGFloat { dp(14) }
    private var backBoxW: CGFloat { dp(44) }
    /// 返回箭头在 26×24dp 的框里（44-2×9、52-2×14）
    private var backArrowW: CGFloat { backBoxW - backPadX * 2 }
    private var backArrowH: CGFloat { toolbarH - backPadY * 2 }
    /// 箭头线的粗细：原图 38×62 里笔画约 11px，等比进 24dp 高 ≈ 4.3dp（圆头，Java 是位图缩放）
    private var backStroke: CGFloat { backArrowH * 0.178 }

    /// Java:102-103 内容区内边距：左右 24dp、上 28dp、下 32dp
    private var padH: CGFloat { dp(24) }
    private var padTop: CGFloat { dp(28) }
    private var padBottom: CGFloat { dp(32) }
    /// Java:181、187 两处 `space(dp(22))`
    private var gap: CGFloat { dp(22) }

    /// Java:175 正文 15sp
    private var noteSize: CGFloat { dp(15) }
    /// Java:178 `setLineSpacing(dp(4), 1f)`
    private var noteLineGap: CGFloat { dp(4) }

    /// Java:198 二维码卡片圆角 14dp
    private var qrRadius: CGFloat { dp(14) }
    /// Java:209 占位文字 13sp
    private var qrHintSize: CGFloat { dp(13) }

    /// Java:227 联系方式卡片圆角 12dp
    private var contactRadius: CGFloat { dp(12) }
    /// Java:229-230 卡片内边距：左右 18dp、上下 12dp
    private var contactPadH: CGFloat { dp(18) }
    private var contactPadV: CGFloat { dp(12) }
    /// Java:235 联系方式 17sp
    private var contactValueSize: CGFloat { dp(17) }
    /// Java:243 提示 12sp
    private var contactHintSize: CGFloat { dp(12) }
    /// Java:246 `hint.setPadding(0, dp(5), 0, 0)`
    private var contactHintGap: CGFloat { dp(5) }

    // MARK: - 状态

    /// 页面状态：三样后台配置值 + 二维码下载结果 + 是否已复制。
    ///
    /// 全部给了默认值，于是自动继承 `ScreenState.init()`；
    /// 上层拿到配置后直接赋值再 `setNeedsDisplay()` 即可（对应 Java:87-89 与 Java:120-129）。
    final class State: ScreenState {
        /// 二维码上方那行字（Java:67 `notice`）
        var notice: String = ""
        /// 手机号 / 微信号（Java:68 `contact`）
        var contact: String = ""
        /// 二维码图片地址（Java:69 `qrUrl`）
        var qrUrl: String = ""
        /// 下载好的二维码（Java:201-204 的 `ImageView` 内容）
        var qrImage: UIImage?
        /// 下载失败（Java:280-283 把占位改成「二维码加载失败」）
        var qrFailed = false
        /// 已经复制过（Java:256 把提示改成「已复制 ✓」）
        var copied = false

        /// Java:173 `notice.trim()`
        var noticeTrimmed: String { notice.trimmingCharacters(in: .whitespacesAndNewlines) }
        /// Java:232 `contact.trim()`
        var contactTrimmed: String { contact.trimmingCharacters(in: .whitespacesAndNewlines) }
        /// Java:207 `qrUrl.trim()`
        var qrUrlTrimmed: String { qrUrl.trimmingCharacters(in: .whitespacesAndNewlines) }
    }

    // MARK: - 布局（draw 与 hitTest 共用一份，避免两处算出不同矩形）

    private struct Metrics {
        var noteLines: [String] = []
        var noteBaselines: [CGFloat] = []
        var qrRect: CGRect = .zero
        /// 二维码占位文案（有图时为空）
        var placeholderText = ""
        var contactRect: CGRect = .zero
        var valueLines: [String] = []
        var valueBaselines: [CGFloat] = []
        var hintText = ""
        var hintBaseline: CGFloat = 0
        /// 内容总高（含底部 32dp 内边距），滚动上限用
        var contentBottom: CGFloat = 0
    }

    private func metrics(for s: State) -> Metrics {
        var m = Metrics()
        let contentTop = toolbarH + dividerH + padTop
        let maxContentW = Self.W - padH * 2

        // ① 二维码上方那行字（Java:171-179）
        let noteFont = Draw.font(size: noteSize)
        let notice = s.noticeTrimmed.isEmpty ? Self.defaultNotice : s.noticeTrimmed
        m.noteLines = wrap(notice, size: noteSize, maxWidth: maxContentW)
        for i in 0..<m.noteLines.count {
            m.noteBaselines.append(contentTop + CGFloat(i) * (noteFont.lineHeight + noteLineGap)
                                  + noteFont.ascender)
        }
        let noteBlockH = CGFloat(m.noteLines.count) * noteFont.lineHeight
            + CGFloat(max(0, m.noteLines.count - 1)) * noteLineGap

        // ② 中间 232dp 的二维码卡片（Java:181-187）
        let qrSize = dp(Self.qrSizeDP)
        let qrTop = contentTop + noteBlockH + gap
        m.qrRect = CGRect(x: (Self.W - qrSize) / 2, y: qrTop, width: qrSize, height: qrSize)

        if s.qrImage == nil {
            if s.qrUrlTrimmed.isEmpty {
                m.placeholderText = "二维码待后台配置"      // Java:207-208
            } else if s.qrFailed {
                m.placeholderText = "二维码加载失败"        // Java:281
            } else {
                m.placeholderText = "二维码加载中…"         // Java:208
            }
        }

        // ③ 二维码下方那行联系方式（Java:189-190、220-247）
        let hasContact = !s.contactTrimmed.isEmpty
        let valueFont = Draw.font(size: contactValueSize, bold: true)
        let valueText = hasContact ? s.contactTrimmed : Self.defaultContact
        let innerW = maxContentW - contactPadH * 2
        m.valueLines = wrap(valueText, size: contactValueSize, maxWidth: innerW, bold: true)
        let widestLine = m.valueLines.map {
            Draw.width($0, size: contactValueSize, bold: true)
        }.max() ?? 0

        let hint = hasContact ? (s.copied ? Self.copiedHint : Self.copyHint) : ""
        // Java 只在建卡时量过一次宽度（Java:221-247），改成「已复制 ✓」后不会再重新测量，
        // 所以这里始终按「点击复制」算卡宽，避免复制后卡片突然变宽。
        let hintW = hasContact ? Draw.width(Self.copyHint, size: contactHintSize) : 0
        // Java 的卡片是 WRAP_CONTENT（Java:190 没给 LayoutParams），所以宽度跟着文字走
        let cardW = min(maxContentW, max(widestLine, hintW) + contactPadH * 2)
        let hintBlockH = hint.isEmpty ? 0 : contactHintGap + Draw.font(size: contactHintSize).lineHeight
        let cardH = contactPadV + CGFloat(m.valueLines.count) * valueFont.lineHeight
            + hintBlockH + contactPadV
        let cardTop = m.qrRect.maxY + gap
        m.contactRect = CGRect(x: (Self.W - cardW) / 2, y: cardTop, width: cardW, height: cardH)

        for i in 0..<m.valueLines.count {
            m.valueBaselines.append(cardTop + contactPadV + CGFloat(i) * valueFont.lineHeight
                                    + valueFont.ascender)
        }
        if !hint.isEmpty {
            let hintFont = Draw.font(size: contactHintSize)
            m.hintText = hint
            m.hintBaseline = cardTop + contactPadV + CGFloat(m.valueLines.count) * valueFont.lineHeight
                + contactHintGap + hintFont.ascender
        }
        m.contentBottom = m.contactRect.maxY + padBottom
        return m
    }

    // MARK: - 滚动（Java:97-107 的 ScrollView；内容通常一屏放得下）

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = state as? State ?? State()
        return max(0, metrics(for: s).contentBottom - Self.H)
    }

    // MARK: - 绘制（Java:135-166 顶栏、Java:168-191 内容、Java:194-264 两张卡片）

    func draw(_ d: Draw, state: ScreenState) {
        let s = state as? State ?? State()
        let m = metrics(for: s)

        // Java:84、93 窗口 / 根容器底色
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // ── 顶栏（Java:135-166）：白底、返回箭头、居中标题、底部细线
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: toolbarH), card)      // Java:137
        drawBack(d)                                                           // Java:139-145
        let titleSize = dp(17)                                                // Java:149
        d.centerText("人工客服", cx: Self.W / 2,                               // Java:148
                     baseline: centeredBaseline(centerY: toolbarH / 2, size: titleSize),
                     size: titleSize, ink, bold: true)                        // Java:150-151
        d.rect(CGRect(x: 0, y: toolbarH, width: Self.W, height: dividerH), dividerLine)  // Java:156-164

        // ── 内容区：只有这里跟着手指滚（顶栏固定，见 `selfScrolls`）
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        // ① 二维码上方那行字（Java:172-179）：居中、可换行
        for (i, line) in m.noteLines.enumerated() where i < m.noteBaselines.count {
            d.centerText(line, cx: Self.W / 2, baseline: m.noteBaselines[i],
                         size: noteSize, ink)
        }

        // ② 二维码卡片（Java:194-218）：白底圆角卡片
        d.round(m.qrRect, radius: qrRadius, card)                             // Java:196-199
        if let image = s.qrImage {
            // Java:201-204 ImageView + FIT_CENTER（不裁剪，卡片圆角之外不额外处理）
            artFit(d, image, in: m.qrRect)
        } else if !m.placeholderText.isEmpty {
            // Java:206-214 占位 TextView 是 MATCH_PARENT + 纯色底，所以是**方角**铺满卡片
            d.rect(m.qrRect, placeholderFill)                                 // Java:212
            d.centerText(m.placeholderText, cx: m.qrRect.midX,                // Java:207-211
                         baseline: centeredBaseline(centerY: m.qrRect.midY, size: qrHintSize),
                         size: qrHintSize, gray)
        }

        // ③ 联系方式卡片（Java:220-264）：白底圆角、上「号码」下「点击复制」
        d.round(m.contactRect, radius: contactRadius, card)                   // Java:225-228
        let valueColor = s.contactTrimmed.isEmpty ? gray : ink                 // Java:237
        for (i, line) in m.valueLines.enumerated() where i < m.valueBaselines.count {
            d.centerText(line, cx: Self.W / 2, baseline: m.valueBaselines[i],
                         size: contactValueSize, valueColor, bold: true)       // Java:233-239
        }
        if !m.hintText.isEmpty {
            d.centerText(m.hintText, cx: Self.W / 2, baseline: m.hintBaseline,
                         size: contactHintSize, brand)                         // Java:241-247
        }

        d.ctx.restoreGState()
    }

    /// 顶栏返回箭头（Java:139-145 的 `order_ic_back` 位图，FIT_XY 到 26×24dp）。
    ///
    /// 原图 38×62 是一个近黑深蓝、圆头的 `<`；这里直接画折线，避免多依赖一张切图。
    private func drawBack(_ d: Draw) {
        let box = CGRect(x: backPadX, y: backPadY, width: backArrowW, height: backArrowH)
        let inset = backStroke / 2
        d.ctx.setStrokeColor(backInk.cgColor)
        d.ctx.setLineWidth(backStroke)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: box.maxX - inset, y: box.minY + inset))
        d.ctx.addLine(to: CGPoint(x: box.minX + inset, y: box.midY))
        d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.maxY - inset))
        d.ctx.strokePath()
    }

    // MARK: - 命中（Java:143 返回、Java:249-261 卡片复制、其余无监听）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = state as? State ?? State()
        let m = metrics(for: s)
        let p = CGPoint(x: x, y: y)

        // Java:144 返回图标的 LayoutParams 就是 44×52dp（贴在左上角）。
        // 顶栏不跟随滚动，而宿主已经把 scroll 加进 y（见 Screen.swift 的 touchesEnded），
        // 所以这里先减回去，滚到底部时返回键照样点得着。
        let screenY = y - s.scroll
        if CGRect(x: 0, y: 0, width: backBoxW, height: toolbarH)
            .contains(CGPoint(x: x, y: screenY)) {
            return "back"
        }
        // Java:249-261：整张卡片 + 两个子 TextView 都挂着同一个 listener，点到哪儿都算复制；
        // 没配号码时（Java:249 的 `if (has)`）整张卡片不可点。
        if !s.contactTrimmed.isEmpty, m.contactRect.contains(p) {
            return "cs:copy"
        }
        return "noop"
    }

    // MARK: - 小工具

    /// 基线对齐：Android 的 `Gravity.CENTER` 是把**行框**居中，等价于
    /// `中心 + (ascender + descender) / 2`（`Draw.text` 的 y 是基线）。
    private func centeredBaseline(centerY: CGFloat, size: CGFloat) -> CGFloat {
        let font = Draw.font(size: size)
        return centerY + (font.ascender + font.descender) / 2
    }

    /// 按字符折行 —— 对应 Android `TextView` 在 MATCH_PARENT 宽度下的自动换行。
    ///
    /// 中文逐字断行是对的；纯英文长串这边会从中间断开（Android 走单词边界），
    /// 但客服文案基本都是中文 + 号码，实测不影响观感。
    private func wrap(_ text: String, size: CGFloat, maxWidth: CGFloat,
                      bold: Bool = false) -> [String] {
        var lines: [String] = []
        for paragraph in text.components(separatedBy: "\n") {
            var current = ""
            for ch in paragraph {
                let next = current + String(ch)
                if !current.isEmpty, Draw.width(next, size: size, bold: bold) > maxWidth {
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

    /// 等比缩放居中（对应 Java:202 `ImageView.ScaleType.FIT_CENTER`）
    private func artFit(_ d: Draw, _ image: UIImage, in rect: CGRect) {
        guard image.size.width > 0, image.size.height > 0 else { return }
        let scale = min(rect.width / image.size.width, rect.height / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let box = CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2,
                         width: size.width, height: size.height)
        d.art(image, box)
    }
}

// MARK: - 二维码下载（Java:274-322 的 `loadQr` / `download`）

/// 把后台配的 http(s) 二维码地址下下来解码。
///
/// 与 Java 的差别只有「谁来存结果」：Java 直接 `image.setImageBitmap()` + 隐藏占位（Java:285-286），
/// iOS 这边把 `UIImage` 回给上层，由上层写进 `CustomerServiceScreen.State.qrImage` /
/// `qrFailed` 再 `setNeedsDisplay()`。
enum CustomerServiceQrLoader {
    /// Java:295-296 `setConnectTimeout(12000)` / `setReadTimeout(12000)`
    static let timeout: TimeInterval = 12
    /// Java:298 `setRequestProperty("User-Agent", "doutu-demo")`
    static let userAgent = "doutu-demo"

    /// 下载并解码，**回调一定在主线程**。
    static func load(_ urlString: String, completion: @escaping (UIImage?) -> Void) {
        let trimmed = urlString.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https" else {
            DispatchQueue.main.async {
                completion(nil)
            }
            return
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = timeout
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")

        URLSession.shared.dataTask(with: request) { data, _, _ in
            var image: UIImage?
            if let data = data, !data.isEmpty {
                image = decode(data)
            }
            DispatchQueue.main.async {
                completion(image)
            }
        }.resume()
    }

    /// Java:307-315：先读边界，再按 `QR_MAX_PX` 降采样；解不出来就当失败。
    private static func decode(_ data: Data) -> UIImage? {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: CustomerServiceScreen.qrMaxPixel,
        ]
        if let source = CGImageSourceCreateWithData(data as CFData, nil),
           let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) {
            return UIImage(cgImage: thumbnail)
        }
        // 兜底：ImageIO 认不出的格式（极少数 GIF/WebP）交给 UIKit 再试一次
        return UIImage(data: data)
    }
}
