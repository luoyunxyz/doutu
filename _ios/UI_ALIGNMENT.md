# 模拟页 UI 逐页对齐记录

> 目标：31 个入口 / 195 个子页，**页面生成图**与安卓设备逐页一致（字号、行距、底色、样式）。
> 判定依据：`python _ios/measure_page.py <png> [x0] [x1]` 量出的**文字带高度与相邻间距**。

## 1. 换算模型（第 5 轮确定）

```
1dp = 1sp = 3px          （设备 480dpi，density 3；画布宽 1080px = 360dp）
行高 = 字号 × 1.2         （安卓 TextView 的行为；**不是** PingFang 的 1.4）
行距 = 字号 × 1.2 + 上下内边距 × 2
文字带高 ≈ 字号 × 3 × 0.88（中文）/ × 0.72（数字）
```

**不要用 `UIFont.lineHeight`** —— 它取的是 PingFang 的 1.4 倍，会让每行偏高 4~5px，
十行就错位近半屏。`MockBuilder.lineHeight` 已改成 `size * 1.2`。

## 2. 已量页面（`_ios/measurements.json` + `node _ios/v2/check-pitch.mjs` 守）

| 页面 | 行距(px) | 字号 + 内边距 |
|---|---|---|
| 农业·明细查询 | 69 | 13 + 4 |
| 流水·明细查询 | 69 | 13 + 4 |
| 农业·明细 | 83 | 15 + 5 |
| 回单 | 66 | 12 + 4 |
| 农信 | 86 | 14 + 6 |
| qq | 90 | 14 + 6.5 |
| 话费·订单 | 124（入口 149） | 13 + 13（入口 17 + 14） |
| 欧易·提币 | 84 | 12 + 7 |
| 民生 | 91 | 14 + 6.5 |
| 汇旺 | 84 | 14 + 5.5 |
| 支付宝·账单详情 | 97 | 14 + 8 |
| 支付宝·收支详情 | 126 | 14 + 12.5 |
| 微信·账单（赞赏码/商户） | 89 | 14 + 6.5 |
| 微信·扫码账单 | 76 | 14 + 4.5 |
| 微信·交易详情 | 87 | 14 + 6 |

标题 **18sp**、金额 **32sp**（数字配 0.72 比例）、状态块 15sp。

## 3. 已量底色

| 位置 | 色值 | 常量 |
|---|---|---|
| 明细查询内容下方 | `#eeeeee` | `MockInk.board` |
| 话费入口分隔带 | `#f5f5f5` | `MockInk.pageAlt` |
| qq 入口区 | `#f0f1f2` | `MockInk.entryGrey` |
| 聊天整页 | `#ededed` | `MockChat.chatBg` |
| TP 整页 | `#f1f2f6` | `MockTPWallet` 内局部常量 |
| **数字钱包 / 农信 / 回单 / 欧易 / 微信 / 支付宝** | **纯白** | `autoBoard = nil` |

底板机制：`MockBuilder.autoBoard`（默认 `nil` = 不铺）→ 内容结束后自动铺到画布底；
需要灰底的页面自己声明。**不要给全局默认值**，会污染白底页面（第 9 轮的教训）。

## 4. 待量页面（还没取过数值的）

按优先级：

1. **6 家银行**（农业/工商/中国/邮政/招商/光大）× 各自 3~12 子页
2. **建设 / 中信 / 浦发 / 平安 / 交通 / 兴业** 各子页（多数已看过截图，未量）
3. **云闪付** 11 子页（`ysf_sub0` 量过：非行列表的结果页）
4. **回单其余 11 子页**、**话费其余 11 子页**、**农信其余 4 子页**
5. **京东**（`jd_sub0` 量过：卡片 + 九宫格）、**抖音**（`dy_sub0`）、**印度**（`yd_sub0`）
6. **短信**（`dx_sub0`，气泡式）、**c币**（`cbi_sub0`）、**数字钱包其余 11**

## 5. 每页的作业流程

```powershell
# 1) 安卓：进子页 → 生成图片 → 抓图
python _ios\android_drive.py restart
python _ios\android_drive.py tapxy 523 2278           # 模拟 tab
python _ios\android_drive.py tapxy <宫格坐标>          # 例：农业 (207,959)
python _ios\android_drive.py tapxy 872 632            # 抽屉第 1 项
python _ios\android_drive.py tapxy 540 850            # 生成图片
python _ios\android_drive.py shot page_name

# 2) 量数值（不显示图片，只打印文字带）
python _ios\measure_page.py android/page_name.png 40 300
python _ios\measure_page.py android/page_name.png 380 760    # 金额列

# 3) 改 Swift：row(size:padV:)、titleSize、autoBoard、MockInk 常量
# 4) 把数值写进 _ios/measurements.json，跑 node _ios/v2/check-pitch.mjs
# 5) 逐页提交
```

宫格坐标（1080×2388）：列 x = 207 / 540 / 873；行 y = 959、1097、1245、1383、1521、
1696、1830、1965、2100；**后 4 格需先 `swipe 1500 900`**，然后 话费(193,1380)、
农信(523,1380)、qq(847,1380)、京东(193,1520)。

## 6. 尚未完成的验证

**iOS 真机截图一次都没取到**（`_ios/live_shots.py` 三次超时，App 未运行）。
所以以上全部是「按安卓实测值反解 iOS 参数」，没有渲染层回归。

一旦 App 在运行，做这一件事即可判定整条链路：

```powershell
python _ios\live_shots.py mock:home:0 mock:sub:1 mock:gen      # 农业·明细查询
python _ios\measure_page.py live/mock_gen.png 40 300
```

若 iOS 量出的行距 ≈ 71px（算得 70.8 / 安卓实测 69），模型成立 → 按 §4 清单批量推进；
若偏差 > 3px，先查 `Draw.text` 的基线算法与字体度量，**不要继续改页面参数**。
