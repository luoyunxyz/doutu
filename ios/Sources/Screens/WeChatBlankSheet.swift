import UIKit

/// 微主页顶部空白 / “Windows 微信已登录”条的底部菜单（对应 Android 的
/// `__fwNativeOpenChatContextAt` 那张 2 列白卡）。
final class WeChatBlankSheet: UIViewController {
    private let items = [
        "返回APP主页", "调节字体大小",
        "添加微信收款助手", "批量添加聊天记录",
        "清空所有聊天记录", "开启电脑设备登录显示"
    ]
    private let contact = "不懂操作？联系客服"
    private let dim = UIControl()
    private let panel = UIView()
    private var buttons: [UIButton] = []
    private var onAction: ((String) -> Void)?

    static func present(from host: UIViewController, onAction: @escaping (String) -> Void) {
        let sheet = WeChatBlankSheet()
        sheet.onAction = onAction
        sheet.modalPresentationStyle = .overFullScreen
        sheet.modalTransitionStyle = .coverVertical
        host.present(sheet, animated: true)
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        dim.backgroundColor = UIColor.black.withAlphaComponent(0.45)
        dim.addTarget(self, action: #selector(close), for: .touchUpInside)
        view.addSubview(dim)

        panel.backgroundColor = .white
        panel.layer.cornerRadius = 18
        panel.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        panel.layer.masksToBounds = true
        view.addSubview(panel)

        for (index, title) in items.enumerated() {
            let button = makeButton(title: title,
                                    color: UIColor(red: 0x22 / 255, green: 0x22 / 255,
                                                   blue: 0x22 / 255, alpha: 1))
            button.tag = index
            button.addTarget(self, action: #selector(tapItem(_:)), for: .touchUpInside)
            panel.addSubview(button)
            buttons.append(button)
        }

        let customer = makeButton(title: contact,
                                  color: UIColor(red: 0x15 / 255, green: 0xb8 / 255,
                                                 blue: 0x63 / 255, alpha: 1))
        customer.tag = 100
        customer.addTarget(self, action: #selector(tapItem(_:)), for: .touchUpInside)
        panel.addSubview(customer)
        buttons.append(customer)

        let cancel = makeButton(title: "取消",
                                color: UIColor(red: 0x22 / 255, green: 0x22 / 255,
                                               blue: 0x22 / 255, alpha: 1))
        cancel.tag = 101
        cancel.backgroundColor = UIColor(red: 0xf6 / 255, green: 0xf6 / 255,
                                          blue: 0xf6 / 255, alpha: 1)
        cancel.addTarget(self, action: #selector(close), for: .touchUpInside)
        panel.addSubview(cancel)
        buttons.append(cancel)
    }

    private func makeButton(title: String, color: UIColor) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setTitleColor(color, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16)
        button.backgroundColor = .white
        button.layer.borderWidth = 0.5
        button.layer.borderColor = UIColor(red: 0xe8 / 255, green: 0xe8 / 255,
                                           blue: 0xe8 / 255, alpha: 1).cgColor
        return button
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        dim.frame = view.bounds
        let bottom = view.safeAreaInsets.bottom
        let rowH: CGFloat = 56
        let contactH: CGFloat = 54
        let cancelH: CGFloat = 52
        let panelH = rowH * 3 + contactH + cancelH + 8 + bottom
        panel.frame = CGRect(x: 0, y: view.bounds.height - panelH,
                             width: view.bounds.width, height: panelH)

        let w = panel.bounds.width
        for row in 0..<3 {
            for col in 0..<2 {
                let index = row * 2 + col
                buttons[index].frame = CGRect(x: CGFloat(col) * w / 2,
                                              y: CGFloat(row) * rowH,
                                              width: w / 2, height: rowH)
            }
        }
        buttons[6].frame = CGRect(x: 0, y: rowH * 3, width: w, height: contactH)
        buttons[7].frame = CGRect(x: 0, y: rowH * 3 + contactH + 8,
                                  width: w, height: cancelH)
    }

    @objc private func tapItem(_ sender: UIButton) {
        let action: String
        if sender.tag == 100 {
            action = contact
        } else if sender.tag >= 0 && sender.tag < items.count {
            action = items[sender.tag]
        } else {
            return
        }
        dismiss(animated: true) { [weak self] in
            self?.onAction?(action)
        }
    }

    @objc private func close() {
        dismiss(animated: true)
    }
}
