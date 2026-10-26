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

    static func hasFullPage(_ sub: Int) -> Bool { sub == 0 || sub == 1 }

    /// 数字钱包整页版式：0=建设转出1（转钱成功），1=建设转出2（交易详情）。
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
