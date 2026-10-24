import UIKit

/// 「模拟 → 云闪付」的子页版式（入口 25，宫格索引 24）。
///
/// 11 个子页（设备抽屉）：`转账 / 转账2 / 转出1 / 转出2 / 转出3 / 转出4 /
/// 支付宝转入 / 转出2 / 支付助手 / 回单 / 通知收款人`。
/// 0 号是「转账成功」：绿勾 + 转账成功 + 完成、金额、0 手续费说明、两条蓝链接、
/// 「通知收款人」卡（电子回单 / 全家福 两格）、抽奖横幅、两张活动卡。
/// 设备上这些卡片都带插画，素材还没同步，这里用纯文本 + 色块还原结构。
enum MockUnionPay {

    /// 云闪付绿（截图取样）
    static let green = UIColor(red: 0x2f / 255.0, green: 0xb3 / 255.0,
                               blue: 0x4c / 255.0, alpha: 1)
    /// 链接蓝
    static let blue = UIColor(red: 0x1a / 255.0, green: 0x5f / 255.0,
                              blue: 0xb8 / 255.0, alpha: 1)
    /// 通知收款人卡的粉底
    static let pinkCard = UIColor(red: 0xfd / 255.0, green: 0xee / 255.0,
                                  blue: 0xee / 255.0, alpha: 1)
    /// 活动卡的浅蓝
    static let skyCard = UIColor(red: 0xe8 / 255.0, green: 0xf2 / 255.0,
                                 blue: 0xfd / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        switch state.sub {
        case 0:
            transferred(b, state, showsTopTitle: showsTopTitle)
        default:
            return MockComposer.placeholder(module: .yunshanfu, metrics: metrics, width: width)
        }
        return b.result
    }

    private static func transferred(_ b: MockBuilder, _ s: MockState,
                                    showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转账成功") }

        // 绿勾 + 转账成功 + 完成
        b.sectionLabel("✓  转账成功                              完成",
                       size: dp(18), color: green, padH: dp(16), padTop: dp(14), padBottom: 0)

        // 金额 + 说明
        b.sectionLabel(s.value("金额", "¥ 200.00"), size: dp(44),
                       color: UIColor(red: 0x33 / 255.0, green: 0x33 / 255.0,
                                      blue: 0x33 / 255.0, alpha: 1),
                       padH: 0, padTop: dp(24), padBottom: 0)
        b.sectionLabel("0手续费，到账以银行处理结果为准", size: dp(15),
                       color: MockInk.label, padH: 0, padTop: dp(18), padBottom: 0)

        // 两条链接
        b.centeredCells(["转账详情", "继续转账"], height: dp(28), size: dp(16), padV: dp(20))

        // 通知收款人卡
        b.panel(gapTop: dp(10), margin: dp(12), pad: dp(14), bg: pinkCard) { p in
            p.sectionLabel("通知收款人", size: dp(18), color: .white,
                           padH: 0, padTop: 0, padBottom: dp(10))
            p.centeredCells(["电子回单", "全家福"], height: dp(40), size: dp(15), padV: dp(6))
        }

        // 抽奖横幅
        b.panel(gapTop: dp(14), margin: dp(12), pad: dp(14), bg: pinkCard) { p in
            p.sectionLabel("支付即可抽奖 抽奖必中红包", size: dp(17),
                           color: MockInk.title, padH: 0, padTop: 0, padBottom: dp(8))
            p.sectionLabel("去查看 ▸", size: dp(15), color: UIColor(red: 0xe8 / 255.0,
                                                                   green: 0x6a / 255.0,
                                                                   blue: 0x2a / 255.0, alpha: 1),
                           padH: 0, padTop: 0, padBottom: dp(4))
        }

        // 两张活动卡
        b.fill(dp(10), .white)
        b.panel(gapTop: dp(14), margin: dp(12), pad: dp(14), bg: skyCard) { p in
            p.sectionLabel("云小圈百万积点  限时发放", size: dp(16),
                           color: MockInk.title, padH: 0, padTop: 0, padBottom: dp(8))
            p.sectionLabel("去查看 ▸", size: dp(15), color: UIColor(red: 0xe8 / 255.0,
                                                                   green: 0x6a / 255.0,
                                                                   blue: 0x2a / 255.0, alpha: 1),
                           padH: 0, padTop: 0, padBottom: 0)
        }
        b.panel(gapTop: dp(12), margin: dp(12), pad: dp(14), bg: skyCard) { p in
            p.sectionLabel("全民出行季  千万补贴 人人可领", size: dp(16),
                           color: MockInk.title, padH: 0, padTop: 0, padBottom: dp(8))
            p.sectionLabel("去申请 ▸", size: dp(15), color: UIColor(red: 0xe8 / 255.0,
                                                                   green: 0x6a / 255.0,
                                                                   blue: 0x2a / 255.0, alpha: 1),
                           padH: 0, padTop: 0, padBottom: 0)
        }
        b.gap(dp(20))
        _ = blue
    }
}
