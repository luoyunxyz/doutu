import UIKit

/// 「模拟 → 浦发模拟」的子页版式（入口 11）。
///
/// 4 个子页（设备抽屉）：`明细转出1 / 明细转出2 / 明细转入 / 支付宝转入`。
/// 0 号是「转账结果」：蓝描边对勾、金额、三条蓝链接、SIM 盾勾选说明、
/// 「微信通知收款人 / 完成」两个按钮、热门指数 banner。
enum MockPufa {

    /// 浦发蓝（截图取样）
    static let blue = UIColor(red: 0x1a / 255.0, green: 0x4f / 255.0,
                              blue: 0xd8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            transferResult(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .pufa, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func transferResult(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转账结果") }

        // 白底顶栏：居中「转账结果」+ 右上「汇款回单」
        b.coloredHeaderBar("转账结果", bg: .white, fg: MockInk.title, trailing: "汇款回单")

        // 蓝描边对勾
        b.gap(dp(26))
        b.checkBadge(diameter: dp(72), color: blue, gapTop: dp(4), symbolSize: dp(40))

        // 转账成功 + 金额
        b.sectionLabel("转账成功", size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(16), padBottom: 0)
        b.sectionLabel(s.value("金额", "¥ 15.00"), size: dp(24), color: MockInk.title,
                       padH: 0, padTop: dp(8), padBottom: 0)

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
