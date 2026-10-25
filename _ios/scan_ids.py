"""扫描 form 页的 id 映射：看哪些 id 能渲染出内容。

    python _ios/scan_ids.py [item]
"""
import os
import sys

from dump_specs import once, go

TEXT_JS = ("(()=>{const c=document.querySelector('.content');"
           "return c?(c.innerText||'').replace(/\\s+/g,' ').slice(0,30):''})()")


def main(item):
    lines = []
    for pid in range(0, 41):
        ok = go(pid, item)
        text = once(TEXT_JS, swallow=True) or ""
        lines.append("%2d  %-5s %s" % (pid, "ok" if ok else "-", text))
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scan_ids.txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", path)


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 2)
