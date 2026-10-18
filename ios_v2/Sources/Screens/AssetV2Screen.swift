import UIKit

/// 「总资产」页 —— `features/zhi/ui/AssetV2Screen.java` 的 1:1 移植。
///
/// 画布是 **1250×2764**（不是常见的 1280×2816），内容高 8120，是 App 里最长的一屏。
///
/// 结构：顶部淡蓝渐变 → 头栏（返回 / 总资产 / 去理财）→ 公告条（蚂蚁图 + 热标 + 一句话）
/// → 蓝色资产概览卡（三列数字 + 视线图标 + 内嵌白色面板三栏）
/// → 持仓分析卡（三笔钱 + 三个勾 + 甜甜圈 + CTA）
/// → 理财 / 保障 / 信用 / 更多服务 四张大卡（都是**整张切图 + 局部补丁**）
/// → 页脚热区。
///
/// 四张大卡几乎全靠切图，只有几处数字需要「盖掉再重画」；补丁色取自原图底色，
/// 所以肉眼看不出来（理财卡用白、累计收益与保额用 `#fafafa`、趋势用 `#f6f6f6`）。
final class AssetV2Screen: Screen {
    let canvasSize = CGSize(width: 1250, height: 2764)
    static let W: CGFloat = 1250
    static let H: CGFloat = 2764
    static let contentH: CGFloat = 8120

    final class State: ScreenState {
        /// 隐藏所有金额（点眼睛切换）
        var hideAll = false
        /// 用户改过的数字
        var edits: [String: Double] = [:]
        /// 用户改过的文字（公告等）；数字走 `edits`
        var texts: [String: String] = [:]
    }

    // MARK: - 调色板（Java:55-71）
    private let ink = Draw.color(0xff1a1a1a)
    private let ink2 = Draw.color(0xff26282b)
    private let gray = Draw.color(0xff9aa0a6)
    private let gray2 = Draw.color(0xff8a9099)
    private let blue = Draw.color(0xff1e7bff)
    private let blueDeep = Draw.color(0xff1677ff)
    private let green = Draw.color(0xff0e9f74)
    private let red = Draw.color(0xffe53935)
    private let divider = Draw.color(0xffeeeeee)
    private let page = Draw.color(0xfff2f2f2)
    private let cardColor = Draw.color(0xffffffff)
    private let onBlueLabel = Draw.color(0xffcfe0ff)
    private let textOnCard2 = Draw.color(0xff5a6470)
    private let navy = Draw.color(0xff16325c)

    private let headNum: CGFloat = 80
    private let panelNum: CGFloat = 53

    /// 16 张切图（Java:101-116）
    private static let artNames = [
        "ant": "asset_ant", "donut": "asset_donut", "piggy": "asset_piggy",
        "iconMoney": "asset_icon_money", "iconShield": "asset_icon_shield",
        "iconCard": "asset_icon_card", "cleanstar": "asset_cleanstar",
        "minidonut": "asset_minidonut", "triangle": "asset_triangle",
        "avatarsSmall": "asset_avatars_small", "avatarsLian": "asset_avatars_lian",
        "ledgerArt": "asset_ledger_art", "more": "asset_more_services",
        "licai": "asset_licai", "baozhang": "asset_baozhang", "xinyong": "asset_xinyong",
    ]
    private var art: [String: UIImage] = [:]

    /// 热区（Java:445-458），命中从后往前
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private var hits: [Hit] = []

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.H) }

    // MARK: - 入口（Java:139-160）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        hits.removeAll()

        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), page)
        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        drawTopBackdrop(d)
        drawHeader(d)
        drawAnnouncement(d, s)
        drawBlueCard(d, s)
        drawHoldings(d)
        drawLicai(d, s)
        drawBaozhang(d, s)
        drawXinyong(d, s)
        drawMoreServices(d, s)

        d.ctx.restoreGState()
    }

    private func hit(_ rect: CGRect, _ token: ActionToken) {
        hits.append(Hit(rect: rect, token: token))
    }

    /// 顶部渐变（Java:162-168）
    private func drawTopBackdrop(_ d: Draw) {
        d.gradient(CGRect(x: 0, y: 0, width: Self.W, height: 500),
                   from: Draw.color(0xffdce9fb), to: Draw.color(0xffedf2fa), vertical: true)
    }

    /// 头栏（Java:171-189）
    private func drawHeader(_ d: Draw) {
        strokeLine(d, points: [CGPoint(x: 80, y: 190), CGPoint(x: 46, y: 221), CGPoint(x: 80, y: 252)],
                   color: ink, width: 9)
        d.text("总资产", x: 132, baseline: 238, size: 62, ink, bold: true)
        d.rightText("去理财", right: 1195, baseline: 238, size: 54, ink)
        hit(CGRect(x: 30, y: 165, width: 1120 - 30, height: 280 - 165), "back")
        hit(CGRect(x: 1000, y: 165, width: 1240 - 1000, height: 280 - 165), "noop")
    }

    /// 公告条（Java:191-202）
    private func drawAnnouncement(_ d: Draw, _ s: State) {
        d.art(art["ant"], CGRect(x: 56, y: 310, width: 180 - 56, height: 432 - 310))

        d.round(CGRect(x: 205, y: 308, width: 905 - 205, height: 428 - 308), radius: 24, cardColor)
        d.round(CGRect(x: 237, y: 345, width: 305 - 237, height: 400 - 345), radius: 10,
                Draw.color(0xff2a7dff))
        d.centerText("热", cx: 271, baseline: 386, size: 34, .white)

        let message = s.texts["announce"]
            ?? UserDefaults.standard.string(forKey: "asset.announce")
            ?? "沪指突破3800！关注什么"
        d.text(message, x: 316, baseline: 382, size: 46, ink)
        hit(CGRect(x: 205, y: 308, width: 905 - 205, height: 428 - 308), "text:announce")
    }

    /// 蓝色资产概览卡（Java:205-272）
    private func drawBlueCard(_ d: Draw, _ s: State) {
        let cardBox = CGRect(x: 27, y: 478, width: 1223 - 27, height: 1166 - 478)
        d.clipped(to: cardBox, radius: 26) {
            d.gradient(cardBox, from: Draw.color(0xff1777ff), to: Draw.color(0xff3a8bff),
                       vertical: false)
        }

        d.text("资产概览", x: 68, baseline: 572, size: 46, .white, bold: true)
        d.round(CGRect(x: 278, y: 530, width: 474 - 278, height: 596 - 530), radius: 33,
                UIColor.white.withAlphaComponent(0x40 / 255.0))
        shieldGlyph(d, CGRect(x: 300, y: 549, width: 332 - 300, height: 583 - 549), .white)
        d.text("保障中", x: 342, baseline: 578, size: 36, .white)

        d.text("我的资产（元）", x: 68, baseline: 696, size: 38, onBlueLabel)
        chevron(d, x0: 340, y0: 672, x1: 358, y1: 698, color: onBlueLabel, stroke: 4)
        eyeGlyph(d, CGRect(x: 368, y: 660, width: 432 - 368, height: 706 - 660))
        d.text(Self.money(s, "a.my", 0), x: 68, baseline: 789, size: headNum, .white,
               bold: true, useNumberFace: true)
        hit(CGRect(x: 60, y: 660, width: 400, height: 150), "num:a.my")
        hit(CGRect(x: 362, y: 654, width: 78, height: 58), "eye")

        d.text("昨日收益", x: 700, baseline: 696, size: 38, onBlueLabel)
        chevron(d, x0: 866, y0: 672, x1: 884, y1: 698, color: onBlueLabel, stroke: 4)
        d.text(Self.money(s, "a.profit", 0), x: 748, baseline: 789, size: headNum, .white,
               bold: true, useNumberFace: true)
        hit(CGRect(x: 690, y: 660, width: 240, height: 150), "num:a.profit")

        d.rect(CGRect(x: 945, y: 662, width: 2, height: 812 - 662),
               UIColor.white.withAlphaComponent(0x59 / 255.0))

        d.text("家庭保障", x: 994, baseline: 696, size: 38, onBlueLabel)
        chevron(d, x0: 1158, y0: 672, x1: 1176, y1: 698, color: onBlueLabel, stroke: 4)
        let family = Self.plain(s, "a.family", 2)
        d.text(family, x: 1084, baseline: 789, size: headNum, .white, bold: true, useNumberFace: true)
        let familyWidth = Fonts.mixedWidth(family, size: headNum, bold: true)
        d.text("份", x: 1084 + familyWidth + 8, baseline: 786, size: 38, .white)
        hit(CGRect(x: 990, y: 660, width: 214, height: 150), "num:a.family")

        // 内嵌白色面板（Java:242-272）
        d.round(CGRect(x: 41, y: 854, width: 1209 - 41, height: 1152 - 854), radius: 18, cardColor)
        d.rect(CGRect(x: 421, y: 884, width: 2, height: 1126 - 884), divider)
        d.rect(CGRect(x: 811, y: 884, width: 2, height: 1126 - 884), divider)

        let labels = ["活期资产", "稳健理财", "进阶理财"]
        let keys1 = ["a.huo1", "a.wen1", "a.jin"]
        let keys2: [String?] = ["a.huo2", "a.wen2", nil]
        let def1: [Double] = [0, 0, 1]
        let def2: [Double] = [0, 0, 0]
        let xs: [CGFloat] = [96, 485, 859]
        for i in 0..<3 {
            let x = xs[i]
            d.text(labels[i], x: x, baseline: 938, size: 36, gray)
            let labelWidth = Draw.width(labels[i], size: 36)
            chevron(d, x0: x + labelWidth + 8, y0: 916, x1: x + labelWidth + 26, y1: 940,
                    color: gray, stroke: 4)
            if i == 2 {
                let value = "费率" + Self.plain(s, "a.jin", 1) + "折起"
                d.text(value, x: x, baseline: 1012, size: 46, ink2)
            } else {
                d.text(Self.money(s, keys1[i], def1[i]), x: x, baseline: 1012, size: panelNum,
                       ink2, useNumberFace: true)
                if let key = keys2[i] {
                    d.text(Self.money(s, key, def2[i]), x: x, baseline: 1078, size: panelNum,
                           Draw.color(0xffa9afb6), useNumberFace: true)
                }
            }
        }
        hit(CGRect(x: 90, y: 970, width: 310, height: 130), "num:a.huo1")
        hit(CGRect(x: 479, y: 970, width: 311, height: 130), "num:a.wen1")
        hit(CGRect(x: 853, y: 890, width: 307, height: 150), "num:a.jin")
    }

    /// 持仓分析卡（Java:275-307）
    private func drawHoldings(_ d: Draw) {
        d.round(CGRect(x: 27, y: 1204, width: 1223 - 27, height: 1814 - 1204), radius: 26, cardColor)

        d.text("持仓分析", x: 66, baseline: 1278, size: 46, blue, bold: true)
        d.rect(CGRect(x: 66, y: 1300, width: 252 - 66, height: 6), blue)
        d.text("收益分析", x: 322, baseline: 1278, size: 44, Draw.color(0xff333333))
        d.text("投资机会", x: 575, baseline: 1278, size: 44, Draw.color(0xff333333))
        d.text("保障分析", x: 828, baseline: 1278, size: 44, Draw.color(0xff333333))
        hit(CGRect(x: 300, y: 1230, width: 240, height: 82), "noop")
        hit(CGRect(x: 553, y: 1230, width: 247, height: 82), "noop")
        hit(CGRect(x: 806, y: 1230, width: 234, height: 82), "noop")

        d.round(CGRect(x: 43, y: 1332, width: 1207 - 43, height: 1795 - 1332), radius: 20,
                Draw.color(0xfff0f6fe))

        d.text("查看三笔钱配置优化方案", x: 78, baseline: 1448, size: 54, navy, bold: true)
        d.text("你的专属资产配置工具", x: 78, baseline: 1543, size: 38, textOnCard2)

        let tags = ["灵活取用", "未来保障", "投资增值"]
        let tx: [CGFloat] = [132, 346, 560]
        let cx: [CGFloat] = [78, 292, 506]
        for i in 0..<3 {
            tick(d, CGRect(x: cx[i], y: 1600, width: 38, height: 38))
            d.text(tags[i], x: tx[i], baseline: 1630, size: 38, ink2)
        }

        d.art(art["donut"], CGRect(x: 890, y: 1400, width: 1185 - 890, height: 1660 - 1400))

        d.text("三笔钱｜帮你管理财富 科学规划未来", x: 80, baseline: 1750, size: 40,
               Draw.color(0xff333333))
        pill(d, CGRect(x: 965, y: 1710, width: 1170 - 965, height: 1790 - 1710), blueDeep)
        d.centerText("去看看", cx: 1067, baseline: 1766, size: 40, .white)
        hit(CGRect(x: 940, y: 1690, width: 250, height: 120), "noop")
    }

    /// 理财资产卡（Java:310-346）：整张切图 + 六处数字补丁
    private func drawLicai(_ d: Draw, _ s: State) {
        d.art(art["licai"], CGRect(x: 0, y: 1856, width: 1250, height: 3038 - 1856))

        patch(d, CGRect(x: 60, y: 2140, width: 158 - 60, height: 2190 - 2140), .white)
        d.text(Self.money(s, "b.yue", 0), x: 68, baseline: 2182, size: 46, ink2,
               useNumberFace: true)
        hit(CGRect(x: 58, y: 2134, width: 262, height: 64), "num:b.yue")

        patch(d, CGRect(x: 660, y: 2352, width: 862 - 660, height: 2408 - 2352), .white)
        let fund = Self.plain(s, "b.fund", 10)
        d.text(fund, x: 668, baseline: 2403, size: 46, ink2, useNumberFace: true)
        d.text("元试试", x: 668 + Fonts.mixedWidth(fund, size: 46, bold: false), baseline: 2403,
               size: 46, ink2)
        hit(CGRect(x: 660, y: 2346, width: 220, height: 66), "num:b.fund")

        patch(d, CGRect(x: 62, y: 2568, width: 286 - 62, height: 2630 - 2568), .white)
        d.text("买入", x: 68, baseline: 2621, size: 45, ink2)
        let inWidth = Draw.width("买入", size: 45)
        let gold = Self.plain(s, "b.gold", 0)
        d.text(gold, x: 68 + inWidth, baseline: 2621, size: 45, ink2, useNumberFace: true)
        d.text("费率", x: 68 + inWidth + Fonts.mixedWidth(gold, size: 45, bold: false),
               baseline: 2621, size: 45, ink2)
        hit(CGRect(x: 62, y: 2564, width: 238, height: 70), "num:b.gold")

        patch(d, CGRect(x: 326, y: 2790, width: 470 - 326, height: 2842 - 2790),
              Draw.color(0xfffafafa))
        d.text(Self.money(s, "b.leiji", -24.72), x: 332, baseline: 2833, size: 46, green,
               useNumberFace: true)
        hit(CGRect(x: 322, y: 2786, width: 158, height: 60), "num:b.leiji")

        patch(d, CGRect(x: 848, y: 2790, width: 1008 - 848, height: 2842 - 2790),
              Draw.color(0xfffafafa))
        d.text(Self.pct(s, "b.shouyi", -0.39), x: 855, baseline: 2833, size: 46, green,
               useNumberFace: true)
        hit(CGRect(x: 844, y: 2786, width: 174, height: 60), "num:b.shouyi")
        hit(CGRect(x: 66, y: 2930, width: 404, height: 80), "noop")
    }

    /// 保障资产卡（Java:349-372）
    private func drawBaozhang(_ d: Draw, _ s: State) {
        d.art(art["baozhang"], CGRect(x: 0, y: 3076, width: 1250, height: 4022 - 3076))

        patch(d, CGRect(x: 62, y: 3244, width: 126 - 62, height: 3332 - 3244), .white)
        d.text(Self.plain(s, "c.count", 2), x: 70, baseline: 3322, size: 90, ink2,
               useNumberFace: true)
        hit(CGRect(x: 60, y: 3240, width: 240, height: 96), "num:c.count")

        patch(d, CGRect(x: 60, y: 3770, width: 222 - 60, height: 3832 - 3770),
              Draw.color(0xfffafafa))
        let cur = Self.money(s, "c.cur", 1.00)
        d.text(cur, x: 70, baseline: 3822, size: 54, ink2, useNumberFace: true)
        d.text("元", x: 70 + Fonts.mixedWidth(cur, size: 54, bold: false), baseline: 3820,
               size: 42, ink2)
        hit(CGRect(x: 58, y: 3766, width: 210, height: 70), "num:c.cur")

        patch(d, CGRect(x: 396, y: 3770, width: 606 - 396, height: 3832 - 3770),
              Draw.color(0xfffafafa))
        let max = Self.plain(s, "c.max", 30000)
        d.text(max, x: 402, baseline: 3822, size: 54, red, useNumberFace: true)
        d.text("元", x: 402 + Fonts.mixedWidth(max, size: 54, bold: false), baseline: 3820,
               size: 42, red)
        hit(CGRect(x: 394, y: 3766, width: 236, height: 70), "num:c.max")
        hit(CGRect(x: 66, y: 3912, width: 234, height: 78), "noop")
    }

    /// 信用资产卡（Java:375-402）
    private func drawXinyong(_ d: Draw, _ s: State) {
        d.art(art["xinyong"], CGRect(x: 0, y: 4058, width: 1250, height: 5192 - 4058))

        patch(d, CGRect(x: 62, y: 4350, width: 412 - 62, height: 4440 - 4350), .white)
        d.text(Self.money(s, "d.avail", 2710.98), x: 68, baseline: 4436, size: 100, ink,
               useNumberFace: true)
        hit(CGRect(x: 58, y: 4344, width: 366, height: 102), "num:d.avail")

        patch(d, CGRect(x: 658, y: 4392, width: 1006 - 658, height: 4444 - 4392), .white)
        let free = Self.plain(s, "d.free", 2000)
        d.text(free, x: 666, baseline: 4438, size: 48, gray2, useNumberFace: true)
        d.text("免费额度起", x: 666 + Fonts.mixedWidth(free, size: 48, bold: false),
               baseline: 4436, size: 48, gray2)
        hit(CGRect(x: 656, y: 4386, width: 356, height: 62), "num:d.free")

        patch(d, CGRect(x: 60, y: 4722, width: 244 - 60, height: 4774 - 4722), .white)
        d.text(Self.money(s, "d.hbAvail", 2710.98), x: 68, baseline: 4768, size: 50, ink2,
               useNumberFace: true)
        hit(CGRect(x: 58, y: 4716, width: 202, height: 64), "num:d.hbAvail")

        patch(d, CGRect(x: 658, y: 4722, width: 848 - 658, height: 4774 - 4722), .white)
        d.text(Self.money(s, "d.hbTotal", 3000.00), x: 665, baseline: 4768, size: 50, ink2,
               useNumberFace: true)
        hit(CGRect(x: 656, y: 4716, width: 210, height: 64), "num:d.hbTotal")
        hit(CGRect(x: 66, y: 5085, width: 234, height: 75), "noop")
    }

    /// 更多服务（Java:406-437）：整块切图 + 六处数字
    private func drawMoreServices(_ d: Draw, _ s: State) {
        d.art(art["more"], CGRect(x: 0, y: 5198, width: 1250, height: 8105 - 5198))

        patch(d, CGRect(x: 672, y: 5542, width: 1013 - 672, height: 5593 - 5542), .white)
        d.text("超" + Self.plain(s, "e.users", 6000) + "万用户已测", x: 678, baseline: 5588,
               size: 39, Draw.color(0xff545454))
        hit(CGRect(x: 668, y: 5538, width: 344, height: 60), "num:e.users")

        patch(d, CGRect(x: 672, y: 7093, width: 1068 - 672, height: 7150 - 7093), .white)
        d.text("超过" + Self.oneDec(s, "g.people", 1.4) + "亿人正在体验", x: 678,
               baseline: 7145, size: 42, Draw.color(0xff222222))
        hit(CGRect(x: 668, y: 7088, width: 397, height: 66), "num:g.people")

        let keys = ["f.r1", "f.r2", "f.r3"]
        let defs: [Double] = [34.06, 42.11, 46.26]
        let base: [CGFloat] = [7352, 7457, 7562]
        for i in 0..<3 {
            patch(d, CGRect(x: 410, y: base[i] - 38, width: 572 - 410, height: 12 + 38),
                  Draw.color(0xfff6f6f6))
            d.text(Self.pctSigned(s, keys[i], defs[i]), x: 416, baseline: base[i], size: 38,
                   red, useNumberFace: true)
            hit(CGRect(x: 406, y: base[i] - 42, width: 172, height: 60), "num:" + keys[i])
        }

        // 卡片按钮：图里已经画好，这里只登记热区
        hit(CGRect(x: 70, y: 6012, width: 250, height: 78), "noop")
        hit(CGRect(x: 680, y: 6002, width: 205, height: 78), "noop")
        hit(CGRect(x: 70, y: 6832, width: 250, height: 78), "noop")
        hit(CGRect(x: 678, y: 6832, width: 252, height: 78), "noop")
        hit(CGRect(x: 68, y: 7622, width: 252, height: 82), "noop")
        hit(CGRect(x: 678, y: 7222, width: 252, height: 88), "noop")
        // 页脚「资产账户信息」也是图里画的
        hit(CGRect(x: 500, y: 7890, width: 260, height: 80), "noop")
    }

    // MARK: - 图元（Java:540-780）

    private func pill(_ d: Draw, _ box: CGRect, _ color: UIColor) {
        d.round(box, radius: box.height / 2, color)
    }

    /// 用一块纯色盖住切图里的旧数字
    private func patch(_ d: Draw, _ box: CGRect, _ color: UIColor) {
        d.rect(box, color)
    }

    /// 右箭头：两段线（Java:630-644）
    private func chevron(_ d: Draw, x0: CGFloat, y0: CGFloat, x1: CGFloat, y1: CGFloat,
                         color: UIColor, stroke: CGFloat) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: x0, y: y0))
        d.ctx.addLine(to: CGPoint(x: x1, y: (y0 + y1) / 2))
        d.ctx.addLine(to: CGPoint(x: x0, y: y1))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 盾牌 + 勾（Java:646-669）
    private func shieldGlyph(_ d: Draw, _ box: CGRect, _ color: UIColor) {
        let w = box.width
        let h = box.height
        let l = box.minX, t = box.minY, r = box.maxX, b = box.maxY
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(2.6)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.beginPath()
        d.ctx.move(to: CGPoint(x: l + w / 2, y: t))
        d.ctx.addLine(to: CGPoint(x: r, y: t + h * 0.20))
        d.ctx.addLine(to: CGPoint(x: r, y: t + h * 0.52))
        d.ctx.addQuadCurve(to: CGPoint(x: l + w / 2, y: b), control: CGPoint(x: r, y: b))
        d.ctx.addQuadCurve(to: CGPoint(x: l, y: t + h * 0.52), control: CGPoint(x: l, y: b))
        d.ctx.addLine(to: CGPoint(x: l, y: t + h * 0.20))
        d.ctx.closePath()
        d.ctx.strokePath()
        d.ctx.beginPath()
        d.ctx.move(to: CGPoint(x: l + w * 0.33, y: t + h * 0.48))
        d.ctx.addLine(to: CGPoint(x: l + w * 0.46, y: t + h * 0.62))
        d.ctx.addLine(to: CGPoint(x: l + w * 0.70, y: t + h * 0.34))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 眼睛图标（Java:712-728）
    private func eyeGlyph(_ d: Draw, _ box: CGRect) {
        let cx = box.midX, cy = box.midY
        let w = box.width / 2, h = box.height / 2
        d.ctx.saveGState()
        d.ctx.setStrokeColor(UIColor.white.cgColor)
        d.ctx.setLineWidth(5)
        d.ctx.beginPath()
        d.ctx.move(to: CGPoint(x: cx - w, y: cy))
        d.ctx.addQuadCurve(to: CGPoint(x: cx + w, y: cy), control: CGPoint(x: cx, y: cy - h))
        d.ctx.addQuadCurve(to: CGPoint(x: cx - w, y: cy), control: CGPoint(x: cx, y: cy + h))
        d.ctx.strokePath()
        d.ctx.restoreGState()
        d.circle(cx: cx, cy: cy, r: 8, .white)
    }

    /// 蓝底白勾的小圆（Java:730-746）
    private func tick(_ d: Draw, _ box: CGRect) {
        let cx = box.midX, cy = box.midY
        d.circle(cx: cx, cy: cy, r: box.width / 2, Draw.color(0xff4f8ff5))
        d.ctx.saveGState()
        d.ctx.setStrokeColor(UIColor.white.cgColor)
        d.ctx.setLineWidth(3.4)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: cx - 7, y: cy))
        d.ctx.addLine(to: CGPoint(x: cx - 2, y: cy + 6))
        d.ctx.addLine(to: CGPoint(x: cx + 8, y: cy - 6))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func strokeLine(_ d: Draw, points: [CGPoint], color: UIColor, width: CGFloat) {
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

    // MARK: - 数值格式（与 Java:461-537 逐条一致）

    private static func group(_ digits: String) -> String {
        guard digits.count > 3 else { return digits }
        var out = ""
        var count = 0
        for ch in digits.reversed() {
            out.append(ch)
            count += 1
            if count % 3 == 0 && count < digits.count { out.append(",") }
        }
        return String(out.reversed())
    }

    static func formatMoney(_ value: Double) -> String {
        let negative = value < 0
        let v = abs(value)
        var integer = (v + 1e-9).rounded(.down)
        var frac = ((v - integer) * 100).rounded()
        if frac >= 100 {
            integer += 1
            frac = 0
        }
        let f = frac < 10 ? "0\(Int(frac))" : "\(Int(frac))"
        return (negative ? "-" : "") + group("\(Int(integer))") + "." + f
    }

    static func formatPlain(_ value: Double) -> String {
        if value == value.rounded(), abs(value) < 1e15 {
            return "\(Int(value))"
        }
        return "\(value)"
    }

    static func formatOneDec(_ value: Double) -> String {
        if value == value.rounded(), abs(value) < 1e15 {
            return "\(Int(value))"
        }
        return "\(value)"
    }

    static func formatPct(_ value: Double) -> String { String(format: "%.2f", value) + "%" }

    static func formatPctSigned(_ value: Double) -> String {
        (value >= 0 ? "+" : "") + String(format: "%.2f", value) + "%"
    }

    private static func val(_ s: State, _ key: String, _ def: Double) -> Double {
        s.edits[key] ?? def
    }

    private static func money(_ s: State, _ key: String, _ def: Double) -> String {
        s.hideAll ? "****" : formatMoney(val(s, key, def))
    }

    private static func plain(_ s: State, _ key: String, _ def: Double) -> String {
        if s.hideAll && def >= 100 { return "****" }
        return formatPlain(val(s, key, def))
    }

    private static func oneDec(_ s: State, _ key: String, _ def: Double) -> String {
        if s.hideAll && def >= 100 { return "****" }
        return formatOneDec(val(s, key, def))
    }

    private static func pct(_ s: State, _ key: String, _ def: Double) -> String {
        formatPct(val(s, key, def))
    }

    private static func pctSigned(_ s: State, _ key: String, _ def: Double) -> String {
        s.hideAll ? "****" : formatPctSigned(val(s, key, def))
    }

    // MARK: - 命中（Java:451-458：后登记优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        let cy = y + s.scroll
        for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: cy)) {
            return hit.token
        }
        return "noop"
    }
}

// MARK: - 可编辑（点数字/公告弹输入框）

extension AssetV2Screen.State: EditableState {
    /// 键名前缀 → 这块数字属于哪一组（调用方只给「a.my」这种内部键，弹窗要给人看的中文）
    private static let groups: [(prefix: String, title: String)] = [
        ("a.", "资产概览"),
        ("b.", "余额与理财"),
        ("c.", "信用与额度"),
        ("d.", "花呗"),
        ("e.", "保障与会员"),
        ("g.", "更多服务"),
    ]

    func editTitle(_ key: String) -> String {
        if key == "announce" { return "编辑公告" }
        for group in Self.groups where key.hasPrefix(group.prefix) {
            return "编辑" + group.title
        }
        return "编辑数值"
    }

    func editValue(_ key: String) -> String {
        if key == "announce" {
            return texts["announce"]
                ?? UserDefaults.standard.string(forKey: "asset.announce") ?? ""
        }
        // 改过的就预填改过的值；没改过就让输入框空着（默认值写在各绘制处，State 拿不到）
        guard let value = edits[key] else { return "" }
        return AssetV2Screen.formatPlain(value)
    }

    func applyEdit(_ key: String, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if key == "announce" {
            if trimmed.isEmpty {
                texts.removeValue(forKey: "announce")
            } else {
                texts["announce"] = trimmed
                UserDefaults.standard.set(trimmed, forKey: "asset.announce")
            }
            return
        }
        // 数字：只取数字与开头负号（与 Android 的 number() 一致）
        var digits = ""
        for ch in trimmed {
            if ch.isNumber || ch == "." || (ch == "-" && digits.isEmpty) { digits.append(ch) }
        }
        if let value = Double(digits) {
            edits[key] = value
        } else if trimmed.isEmpty {
            edits.removeValue(forKey: key)
        }
    }
}
