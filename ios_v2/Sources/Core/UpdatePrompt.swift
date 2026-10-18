import UIKit

/// 版本更新提示 —— `account/UpdatePrompt.java` 的 iOS 版。
///
/// 两端都用**系统弹窗**（Android `AlertDialog` / iOS `UIAlertController`），
/// 不自绘外观：系统弹窗才是各平台用户熟悉的样子，这也是「1:1」在弹窗上的正确含义。
///
/// 行为对齐 Android：
///   · 回前台复查，节流 60 秒
///   · 点「稍后再说」→ 本次运行内不再打扰，下次冷启动照常检查
///   · 服务端说没有更新了 → 立刻撤掉「必须更新」状态，避免用户被永久锁在升级页
///
/// **一处有意偏离 Android**：强制更新时 Android 的做法是「关掉弹窗就杀进程」。
/// iOS 上不这么做 —— ① App Store 审核明确禁止程序主动退出（`exit(0)` 必被拒）；
/// ② 杀进程对用户更粗暴。这里改成「只要还没更新，每次回前台就立刻重新弹」，
/// 效果上同样进不去，但不会闪退，也过得了审核。
final class UpdatePrompt {
    static let shared = UpdatePrompt()

    /// 回前台复查的最短间隔（与 Android 的 RECHECK_MS 一致）
    private let recheckMs: TimeInterval = 60

    private var lastCheckAt: Date?
    private var checking = false
    /// 用户点了「稍后再说」：本次运行内不再弹
    private var snoozed = false
    /// 正在显示弹窗
    private(set) var isShowing = false
    /// 后台标了「必须更新」
    private(set) var forced = false
    /// 强制更新状态下用户还没去更新（回前台就再弹）
    private var forcePending = false

    private init() {}

    // MARK: - 入口

    /// 每次回到前台调一次
    func maybeCheck() {
        // 强制更新没完成：不受节流与「稍后再说」影响，立刻再提醒
        if forcePending {
            Task { await check() }
            return
        }
        guard !snoozed, !checking, !isShowing else { return }
        let now = Date()
        if let last = lastCheckAt, now.timeIntervalSince(last) < recheckMs { return }
        Task { await check() }
    }

    private func check() async {
        guard !checking, !isShowing else { return }
        checking = true
        lastCheckAt = Date()
        defer { checking = false }

        let envelope = await ApiClient.shared.call(
            "GET", "/api/v1/version/check",
            query: ["platform": DtwConfig.platform(), "version_code": String(DtwConfig.versionCode)],
            root: true
        )
        guard envelope.ok, let data = envelope.data as? [String: Any] else { return }

        // 服务端已经没有更新（比如版本被下架）：撤掉强制状态，别把用户锁在升级页
        guard data["has_update"] as? Bool == true else {
            forced = false
            forcePending = false
            return
        }
        guard let latest = data["latest"] as? [String: Any] else { return }
        let rawURL = (latest["url"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rawURL.isEmpty, let url = URL(string: rawURL) else { return }

        let force = latest["force"] as? Bool ?? false
        forced = force
        forcePending = force
        await show(versionName: latest["version_name"] as? String ?? "",
                   changelog: latest["changelog"] as? String ?? "",
                   url: url, force: force)
    }

    // MARK: - 弹窗

    @MainActor
    private func show(versionName: String, changelog: String, url: URL, force: Bool) {
        guard !isShowing, let host = Self.topViewController() else { return }
        isShowing = true

        let title = "发现新版本" + (versionName.isEmpty ? "" : " " + versionName)
        let alert = UIAlertController(
            title: title,
            message: changelog.isEmpty ? nil : "更新内容：\n" + changelog,
            preferredStyle: .alert
        )

        // 「立即更新」：用系统浏览器打开后台「版本推送」里填的下载地址
        alert.addAction(UIAlertAction(title: "立即更新", style: .default) { [weak self] _ in
            self?.isShowing = false
            UIApplication.shared.open(url)
        })

        if !force {
            // 非强制才有「稍后再说」：本次运行不再复查
            alert.addAction(UIAlertAction(title: "稍后再说", style: .cancel) { [weak self] _ in
                self?.isShowing = false
                self?.snoozed = true
            })
        }

        // 注意：`present` 的 completion 是「弹出动画结束」时回调，**不是**弹窗关闭时 ——
        // 把 isShowing 写在里面会导致弹窗还开着、状态已经变回「没在显示」，
        // 回前台时可能在同一个 host 上再叠一个弹窗。所以清标志放在各个 action 里。
        host.present(alert, animated: true)
    }

    // MARK: - 顶层控制器

    /// 找当前正在显示的最上层控制器（弹在用户眼前那一页上，与 Android 的 AlertDialog 一致）
    static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
        let windowScene = scenes.first { $0.activationState == .foregroundActive } as? UIWindowScene
        let window = windowScene?.windows.first { $0.isKeyWindow } ?? windowScene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
