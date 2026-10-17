# 装到 iPhone 上（全程在 Windows 上做，不需要 Mac）

> 现状：GitHub Actions 已经能稳定出包（`ci-logs/status.txt` 里 `status: success`，
> 产物 `Doutu-unsigned.ipa` 约 31 MB）。剩下的事只有「下载 → 用你的 Apple ID 签名 → 装」。

一句话流程：

```
GitHub Actions（macOS 编译，出未签名 IPA）
        ↓ 下载
Sideloadly（Windows，用你的 Apple ID 重签）
        ↓ USB
iPhone（信任证书 + 开发者模式）
```

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
