import UIKit

/// 「修改应用图标」—— 对应 Android `features/icon/AppIconChooser.java`。
///
/// iOS 没有 `activity-alias`，备选图标走 Asset Catalog 的 alternate app icons：
/// `AppIconAlipay / AppIconAlipayHK / AppIconWechat`（project.yml 里的
/// `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`），运行时 `setAlternateIconName`。
/// 「豆图」是主图标，`setAlternateIconName(nil)` 切回。
///
/// 与 Android 的差异：iOS 不允许在运行时改启动器上的应用名，所以这里只换图标，
/// 名称仍然是「豆图」。底栏弹窗的布局（把手 / 标题 / 说明 / 四行 / 蓝勾）照 Android 画。
final class AppIconChooser: UIViewController {
    private struct Option {
        let label: String
        let imageName: String
        let alternateName: String?
    }

    private static let options: [Option] = [
        // 黑色支付宝图标（AppIconAlipayHK）直接显示为「支付宝」，不再带 HK。
        Option(label: "支付宝", imageName: "PreviewAppIconAlipayHK", alternateName: "AppIconAlipayHK"),
        Option(label: "支付宝蓝", imageName: "PreviewAppIconAlipay", alternateName: "AppIconAlipay"),
        Option(label: "微信", imageName: "PreviewAppIconWechat", alternateName: "AppIconWechat"),
        Option(label: "豆图", imageName: "PreviewAppIconDoutu", alternateName: nil),
    ]

    private let dim = UIControl()
    private let sheet = UIView()
    private var rows: [UIControl] = []

    static func present() {
        guard let host = UpdatePrompt.topViewController() else { return }
        if Session.shared.isActivated {
            presentPicker(from: host)
            return
        }
        guard Session.shared.isLoggedIn else {
            let alert = UIAlertController(
                title: "修改应用图标",
                message: "修改应用图标是会员功能。\n\n请先在「我」页面登录并开通会员，之后才能切换图标。",
                preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
            host.present(alert, animated: true)
            return
        }
        // 本地 activated 可能是旧值（续费/换账号/刚登录），先向服务端刷新一次再判断。
        Router.shared?.refreshMemberStatus {
            guard let current = UpdatePrompt.topViewController() else { return }
            if Session.shared.isActivated {
                presentPicker(from: current)
            } else {
                let alert = UIAlertController(
                    title: "修改应用图标",
                    message: "修改应用图标是会员功能。\n\n当前账号未开通或会员已到期，请联系客服开通后再来切换。",
                    preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
                current.present(alert, animated: true)
            }
        }
    }

    private static func presentPicker(from host: UIViewController) {
        let picker = AppIconChooser()
        picker.modalPresentationStyle = .overFullScreen
        picker.modalTransitionStyle = .crossDissolve
        host.present(picker, animated: false)
    }

    /// 图标切换成功后，询问是否把同名桌面图标也加上。
    private static func offerWebClip(_ option: Option) {
        guard let host = UpdatePrompt.topViewController() else { return }
        let alert = UIAlertController(
            title: "添加桌面图标",
            message: "是否一次添加「支付宝 / 支付宝蓝 / 微信 / 豆图」四个桌面图标？\n\n支付宝和支付宝蓝打开小支主页，微信打开小微主页，豆图打开应用首页。\n\niOS 会打开 Safari 下载描述文件，还需要你在「设置」里点一次「安装」。",
            preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "仅换图标", style: .cancel))
        alert.addAction(UIAlertAction(title: "添加四个图标", style: .default) { _ in
            installWebClip(from: host)
        })
        host.present(alert, animated: true)
    }

    private static func installWebClip(from host: UIViewController) {
        let items: [(label: String, icon: UIImage?, url: String)] = [
            ("支付宝", UIImage(named: "PreviewAppIconAlipayHK"), "doutu://zhi"),
            ("支付宝蓝", UIImage(named: "PreviewAppIconAlipay"), "doutu://zhi"),
            ("微信", UIImage(named: "PreviewAppIconWechat"), "doutu://wechat"),
            ("豆图", UIImage(named: "PreviewAppIconDoutu"), "doutu://"),
        ]
        guard let data = WebClipBuilder.profile(items: items) else {
            let alert = UIAlertController(title: "生成失败", message: "描述文件生成失败，请重试。",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
            host.present(alert, animated: true)
            return
        }
        LocalWebServer.webClipData = data
        guard let server = LocalWebServer.shared else {
            let alert = UIAlertController(title: "不可用", message: "本地服务未启动。",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
            host.present(alert, animated: true)
            return
        }
        server.start { port in
            let actual = port ?? 18080
            DispatchQueue.main.async {
                guard let url = URL(string: "http://localhost:\(actual)/__webclip.mobileconfig") else { return }
                UIApplication.shared.open(url, options: [:]) { ok in
                    if !ok {
                        let alert = UIAlertController(
                            title: "无法打开 Safari",
                            message: "请手动用 Safari 打开 \(url.absoluteString)。",
                            preferredStyle: .alert)
                        alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
                        host.present(alert, animated: true)
                    }
                }
            }
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        let leftEdge = UIScreenEdgePanGestureRecognizer(target: self,
                                                        action: #selector(edgeBack(_:)))
        leftEdge.edges = .left
        view.addGestureRecognizer(leftEdge)
        let rightEdge = UIScreenEdgePanGestureRecognizer(target: self,
                                                         action: #selector(edgeBack(_:)))
        rightEdge.edges = .right
        view.addGestureRecognizer(rightEdge)

        dim.backgroundColor = UIColor.black.withAlphaComponent(0.45)
        dim.addTarget(self, action: #selector(close), for: .touchUpInside)
        view.addSubview(dim)

        sheet.backgroundColor = UIColor(red: 0xf7 / 255, green: 0xf7 / 255, blue: 0xf9 / 255, alpha: 1)
        sheet.layer.cornerRadius = 20
        sheet.layer.masksToBounds = true
        view.addSubview(sheet)

        let handle = UIView()
        handle.tag = 100
        handle.backgroundColor = UIColor(red: 0xdc / 255, green: 0xdc / 255, blue: 0xe1 / 255, alpha: 1)
        handle.layer.cornerRadius = 2.5
        sheet.addSubview(handle)

        let title = UILabel()
        title.tag = 101
        title.text = "修改应用图标"
        title.font = .systemFont(ofSize: 17, weight: .bold)
        title.textColor = UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        sheet.addSubview(title)

        let hint = UILabel()
        hint.tag = 102
        hint.text = "点一个图标，启动器上的图标会换过去（iOS 只换图标，名称仍是「豆图」）"
        hint.font = .systemFont(ofSize: 12.5)
        hint.textColor = UIColor(red: 0x93 / 255, green: 0x92 / 255, blue: 0x92 / 255, alpha: 1)
        hint.numberOfLines = 0
        sheet.addSubview(hint)

        let list = UIView()
        list.tag = 103
        list.backgroundColor = .white
        list.layer.cornerRadius = 16
        list.layer.masksToBounds = true
        sheet.addSubview(list)

        let webClipButton = UIButton(type: .system)
        webClipButton.tag = 104
        webClipButton.setTitle("添加到主屏幕（WebClip）", for: .normal)
        webClipButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .medium)
        webClipButton.setTitleColor(UIColor(red: 0x14 / 255, green: 0x78 / 255,
                                            blue: 0xfc / 255, alpha: 1), for: .normal)
        webClipButton.backgroundColor = .white
        webClipButton.layer.cornerRadius = 14
        webClipButton.addTarget(self, action: #selector(addWebClipTapped), for: .touchUpInside)
        sheet.addSubview(webClipButton)

        let current = UIApplication.shared.alternateIconName
        for (index, option) in Self.options.enumerated() {
            let row = makeRow(option: option, index: index, current: current)
            list.addSubview(row)
            rows.append(row)
            if index > 0 {
                let line = UIView()
                line.tag = 200 + index
                line.backgroundColor = UIColor(red: 0xec / 255, green: 0xec / 255, blue: 0xf1 / 255, alpha: 1)
                list.addSubview(line)
            }
        }
    }

    private func makeRow(option: Option, index: Int, current: String?) -> UIControl {
        let row = UIControl()
        row.tag = 110 + index
        row.backgroundColor = .white
        row.addTarget(self, action: #selector(tapRow(_:)), for: .touchUpInside)

        let icon = UIImageView()
        icon.tag = 1
        icon.image = UIImage(named: option.imageName)
        icon.contentMode = .scaleAspectFit
        icon.isUserInteractionEnabled = false
        row.addSubview(icon)

        let name = UILabel()
        name.tag = 2
        let chosen = option.alternateName == current
        name.text = option.label
        name.font = .systemFont(ofSize: 16, weight: chosen ? .bold : .regular)
        name.textColor = chosen
            ? UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1)
            : UIColor(red: 0x23 / 255, green: 0x25 / 255, blue: 0x2b / 255, alpha: 1)
        name.isUserInteractionEnabled = false
        row.addSubview(name)

        let state = UILabel()
        state.tag = 3
        state.text = chosen ? "当前使用中" : "点击切换"
        state.font = .systemFont(ofSize: 12)
        state.textColor = chosen
            ? UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1)
            : UIColor(red: 0x93 / 255, green: 0x92 / 255, blue: 0x92 / 255, alpha: 1)
        state.isUserInteractionEnabled = false
        row.addSubview(state)

        if chosen {
            let check = UILabel()
            check.text = "✓"
            check.font = .systemFont(ofSize: 20, weight: .semibold)
            check.textColor = UIColor(red: 0x14 / 255, green: 0x78 / 255, blue: 0xfc / 255, alpha: 1)
            check.tag = 4
            check.isUserInteractionEnabled = false
            row.addSubview(check)
        }
        return row
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        dim.frame = view.bounds
        let bottom = view.safeAreaInsets.bottom
        let sheetH = 430 + bottom
        let width = view.bounds.width - 24
        sheet.frame = CGRect(x: 12, y: view.bounds.height - sheetH, width: width, height: sheetH)

        sheet.viewWithTag(100)?.frame = CGRect(x: (width - 38) / 2, y: 10, width: 38, height: 5)
        sheet.viewWithTag(101)?.frame = CGRect(x: 20, y: 30, width: width - 40, height: 24)
        sheet.viewWithTag(102)?.frame = CGRect(x: 20, y: 58, width: width - 40, height: 36)

        let listY: CGFloat = 108
        let listH: CGFloat = CGFloat(Self.options.count) * 62
        sheet.viewWithTag(103)?.frame = CGRect(x: 20, y: listY, width: width - 40, height: listH)
        sheet.viewWithTag(104)?.frame = CGRect(x: 20, y: listY + listH + 8,
                                                width: width - 40, height: 40)
        guard let list = sheet.viewWithTag(103) else { return }
        for (index, row) in rows.enumerated() {
            row.frame = CGRect(x: 0, y: CGFloat(index) * 62, width: list.bounds.width, height: 62)
            row.viewWithTag(1)?.frame = CGRect(x: 14, y: 9, width: 44, height: 44)
            row.viewWithTag(2)?.frame = CGRect(x: 74, y: 11, width: list.bounds.width - 130, height: 22)
            row.viewWithTag(3)?.frame = CGRect(x: 74, y: 33, width: list.bounds.width - 130, height: 18)
            row.viewWithTag(4)?.frame = CGRect(x: list.bounds.width - 38, y: 18, width: 24, height: 24)
            if index > 0 {
                list.viewWithTag(200 + index)?.frame = CGRect(x: 74, y: CGFloat(index) * 62,
                                                              width: list.bounds.width - 88, height: 1)
            }
        }
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    @objc private func edgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let sign: CGFloat = gesture.edges.contains(.right) ? -1 : 1
        guard gesture.translation(in: view).x * sign > 60 else { return }
        close()
    }

    @objc private func addWebClipTapped() {
        AppIconChooser.installWebClip(from: self)
    }

    @objc private func tapRow(_ sender: UIControl) {
        let index = sender.tag - 110
        guard Self.options.indices.contains(index) else { return }
        let option = Self.options[index]
        if option.alternateName == UIApplication.shared.alternateIconName {
            close()
            return
        }
        let alert = UIAlertController(title: nil, message: "是否切换为\(option.label)？",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "确定", style: .default) { [weak self] _ in
            self?.apply(option)
        })
        present(alert, animated: true)
    }

    private func apply(_ option: Option) {
        CrashLog.append("ICON apply name=\(option.alternateName ?? "primary") support=\(UIApplication.shared.supportsAlternateIcons)")
        guard UIApplication.shared.supportsAlternateIcons else {
            let alert = UIAlertController(title: "无法切换",
                                          message: "当前系统/安装方式不支持备选图标。",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
            present(alert, animated: true)
            return
        }
        UIApplication.shared.setAlternateIconName(option.alternateName) { [weak self] error in
            DispatchQueue.main.async {
                guard let self else { return }
                if let error {
                    CrashLog.append("ICON error \((error as NSError).code) \(error.localizedDescription)")
                    // 真机（ad-hoc / ldid 重签）上 IconServices 会返回 OSStatus -54；
                    // 正常签名的安装包才会成功，这里把原因说清楚，别只甩一串错误码。
                    let ns = error as NSError
                    let message = ns.code == -54
                        ? "当前安装方式不允许切换图标（需要正常签名的安装包）。\n\n图标资源已经打进包里，用正式签名重装后即可切换。"
                        : error.localizedDescription
                    let alert = UIAlertController(title: "切换失败",
                                                  message: message,
                                                  preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
                    self.present(alert, animated: true)
                } else {
                    self.dismiss(animated: true) {
                        AppIconChooser.offerWebClip(option)
                    }
                }
            }
        }
    }
}
