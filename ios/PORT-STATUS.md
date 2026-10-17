# iOS 移植完工度清单

> 生成于 2026-10-17（第 33 轮）。这份文件的唯一目的：**让任何人一眼看清「哪些做完了、哪些没做、各自缺什么」**。
> 数字全部可由 `node _ios/gen-checklist.mjs` 与 `node _ios/verify-all.mjs` 复现，不是估算。

## 一、总体

| 项目 | 数值 |
|---|---|
| 真实页面（Screen 类） | **27** |
| 已 1:1 移植 | **27**（16,147 / 16,147 行 = **100%**） |
| 未移植 | **0 屏** |
| 死亡代码（全工程 0 引用，不移植） | 3（`ConversationScreen` / `WeiHomeScreen` / `AlipayHomeScreen`） |
| Swift 代码 | 14,237 行 / 58 文件 |
| 素材（与 Android 同源） | 362 张 |
| 主题切图 | 15 张（五档理财皮肤） |

## 二、已完成（24 屏）

按代码行数降序。每一屏都是「坐标/颜色/字号/交互」逐条对照 Java 的 1:1 移植。

| 页面 | Android 行数 | 说明 |
|---|---|---|
| HomeShell（首页外壳） | 2041 | 四个平台页签 + 娱乐页 + 我页 + 详情态；**已用真机截图做几何比对，最大偏差 3px** |
| AssetScreen（总资产） | 857 | 内容高 8120，四张整图卡 + 大量数字补丁 |
| CollectMoneyScreen（收款码） | 628 | 37×37 二维码按同一 LCG 生成 |
| TravelScreen（出行） | 606 | 39 张切图按参考坐标贴回 |
| ZhiHomeScreen（支模块首页） | 589 | 斜向蓝带 + 宫格 + 可展开面板 |
| ZhimaCreditScreen（芝麻信用） | 543 | 等宽数字 + 1.053 拉伸、瀑布流空槽虚线 |
| ReceiptScreen（收款记录） | 538 | 按天分组、整页「批量添加」触发 |
| XiaohebaoScreen（小荷包） | 536 | 成员头像排、橙线随词宽 |
| PaySuccessScreen（支付成功） | 501 | 行/段两级结构 + 渐变切片补丁 + 混排字体 |
| MessageScreen（消息列表） | 472 | 7 个横向压缩系数、顶部切图接缝处理 |
| PayMsgScreen（账单消息） | 457 | 76 项字段表，默认状态即纯切图 |
| PayCodeScreen（收付款码） | 436 | 会员页舌、确定性条形码 |
| BalanceScreen（余额） | 383 | 白色柔光面板、彩卡渐变补丁 |
| ReceiptDetailScreen（收款详情） | 380 | 卡片高度按内容算 |
| BillDetailScreen（账单详情） | 375 | 自动扣款走另一套版式 |
| MeScreen（我的） | 375 | 1280×2560 设计空间缩放 |
| BillScreen（账单列表） | 365 | 0.94 压缩贯穿宽度测量 |
| YuebaoScreen（余额宝） | 339 | 11 色标渐变、横向压缩带锚点 |
| HuabeiScreen（花呗） | 295 | 6 条整宽切图 + 7 处动态补丁 |
| WithdrawDoneScreen（提现完成） | 292 | 11 色标蓝面 + 三种锚点压缩 |
| ProfileScreen（个人信息） | 222 | 逐字量出的横向拉伸系数 |
| ScanOverlayScreen（扫码浮层） | 216 | Hann 窗扫描线 + 预乘 alpha |
| SwitchAccountScreen（切换账号） | 215 | — |
| LicaiScreen（理财） | 842 | 二次多项式色彩场（8×8 采样板）+ 可展开面板；展开/隐藏金额可交互 |

## 三、未移植（0 屏）

**27 屏全部移植完毕，包括 `BillAnalyticsScreen` 的两个「收入版式」（月度收入页、年度收入页）。**

| 页面 | Android 行数 | 状态 |
|---|---|---|
| `WithdrawScreen`（余额提现） | 884 | ✅ 已移植（含 `WithdrawStore` 数据层） |
| `GrowthScreen`（成长版） | 1057 | ✅ 已移植（含 `GrowthStore` + `AdvisorStore`） |
| `BillAnalyticsScreen`（收支分析） | 1703 | ✅ 已移植（三个页签 × 支出/收入 = 六套版式） |

> 接手说明见 [REMAINING-WORK.md](REMAINING-WORK.md)：里头已经把 LicaiScreen 的二次色彩场系数、
> 全部几何常数、31 张素材名都摘好了，不用再读一遍 Java。

## 四、非页面但同样重要的部分

### 已完成并有验证

| 模块 | 状态 | 验证方式 |
|---|---|---|
| DTW 私有协议（握手 / 帧 / 序列号） | ✅ | `verify-dtw-mirror.mjs` 打真实生产 **20 项全过** |
| 业务接口签名（x-sign / 信封解析） | ✅ | 同上 |
| 登录 / 注册 / 邀请码找回密码 | ✅ | 原生表单，接口与 Android `DoutuApi` 对齐 |
| 头像上传 | ✅ | `PHPickerViewController` → 320px → base64；接口已验到设备上限那一步 |
| 版本检查 + 更新弹窗 | ✅ | 生产端到端：建 iOS 强制更新 → 正确下发 → Android 不受影响 |
| 小微主页（WebView 那一半） | ✅ 骨架 | 169 个文件 / 8.95 MB；`app://` scheme + 路由 + 16 方法桥 |
| 导航栈 + 令牌分发 | ✅ | **217 种**页面动作令牌全部有 Router 分支（静态检查强制）|
| 五档理财主题 | ✅ | 与 `LicaiVersion.java` 逐条一致 |
| CI 出包链路 | ✅ 自洽 | `verify-ci.mjs` 24 项：两份工作流的路径前缀、working-directory、artifact、project.yml 约定 |
| 令牌覆盖检查 | ⚠️ 曾漏检 | 见下方「检查器自己也会错」|

### 已知未做 —— 只剩三条「没有证据」与一条真实小缺口

| 项 | 说明 |
|---|---|
| **Swift 一次都没编译过** | 本机无 macOS；`ios-build.yml` 已就位（仓库根与 `ios/` 各一份，见 README §2.5），推上去跑一次即可 |
| 真机运行 | 需出包 + 万能签 |
| `app://` 下 ES module 能否加载 | 唯一技术未知数；退路是本地回环 HTTP 服务 |
| `more` 令牌 | 尚未逐屏确认对应行为（其余令牌都已核对过）|

### 已补齐（原列在这里，现全部完成）

| 项 | 做法 |
|---|---|
| 相机预览（扫码页背景） | `CameraPreviewView`（`AVCaptureVideoPreviewLayer`）+ `Screen.usesCameraBackground` 钩子；权限三态与「无摄像头」都有提示 |
| 长按手势 | 协议 `longPress` + 宿主 500ms 判定 + 芝麻信用返回 `feed:/empty:` 令牌弹模块菜单 |
| 横向拖动手势 | 协议 `carouselDrag/Snap` + 宿主 1.2 倍阈值 + 按卡片步长吸附 |
| 数值/文字编辑（136 个点） | `EditPrompt` + `EditableState`；理财 25 / 成长 34 / `AssetScreen` 22 / `BillDetailScreen` 11 / `ZhimaCreditScreen` 11 / `BalanceScreen` 12 / `YuebaoScreen` 9 / `HuabeiScreen` 7 / `ZhiHomeScreen` 6 / 两个动态键屏 |
| 理财主题切换 | `VersionPicker`，五档可切并即时重绘（黑卡主题会换掉「我的」页 / 消息页 / 支首页配色）|
| 模块底栏导航 | 理财/成长版的 5 格：0 回支首页 · 1 回顶 · 3 消息 · 4 我（与 `LicaiActivity` 一致）|
| 切换账号 | `Accounts.switchTo` 写 `ProfileStore` + 重置私有协议会话 |

### 经核对「不是缺口」的四项

| 令牌 | 结论 |
|---|---|
| `filter`（账单筛选） | Android `BillActivity.act()` 里没有这个分支 |
| `settings/out/in/goal/promise`（小荷包） | Android 是空分支 `} else if (...) { }` |
| `ai`（芝麻信用助手） | Android 是 `if (...) { return; }` |
| `mileage` / `search` / `cabin`（出行） | 出行页里是 `break;`（同名令牌在别的 Activity 有实现，容易误判）|

> 这四项曾被我列成「缺口」，逐个查 Android 源码后确认是误报 —— **清单本身也需要对着源码验证**。

### 检查器自己也会错（重要）

2026-10-17 第 87 轮，读 `XiaohebaoScreen` 时顺手对了一下令牌，发现**5 个令牌没有 Router 分支**，
而「令牌完整性」一直是绿的。原因在 lint 的提取正则：

```js
/hit\([^)]*?,\s*"([^"]*)"\s*\)/     // ✗
```

页面里常写成 `hit(eyeRect.insetBy(dx: -14, dy: -14), "eye")` —— `[^)]*?` 在 `insetBy(`
的第一个 `)` 就停住，**整条注册被漏掉**。改成配平括号扫描后，令牌总数从 **153 → 217**
（有 64 个令牌以前根本没被检查过），并当场暴露 11 个未接分支：

| 令牌 | Android 行为 | 处理 |
|---|---|---|
| `eye`（资产页）| 切换隐藏所有金额 | ✅ 真功能，已接 |
| `purse` / `total`（小荷包）| 弹框改钱包名 / 总金额，写 `PurseStore` | ✅ 真功能，已接 |
| `dots` / `info` / `bell` / `invite` / `rights` / `func` / `plugin` / `publish` | `break;` / 空分支 | 空转，与 Android 一致 |

**教训**：检查器的漏检比代码的 bug 更危险 —— 代码有 bug 用户点到就知道，
检查器漏检会让我长期以为「覆盖完整」。现在这条检查才真正可信。

## 五、可复现的验证命令

```bash
node _ios/verify-all.mjs        # 一键跑完下面全部
node _ios/lint-swift.mjs        # Swift 静态体检 217 项
node _ios/check-draw-api.mjs    # 页面调用的每个 d.xxx 是否真存在（含参数标签与个数）
node _ios/check-symbols.mjs     # 类型成员、Screen 协议、push 配对、State 继承
node _ios/check-access.mjs      # 重复成员 + private 跨文件访问（两类编译错误）
node _ios/check-members.mjs     # `Type.member` 是否指向真的存在的静态成员
node _ios/check-instance.mjs    # `s.member`（State 实例）是否真的存在（311 次访问）
node _ios/list-switches.mjs     # 列出无 default 的 switch（枚举穷尽需人工核对，已全部核过）
node _ios/audit-tokens2.mjs     # Android 有的令牌，iOS 是否都产生/接住
node _ios/check-reachable.mjs   # 27 屏是否都能进入（死页面）+ Router 里的页面是否存在
node _ios/check-state.mjs       # 224 个 State 字段有没有闲置
node _ios/check-deadends.mjs    # Router 里的空转分支（对照 Android 是否也空转）
node _ios/verify-ci.mjs         # CI 链路自洽性 24 项（两份工作流 + project.yml 约定）
node _ios/verify-dtw-mirror.mjs # DTW 协议镜像打生产 20 项
python _ios/compare_geometry.py      # 我页 4 个特征块 vs 真机截图
python _ios/compare_geometry_ent.py  # 娱乐页 4 个特征块 vs 真机截图
python _ios/compare_shots.py --android a.png --ios b.png --align --out diff.png
                                # 其余 25 屏的通用截图比对（缩放 + 自动对齐 + 差异图）
node _ios/gen-checklist.mjs     # 重新生成本文件的进度数字
```

服务端回归（与 iOS 端配套）：

```bash
cd server
node scripts/check-imports.js      # 61 个模块全部可加载
node scripts/smoke.js              # 70 项
node scripts/dtw-test.js           # 19 项
node scripts/version-check-test.js # 99 项
node scripts/upload-test.js        # 21 项
```
