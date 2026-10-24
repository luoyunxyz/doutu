import UIKit

/// 「模拟 → 邮政模拟」的八个子页版式。
///
/// 邮储的编辑页与成品页**共用同一份版式**：两边都调 `PsbcPages`，
/// 成品页只是把 `editable` 传成 false（值不能点改）。所以这里只有一套布局，
/// 编辑页多出来的只有「点文字改值」这一层（由 `MockBuilder` 的 hits 负责）。
///
/// 子页映射（`PsbcActivity.buildPreview()`）：
///   0 转账结果 · 1/2/6 交易详情 · 3/5 汇款明细查询 · 4 转账记录 · 7 电子受理单
enum MockPsbc {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            result(b, state, showsTopTitle: showsTopTitle)
        case 3:
            receipt(b, state, showsTopTitle: showsTopTitle)
        case 4:
            record(b, state, showsTopTitle: showsTopTitle)
        default:
            psbcV2(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    // MARK: - 0 转账结果

    private static func result(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("转账结果", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.checkBadge(diameter: dp(90), color: Draw.color(0xff58c04a),
                     gapTop: dp(30), symbolSize: dp(40))
        b.centerBlock("金额与状态", s.value("金额与状态", "¥100.00 转账成功"),
                      size: dp(19), color: Draw.color(0xff1fa02a), bold: true,
                      padTop: dp(20), padBottom: dp(16))
        b.dualLinks("继续转账", "转账记录", size: dp(15),
                    color: Draw.color(0xff1fa02a),
                    separatorColor: Draw.color(0xffdddddd), padBottom: dp(24))
        b.hairlineFull()

        let rows: [(String, String)] = [
            ("付款账号：", "6221***8142"),
            ("收款人：", "周虞虎"),
            ("收款账号：", "6217 9958 0020 8094 158"),
            ("开户行：", "中国邮政储蓄银行"),
            ("手续费：", "¥ 0.00"),
            ("受理方式：", "实时"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 1/2/6 交易详情

    private static func txDetail(_ b: MockBuilder, _ s: MockState, sub: Int,
                                 showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("交易详情", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.centerBlock("金额", s.value("金额", "+ ¥ 100.00"),
                      size: dp(34), color: Draw.color(0xff222222),
                      padTop: dp(34), padBottom: dp(6))
        // 子页 2 的余额文案里多一个空格（Java:106 的两个分支）
        let balance = sub == 2 ? "账户余额 ¥ 10,129.50" : "账户余额¥10,129.50"
        b.centerBlock("账户余额", s.value("账户余额", balance),
                      size: dp(15), color: MockInk.label,
                      padTop: 0, padBottom: dp(30))

        let rows: [(String, String)]
        if sub == 1 {
            rows = [
                ("交易卡号", "6226***9990"),
                ("交易时间", s.time),
                ("摘要", "他行来账"),
                ("付款人", "李强"),
                ("付款账号", "622143551800063640"),
                ("付款行名", "中国工商银行"),
                ("附言", "手机转账"),
            ]
        } else if sub == 2 {
            rows = [
                ("交易卡号", "6226 **** 9990"),
                ("交易时间", s.time),
                ("摘要", "付款"),
                ("付款人", "李强"),
                ("付款账号", "6226 **** 9995"),
                ("付款行名", "中国工商银行"),
                ("附言", "张三支付宝转账"),
            ]
        } else {
            rows = [
                ("交易卡号", "6226 **** 9990"),
                ("交易时间", s.time),
                ("摘要", "转入"),
                ("付款人", "李强"),
                ("付款账号", "6226 **** 9995"),
                ("付款行名", "中国工商银行"),
                ("附言", "跨行转入"),
            ]
        }
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 4 转账记录

    private static func record(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("转账记录", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.centerBlock("金额", s.value("金额", "- ¥ 521.05"),
                      size: dp(34), color: Draw.color(0xff222222),
                      padTop: dp(34), padBottom: dp(6))
        b.centerBlock("账户余额", s.value("账户余额", "账户余额 ¥ 521.05"),
                      size: dp(15), color: MockInk.label,
                      padTop: 0, padBottom: dp(30))

        let rows: [(String, String)] = [
            ("交易卡号", "6217 **** 7856"),
            ("交易时间", s.time),
            ("摘要", "转账汇出"),
            ("收款人", "付泰"),
            ("收款账号", "6217 **** 3918"),
            ("收款银行", "中国邮政储蓄银行"),
            ("附言", "跨行转账"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 3/5 汇款明细查询

    private static func remit(_ b: MockBuilder, _ s: MockState, big: String,
                              showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("汇款明细查询", color: MockInk.value, trailing: "  ⌂ ")
        }
        let rows: [(String, String)] = [
            ("交易日期", s.time),
            ("付款人", "李强"),
            ("付款账号", "6226 0222 0000 0646 9990"),
            ("收款人", "张大囯"),
            ("收款账号", "6226 0222 0000 0646 9990"),
            ("收款银行", "中国工商银行"),
            ("转账金额", "¥" + big),
            ("手续费", "¥0.00"),
            ("交易状态", "交易成功"),
            ("受理方式", "实时"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 7 电子受理单

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("电子受理单", color: MockInk.value, trailing: "  ⌂ ")
        }
        let rows: [(String, String)] = [
            ("受理单号", "2023021298473050"),
            ("转账金额", "¥ 100.00"),
            ("金额大写", "壹仟元"),
            ("手续费", "¥ 0.00"),
            ("收款人姓名", "李哈哈"),
            ("收款人账号", "6226 **** 3946"),
            ("收款人银行", "中国工商银行"),
            ("交易类型", "实时跨行转账"),
        ]
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(10),
                bg: Draw.color(0xffdbe6fb)) { inner in
            inner.gap(dp(6))
            inner.brandRow(logo: "mock_psbc_logo", logoHeight: dp(24),
                           qr: "mock_psbc_qr",
                           qrSize: CGSize(width: dp(36), height: dp(44)),
                           padH: dp(14))
            for (label, fallback) in rows {
                inner.pairRow(label, s.value(label, fallback), size: dp(15),
                              padH: dp(14), padV: dp(11))
            }
        }
    }

    // MARK: - 1/2/5/6/7 交易详情（安卓 03_01/02/05/06/07 实测）

    private static func psbcV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let sub = s.sub
        if showsTopTitle { b.headerBar("交易详情", color: MockInk.value, trailing: "  ⌂ ") }

        let padH: CGFloat = (sub == 1 || sub == 2) ? dp(24) : (sub == 7 ? dp(28) : dp(33))
        if sub == 5 || sub == 6 {
            b.sectionLabel(s.value("户名", "张三"), size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
        }

        let amount: String
        let amountSize: CGFloat
        let balance: String
        switch sub {
        case 1:
            amount = "+ ¥ 100.00"; amountSize = dp(28); balance = "账户余额¥10,129.50"
        case 2:
            amount = "+ ¥ 100.00"; amountSize = dp(24); balance = "账户余额 ¥ 10,129.50"
        case 6:
            amount = "-￥521.05"; amountSize = dp(21); balance = "余额￥521.05"
        case 7:
            amount = "-￥521.05"; amountSize = dp(21); balance = "账户余额￥521.05"
        default:
            amount = "+￥521.05"; amountSize = dp(21); balance = "余额￥51.05"
        }
        b.sectionLabel(s.value("金额", amount), size: amountSize, color: MockInk.title,
                       padH: padH, padTop: sub == 5 || sub == 6 ? dp(8) : dp(24),
                       padBottom: 0)
        b.sectionLabel(balance, size: dp(12), color: Draw.color(0xff898989),
                       padH: padH, padTop: dp(4), padBottom: dp(8))

        let size = sub == 1 ? dp(13) : dp(14)
        let padV = sub == 1 ? dp(5.87) : (sub == 2 ? dp(5.77) : dp(11.77))
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: padH, padV: padV,
                      labelColor: Draw.color(0xff898989), valueColor: MockInk.title)
        }

        switch sub {
        case 1:
            row("交易卡号", "6226***9990")
            row("交易时间", s.time)
            row("摘要", "他行来账")
            row("付款人", "李强")
            row("付款账号", "622143551800063640")
            row("付款行名", "中国工商银行")
            row("附言", "手机转账")
        case 2:
            row("交易卡号", "6226 **** 9990")
            row("交易时间", s.time)
            row("摘要", "付款")
            row("付款人", "李强")
            row("付款账号", "6226 **** 9995")
            row("付款行名", "中国工商银行")
            row("附言", "张三支付宝转账")
        case 6:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("收款账号", "6217 **** 3918")
            row("收款银行", "中国农业银行股份有限公司")
            row("附言", "付款")
            row("摘要", "跨行汇出")
            b.sectionLabel("交易类型", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("不计入本月收支", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(24), padBottom: 0)
        case 7:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("摘要", "转账汇出")
            row("收款人", "付泰")
            row("收款账号", "6217 **** 3918")
            row("收款银行", "中国邮政储蓄银行")
            row("附言", "转账")
        default:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("付款账号", "6217 **** 3918")
            row("附言", "无附言")
            row("摘要", "他行汇入")
            b.sectionLabel("交易类型", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("所属账本", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("不计入本月收支", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("附注", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("记录点什么...", size: dp(14), color: Draw.color(0xffaeaeaf),
                           padH: padH, padTop: dp(6), padBottom: 0)
        }
    }
}
