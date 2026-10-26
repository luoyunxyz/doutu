import UIKit

/// 「模拟 → 平安模拟」的子页版式（入口 8）。
///
/// 平安的子页：`明细转出(旧) / 明细转出(新) / 明细转入(新) / 明细转出(新) / 转出(新)`，
/// 0 号是「转账交易详情」：深色金额卡 + 三段进度轴 + 明细行 + 底部两个按钮。
/// 颜色（深蓝卡 / 橙色进度与按钮）取自安卓设备截图。
enum MockPingan {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "a0_amt", fallback: "", rect: CGRect(x: 330, y: 295, width: 420, height: 85), size: 62, color: Draw.color(0xFFFFFFFF), bg: Draw.color(0xFF1B1B2F), align: 1),
                MockPsbc.OverlayField(key: "a0_1", fallback: "", rect: CGRect(x: 170, y: 583, width: 738, height: 76), size: 66, color: Draw.color(0xFFE67454), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a0_2", fallback: "", rect: CGRect(x: 784, y: 691, width: 169, height: 52), size: 40, color: Draw.color(0xFF2D2D2D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_3", fallback: "", rect: CGRect(x: 697, y: 772, width: 360, height: 43), size: 30, color: Draw.color(0xFFA5A5A5), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_5", fallback: "", rect: CGRect(x: 82, y: 956, width: 261, height: 46), size: 33, color: Draw.color(0xFFAAAAAA), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a0_6", fallback: "", rect: CGRect(x: 359, y: 1134, width: 91, height: 51), size: 38, color: Draw.color(0xFF5D5D5D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_7", fallback: "", rect: CGRect(x: 359, y: 1215, width: 171, height: 52), size: 40, color: Draw.color(0xFF262626), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_8", fallback: "", rect: CGRect(x: 858, y: 1263, width: 70, height: 47), size: 34, color: Draw.color(0xFF565656), bg: Draw.color(0xFFF5F5F5), align: 2),
                MockPsbc.OverlayField(key: "a0_9", fallback: "", rect: CGRect(x: 359, y: 1343, width: 132, height: 51), size: 38, color: Draw.color(0xFF353535), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_10", fallback: "", rect: CGRect(x: 359, y: 1424, width: 287, height: 56), size: 44, color: Draw.color(0xFF272727), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_11", fallback: "", rect: CGRect(x: 359, y: 1506, width: 93, height: 51), size: 38, color: Draw.color(0xFF535353), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a0_12", fallback: "", rect: CGRect(x: 858, y: 1588, width: 69, height: 51), size: 38, color: Draw.color(0xFF575757), bg: Draw.color(0xFFF6F6F6), align: 2),
                MockPsbc.OverlayField(key: "a0_13", fallback: "", rect: CGRect(x: 928, y: 1730, width: 38, height: 56), size: 44, color: Draw.color(0xFFA4A4A4), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "a1_amt", fallback: "", rect: CGRect(x: 330, y: 295, width: 420, height: 85), size: 62, color: Draw.color(0xFFFFFFFF), bg: Draw.color(0xFF1B1B2F), align: 1),
                MockPsbc.OverlayField(key: "a1_2", fallback: "", rect: CGRect(x: 812, y: 596, width: 176, height: 54), size: 42, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_3", fallback: "", rect: CGRect(x: 730, y: 670, width: 332, height: 43), size: 30, color: Draw.color(0xFFA9A9A9), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_5", fallback: "", rect: CGRect(x: 46, y: 805, width: 175, height: 48), size: 35, color: Draw.color(0xFFA7A7A7), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a1_6", fallback: "", rect: CGRect(x: 365, y: 976, width: 133, height: 51), size: 38, color: Draw.color(0xFF0C0C0C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_7", fallback: "", rect: CGRect(x: 365, y: 1069, width: 172, height: 52), size: 40, color: Draw.color(0xFF0D0D0D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_8", fallback: "", rect: CGRect(x: 912, y: 1115, width: 69, height: 50), size: 37, color: Draw.color(0xFF737373), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_9", fallback: "", rect: CGRect(x: 365, y: 1215, width: 92, height: 52), size: 40, color: Draw.color(0xFF090909), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_10", fallback: "", rect: CGRect(x: 365, y: 1309, width: 289, height: 56), size: 44, color: Draw.color(0xFF0B0B0B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_11", fallback: "", rect: CGRect(x: 46, y: 1419, width: 86, height: 35), size: 21, color: Draw.color(0xFF888888), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a1_12", fallback: "", rect: CGRect(x: 365, y: 1496, width: 629, height: 52), size: 40, color: Draw.color(0xFF0A0A0A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_13", fallback: "", rect: CGRect(x: 365, y: 1545, width: 159, height: 47), size: 34, color: Draw.color(0xFF080808), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a1_14", fallback: "", rect: CGRect(x: 978, y: 1711, width: 39, height: 64), size: 53, color: Draw.color(0xFF9C9C9C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a1_15", fallback: "", rect: CGRect(x: 978, y: 1840, width: 40, height: 63), size: 52, color: Draw.color(0xFF9D9D9D), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "a2_1", fallback: "", rect: CGRect(x: 910, y: 641, width: 132, height: 52), size: 40, color: Draw.color(0xFF323232), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_2", fallback: "", rect: CGRect(x: 469, y: 722, width: 573, height: 52), size: 40, color: Draw.color(0xFF938A89), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_3", fallback: "", rect: CGRect(x: 665, y: 806, width: 377, height: 47), size: 34, color: Draw.color(0xFF919191), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_4", fallback: "", rect: CGRect(x: 330, y: 935, width: 132, height: 53), size: 41, color: Draw.color(0xFF050505), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_5", fallback: "", rect: CGRect(x: 330, y: 1031, width: 94, height: 54), size: 42, color: Draw.color(0xFF070707), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_6", fallback: "", rect: CGRect(x: 330, y: 1129, width: 546, height: 52), size: 40, color: Draw.color(0xFF050505), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_7", fallback: "", rect: CGRect(x: 330, y: 1227, width: 94, height: 53), size: 41, color: Draw.color(0xFF070707), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_8", fallback: "", rect: CGRect(x: 701, y: 1414, width: 314, height: 63), size: 52, color: Draw.color(0xFF1D1D1D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_9", fallback: "", rect: CGRect(x: 1004, y: 1636, width: 37, height: 60), size: 48, color: Draw.color(0xFFCDCDCD), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_10", fallback: "", rect: CGRect(x: 709, y: 1807, width: 332, height: 60), size: 48, color: Draw.color(0xFFA1A1A1), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a2_11", fallback: "", rect: CGRect(x: 764, y: 1977, width: 241, height: 66), size: 55, color: Draw.color(0xFF9C9C9C), bg: Draw.color(0xFFF7F7F7), align: 2),
                MockPsbc.OverlayField(key: "a2_12", fallback: "", rect: CGRect(x: 37, y: 2060, width: 331, height: 48), size: 35, color: Draw.color(0xFFB0B0B0), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 3 {
            return [
                MockPsbc.OverlayField(key: "a3_1", fallback: "", rect: CGRect(x: 910, y: 659, width: 132, height: 52), size: 40, color: Draw.color(0xFF333333), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_2", fallback: "", rect: CGRect(x: 795, y: 740, width: 247, height: 52), size: 40, color: Draw.color(0xFF939393), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_3", fallback: "", rect: CGRect(x: 665, y: 824, width: 377, height: 47), size: 34, color: Draw.color(0xFF8E8E8E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_4", fallback: "", rect: CGRect(x: 330, y: 953, width: 132, height: 53), size: 41, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_5", fallback: "", rect: CGRect(x: 330, y: 1049, width: 94, height: 54), size: 42, color: Draw.color(0xFF333333), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_6", fallback: "", rect: CGRect(x: 330, y: 1147, width: 544, height: 52), size: 40, color: Draw.color(0xFF2A2A2A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_7", fallback: "", rect: CGRect(x: 31, y: 1260, width: 86, height: 35), size: 21, color: Draw.color(0xFFA6A6A6), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a3_8", fallback: "", rect: CGRect(x: 701, y: 1432, width: 314, height: 63), size: 52, color: Draw.color(0xFF3D3D3D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_9", fallback: "", rect: CGRect(x: 1003, y: 1654, width: 38, height: 60), size: 48, color: Draw.color(0xFFCCCCCC), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_10", fallback: "", rect: CGRect(x: 709, y: 1825, width: 332, height: 60), size: 48, color: Draw.color(0xFFA3A3A3), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "a3_11", fallback: "", rect: CGRect(x: 764, y: 1995, width: 240, height: 66), size: 55, color: Draw.color(0xFF9D9D9D), bg: Draw.color(0xFFF7F7F7), align: 2),
                MockPsbc.OverlayField(key: "a3_12", fallback: "", rect: CGRect(x: 37, y: 2078, width: 331, height: 48), size: 35, color: Draw.color(0xFFAFAFAF), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 4 {
            return [
                MockPsbc.OverlayField(key: "a4_0", fallback: "", rect: CGRect(x: 393, y: 271, width: 290, height: 61), size: 50, color: Draw.color(0xFF2B2A2A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a4_1", fallback: "", rect: CGRect(x: 388, y: 376, width: 311, height: 88), size: 79, color: Draw.color(0xFF020202), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a4_2", fallback: "", rect: CGRect(x: 185, y: 517, width: 708, height: 49), size: 36, color: Draw.color(0xFF989898), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a4_3", fallback: "", rect: CGRect(x: 347, y: 605, width: 384, height: 53), size: 41, color: Draw.color(0xFF89A1B1), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "a4_5", fallback: "", rect: CGRect(x: 464, y: 2146, width: 152, height: 48), size: 35, color: Draw.color(0xFFB0B0B0), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        return []
    }


    /// 金额卡底色（截图取样）
    static let navy = UIColor(red: 0x1c / 255.0, green: 0x21 / 255.0,
                              blue: 0x3a / 255.0, alpha: 1)
    /// 进度点与「微信通知收款人」的橙
    static let orange = UIColor(red: 0xf0 / 255.0, green: 0x5a / 255.0,
                                blue: 0x28 / 255.0, alpha: 1)
    /// 卡内次要文字
    static let dimText = UIColor(white: 1, alpha: 0.62)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 五个子页共用「转账交易详情」版式，转入取正
        let incoming = state.sub == 2 || state.sub == 4
        transferDetail(b, state, showsTopTitle: showsTopTitle, incoming: incoming)
        return b.result
    }

    // MARK: - 子页 0：转账交易详情

    private static func transferDetail(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool, incoming: Bool) {
        if showsTopTitle { b.titleBar("转账交易详情") }

        // 深色金额卡
        b.panel(gapTop: dp(12), margin: dp(16), pad: dp(18), bg: navy) { p in
            p.sectionLabel("交易类型：转账", size: dp(13), color: dimText,
                           padH: 0, padTop: 0, padBottom: 0)
            p.sectionLabel(s.value("金额", incoming ? "+ ¥ 100.00" : "- ¥ 100.00"),
                           size: dp(34), color: .white,
                           padH: 0, padTop: dp(12), padBottom: 0)
            p.sectionLabel("手续费：0.00", size: dp(13), color: dimText,
                           padH: 0, padTop: dp(12), padBottom: 0)
        }

        // 三段进度（点 + 文字）
        b.gap(dp(14))
        b.centeredCells(["转账申请提交", "人行处理中", "转账成功"],
                        height: dp(26), size: dp(14), padV: dp(6))
        b.paragraph("\(s.time)                                     \(s.time)",
                    size: dp(12), color: MockInk.label,
                    padH: dp(16), padTop: 0, padBottom: dp(10))

        // 说明
        b.paragraph("我行已转出，如未到账，可拨打平安银行客服95511，并提供指令号进行咨询。",
                    size: dp(14), color: MockInk.value,
                    padH: dp(16), padTop: dp(6), padBottom: dp(10))

        // 明细
        b.row("收款人", s.value("收款人", "胡灿"))
        b.row("收款银行", s.value("收款银行", "工商银行"))
        b.row("", s.value("收款账号", "6222 **** **** 7778"))
        b.row("付款人", s.value("付款人", "李哈哈"))
        b.row("付款银行", s.value("付款银行", "平安银行(7982)"))
        b.row("留言", s.value("留言", "转账"))
        b.row("指令号", s.value("指令号", "7492749274924228"))

        b.chevronRow("查看和胡灿的转账记录", size: dp(15), color: MockInk.title,
                     padH: dp(16), padV: dp(14))
        b.twoButtons("再转一笔", "微信通知收款人")
    }
}
