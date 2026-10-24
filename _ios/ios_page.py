"""抓单页 iOS 成品图：python _ios/ios_page.py 0 3 [0 4 1 0 ...]

成对参数 (入口, 子页)，存到 _ios/live/pages/<id>_<sub>.png。
"""
import os
import sys

from ios_cmd import send

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "live", "pages")


def shoot(page_id, sub):
    os.makedirs(OUT, exist_ok=True)
    send("tab:2")
    if not send("mock:home:%d" % page_id):
        print("home %d failed" % page_id)
        return
    if sub > 0:
        send("mock:sub:%d" % sub)
    path = send("mock:gen", timeout=15)
    if not path:
        print("gen %d/%d failed" % (page_id, sub))
        return
    dst = os.path.join(OUT, "%02d_%02d.png" % (page_id, sub))
    with open(path, "rb") as src, open(dst, "wb") as dst_f:
        dst_f.write(src.read())
    send("back")
    send("back")
    print("saved", dst, os.path.getsize(dst))


def main(argv):
    if len(argv) < 2:
        raise SystemExit("usage: id sub [id sub ...]")
    for i in range(0, len(argv) - 1, 2):
        shoot(int(argv[i]), int(argv[i + 1]))


if __name__ == "__main__":
    main(sys.argv[1:])
