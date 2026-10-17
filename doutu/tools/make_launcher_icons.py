"""
生成「修改应用图标」那四款应用图标的自适应图标资源（adaptive icon）。

为什么要有这一步：四张原图都是小方图 —— alipay.png 实际是 72x72 的 JPEG、
hkalipay.png 48x48、wechat.png 72x72、logo.png 512x512。直接丢给启动器，
在 480+ dpi 的机器上会被放大到发虚；而且 Android 8 起会自己给「非自适应」的
图标套一层白底圆盘，图会缩水一圈。所以这里按自适应图标的规矩重新出一份：

    前景 foreground：把原图按 72% 等比铺在 108x108 的画布中间。
        自适应图标可见的只有中间 72x72 那块（四周 18 是给各家桌面做
        视差/裁切留的），而 108 的 72% = 77.8，刚好把可见区铺满并微微
        多出一点，所以既不会在里面再缩一圈，也不会被裁到。
    背景 background：一块纯色，取自原图自己的底色。前景没有铺到的地方
        （只有 108 画布最外圈，正常看不见）露出来的也是这个色，不会是白边。

每个密度都要出：mdpi 108、hdpi 162、xhdpi 216、xxhdpi 324、xxxhdpi 432。

跑法（在 D:\\AAA\\doutu\\doutu 下）：
    python tools/make_launcher_icons.py
加 --check 只报尺寸、不写文件。

源图和 drawable-nodpi 里的那份都保留：弹窗里的预览图用的就是 drawable-nodpi
的原始方图，看的和以后桌面挂的是同一张。
"""

import argparse
import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

SOURCE = Path(r"D:\AAA\pojia\export\02_flutter_assets\flutter_assets\assets\tupian")
PROJECT = Path(__file__).resolve().parent.parent
RES = PROJECT / "app" / "src" / "main" / "res"

# 108dp 画布上，前景铺多少、从哪开始（见文件开头的说明）
FOREGROUND_SCALE = 0.72

# 每一款：清单里用的资源名、原图、背景色（取自原图角落的底色）
ICONS = {
    "ic_launcher_alipay": ("alipay.png", (23, 120, 253)),
    "ic_launcher_alipay_hk": ("hkalipay.png", (0, 0, 0)),
    "ic_launcher_wechat": ("wechat.png", (1, 228, 110)),
    "ic_launcher_doutu": ("logo.png", (0, 116, 255)),
}

# 密度名 -> 108dp 对应的像素
DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def foreground(source: Image.Image, size: int) -> Image.Image:
    """把原图等比铺到 size x size 画布的中间，占 size 的 72%。"""
    art = round(size * FOREGROUND_SCALE)
    left = (size - art) // 2
    # 原图是方的，等比缩放后正好是 art x art
    scaled = source.convert("RGB").resize((art, art), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(scaled.convert("RGBA"), (left, left))
    return canvas


def legacy(source: Image.Image, size: int) -> Image.Image:
    """给 Android 7 及更早的机器用的方图：没有自适应图标那一套，整张铺满即可。"""
    return source.convert("RGB").resize((size, size), Image.LANCZOS)


def main() -> int:
    parser = argparse.ArgumentParser(description="生成自适应应用图标")
    parser.add_argument("--check", action="store_true", help="只核对，不写文件")
    args = parser.parse_args()

    if not SOURCE.is_dir():
        print(f"[x] 找不到源目录：{SOURCE}")
        return 1

    written = 0
    for name, (file_name, colour) in ICONS.items():
        src = SOURCE / file_name
        if not src.is_file():
            print(f"[x] 缺图：{src}")
            return 1
        source = Image.open(src)
        for density, size in DENSITIES.items():
            folder = RES / f"mipmap-{density}"
            fg_path = folder / f"{name}_foreground.png"
            bg_path = folder / f"{name}_background.png"
            legacy_path = folder / f"{name}.png"
            if args.check:
                ok = fg_path.is_file() and bg_path.is_file() and legacy_path.is_file()
                print(f"[{'ok' if ok else 'x '}] {density:8} {name} {size}x{size}")
                written += 1 if ok else 0
                continue
            folder.mkdir(parents=True, exist_ok=True)
            foreground(source, size).save(fg_path, "PNG", optimize=True)
            Image.new("RGBA", (size, size), colour + (255,)).save(bg_path, "PNG", optimize=True)
            legacy(source, size).save(legacy_path, "PNG", optimize=True)
            written += 1
        print(f"[ok] {file_name} ({source.size[0]}x{source.size[1]}) "
              f"-> {name}，底色 #{colour[0]:02x}{colour[1]:02x}{colour[2]:02x}")

    if args.check:
        expected = len(ICONS) * len(DENSITIES)
        print(f"\n{written}/{expected} 组就位。")
        return 0 if written == expected else 1

    print(f"\n共写出 {written} 组（每组：自适应前景 + 自适应背景 + Android 7 及更早用的方图）。"
          "\n下一步：gradlew.bat assembleDebug")
    return 0


if __name__ == "__main__":
    sys.exit(main())
