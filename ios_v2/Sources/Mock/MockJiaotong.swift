import UIKit

/// 「模拟 → 交通模拟」的子页版式（入口 9）。
///
/// 10 个子页（设备抽屉）：`明细转出1(新) / 明细转入1 / 明细转出2(新) / 支付宝转入2(新) /
/// 明细转入2 / 明细转入3 / 支付宝转入1 / 明细转出3(新) / 回单 / 付款结果`。
/// 0 号是「账号转账」成功页：绿勾、金额、两行明细、三条蓝色链接、推荐卡、活动 banner。
enum MockJiaotong {

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
        case 0:
            transferSuccess(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .jiaotong, metrics: metrics, width: width)
        }
        return b.result
    }

    // MARK: - 子页 0：账号转账成功

    private static func transferSuccess(_ b: MockBuilder, _ s: MockState,
                                        showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("账号转账") }

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
