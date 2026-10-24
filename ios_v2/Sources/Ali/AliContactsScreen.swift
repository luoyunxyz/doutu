import UIKit

/// 「支付宝 → 通讯录」—— `AliContactsActivity` 的移植。
///
/// 版式（Java 的 dp × 3）：
///   · 顶栏 44dp：返回图标 + 「通讯录」18sp + 右侧「添加朋友」16sp
///   · 搜索胶囊：高 36dp、白底圆角 100、左右各 8dp、上边距 9dp
///   · 顶部白卡（圆角 12）：新的朋友 / 群聊 / 生活号 / 最近转账联系人 / 智能服务 / 标签 / 专属客服，每行 54dp
///   · 「批量添加」单独一张卡（同样的行样式）
///   · 联系人分组：字母头 25dp（12sp 灰）+ 每行 54dp（白底圆角 12、头像 38dp、昵称 14sp 粗体）
///   · 右侧 A-Z 索引条（只画，不响应点击 —— 与参考图一致）
///
/// 长按联系人 = 删除确认（同时删通讯录与消息列表，否则会被同步回来）；
/// 点联系人 = 弹「发起聊天 / 删除」（聊天页下一轮做）。
final class AliContactsScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    private let bg = Draw.color(0xfff5f5f5)
    private let ink = Draw.color(0xff333333)
    private let letterGrey = Draw.color(0xff999999)

    /// 数据全在 `AliContactStore`，页面本身只有滚动偏移（基类里的 `scroll`）
    final class State: ScreenState { }

    /// Java `TOP_NAMES` / `TOP_ICONS`（顺序不能动）
    private let topNames = ["新的朋友", "群聊", "生活号", "最近转账联系人", "智能服务", "标签", "专属客服"]
    private let topIcons = ["ali_new_friend", "ali_group", "ali_shh", "ali_early",
                            "ali_smart", "ali_tag", "ali_kefu"]

    // MARK: 行模型（画与点共用同一份）

    private enum RowKind {
        case search
        case topItem(Int)
        case batch
        case letter(String)
        case contact(AliContactStore.Contact)
    }

    private struct Row {
        let kind: RowKind
        let rect: CGRect
    }

    private static let headerH = dp(44)
    private static let rowH = dp(54)
    private static let searchH = dp(36)
    private static let letterH = dp(25)
    private static let cardMargin = dp(8)

    private func rows() -> [Row] {
        var result: [Row] = []
        var y = Self.headerH

        // 搜索胶囊
        y += dp(9)
        result.append(Row(kind: .search,
                          rect: CGRect(x: Self.cardMargin, y: y,
                                       width: Self.W - Self.cardMargin * 2, height: Self.searchH)))
        y += Self.searchH

        // 顶部白卡（7 行）
        y += dp(10)
        for index in topNames.indices {
            result.append(Row(kind: .topItem(index),
                              rect: CGRect(x: Self.cardMargin, y: y,
                                           width: Self.W - Self.cardMargin * 2, height: Self.rowH)))
            y += Self.rowH
        }

        // 批量添加
        y += dp(10)
        result.append(Row(kind: .batch,
                          rect: CGRect(x: Self.cardMargin, y: y,
                                       width: Self.W - Self.cardMargin * 2, height: Self.rowH)))
        y += Self.rowH

        // 分组
        for group in AliContactStore.shared.grouped() {
            result.append(Row(kind: .letter(group.letter),
                              rect: CGRect(x: 0, y: y, width: Self.W, height: Self.letterH)))
            y += Self.letterH
            for contact in group.contacts {
                result.append(Row(kind: .contact(contact),
                                  rect: CGRect(x: Self.cardMargin, y: y,
                                               width: Self.W - Self.cardMargin * 2,
                                               height: Self.rowH)))
                y += Self.rowH
            }
        }

        // 底部留白
        result.append(Row(kind: .letter(""), rect: CGRect(x: 0, y: y, width: Self.W, height: dp(24))))
        return result
    }

    private var contentHeight: CGFloat {
        rows().last.map { $0.rect.maxY } ?? Self.H
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, contentHeight - Self.H)
    }

    // MARK: - 绘制

    func draw(_ d: Draw, state: ScreenState) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), bg)

        // 顶部 7 行是**一整张**圆角卡（Java:172-203 只给 card 设了一次 background），
        // 逐行各自圆角会在行与行之间露出灰底锯齿 —— 先把整张卡铺出来，行只画内容。
        let allRows = rows()
        let topRects = allRows.compactMap { row -> CGRect? in
            if case .topItem = row.kind { return row.rect }
            return nil
        }
        if let first = topRects.first, let last = topRects.last {
            d.round(CGRect(x: first.minX, y: first.minY,
                           width: first.width, height: last.maxY - first.minY),
                    radius: dp(12), .white)
        }

        for row in allRows {
            switch row.kind {
            case .search:
                drawSearch(d, rect: row.rect)
            case let .topItem(index):
                drawIconRow(d, rect: row.rect, icon: topIcons[index], title: topNames[index],
                            rounded: false)
            case .batch:
                drawIconRow(d, rect: row.rect, icon: "ali_new_friend", title: "批量添加",
                            rounded: true)
            case let .letter(letter):
                guard !letter.isEmpty else { continue }
                d.text(letter, x: dp(16),
                       baseline: row.rect.minY + Draw.font(size: dp(12)).ascender + dp(6),
                       size: dp(12), letterGrey)
            case let .contact(contact):
                drawContact(d, rect: row.rect, contact: contact)
            }
        }

        drawIndexBar(d)
        drawHeader(d)
    }

    /// 顶栏（最后画，盖在内容上）
    private func drawHeader(_ d: Draw) {
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.headerH), bg)
        let iconSize = dp(22)
        d.art(UIImage(named: "ali_back"),
              CGRect(x: dp(9), y: (Self.headerH - iconSize) / 2, width: iconSize, height: iconSize))
        let titleSize = dp(18)
        d.text("通讯录", x: dp(9) + iconSize + dp(12),
               baseline: Self.headerH / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
               size: titleSize, ink)
        let addSize = dp(16)
        d.rightText("添加朋友", right: Self.W - dp(16),
                    baseline: Self.headerH / 2 + Draw.font(size: addSize).ascender / 2 - addSize * 0.06,
                    size: addSize, ink)
    }

    private func drawSearch(_ d: Draw, rect: CGRect) {
        d.round(rect, radius: rect.height / 2, .white)
        let iconSize = dp(18)
        d.art(UIImage(named: "ali_search"),
              CGRect(x: rect.minX + dp(15), y: rect.midY - iconSize / 2,
                     width: iconSize, height: iconSize))
        let size = dp(15)
        d.text("搜索", x: rect.minX + dp(15) + iconSize + dp(6),
               baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, Draw.color(0xff9d9d9d))
    }

    /// 白卡里的一行：38dp 图标 + 16sp 粗体文字
    private func drawIconRow(_ d: Draw, rect: CGRect, icon: String, title: String,
                             rounded: Bool) {
        // 顶部七行共用一张卡，行本身不再圆角（否则行间露出灰底锯齿）
        if rounded {
            d.round(rect, radius: dp(12), .white)
        }
        let iconSize = dp(38)
        d.art(UIImage(named: icon),
              CGRect(x: rect.minX + dp(12), y: rect.midY - iconSize / 2,
                     width: iconSize, height: iconSize))
        let size = dp(16)
        d.text(title, x: rect.minX + dp(12) + iconSize + dp(12),
               baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, ink, bold: true)
    }

    /// 联系人行：38dp 圆头像 + 14sp 粗体昵称
    private func drawContact(_ d: Draw, rect: CGRect, contact: AliContactStore.Contact) {
        d.round(rect, radius: dp(12), .white)
        let avatarSize = dp(38)
        let avatarRect = CGRect(x: rect.minX + dp(16), y: rect.midY - avatarSize / 2,
                                width: avatarSize, height: avatarSize)
        let image = Self.avatarImage(contact.avatar) ?? UIImage(named: "ali_avatar")
        if let image {
            d.clipped(to: avatarRect, radius: avatarSize / 2) {
                d.art(image, avatarRect)
            }
        }
        let size = dp(14)
        d.text(contact.name, x: avatarRect.maxX + dp(12),
               baseline: rect.midY + Draw.font(size: size).ascender / 2 - size * 0.06,
               size: size, ink, bold: true)
    }

    /// 右侧 A-Z 索引条（Java:102-112）
    private func drawIndexBar(_ d: Draw) {
        let letters = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        let size = dp(9)
        let lineHeight = Draw.font(size: size).lineHeight + dp(2)
        let total = lineHeight * CGFloat(letters.count)
        var baseline = (Self.H - total) / 2 + Draw.font(size: size).ascender
        for letter in letters {
            d.centerText(String(letter), cx: Self.W - dp(5) - dp(6.5), baseline: baseline,
                         size: size, Draw.color(0xff666666))
            baseline += lineHeight
        }
    }

    /// 头像 token：Android 有 `asset:` / `content:` / `url:` / `file:` 四种；
    /// iOS 先支持 `asset:`（随包资源），其余回落到默认头像。
    static func avatarImage(_ token: String) -> UIImage? {
        guard token.hasPrefix("asset:") else { return nil }
        return UIImage(named: String(token.dropFirst("asset:".count)))
    }

    // MARK: - 命中

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 顶栏
        if y < Self.headerH {
            if x > Self.W - dp(140) { return "ali:addFriend" }
            if x < dp(60) { return "ali:back" }
            return "noop"
        }
        for row in rows() where row.rect.contains(CGPoint(x: x, y: y)) {
            switch row.kind {
            case .search:
                return "ali:search"
            case let .topItem(index):
                return "ali:top:\(index)"
            case .batch:
                return "ali:batch"
            case let .contact(contact):
                return "ali:contact:\(contact.name)"
            case .letter:
                return "noop"
            }
        }
        return "noop"
    }

    /// 长按联系人 = 删除（Java:279-291）
    func longPress(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken? {
        for row in rows() where row.rect.contains(CGPoint(x: x, y: y)) {
            if case let .contact(contact) = row.kind {
                return "ali:delete:\(contact.name)"
            }
        }
        return nil
    }
}
