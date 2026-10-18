"""把 iOS 包内网页 CSS 的 safe-area 内边距清零。

Android 的 WebView 里 `env(safe-area-inset-*)` 全是 0，页面按 1080x2388 满屏排版；
iOS 的 WKWebView 会给出 47/34 的安全区，页面顶部多一条 47pt 的 padding、底栏被
顶出屏幕。这个脚本只改 `ios/Resources/web`（Android 的 assets 不动），把
`env(safe-area-inset-top/bottom/left/right, ...)` 统一替换成 `0px`；
状态栏的留白改由 `WebHostController.viewDidLayoutSubviews` 把 WKWebView 下移
safeAreaInsets.top 来实现，效果与 Android 的「内容在状态栏下方」一致。
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                    "ios", "Resources", "web")
PATTERN = re.compile(
    r"(?:env|constant)\(\s*safe-area-inset-(?:top|right|bottom|left)\s*(?:,\s*[^)]*)?\)")


def main():
    changed = 0
    for dirpath, _, filenames in os.walk(ROOT):
        for name in filenames:
            if not name.endswith(".css"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, "r", encoding="utf-8") as f:
                text = f.read()
            new = PATTERN.sub("0px", text)
            if new != text:
                with open(path, "w", encoding="utf-8", newline="") as f:
                    f.write(new)
                changed += 1
                print("stripped", os.path.relpath(path, ROOT))
    print("safe-area stripped files:", changed)
    return 0


if __name__ == "__main__":
    sys.exit(main())
