/**
 * 后台业务接口：账号管理 / 卡密 / 订单 / 收益 / 任务溯源 / 客服工单。
 *
 * 全部写操作都要过 adminAuth(perm)，敏感操作（删除用户、作废卡密、退款）
 * 额外要求二次输入密码，并强制写 audit_log（规划书 6.4）。
 */
import { Router } from '../../core/http.js';
import config from '../../config.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { adminAuth, confirmPassword } from './auth.js';
import { audit } from '../../services/audit.js';
import { getDb } from '../../db/index.js';
import { now, pageParams, daysLeft, formatDate } from '../../core/util.js';
import { money } from '../../core/money.js';
import { hashPassword } from '../../core/crypto.js';
import { logger } from '../../core/logger.js';
import * as userService from '../../services/user.js';
import * as inviteService from '../../services/invite.js';
import * as orderService from '../../services/order.js';
import * as revenueService from '../../services/revenue.js';
import * as sessionService from '../../services/session.js';
import * as taskService from '../../modules/dw/taskService.js';
import { routeHealthList } from '../../modules/dw/routeSelector.js';
import { sendAlert } from '../../services/alert.js';

const log = logger.child('admin:business');

export const adminBusinessRouter = new Router({ prefix: `${config.security.adminPath}/api` });

/* ------------------------------------------------------------------ */
/*                            工具                                    */
/* ------------------------------------------------------------------ */

function csvEscape(value) {
  const s = value === null || value === undefined ? '' : String(value);
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/** CSV 导出：Excel 打开中文需要 BOM，否则乱码 */
function csvResponse(ctx, filename, headers, rows) {
  const lines = [headers.join(',')];
  for (const row of rows) lines.push(row.map(csvEscape).join(','));
  const body = Buffer.from(`\uFEFF${lines.join('\r\n')}`, 'utf8');
  ctx.res.writeHead(200, {
    'Content-Type': 'text/csv; charset=utf-8',
    'Content-Disposition': `attachment; filename="${encodeURIComponent(filename)}"`,
    'Content-Length': body.length,
  });
  ctx.sent = true;
  ctx.res.end(body);
}

/* ------------------------------------------------------------------ */
/*                          账号管理                                   */
/* ------------------------------------------------------------------ */

adminBusinessRouter.get('/users', adminAuth('user.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const result = await userService.queryUsers({
    keyword: ctx.query.keyword,
    status: ctx.query.status,
    level: ctx.query.level,
    channel: ctx.query.channel,
    startDate: ctx.query.start_date,
    endDate: ctx.query.end_date,
    expireStart: ctx.query.expire_start,
    expireEnd: ctx.query.expire_end,
    minAmount: ctx.query.min_amount,
    maxAmount: ctx.query.max_amount,
    sort: ctx.query.sort || 'created_at',
    order: ctx.query.order || 'desc',
    page,
    limit,
  });
  const rows = result.rows.map((u) => ({
    ...u,
    total_amount: money(u.total_amount),
    status_effective: userService.effectiveStatus(u),
    status_text: userService.STATUS_TEXT[userService.effectiveStatus(u)],
    days_left: daysLeft(u.expire_at),
    phone_masked: u.phone ? `${String(u.phone).slice(0, 3)}****${String(u.phone).slice(-4)}` : null,
  }));
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { ...result, rows } });
});

adminBusinessRouter.get('/users/export', adminAuth(['user.read', 'user.export']), async (ctx) => {
  const result = await userService.queryUsers({
    keyword: ctx.query.keyword,
    status: ctx.query.status,
    level: ctx.query.level,
    startDate: ctx.query.start_date,
    endDate: ctx.query.end_date,
    sort: ctx.query.sort || 'created_at',
    order: ctx.query.order || 'desc',
    page: 1,
    limit: 50000,
  });
  const headers = ['ID', '用户名', '手机号', '昵称', '备注', '等级', '状态', '开通时间', '到期时间', '设备上限', '每日配额', '累计消费', '累计任务', '注册IP', '渠道', '注册时间'];
  const rows = result.rows.map((u) => [
    u.id,
    u.username,
    u.phone || '',
    u.nickname || '',
    u.remark || '',
    u.level,
    userService.STATUS_TEXT[userService.effectiveStatus(u)],
    u.bind_at || '',
    u.expire_at || '',
    u.max_device,
    u.quota_day,
    money(u.total_amount),
    u.total_task,
    u.reg_ip || '',
    u.reg_channel || '',
    u.created_at,
  ]);
  await audit(ctx, 'user', 'export', null, { count: rows.length, filter: ctx.query });
  csvResponse(ctx, `users-${formatDate()}.csv`, headers, rows);
});

adminBusinessRouter.get('/users/:id', adminAuth('user.read'), async (ctx) => {
  const detail = await userService.userDetail(Number(ctx.params.id));
  const db = getDb();
  const profileLogs = await db.query('SELECT * FROM `user_profile_log` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 30', [
    Number(ctx.params.id),
  ]);
  const sessions = await db.query(
    'SELECT `id`, `device_id`, `ip`, `app_version`, `expire_at`, `revoked`, `last_seen`, `created_at` FROM `license_session` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 20',
    [Number(ctx.params.id)],
  );
  const tickets = await db.query('SELECT `ticket_no`, `title`, `status`, `created_at` FROM `ticket` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 10', [
    Number(ctx.params.id),
  ]);
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      user: { ...detail.user, total_amount: money(detail.user.total_amount), status_effective: userService.effectiveStatus(detail.user) },
      invites: detail.invites,
      cards: detail.cards,
      devices: detail.devices,
      tasks: detail.tasks.map((t) => taskService.taskPayload(t)),
      orders: detail.orders.map((o) => ({ ...o, amount: money(o.amount) })),
      profile_logs: profileLogs,
      sessions,
      tickets,
    },
  });
});

/** POST /users —— 后台添加用户（规划书 6.1 的「添加用户」弹窗） */
adminBusinessRouter.post('/users', adminAuth('user.write'), async (ctx) => {
  const db = getDb();
  const remark = String(ctx.input('remark', '')).trim();
  const phone = String(ctx.input('phone', '')).trim() || null;
  // 后台建号也要守同一套用户名规则：不填就跟手机号同名（同一个账号内不算冲突），
  // 填了就必须过格式校验与忽略大小写的查重，否则会破坏「用户名与手机号命名空间不重叠」的前提。
  const rawUsername = String(ctx.input('username', '')).trim();
  let username = rawUsername || phone;
  if (!username) throw new ApiError(ERROR.PARAM, '手机号（或用户名）必填');
  if (rawUsername) {
    username = userService.validateUsername(rawUsername);
    if (await userService.isUsernameTaken(username)) throw new ApiError(ERROR.PARAM, '该用户名已被使用，请换一个');
    if (rawUsername !== phone && !phone) {
      // 只给用户名、没给手机号：确认这个字符串没被别人当手机号用
      const clash = await db.get('SELECT `id` FROM `app_user` WHERE `phone` = ? LIMIT 1', [username]);
      if (clash) throw new ApiError(ERROR.PARAM, '该用户名已被使用，请换一个');
    }
  }

  const expireAt = ctx.input('expire_at', null);
  const days = Number(ctx.input('days', 0)) || 0;
  if (!expireAt && !days) throw new ApiError(ERROR.PARAM, '请指定到期时间或开通时长');

  const level = Number(ctx.input('level', 1)) || 1;
  const maxDevice = Number(ctx.input('max_device', 1)) || 1;
  // quota_day=0 表示不限量，需保留 0 原值
  const quotaDay = ctx.input('quota_day', 20) === '' ? 20 : Number(ctx.input('quota_day', 20));
  const createOrder = ctx.input('create_pay_order', false) === true || ctx.input('create_pay_order', false) === 'true';

  const result = await db.tx(async (tx) => {
    const { user, created } = await userService.openOrExtend(tx, {
      username,
      phone,
      days: expireAt ? 0 : days,
      level,
      maxDevice,
      quotaDay,
      source: 3,
      regIp: ctx.ip,
      regChannel: String(ctx.input('channel', 'admin')),
      nickname: ctx.input('nickname', null),
    });
    let patch = {};
    if (expireAt) patch.expire_at = String(expireAt).replace('T', ' ').slice(0, 19);
    if (remark) patch.remark = remark.slice(0, 255);
    if (Object.keys(patch).length) {
      patch.updated_at = now();
      await tx.update('app_user', { id: user.id }, patch);
    }
    let order = null;
    if (createOrder) {
      order = await orderService.createOrder({
        userId: user.id,
        goodsId: ctx.input('goods_id', null),
        type: 2,
        payType: String(ctx.input('pay_type', 'alipay')),
        clientIp: ctx.ip,
        remark: `管理员代客下单（${ctx.state.admin.username}）`,
      });
    }
    return { user, created, order: order?.order || null };
  });

  await audit(ctx, 'user', 'create', result.user.id, {
    username,
    phone,
    days,
    expire_at: expireAt,
    level,
    max_device: maxDevice,
    quota_day: quotaDay,
    create_pay_order: createOrder,
  });

  ctx.json({
    code: ERROR.OK,
    msg: '创建成功（账号已直接开通）',
    data: {
      user: { ...result.user, total_amount: money(result.user.total_amount) },
      created: result.created,
      order: result.order ? { order_no: result.order.order_no, amount: money(result.order.amount) } : null,
    },
  });
});

/** PUT /users/:id —— 基础信息修改 */
adminBusinessRouter.put('/users/:id', adminAuth('user.write'), async (ctx) => {
  const db = getDb();
  const id = Number(ctx.params.id);
  const user = await userService.findById(id);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '用户不存在');

  const patch = { updated_at: now() };
  const before = {};
  // 用户名走与客户端同一套逻辑（格式 / 忽略大小写查重 / 手机号命名空间冲突 / 写留痕），
  // 免得后台改出「登录时到底是手机号还是用户名」这种模糊账号。
  const newUsername = ctx.input('username', undefined);
  if (newUsername !== undefined && newUsername !== null && String(newUsername).trim() !== String(user.username || '')) {
    await userService.changeUsername(id, newUsername, { adminId: ctx.state.admin.id });
    before.username = user.username;
  }
  for (const [field, column] of [
    ['remark', 'remark'],
    ['phone', 'phone'],
    ['nickname', 'nickname'],
    ['email', 'email'],
  ]) {
    const value = ctx.input(field, undefined);
    if (value !== undefined && value !== null) {
      before[field] = user[column];
      patch[column] = String(value).slice(0, 200) || null;
    }
  }
  if (ctx.input('level', undefined) !== undefined) patch.level = Number(ctx.input('level'));
  if (ctx.input('max_device', undefined) !== undefined) patch.max_device = Number(ctx.input('max_device'));
  if (ctx.input('quota_day', undefined) !== undefined) patch.quota_day = Number(ctx.input('quota_day'));

  await db.update('app_user', { id }, patch);
  await db.insert('user_profile_log', {
    user_id: id,
    admin_id: ctx.state.admin.id,
    field: Object.keys(before).join(',') || 'basic',
    old_value: JSON.stringify(before).slice(0, 250),
    new_value: JSON.stringify(patch).slice(0, 250),
    created_at: now(),
  });
  await audit(ctx, 'user', 'update', id, { before, after: patch });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: { user: await userService.findById(id) } });
});

/** POST /users/:id/activate —— 后台「管理账号激活」（新模型下唯一的开通方式） */
adminBusinessRouter.post('/users/:id/activate', adminAuth('user.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  const days = Number(ctx.input('days', 30));
  if (!Number.isFinite(days) || days === 0) throw new ApiError(ERROR.PARAM, '开通时长必须是非 0 数字（天）');
  const before = await userService.findById(id);
  if (!before) throw new ApiError(ERROR.NOT_FOUND, '用户不存在');

  const user = await userService.activateUser(id, {
    days,
    level: ctx.input('level', undefined),
    quotaDay: ctx.input('quota_day', undefined),
    maxDevice: ctx.input('max_device', undefined),
    adminId: ctx.state.admin.id,
    remark: ctx.input('remark', ''),
  });
  await audit(ctx, 'user', 'activate', id, {
    days,
    before: { status: before.status, expire_at: before.expire_at },
    after: { status: user.status, expire_at: user.expire_at },
  });
  ctx.json({
    code: ERROR.OK,
    msg: `已激活，到期时间 ${user.expire_at}`,
    data: { user: { ...user, total_amount: money(user.total_amount), activation: userService.authStatusPayload(user) } },
  });
});

/** POST /users/:id/duration —— 管理时长 */
adminBusinessRouter.post('/users/:id/duration', adminAuth('user.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  const days = ctx.input('days', undefined);
  const expireAt = ctx.input('expire_at', undefined);
  if (days === undefined && !expireAt) throw new ApiError(ERROR.PARAM, '请指定时长或到期时间');
  const user = await userService.adjustDuration(id, {
    days,
    expireAt: expireAt ? String(expireAt).replace('T', ' ').slice(0, 19) : null,
    level: ctx.input('level', undefined),
    maxDevice: ctx.input('max_device', undefined),
    quotaDay: ctx.input('quota_day', undefined),
    adminId: ctx.state.admin.id,
  });
  await audit(ctx, 'user', 'update', id, { action: 'duration', days, expire_at: expireAt });
  ctx.json({ code: ERROR.OK, msg: '时长已更新', data: { user } });
});

/** POST /users/:id/status —— 封禁 / 解封 / 标记过期 */
adminBusinessRouter.post('/users/:id/status', adminAuth('user.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  const status = Number(ctx.require('status'));
  if (![0, 1, 2, 3].includes(status)) throw new ApiError(ERROR.PARAM, '状态不合法');
  // 封禁属于敏感操作，要求二次确认
  if (status === 3) await confirmPassword(ctx, ctx.state.admin, ctx.input('password'));
  await userService.setStatus(id, status);
  if (status === 3) await sessionService.revokeByUser(id);
  await audit(ctx, 'user', 'update', id, { action: 'status', status });
  ctx.json({ code: ERROR.OK, msg: '状态已更新', data: { user: await userService.findById(id) } });
});

/** POST /users/:id/reset-password */
adminBusinessRouter.post('/users/:id/reset-password', adminAuth('user.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  const password = String(ctx.input('password', '')).trim();
  if (password.length < 6) throw new ApiError(ERROR.PARAM, '密码至少 6 位');
  await getDb().update('app_user', { id }, { password_hash: hashPassword(password), updated_at: now() });
  await sessionService.revokeByUser(id);
  await audit(ctx, 'user', 'update', id, { action: 'reset_password' });
  ctx.json({ code: ERROR.OK, msg: '密码已重置，该用户需重新登录', data: { password } });
});

/** POST /users/:id/unbind —— 解绑设备 */
adminBusinessRouter.post('/users/:id/unbind', adminAuth(['user.write', 'user.device']), async (ctx) => {
  const id = Number(ctx.params.id);
  const deviceId = String(ctx.require('device_id')).trim().toLowerCase();
  await userService.unbindDevice(id, deviceId);
  await audit(ctx, 'user', 'update', id, { action: 'unbind', device_id: deviceId });
  ctx.json({ code: ERROR.OK, msg: '已解绑', data: null });
});

/** POST /users/:id/revoke —— 强制下线（版本被破解时的止血手段之一） */
adminBusinessRouter.post('/users/:id/revoke', adminAuth('user.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  const count = await sessionService.revokeByUser(id);
  await audit(ctx, 'user', 'update', id, { action: 'revoke_sessions', count });
  ctx.json({ code: ERROR.OK, msg: '已强制下线', data: { revoked: count } });
});

/** DELETE /users/:id —— 删除用户（敏感，二次确认）
 *
 *  删号时**同时释放该账号用过的邀请码**：
 *  邀请码是「一码一账号」，如果删号后码还挂在「已使用」，管理员拿它去重注册会撞上
 *  「该邀请码已被使用」，而邀请码列表里又看不到使用者（账号已删）—— 这就是
 *  「删了号的人重新注册不了」的真正原因。释放后该码可以再建一个账号（可审计）。
 */
adminBusinessRouter.delete('/users/:id', adminAuth('user.delete'), async (ctx) => {
  const id = Number(ctx.params.id);
  await confirmPassword(ctx, ctx.state.admin, ctx.input('password'));
  const user = await userService.findById(id);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '用户不存在');
  const db = getDb();
  await sessionService.revokeByUser(id);
  await db.update('user_device', { user_id: id }, { status: 0 });
  const releasedCodes = await inviteService.releaseInvitesOfUser(id);
  await db.remove('app_user', { id });
  await audit(ctx, 'user', 'delete', id, {
    username: user.username,
    phone: user.phone,
    expire_at: user.expire_at,
    released_invite_codes: releasedCodes,
  });
  log.warn('用户已删除', { id, by: ctx.state.admin.username, releasedCodes });
  ctx.json({
    code: ERROR.OK,
    msg: releasedCodes.length
      ? `已删除；该账号使用的邀请码 ${releasedCodes.join('、')} 已释放为未使用，可用于重新注册`
      : '已删除',
    data: { released_invite_codes: releasedCodes },
  });
});

/** POST /users/batch —— 批量操作：延期 / 打标签 / 封禁 / 解封 */
adminBusinessRouter.post('/users/batch', adminAuth('user.write'), async (ctx) => {
  const ids = (Array.isArray(ctx.input('ids', [])) ? ctx.input('ids', []) : []).map(Number).filter(Boolean);
  const action = String(ctx.require('action'));
  if (!ids.length) throw new ApiError(ERROR.PARAM, '请选择用户');
  if (ids.length > 500) throw new ApiError(ERROR.PARAM, '单次最多处理 500 个用户');

  const db = getDb();
  let affected = 0;
  if (action === 'extend') {
    const days = Number(ctx.require('days'));
    for (const id of ids) {
      await userService.adjustDuration(id, { days, adminId: ctx.state.admin.id });
      affected += 1;
    }
  } else if (action === 'tag') {
    const remark = String(ctx.input('remark', '')).slice(0, 255);
    const result = await db.update('app_user', { id: ids }, { remark, updated_at: now() });
    affected = result.affectedRows;
  } else if (action === 'status') {
    const status = Number(ctx.require('status'));
    const result = await db.update('app_user', { id: ids }, { status, updated_at: now() });
    affected = result.affectedRows;
    if (status === 3) for (const id of ids) await sessionService.revokeByUser(id);
  } else {
    throw new ApiError(ERROR.PARAM, `不支持的批量操作：${action}`);
  }
  await audit(ctx, 'user', 'batch', null, { action, ids, affected });
  ctx.json({ code: ERROR.OK, msg: `已处理 ${affected} 个用户`, data: { affected } });
});

/* ------------------------------------------------------------------ */
/*                          邀请码管理                                  */
/* ------------------------------------------------------------------ */
/* 邀请码取代了原卡密体系：一个邀请码只能创建一个账号。                   */
/* 客户端凭邀请码 + 手机号 + 两次密码自助注册；注册出来默认未激活，        */
/* 是否开通由后台「激活账号」决定。                                     */

adminBusinessRouter.get('/invites/summary', adminAuth('invite.read'), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: await inviteService.inviteSummary() });
});

adminBusinessRouter.get('/invites/batches', adminAuth('invite.read'), async (ctx) => {
  const rows = await inviteService.listBatches();
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { rows } });
});

adminBusinessRouter.get('/invites', adminAuth('invite.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 50);
  const result = await inviteService.listInvites({
    status: ctx.query.status,
    batchNo: ctx.query.batch_no,
    keyword: ctx.query.keyword,
    page,
    limit,
  });
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: { ...result, rows: result.rows.map((r) => ({ ...r, display: inviteService.formatInviteCode(r.code) })) },
  });
});

adminBusinessRouter.post('/invites', adminAuth('invite.write'), async (ctx) => {
  const count = Math.min(100000, Number(ctx.require('count')) || 0);
  const result = await inviteService.createBatch({
    count,
    level: Number(ctx.input('level', 1)),
    // duration=0 表示注册后仍需后台激活（默认）；>0 则注册即开通
    duration: Number(ctx.input('duration', 0)),
    quotaDay: ctx.input('quota_day', 20),
    maxDevice: Number(ctx.input('max_device', 1)),
    expireAt: ctx.input('expire_at', null),
    remark: ctx.input('remark', ''),
    adminId: ctx.state.admin.id,
  });
  await audit(ctx, 'invite', 'create', result.batch_no, {
    count: result.codes.length,
    duration: Number(ctx.input('duration', 0)),
    level: Number(ctx.input('level', 1)),
  });
  ctx.json({
    code: ERROR.OK,
    msg: `已生成 ${result.codes.length} 个邀请码`,
    data: {
      batch_no: result.batch_no,
      codes: result.codes.map(inviteService.formatInviteCode),
      raw_codes: result.codes,
      note: '一个邀请码只能创建一个账号',
    },
  });
});

/** POST /invites/:code/reset —— 把已使用/已作废的邀请码恢复为「未使用」
 *
 *  用途：账号被删掉之后，对方要用同一个邀请码重新注册。
 *  与「作废」相反：作废是让码再也不能用，重置是把名额放回去（一码一账号的规则不变，
 *  这里等于管理员人工回收一次名额），全程写审计日志。
 */
adminBusinessRouter.post('/invites/:code/reset', adminAuth('invite.write'), async (ctx) => {
  const code = String(ctx.params.code || '');
  const result = await inviteService.resetInvite(code, { adminId: ctx.state.admin.id });
  await audit(ctx, 'invite', 'reset', result.code, {
    changed: result.changed,
    previous_status: result.previous_status,
    previous_used_by: result.previous_used_by,
    previous_used_phone: result.previous_used_phone,
  });
  ctx.json({
    code: ERROR.OK,
    msg: result.changed
      ? `邀请码 ${inviteService.formatInviteCode(result.code)} 已重置为未使用，可以再次注册`
      : '该邀请码本来就是未使用状态',
    data: result,
  });
});

adminBusinessRouter.get('/invites/export', adminAuth(['invite.read', 'invite.export']), async (ctx) => {
  const batchNo = ctx.query.batch_no ? String(ctx.query.batch_no) : null;
  const onlyUnused = String(ctx.query.all || '') !== '1';
  const codes = await inviteService.exportCodes({ batchNo, onlyUnused });
  await audit(ctx, 'invite', 'export', batchNo, { count: codes.length, onlyUnused });

  if (String(ctx.query.format || 'txt') === 'csv') {
    csvResponse(ctx, `invites-${batchNo || 'all'}.csv`, ['序号', '邀请码'], codes.map((c, i) => [i + 1, c]));
    return;
  }
  const text = [
    '短视频去水印 · 邀请码',
    `批次：${batchNo || '全部'}    数量：${codes.length}    ${onlyUnused ? '（仅未使用）' : '（含已使用）'}`,
    ''.padEnd(46, '-'),
    ...codes,
    ''.padEnd(46, '-'),
    '使用方法：打开 App → 注册 → 填写邀请码 + 手机号 + 两次密码',
    '注意：一个邀请码只能创建一个账号；注册后如需开通会员，请联系客服在后台激活。',
    '本软件仅限处理您自己拥有版权的素材，请勿用于搬运他人作品。',
  ].join('\r\n');
  const body = Buffer.from(`\uFEFF${text}`, 'utf8');
  ctx.res.writeHead(200, {
    'Content-Type': 'text/plain; charset=utf-8',
    'Content-Disposition': `attachment; filename="${encodeURIComponent(`invites-${batchNo || 'all'}.txt`)}"`,
    'Content-Length': body.length,
  });
  ctx.sent = true;
  ctx.res.end(body);
});

adminBusinessRouter.post('/invites/void', adminAuth('invite.void'), async (ctx) => {
  await confirmPassword(ctx, ctx.state.admin, ctx.input('password'));
  const code = String(ctx.require('code'));
  await inviteService.voidInvite(code);
  await audit(ctx, 'invite', 'void', null, {
    code: `****${inviteService.normalizeInviteCode(code).slice(-4)}`,
    reason: ctx.input('reason', ''),
  });
  ctx.json({ code: ERROR.OK, msg: '已作废', data: null });
});

adminBusinessRouter.post('/invites/batches/:batch_no/void', adminAuth('invite.void'), async (ctx) => {
  await confirmPassword(ctx, ctx.state.admin, ctx.input('password'));
  const batchNo = String(ctx.params.batch_no);
  const result = await inviteService.voidBatch(batchNo);
  await audit(ctx, 'invite', 'void', batchNo, { action: 'void_batch', count: result.affectedRows });
  ctx.json({ code: ERROR.OK, msg: `已作废 ${result.affectedRows} 个未使用邀请码`, data: { count: result.affectedRows } });
});

/* ------------------------------------------------------------------ */
/*                            订单 / 收益                              */
/* ------------------------------------------------------------------ */

adminBusinessRouter.get('/orders', adminAuth('order.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const result = await orderService.listOrders({
    keyword: ctx.query.keyword,
    status: ctx.query.status,
    payType: ctx.query.pay_type,
    channel: ctx.query.channel,
    type: ctx.query.type,
    startDate: ctx.query.start_date,
    endDate: ctx.query.end_date,
    minAmount: ctx.query.min_amount,
    maxAmount: ctx.query.max_amount,
    sort: ctx.query.sort || 'created_at',
    order: ctx.query.order || 'desc',
    page,
    limit,
  });
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      ...result,
      rows: result.rows.map((o) => ({
        ...o,
        amount: money(o.amount),
        origin_amount: money(o.origin_amount),
        fee: money(o.fee),
        net_amount: money(o.net_amount),
        status_text: orderService.ORDER_STATUS_TEXT[Number(o.status)],
      })),
    },
  });
});

adminBusinessRouter.get('/orders/summary', adminAuth('order.read'), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: await orderService.orderSummary() });
});

adminBusinessRouter.get('/orders/export', adminAuth(['order.read', 'order.export']), async (ctx) => {
  const rows = await orderService.exportOrders({
    keyword: ctx.query.keyword,
    status: ctx.query.status,
    payType: ctx.query.pay_type,
    startDate: ctx.query.start_date,
    endDate: ctx.query.end_date,
    sort: 'created_at',
    order: 'desc',
  });
  const headers = ['订单号', '第三方交易号', '用户名', '手机号', '商品', '类型', '时长', '原价', '实付', '手续费', '净收入', '支付方式', '状态', '支付时间', '创建时间', 'IP', '备注'];
  const typeText = { 1: '新购', 2: '续费', 3: '升级' };
  const data = rows.map((o) => [
    o.order_no,
    o.trade_no || '',
    o.username || '',
    o.phone || '',
    o.goods_name || '',
    typeText[Number(o.type)] || '',
    o.duration ?? '',
    money(o.origin_amount),
    money(o.amount),
    money(o.fee),
    money(o.net_amount),
    o.pay_type || '',
    orderService.ORDER_STATUS_TEXT[Number(o.status)],
    o.paid_at || '',
    o.created_at,
    o.client_ip || '',
    o.remark || '',
  ]);
  await audit(ctx, 'order', 'export', null, { count: rows.length });
  csvResponse(ctx, `orders-${formatDate()}.csv`, headers, data);
});

adminBusinessRouter.post('/orders/:order_no/refund', adminAuth('order.refund'), async (ctx) => {
  await confirmPassword(ctx, ctx.state.admin, ctx.input('password'));
  const order = await orderService.refundOrder({
    orderNo: ctx.params.order_no,
    adminId: ctx.state.admin.id,
    reason: String(ctx.input('reason', '')),
  });
  await audit(ctx, 'order', 'refund', ctx.params.order_no, { amount: money(order.amount), reason: ctx.input('reason', '') });
  ctx.json({ code: ERROR.OK, msg: '已标记退款并终止该用户授权', data: { order } });
});

/**
 * 补开会员：订单已支付但用户没开通（或需要补偿）时，直接给该订单用户续期。
 * 旧版这里是「补发卡密」，卡密体系下线后改为直接开通。
 */
adminBusinessRouter.post('/orders/:order_no/reissue', adminAuth('order.write'), async (ctx) => {
  const order = await orderService.findOrder(ctx.params.order_no);
  if (!order) throw new ApiError(ERROR.NOT_FOUND, '订单不存在');
  const user = order.user_id ? await userService.findById(order.user_id) : null;
  if (!user) throw new ApiError(ERROR.PARAM, '该订单没有关联用户，无法补开');
  const days = Number(ctx.input('days', order.duration || 30)) || 30;

  const updated = await userService.activateUser(user.id, {
    days,
    adminId: ctx.state.admin.id,
    remark: `订单 ${order.order_no} 补开`,
  });
  await audit(ctx, 'order', 'reissue', order.order_no, { days, expire_at: updated.expire_at });
  ctx.json({
    code: ERROR.OK,
    msg: `已补开 ${days} 天，到期时间 ${updated.expire_at}`,
    data: { user: { ...updated, total_amount: money(updated.total_amount), activation: userService.authStatusPayload(updated) } },
  });
});

adminBusinessRouter.get('/revenue/dashboard', adminAuth('revenue.read'), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: await revenueService.dashboard() });
});

/** 五档统计：year / quarter / month / week / hour（规划书 6.2） */
adminBusinessRouter.get('/revenue/overview', adminAuth('revenue.read'), async (ctx) => {
  const granularity = String(ctx.query.granularity || 'month');
  if (!['year', 'quarter', 'month', 'week', 'hour'].includes(granularity)) {
    throw new ApiError(ERROR.PARAM, 'granularity 只能是 year/quarter/month/week/hour');
  }
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: await revenueService.overview(granularity, { date: ctx.query.date }),
  });
});

adminBusinessRouter.get('/revenue/daily', adminAuth('revenue.read'), async (ctx) => {
  const from = String(ctx.query.from || formatDate(new Date(Date.now() - 29 * 86400000)));
  const to = String(ctx.query.to || formatDate());
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { from, to, rows: await revenueService.filledDailySeries(from, to) } });
});

adminBusinessRouter.post('/revenue/aggregate', adminAuth('revenue.write'), async (ctx) => {
  const days = Math.min(90, Number(ctx.input('days', 3)) || 3);
  const rows = await revenueService.aggregateRange(days);
  await audit(ctx, 'revenue', 'update', null, { action: 'aggregate', days });
  ctx.json({ code: ERROR.OK, msg: `已补算 ${rows.length} 天`, data: { rows } });
});

adminBusinessRouter.post('/revenue/reconcile', adminAuth('revenue.write'), async (ctx) => {
  const { reconcile } = await import('../pay/routes.js');
  const result = await reconcile(String(ctx.input('date', formatDate())));
  await audit(ctx, 'revenue', 'update', null, { action: 'reconcile', date: ctx.input('date', formatDate()) });
  ctx.json({ code: ERROR.OK, msg: result.status === 0 ? '对账一致' : '存在差异，请查看对账日志', data: result });
});

adminBusinessRouter.get('/revenue/reconcile-logs', adminAuth('revenue.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const db = getDb();
  const total = Number((await db.get('SELECT COUNT(*) AS n FROM `reconcile_log`'))?.n || 0);
  const rows = await db.query('SELECT * FROM `reconcile_log` ORDER BY `id` DESC LIMIT ? OFFSET ?', [limit, (page - 1) * limit]);
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { total, rows, page, limit } });
});

/* ------------------------------------------------------------------ */
/*                        任务 / 线路 / 溯源                            */
/* ------------------------------------------------------------------ */

adminBusinessRouter.get('/tasks', adminAuth('task.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const result = await taskService.adminListTasks({
    keyword: ctx.query.keyword,
    status: ctx.query.status,
    platform: ctx.query.platform,
    startDate: ctx.query.start_date,
    endDate: ctx.query.end_date,
    page,
    limit,
  });
  ctx.json({ code: ERROR.OK, msg: 'ok', data: result });
});

adminBusinessRouter.get('/tasks/queue', adminAuth('task.read'), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: await taskService.queueStats() });
});

/** 侵权投诉溯源：按用户 / item_id / 任务号 / IP 定位到具体成品 */
adminBusinessRouter.get('/tasks/trace', adminAuth('task.trace'), async (ctx) => {
  const rows = await taskService.traceTasks({
    userId: ctx.query.user_id,
    itemId: ctx.query.item_id,
    taskNo: ctx.query.task_no,
    ip: ctx.query.ip,
    limit: ctx.query.limit,
  });
  await audit(ctx, 'task', 'trace', null, { query: ctx.query, count: rows.length });
  // 溯源场景下发较长有效期的链接，便于取证下载（1 小时）
  const storage = await import('../../modules/dw/storage.js');
  const payload = rows.map((t) => ({
    ...taskService.taskPayload(t),
    user_id: t.user_id,
    device_id: t.device_id,
    client_ip: t.client_ip,
    src_url: t.src_url,
    out_key: t.out_key,
    created_at: t.created_at,
    evidence_url: t.out_key ? storage.signedUrl(t.out_key, 3600) : null,
  }));
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { count: payload.length, rows: payload } });
});

adminBusinessRouter.get('/routes', adminAuth('task.read'), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { rows: await routeHealthList() } });
});

adminBusinessRouter.post('/routes', adminAuth('route.write'), async (ctx) => {
  const db = getDb();
  const data = {
    platform: String(ctx.require('platform')),
    name: String(ctx.require('name')),
    type: Number(ctx.input('type', 1)),
    endpoint: String(ctx.input('endpoint', '')),
    config: typeof ctx.input('config', {}) === 'object' ? JSON.stringify(ctx.input('config', {})) : String(ctx.input('config', '{}')),
    priority: Number(ctx.input('priority', 100)),
    enabled: ctx.input('enabled', 1) ? 1 : 0,
    created_at: now(),
    updated_at: now(),
  };
  const result = await db.insert('dw_route', data);
  await audit(ctx, 'config', 'create', result.insertId, { route: data });
  ctx.json({ code: ERROR.OK, msg: '已创建', data: { id: result.insertId } });
});

adminBusinessRouter.put('/routes/:id', adminAuth('route.write'), async (ctx) => {
  const db = getDb();
  const id = Number(ctx.params.id);
  const route = await db.get('SELECT * FROM `dw_route` WHERE `id` = ?', [id]);
  if (!route) throw new ApiError(ERROR.NOT_FOUND, '线路不存在');
  const patch = { updated_at: now() };
  for (const field of ['platform', 'name', 'endpoint']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = String(ctx.input(field));
  }
  if (ctx.input('type', undefined) !== undefined) patch.type = Number(ctx.input('type'));
  if (ctx.input('priority', undefined) !== undefined) patch.priority = Number(ctx.input('priority'));
  if (ctx.input('enabled', undefined) !== undefined) {
    patch.enabled = ctx.input('enabled') === true || ctx.input('enabled') === 1 || ctx.input('enabled') === '1' ? 1 : 0;
  }
  const cfg = ctx.input('config', undefined);
  if (cfg !== undefined) patch.config = typeof cfg === 'object' ? JSON.stringify(cfg) : String(cfg);
  await db.update('dw_route', { id }, patch);
  await audit(ctx, 'config', 'update', id, { before: { enabled: route.enabled, priority: route.priority }, after: patch });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: { route: await db.get('SELECT * FROM `dw_route` WHERE `id` = ?', [id]) } });
});

/* ------------------------------------------------------------------ */
/*                          商品（套餐）管理                            */
/* ------------------------------------------------------------------ */

/** GET /goods —— 后台代客下单/后台添加用户时要能挑商品 */
adminBusinessRouter.get('/goods', adminAuth(['order.read', 'user.write']), async (ctx) => {
  const rows = await getDb().query('SELECT * FROM `goods` ORDER BY `status` DESC, `sort` ASC, `id` ASC');
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      rows: rows.map((g) => ({
        ...g,
        price: money(g.price),
        origin_price: money(g.origin_price),
        duration_text: Number(g.duration) === 0 ? '永久' : `${g.duration} 天`,
      })),
    },
  });
});

adminBusinessRouter.post('/goods', adminAuth('config.write'), async (ctx) => {
  const db = getDb();
  const data = {
    name: String(ctx.require('name')).slice(0, 128),
    duration: Number(ctx.input('duration', 30)),
    level: Number(ctx.input('level', 1)),
    max_device: Number(ctx.input('max_device', 1)),
    quota_day: Number(ctx.input('quota_day', 20)),
    price: money(ctx.require('price')),
    origin_price: money(ctx.input('origin_price', '0')),
    tag: String(ctx.input('tag', '')).slice(0, 16),
    sort: Number(ctx.input('sort', 100)),
    status: ctx.input('status', 1) ? 1 : 0,
    created_at: now(),
    updated_at: now(),
  };
  const result = await db.insert('goods', data);
  await audit(ctx, 'config', 'create', result.insertId, { action: 'create_goods', name: data.name });
  ctx.json({ code: ERROR.OK, msg: '已创建', data: { id: result.insertId } });
});

adminBusinessRouter.put('/goods/:id', adminAuth('config.write'), async (ctx) => {
  const db = getDb();
  const patch = { updated_at: now() };
  for (const field of ['name', 'tag']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = String(ctx.input(field));
  }
  for (const field of ['price', 'origin_price']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = money(ctx.input(field));
  }
  for (const field of ['duration', 'level', 'max_device', 'quota_day', 'sort', 'status']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = Number(ctx.input(field));
  }
  await db.update('goods', { id: Number(ctx.params.id) }, patch);
  await audit(ctx, 'config', 'update', ctx.params.id, { action: 'update_goods', patch });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: null });
});

adminBusinessRouter.delete('/goods/:id', adminAuth('config.write'), async (ctx) => {
  const db = getDb();
  // 已被订单引用的商品只下架不删除，避免历史订单丢失商品信息
  const used = await db.count('order', { goods_id: Number(ctx.params.id) });
  if (used > 0) {
    await db.update('goods', { id: Number(ctx.params.id) }, { status: 0, updated_at: now() });
    await audit(ctx, 'config', 'update', ctx.params.id, { action: 'offline_goods', used });
    ctx.json({ code: ERROR.OK, msg: `该商品已被 ${used} 笔订单引用，已改为下架`, data: { offline: true } });
    return;
  }
  await db.remove('goods', { id: Number(ctx.params.id) });
  await audit(ctx, 'config', 'delete', ctx.params.id, { action: 'delete_goods' });
  ctx.json({ code: ERROR.OK, msg: '已删除', data: null });
});

/* ------------------------------------------------------------------ */
/*                            客服工单                                 */
/* ------------------------------------------------------------------ */

adminBusinessRouter.get('/tickets', adminAuth('ticket.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const db = getDb();
  const where = [];
  const params = [];
  if (ctx.query.status !== undefined && ctx.query.status !== '') {
    where.push('t.`status` = ?');
    params.push(Number(ctx.query.status));
  }
  if (ctx.query.keyword) {
    where.push('(t.`ticket_no` LIKE ? OR t.`title` LIKE ? OR u.`username` LIKE ?)');
    const like = `%${ctx.query.keyword}%`;
    params.push(like, like, like);
  }
  // 数据范围：客服只看自己名下用户
  if (Number(ctx.state.admin.data_scope) === 2 && ctx.query.mine === '1') {
    where.push('t.`admin_id` = ?');
    params.push(ctx.state.admin.id);
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number(
    (await db.get(`SELECT COUNT(*) AS n FROM \`ticket\` t LEFT JOIN \`app_user\` u ON u.id = t.user_id WHERE ${clause}`, params))?.n || 0,
  );
  const rows = await db.query(
    `SELECT t.*, u.\`username\`, u.\`phone\`, u.\`expire_at\`, u.\`level\` FROM \`ticket\` t LEFT JOIN \`app_user\` u ON u.id = t.user_id ` +
      `WHERE ${clause} ORDER BY t.\`status\` ASC, t.\`priority\` ASC, t.\`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { total, rows, page, limit } });
});

adminBusinessRouter.get('/tickets/:ticket_no', adminAuth('ticket.read'), async (ctx) => {
  const db = getDb();
  const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ctx.params.ticket_no]);
  if (!ticket) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');
  const [messages, user, tasks] = await Promise.all([
    db.query('SELECT * FROM `ticket_message` WHERE `ticket_id` = ? ORDER BY `id` ASC', [ticket.id]),
    userService.findById(ticket.user_id),
    db.query('SELECT `task_no`, `platform`, `status`, `err_msg`, `created_at` FROM `dw_task` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 5', [
      ticket.user_id,
    ]),
  ]);
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      ticket: { ...ticket, context: (() => { try { return JSON.parse(ticket.context || '{}'); } catch { return {}; } })() },
      messages,
      user: user ? { ...user, total_amount: money(user.total_amount), status_effective: userService.effectiveStatus(user) } : null,
      recent_tasks: tasks,
    },
  });
});

adminBusinessRouter.post('/tickets/:ticket_no/reply', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const content = String(ctx.input('content', '')).trim();
  if (!content) throw new ApiError(ERROR.PARAM, '回复内容不能为空');
  const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ctx.params.ticket_no]);
  if (!ticket) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');
  const ts = now();
  await db.insert('ticket_message', {
    ticket_id: ticket.id,
    sender_type: 2,
    sender_id: ctx.state.admin.id,
    content,
    attachments: JSON.stringify(Array.isArray(ctx.input('attachments', [])) ? ctx.input('attachments', []) : []),
    created_at: ts,
  });
  await db.update(
    'ticket',
    { id: ticket.id },
    {
      status: 2,
      admin_id: ctx.state.admin.id,
      message_count: Number(ticket.message_count || 0) + 1,
      last_reply_at: ts,
      last_reply_by: 2,
      updated_at: ts,
    },
  );
  await audit(ctx, 'ticket', 'update', ticket.ticket_no, { action: 'reply' });
  ctx.json({ code: ERROR.OK, msg: '已回复', data: null });
});

adminBusinessRouter.post('/tickets/:ticket_no/close', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ctx.params.ticket_no]);
  if (!ticket) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');
  await db.update('ticket', { id: ticket.id }, { status: 3, closed_at: now(), admin_id: ctx.state.admin.id, updated_at: now() });
  await audit(ctx, 'ticket', 'update', ticket.ticket_no, { action: 'close' });
  ctx.json({ code: ERROR.OK, msg: '已关闭', data: null });
});

adminBusinessRouter.post('/tickets/:ticket_no/assign', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ctx.params.ticket_no]);
  if (!ticket) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');
  const adminId = Number(ctx.input('admin_id', ctx.state.admin.id));
  await db.update('ticket', { id: ticket.id }, { admin_id: adminId, status: 1, updated_at: now() });
  await audit(ctx, 'ticket', 'update', ticket.ticket_no, { action: 'assign', admin_id: adminId });
  ctx.json({ code: ERROR.OK, msg: '已指派', data: null });
});

/** PUT /tickets/:ticket_no —— 改标题/分类/优先级/SLA，以及重新打开已关闭的工单 */
adminBusinessRouter.put('/tickets/:ticket_no', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ctx.params.ticket_no]);
  if (!ticket) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');

  const patch = { updated_at: now() };
  if (ctx.input('title', undefined) !== undefined) patch.title = String(ctx.input('title')).slice(0, 128);
  for (const field of ['category', 'priority', 'status']) {
    if (ctx.input(field, undefined) !== undefined) {
      const value = Number(ctx.input(field));
      if (field === 'status' && ![0, 1, 2, 3].includes(value)) throw new ApiError(ERROR.PARAM, '状态不合法');
      if (field === 'category' && ![1, 2, 3, 4].includes(value)) throw new ApiError(ERROR.PARAM, '分类不合法');
      if (field === 'priority' && ![1, 2, 3].includes(value)) throw new ApiError(ERROR.PARAM, '优先级不合法');
      patch[field] = value;
    }
  }
  if (ctx.input('admin_id', undefined) !== undefined) patch.admin_id = Number(ctx.input('admin_id'));
  if (ctx.input('sla_at', undefined) !== undefined) patch.sla_at = ctx.input('sla_at') || null;
  // 重新打开：清掉关闭时间，让工单回到「处理中」
  if (patch.status !== undefined && patch.status !== 3) {
    patch.closed_at = null;
    if (Number(ticket.status) === 3 && patch.status === 0) patch.status = 1;
  }
  if (patch.status === 3) patch.closed_at = now();

  await db.update('ticket', { id: ticket.id }, patch);
  await audit(ctx, 'ticket', 'update', ticket.ticket_no, { action: 'update_ticket', patch });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: { ticket: await db.get('SELECT * FROM `ticket` WHERE `id` = ?', [ticket.id]) } });
});

/** 快捷回复模板（支持 {用户名} {到期时间} {卡密} 变量） */
adminBusinessRouter.get('/quick-replies', adminAuth('ticket.read'), async (ctx) => {
  const rows = await getDb().query('SELECT * FROM `quick_reply` ORDER BY `category` ASC, `sort` ASC');
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { rows } });
});

adminBusinessRouter.post('/quick-replies', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const data = {
    title: String(ctx.require('title')).slice(0, 64),
    content: String(ctx.require('content')),
    category: Number(ctx.input('category', 1)),
    sort: Number(ctx.input('sort', 100)),
    admin_id: ctx.state.admin.id,
    created_at: now(),
    updated_at: now(),
  };
  const result = await db.insert('quick_reply', data);
  await audit(ctx, 'ticket', 'create', result.insertId, { title: data.title });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: { id: result.insertId } });
});

adminBusinessRouter.put('/quick-replies/:id', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const patch = { updated_at: now() };
  for (const field of ['title', 'content']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = String(ctx.input(field));
  }
  if (ctx.input('category', undefined) !== undefined) patch.category = Number(ctx.input('category'));
  if (ctx.input('sort', undefined) !== undefined) patch.sort = Number(ctx.input('sort'));
  await db.update('quick_reply', { id: Number(ctx.params.id) }, patch);
  await audit(ctx, 'ticket', 'update', ctx.params.id, patch);
  ctx.json({ code: ERROR.OK, msg: '已保存', data: null });
});

adminBusinessRouter.delete('/quick-replies/:id', adminAuth('ticket.write'), async (ctx) => {
  await getDb().remove('quick_reply', { id: Number(ctx.params.id) });
  await audit(ctx, 'ticket', 'delete', ctx.params.id, { action: 'delete_quick_reply' });
  ctx.json({ code: ERROR.OK, msg: '已删除', data: null });
});

/* ------------------------------------------------------------------ */
/*                    意见反馈 / 求新功能（App 端提交）                 */
/* ------------------------------------------------------------------ */

/** 图片存储 key → 可以直接放进 <img src> 的公开地址（文件名自带随机段，不可枚举）。 */
function feedbackImageUrl(key) {
  const name = String(key || '').split('/').pop();
  return name ? `/api/v1/feedback/image/${name}` : '';
}

function feedbackRow(row) {
  let images = [];
  try {
    images = JSON.parse(row.images || '[]');
  } catch {
    images = [];
  }
  return {
    ...row,
    status: Number(row.status) || 0,
    images: Array.isArray(images) ? images : [],
    image_urls: (Array.isArray(images) ? images : []).map(feedbackImageUrl).filter(Boolean),
  };
}

adminBusinessRouter.get('/feedback', adminAuth('ticket.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const db = getDb();
  const where = [];
  const params = [];
  if (ctx.query.status !== undefined && ctx.query.status !== '') {
    where.push('f.`status` = ?');
    params.push(Number(ctx.query.status));
  }
  if (ctx.query.keyword) {
    where.push('(f.`content` LIKE ? OR u.`username` LIKE ? OR u.`phone` LIKE ?)');
    const like = `%${ctx.query.keyword}%`;
    params.push(like, like, like);
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number(
    (await db.get(`SELECT COUNT(*) AS n FROM \`feedback\` f LEFT JOIN \`app_user\` u ON u.id = f.user_id WHERE ${clause}`, params))?.n || 0,
  );
  const rows = await db.query(
    `SELECT f.*, u.\`username\`, u.\`phone\` FROM \`feedback\` f LEFT JOIN \`app_user\` u ON u.id = f.user_id ` +
      `WHERE ${clause} ORDER BY f.\`status\` ASC, f.\`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  const pending = Number((await db.get('SELECT COUNT(*) AS n FROM `feedback` WHERE `status` = 0'))?.n || 0);
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { total, pending, rows: rows.map(feedbackRow), page, limit } });
});

adminBusinessRouter.get('/feedback/:id', adminAuth('ticket.read'), async (ctx) => {
  const db = getDb();
  const row = await db.get('SELECT * FROM `feedback` WHERE `id` = ?', [Number(ctx.params.id)]);
  if (!row) throw new ApiError(ERROR.NOT_FOUND, '反馈不存在');
  const user = row.user_id ? await db.get('SELECT `id`, `username`, `phone`, `level`, `expire_at` FROM `app_user` WHERE `id` = ?', [row.user_id]) : null;
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { feedback: feedbackRow(row), user } });
});

/** POST /feedback/:id —— 标记已处理 / 未处理 + 处理备注 */
adminBusinessRouter.post('/feedback/:id', adminAuth('ticket.write'), async (ctx) => {
  const db = getDb();
  const row = await db.get('SELECT * FROM `feedback` WHERE `id` = ?', [Number(ctx.params.id)]);
  if (!row) throw new ApiError(ERROR.NOT_FOUND, '反馈不存在');
  const status = ctx.input('status', undefined) === undefined ? Number(row.status) : Number(ctx.input('status'));
  const patch = {
    status: status === 1 ? 1 : 0,
    remark: ctx.input('remark', undefined) === undefined ? row.remark : String(ctx.input('remark', '') || '').slice(0, 255),
    admin_id: ctx.state.admin.id,
    handled_at: status === 1 ? now() : null,
    updated_at: now(),
  };
  await db.update('feedback', { id: row.id }, patch);
  await audit(ctx, 'feedback', 'update', row.id, { status: patch.status });
  ctx.json({ code: ERROR.OK, msg: patch.status === 1 ? '已标记处理完成' : '已标记为待处理', data: null });
});

/* ------------------------------------------------------------------ */
/*                          风控 / 登录日志                            */
/* ------------------------------------------------------------------ */

adminBusinessRouter.get('/risk-events', adminAuth('risk.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const db = getDb();
  const where = [];
  const params = [];
  if (ctx.query.type) {
    where.push('`type` = ?');
    params.push(ctx.query.type);
  }
  if (ctx.query.handled !== undefined && ctx.query.handled !== '') {
    where.push('`handled` = ?');
    params.push(Number(ctx.query.handled));
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`risk_event\` WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT r.*, u.\`username\` FROM \`risk_event\` r LEFT JOIN \`app_user\` u ON u.id = r.user_id WHERE ${clause} ORDER BY r.\`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { total, rows, page, limit } });
});

adminBusinessRouter.post('/risk-events/:id/handle', adminAuth('risk.write'), async (ctx) => {
  const db = getDb();
  await db.update('risk_event', { id: Number(ctx.params.id) }, { handled: 1 });
  await audit(ctx, 'panel', 'update', ctx.params.id, { action: 'handle_risk' });
  ctx.json({ code: ERROR.OK, msg: '已处理', data: null });
});

adminBusinessRouter.get('/login-logs', adminAuth('audit.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const db = getDb();
  const where = [];
  const params = [];
  if (ctx.query.user_type) {
    where.push('`user_type` = ?');
    params.push(Number(ctx.query.user_type));
  }
  if (ctx.query.status !== undefined && ctx.query.status !== '') {
    where.push('`status` = ?');
    params.push(Number(ctx.query.status));
  }
  if (ctx.query.keyword) {
    where.push('(`username` LIKE ? OR `ip` LIKE ?)');
    params.push(`%${ctx.query.keyword}%`, `%${ctx.query.keyword}%`);
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`login_log\` WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(`SELECT * FROM \`login_log\` WHERE ${clause} ORDER BY \`id\` DESC LIMIT ? OFFSET ?`, [
    ...params,
    limit,
    (page - 1) * limit,
  ]);
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { total, rows, page, limit } });
});

export { csvResponse };
export default adminBusinessRouter;
