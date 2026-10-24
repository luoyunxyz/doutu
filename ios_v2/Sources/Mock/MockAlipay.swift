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
        // 子页分支里统一用 `s` 指代 state（与 transfer/billDetail 辅助函数的参数名一致）
        let s = state
        let names = MockModule.alipay.subs
        let title = names.indices.contains(state.sub) ? names[state.sub] : "支付成功"
        switch state.sub {
        case 1, 2, 3, 4, 5:
            // 扫码转出2 / 对行转出 / 对支付宝转出 / 话费转出 / 明细(转出)
            transfer(b, s: state, title: title, showsTopTitle: showsTopTitle)
        case 6:
            // 消息盒子：账单详情（按 `ali_sub6` 截图复刻）
            if showsTopTitle { b.titleBar("账单详情") }
            b.sectionLabel("‹  账单详情", size: dp(19), color: MockInk.title,
                           padH: dp(16), padTop: dp(12), padBottom: dp(8))
            b.panel(gapTop: dp(6), margin: dp(12), pad: dp(14), bg: .white) { p in
                p.sectionLabel("■", size: dp(36), color: UIColor(red: 0xe6 / 255.0,
                                                                green: 0x5a / 255.0,
                                                                blue: 0x1e / 255.0, alpha: 1),
                               padH: 0, padTop: 0, padBottom: dp(4))
                p.sectionLabel(s.value("名称", "风筝"), size: dp(20), color: MockInk.title,
                               padH: 0, padTop: 0, padBottom: dp(4))
                p.sectionLabel(s.value("金额", "-100.00"), size: dp(44), color: MockInk.black,
                               padH: 0, padTop: dp(4), padBottom: dp(4))
                p.sectionLabel("交易成功", size: dp(15), color: MockInk.label,
                               padH: 0, padTop: 0, padBottom: dp(12))
                p.row("付款方式", "花呗 ›")
                p.row("支付宝积分", "立即领取25积分(已翻5倍)")
                p.row("商品说明", "收钱码收款")
                p.row("收款方全称", "**岚(个人)")
                p.row("支付日期", s.time)
                p.row("订单号", "2022100822001454681448487984")
            }
            b.panel(gapTop: dp(12), margin: dp(12), pad: dp(14), bg: .white) { p in
                p.chevronRow("联系收款方", size: dp(16), color: MockInk.title,
                             padH: 0, padV: dp(14))
                p.chevronRow("账单分类          其他", size: dp(16), color: MockInk.title,
                             padH: 0, padV: dp(14))
                p.chevronRow("标签和备注          添加", size: dp(16), color: MockInk.title,
                             padH: 0, padV: dp(14))
                p.chevronRow("查看往来记录", size: dp(16), color: MockInk.title,
                             padH: 0, padV: dp(14))
            }
        case 8, 9, 10, 11:
            // 扫码收款1/2、商户转出1/2：收支详情（按 `ali_sub8` 截图复刻）
            if showsTopTitle { b.titleBar(title) }
            b.sectionLabel("‹  收支详情", size: dp(19), color: MockInk.title,
                           padH: dp(16), padTop: dp(12), padBottom: dp(8))
            b.sectionLabel(s.value("说明", "收钱码收款-来自**容"), size: dp(20),
                           color: MockInk.title, padH: 0, padTop: dp(20), padBottom: 0)
            b.sectionLabel(s.value("金额", "+100.00"), size: dp(44), color: MockInk.black,
                           padH: 0, padTop: dp(8), padBottom: dp(20))
            b.row("类型", "收入")
            b.hairlineFull()
            b.row("时间", s.time)
            b.hairlineFull()
            b.row("余额", "100.00")
            b.hairlineFull()
            b.row("流水号", "2022092622001463831445460044")
            b.hairlineFull()
            b.row("备注", "收钱码收款-来自**容")
            b.gap(dp(20))
        case 7:
            // 收支明细：交易详情（与微信同结构）
            if showsTopTitle { b.titleBar("交易详情") }
            b.sectionLabel("‹            交易详情            •••  ◎",
                           size: dp(17), color: MockInk.title,
                           padH: dp(16), padTop: dp(12), padBottom: dp(6))
            b.sectionLabel(s.value("昵称", "*果"), size: dp(17), color: MockInk.title,
                           padH: 0, padTop: dp(30), padBottom: 0)
            b.sectionLabel(s.value("金额", "¥ 100.00"), size: dp(44), color: MockInk.black,
                           padH: 0, padTop: dp(10), padBottom: dp(24))
            b.hairlineFull()
            b.row("当前状态", "已收钱")
            b.row("收款时间", s.time)
            b.row("收款单号", "62260222000006469990")
            b.fill(dp(10), MockInk.page)
            b.sectionLabel("交易记录", size: dp(16), color: MockInk.label,
                           padH: dp(16), padTop: dp(14), padBottom: dp(8))
            b.sectionLabel("以上是近90天的交易记录", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: dp(6), padBottom: dp(20))
        default:
            // 子页 0：支付成功（蓝色大额 + 福利卡）
            paid(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    /// 转账/话费/明细(转出)：蓝块「到账成功」+ 白色通知卡 + 完成
    /// （按 `ali_sub2` 截图复刻）
    private static func transfer(_ b: MockBuilder, s: MockState, title: String,
                                 showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar(title) }

        // 蓝块：支 / 到账成功 / 金额 / 三行
        b.panel(gapTop: 0, margin: 0, pad: dp(16), bg: blue) { p in
            p.sectionLabel("支                     到账成功                      完成",
                           size: dp(17), color: .white,
                           padH: 0, padTop: dp(8), padBottom: dp(6))
            p.sectionLabel(s.value("金额", "¥ 100.00"), size: dp(44), color: .white,
                           padH: 0, padTop: dp(10), padBottom: dp(14))
            p.row("中国邮政储蓄...(2680)张三号", "¥ 100.00")
            p.row("交易方式", "余额宝(转出资金付款)")
            p.row("到账时间", s.time)
        }

        // 白色通知卡
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.sectionLabel("立即通知收款人", size: dp(16), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(10))
            p.sectionLabel("使用转账凭证            信息准确，对账无忧        去通知",
                           size: dp(15), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(4))
        }

        // 完成
        b.gap(dp(320))
        b.fullButton("完成", filled: true, color: blue,
                     height: dp(48), padH: dp(90), gapTop: dp(10), size: dp(18))
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
