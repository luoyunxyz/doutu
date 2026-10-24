"""在安卓 WebView 上执行一段 JS（CDP），把结果打印成 JSON。

    python _ios/cdp_eval.py "pages/bank/form" _ios/js/dump.js

页面从 http://127.0.0.1:9222/json 里按 title/url 子串挑。
先 adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>。
"""
import json
import sys
import urllib.request

import websocket


def pick(needle):
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        pages = json.load(r)
    for p in pages:
        if needle in p.get("title", "") or needle in p.get("url", ""):
            return p
    raise SystemExit("no page matching %r; have: %s"
                     % (needle, [p.get("title") for p in pages]))


def main():
    needle, path = sys.argv[1], sys.argv[2]
    with open(path, "r", encoding="utf-8") as f:
        expr = f.read()
    page = pick(needle)
    ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=15,
                                     suppress_origin=True)
    try:
        def call(msg_id, method, params=None):
            ws.send(json.dumps({"id": msg_id, "method": method,
                                "params": params or {}}))
            while True:
                data = json.loads(ws.recv())
                if data.get("id") == msg_id:
                    return data

        call(1, "Runtime.enable")
        res = call(2, "Runtime.evaluate", {
            "expression": expr,
            "returnByValue": True,
            "awaitPromise": True,
        })
        result = res.get("result", {})
        if "exceptionDetails" in result:
            print(json.dumps(result["exceptionDetails"], ensure_ascii=False, indent=2))
            return
        value = result.get("result", {}).get("value")
        if isinstance(value, str):
            print(value)
        else:
            print(json.dumps(value, ensure_ascii=False, indent=2))
    finally:
        ws.close()


if __name__ == "__main__":
    main()
