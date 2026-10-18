import UIKit

/// 首页外壳「娱乐」栏 —— `MainActivity.drawEntertainment` 系列的 1:1 移植。
///
/// 这一页整页手画（没有参考图）：顶部一张渐变头图 + 若干分区白卡片，
/// 每行是「淡色圆角图标块 + 标题（可带副标题）+ 右侧箭头或开关」。
/// 行、卡片、分区标题的坐标全部来自 `Playbook.layout()`，与点击热区同源。
extension HomeV2Screen {
    // MARK: - 常量（Java:618-640）

    /// 分区标题那根小色条
    private static let sectionBar = (x1: CGFloat(44), y1Offset: CGFloat(34), x2: CGFloat(56), y2Offset: CGFloat(62), radius: CGFloat(6))
    /// 图标块（Java:1137）
    private static let iconBox = (left: CGFloat(76), right: CGFloat(152), halfH: CGFloat(38), radius: CGFloat(24))
    /// 标题/副标题左边距（Java:1139-1140）
    private static let textLeft: CGFloat = 178
    /// 开关与箭头的位置（Java:1143-1144）
    private static let switchLeft: CGFloat = 872
    private static let chevronX: CGFloat = 990

    // MARK: - 绘制入口（Java:1100-1127）

    func drawEntertainment(_ d: Draw, state: State) {
        let sections = Playbook.sections(from: state.playbookSections)
        let layout = Playbook.layout(sections)
        state.entertainmentBottom = layout.bottom
        state.entertainmentRows = layout.rows

        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), Playbook.pageBg)
        d.ctx.saveGState()
        // Java: clipRect(0,0,1080,CONTENT_BOTTOM) 之后 translate(0,-scroll)
        d.ctx.clip(to: CGRect(x: 0, y: 0, width: Self.W, height: Playbook.contentBottom))
        d.ctx.translateBy(x: 0, y: -state.scroll)

        drawEntertainmentHero(d)

        // 同一分区的行合到一张白卡片里（Java:1107-1124）
        var index = 0
        while index < layout.rows.count {
            let first = layout.rows[index]
            var last = index
            while last + 1 < layout.rows.count, layout.rows[last + 1].sectionIndex == first.sectionIndex {
                last += 1
            }
            let cardBottom = layout.rows[last].bottom
            let title = sections.indices.contains(first.sectionIndex) ? sections[first.sectionIndex].title : ""
            drawSectionTitle(d, title, top: first.top - Playbook.sectionHeaderH, accent: first.accent)

            d.round(CGRect(x: Playbook.cardLeft, y: first.top,
                           width: Playbook.cardRight - Playbook.cardLeft, height: cardBottom - first.top),
                    radius: Playbook.cardRadius, UIColor.white)
            d.roundStroke(CGRect(x: Playbook.cardLeft, y: first.top,
                                 width: Playbook.cardRight - Playbook.cardLeft, height: cardBottom - first.top),
                          radius: Playbook.cardRadius, Playbook.cardEdge, width: 1.6)

            for k in index...last {
                drawEntertainmentRow(d, layout.rows[k])
            }
            index = last + 1
        }

        d.ctx.restoreGState()
        drawNav(d, state: state)
    }

    /// 顶部渐变头图（Java:1072-1092）
    private func drawEntertainmentHero(_ d: Draw) {
        let rect = CGRect(x: Playbook.cardLeft, y: Playbook.heroTop,
                          width: Playbook.cardRight - Playbook.cardLeft,
                          height: Playbook.heroBottom - Playbook.heroTop)
        // 对角渐变：从左上 BRAND 到右下 BRAND_2
        d.gradient(rect, from: Playbook.brand, to: Playbook.brand2, vertical: true)
        d.clipped(to: rect, radius: 34) {
            d.circle(cx: 918, cy: 196, r: 92, UIColor(white: 1, alpha: 0.15))
            d.circle(cx: 1006, cy: 302, r: 54, UIColor(white: 1, alpha: 0.10))
            d.circle(cx: 846, cy: 302, r: 9, UIColor(white: 1, alpha: 0.25))
        }
        d.text("更多玩法", x: 76, baseline: 244, size: 58, UIColor.white, bold: true)
        d.text("挑一个玩法，让截图更真实", x: 78, baseline: 306, size: 28,
               UIColor(white: 1, alpha: 0.8))
    }

    /// 分区标题：小色条 + 深色粗字（Java:1095-1098）
    private func drawSectionTitle(_ d: Draw, _ title: String, top: CGFloat, accent: UIColor) {
        d.round(CGRect(x: 44, y: top + 34, width: 56 - 44, height: 62 - 34), radius: 6, accent)
        d.text(title, x: 78, baseline: top + 64, size: 34, Playbook.ink, bold: true)
    }

    /// 一行玩法（Java:1129-1145）
    private func drawEntertainmentRow(_ d: Draw, _ box: Playbook.RowBox) {
        let top = box.top
        let middle = top + Playbook.rowH / 2
        let titleBaseline = middle - 8
        let hasSubtitle = !box.row.subtitle.isEmpty

        if !box.firstInSection {
            d.hairline(top, from: 76, to: Playbook.cardRight - 44, Playbook.cardHairline, width: 1.6)
        }

        // 左边淡色圆角图标块（Java:1137-1138）
        d.round(CGRect(x: 76, y: middle - 38, width: 152 - 76, height: 76), radius: 24, box.tint)
        drawEntertainmentGlyph(d, index: box.glyph, cx: 114, cy: middle, color: box.accent)

        d.text(box.row.title, x: 178, baseline: hasSubtitle ? titleBaseline : middle + 13,
               size: 36, Playbook.ink)
        if hasSubtitle {
            d.text(box.row.subtitle, x: 178, baseline: middle + 36, size: 28, Playbook.subInk)
        }
        // 「声音」那一行标题后面挂一个 NEW 小牌子（Java:1141-1142）
        if box.row.key == "sound" {
            drawNewBadge(d, left: 178 + Draw.width(box.row.title, size: 36) + 22, baseline: titleBaseline)
        }
        if box.row.switchRow {
            drawSwitch(d, x: 872, top: middle - 28, on: box.row.enabled)
        } else {
            drawChevron(d, cx: 990, cy: middle)
        }
    }

    /// NEW 小牌子（Java:1152-1156）
    private func drawNewBadge(_ d: Draw, left: CGFloat, baseline: CGFloat) {
        let width = Draw.width("NEW", size: 24, bold: true) + 26
        d.round(CGRect(x: left, y: baseline - 30, width: width, height: 42), radius: 21,
                Draw.color(0xffff4d4f))
        d.text("NEW", x: left + 13, baseline: baseline, size: 24, UIColor.white, bold: true)
    }

    /// 右向箭头（Java:1208-1211）
    func drawChevron(_ d: Draw, cx: CGFloat, cy: CGFloat) {
        let gray = Draw.color(0xffaaaaaa)
        d.line(cx - 9, cy - 12, cx + 3, cy, gray, width: 4)
        d.line(cx + 3, cy, cx - 9, cy + 12, gray, width: 4)
    }

    /// 开关（Java:1213-1217）
    private func drawSwitch(_ d: Draw, x: CGFloat, top: CGFloat, on: Bool) {
        d.round(CGRect(x: x, y: top, width: 132, height: 56), radius: 28,
                on ? Playbook.brand : Draw.color(0xffe3e6ef))
        d.circle(cx: x + (on ? 104 : 28), cy: top + 28, r: 23, UIColor.white)
    }

    // MARK: - 八个字形（Java:1159-1206）

    /// 图标块里的字形：整体缩小到 0.78、描边 6，原点平移到 (cx, cy)
    private func drawEntertainmentGlyph(_ d: Draw, index: Int, cx: CGFloat, cy: CGFloat, color: UIColor) {
        d.ctx.saveGState()
        d.ctx.translateBy(x: cx, y: cy)
        d.ctx.scaleBy(x: 0.78, y: 0.78)
        let stroke: CGFloat = 6

        switch index {
        case 0:   // 圆形 + 缺口弧：去水印
            d.ctx.setStrokeColor(color.cgColor)
            d.ctx.setLineWidth(stroke)
            d.ctx.strokeEllipse(in: CGRect(x: -23, y: -23, width: 46, height: 46))
            d.arc(rect: CGRect(x: -27, y: -27, width: 54, height: 54),
                  startDegrees: 35, sweepDegrees: 290, color: color, width: stroke)
        case 1:   // 「淘」
            d.text("淘", x: -23, baseline: 20, size: 42, color, bold: true)
        case 2:   // 圆角方块 + 两点（头像）
            d.round(CGRect(x: -26, y: -22, width: 52, height: 45), radius: 11, color)
            d.circle(cx: -9, cy: -4, r: 2, UIColor.white)
            d.circle(cx: 9, cy: -4, r: 2, UIColor.white)
        case 3:   // 听筒弧 + 两条竖线（来电）
            d.arc(rect: CGRect(x: -26, y: -26, width: 52, height: 52),
                  startDegrees: 125, sweepDegrees: 290, color: color, width: stroke)
            d.line(-23, 15, -23, 28, color, width: 7)
            d.line(23, 15, 23, 28, color, width: 7)
        case 4:   // 圆角矩形 + 闪电（关机）
            d.round(CGRect(x: -25, y: -18, width: 49, height: 35), radius: 5, color)
            d.path(points: [(1, -17), (-9, 1), (1, 1), (-4, 17), (12, -4), (3, -4)], fill: UIColor.white)
        case 5:   // 外弧 + 内圆（声音）
            // Java 那边 p 仍是 STROKE 样式，drawArc 的 useCenter=true 在 STROKE 下
            // 不会填充（只画弧线）；这里如果传 fill:true 会变成「吃豆人」实心饼。
            d.arc(rect: CGRect(x: -25, y: -23, width: 50, height: 48),
                  startDegrees: 205, sweepDegrees: 300, color: color, width: stroke, fill: false)
            d.arc(rect: CGRect(x: -12, y: -12, width: 24, height: 24),
                  startDegrees: 0, sweepDegrees: 360, color: color, width: stroke)
        case 6:   // 实心圆 + ¥（余额）
            d.circle(cx: 0, cy: 0, r: 24, color)
            d.text("¥", x: -13, baseline: 14, size: 29, UIColor.white, bold: true)
        case 7:   // 人形（撩妹）
            d.circle(cx: 0, cy: -13, r: 12, color)
            d.line(0, -1, 0, 25, color, width: stroke)
            d.line(-15, 13, 15, 13, color, width: stroke)
        default:
            break
        }

        d.ctx.restoreGState()
    }
}
