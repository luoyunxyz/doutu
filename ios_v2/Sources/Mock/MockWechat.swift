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
        case 6, 7, 8, 9, 10, 11:
            // 扫码捐款 / 扫码收款 / 赞赏码转出·转出2·转入 / 商户转出1：
            // 都是「账单详情」一族（按 `wx_sub6`、`wx_sub8` 两张截图复刻）
            let status: String
            let remark: String
            let timeLabel: String
            switch state.sub {
            case 8, 9, 10: status = "朋友已收钱"; remark = "赞赏码"; timeLabel = "支付时间"
            case 11: status = "商户已收款"; remark = "商户收款"; timeLabel = "支付时间"
            default: status = "支付成功"; remark = "二维码收款"; timeLabel = "转账时间"
            }
            billDetail(b, s: state, title: title, status: status,
                       remark: remark, timeLabel: timeLabel,
                       showsTopTitle: showsTopTitle)
        case 4:
            // 消息盒子：微信支付空页（按 `wx_sub4` 截图复刻）
            if showsTopTitle { b.titleBar("微信支付") }
            b.sectionLabel("‹            微信支付            ⌕   ⚙",
                           size: dp(18), color: MockInk.title,
                           padH: dp(16), padTop: dp(14), padBottom: dp(10))
            b.fill(dp(760), MockInk.page)
            b.sectionLabel("⌨        我的账单        ｜        支付服务",
                           size: dp(15), color: MockInk.title,
                           padH: dp(16), padTop: dp(14), padBottom: dp(16))
        case 5:
            // 收支明细：本月支出 + 三条流水
            if showsTopTitle { b.titleBar(title) }
            b.coloredHeaderBar(title, bg: .white, fg: MockInk.title)
            b.sectionLabel("本月支出  ¥ 1,286.40", size: dp(30), color: MockInk.black,
                           padH: dp(16), padTop: dp(20), padBottom: dp(10))
            b.row("微信支付-消费", "-29.00")
            b.row("扫码捐款", "-1.00")
            b.row("赞赏码收款", "+50.00")
            b.gap(dp(20))
        default:
            paid(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    /// 账单详情一族：✕、图标、名称、金额、五行明细、账单服务、财付通署名
    private static func billDetail(_ b: MockBuilder, s: MockState, title: String,
                                   status: String, remark: String, timeLabel: String,
                                   showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar(title) }
        b.sectionLabel("✕                                       全部账单",
                       size: dp(16), color: MockInk.title,
                       padH: dp(16), padTop: dp(12), padBottom: dp(0))
        b.gap(dp(24))
        b.checkBadge(diameter: dp(64),
                     color: UIColor(red: 0xf5 / 255.0, green: 0xc0 / 255.0,
                                    blue: 0x2a / 255.0, alpha: 1),
                     gapTop: dp(10), symbolSize: dp(36))
        b.sectionLabel(s.value("说明", title), size: dp(20),
                       color: MockInk.title, padH: 0, padTop: dp(14), padBottom: 0)
        b.sectionLabel(s.value("金额", "-100.00"), size: dp(44),
                       color: MockInk.black, padH: 0, padTop: dp(12), padBottom: dp(20))
        b.hairlineFull()
        b.row("当前状态", status)
        b.row("收款方备注", remark)
        b.row(timeLabel, s.time)
        b.row("支付方式", "零钱")
        b.row("转账单号", "10000499012022092600801723318157")
        b.fill(dp(10), MockInk.page)
        b.sectionLabel("账单服务", size: dp(16), color: MockInk.title,
                       padH: dp(16), padTop: dp(14), padBottom: dp(8))
        b.centeredCells(["对订单有疑惑", "发起群收款"], height: dp(28), size: dp(15), padV: dp(8))
        b.sectionLabel("本服务由财付通提供", size: dp(13), color: MockInk.label,
                       padH: 0, padTop: dp(20), padBottom: dp(20))
    }

    /// 扫码转出1/2、明细(转出)：顶部「微信支付」通知卡 + 头像昵称 + 金额 + 完成
    /// （按 `wx_sub3` 截图复刻）
    private static func transfer(_ b: MockBuilder, s: MockState, title: String,
                                 showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar(title) }

        // 通知卡：微信支付 / 已支付 + 右侧绿图标 + 副行
        b.panel(gapTop: dp(10), margin: dp(10), pad: dp(14), bg: .white) { p in
            p.sectionLabel("微信支付                         ✓",
                           size: dp(17), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(6))
            p.sectionLabel("[1条]微信支付：已支付 ￥0.02", size: dp(15),
                           color: MockInk.label, padH: 0, padTop: 0, padBottom: 0)
        }

        // 头像 / 昵称 / 金额
        b.centeredAvatar(s.value("昵称", "李哈哈"), asset: nil,
                         diameter: dp(80), gapTop: dp(36))
        b.sectionLabel(s.value("昵称", "李哈哈(*要)"), size: dp(17),
                       color: MockInk.title, padH: 0, padTop: dp(12), padBottom: 0)
        b.sectionLabel(s.value("金额", "¥ 100.00"), size: dp(44), color: MockInk.black,
                       padH: 0, padTop: dp(10), padBottom: 0)

        // 完成
        b.gap(dp(430))
        b.fullButton("完成", filled: false, color: MockInk.line,
                     height: dp(46), padH: dp(120), gapTop: dp(10), size: dp(17))
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
