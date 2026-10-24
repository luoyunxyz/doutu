# 模拟页 UI 逐页对齐 · 交接文档

> 目标：**31 个入口 / 195 个子页**的「生成图片」，与安卓设备逐页一致 ——
> 字号、行距、内边距、底色、边框、字重、图标位置，全部要一样。
>
> 判定依据：`python _ios/measure_page.py <png> [x0] [x1]` 量出的**文字带高度**与**相邻间距**。
> 不靠肉眼、不靠猜。

---

## 0. 一句话现状

换算模型已建立并回算验证（15 页误差 ≤2px），**约 15 个子页已按实测值对齐**；
**约 180 个子页还没量过**。iOS 真机截图**一次都没取到**（App 未运行），
所以全部改动尚未经过渲染层回归。

---

## 1. 换算模型（本轮最重要的结论）

```
1dp = 1sp = 3px            设备 480dpi，density 3；画布 1080×2388px = 360×796dp
行高 = 字号 × 1.2           安卓 TextView 的行为
行距 = 字号 × 1.2 + 上下内边距 × 2
文字带高 ≈ 字号 × 3 × 0.88  中文
文字带高 ≈ 字号 × 3 × 0.72  数字（¥ / 金额）
```

**反解公式**（量到行距后求参数）：

```
字号 ≈ 文字带高 / 0.88 / 3        （中文）
内边距 ≈ (行距 - 字号 × 3 × 1.2) / 6
```

⚠️ **绝对不要用 `UIFont.lineHeight`**：PingFang 行高约 1.4 倍，安卓是 1.2 倍。
用错会让每行偏高 4~5px，十行就差近半屏 —— 这是第 5 轮才找到的根因，
`MockBuilder.lineHeight` 已固定返回 `size * 1.2`。

---

## 2. 已量页面（代码已按此对齐）

`_ios/measurements.json` 是机器可读版本，`node _ios/v2/check-pitch.mjs` 守着它们。

| 页面 | 行距 px | 字号 + 内边距 | 代码位置 |
|---|---|---|---|
| 农业·明细查询 | 69 | 13 + 4 | `MockNye.query` |
| 流水·明细查询（7 子页） | 69 | 13 + 4 | `MockLiushui.query` |
| 农业·明细 | 83 | 15 + 5 | `MockNye.statement` |
| 回单（12 子页） | 66 | 12 + 4 | `MockReceipt` |
| 农信（5 子页） | 86 | 14 + 6 | `MockNongxin` |
| qq·交易详情（6 子页） | 90 | 14 + 6.5 | `MockQQ` |
| 话费·订单（12 子页） | 124 / 入口 149 | 13 + 13 / 17 + 14 | `MockHuafei` |
| 欧易·提币（4 子页） | 84 | 12 + 7 | `MockOkx` |
| 民生（3 子页） | 91 | 14 + 6.5 | `MockMinsheng` |
| 汇旺（3 子页） | 84 | 14 + 5.5 | `MockHuiwang` |
| 支付宝·账单详情（子页 6） | 97 | 14 + 8 | `MockAlipay` case 6 |
| 支付宝·收支详情（8–11） | 126 | 14 + 12.5 | `MockAlipay` case 8…11 |
| 微信·账单（8–11） | 89 | 14 + 6.5 | `MockWechat.billDetail` |
| 微信·扫码账单（6、7） | 76 | 14 + 4.5 | 同上，传 `rowPadV` |
| 微信·交易详情（5） | 87 | 14 + 6 | `MockWechat` case 5 |
| 微信·支付空页（4） | — | 标题 15sp | `MockWechat` case 4 |
| 数字钱包（12 子页） | — | 标题 18sp、金额 34sp | `MockDigitalWallet` |

全局：**标题 18sp**（`titleSize`）、**金额 32sp**（`amountSize`）、默认行 **14sp/6dp**。

---

## 3. 底色清单（每个页面都不一样，别套默认值）

| 位置 | 色值 | 常量 |
|---|---|---|
| 明细查询内容下方 | `#eeeeee` | `MockInk.board` |
| 话费入口分隔带 | `#f5f5f5` | `MockInk.pageAlt` |
| qq 入口区 | `#f0f1f2` | `MockInk.entryGrey` |
| 聊天整页 / 微信支付空页 | `#ededed` | `MockChat.chatBg` |
| TP 整页 | `#f1f2f6` | `MockTPWallet` 局部常量 |
| **数字钱包 / 农信 / 回单 / 欧易 / 微信 / 支付宝** | **`#ffffff`** | `autoBoard = nil` |

### 底板机制

```swift
b.autoBoard = MockInk.board   // 内容结束后自动铺到画布底（2388px）
b.autoBoard = nil             // 默认：纯白到底
```

**默认必须是 nil**。第 8 轮我曾把它默认设成 `#eeeeee`，结果会把一大票白底页面
错刷成灰色（第 9 轮撤回）。只有实测为灰的页面自己声明。

---

## 4. 待量清单（剩余约 180 页）

按「一次改一族」的效率排序：

| 模块 | 子页数 | 状态 |
|---|---|---|
| 农业 / 工商 / 中国 / 邮政 / 招商 / 光大 | 5 / 6 / 6 / 8 / 12 / 3 | 早期按 Java 源码移植，只有部分行距确认过 |
| 建设（12） | 12 | 自建版式，**未量** |
| 中信(5) 浦发(4) 平安(5) 交通(10) 兴业(5) | 29 | 有截图，**未量** |
| 云闪付（11） | 11 | `ysf_sub0` 看过多为结果页，**未量** |
| 回单其余 11 | 11 | 同族，行距可复用 66px，但银行名/水印未画 |
| 话费其余 11 | 11 | 同族，已有映射函数 |
| 农信其余 4 | 4 | 同族 86px |
| 京东（1） | 1 | `jd_sub0` 卡片 + 九宫格，未量 |
| 抖音（4） | 4 | `dy_sub0` 单条文字 |
| 印度（4） | 4 | `yd_sub0` 分组（标签 27px / 值 39px） |
| 短信（1） | 1 | `dx_sub0` 气泡式 |
| c币（1） | 1 | `cbi_sub0` 稀疏 |
| 数字钱包其余 11 | 11 | 同族，可用 `bank(sub)` 推导 |
| 微信其余 / 支付宝其余 | ~14 | 版式已建，细节未逐项量 |

---

## 5. 每页作业流程

```powershell
# ① 安卓：进子页 → 生成图片 → 抓图
python _ios\android_drive.py restart
python _ios\android_drive.py tapxy 523 2278        # 「模拟」tab
python _ios\android_drive.py tapxy <宫格坐标>
python _ios\android_drive.py tapxy 872 632         # 抽屉第 1 项（n → 632+(n-1)*130）
python _ios\android_drive.py tapxy 540 850         # 「生成图片」
python _ios\android_drive.py shot page_name

# ② 量数值（只打印数字，不看图）
python _ios\measure_page.py android/page_name.png 40 300      # 左列（标签）
python _ios\measure_page.py android/page_name.png 380 760     # 中列（金额）
python _ios\measure_page.py android/page_name.png 430 660     # 居中标题

# ③ 改 Swift：row(_:_:size:padV:)、titleSize、autoBoard、MockInk 常量
# ④ 数值写进 _ios/measurements.json，跑 node _ios\v2\check-pitch.mjs
# ⑤ 逐页提交（commit message 写清「哪页 / 多少 px」）
```

### 坐标

- 宫格列：`x = 207 / 540 / 873`；行：`y = 959, 1097, 1245, 1383, 1521, 1696, 1830, 1965, 2100`
- **后 4 格不在首屏**：先 `python _ios\android_drive.py swipe 1500 900`，再点
  话费(193,1380)、农信(523,1380)、qq(847,1380)、京东(193,1520)
- 抽屉滚动后：`y = 660, 784, 905, 1030, 1154, 1279, 1403, 1528, 1652, 1776, 1901, 2026`

---

## 6. 唯一未完成项：iOS 真机验证

`python _ios\live_shots.py mock:home:0 mock:sub:1 mock:gen` **连续 4 次 12 秒超时** ——
App 没在运行（构建能过，但进程没起 / 调试桥没连上）。

App 跑起来后，用**一条命令**判定整条链路是否成立：

```powershell
python _ios\live_shots.py mock:home:0 mock:sub:1 mock:gen
python _ios\measure_page.py live/mock_gen.png 40 300
```

- iOS 量出 ≈ **71px**（算得 70.8 / 安卓 69）→ 模型成立，按 §4 批量推进；
- 偏差 > **3px** → 先查 `Draw.text` 基线算法与字体度量，
  **不要再改页面参数**（否则 180 页会带着同一误差返工）。

---

## 7. 已知坑（都踩过）

| 坑 | 症状 | 正解 |
|---|---|---|
| 用 `UIFont.lineHeight` | 每行偏高 4~5px，整页偏松 | `size * 1.2` |
| 全局默认铺灰底板 | 白底页面被刷灰 | 默认 `nil`，逐页声明 |
| 只改 `measurements.json` | 校验绿但页面没变 | 清单与代码必须同步改 |
| PowerShell 写中文文件 | UTF-8 损坏、引号被吃掉 | 只用 `edit`/`write` 工具，别用 `Set-Content` |
| `node -e` 内联带引号 | 命令解析失败 | 写临时 `.mjs` 文件再跑 |
| 同一族套同一行距 | 微信 76 vs 89 差 13px | 每子页单独传参 |
| 盲点按导致进错页 | 抓到别的页面 | 先 `restart`，每次点按后截图确认 |
| `adb exec-out screencap > file` | PNG 损坏 | `adb shell screencap -p /sdcard/x.png` + `adb pull` |

---

## 8. 校验器一览（提交前全跑）

```powershell
node _ios\v2\lint-swift.mjs      # 550 项
node _ios\v2\check-access.mjs
node _ios\v2\check-symbols.mjs
node _ios\v2\check-members.mjs
node _ios\v2\check-instance.mjs
node _ios\v2\check-draw-api.mjs
node _ios\v2\check-mock.mjs      # 13 项：31 入口 / 195 子页 / subs 对齐
node _ios\v2\check-entries.mjs   # 31 入口完整性
node _ios\v2\check-pitch.mjs     # 行距模型 + 底板色 vs 实测
node _ios\v2\check-board.mjs     # 哪些页面没铺底板
```

`check-pitch` 是这一轮的核心资产：**任何人改动字号/内边距，只要偏离实测 2px 就会红**。

---

## 9. 关键 API 备忘

```swift
// 页面
struct MockPage { ops; hits; height; background }        // background = 整页底色
final class MockBuilder {
    var autoBoard: UIColor?      // 内容结束后的底板（默认 nil）
    var pageBackground: UIColor  // 整页底色
    func row(_ label: String, _ value: String,
             bg: UIColor? = nil, size: CGFloat? = nil, padV: CGFloat? = nil)
    func bottomFill(_ color: UIColor, canvasHeight: CGFloat = 2388)
    func lineHeight(_ size: CGFloat) -> CGFloat   // = size * 1.2
}

// 颜色
MockInk.board / pageAlt / entryGrey / page / band / line
```

---

## 10. 下一轮开工顺序（建议）

1. **先做验证**（§6）：取了 iOS 图再动手，避免盲改；
2. 验证通过后按 §4 第一行开始：**6 家银行的子页**（约 40 页，风险最低，同族参数）；
3. 再推 **建设 / 中信 / 浦发 / 平安 / 交通 / 兴业**（约 41 页）；
4. 最后攻 **云闪付 / 京东 / 抖音 / 印度 / 短信 / c币**（版式差异大，需要逐页读图）。
