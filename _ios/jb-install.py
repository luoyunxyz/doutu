"""把 Doutu.app 通过 SSH 装进越狱设备的 /Applications，不需要签名。

用法：
    python _ios/jb-install.py [--host 192.168.1.170] [--password alpine] [--app D:\AAA\ipa\app-stage\Payload\Doutu.app]

流程：SFTP 递归上传 → ldid -S 伪签名 → uicache 刷新桌面 → 重启 SpringBoard。
"""
import argparse
import os
import posixpath
import stat
import sys

import paramiko


def upload_dir(sftp, local_dir, remote_dir):
    try:
        sftp.mkdir(remote_dir)
    except IOError:
        pass
    for name in os.listdir(local_dir):
        lp = os.path.join(local_dir, name)
        rp = posixpath.join(remote_dir, name)
        st = os.lstat(lp)
        if stat.S_ISDIR(st.st_mode):
            upload_dir(sftp, lp, rp)
        elif stat.S_ISLNK(st.st_mode):
            # 软链接（Frameworks 里很少见，但保留结构）
            try:
                sftp.symlink(os.readlink(lp), rp)
            except Exception:
                pass
        else:
            sftp.put(lp, rp)
            sftp.chmod(rp, 0o755 if os.access(lp, os.X_OK) else 0o644)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="192.168.1.170")
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default="alpine")
    ap.add_argument("--app", default=r"D:\AAA\ipa\app-stage\Payload\Doutu.app")
    ap.add_argument("--dest", default="/Applications/Doutu.app")
    args = ap.parse_args()

    if not os.path.isdir(args.app):
        print(f"✗ 本地没有 {args.app}")
        return 1

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"→ 连接 {args.user}@{args.host}")
    client.connect(args.host, port=22, username=args.user, password=args.password,
                   timeout=15, allow_agent=False, look_for_keys=False)

    def run(cmd):
        _, out, err = client.exec_command(cmd, timeout=120)
        o = out.read().decode("utf-8", "replace").strip()
        e = err.read().decode("utf-8", "replace").strip()
        return o, e

    print("→ 检查环境")
    o, _ = run("uname -a; echo ---; which ldid uicache; echo ---; ls -d /Applications")
    print(o)

    print(f"→ 上传 {args.app} → {args.dest}")
    # 先删旧版，避免残留文件
    run(f"rm -rf {args.dest}")
    sftp = client.open_sftp()
    upload_dir(sftp, args.app, args.dest)
    sftp.close()

    print("→ 伪签名 + 刷新桌面")
    o, e = run(
        f"chmod +x {args.dest}/Doutu; "
        f"ldid -S {args.dest}/Doutu 2>&1 || echo 'ldid 失败（设备上可能没装 ldid）'; "
        f"uicache -a 2>&1 || uicache 2>&1 || true; "
        f"killall -9 SpringBoard 2>&1 || true"
    )
    print(o or e)

    o, _ = run(f"ls -ld {args.dest}; ls {args.dest} | head -6")
    print(o)
    client.close()
    print("✓ 完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
