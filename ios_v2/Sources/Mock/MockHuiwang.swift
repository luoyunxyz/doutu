import UIKit

/// 「模拟 → 汇旺模拟」的子页版式（入口 15）。
///
/// 3 个子页（设备抽屉）：`明细转出 / 明细转入 / 明细转出2`。
/// 0 号是「账单详情」：深色顶栏、浅灰金额区（蓝色大金额 + 已付款）、
/// 白卡七行明细。设备页面上还有一层 `1572.com` 斜纹水印，这里先不画。
enum MockHuiwang {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "w0_0", fallback: "", rect: CGRect(x: 912, y: 203, width: 130, height: 51), size: 38, color: Draw.color(0xFFC45F6C), bg: Draw.color(0xFFF7F0F2), align: 2),
                MockPsbc.OverlayField(key: "w0_1", fallback: "", rect: CGRect(x: 39, y: 307, width: 329, height: 85), size: 76, color: Draw.color(0xFF6598DE), bg: Draw.color(0xFFF1F2F5), align: 2),
                MockPsbc.OverlayField(key: "w0_2", fallback: "", rect: CGRect(x: 513, y: 429, width: 56, height: 48), size: 35, color: Draw.color(0xFFC2C2C2), bg: Draw.color(0xFFF1F1F1), align: 2),
                MockPsbc.OverlayField(key: "w0_3", fallback: "", rect: CGRect(x: 904, y: 625, width: 98, height: 55), size: 43, color: Draw.color(0xFF4A4A4A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w0_4", fallback: "", rect: CGRect(x: 592, y: 747, width: 411, height: 53), size: 41, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w0_5", fallback: "", rect: CGRect(x: 590, y: 868, width: 411, height: 55), size: 43, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w0_6", fallback: "", rect: CGRect(x: 907, y: 990, width: 96, height: 54), size: 42, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w0_7", fallback: "", rect: CGRect(x: 785, y: 1112, width: 218, height: 53), size: 41, color: Draw.color(0xFF353535), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w0_8", fallback: "", rect: CGRect(x: 799, y: 1233, width: 204, height: 54), size: 42, color: Draw.color(0xFF242424), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w0_9", fallback: "", rect: CGRect(x: 969, y: 1355, width: 34, height: 54), size: 42, color: Draw.color(0xFF393939), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "w1_0", fallback: "", rect: CGRect(x: 912, y: 203, width: 129, height: 51), size: 38, color: Draw.color(0xFF8BB5A2), bg: Draw.color(0xFFF2F3F2), align: 2),
                MockPsbc.OverlayField(key: "w1_1", fallback: "", rect: CGRect(x: 40, y: 307, width: 344, height: 85), size: 76, color: Draw.color(0xFFC9403F), bg: Draw.color(0xFFF4F1F3), align: 2),
                MockPsbc.OverlayField(key: "w1_2", fallback: "", rect: CGRect(x: 513, y: 429, width: 56, height: 49), size: 36, color: Draw.color(0xFFC2C2C2), bg: Draw.color(0xFFF1F1F1), align: 2),
                MockPsbc.OverlayField(key: "w1_3", fallback: "", rect: CGRect(x: 904, y: 625, width: 98, height: 55), size: 43, color: Draw.color(0xFF4B4B4B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w1_4", fallback: "", rect: CGRect(x: 489, y: 747, width: 514, height: 53), size: 41, color: Draw.color(0xFF131313), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w1_5", fallback: "", rect: CGRect(x: 590, y: 868, width: 409, height: 55), size: 43, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w1_6", fallback: "", rect: CGRect(x: 862, y: 990, width: 140, height: 54), size: 42, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w1_7", fallback: "", rect: CGRect(x: 785, y: 1112, width: 218, height: 53), size: 41, color: Draw.color(0xFF333333), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w1_8", fallback: "", rect: CGRect(x: 969, y: 1233, width: 34, height: 54), size: 42, color: Draw.color(0xFF515151), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "w2_amt", fallback: "", rect: CGRect(x: 70, y: 318, width: 290, height: 82), size: 64, color: Draw.color(0xFF303030), bg: Draw.color(0xFFFFFFFF), align: 0),
                MockPsbc.OverlayField(key: "w2_0", fallback: "", rect: CGRect(x: 76, y: 279, width: 188, height: 52), size: 40, color: Draw.color(0xFF303030), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "w2_1", fallback: "", rect: CGRect(x: 805, y: 630, width: 224, height: 53), size: 41, color: Draw.color(0xFFA2A2A2), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w2_2", fallback: "", rect: CGRect(x: 802, y: 1072, width: 229, height: 55), size: 43, color: Draw.color(0xFFA0A0A0), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w2_3", fallback: "", rect: CGRect(x: 607, y: 1209, width: 422, height: 54), size: 42, color: Draw.color(0xFFA0A0A0), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w2_4", fallback: "", rect: CGRect(x: 960, y: 1345, width: 69, height: 54), size: 42, color: Draw.color(0xFF9F9F9F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w2_5", fallback: "", rect: CGRect(x: 885, y: 1482, width: 144, height: 54), size: 42, color: Draw.color(0xFFA0A0A0), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "w2_6", fallback: "", rect: CGRect(x: 59, y: 1775, width: 113, height: 48), size: 35, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "w2_7", fallback: "", rect: CGRect(x: 874, y: 1907, width: 157, height: 55), size: 43, color: Draw.color(0xFF9D9D9D), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        return []
    }


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
