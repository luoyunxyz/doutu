# 短视频去水印平台 · 服务端

按《服务端建设规划书》实现的 Node.js 服务端：**业务 API + 管理后台 + 媒体合成 worker**，一套代码、两种数据库、可在一台 4 核 8G 机器上跑完首期容量（注册用户 ≤500、峰值并发 ≤50）。

> **会员模型（2026-10 变更）**：卡密体系已下线，改为 **邀请码注册 + 后台开通会员**。
> 一个邀请码只能创建一个账号；客户端凭「邀请码 + 手机号 + 两次密码」自助注册。
> **会员只决定有没有水印，不决定能不能用**：未开通会员也能正常使用，只是界面与成品带水印；
> 后台开通后水印消失。客户端**不做登录门禁**，登录/注册入口在「我」页顶部的头像与名字上。
> 详见 [5.7 会员模型](#57-会员模型邀请码注册--后台开通--水印只跟会员走)。

> 本文档是服务端的总入口。接口细节见 [docs/API.md](docs/API.md)，
> **上线按 [docs/DEPLOY-BT.md](docs/DEPLOY-BT.md)（宝塔面板 + 域名 + HTTPS 分步操作）走**，
> 原理与设计见 [docs/DEPLOY.md](docs/DEPLOY.md) 与 [deploy/README.md](deploy/README.md)。

> **客户端只连域名，不存 IP**：接口地址与签名密钥都是**编译期注入**的（`doutu/app/build.gradle`）。
> 正式包必须显式传 `-PdoutuApi=https://你的域名 -PmasterKey=<服务端 MASTER_KEY>`，
> 少一个直接编译失败；`http://` 地址也会被拒绝；正式包的网络安全策略禁止一切明文 HTTP。
> 换服务器/加 CDN/换证书只要重新打包，不用改一行代码。

---

## 1. 一分钟跑起来（本地，零外部依赖）

```bash
cd server
cp .env.example .env          # Windows: copy .env.example .env
# 把 .env 里的 DB_DIALECT 设为 sqlite（默认就是），并按需改 MASTER_KEY
npm run db:init               # 建表 + 写入初始数据（角色/配置/商品/线路）
npm run admin:create -- --username=admin --password=你的密码   # 创建后台管理员
npm run smoke                 # 授权链路自检（26 项）
npm run e2e                   # 端到端验收：激活→解析→水印合成→下载→后台统计（90 项）
npm start                     # 启动 API（默认 127.0.0.1:7101）
npm run worker                # 另开一个终端：启动媒体处理进程
```

后台入口：`http://127.0.0.1:7101/admin`（可用 `ADMIN_PATH` 改成不可猜的路径）。
健康检查：`curl http://127.0.0.1:7101/healthz`。

**本机需要 Node ≥ 20.11 与系统 ffmpeg**（媒体合成用）。SQLite 模式由 Node 内置的 `node:sqlite` 提供，不需要安装任何数据库。

## 2. 生产部署（Ubuntu 22.04 + 宝塔）

```bash
export DB_DIALECT=mysql && export MYSQL_PASSWORD=... && export MASTER_KEY=$(openssl rand -hex 32)
bash deploy/deploy.sh          # 幂等：建目录 → 装依赖 → 初始化库 → 建管理员 → Supervisor → Nginx
```

部署产物（已随代码提供）：

| 文件 | 用途 |
|---|---|
| [deploy/deploy.sh](deploy/deploy.sh) | 一键部署/更新脚本（含时间同步、权限、幂等检查） |
| [deploy/supervisor.conf](deploy/supervisor.conf) | `doutu-api` 与 `doutu-worker` 两个常驻进程 |
| [deploy/nginx.conf](deploy/nginx.conf) | TLS、HSTS、限流、路由分流、敏感文件拦截 |
| [deploy/crontab.txt](deploy/crontab.txt) | 8 个定时任务（到期扫描/收益聚合/对账/线路健康/清理/健康检查/备份/校时） |
| [sql/schema.mysql.sql](sql/schema.mysql.sql) | MySQL 8.0 建表脚本（由 `npm run db:sql` 生成，可直接给 DBA 审） |

---

## 3. 目录结构

```
server/
├── src/
│   ├── index.js                # API 进程入口
│   ├── worker.js               # 媒体处理进程入口（FFmpeg 合成在这里跑）
│   ├── app.js                  # 路由装配
│   ├── config.js               # 环境变量 → 强类型配置
│   ├── core/                   # http 框架 / 统一响应 / 错误码 / KV / 加密 / 金额 / 工具
│   ├── db/                     # 双方言数据库层 + 表定义 + 种子数据 + DDL 生成器
│   ├── middleware/             # 9 步校验链、三级限流
│   ├── services/               # 用户 / 会话 / 卡密 / 订单 / 收益 / 配额 / 配置 / 审计 / 告警
│   ├── modules/
│   │   ├── auth/               # 激活、登录、握手、心跳、解绑
│   │   ├── dw/                 # 去水印：适配器、线路降级、任务编排、FFmpeg 水印、存储
│   │   ├── pay/                # 易支付对接、回调验签与幂等、对账
│   │   ├── config/ upgrade/ ticket/
│   │   └── admin/              # 后台鉴权 + RBAC + 六个管理模块
│   └── cron/                   # 8 个定时任务 CLI
├── web/admin/                  # 后台前端（零构建静态页 + canvas 图表）
├── scripts/                    # init-db / create-admin / smoke / e2e / check-imports / ui-check
├── sql/                        # 生成的建表脚本
├── deploy/  docs/  screenshots/
└── data/ storage/ logs/ backup/   # 运行时产物（SQLite 库、成品、日志、备份）
```

---

## 4. 实现状态（对照规划书章节）

| 章节 | 内容 | 状态 |
|---|---|---|
| 1 总体架构 | 分层、独立媒体进程 | ✅ API 与 worker 是两个进程，Supervisor 分别守护 |
| 2 服务器/宝塔 | 环境、加固、目录规划 | ✅ 脚本与配置见 `deploy/`；PHP 相关跳过（本项目全是 Node） |
| 3 去水印与合成 | 七步链路、插件化适配器、多线路降级、队列、失败补偿 | ✅ 全部实现；水印在服务端强制合成 |
| 4 数据库设计 | 表结构、索引、金额 decimal | ✅ 表定义在 `src/db/schema.js`，双方言 DDL 自动生成（现 29 张，新增 `invite_code`） |
| 5 卡密系统 | 生成、原子激活、续费语义、边界处理 | ⛔ **已被邀请码取代**（见 5.7）；卡密表保留仅作历史数据，客户端入口已下线 |
| 6 后台功能 | 账号/收益/客服/面板/更新推送 | ✅ 后端全部接口 + 静态后台前端（含「邀请码管理」与「激活账号」） |
| 7 服务端校验与加固 | 9 步校验链、动态密钥、按版本吊销 | ✅ 客户端 so 加固属客户端工作，服务端侧已全部落地 |
| 8 API 设计 | 统一响应、错误码、接口清单 | ✅ 见 [docs/API.md](docs/API.md) |
| 9 部署实施 | 环境、代码、定时任务、Nginx | ✅ |
| 10 安全监控容灾 | 备份加密、监控告警、故障预案 | ✅ 备份 CLI 支持加密/转存/校验；健康检查与告警已接钉钉/企微 |
| 11 里程碑 | P0–P8 | ✅ 服务端部分（P0–P4、P6–P7 的后端） |
| 12 决策点 | 见下表 | ✅ 已按本轮确认的结论落地 |

### 本轮确认的决策

| 决策点 | 结论 | 影响 |
|---|---|---|
| 第 1 条：PHP 还是 Node | **Node.js** | 与媒体处理同栈，一套语言覆盖 API/后台/合成；本机即可验证 |
| 第 2 条：解析线路 | 自带原生适配器 + **本地解析引擎 media-parser** + 第三方 API 模板 | 三类线路可混用，优先级与降级自动生效 |
| 数据库 | **MySQL 生产 + SQLite 本地**，同一份表定义生成两种 DDL | 本机零安装可跑通全链路，生产直接切 MySQL |
| 部署范围 | 全量骨架 + 核心闭环可运行 | P1–P4 完整，P6/P7 接口完整 |

---

## 5. 关键设计说明（为什么这么写）

### 5.1 为什么媒体处理必须独立进程

FFmpeg 合成 1 分钟 1080p 视频要 25–40 秒，塞进请求线程必然把 API 拖死。所以 `src/worker.js` 是独立进程，通过「KV 队列 + 数据库轮询」双通道取任务：

- API 提交任务后 `lpush` 队列并唤醒同进程 worker（低延迟）；
- worker 每轮也按 `status=0` 扫库（Redis 重启/丢消息时任务不会永久卡住）；
- 认领任务用**条件更新**（`UPDATE ... WHERE status=0` 判断影响行数）而不是先查后改，多 worker 不会抢到同一条。

### 5.2 水印为什么只能在服务端

客户端加水印、或服务端只返回参数让客户端加，改包/抓包就能去掉。因此：

- 客户端只能传「要不要水印」，**加什么、加在哪、多透明全由服务端策略决定**（`sys_config` 的 `watermark_*`，可按等级差异化）；
- 成品在服务端用 FFmpeg 重新编码（H.264 CRF 23 + AAC 128k + faststart），仅改容器绕不过去；
- 接口**只下发签名 URL**，不下发原片地址（`dw_task.src_key` 仅用于投诉溯源，不出现在客户端响应里）；
- 签名 URL 15 分钟过期，`sign` 与 `key` 绑定，改一个字符即失效（e2e 有对应用例）。

### 5.3 授权链路的 9 步校验

`src/middleware/clientAuth.js` 按规划书 7.2 实现：版本吊销 → 时间窗(±300s) → nonce 防重放 → 会话 → 签名 → 设备 → 授权状态 → 配额 → 三级限流。

签名算法（客户端 native 层要一致）：

```
body_hash = sha256hex(原始请求体)          // GET 无体时为 sha256hex("")
sign = hmac_sha256_hex(key, timestamp + "\n" + nonce + "\n" + device_id + "\n" + path + "\n" + body_hash)
```

- 引导接口（activate / login / handshake / refresh）用 `MASTER_KEY` 签名——它藏在 so 里；
- 其余接口用会话级 `sign_key`（激活时随机会话密钥，`handshake` 用 `HMAC(MASTER_KEY, server_random+nonce+device_id)` 换发，24 小时有效）；
- **设备号必须先 `trim().toLowerCase()` 再参与签名**，否则大小写不同会被判成两台设备（这是最容易踩的坑，客户端 native 层必须做同样的归一化）；
- 响应也签名：`sign = hmac_sha256_hex(sign_key, JSON.stringify({code,msg,data,ts}))`，`sign` 字段本身不参与计算。

止血手段（规划书 7.3）：后台「按版本一键吊销」会把版本号写进 `app_version_blocklist` 并撤销该版本全部会话，之后该版本的请求一律返回 `9001`，强制用户升级。

### 5.4 数据库为什么用「一份定义生成两种 DDL」

`src/db/schema.js` 是唯一事实来源，`npm run db:sql` 生成 MySQL 与 SQLite 两份建表脚本。两份手写 DDL 必然漂移（改了 MySQL 忘改 SQLite），而测试要跑在 SQLite 上。

两处方言差异是有意为之：

| 项 | MySQL | SQLite | 原因 |
|---|---|---|---|
| 金额 | `decimal(10,2)` | `TEXT`（存 `"12.34"`） | SQLite 的 NUMERIC 会退化成浮点，而**金额绝不能用浮点**；全站金额以整数分运算（`core/money.js`），排序时用 `col + 0` 数值化 |
| 时间 | `datetime` | `TEXT` | 统一 `YYYY-MM-DD HH:mm:ss` 由应用层写入，不依赖 `NOW()`/`ON UPDATE`，跨库迁数据与补算行为一致 |
| JSON | `text` | `TEXT` | MySQL 的 `json` 类型读出来是已解析对象、SQLite 是字符串，同一份业务代码会出现两种行为 |
| 行锁 | `SELECT ... FOR UPDATE` | 无（事务串行 + Promise 链排队） | 卡密激活的并发安全在两种库下都成立 |

### 5.5 解析适配器怎么扩展

`dw_route` 表里的每条线路就是一套配置，改配置不改代码、不用发版（这是这个业务最核心的运维能力，平台风控一年改几十次）：

| endpoint 形式 | 走的适配器 | 说明 |
|---|---|---|
| `native:douyin` | `src/modules/dw/adapters/douyin.js` | 原生实现：短链展开 → 分享页 `_ROUTER_DATA` → `playwm`→`play` 拿无水印直链 |
| `http(s)://.../api/v1/parse` | `mediaparser.js` | 对接本地解析引擎（`name/shuiyin` 的 media-parser，50 平台），`all` 兜底一条即全覆盖 |
| 其它 `http(s)://` | `resolveGeneric` | 第三方 API 模板，用 `config.mapping` 描述字段路径，不必为每个供应商写文件 |
| `native:mock` | `mock.js` | 自检专用（生产种子数据里该线路 `enabled=0`） |

同平台可配多条线路，`priority` 小的先试，失败自动切下一条；每条线路的成功/失败/耗时都进 `dw_route`，近 1 小时成功率低于 80% 会红字告警（`cron:route-health`）。

### 5.7 会员模型：邀请码注册 + 后台开通 + 水印只跟会员走

这是 2026-10 的产品改动，替换掉原先的卡密体系。

**数据与规则**

- 新表 `invite_code`：`code`（16 位去混淆字符集里取 12 位，展示为 `XXXX-XXXX-XXXX`）、`batch_no`、`status`、`used_by`、`used_phone`、`expire_at`、以及注册后的预设 `level / duration / quota_day / max_device`。
- **一个邀请码只能创建一个账号**，与卡密的原子性同理：事务里 `SELECT ... FOR UPDATE` 锁行 → 校验 `status=0` 且未过期 → 建账号 → `UPDATE ... WHERE id=? AND status=0`，受影响行数必须为 1，否则整体回滚。
- `duration = 0`（默认）表示**注册后仍需后台激活**；`duration > 0` 则注册即自动开通（用于活动码）。

**两条开通路径**

| 路径 | 接口 | 说明 |
|---|---|---|
| 客户端自助注册 | `POST /api/v1/auth/register` | `{invite_code, phone, password, password_confirm}`，MASTER_KEY 签名；注册成功即返回会话，`user.activated = false`（未开通会员，但**可以直接用**） |
| 后台创建账号 | `POST /admin/api/users` | 管理员直接建号，**创建即开通**（后台是有意为之的动作） |
| 后台开通已有账号 | `POST /admin/api/users/:id/activate` | `{days, level?, quota_day?, max_device?, remark?}` —— 开通后水印消失 |

**会员只决定「有没有水印」，不决定「能不能用」**

这是与最初实现不同、也是产品明确要求的模型：用户可以选择不开通会员继续使用，只是成品与界面带水印。

| 账号状态 | 能否调用业务接口 | 成品 | 客户端界面 |
|---|---|---|---|
| 未开通会员（`status=0`） | ✅ 可以 | 带水印 | 叠加水印遮罩 |
| 会员已过期（`status=2`） | ✅ 可以 | 带水印 | 叠加水印遮罩 |
| 已开通会员（`status=1`） | ✅ 可以 | **不带水印** | 无水印 |
| 已封禁（`status=3`） | ❌ `2004` | — | — |

- 服务端的判定只有一处：`userService.assertUsable()` 现在**只拦封禁**；
  `ERROR.NOT_ACTIVATED(2001)` 保留为错误码但不再用于「未开通会员」。
- 成品是否加水印由 `dw/taskService` 依据**会员状态**决定（不是无差别强制）：
  `applyWatermark = watermark_enabled && (未开通会员 || !watermark_free_for_member)`。
  关掉 `watermark_free_for_member` 就退回「人人都带水印」的旧行为。
- 客户端的遮罩只看 `/auth/status` 的 `activated` 字段；`activated=false` 时叠加水印。

**客户端的入口位置（本轮 UX 调整）**

- **不做登录门禁**：启动直接进主界面，用户可以先浏览；未登录时同样叠加水印遮罩。
- **登录/注册入口在「我」页顶部的头像与名字**：未登录时身份条显示「点击登录 / 注册」，
  点它（或该卡片任意位置）打开登录页；已登录时点它打开会员面板。
- 会员卡与状态行按真实状态显示：`未开通会员 · 成品带水印` / `已开通会员 · 到期时间`。
- 「短视频一键去水印」在未登录时会提示先到「我」页面登录（这个功能需要账号来记配额与任务）。

**账号标识：用户名 + 手机号，两者都能登录**

| 规则 | 说明 |
|---|---|
| 用户名 | 注册**必填**，2~20 位，只允许中文、字母、数字、下划线、短横线 |
| 不能是手机号 | `1[3-9]` 开头的 11 位数字一律拒绝 —— 否则「登录框里这个名字到底是谁」无法判定 |
| 唯一性 | **忽略大小写**（`Admin` 与 `admin` 是同一个名字），且不会与任何人的手机号撞车 |
| 登录 | `POST /auth/login` 的 `username` 字段传手机号或用户名都行，用户名忽略大小写 |
| 改名 | `POST /auth/profile`（会话签名）：「用户名」可以在 App 里自助修改，**改完立即同步到服务端**；新名字马上能登录、旧名字马上失效，**手机号不受影响**，服务端写 `user_profile_log` 留痕 |
| 后台 | 「添加用户」与「编辑用户」走同一套格式与唯一性校验，改用户名同样留痕 |

客户端入口：「我」页顶部用户行（**整行是一张带边框的卡片**）—— **未登录**点它进登录/注册页（页面标题「注册 / 登录」）；**已登录**点它进「设置」页（头像 / 用户名 / 手机号 / 会员状态 + 底部「退出登录」）；**长按**随时打开快捷账号菜单（修改用户名 / 刷新状态 / 退出登录），右上角「···」同效。

**「我」页版式**（参考设计稿重画，纯 Canvas 绘制，无图片资源）：标题栏「我的 + ···」→ **顶部用户行**（比其它卡片更宽的一张带边框卡片：圆形头像 + 用户名，**没有 ID、没有右侧图标**）→ 深蓝金冠会员卡（金质盾牌王冠 + 有效期 + 金色胶囊按钮）→ 轮播指示点 → 「常用服务」白卡列表（图标块 + 标题 + 灰色说明 + 箭头）。

**头像可以换**（点「设置」页那一行的头像）：

| 环节 | 实现 |
|---|---|
| 选图 | `Intent.ACTION_PICK`（image/*），打不开时退回 `ACTION_GET_CONTENT`；不引第三方图片库 |
| 压缩 | 客户端先按 bounds 算 `inSampleSize`，再缩到最长边 320px，JPEG q88（超 700KB 降到 q60）→ base64 |
| 上传 | `POST /api/v1/auth/avatar`（会话签名，`{image_base64}` 或 `{remove:true}`）；服务端**按文件头识别** JPG/PNG/WebP/GIF，24B~1MB |
| 存储 | 存进存储驱动（本地盘 `storage/media/avatar/<uid>-xxx.jpg` 或 OSS），key 写进 `app_user.avatar`，并写 `user_profile_log` 留痕（换图时删旧图） |
| 读取 | **公开只读** `GET /avatar/:id`（不带签名，供 `BitmapFactory`/`<img>` 直接取），长缓存；换头像后 URL 上的 `?v=更新时间` 变了，客户端自动重新下载 |
| 显示 | 客户端 `AvatarStore` 内存 + 文件双缓存；主界面用 Canvas 圆形裁剪画图，设置页用 `AvatarView`；没有头像就退回用户名首字 |
| 恢复默认 | 设置页**长按**头像 → 二次确认 → 删图并回到首字 |

> ⚠️ 数据迁移：`app_user` 新增 `avatar` 列，`initSchema()` 里加了轻量迁移（`ensureColumns`）——
> `CREATE TABLE IF NOT EXISTS` 不会给老表加列，忘了这步老库会报 `no such column`。
> 另外请求体上限从 1MB 提到 2MB（base64 约 1.4 倍膨胀，1MB 会把正常图片先拦住）。

**成品下载到本地**（解析页那个按钮）：

| 环节 | 实现 |
|---|---|
| 保存位置 | Android 10+ 走 **MediaStore** 写 **`DCIM/豆图/`**（图片走 `Pictures/豆图/`）：**不需要任何存储权限**，写完自动进相册；老系统退回应用外部目录 |
| 进度 | 自己流式下载（不用 DownloadManager），页面上实时显示「正在下载 42%（12.3 / 49.6 MB）」 |
| 完成后 | 按钮从「下载到本地」变成**「打开」**（ACTION_VIEW 交给系统播放器/图库），并提示「已保存到相册：DCIM/豆图/xxx.mp4」 |
| 失败 | 按钮变「重试下载」并把错误显示在按钮下方 |
| 封面 | 视频存好后**顺手把封面图也存进同一个目录**（`<标题>_封面.jpg`）：很多作品首帧是黑场淡入（B 站 MV 尤其明显），相册拿首帧当封面就是一块黑，看着像「没东西」 |

> ⚠️ **目录一定要放在 `DCIM/`**：国产 ROM（vivo/OPPO/小米）的相册只扫 `DCIM` / `Pictures` / `Download`。
> 早先写到 `Movies/豆图` 时，媒体库里明明有记录（`is_pending=0`、时长宽高都探到了），
> 但相册里**根本不出现** —— 真机实测 vivo 就是这样。改到 `DCIM/豆图` 后：
> 相册 → 相册列表里出现「豆图」，相册 → 视频 里能看到成品，**点开能直接播放**
> （证据：[`screenshots/gallery-video-album.png`](screenshots/gallery-video-album.png)、
> [`screenshots/gallery-playback.png`](screenshots/gallery-playback.png)）。
> 存完还会主动 `MediaScannerConnection.scanFile` 扫一次，兼容只认扫描结果的老 ROM。

> 踩坑记录：这块画布上 **`drawCircle` / `drawPath` 的 `LinearGradient` 填充会丢**（`drawRoundRect` 正常）。
> 所以头像、盾牌、金按钮一律「实色打底 + 渐变叠加」，渐变失效时底色依然正确。

**相关公开配置**（`GET /api/v1/config`，后台「全局配置」可改）

| 键 | 默认值 | 作用 |
|---|---|---|
| `register_open` | `1` | 邀请码注册开关；关掉后只能等后台建号 |
| `watermark_free_for_member` | `1` | 会员成品免水印（「开通会员去除水印」的落地开关） |
| `watermark_overlay_text` | `开通会员去除水印` | 未开通会员时遮罩上的文字 |
| `activation_notice` | `未开通会员，可直接使用（成品带水印）` | 未开通会员时的提示文案 |
| `legal_notice` | 版权承诺条款 | 客户端在登录/注册页展示（合规硬性要求） |

### 5.8 解析引擎：本地 media-parser（50 个平台，仅会员可用）

解析能力来自一个**本地 Python sidecar**：项目根目录的 [`media-parser/`](../media-parser)（50 个平台的解析器，
自研线路，不套壳第三方 API）。它与业务进程解耦，通过 `dw_route` 表里的一条线路接入。

| 项目 | 说明 |
|---|---|
| 启动 | `cd media-parser && python -m venv .venv && .venv/Scripts/pip install -r requirements.txt && .venv/Scripts/python run-local.py`（Windows）；Linux 上用 gunicorn，见 `deploy/supervisor.conf` 的 `doutu-media-parser` |
| 关键配置 | `API_ONLY=true` —— 纯解析引擎模式：`/api/v1/parse` **免鉴权**、不建库记请求日志。不开这个就得配 API Key |
| 监听 | 默认 `127.0.0.1:8051`（`run-local.py`），只对本机业务进程开放 |
| 服务端接入 | `.env` 里 `MEDIA_PARSER_BASE=http://127.0.0.1:8051` → `npm run db:init` 会写入线路「本地解析引擎 media-parser」（`platform=all`，priority 50，`src/modules/dw/adapters/mediaparser.js`） |
| 请求契约 | `GET /api/v1/parse?url=<分享文本或链接>` → `{ retcode, retdesc, succ, data:{ platform, title, video_url, cover_url, author, image_list, video_list, audio_url } }`；失败给 `error_code`（`MEDIA_DELETED_OR_PRIVATE` / `PLATFORM_NOT_SUPPORTED` / …） |
| 平台优先级 | 抖音走原生适配器（priority 10，省一次外呼），其它平台与抖音兜底走 media-parser（priority 50）；`native:mock` 只在自检里开 |

**仅会员可用**：`parse_member_only`（默认 `1`）开着时，未开通会员调用 `/api/v1/dw/parse` 返回
`2001 去水印是会员功能，请先联系客服开通会员`，客户端据此弹开通引导。把它设成 `0` 就退回
「未开通也能解析、只是成品带水印」。开关在后台「全局配置」里可改，两条路径自检里都有覆盖。

> 本地调试常踩的两个坑：Windows 没有 `fcntl`（已在 `app.py` 里做降级判断）；
> `ZoneInfo("Asia/Shanghai")` 需要 `tzdata`（Windows 不自带，`pip install tzdata`）。

### 5.9 与规划书不一致的地方（及其理由）

| 规划书 | 本实现 | 理由 |
|---|---|---|
| 口令哈希 bcrypt(cost=12) | Node 内置 **scrypt**（N=16384,r=8,p=1） | 强度不低于 bcrypt，且没有原生编译依赖，宝塔环境不会卡在 node-gyp |
| `config`/`detail` 用 `json` 字段 | 用 `text` 存 JSON 字符串 | 见 5.4；语义相同，双方言行为一致 |
| 依赖 Redis 7 | Redis **可选**，未配置时用进程内实现 | 首期单机 500 用户够用；上第二台机器时必须配 `REDIS_URL`，否则多进程看不到彼此的 nonce 与配额 |
| 「暗水印 DCT 域写入」 | 当前写入媒体元数据（`comment=uid/task`），并在代码中留了扩展位 | 真正的 DCT 隐写需要引入图像处理依赖；元数据成本为零且可取证，强度更高的方案按需再加 |
| 前端 Vue3 + Element Plus | 零构建静态页（原生 JS + canvas 图表） | 后台不引构建链与 CDN，内网/离线也能打开，部署只需同步静态文件 |
| 卡密激活（第 5 章） | **邀请码注册 + 后台激活**（5.7） | 产品要求：一码一账号、真实登录、开通由后台把关；卡密表保留仅作历史数据 |

---

## 6. 验证方式

| 命令 | 覆盖内容 |
|---|---|
| `npm run check-imports` | 56 个模块逐个 import，防相对路径写错与循环依赖 |
| `npm run smoke` | 授权链路 70 项：邀请码注册、用户名/手机号登录、改名、头像上传与公开读取、真实登录、**解析仅限会员（2001）与开关回退**、签名错误、时间窗、重放、一码一账号、两次密码不一致、旧卡密接口已下线、解绑 |
| `npm run e2e` | 端到端 153 项：邀请码注册→未激活受限(2001)→后台激活→解析→FFmpeg 水印合成→签名 URL 下载（含 Range/篡改/过期）→缓存复用→配额与限流→失败退配额→**线路降级到 media-parser 契约**→图集合成→版本检查→热配置→下单支付（mock 渠道，含重复回调幂等）→工单→后台登录/RBAC/账号/邀请码/订单/收益/审计/溯源/CSV 导出→按版本吊销 |
| `E2E_DIALECT=mysql npm run e2e` | **同一套用例跑生产方言**（`MYSQL_HOST/PORT/USER/PASSWORD/DATABASE` 一起给）。最近一次在 MariaDB 11.4.13 上实测：e2e 153/153、smoke 70/70 全过，29 张表与邀请码/头像等新字段都正常建好 |
| `npm run ui:check` | 用系统 Chrome/Edge 无头打开后台，收集 console 报错、校验关键 DOM 并截图（`--user/--pass` 可自动登录） |
| `npm run invite:gen` | 命令行生成邀请码（`--count/--days/--level/--quota/--expire/--out`） |
| `npm run db:init` | 建表 + 初始数据（幂等，可重复执行） |

`npm run e2e` 全程离线：解析线路指向本机起的静态服务，样本视频由 ffmpeg 现场生成。最近一次执行结果：**136 项通过 / 0 项失败**；同一次提交上 `npm run smoke` 40/40、`npm run check-imports` 56/56、后台页面 `ui:check` 全部无 console 报错。真机侧另跑通了「邀请码注册 → 未激活看到水印 → 后台激活 → 水印消失」（见 7.4）。

端到端验收里几个值得一提的用例：

- 「接口不暴露原片地址」—— 断言客户端拿不到未加印的流；
- 「成品与未加印原片不同」—— 用字节比对证明水印确实画上去了，而不只是返回了 200；
- 「配额不足时不留垃圾任务」与「失败后配额已退还」—— 失败不计费、不留脏数据；
- 「配额被拒后 used 不会虚高」—— 回归测试，防止超限请求把当日用量越推越高；
- 「不限量账号（quota_day=0）」—— 语义明确为 `unlimited:true / quota_left:null`；
- 「不上线也能验证解析线路」—— e2e 用本机 mock 服务同时模拟「原生适配器」与「media-parser 接口契约」两条线路，
  并断言主线路失败后任务是由备用线路完成的（`route_id` 命中第二条线路）；
- 「重复回调幂等」—— 第二次回调返回 `already=true` 并留一条 `status=4` 的流水，不加时长、不重复入账。

> 本机没有 MySQL 实例，因此 `npm run e2e` 默认跑 SQLite 方言；
> 生产 MySQL 路径的验证方式见 `docs/DEPLOY.md`（`E2E_DIALECT=mysql` 可让同一套验收跑在 MySQL 上）。

---

## 7. 真机 / 内网联调

服务端默认只监听 `127.0.0.1`（与规划书一致：内网端口不对公网暴露）。要让内网里的手机访问，有两种方式：

### 7.1 方式 A：设备直连内网 IP（推荐，最接近真实链路）

```bash
# 1) 把绑定地址改成 0.0.0.0，并把「签名 URL 的基址」设成设备能访问的内网地址
BIND_HOST=0.0.0.0 PORT=7199 STORAGE_PUBLIC_BASE=http://192.168.1.96:7199 node src/index.js

# 2) 手机浏览器打开（同网段即可）
http://192.168.1.96:7199/devtest/
```

`STORAGE_PUBLIC_BASE` 必须是设备能访问的地址——成品是**签名 URL**，基址写回环地址的话手机就下载不到成品。

Windows 上首次需要放行入站端口（管理员执行一次）：
`New-NetFirewallRule -DisplayName doutu-dev -Direction Inbound -Protocol TCP -LocalPort 7199 -Action Allow`

### 7.2 方式 B：ADB 隧道（不需要改防火墙、也不需要管理员权限）

```bash
adb reverse tcp:7101 tcp:7101        # 设备的 127.0.0.1:7101 → 本机 127.0.0.1:7101
# 手机浏览器打开
http://127.0.0.1:7101/devtest/
```

隧道方式的额外好处：`http://127.0.0.1` 属于**安全上下文**，浏览器才开放 WebCrypto（`crypto.subtle`）。

### 7.3 `/devtest` 自检页做了什么

`public/devtest/index.html` 是一份**跑在设备浏览器里的真实客户端**，用与 `libdwcore.so` 完全相同的算法
（`sign = HMAC-SHA256(ts + "\n" + nonce + "\n" + device_id + "\n" + path + "\n" + body_hash)`）执行：

| # | 检查项 | 说明 |
|---|---|---|
| 1 | 安全上下文 | `crypto.subtle` 可用性 |
| 2 | bootstrap | 取 MASTER_KEY 与一个自动签发的测试邀请码 |
| 3 | `/healthz` | 免签名连通性 |
| 4 | 登录 / 邀请码注册 | 固定手机号先试登录，不存在则用邀请码注册（MASTER_KEY 签名），拿到 `token` / `sign_key` |
| 5 | 授权状态 | 会话密钥签名；读取 `activated` |
| 6 | 未激活拦截 | 未激活时 `/api/v1/dw/parse` 必须返回 `2001`（此时页面停下，等后台激活后刷新继续） |
| 7 | 提交解析 | 已激活时提交 `/api/v1/dw/parse` |
| 8 | 轮询任务 | 直到 `status=3`（服务端 worker 完成 FFmpeg 水印合成） |
| 9 | 下载成品 | 校验 HTTP 200、字节数、`ftyp` 魔数 |
| 10 | Range 请求 | 必须返回 `206`（播放器拖动进度依赖） |
| 11 | 篡改签名 | 必须被拒（`1002`） |

页面最后会把成品直接播放出来，肉眼确认水印在画面上。

> ⚠️ 该端点**仅非生产环境挂载**（`src/app.js` 里由 `config.isProd` 把关），
> 因为 bootstrap 会下发 `MASTER_KEY`。生产环境这条路径完全不存在。
>
> ⚠️ 用**内网 IP + http** 打开该页时，浏览器会因非安全上下文禁用 WebCrypto，页面会明确报出这一点；
> 这不是服务端问题——真实客户端的签名在 Android native 层完成，不受浏览器安全上下文限制。
> 想在浏览器里验签名，请用隧道（`127.0.0.1`）或给站点配 HTTPS。

### 7.4 实测结果（vivo V2220A / Android 14，与本机同网段）

设备浏览器执行 `http://192.168.1.96:7199/devtest/` → **全部通过**，服务端记录：

| 时间 | 任务号 | 来源 IP | 设备号 | 状态 | 成品大小 | 水印 |
|---|---|---|---|---|---|---|
| 13:30:29 | DW20261015133029547155 | 127.0.0.1（隧道） | devtest-158-mv93obpj | 已完成 | 108,129 B | 已加 |
| 13:31:14 | DW20261015133114090835 | 127.0.0.1（隧道） | devtest-158-mv93p9x1 | 已完成 | 108,129 B | 已加 |
| 13:31:41 | DW20261015133141851550 | **192.168.1.165（内网直连）** | devtest-158-mv93pvgu | 已完成 | 108,129 B | 已加 |

截图见 [`screenshots/device-devtest.png`](screenshots/device-devtest.png)（自检通过）与
[`screenshots/device-devtest-steps.png`](screenshots/device-devtest-steps.png)（成品在设备上播放，右下角可见水印）。

### 7.5 App 真机验证：默认进应用 → 未开通带水印 → 后台开通 → 水印消失

这段是**装在手机上的 App 本身**跑出来的，不是浏览器页面。交互按产品要求定成：
**不做登录门禁**（默认就能进应用），**登录/注册入口在「我」页顶部的头像与名字上**，
**会员只决定有没有水印**（不开会员也能用）。

| # | 操作 | 结果 | 证据 |
|---|---|---|---|
| 1 | 全新安装后首次启动 | **直接进主界面**，不弹登录页；全屏叠加斜向水印「开通会员去除水印」 | [`screenshots/app-nogate-home.png`](screenshots/app-nogate-home.png) |
| 2 | 切到「我」页 | 参考设计稿重画：**整行是一张带边框的卡片**（大头像 + 名字 + ID）+ 深蓝金冠 VIP 卡 + 「常用服务」列表；未登录时头像显示「登」、名字位显示「点击登录 / 注册」 | [`screenshots/me-logged-out.png`](screenshots/me-logged-out.png)、[`screenshots/me-redesign.png`](screenshots/me-redesign.png) |
| 2b | 未登录点这一行 | 进登录/注册页，页面顶部标题就是「**注册 / 登录**」 | [`screenshots/login-new.png`](screenshots/login-new.png) |
| 2c | **已登录**点这一行 | 进「设置」页：返回箭头 + 居中标题，白卡里四行（头像 / 用户名 / 手机号 / 会员状态，手机号只一行且打码），**「退出登录」固定在页面最下方** | [`screenshots/settings-page.png`](screenshots/settings-page.png) |
| 2e | 设置页点「头像」→ 系统相册选图 | 压缩上传成功（服务端 `POST /auth/avatar` 200、`storage/media/avatar/8-*.jpg` 15.7KB）；设置页与「我」页那一行立刻显示新头像 | [`screenshots/me-avatar.png`](screenshots/me-avatar.png) |
| 2f | 娱乐页点「短视频一键去水印」（会员） | 进解析页：粘贴链接 → 一键解析 → **进度同步**（解析中 45% → 合成中 → 完成）→ 结果出现在页面底部 → 点结果**内联播放预览** → 预览下方「下载到本地」 | [`screenshots/parse-page.png`](screenshots/parse-page.png)、[`screenshots/parse-progress.png`](screenshots/parse-progress.png)、[`screenshots/parse-result.png`](screenshots/parse-result.png) |
| 2f′ | **首页 → 微信（微页面）→ 抖音去水印** | 同一套门禁、进同一个解析页（微页面第 4 行第 3 格，与娱乐页那格共用一个入口方法） | [`screenshots/wechat-page.png`](screenshots/wechat-page.png) |
| 2g | 真机跑真实链接（B 站）并下载 | media-parser 0.47s 解析命中 → 服务端下载原片 → 会员免水印 → 成品 49.6MB；点「下载到本地」→ 下载中显示百分比 → 完成后按钮变「打开」、提示「已保存到相册：DCIM/豆图/xxx.mp4」 | [`screenshots/parse-download.png`](screenshots/parse-download.png) |
| 2g′ | 相册里能不能看到、能不能播 | 媒体库记录完整（`relative_path=DCIM/豆图/`、`duration=212309`）；系统相册「相册 → 视频」里能看到成品，**点开直接播放正常**（00:33/03:32） | [`screenshots/gallery-video-album.png`](screenshots/gallery-video-album.png)、[`screenshots/gallery-playback.png`](screenshots/gallery-playback.png) |
| 2h | 非会员点这两个入口 | 提示「去水印是会员功能，请先开通会员」并弹会员面板；即使绕过客户端，服务端也返回 `2001` | smoke/e2e 断言 |
| 2d | 设置页里点「用户名」/「会员状态」/「退出登录」 | 分别弹改名框、会员信息、二次确认；确认退出后服务端收到 `/auth/logout` 并撤销会话 | 服务端日志 |
| 3 | 点身份条（头像/名字） | 打开登录页：**渐变头图 + 悬浮白卡**，登录 / 注册新账号两个 Tab，底部带版权承诺（服务端 `legal_notice` 下发） | [`screenshots/login-new.png`](screenshots/login-new.png) |
| 3b | 切到「注册新账号」 | 5 个字段：**用户名** + 邀请码 + 手机号 + 密码 + 确认密码 | [`screenshots/login-register-new.png`](screenshots/login-register-new.png) |
| 4 | 用**已开通会员**的账号登录 | 水印消失；身份条显示「会员有效期至 …」，状态行「已开通会员 · 到期时间」 | [`screenshots/app-member-home.png`](screenshots/app-member-home.png) |
| 5 | 后台把该账号改回「未开通会员」 | **水印立刻回来**；状态行「未开通会员 · 成品带水印」，按钮变「开通」——但功能照常可用 | [`screenshots/app-nonmember-me.png`](screenshots/app-nonmember-me.png) |
| 6 | 后台再「开通会员」 | 水印再次消失 | [`screenshots/app-member-home.png`](screenshots/app-member-home.png) |
| 7 | 真机注册（用户名 `dt_helper` + 邀请码 + 13900007777） | 建号成功：`id=8, status=0 未开通会员`，邀请码核销为「一码一账号」；「我」页身份条显示 `dt_helper` | [`screenshots/me-with-username.png`](screenshots/me-with-username.png) |
| 8 | 点身份条 → 修改用户名 → 保存 | 服务端 `app_user.username` 从 `dt_helper` 变成 `douTu_master`，界面立即刷新 | [`screenshots/me-renamed.png`](screenshots/me-renamed.png) |
| 9 | 退出登录后用**新用户名**登录 | 登录成功回到「我」页（服务端日志 `userId=8`）；另测：旧用户名 `dt_helper` → `账号或密码错误`、手机号 → 通过、大小写变体 `DOUTU_MASTER` → 通过 | [`screenshots/login-by-username.png`](screenshots/login-by-username.png) |

第 5 步是这次改动的关键验证：**未开通会员 ≠ 不能用**。服务端不再对未开通的账号返回 `2001`，
业务接口照常放行，只有成品与界面带水印（见 5.7）。

**这次真机联调踩到的两个坑（都已修掉，值得记下来）**

1. **Android 9+ 默认禁止明文 HTTP**。服务端是 `http://<内网IP>:7199`，请求在**客户端**就被拦掉，
   服务端日志里连一条记录都没有 —— 很容易误判成服务端问题。
   处理：`AndroidManifest.xml` 的 `<application>` 加 `android:usesCleartextTraffic="true"`（已注明正式发布应改 HTTPS 并去掉）。
2. **`/auth/status` 的用户字段是平铺在 `data` 上的**，而 `/auth/register`、`/auth/login` 放在 `data.user` 里。
   客户端一开始只读 `data.user`，导致 `activated` 永远不更新、水印不消失。
   处理：`DoutuApi.Result.user()` 统一兼容两种信封。

另外两处按需求做的取舍：登录页**不**盖水印（那里还没有账号，盖住只会让表单看不清）；
「短视频一键去水印」在未登录时会提示先去「我」页面登录（这个功能需要有账号才能记配额与任务），
登录后无论是否开通会员都能用。

> 客户端代码在 `D:\AAA\doutu\doutu`：新增 `account/Session`、`account/DoutuApi`、`account/LoginActivity`、`account/WatermarkOverlay`，
> 改动 `DoutuApp`（统一挂载水印层）、`MainActivity`（身份条改成登录入口 + 真实会员状态 + 移除授权码弹窗）、`AndroidManifest.xml`。
> 构建：`gradlew assembleDebug`；安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。

---

## 8. 合规边界（规划书第 0 章，必须保留）

本服务端在设计上遵守三条硬约束，请勿移除：

1. **投诉下架通道可用**：后台「任务与线路 → 侵权投诉溯源」支持按 `user_id` / `item_id` / `task_no` / `IP` 任一条件定位到**具体用户、具体任务、具体成品文件**，并给出 1 小时有效期的取证下载链接；`dw_task` 保留原片（`src_key`）与成品（`out_key`）双份记录，会话与登录日志可回溯到设备。
2. **定位是工具而不是搬运工具**：不提供「批量下载他人主页视频」这类功能；批量解析仅限高等级用户且有数量上限（`batch_max`）。
3. **用户协议需含版权承诺条款**：服务端提供「仅限处理自有版权内容」的承诺文案位（`sys_config.legal_notice`），客户端应在首次使用与激活页展示。

水印同时承担品牌曝光与溯源举证作用：`watermark_text_template` 支持 `{nickname} {user_id} {date} {platform}`，成品元数据里也写入了 `uid` 与 `task_no`。

---

## 9. 常见问题

| 现象 | 原因与处理 |
|---|---|
| 客户端全部请求返回 `1002` 签名失败 | 先查两端时钟（`timedatectl status`）。时间窗只有 ±300 秒，服务器或手机时钟漂移会导致全量失败 |
| 客户端 `2005` | 会话被撤销或过期；让它调 `/auth/handshake` 换发，再不行就重新登录 |
| 水印渲染成方块 | 服务器没装中文字体。Ubuntu：`apt install fonts-wqy-zenhei`，并把 `WATERMARK_FONT` 指向 `/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc` |
| 任务一直卡在排队 | worker 进程没起来（`supervisorctl status doutu-worker`），或 `WORKER_CONCURRENCY` 为 0 |
| 合成失败、报「无法启动 FFmpeg」 | `FFMPEG_PATH` 不对或 ffmpeg 未安装；后台「面板管理 → 环境自检」可直接看到 ffmpeg/ffprobe/字体状态 |
| MySQL 拒写 | 磁盘满。`TMPDIR` 必须指向大容量分区，并确认 `cron:cleanup` 在跑 |
| 队列积压告警 | 解析线路集体失效或 worker 掉线，先看 `dw_route.succ_1h/fail_1h` 与后台首页线路成功率 |
| 上线后想改后台入口 | 改 `.env` 的 `ADMIN_PATH`，同时改 `deploy/nginx.conf` 的 location 并 reload |
