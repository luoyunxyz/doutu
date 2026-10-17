/**
 * 媒体处理 worker（独立进程，Supervisor 守护）。
 *
 * 为什么必须独立进程（规划书 1.1）：视频合成动辄 10–60 秒，
 * 放进请求线程会把 API 拖死；宝塔的 PHP-FPM/Node 请求都有超时上限。
 *
 * 并发上限 = WORKER_CONCURRENCY（默认 CPU 核数 - 1），
 * 避免 FFmpeg 把 CPU 打满导致 Nginx 502（规划书 3.3）。
 */
import os from 'node:os';
import config from './config.js';
import { logger } from './core/logger.js';
import { openDb, initSchema, getDb, closeDb } from './db/index.js';
import { openKv, closeKv, kv, keys } from './core/kv.js';
import { randomHex, sleep, now } from './core/util.js';
import * as taskService from './modules/dw/taskService.js';
import * as storage from './modules/dw/storage.js';
import { sendAlert } from './services/alert.js';
import { getInt } from './services/sysconfig.js';

const log = logger.child('worker');

const workerId = `${os.hostname()}-${process.pid}-${randomHex(3)}`;

let running = 0;
let stopping = false;
let consecutiveErrors = 0;
let processedTotal = 0;
// 统一用本地时区格式化（core/util.now），绝不能混用 toISOString()——那是 UTC，
// 会让 system-health 的「进程存活」判断在非 UTC 服务器上永远看不到心跳。
const startedAt = now();

async function heartbeat() {
  const ts = now();
  try {
    await kv().set(keys.workerHeartbeat(workerId), String(Date.now()), 60);
    // 落库一份：进程存活告警（规划书 10.2）需要能跨进程/跨重启判断，
    // 而内存 KV 在多进程下互相看不见，Redis 也不是必选项。
    await getDb().upsert(
      'sys_worker',
      ['worker_id'],
      {
        worker_id: workerId,
        host: os.hostname().slice(0, 64),
        pid: process.pid,
        concurrency: config.media.workerConcurrency,
        processed: processedTotal,
        last_seen: ts,
        started_at: startedAt,
        created_at: startedAt,
        updated_at: ts,
      },
      ['processed', 'last_seen', 'concurrency', 'host', 'pid', 'updated_at'],
    );
  } catch (err) {
    log.debug('心跳写入失败', { err: err.message });
  }
}

export { workerId };

/** 跑一条任务，含失败重试与告警 */
async function runOne(task) {
  running += 1;
  const started = Date.now();
  try {
    const result = await taskService.processTask(task, workerId);
    if (!result.ok) {
      // 失败补偿：换备用线路重试一次，仍失败则退配额并告警
      const retried = await taskService.retryTask(task);
      if (!retried) {
        await sendAlert('解析任务失败', `任务 ${task.task_no}（平台 ${task.platform}）失败：${result.reason}`, {
          key: `task-fail-${task.platform}`,
        }).catch(() => {});
      }
    }
    consecutiveErrors = 0;
    return result;
  } catch (err) {
    consecutiveErrors += 1;
    log.error('任务处理异常', { taskNo: task.task_no, err });
    await taskService.failTask(task, `worker 异常：${err.message}`).catch(() => {});
    return { ok: false, reason: err.message };
  } finally {
    running -= 1;
    processedTotal += 1;
    log.debug('任务处理结束', { taskNo: task.task_no, ms: Date.now() - started });
  }
}

/** 单轮消费：把所有空闲槽位填满 */
async function drain() {
  const concurrency = Math.max(1, config.media.workerConcurrency);
  const pending = [];
  while (!stopping && running + pending.length < concurrency) {
    const task = await taskService.claimTask(workerId);
    if (!task) break;
    pending.push(runOne(task));
  }
  if (pending.length) await Promise.all(pending);
  return pending.length;
}

/** worker 主循环 */
export async function startWorker() {
  log.info('媒体 worker 启动', {
    workerId,
    concurrency: config.media.workerConcurrency,
    timeoutSec: config.media.taskTimeoutSec,
    tmpDir: config.storage.tmpDir,
    storage: config.storage.driver,
    font: config.media.font || '(未配置)',
  });

  const { checkEnvironment } = await import('./modules/dw/watermark.js');
  const env = await checkEnvironment();
  if (!env.ffmpeg || !env.ffprobe) {
    log.error('FFmpeg / FFprobe 不可用，媒体任务无法完成', env);
    await sendAlert('媒体服务环境异常', `FFmpeg 不可用：ffmpeg=${env.ffmpeg} ffprobe=${env.ffprobe}`, { level: 'error' });
  } else if (!env.font) {
    log.warn('未找到中文字体，水印中文会渲染成方块（Linux 请安装 fonts-wqy-zenhei）');
  }

  // 超时扫描节流：每 30 秒扫一次
  let lastTimeoutScan = 0;
  let lastCleanup = 0;

  while (!stopping) {
    try {
      const processed = await drain();
      await heartbeat();

      const nowMs = Date.now();
      if (nowMs - lastTimeoutScan > 30_000) {
        lastTimeoutScan = nowMs;
        const timedOut = await taskService.timeoutScan();
        if (timedOut) log.warn('超时任务已标记', { count: timedOut });
      }
      if (nowMs - lastCleanup > 3_600_000) {
        lastCleanup = nowMs;
        const removed = await storage.cleanupTemp(120);
        if (removed) log.info('临时文件已清理', { removed });
      }

      // 队列积压告警（>50）
      const stats = await taskService.queueStats();
      const threshold = await getInt('queue_max', 50);
      if (stats.backlog > threshold) {
        await sendAlert('去水印队列积压', `当前积压 ${stats.backlog} 个任务（阈值 ${threshold}），请检查解析线路与 worker 状态`, {
          key: 'queue-backlog',
        }).catch(() => {});
      }

      if (!processed) {
        await Promise.race([taskService.waitForWake(config.media.workerPollMs), sleep(config.media.workerPollMs)]);
      }
      if (consecutiveErrors > 20) {
        log.error('连续异常次数过多，暂停 30 秒后继续');
        await sleep(30_000);
        consecutiveErrors = 0;
      }
    } catch (err) {
      log.error('worker 主循环异常', { err });
      await sleep(3000);
    }
  }

  // 等待在跑的任务收尾
  const deadline = Date.now() + 30_000;
  while (running > 0 && Date.now() < deadline) await sleep(300);
  log.info('worker 已退出', { workerId });
}

async function main() {
  await openDb();
  await initSchema(getDb());
  await openKv();
  await startWorker();

  const shutdown = async () => {
    stopping = true;
    taskService.wakeWorker();
    setTimeout(async () => {
      await closeKv().catch(() => {});
      await closeDb().catch(() => {});
      process.exit(0);
    }, 1500);
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

/** 直接运行时才启动（被 index.js import 时不自动跑） */
const isDirectRun = process.argv[1] && process.argv[1].replace(/\\/g, '/').endsWith('src/worker.js');
if (isDirectRun) {
  main().catch((err) => {
    log.error('worker 启动失败', { err });
    process.exit(1);
  });
}

export default { startWorker };
