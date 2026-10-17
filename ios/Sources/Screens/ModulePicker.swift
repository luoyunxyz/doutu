import UIKit

/// 芝麻信用「添加模块」选择器 —— 对应 Android `ZhimaCreditActivity.pickModule`（8 个模块的列表）。
///
/// Android 用 `AlertDialog.Builder(...).setItems(labels, ...)`；iOS 这里用 action sheet，
/// **八个模块名照抄 `ZhimaCreditScreen.library` 的 label**，一字不改。
enum ModulePicker {
    private static weak var presented: UIAlertController?

    static func present(title: String, onPick: @escaping (String) -> Void) {
        guard presented == nil else { return }
        guard let host = EditPrompt.topViewController() else { return }

        let modules = ZhimaCreditScreen.library
        let sheet = UIAlertController(title: title, message: nil, preferredStyle: .actionSheet)
        for module in modules {
            sheet.addAction(UIAlertAction(title: module.label, style: .default) { _ in
                presented = nil
                onPick(module.key)
            })
        }
        sheet.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in presented = nil })

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
