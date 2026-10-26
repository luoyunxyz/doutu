import UIKit

/// 「模拟 → 浦发模拟」的子页版式（入口 11）。
///
/// 4 个子页（设备抽屉）：`明细转出1 / 明细转出2 / 明细转入 / 支付宝转入`。
/// 0 号是「转账结果」：蓝描边对勾、金额、三条蓝链接、SIM 盾勾选说明、
/// 「微信通知收款人 / 完成」两个按钮、热门指数 banner。
enum MockPufa {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "p0_0", fallback: "", rect: CGRect(x: 437, y: 414, width: 203, height: 59), size: 47, color: Draw.color(0xFF0F1010), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p0_1", fallback: "", rect: CGRect(x: 355, y: 555, width: 123, height: 49), size: 36, color: Draw.color(0xFF595959), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_2", fallback: "", rect: CGRect(x: 355, y: 653, width: 439, height: 49), size: 36, color: Draw.color(0xFF434343), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_3", fallback: "", rect: CGRect(x: 357, y: 751, width: 229, height: 50), size: 37, color: Draw.color(0xFF343434), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_4", fallback: "", rect: CGRect(x: 355, y: 850, width: 130, height: 48), size: 35, color: Draw.color(0xFF212121), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_5", fallback: "", rect: CGRect(x: 355, y: 948, width: 91, height: 49), size: 36, color: Draw.color(0xFF1E1E1E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_6", fallback: "", rect: CGRect(x: 355, y: 1046, width: 160, height: 49), size: 36, color: Draw.color(0xFF565656), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_7", fallback: "", rect: CGRect(x: 355, y: 1144, width: 231, height: 50), size: 37, color: Draw.color(0xFF535353), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_8", fallback: "", rect: CGRect(x: 22, y: 1292, width: 159, height: 49), size: 36, color: Draw.color(0xFF545246), bg: Draw.color(0xFFFFFFED), align: 1),
                MockPsbc.OverlayField(key: "p0_9", fallback: "", rect: CGRect(x: 1007, y: 1389, width: 36, height: 68), size: 57, color: Draw.color(0xFFBDBDBD), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_10", fallback: "", rect: CGRect(x: 1007, y: 1533, width: 37, height: 68), size: 57, color: Draw.color(0xFFBFBFBF), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p0_11", fallback: "", rect: CGRect(x: 1007, y: 1677, width: 36, height: 68), size: 57, color: Draw.color(0xFFBDBDBD), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "p1_0", fallback: "", rect: CGRect(x: 143, y: 185, width: 108, height: 86), size: 77, color: Draw.color(0xFF252527), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p1_1", fallback: "", rect: CGRect(x: 357, y: 337, width: 369, height: 91), size: 82, color: Draw.color(0xFF020202), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p1_2", fallback: "", rect: CGRect(x: 327, y: 526, width: 140, height: 55), size: 43, color: Draw.color(0xFF242424), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p1_3", fallback: "", rect: CGRect(x: 329, y: 575, width: 264, height: 55), size: 43, color: Draw.color(0xFF292929), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p1_4", fallback: "", rect: CGRect(x: 327, y: 628, width: 560, height: 49), size: 36, color: Draw.color(0xFF2E2E2E), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p1_5", fallback: "", rect: CGRect(x: 327, y: 718, width: 98, height: 60), size: 48, color: Draw.color(0xFF4A4A4A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p1_6", fallback: "", rect: CGRect(x: 327, y: 904, width: 314, height: 57), size: 45, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p1_7", fallback: "", rect: CGRect(x: 327, y: 1002, width: 139, height: 54), size: 42, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p1_8", fallback: "", rect: CGRect(x: 327, y: 1051, width: 285, height: 54), size: 42, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p1_9", fallback: "", rect: CGRect(x: 326, y: 1231, width: 421, height: 57), size: 45, color: Draw.color(0xFF121212), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p1_10", fallback: "", rect: CGRect(x: 328, y: 1330, width: 597, height: 57), size: 45, color: Draw.color(0xFF111111), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "p2_0", fallback: "", rect: CGRect(x: 322, y: 557, width: 434, height: 66), size: 55, color: Draw.color(0xFF131313), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p2_1", fallback: "", rect: CGRect(x: 332, y: 657, width: 412, height: 93), size: 85, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p2_2", fallback: "", rect: CGRect(x: 907, y: 776, width: 150, height: 57), size: 45, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p2_3", fallback: "", rect: CGRect(x: 729, y: 875, width: 325, height: 63), size: 52, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p2_4", fallback: "", rect: CGRect(x: 710, y: 1194, width: 292, height: 60), size: 48, color: Draw.color(0xFF747E98), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p2_5", fallback: "", rect: CGRect(x: 94, y: 1353, width: 225, height: 56), size: 44, color: Draw.color(0xFFB7938C), bg: Draw.color(0xFFFAE7E3), align: 1),
                MockPsbc.OverlayField(key: "p2_6", fallback: "", rect: CGRect(x: 811, y: 1525, width: 145, height: 82), size: 73, color: Draw.color(0xFFFC9A9F), bg: Draw.color(0xFFFCEAE6), align: 2),
                MockPsbc.OverlayField(key: "p2_7", fallback: "", rect: CGRect(x: 127, y: 1992, width: 267, height: 64), size: 53, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFAFAFA), align: 1),
            ]
        }
        if sub == 3 {
            return [
                MockPsbc.OverlayField(key: "p3_0", fallback: "", rect: CGRect(x: 27, y: 184, width: 183, height: 81), size: 72, color: Draw.color(0xFFE0101C), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p3_1", fallback: "", rect: CGRect(x: 348, y: 322, width: 381, height: 91), size: 82, color: Draw.color(0xFF020202), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p3_2", fallback: "", rect: CGRect(x: 894, y: 486, width: 149, height: 57), size: 45, color: Draw.color(0xFF212121), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_3", fallback: "", rect: CGRect(x: 761, y: 584, width: 282, height: 58), size: 46, color: Draw.color(0xFF0A0A0A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p3_4", fallback: "", rect: CGRect(x: 607, y: 687, width: 436, height: 52), size: 40, color: Draw.color(0xFF161616), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "p3_5", fallback: "", rect: CGRect(x: 939, y: 783, width: 104, height: 57), size: 45, color: Draw.color(0xFF232323), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_6", fallback: "", rect: CGRect(x: 537, y: 938, width: 506, height: 64), size: 53, color: Draw.color(0xFF692B2C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_7", fallback: "", rect: CGRect(x: 894, y: 1039, width: 147, height: 56), size: 44, color: Draw.color(0xFF131313), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_8", fallback: "", rect: CGRect(x: 845, y: 1138, width: 198, height: 55), size: 43, color: Draw.color(0xFF080808), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_9", fallback: "", rect: CGRect(x: 590, y: 1237, width: 454, height: 56), size: 44, color: Draw.color(0xFF070707), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_10", fallback: "", rect: CGRect(x: 388, y: 1336, width: 655, height: 56), size: 44, color: Draw.color(0xFF080808), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "p3_11", fallback: "", rect: CGRect(x: 989, y: 1435, width: 54, height: 56), size: 44, color: Draw.color(0xFF0C0C0C), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        return []
    }


    /// 浦发蓝（截图取样）
    static let blue = UIColor(red: 0x1a / 255.0, green: 0x4f / 255.0,
                              blue: 0xd8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 四个子页共用「转账结果」版式：转入取正
        transferResult(b, state, showsTopTitle: showsTopTitle,
                       incoming: state.sub == 2)
        return b.result
    }

    private static func transferResult(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool, incoming: Bool) {
        if showsTopTitle { b.titleBar("转账结果") }

        // 白底顶栏：居中「转账结果」+ 右上「汇款回单」
        b.coloredHeaderBar("转账结果", bg: .white, fg: MockInk.title, trailing: "汇款回单")

        // 蓝描边对勾
        b.gap(dp(26))
        b.checkBadge(diameter: dp(72), color: blue, gapTop: dp(4), symbolSize: dp(40))

        // 转账成功 + 金额
        b.sectionLabel("转账成功", size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(16), padBottom: 0)
        b.sectionLabel(s.value("金额", incoming ? "¥ 15.00" : "¥ 15.00"), size: dp(24),
                       color: MockInk.title, padH: 0, padTop: dp(8), padBottom: 0)

        // 三条蓝色链接
        b.centeredCells(["继续转账", "查看转账记录", "设置面容ID转账"],
                        height: dp(28), size: dp(15), padV: dp(18))

        // SIM 盾说明（复选框用 ✓ 文字近似）
        b.paragraph("☑ 签约SIM盾业务，最高可享500万转账限额，详细说明请见"
                    + "《手机SIM盾使用说明及介绍》。",
                    size: dp(14), color: MockInk.title,
                    padH: dp(16), padTop: dp(6), padBottom: dp(10))

        // 两个按钮
        b.twoButtons("微信通知收款人", "完成")

        // 热门指数
        b.sectionLabel("一键get全网热门指数", size: dp(18), color: MockInk.title,
                       padH: dp(16), padTop: dp(22), padBottom: dp(10))
        b.fullWidthImage("mock_bg", gapTop: 0)
    }
}
