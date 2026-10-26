import UIKit

/// 「模拟 → 交通模拟」的子页版式（入口 9）。
///
/// 10 个子页（设备抽屉）：`明细转出1(新) / 明细转入1 / 明细转出2(新) / 支付宝转入2(新) /
/// 明细转入2 / 明细转入3 / 支付宝转入1 / 明细转出3(新) / 回单 / 付款结果`。
/// 0 号是「账号转账」成功页：绿勾、金额、两行明细、三条蓝色链接、推荐卡、活动 banner。
enum MockJiaotong {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "t0_0", fallback: "", rect: CGRect(x: 406, y: 391, width: 264, height: 74), size: 64, color: Draw.color(0xFF5CB391), bg: Draw.color(0xFFFEFFFF), align: 1),
                MockPsbc.OverlayField(key: "t0_1", fallback: "", rect: CGRect(x: 380, y: 538, width: 314, height: 78), size: 68, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t0_2", fallback: "", rect: CGRect(x: 910, y: 814, width: 140, height: 55), size: 43, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t0_3", fallback: "", rect: CGRect(x: 705, y: 887, width: 344, height: 60), size: 48, color: Draw.color(0xFF1B1B1B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t0_4", fallback: "", rect: CGRect(x: 436, y: 1018, width: 590, height: 60), size: 48, color: Draw.color(0xFF5A8DC0), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t0_5", fallback: "", rect: CGRect(x: 35, y: 1180, width: 257, height: 52), size: 40, color: Draw.color(0xFFA0A0A0), bg: Draw.color(0xFFF9F9F9), align: 1),
                MockPsbc.OverlayField(key: "t0_6", fallback: "", rect: CGRect(x: 563, y: 1288, width: 439, height: 59), size: 47, color: Draw.color(0xFFA0A4A6), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t0_7", fallback: "", rect: CGRect(x: 677, y: 1420, width: 325, height: 59), size: 47, color: Draw.color(0xFFA0A2A4), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "t1_0", fallback: "", rect: CGRect(x: 448, y: 165, width: 182, height: 54), size: 42, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t1_1", fallback: "", rect: CGRect(x: 397, y: 259, width: 284, height: 68), size: 57, color: Draw.color(0xFFDB686B), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t1_2", fallback: "", rect: CGRect(x: 420, y: 364, width: 238, height: 54), size: 42, color: Draw.color(0xFF9F9F9F), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t1_3", fallback: "", rect: CGRect(x: 703, y: 471, width: 341, height: 57), size: 45, color: Draw.color(0xFF313131), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_4", fallback: "", rect: CGRect(x: 624, y: 576, width: 420, height: 56), size: 44, color: Draw.color(0xFF2C2C2C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_5", fallback: "", rect: CGRect(x: 863, y: 677, width: 181, height: 59), size: 47, color: Draw.color(0xFF222222), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_6", fallback: "", rect: CGRect(x: 903, y: 781, width: 140, height: 60), size: 48, color: Draw.color(0xFF5F5F5F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_7", fallback: "", rect: CGRect(x: 703, y: 888, width: 341, height: 56), size: 44, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_8", fallback: "", rect: CGRect(x: 863, y: 990, width: 181, height: 59), size: 47, color: Draw.color(0xFF252525), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_9", fallback: "", rect: CGRect(x: 945, y: 1094, width: 98, height: 59), size: 47, color: Draw.color(0xFF222222), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t1_10", fallback: "", rect: CGRect(x: 40, y: 1240, width: 751, height: 49), size: 36, color: Draw.color(0xFFA9A9A9), bg: Draw.color(0xFFF9F9F9), align: 1),
                MockPsbc.OverlayField(key: "t1_11", fallback: "", rect: CGRect(x: 364, y: 1385, width: 331, height: 54), size: 42, color: Draw.color(0xFF778CAB), bg: Draw.color(0xFFF7F9FD), align: 1),
                MockPsbc.OverlayField(key: "t1_12", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE8433B), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "t2_0", fallback: "", rect: CGRect(x: 380, y: 176, width: 318, height: 82), size: 73, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t2_1", fallback: "", rect: CGRect(x: 448, y: 312, width: 181, height: 54), size: 42, color: Draw.color(0xFFA5A5A5), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t2_2", fallback: "", rect: CGRect(x: 916, y: 398, width: 149, height: 65), size: 54, color: Draw.color(0xFF242424), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_3", fallback: "", rect: CGRect(x: 453, y: 501, width: 612, height: 61), size: 50, color: Draw.color(0xFF070707), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_4", fallback: "", rect: CGRect(x: 783, y: 597, width: 282, height: 65), size: 54, color: Draw.color(0xFF1C1C1C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_5", fallback: "", rect: CGRect(x: 604, y: 754, width: 461, height: 62), size: 51, color: Draw.color(0xFF070707), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_6", fallback: "", rect: CGRect(x: 549, y: 851, width: 514, height: 65), size: 54, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_7", fallback: "", rect: CGRect(x: 690, y: 950, width: 374, height: 65), size: 54, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_8", fallback: "", rect: CGRect(x: 953, y: 1053, width: 112, height: 62), size: 51, color: Draw.color(0xFF070707), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_9", fallback: "", rect: CGRect(x: 961, y: 1151, width: 104, height: 64), size: 53, color: Draw.color(0xFF1C1C1C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_10", fallback: "", rect: CGRect(x: 780, y: 1250, width: 285, height: 64), size: 53, color: Draw.color(0xFF1D1D1D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_11", fallback: "", rect: CGRect(x: 475, y: 1353, width: 592, height: 61), size: 50, color: Draw.color(0xFF3D4444), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_12", fallback: "", rect: CGRect(x: 897, y: 1403, width: 164, height: 54), size: 42, color: Draw.color(0xFFA8AFC1), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_13", fallback: "", rect: CGRect(x: 961, y: 1492, width: 104, height: 65), size: 54, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t2_14", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE7443B), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 3 {
            return [
                MockPsbc.OverlayField(key: "t3_0", fallback: "", rect: CGRect(x: 387, y: 195, width: 306, height: 55), size: 43, color: Draw.color(0xFF101010), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t3_1", fallback: "", rect: CGRect(x: 385, y: 297, width: 308, height: 77), size: 67, color: Draw.color(0xFFEC707D), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t3_2", fallback: "", rect: CGRect(x: 431, y: 422, width: 218, height: 53), size: 41, color: Draw.color(0xFF969696), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t3_3", fallback: "", rect: CGRect(x: 701, y: 597, width: 344, height: 55), size: 43, color: Draw.color(0xFF474747), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_4", fallback: "", rect: CGRect(x: 620, y: 674, width: 425, height: 54), size: 42, color: Draw.color(0xFF414141), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_5", fallback: "", rect: CGRect(x: 904, y: 750, width: 141, height: 55), size: 43, color: Draw.color(0xFF565656), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_6", fallback: "", rect: CGRect(x: 947, y: 827, width: 97, height: 54), size: 42, color: Draw.color(0xFF4A4A4A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_7", fallback: "", rect: CGRect(x: 827, y: 904, width: 218, height: 53), size: 41, color: Draw.color(0xFF474747), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_8", fallback: "", rect: CGRect(x: 484, y: 979, width: 560, height: 56), size: 44, color: Draw.color(0xFF4C4C4C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_9", fallback: "", rect: CGRect(x: 946, y: 1031, width: 97, height: 52), size: 40, color: Draw.color(0xFF4B4B4B), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t3_10", fallback: "", rect: CGRect(x: 528, y: 1106, width: 517, height: 55), size: 43, color: Draw.color(0xFF555555), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t3_11", fallback: "", rect: CGRect(x: 502, y: 1157, width: 543, height: 52), size: 40, color: Draw.color(0xFF454545), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t3_12", fallback: "", rect: CGRect(x: 757, y: 1208, width: 287, height: 49), size: 36, color: Draw.color(0xFF3F3F3F), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t3_13", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE7443C), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 4 {
            return [
                MockPsbc.OverlayField(key: "t4_0", fallback: "", rect: CGRect(x: 437, y: 172, width: 205, height: 59), size: 47, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t4_1", fallback: "", rect: CGRect(x: 445, y: 266, width: 258, height: 77), size: 67, color: Draw.color(0xFFED707D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t4_2", fallback: "", rect: CGRect(x: 640, y: 449, width: 372, height: 59), size: 47, color: Draw.color(0xFF0C0C0C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t4_3", fallback: "", rect: CGRect(x: 421, y: 547, width: 594, height: 68), size: 57, color: Draw.color(0xFF131313), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t4_4", fallback: "", rect: CGRect(x: 985, y: 718, width: 43, height: 60), size: 48, color: Draw.color(0xFF414141), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t4_5", fallback: "", rect: CGRect(x: 36, y: 853, width: 514, height: 60), size: 48, color: Draw.color(0xFF090909), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t4_6", fallback: "", rect: CGRect(x: 345, y: 1123, width: 20, height: 70), size: 59, color: Draw.color(0xFFD1D1D1), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t4_7", fallback: "", rect: CGRect(x: 892, y: 1411, width: 152, height: 95), size: 87, color: Draw.color(0xFF046BE8), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 5 {
            return [
                MockPsbc.OverlayField(key: "t5_0", fallback: "", rect: CGRect(x: 448, y: 165, width: 183, height: 55), size: 43, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t5_1", fallback: "", rect: CGRect(x: 403, y: 260, width: 284, height: 68), size: 57, color: Draw.color(0xFFDA686A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t5_2", fallback: "", rect: CGRect(x: 420, y: 364, width: 238, height: 55), size: 43, color: Draw.color(0xFF9D9D9D), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t5_3", fallback: "", rect: CGRect(x: 703, y: 472, width: 341, height: 56), size: 44, color: Draw.color(0xFF161616), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_4", fallback: "", rect: CGRect(x: 634, y: 576, width: 410, height: 57), size: 45, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_5", fallback: "", rect: CGRect(x: 863, y: 678, width: 181, height: 59), size: 47, color: Draw.color(0xFF1C1C1C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_6", fallback: "", rect: CGRect(x: 903, y: 782, width: 140, height: 59), size: 47, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_7", fallback: "", rect: CGRect(x: 530, y: 889, width: 514, height: 56), size: 44, color: Draw.color(0xFF101010), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_8", fallback: "", rect: CGRect(x: 863, y: 990, width: 181, height: 60), size: 48, color: Draw.color(0xFF252525), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_9", fallback: "", rect: CGRect(x: 945, y: 1095, width: 98, height: 58), size: 46, color: Draw.color(0xFF272727), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t5_10", fallback: "", rect: CGRect(x: 35, y: 1427, width: 720, height: 45), size: 32, color: Draw.color(0xFFBDBDC1), bg: Draw.color(0xFFF8F8FB), align: 1),
                MockPsbc.OverlayField(key: "t5_11", fallback: "", rect: CGRect(x: 358, y: 1554, width: 342, height: 54), size: 42, color: Draw.color(0xFF85ABC5), bg: Draw.color(0xFFF6F8FA), align: 1),
                MockPsbc.OverlayField(key: "t5_12", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE7433B), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 6 {
            return [
                MockPsbc.OverlayField(key: "t6_0", fallback: "", rect: CGRect(x: 448, y: 222, width: 183, height: 55), size: 43, color: Draw.color(0xFF141414), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t6_1", fallback: "", rect: CGRect(x: 375, y: 325, width: 330, height: 81), size: 72, color: Draw.color(0xFFE87480), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t6_2", fallback: "", rect: CGRect(x: 430, y: 440, width: 219, height: 53), size: 41, color: Draw.color(0xFF979797), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t6_3", fallback: "", rect: CGRect(x: 603, y: 615, width: 442, height: 54), size: 42, color: Draw.color(0xFF212121), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_4", fallback: "", rect: CGRect(x: 621, y: 691, width: 424, height: 54), size: 42, color: Draw.color(0xFF090909), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_5", fallback: "", rect: CGRect(x: 835, y: 778, width: 210, height: 72), size: 62, color: Draw.color(0xFF2B77D8), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_6", fallback: "", rect: CGRect(x: 484, y: 864, width: 561, height: 105), size: 98, color: Draw.color(0xFF121212), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_7", fallback: "", rect: CGRect(x: 633, y: 991, width: 412, height: 54), size: 42, color: Draw.color(0xFF080808), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_8", fallback: "", rect: CGRect(x: 904, y: 1068, width: 141, height: 54), size: 42, color: Draw.color(0xFF121212), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_9", fallback: "", rect: CGRect(x: 948, y: 1143, width: 97, height: 59), size: 47, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_10", fallback: "", rect: CGRect(x: 490, y: 1193, width: 554, height: 59), size: 47, color: Draw.color(0xFF0F0F0F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t6_11", fallback: "", rect: CGRect(x: 861, y: 1243, width: 183, height: 55), size: 43, color: Draw.color(0xFF1A1A1A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t6_12", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE9433B), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 7 {
            return [
                MockPsbc.OverlayField(key: "t7_0", fallback: "", rect: CGRect(x: 387, y: 195, width: 304, height: 55), size: 43, color: Draw.color(0xFF0E0E0E), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t7_1", fallback: "", rect: CGRect(x: 390, y: 296, width: 298, height: 78), size: 68, color: Draw.color(0xFF343434), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t7_2", fallback: "", rect: CGRect(x: 431, y: 422, width: 218, height: 53), size: 41, color: Draw.color(0xFF969696), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t7_3", fallback: "", rect: CGRect(x: 701, y: 598, width: 344, height: 53), size: 41, color: Draw.color(0xFF464646), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t7_4", fallback: "", rect: CGRect(x: 627, y: 674, width: 414, height: 54), size: 42, color: Draw.color(0xFF444444), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t7_5", fallback: "", rect: CGRect(x: 862, y: 750, width: 183, height: 55), size: 43, color: Draw.color(0xFF4E4E4E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t7_6", fallback: "", rect: CGRect(x: 862, y: 827, width: 182, height: 54), size: 42, color: Draw.color(0xFF4D4D4D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t7_7", fallback: "", rect: CGRect(x: 634, y: 904, width: 411, height: 53), size: 41, color: Draw.color(0xFF434343), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t7_8", fallback: "", rect: CGRect(x: 780, y: 980, width: 265, height: 54), size: 42, color: Draw.color(0xFF4D4D4D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t7_9", fallback: "", rect: CGRect(x: 359, y: 1509, width: 340, height: 56), size: 44, color: Draw.color(0xFF5C8FC5), bg: Draw.color(0xFFF6F8FB), align: 1),
                MockPsbc.OverlayField(key: "t7_10", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE7443B), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 8 {
            return [
                MockPsbc.OverlayField(key: "t8_0", fallback: "", rect: CGRect(x: 112, y: 137, width: 304, height: 63), size: 52, color: Draw.color(0xFF606060), bg: Draw.color(0xFFF9F9F9), align: 2),
                MockPsbc.OverlayField(key: "t8_1", fallback: "", rect: CGRect(x: 305, y: 463, width: 623, height: 51), size: 38, color: Draw.color(0xFF7F7F7F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t8_2", fallback: "", rect: CGRect(x: 97, y: 593, width: 656, height: 44), size: 31, color: Draw.color(0xFFB5B5B7), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t8_3", fallback: "", rect: CGRect(x: 388, y: 676, width: 246, height: 102), size: 95, color: Draw.color(0xFFB8B5AE), bg: Draw.color(0xFFFEFEFC), align: 2),
                MockPsbc.OverlayField(key: "t8_4", fallback: "", rect: CGRect(x: 236, y: 807, width: 317, height: 47), size: 34, color: Draw.color(0xFFB6B3AC), bg: Draw.color(0xFFFFFDF6), align: 1),
                MockPsbc.OverlayField(key: "t8_5", fallback: "", rect: CGRect(x: 388, y: 916, width: 246, height: 102), size: 95, color: Draw.color(0xFFB0B3B9), bg: Draw.color(0xFFFEFEFF), align: 2),
                MockPsbc.OverlayField(key: "t8_6", fallback: "", rect: CGRect(x: 236, y: 1047, width: 311, height: 47), size: 34, color: Draw.color(0xFFAEB1B7), bg: Draw.color(0xFFF8FBFF), align: 1),
                MockPsbc.OverlayField(key: "t8_7", fallback: "", rect: CGRect(x: 308, y: 1173, width: 505, height: 76), size: 66, color: Draw.color(0xFF727178), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t8_8", fallback: "", rect: CGRect(x: 306, y: 1271, width: 230, height: 48), size: 35, color: Draw.color(0xFFDFB793), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t8_9", fallback: "", rect: CGRect(x: 306, y: 1347, width: 249, height: 51), size: 38, color: Draw.color(0xFF8C8C8E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t8_10", fallback: "", rect: CGRect(x: 208, y: 1425, width: 191, height: 51), size: 38, color: Draw.color(0xFF89898A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t8_11", fallback: "", rect: CGRect(x: 782, y: 2066, width: 42, height: 36), size: 22, color: Draw.color(0xFFE7433C), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 9 {
            return [
                MockPsbc.OverlayField(key: "t9_0", fallback: "", rect: CGRect(x: 406, y: 358, width: 263, height: 71), size: 61, color: Draw.color(0xFF67B69F), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t9_1", fallback: "", rect: CGRect(x: 427, y: 566, width: 223, height: 81), size: 72, color: Draw.color(0xFF020202), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t9_2", fallback: "", rect: CGRect(x: 497, y: 705, width: 86, height: 58), size: 46, color: Draw.color(0xFF888888), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "t9_3", fallback: "", rect: CGRect(x: 874, y: 856, width: 155, height: 61), size: 50, color: Draw.color(0xFF373737), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_4", fallback: "", rect: CGRect(x: 485, y: 967, width: 543, height: 66), size: 55, color: Draw.color(0xFF3B3B3B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_5", fallback: "", rect: CGRect(x: 684, y: 1078, width: 345, height: 61), size: 50, color: Draw.color(0xFF939393), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_6", fallback: "", rect: CGRect(x: 710, y: 1188, width: 195, height: 59), size: 47, color: Draw.color(0xFF578BC0), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_7", fallback: "", rect: CGRect(x: 35, y: 1450, width: 217, height: 51), size: 38, color: Draw.color(0xFF9F9F9F), bg: Draw.color(0xFFF9F9F9), align: 1),
                MockPsbc.OverlayField(key: "t9_8", fallback: "", rect: CGRect(x: 1004, y: 1562, width: 40, height: 58), size: 46, color: Draw.color(0xFF6C6C6C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_9", fallback: "", rect: CGRect(x: 1004, y: 1694, width: 39, height: 57), size: 45, color: Draw.color(0xFF777777), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_10", fallback: "", rect: CGRect(x: 445, y: 1827, width: 598, height: 58), size: 46, color: Draw.color(0xFFA6A6A7), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "t9_11", fallback: "", rect: CGRect(x: 263, y: 2009, width: 552, height: 52), size: 40, color: Draw.color(0xFFA1A1A1), bg: Draw.color(0xFFF9F9F9), align: 1),
            ]
        }
        return []
    }


    /// 交行绿（截图取样）
    static let green = UIColor(red: 0x2f / 255.0, green: 0xc0 / 255.0,
                               blue: 0x7a / 255.0, alpha: 1)
    /// 交行蓝（「完成」与三条链接）
    static let blue = UIColor(red: 0x1a / 255.0, green: 0x6a / 255.0,
                              blue: 0xd8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 8:
            // 回单：电子受理单结构
            if showsTopTitle { b.titleBar("回单") }
            b.coloredHeaderBar("回单", bg: .white, fg: MockInk.title)
            b.sectionLabel("电子受理单", size: dp(17), color: MockInk.title,
                           padH: dp(16), padTop: dp(20), padBottom: dp(6))
            b.row("付款方", "李哈哈")
            b.row("收款账户", "中国银行(**4919)")
            b.row("金额", "100.00")
            b.row("时间", state.time)
            b.gap(dp(20))
        case 9:
            // 付款结果：与转账成功同页，金额取负
            transferSuccess(b, state, showsTopTitle: showsTopTitle, incoming: false)
        default:
            // 其余子页：账号转账成功，转入/支付宝转入取正
            let incoming = state.sub == 1 || state.sub == 3 || state.sub == 4 || state.sub == 5 || state.sub == 6
            transferSuccess(b, state, showsTopTitle: showsTopTitle, incoming: incoming)
        }
        return b.result
    }

    // MARK: - 子页 0：账号转账成功

    private static func transferSuccess(_ b: MockBuilder, _ s: MockState,
                                        showsTopTitle: Bool, incoming: Bool = true) {
        if showsTopTitle { b.titleBar(incoming ? "账号转账" : "付款结果") }

        // 顶栏：居中标题 + 右上「完成」
        b.coloredHeaderBar("账号转账", bg: .white, fg: MockInk.title, trailing: "完成")

        // 绿色对勾
        b.gap(dp(18))
        b.checkBadge(diameter: dp(72), color: green, gapTop: dp(4), symbolSize: dp(38))

        // 转账成功 + 金额
        b.sectionLabel("转账成功", size: dp(24), color: green,
                       padH: 0, padTop: dp(14), padBottom: 0)
        b.sectionLabel(s.value("金额", "¥100.00"), size: dp(34), color: MockInk.title,
                       padH: 0, padTop: dp(10), padBottom: 0)

        // 到账说明
        b.paragraph("预计实时到账，实际到账时间取决于收款银行",
                    size: dp(15), color: MockInk.value,
                    padH: dp(16), padTop: dp(16), padBottom: dp(8))

        // 两行明细
        b.row("收款方", s.value("收款方", "李哈哈"))
        b.row("收款账户", s.value("收款账户", "中国银行(**4919)"))

        // 三条蓝色链接
        b.centeredCells(["转账记录", "继续转账", "通知收款人"],
                        height: dp(30), size: dp(16), padV: dp(14))

        // 您可能还需要
        b.fill(dp(10), MockInk.page)
        b.sectionLabel("您可能还需要", size: dp(14), color: MockInk.label,
                       padH: dp(16), padTop: dp(14), padBottom: dp(8))
        b.panel(gapTop: 0, margin: dp(16), pad: 0, bg: .white) { p in
            p.chevronRow("活期盈          随时取用，边攒边花", size: dp(15),
                         color: MockInk.title, padH: dp(16), padV: dp(16))
            p.hairlineFull()
            p.chevronRow("出行福利          30元打车红包", size: dp(15),
                         color: MockInk.title, padH: dp(16), padV: dp(16))
        }

        // 活动 banner（截图里的蓝色「打车红包」图；素材未单独同步，用通用底图占位）
        b.fullWidthImage("mock_bg", gapTop: dp(12))
    }
}
