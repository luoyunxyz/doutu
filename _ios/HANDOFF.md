# 交接说明（新会话从这里接手）

> 记录时间：编译收敛第 10 轮。仓库 `git@github.com:luoyunxyz/doutu.git`，分支 `main`。
> 本机：Windows，**无 macOS / 无 Swift 工具链**（已验证排除）。安装在 iPhone 上只能靠 GitHub Actions 出未签名 IPA + Sideloadly 签名。

## 一、目标

把 Android 客户端 1:1 移植成原生 Swift + CoreGraphics（27 屏，15,328 行 / 64 文件，全部已完成），并**在 iPhone 上装上**。
当前卡点只有一件事：**让它编译通过**。

## 二、CI 日志闭环（最重要的机制）

`.github/workflows/ios-build.yml` 里有两个关键设计，**不要删**：

1. 失败时把日志复制到 `ci-logs/`（`env.log` / `xcodegen.log` / `build.log`）并 commit + push 回 `main`
   （`permissions: contents: write`，用 GITHUB_TOKEN，不会再触发工作流）
2. 所以新会话**不需要人贴日志**：

```powershell
cd D:\AAA\doutu
git fetch origin
git show origin/main:ci-logs/build.log | Select-String 'error:'
```

每轮流程：`改代码 → git commit → git push → 等 2-5 分钟 → git fetch → 读 ci-logs → 再改`

## 三、已修好的问题（不要再改回去）

| # | 问题 | 修法 |
|---|---|---|
| 1 | `import CommonCrypto`（无 bridging header 不是可用模块）| 改 CryptoKit；`CCHmac`→`HMAC<SHA256>`；PBKDF2 按 RFC 8018 手写（已用 Node 镜像验证与 openssl 逐字节一致）|
| 2 | `AuthService` / `Playbook` 缺 `import UIKit` | 补上（Swift import 按文件生效）|
| 3 | `project.yml` 没有 `schemes:` | 补 `schemes: Doutu:`（XcodeGen 不自动生成）|
| 4 | 缺 `GENERATE_INFOPLIST_FILE: YES` | 补上，否则 `INFOPLIST_KEY_*` 不生效 |
| 5 | XcodeGen 2.46 写 objectVersion **77**，runner 是 **Xcode 15.4** 读不了 | **钉 2.38.0**（objectVersion 54）；CI 里用 curl 下 release zip，不用 brew |
| 6 | 10 个屏 `State` 重复声明 `var scroll`（基类 `ScreenState` 已有）| 删除子类那 10 行（`_ios/fix-duplicate-scroll.mjs`）|
| 7 | `LicaiScreen.applyEdit` 用 `state.xxx`（该扩展里应是 `self.`）| 改 `self.` |
| 8 | Router 逃逸闭包里访问 `host` / `showToast` 缺 `self.` | 加 `self.`（96 行）；但 `guard let host` 之后的 5 行必须用局部 `host`（63/64/70/120/128 行）|
| 9 | `allCards[safe: index]`（Swift 无此下标）| `indices.contains(index)` + 下标 |
| 10 | `subtitle.isEmpty()`（Java 写法）| `isEmpty` 是属性 |
| 11 | `Int + String`（PayMsgScreen:236）| `String(...) +` |
| 12 | 异构数组字面量（ReceiptDetailScreen:186/191）| 加 `: [Double]` / `: [Int]` |
| 13 | `0x9e3779b97f4a7c15` 溢出 Int64（CollectMoneyScreen:366）| `qrSeed` 与 `state` 改 `UInt64` |

## 四、当前未修的 17 个错误（新会话从这里开始）

```
Screens/BillAnalyticsScreen.swift:219   cannot convert '[UInt32]'  →  multiGradient 需要 [UIColor]
                                        修法：colors.map { Draw.color($0) }
Screens/BillAnalyticsScreen.swift:655/656/660/661/706/732/737/739/742/
                                  745/750  Int → CGFloat（cw / ch / cy0 / x0 等常量声明成了 Int）
                                        修法：把这些常量声明改 CGFloat，或在调用处 CGFloat(...)
Net/ApiClient.swift:110/121            Keychain.set(_:forKey:) 参数标签不符（实现是 set(_:key:) 之类）
                                        修法：按 Keychain 的实际签名调用
```

## 五、工程关键事实（别改错）

- `ios/project.yml`：iOS 15.0 / `com.luoyun.doutu` / `MARKETING_VERSION 1.0.0` / `CURRENT_PROJECT_VERSION 10000` / 不签名 / 竖屏
- runner：`macos-14`，Xcode **15.4**，Swift 5.10，SDK iPhoneOS 17.5
- 产物：`ios/Doutu-unsigned.ipa`，artifact 名 `Doutu-unsigned-ipa`
- 字体：`ios/Resources/Fonts/ArchivoSemiCondensed-Semibold.ttf`（代码按这个名字找，不是 `zhi_asset_number.ttf`）
- 364 个 imageset 的 `Contents.json` 全部完好（已验证）

## 六、本机可跑的验证（21 项，全部通过）

```powershell
cd D:\AAA\doutu
node _ios/verify-all.mjs      # 21 项，含 217 条静态体检
node _ios/verify-ci.mjs       # 29 项，CI 配置自洽
```

注意：**这些检查全绿 ≠ 能编译**。已经证明过多次（44 个编译错误时它们也是绿的）。

## 七、装到 iPhone 的步骤（编译通过后）

1. Actions 页面下载 `Doutu-unsigned-ipa`
2. Windows 装 Sideloadly，插 iPhone，填自己的 Apple ID，拖入 ipa
3. 设置 → 通用 → VPN 与设备管理 → 信任
4. iOS 16+ 还要 设置 → 隐私与安全性 → 开发者模式
5. 免费账号 7 天过期，到期重签

## 八、给新会话的开场白（可直接复制）

> 接手 `D:\AAA\doutu` 的 iOS 移植收尾工作。已建好 GitHub Actions 构建链路，失败时 CI 会把日志提交到仓库的 `ci-logs/`。
> 现在只剩编译错误要收敛，先 `git pull` 然后读 `ci-logs/build.log`，有 17 个错误待修（清单见 `_ios/HANDOFF.md`）。
> 目标：修到 `build-ios-ipa` 成功产出 `Doutu-unsigned-ipa` artifact。每轮改完 `git push`，等 CI，再读日志，直到成功。

## 九、修复脚本清单（`_ios/`）

`fix-duplicate-scroll.mjs`（删重复 scroll）、`fix-compile-round2.mjs`（state→self、self.host、isEmpty）、
`fix3.mjs`（Int+String、数组标注）、`fix4.mjs`（局部 host）、`fix5.mjs`（UInt64 种子）、
`fix-ios-workflow.mjs`（同步两份工作流）。
