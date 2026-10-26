import UIKit

/// 「模拟 → 兴业模拟」的子页版式。
///
/// 兴业这套和六家银行不同：五个子页是
/// `明细转出 / 明细转入 / 回单 / 转账结果 / 明细转出2`，
/// 其中 0 号是**转账结果**页（灰底状态卡 + 明细行 + 电子受理单 + 蓝底操作区），
/// 版式与安卓设备上的截图逐块对应。
///
/// 其余子页还在补：先给出明确的「版式制作中」占位，避免出现空白页。
enum MockXingye {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        switch sub {
        case 0:
            return [
                MockPsbc.OverlayField(key: "x0_0", fallback: "", rect: CGRect(x: 214, y: 154, width: 417, height: 64), size: 56, color: Draw.color(0xFF323234), align: 2),
                MockPsbc.OverlayField(key: "x0_1", fallback: "", rect: CGRect(x: 217, y: 303, width: 329, height: 58), size: 49, color: Draw.color(0xFFA19FA9), align: 2),
                MockPsbc.OverlayField(key: "x0_2", fallback: "", rect: CGRect(x: 857, y: 462, width: 194, height: 56), size: 46, color: Draw.color(0xFF494949), align: 2),
                MockPsbc.OverlayField(key: "x0_3", fallback: "", rect: CGRect(x: 461, y: 578, width: 589, height: 64), size: 56, color: Draw.color(0xFFBEBEC0), align: 2),
                MockPsbc.OverlayField(key: "x0_4", fallback: "", rect: CGRect(x: 451, y: 716, width: 600, height: 56), size: 46, color: Draw.color(0xFF2D2D2D), align: 2),
                MockPsbc.OverlayField(key: "x0_5", fallback: "", rect: CGRect(x: 768, y: 831, width: 283, height: 58), size: 49, color: Draw.color(0xFF232323), align: 2),
                MockPsbc.OverlayField(key: "x0_6", fallback: "", rect: CGRect(x: 670, y: 948, width: 380, height: 56), size: 46, color: Draw.color(0xFF4D4D4D), align: 2),
                MockPsbc.OverlayField(key: "x0_7", fallback: "", rect: CGRect(x: 868, y: 1076, width: 183, height: 57), size: 48, color: Draw.color(0xFF757E93), align: 2),
                MockPsbc.OverlayField(key: "x0_8", fallback: "", rect: CGRect(x: 733, y: 1209, width: 307, height: 47), size: 35, color: Draw.color(0xFFDFBA8C), align: 1),
                MockPsbc.OverlayField(key: "x0_9", fallback: "", rect: CGRect(x: 35, y: 1294, width: 1008, height: 144), size: 122, color: Draw.color(0xFF557ED6), align: 1),
                MockPsbc.OverlayField(key: "x0_10", fallback: "", rect: CGRect(x: 178, y: 1484, width: 722, height: 55), size: 45, color: Draw.color(0xFF427EBA), align: 1),
                MockPsbc.OverlayField(key: "x0_11", fallback: "", rect: CGRect(x: 235, y: 1596, width: 780, height: 53), size: 43, color: Draw.color(0xFFACACAC), align: 2),
                MockPsbc.OverlayField(key: "x0_12", fallback: "", rect: CGRect(x: 891, y: 1656, width: 151, height: 53), size: 43, color: Draw.color(0xFF81AFD0), align: 2),
                MockPsbc.OverlayField(key: "x0_13", fallback: "", rect: CGRect(x: 599, y: 1748, width: 443, height: 195), size: 122, color: Draw.color(0xFFFAA410), align: 2),
            ]
        case 1:
            return [
                MockPsbc.OverlayField(key: "x1_0", fallback: "", rect: CGRect(x: 402, y: 279, width: 264, height: 78), size: 72, color: Draw.color(0xFFA79DD8), align: 1),
                MockPsbc.OverlayField(key: "x1_1", fallback: "", rect: CGRect(x: 451, y: 380, width: 253, height: 83), size: 78, color: Draw.color(0xFF514E67), align: 2),
                MockPsbc.OverlayField(key: "x1_2", fallback: "", rect: CGRect(x: 501, y: 704, width: 553, height: 54), size: 44, color: Draw.color(0xFF67666E), align: 2),
                MockPsbc.OverlayField(key: "x1_3", fallback: "", rect: CGRect(x: 790, y: 822, width: 264, height: 55), size: 45, color: Draw.color(0xFF67666E), align: 2),
                MockPsbc.OverlayField(key: "x1_4", fallback: "", rect: CGRect(x: 872, y: 941, width: 182, height: 54), size: 44, color: Draw.color(0xFF908E97), align: 2),
                MockPsbc.OverlayField(key: "x1_5", fallback: "", rect: CGRect(x: 643, y: 1059, width: 411, height: 54), size: 44, color: Draw.color(0xFF818087), align: 2),
                MockPsbc.OverlayField(key: "x1_6", fallback: "", rect: CGRect(x: 807, y: 1178, width: 248, height: 53), size: 43, color: Draw.color(0xFF7A797F), align: 2),
                MockPsbc.OverlayField(key: "x1_7", fallback: "", rect: CGRect(x: 859, y: 1296, width: 209, height: 63), size: 55, color: Draw.color(0xFF9295AE), align: 2),
                MockPsbc.OverlayField(key: "x1_8", fallback: "", rect: CGRect(x: 1006, y: 1483, width: 41, height: 57), size: 48, color: Draw.color(0xFFFCFCFC), align: 2),
                MockPsbc.OverlayField(key: "x1_9", fallback: "", rect: CGRect(x: 19, y: 1675, width: 1040, height: 143), size: 122, color: Draw.color(0xFF557ED6), align: 1),
                MockPsbc.OverlayField(key: "x1_10", fallback: "", rect: CGRect(x: 20, y: 1838, width: 1039, height: 147), size: 122, color: Draw.color(0xFFFFFFFF), align: 1),
            ]
        case 2:
            return [
                MockPsbc.OverlayField(key: "x2_0", fallback: "", rect: CGRect(x: 814, y: 740, width: 158, height: 59), size: 50, color: Draw.color(0xFF3A3A3A), align: 2),
                MockPsbc.OverlayField(key: "x2_1", fallback: "", rect: CGRect(x: 819, y: 811, width: 153, height: 59), size: 50, color: Draw.color(0xFF4B4B4B), align: 2),
                MockPsbc.OverlayField(key: "x2_2", fallback: "", rect: CGRect(x: 815, y: 882, width: 158, height: 60), size: 51, color: Draw.color(0xFF343434), align: 2),
                MockPsbc.OverlayField(key: "x2_3", fallback: "", rect: CGRect(x: 618, y: 954, width: 355, height: 59), size: 50, color: Draw.color(0xFF787878), align: 2),
                MockPsbc.OverlayField(key: "x2_4", fallback: "", rect: CGRect(x: 673, y: 1025, width: 299, height: 59), size: 50, color: Draw.color(0xFF323232), align: 2),
                MockPsbc.OverlayField(key: "x2_5", fallback: "", rect: CGRect(x: 671, y: 1096, width: 301, height: 60), size: 51, color: Draw.color(0xFF313131), align: 2),
                MockPsbc.OverlayField(key: "x2_6", fallback: "", rect: CGRect(x: 862, y: 1195, width: 110, height: 59), size: 50, color: Draw.color(0xFF494949), align: 2),
                MockPsbc.OverlayField(key: "x2_7", fallback: "", rect: CGRect(x: 618, y: 1266, width: 355, height: 59), size: 50, color: Draw.color(0xFF686868), align: 2),
                MockPsbc.OverlayField(key: "x2_8", fallback: "", rect: CGRect(x: 672, y: 1337, width: 300, height: 59), size: 50, color: Draw.color(0xFF323232), align: 2),
                MockPsbc.OverlayField(key: "x2_9", fallback: "", rect: CGRect(x: 507, y: 1406, width: 465, height: 215), size: 122, color: Draw.color(0xFFE5D3CF), align: 2),
                MockPsbc.OverlayField(key: "x2_10", fallback: "", rect: CGRect(x: 262, y: 1633, width: 555, height: 43), size: 32, color: Draw.color(0xFFB4B4B4), align: 1),
                MockPsbc.OverlayField(key: "x2_11", fallback: "", rect: CGRect(x: 56, y: 1678, width: 964, height: 53), size: 43, color: Draw.color(0xFFA1A5BE), align: 1),
                MockPsbc.OverlayField(key: "x2_12", fallback: "", rect: CGRect(x: 689, y: 1785, width: 276, height: 76), size: 70, color: Draw.color(0xFF667375), align: 2),
                MockPsbc.OverlayField(key: "x2_13", fallback: "", rect: CGRect(x: 31, y: 1978, width: 172, height: 48), size: 37, color: Draw.color(0xFFA0A0A0), align: 1),
                MockPsbc.OverlayField(key: "x2_14", fallback: "", rect: CGRect(x: 31, y: 2043, width: 1012, height: 49), size: 38, color: Draw.color(0xFFA0A0A0), align: 1),
                MockPsbc.OverlayField(key: "x2_15", fallback: "", rect: CGRect(x: 31, y: 2109, width: 1021, height: 49), size: 38, color: Draw.color(0xFFA1A1A1), align: 1),
                MockPsbc.OverlayField(key: "x2_16", fallback: "", rect: CGRect(x: 31, y: 2175, width: 211, height: 49), size: 38, color: Draw.color(0xFFA8A8A8), align: 1),
                MockPsbc.OverlayField(key: "x2_17", fallback: "", rect: CGRect(x: 30, y: 2242, width: 1017, height: 49), size: 38, color: Draw.color(0xFFA2A2A2), align: 1),
                MockPsbc.OverlayField(key: "x2_18", fallback: "", rect: CGRect(x: 31, y: 2308, width: 1000, height: 50), size: 39, color: Draw.color(0xFFA9A9A9), align: 1),
                MockPsbc.OverlayField(key: "x2_19", fallback: "", rect: CGRect(x: 31, y: 2374, width: 987, height: 49), size: 38, color: Draw.color(0xFFA4A4A4), align: 1),
                MockPsbc.OverlayField(key: "x2_20", fallback: "", rect: CGRect(x: 32, y: 2440, width: 354, height: 49), size: 38, color: Draw.color(0xFFA9A9A9), align: 1),
                MockPsbc.OverlayField(key: "x2_21", fallback: "", rect: CGRect(x: 30, y: 2506, width: 1022, height: 50), size: 39, color: Draw.color(0xFFA5A5A5), align: 1),
                MockPsbc.OverlayField(key: "x2_22", fallback: "", rect: CGRect(x: 31, y: 2572, width: 751, height: 49), size: 38, color: Draw.color(0xFFA4A4A4), align: 1),
                MockPsbc.OverlayField(key: "x2_23", fallback: "", rect: CGRect(x: 30, y: 2638, width: 1019, height: 49), size: 38, color: Draw.color(0xFFA1A1A1), align: 1),
                MockPsbc.OverlayField(key: "x2_24", fallback: "", rect: CGRect(x: 31, y: 2705, width: 103, height: 47), size: 35, color: Draw.color(0xFFAAAAAA), align: 1),
                MockPsbc.OverlayField(key: "x2_25", fallback: "", rect: CGRect(x: 31, y: 2770, width: 1017, height: 51), size: 40, color: Draw.color(0xFFA4A4A4), align: 1),
                MockPsbc.OverlayField(key: "x2_26", fallback: "", rect: CGRect(x: 31, y: 2836, width: 139, height: 49), size: 38, color: Draw.color(0xFFA5A5A5), align: 1),
                MockPsbc.OverlayField(key: "x2_27", fallback: "", rect: CGRect(x: 31, y: 2902, width: 1016, height: 49), size: 38, color: Draw.color(0xFFA1A1A1), align: 1),
                MockPsbc.OverlayField(key: "x2_28", fallback: "", rect: CGRect(x: 31, y: 2969, width: 67, height: 48), size: 37, color: Draw.color(0xFFC1C1C1), align: 1),
                MockPsbc.OverlayField(key: "x2_29", fallback: "", rect: CGRect(x: 30, y: 3034, width: 1014, height: 49), size: 38, color: Draw.color(0xFF9D9D9D), align: 1),
                MockPsbc.OverlayField(key: "x2_30", fallback: "", rect: CGRect(x: 31, y: 3100, width: 571, height: 49), size: 38, color: Draw.color(0xFFA7A7A7), align: 1),
                MockPsbc.OverlayField(key: "x2_31", fallback: "", rect: CGRect(x: 31, y: 3166, width: 1017, height: 49), size: 38, color: Draw.color(0xFF9D9D9D), align: 1),
                MockPsbc.OverlayField(key: "x2_32", fallback: "", rect: CGRect(x: 31, y: 3232, width: 1017, height: 49), size: 38, color: Draw.color(0xFF9E9E9E), align: 1),
                MockPsbc.OverlayField(key: "x2_33", fallback: "", rect: CGRect(x: 31, y: 3299, width: 67, height: 47), size: 35, color: Draw.color(0xFFB5B5B5), align: 1),
            ]
        case 3:
            return [
                MockPsbc.OverlayField(key: "x3_0", fallback: "", rect: CGRect(x: 442, y: 169, width: 198, height: 196), size: 122, color: Draw.color(0xFF57C28E), align: 1),
                MockPsbc.OverlayField(key: "x3_1", fallback: "", rect: CGRect(x: 441, y: 401, width: 193, height: 58), size: 49, color: Draw.color(0xFF474747), align: 1),
                MockPsbc.OverlayField(key: "x3_2", fallback: "", rect: CGRect(x: 272, y: 503, width: 550, height: 92), size: 88, color: Draw.color(0xFF010101), align: 1),
                MockPsbc.OverlayField(key: "x3_3", fallback: "", rect: CGRect(x: 824, y: 656, width: 203, height: 60), size: 51, color: Draw.color(0xFF575757), align: 2),
                MockPsbc.OverlayField(key: "x3_4", fallback: "", rect: CGRect(x: 914, y: 764, width: 115, height: 58), size: 49, color: Draw.color(0xFF5A5A5A), align: 2),
                MockPsbc.OverlayField(key: "x3_5", fallback: "", rect: CGRect(x: 727, y: 870, width: 302, height: 60), size: 51, color: Draw.color(0xFF4B4B4B), align: 2),
                MockPsbc.OverlayField(key: "x3_6", fallback: "", rect: CGRect(x: 547, y: 929, width: 483, height: 49), size: 38, color: Draw.color(0xFFAAAAAA), align: 1),
                MockPsbc.OverlayField(key: "x3_7", fallback: "", rect: CGRect(x: 38, y: 1143, width: 1003, height: 149), size: 122, color: Draw.color(0xFFB88F61), align: 1),
                MockPsbc.OverlayField(key: "x3_8", fallback: "", rect: CGRect(x: 583, y: 1348, width: 205, height: 61), size: 52, color: Draw.color(0xFFA2907D), align: 2),
                MockPsbc.OverlayField(key: "x3_9", fallback: "", rect: CGRect(x: 78, y: 1605, width: 258, height: 65), size: 57, color: Draw.color(0xFF333333), align: 1),
                MockPsbc.OverlayField(key: "x3_10", fallback: "", rect: CGRect(x: 417, y: 1747, width: 572, height: 57), size: 48, color: Draw.color(0xFF4C4C4C), align: 2),
                MockPsbc.OverlayField(key: "x3_11", fallback: "", rect: CGRect(x: 797, y: 1840, width: 143, height: 68), size: 60, color: Draw.color(0xFFAF3C3E), align: 2),
                MockPsbc.OverlayField(key: "x3_12", fallback: "", rect: CGRect(x: 753, y: 1921, width: 231, height: 50), size: 39, color: Draw.color(0xFFA9A8A9), align: 2),
            ]
        case 4:
            return [
                MockPsbc.OverlayField(key: "x4_0", fallback: "", rect: CGRect(x: 137, y: 171, width: 506, height: 62), size: 54, color: Draw.color(0xFF657F87), align: 1),
                MockPsbc.OverlayField(key: "x4_1", fallback: "", rect: CGRect(x: 209, y: 307, width: 343, height: 60), size: 51, color: Draw.color(0xFFA0A4B7), align: 2),
                MockPsbc.OverlayField(key: "x4_2", fallback: "", rect: CGRect(x: 816, y: 472, width: 223, height: 59), size: 50, color: Draw.color(0xFF373737), align: 2),
                MockPsbc.OverlayField(key: "x4_3", fallback: "", rect: CGRect(x: 563, y: 562, width: 475, height: 58), size: 49, color: Draw.color(0xFFB6A69B), align: 2),
                MockPsbc.OverlayField(key: "x4_4", fallback: "", rect: CGRect(x: 520, y: 648, width: 519, height: 55), size: 45, color: Draw.color(0xFF181818), align: 2),
                MockPsbc.OverlayField(key: "x4_5", fallback: "", rect: CGRect(x: 757, y: 739, width: 282, height: 57), size: 48, color: Draw.color(0xFF171717), align: 2),
                MockPsbc.OverlayField(key: "x4_6", fallback: "", rect: CGRect(x: 658, y: 832, width: 381, height: 56), size: 46, color: Draw.color(0xFF4F4F4F), align: 2),
                MockPsbc.OverlayField(key: "x4_7", fallback: "", rect: CGRect(x: 870, y: 951, width: 168, height: 57), size: 48, color: Draw.color(0xFFCEC8A6), align: 2),
                MockPsbc.OverlayField(key: "x4_8", fallback: "", rect: CGRect(x: 762, y: 1068, width: 277, height: 42), size: 30, color: Draw.color(0xFFE7C9A2), align: 1),
                MockPsbc.OverlayField(key: "x4_9", fallback: "", rect: CGRect(x: 38, y: 1171, width: 1002, height: 168), size: 122, color: Draw.color(0xFFBEA682), align: 1),
                MockPsbc.OverlayField(key: "x4_10", fallback: "", rect: CGRect(x: 163, y: 1418, width: 751, height: 55), size: 45, color: Draw.color(0xFFC6B3A3), align: 1),
                MockPsbc.OverlayField(key: "x4_11", fallback: "", rect: CGRect(x: 932, y: 1563, width: 123, height: 48), size: 37, color: Draw.color(0xFFD0CECF), align: 2),
                MockPsbc.OverlayField(key: "x4_12", fallback: "", rect: CGRect(x: 675, y: 1612, width: 380, height: 49), size: 38, color: Draw.color(0xFFBEBCBD), align: 2),
                MockPsbc.OverlayField(key: "x4_13", fallback: "", rect: CGRect(x: 17, y: 1662, width: 1036, height: 49), size: 38, color: Draw.color(0xFFBDBBBC), align: 1),
                MockPsbc.OverlayField(key: "x4_14", fallback: "", rect: CGRect(x: 422, y: 1711, width: 632, height: 49), size: 38, color: Draw.color(0xFFC0BEBF), align: 2),
                MockPsbc.OverlayField(key: "x4_15", fallback: "", rect: CGRect(x: 822, y: 1761, width: 232, height: 49), size: 38, color: Draw.color(0xFFC3C1C2), align: 2),
                MockPsbc.OverlayField(key: "x4_16", fallback: "", rect: CGRect(x: 20, y: 1811, width: 320, height: 49), size: 38, color: Draw.color(0xFFBFBDBE), align: 1),
            ]
        default: return []
        }
    }


    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0, 3:
            result(b, state, showsTopTitle: showsTopTitle)
        case 1:
            // 明细转入：与「明细转出2」同版式，金额取正
            transferDetail(b, state, showsTopTitle: showsTopTitle, incoming: true)
        case 2:
            receipt(b, state, showsTopTitle: showsTopTitle)
        default:
            transferDetail(b, state, showsTopTitle: showsTopTitle, incoming: false)
        }
        return b.result
    }

    // MARK: - 子页 2：回单（电子受理单）

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("电子回单") }
        b.sectionLabel("电子受理单", size: dp(17), color: MockInk.title,
                       padH: dp(16), padTop: dp(20), padBottom: dp(6))
        b.row("收款人", s.value("收款人", "张大国"))
        b.row("收款账号", s.value("收款账号", "6226 0222 0000 0646 9990"))
        b.row("收款银行", s.value("收款银行", "中国工商银行"))
        b.row("转账金额", s.value("转账金额", "100.00"))
        b.row("交易时间", s.value("交易时间", s.time))
        b.hairlineFull()
        b.paragraph("本回单为电子凭证，可用于核对转账信息。",
                    size: dp(14), color: MockInk.label,
                    padH: dp(16), padTop: dp(12), padBottom: dp(20))
    }

    // MARK: - 子页 0 / 3：转账结果（`资金已转入对方账户`）

    private static func result(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细转出") }

        // 顶部「完成」
        b.sectionLabel("完成", size: dp(16), color: MockInk.linkBlue,
                       padH: dp(16), padTop: dp(10), padBottom: dp(0))

        // 灰底状态卡：绿勾 + 两句进度
        b.panel(gapTop: dp(10), margin: dp(16), pad: dp(14), bg: MockInk.statusBg) { p in
            p.checkBadge(diameter: dp(22), color: MockInk.green, gapTop: 0, symbolSize: dp(13))
            p.sectionLabel("资金已转入对方账户", size: dp(17), color: MockInk.title,
                           padH: 0, padTop: dp(10), padBottom: 0)
            p.sectionLabel("转账申请已提交", size: dp(15), color: MockInk.label,
                           padH: 0, padTop: dp(14), padBottom: 0)
        }

        // 明细行
        b.gap(dp(6))
        b.row("转账金额", s.value("转账金额", "¥ 100.00"))
        b.row("收款人", s.value("收款人", "张大国"))
        b.row("收款账号", s.value("收款账号", "6226 0222 0000 0646 9990"))
        b.row("收款银行", s.value("收款银行", "中国工商银行"))
        b.row("交易时间", s.value("交易时间", "2026/10/24 19:01"))

        // 电子受理单
        b.hairlineFull()
        b.linkBlock("电子受理单", trailing: "点击查看")

        // 蓝底操作区
        b.fill(dp(10), MockInk.page)
        b.sectionLabel("可用余额 ******", size: dp(13), color: MockInk.orange,
                       padH: dp(16), padTop: dp(12), padBottom: dp(0))
        b.fullButton("通知收款人", filled: true, color: MockInk.icbcRed,
                     height: dp(44), padH: dp(16), gapTop: dp(10), size: dp(16))
        b.dualLinks("保存到相册", "继续转账", size: dp(14),
                    color: MockInk.linkBlue, separatorColor: MockInk.line, padBottom: dp(6))
        b.paragraph("温馨提示：转账已提交仅表明兴业银行已受理转账申请，不代表处理结果，"
                    + "请查询付款账户是否已扣款。",
                    size: dp(13), color: MockInk.label,
                    padH: dp(16), padTop: dp(10), padBottom: dp(12))
    }

    // MARK: - 子页 4：明细转出2（按「明细」模板，字段默认值来自截图）

    private static func transferDetail(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool, incoming: Bool) {
        let m = b.metrics
        if showsTopTitle { b.titleBar(incoming ? "明细转入" : "明细") }

        b.centerBlock("收款人", s.value("收款人", "张大国"),
                      size: m.nameSize, color: MockInk.grey,
                      padTop: m.namePadTop, padBottom: 0)
        b.centerBlock("金额", s.value("金额", incoming ? "+100.00" : "-100.00"),
                      size: m.amountSize, color: MockInk.amount, bold: true,
                      padTop: m.amountPadTop, padBottom: m.amountPadBottom)

        b.statusBar("交易成功", trailing: "进度查询  ›")

        b.row("收款账号", s.value("收款账号", "6226 0222 0000 0646 9990"))
        b.row("收款银行", s.value("收款银行", "中国工商银行"))
        b.divider(m.rowPadH)
        b.row("付款户名", s.value("付款户名", "李东"))
        b.row("付款账号", s.value("付款账号", "6221****3646"))
        b.row("转账方式", s.value("转账方式", "实时转账"))
        b.row("转账附言", s.value("转账附言", "--"))
        b.row("交易时间", s.value("交易时间", s.time))

        b.gap(dp(12))
        b.linkBlock("查看和TA的转账记录", trailing: "›")
        b.twoButtons("再转一笔", "通知收款人")
    }
}
