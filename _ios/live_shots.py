"""按令牌在真机上逐屏抓图（不需要 SSH —— App 自己轮询 cmd.json 并回传）。

    python _ios/live_shots.py tab:2 ali:contacts ali:redpacket

图落在 _ios/live/<name>.png，name 由令牌里的非字母数字替换成下划线。
"""
import glob
import json
import os
import re
import sys
import time

SHARE = r"D:\AAA\ipa\share"
CMD = os.path.join(SHARE, "cmd.json")
SHOTS = os.path.join(SHARE, "shots")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "live")


def main(tokens):
    os.makedirs(OUT, exist_ok=True)
    seq = int(time.time() * 10) % 900000
    for token in tokens:
        seq += 1
        started = time.time()
        with open(CMD, "w", encoding="ascii") as f:
            json.dump({"seq": seq, "token": token}, f, separators=(",", ":"))
        pattern = os.path.join(SHOTS, "shot-%04d*.png" % seq)
        path = None
        deadline = time.time() + 12
        while time.time() < deadline:
            files = [p for p in glob.glob(pattern) if os.path.getmtime(p) >= started - 1]
            if files:
                path = sorted(files, key=os.path.getmtime)[-1]
                break
            time.sleep(0.3)
        name = re.sub(r"[^A-Za-z0-9]+", "_", token).strip("_") or "shot"
        if path:
            target = os.path.join(OUT, name + ".png")
            with open(path, "rb") as src, open(target, "wb") as dst:
                dst.write(src.read())
            print("OK   %-24s -> %s" % (token, os.path.basename(target)))
        else:
            print("FAIL %-24s (12s 内没有新图)" % token)
        time.sleep(1.2)


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        args = ["tab:2", "ali:contacts", "ali:redpacket", "ali:transfer"]
    main(args)
