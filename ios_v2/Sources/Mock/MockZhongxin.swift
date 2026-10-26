import UIKit

/// 「模拟 → 中信模拟」的子页版式（入口 10）。
///
/// 5 个子页（设备抽屉）：`明细转出1(旧) / 明细转入1(旧) / 明细转出2(旧) /
/// 明细转出(新) / 明细转入(新)`。
/// 0 号是「银行卡转账」成功页：红头、绿勾、七行明细（带冒号）、
/// 「猜您需要」三条入口、「返回首页」红按钮。
enum MockZhongxin {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "z0_0", fallback: "", rect: CGRect(x: 437, y: 414, width: 203, height: 59), size: 47, color: Draw.color(0xFF0F1010), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z0_1", fallback: "", rect: CGRect(x: 355, y: 555, width: 123, height: 49), size: 36, color: Draw.color(0xFF595959), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_2", fallback: "", rect: CGRect(x: 355, y: 653, width: 439, height: 49), size: 36, color: Draw.color(0xFF434343), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_3", fallback: "", rect: CGRect(x: 357, y: 751, width: 229, height: 50), size: 37, color: Draw.color(0xFF343434), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_4", fallback: "", rect: CGRect(x: 355, y: 850, width: 130, height: 48), size: 35, color: Draw.color(0xFF212121), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_5", fallback: "", rect: CGRect(x: 355, y: 948, width: 91, height: 49), size: 36, color: Draw.color(0xFF1E1E1E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_6", fallback: "", rect: CGRect(x: 355, y: 1046, width: 160, height: 49), size: 36, color: Draw.color(0xFF565656), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_7", fallback: "", rect: CGRect(x: 355, y: 1144, width: 231, height: 50), size: 37, color: Draw.color(0xFF535353), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_8", fallback: "", rect: CGRect(x: 22, y: 1292, width: 159, height: 49), size: 36, color: Draw.color(0xFF545246), bg: Draw.color(0xFFFFFFED), align: 1),
                MockPsbc.OverlayField(key: "z0_9", fallback: "", rect: CGRect(x: 1007, y: 1389, width: 36, height: 68), size: 57, color: Draw.color(0xFFBDBDBD), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_10", fallback: "", rect: CGRect(x: 1007, y: 1533, width: 37, height: 68), size: 57, color: Draw.color(0xFFBFBFBF), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z0_11", fallback: "", rect: CGRect(x: 1007, y: 1677, width: 36, height: 68), size: 57, color: Draw.color(0xFFBDBDBD), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "z1_0", fallback: "", rect: CGRect(x: 143, y: 185, width: 108, height: 86), size: 77, color: Draw.color(0xFF252527), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z1_1", fallback: "", rect: CGRect(x: 357, y: 337, width: 369, height: 91), size: 82, color: Draw.color(0xFF020202), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z1_2", fallback: "", rect: CGRect(x: 327, y: 526, width: 140, height: 55), size: 43, color: Draw.color(0xFF242424), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z1_3", fallback: "", rect: CGRect(x: 329, y: 575, width: 264, height: 55), size: 43, color: Draw.color(0xFF292929), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z1_4", fallback: "", rect: CGRect(x: 327, y: 628, width: 560, height: 49), size: 36, color: Draw.color(0xFF2E2E2E), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z1_5", fallback: "", rect: CGRect(x: 327, y: 718, width: 98, height: 60), size: 48, color: Draw.color(0xFF4A4A4A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z1_6", fallback: "", rect: CGRect(x: 327, y: 904, width: 314, height: 57), size: 45, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z1_7", fallback: "", rect: CGRect(x: 327, y: 1002, width: 139, height: 54), size: 42, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z1_8", fallback: "", rect: CGRect(x: 327, y: 1051, width: 285, height: 54), size: 42, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z1_9", fallback: "", rect: CGRect(x: 326, y: 1231, width: 421, height: 57), size: 45, color: Draw.color(0xFF121212), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z1_10", fallback: "", rect: CGRect(x: 328, y: 1330, width: 597, height: 57), size: 45, color: Draw.color(0xFF111111), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "z2_0", fallback: "", rect: CGRect(x: 322, y: 557, width: 434, height: 66), size: 55, color: Draw.color(0xFF131313), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z2_1", fallback: "", rect: CGRect(x: 332, y: 657, width: 412, height: 93), size: 85, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "z2_2", fallback: "", rect: CGRect(x: 907, y: 776, width: 150, height: 57), size: 45, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z2_3", fallback: "", rect: CGRect(x: 729, y: 875, width: 325, height: 63), size: 52, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z2_4", fallback: "", rect: CGRect(x: 710, y: 1194, width: 292, height: 60), size: 48, color: Draw.color(0xFF747E98), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "z2_5", fallback: "", rect: CGRect(x: 94, y: 1353, width: 225, height: 56), size: 44, color: Draw.color(0xFFB7938C), bg: Draw.color(0xFFFAE7E3), align: 1),
                MockPsbc.OverlayField(key: "z2_6", fallback: "", rect: CGRect(x: 811, y: 1525, width: 145, height: 82), size: 73, color: Draw.color(0xFFFC9A9F), bg: Draw.color(0xFFFCEAE6), align: 2),
                MockPsbc.OverlayField(key: "z2_7", fallback: "", rect: CGRect(x: 127, y: 1992, width: 267, height: 64), size: 53, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFAFAFA), align: 1),
            ]
        }
        return []
    }


    /// 中信红（截图取样）
    static let red = UIColor(red: 0xcc / 255.0, green: 0x2f / 255.0,
                             blue: 0x2a / 255.0, alpha: 1)
    /// 对勾绿
    static let green = UIColor(red: 0x5c / 255.0, green: 0xc0 / 255.0,
                               blue: 0x6b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 五个子页共用「电子回单/转账结果」版式：转入取正、标题按子页名
        let incoming = state.sub == 1 || state.sub == 4
        bankTransfer(b, state, showsTopTitle: showsTopTitle, incoming: incoming)
        return b.result
    }

    private static func bankTransfer(_ b: MockBuilder, _ s: MockState,
                                     showsTopTitle: Bool, incoming: Bool) {
        if showsTopTitle { b.titleBar(incoming ? "明细转入" : "银行卡转账") }

        // 红色顶栏
        b.coloredHeaderBar("银行卡转账", bg: red, fg: .white)

        // 绿勾 + 交易成功
        b.gap(dp(18))
        b.checkBadge(diameter: dp(72), color: green, gapTop: dp(4), symbolSize: dp(40))
        b.sectionLabel("交易成功", size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(12), padBottom: dp(10))

        // 明细（标签带冒号，与截图一致）
        b.fill(dp(8), MockInk.page)
        b.row("收款人：", s.value("收款人", "林上杰"))
        b.row("收款账号：", s.value("收款账号", "6226 ** ** ** ** ** 9990"))
        b.row("收款银行：", s.value("收款银行", "中国工商银行"))
        b.row("到账金额：", s.value("到账金额", incoming ? "100.00" : "100.00"))
        b.row("手续费：", s.value("手续费", "0.00"))
        b.row("转账方式：", s.value("转账方式", "实时到账"))
        b.row("备注：", s.value("备注", "手机银行转账"))

        // 猜您需要
        b.fill(dp(10), MockInk.page)
        b.fill(dp(36), UIColor(red: 0xfd / 255.0, green: 0xf6 / 255.0,
                               blue: 0xdc / 255.0, alpha: 1))
        b.sectionLabel("猜您需要", size: dp(15), color: MockInk.title,
                       padH: dp(16), padTop: dp(8), padBottom: dp(8))
        for title in ["继续转账", "交易明细", "查询余额"] {
            b.chevronRow(title, size: dp(16), color: MockInk.title,
                         padH: dp(16), padV: dp(18))
        }

        // 返回首页
        b.fill(dp(10), MockInk.page)
        b.fullButton("返回首页", filled: true, color: red,
                     height: dp(46), padH: dp(16), gapTop: dp(14), size: dp(17))

        // 猜你喜欢（分割标题）
        b.sectionLabel("猜你喜欢", size: dp(14), color: MockInk.label,
                       padH: 0, padTop: dp(18), padBottom: 0)
        b.gap(dp(20))
    }
}
