import Foundation

/// 三个「整图卡 + 白块补丁」屏的可编辑支持
///
/// `BalanceScreen`（12 个可编辑点）、`HuabeiScreen`（7）、`YuebaoScreen`（9）的**绘制侧
/// 早就支持**了 —— 它们的 `drawEdits` / `drawDynamic` 会读 `s.nums[key]` 与 `s.texts[key]`，
/// 用一块白（或同色渐变）盖住参考图上的数字再重画，与 Android 的做法完全一致。
///
/// 缺的只是**写入侧**：这些点击此前落到 `GrowthStore` 上（写了也没人读），
/// 表现出来就是「点了弹框、改完没反应」。
///
/// 这里的实现不区分「数字键」与「文字键」—— 两个字典都写：
///   · `nums[key]` 放解析出的数值（解析不出就不写）
///   · `texts[key]` 放原始文本
/// 绘制侧各取所需，所以不需要维护一份键名清单。
extension BalanceScreen.State: EditableState {
    func editTitle(_ key: String) -> String { EditableTitles.title(for: key, group: "余额") }
    func editValue(_ key: String) -> String { EditableTitles.current(key, texts: texts, nums: nums) }
    func applyEdit(_ key: String, _ text: String) {
        EditableTitles.store(key, text, texts: &texts, nums: &nums)
    }
}

extension HuabeiScreen.State: EditableState {
    func editTitle(_ key: String) -> String { EditableTitles.title(for: key, group: "花呗") }
    func editValue(_ key: String) -> String { EditableTitles.current(key, texts: texts, nums: nums) }
    func applyEdit(_ key: String, _ text: String) {
        EditableTitles.store(key, text, texts: &texts, nums: &nums)
    }
}

extension YuebaoScreen.State: EditableState {
    func editTitle(_ key: String) -> String { EditableTitles.title(for: key, group: "余额宝") }
    func editValue(_ key: String) -> String { EditableTitles.current(key, texts: texts, nums: nums) }
    func applyEdit(_ key: String, _ text: String) {
        EditableTitles.store(key, text, texts: &texts, nums: &nums)
    }
}

/// 三个屏共用的三件小事
enum EditableTitles {
    /// 键名 → 弹窗标题。键形如 `bal.avail` / `hb.credit` / `yb.total`，取后半段做提示。
    static func title(for key: String, group: String) -> String {
        let tail = key.split(separator: ".").last.map(String.init) ?? key
        return "编辑「" + group + " · " + tail + "」"
    }

    /// 预填值：优先用户改过的文字，其次改过的数字，都没有就留空
    /// （默认值写在各绘制处，State 拿不到 —— 留空让用户直接输入更省事）
    static func current(_ key: String, texts: [String: String], nums: [String: Double]) -> String {
        if let typed = texts[key], !typed.isEmpty { return typed }
        if let value = nums[key] { return format(value) }
        return ""
    }

    /// 写回：两个字典都写（数字能解析才写数值）
    static func store(_ key: String, _ text: String,
                      texts: inout [String: String], nums: inout [String: Double]) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            texts.removeValue(forKey: key)
            nums.removeValue(forKey: key)
            return
        }
        texts[key] = trimmed
        // 只取数字与开头的负号（与 Android 的 number() 一致）
        var digits = ""
        for ch in trimmed {
            if ch.isNumber || ch == "." || (ch == "-" && digits.isEmpty) { digits.append(ch) }
        }
        if let value = Double(digits) {
            nums[key] = value
        } else {
            nums.removeValue(forKey: key)
        }
    }

    /// 数字显示（与各屏 `Self.money` 的取整规则一致：整数不写小数位）
    private static func format(_ value: Double) -> String {
        if value == value.rounded(), abs(value) < 1e15 { return "\(Int(value))" }
        return String(format: "%.2f", value)
    }
}
