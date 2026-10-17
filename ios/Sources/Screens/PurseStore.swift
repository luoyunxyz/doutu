import UIKit

/// 小荷包的本机数据 —— 对应 Android `features/zhi/data/PurseStore.java`。
///
/// Android 用 SharedPreferences 存「名字 / 总金额 / 头像组」，iOS 用 UserDefaults 同义实现。
/// 头像存的是压缩后的 PNG（用户从相册选的那几张）——与 Android 存 JPEG 字节的做法等价。
final class PurseStore {
    static let shared = PurseStore()

    /// 最多 5 个成员头像（Java:36）
    static let maxAvatars = 5
    static let defaultName = "生活费"
    static let defaultTotal = "0.00"

    private let nameKey = "purse.name"
    private let totalKey = "purse.total"
    private let avatarsKey = "purse.avatars"

    private init() {}

    var name: String {
        get {
            let value = UserDefaults.standard.string(forKey: nameKey) ?? ""
            return value.isEmpty ? Self.defaultName : value
        }
        set { UserDefaults.standard.set(newValue, forKey: nameKey) }
    }

    var total: String {
        get {
            let value = UserDefaults.standard.string(forKey: totalKey) ?? ""
            return value.isEmpty ? Self.defaultTotal : value
        }
        set { UserDefaults.standard.set(newValue, forKey: totalKey) }
    }

    /// 头像数量（至少 1 —— 参考图上那一个默认头像）
    var avatarCount: Int {
        max(1, rawAvatars.count)
    }

    /// 第 index 个头像；没上传过就返回 nil（页面会退回切图）
    func avatar(_ index: Int) -> UIImage? {
        let list = rawAvatars
        guard index >= 0 && index < list.count else { return nil }
        return list[index]
    }

    var isFull: Bool { rawAvatars.count >= Self.maxAvatars }

    /// 追加一个头像；满了返回 false（Java:103-111）
    @discardableResult
    func addAvatar(_ image: UIImage) -> Bool {
        var list = rawAvatars
        guard list.count < Self.maxAvatars else { return false }
        list.append(image)
        persist(list)
        return true
    }

    func reset() {
        UserDefaults.standard.removeObject(forKey: nameKey)
        UserDefaults.standard.removeObject(forKey: totalKey)
        UserDefaults.standard.removeObject(forKey: avatarsKey)
    }

    // MARK: - 存储

    private var rawAvatars: [UIImage] {
        guard let data = UserDefaults.standard.array(forKey: avatarsKey) as? [Data] else { return [] }
        return data.compactMap { UIImage(data: $0) }
    }

    private func persist(_ list: [UIImage]) {
        let datas = list.compactMap { image -> Data? in
            // 头像足够小，PNG 无损压缩即可（用户上传的原图另存 320px 版本）
            let side: CGFloat = 320
            let scale = min(1, side / max(image.size.width, image.size.height))
            let target = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let renderer = UIGraphicsImageRenderer(size: target)
            let scaled = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: target)) }
            return scaled.pngData()
        }
        UserDefaults.standard.set(datas, forKey: avatarsKey)
    }
}
