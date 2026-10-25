"""用 CDP 的 Input 域在 WebView 里发一次真实触摸。

    python _ios/cdp_tap.py 284 132

坐标是 CSS px（360 宽画布），不是设备像素。
"""
import json
import sys
import urllib.request

import websocket


def main(x, y):
    with urllib.request.urlopen("http://127.0.0.1:9222/json", timeout=5) as r:
        pages = json.load(r)
    page = next(p for p in pages if "pages/index/index" in p.get("title", ""))
    ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=10,
                                     suppress_origin=True)
    try:
        def call(mid, method, params=None):
            ws.send(json.dumps({"id": mid, "method": method, "params": params or {}}))
            while True:
                data = json.loads(ws.recv())
                if data.get("id") == mid:
                    return data

        call(1, "Runtime.enable")
        call(2, "Input.dispatchTouchEvent", {
            "type": "touchStart", "touchPoints": [{"x": x, "y": y}]})
        call(3, "Input.dispatchTouchEvent", {"type": "touchEnd", "touchPoints": []})
        print("tap", x, y)
    finally:
        ws.close()


if __name__ == "__main__":
    main(float(sys.argv[1]), float(sys.argv[2]))
