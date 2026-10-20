import UIKit

/// 生成带自定义名称/图标的 WebClip 描述文件。
/// iOS 不允许 App 静默安装描述文件，最终仍需要用户在「设置」里确认一次；
/// 这里只负责把 profile 生成好并通过本地服务器交给 Safari。
enum WebClipBuilder {
    static func profile(label: String, icon: UIImage?) -> Data? {
        let uuid = UUID().uuidString
        var clip: [String: Any] = [
            "PayloadType": "com.apple.webClip.managed",
            "PayloadIdentifier": "com.luoyun.doutu.webclip.\(uuid)",
            "PayloadUUID": uuid,
            "PayloadVersion": 1,
            "Label": label,
            "URL": "doutu://",
            "FullScreen": true,
            "IsRemovable": true,
        ]
        if let data = icon?.pngData() {
            clip["Icon"] = data
        }
        let root: [String: Any] = [
            "PayloadContent": [clip],
            "PayloadDisplayName": label,
            "PayloadIdentifier": "com.luoyun.doutu.webclip",
            "PayloadOrganization": "豆图",
            "PayloadType": "Configuration",
            "PayloadUUID": UUID().uuidString,
            "PayloadVersion": 1,
        ]
        return try? PropertyListSerialization.data(fromPropertyList: root,
                                                   format: .xml,
                                                   options: 0)
    }
}
