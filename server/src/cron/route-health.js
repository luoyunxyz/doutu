#!/usr/bin/env node
/**
 * cron:route_health —— 解析线路成功率统计与告警（每 5 分钟）。
 *
 * 「按平台 × 线路统计近 1 小时成功率，低于 80% 红字告警」是业务健康度的第一指标
 * （规划书 3.1）。这里只做统计与告警，后台列表直接读 dw_route 的两个 1h 字段。
 */
import { withRuntime } from './lib.js';
import { refreshRouteHealth } from '../modules/dw/routeSelector.js';
import { queueStats } from '../modules/dw/taskService.js';
import { sendAlert } from '../services/alert.js';
import config from '../config.js';

await withRuntime('route-health', async () => {
  const alerts = await refreshRouteHealth();
  const queue = await queueStats();

  for (const item of alerts) {
    await sendAlert(
      '解析成功率过低',
      `线路「${item.name}」（${item.platform}）近 1 小时成功率 ${(item.rate * 100).toFixed(1)}%` +
        `（成功 ${item.succ} / 失败 ${item.fail}，阈值 ${(config.media.routeSuccAlert * 100).toFixed(0)}%），请检查解析规则是否失效`,
      { level: 'error', key: `route-alert-${item.route_id}` },
    );
  }
  return { alerts: alerts.length, backlog: queue.backlog };
});
