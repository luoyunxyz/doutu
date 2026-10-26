import UIKit

/// 「模拟 → 建设模拟」的子页版式。
///
/// 建设的 12 个子页名与招商**完全一样**（明细转出(旧) … 财付通转出），但版式不同：
/// 0 号是金头的「转账成功」页（大号对勾徽标 + 说明 + 两行明细 + 三个描边胶囊），
/// 这里按安卓设备截图逐块复刻；其余子页先给占位。
enum MockJianshe {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "j0_0", fallback: "", rect: CGRect(x: 396, y: 470, width: 284, height: 78), size: 68, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "j0_1", fallback: "", rect: CGRect(x: 766, y: 610, width: 224, height: 57), size: 45, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_2", fallback: "", rect: CGRect(x: 850, y: 682, width: 139, height: 55), size: 43, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_3", fallback: "", rect: CGRect(x: 53, y: 755, width: 159, height: 54), size: 42, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "j0_4", fallback: "", rect: CGRect(x: 813, y: 911, width: 177, height: 54), size: 42, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_5", fallback: "", rect: CGRect(x: 594, y: 1035, width: 397, height: 55), size: 43, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_6", fallback: "", rect: CGRect(x: 744, y: 1159, width: 246, height: 55), size: 43, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_7", fallback: "", rect: CGRect(x: 737, y: 1288, width: 253, height: 54), size: 42, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_8", fallback: "", rect: CGRect(x: 813, y: 1412, width: 177, height: 55), size: 43, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "j0_9", fallback: "", rect: CGRect(x: 419, y: 1512, width: 226, height: 51), size: 38, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "j0_10", fallback: "", rect: CGRect(x: 86, y: 1691, width: 871, height: 100), size: 92, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "j0_11", fallback: "", rect: CGRect(x: 41, y: 1852, width: 169, height: 51), size: 38, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "j0_12", fallback: "", rect: CGRect(x: 38, y: 1953, width: 1003, height: 388), size: 409, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "j0_13", fallback: "", rect: CGRect(x: 37, y: 2394, width: 1004, height: 382), size: 403, color: MockInk.title, align: 1),
            ]
        }
        return []
    }


    /// 建设的主题金（截图取自页面大标题与描边胶囊）
    static let gold = UIColor(red: 0xb8 / 255.0, green: 0x86 / 255.0,
                              blue: 0x0b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        let names = MockModule.jianshe.subs
        let title = names.indices.contains(state.sub) ? names[state.sub] : "明细"
        switch state.sub {
        case 0:
            transferSuccess(b, state, showsTopTitle: showsTopTitle)
        case 8:
            // 回单
            if showsTopTitle { b.titleBar("电子回单") }
            b.coloredHeaderBar("电子回单", bg: .white, fg: MockInk.title)
            b.sectionLabel("中国建设银行  电子回单", size: dp(17), color: MockInk.title,
                           padH: dp(16), padTop: dp(16), padBottom: dp(6))
            b.row("付款户名", "李哈哈")
            b.row("收款户名", "张大国")
            b.row("金额", "100.00")
            b.row("时间", state.time)
            b.gap(dp(20))
        case 9:
            // 余额
            if showsTopTitle { b.titleBar("余额") }
            b.coloredHeaderBar("余额", bg: .white, fg: MockInk.title)
            b.sectionLabel("¥ 1,860.00", size: dp(40), color: MockInk.black,
                           padH: 0, padTop: dp(30), padBottom: dp(10))
            b.sectionLabel("中国建设银行 · 储蓄卡 6227****8888", size: dp(14),
                           color: MockInk.label, padH: 0, padTop: 0, padBottom: dp(20))
            b.gap(dp(160))
        default:
            // 其余 9 个子页：建设版的明细页（标题取子页名）
            if showsTopTitle { b.titleBar(title) }
            b.coloredHeaderBar(title, bg: .white, fg: MockInk.title)
            b.centerBlock("对方", state.value("对方", "张大国"),
                          size: dp(15), color: MockInk.grey,
                          padTop: dp(24), padBottom: 0)
            b.centerBlock("金额", state.value("金额", "-100.00"),
                          size: dp(34), color: MockInk.amount, bold: true,
                          padTop: dp(10), padBottom: dp(30))
            b.statusBar("交易成功", trailing: "进度查询  ›")
            b.row("收款账号", "6227 **** **** 8888")
            b.row("收款银行", "中国建设银行")
            b.row("交易类型", title)
            b.row("交易时间", state.time)
            b.gap(dp(20))
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
