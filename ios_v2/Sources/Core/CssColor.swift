import UIKit

/// 把 CSS 的 `rgb()/rgba()` 颜色串转成 UIColor（远程布局 spec 用）。
enum CssColor {
    static func parse(_ css: String?) -> UIColor? {
        guard let css else { return nil }
        let s = css.replacingOccurrences(of: " ", with: "")
        guard s.hasPrefix("rgb(") || s.hasPrefix("rgba("),
              let open = s.firstIndex(of: "("), let close = s.lastIndex(of: ")"),
              open < close else { return nil }
        let inner = String(s[s.index(after: open)..<close])
        let parts = inner.components(separatedBy: ",").compactMap { Double($0) }
        guard parts.count >= 3 else { return nil }
        let a = parts.count >= 4 ? parts[3] : 1
        return UIColor(red: parts[0] / 255, green: parts[1] / 255,
                       blue: parts[2] / 255, alpha: a)
    }
}
