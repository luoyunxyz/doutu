import Foundation

/// 支付宝单聊的本地列表 —— `AliChatStore`（Java 141 行）的移植。
///
/// 键名沿用 Android 的 SharedPreferences 名字（`ali_chat_store` / `chats`），方便两边对照；
/// 数据存在 `UserDefaults.standard` 里。排序规则照抄：置顶在前，其余越新建越靠前。
final class AliChatStore {
    static let shared = AliChatStore()

    struct Chat {
        var name: String
        var avatar: String
        var last: String
        var time: String
        var pinned: Bool
    }

    private let prefsKey = "ali_chat_store"
    private let listKey = "chats"

    private var defaults: UserDefaults { UserDefaults.standard }

    /// 列表：置顶在前，普通会话按创建时间倒序（Java:41-63）
    func list() -> [Chat] {
        var pinned: [Chat] = []
        var normal: [Chat] = []
        guard let raw = defaults.string(forKey: prefsKey + "." + listKey),
              let data = raw.data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }

        for item in array {
            let name = item["name"] as? String ?? ""
            if name.isEmpty { continue }
            let chat = Chat(name: name,
                            avatar: item["avatar"] as? String ?? "",
                            last: item["last"] as? String ?? "开始聊天吧",
                            time: item["time"] as? String ?? "",
                            pinned: item["pinned"] as? Bool ?? false)
            if chat.pinned { pinned.append(chat) } else { normal.append(chat) }
        }
        normal.reverse()
        return pinned + normal
    }

    /// 新建或更新一条会话（Java:65-84）
    func upsert(name: String, avatar: String?, last: String?) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var chats = list()
        if let index = chats.firstIndex(where: { $0.name == trimmed }) {
            if let avatar, !avatar.isEmpty { chats[index].avatar = avatar }
            if let last, !last.isEmpty { chats[index].last = last }
            chats[index].time = Self.now()
            let chat = chats.remove(at: index)
            chats.insert(chat, at: 0)
        } else {
            chats.insert(Chat(name: trimmed, avatar: avatar ?? "",
                              last: last ?? "开始聊天吧", time: Self.now(), pinned: false),
                         at: 0)
        }
        save(chats)
    }

    /// 只更新最后一条与时间（Java:86-97）
    func touch(name: String, last: String?) {
        var chats = list()
        if let index = chats.firstIndex(where: { $0.name == name }) {
            if let last, !last.isEmpty { chats[index].last = last }
            chats[index].time = Self.now()
            save(chats)
            return
        }
        upsert(name: name, avatar: nil, last: last)
    }

    func setPinned(name: String, pinned: Bool) {
        var chats = list()
        guard let index = chats.firstIndex(where: { $0.name == name }) else { return }
        chats[index].pinned = pinned
        save(chats)
    }

    func delete(name: String) {
        var chats = list()
        guard let index = chats.firstIndex(where: { $0.name == name }) else { return }
        chats.remove(at: index)
        save(chats)
    }

    private func save(_ chats: [Chat]) {
        let array: [[String: Any]] = chats.map { chat in
            ["name": chat.name, "avatar": chat.avatar, "last": chat.last,
             "time": chat.time, "pinned": chat.pinned]
        }
        guard let data = try? JSONSerialization.data(withJSONObject: array),
              let text = String(data: data, encoding: .utf8) else { return }
        defaults.set(text, forKey: prefsKey + "." + listKey)
    }

    private static func now() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: Date())
    }
}
