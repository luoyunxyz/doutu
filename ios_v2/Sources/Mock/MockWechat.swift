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
        let names = MockModule.wechat.subs
        let title = names.indices.contains(state.sub) ? names[state.sub] : "支付成功"
        switch state.sub {
        case 1, 2, 3:
            // 扫码转出1/2、明细(转出)：转账结果式页面
            transfer(b, s: state, title: title, showsTopTitle: showsTopTitle)
        case 4:
            // 消息盒子：消息列表
            if showsTopTitle { b.titleBar("消息盒子") }
            b.coloredHeaderBar("消息盒子", bg: .white, fg: MockInk.title)
            b.row("微信支付", "支付成功 ¥29.00")
            b.row("微信收款助手", "收款到账 ¥100.00")
            b.row("服务通知", "零钱提现到账")
            b.gap(dp(20))
        case 5, 6:
            // 收支明细 / 扫码捐款：明细列表
            if showsTopTitle { b.titleBar(title) }
            b.coloredHeaderBar(title, bg: .white, fg: MockInk.title)
            b.sectionLabel("本月支出  ¥ 1,286.40", size: dp(30), color: MockInk.black,
                           padH: dp(16), padTop: dp(20), padBottom: dp(10))
            b.row("微信支付-消费", "-29.00")
            b.row("扫码捐款", "-1.00")
            b.row("赞赏码收款", "+50.00")
            b.gap(dp(20))
        case 7, 8, 9, 10, 11:
            // 扫码收款 / 赞赏码转出·转入 / 商户转出：收款码式页面
            if showsTopTitle { b.titleBar(title) }
            b.coloredHeaderBar(title, bg: .white, fg: MockInk.title)
            b.sectionLabel("¥ 100.00", size: dp(40), color: MockInk.black,
                           padH: 0, padTop: dp(60), padBottom: dp(10))
            b.sectionLabel("请向商家付款", size: dp(15), color: MockInk.label,
                           padH: 0, padTop: 0, padBottom: dp(20))
            b.gap(dp(200))
        default:
            paid(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    /// 扫码转出 / 明细(转出)：绿勾 + 金额 + 对方 + 完成
    private static func transfer(_ b: MockBuilder, s: MockState, title: String,
                                 showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar(title) }
        b.coloredHeaderBar(title, bg: .white, fg: MockInk.title, trailing: "完成")
        b.gap(dp(24))
        b.checkBadge(diameter: dp(56), color: green, gapTop: 0, symbolSize: dp(32))
        b.sectionLabel("支付成功", size: dp(20), color: green,
                       padH: 0, padTop: dp(10), padBottom: 0)
        b.sectionLabel(s.value("金额", "¥ 100.00"), size: dp(40), color: MockInk.black,
                       padH: 0, padTop: dp(10), padBottom: dp(10))
        b.row("当前状态", "支付成功")
        b.row("支付方式", "零钱")
        b.row("交易时间", s.time)
        b.gap(dp(20))
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
