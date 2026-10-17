# 装到 iPhone 上（全程在 Windows 上做，不需要 Mac）

> 现状：GitHub Actions 已经能稳定出包（`ci-logs/status.txt` 里 `status: success`，
> 产物 `Doutu-unsigned.ipa` 约 31 MB）。剩下的事只有「下载 → 用你的 Apple ID 签名 → 装」。

一句话流程：

```
GitHub Actions（macOS 编译，出未签名 IPA）
        ↓ 下载
Sideloadly（Windows，用你的 Apple ID 重签）
        ↓ USB（或 Wi-Fi，但首次要在 iTunes 里用线配对一次）
iPhone（信任证书 + 开发者模式）
```

> 没有数据线？先看下面的 **§0**，四种方案都列了代价。

---

## 0. 没有数据线怎么办？（先看这一节）

**坏消息**：Sideloadly / AltStore / Xcode 的「无线安装」都要**先用线插一次**——
Apple 的设备配对是走 USB 的，要在 iTunes 里勾上「通过 Wi-Fi 同步」之后才认网络上的这台电脑
（[Sideloadly 官方 FAQ](https://github.com/SideloadlyiOS/Sideloadly-Download) 也是这么写的）。
所以「一次线都不插、还想用电脑签名」这条路不存在。

### 方案 A：只插一次线（推荐，最省事）

1. 找一根线（Lightning 或 USB-C，安卓线只要接头对也能用），插一次 iPhone
2. 打开 iTunes → 设备 → 摘要 → 选项 → 勾 **「通过 Wi-Fi 与此 iPhone 同步」** → 应用/同步
3. **拔线**。之后 Sideloadly 列表里会直接出现这台手机，往后 7 天一次的重签**全程无线**

无线装的时候要满足：电脑和手机**同一个 Wi-Fi**、手机**亮屏**（Sideloadly 日志会提示）、
路由器的「AP 隔离/访客网络」关掉、Windows 服务里 `Apple Mobile Device Service` 在跑。

### 方案 B：完全不碰线（手机上自己签）

1. 先在手机 Safari 里 OTA 装一个手机端签名工具：**轻松签 / ESign**、**全能签**、**Feather**
2. 把 `Doutu-unsigned.ipa` 传到手机上（**iCloud 云盘**网页版上传、微信/QQ 发文件、网盘都行，
   全程不需要线）
3. 在工具里导入证书（用 Apple ID 申请的个人证书，或工具自带的共享证书）→ 选择 IPA → 签名安装

缺点：签名工具本身多半靠共享证书装，证书被吊销就得重装工具；界面和步骤比 Sideloadly 繁琐。
适合「手边真的没有线、就想先看一眼界面」。

### 方案 C：OTA 云签名（最无脑，但有风险）

把 IPA 上传到**万能签 / 超级签**这类网站 → 它给你一个安装链接 → 手机 Safari 打开就装。
不用电脑、不用线。缺点：用的是**共享企业证书**，随时可能被 Apple 吊销（一吊销 App 就打不开）；
多数要收费或有广告；有些服务会改你的 bundle id。

### 方案 D：TestFlight（最正规，完全无线，但要 $99/年）

有付费 Apple 开发者账号的话，我可以把 CI 加一步「自动上传 TestFlight」：
推代码 → 云端编译 → TestFlight 里出现新版本 → 手机点「安装」（**不需要电脑、不需要线**），
每个构建 **90 天**有效，还能加最多 100 个外部测试者。
需要你提供 **App Store Connect API Key**（.p8 + Key ID + Issuer ID）存进仓库 Secrets。

| 方案 | 需要线 | 需要花钱 | 有效期 | 稳定性 |
|---|---|---|---|---|
| A 一次性插线 + Sideloadly 无线 | 一次 | 免费 | 7 天（重签即可）| 高 |
| B 手机端自签工具 | 不用 | 免费（共享证书）| 7 天~随时吊销 | 中 |
| C OTA 云签名 | 不用 | 多数收费 | 看证书 | 低 |
| D TestFlight | 不用 | $99/年 | 90 天 | 最高 |

---

## 1. 下载 IPA

1. 打开 <https://github.com/luoyunxyz/doutu/actions>（仓库是私有的，需要你自己的账号登录）
2. 左侧点 **build-ios-ipa**
3. 点最上面那次**带绿勾**的运行
4. 页面拉到最底部 **Artifacts** → 点 **Doutu-unsigned-ipa**
5. 下载得到 `Doutu-unsigned-ipa.zip`，解压出 **`Doutu-unsigned.ipa`**

> GitHub 的 artifact 保留 **90 天**，过期了就在 Actions 里点 **Run workflow** 重跑一次（约 7 分钟）。
> 不想进网页也能核对构建结果：仓库里 `ci-logs/status.txt` 写着 `status: success` 就是成功，
> `status: failure` 则 `ci-logs/build.log` 里是完整报错。

---

## 2. Windows 准备（只做一次，约 10 分钟）

| 装什么 | 为什么 | 注意 |
|---|---|---|
| **iTunes**（<https://www.apple.com/itunes/download/win64>）| 提供 Apple 移动设备驱动，Sideloadly 靠它认手机 | ⚠️ **必须用 apple.com 的桌面版**，Microsoft Store 版驱动不全，Sideloadly 会感叹号 |
| **Sideloadly**（<https://sideloadly.io>）| 用你的 Apple ID 给未签名 IPA 重签并装进手机 | 免费软件 |
| 数据线 | USB 连接 | 插上后手机上点 **「信任此电脑」** |

打开 Sideloadly，左上角能看到你的 iPhone 型号 = 驱动 OK；
显示 `No device found` 就去查 iTunes 是不是装错了版本。

---

## 3. 签名 + 安装（每次出新包做一次，约 2 分钟）

1. **iPhone** 那一栏选中你的手机
2. **Apple account** 填你的 Apple ID（网易/QQ 邮箱都行，不需要付费开发者账号）
3. 把 `Doutu-unsigned.ipa` **拖进 IPA 框**（或点框选文件）
4. 点 **Start**
5. 弹出要密码时：如果账号开了双重认证，**不能填 Apple ID 登录密码**，要填
   **App 专用密码** —— 到 <https://appleid.apple.com> → 登录与安全 → **App 专用密码** → 生成一个（形如 `abcd-efgh-ijkl-mnop`），把它填进去
6. 等 1–3 分钟，Sideloadly 日志出现 `Installation complete` / `done` 即成功

> 想少登录几次：Sideloadly 里勾 **Remember** 记住账号；密码每次都建议重新生成专用密码。

---

## 4. 手机上第一次打开（不做会提示「无法验证 App」）

1. **设置 → 通用 → VPN 与设备管理 → 开发者 App** → 点你的 Apple ID → **信任**
2. **iOS 16 及以上还需要**：设置 → **隐私与安全性** → 拉到底 → **开发者模式** → 打开 → 手机会要求**重启** → 重启后确认
3. 回到桌面点「斗图去水印」图标

图标是自己一屏蓝色炸弹（1024 的 AppIcon 已随包打进去），如果显示白图标说明装的是旧包。

---

## 5. 免费账号的三个硬限制（务必知道）

| 限制 | 说明 | 怎么办 |
|---|---|---|
| **7 天过期** | 用免费 Apple ID 签的 App 第 7 天会打不开（点开闪退）| 到期用 Sideloadly 再点一次 **Start** 重签（覆盖安装，App 里的数据不丢）|
| **最多 3 个自签 App** | 同一台手机、同一账号同时只能有 3 个 | 删掉别的自签 App，或换一个 Apple ID |
| **一台手机同时只能一个账号** | 换账号要重签全部 | 固定用一个账号 |

想要**不过期**：付费开发者账号（$99/年，证书 1 年）；或者用 **SideStore / AltStore**
（在手机上自动 7 天续签，需要在电脑上跑 AltServer，稍麻烦）。

---

## 6. 报错对照表

| Sideloadly / 手机提示 | 原因 | 处理 |
|---|---|---|
| `Unable to install ... could not be verified` | 没在「VPN 与设备管理」里信任证书 | 走第 4 步 |
| `The maximum number of apps for free development profiles has been reached` | 免费账号 3 个 App 已满 | 删别的自签 App / 换账号 |
| `0xe8008015`、`0xe8008016`、`A valid provisioning profile ... not found` | 账号被风控、或 bundle id 已被别的签名占用 | Sideloadly 里点 **Advanced Options → Change Bundle ID** 改成 `com.luoyun.doutu2` 之类再装 |
| `0xe80000b7` / `device is locked` | 手机锁屏、没点信任、线/口不稳 | 解锁手机、换线/换 USB 口、重启 iTunes 服务 |
| 装上了但**点开闪退** | 证书过期、开发者模式没开、或首次启动崩溃 | 先看 7 天是否已过；再看 设置 → 隐私与安全性 → 分析数据 里有没有 `Doutu` 开头的崩溃日志，有就发我 |
| **白图标** | 装的是加 AppIcon 之前的旧包 | 重新下载最新 artifact 再签 |
| **启动黑屏** | SwiftUI 的 Scene Manifest 没生效（已修）| 装最新包；仍黑就发崩溃日志 |

---

## 7. 装好之后要验收什么

按 [`ios/DEVICE-TEST.md`](DEVICE-TEST.md) 走一遍（首页四页签 → 支宫格 12 格 → 娱乐页 →
理财页点数字改金额 → 余额提现输 6 位密码 → 扫码页摄像头预览）。
哪一条不对，把**屏幕截图**发我 —— 我这边有逐块几何比对脚本（已有两屏比到 3px 偏差）。

---

## 8. 以后每次改代码

```powershell
cd D:\AAA\doutu
git add -A; git commit -m "说明"; git push
```

推上去后 Actions 自动跑（`on: push` 命中 `ios/**`）。约 7 分钟后：

```powershell
git fetch origin
git show origin/main:ci-logs/status.txt     # status: success 就是好了
git show origin/main:ci-logs/build.log | Select-String 'error:'   # 失败时看这里
```

成功后重新下载 artifact → Sideloadly 再点一次 Start（**覆盖安装，App 里的数据保留**）。

> 不想等 GitHub 网页：`node _ios/ci-status.mjs` 会 fetch 一次并打印上面这些。

---

## 9. 替代签名工具（Sideloadly 装不上时）

| 工具 | 平台 | 说明 |
|---|---|---|
| **爱思助手** | Windows | 工具箱 → IPA 签名，国内网络更顺；同样是 Apple ID 自签，7 天限制一样 |
| **AltStore / SideStore** | Windows + iPhone | 能自动续签，但要在电脑常开 AltServer，首次配置麻烦 |
| **万能签 / 各类在线签名** | 网页 | 用的是别人的企业证书，可能随时吊销，只适合临时看界面 |
