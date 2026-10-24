"""把 _ios/ref 的 JSON 展开成可读文本，方便逐页读版式。

    python _ios/ref_show.py 1 1        # 只展开 01_00..01_05
    python _ios/ref_show.py            # 全量
"""
import glob
import json
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(ROOT, "ref")
OUT = os.path.join(ROOT, "ref_view")


def main(argv):
    lo = int(argv[0]) if argv else 0
    hi = int(argv[1]) if len(argv) > 1 else 30
    os.makedirs(OUT, exist_ok=True)
    all_paths = sorted(glob.glob(os.path.join(REF, "*.json")))
    print("ref", REF, "files", len(all_paths))
    for path in all_paths:
        stem = os.path.basename(path)[:-5]
        parts = stem.split("_")
        if len(parts) != 2 or not all(p.isdigit() for p in parts):
            continue
        pid, item = int(parts[0]), int(parts[1])
        if not (lo <= pid <= hi):
            continue
        with open(path, "r", encoding="utf-8") as f:
            d = json.load(f)
        lines = ["%s  h=%.1f top=%.1f" % (stem, d.get("h", 0), d.get("top", 0))]
        for r in d.get("rows", []):
            parts = []
            for i in r["items"]:
                parts.append("%s|fs=%s|x=%.1f|%s" % (i["t"], i["fs"], i["x"], i["color"]))
            lines.append("y=%7.2f  %s" % (r["y"], "  ||  ".join(parts)))
        dest = os.path.join(OUT, stem + ".txt")
        with open(dest, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        print("wrote", dest, len(lines))
    print("done", lo, hi)


if __name__ == "__main__":
    main(sys.argv[1:])
