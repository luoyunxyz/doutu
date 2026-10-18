#!/bin/bash
# 不依赖 GitHub Actions 的本地 macOS 构建脚本。
#
# 前置：
#   - macOS + Xcode 15.4（或兼容版本）
#   - XcodeGen 2.38.0（brew install xcodegen 或从 GitHub release 下载）
#   - 仓库已通过 git clone / git bundle 放到本机
#
# 用法：
#   bash scripts/build-ios-v2-mac.sh
#
# 产物：
#   ios_v2/Doutu-final-unsigned.ipa
#
# 注意：这是**未签名 IPA**，需要再用全能签/开发者证书签名安装。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/ios_v2"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "缺少 xcodegen。请安装 XcodeGen 2.38.0：brew install xcodegen" >&2
  exit 1
fi

echo "== xcodegen =="
xcodegen generate

echo "== xcodebuild =="
xcodebuild \
  -project DoutuV2.xcodeproj \
  -scheme DoutuV2 \
  -sdk iphoneos \
  -configuration Release \
  -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  build

APP="$(find build/Build/Products/Release-iphoneos -maxdepth 1 -name '*.app' | head -1)"
test -n "$APP" || { echo "没有找到 .app，编译失败" >&2; exit 1; }

echo "== package =="
rm -rf Payload Doutu-final-unsigned.ipa
mkdir -p Payload
cp -R "$APP" Payload/

PLIST="Payload/$(basename "$APP")/Info.plist"
# 与 CI 一致：补文件共享 + 图标声明
for kv in "UIFileSharingEnabled:bool:true" "LSSupportsOpeningDocumentsInPlace:bool:true"; do
  key=${kv%%:*}; rest=${kv#*:}; type=${rest%%:*}; val=${rest#*:}
  /usr/libexec/PlistBuddy -c "Delete :$key" "$PLIST" >/dev/null 2>&1 || true
  /usr/libexec/PlistBuddy -c "Add :$key $type $val" "$PLIST" || true
done
bash "$ROOT/_ios/v2-set-icons.sh" "$PLIST"

zip -qr Doutu-final-unsigned.ipa Payload
echo
echo "完成：$ROOT/ios_v2/Doutu-final-unsigned.ipa"
/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$PLIST"
/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" "$PLIST"
