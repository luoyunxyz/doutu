import UIKit

/// 「模拟 → 数字钱包」的子页版式（入口 16）。
///
/// 子页很多（设备抽屉里按银行分）：建设转出1/建设转出2/建设转入/建设存入/建设转出/
/// 招商结果/招商转出/工商转出/工商转出2/农业转出/兴业转出/邮政转出 …
/// 0 号是「转钱成功」：✕ 关闭、右上「继续转钱」、绿色描边对勾、
/// 转钱成功、金额、收款方与卡号、完成按钮、通知收款人。
enum MockDigitalWallet {

    /// 主题绿（截图取样）
    static let green = UIColor(red: 0x4c / 255.0, green: 0xaf / 255.0,
                               blue: 0x50 / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 整页纯白（安卓实测 sz_sub0 底色 #ffffff）
        b.autoBoard = nil
        // 12 个子页共用「转钱成功」版式：银行从子页名取，转入/存入取正
        transferred(b, state, showsTopTitle: showsTopTitle)
        return b.result
    }

    /// 子页名 → (银行, 是否转入)
    private static func bank(_ sub: Int) -> (name: String, incoming: Bool) {
        let names = MockModule.shuzi.subs
        guard names.indices.contains(sub) else { return ("建设银行", false) }
        let name = names[sub]
        let incoming = name.contains("转入") || name.contains("存入")
        let banks = ["建设", "招商", "工商", "农业", "兴业", "邮政"]
        let hit = banks.first { name.hasPrefix($0) } ?? "建设"
        return (hit + "银行", incoming)
    }

    private static func transferred(_ b: MockBuilder, _ s: MockState,
                                    showsTopTitle: Bool) {
        if showsTopTitle { b.titleBar("转钱成功") }

        // 顶栏：左 ✕、右「继续转钱」
        b.coloredHeaderBar("", bg: .white, fg: MockInk.title, trailing: "继续转钱")
        b.sectionLabel("✕", size: dp(22), color: MockInk.title,
                       padH: dp(16), padTop: 0, padBottom: 0)

        // 对勾 + 转钱成功（安卓实测：勾直径 110px，标题 18sp）
        b.gap(dp(20))
        b.checkBadge(diameter: dp(110), color: green, gapTop: dp(10), symbolSize: dp(64))
        b.sectionLabel("转钱成功", size: dp(18), color: MockInk.title,
                       padH: 0, padTop: dp(20), padBottom: 0)

        // 金额（实测文字高 74px ≈ 34sp）
        b.sectionLabel(s.value("金额", "¥70.00"), size: dp(34), color: MockInk.black,
                       padH: 0, padTop: dp(60), padBottom: 0)

        // 收款方 + 卡号
        b.sectionLabel(s.value("收款方", bank(s.sub).incoming
                                   ? "收钱从 零花钱 ✏" : "转钱给 零花钱 ✏"),
                       size: dp(17), color: MockInk.title,
                       padH: 0, padTop: dp(34), padBottom: 0)
        b.sectionLabel(s.value("收款卡号", bank(s.sub).name + " 0051****1679"), size: dp(15),
                       color: MockInk.label, padH: 0, padTop: dp(10), padBottom: 0)

        // 完成 / 通知收款人
        b.gap(dp(320))
        b.fullButton("完成", filled: false, color: MockInk.line,
                     height: dp(48), padH: dp(56), gapTop: dp(10), size: dp(17))
        b.sectionLabel("通知收款人", size: dp(15), color: MockInk.title,
                       padH: 0, padTop: dp(16), padBottom: dp(10))
    }
}
