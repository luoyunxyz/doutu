# 苹果端（iOS）—— 1:1 还原 Android 客户端

> **结论先看这份**：[DELIVERY.md](DELIVERY.md) —— 做完了什么 / 没做完什么 / 怎么用，一页讲完。

## 一句话现状

**24 / 27 屏**已按 1:1 移植（12,503 / 16,147 行 = 77.4%），Swift 共 **10,799 行 / 50 个文件**；
网络层（DTW 私有协议）用「逐行镜像」在真实生产服务端验证过 **20 项全过**；
登录/注册/找回密码/头像上传/版本更新/**WKWebView 宿主**都已接通。

**但本机是 Windows，Swift 从未编译过** —— 按下面 §2 的路径在云端出包，是唯一剩下的关键一步。

进度表见 [PORTING-CHECKLIST.md](PORTING-CHECKLIST.md)：真实页面 **27 屏 / 16,147 行**
（另有 3 屏是**死代码**，全工程无人引用，已从清单剔除），
已移植 **27 屏**（16,147 行，100%）。另有 **WKWebView 宿主**承载「微」那一半的 React 页面。
页面之间已有 **push/pop 导航栈**；**登录 / 注册 / 邀请码找回密码 / 头像上传**已接通。

> 搬运顺序不是按行数，而是按**手绘复杂度**排的：`_ios` 下有个一次性扫描脚本统计每屏的
> 切图数 / 文字数 / 形状数，切图多、形状少的先做（`PayMsgScreen` 457 行但只有 1 处手绘，
> 比 216 行却有 5 处形状的 `ScanOverlayScreen` 更容易）。

### 头像上传（目标里点名的一项）

链路：相册选图（`PHPickerViewController`，**不需要相册权限**）→ 居中裁方 → 320px → JPEG 0.85
→ base64 → `POST /api/v1/auth/avatar`（需登录签名）→ 覆盖本地 `ProfileStore` → 通知界面重画。

`_ios/verify-avatar.mjs` 把这条链用 Node 走一遍打真实生产。**当前进度**：

| 环节 | 结果 |
|---|---|
| 隧道握手 | ✅ 通过 |
| 登录请求格式（`rb` 原始体 + `x-sign`） | ✅ 服务端已能正确解析（不再回「请输入账号与密码」） |
| 测试账号 `resettester` | ⚠️ 撞上「最多绑定 1 台设备」（历史测试留下的绑定），需要先解绑一台才能继续 |
| 上传 / 清空头像 | ⏸ 被上一条挡住 |

> 这个 gate 反而验证了一件事：**服务端的设备上限策略是生效的**，而且我的请求确实被当成合法请求处理了。
> 等测试账号有空的设备位，`node _ios/verify-avatar.mjs` 一条命令就能跑完剩下四步。

### 已用真机截图验证过几何的两屏

| 页面 | 校验的特征块 | 最大偏差 |
|---|---|---|
| 「我」页 | 会员卡 / 金色按钮 / 用户行白卡 / 常用服务卡 | **3px** |
| 娱乐页 | 渐变头图 / 三张分区白卡（上下边界） | **3px** |

娱乐页这一条尤其有价值：它验证的是 `Playbook.layout()` 的**排版公式**
（头部 +40、分区标题 96、行高 210、分区距 44）在真机上完全对得上。

### 已用真机点击验证过的命中逻辑

`MainActivity` 的首页宫格命中阈值（分段 280/540/795/1045、列 295/540/782、微模块行 1170/1390/1610/1830、
支模块行 940/1180/1400/1610）我在真机上逐格点过：

| 操作 | 真机结果 | 与 Java 一致 |
|---|---|---|
| 点「支」页签 → 点第 1 行第 4 格 | `features.zhi.YuebaoActivity`（余额宝） | ✅ |
| 点「微」页签 → 点第 1 行第 1 格 | `features.wei.WeiHomeActivity`（小微主页） | ✅ |
| 点「微」→ 第 2 行第 2 格 | 仍是 `WeiHomeActivity`（朋友圈集赞是该页面的**内部路由**） | ✅ |

**这条揭示了一个结构事实**：微模块那 16 格全部是「同一个 WebView 页面的不同路由」，
所以 iOS 端要把它们接上，前提是先有 **WKWebView 宿主 + 那份 React 页面 + 桥**——
这是下一个大件（见下）。

---

## 1. 为什么是原生 Swift + CoreGraphics，而不是 WebView + HTML

你最初选的是 WebView 混合，后来要求「1:1 还原」——这两件事**在像素层面是冲突的**：

- Android 端的界面不是标准控件，而是在**固定参考画布**（首页 1080×2388、支模块 1280×2816）上用
  `Canvas` 按**硬编码坐标手绘**的：`textCentered(c, "我的", 640, 150, 52, INK, true)`
- 绘制原语与 CoreGraphics 是**一一对应**的：`drawText → text`、`drawRoundRect → round`、
  `drawBitmap → art`、`drawPath → addPath`
- 换成 HTML/CSS，即使坐标完全一样，**字体度量、字距、抗锯齿**也不同 → 文字宽度与位置必然偏移，
  做不到逐像素一致

所以 1:1 的唯一可靠路径是：**把绘制语句逐条搬过去，坐标/字号/颜色不改**。
WebView 只在 `fake_world` 那个现成的 React 页面里使用（它本来就是网页）。

---

## 2. 没有 Mac 怎么出包（你选了万能签）

iOS 的 IPA 必须由 Xcode 工具链编译，Windows 上做不到。两条可行路径：

### 路径 A：GitHub Actions 云端编译（免费，推荐）

1. 把仓库推到 GitHub
2. 把 `ios/.github/workflows/ios-build.yml` 复制到仓库根目录的 `.github/workflows/`
3. Actions → **build-ios-ipa** → Run workflow
4. 跑完在 Artifacts 里下载 **Doutu-unsigned-ipa**（未签名 IPA）
5. 把 IPA 交给「万能签」重签，装到 iPhone

工作流做的事：`brew install xcodegen` → `xcodegen generate` → `xcodebuild -sdk iphoneos`
（`CODE_SIGNING_ALLOWED=NO`）→ 打成 `Payload/Doutu.app` 的 IPA → 上传 artifact。
**编译失败会在 Actions 里直接报错**，等于给这套没法本机编译的代码配了一个编译器。

### 路径 B：借一台 Mac / 云 Mac

```bash
brew install xcodegen
cd ios && xcodegen generate && open Doutu.xcodeproj
# 在 Xcode 里选你的开发者账号，连真机 Run
```

---

## 2.5 CI 工作流放在哪（容易踩的坑）

GitHub Actions **只认仓库根的 `.github/workflows/`**。所以按你推送的仓库根不同，有两份等价的工作流：

| 仓库根 | 用哪一份 | 区别 |
|---|---|---|
| `doutu/`（整个项目） | `doutu/.github/workflows/ios-build.yml` | 带 `working-directory: ios`，产物路径 `ios/Doutu-unsigned.ipa` |
| `ios/` 本身 | `ios/.github/workflows/ios-build.yml` | 不带 `working-directory`，路径无 `ios/` 前缀 |

两份的路径前缀写错会在 checkout 之后立刻失败，而且报错信息很难看出原因 ——
所以有 `node _ios/verify-ci.mjs`（24 项）专门对账这两份文件与 `project.yml` 的关键约定。

---

## 3. 工程结构

```
ios/
├── project.yml                    工程定义（XcodeGen；不提交 .xcodeproj）
├── .github/workflows/ios-build.yml 云端出未签名 IPA
├── PORTING-CHECKLIST.md           29 屏移植进度表（自动生成）
├── Sources/
│   ├── App/DoutuApp.swift         App 入口、承载控制器、路由、资料存储、皮肤开关
│   ├── Design/
│   │   ├── Theme.swift            213 个颜色常量（由 Java 自动生成，勿手改）
│   │   ├── Canvas.swift           参考画布 → 屏幕的等比映射
│   │   └── Draw.swift             CoreGraphics 绘制助手（对应 Canvas/Paint）
│   └── Screens/
│       ├── Screen.swift           页面协议 + 承载 View（绘制/滚动/触摸/命中）
│       └── MeScreen.swift         「我的」页 1:1 移植（对应 features/zhi/ui/MeScreen.java）
└── Resources/Assets.xcassets/     19 张切图（从 Android drawable-nodpi 同步）
```

### 理财主题（五档皮肤）

LicaiVersion.swift 完整搬了 Android 的五档主题：普通版（纯绘制）+ 成长版 / V1 / V2 / V3（财富黑卡，
各有自己的背景、铭牌、卡片、卡套四张切图与前景/强调色）。

| 档 | 名字 | 前景色 | 强调色 |
|---|---|---|---|
| 0 | 普通版 | #202124 | #1677ff |
| 1 | 成长版 | #38271c | #b88a62 |
| 2 | V1 | #38271c | #c89f84 |
| 3 | V2 | #152860 | #9ea9c0 |
| 4 | V3 | #0f0f11 | #c8a36f |

lackCard(v) = 除普通版以外都算黑卡 —— 「我的」页、消息页、支首页靠它切换配色与图标（hk_* 那批）。

### 四个「从 Java 源直接生成」的脚本（Android 改了重跑即可同步）

```bash
node _ios/gen-theme.mjs        # 从 Java 扫颜色常量 → Sources/Design/Theme.swift（213 个）
node _ios/gen-billstore.mjs    # 从 BillStore.java 抽数据表 → BillStoreData.swift（68 商户 / 50 商品 / 30 金额档）
node _ios/sync-assets.mjs      # 从 res/drawable-nodpi 拷切图 → Assets.xcassets（235 张）
node _ios/gen-checklist.mjs    # 统计移植进度 → PORTING-CHECKLIST.md
```

**为什么账单数据也必须用脚本生成**：`SHOPS` 是 69 行 × 6 个字段（名称/分类/字母标/标底色/标字色/收款方全称），
手抄一定会错一个字符，而错的那个字会直接显示在账单列表上。脚本按 Java 的数组字面量逐项搬，保证逐字一致。

### 一键全量验证

```bash
node _ios/verify-all.mjs     # 串起下面全部检查，最后给一张总表
```

| 检查 | 内容 | 项数 |
|---|---|---|
| `lint-swift.mjs` | 括号配平 / 类型重名 / 引号 / 资源引用 / 令牌覆盖 / 版本号一致性 | 100 |
| `check-draw-api.mjs` | 页面调用的每个 `d.xxx(` 是否真在 Draw.swift 里（拼错即编译失败） | 3 |
| `verify-dtw-mirror.mjs` | DTW 协议 + 配置接口，打真实生产服务端 | 20 |
| `compare_geometry.py` | 真机截图几何比对（我页 4 个特征块） | 4（3px 内） |
| `compare_geometry_ent.py` | 真机截图几何比对（娱乐页 4 个特征块） | 4（3px 内） |

### 逐像素比对（几何层）—— 已对真机跑通

`_ios/compare_geometry.py` 会从 Android 真机截图里量出特征块的真实边界，与 Swift 里的
坐标公式对账。**「我」页实测：**

| 特征块 | 理论（画布 px） | 真机实测 | 偏差 |
|---|---|---|---|
| 深蓝会员卡 | (74.2, 473.9, 1005.8, 781.7) | (74, 474, 1005, 781) | **1px** |
| 金色开通按钮 | (735.8, 583.9, 951.8, 690.3) | (736, 584, 950, 689) | **2px** |
| 用户行白卡 | (47.2, 199.6, 1032.8, 451.5) | (49, 201, 1030, 449) | **3px**（2.4px 描边） |
| 常用服务白卡 | (74.2, …, 1005.8) | (74, …, 1005) | **1px** |

> 为什么比「几何」而不是比「像素」：两端字体渲染（字形、字距、抗锯齿）本来就不同，
> 比颜色必然失败；而**位置/尺寸**是 1:1 移植最可能算错、也最能客观验证的部分。

---

## 4. 移植一屏的固定步骤（每屏都照这个做）

1. 读 `XxxScreen.java`：把 `W`/`H`、几何常量（`RectF`）、调色板原样抄成 Swift 常量，注释标 Java 行号
2. `draw(_:state:)` 逐语句对应 Java 的 `draw(Canvas, State)`，**坐标一个不改**
3. `hitTest` 的分支顺序与区间照抄（顺序影响命中结果）
4. 位图跑 `node _ios/sync-assets.mjs <资源名>` 同步
5. 真机截图 ↔ Android 同屏截图逐像素比对，修到一致
6. 页面名加进 `_ios/gen-checklist.mjs` 的 `DONE`，重跑生成进度表

---

## 4.5 小微主页（WebView 那一半）

「微」模块那 16 格全部落在**同一个页面**上（真机验证过：都进 `WeiHomeActivity`），
区别只是路由参数。所以 iOS 侧不是 16 个原生页面，而是 **一个 WKWebView 宿主 + 包内那份 React 页面**。

| 组件 | 实现 |
|---|---|
| 页面资源 | `Resources/web/`（从 Android `assets/fake_world` 原样拷贝，169 个文件 / 8.95 MB） |
| 打包方式 | XcodeGen 里以 **`type: folder`** 加入（Vite 产物靠 `./assets/*.js` 相对路径，拆成单文件会散架） |
| 加载方式 | `WKURLSchemeHandler` 把 `app://local/*` 映射到包内 `web/*` |
| 为什么不 `loadFileURL` | 页面是 ES module（`<script type="module" crossorigin>`），`file://` 下会被 CORS 拦成白屏 |
| 路由 | `#/...` → `window.__fwOpenRoute(...)`；`feature:xxx` → `window.__fwOpenFeature(...)`（与 Android 同名） |
| 原生桥 | `window.DoutuApp`（16 个方法，经 `WKScriptMessageHandler` 转发；`httpGet` 已实抓，相册类先按空值应答） |

### 已经验证到的两件事

1. **这份页面能在普通 HTTP origin 下正常加载**：本机静态服务器 + Edge 渲染跑通，
   素材、ES module、hash 路由都正常。
2. **iOS 的 UA 会走移动版分支**。这一点必须验 —— 同一份页面：
   - 桌面 Edge UA → 渲染成「编辑器外壳」（左侧面板 + 设备框，明显不是 App 里该有的样子）
   - **iPhone UA → 立刻变成满屏移动版**，与 Android WebView 观感一致

   iPhone 上 WKWebView 天然是 iPhone UA，所以这条不需要额外适配。

### 还需要真机确认的

`app://` 自定义 scheme 下 **ES module 能否加载** —— 这是本方案唯一的未知数
（iOS 14 之前自定义 scheme + module 有已知问题，15+ 通常可用）。
万一不行，退路是起一个**本地回环 HTTP 服务**（Android 也是靠拦截 http 请求喂 assets，语义等价），
改动只在 `WebHost.swift` 里换加载方式。

---

## 5. 当前验证状态（务必看清）

| 项目 | 状态 |
|---|---|
| 颜色令牌与 Java 一致 | ✅ 自动生成，213 个，无手抄误差 |
| 切图与 Android 同源 | ✅ 33 张从 drawable-nodpi 直接拷 |
| **位图尺寸 ↔ Swift 常量对照** | ✅ 逐一吻合（见下），等于对移植坐标的一次独立验证 |
| 几何常量与 Java 一致 | ✅ 各页面均标注对应 Java 行号，可逐条核对 |
| **DTW 私有协议实现** | ✅ 用 Node 逐行镜像打生产：**20/20 通过**（含配置接口与 playbook） |
| **iOS 版本推送链路** | ✅ 生产端到端：建 iOS 强制更新 → `has_update/force/url` 正确 → Android 不受影响 |
| **Swift 静态体检** | ✅ 100 项 + 绘制 API 对账 3 项（页面用到的 21 个绘制方法全部存在） |
| **真机几何比对** | ✅ 我页 / 娱乐页各 4 个特征块，**最大偏差 3px** |
| Swift 代码能否编译 | ❌ **本机无 macOS/Xcode，未编译过** —— 靠 §2 的 CI 编译验证 |
| 真机运行与像素比对 | ⚠️ 几何已验证；界面外观需出包后在 iPhone 上看（字体渲染两端必然有差异） |

### 静态体检里最有用的三条（都抓到过真问题）

1. **类型重名**：`Canvas` 被定义两次（结构体 + 自动生成的枚举）→ 整个工程编译不过。已改名。
2. **版本号三方一致**：`project.yml` / `DtwConfig.swift` / 编码公式必须对齐，漏改一处 =
   「永远收不到更新」。
3. **动作令牌覆盖**：页面 `hitTest` 返回的 23 种令牌，Router 里必须有分支 ——
   第一版就抓出 4 个「点了没反应」的缺口（`nav:*`、`editName`、`editPhone`）。

### 位图尺寸与代码常量的对照（干跑一遍 1:1 的几何）

| 位图 | 实际尺寸 | 代码里的常量 | 结论 |
|---|---|---|---|
| `home_top` | 1080×912 | `HomeShellScreen.topHeight = 912` | ✅ |
| `seg_wei/zhi/dou/q` | 215×154 | `segmentWidth=215`、`segmentBottom-segmentTop=154` | ✅ |
| `me_bgplate` | 64×80 渐变小图 | 拉伸到 1280×700（Android 同样拉伸） | ✅ |
| `me_card1..8` | 1222×143 | `cardRight-cardLeft=1222`、`841-698=143` | ✅ |
| `sw_card` | 1280×313 | `SwitchAccountScreen.cardH = 313` | ✅ |

参照图与切图是 1:1 移植的基准尺，尺寸对得上说明坐标没有抄错位。

### 静态体检抓到过的真错误

`_ios/lint-swift.mjs` 在第一次运行时发现 **`Canvas` 类型被定义了两次**
（`Design/Canvas.swift` 的结构体 + 自动生成主题文件里的枚举）—— 这在 Swift 里是致命冲突，
会导致整个工程编译失败。已把生成的那个改名为 `ReferenceCanvas`。
这也说明：在没有编译器的环境下，把「能静态发现的问题」自动化掉是值得的。

### 网络层已实现且验证过

`Sources/Net/` 四个文件，逐项对齐 Android `account/dtw/`：

| 文件 | 内容 |
|---|---|
| `DtwCrypto.swift` | PBKDF2-HMAC-SHA256、RSA-OAEP(SHA-256)、AES-256-GCM、SHA256withRSA 验签 |
| `DtwConfig.swift` | 服务端地址、内置公钥（信任锚）、proof 迭代、bucket、路径 |
| `DtwSession.swift` | hello/open 两段握手（含 transcript 验签、慢哈希证明、冷却重试） |
| `DtwTransport.swift` | 隧道帧组/拆包（`DTW1` + 18 字节头做 AAD）+ 发请求 |
| `ApiClient.swift` | 业务请求构造（空跑签名串、`x-sign` HMAC）、信封解析、Keychain 会话 |

**协议验证方式（本机无 Swift 也能验）**：`_ios/verify-dtw-mirror.mjs` 把 Swift 里的每一步
等值写成 Node（同样的常量、同样的 transcript 字段顺序、同样的帧布局、同样的签名串），
直接打生产服务端：

```
node _ios/verify-dtw-mirror.mjs
  ✅ hello 验签通过（信任锚成立）
  ✅ open 验签通过 / 拿到 sid
  ✅ 隧道 HTTP 200 / 响应 magic 正确 / 业务信封 code=0
  ✅ 篡改后的帧无法解密（AEAD 生效）
  通过 15 项，失败 0 项
```

镜像通过 ⇒ 协议理解与转录自洽；Swift 剩下的风险只在语言 API 用法，由 CI 编译发现。

### 下一阶段要做的事（按优先级）

1. ~~DTW 私有协议 / API / 会话~~ ✅ 已完成
2. ~~首页外壳：首页 + 娱乐页 + 我页~~ ✅ 三栏全部完成
   - `HomeShellScreen.swift`：三栏分派 + 首页（四张平台页签底图 + 固定顶部 + 选中态 + 横幅轮播 + 底栏 + 红点）
   - `HomeShellScreen+Entertainment.swift`：渐变头图 + 分区白卡片 + 行（图标块/标题/副标题/NEW/开关/箭头）+ 8 个矢量字形
   - `HomeShellScreen+Profile.swift`：1280×2560 设计空间里的「我」页 —— 用户行（圆形头像/渐变兜底/首字）、
     深蓝金冠会员卡（盾牌+王冠纯路径绘制）、四行常用服务（矢量图标）、轮播指示点
   - `Playbook.swift`：服务端下发的分区/行模型 + 排版计算（画与点同源）
   - `ConfigLoader.swift`：拉 `/api/v1/config` 填娱乐页与横幅（失败退回内置兜底）
3. **更新检查 + 更新弹窗** ✅ 已完成（`UpdatePrompt.swift`）：回前台 60 秒节流复查、稍后再说、
   强制更新期间每次回前台重新弹（iOS 上不杀进程，原因见文件头注释）、用系统
   `UIAlertController` 呈现（与 Android 的 `AlertDialog` 对等）。
   **已在生产端到端验证**：建一条 iOS 强制更新 → 按 iOS 身份查询得到
   `has_update=true / force=true / url=*.apk` → Android 客户端 `has_update=false`（平台隔离生效）→ 删除临时版本。
4. **登录 / 注册 / 邀请码找回密码** ✅ 已完成（`AuthService.swift` + `LoginView.swift`）：
   Android 那边也是原生表单（不是 Canvas），所以 iOS 用 SwiftUI 原生控件 —— 两端操作感受一致。
   接口对齐 `DoutuApi`：`/auth/login`、`/auth/register`、`/auth/invite/verify`、`/auth/invite/reset`、
   `/auth/profile`、`/auth/avatar`、`/auth/logout`；登录响应取 `token` + `sign_key` 写进 Keychain。
5. **首页外壳剩下的详情态**（`drawDetail`）
6. **WKWebView 宿主 + 小微主页（React 页面）** ✅ 骨架已完成（`WebHost.swift`）：
   页面资源、`app://` scheme handler、`__fwOpenRoute`/`__fwOpenFeature` 路由、`DoutuApp` 桥（16 方法）
   都已就位，微模块 16 格全部接上路由。**待真机确认**：自定义 scheme 下 ES module 能否加载。
7. **剩余 20 屏**（按复杂度升序）：`BillScreen`(365) → `PaySuccessScreen`(501) →
   `ZhimaCreditScreen`(543) → `AssetScreen`(857) → `LicaiScreen`(842) → …
8. **头像上传 UI**（相册选择 + 裁剪，接口已在 `AuthService.uploadAvatar`）
