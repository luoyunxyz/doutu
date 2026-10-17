# iOS 1:1 移植进度表

> 自动生成于 2026-10-17 08:46:15

## 总览

| 指标 | 数值 |
|---|---|
| 页面（Screen 类）总数 | 27 |
| 绘制代码总行数 | 16147 |
| 已移植 | 27 屏 / 16147 行（100.0%） |
| 承载类（Activity） | 38 个（iOS 侧统一由 ScreenHostController + Router 承担，不需要逐个搬） |

## 各模块体量

| 模块 | 绘制行数 |
|---|---|
| features | 14106 |
| (shell) | 2041 |

## 逐屏清单（按体量降序，建议从小的做起）

| 状态 | 页面 | 行数 | 对应 Android 源码 |
|---|---|---|---|
| ✅ 已移植 | CollectMoneyScreen | 628 | `features/pay/ui/CollectMoneyScreen.java` |
| ✅ 已移植 | PayCodeScreen | 436 | `features/pay/ui/PayCodeScreen.java` |
| ✅ 已移植 | ScanOverlayScreen | 216 | `features/scan/ui/ScanOverlayScreen.java` |
| ✅ 已移植 | TravelScreen | 606 | `features/travel/ui/TravelScreen.java` |
| ⬜ 待移植 | ConversationScreen | 398 | `features/wei/ui/ConversationScreen.java` |
| ⬜ 待移植 | WeiHomeScreen | 790 | `features/wei/ui/WeiHomeScreen.java` |
| ⬜ 待移植 | AlipayHomeScreen | 386 | `features/zhi/ui/AlipayHomeScreen.java` |
| ✅ 已移植 | AssetScreen | 857 | `features/zhi/ui/AssetScreen.java` |
| ✅ 已移植 | BalanceScreen | 383 | `features/zhi/ui/BalanceScreen.java` |
| ✅ 已移植 | BillAnalyticsScreen | 1703 | `features/zhi/ui/BillAnalyticsScreen.java` |
| ✅ 已移植 | BillDetailScreen | 375 | `features/zhi/ui/BillDetailScreen.java` |
| ✅ 已移植 | BillScreen | 365 | `features/zhi/ui/BillScreen.java` |
| ✅ 已移植 | GrowthScreen | 1057 | `features/zhi/ui/GrowthScreen.java` |
| ✅ 已移植 | HuabeiScreen | 295 | `features/zhi/ui/HuabeiScreen.java` |
| ✅ 已移植 | LicaiScreen | 842 | `features/zhi/ui/LicaiScreen.java` |
| ✅ 已移植 | MeScreen | 375 | `features/zhi/ui/MeScreen.java` |
| ✅ 已移植 | MessageScreen | 472 | `features/zhi/ui/MessageScreen.java` |
| ✅ 已移植 | PayMsgScreen | 457 | `features/zhi/ui/PayMsgScreen.java` |
| ✅ 已移植 | PaySuccessScreen | 501 | `features/zhi/ui/PaySuccessScreen.java` |
| ✅ 已移植 | ProfileScreen | 222 | `features/zhi/ui/ProfileScreen.java` |
| ✅ 已移植 | ReceiptDetailScreen | 380 | `features/zhi/ui/ReceiptDetailScreen.java` |
| ✅ 已移植 | ReceiptScreen | 538 | `features/zhi/ui/ReceiptScreen.java` |
| ✅ 已移植 | SwitchAccountScreen | 215 | `features/zhi/ui/SwitchAccountScreen.java` |
| ✅ 已移植 | WithdrawDoneScreen | 292 | `features/zhi/ui/WithdrawDoneScreen.java` |
| ✅ 已移植 | WithdrawScreen | 884 | `features/zhi/ui/WithdrawScreen.java` |
| ✅ 已移植 | XiaohebaoScreen | 536 | `features/zhi/ui/XiaohebaoScreen.java` |
| ✅ 已移植 | YuebaoScreen | 339 | `features/zhi/ui/YuebaoScreen.java` |
| ✅ 已移植 | ZhiHomeScreen | 589 | `features/zhi/ui/ZhiHomeScreen.java` |
| ✅ 已移植 | ZhimaCreditScreen | 543 | `features/zhima/ui/ZhimaCreditScreen.java` |
| ✅ 已移植 | HomeShell | 2041 | `MainActivity.java` |

## 移植一屏的固定步骤

1. 读 `XxxScreen.java`，把 W/H、几何常量、调色板原样抄成 Swift 常量（加注释标 Java 行号）
2. `draw(_:state:)` 逐语句对应 Java 的 `draw(Canvas, State)`，坐标不改
3. `hitTest` 的分支顺序与区间照抄（顺序影响交互）
4. 位图资源跑 `node _ios/sync-assets.mjs <资源名…>` 同步进 Assets.xcassets
5. 真机截图与 Android 同屏截图比对，差异修到像素级一致
6. 把页面名加进本脚本的 `DONE` 数组，重跑生成本表

## 为什么必须逐语句照抄

Android 端的界面不是标准控件，而是**在固定参考画布上用 Canvas 手绘**的：
坐标是硬编码的（如 `textCentered(c, "我的", 640, 150, 52, INK, true)`），
字号/颜色/边距全部按参考图逐段量过。
要把它们搬到 iOS 且保证 1:1，唯一可靠的办法是让绘制语句一一对应：
`drawText → text(_:x:baseline:size:color:)`、`drawRoundRect → round(_:radius:color:)`、
`drawBitmap → art(_:_:)`，同坐标、同字号、同颜色。
换成 HTML/CSS 会因为字体度量不同而必然出现偏差（这也是最终没有采用 WebView 做界面的原因）。
