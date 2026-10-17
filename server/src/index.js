/**
 * 业务 API + 后台 进程入口。
 *
 * 生产环境（宝塔 + Supervisor）：
 *   node src/index.js     ← 本文件
 *   node src/worker.js    ← 媒体处理独立进程（视频合成动辄几十秒，绝不能塞进请求线程）
 */
import config from './config.js';
import { logger } from './core/logger.js';
import { openDb, initSchema, closeDb } from './db/index.js';
import { openKv, closeKv } from './core/kv.js';
import { buildRouter } from './app.js';
import { createServer, listen } from './core/http.js';

const log = logger.child('main');

async function bootstrap() {
  log.info('服务启动中', {
    env: config.env,
    dialect: config.db.dialect,
    port: config.port,
    storage: config.storage.driver,
    watermarkFont: config.media.font || '(未配置，中文水印会渲染成方块)',
  });

  if (config.db.dialect === 'sqlite') {
    log.warn('当前使用 SQLite（本地/演示模式）。生产环境请把 DB_DIALECT 设为 mysql');
  }
  if (config.storage.driver === 'local') {
    log.warn('当前使用本地磁盘存储。生产建议 STORAGE_DRIVER=oss，成品不进服务器磁盘');
  }

  await openDb();
  await initSchema(await (async () => (await import('./db/index.js')).getDb())());
  await openKv();

  // 可选：单机开发时把媒体 worker 跑在同一进程（生产必须独立进程）
  if (process.env.WORKER_IN_PROCESS === '1') {
    const { startWorker } = await import('./worker.js');
    await startWorker();
    log.warn('媒体 worker 已随 API 进程启动（WORKER_IN_PROCESS=1），生产环境请拆开');
  }

  const router = buildRouter();
  const server = createServer(router, { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  log.info('服务已就绪', { url: `http://${config.bindHost}:${address.port}` });

  const shutdown = async (signal) => {
    log.info('收到退出信号，开始优雅关闭', { signal });
    server.close();
    await closeKv().catch(() => {});
    await closeDb().catch(() => {});
    setTimeout(() => process.exit(0), 300);
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('unhandledRejection', (err) => log.error('未处理的 Promise 拒绝', { err }));
  process.on('uncaughtException', (err) => {
    log.error('未捕获异常，进程即将退出', { err });
    setTimeout(() => process.exit(1), 200);
  });

  return server;
}

bootstrap().catch((err) => {
  log.error('启动失败', { err });
  process.exit(1);
});

export { bootstrap };
