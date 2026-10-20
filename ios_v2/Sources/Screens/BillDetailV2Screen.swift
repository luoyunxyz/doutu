import UIKit

/// 「账单详情」页 —— `features/zhi/ui/BillDetailV2Screen.java` 的 1:1 移植（画布 1280×2816，头栏固定 290）。
///
/// 两张卡：
///   1. **详情卡**：居中的商户标识（真 logo 或纯色圆盘 + 字母标）、收款方、金额（104px 粗体）、
///      状态行，下面是一串「标签 → 值」行。**自动扣款那类多两处**：多一行「管理免密支付」、
///      少一行「收款方全称」、且行整体下移 170。
///   2. **管理卡**：整张切图 + 三处右对齐的值（分类 / 标签 / 备注）+ 一个开关。
///
/// 卡片高度是**按内容算出来的**：商品说明超宽会折成多行（每行 64px），卡片随之变高 —— 与 Java 的
/// `layout()` 一致，所以 `maxScroll` 也跟着变。
final class BillDetailV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let barH: CGFloat = 290

    final class State: ScreenState {
        /// 要显示的那条账单（由调用方塞进来）
        var bill: Bill?
    }

    // MARK: - 几何与调色板（Java:34-68）
    private let cardL: CGFloat = 40
    private let cardR: CGFloat = 1240
    private let cardRadius: CGFloat = 46
    private let c1Top: CGFloat = 326
    private let markCy: CGFloat = 140
    private let markR: CGFloat = 67
    private let nameBase: CGFloat = 298
    private let amountBase: CGFloat = 438
    private let statusBase: CGFloat = 536
    private let rowsTop: CGFloat = 610
    private let rowH: CGFloat = 109
    private let labelX: CGFloat = 82
    private let valueX: CGFloat = 402
    private let valueRight: CGFloat = 1195
    private let cardGap: CGFloat = 26
    private let mTitle: CGFloat = 150
    private let mRow: CGFloat = 138
    private let mBook: CGFloat = 160
    private let mRule: CGFloat = 18
    private let mAction: CGFloat = 116

    private let pageBg = Draw.color(0xfff5f5f5)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff303030)
    private let grey = Draw.color(0xffa5a5a5)
    private let blue = Draw.color(0xff1677ff)
    private let divider = Draw.color(0xfff0f1f5)

    private var manageArt: UIImage?
    private var switchArt: UIImage?
    private var backArt: UIImage?
    private var catArt: UIImage?
    private var fishArt: UIImage?

    /// 布局结果
    private var card1Bottom: CGFloat = 0
    private var card2Top: CGFloat = 0
    private var card2Bottom: CGFloat = 0
    private var contentH: CGFloat = 0
    private var goodsLines: CGFloat = 1
    private var automatic = false

    init() {
        manageArt = UIImage(named: "bill_art_manage")
        switchArt = UIImage(named: "bill_art_switch_on")
        backArt = UIImage(named: "bill_art_back_detail")
        catArt = UIImage(named: "bill_art_cat_detail")
        fishArt = UIImage(named: "bill_art_xianyu_detail")
    }

    private var labels: [String] {
        automatic
            ? ["支付时间", "付款方式", "商品说明", "支付奖励", "订单号", "商家订单号"]
            : ["支付时间", "付款方式", "商品说明", "支付奖励", "收款方全称", "订单号", "商家订单号"]
    }

    private func rowsStart() -> CGFloat { rowsTop + (automatic ? 170 : 0) }

    private func rowOffset(_ index: Int) -> CGFloat {
        CGFloat(index) * rowH
            + (index >= 3 ? (goodsLines - 1) * 64 : 0)
            + (automatic && index >= 2 ? 90 : 0)
    }

    /// 算这一帧两张卡的高度（Java:107-116）
    private func layout(_ bill: Bill?) {
        automatic = bill?.status == "自动扣款成功"
        if let bill {
            goodsLines = max(1, CGFloat(wrap(bill.goods, size: 46, width: valueRight - valueX).count))
        } else {
            goodsLines = 1
        }
        card1Bottom = c1Top + rowsStart() + CGFloat(labels.count) * rowH
            + (goodsLines - 1) * 64 + (automatic ? 90 : 0) + 60
        card2Top = card1Bottom + cardGap
        card2Bottom = card2Top + 1152
        contentH = card2Bottom + 75
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        layout(s.bill)
        return max(0, contentH + 40 - Self.H)
    }

    // MARK: - 绘制（Java:122-132）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)
        guard let bill = s.bill else { return }
        layout(bill)

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)
        drawDetailCard(d, bill)
        drawManageCard(d, bill)
        d.ctx.restoreGState()

        drawBar(d)
    }

    /// 固定头栏（Java:134-141）
    private func drawBar(_ d: Draw) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.barH), pageBg)
        if let art = backArt {
            d.art(art, CGRect(x: 42, y: 180, width: art.size.width * art.scale,
                              height: art.size.height * art.scale))
        }
        d.text("账单详情", x: 134, baseline: 244, size: 62, ink)
    }

    /// 详情卡（Java:143-187）
    private func drawDetailCard(_ d: Draw, _ bill: Bill) {
        d.round(CGRect(x: cardL, y: c1Top, width: cardR - cardL, height: card1Bottom - c1Top),
                radius: cardRadius, cardColor)

        let cy = c1Top + markCy
        let icon: UIImage?
        if bill.mark == "云猫" { icon = catArt }
        else if bill.mark == "闲鱼" { icon = fishArt }
        else if let logo = BillLogos.image(for: bill.merchant, mark: bill.mark) { icon = logo }
        else { icon = nil }

        if let icon {
            let iw = icon.size.width * icon.scale
            let ih = icon.size.height * icon.scale
            let scale = 2 * markR / max(iw, ih)
            let w = iw * scale
            let h = ih * scale
            d.art(icon, CGRect(x: Self.W / 2 - w / 2, y: cy - h / 2, width: w, height: h))
        } else {
            d.circle(cx: Self.W / 2, cy: cy, r: markR, Draw.color(bill.markColor))
            d.centerText(bill.mark, cx: Self.W / 2, baseline: cy + 18, size: 44,
                         Draw.color(bill.markInk), bold: true)
        }

        d.centerText(bill.payee, cx: Self.W / 2, baseline: c1Top + nameBase, size: 46, ink)
        d.centerText((bill.income ? "+" : "-") + Self.money(abs(bill.amount)),
                     cx: Self.W / 2, baseline: c1Top + amountBase, size: 104, ink, bold: true)
        d.centerText(bill.status.isEmpty ? "交易成功" : bill.status, cx: Self.W / 2,
                     baseline: c1Top + statusBase, size: 46, ink)

        if automatic {
            d.text("管理免密支付", x: labelX, baseline: c1Top + rowsTop + 70, size: 44, grey)
            d.text("闲鱼100元以下免密支付", x: valueX, baseline: c1Top + rowsTop + 70, size: 44,
                   Draw.color(0xff245b85))
            d.rect(CGRect(x: labelX, y: c1Top + rowsTop + 130,
                          width: valueRight - labelX, height: 2), Draw.color(0xffeeeeee))
        }

        let values: [String]
        if automatic {
            values = [Self.fullTime(bill.time), bill.payMethod, bill.goods, bill.reward,
                      bill.orderNo, bill.merchantOrderNo]
        } else {
            values = [Self.fullTime(bill.time), bill.payMethod, bill.goods, bill.reward,
                      bill.payee, bill.orderNo, bill.merchantOrderNo]
        }

        for (i, label) in labels.enumerated() {
            let top = c1Top + rowsStart() + rowOffset(i)
            d.text(label, x: labelX, baseline: top + 74, size: 44, grey)
            var baseline = top + 74
            if i == 2 {
                for line in wrap(values[i], size: 46, width: valueRight - valueX) {
                    d.text(line, x: valueX, baseline: baseline, size: 46, ink)
                    baseline += 64
                }
            } else {
                let value = values[i]
                var size: CGFloat = 46
                let natural = Draw.width(value, size: size)
                if natural > valueRight - valueX {
                    size *= (valueRight - valueX) / natural
                }
                d.text(value, x: valueX, baseline: baseline, size: size, ink)
                if i == 1 || i == 3 {
                    let chevX = min(valueRight, valueX + Draw.width(value, size: size) + 35)
                    chevron(d, x: chevX, cy: top + 57, color: Draw.color(0xffb4b4b4))
                }
            }
            if automatic && i == 1 {
                d.text("查看扣款顺序", x: valueX, baseline: top + 154, size: 44,
                       Draw.color(0xff245b85))
            }
        }
    }

    /// 管理卡（Java:192-200）
    private func drawManageCard(_ d: Draw, _ bill: Bill) {
        if let art = manageArt {
            d.art(art, CGRect(x: cardL, y: card2Top, width: art.size.width * art.scale,
                              height: art.size.height * art.scale))
        }
        manageValue(d, bill.billCategory, baseline: card2Top + 240)
        manageValue(d, bill.tag.isEmpty ? "请选择" : bill.tag, baseline: card2Top + 378)
        manageValue(d, bill.note.isEmpty ? "添加" : bill.note, baseline: card2Top + 809)

        // 开关：开着的用切图，关着用自绘（与 Java 一致）
        if bill.billCategory.isEmpty == false, let art = switchArt {
            d.art(art, CGRect(x: 1096, y: card2Top + 622, width: art.size.width * art.scale,
                              height: art.size.height * art.scale))
        }
        DrawSwitchV2.mark(d, cx: 1150, cy: card2Top + 661, on: false)
    }

    /// 只清「值」那一栏再重画（Java:202-216）
    private func manageValue(_ d: Draw, _ value: String, baseline: CGFloat) {
        d.rect(CGRect(x: 690, y: baseline - 54, width: 1158 - 690, height: 66), .white)
        var clipped = value
        while !clipped.isEmpty && Draw.width(clipped, size: 46) > 440 {
            clipped.removeLast()
        }
        if clipped != value {
            while !clipped.isEmpty && Draw.width(clipped + "…", size: 46) > 440 {
                clipped.removeLast()
            }
            clipped += "…"
        }
        d.rightText(clipped, right: 1144, baseline: baseline, size: 46, grey)
    }

    // MARK: - 工具

    /// 品牌图标（与 BillScreen 共用同一张表）
    private static func logo(_ mark: String) -> UIImage? {
        let names: [String: String] = [
            "云猫": "bill_logo_cat", "花呗": "bill_logo_huabei", "闲鱼": "bill_logo_xianyu",
            "移动": "bill_logo_mobile", "公交": "bill_logo_bus", "交管": "bill_logo_traffic",
            "T": "bill_logo_token", "抖音": "bill_logo_douyin",
        ]
        guard let res = names[mark] else { return nil }
        return UIImage(named: res)
    }

    /// 按宽度折行（Java:301-315）
    private func wrap(_ value: String, size: CGFloat, width: CGFloat) -> [String] {
        var lines: [String] = []
        var line = ""
        for ch in value {
            if Draw.width(line + String(ch), size: size) > width && !line.isEmpty {
                lines.append(line)
                line = ""
            }
            line.append(ch)
        }
        if !line.isEmpty { lines.append(line) }
        return lines
    }

    private func chevron(_ d: Draw, x: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(4)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: x, y: cy - 14))
        d.ctx.addLine(to: CGPoint(x: x + 18, y: cy))
        d.ctx.addLine(to: CGPoint(x: x, y: cy + 14))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 金额格式（Java 的 `%,.2f`）
    static func money(_ value: Double) -> String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f.groupingSeparator = ","
        f.locale = Locale(identifier: "en_US_POSIX")
        return f.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }

    /// `yyyy-MM-dd HH:mm:ss`（Java 的 `full`）
    static func fullTime(_ when: TimeInterval) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.string(from: Date(timeIntervalSince1970: when))
    }

    /// 命中（Java:243-292）
    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        layout(s.bill)
        // 顶栏固定（屏幕坐标）；卡片内容用宿主传入的内容坐标。
        let rawY = y - s.scroll
        let cy = y
        if rawY < Self.barH { return x < 130 ? "back" : "noop" }
        guard let bill = s.bill else { return "noop" }

        if cy >= c1Top && cy <= card1Bottom {
            let local = cy - c1Top
            if local < 230 { return "icon" }
            if local >= 230 && local <= 335 { return "edit:payee" }
            if local >= amountBase - 70 && local <= amountBase + 30 { return "edit:amount" }
            for i in 0..<labels.count {
                let top = rowsStart() + rowOffset(i)
                let h = rowH + (i == 2 ? (goodsLines - 1) * 64 : 0) + (automatic && i == 1 ? 90 : 0)
                if local >= top - 12 && local < top + h {
                    switch i {
                    case 0: return "edit:time"
                    case 2: return "edit:goods"
                    case 1: return "edit:payMethod"
                    case 3: return "edit:reward"
                    case 4: return automatic ? "edit:orderNo" : "edit:payee"
                    case 5: return automatic ? "edit:merchantOrderNo" : "edit:orderNo"
                    case 6: return "edit:merchantOrderNo"
                    default: return "noop"
                    }
                }
            }
            return "noop"
        }

        if cy >= card2Top && cy <= card2Bottom {
            let local = cy - card2Top
            var y2 = mTitle
            if local < y2 { return "noop" }
            if local < y2 + mRow { return "edit:category" }
            y2 += mRow
            if local < y2 + mRow { return "edit:tag" }
            y2 += mRow
            if local < y2 + mBook { return "noop" }
            y2 += mBook
            if local < y2 + mRow { return "toggle:counted" }
            y2 += mRow
            if local < y2 + mRow { return "edit:note" }
            y2 += mRow + mRule
            if local < y2 + mAction {
                return "toast:" + (x < Self.W / 2 ? "查看往来记录" : "往来流水证明")
            }
            return "toast:" + (x < Self.W / 2 ? "申请电子回单" : "对此订单有疑问")
        }
        return "noop"
    }
}

// MARK: - 可编辑（账单详情每一行都能单独点开改，共 11 个字段）

extension BillDetailV2Screen.State: EditableState {
    func editTitle(_ key: String) -> String {
        switch key {
        case "payee": return "编辑收款方"
        case "amount": return "编辑金额"
        case "time": return "编辑交易时间（yyyy-MM-dd HH:mm:ss）"
        case "goods": return "编辑商品说明"
        case "payMethod": return "编辑付款方式"
        case "reward": return "编辑奖励"
        case "orderNo": return "编辑订单号"
        case "merchantOrderNo": return "编辑商户订单号"
        case "category": return "编辑账单分类"
        case "tag": return "编辑标签"
        case "note": return "编辑备注"
        default: return "编辑"
        }
    }

    func editValue(_ key: String) -> String {
        guard let bill else { return "" }
        switch key {
        case "payee": return bill.payee
        case "amount": return BillDetailV2Screen.money(abs(bill.amount))
        case "time": return BillDetailV2Screen.fullTime(bill.time)
        case "goods": return bill.goods
        case "payMethod": return bill.payMethod
        case "reward": return bill.reward
        case "orderNo": return bill.orderNo
        case "merchantOrderNo": return bill.merchantOrderNo
        case "category": return bill.billCategory
        case "tag": return bill.tag
        case "note": return bill.note
        default: return ""
        }
    }

    func applyEdit(_ key: String, _ text: String) {
        guard let bill else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch key {
        case "payee": bill.payee = trimmed
        case "amount":
            // 只取数字与小数点；空的就不动
            var digits = ""
            for ch in trimmed where ch.isNumber || ch == "." { digits.append(ch) }
            if let value = Double(digits) {
                // 支出保持负数（Java 用符号区分支出/进账）
                bill.amount = bill.amount < 0 ? -value : value
            }
        case "time":
            for format in ["yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"] {
                let formatter = DateFormatter()
                formatter.locale = Locale(identifier: "en_US_POSIX")
                formatter.dateFormat = format
                if let date = formatter.date(from: trimmed) {
                    bill.time = date.timeIntervalSince1970
                    break
                }
            }
        case "goods": bill.goods = trimmed
        case "payMethod": bill.payMethod = trimmed
        case "reward": bill.reward = trimmed
        case "orderNo": bill.orderNo = trimmed
        case "merchantOrderNo": bill.merchantOrderNo = trimmed
        case "category": bill.billCategory = trimmed
        case "tag": bill.tag = trimmed
        case "note": bill.note = trimmed
        default: break
        }
    }
}

/// 开关的两种画法（开：蓝底白钮在右；关：灰底白钮在左）—— 供账单详情等页面复用
enum DrawSwitchV2 {
    static func mark(_ d: Draw, cx: CGFloat, cy: CGFloat, on: Bool) {
        d.round(CGRect(x: cx - 56, y: cy - 30, width: 112, height: 60), radius: 30,
                on ? Draw.color(0xff1677ff) : Draw.color(0xffd7dbe3))
        d.circle(cx: on ? cx + 26 : cx - 26, cy: cy, r: 24, .white)
    }
}
