import UIKit

/// 「模拟 → 欧易模拟」的子页版式（宫格索引 26）。
///
/// 4 个子页：`链上提币 / 请求提币 / 内部转账 / 转出`。
/// 0 号是「提币详情」：数量 + 金额 + 已转出、灰底提示卡、八行明细、
/// 底部黑胶囊「在区块链浏览器上查看」。
enum MockOkx {

    /// 状态绿
    static let green = UIColor(red: 0x2f / 255.0, green: 0xb3 / 255.0,
                               blue: 0x7a / 255.0, alpha: 1)
    /// 提示链接蓝
    static let linkBlue = UIColor(red: 0x2f / 255.0, green: 0x6f / 255.0,
                                  blue: 0xd8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 四个子页共用「提币详情」版式，只换类型文案
        withdrawDetail(b, state, showsTopTitle: showsTopTitle)
        return b.result
    }

    private static func withdrawDetail(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("提币详情") }

        b.coloredHeaderBar("提币详情", bg: .white, fg: MockInk.title)

        // 数量 / 金额 / 状态
        b.sectionLabel("数量", size: dp(14), color: MockInk.label,
                       padH: 0, padTop: dp(22), padBottom: 0)
        b.sectionLabel(s.value("金额", "-22.229566 TRX"), size: dp(26),
                       color: MockInk.title, padH: 0, padTop: dp(6), padBottom: 0)
        b.sectionLabel("✓ 已转出", size: dp(15), color: green,
                       padH: 0, padTop: dp(6), padBottom: 0)

        // 灰底提示卡
        b.panel(gapTop: dp(20), margin: dp(12), pad: dp(14), bg: MockInk.page) { p in
            p.sectionLabel("资产已成功转出欧易", size: dp(15), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(6))
            p.sectionLabel("为什么我的交易尚未到账？", size: dp(14), color: linkBlue,
                           padH: 0, padTop: 0, padBottom: 0)
        }

        // 八行明细
        b.panel(gapTop: dp(14), margin: 0, pad: dp(16), bg: .white) { p in
            p.row("网络", "TRC20")
            let kinds = MockModule.okx.subs
            p.row("类型", kinds.indices.contains(s.sub) ? kinds[s.sub] : "链上提币")
            p.row("状态", "已转出")
            p.row("提币地址", s.value("提币地址", "TTJeBTxhG12rJaoBXvssfvMPa9z7asKPa4"))
            p.row("区块链交易ID", s.value("区块链交易ID",
                                       "a31471440cd6545d5cc9c30b4959762c39cbe03cccd03d89a509f574de32e2c5f"))
            p.row("手续费", "0.8TRX")
            p.row("时间", s.time)
            p.row("参考编号", "133228641")
        }

        // 底部黑胶囊
        b.gap(dp(120))
        b.fullButton("在区块链浏览器上查看", filled: true, color: MockInk.black,
                     height: dp(48), padH: dp(24), gapTop: dp(10), size: dp(17))
        b.gap(dp(20))
    }
}
