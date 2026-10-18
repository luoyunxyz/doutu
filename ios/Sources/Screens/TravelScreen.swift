import UIKit

/// 「出行」页 —— `features/travel/ui/TravelScreen.java` 的 1:1 移植（参考画布 1280×2816，单屏不滚动）。
///
/// 结构：顶部淡蓝渐变（到 700）→ 头栏（返回 / 地址 / 天气 / 搜索胶囊 / 里程）
/// → 五个入口（第二个「机票」是选中态：深蓝底 + 蓝字，且**不画底片**——它本来就是深蓝块）
/// → 机票卡（页签条 + 比价 + 城市与交换 + 日期 + 舱位与两个勾选 + 优惠券条 + 查询按钮 + 四个底部入口）
/// → 服务卡（两行五个格子）→ 出去玩 → 底部导航。
///
/// 这一屏有 **39 张切图**，每张都登记了它在参考图上的原始位置（Java 的 `box()` 表），
/// 所以不需要逐个算尺寸 —— 直接按表贴回即可。
final class TravelScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    final class State: ScreenState {
        // 与 Android `TravelScreen.State`（Java:592-594）逐字一致
        var address = "恩施土家族苗族自治州"
        var from = "北京"
        var to = "杭州"
        /// 0 单程 / 1 往返 / 2 值机选座 / 3 快速安检
        var tab = 0
        var checkChild = false
        var checkBaby = false
    }

    /// 文字统一 0.94 横向压缩（Java:45）
    private let squeeze: CGFloat = 0.94

    // MARK: - 调色板（Java:48-55、82-83、130-141、160、178-179）
    private let washTop = Draw.color(0xffc3e2fe)
    private let washBottom = Draw.color(0xffe9f5ff)
    private let ink = Draw.color(0xff1f2733)
    private let grey = Draw.color(0xff6b7280)
    private let rule = Draw.color(0xffeef0f4)
    private let blue = Draw.color(0xff1677ff)
    private let tile = Draw.color(0xffe8f3fe)
    private let tileSelected = Draw.color(0xff1c86f5)
    private let tileWarm = Draw.color(0xfffcf1e7)
    private let couponFill = Draw.color(0xfffdeeee)
    private let couponRed = Draw.color(0xffe0301e)
    private let tabStripFill = Draw.color(0xffe8f2fe)
    private let navOn = Draw.color(0xff1677ff)
    private let navOff = Draw.color(0xff2b3550)

    // MARK: - 几何（Java:57-181）
    private let washH: CGFloat = 700
    private let addrX: CGFloat = 145
    private let addrBase: CGFloat = 226
    private let addrSize: CGFloat = 52
    private let weatherX: CGFloat = 146
    private let weatherBase: CGFloat = 264
    private let weatherSize: CGFloat = 30
    private let pill = CGRect(x: 402, y: 157, width: 1122 - 402, height: 269 - 157)
    private let pillRadius: CGFloat = 56
    private let hintX: CGFloat = 492
    private let hintBase: CGFloat = 233
    private let hintSize: CGFloat = 44
    private let searchDividerX: CGFloat = 940
    private let searchX: CGFloat = 966
    private let searchSize: CGFloat = 48

    private let entryCx: [CGFloat] = [139, 390, 640, 890, 1141]
    private let entryNames = ["公交地铁", "机票", "加油", "公共充电", "租车"]
    private let entryArt = ["entryBus", "entryPlane", "entryFuel", "entryCharge", "entryCar"]
    private let entryTileCy: CGFloat = 395
    private let entryTile: CGFloat = 152
    private let entryLabelBase: CGFloat = 545
    private let entryLabelSize: CGFloat = 52

    private let card = CGRect(x: 24, y: 598, width: 1256 - 24, height: 1891 - 598)
    private let cardRadius: CGFloat = 28
    private let tabStrip = CGRect(x: 78, y: 636, width: 1205 - 78, height: 716 - 636)
    private let tabPill = CGRect(x: 89, y: 636, width: 354 - 89, height: 716 - 636)
    private let tabCx: [CGFloat] = [216, 498, 780, 1063]
    private let tabNames = ["单程", "往返", "值机选座", "快速安检"]
    private let tabBase: CGFloat = 698
    private let tabSize: CGFloat = 46

    private let compareX: CGFloat = 95
    private let compareBase: CGFloat = 822
    private let compareSize: CGFloat = 46
    private let compareSubX: CGFloat = 341
    private let compareSubSize: CGFloat = 40

    private let citySize: CGFloat = 84
    private let routeBase: CGFloat = 980
    private let cityLeftX: CGFloat = 85
    private let cityRightX: CGFloat = 1060
    private let swapCx: CGFloat = 640
    private let swapCy: CGFloat = 948
    private let swapR: CGFloat = 38
    private let ruleY: CGFloat = 1032
    private let dateX: CGFloat = 89
    private let dateBase: CGFloat = 1158
    private let dateSize: CGFloat = 84
    private let todaySize: CGFloat = 46

    private let cabinX: CGFloat = 84
    private let cabinBase: CGFloat = 1338
    private let cabinSize: CGFloat = 46
    private let checkCx: [CGFloat] = [696, 987]
    private let checkCy: CGFloat = 1321
    private let checkR: CGFloat = 25
    private let checkNames = ["携带儿童", "携带婴儿"]
    private let checkSubs = ["2-12岁", "14天-2岁"]

    private let coupon = CGRect(x: 84, y: 1433, width: 1191 - 84, height: 1524 - 1433)
    private let couponBase: CGFloat = 1502
    private let couponSize: CGFloat = 46
    private let couponLinkX: CGFloat = 1022

    private let query = CGRect(x: 105, y: 1573, width: 1174 - 105, height: 1735 - 1573)
    private let queryLeft = Draw.color(0xff2f9dff)
    private let queryRight = Draw.color(0xff0a63f5)
    private let queryTextSize: CGFloat = 64

    private let footNames = ["我的订单", "乘机服务", "低价提醒", "更多服务"]
    private let footArt = ["footOrder", "footFlight", "footPrice", "footMore"]
    private let footTextX: [CGFloat] = [144, 442, 739, 1038]
    private let footBase: CGFloat = 1837
    private let footSize: CGFloat = 44

    private let card2 = CGRect(x: 24, y: 1950, width: 1256 - 24, height: 2450 - 1950)
    private let gridArt = ["gridTaxi", "grid12306", "gridBike", "gridHotel", "gridCoupon",
                           "gridCarpool", "gridBusticket", "gridTransit", "gridPlan", "gridAll"]
    private let gridNames = ["打车", "12306", "骑行", "酒店预订", "出行优惠",
                             "顺风车", "汽车票", "实时公交", "出行规划", "全部服务"]
    private let gridTileCy: [CGFloat] = [2043, 2294]
    private let gridLabelBase: [CGFloat] = [2185, 2403]
    private let gridLabelSize: CGFloat = 50

    private let play = CGRect(x: 24, y: 2475, width: 1256 - 24, height: 2816 - 2475)
    private let playTitleX: CGFloat = 73
    private let playTitleBase: CGFloat = 2610
    private let playTitleSize: CGFloat = 66
    private let playSubX: CGFloat = 804
    private let playSubSize: CGFloat = 52

    private let navTop: CGFloat = 2560
    private let navCx: [CGFloat] = [128, 384, 640, 897, 1151]
    private let navNames = ["首页", "车生活", "AI出行助手", "出去玩", "我的"]
    private let navArt: [String?] = ["navHome", "navCar", nil, "navPlay", "navMe"]
    private let navLabelBase: CGFloat = 2724
    private let navLabelSize: CGFloat = 44

    /// 39 张切图（Java:194-251），每张都带参考图上的原始位置
    private var art: [String: UIImage] = [:]
    private var boxes: [String: CGRect] = [:]

    init() {
        loadArt()
    }

    private func loadArt() {
        // 名字 → 资源名
        let mapping: [(String, String)] = [
            ("entryBus", "tr_entry_bus"), ("entryPlane", "tr_entry_plane"),
            ("entryFuel", "tr_entry_fuel"), ("entryCharge", "tr_entry_charge"),
            ("entryCar", "tr_entry_car"), ("platforms", "tr_platforms"),
            ("compareMark", "tr_compare_mark"), ("couponBadge", "tr_coupon_badge"),
            ("mileage", "tr_mileage"), ("footOrder", "tr_foot_order"),
            ("footFlight", "tr_foot_flight"), ("footPrice", "tr_foot_price"),
            ("footMore", "tr_foot_more"), ("gridTaxi", "tr_grid_taxi"),
            ("grid12306", "tr_grid_12306"), ("gridBike", "tr_grid_bike"),
            ("gridHotel", "tr_grid_hotel"), ("gridCoupon", "tr_grid_coupon"),
            ("gridCarpool", "tr_grid_carpool"), ("gridBusticket", "tr_grid_busticket"),
            ("gridTransit", "tr_grid_transit"), ("gridPlan", "tr_grid_plan"),
            ("gridAll", "tr_grid_all"), ("navHome", "tr_nav_home"), ("navCar", "tr_nav_car"),
            ("navPlay", "tr_nav_play"), ("navMe", "tr_nav_me"), ("navAvatar", "tr_nav_avatar"),
        ]
        for (key, res) in mapping {
            if let image = UIImage(named: res) { art[key] = image }
        }

        // 每张切图贴回的位置（Java:224-251）
        boxes = [
            "entryBus": CGRect(x: 97, y: 354, width: 183 - 97, height: 437 - 354),
            "entryPlane": CGRect(x: 315, y: 319, width: 467 - 315, height: 470 - 319),
            "entryFuel": CGRect(x: 601, y: 356, width: 689 - 601, height: 436 - 356),
            "entryCharge": CGRect(x: 853, y: 356, width: 940 - 853, height: 436 - 356),
            "entryCar": CGRect(x: 1083, y: 324, width: 1217 - 1083, height: 433 - 324),
            "platforms": CGRect(x: 1029, y: 771, width: 1185 - 1029, height: 845 - 771),
            "compareMark": CGRect(x: 238, y: 784, width: 297 - 238, height: 813 - 784),
            "couponBadge": CGRect(x: 103, y: 1456, width: 161 - 103, height: 1508 - 1456),
            "mileage": CGRect(x: 1170, y: 158, width: 1255 - 1170, height: 272 - 158),
            "footOrder": CGRect(x: 86, y: 1798, width: 127 - 86, height: 1838 - 1798),
            "footFlight": CGRect(x: 382, y: 1796, width: 424 - 382, height: 1840 - 1796),
            "footPrice": CGRect(x: 679, y: 1799, width: 722 - 679, height: 1837 - 1799),
            "footMore": CGRect(x: 979, y: 1796, width: 1019 - 979, height: 1839 - 1796),
            "gridTaxi": CGRect(x: 89, y: 1998, width: 190 - 89, height: 2084 - 1998),
            "grid12306": CGRect(x: 346, y: 1995, width: 432 - 346, height: 2094 - 1995),
            "gridBike": CGRect(x: 589, y: 1997, width: 691 - 589, height: 2084 - 1997),
            "gridHotel": CGRect(x: 846, y: 2003, width: 936 - 846, height: 2093 - 2003),
            "gridCoupon": CGRect(x: 1095, y: 1999, width: 1186 - 1095, height: 2090 - 1999),
            "gridCarpool": CGRect(x: 92, y: 2255, width: 185 - 92, height: 2333 - 2255),
            "gridBusticket": CGRect(x: 343, y: 2253, width: 436 - 343, height: 2337 - 2253),
            "gridTransit": CGRect(x: 596, y: 2252, width: 683 - 596, height: 2337 - 2252),
            "gridPlan": CGRect(x: 846, y: 2257, width: 936 - 846, height: 2334 - 2257),
            "gridAll": CGRect(x: 1109, y: 2263, width: 1173 - 1109, height: 2327 - 2263),
            "navHome": CGRect(x: 91, y: 2591, width: 166 - 91, height: 2663 - 2591),
            "navCar": CGRect(x: 342, y: 2593, width: 427 - 342, height: 2663 - 2593),
            "navPlay": CGRect(x: 852, y: 2591, width: 937 - 852, height: 2662 - 2591),
            "navMe": CGRect(x: 1116, y: 2590, width: 1188 - 1116, height: 2665 - 2590),
            "navAvatar": CGRect(x: 575, y: 2540, width: 708 - 575, height: 2674 - 2540),
        ]
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:269-287）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: washH),
                   from: washTop, to: washBottom, vertical: true)
        d.rect(CGRect(x: 0, y: washH, width: Self.W, height: navTop - washH), washBottom)
        d.rect(CGRect(x: 0, y: 2450, width: Self.W, height: navTop - 2450), Draw.color(0xfff5f7fa))

        drawHeader(d, s)
        drawEntries(d)
        drawFlightCard(d, s)
        drawServiceCard(d)
        drawPlaySection(d)
        drawNav(d)
    }

    /// 头栏（Java:289-320）
    private func drawHeader(_ d: Draw, _ s: State) {
        stroke(d, points: [CGPoint(x: 81, y: 183), CGPoint(x: 47, y: 201), CGPoint(x: 81, y: 219)],
               color: ink, width: 6)
        let address = ellipsize(s.address, size: addrSize, bold: true, maxWidth: 230)
        d.textSqueezed(address, x: addrX, baseline: addrBase, size: addrSize, ink,
                       bold: true, squeeze: squeeze)
        let end = addrX + Draw.width(address, size: addrSize, bold: true)
        stroke(d, points: [CGPoint(x: end + 16, y: addrBase - 34),
                           CGPoint(x: end + 34, y: addrBase - 16),
                           CGPoint(x: end + 52, y: addrBase - 34)],
               color: ink, width: 5)
        d.textSqueezed("阴 21℃", x: weatherX, baseline: weatherBase, size: weatherSize, grey,
                       squeeze: squeeze)

        d.round(pill, radius: pillRadius, .white)
        // 放大镜：圆环 + 手柄
        let greyStroke = Draw.color(0xff9aa3b0)
        d.ctx.saveGState()
        d.ctx.setStrokeColor(greyStroke.cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.strokeEllipse(in: CGRect(x: 452 - 18, y: 208 - 18, width: 36, height: 36))
        d.ctx.move(to: CGPoint(x: 464, y: 222))
        d.ctx.addLine(to: CGPoint(x: 478, y: 236))
        d.ctx.strokePath()
        d.ctx.restoreGState()

        d.textSqueezed("领8元打车券", x: hintX, baseline: hintBase, size: hintSize,
                       Draw.color(0xff3a4353), squeeze: squeeze)
        stroke(d, points: [CGPoint(x: searchDividerX, y: 190), CGPoint(x: searchDividerX, y: 236)],
               color: Draw.color(0xffd9dfe8), width: 3)
        d.textSqueezed("搜索", x: searchX, baseline: hintBase, size: searchSize, ink,
                       squeeze: squeeze)
        drawArt(d, "mileage")
    }

    /// 五个入口（Java:322-334）
    private func drawEntries(_ d: Draw) {
        for (i, name) in entryNames.enumerated() {
            let cx = entryCx[i]
            if i != 1 {
                let box = CGRect(x: cx - entryTile / 2, y: entryTileCy - entryTile / 2,
                                 width: entryTile, height: entryTile)
                d.round(box, radius: 40, tile)
            }
            drawArt(d, entryArt[i])
            d.centerTextSqueezed(name, cx: cx, baseline: entryLabelBase, size: entryLabelSize,
                                 i == 1 ? blue : ink, bold: true, squeeze: squeeze)
        }
    }

    /// 机票卡（Java:336-402）
    private func drawFlightCard(_ d: Draw, _ s: State) {
        d.round(card, radius: cardRadius, .white)
        d.round(tabStrip, radius: 40, tabStripFill)
        d.round(tabPill, radius: 40, .white)
        for (i, name) in tabNames.enumerated() {
            d.centerTextSqueezed(name, cx: tabCx[i], baseline: tabBase, size: tabSize, ink,
                                 bold: i == 0, squeeze: squeeze)
        }

        drawArt(d, "compareMark")
        d.textSqueezed("多平台比价", x: compareX, baseline: compareBase, size: compareSize,
                       ink, bold: true, squeeze: squeeze)
        d.textSqueezed("· 多家官方平台，智选低价", x: compareSubX, baseline: compareBase,
                       size: compareSubSize, grey, squeeze: squeeze)
        drawArt(d, "platforms")

        d.textSqueezed(s.from, x: cityLeftX, baseline: routeBase, size: citySize, ink,
                       bold: true, squeeze: squeeze)
        d.textSqueezed(s.to, x: cityRightX, baseline: routeBase, size: citySize, ink,
                       bold: true, squeeze: squeeze)
        drawSwap(d)

        stroke(d, points: [CGPoint(x: 60, y: ruleY), CGPoint(x: 1220, y: ruleY)],
               color: rule, width: 3)
        let today = Self.todayText()
        d.textSqueezed(today, x: dateX, baseline: dateBase, size: dateSize, ink,
                       bold: true, squeeze: squeeze)
        let dateEnd = dateX + Draw.width(today, size: dateSize, bold: true)
        d.textSqueezed("今天", x: dateEnd + 24, baseline: dateBase, size: todaySize, ink,
                       squeeze: squeeze)

        d.textSqueezed("不限舱位", x: cabinX, baseline: cabinBase, size: cabinSize, ink,
                       squeeze: squeeze)
        let cabinEnd = cabinX + Draw.width("不限舱位", size: cabinSize)
        stroke(d, points: [CGPoint(x: cabinEnd + 22, y: cabinBase - 34),
                           CGPoint(x: cabinEnd + 40, y: cabinBase - 16),
                           CGPoint(x: cabinEnd + 58, y: cabinBase - 34)],
               color: Draw.color(0xff9aa3b0), width: 4.5)

        for (i, name) in checkNames.enumerated() {
            d.ctx.saveGState()
            d.ctx.setStrokeColor(Draw.color(0xffc9ced8).cgColor)
            d.ctx.setLineWidth(4)
            d.ctx.strokeEllipse(in: CGRect(x: checkCx[i] - checkR, y: checkCy - checkR,
                                           width: checkR * 2, height: checkR * 2))
            d.ctx.restoreGState()
            d.textSqueezed(name, x: checkCx[i] + 48, baseline: checkCy + 4, size: 44, ink,
                           squeeze: squeeze)
            d.textSqueezed(checkSubs[i], x: checkCx[i] + 48, baseline: checkCy + 50, size: 36,
                           grey, squeeze: squeeze)
        }

        // 优惠券条（Java:376-389）
        d.round(coupon, radius: 20, couponFill)
        drawArt(d, "couponBadge")
        d.textSqueezed("最高434元出行券包", x: 183, baseline: couponBase, size: couponSize,
                       ink, bold: true, squeeze: squeeze)
        let couponEnd = 183 + Draw.width("最高434元出行券包", size: couponSize, bold: true)
        d.textSqueezed("周周领", x: couponEnd + 20, baseline: couponBase, size: couponSize,
                       couponRed, bold: true, squeeze: squeeze)
        d.textSqueezed("领优惠", x: couponLinkX, baseline: couponBase, size: couponSize,
                       couponRed, bold: true, squeeze: squeeze)
        let linkEnd = couponLinkX + Draw.width("领优惠", size: couponSize, bold: true)
        stroke(d, points: [CGPoint(x: linkEnd + 16, y: couponBase - 32),
                           CGPoint(x: linkEnd + 34, y: couponBase - 14),
                           CGPoint(x: linkEnd + 52, y: couponBase - 32)],
               color: couponRed, width: 4.5)

        // 查询按钮：左右渐变，整块圆角（Java:391-396）
        d.clipped(to: query, radius: query.height / 2) {
            d.gradient(query, from: queryLeft, to: queryRight, vertical: false)
        }
        d.centerTextSqueezed("查询机票", cx: query.midX,
                             baseline: query.midY + queryTextSize * 0.36,
                             size: queryTextSize, .white, bold: true, squeeze: squeeze)

        for (i, name) in footNames.enumerated() {
            drawArt(d, footArt[i])
            d.textSqueezed(name, x: footTextX[i], baseline: footBase, size: footSize, ink,
                           squeeze: squeeze)
        }
    }

    /// 两城交换的环形箭头（Java:405-413）
    private func drawSwap(_ d: Draw) {
        let greyStroke = Draw.color(0xffc2c8d2)
        d.ctx.saveGState()
        d.ctx.setStrokeColor(greyStroke.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.strokeEllipse(in: CGRect(x: swapCx - swapR, y: swapCy - swapR,
                                       width: swapR * 2, height: swapR * 2))
        d.ctx.restoreGState()
        triangle(d, p1: CGPoint(x: swapCx + 16, y: swapCy - 18),
                 p2: CGPoint(x: swapCx - 4, y: swapCy - 18),
                 p3: CGPoint(x: swapCx - 4, y: swapCy - 32))
        triangle(d, p1: CGPoint(x: swapCx - 16, y: swapCy + 18),
                 p2: CGPoint(x: swapCx + 4, y: swapCy + 18),
                 p3: CGPoint(x: swapCx + 4, y: swapCy + 32))
        stroke(d, points: [CGPoint(x: swapCx - 14, y: swapCy - 18),
                           CGPoint(x: swapCx + 8, y: swapCy - 18)], color: greyStroke, width: 5)
        stroke(d, points: [CGPoint(x: swapCx - 8, y: swapCy + 18),
                           CGPoint(x: swapCx + 14, y: swapCy + 18)], color: greyStroke, width: 5)
    }

    private func triangle(_ d: Draw, p1: CGPoint, p2: CGPoint, p3: CGPoint) {
        d.path([p1, p2, p3], fill: Draw.color(0xffc2c8d2))
    }

    /// 服务卡：两行五格（Java:425-438）
    private func drawServiceCard(_ d: Draw) {
        d.round(card2, radius: cardRadius, .white)
        for (i, artName) in gridArt.enumerated() {
            let row = i / 5
            let col = i % 5
            let cx = entryCx[col]
            let cy = gridTileCy[row]
            let box = CGRect(x: cx - entryTile / 2, y: cy - entryTile / 2,
                             width: entryTile, height: entryTile)
            d.round(box, radius: 40, artName == "gridCoupon" ? tileWarm : tile)
            drawArt(d, artName)
            d.centerTextSqueezed(gridNames[i], cx: cx, baseline: gridLabelBase[row],
                                 size: gridLabelSize, ink, squeeze: squeeze)
        }
    }

    /// 出去玩（Java:440-450）
    private func drawPlaySection(_ d: Draw) {
        d.round(play, radius: 24, .white)
        // 左上角那块粉色渐变（参考图自带）
        let glow = CGRect(x: play.minX, y: play.minY, width: 420, height: 180)
        d.clipped(to: glow, radius: 24) {
            d.gradient(glow, from: Draw.color(0x33f8a2c0),
                       to: Draw.color(0x00000000), vertical: true)
        }
        d.textSqueezed("出去玩", x: playTitleX, baseline: playTitleBase, size: playTitleSize,
                       ink, bold: true, squeeze: squeeze)
        d.textSqueezed("AI 预测一下", x: playSubX, baseline: playTitleBase, size: playSubSize,
                       ink, bold: true, squeeze: squeeze)
    }

    /// 底部导航（Java:452-461）
    private func drawNav(_ d: Draw) {
        d.rect(CGRect(x: 0, y: navTop, width: Self.W, height: Self.H - navTop), .white)
        drawArt(d, "navAvatar")
        for (i, name) in navNames.enumerated() {
            if let artName = navArt[i] { drawArt(d, artName) }
            d.centerTextSqueezed(name, cx: navCx[i], baseline: navLabelBase, size: navLabelSize,
                                 i == 0 ? navOn : navOff, squeeze: squeeze)
        }
    }

    // MARK: - 图元

    private func drawArt(_ d: Draw, _ key: String) {
        guard let image = art[key], let box = boxes[key] else { return }
        d.art(image, box)
    }

    private func stroke(_ d: Draw, points: [CGPoint], color: UIColor, width: CGFloat) {
        guard let first = points.first else { return }
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(width)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: first)
        for point in points.dropFirst() { d.ctx.addLine(to: point) }
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 超长就截断加省略号（Java 的 `ellipsize`）
    private func ellipsize(_ value: String, size: CGFloat, bold: Bool, maxWidth: CGFloat) -> String {
        guard Draw.width(value, size: size, bold: bold) > maxWidth else { return value }
        var cut = value
        while !cut.isEmpty && Draw.width(cut + "…", size: size, bold: bold) > maxWidth {
            cut.removeLast()
        }
        return cut + "…"
    }

    /// 今天的日期：`M月d日`（Java 的 `today()`）
    private static func todayText() -> String {
        let comps = Calendar.current.dateComponents([.month, .day], from: Date())
        return "\(comps.month ?? 1)月\(comps.day ?? 1)日"
    }

    // MARK: - 命中（Java:466-512）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < 150 && x < 130 { return "back" }
        if y >= 150 && y <= 280 && x >= 120 && x <= 340 { return "address" }
        if y > 150 && y < 276 && x > 1180 { return "mileage" }
        if pill.contains(CGPoint(x: x, y: y)) { return "search" }
        if y >= 300 && y <= 560 {
            for (i, cx) in entryCx.enumerated() where abs(x - cx) < entryTile / 2 + 20 {
                return "entry:\(i)"
            }
        }
        if y >= 630 && y <= 720 {
            for (i, cx) in tabCx.enumerated() where abs(x - cx) < 120 {
                return "tab:\(i)"
            }
        }
        if y >= 900 && y <= 1000 {
            if abs(x - swapCx) < 52 { return "swap" }
            if x < 300 { return "city:0" }
            if x > 1000 { return "city:1" }
        }
        if y >= 1290 && y <= 1370 {
            if x < 320 { return "cabin" }
            for (i, cx) in checkCx.enumerated() where abs(x - cx) < 160 {
                return "check:\(i)"
            }
        }
        if coupon.contains(CGPoint(x: x, y: y)) { return x > 990 ? "couponLink" : "coupon" }
        if query.contains(CGPoint(x: x, y: y)) { return "query" }
        if y >= 1780 && y <= 1850 {
            for (i, fx) in footTextX.enumerated() where x > fx - 90 && x < fx + 200 {
                return "foot:\(i)"
            }
        }
        if card2.contains(CGPoint(x: x, y: y)) {
            let row = y < 2210 ? 0 : 1
            for i in 0..<5 where abs(x - entryCx[i]) < 122 {
                return "grid:\(row * 5 + i)"
            }
        }
        if y >= navTop {
            for (i, cx) in navCx.enumerated() where abs(x - cx) < 128 {
                return "nav:\(i)"
            }
        }
        return "noop"
    }
}
