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

    /// 展示用手机号：默认随机生成一个，中间四位固定打码。
    /// 不读真实登录号码，避免个人信息页泄露/复用当前号码。
    var displayPhoneRaw: String {
        get {
            var raw = defaults.string(forKey: "profile.phone.display") ?? ""
            if raw.isEmpty {
                raw = Self.randomPhone()
                defaults.set(raw, forKey: "profile.phone.display")
            }
            return raw
        }
        set {
            let digits = newValue.filter { $0.isNumber }
            if digits.count >= 6 {
                defaults.set(digits, forKey: "profile.phone.display")
            }
        }
    }

    var displayPhone: String {
        get { Self.maskPhone(displayPhoneRaw) }
        set { displayPhoneRaw = newValue }
    }

    static func maskPhone(_ value: String) -> String {
        let digits = value.filter { $0.isNumber }
        guard digits.count >= 7 else { return value }
        let prefix = digits.prefix(3)
        let suffix = digits.suffix(4)
        return "\(prefix)****\(suffix)"
    }

    static func randomPhone() -> String {
        let prefixes = ["138", "139", "150", "151", "158", "159", "186", "187", "188", "189"]
        var value = prefixes.randomElement() ?? "138"
        for _ in 0..<8 { value.append(String(Int.random(in: 0...9))) }
        return value
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

/// 「撩妹加 V」开关：原生 Router 和微网页的扫一扫提示共用。
enum TeaseStore {
    private static let key = "play.tease.enabled"
    static var enabled: Bool {
        get { UserDefaults.standard.bool(forKey: key) }
        set { UserDefaults.standard.set(newValue, forKey: key) }
    }
}

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
    /// 每一层 push 前的页面快照，给交互式边缘返回做背景。
    private var historySnapshots: [UIView] = []
    /// 本轮新补齐页面的实例引用（页内交互要回读到具体 Screen 的方法）
    private weak var parseScreen: ParseScreen?
    private weak var callSetupScreen: CallSetupScreen?
    private weak var accountSoundScreen: AccountSoundScreen?
    private weak var customerServiceScreen: CustomerServiceScreen?
    /// 当前打开的「模拟」编辑页（`mock:*` 令牌要回读它的 state 做改动）
    private weak var mockEditor: MockEditorScreen?
    /// 「撩妹加 V」开关（Android 存在配置里，没有独立页面）
    private var teaseEnabled = TeaseStore.enabled
    /// 登录页关闭后再执行的令牌（例如 resetInvite）
    var pendingAfterLoginDismiss: ActionToken?
    private var pendingParseRetry = false

    /// 登录成功后切到「我」页。
    func selectProfileTab() {
        if let state = host?.state as? HomeShellScreen.State {
            state.tab = .profile
            host?.setNeedsDisplay()
        } else if let state = host?.state as? HomeV2Screen.State {
            state.tab = .profile
            host?.setNeedsDisplay()
        }
    }

    /// 登录页真正 dismiss 后，再执行之前挂起的跳转。
    func runPendingAfterLoginDismiss() {
        guard let token = pendingAfterLoginDismiss else { return }
        pendingAfterLoginDismiss = nil
        handle(token, at: .zero)
    }

    init(host: ScreenHostView) {
        self.host = host
        Router.shared = self
        host.canGoBack = false
    }

    /// 进入新页面（保留当前页面的状态，返回时恢复）
    private func push(_ screen: Screen, state: ScreenState = ScreenState()) {
        guard let host else { return }
        let snapshot = host.snapshotView(afterScreenUpdates: false)
            ?? UIView(frame: host.bounds)
        historySnapshots.append(snapshot)
        stack.append((host.screen, host.state))
        host.previousSnapshot = historySnapshots.last
        host.canGoBack = true
        // 与 iOS 原生导航一致：旧页面向左推出，新页面在下面接管。
        host.setScreen(screen, state: state, animated: true)
    }

    /// 返回上一页；栈空时什么也不做。
    ///
    /// `animated` 区分两种来源：
    ///   · 点页面里的返回箭头 / 系统返回 → true，旧页面向右滑出；
    ///   · 左边缘手势已经亲手把旧页面推走（`edgeback`）→ false，直接换内容，
    ///     否则会再滑一次、还会在两次动画之间闪回旧页。
    private func pop(animated: Bool = true) {
        guard let host, let previous = stack.popLast() else { return }
        _ = historySnapshots.popLast()
        host.setScreen(previous.screen, state: previous.state,
                       animated: animated, backwards: true)
        host.previousSnapshot = historySnapshots.last
        host.canGoBack = !stack.isEmpty
    }

    // MARK: - 娱乐页玩法行（`play:<key>`）

    /// 对应 Android `MainActivity` 娱乐页各行的跳转。
    ///
    /// 之前这些分支是空实现（`_ = t`），所以「娱乐页点哪都没反应」。
    /// 已移植的页面直接进；未移植的给一条明确提示，至少让用户知道点到了。
    private func handlePlay(_ key: String) {
        switch key {
        case "avatar":
            push(ProfileV2Screen(), state: ProfileV2Screen.State())
        case "balance":
            push(BalanceV2Screen(), state: BalanceV2Screen.State())
        case "order":
            push(TaobaoOrderScreen(), state: TaobaoOrderScreen.State())   // 淘宝订单详情（原为 ReceiptV2Screen，点进去像支付宝主页）
        case "parse":
            let screen = ParseScreen()
            parseScreen = screen
            push(screen, state: ParseScreen.State())
        case "call":
            let screen = CallSetupScreen()
            callSetupScreen = screen
            let state = CallSetupScreen.State()
            push(screen, state: state)
            // 从「微」的通讯录同步备注名和头像；失败时保留页面自带的兜底联系人。
            WeChatContactStore.shared.load { [weak self, weak state] contacts in
                guard let state, !contacts.isEmpty else { return }
                state.contacts = contacts
                if state.name.isEmpty, let first = contacts.first {
                    state.name = first.name
                    state.avatarImage = first.avatar
                }
                self?.host?.setNeedsDisplay()
            }
        case "shutdown":
            push(ShutdownScreen(), state: ShutdownScreen.State())
        case "sound":
            let screen = AccountSoundScreen()
            accountSoundScreen = screen
            push(screen, state: AccountSoundScreen.State())
        case "tease":
            // 「撩妹加 V」在 Android 是一个开关（MainActivity 里改配置），没有独立页面。
            // 这里必须同时改 playbook 原始行里的 enabled，否则页面重画后开关又回到关闭态。
            let next = !(currentPlaybookSwitch("tease") ?? teaseEnabled)
            teaseEnabled = next
            TeaseStore.enabled = next
            setPlaybookSwitch("tease", enabled: next)
            host?.setNeedsDisplay()
            showToast(next ? "已开启撩妹加 V" : "已关闭撩妹加 V", ok: true)
        default:
            showToast("该玩法暂未开放", ok: false)
        }
    }

    /// 读取娱乐页开关行当前状态（没有配置时返回 nil）。
    private func currentPlaybookSwitch(_ key: String) -> Bool? {
        let sections: [[String: Any]]?
        if let s = host?.state as? HomeShellScreen.State {
            sections = s.playbookSections
        } else if let s = host?.state as? HomeV2Screen.State {
            sections = s.playbookSections
        } else {
            sections = nil
        }
        guard let sections else {
            return key == "tease" ? TeaseStore.enabled : nil
        }
        for section in sections {
            for row in (section["rows"] as? [[String: Any]] ?? [])
            where (row["key"] as? String) == key {
                return row["enabled"] as? Bool
                    ?? (key == "tease" ? TeaseStore.enabled : false)
            }
        }
        return key == "tease" ? TeaseStore.enabled : nil
    }

    /// 写回娱乐页开关行，保证点击后立即反映到重绘的开关上。
    private func setPlaybookSwitch(_ key: String, enabled: Bool) {
        func updated(_ input: [[String: Any]]) -> [[String: Any]] {
            var sections = input
            for i in sections.indices {
                var rows = sections[i]["rows"] as? [[String: Any]] ?? []
                for j in rows.indices where (rows[j]["key"] as? String) == key {
                    rows[j]["enabled"] = enabled
                }
                sections[i]["rows"] = rows
            }
            return sections
        }
        if let s = host?.state as? HomeShellScreen.State, let sections = s.playbookSections {
            s.playbookSections = updated(sections)
        } else if let s = host?.state as? HomeV2Screen.State, let sections = s.playbookSections {
            s.playbookSections = updated(sections)
        }
    }

    // MARK: - 「我」页下方常用服务四行（`entry:<index>`）

    private func handleProfileEntry(_ index: Int) {
        switch index {
        case 0:
            let screen = CustomerServiceScreen()
            customerServiceScreen = screen
            let state = CustomerServiceScreen.State()
            let defaults = UserDefaults.standard
            state.notice = defaults.string(forKey: "config.cs_notice") ?? ""
            state.contact = defaults.string(forKey: "config.cs_contact") ?? ""
            state.qrUrl = defaults.string(forKey: "config.cs_qr_image") ?? ""
            push(screen, state: state)
            if !state.qrUrlTrimmed.isEmpty {
                CustomerServiceQrLoader.load(state.qrUrlTrimmed) { [weak self] image in
                    state.qrImage = image
                    state.qrFailed = (image == nil)
                    self?.host?.setNeedsDisplay()
                }
            }
        case 1:
            push(FeedbackScreen(), state: FeedbackScreen.State())
        case 2:
            shareToFriend()
        case 3:
            // Android `MainActivity:1710-1713`：这一页还没做，只弹「待更新」，
            // 不进占位详情页。之前 iOS 打开了 SettingsV2Screen，和安卓不一致。
            showToast("待更新", ok: false)
        default:
            break
        }
    }

    /// 收款详情页的编辑（Java `ReceiptDetailActivity.act()`）
    private func handleReceiptDetail(_ token: String) {
        guard let state = host?.state as? ReceiptDetailV2Screen.State,
              let item = state.receipt else { return }
        let stats = ReceiptStore.shared.stats(item.payer)
        let numeric = ["amountLabel", "amount", "actual", "quota", "month", "total", "avg"]
            .contains(token)

        let title: String
        let current: String
        switch token {
        case "amountLabel", "amount":
            title = "修改收款金额"; current = ReceiptStore.money(item.amount)
        case "actual":
            title = "修改实际到账"; current = ReceiptStore.money(item.actual)
        case "quota":
            title = "修改提现免费额度"; current = ReceiptStore.money(item.quota)
        case "prize":
            title = "修改收钱有奖"; current = item.prize
        case "note":
            title = "修改交易说明"; current = item.note
        case "payer":
            title = "修改支付人"; current = item.payer
        case "month":
            title = "修改本月消费"
            current = ReceiptStore.money(item.monthSpend ?? stats.monthSpend)
        case "total":
            title = "修改累计消费"
            current = ReceiptStore.money(item.totalSpend ?? stats.totalSpend)
        case "avg":
            title = "修改平均单价"
            current = ReceiptStore.money(item.avgPrice
                ?? (stats.totalCount == 0 ? 0 : stats.totalSpend / Double(stats.totalCount)))
        default:
            return
        }

        EditPrompt.present(title: title, current: current, numeric: numeric) { [weak self] text in
            let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
            switch token {
            case "amountLabel", "amount":
                if let number = Double(value) { item.amount = number }
            case "actual":
                if let number = Double(value) { item.actual = number }
            case "quota":
                if let number = Double(value) { item.quota = number }
            case "prize":
                item.prize = value
            case "note":
                item.note = value
            case "payer":
                guard !value.isEmpty else { return }
                item.payer = value
            case "month":
                if let number = Double(value) { item.monthSpend = number }
            case "total":
                if let number = Double(value) { item.totalSpend = number }
            case "avg":
                if let number = Double(value) { item.avgPrice = number }
            default:
                return
            }
            ReceiptStore.shared.save()
            self?.host?.setNeedsDisplay()
        }
    }

    // MARK: - 去水印解析页（Parse）

    private func handleParseToken(_ token: String) {
        guard let screen = parseScreen, let state = host?.state as? ParseScreen.State else { return }
        switch token {
        case "link":
            EditPrompt.present(title: "粘贴视频链接", current: state.link, numeric: false) { [weak self] text in
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
        case "download":
            downloadParse(screen: screen, state: state)
        case "openSaved":
            showToast("成品已保存到系统相册", ok: true)
        default:
            break
        }
    }

    private func downloadParse(screen: ParseScreen, state: ParseScreen.State) {
        if state.downloading { return }
        if state.saved {
            showToast("成品已保存到系统相册", ok: true)
            return
        }
        CrashLog.append("PARSE download start video=\(state.outUrl.prefix(60)) images=\(state.imageUrls.count)")
        screen.beginDownload(state)
        host?.setNeedsDisplay()
        let completion: (Result<String, Error>) -> Void = { [weak self, weak screen] result in
            guard let self, let screen else { return }
            switch result {
            case .success(let location):
                screen.finishDownload(state, at: location)
                CrashLog.append("PARSE download saved \(location)")
                self.showToast("已保存到系统相册", ok: true)
            case .failure(let error):
                screen.failDownload(state, message: error.localizedDescription)
                CrashLog.append("PARSE download failed \(error.localizedDescription)")
                self.showToast(error.localizedDescription, ok: false)
            }
            self.host?.setNeedsDisplay()
        }
        if state.imageMode, !state.imageUrls.isEmpty {
            MediaSaver.saveImages(urlStrings: state.imageUrls, completion: completion)
        } else if !state.outUrl.isEmpty {
            MediaSaver.saveVideo(urlString: state.outUrl, completion: completion)
        } else {
            screen.failDownload(state, message: "成品地址还没准备好")
            host?.setNeedsDisplay()
        }
    }

    private static func isAuthExpired(_ message: String) -> Bool {
        message.contains("身份过期") || message.contains("重新登录")
            || message.contains("登录已过期") || message.contains("token")
    }

    private func startParse(screen: ParseScreen, state: ParseScreen.State) {
        let link = state.link.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !link.isEmpty else { showToast("请先粘贴视频链接", ok: false); return }
        guard !state.polling else { return }
        guard Session.shared.isLoggedIn else {
            pendingParseRetry = true
            LoginPresenter.present(onDone: { [weak self, weak screen] in
                guard let self, let screen, Session.shared.isLoggedIn else { return }
                self.pendingParseRetry = false
                self.startParse(screen: screen, state: state)
            }, goProfile: false)
            return
        }
        screen.beginParse(state)
        host?.setNeedsDisplay()
        // 抖音分享文案里常带大段中文/表情；Android 虽然原样发 share_text，
        // 但部分任务只有 URL 才能被服务端解析器识别，这里优先提取第一个链接。
        let shareText: String
        if let range = link.range(of: #"https?://[^\s，。]+"#, options: .regularExpression) {
            shareText = String(link[range])
        } else {
            shareText = link
        }
        CrashLog.append("PARSE request start share=\(shareText.prefix(60))")
        let timeout = DispatchWorkItem { [weak self, weak screen] in
            guard let self, let screen, state.polling, state.taskNo.isEmpty else { return }
            CrashLog.append("PARSE request timeout")
            screen.failParse(state, message: "请求超时，请检查网络后重试")
            self.host?.setNeedsDisplay()
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 15, execute: timeout)
        Task {
            // 关键：DTW 握手（PBKDF2 + 同步网络）不能跑在 MainActor 上，
            // 否则解析一开始主线程就被占住，界面看起来像卡死。
            let env = await Task.detached(priority: .userInitiated) {
                // Android DoutuApi.parse 的字段名是 share_text，不是 link。
                await ApiClient.shared.call("POST", "/api/v1/dw/parse",
                                            body: ["share_text": shareText])
            }.value
            await MainActor.run {
                timeout.cancel()
                CrashLog.append("PARSE response code=\(env.code) msg=\(env.msg)")
                guard env.ok, let data = env.data as? [String: Any] else {
                    if Self.isAuthExpired(env.msg) {
                        Session.shared.clear()
                        screen.failParse(state, message: "登录已过期，请重新登录")
                        self.showToast("登录已过期，请重新登录", ok: false)
                        self.pendingParseRetry = true
                        LoginPresenter.present(onDone: { [weak self, weak screen] in
                            guard let self, let screen, Session.shared.isLoggedIn else { return }
                            self.pendingParseRetry = false
                            self.startParse(screen: screen, state: state)
                        }, goProfile: false)
                    } else {
                        screen.failParse(state, message: env.msg.isEmpty ? "解析失败，请稍后再试" : env.msg)
                        self.showToast(env.msg.isEmpty ? "解析失败，请稍后再试" : env.msg, ok: false)
                    }
                    self.host?.setNeedsDisplay()
                    return
                }
                let taskNo = (data["task_no"] as? String) ?? ""
                screen.attachTask(state, taskNo: taskNo, status: 0)
                self.host?.setNeedsDisplay()
                if !taskNo.isEmpty {
                    // 第一次立刻查，之后 0.7s 一次；之前固定等 1.5s，体感会慢一倍。
                    self.pollParse(screen: screen, state: state, taskNo: taskNo,
                                   left: 60, delay: 0.15)
                }
            }
        }
    }

    private func pollParse(screen: ParseScreen, state: ParseScreen.State, taskNo: String,
                           left: Int, delay: TimeInterval = 0.7) {
        guard left > 0 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self else { return }
            Task {
                let path = "/api/v1/dw/task/\(taskNo)"
                let env = await Task.detached(priority: .userInitiated) {
                    await ApiClient.shared.call("GET", path)
                }.value
                await MainActor.run {
                    guard env.ok else {
                        CrashLog.append("PARSE poll failed code=\(env.code) msg=\(env.msg)")
                        if screen.registerPollFailure(state) {
                            self.pollParse(screen: screen, state: state, taskNo: taskNo,
                                           left: left - 1, delay: 0.7)
                        } else {
                            screen.failParse(state, message: env.msg.isEmpty
                                             ? "查询解析结果失败" : env.msg)
                            self.host?.setNeedsDisplay()
                        }
                        return
                    }
                    let data = env.data as? [String: Any]
                    let status = (data?["status"] as? Int) ?? -1
                    let text = (data?["status_text"] as? String) ?? ""
                    CrashLog.append("PARSE poll status=\(status) text=\(text) task=\(taskNo)")
                    let done = screen.applyPollStatus(state, status: status, statusText: text)
                    self.host?.setNeedsDisplay()
                    if done {
                        if left <= 1 {
                            screen.failParse(state, message: "解析超时，请稍后重试")
                        } else {
                            self.pollParse(screen: screen, state: state, taskNo: taskNo,
                                           left: left - 1, delay: 0.7)
                        }
                    } else {
                        if let data { screen.showResult(state, data) }
                        self.host?.setNeedsDisplay()
                    }
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
            EditPrompt.present(title: "意见与建议", current: state.content, numeric: false) { [weak self] text in
                state.content = text
                self?.host?.setNeedsDisplay()
            }
        case "contact":
            EditPrompt.present(title: "联系方式（选填）", current: state.contact, numeric: false) { [weak self] text in
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
        // 根页面直接就是 TermsScreen，没有可 pop 的栈；必须显式切到首页。
        // 之前只 pop()，真机上就是“点了同意还停在免责声明”。
        // 用转场而不是硬切：首页是用户看到的第一屏，直接跳出来很像「闪屏」。
        host?.setScreen(HomeV2Screen(), state: HomeV2Screen.State(), animated: true)
        host?.setNeedsDisplay()
    }
    // MARK: - 「我」页右上角 ··· 账号菜单

    /// 会员卡弹窗（对应 Android `MainActivity.showActivation()`，Java:1925-1949）
    private func showMemberInfo() {
        guard Session.shared.isLoggedIn else {
            LoginPresenter.present { [weak self] in self?.host?.setNeedsDisplay() }
            return
        }
        guard let presenter = UpdatePrompt.topViewController() else { return }
        let member = Session.shared.isActivated
        let username = ProfileStore.shared.name.isEmpty
            ? Session.shared.displayName : ProfileStore.shared.name
        var lines = ["用户名：\(username.isEmpty ? "-" : username)"]
        let phone = ProfileStore.shared.phone
        if !phone.isEmpty { lines.append("手机号：\(phone)") }
        lines.append("状态：\(SettingsV2Screen.statusText)")
        if member {
            lines.append("到期：\(Session.shared.expireAt.isEmpty ? "-" : Session.shared.expireAt)")
        } else {
            lines.append("")
            lines.append("未开通会员也可以正常使用，只是界面与成品会带水印。")
            lines.append("开通会员后水印会自动消失，请联系客服在后台为你开通。")
        }
        let alert = UIAlertController(title: member ? "账号与会员" : "未开通会员",
                                      message: lines.joined(separator: "\n"),
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "关闭", style: .cancel))
        alert.addAction(UIAlertAction(title: "修改用户名", style: .default) { [weak self] _ in
            self?.promptRename()
        })
        alert.addAction(UIAlertAction(title: "刷新状态", style: .default) { [weak self] _ in
            self?.refreshMemberStatus()
        })
        presenter.present(alert, animated: true)
    }

    private func promptRename() {
        EditPrompt.present(title: "修改用户名", current: ProfileStore.shared.name,
                           numeric: false) { [weak self] text in
            let name = text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { return }
            Task { @MainActor in
                let result = await AuthService.changeUsername(name)
                self?.showToast(result.message, ok: result.ok)
                self?.host?.setNeedsDisplay()
            }
        }
    }

    /// 启动/回前台补一次会员状态（Android `refreshAccount(false)` 的等价时机）。
    func refreshMemberStatusIfNeeded() {
        guard Session.shared.isLoggedIn else { return }
        // 不要只在 status_text 为空时刷新：会员到期/续费/换账号后本地 activated
        // 可能还是旧值，图标等会员功能就会被误判为未开通。
        // 启动/回前台的后台刷新必须静默：服务端不可达时不能弹「网络异常」，
        // 本地功能（钱包、微、娱乐）不依赖这个接口。
        refreshMemberStatus(silent: true)
    }

    /// 收款码页「修改」姓名：Android CollectMoneyActivity.editName 的等价实现。
    private func editCollectName() {
        guard let state = host?.state as? CollectMoneyV2Screen.State,
              let presenter = UpdatePrompt.topViewController() else { return }
        let alert = UIAlertController(title: "修改收款人姓名", message: nil, preferredStyle: .alert)
        alert.addTextField { field in
            field.text = state.name
            field.placeholder = "收款人姓名"
            field.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self] _ in
            let value = alert.textFields?.first?.text?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !value.isEmpty else { return }
            state.name = value
            self?.host?.setNeedsDisplay()
        })
        presenter.present(alert, animated: true)
    }

    /// 对应 Android `MainActivity.refreshAccount(true)`：`GET /api/v1/auth/status`
    func refreshMemberStatus(silent: Bool = false, completion: (() -> Void)? = nil) {
        Task { @MainActor in
            // Android 用 GET；之前写成 POST 会被服务端回「请求方法不支持」，启动时还会弹 toast。
            let res = await ApiClient.shared.call("GET", "/api/v1/auth/status")
            guard res.ok, let data = res.data as? [String: Any] else {
                if !silent {
                    self.showToast(res.msg.isEmpty ? "刷新失败" : res.msg, ok: false)
                }
                completion?()
                return
            }
            // /auth/status 的用户字段平铺在 data 上；login/register 才是 data.user。
            let user = data["user"] as? [String: Any] ?? data
            let defaults = UserDefaults.standard
            if let name = user["username"] as? String, !name.isEmpty {
                defaults.set(name, forKey: "profile.name")
                ProfileStore.shared.name = name
            }
            if let phone = user["phone"] as? String, !phone.isEmpty {
                defaults.set(phone, forKey: "profile.phone")
            }
            defaults.set((user["nickname"] as? String) ?? "", forKey: "profile.nickname")
            defaults.set((user["activated"] as? Bool) ?? ((user["status"] as? Int) == 1),
                         forKey: "profile.activated")
            if let status = user["status_text"] as? String, !status.isEmpty {
                defaults.set(status, forKey: "profile.status_text")
            }
            if let expire = user["expire_at"] as? String {
                defaults.set(expire, forKey: "profile.expire_at")
            }
            if let overlay = data["watermark_overlay_text"] as? String, !overlay.isEmpty {
                defaults.set(overlay, forKey: "config.activation_notice")
            }
            WatermarkOverlay.attachIfNeeded()
            self.host?.setNeedsDisplay()
            if !silent { self.showToast("已刷新", ok: true) }
            completion?()
        }
    }

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
        guard index >= 0, index < routes.count else { return }

        // 修改应用图标：Android 拉起 AppIconChooser 底部弹窗，这里同样走原生弹窗。
        if index == 13 {
            AppIconChooser.present()
            return
        }

        // 抖音去水印：Android 这一格走 `openParseWithGate()` 进原生解析页（和娱乐页
        // 「短视频一键去水印」是同一个 ParseActivity）。之前这里留了 nil，
        // 结果宫格里唯一一个已移植的入口反而点不动。
        if index == 14 {
            let screen = ParseScreen()
            parseScreen = screen
            push(screen, state: ParseScreen.State())
            return
        }

        guard let route = routes[index] else { return }

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

    /// WebClip 使用的深链：doutu://zhi → 小支主页；doutu://wechat → 小微主页。
    func handleDeepLink(_ url: URL) {
        CrashLog.append("DEEPLINK \(url.absoluteString)")
        guard url.scheme?.lowercased() == "doutu", host != nil else { return }
        switch (url.host ?? "").lowercased() {
        case "zhi":
            push(ZhiHomeV2Screen(), state: ZhiHomeV2Screen.State())
        case "wechat":
            presentWeb(route: "#/wechat")
        default:
            break
        }
    }

    /// 全屏弹出小微主页并把路由交过去
    private func presentWeb(route: String) {
        guard let host = UpdatePrompt.topViewController() else { return }
        // 关闭微主页后的 1 秒内忽略再次打开，挡住返回动作穿透到首页宫格。
        if let closedAt = WeChatV2Host.lastCloseAt,
           Date().timeIntervalSince(closedAt) < 1.0 {
            return
        }
        // 防止双击/连续点击把两个微主页叠在一起：返回时只 dismiss 最上面那个，
        // 下面那个会立刻露出来，看起来就像「退出后又自动进入」。
        if let existing = host as? WeChatV2Host {
            existing.open(route: route)
            return
        }
        if let presented = host.presentedViewController as? WeChatV2Host {
            presented.open(route: route)
            return
        }
        let web = WeChatV2Host(route: route)
        // 用 overFullScreen 而不是 fullScreen：微信根页面边缘右滑时，手指下面露出的
        // 应该是首页（跟微信/iOS 原生返回一致），而不是一块黑底。
        // fullScreen 会把 presenting view 从层级里摘掉，没法做跟手返回。
        web.modalPresentationStyle = .overFullScreen
        host.present(web, animated: true)
    }

    /// 支模块 4×4 宫格（`MainActivity:501-520` 的 `zhiGridIntent`）。
    ///
    /// 索引 = row*4 + col，与 Java 完全一致；已移植的页面直接进，未移植的留空。
    private func handleZhiCell(_ index: Int) {
        switch index {
        case 0:   // 小支主页 ✓
            push(ZhiHomeV2Screen(), state: ZhiHomeV2Screen.State())
        case 1:   // 总资产 ✓
            push(AssetV2Screen(), state: AssetV2Screen.State())
        case 2:   // 余额 ✓
            push(BalanceV2Screen(), state: BalanceV2Screen.State())
        case 3:   // 余额宝 ✓
            push(YuebaoV2Screen(), state: YuebaoV2Screen.State())
        case 4:   // 花呗 ✓
            push(HuabeiV2Screen(), state: HuabeiV2Screen.State())
        case 5:   // 支付消息 ✓
            push(PayMsgV2Screen(), state: PayMsgV2Screen.State())
        case 6:   // 支付成功 ✓（Android `zhiGridIntent` case 6 → PaySuccessActivity）
            push(PaySuccessV2Screen(), state: PaySuccessV2Screen.State())
        case 7:   // 自定义账单 ✓
            push(BillV2Screen(), state: BillV2Screen.State())
        case 8:   // 个人信息 ✓
            push(ProfileV2Screen(), state: ProfileV2Screen.State())
        case 9:   // 切换账号 ✓
            push(SwitchAccountV2Screen(), state: SwitchAccountV2Screen.State())
        case 10:  // 财富页：普通版进理财，黑卡用户进成长版 ✓
            if LicaiVersion.blackCard(LicaiVersion.current()) {
                push(GrowthV2Screen(), state: GrowthV2Screen.State())
            } else {
                push(LicaiV2Screen(), state: LicaiV2Screen.State())
            }
        case 11:  // 收款记录 ✓
            push(ReceiptV2Screen(), state: ReceiptV2Screen.State())
        case 12, 13:  // 余额提现 / 转账银行卡 ✓（同一个提现页）
            // Android 进页面就聚焦金额输入框，键盘立刻弹出（参考图上键盘是可见的），
            // 所以这里把 keypad 默认置 true，否则首屏会少一整块键盘、观感差很多。
            let withdrawState = WithdrawV2Screen.State()
            withdrawState.keypad = true
            push(WithdrawV2Screen(), state: withdrawState)
        case 14:  // 小荷包 ✓
            push(XiaohebaoV2Screen(), state: XiaohebaoV2Screen.State())
        case 15:  // 芝麻信用 ✓
            push(ZhimaCreditV2Screen(), state: ZhimaCreditV2Screen.State())
        default:
            break
        }
    }

    func handle(_ token: ActionToken, at point: CGPoint) {
        // 收款详情页的字段名和下面好几个页面重名（amount 也属于收款码/提现页）。
        // 先按当前页面把详情页的编辑令牌截走，避免被前面的通用 case 吞掉。
        if host?.screen is ReceiptDetailV2Screen,
           ["amountLabel", "amount", "actual", "prize", "quota", "note",
            "payer", "month", "total", "avg"].contains(token) {
            handleReceiptDetail(token)
            return
        }
        switch token {
        case "noop":
            // 很多页面（收款风格、随机金额档位、声音开关等）在 hitTest 里已经改完 State，
            // 只吐 noop 表示“不需要 Router 再做别的”，但仍然需要重绘，否则看起来像没反应。
            self.host?.setNeedsDisplay()
            return

        // 调试用：验证备选图标是否真的可用（正式界面走 AppIconChooser 弹窗）
        case "iconstate":
            CrashLog.append("ICON state supported=\(UIApplication.shared.supportsAlternateIcons) "
                + "current=\(UIApplication.shared.alternateIconName ?? "nil")")
        case let t where t.hasPrefix("icon:"):
            let name = String(t.dropFirst("icon:".count))
            let alternate: String? = (name == "default" || name.isEmpty) ? nil : name
            UIApplication.shared.setAlternateIconName(alternate) { error in
                CrashLog.append("ICON switch name=\(name) err=\(error?.localizedDescription ?? "ok")")
            }

        // 真机验收用：给收款记录播种/清空（不参与 UI，只走 DebugBridge 令牌）
        case "receiptseed":
            let made = ReceiptStore.shared.add(
                [ReceiptStore.Spec(name: "小额", min: 0.1, max: 30, count: 3)],
                year: 2026, month: 10)
            CrashLog.append("receiptseed made=\(made) total=\(ReceiptStore.shared.records.count)")
            if let state = self.host?.state as? ReceiptV2Screen.State { state.scroll = 0 }
            self.host?.setNeedsDisplay()
        case "receiptclear":
            ReceiptStore.shared.reset()
            if let state = self.host?.state as? ReceiptV2Screen.State { state.scroll = 0 }
            self.host?.setNeedsDisplay()

        // 首页外壳：底部五栏 + 平台页签（Android MainActivity）
        // tab:3 = 模拟、tab:4 = 工具，漏掉任何一个点了都会没反应。
        case "tab:0", "tab:1", "tab:2", "tab:3", "tab:4":
            let index = Int(token.suffix(1)) ?? 0
            if let state = self.host?.state as? HomeV2Screen.State {
                let next = HomeV2Screen.Tab(rawValue: index) ?? .home
                if next != state.tab {
                    state.tab = next
                    state.scroll = 0
                    Haptics.selection()
                } else {
                    // iOS 习惯：再点一次当前 tab = 回到顶部
                    state.scroll = 0
                }
                self.host?.setNeedsDisplay()
                return
            }
            guard let state = self.host?.state as? HomeShellScreen.State else { return }
            let next = HomeShellScreen.Tab(rawValue: index) ?? .home
            if next != state.tab {
                state.tab = next
                state.scroll = 0
                Haptics.selection()
            } else {
                state.scroll = 0
            }
            self.host?.setNeedsDisplay()
        case let t where t.hasPrefix("pageUnavailable:"):
            showToast("该平台暂未开放，敬请期待", ok: false)
        case let t where t.hasPrefix("page:"):
            let page = max(0, min(3, Int(t.suffix(1)) ?? 0))
            if let state = self.host?.state as? HomeV2Screen.State {
                if state.servicePage != page {
                    state.servicePage = page
                    Haptics.selection()
                }
                self.host?.setNeedsDisplay()
                return
            }
            guard let state = self.host?.state as? HomeShellScreen.State else { return }
            if state.servicePage != page {
                state.servicePage = page
                Haptics.selection()
            }
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
            // 收款详情页也有一个 avatar 热区（Android 换收款人头像）。iOS 还没做
            // 收款头像存储，不能落到「换我的头像」这条分支上。
            if self.host?.screen is ReceiptDetailV2Screen { return }
            // 模拟来电页的头像：选完要回填到当页 State，而不是只改个人资料。
            if let state = self.host?.state as? CallSetupScreen.State {
                if let vc = EditPrompt.topViewController() {
                    QRImagePicker.present(from: vc) { image in
                        guard let image else { return }
                        state.avatarImage = image
                        self.host?.setNeedsDisplay()
                        self.showToast("头像已更新", ok: true)
                    }
                }
                return
            }
            // 换头像：未登录先去登录，已登录直接从相册选
            if Session.shared.isLoggedIn {
                AvatarPicker.present { ok, message in
                    self.host?.setNeedsDisplay()
                    self.showToast(message, ok: ok)
                }
            } else {
                LoginPresenter.present { [weak self] in
                    self?.host?.setNeedsDisplay()
                }
            }
        case "user":
            // 未登录 → 登录/注册表单；已登录 → 账号设置页
            // （Android `MainActivity:1681` 的 `openSettings()`，不是个人信息页）
            if Session.shared.isLoggedIn {
                push(SettingsV2Screen(), state: SettingsV2Screen.State())
            } else {
                LoginPresenter.present { [weak self] in
                    self?.host?.setNeedsDisplay()
                }
            }
        case "member":
            // 会员卡 / 设置页会员行：Android 弹出账号与会员信息（含改用户名、刷新状态）
            showMemberInfo()
        case let t where t.hasPrefix("entry:") && !(self.host?.screen is ReceiptV2Screen):
            // 「我」页的 0 人工客服 / 1 求新功能 / 2 邀请好友 / 3 更多设置。
            // 注意：收款记录页的 `entry:<收款id>` 也是这个前缀，必须让下面那个
            // ReceiptV2Screen 分支处理；否则点任意一条收款都会错跳客服页。
            handleProfileEntry(Int(t.suffix(1)) ?? 0)

        // 「我」页（支模块）
        case "bannerClose":
            if let state = self.host?.state as? MeScreen.State {
                state.banner = false
                self.host?.setNeedsDisplay()
            }
        case "profile":
            // 身份行 → 个人信息页（Java: 点「我的」页的身份行进 ProfileV2Screen）
            push(ProfileV2Screen(), state: ProfileV2Screen.State())
        case "back":
            pop()
        case "edgeback":
            // 左边缘手势：页面已经被手指推出屏幕，这里只换内容，不再播第二段转场。
            pop(animated: false)
        case "balance":
            // 「我的」页资产卡「余额」→ 余额页
            push(BalanceV2Screen(), state: BalanceV2Screen.State())
        case "assets":
            // 「我的」页资产卡「总资产」→ 总资产页
            push(AssetV2Screen(), state: AssetV2Screen.State())
        case "bills":
            // 账单列表（数据由 BillStore 本地生成，与 Android 同一套规则）
            push(BillV2Screen(), state: BillV2Screen.State())

        // 支模块首页（ZhiHomeV2Screen）的交互
        case "plus":
            // 右上「+」浮层
            if let state = self.host?.state as? ZhiHomeV2Screen.State {
                state.plusMenu = true
                self.host?.setNeedsDisplay()
            }
        case "plusClose":
            if let state = self.host?.state as? ZhiHomeV2Screen.State {
                state.plusMenu = false
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("plus:"):
            // 「+」菜单四项：扫一扫 / 收款 / 添加朋友 / 历史记录
            let name = String(t.dropFirst("plus:".count))
            if let state = self.host?.state as? ZhiHomeV2Screen.State {
                state.plusMenu = false
            }
            switch name {
            case "扫一扫": push(ScanOverlayScreen(), state: ScanOverlayScreen.State())
            case "收款": push(CollectMoneyV2Screen(), state: CollectMoneyV2Screen.State())
            case "历史记录": push(ReceiptV2Screen(), state: ReceiptV2Screen.State())
            default: break    // 添加朋友：对应页面未移植
            }
        case let t where t.hasPrefix("action:"):
            // 扫一扫 / 收付款 / 出行 / 卡包
            let name = String(t.dropFirst("action:".count))
            switch name {
            case "扫一扫": push(ScanOverlayScreen(), state: ScanOverlayScreen.State())
            case "收付款": push(PayCodeV2Screen(), state: PayCodeV2Screen.State())
            case "出行": push(TravelV2Screen(), state: TravelV2Screen.State())
            default: break
            }
        case let t where t.hasPrefix("icon:"):
            // 宫格与常用卡里的入口（按名字分发到已移植的页面）
            let name = String(t.dropFirst("icon:".count))
            switch name {
            case "余额宝": push(YuebaoV2Screen(), state: YuebaoV2Screen.State())
            case "花呗": push(HuabeiV2Screen(), state: HuabeiV2Screen.State())
            default: break
            }
        case "all", "zhimaMore":
            break
        case "collect":
            break
        case "zhima":
            push(ZhimaCreditV2Screen(), state: ZhimaCreditV2Screen.State())

        // 收款码页：二维码 / 名字 / 三个动作 / 收款记录 / 助手 / 经营 / 商家服务 / 升级
        case "name" where host?.state is CollectMoneyV2Screen.State:
            editCollectName()
        case "qr" where host?.state is CollectMoneyV2Screen.State:
            guard let host, let state = host.state as? CollectMoneyV2Screen.State,
                  let presenter = UpdatePrompt.topViewController() else { break }
            QRImagePicker.present(from: presenter) { image in
                guard let image else { return }
                state.qrImage = image
                host.setNeedsDisplay()
            }
        case "records" where host?.state is CollectMoneyV2Screen.State:
            push(ReceiptV2Screen(), state: ReceiptV2Screen.State())
        case "qr", "name", "amount", "save", "more2", "records", "assistant",
             "business", "promo", "service", "upgrade":
            // 其余在 Android 里也是空实现（设置金额/保存/商家服务等）
            break

        // 账单页：页签筛选 / 筛选 / 收支分析
        case let t where t.hasPrefix("chip:"):
            let kind = String(t.dropFirst("chip:".count))
            if let state = self.host?.state as? BillV2Screen.State {
                state.kind = kind
                state.scroll = 0
                self.host?.setNeedsDisplay()
            }
        case "analytics":
            // 账单页右上「收支分析」→ 收支分析页
            push(BillAnalyticsV2Screen(), state: BillAnalyticsV2Screen.State())
        case "filter":
            // 账单筛选面板未移植
            break
        case let t where t.hasPrefix("bill:"):
            // 账单详情：`bill:<月序号>:<行号>` —— 让 BillScreen 按它自己的布局取出那一条
            let parts = t.dropFirst("bill:".count).split(separator: ":")
            if parts.count == 2, let monthIndex = Int(parts[0]), let row = Int(parts[1]) {
                let screen = BillV2Screen()
                if let bill = screen.bill(at: monthIndex, row: row,
                                          state: self.host?.state ?? BillV2Screen.State()) {
                    let state = BillDetailV2Screen.State()
                    state.bill = bill
                    push(BillDetailV2Screen(), state: state)
                }
            }
        // 账单详情页里的图标 / 开关
        case "icon", "toggle:counted":
            break

        // 收款详情页：点金额/奖励/说明/付款人/画像列，弹原生编辑框（对应
        // Android ReceiptDetailActivity 的 askNumber/askText/editColumn）。
        // consumer / contact / refund / claim / help 在 Android 里也是直接 return，保持空转。
        case "amountLabel", "amount", "actual", "prize", "quota", "note",
             "payer", "month", "total", "avg":
            handleReceiptDetail(token)
        case "consumer", "contact", "refund", "claim", "help":
            break

        // 收款记录页：整页「批量添加」（Android ReceiptActivity.showBatchSheet）
        case "batch":
            ReceiptBatchSheet.present { [weak self] in
                if let state = self?.host?.state as? ReceiptV2Screen.State {
                    state.scroll = 0
                }
                self?.host?.setNeedsDisplay()
            }
        case "points", "history":
            // 积分页 / 历史账单页：Android 里也没有对应实现
            break
        case "zhimaAvatar":
            if let host = self.host,
               let state = host.state as? ZhimaCreditV2Screen.State,
               let vc = EditPrompt.topViewController() {
                let sheet = UIAlertController(title: "更换信用名片头像", message: nil,
                                              preferredStyle: .actionSheet)
                sheet.addAction(UIAlertAction(title: "随机头像", style: .default) { _ in
                    PurseAvatarAPI.fetch { image in
                        guard let image else { self.showToast("头像接口暂时不可用", ok: false); return }
                        state.cardAvatar = image
                        host.setNeedsDisplay()
                    }
                })
                sheet.addAction(UIAlertAction(title: "从相册选择", style: .default) { _ in
                    QRImagePicker.present(from: vc) { image in
                        guard let image else { return }
                        state.cardAvatar = image
                        host.setNeedsDisplay()
                    }
                })
                sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
                if let popover = sheet.popoverPresentationController {
                    popover.sourceView = vc.view
                    popover.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.midY,
                                                width: 1, height: 1)
                    popover.permittedArrowDirections = []
                }
                vc.present(sheet, animated: true)
            }
        case let t where t.hasPrefix("avatar:") || t.hasPrefix("entry:"):
            // 小荷包成员头像：点击已有头像随机换一张，或从相册选。
            if t.hasPrefix("avatar:"), let host = self.host,
               host.screen is XiaohebaoV2Screen,
               let index = Int(t.dropFirst("avatar:".count)),
               let vc = EditPrompt.topViewController() {
                let sheet = UIAlertController(title: "更换头像", message: nil,
                                              preferredStyle: .actionSheet)
                sheet.addAction(UIAlertAction(title: "随机头像", style: .default) { _ in
                    PurseAvatarAPI.fetch { image in
                        guard let image else { self.showToast("头像接口暂时不可用", ok: false); return }
                        PurseStore.shared.setAvatar(image, at: index)
                        host.setNeedsDisplay()
                    }
                })
                sheet.addAction(UIAlertAction(title: "从相册选择", style: .default) { _ in
                    QRImagePicker.present(from: vc) { image in
                        guard let image else { return }
                        PurseStore.shared.setAvatar(image, at: index)
                        host.setNeedsDisplay()
                    }
                })
                sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
                if let popover = sheet.popoverPresentationController {
                    popover.sourceView = vc.view
                    popover.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.midY,
                                                width: 1, height: 1)
                    popover.permittedArrowDirections = []
                }
                vc.present(sheet, animated: true)
                return
            }
            // `entry:` 被三个屏复用，含义不同：
            //   · HomeShellScreen 的「我」页服务格 → 上面 L238 已处理（先命中）
            //   · ReceiptV2Screen → 单条收款详情（这里的 id 就是 Receipt.id）
            //   · TravelV2Screen → 出行页的入口
            // 所以这里按「当前是哪一屏」分流，不能只看令牌。
            if t.hasPrefix("entry:"), self.host?.screen is ReceiptV2Screen,
               let id = Int64(t.dropFirst("entry:".count)),
               let receipt = ReceiptStore.shared.byId(id) {
                let state = ReceiptDetailV2Screen.State()
                state.receipt = receipt
                push(ReceiptDetailV2Screen(), state: state)
            }
            // 头像大图（ReceiptV2Screen 的 avatar:<id>）与出行页入口：均未做

        // 理财页：展开保障面板 / 隐藏金额 / 主题选择
        case "toggleExpand":
            if let state = self.host?.state as? LicaiV2Screen.State {
                state.expand = state.expand > 0.5 ? 0 : 1
                self.host?.setNeedsDisplay()
                return
            }
            if let state = self.host?.state as? GrowthV2Screen.State {
                state.expand = state.expand > 0.5 ? 0 : 1
                self.host?.setNeedsDisplay()
            }
        case "toggleHide":
            if let state = self.host?.state as? LicaiV2Screen.State {
                state.hidden.toggle()
                self.host?.setNeedsDisplay()
                return
            }
            if self.host?.screen is GrowthV2Screen {
                GrowthStore.shared.hidden.toggle()
                self.host?.setNeedsDisplay()
            }
        case "version":
            // 理财主题选择（五档）。V1/V2/V3 的背景、卡面、铭牌差异太大，原地重绘会残留
            // 上一套资产；选择后统一要求退出重开，下次启动按新主题完整绘制。
            VersionPicker.present { _ in
                VersionPicker.requireRestart()
            }
        case "licai":
            push(LicaiV2Screen(), state: LicaiV2Screen.State())

        // 提现页：金额输入 / 键盘 / 支付密码
        case "avail":
            if self.host?.screen is WithdrawV2Screen {
                EditPrompt.present(title: "修改可用余额",
                                   current: ReceiptStore.money(WithdrawStore.shared.avail),
                                   numeric: true) { text in
                    let cleaned = text.filter { $0.isNumber || $0 == "." }
                    let value = Double(cleaned) ?? 0
                    WithdrawStore.shared.setAvail(value)
                    self.host?.setNeedsDisplay()
                }
            }
        case "amount":
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.keypad = true
                self.host?.setNeedsDisplay()
            }
        case "clear":
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.amount = ""
                self.host?.setNeedsDisplay()
            }
        case "submit", "key:submit":
            // 进支付密码那一层（要求金额合法）
            if let state = self.host?.state as? WithdrawV2Screen.State,
               WithdrawV2Screen.canWithdraw(state, avail: WithdrawStore.shared.avail) {
                state.password = true
                state.keypad = false
                self.host?.setNeedsDisplay()
            }
        case "key:hide":
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.keypad = false
                self.host?.setNeedsDisplay()
            }
        case "key:del":
            if let state = self.host?.state as? WithdrawV2Screen.State, !state.amount.isEmpty {
                Haptics.impact(.light)
                state.amount = String(state.amount.dropLast())
                self.host?.setNeedsDisplay()
            }
        case "key:dot":
            if let state = self.host?.state as? WithdrawV2Screen.State, !state.amount.contains(".") {
                Haptics.impact(.light)
                state.amount = state.amount.isEmpty ? "0." : state.amount + "."
                self.host?.setNeedsDisplay()
            }
        case "pin:close":
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.password = false
                state.pin = ""
                self.host?.setNeedsDisplay()
            }
        case "pin:del":
            if let state = self.host?.state as? WithdrawV2Screen.State, !state.pin.isEmpty {
                Haptics.impact(.light)
                state.pin = String(state.pin.dropLast())
                self.host?.setNeedsDisplay()
            }
        case "pin:forgot":
            // Android 侧这个热区没有绑定任何行为（WithdrawV2Screen.java 里没有对应 listener），
            // 为了保持 1:1 这里也不做动作，只重绘；不要在这里加自造的「功能未完成」类提示。
            self.host?.setNeedsDisplay()
        case "bank":
            // 弹出银行卡选择层（对应 Android 的 BankSheet）
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.bankSheet = true
                self.host?.setNeedsDisplay()
            }
        case "close":
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.bankSheet = false
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("pick:") && t.count > 5:
            // pick:<行号>：选中那张卡（不可用的卡选不上，由 WithdrawStore.select 保证）
            if let index = Int(t.dropFirst(5)),
               WithdrawStore.shared.allCards.indices.contains(index) {
                let card = WithdrawStore.shared.allCards[index]
                WithdrawStore.shared.select(card.id)
                if let state = self.host?.state as? WithdrawV2Screen.State {
                    state.bankSheet = false
                }
                self.host?.setNeedsDisplay()
            }
        case "add":
            // 小荷包「+」：调随机头像接口，成功就立即追加一个成员。
            if let host = self.host, host.screen is XiaohebaoV2Screen {
                guard !PurseStore.shared.isFull else {
                    self.showToast("最多添加 5 个头像", ok: false)
                    return
                }
                PurseAvatarAPI.fetch { image in
                    guard let image, PurseStore.shared.addAvatar(image) else {
                        self.showToast("头像接口暂时不可用", ok: false)
                        return
                    }
                    host.setNeedsDisplay()
                    self.showToast("已添加头像", ok: true)
                }
                return
            }
            // 「添加储蓄卡提现」：先给一张新卡，落位规则照 Java（插在第一张不可用卡之前）
            let store = WithdrawStore.shared
            let bank = WithdrawStore.banks[0]
            store.add(bank: bank.key,
                      name: WithdrawStore.bankCardName(bank.key) ?? "储蓄卡",
                      tail: store.newTail())
            if let state = self.host?.state as? WithdrawV2Screen.State {
                state.bankSheet = false
            }
            self.host?.setNeedsDisplay()
            self.showToast("已添加一张储蓄卡", ok: true)
        case let t where t.hasPrefix("pin:") && t.count == 5:
            // pin:0 … pin:9
            if let state = self.host?.state as? WithdrawV2Screen.State,
               state.pin.count < 6, let digit = t.last, digit.isNumber {
                state.pin.append(digit)
                // 假键盘也要有按键反馈；第六位留给下面的成功触觉，避免连震两下。
                if state.pin.count < 6 { Haptics.impact(.light) }
                // 六位齐了 → 进「提现成功」页（Android `WithdrawActivity` 262-271）
                if state.pin.count == 6 {
                    let amount = WithdrawV2Screen.value(state)
                    let card = WithdrawStore.shared.selected()
                    let bank = card.map { "\($0.name)(\($0.tail))" } ?? "银行卡"
                    state.password = false
                    state.keypad = false
                    state.pin = ""
                    state.amount = ""
                    let done = WithdrawDoneScreen.State()
                    done.amount = amount
                    done.bank = bank
                    // 支付密码输满六位是整条流程唯一的「成功」时刻，给一次成功触觉。
                    Haptics.success()
                    push(WithdrawDoneScreen(), state: done)
                    return
                }
                self.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("key:") && t.count == 5:
            // key:0 … key:9
            if let state = self.host?.state as? WithdrawV2Screen.State, let digit = t.last,
               digit.isNumber {
                Haptics.impact(.light)
                state.amount += String(digit)
                self.host?.setNeedsDisplay()
            }
        case "yuebao":
            // 「我的」页资产卡第 3 行 → 余额宝页
            push(YuebaoV2Screen(), state: YuebaoV2Screen.State())
        case "huabei":
            // 「我的」页资产卡第 4 行 → 花呗页
            push(HuabeiV2Screen(), state: HuabeiV2Screen.State())

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
            if let growth = self.host?.screen as? GrowthV2Screen, let state = self.host?.state as? GrowthV2Screen.State {
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
            // 账单消息页点某张卡 → 账单详情。卡片文字表在 `PayMsgV2Screen.bill(at:)` 里
            // 组装成 `Bill`，两页共用一份模型（Android 也是这么接的）。
            if let index = Int(t.dropFirst("detail:".count)),
               let bill = PayMsgV2Screen.bill(at: index) {
                let state = BillDetailV2Screen.State()
                state.bill = bill
                push(BillDetailV2Screen(), state: state)
            }

        // 消息列表页：「消息盒子」那一行进支付消息页
        case "box":
            push(PayMsgV2Screen(), state: PayMsgV2Screen.State())

        // 支付成功页：点「完成」/「回首页」都退回上一页
        case "done", "home":
            pop()

        // 收付款码页
        case "收付款":
            // 由支模块首页（ZhiHomeV2Screen 的「收付款」格）触发，与 Android 同一入口
            push(PayCodeV2Screen(), state: PayCodeV2Screen.State())
        case "出行":
            // 支模块首页的「出行」格 → 出行页（ZhiHomeActivity:303）
            push(TravelV2Screen(), state: TravelV2Screen.State())
        case "pay":
            // 扫码页左下的「付款码」→ 收付款码页
            push(PayCodeV2Screen(), state: PayCodeV2Screen.State())
        case "album":
            // 扫码页右下的「相册」：从相册选图识别（相机/相册链路未移植）
            break
        case "more":
            // 「⋯」在不同屏上含义不同：只有**账单列表页**的它对应 Android 的
            // `BillActivity.showMoreMenu()`（「账单管理」→ 清空 / 秒变富豪）。
            // 收款码、付款码、收款详情、收支分析上的「⋯」在 Android 上没有处理，保持空转。
            if self.host?.screen is BillV2Screen {
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
                push(CollectMoneyV2Screen(), state: CollectMoneyV2Screen.State())
            }

        // 余额页 / 小荷包：提现与转账（提现页 WithdrawV2Screen 未移植）
        case "withdraw":
            // 余额页的「提现」→ 提现页（数据层与页面都已移植）
            // Android 进页面就聚焦金额输入框，键盘立刻弹出（参考图上键盘是可见的），
            // 所以这里把 keypad 默认置 true，否则首屏会少一整块键盘、观感差很多。
            let withdrawState = WithdrawV2Screen.State()
            withdrawState.keypad = true
            push(WithdrawV2Screen(), state: withdrawState)
        // 小荷包：设置、转出、转入、攒钱目标、承诺券（对应页面均未移植）
        case "settings", "out", "in", "goal", "promise":
            break

        // 芝麻信用页：轮播、展开企业卡、助手卡、瀑布流模块
        case "carousel":
            // 横向拖动轮播（需要手势，Screen 协议目前只处理点击）
            break
        case "expandEnt":
            // 展开企业信用卡
            if let state = self.host?.state as? ZhimaCreditV2Screen.State {
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
            if let col = Int(t.dropFirst(4)), let state = self.host?.state as? ZhimaCreditV2Screen.State {
                ModulePicker.present(title: "添加到" + (col == 0 ? "左列" : "右列")) { key in
                    if let module = ZhimaCreditV2Screen.library.first(where: { $0.key == key }) {
                        state.append(col, key: module.key, height: module.height)
                        self.host?.setNeedsDisplay()
                    }
                }
            }

        // 出行页：地址 / 出发城市 / 到达城市 三处可编辑；里程、舱位、搜索在 Android 上也是空操作
        case "address":
            if let state = self.host?.state as? TravelV2Screen.State {
                EditPrompt.present(title: "编辑出发地", current: state.address,
                                   numeric: false) { text in
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { state.address = trimmed }
                    self.host?.setNeedsDisplay()
                }
            }
        case "city:0":
            if let state = self.host?.state as? TravelV2Screen.State {
                EditPrompt.present(title: "编辑出发城市", current: state.from,
                                   numeric: false) { text in
                    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty { state.from = trimmed }
                    self.host?.setNeedsDisplay()
                }
            }
        case "city:1":
            if let state = self.host?.state as? TravelV2Screen.State {
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
            if let state = self.host?.state as? TravelV2Screen.State {
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
            // 小支主页自己的 5 格底栏：0 支首页 / 1 理财 / 2 视频(未做) / 3 消息 / 4 我。
            // 之前这里只处理 Licai/Growth，ZhiHome 的 nav: 令牌直接落到 default，所以
            // 从「支」主页进小支后，底部四格全部点不动。
            if self.host?.screen is ZhiHomeV2Screen {
                switch index {
                case 0:
                    self.host?.state.scroll = 0
                    self.host?.setNeedsDisplay()
                case 1:
                    if LicaiVersion.blackCard(LicaiVersion.current()) {
                        push(GrowthV2Screen(), state: GrowthV2Screen.State())
                    } else {
                        push(LicaiV2Screen(), state: LicaiV2Screen.State())
                    }
                case 3:
                    push(MessageScreen(), state: MessageScreen.State())
                case 4:
                    push(MeScreen(), state: MeScreen.State())
                default:
                    break                       // 2 = 视频，Android 同样是空
                }
            }
            // 消息页 / 我的页也有同一根五格底栏；之前同样只处理理财页，
            // 所以进消息后点不回首页、也去不了我的。这里补齐。
            if self.host?.screen is MessageScreen {
                switch index {
                case 0:
                    pop()                       // 回小支首页（上一页）
                case 1:
                    if LicaiVersion.blackCard(LicaiVersion.current()) {
                        push(GrowthV2Screen(), state: GrowthV2Screen.State())
                    } else {
                        push(LicaiV2Screen(), state: LicaiV2Screen.State())
                    }
                case 4:
                    push(MeScreen(), state: MeScreen.State())
                default:
                    break
                }
            }
            if self.host?.screen is MeScreen {
                switch index {
                case 0:
                    pop()                       // 回小支首页
                case 1:
                    if LicaiVersion.blackCard(LicaiVersion.current()) {
                        push(GrowthV2Screen(), state: GrowthV2Screen.State())
                    } else {
                        push(LicaiV2Screen(), state: LicaiV2Screen.State())
                    }
                case 3:
                    push(MessageScreen(), state: MessageScreen.State())
                default:
                    break                       // 4 = 本页
                }
            }
            if self.host?.screen is LicaiV2Screen || self.host?.screen is GrowthV2Screen {
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
            // 账号设置页的用户名行走服务端改名（AuthService.changeUsername）；
            // 个人信息页那行只是改本地显示名。
            if self.host?.screen is SettingsV2Screen {
                promptRename()
                return
            }
            EditPrompt.present(title: "编辑名字", current: ProfileStore.shared.name,
                               numeric: false) { text in
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty { ProfileStore.shared.name = trimmed }
                self.host?.setNeedsDisplay()
            }
        case "editPhone":
            EditPrompt.present(title: "换绑手机号", current: ProfileStore.shared.displayPhoneRaw,
                               numeric: true) { text in
                let digits = text.filter { $0.isNumber }
                if digits.count >= 6 {
                    ProfileStore.shared.phone = digits
                    ProfileStore.shared.displayPhone = digits
                }
                self.host?.setNeedsDisplay()
            }

        // 切换账号页
        // 注意：`back` 在本 switch 的**前面**已有分支（`pop()`），这里不再重复 ——
        // 重复的 case 只会得到一条 "already handled" 警告，且第二个分支永远不执行。
        case "manage", "new", "footer":
            // 账号管理 / 新增账号 / 底部说明：Android 的 SwitchAccountV2Screen 里这三处是
            // 位图与文字（`sw_foot` 等），**没有点击监听**，所以这里保持无动作即为一致。
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
            if let state = self.host?.state as? AssetV2Screen.State {
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
            // 与 Android 一样先通知服务端，再清本地会话
            Task { @MainActor [weak self] in
                _ = await AuthService.logout()
                Session.shared.clear()
                self?.showToast("已退出登录", ok: true)
                self?.pop()
                self?.host?.setNeedsDisplay()
            }
        case let t where t.hasPrefix("call:"):
            guard let state = host?.state as? CallSetupScreen.State else { return }
            switch t {
            case "call:cancel":
                state.pendingAt = nil
                SimulatedCall.shared.cancel()
                showToast("已取消", ok: true)
            case "call:start":
                let now = Date()
                if state.timeMode == CallSetupScreen.modeDelay {
                    state.pendingAt = now.addingTimeInterval(
                        TimeInterval(max(1, state.delayMinutes)) * 60)
                } else if state.timeMode == CallSetupScreen.modeAt, state.atHour >= 0 {
                    var comps = Calendar.current.dateComponents([.year, .month, .day], from: now)
                    comps.hour = state.atHour
                    comps.minute = state.atMinute
                    var at = Calendar.current.date(from: comps) ?? now
                    if at <= now { at = at.addingTimeInterval(24 * 60 * 60) }
                    state.pendingAt = at
                } else if state.timeMode == CallSetupScreen.modeAt {
                    showToast("请先选择时间", ok: false)
                    return
                } else {
                    state.pendingAt = now
                }
                if let at = state.pendingAt {
                    SimulatedCall.shared.schedule(
                        at: at,
                        name: state.name.isEmpty ? "朋友" : state.name,
                        avatar: state.avatarImage) { [weak self] in
                            state.pendingAt = nil
                            self?.host?.setNeedsDisplay()
                        }
                }
                showToast("已设置，稍后自动来电", ok: true)
            case "call:time:now":
                state.timeMode = CallSetupScreen.modeNow
            case "call:time:delay":
                EditPrompt.present(title: "延迟分钟数",
                                   current: String(state.delayMinutes), numeric: true) { text in
                    let minutes = Int(text.filter { $0.isNumber }) ?? 1
                    state.delayMinutes = max(1, minutes)
                    state.timeMode = CallSetupScreen.modeDelay
                    self.host?.setNeedsDisplay()
                }
            case "call:time:at":
                EditPrompt.present(title: "指定时间（HH:mm）",
                                   current: CallSetupScreen.atText(state).contains(":")
                                        ? CallSetupScreen.atText(state) : "12:00",
                                   numeric: true) { text in
                    let digits = text.filter { $0.isNumber }
                    guard digits.count == 4 else { return }
                    let hour = Int(digits.prefix(2)) ?? 12
                    let minute = Int(digits.suffix(2)) ?? 0
                    guard hour < 24, minute < 60 else { return }
                    state.atHour = hour
                    state.atMinute = minute
                    state.timeMode = CallSetupScreen.modeAt
                    self.host?.setNeedsDisplay()
                }
            case "call:ring:pick":
                state.ringtoneLabel = "本地铃声"
                showToast("已选择本地铃声", ok: true)
            case "call:ring:default":
                state.ringtoneLabel = ""
            case "call:ring:preview":
                SimulatedCall.shared.preview()
                showToast("正在试听", ok: true)
            case "call:name":
                EditPrompt.present(title: "来电人姓名", current: state.name, numeric: false) { text in
                    state.name = text
                    self.host?.setNeedsDisplay()
                }
            case "call:contacts":
                guard let vc = EditPrompt.topViewController() else { return }
                guard !state.contacts.isEmpty else {
                    showToast("通讯录为空，请先在微里打开一次通讯录", ok: false)
                    return
                }
                let picker = ContactPickerViewController(contacts: state.contacts) { contact in
                    state.name = contact.name
                    state.avatarImage = contact.avatar
                    self.host?.setNeedsDisplay()
                }
                let nav = UINavigationController(rootViewController: picker)
                nav.modalPresentationStyle = .pageSheet
                vc.present(nav, animated: true)
            case let value where value.hasPrefix("call:contact:"):
                let id = String(value.dropFirst("call:contact:".count))
                if let contact = state.contacts.first(where: { $0.id == id }) {
                    state.name = contact.name
                    state.avatarImage = contact.avatar
                }
            default:
                break
            }
            host?.setNeedsDisplay()
        case "orderDetail":
            push(TaobaoOrderScreen(), state: TaobaoOrderScreen.State())
        case let t where t.hasPrefix("order:"):
            guard let state = host?.state as? TaobaoOrderScreen.State else { return }
            let key = String(t.dropFirst("order:".count))
            switch key {
            case "toggle":
                state.orderExpanded.toggle()
            case "copy":
                UIPasteboard.general.string = state.orderNo
                showToast("订单号已复制", ok: true)
            case "addCart":
                showToast("已加入购物车", ok: true)
            case "pickImage":
                if let vc = EditPrompt.topViewController() {
                    QRImagePicker.present(from: vc) { image in
                        guard let image else { return }
                        state.customGoodsImage = image
                        self.host?.setNeedsDisplay()
                    }
                }
            case "pickLogo":
                if let vc = EditPrompt.topViewController() {
                    QRImagePicker.present(from: vc) { image in
                        guard let image else { return }
                        state.shopLogo = image
                        self.host?.setNeedsDisplay()
                    }
                }
            case "receiver":
                EditPrompt.present(title: "修改收货人（姓名 手机号）",
                                   current: state.name + " " + state.phone,
                                   numeric: false) { text in
                    let parts = text.split(whereSeparator: { $0 == " " || $0 == "，" || $0 == "," })
                    if let name = parts.first { state.name = String(name) }
                    if parts.count > 1 { state.phone = parts[1].filter { $0.isNumber } }
                    self.host?.setNeedsDisplay()
                }
            case let value where value.hasPrefix("rec:"):
                guard let index = Int(value.dropFirst("rec:".count)) else { break }
                let rec = state.rec(at: index)
                EditPrompt.present(title: "推荐位 \(index + 1)（标题|价格|标签|销量）",
                                   current: [rec.title, rec.price, rec.tag, rec.sold].joined(separator: "|"),
                                   numeric: false) { text in
                    let parts = text.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
                    guard index < state.recs.count else { return }
                    state.recs[index] = TaobaoOrderScreen.State.Rec(
                        title: parts.count > 0 ? parts[0] : rec.title,
                        price: parts.count > 1 ? parts[1] : rec.price,
                        tag: parts.count > 2 ? parts[2] : rec.tag,
                        sold: parts.count > 3 ? parts[3] : rec.sold
                    )
                    self.host?.setNeedsDisplay()
                }
            default:
                let numericKeys: Set<String> = [
                    "goodPrice", "count", "paidPrice", "totalPrice", "discount",
                    "payable", "guaranteeCount", "orderNo", "tradeNo",
                ]
                EditPrompt.present(title: state.editTitle(key),
                                   current: state.editValue(key),
                                   numeric: numericKeys.contains(key)) { text in
                    state.applyEdit(key, text)
                    self.host?.setNeedsDisplay()
                }
            }
            host?.setNeedsDisplay()
        case "conv":
            push(ConversationScreen(), state: ConversationScreen.State())
        case let t where t.hasPrefix("conv:"):
            guard let screen = host?.screen as? ConversationScreen, let state = host?.state else { return }
            switch t {
            case "conv:send":
                screen.sendDraft(state)
            case "conv:voice":
                screen.toggleVoice(state)
            case "conv:emoji":
                screen.insertEmoji(state)
            case "conv:input":
                let current = (state as? ConversationScreen.State)?.draft ?? ""
                EditPrompt.present(title: "输入消息", current: current, numeric: false) { [weak self] text in
                    (state as? ConversationScreen.State)?.draft = text
                    self?.host?.setNeedsDisplay()
                }
            default:
                break
            }
            host?.setNeedsDisplay()
        case "resetInvite":
            push(ResetByInviteScreen(), state: ResetByInviteScreen.State())
        case let t where t.hasPrefix("reset:"):
            guard let screen = host?.screen as? ResetByInviteScreen,
                  let state = host?.state as? ResetByInviteScreen.State else { return }
            switch t {
            case "reset:restart":
                screen.showStep(1, state)
            case "reset:phone", "reset:invite", "reset:password", "reset:confirm":
                let titles = ["reset:phone": "手机号", "reset:invite": "邀请码",
                              "reset:password": "新密码", "reset:confirm": "确认新密码"]
                EditPrompt.present(title: titles[t] ?? "输入", current: "", numeric: t == "reset:phone") { [weak self] text in
                    screen.setText(t, text, in: state)
                    self?.host?.setNeedsDisplay()
                }
            case "reset:submit":
                if state.step == 1 {
                    guard let pair = screen.beginVerify(state) else { break }
                    Task { @MainActor in
                        let result = await AuthService.verifyInviteForReset(phone: pair.phone, inviteCode: pair.code)
                        screen.finishVerify(state, ok: result.0.ok, message: result.0.message, ticket: result.1)
                        self.host?.setNeedsDisplay()
                    }
                } else {
                    guard let trio = screen.beginSubmit(state) else { break }
                    Task { @MainActor in
                        let outcome = await AuthService.resetPassword(ticket: trio.ticket,
                                                                      password: trio.password,
                                                                      confirm: trio.confirm)
                        screen.finishReset(state, ok: outcome.ok, message: outcome.message)
                        self.host?.setNeedsDisplay()
                        if state.finished { self.pop() }
                    }
                }
            default:
                break
            }
            host?.setNeedsDisplay()
        case "soundPlay", "soundStop":
            host?.setNeedsDisplay()
        case "avatarRestore":
            // Android SettingsActivity 长按头像 → 确认「恢复默认头像」→ DoutuApi.removeAvatar
            let alert = UIAlertController(title: "恢复默认头像",
                                          message: "会删掉已上传的头像，回到用户名首字。",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "取消", style: .cancel))
            alert.addAction(UIAlertAction(title: "恢复", style: .destructive) { [weak self] _ in
                ProfileStore.shared.avatar = nil
                self?.host?.setNeedsDisplay()
                self?.showToast("已恢复默认头像", ok: true)
            })
            UpdatePrompt.topViewController()?.present(alert, animated: true)
        case "bell", "invite", "rights", "func", "plugin", "publish", "dots", "info":
            break

        // 「模拟」模块（Android `features/mock`）：首页 31 格 → 编辑页 → 成品页
        case let t where t.hasPrefix("mock:"):
            handleMock(String(t.dropFirst("mock:".count)))

        // 「工具」Tab（Android `MainActivity.drawTools` / `tools_page.json`）
        case let t where t.hasPrefix("tools:"):
            handleTools(String(t.dropFirst("tools:".count)))

        // 「视频」模块（Android `features/video`）：抖音 / 快手 / 微视
        case let t where t.hasPrefix("video:"):
            handleVideo(String(t.dropFirst("video:".count)))

        // 「支付宝」模块（Android `features/ali`）：目前只接了公众号消息页
        case let t where t.hasPrefix("ali:"):
            handleAli(String(t.dropFirst("ali:".count)))

        default:
            break
        }
    }

    // MARK: - 支付宝模块（Android `features/ali`）

    /// 令牌：`ali:gzh`（新建公众号）/ `ali:contacts`（通讯录，未移植）/
    /// `ali:box`（公众号会话，未移植）/ `ali:nav:<0..4>`（底部五格）。
    private func handleAli(_ rest: String) {
        if rest == "gzh" {
            // Android 是「名称 / 消息内容 / 三个开关 / 取消·确定」的浮层；
            // iOS 用系统弹窗收两个字段，确定后直接写回消息盒子那一行。
            let alert = UIAlertController(title: "新建公众号",
                                          message: "会生成一条公众号消息",
                                          preferredStyle: .alert)
            alert.addTextField { field in
                field.placeholder = "名称"
                field.text = "蚂蚁财富"
            }
            alert.addTextField { field in
                field.placeholder = "消息内容"
                field.text = "你的总资产收益已更新"
            }
            alert.addAction(UIAlertAction(title: "取消", style: .cancel))
            alert.addAction(UIAlertAction(title: "确定", style: .default) { [weak self] _ in
                let name = alert.textFields?.first?.text ?? ""
                let content = alert.textFields?.last?.text ?? ""
                if let state = self?.host?.state as? AliMsgScreen.State {
                    if !name.isEmpty { state.boxName = name }
                    if !content.isEmpty { state.boxSub = content }
                    self?.host?.setNeedsDisplay()
                }
                self?.showToast("已生成公众号消息", ok: true)
            })
            UpdatePrompt.topViewController()?.present(alert, animated: true)
            return
        }
        if rest == "contacts" {
            push(AliContactsScreen(), state: AliContactsScreen.State())
            return
        }
        if rest == "back" {
            pop()
            return
        }
        if rest == "addFriend" {
            EditPrompt.present(title: "添加朋友", current: "", numeric: false) { [weak self] text in
                let name = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !name.isEmpty else { return }
                AliContactStore.shared.add(name: name)
                self?.showToast("已添加 \(name)", ok: true)
                self?.host?.setNeedsDisplay()
            }
            return
        }
        if rest == "batch" || rest == "search" || rest.hasPrefix("top:") {
            showToast("该入口制作中", ok: false)
            return
        }
        if rest.hasPrefix("chat:") {
            handleAliChat(String(rest.dropFirst("chat:".count)))
            return
        }
        if rest.hasPrefix("delete:") {
            confirmDeleteAliContact(String(rest.dropFirst("delete:".count)))
            return
        }
        if rest.hasPrefix("contact:") {
            let name = String(rest.dropFirst("contact:".count))
            let sheet = UIAlertController(title: name, message: nil, preferredStyle: .actionSheet)
            sheet.addAction(UIAlertAction(title: "发起聊天", style: .default) { [weak self] _ in
                // 会话写进 AliChatStore 后，通讯录的「同步来的联系人」也会带上他
                AliChatStore.shared.upsert(name: name, avatar: nil, last: nil)
                self?.openAliChat(name: name)
            })
            sheet.addAction(UIAlertAction(title: "删除联系人", style: .destructive) { [weak self] _ in
                self?.confirmDeleteAliContact(name)
            })
            sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
            if let pop = sheet.popoverPresentationController, let view = host {
                pop.sourceView = view
                pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
                pop.permittedArrowDirections = []
            }
            UpdatePrompt.topViewController()?.present(sheet, animated: true)
            return
        }
        if rest == "redpacket" {
            push(AliRedPacketDetailScreen(), state: AliRedPacketDetailScreen.State())
            return
        }
        if rest == "transfer" {
            push(AliTransferScreen(), state: AliTransferScreen.State())
            return
        }
        if rest.hasPrefix("td:") {
            handleAliTransferDetail(String(rest.dropFirst("td:".count)))
            return
        }
        if rest.hasPrefix("transfer:") {
            handleAliTransfer(String(rest.dropFirst("transfer:".count)))
            return
        }
        if rest.hasPrefix("rp:") {
            handleAliRedPacket(String(rest.dropFirst("rp:".count)))
            return
        }
        if rest == "box" {
            showToast("公众号会话制作中", ok: false)
            return
        }
        if rest.hasPrefix("nav:") {
            switch String(rest.dropFirst("nav:".count)) {
            case "0":
                push(ZhiHomeV2Screen(), state: ZhiHomeV2Screen.State())
            case "1":
                push(LicaiV2Screen(), state: LicaiV2Screen.State())
            case "3":
                break                                   // 当前就是「消息」
            case "4":
                push(MeScreen(), state: MeScreen.State())
            default:
                showToast("该入口制作中", ok: false)
            }
        }
    }

    // MARK: - 视频模块（Android `features/video`）

    /// 令牌形状：`video:field:<key>` / `video:like` / `video:tab:<i>` /
    /// `video:pick`（选视频）/ `video:pickAvatar`（换头像）/ `video:nav:<home|friend|me>`。
    ///
    /// 三家页面共用 `VideoFeedState`，所以这里不用区分是哪个平台。
    private func handleVideo(_ rest: String) {
        guard let state = host?.state as? VideoFeedState else { return }

        if rest == "pick" {
            // Android 是从相册选视频循环播放；iOS 侧播放器还没接（见 HANDOFF 第 17 节）
            showToast("选择视频暂未移植", ok: false)
            return
        }
        if rest == "pickAvatar" {
            showToast("换头像暂未移植", ok: false)
            return
        }
        if rest == "like" {
            state.toggleLiked()
            host?.setNeedsDisplay()
            return
        }
        if rest.hasPrefix("tab:") {
            if let index = Int(rest.dropFirst("tab:".count)) {
                state.selectedTab = index
                host?.setNeedsDisplay()
            }
            return
        }
        if rest.hasPrefix("nav:") {
            showToast("该入口制作中", ok: false)
            return
        }
        if rest.hasPrefix("field:") {
            let key = String(rest.dropFirst("field:".count))
            guard let info = state.fieldInfo(key) else { return }
            EditPrompt.present(title: info.title, current: info.value, numeric: info.numeric) { [weak self] text in
                state.setField(key, text)
                self?.host?.setNeedsDisplay()
            }
        }
    }

    /// 打开某个人的单聊页（`AliChatScreen`）。会话不存在时先落库。
    private func openAliChat(name: String) {
        if !AliChatStore.shared.list().contains(where: { $0.name == name }) {
            AliChatStore.shared.upsert(name: name, avatar: nil, last: nil)
        }
        let state = AliChatScreen.State()
        state.title = name
        if let chat = AliChatStore.shared.list().first(where: { $0.name == name }),
           !chat.avatar.isEmpty {
            state.avatar = chat.avatar
        }
        push(AliChatScreen(), state: state)
    }

    /// 聊天页的令牌：`chip:<i>` 快捷短语 / `input` 输入 / `pay` `more` `voice` `emoji` `plus` /
    /// `action:<0..3>`（相册 / 小视频 / 红包 / 转账）。
    private func handleAliChat(_ rest: String) {
        guard let state = host?.state as? AliChatScreen.State else { return }
        let chips = AliChatScreen.quickPhrases

        if rest.hasPrefix("chip:") {
            if let index = Int(rest.dropFirst("chip:".count)), chips.indices.contains(index) {
                sendAliChat(state, text: chips[index])
            }
            return
        }
        if rest == "input" {
            EditPrompt.present(title: "发消息", current: "", numeric: false) { [weak self] text in
                self?.sendAliChat(state, text: text)
            }
            return
        }
        if rest == "pay" { showToast("收款（演示）", ok: false); return }
        if rest == "more" { showToast("更多（演示）", ok: false); return }
        if rest == "voice" { showToast("按住说话（演示）", ok: false); return }
        if rest == "emoji" { showToast("表情（演示）", ok: false); return }
        if rest == "plus" { showToast("更多功能（演示）", ok: false); return }
        if rest.hasPrefix("action:") {
            switch String(rest.dropFirst("action:".count)) {
            case "0": showToast("从相册选择图片（演示）", ok: false)
            case "1": showToast("录制小视频（演示）", ok: false)
            case "2":
                push(AliRedPacketDetailScreen(), state: AliRedPacketDetailScreen.State())
            default:
                push(AliTransferScreen(), state: AliTransferScreen.State())
            }
        }
    }

    /// 发一条消息：追加气泡 + 写回会话列表的最后一条（Java `AliChatActivity.send`）
    private func sendAliChat(_ state: AliChatScreen.State, text: String) {
        let message = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty else { return }
        state.messages.append(message)
        AliChatStore.shared.touch(name: state.title, last: message)
        host?.setNeedsDisplay()
    }

    /// 转账详情页（账单详情）的令牌：`more` 展开/收起两个额外行；`avatar`/`name`/`amount`/`status`
    /// 改汇总卡；`row:<标签>` 改三行明细里的值；`bill:<标签>` 改账单卡；`action:<i>` 七个入口。
    private func handleAliTransferDetail(_ rest: String) {
        let store = AliTransferStore.shared
        if rest == "more" {
            if let state = host?.state as? AliTransferDetailScreen.State {
                state.expandMore.toggle()
                host?.setNeedsDisplay()
            }
            return
        }
        if rest == "avatar" {
            let sheet = UIAlertController(title: "修改头像", message: nil, preferredStyle: .actionSheet)
            for name in store.randomNames.prefix(6) {
                sheet.addAction(UIAlertAction(title: name, style: .default) { [weak self] _ in
                    let asset = store.sender?.asset
                    store.sender = AliTransferStore.Sender(name: name, asset: asset)
                    self?.host?.setNeedsDisplay()
                })
            }
            sheet.addAction(UIAlertAction(title: "关闭", style: .cancel))
            if let pop = sheet.popoverPresentationController, let view = host {
                pop.sourceView = view
                pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
                pop.permittedArrowDirections = []
            }
            UpdatePrompt.topViewController()?.present(sheet, animated: true)
            return
        }
        if rest == "status" {
            let options = ["交易成功", "交易关闭", "处理中", "已退款"]
            presentAliOptions(title: "交易状态", options: options, current: store.status) { value in
                store.status = value
            }
            return
        }
        if rest.hasPrefix("bill:") {
            let key = String(rest.dropFirst("bill:".count))
            switch key {
            case "账单分类":
                presentAliOptions(title: "账单分类",
                                  options: ["其他", "餐饮美食", "交通出行", "日用百货",
                                            "服饰装扮", "转账红包", "收入"],
                                  current: store.category) { value in
                    store.category = value
                }
            case "标签和备注":
                EditPrompt.present(title: "标签和备注", current: store.tags, numeric: false) { [weak self] text in
                    store.tags = text
                    self?.host?.setNeedsDisplay()
                }
            default:   // 计入收支
                store.includedInBill.toggle()
                host?.setNeedsDisplay()
            }
            return
        }
        if rest.hasPrefix("action:") {
            let index = Int(rest.dropFirst("action:".count)) ?? 0
            let label = store.actionLabels.indices.contains(index) ? store.actionLabels[index] : ""
            showToast("已打开\(label)", ok: true)
            return
        }
        if rest.hasPrefix("row:") {
            let key = String(rest.dropFirst("row:".count))
            switch key {
            case "创建时间":
                EditPrompt.present(title: "创建时间", current: store.createdAt, numeric: false) { [weak self] text in
                    store.createdAt = text
                    self?.host?.setNeedsDisplay()
                }
            case "转账备注":
                EditPrompt.present(title: "转账备注", current: store.note, numeric: false) { [weak self] text in
                    store.note = text
                    self?.host?.setNeedsDisplay()
                }
            case "对方账户":
                EditPrompt.present(title: "对方账户", current: store.account, numeric: false) { [weak self] text in
                    store.account = text
                    self?.host?.setNeedsDisplay()
                }
            case "支付方式":
                presentAliOptions(title: "支付方式",
                                  options: ["余额", "余额宝", "花呗", "银行卡"],
                                  current: store.paymentMethod) { value in
                    store.paymentMethod = value
                }
            default:   // 订单号
                EditPrompt.present(title: "订单号", current: store.orderNumber(), numeric: false) { [weak self] text in
                    store.orderNo = text
                    self?.host?.setNeedsDisplay()
                }
            }
            return
        }
        switch rest {
        case "name":
            EditPrompt.present(title: "修改昵称", current: store.sender?.name ?? "", numeric: false) { [weak self] text in
                let name = text.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !name.isEmpty else { return }
                let asset = store.sender?.asset
                store.sender = AliTransferStore.Sender(name: name, asset: asset)
                self?.host?.setNeedsDisplay()
            }
        case "amount":
            EditPrompt.present(title: "修改金额（可带 + / -）",
                               current: store.amountText, numeric: true) { [weak self] text in
                // Java `editAmount()`：减号表示支出，其余按收入处理
                var raw = text.trimmingCharacters(in: .whitespacesAndNewlines)
                let income = !raw.hasPrefix("-")
                raw = raw.replacingOccurrences(of: "+", with: "")
                    .replacingOccurrences(of: "-", with: "")
                    .replacingOccurrences(of: "¥", with: "")
                    .replacingOccurrences(of: "￥", with: "")
                if let value = Double(raw), value.isFinite, value >= 0 {
                    store.amount = String(format: "%.2f", value)
                    store.income = income
                }
                self?.host?.setNeedsDisplay()
            }
        case "service":
            EditPrompt.present(title: "左侧小标题", current: store.serviceLabel, numeric: false) { [weak self] text in
                store.serviceLabel = text
                self?.host?.setNeedsDisplay()
            }
        case "billTitle":
            EditPrompt.present(title: "小标题", current: store.billTitle, numeric: false) { [weak self] text in
                store.billTitle = text
                self?.host?.setNeedsDisplay()
            }
        case "banner1":
            EditPrompt.present(title: "服务推荐文案", current: store.serviceTip, numeric: false) { [weak self] text in
                store.serviceTip = text
                self?.host?.setNeedsDisplay()
            }
        case "banner2":
            EditPrompt.present(title: "账单横幅文案", current: store.billTip, numeric: false) { [weak self] text in
                store.billTip = text
                self?.host?.setNeedsDisplay()
            }
        case "title":
            EditPrompt.present(title: "页面标题", current: store.pageTitle, numeric: false) { [weak self] text in
                store.pageTitle = text
                self?.host?.setNeedsDisplay()
            }
        default:
            break
        }
    }

    /// 一组选项弹成 action sheet（Java 的 `chooseValue`）
    private func presentAliOptions(title: String, options: [String], current: String,
                                   onPick: @escaping (String) -> Void) {
        let sheet = UIAlertController(title: title, message: nil, preferredStyle: .actionSheet)
        for option in options {
            let mark = option == current ? "✓ " : ""
            sheet.addAction(UIAlertAction(title: mark + option, style: .default) { [weak self] _ in
                onPick(option)
                self?.host?.setNeedsDisplay()
            })
        }
        sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let pop = sheet.popoverPresentationController, let view = host {
            pop.sourceView = view
            pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
            pop.permittedArrowDirections = []
        }
        UpdatePrompt.topViewController()?.present(sheet, animated: true)
    }

    /// 转账编辑页的令牌：`amount` / `note` / `sender`（action sheet）/ `income` / `expense` /
    /// `done`（完成：校验金额 → 落库 → 进详情页；详情页还没移植，先提示）。
    private func handleAliTransfer(_ rest: String) {
        let store = AliTransferStore.shared
        switch rest {
        case "amount":
            EditPrompt.present(title: "转账金额", current: store.amount, numeric: true) { [weak self] text in
                store.setAmount(text)
                self?.host?.setNeedsDisplay()
            }
        case "note":
            EditPrompt.present(title: "转账说明", current: store.note, numeric: false) { [weak self] text in
                store.note = text.trimmingCharacters(in: .whitespacesAndNewlines)
                self?.host?.setNeedsDisplay()
            }
        case "sender":
            let sheet = UIAlertController(title: "切换发送人", message: nil,
                                          preferredStyle: .actionSheet)
            for name in store.randomNames.prefix(6) {
                sheet.addAction(UIAlertAction(title: name, style: .default) { [weak self] _ in
                    store.sender = AliTransferStore.Sender(name: name, asset: nil)
                    store.account = ""
                    store.ensureSender()
                    self?.host?.setNeedsDisplay()
                })
            }
            sheet.addAction(UIAlertAction(title: "关闭", style: .cancel))
            if let pop = sheet.popoverPresentationController, let view = host {
                pop.sourceView = view
                pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
                pop.permittedArrowDirections = []
            }
            UpdatePrompt.topViewController()?.present(sheet, animated: true)
        case "income":
            store.income = true
            host?.setNeedsDisplay()
        case "expense":
            store.income = false
            host?.setNeedsDisplay()
        case "done":
            // Java `submit()`：金额必须大于 0，说明为空时默认「转账」
            let value = Double(store.amount) ?? 0
            guard value.isFinite, value > 0 else {
                showToast("请输入大于 0 的金额", ok: false)
                return
            }
            store.amount = String(format: "%.2f", value)
            if store.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                store.note = "转账"
            }
            store.stamp()
            push(AliTransferDetailScreen(), state: AliTransferDetailScreen.State())
        default:
            break
        }
    }

    /// 红包详情页的令牌：`from` / `sender`（切换发送人）、`amount`（金额）、
    /// `bless`（祝福语）、`thanks`（已答谢）、`open`（打开红包动画）。
    private func handleAliRedPacket(_ rest: String) {
        let store = AliRedPacketStore.shared
        switch rest {
        case "from", "sender":
            let sheet = UIAlertController(title: "切换发送人", message: nil, preferredStyle: .actionSheet)
            for (index, name) in store.senderNames.enumerated() {
                sheet.addAction(UIAlertAction(title: name, style: .default) { [weak self] _ in
                    store.senderIndex = index
                    self?.host?.setNeedsDisplay()
                })
            }
            sheet.addAction(UIAlertAction(title: "关闭", style: .cancel))
            if let pop = sheet.popoverPresentationController, let view = host {
                pop.sourceView = view
                pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.midY, width: 1, height: 1)
                pop.permittedArrowDirections = []
            }
            UpdatePrompt.topViewController()?.present(sheet, animated: true)
        case "amount":
            EditPrompt.present(title: "红包金额", current: store.amount, numeric: true) { [weak self] text in
                store.setAmount(text)
                self?.host?.setNeedsDisplay()
            }
        case "bless":
            EditPrompt.present(title: "祝福语", current: store.blessing, numeric: false) { [weak self] text in
                store.blessing = text
                self?.host?.setNeedsDisplay()
            }
        case "thanks":
            showToast("已答谢", ok: true)
        case "open":
            showToast("打开红包动画", ok: false)
        default:
            break
        }
    }

    /// 删联系人要连消息列表一起删（否则「同步来的联系人」会把它带回来）
    private func confirmDeleteAliContact(_ name: String) {
        let alert = UIAlertController(title: "删除联系人「\(name)」？",
                                      message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "删除", style: .destructive) { [weak self] _ in
            AliContactStore.shared.remove(name: name)
            AliChatStore.shared.delete(name: name)
            self?.host?.setNeedsDisplay()
            self?.showToast("已删除 \(name)", ok: true)
        })
        UpdatePrompt.topViewController()?.present(alert, animated: true)
    }

    // MARK: - 工具 Tab（Android `MainActivity.drawTools`）

    /// 令牌形状：`tools:<action>|<显示名>`（`tools_page.json` 里每个入口的 action）。
    ///
    /// action 词表（2026-10-24 同步时的 43 个入口）：
    ///   · `wechat` / `wechat:#/wechat/...` / `wechat:feature:...` —— 微信 clone 的站内路由
    ///   · `wechat:tool:redpacket|transfer|gzh` —— 先开微信，再调页面里的直达函数
    ///   · `act:licai|balance|bill|assets|me|paymsg|withdraw|receipt|paysuccess` —— 支模块已移植的页
    ///   · `alipay` —— 小支主页
    ///   · 其余（`wxcreatechat` / `sentrp` / `ali*` / `qq*` / 视频四格）—— iOS 还没有对应页面，
    ///     与 Android 对未实现模块的处理一致：提示「制作中」
    private func handleTools(_ rest: String) {
        let parts = rest.split(separator: "|", maxSplits: 1).map(String.init)
        let action = parts.first ?? ""
        let name = parts.count > 1 ? parts[1] : "该入口"

        if action == "alipay" {
            push(ZhiHomeV2Screen(), state: ZhiHomeV2Screen.State())
            return
        }
        if action == "wechat" {
            presentWeb(route: "#/wechat")
            return
        }
        if action.hasPrefix("wechat:") {
            let route = String(action.dropFirst("wechat:".count))
            if route.hasPrefix("tool:") {
                openWeChatTool(String(route.dropFirst("tool:".count)))
                return
            }
            presentWeb(route: route)
            return
        }
        if action.hasPrefix("act:") {
            let key = String(action.dropFirst("act:".count))
            switch key {
            case "licai":
                push(LicaiV2Screen(), state: LicaiV2Screen.State())
            case "balance":
                push(BalanceV2Screen(), state: BalanceV2Screen.State())
            case "bill":
                push(BillV2Screen(), state: BillV2Screen.State())
            case "assets":
                push(AssetV2Screen(), state: AssetV2Screen.State())
            case "me":
                push(MeScreen(), state: MeScreen.State())
            case "paymsg":
                push(PayMsgV2Screen(), state: PayMsgV2Screen.State())
            case "withdraw":
                let state = WithdrawV2Screen.State()
                state.keypad = true
                push(WithdrawV2Screen(), state: state)
            case "receipt":
                push(ReceiptV2Screen(), state: ReceiptV2Screen.State())
            case "paysuccess":
                push(PaySuccessV2Screen(), state: PaySuccessV2Screen.State())
            default:
                showToast("\(name) 制作中", ok: false)
            }
            return
        }
        // 视频模块（工具页 4 个入口）
        switch action {
        case "videohome":
            push(VideoDouyinScreen(), state: VideoDouyinScreen.State())
            return
        case "kuaishou":
            push(VideoKuaishouScreen(), state: VideoKuaishouScreen.State())
            return
        case "weishi":
            push(VideoWeishiScreen(), state: VideoWeishiScreen.State())
            return
        case "alimsg":
            push(AliMsgScreen(), state: AliMsgScreen.State())
            return
        case "alicontacts":
            push(AliContactsScreen(), state: AliContactsScreen.State())
            return
        case "alired":
            push(AliRedPacketDetailScreen(), state: AliRedPacketDetailScreen.State())
            return
        case "alitransfer":
            push(AliTransferScreen(), state: AliTransferScreen.State())
            return
        case "alicreatechat":
            // Android 是一个带头像预览的弹窗；iOS 先用输入框收昵称，确定后直接进聊天页
            EditPrompt.present(title: "对方昵称", current: "飞跃巅峰", numeric: false) { [weak self] text in
                let name = text.trimmingCharacters(in: .whitespacesAndNewlines)
                let final = name.isEmpty ? "飞跃巅峰" : name
                AliChatStore.shared.upsert(name: final, avatar: "res:ali_chat_avatar_b",
                                           last: "开始聊天吧")
                self?.openAliChat(name: final)
            }
            return
        default:
            break
        }
        showToast("\(name) 制作中", ok: false)
    }

    /// 微信里的两个直达入口：开微信 → 等首页就绪 → 调页面里的函数。
    ///
    /// 这两个函数（`__fwOpenToolPay` / `__fwOpenGzhDialog`）是 2026-10-24 从 Android
    /// 的 override 同步过来的（见 `_ios/port-wechat-helpers.mjs`）。
    /// 页面还没加载完时调用会被函数里的 `try/catch` 吞掉，所以先等 0.9 秒再调一次。
    private func openWeChatTool(_ kind: String) {
        presentWeb(route: "#/wechat")
        let script: String
        if kind == "gzh" {
            script = "window.__fwOpenGzhDialog && window.__fwOpenGzhDialog()"
        } else {
            script = "window.__fwOpenToolPay && window.__fwOpenToolPay('\(kind)')"
        }
        for delay in [0.9, 2.0] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
                guard let web = self?.presentedWeChat else { return }
                web.run(script)
            }
        }
    }

    /// 当前呈现在最上层的微信 WebView（`presentWeb` 复用它）。
    ///
    /// ⚠️ 不能对 `host` 取 `presentedViewController`：Router.host 是 `ScreenHostView`（UIView），
    /// 那个属性只在 UIViewController 上有（编译报「has no member 'presentedViewController'」）。
    /// `UpdatePrompt.topViewController()` 会沿着 presentedViewController 一路走到最上层，
    /// 微信页在 present 之后就是它 —— 与 `presentWeb` 里找 host 的方式一致。
    private var presentedWeChat: WeChatV2Host? {
        UpdatePrompt.topViewController() as? WeChatV2Host
    }

    // MARK: - 模拟模块（Android `features/mock`）

    /// `mock:home:<i>`   从 31 格首页点进某家银行（只接了前 6 家）
    /// `mock:drawer`     打开/关闭右侧子页抽屉（`mock:drawer:close` 只关）
    /// `mock:sub:<i>`    切子页
    /// `mock:device:<i>` 切机型模板
    /// `mock:gen`        「生成图片」→ 进成品页
    /// `mock:reset`      「恢复默认」→ 重取交易时间
    /// `mock:template` / `mock:save` / `mock:fill`  与 Android 一样只提示一句
    /// `mock:field:<标签>` 点预览里的文字 → 弹输入框改值（编辑页与成品页都能改）
    private func handleMock(_ rest: String) {
        // 首页宫格
        if rest.hasPrefix("home:") {
            let index = Int(rest.dropFirst("home:".count)) ?? 0
            guard index >= 0, index < MockHome.labels.count else { return }
            let name = MockHome.labels[index]
            guard let module = MockModule(rawValue: index), module.isBuilt else {
                // 与 Android `SimulationActivity.card()` 的 else 分支一致
                showToast("\(name) 制作中", ok: false)
                return
            }
            let screen = MockEditorScreen()
            mockEditor = screen
            push(screen, state: MockEditorScreen.State(module: module))
            return
        }

        let parts = rest.split(separator: ":", maxSplits: 1).map(String.init)
        let verb = parts.first ?? ""
        let arg = parts.count > 1 ? parts[1] : ""

        // 成品页
        if self.host?.screen is MockDetailScreen,
           let ds = self.host?.state as? MockDetailScreen.State {
            switch verb {
            case "field":
                guard !arg.isEmpty else { return }
                EditPrompt.present(title: arg, current: ds.renderState.currentValue(arg),
                                   numeric: false) { [weak self] text in
                    if arg == "交易时间" {
                        ds.time = text
                    } else {
                        ds.fields[arg] = text
                    }
                    self?.host?.setNeedsDisplay()
                }
            case "reset":
                ds.time = MockState.nowString()
                host?.setNeedsDisplay()
            default:
                break
            }
            return
        }

        // 编辑页
        guard let es = self.host?.state as? MockEditorScreen.State else { return }
        switch verb {
        case "drawer":
            es.drawerOpen = (arg == "close") ? false : !es.drawerOpen
            host?.setNeedsDisplay()
        case "sub":
            if let index = Int(arg) {
                es.sub = index
                es.drawerOpen = false
                es.scroll = 0
                host?.setNeedsDisplay()
            }
        case "device":
            if let index = Int(arg) {
                es.device = index
                host?.setNeedsDisplay()
            }
        case "gen":
            let detail = MockDetailScreen()
            push(detail, state: MockDetailScreen.State(module: es.module, sub: es.sub,
                                                       time: es.time, fields: es.fields))
        case "reset":
            es.time = MockState.nowString()
            host?.setNeedsDisplay()
        case "template":
            let device = MockDevices.all.indices.contains(es.device) ? MockDevices.all[es.device] : "本机"
            showToast("当前模版：" + device, ok: true)
        case "save":
            showToast("已保存模版", ok: true)
        case "fill":
            showToast("点预览里对应文字即可修改", ok: true)
        case "field":
            guard !arg.isEmpty else { return }
            EditPrompt.present(title: arg, current: es.currentValue(arg),
                               numeric: false) { [weak self] text in
                es.apply(arg, text)
                self?.host?.setNeedsDisplay()
            }
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
    private var homeV2State: HomeV2Screen.State?

    init(screen: Screen) {
        hostView = ScreenHostView(screen: screen)
        if let home = screen as? HomeV2Screen {
            homeV2State = HomeV2Screen.State()
            hostView.state = homeV2State!
        } else if let home = screen as? HomeShellScreen {
            homeState = HomeShellScreen.State()
            hostView.state = homeState!
        } else if screen is TermsScreen {
            // 之前这里没给 TermsScreen 配 State，draw/hit 每次都拿新 State：
            // 滚动量不保存、reachedBottom 永远 false，表现就是“滑不动、同意永远灰着”。
            hostView.state = TermsScreen.State()
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
            guard let self else { return }
            if let state = self.homeV2State {
                await ConfigLoader.load(into: state)
            } else if let state = self.homeState {
                await ConfigLoader.load(into: state)
            } else {
                return
            }
            self.hostView.setNeedsDisplay()
            // 配置里带水印文案（sys_config.watermark_overlay_text），拉到后刷新覆盖层
            WatermarkOverlay.attachIfNeeded()
        }
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // 整套界面是按 Android 的浅色参考图移植的，没有深色配色。
        // 系统开深色时，UIAlertController / 键盘 / 滚动条会变成黑色，与页面割裂；
        // 这里在 window 层级统一压成 light，一次覆盖所有模态。
        view.window?.overrideUserInterfaceStyle = .light
        // 每次页面可见都按当前会员状态重挂水印（与 Android 的 attachIfNeeded 时机一致）
        WatermarkOverlay.attachIfNeeded(preferredWindow: view.window)
        Router.shared?.refreshMemberStatusIfNeeded()
        // 真机免点按验证：Documents/demo.txt 里放一个命令令牌，启动即跳到那一页
        DebugBridge.shared.runLaunchTokenIfNeeded()
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
    override var preferredStatusBarStyle: UIStatusBarStyle {
        hostView.screen.lightStatusBar ? .lightContent : .default
    }
    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge { .all }
}

// MARK: - App 入口

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
            // 启动落地页：未同意《用户须知》时先给条款页（对应 Android 的 TermsActivity），
            // 同意后写入 terms.accepted，下次直接进首页外壳（三栏 + 四平台页签）。
            ScreenHostContainer(screen: UserDefaults.standard.bool(forKey: "terms.accepted")
                                ? HomeShellScreen()
                                : TermsScreen())
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
