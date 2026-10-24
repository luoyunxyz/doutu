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
            detail(b, state, income: mode(of: state.sub) == 1,
                   showsTopTitle: showsTopTitle)
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
}
