<#
  device-deploy.ps1 —— 把最新 CI 产物装到真机（全自动，无需用户在手机上操作）

  流程：
    origin/artifacts 取 IPA → 推到全能签 → 调 /api/sign/start 签名
    → 下载已签名包 → pymobiledevice3 apps install → 杀进程重启

  前置：
    * 全能签的「无线传输」页面处于前台（服务运行中）
    * 手机用数据线连着，信任过这台电脑
    * python + pymobiledevice3 已安装

  用法：
    pwsh -File _ios/device-deploy.ps1                 # 用 origin/artifacts 最新包
    pwsh -File _ios/device-deploy.ps1 -SkipFetch      # 不重新取包（复用现有 ipa）
    pwsh -File _ios/device-deploy.ps1 -NoLaunch       # 装完不重启 App
#>
param(
    [string]$SignerBase = 'http://192.168.1.170:8080',
    [string]$WorkDir    = 'D:\AAA\ipa',
    [string]$CertId     = '9FTBM39AHP_1DAC02529F803C76706EF64D983EE18B.p12',
    [string]$ProvId     = '9FTBM39AHP_1DAC02529F803C76706EF64D983EE18B.mobileprovision',
    [string]$CertName   = 'iPhone Distribution: ruixian chen (9FTBM39AHP)',
    [string]$BundleId   = 'com.luoyun.doutu',
    [switch]$SkipFetch,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Continue'
$env:HTTP_PROXY = ''; $env:HTTPS_PROXY = ''; $env:NO_PROXY = '*'
$repo = Split-Path -Parent $PSScriptRoot
$unsigned = Join-Path $WorkDir 'share\Doutu-unsigned.ipa'
$signed   = Join-Path $WorkDir 'signed-latest.ipa'

function Fail($msg) { Write-Host "✗ $msg" -ForegroundColor Red; exit 1 }

# ---------- 1. 取最新产物 ----------
if (-not $SkipFetch) {
    Write-Host '→ 取 origin/artifacts 最新包' -ForegroundColor Cyan
    Push-Location $repo
    git fetch origin 'refs/heads/artifacts:refs/remotes/origin/artifacts' --force --quiet
    $wt = Join-Path $WorkDir '_artifacts'
    if (Test-Path $wt) { git worktree remove $wt --force 2>$null | Out-Null }
    git worktree add $wt origin/artifacts --detach --quiet 2>&1 | Out-Null
    Pop-Location
    Copy-Item (Join-Path $wt 'Doutu-unsigned.ipa') $unsigned -Force
}
if (-not (Test-Path $unsigned)) { Fail "找不到 $unsigned" }
$f = Get-Item $unsigned
Write-Host ("  包大小 {0:N2} MB" -f ($f.Length / 1MB))

# ---------- 2. 推给全能签 ----------
if (-not (Test-NetConnection -ComputerName ([uri]$SignerBase).Host -Port ([uri]$SignerBase).Port -WarningAction SilentlyContinue).TcpTestSucceeded) {
    Fail "全能签 $SignerBase 不通 —— 请打开它的「无线传输」页面并保持前台"
}

Write-Host '→ 上传到全能签' -ForegroundColor Cyan
$initBody = @{ fileName = 'Doutu-unsigned.ipa'; totalSize = $f.Length; fresh = $true } | ConvertTo-Json
$tmp = Join-Path $env:TEMP 'dpl_init.json'
[IO.File]::WriteAllText($tmp, $initBody, (New-Object System.Text.UTF8Encoding($false)))
$init = (& curl.exe -s --max-time 25 -X POST "$SignerBase/api/upload/init" -H 'Content-Type: application/json' --data-binary "@$tmp") | ConvertFrom-Json
if (-not $init.ok) { Fail "upload/init 失败：$($init | ConvertTo-Json -Compress)" }

$uid = $init.uploadId; $cs = [int]$init.chunkSize
$bytes = [IO.File]::ReadAllBytes($unsigned); $total = $bytes.Length; $off = 0
$part = Join-Path $env:TEMP 'dpl_chunk.bin'
while ($off -lt $total) {
    $len = [Math]::Min($cs, $total - $off)
    $buf = New-Object byte[] $len
    [Array]::Copy($bytes, $off, $buf, 0, $len)
    [IO.File]::WriteAllBytes($part, $buf)
    $null = & curl.exe -s --max-time 120 -X POST "$SignerBase/api/upload/chunk" `
        -F "uploadId=$uid" -F "start=$off" -F "end=$($off + $len - 1)" `
        -F "total=$total" -F "chunk=@$part;filename=chunk.part"
    $off += $len
}
$cp = Join-Path $env:TEMP 'dpl_complete.json'
[IO.File]::WriteAllText($cp, "{`"uploadId`":`"$uid`"}", (New-Object System.Text.UTF8Encoding($false)))
$null = & curl.exe -s --max-time 90 -X POST "$SignerBase/api/upload/complete" -H 'Content-Type: application/json' --data-binary "@$cp"

$imp = Join-Path $env:TEMP 'dpl_import.json'
[IO.File]::WriteAllText($imp, '{"path":"UserDownloads/Doutu-unsigned.ipa"}', (New-Object System.Text.UTF8Encoding($false)))
$impRes = (& curl.exe -s --max-time 180 -X POST "$SignerBase/api/import-from-download" -H 'Content-Type: application/json' --data-binary "@$imp") | ConvertFrom-Json
if (-not $impRes.ok) { Fail "import-from-download 失败：$($impRes | ConvertTo-Json -Compress)" }
Start-Sleep -Seconds 4

# ---------- 3. 签名（config 必须带 certificate，否则报「缺少 certificate.certId」）----------
Write-Host '→ 调用签名接口' -ForegroundColor Cyan
$defaults = (& curl.exe -s --max-time 15 "$SignerBase/api/sign/defaults") | ConvertFrom-Json
$cfg = @{}
foreach ($p in $defaults.PSObject.Properties) { if ($p.Name -ne 'ok') { $cfg[$p.Name] = $p.Value } }
$cfg['removeDeviceRestrictions'] = $false
$cfg['forceMinVersion10'] = $false
$cfg['appName']  = '斗图去水印'
$cfg['bundleId'] = $BundleId
$cfg['version']  = '1.0.0'
$cfg['certificate'] = @{ certId = $CertId; mobileProvisionId = $ProvId; displayName = $CertName }
$startBody = @{ path = 'Resources/IPA/Doutu-unsigned.ipa'; config = $cfg } | ConvertTo-Json -Depth 6
$sb = Join-Path $env:TEMP 'dpl_sign.json'
[IO.File]::WriteAllText($sb, $startBody, (New-Object System.Text.UTF8Encoding($false)))
$start = (& curl.exe -s --max-time 300 -X POST "$SignerBase/api/sign/start" -H 'Content-Type: application/json' --data-binary "@$sb") | ConvertFrom-Json
if (-not $start.taskId) { Fail "sign/start 失败：$($start | ConvertTo-Json -Compress)" }

$st = $null
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 4
    $st = (& curl.exe -s --max-time 15 "$SignerBase/api/sign/status?taskId=$($start.taskId)") | ConvertFrom-Json
    if ($st.state -eq 'done')   { break }
    if ($st.state -eq 'failed') { Fail "签名失败：$($st.error)" }
}
if ($st.state -ne 'done') { Fail '签名超时' }
Write-Host "  已签名：$($st.outputPath)"

# ---------- 4. 下载 + 安装 ----------
$outRel = $st.outputPath -replace '\\', '/'
& curl.exe -s --max-time 300 -o $signed "$SignerBase/api/download?path=./$outRel"
if (-not (Test-Path $signed)) { Fail '下载已签名包失败' }
Write-Host ("→ 数据线安装 {0:N2} MB" -f ((Get-Item $signed).Length / 1MB)) -ForegroundColor Cyan
$installOut = python -m pymobiledevice3 apps install $signed 2>&1 | Out-String
if ($installOut -notmatch 'succeed') { Fail "安装失败：$installOut" }
Write-Host '  Installation succeed.' -ForegroundColor Green

# ---------- 5. 重启 ----------
if (-not $NoLaunch) {
    $null = python -m pymobiledevice3 developer dvt pkill Doutu 2>&1
    Start-Sleep -Seconds 2
    $launchOut = python -m pymobiledevice3 developer dvt launch $BundleId 2>&1 | Out-String
    if ($launchOut -match 'pid') { Write-Host "  已启动：$($launchOut.Trim())" -ForegroundColor Green }
    Start-Sleep -Seconds 4
}
Write-Host '✓ 部署完成' -ForegroundColor Green
