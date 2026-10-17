/**
 * 邀请码（替代原卡密体系）。
 *
 * 业务规则：**一个邀请码只能创建一个账号**。
 * 与卡密一样用「事务 + 行级锁 + 条件更新影响行数」保证并发下不会一码两号：
 *   1. SELECT ... FOR UPDATE 锁住邀请码（SQLite 下事务本身串行）；
 *   2. 校验 status=0 且未过期；
 *   3. 建账号（status=0 未激活）；
 *   4. UPDATE ... WHERE id=? AND status=0，受影响行数必须为 1，否则整体回滚。
 *
 * 注册出来的账号默认是「未激活」：是否开通由后台决定（后台管理账号激活），
 * 未激活期间客户端会显示斜向水印遮罩。
 */
import { getDb } from '../db/index.js';
import { ApiError, ERROR } from '../core/errors.js';
import { now, randomCode, batchNo, formatCardCode, parseDateTime, formatDateTime, addDays } from '../core/util.js';
import { hashPassword } from '../core/crypto.js';
import { logger } from '../core/logger.js';
import * as userService from './user.js';

const log = logger.child('invite');

/** 邀请码字符集与卡密一致：剔除 0/O/1/I/L 等易混淆字符 */
export const INVITE_LENGTH = 12;

export const normalizeInviteCode = (code) =>
  String(code || '')
    .toUpperCase()
    .replace(/[^0-9A-Z]/g, '');

/** 展示格式：XXXX-XXXX-XXXX */
export const formatInviteCode = (code) => formatCardCode(code);

/* ------------------------------ 生成 ------------------------------ */

export async function createBatch({
  count = 10,
  level = 1,
  duration = 0,
  quotaDay = 20,
  maxDevice = 1,
  expireAt = null,
  remark = '',
  adminId = null,
}) {
  const db = getDb();
  const total = Math.max(1, Math.min(100000, Number(count) || 1));
  const ts = now();
  const todayPrefix = `IV${ts.slice(0, 10).replace(/-/g, '')}`;
  const seqRow = await db.get('SELECT COUNT(*) AS n FROM `invite_code` WHERE `batch_no` LIKE ?', [`${todayPrefix}%`]);
  const batch = batchNo(Number(seqRow?.n || 0) + 1, new Date()).replace(/^CB/, 'IV');

  const codes = [];
  for (let i = 0; i < total; i += 1) {
    let inserted = false;
    for (let attempt = 0; attempt < 6 && !inserted; attempt += 1) {
      const code = randomCode(INVITE_LENGTH);
      try {
        await db.insert('invite_code', {
          code,
          batch_no: batch,
          status: 0,
          level: Number(level) || 1,
          duration: Number(duration) || 0,
          quota_day: quotaDay === undefined || quotaDay === null || quotaDay === '' ? 20 : Number(quotaDay),
          max_device: Number(maxDevice) || 1,
          expire_at: expireAt ? formatDateTime(parseDateTime(expireAt) || new Date()) : null,
          created_by: adminId || null,
          remark: remark || null,
          created_at: ts,
          updated_at: ts,
        });
        codes.push(code);
        inserted = true;
      } catch (err) {
        if (!/UNIQUE|Duplicate|constraint/i.test(String(err?.message || ''))) throw err;
      }
    }
    if (!inserted) throw new ApiError(ERROR.SERVER, '邀请码生成失败，请重试');
  }

  log.info('邀请码批次已生成', { batch, count: codes.length });
  return { batch_no: batch, codes, created_at: ts };
}

/* ------------------------------ 注册核销 ------------------------------ */

/**
 * 用邀请码创建账号（原子）。
 * 返回 { user, invite, created }；失败原因用 ApiError 表达，客户端可直接展示 msg。
 */
export async function registerWithInvite({
  code,
  phone,
  password,
  passwordConfirm,
  username,
  nickname = null,
  ip,
  ua,
  deviceId,
}) {
  const db = getDb();
  const normalized = normalizeInviteCode(code);
  if (normalized.length !== INVITE_LENGTH) throw new ApiError(ERROR.PARAM, '邀请码格式不正确（12 位字母数字）');

  // 用户名是注册必填项，也是登录标识之一（详见 userService.validateUsername）
  const name = userService.validateUsername(username);
  if (!/^\d{6,20}$/.test(String(phone || ''))) throw new ApiError(ERROR.PARAM, '请输入正确的手机号');
  if (String(password || '').length < 6) throw new ApiError(ERROR.PARAM, '密码至少 6 位');
  if (String(password) !== String(passwordConfirm)) throw new ApiError(ERROR.PARAM, '两次输入的密码不一致');

  const exists = await userService.findByPhone(phone);
  if (exists) throw new ApiError(ERROR.PARAM, '该手机号已注册，请直接登录');
  if (await userService.isUsernameTaken(name)) throw new ApiError(ERROR.PARAM, '该用户名已被使用，请换一个');
  // 也不能占用别人当手机号用的字符串（虽然用户名不允许长得像手机号，这里再兜一层）
  const nameAsPhone = await db.get('SELECT `id` FROM `app_user` WHERE `phone` = ? LIMIT 1', [name]);
  if (nameAsPhone) throw new ApiError(ERROR.PARAM, '该用户名已被使用，请换一个');

  // 事务外先看有没有这个码，给出更准确的提示
  const pre = await db.get('SELECT `status`, `expire_at` FROM `invite_code` WHERE `code` = ?', [normalized]);
  if (!pre) throw new ApiError(ERROR.PARAM, '邀请码不存在，请核对后重试');
  if (Number(pre.status) === 1) throw new ApiError(ERROR.PARAM, '该邀请码已被使用');
  if (Number(pre.status) === 2) throw new ApiError(ERROR.PARAM, '该邀请码已作废');
  const preExpire = parseDateTime(pre.expire_at);
  if (preExpire && preExpire.getTime() < Date.now()) throw new ApiError(ERROR.PARAM, '该邀请码已过期');

  const out = await db.tx(async (tx) => {
    const invite = await tx.get(`SELECT * FROM \`invite_code\` WHERE \`code\` = ?${tx.lockSuffix}`, [normalized]);
    if (!invite) throw new ApiError(ERROR.PARAM, '邀请码不存在');
    if (Number(invite.status) !== 0) throw new ApiError(ERROR.PARAM, '该邀请码已被使用');
    const expire = parseDateTime(invite.expire_at);
    if (expire && expire.getTime() < Date.now()) {
      await tx.update('invite_code', { id: invite.id }, { status: 3, updated_at: now() });
      throw new ApiError(ERROR.PARAM, '该邀请码已过期');
    }

    const ts = now();
    // 预设时长 > 0 时注册即开通；= 0 时注册后仍需后台激活
    const presetDays = Number(invite.duration || 0);
    const activated = presetDays > 0;
    const result = await tx.insert('app_user', {
      username: name,
      phone: String(phone),
      password_hash: hashPassword(password),
      nickname: nickname ? String(nickname).slice(0, 64) : null,
      level: Number(invite.level) || 1,
      // 0 未激活 / 1 正常（由邀请码预设天数决定）
      status: activated ? userService.STATUS.NORMAL : userService.STATUS.INACTIVE,
      bind_at: activated ? ts : null,
      expire_at: activated ? formatDateTime(addDays(new Date(), presetDays)) : null,
      max_device: Number(invite.max_device) || 1,
      // quota_day=0 表示不限量，是合法值，不能用 || 顶掉
      quota_day: invite.quota_day === null || invite.quota_day === undefined || invite.quota_day === '' ? 20 : Number(invite.quota_day),
      total_amount: '0.00',
      total_task: 0,
      reg_ip: ip || null,
      reg_channel: 'invite',
      source: 4,
      created_at: ts,
      updated_at: ts,
    });

    const occupied = await tx.update(
      'invite_code',
      { id: invite.id, status: 0 },
      { status: 1, used_by: result.insertId, used_at: ts, used_phone: String(phone), updated_at: ts },
    );
    if (occupied.affectedRows !== 1) throw new ApiError(ERROR.SERVER, '邀请码占用失败，请重试');

    await tx.insert('user_profile_log', {
      user_id: result.insertId,
      admin_id: null,
      field: 'register',
      old_value: null,
      new_value: JSON.stringify({ invite_code: formatInviteCode(normalized), phone: String(phone), username: name, activated }),
      created_at: ts,
    });

    const user = await tx.get('SELECT * FROM `app_user` WHERE `id` = ?', [result.insertId]);
    return { user, invite, activated };
  });

  log.info('邀请码注册成功', {
    userId: out.user.id,
    phone: String(phone).slice(0, 3) + '****',
    activated: out.activated,
    batch: out.invite.batch_no,
  });
  void ua;
  void deviceId;
  return { user: out.user, invite: out.invite, activated: out.activated };
}

/* ------------------------------ 邀请码找回密码 ------------------------------ */

/**
 * 用「注册手机号 + 邀请码」把账号找回来（找回密码第一步的核验）。
 *
 * <p>一个邀请码只能注册一个账号，注册时 `invite_code.used_by / used_phone`
 * 就把两端钉死了，所以这两样凑齐足以证明「这就是那个号的主人」，不用短信验证码。
 *
 * <p>校验顺序和提示都要说人话：码不存在 / 没被用过 / 手机号对不上，分别给不同的说法，
 * 免得用户拿着正确的码却不知道错在哪。
 */
export async function findUserByInviteAndPhone(code, phone) {
  const db = getDb();
  const normalized = normalizeInviteCode(code);
  if (normalized.length !== INVITE_LENGTH) {
    throw new ApiError(ERROR.PARAM, `邀请码格式不正确（${INVITE_LENGTH} 位字母数字）`);
  }
  const inputPhone = String(phone || '').trim();
  if (!/^\d{6,20}$/.test(inputPhone)) throw new ApiError(ERROR.PARAM, '请输入注册时填写的手机号');

  const invite = await db.get(
    'SELECT `code`, `status`, `used_by`, `used_phone`, `used_at` FROM `invite_code` WHERE `code` = ?',
    [normalized],
  );
  if (!invite) throw new ApiError(ERROR.PARAM, '邀请码不存在，请核对后重试');
  if (Number(invite.status) === 2) throw new ApiError(ERROR.PARAM, '该邀请码已作废');
  if (Number(invite.status) !== 1 || !invite.used_by) {
    throw new ApiError(ERROR.PARAM, '这个邀请码还没有注册过账号，请确认是不是注册时用的那一个');
  }

  const user = await db.get('SELECT * FROM `app_user` WHERE `id` = ?', [invite.used_by]);
  if (!user) throw new ApiError(ERROR.PARAM, '邀请码对应的账号不存在，请联系客服');

  // 以邀请码上记的那个手机号为准；老数据没记的话退回账号自己的手机号
  const bound = String(invite.used_phone || '').trim() || String(user.phone || '').trim();
  if (bound !== inputPhone) throw new ApiError(ERROR.PARAM, '手机号与这个邀请码注册的账号对不上');

  return { user, invite };
}

/* ------------------------------ 后台管理 ------------------------------ */

export async function listInvites({ status, batchNo, keyword, page = 1, limit = 50 } = {}) {
  const db = getDb();
  const where = [];
  const params = [];
  if (status !== undefined && status !== null && status !== '') {
    where.push('i.`status` = ?');
    params.push(Number(status));
  }
  if (batchNo) {
    where.push('i.`batch_no` = ?');
    params.push(String(batchNo));
  }
  if (keyword) {
    // 支持按邀请码 / 使用手机号 / 批次号 / 使用人用户名搜索
    where.push('(i.`code` LIKE ? OR i.`used_phone` LIKE ? OR i.`batch_no` LIKE ? OR u.`username` LIKE ?)');
    const like = `%${normalizeInviteCode(keyword) || keyword}%`;
    params.push(like, like, like, `%${keyword}%`);
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number(
    (await db.get(`SELECT COUNT(*) AS n FROM \`invite_code\` i LEFT JOIN \`app_user\` u ON u.id = i.used_by WHERE ${clause}`, params))
      ?.n || 0,
  );
  const rows = await db.query(
    `SELECT i.*, u.\`username\`, u.\`status\` AS user_status, u.\`expire_at\` AS user_expire_at ` +
      `FROM \`invite_code\` i LEFT JOIN \`app_user\` u ON u.id = i.used_by ` +
      `WHERE ${clause} ORDER BY i.\`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows, page, limit };
}

/** 批次维度统计（后台列表用一次 GROUP BY，避免 N+1） */
export async function batchStats(batchNos = []) {
  const map = new Map(batchNos.map((b) => [b, { unused: 0, used: 0, voided: 0, expired: 0, total: 0 }]));
  if (!batchNos.length) return map;
  const rows = await getDb().query(
    `SELECT \`batch_no\`, \`status\`, COUNT(*) AS n FROM \`invite_code\` WHERE \`batch_no\` IN (${batchNos
      .map(() => '?')
      .join(', ')}) GROUP BY \`batch_no\`, \`status\``,
    batchNos,
  );
  for (const row of rows) {
    const bucket = map.get(row.batch_no);
    if (!bucket) continue;
    const n = Number(row.n);
    bucket.total += n;
    if (Number(row.status) === 0) bucket.unused += n;
    else if (Number(row.status) === 1) bucket.used += n;
    else if (Number(row.status) === 2) bucket.voided += n;
    else bucket.expired += n;
  }
  return map;
}

export async function listBatches() {
  const rows = await getDb().query(
    'SELECT `batch_no`, MIN(`created_at`) AS created_at, MAX(`expire_at`) AS expire_at, MAX(`duration`) AS duration, MAX(`level`) AS level ' +
      'FROM `invite_code` GROUP BY `batch_no` ORDER BY `batch_no` DESC',
  );
  const stats = await batchStats(rows.map((r) => r.batch_no));
  return rows.map((r) => ({ ...r, stats: stats.get(r.batch_no) }));
}

export async function voidInvite(code) {
  const db = getDb();
  const row = await db.get('SELECT * FROM `invite_code` WHERE `code` = ?', [normalizeInviteCode(code)]);
  if (!row) throw new ApiError(ERROR.NOT_FOUND, '邀请码不存在');
  if (Number(row.status) === 1) throw new ApiError(ERROR.PARAM, '邀请码已被使用，不能作废');
  await db.update('invite_code', { id: row.id }, { status: 2, updated_at: now() });
  return true;
}

export async function voidBatch(batchNo) {
  return getDb().update('invite_code', { batch_no: batchNo, status: 0 }, { status: 2, updated_at: now() });
}

/**
 * 重置邀请码：把「已使用 / 已作废 / 已过期」的码恢复成**未使用**，清空使用者信息。
 *
 * 典型场景：管理员在后台把某个账号删了，那个人要用**同一个邀请码**重新注册。
 * 「一个邀请码只能创建一个账号」的规则本身不动 —— 重置是管理员显式的一次操作，
 * 等于人工把名额放回去，全程写审计日志。
 */
export async function resetInvite(code, { adminId = null } = {}) {
  const db = getDb();
  const target = await db.get('SELECT * FROM `invite_code` WHERE `code` = ?', [normalizeInviteCode(code)]);
  if (!target) throw new ApiError(ERROR.NOT_FOUND, '邀请码不存在');

  const previous = Number(target.status);
  if (previous === 0) {
    return { code: target.code, changed: false, previous_status: 0, previous_used_by: null };
  }
  await db.update(
    'invite_code',
    { id: target.id },
    { status: 0, used_by: null, used_phone: null, used_at: null, updated_at: now() },
  );
  log.warn('邀请码已重置为未使用', {
    code: target.code,
    previousStatus: previous,
    previousUsedBy: target.used_by,
    previousUsedPhone: target.used_phone,
    adminId,
  });
  return {
    code: target.code,
    changed: true,
    previous_status: previous,
    previous_used_by: target.used_by,
    previous_used_phone: target.used_phone,
  };
}

/**
 * 释放某个账号用过的邀请码（删号时调用）。
 *
 * 删掉账号之后，那个邀请码如果一直挂在「已使用」，管理员再拿它去注册就会撞上
 * 「该邀请码已被使用」，而界面上又看不到使用者（账号已删）—— 正是这个断点让
 * 「删了号的人重新注册不了」。这里在删号的同时把码放回可用状态。
 */
export async function releaseInvitesOfUser(userId) {
  const db = getDb();
  const id = Number(userId);
  if (!id) return [];
  const rows = await db.query('SELECT `id`, `code` FROM `invite_code` WHERE `used_by` = ?', [id]);
  if (!rows.length) return [];
  await db.update(
    'invite_code',
    { used_by: id },
    { status: 0, used_by: null, used_phone: null, used_at: null, updated_at: now() },
  );
  const codes = rows.map((r) => r.code);
  log.warn('删号时释放邀请码', { userId: id, codes });
  return codes;
}

/** 导出未使用的邀请码（发给用户） */
export async function exportCodes({ batchNo, onlyUnused = true } = {}) {
  const db = getDb();
  const where = [];
  const params = [];
  if (batchNo) {
    where.push('`batch_no` = ?');
    params.push(String(batchNo));
  }
  if (onlyUnused) where.push('`status` = 0');
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const rows = await db.query(`SELECT \`code\` FROM \`invite_code\` WHERE ${clause} ORDER BY \`id\` ASC`, params);
  return rows.map((r) => formatInviteCode(r.code));
}

/** 邀请码统计：后台首页/概览用 */
export async function inviteSummary() {
  const db = getDb();
  const row = await db.get(
    'SELECT COUNT(*) AS total, ' +
      'SUM(CASE WHEN `status` = 0 THEN 1 ELSE 0 END) AS unused, ' +
      'SUM(CASE WHEN `status` = 1 THEN 1 ELSE 0 END) AS used ' +
      'FROM `invite_code`',
  );
  return {
    total: Number(row?.total || 0),
    unused: Number(row?.unused || 0),
    used: Number(row?.used || 0),
  };
}

export default {
  INVITE_LENGTH,
  normalizeInviteCode,
  formatInviteCode,
  createBatch,
  registerWithInvite,
  findUserByInviteAndPhone,
  listInvites,
  listBatches,
  batchStats,
  voidInvite,
  voidBatch,
  resetInvite,
  releaseInvitesOfUser,
  exportCodes,
  inviteSummary,
};
