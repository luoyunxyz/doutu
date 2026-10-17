/**
 * 配额：Redis/内存计数，key 按天过期（规划书 7.2 第 7 步）。
 *
 * 消费与退还必须成对：解析/合成失败要退配额，超时不计费（规划书 3.3）。
 */
import { kv, keys } from '../core/kv.js';
import { ApiError, ERROR } from '../core/errors.js';
import { formatDate } from '../core/util.js';

const SECONDS_PER_DAY = 86400;

/** 距今天结束还有多少秒，作为计数键的 TTL */
function secondsUntilMidnight() {
  const nowDate = new Date();
  const midnight = new Date(nowDate.getFullYear(), nowDate.getMonth(), nowDate.getDate() + 1, 0, 0, 0, 0);
  return Math.max(60, Math.floor((midnight.getTime() - nowDate.getTime()) / 1000));
}

export const quotaKeyOf = (userId, date = formatDate()) => keys.quota(userId, date);

export async function quotaUsed(userId) {
  return Number((await kv().get(quotaKeyOf(userId))) || 0);
}

/**
 * 配额信息。
 * 约定：quota_day <= 0 表示**不限量**（unlimited=true，left=null），
 * 与 consumeQuota 的判定保持一致，避免客户端把不限量账号显示成「剩余 0」。
 */
export async function quotaInfo(user) {
  const used = await quotaUsed(user.id);
  const limit = Number(user.quota_day || 0);
  if (limit <= 0) return { used, limit: 0, left: null, unlimited: true };
  return { used, limit, left: Math.max(0, limit - used), unlimited: false };
}

/**
 * 消费一次配额；超限抛 3001。
 * 超限时把计数**压回上限**（而不是再次自增）—— 否则每次被拒的请求都会让
 * 当日用量继续虚高，客户端看到的 used 会一直往上涨且无法回收。
 */
export async function consumeQuota(user, cost = 1) {
  const limit = Number(user.quota_day || 0);
  if (limit <= 0) return { used: await quotaUsed(user.id), limit: 0, left: null, unlimited: true };

  const key = quotaKeyOf(user.id);
  const used = await kv().incr(key, secondsUntilMidnight());
  if (used > limit) {
    await kv().set(key, String(limit), secondsUntilMidnight());
    throw new ApiError(ERROR.QUOTA, '今日额度已用完', { limit, used: limit, left: 0 });
  }
  void cost;
  return { used, limit, left: Math.max(0, limit - used), unlimited: false };
}

/** 退还配额（任务失败、超时、服务端异常时调用） */
export async function refundQuota(user) {
  const key = quotaKeyOf(user.id);
  const used = Number((await kv().get(key)) || 0);
  if (used > 0) await kv().set(key, String(used - 1), secondsUntilMidnight());
}

export default { quotaKeyOf, quotaUsed, quotaInfo, consumeQuota, refundQuota };