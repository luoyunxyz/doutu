/**
 * 定时任务的公共外壳：开库 → 跑任务 → 关库 → 记录耗时与结果。
 *
 * crontab 里每个任务都是独立进程（规划书 9.3），这样单个任务卡死不会影响其他任务，
 * 也避免了在 API 进程里塞定时器导致重启丢任务。
 */
import { logger } from '../core/logger.js';
import { openDb, initSchema, getDb, closeDb } from '../db/index.js';
import { openKv, closeKv } from '../core/kv.js';

const log = logger.child('cron');

export async function withRuntime(name, fn) {
  const started = Date.now();
  log.info(`[${name}] 开始`);
  try {
    await openDb();
    await initSchema(getDb());
    await openKv();
    const result = await fn({ db: getDb() });
    log.info(`[${name}] 完成`, { ms: Date.now() - started, result });
    await closeKv().catch(() => {});
    await closeDb().catch(() => {});
    return result;
  } catch (err) {
    log.error(`[${name}] 失败`, { err, ms: Date.now() - started });
    await closeKv().catch(() => {});
    await closeDb().catch(() => {});
    process.exitCode = 1;
    return null;
  }
}

/** 简单参数解析：--date=2026-02-14 --days=3 */
export function parseArgs(argv = process.argv.slice(2)) {
  const out = {};
  for (const arg of argv) {
    const m = /^--([^=]+)(?:=(.*))?$/.exec(arg);
    if (m) out[m[1]] = m[2] === undefined ? true : m[2];
  }
  return out;
}

export default { withRuntime, parseArgs };
