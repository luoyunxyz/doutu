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
                MockPsbc.OverlayField(key: "j0_0",fallback: "", rect: CGRect(x: 396, y: 470, width: 284, height: 78), size: 68, color: Draw.color(0xFFBAA263), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j0_1",fallback: "", rect: CGRect(x: 766, y: 610, width: 224, height: 57), size: 45, color: Draw.color(0xFF787879), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_2",fallback: "", rect: CGRect(x: 850, y: 682, width: 139, height: 55), size: 43, color: Draw.color(0xFF78787A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_3",fallback: "", rect: CGRect(x: 53, y: 755, width: 159, height: 54), size: 42, color: Draw.color(0xFF767676), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j0_4",fallback: "", rect: CGRect(x: 813, y: 911, width: 177, height: 54), size: 42, color: Draw.color(0xFF959595), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_5",fallback: "", rect: CGRect(x: 594, y: 1035, width: 397, height: 55), size: 43, color: Draw.color(0xFF9B9B9B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_6",fallback: "", rect: CGRect(x: 744, y: 1159, width: 246, height: 55), size: 43, color: Draw.color(0xFF9C9C9C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_7",fallback: "", rect: CGRect(x: 737, y: 1288, width: 253, height: 54), size: 42, color: Draw.color(0xFF959595), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_8",fallback: "", rect: CGRect(x: 813, y: 1412, width: 177, height: 55), size: 43, color: Draw.color(0xFF979797), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j0_9",fallback: "", rect: CGRect(x: 419, y: 1512, width: 226, height: 51), size: 38, color: Draw.color(0xFFBDAE7E), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j0_10",fallback: "", rect: CGRect(x: 86, y: 1691, width: 871, height: 100), size: 92, color: Draw.color(0xFFBEAF80), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j0_11",fallback: "", rect: CGRect(x: 41, y: 1852, width: 169, height: 51), size: 38, color: Draw.color(0xFFAAAAAA), bg: Draw.color(0xFFFAFAFA), align: 1),
                MockPsbc.OverlayField(key: "j0_12",fallback: "", rect: CGRect(x: 38, y: 1953, width: 1003, height: 388), size: 409, color: Draw.color(0xFFEB594A), bg: Draw.color(0xFFFBF0DB), align: 1),
                MockPsbc.OverlayField(key: "j0_13",fallback: "", rect: CGRect(x: 37, y: 2394, width: 1004, height: 382), size: 403, color: Draw.color(0xFF2F3475), bg: Draw.color(0xFFF7F0DD), align: 1),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "j1_0",fallback: "", rect: CGRect(x: 331, y: 163, width: 418, height: 100), size: 92, color: Draw.color(0xFFF01C1B), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j1_1",fallback: "", rect: CGRect(x: 439, y: 293, width: 202, height: 58), size: 46, color: Draw.color(0xFFA1A1A1), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j1_2",fallback: "", rect: CGRect(x: 761, y: 434, width: 272, height: 51), size: 38, color: Draw.color(0xFF2F2F2F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j1_3",fallback: "", rect: CGRect(x: 885, y: 581, width: 148, height: 53), size: 41, color: Draw.color(0xFF111111), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j1_4",fallback: "", rect: CGRect(x: 852, y: 729, width: 180, height: 54), size: 42, color: Draw.color(0xFF1D1D1D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j1_5",fallback: "", rect: CGRect(x: 622, y: 877, width: 408, height: 56), size: 44, color: Draw.color(0xFF272727), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j1_6",fallback: "", rect: CGRect(x: 609, y: 1027, width: 424, height: 53), size: 41, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j1_7",fallback: "", rect: CGRect(x: 785, y: 1175, width: 248, height: 53), size: 41, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j1_8",fallback: "", rect: CGRect(x: 398, y: 1323, width: 632, height: 55), size: 43, color: Draw.color(0xFF1F1F1F), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "j2_0",fallback: "", rect: CGRect(x: 436, y: 229, width: 204, height: 59), size: 47, color: Draw.color(0xFF1D1D1D), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j2_1",fallback: "", rect: CGRect(x: 393, y: 346, width: 358, height: 87), size: 78, color: Draw.color(0xFF030303), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_2",fallback: "", rect: CGRect(x: 594, y: 557, width: 421, height: 52), size: 40, color: Draw.color(0xFF252525), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_3",fallback: "", rect: CGRect(x: 844, y: 660, width: 171, height: 52), size: 40, color: Draw.color(0xFF353535), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_4",fallback: "", rect: CGRect(x: 631, y: 764, width: 384, height: 50), size: 37, color: Draw.color(0xFF313131), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_5",fallback: "", rect: CGRect(x: 844, y: 866, width: 168, height: 51), size: 38, color: Draw.color(0xFF333333), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_6",fallback: "", rect: CGRect(x: 669, y: 969, width: 346, height: 51), size: 38, color: Draw.color(0xFF3D3D3D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_7",fallback: "", rect: CGRect(x: 787, y: 1073, width: 228, height: 49), size: 36, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_8",fallback: "", rect: CGRect(x: 845, y: 1175, width: 170, height: 50), size: 37, color: Draw.color(0xFF1C1C1C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j2_9",fallback: "", rect: CGRect(x: 35, y: 2068, width: 1008, height: 128), size: 123, color: Draw.color(0xFF6B99EB), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 3 {
            return [
                MockPsbc.OverlayField(key: "j3_0",fallback: "", rect: CGRect(x: 389, y: 231, width: 301, height: 60), size: 48, color: Draw.color(0xFF181818), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j3_1",fallback: "", rect: CGRect(x: 406, y: 360, width: 348, height: 85), size: 76, color: Draw.color(0xFF030303), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_2",fallback: "", rect: CGRect(x: 558, y: 566, width: 463, height: 55), size: 43, color: Draw.color(0xFF3B3B3B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_3",fallback: "", rect: CGRect(x: 838, y: 676, width: 183, height: 54), size: 42, color: Draw.color(0xFF2D2D2D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_4",fallback: "", rect: CGRect(x: 603, y: 787, width: 418, height: 52), size: 40, color: Draw.color(0xFF141414), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_5",fallback: "", rect: CGRect(x: 796, y: 895, width: 224, height: 55), size: 43, color: Draw.color(0xFF1F1F1F), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_6",fallback: "", rect: CGRect(x: 421, y: 1005, width: 598, height: 54), size: 42, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_7",fallback: "", rect: CGRect(x: 749, y: 1056, width: 272, height: 50), size: 37, color: Draw.color(0xFF2E2E2E), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j3_8",fallback: "", rect: CGRect(x: 777, y: 1165, width: 244, height: 52), size: 40, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j3_9",fallback: "", rect: CGRect(x: 839, y: 1274, width: 182, height: 52), size: 40, color: Draw.color(0xFF323232), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 4 {
            return [
                MockPsbc.OverlayField(key: "j4_0",fallback: "", rect: CGRect(x: 439, y: 232, width: 203, height: 59), size: 47, color: Draw.color(0xFF0A0A0A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j4_1",fallback: "", rect: CGRect(x: 406, y: 360, width: 348, height: 85), size: 76, color: Draw.color(0xFF030303), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_2",fallback: "", rect: CGRect(x: 558, y: 566, width: 463, height: 55), size: 43, color: Draw.color(0xFF3C3C3C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_3",fallback: "", rect: CGRect(x: 838, y: 676, width: 183, height: 54), size: 42, color: Draw.color(0xFF2D2D2D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_4",fallback: "", rect: CGRect(x: 604, y: 787, width: 417, height: 52), size: 40, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_5",fallback: "", rect: CGRect(x: 840, y: 895, width: 180, height: 54), size: 42, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_6",fallback: "", rect: CGRect(x: 617, y: 1005, width: 404, height: 53), size: 41, color: Draw.color(0xFF1E1E1E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_7",fallback: "", rect: CGRect(x: 777, y: 1116, width: 244, height: 51), size: 38, color: Draw.color(0xFF2E2E2E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j4_8",fallback: "", rect: CGRect(x: 839, y: 1224, width: 182, height: 53), size: 41, color: Draw.color(0xFF121212), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 5 {
            return [
                MockPsbc.OverlayField(key: "j5_0",fallback: "", rect: CGRect(x: 433, y: 209, width: 211, height: 209), size: 212, color: Draw.color(0xFF5687E9), bg: Draw.color(0xFFF0F3F9), align: 1),
                MockPsbc.OverlayField(key: "j5_1",fallback: "", rect: CGRect(x: 390, y: 528, width: 295, height: 82), size: 73, color: Draw.color(0xFF545FAE), bg: Draw.color(0xFFF9FAFE), align: 1),
                MockPsbc.OverlayField(key: "j5_2",fallback: "", rect: CGRect(x: 901, y: 659, width: 109, height: 63), size: 52, color: Draw.color(0xFF737373), bg: Draw.color(0xFFFAFAFA), align: 2),
                MockPsbc.OverlayField(key: "j5_3",fallback: "", rect: CGRect(x: 900, y: 731, width: 110, height: 60), size: 48, color: Draw.color(0xFF6F6F6F), bg: Draw.color(0xFFFAFAFA), align: 2),
                MockPsbc.OverlayField(key: "j5_4",fallback: "", rect: CGRect(x: 228, y: 803, width: 324, height: 62), size: 51, color: Draw.color(0xFF777777), bg: Draw.color(0xFFFAFAFA), align: 2),
                MockPsbc.OverlayField(key: "j5_5",fallback: "", rect: CGRect(x: 761, y: 996, width: 185, height: 55), size: 43, color: Draw.color(0xFF787878), bg: Draw.color(0xFFFAFAFA), align: 2),
                MockPsbc.OverlayField(key: "j5_6",fallback: "", rect: CGRect(x: 588, y: 1133, width: 359, height: 54), size: 42, color: Draw.color(0xFF777777), bg: Draw.color(0xFFFAFAFA), align: 2),
                MockPsbc.OverlayField(key: "j5_7",fallback: "", rect: CGRect(x: 433, y: 1253, width: 204, height: 49), size: 36, color: Draw.color(0xFF787FB0), bg: Draw.color(0xFFF9FAFE), align: 1),
                MockPsbc.OverlayField(key: "j5_8",fallback: "", rect: CGRect(x: 958, y: 1394, width: 32, height: 131), size: 127, color: Draw.color(0xFFAAB3BB), bg: Draw.color(0xFFE8F1FA), align: 2),
                MockPsbc.OverlayField(key: "j5_9",fallback: "", rect: CGRect(x: 723, y: 1638, width: 318, height: 121), size: 116, color: Draw.color(0xFF4B7CD5), bg: Draw.color(0xFFF8F8F8), align: 2),
                MockPsbc.OverlayField(key: "j5_10",fallback: "", rect: CGRect(x: 35, y: 1880, width: 162, height: 49), size: 36, color: Draw.color(0xFFB2B2B2), bg: Draw.color(0xFFFAFAFA), align: 1),
                MockPsbc.OverlayField(key: "j5_11",fallback: "", rect: CGRect(x: 33, y: 1940, width: 1013, height: 376), size: 396, color: Draw.color(0xFF70B7FD), bg: Draw.color(0xFFF8F8F8), align: 1),
            ]
        }
        if sub == 6 {
            return [
                MockPsbc.OverlayField(key: "j6_0",fallback: "", rect: CGRect(x: 406, y: 253, width: 268, height: 77), size: 67, color: Draw.color(0xFFBF443F), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j6_1",fallback: "", rect: CGRect(x: 490, y: 377, width: 98, height: 54), size: 42, color: Draw.color(0xFF191919), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j6_2",fallback: "", rect: CGRect(x: 904, y: 618, width: 139, height: 55), size: 43, color: Draw.color(0xFF0B0B0B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j6_3",fallback: "", rect: CGRect(x: 862, y: 716, width: 183, height: 53), size: 41, color: Draw.color(0xFF1E1E1E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j6_4",fallback: "", rect: CGRect(x: 915, y: 813, width: 130, height: 54), size: 42, color: Draw.color(0xFF080808), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j6_5",fallback: "", rect: CGRect(x: 467, y: 910, width: 552, height: 56), size: 44, color: Draw.color(0xFF0B0B0B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j6_6",fallback: "", rect: CGRect(x: 696, y: 960, width: 347, height: 55), size: 43, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j6_7",fallback: "", rect: CGRect(x: 820, y: 1057, width: 224, height: 55), size: 43, color: Draw.color(0xFF161616), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j6_8",fallback: "", rect: CGRect(x: 866, y: 1155, width: 179, height: 57), size: 45, color: Draw.color(0xFF161616), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j6_9",fallback: "", rect: CGRect(x: 201, y: 1302, width: 847, height: 219), size: 223, color: Draw.color(0xFF87A5CA), bg: Draw.color(0xFFFAFEFE), align: 2),
                MockPsbc.OverlayField(key: "j6_10",fallback: "", rect: CGRect(x: 34, y: 1549, width: 1014, height: 648), size: 695, color: Draw.color(0xFFD96C72), bg: Draw.color(0xFFFCFCFA), align: 1),
            ]
        }
        if sub == 7 {
            return [
                MockPsbc.OverlayField(key: "j7_0",fallback: "", rect: CGRect(x: 476, y: 233, width: 127, height: 69), size: 58, color: Draw.color(0xFF060606), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j7_1",fallback: "", rect: CGRect(x: 301, y: 361, width: 461, height: 60), size: 48, color: Draw.color(0xFF7A776F), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j7_2",fallback: "", rect: CGRect(x: 406, y: 478, width: 282, height: 77), size: 67, color: Draw.color(0xFF476FC1), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j7_3",fallback: "", rect: CGRect(x: 596, y: 685, width: 433, height: 54), size: 42, color: Draw.color(0xFF696969), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j7_4",fallback: "", rect: CGRect(x: 723, y: 782, width: 306, height: 55), size: 43, color: Draw.color(0xFF767676), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j7_5",fallback: "", rect: CGRect(x: 847, y: 880, width: 183, height: 54), size: 42, color: Draw.color(0xFF707070), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j7_6",fallback: "", rect: CGRect(x: 769, y: 977, width: 261, height: 55), size: 43, color: Draw.color(0xFF6E6E6E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j7_7",fallback: "", rect: CGRect(x: 931, y: 1075, width: 99, height: 54), size: 42, color: Draw.color(0xFF797979), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j7_8",fallback: "", rect: CGRect(x: 733, y: 1173, width: 293, height: 53), size: 41, color: Draw.color(0xFF6E6E6E), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j7_9",fallback: "", rect: CGRect(x: 56, y: 1332, width: 163, height: 50), size: 37, color: Draw.color(0xFF959595), bg: Draw.color(0xFFF0F0F0), align: 1),
                MockPsbc.OverlayField(key: "j7_10",fallback: "", rect: CGRect(x: 56, y: 1410, width: 933, height: 49), size: 36, color: Draw.color(0xFFABABAB), bg: Draw.color(0xFFF0F0F0), align: 1),
                MockPsbc.OverlayField(key: "j7_11",fallback: "", rect: CGRect(x: 56, y: 1484, width: 961, height: 50), size: 37, color: Draw.color(0xFFACACAC), bg: Draw.color(0xFFF0F0F0), align: 1),
                MockPsbc.OverlayField(key: "j7_12",fallback: "", rect: CGRect(x: 56, y: 1559, width: 327, height: 49), size: 36, color: Draw.color(0xFFABABAB), bg: Draw.color(0xFFF0F0F0), align: 1),
                MockPsbc.OverlayField(key: "j7_13",fallback: "", rect: CGRect(x: 550, y: 1925, width: 475, height: 132), size: 128, color: Draw.color(0xFF4F7FD8), bg: Draw.color(0xFFEEEEF0), align: 2),
            ]
        }
        if sub == 8 {
            return [
                MockPsbc.OverlayField(key: "j8_0",fallback: "", rect: CGRect(x: 370, y: 184, width: 446, height: 57), size: 45, color: Draw.color(0xFF555659), bg: Draw.color(0xFFF9FAFE), align: 2),
                MockPsbc.OverlayField(key: "j8_1",fallback: "", rect: CGRect(x: 370, y: 273, width: 104, height: 57), size: 45, color: Draw.color(0xFF636564), bg: Draw.color(0xFFFAFBFD), align: 2),
                MockPsbc.OverlayField(key: "j8_2",fallback: "", rect: CGRect(x: 370, y: 362, width: 104, height: 57), size: 45, color: Draw.color(0xFF616263), bg: Draw.color(0xFFFAFBFD), align: 2),
                MockPsbc.OverlayField(key: "j8_3",fallback: "", rect: CGRect(x: 369, y: 452, width: 193, height: 56), size: 44, color: Draw.color(0xFFB7A36C), bg: Draw.color(0xFFF7F8FC), align: 2),
                MockPsbc.OverlayField(key: "j8_4",fallback: "", rect: CGRect(x: 370, y: 541, width: 149, height: 57), size: 45, color: Draw.color(0xFF666769), bg: Draw.color(0xFFFAFBFE), align: 2),
                MockPsbc.OverlayField(key: "j8_5",fallback: "", rect: CGRect(x: 369, y: 631, width: 225, height: 55), size: 43, color: Draw.color(0xFF535356), bg: Draw.color(0xFFFAFAFE), align: 2),
                MockPsbc.OverlayField(key: "j8_6",fallback: "", rect: CGRect(x: 47, y: 726, width: 139, height: 112), size: 106, color: Draw.color(0xFFBAAC7B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j8_7",fallback: "", rect: CGRect(x: 370, y: 853, width: 150, height: 57), size: 45, color: Draw.color(0xFF626364), bg: Draw.color(0xFFFAFBFE), align: 2),
                MockPsbc.OverlayField(key: "j8_8",fallback: "", rect: CGRect(x: 370, y: 942, width: 599, height: 56), size: 44, color: Draw.color(0xFF515254), bg: Draw.color(0xFFF9FAFE), align: 2),
                MockPsbc.OverlayField(key: "j8_9",fallback: "", rect: CGRect(x: 373, y: 1031, width: 282, height: 57), size: 45, color: Draw.color(0xFF676869), bg: Draw.color(0xFFFAFBFE), align: 2),
                MockPsbc.OverlayField(key: "j8_10",fallback: "", rect: CGRect(x: 370, y: 1123, width: 638, height: 299), size: 311, color: Draw.color(0xFFA86462), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j8_11",fallback: "", rect: CGRect(x: 34, y: 1491, width: 159, height: 48), size: 35, color: Draw.color(0xFF898989), bg: Draw.color(0xFFFAFAFA), align: 1),
                MockPsbc.OverlayField(key: "j8_12",fallback: "", rect: CGRect(x: 34, y: 1581, width: 995, height: 50), size: 37, color: Draw.color(0xFFA6A6A6), bg: Draw.color(0xFFFAFAFA), align: 1),
                MockPsbc.OverlayField(key: "j8_13",fallback: "", rect: CGRect(x: 34, y: 1670, width: 950, height: 49), size: 36, color: Draw.color(0xFFA6A6A6), bg: Draw.color(0xFFFAFAFA), align: 1),
                MockPsbc.OverlayField(key: "j8_14",fallback: "", rect: CGRect(x: 877, y: 1768, width: 158, height: 54), size: 42, color: Draw.color(0xFFBBA874), bg: Draw.color(0xFFF8F8FA), align: 1),
                MockPsbc.OverlayField(key: "j8_15",fallback: "", rect: CGRect(x: 742, y: 1932, width: 262, height: 262), size: 271, color: Draw.color(0xFF616268), bg: Draw.color(0xFFFDFDFD), align: 2),
            ]
        }
        if sub == 9 {
            return [
                MockPsbc.OverlayField(key: "j9_0",fallback: "", rect: CGRect(x: 337, y: 155, width: 420, height: 56), size: 44, color: Draw.color(0xFF757575), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j9_1",fallback: "", rect: CGRect(x: 35, y: 285, width: 1008, height: 555), size: 593, color: Draw.color(0xFF336BB2), bg: Draw.color(0xFFDAE6F4), align: 1),
                MockPsbc.OverlayField(key: "j9_2",fallback: "", rect: CGRect(x: 36, y: 855, width: 1007, height: 555), size: 593, color: Draw.color(0xFF79A0E5), bg: Draw.color(0xFFEAFBFD), align: 1),
                MockPsbc.OverlayField(key: "j9_3",fallback: "", rect: CGRect(x: 38, y: 1479, width: 205, height: 60), size: 48, color: Draw.color(0xFF464646), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j9_4",fallback: "", rect: CGRect(x: 374, y: 1583, width: 577, height: 90), size: 81, color: Draw.color(0xFFC88988), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j9_5",fallback: "", rect: CGRect(x: 578, y: 1691, width: 450, height: 51), size: 38, color: Draw.color(0xFF696969), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j9_6",fallback: "", rect: CGRect(x: 826, y: 1736, width: 167, height: 51), size: 38, color: Draw.color(0xFF6B6B6B), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j9_7",fallback: "", rect: CGRect(x: 861, y: 1798, width: 99, height: 88), size: 79, color: Draw.color(0xFF5486CC), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j9_8",fallback: "", rect: CGRect(x: 331, y: 1908, width: 681, height: 52), size: 40, color: Draw.color(0xFF707070), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j9_9",fallback: "", rect: CGRect(x: 862, y: 1954, width: 92, height: 49), size: 36, color: Draw.color(0xFF616161), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "j9_10",fallback: "", rect: CGRect(x: 35, y: 2128, width: 1008, height: 68), size: 57, color: Draw.color(0xFF336AB2), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 10 {
            return [
                MockPsbc.OverlayField(key: "j10_0",fallback: "", rect: CGRect(x: 389, y: 203, width: 302, height: 73), size: 63, color: Draw.color(0xFFC91E1F), bg: Draw.color(0xFFFDFDFD), align: 1),
                MockPsbc.OverlayField(key: "j10_1",fallback: "", rect: CGRect(x: 491, y: 322, width: 97, height: 54), size: 42, color: Draw.color(0xFF262626), bg: Draw.color(0xFFFDFDFD), align: 1),
                MockPsbc.OverlayField(key: "j10_2",fallback: "", rect: CGRect(x: 904, y: 506, width: 139, height: 54), size: 42, color: Draw.color(0xFF1B1B1B), bg: Draw.color(0xFFFDFDFD), align: 2),
                MockPsbc.OverlayField(key: "j10_3",fallback: "", rect: CGRect(x: 880, y: 607, width: 161, height: 53), size: 41, color: Draw.color(0xFF171717), bg: Draw.color(0xFFFEFEFE), align: 2),
                MockPsbc.OverlayField(key: "j10_4",fallback: "", rect: CGRect(x: 894, y: 707, width: 151, height: 53), size: 41, color: Draw.color(0xFF0E0E0E), bg: Draw.color(0xFFFEFEFE), align: 2),
                MockPsbc.OverlayField(key: "j10_5",fallback: "", rect: CGRect(x: 780, y: 807, width: 264, height: 54), size: 42, color: Draw.color(0xFF151515), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "j10_6",fallback: "", rect: CGRect(x: 778, y: 907, width: 267, height: 55), size: 43, color: Draw.color(0xFF202020), bg: Draw.color(0xFFFDFDFD), align: 2),
                MockPsbc.OverlayField(key: "j10_7",fallback: "", rect: CGRect(x: 904, y: 1009, width: 140, height: 53), size: 41, color: Draw.color(0xFF171717), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 11 {
            return [
                MockPsbc.OverlayField(key: "j11_0",fallback: "", rect: CGRect(x: 456, y: 189, width: 164, height: 160), size: 158, color: Draw.color(0xFF3D6ED3), bg: Draw.color(0xFFF8F8F8), align: 1),
                MockPsbc.OverlayField(key: "j11_1",fallback: "", rect: CGRect(x: 324, y: 394, width: 431, height: 93), size: 85, color: Draw.color(0xFF030303), bg: Draw.color(0xFFF8F8F8), align: 1),
                MockPsbc.OverlayField(key: "j11_2",fallback: "", rect: CGRect(x: 235, y: 511, width: 605, height: 65), size: 54, color: Draw.color(0xFF0B0B0B), bg: Draw.color(0xFFF9F9F9), align: 1),
                MockPsbc.OverlayField(key: "j11_3",fallback: "", rect: CGRect(x: 470, y: 696, width: 573, height: 59), size: 47, color: Draw.color(0xFF787878), bg: Draw.color(0xFFF8F8F8), align: 2),
                MockPsbc.OverlayField(key: "j11_4",fallback: "", rect: CGRect(x: 819, y: 788, width: 225, height: 54), size: 42, color: Draw.color(0xFF6F6F6F), bg: Draw.color(0xFFF8F8F8), align: 2),
                MockPsbc.OverlayField(key: "j11_5",fallback: "", rect: CGRect(x: 434, y: 876, width: 212, height: 48), size: 35, color: Draw.color(0xFF86A1C9), bg: Draw.color(0xFFF9FAFC), align: 1),
                MockPsbc.OverlayField(key: "j11_6",fallback: "", rect: CGRect(x: 379, y: 1000, width: 664, height: 121), size: 116, color: Draw.color(0xFF5182D4), bg: Draw.color(0xFFF8F8F8), align: 2),
                MockPsbc.OverlayField(key: "j11_7",fallback: "", rect: CGRect(x: 34, y: 1233, width: 163, height: 50), size: 37, color: Draw.color(0xFFB2B2B2), bg: Draw.color(0xFFFAFAFA), align: 1),
                MockPsbc.OverlayField(key: "j11_8",fallback: "", rect: CGRect(x: 556, y: 1292, width: 425, height: 396), size: 418, color: Draw.color(0xFF7EBBF5), bg: Draw.color(0xFFF6FAFC), align: 2),
                MockPsbc.OverlayField(key: "j11_9",fallback: "", rect: CGRect(x: 970, y: 1677, width: 65, height: 48), size: 35, color: Draw.color(0xFFAEACB0), bg: Draw.color(0xFFDCE9FE), align: 2),
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
