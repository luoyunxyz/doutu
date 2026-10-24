import UIKit

/// 「模拟 → 中国模拟」的六个子页版式。
///
/// 逐块对应 `BocActivity.buildResult()` / `buildDetail()`（编辑页）
/// 与 `BocDetailActivity.buildResult()` / `buildDetail()`（成品页）。
///
/// 中行的两套版式差别很大：
///   · 子页 0/4（明细转出 / 转入2）是「操作结果」页：绿色 ✓ 徽标 + 成功文案 + 你可能需要的三行；
///   · 其余是「明细」页：左对齐的收入金额 + 灰底对方账户块 + 六行明细。
enum MockBoc {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        if state.sub == 0 || state.sub == 5 {
            result(b, state, showsTopTitle: showsTopTitle)
        } else {
            detail(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    /// `yyyy-MM-dd HH:mm:ss` → `yyyy/MM/dd HH:mm:ss`（Java 的 `time.replace("-", "/")`）
    private static func slashed(_ text: String) -> String {
        text.replacingOccurrences(of: "-", with: "/")
    }

    // MARK: - 明细转出 / 转入2：操作结果（`buildResult()`）

    private static func result(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let detail = b.metrics.isDetail
        if showsTopTitle { b.titleBar("操作结果") }

        b.checkBadge(diameter: detail ? dp(54) : dp(48),
                     color: Draw.color(0xff5fd6a0),
                     gapTop: detail ? dp(18) : dp(12),
                     symbolSize: detail ? dp(28) : dp(26))

        b.centerBlock("金额", s.value("金额", "100.00元交易成功"),
                      size: detail ? dp(20) : dp(18), color: MockInk.title, bold: true,
                      padTop: detail ? dp(12) : dp(10), padBottom: dp(2))
        b.centerBlock(nil, "预计10秒内到账，实际时间取决于对方银行",
                      size: detail ? dp(13) : dp(12), color: MockInk.noteTitle,
                      padTop: 0, padBottom: detail ? dp(16) : dp(14))

        b.hairlineFull()

        let rowH = detail ? dp(16) : dp(14)
        let rowV = detail ? dp(13) : dp(11)
        let rowPadH = detail ? dp(16) : dp(14)
        b.pairRow("优惠后费用", s.value("优惠后费用", "0.00"), size: rowH,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("付款后\n可用余额", s.value("付款后可用余额", "100.00"), size: rowH,
                  padH: rowPadH, padV: rowV, labelColor: MockInk.value)

        b.hairlineFull()

        b.centeredLink("交易详情", size: detail ? dp(15) : dp(14),
                       color: Draw.color(0xff2f6fb2), padV: detail ? dp(14) : dp(12))

        b.fullWidthImage("mock_boc_banner")

        b.sectionLabel("您可能需要", size: detail ? dp(13) : dp(12), color: MockInk.noteTitle,
                       padH: detail ? dp(16) : dp(14),
                       padTop: detail ? dp(14) : dp(12), padBottom: detail ? dp(10) : dp(8))
        for item in ["分享给微信好友", "继续转账", "转账记录"] {
            b.chevronRow(item, size: rowH, color: MockInk.title,
                         padH: detail ? dp(16) : dp(14), padV: detail ? dp(16) : dp(14))
            b.hairlineFull()
        }

        b.sectionLabel("更多推荐", size: detail ? dp(16) : dp(15), color: MockInk.title,
                       padH: detail ? dp(16) : dp(14),
                       padTop: detail ? dp(16) : dp(14), padBottom: detail ? dp(10) : dp(8))

        b.fullWidthImage("mock_boc_ad")

        b.fullButton("再次转账", filled: true, color: Draw.color(0xffe8452f),
                     height: detail ? dp(50) : dp(46),
                     padH: detail ? dp(16) : dp(14),
                     gapTop: detail ? dp(16) : dp(14),
                     size: detail ? dp(17) : dp(16))
    }

    // MARK: - 其余子页：明细（`buildDetail()`）

    private static func detail(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let detail = b.metrics.isDetail
        let padH = detail ? dp(16) : dp(14)
        if showsTopTitle { b.titleBar("明细") }

        // 安卓实测：02/01 行 12sp / 121px，02/03 行 14sp / 121px，02/04 行 12sp / 105px；
        // 收入标签 16sp，金额 28 / 24 / 26sp。
        let labelSize = dp(16)
        let amountSize = s.sub == 3 ? dp(24) : (s.sub == 4 ? dp(26) : dp(28))
        b.leftAmount("收入金额（人民币元）", labelKey: nil,
                     s.value("金额", "100.00"), valueKey: "金额",
                     labelSize: labelSize,
                     amountSize: amountSize,
                     padH: padH,
                     padTop: dp(14), padBottom: dp(14))

        b.hairlineFull()

        b.greyKvBlock("对方账户名称/\n账号", s.value("对方账户", "李哈哈 6221 ****** 3640"),
                      labelWidth: detail ? dp(120) : dp(110),
                      size: detail ? dp(14) : dp(13),
                      padH: padH, padV: detail ? dp(14) : dp(12))

        let rowSize = s.sub == 3 ? dp(14) : dp(12)
        let rowV = s.sub == 3 ? dp(11.77) : (s.sub == 4 ? dp(10.3) : dp(12.97))
        let channel = (s.sub == 2 || s.sub == 5) ? "支付宝" : "网上银行"

        b.pairRow("交易时间", s.value("交易时间", slashed(s.time)),
                  size: rowSize, padH: padH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("记账时间", s.value("记账时间", slashed(s.time)),
                  size: rowSize, padH: padH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("交易类型", s.value("交易类型", "跨行转账"),
                  size: rowSize, padH: padH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("交易后余额", s.value("交易后余额", "人民币元 100.00"),
                  size: rowSize, padH: padH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("交易渠道/场所", s.value("交易渠道/场所", channel),
                  size: rowSize, padH: padH, padV: rowV, labelColor: MockInk.value)
        b.pairRow("附言", s.value("附言", "-"),
                  size: rowSize, padH: padH, padV: rowV, labelColor: MockInk.value)

        if s.sub == 1 || s.sub == 3 {
            b.paragraph("\"交易时间\"为该笔交易提交的时间：\"记账时间\"为后台系统对该笔交易的实际处理时间。",
                        size: dp(13), color: Draw.color(0xff5d5d5d),
                        padH: dp(16), padTop: dp(10), padBottom: dp(10))
        }
    }
}
