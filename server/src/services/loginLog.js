/**
 * 登录日志（客户端与后台共用一张表，用 user_type 区分）。
 */
import { getDb } from '../db/index.js';
import { now } from '../core/util.js';
import { logger } from '../core/logger.js';

const log = logger.child('loginlog');

export async function writeLoginLog({ userType = 1, userId = null, username = null, ip, ua, deviceId, status = 1, msg }) {
  try {
    await getDb().insert('login_log', {
      user_type: userType,
      user_id: userId,
      username: username ? String(username).slice(0, 64) : null,
      ip: ip || null,
      ua: ua ? String(ua).slice(0, 255) : null,
      device_id: deviceId ? String(deviceId).slice(0, 64) : null,
      status,
      msg: msg ? String(msg).slice(0, 128) : null,
      created_at: now(),
    });
  } catch (err) {
    log.error('登录日志写入失败', { err: err.message });
  }
}

/** 近期失败次数：后台登录失败 5 次锁定 15 分钟的依据 */
export async function recentFailures({ userType, username, ip, minutes = 15 }) {
  const { formatDateTime, addMinutes } = await import('../core/util.js');
  const since = formatDateTime(addMinutes(new Date(), -minutes));
  const row = await getDb().get(
    'SELECT COUNT(*) AS n FROM `login_log` WHERE `user_type` = ? AND `status` = 0 AND `created_at` >= ? AND (`username` = ? OR `ip` = ?)',
    [userType, since, username || '', ip || ''],
  );
  return Number(row?.n || 0);
}

export default { writeLoginLog, recentFailures };
