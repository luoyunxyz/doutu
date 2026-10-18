import SwiftUI
import UIKit

/// 重构版第一阶段的“服务器控制台”：
/// 直接验证 DTW/1 握手、root 配置、登录、会员状态、版本检查与微信 WebView。
/// 后续阶段用它做回归入口，完整 UI 会替换掉这个控制台。
struct ControlView: View {
    @State private var account = ""
    @State private var password = ""
    @State private var log = "准备就绪"
    @State private var busy = false

    var body: some View {
        NavigationView {
            VStack(spacing: 10) {
                Text("Doutu v2 · Server Control")
                    .font(.system(size: 20, weight: .bold))
                Text("platform=\(DtwConfig.platform())  base=\(DtwConfig.baseUrl)")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)

                HStack {
                    TextField("账号 / 手机号", text: $account)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                    SecureField("密码", text: $password)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }

                HStack(spacing: 8) {
                    button("握手/配置") { await callConfig() }
                    button("登录") { await login() }
                    button("会员状态") { await callStatus() }
                }
                HStack(spacing: 8) {
                    button("版本检查") { await callVersion() }
                    button("微信 WebView") { presentWeb() }
                    button("清空") { log = "" }
                }

                ScrollView {
                    Text(log)
                        .font(.system(size: 12, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(8)
                }
                .background(Color.black.opacity(0.04))
                .cornerRadius(8)
            }
            .padding(12)
            .navigationBarHidden(true)
            .onAppear { Task { await callConfig() } }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }

    private func button(_ title: String, action: @escaping () async -> Void) -> some View {
        Button {
            Task {
                guard !busy else { return }
                busy = true
                await action()
                busy = false
            }
        } label: {
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(Color.blue.opacity(0.12))
                .cornerRadius(7)
        }
        .disabled(busy)
    }

    private func append(_ text: String) {
        log += "\n" + text
    }

    private func summary(_ data: Any?) -> String {
        guard let data, JSONSerialization.isValidJSONObject(data),
              let raw = try? JSONSerialization.data(withJSONObject: data, options: [.sortedKeys]),
              let text = String(data: raw, encoding: .utf8) else { return "\(String(describing: data))" }
        return String(text.prefix(600))
    }

    @MainActor
    private func callConfig() async {
        append("→ GET /api/v1/config (DTW root)")
        let env = await ApiClient.shared.call("GET", "/api/v1/config", root: true)
        append("code=\(env.code) msg=\(env.msg)")
        append(summary(env.data))
    }

    @MainActor
    private func login() async {
        let a = account.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !a.isEmpty, !password.isEmpty else { append("请输入账号和密码"); return }
        append("→ login \(a)")
        let result = await AuthService.login(account: a, password: password)
        append("login ok=\(result.ok) msg=\(result.message)")
        append("device=\(Session.shared.deviceId) token=\(Session.shared.token.isEmpty ? "-" : "set")")
    }

    @MainActor
    private func callStatus() async {
        append("→ GET /api/v1/auth/status")
        let env = await ApiClient.shared.call("GET", "/api/v1/auth/status")
        append("code=\(env.code) msg=\(env.msg)")
        append(summary(env.data))
    }

    @MainActor
    private func callVersion() async {
        append("→ GET /api/v1/upgrade/check")
        let env = await ApiClient.shared.call(
            "GET", "/api/v1/upgrade/check",
            query: ["platform": DtwConfig.platform(), "version_code": String(DtwConfig.versionCode)],
            root: true
        )
        append("code=\(env.code) msg=\(env.msg)")
        append(summary(env.data))
    }

    @MainActor
    private func presentWeb() {
        guard let root = UIApplication.shared.windows.first(where: { $0.isKeyWindow })?.rootViewController
                ?? UIApplication.shared.windows.first?.rootViewController else {
            append("找不到 key window")
            return
        }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        let web = WebHostController(route: "#/wechat")
        web.modalPresentationStyle = .fullScreen
        top.present(web, animated: true)
    }
}
