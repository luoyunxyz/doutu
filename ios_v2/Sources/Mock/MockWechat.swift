import UIKit

/// 「模拟 → 微信模拟」的子页版式（入口 13）。
///
/// 子页很多（设备抽屉里 12 项以上）：扫码转出1 / 扫码转出2 / 扫码转出2 /
/// 明细(转出) / 消息盒子 / 收支明细 / 扫码捐款 / 扫码收款 / 赞赏码转出 /
/// 赞赏码转出2 / 赞赏码转入 / 商户转出1 …
/// 0 号是「支付成功」页：绿勾标题、头像、昵称、金额、`1分钱做好事`、完成按钮。
enum MockWechat {

    /// 微信绿
    static let green = UIColor(red: 0x07 / 255.0, green: 0xc1 / 255.0,
                               blue: 0x60 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            paid(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .wechat, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func paid(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("支付成功") }

        // 绿勾 + 支付成功
        b.gap(dp(24))
        b.checkBadge(diameter: dp(30), color: green, gapTop: 0, symbolSize: dp(18))
        b.sectionLabel("支付成功", size: dp(20), color: green,
                       padH: 0, padTop: dp(8), padBottom: 0)

        // 头像 / 昵称 / 金额
        b.centeredAvatar(s.value("昵称", "李哈哈"), asset: nil,
                         diameter: dp(80), gapTop: dp(48))
        b.sectionLabel(s.value("昵称", "李哈哈(*要)"), size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(12), padBottom: 0)
        b.sectionLabel(s.value("金额", "¥ 100.00"), size: dp(40), color: MockInk.title,
                       padH: 0, padTop: dp(10), padBottom: 0)

        // 1分钱做好事
        b.gap(dp(430))
        b.sectionLabel("❀ 1分钱做好事 ›", size: dp(15), color: MockInk.label,
                       padH: 0, padTop: 0, padBottom: dp(16))

        // 完成
        b.fullButton("完成", filled: false, color: MockInk.line,
                     height: dp(46), padH: dp(120), gapTop: dp(6), size: dp(17))
    }
}
