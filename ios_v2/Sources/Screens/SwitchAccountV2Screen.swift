import UIKit

/// 账号列表模型（对应 Android `features/zhi/data/AccountsV2.java` 的 iOS 版）。
///
/// Android 端账号存在本地（SharedPreferences 序列化），「当前账号」是其中一个标记。
/// iOS 先做成内存 + UserDefaults 的等价实现，接上网络层后由接口同步。
final class AccountsV2 {
    static let shared = AccountsV2()

    /// 参考图卡片上印着的那个号码：只有别的号才需要盖掉重画
    static let defaultPhone = "138****8888"

    struct Account {
        let id: String
        var name: String
        var phone: String
        /// 头像（nil 用参考图抠出来的那张）
        var avatar: UIImage?
        /// 是否是自己登录过的账号（有名字和号码）
        var ownIdentity: Bool
    }

    private(set) var all: [Account] = []
    private(set) var currentId: String = ""

    private init() {
        currentId = UserDefaults.standard.string(forKey: "accounts.current") ?? "default"
        if let phone = UserDefaults.standard.string(forKey: "accounts.phone"),
           let name = UserDefaults.standard.string(forKey: "accounts.name") {
            all = [Account(id: currentId, name: name, phone: phone, avatar: ProfileStore.shared.avatar,
                           ownIdentity: !name.isEmpty)]
        } else {
            all = [Account(id: currentId, name: "", phone: Self.defaultPhone, avatar: nil, ownIdentity: false)]
        }
    }

    func isCurrent(_ id: String) -> Bool { id == currentId }

    /// 切换账号：除了记下新的当前账号，还要把它写进「个人信息」存档 ——
    /// 否则「我的」页仍显示上一个人的名字（对应 Android `AccountsV2.switchTo` + `applyProfile`）
    func switchTo(_ id: String) {
        guard let account = all.first(where: { $0.id == id }) else { return }
        currentId = id
        UserDefaults.standard.set(id, forKey: "accounts.current")
        if account.ownIdentity {
            if !account.name.isEmpty { ProfileStore.shared.name = account.name }
            if !account.phone.isEmpty { ProfileStore.shared.phone = account.phone }
        }
        if let avatar = account.avatar { ProfileStore.shared.avatar = avatar }
        // 换了账号，私有协议会话要重新握手（不然还是上一个人的会话）
        DtwSession.shared.reset()
    }

    /// 新增一个账号（对应 Android 的「加号卡」）
    func add(name: String, phone: String, avatar: UIImage?) {
        let id = DtwCrypto.randomHex(8)
        all.append(Account(id: id, name: name, phone: phone, avatar: avatar, ownIdentity: !name.isEmpty))
    }
}

/// 切换账号页 —— `features/zhi/ui/SwitchAccountV2Screen.java` 的 1:1 移植。
///
/// 整页就是若干切图贴回 1280×2816 的参考画布：顶栏、大标题、账号卡、加号卡、底部蓝字。
/// 帐号多到一屏放不下时跟着手指滚（`maxScroll = contentH - H`）。
final class SwitchAccountV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    // 调色板（Java:36-38）
    private let pageBg = Draw.color(0xfff5f5f5)
    private let card = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff333333)

    // 几何（Java:40-74）
    private let headY: CGFloat = 136
    private let titleX: CGFloat = 255
    private let titleY: CGFloat = 545
    private let listTop: CGFloat = 818
    private let cardH: CGFloat = 313
    private let cardGap: CGFloat = 42
    private let avatarX: CGFloat = 113
    private let avatarY: CGFloat = 70
    private let avatarSize: CGFloat = 172
    private let avatarRadius: CGFloat = 28
    private let phoneX: CGFloat = 331
    private let phoneBaseline: CGFloat = 182
    private let phoneSize: CGFloat = 70
    private let nameBaseline: CGFloat = 136
    private let nameSize: CGFloat = 48
    private let phoneBaselineNamed: CGFloat = 214
    private let tagX: CGFloat = 1040
    private let tagDy: CGFloat = -8
    private let tagW: CGFloat = 180
    private let tagH: CGFloat = 85
    private let footerX: CGFloat = 350
    private let footerYDefault: CGFloat = 2598
    private let footerW: CGFloat = 580
    private let footerH: CGFloat = 70

    final class State: ScreenState {}

    private static let artNames = [
        "head": "sw_head", "title": "sw_title", "card": "sw_card", "avatar": "sw_avatar",
        "tag": "sw_tag", "add": "sw_add", "footer": "sw_foot",
    ]
    private var art: [String: UIImage] = [:]

    /// 这一帧算出来的位置（点哪儿要用，Java:87-89）
    private var shown: [AccountsV2.Account] = []
    private var footerY = SwitchAccountV2Screen.H
    private var contentH: CGFloat = SwitchAccountV2Screen.H

    init() {
        for (key, res) in Self.artNames {
            if let image = UIImage(named: res) { art[key] = image }
        }
    }

    func maxScroll(_ state: ScreenState) -> CGFloat {
        max(0, contentH - Self.H)
    }

    // MARK: - 绘制（Java:106-122）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        shown = AccountsV2.shared.all
        let addTop = self.addTop(shown.count)
        footerY = max(footerYDefault, addTop + cardH + 80)
        contentH = max(Self.H, footerY + footerH + 46)

        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)
        d.art(art["head"], CGRect(x: 0, y: headY, width: Self.W, height: artHeight("head")))

        d.ctx.saveGState()
        d.ctx.translateBy(x: 0, y: -s.scroll)

        d.art(art["title"], CGRect(x: titleX, y: titleY, width: artWidth("title"), height: artHeight("title")))
        for (i, account) in shown.enumerated() {
            drawAccount(d, account, top: cardTop(i))
        }
        d.art(art["add"], CGRect(x: 0, y: addTop, width: Self.W, height: artHeight("add")))
        d.art(art["footer"], CGRect(x: footerX, y: footerY, width: artWidth("footer"), height: footerH))
        d.ctx.restoreGState()
    }

    /// Java:124-159
    private func drawAccount(_ d: Draw, _ account: AccountsV2.Account, top: CGFloat) {
        d.art(art["card"], CGRect(x: 0, y: top, width: Self.W, height: cardH))

        let picture = account.avatar ?? art["avatar"]
        let avatarRect = CGRect(x: avatarX, y: top + avatarY, width: avatarSize, height: avatarSize)
        d.clipped(to: avatarRect, radius: avatarRadius) {
            d.art(picture, avatarRect)
        }

        if account.ownIdentity {
            // 自己有名字和号码：盖掉参考图那一行，名字在上、号码在下（Java:142-149）
            d.rect(CGRect(x: 300, y: top + 78, width: 1006 - 300, height: 234 - 78), card)
            d.text(account.name, x: phoneX, baseline: top + nameBaseline, size: nameSize, ink)
            d.text(account.phone, x: phoneX, baseline: top + phoneBaselineNamed, size: phoneSize, ink)
        } else if account.phone != AccountsV2.defaultPhone {
            // 卡片上印的是参考图里那个号，只有别的号才盖掉重画（Java:150-157）
            d.rect(CGRect(x: 300, y: top + 112, width: 1006 - 300, height: 202 - 112), card)
            d.text(account.phone, x: phoneX, baseline: top + phoneBaseline, size: phoneSize, ink)
        }

        if AccountsV2.shared.isCurrent(account.id) {
            d.art(art["tag"], CGRect(x: tagX, y: top + tagDy, width: tagW, height: tagH))
        }
    }

    // MARK: - 位置计算（Java:161-167）

    private func cardTop(_ index: Int) -> CGFloat {
        listTop + CGFloat(index) * (cardH + cardGap)
    }

    private func addTop(_ count: Int) -> CGFloat {
        cardTop(count)
    }

    /// 位图按原始尺寸贴（Android 的 `drawBitmap(bitmap, x, y, paint)` 不缩放）
    private func artWidth(_ key: String) -> CGFloat {
        guard let image = art[key] else { return 0 }
        return image.size.width * image.scale
    }

    private func artHeight(_ key: String) -> CGFloat {
        guard let image = art[key] else { return 0 }
        return image.size.height * image.scale
    }

    // MARK: - 命中（Java:190-212，顺序与区间照抄）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 顶部返回/管理是固定层，用屏幕坐标；账号卡是内容层。
        let rawY = y - s.scroll
        if rawY < headY + 30 { return "noop" }
        if rawY >= 160 && rawY <= 290 {
            if x < 150 { return "back" }
            if x >= 1040 { return "manage" }
        }
        let cy = y
        for (i, account) in shown.enumerated() {
            let top = cardTop(i)
            if cy >= top && cy <= top + cardH {
                if AccountsV2.shared.isCurrent(account.id) { return "toast:这已经是当前账号" }
                return "switch:\(account.id)"
            }
        }
        let addTop = self.addTop(shown.count)
        if cy >= addTop && cy <= addTop + cardH { return "new" }
        if cy >= footerY - 10, cy <= footerY + footerH + 10,
           x >= footerX - 40, x <= footerX + footerW + 40 {
            return "footer"
        }
        return "noop"
    }
}
