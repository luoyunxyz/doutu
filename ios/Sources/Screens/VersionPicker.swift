import UIKit

extension Notification.Name {
    /// 理财主题变了 —— 「我的」页 / 消息页 / 支首页 的配色与图标要跟着换
    static let doutuThemeChanged = Notification.Name("doutu.theme.changed")
}

/// 理财主题选择面板 —— 对应 Android 各页 `version` 令牌弹出的「版本选择」弹窗。
///
/// 五档（普通版 / 成长版 / V1 / V2 / V3）各有配色与切图，数据在 `LicaiVersion` 里。
/// Android 用自定义底部弹窗；iOS 这里用 `UIAlertController` 的 action sheet：
/// 五个按钮是五档名字，`message` 里逐行列出**每档的「获得方式」原文**（照抄 `LicaiVersion.tip`），
/// 当前档位用 `●` 标出。容器不同是有意的平台适配，文案一字不改。
enum VersionPicker {
    private static weak var presented: UIAlertController?

    static func present(onPick: @escaping (Int) -> Void) {
        guard presented == nil else { return }
        guard let host = EditPrompt.topViewController() else { return }
        let current = LicaiVersion.current()

        // 消息区：五档「●/○ 名称  获得方式」
        var lines: [String] = []
        for value in 0...4 {
            let mark = value == current ? "● " : "○ "
            lines.append(mark + LicaiVersion.name(value) + "  " + LicaiVersion.tip(value))
        }

        let sheet = UIAlertController(title: "我的理财主题", message: lines.joined(separator: "\n"),
                                      preferredStyle: .actionSheet)
        for value in 0...4 {
            let title = value == current ? "✓ " + LicaiVersion.name(value) : LicaiVersion.name(value)
            sheet.addAction(UIAlertAction(title: title, style: .default) { _ in
                presented = nil
                LicaiVersion.current(value)
                onPick(value)
            })
        }
        sheet.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in presented = nil })

        // iPad 上 action sheet 必须给锚点，否则会崩
        if let popover = sheet.popoverPresentationController {
            popover.sourceView = host.view
            popover.sourceRect = CGRect(x: host.view.bounds.midX, y: host.view.bounds.midY,
                                        width: 1, height: 1)
            popover.permittedArrowDirections = []
        }
        presented = sheet
        host.present(sheet, animated: true)
    }
}
