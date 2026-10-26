import UIKit

/// 「模拟 → 数字钱包」的子页版式（入口 16）。
///
/// 子页很多（设备抽屉里按银行分）：建设转出1/建设转出2/建设转入/建设存入/建设转出/
/// 招商结果/招商转出/工商转出/工商转出2/农业转出/兴业转出/邮政转出 …
/// 0 号是「转钱成功」：✕ 关闭、右上「继续转钱」、绿色描边对勾、
/// 转钱成功、金额、收款方与卡号、完成按钮、通知收款人。
enum MockDigitalWallet {

    static func overlayFields(_ sub: Int) -> [MockPsbc.OverlayField] {
        if sub == 0 {
            return [
                MockPsbc.OverlayField(key: "s0_0", fallback: "", rect: CGRect(x: 413, y: 496, width: 251, height: 71), size: 61, color: Draw.color(0xFF040404), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "s0_1", fallback: "", rect: CGRect(x: 420, y: 753, width: 294, height: 91), size: 82, color: Draw.color(0xFF010101), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s0_2", fallback: "", rect: CGRect(x: 330, y: 940, width: 403, height: 66), size: 55, color: Draw.color(0xFF0A0A0A), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "s0_3", fallback: "", rect: CGRect(x: 267, y: 1026, width: 546, height: 54), size: 42, color: Draw.color(0xFF797979), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "s0_4", fallback: "", rect: CGRect(x: 490, y: 1982, width: 101, height: 57), size: 45, color: Draw.color(0xFF8E8E8E), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "s0_5", fallback: "", rect: CGRect(x: 427, y: 2136, width: 225, height: 56), size: 44, color: Draw.color(0xFF8A8A8A), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 1 {
            return [
                MockPsbc.OverlayField(key: "s1_0", fallback: "", rect: CGRect(x: 502, y: 266, width: 79, height: 48), size: 35, color: Draw.color(0xFF2E2F31), bg: Draw.color(0xFFFEFFFF), align: 1),
                MockPsbc.OverlayField(key: "s1_1", fallback: "", rect: CGRect(x: 502, y: 300, width: 79, height: 48), size: 35, color: Draw.color(0xFF323335), bg: Draw.color(0xFFFEFFFF), align: 1),
                MockPsbc.OverlayField(key: "s1_2", fallback: "", rect: CGRect(x: 413, y: 403, width: 253, height: 53), size: 41, color: Draw.color(0xFF1A1A1C), bg: Draw.color(0xFFFAFBFC), align: 1),
                MockPsbc.OverlayField(key: "s1_3", fallback: "", rect: CGRect(x: 418, y: 517, width: 243, height: 73), size: 63, color: Draw.color(0xFF020204), bg: Draw.color(0xFFF7F8FA), align: 1),
                MockPsbc.OverlayField(key: "s1_4", fallback: "", rect: CGRect(x: 865, y: 742, width: 157, height: 48), size: 35, color: Draw.color(0xFF73A987), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_5", fallback: "", rect: CGRect(x: 831, y: 868, width: 191, height: 54), size: 42, color: Draw.color(0xFF4C4C4C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_6", fallback: "", rect: CGRect(x: 721, y: 995, width: 303, height: 49), size: 36, color: Draw.color(0xFF333333), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_7", fallback: "", rect: CGRect(x: 650, y: 1125, width: 373, height: 49), size: 36, color: Draw.color(0xFF636363), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_8", fallback: "", rect: CGRect(x: 664, y: 1254, width: 360, height: 48), size: 35, color: Draw.color(0xFF272727), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_9", fallback: "", rect: CGRect(x: 510, y: 1508, width: 514, height: 48), size: 35, color: Draw.color(0xFF3A3A3A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_10", fallback: "", rect: CGRect(x: 977, y: 1691, width: 36, height: 54), size: 42, color: Draw.color(0xFFC4C4C4), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_11", fallback: "", rect: CGRect(x: 977, y: 1845, width: 36, height: 52), size: 40, color: Draw.color(0xFFC2C2C2), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s1_12", fallback: "", rect: CGRect(x: 449, y: 2009, width: 179, height: 54), size: 42, color: Draw.color(0xFF878787), bg: Draw.color(0xFFFFFFFF), align: 1),
            ]
        }
        if sub == 2 {
            return [
                MockPsbc.OverlayField(key: "s2_0", fallback: "", rect: CGRect(x: 492, y: 255, width: 91, height: 88), size: 79, color: Draw.color(0xFF464749), bg: Draw.color(0xFFFEFFFF), align: 1),
                MockPsbc.OverlayField(key: "s2_1", fallback: "", rect: CGRect(x: 320, y: 403, width: 437, height: 57), size: 45, color: Draw.color(0xFF131315), bg: Draw.color(0xFFF9FAFC), align: 1),
                MockPsbc.OverlayField(key: "s2_2", fallback: "", rect: CGRect(x: 464, y: 496, width: 243, height: 77), size: 67, color: Draw.color(0xFF010204), bg: Draw.color(0xFFF7F8FA), align: 2),
                MockPsbc.OverlayField(key: "s2_3", fallback: "", rect: CGRect(x: 865, y: 706, width: 157, height: 49), size: 36, color: Draw.color(0xFF749F8D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s2_4", fallback: "", rect: CGRect(x: 900, y: 833, width: 124, height: 49), size: 36, color: Draw.color(0xFF595959), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s2_5", fallback: "", rect: CGRect(x: 692, y: 965, width: 330, height: 60), size: 48, color: Draw.color(0xFF3A458A), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s2_6", fallback: "", rect: CGRect(x: 828, y: 1098, width: 196, height: 49), size: 36, color: Draw.color(0xFF4C4C4C), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s2_7", fallback: "", rect: CGRect(x: 666, y: 1225, width: 358, height: 48), size: 35, color: Draw.color(0xFF272727), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s2_8", fallback: "", rect: CGRect(x: 969, y: 1558, width: 40, height: 51), size: 38, color: Draw.color(0xFFA7A7A7), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s2_9", fallback: "", rect: CGRect(x: 969, y: 1731, width: 40, height: 51), size: 38, color: Draw.color(0xFFA7A7A7), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 3 {
            return [
                MockPsbc.OverlayField(key: "s3_0", fallback: "", rect: CGRect(x: 393, y: 660, width: 289, height: 83), size: 74, color: Draw.color(0xFF616161), bg: Draw.color(0xFFFFFFFF), align: 1),
                MockPsbc.OverlayField(key: "s3_1", fallback: "", rect: CGRect(x: 756, y: 892, width: 289, height: 56), size: 44, color: Draw.color(0xFF2B2B2B), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s3_2", fallback: "", rect: CGRect(x: 808, y: 1008, width: 237, height: 59), size: 47, color: Draw.color(0xFF0D0D0D), bg: Draw.color(0xFFFFFFFF), align: 2),
                MockPsbc.OverlayField(key: "s3_3", fallback: "", rect: CGRect(x: 484, y: 1124, width: 558, height: 62), size: 51, color: Draw.color(0xFF1B1B1B), bg: Draw.color(0xFFFFFFFF), align: 2),
            ]
        }
        if sub == 4 {
            return [
                MockPsbc.OverlayField(key: "s4_0", fallback: "", rect: CGRect(x: 319, y: 450, width: 440, height: 94), size: 86, color: Draw.color(0xFF030303), bg: Draw.color(0xFFF6F6F6), align: 1),
                MockPsbc.OverlayField(key: "s4_1", fallback: "", rect: CGRect(x: 460, y: 581, width: 158, height: 60), size: 48, color: Draw.color(0xFF141414), bg: Draw.color(0xFFF9F9F9), align: 1),
                MockPsbc.OverlayField(key: "s4_2", fallback: "", rect: CGRect(x: 478, y: 823, width: 564, height: 59), size: 47, color: Draw.color(0xFF787878), bg: Draw.color(0xFFF8F8F8), align: 2),
                MockPsbc.OverlayField(key: "s4_3", fallback: "", rect: CGRect(x: 814, y: 930, width: 230, height: 54), size: 42, color: Draw.color(0xFF6C6C6C), bg: Draw.color(0xFFF8F8F8), align: 2),
                MockPsbc.OverlayField(key: "s4_4", fallback: "", rect: CGRect(x: 436, y: 1170, width: 210, height: 49), size: 36, color: Draw.color(0xFF7790B7), bg: Draw.color(0xFFF8F9FC), align: 1),
                MockPsbc.OverlayField(key: "s4_5", fallback: "", rect: CGRect(x: 35, y: 1524, width: 162, height: 50), size: 37, color: Draw.color(0xFFA3A3A3), bg: Draw.color(0xFFFAFAFA), align: 1),
            ]
        }
        return []
    }


    /// 主题绿（截图取样）
    static let green = UIColor(red: 0x4c / 255.0, green: 0xaf / 255.0,
                               blue: 0x50 / 255.0, alpha: 1)

    // MARK: - 数字钱包整页还原（1280×2816 参考图）

    /// 用户给的参考图就是 1280×2816 的整屏截图（含 Android 状态栏、底部手势条），
    /// 所以这些页面不裁状态栏、也不借用系统安全区：整页按原图铺，热区坐标就是原图坐标。
    ///
    /// `clear` 是改字时要先擦掉的矩形（文字外扩 8px），`baseline` / `size` 是按原图
    /// 墨迹高度反推出来的；没改过的字段不绘制，保留原图像素。
    struct FullField {
        enum Kind {
            /// 普通文字（居中 / 右对齐 / 左对齐）
            case text
            /// 「¥70.00」：保留原图 ¥ 字形，数字用 Archivo 数字字体
            case amount
            /// 「-70.00」：没有货币符号，整串用数字字体
            case signedAmount
            /// 「转钱给 xxx ✏」：文字居中，铅笔图标跟着文字走
            case recipient
        }

        enum Align {
            case left(CGFloat)
            case center(CGFloat)
            case right(CGFloat)
        }

        let key: String
        /// 编辑框预填值 + 「改回默认就恢复原图」的判定值
        let fallback: String
        let clear: CGRect
        let size: CGFloat
        let color: UIColor
        let bold: Bool
        let align: Align
        let baseline: CGFloat
        let kind: Kind
        /// 弹出编辑框时的标题
        let label: String
        /// 擦除区域的底色（整页里的灰色汇总区要填灰，不能填白）
        var bg: UIColor = MockInk.white
        /// 最长宽度；超过时按比例缩小字号，避免改成长文案后压到旁边的元素
        var maxWidth: CGFloat = 0
    }

    static let fullPageSize = CGSize(width: 1280, height: 2816)
    /// 建设转出2 的灰色汇总区底色
    static let fullGrey = Draw.color(0xFFF7F8FA)
    /// 建设存入的蓝色页头
    static let fullBlue = Draw.color(0xFF426DD6)
    /// 建设转出的浅灰页面底色
    static let fullPage4Bg = Draw.color(0xFFF8F8F8)
    /// 招商结果页头浅灰
    static let fullPage5HeaderBg = Draw.color(0xFFF5F5F5)
    /// 招商结果页底部浅灰
    static let fullPage5Bg = Draw.color(0xFFF7F7F7)
    /// 招商结果的红色按钮
    static let fullRed5 = Draw.color(0xFFF4514E)
    /// 工商转出的红色按钮
    static let fullRed7 = Draw.color(0xFFF45154)
    /// 工商转出2 的红色按钮
    static let fullRed8 = Draw.color(0xFFF9565B)
    /// 农业转出的橙色按钮
    static let fullOrange9 = Draw.color(0xFFEAAE40)
    /// 兴业转出的蓝色按钮
    static let fullBlue10 = Draw.color(0xFF4B7AC0)
    /// 兴业转出的提示文字
    static let fullWarn10 = Draw.color(0xFF8A93A8)

    static func hasFullPage(_ sub: Int) -> Bool { (0...11).contains(sub) }

    /// 数字钱包整页版式：0=建设转出1（转钱成功），1=建设转出2（交易详情），
    /// 2=建设转入（交易详情），3=建设存入（存入成功），4=建设转出（支付成功），
    /// 5=招商结果，6=招商转出，7=工商转出，8=工商转出2，9=农业转出，10=兴业转出，
    /// 11=邮政转出（交易详情）。
    /// 所有坐标/字号都从 1280×2816 的原图上量得。
    static func fullFields(_ sub: Int) -> [FullField] {
        if sub == 0 {
            return [
                FullField(key: "full_title", fallback: "转钱成功",
                          clear: CGRect(x: 489, y: 687, width: 302, height: 86),
                          size: 75, color: Draw.color(0xFF000000), bold: true,
                          align: .center(640), baseline: 764, kind: .text,
                          label: "修改状态标题"),
                FullField(key: "full_amount", fallback: "70.00",
                          clear: CGRect(x: 441, y: 991, width: 402, height: 108),
                          size: 123, color: Draw.color(0xFF000000), bold: true,
                          align: .center(642), baseline: 1090, kind: .amount,
                          label: "修改金额"),
                FullField(key: "full_recipient", fallback: "转钱给 零花钱",
                          clear: CGRect(x: 394, y: 1214, width: 474, height: 77),
                          size: 67, color: Draw.color(0xFF000000), bold: true,
                          align: .center(631), baseline: 1282, kind: .recipient,
                          label: "修改收款方"),
                FullField(key: "full_bank", fallback: "中国建设银行 0051****1679",
                          clear: CGRect(x: 320, y: 1316, width: 641, height: 64),
                          size: 53, color: Draw.color(0xFF6E6E6E), bold: false,
                          align: .center(640), baseline: 1371, kind: .text,
                          label: "修改收款卡号"),
                FullField(key: "full_continue", fallback: "继续转钱",
                          clear: CGRect(x: 999, y: 171, width: 212, height: 64),
                          size: 54, color: Draw.color(0xFF6E6E6E), bold: false,
                          align: .right(1203), baseline: 227, kind: .text,
                          label: "修改右上角文字"),
                FullField(key: "full_done", fallback: "完成",
                          clear: CGRect(x: 584, y: 2450, width: 113, height: 65),
                          size: 56, color: Draw.color(0xFF333333), bold: false,
                          align: .center(640), baseline: 2507, kind: .text,
                          label: "修改「完成」按钮文字"),
                FullField(key: "full_notify", fallback: "通知收款人",
                          clear: CGRect(x: 510, y: 2632, width: 260, height: 64),
                          size: 55, color: Draw.color(0xFF4A4A4A), bold: false,
                          align: .center(640), baseline: 2688, kind: .text,
                          label: "修改「通知收款人」"),
            ]
        }

        if sub == 1 {
            let labelColor = Draw.color(0xFF7C7C7C)
            let valueColor = Draw.color(0xFF111111)
            let greenText = Draw.color(0xFF2BA471)
            let rowSize: CGFloat = 46

            return [
                FullField(key: "full1_title", fallback: "交易详情",
                          clear: CGRect(x: 511, y: 169, width: 257, height: 76),
                          size: 68, color: Draw.color(0xFF222222), bold: true,
                          align: .center(640), baseline: 238, kind: .text,
                          label: "修改页面标题", maxWidth: 900),
                FullField(key: "full1_transfer", fallback: "转钱-转给建6",
                          clear: CGRect(x: 493, y: 579, width: 294, height: 60),
                          size: 50, color: Draw.color(0xFF000000), bold: true,
                          align: .center(640), baseline: 632, kind: .text,
                          label: "修改转账说明", bg: fullGrey, maxWidth: 900),
                FullField(key: "full1_amount", fallback: "-70.00",
                          clear: CGRect(x: 499, y: 714, width: 282, height: 84),
                          size: 94, color: Draw.color(0xFF000000), bold: false,
                          align: .center(640), baseline: 790, kind: .signedAmount,
                          label: "修改金额", bg: fullGrey, maxWidth: 1000),

                FullField(key: "full1_status_label", fallback: "当前状态",
                          clear: CGRect(x: 70, y: 980, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(78), baseline: 1027, kind: .text,
                          label: "修改「当前状态」"),
                FullField(key: "full1_status_value", fallback: "交易成功",
                          clear: CGRect(x: 1029, y: 980, width: 180, height: 55),
                          size: rowSize, color: greenText, bold: false,
                          align: .right(1202), baseline: 1027, kind: .text,
                          label: "修改交易状态"),

                FullField(key: "full1_pay_label", fallback: "付款钱包",
                          clear: CGRect(x: 69, y: 1130, width: 182, height: 60),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(78), baseline: 1178, kind: .text,
                          label: "修改「付款钱包」"),
                FullField(key: "full1_pay_value", fallback: "花花(3691)",
                          clear: CGRect(x: 988, y: 1130, width: 222, height: 60),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1178, kind: .text,
                          label: "修改付款钱包"),

                FullField(key: "full1_recv_label", fallback: "收款钱包",
                          clear: CGRect(x: 70, y: 1280, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(78), baseline: 1328, kind: .text,
                          label: "修改「收款钱包」"),
                FullField(key: "full1_recv_value", fallback: "建设银行匿名钱包",
                          clear: CGRect(x: 858, y: 1280, width: 352, height: 55),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1328, kind: .text,
                          label: "修改收款钱包", maxWidth: 800),

                FullField(key: "full1_acct_label", fallback: "收款钱包编号",
                          clear: CGRect(x: 70, y: 1434, width: 265, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(78), baseline: 1482, kind: .text,
                          label: "修改「收款钱包编号」"),
                FullField(key: "full1_acct_value", fallback: "2393*********0001",
                          clear: CGRect(x: 774, y: 1438, width: 365, height: 50),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1127), baseline: 1482, kind: .text,
                          label: "修改收款钱包编号", maxWidth: 345),

                FullField(key: "full1_time_label", fallback: "交易时间",
                          clear: CGRect(x: 69, y: 1588, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1634, kind: .text,
                          label: "修改「交易时间」"),
                FullField(key: "full1_time_value", fallback: "2026-10-26 17:27:20",
                          clear: CGRect(x: 791, y: 1591, width: 420, height: 50),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1634, kind: .text,
                          label: "修改交易时间", maxWidth: 403),

                FullField(key: "full1_voucher_label", fallback: "凭证号",
                          clear: CGRect(x: 68, y: 1738, width: 140, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(76), baseline: 1784, kind: .text,
                          label: "修改「凭证号」"),
                FullField(key: "full1_voucher_value",
                          fallback: "69473776967547746432233661807635137574",
                          clear: CGRect(x: 276, y: 1741, width: 935, height: 50),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1784, kind: .text,
                          label: "修改凭证号", maxWidth: 918),

                FullField(key: "full1_serial_label", fallback: "流水号",
                          clear: CGRect(x: 69, y: 1888, width: 140, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1934, kind: .text,
                          label: "修改「流水号」"),
                FullField(key: "full1_serial_value",
                          fallback: "1012631964134212734867737",
                          clear: CGRect(x: 608, y: 1891, width: 600, height: 50),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1934, kind: .text,
                          label: "修改流水号", maxWidth: 586),

                FullField(key: "full1_notice", fallback: "通知收款人",
                          clear: CGRect(x: 73, y: 2105, width: 245, height: 60),
                          size: rowSize, color: Draw.color(0xFF4D4D4D), bold: false,
                          align: .left(81), baseline: 2158, kind: .text,
                          label: "修改「通知收款人」", maxWidth: 900),
                FullField(key: "full1_question", fallback: "对此订单有疑问",
                          clear: CGRect(x: 73, y: 2288, width: 333, height: 60),
                          size: rowSize, color: Draw.color(0xFF494949), bold: false,
                          align: .left(81), baseline: 2339, kind: .text,
                          label: "修改「对此订单有疑问」", maxWidth: 900),
                FullField(key: "full1_send", fallback: "给TA转钱",
                          clear: CGRect(x: 536, y: 2482, width: 210, height: 60),
                          size: rowSize, color: Draw.color(0xFF474747), bold: false,
                          align: .center(639), baseline: 2535, kind: .text,
                          label: "修改「给TA转钱」", maxWidth: 600),
            ]
        }

        if sub == 2 {
            let labelColor = Draw.color(0xFF7C7C7C)
            let valueColor = Draw.color(0xFF111111)
            let greenText = Draw.color(0xFF2BA471)
            let rowSize: CGFloat = 46

            return [
                FullField(key: "full2_title", fallback: "交易详情",
                          clear: CGRect(x: 507, y: 158, width: 245, height: 72),
                          size: 60, color: Draw.color(0xFF333333), bold: true,
                          align: .center(640), baseline: 220, kind: .text,
                          label: "修改页面标题", maxWidth: 900),
                FullField(key: "full2_transfer", fallback: "充钱包-建设银行 (1597)",
                          clear: CGRect(x: 383, y: 579, width: 512, height: 66),
                          size: 55, color: Draw.color(0xFF000000), bold: true,
                          align: .center(639), baseline: 636, kind: .text,
                          label: "修改充值说明", bg: fullGrey, maxWidth: 1000),
                FullField(key: "full2_amount", fallback: "+100.00",
                          clear: CGRect(x: 445, y: 688, width: 390, height: 90),
                          size: 101, color: Draw.color(0xFF000000), bold: false,
                          align: .center(640), baseline: 770, kind: .signedAmount,
                          label: "修改金额", bg: fullGrey, maxWidth: 1000),

                FullField(key: "full2_status_label", fallback: "当前状态",
                          clear: CGRect(x: 70, y: 938, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(78), baseline: 986, kind: .text,
                          label: "修改「当前状态」"),
                FullField(key: "full2_status_value", fallback: "交易成功",
                          clear: CGRect(x: 1029, y: 938, width: 180, height: 55),
                          size: rowSize, color: greenText, bold: false,
                          align: .right(1202), baseline: 986, kind: .text,
                          label: "修改交易状态"),

                FullField(key: "full2_type_label", fallback: "银行卡类型",
                          clear: CGRect(x: 69, y: 1088, width: 225, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1136, kind: .text,
                          label: "修改「银行卡类型」"),
                FullField(key: "full2_type_value", fallback: "储蓄卡",
                          clear: CGRect(x: 1068, y: 1088, width: 142, height: 55),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1136, kind: .text,
                          label: "修改银行卡类型"),

                FullField(key: "full2_acct_label", fallback: "银行账户",
                          clear: CGRect(x: 69, y: 1246, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1305, kind: .text,
                          label: "修改「银行账户」"),
                FullField(key: "full2_acct_value", fallback: "建设银行 (1597)",
                          clear: CGRect(x: 892, y: 1252, width: 318, height: 55),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1200), baseline: 1305, kind: .text,
                          label: "修改银行账户", maxWidth: 300),

                FullField(key: "full2_method_label", fallback: "充钱方式",
                          clear: CGRect(x: 69, y: 1402, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1450, kind: .text,
                          label: "修改「充钱方式」"),
                FullField(key: "full2_method_value", fallback: "银行卡转入",
                          clear: CGRect(x: 986, y: 1402, width: 224, height: 55),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1450, kind: .text,
                          label: "修改充钱方式", maxWidth: 800),

                FullField(key: "full2_time_label", fallback: "交易时间",
                          clear: CGRect(x: 69, y: 1553, width: 180, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1600, kind: .text,
                          label: "修改「交易时间」"),
                FullField(key: "full2_time_value", fallback: "2026-10-26 17:27:32",
                          clear: CGRect(x: 793, y: 1556, width: 418, height: 50),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1600, kind: .text,
                          label: "修改交易时间", maxWidth: 401),

                FullField(key: "full2_voucher_label", fallback: "凭证号",
                          clear: CGRect(x: 69, y: 1703, width: 140, height: 55),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(77), baseline: 1749, kind: .text,
                          label: "修改「凭证号」"),
                FullField(key: "full2_voucher_value",
                          fallback: "10100195896870611960170230000000010000",
                          clear: CGRect(x: 238, y: 1706, width: 945, height: 50),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1202), baseline: 1749, kind: .text,
                          label: "修改凭证号", maxWidth: 956),

                FullField(key: "full2_related", fallback: "关联交易",
                          clear: CGRect(x: 69, y: 1948, width: 180, height: 58),
                          size: rowSize, color: Draw.color(0xFF111111), bold: true,
                          align: .left(77), baseline: 1998, kind: .text,
                          label: "修改「关联交易」", maxWidth: 900),
                FullField(key: "full2_question", fallback: "对此订单有疑问",
                          clear: CGRect(x: 68, y: 2152, width: 308, height: 58),
                          size: rowSize, color: Draw.color(0xFF111111), bold: true,
                          align: .left(76), baseline: 2203, kind: .text,
                          label: "修改「对此订单有疑问」", maxWidth: 900),
            ]
        }

        if sub == 3 {
            let labelColor = Draw.color(0xFF7C7C7C)
            let valueColor = Draw.color(0xFF111111)
            let rowSize: CGFloat = 52

            return [
                FullField(key: "full3_title", fallback: "存入钱包",
                          clear: CGRect(x: 514, y: 157, width: 246, height: 64),
                          size: 56, color: .white, bold: true,
                          align: .center(637), baseline: 214, kind: .text,
                          label: "修改页面标题", bg: fullBlue, maxWidth: 600),
                FullField(key: "full3_success", fallback: "存入成功",
                          clear: CGRect(x: 469, y: 842, width: 338, height: 92),
                          size: 86, color: Draw.color(0xFF333333), bold: true,
                          align: .center(637), baseline: 926, kind: .text,
                          label: "修改状态标题", maxWidth: 900),

                FullField(key: "full3_wallet_label", fallback: "钱包编号",
                          clear: CGRect(x: 44, y: 1105, width: 224, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(52), baseline: 1159, kind: .text,
                          label: "修改「钱包编号」"),
                FullField(key: "full3_wallet_value", fallback: "0647***7386",
                          clear: CGRect(x: 900, y: 1105, width: 335, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1227), baseline: 1159, kind: .text,
                          label: "修改钱包编号", maxWidth: 319),

                FullField(key: "full3_amount_label", fallback: "存入金额",
                          clear: CGRect(x: 44, y: 1236, width: 224, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(52), baseline: 1294, kind: .text,
                          label: "修改「存入金额」"),
                FullField(key: "full3_amount_value", fallback: "¥2,000.00",
                          clear: CGRect(x: 962, y: 1236, width: 273, height: 66),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1226), baseline: 1294, kind: .text,
                          label: "修改存入金额", maxWidth: 264),

                FullField(key: "full3_account_label", fallback: "充值账户",
                          clear: CGRect(x: 45, y: 1367, width: 220, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(53), baseline: 1428, kind: .text,
                          label: "修改「充值账户」"),
                FullField(key: "full3_account_value", fallback: "中国建设银行 龙卡通(7641)",
                          clear: CGRect(x: 577, y: 1367, width: 660, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1224), baseline: 1428, kind: .text,
                          label: "修改充值账户", maxWidth: 639),

                FullField(key: "full3_done", fallback: "完成",
                          clear: CGRect(x: 576, y: 2582, width: 130, height: 70),
                          size: 62, color: Draw.color(0xFF4A6FC0), bold: false,
                          align: .center(640), baseline: 2645, kind: .text,
                          label: "修改「完成」按钮文字", maxWidth: 900),
            ]
        }

        if sub == 4 {
            let pageBg = fullPage4Bg
            let rowSize: CGFloat = 52
            let valueColor = Draw.color(0xFF5F5F5F)

            return [
                FullField(key: "full4_done", fallback: "完成",
                          clear: CGRect(x: 1076, y: 167, width: 132, height: 74),
                          size: 64, color: Draw.color(0xFF4A73B9), bold: false,
                          align: .right(1200), baseline: 233, kind: .text,
                          label: "修改右上角「完成」", bg: pageBg, maxWidth: 300),
                FullField(key: "full4_amount", fallback: "2,000.00元",
                          clear: CGRect(x: 380, y: 633, width: 520, height: 112),
                          size: 100, color: Draw.color(0xFF000000), bold: false,
                          align: .center(640), baseline: 735, kind: .text,
                          label: "修改金额", bg: pageBg, maxWidth: 1200),
                FullField(key: "full4_alipay", fallback: "支付宝",
                          clear: CGRect(x: 549, y: 790, width: 180, height: 70),
                          size: 59, color: Draw.color(0xFF111111), bold: false,
                          align: .center(640), baseline: 850, kind: .text,
                          label: "修改付款方式", bg: pageBg, maxWidth: 800),

                FullField(key: "full4_pay_label", fallback: "付款账户",
                          clear: CGRect(x: 44, y: 1077, width: 208, height: 62),
                          size: rowSize, color: Draw.color(0xFF111111), bold: false,
                          align: .left(52), baseline: 1136, kind: .text,
                          label: "修改「付款账户」", bg: pageBg),
                FullField(key: "full4_pay_value", fallback: "中国建设银行 储蓄账户(1234)",
                          clear: CGRect(x: 570, y: 1077, width: 665, height: 66),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1225), baseline: 1136, kind: .text,
                          label: "修改付款账户", bg: pageBg, maxWidth: 647),

                FullField(key: "full4_amt_label", fallback: "付款金额",
                          clear: CGRect(x: 44, y: 1203, width: 212, height: 62),
                          size: rowSize, color: Draw.color(0xFF111111), bold: false,
                          align: .left(52), baseline: 1260, kind: .text,
                          label: "修改「付款金额」", bg: pageBg),
                FullField(key: "full4_amt_value", fallback: "2,000.00元",
                          clear: CGRect(x: 969, y: 1203, width: 258, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1227), baseline: 1260, kind: .text,
                          label: "修改付款金额", bg: pageBg, maxWidth: 250),

                FullField(key: "full4_order_label", fallback: "订单号",
                          clear: CGRect(x: 44, y: 1329, width: 160, height: 50),
                          size: 42, color: Draw.color(0xFF111111), bold: false,
                          align: .left(52), baseline: 1370, kind: .text,
                          label: "修改「订单号」", bg: pageBg),
                FullField(key: "full4_order1", fallback: "202403141309636",
                          clear: CGRect(x: 338, y: 1332, width: 680, height: 52),
                          size: 48, color: valueColor, bold: false,
                          align: .left(347), baseline: 1370, kind: .text,
                          label: "修改订单号第一行", bg: pageBg, maxWidth: 650),
                FullField(key: "full4_order2", fallback: "0303684217243364",
                          clear: CGRect(x: 338, y: 1391, width: 680, height: 58),
                          size: 48, color: valueColor, bold: false,
                          align: .left(347), baseline: 1438, kind: .text,
                          label: "修改订单号第二行", bg: pageBg, maxWidth: 650),
                FullField(key: "full4_copy", fallback: "复制",
                          clear: CGRect(x: 1104, y: 1332, width: 110, height: 54),
                          size: 44, color: Draw.color(0xFF6B6B6B), bold: false,
                          align: .center(1155), baseline: 1379, kind: .text,
                          label: "修改「复制」按钮文字", bg: pageBg, maxWidth: 160),

                FullField(key: "full4_collapse", fallback: "点击收起",
                          clear: CGRect(x: 520, y: 1487, width: 180, height: 55),
                          size: 45, color: Draw.color(0xFF4A73B9), bold: false,
                          align: .left(528), baseline: 1535, kind: .text,
                          label: "修改「点击收起」", bg: pageBg, maxWidth: 168),

                FullField(key: "full4_btn1", fallback: "话费充值",
                          clear: CGRect(x: 140, y: 1677, width: 186, height: 58),
                          size: 46, color: Draw.color(0xFF333333), bold: false,
                          align: .center(233), baseline: 1726, kind: .text,
                          label: "修改「话费充值」按钮", bg: pageBg, maxWidth: 300),
                FullField(key: "full4_btn2", fallback: "查看财富",
                          clear: CGRect(x: 547, y: 1677, width: 184, height: 58),
                          size: 46, color: Draw.color(0xFF333333), bold: false,
                          align: .center(639), baseline: 1726, kind: .text,
                          label: "修改「查看财富」按钮", bg: pageBg, maxWidth: 300),
                FullField(key: "full4_btn3", fallback: "龙支付",
                          clear: CGRect(x: 977, y: 1677, width: 140, height: 58),
                          size: 46, color: .white, bold: false,
                          align: .center(1046), baseline: 1725, kind: .text,
                          label: "修改「龙支付」按钮", bg: Draw.color(0xFF4679D8),
                          maxWidth: 300),
                FullField(key: "full4_more", fallback: "更多精彩",
                          clear: CGRect(x: 45, y: 1907, width: 186, height: 58),
                          size: 46, color: Draw.color(0xFF7C7C7C), bold: false,
                          align: .left(53), baseline: 1956, kind: .text,
                          label: "修改「更多精彩」", bg: pageBg, maxWidth: 600),
            ]
        }

        if sub == 5 {
            let card = UIColor.white
            let labelColor = Draw.color(0xFF6D6D6D)
            let valueColor = Draw.color(0xFF050505)
            let rowSize: CGFloat = 52

            return [
                FullField(key: "full5_title", fallback: "钱包充值",
                          clear: CGRect(x: 510, y: 166, width: 258, height: 74),
                          size: 64, color: Draw.color(0xFF292929), bold: false,
                          align: .center(639), baseline: 233, kind: .text,
                          label: "修改页面标题", bg: fullPage5HeaderBg, maxWidth: 600),
                FullField(key: "full5_done", fallback: "充值完成",
                          clear: CGRect(x: 531, y: 703, width: 216, height: 66),
                          size: 58, color: Draw.color(0xFF323232), bold: false,
                          align: .center(639), baseline: 761, kind: .text,
                          label: "修改状态标题", bg: fullPage5HeaderBg, maxWidth: 600),

                FullField(key: "full5_amt_label", fallback: "充值金额",
                          clear: CGRect(x: 101, y: 925, width: 211, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(109), baseline: 979, kind: .text,
                          label: "修改「充值金额」", bg: card),
                FullField(key: "full5_amt_value", fallback: "¥487.00",
                          clear: CGRect(x: 959, y: 929, width: 219, height: 58),
                          size: 54, color: Draw.color(0xFF030303), bold: false,
                          align: .right(1170), baseline: 979, kind: .text,
                          label: "修改充值金额", bg: card, maxWidth: 210),

                FullField(key: "full5_card_label", fallback: "兑换银行卡号",
                          clear: CGRect(x: 101, y: 1100, width: 309, height: 68),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(109), baseline: 1160, kind: .text,
                          label: "修改「兑换银行卡号」", bg: card),
                FullField(key: "full5_card_value", fallback: "招商银行(尾号0313)",
                          clear: CGRect(x: 726, y: 1100, width: 451, height: 68),
                          size: rowSize, color: Draw.color(0xFF060606), bold: false,
                          align: .right(1168), baseline: 1160, kind: .text,
                          label: "修改兑换银行卡号", bg: card, maxWidth: 440),

                FullField(key: "full5_name_label", fallback: "钱包名称",
                          clear: CGRect(x: 100, y: 1274, width: 212, height: 64),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(108), baseline: 1329, kind: .text,
                          label: "修改「钱包名称」", bg: card),
                FullField(key: "full5_name_value", fallback: "我的钱包棕9",
                          clear: CGRect(x: 889, y: 1274, width: 289, height: 64),
                          size: rowSize, color: Draw.color(0xFF080808), bold: false,
                          align: .right(1170), baseline: 1329, kind: .text,
                          label: "修改钱包名称", bg: card, maxWidth: 280),

                FullField(key: "full5_wallet_label", fallback: "钱包编号",
                          clear: CGRect(x: 100, y: 1450, width: 210, height: 64),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(108), baseline: 1504, kind: .text,
                          label: "修改「钱包编号」", bg: card),
                FullField(key: "full5_wallet_value", fallback: "023335 **** ** 3042",
                          clear: CGRect(x: 708, y: 1453, width: 470, height: 58),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1170), baseline: 1504, kind: .text,
                          label: "修改钱包编号", bg: card, maxWidth: 460),

                FullField(key: "full5_red_button", fallback: "返回“数字人民币”App",
                          clear: CGRect(x: 349, y: 1700, width: 582, height: 74),
                          size: 64, color: .white, bold: false,
                          align: .center(640), baseline: 1764, kind: .text,
                          label: "修改红色按钮文字", bg: fullRed5, maxWidth: 1000),

                FullField(key: "full5_link1", fallback: "交易明细",
                          clear: CGRect(x: 94, y: 1912, width: 235, height: 72),
                          size: 60, color: Draw.color(0xFF3484BD), bold: false,
                          align: .left(102), baseline: 1974, kind: .text,
                          label: "修改「交易明细」", bg: fullPage5Bg, maxWidth: 300),
                FullField(key: "full5_link2", fallback: "查询余额",
                          clear: CGRect(x: 521, y: 1912, width: 238, height: 72),
                          size: 60, color: Draw.color(0xFF3484BD), bold: false,
                          align: .left(529), baseline: 1974, kind: .text,
                          label: "修改「查询余额」", bg: fullPage5Bg, maxWidth: 300),
                FullField(key: "full5_link3", fallback: "电子回单",
                          clear: CGRect(x: 954, y: 1912, width: 232, height: 72),
                          size: 60, color: Draw.color(0xFF3484BD), bold: false,
                          align: .left(962), baseline: 1974, kind: .text,
                          label: "修改「电子回单」", bg: fullPage5Bg, maxWidth: 300),
            ]
        }

        if sub == 6 {
            return [
                FullField(key: "full6_amount", fallback: "¥1000.00",
                          clear: CGRect(x: 395, y: 789, width: 510, height: 100),
                          size: 116, color: Draw.color(0xFF000000), bold: false,
                          align: .center(650), baseline: 882, kind: .text,
                          label: "修改金额", maxWidth: 1000),
                FullField(key: "full6_pay_label", fallback: "付款方式",
                          clear: CGRect(x: 44, y: 969, width: 210, height: 62),
                          size: 52, color: Draw.color(0xFF616161), bold: false,
                          align: .left(52), baseline: 1029, kind: .text,
                          label: "修改「付款方式」"),
                FullField(key: "full6_pay_value", fallback: "招商银行储蓄卡(7677)",
                          clear: CGRect(x: 730, y: 969, width: 502, height: 68),
                          size: 52, color: Draw.color(0xFF4A4A4A), bold: false,
                          align: .right(1224), baseline: 1029, kind: .text,
                          label: "修改付款方式", maxWidth: 490),
                FullField(key: "full6_back_button", fallback: "返回商户",
                          clear: CGRect(x: 518, y: 1709, width: 238, height: 68),
                          size: 60, color: Draw.color(0xFF4A4A4A), bold: false,
                          align: .center(639), baseline: 1769, kind: .text,
                          label: "修改按钮文字", maxWidth: 900),
            ]
        }

        if sub == 7 {
            let rowSize: CGFloat = 52

            return [
                FullField(key: "full7_title", fallback: "数字人民币钱包充值",
                          clear: CGRect(x: 329, y: 183, width: 620, height: 80),
                          size: 70, color: Draw.color(0xFF6C7378), bold: false,
                          align: .center(638), baseline: 256, kind: .text,
                          label: "修改页面标题", maxWidth: 620),
                FullField(key: "full7_success", fallback: "成功",
                          clear: CGRect(x: 744, y: 496, width: 148, height: 80),
                          size: 72, color: Draw.color(0xFF55A374), bold: false,
                          align: .left(752), baseline: 569, kind: .text,
                          label: "修改状态文字", maxWidth: 300),
                FullField(key: "full7_msg1", fallback: "充值申请提交成功,请前往数字人民币AP",
                          clear: CGRect(x: 74, y: 742, width: 1130, height: 72),
                          size: 70, color: Draw.color(0xFF7F7F7F), bold: false,
                          align: .center(639), baseline: 813, kind: .text,
                          label: "修改提示第一行", maxWidth: 1120),
                FullField(key: "full7_msg2", fallback: "P查询充值结果。",
                          clear: CGRect(x: 392, y: 816, width: 496, height: 72),
                          size: 70, color: Draw.color(0xFF7F7F7F), bold: false,
                          align: .center(640), baseline: 883, kind: .text,
                          label: "修改提示第二行", maxWidth: 1120),

                FullField(key: "full7_name_label", fallback: "钱包名称",
                          clear: CGRect(x: 44, y: 1015, width: 236, height: 66),
                          size: rowSize, color: Draw.color(0xFF808080), bold: false,
                          align: .left(52), baseline: 1074, kind: .text,
                          label: "修改「钱包名称」"),
                FullField(key: "full7_name_value", fallback: "卢0632",
                          clear: CGRect(x: 1036, y: 1015, width: 198, height: 66),
                          size: rowSize, color: Draw.color(0xFF040404), bold: false,
                          align: .right(1226), baseline: 1074, kind: .text,
                          label: "修改钱包名称", maxWidth: 400),

                FullField(key: "full7_id_label", fallback: "钱包ID",
                          clear: CGRect(x: 44, y: 1153, width: 180, height: 66),
                          size: rowSize, color: Draw.color(0xFF808080), bold: false,
                          align: .left(52), baseline: 1212, kind: .text,
                          label: "修改「钱包ID」"),
                FullField(key: "full7_id_value", fallback: "0091028928000361",
                          clear: CGRect(x: 699, y: 1157, width: 530, height: 60),
                          size: rowSize, color: Draw.color(0xFF050505), bold: false,
                          align: .right(1221), baseline: 1212, kind: .text,
                          label: "修改钱包ID", maxWidth: 520),

                FullField(key: "full7_amt_label", fallback: "充值金额",
                          clear: CGRect(x: 45, y: 1291, width: 236, height: 66),
                          size: rowSize, color: Draw.color(0xFF808080), bold: false,
                          align: .left(53), baseline: 1350, kind: .text,
                          label: "修改「充值金额」"),
                FullField(key: "full7_amt_value", fallback: "2.00元",
                          clear: CGRect(x: 1051, y: 1294, width: 183, height: 66),
                          size: rowSize, color: Draw.color(0xFF040404), bold: false,
                          align: .right(1226), baseline: 1351, kind: .text,
                          label: "修改充值金额", maxWidth: 175),

                FullField(key: "full7_btn1", fallback: "返回",
                          clear: CGRect(x: 568, y: 1516, width: 140, height: 72),
                          size: 64, color: .white, bold: false,
                          align: .center(637), baseline: 1580, kind: .text,
                          label: "修改红色按钮文字", bg: fullRed7, maxWidth: 300),
                FullField(key: "full7_btn2", fallback: "返回数字人民币APP",
                          clear: CGRect(x: 333, y: 1695, width: 614, height: 82),
                          size: 74, color: Draw.color(0xFFD14A4A), bold: false,
                          align: .center(640), baseline: 1768, kind: .text,
                          label: "修改白色按钮文字", maxWidth: 1100),
            ]
        }

        if sub == 8 {
            let labelColor = Draw.color(0xFF7C7C7C)
            let valueColor = Draw.color(0xFF050505)
            let discount = Draw.color(0xFFA4555F)
            let rowSize: CGFloat = 52

            return [
                FullField(key: "full8_title", fallback: "支付结果",
                          clear: CGRect(x: 513, y: 170, width: 252, height: 72),
                          size: 62, color: Draw.color(0xFF6E7474), bold: false,
                          align: .center(639), baseline: 235, kind: .text,
                          label: "修改页面标题", maxWidth: 600),
                FullField(key: "full8_success", fallback: "成功",
                          clear: CGRect(x: 708, y: 470, width: 134, height: 78),
                          size: 66, color: Draw.color(0xFF64A67D), bold: false,
                          align: .left(716), baseline: 539, kind: .text,
                          label: "修改状态文字", maxWidth: 300),
                FullField(key: "full8_amount", fallback: "¥2,888.00",
                          clear: CGRect(x: 363, y: 704, width: 575, height: 122),
                          size: 112, color: Draw.color(0xFF000000), bold: false,
                          align: .center(650), baseline: 818, kind: .text,
                          label: "修改金额", maxWidth: 1000),
                FullField(key: "full8_sub", fallback: "R*6",
                          clear: CGRect(x: 589, y: 907, width: 102, height: 62),
                          size: 52, color: Draw.color(0xFF000000), bold: false,
                          align: .center(640), baseline: 960, kind: .text,
                          label: "修改付款说明", maxWidth: 600),

                FullField(key: "full8_order_label", fallback: "订单号",
                          clear: CGRect(x: 44, y: 1116, width: 180, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(52), baseline: 1184, kind: .text,
                          label: "修改「订单号」"),
                FullField(key: "full8_order_value", fallback: "08Jdqwlt4_xaQx190787153621736",
                          clear: CGRect(x: 309, y: 1119, width: 925, height: 72),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1226), baseline: 1184, kind: .text,
                          label: "修改订单号", maxWidth: 910),

                FullField(key: "full8_card_label", fallback: "交易卡号",
                          clear: CGRect(x: 45, y: 1246, width: 240, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(53), baseline: 1314, kind: .text,
                          label: "修改「交易卡号」"),
                FullField(key: "full8_card_value", fallback: "工商银行借记卡(9635)",
                          clear: CGRect(x: 653, y: 1246, width: 580, height: 74),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1223), baseline: 1314, kind: .text,
                          label: "修改交易卡号", maxWidth: 560),

                FullField(key: "full8_disc_label", fallback: "优惠卷抵扣",
                          clear: CGRect(x: 44, y: 1377, width: 292, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(52), baseline: 1436, kind: .text,
                          label: "修改「优惠卷抵扣」"),
                FullField(key: "full8_disc_value", fallback: "- ¥0.00",
                          clear: CGRect(x: 1024, y: 1381, width: 210, height: 60),
                          size: rowSize, color: discount, bold: false,
                          align: .right(1226), baseline: 1436, kind: .text,
                          label: "修改优惠卷抵扣", maxWidth: 200),

                FullField(key: "full8_sale_label", fallback: "消费立减",
                          clear: CGRect(x: 45, y: 1508, width: 240, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(53), baseline: 1567, kind: .text,
                          label: "修改「消费立减」"),
                FullField(key: "full8_sale_value", fallback: "¥0.00",
                          clear: CGRect(x: 1060, y: 1512, width: 175, height: 60),
                          size: rowSize, color: discount, bold: false,
                          align: .right(1226), baseline: 1567, kind: .text,
                          label: "修改消费立减", maxWidth: 170),

                FullField(key: "full8_points_label", fallback: "积分抵扣",
                          clear: CGRect(x: 44, y: 1638, width: 240, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(52), baseline: 1698, kind: .text,
                          label: "修改「积分抵扣」"),
                FullField(key: "full8_points_value", fallback: "0.00",
                          clear: CGRect(x: 1105, y: 1643, width: 130, height: 58),
                          size: rowSize, color: discount, bold: false,
                          align: .right(1226), baseline: 1698, kind: .text,
                          label: "修改积分抵扣", maxWidth: 125),

                FullField(key: "full8_time_label", fallback: "交易时间",
                          clear: CGRect(x: 45, y: 1769, width: 240, height: 66),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(53), baseline: 1828, kind: .text,
                          label: "修改「交易时间」"),
                FullField(key: "full8_time_value", fallback: "2026-10-26 19:17:20",
                          clear: CGRect(x: 684, y: 1773, width: 550, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1226), baseline: 1828, kind: .text,
                          label: "修改交易时间", maxWidth: 535),

                FullField(key: "full8_red_button", fallback: "返回",
                          clear: CGRect(x: 583, y: 1945, width: 116, height: 64),
                          size: 56, color: .white, bold: false,
                          align: .center(641), baseline: 1999, kind: .text,
                          label: "修改红色按钮文字", bg: fullRed8, maxWidth: 900),
                FullField(key: "full8_white_button", fallback: "开通指纹/刷脸支付,安全又便捷",
                          clear: CGRect(x: 229, y: 2121, width: 818, height: 74),
                          size: 56, color: Draw.color(0xFFC96374), bold: false,
                          align: .center(638), baseline: 2186, kind: .text,
                          label: "修改白色按钮文字", maxWidth: 1000),
            ]
        }

        if sub == 9 {
            let gray = Draw.color(0xFF808080)
            let rowSize: CGFloat = 50

            return [
                FullField(key: "full9_title", fallback: "交易成功",
                          clear: CGRect(x: 520, y: 167, width: 236, height: 70),
                          size: 56, color: Draw.color(0xFF080808), bold: false,
                          align: .center(638), baseline: 228, kind: .text,
                          label: "修改页面标题", maxWidth: 600),
                FullField(key: "full9_status", fallback: "交易成功",
                          clear: CGRect(x: 511, y: 974, width: 256, height: 76),
                          size: 64, color: Draw.color(0xFF404040), bold: false,
                          align: .center(638), baseline: 1041, kind: .text,
                          label: "修改状态标题", maxWidth: 600),

                FullField(key: "full9_pay_label", fallback: "付款账户：",
                          clear: CGRect(x: 18, y: 1263, width: 210, height: 60),
                          size: rowSize, color: gray, bold: false,
                          align: .left(26), baseline: 1313, kind: .text,
                          label: "修改「付款账户」", maxWidth: 300),
                FullField(key: "full9_pay_value", fallback: "6228****6026",
                          clear: CGRect(x: 924, y: 1267, width: 336, height: 56),
                          size: rowSize, color: gray, bold: false,
                          align: .right(1252), baseline: 1313, kind: .text,
                          label: "修改付款账户", maxWidth: 325),

                FullField(key: "full9_amt_label", fallback: "付款金额：",
                          clear: CGRect(x: 18, y: 1343, width: 210, height: 60),
                          size: rowSize, color: gray, bold: false,
                          align: .left(26), baseline: 1394, kind: .text,
                          label: "修改「付款金额」", maxWidth: 300),
                FullField(key: "full9_amt_value", fallback: "3,000.00元",
                          clear: CGRect(x: 993, y: 1347, width: 266, height: 58),
                          size: rowSize, color: gray, bold: false,
                          align: .right(1251), baseline: 1394, kind: .text,
                          label: "修改付款金额", maxWidth: 255),

                FullField(key: "full9_disc_label", fallback: "优惠金额：",
                          clear: CGRect(x: 19, y: 1423, width: 210, height: 60),
                          size: rowSize, color: gray, bold: false,
                          align: .left(27), baseline: 1476, kind: .text,
                          label: "修改「优惠金额」", maxWidth: 300),
                FullField(key: "full9_disc_value", fallback: "0.00元",
                          clear: CGRect(x: 1095, y: 1427, width: 164, height: 54),
                          size: rowSize, color: gray, bold: false,
                          align: .right(1251), baseline: 1476, kind: .text,
                          label: "修改优惠金额", maxWidth: 155),

                FullField(key: "full9_done", fallback: "完成",
                          clear: CGRect(x: 574, y: 2330, width: 128, height: 68),
                          size: 58, color: .white, bold: false,
                          align: .center(638), baseline: 2391, kind: .text,
                          label: "修改「完成」按钮文字", bg: fullOrange9, maxWidth: 900),
            ]
        }

        if sub == 10 {
            let textColor = Draw.color(0xFF343434)
            let rowSize: CGFloat = 50

            return [
                FullField(key: "full10_title", fallback: "钱包充值结果",
                          clear: CGRect(x: 450, y: 153, width: 384, height: 76),
                          size: 68, color: Draw.color(0xFF2B2A31), bold: false,
                          align: .center(642), baseline: 222, kind: .text,
                          label: "修改页面标题", maxWidth: 700),
                FullField(key: "full10_status", fallback: "充值成功",
                          clear: CGRect(x: 512, y: 726, width: 256, height: 76),
                          size: 68, color: Draw.color(0xFF2B2A31), bold: false,
                          align: .center(640), baseline: 795, kind: .text,
                          label: "修改状态标题", maxWidth: 600),

                FullField(key: "full10_amt_label", fallback: "充值金额",
                          clear: CGRect(x: 48, y: 1058, width: 180, height: 58),
                          size: rowSize, color: textColor, bold: false,
                          align: .left(56), baseline: 1105, kind: .text,
                          label: "修改「充值金额」"),
                FullField(key: "full10_amt_value", fallback: "¥2.00",
                          clear: CGRect(x: 1091, y: 1070, width: 140, height: 50),
                          size: rowSize, color: textColor, bold: false,
                          align: .right(1223), baseline: 1112, kind: .text,
                          label: "修改充值金额", maxWidth: 130),

                FullField(key: "full10_bank_label", fallback: "银行账户",
                          clear: CGRect(x: 47, y: 1184, width: 180, height: 58),
                          size: rowSize, color: textColor, bold: false,
                          align: .left(55), baseline: 1232, kind: .text,
                          label: "修改「银行账户」"),
                FullField(key: "full10_bank_value", fallback: "6229 **** 5752*",
                          clear: CGRect(x: 888, y: 1194, width: 342, height: 56),
                          size: rowSize, color: textColor, bold: false,
                          align: .right(1222), baseline: 1232, kind: .text,
                          label: "修改银行账户", maxWidth: 330),

                FullField(key: "full10_name_label", fallback: "钱包名称",
                          clear: CGRect(x: 47, y: 1311, width: 180, height: 58),
                          size: rowSize, color: textColor, bold: false,
                          align: .left(55), baseline: 1360, kind: .text,
                          label: "修改「钱包名称」"),
                FullField(key: "full10_name_value", fallback: "卢103",
                          clear: CGRect(x: 1098, y: 1319, width: 133, height: 60),
                          size: rowSize, color: textColor, bold: false,
                          align: .right(1223), baseline: 1370, kind: .text,
                          label: "修改钱包名称", maxWidth: 125),

                FullField(key: "full10_id_label", fallback: "钱包编号",
                          clear: CGRect(x: 47, y: 1439, width: 180, height: 58),
                          size: rowSize, color: textColor, bold: false,
                          align: .left(55), baseline: 1487, kind: .text,
                          label: "修改「钱包编号」"),
                FullField(key: "full10_id_value", fallback: "0092****3625",
                          clear: CGRect(x: 930, y: 1448, width: 300, height: 54),
                          size: rowSize, color: textColor, bold: false,
                          align: .right(1223), baseline: 1487, kind: .text,
                          label: "修改钱包编号", maxWidth: 290),

                FullField(key: "full10_org_label", fallback: "所属机构",
                          clear: CGRect(x: 47, y: 1565, width: 180, height: 58),
                          size: rowSize, color: textColor, bold: false,
                          align: .left(55), baseline: 1614, kind: .text,
                          label: "修改「所属机构」"),
                FullField(key: "full10_org_value", fallback: "交通银行",
                          clear: CGRect(x: 1036, y: 1573, width: 196, height: 60),
                          size: rowSize, color: textColor, bold: false,
                          align: .right(1224), baseline: 1614, kind: .text,
                          label: "修改所属机构", maxWidth: 185),

                FullField(key: "full10_blue_button", fallback: "访问兴业银行App",
                          clear: CGRect(x: 418, y: 1799, width: 446, height: 74),
                          size: 58, color: .white, bold: false,
                          align: .center(640), baseline: 1865, kind: .text,
                          label: "修改蓝色按钮文字", bg: fullBlue10, maxWidth: 1100),
                FullField(key: "full10_white_button", fallback: "返回数字人民币App",
                          clear: CGRect(x: 388, y: 2008, width: 504, height: 76),
                          size: 58, color: Draw.color(0xFF466492), bold: false,
                          align: .center(639), baseline: 2076, kind: .text,
                          label: "修改白色按钮文字", maxWidth: 1100),

                FullField(key: "full10_warn1",
                          fallback: "温馨提示：您可通过兴业银行App>>数字人民币专区>>",
                          clear: CGRect(x: 54, y: 2191, width: 1172, height: 64),
                          size: 50, color: fullWarn10, bold: false,
                          align: .left(54), baseline: 2249, kind: .text,
                          label: "修改提示第一行", bg: fullPage5Bg, maxWidth: 1172),
                FullField(key: "full10_warn2",
                          fallback: "交易明细功能，查询本次交易详情。如有任何疑问，请",
                          clear: CGRect(x: 52, y: 2267, width: 1172, height: 62),
                          size: 50, color: fullWarn10, bold: false,
                          align: .left(52), baseline: 2323, kind: .text,
                          label: "修改提示第二行", bg: fullPage5Bg, maxWidth: 1172),
                FullField(key: "full10_warn3",
                          fallback: "您通过在线客服或拨打兴业银行官方客服电话95561寻",
                          clear: CGRect(x: 55, y: 2342, width: 1172, height: 58),
                          size: 50, color: fullWarn10, bold: false,
                          align: .left(55), baseline: 2394, kind: .text,
                          label: "修改提示第三行", bg: fullPage5Bg, maxWidth: 1172),
                FullField(key: "full10_warn4",
                          fallback: "求帮助，感谢您的理解与支持！",
                          clear: CGRect(x: 56, y: 2418, width: 640, height: 58),
                          size: 50, color: fullWarn10, bold: false,
                          align: .left(56), baseline: 2470, kind: .text,
                          label: "修改提示第四行", bg: fullPage5Bg, maxWidth: 1172),
            ]
        }

        if sub == 11 {
            let labelColor = Draw.color(0xFF7F7F7F)
            let valueColor = Draw.color(0xFF070707)
            let rowSize: CGFloat = 52

            return [
                FullField(key: "full11_title", fallback: "交易详情",
                          clear: CGRect(x: 483, y: 184, width: 264, height: 76),
                          size: 68, color: Draw.color(0xFF323232), bold: false,
                          align: .center(639), baseline: 253, kind: .text,
                          label: "修改页面标题", maxWidth: 600),
                FullField(key: "full11_wallet", fallback: "我的钱包密5",
                          clear: CGRect(x: 494, y: 420, width: 291, height: 64),
                          size: 54, color: Draw.color(0xFF040404), bold: false,
                          align: .center(640), baseline: 476, kind: .text,
                          label: "修改钱包名称", maxWidth: 600),
                FullField(key: "full11_amount", fallback: "- ¥521.05",
                          clear: CGRect(x: 469, y: 554, width: 341, height: 76),
                          size: 76, color: Draw.color(0xFF020202), bold: false,
                          align: .center(640), baseline: 621, kind: .text,
                          label: "修改金额", maxWidth: 600),
                FullField(key: "full11_balance", fallback: "余额 ¥521.05",
                          clear: CGRect(x: 509, y: 648, width: 262, height: 54),
                          size: 46, color: Draw.color(0xFF7F7F7F), bold: false,
                          align: .center(640), baseline: 694, kind: .text,
                          label: "修改余额", maxWidth: 500),

                FullField(key: "full11_card_label", fallback: "交易卡号",
                          clear: CGRect(x: 112, y: 798, width: 210, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(120), baseline: 852, kind: .text,
                          label: "修改「交易卡号」"),
                FullField(key: "full11_card_value", fallback: "6217 **** 7856",
                          clear: CGRect(x: 812, y: 802, width: 356, height: 58),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1160), baseline: 852, kind: .text,
                          label: "修改交易卡号", maxWidth: 340),

                FullField(key: "full11_time_label", fallback: "交易时间",
                          clear: CGRect(x: 112, y: 942, width: 210, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(120), baseline: 996, kind: .text,
                          label: "修改「交易时间」"),
                FullField(key: "full11_time_value", fallback: "2026-10-26 19:22:16",
                          clear: CGRect(x: 685, y: 946, width: 483, height: 58),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1160), baseline: 996, kind: .text,
                          label: "修改交易时间", maxWidth: 468),

                FullField(key: "full11_acct_label", fallback: "收款账号",
                          clear: CGRect(x: 113, y: 1087, width: 210, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(121), baseline: 1140, kind: .text,
                          label: "修改「收款账号」"),
                FullField(key: "full11_acct_value", fallback: "6217 **** 3918",
                          clear: CGRect(x: 818, y: 1090, width: 350, height: 58),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1160), baseline: 1140, kind: .text,
                          label: "修改收款账号", maxWidth: 334),

                FullField(key: "full11_bank_label", fallback: "收款银行",
                          clear: CGRect(x: 113, y: 1230, width: 210, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(121), baseline: 1284, kind: .text,
                          label: "修改「收款银行」"),
                FullField(key: "full11_bank_value", fallback: "中国农业银行",
                          clear: CGRect(x: 861, y: 1230, width: 306, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1160), baseline: 1284, kind: .text,
                          label: "修改收款银行", maxWidth: 291),

                FullField(key: "full11_note_label", fallback: "附言",
                          clear: CGRect(x: 114, y: 1375, width: 120, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(122), baseline: 1428, kind: .text,
                          label: "修改「附言」"),
                FullField(key: "full11_note_value", fallback: "汇款兑出",
                          clear: CGRect(x: 958, y: 1374, width: 205, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1155), baseline: 1428, kind: .text,
                          label: "修改附言", maxWidth: 190),

                FullField(key: "full11_summary_label", fallback: "摘要",
                          clear: CGRect(x: 112, y: 1519, width: 120, height: 62),
                          size: rowSize, color: labelColor, bold: false,
                          align: .left(120), baseline: 1572, kind: .text,
                          label: "修改「摘要」"),
                FullField(key: "full11_summary_value", fallback: "支取数币",
                          clear: CGRect(x: 958, y: 1518, width: 207, height: 62),
                          size: rowSize, color: valueColor, bold: false,
                          align: .right(1157), baseline: 1572, kind: .text,
                          label: "修改摘要", maxWidth: 192),

                FullField(key: "full11_type_label", fallback: "交易类型",
                          clear: CGRect(x: 112, y: 1751, width: 210, height: 62),
                          size: rowSize, color: Draw.color(0xFF111111), bold: false,
                          align: .left(120), baseline: 1812, kind: .text,
                          label: "修改「交易类型」"),
                FullField(key: "full11_type_value", fallback: "转账给他人",
                          clear: CGRect(x: 837, y: 1751, width: 260, height: 62),
                          size: rowSize, color: Draw.color(0xFF111111), bold: false,
                          align: .right(1088), baseline: 1812, kind: .text,
                          label: "修改交易类型", maxWidth: 244),

                FullField(key: "full11_book_label", fallback: "所属账本",
                          clear: CGRect(x: 111, y: 1945, width: 210, height: 62),
                          size: rowSize, color: Draw.color(0xFF111111), bold: false,
                          align: .left(119), baseline: 1999, kind: .text,
                          label: "修改「所属账本」"),
                FullField(key: "full11_book_value", fallback: "请选择",
                          clear: CGRect(x: 935, y: 1945, width: 160, height: 62),
                          size: rowSize, color: Draw.color(0xFF999999), bold: false,
                          align: .right(1086), baseline: 1999, kind: .text,
                          label: "修改所属账本", maxWidth: 144),

                FullField(key: "full11_toggle_label", fallback: "不计入本月收支",
                          clear: CGRect(x: 112, y: 2152, width: 358, height: 78),
                          size: rowSize, color: Draw.color(0xFF111111), bold: false,
                          align: .left(120), baseline: 2223, kind: .text,
                          label: "修改「不计入本月收支」", maxWidth: 340),
            ]
        }
        return []
    }

    static func fullField(_ sub: Int, _ key: String) -> FullField? {
        fullFields(sub).first { $0.key == key }
    }

    /// 重画一个被改过的字段。调用前 `clear` 内已经铺过底色。
    static func drawFullField(_ d: Draw, _ f: FullField, _ raw: String) {
        var value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return }

        switch f.kind {
        case .amount:
            // 用户可能把 ¥ 一起打进来，这里只保留数字部分，¥ 用原图里抠出的字形。
            if value.hasPrefix("¥") || value.hasPrefix("￥") { value.removeFirst() }
            value = value.trimmingCharacters(in: .whitespaces)
            let yenW: CGFloat = 49
            let gap: CGFloat = 16
            let font = Fonts.number(f.size)
            let w = (value as NSString).size(withAttributes: [.font: font]).width
            let cx: CGFloat
            if case .center(let c) = f.align { cx = c } else { cx = f.clear.midX }
            let start = cx - (yenW + gap + w) / 2
            d.art(UIImage(named: "mock_sz_yen"),
                  CGRect(x: start, y: 999, width: yenW, height: 94))
            d.text(value, x: start + yenW + gap, baseline: f.baseline,
                   size: f.size, f.color, useNumberFace: true)

        case .signedAmount:
            var size = f.size
            if f.maxWidth > 0 {
                let base = Fonts.number(size)
                let w = (value as NSString).size(withAttributes: [.font: base]).width
                if w > f.maxWidth { size *= f.maxWidth / w }
            }
            let font = Fonts.number(size)
            let w = (value as NSString).size(withAttributes: [.font: font]).width
            let cx: CGFloat
            if case .center(let c) = f.align { cx = c } else { cx = f.clear.midX }
            d.text(value, x: cx - w / 2, baseline: f.baseline,
                   size: size, f.color, useNumberFace: true)

        case .recipient:
            let font = Draw.font(size: f.size, bold: true)
            let textW = (value as NSString).size(withAttributes: [.font: font]).width
            let pencil = UIImage(named: "mock_sz_pencil")
            let scale = f.size / 67
            let penW = 56 * scale
            let penH = 62 * scale
            let gap = 34 * scale
            let total = textW + gap + penW
            let center: CGFloat
            if case .center(let c) = f.align { center = c } else { center = f.clear.midX }
            let start = center - total / 2
            d.text(value, x: start, baseline: f.baseline, size: f.size,
                   f.color, bold: true)
            d.art(pencil, CGRect(x: start + textW + gap,
                                 y: f.baseline - penH + 5 * scale,
                                 width: penW, height: penH))

        case .text:
            var size = f.size
            var font = Draw.font(size: size, bold: f.bold)
            if f.maxWidth > 0 {
                let w = (value as NSString).size(withAttributes: [.font: font]).width
                if w > f.maxWidth {
                    size *= f.maxWidth / w
                    font = Draw.font(size: size, bold: f.bold)
                }
            }
            switch f.align {
            case .left(let x):
                d.text(value, x: x, baseline: f.baseline, size: size,
                       f.color, bold: f.bold)
            case .center(let cx):
                d.centerText(value, cx: cx, baseline: f.baseline, size: size,
                             f.color, bold: f.bold)
            case .right(let right):
                d.rightText(value, right: right, baseline: f.baseline, size: size,
                            f.color, bold: f.bold)
            }
        }
    }

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 整页纯白（安卓实测 sz_sub0 底色 #ffffff）
        b.autoBoard = nil
        // 12 个子页共用「转钱成功」版式：银行从子页名取，转入/存入取正
        transferred(b, state, showsTopTitle: showsTopTitle)
        return b.result
    }

    /// 子页名 → (银行, 是否转入)
    private static func bank(_ sub: Int) -> (name: String, incoming: Bool) {
        let names = MockModule.shuzi.subs
        guard names.indices.contains(sub) else { return ("建设银行", false) }
        let name = names[sub]
        let incoming = name.contains("转入") || name.contains("存入")
        let banks = ["建设", "招商", "工商", "农业", "兴业", "邮政"]
        let hit = banks.first { name.hasPrefix($0) } ?? "建设"
        return (hit + "银行", incoming)
    }

    private static func transferred(_ b: MockBuilder, _ s: MockState,
                                    showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转钱成功") }

        // 顶栏：左 ✕、右「继续转钱」
        b.coloredHeaderBar("", bg: .white, fg: MockInk.title, trailing: "继续转钱")
        b.sectionLabel("✕", size: dp(22), color: MockInk.title,
                       padH: dp(16), padTop: 0, padBottom: 0)

        // 对勾 + 转钱成功（安卓实测：勾直径 110px，标题 18sp）
        b.gap(dp(20))
        b.checkBadge(diameter: dp(110), color: green, gapTop: dp(10), symbolSize: dp(64))
        b.sectionLabel("转钱成功", size: dp(18), color: MockInk.title,
                       padH: 0, padTop: dp(20), padBottom: 0)

        // 金额（实测文字高 74px ≈ 34sp）
        b.sectionLabel(s.value("金额", "¥70.00"), size: dp(34), color: MockInk.black,
                       padH: 0, padTop: dp(60), padBottom: 0)

        // 收款方 + 卡号
        b.sectionLabel(s.value("收款方", bank(s.sub).incoming
                                   ? "收钱从 零花钱 ✏" : "转钱给 零花钱 ✏"),
                       size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(34), padBottom: 0)
        b.sectionLabel(s.value("收款卡号", bank(s.sub).name + " 0051****1679"), size: dp(15),
                       color: MockInk.label, padH: 0, padTop: dp(10), padBottom: 0)

        // 完成 / 通知收款人
        b.gap(dp(320))
        b.fullButton("完成", filled: false, color: MockInk.line,
                     height: dp(48), padH: dp(56), gapTop: dp(10), size: dp(17))
        b.sectionLabel("通知收款人", size: dp(15), color: MockInk.title,
                       padH: 0, padTop: dp(16), padBottom: dp(10))
    }
}
