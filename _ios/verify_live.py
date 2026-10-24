"""重启 iOS App 后按令牌逐屏抓图，用于 §6 的换算模型验证。

    python _ios/verify_live.py mock:home:0 mock:sub:1 mock:gen

与 live_shots.py 的区别：先通过 SSH 冷启动 App，等 DebugBridge 就绪，
避免「App 没运行导致 12 秒超时」的假失败。
"""
import os
import shutil
import sys
import time

from capture_ios import Capturer, OUT, SHOTS


def main(tokens):
    cap = Capturer()
    try:
        cap.reset()
        print("App 已启动，DebugBridge 就绪")
        for token in tokens:
            seq = cap.send(token)
            if token != tokens[-1]:
                time.sleep(2.0)
            path = cap.wait_shot(seq)
            name = token.replace(":", "_")
            if path:
                dst = os.path.join(OUT, name + ".png")
                shutil.copyfile(path, dst)
                print("OK   %-20s -> %s" % (token, os.path.basename(dst)))
            else:
                print("FAIL %-20s (8s 内没有新图)" % token)
            time.sleep(1.0)
    finally:
        cap.close()


if __name__ == "__main__":
    main(sys.argv[1:] or ["mock:home:0", "mock:sub:1", "mock:gen"])
