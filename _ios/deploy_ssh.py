"""把 CI 产物直接装进越狱 iPhone（SSH + ldid），不需要签名/数据线。

用法：
    python _ios/deploy_ssh.py [--ipa D:\\AAA\\ipa\\_artifacts\\Doutu-unsigned.ipa]
                              [--host 192.168.1.171] [--password alpine]
                              [--keep-data]

相对 `jb-install.py` 的改进：
  * 自动找当前安装的 Doutu.app（不写死 UUID）
  * 上传前给 Info.plist 注册 `doutu://` 调试 scheme（这样 SSH 里可以 `uiopen doutu://` 启动）
  * 用旧包的 entitlements 做 ldid -S，保住 Keychain（登录态）与数据容器
  * 把已开通会员的偏好写回所有数据容器，截图不会出现「未开通」水印
"""
import argparse
import os
import plistlib
import posixpath
import shutil
import stat
import sys
import time
import zipfile

import paramiko

OLD_CONTAINER_PREFS = ("/var/mobile/Containers/Data/Application/"
                       "3F9C859D-C14B-4552-8800-57AC45E4619F/Library/Preferences/"
                       "com.luoyun.doutu.plist")


def run(client, cmd, timeout=120):
    _, out, err = client.exec_command(cmd, timeout=timeout)
    return (out.read().decode("utf-8", "replace").strip(),
            err.read().decode("utf-8", "replace").strip())


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
            try:
                sftp.symlink(os.readlink(lp), rp)
            except Exception:
                pass
        else:
            sftp.put(lp, rp)
            mode = 0o755 if os.access(lp, os.X_OK) else 0o644
            sftp.chmod(rp, mode)


def prepare(ipa, stage):
    if os.path.isdir(stage):
        shutil.rmtree(stage)
    os.makedirs(stage)
    with zipfile.ZipFile(ipa) as z:
        z.extractall(stage)
    app = os.path.join(stage, "Payload", "Doutu.app")
    if not os.path.isdir(app):
        raise SystemExit("IPA 里没有 Payload/Doutu.app")
    # 调试用 URL scheme：SSH 里 `uiopen doutu://` 就能启动（正式包不会带这个）
    info_path = os.path.join(app, "Info.plist")
    with open(info_path, "rb") as f:
        pl = plistlib.load(f)
    pl["CFBundleURLTypes"] = [{
        "CFBundleURLName": "com.luoyun.doutu.debug",
        "CFBundleURLSchemes": ["doutu"],
    }]
    # 调试期也要能走 127.0.0.1 回环 HTTP（正式包由 CI 打包步骤写同样的 ATS）
    ats = pl.setdefault("NSAppTransportSecurity", {})
    ats["NSAllowsLocalNetworking"] = True
    ats["NSExceptionDomains"] = {
        "localhost": {"NSExceptionAllowsInsecureHTTPLoads": True,
                      "NSIncludesSubdomains": True},
        "127.0.0.1": {"NSExceptionAllowsInsecureHTTPLoads": True},
    }
    with open(info_path, "wb") as f:
        plistlib.dump(pl, f, fmt=plistlib.FMT_BINARY)
    return app


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ipa", default=r"D:\AAA\ipa\_artifacts\Doutu-unsigned.ipa")
    ap.add_argument("--host", default="192.168.1.171")
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default="alpine")
    ap.add_argument("--stage", default=r"D:\AAA\ipa\app-stage3")
    ap.add_argument("--keep-data", action="store_true")
    ap.add_argument("--copy-prefs", action="store_true",
                    help="把旧容器的会员状态 plist 覆盖到所有容器（会清掉收款记录等用户数据，默认关闭）")
    args = ap.parse_args()

    app = prepare(args.ipa, args.stage)

    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(args.host, 22, username=args.user, password=args.password,
              timeout=15, allow_agent=False, look_for_keys=False)

    out, _ = run(c, "find /var/containers/Bundle/Application -maxdepth 3 -name Doutu.app "
                    "-type d 2>/dev/null")
    paths = [line.strip() for line in out.splitlines() if line.strip()]
    if not paths:
        raise SystemExit("设备上找不到已安装的 Doutu.app（先用 jb-install.py 装一次）")
    # 有多个时选最近修改的那个
    def mtime(path):
        o, _ = run(c, "stat -c %%Y %s 2>/dev/null || stat -f %%m %s" % (path, path))
        try:
            return int(o.split()[0])
        except Exception:
            return 0
    paths.sort(key=mtime, reverse=True)
    dest = paths[0]
    print("device app:", dest)

    run(c, "killall -9 Doutu 2>/dev/null; killall -9 cfprefsd 2>/dev/null; echo ok")
    print("uploading ...")
    sftp = c.open_sftp()
    run(c, "rm -rf %s" % dest)
    upload_dir(sftp, app, dest)
    sftp.close()
    print("uploaded")

    # 用旧包 entitlements 签名：保住 keychain access group，登录态才不会丢
    run(c, "test -s /tmp/ent.plist || ldid -e /tmp/olddoutu > /tmp/ent.plist 2>/dev/null || true")
    o, e = run(c, "ldid -S/tmp/ent.plist %s/Doutu 2>&1; echo rc=$?" % dest)
    print("ldid:", o or e)

    # 会员状态：默认不再覆盖 plist（旧逻辑会把 receipt.records 等用户数据一起清掉）。
    # 只有显式 --copy-prefs 时才做旧容器的整份拷贝。
    if args.copy_prefs and not args.keep_data:
        o, _ = run(c, "find /var/mobile/Containers/Data/Application -name "
                       "'com.luoyun.doutu.plist' -path '*/Library/Preferences/*' 2>/dev/null")
        targets = [line.strip() for line in o.splitlines() if line.strip()]
        for target in targets:
            run(c, "cp %s %s 2>/dev/null; chown mobile:mobile %s; chmod 600 %s"
                % (OLD_CONTAINER_PREFS, target, target, target))

    run(c, "uicache -a; echo ok")
    time.sleep(1)
    run(c, "uiopen doutu://")
    time.sleep(4)
    ps, _ = run(c, "ps aux | grep -i Doutu | grep -v grep")
    print(ps or "(app not running yet)")
    c.close()
    print("DONE")


if __name__ == "__main__":
    main()
