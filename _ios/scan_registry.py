"""扫描 form 页的 id 注册表：哪些 id 能渲染、首页文本是什么。

    python _ios/scan_registry.py [item] [max]

结果写 _ios/registry_<item>.txt（UTF-8）。
"""
import os
import sys

from dump_specs import go, once

TEXT_JS = ("(()=>{const c=document.querySelector('.content');"
           "return c?(c.innerText||'').replace(/\\s+/g,' ').slice(0,80):''})()")


def main(item, hi):
    lines = []
    for pid in range(0, hi + 1):
        ok = go(pid, item)
        text = once(TEXT_JS, swallow=True) or ""
        lines.append("%3d  %-5s %s" % (pid, "ok" if ok else "-", text))
        if pid % 10 == 0:
            print("scanned", pid, flush=True)
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "registry_%d.txt" % item)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("wrote", path)


if __name__ == "__main__":
    item = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    hi = int(sys.argv[2]) if len(sys.argv) > 2 else 120
    main(item, hi)
