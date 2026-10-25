import UIKit

/// 「模拟 → 民生模拟」的子页版式（入口 12）。
///
/// 3 个子页（设备抽屉）：`明细转出 / 明细转入 / 明细转入支付宝`。
/// 0 号是「交易详情」：白卡金额 + 六行明细，下面单独一张「分类」卡。
enum MockMinsheng {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 安卓 12_01 是「账单详情」，12_00/12_02 是支付成功/消息盒子；
        // 目前先对齐 12_01，另外两页保持旧版式。
        if state.sub == 1 {
            bill(b, state, showsTopTitle: showsTopTitle)
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
}
