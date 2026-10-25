"""统计 _ios/ref 里每个入口的参考覆盖情况。

    python _ios/ref_coverage.py
"""
import glob
import json
import os

ROOT = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(ROOT, "ref")
SUBS = {
    0: 5, 1: 6, 2: 6, 3: 8, 4: 12, 5: 3, 6: 5, 7: 12, 8: 5, 9: 10,
    10: 5, 11: 4, 12: 3, 13: 12, 14: 12, 15: 3, 16: 12, 17: 4, 18: 4,
    19: 1, 20: 12, 21: 7, 22: 2, 23: 1, 24: 11, 25: 2, 26: 4, 27: 12,
    28: 5, 29: 6, 30: 1,
}
NAMES = ["农业", "工商", "中国", "邮政", "招商", "光大", "兴业", "建设", "平安", "交通",
         "中信", "浦发", "民生", "微信", "支付宝", "汇旺", "数字钱包", "抖音", "印度",
         "短信", "回单", "流水", "聊天", "c币", "云闪付", "TP", "欧易", "话费", "农信",
         "qq", "京东"]


def main():
    have = {}
    for path in glob.glob(os.path.join(REF, "*.json")):
        stem = os.path.basename(path)[:-5]
        parts = stem.split("_")
        if len(parts) != 2 or not all(p.isdigit() for p in parts):
            continue
        pid, item = int(parts[0]), int(parts[1])
        with open(path, "r", encoding="utf-8") as f:
            d = json.load(f)
        if d.get("rows"):
            have.setdefault(pid, set()).add(item)
    total_subs = sum(SUBS.values())
    total_have = sum(len(v) for v in have.values())
    print("入口  子页  有参考  覆盖率")
    for pid in range(31):
        n = SUBS[pid]
        k = len(have.get(pid, ()))
        print("%-6s %3d  %5d  %4d%%" % (NAMES[pid], n, k, round(k * 100 / n)))
    print("合计 %d 子页，有参考 %d（%d%%）" % (total_subs, total_have, round(total_have * 100 / total_subs)))


if __name__ == "__main__":
    main()
