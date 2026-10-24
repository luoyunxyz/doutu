import UIKit

/// 「模拟 → 流水」的子页版式（宫格索引 21）。
///
/// 7 个子页：`农业流水 / 微信零钱明细 / 微信账单明细 / 支宝余额明细 /
/// 支宝话费 / 支宝账单 / 网商账单`。
/// 0 号是农行「明细查询」：顶栏、卡号行、四个时间筛选项、下方空列表。
enum MockLiushui {

    /// 选中筛选项的橙
    static let orange = UIColor(red: 0xe8 / 255.0, green: 0xa0 / 255.0,
                                blue: 0x3a / 255.0, alpha: 1)
    /// 筛选条底色
    static let filterBg = UIColor(red: 0xf5 / 255.0, green: 0xf6 / 255.0,
                                  blue: 0xf8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 七个子页都是「明细查询」列表：标题取子页名，条数/来源按子页区分
        query(b, state, showsTopTitle: showsTopTitle)
        return b.result
    }

    private static func query(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let names = MockModule.liushui.subs
        let title = names.indices.contains(s.sub) ? names[s.sub] : "明细查询"
        if showsTopTitle { b.titleBar(title) }
        // 内容下方露 #eeeeee 底板（与农业明细查询一致）
        b.autoBoard = MockInk.board

        b.coloredHeaderBar(title, bg: .white, fg: MockInk.title)

        // 卡号行（按子页换来源）
        let account: String
        switch s.sub {
        case 1, 2: account = "微信零钱                       人民币"
        case 3, 4, 5: account = "支付宝余额                     人民币"
        case 6: account = "网商银行 **** 6688            人民币"
        default: account = "Ꮚ  6228 **** 1259              人民币 | 本币 ▾"
        }
        b.sectionLabel(account, size: dp(19), color: MockInk.title,
                       padH: dp(16), padTop: dp(10), padBottom: dp(12))

        // 筛选条
        b.fill(dp(64), filterBg)
        b.sectionLabel("近一周        近一月        近三月        筛选 ▾",
                       size: dp(16), color: MockInk.title,
                       padH: dp(16), padTop: dp(14), padBottom: dp(10))

        // 行距按安卓实测：13sp 字 + 上下 4dp
        let rowSize = dp(13)
        let rowPadV = dp(4)
        b.row("2026-10-24 19:40  转出", "-100.00", size: rowSize, padV: rowPadV)
        b.row("2026-10-23 12:05  转入", "+200.00", size: rowSize, padV: rowPadV)
        b.row("2026-10-22 08:30  消费", "-19.90", size: rowSize, padV: rowPadV)
        b.row("2026-10-21 21:10  退款", "+29.00", size: rowSize, padV: rowPadV)
        b.gap(dp(20))
    }
}
