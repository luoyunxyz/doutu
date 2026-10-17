import UIKit

/// 「提现完成」页 —— `features/zhi/ui/WithdrawDoneScreen.java` 的 1:1 移植（参考画布 1280×2816）。
///
/// 这一页的难点在两处：
///   1. **蓝色渐变面**是 11 个色标的多段渐变（顶部 #3B96FF 压深到 #1778FD，再从 y≈1050 淡回页底色），
///      不是两色渐变；
///   2. 所有文字都做了 **0.94 的横向压缩**，而且缩放轴在文字各自的锚点上
///      （左对齐以 x 为轴、右对齐以 right 为轴、居中以 cx 为轴）—— 这就是 Android 里
///      `c.scale(TEXT_SQUEEZE, 1f, pivotX, 0f)` 的语义。
final class WithdrawDoneScreen: Screen {
    let canvasSize = CGSize(width: 1280, height: 2816)
    /// 这一屏的 draw 不自带滚动 translate —— 由宿主统一滚
    let selfScrolls = false
    static let W: CGFloat = 1280
    static let H: CGFloat = 2816

    /// 文字横向压缩系数（Java:38）
    private let squeeze: CGFloat = 0.94

    final class State: ScreenState {
        /// 提现金额
        var amount: Double = 0
        /// 到账银行卡（形如「招商银行储蓄卡(4646)」，括号前没空格）
        var bank: String = ""
    }

    // MARK: - 调色板（Java:40-48）
    private let pageBg = Draw.color(0xfff2f3f7)
    private let card = Draw.color(0xffffffff)
    private let subtle = Draw.color(0xffb4d6ff)
    private let cardInk = Draw.color(0xff313131)
    private let cardSub = Draw.color(0xff9d9d9d)
    private let chevronColor = Draw.color(0xffb9bcc2)
    private let disc = Draw.color(0xfff7f8fa)
    private let orange = Draw.color(0xfffca51a)

    // MARK: - 蓝面多段渐变（Java:51-58）
    private let sheetH: CGFloat = 1460
    private let sheetColors: [UIColor] = [
        Draw.color(0xff3b96ff), Draw.color(0xff2f8aff), Draw.color(0xff2781ff),
        Draw.color(0xff1d78fd), Draw.color(0xff1778fd), Draw.color(0xff2c84ff),
        Draw.color(0xff4892fd), Draw.color(0xff75adfa), Draw.color(0xffa8c8f9),
        Draw.color(0xffd9e6f9), Draw.color(0xfff2f3f7),
    ]
    private var sheetStops: [CGFloat] {
        [0, 300 / sheetH, 600 / sheetH, 900 / sheetH, 1000 / sheetH, 1050 / sheetH,
         1100 / sheetH, 1200 / sheetH, 1300 / sheetH, 1400 / sheetH, 1]
    }

    // MARK: - 几何（Java:60-100）
    private let doneBase: CGFloat = 242
    private let doneSize: CGFloat = 61.5
    private let doneRight: CGFloat = 1234
    private let markRect = CGRect(x: 594, y: 311, width: 683 - 594, height: 400 - 311)
    private let titleBase: CGFloat = 474
    private let titleSize: CGFloat = 54
    private let subBase: CGFloat = 571
    private let subSize: CGFloat = 44
    private let amountBase: CGFloat = 755
    private let amountSize: CGFloat = 158

    private let rowLabelX: CGFloat = 81
    private let rowValueRight: CGFloat = 1198
    private let rowSize: CGFloat = 51
    private let rowBase: [CGFloat] = [884, 982, 1071]

    private let cardBox = CGRect(x: 41, y: 1134, width: 1237 - 41, height: 1432 - 1134)
    private let cardRadius: CGFloat = 47
    private let discR: CGFloat = 101
    private let discCx: CGFloat = 189
    private let discCy: CGFloat = 1281
    private let iconRect = CGRect(x: 135, y: 1231, width: 232 - 135, height: 1328 - 1231)
    private let iconRadius: CGFloat = 16
    private let iconBarH: CGFloat = 10
    private let iconBarR: CGFloat = 5
    private let cardTitleX: CGFloat = 323
    private let cardTitleBase: CGFloat = 1271
    private let cardTitleSize: CGFloat = 58
    private let cardSubBase: CGFloat = 1335
    private let cardSubSize: CGFloat = 47
    private let cardChevron = CGRect(x: 1165, y: 1266, width: 1183 - 1165, height: 1299 - 1266)

    private let button = CGRect(x: 240, y: 2560, width: 1038 - 240, height: 2710 - 2560)
    private let buttonRadius: CGFloat = 75
    private let buttonL = Draw.color(0xff119dff)
    private let buttonR = Draw.color(0xff1879fe)
    private let buttonTextBase: CGFloat = 2657
    private let buttonTextSize: CGFloat = 60
    private let chevronStroke: CGFloat = 4.6

    private var mark: UIImage?

    init() {
        mark = UIImage(named: "wd_done_mark")
    }

    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }   // 这一页不滚动（Java:286-288）

    // MARK: - 绘制（Java:121-168）

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // ① 11 色标的多段蓝面
        d.multiGradient(CGRect(x: 0, y: 0, width: Self.W, height: sheetH),
                        colors: sheetColors, stops: sheetStops)

        // ② 右上角「完成」
        d.rightTextSqueezed("完成", right: doneRight, baseline: doneBase, size: doneSize,
                            UIColor.white, squeeze: squeeze)

        // ③ 支字标 + 三行大字
        d.art(mark, markRect)
        d.centerTextSqueezed("提交成功", cx: Self.W / 2 - 2, baseline: titleBase, size: titleSize,
                             UIColor.white, squeeze: squeeze)
        d.centerTextSqueezed("预计2小时内到账", cx: Self.W / 2 - 1, baseline: subBase, size: subSize,
                             subtle, squeeze: squeeze)
        d.centerTextSqueezed("¥" + Self.money(s.amount), cx: Self.W / 2 - 6, baseline: amountBase,
                             size: amountSize, UIColor.white, bold: true, squeeze: squeeze)

        // ④ 三行明细（左标签 + 右值）
        let rows: [(String, String)] = [
            ("到账银行卡", s.bank),
            ("提现金额", "¥" + Self.money(s.amount)),
            ("服务费", "本次提现免费"),
        ]
        for (i, row) in rows.enumerated() {
            d.textSqueezed(row.0, x: rowLabelX, baseline: rowBase[i], size: rowSize,
                           UIColor.white, squeeze: squeeze)
            d.rightTextSqueezed(row.1, right: rowValueRight, baseline: rowBase[i], size: rowSize,
                                UIColor.white, squeeze: squeeze)
        }

        // ⑤ 白卡：浅灰圆盘 + 橙色清单图标 + 两行字 + 箭头
        d.round(cardBox, radius: cardRadius, card)
        d.circle(cx: discCx, cy: discCy, r: discR, disc)
        drawIcon(d)
        d.textSqueezed("查看提现到账进度", x: cardTitleX, baseline: cardTitleBase,
                       size: cardTitleSize, cardInk, squeeze: squeeze)
        d.textSqueezed("可在「我的-账单」查看进度", x: cardTitleX + 1, baseline: cardSubBase,
                       size: cardSubSize, cardSub, squeeze: squeeze)
        drawChevronInBox(d, cardChevron)

        // ⑥ 底部按钮：左右渐变（先圆角裁剪再铺渐变，保证按钮是圆角的）
        d.clipped(to: button, radius: buttonRadius) {
            d.gradient(button, from: buttonL, to: buttonR, vertical: false)
        }
        d.centerTextSqueezed("完成", cx: button.midX, baseline: buttonTextBase,
                             size: buttonTextSize, UIColor.white, squeeze: squeeze)
    }

    /// 橙色的清单图标：一个圆角方块 + 三条白色横杠（Java:171-183）
    private func drawIcon(_ d: Draw) {
        d.round(iconRect, radius: iconRadius, orange)
        let l = iconRect.minX + 20
        let r = iconRect.maxX - 20
        for y in [iconRect.minY + 26, iconRect.midY, iconRect.maxY - 26] {
            d.round(CGRect(x: l, y: y - iconBarH / 2, width: r - l, height: iconBarH),
                    radius: iconBarR, UIColor.white)
        }
    }

    /// 粗圆头箭头（Java:216-230）
    private func drawChevronInBox(_ d: Draw, _ box: CGRect) {
        let inset = chevronStroke / 2
        d.ctx.setStrokeColor(chevronColor.cgColor)
        d.ctx.setLineWidth(chevronStroke)
        d.ctx.setLineCap(.round)
        d.ctx.setLineJoin(.round)
        d.ctx.move(to: CGPoint(x: box.minX + inset, y: box.minY + inset))
        d.ctx.addLine(to: CGPoint(x: box.maxX - inset, y: box.midY))
        d.ctx.addLine(to: CGPoint(x: box.minX + inset, y: box.maxY - inset))
        d.ctx.strokePath()
    }

    // MARK: - 命中（Java:186-192：后画的优先）

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        // 与 Java 一致：按「最后登记的矩形先判」的顺序
        let rects: [(CGRect, ActionToken)] = [
            (CGRect(x: doneRight - 160, y: doneBase - 60, width: 160, height: 78), "finish"),
            (cardBox, "progress"),
            (button, "finish"),
        ]
        for (rect, token) in rects.reversed() where rect.contains(CGPoint(x: x, y: y)) {
            return token
        }
        return "noop"
    }

    /// 金额格式化：`%.2f`（与 Android `ReceiptStore.money` 一致）
    static func money(_ value: Double) -> String {
        String(format: "%.2f", value)
    }
}
