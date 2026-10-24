import Foundation

/// 支付宝通讯录的本地数据 —— `AliContactStore`（Java 203 行）的移植。
///
/// 两类联系人（与 Android 一致）：
///   1. **同步来的** —— 消息列表里的会话（`AliChatStore`），每次打开都跟着那边走；
///   2. **手动加的** —— 「添加朋友」加进来的，带自己的头像 token。
///
/// 手动加的单独存一条 JSON（`[{"n":昵称,"a":头像}]`），键名沿用 Android 的
/// `doutu_ali` / `contacts2`，方便两边对照。
final class AliContactStore {
    static let shared = AliContactStore()

    struct Contact {
        var name: String
        var avatar: String

        init(name: String, avatar: String = "") {
            self.name = name
            self.avatar = avatar
        }
    }

    private let prefsKey = "doutu_ali"
    private let listKey = "contacts2"
    private var defaults: UserDefaults { UserDefaults.standard }
    private var storageKey: String { prefsKey + "." + listKey }

    // MARK: - 读

    /// 消息列表里的会话就是通讯录里的好友
    func synced() -> [Contact] {
        AliChatStore.shared.list().compactMap { chat in
            let name = chat.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty else { return nil }
            return Contact(name: name, avatar: chat.avatar)
        }
    }

    func manual() -> [Contact] {
        guard let raw = defaults.string(forKey: storageKey), !raw.isEmpty else { return [] }
        guard let data = raw.data(using: .utf8) else { return [] }

        // 新格式：JSON 数组
        if raw.trimmingCharacters(in: .whitespaces).hasPrefix("["),
           let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            return array.compactMap { item in
                let name = (item["n"] as? String ?? "").trimmingCharacters(in: .whitespaces)
                guard !name.isEmpty else { return nil }
                return Contact(name: name, avatar: item["a"] as? String ?? "")
            }
        }

        // 兼容早期「字母|昵称|头像」行文本
        return raw.split(separator: "\n", omittingEmptySubsequences: true).compactMap { line in
            let parts = line.split(separator: "|", maxSplits: 2, omittingEmptySubsequences: false)
            guard parts.count >= 2 else { return nil }
            let name = parts[1].trimmingCharacters(in: .whitespaces)
            guard !name.isEmpty else { return nil }
            return Contact(name: name, avatar: parts.count > 2 ? String(parts[2]) : "")
        }
    }

    /// 同步的 + 手动的，按昵称去重（手动的优先，因为它带头像）
    func all() -> [Contact] {
        var merged: [String: Contact] = [:]
        var order: [String] = []
        for contact in manual() where merged[contact.name] == nil {
            merged[contact.name] = contact
            order.append(contact.name)
        }
        for contact in synced() where merged[contact.name] == nil {
            merged[contact.name] = contact
            order.append(contact.name)
        }
        return order.compactMap { merged[$0] }
    }

    /// 按拼音首字母分组：A-Z 在前，认不出来的归到 #
    func grouped() -> [(letter: String, contacts: [Contact])] {
        var buckets: [String: [Contact]] = [:]
        for contact in all() {
            buckets[Self.letterOf(contact.name), default: []].append(contact)
        }
        // 直接声明成带标签的元组：返回时不依赖「无标签 → 有标签」的隐式转换
        var result: [(letter: String, contacts: [Contact])] = []
        for letter in Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ").map(String.init) {
            if let list = buckets[letter] { result.append((letter: letter, contacts: list)) }
        }
        if let rest = buckets["#"] { result.append((letter: "#", contacts: rest)) }
        return result
    }

    // MARK: - 写

    func add(name: String, avatar: String = "") {
        addAll([Contact(name: name, avatar: avatar)])
    }

    func addAll(_ contacts: [Contact]) {
        guard !contacts.isEmpty else { return }
        var current = manual()
        var seen = Set(current.map { $0.name })
        for contact in contacts {
            let name = contact.name.trimmingCharacters(in: .whitespaces)
            guard !name.isEmpty, seen.insert(name).inserted else { continue }
            current.append(Contact(name: name, avatar: contact.avatar))
        }
        save(current)
    }

    func remove(name: String) {
        guard !name.isEmpty else { return }
        var current = manual()
        guard let index = current.firstIndex(where: { $0.name == name }) else { return }
        current.remove(at: index)
        save(current)
    }

    func setAvatar(name: String, avatar: String) {
        var current = manual()
        if let index = current.firstIndex(where: { $0.name == name }) {
            current[index].avatar = avatar
        } else {
            current.append(Contact(name: name, avatar: avatar))
        }
        save(current)
    }

    private func save(_ contacts: [Contact]) {
        let array: [[String: Any]] = contacts.map { ["n": $0.name, "a": $0.avatar] }
        guard let data = try? JSONSerialization.data(withJSONObject: array),
              let text = String(data: data, encoding: .utf8) else { return }
        defaults.set(text, forKey: storageKey)
    }

    // MARK: - 首字母

    /// 昵称的拼音首字母；认不出来就 `#`。
    ///
    /// Android 用 GB2312 首字节落在哪一段来判定（快，但依赖编码表）；
    /// iOS 直接用系统的汉字转拼音再取首字母，常见字结果一致。
    static func letterOf(_ name: String) -> String {
        guard let first = name.first else { return "#" }
        if first.isASCII, first.isLetter {
            return String(first).uppercased()
        }
        let latin = String(first)
            .applyingTransform(.toLatin, reverse: false)?
            .applyingTransform(.stripDiacritics, reverse: false) ?? ""
        if let letter = latin.first, letter.isLetter {
            return String(letter).uppercased()
        }
        return "#"
    }
}
