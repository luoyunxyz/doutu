import Foundation

/// 三个用**动态键**的屏的可编辑支持
///
/// 它们的 `hitTest` 返回 `"edit:" + field.key` 这种运行时拼出来的键，所以
/// `check-editable.mjs` 只能看到 `(动态)` ✓ 这一层就是把「键 → 属性」补上。
///
/// 三屏原本都是「emit 了令牌、但没实现 `EditableState`」—— 改值会写进 `GrowthStore`，
/// 界面不变。用户看到的是「点了弹框、改完没反应」。

// MARK: - 芝麻信用（11 个字段，Java 的 State 上是独立属性）

extension ZhimaCreditScreen.State: EditableState {
    func editTitle(_ key: String) -> String {
        switch key {
        case "p.label": return "编辑信用状态"
        case "p.score": return "编辑芝麻分"
        case "p.quota": return "编辑可用额度"
        case "p.discount": return "编辑可享优惠"
        case "p.privilege": return "编辑特权数"
        case "e.label": return "编辑企业信用状态"
        case "e.score": return "编辑企业信用分"
        case "e.company": return "编辑企业名称"
        case "e.go": return "编辑「去涨分」文案"
        case "e.quota": return "编辑「测一测额度」文案"
        case "e.privilege": return "编辑企业特权数"
        default: return "编辑"
        }
    }

    func editValue(_ key: String) -> String {
        switch key {
        case "p.label": return pLabel
        case "p.score": return pScore
        case "p.quota": return pQuota
        case "p.discount": return pDiscount
        case "p.privilege": return pPrivilege
        case "e.label": return eLabel
        case "e.score": return eScore
        case "e.company": return eCompany
        case "e.go": return eGo
        case "e.quota": return eQuota
        case "e.privilege": return ePrivilege
        default: return ""
        }
    }

    func applyEdit(_ key: String, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        switch key {
        case "p.label": pLabel = trimmed
        case "p.score": pScore = trimmed
        case "p.quota": pQuota = trimmed
        case "p.discount": pDiscount = trimmed
        case "p.privilege": pPrivilege = trimmed
        case "e.label": eLabel = trimmed
        case "e.score": eScore = trimmed
        case "e.company": eCompany = trimmed
        case "e.go": eGo = trimmed
        case "e.quota": eQuota = trimmed
        case "e.privilege": ePrivilege = trimmed
        default: break
        }
    }
}

// MARK: - 账单消息页 / 支付成功页（键是运行时拼的，值统一放 `texts` 字典）

extension PayMsgScreen.State: EditableState {
    func editTitle(_ key: String) -> String { "编辑「" + key + "」" }
    func editValue(_ key: String) -> String { texts[key] ?? "" }
    func applyEdit(_ key: String, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { texts.removeValue(forKey: key) } else { texts[key] = trimmed }
    }
}

extension PaySuccessScreen.State: EditableState {
    func editTitle(_ key: String) -> String { "编辑「" + key + "」" }
    func editValue(_ key: String) -> String { texts[key] ?? "" }
    func applyEdit(_ key: String, _ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { texts.removeValue(forKey: key) } else { texts[key] = trimmed }
    }
}
