"""把已有 spec 里的安卓 file:// 图片路径改写成 8000 端口 HTTP 路径。"""
import glob
import json
import os

OUT = r"D:\AAA\ipa\share\specs"
OLD = "file:///data/user/0/uni.UNIE8A9C59/files/apps/__UNI__E8A9C59/www/"
NEW = "http://192.168.1.96:8000/android_assets/"


def main():
    n = 0
    for path in glob.glob(os.path.join(OUT, "*.json")):
        with open(path, "r", encoding="utf-8") as f:
            d = json.load(f)
        changed = False
        for op in d.get("ops", []):
            if op.get("k") == "i" and (op.get("src") or "").startswith(OLD):
                op["src"] = NEW + op["src"][len(OLD):]
                changed = True
        if changed:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(d, f, ensure_ascii=False)
            n += 1
    print("rewrote", n, "specs")


if __name__ == "__main__":
    main()
