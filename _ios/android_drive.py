"""用 ADB 驱动安卓豆图，截图 + 按文字点按（给「逐屏对齐」用）。

    python _ios/android_drive.py shot home
    python _ios/android_drive.py texts
    python _ios/android_drive.py tap 通讯录
    python _ios/android_drive.py shot ali_contacts

图落在 _ios/android/<name>.png（1080x2388，和 iOS 画布同尺寸）。
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "_ios", "android")
DEVICE_XML = "/sdcard/_doutu_ui.xml"
DEVICE_PNG = "/sdcard/_doutu_shot.png"


def adb(*args, timeout=60):
    p = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    return p.stdout.decode("utf-8", "replace") + p.stderr.decode("utf-8", "replace")


def shot(name):
    os.makedirs(OUT, exist_ok=True)
    adb("shell", "screencap", "-p", DEVICE_PNG)
    target = os.path.join(OUT, name + ".png")
    adb("pull", DEVICE_PNG, target)
    print("shot ->", target)
    return target


def dump_xml():
    adb("shell", "uiautomator", "dump", DEVICE_XML)
    local = os.path.join(ROOT, "_ios", "ui.xml")
    adb("pull", DEVICE_XML, local)
    with open(local, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def nodes(xml):
    out = []
    for m in re.finditer(r'<node[^>]*>', xml):
        tag = m.group(0)
        text = re.search(r'text="([^"]*)"', tag)
        desc = re.search(r'content-desc="([^"]*)"', tag)
        bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not bounds:
            continue
        label = (text.group(1) if text else "") or (desc.group(1) if desc else "")
        x1, y1, x2, y2 = (int(bounds.group(i)) for i in (1, 2, 3, 4))
        out.append({"text": label, "rect": (x1, y1, x2, y2),
                    "center": ((x1 + x2) // 2, (y1 + y2) // 2)})
    return out


def list_texts():
    ns = [n for n in nodes(dump_xml()) if n["text"].strip()]
    for n in ns:
        print("%-28s %s" % (n["text"][:28], n["rect"]))
    if not ns:
        print("(没有文字节点 —— 可能是 WebView/Canvas 渲染)")
    return ns


def tap_text(label, index=0):
    ns = [n for n in nodes(dump_xml()) if n["text"].strip() == label]
    if not ns:
        ns = [n for n in nodes(dump_xml()) if label in n["text"]]
    if not ns:
        print("找不到:", label)
        return False
    x, y = ns[min(index, len(ns) - 1)]["center"]
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)
    print("tap %s -> (%d,%d)" % (label, x, y))
    return True


def main(argv):
    if not argv:
        print(__doc__)
        return
    cmd, rest = argv[0], argv[1:]
    if cmd == "shot":
        shot(rest[0] if rest else "shot")
    elif cmd == "texts":
        list_texts()
    elif cmd == "tap":
        tap_text(rest[0], int(rest[1]) if len(rest) > 1 else 0)
    elif cmd == "tapxy":
        adb("shell", "input", "tap", rest[0], rest[1])
        time.sleep(1.0)
    elif cmd == "swipe":
        adb("shell", "input", "swipe", "540", rest[0], "540", rest[1], "300")
        time.sleep(0.8)
    elif cmd == "back":
        adb("shell", "input", "keyevent", "4")
        time.sleep(1.0)
    elif cmd == "start":
        adb("shell", "am", "start", "-n", "uni.UNIE8A9C59/com.luoyun.doutu.Icon.doutu")
        time.sleep(3)
    else:
        print("未知命令", cmd)


if __name__ == "__main__":
    main(sys.argv[1:])
