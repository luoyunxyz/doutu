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
        NavigationView {
            Form {
                Section {
                    Picker("方式", selection: $mode) {
                        ForEach(Mode.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    // 用邀请码重置密码（对应 Android 的 ResetByInviteActivity）
                    Button("用邀请码重置密码") {
                        Router.shared?.handle("resetInvite", at: .zero)
                    }
                }

                Section {
                    if mode == .login {
                        TextField("手机号或用户名", text: $username)
                            .textContentType(.username)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    } else if mode == .register {
                        TextField("用户名", text: $username)
                            .autocapitalization(.none)
                        TextField("邀请码", text: $inviteCode)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                        TextField("手机号", text: $phone)
                            .keyboardType(.numberPad)
                    } else {
                        if resetTicket.isEmpty {
                            TextField("注册手机号", text: $phone).keyboardType(.numberPad)
                            TextField("邀请码", text: $inviteCode).autocapitalization(.none)
                        }
                    }
                }

                Section {
                    if mode != .reset || !resetTicket.isEmpty {
                        SecureField("密码", text: $password)
                        SecureField("确认密码", text: $confirm)
                    }
                }

                if !message.isEmpty {
                    Section {
                        Text(message)
                            .foregroundColor(messageIsError ? .red : .secondary)
                            .font(.footnote)
                    }
                }

                Section {
                    Button(action: submit) {
                        HStack {
                            Spacer()
                            if busy { ProgressView().padding(.trailing, 6) }
                            Text(busy ? "请稍候…" : submitTitle).bold()
                            Spacer()
                        }
                    }
                    .disabled(busy || !canSubmit)
                }
            }
            .navigationTitle(mode == .login ? "登录" : (mode == .register ? "注册" : "找回密码"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
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
                    presentationMode.wrappedValue.dismiss()
                }
                if outcome.ok && mode == .reset && !resetTicket.isEmpty {
                    // 第一步通过：留在本页继续填新密码
                    message = "验证通过，请设置新密码"
                }
            }
        }
    }
}

/// 用当前最上层控制器把登录表单弹出来（UIKit 世界里调用 SwiftUI 视图的标准做法）。
enum LoginPresenter {
    @MainActor
    static func present(onDone: (() -> Void)? = nil) {
        guard let host = UpdatePrompt.topViewController() else { return }
        let controller = UIHostingController(rootView: LoginView(onDone: onDone))
        controller.modalPresentationStyle = .pageSheet
        // 登录/注册页不铺水印（会盖住输入框；Android 侧同样跳过登录页）
        WatermarkOverlay.beginSuppress()
        host.present(controller, animated: true) {
            // 关闭后恢复：这段时间会员状态可能已经变了，重新评估一次
            WatermarkOverlay.endSuppress()
            WatermarkOverlay.attachIfNeeded()
            onDone?()
        }
    }
}
