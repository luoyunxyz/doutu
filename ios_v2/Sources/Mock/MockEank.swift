import UIKit

/// 「模拟 → 光大模拟」的三个子页版式。
///
/// 逐块对应 `EankActivity.buildOut()` / `buildIn()` / `buildTransfer()`（编辑页）
/// 与 `EankDetailActivity` 的同名方法（成品页）。
///
/// 光大的版式是「一张灰底信息卡 + 若干白底明细卡」，
/// 主色是紫色 `0xff7a4fbf`；成品页把灰底换成白底、字号各 +1~2dp。
enum MockEank {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        switch sub {
        case 0:
            return [
                MockPsbc.OverlayField(key: "e0_0", fallback: "", rect: CGRect(x: 441, y: 164, width: 198, height: 69), size: 61, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e0_1", fallback: "", rect: CGRect(x: 331, y: 279, width: 417, height: 73), size: 66, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e0_2", fallback: "", rect: CGRect(x: 429, y: 401, width: 221, height: 47), size: 35, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e0_3", fallback: "", rect: CGRect(x: 587, y: 560, width: 434, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_4", fallback: "", rect: CGRect(x: 662, y: 664, width: 359, height: 47), size: 35, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_5", fallback: "", rect: CGRect(x: 791, y: 765, width: 230, height: 50), size: 39, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_6", fallback: "", rect: CGRect(x: 655, y: 869, width: 366, height: 47), size: 35, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_7", fallback: "", rect: CGRect(x: 861, y: 971, width: 160, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_8", fallback: "", rect: CGRect(x: 862, y: 1073, width: 159, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_9", fallback: "", rect: CGRect(x: 610, y: 1248, width: 409, height: 50), size: 39, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_10", fallback: "", rect: CGRect(x: 710, y: 1387, width: 303, height: 54), size: 44, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_11", fallback: "", rect: CGRect(x: 826, y: 1516, width: 188, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_12", fallback: "", rect: CGRect(x: 888, y: 1630, width: 21, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_13", fallback: "", rect: CGRect(x: 945, y: 1861, width: 64, height: 58), size: 49, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e0_14", fallback: "", rect: CGRect(x: 694, y: 2072, width: 231, height: 48), size: 37, color: MockInk.title, align: 2),
            ]
        case 1:
            return [
                MockPsbc.OverlayField(key: "e1_0", fallback: "", rect: CGRect(x: 468, y: 218, width: 142, height: 54), size: 44, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e1_1", fallback: "", rect: CGRect(x: 468, y: 335, width: 263, height: 80), size: 74, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e1_2", fallback: "", rect: CGRect(x: 413, y: 472, width: 251, height: 51), size: 40, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e1_3", fallback: "", rect: CGRect(x: 639, y: 733, width: 364, height: 55), size: 45, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e1_4", fallback: "", rect: CGRect(x: 601, y: 901, width: 401, height: 55), size: 45, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e1_5", fallback: "", rect: CGRect(x: 862, y: 1071, width: 139, height: 54), size: 44, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e1_6", fallback: "", rect: CGRect(x: 737, y: 1239, width: 266, height: 55), size: 45, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e1_7", fallback: "", rect: CGRect(x: 581, y: 1408, width: 422, height: 54), size: 44, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e1_8", fallback: "", rect: CGRect(x: 819, y: 1576, width: 184, height: 55), size: 45, color: MockInk.title, align: 2),
            ]
        case 2:
            return [
                MockPsbc.OverlayField(key: "e2_0", fallback: "", rect: CGRect(x: 414, y: 240, width: 250, height: 58), size: 49, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e2_1", fallback: "", rect: CGRect(x: 359, y: 382, width: 368, height: 89), size: 84, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "e2_2", fallback: "", rect: CGRect(x: 820, y: 655, width: 182, height: 56), size: 46, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_3", fallback: "", rect: CGRect(x: 610, y: 791, width: 393, height: 56), size: 46, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_4", fallback: "", rect: CGRect(x: 548, y: 975, width: 455, height: 56), size: 46, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_5", fallback: "", rect: CGRect(x: 821, y: 1110, width: 182, height: 56), size: 46, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_6", fallback: "", rect: CGRect(x: 639, y: 1246, width: 364, height: 56), size: 46, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_7", fallback: "", rect: CGRect(x: 601, y: 1382, width: 402, height: 56), size: 46, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_8", fallback: "", rect: CGRect(x: 819, y: 1518, width: 184, height: 55), size: 45, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_9", fallback: "", rect: CGRect(x: 820, y: 1654, width: 183, height: 55), size: 45, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "e2_10", fallback: "", rect: CGRect(x: 568, y: 1780, width: 482, height: 167), size: 110, color: MockInk.title, align: 2),
            ]
        default: return []
        }
    }


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
