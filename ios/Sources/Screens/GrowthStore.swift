import Foundation

/// 成长版页面上的数字 —— `features/zhi/data/GrowthStore.java` 的 1:1 移植。
///
/// 特点：**大部分数字是从「总资产」和「年化利率」算出来的**，不是各写各的：
///
/// ```
/// 余额宝   = 总资产 × 0.4264811246938218
/// 基金     = 总资产 × 0.5309281857101644
/// 储蓄|养老 = 总资产 − 上面两项
/// 余额宝昨日收益 = 总资产 × 0.4264… × 3.0866940698524315e-06 × 利率 / 3.91
/// 基金昨日收益   = 总资产 × 0.5309… × 0.002014053158372705    × 利率 / 3.91
/// ```
///
/// 用户在编辑框里改过的字段进 `overrides`，一旦改「总资产」或「利率」就整表清空
/// （因为其余数字都要重新算）。**这一页不落盘** —— Java 注释写明了它只活在本次运行里。
final class GrowthStore {
    static let shared = GrowthStore()

    private static let rateRef: Double = 3.91
    private static let shareReserve: Double = 0.4264811246938218
    private static let shareFund: Double = 0.5309281857101644
    private static let reserveDayRatio: Double = 3.0866940698524315e-06
    private static let fundDayRatio: Double = 0.002014053158372705
    private static let defaultHolder = "ZhangXinYu"

    /// 可编辑字段的键（顺序与 Java 一致，编辑面板按它遍历）
    static let keys = [
        "total", "rate", "yesterday", "family", "holder",
        "reserve", "reserveProfit", "fund", "fundProfit", "savings",
        "huabei", "idx2", "move2", "idxp2",
        "fundSelfPct", "fundSelfTag", "rankPct0", "rankPct1", "windMove", "allocPct",
        "bidx0", "bidx1", "bidx2", "bpct0", "bpct1", "bpct2",
        "flow0", "flow1", "flow2", "windName", "windPct", "prod0", "prod1", "prod2",
    ]

    static let titles = [
        "编辑总资产", "编辑年化利率(%)", "编辑昨日收益", "编辑家庭保障份数", "编辑持卡人签名",
        "编辑余额宝金额", "编辑余额宝昨日收益", "编辑基金金额", "编辑基金昨日收益",
        "编辑储蓄|养老", "编辑花呗可用额度",
        "编辑纳斯达克点数", "编辑纳斯达克涨跌", "编辑纳斯达克涨跌幅",
        "编辑基金自选涨幅", "编辑基金自选标签", "编辑基金排行第1名涨幅", "编辑基金排行第2名涨幅",
        "编辑看行情涨跌", "编辑配资产涨幅",
        "编辑上证指数", "编辑深证成指", "编辑创业板指",
        "编辑上证涨跌幅", "编辑深证涨跌幅", "编辑创业板涨跌幅",
        "编辑资金流入第1名", "编辑资金流入第2名", "编辑资金流入第3名",
        "编辑风口名称", "编辑风口涨幅",
        "编辑第1个产品涨幅", "编辑第2个产品涨幅", "编辑第3个产品涨幅",
    ]

    private var total: Double = 258276.87
    private var rate: Double = GrowthStore.rateRef
    private var totalText = "258,276.87"
    private var family = "1"
    private var holder = GrowthStore.defaultHolder
    private var huabei = "可用1,577.93"
    private var idx2 = "21020.02"
    private var move2 = "+127.33"
    private var idxp2 = "+0.61%"
    private var fundSelfPct = "+2.63%"
    private var fundSelfTag = "连涨4个月"
    private var rankPct0 = "+65.04%"
    private var rankPct1 = "+64.39%"
    private var windMove = "+0.6%"
    private var allocPct = "+2.78%"
    private var board = ["3797.24", "12384.87", "2963.82"]
    private var boardPct = ["−0.43%", "−2.03%", "−2.61%"]
    private var flow = ["+14.4亿元", "+12.4亿元", "+10.7亿元"]
    private var windName = "创新药"
    private var windPct = "−1.74%"
    private var prodPct = ["+4.49%", "+6.56%", "+4.97%"]
    private var overrides: [String: String] = [:]

    /// 眼睛默认是**闭上**的 —— 参考图就是闭着拍的（Java:91）
    var hidden = true

    private init() {}

    /// 换账号时把这一页记住的数字抹回去
    func reset() {
        total = 258276.87
        rate = Self.rateRef
        totalText = "258,276.87"
        family = "1"
        holder = Self.defaultHolder
        huabei = "可用1,577.93"
        idx2 = "21020.02"
        move2 = "+127.33"
        idxp2 = "+0.61%"
        fundSelfPct = "+2.63%"
        fundSelfTag = "连涨4个月"
        rankPct0 = "+65.04%"
        rankPct1 = "+64.39%"
        windMove = "+0.6%"
        allocPct = "+2.78%"
        board = ["3797.24", "12384.87", "2963.82"]
        boardPct = ["−0.43%", "−2.03%", "−2.61%"]
        flow = ["+14.4亿元", "+12.4亿元", "+10.7亿元"]
        windName = "创新药"
        windPct = "−1.74%"
        prodPct = ["+4.49%", "+6.56%", "+4.97%"]
        overrides.removeAll()
        hidden = true
    }

    // MARK: - 取值（Java:127-167）

    func value(_ key: String) -> String {
        if let typed = overrides[key] { return typed }
        switch key {
        case "total": return totalText
        case "rate": return Self.trim(rate)
        case "yesterday": return Self.signed(reserveDay() + fundDay(), decimals: 2)
        case "family": return family
        case "holder": return holder
        case "reserve": return Self.money(total * Self.shareReserve)
        case "fund": return Self.money(total * Self.shareFund)
        case "savings": return Self.money(total - total * Self.shareReserve - total * Self.shareFund)
        case "huabei": return huabei
        case "reserveProfit": return Self.signed(reserveDay(), decimals: 2)
        case "fundProfit": return Self.signed(fundDay(), decimals: 2)
        case "idx2": return idx2
        case "move2": return move2
        case "idxp2": return idxp2
        case "fundSelfPct": return fundSelfPct
        case "fundSelfTag": return fundSelfTag
        case "rankPct0": return rankPct0
        case "rankPct1": return rankPct1
        case "windMove": return windMove
        case "allocPct": return allocPct
        case "bidx0": return board[0]
        case "bidx1": return board[1]
        case "bidx2": return board[2]
        case "bpct0": return boardPct[0]
        case "bpct1": return boardPct[1]
        case "bpct2": return boardPct[2]
        case "flow0": return flow[0]
        case "flow1": return flow[1]
        case "flow2": return flow[2]
        case "windName": return windName
        case "windPct": return windPct
        case "prod0": return prodPct[0]
        case "prod1": return prodPct[1]
        case "prod2": return prodPct[2]
        default: return ""
        }
    }

    // MARK: - 改值（Java:169-252）

    func setValue(_ key: String, _ text: String) {
        let typed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch key {
        case "total":
            guard !typed.isEmpty else { return }
            totalText = typed
            let parsed = Self.number(typed)
            if parsed > 0 {
                total = parsed
                overrides.removeAll()
            }
        case "rate":
            guard !typed.isEmpty else { return }
            let percent = Self.number(typed)
            if percent > 0 {
                rate = percent
                overrides.removeAll()
            }
        case "family":
            family = typed.isEmpty ? "1" : typed
        case "holder":
            holder = typed.isEmpty ? Self.defaultHolder : typed
        case "huabei":
            if !typed.isEmpty { huabei = typed }
        case "idx2":
            if !typed.isEmpty { idx2 = typed }
        case "move2":
            if !typed.isEmpty { move2 = typed }
        case "idxp2":
            if !typed.isEmpty { idxp2 = typed }
        case "fundSelfPct":
            if !typed.isEmpty { fundSelfPct = typed }
        case "fundSelfTag":
            if !typed.isEmpty { fundSelfTag = typed }
        case "rankPct0":
            if !typed.isEmpty { rankPct0 = typed }
        case "rankPct1":
            if !typed.isEmpty { rankPct1 = typed }
        case "windMove":
            if !typed.isEmpty { windMove = typed }
        case "allocPct":
            if !typed.isEmpty { allocPct = typed }
        case "bidx0", "bidx1", "bidx2":
            if !typed.isEmpty, let index = Self.arrayIndex(key) { board[index] = typed }
        case "bpct0", "bpct1", "bpct2":
            if !typed.isEmpty, let index = Self.arrayIndex(key) { boardPct[index] = typed }
        case "flow0", "flow1", "flow2":
            if !typed.isEmpty, let index = Self.arrayIndex(key) { flow[index] = typed }
        case "windName":
            if !typed.isEmpty { windName = typed }
        case "windPct":
            if !typed.isEmpty { windPct = typed }
        case "prod0", "prod1", "prod2":
            if !typed.isEmpty, let index = Self.arrayIndex(key) { prodPct[index] = typed }
        default:
            if typed.isEmpty { overrides.removeValue(forKey: key) }
            else { overrides[key] = typed }
        }
    }

    /// `bidx2` → 2（Java 用的是 `key.charAt(4) - '0'`）
    private static func arrayIndex(_ key: String) -> Int? {
        guard key.count == 5, let last = key.last, last.isNumber else { return nil }
        return last.wholeNumberValue
    }

    static func titleOf(_ key: String) -> String {
        if let index = keys.firstIndex(of: key), index < titles.count { return titles[index] }
        return key
    }

    // MARK: - 派生数字

    /// 余额宝今天赚了多少（按参考图的比例，随利率走）
    private func reserveDay() -> Double {
        total * Self.shareReserve * Self.reserveDayRatio * rate / Self.rateRef
    }

    private func fundDay() -> Double {
        total * Self.shareFund * Self.fundDayRatio * rate / Self.rateRef
    }

    // MARK: - 数字格式化

    /// 千分位两位小数（Java 的 `%,.2f`）
    static func money(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US")
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.usesGroupingSeparator = true
        return formatter.string(from: NSNumber(value: value)) ?? String(format: "%.2f", value)
    }

    /// 正数带 `+`，负数用真正的减号 `−`（不是连字符）
    static func signed(_ value: Double, decimals: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US")
        formatter.minimumFractionDigits = decimals
        formatter.maximumFractionDigits = decimals
        formatter.usesGroupingSeparator = true
        let text = formatter.string(from: NSNumber(value: abs(value))) ?? String(format: "%.2f", abs(value))
        return value < 0 ? "−" + text : "+" + text
    }

    /// 整数就不写小数位（3.91 → "3.91"，4.0 → "4"）
    static func trim(_ value: Double) -> String {
        if value == value.rounded() { return String(Int64(value)) }
        return String(format: "%.2f", value)
    }

    /// 只取数字与（开头的）负号，这样 `"258,276.87"` 与 `"3.91%"` 都能解析
    static func number(_ text: String) -> Double {
        var digits = ""
        for ch in text {
            if ch.isNumber || ch == "." || (ch == "-" && digits.isEmpty) {
                digits.append(ch)
            }
        }
        return Double(digits) ?? 0
    }
}
