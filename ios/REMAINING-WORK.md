# 剩余 4 屏的接手说明

> 这份文件是给「继续搬剩下 4 屏」的人（或下一轮的我）用的。
> 里头的常数都是**直接从 Java 源码里摘出来的**，不用再读一遍。

## 一、~~`LicaiScreen`（理财）842 行~~ ✅ **已完成**（第 36 轮）

> 下面这一节保留着当初挖出来的常数与做法，作为**已完成页面的技术记录**，
> 也给 `GrowthScreen` 复用（它调用的 `drawBody()` 就是这一屏的 body）。

### 已经确认的关键结构

1. **顶部蓝底不是渐变，是一个二次多项式色彩场**（Java:241-245）。三个通道各 6 个系数：

```java
private static final float[] HEADER_BLUE = {
    34.54431f, -0.0314062f, 0.0169517f, 0.00000517f, -0.00000712f, 0.00000510f,   // red
    141.45530f, -0.0532967f, 0.0250387f, 0.00001342f, -0.00001059f, 0.00000673f,   // green
    254.33077f, 0.0003059f, 0.0014226f, 0.000000172f, -0.000000917f, -0.000001554f // blue
};
// v = c[0] + c[1]*x + c[2]*y + c[3]*x² + c[4]*x*y + c[5]*y²
```

渲染方式：**只采 8×8 个点**生成一张小位图，然后拉伸铺满 `0 .. HEADER_BOTTOM+PANEL_H`
（`HEADER_BOTTOM=546`、`PANEL_H=601`）。Swift 侧照这个思路用 `CGImage` 生成 8×8 再 `draw` 即可 ——
与扫码页的扫描线位图同样的做法。

2. **可展开面板**：`expand` 为 true 时整页多出 `PANEL_H`（601）的高度，
   顶部那段裁剪区域变成 `0..546+601`，下面的 body 整体 `translate(0, +601)`，`maxScroll` 也多 601。

### 几何常数（Java:174-220）

```java
HEADER_BOTTOM=546, PANEL_H=601, CARET_Y=515, FULI_TOP=546, FULI_BOTTOM=728
GRID_TOP=680, GRID_BOTTOM=1184
GOLD_CARD=(42,1210,1237,1708)
CAROUSEL_TOP=1742, CAROUSEL_BOTTOM=2203, CAROUSEL_CARD_W=585, CAROUSEL_Y=1742
BOARD=(28,2235,1252,3157)
INDEX_CARD=(71,2270,1207,2445)
ETF_CARD=(71,2478,523,3155)
WIND_CARD=(554,2478,1207,3155)
TAB_TOP=3180, TAB_BOTTOM=3332
STEADY_CARD=(42,3374,1237,4180)
NAV_TOP=2560, NAV_X={128,382,639,898,1151}
PILLS={"热销产品","月月享","季季享","年年享"}, PILL_L={80,372,659,946}, PILL_R=1201
PILL_TOP=3554, PILL_BOTTOM=3669
```

**两行宫格的图标框（数组，顺序即 余额宝/稳健理财/黄金/基金/股票 与 定期/进阶理财/保险/帮你投/养老金）**：

```java
ROW1_BOX = {{90,728,192,837},{340,732,441,834},{581,733,695,834},{836,732,942,834},{1088,732,1187,834}}
ROW2_BOX = {{89,964,196,1080},{331,965,451,1080},{572,967,707,1076},{776,950,945,1080},{1077,971,1201,1078}}
ROW1_CX = {141,390,639,888,1137}   // 第一行文字基线 902，字号 43
ROW2_CX = {143,391,639,888,1137}   // 第二行文字基线 1131，字号 43
```

### 还缺的一块

`State` 类的默认文案（`total / yesterday / family / fundName / fundPct / stockName / stockPct /
index[3] / indexPct[3] / etfName[3] / etfPct[3] / windName / windPct / windLine1 / windLine2 /
prodName / prodPct`）在 **Java:50-148**，下一位只需读这一段就能写。

### 切图（32 张，`lci_*` 前缀）

`mascot / searchbar / search / gear / eye / shield / badge_chatiao / ic_yuebao / ic_wenjian /
ic_gold / ic_fund / ic_stock / ic_dingqi / ic_jinjie / ic_baoxian / ic_bangni / ic_yanglao /
gold_chart / card_fund / card_stock / card_etf / card_wind / tabbar / steady_mark / fuli /
nav_home / nav_zhi / nav_licai / nav_video / nav_msg / nav_me`

一次性同步：
```bash
node _ios/sync-assets.mjs lci_mascot lci_searchbar lci_search lci_gear lci_eye lci_shield \
  lci_badge_chatiao lci_ic_yuebao lci_ic_wenjian lci_ic_gold lci_ic_fund lci_ic_stock \
  lci_ic_dingqi lci_ic_jinjie lci_ic_baoxian lci_ic_bangni lci_ic_yanglao lci_gold_chart \
  lci_card_fund lci_card_stock lci_card_etf lci_card_wind lci_tabbar lci_steady_mark lci_fuli \
  lci_nav_home lci_nav_zhi lci_nav_licai lci_nav_video lci_nav_msg lci_nav_me
```

---

## 二、`WithdrawScreen`（余额提现）884 行 · 数据层 ✅ 已就位，只剩页面本身 ← **建议下一个做这个**

- `WithdrawStore.swift` —— ✅ **已移植完成**（13 家银行图标表、用户自备 logo、不可用卡、
  两个金额、选中卡、新卡插在不可用卡之前的规则，全部照 Java 逐条搬）
- `ReceiptStore` —— ✅ 已移植完成
- 入口：支宫格索引 12「余额提现」与 13「转账银行卡」都进它
- **所以这一屏现在只需要搬绘制与命中**，不用再建数据层

## 三、`GrowthScreen`（成长版）1057 行 · 需要两套数据层

- 依赖 `GrowthStore`（310 行）+ `AdvisorStore`（141 行）+ `LicaiVersion`（**已移植** ✓）
- 它复用 `LicaiScreen` 的 `drawBody(...)`（Java 里就是 `public` 的，两屏共用同一段 body）
  —— **`LicaiScreen` 已移植完成 ✓ 可直接照 Swift 版 `LicaiScreen.swift` 的 body 部分搬**

## 四、`BillAnalyticsScreen`（收支分析）1703 行 · 全工程最大

- 依赖 `BillStore`（**已移植** ✓）
- 71 处手绘，5 张切图；是账单页右上「收支分析」按钮的目标页
- 建议拆两轮：先做上半（概览 + 趋势图），再做下半（分类明细）

---

## 五、搬运一屏的固定流程（照这个做就不会漏）

1. 读 `XxxScreen.java`：抄 `W`/`H`、几何常量、调色板，注释标 Java 行号
2. `draw` 逐语句对应，**坐标一个不改**
3. `hitTest` 的分支顺序与区间照抄（顺序影响命中结果）
4. `node _ios/sync-assets.mjs <资源名…>` 同步切图
5. `node _ios/lint-swift.mjs` —— 会立刻报出「令牌没接」「素材不存在」「类型重名」
6. 在 Router 里接令牌；`node _ios/lint-swift.mjs` 再跑一次确认
7. 页面名加进 `_ios/gen-checklist.mjs` 的 `DONE`
8. `node _ios/verify-all.mjs` 全绿后再收工

**每一步都有自动检查兜着** —— 漏哪一步都会在 `verify-all` 里报出来。
