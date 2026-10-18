import UIKit

/// 账单管理菜单 —— 对应 Android `BillActivity.showMoreMenu()`：
///
/// ```
/// 标题「账单管理」，两项：清空 / 秒变富豪
///   清空   → 先确认「清空所有账单？会把全部账单删掉，之后不会自动恢复。」再清
///   秒变富豪 → BillStore.becomeRich()，提示「已生成大额账单」
/// ```
///
/// Android 用 `AlertDialog.setItems`；iOS 这里用 action sheet，**文案逐字照抄**。
/// 清空是真的清（`BillStore.clear` 之后不再自动生成），所以按 Android 的做法先二次确认。
enum BillMenu {
    enum Choice { case clear, becomeRich }

    private static weak var presented: UIAlertController?

    static func present(onPick: @escaping (Choice) -> Void) {
        guard presented == nil else { return }
        guard let host = EditPrompt.topViewController() else { return }

        let sheet = UIAlertController(title: "账单管理", message: nil, preferredStyle: .actionSheet)
        sheet.addAction(UIAlertAction(title: "清空", style: .destructive) { _ in
            presented = nil
            confirmClear(onPick: onPick)
        })
        sheet.addAction(UIAlertAction(title: "秒变富豪", style: .default) { _ in
            presented = nil
            onPick(.becomeRich)
        })
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

    /// 二次确认（Android 的 `confirmClear`）—— 清空不可恢复，必须问一次
    private static func confirmClear(onPick: @escaping (Choice) -> Void) {
        guard let host = EditPrompt.topViewController() else { return }
        let alert = UIAlertController(title: "清空所有账单？",
                                      message: "会把全部账单删掉，之后不会自动恢复。",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "清空", style: .destructive) { _ in
            onPick(.clear)
        })
        host.present(alert, animated: true)
    }
}
