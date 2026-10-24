import UIKit

/// 「模拟 → 聊天」的子页版式（宫格索引 22）。
///
/// 2 个子页：`微信 / 微信（群聊）`。
/// 版式是微信单聊/群聊的空对话页：`‹` + 会话名 + `⋯`、灰底消息区、
/// 底部输入条（语音 / 输入框 / 表情 / 加号）。
enum MockChat {

    /// 消息区灰底
    static let chatBg = UIColor(red: 0xed / 255.0, green: 0xed / 255.0,
                                blue: 0xed / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        // 聊天页整页就是 #ededed（安卓实测 lt_sub0），所以底板直接用这个灰
        b.autoBoard = chatBg
        b.pageBackground = chatBg
        let group = state.sub == 1
        if showsTopTitle { b.titleBar(group ? "微信（群聊）" : "微信") }
        thread(b, group: group)
        return b.result
    }

    private static func thread(_ b: MockBuilder, group: Bool) {
        // 顶栏
        b.sectionLabel(group ? "‹   微信（群聊）                            ⋯"
                             : "‹   图册666                              ⋯",
                       size: dp(19), color: MockInk.title,
                       padH: dp(16), padTop: dp(12), padBottom: dp(12))

        // 消息区
        b.fill(dp(900), chatBg)

        // 底部输入条
        b.sectionLabel("◉   [ 输入… ]                    ☺   ⊕",
                       size: dp(17), color: MockInk.title,
                       padH: dp(16), padTop: dp(16), padBottom: dp(16))
    }
}
