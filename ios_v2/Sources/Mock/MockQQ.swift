import UIKit

/// 「模拟 → qq」的子页版式（宫格索引 29）。
///
/// 6 个子页：`转账 / 流水记录 / 流水 / 广告 / 转出 / 转出2`。
/// 0 号是「交易详情」：居中标题、绿色 ✓ 支付成功、七行明细、三条带箭头的入口。
enum MockQQ {

    /// 支付成功绿
    static let green = UIColor(red: 0x3c / 255.0, green: 0xbf / 255.0,
                               blue: 0x4f / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            detail(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .qq, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func detail(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("交易详情") }

        // 居中标题 + 绿勾
        b.sectionLabel("交易详情", size: dp(19), color: MockInk.title,
                       padH: 0, padTop: dp(14), padBottom: 0)
        b.sectionLabel("✓  支付成功", size: dp(20), color: green,
                       padH: 0, padTop: dp(14), padBottom: dp(10))
        b.hairlineFull()

        // 七行明细
        b.row("金额", s.value("金额", "1,000.00"))
        b.row("商品", s.value("商品", "QQ会员"))
        b.row("商户全称", s.value("商户全称", "腾讯米大事移动支付"))
        b.row("支付方式", s.value("支付方式", "邮政银行(3728)"))
        b.row("交易时间", s.value("交易时间", s.time))
        b.row("交易订单", s.value("交易订单", "74311277724670744324628546038961"))
        b.row("商户订单", s.value("商户订单", "19240758031852801729335666864227"))

        // 三条入口
        b.fill(dp(8), MockInk.page)
        for title in ["联系商户", "修改密码", "客服中心"] {
            b.chevronRow(title, size: dp(18), color: MockInk.title,
                         padH: dp(16), padV: dp(20))
        }
    }
}
