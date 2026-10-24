import UIKit

/// 「工具」Tab 的目录数据 —— 对应 Android `assets/tools_page.json`。
///
/// Android 是运行时从 assets 读这个 JSON 再排版，iOS 保持同样做法（数据随包，
/// 改条目不用动代码）。文件由 `_ios/sync-tools-assets.mjs` 从 Android 原样同步过来。
///
/// 结构（2026-10-24 同步时）：4 个分区 / 43 个入口
///
/// ```text
/// 小微（17）· 阿宝（16）· 小Q（6）· 视频（4）
/// ```
enum ToolsCatalog {
    struct Cell {
        let name: String
        let icon: String
        let action: String
    }

    struct Section {
        let title: String
        let icon: String
        let action: String
        let items: [Cell]
    }

    static let sections: [Section] = load()

    private static func load() -> [Section] {        guard let url = Bundle.main.url(forResource: "tools_page", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let object = try? JSONSerialization.jsonObject(with: data),
              let raw = object as? [[String: Any]]
        else {
            return []
        }
        return raw.compactMap { entry in
            guard let title = entry["title"] as? String else { return nil }
            let items = (entry["items"] as? [[String: Any]] ?? []).compactMap { item -> Cell? in
                guard let name = item["name"] as? String else { return nil }
                return Cell(name: name,
                            icon: item["icon"] as? String ?? "",
                            action: item["action"] as? String ?? "")
            }
            return Section(title: title,
                           icon: entry["icon"] as? String ?? "",
                           action: entry["action"] as? String ?? "",
                           items: items)
        }
    }
}

/// 「工具」页图标的资源入口。
///
/// Android 是 `tools_icons/<key>.png`；同步进 iOS 资源目录时统一加了 `tool_` 前缀
/// （见 `_ios/sync-tools-assets.mjs`）。单独包一层是为了让资源名拼接不写进
/// `UIImage(named:)` 的字面量里 —— 静态检查会把它当成「引用了不存在的资源」。
enum ToolsAssets {
    static func icon(_ key: String) -> UIImage? {
        let asset = "tool_" + key
        return UIImage(named: asset)
    }
}
