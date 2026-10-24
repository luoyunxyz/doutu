import UIKit

/// 「模拟 → TP钱包」的子页版式（宫格索引 25）。
///
/// 2 个子页：`转入 / 转出`。
/// 0 号是「交易详情」：绿勾 + 交易成功 + `+70 USDT`、发送方/接收方卡、
/// 网络配置三个胶囊、交易哈希/区块高度/交易时间卡（右侧二维码位）、
/// 底部「前往查询详细信息」+ 三个区块浏览器入口。
enum MockTPWallet {

    /// TP 绿（截图取样）
    static let green = UIColor(red: 0x1d / 255.0, green: 0xc0 / 255.0,
                               blue: 0x8a / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            detail(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .tp, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func detail(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("交易详情") }

        b.fill(dp(0), MockInk.page)
        b.coloredHeaderBar("交易详情", bg: MockInk.page, fg: MockInk.title)

        // 绿勾 + 交易成功 + 金额
        b.gap(dp(16))
        b.checkBadge(diameter: dp(76), color: green, gapTop: dp(4), symbolSize: dp(42))
        b.sectionLabel("交易成功", size: dp(17), color: green,
                       padH: 0, padTop: dp(10), padBottom: 0)
        b.sectionLabel(s.value("金额", "+70 USDT"), size: dp(26), color: MockInk.title,
                       padH: 0, padTop: dp(6), padBottom: 0)

        // 发送方 / 接收方
        b.panel(gapTop: dp(14), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.sectionLabel("发送方", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: 0, padBottom: 0)
            p.sectionLabel(s.value("发送方", "TB1k1GYtq…HC1rkgPY6"), size: dp(17),
                           color: MockInk.title, padH: 0, padTop: dp(4), padBottom: 0)
            p.sectionLabel("接收方", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: dp(10), padBottom: 0)
            p.sectionLabel(s.value("接收方", "TTJeBTxh…a9z7asKPa4"), size: dp(17),
                           color: MockInk.title, padH: 0, padTop: dp(4), padBottom: 0)
            p.sectionLabel("(TRX-1)", size: dp(13), color: MockInk.label,
                           padH: 0, padTop: dp(2), padBottom: 0)
            p.sectionLabel("网络配置", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: dp(12), padBottom: dp(6))
            p.centeredCells(["13.7409 TRX", "345 带宽", "31,895 能量"],
                            height: dp(30), size: dp(14), padV: dp(2))
        }

        // 交易哈希 / 区块高度 / 交易时间
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.sectionLabel("交易哈希", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: 0, padBottom: 0)
            p.sectionLabel(s.value("交易哈希", "6cfddb68b…df013c8d59"), size: dp(17),
                           color: MockInk.title, padH: 0, padTop: dp(4), padBottom: 0)
            p.sectionLabel("区块高度", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: dp(10), padBottom: 0)
            p.sectionLabel(s.value("区块高度", "55544467"), size: dp(17),
                           color: MockInk.title, padH: 0, padTop: dp(4), padBottom: 0)
            p.sectionLabel("交易时间", size: dp(14), color: MockInk.label,
                           padH: 0, padTop: dp(10), padBottom: 0)
            p.sectionLabel(s.value("交易时间", s.time), size: dp(17),
                           color: MockInk.title, padH: 0, padTop: dp(4), padBottom: 0)
        }

        // 前往查询详细信息
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.sectionLabel("前往查询详细信息", size: dp(15), color: MockInk.label,
                           padH: 0, padTop: 0, padBottom: dp(10))
            p.centeredCells(["tronscan", "TOKENVIEW", "OKLink"],
                            height: dp(28), size: dp(14), padV: dp(6))
        }
        b.gap(dp(20))
    }
}
