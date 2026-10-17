#!/usr/bin/env node
/**
 * cron:system_health —— 服务器与进程健康检查（每 5 分钟）。
 *
 * 对应规划书 10.2 的监控项：
 *   CPU / 内存 / 磁盘 > 85%、媒体 worker 进程存活、证书到期（可选，需配置证书路径）。
 * 告警本身有 10 分钟静默窗口（services/alert.js），因此「每 5 分钟跑一次」等价于
 * 「持续超过 5 分钟才告警一次」，不会把机器人刷爆。
 */
import os from 'node:os';
import fs from 'node:fs';
import { withRuntime, parseArgs } from './lib.js';
import { getDb } from '../db/index.js';
import { sendAlert } from '../services/alert.js';
import * as storage from '../modules/dw/storage.js';
import config from '../config.js';
import { formatDateTime, addMinutes } from '../core/util.js';
import { logger } from '../core/logger.js';

const log = logger.child('system-health');

const args = parseArgs();
const THRESHOLD = Number(args.threshold || 85) / 100;

/** 磁盘使用率：优先用 statfs，平台不支持时返回 null */
function diskUsage(dir) {
  try {
    const stat = fs.statfsSync(dir);
    return 1 - stat.bavail / stat.blocks;
  } catch {
    return null;
  }
}

/** CPU 使用率：用 1 分钟负载 / 核数近似（跨平台可用，不需要额外依赖） */
function cpuUsage() {
  const cores = os.cpus().length || 1;
  return Math.min(1, os.loadavg()[0] / cores);
}

await withRuntime('system-health', async () => {
  const alerts = [];
  const cpu = cpuUsage();
  const memUsed = 1 - os.freemem() / os.totalmem();
  const diskPath = storage.usagePath();
  const disk = diskUsage(diskPath);
  if (disk === null) log.warn('磁盘水位不可用（statfs 失败）', { path: diskPath });

  if (cpu > THRESHOLD) {
    alerts.push({ key: 'cpu', text: `CPU 1 分钟负载 ${(cpu * 100).toFixed(1)}%（${os.loadavg()[0].toFixed(2)} / ${os.cpus().length} 核）` });
  }
  if (memUsed > THRESHOLD) {
    alerts.push({ key: 'mem', text: `内存使用率 ${(memUsed * 100).toFixed(1)}%` });
  }
  if (disk !== null && disk > THRESHOLD) {
    alerts.push({ key: 'disk', text: `磁盘使用率 ${(disk * 100).toFixed(1)}%（${config.storage.localDir}）` });
  }

  // 媒体 worker 存活：60 秒内没有心跳即视为进程已挂
  const db = getDb();
  const since = formatDateTime(addMinutes(new Date(), -2));
  const workers = await db.query('SELECT `worker_id`, `host`, `pid`, `processed`, `last_seen` FROM `sys_worker` WHERE `last_seen` >= ?', [since]);
  const stale = await db.query('SELECT `worker_id`, `last_seen` FROM `sys_worker` WHERE `last_seen` < ?', [since]);
  if (!workers.length) {
    alerts.push({
      key: 'worker-down',
      text: `媒体处理进程无心跳（离线进程：${stale.map((w) => w.worker_id).join(', ') || '无记录'}）。Supervisor 是否在守护 src/worker.js？`,
      level: 'error',
    });
  }

  for (const item of alerts) {
    await sendAlert(item.key === 'worker-down' ? '媒体处理进程异常' : '服务器资源告警', item.text, {
      level: item.level || 'warn',
      key: item.key,
    });
  }

  return {
    cpu: Math.round(cpu * 1000) / 10,
    mem: Math.round(memUsed * 1000) / 10,
    disk: disk === null ? null : Math.round(disk * 1000) / 10,
    workers: workers.length,
    alerts: alerts.length,
  };
});
