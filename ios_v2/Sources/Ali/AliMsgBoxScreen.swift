import UIKit

/// 「支付宝 → 消息盒子」—— `AliMsgBoxActivity`（92 行）的移植。
///
/// 结构很简单：56dp 灰条（`‹` + 居中「消息盒子」17sp）+ 44dp 白色双页签
/// （服务消息蓝色选中 / 支付消息灰色）+ 剩余空间居中的「暂无消息」灰字。
/// 两个页签都没有内容 —— 与 Android 一致。
final class AliMsgBoxScreen: Screen {
    let canvasSize = CGSize(width: 1080, height: 2388)
    let selfScrolls = false
    func maxScroll(_ state: ScreenState) -> CGFloat { 0 }

    static let W: CGFloat = 1080
    static let H: CGFloat = 2388

    static let barH = dp(56)
    static let tabH = dp(44)

    private let barBg = Draw.color(0xffededed)
    private let pageBg = Draw.color(0xfff5f5f5)
    private let ink = Draw.color(0xff333333)
    private let active = Draw.color(0xff3e7af6)
    private let normal = Draw.color(0xff666666)
    private let emptyInk = Draw.color(0xff999999)

    final class State: ScreenState {
        var tab = 0
    }

    func draw(_ d: Draw, state: ScreenState) {
        let s = (state as? State) ?? State()
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.H), pageBg)

        // 顶栏
        d.rect(CGRect(x: 0, y: 0, width: Self.W, height: Self.barH), barBg)
        d.centerText("‹", cx: dp(22), baseline: Self.barH / 2 + dp(9), size: dp(26), ink)
        let titleSize = dp(17)
        d.centerText("消息盒子", cx: Self.W / 2,
                     baseline: Self.barH / 2 + Draw.font(size: titleSize).ascender / 2 - titleSize * 0.06,
                     size: titleSize, ink)

        // 两个页签
        let tabTop = Self.barH
        d.rect(CGRect(x: 0, y: tabTop, width: Self.W, height: Self.tabH), .white)
        let tabs = ["服务消息", "支付消息"]
        let size = dp(15)
        for (index, text) in tabs.enumerated() {
            let selected = index == s.tab
            let cx = Self.W / 4 + Self.W / 2 * CGFloat(index)
            d.centerText(text, cx: cx,
                         baseline: tabTop + Self.tabH / 2 + Draw.font(size: size).ascender / 2 - size * 0.06,
                         size: size, selected ? active : normal, bold: selected)
        }

        // 暂无消息（垂直居中于剩余空间）
        let restTop = tabTop + Self.tabH
        let emptySize = dp(14)
        d.centerText("暂无消息", cx: Self.W / 2,
                     baseline: restTop + (Self.H - restTop) / 2 + Draw.font(size: emptySize).ascender / 2 - emptySize * 0.06,
                     size: emptySize, emptyInk)
    }

    func hitTest(_ state: ScreenState, x: CGFloat, y: CGFloat) -> ActionToken {
        if y < Self.barH {
            return x < dp(44) ? "ali:back" : "noop"
        }
        if y < Self.barH + Self.tabH {
            return x < Self.W / 2 ? "ali:msgbox:tab:0" : "ali:msgbox:tab:1"
        }
        return "noop"
    }
}
