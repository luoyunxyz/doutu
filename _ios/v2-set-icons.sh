#!/bin/bash
# v2 打包后写入图标声明。
#
# 现象：asset catalog 里明明有 AppIcon/AppIconAlipay/AppIconAlipayHK/AppIconWechat，
# 但 XcodeGen + GENERATE_INFOPLIST_FILE 产出的 Info.plist 没有 CFBundleIcons，
# 真机 setAlternateIconName 一直返回 OSStatus -54。
# 这里在未签名 IPA 打包前显式补上主图标和三个备选图标。
set -euo pipefail

PLIST="${1:?usage: v2-set-icons.sh Payload/DoutuV2.app/Info.plist}"
test -f "$PLIST"

say() { printf '%s\n' "$*"; }

set_key() {
  local key="$1"; shift
  /usr/libexec/PlistBuddy -c "Delete :$key" "$PLIST" >/dev/null 2>&1 || true
  /usr/libexec/PlistBuddy -c "Add :$key $*" "$PLIST"
}

say "--- setting icon keys in $PLIST ---"

set_key CFBundleIconName string AppIcon

set_key CFBundleIcons dict
set_key CFBundleIcons:CFBundlePrimaryIcon dict
set_key CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconName string AppIcon
set_key CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconFiles array
set_key CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconFiles:0 string AppIcon60x60

set_key CFBundleIcons:CFBundleAlternateIcons dict
for icon in AppIconAlipay AppIconAlipayHK AppIconWechat; do
  set_key "CFBundleIcons:CFBundleAlternateIcons:$icon" dict
  set_key "CFBundleIcons:CFBundleAlternateIcons:$icon:CFBundleIconFiles" array
  set_key "CFBundleIcons:CFBundleAlternateIcons:$icon:CFBundleIconFiles:0" string "$icon"
  set_key "CFBundleIcons:CFBundleAlternateIcons:$icon:UIPrerenderedIcon" bool false
done

/usr/libexec/PlistBuddy -c "Print :CFBundleIcons" "$PLIST"
say "--- icon keys written ---"
