import UIKit

/// 「模拟 → 中信模拟」的子页版式（入口 10）。
///
/// 5 个子页（设备抽屉）：`明细转出1(旧) / 明细转入1(旧) / 明细转出2(旧) /
/// 明细转出(新) / 明细转入(新)`。
/// 0 号是「银行卡转账」成功页：红头、绿勾、七行明细（带冒号）、
/// 「猜您需要」三条入口、「返回首页」红按钮。
enum MockZhongxin {

    /// 中信红（截图取样）
    static let red = UIColor(red: 0xcc / 255.0, green: 0x2f / 255.0,
                             blue: 0x2a / 255.0, alpha: 1)
    /// 对勾绿
    static let green = UIColor(red: 0x5c / 255.0, green: 0xc0 / 255.0,
                               blue: 0x6b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 五个子页共用「电子回单/转账结果」版式：转入取正、标题按子页名
        let incoming = state.sub == 1 || state.sub == 4
        bankTransfer(b, state, showsTopTitle: showsTopTitle, incoming: incoming)
        return b.result
    }

    private static func bankTransfer(_ b: MockBuilder, _ s: MockState,
                                     showsTopTitle: Bool, incoming: Bool) {
        if showsTopTitle { b.titleBar(incoming ? "明细转入" : "银行卡转账") }

        // 红色顶栏
        b.coloredHeaderBar("银行卡转账", bg: red, fg: .white)

        // 绿勾 + 交易成功
        b.gap(dp(18))
        b.checkBadge(diameter: dp(72), color: green, gapTop: dp(4), symbolSize: dp(40))
        b.sectionLabel("交易成功", size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(12), padBottom: dp(10))

        // 明细（标签带冒号，与截图一致）
        b.fill(dp(8), MockInk.page)
        b.row("收款人：", s.value("收款人", "林上杰"))
        b.row("收款账号：", s.value("收款账号", "6226 ** ** ** ** ** 9990"))
        b.row("收款银行：", s.value("收款银行", "中国工商银行"))
        b.row("到账金额：", s.value("到账金额", incoming ? "100.00" : "100.00"))
        b.row("手续费：", s.value("手续费", "0.00"))
        b.row("转账方式：", s.value("转账方式", "实时到账"))
        b.row("备注：", s.value("备注", "手机银行转账"))

        // 猜您需要
        b.fill(dp(10), MockInk.page)
        b.fill(dp(36), UIColor(red: 0xfd / 255.0, green: 0xf6 / 255.0,
                               blue: 0xdc / 255.0, alpha: 1))
        b.sectionLabel("猜您需要", size: dp(15), color: MockInk.title,
                       padH: dp(16), padTop: dp(8), padBottom: dp(8))
        for title in ["继续转账", "交易明细", "查询余额"] {
            b.chevronRow(title, size: dp(16), color: MockInk.title,
                         padH: dp(16), padV: dp(18))
        }

        // 返回首页
        b.fill(dp(10), MockInk.page)
        b.fullButton("返回首页", filled: true, color: red,
                     height: dp(46), padH: dp(16), gapTop: dp(14), size: dp(17))

        // 猜你喜欢（分割标题）
        b.sectionLabel("猜你喜欢", size: dp(14), color: MockInk.label,
                       padH: 0, padTop: dp(18), padBottom: 0)
        b.gap(dp(20))
    }
}
