"""走一遍用户指定的流程并抓生成图片页的完整 spec：

模拟 → 类目 → 子页 → 生成图片 → 立刻抓 pages/bank/curr。

    python _ios/gen_one.py 0 2
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

import websocket

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = r"D:\AAA\ipa\share\specs"
ROUTE_JS = ("(()=>{try{const p=getCurrentPages();"
            "return p.length?p[p.length-1].route:''}catch(e){return ''}})()")
SUBS = {0: 5, 1: 6, 2: 6, 3: 8, 4: 12, 5: 3, 6: 5, 7: 12, 8: 5, 9: 10,
        10: 5, 11: 4, 12: 3, 13: 12, 14: 12, 15: 3, 16: 12, 17: 4, 18: 4,
        19: 1, 20: 12, 21: 7, 22: 2, 23: 1, 24: 11, 25: 2, 26: 4, 27: 12,
        28: 5, 29: 6, 30: 1}


def sh(*args):
    subprocess.run(["adb"] + list(args), check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def forward():
    out = subprocess.run(["adb", "shell", "cat", "/proc/net/unix"],
                         capture_output=True, text=True).stdout
    m = re.search(r"webview_devtools_remote_(\d+)", out)
    if not m:
        return False
    sh("forward", "--remove", "tcp:9222")
    sh("forward", "tcp:9222", "localabstract:webview_devtools_remote_" + m.group(1))
    time.sleep(0.5)
    return True


def targets():
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        return json.load(r)


def eval_on(url, expr):
    ws = websocket.create_connection(url, timeout=8, suppress_origin=True)
    try:
        ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate",
                            "params": {"expression": expr, "returnByValue": True}}))
        while True:
            data = json.loads(ws.recv())
            if data.get("id") == 1:
                return data.get("result", {}).get("result", {}).get("value")
    finally:
        ws.close()


def find_curr():
    for p in targets():
        try:
            desc = json.loads(p.get("description", "{}"))
        except ValueError:
            continue
        if not desc.get("visible"):
            continue
        try:
            route = eval_on(p["webSocketDebuggerUrl"], ROUTE_JS) or ""
        except Exception:
            continue
        if "bank/curr" in route:
            return p
    return None


def main(pid, sub):
    with open(os.path.join(ROOT, "js", "dump_spec.js"), "r", encoding="utf-8") as f:
        dump_js = f.read()

    sh("shell", "am", "force-stop", "uni.UNIE8A9C59")
    time.sleep(1)
    sh("shell", "am", "start", "-n", "uni.UNIE8A9C59/com.luoyun.doutu.Icon.doutu")
    time.sleep(6)
    forward()
    tap(540, 2249)           # 模拟
    time.sleep(2)
    col = pid % 3
    row = pid // 3
    tap(207 + col * 333, 965 + row * 135)
    time.sleep(2)
    tap(700, 660 + sub * 136)  # 抽屉第 sub 项
    time.sleep(2)
    tap(540, 941)            # 生成图片
    forward()
    for _ in range(25):
        time.sleep(0.3)
        p = find_curr()
        if not p:
            continue
        data = eval_on(p["webSocketDebuggerUrl"], dump_js)
        if not isinstance(data, dict) or not data.get("ops"):
            continue
        os.makedirs(OUT, exist_ok=True)
        path = os.path.join(OUT, "%02d_%02d.json" % (pid, sub))
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        print("wrote", path, "ops", len(data["ops"]))
        return
    raise SystemExit("no curr spec captured")


if __name__ == "__main__":
    main(int(sys.argv[1]), int(sys.argv[2]))
