"""把安卓「模拟」31 个入口 × 全部子页的预览 DOM 全部抓下来。

依赖：adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>，
      并且先打开「模拟」网格 → 任意入口，使 pages/bank/form 页面存在。

    python _ios/dump_all_pages.py            # 全量，落 %TEMP%/mock_ref/
    python _ios/dump_all_pages.py 0 5        # 只抓 id 0..5

输出 JSON：{id, item, options, rows:[{y, items:[{t,x,y,w,h,fs,color}]}]}
坐标单位 CSS px（360 宽画布）；×3 就是 1080 画布的像素。

⚠️ uni.redirectTo 会重建 WebView，旧 CDP 连接会断；所以每次操作都重新连。
"""
import json
import os
import sys
import time
import urllib.request

import websocket

SUBS = {
    0: 5, 1: 6, 2: 6, 3: 8, 4: 12, 5: 3, 6: 5, 7: 12, 8: 5, 9: 10,
    10: 5, 11: 4, 12: 3, 13: 12, 14: 12, 15: 3, 16: 12, 17: 4, 18: 4,
    19: 1, 20: 12, 21: 7, 22: 2, 23: 1, 24: 11, 25: 2, 26: 4, 27: 12,
    28: 5, 29: 6, 30: 1,
}
JS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "js")
OUT = os.path.join(os.environ["TEMP"], "mock_ref")


def load(name):
    with open(os.path.join(JS_DIR, name), "r", encoding="utf-8") as f:
        return f.read()


def targets():
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        return json.load(r)


def pick():
    pages = [p for p in targets() if "pages/bank/form" in p.get("title", "")]
    if not pages:
        raise RuntimeError("no pages/bank/form target; open 模拟 → 任一入口 first")

    def visible(p):
        try:
            return bool(json.loads(p.get("description", "{}")).get("visible"))
        except ValueError:
            return False

    vis = [p for p in pages if visible(p)]
    return (vis or pages)[0]


class Session:
    def __init__(self):
        page = pick()
        self.ws = websocket.create_connection(page["webSocketDebuggerUrl"],
                                              timeout=20, suppress_origin=True)
        self.id = 0

    def call(self, method, params=None):
        self.id += 1
        self.ws.send(json.dumps({"id": self.id, "method": method,
                                 "params": params or {}}))
        while True:
            data = json.loads(self.ws.recv())
            if data.get("id") == self.id:
                return data

    def eval(self, expr):
        res = self.call("Runtime.evaluate", {
            "expression": expr, "returnByValue": True, "awaitPromise": True})
        r = res.get("result", {})
        if "exceptionDetails" in r:
            raise RuntimeError(json.dumps(r["exceptionDetails"], ensure_ascii=False))
        return r.get("result", {}).get("value")

    def close(self):
        try:
            self.ws.close()
        except Exception:  # noqa: BLE001
            pass


def eval_once(expr, swallow=False):
    s = None
    try:
        s = Session()
        return s.eval(expr)
    except Exception as exc:  # noqa: BLE001
        if swallow:
            return None
        raise exc
    finally:
        if s:
            s.close()


OPTIONS_JS = ("(()=>{try{const p=getCurrentPages();"
              "return p.length?JSON.stringify(p[p.length-1].options):''}"
              "catch(e){return ''}})()")
SIG_JS = ("(()=>{const c=document.querySelector('.content');"
          "return c?c.innerText.replace(/\\s+/g,' ').slice(0,80):''})()")


def go(page_id, item, before_sig):
    expr = ("uni.redirectTo({url:'/pages/bank/form?id=%d&item_id=%d'})"
            % (page_id, item))
    # redirectTo 往往来不及回包就重建 WebView，断线是正常的
    eval_once(expr, swallow=True)
    time.sleep(0.55)
    opts = None
    for _ in range(10):
        opts = eval_once(OPTIONS_JS, swallow=True)
        try:
            got = json.loads(opts) if opts else {}
        except ValueError:
            got = {}
        if str(got.get("id")) == str(page_id) and str(got.get("item_id")) == str(item):
            opts = got
            break
        time.sleep(0.3)
        opts = None
    if opts is None:
        return None
    # 再等内容真的换掉（uni-app 的 options 比 DOM 先更新）
    for _ in range(12):
        sig = eval_once(SIG_JS, swallow=True) or ""
        if sig and sig != before_sig:
            return opts
        time.sleep(0.25)
    return opts


def main(argv):
    lo = int(argv[0]) if len(argv) > 0 else 0
    hi = int(argv[1]) if len(argv) > 1 else 30
    os.makedirs(OUT, exist_ok=True)
    dump = load("dump_page.js")
    print("dumping id %d..%d -> %s" % (lo, hi, OUT), flush=True)
    missing = []
    total = 0
    for page_id in range(lo, hi + 1):
        n = SUBS[page_id]
        for item in range(n):
            before = eval_once(SIG_JS, swallow=True) or ""
            opts = go(page_id, item, before)
            if opts is None:
                missing.append((page_id, item))
                print("  !! no render %d/%d" % (page_id, item), flush=True)
                continue
            try:
                data = eval_once(dump)
            except Exception as exc:  # noqa: BLE001
                missing.append((page_id, item))
                print("  !! dump fail %d/%d: %s" % (page_id, item, exc), flush=True)
                continue
            data["id"] = page_id
            data["item"] = item
            data["options"] = opts
            path = os.path.join(OUT, "%02d_%02d.json" % (page_id, item))
            with open(path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False)
            total += 1
            print("  %02d/%02d rows=%d" % (page_id, item, len(data.get("rows", []))),
                  flush=True)
    print("done: %d pages, missing %d %s" % (total, len(missing), missing), flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
