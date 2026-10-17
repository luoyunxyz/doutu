#!/usr/bin/env bash
# =============================================================================
# 短视频去水印平台 · 部署 / 更新脚本（Ubuntu 22.04 + 宝塔面板）
# -----------------------------------------------------------------------------
# 特性：
#   - 幂等：可以反复执行；已存在的目录、.env、数据库不会被覆盖或重复初始化。
#   - 严格模式：set -euo pipefail，任何一步失败立即中断，绝不带着半成品继续。
#   - 关键步骤全部回显，便于宝塔「计划任务/shell 终端」里直接看结果。
#
# 用法：
#   bash deploy.sh                                  # 使用默认仓库与分支
#   bash deploy.sh <git仓库地址> <分支>              # 首次部署指定来源
#   首次部署完成后，日常发版只需：
#     supervisorctl restart doutu-api                     # 只改后端逻辑，无需重启 worker
#     supervisorctl restart doutu-api doutu-worker        # 改了媒体处理逻辑
#
# 环境变量覆盖（可选）：
#   APP_DIR  默认 /www/wwwroot/api.example.com
#   APP_USER 默认 www
#   TMP_DIR  默认 /www/tmp
#   LOG_DIR  默认 /www/logs
#   NODE_BIN 默认 /www/server/nodejs/v18/bin/node
#
# 约定：脚本会引用以下 npm script 与入口文件（均由本代码库提供）：
#   npm run db:init        建表 + 种子数据（node scripts/init-db.js，幂等）——已实现
#   npm run admin:create   创建或重置管理员（node scripts/create-admin.js）——尚未实现
# 另外本脚本假定下列入口在部署后存在（缺失会导致 supervisor 起不来，见 docs/DEPLOY.md 2.1 节）：
#   src/index.js           业务 API 入口
#   src/worker.js          媒体处理入口
#   src/cron/*.js          定时任务（由 deploy/crontab.txt 调用）
# 若某天这些名称改动，请同步修改本脚本与 deploy/ 下其他产物，否则部署会中断或静默失效。
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# 0. 参数与全局常量
# -----------------------------------------------------------------------------
REPO_URL="${1:-}"
REPO_BRANCH="${2:-main}"

APP_DIR="${APP_DIR:-/www/wwwroot/api.example.com}"
APP_USER="${APP_USER:-www}"
TMP_DIR="${TMP_DIR:-/www/tmp}"
LOG_DIR="${LOG_DIR:-/www/logs}"
BACKUP_DIR="${BACKUP_DIR:-/www/backup/db}"
NODE_BIN="${NODE_BIN:-/www/server/nodejs/v18/bin/node}"

SUPERVISOR_CONF_SRC="${APP_DIR}/deploy/supervisor.conf"
SUPERVISOR_CONF_DST="/etc/supervisor/conf.d/doutu.conf"
CRON_SRC="${APP_DIR}/deploy/crontab.txt"
CRON_DST="/etc/cron.d/doutu"
NGINX_CONF_SRC="${APP_DIR}/deploy/nginx.conf"

# 回显函数：加时间戳，方便比对步骤耗时
log()  { printf '\n\033[1;32m[%s] %s\033[0m\n' "$(date '+%F %T')" "$*"; }
warn() { printf '\n\033[1;33m[%s][WARN] %s\033[0m\n' "$(date '+%F %T')" "$*"; }
die()  { printf '\n\033[1;31m[%s][ERROR] %s\033[0m\n' "$(date '+%F %T')" "$*" >&2; exit 1; }

# -----------------------------------------------------------------------------
# 1. 前置检查：必须 root 执行；必须存在 Node 与 npm
# -----------------------------------------------------------------------------
[[ "${EUID}" -eq 0 ]] || die "必须用 root 执行：sudo bash deploy.sh"
command -v git >/dev/null 2>&1 || die "未安装 git：apt-get install -y git"

[[ -x "${NODE_BIN}" ]] || die "找不到 Node 可执行文件 ${NODE_BIN}
宝塔面板 -> 软件商店 -> Node.js 版本管理器 安装 20 LTS，然后：
  ls /www/server/nodejs/                  # 查看真实目录名
  NODE_BIN=/www/server/nodejs/v20.18.0/bin/node bash deploy.sh
本项目要求 Node.js >= 20（依赖 node:sqlite、ESM 等能力），不要用 18。"

NODE_VERSION="$("${NODE_BIN}" -v)"
NODE_MAJOR="${NODE_VERSION#v}"; NODE_MAJOR="${NODE_MAJOR%%.*}"
[[ "${NODE_MAJOR}" -ge 20 ]] || warn "当前 Node 版本为 ${NODE_VERSION}，低于要求的 20，请尽快升级（先改 supervisor.conf 的 command 与 PATH）。"

log "使用 Node：${NODE_BIN}（${NODE_VERSION}）"

# 入口文件预检：缺入口时 supervisor 会反复启动失败，提前告警比事后排 502 更省事
for f in src/index.js src/worker.js; do
    [[ -f "${APP_DIR}/${f}" ]] || warn "缺少入口 ${APP_DIR}/${f} —— supervisor 起不来，请先补齐（见 docs/DEPLOY.md 2.1 节）"
done
[[ -f "${APP_DIR}/src/cron/expire-check.js" ]] || warn "缺少 src/cron/*.js —— crontab 任务会报 MODULE_NOT_FOUND"
[[ -f "${APP_DIR}/package-lock.json" ]] || warn "缺少 package-lock.json —— npm ci 会失败，脚本将降级为 npm install --omit=dev"

# -----------------------------------------------------------------------------
# 2. 系统时区与时间同步（最高优先级，放最前面）
#    签名时间窗 SIGN_WINDOW_SEC=300：服务器时钟偏移超过 5 分钟，
#    所有客户端的请求签名都会校验失败，表现为「全站 401/签名错误」，
#    这是最容易发生、也最难第一时间定位的线上故障，因此部署时必须强制校时。
# -----------------------------------------------------------------------------
log "步骤 1/12：校准时区与 NTP 时间同步"
timedatectl set-timezone Asia/Shanghai
timedatectl set-ntp true
timedatectl status | sed -n '1,6p'

# Ubuntu 22.04 默认使用 systemd-timesyncd；若装了 chrony 请确保只有一个 NTP 服务在跑
if systemctl list-unit-files | grep -q '^chrony\.service'; then
    warn "检测到 chrony，将使用 chrony 校时（同时会禁用 systemd-timesyncd，避免两个 NTP 服务抢时钟）"
    systemctl disable --now systemd-timesyncd >/dev/null 2>&1 || true
    systemctl enable --now chrony
    chronyc makestep >/dev/null 2>&1 || warn "chronyc makestep 执行失败，请手动检查 chronyc sources"
else
    systemctl enable --now systemd-timesyncd >/dev/null 2>&1 || true
fi

# 打印当前与 NTP 的偏差，便于人工确认（正常应在毫秒级）
if command -v timedatectl >/dev/null 2>&1; then
    timedatectl show -p NTPSynchronized -p TimeUSec || true
fi

# -----------------------------------------------------------------------------
# 3. 运行用户 www
#    以低权限用户跑 Node：ffmpeg、解析引擎一旦被攻破，也不会直接拿到 root。
# -----------------------------------------------------------------------------
log "步骤 2/12：检查运行用户 ${APP_USER}"
if id -u "${APP_USER}" >/dev/null 2>&1; then
    echo "用户 ${APP_USER} 已存在（uid=$(id -u "${APP_USER}")）"
else
    useradd -r -s /sbin/nologin -M "${APP_USER}"
    echo "已创建系统用户 ${APP_USER}"
fi

# -----------------------------------------------------------------------------
# 4. 目录规划与权限
#    与规划书 2.4 节、supervisor.conf、crontab.txt 保持一致：
#      APP_DIR     代码 + .env + storage
#      /www/tmp    ffmpeg 中间文件（务必在大容量分区，且允许 www 读写）
#      /www/logs   应用日志 + cron 日志 + nginx 日志
#      /www/backup 数据库备份（本地 7 天）
# -----------------------------------------------------------------------------
log "步骤 3/12：创建目录并设置权限"
mkdir -p "${APP_DIR}"
# 与 src/config.js 的默认值保持一致：./storage/media（成品）与 ./storage/tmp（中间文件）
mkdir -p "${APP_DIR}/storage/media"    # STORAGE_LOCAL_DIR，成品/素材本地盘
mkdir -p "${APP_DIR}/storage/tmp"      # TMPDIR 的兜底位置（生产应在 .env 里改到 ${TMP_DIR}）
mkdir -p "${APP_DIR}/server/public/upgrade"   # 升级包静态目录（Nginx 直接读）
mkdir -p "${TMP_DIR}"
mkdir -p "${LOG_DIR}/nginx"
mkdir -p "${BACKUP_DIR}"

chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}" "${TMP_DIR}" "${LOG_DIR}" "${BACKUP_DIR}"
chmod 750 "${TMP_DIR}" "${LOG_DIR}" "${BACKUP_DIR}"
chmod 755 "${APP_DIR}" "${APP_DIR}/server/public/upgrade"

# /www/tmp 如果和生产数据目录在同一块盘，磁盘满会连带 MySQL 拒写，
# 建议单独挂载数据盘后把 TMP_DIR 指过去（见 docs/DEPLOY.md「目录规划」）。
df -h "${TMP_DIR}" | sed -n '1,2p'

# -----------------------------------------------------------------------------
# 5. 拉取 / 更新代码
#    幂等策略：目录里没有 .git 就 clone；已有则 fetch + reset --hard，
#    保证服务器工作区始终与目标分支一致（服务器上不允许人工改代码）。
# -----------------------------------------------------------------------------
log "步骤 4/12：同步代码到 ${APP_DIR}"
if [[ -z "${REPO_URL}" && ! -d "${APP_DIR}/.git" ]]; then
    die "首次部署必须给出仓库地址：bash deploy.sh <git仓库地址> <分支>"
fi

if [[ -d "${APP_DIR}/.git" ]]; then
    if [[ -n "${REPO_URL}" ]]; then
        git -C "${APP_DIR}" remote set-url origin "${REPO_URL}"
    fi
    git -C "${APP_DIR}" fetch --all --prune
    # -B 而非 checkout：即使本地分支不存在（或处于游离 HEAD）也能强制对齐远端
    git -C "${APP_DIR}" checkout -B "${REPO_BRANCH}" "origin/${REPO_BRANCH}"
    git -C "${APP_DIR}" reset --hard "origin/${REPO_BRANCH}"
    echo "已更新到：$(git -C "${APP_DIR}" log -1 --format='%h %ad %s' --date=short)"
else
    # clone 到临时目录再搬进来，避免 clone 失败留下半个可用目录
    TMP_CLONE="$(mktemp -d)"
    git clone --branch "${REPO_BRANCH}" --depth 1 "${REPO_URL}" "${TMP_CLONE}/repo"
    shopt -s dotglob
    cp -a "${TMP_CLONE}/repo/." "${APP_DIR}/"
    shopt -u dotglob
    rm -rf "${TMP_CLONE}"
    echo "已完成首次拉取：$(git -C "${APP_DIR}" log -1 --format='%h %s')"
fi

# 代码目录下的 .env 权限必须收紧（下面第 6 步处理内容，这里只保护已有文件）
[[ -f "${APP_DIR}/.env" ]] && chmod 600 "${APP_DIR}/.env"

# -----------------------------------------------------------------------------
# 6. 生产依赖
#    npm ci 严格按 package-lock.json 安装，结果可复现；
#    --omit=dev 不装 devDependencies，减小攻击面与体积。
#    以 APP_USER 身份执行，避免 node_modules 属主变成 root。
# -----------------------------------------------------------------------------
log "步骤 5/12：安装生产依赖（npm ci --omit=dev）"
[[ -f "${APP_DIR}/package.json" ]] || die "${APP_DIR} 下没有 package.json，代码同步可能失败"
[[ -f "${APP_DIR}/package-lock.json" ]] || warn "缺少 package-lock.json，npm ci 会失败；请先在开发机提交 lockfile"

cd "${APP_DIR}"
export PATH="$(dirname "${NODE_BIN}"):${PATH}"
if [[ -f package-lock.json ]]; then
    runuser -u "${APP_USER}" -- env "PATH=${PATH}" npm ci --omit=dev --no-audit --no-fund
else
    warn "降级为 npm install --omit=dev（仅供临时排障使用）"
    runuser -u "${APP_USER}" -- env "PATH=${PATH}" npm install --omit=dev --no-audit --no-fund
fi
chown -R "${APP_USER}:${APP_USER}" "${APP_DIR}/node_modules"

# -----------------------------------------------------------------------------
# 7. 环境变量文件 .env
#    脚本只做「存在性检查 + 权限收紧 + 模板落地」，绝不覆盖生产 .env。
#    首次部署会从 .env.example 复制一份模板，必须人工填写后再继续。
# -----------------------------------------------------------------------------
log "步骤 6/12：检查环境变量文件"
if [[ -f "${APP_DIR}/.env" ]]; then
    chmod 600 "${APP_DIR}/.env"
    chown "${APP_USER}:${APP_USER}" "${APP_DIR}/.env"
    echo ".env 已存在，未做修改（权限 600）"
else
    if [[ -f "${APP_DIR}/.env.example" ]]; then
        cp "${APP_DIR}/.env.example" "${APP_DIR}/.env"
        chmod 600 "${APP_DIR}/.env"
        chown "${APP_USER}:${APP_USER}" "${APP_DIR}/.env"
        warn "已从 .env.example 生成 ${APP_DIR}/.env —— 现在是模板值，必须人工填写！
至少填写：MYSQL_PASSWORD、MASTER_KEY（32 字节随机串）、EPAY_PID、EPAY_KEY、
          STORAGE_PUBLIC_BASE、EPAY_NOTIFY_BASE，并把 DB_DIALECT 设为 mysql。
生成随机密钥：openssl rand -hex 32
填好后重新执行本脚本以完成初始化。"
        die "请先完善 .env，再重新运行 deploy.sh（这是有意中断，避免用模板值上线）"
    else
        die "既没有 .env 也没有 .env.example，无法继续。请先按 docs/DEPLOY.md「环境变量全表」准备 .env"
    fi
fi
chmod 600 "${APP_DIR}/.env"

# .env 关键项体检：占位符/空值/格式不对一律中止（宁可中断，也不要用模板值上线）
if grep -qE '^(MASTER_KEY|MYSQL_PASSWORD)=("")?$|^(MASTER_KEY|MYSQL_PASSWORD)=(")?(REPLACE|CHANGE_ME|change_me|TODO|xxx)' "${APP_DIR}/.env"; then
    die ".env 中 MASTER_KEY 或 MYSQL_PASSWORD 仍是占位/空值，请填真实值后重试"
fi
# MASTER_KEY 必须是 64 位十六进制（32 字节）：src/config.js 用它派生会话密钥
if grep -qE '^MASTER_KEY=' "${APP_DIR}/.env"; then
    MK="$(grep -E '^MASTER_KEY=' "${APP_DIR}/.env" | tail -1 | cut -d= -f2- | tr -d '"' | tr -d "'" | tr -d '[:space:]')"
    if [[ ! "${MK}" =~ ^[0-9a-fA-F]{64}$ ]]; then
        die "MASTER_KEY 不合法：必须恰好 64 位十六进制字符（32 字节）。
当前长度 ${#MK}。生成方式：openssl rand -hex 32"
    fi
fi
# 生产环境必须是 mysql 方言
if ! grep -qE '^DB_DIALECT=mysql' "${APP_DIR}/.env"; then
    warn "DB_DIALECT 不是 mysql —— 生产环境请改为 mysql（当前读到：$(grep -E '^DB_DIALECT=' "${APP_DIR}/.env" | tail -1 || echo '未设置'))"
fi

# -----------------------------------------------------------------------------
# 8. 数据库初始化
#    npm run db:init 由主程序提供，必须是幂等的迁移脚本。
#    执行前先探测 MySQL 可达性，避免把连接错误伪装成「迁移失败」。
# -----------------------------------------------------------------------------
log "步骤 7/12：初始化数据库结构（npm run db:init）"
if grep -qE '^DB_DIALECT=mysql' "${APP_DIR}/.env"; then
    MYSQL_HOST="$(grep -E '^MYSQL_HOST=' "${APP_DIR}/.env" | tail -1 | cut -d= -f2- | tr -d '"' || true)"
    MYSQL_PORT="$(grep -E '^MYSQL_PORT=' "${APP_DIR}/.env" | tail -1 | cut -d= -f2- | tr -d '"' || true)"
    MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"; MYSQL_PORT="${MYSQL_PORT:-3306}"
    if command -v mysqladmin >/dev/null 2>&1; then
        mysqladmin --protocol=tcp -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" ping >/dev/null 2>&1 \
            || warn "MySQL ${MYSQL_HOST}:${MYSQL_PORT} ping 不通，请确认宝塔已启动 MySQL 8.0 且端口只监听 127.0.0.1"
    fi
fi
runuser -u "${APP_USER}" -- env "PATH=${PATH}" npm run db:init

# -----------------------------------------------------------------------------
# 9. 创建管理员
#    npm run admin:create 由主程序提供；幂等实现应为「已存在则重置密码」。
#    非交互场景可通过 ADMIN_USER / ADMIN_PASS 环境变量注入（约定由主程序读取）。
# -----------------------------------------------------------------------------
log "步骤 8/12：创建 / 重置管理员账号（npm run admin:create）"
if [[ -n "${ADMIN_USER:-}" && -n "${ADMIN_PASS:-}" ]]; then
    runuser -u "${APP_USER}" -- env "PATH=${PATH}" ADMIN_USER="${ADMIN_USER}" ADMIN_PASS="${ADMIN_PASS}" \
        npm run admin:create
else
    warn "未提供 ADMIN_USER / ADMIN_PASS，将进入交互式创建（若主程序不支持交互，请改为：
    ADMIN_USER=admin ADMIN_PASS='强密码' bash deploy.sh）"
    runuser -u "${APP_USER}" -- env "PATH=${PATH}" npm run admin:create
fi

# -----------------------------------------------------------------------------
# 10. Supervisor
#     把 deploy/supervisor.conf 落到 /etc/supervisor/conf.d/doutu.conf，
#     然后用 reread+update 做「增量加载」，不 restart supervisor 本身，
#     避免把其他项目的进程一起打断。
# -----------------------------------------------------------------------------
log "步骤 9/12：配置并重启 Supervisor"
command -v supervisord >/dev/null 2>&1 || die "未安装 supervisor：apt-get install -y supervisor"

# 文件内容有变化才覆盖，保证幂等且不产生无意义的时间戳跳动
if [[ -f "${SUPERVISOR_CONF_SRC}" ]]; then
    if ! cmp -s "${SUPERVISOR_CONF_SRC}" "${SUPERVISOR_CONF_DST}" 2>/dev/null; then
        install -m 644 -o root -g root "${SUPERVISOR_CONF_SRC}" "${SUPERVISOR_CONF_DST}"
        echo "已更新 ${SUPERVISOR_CONF_DST}"
    else
        echo "${SUPERVISOR_CONF_DST} 内容无变化，跳过覆盖"
    fi
else
    die "缺少 ${SUPERVISOR_CONF_SRC}"
fi

# supervisor.conf 里 Node 路径写死为 v18 目录，这里按实际 NODE_BIN 校正，避免人工遗漏
if [[ "${NODE_BIN}" != "/www/server/nodejs/v18/bin/node" ]]; then
    sed -i "s#/www/server/nodejs/v18/bin/node#${NODE_BIN}#g" "${SUPERVISOR_CONF_DST}"
    echo "已将 supervisor 配置中的 node 路径替换为 ${NODE_BIN}"
fi

systemctl enable supervisor >/dev/null 2>&1 || true
systemctl start supervisor
supervisorctl reread
supervisorctl update
# 首次部署时进程尚未启动，restart 会报 "no such process"；先 start 再 restart 则始终安全
for p in doutu-api doutu-worker; do
    supervisorctl start "${p}" 2>/dev/null || true
    supervisorctl restart "${p}"
done
sleep 3
supervisorctl status

# -----------------------------------------------------------------------------
# 11. crontab
#     安装到 /etc/cron.d/doutu（root 属主、无点号后缀，符合 cron 对该目录的要求）。
# -----------------------------------------------------------------------------
log "步骤 10/12：安装定时任务"
if [[ -f "${CRON_SRC}" ]]; then
    install -m 644 -o root -g root "${CRON_SRC}" "${CRON_DST}"
    # 同样按实际 Node 路径校正
    if [[ "${NODE_BIN}" != "/www/server/nodejs/v18/bin/node" ]]; then
        sed -i "s#/www/server/nodejs/v18/bin/node#${NODE_BIN}#g" "${CRON_DST}"
    fi
    systemctl enable cron >/dev/null 2>&1 || true
    systemctl restart cron
    echo "已安装 ${CRON_DST} 并重启 cron，当前生效任务："
    grep -E '^[0-9*]' "${CRON_DST}" || true
    echo "生效任务条数：$(grep -cE '^[0-9*]' "${CRON_DST}")"
else
    warn "缺少 ${CRON_SRC}，跳过定时任务安装"
fi

# -----------------------------------------------------------------------------
# 12. Nginx
#     本脚本不直接改站点配置（宝塔面板会覆写自己的 vhost 文件），
#     只做「语法校验 + 提示人工粘贴」。若已由面板写入同一份配置则仅校验并 reload。
# -----------------------------------------------------------------------------
log "步骤 11/12：Nginx 配置校验与重载"
if command -v nginx >/dev/null 2>&1; then
    if [[ -f "${NGINX_CONF_SRC}" ]]; then
        # 把 upstream + server 段临时塞进一个完整 http 上下文做语法校验，避免污染线上配置。
        # 注意：这份临时配置里限流区（附录 A）仍是注释状态，所以「zone 未定义」这类错误
        # 不会被这次校验覆盖，正式生效前仍必须执行一次 nginx -t 看真实报错。
        NGINX_TEST_DIR="$(mktemp -d)"
        {
            echo "events { worker_connections 1024; }"
            echo "http {"
            echo "  include /etc/nginx/mime.types;"
            sed -n '/^upstream /,$p' "${NGINX_CONF_SRC}" | sed 's#^#  #'
            echo "}"
        } > "${NGINX_TEST_DIR}/test.conf" 2>/dev/null || true
        if nginx -t -c "${NGINX_TEST_DIR}/test.conf" >/dev/null 2>&1; then
            echo "nginx.conf 语法校验通过"
        else
            warn "nginx.conf 语法校验未通过（常见原因：限流区 zone 未在 http 层定义、
证书路径不存在、端口被其他站点占用）。
请先看完整报错：nginx -t -c ${NGINX_TEST_DIR}/test.conf"
        fi
        rm -rf "${NGINX_TEST_DIR}"
    fi
    nginx -t
    systemctl reload nginx
    echo "Nginx 已重载"
    if [[ ! -f "/www/server/panel/vhost/nginx/api.example.com.conf" ]]; then
        warn "未检测到宝塔站点配置文件，请把 ${NGINX_CONF_SRC} 的 server 段
粘贴到 宝塔 -> 网站 -> api.example.com -> 设置 -> 配置文件，
并把附录 A 的限流区三行放进 nginx http {} 中，再执行 nginx -t && systemctl reload nginx"
    fi
else
    warn "未找到 nginx 命令，跳过（宝塔面板环境请用面板的「重载配置」）"
fi

# -----------------------------------------------------------------------------
# 13. 部署后自检
#     只做只读探测：进程状态 + 端口监听 + 本机健康检查。
# -----------------------------------------------------------------------------
log "步骤 12/12：部署后自检"
supervisorctl status doutu-api doutu-worker || true
ss -lntp 2>/dev/null | grep -E '127\.0\.0\.1:(7101|8051|3306|6379)' || warn "未看到内网端口监听，请检查对应进程"

if curl -fsS --max-time 5 "http://127.0.0.1:7101/healthz" >/dev/null 2>&1; then
    echo "本机健康检查通过：http://127.0.0.1:7101/healthz"
else
    warn "本机健康检查失败。排查顺序：
  1) supervisorctl status                     # 进程是否 RUNNING
  2) supervisorctl tail -100 doutu-api stderr # 启动报错
  3) grep -E '^(DB_DIALECT|MYSQL_|MASTER_KEY)' ${APP_DIR}/.env   # 配置是否可用
  4) timedatectl                              # 时间是否同步"
fi

log "部署流程执行完毕"
cat <<'EOF'
-------------------------------------------------------------
人工待办（脚本不会替你做的部分）：
  [ ] 确认 .env 中所有 REPLACE_ 占位符已替换
  [ ] 宝塔 -> 网站 -> api.example.com -> 申请/部署 SSL 证书
  [ ] 把 deploy/nginx.conf 的 server 段写入站点配置，并处理附录 A 限流区
  [ ] 宝塔 -> 安全：放行 80/443，禁止 7101/8051/3306/6379 对公网开放
  [ ] 安装 ffmpeg 与中文字体：apt-get install -y ffmpeg fonts-wqy-zenhei
  [ ] 验证升级包目录可下载：curl -I https://api.example.com/upgrade/<包名>
-------------------------------------------------------------
EOF
