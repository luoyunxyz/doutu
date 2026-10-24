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
        switch state.sub {
        case 0:
            query(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .liushui, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func query(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("明细查询") }

        b.coloredHeaderBar("明细查询", bg: .white, fg: MockInk.title)

        // 卡号行
        b.sectionLabel("Ꮚ  6228 **** 1259                    人民币 | 本币 ▾",
                       size: dp(19), color: MockInk.title,
                       padH: dp(16), padTop: dp(10), padBottom: dp(12))

        // 筛选条
        b.fill(dp(64), filterBg)
        b.sectionLabel("近一周        近一月        近三月        筛选 ▾",
                       size: dp(16), color: MockInk.title,
                       padH: dp(16), padTop: dp(14), padBottom: 0)

        // 空列表
        b.gap(dp(400))
        _ = orange
        _ = s
    }
}
