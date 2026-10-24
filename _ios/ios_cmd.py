"""给 iPhone 发导航令牌并取回截图（走 8000 端口的 debug_server.py）。

    python _ios/ios_cmd.py mock:home:0 mock:sub:2 mock:gen

每条令牌执行后 App 会回传一张 hostView 截图；脚本等新图、存到 _ios/live/<token>.png。
token 里的非字母数字会换成下划线，重复 token 会覆盖 —— 批量抓页请用 ios_sweep.py。
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

_seq = int(time.time() * 10) % 800000 + 100000


def send(token, timeout=12.0, copy_to=None):
    global _seq
    _seq += 1
    seq = _seq
    started = time.time()
    tmp = CMD + ".tmp"
    with open(tmp, "w", encoding="ascii") as f:
        json.dump({"seq": seq, "token": token}, f, separators=(",", ":"))
    # debug_server 每秒都在读 cmd.json；Windows 下 rename 可能撞上占用，重试几次，
    # 最后兜底直接覆盖写（JSON 很小，读到半截的概率极低）。
    for _ in range(20):
        try:
            os.replace(tmp, CMD)
            break
        except PermissionError:
            time.sleep(0.05)
    else:
        with open(CMD, "w", encoding="ascii") as f:
            json.dump({"seq": seq, "token": token}, f, separators=(",", ":"))
        try:
            os.remove(tmp)
        except OSError:
            pass
    pattern = os.path.join(SHOTS, "shot-%d-*.png" % seq)
    deadline = time.time() + timeout
    path = None
    while time.time() < deadline:
        files = [p for p in glob.glob(pattern) if os.path.getmtime(p) >= started - 1]
        if files:
            path = sorted(files, key=os.path.getmtime)[-1]
            break
        time.sleep(0.15)
    if path and copy_to:
        os.makedirs(os.path.dirname(copy_to), exist_ok=True)
        with open(path, "rb") as src, open(copy_to, "wb") as dst:
            dst.write(src.read())
    return path


def main(tokens):
    os.makedirs(OUT, exist_ok=True)
    for token in tokens:
        started = time.time()
        path = send(token)
        name = re.sub(r"[^A-Za-z0-9]+", "_", token).strip("_") or "shot"
        if path:
            dst = os.path.join(OUT, name + ".png")
            with open(path, "rb") as src, open(dst, "wb") as dst_f:
                dst_f.write(src.read())
            print("OK   %-22s -> %s  (%.1fs)" % (token, os.path.basename(dst), time.time() - started))
        else:
            print("FAIL %-22s (12s 内没有新图)" % token)
        time.sleep(0.2)


if __name__ == "__main__":
    args = sys.argv[1:] or ["mock:home:0"]
    main(args)
