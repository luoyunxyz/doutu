"""
把「修改应用图标」用的四张图装进项目的资源目录。

图都在：
    D:\\AAA\\pojia\\export\\02_flutter_assets\\flutter_assets\\assets\\tupian
        alipay.png     -> 支付宝
        hkalipay.png   -> 支付宝（香港）
        wechat.png     -> 微信
        logo.png       -> 豆图

装到：
    <项目>\\app\\src\\main\\res\\drawable-nodpi\\

清单里的四个 activity-alias 直接引 @drawable/alipay、@drawable/hkalipay、
@drawable/wechat、@drawable/logo，所以文件名必须原样保留。

跑法（在 D:\\AAA\\doutu\\doutu 下）：
    python tools/install_icon_assets.py
加 --check 只看结果、不写文件。

脚本只做复制和核对，不改图片本身：这四张是 512 x 512 的方图，桌面自己的形状
遮罩会处理圆角，直接当启动器图标用就是最忠实的样子。
"""

import argparse
import hashlib
import shutil
import sys
from pathlib import Path

SOURCE = Path(r"D:\AAA\pojia\export\02_flutter_assets\flutter_assets\assets\tupian")
# 脚本放在 <项目>\tools\ 下，所以项目根就是脚本上一级
PROJECT = Path(__file__).resolve().parent.parent
TARGET = PROJECT / "app" / "src" / "main" / "res" / "drawable-nodpi"

FILES = ["alipay.png", "hkalipay.png", "wechat.png", "logo.png"]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description="安装应用图标资源")
    parser.add_argument("--check", action="store_true", help="只核对，不写文件")
    args = parser.parse_args()

    if not SOURCE.is_dir():
        print(f"[x] 找不到源目录：{SOURCE}")
        return 1
    if not TARGET.is_dir():
        print(f"[x] 找不到资源目录：{TARGET}")
        return 1

    failed = 0
    for name in FILES:
        src = SOURCE / name
        dst = TARGET / name
        if not src.is_file():
            print(f"[x] 缺图：{src}")
            failed += 1
            continue
        if args.check:
            if not dst.is_file():
                print(f"[x] 还没装：{dst}")
                failed += 1
            elif digest(src) == digest(dst):
                print(f"[ok] {name} 已经就位（{dst.stat().st_size} 字节）")
            else:
                print(f"[x] 内容不一致：{dst}（需要重新复制）")
                failed += 1
            continue
        shutil.copyfile(src, dst)
        same = digest(src) == digest(dst)
        print(f"[{'ok' if same else 'x'}] {src.name} -> {dst}"
              f"（{dst.stat().st_size} 字节）")
        if not same:
            failed += 1

    if failed:
        print(f"\n有 {failed} 项没成，先解决了再编。")
        return 1
    print("\n四张图都就位了。下一步：gradlew.bat assembleDebug")
    return 0


if __name__ == "__main__":
    sys.exit(main())
