import UIKit

/// 「工具」Tab —— `MainActivity.drawTools()`（Java:1778-1844）与 `buildToolsLayout()`（Java:1623-1672）的移植。
///
/// 版式（全部是 Android 的常量，单位与参考画布一致，不乘 3 —— 这些数值本来就是画布像素）：
///
/// ```text
/// 顶部黄条 234 高，居中「工具」42 号字，基线 174
/// 分区头 258 高：圆底 168 的图标 + 标题 39 号 + 右侧 › + 底线
/// 入口格 4 列：264 × 272.25，从 12 起排；图标槽 132，圆底 = 0.86×132，字形 = 0.63×圆底
/// 文字 33 号：不超过 4 字一行，超过则第 5 字起换行（Java:1811-1817）
/// 分区高 = 906.75 + (行数-1) × 272.25，末尾再画「更多功能及教程」胶囊
/// ```
///
/// 这一页没有独立 Screen：它画在 `HomeV2Screen` 的第 5 个 Tab 里（与「模拟」同样的做法），
/// 滚动由本文件自己做 `-scroll` 平移（`HomeV2Screen.selfScrolls == true`）。
enum ToolsPage {
    static let W: CGFloat = 1080

    // MARK: 常量（逐条对着 Java:638-653 抄）

    static let barH: CGFloat = 234
    static let rowH: CGFloat = 258
    static let hdrLogo: CGFloat = 168
    static let iconSlot: CGFloat = 132
    static let cellW: CGFloat = 264
    static let cellH: CGFloat = 272.25
    static let left: CGFloat = 12
    static let gridDY: CGFloat = 373.5
    static let labelDY: CGFloat = 150
    static let labelH: CGFloat = 83.25
    static let tutW: CGFloat = 301.5
    static let tutH: CGFloat = 80.25

    static let barColor = Draw.color(0xfff9db61)
    static let accent = Draw.color(0xffff7a1a)
    static let ink = Draw.color(0xff333333)
    static let band = Draw.color(0xfff7f7f7)
    static let circle = Draw.color(0xfff5f5f5)
    static let lineColor = Draw.color(0xffeeeeee)
    static let chevron = Draw.color(0xffb0b0b0)

    /// 每个分区的起止（内容坐标，从 barH 开始往下堆）
    static func layout(_ sections: [ToolsCatalog.Section] = ToolsCatalog.sections)
        -> [(section: ToolsCatalog.Section, top: CGFloat, gridTop: CGFloat, height: CGFloat)] {
        var result: [(section: ToolsCatalog.Section, top: CGFloat, gridTop: CGFloat, height: CGFloat)] = []
        var y = barH
        for section in sections {
            let rows = max(1, (section.items.count + 3) / 4)
            // Java:1664 —— 分区高 = 教程胶囊顶 - 分区顶 + 215.25，等价于下面这个式子
            let height = 906.75 + CGFloat(rows - 1) * cellH
            result.append((section: section, top: y, gridTop: y + gridDY, height: height))
            y += height
        }
        return result
    }

    static var contentHeight: CGFloat {
        let laid = layout()
        guard let last = laid.last else { return barH + 40 }
        return last.top + last.height + 30
    }

    // MARK: 绘制

    /// - Parameter scroll: 当前滚动偏移。**分区内容自己平移**，顶部黄条固定在屏幕顶
    ///   （对应 Java `drawTools`：先 `clipRect(0, TOOL_BAR_H, W, 2212)` + `translate(0,-scroll)`
    ///   画分区，`restore()` 之后再画黄条）。
    static func draw(_ d: Draw, scroll: CGFloat) {
        let laid = layout()
        d.rect(CGRect(x: 0, y: 0, width: W, height: 2388), MockInk.white)

        d.ctx.saveGState()
        // 先按屏幕坐标裁（黄条以下），再平移 —— 顺序与 Android 一致，
        // 反过来会让裁剪区跟着内容一起滚。
        d.ctx.clip(to: CGRect(x: 0, y: barH, width: W, height: 2388 - barH))
        d.ctx.translateBy(x: 0, y: -scroll)

        if laid.isEmpty {
            d.centerText("工具数据缺失（tools_page.json 没打进包）", cx: W / 2, baseline: barH + 160,
                         size: 36, MockInk.label)
        }

        for (index, item) in laid.enumerated() {
            // Java:1794-1797：第 3 个分区起，头部上面垫一条灰色分隔带
            if index >= 2 {
                d.rect(CGRect(x: 0, y: item.top - 47.5, width: W, height: 30), band)
            }
            drawHeader(d, item.section, top: item.top)
            for (cellIndex, cell) in item.section.items.enumerated() {
                let col = cellIndex % 4
                let row = cellIndex / 4
                let cellLeft = left + CGFloat(col) * cellW
                let cellTop = item.gridTop + CGFloat(row) * cellH
                drawCell(d, cell, left: cellLeft, top: cellTop)
            }
            drawTutorialPill(d, top: tutorialTop(item))
        }
        d.ctx.restoreGState()

        // 顶部黄条固定在屏幕顶（Java:1840-1842）
        d.rect(CGRect(x: 0, y: 0, width: W, height: barH), barColor)
        d.centerText("工具", cx: W / 2, baseline: 174, size: 42, Draw.color(0xff1a1a1a))
    }

    private static func tutorialTop(
        _ item: (section: ToolsCatalog.Section, top: CGFloat, gridTop: CGFloat, height: CGFloat)
    ) -> CGFloat {
        let rows = max(1, (item.section.items.count + 3) / 4)
        let lastLogo = item.gridTop + CGFloat(rows - 1) * cellH
        let lastLabelBottom = lastLogo + labelDY + labelH
        return lastLabelBottom + 84.75
    }

    /// 分区头：圆底图标 + 标题 + ›（Java:1739-1750）
    private static func drawHeader(_ d: Draw, _ section: ToolsCatalog.Section, top: CGFloat) {
        let logo = CGRect(x: 45, y: top + 45, width: hdrLogo, height: hdrLogo)
        d.round(logo, radius: hdrLogo / 2, circle)
        drawGlyph(d, section.icon, cx: logo.midX, cy: logo.midY, size: hdrLogo * 0.78)

        let titleSize: CGFloat = 39
        d.text(section.title, x: 258, baseline: top + 143, size: titleSize, ink)

        // › 两条线（Java:1746-1747）
        d.line(1013, top + 113, 1027, top + 129, chevron, width: 4.5)
        d.line(1027, top + 129, 1013, top + 145, chevron, width: 4.5)
        d.hairline(top + rowH, from: 0, to: W, lineColor, width: 1.5)
    }

    /// 一个入口格：圆底 + 字形 + 文字（Java:1799-1818）
    private static func drawCell(_ d: Draw, _ cell: ToolsCatalog.Cell, left cellLeft: CGFloat, top: CGFloat) {
        let logoLeft = cellLeft + (cellW - iconSlot) / 2
        // 参考图里圆底比图标槽小一圈（152/176 → 0.86）
        let dot = iconSlot * 0.86
        let inset = (iconSlot - dot) / 2
        d.circle(cx: logoLeft + inset + dot / 2, cy: top + inset + dot / 2,
                 r: dot / 2, circle)
        drawGlyph(d, cell.icon, cx: logoLeft + iconSlot / 2, cy: top + iconSlot / 2,
                  size: dot * 0.63)

        let labelTop = top + labelDY
        let centerX = cellLeft + cellW / 2
        let size: CGFloat = 33
        if cell.name.count > 4 {
            let cut = cell.name.index(cell.name.startIndex, offsetBy: 4)
            d.centerText(String(cell.name[cell.name.startIndex..<cut]),
                         cx: centerX, baseline: labelTop + 34, size: size, ink)
            d.centerText(String(cell.name[cut...]),
                         cx: centerX, baseline: labelTop + 71.5, size: size, ink)
        } else {
            d.centerText(cell.name, cx: centerX, baseline: labelTop + 52, size: size, ink)
        }
    }

    /// 「更多功能及教程」胶囊（Java:1766-1776）
    private static func drawTutorialPill(_ d: Draw, top: CGFloat) {
        let left = W / 2 - tutW / 2
        d.round(CGRect(x: left, y: top, width: tutW, height: tutH), radius: tutH / 2, circle)
        let size: CGFloat = 32
        let a = "更多功能及"
        let b = "教程"
        let wa = Draw.width(a, size: size)
        let wb = Draw.width(b, size: size)
        let x = W / 2 - (wa + wb) / 2
        d.text(a, x: x, baseline: top + 52, size: size, ink)
        d.text(b, x: x + wa, baseline: top + 52, size: size, accent)
    }

    /// 图标字形：切图本身带透明边，按 63%/78% 槽位等比画进去（对应 `drawToolGlyph`）
    private static func drawGlyph(_ d: Draw, _ icon: String, cx: CGFloat, cy: CGFloat, size: CGFloat) {
        guard !icon.isEmpty, let image = ToolsAssets.icon(icon) else { return }
        // 保持宽高比：按较长边缩到 size
        let w = image.size.width
        let h = image.size.height
        let scale = size / max(1, max(w, h))
        d.art(image, CGRect(x: cx - w * scale / 2, y: cy - h * scale / 2,
                            width: w * scale, height: h * scale))
    }

    // MARK: 命中（内容坐标：宿主已把 scroll 加进 y）

    static func hit(x: CGFloat, y: CGFloat) -> ActionToken {
        for item in layout() {
            if y >= item.top && y < item.top + rowH {
                return item.section.action.isEmpty ? "noop" : "tools:\(item.section.action)|\(item.section.title)"
            }
            for (cellIndex, cell) in item.section.items.enumerated() {
                let cellLeft = left + CGFloat(cellIndex % 4) * cellW
                let cellTop = item.gridTop + CGFloat(cellIndex / 4) * cellH
                if x >= cellLeft && x < cellLeft + cellW && y >= cellTop && y < cellTop + cellH {
                    return cell.action.isEmpty ? "noop" : "tools:\(cell.action)|\(cell.name)"
                }
            }
            let tutTop = tutorialTop(item)
            if y >= tutTop - 8 && y < tutTop + tutH
                && x >= W / 2 - tutW / 2 && x <= W / 2 + tutW / 2 {
                return "tools:tutorial|更多功能及教程"
            }
        }
        return "noop"
    }
}
