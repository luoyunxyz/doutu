import UIKit

/// 首页外壳「我」栏 —— `MainActivity.drawProfile` 系列的 1:1 移植。
///
/// 注意坐标系：这一页在 **1280×2560 的设计空间**里画（Android 那行
/// `c.scale(1080f/1280f, 2388f/2560f)`），最后再按同一个比例缩回 1080×2388 画布。
/// 底栏不参与这个缩放 —— 它用的是画布自己的坐标（Java:1264）。
extension HomeV2Screen {
    // MARK: - 设计空间与版式常量（Java:1224-1250）

    /// 这一页的设计空间
    static let profileDesign = CGSize(width: 1280, height: 2560)

    private static let meLeft: CGFloat = 88
    private static let meRight: CGFloat = 1192
    private static let meUserTop: CGFloat = 214
    private static let meUserBottom: CGFloat = 484
    private static let meUserLeft: CGFloat = 56
    private static let meUserRight: CGFloat = 1224
    private static let meMemberTop: CGFloat = 508
    private static let meMemberBottom: CGFloat = 838
    private static let meDotsY: CGFloat = 900
    private static let meEntriesTop: CGFloat = 948
    private static let meEntryH: CGFloat = 190

    private static let vipCard1 = Draw.color(0xff16265c)
    private static let vipCard2 = Draw.color(0xff2a5bd7)
    private static let vipGold = Draw.color(0xffffe3a3)
    private static let vipGold2 = Draw.color(0xfff5c169)
    private static let vipFaint = Draw.color(0xffc9d6ff)

    /// 常用服务四行（Java:1241-1244）
    private static let profileEntries = ["人工客服", "求新功能", "邀请好友", "更多设置"]
    private static let profileEntryIcons = ["service", "feature", "share", "more"]
    private static let profileEntryNotes = ["7×12 小时在线", "提交产品建议", "一起体验更好用的功能", "账号、设备与隐私"]

    // MARK: - 绘制（Java:1251-1265）

    func drawProfile(_ d: Draw, state: State) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), Playbook.pageBg)

        d.ctx.saveGState()
        // 进入 1280×2560 设计空间：先按 Android 的比例缩放
        d.ctx.scaleBy(x: Self.W / Self.profileDesign.width, y: Self.H / Self.profileDesign.height)
        d.ctx.translateBy(x: 0, y: -state.scroll)
        drawProfileTitle(d)
        drawProfileUser(d)
        drawMemberCard(d)
        drawProfileEntries(d)
        d.ctx.restoreGState()

        // 底栏用画布自己的坐标（不参与上面的缩放）
        drawNav(d, state: state)
    }

    /// 标题栏（Java:1268-1273）
    private func drawProfileTitle(_ d: Draw) {
        d.centerText("我的", cx: 640, baseline: 150, size: 52, Playbook.ink, bold: true)
        for i in 0..<3 {
            d.circle(cx: 1136 + CGFloat(i) * 30, cy: 132, r: 7.5, Playbook.ink)
        }
    }

    /// 用户行：整行一张带边框的卡片（Java:1279-1330）
    private func drawProfileUser(_ d: Draw) {
        let logged = Session.shared.isLoggedIn
        let cy = (Self.meUserTop + Self.meUserBottom) / 2
        let rowRect = CGRect(x: Self.meUserLeft, y: Self.meUserTop,
                             width: Self.meUserRight - Self.meUserLeft,
                             height: Self.meUserBottom - Self.meUserTop)

        d.round(rowRect, radius: 44, UIColor.white)
        d.roundStroke(rowRect, radius: 44, Draw.color(0xffe1e7f5), width: 2.4)

        // 头像：Android 的算法是「行左边界 + 104」
        let ax = (Self.meUserLeft + Self.meUserRight) / 2 - (Self.meUserRight - Self.meUserLeft) / 2 + 104
        let radius: CGFloat = 82
        d.circle(cx: ax, cy: cy, r: radius + 8, Draw.color(0x1a2f6fed))
        d.circle(cx: ax, cy: cy, r: radius, Playbook.brand)
        // 渐变叠一层（渐变没生效时底色也是对的，与 Android 的做法一致）
        d.clipped(to: CGRect(x: ax - radius, y: cy - radius, width: radius * 2, height: radius * 2),
                  radius: radius) {
            d.gradient(CGRect(x: ax - radius, y: cy - radius, width: radius * 2, height: radius * 2),
                       from: Playbook.brand, to: Draw.color(0xff8a6bff), vertical: true)
        }

        let avatar = ProfileStore.shared.avatar
        if logged, let avatar {
            // 圆形裁剪 + 等比铺满（对应 Android 的 coverMatrix）
            d.clipped(to: CGRect(x: ax - radius, y: cy - radius, width: radius * 2, height: radius * 2),
                      radius: radius) {
                d.artFill(avatar, in: CGRect(x: ax - radius, y: cy - radius,
                                             width: radius * 2, height: radius * 2))
            }
        } else {
            let glyph = logged ? firstChar(Session.shared.displayName) : "登"
            let size = radius * 0.82
            d.text(glyph, x: ax - Draw.width(glyph, size: size, bold: true) / 2,
                   baseline: cy + radius * 0.29, size: size, UIColor.white, bold: true)
        }

        let name = logged ? Session.shared.displayName : "点击登录 / 注册"
        d.text(name, x: ax + radius + 46, baseline: cy + 20, size: 60, Playbook.ink, bold: true)
    }

    /// 取昵称第一个字（空串兜底「登」，Java:1333-1336）
    private func firstChar(_ text: String) -> String {
        guard let first = text.first else { return "登" }
        return String(first)
    }

    /// 深蓝金冠会员卡（Java:1342-1391）
    private func drawMemberCard(_ d: Draw) {
        let logged = Session.shared.isLoggedIn
        let active = Session.shared.isActivated
        let card = CGRect(x: Self.meLeft, y: Self.meMemberTop,
                          width: Self.meRight - Self.meLeft,
                          height: Self.meMemberBottom - Self.meMemberTop)

        d.round(card, radius: 44, Self.vipCard1)
        d.clipped(to: card, radius: 44) {
            d.gradient(card, from: Self.vipCard1, to: Self.vipCard2, vertical: true)
            // 右上角那道浅色弧光（Java:1356-1364）
            d.circle(cx: 1180, cy: Self.meMemberTop + 20, r: 260, UIColor(white: 1, alpha: 0.10))
            d.circle(cx: 1230, cy: Self.meMemberBottom, r: 200, UIColor(white: 1, alpha: 0.07))
        }

        drawVipCrown(d, cx: 224, cy: (Self.meMemberTop + Self.meMemberBottom) / 2)

        d.text("VIP 会员", x: 366, baseline: Self.meMemberTop + 132, size: 56,
               UIColor.white, bold: true)

        var line: String
        if !logged {
            line = "登录后查看会员状态"
        } else if active {
            let expire = Session.shared.expireAt
            let shown = expire.isEmpty ? "-" : String(expire.prefix(10)).replacingOccurrences(of: "-", with: ".")
            line = "有效期至 " + shown
        } else {
            line = Session.shared.overlayText
        }
        if line.count > 16 { line = String(line.prefix(16)) + "…" }
        d.text(line, x: 368, baseline: Self.meMemberTop + 196, size: 32, Self.vipFaint)

        // 金色胶囊按钮（Java:1380-1386）
        let pill = CGRect(x: 872, y: Self.meMemberTop + 118,
                          width: 1128 - 872, height: 232 - 118)
        d.round(pill, radius: 57, Self.vipGold)
        d.clipped(to: pill, radius: 57) {
            d.gradient(pill, from: Self.vipGold, to: Self.vipGold2, vertical: true)
        }
        d.centerText(active ? "续费升级" : "开通会员", cx: 1000, baseline: Self.meMemberTop + 186,
                     size: 36, Draw.color(0xff4a3410), bold: true)

        // 轮播指示点（Java:1389-1390）
        d.round(CGRect(x: 570, y: Self.meDotsY - 7, width: 646 - 570, height: 14), radius: 7, Playbook.brand)
        d.round(CGRect(x: 662, y: Self.meDotsY - 7, width: 726 - 662, height: 14), radius: 7,
                Draw.color(0xffd7dced))
    }

    /// 金质盾牌 + 王冠（Java:1394-1440）：纯路径绘制，不引图片
    private func drawVipCrown(_ d: Draw, cx: CGFloat, cy: CGFloat) {
        let w: CGFloat = 62, h: CGFloat = 74
        // 盾牌：六边形
        let shield: [(CGFloat, CGFloat)] = [
            (cx, cy - h),
            (cx + w, cy - h * 0.55),
            (cx + w, cy + h * 0.30),
            (cx, cy + h),
            (cx - w, cy + h * 0.30),
            (cx - w, cy - h * 0.55),
        ]
        d.path(points: shield, fill: Self.vipGold)
        // Java:1409-1410 是「再用同一个盾牌 Path 叠一层渐变」。只按包围盒裁会把盾牌
        // 画成金色方块（真机截图就是这么暴露的），所以这里必须按盾牌多边形裁。
        d.clipped(points: shield) {
            d.gradient(CGRect(x: cx - w, y: cy - h, width: w * 2, height: h * 2),
                       from: Self.vipGold, to: Self.vipGold2, vertical: true)
        }

        // 内圈浅色，做出厚度
        let iw = w * 0.82, ih = h * 0.84
        d.path(points: [
            (cx, cy - ih),
            (cx + iw, cy - ih * 0.55),
            (cx + iw, cy + ih * 0.30),
            (cx, cy + ih),
            (cx - iw, cy + ih * 0.30),
            (cx - iw, cy - ih * 0.55),
        ], fill: UIColor(white: 1, alpha: 0.20))

        // 王冠（三个尖 + 底座）
        let cw = w * 0.68
        let baseY = cy + h * 0.42
        let tipTop = cy - h * 0.42
        let crownDark = Draw.color(0xff7a5410)
        d.path(points: [
            (cx - cw, baseY),
            (cx - cw, tipTop + h * 0.16),
            (cx - cw * 0.40, tipTop + h * 0.42),
            (cx, tipTop),
            (cx + cw * 0.40, tipTop + h * 0.42),
            (cx + cw, tipTop + h * 0.16),
            (cx + cw, baseY),
        ], fill: crownDark)
        // 王冠底下的横条
        d.round(CGRect(x: cx - cw, y: baseY + 6, width: cw * 2, height: 10), radius: 5, crownDark)
    }

    /// 常用服务白卡（Java:1443-1461）
    private func drawProfileEntries(_ d: Draw) {
        let count = CGFloat(Self.profileEntries.count)
        let bottom = Self.meEntriesTop + 150 + count * Self.meEntryH
        d.round(CGRect(x: Self.meLeft, y: Self.meEntriesTop,
                       width: Self.meRight - Self.meLeft, height: bottom - Self.meEntriesTop),
                radius: 40, UIColor.white)
        d.text("常用服务", x: 128, baseline: Self.meEntriesTop + 90, size: 46, Playbook.ink, bold: true)

        for (i, label) in Self.profileEntries.enumerated() {
            let rowTop = Self.meEntriesTop + 150 + CGFloat(i) * Self.meEntryH
            let middle = rowTop + Self.meEntryH / 2
            if i > 0 {
                d.hairline(rowTop, from: 254, to: 1076, Playbook.cardHairline, width: 1.6)
            }
            d.round(CGRect(x: 128, y: middle - 50, width: 232 - 128, height: 100), radius: 30,
                    Playbook.sectionTint[0])
            d.ctx.saveGState()
            d.ctx.translateBy(x: 180, y: middle)
            d.ctx.scaleBy(x: 0.82, y: 0.82)
            drawProfileIcon(d, kind: Self.profileEntryIcons[i], cx: 0, cy: 0, color: Playbook.sectionAccent[0])
            d.ctx.restoreGState()

            d.text(label, x: 274, baseline: middle - 6, size: 44, Playbook.ink)
            d.text(Self.profileEntryNotes[i], x: 276, baseline: middle + 46, size: 28, Playbook.subInk)
            drawChevron(d, cx: 1096, cy: middle)
        }
    }

    /// 服务入口图标（Java:1463-1514）：service / feature / share / more（含未用到的 font、watch）
    private func drawProfileIcon(_ d: Draw, kind: String, cx: CGFloat, cy: CGFloat, color: UIColor) {
        let stroke: CGFloat = 7
        switch kind {
        case "service":   // 耳机客服
            d.arc(rect: CGRect(x: cx - 25, y: cy - 28, width: 50, height: 56),
                  startDegrees: 180, sweepDegrees: 180, color: color, width: stroke)
            d.line(cx - 25, cy, cx - 25, cy + 22, color, width: stroke)
            d.line(cx + 25, cy, cx + 25, cy + 22, color, width: stroke)
            d.roundStroke(CGRect(x: cx - 33, y: cy + 10, width: 13, height: 20), radius: 4, color, width: stroke)
            d.roundStroke(CGRect(x: cx + 20, y: cy + 10, width: 13, height: 20), radius: 4, color, width: stroke)
        case "font":
            d.text("Aa", x: cx - 36, baseline: cy + 18, size: 42, color, bold: true)
        case "feature":   // 四个点 + 两条线
            d.circle(cx: cx - 16, cy: cy - 12, r: 7, color)
            d.circle(cx: cx + 13, cy: cy - 12, r: 7, color)
            d.circle(cx: cx - 16, cy: cy + 16, r: 7, color)
            d.circle(cx: cx + 13, cy: cy + 16, r: 7, color)
            d.line(cx + 13, cy + 16, cx + 13, cy - 6, color, width: stroke)
            d.line(cx + 2, cy + 5, cx + 24, cy + 5, color, width: stroke)
        case "share":     // 三个点 + 两条连线
            d.circle(cx: cx - 20, cy: cy + 8, r: 7, color)
            d.circle(cx: cx + 18, cy: cy - 18, r: 7, color)
            d.circle(cx: cx + 18, cy: cy + 29, r: 7, color)
            d.line(cx - 14, cy + 5, cx + 12, cy - 14, color, width: stroke)
            d.line(cx - 14, cy + 12, cx + 12, cy + 25, color, width: stroke)
        case "watch":     // 六边形 + 指针
            d.strokeHexagon(cx: cx, cy: cy, radius: 30, color: color, width: stroke)
            d.line(cx, cy - 13, cx, cy + 1, color, width: stroke)
            d.line(cx, cy + 1, cx + 11, cy + 7, color, width: stroke)
        default:          // more：六边形 + 圆
            d.strokeHexagon(cx: cx, cy: cy, radius: 29, color: color, width: stroke)
            d.ctx.setStrokeColor(color.cgColor)
            d.ctx.setLineWidth(stroke)
            d.ctx.strokeEllipse(in: CGRect(x: cx - 9, y: cy - 9, width: 18, height: 18))
        }
    }
}
