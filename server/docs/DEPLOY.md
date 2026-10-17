# 短视频去水印平台 · 部署与运维手册（DEPLOY.md）

> 适用环境：**Ubuntu 22.04 LTS + 宝塔面板**，Nginx 终止 TLS，Node.js ≥ 20 单代码库多进程。
> 首期容量目标：注册用户 ≤ 500、峰值并发 ≤ 50。
> 一页式快速上手见 [`deploy/README.md`](../deploy/README.md)；本文件是长期运维依据。

---

## 目录

1. [环境要求](#1-环境要求)
2. [目录规划](#2-目录规划)
3. [环境变量全表](#3-环境变量全表)
4. [上线检查清单](#4-上线检查清单)
5. [监控与告警阈值](#5-监控与告警阈值)
6. [备份策略](#6-备份策略)
7. [故障预案](#7-故障预案)
8. [常见故障排查](#8-常见故障排查)
9. [日常运维动作](#9-日常运维动作)

---

## 1. 环境要求

### 1.1 硬件

| 项 | 最低 | 推荐 | 说明 |
| --- | --- | --- | --- |
| CPU | 2 核 | 4 核 | ffmpeg 按核吃满，`WORKER_CONCURRENCY ≈ 核数 / 1.5` |
| 内存 | 4 GB | 8 GB | ffmpeg 单任务常驻 300MB~1GB；MySQL 8.0 默认吃 1GB+ |
| 系统盘 | 40 GB | 40 GB | 只放系统、MySQL 数据与日志 |
| 数据盘 | 100 GB | 200 GB+ | `TMPDIR`、成品文件、备份；**与系统盘分开** |
| 带宽 | 5 Mbps | 10 Mbps+ | 成品视频下载是主要出口流量 |

> 磁盘是这台机器上最先耗尽的资源。`/www/tmp`（ffmpeg 中间文件）与
> `/www/wwwroot/api.example.com/storage`（成品）必须落在数据盘，
> 否则一旦写满，MySQL 会直接拒绝写入（`No space left on device`），表现为全站不可用。

### 1.2 软件

| 组件 | 版本要求 | 用途 / 备注 |
| --- | --- | --- |
| Ubuntu | 22.04 LTS | 不用 CentOS 7（已 EOL，OpenSSL 太旧不支持 TLS1.3 现代套件） |
| Nginx | ≥ 1.22 | TLS 1.3、`limit_req`、`http2` |
| Node.js | **≥ 20** | 依赖 `node:sqlite`、ESM、原生 fetch。宝塔默认可能只装 18，需手动装 20 LTS |
| MySQL | **8.0** | utf8mb4；只监听 `127.0.0.1:3306` |
| Redis | 7.x（可选） | `REDIS_URL` 为空时用进程内内存实现，首期单机可不装 |
| Supervisor | 4.x | 守护 `doutu-api` / `doutu-worker` |
| ffmpeg | ≥ 5.0 | 媒体处理；`FFMPEG_PATH` 可覆盖 |
| 中文字体 | `fonts-wqy-zenhei` | 水印文字渲染，`WATERMARK_FONT` 指向 `.ttc` |
| git / curl / openssl | 系统自带 | 部署、健康检查、密钥生成与备份加密 |
| PHP / PHP-FPM | **不需要** | 早期候选方案已废弃，全部服务端逻辑由 Node.js 实现 |

### 1.3 网络与端口

| 端口 | 服务 | 监听地址 | 公网放行 |
| --- | --- | --- | --- |
| 80 / 443 | Nginx | `0.0.0.0` | ✅ 放行 |
| 7101 | 业务 API + 后台 + 下载 | `127.0.0.1` | ❌ 禁止 |
| 8051 | 自研解析引擎 media-parser | `127.0.0.1` | ❌ 禁止 |
| 3306 | MySQL 8.0 | `127.0.0.1` | ❌ 禁止 |
| 6379 | Redis 7（可选） | `127.0.0.1` | ❌ 禁止 |
| 面板端口 | 宝塔 | 自定义高位端口 | ⚠️ 仅管理 IP |

验证命令：

```bash
ss -lntp | grep -E ':(80|443|7101|8051|3306|6379)\b'
# 期望：7101/8051/3306/6379 全部显示 127.0.0.1:xxxx，绝不出现 0.0.0.0
```

### 1.4 时钟

`SIGN_WINDOW_SEC=300`（签名时间窗 5 分钟），`URL_TTL_SEC=900`（下载链接时效 15 分钟），
收益与对账按自然日切天。因此：

```bash
timedatectl set-timezone Asia/Shanghai
timedatectl set-ntp true
timedatectl show -p NTPSynchronized -p TimeUSec    # NTPSynchronized=yes
```

> **时钟漂移是本项目的头号故障源**：偏移 >5 分钟 → 所有客户端请求签名校验失败
> → 现象是「全站 401 / 签名错误」，而代码和数据库其实都是好的。
> 排障时**第一件事**就是查时间同步。

---

## 2. 目录规划

对应规划书 2.4 节，Linux 上的实际布局如下（与 `deploy/supervisor.conf`、
`deploy/crontab.txt`、`deploy/deploy.sh` 完全一致）：

```
/www
├── wwwroot
│   └── api.example.com                 # 代码根目录（APP_DIR），属主 www:www
│       ├── src/                        # 服务端源码（ESM，"type": "module"）
│       │   ├── index.js                # 业务 API + 后台入口，监听 127.0.0.1:7101
│       │   ├── worker.js               # 媒体处理常驻进程
│       │   ├── config.js               # 配置装载（.env → process.env → 强类型配置）
│       │   ├── core/                   # crypto / http / kv / logger / money / errors …
│       │   ├── db/                     # schema.js（28 张表）/ index.js / ddl.js / seed.js
│       │   ├── services/               # user / session / alert / audit / sysconfig …
│       │   └── cron/                   # 定时任务 CLI（跑完即退出）
│       │       ├── expire-check.js      # 到期扫描（每 10 分钟）
│       │       ├── revenue-daily.js     # 收益日聚合（每日 01:10，补算 3 天）
│       │       ├── reconcile.js         # 支付对账（每日 02:00）
│       │       ├── route-health.js      # 解析线路健康（每 5 分钟）
│       │       ├── cleanup.js           # 临时文件清理（每小时）
│       │       └── backup.js            # 数据库备份/转存（每日 03:00、03:30）
│       ├── scripts/                    # init-db.js / export-sql.js / create-admin.js …
│       ├── sql/                        # schema.mysql.sql / schema.sqlite.sql（由 npm run db:sql 生成）
│       ├── server/
│       │   └── public/                 # ★ 唯一对外静态目录（Nginx root）
│       │       └── upgrade/            # 版本升级包（后缀白名单，禁止列目录）
│       ├── storage/                    # STORAGE_LOCAL_DIR 的父目录（建议软链到数据盘）
│       │   ├── media/                  # 成品/素材（STORAGE_LOCAL_DIR 默认 ./storage/media）
│       │   └── tmp/                    # TMPDIR 默认 ./storage/tmp（生产应改到 /www/tmp）
│       ├── node_modules/               # npm ci --omit=dev 产物
│       ├── deploy/                     # 部署产物
│       │   ├── nginx.conf
│       │   ├── supervisor.conf
│       │   ├── crontab.txt
│       │   ├── deploy.sh
│       │   └── README.md
│       ├── docs/                       # 本项目文档（含本文件）
│       ├── package.json                # 含 db:init / admin:create / cron:* 等 script
│       ├── package-lock.json           # ★ 必须有，否则 npm ci 不可用
│       └── .env                        # ★ 权限 600，属主 www，绝不入库、绝不放 public
├── tmp                                 # TMPDIR：ffmpeg 中间文件（必须大容量分区）
├── logs                                # LOG_DIR
│   ├── doutu-api.out.log               # Supervisor 托管，50MB 轮转
│   ├── doutu-api.err.log
│   ├── doutu-worker.out.log
│   ├── doutu-worker.err.log
│   ├── cron-*.log                      # 定时任务输出
│   └── nginx/
│       ├── api.example.com.access.log
│       └── api.example.com.error.log
├── backup
│   └── db                              # 本地备份保留 7 天（加密后可转存 OSS）
└── server
    ├── panel/vhost/nginx/              # 宝塔站点配置（api.example.com.conf）
    ├── panel/vhost/cert/api.example.com/  # 证书 fullchain.pem / privkey.pem
    └── nodejs/v20.18.0/bin/node        # Node 可执行文件（版本号以实际为准）
```

### 2.1 部署前置检查清单（**部署前必读**）

服务端代码已全部就位。部署前按下表核对一遍即可，缺失任何一项都会在启动或 cron 阶段直接报错：

| 入口 / 产物 | 状态 | 说明 |
| --- | --- | --- |
| `src/config.js`、`src/core/*`、`src/db/*`、`src/services/*` | ✅ 已有 | 配置、加密、HTTP 框架、KV（内存/Redis）、28 张表结构、种子数据 |
| `src/app.js`、`src/index.js` | ✅ 已有 | 路由装配与 API 进程入口（`doutu-api`） |
| `src/worker.js` | ✅ 已有 | 媒体处理进程入口（`doutu-worker`，独立进程跑 FFmpeg） |
| `src/modules/*` | ✅ 已有 | auth / dw / pay / config / upgrade / ticket / admin |
| `src/cron/*.js` | ✅ 已有 | 9 个 CLI：expire-check、revenue-daily、reconcile、route-health、cleanup、system-health、backup + lib |
| `src/middleware/*` | ✅ 已有 | 9 步校验链、三级限流 |
| `web/admin/` | ✅ 已有 | 后台前端（零构建静态页，由 Node 自身托管） |
| `scripts/init-db.js`、`export-sql.js`、`create-admin.js`、`smoke.js`、`e2e.js`、`check-imports.js` | ✅ 已有 | 建表、导出 DDL、建管理员、两项自检脚本 |
| `sql/schema.mysql.sql`、`sql/schema.sqlite.sql` | ✅ 已有 | 由 `npm run db:sql` 生成，勿手改（改结构请改 `src/db/schema.js`） |
| `package-lock.json` | ✅ 已有 | `npm ci` 可直接使用 |
| `package.json` 中 `mysql2` 位于 **`optionalDependencies`** | ⚠️ 注意 | 这样纯 SQLite 场景可零依赖运行；生产用 MySQL 时它**必须装上**。`npm ci --omit=dev` 默认会装可选依赖，若你的环境跳过了，请显式 `npm install mysql2`；漏装时启动会报「未安装 mysql2」并给出处理办法 |

> 结论：**代码齐备，可直接部署。** 唯一需要人工判断的是 `mysql2` 是否装上（见上表最后一行）。

> 建议部署后立即跑一次自检，确认环境无误：
> `npm run check-imports`（模块路径）→ `npm run smoke`（授权链路）→ `npm run e2e`（端到端，需要 ffmpeg）。

### 2.2 数据库对象

`npm run db:init` 会在目标库中创建以下 **28 张表**（与 `sql/schema.mysql.sql` 一致）：

`admin`、`role`、`admin_role`、`admin_token`、`app_user`、`user_profile_log`、`user_device`、
`card_batch`、`card_key`、`goods`、`order`、`payment_record`、`revenue_daily`、`reconcile_log`、
`dw_task`、`dw_route`、`license_session`、`ticket`、`ticket_message`、`quick_reply`、
`sys_config`、`app_version`、`notice`、`audit_log`、`login_log`、`risk_event`、`sys_worker`、`schema_meta`。

其中 `schema_meta` 记录结构版本，`sys_config` 存后台可改的业务配置（水印策略、公告等），
`sys_worker` 记录媒体处理进程心跳（进程存活告警依据）。


权限基线：

```bash
chown -R www:www /www/wwwroot/api.example.com /www/tmp /www/logs /www/backup
chmod 600 /www/wwwroot/api.example.com/.env
chmod 750 /www/tmp /www/logs /www/backup
chmod 755 /www/wwwroot/api.example.com/public/upgrade
```

---

## 3. 环境变量全表

配置来源与优先级（与 `src/config.js` 的实现严格一致）：

1. **已存在的进程环境变量优先**（Supervisor `environment`）；
2. 其次读取站点根目录的 `.env`（`src/config.js` 中的 `loadEnvFile()`，只填空缺项，不覆盖已有环境变量）；
3. 都没有则使用代码内置默认值，且**所有路径型配置会被解析成绝对路径**（相对路径基于代码根目录，而不是当前工作目录）。

因此推荐做法：**敏感值全部放 `.env`（600 权限），Supervisor 只写非敏感开关**。
下表与 `src/config.js` 一一对应，可直接用于评审。

`.` 表示代码内无默认值、必须显式配置。
「现行 `.env.example`」一列的含义：✅ 已在仓库 `.env.example` 中；
⚠️ 代码已支持但示例文件暂缺，需要人工补进 `.env`。

| 变量名 | 示例值 | 必填 | 代码默认值 | 现行 `.env.example` | 说明 |
| --- | --- | --- | --- | --- | --- |
| `NODE_ENV` | `production` | ✅ | `development` | ✅ | 生产模式；非 production 视为开发，禁止用于线上 |
| `BIND_HOST` | `127.0.0.1` | ✅ | `127.0.0.1` | ✅ | 只监听回环；填 `0.0.0.0` 等于把 Node 直接暴露公网，禁止 |
| `PORT` | `7101` | ✅ | `7101` | ✅ | 业务进程监听端口；必须与 `nginx.conf` 的 `upstream doutu_api` 一致 |
| `LOG_LEVEL` | `info` | ✅ | `info` | ✅ | `debug` / `info` / `warn` / `error`；生产用 `info` |
| `LOG_DIR` | `/www/logs` | ✅ | `./logs` | ✅ | 应用结构化日志目录；生产改为绝对路径 `/www/logs` |
| `DB_DIALECT` | `mysql` | ✅ | `sqlite` | ✅ | `mysql`（生产）/ `sqlite`（本地开发，零依赖 `node:sqlite`） |
| `SQLITE_FILE` | `./data/doutu.db` | ⭕ | `./data/doutu.db` | ✅ | 仅 `DB_DIALECT=sqlite` 时使用 |
| `MYSQL_HOST` | `127.0.0.1` | ✅ | `127.0.0.1` | ✅ | 仅本机连接，禁止公网可达 |
| `MYSQL_PORT` | `3306` | ✅ | `3306` | ✅ | MySQL 端口 |
| `MYSQL_USER` | `doutu` | ✅ | `root` | ✅ | 独立业务账号，**不要用 root**（默认值就是 root，务必覆盖） |
| `MYSQL_PASSWORD` | `********` | ✅ | 空 | ✅ | 32 位随机串：`openssl rand -base64 24` |
| `MYSQL_DATABASE` | `doutu` | ✅ | `doutu` | ✅ | 库名，字符集 utf8mb4 / utf8mb4_unicode_ci |
| `MYSQL_POOL` | `10` | ⭕ | `10` | ✅ | mysql2 连接池上限；并发 ≤50 时 10 足够，配合只读查询不要盲目调大 |
| `REDIS_URL` | `redis://127.0.0.1:6379/0` | ⭕ | 空（内存模式） | ✅ | **留空即进程内内存实现**（`src/core/kv.js` 的 `MemoryKv`）；API 与 worker 是两个进程，跨进程队列/共享状态必须配 Redis。形如 `redis://:password@127.0.0.1:6379/0` |
| `MASTER_KEY` | `openssl rand -hex 32` | ✅ | 空 | ✅ | **必须是 64 位十六进制字符（32 字节）**，用于派生会话密钥（`deriveSessionKey`），必须与客户端内嵌值对应；上线后绝不更换 |
| `SIGN_WINDOW_SEC` | `300` | ✅ | `300` | ✅ | 接口签名时间窗（秒）。调小更安全但对客户端时钟更敏感；调大容错但重放窗口变大 |
| `SESSION_TTL_HOURS` | `24` | ✅ | `24` | ✅ | 会话有效期（小时），对应 `license_session` 表 |
| `ADMIN_TOKEN_TTL_MIN` | `120` | ✅ | `120` | ✅ | 后台 token 有效期（分钟） |
| `ADMIN_IDLE_TIMEOUT_MIN` | `30` | ✅ | `30` | ✅ | 后台空闲自动登出（分钟） |
| `ACTIVATE_FAIL_LIMIT` | `20` | ✅ | `20` | ✅ | 同 IP 1 小时内激活失败 N 次即封禁（应用层风控，与 Nginx 限流互补） |
| `ACTIVATE_LOCK_MINUTES` | `60` | ✅ | `60` | ✅ | 触发上式后封禁时长（分钟） |
| `MIN_SUPPORTED_VERSION_CODE` | `0` | ⭕ | `0` | ✅ | 低于该版本号的旧客户端一律拒绝（0 = 不限制）；强更时改这里 |
| `ADMIN_PATH` | `/admin` | ✅ | 无（由路由注册决定） | ⚠️ | 后台挂载路径；默认 `/admin`，改为非常规路径可降低被扫描概率，**改动后必须同步 Nginx 的 `location ^~ /admin`** |
| `STORAGE_DRIVER` | `local` | ✅ | `local` | ✅ | `local`（本机磁盘 + 签名 URL）/ `oss`（私有桶 + 签名 URL） |
| `STORAGE_LOCAL_DIR` | `/www/wwwroot/api.example.com/storage/media` | ✅ | `./storage/media` | ✅ | `local` 模式落盘根目录；生产改为绝对路径并指向数据盘 |
| `STORAGE_PUBLIC_BASE` | `https://api.example.com` | ✅ | `http://127.0.0.1:7101` | ✅ | 对外可访问的下载前缀。⚠️ **只写到域名，不要带 `/dw`**：代码自己会拼 `/dw/file/{key}`（`src/modules/dw/storage.js` 的 `signedUrl`），写成 `https://api.example.com/dw` 会得到 `/dw/dw/file/...` 直接 404。默认值是回环地址，只能本机访问，生产必须改 |
| `URL_TTL_SEC` | `900` | ✅ | `900` | ✅ | 成品下载链接有效期（秒）；过期即失效，防止链接被转发盗刷 |
| `OSS_ENDPOINT` | `oss-cn-hangzhou.aliyuncs.com` | ⭕ | `oss-cn-hangzhou.aliyuncs.com` | ✅ | `STORAGE_DRIVER=oss` 时必填 |
| `OSS_BUCKET` | `doutu-prod` | ⭕ | 空 | ✅ | 同上 |
| `OSS_ACCESS_KEY_ID` | `LTAI********` | ⭕ | 空 | ✅ | 同上；建议用只授权该 bucket 的 RAM 子账号 |
| `OSS_ACCESS_KEY_SECRET` | `********` | ⭕ | 空 | ✅ | 同上 |
| `FFMPEG_PATH` | `/usr/bin/ffmpeg` | ✅ | `ffmpeg`（依赖 PATH） | ✅ | **生产必须写绝对路径**：Supervisor/cron 的 PATH 极简，写 `ffmpeg` 常找不到 |
| `FFPROBE_PATH` | `/usr/bin/ffprobe` | ✅ | `ffprobe`（依赖 PATH） | ✅ | 同上，用于读取源视频流信息；与 ffmpeg 同包安装 |
| `TMPDIR` | `/www/tmp` | ✅ | `./storage/tmp` | ✅ | ffmpeg 中间文件目录；**必须大容量分区**，由 `cleanup.js` 每小时清理 |
| `WORKER_CONCURRENCY` | `3` | ✅ | `3` | ✅ | worker 并发任务数；建议 ≈ CPU 核数 / 1.5，调大只会互相抢 CPU 反而更慢 |
| `TASK_TIMEOUT_SEC` | `120` | ✅ | `120` | ✅ | 单任务超时（秒）；超时判失败并清理临时文件 |
| `WORKER_POLL_MS` | `1000` | ⭕ | `1000` | ✅ | worker 拉取队列间隔（毫秒）；调大降低空转，调小降低延迟 |
| `WATERMARK_FONT` | `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc` | ⭕ | 自动探测 | ✅ | 中文水印字体。**留空时 `src/config.js` 会自动在候选路径中探测**（Linux 优先文泉驿/思源，Windows 用 `msyh.ttc`）；探测不到且无值 → 中文水印渲染成方块 |
| `WATERMARK_OPACITY` | `0.42` | ⭕ | `0.42` | ✅ | 水印不透明度 0~1 |
| `WATERMARK_WIDTH_RATIO` | `0.20` | ⭕ | `0.20` | ✅ | 水印宽度占视频宽度比例 |
| `WATERMARK_POSITION` | `bottom-right` | ⭕ | `bottom-right` | ✅ | 水印位置 |
| `MEDIA_PARSER_BASE` | `http://127.0.0.1:8051` | ✅ | 空 | ✅ | 自研解析引擎 media-parser（Flask）地址，仅内网 |
| `MEDIA_PARSER_KEY` | `********` | ⭕ | 空 | ✅ | 解析引擎调用密钥；引擎开启鉴权时必填 |
| `MEDIA_PARSER_TIMEOUT_MS` | `20000` | ⭕ | `20000` | ✅ | 单次解析请求超时（毫秒） |
| `ORIGIN_CACHE_HOURS` | `24` | ⭕ | `24` | ✅ | 原片缓存时长（小时），同 `item_id` 命中即复用，省解析与带宽 |
| `ROUTE_SUCC_ALERT` | `0.8` | ⭕ | `0.8` | ✅ | 线路成功率告警阈值（0.8 = 80%），与第 5 节监控表一致 |
| `PAY_PROVIDER` | `epay` | ✅ | `mock` | ✅ | `epay`（易支付/彩虹易支付）/ `mock`（仅联调，**生产禁止**） |
| `EPAY_GATEWAY` | `https://pay.example.com` | ⭕ | 空 | ✅ | `PAY_PROVIDER=epay` 时必填，支付网关地址 |
| `EPAY_PID` | `10001` | ⭕ | 空 | ✅ | 易支付商户 PID |
| `EPAY_KEY` | `********` | ⭕ | 空 | ✅ | 易支付商户密钥，用于回调验签；泄露等于可伪造到账 |
| `EPAY_NOTIFY_BASE` | `https://api.example.com` | ⭕ | 空 | ✅ | 回调地址前缀，必须是**公网可达的 HTTPS**，否则渠道回调失败 |
| `ALERT_WEBHOOK` | `https://oapi.dingtalk.com/robot/send?access_token=...` | ⭕ | 空（只写日志） | ✅ | 钉钉/企业微信机器人；**留空则只写日志、无任何主动告警**，强烈建议填 |

`.env` 生产模板（直接放在 `docs/DEPLOY.md` 中说明，不单独生成文件；
`# ⚠️` 标注的是现行 `.env.example` 缺失、需要人工补上的项）：

```dotenv
# ============================================================================
#  短视频去水印平台 · 生产 .env
#  位置：/www/wwwroot/api.example.com/.env     权限：chmod 600，属主 www
# ============================================================================

# ---------- 运行 ----------
NODE_ENV=production
BIND_HOST=127.0.0.1
PORT=7101
LOG_LEVEL=info
LOG_DIR=/www/logs

# ---------- 数据库（生产用 MySQL 8.0）----------
DB_DIALECT=mysql
# SQLITE_FILE=./data/doutu.db
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=doutu
MYSQL_PASSWORD=REPLACE_ME
MYSQL_DATABASE=doutu
MYSQL_POOL=10

# ---------- Redis（留空 = 进程内内存实现）----------
# API 与 worker 是两个进程：需要跨进程队列/共享状态时必须配 Redis
REDIS_URL=

# ---------- 安全 ----------
# 必须是 64 位十六进制（32 字节）：openssl rand -hex 32
# 生成：node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
MASTER_KEY=REPLACE_WITH_64_HEX_CHARS
SIGN_WINDOW_SEC=300
SESSION_TTL_HOURS=24
ADMIN_TOKEN_TTL_MIN=120
ADMIN_IDLE_TIMEOUT_MIN=30
ACTIVATE_FAIL_LIMIT=20
ACTIVATE_LOCK_MINUTES=60
MIN_SUPPORTED_VERSION_CODE=0
# ⚠️ 现行 .env.example 未包含，需补：后台挂载路径，改了要同步 Nginx
ADMIN_PATH=/admin

# ---------- 存储 ----------
STORAGE_DRIVER=local
STORAGE_LOCAL_DIR=/www/wwwroot/api.example.com/storage/media
# 必须与 Nginx 的 /dw/ 反代对应
STORAGE_PUBLIC_BASE=https://api.example.com
URL_TTL_SEC=900
OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
# OSS_BUCKET=
# OSS_ACCESS_KEY_ID=
# OSS_ACCESS_KEY_SECRET=

# ---------- 媒体处理 ----------
# 必须写绝对路径（Supervisor/cron 的 PATH 极简）
FFMPEG_PATH=/usr/bin/ffmpeg
FFPROBE_PATH=/usr/bin/ffprobe
# 必须在大容量分区，由 cleanup.js 每小时清理
TMPDIR=/www/tmp
WORKER_CONCURRENCY=3
TASK_TIMEOUT_SEC=120
WORKER_POLL_MS=1000
# 留空则自动探测；显式指定更可控
WATERMARK_FONT=/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc
WATERMARK_OPACITY=0.42
WATERMARK_WIDTH_RATIO=0.20
WATERMARK_POSITION=bottom-right

# ---------- 解析引擎（内网）----------
MEDIA_PARSER_BASE=http://127.0.0.1:8051
MEDIA_PARSER_KEY=
MEDIA_PARSER_TIMEOUT_MS=20000
ORIGIN_CACHE_HOURS=24
ROUTE_SUCC_ALERT=0.8

# ---------- 支付 ----------
PAY_PROVIDER=epay
EPAY_GATEWAY=https://pay.example.com
EPAY_PID=REPLACE_ME
EPAY_KEY=REPLACE_ME
EPAY_NOTIFY_BASE=https://api.example.com

# ---------- 告警 ----------
ALERT_WEBHOOK=
```

设置与自检：

```bash
cd /www/wwwroot/api.example.com
cp .env.example .env                   # 注意：example 默认 DB_DIALECT=sqlite / PAY_PROVIDER=mock，必须按上表改
vi .env                                # 按上面的生产模板填入真实值，并补上标 ⚠️ 的项
chmod 600 .env && chown www:www .env

# 校验：确认关键项不是默认值/占位符
grep -nE '^(DB_DIALECT|NODE_ENV|MYSQL_USER|MYSQL_PASSWORD|MASTER_KEY|ADMIN_PATH|STORAGE_PUBLIC_BASE|PAY_PROVIDER)=' .env

# 校验 MASTER_KEY 是 64 位十六进制
awk -F= '/^MASTER_KEY=/{ if ($2 ~ /^[0-9a-f]{64}$/) print "MASTER_KEY OK"; else print "MASTER_KEY 不合法（需 64 位十六进制）" }' .env

# 生效：重启进程（.env 由应用启动时加载，改完必须重启）
supervisorctl restart doutu-api doutu-worker
```

---

## 4. 上线检查清单

### 4.1 部署前（开发侧）

- [ ] `src/index.js` / `src/worker.js` 可正常启动（`node src/index.js` 后 `curl /healthz` 返回 `code:0`）
- [ ] `npm run check-imports` 全部通过（52 个模块，防止相对路径写错）
- [ ] `npm run smoke` 通过（授权链路 26 项）
- [ ] `npm run e2e` 通过（端到端 90 项；需要 ffmpeg 与中文字体）
- [ ] `scripts/create-admin.js` 可用：`npm run admin:create -- --username=admin --password=...`
- [ ] `package-lock.json` 已提交（`npm ci` 依赖它）
- [ ] 选择数据库方言：生产用 MySQL 时必须确认 `mysql2` 已安装（见 2.1 节最后一行）
- [ ] `package.json` 含 `"type": "module"`，入口与 `deploy/supervisor.conf`、`deploy/crontab.txt` 一致
- [ ] npm script 可用：`db:init`、`admin:create`（`deploy.sh` 依赖这两个）
- [ ] `.env` 已按第 3 节填齐（至少 `MASTER_KEY` 必须换成 64 位十六进制随机串，`ADMIN_PATH` 按需修改）
- [ ] 代码中无任何硬编码密钥、测试账号；`PAY_PROVIDER` 已从 `mock` 改为 `epay`
- [ ] 数据库迁移向前兼容（先加列、后改代码、最后清理旧列）
- [ ] `npm run db:sql` 生成的两份 SQL 与 `src/db/schema.js` 一致（避免结构漂移）

### 4.2 部署中（服务器侧）

- [ ] `timedatectl show -p NTPSynchronized` = `yes`，时区 `Asia/Shanghai`
- [ ] 云安全组只放行 22/80/443；`7101/8051/3306/6379` 仅回环
- [ ] 宝塔面板改端口 + 开面板 SSL + IP 白名单
- [ ] MySQL 8.0 已建库 `doutu`（utf8mb4），业务账号非 root
- [ ] Node.js ≥ 20，`supervisor.conf` 与 `crontab.txt` 中 node 路径与实际一致
- [ ] `ffmpeg`、`ffprobe`、`fonts-wqy-zenhei` 已安装并验证
- [ ] `.env` 权限 600、属主 www，`DB_DIALECT=mysql`、`PAY_PROVIDER=epay`
- [ ] `.env` 无 `REPLACE_` / `change_me` / `please_replace` 残留，`MASTER_KEY` 为 64 位十六进制
- [ ] 生产必需的绝对路径已设置：`LOG_DIR` / `STORAGE_LOCAL_DIR` / `TMPDIR` / `FFMPEG_PATH` / `FFPROBE_PATH`
- [ ] `npm ci --omit=dev` 成功，且确认 `mysql2` 已装入（生产 MySQL 必需）
- [ ] `npm run db:init` 成功（退出码 0），表数量为 27
- [ ] `npm run admin:create` 成功，管理员可登录
- [ ] `supervisorctl status` 两个进程均 `RUNNING`
- [ ] `/etc/cron.d/doutu` 属主 root、权限 644、任务条数与 `crontab.txt` 一致
- [ ] Nginx 限流 zone 已在 `http {}` 定义一次（无 duplicate zone 报错）
- [ ] `nginx -t` 通过并已 reload
- [ ] 证书已部署且自动续签正常

### 4.3 部署后（验收）

- [ ] `curl -fsS https://api.example.com/healthz` 返回 200
- [ ] `curl -I http://api.example.com/healthz` 返回 301
- [ ] `/.env`、`/.git/config`、`/x.sql`、`/x.zip` 全部 404
- [ ] 激活接口连续请求出现 429（严格限流生效）
- [ ] `/upgrade/` 目录不可列举（404），具体升级包可下载
- [ ] `/dw/` 大文件下载不中断（下载一个 >100MB 成品验证 `proxy_read_timeout`）
- [ ] 后台 `ADMIN_PATH` 可登录，静态资源正常
- [ ] 提交一条真实任务，worker 出片成功（看 `doutu-worker` 日志）
- [ ] `mock`/真实支付回调能正确改单（`EPAY_NOTIFY_BASE` 公网可达）
- [ ] 手工执行 `reconcile.js`、`backup.js`，退出码 0
- [ ] `ALERT_WEBHOOK` 能收到一条测试消息

---

## 5. 监控与告警阈值

所有阈值都通过 `ALERT_WEBHOOK`（钉钉/企业微信机器人）推送。
**未配置 webhook = 没有监控**，上线前必须配置。

| 监控项 | 阈值 | 持续/触发条件 | 级别 | 处置建议 |
| --- | --- | --- | --- | --- |
| CPU 使用率 | **> 85%** | 持续 5 分钟 | P2 告警 | 查看是否 worker 并发过高（`WORKER_CONCURRENCY`）或 ffmpeg 卡死 |
| 内存使用率 | **> 85%** | 持续 5 分钟 | P2 告警 | 检查 ffmpeg 泄漏、MySQL buffer pool 过大；准备重启 worker |
| 磁盘使用率 | **> 85%** | 持续 5 分钟 | **P1 告警** | 立即清 `/www/tmp`、成品与日志；磁盘满会导致 MySQL 拒写 |
| Nginx 5xx 比例 | **> 1%** | 5 分钟窗口 | **P1 告警** | 先查 Node 进程是否存活（502 多为进程挂） |
| 解析成功率 | **< 80%** | 5 分钟窗口 | **P1 告警** | 查 media-parser（8051）与上游平台接口变更 |
| 任务队列积压 | **> 50** | 连续 2 次采样 | P1 告警 | worker 挂了或并发不足；扩容/重启 worker |
| 支付回调失败 | 任意 1 次 | **立即** | **P0 告警** | 回调失败 = 用户付钱不到账，立即人工核对渠道后台 |
| 证书剩余有效期 | **< 15 天** | 每日检查 | P1 告警 | 手动续签并排查自动续签为何失败 |
| 时钟偏移 | > 60 秒 | 每 5 分钟 | P1 告警 | `timedatectl set-ntp true`；偏移 >5 分钟会全站签名失败 |
| MySQL 连接数 | > 80% `max_connections` | 持续 5 分钟 | P2 告警 | 查慢查询与连接泄漏 |
| 进程存活 | `doutu-api` / `doutu-worker` 非 RUNNING | 每次采样 | **P0 告警** | `supervisorctl status` + 看 stderr 日志 |
| 健康检查 | `/healthz` 非 200 | 连续 3 次 | **P0 告警** | 从进程 → 端口 → `.env` → 数据库逐层排查 |
| 接口 P99 延迟 | > 3 秒 | 5 分钟窗口 | P2 告警 | 查慢 SQL、解析引擎超时 |
| 备份任务 | 当日无成功记录 | 每日 04:00 检查 | P1 告警 | 备份失败等于没有兜底，当天必须补备 |

采样与实现建议：

```bash
# 最小可用的存活探测（可放进宝塔「计划任务」每 5 分钟执行）
curl -fsS --max-time 5 http://127.0.0.1:7101/healthz >/dev/null \
  || curl -sS -X POST "$ALERT_WEBHOOK" -H 'Content-Type: application/json' \
       -d '{"msgtype":"text","text":{"content":"[P0] doutu-api 健康检查失败"}}'
```

> 阈值基于首期容量（≤500 用户、≤50 并发）设定；
> 用户量增长后需重新评估 CPU/内存阈值与 `WORKER_CONCURRENCY`。

---

## 6. 备份策略

### 6.1 备份对象与频率

| 对象 | 频率 | 保留 | 方式 |
| --- | --- | --- | --- |
| MySQL 全量（逻辑备份） | 每日 03:00 | 本地 **7 天** | `mysqldump --single-transaction` → gzip |
| MySQL binlog | 持续 | 7 天 | 开启 binlog，用于**定点恢复**（PITR） |
| 全量备份转存 OSS | 每日 03:30 | 远端 **30 天** | 加密后上传（异地容灾） |
| `.env` 与 Nginx/证书 | 变更时 | 长期 | 单独加密保存到密码管理器，**不要**放 OSS 明文 |
| 代码 | 每次提交 | 永久 | git 远端仓库 |

### 6.2 全量备份命令（等价实现，`backup.js` 内部即此逻辑）

```bash
set -euo pipefail
STAMP="$(date +%F)"
DIR=/www/backup/db
mkdir -p "$DIR"

mysqldump --single-transaction --quick --routines --events \
  --default-character-set=utf8mb4 \
  -h 127.0.0.1 -P 3306 -u doutu -p"$MYSQL_PASSWORD" doutu \
  | gzip -9 > "$DIR/doutu-$STAMP.sql.gz"

# 校验产物非空（避免「备份成功但文件 0 字节」这种最危险的假成功）
[ -s "$DIR/doutu-$STAMP.sql.gz" ] || { echo "备份文件为空，失败"; exit 1; }

# 本地保留 7 天
find "$DIR" -name 'doutu-*.sql.gz*' -mtime +7 -delete
```

binlog 定点恢复所需的最小配置（`/etc/my.cnf` 或宝塔 MySQL 配置）：

```ini
[mysqld]
log-bin=mysql-bin
binlog_format=ROW
expire_logs_days=7
server-id=1
```

### 6.3 加密与转存 OSS

备份文件**离开本机前必须加密**（含用户与订单数据）：

```bash
# 一次性生成口令文件（不要进版本库）
openssl rand -base64 32 > /www/backup/.backup_pass
chmod 600 /www/backup/.backup_pass

# 加密（AES-256-CBC + PBKDF2 派生，salt 随机）
STAMP="$(date +%F)"
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
  -in  /www/backup/db/doutu-$STAMP.sql.gz \
  -out /www/backup/db/doutu-$STAMP.sql.gz.enc \
  -pass file:/www/backup/.backup_pass

# 上传（30 天后由 OSS 生命周期规则清理）
# 方式 A：应用内实现（推荐，带加密 + 保留策略 + 校验，见 src/cron/backup.js）
#   node src/cron/backup.js --retain-days=7                        # 备份 + 本地保留 7 天
#   node src/cron/backup.js --upload-oss --encrypt --retain-remote-days=30
#   node src/cron/backup.js --verify                               # 恢复演练：解密 + 解压 + 内容校验
#   口令来源：BACKUP_PASSWORD 或 BACKUP_PASS_FILE（文件须 chmod 600 且不进版本库）

# 方式 B：ossutil（独立于应用进程，应用挂了备份照样能走）
ossutil cp /www/backup/db/doutu-$STAMP.sql.gz.enc \
  oss://doutu-backup/db/doutu-$STAMP.sql.gz.enc
```

> `backup.js` 已实现，`deploy/crontab.txt` 里的两条备份任务可直接使用。
> **每月请至少跑一次 `node src/cron/backup.js --verify` 做恢复演练** ——
> 没有验证过的备份等于没有备份（规划书 10.1）。

> `openssl enc` 请务必带 `-pbkdf2`：不加的话默认 KDF 极弱，且新版 OpenSSL 会直接拒绝。
> 口令文件 `/www/backup/.backup_pass` 必须 `chmod 600`、属主 www，
> 且**必须另存一份到密码管理器**——口令丢了，备份等于没有。

### 6.4 恢复流程

```bash
# 1) 解密
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in doutu-2026-01-01.sql.gz.enc -out doutu-2026-01-01.sql.gz \
  -pass file:/www/backup/.backup_pass

# 2) 解压
gunzip -c doutu-2026-01-01.sql.gz > doutu-2026-01-01.sql

# 3) 先恢复到临时库验证，再决定是否覆盖生产（绝不直接覆盖生产库）
mysql -h 127.0.0.1 -u root -p -e "CREATE DATABASE doutu_restore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h 127.0.0.1 -u root -p doutu_restore < doutu-2026-01-01.sql
mysql -h 127.0.0.1 -u root -p -e "SELECT COUNT(*) FROM doutu_restore.app_user;"   # 抽查数据

# 4) 定点恢复（全量 + binlog 到故障前一刻）
mysqlbinlog --start-datetime="2026-01-01 03:00:00" --stop-datetime="2026-01-01 14:30:00" \
  /www/server/data/mysql-bin.000123 | mysql -h 127.0.0.1 -u root -p doutu_restore
```

### 6.5 每月恢复演练（必须做）

每月固定一天（建议每月 1 日）执行并记录：

- [ ] 从 OSS 拉取最近一次加密备份到**非生产机器**
- [ ] 解密 + 解压成功
- [ ] 恢复到临时库成功，抽查行数（用户表 / 订单表 / 任务表）
- [ ] 用恢复库启动一次只读实例，后台能查询历史订单
- [ ] 记录「备份可恢复性」结论与耗时，归档到运维记录
- [ ] 演练后删除临时库与临时文件

> 没演练过的备份不算备份。常见失效原因：口令文件丢失、`mysqldump` 中途报错但管道掩盖了退出码
> （因此备份脚本必须显式检查产物非空与退出码，见 6.2）。

---

## 7. 故障预案

| 场景 | 现象 | 立即处置 | 恢复与根治 |
| --- | --- | --- | --- |
| **域名被封 / 被墙** | 国内访问超时、ping 通但 HTTPS 握手失败 | ① 第一时间切备用域名：DNS 改解析 + 申请新证书；② 后台/接口域名与下载域名分离，先保接口可用 | 准备 2 个以上备用域名与一次性证书申请脚本；客户端配置域名列表 + 灰度切换能力（`/upgrade` 通道与域名解耦） |
| **证书 pin 失效** | 客户端 App 报 SSL 校验失败，全部请求不通 | ① 若 App 做了证书 pinning，必须**先确认 pin 的 CA/公钥**；② 换证书时保证同一公钥（沿用同一私钥）即可继续通过 pin | 不用证书 pin 而是 pin 公钥或 CA；证书续签沿用同一密钥对；准备「pin 失效应急开关」（App 端可远程降级为系统信任链），并把证书变更纳入发布流程提前通知 |
| **数据库故障** | API 全量 5xx，日志出现 `ECONNREFUSED`/`Too many connections`/`Table is marked as crashed` | ① `systemctl status mysql`；② 看 `/www/server/data/*.err` 最后 100 行；③ 磁盘满先清 `/www/tmp` 与 binlog；④ 表损坏用 `REPAIR TABLE` | 用最近全量 + binlog 恢复到临时库，核对后切换；根因：磁盘满/OOM/慢查询长事务，需加监控与索引优化 |
| **被 DDoS / CC** | 带宽打满、连接数暴涨、`limit_req` 大量 429 | ① Nginx 层已有限流（普通 20r/s、激活 1r/s、单 IP 连接 20）；② 开启宝塔/云厂商的防火墙与 CC 防护；③ 封禁攻击 IP 段 | 上云 WAF/CDN 隐藏源站 IP；`/api/v1/auth/activate` 保持独立严限流；关键接口加验证码 |
| **被大规模破解** | 激活接口/登录接口大量失败、出现异常订单 | ① 提高限流强度与封禁 IP；② 强制下线所有会话（重置 `SESSION_TTL_HOURS` 对应存储并清 session）；③ 检查是否有账号余额异常变动 | 轮换 `MASTER_KEY` 关联的下游凭据、`EPAY_KEY`；后台强制强密码 + 二次验证；审计日志留全量 IP |
| **0day / 依赖漏洞** | 收到漏洞通告或异常 RCE 迹象 | ① 立即隔离：Nginx 拦掉受影响入口（返回 403/404）；② 备份现场（日志、内存快照）用于溯源；③ 停 worker 防横向 | **热修复优先于优雅修复**：先打补丁或临时下线该功能，再按 `npm audit` 升级依赖；`npm ci` 保证可复现；定期跑依赖审计并关注 Node/MySQL/Nginx 安全公告 |
| **MySQL 磁盘满** | MySQL 拒写、`No space left on device` | ① 清 `/www/tmp` 与 `/www/logs`；② 清 `storage` 中过期成品；③ 缩短 binlog 保留 | 把 `TMPDIR` 与数据文件分区隔离；`cleanup.js` 每小时执行并告警；磁盘 >85% 立刻报警 |
| **worker 挂了** | 任务一直「处理中」、队列积压 >50 | `supervisorctl restart doutu-worker`；看 stderr 是否 ffmpeg 报错 | 修正 `FFMPEG_PATH`/字体/并发；`autorestart=true` 已兜底，但反复重启必须查根因 |

---

## 8. 常见故障排查

### 8.1 全站「签名错误 / 401」——先查时间同步

**这是本项目最高频的故障**，且症状极具误导性（像代码 bug，其实只是时钟漂移）。

```bash
timedatectl                       # 看 System clock synchronized 是否为 yes
timedatectl show -p NTPSynchronized -p TimeUSec
date -R                           # 与手机/本机时间对比
systemctl status systemd-timesyncd
journalctl -u systemd-timesyncd -n 30 --no-pager
```

处置：

```bash
timedatectl set-timezone Asia/Shanghai
timedatectl set-ntp true
systemctl restart systemd-timesyncd
# 装了 chrony 的话（二选一，不要同时启用两个 NTP 服务）
chronyc makestep && chronyc sources -v
```

> 若个别用户报签名失败而其他人正常 → 是**客户端**时间不准，不是服务器；
> 让用户开启「自动设置时间」。若全部用户同时失败 → 服务器时钟或 `SIGN_WINDOW_SEC` 配置问题。

### 8.2 HTTP 502 / 504 —— 查进程存活

```bash
# 1) 进程是否活着
supervisorctl status
systemctl status supervisor

# 2) 端口是否在听（必须 127.0.0.1:7101）
ss -lntp | grep 7101

# 3) 本机直连是否通（绕过 Nginx，区分是 Node 问题还是 Nginx 问题）
curl -v --max-time 5 http://127.0.0.1:7101/healthz

# 4) 看真实错误栈
supervisorctl tail -200 doutu-api stderr
tail -100 /www/logs/doutu-api.err.log
```

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| `curl` 本机也 502/连不上 | Node 进程没起来／启动即崩 | 看 stderr：多为 `.env` 缺失、MySQL 连不上、`MASTER_KEY` 不合法、Node 版本 < 20 |
| 本机 `curl` 200，域名 502 | Nginx 配置或 upstream 指向错 | `nginx -t`；确认 `upstream doutu_api` 是 `127.0.0.1:7101`；`systemctl reload nginx` |
| 504 且是下载/长任务 | 超时太短 | `/dw/` 已放宽到 `proxy_read_timeout 600s`；确认改的是 `/dw/` 的 location |
| 间歇性 502 | 后端被 OOM Kill | `dmesg -T \| grep -i -E 'oom\|killed'`；降低 `WORKER_CONCURRENCY` 或加内存 |

### 8.3 FFmpeg 报错 / 水印显示方块

```bash
# FFMPEG_PATH 是否可用（必须绝对路径）
echo "$FFMPEG_PATH"; /usr/bin/ffmpeg -version | head -1
ffmpeg -hide_banner -filters | grep -E 'drawtext|subtitles'   # 确认编译了 drawtext（需要 libfreetype）

# 水印字体是否存在（WATERMARK_FONT）
ls -l /usr/share/fonts/truetype/wqy/wqy-zenhei.ttc
fc-list :lang=zh | head
```

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| 水印位置正常但文字是 **□□□ 方块** | `WATERMARK_FONT` 指向的字体不含中文，或字体文件不存在 | `apt-get install -y fonts-wqy-zenhei`；把 `WATERMARK_FONT` 指到 `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc`；重启 `doutu-worker` |
| `drawtext` 滤镜不存在 | 系统 ffmpeg 未编译 freetype | 换成带 freetype 的构建（Ubuntu 官方包默认带）；`ffmpeg -filters` 自查 |
| `Fontconfig error: cannot load default config file` | worker 的 `HOME`/环境过简 | Supervisor 已显式给 PATH；再补 `HOME=/www/wwwroot/api.example.com` |
| 任务一直超时 | `TASK_TIMEOUT_SEC=120` 对长视频偏小，或 CPU 不足 | 适度调大超时；降低 `WORKER_CONCURRENCY`；改硬件 |
| 磁盘突然满 | 中间文件未清 | `/www/tmp` 是否指向数据盘；`cleanup.js` 是否在 cron 里；`df -h` |
| 输出视频无声音/花屏 | 源流分离、编码参数不兼容 | 保留中间文件复现：`TMPDIR` 下按任务 ID 定位；用 `ffprobe` 查源流 |

### 8.4 磁盘满导致 MySQL 拒写

症状：接口大量 500，日志出现 `No space left on device` / `Disk full` / MySQL 只读。

```bash
# 1) 定位谁在吃空间
df -h
du -sh /www/* 2>/dev/null | sort -h | tail -10
du -sh /www/wwwroot/api.example.com/storage/* 2>/dev/null | sort -h | tail -10
du -sh /www/tmp /www/logs /www/backup 2>/dev/null
find /www/server/data -name 'mysql-bin.*' -printf '%s %p\n' 2>/dev/null | sort -n | tail -5

# 2) 先救急（按安全顺序：临时文件 → 旧日志 → 过期成品 → 旧备份 → binlog）
find /www/tmp -type f -mmin +120 -delete
find /www/logs -name '*.log.*' -mtime +7 -delete
# 过期成品由 cleanup.js 清理，必要时手工按 mtime 删
find /www/backup/db -name 'doutu-*.sql.gz*' -mtime +7 -delete
# binlog 清理（务必确认已有可用全量备份！）
mysql -h 127.0.0.1 -u root -p -e "PURGE BINARY LOGS BEFORE DATE_SUB(NOW(), INTERVAL 3 DAY);"

# 3) 确认 MySQL 恢复可写
mysql -h 127.0.0.1 -u root -p -e "SELECT 1;" && supervisorctl restart doutu-api
```

**根因治理（必做，否则必然复发）**：
`TMPDIR=/www/tmp` 与 MySQL 数据目录分属不同分区；`cleanup.js` 每小时执行；
磁盘 >85% 持续 5 分钟告警；成品保留策略与 `URL_TTL_SEC` 一致（过期即删）。

### 8.5 支付回调失败

```bash
# 回调地址必须公网可达且为 HTTPS
curl -sSI https://api.example.com/api/v1/pay/notify | head -5
grep -E '^(PAY_PROVIDER|EPAY_PID|EPAY_KEY|EPAY_NOTIFY_BASE)' .env
supervisorctl tail -200 doutu-api stderr | grep -i -E 'notify|epay|sign'
```

| 现象 | 原因 | 处置 |
| --- | --- | --- |
| 渠道后台显示「回调失败」 | `EPAY_NOTIFY_BASE` 不是公网 HTTPS，或被 WAF/限流拦截 | 修正为 `https://api.example.com`；确认回调路径不被 Nginx 的严格限流误伤 |
| 验签不通过 | `EPAY_KEY` 错误，或参数拼接顺序/编码与渠道不一致 | 对照渠道文档核对验签规则；注意中文参数需按渠道要求编码 |
| 用户已付款但订单未开通 | 回调成功但业务处理报错（幂等/事务问题） | 查订单流水与 `reconcile.js` 对账结果；立即人工补单 |
| 重复到账 | 回调重复投递未做幂等 | 回调处理必须按渠道流水号做唯一约束 |

> 支付回调失败属 **P0**：用户付钱不到账，必须立即告警 + 人工介入，不能等对账。

### 8.6 解析成功率下降

```bash
curl -sS --max-time 5 http://127.0.0.1:8051/healthz   # media-parser 存活
supervisorctl status                                  # 解析引擎若也用 supervisor 守护
tail -100 /www/logs/cron-route-health.log             # 线路健康统计输出
```

常见原因：上游平台接口改版（最常见）、出口 IP 被限流、解析引擎进程挂掉、
`MEDIA_PARSER_BASE` 配错。处置：切换备用线路、升级解析规则、重启解析进程。

### 8.7 429 过多（限流误伤正常用户）

```bash
grep -c ' 429 ' /www/logs/nginx/api.example.com.access.log
awk '{print $1}' /www/logs/nginx/api.example.com.access.log | sort | uniq -c | sort -rn | head
```

- 若集中在少数 IP → 是攻击，保持限流并封禁；
- 若集中在企业出口/NAT 大 IP → 该 IP 后多人共享，会被 20r/s 与单 IP 连接 20 误伤，
  此时应放宽 `api_general` 的 rate/burst 或按账号维度限流（应用层实现）。

### 8.8 后台打不开

```bash
grep '^ADMIN_PATH=' .env
curl -sSI https://api.example.com/admin | head -5
```

确认 `ADMIN_PATH` 与 Nginx 的 `location ^~ /admin` 一致；
改了 `ADMIN_PATH` 却忘了改 Nginx 是最常见原因。

---

## 9. 日常运维动作

| 频率 | 动作 | 命令 / 说明 |
| --- | --- | --- |
| 每日 | 看告警与进程状态 | `supervisorctl status`；检查 webhook 是否有 P0/P1 消息 |
| 每日 | 确认定时任务与备份成功 | `tail -50 /www/logs/cron-*.log`；确认当日备份文件存在且非 0 字节 |
| 每日 | 磁盘水位 | `df -h`；>85% 立即清理 |
| 每周 | 检查解析成功率与队列积压 | 后台线路健康页 + `route-health.js` 输出 |
| 每周 | 证书剩余天数 | `echo \| openssl s_client -connect api.example.com:443 -servername api.example.com 2>/dev/null \| openssl x509 -noout -dates` |
| 每月 | **恢复演练** | 见 [6.5](#65-每月恢复演练必须做) |
| 每月 | 依赖安全审计 | `npm audit --omit=dev`；关注 Node/MySQL/Nginx 安全公告 |
| 每季 | 密钥与凭据轮换评估 | `EPAY_KEY`、OSS RAM 子账号；`MASTER_KEY` **不可轮换**（会废掉历史密文） |
| 每季 | 容量评估 | 用户数/并发/存储增长趋势，决定是否升级配置或引入 Redis、对象存储 |
| 变更时 | 发版 | `bash deploy/deploy.sh`；只改后端逻辑时 `supervisorctl restart doutu-api` 即可，无需重启 worker |

发版标准动作：

```bash
cd /www/wwwroot/api.example.com
git fetch --all && git reset --hard origin/main
runuser -u www -- npm ci --omit=dev
runuser -u www -- npm run db:init          # 迁移必须幂等且向前兼容
supervisorctl restart doutu-api            # 有媒体逻辑变更时再 restart doutu-worker
sleep 2 && curl -fsS http://127.0.0.1:7101/healthz && echo "OK"
```

> 迁移与发版顺序：**先迁移（兼容旧代码）→ 再发新代码 → 最后清理旧字段**，
> 这样任何一步失败都能安全回滚到上一个 commit。

---

## 附：约定（文档与 `deploy/` 脚本依赖的入口）

下表全部入口均已实现，可直接使用（✅ = 仓库中已存在且可执行）。

| 名称 | 现状 | 用途 |
| --- | --- | --- |
| `node src/index.js` | ✅ | 业务 API + 后台 + 下载，监听 `127.0.0.1:7101`（`npm start`） |
| `node src/worker.js` | ✅ | 媒体处理常驻进程（`npm run worker`） |
| `node src/cron/expire-check.js` | ✅ | 到期扫描 + 关闭超时订单 + 清理会话（`npm run cron:expire`） |
| `node src/cron/revenue-daily.js` | ✅ | 收益日聚合，默认补算 3 天（`npm run cron:revenue`） |
| `node src/cron/reconcile.js` | ✅ | 支付对账（`npm run cron:reconcile`） |
| `node src/cron/route-health.js` | ✅ | 解析线路成功率统计与告警（`npm run cron:route-health`） |
| `node src/cron/cleanup.js` | ✅ | 临时文件与旧成品清理、磁盘水位告警（`npm run cron:cleanup`） |
| `node src/cron/system-health.js` | ✅ | CPU/内存/磁盘 + worker 进程存活检查（告警） |
| `node src/cron/backup.js` | ✅ | 备份 CLI：`--retain-days` / `--upload-oss` / `--encrypt` / `--retain-remote-days` / `--verify` |
| `npm run db:init` | ✅ | 建表 + 种子数据；支持 `--drop`（先删表）与 `--no-seed`（只建表），幂等 |
| `npm run db:sql` | ✅ | 由 `src/db/schema.js` 重新生成 `sql/schema.*.sql` |
| `npm run admin:create` | ✅ | 创建/重置后台管理员（`--username` / `--password` / `--role` / `--reset`） |
| `npm run smoke` / `e2e` / `check-imports` | ✅ | 授权链路自检 / 端到端验收 / 模块导入检查 |
| `GET /healthz` | ✅ | 健康检查（无需签名），返回 `{code:0,data:{db:"up",...}}`，供 Nginx 与监控探测 |

> 上表入口命名与端口（`node src/index.js`、`node src/worker.js`、`node src/cron/*.js`、
> `127.0.0.1:7101`）是 `deploy/` 下四个产物与本手册共同依赖的契约。
> 若确需改名，请同步更新：`deploy/supervisor.conf`、`deploy/crontab.txt`、`deploy/deploy.sh`、本文档。
>
> 文档维护约定：接口有变更时同步更新 `docs/API.md`，表结构有变更时先改 `src/db/schema.js`
> 再执行 `npm run db:sql`，并保持本手册第 2.2 节的表清单一致。
