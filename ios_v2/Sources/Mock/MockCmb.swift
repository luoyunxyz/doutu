import UIKit

/// 「模拟 → 招商模拟」的十二个子页版式。
///
/// 招商用一张 `MODES` 表把 12 个子页归成 5 种版式（`CmbActivity.MODES`）：
///
/// ```text
/// MODES = { 0, 1, 0, 1, 1, 0, 1, 2, 3, 4, 1, 0 }
///          0  1  2  3  4  5  6  7  8  9  10 11
/// ```
///   0 明细详情（转出）· 1 明细详情（转入）· 2 账户 · 3 回单 · 4 余额
///
/// 编辑页与成品页共用这些版式，差别在字号/内边距（成品页大一圈），
/// 以及成品页的「账户 / 余额」那种彩色头部由 `MockDetailScreen` 的白头替代。
enum MockCmb {
    /// 招商只有两页：0 明细转出、1 明细转入。整页贴图上的数字热区。
    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        switch sub {
        case 0:
            return [
                MockPsbc.OverlayField(key: "c0_0", fallback: "", rect: CGRect(x: 420, y: 163, width: 239, height: 58), size: 49, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c0_1", fallback: "", rect: CGRect(x: 476, y: 279, width: 252, height: 78), size: 72, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_2", fallback: "", rect: CGRect(x: 675, y: 588, width: 376, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_3", fallback: "", rect: CGRect(x: 821, y: 678, width: 230, height: 50), size: 39, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_4", fallback: "", rect: CGRect(x: 738, y: 770, width: 313, height: 47), size: 35, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_5", fallback: "", rect: CGRect(x: 963, y: 860, width: 88, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_6", fallback: "", rect: CGRect(x: 892, y: 951, width: 159, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_7", fallback: "", rect: CGRect(x: 821, y: 1041, width: 230, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_8", fallback: "", rect: CGRect(x: 808, y: 1220, width: 235, height: 98), size: 95, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_9", fallback: "", rect: CGRect(x: 1007, y: 1435, width: 36, height: 51), size: 40, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_10", fallback: "", rect: CGRect(x: 1007, y: 1583, width: 36, height: 51), size: 40, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c0_11", fallback: "", rect: CGRect(x: 25, y: 1723, width: 1028, height: 136), size: 110, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c0_12", fallback: "", rect: CGRect(x: 34, y: 1958, width: 85, height: 43), size: 32, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c0_13", fallback: "", rect: CGRect(x: 34, y: 2013, width: 983, height: 44), size: 33, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c0_14", fallback: "", rect: CGRect(x: 34, y: 2069, width: 121, height: 43), size: 32, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c0_15", fallback: "", rect: CGRect(x: 35, y: 2123, width: 693, height: 45), size: 34, color: MockInk.title, align: 1),
            ]
        case 1:
            return [
                MockPsbc.OverlayField(key: "c1_0", fallback: "", rect: CGRect(x: 441, y: 164, width: 198, height: 69), size: 61, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c1_1", fallback: "", rect: CGRect(x: 331, y: 279, width: 417, height: 73), size: 66, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c1_2", fallback: "", rect: CGRect(x: 429, y: 401, width: 221, height: 47), size: 35, color: MockInk.title, align: 1),
                MockPsbc.OverlayField(key: "c1_3", fallback: "", rect: CGRect(x: 587, y: 560, width: 434, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_4", fallback: "", rect: CGRect(x: 662, y: 664, width: 359, height: 47), size: 35, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_5", fallback: "", rect: CGRect(x: 791, y: 765, width: 230, height: 50), size: 39, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_6", fallback: "", rect: CGRect(x: 655, y: 869, width: 366, height: 47), size: 35, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_7", fallback: "", rect: CGRect(x: 861, y: 971, width: 160, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_8", fallback: "", rect: CGRect(x: 862, y: 1073, width: 159, height: 49), size: 38, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_9", fallback: "", rect: CGRect(x: 610, y: 1248, width: 409, height: 50), size: 39, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_10", fallback: "", rect: CGRect(x: 710, y: 1387, width: 303, height: 54), size: 44, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_11", fallback: "", rect: CGRect(x: 826, y: 1516, width: 188, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_12", fallback: "", rect: CGRect(x: 888, y: 1630, width: 21, height: 48), size: 37, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_13", fallback: "", rect: CGRect(x: 945, y: 1861, width: 64, height: 58), size: 49, color: MockInk.title, align: 2),
                MockPsbc.OverlayField(key: "c1_14", fallback: "", rect: CGRect(x: 694, y: 2072, width: 231, height: 48), size: 37, color: MockInk.title, align: 2),
            ]
        default: return []
        }
    }


    /// 与 `CmbActivity.MODES` 完全一致
    static let modes: [Int] = [0, 1, 0, 1, 1, 0, 1, 2, 3, 4, 1, 0]

    static func mode(of sub: Int) -> Int {
        guard sub >= 0, sub < modes.count else { return 0 }
        return modes[sub]
    }

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch mode(of: state.sub) {
        case 2:
            account(b, state, showsTopTitle: showsTopTitle)
        case 3:
            receipt(b, state, showsTopTitle: showsTopTitle)
        case 4:
            balance(b, state, showsTopTitle: showsTopTitle)
        default:
            if state.sub == 0 || state.sub == 1 {
                detailV2(b, state, income: state.sub == 1,
                         showsTopTitle: showsTopTitle)
            } else {
                detail(b, state, income: mode(of: state.sub) == 1,
                       showsTopTitle: showsTopTitle)
            }
        }
        return b.result
    }

    /// `time.substring(0, 10)`（Java 直接取前 10 个字符）
    private static func dayPart(_ text: String) -> String {
        text.count >= 10 ? String(text.prefix(10)) : text
    }

    // MARK: - 0/1 明细详情

    private static func detail(_ b: MockBuilder, _ s: MockState, income: Bool,
                               showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            b.headerBar("明细详情", color: MockInk.value, trailing: "⌕   ⋯")
        }
        b.centerBlock("类型", s.value("类型", income ? "跨行转入" : "跨行转出"),
                      size: isDetail ? dp(17) : dp(16), color: Draw.color(0xff444444),
                      padTop: isDetail ? dp(24) : dp(18),
                      padBottom: isDetail ? dp(4) : dp(2))
        b.centerBlock("金额", s.value("金额", (income ? "+" : "-") + " ¥100.00"),
                      size: isDetail ? dp(32) : dp(28), color: MockInk.amount, bold: true,
                      padTop: dp(2), padBottom: isDetail ? dp(22) : dp(18))

        let rowSize = isDetail ? dp(14) : dp(13)
        let padH = isDetail ? dp(16) : dp(14)
        let padV = isDetail ? dp(12) : dp(10)
        // 编辑页的标签是酒红色 `0xffb08686`，成品页退回灰色（Java 两处写法不同）
        let labelColor = isDetail ? MockInk.label : Draw.color(0xffb08686)

        let rows: [(String, String)] = [
            ("交易账户", "建设银行"),
            ("交易子账户", ""),
            ("交易时间", s.time),
            ("交易地点/附言", ""),
            ("对方账户", "张三"),
            ("记账日", dayPart(s.time)),
            ("账户余额", "¥ 565123.20"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: rowSize,
                      padH: padH, padV: padV, labelColor: labelColor)
        }
    }

    // MARK: - 2 账户

    private static func account(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            b.coloredHeaderBar("账户", bg: Draw.color(0xff2f5fb3), fg: MockInk.white,
                               trailing: "⋯")
        }

        b.tabsRow(["储蓄账户", "信用卡", "其他账户"], selected: 0,
                  size: isDetail ? dp(15) : dp(14),
                  padH: isDetail ? dp(16) : dp(14),
                  padTop: isDetail ? dp(14) : dp(12),
                  padBottom: isDetail ? dp(8) : dp(6))

        b.gradientPanel(gapTop: isDetail ? dp(0) : dp(6),
                        margin: isDetail ? dp(12) : dp(10),
                        pad: isDetail ? dp(16) : dp(14),
                        radius: dp(10),
                        from: Draw.color(0xff2f5fb3),
                        to: Draw.color(0xff4a7fd0)) { card in
            card.leftLine("◉  龙卡通", size: isDetail ? dp(15) : dp(14),
                          color: MockInk.white, padH: 0, padTop: 0, padBottom: 0)
            card.leftLine("6222 *** 5656   查看卡号", size: isDetail ? dp(19) : dp(17),
                          color: MockInk.white, padH: 0,
                          padTop: isDetail ? dp(12) : dp(10), padBottom: dp(4))
            card.leftLine("可用余额(元)", size: isDetail ? dp(12) : dp(11),
                          color: Draw.color(0xffdce8ff), padH: 0, padTop: 0, padBottom: 0)
            card.leftLine(s.value("可用余额", "565,123.20"),
                          size: isDetail ? dp(30) : dp(26), color: MockInk.white,
                          bold: true, padH: 0, padTop: 0, padBottom: 0, key: "可用余额")
            card.actionCells(["详情", "明细", "特约取款", "转账"],
                             size: isDetail ? dp(14) : dp(13),
                             color: MockInk.white,
                             padTop: isDetail ? dp(12) : dp(10))
        }

        // 促销卡只在编辑页出现（Java 的成品页没有这一段）
        if !isDetail {
            b.gradientPanel(gapTop: dp(12), margin: dp(10), pad: dp(14), radius: dp(10),
                            from: Draw.color(0xff6f8fd8),
                            to: Draw.color(0xff9fb8e8)) { promo in
                promo.leftLine("e账户", size: dp(15), color: MockInk.white,
                               padH: 0, padTop: 0, padBottom: 0)
                promo.paragraph("只要您持有工行、农行、中行、建行、交行、邮储银行的境内储蓄卡，即可享受建行的优质投资理财服务，如速盈（货币基金）、专属高收益理财产品、基金投资、专户理财、养老保障产品、账户贵金属等。",
                                size: dp(11), color: Draw.color(0xfff0f4ff),
                                padH: 0, padTop: dp(6), padBottom: 0)
            }
        }
    }

    // MARK: - 3 回单

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            b.centeredHeader("电子回单", size: dp(16), color: MockInk.title,
                             padTop: dp(12), padBottom: dp(10))
        }
        let rows: [(String, String)] = [
            ("付款人", "李哈哈"),
            ("付款账号", "6225 **** **** 1234"),
            ("收款人", "张三"),
            ("收款账号", "6214 **** **** 5678"),
            ("金额", "¥ 100.00"),
            ("用途", "转账"),
            ("交易时间", s.time),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback),
                      size: isDetail ? dp(14) : dp(13),
                      padH: isDetail ? dp(16) : dp(14),
                      padV: isDetail ? dp(12) : dp(10))
        }
    }

    // MARK: - 4 余额

    private static func balance(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let isDetail = b.metrics.isDetail
        if showsTopTitle {
            b.coloredHeaderBar("余额", bg: Draw.color(0xff2f5fb3), fg: MockInk.white)
        }
        b.leftLine("可用余额(元)", size: isDetail ? dp(13) : dp(12),
                   color: MockInk.noteTitle, padH: isDetail ? dp(18) : dp(16),
                   padTop: isDetail ? dp(30) : dp(24), padBottom: isDetail ? 0 : dp(2))
        b.leftLine(s.value("可用余额", "565,123.20"),
                   size: isDetail ? dp(36) : dp(34), color: MockInk.amount, bold: true,
                   padH: isDetail ? dp(18) : dp(16),
                   padTop: isDetail ? dp(4) : dp(0),
                   padBottom: isDetail ? dp(24) : dp(22), key: "可用余额")

        b.pairRow("账户", s.value("账户", "6222 *** 5656"),
                  size: isDetail ? dp(14) : dp(13),
                  padH: isDetail ? dp(16) : dp(14),
                  padV: isDetail ? dp(12) : dp(10),
                  labelColor: isDetail ? MockInk.label : MockInk.noteTitle)
        b.pairRow("账户类型", s.value("账户类型", "储蓄账户"),
                  size: isDetail ? dp(14) : dp(13),
                  padH: isDetail ? dp(16) : dp(14),
                  padV: isDetail ? dp(12) : dp(10),
                  labelColor: isDetail ? MockInk.label : MockInk.noteTitle)
    }

    // MARK: - 招商 04/00、04/01（安卓 DOM 实测：90 / 103px）

    private static func detailV2(_ b: MockBuilder, _ s: MockState, income: Bool,
                                 showsTopTitle: Bool) {
        if showsTopTitle { b.headerBar("明细详情", color: MockInk.value, trailing: "⌕   ⋯") }

        let rowSize = dp(12)
        func row(_ label: String, _ value: String, padH: CGFloat, padV: CGFloat) {
            b.pairRow(label, s.value(label, value), size: rowSize, padH: padH, padV: padV,
                      labelColor: Draw.color(0xffb2b2b2), valueColor: MockInk.title)
        }

        if !income {
            b.sectionLabel("转给李哈哈", size: dp(15), color: MockInk.title,
                           padH: dp(40), padTop: dp(20), padBottom: 0)
            b.sectionLabel("- ¥ 100.00", size: dp(28), color: MockInk.title,
                           padH: dp(40), padTop: dp(4), padBottom: dp(20))
            let padV = dp(7.8)
            row("收款账户", "6226 1225 4625 3990", padH: dp(12), padV: padV)
            row("收款银行", "中国工商银行", padH: dp(12), padV: padV)
            row("付款账户", "6221********3640", padH: dp(12), padV: padV)
            row("转账附言", "转账", padH: dp(12), padV: padV)
            row("转账渠道", "手机银行", padH: dp(12), padV: padV)
            row("转账方式", "电子银行转账", padH: dp(12), padV: padV)

            b.sectionLabel("转账完成", size: dp(14), color: Draw.color(0xff23b956),
                           padH: dp(15), padTop: dp(18), padBottom: 0)
            b.sectionLabel(s.time, size: dp(11), color: Draw.color(0xff9c9c9c),
                           padH: dp(15), padTop: dp(2), padBottom: 0)
            b.centeredLink("通知收款人", size: dp(13), color: Draw.color(0xff849ed3), padV: dp(10))
            b.centeredLink("查看和TA的转账记录", size: dp(13), color: MockInk.title, padV: dp(8))
            b.fullButton("再转一笔", filled: true, color: Draw.color(0xffe8452f),
                         height: dp(44), padH: dp(16), gapTop: dp(14), size: dp(12))
        } else {
            b.centerBlock("户名", s.value("户名", "李哈哈"), size: dp(13),
                          color: Draw.color(0xff939393), padTop: dp(18), padBottom: 0)
            b.sectionLabel("+ ¥100.00", size: dp(26), color: MockInk.title,
                           padH: dp(22), padTop: dp(8), padBottom: 0)
            b.sectionLabel("余额¥100.00", size: dp(12), color: Draw.color(0xff9d9d9d),
                           padH: dp(22), padTop: dp(4), padBottom: dp(10))
            let padV = dp(9.97)
            row("交易卡号", "一卡通 6226********9990", padH: dp(22), padV: padV)
            row("交易时间", s.time, padH: dp(22), padV: padV)
            row("付款银行", "中国工商银行", padH: dp(22), padV: padV)
            row("付款账号", "6226***********9990", padH: dp(22), padV: padV)
            row("转账附言", "手机转账", padH: dp(22), padV: padV)
            row("银行交易类型", "汇入汇款", padH: dp(22), padV: padV)

            b.centeredCells(["给TA转账", "查看往来记录"], height: dp(30), size: dp(12), padV: dp(8))
            b.pairRow("分类", s.value("分类", "其他支出"), size: rowSize, padH: dp(25),
                      padV: dp(8), labelColor: Draw.color(0xffb2b2b2))
            b.pairRow("所属账本", s.value("所属账本", "请选择"), size: rowSize, padH: dp(25),
                      padV: dp(8), labelColor: Draw.color(0xffb2b2b2))
            b.sectionLabel("不计入本月收支", size: rowSize, color: Draw.color(0xffb2b2b2),
                           padH: dp(25), padTop: dp(8), padBottom: 0)
            b.sectionLabel("备注", size: rowSize, color: Draw.color(0xffb2b2b2),
                           padH: dp(25), padTop: dp(10), padBottom: 0)
            b.sectionLabel("记录点什么....", size: dp(14), color: Draw.color(0xffb2b2b2),
                           padH: dp(25), padTop: dp(4), padBottom: 0)
        }
    }
}
