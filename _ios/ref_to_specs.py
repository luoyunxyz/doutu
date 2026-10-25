"""把 _ios/ref 的 DOM 文本参考转成远程布局 spec（只含文字 + 白底）。

今天的 spec 抓取只覆盖 88/195；已抓过的页优先保留（带背景/分割线），
缺失的页用早先的 ref 文本补齐，至少保证文字坐标/字号/颜色一致。

    python _ios/ref_to_specs.py
"""
import glob
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(ROOT, "ref")
OUT = r"D:\AAA\ipa\share\specs"


def align_of(x, w):
    if x > 150:
        return "right"
    if x < 10 and w > 280:
        return "center"
    return "left"


def main():
    os.makedirs(OUT, exist_ok=True)
    added = 0
    for path in sorted(glob.glob(os.path.join(REF, "*.json"))):
        stem = os.path.basename(path)[:-5]
        parts = stem.split("_")
        if len(parts) != 2 or not all(p.isdigit() for p in parts):
            continue
        target = os.path.join(OUT, stem + ".json")
        if os.path.exists(target):
            continue
        with open(path, "r", encoding="utf-8") as f:
            d = json.load(f)
        if not d.get("rows"):
            continue
        ops = [{"k": "r", "x": 0, "y": 0, "w": 360, "h": 820,
                "c": "rgb(255, 255, 255)", "r": 0}]
        for row in d["rows"]:
            for it in row["items"]:
                text = (it.get("t") or "").strip()
                if not text:
                    continue
                ops.append({
                    "k": "t", "x": it["x"], "y": it["y"],
                    "w": it.get("w", 40), "h": it.get("h", 18),
                    "s": it.get("fs", 14), "c": it.get("color", "rgb(0, 0, 0)"),
                    "b": int(it.get("fw", "400") or 400) >= 600,
                    "a": align_of(it["x"], it.get("w", 40)),
                    "t": text[:100],
                })
        with open(target, "w", encoding="utf-8") as f:
            json.dump({"w": 360, "h": 820, "ops": ops}, f, ensure_ascii=False)
        added += 1
    print("added", added, "specs from refs; total",
          len(glob.glob(os.path.join(OUT, "*.json"))))


if __name__ == "__main__":
    main()
