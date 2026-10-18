import UIKit

/// 用户在「我的」页展示的资料（新版 Core 的最小实现）。
final class ProfileStore {
    static let shared = ProfileStore()
    private let defaults = UserDefaults.standard

    var name: String {
        get { defaults.string(forKey: "profile.name") ?? "未登录" }
        set { defaults.set(newValue, forKey: "profile.name") }
    }

    var phone: String {
        get { defaults.string(forKey: "profile.phone") ?? "" }
        set { defaults.set(newValue, forKey: "profile.phone") }
    }

    var avatar: UIImage? {
        get {
            guard let data = defaults.data(forKey: "profile.avatar") else { return nil }
            return UIImage(data: data)
        }
        set {
            if let newValue, let data = newValue.pngData() {
                defaults.set(data, forKey: "profile.avatar")
            } else {
                defaults.removeObject(forKey: "profile.avatar")
            }
        }
    }
}

/// 新版导航占位：微信 WebView 的少数原生跳转通过它转发。
/// 后续阶段会替换成真正的 Router。
final class Router {
    static weak var shared: Router?
    func handle(_ token: String, at point: CGPoint) {}
}
