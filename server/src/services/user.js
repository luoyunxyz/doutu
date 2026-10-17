/**
 * 用户域：状态判定、设备绑定、配额、到期扫描。
 *
 * 状态语义（规划书 4.2 app_user.status）：
 *   0 未激活 / 1 正常 / 2 已过期 / 3 封禁
 * 「已过期」是**标记**而不是删除用户 —— 续费后要能恢复（规划书附则第 9 条）。
 */
import { getDb } from '../db/index.js';
import { ApiError, ERROR } from '../core/errors.js';
import { hashPassword } from '../core/crypto.js';
import { now, daysLeft, PERMANENT_EXPIRE, parseDateTime, formatDateTime, addDays } from '../core/util.js';
import { logger } from '../core/logger.js';

const log = logger.child('user');

export const STATUS = { INACTIVE: 0, NORMAL: 1, EXPIRED: 2, BANNED: 3 };
export const STATUS_TEXT = { 0: '未开通会员', 1: '正常', 2: '已过期', 3: '已封禁' };

/* --------------------------- 用户名（登录标识） --------------------------- */

/**
 * 用户名规则：2~20 位，中英文、数字、下划线、短横线。
 * **不允许长得像手机号**（11 位、1[3-9] 开头）——因为登录框同时接受手机号与用户名，
 * 两个命名空间必须互不重叠，否则「这个名字到底是谁」无法判定。
 */
export const USERNAME_MIN = 2;
export const USERNAME_MAX = 20;
const USERNAME_RE = /^[\u4e00-\u9fa5A-Za-z0-9_-]{2,20}$/;
const PHONE_SHAPE_RE = /^1[3-9]\d{9}$/;

/** 校验用户名，返回规范化后的值（首尾空白已去掉）；不合法直接抛 1001。 */
export function validateUsername(input, { field = '用户名' } = {}) {
  const name = String(input == null ? '' : input).trim();
  if (!name) throw new ApiError(ERROR.PARAM, `请输入${field}`);
  if (name.length < USERNAME_MIN || name.length > USERNAME_MAX) {
    throw new ApiError(ERROR.PARAM, `${field}长度需为 ${USERNAME_MIN}~${USERNAME_MAX} 位`);
  }
  if (!USERNAME_RE.test(name)) {
    throw new ApiError(ERROR.PARAM, `${field}只能包含中文、字母、数字、下划线或短横线`);
  }
  if (PHONE_SHAPE_RE.test(name)) {
    throw new ApiError(ERROR.PARAM, `${field}不能是手机号，请换一个`);
  }
  return name;
}

/**
 * 用户名是否被占用（**忽略大小写**：Admin 与 admin 视为同一个名字）。
 * @param {string} username
 * @param {number|null} excludeUserId 改用户名时排除自己
 */
export async function isUsernameTaken(username, excludeUserId = null) {
  const name = String(username || '').trim();
  if (!name) return false;
  const row = await getDb().get(
    'SELECT `id` FROM `app_user` WHERE LOWER(`username`) = LOWER(?) AND `id` <> ? LIMIT 1',
    [name, Number(excludeUserId) || 0],
  );
  return Boolean(row);
}

/** 手机号是否已被占用（同样排除自己）。 */export async function isPhoneTaken(phone, excludeUserId = null) {
  const value = String(phone || '').trim();
  if (!value) return false;
  const row = await getDb().get(
    'SELECT `id` FROM `app_user` WHERE `phone` = ? AND `id` <> ? LIMIT 1',
    [value, Number(excludeUserId) || 0],
  );
  return Boolean(row);
}

/**
 * 改用户名（客户端自助，或后台代改）。
 * 会写 `user_profile_log` 留痕；改名后**手机号登录不受影响**，新旧用户名立即可用于登录。
 */
export async function changeUsername(userId, newUsername, { adminId = null, nickname } = {}) {
  const user = await findById(userId);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '账号不存在');
  const name = validateUsername(newUsername);
  if (String(user.username || '').toLowerCase() === name.toLowerCase()) {
    throw new ApiError(ERROR.PARAM, '新用户名与当前用户名相同');
  }
  if (await isUsernameTaken(name, userId)) throw new ApiError(ERROR.PARAM, '该用户名已被使用，请换一个');
  // 手机号的命名空间也要让开：不能改成一个已被别人当作手机号的字符串
  const phoneClash = await getDb().get('SELECT `id` FROM `app_user` WHERE `phone` = ? AND `id` <> ? LIMIT 1', [name, userId]);
  if (phoneClash) throw new ApiError(ERROR.PARAM, '该用户名已被使用，请换一个');

  const ts = now();
  const patch = { username: name, updated_at: ts };
  const nick = nickname == null ? '' : String(nickname).trim();
  if (nick && nick !== String(user.nickname || '')) patch.nickname = nick.slice(0, 64);
  await getDb().update('app_user', { id: userId }, patch);
  await getDb().insert('user_profile_log', {
    user_id: userId,
    admin_id: adminId || null,
    field: 'username',
    old_value: String(user.username || ''),
    new_value: name,
    created_at: ts,
  });
  log.info('用户名已修改', { userId, from: user.username, to: name, adminId: adminId || null });
  return findById(userId);
}

/**
 * 直接换掉某个账号的密码（邀请码找回密码用）。
 *
 * <p>密码是单向哈希，没法「验证旧密码再改」—— 找回这条路的合法性由调用方保证
 * （邀请码 + 注册手机号核验通过），所以这里只负责写新哈希 + 留一条改密记录。
 * 调用方还要顺手把旧会话全部吊销，否则旧密码虽然不能登录了，已经登录的会话还在。
 */
export async function setPassword(userId, password, { adminId = null, reason = 'invite_reset' } = {}) {
  const db = getDb();
  const user = await findById(userId);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '账号不存在');
  if (String(password || '').length < 6) throw new ApiError(ERROR.PARAM, '密码至少 6 位');

  const ts = now();
  await db.update('app_user', { id: userId }, { password_hash: hashPassword(password), updated_at: ts });
  await db.insert('user_profile_log', {
    user_id: userId,
    admin_id: adminId || null,
    field: 'password',
    old_value: null,
    new_value: JSON.stringify({ reason, at: ts }),
    created_at: ts,
  });
  log.info('密码已重置', { userId, reason, adminId: adminId || null });
  return true;
}

/** 计算用户「当下真实」的状态：库里 status=1 但已过期时返回 2。 */
export function effectiveStatus(user, at = new Date()) {
  if (!user) return STATUS.INACTIVE;
  if (Number(user.status) === STATUS.BANNED) return STATUS.BANNED;
  if (Number(user.status) === STATUS.INACTIVE) return STATUS.INACTIVE;
  const expire = parseDateTime(user.expire_at);
  if (!expire) return STATUS.INACTIVE;
  if (expire.getTime() <= at.getTime()) return STATUS.EXPIRED;
  return STATUS.NORMAL;
}

/**
 * 授权校验。
 *
 * 会员模型（2026-10 调整）：**开通会员与否只决定「有没有水印」，不决定能不能用**。
 * 因此未激活(0)与已过期(2)的账号都可以正常调用业务接口（解析、任务、历史），
 * 客户端会在这两种状态下叠加水印遮罩，用户可以选择不开会员继续用。
 * 只有封禁(3)才拒绝服务。
 */
export function assertUsable(user) {
  const status = effectiveStatus(user);
  if (status === STATUS.BANNED) throw new ApiError(ERROR.BANNED);
  return true;
}

/** 用户对象 → 下发给客户端的授权信息 */
export function authStatusPayload(user, extra = {}) {
  const status = effectiveStatus(user);
  const activated = status === STATUS.NORMAL;
  return {
    user_id: user.id,
    username: user.username,
    nickname: user.nickname || user.username,
    // 手机号也下发（是用户自己的资料，客户端要显示在「我的」页）
    phone: user.phone || null,
    // 头像：客户端拼上自己的 base_url 去取（公开只读路由 /avatar/:id，带版本号防缓存）
    avatar_url: user.avatar ? `/avatar/${user.id}?v=${String(user.updated_at || '').replace(/\D/g, '').slice(0, 14)}` : null,
    level: Number(user.level),
    status,
    status_text: STATUS_TEXT[status],
    // activated 是客户端决定「是否显示未激活水印遮罩」的唯一依据
    activated,
    bind_at: user.bind_at,
    expire_at: user.expire_at,
    expire_days: daysLeft(user.expire_at),
    permanent: String(user.expire_at || '').startsWith('2099-'),
    max_device: Number(user.max_device),
    quota_day: Number(user.quota_day),
    total_task: Number(user.total_task || 0),
    ...extra,
  };
}

/**
 * 后台「管理账号激活」：把账号置为已激活并设置到期时间。
 * 这是新模型下唯一的开通方式（客户端不再有卡密激活入口）。
 */
export async function activateUser(userId, { days = 30, level, quotaDay, maxDevice, adminId, remark } = {}) {
  const db = getDb();
  const user = await findById(userId);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '用户不存在');
  if (Number(user.status) === STATUS.BANNED) throw new ApiError(ERROR.BANNED, '账号已被封禁，请先解封');

  const ts = now();
  const patch = {
    status: STATUS.NORMAL,
    bind_at: user.bind_at || ts,
    expire_at: extendExpire(user.expire_at, Number(days)),
    updated_at: ts,
  };
  if (level !== undefined && level !== null && level !== '') patch.level = Number(level);
  if (quotaDay !== undefined && quotaDay !== null && quotaDay !== '') patch.quota_day = Number(quotaDay);
  if (maxDevice !== undefined && maxDevice !== null && maxDevice !== '') patch.max_device = Number(maxDevice);

  await db.update('app_user', { id: userId }, patch);
  await db.insert('user_profile_log', {
    user_id: userId,
    admin_id: adminId || null,
    field: 'activate',
    old_value: JSON.stringify({ status: user.status, expire_at: user.expire_at }),
    new_value: JSON.stringify({ status: patch.status, expire_at: patch.expire_at, days, remark: remark || '' }),
    created_at: ts,
  });
  return findById(userId);
}

/* ------------------------------ 头像 ------------------------------ */

/** 从文件头识别图片类型；不是这几种就拒绝（避免把任意文件当头像存下来）。 */
export function sniffImage(buffer) {
  if (!buffer || buffer.length < 12) return null;
  if (buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff) return { ext: 'jpg', mime: 'image/jpeg' };
  if (buffer[0] === 0x89 && buffer[1] === 0x50 && buffer[2] === 0x4e && buffer[3] === 0x47) return { ext: 'png', mime: 'image/png' };
  const riff = buffer.toString('latin1', 0, 4) === 'RIFF' && buffer.toString('latin1', 8, 12) === 'WEBP';
  if (riff) return { ext: 'webp', mime: 'image/webp' };
  if (buffer[0] === 0x47 && buffer[1] === 0x49 && buffer[2] === 0x46) return { ext: 'gif', mime: 'image/gif' };
  return null;
}

/** 头像大小限制（客户端已经压到 320px 再传，这里只是兜底）。 */
export const AVATAR_MAX_BYTES = 1024 * 1024;
// 下限只用来挡「空的 / 截断的」数据：一张合法的 1x1 PNG 本身就只有 ~69 字节，
// 真正的格式校验靠 sniffImage 的文件头识别（它自己也会拒掉 < 12 字节的输入）。
export const AVATAR_MIN_BYTES = 24;

/**
 * 换头像：`avatarKey` 传 null 表示清空。
 * 旧图会被删掉（换一次不残留一份），并写 `user_profile_log` 留痕。
 */
export async function setAvatar(userId, avatarKey, { adminId = null, removeFile = null } = {}) {
  const user = await findById(userId);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '账号不存在');
  const ts = now();
  await getDb().update('app_user', { id: userId }, { avatar: avatarKey || null, updated_at: ts });
  await getDb().insert('user_profile_log', {
    user_id: userId,
    admin_id: adminId || null,
    field: 'avatar',
    old_value: user.avatar || null,
    new_value: avatarKey || null,
    created_at: ts,
  });
  if (removeFile && user.avatar && user.avatar !== avatarKey) {
    await removeFile(user.avatar).catch(() => {});
  }
  return findById(userId);
}

/* ------------------------------ 查询 ------------------------------ */

export const findById = (id) => getDb().get('SELECT * FROM `app_user` WHERE `id` = ?', [id]);
/** 按用户名查（忽略大小写，与唯一性校验保持一致）。 */
export const findByUsername = (username) =>
  getDb().get('SELECT * FROM `app_user` WHERE LOWER(`username`) = LOWER(?) LIMIT 1', [String(username || '').trim()]);
export const findByPhone = (phone) =>
  getDb().get('SELECT * FROM `app_user` WHERE `phone` = ?', [String(phone || '').trim()]);
export const findByDevice = (deviceId) =>
  getDb().get(
    'SELECT u.* FROM `user_device` d JOIN `app_user` u ON u.`id` = d.`user_id` ' +
      'WHERE d.`device_id` = ? AND d.`status` = 1 ORDER BY d.`id` DESC LIMIT 1',
    [deviceId],
  );

/**
 * 登录用：**手机号或用户名都可以**。
 * 因为用户名不允许长得像手机号（见 validateUsername），两个命名空间不会重叠，
 * 所以这里先按用户名查、再按手机号查，不会出现「同一个输入命中两个人」的情况。
 */
export async function findByLogin(identifier) {
  const key = String(identifier || '').trim();
  if (!key) return null;
  return (await findByUsername(key)) || (await findByPhone(key));
}

/* --------------------------- 开通 / 续费 --------------------------- */

/** 到期时间累加：永久卡直接给哨兵值；否则从 MAX(现在, 原到期时间) 起加天数。 */
export function extendExpire(currentExpire, days) {
  if (!days || Number(days) === 0) return PERMANENT_EXPIRE;
  const base = parseDateTime(currentExpire);
  const start = base && base.getTime() > Date.now() ? base : new Date();
  return formatDateTime(addDays(start, Number(days)));
}

/**
 * 开通或续费（必须在事务里调用，tx 由调用方传入）。
 * 返回 { user, created }
 */
export async function openOrExtend(tx, { username, phone, days, level, maxDevice, quotaDay, source = 1, regIp, regChannel, nickname }) {
  const db = tx || getDb();
  const key = String(phone || username || '').trim();
  let user = phone ? await db.get('SELECT * FROM `app_user` WHERE `phone` = ?', [phone]) : null;
  // 用户名查库同样忽略大小写，避免后台「按用户名开通」时把 Admin 和 admin 当成两个人
  if (!user && username) user = await db.get('SELECT * FROM `app_user` WHERE LOWER(`username`) = LOWER(?) LIMIT 1', [username]);

  const ts = now();
  // 注意：quota_day = 0 是「不限量」的合法取值，不能用 `|| 20` 把它顶掉
  const quotaNew = quotaDay === undefined || quotaDay === null || quotaDay === '' ? 20 : Number(quotaDay);
  if (!user) {
    const result = await db.insert('app_user', {
      username: username || key,
      phone: phone || null,
      nickname: nickname || null,
      level: level || 1,
      status: STATUS.NORMAL,
      bind_at: ts,
      expire_at: extendExpire(null, days),
      max_device: maxDevice || 1,
      quota_day: quotaNew,
      total_amount: '0.00',
      total_task: 0,
      reg_ip: regIp || null,
      reg_channel: regChannel || null,
      source,
      created_at: ts,
      updated_at: ts,
    });
    user = await db.get('SELECT * FROM `app_user` WHERE `id` = ?', [result.insertId]);
    return { user, created: true };
  }

  // 老用户：封禁状态直接拒绝激活
  if (Number(user.status) === STATUS.BANNED) {
    throw new ApiError(ERROR.BANNED, '账号已被封禁，请联系客服');
  }

  const patch = {
    // 续费语义：MAX(现有到期时间, 现在) + 时长，而不是覆盖
    expire_at: extendExpire(user.expire_at, days),
    level: Math.max(Number(user.level) || 1, Number(level) || 1),
    max_device: Math.max(Number(user.max_device) || 1, Number(maxDevice) || 1),
    // 不限量卡（0）应直接置为不限量，否则 max() 会把它挡掉
    quota_day: quotaNew === 0 ? 0 : Math.max(Number(user.quota_day) || 0, quotaNew),
    // 过期用户续费后恢复为正常
    status: Number(user.status) === STATUS.EXPIRED || Number(user.status) === STATUS.INACTIVE ? STATUS.NORMAL : user.status,
    bind_at: user.bind_at || ts,
    updated_at: ts,
  };
  await db.update('app_user', { id: user.id }, patch);
  user = await db.get('SELECT * FROM `app_user` WHERE `id` = ?', [user.id]);
  return { user, created: false };
}

/** 人为调整时长（后台「管理时长」）：delta 天，可为负 */
export async function adjustDuration(userId, { days, expireAt, level, maxDevice, quotaDay, adminId, remark }) {
  const db = getDb();
  const user = await findById(userId);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '用户不存在');

  const patch = { updated_at: now() };
  if (expireAt) {
    patch.expire_at = expireAt === PERMANENT_EXPIRE ? PERMANENT_EXPIRE : formatDateTime(parseDateTime(expireAt) || new Date());
  } else if (days !== undefined && days !== null && days !== '') {
    patch.expire_at = extendExpire(user.expire_at, Number(days));
  }
  if (level !== undefined && level !== null && level !== '') patch.level = Number(level);
  if (maxDevice !== undefined && maxDevice !== null && maxDevice !== '') patch.max_device = Number(maxDevice);
  if (quotaDay !== undefined && quotaDay !== null && quotaDay !== '') patch.quota_day = Number(quotaDay);
  if (remark !== undefined && remark !== null && remark !== '') patch.remark = String(remark).slice(0, 255);

  // 调整后如果仍在有效期内，且当前是「已过期」，顺手恢复为正常
  const nextExpire = parseDateTime(patch.expire_at || user.expire_at);
  if (nextExpire && nextExpire.getTime() > Date.now() && Number(user.status) === STATUS.EXPIRED) {
    patch.status = STATUS.NORMAL;
  }

  await db.update('app_user', { id: userId }, patch);
  await db.insert('user_profile_log', {
    user_id: userId,
    admin_id: adminId || null,
    field: 'duration',
    old_value: `expire=${user.expire_at},level=${user.level},max_device=${user.max_device},quota_day=${user.quota_day}`,
    new_value: JSON.stringify(patch),
    created_at: now(),
  });
  return findById(userId);
}

export async function setStatus(userId, status) {
  await getDb().update('app_user', { id: userId }, { status: Number(status), updated_at: now() });
  return findById(userId);
}

export async function addUserStats(tx, userId, { amount = '0.00', taskDelta = 1 } = {}) {
  const db = tx || getDb();
  const user = await db.get('SELECT `total_amount`, `total_task` FROM `app_user` WHERE `id` = ?', [userId]);
  if (!user) return;
  const { addMoney } = await import('../core/money.js');
  await db.update(
    'app_user',
    { id: userId },
    {
      total_amount: addMoney(user.total_amount || '0.00', amount),
      total_task: Number(user.total_task || 0) + Number(taskDelta || 0),
      updated_at: now(),
    },
  );
}

/* ------------------------------ 设备 ------------------------------ */

export async function listDevices(userId) {
  return getDb().query('SELECT * FROM `user_device` WHERE `user_id` = ? ORDER BY `id` ASC', [userId]);
}

/**
 * 绑定设备：达到上限时抛 2003，提示用户先解绑。
 * 同一设备重复绑定是幂等的（刷新 last_at / 版本信息即可）。
 * tx 由调用方传入时参与外层事务 —— 卡密激活必须把绑设备放进同一个事务，
 * 否则会出现「卡已占用但设备没绑上」的中间态。
 */
export async function bindDevice(userId, deviceId, info = {}, tx = null) {
  const db = tx || getDb();
  const id = String(deviceId || '').trim();
  if (!id) throw new ApiError(ERROR.PARAM, '缺少设备标识');

  const existing = await db.get('SELECT * FROM `user_device` WHERE `user_id` = ? AND `device_id` = ?', [userId, id]);
  const ts = now();
  if (existing) {
    if (Number(existing.status) === 2) throw new ApiError(ERROR.BANNED, '此设备已被封禁');
    await db.update(
      'user_device',
      { id: existing.id },
      {
        status: 1,
        last_at: ts,
        brand: info.brand || existing.brand,
        model: info.model || existing.model,
        os_version: info.osVersion || existing.os_version,
        app_version: info.appVersion || existing.app_version,
      },
    );
    return { device: await db.get('SELECT * FROM `user_device` WHERE `id` = ?', [existing.id]), reused: true };
  }

  const user = await findById(userId);
  const bound = await db.count('user_device', { user_id: userId, status: 1 });
  if (bound >= Number(user?.max_device || 1)) {
    throw new ApiError(ERROR.DEVICE_LIMIT, `最多绑定 ${user?.max_device || 1} 台设备，请先解绑其他设备`, {
      bound,
      max_device: Number(user?.max_device || 1),
    });
  }

  const result = await db.insert('user_device', {
    user_id: userId,
    device_id: id,
    brand: info.brand || null,
    model: info.model || null,
    os_version: info.osVersion || null,
    app_version: info.appVersion || null,
    first_at: ts,
    last_at: ts,
    status: 1,
  });
  return { device: await db.get('SELECT * FROM `user_device` WHERE `id` = ?', [result.insertId]), reused: false };
}

export async function unbindDevice(userId, deviceId) {
  const db = getDb();
  const row = await db.get('SELECT * FROM `user_device` WHERE `user_id` = ? AND `device_id` = ?', [userId, deviceId]);
  if (!row) throw new ApiError(ERROR.NOT_FOUND, '设备未绑定');
  await db.update('user_device', { id: row.id }, { status: 0, last_at: now() });
  // 解绑即撤销该设备上的所有会话
  await db.update(
    'license_session',
    { user_id: userId, device_id: deviceId, revoked: 0 },
    { revoked: 1, revoked_at: now() },
  );
  return true;
}

export async function isDeviceAllowed(userId, deviceId) {
  const row = await getDb().get(
    'SELECT `status` FROM `user_device` WHERE `user_id` = ? AND `device_id` = ?',
    [userId, deviceId],
  );
  return Boolean(row) && Number(row.status) === 1;
}

/* ---------------------------- 到期扫描 ---------------------------- */

/** cron:expire_check —— 只标记过期，不删除用户 */
export async function expireScan() {
  const db = getDb();
  const ts = now();
  const result = await db.run(
    'UPDATE `app_user` SET `status` = ?, `updated_at` = ? WHERE `status` = ? AND `expire_at` IS NOT NULL AND `expire_at` < ?',
    [STATUS.EXPIRED, ts, STATUS.NORMAL, ts],
  );
  if (result.affectedRows) log.info('到期扫描完成', { expired: result.affectedRows });
  return result.affectedRows;
}

/** 名单查询（后台列表共用）：支持排序/筛选/分页，索引见 schema.js */
export async function queryUsers({
  keyword,
  status,
  level,
  channel,
  startDate,
  endDate,
  expireStart,
  expireEnd,
  minAmount,
  maxAmount,
  sort = 'created_at',
  order = 'desc',
  page = 1,
  limit = 20,
} = {}) {
  const db = getDb();
  const where = [];
  const params = [];

  if (keyword) {
    where.push('(`username` LIKE ? OR `phone` LIKE ? OR `nickname` LIKE ? OR `remark` LIKE ?)');
    const like = `%${keyword}%`;
    params.push(like, like, like, like);
  }
  if (status !== undefined && status !== null && status !== '') {
    if (Number(status) === 2) {
      // 「已过期」既包含被标记的，也包含尚未被 cron 标记但时间已过的
      where.push('((`status` = 2) OR (`status` = 1 AND `expire_at` IS NOT NULL AND `expire_at` < ?))');
      params.push(now());
    } else {
      where.push('`status` = ?');
      params.push(Number(status));
    }
  }
  if (level) {
    where.push('`level` = ?');
    params.push(Number(level));
  }
  if (channel) {
    where.push('`reg_channel` = ?');
    params.push(channel);
  }
  if (startDate) {
    where.push('`created_at` >= ?');
    params.push(`${startDate} 00:00:00`);
  }
  if (endDate) {
    where.push('`created_at` <= ?');
    params.push(`${endDate} 23:59:59`);
  }
  if (expireStart) {
    where.push('`expire_at` >= ?');
    params.push(`${expireStart} 00:00:00`);
  }
  if (expireEnd) {
    where.push('`expire_at` <= ?');
    params.push(`${expireEnd} 23:59:59`);
  }
  if (minAmount) {
    where.push('`total_amount` >= ?');
    params.push(String(minAmount));
  }
  if (maxAmount) {
    where.push('`total_amount` <= ?');
    params.push(String(maxAmount));
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';

  // 排序白名单：后台表格的排序项（规划书 6.1），禁止直接拼用户输入
  const sortMap = {
    created_at: '`created_at`',
    id: '`id`',
    phone: '`phone`',
    username: '`username`',
    expire_at: '`expire_at`',
    total_amount: db.moneySort('total_amount'),
    total_task: '`total_task`',
    level: '`level`',
  };
  const sortCol = sortMap[sort] || sortMap.created_at;
  const direction = String(order).toLowerCase() === 'asc' ? 'ASC' : 'DESC';

  const total = Number(
    (await db.get(`SELECT COUNT(*) AS n FROM \`app_user\` WHERE ${clause}`, params))?.n || 0,
  );
  const rows = await db.query(
    `SELECT * FROM \`app_user\` WHERE ${clause} ORDER BY ${sortCol} ${direction}, \`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows, page, limit };
}

/** 用户详情页要用的关联数据（邀请码、设备、最近任务、订单） */
export async function userDetail(userId) {
  const db = getDb();
  const user = await findById(userId);
  if (!user) throw new ApiError(ERROR.NOT_FOUND, '用户不存在');
  const [invites, cards, devices, tasks, orders] = await Promise.all([
    // 新模型：用户来自哪张邀请码（卡密体系已下线，仅保留历史数据查询）
    db.query(
      'SELECT `code`, `batch_no`, `status`, `used_at`, `used_phone`, `duration`, `level` FROM `invite_code` WHERE `used_by` = ? ORDER BY `id` DESC LIMIT 5',
      [userId],
    ),
    db.query(
      'SELECT k.`code`, k.`status`, k.`used_at`, b.`batch_no`, b.`name` AS batch_name, b.`duration`, b.`price` ' +
        'FROM `card_key` k LEFT JOIN `card_batch` b ON b.`id` = k.`batch_id` WHERE k.`used_by` = ? ORDER BY k.`id` DESC LIMIT 20',
      [userId],
    ),
    listDevices(userId),
    db.query('SELECT * FROM `dw_task` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 10', [userId]),
    db.query('SELECT * FROM `order` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 20', [userId]),
  ]);
  return {
    user: { ...user, activation: authStatusPayload(user) },
    invites: invites.map((i) => ({
      ...i,
      display: String(i.code || '').replace(/(.{4})(?=.)/g, '$1-'),
      code: `****${String(i.code || '').slice(-4)}`,
    })),
    cards,
    devices,
    tasks,
    orders,
  };
}

export default {
  STATUS,
  STATUS_TEXT,
  effectiveStatus,
  assertUsable,
  authStatusPayload,
  activateUser,
  setPassword,
  findById,
  findByUsername,
  findByPhone,
  findByDevice,
  extendExpire,
  openOrExtend,
  adjustDuration,
  setStatus,
  addUserStats,
  listDevices,
  bindDevice,
  unbindDevice,
  isDeviceAllowed,
  expireScan,
  queryUsers,
  userDetail,
};
