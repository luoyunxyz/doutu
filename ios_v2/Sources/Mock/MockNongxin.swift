import UIKit

/// 「模拟 → 农信」的子页版式（宫格索引 28）。
///
/// 5 个子页：`恩施回单 / 广东回单 / 广东转出 / 恩施转出 / 山东支出`。
/// 0 号是「电子回单详情」：蓝头、白底十来行明细、温馨提示、底部二维码。
/// 设备页面还盖了一枚红色「电子回单专用章」，暂未画。
enum MockNongxin {

    /// 顶栏蓝
    static let blue = UIColor(red: 0x1a / 255.0, green: 0x73 / 255.0,
                              blue: 0xe8 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 五个子页共用「电子回单详情」版式，标题取子页名
        receipt(b, state, showsTopTitle: showsTopTitle)
        return b.result
    }

    private static func receipt(_ b: MockBuilder, _ s: MockState, showsTopTitle: Bool) {
        let names = MockModule.nongxin.subs
        if showsTopTitle {
            b.titleBar("电子回单详情")
        }
        b.coloredHeaderBar(names.indices.contains(s.sub) ? names[s.sub] : "电子回单详情",
                           bg: blue, fg: .white)

        // 行距按安卓实测（nx_sub0）：14sp 字 + 上下 6dp，行距 86px
        let rs = dp(14)
        let rp = dp(6)
        b.row("手机银行电子回单", "", size: rs, padV: rp)
        b.hairlineFull()
        b.row("电子回单编号", s.value("电子回单编号", "843471427"), size: rs, padV: rp)
        b.row("付款人户名", s.value("付款人户名", "张能都"), size: rs, padV: rp)
        b.row("付款人账号", s.value("付款人账号", "6230 5505 7665 7776"), size: rs, padV: rp)
        b.row("收款人户名", s.value("收款人户名", "谢歌"), size: rs, padV: rp)
        b.row("收款人账号", s.value("收款人账号", "6230 5224 4003 3654 947"), size: rs, padV: rp)
        b.row("收款人开户行", s.value("收款人开户行", "中国农业银行"), size: rs, padV: rp)
        b.row("交易时间", s.value("交易时间", s.time), size: rs, padV: rp)
        b.row("转账汇款金额", s.value("转账汇款金额", "¥ 0.00"), size: rs, padV: rp)
        b.row("手续费", s.value("手续费", "0.00"), size: rs, padV: rp)
        b.row("付款用途", s.value("付款用途", "往来款"), size: rs, padV: rp)
        b.row("附言", s.value("附言", ""), size: rs, padV: rp)

        b.paragraph("温馨提示：\n1.本电子回单是非正式的记账凭证，不作为收款方的发货依据。\n"
                    + "2.可通过手机银行电子回单验证功能，输入电子回单号，收款人姓名验证，电子回单。",
                    size: dp(15), color: MockInk.label,
                    padH: dp(16), padTop: dp(14), padBottom: dp(14))
        b.gap(dp(20))
    }
}
