import Foundation

/// 开发期把电脑上备份的 UserDefaults 拉回真机。
///
/// TrollStore 跨签名升级会新建数据容器，`apps push` 又因为 App 不在 installd
/// 的安装记录里而失败（InstallationLookupFailed）。这里在首次启动、条款还没同意时，
/// 从调试电脑的 8008 端口拉 `restore.plist`，恢复会员/收款记录等偏好，然后直接进首页。
///
/// 正式发布前应删除或改成只在内网调试包启用。
enum BootstrapRestore {
    private static let urlString = "http://192.168.1.96:8008/restore.plist"

    static func restoreIfNeeded(completion: @escaping () -> Void) {
        let defaults = UserDefaults.standard
        let accepted = defaults.bool(forKey: "terms.accepted")
        let activated = defaults.bool(forKey: "profile.activated")
        guard (!accepted || !activated), let url = URL(string: urlString) else {
            return
        }
        var request = URLRequest(url: url)
        request.timeoutInterval = 4
        URLSession.shared.dataTask(with: request) { data, _, _ in
            guard let data,
                  let object = try? PropertyListSerialization.propertyList(
                    from: data, options: [], format: nil),
                  let plist = object as? [String: Any] else {
                return
            }
            DispatchQueue.main.async {
                let defaults = UserDefaults.standard
                for (key, value) in plist {
                    defaults.set(value, forKey: key)
                }
                defaults.set(true, forKey: "terms.accepted")
                defaults.synchronize()
                completion()
            }
        }.resume()
    }
}
