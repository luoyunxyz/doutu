import UIKit

/// 「模拟 → 建设模拟」的子页版式。
///
/// 建设的 12 个子页名与招商**完全一样**（明细转出(旧) … 财付通转出），但版式不同：
/// 0 号是金头的「转账成功」页（大号对勾徽标 + 说明 + 两行明细 + 三个描边胶囊），
/// 这里按安卓设备截图逐块复刻；其余子页先给占位。
enum MockJianshe {

    /// 建设的主题金（截图取自页面大标题与描边胶囊）
    static let gold = UIColor(red: 0xb8 / 255.0, green: 0x86 / 255.0,
                              blue: 0x0b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            transferSuccess(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .jianshe, metrics: metrics, width: width)
        }
        return b.result
    }

    // MARK: - 子页 0：转账成功（`明细转出(旧)`）

    private static func transferSuccess(_ b: MockBuilder, _ s: MockState,
                                        showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细转出(旧)") }

        // 金色顶条 + 右上「完成」
        b.coloredHeaderBar("", bg: gold, fg: .white, trailing: "完成")

        // 大号对勾徽标：白底描边圆 + 金色勾
        b.gap(dp(10))
        b.checkBadge(diameter: dp(96), color: gold, gapTop: dp(6), symbolSize: dp(52))

        // 标题
        b.sectionLabel("转账成功", size: dp(24), color: gold,
                       padH: 0, padTop: dp(14), padBottom: 0)

        // 说明
        b.paragraph("一般情况下，资金实时转入收款账户，实际转入收款账户时间视收款行处理而定。"
                    + "如有疑问，请咨询收款行。",
                    size: dp(15), color: MockInk.value,
                    padH: dp(20), padTop: dp(18), padBottom: dp(8))

        // 两行明细
        b.row("转账金额", s.value("转账金额", "100.00元"))
        b.hairlineFull()
        b.row("收款账户", s.value("收款账户", "李哈哈  6226***9990"))

        // 查看更多
        b.centeredLink("查看更多 ⌄", size: dp(15), color: gold, padV: dp(10))

        // 三个描边胶囊
        b.centeredCells(["继续转账", "我的账户", "通知收款人"],
                        height: dp(36), size: dp(15), padV: dp(18))

        // 更多精彩
        b.fill(dp(10), MockInk.page)
        b.sectionLabel("更多精彩", size: dp(16), color: MockInk.label,
                       padH: dp(16), padTop: dp(14), padBottom: dp(10))
        b.fullWidthImage("mock_bg", gapTop: 0)
    }
}
