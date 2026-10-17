# 交接说明（新会话从这里接手）

> 更新时间：**编译收敛完成**。仓库 `git@github.com:luoyunxyz/doutu.git`，分支 `main`。
> 本机：Windows，**无 macOS / 无 Swift 工具链**。出包靠 GitHub Actions，安装靠 Sideloadly 自签。

## 一、现在的状态：能出包了 ✅

| 事项 | 状态 |
|---|---|
| 27 屏 Swift + CoreGraphics 移植（15,328 行 / 64 文件）| ✅ 完成 |
| 云端编译（macos-14 / Xcode 15.4 / iOS SDK 17.5）| ✅ **通过**（run 39019190956）|
| 产物 `Doutu-unsigned.ipa` | ✅ 31 MB，artifact 名 `Doutu-unsigned-ipa` |
| App 图标（1024 AppIcon）| ✅ 已加，随包打进 Assets.car |
| SwiftUI Scene Manifest | ✅ `INFOPLIST_KEY_UIApplicationSceneManifest_Generation: YES` |
| 装到 iPhone | ⏳ **只剩人来做**：下载 artifact → Sideloadly 用 Apple ID 签名 → 信任证书 |

**下一步不是改代码，而是装机验收** —— 步骤见 [`ios/INSTALL-IPA.md`](../ios/INSTALL-IPA.md)，
验收清单见 [`ios/DEVICE-TEST.md`](../ios/DEVICE-TEST.md)。

## 二、CI 日志闭环（最重要的机制）

`.github/workflows/ios-build.yml` 里这几条设计，**不要删**：

1. **成功时**把 `status: success` 写进 `ci-logs/status.txt`（附 run id、commit、IPA 大小），
   并把 `ci-logs/info-plist.log`（产物 Info.plist、图标、字体的清点结果）一起 commit + push 回 `main`
2. **失败时**把 `build.log` / `xcodegen.log` / `env.log` 复制到 `ci-logs/` 并 commit + push 回 `main`，
   `ci-logs/status.txt` 写 `status: failure`
3. 用 GITHUB_TOKEN 推送不会再次触发工作流（所以不会死循环）

所以本机**不需要登录 Actions 页面**就能知道结果：

```powershell
cd D:\AAA\doutu
node _ios/ci-status.mjs          # fetch + 打印结论（失败时列出 error 行）
node _ios/ci-status.mjs --wait   # 每 30 秒查，最多 15 分钟
```

或者纯 git：

```powershell
git fetch origin
git show origin/main:ci-logs/status.txt
git show origin/main:ci-logs/build.log | Select-String 'error:'
```

每轮流程：`改代码 → git commit → git push → 等 7 分钟 → node _ios/ci-status.mjs`

## 三、编译阶段修过的问题（别再改回去）

| # | 问题 | 修法 |
|---|---|---|
| 1 | `import CommonCrypto`（无 bridging header 不是可用模块）| 改 CryptoKit；`CCHmac`→`HMAC<SHA256>`；PBKDF2 按 RFC 8018 手写（已用 Node 镜像验证与 openssl 逐字节一致）|
| 2 | `AuthService` / `Playbook` 缺 `import UIKit` | 补上（Swift import 按文件生效）|
| 3 | `project.yml` 没有 `schemes:` | 补 `schemes: Doutu:`（XcodeGen 不自动生成）|
| 4 | 缺 `GENERATE_INFOPLIST_FILE: YES` | 补上，否则 `INFOPLIST_KEY_*` 不生效 |
| 5 | XcodeGen 2.46 写 objectVersion 77，runner 是 Xcode 15.4 读不了 | 钉 2.38.0（objectVersion 56）；CI 里用 curl 下 release zip，不用 brew |
| 6 | 10 个屏 `State` 重复声明 `var scroll`（基类 `ScreenState` 已有）| 删除子类那 10 行 |
| 7 | `LicaiScreen.applyEdit` 用 `state.xxx`（该扩展里应是 `self.`）| 改 `self.` |
| 8 | Router 逃逸闭包里访问 `host` / `showToast` 缺 `self.` | 加 `self.`；但 `guard let host` 之后的几行必须用局部 `host` |
| 9 | `allCards[safe: index]`（Swift 无此下标）| `indices.contains(index)` + 下标 |
| 10 | `subtitle.isEmpty()`（Java 写法）| `isEmpty` 是属性 |
| 11 | `Int + String`（PayMsgScreen:236）| `String(...) +` |
| 12 | 异构数组字面量（ReceiptDetailScreen:186/191）| 加 `: [Double]` / `: [Int]` |
| 13 | `0x9e3779b97f4a7c15` 溢出 Int64（CollectMoneyScreen:366）| `qrSeed` 与 `state` 改 `UInt64` |
| 14 | `Keychain.set(_:forKey:)` 标签不符（实现是 `set(_:for:)`）| 改 `for:` |
| 15 | `multiGradient(colors:)` 要 `[UIColor]` 但传了 `[UInt32]` | `colors.map { Draw.color($0) }` |
| 16 | `let cw = (1140 - 3 * 20) / 4` 被推断成 `Int`（BillAnalytics 多处）| 常量显式标 `CGFloat`（`cw` / `tw`）—— 这也是「unable to type-check in reasonable time」的根因 |

## 四、工程关键事实（别改错）

- `ios/project.yml`：iOS 15.0 / `com.luoyun.doutu` / `MARKETING_VERSION 1.0.0` /
  `CURRENT_PROJECT_VERSION 10000` / 不签名 / 仅 iPhone / 竖屏 / `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`
- runner：`macos-14`，Xcode **15.4**，Swift 5.10，SDK iPhoneOS 17.5
- 产物：`ios/Doutu-unsigned.ipa`，artifact 名 `Doutu-unsigned-ipa`
- 图标：`ios/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`
  （由 Android `mipmap-xxxhdpi/ic_launcher_doutu.png` 放大而来，RGB 无 alpha）
- 字体：`ios/Resources/Fonts/ArchivoSemiCondensed-Semibold.ttf`（代码按这个名字找）
- 364 个 imageset 的 `Contents.json` 全部完好

## 五、本机可跑的验证

```powershell
cd D:\AAA\doutu
node _ios/verify-all.mjs      # 21 项，含 217 条静态体检
node _ios/verify-ci.mjs       # 29 项，CI 配置自洽
node _ios/ci-status.mjs       # 云端最近一次构建结论
```

注意：这些静态检查全绿 **不等于** 能编译（曾经 44 个编译错误时它们也是绿的）。
真正的判据只有 `ci-logs/status.txt`。

## 六、装到 iPhone 的步骤（摘要，详见 `ios/INSTALL-IPA.md`）

1. Actions → build-ios-ipa → 最近一次绿勾 → Artifacts 下载 `Doutu-unsigned-ipa`
2. Windows 装 **iTunes（apple.com 桌面版）+ Sideloadly**，插 iPhone 点「信任此电脑」
3. Sideloadly 填 Apple ID（**开双重认证要填 App 专用密码**），拖入 ipa，Start
4. 手机：设置 → 通用 → VPN 与设备管理 → 信任
5. iOS 16+ 还要：设置 → 隐私与安全性 → 开发者模式 → 打开 → 重启
6. 免费账号 7 天过期，到期用 Sideloadly 再点一次 Start 重签（数据保留）；同时最多 3 个自签 App

## 七、已知的未验证风险（装机后优先看这几处）

1. **WebView 白屏** —— 小微主页是 Vite 产物，按文件夹引用打包，`app://` 下 ES module 能否加载是唯一没验证过的技术点；退路是改成本机回环 HTTP 服务
2. **闪退** —— 最可能是素材名/字体注册；崩溃日志在 设置 → 隐私与安全性 → 分析数据 里（`Doutu` 开头）
3. **界面错位** —— 把截图发我，`_ios/compare_shots.py` 可做逐块几何比对（已有 2 屏比到 3px）
4. 摄像头扫码只在真机能验（模拟器没有相机）

## 八、给新会话的开场白（可直接复制）

> 接手 `D:\AAA\doutu` 的 iOS 移植收尾。编译已在 GitHub Actions 里跑通（`ci-logs/status.txt` 为
> `status: success`，产物约 31 MB 的 `Doutu-unsigned.ipa`）；本机是 Windows，没有 macOS。
> 现在处于「装机验收」阶段：装机指引 `ios/INSTALL-IPA.md`，验收清单 `ios/DEVICE-TEST.md`。
> 改代码后的流程是 `git push` → `node _ios/ci-status.mjs --wait` → 看 `ci-logs/build.log` 的
> `error:` 行。不要删除 CI 里的「失败写日志 / 成功写状态」两步。

## 九、修复脚本清单（`_ios/`，历史归档）

`fix-duplicate-scroll.mjs`、`fix-compile-round2.mjs`、`fix3.mjs`、`fix4.mjs`、`fix5.mjs`、
`fix-ios-workflow.mjs`（同步两份工作流）；新增 `ci-status.mjs`（查云端构建结论）。
