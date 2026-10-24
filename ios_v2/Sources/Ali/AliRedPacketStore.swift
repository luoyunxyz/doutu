import Foundation

/// 红包两页（编辑 / 详情）共享的可改状态 —— `AliRedPacketStore`（49 行）的移植。
///
/// 发送页改完点「发送」，详情页直接读这里，所以两页上的名字/头像/数字始终一致。
final class AliRedPacketStore {
    static let shared = AliRedPacketStore()

    /// 可切的发送人（Java 的同名常量）
    let senderNames = ["话不太美", "自在枯荣", "小满", "阿宝", "不是山谷"]
    /// 前两个有切图，其余用首字头像
    private let senderAssets: [String?] = ["ali_rp_sender", "ali_rp_avatar", nil, nil, nil]

    var senderIndex = 0
    var blessing = "恭喜发财"
    var amount = "96.00"

    var senderName: String {
        senderNames.indices.contains(senderIndex) ? senderNames[senderIndex] : senderNames[0]
    }

    /// 发送人头像资源名（没有切图就返回 nil，由调用方画首字）
    var senderAsset: String? {
        senderAssets.indices.contains(senderIndex) ? senderAssets[senderIndex] : nil
    }

    /// 金额输入格式化：去掉「￥」/逗号/空格，保留两位小数（对应 Android `format()`）
    func setAmount(_ raw: String) {
        var text = raw.replacingOccurrences(of: "￥", with: "")
            .replacingOccurrences(of: "¥", with: "")
            .replacingOccurrences(of: ",", with: "")
            .replacingOccurrences(of: " ", with: "")
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        if let value = Double(text) {
            amount = String(format: "%.2f", value)
        } else {
            amount = text
        }
    }
}
