import UIKit

/// 提现数据 —— `features/zhi/data/WithdrawStore.java` 的移植。
///
/// 管三件事：**银行卡列表**（含"用户自有 logo"与"不可提现"两种特例）、
/// **两张金额**（免费额度 quota / 可用余额 avail）、以及选中哪张卡。
/// Android 用 SharedPreferences 存 JSON，iOS 用 UserDefaults 存同样的字段。
final class WithdrawStore {
    static let shared = WithdrawStore()

    /// 用户自带的 logo（不走银行图标表）
    static let bespoke = "bespoke"
    /// 首次打开时的三条文案（照参考图）
    static let arrivalSoon = "2小时内到账"
    static let arrivalBlocked = "此卡不可用于提现"
    /// 免费额度初始值
    static let quotaSeed: Double = 28404.47

    /// 一张卡（对应 Java 的 `Card`）
    final class Card {
        var id: Int64 = 0
        var bank = "not"
        var name = ""
        var tail = ""
        var arrival = WithdrawStore.arrivalSoon
        var usable = true
        /// 用户自带的 logo 图片（`bank == bespoke` 时用）
        var bespokeImage: UIImage?

        var title: String { "\(name) (\(tail))" }
    }

    /// 一个可选的银行 logo（对应 Java 的 `Bank`）
    struct Bank {
        let key: String
        let label: String
        let resource: String
    }

    /// 13 家银行 + 空白卡（Java:83-98）
    static let banks: [Bank] = [
        Bank(key: "gongshangyh", label: "工商银行", resource: "bank_gongshangyh"),
        Bank(key: "nongyeyh", label: "农业银行", resource: "bank_nongyeyh"),
        Bank(key: "zhongguoyh", label: "中国银行", resource: "bank_zhongguoyh"),
        Bank(key: "jiansheyh", label: "建设银行", resource: "bank_jiansheyh"),
        Bank(key: "jiaotongyh", label: "交通银行", resource: "bank_jiaotongyh"),
        Bank(key: "zhaoshangyh", label: "招商银行", resource: "bank_zhaoshangyh"),
        Bank(key: "pufayh", label: "浦发银行", resource: "bank_pufayh"),
        Bank(key: "xingyeyh", label: "兴业银行", resource: "bank_xingyeyh"),
        Bank(key: "guangfayh", label: "广发银行", resource: "bank_guangfayh"),
        Bank(key: "minshengyh", label: "民生银行", resource: "bank_minshengyh"),
        Bank(key: "youchuyh", label: "邮储银行", resource: "bank_youchuyh"),
        Bank(key: "zhongxinyh", label: "中信银行", resource: "bank_zhongxinyh"),
        Bank(key: "wangshang", label: "网商银行", resource: "wd_bank_wangshang"),
        Bank(key: "not", label: "银行卡", resource: "bank_not"),
    ]

    private let cardsKey = "withdraw.cards"
    private let selectedKey = "withdraw.selected"
    private let quotaKey = "withdraw.quota"
    private let availKey = "withdraw.avail"

    private var cards: [Card] = []
    private var selectedID: Int64 = 0
    private var quotaValue: Double = WithdrawStore.quotaSeed
    private var availValue: Double = 0
    private var loaded = false

    private init() {}

    // MARK: - 读写

    func load() {
        guard !loaded else { return }
        loaded = true
        guard let stored = UserDefaults.standard.array(forKey: cardsKey) as? [[String: Any]] else {
            seed()
            persist()
            return
        }
        cards = stored.compactMap { dict in
            guard let id = (dict["id"] as? NSNumber)?.int64Value else { return nil }
            let card = Card()
            card.id = id
            card.bank = dict["bank"] as? String ?? "not"
            card.name = dict["name"] as? String ?? "储蓄卡"
            card.tail = dict["tail"] as? String ?? "0000"
            card.arrival = dict["arrival"] as? String ?? Self.arrivalSoon
            card.usable = dict["usable"] as? Bool ?? true
            return card
        }
        selectedID = (UserDefaults.standard.object(forKey: selectedKey) as? NSNumber)?.int64Value
            ?? (cards.first?.id ?? 0)
        quotaValue = UserDefaults.standard.object(forKey: quotaKey) as? Double ?? Self.quotaSeed
        availValue = UserDefaults.standard.object(forKey: availKey) as? Double ?? 0
        if cards.isEmpty { seed() }
    }

    /// 首次进入的初始列表：招商(4646) 选中、招商(5021)、网商(6628) 不可用
    private func seed() {
        cards = [
            make(1, "zhaoshangyh", "招商银行储蓄卡", "4646", true),
            make(2, "zhaoshangyh", "招商银行储蓄卡", "5021", true),
            make(3, "wangshang", "网商银行储蓄卡", "6628", false),
        ]
        selectedID = 1
        quotaValue = Self.quotaSeed
        availValue = 0
    }

    private func make(_ id: Int64, _ bank: String, _ name: String, _ tail: String,
                      _ usable: Bool) -> Card {
        let card = Card()
        card.id = id
        card.bank = bank
        card.name = name
        card.tail = tail
        card.arrival = usable ? Self.arrivalSoon : Self.arrivalBlocked
        card.usable = usable
        return card
    }

    private func persist() {
        let payload: [[String: Any]] = cards.map {
            ["id": $0.id, "bank": $0.bank, "name": $0.name, "tail": $0.tail,
             "arrival": $0.arrival, "usable": $0.usable]
        }
        UserDefaults.standard.set(payload, forKey: cardsKey)
        UserDefaults.standard.set(selectedID, forKey: selectedKey)
        UserDefaults.standard.set(quotaValue, forKey: quotaKey)
        UserDefaults.standard.set(availValue, forKey: availKey)
    }

    var allCards: [Card] {
        load()
        return cards
    }

    func selected() -> Card? {
        load()
        return cards.first { $0.id == selectedID } ?? cards.first
    }

    /// 选中一张卡；不可用的卡选不上（Java:147-157）
    func select(_ id: Int64) {
        load()
        guard let card = cards.first(where: { $0.id == id }), card.usable else { return }
        selectedID = id
        persist()
    }

    func byId(_ id: Int64) -> Card? {
        load()
        return cards.first { $0.id == id }
    }

    /// 新卡插在「第一张不可用的卡」之前（Java:170-174）
    private func addIndex() -> Int {
        load()
        if let index = cards.firstIndex(where: { !$0.usable }) { return index }
        return cards.count
    }

    @discardableResult
    func add(bank: String, name: String, tail: String) -> Card {
        load()
        let card = Card()
        card.id = Int64(Date().timeIntervalSince1970 * 1_000_000)
        card.bank = bank
        card.name = name
        card.tail = tail
        card.arrival = Self.arrivalSoon
        card.usable = true
        cards.insert(card, at: max(0, min(cards.count, addIndex())))
        selectedID = card.id
        persist()
        return card
    }

    func rename(_ card: Card?, _ name: String) {
        guard let card else { return }
        let value = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }
        card.name = value
        persist()
    }

    /// 换 logo：银行 key 或用户自带图片（Java:201-207）
    func logo(_ card: Card?, bank: String, bespoke: UIImage? = nil) {
        guard let card else { return }
        card.bank = bank
        card.bespokeImage = bank == Self.bespoke ? bespoke : nil
        persist()
    }

    /// 银行 key → 显示名
    static func bankName(_ key: String) -> String {
        banks.first { $0.key == key }?.label ?? "储蓄卡"
    }

    /// 银行 key → 卡名（招商银行 → 招商银行储蓄卡；自备图没有名字；空白卡就是「银行卡」）
    static func bankCardName(_ key: String) -> String? {
        if key == bespoke { return nil }
        if key == "not" { return "银行卡" }
        return bankName(key) + "储蓄卡"
    }

    /// 这个名字是否还跟着图标走（Java:227-234）
    static func autoName(_ name: String) -> Bool {
        banks.contains { bankCardName($0.key) == name }
    }

    /// 一个没被占用的四位尾号
    func newTail() -> String {
        load()
        for _ in 0..<40 {
            let tail = String(format: "%04d", 1000 + Int.random(in: 0..<9000))
            if !cards.contains(where: { $0.tail == tail }) { return tail }
        }
        return String(format: "%04d", 1000 + Int.random(in: 0..<9000))
    }

    // MARK: - 两个金额

    var quota: Double {
        load()
        return quotaValue
    }

    func setQuota(_ value: Double) {
        load()
        quotaValue = max(0, ReceiptStore.round2(value))
        persist()
    }

    var avail: Double {
        load()
        return availValue
    }

    func setAvail(_ value: Double) {
        load()
        availValue = max(0, ReceiptStore.round2(value))
        persist()
    }

    // MARK: - logo

    /// 卡片的 logo 图片（银行图标或用户自备图）
    func icon(_ card: Card?) -> UIImage? {
        load()
        guard let card else { return nil }
        if card.bank == Self.bespoke { return card.bespokeImage }
        guard let bank = Self.banks.first(where: { $0.key == card.bank }) else { return nil }
        return UIImage(named: bank.resource)
    }

    func reset() {
        cards.removeAll()
        loaded = false
        selectedID = 0
        quotaValue = Self.quotaSeed
        availValue = 0
        UserDefaults.standard.removeObject(forKey: cardsKey)
        UserDefaults.standard.removeObject(forKey: selectedKey)
        UserDefaults.standard.removeObject(forKey: quotaKey)
        UserDefaults.standard.removeObject(forKey: availKey)
    }
}
