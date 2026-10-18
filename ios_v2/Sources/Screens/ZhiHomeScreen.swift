import UIKit

/// 「支」模块首页 —— `features/zhi/ui/ZhiHomeScreen.java` 的 1:1 移植（参考画布 1280×2816，内容高 2990）。
///
/// 结构：顶部斜向蓝渐变带（648 高，从右上深到左下浅）→ 地址 + 搜索胶囊 + 花呗入口 + 加号
/// → 四格快捷动作（扫一扫 / 收付款 / 出行 / 卡包）→ 宫格卡（两行五列，顶部带淡蓝渐变）
/// → 常用卡（左上角蓝色斜角标）→ 最近消息卡（两行，可点击改）→ 页签条（整张切图 + 一条下划蓝线）
/// → 信用分卡（切图 + 可改的分数与文案）→ 底部导航 → 可选「加号」浮层菜单。
///
/// 黑卡皮肤（`LicaiVersion`）会换掉四个快捷动作与宫格里几张图标 —— 与 Android 同一套分支。
final class ZhiHomeScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let navTop: CGFloat = 2560
    static let contentH: CGFloat = 2990

    final class State: ScreenState {
        // 默认值必须与 Android `ZhiHomeScreen.State` 逐字一致（Java:42-55）。
        // 之前 iOS 用了上海市/3条/支付宝安全中心/736，真机首屏就和 Android 对不上。
        var address = "恩施土…"
        var plusMenu = false
        var unread = true
        var newMessage = "7条新消息"
        /// 两行消息：{标题, 摘要, 时间}
        var messages: [[String]] = [
            ["支付宝会员", "你有33积分待领取，解锁酷洛米瓶中精灵主⋯", "刚刚"],
            ["花呗", "查看你的账单进度", "2小时前"],
        ]
        var creditLabel = "信用良好"
        var score = "638"
        var hideScoreTail = true
    }

    // MARK: - 调色板（Java:58-63）
    private let ink = Draw.color(0xff16181c)
    private let label = Draw.color(0xff26262a)
    private let muted = Draw.color(0xff9aa0a8)
    private let blue = Draw.color(0xff1677ff)
    private let cardColor = Draw.color(0xffffffff)
    private let pageBg = Draw.color(0xfff2f3f7)

    private let gridCols: [CGFloat] = [145, 391, 638, 885, 1131]
    private let commonCols: [CGFloat] = [151, 386, 634, 875, 1125]
    private let colEdge: [CGFloat] = [0, 268, 514, 761, 1008, 1280]
    private let gridRow1 = ["alipay", "job", "mobile", "sport", "movie"]
    private let gridRow1Text = ["支付宝会员", "就业", "手机营业厅", "运动", "淘票票"]
    private let gridRow2 = ["ant", "city", "purse", "yuebao", "more"]
    private let gridRow2Text = ["蚂蚁保", "市民中心", "支付宝荷包", "余额宝", "更多"]
    private let common = ["car", "huabei", "suishen", "med", "fund"]
    private let commonText = ["鄂车管", "花呗", "随申办", "医疗健康", "基金"]

    private let actions = ["扫一扫", "收付款", "出行", "卡包"]
    private let actionX: [CGFloat] = [159, 479, 799, 1119]
    private let actionArt = ["actScan", "actPay", "actTravel", "actCard"]

    private let bandH: CGFloat = 648
    private let bandBlueDark = Draw.color(0xff176cfc)
    private let bandBlueLight = Draw.color(0xff5387f8)
    private let bandHeika = Draw.color(0xff0f152c)

    private let navX: [CGFloat] = [128, 382, 639, 898, 1151]

    private let plusItems = ["扫一扫", "收款", "添加朋友", "历史记录"]
    private let plusArt = ["plusScan", "plusPay", "plusFriend", "plusHistory"]
    private let plusL: CGFloat = 768
    private let plusT: CGFloat = 330
    private let plusR: CGFloat = 1258
    private let plusB: CGFloat = 1071
    private let plusRadius: CGFloat = 37
    private let plusRowH: CGFloat = (1071 - 330) / 4
    private let plusIconCx: CGFloat = 874
    private let plusIconSize: CGFloat = 85
    private let plusTextX: CGFloat = 945
    private let plusTextSize: CGFloat = 54
    private let plusDividerL: CGFloat = 917
    private let plusBeakCx: CGFloat = 1192
    private let plusBeakTip: CGFloat = 308
    private let plusBeakHalf: CGFloat = 21
    private let plusPanel = Draw.color(0xe6000000)
    private let plusHairline = Draw.color(0x24ffffff)

    /// 24 张切图（Java:126-156）
    private var art: [String: UIImage] = [:]
    private var iconRects: [String: CGRect] = [:]

    init() {
        let mapping: [(String, String)] = [
            ("alipay", "zhi_icon_alipay"), ("job", "zhi_icon_job"), ("mobile", "zhi_icon_mobile"),
            ("sport", "zhi_icon_sport"), ("movie", "zhi_icon_movie"), ("ant", "zhi_icon_ant"),
            ("city", "zhi_icon_city"), ("purse", "zhi_icon_purse"), ("yuebao", "zhi_icon_yuebao"),
            ("more", "zhi_icon_more"), ("car", "zhi_icon_car"), ("huabei", "zhi_icon_huabei"),
            ("suishen", "zhi_icon_suishen"), ("med", "zhi_icon_med"), ("fund", "zhi_icon_fund"),
            ("msgA", "zhi_icon_msg_a"), ("msgH", "zhi_icon_msg_h"),
            ("actScan", "zhi_act_scan"), ("actPay", "zhi_act_pay"),
            ("actTravel", "zhi_act_travel"), ("actCard", "zhi_act_card"),
            ("tabbar", "zhi_tabbar"), ("credit", "zhi_credit_card"),
            ("locArrow", "zhi_loc_arrow"), ("navbar", "zhi_navbar"), ("fruit", "zhi_fruit"),
            ("plusScan", "zhi_plus_scan"), ("plusPay", "zhi_plus_pay"),
            ("plusFriend", "zhi_plus_addfriend"), ("plusHistory", "zhi_plus_history"),
            ("hkScan", "hk_home_scan"), ("hkPay", "hk_home_pay"),
            ("hkTravel", "hk_home_travel"), ("hkCard", "hk_home_card"),
            ("hkMobile", "hk_feature_mobile"), ("hkCity", "hk_feature_city"),
            ("hkYuebao", "hk_feature_yuebao"), ("hkMovie", "hk_feature_movie"),
            ("hkMore", "hk_feature_more"),
        ]
        for (key, res) in mapping {
            if let image = UIImage(named: res) { art[key] = image }
        }

        // 每张切图贴回的框（Java:126-156）
        iconRects = [
            "alipay": CGRect(x: 85, y: 604, width: 195 - 85, height: 720 - 604),
            "job": CGRect(x: 337, y: 584, width: 478 - 337, height: 718 - 584),
            "mobile": CGRect(x: 593, y: 612, width: 683 - 593, height: 716 - 612),
            "sport": CGRect(x: 830, y: 615, width: 941 - 830, height: 714 - 615),
            "movie": CGRect(x: 1081, y: 611, width: 1184 - 1081, height: 718 - 611),
            "ant": CGRect(x: 89, y: 846, width: 200 - 89, height: 949 - 846),
            "city": CGRect(x: 339, y: 848, width: 445 - 339, height: 951 - 848),
            "purse": CGRect(x: 590, y: 844, width: 687 - 590, height: 954 - 844),
            "yuebao": CGRect(x: 833, y: 843, width: 937 - 833, height: 954 - 843),
            "more": CGRect(x: 1083, y: 849, width: 1182 - 1083, height: 945 - 849),
            "car": CGRect(x: 99, y: 1208, width: 198 - 99, height: 1303 - 1208),
            "huabei": CGRect(x: 340, y: 1202, width: 446 - 340, height: 1308 - 1202),
            "suishen": CGRect(x: 583, y: 1199, width: 693 - 583, height: 1304 - 1199),
            "med": CGRect(x: 827, y: 1208, width: 938 - 827, height: 1303 - 1208),
            "fund": CGRect(x: 1073, y: 1208, width: 1181 - 1073, height: 1304 - 1208),
            "msgA": CGRect(x: 64, y: 1592, width: 119 - 64, height: 1654 - 1592),
            "msgH": CGRect(x: 64, y: 1753, width: 118 - 64, height: 1807 - 1753),
            "actScan": CGRect(x: 97, y: 316, width: 221 - 97, height: 440 - 316),
            "actPay": CGRect(x: 417, y: 316, width: 541 - 417, height: 440 - 316),
            "actTravel": CGRect(x: 737, y: 316, width: 861 - 737, height: 440 - 316),
            "actCard": CGRect(x: 1057, y: 316, width: 1181 - 1057, height: 440 - 316),
            "tabbar": CGRect(x: 0, y: 1878, width: 1280, height: 2046 - 1878),
            "credit": CGRect(x: 0, y: 2056, width: 1280, height: 2590 - 2056),
            "locArrow": CGRect(x: 262, y: 178, width: 312 - 262, height: 212 - 178),
            "navbar": CGRect(x: 0, y: 2560, width: 1280, height: 2816 - 2560),
            "fruit": CGRect(x: 361, y: 180, width: 443 - 361, height: 262 - 180),
            // 黑卡皮肤复用的框（Java:206-214）
            "hkScan": CGRect(x: 97, y: 316, width: 221 - 97, height: 440 - 316),
            "hkPay": CGRect(x: 417, y: 316, width: 541 - 417, height: 440 - 316),
            "hkTravel": CGRect(x: 737, y: 316, width: 861 - 737, height: 440 - 316),
            "hkCard": CGRect(x: 1057, y: 316, width: 1181 - 1057, height: 440 - 316),
            "hkMobile": CGRect(x: 593, y: 612, width: 683 - 593, height: 716 - 612),
            "hkCity": CGRect(x: 339, y: 848, width: 445 - 339, height: 951 - 848),
            "hkYuebao": CGRect(x: 833, y: 843, width: 937 - 833, height: 954 - 843),
            "hkMovie": CGRect(x: 1081, y: 611, width: 1184 - 1081, height: 718 - 611),
            "hkMore": CGRect(x: 1083, y: 849, width: 1182 - 1083, height: 945 - 849),
        ]
        for (i, name) in plusArt.enumerated() {
            let cy = plusT + plusRowH * (CGFloat(i) + 0.5)
            iconRects[name] = CGRect(x: plusIconCx - plusIconSize / 2, y: cy - plusIconSize / 2,
                                     width: plusIconSize, height: plusIconSize)
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.H) }

    // MARK: - 绘制（Java:223-243）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)
        drawHeader(d, s)
        drawGridCard(d)
        drawCommonCard(d)
        drawMessages(d, s)
        drawTabs(d, s)
        drawCreditCard(d, s)
        d.ctx.restoreGState()

        // 蓝色头图滚走之后，状态栏落在浅色内容上
        if s.scroll > 420 {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: 150), pageBg)
        }
        drawArt(d, "navbar")
        if s.plusMenu { drawPlusMenu(d) }
    }

    /// 头栏（Java:245-292）
    private func drawHeader(_ d: Draw, _ s: State) {
        let blackCard = LicaiVersion.blackCard(LicaiVersion.current())
        if blackCard {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: bandH), bandHeika)
        } else {
            // 参考图这支蓝是从右上角深处斜向左下角浅处走的，不是竖直渐变
            d.multiGradient(CGRect(x: 0, y: 0, width: Self.W, height: bandH),
                            colors: [bandBlueDark, bandBlueLight], stops: [0, 1], vertical: false)
        }

        let address = ellipsize(s.address, maxWidth: 200, size: 50)
        d.text(address, x: 44, baseline: 244, size: 50, .white)
        var textEnd = 44 + Draw.width(address, size: 50)
        textEnd = max(textEnd, 0)
        d.art(art["locArrow"], CGRect(x: textEnd + 14, y: 204, width: 50, height: 34))
        d.round(CGRect(x: 339, y: 163, width: 1101 - 339, height: 276 - 163), radius: 56, .white)
        drawArt(d, "fruit")
        d.text("花呗", x: 445, baseline: 240, size: 50, Draw.color(0xff3c3c3e))
        d.round(CGRect(x: 563, y: 190, width: 621 - 563, height: 249 - 190), radius: 12,
                Draw.color(0xffe2503a))
        d.centerText("热", cx: 592, baseline: 236, size: 38, .white, bold: true)
        d.rect(CGRect(x: 914, y: 192, width: 3, height: 240 - 192), Draw.color(0xffd9dce1))
        d.text("搜索", x: 963, baseline: 240, size: 50, Draw.color(0xff5a6474))

        // 右上「+」：圆环 + 十字
        d.ctx.saveGState()
        d.ctx.setStrokeColor(UIColor.white.cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.strokeEllipse(in: CGRect(x: 1190 - 37, y: 219 - 37, width: 74, height: 74))
        d.ctx.move(to: CGPoint(x: 1170, y: 219))
        d.ctx.addLine(to: CGPoint(x: 1210, y: 219))
        d.ctx.move(to: CGPoint(x: 1190, y: 199))
        d.ctx.addLine(to: CGPoint(x: 1190, y: 239))
        d.ctx.strokePath()
        d.ctx.restoreGState()

        let blackActions = ["hkScan", "hkPay", "hkTravel", "hkCard"]
        for i in 0..<4 {
            drawArt(d, blackCard ? blackActions[i] : actionArt[i])
            d.centerText(actions[i], cx: actionX[i], baseline: 494, size: 47, .white)
        }
    }

    /// 宫格卡（Java:295-312）
    private func drawGridCard(_ d: Draw) {
        let blackCard = LicaiVersion.blackCard(LicaiVersion.current())
        let box = CGRect(x: 0, y: 545, width: Self.W, height: 1032 - 545)
        d.clipped(to: box, radius: 36) {
            d.gradient(box, from: Draw.color(0xffc8daf9), to: .white, vertical: true)
        }
        for i in 0..<5 {
            let icon = (i == 2 && blackCard) ? "hkMobile" : gridRow1[i]
            drawArt(d, icon)
            d.centerText(gridRow1Text[i], cx: gridCols[i], baseline: 764, size: 43, label)

            let icon2: String
            if i == 1 && blackCard { icon2 = "hkCity" }
            else if i == 3 && blackCard { icon2 = "hkYuebao" }
            else if i == 4 && blackCard { icon2 = "hkMore" }
            else { icon2 = gridRow2[i] }
            drawArt(d, icon2)
            d.centerText(gridRow2Text[i], cx: gridCols[i], baseline: 998, size: 42, label)
        }
        d.round(CGRect(x: 617, y: 1054, width: 642 - 617, height: 10), radius: 5,
                Draw.color(0xff2b5fd9))
        d.round(CGRect(x: 650, y: 1054, width: 680 - 650, height: 10), radius: 5,
                Draw.color(0xffcfe0f7))
    }

    /// 常用卡（Java:314-334）
    private func drawCommonCard(_ d: Draw) {
        d.round(CGRect(x: 22, y: 1085, width: 1258 - 22, height: 1432 - 1085), radius: 26, cardColor)

        // 左上角的蓝色斜角标
        let tab = UIBezierPath()
        tab.move(to: CGPoint(x: 22, y: 1085))
        tab.addLine(to: CGPoint(x: 180, y: 1085))
        tab.addLine(to: CGPoint(x: 198, y: 1142))
        tab.addLine(to: CGPoint(x: 22, y: 1142))
        tab.close()
        d.ctx.saveGState()
        d.ctx.addPath(tab.cgPath)
        d.ctx.clip()
        d.gradient(CGRect(x: 22, y: 1085, width: 198 - 22, height: 1142 - 1085),
                   from: Draw.color(0xff84b9ff), to: Draw.color(0xff4a8df7), vertical: false)
        d.ctx.restoreGState()

        d.text("常用", x: 44, baseline: 1131, size: 44, .white, bold: true)
        d.text("全部", x: 1062, baseline: 1159, size: 45, Draw.color(0xff33363b))
        chevron(d, x: 1200, cy: 1140, color: Draw.color(0xff9aa0a8), halfH: 16, width: 4)

        for i in 0..<5 {
            drawArt(d, common[i])
            d.centerText(commonText[i], cx: commonCols[i], baseline: 1355, size: 43, label)
        }
        d.round(CGRect(x: 600, y: 1386, width: 628 - 600, height: 10), radius: 5,
                Draw.color(0xff2b5fd9))
        d.round(CGRect(x: 636, y: 1386, width: 660 - 636, height: 10), radius: 5,
                Draw.color(0xffcfe0f7))
    }

    /// 最近消息卡（Java:336-352）
    private func drawMessages(_ d: Draw, _ s: State) {
        d.round(CGRect(x: 22, y: 1456, width: 1258 - 22, height: 1876 - 1456), radius: 26, cardColor)
        d.text("最近消息", x: 66, baseline: 1546, size: 44, Draw.color(0xff8a8a8e))
        if !s.newMessage.isEmpty {
            d.rightText(s.newMessage, right: 1094, baseline: 1551, size: 45,
                        Draw.color(0xff4a4a4e))
        }
        if s.unread {
            d.circle(cx: 1141, cy: 1524, r: 22, Draw.color(0xffe8452c))
        }
        chevron(d, x: 1198, cy: 1524, color: Draw.color(0xff9aa0a8), halfH: 16, width: 4)

        if s.messages.count >= 2 {
            drawArt(d, "msgA")
            d.text(s.messages[0][0], x: 144, baseline: 1640, size: 48, ink, bold: true)
            d.text(s.messages[0][1], x: 144, baseline: 1709, size: 45, muted)
            d.rightText(s.messages[0][2], right: 1206, baseline: 1709, size: 40,
                        Draw.color(0xffb6bac0))

            drawArt(d, "msgH")
            d.text(s.messages[1][0], x: 144, baseline: 1798, size: 47, ink, bold: true)
            let detailX = 144 + Draw.width(s.messages[1][0], size: 47, bold: true) + 16
            d.text(s.messages[1][1], x: detailX, baseline: 1798, size: 45, muted)
            d.rightText(s.messages[1][2], right: 1206, baseline: 1798, size: 42,
                        Draw.color(0xffb6bac0))
        }
    }

    /// 页签条（Java:373-379）：整条是切图，只补一条「为你推荐」的下划线
    private func drawTabs(_ d: Draw, _ s: State) {
        drawArt(d, "tabbar")
        d.round(CGRect(x: 293, y: 2002, width: 347 - 293, height: 11), radius: 6,
                Draw.color(0xff2f6ff0))
    }

    /// 信用分卡（Java:381-394）
    private func drawCreditCard(_ d: Draw, _ s: State) {
        d.round(CGRect(x: 12, y: 2058, width: 1268 - 12, height: 2690 - 2058), radius: 28, cardColor)
        drawArt(d, "credit")
        let caption = ellipsize(s.creditLabel, maxWidth: 240, size: 34)
        if !caption.isEmpty {
            // 这行字在 Android 里是「居中 + 1.12 横向拉伸」
            d.centerTextSqueezed(caption, cx: 348, baseline: 2557, size: 34,
                                 Draw.color(0xff3f7fd0), squeeze: 1.12)
        }
        var score = s.score
        if !score.isEmpty && s.hideScoreTail {
            score = String(score.dropLast()) + "*"
        }
        if !score.isEmpty {
            d.centerText(score, cx: 345, baseline: 2470, size: 84, Draw.color(0xff3c71f0),
                         bold: true)
        }
    }

    /// 「加号」浮层菜单（Java:396-424）
    private func drawPlusMenu(_ d: Draw) {
        let panel = UIBezierPath(roundedRect: CGRect(x: plusL, y: plusT, width: plusR - plusL,
                                                    height: plusB - plusT),
                                 cornerRadius: plusRadius)
        panel.move(to: CGPoint(x: plusBeakCx - plusBeakHalf, y: plusT + 6))
        panel.addLine(to: CGPoint(x: plusBeakCx, y: plusBeakTip))
        panel.addLine(to: CGPoint(x: plusBeakCx + plusBeakHalf, y: plusT + 6))
        panel.close()
        d.ctx.saveGState()
        d.ctx.setFillColor(plusPanel.cgColor)
        d.ctx.addPath(panel.cgPath)
        d.ctx.fillPath()
        d.ctx.restoreGState()

        for i in 1..<plusItems.count {
            let y = plusT + plusRowH * CGFloat(i)
            d.rect(CGRect(x: plusDividerL, y: y, width: plusR - 4 - plusDividerL, height: 1.6),
                   plusHairline)
        }
        for (i, item) in plusItems.enumerated() {
            let cy = plusT + plusRowH * (CGFloat(i) + 0.5)
            let box = CGRect(x: plusIconCx - plusIconSize / 2, y: cy - plusIconSize / 2,
                             width: plusIconSize, height: plusIconSize)
            if let image = art[plusArt[i]] { d.art(image, box) }
            d.text(item, x: plusTextX, baseline: cy + 18, size: plusTextSize, .white)
        }
    }

    // MARK: - 图元

    private func drawArt(_ d: Draw, _ key: String) {
        guard let image = art[key], let box = iconRects[key] else { return }
        d.art(image, box)
    }

    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor,
                         halfH: CGFloat, width: CGFloat) {
        let dx = halfH * 0.45
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x - dx, y: cy - halfH))
        d.ctx.addLine(to: CGPoint(x: x + dx, y: cy))
        d.ctx.addLine(to: CGPoint(x: x - dx, y: cy + halfH))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func ellipsize(_ value: String, maxWidth: CGFloat, size: CGFloat) -> String {
        guard Draw.width(value, size: size) > maxWidth else { return value }
        var cut = value
        while cut.count > 1 && Draw.width(cut + "…", size: size) > maxWidth {
            cut.removeLast()
        }
        return cut + "…"
    }

    // MARK: - 命中（Java:429-488）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if s.plusMenu {
            if x < plusL || x > plusR || y < plusT || y > plusB { return "plusClose" }
            var row = Int((y - plusT) / plusRowH)
            row = max(0, min(plusItems.count - 1, row))
            return "plus:" + plusItems[row]
        }
        if y >= Self.navTop { return "nav:\(nearest(navX, x))" }

        let cy = y + s.scroll
        if y < 300 {
            if x > 1120 { return "plus" }
            if x > 330 && x < 1120 { return "search" }
            return "edit:address"
        }
        if cy >= 300 && cy <= 540 { return "action:" + actions[nearest(actionX, x)] }
        if cy > 545 && cy < 1060 {
            let col = column(x)
            if cy < 810 { return "icon:" + gridRow1Text[col] }
            if cy < 1030 { return "icon:" + gridRow2Text[col] }
            return "noop"
        }
        if cy >= 1060 && cy < 1100 { return "noop" }
        if cy >= 1085 && cy < 1432 {
            if cy < 1160 && x > 1040 { return "all" }
            return "icon:" + commonText[column(x)]
        }
        if cy >= 1456 && cy < 1876 {
            if cy < 1580 { return x > 850 ? "edit:newmsg" : "noop" }
            if cy < 1740 { return "edit:msg0" }
            return "edit:msg1"
        }
        if cy >= 1878 && cy < 2046 { return "noop" }
        if cy >= 2058 && cy < 2760 {
            if cy < 2160 && x > 1100 { return "zhimaMore" }
            if cy > 2380 && cy < 2510 && x > 230 && x < 460 { return "edit:score" }
            if cy > 2515 && cy < 2600 && x > 200 && x < 500 { return "edit:credit" }
            if cy > 2400 && cy < 2500 && x > 690 && x < 900 { return "collect" }
            return "zhima"
        }
        return "noop"
    }

    private func column(_ x: CGFloat) -> Int {
        for i in 0..<5 where x < colEdge[i + 1] { return i }
        return 4
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

// MARK: - 可编辑（地址、新消息提示、卡面分数与信用标签、两行消息标题）

extension ZhiHomeScreen.State: EditableState {
    func editTitle(_ key: String) -> String {
        switch key {
        case "address": return "编辑地址"
        case "newmsg": return "编辑「新消息」提示"
        case "score": return "编辑芝麻分"
        case "credit": return "编辑信用状态"
        case "msg0": return "编辑第 1 条消息标题"
        case "msg1": return "编辑第 2 条消息标题"
        default: return "编辑"
        }
    }

    func editValue(_ key: String) -> String {
        switch key {
        case "address": return address
        case "newmsg": return newMessage
        case "score": return score
        case "credit": return creditLabel
        case "msg0": return messages.count > 0 && messages[0].count > 0 ? messages[0][0] : ""
        case "msg1": return messages.count > 1 && messages[1].count > 0 ? messages[1][0] : ""
        default: return ""
        }
    }

    func applyEdit(_ key: String, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch key {
        case "address":
            if !trimmed.isEmpty { address = trimmed }
        case "newmsg":
            newMessage = trimmed
        case "score":
            if !trimmed.isEmpty { score = trimmed }
        case "credit":
            if !trimmed.isEmpty { creditLabel = trimmed }
        case "msg0":
            if !trimmed.isEmpty && messages.count > 0 && messages[0].count > 0 {
                messages[0][0] = trimmed
            }
        case "msg1":
            if !trimmed.isEmpty && messages.count > 1 && messages[1].count > 0 {
                messages[1][0] = trimmed
            }
        default:
            break
        }
    }
}
