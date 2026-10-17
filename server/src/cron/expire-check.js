#!/usr/bin/env node
/**
 * cron:expire_check —— 卡密与用户到期扫描（每 10 分钟）。
 *
 * 只「标记过期」，绝不删除用户：用户续费后要能恢复（规划书附则第 9 条）。
 * 顺带清理超时未支付订单与过期会话，避免这三件事各写一个脚本。
 */
import { withRuntime } from './lib.js';
import * as userService from '../services/user.js';
import * as sessionService from '../services/session.js';
import * as orderService from '../services/order.js';

await withRuntime('expire-check', async () => {
  const expiredUsers = await userService.expireScan();
  const closedOrders = await orderService.closeExpiredOrders();
  const cleanedSessions = await sessionService.cleanupExpired(7);
  return { expired_users: expiredUsers, closed_orders: closedOrders, cleaned_sessions: cleanedSessions };
});
