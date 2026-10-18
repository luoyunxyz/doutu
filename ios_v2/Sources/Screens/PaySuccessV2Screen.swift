import UIKit

/// 「支付成功」页 —— `features/zhi/ui/PaySuccessV2Screen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 这一页有两件事和别页不同：
///
/// 1. **可改内容是「行/段」两层结构**：一行（`Line`）有补丁矩形与补丁方式，行内若干段（`Run`）
///    各自有键、默认值、位置、字号、颜色、对齐。默认状态（没人改过）就是**纯切图**；
///    只有某一行里存在被改过的键，才盖掉那一行重画。
/// 2. **补丁不是纯色**：蓝底那几行是把左边那条 6px 宽的竖向渐变（`ps_back`）按同样的 y 区间
///    切一段、横向拉伸铺上去；`GRADIENT_DIM` 再多盖一层 3.5% 的黑；红按钮则是把 `ps_red`
///    那条红色渐变拉伸过来。这样补丁和周围是同一张图上的像素，颜色一模一样。
///
/// 数字用 Archivo SemiCondensed Semibold（见 `Fonts`），汉字与「¥」用系统字体 —— 参考图就是这样。
final class PaySuccessV2Screen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    final class State: ScreenState {
        /// 被改过的段（key → 新文字）
        var texts: [String: String] = [:]
    }

    // MARK: - 调色板（Java:46-67）
    private let topBlue = Draw.color(0xff3874f6)
    private let cardColor = Draw.color(0xffffffff)
    private let ink = Draw.color(0xff2b2b2b)
    private let cardTitle = Draw.color(0xff1a1a1a)
    private let grey = Draw.color(0xff9a9a9a)
    private let red = Draw.color(0xffed4e3b)
    private let redText = Draw.color(0xffe8503f)
    private let chipBg = Draw.color(0xfffef7f1)
    private let chipLine = Draw.color(0xffebe2dc)
    private let chipText = Draw.color(0xffc4683c)
    private let doneBg = Draw.color(0xfff5f5f5)
    private let doneText = Draw.color(0xff1677ff)
    private let white = UIColor.white
    /// 盖一层黑：alpha 9/255（Java 的 DIM_ALPHA）
    private let dimAlpha: CGFloat = 9.0 / 255.0

    private let topY: CGFloat = 0
    private let cardY: CGFloat = 1013
    private let bottomY: CGFloat = 2344
    /// `ps_back` 这条渐变条的尺寸（Java:48-52）
    private let backW: CGFloat = 6
    private let backH: CGFloat = 2816
    private let redW: CGFloat = 6
    private let redH: CGFloat = 78

    // MARK: - 行/段结构

    /// 补丁方式（Java:100）
    private enum PatchKind { case plain, gradient, gradientDim, money }
    private enum Align { case left, center, right }

    private struct Run {
        let key: String
        let def: String
        let x: CGFloat
        let baseline: CGFloat
        let size: CGFloat
        let color: UIColor
        let align: Align
        let bold: Bool
        /// 金额里「¥」的字号（>0 表示这一段按金额画）
        var yuan: CGFloat = 0
        var chip = false
    }

    private struct Line {
        let id: String
        let rect: CGRect
        let patch: PatchKind
        var patchColor: UIColor = .white
        var chain = false
        var runs: [Run] = []
    }

    private let lines: [Line]

    /// 5 行优惠（Java:144-150）
    private static let rows: [[String]] = [
        ["14元", "闪购新人红包", "淘宝闪购", "下单0元起用", "免费领"],
        ["", "资金周转找网商贷", "网商贷", "最高可借1000000元", "去看看"],
        ["4元", "漫花厨房湿巾优惠券", "分期生活", "满4元可用", "免费领"],
        ["3元", "国网电费券", "生活缴费", "满50元立减", "免费领"],
        ["8.8折", "顺风车红包", "同程旅行", "满20元可用", "免费领"],
    ]
    private let rowTop: CGFloat = 1191
    private let rowStep: CGFloat = 239

    private var art: [String: UIImage] = [:]

    init() {
        for res in ["ps_top", "ps_card", "ps_bottom", "ps_back", "ps_red"] {
            if let image = UIImage(named: res) { art[res] = image }
        }

        var list: [Line] = []

        // 标题、回首页、金额、两行明细、对账按钮（Java:155-172）
        list.append(Line(id: "title", rect: CGRect(x: 556, y: 220, width: 816 - 556, height: 305 - 220),
                         patch: .gradient, runs: [
                            Run(key: "ps.title", def: "支付成功", x: 574, baseline: 295, size: 57,
                                color: UIColor.white, align: .left, bold: false),
                         ]))
        list.append(Line(id: "home", rect: CGRect(x: 1058, y: 188, width: 1238 - 1058, height: 262 - 188),
                         patch: .gradient, runs: [
                            Run(key: "ps.home", def: "回首页", x: 1072, baseline: 251, size: 54,
                                color: UIColor.white, align: .left, bold: false),
                         ]))
        list.append(Line(id: "amount", rect: CGRect(x: 400, y: 378, width: 880 - 400, height: 545 - 378),
                         patch: .money, runs: [
                            Run(key: "ps.amount", def: "0.00", x: 640, baseline: 523, size: 174,
                                color: UIColor.white, align: .center, bold: false, yuan: 119),
                         ]))
        list.append(Line(id: "l1", rect: CGRect(x: 78, y: 642, width: 330 - 78, height: 716 - 642),
                         patch: .gradient, runs: [
                            Run(key: "ps.l1", def: "你", x: 87, baseline: 704, size: 51,
                                color: UIColor.white, align: .left, bold: false),
                         ]))
        list.append(Line(id: "v1", rect: CGRect(x: 900, y: 642, width: 1215 - 900, height: 716 - 642),
                         patch: .gradient, runs: [
                            Run(key: "ps.v1", def: "¥ 0.01", x: 1188, baseline: 702, size: 57,
                                color: UIColor.white, align: .right, bold: false),
                         ]))
        list.append(Line(id: "l2", rect: CGRect(x: 78, y: 737, width: 330 - 78, height: 811 - 737),
                         patch: .gradient, runs: [
                            Run(key: "ps.l2", def: "百次立减", x: 88, baseline: 799, size: 51,
                                color: UIColor.white, align: .left, bold: false),
                         ]))
        list.append(Line(id: "v2", rect: CGRect(x: 900, y: 737, width: 1215 - 900, height: 811 - 737),
                         patch: .gradient, runs: [
                            Run(key: "ps.v2", def: "-¥ 0.01", x: 1188, baseline: 797, size: 57,
                                color: UIColor.white, align: .right, bold: false),
                         ]))
        list.append(Line(id: "check", rect: CGRect(x: 498, y: 876, width: 860 - 498, height: 948 - 876),
                         patch: .gradientDim, runs: [
                            Run(key: "ps.check", def: "点击给商家对账", x: 505, baseline: 935, size: 49,
                                color: UIColor.white, align: .left, bold: false),
                         ]))
        list.append(Line(id: "card", rect: CGRect(x: 60, y: 1038, width: 400 - 60, height: 1112 - 1038),
                         patch: .plain, runs: [
                            Run(key: "ps.card", def: "支付有礼", x: 68, baseline: 1100, size: 52,
                                color: Draw.color(0xff1a1a1a), align: .left, bold: true),
                         ]))

        // 5 行：标题行（红色数字 + 标题，接着排）、标签行（胶囊 + 说明）、右侧红按钮
        for (i, row) in Self.rows.enumerated() {
            let top = rowTop + CGFloat(i) * rowStep
            var head = Line(id: "r\(i).head",
                            rect: CGRect(x: 230, y: top - 8, width: 1000 - 230, height: 56 + 8),
                            patch: .plain)
            head.chain = true
            if !row[0].isEmpty {
                head.runs.append(Run(key: "r\(i).amount", def: row[0], x: 239, baseline: top + 51,
                                     size: 49, color: Draw.color(0xffe8503f), align: .left,
                                     bold: true))
            }
            head.runs.append(Run(key: "r\(i).title", def: row[1],
                                 x: row[0].isEmpty ? 239 : 0, baseline: top + 51, size: 49,
                                 color: Draw.color(0xff2b2b2b), align: .left, bold: true))
            list.append(head)

            var tag = Line(id: "r\(i).tag",
                           rect: CGRect(x: 232, y: top + 64, width: 1000 - 232, height: 132 - 64),
                           patch: .plain)
            var chip = Run(key: "r\(i).tag", def: row[2], x: 255, baseline: top + 119, size: 41,
                           color: Draw.color(0xffc4683c), align: .left, bold: false)
            chip.chip = true
            tag.runs.append(chip)
            tag.runs.append(Run(key: "r\(i).desc", def: row[3], x: 452, baseline: top + 119,
                                size: 41, color: Draw.color(0xff9a9a9a), align: .left, bold: false))
            list.append(tag)

            var button = Line(id: "r\(i).button",
                              rect: CGRect(x: 1010, y: top + 24, width: 1208 - 1010, height: 104 - 24),
                              patch: .plain)
            button.patchColor = Draw.color(0xffed4e3b)
            button.runs.append(Run(key: "r\(i).button", def: row[4], x: 1109, baseline: top + 83,
                                   size: 40, color: UIColor.white, align: .center, bold: false))
            list.append(button)
        }

        // 底部「完成」
        var done = Line(id: "done", rect: CGRect(x: 555, y: 2578, width: 725 - 555, height: 2662 - 2578),
                        patch: .plain)
        done.patchColor = Draw.color(0xfff5f5f5)
        done.runs.append(Run(key: "ps.done", def: "完成", x: 640, baseline: 2647, size: 60,
                             color: Draw.color(0xff1677ff), align: .center, bold: false))
        list.append(done)

        lines = list
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    // MARK: - 绘制（Java:234-240）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), topBlue)
        drawStrip(d, "ps_top", y: topY)
        drawStrip(d, "ps_card", y: cardY)
        drawStrip(d, "ps_bottom", y: bottomY)
        for line in lines {
            drawLine(d, s, line)
        }
    }

    /// 整宽切图
    private func drawStrip(_ d: Draw, _ name: String, y: CGFloat) {
        guard let image = art[name] else { return }
        d.art(image, CGRect(x: 0, y: y, width: image.size.width * image.scale,
                            height: image.size.height * image.scale))
    }

    /// 只画「有键被改过」的行（Java:242-262）
    private func drawLine(_ d: Draw, _ s: State, _ line: Line) {
        let changed = line.runs.contains { s.texts[$0.key] != nil }
        guard changed else { return }
        patch(d, line)

        var cursor: CGFloat = 0
        var first = true
        for run in line.runs {
            let value = s.texts[run.key] ?? run.def
            if value.isEmpty { continue }
            var x = run.x
            if line.chain && !first { x = cursor + 14 }
            if line.chain { cursor = x + Fonts.mixedWidth(value, size: run.size, bold: run.bold) }

            if run.chip {
                drawChip(d, run, value, x: x)
            } else if run.yuan > 0 {
                drawMoney(d, run, value)
            } else {
                drawMixed(d, value, x: x, baseline: run.baseline, size: run.size,
                          color: run.color, align: run.align, bold: run.bold)
            }
            first = false
        }
    }

    /// 金额：小一号「¥」+ 大号数字，整体居中（Java:265-272）
    private func drawMoney(_ d: Draw, _ run: Run, _ value: String) {
        let digits = Fonts.mixedWidth(value, size: run.size, bold: run.bold)
        let yuan = Fonts.mixedWidth("¥", size: run.yuan, bold: run.bold)
        let start = run.x - (yuan + 18 + digits) / 2
        drawMixed(d, "¥", x: start, baseline: run.baseline, size: run.yuan,
                  color: run.color, align: .left, bold: run.bold)
        drawMixed(d, value, x: start + yuan + 18, baseline: run.baseline, size: run.size,
                  color: run.color, align: .left, bold: run.bold)
    }

    /// 浅底胶囊（Java:275-293）
    private func drawChip(_ d: Draw, _ run: Run, _ value: String, x: CGFloat) {
        var size = run.size
        while size > 26 && Fonts.mixedWidth(value, size: size, bold: run.bold) > 170 { size -= 1 }
        let wide = Fonts.mixedWidth(value, size: size, bold: run.bold)
        let box = CGRect(x: x - 16, y: run.baseline - 43, width: wide + 32, height: 60)
        d.round(box, radius: 8, chipBg)
        d.roundStroke(box, radius: 8, chipLine, width: 2)
        drawMixed(d, value, x: x, baseline: run.baseline, size: size,
                  color: run.color, align: .left, bold: run.bold)
    }

    /// 按行内的补丁方式盖掉原内容（Java:299-329）
    private func patch(_ d: Draw, _ line: Line) {
        switch line.patch {
        case .gradient, .gradientDim, .money:
            // 从 `ps_back` 上切同样 y 区间的一条，横向拉伸铺过来
            guard let back = art["ps_back"] else { return }
            let top = max(0, min(backH, line.rect.minY))
            let bottom = max(0, min(backH, line.rect.maxY))
            let scale = back.scale
            let srcRect = CGRect(x: 0, y: top * scale, width: backW * scale,
                                 height: (bottom - top) * scale)
            guard let slice = back.cgImage?.cropping(to: srcRect) else { return }
            d.art(UIImage(cgImage: slice, scale: scale, orientation: .up), line.rect)
            if line.patch == .gradientDim {
                d.ctx.setFillColor(UIColor.black.withAlphaComponent(dimAlpha).cgColor)
                d.ctx.fill(line.rect)
            }
        case .plain:
            if line.patchColor == red, let redArt = art["ps_red"] {
                // 红按钮：把那条 6×78 的红色渐变拉伸过来
                let scale = redArt.scale
                let srcRect = CGRect(x: 0, y: 0, width: redW * scale, height: redH * scale)
                if let slice = redArt.cgImage?.cropping(to: srcRect) {
                    d.art(UIImage(cgImage: slice, scale: scale, orientation: .up), line.rect)
                }
            } else {
                d.rect(line.rect, line.patchColor)
            }
        }
    }

    /// 混排文字：数字走 Archivo，汉字与符号走系统字体（Java:341-360）
    private func drawMixed(_ d: Draw, _ text: String, x: CGFloat, baseline: CGFloat,
                           size: CGFloat, color: UIColor, align: Align, bold: Bool) {
        let width = Fonts.mixedWidth(text, size: size, bold: bold)
        var start = x
        switch align {
        case .left: start = x
        case .center: start = x - width / 2
        case .right: start = x - width
        }
        var cursor = start
        for ch in text {
            let font = Fonts.usesNumberFace(ch) ? Fonts.number(size) : Fonts.system(size, bold: bold)
            let piece = String(ch)
            let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
            NSAttributedString(string: piece, attributes: attrs)
                .draw(at: CGPoint(x: cursor, y: baseline - font.ascender))
            cursor += piece.size(withAttributes: [.font: font]).width
        }
    }

    // MARK: - 命中（Java:398-438）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        let s = (state as? State) ?? State()
        // 完成按钮、对账按钮、每行红按钮与「…」按整块算
        if y >= 2538 && y <= 2696 && x >= 275 && x <= 1005 { return "done" }
        if y >= 856 && y <= 965 && x >= 390 && x <= 890 { return "noop" }
        for i in 0..<Self.rows.count {
            let top = rowTop + CGFloat(i) * rowStep
            if y >= top + 20 && y <= top + 107 && x >= 1007 && x <= 1211 { return "noop" }
            if y >= top - 10 && y <= top + 60 && x >= 1140 && x <= 1220 { return "noop" }
        }
        for line in lines {
            for run in line.runs {
                if run.key == "ps.home" && y >= 180 && y <= 270 && x >= 1040 { return "home" }
                if inside(run, s, x, y) { return "edit:" + run.key }
            }
        }
        return "noop"
    }

    private func inside(_ run: Run, _ s: State, _ x: CGFloat, _ y: CGFloat) -> Bool {
        let value = s.texts[run.key] ?? run.def
        let wide = max(40, Fonts.mixedWidth(value, size: run.size, bold: run.bold))
        let left: CGFloat
        let right: CGFloat
        switch run.align {
        case .center:
            left = run.x - wide / 2 - 12
            right = run.x + wide / 2 + 12
        case .right:
            left = run.x - wide - 12
            right = run.x + 12
        case .left:
            left = run.x - 12
            right = run.x + wide + 12
        }
        return x >= left && x <= right && y >= run.baseline - run.size - 8 && y <= run.baseline + 16
    }
}
