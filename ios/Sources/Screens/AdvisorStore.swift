import UIKit

/// 理财顾问名片 —— `features/zhi/data/AdvisorStore.java` 的移植。
///
/// 只有两样东西：**一张头像**（用户可换，最大 512）与**一个名字**（默认「赵军」，不能为空）。
/// Android 把头像写成文件（`advisor_avatar.png`）、名字写进 SharedPreferences；
/// iOS 两个都放 UserDefaults（头像转 PNG 存 Data）。
final class AdvisorStore {
    static let shared = AdvisorStore()

    private static let defaultName = "赵军"
    private static let avatarMax: CGFloat = 512
    /// 头像是**按账号隔离**的（Android 的文件名带账号后缀），这里用手机号后四位
    private var nameKey: String {
        "advisor.name." + (ProfileStore.shared.phone.isEmpty ? "default" : String(ProfileStore.shared.phone.suffix(4)))
    }
    private var avatarKey: String { nameKey.replacingOccurrences(of: "advisor.name.", with: "advisor.avatar.") }

    private var nameValue: String = AdvisorStore.defaultName
    private var avatarImage: UIImage?
    private var loaded = false

    private init() {}

    private func load() {
        guard !loaded else { return }
        loaded = true
        if let stored = UserDefaults.standard.string(forKey: nameKey), !stored.isEmpty {
            nameValue = stored
        }
        if let data = UserDefaults.standard.data(forKey: avatarKey),
           let image = UIImage(data: data) {
            avatarImage = image
        }
    }

    /// 用户换过的头像；没换过就返回 nil（页面用参考图裁出来的那张兜底）
    var avatar: UIImage? {
        load()
        return avatarImage
    }

    func setAvatar(_ image: UIImage?) {
        load()
        guard let image else {
            avatarImage = nil
            UserDefaults.standard.removeObject(forKey: avatarKey)
            return
        }
        let scaled = Self.scaled(image, max: Self.avatarMax)
        avatarImage = scaled
        UserDefaults.standard.set(scaled.pngData(), forKey: avatarKey)
    }

    var name: String {
        load()
        return nameValue
    }

    func setName(_ value: String?) {
        load()
        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        nameValue = trimmed.isEmpty ? Self.defaultName : trimmed
        UserDefaults.standard.set(nameValue, forKey: nameKey)
    }

    func reset() {
        UserDefaults.standard.removeObject(forKey: nameKey)
        UserDefaults.standard.removeObject(forKey: avatarKey)
        nameValue = Self.defaultName
        avatarImage = nil
        loaded = false
    }

    /// 长边缩到 max 以内（Android 的 `AVATAR_MAX = 512`）
    private static func scaled(_ image: UIImage, max: CGFloat) -> UIImage {
        let w = image.size.width * image.scale
        let h = image.size.height * image.scale
        let longest = Swift.max(w, h)
        guard longest > max, longest > 0 else { return image }
        let factor = max / longest
        let size = CGSize(width: w * factor, height: h * factor)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
