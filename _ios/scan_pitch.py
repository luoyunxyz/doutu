"""批量量安卓截图的「行距」（measure_page 的批处理版）。

    python _ios/scan_pitch.py                    # 扫 _ios/android 下所有 *_sub*.png
    python _ios/scan_pitch.py _ios/android/a.png ...

⚠️ 行距 = 相邻文字带的 **top - top**（同一列标签的基线节奏），
   不是 measure_page.py 打印的「间距」（那是上一条带底到下一条带顶的墨迹空隙）。
   两者差一个文字带高度（34~40px），混用会让每行少 30px 以上。
"""
import glob
import os
import statistics
import sys
from collections import Counter

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))


def bands(im, x0=40, x1=300, thr=205, min_dark=3, min_run=6):
    out, start = [], None
    w, h = im.size
    px = im.load()
    for y in range(h):
        d = 0
        for x in range(x0, min(x1, w), 2):
            if px[x, y][0] < thr:
                d += 1
        if d >= min_dark and start is None:
            start = y
        elif d < min_dark and start is not None:
            if y - start >= min_run:
                out.append((start, y - 1, y - start))
            start = None
    return out


def analyse(path, x0, x1):
    im = Image.open(path).convert("RGB")
    bs = bands(im, x0, x1)
    pitches = []
    heights = []
    for prev, cur in zip(bs, bs[1:]):
        pitch = cur[0] - prev[0]
        if 40 <= pitch <= 260:
            pitches.append(pitch)
            heights.append(prev[2])
    mode = Counter(pitches).most_common(1)[0] if pitches else (None, 0)
    med_h = int(statistics.median(heights)) if heights else 0
    return im.size, len(bs), pitches, mode, med_h


def main(argv):
    files = argv or sorted(glob.glob(os.path.join(ROOT, "android", "*_sub*.png")))
    for path in files:
        size, n, pitches, (mode, hits), h = analyse(path, 40, 300)
        label = os.path.basename(path)
        if mode is None:
            print("%-26s %4dx%-4d 带%2d  无可测行" % (label, size[0], size[1], n))
            continue
        detail = ",".join(str(g) for g in pitches)
        print("%-26s 带%2d  行距 %3d (%d/%d)  字带高 %2d  全部: %s"
              % (label, n, mode, hits, len(pitches), h, detail))


if __name__ == "__main__":
    main(sys.argv[1:])
