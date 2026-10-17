/**
 * 后台操作审计（规划书 6.4：删除用户、作废卡密、退款等敏感操作必须留痕）。
 * 审计写入失败不能阻断业务，但要留错误日志 —— 审计缺失本身就是事故。
 */
import { getDb } from '../db/index.js';
import { logger } from '../core/logger.js';
import { now } from '../core/util.js';

const log = logger.child('audit');

/** 敏感字段在写审计前脱敏，避免审计表变成第二个密码库 */
const SENSITIVE_KEYS = new Set(['password', 'password_hash', 'new_password', 'old_password', 'totp_secret', 'sign_key', 'code']);

export function sanitizeSnapshot(obj) {
  if (!obj || typeof obj !== 'object') return obj;
  const out = Array.isArray(obj) ? [] : {};
  for (const [key, value] of Object.entries(obj)) {
    if (SENSITIVE_KEYS.has(key)) {
      out[key] = '***';
    } else if (value && typeof value === 'object') {
      out[key] = sanitizeSnapshot(value);
    } else {
      out[key] = value;
    }
  }
  return out;
}

export async function writeAudit({ adminId, username, module, action, targetId, detail, ctx }) {
  try {
    await getDb().insert('audit_log', {
      admin_id: adminId || 0,
      username: username || null,
      module,
      action,
      target_id: targetId === undefined || targetId === null ? null : String(targetId),
      detail: detail ? JSON.stringify(sanitizeSnapshot(detail)) : null,
      ip: ctx?.ip || null,
      ua: ctx?.ua || null,
      created_at: now(),
    });
  } catch (err) {
    log.error('审计写入失败', { module, action, err: err.message });
  }
}

/** 便捷包装：把 ctx.state.admin 带进审计 */
export const audit = (ctx, module, action, targetId, detail) =>
  writeAudit({
    adminId: ctx?.state?.admin?.id,
    username: ctx?.state?.admin?.username,
    module,
    action,
    targetId,
    detail,
    ctx,
  });

export async function listAudit({ module, adminId, targetId, page = 1, limit = 20 }) {
  const db = getDb();
  const where = [];
  const params = [];
  if (module) {
    where.push('`module` = ?');
    params.push(module);
  }
  if (adminId) {
    where.push('`admin_id` = ?');
    params.push(adminId);
  }
  if (targetId) {
    where.push('`target_id` = ?');
    params.push(String(targetId));
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number(
    (await db.get(`SELECT COUNT(*) AS n FROM \`audit_log\` WHERE ${clause}`, params))?.n || 0,
  );
  const rows = await db.query(
    `SELECT * FROM \`audit_log\` WHERE ${clause} ORDER BY \`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows };
}

export default { writeAudit, audit, listAudit, sanitizeSnapshot };
