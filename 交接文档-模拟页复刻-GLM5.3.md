# 交接文档：iOS「模拟」银行页面逐像素复刻（面向 GLM 5.3）

> 最后更新：2026-10-25 21:05（北京时间）
> 仓库：`D:\AAA\doutu`，分支 `main`，远端 `git@github.com:luoyunxyz/doutu.git`
> 当前 HEAD：`b159a6b`（BOC 支付宝转入）
> 最近一次**已记录**的 CI 成功：`4a1579d`（`ci-logs/status.txt`，产物 `ios_v2/DoutuV2-unsigned.ipa`，46 MB）；
> `b159a6b` 的构建在推送后约 7 分钟出结果，用 `node _ios/ci-status.mjs` 看最新值。

---

## 0. 如果你只读 5 行

1. 你在做 **iOS App（`ios_v2/`）里「模拟」模块的银行页面复刻**：用户发一张银行 App 截图，你把它做到**像素级一致**（文字、字号、颜色、行距、图标都要一样）。
2. 本机是 **Windows，没有 macOS/Swift 工具链**。改完只能 `git commit + push`，由 GitHub Actions 构建 iOS 包；本机**不能**编译验证。
3. 所有页面都用 **1080×2388 的参考画布**、`dp(v) = v * 3` 设计坐标；截图参考一般是 1280 宽，换算系数 **×0.84375**。
4. 缺的图标/插画/banner **直接从用户截图裁切**成 `Assets.xcassets/<name>.imageset`（1x PNG，`Contents.json` 必须无 BOM）。
5. 用户会反复发图纠正；**先按图量、再做离线渲染比对、再推送**。页面归属（哪个子页）有歧义时**先问一句**，不要猜。

---

## 1. 任务与用户的要求

用户在做一款“模拟截图”类 App（豆图）。「模拟」Tab 下有 31 家银行/场景，每家 1~12 个子页。
近期的需求是：**按用户提供的真实银行 App 截图，逐页复刻 iOS 版页面**，要求：

- 版式、字号、颜色、行距、左右列位置**一模一样**；
- 顶部标题栏的样式（高度、标题颜色/字重、右上角图标、分割线）也要一致；
- 截图里有、代码里没有的图标/图片，**直接抠图**做成资源；
- 用户会指出“XX 位置不对/字体颜色不一样/页面也需要一模一样”，按图修。

用户常用语对照：

| 用户说 | 实际含义 |
|---|---|
| “这是 XX 页面” | 给出该子页的参考截图 |
| “需求一样” | 和上一页同样要求：像素级复刻 + 抠图 |
| “顶部的分层和右上角的两个图标不一样” | 标题栏高度/标题样式/右上角图标不对 |
| “字体颜色也要一样” | 按截图采样文字颜色，不要用近似色 |
| “明细二三” | 抽屉里第 2、3 个子页（有歧义时确认） |

---

## 2. 环境与工具（Windows）

| 项 | 值 |
|---|---|
| 仓库 | `D:\AAA\doutu`（Git Bash 可用，PowerShell 7） |
| iOS 工程 | `ios_v2/`（XcodeGen + Swift 5.9，iOS 14，`project.yml`） |
| 构建 | push 到 `main` 触发 `.github/workflows/ios-v2-build.yml`（macOS runner，约 7 分钟） |
| 构建结论 | `node _ios/ci-status.mjs`（绿=成功；失败看 `ci-logs/build.log` 的 `error:`） |
| 产物 | Actions artifact `DoutuV2-unsigned.ipa` / `origin/artifacts` 分支 |
| 真机 | iPhone 12（iPhone13,2，iOS 14.3），USB 已连接；`pymobiledevice3 usbmux list` 可见 |
| 安装 | 用户自己做（Sideloadly/全能签）；`_ios/device-deploy.ps1` 需要 `192.168.1.170:8080` 的全能签服务，**当前不可达** |
| 静态体检 | `node _ios/lint-swift.mjs`（277 项，只查括号/资源/符号，不做类型检查） |
| 参考图 | 用户消息里的 jpg；本文档配套的临时文件在 `_mockfix/`（**不要提交**） |

> ⚠️ 本会话一开始 `.git` 被沙箱禁止写入，需要 `danger-full-access` 才能 `git add/commit/push`。如果新会话又被拒绝，用同样方式申请一次权限即可。

### 常用命令

```powershell
cd D:\AAA\doutu
git status --short
git add -- <只加你改的文件>
git commit -m "..."
git fetch origin main --quiet
git pull --rebase --autostash origin main   # CI 会往 main 回写 ci-logs，必须先 rebase
git push origin main
node _ios/ci-status.mjs                     # 约 7 分钟后看结果
node _ios/lint-swift.mjs                    # 推送前跑
```

**只提交你改的文件**：工作区里有大量历史遗留的未提交修改（Android、server、_ios 脚本等），不要 `git add -A`。

---

## 3. 代码地图（iOS 模拟模块）

```
ios_v2/Sources/Mock/
  MockKit.swift          共用层：MockModule（31 个模块/子页名/detailTitle）、
                         MockBuilder（逐块堆叠绘制）、MockMetrics、MockOp、MockPage、
                         MockState（可编辑字段）、大量版式 helper
  MockEditorScreen.swift 编辑页（预览 + 生成图片按钮 + 抽屉）
  MockDetailScreen.swift 成品页（固定标题栏 + 页面内容；标题栏样式/高度按页配置）
  MockRemote.swift       远程 spec 调试渲染（enabled = false，别打开）
  Mock<Bank>.swift       各银行版式：MockNye/MockIcbc/MockBoc/...
ios_v2/Resources/Assets.xcassets/<name>.imageset/
                        抠图资源（Contents.json + 1x PNG）
```

### 3.1 关键概念

- **画布**：`1080×2388`，`dp(v) = v*3`。所有绘制坐标都是画布 px。
- **成品页标题栏**：`MockDetailScreen.headH = dp(52) = 156`，默认黑色粗体 17dp 标题 + `‹`；
  每页可以在 `headerStyle(_:)` 里覆盖标题颜色/字号/字重、右上角图标、底部线；
  **页高也可以按页覆盖**：`headH(_ s:)`，目前中国银行 0/1/2 子页用 `dp(31.5)=94.5`。
- **成品页比例**：`MockDetailScreen.keepsReferenceAspect = true`，按宽度等比铺满，
  避免安全区把整页纵向压扁 11.5%；底部超出屏幕的空白被裁掉（正常）。
- **编辑页预览**：`MockEditorScreen` 用 `.editor` 度量、页内画标题栏；和成品页布局代码共用。
- **可点编辑**：`MockHit` + `"mock:field:<标签>"`，`MockState.value(key, fallback)` 取值。
- **远程 spec**：`MockRemote.enabled = false`。它会让页面随“电脑上的 debug_server 是否可达”变样，**不要重新打开**。

### 3.2 MockBuilder 里新增/常用的 helper（复刻时优先复用）

| 方法 | 用途 |
|---|---|
| `titleBar(_:line:)` | 编辑页标题栏，`line:true` 补 1px 分割线 |
| `row(_:_:bg:size:padV:padH:)` | 通用“标签—值”行，值右对齐 |
| `pairRow(_:_:size:padH:padV:labelColor:valueColor:)` | 左右两端的行，支持 `\n` 两边多行 |
| `fixedRow(_:_:labelX:valueX:size:padV:...)` | 表格行：标签左对齐、**值固定列**，值支持 `\n`；单行标签在多行值里垂直居中 |
| `kvRow` / `greyKvBlock` / `cardRow` / `amountRow` | 工行/中行旧版式 |
| `centerBlock` / `leftBlock` | 居中/左对齐的单行大字 |
| `statusRow` | 可调高度的状态条（白底/绿✓） |
| `linkBlock` / `collapsibleLink(up:)` | 灰底链接条 / 居中“收起 ︿ / 查看更多 ⌄” |
| `settingRow` / `toggleRow` / `noteBlock` | 收支详情的设置卡（分类/开关/备注） |
| `twoButtons(...)` | 底部两个橙按钮（尺寸可覆盖） |
| `centeredImage` / `insetImage` / `fullWidthImage` | 居中图 / 左右留白图 / 满宽图（按图片比例算高） |
| `bandLabel` | 灰底小节标题（“您可能需要”） |
| `sectionActionRow` | 左标题 + 右图标/文字（“更多推荐 ——— 换一换”） |
| `divider(_:padV:after:)` | 细分割线（上下留白可不等） |
| `fill` / `gap` / `hairlineFull` / `bottomFill` | 色块/留白/满宽线/铺到底 |
| `MockOp.chevronUp/chevronDown` | 用两条线画的上下箭头 |

---

## 4. 当前页面映射（重要，容易错）

`MockModule.subs` 的顺序 = 抽屉里的顺序。**成品页标题**走 `MockModule.detailTitle(sub)`。

### 农业（`MockNye`，subs：明细转出1 / 明细转出2 / 明细转入 / 支付宝转入 / 明细转出3）

| sub | 抽屉名 | 实现 | 参考图 | 状态 |
|---|---|---|---|---|
| 0 | 明细转出1 | `statement` | 农业明细（黑标题+分割线） | ✅ 已按银行参考图重排 |
| 1 | 明细转出2 | `query(out:)` | — | 旧版式 |
| 2 | 明细转入 | `query(out:false)` | — | 旧版式 |
| 3 | 支付宝转入 | `alipayIncoming`（收支详情-收入） | 用户图（+264.00） | ✅ |
| 4 | 明细转出3 | `alipayOutgoing`（收支详情-消费） | 用户图（-264.00） | ✅ |

### 工商（`MockIcbc`，subs：明细转出1 / 明细转出2 / 明细转入1 / 明细支付宝转入 / 明细转入2 / 明细转入3）

| sub | 抽屉名 | 实现 | 参考图 | 状态 |
|---|---|---|---|---|
| 0 | 明细转出1 | `remit` | 汇款明细查询（灰蓝标题+耳机+⋯+灰绿线） | ✅ |
| 1 | 明细转出2 | `detail02` | 用户称“明细二”：+100.00 收入页 | ✅（按用户“二三”映射） |
| 2 | 明细转入1 | `detail03` | 用户称“明细三”：-70.00 消费 + 详情设置/为您推荐 | ✅ |
| 3+ | 支付宝转入等 | `detailV2` | 暂无新参考 | 旧版式 |

> ⚠️ 工商 1/2 的归属是用户口头说的“明细二三”，与抽屉名（转出2/转入1）语义不完全对应。
> **如果用户再说“位置不对”，先问清楚抽屉第 N 项对应哪张图，再改映射。**

### 中国（`MockBoc`，subs：明细转出1 / 明细转入1 / 支付宝转入 / 明细转入2 / 明细转出2 / 支付宝到账）

| sub | 抽屉名 | 实现 | 参考图 | 状态 |
|---|---|---|---|---|
| 0 | 明细转出1 | `resultV2`（操作结果） | 绿色✓ + 100.00元交易成功 + banner + 推荐 | ✅ |
| 1 | 明细转入1 | `detailIncoming`（收入金额/对方账户/6 行/灰底说明） | 用户图（100.00，跨行转账） | ✅ |
| 2 | 支付宝转入 | `detailIncoming`（同一版式，附言两行） | 用户图（网上快捷提现/在线交易网关/长附言） | ✅ |
| 3/4 | 明细转入2 / 转出2 | `detail` | 暂无新参考 | 旧版式 |
| 5 | 支付宝到账 | `receipt` | — | 旧版式 |

> 中国 0/1/2 用 **94.5px 矮标题栏**（`headH`）；0 的标题是 15dp 黑粗体 + 耳机图标，1/2 是 17dp 黑粗体、无图标。

---

## 5. 复刻一页的标准流程（照着做）

1. **收图**：把用户图复制到 `_mockfix/refN.jpg`（不要提交）。
2. **量图**（Python + Pillow，已装）：
   - 找到**内容顶**：标题栏底/分割线的 y（没有分割线就看标题中心 + 标题栏高度）。
   - 用行投影找每条文字带的 top/bottom，再找横向列位置（标签 x、值列 x）。
   - 颜色：取文字带里最暗 15% 像素的中位数（PIL/numpy），别用肉眼近似。
   - 换算：`design = (ref_y - 内容顶) × 1080/1280`；`dp = design / 3`。
3. **抠图**：图标/banner/广告直接用 PIL 裁：
   ```python
   from PIL import Image
   im = Image.open(r"_mockfix\refN.jpg").convert("RGB")
   icon = im.crop((x0, y0, x1, y1))
   out = r"ios_v2\Resources\Assets.xcassets\mock_xxx.imageset"
   import os; os.makedirs(out, exist_ok=True)
   icon.save(os.path.join(out, "mock_xxx.png"))
   ```
   `Contents.json` 用 **无 BOM UTF-8** 写：
   ```powershell
   $json = @'
   { "images" : [ { "filename" : "mock_xxx.png", "idiom" : "universal", "scale" : "1x" } ],
     "info" : { "author" : "xcode", "version" : 1 } }
   '@
   [System.IO.File]::WriteAllText("...\Contents.json", $json, (New-Object System.Text.UTF8Encoding($false)))
   ```
4. **写版式**：在对应 `Mock<Bank>.swift` 里加函数，`MockComposer`/`MockBoc.page` 的 switch 里加分支；
   页面标题在 `MockModule.detailTitle` 里配；标题栏样式在 `MockDetailScreen.headerStyle` 里配；
   特殊标题栏高度在 `MockDetailScreen.headH(_:)` 里配。
5. **离线验证**（强烈建议）：在 `_mockfix/` 写一个 Python 脚本按同样的公式渲染 1080×2388，
   和参考图（裁掉状态栏、按标题栏顶对齐）做行投影比对；文字带位置误差控制在 ~5px 内。
6. **静态体检**：`node _ios/lint-swift.mjs`（277 项应全绿）。
7. **提交推送**：只 add 你改的 Swift/资源，`fetch + pull --rebase --autostash + push`。
8. **等 CI**：`node _ios/ci-status.mjs`；失败就看 `ci-logs/build.log` 的 `error:`。
9. 告诉用户：提交号、构建状态、需要重签安装。

---

## 6. 坐标/排版约定（血的教训）

- **参考图比例不固定**：1280×2816、1279×2528、1280×3249 都出现过；**必须按宽度换算**，不要按高度。
- **状态栏**：参考图含手机状态栏（0~90/140px 不等），先找标题栏边界，再以**标题栏底**为内容 0 点。
- **行高**：不要用 iOS 字体行高，统一 `lineHeight = size * 1.2`。
- **字号**：用文字带 ink 高度/宽度反推，**不同页字号不同**（同是“明细行”可能是 12.7dp / 13.3dp / 14dp / 16dp）。
- **左右列**：这批准贷记账页的值是**固定列左对齐**（如中国明细：标签 x=50.7，值 x=309），
  不是右对齐；`fixedRow` 就是为此加的。标签与值之间的间距要按图留足（用户专门提过“紧贴着”）。
- **多行值**：`fixedRow` 的 value 可以带 `\n`；行高按最大行数算，单行标签在多行值里垂直居中。
- **标题栏**：中行这批是 94.5px 矮标题栏；工行/农行是 156px。标题颜色/字重/右上角图标都要按图。
- **负 gap**：某些页“余额”和首行贴得紧，行盒留白会把内容推低，可用 `b.gap(-dp(x))` 抵消（已用于工商 -70 页）。
- **图片**：banner/广告/图标一律从截图裁；`fullWidthImage` 按图片比例算高，`insetImage` 留左右边距。
- **颜色采样**：文字带最暗 15% 的中位数近似“真色”；JPEG 会偏色，拿不准就用图上的中位色。

---

## 7. 已知坑

1. **不要打开 `MockRemote.enabled`**：会让页面依赖电脑上的 debug_server，同一页两种样子。
2. **不要 `git add -A`**：工作区有大量历史改动；只加自己的文件。
3. **CI 会回写 main**（`ci-logs/*`）：push 前必须 `fetch + pull --rebase --autostash`。
4. **资源 Contents.json 不能有 BOM**（Xcode 也许能读，但保持和现有一致）；用 `UTF8Encoding($false)`。
5. **本机不能编译**：Swift 的类型错误只有 CI 能发现；改完先跑 `lint-swift.mjs`，再看 CI。
6. **模拟器/真机截图**：`_ios/live_shots.py` 通过 App 的 DebugBridge 轮询 `cmd.json` 抓图（需要电脑 `debug_server.py` 在 8000 端口、手机同一 WiFi）。
7. **安装**：全能签 192.168.1.170:8080 当前连不上；不要承诺自动装机，告诉用户自己重签。
8. **用户对“像素级一致”很敏感**：不要只看颜色，字号/行距/列位置/标题栏高度都要对；改完最好给离线对比图。

---

## 8. 未完成 / 下一步建议

- **中国**：sub 3/4（明细转入2/转出2）还没有新参考图，仍是旧 `detail`；等用户发图。
- **工商**：sub 3/4/5 还是旧 `detailV2`；sub 1/2 的“二三”映射待用户最终确认。
- **农业**：sub 1/2（查询页）没按新参考重排；00/03/04 已完成。
- **其它银行**（邮政、招商、光大、建设、平安、交通、中信、浦发、民生、微信、支付宝等）：大多还是旧版式，等图。
- **通用能力**：`MockDetailScreen.headH` 目前只对中行 0/1/2 特殊；如果更多页需要矮标题栏，扩展成按页表。
- **可复用经验**：新页优先用 `fixedRow` / `bandLabel` / `sectionActionRow` / `centeredImage` / `insetImage`，
  比每次手写 ops 快且一致。

---

## 9. 交付前检查清单

- [ ] 标题栏高度/标题颜色字重/右上角图标/分割线按图
- [ ] 字号、行距、左右列位置按图（行投影比对误差 ≤ ~5px）
- [ ] 文字颜色按图采样
- [ ] 图标/banner/广告已抠图，资源引用存在（lint 会查）
- [ ] `node _ios/lint-swift.mjs` 全绿
- [ ] 只提交本次文件；`pull --rebase` 后 push 成功
- [ ] CI 成功（`node _ios/ci-status.mjs`）
- [ ] 回复用户：提交号 + 构建状态 + 需要重签安装 + 如有歧义主动问

---

## 10. 给 GLM 5.3 的行为约定

- **先量后写**：没有测量数据不要凭感觉写坐标。
- **一次只改一页/一族**，改完立即 lint + commit + push，别攒大批改动。
- **不猜映射**：用户说“第 N 页”但抽屉顺序和语义对不上时，先问。
- **不擅自打开远程 spec / 不删旧版式函数**（可能还有页面在用）。
- **保留现有注释风格**：中文、说明“为什么”和参考图数值，方便下一轮对照。
- **推送后主动报**：commit hash、CI 状态、下一步。
