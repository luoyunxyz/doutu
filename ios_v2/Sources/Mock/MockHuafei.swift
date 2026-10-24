import UIKit

/// 「模拟 → 话费」的子页版式（宫格索引 27）。
///
/// 12 个子页（设备抽屉）：`微电信话费 / 微移动信话费 / 微联通话费 / 支移动话费 /
/// 支电信话费 / 支电信话费2 / 支付电力2 / 手机 / 支付宝联通话费 / 支宝话费3 /
/// 微信充值 / 移动`。
/// 0 号是「充值订单详情」：绿色 ¥ 图标、套餐名、手机号、查余额链接、
/// 五行明细、五条客服/处理入口。
enum MockHuafei {

    /// 主题绿
    static let green = UIColor(red: 0x2f / 255.0, green: 0xb3 / 255.0,
                               blue: 0x5a / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            orderDetail(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .huafei, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func orderDetail(_ b: MockBuilder, _ s: MockState,
                                    showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("充值订单") }

        // 顶栏（左侧返回，右侧两个圆形按钮）
        b.sectionLabel("‹                       ⋯   ◎", size: dp(20), color: MockInk.title,
                       padH: dp(16), padTop: dp(12), padBottom: 0)

        // 绿色 ¥ 图标
        b.sectionLabel("¥", size: dp(40), color: .white,
                       padH: 0, padTop: dp(18), padBottom: 0)
        b.sectionLabel(s.value("套餐", "广东电信50元"), size: dp(22), color: MockInk.title,
                       padH: 0, padTop: dp(10), padBottom: 0)
        b.sectionLabel(s.value("手机号", "177 6594 2365"), size: dp(17),
                       color: MockInk.label, padH: 0, padTop: dp(6), padBottom: 0)
        b.sectionLabel("查余额", size: dp(15), color: green,
                       padH: 0, padTop: dp(6), padBottom: dp(12))

        // 明细
        b.row("当前状态", "充值成功")
        b.row("支付金额", s.value("支付金额", "¥50"))
        b.row("下单时间", s.time)
        b.row("交易单号", "8124068569954568456258745624")
        b.paragraph("充值成功，可登陆运营商APP核实到账情况，月初（月末）为充值高峰，"
                    + "可能会延迟到账，感谢你的理解。",
                    size: dp(15), color: MockInk.label,
                    padH: dp(16), padTop: dp(8), padBottom: dp(12))

        // 五条入口
        b.fill(dp(8), MockInk.page)
        for title in ["充值未到账", "充错号码", "删除订单", "开发票指引", "客服帮助"] {
            b.chevronRow(title, size: dp(18), color: MockInk.title,
                         padH: dp(16), padV: dp(18))
        }
    }
}
