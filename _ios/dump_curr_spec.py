"""从生成图片后的 pages/bank/curr 页面抓完整 spec。

    python _ios/dump_curr_spec.py 0 2

输出 D:\\AAA\\ipa\\share\\specs\\00_02.json
"""
import json
import os
import sys

from dump_specs import Session

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = r"D:\AAA\ipa\share\specs"


def main(pid, sub, out_name=None):
    with open(os.path.join(ROOT, "js", "dump_spec.js"), "r", encoding="utf-8") as f:
        js = f.read()
    s = Session("pages/bank/curr")
    try:
        data = s.eval(js)
    finally:
        s.close()
    if not isinstance(data, dict) or not data.get("ops"):
        raise SystemExit("empty curr page")
    os.makedirs(OUT, exist_ok=True)
    name = out_name or ("%02d_%02d.json" % (pid, sub))
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    print("wrote", path, "ops", len(data["ops"]))


if __name__ == "__main__":
    pid = int(sys.argv[1])
    sub = int(sys.argv[2])
    main(pid, sub, sys.argv[3] if len(sys.argv) > 3 else None)
