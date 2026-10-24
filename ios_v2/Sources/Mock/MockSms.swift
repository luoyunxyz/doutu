import UIKit

/// 「模拟 → 短信」的子页版式（宫格索引 19）。
///
/// 只有 1 个子页：`短信模版`。
/// 页面是一条银行短信：← + 两行标题（中国银行 / 95566）+ ⋮、
/// 时间行、灰底短信卡（账户入账 / 收入金额 / 金额 / 两列四项）、底部输入栏。
enum MockSms {

    /// 灰底短信卡
    static let bubble = UIColor(red: 0xf5 / 255.0, green: 0xf6 / 255.0,
                                blue: 0xf8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        _ = state
        if showsTopTitle { b.titleBar("短信模版") }
        thread(b)
        return b.result
    }

    private static func thread(_ b: MockBuilder) {
        // 顶栏（两行标题）
        b.sectionLabel("←", size: dp(22), color: MockInk.title,
                       padH: dp(16), padTop: dp(12), padBottom: 0)
        b.sectionLabel("中国银行", size: dp(19), color: MockInk.title,
                       padH: 0, padTop: dp(4), padBottom: 0)
        b.sectionLabel("95566", size: dp(15), color: MockInk.label,
                       padH: 0, padTop: dp(2), padBottom: dp(16))

        // 时间行
        b.sectionLabel("2 7-28 上午9:10", size: dp(14), color: MockInk.label,
                       padH: dp(16), padTop: dp(6), padBottom: dp(8))

        // 短信卡
        b.panel(gapTop: 0, margin: dp(12), pad: dp(16), bg: bubble) { p in
            p.sectionLabel("账户入账", size: dp(17), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(10))
            p.sectionLabel("收入金额", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: 0, padBottom: 0)
            p.sectionLabel("+ 200000.00元", size: dp(28), color: MockInk.black,
                           padH: 0, padTop: dp(6), padBottom: dp(14))
            p.row("账户信息", "交易时间")
            p.sectionLabel("中国银行*1754                          07月28日",
                           size: dp(17), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(12))
            p.row("交易描述", "账号余额")
            p.sectionLabel("手机银行收入                            2000.00",
                           size: dp(17), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: 0)
        }

        // 底部输入栏
        b.gap(dp(300))
        b.sectionLabel("⌨   中国银行", size: dp(17), color: MockInk.title,
                       padH: dp(16), padTop: dp(12), padBottom: dp(12))
    }
}
