import UIKit

/// 「模拟 → 邮政模拟」的八个子页版式。
///
/// 邮储的编辑页与成品页**共用同一份版式**：两边都调 `PsbcPages`，
/// 成品页只是把 `editable` 传成 false（值不能点改）。所以这里只有一套布局，
/// 编辑页多出来的只有「点文字改值」这一层（由 `MockBuilder` 的 hits 负责）。
///
/// 子页映射（`PsbcActivity.buildPreview()`）：
///   0 转账结果 · 1/2/6 交易详情 · 3/5 汇款明细查询 · 4 转账记录 · 7 电子受理单
enum MockPsbc {

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            resultRef(b, state, showsTopTitle: showsTopTitle)
        case 3:
            resultNew(b, state, showsTopTitle: showsTopTitle)
        case 4:
            recordV2(b, state, showsTopTitle: showsTopTitle)
        case 7:
            receiptV2(b, state, showsTopTitle: showsTopTitle)
        default:
            psbcV2(b, state, showsTopTitle: showsTopTitle)
        }
        return b.result
    }

    /// 整页贴图上的可点数字（邮政各页通用；目前先接 sub 1）
    struct OverlayField {
        let key: String
        let fallback: String
        let rect: CGRect
        let size: CGFloat
        let color: UIColor
        var bg: UIColor = MockInk.white
        let align: Int   // 0 左 1 中 2 右
    }

    static func overlayFields(_ sub: Int) -> [OverlayField] {
        switch sub {
        case 0:
            return [
                OverlayField(key: "p0_0",fallback: "", rect: CGRect(x: 783, y: 209, width: 274, height: 209), size: 110, color: Draw.color(0xFF8AC97A), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p0_1",fallback: "", rect: CGRect(x: 545, y: 460, width: 215, height: 63), size: 55, color: Draw.color(0xFF639363), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p0_2",fallback: "", rect: CGRect(x: 700, y: 599, width: 183, height: 55), size: 45, color: Draw.color(0xFF71996F), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p0_3",fallback: "", rect: CGRect(x: 793, y: 759, width: 258, height: 53), size: 43, color: Draw.color(0xFF818181), bg: Draw.color(0xFFFBFBFB), align: 2),
                OverlayField(key: "p0_4",fallback: "", rect: CGRect(x: 910, y: 844, width: 141, height: 54), size: 44, color: Draw.color(0xFF8E8E8E), bg: Draw.color(0xFFFCFCFC), align: 2),
                OverlayField(key: "p0_5",fallback: "", rect: CGRect(x: 528, y: 930, width: 523, height: 53), size: 43, color: Draw.color(0xFF7B7B7B), bg: Draw.color(0xFFFBFBFB), align: 2),
                OverlayField(key: "p0_6",fallback: "", rect: CGRect(x: 702, y: 1015, width: 348, height: 54), size: 44, color: Draw.color(0xFF828282), bg: Draw.color(0xFFFCFCFC), align: 2),
                OverlayField(key: "p0_7",fallback: "", rect: CGRect(x: 914, y: 1100, width: 137, height: 54), size: 44, color: Draw.color(0xFF7E7E7E), bg: Draw.color(0xFFFBFBFB), align: 2),
                OverlayField(key: "p0_8",fallback: "", rect: CGRect(x: 952, y: 1186, width: 99, height: 54), size: 44, color: Draw.color(0xFF818181), bg: Draw.color(0xFFFBFBFB), align: 2),
                OverlayField(key: "p0_9",fallback: "", rect: CGRect(x: 690, y: 1271, width: 361, height: 54), size: 44, color: Draw.color(0xFF7F7F7F), bg: Draw.color(0xFFFBFBFB), align: 2),
                OverlayField(key: "p0_10",fallback: "", rect: CGRect(x: 22, y: 1413, width: 1035, height: 148), size: 110, color: Draw.color(0xFF4DAD25), bg: Draw.color(0xFFFEFFFD), align: 1),
                OverlayField(key: "p0_11",fallback: "", rect: CGRect(x: 42, y: 1620, width: 1022, height: 442), size: 110, color: Draw.color(0xFF6497C7), bg: Draw.color(0xFFF8FCFD), align: 1),
                OverlayField(key: "p0_12",fallback: "", rect: CGRect(x: 294, y: 2049, width: 751, height: 47), size: 35, color: Draw.color(0xFF9598A1), bg: Draw.color(0xFFFEFEFE), align: 2),
            ]
        case 1:
            return [
                OverlayField(key: "p1_0",fallback: "", rect: CGRect(x: 403, y: 242, width: 349, height: 81), size: 76, color: Draw.color(0xFF030303), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_1",fallback: "", rect: CGRect(x: 768, y: 359, width: 289, height: 107), size: 105, color: Draw.color(0xFF88CA79), bg: Draw.color(0xFFFCFFFE), align: 2),
                OverlayField(key: "p1_2",fallback: "", rect: CGRect(x: 761, y: 558, width: 254, height: 51), size: 40, color: Draw.color(0xFF3A3A3A), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_3",fallback: "", rect: CGRect(x: 625, y: 639, width: 390, height: 52), size: 41, color: Draw.color(0xFF363636), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_4",fallback: "", rect: CGRect(x: 843, y: 721, width: 172, height: 52), size: 41, color: Draw.color(0xFF262626), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_5",fallback: "", rect: CGRect(x: 922, y: 803, width: 93, height: 51), size: 40, color: Draw.color(0xFF4C4C4C), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_6",fallback: "", rect: CGRect(x: 595, y: 885, width: 420, height: 51), size: 40, color: Draw.color(0xFF1D1D1D), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_7",fallback: "", rect: CGRect(x: 768, y: 966, width: 247, height: 52), size: 41, color: Draw.color(0xFF2D2D2D), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p1_8",fallback: "", rect: CGRect(x: 844, y: 1048, width: 171, height: 51), size: 40, color: Draw.color(0xFF2C2C2C), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        case 2:
            return [
                OverlayField(key: "p2_0",fallback: "", rect: CGRect(x: 352, y: 285, width: 376, height: 72), size: 65, color: Draw.color(0xFF030303), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p2_1",fallback: "", rect: CGRect(x: 349, y: 360, width: 381, height: 51), size: 40, color: Draw.color(0xFFA3A3A3), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p2_2",fallback: "", rect: CGRect(x: 700, y: 487, width: 315, height: 54), size: 44, color: Draw.color(0xFF2E2E2E), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p2_3",fallback: "", rect: CGRect(x: 594, y: 573, width: 421, height: 53), size: 43, color: Draw.color(0xFF171717), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p2_4",fallback: "", rect: CGRect(x: 915, y: 658, width: 99, height: 54), size: 44, color: Draw.color(0xFF282828), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p2_5",fallback: "", rect: CGRect(x: 916, y: 744, width: 99, height: 53), size: 43, color: Draw.color(0xFF1E1E1E), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p2_6",fallback: "", rect: CGRect(x: 701, y: 829, width: 314, height: 53), size: 43, color: Draw.color(0xFF2F2F2F), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p2_7",fallback: "", rect: CGRect(x: 750, y: 914, width: 265, height: 55), size: 45, color: Draw.color(0xFF252525), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p2_8",fallback: "", rect: CGRect(x: 706, y: 1000, width: 308, height: 54), size: 44, color: Draw.color(0xFF222222), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        case 3:
            return [
                OverlayField(key: "p3_0",fallback: "", rect: CGRect(x: 782, y: 230, width: 307, height: 191), size: 110, color: Draw.color(0xFF96D5B8), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_1",fallback: "", rect: CGRect(x: 825, y: 437, width: 264, height: 135), size: 110, color: Draw.color(0xFFFB834B), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_2",fallback: "", rect: CGRect(x: 883, y: 666, width: 131, height: 41), size: 29, color: Draw.color(0xFFF06961), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p3_3",fallback: "", rect: CGRect(x: 442, y: 694, width: 541, height: 57), size: 48, color: Draw.color(0xFF5B6F61), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_4",fallback: "", rect: CGRect(x: 901, y: 886, width: 150, height: 57), size: 48, color: Draw.color(0xFF1D1D1D), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_5",fallback: "", rect: CGRect(x: 463, y: 976, width: 588, height: 55), size: 45, color: Draw.color(0xFF0C0C0C), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_6",fallback: "", rect: CGRect(x: 768, y: 1064, width: 283, height: 57), size: 48, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_7",fallback: "", rect: CGRect(x: 906, y: 1154, width: 145, height: 56), size: 46, color: Draw.color(0xFF101010), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_8",fallback: "", rect: CGRect(x: 947, y: 1243, width: 103, height: 56), size: 46, color: Draw.color(0xFF1C1C1C), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_9",fallback: "", rect: CGRect(x: 946, y: 1332, width: 104, height: 57), size: 48, color: Draw.color(0xFF1F1F1F), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_10",fallback: "", rect: CGRect(x: 723, y: 1422, width: 328, height: 55), size: 45, color: Draw.color(0xFF282828), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_11",fallback: "", rect: CGRect(x: 890, y: 1511, width: 161, height: 55), size: 45, color: Draw.color(0xFF0F0F0F), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_12",fallback: "", rect: CGRect(x: 667, y: 1600, width: 384, height: 56), size: 46, color: Draw.color(0xFF0C0C0C), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p3_13",fallback: "", rect: CGRect(x: 550, y: 1769, width: 483, height: 142), size: 110, color: Draw.color(0xFF025936), bg: Draw.color(0xFFFFFFFE), align: 2),
            ]
        case 4:
            return [
                OverlayField(key: "p4_0",fallback: "", rect: CGRect(x: 389, y: 257, width: 301, height: 66), size: 59, color: Draw.color(0xFF040404), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p4_1",fallback: "", rect: CGRect(x: 387, y: 324, width: 305, height: 48), size: 37, color: Draw.color(0xFF999999), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p4_2",fallback: "", rect: CGRect(x: 697, y: 451, width: 306, height: 54), size: 44, color: Draw.color(0xFF323232), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_3",fallback: "", rect: CGRect(x: 582, y: 573, width: 421, height: 53), size: 43, color: Draw.color(0xFF161616), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_4",fallback: "", rect: CGRect(x: 820, y: 694, width: 180, height: 54), size: 44, color: Draw.color(0xFF232323), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_5",fallback: "", rect: CGRect(x: 903, y: 816, width: 99, height: 54), size: 44, color: Draw.color(0xFF323232), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_6",fallback: "", rect: CGRect(x: 702, y: 937, width: 301, height: 54), size: 44, color: Draw.color(0xFF333333), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_7",fallback: "", rect: CGRect(x: 654, y: 1058, width: 349, height: 55), size: 45, color: Draw.color(0xFF272727), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_8",fallback: "", rect: CGRect(x: 904, y: 1180, width: 98, height: 54), size: 44, color: Draw.color(0xFF474747), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_9",fallback: "", rect: CGRect(x: 962, y: 1377, width: 34, height: 56), size: 46, color: Draw.color(0xFFD0D0D0), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p4_10",fallback: "", rect: CGRect(x: 25, y: 2021, width: 1023, height: 155), size: 110, color: Draw.color(0xFF146F41), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        case 5:
            return [
                OverlayField(key: "p5_0",fallback: "", rect: CGRect(x: 490, y: 218, width: 99, height: 53), size: 43, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p5_1",fallback: "", rect: CGRect(x: 389, y: 300, width: 301, height: 65), size: 57, color: Draw.color(0xFF040404), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p5_2",fallback: "", rect: CGRect(x: 423, y: 367, width: 233, height: 48), size: 37, color: Draw.color(0xFF9A9A9A), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p5_3",fallback: "", rect: CGRect(x: 682, y: 494, width: 306, height: 54), size: 44, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_4",fallback: "", rect: CGRect(x: 574, y: 615, width: 414, height: 54), size: 44, color: Draw.color(0xFF111111), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_5",fallback: "", rect: CGRect(x: 687, y: 737, width: 301, height: 54), size: 44, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_6",fallback: "", rect: CGRect(x: 471, y: 858, width: 514, height: 55), size: 45, color: Draw.color(0xFF222222), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_7",fallback: "", rect: CGRect(x: 888, y: 980, width: 99, height: 54), size: 44, color: Draw.color(0xFF292929), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_8",fallback: "", rect: CGRect(x: 805, y: 1101, width: 180, height: 54), size: 44, color: Draw.color(0xFF252525), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_9",fallback: "", rect: CGRect(x: 631, y: 1297, width: 351, height: 63), size: 55, color: Draw.color(0xFF515151), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_10",fallback: "", rect: CGRect(x: 873, y: 1478, width: 25, height: 55), size: 45, color: Draw.color(0xFFC8C8C8), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p5_11",fallback: "", rect: CGRect(x: 794, y: 2084, width: 210, height: 59), size: 50, color: Draw.color(0xFF538469), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        case 6:
            return [
                OverlayField(key: "p6_0",fallback: "", rect: CGRect(x: 490, y: 238, width: 99, height: 53), size: 43, color: Draw.color(0xFF101010), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p6_1",fallback: "", rect: CGRect(x: 383, y: 320, width: 314, height: 65), size: 57, color: Draw.color(0xFF040404), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p6_2",fallback: "", rect: CGRect(x: 433, y: 387, width: 213, height: 48), size: 37, color: Draw.color(0xFF9B9B9B), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p6_3",fallback: "", rect: CGRect(x: 682, y: 514, width: 306, height: 54), size: 44, color: Draw.color(0xFF323232), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_4",fallback: "", rect: CGRect(x: 575, y: 636, width: 413, height: 53), size: 43, color: Draw.color(0xFF161616), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_5",fallback: "", rect: CGRect(x: 687, y: 757, width: 301, height: 54), size: 44, color: Draw.color(0xFF343434), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_6",fallback: "", rect: CGRect(x: 847, y: 879, width: 141, height: 54), size: 44, color: Draw.color(0xFF2D2D2D), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_7",fallback: "", rect: CGRect(x: 805, y: 1000, width: 182, height: 54), size: 44, color: Draw.color(0xFF1C1C1C), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_8",fallback: "", rect: CGRect(x: 631, y: 1195, width: 351, height: 58), size: 49, color: Draw.color(0xFF545454), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_9",fallback: "", rect: CGRect(x: 798, y: 1360, width: 184, height: 54), size: 44, color: Draw.color(0xFFA9A9A9), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_10",fallback: "", rect: CGRect(x: 873, y: 1522, width: 25, height: 55), size: 45, color: Draw.color(0xFFC9C9C9), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p6_11",fallback: "", rect: CGRect(x: 93, y: 1703, width: 97, height: 55), size: 45, color: Draw.color(0xFF262626), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p6_12",fallback: "", rect: CGRect(x: 91, y: 1811, width: 257, height: 53), size: 43, color: Draw.color(0xFFB4B4B4), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        case 7:
            return [
                OverlayField(key: "p7_0",fallback: "", rect: CGRect(x: 154, y: 176, width: 901, height: 208), size: 110, color: Draw.color(0xFFA7916A), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p7_1",fallback: "", rect: CGRect(x: 576, y: 443, width: 414, height: 54), size: 44, color: Draw.color(0xFF898989), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_2",fallback: "", rect: CGRect(x: 850, y: 495, width: 140, height: 55), size: 45, color: Draw.color(0xFF949494), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_3",fallback: "", rect: CGRect(x: 560, y: 548, width: 430, height: 54), size: 44, color: Draw.color(0xFF8F8F8F), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_4",fallback: "", rect: CGRect(x: 843, y: 600, width: 147, height: 54), size: 44, color: Draw.color(0xFF8B8B8B), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_5",fallback: "", rect: CGRect(x: 683, y: 653, width: 307, height: 54), size: 44, color: Draw.color(0xFF919191), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_6",fallback: "", rect: CGRect(x: 726, y: 705, width: 264, height: 55), size: 45, color: Draw.color(0xFF909090), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_7",fallback: "", rect: CGRect(x: 892, y: 758, width: 98, height: 54), size: 44, color: Draw.color(0xFF959595), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_8",fallback: "", rect: CGRect(x: 726, y: 810, width: 264, height: 54), size: 44, color: Draw.color(0xFF8D8D8D), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_9",fallback: "", rect: CGRect(x: 893, y: 863, width: 97, height: 53), size: 43, color: Draw.color(0xFF929292), bg: Draw.color(0xFFFFFFFF), align: 2),
                OverlayField(key: "p7_10",fallback: "", rect: CGRect(x: 133, y: 960, width: 813, height: 55), size: 45, color: Draw.color(0xFFCE625B), bg: Draw.color(0xFFF3ECEF), align: 1),
                OverlayField(key: "p7_11",fallback: "", rect: CGRect(x: 433, y: 1076, width: 211, height: 209), size: 110, color: Draw.color(0xFF3F4645), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p7_12",fallback: "", rect: CGRect(x: 68, y: 1614, width: 932, height: 243), size: 110, color: Draw.color(0xFFF5A297), bg: Draw.color(0xFFFFFFFF), align: 1),
                OverlayField(key: "p7_13",fallback: "", rect: CGRect(x: 12, y: 1899, width: 1055, height: 95), size: 92, color: Draw.color(0xFF05AB03), bg: Draw.color(0xFFF7F7F6), align: 1),
            ]
        default: return []
        }
    }

    /// `yyyy-MM-dd HH:mm:ss` → `yyyy/MM/dd HH:mm:ss`
    private static func slashed(_ text: String) -> String {
        text.replacingOccurrences(of: "-", with: "/")
    }

    // MARK: - 0 转账结果

    /// 0/00「转账结果」：按用户的 1280×2816 截图量（标题栏底 267）。
    /// 0/03「转账结果（新/天天盈）」：白卡片 + 三个绿色链接 + 九行 + 两个按钮。
    private static func resultNew(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转账结果") }
        let green = Draw.color(0xff1fa02a)
        b.checkBadge(diameter: dp(46), color: Draw.color(0xff2fae4a),
                     gapTop: dp(38), symbolSize: dp(22))
        b.centerBlock("金额", s.value("金额", "¥100.00"), size: dp(24),
                      color: MockInk.title, bold: true, padTop: dp(8), padBottom: 0)
        b.centerBlock(nil, "转账成功", size: dp(15), color: MockInk.title,
                      padTop: 0, padBottom: dp(6))
        b.centeredCells(["继续转账", "转账记录", "转入天天盈"], height: dp(46),
                        size: dp(15), padV: dp(4))
        let size = dp(15), padH = dp(16)
        for (label, fallback) in [("收款人", "李哈哈"), ("收款账号", "6228 4809 0860 0283 7175"),
                                  ("开户行", "中国工商银行"), ("手续费", "¥ 0.00"),
                                  ("受理方式", "实时"), ("附言", "转账"),
                                  ("付款账号", "6228 **** 8185"), ("可用余额", "¥ 19.50"),
                                  ("交易日期", "2026-10-25 21:40")] {
            b.pairRow(label, s.value(label, fallback), size: size, padH: padH, padV: dp(5.6),
                      labelColor: Draw.color(0xff898989), valueColor: MockInk.title)
        }
        b.twoFilledButtons("完成", "通知收款人", color: Draw.color(0xff0c5f3f),
                           height: dp(43.6), padH: dp(12), gap: dp(10),
                           padV: dp(12), radius: dp(4), size: dp(16))
    }

    private static func resultRef(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转账结果") }
        let green = Draw.color(0xff2fa83a)
        b.checkBadge(diameter: dp(46), color: Draw.color(0xff58c04a),
                     gapTop: dp(41), symbolSize: dp(22))
        b.centerBlock("金额与状态", s.value("金额与状态", "¥100.00  转账成功"),
                      size: dp(19), color: green, bold: true,
                      padTop: dp(10), padBottom: dp(8))
        b.dualLinks("继续转账", "转账记录", size: dp(15), color: green,
                    separatorColor: Draw.color(0xffdddddd), padBottom: dp(8))
        let size = dp(15), padH = dp(14)
        for (label, fallback) in [("付款账号：", "6221***8142"), ("收款人：", "周虞虎"),
                                  ("收款账号：", "6217 9958 0020 8094 158"),
                                  ("开户行：", "中国邮政储蓄银行"), ("手续费：", "¥ 0.00"),
                                  ("受理方式：", "实时"), ("交易日期：", "2026/10/25 21:39")] {
            b.pairRow(label, s.value(label, fallback), size: size, padH: padH, padV: dp(5.2))
        }
        b.twoFilledButtons("完成", "电子回单", color: Draw.color(0xff3fae2a),
                           height: dp(43.6), padH: dp(11), gap: dp(10),
                           padV: dp(12), radius: dp(2), size: dp(16))
        b.insetImage("mock_psbc_ad", padH: dp(11.3), gapTop: dp(8))
    }

    private static func result(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("转账结果", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.checkBadge(diameter: dp(90), color: Draw.color(0xff58c04a),
                     gapTop: dp(30), symbolSize: dp(40))
        b.centerBlock("金额与状态", s.value("金额与状态", "¥100.00 转账成功"),
                      size: dp(19), color: Draw.color(0xff1fa02a), bold: true,
                      padTop: dp(20), padBottom: dp(16))
        b.dualLinks("继续转账", "转账记录", size: dp(15),
                    color: Draw.color(0xff1fa02a),
                    separatorColor: Draw.color(0xffdddddd), padBottom: dp(24))
        b.hairlineFull()

        let rows: [(String, String)] = [
            ("付款账号：", "6221***8142"),
            ("收款人：", "周虞虎"),
            ("收款账号：", "6217 9958 0020 8094 158"),
            ("开户行：", "中国邮政储蓄银行"),
            ("手续费：", "¥ 0.00"),
            ("受理方式：", "实时"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 1/2/6 交易详情

    private static func txDetail(_ b: MockBuilder, _ s: MockState, sub: Int,
                                 showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("交易详情", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.centerBlock("金额", s.value("金额", "+ ¥ 100.00"),
                      size: dp(34), color: Draw.color(0xff222222),
                      padTop: dp(34), padBottom: dp(6))
        // 子页 2 的余额文案里多一个空格（Java:106 的两个分支）
        let balance = sub == 2 ? "账户余额 ¥ 10,129.50" : "账户余额¥10,129.50"
        b.centerBlock("账户余额", s.value("账户余额", balance),
                      size: dp(15), color: MockInk.label,
                      padTop: 0, padBottom: dp(30))

        let rows: [(String, String)]
        if sub == 1 {
            rows = [
                ("交易卡号", "6226***9990"),
                ("交易时间", s.time),
                ("摘要", "他行来账"),
                ("付款人", "李强"),
                ("付款账号", "622143551800063640"),
                ("付款行名", "中国工商银行"),
                ("附言", "手机转账"),
            ]
        } else if sub == 2 {
            rows = [
                ("交易卡号", "6226 **** 9990"),
                ("交易时间", s.time),
                ("摘要", "付款"),
                ("付款人", "李强"),
                ("付款账号", "6226 **** 9995"),
                ("付款行名", "中国工商银行"),
                ("附言", "张三支付宝转账"),
            ]
        } else {
            rows = [
                ("交易卡号", "6226 **** 9990"),
                ("交易时间", s.time),
                ("摘要", "转入"),
                ("付款人", "李强"),
                ("付款账号", "6226 **** 9995"),
                ("付款行名", "中国工商银行"),
                ("附言", "跨行转入"),
            ]
        }
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 4 转账记录

    private static func record(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("转账记录", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.centerBlock("金额", s.value("金额", "- ¥ 521.05"),
                      size: dp(34), color: Draw.color(0xff222222),
                      padTop: dp(34), padBottom: dp(6))
        b.centerBlock("账户余额", s.value("账户余额", "账户余额 ¥ 521.05"),
                      size: dp(15), color: MockInk.label,
                      padTop: 0, padBottom: dp(30))

        let rows: [(String, String)] = [
            ("交易卡号", "6217 **** 7856"),
            ("交易时间", s.time),
            ("摘要", "转账汇出"),
            ("收款人", "付泰"),
            ("收款账号", "6217 **** 3918"),
            ("收款银行", "中国邮政储蓄银行"),
            ("附言", "跨行转账"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 3/5 汇款明细查询

    private static func remit(_ b: MockBuilder, _ s: MockState, big: String,
                              showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("汇款明细查询", color: MockInk.value, trailing: "  ⌂ ")
        }
        let rows: [(String, String)] = [
            ("交易日期", s.time),
            ("付款人", "李强"),
            ("付款账号", "6226 0222 0000 0646 9990"),
            ("收款人", "张大囯"),
            ("收款账号", "6226 0222 0000 0646 9990"),
            ("收款银行", "中国工商银行"),
            ("转账金额", "¥" + big),
            ("手续费", "¥0.00"),
            ("交易状态", "交易成功"),
            ("受理方式", "实时"),
        ]
        for (label, fallback) in rows {
            b.pairRow(label, s.value(label, fallback), size: dp(15),
                      padH: dp(14), padV: dp(11))
        }
    }

    // MARK: - 7 电子受理单

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("电子受理单", color: MockInk.value, trailing: "  ⌂ ")
        }
        let rows: [(String, String)] = [
            ("受理单号", "2023021298473050"),
            ("转账金额", "¥ 100.00"),
            ("金额大写", "壹仟元"),
            ("手续费", "¥ 0.00"),
            ("收款人姓名", "李哈哈"),
            ("收款人账号", "6226 **** 3946"),
            ("收款人银行", "中国工商银行"),
            ("交易类型", "实时跨行转账"),
        ]
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(10),
                bg: Draw.color(0xffdbe6fb)) { inner in
            inner.gap(dp(6))
            inner.brandRow(logo: "mock_psbc_logo", logoHeight: dp(24),
                           qr: "mock_psbc_qr",
                           qrSize: CGSize(width: dp(36), height: dp(44)),
                           padH: dp(14))
            for (label, fallback) in rows {
                inner.pairRow(label, s.value(label, fallback), size: dp(15),
                              padH: dp(14), padV: dp(11))
            }
        }
    }

    // MARK: - 1/2/5/6/7 交易详情（安卓 03_01/02/05/06/07 实测）

    private static func psbcV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let sub = s.sub
        if showsTopTitle { b.headerBar("交易详情", color: MockInk.value, trailing: "  ⌂ ") }

        let padH: CGFloat = (sub == 1 || sub == 2) ? dp(24) : (sub == 7 ? dp(28) : dp(33))
        if sub == 5 || sub == 6 {
            b.sectionLabel(s.value("户名", "张三"), size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
        }

        let amount: String
        let amountSize: CGFloat
        let balance: String
        switch sub {
        case 1:
            amount = "+ ¥ 100.00"; amountSize = dp(28); balance = "账户余额¥10,129.50"
        case 2:
            amount = "+ ¥ 100.00"; amountSize = dp(24); balance = "账户余额 ¥ 10,129.50"
        case 5:
            amount = "-￥521.05"; amountSize = dp(21); balance = "余额￥521.05"
        case 6:
            amount = "+￥521.05"; amountSize = dp(21); balance = "余额￥51.05"
        default:
            amount = "+￥521.05"; amountSize = dp(21); balance = "余额￥51.05"
        }
        b.sectionLabel(s.value("金额", amount), size: amountSize, color: MockInk.title,
                       padH: padH, padTop: sub == 5 || sub == 6 ? dp(8) : dp(24),
                       padBottom: 0)
        b.sectionLabel(balance, size: dp(12), color: Draw.color(0xff898989),
                       padH: padH, padTop: dp(4), padBottom: dp(8))

        let size = sub == 1 ? dp(13) : dp(14)
        let padV = sub == 1 ? dp(5.87) : (sub == 2 ? dp(5.77) : dp(11.77))
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: padH, padV: padV,
                      labelColor: Draw.color(0xff898989), valueColor: MockInk.title)
        }

        switch sub {
        case 1:
            row("交易卡号", "6226***9990")
            row("交易时间", s.time)
            row("摘要", "他行来账")
            row("付款人", "李强")
            row("付款账号", "622143551800063640")
            row("付款行名", "中国工商银行")
            row("附言", "手机转账")
        case 2:
            row("交易卡号", "6226 **** 9990")
            row("交易时间", s.time)
            row("摘要", "付款")
            row("付款人", "李强")
            row("付款账号", "6226 **** 9995")
            row("付款行名", "中国工商银行")
            row("附言", "张三支付宝转账")
        case 5:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("收款账号", "6217 **** 3918")
            row("收款银行", "中国农业银行股份有限公司")
            row("附言", "付款")
            row("摘要", "跨行汇出")
            b.sectionLabel("交易类型", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("不计入本月收支", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(24), padBottom: 0)
        case 6:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("付款账号", "6217 **** 3918")
            row("附言", "无附言")
            row("摘要", "他行汇入")
            b.sectionLabel("交易类型", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("所属账本", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(24), padBottom: 0)
            b.sectionLabel("不计入本月收支", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(24), padBottom: 0)
            b.sectionLabel("附注", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(24), padBottom: 0)
        case 7:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("摘要", "转账汇出")
            row("收款人", "付泰")
            row("收款账号", "6217 **** 3918")
            row("收款银行", "中国邮政储蓄银行")
            row("附言", "转账")
        default:
            row("交易卡号", "6217 **** 7856")
            row("交易时间", s.time)
            row("付款账号", "6217 **** 3918")
            row("附言", "无附言")
            row("摘要", "他行汇入")
            b.sectionLabel("交易类型", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("所属账本", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("不计入本月收支", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("附注", size: dp(14), color: MockInk.title,
                           padH: padH, padTop: dp(18), padBottom: 0)
            b.sectionLabel("记录点什么...", size: dp(14), color: Draw.color(0xffaeaeaf),
                           padH: padH, padTop: dp(6), padBottom: 0)
        }
    }

    // MARK: - 03/00 转账结果（14sp / 85px）

    private static func resultV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("转账结果", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.checkBadge(diameter: dp(40), color: Draw.color(0xff58c04a),
                     gapTop: dp(18), symbolSize: dp(22))
        b.centerBlock("金额与状态", s.value("金额与状态", "¥100.00 转账成功"),
                      size: dp(17), color: Draw.color(0xff2c892e), bold: false,
                      padTop: dp(12), padBottom: dp(6))
        b.dualLinks("继续转账", "转账记录", size: dp(14),
                    color: Draw.color(0xff2c892e),
                    separatorColor: Draw.color(0xffdddddd), padBottom: dp(12))
        b.hairlineFull()

        let size = dp(14)
        let padV = dp(5.77)
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: dp(12), padV: padV,
                      labelColor: Draw.color(0xff6a6a6a), valueColor: Draw.color(0xff6c6c6c))
        }
        row("付款账号：", "6221***8142")
        row("收款人：", "周虞虎")
        row("收款账号：", "6217 9958 0020 8094 158")
        row("开户行：", "中国邮政储蓄银行")
        row("手续费：", "¥ 0.00")
        row("受理方式：", "实时")
        row("交易日期：", "2026/10/24 21:41")

        b.twoButtons("完成", "电子回单")
    }

    // MARK: - 03/03 电子回单（14sp / 52px）

    private static func receiptV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("电子回单", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.paragraph("交易已受理，请查询余额和交易明细",
                    size: dp(15), color: Draw.color(0xff35910a),
                    padH: dp(20), padTop: dp(20), padBottom: dp(0))
        b.paragraph("实际到账时间以收款方银行到账时间为准",
                    size: dp(14), color: Draw.color(0xff959595),
                    padH: dp(20), padTop: dp(8), padBottom: dp(10))

        let size = dp(14)
        let padV = dp(0.27)
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: dp(32), padV: padV,
                      labelColor: Draw.color(0xff7f7f7f), valueColor: Draw.color(0xff3c3c3c))
        }
        row("交易时间：", slashed(s.time))
        row("收款人：", "李哈哈")
        row("收款账号：", "6226 **** 3946")
        row("转账金额：", "¥ 100.00")
        row("附言：", "转账")
        row("开户行：", "中国工商银行")
        row("付款人：", "李强")
        row("付款账号：", "6226 **** 9990")
        row("受理方式：", "实时")

        b.paragraph("此单不作为收付款双方交易确认的最终依据",
                    size: dp(14), color: Draw.color(0xffea2d1d),
                    padH: dp(22), padTop: dp(10), padBottom: dp(10))
        b.twoButtons("保存", "分享")
    }

    // MARK: - 03/04 转账成功（15sp / 89px）

    private static func recordV2(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle {
            b.headerBar("转账成功", color: MockInk.value, trailing: "  ⌂ ")
        }
        b.centerBlock("金额", s.value("金额", "¥ 100.00"), size: dp(26),
                      color: MockInk.title, bold: false, padTop: dp(20), padBottom: 0)
        b.centerBlock(nil, "转账成功", size: dp(16), color: MockInk.title,
                      padTop: dp(4), padBottom: dp(8))
        b.centeredCells(["继续转账", "转账记录", "转入天天盈"],
                        height: dp(30), size: dp(15), padV: dp(8))

        let size = dp(15)
        let padV = dp(5.83)
        func row(_ label: String, _ value: String) {
            b.pairRow(label, s.value(label, value), size: size, padH: dp(12), padV: padV,
                      labelColor: Draw.color(0xffb9b9b9), valueColor: MockInk.title)
        }
        row("收款人", "李哈哈")
        row("收款账号", "6228 4809 0860 0283 7175")
        row("开户行", "中国工商银行")
        row("手续费", "￥ 0.00")
        row("受理方式", "实时")
        row("附言", "转账")
        row("付款账号", "6228 **** 8185")
        row("可用余额", "￥ 19.50")
        row("交易日期", "2026-10-24 21:42")

        b.twoButtons("完成", "通知收款人")
    }
}
