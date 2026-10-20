import SwiftUI

/// 登录 / 注册表单（原生控件）。
///
/// Android 那边 `LoginActivity` 也是**原生表单**（ScrollView + EditText），不是 Canvas ——
/// 这类页面 1:1 移植的正确做法就是用各平台原生控件，用户在两端的操作感受才一致。
///
/// 对接的接口见 `AuthService`：登录 / 邀请码注册 / 邀请码找回密码（两步）。
struct LoginView: View {
    /// 关闭时回调（登录成功后应刷新界面）
    var onDone: (() -> Void)?
    /// 由 LoginPresenter 注入：直接 dismiss UIHostingController，避免 presentationMode 在自定义转场下失效。
    var onClose: (() -> Void)? = nil

    private enum Mode: String, CaseIterable {
        case login = "登录"
        case register = "注册"
        case reset = "找回密码"
    }

    @State private var mode: Mode = .login
    @State private var username = ""
    @State private var inviteCode = ""
    @State private var phone = ""
    @State private var password = ""
    @State private var confirm = ""
    @State private var resetTicket = ""
    @State private var busy = false
    @State private var message = ""
    @State private var messageIsError = false

    /// ⚠️ Environment(\.dismiss) 是 iOS 15 起的 API，本项目要支持 iOS 14.3，
    /// 所以用 14 上就有的 presentationMode（LoginView 是以 modal sheet 呈现的，语义一致）。
    @Environment(\.presentationMode) private var presentationMode

    var body: some View {
        ZStack {
            Color(red: 0.94, green: 0.96, blue: 0.995).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    header
                    panel
                    legal
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 28)
            }
        }
        .preferredColorScheme(.light)
    }

    private var header: some View {
        ZStack(alignment: .topTrailing) {
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.29, green: 0.48, blue: 0.97),
                    Color(red: 0.55, green: 0.36, blue: 0.96),
                ]),
                startPoint: .topLeading, endPoint: .bottomTrailing)
            VStack(alignment: .leading, spacing: 12) {
                Text("注册 / 登录")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white)
                Text("一键解析 · 服务端合成 · 支持批量")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.86))
                Text("未开通会员也能用，只是成品带水印")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.95))
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Color.white.opacity(0.16))
                    .clipShape(Capsule())
                    .padding(.top, 8)
                Spacer(minLength: 0)
            }
            .padding(.leading, 28)
            .padding(.top, 48)

            Button {
                closeLogin()
            } label: {
                Text("×")
                    .font(.system(size: 30, weight: .light))
                    .foregroundColor(.white.opacity(0.92))
                    .frame(width: 44, height: 44)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 300)
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .padding(.horizontal, 18)
        .padding(.top, 16)
    }

    private var panel: some View {
        VStack(alignment: .leading, spacing: 18) {
            modeTabs
            fields
            if !message.isEmpty {
                Text(message)
                    .font(.system(size: 13))
                    .foregroundColor(messageIsError ? .red : Color(red: 0.36, green: 0.4, blue: 0.48))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            submitButton
            footerLinks
        }
        .padding(.horizontal, 22)
        .padding(.top, 22)
        .padding(.bottom, 26)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .padding(.horizontal, 24)
        .padding(.top, -54)
    }

    private var modeTabs: some View {
        HStack(spacing: 0) {
            tabButton("登录", .login)
            tabButton("注册新账号", .register)
        }
        .padding(4)
        .background(Color(red: 0.95, green: 0.96, blue: 0.975))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func tabButton(_ title: String, _ value: Mode) -> some View {
        Button {
            mode = value
            message = ""
        } label: {
            Text(title)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(mode == value ? .white : Color(red: 0.42, green: 0.45, blue: 0.52))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(mode == value
                            ? Color(red: 0.29, green: 0.48, blue: 0.97)
                            : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
    }

    @ViewBuilder private var fields: some View {
        if mode == .login {
            inputField("手机号 / 用户名", "手机号或用户名都行", text: $username)
            inputField("密码", "至少 6 位", text: $password, secure: true)
        } else if mode == .register {
            inputField("用户名", "请输入用户名", text: $username)
            inputField("邀请码", "请输入邀请码", text: $inviteCode)
            inputField("手机号", "请输入手机号", text: $phone, number: true)
            inputField("密码", "至少 6 位", text: $password, secure: true)
            inputField("确认密码", "再次输入密码", text: $confirm, secure: true)
        } else if resetTicket.isEmpty {
            inputField("注册手机号", "请输入注册手机号", text: $phone, number: true)
            inputField("邀请码", "请输入邀请码", text: $inviteCode)
        } else {
            inputField("新密码", "至少 6 位", text: $password, secure: true)
            inputField("确认新密码", "再次输入新密码", text: $confirm, secure: true)
        }
    }

    private func inputField(_ label: String, _ placeholder: String,
                            text: Binding<String>, secure: Bool = false,
                            number: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label)
                .font(.system(size: 15))
                .foregroundColor(Color(red: 0.39, green: 0.42, blue: 0.48))
            Group {
                if secure {
                    SecureField(placeholder, text: text)
                } else if number {
                    TextField(placeholder, text: text).keyboardType(.numberPad)
                } else {
                    TextField(placeholder, text: text)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                }
            }
            .font(.system(size: 17))
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: 52)
            .background(Color(red: 0.96, green: 0.965, blue: 0.975))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var submitButton: some View {
        Button(action: submit) {
            HStack(spacing: 8) {
                if busy {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                }
                Text(busy ? "请稍候…" : submitTitle)
                    .font(.system(size: 20, weight: .semibold))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 58)
            .background(canSubmit && !busy
                        ? Color(red: 0.29, green: 0.48, blue: 0.97)
                        : Color(red: 0.55, green: 0.66, blue: 0.94))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .disabled(busy || !canSubmit)
        .padding(.top, 6)
    }

    private var footerLinks: some View {
        VStack(spacing: 12) {
            Text("还没有账号？点上面的「注册新账号」，用邀请码创建。")
                .font(.system(size: 15))
                .foregroundColor(Color(red: 0.44, green: 0.47, blue: 0.54))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            Button("忘记密码？请联系客服协助重置") {
                dismissThen("entry:0")
            }
            .font(.system(size: 16))
            .foregroundColor(Color(red: 0.29, green: 0.48, blue: 0.97))
            Button("邀请码重置密码") {
                dismissThen("resetInvite")
            }
            .font(.system(size: 16))
            .foregroundColor(Color(red: 0.29, green: 0.48, blue: 0.97))
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
    }

    private func closeLogin() {
        if let onClose {
            onClose()
        } else {
            presentationMode.wrappedValue.dismiss()
        }
    }

    private func dismissThen(_ token: String) {
        // 等 LoginHostingController 真正 viewDidDisappear 后再执行，
        // 避免跳转页被推到登录模态下面。
        Router.shared?.pendingAfterLoginDismiss = token
        closeLogin()
        // 兜底：万一 viewDidDisappear 没有触发 onDismiss，0.6 秒后也执行一次。
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            Router.shared?.runPendingAfterLoginDismiss()
        }
    }

    private var legal: some View {
        Text("本软件仅用于处理您自己创作或拥有版权的素材，请勿用于去除他人作品的版权水印、搬运或二次发布。平台提供侵权投诉与下架通道，收到有效投诉将定位到具体账号与任务并停止服务。")
            .font(.system(size: 13))
            .foregroundColor(Color(red: 0.48, green: 0.5, blue: 0.56))
            .lineSpacing(4)
            .padding(.horizontal, 34)
            .padding(.top, 22)
    }

    private var submitTitle: String {
        switch mode {
        case .login: return "登 录"
        case .register: return "注 册"
        case .reset: return resetTicket.isEmpty ? "验证邀请码" : "重置密码"
        }
    }

    private var canSubmit: Bool {
        switch mode {
        case .login:
            return !username.isEmpty && !password.isEmpty
        case .register:
            return !username.isEmpty && !inviteCode.isEmpty && !phone.isEmpty
                && !password.isEmpty && !confirm.isEmpty
        case .reset:
            return resetTicket.isEmpty
                ? (!phone.isEmpty && !inviteCode.isEmpty)
                : (!password.isEmpty && !confirm.isEmpty)
        }
    }

    private func submit() {
        busy = true
        message = ""
        Task {
            let outcome: AuthService.Outcome
            switch mode {
            case .login:
                outcome = await AuthService.login(account: username, password: password)
            case .register:
                outcome = await AuthService.register(username: username, inviteCode: inviteCode,
                                                    phone: phone, password: password,
                                                    confirm: confirm)
            case .reset:
                if resetTicket.isEmpty {
                    let (result, ticket) = await AuthService.verifyInviteForReset(phone: phone,
                                                                                 inviteCode: inviteCode)
                    outcome = result
                    resetTicket = ticket
                    password = ""
                    confirm = ""
                } else {
                    outcome = await AuthService.resetPassword(ticket: resetTicket,
                                                             password: password, confirm: confirm)
                }
            }
            await MainActor.run {
                busy = false
                message = outcome.message
                messageIsError = !outcome.ok
                // 登录/注册成功：直接关闭并用新会话刷新界面
                if outcome.ok && mode != .reset {
                    onDone?()
                    // 登录/注册成功回到「我」页；写 State 后关闭登录页即可看到。
                    Router.shared?.selectProfileTab()
                    closeLogin()
                }
                if outcome.ok && mode == .reset && !resetTicket.isEmpty {
                    // 第一步通过：留在本页继续填新密码
                    message = "验证通过，请设置新密码"
                }
            }
        }
    }
}

/// 登录表单的宿主控制器：只在真正消失时解除水印抑制。
/// 之前把 endSuppress 放在 present 的 completion 里，而那个 completion 是“弹出完成后”
/// 立刻执行，不是关闭后执行，所以登录页刚出现水印就被重新挂上来了。
final class LoginHostingController: UIHostingController<LoginView> {
    var onDismiss: (() -> Void)?
    private var didNotify = false

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        guard !didNotify,
              isBeingDismissed || presentingViewController == nil
                || navigationController?.isBeingDismissed == true else { return }
        didNotify = true
        onDismiss?()
    }
}

/// 用当前最上层控制器把登录表单弹出来（UIKit 世界里调用 SwiftUI 视图的标准做法）。
@MainActor
private final class LoginCloseBox {
    weak var navigation: UINavigationController?
    func close() {
        navigation?.dismiss(animated: true)
    }
}

enum LoginPresenter {
    @MainActor
    static func present(onDone: (() -> Void)? = nil) {
        guard let host = UpdatePrompt.topViewController() else { return }
        let box = LoginCloseBox()
        let controller = LoginHostingController(
            rootView: LoginView(onDone: onDone, onClose: { box.close() }))

        // 用一个空导航控制器承载：present 时无动画，再 push 登录页，
        // 得到和 Android 一样的“从右向左进入”，也避免自定义转场导致 dismiss 卡死。
        let nav = UINavigationController(rootViewController: UIViewController())
        nav.setNavigationBarHidden(true, animated: false)
        nav.modalPresentationStyle = .fullScreen
        box.navigation = nav

        // 登录/注册页不铺水印（会盖住输入框；Android 侧同样跳过登录页）
        WatermarkOverlay.beginSuppress()
        controller.onDismiss = {
            WatermarkOverlay.endSuppress()
            WatermarkOverlay.attachIfNeeded()
            onDone?()
            // 登录页完全消失后再执行 resetInvite 等挂起跳转。
            Router.shared?.runPendingAfterLoginDismiss()
        }
        host.present(nav, animated: false) {
            nav.pushViewController(controller, animated: true)
        }
    }
}
