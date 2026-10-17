# 怎么把它装到 iPhone 上（无 Mac 版）

Windows 上**不可能**直接产出 iOS 安装包 —— 需要 Xcode 工具链。所以走云端 CI：
GitHub 的 macOS runner 编译出**未签名 IPA**，再用「万能签」类工具用你自己的 Apple ID 重签安装。

整个过程只有 4 步，其中 3 步在你的浏览器里完成。

---

## 第 1 步：建一个空仓库（1 分钟）

1. 打开 https://github.com/new
2. Repository name 随便取（例如 `doutu`），**Private 或 Public 都可以**
   - Private 也能跑 Actions（每月 2000 分钟免费额度，这个工程一次约 5-8 分钟）
3. **不要**勾选 "Add a README file"（要一个空仓库）
4. 点 Create repository

## 第 2 步：把本地代码推上去（复制粘贴两条命令）

在 `D:\AAA\doutu` 里打开 PowerShell，把 `<你的用户名>/<仓库名>` 换成实际的：

```powershell
cd D:\AAA\doutu
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

> 仓库已经初始化好并提交过一次（commit `d5388e2`，2,378 个文件 / 103 MB），
> `.gitignore` 已经把备份、构建产物、截图这些东西排除了。
> 如果提示要登录，用 GitHub 的 **Personal Access Token** 当密码
> （Settings → Developer settings → Personal access tokens → 勾 `repo` 和 `workflow`）。

## 第 3 步：跑一次构建（点一下）

1. 打开你的仓库 → **Actions** 标签页
2. 左侧选 **build-ios-ipa**
3. 右侧点 **Run workflow** → **Run workflow**
4. 等 5-8 分钟。绿勾 = 成功

> 推代码时也会自动触发一次（`on: push`），所以可能不用手点，直接看运行记录即可。

## 第 4 步：下载 + 签名 + 安装

**成功时**：在该次运行页面的底部 **Artifacts** 里下载 `Doutu-unsigned-ipa`
→ 解压得到 `Doutu-unsigned.ipa`。

**失败时**：下载 `build-log` 这个 artifact（里面是 `build.log`），
把它发给我 —— 日志会直接指出要改的几十行。

**签名安装**（未签名 IPA 不能直接装）三选一：

| 工具 | 说明 |
|---|---|
| **Sideloadly** | Windows 可用，填 Apple ID 自动签名（免费账号 7 天有效）|
| **爱思助手** | 国内常用，工具箱 → IPA 签名 |
| **AltStore / SideStore** | 需要装 AltServer，稍麻烦但可自动续签 |

签名后的包用 USB 装到 iPhone 即可（免费开发者账号需要设备信任：
设置 → 通用 → VPN 与设备管理 → 信任你的 Apple ID）。

---

## 装上去之后该看什么

第一次运行最可能出问题的地方，按概率排序：

1. **闪退**：看 `Console.app` / Xcode Devices 里的崩溃日志，通常是某个素材名或字体注册失败
2. **界面错位**：把截图发我，我有 `_ios/compare_shots.py` 可以做逐块几何比对（已有 2 屏做过，偏差 3px）
3. **WebView 白屏**：`app://` 下 ES module 能否加载是唯一未验证的技术点，退路是改成回环 HTTP 服务

请重点确认这几屏（都是 1:1 复刻的重点）：

- 启动首页（三栏 + 四平台页签）
- 我页 / 娱乐页（这两屏已经和 Android 真机截图比对到 3px）
- 理财页 → 点数字能改、点底部五格能切模块
- 芝麻信用 → 长按模块、左右拖动轮播
- 支宫格第 7 格 → 支付成功页
- 余额 → 提现 → 输 6 位密码 → 提现成功页

---

## 我这边能做的和不能做的

| 能 | 不能 |
|---|---|
| 19 项静态检查（错误率已压到最低）| 编译（没有 macOS）|
| 协议层打真实生产 20 项 | 产出 IPA（同上）|
| 逐屏对着 Android 源码 1:1 核对 | 真机界面验证（只有 2 屏有证据）|
| 你贴日志后快速定位并修 | 替你登录 GitHub / 签名 |

**所以现在卡住的不是「还不完善」，而是「必须有人跑一次编译器」。**
那一次运行之后，我才能从「静态能查的都查了」进入「按真实报错逐个修」。
