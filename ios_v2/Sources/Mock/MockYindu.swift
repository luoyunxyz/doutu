import UIKit

/// 「模拟 → 印度模拟」的子页版式（宫格索引 18）。
///
/// 4 个子页：`转出1 / 转出2 / 转出3 / 转出4`。
/// 0 号是 UPI 的「Money Sent」页（英文）：金额 + 绿色对勾、TO/FROM 两段银行信息、
/// UPI RefenceID、Repeat Payment 按钮、底部「Recent Payments with Bank」。
enum MockYindu {

    /// 绿色对勾
    static let green = UIColor(red: 0x22 / 255.0, green: 0xa5 / 255.0,
                               blue: 0x6a / 255.0, alpha: 1)
    /// 顶栏浅灰
    static let headGrey = UIColor(red: 0xf4 / 255.0, green: 0xf5 / 255.0,
                                  blue: 0xf7 / 255.0, alpha: 1)
    /// 按钮青
    static let teal = UIColor(red: 0x2f / 255.0, green: 0x6f / 255.0,
                              blue: 0x6f / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            moneySent(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .yindu, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func moneySent(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转出1") }

        // 顶部浅灰块：返回 / 分享 / Money Sent / 金额 / 时间
        b.fill(dp(300), headGrey)
        b.sectionLabel("←                                             ⤴",
                       size: dp(20), color: MockInk.title,
                       padH: dp(16), padTop: dp(14), padBottom: 0)
        b.sectionLabel("Money Sent", size: dp(22), color: MockInk.title,
                       padH: dp(16), padTop: dp(26), padBottom: 0)
        b.sectionLabel(s.value("金额", "₹10,001"), size: dp(44), color: MockInk.black,
                       padH: dp(16), padTop: dp(8), padBottom: 0)
        b.sectionLabel(s.value("时间", "04 Nov 2021,01:27"), size: dp(15),
                       color: MockInk.label, padH: dp(16), padTop: dp(12), padBottom: dp(20))

        // TO / FROM
        b.sectionLabel("TO", size: dp(14), color: MockInk.label,
                       padH: dp(16), padTop: dp(22), padBottom: 0)
        b.sectionLabel("Bank Of India", size: dp(18), color: MockInk.title,
                       padH: dp(16), padTop: dp(6), padBottom: 0)
        b.sectionLabel("A/c No.XXXXXXXXX1487", size: dp(16), color: MockInk.title,
                       padH: dp(16), padTop: dp(4), padBottom: 0)

        b.sectionLabel("FROM", size: dp(14), color: MockInk.label,
                       padH: dp(16), padTop: dp(18), padBottom: 0)
        b.sectionLabel("Union Bank Of India", size: dp(18), color: MockInk.title,
                       padH: dp(16), padTop: dp(6), padBottom: 0)
        b.sectionLabel("A/c No.XXXX XXXX XXX5 974", size: dp(16), color: MockInk.title,
                       padH: dp(16), padTop: dp(4), padBottom: 0)

        b.divider(dp(16))
        b.sectionLabel(s.value("UPI RefenceID", "UPI RefenceID 00156384562"), size: dp(15),
                       color: MockInk.label, padH: dp(16), padTop: dp(10), padBottom: 0)

        // Repeat Payment
        b.fullButton("Repeat Payment", filled: false, color: teal,
                     height: dp(46), padH: dp(16), gapTop: dp(22), size: dp(17))
        b.sectionLabel("↺  Recent Payments with Bank  ›", size: dp(17),
                       color: MockInk.title, padH: dp(16), padTop: dp(30), padBottom: dp(20))
        _ = green
    }
}
