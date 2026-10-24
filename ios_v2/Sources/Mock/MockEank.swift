import UIKit

/// 「模拟 → 光大模拟」的三个子页版式。
///
/// 逐块对应 `EankActivity.buildOut()` / `buildIn()` / `buildTransfer()`（编辑页）
/// 与 `EankDetailActivity` 的同名方法（成品页）。
///
/// 光大的版式是「一张灰底信息卡 + 若干白底明细卡」，
/// 主色是紫色 `0xff7a4fbf`；成品页把灰底换成白底、字号各 +1~2dp。
enum MockEank {

    private static let purple = Draw.color(0xff7a4fbf)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            out(b, state, showsTopTitle: showsTopTitle)
        case 1:
            incoming(b, state, showsTopTitle: showsTopTitle)
        default:
            transfer(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    // MARK: - 0 明细转出：转账汇款

    private static func out(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            // 抬头是「转账汇款 + 完成」，没有返回箭头（Java:279-294）
            b.coloredHeaderBar("转账汇款", bg: MockInk.white, fg: MockInk.title,
                               trailing: "完成")
        }

        b.tintedPanel(gapTop: isDetail ? dp(12) : 0,
                      margin: isDetail ? dp(12) : dp(10),
                      pad: isDetail ? dp(16) : dp(14),
                      bg: isDetail ? MockInk.white : Draw.color(0xfff7f7f7)) { box in
            box.outlinedCircleBadge(diameter: isDetail ? dp(64) : dp(56),
                                    stroke: purple, strokeWidth: dp(2),
                                    symbol: "✓", symbolColor: purple,
                                    symbolSize: isDetail ? dp(34) : dp(30),
                                    gapTop: 0)
            box.centerBlock(nil, "转账成功！", size: isDetail ? dp(20) : dp(18),
                            color: MockInk.title, bold: true,
                            padTop: isDetail ? dp(14) : dp(12),
                            padBottom: isDetail ? dp(18) : dp(16))
            box.centeredCells(["查询明细", "继续转账", "分享回单"],
                              height: isDetail ? dp(24) : dp(22),
                              size: isDetail ? dp(14) : dp(13), padV: 0)
        }

        // 白底明细卡：只是 padding，画在页面白底上即可
        let rowPadH = (isDetail ? dp(12) : dp(10)) + (isDetail ? dp(16) : dp(14))
        let rowV = isDetail ? dp(14) : dp(12)
        let rowSize = isDetail ? dp(15) : dp(14)
        b.gap(isDetail ? dp(12) : dp(10))
        b.pairRow("转账金额", s.value("转账金额", "¥ 100.00"), size: rowSize,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("付款账号", s.value("付款账号", "622602*****9990"), size: rowSize,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)
        b.centeredLink("查看详情⌄", size: isDetail ? dp(14) : dp(13), color: purple,
                       padV: isDetail ? dp(10) : dp(8))
    }

    // MARK: - 1 明细转入：交易详情

    private static func incoming(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            b.coloredHeaderBar("交易详情", bg: MockInk.white, fg: MockInk.title,
                               trailing: "⋯")
        }

        b.tintedPanel(gapTop: isDetail ? dp(12) : 0,
                      margin: isDetail ? dp(12) : dp(10),
                      pad: isDetail ? dp(16) : dp(14),
                      bg: isDetail ? MockInk.white : Draw.color(0xfff7f7f7)) { box in
            box.centerBlock("姓名", s.value("姓名", "李哈哈"),
                            size: isDetail ? dp(16) : dp(15), color: MockInk.value,
                            padTop: 0, padBottom: 0)
            box.centerBlock("金额", s.value("金额", "收入 100.00"),
                            size: isDetail ? dp(28) : dp(26), color: Draw.color(0xffe0651a),
                            bold: true, padTop: isDetail ? dp(10) : dp(8),
                            padBottom: isDetail ? dp(8) : dp(6))
            box.centerBlock("余额", s.value("余额", "余额 ¥100.00"),
                            size: isDetail ? dp(14) : dp(13), color: MockInk.label,
                            padTop: 0, padBottom: 0)
        }

        let rowPadH = (isDetail ? dp(12) : dp(10)) + (isDetail ? dp(16) : dp(14))
        let rowV = isDetail ? dp(14) : dp(12)
        let rowSize = isDetail ? dp(15) : dp(14)
        b.gap(isDetail ? dp(12) : dp(10))
        b.pairRow("交易卡号", s.value("交易卡号", "622602*****9990"), size: rowSize,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("交易时间", s.value("交易时间", s.time), size: rowSize,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("币种", s.value("币种", "人民币"), size: rowSize,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)
    }

    // MARK: - 2 转账详情

    private static func transfer(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            b.coloredHeaderBar("转账详情", bg: MockInk.white, fg: MockInk.title,
                               trailing: "⋯")
        }

        b.tintedPanel(gapTop: isDetail ? dp(12) : 0,
                      margin: isDetail ? dp(12) : dp(10),
                      pad: isDetail ? dp(16) : dp(14),
                      bg: isDetail ? MockInk.white : Draw.color(0xfff7f7f7)) { box in
            box.centerBlock("收款人", s.value("收款人", "转给 欧阳询"),
                            size: isDetail ? dp(16) : dp(15), color: MockInk.grey,
                            padTop: 0, padBottom: 0)
            box.centerBlock("金额", s.value("金额", "¥100.00"),
                            size: isDetail ? dp(32) : dp(30), color: MockInk.amount,
                            padTop: isDetail ? dp(10) : dp(8), padBottom: 0)
        }

        let rowPadH = (isDetail ? dp(12) : dp(10)) + (isDetail ? dp(16) : dp(14))
        let rowV = isDetail ? dp(14) : dp(12)
        let rowSize = isDetail ? dp(15) : dp(14)

        // 第一张卡：交易状态（绿字）+ 状态说明
        b.gap(isDetail ? dp(12) : dp(10))
        b.pairRow("交易状态", s.value("交易状态", "交易成功"), size: rowSize,
                  padH: rowPadH, padV: rowV,
                  labelColor: MockInk.value, valueColor: MockInk.green)
        b.pairRow("状态说明", s.value("状态说明", "资金已到账，请确认"), size: rowSize,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)

        // 第二张卡：六行明细
        b.gap(isDetail ? dp(12) : dp(10))
        let rows: [(String, String)] = [
            ("收款账户", "6217858000041234288"),
            ("收款银行", "中国银行"),
            ("付款账户", "622178*****4508"),
            ("交易时间", s.time),
            ("转账用途", "转账汇款"),
            ("转账方式", "实时处理"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: rowSize,
                      padH: rowPadH, padV: rowV, labelColor: MockInk.value)
        }

        b.sideButtons("查看回单", "再转一笔", color: purple,
                      height: isDetail ? dp(48) : dp(46),
                      padH: isDetail ? dp(12) : dp(10),
                      gapTop: isDetail ? dp(14) : dp(10),
                      size: isDetail ? dp(16) : dp(15))
    }
}
