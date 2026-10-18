import UIKit

/// 「账单消息」页 —— `features/zhi/ui/PayMsgV2Screen.java` 的 1:1 移植（参考画布 1280×2816，内容高 7545）。
///
/// 这一页的结构是「3 条整宽切图 + 8 张卡片切图 + 一张可编辑字段表」：
///   · 默认状态（用户没改过任何东西）**就是纯切图**，因此默认渲染天然 1:1
///   · 每张卡片的商户名/时间/状态/金额/两行明细都登记成一个 `Field`，
///     用户改过（或标记 force）才用同色块盖掉原图重画
///
/// 字段表的数值全部照抄 Java 的 `FIELDS` 静态初始化块，注释里标了对应行号。
final class PayMsgV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    /// 顶栏底边（这一条以上不跟着滚动）
    static let headBottom: CGFloat = 348
    /// 整页内容高度
    static let contentH: CGFloat = 7545

    final class State: ScreenState {
        /// 用户改过的字段（key → 新文字）
        var texts: [String: String] = [:]
    }

    // MARK: - 调色板（Java:43-52）
    private let pageBg = Draw.color(0xfff5f5f5)
    private let card = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff333333)
    private let grey = Draw.color(0xff999999)
    private let value = Draw.color(0xff666666)
    private let blue = Draw.color(0xff1677ff)
    private let chipBg = Draw.color(0xffedf4fe)

    private let cardL: CGFloat = 28
    private let cardR: CGFloat = 1252
    private let center: CGFloat = 640

    /// 卡片 {上沿, 高, 型}（Java:104-112）
    private static let cards: [(top: CGFloat, height: CGFloat, type: Int)] = [
        (712, 669, 0), (1424, 587, 1), (2054, 669, 0), (2766, 997, 2),
        (3806, 587, 1), (4436, 997, 2), (5476, 997, 2), (6516, 997, 2),
    ]
    /// 每张卡片贴的切图（最后一张借第 7 张，Java:115-116）
    private static let cardArt = ["pm_c1", "pm_c2", "pm_c3", "pm_c4",
                                 "pm_c5", "pm_c6", "pm_c7", "pm_c7"]

    /// 卡片上的文字：商户名/时间/状态/金额/左标签/值/右标签/值/操作1/操作2（Java:124-144）
    private static let cardText: [[String]] = [
        ["杭州深度求索", "11:44", "付款成功", "19.92", "付款方式", "招商银行储蓄卡(4646)",
         "抵扣金额", "红包 0.08", "", ""],
        ["南京爱维云网络科技有限公司", "昨天 20:44", "付款成功", "20.00", "付款方式",
         "招商银行储蓄卡(4646)", "", "", "", ""],
        ["花呗", "10月11日 21:40", "付款成功", "147.87", "付款方式", "招商银行储蓄卡(4646)",
         "还款到", "花呗", "", ""],
        ["闲鱼", "10月8日 20:13", "自动扣款成功", "1.40", "付款方式", "招商银行储蓄卡(4646)",
         "交易对象", "x***0", "管理当前自动扣款服务", "进入闲鱼"],
        ["中国移动", "10月3日 10:08", "付款成功", "50.00", "付款方式", "招商银行储蓄卡(4646)",
         "", "", "", ""],
        ["闲鱼", "10月1日 15:31", "自动扣款成功", "36.00", "付款方式", "招商银行储蓄卡(4646)",
         "交易对象", "x***0", "管理当前自动扣款服务", "进入闲鱼"],
        ["闲鱼", "10月1日 15:10", "自动扣款成功", "1.40", "付款方式", "招商银行储蓄卡(4646)",
         "交易对象", "x***0", "管理当前自动扣款服务", "进入闲鱼"],
        ["闲鱼", "10月1日 14:56", "自动扣款成功", "1.40", "付款方式", "招商银行储蓄卡(4646)",
         "交易对象", "x***0", "管理当前自动扣款服务", "进入闲鱼"],
    ]

    // MARK: - 把一张消息卡变成账单，供「账单详情」页复用（Java 点卡片进 detail）

    /// 卡片文字表的下标含义（`cardText`）：0 商户 / 1 时间 / 2 状态 / 3 金额 /
    /// 4 左标签 / 5 左值 / 6 右标签 / 7 右值 / 8 操作一 / 9 操作二
    static func bill(at index: Int) -> Bill? {
        guard index >= 0 && index < cardText.count else { return nil }
        let row = cardText[index]
        func field(_ i: Int) -> String { i < row.count ? row[i] : "" }

        let bill = Bill()
        bill.merchant = field(0)
        bill.category = "转账红包"       // 账单详情的分类行；这页参考图没有分类，用最常见的
        bill.status = field(2)
        bill.payMethod = field(5)
        bill.goods = field(0)
        // 金额：这页全是支出，所以取负（`Bill.income` 以符号判断）
        if let amount = Double(field(3)) { bill.amount = -amount }
        // 时间：卡片上写的是「11:44 / 昨天 20:44 / 10月11日 21:40」，解析出时分即可
        bill.time = Self.parseTime(field(1))
        bill.orderNo = "2026" + String(format: "%08d", index + 1)
        bill.merchantOrderNo = "M" + String(format: "%012d", index + 1)
        return bill
    }

    /// 从卡片上的时间文字里尽量取一个时间点；取不到就用今天同一时刻
    private static func parseTime(_ text: String) -> TimeInterval {
        let calendar = Calendar.current
        let now = Date()
        // 先把「今天 / 昨天 / 10月11日」这类前缀去掉，只留 HH:mm
        var clock = ""
        for token in text.split(separator: " ") {
            let piece = String(token)
            if piece.contains(":") { clock = piece }
        }
        let parts = clock.split(separator: ":").compactMap { Int($0) }
        var comps = calendar.dateComponents([.year, .month, .day], from: now)
        comps.hour = parts.count == 2 ? parts[0] : 12
        comps.minute = parts.count == 2 ? parts[1] : 0
        comps.second = 0
        var date = calendar.date(from: comps) ?? now
        // 「昨天」往前挪一天
        if text.contains("昨天") {
            date = calendar.date(byAdding: .day, value: -1, to: date) ?? date
        }
        return date.timeIntervalSince1970
    }

    private static let sumNum = ["0", "4", "0", "0"]
    private static let sumLabel = ["大额消费", "自动扣款", "花呗付款", "分期付款"]
    private static let sumNumCx: [CGFloat] = [199, 492, 787, 1081]
    private static let sumLabCx: [CGFloat] = [198, 493, 786, 1080]

    /// 字段的绘制种类（Java:52）
    private enum Kind { case plain, money, chip }
    private enum Align { case left, center, right }

    /// 一行可编辑内容（对应 Java 的 `Field`）
    private struct Field {
        let key: String
        let def: String
        let rect: CGRect
        let x: CGFloat
        let baseline: CGFloat
        let size: CGFloat
        let color: UIColor
        let align: Align
        let kind: Kind
        var force = false
    }

    private var art: [String: UIImage] = [:]
    private let fields: [Field]

    init() {
        for res in ["pm_head", "pm_sum"] + Self.cardArt {
            if let image = UIImage(named: res) { art[res] = image }
        }
        // 构建字段表（照抄 Java:158-217）
        var list: [Field] = []
        let numL: [CGFloat] = [140, 432, 728, 1022]
        for i in 0..<Self.sumNum.count {
            list.append(Field(key: "sum.n\(i + 1)", def: Self.sumNum[i],
                              rect: CGRect(x: numL[i], y: 494, width: 120, height: 566 - 494),
                              x: Self.sumNumCx[i], baseline: 559, size: 79,
                              color: Draw.color(0xff333333), align: .center, kind: .plain))
            list.append(Field(key: "sum.l\(i + 1)", def: Self.sumLabel[i],
                              rect: CGRect(x: numL[i] - 60, y: 576, width: 240, height: 628 - 576),
                              x: Self.sumLabCx[i], baseline: 620, size: 41,
                              color: Draw.color(0xff999999), align: .center, kind: .plain))
        }
        list.append(Field(key: "sum.title", def: Self.monthTitle(),
                          rect: CGRect(x: 66, y: 396, width: 430 - 66, height: 452 - 396),
                          x: 71, baseline: 445, size: 47, color: Draw.color(0xff333333),
                          align: .left, kind: .plain))
        list.append(Field(key: "sum.spend", def: "本月支出 ¥131.11，较上月降低",
                          rect: CGRect(x: 600, y: 396, width: 1198 - 600, height: 452 - 396),
                          x: 1195, baseline: 445, size: 44, color: Draw.color(0xff999999),
                          align: .right, kind: .plain))

        for (i, card) in Self.cards.enumerated() {
            let top = card.top
            let text = Self.cardText[i]
            let p = "c\(i + 1)."
            list.append(Field(key: p + "name", def: text[0],
                              rect: CGRect(x: 198, y: top + 58, width: 1012 - 198, height: 54),
                              x: 205, baseline: top + 106, size: 50,
                              color: Draw.color(0xff333333), align: .left, kind: .plain))
            list.append(Field(key: p + "time", def: text[1],
                              rect: CGRect(x: 198, y: top + 118, width: 900 - 198, height: 40),
                              x: 207, baseline: top + 152, size: 38,
                              color: Draw.color(0xff999999), align: .left, kind: .plain))
            if card.type == 2 {
                list.append(Field(key: p + "status", def: text[2],
                                  rect: CGRect(x: 452, y: top + 194, width: 828 - 452, height: 118),
                                  x: 640, baseline: top + 277, size: 49,
                                  color: Draw.color(0xff1677ff), align: .center, kind: .chip))
                list.append(Field(key: p + "amount", def: text[3],
                                  rect: CGRect(x: 300, y: top + 312, width: 984 - 300, height: 90),
                                  x: 640, baseline: top + 391, size: 104,
                                  color: Draw.color(0xff333333), align: .left, kind: .money))
            } else {
                list.append(Field(key: p + "status", def: text[2],
                                  rect: CGRect(x: 410, y: top + 206, width: 872 - 410, height: 66),
                                  x: 640, baseline: top + 264, size: 49,
                                  color: Draw.color(0xff999999), align: .center, kind: .plain))
                list.append(Field(key: p + "amount", def: text[3],
                                  rect: CGRect(x: 300, y: top + 282, width: 984 - 300, height: 90),
                                  x: 640, baseline: top + 363, size: 104,
                                  color: Draw.color(0xff333333), align: .left, kind: .money))
            }
            let row1 = card.type == 2 ? top + 564 : top + 532
            let row2 = card.type == 2 ? top + 640 : top + 614
            list.append(Field(key: p + "l1", def: text[4],
                              rect: CGRect(x: 78, y: row1 - 46, width: 282 - 78, height: 56),
                              x: 85, baseline: row1, size: 45,
                              color: Draw.color(0xff999999), align: .left, kind: .plain))
            list.append(Field(key: p + "v1", def: text[5],
                              rect: CGRect(x: 290, y: row1 - 50, width: 1214 - 290, height: 64),
                              x: 297, baseline: row1, size: 46,
                              color: Draw.color(0xff666666), align: .left, kind: .plain))
            if !text[6].isEmpty {
                list.append(Field(key: p + "l2", def: text[6],
                                  rect: CGRect(x: 78, y: row2 - 46, width: 282 - 78, height: 56),
                                  x: 85, baseline: row2, size: 45,
                                  color: Draw.color(0xff999999), align: .left, kind: .plain))
                list.append(Field(key: p + "v2", def: text[7],
                                  rect: CGRect(x: 290, y: row2 - 50, width: 1214 - 290, height: 64),
                                  x: 297, baseline: row2, size: 46,
                                  color: Draw.color(0xff666666), align: .left, kind: .plain))
            }
            if card.type == 2 {
                list.append(Field(key: p + "a1", def: text[8],
                                  rect: CGRect(x: 78, y: top + 726, width: 1146 - 78, height: 82),
                                  x: 85, baseline: top + 795, size: 50,
                                  color: Draw.color(0xff333333), align: .left, kind: .plain))
                list.append(Field(key: p + "a2", def: text[9],
                                  rect: CGRect(x: 78, y: top + 876, width: 1146 - 78, height: 82),
                                  x: 85, baseline: top + 944, size: 50,
                                  color: Draw.color(0xff333333), align: .left, kind: .plain))
            }
        }
        // 第 8 张卡片借的是第 7 张切图，时间那行不一样 → 永远自己画（Java:215-216）
        for i in list.indices where list[i].key == "c8.time" {
            list[i].force = true
        }
        fields = list
    }

    static func monthTitle() -> String {
        String(Calendar.current.component(.month, from: Date())) + "月统计支出"
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { max(0, Self.contentH - Self.H) }

    // MARK: - 绘制（Java:253-267）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // 顶栏切图（不跟着滚动）
        drawStrip(d, "pm_head", y: 136)

        d.ctx.saveGState()
        d.ctx.clip(to: CGRect(x: 0, y: Self.headBottom, width: Self.W, height: Self.H - Self.headBottom))
        d.ctx.translateBy(x: 0, y: -s.scroll)

        drawStrip(d, "pm_sum", y: 348)
        for (i, card) in Self.cards.enumerated() {
            drawStrip(d, Self.cardArt[i], y: card.top)
        }
        drawEdits(d, s)
        d.ctx.restoreGState()
    }

    /// 整宽切图（原始尺寸，不缩放）
    private func drawStrip(_ d: Draw, _ name: String, y: CGFloat) {
        guard let image = art[name] else { return }
        let width = image.size.width * image.scale
        let height = image.size.height * image.scale
        d.art(image, CGRect(x: 0, y: y, width: width, height: height))
    }

    /// 只画用户改过（或 force）的字段（Java:269-289）
    private func drawEdits(_ d: Draw, _ s: State) {
        for f in fields {
            let changed = s.texts[f.key]
            if changed == nil && !f.force { continue }
            let text = changed ?? f.def
            switch f.kind {
            case .money:
                drawMoney(d, f, text)
            case .chip:
                drawChip(d, f, text)
            case .plain:
                d.rect(f.rect, card)
                let size = fit(text, f.size, f.rect.width)
                drawAligned(d, text, f, size: size)
            }
        }
    }

    /// 金额：小号「¥」+ 大号数字，整体居中（Java:292-302）
    private func drawMoney(_ d: Draw, _ f: Field, _ text: String) {
        d.rect(f.rect, card)
        let yuanRef = Draw.width("¥", size: 72)
        let size = fit(text, f.size, f.rect.width - yuanRef - 18)
        let yuanSize = min(72, size * 72 / f.size)
        let yuan = Draw.width("¥", size: yuanSize)
        let digits = Draw.width(text, size: size)
        let start = center - (yuan + 18 + digits) / 2
        d.text("¥", x: start, baseline: f.baseline, size: yuanSize, ink)
        d.text(text, x: start + yuan + 18, baseline: f.baseline, size: size, ink)
    }

    /// 浅蓝胶囊（Java:312-324）
    private func drawChip(_ d: Draw, _ f: Field, _ text: String) {
        d.rect(f.rect, card)
        let size = fit(text, f.size, 760)
        let half = Draw.width(text, size: size) / 2 + 22
        let top = f.baseline - 71
        let bottom = f.baseline + 23
        d.round(CGRect(x: center - half, y: top, width: half * 2, height: bottom - top),
                radius: (bottom - top) / 2, chipBg)
        d.centerText(text, cx: center, baseline: f.baseline, size: size, blue)
    }

    /// 字号收到能塞进 max（最低 24），对应 Java 的 `fit()`（Java:305-309）
    private func fit(_ text: String, _ size: CGFloat, _ max: CGFloat) -> CGFloat {
        var s = size
        while s > 24 && Draw.width(text, size: s) > max { s -= 1 }
        return s
    }

    private func drawAligned(_ d: Draw, _ text: String, _ f: Field, size: CGFloat) {
        switch f.align {
        case .left: d.text(text, x: f.x, baseline: f.baseline, size: size, f.color)
        case .center: d.centerText(text, cx: f.x, baseline: f.baseline, size: size, f.color)
        case .right: d.rightText(text, right: f.x, baseline: f.baseline, size: size, f.color)
        }
    }

    // MARK: - 命中（Java:363-388：字段优先、后登记的优先；再退到卡片）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if y < Self.headBottom {
            if x < 140 { return "back" }
            return "noop"
        }
        let cy = y + s.scroll
        for f in fields.reversed() where f.rect.contains(CGPoint(x: x, y: cy)) {
            return "edit:" + f.key
        }
        for (i, card) in Self.cards.enumerated()
        where cy >= card.top && cy <= card.top + card.height && x >= cardL && x <= cardR {
            return "detail:\(i)"
        }
        return "noop"
    }
}
