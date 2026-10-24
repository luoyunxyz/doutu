import UIKit

/// 「模拟 → 汇旺模拟」的子页版式（入口 15）。
///
/// 3 个子页（设备抽屉）：`明细转出 / 明细转入 / 明细转出2`。
/// 0 号是「账单详情」：深色顶栏、浅灰金额区（蓝色大金额 + 已付款）、
/// 白卡七行明细。设备页面上还有一层 `1572.com` 斜纹水印，这里先不画。
enum MockHuiwang {

    /// 顶栏深蓝黑（截图取样）
    static let navy = UIColor(red: 0x1b / 255.0, green: 0x20 / 255.0,
                              blue: 0x2b / 255.0, alpha: 1)
    /// 金额蓝
    static let amountBlue = UIColor(red: 0x4a / 255.0, green: 0x7d / 255.0,
                                    blue: 0xd8 / 255.0, alpha: 1)
    /// 「已付款」红
    static let paidRed = UIColor(red: 0xe0 / 255.0, green: 0x3b / 255.0,
                                 blue: 0x3b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 三个子页共用「账单详情」版式：转入的金额取正、标题不同
        let incoming = state.sub == 1
        billDetail(b, state, showsTopTitle: showsTopTitle, incoming: incoming)
        return b.result
    }

    private static func billDetail(_ b: MockBuilder, _ s: MockState,
                                   showsTopTitle: Bool, incoming: Bool) {
        if showsTopTitle { b.titleBar("账单详情") }

        b.coloredHeaderBar("账单详情", bg: navy, fg: .white)

        // 金额区（浅灰底）
        b.panel(gapTop: 0, margin: 0, pad: dp(16), bg: MockInk.page) { p in
            p.sectionLabel("交易金额                                    已付款",
                           size: dp(15), color: MockInk.label,
                           padH: 0, padTop: dp(6), padBottom: dp(6))
            p.sectionLabel(s.value("金额", incoming ? "100.00 USD" : "-100.00 USD"),
                           size: dp(40), color: amountBlue,
                           padH: 0, padTop: dp(4), padBottom: dp(8))
            p.sectionLabel("交易后的金额：****** USD", size: dp(14),
                           color: MockInk.label, padH: 0, padTop: 0, padBottom: 0)
        }

        // 明细白卡（行距按安卓实测 hw_sub0：14sp + 11.75dp，行距 121px；
        // 121 是文字带 top→top，「间距 84」只是墨迹空隙）
        let rs = dp(14)
        let rp = dp(11.75)
        b.panel(gapTop: dp(14), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.row("账单类型", s.value("账单类型", "转账"), size: rs, padV: rp)
            p.row("账单号", s.value("账单号", "22082179616251015"), size: rs, padV: rp)
            p.row("账单时间", s.value("账单时间", s.time), size: rs, padV: rp)
            p.row("收款人", s.value("收款人", "李**"), size: rs, padV: rp)
            p.row("收款账号", s.value("收款账号", "62*****699"), size: rs, padV: rp)
            p.row("收款方式", s.value("收款方式", "余额-USD"), size: rs, padV: rp)
            p.row("备注", s.value("备注", "-"), size: rs, padV: rp)
        }
        _ = paidRed
    }
}
