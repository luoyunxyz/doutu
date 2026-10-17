/**
 * 订单与支付入账（规划书 8.2 的支付部分）。
 *
 * 支付回调是唯一没有客户端签名的入口，因此这里的规则最严：
 *   ① 金额必须与订单一致；
 *   ② 同一 trade_no 只能成功处理一次（幂等）；
 *   ③ 只信任服务端主动反查的结果（回调后反查一次，见 pay/epay.js）。
 */
import { getDb } from '../db/index.js';
import { ApiError, ERROR } from '../core/errors.js';
import { now, formatDateTime, addMinutes, parseDateTime, businessNo } from '../core/util.js';
import { money, feeOf, subMoney, eqMoney, addMoney } from '../core/money.js';
import { logger } from '../core/logger.js';
import { getConfig, getInt } from './sysconfig.js';
import * as userService from './user.js';

const log = logger.child('order');

export const ORDER_STATUS = { PENDING: 0, PAID: 1, CLOSED: 2, REFUND: 3 };
export const ORDER_STATUS_TEXT = { 0: '待支付', 1: '已支付', 2: '已关闭', 3: '已退款' };

export const findOrder = (orderNo) => getDb().get('SELECT * FROM `order` WHERE `order_no` = ?', [String(orderNo)]);
export const findOrderByTradeNo = (tradeNo) =>
  getDb().get('SELECT * FROM `order` WHERE `trade_no` = ?', [String(tradeNo)]);

/** 创建待支付订单 */
export async function createOrder({ userId, goodsId, type = 1, payType = 'alipay', clientIp = null, remark = '' }) {
  const db = getDb();
  const user = userId ? await userService.findById(userId) : null;
  const goods = goodsId ? await db.get('SELECT * FROM `goods` WHERE `id` = ? AND `status` = 1', [goodsId]) : null;
  if (!goods) throw new ApiError(ERROR.PARAM, '商品不存在或已下架');

  const minAmount = await getConfig('pay_min_amount', '1.00');
  if (!eqMoney(goods.price, minAmount) && Number(goods.price) < Number(minAmount)) {
    throw new ApiError(ERROR.PARAM, `最低支付金额 ${minAmount} 元`);
  }

  const expireMin = await getInt('pay_order_expire_min', 30);
  const ts = now();
  const orderNo = businessNo('OD');
  const result = await db.insert('order', {
    order_no: orderNo,
    trade_no: null,
    user_id: user?.id || null,
    username: user?.username || null,
    phone: user?.phone || null,
    type,
    goods_id: goods.id,
    goods_name: goods.name,
    duration: Number(goods.duration),
    origin_amount: money(goods.origin_price || goods.price),
    amount: money(goods.price),
    discount: '0.00',
    fee: '0.00',
    net_amount: '0.00',
    pay_type: payType,
    pay_channel: null,
    status: ORDER_STATUS.PENDING,
    card_id: null,
    paid_at: null,
    client_ip: clientIp,
    remark: remark || null,
    created_at: ts,
    updated_at: ts,
  });

  log.info('订单已创建', { orderNo, goods: goods.name, amount: money(goods.price), expireMin });
  return {
    order: await db.get('SELECT * FROM `order` WHERE `id` = ?', [result.insertId]),
    expire_at: formatDateTime(addMinutes(new Date(), expireMin)),
  };
}

/**
 * 入账（回调 / 主动查询共用）。
 * 返回 { order, already, activated }，already=true 表示重复回调，已幂等处理。
 */
export async function markPaid({ orderNo, tradeNo, channel, amount, raw, ip, payType }) {
  const db = getDb();
  const feeRate = Number(await getConfig('pay_fee_rate', '0.02')) || 0;

  // 回调原始报文先落库：出现纠纷时这是唯一的证据链
  const record = {
    order_no: orderNo || null,
    trade_no: tradeNo || null,
    channel: channel || null,
    amount: money(amount || 0),
    status: 0,
    raw: raw ? (typeof raw === 'string' ? raw : JSON.stringify(raw)) : null,
    msg: null,
    ip: ip || null,
    created_at: now(),
  };

  const out = await db.tx(async (tx) => {
    const order = await tx.get(`SELECT * FROM \`order\` WHERE \`order_no\` = ?${tx.lockSuffix}`, [orderNo]);
    if (!order) throw new ApiError(ERROR.NOT_FOUND, '订单不存在');

    if (Number(order.status) === ORDER_STATUS.PAID) {
      // 重复回调是常态而不是异常（规划书附则第 4 条）：记录一条 status=4 的流水便于对账排查，
      // 但绝不再加一次时长、也不再入账。
      await tx.insert('payment_record', { ...record, status: 4, msg: '重复回调，已幂等忽略' });
      return { order, already: true, activated: false };
    }
    if (Number(order.status) === ORDER_STATUS.REFUND) {
      throw new ApiError(ERROR.PARAM, '订单已退款，忽略本次回调');
    }

    // 金额必须与订单一致，否则绝不入账
    if (amount !== undefined && amount !== null && amount !== '' && !eqMoney(amount, order.amount)) {
      await tx.insert('payment_record', { ...record, status: 3, msg: `金额不符：回调 ${money(amount)} / 订单 ${money(order.amount)}` });
      throw new ApiError(ERROR.PARAM, '回调金额与订单不一致');
    }

    // 同一 trade_no 只能入账一次（uk_trade_no 兜底，这里先给出人话错误）
    if (tradeNo) {
      const dup = await tx.get('SELECT `order_no` FROM `order` WHERE `trade_no` = ? AND `order_no` <> ?', [
        tradeNo,
        order.order_no,
      ]);
      if (dup) {
        await tx.insert('payment_record', { ...record, status: 4, msg: `交易号已被订单 ${dup.order_no} 使用` });
        throw new ApiError(ERROR.PARAM, '该交易号已入账');
      }
    }

    const fee = feeOf(order.amount, feeRate);
    await tx.update(
      'order',
      { id: order.id },
      {
        trade_no: tradeNo || order.trade_no,
        status: ORDER_STATUS.PAID,
        paid_at: now(),
        fee,
        net_amount: subMoney(order.amount, fee),
        pay_channel: channel || order.pay_channel,
        pay_type: payType || order.pay_type,
        updated_at: now(),
      },
    );

    // 自动开通/续费：商品里带着时长与等级
    let activated = false;
    if (order.user_id) {
      let duration = order.duration;
      let level = null;
      let maxDevice = null;
      let quotaDay = null;
      if (order.goods_id) {
        const goods = await tx.get('SELECT * FROM `goods` WHERE `id` = ?', [order.goods_id]);
        if (goods) {
          duration = Number(goods.duration);
          level = Number(goods.level);
          maxDevice = Number(goods.max_device);
          quotaDay = Number(goods.quota_day);
        }
      }
      await userService.openOrExtend(tx, {
        username: order.username,
        phone: order.phone,
        days: duration,
        level,
        maxDevice,
        quotaDay,
        source: 2,
        regIp: ip,
        regChannel: 'pay',
      });
      activated = true;
    }

    const inserted = await tx.insert('payment_record', { ...record, status: 1, msg: '入账成功' });
    return { order: { ...order, status: ORDER_STATUS.PAID }, already: false, activated, recordId: inserted.insertId };
  });

  // 事务外更新用户累计消费（金额统计不参与核心一致性）
  if (!out.already && out.order.user_id) {
    await userService
      .addUserStats(db, out.order.user_id, { amount: money(out.order.amount), taskDelta: 0 })
      .catch((err) => log.warn('累计消费更新失败', { err: err.message }));
  }

  log.info('订单入账', { orderNo, tradeNo, amount: money(out.order.amount), already: out.already });
  return out;
}

/** 关闭超时未支付订单（cron 每 10 分钟） */
export async function closeExpiredOrders() {
  const db = getDb();
  const expireMin = await getInt('pay_order_expire_min', 30);
  const cutoff = formatDateTime(addMinutes(new Date(), -expireMin));
  const result = await db.run(
    'UPDATE `order` SET `status` = ?, `updated_at` = ? WHERE `status` = ? AND `created_at` < ?',
    [ORDER_STATUS.CLOSED, now(), ORDER_STATUS.PENDING, cutoff],
  );
  if (result.affectedRows) log.info('关闭超时订单', { count: result.affectedRows });
  return result.affectedRows;
}

/** 退款（后台，需二次确认 + 审计） */
export async function refundOrder({ orderNo, adminId, reason = '' }) {
  const db = getDb();
  const order = await findOrder(orderNo);
  if (!order) throw new ApiError(ERROR.NOT_FOUND, '订单不存在');
  if (Number(order.status) !== ORDER_STATUS.PAID) throw new ApiError(ERROR.PARAM, '只有已支付订单可以退款');

  await db.update(
    'order',
    { id: order.id },
    { status: ORDER_STATUS.REFUND, remark: `${order.remark || ''} | 退款：${reason}`.slice(0, 255), updated_at: now() },
  );
  // 退款后立即吊销会话：避免「退款了还能继续用」
  if (order.user_id) await userService.setStatus(order.user_id, userService.STATUS.EXPIRED);
  log.warn('订单退款', { orderNo, adminId, reason });
  return findOrder(orderNo);
}

/** 后台订单列表：筛选维度按规划书 6.2 */
export async function listOrders({
  keyword,
  status,
  payType,
  channel,
  startDate,
  endDate,
  minAmount,
  maxAmount,
  type,
  sort = 'created_at',
  order = 'desc',
  page = 1,
  limit = 20,
} = {}) {
  const db = getDb();
  const where = [];
  const params = [];
  if (keyword) {
    where.push('(`order_no` LIKE ? OR `trade_no` LIKE ? OR `username` LIKE ? OR `phone` LIKE ?)');
    const like = `%${keyword}%`;
    params.push(like, like, like, like);
  }
  if (status !== undefined && status !== null && status !== '') {
    where.push('`status` = ?');
    params.push(Number(status));
  }
  if (type) {
    where.push('`type` = ?');
    params.push(Number(type));
  }
  if (payType) {
    where.push('`pay_type` = ?');
    params.push(payType);
  }
  if (channel) {
    where.push('`pay_channel` = ?');
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
  if (minAmount) {
    where.push('`amount` >= ?');
    params.push(money(minAmount));
  }
  if (maxAmount) {
    where.push('`amount` <= ?');
    params.push(money(maxAmount));
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';

  const sortMap = {
    created_at: '`created_at`',
    paid_at: '`paid_at`',
    amount: db.moneySort('amount'),
    id: '`id`',
    status: '`status`',
  };
  const sortCol = sortMap[sort] || sortMap.created_at;
  const direction = String(order).toLowerCase() === 'asc' ? 'ASC' : 'DESC';

  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`order\` WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT * FROM \`order\` WHERE ${clause} ORDER BY ${sortCol} ${direction}, \`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows, page, limit };
}

/** 导出用：不分页但设上限，超限提示走异步（规划书 6.2 的 5 万行规则） */
export async function exportOrders(filters = {}, maxRows = 50000) {
  const { rows } = await listOrders({ ...filters, page: 1, limit: maxRows });
  return rows;
}

/** 订单总数与今日金额：后台首页卡片 */
export async function orderSummary() {
  const db = getDb();
  const today = formatDateTime().slice(0, 10);
  const [pending, paidTotal, todayPaid] = await Promise.all([
    db.get('SELECT COUNT(*) AS n FROM `order` WHERE `status` = 0'),
    db.get('SELECT COUNT(*) AS n, SUM(`amount`) AS amt FROM `order` WHERE `status` = 1'),
    db.get(
      'SELECT COUNT(*) AS n, SUM(`amount`) AS amt FROM `order` WHERE `status` = 1 AND `paid_at` >= ? AND `paid_at` <= ?',
      [`${today} 00:00:00`, `${today} 23:59:59`],
    ),
  ]);
  return {
    pending_count: Number(pending?.n || 0),
    paid_count: Number(paidTotal?.n || 0),
    paid_amount: money(paidTotal?.amt || 0),
    today_count: Number(todayPaid?.n || 0),
    today_amount: money(todayPaid?.amt || 0),
  };
}

/** 当天某小时是否已有支付记录（对账用） */
export async function paidOrdersBetween(from, to) {
  return getDb().query(
    'SELECT * FROM `order` WHERE `status` = 1 AND `paid_at` >= ? AND `paid_at` <= ? ORDER BY `id` ASC',
    [from, to],
  );
}

export { addMoney, parseDateTime };
