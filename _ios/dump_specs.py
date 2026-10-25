"""把安卓每页的 DOM 视觉块（背景/文字/分割线）导出成 display list。

    python _ios/dump_specs.py

输出 D:\\AAA\\ipa\\share\\specs\\<id>_<item>.json
"""
import json
import os
import subprocess
import sys
import urllib.request

import websocket

SUBS = {
    0: 5, 1: 6, 2: 6, 3: 8, 4: 12, 5: 3, 6: 5, 7: 12, 8: 5, 9: 10,
    10: 5, 11: 4, 12: 3, 13: 12, 14: 12, 15: 3, 16: 12, 17: 4, 18: 4,
    19: 1, 20: 12, 21: 7, 22: 2, 23: 1, 24: 11, 25: 2, 26: 4, 27: 12,
    28: 5, 29: 6, 30: 1,
}
OUT = r"D:\AAA\ipa\share\specs"
JS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "js", "dump_spec.js")

OPTIONS_JS = ("(()=>{try{const p=getCurrentPages();"
              "return p.length?JSON.stringify(p[p.length-1].options):''}"
              "catch(e){return ''}})()")
SIG_JS = ("(()=>{const c=document.querySelector('.content');"
          "return c?c.innerText.replace(/\\s+/g,' ').slice(0,60):''})()")


def targets():
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        return json.load(r)


def pick(title):
    pages = [p for p in targets() if title in p.get("title", "")]
    if not pages:
        raise RuntimeError("no %s target" % title)

    def visible(p):
        try:
            return bool(json.loads(p.get("description", "{}")).get("visible"))
        except ValueError:
            return False

    vis = [p for p in pages if visible(p)]
    return (vis or pages)[-1]


class Session:
    def __init__(self, title="pages/bank/form"):
        page = pick(title)
        self.ws = websocket.create_connection(page["webSocketDebuggerUrl"],
                                              timeout=20, suppress_origin=True)
        self.id = 0

    def eval(self, expr):
        self.id += 1
        self.ws.send(json.dumps({"id": self.id, "method": "Runtime.evaluate",
                                 "params": {"expression": expr, "returnByValue": True,
                                            "awaitPromise": True}}))
        while True:
            data = json.loads(self.ws.recv())
            if data.get("id") == self.id:
                r = data.get("result", {})
                if "exceptionDetails" in r:
                    raise RuntimeError(json.dumps(r["exceptionDetails"], ensure_ascii=False))
                return r.get("result", {}).get("value")

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass


def once(expr, swallow=False, title="pages/bank/form"):
    s = None
    try:
        s = Session(title)
        return s.eval(expr)
    except Exception as exc:
        if swallow:
            return None
        raise exc
    finally:
        if s:
            s.close()


def go(pid, item):
    expr = "uni.redirectTo({url:'/pages/bank/form?id=%d&item_id=%d'})" % (pid, item)
    once(expr, swallow=True)
    import time
    time.sleep(0.5)
    for _ in range(8):
        opts = once(OPTIONS_JS, swallow=True)
        try:
            got = json.loads(opts) if opts else {}
        except ValueError:
            got = {}
        if str(got.get("id")) == str(pid) and str(got.get("item_id")) == str(item):
            return True
        time.sleep(0.3)
    return False


def main(argv):
    lo = int(argv[0]) if argv else 0
    hi = int(argv[1]) if len(argv) > 1 else 30
    os.makedirs(OUT, exist_ok=True)
    with open(JS, "r", encoding="utf-8") as f:
        dump = f.read()
    print("specs ->", OUT, flush=True)
    n = 0
    for pid in range(lo, hi + 1):
        for item in range(SUBS[pid]):
            if not go(pid, item):
                print("  !! no page %d/%d" % (pid, item), flush=True)
                continue
            try:
                data = once(dump, swallow=False)
            except Exception as exc:
                print("  !! dump %d/%d %r" % (pid, item, exc), flush=True)
                continue
            if not isinstance(data, dict) or not data.get("ops"):
                print("  -- empty %d/%d" % (pid, item), flush=True)
                continue
            path = os.path.join(OUT, "%02d_%02d.json" % (pid, item))
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False)
            n += 1
            print("  %02d/%02d ops=%d" % (pid, item, len(data["ops"])), flush=True)
    print("done: %d specs" % n, flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
