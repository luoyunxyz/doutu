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
        // 子页 1/2 是流水列表，其余共用「交易详情」版式
        switch state.sub {
        case 1, 2:
            if showsTopTitle { b.titleBar("流水") }
            b.coloredHeaderBar("流水", bg: .white, fg: MockInk.title)
            b.row("2026-10-24 19:47", "-1,000.00")
            b.row("2026-10-23 12:05", "+200.00")
            b.row("2026-10-22 08:30", "-19.90")
            b.row("2026-10-21 21:10", "+88.00")
            b.gap(dp(20))
        case 3:
            if showsTopTitle { b.titleBar("广告") }
            b.coloredHeaderBar("广告", bg: .white, fg: MockInk.title)
            b.sectionLabel("“充值特惠”", size: dp(22), color: MockInk.title,
                           padH: dp(16), padTop: dp(24), padBottom: dp(8))
            b.paragraph("充值 100 元立减 10 元，活动规则见页面底部。",
                        size: dp(15), color: MockInk.label,
                        padH: dp(16), padTop: 0, padBottom: dp(20))
        default:
            detail(b, state, showsTopTitle: showsTopTitle)
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

        // 七行明细（行距按安卓实测 qq_sub0：14sp 字 + 上下 7dp，行距 ~90px）
        let rs = dp(14)
        let rp = dp(7)
        b.row("金额", s.value("金额", "1,000.00"), size: rs, padV: rp)
        b.row("商品", s.value("商品", "QQ会员"), size: rs, padV: rp)
        b.row("商户全称", s.value("商户全称", "腾讯米大事移动支付"), size: rs, padV: rp)
        b.row("支付方式", s.value("支付方式", "邮政银行(3728)"), size: rs, padV: rp)
        b.row("交易时间", s.value("交易时间", s.time), size: rs, padV: rp)
        b.row("交易订单", s.value("交易订单", "74311277724670744324628546038961"),
              size: rs, padV: rp)
        b.row("商户订单", s.value("商户订单", "19240758031852801729335666864227"),
              size: rs, padV: rp)

        // 三条入口
        b.fill(dp(8), MockInk.page)
        for title in ["联系商户", "修改密码", "客服中心"] {
            b.chevronRow(title, size: dp(18), color: MockInk.title,
                         padH: dp(16), padV: dp(20))
        }
    }
}
