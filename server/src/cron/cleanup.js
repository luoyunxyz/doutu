#!/usr/bin/env node
/**
 * cron:cleanup —— 临时文件与磁盘清理（每小时）。
 *
 * FFmpeg 临时文件会持续增长，磁盘 100% 会导致 MySQL 拒绝写入 ——
 * 这是「容易被忽略但会出事」的清单第一条（规划书附则第 3 条）。
 *
 * 用法：node src/cron/cleanup.js --minutes=120 --task-days=90
 */
import fs from 'node:fs';
import { withRuntime, parseArgs } from './lib.js';
import * as storage from '../modules/dw/storage.js';
import { getDb } from '../db/index.js';
import { formatDateTime, addHours } from '../core/util.js';
import { logger } from '../core/logger.js';

const log = logger.child('cleanup');

const args = parseArgs();

await withRuntime('cleanup', async () => {
  const minutes = Math.max(30, Number(args.minutes) || 120);
  const removed = await storage.cleanupTemp(minutes);

  // 成品文件按任务保留期清理：任务记录保留 90 天，文件本身按 OSS/磁盘策略更早清理
  const taskDays = Math.max(7, Number(args['task-days']) || 90);
  const cutoff = formatDateTime(addHours(new Date(), -24 * taskDays));
  const db = getDb();
  const old = await db.query(
    'SELECT `id`, `out_key`, `src_key` FROM `dw_task` WHERE `created_at` < ? AND (`out_key` IS NOT NULL OR `src_key` IS NOT NULL) LIMIT 500',
    [cutoff],
  );
  let removedFiles = 0;
  for (const task of old) {
    for (const key of [task.out_key, task.src_key]) {
      if (key && (await storage.remove(key))) removedFiles += 1;
    }
    await db.update('dw_task', { id: task.id }, { out_key: null, src_key: null, out_url: null });
  }

  // 清理早已离线的 worker 心跳记录（保留 7 天，便于回溯进程重启历史）
  const workerCutoff = formatDateTime(addHours(new Date(), -24 * 7));
  const staleWorkers = await db.run('DELETE FROM `sys_worker` WHERE `last_seen` < ?', [workerCutoff]);

  // 磁盘水位告警：剩余空间低于 10% 时立即告警（磁盘满是 MySQL 拒写的直接原因）
  const { sendAlert } = await import('../services/alert.js');
  try {
    const statfs = fs.statfsSync(storage.usagePath());
    const freeRatio = statfs.bavail / statfs.blocks;
    if (freeRatio < 0.1) {
      await sendAlert(
        '服务器磁盘空间不足',
        `存储目录剩余 ${(freeRatio * 100).toFixed(1)}%，请立即清理 /www/tmp 与旧成品，否则 MySQL 可能拒绝写入`,
        { level: 'error', key: 'disk-low' },
      );
    }
  } catch (err) {
    log.warn('磁盘水位检查失败', { err: err.message });
  }

  return {
    temp_removed: removed,
    task_files_removed: removedFiles,
    scanned: old.length,
    stale_workers: staleWorkers.affectedRows,
    minutes,
    task_days: taskDays,
  };
});
