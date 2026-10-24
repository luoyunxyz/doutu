"""量一张安卓截图的版式数值（不显示图片，只打印数字，省上下文）。

    python _ios/measure_page.py android/nye_sub2_detail.png [x0] [x1]
    python _ios/measure_page.py android/nye_sub2_detail.png 40 300
"""
import sys

from PIL import Image

ROOT = "D:/AAA/doutu/_ios"


def bands(im, x0, x1, thr=205, min_dark=3, min_run=6):
    """返回左列文字带 (top, bottom, height)。"""
    out, start = [], None
    for y in range(im.height):
        d = 0
        for x in range(x0, min(x1, im.width), 2):
            if im.getpixel((x, y))[0] < thr:
                d += 1
        if d >= min_dark and start is None:
            start = y
        elif d < min_dark and start is not None:
            if y - start >= min_run:
                out.append((start, y - 1, y - start))
            start = None
    return out


def main(argv):
    path = argv[0]
    if not path.startswith("D:") and not path.startswith("/"):
        path = ROOT + "/" + path
    im = Image.open(path).convert("RGB")
    x0 = int(argv[1]) if len(argv) > 1 else 40
    x1 = int(argv[2]) if len(argv) > 2 else 300
    print(f"# {path}  {im.width}x{im.height}  列 {x0}..{x1}")

    bs = bands(im, x0, x1)
    print("文字带 (top,bottom,h) 与相邻间距:")
    prev = None
    for b in bs:
        gap = "" if prev is None else f" 间距 {b[0]-prev}"
        print(f"  {b}{gap}")
        prev = b[1]

    # 纵向扫描：找底板色变化（内容结束后露出的底色）
    print("纵向底色变化（x=8 处，每 4px 采样，只打印变化点）:")
    last = None
    for y in range(0, im.height, 4):
        c = im.getpixel((8, y))
        key = (c[0] // 8 * 8, c[1] // 8 * 8, c[2] // 8 * 8)
        if key != last:
            print(f"  y={y}  #{c[0]:02x}{c[1]:02x}{c[2]:02x}")
            last = key


if __name__ == "__main__":
    main(sys.argv[1:])
