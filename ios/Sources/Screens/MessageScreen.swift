import UIKit

/// 「消息」列表页 —— `features/zhi/ui/MessageScreen.java` 的 1:1 移植（参考画布 1280×2816，单屏不滚动）。
///
/// 结构：顶部采样图 + 两段纯色灰（中间用渐变过渡）+ 一张大圆角白卡（全部会话 + 5 行会话）+ 底部导航。
/// 文字**全部带横向压缩校准**（7 个系数，是 Android 对着参考图逐段量的），不抄就对不齐。
///
/// 黑卡皮肤（后台可切）只影响顶部那一条：换 `hk_message_bg` 切图、字变成浅色，卡片以下仍是页底色。
final class MessageScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let navTop: CGFloat = 2560

    final class State: ScreenState {}

    // MARK: - 调色板（Java:44-55）
    private let pageBg = Draw.color(0xfff2f3f7)
    private let pageLow = Draw.color(0xfff5f5f5)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff2e2e2e)
    private let headInk = Draw.color(0xff2a2e33)
    private let subInk = Draw.color(0xff949494)
    private let timeInk = Draw.color(0xffcdcdcd)
    private let unread = Draw.color(0xffcccccc)
    private let plusInk = Draw.color(0xff2b3038)
    private let badge = Draw.color(0xffc9d1dc)
    private let goldTint = Draw.color(0xffeacfa4)

    // MARK: - 横向压缩系数（Java:63-69，逐段量出来的）
    private let sxTitle: CGFloat = 1.006
    private let sxHead: CGFloat = 0.995
    private let sxName: CGFloat = 0.984
    private let sxSub: CGFloat = 1.016
    private let sxTime: CGFloat = 0.978
    private let sxSection: CGFloat = 0.963
    private let sxCaption: CGFloat = 1.030

    // MARK: - 几何（Java:73-160）
    private let plateH: CGFloat = 600
    private let blackHeaderH: CGFloat = 440
    private let blendTop: CGFloat = 1380
    private let blendBottom: CGFloat = 1500

    private let cardRect = CGRect(x: 35, y: 636, width: 1243 - 35, height: 2106 - 636)
    private let cardRadius: CGFloat = 52

    private let titleX: CGFloat = 35
    private let titleBase: CGFloat = 246
    private let titleSize: CGFloat = 64
    private let badgeX: CGFloat = 243
    private let badgeY: CGFloat = 222
    private let badgeR: CGFloat = 44
    private let pillRect = CGRect(x: 316, y: 160, width: 983 - 316, height: 283 - 160)
    private let contactsRect = CGRect(x: 1010, y: 170, width: 1106 - 1010, height: 266 - 170)
    private let broomRect = CGRect(x: 197, y: 176, width: 289 - 197, height: 268 - 176)
    private let plusX: CGFloat = 1200.5
    private let plusY: CGFloat = 220.5
    private let plusR: CGFloat = 37
    private let plusStroke: CGFloat = 6.5
    private let plusArm: CGFloat = 15.5

    private let avPanRect = CGRect(x: 68, y: 330, width: 245 - 68, height: 507 - 330)
    private let avAllRect = CGRect(x: 316, y: 337, width: 480 - 316, height: 502 - 337)
    private let capBase: CGFloat = 583
    private let capSize: CGFloat = 46
    private let capPanCx: CGFloat = 138
    private let capAllCx: CGFloat = 399

    private let headX: CGFloat = 78
    private let headBase: CGFloat = 729
    private let headSize: CGFloat = 50
    private let headIcons: [CGRect] = [
        CGRect(x: 781, y: 678, width: 845 - 781, height: 743 - 678),
        CGRect(x: 905, y: 677, width: 965 - 905, height: 743 - 677),
        CGRect(x: 1022, y: 677, width: 1088 - 1022, height: 743 - 677),
        CGRect(x: 1143, y: 677, width: 1209 - 1143, height: 743 - 677),
    ]
    private let headIconArt = ["msg_ic_at", "msg_ic_mail", "msg_ic_yen", "msg_ic_gift"]

    private let rowNameSize: CGFloat = 58
    private let rowSubSize: CGFloat = 45
    private let rowTimeSize: CGFloat = 39.5
    private let rowNameX: CGFloat = 284
    private let rowSubX: CGFloat = 285
    private let rowTimeRight: CGFloat = 1207

    /// 5 行会话：icon 左/上/右/下、名字基线、副标题基线、时间基线（Java:129-135）
    private static let rows: [[CGFloat]] = [
        [77, 827, 243, 993, 889, 957, 883],
        [85, 1094, 225, 1226, 1147, 1215, 1141],
        [97, 1408, 223, 1558, 1476, 1544, 1471],
        [77, 1653, 243, 1820, 1725, 1794, 1720],
        [79, 1906, 239, 2063, 1977, 2041, 1969],
    ]
    private static let rowIconArt = ["msg_ic_box", "msg_ic_follow", "msg_ic_cake", "msg_ic_life", "msg_ic_ai"]
    private static let rowName = ["消息盒子", "关注动态", "生日提醒", "生活号", "AI理财助理蚂小财"]
    private static let rowSub = [
        "支付宝就业｜易立德企业向你发来一个投递…",
        "支付宝市民中心：尊界回应在测试中V80…",
        "本周有1个好友过生日，送上祝福吧",
        "【1】外卖补贴待领取",
        "[服务卡片]",
    ]
    private static let rowTime = ["11:58", "07:56", "9月21日", "7月11日", "4月24日"]
    private let sectionBefore = 2
    private let sectionLabel = "2周前的消息"
    private let sectionX: CGFloat = 78
    private let sectionBase: CGFloat = 1341
    private let sectionSize: CGFloat = 52
    private let dotRow = 3
    private let dotX: CGFloat = 1190.5
    private let dotY: CGFloat = 1775
    private let dotR: CGFloat = 18
    private let tagRow = 4
    private let tagRect = CGRect(x: 754, y: 1920, width: 821 - 754, height: 1986 - 1920)
    private let navX: [CGFloat] = [128, 382, 639, 898, 1151]

    /// 18 张切图（Java:171-193）
    private static let artNames = [
        "plate": "msg_bgplate", "search": "msg_search", "broom": "msg_ic_broom",
        "contacts": "msg_ic_contacts", "avPan": "msg_av_pan", "avAll": "msg_av_all",
        "atsign": "msg_ic_at", "mail": "msg_ic_mail", "yen": "msg_ic_yen", "gift": "msg_ic_gift",
        "box": "msg_ic_box", "follow": "msg_ic_follow", "cake": "msg_ic_cake",
        "life": "msg_ic_life", "ai": "msg_ic_ai", "tagAi": "msg_tag_ai",
        "navbar": "msg_navbar", "blackBackground": "hk_message_bg",
    ]
    private var art: [String: UIImage] = [:]

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }   // 单屏不滚动（Java:196-198）

    // MARK: - 绘制（Java:202-208）

    func draw(_ d: Draw, state: ScreenState) {
        drawBackdrop(d)
        drawCardWithRows(d)
        drawHeaderBar(d)
        drawPinnedEntries(d)
        drawNavBar(d)
    }

    /// 顶部采样图 + 两段灰 + 过渡渐变（Java:211-235）
    private func drawBackdrop(_ d: Draw) {
        if LicaiVersion.blackCard(LicaiVersion.current()) {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageLow)
            headerPlate(d, "blackBackground", artHeight: blackHeaderH, bottom: cardRect.minY)
            return
        }
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)
        if let plate = art["plate"] {
            d.art(plate, CGRect(x: 0, y: 0, width: Self.W, height: plateH))
        }
        d.gradient(CGRect(x: 0, y: blendTop, width: Self.W, height: blendBottom - blendTop),
                   from: pageBg, to: pageLow, vertical: true)
        d.rect(CGRect(x: 0, y: blendBottom, width: Self.W, height: Self.H - blendBottom), pageLow)
    }

    /// 把顶部切图的最后一行延伸到卡片上沿（Java:243-259），避免中间露出页底色
    private func headerPlate(_ d: Draw, _ name: String, artHeight: CGFloat, bottom: CGFloat) {
        guard let bitmap = art[name] else { return }
        let height = min(bottom, artHeight)
        d.art(bitmap, CGRect(x: 0, y: 0, width: Self.W, height: height + 2))
        if height + 1 >= bottom { return }
        d.art(bitmap, src: nil, CGRect(x: 0, y: height + 1, width: Self.W, height: bottom - (height + 1)))
    }

    /// 顶部那一排（Java:261-279）
    private func drawHeaderBar(_ d: Draw) {
        let blackCard = LicaiVersion.blackCard(LicaiVersion.current())
        let headerColor = blackCard ? Draw.color(0xfff4f4f4) : headInk
        d.textSqueezed("消息", x: titleX, baseline: titleBase, size: titleSize, headerColor,
                       bold: true, squeeze: sxTitle)
        d.circle(cx: badgeX, cy: badgeY, r: badgeR, badge)
        d.art(art["broom"], broomRect)
        d.art(art["search"], pillRect)
        d.art(art["contacts"], contactsRect)

        // 「+」是圆环 + 十字，直接画（Java:270-278）
        let plusColor = blackCard ? goldTint : plusInk
        d.ctx.setStrokeColor(plusColor.cgColor)
        d.ctx.setLineWidth(plusStroke)
        d.ctx.strokeEllipse(in: CGRect(x: plusX - plusR, y: plusY - plusR,
                                       width: plusR * 2, height: plusR * 2))
        d.ctx.move(to: CGPoint(x: plusX - plusArm, y: plusY))
        d.ctx.addLine(to: CGPoint(x: plusX + plusArm, y: plusY))
        d.ctx.move(to: CGPoint(x: plusX, y: plusY - plusArm))
        d.ctx.addLine(to: CGPoint(x: plusX, y: plusY + plusArm))
        d.ctx.strokePath()
    }

    /// 卡片上沿那两个置顶入口（Java:281-287）
    private func drawPinnedEntries(_ d: Draw) {
        let color = LicaiVersion.blackCard(LicaiVersion.current()) ? Draw.color(0xfff4f4f4) : ink
        d.art(art["avPan"], avPanRect)
        d.art(art["avAll"], avAllRect)
        d.centerTextSqueezed("理财盘…", cx: capPanCx, baseline: capBase, size: capSize,
                             color, bold: true, squeeze: sxCaption)
        d.centerTextSqueezed("全部", cx: capAllCx, baseline: capBase + 1, size: capSize,
                             color, bold: true, squeeze: sxCaption)
    }

    /// 白卡 + 全部会话 + 5 行（Java:289-315）
    private func drawCardWithRows(_ d: Draw) {
        d.round(cardRect, radius: cardRadius, cardColor)
        d.textSqueezed("全部会话", x: headX, baseline: headBase, size: headSize, ink,
                       bold: true, squeeze: sxHead)
        for (i, box) in headIcons.enumerated() where i < headIconArt.count {
            d.art(art[Self.artKey(for: headIconArt[i])], box)
        }

        for (i, row) in Self.rows.enumerated() {
            if i == sectionBefore {
                d.textSqueezed(sectionLabel, x: sectionX, baseline: sectionBase, size: sectionSize,
                               subInk, squeeze: sxSection)
            }
            d.art(art[Self.artKey(for: Self.rowIconArt[i])],
                  CGRect(x: row[0], y: row[1], width: row[2] - row[0], height: row[3] - row[1]))
            d.textSqueezed(Self.rowName[i], x: rowNameX, baseline: row[4], size: rowNameSize,
                           ink, bold: true, squeeze: sxName)
            d.textSqueezed(Self.rowSub[i], x: rowSubX, baseline: row[5], size: rowSubSize,
                           subInk, squeeze: sxSub)
            d.rightTextSqueezed(Self.rowTime[i], right: rowTimeRight, baseline: row[6],
                                size: rowTimeSize, timeInk, squeeze: sxTime)
            if i == dotRow {
                d.circle(cx: dotX, cy: dotY, r: dotR, unread)
            }
            if i == tagRow {
                d.art(art["tagAi"], tagRect)
            }
        }
    }

    /// 底栏就是参考图那条带子（Java:317-324）
    private func drawNavBar(_ d: Draw) {
        d.art(art["navbar"], CGRect(x: 0, y: Self.navTop, width: Self.W, height: Self.H - Self.navTop))
    }

    /// 资源名 → 字典键（两边命名不同，这里统一映射）
    private static func artKey(for resource: String) -> String {
        artNames.first { $0.value == resource }?.key ?? resource
    }

    // MARK: - 命中（Java:329-385）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y >= Self.navTop { return "nav:\(nearest(navX, x))" }

        if y < 300 {
            if x >= 190 && x < 300 { return "toast:已清除消息提醒" }
            return "noop"
        }
        if y >= avPanRect.minY - 10 && y <= avPanRect.maxY + 10 { return "noop" }
        if y >= 520 && y < 620 { return "noop" }
        if y < cardRect.minY { return "noop" }

        if y >= cardRect.minY && y < cardRect.maxY {
            if y < 760 { return "noop" }
            if y >= 1290 && y < 1370 { return "noop" }
            let row = rowAt(y)
            if row < 0 { return "noop" }
            // 消息盒子是支付消息页的常规入口（Java:362-363）
            if row == 0 { return "box" }
            return "noop"
        }
        return "noop"
    }

    /// 命中行：行的上下沿各放宽 18px；分段标题那行再往上多 60px（Java:377-385）
    private func rowAt(_ y: CGFloat) -> Int {
        for (i, row) in Self.rows.enumerated() {
            var top = row[1] - 18
            let bottom = row[3] + 18
            if i == sectionBefore { top -= 60 }
            if y >= top && y <= bottom { return i }
        }
        return -1
    }

    private func nearest(_ values: [CGFloat], _ x: CGFloat) -> Int {
        var best = 0
        var bestD = CGFloat.greatestFiniteMagnitude
        for (i, v) in values.enumerated() where abs(v - x) < bestD {
            bestD = abs(v - x)
            best = i
        }
        return best
    }
}
