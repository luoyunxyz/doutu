#!/usr/bin/env node
/**
 * cron:revenue_daily —— 收益日聚合（每日 01:10）。
 *
 * 默认补算最近 3 天：聚合支付的对账可能延迟到账，
 * 只算昨天会出现「第一天少算了、之后再也补不回来」的问题（规划书附则第 10 条）。
 *
 * 用法：node src/cron/revenue-daily.js --days=3
 */
import { withRuntime, parseArgs } from './lib.js';
import * as revenueService from '../services/revenue.js';

const args = parseArgs();

await withRuntime('revenue-daily', async () => {
  const days = Math.max(1, Math.min(90, Number(args.days) || 3));
  const rows = await revenueService.aggregateRange(days);
  return { days, rows: rows.length, gross: rows.map((r) => r.gross) };
});
