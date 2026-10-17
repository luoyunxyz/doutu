/**
 * 后台鉴权与 RBAC（规划书 6.4 面板管理）。
 *
 * 登录安全：
 *  - 登录失败 5 次锁定 15 分钟（login_log 统计，不引入额外依赖）；
 *  - 可选 TOTP 二次验证（admin.totp_secret 有值就必须带 code）；
 *  - token 2 小时过期、空闲 30 分钟自动登出（每次请求滑动续期）；
 *  - 记录 IP 与 UA。
 * 权限：菜单权限（menus）+ 接口权限（perms）+ 数据范围（data_scope）。
 * 敏感操作（删除用户 / 作废卡密 / 退款）必须二次输入密码，见 confirmPassword。
 */
import { getDb } from '../../db/index.js';
import config from '../../config.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { randomHex, verifyPassword, verifyTotp, hashPassword, generateTotpSecret, otpauthUrl } from '../../core/crypto.js';
import { now, formatDateTime, addMinutes, parseDateTime } from '../../core/util.js';
import { logger } from '../../core/logger.js';
import { recentFailures, writeLoginLog } from '../../services/loginLog.js';
import { writeAudit } from '../../services/audit.js';

const log = logger.child('admin:auth');

const TOKEN_HEADER = 'x-admin-token';

/** 权限判定：支持 '*' 全权与 'user.*' 前缀通配 */
export function hasPerm(perms, required) {
  if (!required) return true;
  const list = Array.isArray(perms) ? perms : [];
  if (list.includes('*')) return true;
  if (list.includes(required)) return true;
  const prefix = required.split('.')[0];
  return list.includes(`${prefix}.*`);
}

export function parseJsonArray(text, fallback = []) {
  try {
    const parsed = text ? JSON.parse(text) : fallback;
    return Array.isArray(parsed) ? parsed : fallback;
  } catch {
    return fallback;
  }
}

/** 取角色（role_id 内置角色 + admin_role 附加角色合并权限） */
export async function loadAdminWithRole(admin) {
  const db = getDb();
  const role = await db.get('SELECT * FROM `role` WHERE `id` = ?', [admin.role_id]);
  const extra = await db.query(
    'SELECT r.* FROM `admin_role` ar JOIN `role` r ON r.id = ar.role_id WHERE ar.admin_id = ?',
    [admin.id],
  );
  const menus = new Set(parseJsonArray(role?.menus));
  const perms = new Set(parseJsonArray(role?.perms));
  for (const r of extra) {
    for (const m of parseJsonArray(r.menus)) menus.add(m);
    for (const p of parseJsonArray(r.perms)) perms.add(p);
  }
  return {
    ...admin,
    role_name: role?.name || '未知角色',
    menus: [...menus],
    perms: [...perms],
    data_scope: Number(role?.data_scope || 1),
  };
}

/** POST /admin/api/login 的业务体 */
export async function adminLogin({ username, password, totp, ip, ua }) {
  const db = getDb();
  const failures = await recentFailures({ userType: 2, username, ip, minutes: 15 });
  if (failures >= 5) {
    throw new ApiError(ERROR.RATE_LIMIT, '登录失败次数过多，请 15 分钟后再试');
  }

  const admin = await db.get('SELECT * FROM `admin` WHERE `username` = ?', [String(username || '').trim()]);
  const okPassword = admin ? verifyPassword(admin.password_hash, password) : false;
  if (!okPassword) {
    await writeLoginLog({ userType: 2, username, ip, ua, status: 0, msg: '账号或密码错误' });
    throw new ApiError(ERROR.PARAM, '账号或密码错误');
  }
  if (Number(admin.status) !== 1) {
    await writeLoginLog({ userType: 2, userId: admin.id, username, ip, ua, status: 0, msg: '账号已禁用' });
    throw new ApiError(ERROR.ADMIN_FORBIDDEN, '账号已被禁用');
  }
  if (admin.totp_secret) {
    if (!totp) throw new ApiError(ERROR.ADMIN_TOTP, '请输入动态验证码');
    if (!verifyTotp(admin.totp_secret, totp)) {
      await writeLoginLog({ userType: 2, userId: admin.id, username, ip, ua, status: 0, msg: '动态码错误' });
      throw new ApiError(ERROR.ADMIN_TOTP, '动态验证码错误');
    }
  }

  const token = randomHex(32);
  const ts = now();
  await db.insert('admin_token', {
    admin_id: admin.id,
    token,
    ip: ip || null,
    ua: ua ? String(ua).slice(0, 255) : null,
    expire_at: formatDateTime(addMinutes(new Date(), config.security.adminTokenTtlMin)),
    last_seen: ts,
    revoked: 0,
    created_at: ts,
    updated_at: ts,
  });
  await db.update(
    'admin',
    { id: admin.id },
    { last_login_at: ts, last_login_ip: ip || null, updated_at: ts },
  );
  await writeLoginLog({ userType: 2, userId: admin.id, username, ip, ua, status: 1, msg: '登录成功' });
  await writeAudit({
    adminId: admin.id,
    username: admin.username,
    module: 'panel',
    action: 'login',
    targetId: admin.id,
    detail: { ip, ua },
    ctx: { ip, ua },
  });

  const full = await loadAdminWithRole(admin);
  return { token, expire_at: formatDateTime(addMinutes(new Date(), config.security.adminTokenTtlMin)), admin: publicAdmin(full) };
}

export function publicAdmin(admin) {
  return {
    id: admin.id,
    username: admin.username,
    real_name: admin.real_name,
    role_id: Number(admin.role_id),
    role_name: admin.role_name,
    phone: admin.phone,
    menus: admin.menus || [],
    perms: admin.perms || [],
    data_scope: admin.data_scope ?? 1,
    totp_enabled: Boolean(admin.totp_secret),
    last_login_at: admin.last_login_at,
    last_login_ip: admin.last_login_ip,
  };
}

/** 取当前后台会话（token 校验 + 滑动续期 + 空闲超时） */
export async function resolveAdminToken(ctx) {
  const db = getDb();
  const header = String(ctx.get(TOKEN_HEADER) || '');
  const bearer = String(ctx.get('authorization') || '').replace(/^Bearer\s+/i, '');
  const cookie = String(ctx.get('cookie') || '')
    .split(';')
    .map((s) => s.trim())
    .find((s) => s.startsWith('admin_token='))
    ?.slice('admin_token='.length);
  const token = header || bearer || cookie || '';
  if (!token) return null;

  const row = await db.get('SELECT * FROM `admin_token` WHERE `token` = ?', [token]);
  if (!row || Number(row.revoked) === 1) return null;
  const expire = parseDateTime(row.expire_at);
  if (!expire || expire.getTime() < Date.now()) return null;

  const idleLimitMs = config.security.adminIdleTimeoutMin * 60 * 1000;
  const lastSeen = parseDateTime(row.last_seen);
  if (lastSeen && Date.now() - lastSeen.getTime() > idleLimitMs) {
    await db.update('admin_token', { id: row.id }, { revoked: 1 });
    log.info('后台会话空闲超时', { adminId: row.admin_id });
    return null;
  }

  const admin = await db.get('SELECT * FROM `admin` WHERE `id` = ?', [row.admin_id]);
  if (!admin || Number(admin.status) !== 1) return null;

  const ts = now();
  await db.update('admin_token', { id: row.id }, {
    last_seen: ts,
    expire_at: formatDateTime(addMinutes(new Date(), config.security.adminTokenTtlMin)),
  });

  const full = await loadAdminWithRole(admin);
  return { ...full, token_row_id: row.id, token };
}

/**
 * 后台鉴权中间件工厂。
 * @param {string|string[]|null} perm 需要的权限；数组表示「满足其中之一即可」；null 表示只要登录
 */
export function adminAuth(perm = null) {
  return async function adminAuthMiddleware(ctx, next) {
    const admin = await resolveAdminToken(ctx);
    if (!admin) throw new ApiError(ERROR.ADMIN_AUTH, '登录已失效，请重新登录');
    const required = Array.isArray(perm) ? perm : perm ? [perm] : [];
    if (required.length && !required.some((p) => hasPerm(admin.perms, p))) {
      log.warn('后台越权访问', { adminId: admin.id, perm: required.join('|'), path: ctx.pathname });
      throw new ApiError(ERROR.ADMIN_FORBIDDEN, `没有权限：${required.join(' 或 ')}`);
    }
    ctx.state.admin = admin;
    await next();
  };
}

/** 敏感操作二次确认：必须带上当前账号密码 */
export async function confirmPassword(ctx, admin, password) {
  const value = String(password || ctx.input('confirm_password', ''));
  if (!value) throw new ApiError(ERROR.PARAM, '该操作需要二次输入登录密码');
  const db = getDb();
  const row = await db.get('SELECT `password_hash` FROM `admin` WHERE `id` = ?', [admin.id]);
  if (!row || !verifyPassword(row.password_hash, value)) {
    log.warn('敏感操作二次确认失败', { adminId: admin.id, path: ctx.pathname });
    await writeAudit({
      adminId: admin.id,
      username: admin.username,
      module: 'panel',
      action: 'confirm_failed',
      targetId: admin.id,
      detail: { path: ctx.pathname },
      ctx,
    });
    throw new ApiError(ERROR.ADMIN_FORBIDDEN, '密码错误，操作已取消');
  }
  return true;
}

/** 修改密码 */
export async function changePassword(admin, oldPassword, newPassword) {
  const db = getDb();
  const row = await db.get('SELECT `password_hash` FROM `admin` WHERE `id` = ?', [admin.id]);
  if (!verifyPassword(row.password_hash, oldPassword)) throw new ApiError(ERROR.PARAM, '原密码错误');
  if (String(newPassword).length < 8) throw new ApiError(ERROR.PARAM, '新密码至少 8 位');
  await db.update('admin', { id: admin.id }, { password_hash: hashPassword(newPassword), updated_at: now() });
  // 改密后吊销其他会话
  await db.update('admin_token', { admin_id: admin.id, revoked: 0 }, { revoked: 1 });
  return true;
}

/** TOTP 绑定：返回密钥与 otpauth 链接，确认一次动态码后写入 */
export async function setupTotp(admin) {
  const secret = generateTotpSecret();
  return { secret, otpauth: otpauthUrl(secret, admin.username) };
}

export async function enableTotp(admin, secret, code) {
  if (!verifyTotp(secret, code)) throw new ApiError(ERROR.ADMIN_TOTP, '动态验证码错误');
  await getDb().update('admin', { id: admin.id }, { totp_secret: secret, updated_at: now() });
  return true;
}

export async function disableTotp(admin, password) {
  const db = getDb();
  const row = await db.get('SELECT `password_hash` FROM `admin` WHERE `id` = ?', [admin.id]);
  if (!verifyPassword(row.password_hash, password)) throw new ApiError(ERROR.PARAM, '密码错误');
  await db.update('admin', { id: admin.id }, { totp_secret: null, updated_at: now() });
  return true;
}

export async function logout(tokenRowId) {
  await getDb().update('admin_token', { id: tokenRowId }, { revoked: 1 });
  return true;
}

export default {
  hasPerm,
  adminLogin,
  adminAuth,
  resolveAdminToken,
  confirmPassword,
  changePassword,
  setupTotp,
  enableTotp,
  disableTotp,
  logout,
  publicAdmin,
  loadAdminWithRole,
  parseJsonArray,
};
