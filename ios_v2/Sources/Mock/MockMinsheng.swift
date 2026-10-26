import UIKit

/// 「模拟 → 民生模拟」的子页版式（入口 12）。
///
/// 3 个子页（设备抽屉）：`明细转出 / 明细转入 / 明细转入支付宝`。
/// 0 号是「交易详情」：白卡金额 + 六行明细，下面单独一张「分类」卡。
enum MockMinsheng {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "m0_0", fallback: "", rect: CGRect(x: 393, y: 300, width: 425, height: 98), size: 90, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_1", fallback: "", rect: CGRect(x: 832, y: 480, width: 171, height: 51), size: 38, color: Draw.color(0xFF666666), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_2", fallback: "", rect: CGRect(x: 507, y: 570, width: 496, height: 52), size: 40, color: Draw.color(0xFF616161), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_3", fallback: "", rect: CGRect(x: 871, y: 661, width: 132, height: 52), size: 40, color: Draw.color(0xFF626262), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_4", fallback: "", rect: CGRect(x: 625, y: 752, width: 378, height: 51), size: 38, color: Draw.color(0xFF5A5A5A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_5", fallback: "", rect: CGRect(x: 756, y: 843, width: 247, height: 51), size: 38, color: Draw.color(0xFF5F5F5F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_6", fallback: "", rect: CGRect(x: 628, y: 934, width: 375, height: 51), size: 38, color: Draw.color(0xFF565656), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m0_7", fallback: "", rect: CGRect(x: 702, y: 1132, width: 306, height: 67), size: 56, color: Draw.color(0xFF737373), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "m1_0", fallback: "", rect: CGRect(x: 403, y: 300, width: 425, height: 98), size: 90, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_1", fallback: "", rect: CGRect(x: 910, y: 480, width: 93, height: 51), size: 38, color: Draw.color(0xFF666666), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_2", fallback: "", rect: CGRect(x: 507, y: 570, width: 496, height: 52), size: 40, color: Draw.color(0xFF5E5E5E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_3", fallback: "", rect: CGRect(x: 871, y: 661, width: 132, height: 52), size: 40, color: Draw.color(0xFF626262), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_4", fallback: "", rect: CGRect(x: 597, y: 752, width: 406, height: 51), size: 38, color: Draw.color(0xFF575757), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_5", fallback: "", rect: CGRect(x: 756, y: 843, width: 247, height: 51), size: 38, color: Draw.color(0xFF5F5F5F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_6", fallback: "", rect: CGRect(x: 627, y: 934, width: 376, height: 51), size: 38, color: Draw.color(0xFF5B5B5B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m1_7", fallback: "", rect: CGRect(x: 702, y: 1131, width: 306, height: 68), size: 57, color: Draw.color(0xFF747474), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "m2_0", fallback: "", rect: CGRect(x: 403, y: 300, width: 425, height: 98), size: 90, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_1", fallback: "", rect: CGRect(x: 464, y: 480, width: 538, height: 51), size: 38, color: Draw.color(0xFF666666), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_2", fallback: "", rect: CGRect(x: 949, y: 527, width: 54, height: 49), size: 36, color: Draw.color(0xFF727272), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "m2_3", fallback: "", rect: CGRect(x: 507, y: 616, width: 496, height: 52), size: 40, color: Draw.color(0xFF5C5C5C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_4", fallback: "", rect: CGRect(x: 910, y: 707, width: 93, height: 51), size: 38, color: Draw.color(0xFF676767), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_5", fallback: "", rect: CGRect(x: 764, y: 798, width: 239, height: 51), size: 38, color: Draw.color(0xFF5B5B5B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_6", fallback: "", rect: CGRect(x: 871, y: 888, width: 132, height: 52), size: 40, color: Draw.color(0xFF646464), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_7", fallback: "", rect: CGRect(x: 633, y: 980, width: 370, height: 51), size: 38, color: Draw.color(0xFF575757), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "m2_8", fallback: "", rect: CGRect(x: 702, y: 1177, width: 306, height: 68), size: 57, color: Draw.color(0xFF707070), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        return []
    }


    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 安卓 12_01 是「账单详情」，12_02 是「消息盒子」，12_00 是支付成功。
        if state.sub == 1 {
            bill(b, state, showsTopTitle: showsTopTitle)
            return b.result
        }
        if state.sub == 2 {
            messages(b, state, showsTopTitle: showsTopTitle)
            return b.result
        }
        let incoming = state.sub != 0
        detail(b, state, showsTopTitle: showsTopTitle, incoming: incoming,
               alipay: state.sub == 2)
        return b.result
    }

    // MARK: - 12/01 账单详情（14sp / 94px 行）

    private static func bill(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("账单详情") }

        b.centerBlock("商户", s.value("商户", "风筝"), size: dp(16),
                      color: MockInk.title, padTop: dp(16), padBottom: 0)
        b.centerBlock("金额", s.value("金额", "-100.00"), size: dp(35),
                      color: MockInk.title, padTop: dp(4), padBottom: 0)
        b.centerBlock(nil, "交易成功", size: dp(14), color: MockInk.title,
                      padTop: dp(6), padBottom: dp(12))

        let size = dp(14)
        let padV = dp(7.27)
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: dp(13), padV: padV,
                      labelColor: Draw.color(0xff777777), valueColor: MockInk.title)
        }
        row("付款方式", "花呗")
        row("支付宝积分", "立即领取25积分(己翻5倍)")
        row("商品说明", "收钱码收款")
        row("收款方全称", "**岚(个人）")
        row("支付日期", s.time)
        row("订单号", "2022100822001454681448487984")

        b.gap(dp(12))
        b.panel(gapTop: 0, margin: dp(12), pad: dp(16), bg: .white) { p in
            p.pairRow("联系收款方", "", size: size, padH: 0, padV: dp(10),
                      labelColor: Draw.color(0xff3b4144))
            p.pairRow("账单分类", "其他", size: size, padH: 0, padV: dp(10),
                      labelColor: Draw.color(0xff3b4144),
                      valueColor: Draw.color(0xff999999))
            p.pairRow("标签和备注", "添加", size: size, padH: 0, padV: dp(10),
                      labelColor: Draw.color(0xff3b4144),
                      valueColor: Draw.color(0xff999999))
            p.pairRow("查看往来记录", "", size: size, padH: 0, padV: dp(10),
                      labelColor: Draw.color(0xff3b4144))
        }
        b.gap(dp(20))
    }

    private static func detail(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool,
                               incoming: Bool, alipay: Bool) {
        if showsTopTitle { b.titleBar("交易详情") }

        // 灰底 + 白底顶栏
        b.fill(dp(0), MockInk.page)
        b.coloredHeaderBar("交易详情", bg: MockInk.page, fg: MockInk.title)

        // 金额 + 六行明细（同一张白卡）
        b.panel(gapTop: dp(8), margin: dp(12), pad: dp(16), bg: .white) { p in
            p.sectionLabel(s.value("金额", incoming ? "+ ¥ 2,000.00" : "- ¥ 2,000.00"),
                           size: dp(34), color: MockInk.title,
                           padH: 0, padTop: dp(8), padBottom: dp(10))
            // 行距按安卓实测（ms_sub0）：14sp + 6.5dp，行距 91px
            let rs = dp(14)
            let rp = dp(6.5)
            p.row("摘要", s.value("摘要", alipay ? "支付宝转账" : "手机转账"), size: rs, padV: rp)
            p.row("交易卡号", s.value("交易卡号", "民生卡6226 22** **** 5978"),
                  size: rs, padV: rp)
            p.row("对方户名", s.value("对方户名", "李四喜"), size: rs, padV: rp)
            p.row("对方账户", s.value("对方账户", "6227 64** **** 3815"), size: rs, padV: rp)
            p.row("对方开户行", s.value("对方开户行", "中国银行总行"), size: rs, padV: rp)
            p.row("交易时间", s.value("交易时间", "2026.10.24 19:16:11"), size: rs, padV: rp)
        }

        // 分类卡
        b.panel(gapTop: dp(10), margin: dp(12), pad: dp(16), bg: .white) { p in
            p.chevronRow("分类          转给他人", size: dp(15),
                         color: MockInk.title, padH: 0, padV: dp(8))
        }

        b.gap(dp(24))
    }

    // MARK: - 12/02 消息盒子（三张通知卡，13sp）

    private static func messages(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("消息盒子") }
        b.pageBackground = MockInk.page

        let size = dp(13)
        let gray = Draw.color(0xff8f8f8f)
        let orange = Draw.color(0xffde6f2b)

        func card(_ title: String, _ time: String,
                  _ rows: [(String, String, UIColor)],
                  _ big: String?) {
            b.panel(gapTop: dp(10), margin: dp(10), pad: dp(14), bg: .white) { p in
                p.pairRow(title, time, size: dp(13), padH: 0, padV: dp(4),
                          labelColor: MockInk.title, valueColor: Draw.color(0xffbbbbbb))
                if let big {
                    p.sectionLabel(big, size: dp(16), color: MockInk.title,
                                   padH: 0, padTop: dp(6), padBottom: dp(6))
                }
                for (label, value, color) in rows {
                    p.pairRow(label, value, size: size, padH: 0, padV: dp(6),
                              labelColor: gray, valueColor: color)
                }
                p.sectionLabel("查看详情", size: size, color: MockInk.title,
                               padH: 0, padTop: dp(8), padBottom: 0)
            }
        }

        card("商家服务", "10:00",
             [("经营数据", "今日第1笔收入，共计¥100.00", MockInk.title),
              ("备注", "已开通笔笔转入余额宝", MockInk.title),
              ("本次奖励", "9月现金奖励等你赚取", orange)],
             "收款金额¥100.00")
        card("余额宝", "10:00",
             [("昨日收益", "", gray),
              ("收益日期", "09-25余额宝收益", MockInk.title),
              ("本次奖励", "昨日有收益领芭芭农场肥料", orange)],
             "+0.01")
        card("支付宝会员", "10:00",
             [("我的积分", "今日第1笔收款．共计¥100.00", MockInk.title),
              ("领取提示", "积分奖励不领取会消失，请尽快领取哦", MockInk.title)],
             "今日第1笔收款．共计¥100.00")
        b.gap(dp(20))
    }
}
