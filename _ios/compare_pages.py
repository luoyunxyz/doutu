"""对比 iOS 成品页截图与安卓 DOM 参考的「行距 / 字号」。

    python _ios/compare_pages.py 0 5

Android 参考：_ios/ref/<id>_<item>.json（360 CSS 宽，×3 = 1080 画布）
iOS 截图：_ios/live/pages/<id>_<item>.png（1170×2532）

iOS 的显示变换实测为：screen = 0.96 × canvas + (66, 141)；
所以 canvas_pitch = screen_pitch / 0.96，与 Android 的 css_pitch×3 直接比。
"""
import glob
import json
import os
import statistics
import sys
from collections import Counter

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(ROOT, "ref")
SHOTS = os.path.join(ROOT, "live", "pages")
SCALE = 0.96


def bands(im, x0, x1, thr=205, min_dark=3, min_run=6):
    out, start = [], None
    px = im.load()
    for y in range(im.height):
        d = 0
        for x in range(x0, min(x1, im.width), 2):
            if px[x, y][0] < thr:
                d += 1
        if d >= min_dark and start is None:
            start = y
        elif d < min_dark and start is not None:
            if y - start >= min_run:
                out.append((start, y - 1, y - start))
            start = None
    return out


def dominant_run(tops):
    """最长的一段等间距（±1.5px）行，返回 (pitch, 行数)。"""
    best = (0, 0)
    n = len(tops)
    for i in range(n - 1):
        p = tops[i + 1] - tops[i]
        if not (18 <= p <= 240):
            continue
        j = i + 1
        while j < n - 1 and abs((tops[j + 1] - tops[j]) - p) <= 1.5:
            j += 1
        if j - i > best[1]:
            best = (p, j - i + 1)
    return best


def android_info(d):
    rows = [r for r in d.get("rows", []) if r.get("items")]
    tops = [r["y"] for r in rows if 10 <= min(i["fs"] for i in r["items"]) <= 17]
    pitches = [round(tops[i + 1] - tops[i], 2) for i in range(len(tops) - 1)]
    pitch, count = dominant_run(tops)
    fonts = Counter(i["fs"] for r in rows for i in r["items"] if 10 <= i["fs"] <= 17)
    return pitch, count, (fonts.most_common(1)[0][0] if fonts else None)


def ios_info(path):
    im = Image.open(path).convert("RGB")
    bs = bands(im, 60, 560)
    bs = [b for b in bs if b[0] >= 300 and 18 <= b[2] <= 55]
    tops = [b[0] for b in bs]
    p, count = dominant_run(tops)
    if not p:
        return None, 0, None
    # 只统计这一段里的字带高度
    i = tops.index(tops[0])
    hs = [b[2] for b in bs if b[2] >= 18]
    h = statistics.median(hs) if hs else 0
    fs = h / 0.88 / 3 / SCALE
    return p / SCALE, count, fs


def main(argv):
    lo = int(argv[0]) if argv else 0
    hi = int(argv[1]) if len(argv) > 1 else 30
    print("id/it  A行距  I行距  差   A字 I字  差   A行 I行")
    bad = 0
    for path in sorted(glob.glob(os.path.join(SHOTS, "*.png"))):
        stem = os.path.basename(path)[:-4]
        try:
            pid, item = (int(x) for x in stem.split("_"))
        except ValueError:
            continue
        if not (lo <= pid <= hi):
            continue
        ref = os.path.join(REF, stem + ".json")
        if not os.path.exists(ref):
            continue
        with open(ref, "r", encoding="utf-8") as f:
            d = json.load(f)
        ap, ac, afs = android_info(d)
        ip, ic, ifs = ios_info(path)
        if not ap or not ip:
            print("%s  A=%s I=%s  (跳过)" % (stem, ap, ip))
            continue
        dp = ip - ap * 3
        df = (ifs or 0) - (afs or 0)
        flag = "  <<<" if abs(dp) > 2 or abs(df) > 0.8 else ""
        if flag:
            bad += 1
        print("%s  %5.0f %5.0f %+5.0f  %4s %4.1f %+5.1f  %3d %3d%s"
              % (stem, ap * 3, ip, dp, afs, ifs or 0, df, ac, ic, flag))
    print("flagged:", bad)


if __name__ == "__main__":
    main(sys.argv[1:])
