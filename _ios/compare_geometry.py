"""
几何比对：Android 真机截图 vs Swift 算出的坐标（2026-10-17）

    python _ios/compare_geometry.py

1:1 移植最容易错的是**坐标**（颜色字号好抄，位置容易算错）。这里不比颜色（两端字体渲染
必然不同），而是沿**指定的一行/一列扫最长连续色块**，测出特征块的真实边界，与代码里的
公式对账。

「我」页画在 1280×2560 的设计空间，再按 (1080/1280, 2388/2560) 缩到 1080×2388 画布。
"""
from PIL import Image
import sys

GOLDEN = r'D:\AAA\doutu\_ios\golden_me.png'
SX = 1080 / 1280
SY = 2388 / 2560


def d2c(l, t, r, b):
    """设计空间矩形 -> 画布矩形"""
    return (l * SX, t * SY, r * SX, b * SY)


def row_extent(img, y, predicate, x0=0, x1=None):
    """某一行里最长连续满足条件的横向区间"""
    w, h = img.size
    x1 = x1 if x1 is not None else w
    px = img.load()
    best = None
    start = None
    last = x0
    for x in range(x0, min(x1, w)):
        if predicate(px[x, y]):
            if start is None:
                start = x
        else:
            if start is not None:
                if best is None or (x - start) > (best[1] - best[0]):
                    best = (start, x - 1)
                start = None
        last = x
    if start is not None and (best is None or (last - start) > (best[1] - best[0])):
        best = (start, last)
    return best


def col_extent(img, x, predicate, y0=0, y1=None):
    """某一列里最长连续满足条件的纵向区间"""
    w, h = img.size
    y1 = y1 if y1 is not None else h
    px = img.load()
    best = None
    start = None
    last = y0
    for y in range(y0, min(y1, h)):
        if predicate(px[x, y]):
            if start is None:
                start = y
        else:
            if start is not None:
                if best is None or (y - start) > (best[1] - best[0]):
                    best = (start, y - 1)
                start = None
        last = y
    if start is not None and (best is None or (last - start) > (best[1] - best[0])):
        best = (start, last)
    return best


def row_extent(img, y, predicate, x0, x1):
    """某一行里匹配像素的最左与最右（对水印覆盖造成的间断免疫）"""
    px = img.load()
    xs = [x for x in range(max(0, x0), min(x1, img.size[0])) if predicate(px[x, y])]
    return (min(xs), max(xs)) if xs else None


def col_extent(img, x, predicate, y0, y1):
    """某一列里匹配像素的最上与最下"""
    px = img.load()
    ys = [y for y in range(max(0, y0), min(y1, img.size[1])) if predicate(px[x, y])]
    return (min(ys), max(ys)) if ys else None


def is_vip_blue(p):
    r, g, b = p[0], p[1], p[2]
    return 20 <= r <= 90 and 40 <= g <= 120 and b >= 100 and b > r + 40


def is_gold(p):
    r, g, b = p[0], p[1], p[2]
    return r >= 235 and g >= 180 and b <= 195 and r > b + 45


def is_pure_white(p):
    return p[0] >= 252 and p[1] >= 252 and p[2] >= 252


def main():
    img = Image.open(GOLDEN).convert('RGB')
    print('screenshot %dx%d' % (img.size[0], img.size[1]))

    results = []

    # 1) 会员卡：扫 y=640（卡片中部）那一行的蓝，再扫 x=500 那一列
    e = d2c(88, 508, 1192, 838)
    row = row_extent(img, 640, is_vip_blue, 0, img.size[0])
    col = col_extent(img, 500, is_vip_blue, int(400 * SY), int(900 * SY))
    results.append(('vip-card', e, (row[0], col[0], row[1], col[1]) if (row and col) else None))

    # 2) 金色按钮：单行容易被水印压断，扫多行取并集再取最左/最右
    e = d2c(872, 508 + 118, 1128, 508 + 232)
    gx0, gx1 = int(600 * SX), int(1140 * SX)
    goldRows = [r for r in (row_extent(img, y, is_gold, gx0, gx1)
                            for y in (600, 620, 640, 660, 680)) if r]
    col = col_extent(img, 840, is_gold, int(520 * SY), int(800 * SY))
    goldActual = None
    if goldRows and col:
        goldActual = (min(r[0] for r in goldRows), col[0],
                      max(r[1] for r in goldRows), col[1])
    results.append(('gold-button', e, goldActual))

    # 3) 用户行白卡：扫 y=300 那一行的纯白（页底色 #f5f5f5=245 不会被算进来）
    e = d2c(56, 214, 1224, 484)
    row = row_extent(img, 300, is_pure_white, 0, img.size[0])
    col = col_extent(img, 540, is_pure_white, int(120 * SY), int(560 * SY))
    results.append(('user-card', e, (row[0], col[0], row[1], col[1]) if (row and col) else None))

    # 4) 常用服务白卡：只比横向（y=1500 那一行）
    e = d2c(88, 948, 1192, 948 + 150 + 4 * 190)
    row = row_extent(img, 1500, is_pure_white, 0, img.size[0])
    results.append(('entries-card-x', (e[0], 0, e[2], 0), (row[0], 0, row[1], 0) if row else None))

    print()
    print('%-18s%-26s%-26s%s' % ('feature', 'expected(canvas px)', 'actual', 'delta'))
    print('-' * 100)
    worst = 0
    for name, expect, actual in results:
        e = tuple(round(v, 1) for v in expect)
        if actual is None:
            print('%-18s%-26s%-26s%s' % (name, str(e), 'not found', '-'))
            worst = max(worst, 999)
            continue
        a = tuple(int(v) for v in actual)
        deltas = [round(a[i] - e[i], 1) for i in range(4)]
        maxd = max(abs(d) for d in deltas)
        worst = max(worst, maxd)
        flag = '[OK]' if maxd <= 6 else ('[WARN]' if maxd <= 15 else '[FAIL]')
        print('%-18s%-26s%-26s%s max %.0fpx %s' % (name, str(e), str(a), flag, maxd, deltas))

    print()
    print('worst delta = %.0fpx' % worst)
    if worst <= 6:
        print('[OK] geometry matches (within antialias / corner rounding)')
    elif worst <= 15:
        print('[WARN] small drift, check the related constants')
    else:
        print('[FAIL] off by too much, coordinates are wrong')
    return 0 if worst <= 15 else 1


if __name__ == '__main__':
    sys.exit(main())
