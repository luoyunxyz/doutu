import UIKit

/// 「模拟 → 平安模拟」的子页版式（入口 8）。
///
/// 平安的子页：`明细转出(旧) / 明细转出(新) / 明细转入(新) / 明细转出(新) / 转出(新)`，
/// 0 号是「转账交易详情」：深色金额卡 + 三段进度轴 + 明细行 + 底部两个按钮。
/// 颜色（深蓝卡 / 橙色进度与按钮）取自安卓设备截图。
enum MockPingan {

    /// 金额卡底色（截图取样）
    static let navy = UIColor(red: 0x1c / 255.0, green: 0x21 / 255.0,
                              blue: 0x3a / 255.0, alpha: 1)
    /// 进度点与「微信通知收款人」的橙
    static let orange = UIColor(red: 0xf0 / 255.0, green: 0x5a / 255.0,
                                blue: 0x28 / 255.0, alpha: 1)
    /// 卡内次要文字
    static let dimText = UIColor(white: 1, alpha: 0.62)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 五个子页共用「转账交易详情」版式，转入取正
        let incoming = state.sub == 2 || state.sub == 4
        transferDetail(b, state, showsTopTitle: showsTopTitle, incoming: incoming)
        return b.result
    }

    // MARK: - 子页 0：转账交易详情

    private static func transferDetail(_ b: MockBuilder, _ s: MockState,
                                       showsTopTitle: Bool, incoming: Bool) {
        if showsTopTitle { b.titleBar("转账交易详情") }

        // 深色金额卡
        b.panel(gapTop: dp(12), margin: dp(16), pad: dp(18), bg: navy) { p in
            p.sectionLabel("交易类型：转账", size: dp(13), color: dimText,
                           padH: 0, padTop: 0, padBottom: 0)
            p.sectionLabel(s.value("金额", incoming ? "+ ¥ 100.00" : "- ¥ 100.00"),
                           size: dp(34), color: .white,
                           padH: 0, padTop: dp(12), padBottom: 0)
            p.sectionLabel("手续费：0.00", size: dp(13), color: dimText,
                           padH: 0, padTop: dp(12), padBottom: 0)
        }

        // 三段进度（点 + 文字）
        b.gap(dp(14))
        b.centeredCells(["转账申请提交", "人行处理中", "转账成功"],
                        height: dp(26), size: dp(14), padV: dp(6))
        b.paragraph("\(s.time)                                     \(s.time)",
                    size: dp(12), color: MockInk.label,
                    padH: dp(16), padTop: 0, padBottom: dp(10))

        // 说明
        b.paragraph("我行已转出，如未到账，可拨打平安银行客服95511，并提供指令号进行咨询。",
                    size: dp(14), color: MockInk.value,
                    padH: dp(16), padTop: dp(6), padBottom: dp(10))

        // 明细
        b.row("收款人", s.value("收款人", "胡灿"))
        b.row("收款银行", s.value("收款银行", "工商银行"))
        b.row("", s.value("收款账号", "6222 **** **** 7778"))
        b.row("付款人", s.value("付款人", "李哈哈"))
        b.row("付款银行", s.value("付款银行", "平安银行(7982)"))
        b.row("留言", s.value("留言", "转账"))
        b.row("指令号", s.value("指令号", "7492749274924228"))

        b.chevronRow("查看和胡灿的转账记录", size: dp(15), color: MockInk.title,
                     padH: dp(16), padV: dp(14))
        b.twoButtons("再转一笔", "微信通知收款人")
    }
}
