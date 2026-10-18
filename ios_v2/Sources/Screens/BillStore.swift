import Foundation

/// 一条账单 —— 对应 Android `features/zhi/data/Bill.java`。
///
/// 所有字段都是页面自己的：列表页显示的名称/分类/字母标/时间/金额，详情页显示的商品说明、
/// 收款方、两个订单号。**全部是随机生成的演示数据**，不对应任何真实订单。
final class Bill {
    var merchant = ""
    var category = ""
    /// 字母标上的字（没选图标时用）
    var mark = ""
    var markColor: UInt32 = 0xff1677ff
    var markInk: UInt32 = 0xffffffff

    /// 页签归类：支出 / 转账 / 退款 / 订单
    var kind = ""
    var time: TimeInterval = 0
    /// 负数是支出，正数是进账
    var amount: Double = 0
    /// 「自动扣款成功」这类状态行
    var status = ""

    var payMethod = ""
    var goods = ""
    var reward = ""
    var payee = ""
    var orderNo = ""
    var merchantOrderNo = ""

    var billCategory = ""
    var tag = ""
    var note = ""
    /// 是否计入统计（「不计入收支」的账单会置 false）—— Java `Bill.counted`
    var counted = true

    var income: Bool { amount >= 0 }
}

/// 一个月的账单 —— 对应 Android `BillStore.Month`。
final class BillMonth {
    let year: Int
    let month: Int
    var bills: [Bill] = []

    init(year: Int, month: Int) {
        self.year = year
        self.month = month
    }

    /// 这个月的进账合计 / 支出合计（页面顶部那两个数字用得上）
    var income: Double { bills.filter { $0.amount > 0 }.reduce(0) { $0 + $1.amount } }
    var expense: Double { bills.filter { $0.amount < 0 }.reduce(0) { $0 - $1.amount } }
}

/// 账单数据 —— `features/zhi/data/BillStore.java` 的 1:1 移植。
///
/// 生成规则完全照抄：当前月填 8 条、往前两个月各 18–24 条；商家从 68 家商户池里随机抽，
/// 金额按**分类区间**随机（奶茶这类单价低的品类区间也低），最后再叠一个 0–0.86 元的零头。
/// 三个特殊商户有额外处理（闲鱼/云猫/花呗），逻辑与 Java 逐行对应。
///
/// 商户池、商品池、金额档由 `_ios/gen-billstore.mjs` 从 Java 源直接生成到 `BillStoreData.swift`，
/// 保证逐字一致。
final class BillStore {
    static let shared = BillStore()

    /// 生成几个月（Java: MONTHS）
    static let monthCount = 3
    /// 每条账单最多 9 条 / 最少 5 条（Java 的 ORDERS_MIN/MAX）
    static let ordersMin = 5
    static let ordersMax = 9

    private(set) var months: [BillMonth] = []
    private var selectedMonth: BillMonth?
    private var selected: Bill?
    /// 用户点过「清空」之后就不再自动生成
    private var cleared = false
    /// 页面徽标「今年累计已省」的数字
    private(set) var savedThisYear: Double = 53

    private let payMethods = [
        "招商银行储蓄卡(4646)", "余额宝", "花呗", "账户余额", "建设银行储蓄卡(8812)",
        "工商银行储蓄卡(3390)", "中国银行信用卡(6271)",
    ]

    private init() {}

    // MARK: - 生成（Java:206-261）

    func generate() {
        guard !cleared else { return }
        months.removeAll()
        selected = nil
        selectedMonth = nil
        let now = Date()
        for back in 0..<Self.monthCount {
            let when = Calendar.current.date(byAdding: .month, value: -back, to: now) ?? now
            months.append(fill(when, current: back == 0))
        }
        savedThisYear = 30 + Double(Int.random(in: 0..<90)) + Double.random(in: 0..<1)
    }

    func clear() {
        months.removeAll()
        selected = nil
        selectedMonth = nil
        cleared = true
    }

    /// 秒变富豪：清掉旧账，塞满大额随机进出账
    func becomeRich() {
        cleared = false
        months.removeAll()
        selected = nil
        selectedMonth = nil
        let now = Date()
        for back in 0..<Self.monthCount {
            let when = Calendar.current.date(byAdding: .month, value: -back, to: now) ?? now
            months.append(fillRich(when, current: back == 0))
        }
        savedThisYear = 2000 + Double(Int.random(in: 0..<8000)) + Double.random(in: 0..<1)
    }

    private func fillRich(_ when: Date, current: Bool) -> BillMonth {
        let comps = Calendar.current.dateComponents([.year, .month], from: when)
        let month = BillMonth(year: comps.year ?? 2026, month: comps.month ?? 1)
        let lastDay = current ? Calendar.current.component(.day, from: Date())
            : daysInMonth(when)
        let count = current ? 16 + Int.random(in: 0..<10) : 26 + Int.random(in: 0..<14)
        for i in 0..<count {
            let bill = order(month, lastDay, current, i)
            let income = Int.random(in: 0..<10) < 6
            let amount = amountFor(bill.category, rich: true)
            bill.amount = Self.round2(income ? amount : -amount)
            if income {
                bill.kind = "退款"
            } else {
                bill.kind = Int.random(in: 0..<10) < 7 ? "支出" : "转账"
            }
            bill.status = ""
            bill.payee = "大额交易 \(1000 + Int.random(in: 0..<9000))"
            month.bills.append(bill)
        }
        month.bills.sort { $0.time > $1.time }
        return month
    }

    private func fill(_ when: Date, current: Bool) -> BillMonth {
        let comps = Calendar.current.dateComponents([.year, .month], from: when)
        let month = BillMonth(year: comps.year ?? 2026, month: comps.month ?? 1)
        let lastDay = current ? Calendar.current.component(.day, from: Date())
            : daysInMonth(when)
        let count = current ? 8 : 18 + Int.random(in: 0..<7)
        for i in 0..<count {
            month.bills.append(order(month, lastDay, current, i))
        }
        month.bills.sort { $0.time > $1.time }
        return month
    }

    private func order(_ month: BillMonth, _ lastDay: Int, _ current: Bool, _ index: Int) -> Bill {
        let bill = Bill()
        let shop = BillStoreData.shops.randomElement() ?? BillStoreData.shops[0]
        bill.merchant = shop[0]
        bill.category = shop[1]
        bill.mark = shop[2]
        bill.markColor = UInt32(shop[3], radix: 16) ?? 0xff1677ff
        bill.markInk = UInt32(shop[4], radix: 16) ?? 0xffffffff

        // 订单落在本月的哪一天：当月最新几单就在昨天及之前
        let step = current ? 1 + Int.random(in: 0..<3) : 1 + Int.random(in: 0..<4)
        let day = max(1, lastDay - index * step)
        var comps = DateComponents()
        comps.year = month.year
        comps.month = month.month
        comps.day = min(day, lastDay)
        comps.hour = 7 + Int.random(in: 0..<16)
        comps.minute = Int.random(in: 0..<60)
        comps.second = Int.random(in: 0..<60)
        var at = Calendar.current.date(from: comps) ?? Date()
        if current && (index == 0 || at.timeIntervalSince1970 * 1000 > Date().timeIntervalSince1970 * 1000) {
            at = Date().addingTimeInterval(-Double(Int.random(in: 0..<1000)) / 1000)
        }
        bill.time = at.timeIntervalSince1970

        let income = Int.random(in: 0..<10) == 0
        let amount = amountFor(bill.category, rich: false)
        bill.amount = income ? amount : -amount
        if income {
            bill.kind = "退款"
        } else {
            let roll = Int.random(in: 0..<10)
            bill.kind = roll < 7 ? "支出" : (roll == 7 ? "转账" : (roll == 8 ? "订单" : "退款"))
        }
        bill.status = ""
        bill.payMethod = payMethods.randomElement() ?? payMethods[0]
        bill.goods = BillStoreData.goods.randomElement() ?? ""
        bill.reward = "已领取\(1 + Int.random(in: 0..<20))积分"
        bill.payee = shop[5]
        bill.orderNo = Self.orderNo(at)
        bill.merchantOrderNo = Self.merchantOrderNo(at)

        // 三个特殊商户（Java:334-351）
        if bill.mark == "闲鱼" {
            bill.status = "自动扣款成功"
            bill.kind = "订单"
            bill.payee = "x***\(Int.random(in: 0..<10))"
            bill.amount = -(0.5 + Double(Int.random(in: 0..<9900)) / 100)
            var merchantId = "T200P"
            while merchantId.count < 24 { merchantId.append(String(Int.random(in: 0..<10))) }
            bill.merchantOrderNo = merchantId
            bill.goods = BillStoreData.goods.randomElement() ?? ""
            bill.merchant = bill.goods
        } else if bill.mark == "云猫" {
            bill.goods = bill.merchantOrderNo
            bill.merchant = bill.goods
            bill.category = "其他"
        } else if bill.mark == "花呗" {
            bill.goods = "花呗自动还款-\(month.year)年\(month.month)月账单"
            bill.merchant = bill.goods
        }

        bill.amount = (bill.income ? 1 : -1)
            * Self.round2(abs(bill.amount) + Double(Int.random(in: 0..<87)) / 100)
        bill.billCategory = bill.category
        return bill
    }

    // MARK: - 金额区间（Java:361-385）

    private func categoryRange(_ category: String) -> (Double, Double, Double) {
        switch category {
        case "餐饮美食": return (6, 120, 10)
        case "交通出行": return (3, 300, 10)
        case "文化娱乐": return (6, 200, 10)
        case "医疗健康": return (15, 500, 8)
        case "生活服务": return (8, 200, 10)
        case "充值缴费": return (20, 300, 10)
        case "生活缴费": return (30, 600, 10)
        case "日用百货": return (15, 400, 20)
        case "商业服务": return (20, 2000, 30)
        case "信用借还": return (50, 5000, 30)
        case "公共服务": return (20, 500, 10)
        default: return (10, 300, 20)
        }
    }

    private func amountFor(_ category: String, rich: Bool) -> Double {
        var (minValue, maxValue, multiplier) = categoryRange(category)
        if rich {
            minValue *= multiplier
            maxValue *= multiplier
        }
        return Self.round2(minValue + Double.random(in: 0..<1) * (maxValue - minValue))
    }

    // MARK: - 订单号（Java:393-409）

    private static func orderNo(_ at: Date) -> String {
        var out = stamp(at, "yyyyMMdd") + "22"
        while out.count < 28 { out.append(String(Int.random(in: 0..<10))) }
        return out
    }

    private static func merchantOrderNo(_ at: Date) -> String {
        let letters = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        var out = ""
        for _ in 0..<3 { out.append(letters.randomElement() ?? "A") }
        out += stamp(at, "MMdd")
        let pool = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        while out.count < 15 { out.append(pool.randomElement() ?? "0") }
        return out
    }

    private static func stamp(_ at: Date, _ format: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = format
        return formatter.string(from: at)
    }

    private static func round2(_ value: Double) -> Double {
        (value * 100).rounded() / 100
    }

    private func daysInMonth(_ date: Date) -> Int {
        Calendar.current.range(of: .day, in: .month, for: date)?.count ?? 30
    }

    // MARK: - 过滤（Java:420-441）

    /// 要画的月份：全部，或只留页签要的那一类
    func months(kind: String) -> [BillMonth] {
        if kind.isEmpty || kind == "全部" { return months }
        var out: [BillMonth] = []
        for month in months {
            let copy = BillMonth(year: month.year, month: month.month)
            for bill in month.bills {
                let matches = kind == "支出" ? bill.amount < 0 : bill.kind == kind
                if matches { copy.bills.append(bill) }
            }
            if !copy.bills.isEmpty { out.append(copy) }
        }
        return out
    }

    /// 按关键字搜（商家 / 商品 / 收款方 / 两个订单号）
    static func matches(_ bill: Bill, query: String) -> Bool {
        guard !query.isEmpty else { return true }
        let hay = "\(bill.merchant) \(bill.goods) \(bill.payee) \(bill.orderNo) \(bill.merchantOrderNo)"
        return hay.lowercased().contains(query.lowercased())
    }
}
