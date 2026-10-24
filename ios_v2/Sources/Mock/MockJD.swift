import UIKit

/// 「模拟 → 京东」的子页版式（宫格索引 30）。
///
/// 只有 1 个子页：`支付成功`。
/// 页面：‹ + 搜索 + 回首页、红色 ✓ 支付成功、京东支付金额行、
/// 「下单抽抽乐」九宫格、三页签推荐位（插画与商品图未同步，用文字块占位）。
enum MockJD {

    /// 京东红
    static let red = UIColor(red: 0xe1 / 255.0, green: 0x25 / 255.0,
                             blue: 0x2b / 255.0, alpha: 1)

    static func page(state: MockState, metrics: MockMetrics,
                     width: CGFloat, showsTopTitle: Bool) -> MockPage {
        let b = MockBuilder(width: width, metrics: metrics)
        _ = state
        if showsTopTitle { b.titleBar("支付成功") }
        paid(b)
        return b.result
    }

    private static func paid(_ b: MockBuilder) {
        // 顶栏：返回 + 搜索 + 回首页
        b.sectionLabel("‹            ⌕        回首页", size: dp(18),
                       color: MockInk.title, padH: dp(16), padTop: dp(14), padBottom: 0)

        // 支付成功
        b.sectionLabel("✓ 支付成功", size: dp(24), color: red,
                       padH: dp(16), padTop: dp(20), padBottom: dp(10))
        b.sectionLabel("京东支付 ¥9236.98，共优惠 ¥0.02        查看订单 ›",
                       size: dp(17), color: MockInk.title,
                       padH: dp(16), padTop: 0, padBottom: dp(14))

        // 下单抽抽乐
        b.panel(gapTop: dp(6), margin: dp(12), pad: dp(14), bg: .white) { p in
            p.sectionLabel("下单抽抽乐                              活动规则",
                           size: dp(17), color: MockInk.title,
                           padH: 0, padTop: 0, padBottom: dp(8))
            p.centeredCells(["5京豆", "电话卡", "满8.9减8"],
                            height: dp(56), size: dp(15), padV: dp(4))
            p.centeredCells(["领129元购物金", "立即抽奖GO", "PLUS联名卡"],
                            height: dp(56), size: dp(15), padV: dp(4))
            p.centeredCells(["50g水滴", "谢谢参与", "拍拍回收券"],
                            height: dp(56), size: dp(15), padV: dp(4))
        }

        // 三页签 + 推荐位
        b.gap(dp(12))
        b.centeredCells(["专属推荐", "京东好价", "视频种草"],
                        height: dp(34), size: dp(16), padV: dp(8))
        b.gap(dp(10))
        b.sectionLabel("【京东到家 房屋漏水维修】   ¥5.00\n\n"
                       + "自营 网易严选【小刘鸭联名】  ¥13.90",
                       size: dp(15), color: MockInk.title,
                       padH: dp(16), padTop: dp(10), padBottom: dp(20))
    }
}
