import UIKit

/// 「模拟 → 回单」的子页版式（宫格索引 20）。
///
/// 12 个子页（设备抽屉）：`工商回单 / 建设回单 / 农业回单 / 邮政回单 / 邮政回单（新）/
/// 光大回单 / 中国回单 / 支付宝1 / 支付宝2 / 银座 / 河北 / 中信`。
/// 0 号是工行的「境内汇款电子回单」：银行标题行、收款/付款/回单三段、
/// 附言、隐藏卡号勾选、模板缩略图、四个导出入口、页脚。
/// 设备页面上有整页水印，暂不画。
enum MockReceipt {

    /// 标题行与金额的红
    static let red = UIColor(red: 0xc8 / 255.0, green: 0x2f / 255.0,
                             blue: 0x2a / 255.0, alpha: 1)
    /// 表格标签灰
    static let labelGrey = UIColor(red: 0x55 / 255.0, green: 0x5b / 255.0,
                                   blue: 0x66 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 12 个子页共用「电子回单」版式；银行名从子页名推出来（「建设回单」→ 建设银行）
        icbcReceipt(b, state, showsTopTitle: showsTopTitle)
        return b.result
    }

    /// 由子页名推银行名：去掉「回单」与括号后缀
    private static func bankName(_ sub: Int) -> String {
        let names = MockModule.huidan.subs
        guard names.indices.contains(sub) else { return "中国工商银行" }
        var base = names[sub]
        for suffix in ["（新）", "回单"] where base.hasSuffix(suffix) {
            base = String(base.dropLast(suffix.count))
        }
        if base == "支付宝1" || base == "支付宝2" { return "支付宝" }
        if base.hasSuffix("银行") { return base }
        return base + "银行"
    }

    private static func icbcReceipt(_ b: MockBuilder, _ s: MockState,
                                    showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("通知收款人") }

        b.coloredHeaderBar("通知收款人", bg: .white, fg: MockInk.title, trailing: "⋯")

        // 银行标题行
        b.sectionLabel(bankName(s.sub), size: dp(17), color: MockInk.title,
                       padH: dp(16), padTop: dp(10), padBottom: 0)
        b.sectionLabel("境内汇款电子回单", size: dp(14), color: labelGrey,
                       padH: dp(16), padTop: dp(2), padBottom: dp(8))
        b.hairlineFull()

        // 行距按安卓实测（hd_sub0）：12sp 字 + 上下 4dp，行距 66px
        let rs = dp(12)
        let rp = dp(4)

        // 收款段
        b.row("收款户名", s.value("收款户名", "张三"), size: rs, padV: rp)
        b.row("收款卡号", s.value("收款卡号", "6229****2199"), size: rs, padV: rp)
        b.row("收款银行", s.value("收款银行", "兴业银行"), size: rs, padV: rp)
        b.row("收款金额", s.value("收款金额", "0.10 元(人民币)"), size: rs, padV: rp)
        b.row("手续费", s.value("手续费", "免费"), size: rs, padV: rp)
        b.row("合计", s.value("合计", "零角零分"), size: rs, padV: rp)
        b.hairlineFull()

        // 付款段
        b.row("付款户名", s.value("付款户名", "李四"), size: rs, padV: rp)
        b.row("付款卡号", s.value("付款卡号", "6222****8198"), size: rs, padV: rp)
        b.row("付款银行", s.value("付款银行", "中国工商银行"), size: rs, padV: rp)
        b.hairlineFull()

        // 回单段
        b.row("回单编号", s.value("回单编号", "ZZHK-0004-1234-6845-0016"), size: rs, padV: rp)
        b.row("指令序号", s.value("指令序号", "374878806332417757147418227"), size: rs, padV: rp)
        b.row("交易时间", s.value("交易时间", "2026/10/24 19:38"), size: rs, padV: rp)
        b.row("附言", s.value("附言", "--"), size: rs, padV: rp)

        // 隐藏卡号 + 模板缩略图 + 导出
        b.gap(dp(10))
        b.sectionLabel("☐  隐藏收付款卡号、手机号、e-mail", size: dp(17),
                       color: MockInk.title, padH: dp(16), padTop: dp(6), padBottom: dp(10))
        b.centeredCells(["电子回单", "招财进宝", "蟾宫折桂", "恭贺新春"],
                        height: dp(92), size: dp(14), padV: dp(4))
        b.centeredCells(["发送到融e联", "发送到微信", "发送到邮箱", "下载到相册"],
                        height: dp(56), size: dp(14), padV: dp(10))
        b.sectionLabel("来自 中国工商银行手机银行", size: dp(14), color: labelGrey,
                       padH: dp(16), padTop: dp(10), padBottom: dp(16))
        _ = red
    }
}
