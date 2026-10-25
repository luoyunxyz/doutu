"""列出当前所有可见 WebView 的 route（生成页常只有 View 标题）。"""
import json
import urllib.request

import websocket

ROUTE = ("(()=>{try{const p=getCurrentPages();"
         "return p.map(x=>x.route).join('|')}catch(e){return 'err'}})()")


def main():
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        pages = json.load(r)
    for p in pages:
        try:
            desc = json.loads(p.get("description", "{}"))
        except ValueError:
            desc = {}
        if not desc.get("visible"):
            continue
        try:
            ws = websocket.create_connection(p["webSocketDebuggerUrl"], timeout=6,
                                             suppress_origin=True)
            ws.send(json.dumps({"id": 1, "method": "Runtime.evaluate",
                                "params": {"expression": ROUTE, "returnByValue": True}}))
            route = ""
            while True:
                d = json.loads(ws.recv())
                if d.get("id") == 1:
                    route = d.get("result", {}).get("result", {}).get("value", "")
                    break
            ws.close()
        except Exception as exc:
            route = "ERR %r" % (exc,)
        print("%-24s %s" % (p.get("title", "")[:24], route))


if __name__ == "__main__":
    main()
