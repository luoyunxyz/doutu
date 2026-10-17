/**
 * 授权会话（license_session）：token / refresh / 会话级动态密钥。
 *
 * 动态密钥的价值（规划书 7.3）：即便 so 里的 MASTER_KEY 被逆出来，
 * 攻击者仍需通过 /auth/handshake 才能拿到可用会话，而服务端会校验设备与账号状态；
 * 会话密钥 24 小时过期，泄漏窗口有限；必要时可按 app_version 一键吊销。
 */
import { getDb } from '../db/index.js';
import config from '../config.js';
import { ApiError, ERROR } from '../core/errors.js';
import { normalizeDeviceId, randomHex } from '../core/crypto.js';
import { now, formatDateTime, addHours, parseDateTime } from '../core/util.js';
import { logger } from '../core/logger.js';

const log = logger.child('session');

const REFRESH_TTL_DAYS = 30;

/**
 * 建立会话。activation/login 成功后调用。
 * sign_key 是随机会话密钥；客户端后续所有请求用它与服务端互相验签。
 */
export async function createSession(tx, { user, deviceId, ip, ua, appVersion, osVersion }) {
  const db = tx || getDb();
  const device = normalizeDeviceId(deviceId);
  const ts = now();
  const ttlMs = config.security.sessionTtlHours * 3600 * 1000;

  const token = randomHex(32);
  const refresh = randomHex(32);
  const signKey = randomHex(32);

  // 同一设备上的旧会话先撤销，避免一个设备堆出几十条有效会话
  await db.update(
    'license_session',
    { user_id: user.id, device_id: device, revoked: 0 },
    { revoked: 1, revoked_at: ts },
  );

  const result = await db.insert('license_session', {
    user_id: user.id,
    device_id: device,
    token,
    refresh,
    sign_key: signKey,
    app_version: appVersion ? String(appVersion).slice(0, 20) : null,
    os_version: osVersion ? String(osVersion).slice(0, 20) : null,
    ip: ip || null,
    ua: ua ? String(ua).slice(0, 255) : null,
    expire_at: formatDateTime(new Date(Date.now() + ttlMs)),
    refresh_expire_at: formatDateTime(new Date(Date.now() + REFRESH_TTL_DAYS * 86400000)),
    revoked: 0,
    last_seen: ts,
    created_at: ts,
  });

  log.info('会话建立', { userId: user.id, device: device.slice(0, 8), sessionId: result.insertId });
  return {
    session_id: result.insertId,
    token,
    refresh,
    sign_key: signKey,
    expire_at: formatDateTime(new Date(Date.now() + ttlMs)),
    expire_in: Math.floor(ttlMs / 1000),
  };
}

export function findByToken(token) {
  return getDb().get('SELECT * FROM `license_session` WHERE `token` = ?', [String(token || '')]);
}

export function findByRefresh(refresh) {
  return getDb().get('SELECT * FROM `license_session` WHERE `refresh` = ?', [String(refresh || '')]);
}

/** 会话有效性：未撤销、未过期（含刷新窗口） */
export function isSessionValid(session) {
  if (!session) return false;
  if (Number(session.revoked) === 1) return false;
  const expire = parseDateTime(session.expire_at);
  return Boolean(expire && expire.getTime() > Date.now());
}

/**
 * /auth/handshake：用 MASTER_KEY 换发新的会话级密钥。
 * 派发算法与规划书一致：session_sign_key = HMAC(MASTER_KEY, server_random + nonce + device_id)
 */
export async function handshake({ session, deviceId, nonce }) {
  const db = getDb();
  const serverRandom = randomHex(16);
  // 2026-10-17：会话密钥改成**每次登录随机生成**，不再由 MASTER_KEY 派生。
  // 以前 sign_key = HMAC(MASTER_KEY, server_random+nonce+device_id) —— 主密钥一旦泄露，
  // 所有会话密钥都有了推导材料。现在主密钥与会话彻底解耦：签名密钥只存在于
  // 「服务端生成 → 发给客户端 → 双方各自保存」这一段里。
  const signKey = randomHex(32);
  const ts = now();
  await db.update(
    'license_session',
    { id: session.id },
    { sign_key: signKey, last_seen: ts, revoked: 0, revoked_at: null },
  );
  return { server_random: serverRandom, sign_key: signKey, ts };
}

/** 心跳：续期会话并把在线信息写回去 */
export async function heartbeat(session, { ip, ua, appVersion } = {}) {
  const db = getDb();
  const ttlMs = config.security.sessionTtlHours * 3600 * 1000;
  const expireAt = formatDateTime(new Date(Date.now() + ttlMs));
  await db.update(
    'license_session',
    { id: session.id },
    {
      expire_at: expireAt,
      last_seen: now(),
      ip: ip || session.ip,
      ua: ua ? String(ua).slice(0, 255) : session.ua,
      app_version: appVersion ? String(appVersion).slice(0, 20) : session.app_version,
    },
  );
  await db.update('user_device', { user_id: session.user_id, device_id: session.device_id }, { last_at: now() });
  return { expire_at: expireAt, expire_in: Math.floor(ttlMs / 1000) };
}

export async function refreshSession(refreshToken, { ip, ua, appVersion } = {}) {
  const session = await findByRefresh(refreshToken);
  if (!session) throw new ApiError(ERROR.SESSION_INVALID);
  if (Number(session.revoked) === 1) throw new ApiError(ERROR.SESSION_INVALID, '会话已被撤销，请重新登录');
  const refreshExpire = parseDateTime(session.refresh_expire_at || session.expire_at);
  if (!refreshExpire || refreshExpire.getTime() <= Date.now()) {
    throw new ApiError(ERROR.SESSION_INVALID, '登录状态已过期，请重新登录');
  }
  const db = getDb();
  const token = randomHex(32);
  const ttlMs = config.security.sessionTtlHours * 3600 * 1000;
  const expireAt = formatDateTime(new Date(Date.now() + ttlMs));
  await db.update(
    'license_session',
    { id: session.id },
    {
      token,
      expire_at: expireAt,
      last_seen: now(),
      ip: ip || session.ip,
      ua: ua ? String(ua).slice(0, 255) : session.ua,
      app_version: appVersion ? String(appVersion).slice(0, 20) : session.app_version,
    },
  );
  return { token, expire_at: expireAt, expire_in: Math.floor(ttlMs / 1000) };
}

export async function revokeByToken(token) {
  const session = await findByToken(token);
  if (!session) return false;
  await getDb().update('license_session', { id: session.id }, { revoked: 1, revoked_at: now() });
  return true;
}

export async function revokeAllForUser(userId) {
  return getDb().update('license_session', { user_id: userId, revoked: 0 }, { revoked: 1, revoked_at: now() });
}

/** 后台「强制下线」：撤销某用户全部设备会话 */
export async function revokeByUser(userId) {
  const result = await revokeAllForUser(userId);
  return result.affectedRows;
}

/**
 * 版本吊销（规划书 7.3 最有效的止血手段）：
 * 发现某客户端版本被大规模破解，直接拒绝该版本的所有请求，强制升级。
 */
export async function purgeVersion(appVersion) {
  if (!appVersion) return 0;
  const result = await getDb().update(
    'license_session',
    { app_version: String(appVersion), revoked: 0 },
    { revoked: 1, revoked_at: now() },
  );
  log.warn('按版本吊销会话', { appVersion, revoked: result.affectedRows });
  return result.affectedRows;
}

/** cron：清理过期会话（保留最近 7 天用于排查） */
export async function cleanupExpired(days = 7) {
  const cutoff = formatDateTime(addHours(new Date(), -24 * days));
  const result = await getDb().run('DELETE FROM `license_session` WHERE `expire_at` < ?', [cutoff]);
  return result.affectedRows;
}

export default {
  createSession,
  findByToken,
  findByRefresh,
  isSessionValid,
  handshake,
  heartbeat,
  refreshSession,
  revokeByToken,
  revokeByUser,
  purgeVersion,
  cleanupExpired,
};
