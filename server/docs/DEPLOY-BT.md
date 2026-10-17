# 宝塔面板部署指南（域名 + HTTPS）

> 这份是从零到跑通的**操作步骤**。原理与设计见 [`DEPLOY.md`](DEPLOY.md)，
> 安装包与配置文件都在 [`../deploy/](../deploy)。
>
> ✅ **本文档描述的部署已经在一台真实服务器上完成并通过验收**，
> 实际环境与踩坑记录见文末「附录 A：本次部署实录」，照着做能避开那 4 个坑。

**先明确三件事，后面所有步骤都围绕它们：**

| 约定 | 值（示例，实际替换成你自己的） |
|---|---|
| 站点域名 | `api.example.com`（**必须已经解析到服务器 IP**，并完成备案） |
| 代码目录 | `/www/wwwroot/api.example.com/`（Node 服务端直接放这里，`media-parser/` 放它下面） |
| 对外地址 | `https://api.example.com`（客户端只认这个域名，**App 里不存任何 IP**） |

---

## 0. 客户端为什么不用改代码也能换服务器

App 的接口地址与签名密钥都是**编译期注入**的（`doutu/app/build.gradle`）：

```bash
# 调试包（内网联调，允许 http 和长按改地址）
./gradlew assembleDebug

# 正式包（域名 + HTTPS，必须显式给服务端密钥）
./gradlew assembleRelease \
  -PdoutuApi=https://api.example.com \
  -PmasterKey=<服务器 .env 里的 MASTER_KEY>
```

两个保护：正式包不带 `-PmasterKey` **直接编译失败**；`-PdoutuApi` 写 `http://` 也直接失败。
正式包的网络策略（`res/xml/network_security_config.xml`）**禁止一切明文 HTTP**，
所以客户端物理上连不了 `http://IP`。换服务器、加 CDN、换证书都只要重新打包，不用改一行代码。

---

## 1. 宝塔装齐软件

宝塔面板 → **软件商店**，依次安装：

| 软件 | 版本建议 | 用途 |
|---|---|---|
| Nginx | 1.24+ | 反向代理 + HTTPS 终止 |
| MySQL | 5.7 / 8.0 | 生产数据库（本地开发用 SQLite，线上必须换 MySQL） |
| Node.js 版本管理器 | 装 **Node 20 LTS** | 跑 API 与 worker |
| Python 项目管理器 | 装 **Python 3.10+** | 跑 media-parser 解析引擎 |
| Supervisor 管理器 | 最新 | 守护 api / worker / media-parser 三个进程 |
| Redis | 5.0+（可选但建议） | 多进程共享限流与 nonce；不装也能跑单机 |
| FFmpeg | 4.4+ | 服务端合成水印；宝塔软件商店若没有，用命令行装（见下） |

FFmpeg 命令行安装（Debian/Ubuntu 系）：

```bash
apt update && apt install -y ffmpeg fonts-wqy-zenhei
which ffmpeg                      # 记住路径，下面 .env 要用
```

> 字体包 `fonts-wqy-zenhei` 是给水印画中文用的，不装会画成方块。

---

## 2. 建站点 + 申请证书

1. 面板 → **网站 → 添加站点**
   - 域名：`api.example.com`
   - 根目录：`/www/wwwroot/api.example.com`
   - PHP 版本：**纯静态**（本项目没有 PHP）
   - 数据库：不创建（第 4 步单独建）
2. 站点 → **SSL → Let's Encrypt**，勾选域名申请；签发后打开 **强制 HTTPS**
3. 站点 → **配置文件** 先放着，第 6 步替换成 `deploy/nginx.conf`

---

## 3. 上传代码

把本地这两块传上去（宝塔文件管理器上传 zip 后解压，或 `scp`）：

```
/www/wwwroot/api.example.com/            ← server/ 里的内容直接放这一层
├── src/  public/  scripts/  deploy/  docs/  sql/  package.json  .env.example
└── media-parser/                        ← 整个 media-parser 目录放这里
```

命令行方式（在本地开发机执行）：

```bash
# 服务端
scp -r D:/AAA/doutu/server/* root@<服务器IP>:/www/wwwroot/api.example.com/
# 解析引擎
scp -r D:/AAA/doutu/media-parser root@<服务器IP>:/www/wwwroot/api.example.com/
```

传完设权限（宝塔默认站点用户是 `www`）：

```bash
chown -R www:www /www/wwwroot/api.example.com
chmod -R 755 /www/wwwroot/api.example.com
```

---

## 4. 建数据库

面板 → **数据库 → 添加数据库**：

- 数据库名：`doutu_prod`
- 用户名：`doutu_prod`
- 密码：**点随机生成并复制下来**
- 访问权限：**本地服务器**（不要开外网）

---

## 5. 配置 .env

```bash
cd /www/wwwroot/api.example.com
cp .env.example .env
# 生成两个随机密钥
openssl rand -hex 32     # → MASTER_KEY（客户端打包要用同一个！）
openssl rand -hex 32     # → ADMIN_TOKEN_SALT / 其它密钥
```

要改的关键项（其余保持默认即可）：

```ini
NODE_ENV=production                 # 生产：不挂 /devtest，报错不外泄
BIND_HOST=127.0.0.1                 # 只让本机 Nginx 访问，不要 0.0.0.0
PORT=7101

DB_DIALECT=mysql
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=doutu_prod
MYSQL_PASSWORD=<第 4 步的密码>
MYSQL_DATABASE=doutu_prod

MASTER_KEY=<openssl rand -hex 32 的结果>   # ⚠️ 客户端正式包必须用同一个值

STORAGE_DRIVER=local
STORAGE_LOCAL_DIR=/www/wwwroot/api.example.com/storage/media
STORAGE_PUBLIC_BASE=https://api.example.com   # ⚠️ 只到域名，别带 /dw（代码自己会拼 /dw/file/…）
TMPDIR=/www/tmp

FFMPEG_PATH=/usr/bin/ffmpeg
WATERMARK_FONT=/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc

MEDIA_PARSER_BASE=http://127.0.0.1:8051       # 解析引擎（只监听回环）
REDIS_URL=redis://127.0.0.1:6379/0            # 装了 Redis 就填，没装留空
```

```bash
chown www:www .env && chmod 600 .env     # 密钥文件只有站点用户能读
mkdir -p /www/tmp /www/logs /www/wwwroot/api.example.com/storage/media
chown -R www:www /www/tmp /www/logs /www/wwwroot/api.example.com/storage
```

---

## 6. 装依赖、建表、建管理员

面板 → **网站 → Node 项目**（或命令行）执行：

```bash
cd /www/wwwroot/api.example.com
export PATH=/www/server/nodejs/v20*/bin:$PATH    # 用宝塔装的 Node 20

npm ci --omit=dev      # 没有 package-lock.json 就用 npm install --omit=dev
                       # 注：本项目零运行时依赖，只有可选的 mysql2（走 MySQL 时必须装到）
npm run db:init        # 建表 + 种子数据（幂等，可重复跑）
npm run admin:create -- --username=admin --password='<强密码>'   # 建后台管理员
```

`db:init` 会顺带把「本地解析引擎 media-parser」这条解析线路写进 `dw_route` 表
（因为第 5 步配了 `MEDIA_PARSER_BASE`）。

---

## 7. 装解析引擎 media-parser

```bash
cd /www/wwwroot/api.example.com/media-parser
bash setup-linux.sh          # 建 venv + 装依赖 + 自检
```

自检通过后它就是一个能跑的 Flask/gunicorn 服务，由 Supervisor 守着（下一步）。

---

## 8. 配 Supervisor（三个进程）

面板 → **软件商店 → Supervisor 管理器 → 添加守护进程**，
或者直接编辑 `/www/server/panel/plugin/supervisor/supervisord.conf`，
把 [`deploy/supervisor.conf`](../deploy/supervisor.conf) 里的三段（`doutu-api`、`doutu-worker`、`doutu-media-parser`）粘进去：

- `command` 里的 Node 路径改成你装的那版（如 `/www/server/nodejs/v20.18.0/bin/node`）
- `environment` 里的 `MYSQL_PASSWORD`、`MASTER_KEY` 等填真实值（**或全部删掉，让它读 `.env`**，二选一，别两处不一致）
- `user=www`

然后：

```bash
supervisorctl reread && supervisorctl update
supervisorctl status        # 三个都应是 RUNNING
```

> 三个进程的分工：`doutu-api` 只处理 HTTP；`doutu-worker` 跑 FFmpeg（吃 CPU，独立重启不影响接口）；
> `doutu-media-parser` 是解析引擎（Python），只监听 127.0.0.1:8051。

---

## 9. 换 Nginx 配置

面板 → 站点 → **配置文件**，把内容换成 [`deploy/nginx.conf`](../deploy/nginx.conf)：

1. `server_name` 改成你的域名（文件里是 `api.example.com`）
2. `ssl_certificate` / `ssl_certificate_key` 用宝塔生成的路径
   （通常是 `/www/server/panel/vhost/cert/<域名>/fullchain.pem`）
3. 把文件末尾「附录 A」里三条 `limit_req_zone` / `limit_conn_zone` **粘到面板的 http 段**
   （宝塔：软件商店 → Nginx → 配置修改，粘到 `http {}` 里），否则 `limit_req zone=...` 会报未知 zone
4. `root` 指向 `/www/wwwroot/api.example.com/public`
5. 保存后重载：

```bash
nginx -t && nginx -s reload
```

---

## 10. 定时任务

面板 → **计划任务**，按 [`deploy/crontab.txt`](../deploy/crontab.txt) 加 8 条（脚本路径、日志路径照抄文件里的）。
至少别漏这三条：到期扫描、任务补偿、日志清理。

---

## 11. 上线自检（照着敲一遍）

```bash
# 1) 本机直连后端
curl -s http://127.0.0.1:7101/healthz          # {"code":0,...,"db":"up"}

# 2) 走域名（HTTPS）
curl -s https://api.example.com/healthz
curl -s https://api.example.com/api/v1/config | head -c 300

# 3) 解析引擎
curl -s http://127.0.0.1:8051/api/health        # {"status":"ok"}

# 4) 后台能打开（浏览器）
#    https://api.example.com/admin

# 5) 敏感文件必须拿不到
curl -s -o /dev/null -w '%{http_code}\n' https://api.example.com/.env          # 期望 404/403
curl -s -o /dev/null -w '%{http_code}\n' https://api.example.com/src/app.js    # 期望 404

# 6) 真机：装上 App → 注册 → 后台开通会员 → 解析一条真实链接 → 下载
```

**签名类问题先看服务器时间**：客户端签名带时间戳（默认允许 ±300 秒），
服务器时间不准会全场 1003。宝塔 → 面板设置 → 开启时间同步。

---

## 12. 打客户端正式包

```bash
cd D:/AAA/doutu/doutu

./gradlew assembleRelease \
  -PdoutuApi=https://api.example.com \
  -PmasterKey=<第 5 步那个 MASTER_KEY> \
  -PdoutuStoreFile=<keystore 路径> \
  -PdoutuStorePassword=<密码> \
  -PdoutuKeyAlias=<别名> \
  -PdoutuKeyPassword=<密码>
```

产物：`app/build/outputs/apk/release/app-release.apk`（不带签名参数时是 `-unsigned.apk`，需再签）。
装到手机后打开「我 → 设置」，页面底部显示的**服务端就是域名**，没有任何 IP。

---

## 常见问题

| 现象 | 原因与处理 |
|---|---|
| 502 Bad Gateway | Node 没起来。`supervisorctl status` + `tail -f /www/logs/doutu-api.err.log` |
| 接口全返回 1003（时间戳） | 服务器时间不准，或 `MASTER_KEY` 客户端与服务端不一致（重打包客户端） |
| 1002（签名失败） | 同上；另外确认客户端没被中间设备改写请求 |
| 解析一直失败 | `supervisorctl tail -f doutu-media-parser stderr`；国内服务器要能出网；小红书/视频号需要配 Cookie（`media-parser/docs/cookie-config.md`） |
| 下载 404 | `STORAGE_PUBLIC_BASE` 写成了 `https://域名/dw`，改成 `https://域名`（代码自己拼 `/dw/file/…`） |
| 头像不显示 | Nginx 少了 `location ^~ /avatar/`（老配置没有，用最新 `deploy/nginx.conf`） |
| 上传头像报「请求体过大」 | 用的是旧版代码（请求体上限 1MB）；新版已提到 2MB，头像会压到 320px |
| 成品下载链接 15 分钟就失效 | 设计如此（`URL_TTL_SEC=900`）；要放宽改这个配置 |

---

## 回滚

每次发版前备份这三样，出问题 5 分钟能退回去：

```bash
cp -a /www/wwwroot/api.example.com /www/backup/api-$(date +%F-%H%M)
mysqldump -u doutu_prod -p doutu_prod > /www/backup/db-$(date +%F-%H%M).sql
# 回滚：换回旧目录 + 导入旧库 + supervisorctl restart doutu-api doutu-worker
```

---

# 附录 A：本次部署实录

> 2026-10-15 在一台真实服务器上完成，验收命令与结果都记在这里，照着复现能避开全部坑。

## A1 实际环境

| 项 | 值 |
|---|---|
| 域名 | `tdapi.luoyun.xyz`（A 记录 → 服务器 IP，已验证解析） |
| 服务器 | Ubuntu 22.04.5 LTS / x86_64 / **2 核 1.8G 内存** / 30G 磁盘 / 1G swap |
| 出口 IP 归属 | **香港**（AS62468）—— 这点很关键，见 A4 |
| Web 服务器 | 宝塔自带 nginx 1.30.5（`/www/server/nginx`，站点配置在 `/www/server/panel/vhost/nginx/`） |
| 数据库 | 宝塔自带 **MySQL 5.7.43**（`/www/server/mysql`，数据目录 `/www/server/data`） |
| 缓存 | Redis 6.0.16，**跑在 16379**（原因见 A3） |
| 运行时 | Node 20.20.2（NodeSource）、Python 3.10.12、ffmpeg 4.4.2、certbot 1.21 |
| 代码目录 | `/www/wwwroot/tdapi.luoyun.xyz/`（Node 服务端直接放这层，`media-parser/` 在它下面） |

## A2 最终产物

| 组件 | 形态 | 端口 |
|---|---|---|
| `doutu-api` | systemd 服务，User=www | 127.0.0.1:7101 |
| `doutu-worker` | systemd 服务，User=www | — |
| `doutu-parser` | systemd 服务，gunicorn 2 worker × 4 线程 | 127.0.0.1:8051 |
| nginx 站点 | `/www/server/panel/vhost/nginx/tdapi.luoyun.xyz.conf` | 80（跳转）/ 443（HTTPS） |
| 证书 | certbot + Let's Encrypt，自动续期（`certbot.timer`） | 有效期至 2027-01-13 |
| 定时任务 | 7 个 `doutu-cron-*.timer`（到期扫描/任务补偿/线路健康/系统自检/收益汇总/清理/备份） | — |

常用运维命令：

```bash
systemctl status doutu-api doutu-worker doutu-parser
journalctl -u doutu-api -n 50 --no-pager          # 或看 /www/wwwroot/<站点>/logs/*.log
systemctl restart doutu-api doutu-worker          # 只改后端逻辑
systemctl restart doutu-parser                    # 只改解析引擎
systemctl list-timers 'doutu-cron-*'              # 定时任务
nginx -t && nginx -s reload                       # 改了站点配置
```

## A3 踩到的 4 个坑（都很隐蔽）

**① 6379 端口上是蜜罐，不是 Redis** ⚠️ 最坑的一个

这台机器装了 **HFish 蜜罐**，它在 `*:6379` 上伪装成 Redis：`PING` 返回 `ok`（真 Redis 是 `PONG`）、
`SET k v` 返回 `set success`（真是 `OK`）、**带 `NX` 的直接报参数错误**。
后果：我装的真 Redis 因为端口被占**根本没启动**，App 连到蜜罐上，
`SET NX` 永远失败 → 每个签名请求都被判成「重复请求已被拒绝」(1003)，整个接口全废。

处置：真 Redis 改到 **16379**，只监听 `127.0.0.1 ::1`：

```bash
sed -i 's/^port 6379/port 16379/' /etc/redis/redis.conf
sed -i 's/^bind .*/bind 127.0.0.1 ::1/' /etc/redis/redis.conf
systemctl restart redis-server
# 验证：redis-cli -p 16379 PING 必须是 PONG（不是 ok！）
```

**② RESP 解析把 `+OK` 解析成 Buffer**（代码已修）

`src/core/kv.js` 的 `parseResp` 原来对状态行/bulk string 返回 **Buffer** 而不是字符串，
于是 `setnx` 里的 `res === 'OK'` 恒为 false —— 语义变成「key 永远已存在」。
内存版 KV 返回的是字符串，所以**只在真 Redis 上才暴露**。
修法：解析时 `.toString('utf8')`。这次就是靠「在真服务器上跑 e2e」才抓到的。

**③ 用 root 跑过一次 node 脚本，日志文件属主变 root，www 进程写不了**

`npm run db:init` 这类命令如果用 root 跑，会在 `logs/` 下留下 root 属主的日志文件，
之后以 www 身份运行的服务启动即 `EACCES: permission denied, open .../logs/server-2026-10-15.log`。

```bash
chown -R www:www /www/wwwroot/<站点>
find /www/wwwroot/<站点> -type d -exec chmod 755 {} \;
find /www/wwwroot/<站点> -type f -exec chmod 644 {} \;
chmod 600 /www/wwwroot/<站点>/.env
```

**④ 宝塔里的 MySQL root 密码与实际不符**

面板 `default.db` 的 `config.mysql_root` 记的是 `admin`，但 MySQL 实际不认。
因为机器上还没有任何业务库，用标准方式重置了 root 密码并**同步回面板**（否则面板的数据库管理会连不上）：

```bash
/etc/init.d/mysqld stop
/www/server/mysql/bin/mysqld_safe --skip-grant-tables --skip-networking --datadir=/www/server/data &
mysql -uroot -e "FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED BY '<新密码>'; FLUSH PRIVILEGES;"
mysqladmin -uroot shutdown && /etc/init.d/mysqld start
sqlite3 /www/server/panel/data/default.db "update config set mysql_root='<新密码>' where id=1;"
```

## A4 服务器在境外会限制部分平台解析 ⚠️

本机出口 IP 是**香港**，实测：

| 平台 | 结果 |
|---|---|
| **抖音** | ✅ 解析成功（引擎日志：`Successfully fetched Douyin video detail via mobile feed API`），完整链路跑通 |
| B 站 | ❌ `MEDIA_NOT_FOUND`；引擎日志 `B站 API 返回错误: code=-404, message=啥都木有`（海外/机房 IP 被风控） |
| 微博 | ❌ 同类风控 |

同一条 B 站链接在国内 IP 的机器上 0.47s 就能解析 —— 所以**这是 IP 问题，不是部署问题**。

三种解法，按推荐顺序：

1. **给解析引擎配国内动态代理**（引擎原生支持，改动最小）：
   在 `media-parser/.env` 里配 `DYNAMIC_PROXY_API_URL`（兼容芝麻代理/巨量 IP/青果等，
   自带三 IP 重试与 `PROXY_UNAVAILABLE`/`PROXY_RETRY_EXHAUSTED` 错误码），
   详见 `media-parser/docs/proxy-retry.md`。
2. **加一条国内第三方解析 API 作为主线路**：后台「解析线路」里把 `第三方API兜底` 启用并填接口，
   系统本来就有线路降级逻辑，服务端 IP 就不重要了。
3. **换国内服务器**（最彻底，但成本最高）。

## A5 验收记录（全部通过）

```bash
# 外网 HTTPS
curl -s https://tdapi.luoyun.xyz/healthz
# → {"code":0,"msg":"ok","data":{"service":"doutu-server","env":"production","db":"up",...}}

# HTTP 自动跳 HTTPS
curl -sI http://tdapi.luoyun.xyz/healthz | head -1        # → 301

# 敏感路径
/.env → 403    /src/app.js → 403    /docs/*.md → 403    /package.json → 404    /storage/* → 403
# 其它
/avatar/1 → 404    /nonexistent → 404    TLS 校验结果 = 0（证书有效）
```

在服务器上跑完整自检（**生产方言 MySQL 5.7**）：

```
E2E_DIALECT=mysql node scripts/e2e.js   →  通过 153 项，失败 0 项（建表 29 张）
```

真实链路（真解析引擎 + 真下载，抖音链接）：

```
⑤ 解析受理    platform=douyin  quota_left=49
⑥ worker      12 秒完成（下载原片 → ffmpeg 合成）
⑦ 成品        1.37 MB，out_url = https://tdapi.luoyun.xyz/dw/file/...?exp=...&sign=...
⑧ 下载        HTTP 200，1,439,577 字节 = out_size，Content-Type: video/mp4
⑨ 篡改签名    HTTP 401
会员门禁      未开通时 /dw/parse → 2001「去水印是会员功能，请先联系客服开通会员」
```

## A6 本次部署产生的凭据（都存在服务器 `/root/` 下，权限 600）

| 用途 | 文件 | 说明 |
|---|---|---|
| 客户端打包用 | `/root/.doutu_master_key` | 与服务端 `.env` 的 `MASTER_KEY` 一致，**打正式包必须传这个值** |
| 后台管理员 | `/root/.doutu_admin_pass` | 用户名 `admin`，首次登录后请改密码 |
| 业务库 | `/root/.doutu_db_pass` | MySQL 用户 `doutu` 的密码 |
| MySQL root | `/root/.mysql_root_pass` | 同时已同步进宝塔面板 |

> 建议部署完就把这几个文件收好或删掉，密码在「后台 → 账号安全」里轮换一次。

## A7 客户端连不上生产的一个大坑：装错包 ⚠️

线上反馈「后台生成了邀请码，客户端用不了」。排查结论是**App 根本没连到这台服务器**：

```bash
# 1) 看服务端访问日志里有没有客户端请求（关键一步）
grep '/api/v1/' /www/wwwlogs/tdapi.luoyun.xyz.log | tail
#   → 一条都没有，只有后台网页的 /admin/api/*

# 2) 把手机上装的 APK 拉回来，看它内置的服务端地址
adb shell pm path com.luoyun.doutu
adb pull <上面的路径> installed.apk
python -c "import zipfile,re;z=zipfile.ZipFile('installed.apk');b=b''.join(z.read(n) for n in z.namelist() if n.endswith('.dex')).decode('latin1');print([s for s in ['https://tdapi.luoyun.xyz','http://192.168.1.96:7199','https://api.example.com'] if re.search(re.escape(s),b)])"
#   → ['http://192.168.1.96:7199']   ← 装的是联调包，连的是开发机！
```

**根因**：地址是编译期注入的，装的是**调试包**（默认 `http://<开发机内网IP>:7199`）。
生产的邀请码在生产的库里，客户端问的是开发机，两边对不上 → 看起来就是「邀请码用不了」。
（同理，如果打正式包时忘了传 `-PdoutuApi`，会连到占位域名 `https://api.example.com`，一样连不上。）

**判断与处理**：

| 现象 | 说明 |
|---|---|
| App 里「我 → 设置」页底部显示 `http://192.168.x.x` | 装的是联调包，不是生产包 |
| 显示 `https://api.example.com` | 打正式包时漏了 `-PdoutuApi` |
| 显示 `https://tdapi.luoyun.xyz` | 对了（正式包不会提示"长按标题可改"） |

修复就是**用生产域名重新打包并安装**（正式包签名与调试包不同，必须先卸载）：

```bash
cd D:/AAA/doutu/doutu
gradlew.bat assembleRelease ^
  -PdoutuApi=https://tdapi.luoyun.xyz ^
  -PmasterKey=<服务器 /root/.doutu_master_key> ^
  -PdoutuStoreFile=keystore\doutu-release.jks ^
  -PdoutuStorePassword=doutu2026 ^
  -PdoutuKeyAlias=doutu ^
  -PdoutuKeyPassword=doutu2026
adb uninstall com.luoyun.doutu
adb install -r app/build/outputs/apk/release/app-release.apk
```

> ⚠️ 两个打包细节（都踩过）：
> 1. `signingConfigs {}` 必须写在 `buildTypes {}` **之前**，否则报 `Could not get unknown property 'release'`；
> 2. keytool 默认生成 **PKCS12** 密钥库，`-keypass` 会被忽略 —— store 与 key 必须是**同一个密码**，否则报 `Given final block not properly padded`。

## A8 邀请码 ≠ 会员：新号注册后能不能直接用

邀请码只决定「能不能注册」，**不决定会员**。生成邀请码时那个「时长」字段才是关键：

| 生成时填的时长 | 注册出来的账号 | 客户端表现 |
|---|---|---|
| **0（默认）** | 未开通会员 | 能登录、能进 App，但点「短视频一键去水印」会提示 **「去水印是会员功能，请先联系客服开通会员」**（服务端 `2001`）。要在「账号管理 → 开通会员」里开通后才可用 |
| **> 0（如 30 天）** | 注册即开通，到期时间 = 注册时间 + 时长 | 注册完直接就能解析、成品无水印 |

这是最初定的「后台管理账号激活」模型。生产实测：用时长=0 的码注册 → 解析被拦 `2001`；
后台开通 30 天后 → 同一条抖音链接解析成功，成品标注「抖音 · 1.4 MB · **无水印（会员）**」。
所以**发给用户的邀请码，按需要把时长填上**（要想注册即可用就填 30）。

真实链路验收（手机端，生产环境）：

```
App 启动 → /api/v1/config 200
注册     → POST /api/v1/auth/register 200（UA: Dalvik/2.1.0 ... V2220A）
          邀请码标记 used_by=17 / used_phone=15972450520
解析     → 解析中 45% → 解析完成：抖音 · 1.4 MB · 无水印（会员）
下载     → 已保存到相册：DCIM/豆图/xxx.mp4（+ 同名封面图）
```

## A9 客户端 bug：详情页里底部标签栏点不动（已修）

**现象**（4 台手机实测一致）：在「我」页面点进任意一个入口（人工客服 / 求新功能 / 邀请好友…）之后，
底部「首页 / 娱乐 / 我」三个标签**照样画着**，但点上去**完全没反应**，只能点左上角「返回」。

**根因**（`MainActivity.NativeView.onTouchEvent`）：判断顺序反了。

```java
// 旧代码：详情态先 return，底部导航根本没机会执行
if (detailTitle != null) {           // drawDetail 里调了 drawFooterAndNav → 标签栏是画着的
    if (y < 210) detailTitle = null; // 只有左上角返回有效
    return true;                     // ← 其余点击全被吞掉
}
if (bottom == 2) { ...底部导航判定... }
if (y >= 2212)   { ...底部导航判定... }
```

**修法**：把底部导航判定提到 `detailTitle` 分支**之前**，并在切页时清掉 `detailTitle`（切页即退出详情态）。
验证方式是可复现的像素对比：修复前「详情页点首页」画面变化 **0%**，修复后 **54%**（娱乐详情 → 我：14%）。

> 这类「控件画出来了但点击被别的分支吞掉」的问题，真机复现 + 截图像素对比比读代码快得多，
> 建议以后遇到交互问题都先固定一条路径，逐步截图对比。


