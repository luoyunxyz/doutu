import UIKit

/// 「收款码」页 —— `features/pay/ui/CollectMoneyScreen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 背景是一支**斜向**橙黄渐变（9 个色标，向右下逐渐压暗），上面盖四张白卡，
/// 最上面是渐变头栏（盖住卡片顶部）与底部竖条 —— 与参考图一致：卡片可滚动，头尾固定。
///
/// 两个「程序生成」的东西：
///   1. **二维码**是 37×37 的模块阵列，用固定 LCG 生成，再叠上三个定位角、时序线和一个对齐块；
///      中间贴 `qrlogo` 那张小图。关掉抗锯齿，否则模块之间会出现白缝。
///   2. 「升级你的收钱码」那张卡是两个立牌 + 气泡 + 印章的切图，只把两行字与按钮画上去。
final class CollectMoneyScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let contentH: CGFloat = 3060
    /// 滚动视口下沿（这以下是固定的头尾）
    static let viewportBottom: CGFloat = 2752

    final class State: ScreenState {
        var scroll: CGFloat = 0
        var name = "张三的小店"
    }

    // MARK: - 调色板（Java:39-60）
    private let bgColors: [UIColor] = [
        Draw.color(0xfff4b45a), Draw.color(0xfff3ae55), Draw.color(0xfff2a64d),
        Draw.color(0xfff09f47), Draw.color(0xfff09c45), Draw.color(0xfff09a44),
        Draw.color(0xffef9a45), Draw.color(0xffef9841), Draw.color(0xffef953e),
    ]
    private let bgStops: [CGFloat] = [0, 0.20, 0.30, 0.40, 0.50, 0.60, 0.75, 0.90, 1]
    private let ink = Draw.color(0xff202020)
    private let muted = Draw.color(0xff909090)
    private let protectInk = Draw.color(0xff5c5c5c)
    private let chevronLight = Draw.color(0xffcacaca)
    private let chevronMid = Draw.color(0xff999999)
    private let blue = Draw.color(0xff3a78f0)
    private let redDot = Draw.color(0xffeb5232)
    private let cream = Draw.color(0xfffdf7f1)
    private let dividerColor = Draw.color(0xffececec)
    private let codeInk = Draw.color(0xff000000)
    private let upInk = Draw.color(0xff26292c)
    private let upSubInk = Draw.color(0xff4c4e52)

    // MARK: - 几何（Java:64-205）
    private let cardL: CGFloat = 54
    private let cardR: CGFloat = 1226
    private let cardRadius: CGFloat = 46
    private let card1T: CGFloat = 320
    private let card1B: CGFloat = 1999
    private let card2T: CGFloat = 2030
    private let card2B: CGFloat = 2198
    private let card3T: CGFloat = 2230
    private let card3B: CGFloat = 2643

    private let upT: CGFloat = 2686
    private let upB: CGFloat = 2999
    private let upArtL: CGFloat = 54
    private let upArtT: CGFloat = 2690
    private let upTitleX: CGFloat = 416
    private let upTitleBase: CGFloat = 2813
    private let upTitleSize: CGFloat = 61
    private let upTitleW: CGFloat = 376
    private let upSubX: CGFloat = 415
    private let upSubBase: CGFloat = 2898
    private let upSubSize: CGFloat = 52
    private let upSubW: CGFloat = 399
    private let upBtnL: CGFloat = 940
    private let upBtnT: CGFloat = 2800
    private let upBtnR: CGFloat = 1192
    private let upBtnB: CGFloat = 2890
    private let upBtnTextBase: CGFloat = 2863
    private let upBtnTextSize: CGFloat = 40

    private let backTipX: CGFloat = 70
    private let backCy: CGFloat = 220
    private let backHalfW: CGFloat = 29
    private let backHalfH: CGFloat = 26
    private let titleX: CGFloat = 164
    private let titleBase: CGFloat = 240
    private let titleSize: CGFloat = 63
    private let dotCx: [CGFloat] = [1171, 1195, 1218]
    private let dotCy: CGFloat = 220
    private let dotR: CGFloat = 8

    private let headIconL: CGFloat = 96
    private let headIconT: CGFloat = 368
    private let headTextX: CGFloat = 218
    private let headTextBase: CGFloat = 425
    private let headTextSize: CGFloat = 60
    private let collapseCx: CGFloat = 1152
    private let collapseCy: CGFloat = 405
    private let collapseHalfW: CGFloat = 26
    private let collapseHalfH: CGFloat = 15

    private let dividerL: CGFloat = 100
    private let dividerR: CGFloat = 1178
    private let dividerW: CGFloat = 2

    private let shieldL: CGFloat = 462
    private let shieldT: CGFloat = 534
    private let protectX: CGFloat = 531
    private let protectBase: CGFloat = 577
    private let protectSize: CGFloat = 46

    private let nameGap: CGFloat = 16
    private let nameBase: CGFloat = 677
    private let nameSize: CGFloat = 60
    private let editBase: CGFloat = 674
    private let editSize: CGFloat = 56

    private let qrL: CGFloat = 323
    private let qrT: CGFloat = 759
    private let qrSize: CGFloat = 634
    private let qrModules = 37
    private let qrLogoL: CGFloat = 585
    private let qrLogoT: CGFloat = 1021
    private let qrLogoSize: CGFloat = 110

    private let actBase: CGFloat = 1523
    private let actSize: CGFloat = 53
    private let actCx: [CGFloat] = [254, 639, 1025.5]
    private let actLabels = ["设置金额", "保存图片", "更多设置"]
    private let actSepX: [CGFloat] = [446.5, 832.5]
    private let actSepT: CGFloat = 1479
    private let actSepB: CGFloat = 1531

    private let receiptL: CGFloat = 100
    private let receiptT: CGFloat = 1729
    private let rowTextX: CGFloat = 216
    private let receiptBase: CGFloat = 1785
    private let rowTextSize: CGFloat = 60
    private let receiptChevX: CGFloat = 1169
    private let receiptChevCy: CGFloat = 1765
    private let receiptChevHalfW: CGFloat = 13
    private let receiptChevHalfH: CGFloat = 24

    private let creamT: CGFloat = 1858
    private let assistIconL: CGFloat = 96
    private let assistIconT: CGFloat = 1892
    private let assistBase: CGFloat = 1947
    private let assistSize: CGFloat = 50
    private let pill1L: CGFloat = 973
    private let pill1T: CGFloat = 1883
    private let pill1R: CGFloat = 1179
    private let pill1B: CGFloat = 1972
    private let pill1TextBase: CGFloat = 1943
    private let pill1TextSize: CGFloat = 43

    private let shopL: CGFloat = 92
    private let shopT: CGFloat = 2076
    private let bizBase: CGFloat = 2134
    private let supportX: CGFloat = 1063
    private let supportBase: CGFloat = 2131
    private let supportSize: CGFloat = 53
    private let redCx: CGFloat = 1094.5
    private let redCy: CGFloat = 2113.5
    private let redR: CGFloat = 18
    private let chevDownCx: CGFloat = 1153
    private let chevDownCy: CGFloat = 2113
    private let chevDownHalfW: CGFloat = 25
    private let chevDownHalfH: CGFloat = 14

    private let storeL: CGFloat = 96
    private let storeT: CGFloat = 2275
    private let serviceBase: CGFloat = 2336
    private let serviceChevX: CGFloat = 1166
    private let serviceChevCy: CGFloat = 2313

    private let divider3Y: CGFloat = 2399
    private let promoL: CGFloat = 92
    private let promoT: CGFloat = 2449
    private let promoTextX: CGFloat = 280
    private let promoTitleBase: CGFloat = 2509
    private let promoSubBase: CGFloat = 2584
    private let pill2L: CGFloat = 933
    private let pill2T: CGFloat = 2478
    private let pill2R: CGFloat = 1177
    private let pill2B: CGFloat = 2568
    private let pill2TextBase: CGFloat = 2539
    private let pill2TextSize: CGFloat = 46

    /// 八张切图（Java:217-224）
    private static let artNames = [
        "bag": "pay_collect_bag", "receipt": "pay_collect_receipt", "shop": "pay_collect_shop",
        "store": "pay_collect_store", "shield": "pay_collect_shield",
        "promo": "pay_collect_promo", "qrlogo": "pay_collect_qrlogo",
        "upgrade": "pay_collect_upgrade",
    ]
    private var art: [String: UIImage] = [:]
    /// 二维码模块阵列（生成一次）
    private var qrField: [Bool]?
    /// 与 Android 同一个种子
    private let qrSeed: Int64 = 0x5eed1234abcd

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.viewportBottom) }

    // MARK: - 绘制（Java:236-257）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        // 整页斜向渐变
        d.multiGradient(CGRect(x: 0, y: 0, width: Self.W, height: Self.H),
                        colors: bgColors, stops: bgStops, vertical: false)

        // 卡片按滚动偏移画在视口内
        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: 0, width: Self.W, height: Self.viewportBottom))
        d.ctx.translateBy(x: 0, y: -s.scroll)
        drawContent(d, s)
        d.ctx.restoreGState()

        // 固定的头栏与底部竖条
        d.multiGradient(CGRect(x: 0, y: 0, width: Self.W, height: card1T),
                        colors: bgColors, stops: bgStops, vertical: false)
        d.multiGradient(CGRect(x: 0, y: Self.viewportBottom, width: Self.W,
                               height: Self.H - Self.viewportBottom),
                        colors: bgColors, stops: bgStops, vertical: false)
        drawTopBar(d)
    }

    private func drawContent(_ d: Draw, _ s: State) {
        drawCard(d, card1T, card1B)
        drawCard(d, card2T, card2B)
        drawCard(d, card3T, card3B)
        drawCard(d, upT, upB)

        drawCard1(d, s)
        drawCard2(d)
        drawCard3(d)
        drawUpgradeCard(d)
    }

    private func drawCard(_ d: Draw, _ top: CGFloat, _ bottom: CGFloat) {
        d.round(CGRect(x: cardL, y: top, width: cardR - cardL, height: bottom - top),
                radius: cardRadius, .white)
    }

    /// 顶部固定栏（Java:277-286）
    private func drawTopBar(_ d: Draw) {
        stroke(d, from: CGPoint(x: backTipX + backHalfW, y: backCy - backHalfH),
               to: CGPoint(x: backTipX, y: backCy), color: .white, width: 8)
        stroke(d, from: CGPoint(x: backTipX, y: backCy),
               to: CGPoint(x: backTipX + backHalfW, y: backCy + backHalfH), color: .white, width: 8)
        d.text("收钱", x: titleX, baseline: titleBase, size: titleSize, .white)
        for cx in dotCx { d.circle(cx: cx, cy: dotCy, r: dotR, .white) }
    }

    /// 第一张卡（Java:288-332）
    private func drawCard1(_ d: Draw, _ s: State) {
        drawArt(d, "bag", headIconL, headIconT)
        d.text("个人收钱", x: headTextX, baseline: headTextBase, size: headTextSize, ink)
        chevronUp(d, cx: collapseCx, cy: collapseCy, halfW: collapseHalfW, halfH: collapseHalfH,
                  color: chevronMid, width: 6)
        divider(d, 490)

        drawArt(d, "shield", shieldL, shieldT)
        d.text("你敢收 我敢赔", x: protectX, baseline: protectBase, size: protectSize, protectInk)

        // 名字与「修改」一起居中
        let nameWidth = Draw.width(s.name, size: nameSize)
        let editWidth = Draw.width("修改", size: editSize)
        let lineWidth = nameWidth + nameGap + editWidth
        let lineLeft = (cardL + cardR) / 2 - lineWidth / 2
        d.text(s.name, x: lineLeft, baseline: nameBase, size: nameSize, ink)
        d.text("修改", x: lineLeft + nameWidth + nameGap, baseline: editBase, size: editSize, blue)

        drawCode(d, s)

        for (i, label) in actLabels.enumerated() {
            d.centerText(label, cx: actCx[i], baseline: actBase, size: actSize, muted)
        }
        for x in actSepX {
            stroke(d, from: CGPoint(x: x, y: actSepT), to: CGPoint(x: x, y: actSepB),
                   color: Draw.color(0xffdcdcdc), width: 2)
        }
        divider(d, 1679)

        drawArt(d, "receipt", receiptL, receiptT)
        d.text("收款记录", x: rowTextX, baseline: receiptBase, size: rowTextSize, ink)
        chevronRight(d, x: receiptChevX, cy: receiptChevCy, halfW: receiptChevHalfW,
                     halfH: receiptChevHalfH, color: chevronLight, width: 5)

        // 米色助手条：裁剪到卡片圆角内
        d.clipped(to: CGRect(x: cardL, y: creamT, width: cardR - cardL, height: card1B - creamT),
                  radius: cardRadius) {
            d.rect(CGRect(x: cardL, y: creamT, width: cardR - cardL, height: card1B - creamT), cream)
        }
        drawArt(d, "bag", assistIconL, assistIconT)
        d.text("开启收款助手，首页便捷查账", x: rowTextX, baseline: assistBase, size: assistSize, ink)
        pill(d, CGRect(x: pill1L, y: pill1T, width: pill1R - pill1L, height: pill1B - pill1T),
             label: "去开启", baseline: pill1TextBase, size: pill1TextSize)
    }

    /// 第二张卡（Java:334-342）
    private func drawCard2(_ d: Draw) {
        drawArt(d, "shop", shopL, shopT)
        d.text("经营收钱", x: rowTextX, baseline: bizBase, size: rowTextSize, ink)
        d.rightText("支持信用卡/花呗", right: supportX, baseline: supportBase, size: supportSize,
                    muted)
        d.circle(cx: redCx, cy: redCy, r: redR, redDot)
        chevronDown(d, cx: chevDownCx, cy: chevDownCy, halfW: chevDownHalfW,
                    halfH: chevDownHalfH, color: chevronMid, width: 5)
    }

    /// 第三张卡（Java:344-355）
    private func drawCard3(_ d: Draw) {
        drawArt(d, "store", storeL, storeT)
        d.text("商家服务", x: rowTextX, baseline: serviceBase, size: rowTextSize, ink)
        chevronRight(d, x: serviceChevX, cy: serviceChevCy, halfW: 13, halfH: 23,
                     color: chevronLight, width: 5)
        divider(d, divider3Y)

        drawArt(d, "promo", promoL, promoT)
        d.text("充值锁客提升复购", x: promoTextX, baseline: promoTitleBase, size: rowTextSize, ink)
        d.text("超50万商家使用中", x: promoTextX, baseline: promoSubBase, size: supportSize, muted)
        pill(d, CGRect(x: pill2L, y: pill2T, width: pill2R - pill2L, height: pill2B - pill2T),
             label: "去开通", baseline: pill2TextBase, size: pill2TextSize)
    }

    /// 升级卡（Java:358-378）
    private func drawUpgradeCard(_ d: Draw) {
        drawArt(d, "upgrade", upArtL, upArtT)
        textFit(d, "升级你的收钱码", x: upTitleX, baseline: upTitleBase, size: upTitleSize,
                maxWidth: upTitleW, color: upInk, bold: true)
        textFit(d, "首碰收款奖2元红包", x: upSubX, baseline: upSubBase, size: upSubSize,
                maxWidth: upSubW, color: upSubInk, bold: false)

        let button = CGRect(x: upBtnL, y: upBtnT, width: upBtnR - upBtnL, height: upBtnB - upBtnT)
        d.clipped(to: button, radius: button.height / 2) {
            d.gradient(button, from: Draw.color(0xff3d90f4), to: Draw.color(0xff3379ee),
                       vertical: true)
        }
        d.centerText("立即换", cx: (upBtnL + upBtnR) / 2, baseline: upBtnTextBase,
                     size: upBtnTextSize, .white)
    }

    /// 二维码（Java:380-404）：37×37 模块阵列 + 中间小图
    private func drawCode(_ d: Draw, _ s: State) {
        let module = qrSize / CGFloat(qrModules)
        let field = ensureQr()
        d.ctx.saveGState()
        d.ctx.setShouldAntialias(false)
        d.ctx.setFillColor(codeInk.cgColor)
        for y in 0..<qrModules {
            for x in 0..<qrModules where field[y * qrModules + x] {
                let left = qrL + CGFloat(x) * module
                let top = qrT + CGFloat(y) * module
                d.ctx.fill(CGRect(x: left, y: top, width: module, height: module))
            }
        }
        d.ctx.restoreGState()
        drawArt(d, "qrlogo", qrLogoSize: qrLogoSize)
    }

    /// 生成二维码模块（Java:409-453）：与 Android 同一个 LCG、同样的定位角与时序线
    private func ensureQr() -> [Bool] {
        if let cached = qrField { return cached }
        let n = qrModules
        var m = [Bool](repeating: false, count: n * n)
        var state = qrSeed ^ 0x9e3779b97f4a7c15
        for i in 0..<m.count {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            m[i] = ((state >> 41) & 1) == 1
        }
        // 清空三个定位角与左下角区域
        for y in 0..<8 {
            for x in 0..<8 {
                m[y * n + x] = false
                m[y * n + (n - 8 + x)] = false
                m[(n - 8 + y) * n + x] = false
            }
        }
        finder(&m, n, 0, 0)
        finder(&m, n, n - 7, 0)
        finder(&m, n, 0, n - 7)
        for i in 8..<(n - 8) {
            m[6 * n + i] = i % 2 == 0
            m[i * n + 6] = i % 2 == 0
        }
        align(&m, n, n - 9, n - 9)
        qrField = m
        return m
    }

    private func finder(_ m: inout [Bool], _ n: Int, _ ox: Int, _ oy: Int) {
        for y in 0..<7 {
            for x in 0..<7 {
                let edge = x == 0 || x == 6 || y == 0 || y == 6
                let core = x >= 2 && x <= 4 && y >= 2 && y <= 4
                m[(oy + y) * n + ox + x] = edge || core
            }
        }
    }

    private func align(_ m: inout [Bool], _ n: Int, _ cx: Int, _ cy: Int) {
        for y in -2...2 {
            for x in -2...2 {
                let ring = max(abs(x), abs(y)) != 1
                m[(cy + y) * n + cx + x] = ring
            }
        }
    }

    // MARK: - 图元

    private func divider(_ d: Draw, _ y: CGFloat) {
        stroke(d, from: CGPoint(x: dividerL, y: y), to: CGPoint(x: dividerR, y: y),
               color: dividerColor, width: dividerW)
    }

    /// 蓝色描边胶囊 + 蓝字（Java:506-516）
    private func pill(_ d: Draw, _ box: CGRect, label: String, baseline: CGFloat, size: CGFloat) {
        d.roundStroke(box, radius: box.height / 2, blue, width: 5)
        d.centerText(label, cx: box.midX, baseline: baseline, size: size, blue)
    }

    /// 按原始尺寸贴图（Java:518-529）
    private func drawArt(_ d: Draw, _ key: String, _ left: CGFloat, _ top: CGFloat) {
        drawArt(d, key, qrLogoSize: 0, left: left, top: top)
    }

    private func drawArt(_ d: Draw, _ key: String, qrLogoSize: CGFloat,
                         left: CGFloat? = nil, top: CGFloat? = nil) {
        guard let image = art[key] else { return }
        let w = key == "qrlogo" ? qrLogoSize : image.size.width * image.scale
        let h = key == "qrlogo" ? qrLogoSize : image.size.height * image.scale
        let x = left ?? qrLogoL
        let y = top ?? qrLogoT
        d.art(image, CGRect(x: x, y: y, width: w, height: h))
    }

    private func stroke(_ d: Draw, from: CGPoint, to: CGPoint, color: UIColor, width: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: from)
        d.ctx.addLine(to: to)
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func chevronUp(_ d: Draw, cx: CGFloat, cy: CGFloat, halfW: CGFloat, halfH: CGFloat,
                           color: UIColor, width: CGFloat) {
        stroke(d, from: CGPoint(x: cx - halfW, y: cy + halfH),
               to: CGPoint(x: cx, y: cy - halfH), color: color, width: width)
        stroke(d, from: CGPoint(x: cx, y: cy - halfH),
               to: CGPoint(x: cx + halfW, y: cy + halfH), color: color, width: width)
    }

    private func chevronDown(_ d: Draw, cx: CGFloat, cy: CGFloat, halfW: CGFloat, halfH: CGFloat,
                             color: UIColor, width: CGFloat) {
        stroke(d, from: CGPoint(x: cx - halfW, y: cy - halfH),
               to: CGPoint(x: cx, y: cy + halfH), color: color, width: width)
        stroke(d, from: CGPoint(x: cx, y: cy + halfH),
               to: CGPoint(x: cx + halfW, y: cy - halfH), color: color, width: width)
    }

    private func chevronRight(_ d: Draw, x: CGFloat, cy: CGFloat, halfW: CGFloat, halfH: CGFloat,
                              color: UIColor, width: CGFloat) {
        stroke(d, from: CGPoint(x: x - halfW, y: cy - halfH),
               to: CGPoint(x: x + halfW, y: cy), color: color, width: width)
        stroke(d, from: CGPoint(x: x + halfW, y: cy),
               to: CGPoint(x: x - halfW, y: cy + halfH), color: color, width: width)
    }

    /// 超宽时横向压缩（Java 的 `textFit`）
    private func textFit(_ d: Draw, _ value: String, x: CGFloat, baseline: CGFloat, size: CGFloat,
                         maxWidth: CGFloat, color: UIColor, bold: Bool) {
        let natural = Draw.width(value, size: size, bold: bold)
        let squeeze = natural > maxWidth ? maxWidth / natural : 1
        d.textSqueezed(value, x: x, baseline: baseline, size: size, color,
                       bold: bold, squeeze: squeeze)
    }

    // MARK: - 命中（Java:461-498）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if y < 320 {
            if x < 150 { return "back" }
            if x > 1100 { return "more" }
            return "noop"
        }
        let cy = y + s.scroll
        if x > qrL - 10 && x < qrL + qrSize + 10 && cy > qrT - 10 && cy < qrT + qrSize + 10 {
            return "qr"
        }
        if cy > 600 && cy < 705 { return "name" }
        if cy > 1440 && cy < 1570 {
            if x < actSepX[0] { return "amount" }
            if x < actSepX[1] { return "save" }
            return "more2"
        }
        if cy > 1679 && cy < creamT { return "records" }
        if x > pill1L && x < pill1R && cy > pill1T - 20 && cy < pill1B + 20 { return "assistant" }
        if cy > card2T && cy < card2B { return "business" }
        if cy > divider3Y && cy < card3B {
            if x > pill2L && x < pill2R && cy > pill2T - 20 && cy < pill2B + 20 { return "promo" }
            return "noop"
        }
        if cy > card3T && cy < divider3Y { return "service" }
        if cy > upT && cy < upB {
            if x > upBtnL && x < upBtnR && cy > upBtnT && cy < upBtnB { return "upgrade" }
            return "noop"
        }
        return "noop"
    }
}
