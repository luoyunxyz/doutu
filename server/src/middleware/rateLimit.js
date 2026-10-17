/**
 * 三级限流（单 IP / 单设备 / 单用户），固定窗口计数，落在 KV 上。
 *
 * 规划书 7.2 第 8 步。卡密激活另有更严的一层：同 IP 1 小时失败 20 次封 1 小时
 * （见 clientAuth 里的 activateGuard）。
 */
import { kv, keys } from '../core/kv.js';
import { ApiError, ERROR } from '../core/errors.js';

/**
 * @param {string} key   计数键
 * @param {number} limit 窗口内允许次数
 * @param {number} windowSec 窗口秒数
 */
export async function hit(key, limit, windowSec) {
  if (!limit || limit <= 0) return { allowed: true, count: 0, limit };
  const count = await kv().incr(key, windowSec);
  if (count > limit) {
    const ttl = await kv().ttl(key);
    throw new ApiError(ERROR.RATE_LIMIT, '操作过于频繁，请稍后再试', {
      limit,
      window_sec: windowSec,
      retry_after: ttl > 0 ? ttl : windowSec,
    });
  }
  return { allowed: true, count, limit };
}

/** 默认业务限流：按 IP 与用户双维度 */
export async function limitAll(ctx, { bucket = 'api', ipLimit = 120, userLimit = 60, windowSec = 60 } = {}) {
  await hit(keys.rateIp(ctx.ip, bucket), ipLimit, windowSec);
  if (ctx.state.user) {
    await hit(keys.rateUser(ctx.state.user.id, bucket), userLimit, windowSec);
  }
  return true;
}

export default { hit, limitAll };
