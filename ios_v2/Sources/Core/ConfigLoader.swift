import Foundation

/// 公开配置加载器 —— 对应 Android `MainActivity.refreshPublicConfig()`。
///
/// 拉 `/api/v1/config`（root 类接口，不要求登录），把两样东西填进界面：
///   1. `playbook.entertainment.sections` → 娱乐页的分区/行（服务端下发，抄走包也拿不到）
///   2. `banner.*` → 首页横幅的文案池与轮播节奏
///
/// 设计原则：**拉失败不影响界面**。取不到就用内置兜底（一条「短视频一键去水印」+ 时间问候语），
/// 用户照样能用。
protocol HomeConfigState: AnyObject {
    var playbookSections: [[String: Any]]? { get set }
    var entertainmentRows: [Playbook.RowBox] { get set }
    var entertainmentBottom: CGFloat { get set }
}

extension HomeShellScreen.State: HomeConfigState {}
extension HomeV2Screen.State: HomeConfigState {}

enum ConfigLoader {
    /// 拉一次公开配置并应用到界面状态
    @discardableResult
    static func load<S: HomeConfigState>(into state: S) async -> Bool {
        let envelope = await ApiClient.shared.call("GET", "/api/v1/config", root: true)
        guard envelope.ok, let data = envelope.data as? [String: Any] else { return false }

        // ① 娱乐页玩法
        if let playbook = data["playbook"] as? [String: Any],
           let entertainment = playbook["entertainment"] as? [String: Any],
           let sections = entertainment["sections"] as? [[String: Any]] {
            state.playbookSections = sections
        }

        // ② 首页横幅
        if let banner = data["banner"] as? [String: Any] {
            let enabled = banner["enabled"] as? Bool ?? true
            let hints = (banner["hint_list"] as? [String] ?? []).filter { !$0.isEmpty }
            let defaultHint = (banner["default_hint"] as? String ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            // 上排主文案优先用后台配的默认标题；没有就用内置问候语（BannerStore 内部处理）
            let defaultTitle = (banner["default_title"] as? String ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            var pool = hints
            if !defaultHint.isEmpty { pool.insert(defaultHint, at: 0) }
            BannerStore.shared.apply(
                title: defaultTitle.isEmpty ? nil : defaultTitle,
                hints: pool,
                rotateSec: banner["hint_rotate_sec"] as? Int ?? 5,
                enabled: enabled
            )
        }

        // ③ 人工客服（对应 Android `Session.applyCustomerService`）
        // 之前漏了这一段，iOS 客服页永远显示「二维码待后台配置 / 联系方式待后台配置」，
        // 而 Android 同账号是有服务器下发的二维码与联系方式的。
        if let service = data["customer_service"] as? [String: Any] {
            let defaults = UserDefaults.standard
            defaults.set((service["notice"] as? String ?? "")
                            .trimmingCharacters(in: .whitespacesAndNewlines),
                         forKey: "config.cs_notice")
            defaults.set((service["contact"] as? String ?? "")
                            .trimmingCharacters(in: .whitespacesAndNewlines),
                         forKey: "config.cs_contact")
            defaults.set((service["qr_image"] as? String ?? "")
                            .trimmingCharacters(in: .whitespacesAndNewlines),
                         forKey: "config.cs_qr_image")
        }
        return true
    }
}
