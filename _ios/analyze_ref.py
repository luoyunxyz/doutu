"""读 %TEMP%/mock_ref/*.json，按页汇总行距 / 字号 / 行内边距。

    python _ios/analyze_ref.py 0 4

输出每页一行：
    id/item  行距(css/px)  字号  行起点y  x  标题字号  金额字号
"""
import glob
import json
import os
import statistics
import sys
from collections import Counter

OUT = os.path.join(os.environ["TEMP"], "mock_ref")


def load(lo, hi):
    for path in sorted(glob.glob(os.path.join(OUT, "*.json"))):
        with open(path, "r", encoding="utf-8") as f:
            d = json.load(f)
        if lo <= d.get("id", -1) <= hi:
            yield d


def detail_rows(d):
    """找连续、同字号、横向两列的行（明细行）。"""
    rows = d.get("rows", [])
    out = []
    for r in rows:
        its = r.get("items", [])
        if len(its) >= 1 and 25 <= r["y"] <= 700:
            fs = min(i["fs"] for i in its)
            if 10 <= fs <= 17 and all(10 <= i["fs"] <= 20 for i in its):
                out.append(r)
    return out


def main(argv):
    lo = int(argv[0]) if argv else 0
    hi = int(argv[1]) if len(argv) > 1 else 30
    print("id/it  行距css  行距px  字号  行数  首行y   标题   金额")
    for d in load(lo, hi):
        rows = detail_rows(d)
        pitches = [round(rows[i + 1]["y"] - rows[i]["y"], 2) for i in range(len(rows) - 1)]
        pitches = [p for p in pitches if 20 <= p <= 60]
        mode = Counter(pitches).most_common(1)[0][0] if pitches else None
        # 只保留模态间距对应的连续段
        seq = []
        for i, p in enumerate(pitches):
            if p == mode:
                seq.extend([rows[i], rows[i + 1]])
        seq = {id(r): r for r in seq}.values()
        fonts = Counter(i["fs"] for r in seq for i in r["items"])
        fs = fonts.most_common(1)[0][0] if fonts else None
        first_y = min((r["y"] for r in seq), default=None)
        title = d["rows"][0]["items"][0] if d.get("rows") else {}
        # 金额：字号最大的那一行
        amt = max((i["fs"] for r in d.get("rows", []) for i in r["items"]), default=0)
        print("%02d/%02d  %8s  %6s  %4s  %4d  %6s  %5s  %5s"
              % (d["id"], d["item"], mode if mode is not None else "-",
                 round(mode * 3) if mode else "-", fs, len(seq), first_y,
                 title.get("fs", "-"), amt))


if __name__ == "__main__":
    main(sys.argv[1:])
