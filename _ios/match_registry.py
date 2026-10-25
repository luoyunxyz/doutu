"""把 ref 页面和模板注册表的 (id,item) 做文本匹配，输出映射。

    python _ios/match_registry.py

结果：_ios/registry_map.json  {"<pid>_<item>": [reg_id, reg_item, score]}
"""
import glob
import json
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(ROOT, "ref")
MATRIX = os.path.join(ROOT, "registry_matrix.json")
OUT = os.path.join(ROOT, "registry_map.json")


def norm(s):
    s = re.sub(r"[\s:：*（）()]", "", s or "")
    return s


def main():
    with open(MATRIX, "r", encoding="utf-8") as f:
        matrix = json.load(f)
    for row in matrix:
        row["ntext"] = norm(row["text"])
    mapping = {}
    for path in sorted(glob.glob(os.path.join(REF, "*.json"))):
        stem = os.path.basename(path)[:-5]
        parts = stem.split("_")
        if len(parts) != 2:
            continue
        with open(path, "r", encoding="utf-8") as f:
            d = json.load(f)
        labels = []
        for row in d.get("rows", []):
            for it in row.get("items", []):
                t = norm(it.get("t"))
                if len(t) >= 2 and not t.isdigit() and t not in labels:
                    labels.append(t)
        if not labels:
            continue
        best = (0, None, None)
        for row in matrix:
            hit = sum(1 for l in labels if l in row["ntext"])
            score = hit / len(labels)
            if score > best[0]:
                best = (score, row["id"], row["item"])
        if best[0] >= 0.5:
            mapping[stem] = [best[1], best[2], round(best[0], 2)]
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(mapping, f, ensure_ascii=False, indent=1, sort_keys=True)
    print("mapped", len(mapping), "->", OUT)
    for k in sorted(mapping)[:20]:
        print(k, mapping[k])


if __name__ == "__main__":
    main()
