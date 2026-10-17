#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
通用真机截图比对（2026-10-17）

    python _ios/compare_shots.py --android _ios/golden_me.png --ios _ios/iphone_me.png
    python _ios/compare_shots.py --android a.png --ios b.png --out diff.png --align

为什么需要它：现在只有「我页」「娱乐页」两屏做过几何比对，其余 25 屏是
「坐标逐条照抄 + 静态核验」—— 那不等于验证过。有了这个脚本，出包后每屏拍一张、
跑一条命令，就能量化「差多少像素、差在哪一段」。

它做的事：
  1. 把两张图缩放到同一宽度（默认 1080，等于 Android 的设计宽度）
  2. 可选 `--align`：在 ±12px 内自动找最佳垂直/水平偏移（截图裁剪常常差几像素）
  3. 输出：整体平均差、最差的 20 个横向条带（每条 24px 高）、以及并排+差异图

判定建议（经验值）：
  · 平均差 < 6   且  最差条带 < 40  → 基本一致，可认为该屏过关
  · 某一条带特别高 → 那一段有结构性偏差（多半是某个卡片位置或字号错了）
"""
import argparse
import os
import sys

try:
    from PIL import Image, ImageChops
except ImportError:
    sys.stderr.write('需要 Pillow：pip install Pillow\n')
    sys.exit(2)

try:
    import numpy as np
except ImportError:
    np = None

BAND = 24      # 条带高度
TOP_N = 20     # 报多少条最差条带


def load_scaled(path, width):
    image = Image.open(path).convert('RGB')
    if image.width != width:
        height = max(1, round(image.height * width / image.width))
        image = image.resize((width, height), Image.LANCZOS)
    return image


def best_shift(a, b, limit=30):
    """在 ±limit 内找让平均差最小的 (dx, dy)。截图的裁剪差异常常有十几到二十几像素。"""
    if np is None:
        return 0, 0
    best = (0, 0, 1e9)
    aw, ah = a.size
    bw, bh = b.size
    w = min(aw, bw) - 2 * limit
    h = min(ah, bh) - 2 * limit
    if w <= 0 or h <= 0:
        return 0, 0
    aa = np.asarray(a.crop((limit, limit, limit + w, limit + h)), dtype=np.int16)
    for dy in range(-limit, limit + 1, 2):
        for dx in range(-limit, limit + 1, 2):
            bb = np.asarray(
                b.crop((limit + dx, limit + dy, limit + dx + w, limit + dy + h)),
                dtype=np.int16)
            diff = float(np.mean(np.abs(aa - bb)))
            if diff < best[2]:
                best = (dx, dy, diff)
    return best[0], best[1]


def band_report(a, b):
    """按横向条带统计平均差，返回 (list[(y, mean)], overall_raw)。"""
    if np is None:
        # 没有 numpy 时退化为逐像素
        w, h = min(a.width, b.width), min(a.height, b.height)
        aa = a.crop((0, 0, w, h))
        bb = b.crop((0, 0, w, h))
        diff = ImageChops.difference(aa, bb)
        total = 0
        bands = {}
        pixels = 0
        for y in range(h):
            row = 0
            for x in range(0, w, 4):
                p = diff.getpixel((x, y))
                row += sum(p)
                pixels += 1
            total += row
            bands[y // BAND] = bands.get(y // BAND, 0) + row
        means = {y * BAND: v / (BAND * (w // 4) * 3) for y, v in bands.items()}
        return sorted(means.items(), key=lambda kv: -kv[1]), total / max(1, pixels) / 3

    w, h = min(a.width, b.width), min(b.height if False else b.height, a.height)
    aa = np.asarray(a.crop((0, 0, w, h)), dtype=np.int16)
    bb = np.asarray(b.crop((0, 0, w, h)), dtype=np.int16)
    diff = np.abs(aa - bb).mean(axis=2)          # 每像素三通道平均差
    overall = float(diff.mean())
    rows = diff.mean(axis=1)                     # 每一行的平均差
    bands = []
    for y0 in range(0, h, BAND):
        segment = rows[y0:y0 + BAND]
        if len(segment):
            bands.append((y0, float(segment.mean())))
    bands.sort(key=lambda kv: -kv[1])
    return bands, overall


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--android', required=True, help='Android 真机截图')
    parser.add_argument('--ios', required=True, help='iPhone 截图')
    parser.add_argument('--width', type=int, default=1080, help='统一缩放到的宽度')
    parser.add_argument('--align', action='store_true', help='自动找最佳偏移再比')
    parser.add_argument('--out', default='', help='输出差异图路径（可选）')
    args = parser.parse_args()

    for path in (args.android, args.ios):
        if not os.path.isfile(path):
            sys.stderr.write('找不到文件：%s\n' % path)
            return 2

    a = load_scaled(args.android, args.width)
    b = load_scaled(args.ios, args.width)

    dx = dy = 0
    if args.align:
        dx, dy = best_shift(a, b)
        if dx or dy:
            # 用偏移后的 b 再比
            b = b.crop((max(0, dx), max(0, dy), b.width, b.height))
            a = a.crop((max(0, -dx), max(0, -dy), a.width, a.height))

    print('  尺寸（缩放到宽 %d）：android %dx%d   ios %dx%d'
          % (args.width, a.width, a.height, b.width, b.height))
    if dx or dy:
        print('  自动对齐偏移：dx=%+d dy=%+d' % (dx, dy))

    bands, overall = band_report(a, b)
    print('')
    print('  整体平均差（0-255）：%.2f' % overall)
    print('  最差 %d 个横向条带（每条 %dpx 高）：' % (TOP_N, BAND))
    for y, value in bands[:TOP_N]:
        flag = '  <<<' if value > 40 else ''
        print('    y=%5d .. %5d   %.2f%s' % (y, y + BAND, value, flag))

    verdict = 'PASS' if overall < 6 and (not bands or bands[0][1] < 40) else 'CHECK'
    print('')
    print('  判定：%s' % ('基本一致（可认为该屏过关）' if verdict == 'PASS'
                        else '有可见偏差，看上面标 <<< 的条带'))

    if args.out:
        w = min(a.width, b.width)
        h = min(a.height, b.height)
        aa = a.crop((0, 0, w, h))
        bb = b.crop((0, 0, w, h))
        diff = ImageChops.difference(aa, bb)
        # 差异压暗，便于看结构
        diff = diff.point(lambda v: min(255, v * 3))
        canvas = Image.new('RGB', (w * 3 + 20, h), (24, 24, 28))
        canvas.paste(aa, (0, 0))
        canvas.paste(bb, (w + 10, 0))
        canvas.paste(diff, (w * 2 + 20, 0))
        canvas.save(args.out)
        print('  差异图已保存：%s（左=Android，中=iPhone，右=差异×3）' % args.out)

    return 0


if __name__ == '__main__':
    sys.exit(main())
