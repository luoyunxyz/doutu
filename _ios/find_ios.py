"""在局域网里找能 root/alpine 登录的越狱 iPhone。

    python _ios/find_ios.py [网段前缀，默认 192.168.1]
"""
import concurrent.futures
import ipaddress
import socket
import sys

import paramiko


def tcp_open(host, timeout=0.45):
    s = socket.socket()
    s.settimeout(timeout)
    try:
        s.connect((host, 22))
        return True
    except OSError:
        return False
    finally:
        s.close()


def try_login(host):
    if not tcp_open(host):
        return None
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        c.connect(host, 22, username="root", password="alpine", timeout=4,
                  allow_agent=False, look_for_keys=False, banner_timeout=4,
                  auth_timeout=4)
        _, out, _ = c.exec_command("uname -n", timeout=4)
        name = out.read().decode("utf-8", "replace").strip()
        return host, name
    except Exception:  # noqa: BLE001
        return None
    finally:
        c.close()


def main(argv):
    prefix = argv[0] if argv else "192.168.1"
    net = ipaddress.ip_network(prefix + ".0/24", strict=False)
    hosts = [str(h) for h in net.hosts()]
    found = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=64) as pool:
        for res in pool.map(try_login, hosts):
            if res:
                found.append(res)
                print("FOUND %s  hostname=%s" % res, flush=True)
    if not found:
        print("no jailbroken iPhone answered on %s.0/24" % prefix)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
