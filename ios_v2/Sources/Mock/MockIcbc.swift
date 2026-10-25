import UIKit

/// 「模拟 → 工商模拟」的六个子页版式。
///
/// 逐块对应 `IcbcActivity.buildRemit()` / `buildDetail()`（编辑页）
/// 与 `IcbcDetailActivity.buildRemit()` / `buildDetail()`（成品页）。
///
/// 工行的版式与农行不同，主要是两点：
///   1. 卡片头是自绘的（`‹` + 灰蓝标题 + 耳机/图标 + `⋯`），不是通用标题条；
///   2. 「标签：值」是**表格样式**——标签固定 110/120dp 宽、右对齐带冒号，值左对齐。
enum MockIcbc {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            remit(b, state, showsTopTitle: showsTopTitle)
        case 2:
            detail02(b, state, showsTopTitle: showsTopTitle)
        case 3:
            detail03(b, state, showsTopTitle: showsTopTitle)
        default:
            detailV2(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    // MARK: - 明细转出1：汇款明细查询（`buildRemit()`）

    private static func remit(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        // 安卓 01_00 实测：16 行、14sp、行距 60px（20 CSS）＝上下各 1.6dp；
        // 标签右对齐列右边缘 ≈160 CSS，值从 ≈166 起。
        let padH = dp(14)
        let padV = dp(1.6)
        let rowSize = dp(14)
        let labelWidth = dp(146)
        let detail = b.metrics.isDetail
        let buttonH = detail ? dp(50) : dp(46)
        let buttonPadH = detail ? dp(14) : dp(12)
        let buttonSize = detail ? dp(17) : dp(16)
        let noteSize = detail ? dp(13) : dp(12)
        let notePad = detail ? dp(14) : dp(12)

        if showsTopTitle {
            b.headerBar("汇款明细查询", color: MockInk.steel,
                        icon: "mock_icbc_headset",
                        iconSize: CGSize(width: dp(20), height: dp(18)),
                        trailing: "  ⋯")
        }

        // Java:313-330 的 16 行（顺序不能动）
        let rows: [(String, String)] = [
            ("交易日期", s.time),
            ("付款人", "李强"),
            ("付款账号", "6221****3640"),
            ("付款账号开户行", "北京东城区工商银行"),
            ("收款人", "李哈哈"),
            ("收款账号", "6221****3640"),
            ("收款银行开户行", "中国农业银行"),
            ("金额", "100.00元"),
            ("币种", "人民币"),
            ("钞汇标志", "现钞"),
            ("手续费*", "0元"),
            ("是否抵扣套餐", "否"),
            ("汇款人附言", "-"),
            ("交易渠道", "手机银行"),
            ("交易状态", "交易成功"),
            ("汇款时间", "实时汇款"),
        ]
        for (label, fallback) in rows {
            // 「交易日期」跟 `time` 走；其余允许改
            let value = label == "交易日期" ? s.value("交易时间", fallback) : s.value(label, fallback)
            b.kvRow(label, value, labelWidth: labelWidth, size: rowSize, padH: padH, padV: padV)
        }

        b.fullButton("再次汇款", filled: true, color: MockInk.icbcRed,
                     height: buttonH, padH: buttonPadH,
                     gapTop: detail ? dp(14) : dp(12), size: buttonSize)
        b.fullButton("电子回单", filled: false, color: MockInk.icbcRed,
                     height: buttonH, padH: buttonPadH,
                     gapTop: detail ? dp(10) : dp(8), size: buttonSize)
        b.noteBlock(title: "—— 温馨提示 ——",
                    body: "1.*手续费：个人转账汇款手续费\n2.支持查询最长五年内的汇款明细、电子回单，如有需要请及时下载回单。",
                    size: noteSize, pad: notePad,
                    gapTop: detail ? dp(14) : dp(12))
    }

    // MARK: - 其余五个子页：明细详情（`buildDetail()`）

    /// 01/03「明细支付宝转入」：金额 -70.00 + 余额 + 7 行 +「查看全部」
    /// + 详情设置 / 为您推荐。
    ///
    /// 参考图 1280×3249（状态栏下 342px 起算），×1080/1280、标题栏 156 之下：
    ///   金额中心 149  余额 233  首行 295（行距 88）
    ///   查看全部 927  灰隔条 993 起；设置 + 推荐整块是从参考图裁的切图。
    private static func detail03(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("明细详情", color: MockInk.grey, icon: "mock_icbc_hdricons",
                        iconSize: CGSize(width: dp(44), height: dp(24)))
        }

        b.centerBlock("金额", s.value("金额", "-70.00"), size: dp(32),
                      color: Draw.color(0xff2e7d6f), padTop: dp(30.4), padBottom: 0)
        b.centerBlock("余额", s.value("余额", "余额：72.09"), size: dp(16),
                      color: Draw.color(0xff555555), padTop: 0, padBottom: 0)

        let size = dp(14), padV = dp(6.27), padH = dp(20)
        func row(_ label: String, _ value: String, color: UIColor = MockInk.value) {
            b.pairRow(label, s.value(label, value), size: size, padH: padH, padV: padV,
                      labelColor: Draw.color(0xff666666), valueColor: color)
        }
        row("交易卡号", "6222****7958")
        row("交易账户", "2108****5507")
        row("交易户名", "21083555555545507")
        row("交易时间", s.time)
        row("业务摘要", "消费", color: Draw.color(0xff3b6d99))
        row("交易场所", "支付宝-杭州易充宝电子商务有限公司")
        row("交易金额", "70.00")

        b.gap(dp(3.7))
        b.collapsibleLink("查看全部", size: dp(14), color: MockInk.grey,
                          padV: dp(6.7), up: false)
        b.gap(dp(5.7))
        // 「详情设置（分类/不计入收支/备注）+ 为您推荐」整块按参考图裁切，
        // 里面的文字/图标太多且随时会变，直接用位图对齐最稳。
        b.fullWidthImage("mock_icbc_setrec", gapTop: 0)
    }

    /// 01/02「明细转入1」：+100.00 收入页（橙字），8 行 + 分隔线 + 3 行对方信息，
    /// 底部灰底上一条蓝色咨询链接。
    ///
    /// 参考图 1280×2816（内容顶 294px），×1080/1280、标题栏 156 之下：
    ///   金额中心 127  首行 308（行距 98）  分隔线 1086
    ///   对方账户 1160 / 户名 1258 / 行别 1356  灰底 1408 起，蓝链接中心 1509
    private static func detail02(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("明细详情", color: Draw.color(0xff888888), icon: "mock_icbc_download",
                        iconSize: CGSize(width: dp(17.7), height: dp(17.7)))
        }

        b.centerBlock("金额", s.value("金额", "+100.00"), size: dp(32),
                      color: Draw.color(0xffff5500), padTop: dp(23.1), padBottom: 0)
        b.gap(dp(25))

        let size = dp(16), padV = dp(6.73), padH = dp(15)
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: padH, padV: padV,
                      labelColor: Draw.color(0xff333333), valueColor: MockInk.value)
        }

        row("交易时间", s.time)
        row("交易卡号", "6221****3640")
        row("业务摘要", "他行汇入")
        // 两行标签，值（CHN）落在第二行
        b.pairRow("交易国家或地区\n简称", s.value("交易国家或地区简称", "\nCHN"),
                  size: size, padH: padH, padV: dp(1.33),
                  labelColor: Draw.color(0xff333333), valueColor: MockInk.value)
        row("交易金额", "100.00")
        row("记账金额", "100.00")
        row("记账币种", "人民币")
        row("交易卡余额", "100.00")

        b.gap(dp(5.7))
        b.hairlineFull()
        b.gap(dp(7.9))

        row("对方账户", "6221****3640")
        row("对方户名", "李哈哈")
        row("对方账户行别", "中国农业银行")

        // 底部灰底 + 蓝色咨询链接（链接中心 1509，灰底从这里一直到画布底）
        b.bottomFill(MockInk.board,
                     canvasHeight: showsTopTitle ? 2388 : 2388 - MockDetailScreen.headH)
        b.centeredLink("对该笔订单有疑问？立即咨询", size: dp(16),
                       color: Draw.color(0xff2f9fd6), padV: dp(24.4))
    }

    private static func detail(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let detail = b.metrics.isDetail
        let plus = s.sub != 1

        if showsTopTitle {
            b.headerBar("明细详情", color: MockInk.value,
                        icon: "mock_icbc_hdricons",
                        iconSize: CGSize(width: dp(44), height: dp(24)))
        }

        let kind = plus ? "跨行转入" : "跨行转出"
        b.centerBlock("类型", s.value("类型", kind),
                      size: detail ? dp(16) : dp(15), color: Draw.color(0xff444444),
                      padTop: detail ? dp(20) : dp(16), padBottom: dp(2))

        let amountText = (plus ? "+" : "-") + (s.sub == 1 ? "70.00" : "100.00")
        let amountColor: UIColor = s.sub == 5
            ? Draw.color(0xffd63c3c)
            : (plus ? Draw.color(0xffe8650e) : Draw.color(0xff2e7d6f))
        b.centerBlock("金额", s.value("金额", amountText),
                      size: detail ? dp(30) : dp(28), color: amountColor, bold: true,
                      padTop: dp(2), padBottom: dp(8))

        if s.sub == 1 || s.sub == 4 || s.sub == 5 {
            let balance = s.sub == 1 ? "余额：72.09" : "余额:100.00"
            b.centerBlock("余额", s.value("余额", balance),
                          size: detail ? dp(14) : dp(13), color: MockInk.grey,
                          padTop: 0, padBottom: detail ? dp(12) : dp(10))
        }

        let padH = detail ? dp(16) : dp(14)
        let padV = detail ? dp(13) : dp(11)
        let rowSize = detail ? dp(15) : dp(14)
        // 编辑页「业务摘要」是蓝字（`rowBlue`）；成品页 Java 里退回了普通色
        let summaryColor = detail ? MockInk.value : MockInk.linkBlue
        let linkSize = detail ? dp(14) : dp(13)
        let linkPadV = detail ? dp(14) : dp(12)

        func pair(_ label: String, _ fallback: String) {
            let color = label == "业务摘要" ? summaryColor : MockInk.value
            b.pairRow(label, s.value(label, fallback), size: rowSize,
                      padH: padH, padV: padV, valueColor: color)
        }

        switch s.sub {
        case 1:
            pair("交易卡号", "6222****7958")
            pair("交易账户", "2108****5507")
            pair("交易户名", "2108355555545507")
            pair("交易时间", s.time)
            pair("业务摘要", "消费")
            pair("交易场所", "支付宝-杭州易充宝电子商务有限公司")
            pair("交易金额", "70.00")
            b.centeredLink("查看全部", size: linkSize, color: MockInk.noteTitle, padV: linkPadV)
            // 两张整宽切图：编辑页有 6/8dp 间距，成品页紧挨着（Java:212-227）
            b.fullWidthImage("mock_icbc_setblock", gapTop: detail ? 0 : dp(6))
            b.fullWidthImage("mock_icbc_recblock", gapTop: detail ? 0 : dp(8))
        case 2:
            pair("交易时间", s.time)
            pair("交易卡号", "6221****3640")
            pair("业务摘要", "他行汇入")
            pair("交易国家或地区简称", "CHN")
            pair("交易金额", "100.00")
            pair("记账金额", "100.00")
            pair("记账币种", "人民币")
            pair("交易卡余额", "100.00")
        case 4:
            pair("交易卡号", "6221****3640")
            pair("交易时间", s.time)
            pair("业务摘要", "银联入账")
            pair("交易场所", "网上银行")
            pair("交易金额", "100.00")
            b.centeredLink("查看更多", size: linkSize, color: MockInk.noteTitle, padV: linkPadV)
            // 三个文字格只在编辑页出现（成品页 Java 里没加）
            if !detail {
                b.centeredCells(["投资理财", "信用卡还款", "查看来往"],
                                height: dp(46), size: dp(11), padV: dp(10))
            }
        case 3:
            pair("交易卡号", "6221****3640")
            pair("交易时间", s.time)
            pair("业务摘要", "支付宝转账")
            pair("交易场所", "支付宝")
            pair("交易金额", "100.00")
            b.centeredLink("查看更多", size: linkSize, color: MockInk.noteTitle, padV: linkPadV)
        default:
            pair("交易卡号", "6222****7958")
            pair("交易账户", "1911****7581")
            pair("交易户名", "李晓晓")
            pair("交易时间", s.time)
            pair("业务摘要", "黄杨少支付宝转账")
            pair("交易详情", "黄杨少支付宝转账")
            pair("交易场所", "支付宝")
            pair("交易金额", "100.00")
            b.centeredLink("查看全部", size: linkSize, color: MockInk.noteTitle, padV: linkPadV)
        }
    }

    // MARK: - 工商明细详情：01_01 / 01_02 / 01_04（安卓 DOM 实测：14sp、33 CSS 行距）

    private static func detailV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("明细详情", color: MockInk.value,
                        icon: "mock_icbc_hdricons",
                        iconSize: CGSize(width: dp(44), height: dp(24)))
        }

        // 安卓 01_03 / 01_05 是「收支详情 + 详情设置」，金额 33sp、余额 15sp；
        // 01_01 / 01_02 / 01_04 是 28sp 金额。
        let isSettings = s.sub == 3 || s.sub == 5
        let amountSize = isSettings ? dp(33) : dp(28)
        let amountColor: UIColor
        switch s.sub {
        case 1, 2, 4: amountColor = Draw.color(0xffff5500)
        case 3: amountColor = Draw.color(0xffc23933)
        default: amountColor = Draw.color(0xff35827c)
        }
        b.centerBlock("金额", s.value("金额", s.sub == 5 ? "-70.00" : "+100.00"),
                      size: amountSize, color: amountColor, bold: false,
                      padTop: dp(17), padBottom: dp(4))
        if s.sub == 2 {
            b.centerBlock("余额", s.value("余额", "余额:100.00"), size: dp(13),
                          color: MockInk.grey, padTop: dp(12), padBottom: dp(2))
        }
        if isSettings {
            b.centerBlock("余额", s.value("余额", s.sub == 5 ? "余额：72.09" : "余额：100.00"),
                          size: dp(15), color: Draw.color(0xff636363),
                          padTop: dp(12), padBottom: dp(2))
        }

        let size = dp(14)
        let padV = dp(8.1)
        let padH = dp(14.4)

        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: padH, padV: padV,
                      labelColor: Draw.color(0xff313131), valueColor: MockInk.value)
        }

        switch s.sub {
        case 1:
            row("交易时间", s.time)
            row("交易卡号", "6221****3640")
            row("业务摘要", "跨行转入")
            row("交易国家或地区简称", "CHN")
            row("交易金额", "100.00")
            row("记账金额", "100.00")
            row("记账币种", "人民币")
            row("交易卡余额", "100.00")
            row("对方账户", "6222****7958")
            row("对方户名", "张三")
            row("对方账户行别", "中国工商银行")
            b.centeredLink("对该笔订单有疑问？立即咨询", size: dp(16),
                           color: Draw.color(0xff009dec), padV: dp(14))
        case 2:
            row("交易卡号", "6221****3640")
            row("交易时间", s.time)
            row("业务摘要", "银联入账")
            row("交易场所", "网上银行")
            row("交易金额", "100.00")
            b.centeredLink("查看更多", size: dp(13), color: MockInk.grey, padV: dp(10))
            b.centeredCells(["投资理财", "信用卡还款", "查看来往"],
                            height: dp(38), size: dp(12), padV: dp(10))
        case 3, 5:
            let sSize = dp(13)
            let sPadV = dp(6.87)
            let sPadH = dp(19)
            func srow(_ label: String, _ value: String) {
                b.pairRow(label, s.value(label, value), size: sSize, padH: sPadH, padV: sPadV,
                          labelColor: Draw.color(0xff636363), valueColor: Draw.color(0xff323232))
            }
            srow("交易卡号", "6222****7958")
            srow("交易账户", "1911****7581")
            srow("交易户名", "李晓晓")
            srow("交易时间", s.time)
            srow("业务摘要", s.sub == 3 ? "黄杨少支付宝转账" : "消费")
            if s.sub == 3 { srow("交易详情", "黄杨少支付宝转账") }
            srow("交易场所", s.sub == 3 ? "支付宝" : "支付宝-杭州易充宝电子商务有限公司")
            srow("交易金额", s.sub == 3 ? "100.00" : "70.00")
            b.centeredLink("查看全部", size: dp(13), color: MockInk.grey, padV: dp(8))
            // 「详情设置 / 分类 / 不计入收支 / 备注 / 为您推荐」用安卓截图切块
            b.fullWidthImage("mock_icbc_setblock", gapTop: 0)
            b.fullWidthImage("mock_icbc_recblock", gapTop: 0)
        default:
            row("交易时间", s.time)
            row("交易卡号", "6221****3640")
            row("业务摘要", "银联入账")
            row("交易详情", "银联入账")
            row("交易国家或地区简称", "CHN")
            row("交易场所", "网上银行")
            row("交易金额", "100.00")
            row("记账金额", "100.00")
            row("记账币种", "人民币")
            row("交易卡余额", "100.00")
            row("对方账户", "6222****7958")
            row("对方户名", "张三")
            row("对方账户行别", "中国工商银行")
            b.centeredLink("对该笔订单有疑问？立即咨询", size: dp(16),
                           color: Draw.color(0xff009dec), padV: dp(14))
        }
    }
}
