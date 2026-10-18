import Foundation

/// 一条收款记录 —— 对应 Android `features/zhi/data/Receipt.java`。
///
/// 收款列表、收款详情、提现页都用它；金额全是**随机生成的演示数据**。
final class Receipt {
    var id: Int64 = 0
    var time: TimeInterval = 0
    var amount: Double = 0
    /// 实收
    var actual: Double = 0
    /// 计入额度
    var quota: Double = 0
    var payer = "**峻"
    var prize = "10积分未领取已过期"
    var note = "收钱码收款"

    /// 顾客画像（详情页那几列，按需从 `ReceiptStore.stats` 填）
    var monthSpend: Double?
    var monthCount: Int?
    var totalSpend: Double?
    var totalCount: Int?
    var avgPrice: Double?

    /// 列表上的金额写法：`¥20.00`
    var amountText: String { "¥" + ReceiptStore.money(amount) }
    /// 到账写法（与 Android `incomeText()` 同义）
    var incomeText: String { "¥" + ReceiptStore.money(actual) }
}

/// 收款记录数据 —— `features/zhi/data/ReceiptStore.java` 的 1:1 移植。
///
/// 记录是**按批次生成**的：调用方给出若干「区间 + 条数」（`Spec`），每条金额在区间内均匀取、
/// 时间散落在指定年月的**任意一天任意时刻**，生成后按时间倒序排。
/// 与账单不同，收款记录会持久化（iOS 用 UserDefaults，Android 用 SharedPreferences）。
final class ReceiptStore {
    static let shared = ReceiptStore()

    /// 一次最多生成 100 条（Java:42）
    static let maxBatch = 100
    /// 头像预取上限（Java:49）
    static let primeMax = 60

    /// 五档金额区间与名字（Java:55-57）
    static let tierNames = ["小额", "正常", "较大", "大额", "老板"]
    static let tierRanges: [(Double, Double)] = [
        (0.1, 50), (51, 300), (301, 1000), (1001, 50000), (50001, 100000),
    ]

    private let payers = [
        "**峻", "**华", "**明", "**丽", "**涛", "**静", "**芳", "**磊", "**伟", "**敏",
        "**强", "**霞", "**鹏", "**婷", "**军", "**洋",
    ]
    private let prizes = [
        "10积分未领取已过期", "5积分已领取", "20积分未领取已过期", "3积分已领取",
    ]

    private let storageKey = "receipt.records"
    private(set) var records: [Receipt] = []
    private var loaded = false

    private init() {}

    /// 一条生成指令：区间 + 条数（Java:153-165）
    struct Spec {
        let name: String
        let min: Double
        let max: Double
        let count: Int
    }

    // MARK: - 读取与生成

    func load() {
        guard !loaded else { return }
        loaded = true
        guard let stored = UserDefaults.standard.array(forKey: storageKey) as? [[String: Any]] else {
            return
        }
        records = stored.compactMap { dict -> Receipt? in
            guard let id = dict["id"] as? Int64 ?? (dict["id"] as? Int).map(Int64.init),
                  let time = dict["time"] as? Double else { return nil }
            let receipt = Receipt()
            receipt.id = id
            receipt.time = time
            receipt.amount = dict["amount"] as? Double ?? 0
            receipt.actual = dict["actual"] as? Double ?? 0
            receipt.quota = dict["quota"] as? Double ?? 0
            receipt.payer = dict["payer"] as? String ?? "**峻"
            receipt.prize = dict["prize"] as? String ?? ""
            receipt.note = dict["note"] as? String ?? "收钱码收款"
            // 详情页「修改本月消费 / 累计消费 / 平均单价」会写这三项覆盖值
            receipt.monthSpend = dict["monthSpend"] as? Double
            receipt.monthCount = dict["monthCount"] as? Int
            receipt.totalSpend = dict["totalSpend"] as? Double
            receipt.totalCount = dict["totalCount"] as? Int
            receipt.avgPrice = dict["avgPrice"] as? Double
            return receipt
        }
        sort()
    }

    func byId(_ id: Int64) -> Receipt? {
        load()
        return records.first { $0.id == id }
    }

    /// 按指令生成并落盘，返回实际生成条数（Java:173-193）
    @discardableResult
    func add(_ specs: [Spec], year: Int, month: Int) -> Int {
        load()
        var made = 0
        for spec in specs {
            for _ in 0..<spec.count where made < Self.maxBatch {
                let receipt = Receipt()
                receipt.amount = Self.round2(spec.min
                    + Double.random(in: 0..<1) * max(0.01, spec.max - spec.min))
                receipt.actual = receipt.amount
                receipt.quota = receipt.amount
                receipt.payer = payers.randomElement() ?? payers[0]
                receipt.prize = prizes.randomElement() ?? prizes[0]
                receipt.time = Self.timeIn(year, month)
                receipt.id = Int64(receipt.time * 1000) + Int64(made)
                records.append(receipt)
                made += 1
            }
        }
        sort()
        persist()
        return made
    }

    func reset() {
        records.removeAll()
        persist()
    }

    /// 收款详情页改完金额/奖励/付款人后落盘（对应 Android `commit()`）
    func save() {
        sort()
        persist()
    }

    /// 某个时间点：落在指定年月的**任意一天任意时刻**（Java:200-210）
    private static func timeIn(_ year: Int, _ month: Int) -> TimeInterval {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = 1
        let firstDay = Calendar.current.date(from: comps) ?? Date()
        let days = Calendar.current.range(of: .day, in: .month, for: firstDay)?.count ?? 30
        comps.day = 1 + Int.random(in: 0..<days)
        comps.hour = Int.random(in: 0..<24)
        comps.minute = Int.random(in: 0..<60)
        comps.second = Int.random(in: 0..<60)
        return (Calendar.current.date(from: comps) ?? Date()).timeIntervalSince1970
    }

    private func sort() {
        records.sort { $0.time > $1.time }
    }

    private func persist() {
        let payload: [[String: Any]] = records.map { receipt in
            var dict: [String: Any] = [
                "id": receipt.id, "time": receipt.time, "amount": receipt.amount,
                "actual": receipt.actual, "quota": receipt.quota,
                "payer": receipt.payer, "prize": receipt.prize, "note": receipt.note,
            ]
            if let value = receipt.monthSpend { dict["monthSpend"] = value }
            if let value = receipt.monthCount { dict["monthCount"] = value }
            if let value = receipt.totalSpend { dict["totalSpend"] = value }
            if let value = receipt.totalCount { dict["totalCount"] = value }
            if let value = receipt.avgPrice { dict["avgPrice"] = value }
            return dict
        }
        UserDefaults.standard.set(payload, forKey: storageKey)
    }

    // MARK: - 顾客画像（Java:215-244）

    struct Stats {
        var monthSpend: Double = 0
        var monthCount = 0
        var totalSpend: Double = 0
        var totalCount = 0

        var avgPrice: Double { totalCount == 0 ? 0 : totalSpend / Double(totalCount) }
    }

    /// 某个付款人本月 / 累计的消费合计
    func stats(_ payer: String) -> Stats {
        load()
        var result = Stats()
        let now = Calendar.current.dateComponents([.year, .month], from: Date())
        for receipt in records where receipt.payer == payer {
            result.totalSpend += receipt.amount
            result.totalCount += 1
            let comps = Calendar.current.dateComponents([.year, .month],
                                                        from: Date(timeIntervalSince1970: receipt.time))
            if comps.year == now.year && comps.month == now.month {
                result.monthSpend += receipt.amount
                result.monthCount += 1
            }
        }
        return result
    }

    // MARK: - 两个页面共用的格式（Java:248-289）

    /// 两位小数（Java 用的是 `%.2f`，不是千分位）
    static func money(_ value: Double) -> String {
        String(format: "%.2f", value)
    }

    /// 列表上的金额写法：`¥20.00`
    static func yuan(_ value: Double) -> String { "¥" + money(value) }

    static func round2(_ value: Double) -> Double { (value * 100).rounded() / 100 }

    static func startOfDay(_ time: TimeInterval) -> TimeInterval {
        Calendar.current.startOfDay(for: Date(timeIntervalSince1970: time)).timeIntervalSince1970
    }

    static func sameDay(_ a: TimeInterval, _ b: TimeInterval) -> Bool {
        startOfDay(a) == startOfDay(b)
    }

    /// `M月d日`（列表里每张卡上面的日期标题）
    static func dayLabel(_ time: TimeInterval) -> String {
        let comps = Calendar.current.dateComponents([.month, .day],
                                                    from: Date(timeIntervalSince1970: time))
        return "\(comps.month ?? 1)月\(comps.day ?? 1)日"
    }

    /// `HH:mm:ss`（付款人名字下面那行）
    static func clock(_ time: TimeInterval) -> String {
        let comps = Calendar.current.dateComponents([.hour, .minute, .second],
                                                    from: Date(timeIntervalSince1970: time))
        return String(format: "%02d:%02d:%02d", comps.hour ?? 0, comps.minute ?? 0, comps.second ?? 0)
    }
}
