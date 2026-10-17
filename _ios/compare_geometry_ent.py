"""
娱乐页几何比对：Android 真机截图 vs `Playbook.layout()` 算出的坐标（2026-10-17）

    python _ios/compare_geometry_ent.py

娱乐页整页手画，排版公式在 `Playbook.layout()`：
    y = heroBottom + 40
    每个分区：y += 96（分区标题）→ 每行 210 → y += 44（分区间距）
    bottom = y - 44

下发给客户端的 playbook 有 3 个分区（2 / 3 / 1 行），所以三张白卡的纵向位置是：
    卡片1: 508 .. 928      （2 行 × 210）
    卡片2: 1068 .. 1698    （3 行）
    卡片3: 1838 .. 2048    （1 行）
头部渐变：112 .. 372，左右 32 .. 1048
"""
from PIL import Image
import sys

GOLDEN = r'D:\AAA\doutu\_ios\golden_ent.png'

# 与 Swift/Java 里完全相同的常量
HERO_TOP, HERO_BOTTOM = 112, 372
CARD_LEFT, CARD_RIGHT = 32, 1048
EXPECTED_CARDS = [(508, 928), (1068, 1698), (1838, 2048)]


def is_purple(p):
    r, g, b = p[0], p[1], p[2]
    return b >= 200 and 60 <= r <= 170 and 70 <= g <= 170 and b > r + 50


def is_white(p):
    return p[0] >= 252 and p[1] >= 252 and p[2] >= 252


def row_extent(img, y, predicate, x0, x1):
    px = img.load()
    xs = [x for x in range(max(0, x0), min(x1, img.size[0])) if predicate(px[x, y])]
    return (min(xs), max(xs)) if xs else None


def col_extent(img, x, predicate, y0, y1):
    px = img.load()
    ys = [y for y in range(max(0, y0), min(y1, img.size[1])) if predicate(px[x, y])]
    return (min(ys), max(ys)) if ys else None


def white_runs(img, x, y0, y1, min_len=60):
    """沿某一列找连续的纯白段（>= min_len），用来定位白卡的上下边界"""
    px = img.load()
    runs = []
    start = None
    for y in range(y0, min(y1, img.size[1])):
        if is_white(px[x, y]):
            if start is None:
                start = y
        else:
            if start is not None and y - start >= min_len:
                runs.append((start, y - 1))
            start = None
    if start is not None and min(y1, img.size[1]) - start >= min_len:
        runs.append((start, min(y1, img.size[1]) - 1))
    return runs


def white_edge(img, x, expected, window=60, want_top=True):
    """在 expected±window 内找白↔非白的跳变点（卡片内有文字图标，不能用连续纯白段）。

    want_top=True 找「从非白进入白」的最靠上位置；False 找「从白离开」的最靠下位置。
    返回 None 表示窗口内没有跳变（说明坐标可能真的错了）。
    """
    px = img.load()
    lo = max(0, int(expected - window))
    hi = min(img.size[1] - 1, int(expected + window))
    hits = []
    for y in range(lo, hi + 1):
        white = is_white(px[x, y])
        if want_top and white:
            hits.append(y)
        elif not want_top and white:
            hits.append(y)
    if not hits:
        return None
    return min(hits) if want_top else max(hits)


def main():
    img = Image.open(GOLDEN).convert('RGB')
    print('screenshot %dx%d' % (img.size[0], img.size[1]))
    results = []

    # 1) 头部渐变块：横向在 y=240 扫，纵向在 x=200 扫
    row = row_extent(img, 240, is_purple, 0, img.size[0])
    col = col_extent(img, 200, is_purple, 0, 600)
    results.append(('hero-block', (CARD_LEFT, HERO_TOP, CARD_RIGHT, HERO_BOTTOM),
                    (row[0], col[0], row[1], col[1]) if (row and col) else None))

    # 2) 三张白卡：在右侧空白列（x=1024，避开文字与图标）上找上下边界
    X = 1024
    for i, (top, bottom) in enumerate(EXPECTED_CARDS):
        actual_top = white_edge(img, X, top, want_top=True)
        actual_bottom = white_edge(img, X, bottom, want_top=False)
        actual = None
        if actual_top is not None and actual_bottom is not None:
            actual = (CARD_LEFT, actual_top, CARD_RIGHT, actual_bottom)
        results.append(('card-%d' % (i + 1), (CARD_LEFT, top, CARD_RIGHT, bottom), actual))

    print()
    print('%-12s%-26s%-26s%s' % ('feature', 'expected', 'actual', 'delta'))
    print('-' * 92)
    worst = 0
    for name, expect, actual in results:
        e = tuple(round(v, 1) for v in expect)
        if actual is None:
            print('%-12s%-26s%-26s%s' % (name, str(e), 'not found', '-'))
            worst = max(worst, 999)
            continue
        a = tuple(int(v) for v in actual)
        deltas = [round(a[i] - e[i], 1) for i in range(4)]
        maxd = max(abs(d) for d in deltas)
        worst = max(worst, maxd)
        flag = '[OK]' if maxd <= 6 else ('[WARN]' if maxd <= 18 else '[FAIL]')
        print('%-12s%-26s%-26s%s max %.0fpx %s' % (name, str(e), str(a), flag, maxd, deltas))

    # 卡片左右边界无法从纯白段精确得到（水印打断），单独用一行验证
    row = row_extent(img, 600, is_white, 0, img.size[0])
    if row:
        e = (CARD_LEFT, CARD_RIGHT)
        deltas = [row[0] - CARD_LEFT, row[1] - CARD_RIGHT]
        print()
        print('card-1 左右边界：理论 %s  实测 %s  偏差 %s' % (e, row, deltas))
        worst = max(worst, abs(deltas[0]), abs(deltas[1]))

    print()
    print('worst delta = %.0fpx' % worst)
    print('[OK] geometry matches' if worst <= 6 else ('[WARN] small drift' if worst <= 18 else '[FAIL]'))
    return 0 if worst <= 18 else 1


if __name__ == '__main__':
    sys.exit(main())
