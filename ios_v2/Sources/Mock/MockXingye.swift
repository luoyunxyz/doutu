import UIKit

/// 「模拟 → 兴业模拟」的子页版式。
///
/// 兴业这套和六家银行不同：五个子页是
/// `明细转出 / 明细转入 / 回单 / 转账结果 / 明细转出2`，
/// 其中 0 号是**转账结果**页（灰底状态卡 + 明细行 + 电子受理单 + 蓝底操作区），
/// 版式与安卓设备上的截图逐块对应。
///
/// 其余子页还在补：先给出明确的「版式制作中」占位，避免出现空白页。
enum MockXingye {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0, 3:
            result(b, state, showsTopTitle: showsTopTitle)
        case 1:
            // 明细转入：与「明细转出2」同版式，金额取正
            transferDetail(b, state, showsTopTitle: showsTopTitle, incoming: true)
        case 2:
            receipt(b, state, showsTopTitle: showsTopTitle)
        default:
            transferDetail(b, state, showsTopTitle: showsTopTitle, incoming: false)
        }
        return b.result
    }

    // MARK: - 子页 2：回单（电子受理单）

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("电子回单") }
        b.sectionLabel("电子受理单", size: dp(17), color: MockInk.title,
                       padH: dp(16), padTop: dp(20), padBottom: dp(6))
        b.row("收款人", s.value("收款人", "张大国"))
        b.row("收款账号", s.value("收款账号", "6226 0222 0000 0646 9990"))
        b.row("收款银行", s.value("收款银行", "中国工商银行"))
        b.row("转账金额", s.value("转账金额", "100.00"))
        b.row("交易时间", s.value("交易时间", s.time))
        b.hairlineFull()
        b.paragraph("本回单为电子凭证，可用于核对转账信息。",
                    size: dp(14), color: MockInk.label,
                    padH: dp(16), padTop: dp(12), padBottom: dp(20))
    }

    // MARK: - 子页 0 / 3：转账结果（`资金已转入对方账户`）

    private static func result(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细转出") }

        // 顶部「完成」
        b.sectionLabel("完成", size: dp(16), color: MockInk.linkBlue,
                       padH: dp(16), padTop: dp(10), padBottom: dp(0))

        // 灰底状态卡：绿勾 + 两句进度
        b.panel(gapTop: dp(10), margin: dp(16), pad: dp(14), bg: MockInk.statusBg) { p in
            p.checkBadge(diameter: dp(22), color: MockInk.green, gapTop: 0, symbolSize: dp(13))
            p.sectionLabel("资金已转入对方账户", size: dp(17), color: MockInk.title,
                           padH: 0, padTop: dp(10), padBottom: 0)
            p.sectionLabel("转账申请已提交", size: dp(15), color: MockInk.label,
                           padH: 0, padTop: dp(14), padBottom: 0)
        }

        // 明细行
        b.gap(dp(6))
        b.row("转账金额", s.value("转账金额", "¥ 100.00"))
        b.row("收款人", s.value("收款人", "张大国"))
        b.row("收款账号", s.value("收款账号", "6226 0222 0000 0646 9990"))
        b.row("收款银行", s.value("收款银行", "中国工商银行"))
        b.row("交易时间", s.value("交易时间", "2026/10/24 19:01"))

        // 电子受理单
        b.hairlineFull()
        b.linkBlock("电子受理单", trailing: "点击查看")

        // 蓝底操作区
        b.fill(dp(10), MockInk.page)
        b.sectionLabel("可用余额 ******", size: dp(13), color: MockInk.orange,
                       padH: dp(16), padTop: dp(12), padBottom: dp(0))
        b.fullButton("通知收款人", filled: true, color: MockInk.icbcRed,
                     height: dp(44), padH: dp(16), gapTop: dp(10), size: dp(16))
        b.dualLinks("保存到相册", "继续转账", size: dp(14),
                    color: MockInk.linkBlue, separatorColor: MockInk.line, padBottom: dp(6))
        b.paragraph("温馨提示：转账已提交仅表明兴业银行已受理转账申请，不代表处理结果，"
                    + "请查询付款账户是否已扣款。",
                    size: dp(13), color: MockInk.label,
                    padH: dp(16), padTop: dp(10), padBottom: dp(12))
    }

    // MARK: - 子页 4：明细转出2（按「明细」模板，字段默认值来自截图）

    private static func transferDetail(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool, incoming: Bool) {
        let m = b.metrics
        if showsTopTitle { b.titleBar(incoming ? "明细转入" : "明细") }

        b.centerBlock("收款人", s.value("收款人", "张大国"),
                      size: m.nameSize, color: MockInk.grey,
                      padTop: m.namePadTop, padBottom: 0)
        b.centerBlock("金额", s.value("金额", incoming ? "+100.00" : "-100.00"),
                      size: m.amountSize, color: MockInk.amount, bold: true,
                      padTop: m.amountPadTop, padBottom: m.amountPadBottom)

        b.statusBar("交易成功", trailing: "进度查询  ›")

        b.row("收款账号", s.value("收款账号", "6226 0222 0000 0646 9990"))
        b.row("收款银行", s.value("收款银行", "中国工商银行"))
        b.divider(m.rowPadH)
        b.row("付款户名", s.value("付款户名", "李东"))
        b.row("付款账号", s.value("付款账号", "6221****3646"))
        b.row("转账方式", s.value("转账方式", "实时转账"))
        b.row("转账附言", s.value("转账附言", "--"))
        b.row("交易时间", s.value("交易时间", s.time))

        b.gap(dp(12))
        b.linkBlock("查看和TA的转账记录", trailing: "›")
        b.twoButtons("再转一笔", "通知收款人")
    }
}
