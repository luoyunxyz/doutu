/**
 * 配置装载：.env 文件（若存在）→ process.env → 带默认值的强类型化配置对象。
 *
 * 约定：
 *  - 所有路径型配置在读取时就解析成绝对路径，避免 cwd 变化导致写错目录；
 *  - 布尔/数字统一在这里转换，业务代码里不再出现字符串比较。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

/** 极简 .env 解析：支持 KEY=VALUE、# 注释、可选引号；已存在的环境变量优先。 */
export function loadEnvFile(file = path.join(ROOT_DIR, '.env')) {
  if (!fs.existsSync(file)) return false;
  const text = fs.readFileSync(file, 'utf8');
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"') && value.length > 1) ||
      (value.startsWith("'") && value.endsWith("'") && value.length > 1)
    ) {
      value = value.slice(1, -1);
    }
    if (process.env[key] === undefined) process.env[key] = value;
  }
  return true;
}

const env = (key, fallback = '') => {
  const v = process.env[key];
  return v === undefined || v === null || v === '' ? fallback : String(v).trim();
};
const num = (key, fallback) => {
  // 注意：不能直接 Number('')，那会得到 0 —— 未配置的阈值会全部变成 0，
  // 例如 ACTIVATE_FAIL_LIMIT=0 会让第一次失败就触发封禁。
  const raw = env(key, '');
  if (raw === '') return fallback;
  const v = Number(raw);
  return Number.isFinite(v) ? v : fallback;
};
const bool = (key, fallback = false) => {
  const v = env(key, '').toLowerCase();
  if (!v) return fallback;
  return ['1', 'true', 'yes', 'on'].includes(v);
};
const abs = (p, fallback) => path.resolve(ROOT_DIR, p || fallback);

/** 站内路径归一化：保证以 / 开头、不以 / 结尾 */
function normalizePath(p) {
  let s = String(p || '/admin').trim();
  if (!s.startsWith('/')) s = `/${s}`;
  return s.replace(/\/+$/, '') || '/admin';
}

loadEnvFile();

export const config = {
  rootDir: ROOT_DIR,
  env: env('NODE_ENV', 'development'),
  isProd: env('NODE_ENV', 'development') === 'production',
  bindHost: env('BIND_HOST', '127.0.0.1'),
  port: num('PORT', 7101),

  log: {
    level: env('LOG_LEVEL', 'info'),
    dir: abs(env('LOG_DIR', './logs'), './logs'),
  },

  db: {
    dialect: env('DB_DIALECT', 'sqlite').toLowerCase(),
    sqliteFile: abs(env('SQLITE_FILE', './data/doutu.db'), './data/doutu.db'),
    mysql: {
      host: env('MYSQL_HOST', '127.0.0.1'),
      port: num('MYSQL_PORT', 3306),
      user: env('MYSQL_USER', 'root'),
      password: env('MYSQL_PASSWORD', ''),
      database: env('MYSQL_DATABASE', 'doutu'),
      connectionLimit: num('MYSQL_POOL', 10),
      charset: 'utf8mb4',
      timezone: 'local',
      dateStrings: true,
      supportBigNumbers: true,
      bigNumberStrings: false,
      decimalNumbers: false,
      multipleStatements: true,
    },
  },

  redis: {
    url: env('REDIS_URL', ''),
  },

  security: {
    /**
     * 主密钥：**只留在服务端**，客户端的任何包里都不该出现它（2026-10-17 起已经彻底移除）。
     *
     * 密钥分工原则 —— 一把钥匙只干一件事，泄露一把不牵连其他：
     *   · MASTER_KEY      部署期的主密钥；未单独配置时，下面几项回退到它（保持兼容）
     *   · STORAGE_SECRET  成品下载 URL 的签名（泄露不至于波及引导接口）
     *   · ROOT_SIGN_KEY   老客户端的明文 root 签名（只有版本检查这类白名单接口还在用）
     *   · DTW_PRIVATE_KEY 私有协议握手（见 config.dtw）
     *   · 会话 sign_key   已改为**每会话随机**，不再由任何密钥派生
     *   · 管理端          账密 + 令牌，不使用密钥
     */
    masterKey: env('MASTER_KEY', ''),
    storageSecret: env('STORAGE_SECRET', ''),
    rootSignKey: env('ROOT_SIGN_KEY', ''),
    signWindowSec: num('SIGN_WINDOW_SEC', 300),
    sessionTtlHours: num('SESSION_TTL_HOURS', 24),
    adminTokenTtlMin: num('ADMIN_TOKEN_TTL_MIN', 120),
    adminIdleTimeoutMin: num('ADMIN_IDLE_TIMEOUT_MIN', 30),
    activateFailLimit: num('ACTIVATE_FAIL_LIMIT', 20),
    activateLockMinutes: num('ACTIVATE_LOCK_MINUTES', 60),
    minSupportedVersionCode: num('MIN_SUPPORTED_VERSION_CODE', 0),
    // 后台入口可改（规划书 6.4「后台地址管理」）：改成 /x7k9m2 这类不可猜路径，
    // 能挡掉绝大部分自动化扫描。改完必须同步改 Nginx 的 location。
    adminPath: normalizePath(env('ADMIN_PATH', '/admin')),
  },

  /**
   * 私有传输协议 DTW/1（2026-10-17）。
   *
   * 目标：客户端不再持有服务端根密钥，且抓包看到的是密文而不是明文 JSON。
   * 三段式：
   *   1) hello  —— 服务端下发一次性 challenge（明文 + RSA 私钥签名，客户端拿内置公钥验签）；
   *   2) open   —— 客户端用服务端公钥做混合加密，把 session_key 与「慢哈希证明」送上来，
   *                服务端复算证明（salt/challenge 都是自己发的，所以真能验），发回会话 id；
   *   3) tunnel —— 之后所有接口都是 AES-256-GCM 帧，载荷里带真实 method/path/body。
   *
   * ⚠️ 老实说：私有协议只能提高门槛（要逆向出协议才能伪造客户端），
   * 挡不住铁了心的人。真正的价值在于「客户端不再有长期密钥」+「服务端可限流可按设备吊销」。
   */
  dtw: {
    enabled: bool('DTW_ENABLED', false),
    // 打开后：不在白名单里的 /api/v1 明文请求一律拒绝（只保新包）
    require: bool('DTW_REQUIRE', false),
    tunnelPath: normalizePath(env('DTW_TUNNEL_PATH', '/api/v1/dtw')),
    handshakePath: normalizePath(env('DTW_HANDSHAKE_PATH', '/api/v1/dtw/hs')),
    // 会话与时间窗
    sessionTtlSec: num('DTW_SESSION_TTL', 3600),
    windowSec: num('DTW_TS_WINDOW', 300),
    // 客户端 key 派生（慢哈希）的迭代次数：两端必须一致
    proofIter: num('DTW_PROOF_ITER', 20000),
    // 盐按代轮换：换 DTW_SALT_ID 就能让旧客户端的 hello 失效
    salt: env('DTW_SALT', ''),
    saltId: env('DTW_SALT_ID', 's1'),
    kid: env('DTW_KID', 'k1'),
    // 服务端 RSA 私钥（PEM）。生产必须配置；可用 \n 转义写成单行
    privateKey: env('DTW_PRIVATE_KEY', '').replace(/\\n/g, '\n'),
    // 允许放行的明文路径前缀（后台、下载、健康检查、握手本身）。
    // ⚠️ 两类必须放行：
    //   1) `/api/v1/version/check` —— 老客户端没有私有协议，连版本检查都被 426 拒掉的话，
    //      它就永远发现不了「有新版本」，等于把老用户锁死在用不了的旧包里；
    //   2) `/api/v1/feedback/image` —— 后台「意见反馈」列表用 <img src> 直接引用（浏览器不会带
    //      任何头），它本来就刻意放在签名中间件之外、靠文件名随机 hex 防枚举。
    // 这两个都不泄露任何东西。
    plainWhitelist: String(
      env(
        'DTW_PLAIN_WHITELIST',
        '/healthz,/dw/file,/admin,/avatar,/upgrade,/devtest,/api/v1/version/check,/api/v1/feedback/image',
      ),
    )
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
  },

  storage: {
    driver: env('STORAGE_DRIVER', 'local').toLowerCase(),
    localDir: abs(env('STORAGE_LOCAL_DIR', './storage/media'), './storage/media'),
    publicBase: env('STORAGE_PUBLIC_BASE', `http://127.0.0.1:${num('PORT', 7101)}`).replace(/\/+$/, ''),
    urlTtlSec: num('URL_TTL_SEC', 900),
    tmpDir: abs(env('TMPDIR', './storage/tmp'), './storage/tmp'),
    oss: {
      endpoint: env('OSS_ENDPOINT', ''),
      bucket: env('OSS_BUCKET', ''),
      accessKeyId: env('OSS_ACCESS_KEY_ID', ''),
      accessKeySecret: env('OSS_ACCESS_KEY_SECRET', ''),
    },
  },

  media: {
    ffmpeg: env('FFMPEG_PATH', 'ffmpeg'),
    ffprobe: env('FFPROBE_PATH', 'ffprobe'),
    workerConcurrency: Math.max(1, num('WORKER_CONCURRENCY', 3)),
    taskTimeoutSec: num('TASK_TIMEOUT_SEC', 120),
    workerPollMs: num('WORKER_POLL_MS', 1000),
    font: env('WATERMARK_FONT', defaultFont()),
    opacity: num('WATERMARK_OPACITY', 0.42),
    widthRatio: num('WATERMARK_WIDTH_RATIO', 0.2),
    position: env('WATERMARK_POSITION', 'bottom-right'),
    originCacheHours: num('ORIGIN_CACHE_HOURS', 24),
    routeSuccAlert: num('ROUTE_SUCC_ALERT', 0.8),
  },

  parser: {
    base: env('MEDIA_PARSER_BASE', '').replace(/\/+$/, ''),
    key: env('MEDIA_PARSER_KEY', ''),
    timeoutMs: num('MEDIA_PARSER_TIMEOUT_MS', 20000),
  },

  pay: {
    provider: env('PAY_PROVIDER', 'mock').toLowerCase(),
    epay: {
      gateway: env('EPAY_GATEWAY', '').replace(/\/+$/, ''),
      pid: env('EPAY_PID', ''),
      key: env('EPAY_KEY', ''),
      notifyBase: env('EPAY_NOTIFY_BASE', '').replace(/\/+$/, ''),
    },
  },

  alert: {
    webhook: env('ALERT_WEBHOOK', ''),
  },
  backup: {
    dir: abs(env('BACKUP_DIR', './backup'), './backup'),
    retainDays: num('BACKUP_RETAIN_DAYS', 7),
    retainRemoteDays: num('BACKUP_RETAIN_REMOTE_DAYS', 30),
    // 加密口令文件（chmod 600，不进版本库）；也可直接用 BACKUP_PASSWORD
    passFile: env('BACKUP_PASS_FILE', ''),
    password: env('BACKUP_PASSWORD', ''),
    mysqldump: env('MYSQLDUMP_PATH', 'mysqldump'),
  },
};

/** 默认中文字体：Linux 优先思源/文泉驿，Windows 用微软雅黑。 */
function defaultFont() {
  const candidates =
    process.platform === 'win32'
      ? ['C:\\Windows\\Fonts\\msyh.ttc', 'C:\\Windows\\Fonts\\simhei.ttf']
      : [
          '/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',
          '/usr/share/fonts/truetype/wqy/wqy-microhei.ttc',
          '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc',
          '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
        ];
  for (const f of candidates) if (fs.existsSync(f)) return f;
  return '';
}

/**
 * MASTER_KEY 的实际取值。
 * 生产环境必须显式配置（64 位十六进制）；开发环境未配置时回退到一个固定串，
 * 并且**所有**使用方（验签、会话密钥派发）都必须用同一个值 ——
 * 否则客户端按文档公式自算出的 sign_key 会与服务端响应不一致。
 */
export function masterKeyEffective() {
  const key = config.security.masterKey;
  if (key && key !== 'please_replace_with_64_hex_chars') return key;
  if (config.isProd) {
    throw new Error('生产环境必须配置 MASTER_KEY（64 位十六进制随机串，生成：openssl rand -hex 32）');
  }
  return 'dev-master-key';
}

/**
 * 成品下载 URL 的签名密钥。没单独配 STORAGE_SECRET 就回退到 MASTER_KEY
 * （老部署零改动；想隔离就 `openssl rand -hex 32` 写进 .env）。
 *
 * 为什么要分开：这个密钥每天都在签下载链接，一旦因为某个日志/前端泄露，
 * 不该连带把引导接口、协议握手一起送出去。
 */
export function storageSecretEffective() {
  const own = String(config.security.storageSecret || '').trim();
  if (own) return own;
  return masterKeyEffective();
}

/**
 * 老客户端「明文 root 签名」用的密钥（版本检查这类白名单接口还留着这条路）。
 * 同样允许独立配置，默认回退 MASTER_KEY 保持兼容。
 */
export function rootSignKeyEffective() {
  const own = String(config.security.rootSignKey || '').trim();
  if (own) return own;
  return masterKeyEffective();
}

export default config;
