import UIKit

/// 理财界面主题 —— `features/zhi/data/LicaiVersion.java` 的 1:1 移植。
///
/// 五档主题，普通版是蓝色，其余四档都是「财富黑卡」皮肤（只是配色与那几张背景图不同）：
///
/// | 档 | 名字 | 前景色 | 强调色 | 背景 / 铭牌 / 卡片 / 卡套 |
/// |---|---|---|---|---|
/// | 0 普通版 | 普通版 | #202124 | #1677ff | 无（纯绘制） |
/// | 1 成长版 | 成长版 | #38271c | #b88a62 | growth_v1 / growth / growth / growth |
/// | 2 V1 | V1 | #38271c | #c89f84 | growth_v1 / v1 / v1 / v1 |
/// | 3 V2 | V2 | #152860 | #9ea9c0 | v2 / v2 / v2 / v2 |
/// | 4 V3 | V3 | #0f0f11 | #c8a36f | v3 / v3 / v3 / v3 |
///
/// `blackCard(v)` 是「除普通版以外都算黑卡」——「我的」页、消息页、支首页都靠它切换配色与图标。
final class LicaiVersion {
    static let normal = 0
    static let growth = 1
    static let v1 = 2
    static let v2 = 3
    static let v3 = 4

    static let names = ["普通版", "成长版", "V1", "V2", "V3"]
    static let tips = [
        "普通蓝色主题",
        "获得方式：曾经有活动可免费领取",
        "获得方式：近30天日均资产30万即可达成",
        "获得方式：近30天日均资产50万即可达成",
        "获得方式：近30天日均资产100万即可达成",
    ]
    static let badges: [String?] = [nil, "成长版", "V1", "V2", "V3"]

    /// 一套主题（对应 Java 的 `Theme`）
    struct Theme {
        let value: Int
        let name: String
        let tip: String
        let foreground: UInt32
        let accent: UInt32
        /// 背景切图（空串表示这一档没有图，纯绘制）
        let backgroundRes: String
        let badgeRes: String
        let cardRes: String
        let holderRes: String
    }

    private static let themes: [Theme] = [
        Theme(value: 0, name: names[0], tip: tips[0], foreground: 0xff202124, accent: 0xff1677ff,
              backgroundRes: "", badgeRes: "", cardRes: "", holderRes: ""),
        Theme(value: 1, name: names[1], tip: tips[1], foreground: 0xff38271c, accent: 0xffb88a62,
              backgroundRes: "licai_bg_growth_v1", badgeRes: "licai_badge_growth",
              cardRes: "licai_card_growth", holderRes: "licai_holder_growth"),
        Theme(value: 2, name: names[2], tip: tips[2], foreground: 0xff38271c, accent: 0xffc89f84,
              backgroundRes: "licai_bg_growth_v1", badgeRes: "licai_badge_v1",
              cardRes: "licai_card_v1", holderRes: "licai_holder_v1"),
        Theme(value: 3, name: names[3], tip: tips[3], foreground: 0xff152860, accent: 0xff9ea9c0,
              backgroundRes: "licai_bg_v2", badgeRes: "licai_badge_v2",
              cardRes: "licai_card_v2", holderRes: "licai_holder_v2"),
        Theme(value: 4, name: names[4], tip: tips[4], foreground: 0xff0f0f11, accent: 0xffc8a36f,
              backgroundRes: "licai_bg_v3", badgeRes: "licai_badge_v3",
              cardRes: "licai_card_v3", holderRes: "licai_holder_v3"),
    ]

    /// 与 Android 一样按账号隔离存档（`PREFS + Accounts.suffix()`）。
    ///
    /// Android 的 `Accounts.suffix()` 是当前账号 id 的短串；iOS 侧没有那份账号表，
    /// 这里用「手机号后四位」当后缀 —— 换账号时存档自然分开，未登录时用 `default`。
    private static var storageKey: String {
        let phone = ProfileStore.shared.phone
        let suffix = phone.count >= 4 ? String(phone.suffix(4)) : "default"
        return "licai.version." + suffix
    }

    private static let legacyKey = "licai.version"

    /// 当前这一档（未设置过就是普通版）
    static func current() -> Int {
        let stored = UserDefaults.standard.integer(forKey: storageKey)
        if UserDefaults.standard.object(forKey: storageKey) == nil {
            // 兼容早期只存了一个整数的那版
            let old = UserDefaults.standard.integer(forKey: legacyKey)
            return drawn(old) ? old : normal
        }
        return drawn(stored) ? stored : normal
    }

    static func current(_ value: Int) {
        guard drawn(value) else { return }
        UserDefaults.standard.set(value, forKey: storageKey)
    }

    /// 除普通版以外都算财富黑卡
    static func blackCard(_ value: Int) -> Bool { value != normal }

    /// 这一档有没有自己的页面（五档都有）
    static func drawn(_ value: Int) -> Bool { value >= normal && value <= v3 }

    static func name(_ value: Int) -> String { drawn(value) ? names[value] : names[normal] }
    static func tip(_ value: Int) -> String { drawn(value) ? tips[value] : tips[normal] }
    static func badge(_ value: Int) -> String? { drawn(value) ? badges[value] : nil }

    static func theme() -> Theme { theme(current()) }
    static func theme(_ value: Int) -> Theme { themes[drawn(value) ? value : normal] }

    /// 换账号时把这一份丢掉
    static func reset() {
        UserDefaults.standard.removeObject(forKey: storageKey)
    }
}
