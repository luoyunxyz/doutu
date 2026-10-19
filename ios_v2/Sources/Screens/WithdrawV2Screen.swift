import UIKit

/// 「余额提现」页 —— `features/zhi/ui/WithdrawV2Screen.java` 的 1:1 移植（画布 1280×2816）。
///
/// 三层结构：
///   1. **页面**：头栏 → 到账银行卡行 → 提现金额卡（含可用余额/剩余额度/红色提示）→ 去…服务卡 → 提现按钮
///   2. **金额键盘**（`keypad`）：浅灰面板 + 白圆角键，四列四行，右下角蓝的「提现」键
///   3. **支付密码**（`password`）：压暗上半屏 + 白面板（标题/副标题/六格/忘记密码）+ 全白密码键盘
///
/// 两个键盘**几何完全不同**，Java 里特意分开写：
///   · 金额键盘：(20.4/333/643.7/954.4) 四列，行基线 1988.1 起，每行 170 高
///   · 密码键盘：三列四行铺满 2030..2752.9，只在 1/3、2/3 处画极浅竖线，最后一排「0 在中间列、⌫ 在右列」
final class WithdrawV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816
    static let panelTop: CGFloat = 1830

    final class State: ScreenState {
        /// 输入框里的数字（字符串，保留用户敲的样子）
        var amount = ""
        var keypad = false
        var password = false
        var pin = ""
        /// 银行卡选择层（点「到账银行卡」弹出，对应 Android 的 `BankSheet`）
        var bankSheet = false
    }

    private let squeeze: CGFloat = 0.94

    // MARK: - 调色板（Java:45-57）
    private let pageBg = Draw.color(0xfff2f3f7)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff2a2c30)
    private let caption = Draw.color(0xff9b9ca0)
    private let faint = Draw.color(0xffa8acb4)
    private let rule2 = Draw.color(0xffeeeeee)
    private let panel = Draw.color(0xfff6f7fb)
    private let keyColor = Draw.color(0xffffffff)
    private let digitInk = Draw.color(0xff303030)
    private let chevronColor = Draw.color(0xffb9bcc2)
    private let actionOn = Draw.color(0xff1677ff)
    private let actionOff = Draw.color(0xffa4c8fa)

    // MARK: - 几何（Java:60-186）
    private let backRect = CGRect(x: 49.2, y: 190.2, width: 84.4 - 49.2, height: 250.5 - 190.2)
    private let titleX: CGFloat = 135
    private let titleBase: CGFloat = 245.1
    private let titleSize: CGFloat = 68
    private let dotsX: [CGFloat] = [1175.7, 1197.9, 1220.2]
    private let dotsY: CGFloat = 219.6
    private let dotsR: CGFloat = 8.8

    private let labelX: CGFloat = 82
    private let labelBase: CGFloat = 437.6
    private let labelSize: CGFloat = 54
    private let bankIcon = CGRect(x: 81.7, y: 489.3, width: 142.0 - 81.7, height: 549.6 - 489.3)
    private let bankNameX: CGFloat = 171
    private let bankNameBase: CGFloat = 537.1
    private let bankNameSize: CGFloat = 54.5
    private let bankSubBase: CGFloat = 608.6
    private let bankSubSize: CGFloat = 47
    private let bankChevron = CGRect(x: 1156.8, y: 503.2, width: 1178.1 - 1156.8, height: 535.7 - 503.2)

    private let card1 = CGRect(x: 40.8, y: 707.2, width: 1238.3 - 40.8, height: 1310.1 - 707.2)
    private let cardRadius: CGFloat = 47
    private let card1TitleX: CGFloat = 82
    private let card1TitleBase: CGFloat = 794.6
    private let card1TitleSize: CGFloat = 55
    private let amountX: CGFloat = 80
    private let amountBase: CGFloat = 1105
    private let amountSize: CGFloat = 98
    private let amountValueX: CGFloat = 193
    private let amountValueSize: CGFloat = 134
    private let availRight: CGFloat = 1198
    private let availBase: CGFloat = 1094.5
    private let availSize: CGFloat = 47.5
    private let divider1Y: CGFloat = 1152.9
    private let dividerL: CGFloat = 84.6
    private let dividerR: CGFloat = 1194.4
    private let quotaX: CGFloat = 82
    private let quotaBase: CGFloat = 1251
    private let quotaSize: CGFloat = 54.5
    private let infoRect = CGRect(x: 672.6, y: 1204.7, width: 722.7 - 672.6, height: 1253.9 - 1204.7)
    private let unitCaretX: CGFloat = 191.5
    private let unitCaretW: CGFloat = 2.8
    private let unitCaretTop: CGFloat = 888
    private let unitCaretBottom: CGFloat = 950
    private let unitX: CGFloat = 210
    private let unitBase: CGFloat = 936
    private let unitSize: CGFloat = 53
    private let amountValueBase: CGFloat = 1099
    private let clearRect = CGRect(x: 1139, y: 1052.8, width: 1194.6 - 1139, height: 1108.4 - 1052.8)
    private let clearCross: CGFloat = 4.6
    private let clearDisc = Draw.color(0xffcecece)
    private let warnX: CGFloat = 84
    private let warnBase: CGFloat = 1244
    private let warnSize: CGFloat = 53.5
    private let warnRed = Draw.color(0xffe1483c)
    private let warnQuotaShift: CGFloat = 99
    private let warnCard1Shift: CGFloat = 101
    private let warnCard2Shift: CGFloat = 102.4

    private let card2 = CGRect(x: 40.8, y: 1337.9, width: 1238.3 - 40.8, height: 2008.5 - 1337.9)
    private let rowIcon: [CGRect] = [
        CGRect(x: 102.6, y: 1400.6, width: 196.3 - 102.6, height: 1494.3 - 1400.6),
        CGRect(x: 102.6, y: 1626.6, width: 196.3 - 102.6, height: 1720.3 - 1626.6),
        CGRect(x: 86.6, y: 1829.6, width: 213.7 - 86.6, height: 1968.8 - 1829.6),
    ]
    private let rowTextX: CGFloat = 253
    private let rowTitleSize: CGFloat = 54
    private let rowTitleBase: [CGFloat] = [1432.8, 1653.7, 1884.7]
    private let rowSubSize: CGFloat = 47
    private let rowSubBase: [CGFloat] = [1512.2, 1728.7, 1960.7]
    private let rowBadge: [CGRect?] = [
        CGRect(x: 784.9, y: 1387.9, width: 915.7 - 784.9, height: 1442.7 - 1387.9),
        nil,
        CGRect(x: 673.9, y: 1837.9, width: 858.5 - 673.9, height: 1891.7 - 1837.9),
    ]
    private let rowChevron: [CGRect] = [
        CGRect(x: 1179.5, y: 1431.4, width: 1197.3 - 1179.5, height: 1463.6 - 1431.4),
        CGRect(x: 1179.5, y: 1656.7, width: 1197.3 - 1179.5, height: 1689.0 - 1656.7),
        CGRect(x: 1179.5, y: 1882.3, width: 1197.3 - 1179.5, height: 1914.7 - 1882.3),
    ]
    private let rowRuleY: [CGFloat] = [1560.9, 1786.5]
    private let rowRuleL: CGFloat = 255.7
    private let rowRuleR: CGFloat = 1198
    private let rowTitle = ["余利宝收益率可达1.8%", "去查看你的卡账单", "去会员中心领积分"]
    private let rowSub = ["免费转出到银行卡，闲钱享收益", "点击查看详情", "领取支付宝积分可兑红包、生活用品"]

    private let button = CGRect(x: 52.9, y: 2515, width: 1226.2 - 52.9, height: 2683.8 - 2515)
    private let buttonRadius: CGFloat = 84.4
    private let buttonCx: CGFloat = 639.5
    private let buttonBase: CGFloat = 2622.9
    private let buttonSize: CGFloat = 62

    private let panelBottom: CGFloat = 2752.9
    private let keyL: [CGFloat] = [20.4, 333.0, 643.7, 954.4]
    private let keyR: [CGFloat] = [311.7, 624.2, 934.0, 1259.6]
    private let keyT: [CGFloat] = [1988.1, 2180.1, 2371.2, 2562.3]
    private let keyH: CGFloat = 170
    private let keyRadius: CGFloat = 20
    private let digitSize: CGFloat = 82
    private let digitDrop: CGFloat = 33
    private let dotDrop: CGFloat = 2
    private let dotShiftX: CGFloat = 8.6
    private let delRect = CGRect(x: 1059.3, y: 2037.3, width: 1154.8 - 1059.3, height: 2108.7 - 2037.3)
    private let actionRect = CGRect(x: 954.4, y: 2180.5, width: 1259.6 - 954.4, height: 2730 - 2180.5)
    private let actionTextSize: CGFloat = 61
    private let actionTextDrop: CGFloat = 33
    private let chevronDownRect = CGRect(x: 620.6, y: 1882.9, width: 668.8 - 620.6, height: 1911.6 - 1882.9)
    private let chevronStroke: CGFloat = 4.6
    private let chevronLeftStroke: CGFloat = 6.4
    private let keyLabel: [[String]] = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"]]

    // 支付密码那一层（Java:578-617）
    private let pinLength = 6
    private let pwPanel = CGRect(x: 124, y: 628, width: 1158 - 124, height: 1459 - 628)
    private let pwScale: CGFloat = 0.88
    private let pwRadius: CGFloat = 24
    private let pwClose = CGRect(x: 190, y: 683, width: 248 - 190, height: 741 - 683)
    private let pwCloseStroke: CGFloat = 4.6
    private let pwTitleBase: CGFloat = 880
    private let pwTitleSize: CGFloat = 70
    private let pwSubBase: CGFloat = 983
    private let pwSubSize: CGFloat = 57
    private let pwSubInk = Draw.color(0xff6b7076)
    private let pwCells = CGRect(x: 197, y: 1055, width: 1082 - 197, height: 1218 - 1055)
    private let pwCellRadius: CGFloat = 8
    private let pwCellStroke: CGFloat = 2
    private let pwCellLine = Draw.color(0xffe6e8ec)
    private let pwDotR: CGFloat = 10
    private let pwDot = Draw.color(0xff26282b)
    private let pwForgotBase: CGFloat = 1339
    private let pwForgotSize: CGFloat = 53
    private let pwBlue = Draw.color(0xff1677ff)

    private let pinSheet = Draw.color(0xffffffff)
    private let pinInk = Draw.color(0xff33363a)
    private let pinLine = Draw.color(0xffe9eaee)
    private let pinLineW: CGFloat = 2
    private let pinBackStroke: CGFloat = 4.6
    private let pinCols = 3
    private let pinRows = 4
    private let pinTop: CGFloat = 2030
    private let pinDigitSize: CGFloat = 84
    private let pinBackW: CGFloat = 82
    private let pinBackH: CGFloat = 56

    private var art: [String: UIImage] = [:]
    private struct Hit { let rect: CGRect; let token: ActionToken }
    private var hits: [Hit] = []

    init() {
        let mapping = ["yulibao": "wd_icon_yulibao", "bill": "wd_icon_bill", "member": "wd_icon_member",
                       "badgeLhb": "wd_badge_lhb", "badgeYuan": "wd_badge_yuan",
                       "info": "wd_info", "del": "wd_del",
                       // 银行卡选择层用到的两张
                       "tick": "wd_check", "addLogo": "wd_icon_add"]
        for (key, res) in mapping {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 数值（Java:261-309）

    static func value(_ s: State) -> Double { Double(s.amount) ?? 0 }

    static func canWithdraw(_ s: State, avail: Double) -> Bool {
        let amount = value(s)
        return amount > 0 && avail > 0 && amount <= avail + 0.0001
    }

    private func warning(_ s: State, avail: Double) -> String? {
        let amount = Self.value(s)
        if amount <= 0 { return nil }
        if avail <= 0 { return "当前余额为" + Self.plainNumber(avail) + "，暂无法提现" }
        if amount > avail + 0.0001 { return "可用余额不足，暂无法提现" }
        return nil
    }

    private static func plainNumber(_ value: Double) -> String {
        var text = ReceiptStore.money(value)
        if text.hasSuffix(".00") { text = String(text.dropLast(3)) }
        return text
    }

    private func unitText(_ s: State) -> String {
        let digits = s.amount.filter { $0.isNumber }.count
        switch digits {
        case 5: return "万"
        case 6: return "十万"
        case 7: return "百万"
        case 8: return "千万"
        default: return digits > 8 ? "亿" : ""
        }
    }

    // MARK: - 绘制（Java:233-255）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        hits.removeAll()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        let quota = WithdrawStore.shared.quota
        let avail = WithdrawStore.shared.avail
        let warn = warning(s, avail: avail)
        let shift = warn == nil ? 0 : warnCard2Shift

        drawHeader(d)
        drawBankRow(d)
        drawAmountCard(d, s, quota: quota, avail: avail, warn: warn)
        drawServiceCard(d, shift: shift)
        drawButton(d, active: Self.canWithdraw(s, avail: avail))

        if s.password {
            drawAmountKeypad(d, active: false, password: true)
            drawPasswordPanel(d, s)
        } else if s.keypad {
            drawAmountKeypad(d, active: Self.canWithdraw(s, avail: avail), password: false)
        }
        // 银行卡选择层画在最上面（对应 Android 的 BankSheet）
        if s.bankSheet { drawBankSheet(d) }
    }

    // MARK: - 银行卡选择层（`BankSheet.java`，268 行）

    /// 弹层几何（Java:33-84）
    private let dimTop: CGFloat = 306
    private let sheetTop: CGFloat = 1530.7
    private let sheetRadius: CGFloat = 30
    private let sheetTitleCx: CGFloat = 624
    private let sheetTitleBase: CGFloat = 1631.9
    private let sheetTitleSize: CGFloat = 65
    private let sheetCloseRect = CGRect(x: 1166.6, y: 1582.4, width: 1225.1 - 1166.6,
                                        height: 1637.1 - 1582.4)
    private let blockL: CGFloat = 46.8
    private let blockR: CGFloat = 1232.7
    private let blockTop: CGFloat = 1746.8
    private let blockRadius: CGFloat = 40
    private let sheetRuleL: CGFloat = 250.7
    private let sheetRuleR: CGFloat = 1196.2
    private let cardRowH: CGFloat = 219.9
    private let addRowH: CGFloat = 177.3
    private let blockBottomLimit: CGFloat = 2740
    private let iconL: CGFloat = 95.7
    private let iconTopOffset: CGFloat = 40.6
    private let iconSize: CGFloat = 61.2
    private let sheetNameX: CGFloat = 198
    private let sheetNameBase: CGFloat = 90.3
    private let sheetNameSize: CGFloat = 55
    private let sheetSubX: CGFloat = 204
    private let sheetSubBase: CGFloat = 159.8
    private let sheetSubSize: CGFloat = 47.5
    private let tickRect = CGRect(x: 1136.6, y: 50.8, width: 1193.1 - 1136.6, height: 94.4 - 50.8)
    private let addIconRect = CGRect(x: 92.7, y: 65.4, width: 158.6 - 92.7, height: 114.6 - 65.4)
    private let addNameBase: CGFloat = 105.3
    private let addChevronRect = CGRect(x: 1152.1, y: 67.9, width: 1177.2 - 1152.1,
                                        height: 109.6 - 67.9)

    private func drawBankSheet(_ d: Draw) {
        let cards = WithdrawStore.shared.allCards
        let selected = WithdrawStore.shared.selected()?.id ?? 0

        // 上面那层变暗
        d.rect(CGRect(x: 0, y: dimTop, width: Self.W, height: Self.H - dimTop),
               UIColor.black.withAlphaComponent(0xbf / 255.0))

        // 弹层本体：上半截页底色
        d.round(CGRect(x: 0, y: sheetTop, width: Self.W, height: Self.H - sheetTop),
                radius: sheetRadius, Draw.color(0xfff2f3f7))

        d.centerText("选择提现银行卡", cx: sheetTitleCx, baseline: sheetTitleBase,
                     size: sheetTitleSize, ink, bold: true)
        chevronCross(d, sheetCloseRect, stroke: 4.4, color: Draw.color(0xffaeafb3))
        hit(sheetCloseRect.insetBy(dx: -16, dy: -16), "close")

        let count = max(1, cards.count)
        let room = blockBottomLimit - blockTop - addRowH
        let rowH = min(cardRowH, max(120, room / CGFloat(count)))
        let blockBottom = blockTop + CGFloat(count) * rowH + addRowH
        d.round(CGRect(x: blockL, y: blockTop, width: blockR - blockL, height: blockBottom - blockTop),
                radius: blockRadius, .white)

        for (i, card) in cards.enumerated() {
            let top = blockTop + CGFloat(i) * rowH
            let scale = rowH / cardRowH
            let iconBox = CGRect(x: iconL, y: top + iconTopOffset * scale,
                                 width: iconSize, height: iconSize)
            if let logo = WithdrawStore.shared.icon(card) {
                drawLogo(d, logo, iconBox, cover: card.bank == WithdrawStore.bespoke)
            } else {
                d.circle(cx: iconBox.midX, cy: iconBox.midY, r: iconBox.width / 2,
                         Draw.color(0xffeef1f6))
            }
            let usable = card.usable
            d.text(card.title, x: sheetNameX, baseline: top + sheetNameBase * scale,
                   size: sheetNameSize, usable ? ink : Draw.color(0xffafafaf))
            d.text(card.arrival, x: sheetSubX, baseline: top + sheetSubBase * scale,
                   size: sheetSubSize, usable ? caption : Draw.color(0xffc6c8cb))
            if usable && card.id == selected, let tick = art["tick"] {
                d.art(tick, CGRect(x: tickRect.minX, y: top + tickRect.minY * scale,
                                   width: tickRect.width, height: tickRect.height * scale))
            }
            hit(CGRect(x: blockL, y: top, width: blockR - blockL, height: rowH), "pick:\(i)")
        }

        // 每张卡下面一条细线
        for i in 1...count {
            let y = blockTop + CGFloat(i) * rowH
            d.rect(CGRect(x: sheetRuleL, y: y - 2, width: sheetRuleR - sheetRuleL, height: 2),
                   Draw.color(0xffeeeeee))
        }

        let addTop = blockTop + CGFloat(count) * rowH
        if let addLogo = art["addLogo"] {
            d.art(addLogo, CGRect(x: addIconRect.minX, y: addTop + addIconRect.minY,
                                  width: addIconRect.width, height: addIconRect.height))
        }
        d.text("添加储蓄卡提现", x: sheetNameX, baseline: addTop + addNameBase,
               size: sheetNameSize, ink)
        chevronRight(d, CGRect(x: addChevronRect.minX, y: addTop + addChevronRect.minY,
                               width: addChevronRect.width, height: addChevronRect.height),
                     stroke: 4.6, color: Draw.color(0xffb9bcc2))
        hit(CGRect(x: blockL, y: addTop, width: blockR - blockL, height: addRowH), "add")
    }

    private func hit(_ rect: CGRect, _ token: ActionToken) {
        hits.append(Hit(rect: rect, token: token))
    }

    /// 头栏（Java:312-322）
    private func drawHeader(_ d: Draw) {
        chevronLeft(d, backRect, stroke: chevronLeftStroke, color: ink)
        d.text("提现", x: titleX, baseline: titleBase, size: titleSize, ink, bold: true)
        for x in dotsX { d.circle(cx: x, cy: dotsY, r: dotsR, ink) }
        hit(CGRect(x: 28, y: 156, width: 140 - 28, height: 286 - 156), "back")
        hit(CGRect(x: 1136, y: 176, width: 1264 - 1136, height: 266 - 176), "dots")
    }

    /// 到账银行卡（Java:325-341）
    private func drawBankRow(_ d: Draw) {
        d.text("到账银行卡", x: labelX, baseline: labelBase, size: labelSize, ink)
        let card = WithdrawStore.shared.selected()
        if let logo = WithdrawStore.shared.icon(card) {
            drawLogo(d, logo, bankIcon, cover: card?.bank == WithdrawStore.bespoke)
        } else {
            d.circle(cx: bankIcon.midX, cy: bankIcon.midY, r: bankIcon.width / 2,
                     Draw.color(0xffeef1f6))
        }
        d.text(card?.title ?? "", x: bankNameX, baseline: bankNameBase, size: bankNameSize, ink)
        d.text(card?.arrival ?? "", x: bankNameX, baseline: bankSubBase, size: bankSubSize, caption)
        chevronRight(d, bankChevron, stroke: chevronStroke, color: chevronColor)
        hit(CGRect(x: 40, y: 470, width: 1240 - 40, height: 630 - 470), "bank")
    }

    /// 提现金额卡（Java:344-378）
    private func drawAmountCard(_ d: Draw, _ s: State, quota: Double, avail: Double,
                                warn: String?) {
        let filled = !s.amount.isEmpty
        let box = CGRect(x: card1.minX, y: card1.minY, width: card1.width,
                         height: card1.height + (warn == nil ? 0 : warnCard1Shift))
        d.round(box, radius: cardRadius, cardColor)
        d.text("提现金额", x: card1TitleX, baseline: card1TitleBase, size: card1TitleSize, ink)
        d.text("¥", x: amountX, baseline: amountBase, size: amountSize, ink, bold: true)

        if filled {
            let unit = unitText(s)
            if !unit.isEmpty {
                d.rect(CGRect(x: unitCaretX, y: unitCaretTop, width: unitCaretW,
                              height: unitCaretBottom - unitCaretTop), Draw.color(0xff7d838c))
                d.text(unit, x: unitX, baseline: unitBase, size: unitSize, caption)
            }
            d.text(s.amount, x: amountValueX, baseline: amountValueBase, size: amountValueSize,
                   ink, bold: true)
            drawClearButton(d)
        } else {
            d.rightText("可用余额 ¥" + ReceiptStore.money(avail), right: availRight,
                        baseline: availBase, size: availSize, caption)
            // 可用余额本身可点：点开数字键盘修改 WithdrawStore.avail。
            hit(CGRect(x: 650, y: availBase - 62, width: availRight - 640, height: 92), "avail")
        }

        rule(d, y: divider1Y, l: dividerL, r: dividerR)
        if let warn { d.text(warn, x: warnX, baseline: warnBase, size: warnSize, warnRed) }

        let quotaBaseY = quotaBase + (warn == nil ? 0 : warnQuotaShift)
        d.text("剩余免费额度 ¥" + ReceiptStore.money(quota), x: quotaX, baseline: quotaBaseY,
               size: quotaSize, faint, sx: 0.96)
        if let info = art["info"] {
            d.art(info, CGRect(x: infoRect.minX, y: infoRect.minY + (warn == nil ? 0 : warnQuotaShift),
                               width: infoRect.width, height: infoRect.height))
        }
        hit(CGRect(x: 55, y: 1000, width: 1185 - 55, height: 150), "amount")
        if filled {
            hit(clearRect.insetBy(dx: -6, dy: -6), "clear")
        }
        hit(CGRect(x: 60, y: quotaBaseY - 55, width: 600, height: 72), "quota")
        hit(CGRect(x: 660, y: quotaBaseY - 55, width: 80, height: 72), "info")
    }

    /// ⊗ 清空按钮（Java:381-398）
    private func drawClearButton(_ d: Draw) {
        d.circle(cx: clearRect.midX, cy: clearRect.midY, r: clearRect.width / 2, clearDisc)
        let arm = clearRect.width * 0.21
        d.ctx.saveGState()
        d.ctx.setStrokeColor(UIColor.white.cgColor)
        d.ctx.setLineWidth(clearCross)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: clearRect.midX - arm, y: clearRect.midY - arm))
        d.ctx.addLine(to: CGPoint(x: clearRect.midX + arm, y: clearRect.midY + arm))
        d.ctx.move(to: CGPoint(x: clearRect.midX + arm, y: clearRect.midY - arm))
        d.ctx.addLine(to: CGPoint(x: clearRect.midX - arm, y: clearRect.midY + arm))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 服务卡（Java:402-420）
    private func drawServiceCard(_ d: Draw, shift: CGFloat) {
        d.round(CGRect(x: card2.minX, y: card2.minY + shift, width: card2.width,
                       height: card2.height), radius: cardRadius, cardColor)
        let names = ["yulibao", "bill", "member"]
        for (i, box) in rowIcon.enumerated() {
            if let image = art[names[i]] {
                d.art(image, CGRect(x: box.minX, y: box.minY + shift,
                                    width: box.width, height: box.height))
            }
        }
        for i in 0..<rowTitle.count {
            d.text(rowTitle[i], x: rowTextX, baseline: rowTitleBase[i] + shift, size: rowTitleSize, ink)
            d.text(rowSub[i], x: rowTextX, baseline: rowSubBase[i] + shift, size: rowSubSize, caption)
            let chev = rowChevron[i]
            chevronRight(d, CGRect(x: chev.minX, y: chev.minY + shift, width: chev.width,
                                   height: chev.height), stroke: chevronStroke, color: chevronColor)
            let top = (i == 0 ? card2.minY : rowRuleY[i - 1]) + shift
            let bottom = (i == rowTitle.count - 1 ? card2.maxY : rowRuleY[i]) + shift
            hit(CGRect(x: card2.minX + 14, y: top + 6, width: card2.width - 28, height: bottom - top - 12),
                "row:\(i)")
        }
        if let badge = rowBadge[0], let image = art["badgeLhb"] {
            d.art(image, CGRect(x: badge.minX, y: badge.minY + shift,
                                width: badge.width, height: badge.height))
        }
        if let badge = rowBadge[2], let image = art["badgeYuan"] {
            d.art(image, CGRect(x: badge.minX, y: badge.minY + shift,
                                width: badge.width, height: badge.height))
        }
        for y in rowRuleY { rule(d, y: y + shift, l: rowRuleL, r: rowRuleR) }
    }

    /// 提现按钮（Java:423-427）
    private func drawButton(_ d: Draw, active: Bool) {
        d.round(button, radius: buttonRadius, active ? actionOn : actionOff)
        d.centerText("提现", cx: buttonCx, baseline: buttonBase, size: buttonSize, .white, bold: true)
        hit(button, "submit")
    }

    /// 金额键盘（Java:430-477）
    private func drawAmountKeypad(_ d: Draw, active: Bool, password: Bool) {
        if password {
            drawPinKeypad(d)
            return
        }
        d.rect(CGRect(x: 0, y: Self.panelTop, width: Self.W, height: panelBottom - Self.panelTop),
               panel)
        hit(CGRect(x: 0, y: Self.panelTop, width: Self.W, height: Self.H - Self.panelTop), "noop")

        chevronDown(d, chevronDownRect, stroke: chevronStroke, color: Draw.color(0xff8a8f99))
        hit(CGRect(x: 600, y: 1866, width: 90, height: 64), "key:hide")

        for row in 0..<3 {
            for col in 0..<3 {
                key(d, col: col, row: row, label: keyLabel[row][col], token: "key:" + keyLabel[row][col])
            }
        }
        // 0 跨前两列；小数点单独一套 drop/shift
        d.round(CGRect(x: keyL[0], y: keyT[3], width: keyR[1] - keyL[0], height: keyH),
                radius: keyRadius, keyColor)
        d.centerText("0", cx: (keyL[0] + keyR[1]) / 2, baseline: keyT[3] + keyH / 2 + digitDrop,
                     size: digitSize, digitInk)
        hit(CGRect(x: keyL[0], y: keyT[3], width: keyR[1] - keyL[0], height: keyH), "key:0")

        d.round(CGRect(x: keyL[2], y: keyT[3], width: keyR[2] - keyL[2], height: keyH),
                radius: keyRadius, keyColor)
        d.centerText(".", cx: (keyL[2] + keyR[2]) / 2 + dotShiftX, baseline: keyT[3] + keyH / 2 + dotDrop,
                     size: digitSize, digitInk)
        hit(CGRect(x: keyL[2], y: keyT[3], width: keyR[2] - keyL[2], height: keyH), "key:dot")

        if let del = art["del"] { d.art(del, delRect) }
        hit(CGRect(x: keyL[3], y: keyT[0], width: keyR[3] - keyL[3], height: keyH), "key:del")

        d.round(actionRect, radius: keyRadius + 4, active ? actionOn : actionOff)
        d.centerText("提现", cx: actionRect.midX, baseline: actionRect.midY + actionTextDrop,
                     size: actionTextSize, .white, bold: true)
        hit(actionRect, "key:submit")
    }

    private func key(_ d: Draw, col: Int, row: Int, label: String, token: ActionToken) {
        let l = keyL[col], r = keyR[col], top = keyT[row]
        d.round(CGRect(x: l, y: top, width: r - l, height: keyH), radius: keyRadius, keyColor)
        d.centerText(label, cx: (l + r) / 2, baseline: top + keyH / 2 + digitDrop, size: digitSize,
                     digitInk)
        hit(CGRect(x: l, y: top, width: r - l, height: keyH), token)
    }

    /// 密码键盘（Java:493-535）：整片白 + 极浅格线 + 三列四行
    private func drawPinKeypad(_ d: Draw) {
        d.rect(CGRect(x: 0, y: pinTop, width: Self.W, height: panelBottom - pinTop), pinSheet)
        hit(CGRect(x: 0, y: pinTop, width: Self.W, height: Self.H - pinTop), "noop")

        let colW = Self.W / CGFloat(pinCols)
        let rowH = (panelBottom - pinTop) / CGFloat(pinRows)

        d.ctx.saveGState()
        d.ctx.setStrokeColor(pinLine.cgColor)
        d.ctx.setLineWidth(pinLineW)
        for i in 1..<pinCols {
            let x = CGFloat(i) * colW
            d.ctx.move(to: CGPoint(x: x, y: pinTop))
            d.ctx.addLine(to: CGPoint(x: x, y: panelBottom))
        }
        for i in 1..<pinRows {
            let y = pinTop + CGFloat(i) * rowH
            d.ctx.move(to: CGPoint(x: 0, y: y))
            d.ctx.addLine(to: CGPoint(x: Self.W, y: y))
        }
        d.ctx.strokePath()
        d.ctx.restoreGState()

        for row in 0..<3 {
            for col in 0..<pinCols {
                let digit = keyLabel[row][col]
                pinKey(d, col: CGFloat(col), row: CGFloat(row), colW: colW, rowH: rowH, label: digit)
                hit(CGRect(x: CGFloat(col) * colW, y: pinTop + CGFloat(row) * rowH,
                           width: colW, height: rowH), "pin:" + digit)
            }
        }
        // 最后一排：中间 0、右边 ⌫、左边空
        pinKey(d, col: 1, row: 3, colW: colW, rowH: rowH, label: "0")
        hit(CGRect(x: colW, y: pinTop + 3 * rowH, width: colW, height: rowH), "pin:0")
        let cx = 2.5 * colW
        let cy = pinTop + 3.5 * rowH
        backspace(d, CGRect(x: cx - pinBackW / 2, y: cy - pinBackH / 2,
                            width: pinBackW, height: pinBackH), stroke: pinBackStroke)
        hit(CGRect(x: 2 * colW, y: pinTop + 3 * rowH, width: Self.W - 2 * colW, height: rowH),
            "pin:del")
    }

    private func pinKey(_ d: Draw, col: CGFloat, row: CGFloat, colW: CGFloat, rowH: CGFloat,
                        label: String) {
        let size = pinDigitSize
        d.centerText(label, cx: (col + 0.5) * colW,
                     baseline: pinTop + (row + 0.5) * rowH + size * 0.39, size: size, pinInk)
    }

    /// ⌫（Java:545-570）
    private func backspace(_ d: Draw, _ box: CGRect, stroke: CGFloat) {
        let tail = box.height * 0.38
        let inset = stroke / 2
        d.ctx.saveGState()
        d.ctx.setStrokeColor(pinInk.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineJoin(.round)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: box.maxX - inset, y: box.minY + inset))
        d.ctx.addLine(to: CGPoint(x: box.minX + tail, y: box.minY + inset))
        d.ctx.addLine(to: CGPoint(x: box.minX + inset, y: box.midY))
        d.ctx.addLine(to: CGPoint(x: box.minX + tail, y: box.maxY - inset))
        d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.maxY - inset))
        d.ctx.closePath()
        d.ctx.strokePath()
        let arm = box.height * 0.17
        let cx = box.midX + tail * 0.18
        d.ctx.move(to: CGPoint(x: cx - arm, y: box.midY - arm))
        d.ctx.addLine(to: CGPoint(x: cx + arm, y: box.midY + arm))
        d.ctx.move(to: CGPoint(x: cx + arm, y: box.midY - arm))
        d.ctx.addLine(to: CGPoint(x: cx - arm, y: box.midY + arm))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// 支付密码面板（Java:634-675）
    private func drawPasswordPanel(_ d: Draw, _ s: State) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: pinTop),
               UIColor.black.withAlphaComponent(0xbf / 255.0))
        hit(CGRect(x: 0, y: 0, width: Self.W, height: pinTop), "noop")

        d.ctx.saveGState()
        d.ctx.translateBy(x: pwPanel.midX, y: pwPanel.midY)
        d.ctx.scaleBy(x: pwScale, y: pwScale)
        d.ctx.translateBy(x: -pwPanel.midX, y: -pwPanel.midY)

        d.round(pwPanel, radius: pwRadius, cardColor)
        chevronCross(d, pwClose, stroke: pwCloseStroke, color: Draw.color(0xffa9aeb6))
        d.centerText("请输入支付密码", cx: pwPanel.midX, baseline: pwTitleBase, size: pwTitleSize, ink)
        d.centerText("提现金额 ¥" + ReceiptStore.money(Self.value(s)), cx: pwPanel.midX,
                     baseline: pwSubBase, size: pwSubSize, pwSubInk)

        d.roundStroke(pwCells, radius: pwCellRadius, pwCellLine, width: pwCellStroke)
        let step = pwCells.width / CGFloat(pinLength)
        d.ctx.saveGState()
        d.ctx.setStrokeColor(pwCellLine.cgColor)
        d.ctx.setLineWidth(pwCellStroke)
        for i in 1..<pinLength {
            let x = pwCells.minX + CGFloat(i) * step
            d.ctx.move(to: CGPoint(x: x, y: pwCells.minY))
            d.ctx.addLine(to: CGPoint(x: x, y: pwCells.maxY))
        }
        d.ctx.strokePath()
        d.ctx.restoreGState()

        for i in 0..<min(s.pin.count, pinLength) {
            d.circle(cx: pwCells.minX + (CGFloat(i) + 0.5) * step, cy: pwCells.midY, r: pwDotR, pwDot)
        }

        d.centerText("忘记密码？", cx: pwPanel.midX, baseline: pwForgotBase, size: pwForgotSize, pwBlue)
        d.ctx.restoreGState()

        hit(CGRect(x: pwX(pwClose.minX - 20), y: pwY(pwClose.minY - 20),
                   width: pwX(pwClose.maxX + 20) - pwX(pwClose.minX - 20),
                   height: pwY(pwClose.maxY + 20) - pwY(pwClose.minY - 20)), "pin:close")
        hit(CGRect(x: pwX(pwPanel.midX - 220), y: pwY(pwForgotBase - 46),
                   width: pwX(pwPanel.midX + 220) - pwX(pwPanel.midX - 220),
                   height: pwY(pwForgotBase + 16) - pwY(pwForgotBase - 46)), "pin:forgot")
    }

    private func pwX(_ x: CGFloat) -> CGFloat { pwPanel.midX + (x - pwPanel.midX) * pwScale }
    private func pwY(_ y: CGFloat) -> CGFloat { pwPanel.midY + (y - pwPanel.midY) * pwScale }

    // MARK: - 图元

    private func rule(_ d: Draw, y: CGFloat, l: CGFloat, r: CGFloat) {
        d.rect(CGRect(x: l, y: y, width: r - l, height: 2), rule2)
    }

    /// 头像/银行 logo（Java:714-733）：cover 时按短边放大并裁成圆
    private func drawLogo(_ d: Draw, _ image: UIImage, _ box: CGRect, cover: Bool) {
        let iw = image.size.width * image.scale
        let ih = image.size.height * image.scale
        guard iw > 0, ih > 0 else { return }
        let scale = cover ? max(box.width / iw, box.height / ih) : min(box.width / iw, box.height / ih)
        let w = iw * scale
        let h = ih * scale
        let rect = CGRect(x: box.midX - w / 2, y: box.midY - h / 2, width: w, height: h)
        if cover {
            d.clipped(to: box, radius: box.width / 2) { d.art(image, rect) }
        } else {
            d.art(image, rect)
        }
    }

    private func chevronRight(_ d: Draw, _ box: CGRect, stroke: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: box.minX, y: box.minY))
        d.ctx.addLine(to: CGPoint(x: box.maxX, y: box.midY))
        d.ctx.addLine(to: CGPoint(x: box.minX, y: box.maxY))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func chevronLeft(_ d: Draw, _ box: CGRect, stroke: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: box.maxX, y: box.minY))
        d.ctx.addLine(to: CGPoint(x: box.minX, y: box.midY))
        d.ctx.addLine(to: CGPoint(x: box.maxX, y: box.maxY))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    private func chevronDown(_ d: Draw, _ box: CGRect, stroke: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: box.minX, y: box.minY))
        d.ctx.addLine(to: CGPoint(x: box.midX, y: box.maxY))
        d.ctx.addLine(to: CGPoint(x: box.maxX, y: box.minY))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    /// ✕（Java:678-692）
    private func chevronCross(_ d: Draw, _ box: CGRect, stroke: CGFloat, color: UIColor) {
        let inset = stroke / 2
        d.ctx.saveGState()
        d.ctx.setStrokeColor(color.cgColor)
        d.ctx.setLineWidth(stroke)
        d.ctx.setLineCap(.round)
        d.ctx.move(to: CGPoint(x: box.minX + inset, y: box.minY + inset))
        d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.maxY - inset))
        d.ctx.move(to: CGPoint(x: box.maxX - inset, y: box.minY + inset))
        d.ctx.addLine(to: CGPoint(x: box.minX + inset, y: box.maxY - inset))
        d.ctx.strokePath()
        d.ctx.restoreGState()
    }

    // MARK: - 命中（Java:701-707：后登记优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 银行卡弹层在最上面：它开着的时候，只有它能接收点击
        if s.bankSheet {
            for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: y)) {
                if hit.token == "close" || hit.token == "add" || hit.token.hasPrefix("pick:") {
                    return hit.token
                }
            }
            return "noop"
        }
        for hit in hits.reversed() where hit.rect.contains(CGPoint(x: x, y: y)) {
            return hit.token
        }
        return "noop"
    }
}
