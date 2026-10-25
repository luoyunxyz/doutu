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
        switch state.sub {
        case 0:
            resultV2(b, state, showsTopTitle: showsTopTitle)
        case 1, 2:
            detailIncoming(b, state, showsTopTitle: showsTopTitle)
        case 3:
            detailSettings(b, state, showsTopTitle: showsTopTitle)
        case 4:
            detailOutgoing(b, state, showsTopTitle: showsTopTitle)
        case 5:
            detailIncoming(b, state, showsTopTitle: showsTopTitle)
        default:
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

    /// 中国 02/01「明细转入1」：收入金额 + 对方账户块 + 6 行 + 灰底说明。
    ///
    /// 参考图 1280×2816（标题栏底 252px）逐块量，×1080/1280、标题栏 94.5 之下：
    ///   收入金额 86  金额 181  分隔线 334  对方账户块 364…437
    ///   灰条 469…499  首行中心 556（行距 121.5）  灰底说明 1225 起。
    private static func detailIncoming(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细") }

        b.leftBlock("收入金额（人民币元）", x: dp(16.9), size: dp(17),
                    color: MockInk.title, bold: true, padTop: dp(27.7))
        b.leftBlock(s.value("金额", "100.00"), x: dp(16.9), size: dp(30),
                    color: MockInk.title, bold: true, padTop: dp(10.9), padBottom: dp(16.3))
        b.hairlineFull()

        b.fixedRow("对方账户名称/\n账号", s.value("对方账户",
                   s.sub == 5 ? "黄欢易 2088 ****** 0156" : "李哈哈 6221 ****** 3640"),
                   labelX: dp(16.9), valueX: dp(103), size: dp(12.7), padV: dp(6.7),
                   labelColor: MockInk.title, valueColor: Draw.color(0xff333333))
        b.fill(dp(10.1), Draw.color(0xffeeeeee))

        let size = dp(12.7), pitchV = dp(12.65)
        func row(_ label: String, _ value: String) {
            b.fixedRow(label, s.value(label, value), labelX: dp(16.9), valueX: dp(103),
                       size: size, padV: pitchV,
                       labelColor: MockInk.title, valueColor: Draw.color(0xff666666))
        }
        row("交易时间", slashed(s.time))
        row("记账时间", slashed(s.time))
        let alipay = s.sub == 2 || s.sub == 5
        row("交易类型", alipay ? "网上快捷提现" : "跨行转账")
        row("交易后余额", alipay ? "人民币元 345.02" : "人民币元 100.00")
        b.fixedRow("交易渠道/\n场所", s.value("交易渠道/场所", alipay ? "在线交易网关" : "网上银行"),
                   labelX: dp(16.9), valueX: dp(103), size: size, padV: dp(5.05),
                   labelColor: MockInk.title, valueColor: Draw.color(0xff666666))
        b.fixedRow("附言",
                   s.value("附言", alipay
                           ? (s.sub == 5
                              ? "支付宝-黄欢易-黄欢易支付宝转账-黄欢\n易支付宝转账"
                              : "支付宝-刘石军-刘石军支付宝转\n账-刘石军支付宝转账")
                           : "手机转账"),
                   labelX: dp(16.9), valueX: dp(103), size: size, padV: dp(5.05),
                   labelColor: MockInk.title, valueColor: Draw.color(0xff666666))

        b.bottomFill(Draw.color(0xffeeeeee),
                     canvasHeight: showsTopTitle ? 2388 : 2388 - MockDetailScreen.headH)
        b.paragraph("“交易时间”为该笔交易的提交时间；“记账时间”为后台系统对该笔交易的实际处理时间。",
                    size: dp(12), color: Draw.color(0xff5d5d5d),
                    padH: dp(15), padTop: dp(12.1), padBottom: dp(20))
    }

    /// 中国 02/03「明细转入2」：收入金额 + 两行账户 + 灰条 + 七行 + 设置区。
    /// 参考图 1280×2816（标题栏底 267px，×1080/1280、标题栏 94.5 之下）。
    private static func detailSettings(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细") }
        b.leftBlock("收入金额(人民币元)", x: dp(12.7), size: dp(13),
                    color: MockInk.title, bold: true, padTop: dp(28.1))
        b.leftBlock(s.value("金额", "500.00"), x: dp(12.7), size: dp(27),
                    color: MockInk.title, bold: true, padTop: dp(13.5), padBottom: dp(18))
        b.hairlineFull()

        let size = dp(13), padH = dp(12.7), valueX = dp(114)
        let single: CGFloat = 105.5, double: CGFloat = 139.6
        func row(_ label: String, _ value: String, _ h: CGFloat) {
            b.fixedRow(label, s.value(label, value), labelX: padH, valueX: valueX,
                       size: size, padV: (h - 46.8) / 2,
                       labelColor: MockInk.title, valueColor: Draw.color(0xff333333))
            b.hairlineFull()
        }
        row("交易账户", "借记卡6221 ****** 3879", single)
        row("交易后余额", "人民币元 600.00", single)
        b.fill(dp(9), Draw.color(0xffeeeeee))
        row("对方名称", "吴俊号", single)
        row("对方账号", "6221 ****** 0636", single)
        row("对方银行名称", "中国建设银行股份有限公司xxx支行", double)
        row("交易类型", "跨行转账", single)
        row("交易渠道/场所", "网上银行", double)
        row("附言", "跨行转出", single)
        row("交易时间", slashed(s.time), single)
        b.fill(dp(9), Draw.color(0xffeeeeee))
        b.leftBlock("可修改分类、备注、所属账本、交易对象和计入收支情况",
                    x: padH, size: dp(11), color: Draw.color(0xff999999), padTop: dp(6.5))
        b.fill(dp(9), Draw.color(0xffeeeeee))

        let h = dp(35), blue = Draw.color(0xff2f6fd0)
        b.actionRow("分类", value: s.value("分类", "其他收入"), action: "修改",
                    size: size, height: h, padH: padH, valueX: valueX, actionColor: blue)
        b.actionRow("交易备注", value: nil, action: "修改",
                    size: size, height: h, padH: padH, valueX: valueX, actionColor: blue)
        b.actionRow("所属账本", value: nil, action: "选择账本",
                    size: size, height: h, padH: padH, valueX: valueX, actionColor: blue)
        b.actionRow("交易对象", value: s.value("交易对象", "本人"), action: "修改",
                    size: size, height: h, padH: padH, valueX: valueX, actionColor: blue)
        b.actionRow("计入收支", value: s.value("计入收支", "计入"), action: nil,
                    size: size, height: h, padH: padH, valueX: valueX,
                    actionColor: blue, toggle: true)
        b.bottomFill(Draw.color(0xffeeeeee),
                     canvasHeight: showsTopTitle ? 2388 : 2388 - MockDetailScreen.headH)
    }

    /// 中国 02/04「明细转出2」：转账金额 + 交易成功 + 说明 + 两组明细 + 三个链接。
    private static func detailOutgoing(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细") }
        b.sectionActionRow("转账金额（人民币）", action: "✓ 交易成功", icon: nil,
                           size: dp(13), actionSize: dp(13), padH: dp(12.7),
                           iconSize: .zero, padTop: dp(12), padBottom: dp(2))
        b.leftBlock(s.value("金额", "500.00"), x: dp(12.7), size: dp(27),
                    color: MockInk.title, bold: true, padTop: 0, padBottom: dp(3))
        b.paragraph("收款行处理成功，具体入账情况可凭人行报文号联系对方银行查询。",
                    size: dp(11), color: Draw.color(0xff999999),
                    padH: dp(12.7), padTop: dp(3), padBottom: dp(10))

        let size = dp(13), padH = dp(12.7), valueX = dp(96.5), h: CGFloat = 105.5
        func row(_ label: String, _ value: String) {
            b.fixedRow(label, s.value(label, value), labelX: padH, valueX: valueX,
                       size: size, padV: (h - 46.8) / 2,
                       labelColor: Draw.color(0xff666666), valueColor: Draw.color(0xff333333))
        }
        row("人行报文号", "1041000000042023081423844751")
        row("优惠后费用", "人民币元 0.00")
        b.fill(dp(9), Draw.color(0xffeeeeee))
        row("收款人名称", "李哈哈")
        row("收款账号", "6217 ****** 2284")
        row("收款开户行", "上海浦东发展银行")
        row("付款账户", "张珊珊 6217 ****** 8765")
        row("交易渠道", "手机银行")
        row("交易序号", "44662595972")
        row("交易时间", slashed(s.time))
        b.fill(dp(9), Draw.color(0xffeeeeee))
        b.centeredLink("分享给微信好友", size: dp(14), color: Draw.color(0xff2f6fd0), padV: dp(8))
        b.fill(dp(9), Draw.color(0xffeeeeee))
        b.centeredLink("再转一笔", size: dp(14), color: Draw.color(0xff2f6fd0), padV: dp(8))
        b.fill(dp(9), Draw.color(0xffeeeeee))
        b.centeredLink("和TA的转账记录", size: dp(14), color: Draw.color(0xff2f6fd0), padV: dp(8))
        b.bottomFill(Draw.color(0xffeeeeee),
                     canvasHeight: showsTopTitle ? 2388 : 2388 - MockDetailScreen.headH)
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
            b.paragraph("“交易时间”为该笔交易的提交时间；“记账时间”为后台系统对该笔交易的实际处理时间。",
                        size: dp(13), color: Draw.color(0xff5d5d5d),
                        padH: dp(16), padTop: dp(10), padBottom: dp(10))
        }
    }

    // MARK: - 中国 02/02：分享信息（14sp / 88px）

    private static func share(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("分享信息") }

        b.sectionLabel("转账交易成功", size: dp(15), color: MockInk.title,
                       padH: dp(16), padTop: dp(20), padBottom: 0)
        b.paragraph("此回单为客户自行打印，表示汇款申请已提交，仅供参考。资金到账状态以收款方账户为准。",
                    size: dp(16), color: Draw.color(0xffa8b2b1),
                    padH: dp(16), padTop: dp(10), padBottom: dp(10))

        let size = dp(14)
        let padV = dp(6.27)
        func row(_ label: String, _ value: String) {
            b.kvRow(label, s.value(label, value), labelWidth: dp(130), size: size,
                    padH: dp(16), padV: padV, labelColor: MockInk.value)
        }
        row("转账金额", "100.00 元")
        row("交易日期", "2026/10/24")
        row("收款人名称", "李哈哈")
        row("收款账号", "6226 ****** 9990")
        row("收款银行", "中国工商银行")
        row("付款人名称", "李哈哈")
        row("付款账号", "6226 ****** 9990")
        row("交易序号", "75802482610")

        b.fullButton("分享给微信好友", filled: true, color: Draw.color(0xff2fae4f),
                     height: dp(46), padH: dp(16), gapTop: dp(18), size: dp(16))
    }

    // MARK: - 中国 02/05：支付宝到账回单（12sp / 105px）

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转账金额") }

        b.sectionLabel("转账金额（人民币）", size: dp(13), color: MockInk.title,
                       padH: dp(12), padTop: dp(20), padBottom: 0)
        b.sectionLabel("500.00", size: dp(24), color: MockInk.title,
                       padH: dp(12), padTop: dp(8), padBottom: 0)
        b.paragraph("收款行处理成功，具体入账情况可凭人行报文号联系对方银行查询。",
                    size: dp(12), color: Draw.color(0xff666666),
                    padH: dp(12), padTop: dp(8), padBottom: dp(10))

        let size = dp(12)
        let padV = dp(10.3)
        func row(_ label: String, _ value: String) {
            b.kvRow(label, s.value(label, value), labelWidth: dp(84), size: size,
                    padH: dp(12), padV: padV, labelColor: Draw.color(0xff666666))
        }
        row("人行报文号", "1041000000042023081423844751")
        row("优惠后费用", "人民币元 0.00")
        row("收款人名称", "李哈哈")
        row("收款账号", "6217 ****** 2284")
        row("收款开户行", "上海浦东发展银行")
        row("付款账户", "张珊珊 6217 ****** 8765")
        row("交易渠道", "手机银行")
        row("交易序号", "44662595972")
        row("交易时间", "2026/10/24 21:41:55")
    }

    // MARK: - 中国 02/00：转账结果（14sp 金额、12sp 明细、完成按钮）

    /// 中国 02/00「操作结果」：按参考图（1280×2816，标题栏底 252px）逐块量。
    /// 标题栏 94.5（本页比默认的 156 矮），其下：
    ///   ✓ 100.4  「100.00元交易成功」298  副标题 366  分隔线 424
    ///   优惠后费用 473  付款后/可用余额 579  交易详情 694
    ///   手机盾 banner 785  您可能需要 1182  三行 1329/1430/1530
    ///   更多推荐 1637  广告 1691  完成 1963
    private static func resultV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("操作结果") }

        b.centeredImage("mock_boc_check", width: dp(42.8), height: dp(42.8), gapTop: dp(33.5))
        b.centerBlock("金额", s.value("金额", "100.00元交易成功"), size: dp(15),
                      color: MockInk.title, bold: true, padTop: dp(14.2), padBottom: 0)
        b.centerBlock(nil, "预计10秒内到账，实际时间取决于对方银行", size: dp(11.3),
                      color: Draw.color(0xff999999), padTop: dp(6.7), padBottom: dp(12.6))
        b.hairlineFull()

        let size = dp(13)
        b.fixedRow("优惠后费用", s.value("优惠后费用", "0.00"),
                   labelX: dp(15), valueX: dp(115.3), size: size, padV: dp(7.2),
                   labelColor: Draw.color(0xff666666), valueColor: Draw.color(0xff333333))
        b.hairlineFull()
        b.fixedRow("付款后\n可用余额", s.value("付款后可用余额", "100.00"),
                   labelX: dp(15), valueX: dp(115.3), size: size, padV: dp(5.1),
                   labelColor: Draw.color(0xff666666), valueColor: Draw.color(0xff333333))
        b.hairlineFull()

        b.centeredLink("交易详情", size: dp(14), color: Draw.color(0xff3f6ee5), padV: dp(7.4))

        b.fullWidthImage("mock_boc_banner_r", gapTop: dp(14.5))
        b.bandLabel("您可能需要", height: dp(32.3), bg: Draw.color(0xfff5f5f5),
                    size: dp(12), color: Draw.color(0xff999999), padH: dp(15))
        for item in ["分享给微信好友", "继续转账", "转账记录"] {
            b.chevronRow(item, size: dp(14), color: MockInk.title,
                         padH: dp(15), padV: dp(8.3))
            b.hairlineFull()
        }

        b.sectionActionRow("更多推荐", action: "换一换", icon: "mock_boc_refresh",
                           size: dp(16), actionSize: dp(13), padH: dp(15),
                           iconSize: CGSize(width: dp(11.5), height: dp(11.5)),
                           padTop: dp(8.5), padBottom: dp(5.1))
        b.insetImage("mock_boc_ad_r", padH: dp(12.9), gapTop: 0)
        b.centeredLink("完成", size: dp(15), color: Draw.color(0xffe8452f), padV: dp(11.9))
    }
}
