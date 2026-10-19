import UIKit

/// 我的（小支主页的「我的」页）—— `features/zhi/ui/MeScreen.java` 的 1:1 移植。
///
/// 移植要点（对照 Java 源码逐段抄，注释里标了 Java 行号以便核对）：
///   - 参考画布 W=1280 H=2816，内容高 CONTENT_H=4067，底部导航从 NAV_TOP=2560 开始
///   - 页面底色 PAGE_BG，卡片、banner、头像等是位图（来自 Android 的 res/drawable）
///   - 顶部四个按钮（返回/耳机/齿轮/箭头）、头像、名字、手机号胶囊都是这里画的
///   - 底部导航用 5 格：消息 / 通讯录 / 发现 / 我（其中两格替换成本页的选中态）
final class MeScreen: Screen {
    var zhiTabIndex: Int? { 4 }
    // MARK: - 参考画布（Java MeScreen.java:30-34）
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let NAV_TOP: CGFloat = 2560
    static let CONTENT_H: CGFloat = 4067

    /// 页面状态（Java 的 MeScreen.State）
    final class State: ScreenState {
        /// 「完成身份」横幅可用它自己的 × 关掉
        var banner = true
    }

    // MARK: - 调色板（Java:44-48）
    // 2026-10-19: Android 的「我的」正文底色是白色（顶部渐变只覆盖头图区）。
    // 之前用 #f2f3f7，白卡半透明边缘叠在灰底上就形成一圈浅紫/灰描边。
    private let pageBg = UIColor.white
    private let barBg = Draw.color(0xfffafbff)
    private let phonePill = Draw.color(0xffe3ecf6)
    private let phoneInk = Draw.color(0xff2e4162)
    private let nameInk = Draw.color(0xff27292d)

    /// 文字横向拉伸校准（Java:51-52，按参考图逐段量出来的）
    private let sxName: CGFloat = 1.020
    private let sxPhone: CGFloat = 1.000

    // MARK: - 几何（Java:55-103）
    private let plateH: CGFloat = 700
    private let blackHeaderH: CGFloat = 620

    private let backOld = CGRect(x: 782, y: 182, width: 996 - 782, height: 258 - 182)
    private let headset = CGRect(x: 1032, y: 178, width: 1114 - 1032, height: 262 - 178)
    private let gear = CGRect(x: 1148, y: 172, width: 1234 - 1148, height: 266 - 172)
    private let headChevron = CGRect(x: 1166, y: 367, width: 1202 - 1166, height: 422 - 367)

    private let avatar = CGRect(x: 85, y: 280, width: 313 - 85, height: 508 - 280)
    private let avatarRadius: CGFloat = 44

    private let nameX: CGFloat = 363
    private let nameBase: CGFloat = 366
    private let nameSize: CGFloat = 63
    private let phonePillRect = CGRect(x: 362, y: 412, width: 584 - 362, height: 482 - 412)
    private let phoneCx: CGFloat = 473
    private let phoneBase: CGFloat = 459
    private let phoneSize: CGFloat = 36
    private let phonePillRadius: CGFloat = 35

    private let bannerRect = CGRect(x: 56, y: 554, width: 1224 - 56, height: 664 - 554)
    private let bannerDone = CGRect(x: 912, y: 576, width: 1100 - 912, height: 644 - 576)
    private let bannerClose = CGRect(x: 1118, y: 570, width: 1196 - 1118, height: 650 - 570)

    private let cardLeft: CGFloat = 28
    private let cardRight: CGFloat = 1250
    /// 8 张服务卡的上下边界（内容坐标，Java:84-86）
    private let cards: [(CGFloat, CGFloat)] = [
        (698, 841), (878, 1303), (1341, 1484), (1521, 2123),
        (2161, 2665), (2702, 3089), (3126, 3513), (3550, 3937),
    ]

    /// 资产卡的 4 行（Java:91-93）
    private let assetTop: CGFloat = 1540
    private let assetRowH: CGFloat = 153.5

    /// 图标网格的四列中心（Java:96-97）
    private let gridCx: [CGFloat] = [180, 486, 792, 1098]
    private let gridColW: CGFloat = 306

    // MARK: - 位图

    /// 与 Android res/drawable 的映射：这里用资源名取图，`Assets.swift` 里登记。
    private static let artNames = [
        "plate": "me_bgplate", "backOld": "me_backold", "headset": "me_headset",
        "gear": "me_gear", "headChevron": "me_chevron", "avatar": "me_avatar",
        "banner": "me_banner",
        "card1": "me_card1", "card2": "me_card2", "card3": "me_card3", "card4": "me_card4",
        "card5": "me_card5", "card6": "me_card6", "card7": "me_card7", "card8": "me_card8",
        "navMsg": "me_nav_msg", "navMe": "me_nav_me", "navbar": "msg_navbar",
        "blackBackground": "hk_me_bg",
    ]
    private var art: [String: UIImage] = [:]

    /// 资料（对应 Android 的 ProfileStore：名字、手机号、头像）
    private let profile: ProfileStore

    init(profile: ProfileStore = .shared) {
        self.profile = profile
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    // MARK: - 滚动（Java:137-139）

    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, Self.CONTENT_H - Self.NAV_TOP)
    }

    // MARK: - 绘制（Java:143-168）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let blackCard = LicaiVersion.blackCard(LicaiVersion.current())

        // Java:147 —— 先铺整页底灰
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        // Java:150-158 顶部大图（黑卡皮肤 or 普通底板）
        if blackCard {
            headerPlate(d, "blackBackground", artHeight: blackHeaderH, bottom: plateH)
        } else if let plate = art["plate"] {
            d.art(plate, CGRect(x: 0, y: 0, width: Self.W, height: plateH))
        }

        drawHeader(d, blackCard: blackCard)

        if s.banner {
            d.art(art["banner"], bannerRect)
        }
        // Java:163-165 八张服务卡。
        // 素材边缘自带 1~2px 边框；iOS 3x 屏上会变成很明显的细线。裁掉最外圈再画。
        for (i, card) in cards.enumerated() {
            let key = "card\(i + 1)"
            let rect = CGRect(x: cardLeft, y: card.0,
                              width: cardRight - cardLeft, height: card.1 - card.0)
            // 卡片素材外圈是半透明白边，直接叠在灰底上会形成一条浅紫/灰色描边。
            // 先用同尺寸白圆角垫底，让半透明边缘融进白色，再画卡片。
            d.round(rect, radius: 10, .white)
            if let image = art[key], let cg = image.cgImage, cg.width > 4, cg.height > 4 {
                let crop = CGRect(x: 6, y: 6, width: cg.width - 12, height: cg.height - 12)
                d.art(image, src: crop, rect)
            } else {
                d.art(art[key], rect)
            }
            // 最后用白色细边压住图片边缘插值产生的 1px 灰线。
            d.roundStroke(rect, radius: 10, .white, width: 2)
            d.roundStroke(rect.insetBy(dx: -1, dy: -1), radius: 11, .white, width: 2)
        }
        d.ctx.restoreGState()

        drawNav(d, blackCard: blackCard)
    }

    /// Java:175-190
    private func headerPlate(_ d: Draw, _ name: String, artHeight: CGFloat, bottom: CGFloat) {
        guard let bitmap = art[name] else { return }
        // 一次画满到 bottom，避免上下两段拼接在 3x 屏上出现一条明显的接缝线。
        d.art(bitmap, CGRect(x: 0, y: 0, width: Self.W, height: bottom))
    }

    /// Java:192-218
    private func drawHeader(_ d: Draw, blackCard: Bool) {
        let tint: UIColor? = blackCard ? Draw.color(0xffeacfa4) : nil
        d.art(art["backOld"], backOld)
        drawTinted(d, "headset", headset, tint)
        drawTinted(d, "gear", gear, tint)
        drawTinted(d, "headChevron", headChevron, tint)

        // 头像：优先用用户设置的头像，没有就用素材默认图
        let avatarImage = profile.avatar ?? art["avatar"]
        d.clipped(to: avatar, radius: avatarRadius) {
            d.art(avatarImage, avatar)
        }

        d.text(profile.name, x: nameX, baseline: nameBase, size: nameSize,
               blackCard ? Draw.color(0xfff4f4f4) : nameInk, bold: true, sx: sxName)
        d.round(phonePillRect, radius: phonePillRadius, phonePill)
        d.centerText(profile.phone, cx: phoneCx, baseline: phoneBase, size: phoneSize,
                     phoneInk, sx: sxPhone)
    }

    /// 带着色的位图（对应 Java 的 PorterDuffColorFilter SRC_IN）
    private func drawTinted(_ d: Draw, _ name: String, _ r: CGRect, _ tint: UIColor?) {
        guard let image = art[name] else { return }
        guard let tint else { d.art(image, r); return }
        let template = image.withRenderingMode(.alwaysTemplate)
        d.ctx.saveGState()
        d.ctx.setFillColor(tint.cgColor)
        d.clipped(to: r, radius: 0) {
            d.art(template, r)
        }
        d.ctx.restoreGState()
    }

    /// Java:226-237
    private func drawNav(_ d: Draw, blackCard: Bool) {
        d.art(art["navbar"], CGRect(x: 0, y: Self.NAV_TOP, width: Self.W, height: Self.H - Self.NAV_TOP))
        d.rect(CGRect(x: 768, y: Self.NAV_TOP + 1, width: Self.W - 768, height: 173), barBg)
        d.art(art["navMsg"], CGRect(x: 768, y: Self.NAV_TOP, width: 1024 - 768, height: 173))
        d.art(art["navMe"], CGRect(x: 1024, y: Self.NAV_TOP, width: Self.W - 1024, height: 173))
        _ = blackCard
    }

    // MARK: - 命中测试（Java:242-316，分支顺序必须与 Java 完全一致）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()

        // 底部导航：5 格，取最近的列心（Java:243）
        if y >= Self.NAV_TOP {
            let centers: [CGFloat] = [128, 382, 639, 898, 1151]
            var best = 0
            var bestD = CGFloat.greatestFiniteMagnitude
            for (i, c) in centers.enumerated() where abs(c - x) < bestD {
                bestD = abs(c - x)
                best = i
            }
            if best == 3 { return "nav:3" }   // 本页选中项
            return "nav:\(best)"
        }

        let cy = y + s.scroll

        // 顶部身份区（Java:246-255）
        if cy < 540 {
            if cy > 160 && cy < 280 {
                if x >= backOld.minX - 20 && x <= backOld.maxX + 20 { return "noop" }
                if x >= headset.minX - 20 && x <= headset.maxX + 10 { return "noop" }
                if x >= gear.minX - 10 { return "noop" }
            }
            // 整条身份行（头像、名字、手机号）都进个人信息
            if x >= 60 && x <= 1110 { return "profile" }
            return "noop"
        }

        // 完成身份横幅（Java:256-260）
        if cy >= bannerRect.minY && cy <= bannerRect.maxY {
            if x >= bannerDone.minX && x <= bannerDone.maxX { return "noop" }
            if x >= bannerClose.minX && x <= bannerClose.maxX { return "bannerClose" }
            return "noop"
        }

        if cy < cards[0].0 { return "noop" }
        if cy <= cards[0].1 { return "noop" }
        if cy <= cards[1].1 { return "noop" }

        // 账单卡 → 账单页（Java:269）
        if cy <= cards[2].1 { return "bills" }

        // 资产卡的四行（Java:270-279）
        if cy <= cards[3].1 {
            var row = Int((cy - assetTop) / assetRowH)
            row = max(0, min(3, row))
            switch row {
            case 0: return "assets"
            case 1: return "balance"
            case 2: return "yuebao"
            case 3: return "huabei"
            default: return "noop"
            }
        }

        // 后面四张卡的图标网格：Java 里 grid() 目前一律返回 noop（尚未接功能）
        if cy <= cards[4].1 { return "noop" }
        if cy <= cards[5].1 { return cy < 2830 ? "noop" : "noop" }
        if cy <= cards[6].1 { return cy < 3255 ? "noop" : "noop" }
        if cy <= cards[7].1 { return cy < 3680 ? "noop" : "noop" }
        return "noop"
    }
}
