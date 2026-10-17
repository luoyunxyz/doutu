#!/usr/bin/env node
/**
 * cron:reconcile —— 支付对账（每日 02:00）。
 *
 * 聚合支付漏单率不低，务必做（规划书 6.2「强烈建议做」）。
 * 默认对昨天；差异会写 reconcile_log 并触发告警。
 *
 * 用法：node src/cron/reconcile.js --date=2026-02-14
 */
import { withRuntime, parseArgs } from './lib.js';
import { reconcile } from '../modules/pay/routes.js';
import { formatDate } from '../core/util.js';

const args = parseArgs();
const target = args.date && args.date !== true ? String(args.date) : formatDate(new Date(Date.now() - 86400000));

await withRuntime('reconcile', async () => reconcile(target));
