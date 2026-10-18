import UIKit

/// 页面上「点一下数字就能改」的那一层 —— 对应 Android 各页的 `AlertDialog + EditText`。
///
/// Android 侧每个页面自己弹一个带输入框的对话框（`GrowthScreen`、`LicaiScreen`、`AssetScreen`… 都有），
/// 标题取自各自的 `TITLES` 表。这里把**弹窗本身**统一成一份，页面只需要回答两件事：
///
///   · 这个键现在是什么值（用来预填输入框）
///   · 用户改成了什么（写回去）
///
/// 这样做的好处：一个已移植页面只要实现了 `EditableScreen`，它上面所有 `edit:` / `text:` / `num:`
/// 令牌就全都活了 —— 不用为每页各写一套弹窗。
protocol EditableState: AnyObject {
    /// 这个键对应的标题（「编辑总资产」这种）
    func editTitle(_ key: String) -> String
    /// 当前值，用来预填
    func editValue(_ key: String) -> String
    /// 用户改完之后写回；空串表示清空/恢复默认（由页面自己决定）
    func applyEdit(_ key: String, _ text: String)
}

enum EditPrompt {
    /// 正在弹的那个 —— 防止连点弹出好几个
    private static weak var presented: UIAlertController?

    /// 弹出编辑框。键盘类型按 `numeric` 选（数字键还是全键盘）。
    static func present(title: String, current: String, numeric: Bool,
                        onCommit: @escaping (String) -> Void) {
        guard presented == nil else { return }
        guard let host = topViewController() else { return }

        let alert = UIAlertController(title: title, message: nil,
                                      preferredStyle: .alert)
        alert.addTextField { field in
            field.text = current
            field.clearButtonMode = .whileEditing
            field.keyboardType = numeric ? .decimalPad : .default
            field.autocorrectionType = .no
            field.autocapitalizationType = .none
            // 弹出即全选，方便整段替换
            DispatchQueue.main.async {
                field.selectAll(nil)
            }
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in
            presented = nil
        })
        alert.addAction(UIAlertAction(title: "确定", style: .default) { _ in
            presented = nil
            let text = alert.textFields?.first?.text ?? ""
            onCommit(text)
        })
        presented = alert
        host.present(alert, animated: true)
    }

    /// 从令牌里拿出键名：`edit:total` → `total`
    static func key(from token: ActionToken) -> String? {
        for prefix in ["edit:", "num:", "text:"] where token.hasPrefix(prefix) {
            let key = String(token.dropFirst(prefix.count))
            return key.isEmpty ? nil : key
        }
        return nil
    }

    /// 这个键是不是数字类的（决定键盘类型）
    static func isNumeric(_ key: String) -> Bool {
        let lowered = key.lowercased()
        if lowered.contains("name") || lowered.contains("tag") || lowered.contains("line")
            || lowered.contains("holder") || lowered.contains("note") || lowered.contains("title") {
            return false
        }
        return true
    }

    /// 找到当前最上层的控制器（弹窗要从它 present）
    static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController
            ?? scene?.windows.first?.rootViewController
        while let next = top?.presentedViewController, !(next is UIAlertController) {
            top = next
        }
        return top
    }
}
