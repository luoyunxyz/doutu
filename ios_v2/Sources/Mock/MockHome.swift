import UIKit

/// 「模拟」首页 —— `SimulationActivity` 的 1:1 移植。
///
/// 结构（自上而下）：手账横幅（`mock_bg` + 「时间9999999999」「设置」两颗胶囊）
/// → 31 格功能卡片（3 列，真实银行 logo）→ 「最近使用」。
///
/// 这一页不单独成一个 Screen：它画在 `HomeV2Screen` 的第 4 个 Tab 里，
/// 与 Android 把「模拟」做成底部 Tab 的交互一致。
enum MockHome {
    /// 页面宽（与 `HomeV2Screen.W` 一致）
    static let W: CGFloat = 1080

    /// `SimulationActivity.LABELS`
    static let labels: [String] = [
        "农业模拟", "工商模拟", "中国模拟",
        "邮政模拟", "招商模拟", "光大模拟",
        "兴业模拟", "建设模拟", "平安模拟",
        "交通模拟", "中信模拟", "浦发模拟",
        "民生模拟", "微信模拟", "支付宝",
        "汇旺模拟", "数字钱包", "抖音",
        "印度模拟", "短信模拟", "回单模拟",
        "流水模拟", "聊天模拟", "c币钱包",
        "云闪付", "TP钱包", "欧易模拟",
        "话费模拟", "农信", "qq模拟",
        "京东",
    ]

    /// `SimulationActivity.LOGOS`（空串表示没有 logo，用蓝底首字兜底）
    static let logos: [String] = [
        "abc", "icbc", "boc",
        "psbc", "cmb", "ceb",
        "cib", "ccb", "",
        "bcm", "citic", "bufa",
        "mingsheng", "wechat", "alipay",
        "hw", "shuzi", "douy",
        "yindu", "duanxin", "huidan",
        "liushui", "", "cbi",
        "", "", "",
        "", "nongxing", "qq",
        "jd",
    ]

    // MARK: - 几何（全部是 Android 的 dp × 3）

    static let statusTop = dp(44)
    static let padH = dp(10)
    static let bannerH = dp(136)
    static let cardH = dp(42)
    static let rowGap = dp(8)
    static let cellGap = dp(5)

    /// 三列卡片宽度
    static var cellW: CGFloat { (W - padH * 2 - cellGap * 2) / 3 }

    static var bannerRect: CGRect {
        CGRect(x: padH, y: statusTop, width: W - padH * 2, height: bannerH)
    }

    static var gridTop: CGFloat { bannerRect.maxY + dp(10) }

    static var rowCount: Int { (labels.count + 2) / 3 }

    static func cardRect(_ index: Int) -> CGRect {
        let row = index / 3
        let col = index % 3
        let x = padH + CGFloat(col) * (cellW + cellGap)
        let y = gridTop + CGFloat(row) * (cardH + rowGap)
        return CGRect(x: x, y: y, width: cellW, height: cardH)
    }

    static var gridBottom: CGFloat {
        gridTop + CGFloat(rowCount) * cardH + CGFloat(rowCount - 1) * rowGap
    }

    static var recentTitleTop: CGFloat { gridBottom + dp(18) }
    static var recentTitleSize: CGFloat { dp(15) }

    static var recentCardRect: CGRect {
        let titleH = Draw.font(size: recentTitleSize).lineHeight + dp(8)
        return CGRect(x: padH, y: recentTitleTop + titleH + dp(4),
                      width: W - padH * 2, height: dp(48))
    }

    /// 内容总高度（滚动范围用）
    static var contentHeight: CGFloat { recentCardRect.maxY + dp(24) }

    // MARK: - 绘制

    static func draw(_ d: Draw) {
        d.rect(CGRect(x: 0, y: 0, width: W, height: HomeV2Screen.H), MockInk.homePage)

        // 手账横幅（`mock_home/bg.jpg`，FIT_XY 铺满）
        d.art(UIImage(named: "mock_bg"), bannerRect)

        drawPills(d)

        for index in labels.indices {
            drawCard(d, index)
        }

        // 最近使用
        let titleSize = recentTitleSize
        let baseline = recentTitleTop + Draw.font(size: titleSize).ascender
        d.text("最近使用", x: padH + dp(4), baseline: baseline, size: titleSize, MockInk.title)

        let recent = recentCardRect
        d.round(recent, radius: dp(8), MockInk.white)
        let iconSize = dp(20)
        let textSize = dp(14)
        let textW = Draw.width("明细转出", size: textSize)
        let total = iconSize + dp(8) + textW
        let startX = (W - total) / 2
        d.art(UIImage(named: "mock_cib"),
              CGRect(x: startX, y: recent.midY - iconSize / 2, width: iconSize, height: iconSize))
        let textBaseline = recent.midY + Draw.font(size: textSize).ascender / 2 - textSize * 0.06
        d.text("明细转出", x: startX + iconSize + dp(8), baseline: textBaseline,
               size: textSize, MockInk.title)
    }

    /// 横幅底部那两颗胶囊：「时间9999999999」（绿）与「设置」（紫）
    private static func drawPills(_ d: Draw) {
        let size = dp(12)
        let padH = dp(10)
        let padV = dp(3)
        let lineH = Draw.font(size: size).lineHeight
        let h = lineH + padV * 2
        let timeText = "时间9999999999"
        let setText = "设置"
        let timeW = Draw.width(timeText, size: size) + padH * 2
        let setW = Draw.width(setText, size: size) + padH * 2
        let gapW = dp(8)
        let total = timeW + gapW + setW
        let x0 = (W - total) / 2
        let y = bannerRect.maxY - dp(58) - h

        let timeRect = CGRect(x: x0, y: y, width: timeW, height: h)
        let setRect = CGRect(x: x0 + timeW + gapW, y: y, width: setW, height: h)
        let green = Draw.color(0xff3a9d4a)
        let purple = Draw.color(0xffa53aa5)
        d.roundStroke(timeRect, radius: dp(3), green, width: dp(1))
        d.roundStroke(setRect, radius: dp(3), purple, width: dp(1))
        let baseline = y + padV + Draw.font(size: size).ascender
        d.text(timeText, x: timeRect.minX + padH, baseline: baseline, size: size, green)
        d.text(setText, x: setRect.minX + padH, baseline: baseline, size: size, purple)
    }

    /// 一张功能卡片：白底圆角 + logo（22dp）+ 标题（12sp）
    private static func drawCard(_ d: Draw, _ index: Int) {
        let rect = cardRect(index)
        d.round(rect, radius: dp(8), MockInk.white)

        let logoSize = dp(22)
        let logoX = rect.minX + dp(8)
        let logoY = rect.midY - logoSize / 2
        let logo = logos[index]
        if !logo.isEmpty, let image = MockAssets.named(logo) {
            d.art(image, CGRect(x: logoX, y: logoY, width: logoSize, height: logoSize))
        } else {
            // 没有 logo：蓝底 + 标题首字（`card()` 的 else 分支）
            d.circle(cx: logoX + logoSize / 2, cy: logoY + logoSize / 2,
                     r: logoSize / 2, MockInk.logoFallback)
            let label = labels[index]
            let first = label.isEmpty ? "?" : String(label.prefix(1))
            let size = dp(9)
            let baseline = logoY + logoSize / 2 + Draw.font(size: size).ascender / 2 - size * 0.06
            d.centerText(first, cx: logoX + logoSize / 2, baseline: baseline,
                         size: size, MockInk.white)
        }

        let textSize = dp(12)
        let baseline = rect.midY + Draw.font(size: textSize).ascender / 2 - textSize * 0.06
        d.text(labels[index], x: logoX + logoSize + dp(6), baseline: baseline,
               size: textSize, MockInk.title)
    }

    // MARK: - 命中

    /// 内容坐标（宿主已加上 `state.scroll`）
    static func hit(x: CGFloat, y: CGFloat) -> ActionToken {
        for index in labels.indices where cardRect(index).contains(CGPoint(x: x, y: y)) {
            return "mock:home:\(index)"
        }
        return "noop"
    }
}
