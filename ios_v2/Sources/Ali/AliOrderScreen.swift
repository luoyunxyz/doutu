import UIKit

/// 「支付宝 → 订单」—— `AliOrderActivity`（265 行）的移植。
///
/// 78dp 顶栏（`‹` + 22sp「卖家已发货」+ 12sp 倒计时 + 右侧 `···` 与橙色角标 13）
/// 下面是三张白色圆角卡（半径 10、内边距 14、卡间距 10）：
///   1. **物流卡**：橙色「● 运输中 20:42 ›」+ 四段文案；
///   2. **商品卡**：店铺行 → 80dp 灰底商品图 + 标题/规格/运费险 + 价格 →
///      「加入购物车 / 退款」胶囊 → 实付款 → 订单编号/交易快照/花呗账单 → 「查看更多 ⌄」；
///   3. **订单服务卡**：标题 + 运费险行 + 「延长收货 / 查看物流 / 确认收货」三个胶囊。
final class AliOrderScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388
    static let barH = dp(78)

    private let pageBg = Draw.color(0xfff5f5f5)
    private let ink = Draw.color(0xff333333)
    private let dark = Draw.color(0xff1a1a1a)
    private let sub = Draw.color(0xff888888)
    private let orange = Draw.color(0xffff5000)
    private let arrowGrey = Draw.color(0xffbbbbbb)
    private let pillBorder = Draw.color(0xffdddddd)
    private let pillFill = Draw.color(0xfffff1e6)
    private let cardPad = dp(14)
    private let cardGap = dp(10)

    final class State: ScreenState { }

    // MARK: - 行模型

    private enum ItemKind {
        case status
        case para(String, CGFloat, UIColor)
        case shop
        case item
        case buttons
        case paid
        case kv(String, String)
        case more
        case serviceTitle
        case serviceRow
        case serviceActions
    }

    private struct Item {
        let kind: ItemKind
        let rect: CGRect
    }

    private struct Layout {
        var cards: [CGRect] = []
        var items: [Item] = []
        var contentHeight: CGFloat = 0
    }

    // MARK: - 布局

    private func wrap(_ text: String, maxWidth: CGFloat, size: CGFloat) -> [String] {
        var lines: [String] = []
        var current = ""
        for character in text {
            let next = current + String(character)
            if Draw.width(next, size: size) > maxWidth, !current.isEmpty {
                lines.append(current)
                current = String(character)
            } else {
                current = next
            }
        }
        if !current.isEmpty { lines.append(current) }
        return lines.isEmpty ? [""] : lines
    }

    private func paraHeight(_ text: String, size: CGFloat, width: CGFloat) -> CGFloat {
        let lines = wrap(text, maxWidth: width, size: size).count
        return Draw.font(size: size).lineHeight * CGFloat(lines) + dp(4) * CGFloat(max(0, lines - 1))
            + dp(6) * 2
    }

    private func layout() -> Layout {
        var out = Layout()
        let cardLeft = dp(12)
        let cardW = Self.W - cardLeft * 2
        let innerW = cardW - cardPad * 2
        let innerX = cardLeft + cardPad
        var y = Self.barH + dp(4)

        // ---- 卡一：物流 ----
        var cardTop = y
        var cy = y + cardPad
        out.items.append(Item(kind: .status,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(22))))
        cy += dp(22)
        let paras: [(String, CGFloat, UIColor)] = [
            ("【池州市】快件已由【三二四便利店】签收，如有疑问请电联派件业务员：18356653050，签…",
             dp(14), ink),
            ("送至 东湖中路269号胜利新村6号楼269号胜利新村6号楼4401", dp(15), dark),
            ("肆意 86-152****1201  号码保护中", dp(13), sub),
            ("取件可出示虚拟号 86-152****1201", dp(13), sub),
        ]
        for (text, size, color) in paras {
            let h = paraHeight(text, size: size, width: innerW)
            out.items.append(Item(kind: .para(text, size, color),
                                  rect: CGRect(x: innerX, y: cy, width: innerW, height: h)))
            cy += h
        }
        cy += cardPad
        out.cards.append(CGRect(x: cardLeft, y: cardTop, width: cardW, height: cy - cardTop))
        y = cy + cardGap

        // ---- 卡二：商品 ----
        cardTop = y
        cy = y + cardPad
        out.items.append(Item(kind: .shop,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(21))))
        cy += dp(21) + dp(10)
        out.items.append(Item(kind: .item,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(80))))
        cy += dp(80) + dp(12)
        out.items.append(Item(kind: .buttons,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(34))))
        cy += dp(34) + dp(14)
        out.items.append(Item(kind: .paid,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(26))))
        cy += dp(26)
        for (name, value) in [("订单编号:", "3555369936238415557 | 复制"),
                              ("交易快照", "发生交易争议时，可作为判断依据 ›"),
                              ("花呗账单:", "去支付宝中查看 ›")] {
            out.items.append(Item(kind: .kv(name, value),
                                  rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(36))))
            cy += dp(36)
        }
        out.items.append(Item(kind: .more,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(30))))
        cy += dp(30)
        cy += cardPad
        out.cards.append(CGRect(x: cardLeft, y: cardTop, width: cardW, height: cy - cardTop))
        y = cy + cardGap

        // ---- 卡三：订单服务 ----
        cardTop = y
        cy = y + cardPad
        out.items.append(Item(kind: .serviceTitle,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(22))))
        cy += dp(22)
        out.items.append(Item(kind: .serviceRow,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(36))))
        cy += dp(36) + dp(12)
        out.items.append(Item(kind: .serviceActions,
                              rect: CGRect(x: innerX, y: cy, width: innerW, height: dp(34))))
        cy += dp(34)
        cy += cardPad
        out.cards.append(CGRect(x: cardLeft, y: cardTop, width: cardW, height: cy - cardTop))
        y = cy + dp(16)

        out.contentHeight = y
        return out
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, layout().contentHeight - Self.H)
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let scroll = s.scroll
        let laid = layout()

        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), pageBg)
        for card in laid.cards {
            d.round(card, radius: dp(10), .white)
        }
        for item in laid.items {
            drawItem(d, item: item)
        }
        drawBar(d, scroll: scroll)
    }

    private func drawItem(_ d: Draw, item: Item) {
        switch item.kind {
        case .status:
            let dotSize = dp(10)
            d.text("●", x: item.rect.minX, baseline: item.rect.minY + dotSize,
                   size: dotSize, orange)
            let size = dp(16)
            d.text("运输中 20:42", x: item.rect.minX + dp(6) + dotSize,
                   baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                   size: size, orange)
            d.rightText("›", right: item.rect.maxX,
                        baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                        size: size, arrowGrey)
        case let .para(text, size, color):
            var baseline = item.rect.minY + dp(6) + Draw.font(size: size).ascender
            for line in wrap(text, maxWidth: item.rect.width, size: size) {
                d.text(line, x: item.rect.minX, baseline: baseline, size: size, color)
                baseline += Draw.font(size: size).lineHeight + dp(4)
            }
        case .shop:
            let size = dp(15)
            d.text("天猫  周小二美妆 ›", x: item.rect.minX,
                   baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                   size: size, ink)
        case .item:
            let image = CGRect(x: item.rect.minX, y: item.rect.minY,
                               width: dp(80), height: dp(80))
            d.round(image, radius: dp(6), Draw.color(0xffe0e0e0))
            let infoX = image.maxX + dp(10)
            let titleSize = dp(15)
            d.text("pony王菲菲同款270遮瑕刷无…", x: infoX,
                   baseline: item.rect.minY + Draw.font(size: titleSize).ascender,
                   size: titleSize, dark)
            let specSize = dp(13)
            d.text("270遮瑕刷;人造纤维", x: infoX,
                   baseline: item.rect.minY + dp(26) + Draw.font(size: specSize).ascender,
                   size: specSize, sub)
            let tagSize = dp(12)
            d.text("运费险", x: infoX,
                   baseline: item.rect.minY + dp(50) + Draw.font(size: tagSize).ascender,
                   size: tagSize, orange)
            d.rightText("¥9.90", right: item.rect.maxX,
                        baseline: item.rect.minY + Draw.font(size: titleSize).ascender,
                        size: titleSize, dark)
            d.rightText("x1", right: item.rect.maxX,
                        baseline: item.rect.minY + dp(26) + Draw.font(size: tagSize).ascender,
                        size: tagSize, sub)
        case .buttons:
            // 右对齐两个胶囊
            var right = item.rect.maxX
            for label in ["退款", "加入购物车"] {
                let width = Draw.width(label, size: dp(13)) + dp(28)
                let rect = CGRect(x: right - width, y: item.rect.minY,
                                  width: width, height: dp(34))
                drawPill(d, rect: rect, label: label, filled: false)
                right = rect.minX - dp(10)
            }
        case .paid:
            let size = dp(15)
            d.text("实付款", x: item.rect.minX,
                   baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                   size: size, ink)
            let priceSize = dp(19)
            d.rightText("¥10.90", right: item.rect.maxX,
                        baseline: item.rect.midY + Draw.font(size: priceSize).ascender / 2 - priceSize * 0.06,
                        size: priceSize, orange)
        case let .kv(name, value):
            let nameSize = dp(14)
            let valueSize = dp(13)
            d.text(name, x: item.rect.minX,
                   baseline: item.rect.midY + Draw.font(size: nameSize).ascender / 2 - nameSize * 0.06,
                   size: nameSize, ink)
            d.rightText(value, right: item.rect.maxX,
                        baseline: item.rect.midY + Draw.font(size: valueSize).ascender / 2 - valueSize * 0.06,
                        size: valueSize, sub)
        case .more:
            let size = dp(13)
            d.text("查看更多 ⌄", x: item.rect.minX,
                   baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                   size: size, sub)
        case .serviceTitle:
            let size = dp(16)
            d.text("订单服务 保障中", x: item.rect.minX,
                   baseline: item.rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                   size: size, dark)
        case .serviceRow:
            let nameSize = dp(14)
            let valueSize = dp(13)
            d.text("运费险", x: item.rect.minX,
                   baseline: item.rect.midY + Draw.font(size: nameSize).ascender / 2 - nameSize * 0.06,
                   size: nameSize, ink)
            d.rightText("七天无理由", right: item.rect.maxX,
                        baseline: item.rect.midY + Draw.font(size: valueSize).ascender / 2 - valueSize * 0.06,
                        size: valueSize, sub)
        case .serviceActions:
            var right = item.rect.maxX
            let specs: [(String, Bool)] = [("确认收货", true), ("查看物流", false), ("延长收货", false)]
            for (label, filled) in specs {
                let width = Draw.width(label, size: dp(13)) + dp(28)
                let rect = CGRect(x: right - width, y: item.rect.minY,
                                  width: width, height: dp(34))
                drawPill(d, rect: rect, label: label, filled: filled)
                right = rect.minX - dp(8)
            }
        }
    }

    private func drawPill(_ d: Draw, rect: CGRect, label: String, filled: Bool) {
        d.round(rect, radius: dp(17), filled ? pillFill : .white)
        d.roundStroke(rect, radius: dp(17), filled ? orange : pillBorder, width: dp(1))
        let size = dp(13)
        d.centerText(label, cx: rect.midX,
                     baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                     size: size, filled ? orange : ink)
    }

    private func drawBar(_ d: Draw, scroll: CGFloat) {
        let bar = CGRect(x: 0, y: scroll, width: Self.W, height: Self.barH)
        d.rect(bar, pageBg)
        let backSize = dp(28)
        d.centerText("‹", cx: dp(24),
                     baseline: bar.minY + bar.height / 2 + Draw.font(size: backSize).ascender / 2 - backSize * 0.06,
                     size: backSize, ink)

        let titleSize = dp(22)
        d.text("卖家已发货", x: dp(46),
               baseline: bar.minY + dp(10) + Draw.font(size: titleSize).ascender,
               size: titleSize, dark)
        let subSize = dp(12)
        d.text("还剩6天2时自动确认", x: dp(46),
               baseline: bar.minY + dp(10) + Draw.font(size: titleSize).lineHeight
                  + Draw.font(size: subSize).ascender,
               size: subSize, sub)

        let moreSize = dp(20)
        d.centerText("···", cx: Self.W - dp(22),
                     baseline: bar.minY + bar.height / 2 + Draw.font(size: moreSize).ascender / 2 - moreSize * 0.06,
                     size: moreSize, ink)

        let badge = CGRect(x: Self.W - dp(4) - dp(22), y: bar.minY + dp(14),
                           width: dp(22), height: dp(22))
        d.circle(cx: badge.midX, cy: badge.midY, r: dp(11), Draw.color(0xffff6a00))
        let badgeSize = dp(11)
        d.centerText("13", cx: badge.midX,
                     baseline: badge.midY + Draw.font(size: badgeSize).ascender / 2 - badgeSize * 0.06,
                     size: badgeSize, .white)
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        if y >= s.scroll && y < s.scroll + Self.barH {
            if x < dp(48) { return "ali:back" }
            if x > Self.W - dp(44) { return "ali:order:more" }
            return "noop"
        }
        for item in layout().items where item.rect.contains(CGPoint(x: x, y: y)) {
            switch item.kind {
            case .buttons: return "ali:order:buttons"
            case .serviceActions: return "ali:order:service"
            case .kv: return "ali:order:row"
            case .more: return "ali:order:more"
            case .status: return "ali:order:logistics"
            default: return "noop"
            }
        }
        return "noop"
    }
}
