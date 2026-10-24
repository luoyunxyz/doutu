import UIKit

/// 「模拟 → c币钱包」的子页版式（宫格索引 23）。
///
/// 只有 1 个子页：`c币钱包`。
/// 页面是「交易流水详情」：标题 + 28sp「接口支付」+ 四行明细（数量/订单号/日期/商家名称）。
enum MockCbi {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        _ = state
        if showsTopTitle { b.titleBar("交易流水详情") }
        detail(b)
        return b.result
    }

    private static func detail(_ b: MockBuilder) {
        // 大标题
        b.sectionLabel("接口支付", size: dp(28), color: MockInk.title,
                       padH: dp(16), padTop: dp(24), padBottom: dp(6))

        // 四行明细
        b.row("数量：", "-100")
        b.row("订单号：", "20231010210027bFMDD1")
        b.row("日期：", "2026-10-24 19:27:41")
        b.row("商家名称：", "c币钱包")
        b.gap(dp(30))
    }
}
