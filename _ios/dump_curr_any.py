"""从当前可见的生成图片页（route 含 bank/curr，标题常只是 View）抓 spec。

    python _ios/dump_curr_any.py <out.json>
"""
import json
import os
import sys
import urllib.request

import websocket

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = r"D:\AAA\ipa\share\specs"
ROUTE_JS = ("(()=>{try{const p=getCurrentPages();"
            "return p.length?p[p.length-1].route:''}catch(e){return ''}})()")


def pages():
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        return json.load(r)


def eval_on(url, expr):
    ws = websocket.create_connection(url, timeout=10, suppress_origin=True)
    try:
        ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate",
                            "params": {"expression": expr, "returnByValue": True}}))
        while True:
            data = json.loads(ws.recv())
            if data.get("id") == 1:
                return data.get("result", {}).get("result", {}).get("value")
    finally:
        ws.close()


def main(out_name):
    with open(os.path.join(ROOT, "js", "dump_spec.js"), "r", encoding="utf-8") as f:
        dump_js = f.read()
    for p in pages():
        try:
            desc = json.loads(p.get("description", "{}"))
        except ValueError:
            desc = {}
        if not desc.get("visible"):
            continue
        try:
            route = eval_on(p["webSocketDebuggerUrl"], ROUTE_JS) or ""
        except Exception:
            continue
        if "bank/curr" not in route:
            continue
        data = eval_on(p["webSocketDebuggerUrl"], dump_js)
        if not isinstance(data, dict) or not data.get("ops"):
            continue
        os.makedirs(OUT, exist_ok=True)
        path = os.path.join(OUT, out_name)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
        print("wrote", path, "ops", len(data["ops"]), "route", route)
        return
    raise SystemExit("no visible bank/curr page")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "zz_test.json")
