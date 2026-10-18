"""批量抓 iOS 真机截图（用于和 Android 同屏比对）。

原理：App 的 DebugBridge 每秒轮询 `D:\\AAA\\ipa\\share\\cmd.json`，
执行 token 后把 hostView / WebView / 整窗截图 POST 回 `share\\shots`。
这个脚本负责「重置 App → 发 token → 等新图 → 归档」。

用法：
    python _ios/capture_ios.py home me zhi0 zhi1 ...
    python _ios/capture_ios.py --all
"""
import argparse
import glob
import json
import os
import shutil
import time

import paramiko

SHARE = r"D:\AAA\ipa\share"
CMD = os.path.join(SHARE, "cmd.json")
SHOTS = os.path.join(SHARE, "shots")
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "_ios", "shots")
HOST = "192.168.1.171"
LOG = ("/var/mobile/Containers/Data/Application/"
       "6A0FD58B-3AE5-44FE-A6B2-88514FB21056/Documents/crash.log")


def ssh():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, 22, username="root", password="alpine", timeout=10,
              allow_agent=False, look_for_keys=False)
    return c


def run(c, cmd, timeout=30):
    _, out, err = c.exec_command(cmd, timeout=timeout)
    return out.read().decode("utf-8", "replace") + err.read().decode("utf-8", "replace")


class Capturer:
    def __init__(self):
        os.makedirs(SHOTS, exist_ok=True)
        os.makedirs(OUT, exist_ok=True)
        self.c = ssh()
        # 用时间戳做序号，避免和上一次运行留下的 shot-<seq> 撞名（撞名会取到旧图）
        self.seq = int(time.time() * 10) % 900000
        self.started = time.time()

    def reset(self):
        # 先写一条 noop：App 启动时 DebugBridge 的 lastSeq 会重置，如果 cmd.json
        # 还留着上一条业务指令，冷启动后会先把那条指令执行掉（例如又跳进客服页），
        # 后面的步骤就全乱了。
        self.send("noop")
        # 在日志里打一个标记；App 真正启动到 DebugBridge 轮询之后，才会在标记后面
        # 出现 "DebugBridge started"。启动没完成就发 token，会被后一条覆盖掉
        # （DebugBridge 只读 cmd.json 的最后一条），于是经常抓到上一屏/客服页。
        marker = "---CAPTURE-%d---" % int(time.time() * 1000)
        run(self.c, "echo '%s' >> %s" % (marker, LOG))
        run(self.c, "killall -9 Doutu 2>/dev/null; sleep 0.8; uiopen doutu://; sleep 2.8")
        ps = run(self.c, "ps aux | grep -i Doutu | grep -v grep")
        if "Doutu.app" not in ps:
            run(self.c, "uiopen doutu://; sleep 2.5")
        for _ in range(30):
            tail = run(self.c, "tail -60 %s" % LOG)
            pos = tail.rfind(marker)
            if pos >= 0 and "DebugBridge started" in tail[pos:]:
                break
            time.sleep(0.5)
        self.started = time.time()

    def send(self, token):
        self.seq += 1
        with open(CMD, "w", encoding="ascii") as f:
            json.dump({"seq": self.seq, "token": token}, f, separators=(",", ":"))
        return self.seq

    def wait_shot(self, seq, name=None, timeout=8):
        pattern = os.path.join(SHOTS, "shot-%04d%s-*.png" % (seq, ("-" + name) if name else ""))
        deadline = time.time() + timeout
        while time.time() < deadline:
            files = [p for p in glob.glob(pattern)
                     if os.path.getmtime(p) >= self.started - 1]
            if files:
                return sorted(files, key=os.path.getmtime)[-1]
            time.sleep(0.25)
        return None

    def capture(self, key, tokens, web=False, window=False):
        self.reset()
        for token in tokens:
            seq = self.send(token)
            if token != tokens[-1]:
                time.sleep(2.0)
        if web:
            time.sleep(1.5)
            seq = self.send("webshot")
            path = self.wait_shot(seq, "web")
        elif window:
            seq = self.send("winshot")
            path = self.wait_shot(seq, "window")
        else:
            path = self.wait_shot(seq)
        if not path:
            print("%-16s MISS" % key)
            return None
        dst = os.path.join(OUT, "ios-%s.png" % key)
        shutil.copyfile(path, dst)
        print("%-16s %s (%d)" % (key, os.path.basename(path), os.path.getsize(dst)))
        return dst

    def close(self):
        self.c.close()


CASES = {
    "home_wei": (["tab:0", "page:0"], False),
    "home_zhi": (["tab:0", "page:1"], False),
    "home_dou": (["tab:0", "page:2"], False),
    "home_q": (["tab:0", "page:3"], False),
    "ent": (["tab:1"], False),
    "me": (["tab:2"], False),
    "zhi0": (["zhiCell:0"], False),
    "zhi1": (["zhiCell:1"], False),
    "zhi2": (["zhiCell:2"], False),
    "zhi3": (["zhiCell:3"], False),
    "zhi4": (["zhiCell:4"], False),
    "zhi5": (["zhiCell:5"], False),
    "zhi6": (["zhiCell:6"], False),
    "zhi7": (["zhiCell:7"], False),
    "zhi8": (["zhiCell:8"], False),
    "zhi9": (["zhiCell:9"], False),
    "zhi10": (["zhiCell:10"], False),
    "zhi11": (["zhiCell:11"], False),
    "zhi12": (["zhiCell:12"], False),
    "zhi14": (["zhiCell:14"], False),
    "zhi15": (["zhiCell:15"], False),
    "play_parse": (["play:parse"], False),
    "play_order": (["play:order"], False),
    "play_call": (["play:call"], False),
    "play_shutdown": (["play:shutdown"], False),
    "play_sound": (["play:sound"], False),
    "play_avatar": (["play:avatar"], False),
    "play_balance": (["play:balance"], False),
    "paycode": (["收付款"], False),
    "collect": (["row:收钱"], False),
    "travel": (["出行"], False),
    "service": (["entry:0"], False),
    "analytics": (["zhiCell:7", "analytics"], False),
    "receipt_loaded": (["zhiCell:11", "entry:1792296000000", "back"], False),
    "receipt": (["zhiCell:11"], False),
    "receipt_detail": (["zhiCell:11", "entry:1792296000000"], False),
    "settings2": (["user"], False),
    "parsegrid": (["tab:0", "page:0", "weiCell:3:2"], False),
    "feedback": (["entry:1"], False),
    "settings": (["entry:3"], False),
    "wei0": (["tab:0", "page:0", "weiCell:0:0"], True),
    "wei1": (["tab:0", "page:0", "weiCell:0:1"], True),
    "wei6": (["tab:0", "page:0", "weiCell:1:2"], True),
    "wei7": (["tab:0", "page:0", "weiCell:1:3"], True),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="*")
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()
    names = list(CASES) if args.all else args.names
    cap = Capturer()
    try:
        for name in names:
            if name not in CASES:
                print("skip unknown", name)
                continue
            tokens, web = CASES[name]
            cap.capture(name, tokens, web=web)
    finally:
        cap.close()


if __name__ == "__main__":
    main()
