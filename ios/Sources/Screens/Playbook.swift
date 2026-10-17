import Foundation

/// 娱乐页（更多玩法）的数据模型 —— 对应 `MainActivity` 里的
/// `FeatureRow` / `Section` / `EntertainmentRow`，以及 `buildEntertainmentLayout()` 的排版计算。
///
/// 内容**由服务端下发**（`Session.playbook()` → `sections[]`），客户端只认它实现了的 key；
/// 认不出来的行直接不显示，避免「点了没反应」。后台下线一个玩法也不用发版。
enum Playbook {
    // MARK: - 数据

    /// 一行玩法
    struct FeatureRow {
        /// 稳定标识（服务端下发），决定点进去哪个页面
        let key: String
        let title: String
        let subtitle: String
        /// 渲染成开关的行（如「撩妹加V」）
        let switchRow: Bool
        var enabled: Bool

        init(key: String, title: String, subtitle: String, switchRow: Bool = false, enabled: Bool = false) {
            self.key = key
            self.title = title
            self.subtitle = subtitle
            self.switchRow = switchRow
            self.enabled = enabled
        }
    }

    /// 一个分区（一张白卡片）
    struct Section {
        let title: String
        /// 强调色键：blue / purple / orange
        let accent: String
        var rows: [FeatureRow]
    }

    /// 一整行摆在哪 —— 画的时候和点的时候读的是同一份，
    /// 所以改版式不会出现「看到的位置」和「点得到的位置」对不上。
    struct RowBox {
        let row: FeatureRow
        let sectionIndex: Int
        /// 画哪个字形（认 key，不是第几行）
        let glyph: Int
        let accent: UIColor
        let tint: UIColor
        let top: CGFloat
        let bottom: CGFloat
        /// 是不是所在卡片第一行（第一行头顶不画分隔线）
        let firstInSection: Bool
    }

    // MARK: - 版式常量（Java:618-640）

    static let heroTop: CGFloat = 112
    static let heroBottom: CGFloat = 372
    static let sectionHeaderH: CGFloat = 96
    static let sectionGap: CGFloat = 44
    static let rowH: CGFloat = 210
    static let cardLeft: CGFloat = 32
    static let cardRight: CGFloat = 1048
    static let cardRadius: CGFloat = 30
    static let contentBottom: CGFloat = 2145

    static let pageBg = Draw.color(0xfff5f6fa)
    static let cardEdge = Draw.color(0xffeef0f6)
    static let cardHairline = Draw.color(0xfff1f2f7)
    static let ink = Draw.color(0xff1d1f26)
    static let subInk = Draw.color(0xff8b90a0)
    static let brand = Draw.color(0xff4a7bf7)
    static let brand2 = Draw.color(0xff8b5cf6)

    static let sectionAccent: [UIColor] = [
        Draw.color(0xff4a7bf7), Draw.color(0xff7c5cf6), Draw.color(0xffff7a45),
    ]
    static let sectionTint: [UIColor] = [
        Draw.color(0xffeaf1ff), Draw.color(0xffefeaff), Draw.color(0xfffff0e8),
    ]

    /// 客户端真正实现了的玩法 key。
    static let knownHandlers: Set<String> = [
        "parse", "order", "call", "shutdown", "sound", "tease", "avatar", "balance",
    ]

    /// 解析不到服务端配置时的兜底（Java:688-690、725-727）
    static var defaultSections: [Section] {
        [Section(title: "实用功能", accent: "blue", rows: [
            FeatureRow(key: "parse", title: "短视频一键去水印",
                       subtitle: "支持抖音、快手、小红书、B站等100+平台"),
        ])]
    }

    // MARK: - 服务端配置解析（Java:685-729）

    /// 从 `sections` 数组解析；认不出的行丢弃。传空/非法时退回兜底。
    static func sections(from json: [[String: Any]]?) -> [Section] {
        guard let json, !json.isEmpty else { return defaultSections }
        var result: [Section] = []
        for item in json {
            let title = (item["title"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if title.isEmpty { continue }
            var rows: [FeatureRow] = []
            for raw in (item["rows"] as? [[String: Any]] ?? []) {
                // enabled=false 的行直接不出现：后台下线一个玩法不用发版
                if let enabled = raw["enabled"] as? Bool, !enabled { continue }
                let key = (raw["key"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let rowTitle = (raw["title"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                if key.isEmpty || rowTitle.isEmpty { continue }
                if !knownHandlers.contains(key) { continue }
                rows.append(FeatureRow(
                    key: key,
                    title: rowTitle,
                    subtitle: raw["subtitle"] as? String ?? "",
                    switchRow: raw["switch"] as? Bool ?? false
                ))
            }
            if !rows.isEmpty {
                result.append(Section(title: title, accent: item["accent"] as? String ?? "", rows: rows))
            }
        }
        return result.isEmpty ? defaultSections : result
    }

    // MARK: - 排版（Java:731-749）

    /// 把分区摊成一行行的格子，同时算出可滚动高度
    static func layout(_ sections: [Section]) -> (rows: [RowBox], bottom: CGFloat) {
        var rows: [RowBox] = []
        var y = heroBottom + 40
        for (index, section) in sections.enumerated() {
            let accent = accentFor(section, index: index)
            let tint = tintFor(accent)
            y += sectionHeaderH
            for (offset, row) in section.rows.enumerated() {
                rows.append(RowBox(row: row, sectionIndex: index, glyph: glyphFor(row.key),
                                   accent: accent, tint: tint, top: y, bottom: y + rowH,
                                   firstInSection: offset == 0))
                y += rowH
            }
            y += sectionGap
        }
        return (rows, y - sectionGap)
    }

    /// 分区强调色：优先用服务端下发的 key，认不出来按序号循环（Java:755-761）
    static func accentFor(_ section: Section, index: Int) -> UIColor {
        switch section.accent {
        case "blue": return sectionAccent[0]
        case "purple": return sectionAccent[1]
        case "orange": return sectionAccent[2]
        default: return sectionAccent[abs(index) % sectionAccent.count]
        }
    }

    /// 图标底纹：强调色的极淡版本（Java:764-769）
    static func tintFor(_ accent: UIColor) -> UIColor {
        for (i, candidate) in sectionAccent.enumerated() where candidate == accent {
            return sectionTint[i]
        }
        return sectionTint[0]
    }

    /// 这一行画哪个字形（Java:772-783）
    static func glyphFor(_ key: String) -> Int {
        switch key {
        case "order": return 1
        case "avatar": return 2
        case "call": return 3
        case "shutdown": return 4
        case "sound": return 5
        case "balance": return 6
        case "tease": return 7
        default: return 0   // parse：短视频一键去水印
        }
    }
}
