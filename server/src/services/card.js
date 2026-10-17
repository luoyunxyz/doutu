/**
 * 卡密系统（规划书 5 章）。
 *
 * 关键点：
 *  - 字符集剔除 0/O/1/I/L，16 位 → 31^16 组合，配合 uk_code 唯一索引兜底；
 *  - 激活必须在事务里 `SELECT ... FOR UPDATE` 锁住卡密，否则同一张卡能被并发激活两次；
 *  - 续费语义是 MAX(到期时间, 现在) + 时长，不是覆盖；
 *  - 已使用卡密被同一设备重复提交 → 幂等返回成功，不重复加时长。
 */
import { getDb } from '../db/index.js';
import { ApiError, ERROR } from '../core/errors.js';
import { now, randomCode, batchNo, formatCardCode, addDays, formatDateTime, parseDateTime } from '../core/util.js';
import { money } from '../core/money.js';
import { logger } from '../core/logger.js';
import * as userService from './user.js';

const log = logger.child('card');

/** 用户抄录的卡密可能带横线、空格、小写，统一归一化 */
export const normalizeCode = (code) =>
  String(code || '')
    .toUpperCase()
    .replace(/[^0-9A-Z]/g, '');

/* ------------------------------ 生成 ------------------------------ */

export async function createBatch({
  count = 10,
  duration = 30,
  level = 1,
  maxDevice = 1,
  quotaDay = 20,
  price = '0.00',
  name = '',
  expireAt = null,
  remark = '',
  adminId = null,
}) {
  const db = getDb();
  const total = Math.max(1, Math.min(100000, Number(count) || 1));
  const ts = now();
  // quota_day = 0 表示不限量，是合法值，不能用 || 顶掉
  const quotaDayValue = quotaDay === undefined || quotaDay === null || quotaDay === '' ? 20 : Number(quotaDay);

  const todayPrefix = `CB${ts.slice(0, 10).replace(/-/g, '')}`;
  const seqRow = await db.get('SELECT COUNT(*) AS n FROM `card_batch` WHERE `batch_no` LIKE ?', [`${todayPrefix}%`]);
  const batch = {
    batch_no: batchNo(Number(seqRow?.n || 0) + 1, new Date()),
    name: name || `${duration ? `${duration}天卡` : '永久卡'}`,
    duration: Number(duration) || 0,
    level: Number(level) || 1,
    max_device: Number(maxDevice) || 1,
    quota_day: quotaDayValue,
    price: money(price),
    total,
    used: 0,
    expire_at: expireAt ? formatDateTime(parseDateTime(expireAt) || new Date()) : null,
    created_by: adminId || null,
    remark: remark || null,
    created_at: ts,
    updated_at: ts,
  };
  const result = await db.insert('card_batch', batch);
  const batchId = result.insertId;

  const codes = await generateCodes(batchId, total);
  log.info('卡密批次已生成', { batchNo: batch.batch_no, count: codes.length, duration: batch.duration });
  return { batch: await db.get('SELECT * FROM `card_batch` WHERE `id` = ?', [batchId]), codes };
}

/**
 * 批量插卡密：唯一索引兜底 + 冲突重试。
 * 不在应用层「先查再插」—— 并发下那样会漏（规划书 5.1）。
 */
export async function generateCodes(batchId, count) {
  const db = getDb();
  const codes = [];
  const ts = now();
  for (let i = 0; i < count; i += 1) {
    let inserted = false;
    for (let attempt = 0; attempt < 6 && !inserted; attempt += 1) {
      const code = randomCode(16);
      try {
        await db.insert('card_key', {
          code,
          batch_id: batchId,
          status: 0,
          created_at: ts,
        });
        codes.push(code);
        inserted = true;
      } catch (err) {
        const msg = String(err?.message || '');
        // 唯一键冲突 → 换一个再试；其他错误直接抛
        if (!/UNIQUE|Duplicate|constraint/i.test(msg)) throw err;
      }
    }
    if (!inserted) throw new ApiError(ERROR.SERVER, '卡密生成失败，请重试');
  }
  return codes;
}

/* ------------------------------ 激活 ------------------------------ */

/**
 * 卡密激活（原子）：锁卡 → 校验 → 开通/续费 → 占卡 → 写订单 → 绑设备。
 * 返回 { user, card, batch, session, idempotent }
 */
export async function activateCard({
  code,
  deviceId,
  phone = null,
  nickname = null,
  ip,
  ua,
  appVersion,
  osVersion,
  brand,
  model,
}) {
  const db = getDb();
  const normalized = normalizeCode(code);
  if (normalized.length !== 16) throw new ApiError(ERROR.PARAM, '卡密格式不正确');

  // 事务外先做一次幂等判断（同一设备重复提交同一张卡）
  const preCard = await db.get('SELECT * FROM `card_key` WHERE `code` = ?', [normalized]);
  if (!preCard) throw new ApiError(ERROR.PARAM, '卡密不存在，请核对后重试');
  if (Number(preCard.status) === 1 && preCard.used_by) {
    const owner = await userService.findById(preCard.used_by);
    const sameDevice = normalizedDeviceOwner(preCard, deviceId);
    if (owner && sameDevice) {
      return { user: owner, card: preCard, idempotent: true, batch: null };
    }
    throw new ApiError(ERROR.PARAM, '该卡密已被使用');
  }
  if (Number(preCard.status) === 2) throw new ApiError(ERROR.PARAM, '该卡密已作废');
  if (Number(preCard.status) === 3) throw new ApiError(ERROR.PARAM, '该卡密已过期');

  const out = await db.tx(async (tx) => {
    // 1) 悲观锁住卡密（SQLite 下事务本身串行，lockSuffix 为空）
    const card = await tx.get(
      `SELECT * FROM \`card_key\` WHERE \`code\` = ?${tx.lockSuffix}`,
      [normalized],
    );
    if (!card) throw new ApiError(ERROR.PARAM, '卡密不存在');
    if (Number(card.status) !== 0) throw new ApiError(ERROR.PARAM, '该卡密已被使用');

    // 2) 批次校验（卡密本身的有效期）
    const batch = await tx.get('SELECT * FROM `card_batch` WHERE `id` = ?', [card.batch_id]);
    if (!batch) throw new ApiError(ERROR.SERVER, '卡密批次不存在');
    const batchExpire = parseDateTime(batch.expire_at);
    if (batchExpire && batchExpire.getTime() < Date.now()) {
      await tx.update('card_key', { id: card.id }, { status: 3 });
      throw new ApiError(ERROR.PARAM, '该批次卡密已过期');
    }

    // 3) 开通或续费
    const username = phone || `u${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
    const { user, created } = await userService.openOrExtend(tx, {
      username: phone || username,
      phone: phone || null,
      nickname: nickname || null,
      days: Number(batch.duration),
      level: Number(batch.level),
      maxDevice: Number(batch.max_device),
      quotaDay: Number(batch.quota_day),
      source: 1,
      regIp: ip,
      regChannel: 'card',
    });

    // 4) 占卡：受影响行数必须为 1
    const occupied = await tx.update(
      'card_key',
      { id: card.id, status: 0 },
      { status: 1, used_by: user.id, used_at: now(), device_id: deviceId || null },
    );
    if (occupied.affectedRows !== 1) throw new ApiError(ERROR.SERVER, '卡密占用失败，请重试');

    await tx.update('card_batch', { id: batch.id }, { used: Number(batch.used || 0) + 1, updated_at: now() });

    // 5) 写订单（卡密渠道，金额=批次面值）
    const orderResult = await tx.insert('order', {
      order_no: `CK${Date.now()}${Math.floor(Math.random() * 1000)}`,
      trade_no: null,
      user_id: user.id,
      username: user.username,
      phone: user.phone,
      type: created ? 1 : 2,
      goods_id: null,
      goods_name: batch.name,
      duration: Number(batch.duration),
      origin_amount: money(batch.price),
      amount: money(batch.price),
      discount: '0.00',
      fee: '0.00',
      net_amount: money(batch.price),
      pay_type: 'card',
      pay_channel: 'card',
      status: 1,
      card_id: card.id,
      paid_at: now(),
      client_ip: ip || null,
      remark: `卡密激活 ${batch.batch_no}`,
      created_at: now(),
      updated_at: now(),
    });

    // 6) 绑定设备（在事务内，失败会连带回滚，避免出现「卡已用但设备没绑上」）
    if (deviceId) {
      await userService.bindDevice(
        user.id,
        deviceId,
        { brand, model, osVersion, appVersion },
        tx,
      );
    }

    return { user, card, batch, orderId: orderResult.insertId, created };
  });

  // 事务外：写累计消费（非关键路径，失败不影响开通结果）与会话
  try {
    await userService.addUserStats(db, out.user.id, { amount: money(out.batch?.price || 0), taskDelta: 0 });
  } catch (err) {
    log.warn('累计消费写入失败', { userId: out.user.id, err: err.message });
  }

  const fresh = await userService.findById(out.user.id);
  return {
    user: fresh,
    card: out.card,
    batch: out.batch,
    order_id: out.orderId,
    created: out.created,
    idempotent: false,
  };
}

function normalizedDeviceOwner(card, deviceId) {
  if (!deviceId) return false;
  return String(card.device_id || '').toLowerCase() === String(deviceId).toLowerCase();
}

/* ------------------------------ 管理 ------------------------------ */

export async function voidCard(code, reason = '') {
  const db = getDb();
  const card = await db.get('SELECT * FROM `card_key` WHERE `code` = ?', [normalizeCode(code)]);
  if (!card) throw new ApiError(ERROR.NOT_FOUND, '卡密不存在');
  if (Number(card.status) === 1) throw new ApiError(ERROR.PARAM, '卡密已被使用，不能作废');
  await db.update('card_key', { id: card.id }, { status: 2 });
  log.info('卡密作废', { code: formatCardCode(card.code), reason });
  return true;
}

export async function voidBatch(batchId) {
  const db = getDb();
  const result = await db.update('card_key', { batch_id: batchId, status: 0 }, { status: 2 });
  await db.update('card_batch', { id: batchId }, { updated_at: now() });
  return result.affectedRows;
}

export async function listBatches({ keyword, page = 1, limit = 20 } = {}) {
  const db = getDb();
  const params = [];
  let clause = '1 = 1';
  if (keyword) {
    clause = '(`batch_no` LIKE ? OR `name` LIKE ?)';
    params.push(`%${keyword}%`, `%${keyword}%`);
  }
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`card_batch\` WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT * FROM \`card_batch\` WHERE ${clause} ORDER BY \`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows };
}

export async function listCards({ batchId, status, keyword, page = 1, limit = 50 } = {}) {
  const db = getDb();
  const where = [];
  const params = [];
  if (batchId) {
    where.push('k.`batch_id` = ?');
    params.push(Number(batchId));
  }
  if (status !== undefined && status !== null && status !== '') {
    where.push('k.`status` = ?');
    params.push(Number(status));
  }
  if (keyword) {
    where.push('k.`code` LIKE ?');
    params.push(`%${normalizeCode(keyword)}%`);
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`card_key\` k WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT k.*, b.\`batch_no\`, b.\`duration\`, b.\`level\`, b.\`price\`, u.\`username\` AS used_username ` +
      `FROM \`card_key\` k LEFT JOIN \`card_batch\` b ON b.id = k.batch_id ` +
      `LEFT JOIN \`app_user\` u ON u.id = k.used_by ` +
      `WHERE ${clause} ORDER BY k.\`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows };
}

/** 导出：带横线的展示格式，便于发给客户抄录 */
export async function exportCodes(batchId) {
  const db = getDb();
  const rows = await db.query('SELECT `code` FROM `card_key` WHERE `batch_id` = ? ORDER BY `id` ASC', [batchId]);
  return rows.map((r) => formatCardCode(r.code));
}

export async function batchStats(batchId) {
  const db = getDb();
  const rows = await db.query(
    'SELECT `status`, COUNT(*) AS n FROM `card_key` WHERE `batch_id` = ? GROUP BY `status`',
    [batchId],
  );
  const out = { unused: 0, used: 0, voided: 0, expired: 0 };
  for (const row of rows) {
    if (Number(row.status) === 0) out.unused = Number(row.n);
    else if (Number(row.status) === 1) out.used = Number(row.n);
    else if (Number(row.status) === 2) out.voided = Number(row.n);
    else out.expired = Number(row.n);
  }
  return out;
}

/**
 * 批量统计（一次 GROUP BY 取回多批次，避免后台列表 N+1 查询）。
 * @returns {Map<number, {unused,used,voided,expired}>}
 */
export async function batchStatsBulk(batchIds = []) {
  const ids = batchIds.map(Number).filter(Boolean);
  const empty = new Map(ids.map((id) => [id, { unused: 0, used: 0, voided: 0, expired: 0 }]));
  if (!ids.length) return empty;
  const db = getDb();
  const rows = await db.query(
    `SELECT \`batch_id\`, \`status\`, COUNT(*) AS n FROM \`card_key\` WHERE \`batch_id\` IN (${ids
      .map(() => '?')
      .join(', ')}) GROUP BY \`batch_id\`, \`status\``,
    ids,
  );
  for (const row of rows) {
    const bucket = empty.get(Number(row.batch_id));
    if (!bucket) continue;
    const n = Number(row.n);
    if (Number(row.status) === 0) bucket.unused += n;
    else if (Number(row.status) === 1) bucket.used += n;
    else if (Number(row.status) === 2) bucket.voided += n;
    else bucket.expired += n;
  }
  return empty;
}

export default {
  normalizeCode,
  createBatch,
  generateCodes,
  activateCard,
  voidCard,
  voidBatch,
  listBatches,
  listCards,
  exportCodes,
  batchStats,
  batchStatsBulk,
};
