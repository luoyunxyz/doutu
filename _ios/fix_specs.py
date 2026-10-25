"""修正 spec：把单独绘制的 + / - 符号并回金额文本，并右对齐。

安卓 DOM 里金额符号常是独立 span，iOS 分别绘制会变成「100.00   -」。
"""
import glob
import json
import os

OUT = r"D:\AAA\ipa\share\specs"


def fix(path):
    with open(path, "r", encoding="utf-8") as f:
        d = json.load(f)
    ops = d.get("ops")
    if not isinstance(ops, list):
        return 0
    texts = [o for o in ops if o.get("k") == "t"]
    signs = [o for o in texts if (o.get("t") or "").strip() in ("+", "-", "＋", "－")]
    nums = [o for o in texts if any(ch.isdigit() for ch in (o.get("t") or ""))]
    changed = 0
    for s in signs:
        cand = [n for n in nums
                if n is not s and abs(n.get("y", 0) - s.get("y", 0)) < 4
                and n.get("x", 0) > 30
                and n.get("x", 0) <= s.get("x", 0) + 40]
        if not cand:
            continue
        n = max(cand, key=lambda o: o.get("x", 0))
        sign = (s.get("t") or "").strip()
        if sign in ("＋", "－"):
            sign = "+" if sign == "＋" else "-"
        n["t"] = sign + (n.get("t") or "")
        n["a"] = "right"
        n["x"] = min(n.get("x", 0), s.get("x", 0))
        n["w"] = (s.get("x", 0) + s.get("w", 0)) - n["x"]
        s["t"] = ""
        changed += 1
    if changed:
        d["ops"] = [o for o in ops if not (o.get("k") == "t" and not (o.get("t") or ""))]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False)
    return changed


def main():
    total = 0
    files = 0
    for path in glob.glob(os.path.join(OUT, "*.json")):
        n = fix(path)
        if n:
            files += 1
            total += n
    print("fixed", total, "signs in", files, "files")


if __name__ == "__main__":
    main()
