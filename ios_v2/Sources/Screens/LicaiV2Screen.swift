import UIKit

/// 理财页 —— `features/zhi/ui/LicaiV2Screen.java` 的 1:1 移植（1280×2816，NAV_TOP 2560，内容高 4440）。
///
/// 两个特殊之处：
/// 1. **顶部蓝底不是渐变，是二次多项式色彩场**：三个通道各 6 个系数，只采 8×8 个点生成小位图再拉伸铺满。
/// 2. **可展开面板**：`expand` 时整页多出 PANEL_H(601)，顶部裁剪区变高、下方整体下移。
final class LicaiV2Screen: Screen {
    var zhiTabIndex: Int? { 1 }
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let navTop: CGFloat = 2560
    static let contentH: CGFloat = 4440
    static let headerBottom: CGFloat = 546
    static let panelH: CGFloat = 601

    final class State: ScreenState {
        var total = "0.00"
        var yesterday = "0.00"
        var family = "2"
        var hidden = false
        var expand: CGFloat = 0
        var index = ["3808.33", "12495.44", "3002.50"]
        var indexPct = ["−0.14%", "−1.16%", "−1.34%"]
        var fundName = "汇安鼎利纯债债券C"
        var fundPct = "+5.07%"
        var stockName = "深高速"
        var stockPct = "+4.32%"
        var etfName = ["通信ET...ETF", "科创50...ETF", "煤炭ET...ETF"]
        var etfPct = ["−3.38%", "−1.30%", "−1.39%"]
        var windName = "创新药"
        var windPct = "−1.26%"
        var windLine1 = "创新药政策利好，叠加AI技"
        var windLine2 = "术加持催化。"
        var prodName = "年年享｜招商信用添利"
        var prodPct = "+6.56%"
    }

    // MARK: - 调色板（Java:149-166）
    private let pageBg = Draw.color(0xfff2f3f7)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff23252b)
    private let muted = Draw.color(0xff939292)
    private let blue = Draw.color(0xff1478fc)
    private let deepBlue = Draw.color(0xff2f6df0)
    private let green = Draw.color(0xff0b906a)
    private let red = Draw.color(0xfff0241c)
    private let tagBg = Draw.color(0xffe4f0fc)
    private let squeeze: CGFloat = 0.93

    // MARK: - 几何（Java:169-226）
    private let caretY: CGFloat = 515
    private let fuliTop: CGFloat = 546
    private let fuliBottom: CGFloat = 728
    private let gridTop: CGFloat = 680
    private let gridBottom: CGFloat = 1184
    private let goldCard = CGRect(x: 42, y: 1210, width: 1237 - 42, height: 1708 - 1210)
    private let carouselTop: CGFloat = 1742
    private let carouselBottom: CGFloat = 2203
    private let carouselCardW: CGFloat = 585
    private let carouselY: CGFloat = 1742
    private let board = CGRect(x: 28, y: 2235, width: 1252 - 28, height: 3157 - 2235)
    private let indexCard = CGRect(x: 71, y: 2270, width: 1207 - 71, height: 2445 - 2270)
    private let etfCard = CGRect(x: 71, y: 2478, width: 523 - 71, height: 3155 - 2478)
    private let windCard = CGRect(x: 554, y: 2478, width: 1207 - 554, height: 3155 - 2478)
    private let tabTop: CGFloat = 3180
    private let tabBottom: CGFloat = 3332
    private let steadyCard = CGRect(x: 42, y: 3374, width: 1237 - 42, height: 4180 - 3374)

    private let row1Box: [[CGFloat]] = [
        [90, 728, 192, 837], [340, 732, 441, 834], [581, 733, 695, 834],
        [836, 732, 942, 834], [1088, 732, 1187, 834],
    ]
    private let row2Box: [[CGFloat]] = [
        [89, 964, 196, 1080], [331, 965, 451, 1080], [572, 967, 707, 1076],
        [776, 950, 945, 1080], [1077, 971, 1201, 1078],
    ]
    private let row1Art = ["icYuebao", "icWenjian", "icGold", "icFund", "icStock"]
    private let row2Art = ["icDingqi", "icJinjie", "icBaoxian", "icBangni", "icYanglao"]
    private let row1Text = ["余额宝", "稳健理财", "黄金", "基金", "股票"]
    private let row2Text = ["定期", "进阶理财", "保险", "帮你投", "养老金"]
    private let row1Cx: [CGFloat] = [141, 390, 639, 888, 1137]
    private let row2Cx: [CGFloat] = [143, 391, 639, 888, 1137]
    private let navX: [CGFloat] = [128, 382, 639, 898, 1151]
    private let pills = ["热销产品", "月月享", "季季享", "年年享"]
    private let pillL: [CGFloat] = [80, 372, 659, 946]
    private let pillR: CGFloat = 1201
    private let pillTop: CGFloat = 3554
    private let pillBottom: CGFloat = 3669

    /// 三通道的二次系数（Java:241-245）：`v = c0 + c1x + c2y + c3x² + c4xy + c5y²`
    private let headerBlue: [[CGFloat]] = [
        [34.54431, -0.0314062, 0.0169517, 0.00000517, -0.00000712, 0.00000510],
        [141.45530, -0.0532967, 0.0250387, 0.00001342, -0.00001059, 0.00000673],
        [254.33077, 0.0003059, 0.0014226, 0.000000172, -0.000000917, -0.000001554],
    ]

    private var art: [String: UIImage] = [:]
    private var plate: UIImage?

    init() {
        let names = ["mascot", "searchbar", "search", "gear", "eye", "shield", "chawen",
                     "icYuebao", "icWenjian", "icGold", "icFund", "icStock",
                     "icDingqi", "icJinjie", "icBaoxian", "icBangni", "icYanglao",
                     "goldChart", "cardFund", "cardStock", "cardEtf", "cardWind",
                     "tabbar", "navbar", "navHome", "steadyMark", "fuli",
                     "navZhi", "navLicai", "navVideo", "navMsg", "navMe"]
        let mapping: [String: String] = [
            "mascot": "lci_mascot", "searchbar": "lci_searchbar", "search": "lci_search",
            "gear": "lci_gear", "eye": "lci_eye", "shield": "lci_shield",
            "chawen": "lci_badge_chatiao", "icYuebao": "lci_ic_yuebao",
            "icWenjian": "lci_ic_wenjian", "icGold": "lci_ic_gold",
            "icFund": "lci_ic_fund", "icStock": "lci_ic_stock",
            "icDingqi": "lci_ic_dingqi", "icJinjie": "lci_ic_jinjie",
            "icBaoxian": "lci_ic_baoxian", "icBangni": "lci_ic_bangni",
            "icYanglao": "lci_ic_yanglao", "goldChart": "lci_gold_chart",
            "cardFund": "lci_card_fund", "cardStock": "lci_card_stock",
            "cardEtf": "lci_card_etf", "cardWind": "lci_card_wind",
            "tabbar": "lci_tabbar", "navbar": "zhi_navbar", "navHome": "lci_nav_home",
            "steadyMark": "lci_steady_mark", "fuli": "lci_fuli",
            "navZhi": "lci_nav_zhi", "navLicai": "lci_nav_licai",
            "navVideo": "lci_nav_video", "navMsg": "lci_nav_msg", "navMe": "lci_nav_me",
        ]
        for name in names {
            if let res = mapping[name], let image = UIImage(named: res) { art[name] = image }
        }
        plate = Self.buildPlate(headerBlue)
    }

    /// 采 8×8 个点生成顶部色彩场（Java:289-310）
    private static func buildPlate(_ coeffs: [[CGFloat]]) -> UIImage? {
        let n = 8
        let full = LicaiV2Screen.headerBottom + LicaiV2Screen.panelH
        var pixels = [UInt8](repeating: 255, count: n * n * 4)
        for j in 0..<n {
            for i in 0..<n {
                let x = LicaiV2Screen.W * CGFloat(i) / CGFloat(n - 1)
                let y = full * CGFloat(j) / CGFloat(n - 1)
                func shade(_ c: [CGFloat]) -> CGFloat {
                    let v = c[0] + c[1] * x + c[2] * y + c[3] * x * x + c[4] * x * y + c[5] * y * y
                    return max(0, min(255, v.rounded()))
                }
                let idx = (j * n + i) * 4
                pixels[idx] = UInt8(shade(coeffs[0]))
                pixels[idx + 1] = UInt8(shade(coeffs[1]))
                pixels[idx + 2] = UInt8(shade(coeffs[2]))
                pixels[idx + 3] = 255
            }
        }
        guard let provider = CGDataProvider(data: Data(pixels) as CFData),
              let cg = CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32,
                               bytesPerRow: n * 4, space: CGColorSpaceCreateDeviceRGB(),
                               bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                               provider: provider, decode: nil, shouldInterpolate: true,
                               intent: .defaultIntent) else { return nil }
        return UIImage(cgImage: cg)
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return max(0, Self.contentH + Self.panelH * s.expand - Self.H)
    }

    // MARK: - 绘制（Java:319-346）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let off = Self.panelH * s.expand
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: 0, width: Self.W, height: Self.headerBottom + off))
        // V1/V2/V3/成长版各有自己的顶部背景切图；普通版才用渐变 plate。
        let theme = LicaiVersion.theme()
        if !theme.backgroundRes.isEmpty, let bg = UIImage(named: theme.backgroundRes) {
            d.art(bg, CGRect(x: 0, y: 0, width: Self.W, height: Self.headerBottom))
        } else if let plate {
            d.art(plate, CGRect(x: 0, y: 0, width: Self.W,
                                height: Self.headerBottom + Self.panelH))
        }
        drawHeader(d, s, off)
        drawPanel(d, s)
        d.ctx.restoreGState()

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: off)
        drawGridCard(d, bar: true)
        drawGoldCard(d)
        drawCarousel(d, s)
        drawBoard(d, s)
        drawTabs(d)
        drawSteadySection(d, s)
        d.ctx.restoreGState()

        d.ctx.restoreGState()

        if s.scroll > Self.headerBottom - 150 {
            d.rect(CGRect(x: 0, y: 0, width: Self.W, height: 150), pageBg)
        }
        drawNav(d)
    }

    /// 顶部（Java:375-407）
    private func drawHeader(_ d: Draw, _ s: State, _ off: CGFloat) {
        d.art(art["mascot"], CGRect(x: 35, y: 158, width: 173 - 35, height: 296 - 158))
        d.art(art["searchbar"], CGRect(x: 140, y: 150, width: 1000 - 140, height: 292 - 150))
        d.art(art["search"], CGRect(x: 998, y: 156, width: 1116 - 998, height: 268 - 156))
        d.art(art["gear"], CGRect(x: 1142, y: 156, width: 1260 - 1142, height: 268 - 156))

        d.text("总资产(元)", x: 45, baseline: 388, size: 45, Draw.color(0xffd6e6ff))
        d.text(s.hidden ? "****" : s.total, x: 45, baseline: 498, size: 100, .white)
        d.art(art["eye"], CGRect(x: 286, y: 338, width: 374 - 286, height: 412 - 338))
        d.round(CGRect(x: 410, y: 342, width: 616 - 410, height: 406 - 342), radius: 32,
                Draw.color(0xff1a6dd7))
        d.art(art["shield"], CGRect(x: 428, y: 344, width: 478 - 428, height: 404 - 344))
        d.text("保障中", x: 488, baseline: 390, size: 42, .white)

        d.text("昨日收益", x: 814, baseline: 387, size: 45, Draw.color(0xffd6e6ff))
        d.rightText(s.hidden ? "****" : s.yesterday, right: 990, baseline: 498, size: 100, .white)
        d.rect(CGRect(x: 1017, y: 340, width: 2.5, height: 500 - 340),
               UIColor.white.withAlphaComponent(0x55 / 255.0))
        d.text("家庭保障", x: 1069, baseline: 386, size: 45, Draw.color(0xffd6e6ff))
        d.text(s.family, x: 1104, baseline: 489, size: 100, .white)
        let familyWidth = Draw.width(s.family, size: 100)
        d.text("份", x: 1104 + familyWidth + 6, baseline: 486, size: 40, .white)

        // 折叠箭头：展开时旋转 180°（Java:400-405）
        let cy = caretY + off
        d.ctx.saveGState()
        d.ctx.translateBy(x: 639, y: cy)
        d.ctx.rotate(by: .pi * s.expand)
        d.ctx.setStrokeColor(Draw.color(0xffbcd6ff).cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: -15, y: -7.5))
        d.ctx.addLine(to: CGPoint(x: 0, y: 7.5))
        d.ctx.addLine(to: CGPoint(x: 15, y: -7.5))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 保障资产 / 额度面板（Java:414-423）
    private func drawPanel(_ d: Draw, _ s: State) {
        d.text("我的保障资产", x: 45, baseline: 638, size: 44, Draw.color(0xffd6e6ff))
        d.text("保险", x: 44, baseline: 776, size: 44, .white)
        d.text("2份免费保单保障中", x: 158, baseline: 776, size: 44, Draw.color(0xffd6e6ff))
        d.text("我的额度", x: 45, baseline: 902, size: 44, Draw.color(0xffd6e6ff))
        d.text("花呗", x: 44, baseline: 1041, size: 44, .white)
        d.text("可用2,710.98", x: 158, baseline: 1041, size: 44, Draw.color(0xffd6e6ff))
        d.text("信用卡", x: 676, baseline: 1041, size: 44, .white)
        d.text("2000免费额度起", x: 833, baseline: 1041, size: 44, Draw.color(0xffd6e6ff))
    }

    /// 宫格卡（Java:425-444）
    private func drawGridCard(_ d: Draw, bar: Bool) {
        let box = CGRect(x: 0, y: gridTop, width: Self.W, height: gridBottom - gridTop)
        d.clipped(to: box, radius: 58) {
            d.gradient(box, from: .white, to: pageBg, vertical: true)
        }
        if bar { d.art(art["fuli"], CGRect(x: 0, y: fuliTop, width: Self.W, height: fuliBottom - fuliTop)) }

        for i in 0..<5 {
            let b1 = row1Box[i]
            d.art(art[row1Art[i]], CGRect(x: b1[0], y: b1[1], width: b1[2] - b1[0], height: b1[3] - b1[1]))
            d.centerTextSqueezed(row1Text[i], cx: row1Cx[i], baseline: 902, size: 43, ink, squeeze: squeeze)
            let b2 = row2Box[i]
            d.art(art[row2Art[i]], CGRect(x: b2[0], y: b2[1], width: b2[2] - b2[0], height: b2[3] - b2[1]))
            d.centerTextSqueezed(row2Text[i], cx: row2Cx[i], baseline: 1131, size: 43, ink, squeeze: squeeze)
        }
        d.art(art["chawen"], CGRect(x: 648, y: 923, width: 774 - 648, height: 969 - 923))
        d.round(CGRect(x: 604, y: 1168, width: 48, height: 14), radius: 7, Draw.color(0xff2a76f7))
        d.circle(cx: 668, cy: 1175, r: 6.5, Draw.color(0xffdedde1))
    }

    /// 黄金账户卡（Java:446-489）
    private func drawGoldCard(_ d: Draw) {
        d.round(goldCard, radius: 28, cardColor)
        d.round(CGRect(x: 78, y: 1249, width: 228 - 78, height: 1305 - 1249), radius: 10,
                Draw.color(0xffe42838))
        d.centerText("账户服务", cx: 153, baseline: 1291, size: 40, .white)
        d.text("你的黄金账户停留在开户很久了", x: 240, baseline: 1303, size: 53,
               Draw.color(0xff22242a), bold: true)

        let bubble = CGRect(x: 105, y: 1378, width: 430 - 105, height: 1445 - 1378)
        d.round(bubble, radius: 14, .clear)
        d.clipped(to: bubble, radius: 14) {
            d.gradient(bubble, from: Draw.color(0xff3f9cff), to: Draw.color(0xff0f6ff5),
                       vertical: false)
        }
        d.path([CGPoint(x: 180, y: 1443), CGPoint(x: 197, y: 1462), CGPoint(x: 205, y: 1443)],
               fill: Draw.color(0xff0f6ff5))
        d.centerText("查看今日国内实时金价", cx: 267, baseline: 1432, size: 34, .white)

        d.art(art["goldChart"], CGRect(x: 698, y: 1316, width: 1202 - 698, height: 1502 - 1316))

        d.circle(cx: 193, cy: 1476, r: 29, blue)
        d.ctx.saveGState()
        d.ctx.setStrokeColor(UIColor.white.cgColor)
        d.ctx.setLineWidth(7)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: 179, y: 1476))
        d.ctx.addLine(to: CGPoint(x: 189, y: 1486))
        d.ctx.addLine(to: CGPoint(x: 208, y: 1466))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        d.rect(CGRect(x: 222, y: 1469.5, width: 503 - 222, height: 13), blue)
        d.rect(CGRect(x: 503, y: 1469.5, width: 762 - 503, height: 13), Draw.color(0xffd8e5f5))
        d.circle(cx: 503, cy: 1476, r: 15, blue)
        d.round(CGRect(x: 757, y: 1467, width: 22, height: 18), radius: 6, Draw.color(0xffd8e5f5))

        d.centerText("进入开户", cx: 193, baseline: 1536, size: 36, deepBlue)
        d.centerText("身份校验", cx: 467, baseline: 1536, size: 36, deepBlue)
        d.centerText("开户成功", cx: 770, baseline: 1536, size: 36, deepBlue)

        d.text("开通黄金实时交易账户", x: 85, baseline: 1643, size: 43, Draw.color(0xff2f3138))
        d.round(CGRect(x: 985, y: 1580, width: 215, height: 88), radius: 44, blue)
        d.centerText("去开户", cx: 1092, baseline: 1649, size: 48, .white, bold: true)
    }

    /// 自选卡轮播（Java:491-505）
    private func drawCarousel(_ d: Draw, _ s: State) {
        d.round(CGRect(x: -550, y: carouselTop, width: 30 - (-550), height: carouselBottom - carouselTop),
                radius: 27, cardColor)
        // Java 的 drawArt 参数是 (left, top, right, bottom)；这里对应 CGRect 的
        // width/height 必须传「宽、高」，不能再传 right/bottom。
        // 之前传成 57+585 / 1742+461，图片被放大到 642×2203 并且只露出左上角，
        // 真机表现就是「基金自选」跑到卡片中间、走势图和加自选按钮全不见。
        d.art(art["cardFund"], CGRect(x: 57, y: carouselY, width: carouselCardW,
                                      height: 461))
        d.art(art["cardStock"], CGRect(x: 669, y: carouselY, width: carouselCardW,
                                       height: 461))

        d.text(s.fundName, x: 100, baseline: 1851, size: 50, ink, bold: true)
        d.text(s.fundPct, x: 99, baseline: 1934, size: 56, red, bold: true)
        d.text("近一年净值增长", x: 100, baseline: 2003, size: 43, muted)

        let nx = 705 + Draw.width(s.stockName, size: 50, bold: true)
        d.text(s.stockName, x: 705, baseline: 1851, size: 50, ink, bold: true)
        d.text(s.stockPct, x: nx + 16, baseline: 1851, size: 47, red, bold: true)
        d.text(s.stockPct, x: 704, baseline: 1929, size: 56, red, bold: true)
        d.text("最大涨幅", x: 705, baseline: 2001, size: 43, muted)
    }

    /// 行情面板（Java:507-534）
    private func drawBoard(_ d: Draw, _ s: State) {
        d.clipped(to: board, radius: 30) {
            d.gradient(board, from: Draw.color(0xffdceefe), to: Draw.color(0xfffbfbfb),
                       vertical: true)
        }
        d.round(indexCard, radius: 26, cardColor)
        for i in 0..<3 {
            let lx = 114 + CGFloat(i) * 385.5
            d.text(Self.indexLabel(i), x: lx, baseline: 2344, size: 46, Draw.color(0xff2a2b2d))
            let end = lx + Draw.width(Self.indexLabel(i), size: 46)
            chevron(d, x: end + 16, cy: 2324, color: Draw.color(0xffb6bac0))
            d.text(s.index[i], x: lx, baseline: 2415, size: 52, green, bold: true)
            let vx = lx + Draw.width(s.index[i], size: 52, bold: true)
            d.text(s.indexPct[i], x: vx + 12, baseline: 2415, size: 36, green, bold: true)
        }

        d.art(art["cardEtf"], etfCard)
        d.art(art["cardWind"], windCard)
        for i in 0..<3 {
            let top = 2608 + CGFloat(i) * 173.5
            d.text(s.etfName[i], x: 224, baseline: top + 52, size: 38, Draw.color(0xff2c2c2a))
            d.text(s.etfPct[i], x: 224, baseline: top + 115, size: 48, green, bold: true)
        }
        d.text(s.windName, x: 624, baseline: 2706, size: 64, ink, bold: true)
        d.text(s.windPct, x: 624, baseline: 2794, size: 62, green, bold: true)
        d.text(s.windLine1, x: 628, baseline: 2988, size: 43, Draw.color(0xff40424a))
        d.text(s.windLine2, x: 628, baseline: 3038, size: 43, Draw.color(0xff40424a))
    }

    private static func indexLabel(_ i: Int) -> String {
        i == 0 ? "上证指数" : (i == 1 ? "深证成指" : "创业板指")
    }

    /// 页签条（Java:540-547）
    private func drawTabs(_ d: Draw) {
        let band = CGRect(x: 0, y: tabBottom, width: Self.W, height: 3540 - tabBottom)
        d.gradient(band, from: Draw.color(0xfffcfcfc), to: pageBg, vertical: true)
        d.art(art["tabbar"], CGRect(x: 0, y: tabTop, width: Self.W, height: tabBottom - tabTop))
    }

    /// 安稳理财（Java:549-588）
    private func drawSteadySection(_ d: Draw, _ s: State) {
        d.clipped(to: steadyCard, radius: 28) {
            d.gradient(steadyCard, from: Draw.color(0xffe7ecfd), to: .white, vertical: true)
        }
        d.art(art["steadyMark"], CGRect(x: 540, y: 3374, width: 1242 - 540, height: 3512 - 3374))
        d.text("安稳理财", x: 99, baseline: 3488, size: 59, ink, bold: true)
        d.text("主投债券·波动小·收益稳", x: 388, baseline: 3489, size: 50, Draw.color(0xff5e5f63))
        chevron(d, x: 1187, cy: 3465, color: Draw.color(0xff8a8f99))

        for (i, pill) in pills.enumerated() {
            let left = pillL[i]
            let right: CGFloat = i == 0 ? 339 : left + 255
            let box = CGRect(x: left, y: pillTop, width: right - left, height: pillBottom - pillTop)
            d.round(box, radius: 57, cardColor)
            if i == 0 { d.roundStroke(box, radius: 57, blue, width: 2.5) }
            d.centerTextSqueezed(pill, cx: (left + right) / 2, baseline: 3636,
                                 size: i == 0 ? 49 : 48,
                                 i == 0 ? blue : Draw.color(0xff919294),
                                 bold: i == 0, squeeze: squeeze)
        }

        d.text(s.prodName, x: 100, baseline: 3796, size: 50, ink, bold: true)
        let plusX: CGFloat = 585
        d.roundStroke(CGRect(x: plusX, y: 3750, width: 52, height: 50), radius: 8,
                      Draw.color(0xffc3c9d4), width: 2.5)
        d.rect(CGRect(x: plusX + 15, y: 3773, width: 22, height: 4), Draw.color(0xff8d949f))
        d.rect(CGRect(x: plusX + 24, y: 3764, width: 4, height: 22), Draw.color(0xff8d949f))
        tag(d, left: 645, top: 3750, "超10万人关注", size: 32)
        tag(d, left: 890, top: 3750, "银行系", size: 32)

        d.text(s.prodPct, x: 98, baseline: 3932, size: 62, red, bold: true)
        d.text("近一年净值增长", x: 99, baseline: 3980, size: 43, muted)
        d.text("灵活申赎", x: 583, baseline: 3939, size: 61, ink, bold: true)
        d.text("10元起购 ｜ 中低风险 ｜ 债券基金", x: 583, baseline: 3983, size: 42, muted)
    }

    private func tag(_ d: Draw, left: CGFloat, top: CGFloat, _ label: String, size: CGFloat) {
        let width = Draw.width(label, size: size) + 36
        d.round(CGRect(x: left, y: top, width: width, height: 44), radius: 8, tagBg)
        d.centerText(label, cx: left + width / 2, baseline: top + 33, size: size,
                     Draw.color(0xff1677ff))
    }

    /// 底部导航（Java:597-610）
    private func drawNav(_ d: Draw) {
        d.art(art["navbar"], CGRect(x: 0, y: Self.navTop, width: Self.W, height: Self.H - Self.navTop))
        d.art(art["navHome"], CGRect(x: 20, y: 2560, width: 245 - 20, height: 2816 - 2560))
        d.rect(CGRect(x: 340, y: 2584, width: 426 - 340, height: 2732 - 2584),
               Draw.color(0xfffafbff))
        // 选中态必须染成主题蓝；直接画 lci_nav_licai 是未选中的深灰。
        let navLicai = art["navLicai"]?.withTintColor(Draw.color(0xff1677ff), renderingMode: .alwaysOriginal)
            ?? art["navLicai"]
        d.art(navLicai, CGRect(x: 340, y: 2584, width: 426 - 340, height: 2732 - 2584))
    }

    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: x, y: cy - 14))
        d.ctx.addLine(to: CGPoint(x: x + 18, y: cy))
        d.ctx.addLine(to: CGPoint(x: x, y: cy + 14))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    // MARK: - 命中（Java:615-708）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 底部导航是固定层（draw 里在 restore 之后画），用屏幕坐标；
        // 其余内容用宿主传进来的内容坐标（已含 scroll）。
        if y - s.scroll >= Self.navTop { return "nav:\(nearest(navX, x))" }
        var cy = y

        let caret = caretY + Self.panelH * s.expand
        if x > 569 && x < 709 && cy > caret - 40 && cy < caret + 45 { return "toggleExpand" }

        if cy < 300 { return "version" }
        if cy < 545 {
            if x >= 280 && x <= 380 { return "toggleHide" }
            if cy > 400 {
                if x < 300 { return "edit:total" }
                if x > 760 && x < 1000 { return "edit:yesterday" }
                if x > 1040 { return "edit:family" }
            }
            return "version"
        }
        if cy < 546 + Self.panelH * s.expand { return "version" }

        cy -= Self.panelH * s.expand
        if cy < gridBottom {
            let col = column(x)
            return "noop"
        }
        if cy < goldCard.maxY { return "noop" }
        if cy >= carouselTop && cy <= carouselBottom {
            let right = x > 660
            if cy >= 1795 && cy <= 1865 { return "edit:" + (right ? "stockName" : "fundName") }
            if cy >= 1870 && cy <= 1945 { return "edit:" + (right ? "stockPct" : "fundPct") }
            return "noop"
        }
        if cy >= indexCard.minY && cy <= indexCard.maxY {
            let col = x < 590 ? 0 : (x < 1000 ? 1 : 2)
            return cy > 2360 ? "edit:idxp\(col)" : "edit:idx\(col)"
        }
        if cy >= etfCard.minY && cy <= etfCard.maxY {
            let row = max(0, min(2, Int((cy - 2600) / 173.5)))
            return cy > 2672 + CGFloat(row) * 173.5 ? "edit:etfp\(row)" : "edit:etf\(row)"
        }
        if cy >= windCard.minY && cy <= windCard.maxY {
            if cy >= 2940 { return "edit:windLine1" }
            if cy >= 2630 && cy < 2720 { return "edit:windName" }
            if cy >= 2720 && cy <= 2810 { return "edit:windPct" }
            return "noop"
        }
        if cy > steadyCard.minY {
            if cy >= 3740 && cy <= 3830 && x < 578 { return "edit:prodName" }
            return "noop"
        }
        return "noop"
    }

    private func column(_ x: CGFloat) -> Int {
        if x < 265 { return 0 }
        if x < 515 { return 1 }
        if x < 762 { return 2 }
        if x < 1009 { return 3 }
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

// MARK: - 可编辑（点数字弹输入框）

extension LicaiV2Screen.State: EditableState {
    /// 键名与 Java 的 `State.KEYS` 一致（顺序也一致）
    private static let editKeys = [
        "total", "yesterday", "family",
        "idx0", "idx1", "idx2", "idxp0", "idxp1", "idxp2",
        "fundName", "fundPct", "stockName", "stockPct",
        "etf0", "etf1", "etf2", "etfp0", "etfp1", "etfp2",
        "windName", "windPct", "windLine1", "windLine2",
        "prodName", "prodPct",
    ]

    /// 与 Java 的 `State.TITLES` 逐条对应
    private static let editTitles = [
        "编辑总资产", "编辑昨日收益", "编辑家庭保障份数",
        "编辑上证指数", "编辑深证成指", "编辑创业板指",
        "编辑上证涨跌幅", "编辑深证涨跌幅", "编辑创业板涨跌幅",
        "编辑基金名称", "编辑基金涨幅", "编辑股票名称", "编辑股票涨幅",
        "编辑ETF第1名", "编辑ETF第2名", "编辑ETF第3名",
        "编辑ETF第1涨幅", "编辑ETF第2涨幅", "编辑ETF第3涨幅",
        "编辑风口名称", "编辑风口涨幅", "编辑风口说明第一行", "编辑风口说明第二行",
        "编辑产品名称", "编辑产品涨幅",
    ]

    func editTitle(_ key: String) -> String {
        if let index = Self.editKeys.firstIndex(of: key), index < Self.editTitles.count {
            return Self.editTitles[index]
        }
        return "编辑" + key
    }

    func editValue(_ key: String) -> String {
        let value: String?
        switch key {
        case "total": value = total
        case "yesterday": value = yesterday
        case "family": value = family
        case "idx0": value = index[0]
        case "idx1": value = index[1]
        case "idx2": value = index[2]
        case "idxp0": value = indexPct[0]
        case "idxp1": value = indexPct[1]
        case "idxp2": value = indexPct[2]
        case "fundName": value = fundName
        case "fundPct": value = fundPct
        case "stockName": value = stockName
        case "stockPct": value = stockPct
        case "etf0": value = etfName[0]
        case "etf1": value = etfName[1]
        case "etf2": value = etfName[2]
        case "etfp0": value = etfPct[0]
        case "etfp1": value = etfPct[1]
        case "etfp2": value = etfPct[2]
        case "windName": value = windName
        case "windPct": value = windPct
        case "windLine1": value = windLine1
        case "windLine2": value = windLine2
        case "prodName": value = prodName
        case "prodPct": value = prodPct
        default: value = nil
        }
        return value ?? GrowthStore.shared.value(key)
    }

    func applyEdit(_ key: String, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch key {
        case "total": if !trimmed.isEmpty { self.total = trimmed }
        case "yesterday": if !trimmed.isEmpty { self.yesterday = trimmed }
        case "family": self.family = trimmed.isEmpty ? "2" : trimmed
        case "idx0": if !trimmed.isEmpty { self.index[0] = trimmed }
        case "idx1": if !trimmed.isEmpty { self.index[1] = trimmed }
        case "idx2": if !trimmed.isEmpty { self.index[2] = trimmed }
        case "idxp0": if !trimmed.isEmpty { self.indexPct[0] = trimmed }
        case "idxp1": if !trimmed.isEmpty { self.indexPct[1] = trimmed }
        case "idxp2": if !trimmed.isEmpty { self.indexPct[2] = trimmed }
        case "fundName": if !trimmed.isEmpty { self.fundName = trimmed }
        case "fundPct": if !trimmed.isEmpty { self.fundPct = trimmed }
        case "stockName": if !trimmed.isEmpty { self.stockName = trimmed }
        case "stockPct": if !trimmed.isEmpty { self.stockPct = trimmed }
        case "etf0": if !trimmed.isEmpty { self.etfName[0] = trimmed }
        case "etf1": if !trimmed.isEmpty { self.etfName[1] = trimmed }
        case "etf2": if !trimmed.isEmpty { self.etfName[2] = trimmed }
        case "etfp0": if !trimmed.isEmpty { self.etfPct[0] = trimmed }
        case "etfp1": if !trimmed.isEmpty { self.etfPct[1] = trimmed }
        case "etfp2": if !trimmed.isEmpty { self.etfPct[2] = trimmed }
        case "windName": if !trimmed.isEmpty { self.windName = trimmed }
        case "windPct": if !trimmed.isEmpty { self.windPct = trimmed }
        case "windLine1": if !trimmed.isEmpty { self.windLine1 = trimmed }
        case "windLine2": if !trimmed.isEmpty { self.windLine2 = trimmed }
        case "prodName": if !trimmed.isEmpty { self.prodName = trimmed }
        case "prodPct": if !trimmed.isEmpty { self.prodPct = trimmed }
        default: break
        }
    }
}
