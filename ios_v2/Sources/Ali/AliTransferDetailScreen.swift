import UIKit

/// 「支付宝 → 转账详情（账单详情）」—— `AliTransferDetailActivity`（990 行）的移植。
///
/// 两张大白卡（圆角 6、左右各 12dp）：
///   · 汇总卡：39dp 头像 → 昵称带箭头 → 金额 33sp 粗体 → 交易状态 15sp →
///     三行「标签/值」（创建时间 / 转账备注 / 对方账户）→「更多 ﹀」展开两行 →
///     服务推荐横幅 → 更多开关；
///   · 账单卡：小标题 → 黄色横幅 → 账单分类 / 标签和备注 / 计入收支开关 → 分割线 →
///     七个入口（两列、每行 35.4dp，图标 15.2dp + 5.1dp 间距 + 13sp 文字）。
///
/// 顶部 47dp 条固定不动，内容整体可滚（内容比一屏高）。
final class AliTransferDetailScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    // Java:72-105 的几何常量
    static let barH = dp(47)
    private let bodyMargin = dp(12)
    private let cardGap = dp(8)
    private let cardPad = dp(12)
    private let cardRadius = dp(6)
    private let avatarSide = dp(39)
    private let avatarTop = dp(27)
    private let nameTop = dp(12)
    private let amountTop = dp(2)
    private let statusTop = dp(4)
    private let rowsTop = dp(16.4)
    private let rowH = dp(32.4)
    private let labelW = dp(89.2)
    private let serviceTop = dp(9)
    private let bannerH = dp(39.4)
    private let bannerRadius = dp(6)
    private let bannerPadL = dp(12)
    private let bannerPadR = dp(13)
    private let moreTop = dp(14.5)
    private let card1Bottom = dp(19)
    private let billTitleTop = dp(11)
    private let billBannerTop = dp(10.8)
    private let billBannerH = dp(37.1)
    private let billRowsTop = dp(11.65)
    private let billRowH = dp(37.5)
    private let dividerTop = dp(11)
    private let dividerBottom = dp(9.3)
    private let actionRowH = dp(35.4)
    private let actionIconW = dp(15.2)
    private let actionIconGap = dp(5.1)
    private let card2Bottom = dp(7)

    // Java:108-126 的颜色
    private let pageBg = Draw.color(0xfff5f5f5)
    private let cardBg = UIColor.white
    private let titleInk = Draw.color(0xff303030)
    private let nameInk = Draw.color(0xff2f2f2f)
    private let labelInk = Draw.color(0xff969696)
    private let valueInk = Draw.color(0xff2f2f2f)
    private let chevronInk = Draw.color(0xffc8c8c8)
    private let banner1Bg = Draw.color(0xfff6f7fb)
    private let banner1Text = Draw.color(0xff2f5b8a)
    private let banner2Bg = Draw.color(0xfff8f9f3)
    private let banner2Text = Draw.color(0xffb39132)
    private let moreInk = Draw.color(0xff9a9a9a)
    private let actionInk = Draw.color(0xff5c6784)
    private let switchOn = Draw.color(0xff1677ff)
    private let switchOff = Draw.color(0xffd8d8d8)
    private let dividerInk = Draw.color(0xffededed)

    private let statusOptions = ["交易成功", "交易关闭", "处理中", "已退款"]
    private let categoryOptions = ["其他", "餐饮美食", "交通出行", "日用百货",
                                   "服饰装扮", "转账红包", "收入"]
    private let actionIcons = ["ali_act_history", "ali_act_aa", "ali_act_voucher",
                               "ali_act_flow", "ali_act_receipt", "ali_act_question",
                               "ali_act_complaint"]

    final class State: ScreenState {
        /// 「更多 ﹀」是否展开（Java 的 moreRows 显隐）
        var expandMore = false
    }

    // MARK: - 布局（画与点共用）

    private enum ItemKind {
        case avatar
        case name
        case amount
        case status
        case row(String)
        case serviceLabel
        case banner1
        case more
        case billTitle
        case banner2
        case billRow(String)
        case divider
        case action(Int)
    }

    private struct Item {
        let kind: ItemKind
        let rect: CGRect
    }

    private struct Layout {
        var card1 = CGRect.zero
        var card2 = CGRect.zero
        var items: [Item] = []
        var contentHeight: CGFloat = 0
    }

    private func layout(_ s: State) -> Layout {
        var out = Layout()
        let store = AliTransferStore.shared
        let cardX = bodyMargin
        let cardW = Self.W - bodyMargin * 2
        let innerLeft = cardX + cardPad
        let innerRight = cardX + cardW - cardPad
        let innerW = innerRight - innerLeft

        // ---- 卡一 ----
        var y = Self.barH
        let card1Top = y
        y += avatarTop
        let avatar = CGRect(x: (Self.W - avatarSide) / 2, y: y,
                            width: avatarSide, height: avatarSide)
        out.items.append(Item(kind: .avatar, rect: avatar))
        y = avatar.maxY + nameTop

        let nameSize = dp(17)
        let nameH = Draw.font(size: nameSize).lineHeight
        out.items.append(Item(kind: .name, rect: CGRect(x: cardX + cardPad, y: y,
                                                        width: innerW, height: nameH)))
        y += nameH + amountTop

        let amountSize = dp(33)
        let amountH = Draw.font(size: amountSize, bold: true).lineHeight
        out.items.append(Item(kind: .amount, rect: CGRect(x: cardX, y: y,
                                                          width: cardW, height: amountH)))
        y += amountH + statusTop

        let statusSize = dp(15)
        let statusH = Draw.font(size: statusSize).lineHeight
        out.items.append(Item(kind: .status, rect: CGRect(x: cardX, y: y,
                                                          width: cardW, height: statusH)))
        y += statusH + rowsTop

        for label in ["创建时间", "转账备注", "对方账户"] {
            out.items.append(Item(kind: .row(label), rect: CGRect(x: innerLeft, y: y,
                                                                  width: innerW, height: rowH)))
            y += rowH
        }
        if s.expandMore {
            for label in ["支付方式", "订单号"] {
                out.items.append(Item(kind: .row(label), rect: CGRect(x: innerLeft, y: y,
                                                                     width: innerW, height: rowH)))
                y += rowH
            }
        }

        y += serviceTop
        let serviceSize = dp(13)
        let serviceH = Draw.font(size: serviceSize).lineHeight
        out.items.append(Item(kind: .serviceLabel, rect: CGRect(x: innerLeft, y: y,
                                                                width: innerW, height: serviceH)))
        y += serviceH

        let banner1 = CGRect(x: innerLeft, y: y, width: innerW, height: bannerH)
        out.items.append(Item(kind: .banner1, rect: banner1))
        y = banner1.maxY + moreTop

        let moreSize = dp(13)
        let moreH = Draw.font(size: moreSize).lineHeight
        out.items.append(Item(kind: .more, rect: CGRect(x: innerLeft, y: y,
                                                        width: innerW, height: moreH)))
        y += moreH + card1Bottom
        out.card1 = CGRect(x: cardX, y: card1Top, width: cardW, height: y - card1Top)

        // ---- 卡二 ----
        y += cardGap
        let card2Top = y
        y += billTitleTop
        let billTitleSize = dp(16)
        let billTitleH = Draw.font(size: billTitleSize, bold: true).lineHeight
        out.items.append(Item(kind: .billTitle, rect: CGRect(x: innerLeft, y: y,
                                                             width: innerW, height: billTitleH)))
        y += billTitleH + billBannerTop

        let banner2 = CGRect(x: innerLeft, y: y, width: innerW, height: billBannerH)
        out.items.append(Item(kind: .banner2, rect: banner2))
        y = banner2.maxY + billRowsTop

        for label in ["账单分类", "标签和备注", "计入收支"] {
            out.items.append(Item(kind: .billRow(label), rect: CGRect(x: innerLeft, y: y,
                                                                      width: innerW, height: billRowH)))
            y += billRowH
        }

        y += dividerTop
        out.items.append(Item(kind: .divider, rect: CGRect(x: innerLeft, y: y,
                                                           width: innerW, height: dp(0.5))))
        y += dp(0.5) + dividerBottom

        // 七个入口，两列
        let halfW = innerW / 2
        for (index, _) in actionIcons.enumerated() {
            let col = index % 2
            let rect = CGRect(x: innerLeft + halfW * CGFloat(col), y: y,
                              width: halfW, height: actionRowH)
            out.items.append(Item(kind: .action(index), rect: rect))
            if col == 1 || index == actionIcons.count - 1 { y += actionRowH }
        }

        y += card2Bottom
        out.card2 = CGRect(x: cardX, y: card2Top, width: cardW, height: y - card2Top)
        out.contentHeight = y + dp(16)
        _ = store
        return out
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        let s = (state as? State) ?? State()
        return max(0, layout(s).contentHeight - Self.H)
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        let store = AliTransferStore.shared
        let laid = layout(s)
        let scroll = s.scroll

        // 宿主整体做了 translate(0,-scroll)：铺底从 scroll 开始，才正好盖住整屏
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.H), pageBg)
        d.round(laid.card1, radius: cardRadius, cardBg)
        d.round(laid.card2, radius: cardRadius, cardBg)

        for item in laid.items {
            switch item.kind {
            case .avatar: drawAvatar(d, rect: item.rect, store: store)
            case .name: drawName(d, rect: item.rect, store: store)
            case .amount: drawAmount(d, rect: item.rect, store: store)
            case .status: drawStatus(d, rect: item.rect, store: store)
            case let .row(label): drawValueRow(d, rect: item.rect, label: label,
                                               value: valueFor(label, store), chevron: false)
            case .serviceLabel: drawSmallLabel(d, rect: item.rect, text: store.serviceLabel)
            case .banner1: drawBanner(d, rect: item.rect, text: store.serviceTip,
                                      bg: banner1Bg, ink: banner1Text)
            case .more: drawMore(d, rect: item.rect, expanded: s.expandMore)
            case .billTitle: drawBillTitle(d, rect: item.rect, store: store)
            case .banner2: drawBanner(d, rect: item.rect, text: store.billTip,
                                      bg: banner2Bg, ink: banner2Text)
            case let .billRow(label): drawBillRow(d, rect: item.rect, label: label, store: store)
            case .divider: d.rect(item.rect, dividerInk)
            case let .action(index): drawAction(d, rect: item.rect, index: index, store: store)
            }
        }

        drawTopBar(d, store: store, scroll: scroll)
    }

    private func valueFor(_ label: String, _ store: AliTransferStore) -> String {
        switch label {
        case "创建时间": return store.stamp()
        case "转账备注": return store.note
        case "对方账户": return store.account
        case "支付方式": return store.paymentMethod
        case "订单号": return store.orderNumber()
        default: return ""
        }
    }

    /// 顶栏固定：画在内容坐标 `scroll` 处（宿主已经整体平移过）
    private func drawTopBar(_ d: Draw, store: AliTransferStore, scroll: CGFloat) {
        d.rect(CGRect(x: 0, y: scroll, width: Self.W, height: Self.barH), pageBg)
        d.art(UIImage(named: "ali_back_arrow"),
              CGRect(x: dp(13.5), y: scroll + (Self.barH - dp(22)) / 2,
                     width: dp(14), height: dp(22)))
        let size = dp(19)
        d.text(store.pageTitle, x: dp(38.8),
               baseline: scroll + Self.barH / 2 + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, titleInk)
    }

    private func drawAvatar(_ d: Draw, rect: CGRect, store: AliTransferStore) {
        let sender = store.sender ?? AliTransferStore.Sender(name: "怕你久居她心", asset: nil)
        if let asset = sender.asset, let image = UIImage(named: asset) {
            d.clipped(to: rect, radius: rect.width / 2) { d.art(image, rect) }
            return
        }
        if let image = UIImage(named: "ali_avatar_default") {
            d.clipped(to: rect, radius: rect.width / 2) { d.art(image, rect) }
            return
        }
        d.circle(cx: rect.midX, cy: rect.midY, r: rect.width / 2, Draw.color(0xff7aa7d8))
    }

    private func drawName(_ d: Draw, rect: CGRect, store: AliTransferStore) {
        let size = dp(17)
        let sender = store.sender ?? AliTransferStore.Sender(name: "怕你久居她心", asset: nil)
        let width = Draw.width(sender.name, size: size)
        let baseline = rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06
        d.centerText(sender.name, cx: rect.midX - dp(6), baseline: baseline, size: size, nameInk)
        // 右侧小箭头（Java 的 chevron(11)）
        let ax = rect.midX - dp(6) + width / 2 + dp(4)
        d.line(ax, rect.midY - dp(5), ax + dp(5), rect.midY, chevronInk, width: dp(1.2))
        d.line(ax + dp(5), rect.midY, ax, rect.midY + dp(5), chevronInk, width: dp(1.2))
    }

    private func drawAmount(_ d: Draw, rect: CGRect, store: AliTransferStore) {
        let size = dp(33)
        d.centerText(store.amountText, cx: Self.W / 2,
                     baseline: rect.midY + Draw.font(size: size, bold: true).ascender / 2 - size * 0.06,
                     size: size, Draw.color(0xff000000), bold: true)
    }

    private func drawStatus(_ d: Draw, rect: CGRect, store: AliTransferStore) {
        let size = dp(15)
        d.centerText(store.status, cx: Self.W / 2,
                     baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                     size: size, Draw.color(0xff303030))
    }

    private func drawValueRow(_ d: Draw, rect: CGRect, label: String, value: String,
                              chevron: Bool) {
        let size = dp(13)
        let baseline = rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06
        d.text(label, x: rect.minX, baseline: baseline, size: size, labelInk)
        d.rightText(value, right: rect.maxX - (chevron ? dp(14) : 0), baseline: baseline,
                    size: size, valueInk)
        if chevron {
            let x = rect.maxX - dp(8)
            d.line(x, rect.midY - dp(4), x + dp(4), rect.midY, chevronInk, width: dp(1.2))
            d.line(x + dp(4), rect.midY, x, rect.midY + dp(4), chevronInk, width: dp(1.2))
        }
    }

    private func drawSmallLabel(_ d: Draw, rect: CGRect, text: String) {
        let size = dp(13)
        d.text(text, x: rect.minX,
               baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, labelInk)
    }

    private func drawBanner(_ d: Draw, rect: CGRect, text: String,
                            bg: UIColor, ink: UIColor) {
        d.round(rect, radius: bannerRadius, bg)
        let size = dp(13)
        d.text(text, x: rect.minX + bannerPadL,
               baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, ink)
    }

    private func drawMore(_ d: Draw, rect: CGRect, expanded: Bool) {
        let size = dp(13)
        let text = expanded ? "收起 ﹀" : "更多 ﹀"
        d.centerText(text, cx: rect.midX,
                     baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
                     size: size, moreInk)
    }

    private func drawBillTitle(_ d: Draw, rect: CGRect, store: AliTransferStore) {
        let size = dp(16)
        d.text(store.billTitle, x: rect.minX,
               baseline: rect.midY + Draw.font(size: size, bold: true).ascender / 2 - size * 0.06,
               size: size, valueInk, bold: true)
    }

    private func drawBillRow(_ d: Draw, rect: CGRect, label: String, store: AliTransferStore) {
        if label == "计入收支" {
            let size = dp(13)
            let baseline = rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06
            d.text(label, x: rect.minX, baseline: baseline, size: size, valueInk)
            // 开关 28×18dp
            let w = dp(28), h = dp(18)
            let track = CGRect(x: rect.maxX - w, y: rect.midY - h / 2, width: w, height: h)
            d.round(track, radius: h / 2, store.includedInBill ? switchOn : switchOff)
            let knob = h * 0.78
            let knobCx = store.includedInBill ? track.maxX - h / 2 : track.minX + h / 2
            d.circle(cx: knobCx, cy: track.midY, r: knob / 2, .white)
            return
        }
        let value: String
        switch label {
        case "账单分类": value = store.category
        case "标签和备注": value = store.tags.isEmpty ? "添加" : store.tags
        default: value = ""
        }
        drawValueRow(d, rect: rect, label: label, value: value, chevron: true)
    }

    private func drawAction(_ d: Draw, rect: CGRect, index: Int, store: AliTransferStore) {
        guard actionIcons.indices.contains(index) else { return }
        let iconRect = CGRect(x: rect.minX, y: rect.midY - actionIconW / 2,
                              width: actionIconW, height: actionIconW)
        d.art(UIImage(named: actionIcons[index]), iconRect)
        let size = dp(13)
        let label = store.actionLabels.indices.contains(index) ? store.actionLabels[index] : ""
        d.text(label, x: iconRect.maxX + actionIconGap,
               baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, actionInk)
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 宿主已经把 scroll 加进 y，所以这里 y 就是内容坐标
        if y >= s.scroll && y < s.scroll + Self.barH {
            if x < dp(60) { return "ali:back" }
            return "ali:td:title"
        }
        for item in layout(s).items where item.rect.contains(CGPoint(x: x, y: y)) {
            switch item.kind {
            case .avatar: return "ali:td:avatar"
            case .name: return "ali:td:name"
            case .amount: return "ali:td:amount"
            case .status: return "ali:td:status"
            case let .row(label): return "ali:td:row:\(label)"
            case .serviceLabel: return "ali:td:service"
            case .banner1: return "ali:td:banner1"
            case .more: return "ali:td:more"
            case .billTitle: return "ali:td:billTitle"
            case .banner2: return "ali:td:banner2"
            case let .billRow(label): return "ali:td:bill:\(label)"
            case .divider: return "noop"
            case let .action(index): return "ali:td:action:\(index)"
            }
        }
        return "noop"
    }
}
