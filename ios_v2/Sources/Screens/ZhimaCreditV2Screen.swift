import UIKit

/// 「芝麻信用」页 —— `features/zhima/ui/ZhimaCreditV2Screen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 三块内容：
///   1. **顶部蓝底渐变**：从 #2d7cf6 平铺到 0.704 处，再淡到 #eef1f7（三色标）
///   2. **信用卡轮播**：个人卡（858 宽）+ 企业卡（收起 310 / 展开 858），可横向偏移
///   3. **瀑布流**：两列（x=41 / 654，列宽 585），每列若干「插槽」，插槽可以放模块也可以留空
///      （留空画虚线圆角框）。列内 y 从 1377 起，逐槽累加「槽高 + 28 间距」。
///
/// **信用分用的是等宽数字**：参考图里每个数字占同样宽度（比例字体的「1」会更窄），
/// 所以这里取每个数字里最宽的那个当步进、逐个居中画，再把整串横向拉伸到 1.053 倍。
final class ZhimaCreditV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// 底部导航上沿、头部高度（Java:39-41）
    static let navTop: CGFloat = 2544
    static let headerH: CGFloat = 300

    // MARK: - 状态

    /// 一个可放入瀑布流的模块（Java:72-86）
    struct Module {
        let key: String
        let label: String
        let resource: String
        let height: CGFloat
    }

    /// 八个可放模块（Java:91-98）
    static let library: [Module] = [
        Module(key: "goal", label: "芝麻分目标", resource: "zhm_f_goal", height: 780),
        Module(key: "judge", label: "芝麻鉴真", resource: "zhm_f_judge", height: 803),
        Module(key: "table", label: "免押额度对照", resource: "zhm_f_table", height: 1075),
        Module(key: "huabei", label: "花呗问答", resource: "zhm_f_huabei", height: 1076),
        Module(key: "quote", label: "信用分问答", resource: "zhm_f_quote", height: 1076),
        Module(key: "qa663", label: "663分资讯", resource: "zhm_f_qa663", height: 1078),
        Module(key: "limit", label: "信用额度", resource: "zhm_f_limit", height: 781),
        Module(key: "wlj", label: "王老吉广告", resource: "zhm_f_wlj", height: 972),
    ]

    /// 一个插槽（Java:114-122）：key 为空表示空槽，只保留高度
    struct Slot {
        var key: String?
        var height: CGFloat
    }

    final class State: ScreenState {
        /// 轮播横向偏移
        var carousel: CGFloat = 0
        /// false = 企业卡收起（310 宽），true = 展开（858 宽）
        var enterpriseOpen = false

        /// 信用名片自己的头像；nil 时同步个人信息头像。
        var cardAvatar: UIImage?
        var pLabel = "芝麻信用良好"
        var pScore = "636"
        var pQuota = "2500元"
        var pDiscount = "1157元"
        var pPrivilege = "1项"

        var eLabel = "企业信用较弱"
        var eScore = "1319"
        var eCompany = "乐畅云（上海）科技有限公司"
        var eGo = "去涨分"
        var eQuota = "测一测额度"
        var ePrivilege = "5项"

        var left: [Slot] = [
            Slot(key: "goal", height: 780), Slot(key: "table", height: 1075),
            Slot(key: "quote", height: 1076),
        ]
        var right: [Slot] = [
            Slot(key: "judge", height: 803), Slot(key: "huabei", height: 1076),
            Slot(key: "qa663", height: 1078),
        ]

        func column(_ col: Int) -> [Slot] { col == 0 ? left : right }

        /// 往某一列末尾加一个模块（Java 的 `state.column(col).add(...)`）
        func append(_ col: Int, key: String, height: CGFloat) {
            let slot = Slot(key: key, height: height)
            if col == 0 { left.append(slot) } else { right.append(slot) }
        }

        /// 把某列某一格填上模块（Java 的 `state.place(col, index, key)`）
        func place(_ col: Int, _ index: Int, key: String) {
            guard let module = ZhimaCreditV2Screen.library.first(where: { $0.key == key })
            else { return }
            var slots = column(col)
            guard index >= 0 && index < slots.count else { return }
            slots[index] = Slot(key: key, height: module.height)
            if col == 0 { left = slots } else { right = slots }
        }

        /// 清空某一格（长按换位/删除用）
        func clear(_ col: Int, _ index: Int) {
            var slots = column(col)
            guard index >= 0 && index < slots.count else { return }
            slots[index] = Slot(key: nil, height: slots[index].height)
            if col == 0 { left = slots } else { right = slots }
        }
    }

    // MARK: - 调色板（Java:65-69）
    private let navBlue = Draw.color(0xff1677ff)
    private let navLabel = Draw.color(0xff3c3f45)
    private let ink = Draw.color(0xff1f2023)
    private let blueScore = Draw.color(0xff1f63f2)
    private let muted = Draw.color(0xff4a4a4e)

    // MARK: - 几何（Java:43-63）
    private let carouselX: CGFloat = 42
    private let carouselY: CGFloat = 327
    private let cardH: CGFloat = 605
    private let cardGap: CGFloat = 27
    private let personalW: CGFloat = 858
    private let entCompactW: CGFloat = 310
    private let entFullW: CGFloat = 858
    private let aiX: CGFloat = 40
    private let aiY: CGFloat = 936
    private let aiH: CGFloat = 402
    private let aiW: CGFloat = 1200
    private let feedTop: CGFloat = 1377
    private let feedBottomPad: CGFloat = 420
    private let colW: CGFloat = 585
    private let rowGap: CGFloat = 28
    private let colX: [CGFloat] = [41, 654]

    private var art: [String: UIImage] = [:]

    init() {
        for res in ["personal", "entCompact", "entFull", "aiGift", "aiExpire", "badge",
                    "navHome", "navRent", "navMe"] {
            if let image = UIImage(named: Self.assetName(res)) { art[res] = image }
        }
        for module in Self.library {
            if let image = UIImage(named: module.resource) { art[module.key] = image }
        }
    }

    /// 九张界面切图的资源名映射（Java:175-183）
    private static func assetName(_ key: String) -> String {
        switch key {
        case "personal": return "zhm_card_personal"
        case "entCompact": return "zhm_card_ent_compact"
        case "entFull": return "zhm_card_ent_full"
        case "aiGift": return "zhm_ai_gift"
        case "aiExpire": return "zhm_ai_expire"
        case "badge": return "zhm_badge"
        case "navHome": return "zhm_nav_home"
        case "navRent": return "zhm_nav_rent"
        case "navMe": return "zhm_nav_me"
        default: return key
        }
    }

    // MARK: - 位置计算（Java:209-225）

    /// 某列第 index 个槽的顶边（页面坐标）
    static func columnTop(_ s: State, _ col: Int, _ index: Int) -> CGFloat {
        var y: CGFloat = 1377
        let slots = s.column(col)
        for i in 0..<min(index, slots.count) {
            y += slots[i].height + 28
        }
        return y
    }

    static func columnBottom(_ s: State, _ col: Int) -> CGFloat {
        columnTop(s, col, s.column(col).count)
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        let bottom = max(Self.columnBottom(s, 0), Self.columnBottom(s, 1))
        return max(0, bottom + feedBottomPad - Self.H)
    }

    private func enterpriseWidth(_ s: State) -> CGFloat {
        s.enterpriseOpen ? entFullW : entCompactW
    }

    // MARK: - 手势：轮播横拖 + 长按开菜单（Java:331-386 / 204-205 / 448）

    /// 轮播可横向偏移的上限（Java:200-205）
    static func maxCarousel(_ s: State, personalW: CGFloat, cardGap: CGFloat,
                            carouselX: CGFloat, width: CGFloat, enterpriseW: CGFloat) -> CGFloat {
        let contentWidth = carouselX + personalW + cardGap + enterpriseW
        return max(0, contentWidth + carouselX - width)
    }

    func maxCarousel(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return Self.maxCarousel(s, personalW: personalW, cardGap: cardGap,
                                carouselX: carouselX, width: Self.W,
                                enterpriseW: enterpriseWidth(s))
    }

    /// 只有手指落在轮播带里才接管（Java:344-346：上下各放宽 30）
    private func inCarouselBand(_ s: State, designY: CGFloat) -> Bool {
        let cy = designY
        guard cy >= carouselY - 30, cy <= carouselY + cardH + 30 else { return false }
        guard designY > Self.headerH, designY < Self.navTop else { return false }
        return true
    }

    func carouselDrag(_ state: ScreenState, dx: CGFloat, startY: CGFloat) -> Bool {
        guard let s = state as? State else { return false }
        // 只有手指按下时确实落在轮播带里才接管；否则竖向滚动会被横向手势吃掉。
        guard inCarouselBand(s, designY: startY) else { return false }
        let limit = maxCarousel(state)
        guard limit > 0 else { return false }
        s.carousel = min(max(0, s.carousel - dx), limit)
        return true
    }

    /// 松手吸附到最近的一页（Java 的 snapCarousel：按卡片步长取整）
    func carouselSnap(_ state: ScreenState, totalDx: CGFloat) {
        guard let s = state as? State else { return }
        let step = personalW + cardGap
        guard step > 0 else { return }
        let limit = maxCarousel(state)
        let index = ((s.carousel / step).rounded())
        s.carousel = min(max(0, index * step), limit)
    }

    /// 长按落在哪个模块上 → 返回 `feed:<列>:<行>` 或 `empty:<列>:<行>`，与 Router 的既有分支对齐
    func longPress(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken? {
        guard let s = state as? State else { return nil }
        // 宿主传进来的 y 已经叠加过滚动量（与 hitTest 的约定一致），这里不能再加一次，
        // 否则滑过再长按会多偏移一个 scroll，点到别的模块。
        // 可见区域换算回内容坐标就是 [scroll + headerH, scroll + navTop)。
        guard y >= s.scroll + Self.headerH, y < s.scroll + Self.navTop else { return nil }
        let cy = y
        let feedTop = Self.columnTop(s, 0, 0)
        guard cy >= feedTop else { return nil }
        let split = colX[0] + colW + (colX[1] - colX[0] - colW) / 2
        let col = x < split ? 0 : 1
        let slots = s.column(col)
        for i in 0..<slots.count {
            let top = Self.columnTop(s, col, i)
            if cy >= top && cy <= top + slots[i].height {
                return (slots[i].key == nil ? "empty:" : "feed:") + "\(col):\(i)"
            }
        }
        return nil
    }

    // MARK: - 绘制（Java:229-241）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        drawBackground(d)

        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: Self.headerH, width: Self.W, height: Self.navTop - Self.headerH))
        d.ctx.translateBy(x: 0, y: -s.scroll)
        drawCarousel(d, s)
        drawAssistant(d)
        drawFeed(d, s)
        d.ctx.restoreGState()

        drawHeader(d)
        drawNav(d)
    }

    /// 三色标渐变（Java:243-250）
    private func drawBackground(_ d: Draw) {
        d.multiGradient(CGRect(x: 0, y: 0, width: Self.W, height: Self.H),
                        colors: [Draw.color(0xff2d7cf6), Draw.color(0xff2d7cf6),
                                 Draw.color(0xffeef1f7)],
                        stops: [0, 0.704, 1], vertical: true)
    }

    /// 顶栏：返回箭头 + 标题 + 待收取（Java:252-261）
    private func drawHeader(_ d: Draw) {
        strokeLine(d, from: CGPoint(x: 88, y: 190), to: CGPoint(x: 62, y: 219),
                   color: .white, width: 6)
        strokeLine(d, from: CGPoint(x: 62, y: 219), to: CGPoint(x: 88, y: 248),
                   color: .white, width: 6)
        d.text("芝麻信用", x: 130, baseline: 247, size: 64, UIColor.white)
        d.art(art["badge"], CGRect(x: 1028, y: 179, width: 1104 - 1028, height: 261 - 179))
        d.text("待收取", x: 1117, baseline: 236, size: 41, UIColor.white)
    }

    /// 底部导航：上两角圆角的白底 + 三个条目（Java:263-282）
    private func drawNav(_ d: Draw) {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: 0, y: Self.navTop + 30))
        path.addQuadCurve(to: CGPoint(x: 30, y: Self.navTop), controlPoint: CGPoint(x: 0, y: Self.navTop))
        path.addLine(to: CGPoint(x: Self.W - 30, y: Self.navTop))
        path.addQuadCurve(to: CGPoint(x: Self.W, y: Self.navTop + 30),
                          controlPoint: CGPoint(x: Self.W, y: Self.navTop))
        path.addLine(to: CGPoint(x: Self.W, y: Self.H))
        path.addLine(to: CGPoint(x: 0, y: Self.H))
        path.close()
        d.ctx.saveGState()
        d.ctx.setFillColor(UIColor.white.cgColor)
        d.ctx.addPath(path.cgPath)
        d.ctx.fillPath()
        d.ctx.restoreGState()

        d.centerText("首页", cx: 214, baseline: 2718, size: 40, navBlue)
        d.centerText("租赁", cx: 640, baseline: 2718, size: 40, navLabel)
        d.centerText("我的", cx: 1067, baseline: 2718, size: 40, navLabel)
        d.art(art["navHome"], CGRect(x: 183, y: 2598, width: 246 - 183, height: 2671 - 2598))
        d.art(art["navRent"], CGRect(x: 600, y: 2572, width: 735 - 600, height: 2684 - 2572))
        d.art(art["navMe"], CGRect(x: 1025, y: 2582, width: 1120 - 1025, height: 2677 - 2582))
    }

    /// 信用卡轮播（Java:284-296）
    private func drawCarousel(_ d: Draw, _ s: State) {
        let x = carouselX - s.carousel
        d.art(art["personal"], CGRect(x: x, y: carouselY, width: personalW, height: cardH))
        // 信用名片头像默认同步「个人信息」头像；有设置头像时盖掉素材里的默认头像。
        if let avatar = s.cardAvatar ?? ProfileStore.shared.avatar {
            let avatarRect = CGRect(x: x + 450, y: carouselY + 96, width: 140, height: 140)
            d.clipped(to: avatarRect, radius: avatarRect.width / 2) {
                d.art(avatar, avatarRect)
            }
        }
        drawPersonalText(d, s, x)

        let ex = x + personalW + cardGap
        if s.enterpriseOpen {
            d.art(art["entFull"], CGRect(x: ex, y: carouselY, width: entFullW, height: cardH))
            drawEnterpriseText(d, s, ex)
        } else {
            d.art(art["entCompact"], CGRect(x: ex, y: carouselY, width: entCompactW, height: cardH))
        }
    }

    /// 个人卡上的文字（Java:298-304）
    private func drawPersonalText(_ d: Draw, _ s: State, _ ox: CGFloat) {
        textFit(d, s.pLabel, x: ox + 38, baseline: carouselY + 64, size: 34, maxWidth: 320,
                color: ink, bold: true, xScale: 1)
        drawScore(d, s.pScore, x: ox + 40, baseline: carouselY + 213, maxWidth: 330)
        textFit(d, s.pQuota, x: ox + 41, baseline: carouselY + 447, size: 58, maxWidth: 235,
                color: ink, bold: true, xScale: 0.88)
        textFit(d, s.pDiscount, x: ox + 331, baseline: carouselY + 447, size: 58, maxWidth: 235,
                color: ink, bold: true, xScale: 0.88)
        textFit(d, s.pPrivilege, x: ox + 617, baseline: carouselY + 447, size: 58, maxWidth: 150,
                color: ink, bold: true, xScale: 0.88)
    }

    /// 企业卡上的文字（Java:306-314）
    private func drawEnterpriseText(_ d: Draw, _ s: State, _ ox: CGFloat) {
        textFit(d, s.eLabel, x: ox + 41, baseline: carouselY + 66, size: 34, maxWidth: 320,
                color: ink, bold: true, xScale: 1)
        drawScore(d, s.eScore, x: ox + 39, baseline: carouselY + 212, maxWidth: 330)
        textFit(d, s.eCompany, x: ox + 344, baseline: carouselY + 139, size: 36, maxWidth: 470,
                color: muted, bold: false, xScale: 1)
        textFit(d, s.eGo, x: ox + 344, baseline: carouselY + 202, size: 40, maxWidth: 240,
                color: ink, bold: false, xScale: 1)
        chevron(d, x: ox + 484, cy: carouselY + 189, color: Draw.color(0xff9aa0a8), halfH: 16, width: 4)
        textFit(d, s.eQuota, x: ox + 43, baseline: carouselY + 452, size: 42, maxWidth: 270,
                color: navBlue, bold: false, xScale: 1)
        textFit(d, s.ePrivilege, x: ox + 474, baseline: carouselY + 452, size: 46, maxWidth: 150,
                color: ink, bold: true, xScale: 1)
    }

    /// 大号信用分：**等宽数字 + 1.053 拉伸**（Java:317-346）
    private func drawScore(_ d: Draw, _ value: String, x: CGFloat, baseline: CGFloat,
                           maxWidth: CGFloat) {
        guard !value.isEmpty else { return }
        let size: CGFloat = 137
        let font = Fonts.number(size)
        // 每个数字取同样步进：用最宽的那个
        var advance: CGFloat = 0
        for ch in value {
            advance = max(advance, String(ch).size(withAttributes: [.font: font]).width)
        }
        let natural = advance * CGFloat(value.count)
        let scale = min(1.053, natural > maxWidth ? maxWidth / natural : 1.053)

        d.ctx.saveGState()
        d.ctx.translateBy(x: x, y: 0)
        d.ctx.scaleBy(x: scale, y: 1)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: blueScore]
        for (i, ch) in value.enumerated() {
            let glyph = String(ch)
            let width = glyph.size(withAttributes: [.font: font]).width
            let offset = CGFloat(i) * advance + (advance - width) / 2
            NSAttributedString(string: glyph, attributes: attrs)
                .draw(at: CGPoint(x: offset, y: baseline - font.ascender))
        }
        d.ctx.restoreGState()
    }

    /// 左对齐、超宽时横向压缩（Java:349-357）
    private func textFit(_ d: Draw, _ value: String, x: CGFloat, baseline: CGFloat, size: CGFloat,
                         maxWidth: CGFloat, color: UIColor, bold: Bool, xScale: CGFloat) {
        guard !value.isEmpty else { return }
        let natural = Draw.width(value, size: size, bold: bold) * xScale
        let squeeze = natural > maxWidth ? xScale * maxWidth / natural : xScale
        d.textSqueezed(value, x: x, baseline: baseline, size: size, color,
                       bold: bold, squeeze: squeeze)
    }

    /// 助手卡（Java:359-361）
    private func drawAssistant(_ d: Draw) {
        d.art(art["aiGift"], CGRect(x: aiX, y: aiY, width: aiW, height: aiH))
    }

    /// 瀑布流两列（Java:363-376）
    private func drawFeed(_ d: Draw, _ s: State) {
        for col in 0..<2 {
            var y = feedTop
            for slot in s.column(col) {
                if let key = slot.key {
                    d.art(art[key], CGRect(x: colX[col], y: y, width: colW, height: slot.height))
                } else {
                    drawEmptySlot(d, top: y, height: slot.height)
                }
                y += slot.height + rowGap
            }
        }
    }

    /// 空槽的虚线圆角框（Java:379-393）：两列同时画
    private func drawEmptySlot(_ d: Draw, top: CGFloat, height: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(Draw.color(0x556d7a8c).cgColor)
        d.ctx.setLineWidth(2.5)
        d.ctx.setLineDash(phase: 0, lengths: [14, 12])
        for x in colX {
            let box = CGRect(x: x + 1, y: top + 1, width: colW - 2, height: height - 2)
            let path = UIBezierPath(roundedRect: box, cornerRadius: 26)
            d.ctx.addPath(path.cgPath)
        }
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    // MARK: - 图元

    private func strokeLine(_ d: Draw, from: CGPoint, to: CGPoint, color: UIColor, width: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: from)
        d.ctx.addLine(to: to)
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor,
                         halfH: CGFloat, width: CGFloat) {
        let dx = halfH * 0.45
        strokeLine(d, from: CGPoint(x: x - dx, y: cy - halfH), to: CGPoint(x: x + dx, y: cy),
                   color: color, width: width)
        strokeLine(d, from: CGPoint(x: x + dx, y: cy), to: CGPoint(x: x - dx, y: cy + halfH),
                   color: color, width: width)
    }

    // MARK: - 命中（Java:398-477）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 底栏 / 顶部返回固定，用屏幕坐标；轮播与瀑布流用内容坐标。
        let rawY = y - s.scroll
        if rawY >= Self.navTop {
            if x < 426 { return "nav:0" }
            if x < 853 { return "nav:1" }
            return "nav:2"
        }
        if rawY < Self.headerH {
            return x < 140 ? "back" : "noop"
        }
        let cy = y
        if cy >= carouselY && cy <= carouselY + cardH {
            let ox = carouselX - s.carousel
            let ex = ox + personalW + cardGap
            if s.enterpriseOpen {
                if x >= ex && x < ex + entFullW {
                    if let field = creditField(x - ex, cy - carouselY, enterprise: true) {
                        return "edit:" + field
                    }
                    return "carousel"
                }
            } else if x >= ex && x < ex + entCompactW
                        && cy - carouselY > 460 && cy - carouselY < 565 {
                return "expandEnt"
            }
            if x >= ox && x < ox + personalW {
                let lx = x - ox
                let ly = cy - carouselY
                if lx >= 450 && lx <= 590 && ly >= 96 && ly <= 236 {
                    return "zhimaAvatar"
                }
                if let field = creditField(lx, ly, enterprise: false) {
                    return "edit:" + field
                }
                return "carousel"
            }
            return "carousel"
        }
        if cy >= aiY && cy <= aiY + aiH && x >= aiX && x <= aiX + aiW { return "ai" }
        let split = colX[0] + colW + (colX[1] - colX[0] - colW) / 2
        let col = x < split ? 0 : 1
        if cy >= feedTop {
            let slots = s.column(col)
            for i in 0..<slots.count {
                let top = Self.columnTop(s, col, i)
                if cy >= top && cy <= top + slots[i].height
                    && x >= colX[col] && x <= colX[col] + colW {
                    return slots[i].key == nil ? "empty:\(col):\(i)" : "feed:\(col):\(i)"
                }
            }
            // 最后一格下面的空白：加一个模块（Java:458 的 `return "add:" + col`）
            return "add:\(col)"
        }
        return "noop"
    }

    /// 卡面上几个可编辑字段的局部坐标（Java:461-477）
    private func creditField(_ lx: CGFloat, _ ly: CGFloat, enterprise: Bool) -> String? {
        func inside(_ l: CGFloat, _ t: CGFloat, _ r: CGFloat, _ b: CGFloat) -> Bool {
            lx >= l && lx <= r && ly >= t && ly <= b
        }
        if enterprise {
            if inside(36, 30, 246, 72) { return "e.label" }
            if inside(46, 105, 320, 220) { return "e.score" }
            if inside(340, 100, 790, 145) { return "e.company" }
            if inside(340, 160, 500, 212) { return "e.go" }
            if inside(36, 408, 250, 458) { return "e.quota" }
            if inside(466, 400, 550, 460) { return "e.privilege" }
            return nil
        }
        if inside(36, 29, 246, 75) { return "p.label" }
        if inside(40, 105, 252, 224) { return "p.score" }
        if inside(38, 392, 216, 458) { return "p.quota" }
        if inside(328, 392, 504, 458) { return "p.discount" }
        if inside(614, 392, 692, 458) { return "p.privilege" }
        return nil
    }
}

// MARK: - 信用名片可编辑
extension ZhimaCreditV2Screen.State: EditableState {
    func editTitle(_ key: String) -> String {
        switch key {
        case "p.label": return "修改信用描述"
        case "p.score": return "修改芝麻分"
        case "p.quota": return "修改可用额度"
        case "p.discount": return "修改可享优惠"
        case "p.privilege": return "修改可用特权"
        case "e.label": return "修改企业信用描述"
        case "e.score": return "修改企业信用分"
        case "e.company": return "修改企业名称"
        case "e.go": return "修改企业认证"
        case "e.quota": return "修改企业额度"
        case "e.privilege": return "修改企业特权"
        default: return "修改信用名片"
        }
    }

    func editValue(_ key: String) -> String {
        switch key {
        case "p.label": return pLabel
        case "p.score": return pScore
        case "p.quota": return pQuota
        case "p.discount": return pDiscount
        case "p.privilege": return pPrivilege
        case "e.label": return eLabel
        case "e.score": return eScore
        case "e.company": return eCompany
        case "e.go": return eGo
        case "e.quota": return eQuota
        case "e.privilege": return ePrivilege
        default: return ""
        }
    }

    func applyEdit(_ key: String, _ text: String) {
        switch key {
        case "p.label": pLabel = text
        case "p.score": pScore = text
        case "p.quota": pQuota = text
        case "p.discount": pDiscount = text
        case "p.privilege": pPrivilege = text
        case "e.label": eLabel = text
        case "e.score": eScore = text
        case "e.company": eCompany = text
        case "e.go": eGo = text
        case "e.quota": eQuota = text
        case "e.privilege": ePrivilege = text
        default: break
        }
    }
}
