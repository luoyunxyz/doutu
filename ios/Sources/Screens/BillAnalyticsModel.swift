import Foundation

/// 「收支分析」的数据推导 —— `features/zhi/ui/BillAnalyticsScreen.java` 前半部分（Java:120-335）的移植。
///
/// 这一页有三个页签（周 / 月 / 年），每个页签的分类、金额、笔数、环形图分片**全部自动算出来**：
///
///   1. `syncLedger` 从**账单页的真实数据**刷新一批默认值（近五月柱状、十二月、当月分类、整年分类）
///   2. 用户手动改过的值存在 `edits` / `cnt` / `texts` 里，**优先于**算出来的值
///   3. 取值的优先级：手动改过 → 账单算出来的 → 参考图写死的默认值
///
/// 环形图的角度也是算的：从 −90° 开始，每片之间留 0.8° 间隙，最小 2.5°。
final class BillAnalyticsModel {
    /// 三个页签
    enum Tab: Int { case week = 0, month = 1, year = 2 }

    /// 环形图的一片
    struct Slice {
        var name = ""
        var amount: Double = 0
        var count = 0
        var color: UInt32 = 0xff1777ff
        var a0: CGFloat = 0
        var sweep: CGFloat = 0
        var pct: CGFloat = 0
    }

    // 七档蓝（Java:40）
    static let catPalette: [UInt32] = [
        0xff1777ff, 0xff5aa0ff, 0xff8dbcff, 0xffb3d3ff, 0xffcfe3ff, 0xffe0eeff, 0xffedf5ff,
    ]

    // 用户改过的值
    var edits: [String: Double] = [:]
    var cnts: [String: Int] = [:]
    var texts: [String: String] = [:]

    // 账单算出来的值（每次绘制重算）
    private var base: [String: Double] = [:]
    private var baseCnt: [String: Int] = [:]

    let now: Date

    init(now: Date = Date()) {
        self.now = now
    }

    // MARK: - 取值三优先级（Java:127-143）

    func value(_ key: String, _ fallback: Double) -> Double {
        if let typed = edits[key] { return typed }
        if let computed = base[key] { return computed }
        return fallback
    }

    func count(_ key: String, _ fallback: Int) -> Int {
        if let typed = cnts[key] { return typed }
        if let computed = baseCnt[key] { return computed }
        return fallback
    }

    func text(_ key: String, _ fallback: String) -> String {
        texts[key] ?? fallback
    }

    // MARK: - 与账单同步（Java:150-223）

    func syncLedger(monthOffset: Int = 0, yearOffset: Int = 0) {
        base.removeAll()
        baseCnt.removeAll()
        let all = BillStore.shared.months(kind: "全部")
        guard !all.isEmpty else { return }
        let calendar = Calendar.current

        // 近五个月柱状（支出 / 收入）
        for i in 0..<5 {
            guard let mo = calendar.date(byAdding: .month, value: (i - 4) + monthOffset, to: now)
            else { continue }
            let comps = calendar.dateComponents([.year, .month], from: mo)
            guard let ledger = Self.ledger(all, year: comps.year ?? 0, month: comps.month ?? 0)
            else { continue }
            base["m.bar.\(i)"] = ledger.expense
            base["mi.b.\(i)"] = ledger.income
        }

        // 十二个月（年度）
        let year = (calendar.dateComponents([.year], from: now).year ?? 2026) + yearOffset
        for i in 0..<12 {
            guard let ledger = Self.ledger(all, year: year, month: i + 1) else { continue }
            base["y.m.\(i)"] = ledger.expense
            base["yi.m.\(i)"] = ledger.income
        }

        // 当月分类（月度饼图）
        if let mc = calendar.date(byAdding: .month, value: monthOffset, to: now) {
            let comps = calendar.dateComponents([.year, .month], from: mc)
            if let cm = Self.ledger(all, year: comps.year ?? 0, month: comps.month ?? 0) {
                let monthGroups = [["充值缴费", "生活缴费"], ["日用百货"], []]
                let mv = Self.bucket(cm, income: false, groups: monthGroups)
                if mv.reduce(0, +) > 0 {
                    base["m.c.0"] = mv[0]
                    base["m.c.1"] = mv[1]
                    base["m.c.2"] = mv[2]
                }
                let mn = Self.bucketCount(cm, income: false, groups: monthGroups)
                baseCnt["m.n.0"] = mn[0]
                baseCnt["m.n.1"] = mn[1]
                baseCnt["m.n.2"] = mn[2]
                baseCnt["mi.n"] = Self.count(cm, income: true)
            }
        }

        // 整年分类
        let yearGroups = [["餐饮美食"], ["日用百货"], ["信用借还", "转账红包"], ["交通出行"],
                          ["文化娱乐", "文化休闲"], ["美容美发"], []]
        var yv = [Double](repeating: 0, count: yearGroups.count)
        var yn = [Int](repeating: 0, count: yearGroups.count)
        for month in all where month.year == year {
            let part = Self.bucket(month, income: false, groups: yearGroups)
            let partN = Self.bucketCount(month, income: false, groups: yearGroups)
            for g in 0..<yv.count {
                yv[g] += part[g]
                yn[g] += partN[g]
            }
        }
        if yv.reduce(0, +) > 0 {
            for g in 0..<yv.count { base["y.c.\(g)"] = yv[g] }
            for g in 0..<yn.count { baseCnt["y.n.\(g)"] = yn[g] }
        }

        // 餐饮美食下的六个小类，按餐饮美食总额等比缩放
        let tileDefault = [2013.67, 644.05, 243.00, 20.40, 8.00, 4.00]
        let refFood = tileDefault.reduce(0, +)
        let k = refFood > 0 ? yv[0] / refFood : 0
        for (i, v) in tileDefault.enumerated() { base["y.t.\(i)"] = v * k }
    }

    static func ledger(_ all: [BillMonth], year: Int, month: Int) -> BillMonth? {
        all.first { $0.year == year && $0.month == month }
    }

    /// 把某月某一方向的金额按分组归类；没命中的落到最后一组（「其他」）
    static func bucket(_ month: BillMonth?, income: Bool, groups: [[String]]) -> [Double] {
        var out = [Double](repeating: 0, count: groups.count)
        guard let month else { return out }
        for bill in month.bills where bill.counted && bill.income == income {
            out[groupOf(bill.category, groups)] += abs(bill.amount)
        }
        return out
    }

    static func bucketCount(_ month: BillMonth?, income: Bool, groups: [[String]]) -> [Int] {
        var out = [Int](repeating: 0, count: groups.count)
        guard let month else { return out }
        for bill in month.bills where bill.counted && bill.income == income {
            out[groupOf(bill.category, groups)] += 1
        }
        return out
    }

    static func groupOf(_ category: String?, _ groups: [[String]]) -> Int {
        let cat = category ?? ""
        for g in 0..<(groups.count - 1) {
            if groups[g].contains(cat) { return g }
        }
        return groups.count - 1
    }

    static func count(_ month: BillMonth, income: Bool) -> Int {
        month.bills.filter { $0.counted && $0.income == income }.count
    }

    // MARK: - 三个页签的分类（Java:266-298）

    func weekCategories() -> [(String, Double)] {
        [("其他", value("w.c.0", 20.00))]
    }

    func monthCategories() -> [(String, Double)] {
        [("充值缴费", value("m.c.0", 50.00)),
         ("日用百货", value("m.c.1", 41.19)),
         ("其他", value("m.c.2", 20.00))]
    }

    func yearCategories() -> [(String, Double)] {
        [("餐饮美食", value("y.c.0", 2933.12)),
         ("日用百货", value("y.c.1", 1866.58)),
         ("转账红包", value("y.c.2", 1401.90)),
         ("交通出行", value("y.c.3", 1063.50)),
         ("文化休闲", value("y.c.4", 1033.40)),
         ("美容美发", value("y.c.5", 403.50)),
         ("其他", value("y.c.6", 1139.67))]
    }

    func categories(tab: Tab) -> [(String, Double)] {
        switch tab {
        case .week: return weekCategories()
        case .month: return monthCategories()
        case .year: return yearCategories()
        }
    }

    // MARK: - 环形图（Java:300-335）

    func slices(tab: Tab) -> [Slice] {
        let items = categories(tab: tab)
        let total = items.reduce(0) { $0 + $1.1 }
        var out: [Slice] = []
        for (i, item) in items.enumerated() {
            var slice = Slice()
            slice.name = item.0
            slice.amount = item.1
            let nKey = countKey(tab: tab, index: i)
            slice.count = count(nKey, defaultCount(tab: tab, index: i))
            slice.color = Self.catPalette[i % Self.catPalette.count]
            slice.pct = total > 0 ? CGFloat(item.1 / total * 100) : 0
            out.append(slice)
        }
        // 从 −90° 起，片间留 0.8°，最小 2.5°
        let gap: CGFloat = 0.8
        var angle: CGFloat = -90
        for i in out.indices {
            out[i].a0 = angle
            out[i].sweep = max(2.5, out[i].pct * 3.6 - gap)
            angle += out[i].sweep + gap
        }
        return out
    }

    func countKey(tab: Tab, index: Int) -> String {
        let prefix = tab == .week ? "w" : (tab == .month ? "m" : "y")
        return "\(prefix).n.\(index)"
    }

    func defaultCount(tab: Tab, index: Int) -> Int {
        switch tab {
        case .week: return 1
        case .month: return index == 0 ? 1 : (index == 1 ? 5 : 1)
        case .year:
            let defaults = [133, 100, 9, 0, 0, 0, 31]
            return defaults[min(index, defaults.count - 1)]
        }
    }

    /// 合计
    func total(tab: Tab) -> Double {
        categories(tab: tab).reduce(0) { $0 + $1.1 }
    }

    /// 笔数合计
    func totalCount(tab: Tab) -> Int {
        var n = 0
        for (i, _) in categories(tab: tab).enumerated() {
            n += count(countKey(tab: tab, index: i), defaultCount(tab: tab, index: i))
        }
        return n
    }
}
