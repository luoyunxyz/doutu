"""从安卓 base.apk 里抽出 www 静态资源，供 iOS 远程渲染图片。

    python _ios/extract_assets.py
"""
import os
import zipfile

APK = os.path.join(os.environ.get("TEMP", "."), "doutu_base.apk")
OUT = r"D:\AAA\ipa\share\android_assets"


def main():
    z = zipfile.ZipFile(APK)
    names = [n for n in z.namelist() if "/www/" in n and not n.endswith("/")]
    print("entries", len(names))
    n = 0
    for name in names:
        rel = name.split("/www/", 1)[1]
        dest = os.path.join(OUT, rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with z.open(name) as src, open(dest, "wb") as f:
            f.write(src.read())
        n += 1
    print("extracted", n, "->", OUT)


if __name__ == "__main__":
    main()
