"""把安卓真机截图和 iOS 真机截图拼成左右对照图，并给出文字带的纵向位置。

    python _ios/compare_page.py android/nye_detail_android.png live/mock_gen.png nye

左安卓、右 iOS（iOS 缩放到安卓尺寸），存到 _ios/compare/<name>.png。
另外打印两边「文字带」的中心 y（同一坐标系，2388 高），用来发现行距/间距偏差。
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "_ios", "compare")


def bands(path, x0, x1, thr=200, min_run=5):
    im = Image.open(path).convert("L")
    hits = []
    for y in range(im.height):
        dark = 0
        for x in range(x0, min(x1, im.width), 2):
            if im.getpixel((x, y)) < thr:
                dark += 1
        hits.append(dark)
    out, start = [], None
    for i, v in enumerate(hits):
        if v > 2 and start is None:
            start = i
        elif v <= 2 and start is not None:
            if i - start >= min_run:
                out.append((start + i) // 2)
            start = None
    return out


def main(a_rel, b_rel, name):
    a_path = os.path.join(ROOT, "_ios", "android", a_rel)
    b_path = os.path.join(ROOT, "_ios", "live", b_rel)
    a = Image.open(a_path).convert("RGB")
    b = Image.open(b_path).convert("RGB").resize(a.size, Image.LANCZOS)

    os.makedirs(OUT, exist_ok=True)
    w, h = a.size
    canvas = Image.new("RGB", (w * 2 + 20, h), (255, 255, 255))
    canvas.paste(a, (0, 0))
    canvas.paste(b, (w + 20, 0))
    scale = 0.45
    canvas = canvas.resize((int((w * 2 + 20) * scale), int(h * scale)), Image.LANCZOS)
    target = os.path.join(OUT, name + ".png")
    canvas.save(target)

    # iOS 图换算到安卓的高度坐标系再比
    ratio = h / Image.open(b_path).height
    left = bands(a_path, 40, 260)
    right = [int(y * ratio) for y in bands(b_path, 40, 260)]
    print("android 文字带 y:", left)
    print("ios     文字带 y:", right, "(已换算到 %d 高)" % h)
    print("对照图 ->", target)


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print(__doc__)
    else:
        main(sys.argv[1], sys.argv[2], sys.argv[3])
