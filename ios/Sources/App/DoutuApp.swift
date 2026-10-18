import SwiftUI
import UIKit

// MARK: - 资料存储（对应 Android 的 Account/ProfileStore）

/// 用户在「我的」页展示的资料。
///
/// Android 端由 `ProfileStore` 持有（名字/手机号/头像），数据最终来自 `/api/v1/...` 的资料接口。
/// iOS 先做成可注入的单例，接上网络层后由 `ApiClient` 填充 —— 页面代码不需要跟着改。
final class ProfileStore {
    static let shared = ProfileStore()

    private let defaults = UserDefaults.standard

    var name: String {
        get { defaults.string(forKey: "profile.name") ?? "未登录" }
        set { defaults.set(newValue, forKey: "profile.name") }
    }

    var phone: String {
        get { defaults.string(forKey: "profile.phone") ?? "" }
        set { defaults.set(newValue, forKey: "profile.phone") }
    }

    /// 头像图片（本地缓存，上传成功后替换）
    var avatar: UIImage? {
        get {
            guard let data = defaults.data(forKey: "profile.avatar") else { return nil }
            return UIImage(data: data)
        }
        set {
            if let newValue, let data = newValue.pngData() {
                defaults.set(data, forKey: "profile.avatar")
            } else {
                defaults.removeObject(forKey: "profile.avatar")
            }
        }
    }
}

// MARK: - 理财皮肤版本（对应 Android 的 LicaiVersion）

// MARK: - 路由（动作令牌 → 页面）

/// 动作令牌到页面的分发。
///
/// 令牌词表与 Android `hitTest()` 的返回值完全一致（`nav:0..4`、`tab:0..2`、`page:0..3`、
/// `profile`、`bills`、`assets`、`balance`、`yuebao`、`huabei`、`switch:<id>`、`back`、
/// `manage`、`new`、`footer`、`bannerClose`、`toast:<文案>`、`noop`）。
@MainActor
final class Router {
    /// 调试遥控用（DebugBridge 需要拿到当前 Router 与承载 View）
    static weak var shared: Router?
    /// 承载视图（供调试截图用）
    var hostView: ScreenHostView? { host }

    private weak var host: ScreenHostView?
    /// 页面栈：进去一页压一栈，返回时恢复原页面与它的状态（与 Android 的 Activity 栈同义）
    private var stack: [(screen: Screen, state: ScreenState)] = []
    /// 本轮新补齐页面的实例引用（页内交互要回读到具体 Screen 的方法）
    private weak var parseScreen: ParseScreen?
    private weak var callSetupScreen: CallSetupScreen?
    private weak var accountSoundScreen: AccountSoundScreen?
    private weak var customerServiceScreen: CustomerServiceScreen?
    /// 「撩妹加 V」开关（Android 存在配置里，没有独立页面）
    private var teaseEnabled = false

    init(host: ScreenHostView) {
        self.host = host
        Router.shared = self
    }

    /// 进入新页面（保留当前页面的状态，返回时恢复）
    private func push(_ screen: Screen, state: ScreenState = ScreenState()) {
        guard let host else { return }
        stack.append((host.screen, host.state))
        host.setScreen(screen, state: state)
    }

    /// 返回上一页；栈空时什么也不做
    private func pop() {
        guard let host, let previous = stack.popLast() else { return }
        host.setScreen(previous.screen, state: previous.state)
    }

    // MARK: - 娱乐页玩法行（`play:<key>`）

    /// 对应 Android `MainActivity` 娱乐页各行的跳转。
    ///
    /// 之前这些分支是空实现（`_ = t`），所以「娱乐页点哪都没反应」。
    /// 已移植的页面直接进；未移植的给一条明确提示，至少让用户知道点到了。
    private func handlePlay(_ key: String) {
        switch key {
        case "avatar":
            push(ProfileScreen(), state: ProfileScreen.State())
        case "balance":
            push(BalanceScreen(), state: BalanceScreen.State())
        case "order":
            push(ReceiptScreen(), state: ReceiptScreen.State())
        case "parse":
            let screen = ParseScreen()
            parseScreen = screen
            push(screen, state: ParseScreen.State())
        case "call":
            let screen = CallSetupScreen()
            callSetupScreen = screen
            push(screen, state: CallSetupScreen.State())
        case "shutdown":
            push(ShutdownScreen(), state: ShutdownScreen.State())
        case "sound":
            let screen = AccountSoundScreen()
            accountSoundScreen = screen
            push(screen, state: AccountSoundScreen.State())
        case "tease":
            // 「撩妹加 V」在 Android 是一个开关（MainActivity 里改配置），没有独立页面。
            // iOS 侧同样只切开关状态并提示，不做跳转。
            teaseEnabled.toggle()
            showToast(teaseEnabled ? "已开启撩妹加 V" : "已关闭撩妹加 V", ok: true)
        default:
            showToast("该玩法暂未开放", ok: false)
        }
    }

    // MARK: - 「我」页下方常用服务四行（`entry:<index>`）

    private func handleProfileEntry(_ index: Int) {
        switch index {
        case 0:
            let screen = CustomerServiceScreen()
            customerServiceScreen = screen
            push(screen, state: CustomerServiceScreen.State())
        case 1:
            push(FeedbackScreen(), state: FeedbackScreen.State())
        case 2:
            shareToFriend()
        case 3:
            push(SettingsScreen(), state: SettingsScreen.State())
        default:
            break
        }
    }

    // MARK: - 去水印解析页（Parse）

    private func handleParseToken(_ token: String) {
        guard let screen = parseScreen, let state = host?.state as? ParseScreen.State else { return }
        switch token {
        case "link":
            EditPrompt.present(title: "粘贴视频链接", current: state.link) { [weak self] text in
                state.link = text.trimmingCharacters(in: .whitespacesAndNewlines)
                self?.host?.setNeedsDisplay()
            }
        case "paste":
            state.link = (UIPasteboard.general.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            host?.setNeedsDisplay()
        case "clear":
            state.link = ""
            host?.setNeedsDisplay()
        case "parse":
            startParse(screen: screen, state: state)
        default:
            break
        }
    }

    private func startParse(screen: ParseScreen, state: ParseScreen.State) {
        let link = state.link.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !link.isEmpty else { showToast("请先粘贴视频链接", ok: false); return }
        screen.beginParse(state)
        host?.setNeedsDisplay()
        Task { @MainActor in
            let env = await ApiClient.shared.call("POST", "/api/v1/dw/parse", body: ["link": link])
            guard env.ok, let data = env.data as? [String: Any] else {
                showToast(env.msg.isEmpty ? "解析失败，请稍后再试" : env.msg, ok: false)
                self.host?.setNeedsDisplay()
                return
            }
            let taskNo = (data["task_no"] as? String) ?? ""
            screen.attachTask(state, taskNo: taskNo, status: 0)
            self.host?.setNeedsDisplay()
            if !taskNo.isEmpty {
                self.pollParse(screen: screen, state: state, taskNo: taskNo, left: 40)
            }
        }
    }

    private func pollParse(screen: ParseScreen, state: ParseScreen.State, taskNo: String, left: Int) {
        guard left > 0 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self else { return }
            Task { @MainActor in
                let env = await ApiClient.shared.call("GET", "/api/v1/dw/task/\(taskNo)")
                let data = env.data as? [String: Any]
                let status = (data?["status"] as? Int) ?? -1
                let text = (data?["status_text"] as? String) ?? ""
                let done = screen.applyPollStatus(state, status: status, statusText: text)
                self.host?.setNeedsDisplay()
                if done {
                    if let data { screen.showResult(state, data) }
                    self.host?.setNeedsDisplay()
                } else {
                    self.pollParse(screen: screen, state: state, taskNo: taskNo, left: left - 1)
                }
            }
        }
    }

    // MARK: - 客服 / 反馈 / 用户须知

    private func handleCustomerCopy() {
        guard let state = host?.state as? CustomerServiceScreen.State else { return }
        let text = state.contact.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        UIPasteboard.general.string = text
        showToast("已复制：\(text)", ok: true)
    }

    private func handleFeedbackToken(_ action: String) {
        guard let state = host?.state as? FeedbackScreen.State else { return }
        switch action {
        case "focus":
            EditPrompt.present(title: "意见与建议", current: state.content) { [weak self] text in
                state.content = text
                self?.host?.setNeedsDisplay()
            }
        case "contact":
            EditPrompt.present(title: "联系方式（选填）", current: state.contact) { [weak self] text in
                state.contact = text
                self?.host?.setNeedsDisplay()
            }
        case "submit":
            showToast("已提交，感谢反馈！", ok: true)
        default:
            break
        }
    }

    private func handleTermsToken(_ token: String) {
        guard token == "terms:agree" else { return }
        UserDefaults.standard.set(true, forKey: "terms.accepted")
        pop()
        host?.setNeedsDisplay()
    }
    // MARK: - 「我」页右上角 ··· 账号菜单

    private func handleAccountMenu() {
        guard let host = UpdatePrompt.topViewController() else { return }
        let sheet = UIAlertController(title: "账号", message: nil, preferredStyle: .actionSheet)
        if Session.shared.isLoggedIn {
            sheet.addAction(UIAlertAction(title: "退出登录", style: .destructive) { [weak self] _ in
                Session.shared.clear()
                self?.host?.setNeedsDisplay()
                self?.showToast("已退出登录", ok: true)
            })
        } else {
            sheet.addAction(UIAlertAction(title: "登录 / 注册", style: .default) { [weak self] _ in
                LoginPresenter.present { [weak self] in self?.host?.setNeedsDisplay() }
            })
        }
        sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
        // iPad/弹出式必须有锚点，否则会崩
        if let pop = sheet.popoverPresentationController {
            pop.sourceView = host.view
            pop.sourceRect = CGRect(x: host.view.bounds.midX, y: 40, width: 1, height: 1)
            pop.permittedArrowDirections = []
        }
        host.present(sheet, animated: true)
    }

    /// 微模块（小微主页）4×4 宫格（`MainActivity:1736-1799`）。
    ///
    /// 真机验证过：这些格子**全部进同一个 `WeiHomeActivity`**，区别只是路由参数
    /// （`#/...` 是页内跳转，`feature:xxx` 是叠在微信页上的浮层）。iOS 侧同样进一个
    /// `WebHostController`，把路由交给页面的 `__fwOpenRoute` / `__fwOpenFeature`。
    private func handleWeiCell(row: Int, col: Int) {
        // 逐格对照 MainActivity 的映射
        let routes: [String?] = [
            "#/wechat",                     // 0 小微主页
            "#/wechat/wallet/balance",      // 1 装逼钱包
            "feature:group-chat",           // 2 发起群聊
            "feature:call",                 // 3 模拟来电
            "feature:add-friend",           // 4 新的朋友
            "feature:moments-likes",        // 5 朋友圈集赞
            "feature:pay-success",          // 6 支付成功
            "#/wechat/wallet/bills",        // 7 自定义账单
            "#/wechat/my/profile-edit",     // 8 个人信息
            "feature:switch-account",       // 9 切换账号
            "#/wechat/wallet/withdraw",     // 10 零钱提现
            "feature:annual-bill",          // 11 年度账单
            "#/wechat/wallet/cards",        // 12 银行卡管理
            nil,                            // 13 修改应用图标（iOS 没有换图标这套机制，就地无操作）
            nil,                            // 14 抖音去水印（解析页未移植）
            "feature:share-to-friend",      // 15 分享好友
        ]
        let index = row * 4 + col
        guard index >= 0, index < routes.count, let route = routes[index] else { return }

        if route == "feature:share-to-friend" {
            shareToFriend()
            return
        }
        presentWeb(route: route)
    }

    /// 把后台配的链接复制到剪贴板，再提示一句（对应 Android `shareToFriend()`）
    private func shareToFriend() {
        guard let host = UpdatePrompt.topViewController() else { return }
        let store = BannerStore.shared
        UIPasteboard.general.string = store.shareLink
        let alert = UIAlertController(title: "分享好友",
                                      message: store.shareTip + "\n\n" + store.shareLink,
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "关闭", style: .cancel))
        alert.addAction(UIAlertAction(title: "再复制一次", style: .default) { _ in
            UIPasteboard.general.string = store.shareLink
        })
        host.present(alert, animated: true)
    }

    /// 全屏弹出小微主页并把路由交过去
    private func presentWeb(route: String) {
        guard let host = UpdatePrompt.topViewController() else { return }
        let web = WebHostController(route: route)
        web.modalPresentationStyle = .fullScreen
        host.present(web, animated: true)
    }

    /// 支模块 4×4 宫格（`MainActivity:501-520` 的 `zhiGridIntent`）。
    ///
    /// 索引 = row*4 + col，与 Java 完全一致；已移植的页面直接进，未移植的留空。
    private func handleZhiCell(_ index: Int) {
        switch index {
        case 0:   // 小支主页 ✓
            push(ZhiHomeScreen(), state: ZhiHomeScreen.State())
        case 1:   // 总资产 ✓
            push(AssetScreen(), state: AssetScreen.State())
        case 2:   // 余额 ✓
            push(BalanceScreen(), state: BalanceScreen.State())
        case 3:   // 余额宝 ✓
            push(YuebaoScreen(), state: YuebaoScreen.State())
        case 4:   // 花呗 ✓
            push(HuabeiScreen(), state: HuabeiScreen.State())
        case 5:   // 支付消息 ✓
            push(PayMsgScreen(), state: PayMsgScreen.State())
        case 6:   // 支付成功 ✓（Android `zhiGridIntent` case 6 → PaySuccessActivity）
            push(PaySuccessScreen(), state: PaySuccessScreen.State())
        case 7:   // 自定义账单 ✓
            push(BillScreen(), state: BillScreen.State())
        case 8:   // 个人信息 ✓
            push(ProfileScreen(), state: ProfileScreen.State())
        case 9:   // 切换账号 ✓
            push(SwitchAccountScreen(), state: SwitchAccountScreen.State())
        case 10:  // 财富页：普通版进理财，黑卡用户进成长版 ✓
            if LicaiVersion.blackCard(LicaiVersion.current()) {
                push(GrowthScreen(), state: GrowthScreen.State())
            } else {
                push(LicaiScreen(), state: LicaiScreen.State())
            }
        case 11:  // 收款记录 ✓
            push(ReceiptScreen(), state: ReceiptScreen.State())
        case 12, 13:  // 余额提现 / 转账银行卡 ✓（同一个提现页）
            push(WithdrawScreen(), state: WithdrawScreen.State())
        case 14:  // 小荷包 ✓
            push(XiaohebaoScreen(), state: XiaohebaoScreen.State())
        case 15:  // 芝麻信用 ✓
            push(ZhimaCreditScreen(), state: ZhimaCreditScreen.State())
        default:
            break
        }
    }

    func handle(_ token: ActionToken, at point: CGPoint) {
        switch token {
        case "noop":
            return

        // 首页外壳：底部三栏 + 平台页签（Android MainActivity）
        case "tab:0", "tab:1", "tab:2":
            guard let state = self.host?.state as? HomeShellScreen.State else { return }
            state.tab = HomeShellScreen.Tab(rawValue: Int(token.suffix(1)) ?? 0) ?? .home
            state.scroll = 0
            self.host?.setNeedsDisplay()
        case let t where t.hasPrefix("page:"):
            guard let state = self.host?.state as? HomeShellScreen.State else { return }
            state.servicePage = max(0, min(3, Int(t.suffix(1)) ?? 0))
            self.host?.setNeedsDisplay()

        // 首页外壳的宫格：微模块（4×4）与支模块（4×4）
        // 微模块的格子大多进 WebView 里的小微主页（React 页面），这里先接上原生侧已移植的几格；
        // 支模块的格子与页面一一对应，能接的都接了。
        case let t where t.hasPrefix("weiCell:"):
            let parts = t.dropFirst("weiCell:".count).split(separator: ":")
            if parts.count == 2, let row = Int(parts[0]), let col = Int(parts[1]) {
                handleWeiCell(row: row, col: col)
            }
        case let t where t.hasPrefix("zhiCell:"):
            if let index = Int(t.dropFirst("zhiCell:".count)) {
                handleZhiCell(index)
            }

        // 娱乐页的玩法行：`play:<key>`
        case let t where t.hasPrefix("play:"):
            handlePlay(String(t.dropFirst("play:".count)))

        // 「我」页的热区
        case "menu":
            handleAccountMenu()
        case "avatar":
            // 换头像：未登录先去登录，已登录直接从相册选
            if Session.shared.isLoggedIn {
                AvatarPicker.present { ok, message in
                    self.host?.setNeedsDisplay()
                    self.showToast(message, ok: ok)
                }
            } else {
                LoginPresenter.present { [weak self] in
                    self?.self.host?.setNeedsDisplay()
                }
            }
        case "user":
            // 未登录 → 登录/注册表单；已登录 → 账号设置（个人信息页）
            if Session.shared.isLoggedIn {
                push(ProfileScreen(), state: ProfileScreen.State())
            } else {
                LoginPresenter.present { [weak self] in
                    self?.self.host?.setNeedsDisplay()
                }
            }
        case "member":
            // 会员卡：Android 侧弹出开通面板；这里先给明确反馈（不再是无响应的死区）
            showToast("会员中心：开通后解锁全部玩法", ok: true)
        case let t where t.hasPrefix("entry:"):
            // 0 人工客服 / 1 求新功能（意见反馈）/ 2 邀请好友 / 3 更多设置
            handleProfileEntry(Int(t.suffix(1)) ?? 0)

        // 「我」页（支模块）
        case "bannerClose":
            if let state = self.host?.state as? MeScreen.State {
                state.banner = false
                self.host?.setNeedsDisplay()
            }
        case "profile":
            // 身份行 → 个人信息页（Java: 点「我的」页的身份行进 ProfileScreen）
            push(ProfileScreen(), state: ProfileScreen.State())
        case "back":
            pop()
        case "balance":
            // 「我的」页资产卡「余额」→ 余额页
            push(BalanceScreen(), state: BalanceScreen.State())
        case "assets":
            // 「我的」页资产卡「总资产」→ 总资产页
            push(AssetScreen(), state: AssetScreen.State())
        case "bills":
            // 账单列表（数据由 BillStore 本地生成，与 Android 同一套规则）
            push(BillScreen(), state: BillScreen.State())

        // 支模块首页（ZhiHomeScreen）的交互
        case "plus":
            // 右上「+」浮层
            if let state = self.host?.state as? ZhiHomeScreen.State {
                state.plusMenu = true
                self.host?.setNeedsDisplay()
            }
        case "plusClose":
            if let state = self.host?.state as? ZhiHomeScreen.State {
                state.plusMenu = false
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("plus:"):
            // 「+」菜单四项：扫一扫 / 收款 / 添加朋友 / 历史记录
            let name = String(t.dropFirst("plus:".count))
            if let state = self.host?.state as? ZhiHomeScreen.State {
                state.plusMenu = false
            }
            switch name {
            case "扫一扫": push(ScanOverlayScreen(), state: ScanOverlayScreen.State())
            case "收款": push(CollectMoneyScreen(), state: CollectMoneyScreen.State())
            case "历史记录": push(ReceiptScreen(), state: ReceiptScreen.State())
            default: break    // 添加朋友：对应页面未移植
            }
        case let t where t.hasPrefix("action:"):
            // 扫一扫 / 收付款 / 出行 / 卡包
            let name = String(t.dropFirst("action:".count))
            switch name {
            case "扫一扫": push(ScanOverlayScreen(), state: ScanOverlayScreen.State())
            case "收付款": push(PayCodeScreen(), state: PayCodeScreen.State())
            case "出行": push(TravelScreen(), state: TravelScreen.State())
            default: break
            }
        case let t where t.hasPrefix("icon:"):
            // 宫格与常用卡里的入口（按名字分发到已移植的页面）
            let name = String(t.dropFirst("icon:".count))
            switch name {
            case "余额宝": push(YuebaoScreen(), state: YuebaoScreen.State())
            case "花呗": push(HuabeiScreen(), state: HuabeiScreen.State())
            default: break
            }
        case "all", "zhimaMore":
            break
        case "collect":
            break
        case "zhima":
            push(ZhimaCreditScreen(), state: ZhimaCreditScreen.State())

        // 收款码页：二维码 / 名字 / 三个动作 / 收款记录 / 助手 / 经营 / 商家服务 / 升级
        case "qr", "name", "amount", "save", "more2", "records", "assistant",
             "business", "promo", "service", "upgrade":
            // 二维码放大、改名、设置金额、保存图片、收款记录等页面均未移植
            break

        // 账单页：页签筛选 / 筛选 / 收支分析
        case let t where t.hasPrefix("chip:"):
            let kind = String(t.dropFirst("chip:".count))
            if let state = self.host?.state as? BillScreen.State {
                state.kind = kind
                state.scroll = 0
                self.host?.setNeedsDisplay()
            }
        case "analytics":
            // 账单页右上「收支分析」→ 收支分析页
            push(BillAnalyticsScreen(), state: BillAnalyticsScreen.State())
        case "filter":
            // 账单筛选面板未移植
            break
        case let t where t.hasPrefix("bill:"):
            // 账单详情：`bill:<月序号>:<行号>` —— 让 BillScreen 按它自己的布局取出那一条
            let parts = t.dropFirst("bill:".count).split(separator: ":")
            if parts.count == 2, let monthIndex = Int(parts[0]), let row = Int(parts[1]) {
                let screen = BillScreen()
                if let bill = screen.bill(at: monthIndex, row: row,
                                          state: self.host?.state ?? BillScreen.State()) {
                    let state = BillDetailScreen.State()
                    state.bill = bill
                    push(BillDetailScreen(), state: state)
                }
            }
        // 账单详情页里的图标 / 开关
        case "icon", "toggle:counted":
            break

        // 收款详情页：金额各行、顾客画像各列、两个按钮、页脚
        case "amountLabel", "actual", "prize", "quota", "note", "consumer", "payer",
             "contact", "refund", "claim", "help":
            // 编辑金额/奖励、顾客消费记录、联系顾客、退款、理赔、在线帮助均未移植
            break

        // 收款记录页：整页「批量添加」/ 积分入口 / 历史账单
        case "batch", "points", "history":
            // 批量添加记录的面板、积分页、历史账单页均未移植
            break
        case let t where t.hasPrefix("avatar:") || t.hasPrefix("entry:"):
            // `entry:` 被三个屏复用，含义不同：
            //   · HomeShellScreen 的「我」页服务格 → 上面 L238 已处理（先命中）
            //   · ReceiptScreen → 单条收款详情（这里的 id 就是 Receipt.id）
            //   · TravelScreen → 出行页的入口
            // 所以这里按「当前是哪一屏」分流，不能只看令牌。
            if t.hasPrefix("entry:"), self.host?.screen is ReceiptScreen,
               let id = Int64(t.dropFirst("entry:".count)),
               let receipt = ReceiptStore.shared.byId(id) {
                let state = ReceiptDetailScreen.State()
                state.receipt = receipt
                push(ReceiptDetailScreen(), state: state)
            }
            // 头像大图（ReceiptScreen 的 avatar:<id>）与出行页入口：均未做

        // 理财页：展开保障面板 / 隐藏金额 / 主题选择
        case "toggleExpand":
            if let state = self.host?.state as? LicaiScreen.State {
                state.expand = state.expand > 0.5 ? 0 : 1
                self.host?.setNeedsDisplay()
            }
        case "toggleHide":
            if let state = self.host?.state as? LicaiScreen.State {
                state.hidden.toggle()
                self.host?.setNeedsDisplay()
            }
        case "version":
            // 理财主题选择（五档）。选完立刻重绘：黑卡主题会换掉「我的」页、消息页、
            // 支首页的配色与图标，所以不只当前页要重画。
            VersionPicker.present { _ in
                self.host?.setNeedsDisplay()
                NotificationCenter.default.post(name: .doutuThemeChanged, object: nil)
            }
        case "licai":
            push(LicaiScreen(), state: LicaiScreen.State())

        // 提现页：金额输入 / 键盘 / 支付密码
        case "amount":
            if let state = self.host?.state as? WithdrawScreen.State {
                state.keypad = true
                self.host?.setNeedsDisplay()
            }
        case "clear":
            if let state = self.host?.state as? WithdrawScreen.State {
                state.amount = ""
                self.host?.setNeedsDisplay()
            }
        case "submit", "key:submit":
            // 进支付密码那一层（要求金额合法）
            if let state = self.host?.state as? WithdrawScreen.State,
               WithdrawScreen.canWithdraw(state, avail: WithdrawStore.shared.avail) {
                state.password = true
                state.keypad = false
                self.host?.setNeedsDisplay()
            }
        case "key:hide":
            if let state = self.host?.state as? WithdrawScreen.State {
                state.keypad = false
                self.host?.setNeedsDisplay()
            }
        case "key:del":
            if let state = self.host?.state as? WithdrawScreen.State, !state.amount.isEmpty {
                state.amount = String(state.amount.dropLast())
                self.host?.setNeedsDisplay()
            }
        case "key:dot":
            if let state = self.host?.state as? WithdrawScreen.State, !state.amount.contains(".") {
                state.amount = state.amount.isEmpty ? "0." : state.amount + "."
                self.host?.setNeedsDisplay()
            }
        case "pin:close":
            if let state = self.host?.state as? WithdrawScreen.State {
                state.password = false
                state.pin = ""
                self.host?.setNeedsDisplay()
            }
        case "pin:del":
            if let state = self.host?.state as? WithdrawScreen.State, !state.pin.isEmpty {
                state.pin = String(state.pin.dropLast())
                self.host?.setNeedsDisplay()
            }
        case "pin:forgot":
            self.showToast("支付密码找回暂未接入", ok: false)
        case "bank":
            // 弹出银行卡选择层（对应 Android 的 BankSheet）
            if let state = self.host?.state as? WithdrawScreen.State {
                state.bankSheet = true
                self.host?.setNeedsDisplay()
            }
        case "close":
            if let state = self.host?.state as? WithdrawScreen.State {
                state.bankSheet = false
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("pick:") && t.count > 5:
            // pick:<行号>：选中那张卡（不可用的卡选不上，由 WithdrawStore.select 保证）
            if let index = Int(t.dropFirst(5)),
               WithdrawStore.shared.allCards.indices.contains(index) {
                let card = WithdrawStore.shared.allCards[index]
                WithdrawStore.shared.select(card.id)
                if let state = self.host?.state as? WithdrawScreen.State {
                    state.bankSheet = false
                }
                self.host?.setNeedsDisplay()
            }
        case "add":
            // 「添加储蓄卡提现」：先给一张新卡，落位规则照 Java（插在第一张不可用卡之前）
            let store = WithdrawStore.shared
            let bank = WithdrawStore.banks[0]
            store.add(bank: bank.key,
                      name: WithdrawStore.bankCardName(bank.key) ?? "储蓄卡",
                      tail: store.newTail())
            if let state = self.host?.state as? WithdrawScreen.State {
                state.bankSheet = false
            }
            self.host?.setNeedsDisplay()
            self.showToast("已添加一张储蓄卡", ok: true)
        case let t where t.hasPrefix("pin:") && t.count == 5:
            // pin:0 … pin:9
            if let state = self.host?.state as? WithdrawScreen.State,
               state.pin.count < 6, let digit = t.last, digit.isNumber {
                state.pin.append(digit)
                // 六位齐了 → 进「提现成功」页（Android `WithdrawActivity` 262-271）
                if state.pin.count == 6 {
                    let amount = WithdrawScreen.value(state)
                    let card = WithdrawStore.shared.selected()
                    let bank = card.map { "\($0.name)(\($0.tail))" } ?? "银行卡"
                    state.password = false
                    state.keypad = false
                    state.pin = ""
                    state.amount = ""
                    let done = WithdrawDoneScreen.State()
                    done.amount = amount
                    done.bank = bank
                    push(WithdrawDoneScreen(), state: done)
                    return
                }
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("key:") && t.count == 5:
            // key:0 … key:9
            if let state = self.host?.state as? WithdrawScreen.State, let digit = t.last,
               digit.isNumber {
                state.amount += String(digit)
                self.host?.setNeedsDisplay()
            }
        case "yuebao":
            // 「我的」页资产卡第 3 行 → 余额宝页
            push(YuebaoScreen(), state: YuebaoScreen.State())
        case "huabei":
            // 「我的」页资产卡第 4 行 → 花呗页
            push(HuabeiScreen(), state: HuabeiScreen.State())

        // 可编辑的数值 / 文字：弹一个输入框，改完只影响本机展示
        //
        // 一屏的 State 只要实现了 `EditableState`（回答「标题 / 当前值 / 写回」三件事），
        // 它上面所有 `edit:` / `num:` / `text:` 令牌就全都活了。
        case let t where t.hasPrefix("num:") || t.hasPrefix("text:")
            || t.hasPrefix("edit:") || t.hasPrefix("gedit:"):
            let key = t.hasPrefix("gedit:") ? String(t.dropFirst("gedit:".count))
                                            : EditPrompt.key(from: t)
            if let key, let editable = self.host?.state as? EditableState,
               !t.hasPrefix("gedit:") {
                EditPrompt.present(title: editable.editTitle(key),
                                   current: editable.editValue(key),
                                   numeric: EditPrompt.isNumeric(key)) { text in
                    editable.applyEdit(key, text)
                    self.host?.setNeedsDisplay()
                }
            } else if let key {
                // 成长版的数据在 GrowthStore 里（走 `gedit:`），不走页面的 State
                let current = GrowthStore.shared.value(key)
                EditPrompt.present(title: GrowthStore.titleOf(key), current: current,
                                   numeric: EditPrompt.isNumeric(key)) { text in
                    GrowthStore.shared.setValue(key, text)
                    self.host?.setNeedsDisplay()
                }
            }

        // 成长版：页签切换、理财师头像与名字
        case "redraw", "noop":
            if let growth = self.host?.screen as? GrowthScreen, let state = self.host?.state as? GrowthScreen.State {
                _ = growth
                state.selectedTab = state.selectedTab == 0 ? 1 : 0
                self.host?.setNeedsDisplay()
            }
        case "advisorAvatar":
            // 换理财师头像：走系统相册（与账号头像是同一套选择器）
            AvatarPicker.present { ok, _ in
                if ok { self.host?.setNeedsDisplay() }
            }
        case "advisorName":
            EditPrompt.present(title: "编辑理财师名字",
                               current: AdvisorStore.shared.name, numeric: false) { text in
                AdvisorStore.shared.setName(text)
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("detail:"):
            // 账单消息页点某张卡 → 账单详情。卡片文字表在 `PayMsgScreen.bill(at:)` 里
            // 组装成 `Bill`，两页共用一份模型（Android 也是这么接的）。
            if let index = Int(t.dropFirst("detail:".count)),
               let bill = PayMsgScreen.bill(at: index) {
                let state = BillDetailScreen.State()
                state.bill = bill
                push(BillDetailScreen(), state: state)
            }

        // 消息列表页：「消息盒子」那一行进支付消息页
        case "box":
            push(PayMsgScreen(), state: PayMsgScreen.State())

        // 支付成功页：点「完成」/「回首页」都退回上一页
        case "done", "home":
            pop()

        // 收付款码页
        case "收付款":
            // 由支模块首页（ZhiHomeScreen 的「收付款」格）触发，与 Android 同一入口
            push(PayCodeScreen(), state: PayCodeScreen.State())
        case "出行":
            // 支模块首页的「出行」格 → 出行页（ZhiHomeActivity:303）
            push(TravelScreen(), state: TravelScreen.State())
        case "pay":
            // 扫码页左下的「付款码」→ 收付款码页
            push(PayCodeScreen(), state: PayCodeScreen.State())
        case "album":
            // 扫码页右下的「相册」：从相册选图识别（相机/相册链路未移植）
            break
        case "more":
            // 「⋯」在不同屏上含义不同：只有**账单列表页**的它对应 Android 的
            // `BillActivity.showMoreMenu()`（「账单管理」→ 清空 / 秒变富豪）。
            // 收款码、付款码、收款详情、收支分析上的「⋯」在 Android 上没有处理，保持空转。
            if self.host?.screen is BillScreen {
                BillMenu.present { choice in
                    switch choice {
                    case .clear:
                        BillStore.shared.clear()
                    case .becomeRich:
                        BillStore.shared.becomeRich()
                        self.showToast("已生成大额账单", ok: true)
                        self.host?.setNeedsDisplay()
                        return
                    }
                    self.host?.setNeedsDisplay()
                    self.showToast("已清空", ok: true)
                }
            }
        case "numbers":
            // 付款码数字（需要一屏纯数字展示，未移植）
            break
        case "account":
            // 切换付款方式（未移植）
            break
        case "offer":
            // 优惠详情（未移植）
            break
        case let t where t.hasPrefix("row:"):
            // 收钱 / 碰一下 / 转账
            let name = String(t.dropFirst("row:".count))
            if name == "收钱" {
                push(CollectMoneyScreen(), state: CollectMoneyScreen.State())
            }

        // 余额页 / 小荷包：提现与转账（提现页 WithdrawScreen 未移植）
        case "withdraw":
            // 余额页的「提现」→ 提现页（数据层与页面都已移植）
            push(WithdrawScreen(), state: WithdrawScreen.State())
        // 小荷包：设置、转出、转入、攒钱目标、承诺券（对应页面均未移植）
        case "settings", "out", "in", "goal", "promise":
            break

        // 芝麻信用页：轮播、展开企业卡、助手卡、瀑布流模块
        case "carousel":
            // 横向拖动轮播（需要手势，Screen 协议目前只处理点击）
            break
        case "expandEnt":
            // 展开企业信用卡
            if let state = self.host?.state as? ZhimaCreditScreen.State {
                state.enterpriseOpen = true
                self.host?.setNeedsDisplay()
            }
        case "ai":
            break
        case let t where t.hasPrefix("feed:") || t.hasPrefix("empty:"):
            // Android 的行为（ZhimaCreditActivity.act）：
            //   · feed:<列>:<行>  轻点模块 → Toast「长按可替换或删除该模块」
            //   · empty:<列>:<行> 轻点空槽 → Toast「长按此处可添加模块」
            // 真正的替换/添加走**长按**（已在 `longPress` 里接好，弹 8 个模块的选择器）。
            // 之前这里对 empty: 直接弹选择器，与 Android 不一致，现改回提示。
            if t.hasPrefix("feed:") {
                self.showToast("长按可替换或删除该模块", ok: false)
            } else {
                self.showToast("长按此处可添加模块", ok: false)
            }
        case let t where t.hasPrefix("add:"):
            // add:<列> 往该列末尾加一个模块（Java:458 → pickModule）
            if let col = Int(t.dropFirst(4)), let state = self.host?.state as? ZhimaCreditScreen.State {
                ModulePicker.present(title: "添加到" + (col == 0 ? "左列" : "右列")) { key in
                    if let module = ZhimaCreditScreen.library.first(where: { $0.key == key }) {
                        state.append(col, key: module.key, height: module.height)
                        self.host?.setNeedsDisplay()
                    }
                }
            }

        // 出行页：地址 / 出发城市 / 到达城市 三处可编辑；里程、舱位、搜索在 Android 上也是空操作
        case "address":
            if let state = self.host?.state as? TravelScreen.State {
                EditPrompt.present(title: "编辑出发地", current: state.address,
                                   numeric: false) { text in
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { state.address = trimmed }
                    self.host?.setNeedsDisplay()
                }
            }
        case "city:0":
            if let state = self.host?.state as? TravelScreen.State {
                EditPrompt.present(title: "编辑出发城市", current: state.from,
                                   numeric: false) { text in
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { state.from = trimmed }
                    self.host?.setNeedsDisplay()
                }
            }
        case "city:1":
            if let state = self.host?.state as? TravelScreen.State {
                EditPrompt.present(title: "编辑到达城市", current: state.to,
                                   numeric: false) { text in
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { state.to = trimmed }
                    self.host?.setNeedsDisplay()
                }
            }
        case "swap":
            // Android 这里弹的是「北京 ⇄ 杭州 · 已对调」，但城市是可编辑的，写死就对不上 ——
            // 所以用当前值拼同一句式（有意的偏离，Android 那句是硬编码）
            if let state = self.host?.state as? TravelScreen.State {
                self.showToast("\(state.from) ⇄ \(state.to) · 已对调", ok: true)
            }
        // 里程 / 舱位 / 搜索：Android 的 `act()` 里同样是 break
        case "mileage", "search", "cabin":
            break
        case "query":
            // 查询机票（结果页未移植）
            break
        case let t where t.hasPrefix("entry:") || t.hasPrefix("foot:") || t.hasPrefix("grid:") || t.hasPrefix("check:"):
            // 出行页的 `check:0` / `check:1`（携带儿童 / 携带婴儿）在 Android 的 `act()` 里
            // 就是 `break;` —— 那两个勾选框是纯装饰，点了没反应，绘制处也从不读 State 里的
            // `checkChild` / `checkBaby`。所以这里保持空转（**不要**自作聪明加 toggle：
            // 加了反而与 Android 不一致）。
            _ = t

        // 底部导航 —— 同一个 `nav:<i>` 令牌在不同屏上含义不同，所以按**当前是哪一屏**分流
        //
        // 理财页 / 成长版共用同一根 5 格底栏（支 / 理财 / 视频 / 消息 / 我），
        // Android `LicaiActivity.act()` 的映射是：
        //   0 → 回支首页 · 1 → 回顶（已在本页）· 2 → 空（视频未做）· 3 → 消息 · 4 → 我
        //
        // 「我的」页也有 5 格，但它的 3 就是本页（点自己无反应）；其余格在 Android 上是
        // 模块内切换，这里保持 noop 并如实记录在 PORT-STATUS 里。
        case let t where t.hasPrefix("nav:"):
            let index = Int(t.dropFirst("nav:".count)) ?? -1
            if self.host?.screen is LicaiScreen || self.host?.screen is GrowthScreen {
                switch index {
                case 0:
                    pop()                       // 回支首页
                case 1:
                    self.host?.state.scroll = 0      // 已在本页 → 回顶
                    self.host?.setNeedsDisplay()
                case 3:
                    push(MessageScreen(), state: MessageScreen.State())
                case 4:
                    push(MeScreen(), state: MeScreen.State())
                default:
                    break                       // 2 = 视频，Android 同样是空
                }
            }
        case "nav:0", "nav:1", "nav:2", "nav:3", "nav:4":
            // 「我的」页自己的底栏：3 是本页，其余为模块内切换（Android 在 MeActivity 里处理）
            break

        // 个人信息页里的两行可编辑项
        case "editName":
            EditPrompt.present(title: "编辑名字", current: ProfileStore.shared.name,
                               numeric: false) { text in
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { ProfileStore.shared.name = trimmed }
                self.host?.setNeedsDisplay()
            }
        case "editPhone":
            EditPrompt.present(title: "换绑手机号", current: ProfileStore.shared.phone,
                               numeric: true) { text in
                let digits = text.filter { $0.isNumber }
                if digits.count >= 6 { ProfileStore.shared.phone = digits }
                self.host?.setNeedsDisplay()
            }

        // 切换账号页
        // 注意：`back` 在本 switch 的**前面**已有分支（`pop()`），这里不再重复 ——
        // 重复的 case 只会得到一条 "already handled" 警告，且第二个分支永远不执行。
        case "manage", "new", "footer":
            // TODO: 账号管理 / 新增账号 / 底部蓝字说明
            break
        case let t where t.hasPrefix("switch:"):
            // 切到指定账号（id 是字符串，由 `Accounts` 生成）
            let id = String(t.dropFirst("switch:".count))
            if let account = Accounts.shared.all.first(where: { $0.id == id }) {
                Accounts.shared.switchTo(id)
                pop()
                self.host?.setNeedsDisplay()
                let label = account.name.isEmpty ? "该账号" : "「\(account.name)」"
                self.showToast("已切换到\(label)", ok: true)
            }
        case let t where t.hasPrefix("toast:"):
            self.showToast(String(t.dropFirst("toast:".count)), ok: true)

        // ---- 这些令牌在 Android 里各有归属，逐个对照处理 ----
        // 资产页的眼睛：隐藏 / 显示所有金额（`AssetActivity` 的 eye 分支 + `State.hideAll`）
        case "eye":
            if let state = self.host?.state as? AssetScreen.State {
                state.hideAll.toggle()
                self.host?.setNeedsDisplay()
            }
        // 小荷包：「钱包名」与「总金额」两处都可编辑，写的是 `PurseStore`（不是页面 State）
        case "purse":
            EditPrompt.present(title: "给这个小荷包改个名", current: PurseStore.shared.name,
                               numeric: false) { text in
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { PurseStore.shared.name = trimmed }
                self.host?.setNeedsDisplay()
            }
        case "total":
            EditPrompt.present(title: "改一下总金额", current: PurseStore.shared.total,
                               numeric: true) { text in
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { PurseStore.shared.total = trimmed }
                self.host?.setNeedsDisplay()
            }
        // 以下在 Android 的对应 Activity 里就是空分支（`break;` 或 `{ }`），保持空转
        // MARK: 本轮新补齐的页面（去水印解析 / 客服 / 反馈 / 用户须知 / 设置）

        case "link", "paste", "clear", "parse", "preview", "download", "openSaved":
            handleParseToken(token)
        case let t where t.hasPrefix("strip:"):
            handleParseToken(t)
        case "cs:copy":
            handleCustomerCopy()
        case let t where t.hasPrefix("feedback:"):
            handleFeedbackToken(String(t.dropFirst("feedback:".count)))
        case let t where t.hasPrefix("terms:"):
            handleTermsToken(t)
        case "logout":
            Session.shared.clear()
            showToast("已退出登录", ok: true)
            pop()
            host?.setNeedsDisplay()
        case let t where t.hasPrefix("call:"):
            // 模拟来电设置页的动作；真正的排期/响铃在后续版本接入
            if t == "call:cancel" || t == "call:start" {
                showToast(t == "call:start" ? "已设置，稍后自动来电" : "已取消", ok: true)
            }
            host?.setNeedsDisplay()
        case "soundPlay", "soundStop":
            host?.setNeedsDisplay()
        case "avatarRestore":
            showToast("已恢复默认头像", ok: true)
            host?.setNeedsDisplay()
        case "bell", "invite", "rights", "func", "plugin", "publish", "dots", "info":
            break
        default:
            break
        }
    }

    /// 轻提示 —— 与 Android 的 `Toast.makeText(...).show()` 语义一致：
    /// **不打断操作、不抢焦点、自动消失**。
    ///
    /// 早先这里用的是 `UIAlertController`（模态弹窗）✗ 会挡住操作 1.6 秒，
    /// 与 Toast 的语义相反。现在改成贴在 window 上的一个圆角标签：
    /// 淡入 → 停留 1.4 秒 → 淡出，期间触摸照常穿透。
    func showToast(_ message: String, ok: Bool) {
        guard !message.isEmpty else { return }
        ToastView.show(message, ok: ok)
    }
}

/// 非阻塞的轻提示（Android `Toast` 的等价物）
enum ToastView {
    private static weak var current: UIView?

    static func show(_ message: String, ok: Bool) {
        // 同时只留一个：新的直接把旧的换掉（Android 也是这样）
        current?.removeFromSuperview()

        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        guard let window = scene?.windows.first(where: { $0.isKeyWindow }) ?? scene?.windows.first
        else { return }

        let label = UILabel()
        // UILabel 没有内边距，前后补两个全角空格当 padding（比再套一层容器简单）
        label.text = "　" + message + "　"
        label.numberOfLines = 0
        label.textAlignment = .center
        label.textColor = .white
        label.font = .systemFont(ofSize: 15, weight: .medium)
        label.backgroundColor = UIColor.black.withAlphaComponent(ok ? 0.78 : 0.82)
        label.layer.cornerRadius = 12
        label.layer.masksToBounds = true
        label.alpha = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        // 不接收触摸 —— 这是与模态弹窗的关键区别
        label.isUserInteractionEnabled = false
        window.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: window.centerXAnchor),
            label.bottomAnchor.constraint(equalTo: window.safeAreaLayoutGuide.bottomAnchor,
                                          constant: -96),
            label.widthAnchor.constraint(lessThanOrEqualTo: window.widthAnchor,
                                         multiplier: 0.8),
            label.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
        ])
        current = label

        UIView.animate(withDuration: 0.18, animations: { label.alpha = 1 }) { _ in
            UIView.animate(withDuration: 0.28, delay: 1.4, options: [.curveEaseIn],
                           animations: { label.alpha = 0 }) { _ in
                label.removeFromSuperview()
            }
        }
    }
}

// MARK: - 承载控制器

final class ScreenHostController: UIViewController {
    private(set) var hostView: ScreenHostView
    private var router: Router?
    /// 首页外壳的状态（配置拉回来要写进它）
    private var homeState: HomeShellScreen.State?

    init(screen: Screen) {
        hostView = ScreenHostView(screen: screen)
        if let home = screen as? HomeShellScreen {
            homeState = HomeShellScreen.State()
            hostView.state = homeState!
        }
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        hostView.frame = view.bounds
        hostView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(hostView)
        let router = Router(host: hostView)
        self.router = router
        hostView.onAction = { [weak router] token, point in
            Task { @MainActor in router?.handle(token, at: point) }
        }
        // 横幅轮播心跳（Android 是 250ms 静止 / 33ms 滚动，这里统一 250ms）
        hostView.startRedrawTimer()
        // 真机调试遥控：从电脑的局域网服务拉指令 + 回传截图（正式发布前关掉）
        DebugBridge.shared.start()
        // 启动就拉一次公开配置：娱乐页玩法与首页横幅文案都由它决定
        Task { @MainActor [weak self] in
            guard let self, let state = self.homeState else { return }
            await ConfigLoader.load(into: state)
            self.hostView.setNeedsDisplay()
            // 配置里带水印文案（sys_config.watermark_overlay_text），拉到后刷新覆盖层
            WatermarkOverlay.attachIfNeeded()
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // 每次页面可见都按当前会员状态重挂水印（与 Android 的 attachIfNeeded 时机一致）
        WatermarkOverlay.attachIfNeeded(preferredWindow: view.window)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // 首帧 viewDidAppear 时窗口可能还没挂上（真机实测水印没出现就是这个原因），
        // 布局阶段再兜一次；attachIfNeeded 复用已有图层，重复调用没有开销。
        WatermarkOverlay.attachIfNeeded(preferredWindow: view.window)
    }

    deinit {
        hostView.stopRedrawTimer()
    }

    override var prefersStatusBarHidden: Bool { true }
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { .all }
}

// MARK: - App 入口

@main
struct DoutuApp: App {
    /// 回到前台就复查版本（与 Android 在 onResume 里调 `UpdatePrompt.maybeCheck` 等价）
    @Environment(\.scenePhase) private var scenePhase

    /// 启动最早时机：写一条 LAUNCH 记录 + 装崩溃钩子。
    /// 排障用（真机闪退取现场），详见 CrashLog.swift 顶部说明。
    init() {
        CrashLog.install()
    }

    var body: some Scene {
        WindowGroup {
            // 启动落地页：首页外壳（三栏 + 四平台页签），与 Android 一致
            ScreenHostContainer(screen: HomeShellScreen())
                .ignoresSafeArea()
                .statusBarHidden(true)
                .onAppear { UpdatePrompt.shared.maybeCheck() }
                .onChange(of: scenePhase) { phase in
                    if phase == .active { UpdatePrompt.shared.maybeCheck() }
                }
        }
    }
}

/// 把 UIKit 的承载控制器包成 SwiftUI 视图。
struct ScreenHostContainer: UIViewControllerRepresentable {
    let screen: Screen

    func makeUIViewController(context: Context) -> ScreenHostController {
        ScreenHostController(screen: screen)
    }

    func updateUIViewController(_ controller: ScreenHostController, context: Context) {}
}
