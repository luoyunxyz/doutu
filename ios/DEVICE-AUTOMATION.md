# 真机无人值守流水线（2026-10-18 打通）

从「改完代码」到「手机上跑起来并截图」全自动，**不需要用户点手机**：

```powershell
# 1) CI 出包（推 main 后自动触发）
node _ios/ci-status.mjs --wait

# 2) 取 artifacts 分支的 IPA
git fetch origin refs/heads/artifacts:refs/remotes/origin/artifacts --force
git worktree add D:\AAA\ipa\_artifacts origin/artifacts --detach
Copy-Item D:\AAA\ipa\_artifacts\Doutu-unsigned.ipa D:\AAA\ipa\share\ -Force

# 3) 推进全能签并用它的 API 直接签名
#    POST /api/upload/init → /api/upload/chunk → /api/upload/complete
#    POST /api/import-from-download {"path":"UserDownloads/Doutu-unsigned.ipa"}
#    POST /api/sign/start  { path: "Resources/IPA/Doutu-unsigned.ipa", config: {...默认值, certificate: {certId, mobileProvisionId}} }
#    GET  /api/sign/status?taskId=…
# 注意：config 里必须带 certificate 对象，否则报「缺少 certificate.certId」

# 4) 下载已签名包 → 数据线安装
python -m pymobiledevice3 apps install D:\AAA\ipa\signed-latest.ipa

# 5) 启动并截图
python -m pymobiledevice3 developer dvt pkill Doutu
python -m pymobiledevice3 developer dvt launch com.luoyun.doutu
python -m pymobiledevice3 developer screenshot out.png      # 含水印（窗口级）
```

## 两种远程驱动方式

| 方式 | 用法 | 适用 |
|---|---|---|
| 调试遥控（推荐） | 写 `D:\AAA\ipa\share\cmd.json` 为 `{"seq":N,"token":"..."}`，等 3~4 秒，截图落在 `share\shots\shot-<seq>-*.png` | 页内跳转、逐屏巡检 |
| 启动令牌 | 把 token 写进 `Documents/demo.txt`，然后 pkill + launch | 冷启动状态、首启页面 |

两种截图都只拍 `hostView`（内容层），**不含水印**；要验水印用 `developer screenshot`。

## 只读诊断

```powershell
python -m pymobiledevice3 apps pull com.luoyun.doutu Documents/crash.log crash.log
python -m pymobiledevice3 apps query com.luoyun.doutu
```
crash.log 里每帧都有 `WM check activated=… hidden=…` / `WM attach frame=… text=…`。
