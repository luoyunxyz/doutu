import UIKit

/// 「收付款码」页 —— `features/pay/ui/PayCodeV2Screen.java` 的 1:1 移植（参考画布 1280×2856）。
///
/// 结构：整页蓝色渐变底 → 白色大卡（顶边中间有个**向下鼓出的「会员」页舌**，用的是同一支渐变，
/// 所以看不出接缝）→ 条形码 / 说明 / 二维码 / 账户行 / 优惠条（只有底部两角跟随卡片圆角）
/// → 底部深蓝快捷面板（收钱 / 碰一下 / 转账）→ 顶部胶囊页签。
///
/// 两个细节：
/// 1. **条形码不是图片**，是按固定 LCG 生成的确定性条纹（`ensureBarcode`）：每根条宽 1–3 单元、
///    间隔 3–6 单元，单元 5.4px。两端带抗锯齿反而会出白缝，所以这一段关掉抗锯齿。
/// 2. 二维码直接贴 `pay_code_qr` 那张原图（里面自带支付宝徽章），中间不再叠任何东西。
final class PayCodeV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2856)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2856

    final class State: ScreenState {
        /// 0 = 收付款，1 = 支付有礼
        var tab: Int = 0
    }

    // MARK: - 调色板（Java:36-51）
    private let bgTop = Draw.color(0xff3f80f0)
    private let bgBottom = Draw.color(0xff2a63e6)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff3c3c3e)
    private let muted = Draw.color(0xffa2a6ad)
    private let codeInk = Draw.color(0xff000000)
    private let capsule = Draw.color(0xff2a5fd9)
    private let capsuleEdge = Draw.color(0x33000000)
    private let pill = Draw.color(0xfff2f0f4)
    private let pillText = Draw.color(0xff8b8b93)
    private let panel = Draw.color(0xff578ceb)
    private let panelLine = Draw.color(0x30ffffff)
    private let bannerBg = Draw.color(0xfff9ece7)
    private let bannerBadge = Draw.color(0xffe4713c)
    private let bannerText = Draw.color(0xffc06a4a)
    private let chevronGrey = Draw.color(0xffc2c6cc)

    // MARK: - 几何（Java:55-144）
    private let cardL: CGFloat = 46
    private let cardT: CGFloat = 371
    private let cardR: CGFloat = 1237
    private let cardB: CGFloat = 2087
    private let cardRadius: CGFloat = 36

    private let tabL: CGFloat = 450
    private let tabR: CGFloat = 830
    private let tabDip: CGFloat = 404

    private let vipIconL: CGFloat = 501
    private let vipIconT: CGFloat = 315
    private let vipIconSize: CGFloat = 56
    private let vipTextX: CGFloat = 570
    private let vipTextBase: CGFloat = 362
    private let vipTextSize: CGFloat = 50

    private let merchantIconL: CGFloat = 93
    private let merchantIconT: CGFloat = 417
    private let merchantIconSize: CGFloat = 70
    private let merchantTextX: CGFloat = 217
    private let merchantTextBase: CGFloat = 474
    private let merchantTextSize: CGFloat = 58

    private let barcodeL: CGFloat = 78
    private let barcodeR: CGFloat = 1215
    private let barcodeT: CGFloat = 545
    private let barcodeB: CGFloat = 875

    private let captionBase: CGFloat = 958
    private let captionSize: CGFloat = 49

    private let qrL: CGFloat = 361
    private let qrT: CGFloat = 1075
    private let qrSize: CGFloat = 560

    private let balanceL: CGFloat = 88
    private let balanceT: CGFloat = 1795
    private let balanceSize: CGFloat = 80
    private let accountTextX: CGFloat = 214
    private let accountTitleBase: CGFloat = 1830
    private let accountTitleSize: CGFloat = 62
    private let accountSubBase: CGFloat = 1898
    private let accountSubSize: CGFloat = 46
    private let accountChevronX: CGFloat = 1162
    private let accountChevronY: CGFloat = 1838

    private let bannerT: CGFloat = 1945
    private let bannerBadgeL: CGFloat = 91
    private let bannerBadgeR: CGFloat = 323
    private let bannerBadgeT: CGFloat = 1978
    private let bannerBadgeB: CGFloat = 2045
    private let bannerTextX: CGFloat = 372
    private let bannerTextBase: CGFloat = 2033
    private let bannerTextSize: CGFloat = 43

    private let panelL: CGFloat = 48
    private let panelT: CGFloat = 2096
    private let panelR: CGFloat = 1238
    private let panelB: CGFloat = 2648
    private let panelRadius: CGFloat = 30
    private let rowStep: CGFloat = 174
    private let rowFirstCy: CGFloat = 2204
    private let rowIconCx: CGFloat = 130
    private let rowTextX: CGFloat = 217
    private let rowTextSize: CGFloat = 54
    private let rowChevronX: CGFloat = 1162
    private let rowChevronH: CGFloat = 46
    private let rowDividerL: CGFloat = 128

    private let capsuleL: CGFloat = 385
    private let capsuleR: CGFloat = 890
    private let capsuleT: CGFloat = 163
    private let capsuleB: CGFloat = 285
    private let pillL: CGFloat = 390
    private let pillR: CGFloat = 637
    private let pillLSecond: CGFloat = 640
    private let pillRSecond: CGFloat = 886
    private let pillT: CGFloat = 168
    private let pillB: CGFloat = 280
    private let tabTextBase: CGFloat = 244
    private let tabTextSize: CGFloat = 45
    private let tabFirstCx: CGFloat = 513
    private let tabSecondCx: CGFloat = 772

    private static let rowLabels = ["收钱", "碰一下", "转账"]
    private static let rowArt = ["pay_icon_collect", "pay_icon_tap", "pay_icon_transfer"]
    private static let rowIconBox: [CGFloat] = [72, 70, 80, 48, 70, 73]

    /// 七张切图（Java:156-162）
    private static let artNames = [
        "vip": "pay_icon_vip", "collect": "pay_icon_collect", "tap": "pay_icon_tap",
        "transfer": "pay_icon_transfer", "balance": "pay_icon_balance",
        "qrCode": "pay_code_qr", "merchant": "pay_icon_merchant",
    ]
    private var art: [String: UIImage] = [:]

    /// 条形码种子（Java:152）
    private let barcodeSeed: Int64 = 0x5eed1234abcd
    private var barcodeRects: [CGRect]?

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:169-190）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: Self.H),
                   from: bgTop, to: bgBottom, vertical: true)

        drawCard(d)
        if s.tab == 0 {
            drawMerchantRow(d)
            drawBarcodeStrip(d)
            d.centerText("点击可查看付款码数字", cx: Self.W / 2, baseline: captionBase,
                         size: captionSize, muted)
            drawQr(d)
            drawAccountRow(d)
            drawDiscount(d)
        } else {
            d.centerText("支付有礼 · 敬请期待", cx: Self.W / 2, baseline: cardT + 300,
                         size: 48, muted)
        }
        drawShortcuts(d)
        drawTopBar(d, s)
    }

    /// 白卡 + 顶部中间那块「页舌」（Java:193-212）
    private func drawCard(_ d: Draw) {
        d.round(CGRect(x: cardL, y: cardT, width: cardR - cardL, height: cardB - cardT),
                radius: cardRadius, cardColor)

        // 页舌：一片用同一支蓝渐变填充、向下鼓出的曲线，所以看不出接缝
        d.ctx.saveGState()
        d.ctx.beginPath()
        d.ctx.move(to: CGPoint(x: tabL, y: cardT))
        d.ctx.addCurve(to: CGPoint(x: tabR, y: cardT),
                       control1: CGPoint(x: tabL + 80, y: tabDip),
                       control2: CGPoint(x: tabR - 80, y: tabDip))
        d.ctx.closePath()
        d.ctx.clip()
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: Self.H),
                   from: bgTop, to: bgBottom, vertical: true)
        d.ctx.restoreGState()

        drawArt(d, "vip", CGRect(x: vipIconL, y: vipIconT, width: vipIconSize, height: vipIconSize))
        d.text("大众会员", x: vipTextX, baseline: vipTextBase, size: vipTextSize, UIColor.white)
    }

    private func drawMerchantRow(_ d: Draw) {
        drawArt(d, "merchant", CGRect(x: merchantIconL, y: merchantIconT,
                                      width: merchantIconSize, height: merchantIconSize))
        d.text("向商家付款", x: merchantTextX, baseline: merchantTextBase, size: merchantTextSize, ink)
    }

    /// 条形码：关掉抗锯齿画实心方块（Java:220-229）
    private func drawBarcodeStrip(_ d: Draw) {
        let rects = ensureBarcode()
        d.ctx.saveGState()
        d.ctx.setShouldAntialias(false)
        d.ctx.setFillColor(codeInk.cgColor)
        for rect in rects {
            d.ctx.fill(rect)
        }
        d.ctx.restoreGState()
    }

    /// 二维码：直接贴原图（里面自带徽章），先铺一块白底（Java:235-249）
    private func drawQr(_ d: Draw) {
        guard let code = art["qrCode"] else { return }
        let box = CGRect(x: qrL, y: qrT, width: qrSize, height: qrSize)
        d.ctx.saveGState()
        d.ctx.setShouldAntialias(false)
        d.ctx.setFillColor(UIColor.white.cgColor)
        d.ctx.fill(box)
        d.ctx.restoreGState()
        d.art(code, box)
    }

    private func drawAccountRow(_ d: Draw) {
        drawArt(d, "balance", CGRect(x: balanceL, y: balanceT, width: balanceSize, height: balanceSize))
        d.text("账户余额", x: accountTextX, baseline: accountTitleBase, size: accountTitleSize, ink)
        d.text("优先使用此付款方式", x: accountTextX, baseline: accountSubBase,
               size: accountSubSize, muted)
        chevron(d, x: accountChevronX, y: accountChevronY, color: chevronGrey,
                halfH: 24, width: 4.5)
    }

    /// 优惠条：只有底部两角跟随卡片圆角（Java:258-280）
    private func drawDiscount(_ d: Draw) {
        d.ctx.saveGState()
        d.ctx.setFillColor(bannerBg.cgColor)
        d.ctx.beginPath()
        d.ctx.move(to: CGPoint(x: cardL, y: bannerT))
        d.ctx.addLine(to: CGPoint(x: cardR, y: bannerT))
        d.ctx.addLine(to: CGPoint(x: cardR, y: cardB - cardRadius))
        d.ctx.addQuadCurve(to: CGPoint(x: cardR - cardRadius, y: cardB),
                           control: CGPoint(x: cardR, y: cardB))
        d.ctx.addLine(to: CGPoint(x: cardL + cardRadius, y: cardB))
        d.ctx.addQuadCurve(to: CGPoint(x: cardL, y: cardB - cardRadius),
                           control: CGPoint(x: cardL, y: cardB))
        d.ctx.closePath()
        d.ctx.fillPath()
        d.ctx.restoreGState()

        d.round(CGRect(x: bannerBadgeL, y: bannerBadgeT,
                       width: bannerBadgeR - bannerBadgeL, height: bannerBadgeB - bannerBadgeT),
                radius: 10, bannerBadge)
        d.centerText("百次立减", cx: (bannerBadgeL + bannerBadgeR) / 2, baseline: 2033,
                     size: 42, UIColor.white, bold: true)
        d.text("优惠生效中，本单可立减", x: bannerTextX, baseline: bannerTextBase,
               size: bannerTextSize, bannerText)
    }

    /// 底部深蓝快捷面板（Java:282-301）
    private func drawShortcuts(_ d: Draw) {
        d.round(CGRect(x: panelL, y: panelT, width: panelR - panelL, height: panelB - panelT),
                radius: panelRadius, panel)

        for i in 1..<Self.rowLabels.count {
            let y = rowFirstCy + rowStep * CGFloat(i) - rowStep / 2
            line(d, 0, x1: rowDividerL, y1: y, x2: panelR - 10, y2: y, color: panelLine, width: 2)
        }
        for i in 0..<Self.rowLabels.count {
            let cy = rowFirstCy + rowStep * CGFloat(i)
            let w = Self.rowIconBox[i * 2]
            let h = Self.rowIconBox[i * 2 + 1]
            drawArt(d, Self.rowArt[i], CGRect(x: rowIconCx - w / 2, y: cy - h / 2, width: w, height: h))
            d.text(Self.rowLabels[i], x: rowTextX, baseline: cy + 19, size: rowTextSize, UIColor.white)
            chevron(d, x: rowChevronX, y: cy, color: UIColor.white, halfH: 20, width: 5)
        }
    }

    /// 顶部胶囊页签 + 返回 + 更多（Java:303-334）
    private func drawTopBar(_ d: Draw, _ s: State) {
        let capsuleBox = CGRect(x: capsuleL, y: capsuleT,
                                width: capsuleR - capsuleL, height: capsuleB - capsuleT)
        let radius = (capsuleB - capsuleT) / 2
        d.round(capsuleBox, radius: radius, capsule)
        d.roundStroke(capsuleBox, radius: radius, capsuleEdge, width: 2.5)

        // 浅色药丸压在选中的那一页签下面
        let pl = s.tab == 0 ? pillL : pillLSecond
        let pr = s.tab == 0 ? pillR : pillRSecond
        d.round(CGRect(x: pl, y: pillT, width: pr - pl, height: pillB - pillT),
                radius: (pillB - pillT) / 2, pill)

        d.centerText("收付款", cx: tabFirstCx, baseline: tabTextBase, size: tabTextSize,
                     s.tab == 0 ? pillText : UIColor.white)
        d.centerText("支付有礼", cx: tabSecondCx, baseline: tabTextBase, size: tabTextSize,
                     s.tab == 1 ? pillText : UIColor.white)

        // 左上返回箭头（两段线）+ 右上三个点
        line(d, 0, x1: 104, y1: 197, x2: 63, y2: 225, color: UIColor.white, width: 7)
        line(d, 0, x1: 63, y1: 225, x2: 104, y2: 253, color: UIColor.white, width: 7)
        for i in 0..<3 {
            d.circle(cx: 1172 + CGFloat(i) * 20, cy: 225, r: 5, UIColor.white)
        }
    }

    // MARK: - 图元

    private func drawArt(_ d: Draw, _ key: String, _ box: CGRect) {
        guard let image = art[key] else { return }
        d.art(image, box)
    }

    private func line(_ d: Draw, _ unused: Int, x1: CGFloat, y1: CGFloat, x2: CGFloat, y2: CGFloat,
                      color: UIColor, width: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x1, y: y1))
        d.ctx.addLine(to: CGPoint(x: x2, y: y2))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 右箭头：两段线（Java:431-434）
    private func chevron(_ d: Draw, x: CGFloat, y: CGFloat, color: UIColor,
                         halfH: CGFloat, width: CGFloat) {
        let dx = halfH * 0.45
        line(d, 0, x1: x - dx, y1: y - halfH, x2: x + dx, y2: y, color: color, width: width)
        line(d, 0, x1: x + dx, y1: y, x2: x - dx, y2: y + halfH, color: color, width: width)
    }

    /// 确定性条纹（Java:339-356）：与 Android 同一个 LCG、同一组取位、同一个单元宽度
    private func ensureBarcode() -> [CGRect] {
        if let cached = barcodeRects { return cached }
        var rects: [CGRect] = []
        var state = barcodeSeed ^ 0x2545f4914f6cdd1d
        let unit: CGFloat = 5.4
        var x = barcodeL
        while true {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            let bar = 1 + CGFloat((state >> 40) % 3)
            state = state &* 6364136223846793005 &+ 1442695040888963407
            let gap = 3 + CGFloat((state >> 40) % 4)
            let right = x + bar * unit
            if right > barcodeR - 2 { break }
            rects.append(CGRect(x: x, y: barcodeT, width: right - x, height: barcodeB - barcodeT))
            x = right + gap * unit
        }
        barcodeRects = rects
        return rects
    }

    // MARK: - 命中（Java:361-381）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if y < 100 { return "noop" }
        if y < 320 {
            if x < 150 { return "back" }
            if x > 1100 { return "more" }
            if x > capsuleL && x < capsuleR {
                return x < (capsuleL + capsuleR) / 2 ? "tab:0" : "tab:1"
            }
            return "noop"
        }
        if s.tab == 0 {
            if x > 150 && x < 600 && y > 900 && y < 1000 { return "numbers" }
            if y > 1780 && y < 1920 && x > 46 && x < cardR { return "account" }
            if y > bannerT && y < cardB { return "offer" }
        }
        if y > panelT && y < panelB {
            let row = Int(((y - rowFirstCy) / rowStep).rounded())
            if row >= 0 && row < Self.rowLabels.count { return "row:" + Self.rowLabels[row] }
        }
        return "noop"
    }
}
