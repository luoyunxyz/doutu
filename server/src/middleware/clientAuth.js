/**
 * 客户端请求校验链（规划书 7.2 的 9 步）。
 *
 * 执行顺序（签名必须在拿到密钥之后，所以会话查询前移，其余严格按规划书）：
 *   1. 版本吊销   被 blocklist 命中的 app_version 直接拒绝（VERSION_LOW）
 *   2. 时间窗     |now - timestamp| <= 300s，客户端时钟不准会在这一步全量失败
 *   3. 防重放     Redis/内存 SETNX nonce:{nonce} EX 300
 *   4. 会话       token 有效、未撤销、未过期（root 模式跳过，改用 MASTER_KEY）
 *   5. 签名       HMAC-SHA256(ts+nonce+device_id+path+body_hash, key)
 *   6. 设备       device_id 在 user_device 中且 status=1
 *   7. 授权       user.status 正常且未过期
 *   8. 配额       由业务路由调用 quota.consumeQuota（这里只做前置状态检查）
 *   9. 限流       单 IP / 单设备 / 单用户
 * 任何一环失败 → 拒绝服务。
 */
import config, { rootSignKeyEffective, masterKeyEffective } from '../config.js';
import { ApiError, ERROR } from '../core/errors.js';
import { sha256Hex, verifyRequestSign, normalizeDeviceId } from '../core/crypto.js';
import { kv, keys } from '../core/kv.js';
import { logger } from '../core/logger.js';
import { getJson, getBool, getInt } from '../services/sysconfig.js';
import { hit } from './rateLimit.js';
import * as sessionService from '../services/session.js';
import * as userService from '../services/user.js';

const log = logger.child('auth');

/** 从请求头取设备号 / token，兼容常见的两种命名 */
export const headerToken = (ctx) => ctx.get('x-token') || ctx.get('token') || '';
export const headerDeviceId = (ctx) => normalizeDeviceId(ctx.get('x-device-id') || ctx.get('device-id') || ctx.input('device_id', ''));

/** 版本吊销检查：这一条是最有效的止血手段（规划书 7.3） */
async function checkVersion(ctx) {
  const appVersion = String(ctx.get('x-app-version') || ctx.input('version', '') || '');
  const versionCode = Number.parseInt(String(ctx.get('x-version-code') || ctx.input('version_code', '') || '0'), 10) || 0;
  const blocklist = await getJson('app_version_blocklist', []);
  if (Array.isArray(blocklist) && blocklist.length) {
    const blocked = blocklist.some((entry) => {
      if (entry === null || entry === undefined) return false;
      if (typeof entry === 'number') return entry === versionCode;
      return String(entry) === appVersion || String(entry) === String(versionCode);
    });
    if (blocked) {
      log.warn('拒绝被吊销的客户端版本', { appVersion, versionCode, ip: ctx.ip });
      throw new ApiError(ERROR.VERSION_LOW, '当前版本已停用，请升级到最新版本');
    }
  }
  const minCode = config.security.minSupportedVersionCode;
  if (minCode && versionCode && versionCode < minCode) {
    throw new ApiError(ERROR.VERSION_LOW, '版本过低，请升级后使用');
  }
  ctx.state.appVersion = appVersion || null;
  ctx.state.versionCode = versionCode;
  return true;
}

async function checkTimestamp(ctx) {
  const ts = Number.parseInt(String(ctx.get('x-timestamp') || ctx.input('timestamp', '0')), 10);
  if (!ts) throw new ApiError(ERROR.PARAM, '缺少时间戳');
  const drift = Math.abs(Math.floor(Date.now() / 1000) - ts);
  if (drift > config.security.signWindowSec) {
    throw new ApiError(ERROR.TIMESTAMP, '请求已过期，请校准设备时间后重试', {
      drift,
      window: config.security.signWindowSec,
    });
  }
  ctx.state.timestamp = ts;
  return true;
}

async function checkReplay(ctx) {
  const nonce = String(ctx.get('x-nonce') || ctx.input('nonce', ''));
  if (!nonce || nonce.length < 8) throw new ApiError(ERROR.PARAM, '缺少随机串 nonce');
  const fresh = await kv().setnx(keys.nonce(nonce), '1', config.security.signWindowSec);
  if (!fresh) throw new ApiError(ERROR.TIMESTAMP, '重复请求已被拒绝');
  ctx.state.nonce = nonce;
  return true;
}

/** 会话模式：加载 token 对应的会话与用户 */
async function loadSession(ctx, { requireDevice = true } = {}) {
  const token = String(headerToken(ctx)).trim();
  if (!token) throw new ApiError(ERROR.SESSION_INVALID, '缺少登录凭证');
  const session = await sessionService.findByToken(token);
  if (!session || !sessionService.isSessionValid(session)) {
    throw new ApiError(ERROR.SESSION_INVALID);
  }
  const user = await userService.findById(session.user_id);
  if (!user) throw new ApiError(ERROR.SESSION_INVALID, '账号不存在');

  const deviceId = headerDeviceId(ctx);
  if (requireDevice && deviceId && normalizeDeviceId(session.device_id) !== deviceId) {
    log.warn('会话设备不匹配', { sessionId: session.id, userId: user.id });
    throw new ApiError(ERROR.SESSION_INVALID, '登录设备已变更，请重新登录');
  }
  ctx.state.session = session;
  ctx.state.user = user;
  ctx.state.deviceId = deviceId || session.device_id;
  return session;
}

/** root 模式：老客户端的明文签名（只有版本检查这类白名单接口还走这条路） */
function rootSecret() {
  try {
    // 2026-10-17：与存储签名、协议握手分开 —— 独立配置 ROOT_SIGN_KEY，未配置才回退主密钥
    return rootSignKeyEffective();
  } catch (err) {
    // 生产环境漏配 MASTER_KEY 属于部署事故，必须显式报出来而不是静默放行
    log.error('MASTER_KEY 未配置', { err: err.message });
    throw new ApiError(ERROR.SERVER, '服务端未正确配置 MASTER_KEY');
  }
}

function checkSignature(ctx, secret, legacySecret = null) {
  const sign = String(ctx.get('x-sign') || ctx.input('sign', '')).toLowerCase();
  if (!sign) throw new ApiError(ERROR.SIGN, '缺少签名');
  const bodyHash = sha256Hex(ctx.rawBody || '');
  const parts = {
    method: ctx.method,
    path: ctx.pathname,
    timestamp: ctx.state.timestamp,
    nonce: ctx.state.nonce,
    deviceId: ctx.state.deviceId,
    bodyHash,
  };
  // 换密钥当天的兼容：先认新密钥，再认老的（老链接/老客户端不会集体失效）
  const ok = verifyRequestSign(secret, parts, sign) ||
    (legacySecret && legacySecret !== secret ? verifyRequestSign(legacySecret, parts, sign) : false);
  if (!ok) {
    log.warn('签名校验失败', { path: ctx.pathname, ip: ctx.ip, device: String(ctx.state.deviceId).slice(0, 8) });
    throw new ApiError(ERROR.SIGN);
  }
  ctx.state.bodyHash = bodyHash;
  return true;
}

/** 设备校验：device_id 必须在 user_device 里且未被解绑/封禁 */
async function checkDevice(ctx) {
  const { user, deviceId } = ctx.state;
  if (!deviceId) {
    // 未传设备号时不强制（如 /auth/status 只查授权状态），但会记录用于风控
    ctx.state.deviceUnbound = true;
    return true;
  }
  const allowed = await userService.isDeviceAllowed(user.id, deviceId);
  if (!allowed) {
    log.warn('设备未授权', { userId: user.id, device: String(deviceId).slice(0, 8) });
    throw new ApiError(ERROR.DEVICE_LIMIT, '此设备未授权，请重新激活或联系客服');
  }
  return true;
}

/**
 * 中间件工厂。
 * @param {object} options
 * @param {'session'|'root'|'none'} options.mode 鉴权模式
 * @param {boolean} options.requireActive 是否要求账号处于有效期内
 * @param {boolean} options.requireDevice 是否要求已绑定设备
 * @param {object} options.rate 限流参数
 */
export function clientAuth(options = {}) {
  const {
    mode = 'session',
    requireActive = true,
    requireDevice = true,
    rate = null,
    skipVersionCheck = false,
  } = options;

  return async function clientAuthMiddleware(ctx, next) {
    if (!skipVersionCheck) await checkVersion(ctx);

    // 服务总开关：维护中只放行授权接口，避免用户在维护期把配额耗光
    if (!ctx.pathname.includes('/auth/')) {
      const serviceOn = await getBool('service_switch', true);
      if (!serviceOn) {
        const notice = await getJson('maintenance_notice', '系统维护中');
        throw new ApiError(ERROR.BUSY, String(notice || '系统维护中，请稍后重试'));
      }
    }

    await checkTimestamp(ctx);
    await checkReplay(ctx);

    if (mode === 'session') {
      await loadSession(ctx, { requireDevice });
      checkSignature(ctx, ctx.state.session.sign_key);
      // 响应也要签名，客户端 native 层用 verifyResponse 校验
      ctx.state.signKey = ctx.state.session.sign_key;
      if (requireDevice && !ctx.state.deviceUnbound) await checkDevice(ctx);
    } else if (mode === 'root') {
      ctx.state.deviceId = headerDeviceId(ctx);
      if (ctx.state.dtw) {
        // 走私有协议隧道进来的引导请求：隧道本身已经用会话密钥做过认证（AEAD 标签 + 一次性
        // challenge 换来的会话），客户端**不再持有 MASTER_KEY**，这里也就没必要再要求 root 签名。
        // 安全性没有下降：老客户端本来就带着 MASTER_KEY，而新客户端必须先完成
        // RSA 握手 + 慢哈希证明才能拿到会话。
        ctx.state.signKey = null;
      } else {
        checkSignature(ctx, rootSecret(), masterKeyEffective());
        ctx.state.signKey = rootSecret();
      }
    } else {
      ctx.state.signKey = null;
    }

    if (requireActive && ctx.state.user) {
      userService.assertUsable(ctx.state.user);
    }

    if (rate !== false) {
      const bucket = rate?.bucket || 'api';
      const windowSec = rate?.windowSec || 60;
      if (rate?.ipLimit !== 0) await hit(keys.rateIp(ctx.ip, bucket), rate?.ipLimit ?? 120, windowSec);
      if (ctx.state.deviceId && rate?.deviceLimit) {
        await hit(keys.rateDevice(ctx.state.deviceId, bucket), rate.deviceLimit, windowSec);
      }
      if (ctx.state.user && rate?.userLimit !== 0) {
        await hit(keys.rateUser(ctx.state.user.id, bucket), rate?.userLimit ?? 60, windowSec);
      }
    }

    await next();
  };
}

/**
 * 卡密激活防爆破（规划书 9.5）：同 IP 1 小时内失败 N 次 → 封禁 M 分钟。
 * 返回 { locked } 供路由判断。
 */
export async function activateGuard(ip) {
  const lockKey = keys.activateLock(ip);
  if (await kv().get(lockKey)) {
    const ttl = await kv().ttl(lockKey);
    throw new ApiError(ERROR.RATE_LIMIT, `尝试次数过多，请 ${Math.ceil((ttl > 0 ? ttl : 60) / 60)} 分钟后再试`);
  }
  return { locked: false };
}

export async function recordActivateFailure(ip) {
  const limit = config.security.activateFailLimit;
  const windowSec = 3600;
  const count = await kv().incr(keys.activateFail(ip), windowSec);
  if (count >= limit) {
    // TTL 至少 1 分钟：绝不允许出现「TTL=0 → 永久锁定」这种把自己人也挡在外面的情况
    const lockSec = Math.max(60, Math.round(config.security.activateLockMinutes * 60));
    await kv().set(keys.activateLock(ip), '1', lockSec);
    log.warn('IP 触发激活防爆破锁定', { ip, count, lockMinutes: Math.round(lockSec / 60) });
  }
  return count;
}

export async function clearActivateFailure(ip) {
  await kv().del(keys.activateFail(ip));
}

/**
 * 风控事件（规划书 7.2 第 9 步）：同设备多账号 / 同账号多设备 / 异地秒切。
 * 只做记录与告警；是否自动封禁由 sys_config.risk_ban_enabled 决定。
 */
export async function recordRisk({ type, userId, deviceId, level = 1, detail, ip }) {
  const { getDb } = await import('../db/index.js');
  const { now } = await import('../core/util.js');
  try {
    await getDb().insert('risk_event', {
      user_id: userId || null,
      device_id: deviceId ? String(deviceId).slice(0, 64) : null,
      type,
      level,
      detail: detail ? JSON.stringify(detail) : '',
      ip: ip || null,
      handled: 0,
      created_at: now(),
    });
    if (level >= 2) log.warn('风控命中', { type, userId, level, detail });
  } catch (err) {
    log.error('风控事件写入失败', { err: err.message });
  }
}

/** 是否命中「同设备多账号」：同一 device_id 近期出现过别的 user_id */
export async function detectMultiAccount(deviceId, userId, windowHours = 24) {
  if (!deviceId || !userId) return false;
  const { getDb } = await import('../db/index.js');
  const { formatDateTime, addHours } = await import('../core/util.js');
  const since = formatDateTime(addHours(new Date(), -windowHours));
  const row = await getDb().get(
    'SELECT COUNT(DISTINCT `user_id`) AS n FROM `license_session` WHERE `device_id` = ? AND `created_at` >= ? AND `user_id` <> ?',
    [deviceId, since, userId],
  );
  return Number(row?.n || 0) > 0;
}

export { checkSignature, rootSecret };
export default { clientAuth, activateGuard, recordActivateFailure, clearActivateFailure, recordRisk, detectMultiAccount };
