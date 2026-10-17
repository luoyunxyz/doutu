import UIKit

/// 首页外壳 —— `MainActivity.NativeView` 的 1:1 移植（先做「首页」这一栏）。
///
/// Android 的首页是**四张参考图**（微/支/抖/Q 四个平台页）叠一块固定顶部，
/// 再补一条选中态高亮；文字只有横幅那两处，其余都是位图。因此移植非常直接：
/// 三个图层按参考画布坐标贴回去，再把横幅文字与底栏画上。
///
/// 尚未移植：`drawEntertainment`（娱乐页）、`drawProfile`（我页）、`drawDetail`（详情态）。
/// 它们在 `onDraw` 里是同一层分派，下一轮按 `tab` 分支补上即可。
final class HomeShellScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    /// 顶部固定区高度（Java:605）
    static let topHeight: CGFloat = 912
    /// 四个平台页签的高亮位置（Java:601-604）
    static let segmentTop: CGFloat = 736
    static let segmentBottom: CGFloat = 890
    static let segmentLeft: [CGFloat] = [53, 306, 558, 811]
    static let segmentWidth: [CGFloat] = [215, 215, 216, 215]

    /// 底栏：白色底从 2212 到 2388（Java:1571）
    static let navTop: CGFloat = 2212
    /// 三格中心（Java:1573-1575）
    static let navCenters: [CGFloat] = [180, 540, 900]
    static let navBaseline: CGFloat = 2324
    static let navFontSize: CGFloat = 46

    /// 顶部三栏：0 首页 / 1 娱乐 / 2 我（Java:884-886）
    enum Tab: Int { case home = 0, entertainment = 1, profile = 2 }

    final class State: ScreenState {
        var tab: Tab = .home
        /// 平台页签（0 微 / 1 支 / 2 抖 / 3 Q）
        var servicePage = 0
        /// 服务端下发的娱乐页分区（nil 表示还没拉到，用内置兜底）
        var playbookSections: [[String: Any]]?
        /// 娱乐页排版结果（画与点共用同一份）
        var entertainmentRows: [Playbook.RowBox] = []
        var entertainmentBottom: CGFloat = 0
    }

    private let pageBg = Draw.color(0xfff6f6f6)
    private let navBg = Draw.color(0xffffffff)
    private let navLine = Draw.color(0xffeef0f6)
    private let navSelectedPill = Draw.color(0xfff1f2f5)
    private let navSelectedText = Draw.color(0xff222226)
    private let navText = Draw.color(0xff9aa0ac)
    private let redDot = Draw.color(0xffee3b30)
    /// 横幅长条（Java:968-976）
    private let barWhite = Draw.color(0xffffffff)
    private let barStroke = Draw.color(0xff8f86e8)
    private let barInk = Draw.color(0xff4a44b5)

    /// 四张平台参考图 + 固定顶部 + 四个页签高亮（Android:594-596）
    private static let pageArt = ["home_reference", "zhi_reference", "dou_reference", "q_reference"]
    private static let segmentArt = ["seg_wei", "seg_zhi", "seg_dou", "seg_q"]
    private static let topArt = "home_top"

    private var pages: [UIImage?] = []
    private var segments: [UIImage?] = []
    private var topImage: UIImage?

    /// 横幅文字每帧重算（对应 Android 的 bannerTick 心跳重绘）
    private var bannerTick: Timer?

    init() {
        pages = Self.pageArt.map { UIImage(named: $0) }
        segments = Self.segmentArt.map { UIImage(named: $0) }
        topImage = UIImage(named: Self.topArt)
    }

    deinit { bannerTick?.invalidate() }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        // 只有娱乐页能滚；首页与「我」页不滚（Java:786-789）
        guard s.tab == .entertainment else { return 0 }
        return max(0, s.entertainmentBottom + 40 - Playbook.contentBottom)
    }

    /// 让横幅轮播动起来（对应 Android 的 250ms/33ms 心跳）
    func startAnimation(onChange: @escaping () -> Void) {
        bannerTick?.invalidate()
        bannerTick = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { _ in onChange() }
    }

    // MARK: - 绘制（Java:879-888 的分派）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        switch s.tab {
        case .home:
            drawHome(d, state: s)
        case .entertainment:
            drawEntertainment(d, state: s)
        case .profile:
            drawProfile(d, state: s)
        }
    }

    /// 首页（Java:890-920）
    private func drawHome(_ d: Draw, state s: State) {
        // 先铺满当前平台页的参考图，再用固定顶部盖掉它自己的横幅与页签区
        if let page = pages.indices.contains(s.servicePage) ? pages[s.servicePage] : nil {
            d.art(page, CGRect(x: 0, y: 0, width: Self.W, height: Self.H))
        }
        if let top = topImage {
            d.art(top, CGRect(x: 0, y: 0, width: Self.W, height: Self.topHeight))
        }
        let page = max(0, min(3, s.servicePage))
        if let highlight = segments[page] {
            d.art(highlight, CGRect(x: Self.segmentLeft[page], y: Self.segmentTop,
                                    width: Self.segmentWidth[page],
                                    height: Self.segmentBottom - Self.segmentTop))
        }

        drawBanner(d)

        // 参考图底部那行红字不要了，压成和周围一样的底色（Java:917-918）
        d.rect(CGRect(x: 0, y: 2142, width: Self.W, height: 2212 - 2142), pageBg)
        drawNav(d, state: s)
    }

    /// 横幅文字（Java:930-1008）
    func drawBanner(_ d: Draw) {
        let store = BannerStore.shared
        guard store.enabled else { return }
        let title = store.currentTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else { return }

        // 主文案：人物旁边，无底框，靠阴影压住紫底（Java:943-959）
        let left: CGFloat = 120
        let titleMaxRight: CGFloat = 560
        let titleBaseline: CGFloat = 396
        let titleSize: CGFloat = 42
        let lines = BannerStore.wrap(title, maxWidth: titleMaxRight - left, size: titleSize,
                                     bold: true, maxLines: 2)
        var baseline = titleBaseline
        for line in lines {
            d.textWithShadow(line, x: left, baseline: baseline, size: titleSize,
                             UIColor.white, bold: true, radius: 8, offsetY: 3,
                             shadow: UIColor(red: 0, green: 0, blue: 0, alpha: 0.45))
            baseline += 56
        }

        // 下排长条：不透明白底 + 淡紫细边（Java:967-972）
        let barL: CGFloat = 96, barR: CGFloat = 985, barH: CGFloat = 100, barY: CGFloat = 565
        d.round(CGRect(x: barL, y: barY, width: barR - barL, height: barH), radius: barH / 2, barWhite)
        d.roundStroke(CGRect(x: barL, y: barY, width: barR - barL, height: barH),
                      radius: barH / 2, barStroke, width: 2)

        // 字号 = 条高 × 0.42（100 → 42），放不下自动缩到 18（Java:978-990）
        let frame = store.hintFrame()
        guard !frame.current.isEmpty else { return }
        let textPad = max(24, barH * 0.30)
        var fontSize = max(18, barH * 0.42)
        let maxTextW = barR - barL - textPad * 2
        let oneLine = frame.current.split(separator: " ").joined(separator: " ")
        let nextLine = frame.next?.split(separator: " ").joined(separator: " ") ?? ""
        while max(Draw.width(oneLine, size: fontSize), Draw.width(nextLine, size: fontSize)) > maxTextW,
              fontSize > 18 {
            fontSize -= 2
        }
        let centerY = barY + barH / 2
        let textBaseline = centerY + fontSize * 0.35
        let textRect = CGRect(x: barL, y: barY, width: barR - barL, height: barH)

        if frame.rolling, !nextLine.isEmpty {
            // 滚动中：把长条内部裁出来，上一条往上滑出、下一条从下面滑入（Java:994-1003）
            d.clipped(to: textRect, radius: barH / 2) {
                let dy = frame.progress * barH
                d.text(oneLine, x: barL + textPad, baseline: textBaseline - dy,
                       size: fontSize, barInk)
                d.text(nextLine, x: barL + textPad, baseline: textBaseline + barH - dy,
                       size: fontSize, barInk)
            }
        } else {
            d.text(oneLine, x: barL + textPad, baseline: textBaseline, size: fontSize, barInk)
        }
    }

    /// 底部三格（Java:1569-1590）
    func drawNav(_ d: Draw, state: State) {
        d.rect(CGRect(x: 0, y: Self.navTop, width: Self.W, height: Self.H - Self.navTop), navBg)
        d.hairline(Self.navTop, from: 0, to: Self.W, navLine, width: 1.6)

        let labels = ["首页", "娱乐", "我"]
        for (index, center) in Self.navCenters.enumerated() {
            let selected = state.tab.rawValue == index
            if selected {
                d.round(CGRect(x: center - 88, y: 2250, width: 176, height: 2358 - 2250),
                        radius: 54, navSelectedPill)
            }
            d.centerText(labels[index], cx: center, baseline: Self.navBaseline,
                         size: Self.navFontSize, selected ? navSelectedText : navText, bold: selected)
            // 「娱乐」右上角那颗红点：不在这两格时才点着（Java:1586-1589）
            if !selected && labels[index] == "娱乐" {
                d.circle(cx: center + 48, cy: 2274, r: 11, redDot)
            }
        }
    }

    // MARK: - 命中（Java:1592-1640 的导航部分 + 页签部分）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 底部标签栏永远优先响应（Java:1619-1635）
        if y >= Self.navTop {
            if x < 360 { return "tab:0" }
            if x < 720 { return "tab:1" }
            return "tab:2"
        }
        switch s.tab {
        case .home:
            // 分段控件（微/支/抖/Q）：**命中的阈值与绘制不同**，Java 里是固定的
            // 280 / 540 / 795 / 1045（MainActivity:1726），必须照抄，不能用绘制用的 left/width
            if y >= Self.segmentTop - 8 && y <= Self.segmentBottom + 8 {
                let page = x < 280 ? 0 : x < 540 ? 1 : x < 795 ? 2 : (x <= 1045 ? 3 : -1)
                if page >= 0 { return "page:\(page)" }
                return "noop"
            }
            // 宫格：列分界 295 / 540 / 782（微与支共用），行分界见下（Java:1736-1814）
            if s.servicePage == 0 {
                // 微模块 4×4：行区间 (900,1170] (1170,1390] (1390,1610] (1610,1830]
                let col = x < 295 ? 0 : x < 540 ? 1 : x < 782 ? 2 : 3
                let row: Int
                if y > 900 && y <= 1170 { row = 0 }
                else if y > 1170 && y <= 1390 { row = 1 }
                else if y > 1390 && y <= 1610 { row = 2 }
                else if y > 1610 && y <= 1830 { row = 3 }
                else { return "noop" }
                return "weiCell:\(row):\(col)"
            }
            if s.servicePage == 1 {
                // 支模块 4×4：行区间 (940,1180] (1180,1400] (1400,1610] (1610,1830]
                let col = x < 295 ? 0 : x < 540 ? 1 : x < 782 ? 2 : 3
                let row: Int
                if y <= 940 || y > 1830 { return "noop" }
                else if y <= 1180 { row = 0 }
                else if y <= 1400 { row = 1 }
                else if y <= 1610 { row = 2 }
                else { row = 3 }
                return "zhiCell:\(row * 4 + col)"
            }
            return "noop"
        case .entertainment:
            // 落在哪一行就读画出来那份坐标（y 加上滚动量，Java:1912-1917）
            let cy = y + s.scroll
            for box in s.entertainmentRows where cy >= box.top && cy < box.bottom {
                return "play:\(box.row.key)"
            }
            return "noop"
        case .profile:
            // 这一页画在 1280×2560 空间里，热点也要换到那个空间（Java:1657-1660）
            let px = x * Self.profileDesign.width / Self.W
            let py = y * Self.profileDesign.height / Self.H + s.scroll

            // 右上角「···」：账号菜单（Java:1662-1665）
            if py >= 90 && py < 190 && px >= 1090 { return "menu" }

            // 用户行：头像与名字分开命中（Java:1672-1684）
            if py >= 214 && py < 484 && px >= 56 && px <= 1224 {
                let avatarCx: CGFloat = 56 + 104
                let avatarCy: CGFloat = (214 + 484) / 2
                let dx = px - avatarCx
                let dy = py - avatarCy
                if dx * dx + dy * dy <= 104 * 104 { return "avatar" }
                return "user"
            }

            // 深蓝会员卡：整张可点（Java:1686-1689）
            if py >= 508 && py < 838 { return "member" }

            // 「常用服务」四行（Java:1691-1694）
            let entriesTop: CGFloat = 948 + 150
            if py >= entriesTop && py < entriesTop + 4 * 190 {
                let index = Int((py - entriesTop) / 190)
                if index >= 0 && index < 4 { return "entry:\(index)" }
            }
            return "noop"
        }
    }
}
