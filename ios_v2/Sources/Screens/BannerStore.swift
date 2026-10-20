import Foundation

/// 首页横幅文案（对应 Android `account/Session.java` 的 Banner / HintFrame 部分）。
///
/// 真实文案由后台「公告管理」下发（`/api/v1/config/public` → `banner`），
/// 这里先实现**兜底默认**与轮播节奏，接上配置接口后只需把 `apply(remote:)` 填好。
///
/// 默认问候语按小时分档 —— 与 Java 里那份逐字一致（`defaultGreeting()`）。
final class BannerStore {
    static let shared = BannerStore()

    /// 主文案（人物旁边那两行）
    private(set) var title: String = ""
    /// 下排长条的文字池（轮播）
    private(set) var hints: [String] = []
    /// 每条停留秒数（后台可配，最小 3 秒）
    private(set) var rotateSec: Int = 8
    /// 横幅总开关
    private(set) var enabled = true

    /// 轮播动画时长（与 Android 的 HINT_ANIM_MS 一致）
    private let animMs: Double = 420

    /// 分享好友用的链接与提示语（对应 Android `Session.shareLink()/shareTip()`）
    /// 后台 `/config` 可下发，先给与 Android 完全相同的兜底值。
    private static let shareLinkFallback = "https://tdapi.luoyun.xyz/"
    private static let shareTipFallback = "链接已复制，粘贴给好友即可~"
    private(set) var shareLink: String = BannerStore.shareLinkFallback
    private(set) var shareTip: String = BannerStore.shareTipFallback

    private init() {
        title = Self.defaultGreeting()
        hints = []
    }

    /// 当前该显示的主文案：优先服务端下发的第一条公告标题，否则用时间问候
    var currentTitle: String {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? Self.defaultGreeting() : trimmed
    }

    // MARK: - 轮播

    /// 一条长条文案 + 切换动画进度（0~1，仅在滚动的那 420ms 内非 0）
    struct HintFrame {
        let current: String
        let next: String?
        let progress: Double

        /// Android `HintFrame.rolling()`：进度严格在 0~1 之间才算「正在滚」。
        /// 之前写成 `next != nil`，进度被钳到 1 时仍然按 30fps 重绘，白耗电。
        var rolling: Bool { next != nil && progress > 0 && progress < 1 }
    }

    /// 取当前帧（与 Android `Session.hintFrame()` 同一套时间算法）
    func hintFrame(now: Date = Date()) -> HintFrame {
        let pool = hintList()
        guard !pool.isEmpty else { return HintFrame(current: "", next: nil, progress: 0) }
        let interval = max(3, rotateSec)
        let intervalMs = Double(interval) * 1000
        let nowMs = now.timeIntervalSince1970 * 1000
        let idx = Int((nowMs / intervalMs).truncatingRemainder(dividingBy: Double(pool.count)))
        let phase = nowMs.truncatingRemainder(dividingBy: intervalMs)
        if phase >= intervalMs - animMs, pool.count > 1 {
            let next = pool[(idx + 1) % pool.count]
            // 注意这里必须减去窗口起点 `intervalMs - animMs`。
            // 之前是 `phase / animMs`（phase 已经是区间末尾，比如 7600/420 = 18），
            // 一进窗口就钳成 1：文字瞬间跳到下一条，420ms 的滚动动画从来没真正播过。
            let progress = min(1, max(0, (phase - (intervalMs - animMs)) / animMs))
            return HintFrame(current: pool[idx], next: next, progress: progress)
        }
        return HintFrame(current: pool[idx], next: nil, progress: 0)
    }

    /// 距离下一次「滚动窗口」开始还有多少秒。
    ///
    /// 宿主的重绘心跳只有 0.25s，420ms 的滚动窗口很容易被整段错过；
    /// 用它把下一帧对齐到窗口起点，动画才能从第一帧开始播。
    func secondsUntilRoll(now: Date = Date()) -> TimeInterval {
        let pool = hintList()
        guard pool.count > 1 else { return 60 }
        let interval = Double(max(3, rotateSec))
        let phase = now.timeIntervalSince1970.truncatingRemainder(dividingBy: interval)
        let start = interval - animMs / 1000
        if phase < start { return start - phase }
        return 1.0 / 30.0
    }

    /// 文字池：公告下排 + 后台配的池子；都没有时退回问候语
    private func hintList() -> [String] {
        var pool = hints.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if pool.isEmpty { pool = [Self.defaultGreeting()] }
        return pool
    }

    /// 长文案折行（对应 Android 的 `wrapText`：中文逐字、最多两行）
    static func wrap(_ text: String, maxWidth: CGFloat, size: CGFloat, bold: Bool, maxLines: Int) -> [String] {
        guard !text.isEmpty else { return [] }
        var lines: [String] = []
        var line = ""
        for ch in text {
            let candidate = line + String(ch)
            if Draw.width(candidate, size: size, bold: bold) > maxWidth && !line.isEmpty {
                lines.append(line)
                line = ""
                if lines.count == maxLines { break }
            }
            line += String(ch)
        }
        if lines.count < maxLines && !line.isEmpty { lines.append(line) }
        // 还有剩余内容就补省略号
        if lines.count == maxLines {
            let tail = text.dropFirst(lines.joined().count)
            if !tail.isEmpty {
                var last = lines[maxLines - 1]
                while !last.isEmpty && Draw.width(last + "…", size: size, bold: bold) > maxWidth {
                    last.removeLast()
                }
                lines[maxLines - 1] = last + "…"
            }
        }
        return lines
    }

    /// 内置兜底问候语（与 Java `defaultGreeting()` 逐字一致，按小时分档）
    static func defaultGreeting(now: Date = Date()) -> String {
        let hour = Calendar.current.component(.hour, from: now)
        switch hour {
        case 0..<5: return "凌晨了！这个点就别忙了，早点睡，明天再弄也不迟~"
        case 5..<8: return "早晨好！新的一天开始了，慢慢来~"
        case 8..<11: return "上午好！有需要随时招呼我~"
        case 11..<13: return "中午好！先好好吃顿饭，忙活不差这一会儿~"
        case 13..<17: return "下午好！今天想弄点什么？"
        case 17..<19: return "傍晚好！忙了一天，先歇口气~"
        case 19..<22: return "晚上好！一天将尽，放松身心，别太辛苦~"
        default: return "夜深了！早点休息吧，明天再弄也不迟~"
        }
    }

    /// 应用服务端下发的公告配置（接口接上后调用）
    func apply(title: String?, hints: [String], rotateSec: Int, enabled: Bool) {
        if let title { self.title = title }
        self.hints = hints
        self.rotateSec = max(3, rotateSec)
        self.enabled = enabled
    }
}
