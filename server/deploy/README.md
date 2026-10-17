# 短视频去水印平台 · 一页式部署说明

> 目标环境：**Ubuntu 22.04 + 宝塔面板**；首期容量：注册用户 ≤500、峰值并发 ≤50。
> 本目录的 4 个文件（`nginx.conf` / `supervisor.conf` / `crontab.txt` / `deploy.sh`）
> 是被部署的产物；更详细的运维手册见上一级 `docs/DEPLOY.md`。

> ✅ **落地状态**：本目录的全部产物都已对齐代码库，可直接使用。
> 入口齐全：`src/index.js`（API）、`src/worker.js`（媒体进程）、
> `src/cron/*.js`（9 个定时任务 CLI，含 `backup.js`）、`scripts/create-admin.js`、
> `package-lock.json` 也已在仓库中，`npm ci` 可用。
> 建表与初始数据由 `npm run db:init` 完成（28 张表），后台前端在 `web/admin/`。

服务端形态（已定，不要再改动）：

| 项目 | 约定 |
| --- | --- |
| 代码根目录 | `/www/wwwroot/api.example.com` |
| 业务入口 | `node src/index.js`，监听 `127.0.0.1:7101`（`PORT` 可覆盖） |
| 媒体 worker | `node src/worker.js`（独立进程，Supervisor 守护） |
| 定时任务 | `node src/cron/*.js`（crontab 触发，非常驻） |
| 静态目录 | 仅暴露 `public/`，`public/upgrade/` 放版本包；后台前端在 `web/admin/` |
| 后台 | 由 Node 自身托管在 `/admin`（`ADMIN_PATH` 可改） |
| 数据库 | 生产 MySQL 8.0（utf8mb4）；本地开发 SQLite（`node:sqlite`，零依赖） |
| Redis | 可选，`REDIS_URL` 为空即进程内内存模式 |
| 内网端口 | 业务 7101 / 解析引擎 8051 / MySQL 3306 / Redis 6379，**全部只监听 127.0.0.1** |

---

## 0. 一句话流程

```
买服务器 → 宝塔初始化 → 域名解析 + 证书 → 建库建站 → 上传代码
        → 写 .env → 初始化库与管理员 → Supervisor → crontab → Nginx → 验证清单
```

---

## 1. 买服务器与基础安全

1. 规格建议：**2C4G 起**（ffmpeg 是 CPU 密集，CPU 核数直接决定出片速度），
   系统盘 40G + **数据盘 100G 起**（媒体中间文件与成品很吃空间）。
2. 系统选 **Ubuntu 22.04 LTS**，不要选 CentOS 7（已 EOL）。
3. 云厂商安全组只放行 **22 / 80 / 443**；
   `7101`、`8051`、`3306`、`6379` **绝对不要** 对公网开放。
4. 首登后先做：
   ```bash
   apt-get update && apt-get upgrade -y
   timedatectl set-timezone Asia/Shanghai
   timedatectl set-ntp true          # 必须！签名时间窗 SIGN_WINDOW_SEC=300 依赖准确时钟
   timedatectl status
   ```
   > 时钟漂移超过 5 分钟 → 客户端签名全量校验失败 → 全站 401。这是本项目第一号事故来源。

## 2. 宝塔面板初始化

```bash
# 官方安装脚本（Ubuntu/Debian）
wget -O install.sh https://download.bt.cn/install/install-ubuntu_6.0.sh && bash install.sh
```

安装完成后按顺序做：

1. **改面板端口**：面板 -> 设置 -> 面板端口，从默认 `8888` 改成高位随机端口；
   同时只放行你自己的管理 IP。
2. **面板 SSL**：设置 -> 面板 SSL -> 开启（自签或上传证书），避免面板密码明文传输。
3. **IP 白名单**：设置 -> 授权 IP / 防火墙，限制面板访问来源 IP。
4. **绑定宝塔账号 + 开启告警**：设置 -> 消息通知（服务器 CPU/磁盘异常可第一时间收到）。
5. **软件商店安装**：
   - `Nginx`（1.22+，TLS 1.3 必需）
   - `MySQL 8.0`（务必 8.0，字符集 utf8mb4）
   - `Supervisor 管理器`
   - `Node.js 版本管理器` → 安装 **Node.js 20 LTS**（见第 8 节说明）
   - `Redis`（可选；首期单机可不装，内存模式够用）
6. **系统依赖**（用宝塔终端或 SSH）：
   ```bash
   apt-get install -y ffmpeg fonts-wqy-zenhei git curl unzip
   ffmpeg -version | head -1                          # 验证 ffmpeg
   fc-list | grep -i wqy                              # 验证中文字体（水印用）
   ```
   > 水印文字依赖中文字体，`WATERMARK_FONT` 指向
   > `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc`；缺字体时水印会渲染成方块（□□□）。

### 2.1 为什么不需要 PHP

规划书早期把 PHP-FPM 列为候选，**最终决定全部用 Node.js 实现**，因此：

- 建站时**不要**创建 PHP 版本、不要装 PHP-FPM；
- 不需要 `php.ini`、不需要 `open_basedir`、不需要 `disable_functions` 加固；
- 站点是「纯静态 + 反向代理」形态，所有动态请求都由 Nginx 转给 `127.0.0.1:7101` 的 Node 进程。

这样做的收益：少一层运行时、少一套权限模型、少一个 502 来源；部署产物只需 Supervisor + crontab + Nginx。

> 如果面板强制要求选择 PHP 版本，选「纯静态」即可；后续在站点配置文件里按 `deploy/nginx.conf` 覆盖。

## 3. 域名与证书

1. DNS：`api.example.com` A 记录指向服务器公网 IP（建议同时配 `www` 或另起 `admin` 子域）。
2. 宝塔 -> 网站 -> 添加站点 `api.example.com`，类型选**纯静态**，不要建数据库（数据库单独建，见下）。
3. 站点 -> SSL -> Let's Encrypt 申请证书，开启**强制 HTTPS**；
   宝塔会写入 `fullchain.pem` / `privkey.pem`，路径与 `deploy/nginx.conf` 中的占位一致：
   ```
   /www/server/panel/vhost/cert/api.example.com/fullchain.pem
   /www/server/panel/vhost/cert/api.example.com/privkey.pem
   ```
   > 若路径不同，请以面板实际写入为准修改 `nginx.conf`（宝塔「部署证书」会自动覆写这两行）。
4. 证书续签：Let's Encrypt 是 90 天，宝塔默认自动续签；
   但**必须加监控**：到期前 15 天告警（见 `docs/DEPLOY.md` 监控阈值表），
   自动续签失败时人工介入，否则全站 HTTPS 直接不可用。
5. 开启 OCSP 装订：`nginx.conf` 已配置 `ssl_stapling on`，需保证服务器能访问
   `223.5.5.5 / 119.29.29.29`（国内 DNS，已写入配置）。

## 4. 建库

宝塔 -> 数据库 -> 添加数据库：

| 项 | 值 |
| --- | --- |
| 数据库名 | `doutu` |
| 用户名 | `doutu`（不要用 root） |
| 密码 | 32 位随机串：`openssl rand -base64 24` |
| 字符集/排序 | `utf8mb4` / `utf8mb4_unicode_ci` |
| 访问权限 | **本地服务器**（即 127.0.0.1，不对公网开放） |

同时确认 MySQL 只监听回环：

```bash
ss -lntp | grep 3306          # 应显示 127.0.0.1:3306 而不是 0.0.0.0:3306
```

## 5. 上传代码

推荐用 git（便于 `deploy.sh` 幂等更新）：

```bash
mkdir -p /www/wwwroot/api.example.com
cd /www/wwwroot/api.example.com
git clone <你的仓库地址> .
```

没有仓库时用面板上传压缩包解压，但**必须**保证目录结构与约定一致：

```
/www/wwwroot/api.example.com
├── src/                 # index.js / worker.js / cron/*.js
├── public/       # 唯一对外静态目录（含 upgrade/）
├── deploy/              # 本目录（nginx.conf / supervisor.conf / crontab.txt / deploy.sh）
├── docs/                # 运维文档
├── package.json / package-lock.json
└── .env                 # 由 .env.example 复制而来，权限 600
```

> ⚠️ 不要把 `.env`、`src/`、`node_modules/` 放进 `public/`。

## 6. 配置 `.env`

```bash
cp .env.example .env          # 若仓库提供模板
chmod 600 .env
chown www:www .env
```

`.env` **完整字段表（名称 / 示例 / 是否必填 / 说明）见 `docs/DEPLOY.md`「环境变量全表」**。
最少必填项：

| 变量 | 说明 |
| --- | --- |
| `NODE_ENV=production` | 生产模式 |
| `DB_DIALECT=mysql` | 生产用 MySQL；本地开发用 `sqlite` |
| `MYSQL_HOST=127.0.0.1` / `MYSQL_PORT=3306` / `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_DATABASE` | 第 4 步建库信息（`MYSQL_USER` 代码默认是 root，务必覆盖） |
| `MASTER_KEY` | **64 位十六进制（32 字节）**：`openssl rand -hex 32`，用于派生会话密钥，**上线后不要更换** |
| `PORT=7101` / `BIND_HOST=127.0.0.1` | 必须与 `nginx.conf` 的 upstream 一致 |
| `TMPDIR=/www/tmp` | ffmpeg 中间文件目录，指向大容量分区 |
| `FFMPEG_PATH=/usr/bin/ffmpeg` / `FFPROBE_PATH=/usr/bin/ffprobe` | 必须绝对路径（Supervisor/cron 的 PATH 极简） |
| `MEDIA_PARSER_BASE=http://127.0.0.1:8051` | 自研解析引擎地址 |
| `WATERMARK_FONT=/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc` | 中文字体；留空会由 `src/config.js` 自动探测候选路径 |
| `ADMIN_PATH=/admin` | 后台路径（已在 `.env.example` 中，改了要同步 Nginx location） |
| `PAY_PROVIDER=epay` + `EPAY_*` | 支付渠道；`.env.example` 默认是 `mock`，**生产必须改成 `epay`** |
| `STORAGE_DRIVER=local` / `STORAGE_LOCAL_DIR` / `STORAGE_PUBLIC_BASE` | 存储；接 OSS 时改 `oss` 并填 `OSS_*` |
| `ALERT_WEBHOOK` | 钉钉/企业微信机器人地址，可空但**强烈建议填**（留空则只写日志、没有任何主动告警） |

> 完整 40+ 个变量（含 `MYSQL_POOL`、`SESSION_TTL_HOURS`、`ADMIN_TOKEN_TTL_MIN`、
> `ACTIVATE_FAIL_LIMIT`、`WATERMARK_*`、`MEDIA_PARSER_KEY`、`ORIGIN_CACHE_HOURS` 等）
> 见 [`docs/DEPLOY.md`](../docs/DEPLOY.md) 第 3 节「环境变量全表」，那里逐项给出了
> 代码默认值与是否已在 `.env.example` 中。

## 7. 初始化

```bash
cd /www/wwwroot/api.example.com
npm ci --omit=dev          # 生产依赖，严格按 lockfile
npm run db:init            # 建表 + 种子数据（28 张表，幂等）
npm run admin:create       # 创建管理员（幂等）
```

状态说明（与 `docs/DEPLOY.md` 2.1 节一致）：

- `npm run db:init`（→ `scripts/init-db.js`）**已实现**：建表 + 写入种子数据，
  支持 `--drop`（先删表，危险）与 `--no-seed`（只建表）。
- 生产用 MySQL 时 `mysql2` 是必需依赖（它在 `package.json` 的 `optionalDependencies` 里，
  这样纯 SQLite 场景可以零依赖运行）。`npm ci --omit=dev` 默认会装上它；
  若你的环境跳过了可选依赖，请显式执行 `npm install mysql2`，否则启动时会看到
  「未安装 mysql2」的明确报错。
  请先提交 lockfile，或临时用 `npm install --omit=dev`。
- 生产用 MySQL 时 `mysql2` 是必需依赖，但它现在位于 `optionalDependencies`，
  建议移入 `dependencies`，以免 `--omit=dev` 场景下被跳过。

> `deploy.sh` 会调用 `db:init` 与 `admin:create`，因此**不要改名**；
> 若改名请同步修改 `deploy/deploy.sh` 与 `docs/DEPLOY.md` 的「附：约定」表。

## 8. Supervisor（守护两个常驻进程）

```bash
apt-get install -y supervisor
cp /www/wwwroot/api.example.com/deploy/supervisor.conf /etc/supervisor/conf.d/doutu.conf
supervisorctl reread && supervisorctl update
supervisorctl status
```

期望输出（两个都必须是 `RUNNING`）：

```
doutu-api      RUNNING   pid 12345, uptime 0:00:10
doutu-worker   RUNNING   pid 12346, uptime 0:00:09
```

**Node 路径务必核对**：模板里写的是宝塔默认目录 `/www/server/nodejs/v18/bin/node`，
但本项目**要求 Node.js ≥ 20**（用到 `node:sqlite`、ESM 等能力）。

```bash
ls /www/server/nodejs/                       # 例如看到 v20.18.0
# 把 supervisor.conf 与 crontab.txt 里的 v18 全部换成 v20.18.0：
sed -i 's#/www/server/nodejs/v18/bin/node#/www/server/nodejs/v20.18.0/bin/node#g' \
    /etc/supervisor/conf.d/doutu.conf /etc/cron.d/doutu
supervisorctl reread && supervisorctl update && supervisorctl restart doutu-api doutu-worker
```

> 若面板里只装了 18，请先在 Node.js 版本管理器里装 20 LTS：
> Node 18 已进入维护末期，且本项目约定 ≥20，用 18 会直接启动失败。

## 9. crontab（定时任务）

```bash
cp /www/wwwroot/api.example.com/deploy/crontab.txt /etc/cron.d/doutu
chown root:root /etc/cron.d/doutu && chmod 644 /etc/cron.d/doutu
systemctl restart cron
grep -E '^[0-9*]' /etc/cron.d/doutu      # 确认 8 条任务已生效
```

任务频率与理由（详见 `deploy/crontab.txt` 内注释）：

| 任务 | 频率 | 为什么是这个频率 |
| --- | --- | --- |
| `expire-check.js` 到期扫描 | 每 10 分钟 | 授权过期不及时会让过期用户继续白嫖（资损），10 分钟兼顾体感与负载 |
| `revenue-daily.js` 收益日聚合 | 每日 01:10，补算 3 天 | 避开 00:00 跨天写入峰值；渠道明细 T+1 才全，必须可重算 |
| `reconcile.js` 支付对账 | 每日 02:00 | 渠道账单已就绪且业务低峰，差异生成工单 |
| `route-health.js` 线路健康 | 每 5 分钟 | 实性时与样本量平衡，用于自动降权坏线路 |
| `cleanup.js` 临时文件清理 | 每小时 | `TMPDIR` 单任务数百 MB，磁盘满是 MySQL 拒写的头号原因 |
| 备份 + 转存 OSS | 03:00 / 03:30 | 最低峰，避开对账；本地 7 天 + OSS 30 天 |

## 10. Nginx（对外入口）

1. 宝塔 -> 网站 -> `api.example.com` -> 设置 -> **配置文件**，
   把 `deploy/nginx.conf` 的 `upstream` + `server` 段粘贴进去（或整段覆盖）。
2. **限流区必须放在 `http {}` 里**（不能写在 server 内）：
   把 `nginx.conf` 附录 A 的三行取消注释，放进
   `/www/server/nginx/conf/nginx.conf` 的 `http {}` 中；
   附录 B 的 gzip 同理（宝塔若已开启 gzip 就跳过）。
3. 校验并重载：
   ```bash
   nginx -t && systemctl reload nginx
   ```
4. 路由分流结果（必须与规划书一致）：

| 路径 | 去向 | 限流 |
| --- | --- | --- |
| `/api/v1/*` | `127.0.0.1:7101` | 20r/s，burst 40，单 IP 连接 20 |
| `/api/v1/auth/activate` | `127.0.0.1:7101` | **1r/s，burst 3**（独立 zone，防撞库） |
| `/dw/*` | `127.0.0.1:7101` | 20r/s；`proxy_read_timeout 600s`，关闭缓冲（大文件） |
| `/admin*` | `127.0.0.1:7101` | 20r/s；响应不缓存 |
| `/upgrade/*` | 静态 `public/upgrade/` | 后缀白名单，`autoindex off` |
| `/healthz` | `127.0.0.1:7101` | 不记日志，供监控高频探测 |
| `/.env`、`/.git`、`*.sql`、`*.bak`、`*.log`、`*.zip` 等 | **404 / deny** | — |

## 11. 一键部署脚本（推荐用于首次部署与后续发版）

```bash
cd /www/wwwroot/api.example.com
bash deploy/deploy.sh <git仓库地址> main
```

脚本做的事（幂等，可重复执行）：校时 → 建目录与权限 → 拉代码 → `npm ci --omit=dev`
→ `chmod 600 .env` → `npm run db:init` → `npm run admin:create`
→ 安装并重启 Supervisor → 安装 crontab → 校验并重载 Nginx → 自检。

> 首次执行会在缺少 `.env` 时从 `.env.example` 生成模板并**主动中止**，
> 提醒你填完真实值再跑一次 —— 这是有意设计，避免用模板值上线。

## 12. 日志与切割

| 日志 | 路径 | 切割 |
| --- | --- | --- |
| 应用 stdout/stderr | `/www/logs/doutu-api.{out,err}.log`、`/www/logs/doutu-worker.{out,err}.log` | Supervisor 自带：50MB 轮转、保留 7 份 |
| 应用结构化日志 | `/www/logs/`（`LOG_DIR`） | 由 `cleanup.js` 按保留天数清理 |
| cron 任务 | `/www/logs/cron-*.log` | 随定时清理任务回收 |
| Nginx | `/www/logs/nginx/api.example.com.{access,error}.log` | 宝塔默认按天切割 |

如需**按天**切割应用日志（而非按大小），自行加一份 logrotate（本目录不额外生成文件）：

```
# /etc/logrotate.d/doutu
/www/logs/doutu-*.log {
    daily
    rotate 14
    missingok
    notifempty
    compress
    delaycompress
    copytruncate
    su www www
}
```
（`copytruncate` 是因为 Supervisor 持有文件句柄，不能直接 move 后重开。）

## 13. 上线验证清单

按顺序执行，全部通过才算上线完成：

```bash
# 1) 进程存活（两个都 RUNNING）
supervisorctl status

# 2) 端口只监听回环（不能出现 0.0.0.0:7101）
ss -lntp | grep -E '7101|8051|3306|6379'

# 3) 本机健康检查（绕过 Nginx，验证 Node 自身）
curl -fsS http://127.0.0.1:7101/healthz && echo

# 4) 经过 Nginx 的 HTTPS 健康检查
curl -fsS https://api.example.com/healthz && echo

# 5) HTTP 是否强制跳转 HTTPS（期望 301）
curl -sSI http://api.example.com/healthz | head -3

# 6) 业务接口可达（期望 JSON，未登录返回 401 属正常）
curl -sS -o /dev/null -w '%{http_code}\n' https://api.example.com/api/v1/health

# 7) 激活接口限流生效（连续打 10 次，应出现 429）
for i in $(seq 1 10); do
  curl -sS -o /dev/null -w '%{http_code} ' -X POST https://api.example.com/api/v1/auth/activate
done; echo

# 8) 敏感文件必须 404
curl -sS -o /dev/null -w '%{http_code}\n' https://api.example.com/.env
curl -sS -o /dev/null -w '%{http_code}\n' https://api.example.com/.git/config
curl -sS -o /dev/null -w '%{http_code}\n' https://api.example.com/backup.sql

# 9) TLS 版本与安全头
curl -sSI https://api.example.com/healthz | grep -iE 'strict-transport|x-content-type|x-frame'
openssl s_client -connect api.example.com:443 -tls1_1 </dev/null 2>&1 | grep -i 'handshake failure'   # 期望失败

# 10) 静态升级包目录
curl -sSI https://api.example.com/upgrade/ | head -3      # 期望 404（目录不可列）

# 11) 证书有效期（剩余天数应 > 15，否则告警）
echo | openssl s_client -connect api.example.com:443 -servername api.example.com 2>/dev/null \
  | openssl x509 -noout -dates

# 12) 时间同步（NTPSynchronized=yes）
timedatectl show -p NTPSynchronized -p TimeUSec

# 13) 定时任务已注册
grep -E '^[0-9*]' /etc/cron.d/doutu

# 14) 磁盘与临时目录（/www/tmp 不要和生产数据同盘、不要 >85%）
df -h /www /www/tmp
```

端到端功能验证（人工）：

- [ ] 激活一个测试激活码，确认写库成功、过期时间正确
- [ ] 提交一条去水印任务，确认 worker 出片（看 `supervisorctl tail -f doutu-worker`）
- [ ] 通过 `/dw/<签名链接>` 下载成品，确认链接过期后返回失效
- [ ] 触发一次支付回调（联通用 `PAY_PROVIDER=mock`，生产用 `epay`），确认订单状态与收益流水一致
- [ ] 后台 `/admin` 登录、查看任务列表与收益日报
- [ ] 手工执行一次对账与备份，确认退出码为 0（这两个 CLI 需先实现，见第 7 节说明）
  ```bash
  runuser -u www -- node src/cron/reconcile.js; echo "exit=$?"
  runuser -u www -- node src/cron/backup.js --retain-days=7; echo "exit=$?"
  # 恢复演练：解密 + 解压 + 内容校验（建议每月一次）
  ```

## 14. 回滚

```bash
cd /www/wwwroot/api.example.com
git log --oneline -5                       # 找到上一个可用 commit
git reset --hard <commit>
runuser -u www -- npm ci --omit=dev
supervisorctl restart doutu-api doutu-worker
curl -fsS http://127.0.0.1:7101/healthz && echo
```

数据库回滚：数据库迁移**必须**向前兼容（先加字段再用，禁止直接删列）；
若确需回滚数据，用 `docs/DEPLOY.md` 备份策略中的最近一次全量 + binlog 定点恢复。

## 15. 相关文件

| 文件 | 用途 |
| --- | --- |
| [`deploy/nginx.conf`](./nginx.conf) | 站点 server 段（TLS、分流、限流、安全头、静态与下载） |
| [`deploy/supervisor.conf`](./supervisor.conf) | `doutu-api` / `doutu-worker` 进程守护 |
| [`deploy/crontab.txt`](./crontab.txt) | 8 条定时任务（含频率理由） |
| [`deploy/deploy.sh`](./deploy.sh) | 幂等部署脚本（`set -euo pipefail`） |
| [`docs/DEPLOY.md`](../docs/DEPLOY.md) | 详细运维手册：环境变量全表、监控阈值、备份、故障预案、排查 |
