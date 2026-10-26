import UIKit

/// 「模拟 → 建设模拟」的子页版式。
///
/// 建设的 12 个子页名与招商**完全一样**（明细转出(旧) … 财付通转出），但版式不同：
/// 0 号是金头的「转账成功」页（大号对勾徽标 + 说明 + 两行明细 + 三个描边胶囊），
/// 这里按安卓设备截图逐块复刻；其余子页先给占位。
enum MockJianshe {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "j0_0", fallback: "", rect: CGRect(x: 396, y: 470, width: 284, height: 78), size: 68, color: Draw.color(0xFFB79E59), align: 1),
                MockPsbc.OverlayField(key: "j0_1", fallback: "", rect: CGRect(x: 766, y: 610, width: 224, height: 57), size: 45, color: Draw.color(0xFF777778), align: 2),
                MockPsbc.OverlayField(key: "j0_2", fallback: "", rect: CGRect(x: 850, y: 682, width: 139, height: 55), size: 43, color: Draw.color(0xFF6C6C6E), align: 2),
                MockPsbc.OverlayField(key: "j0_3", fallback: "", rect: CGRect(x: 53, y: 755, width: 159, height: 54), size: 42, color: Draw.color(0xFF767676), align: 1),
                MockPsbc.OverlayField(key: "j0_4", fallback: "", rect: CGRect(x: 813, y: 911, width: 177, height: 54), size: 42, color: Draw.color(0xFFA9A9A9), align: 2),
                MockPsbc.OverlayField(key: "j0_5", fallback: "", rect: CGRect(x: 594, y: 1035, width: 397, height: 55), size: 43, color: Draw.color(0xFFAFAFAF), align: 2),
                MockPsbc.OverlayField(key: "j0_6", fallback: "", rect: CGRect(x: 744, y: 1159, width: 246, height: 55), size: 43, color: Draw.color(0xFF9F9F9F), align: 2),
                MockPsbc.OverlayField(key: "j0_7", fallback: "", rect: CGRect(x: 737, y: 1288, width: 253, height: 54), size: 42, color: Draw.color(0xFFB2B2B2), align: 2),
                MockPsbc.OverlayField(key: "j0_8", fallback: "", rect: CGRect(x: 813, y: 1412, width: 177, height: 55), size: 43, color: Draw.color(0xFFB8B8B8), align: 2),
                MockPsbc.OverlayField(key: "j0_9", fallback: "", rect: CGRect(x: 419, y: 1512, width: 226, height: 51), size: 38, color: Draw.color(0xFFC2B485), align: 1),
                MockPsbc.OverlayField(key: "j0_10", fallback: "", rect: CGRect(x: 86, y: 1691, width: 871, height: 100), size: 92, color: Draw.color(0xFFE0D5AF), align: 1),
                MockPsbc.OverlayField(key: "j0_11", fallback: "", rect: CGRect(x: 41, y: 1852, width: 169, height: 51), size: 38, color: Draw.color(0xFFAAAAAA), align: 1),
                MockPsbc.OverlayField(key: "j0_12", fallback: "", rect: CGRect(x: 38, y: 1953, width: 1003, height: 388), size: 409, color: Draw.color(0xFFE64534), align: 1),
                MockPsbc.OverlayField(key: "j0_13", fallback: "", rect: CGRect(x: 37, y: 2394, width: 1004, height: 382), size: 403, color: Draw.color(0xFF262962), align: 1),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "j1_0", fallback: "", rect: CGRect(x: 331, y: 163, width: 418, height: 100), size: 92, color: Draw.color(0xFFF41413), align: 1),
                MockPsbc.OverlayField(key: "j1_1", fallback: "", rect: CGRect(x: 439, y: 293, width: 202, height: 58), size: 46, color: Draw.color(0xFFB0B0B0), align: 1),
                MockPsbc.OverlayField(key: "j1_2", fallback: "", rect: CGRect(x: 761, y: 434, width: 272, height: 51), size: 38, color: Draw.color(0xFF424242), align: 2),
                MockPsbc.OverlayField(key: "j1_3", fallback: "", rect: CGRect(x: 885, y: 581, width: 148, height: 53), size: 41, color: Draw.color(0xFF3D3D3D), align: 2),
                MockPsbc.OverlayField(key: "j1_4", fallback: "", rect: CGRect(x: 852, y: 729, width: 180, height: 54), size: 42, color: Draw.color(0xFF4F4F4F), align: 2),
                MockPsbc.OverlayField(key: "j1_5", fallback: "", rect: CGRect(x: 622, y: 877, width: 408, height: 56), size: 44, color: Draw.color(0xFF2E2E2E), align: 2),
                MockPsbc.OverlayField(key: "j1_6", fallback: "", rect: CGRect(x: 609, y: 1027, width: 424, height: 53), size: 41, color: Draw.color(0xFF3C3C3C), align: 2),
                MockPsbc.OverlayField(key: "j1_7", fallback: "", rect: CGRect(x: 785, y: 1175, width: 248, height: 53), size: 41, color: Draw.color(0xFF414141), align: 2),
                MockPsbc.OverlayField(key: "j1_8", fallback: "", rect: CGRect(x: 398, y: 1323, width: 632, height: 55), size: 43, color: Draw.color(0xFF333333), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "j2_0", fallback: "", rect: CGRect(x: 436, y: 229, width: 204, height: 59), size: 47, color: Draw.color(0xFF1D1D1D), align: 1),
                MockPsbc.OverlayField(key: "j2_1", fallback: "", rect: CGRect(x: 393, y: 346, width: 358, height: 87), size: 78, color: Draw.color(0xFF020202), align: 2),
                MockPsbc.OverlayField(key: "j2_2", fallback: "", rect: CGRect(x: 594, y: 557, width: 421, height: 52), size: 40, color: Draw.color(0xFF3F3F3F), align: 2),
                MockPsbc.OverlayField(key: "j2_3", fallback: "", rect: CGRect(x: 844, y: 660, width: 171, height: 52), size: 40, color: Draw.color(0xFF1F1F1F), align: 2),
                MockPsbc.OverlayField(key: "j2_4", fallback: "", rect: CGRect(x: 631, y: 764, width: 384, height: 50), size: 37, color: Draw.color(0xFF747474), align: 2),
                MockPsbc.OverlayField(key: "j2_5", fallback: "", rect: CGRect(x: 844, y: 866, width: 168, height: 51), size: 38, color: Draw.color(0xFF373737), align: 2),
                MockPsbc.OverlayField(key: "j2_6", fallback: "", rect: CGRect(x: 669, y: 969, width: 346, height: 51), size: 38, color: Draw.color(0xFF636363), align: 2),
                MockPsbc.OverlayField(key: "j2_7", fallback: "", rect: CGRect(x: 787, y: 1073, width: 228, height: 49), size: 36, color: Draw.color(0xFF5F5F5F), align: 2),
                MockPsbc.OverlayField(key: "j2_8", fallback: "", rect: CGRect(x: 845, y: 1175, width: 170, height: 50), size: 37, color: Draw.color(0xFF525252), align: 2),
                MockPsbc.OverlayField(key: "j2_9", fallback: "", rect: CGRect(x: 35, y: 2068, width: 1008, height: 128), size: 123, color: Draw.color(0xFF4F7ADA), align: 1),
            ]
        }
        if sub == 3 {
            return [
                MockPsbc.OverlayField(key: "j3_0", fallback: "", rect: CGRect(x: 389, y: 231, width: 301, height: 60), size: 48, color: Draw.color(0xFF121212), align: 1),
                MockPsbc.OverlayField(key: "j3_1", fallback: "", rect: CGRect(x: 406, y: 360, width: 348, height: 85), size: 76, color: Draw.color(0xFF030303), align: 2),
                MockPsbc.OverlayField(key: "j3_2", fallback: "", rect: CGRect(x: 558, y: 566, width: 463, height: 55), size: 43, color: Draw.color(0xFF3A3A3A), align: 2),
                MockPsbc.OverlayField(key: "j3_3", fallback: "", rect: CGRect(x: 838, y: 676, width: 183, height: 54), size: 42, color: Draw.color(0xFF131313), align: 2),
                MockPsbc.OverlayField(key: "j3_4", fallback: "", rect: CGRect(x: 603, y: 787, width: 418, height: 52), size: 40, color: Draw.color(0xFF656565), align: 2),
                MockPsbc.OverlayField(key: "j3_5", fallback: "", rect: CGRect(x: 796, y: 895, width: 224, height: 55), size: 43, color: Draw.color(0xFF202020), align: 2),
                MockPsbc.OverlayField(key: "j3_6", fallback: "", rect: CGRect(x: 421, y: 1005, width: 598, height: 54), size: 42, color: Draw.color(0xFF3D3D3D), align: 2),
                MockPsbc.OverlayField(key: "j3_7", fallback: "", rect: CGRect(x: 749, y: 1056, width: 272, height: 50), size: 37, color: Draw.color(0xFF373737), align: 1),
                MockPsbc.OverlayField(key: "j3_8", fallback: "", rect: CGRect(x: 777, y: 1165, width: 244, height: 52), size: 40, color: Draw.color(0xFF4F4F4F), align: 2),
                MockPsbc.OverlayField(key: "j3_9", fallback: "", rect: CGRect(x: 839, y: 1274, width: 182, height: 52), size: 40, color: Draw.color(0xFF3C3C3C), align: 2),
            ]
        }
        if sub == 4 {
            return [
                MockPsbc.OverlayField(key: "j4_0", fallback: "", rect: CGRect(x: 439, y: 232, width: 203, height: 59), size: 47, color: Draw.color(0xFF3C3C3C), align: 1),
                MockPsbc.OverlayField(key: "j4_1", fallback: "", rect: CGRect(x: 406, y: 360, width: 348, height: 85), size: 76, color: Draw.color(0xFF020202), align: 2),
                MockPsbc.OverlayField(key: "j4_2", fallback: "", rect: CGRect(x: 558, y: 566, width: 463, height: 55), size: 43, color: Draw.color(0xFF3A3A3A), align: 2),
                MockPsbc.OverlayField(key: "j4_3", fallback: "", rect: CGRect(x: 838, y: 676, width: 183, height: 54), size: 42, color: Draw.color(0xFF131313), align: 2),
                MockPsbc.OverlayField(key: "j4_4", fallback: "", rect: CGRect(x: 604, y: 787, width: 417, height: 52), size: 40, color: Draw.color(0xFF5B5B5B), align: 2),
                MockPsbc.OverlayField(key: "j4_5", fallback: "", rect: CGRect(x: 840, y: 895, width: 180, height: 54), size: 42, color: Draw.color(0xFF464646), align: 2),
                MockPsbc.OverlayField(key: "j4_6", fallback: "", rect: CGRect(x: 617, y: 1005, width: 404, height: 53), size: 41, color: Draw.color(0xFF3E3E3E), align: 2),
                MockPsbc.OverlayField(key: "j4_7", fallback: "", rect: CGRect(x: 777, y: 1116, width: 244, height: 51), size: 38, color: Draw.color(0xFF4A4A4A), align: 2),
                MockPsbc.OverlayField(key: "j4_8", fallback: "", rect: CGRect(x: 839, y: 1224, width: 182, height: 53), size: 41, color: Draw.color(0xFF555555), align: 2),
            ]
        }
        if sub == 5 {
            return [
                MockPsbc.OverlayField(key: "j5_0", fallback: "", rect: CGRect(x: 433, y: 209, width: 211, height: 209), size: 212, color: Draw.color(0xFF4677DC), align: 1),
                MockPsbc.OverlayField(key: "j5_1", fallback: "", rect: CGRect(x: 390, y: 528, width: 295, height: 82), size: 73, color: Draw.color(0xFF4F5BA9), align: 1),
                MockPsbc.OverlayField(key: "j5_2", fallback: "", rect: CGRect(x: 901, y: 659, width: 109, height: 63), size: 52, color: Draw.color(0xFF727272), align: 2),
                MockPsbc.OverlayField(key: "j5_3", fallback: "", rect: CGRect(x: 900, y: 731, width: 110, height: 60), size: 48, color: Draw.color(0xFF686868), align: 2),
                MockPsbc.OverlayField(key: "j5_4", fallback: "", rect: CGRect(x: 228, y: 803, width: 324, height: 62), size: 51, color: Draw.color(0xFF6D6D6D), align: 2),
                MockPsbc.OverlayField(key: "j5_5", fallback: "", rect: CGRect(x: 761, y: 996, width: 185, height: 55), size: 43, color: Draw.color(0xFF909090), align: 2),
                MockPsbc.OverlayField(key: "j5_6", fallback: "", rect: CGRect(x: 588, y: 1133, width: 359, height: 54), size: 42, color: Draw.color(0xFF8C8C8C), align: 2),
                MockPsbc.OverlayField(key: "j5_7", fallback: "", rect: CGRect(x: 433, y: 1253, width: 204, height: 49), size: 36, color: Draw.color(0xFF858CBA), align: 1),
                MockPsbc.OverlayField(key: "j5_8", fallback: "", rect: CGRect(x: 958, y: 1394, width: 32, height: 131), size: 127, color: Draw.color(0xFFE8F1FA), align: 2),
                MockPsbc.OverlayField(key: "j5_9", fallback: "", rect: CGRect(x: 723, y: 1638, width: 318, height: 121), size: 116, color: Draw.color(0xFF3C6DD2), align: 2),
                MockPsbc.OverlayField(key: "j5_10", fallback: "", rect: CGRect(x: 35, y: 1880, width: 162, height: 49), size: 36, color: Draw.color(0xFFB0B0B0), align: 1),
                MockPsbc.OverlayField(key: "j5_11", fallback: "", rect: CGRect(x: 33, y: 1940, width: 1013, height: 376), size: 396, color: Draw.color(0xFF869E82), align: 1),
            ]
        }
        if sub == 6 {
            return [
                MockPsbc.OverlayField(key: "j6_0", fallback: "", rect: CGRect(x: 406, y: 253, width: 268, height: 77), size: 67, color: Draw.color(0xFFC0433F), align: 1),
                MockPsbc.OverlayField(key: "j6_1", fallback: "", rect: CGRect(x: 490, y: 377, width: 98, height: 54), size: 42, color: Draw.color(0xFF2C2C2C), align: 1),
                MockPsbc.OverlayField(key: "j6_2", fallback: "", rect: CGRect(x: 904, y: 618, width: 139, height: 55), size: 43, color: Draw.color(0xFF1B1B1B), align: 2),
                MockPsbc.OverlayField(key: "j6_3", fallback: "", rect: CGRect(x: 862, y: 716, width: 183, height: 53), size: 41, color: Draw.color(0xFF191919), align: 2),
                MockPsbc.OverlayField(key: "j6_4", fallback: "", rect: CGRect(x: 915, y: 813, width: 130, height: 54), size: 42, color: Draw.color(0xFF222222), align: 2),
                MockPsbc.OverlayField(key: "j6_5", fallback: "", rect: CGRect(x: 467, y: 910, width: 552, height: 56), size: 44, color: Draw.color(0xFF1B1B1B), align: 2),
                MockPsbc.OverlayField(key: "j6_6", fallback: "", rect: CGRect(x: 696, y: 960, width: 347, height: 55), size: 43, color: Draw.color(0xFF090909), align: 1),
                MockPsbc.OverlayField(key: "j6_7", fallback: "", rect: CGRect(x: 820, y: 1057, width: 224, height: 55), size: 43, color: Draw.color(0xFF0B0B0B), align: 2),
                MockPsbc.OverlayField(key: "j6_8", fallback: "", rect: CGRect(x: 866, y: 1155, width: 179, height: 57), size: 45, color: Draw.color(0xFF222222), align: 2),
                MockPsbc.OverlayField(key: "j6_9", fallback: "", rect: CGRect(x: 201, y: 1302, width: 847, height: 219), size: 223, color: Draw.color(0xFF6A7FBC), align: 2),
                MockPsbc.OverlayField(key: "j6_10", fallback: "", rect: CGRect(x: 34, y: 1549, width: 1014, height: 648), size: 695, color: Draw.color(0xFF1F3461), align: 1),
            ]
        }
        if sub == 7 {
            return [
                MockPsbc.OverlayField(key: "j7_0", fallback: "", rect: CGRect(x: 476, y: 233, width: 127, height: 69), size: 58, color: Draw.color(0xFF030303), align: 1),
                MockPsbc.OverlayField(key: "j7_1", fallback: "", rect: CGRect(x: 301, y: 361, width: 461, height: 60), size: 48, color: Draw.color(0xFF79776F), align: 1),
                MockPsbc.OverlayField(key: "j7_2", fallback: "", rect: CGRect(x: 406, y: 478, width: 282, height: 77), size: 67, color: Draw.color(0xFF476EC0), align: 1),
                MockPsbc.OverlayField(key: "j7_3", fallback: "", rect: CGRect(x: 596, y: 685, width: 433, height: 54), size: 42, color: Draw.color(0xFF6C6C6C), align: 2),
                MockPsbc.OverlayField(key: "j7_4", fallback: "", rect: CGRect(x: 723, y: 782, width: 306, height: 55), size: 43, color: Draw.color(0xFF676767), align: 2),
                MockPsbc.OverlayField(key: "j7_5", fallback: "", rect: CGRect(x: 847, y: 880, width: 183, height: 54), size: 42, color: Draw.color(0xFF696969), align: 2),
                MockPsbc.OverlayField(key: "j7_6", fallback: "", rect: CGRect(x: 769, y: 977, width: 261, height: 55), size: 43, color: Draw.color(0xFF7F7F7F), align: 2),
                MockPsbc.OverlayField(key: "j7_7", fallback: "", rect: CGRect(x: 931, y: 1075, width: 99, height: 54), size: 42, color: Draw.color(0xFF696969), align: 2),
                MockPsbc.OverlayField(key: "j7_8", fallback: "", rect: CGRect(x: 733, y: 1173, width: 293, height: 53), size: 41, color: Draw.color(0xFF6C6C6C), align: 2),
                MockPsbc.OverlayField(key: "j7_9", fallback: "", rect: CGRect(x: 56, y: 1332, width: 163, height: 50), size: 37, color: Draw.color(0xFF888888), align: 1),
                MockPsbc.OverlayField(key: "j7_10", fallback: "", rect: CGRect(x: 56, y: 1410, width: 933, height: 49), size: 36, color: Draw.color(0xFFACACAC), align: 1),
                MockPsbc.OverlayField(key: "j7_11", fallback: "", rect: CGRect(x: 56, y: 1484, width: 961, height: 50), size: 37, color: Draw.color(0xFFAFAFAF), align: 1),
                MockPsbc.OverlayField(key: "j7_12", fallback: "", rect: CGRect(x: 56, y: 1559, width: 327, height: 49), size: 36, color: Draw.color(0xFFB0B0B0), align: 1),
                MockPsbc.OverlayField(key: "j7_13", fallback: "", rect: CGRect(x: 550, y: 1925, width: 475, height: 132), size: 128, color: Draw.color(0xFF3C6DD3), align: 2),
            ]
        }
        if sub == 8 {
            return [
                MockPsbc.OverlayField(key: "j8_0", fallback: "", rect: CGRect(x: 370, y: 184, width: 446, height: 57), size: 45, color: Draw.color(0xFF737477), align: 2),
                MockPsbc.OverlayField(key: "j8_1", fallback: "", rect: CGRect(x: 370, y: 273, width: 104, height: 57), size: 45, color: Draw.color(0xFF515252), align: 2),
                MockPsbc.OverlayField(key: "j8_2", fallback: "", rect: CGRect(x: 370, y: 362, width: 104, height: 57), size: 45, color: Draw.color(0xFF4E5051), align: 2),
                MockPsbc.OverlayField(key: "j8_3", fallback: "", rect: CGRect(x: 369, y: 452, width: 193, height: 56), size: 44, color: Draw.color(0xFFBDAB7B), align: 2),
                MockPsbc.OverlayField(key: "j8_4", fallback: "", rect: CGRect(x: 370, y: 541, width: 149, height: 57), size: 45, color: Draw.color(0xFF5A5A5C), align: 2),
                MockPsbc.OverlayField(key: "j8_5", fallback: "", rect: CGRect(x: 369, y: 631, width: 225, height: 55), size: 43, color: Draw.color(0xFF545457), align: 2),
                MockPsbc.OverlayField(key: "j8_6", fallback: "", rect: CGRect(x: 47, y: 726, width: 139, height: 112), size: 106, color: Draw.color(0xFFF6F0D2), align: 2),
                MockPsbc.OverlayField(key: "j8_7", fallback: "", rect: CGRect(x: 370, y: 853, width: 150, height: 57), size: 45, color: Draw.color(0xFF4E504F), align: 2),
                MockPsbc.OverlayField(key: "j8_8", fallback: "", rect: CGRect(x: 370, y: 942, width: 599, height: 56), size: 44, color: Draw.color(0xFF58595B), align: 2),
                MockPsbc.OverlayField(key: "j8_9", fallback: "", rect: CGRect(x: 373, y: 1031, width: 282, height: 57), size: 45, color: Draw.color(0xFF505152), align: 2),
                MockPsbc.OverlayField(key: "j8_10", fallback: "", rect: CGRect(x: 370, y: 1123, width: 638, height: 299), size: 311, color: Draw.color(0xFFF7F8FC), align: 2),
                MockPsbc.OverlayField(key: "j8_11", fallback: "", rect: CGRect(x: 34, y: 1491, width: 159, height: 48), size: 35, color: Draw.color(0xFF7C7C7C), align: 1),
                MockPsbc.OverlayField(key: "j8_12", fallback: "", rect: CGRect(x: 34, y: 1581, width: 995, height: 50), size: 37, color: Draw.color(0xFFAAAAAA), align: 1),
                MockPsbc.OverlayField(key: "j8_13", fallback: "", rect: CGRect(x: 34, y: 1670, width: 950, height: 49), size: 36, color: Draw.color(0xFFA8A8A8), align: 1),
                MockPsbc.OverlayField(key: "j8_14", fallback: "", rect: CGRect(x: 877, y: 1768, width: 158, height: 54), size: 42, color: Draw.color(0xFFC6B587), align: 1),
                MockPsbc.OverlayField(key: "j8_15", fallback: "", rect: CGRect(x: 742, y: 1932, width: 262, height: 262), size: 271, color: Draw.color(0xFF181818), align: 2),
            ]
        }
        if sub == 9 {
            return [
                MockPsbc.OverlayField(key: "j9_0", fallback: "", rect: CGRect(x: 337, y: 155, width: 420, height: 56), size: 44, color: Draw.color(0xFF858585), align: 2),
                MockPsbc.OverlayField(key: "j9_1", fallback: "", rect: CGRect(x: 35, y: 285, width: 1008, height: 555), size: 593, color: Draw.color(0xFF316AB1), align: 1),
                MockPsbc.OverlayField(key: "j9_2", fallback: "", rect: CGRect(x: 36, y: 855, width: 1007, height: 555), size: 593, color: Draw.color(0xFF79A0E5), align: 1),
                MockPsbc.OverlayField(key: "j9_3", fallback: "", rect: CGRect(x: 38, y: 1479, width: 205, height: 60), size: 48, color: Draw.color(0xFF282828), align: 1),
                MockPsbc.OverlayField(key: "j9_4", fallback: "", rect: CGRect(x: 374, y: 1583, width: 577, height: 90), size: 81, color: Draw.color(0xFFF4F1EC), align: 2),
                MockPsbc.OverlayField(key: "j9_5", fallback: "", rect: CGRect(x: 578, y: 1691, width: 450, height: 51), size: 38, color: Draw.color(0xFF5E5E5E), align: 2),
                MockPsbc.OverlayField(key: "j9_6", fallback: "", rect: CGRect(x: 826, y: 1736, width: 167, height: 51), size: 38, color: Draw.color(0xFF454545), align: 1),
                MockPsbc.OverlayField(key: "j9_7", fallback: "", rect: CGRect(x: 861, y: 1798, width: 99, height: 88), size: 79, color: Draw.color(0xFF4A7EC8), align: 2),
                MockPsbc.OverlayField(key: "j9_8", fallback: "", rect: CGRect(x: 331, y: 1908, width: 681, height: 52), size: 40, color: Draw.color(0xFF656565), align: 2),
                MockPsbc.OverlayField(key: "j9_9", fallback: "", rect: CGRect(x: 862, y: 1954, width: 92, height: 49), size: 36, color: Draw.color(0xFF4D4D4D), align: 1),
                MockPsbc.OverlayField(key: "j9_10", fallback: "", rect: CGRect(x: 35, y: 2128, width: 1008, height: 68), size: 57, color: Draw.color(0xFF2E69AE), align: 1),
            ]
        }
        if sub == 10 {
            return [
                MockPsbc.OverlayField(key: "j10_0", fallback: "", rect: CGRect(x: 389, y: 203, width: 302, height: 73), size: 63, color: Draw.color(0xFFC71615), align: 1),
                MockPsbc.OverlayField(key: "j10_1", fallback: "", rect: CGRect(x: 491, y: 322, width: 97, height: 54), size: 42, color: Draw.color(0xFF565656), align: 1),
                MockPsbc.OverlayField(key: "j10_2", fallback: "", rect: CGRect(x: 904, y: 506, width: 139, height: 54), size: 42, color: Draw.color(0xFF363636), align: 2),
                MockPsbc.OverlayField(key: "j10_3", fallback: "", rect: CGRect(x: 880, y: 607, width: 161, height: 53), size: 41, color: Draw.color(0xFF5E5E5E), align: 2),
                MockPsbc.OverlayField(key: "j10_4", fallback: "", rect: CGRect(x: 894, y: 707, width: 151, height: 53), size: 41, color: Draw.color(0xFF393939), align: 2),
                MockPsbc.OverlayField(key: "j10_5", fallback: "", rect: CGRect(x: 780, y: 807, width: 264, height: 54), size: 42, color: Draw.color(0xFF444444), align: 2),
                MockPsbc.OverlayField(key: "j10_6", fallback: "", rect: CGRect(x: 778, y: 907, width: 267, height: 55), size: 43, color: Draw.color(0xFF1E1E1E), align: 2),
                MockPsbc.OverlayField(key: "j10_7", fallback: "", rect: CGRect(x: 904, y: 1009, width: 140, height: 53), size: 41, color: Draw.color(0xFF616161), align: 2),
            ]
        }
        if sub == 11 {
            return [
                MockPsbc.OverlayField(key: "j11_0", fallback: "", rect: CGRect(x: 456, y: 189, width: 164, height: 160), size: 158, color: Draw.color(0xFF3D6FD2), align: 1),
                MockPsbc.OverlayField(key: "j11_1", fallback: "", rect: CGRect(x: 324, y: 394, width: 431, height: 93), size: 85, color: Draw.color(0xFF040404), align: 1),
                MockPsbc.OverlayField(key: "j11_2", fallback: "", rect: CGRect(x: 235, y: 511, width: 605, height: 65), size: 54, color: Draw.color(0xFF080808), align: 1),
                MockPsbc.OverlayField(key: "j11_3", fallback: "", rect: CGRect(x: 470, y: 696, width: 573, height: 59), size: 47, color: Draw.color(0xFF727272), align: 2),
                MockPsbc.OverlayField(key: "j11_4", fallback: "", rect: CGRect(x: 819, y: 788, width: 225, height: 54), size: 42, color: Draw.color(0xFF787878), align: 2),
                MockPsbc.OverlayField(key: "j11_5", fallback: "", rect: CGRect(x: 434, y: 876, width: 212, height: 48), size: 35, color: Draw.color(0xFF8FAACE), align: 1),
                MockPsbc.OverlayField(key: "j11_6", fallback: "", rect: CGRect(x: 379, y: 1000, width: 664, height: 121), size: 116, color: Draw.color(0xFF3D6ED3), align: 2),
                MockPsbc.OverlayField(key: "j11_7", fallback: "", rect: CGRect(x: 34, y: 1233, width: 163, height: 50), size: 37, color: Draw.color(0xFFB0B0B0), align: 1),
                MockPsbc.OverlayField(key: "j11_8", fallback: "", rect: CGRect(x: 556, y: 1292, width: 425, height: 396), size: 418, color: Draw.color(0xFF6D9EDA), align: 2),
                MockPsbc.OverlayField(key: "j11_9", fallback: "", rect: CGRect(x: 970, y: 1677, width: 65, height: 48), size: 35, color: Draw.color(0xFFADABAF), align: 2),
            ]
        }
        return []
    }


    /// 建设的主题金（截图取自页面大标题与描边胶囊）
    static let gold = UIColor(red: 0xb8 / 255.0, green: 0x86 / 255.0,
                              blue: 0x0b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        let names = MockModule.jianshe.subs
        let title = names.indices.contains(state.sub) ? names[state.sub] : "明细"
        switch state.sub {
        case 0:
            transferSuccess(b, state, showsTopTitle: showsTopTitle)
        case 8:
            // 回单
            if showsTopTitle { b.titleBar("电子回单") }
            b.coloredHeaderBar("电子回单", bg: .white, fg: MockInk.title)
            b.sectionLabel("中国建设银行  电子回单", size: dp(17), color: MockInk.title,
                           padH: dp(16), padTop: dp(16), padBottom: dp(6))
            b.row("付款户名", "李哈哈")
            b.row("收款户名", "张大国")
            b.row("金额", "100.00")
            b.row("时间", state.time)
            b.gap(dp(20))
        case 9:
            // 余额
            if showsTopTitle { b.titleBar("余额") }
            b.coloredHeaderBar("余额", bg: .white, fg: MockInk.title)
            b.sectionLabel("¥ 1,860.00", size: dp(40), color: MockInk.black,
                           padH: 0, padTop: dp(30), padBottom: dp(10))
            b.sectionLabel("中国建设银行 · 储蓄卡 6227****8888", size: dp(14),
                           color: MockInk.label, padH: 0, padTop: 0, padBottom: dp(20))
            b.gap(dp(160))
        default:
            // 其余 9 个子页：建设版的明细页（标题取子页名）
            if showsTopTitle { b.titleBar(title) }
            b.coloredHeaderBar(title, bg: .white, fg: MockInk.title)
            b.centerBlock("对方", state.value("对方", "张大国"),
                          size: dp(15), color: MockInk.grey,
                          padTop: dp(24), padBottom: 0)
            b.centerBlock("金额", state.value("金额", "-100.00"),
                          size: dp(34), color: MockInk.amount, bold: true,
                          padTop: dp(10), padBottom: dp(30))
            b.statusBar("交易成功", trailing: "进度查询  ›")
            b.row("收款账号", "6227 **** **** 8888")
            b.row("收款银行", "中国建设银行")
            b.row("交易类型", title)
            b.row("交易时间", state.time)
            b.gap(dp(20))
        }
        return b.result
    }

    // MARK: - 子页 0：转账成功（`明细转出(旧)`）

    private static func transferSuccess(_ b: MockBuilder, _ s: MockState,
                                        showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细转出(旧)") }

        // 金色顶条 + 右上「完成」
        b.coloredHeaderBar("", bg: gold, fg: .white, trailing: "完成")

        // 大号对勾徽标：白底描边圆 + 金色勾
        b.gap(dp(10))
        b.checkBadge(diameter: dp(96), color: gold, gapTop: dp(6), symbolSize: dp(52))

        // 标题
        b.sectionLabel("转账成功", size: dp(24), color: gold,
                       padH: 0, padTop: dp(14), padBottom: 0)

        // 说明
        b.paragraph("一般情况下，资金实时转入收款账户，实际转入收款账户时间视收款行处理而定。"
                    + "如有疑问，请咨询收款行。",
                    size: dp(15), color: MockInk.value,
                    padH: dp(20), padTop: dp(18), padBottom: dp(8))

        // 两行明细
        b.row("转账金额", s.value("转账金额", "100.00元"))
        b.hairlineFull()
        b.row("收款账户", s.value("收款账户", "李哈哈  6226***9990"))

        // 查看更多
        b.centeredLink("查看更多 ⌄", size: dp(15), color: gold, padV: dp(10))

        // 三个描边胶囊
        b.centeredCells(["继续转账", "我的账户", "通知收款人"],
                        height: dp(36), size: dp(15), padV: dp(18))

        // 更多精彩
        b.fill(dp(10), MockInk.page)
        b.sectionLabel("更多精彩", size: dp(16), color: MockInk.label,
                       padH: dp(16), padTop: dp(14), padBottom: dp(10))
        b.fullWidthImage("mock_bg", gapTop: 0)
    }
}
