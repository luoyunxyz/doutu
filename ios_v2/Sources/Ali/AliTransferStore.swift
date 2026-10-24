import Foundation

/// 支付宝转账两页共享的状态 —— `AliTransferStore`（Java 100 行）的移植。
///
/// 编辑页填写金额/说明/发送人/收支类型，点「完成」后 `stamp()` 落时间，
/// 详情页（账单详情）直接读这里，所以两页数据始终一致。
final class AliTransferStore {
    static let shared = AliTransferStore()

    struct Sender {
        var name: String
        /// 头像资源名（没有就用首字头像）
        var asset: String?
    }

    /// 发送人候选（Java `RANDOM_NAMES`，20 个）
    let randomNames = [
        "怕你久居她心", "漠然一别", "不愿风停", "不得爱而", "往日少女",
        "听夜雪", "夏日浅笑", "话不太美", "眉宇温存", "自在枯荣",
        "晚风入怀", "山川为聘", "星河入梦", "暮色微凉", "南巷旧人",
        "月下清欢", "云间来信", "岁月无声", "雾里看花", "拾光旅人",
    ]

    var sender: Sender?
    var amount = "0.0"
    var note = ""
    var income = true
    var createdAt = ""
    var account = ""
    var status = "交易成功"
    var pageTitle = "账单详情"

    // 详情页（账单详情）用到的其它字段，默认值与 Java 一致
    var category = "其他"
    var tags = ""
    var includedInBill = true
    var serviceTip = "恭喜你有6元转账保障福利待领取！"
    var billTip = "恭喜你有6元转账保障福利待领取！"
    var paymentMethod = "余额"
    var orderNo = ""
    var billTitle = "账单管理"
    var serviceLabel = "服务推荐"
    var actionLabels = [
        "查看往来记录", "AA收款", "转账凭证", "往来流水证明", "申请电子回单",
        "对此订单有疑问", "投诉",
    ]

    /// 订单号：没填过就按时间戳生成（Java `orderNumber()`）
    func orderNumber() -> String {
        if orderNo.isEmpty {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyyMMddHHmmss"
            orderNo = formatter.string(from: Date()) + "4200"
        }
        return orderNo
    }

    /// 没选过发送人就给一个默认的（Java `ensureSender`）
    func ensureSender(fallbackName: String = "怕你久居她心") {
        if sender == nil {
            sender = Sender(name: fallbackName, asset: nil)
        }
        if account.isEmpty, let name = sender?.name {
            account = name + " 181******18"
        }
    }

    /// 详情页显示用：`+96.00` / `-96.00`
    var amountText: String {
        let value = amount.trimmingCharacters(in: .whitespaces).isEmpty ? "0.0" : amount
        return (income ? "+" : "-") + value
    }

    /// 第一次进详情页时落时间（Java `stamp()`）
    @discardableResult
    func stamp() -> String {
        if createdAt.isEmpty {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
            createdAt = formatter.string(from: Date())
        }
        return createdAt
    }

    /// 金额格式化（去掉 ￥/逗号，保留两位）
    func setAmount(_ raw: String) {
        let cleaned = raw
            .replacingOccurrences(of: "￥", with: "")
            .replacingOccurrences(of: "¥", with: "")
            .replacingOccurrences(of: ",", with: "")
            .replacingOccurrences(of: " ", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }
        if let value = Double(cleaned), value.isFinite {
            amount = String(format: "%.2f", value)
        }
    }
}
