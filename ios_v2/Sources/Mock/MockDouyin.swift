import UIKit

/// 「模拟 → 抖音」的子页版式（宫格索引 17）。
///
/// 4 个子页：`支付 / 零钱明细 / 支付-零钱 / 支付2`。
/// 0 号是「支付成功」：抖音 logo、支付成功、金额、支付方式（抖音支付）、完成按钮。
/// 页面背景是淡紫到白的柔和渐变，这里用纯白 + 顶部淡紫块近似。
enum MockDouyin {

    /// 顶部淡紫（截图取样）
    static let lavender = UIColor(red: 0xee / 255.0, green: 0xee / 255.0,
                                  blue: 0xf8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            paid(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .douyin, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func paid(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("支付成功") }

        // 顶部淡紫块
        b.fill(dp(430), lavender)

        // logo + 支付成功 + 金额
        b.sectionLabel("♪", size: dp(46), color: MockInk.black,
                       padH: 0, padTop: dp(20), padBottom: 0)
        b.sectionLabel("支付成功", size: dp(18), color: MockInk.title,
                       padH: 0, padTop: dp(8), padBottom: 0)
        b.sectionLabel(s.value("金额", "¥ 29.00"), size: dp(44), color: MockInk.black,
                       padH: 0, padTop: dp(10), padBottom: 0)

        // 支付方式
        b.sectionLabel("支付方式                                     ♪ 抖音支付",
                       size: dp(15), color: MockInk.label,
                       padH: dp(16), padTop: dp(460), padBottom: 0)

        // 完成
        b.gap(dp(330))
        b.fullButton("完成", filled: false, color: MockInk.line,
                     height: dp(46), padH: dp(60), gapTop: dp(10), size: dp(17))
    }
}
