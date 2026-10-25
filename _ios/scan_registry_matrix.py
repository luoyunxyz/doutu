"""把模板注册表的 (id, item) 全部走一遍，记录首页文本，用于和 ref 匹配。

    python _ios/scan_registry_matrix.py

结果：_ios/registry_matrix.json  [{id,item,text}]
"""
import json
import os
import sys

from dump_specs import go, once

TEXT_JS = ("(()=>{const c=document.querySelector('.content');"
           "return c?(c.innerText||'').replace(/\\s+/g,' ').slice(0,300):''})()")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "registry_matrix.json")


def main():
    rows = []
    for pid in range(0, 41):
        for item in range(0, 12):
            if not go(pid, item):
                continue
            text = once(TEXT_JS, swallow=True) or ""
            if not text.strip():
                continue
            rows.append({"id": pid, "item": item, "text": text})
            print("%02d/%02d  %s" % (pid, item, text[:40]), flush=True)
        with open(OUT, "w", encoding="utf-8") as f:
            json.dump(rows, f, ensure_ascii=False)
    print("done", len(rows), "->", OUT)


if __name__ == "__main__":
    main()
