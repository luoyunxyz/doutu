import UIKit

/// 「模拟 → 支付宝模拟」的子页版式（入口 14）。
///
/// 12 个子页（设备抽屉）：`扫码转出1 / 扫码转出2 / 对行转出 / 对支付宝转出 / 话费转出 /
/// 明细(转出) / 消息盒子 / 收支明细 / 扫码收款1 / 扫码收款2 / 商户转出1 / 商户转出2`。
/// 0 号是「支付成功」：蓝色区（支 logo + 支付成功 + 金额 + 付款方/交易方式）+
/// 白卡「支付有福利」五条 + 底部蓝色「完成」。
enum MockAlipay {

    /// 支付宝蓝（截图取样）
    static let blue = UIColor(red: 0x16 / 255.0, green: 0x77 / 255.0,
                              blue: 0xff / 255.0, alpha: 1)
    /// 福利条目的红
    static let promoRed = UIColor(red: 0xf5 / 255.0, green: 0x2b / 255.0,
                                  blue: 0x2b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            paid(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .alipay, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func paid(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("支付成功") }

        // 蓝色区：支付成功 + 金额 + 付款方/交易方式
        b.panel(gapTop: dp(6), margin: 0, pad: dp(16), bg: blue) { p in
            p.sectionLabel("支  支付成功", size: dp(17), color: .white,
                           padH: 0, padTop: 0, padBottom: dp(6))
            p.sectionLabel(s.value("金额", "¥ 100.00"), size: dp(40), color: .white,
                           padH: 0, padTop: dp(10), padBottom: dp(14))
            p.row("李哈哈", "¥ 100.00")
            p.row("交易方式", "余额宝(转出资金付款)")
        }

        // 支付有福利
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.sectionLabel("支付有福利", size: dp(17), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(8))
            for title in ["1.5元 100元话费充值券        免费领",
                          "租苹果新机太省啦           去看看",
                          "10元 拼车券                免费领",
                          "100个 福利金可抵扣现金      免费领",
                          "10亿 到店红包免费发放        立即抢"] {
                p.chevronRow(title, size: dp(15), color: promoRed,
                             padH: 0, padV: dp(14))
            }
        }

        // 完成
        b.gap(dp(120))
        b.fullButton("完成", filled: true, color: blue,
                     height: dp(48), padH: dp(40), gapTop: dp(10), size: dp(18))
    }
}
