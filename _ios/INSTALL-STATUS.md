# 装机进度与卡点（2026-10-17 收尾）

> 目标机型：**iPhone 12（蓝色 128GB）· iOS 14.3** · UDID `00008101-000A71912110001E`
> 结论先说：**App 本身已经完全就绪，卡在 Apple 的开发者认证（GSA 503）**，与代码、包体、网络都无关。

---

## 一、已经完成的部分 ✅

| 事项 | 状态 | 证据 |
|---|---|---|
| 27 屏 Swift 移植（15,328 行 / 64 文件）| ✅ | 静态体检 21 项全绿 |
| 云端编译（macos-14 / Xcode 15.4）| ✅ | `ci-logs/status.txt` = `status: success` |
| **支持 iOS 14.0+**（原为 15.0）| ✅ | 产物 `MinimumOSVersion = 14.0` |
| 未签名包 `Doutu-unsigned.ipa`（33.3 MB）| ✅ | artifacts: `Doutu-unsigned-ipa` |
| ad-hoc 签名包 `Doutu-adhoc.ipa`（33.4 MB）| ✅ | artifacts: `Doutu-adhoc-ipa`，含 `_CodeSignature/` |
| IPA 结构修正（`PayloadAdhoc/` → `Payload/`）| ✅ | `ci-logs/info-plist.log` 里 `unzip -l` 自检 |
| App 图标 1024 / Scene Manifest / 字体 | ✅ | `CFBundleIcons = AppIcon`、`UIApplicationSceneManifest` 存在 |
| iPhone 识别 + 配对信任 | ✅ | `Apple Mobile Device Service` 运行、`Lockdown` 配对记录存在 |
| Apple ID 注册 + 双重认证 + 开发者协议 | ✅ | 账号 `2259475102@qq.com`，1 台受信任设备 |
| TrollInstallerX 1.0.3（MinOS 14.0）就绪 | ✅ | `D:\AAA\ipa\share\TrollInstallerX.ipa`（6.6 MB）|

**iOS 14.3 兼容改了两处**（都已提交）：
1. `Net/DtwTransport.swift` —— `URLSession.shared.data(for:)`（iOS 15+）换成 `withCheckedThrowingContinuation` 包 `dataTask`
2. `Screens/LoginView.swift` —— `@Environment(\.dismiss)`（iOS 15+）换成 `\.presentationMode`
3. `project.yml` —— `deploymentTarget.iOS: "14.0"`

---

## 二、当前唯一的卡点 ❌

```
Fatal Error
Install failed: Guru Meditation 0db732@291:3aea77
GSA returned a non-plist response (HTTP 503 Service Temporarily Unavailable)
— likely a network/TLS failure reaching gsa.apple.com
```

### 已排除的原因（逐项验过）

| 检查 | 结果 |
|---|---|
| DNS / TCP 443 到 `gsa.apple.com` | ✅ 17.157.64.66，可连 |
| 直连 TLS | ✅ TLS 1.3，真 Apple 证书 `CN=gsa.apple.com`（无劫持）|
| `developerservices2.apple.com` | ✅ 200/401 正常 |
| WinHTTP 代理层 | ✅ `Direct access (no proxy)` |
| WinINET 系统代理（Clash `127.0.0.1:7877`）| 关掉后重试，**仍 503** → 不是代理 |
| hosts 篡改 / IPv6 干扰 | ✅ 无（gsa 无 AAAA 记录，hosts 干净）|
| 系统时间（TLS 依赖）| ✅ 正确 |
| Anisette 组件（`an\CoreFP.dll` 等）| ✅ Sideloadly 自带，2026-09-29 |
| 开发者协议是否接受 | ✅ 已接受，仍 503 |
| Sideloadly 缓存/旧 token | ✅ 已备份并清空重试，仍 503 |
| 爱思助手签名 | ❌ 需要 p12 证书，而证书同样要靠这道认证生成 |

### 判断

**Apple 侧对新注册账号的 GSA 有宽限期/限流**（同时今天连续尝试多次，也可能叠加了限流）。
属于**账号侧**问题，本机无法再"修"，只能等或用别的方式换一条路。

---

## 三、恢复原状的操作（我改动过的东西）

| 我改的 | 现在状态 | 恢复方式 |
|---|---|---|
| 系统代理（Clash）| **已恢复开启** | — |
| Sideloadly 缓存 | 已清空 | 备份在 `D:\AAA\ipa\sideloadly-backup` |
| 局域网上传服务（python 8000）| 会话结束后会停 | 双击 `D:\AAA\ipa\start-share.bat` 重新开 |
| 局域网内容 | 保留 | `D:\AAA\ipa\share\`（含两个 ipa + TrollInstallerX + 安装面板 `index.html`）|

---

## 四、明天 / 接下来怎么做（按成功率排序）

### 方案 1（最稳）：等 24 小时后用同一套再来一次
1. 打开 Sideloadly，拖入 `D:\AAA\ipa\share\TrollInstallerX.ipa`
2. Apple ID 填 `2259475102@qq.com` → **Start** → 输密码 + 6 位验证码
3. 成功后：手机 **设置 → 通用 → VPN 与设备管理 → 信任**
4. 打开桌面 **TrollInstallerX** → **Install TrollStore**
5. 手机 Safari 打开局域网面板 → 下载 `Doutu-adhoc.ipa` → 分享给 TrollStore → **Install**
6. 完成，**永久有效**

### 方案 2：换网络（绕过可能的边缘限流）
把电脑连到**手机热点**（另一部手机）或别的 Wi-Fi，再点 Start。

### 方案 3：换一个"老" Apple ID
借家人/朋友**已经用了一段时间**的 Apple ID（不需要付费账号），新注册账号最容易撞 503。

### 方案 4：Sideloadly 设置里切「远程 Anisette」
齿轮 → Anisette 从 Local 改 Remote。设置弹窗我这边点不开（副屏 + 焦点问题），需要手动点。

---

## 五、复盘：今天试过但（对这台机器）不可行的路

| 路线 | 为什么不行 |
|---|---|
| TrollHelperOTA（itms-services 在线装 TrollStore）| 那个包无 `embedded.mobileprovision`，共享证书已被吊销 →「证书无效」 |
| 爱思助手 IPA 签名 | 需要 p12 + 描述文件，仍要先过 Apple 认证 |
| 无线安装（Sideloadly Wi-Fi）| 需先 USB 配对，且签名问题不变 |
| TestFlight | 需要 $99/年付费开发者账号 |
