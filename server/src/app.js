/**
 * 路由装配：所有模块在这里挂载，index.js 只管启动。
 */
import { Router, serveStatic } from './core/http.js';
import fs from 'node:fs';
import path from 'node:path';
import config from './config.js';
import { logger } from './core/logger.js';
import { ERROR, ERROR_MSG, ApiError } from './core/errors.js';
import { authRouter } from './modules/auth/routes.js';
import { dwRouter, dwInternalRouter, fileRouter } from './modules/dw/routes.js';
import { configRouter } from './modules/config/routes.js';
import { dtwRouter } from './modules/dtw/routes.js';
import { upgradeRouter, upgradeFileRouter } from './modules/upgrade/routes.js';
import { ticketRouter } from './modules/ticket/routes.js';
import { feedbackRouter } from './modules/feedback/routes.js';
import { payRouter } from './modules/pay/routes.js';
import { adminRouter } from './modules/admin/routes.js';
import { devtestRouter } from './modules/devtest/routes.js';

const log = logger.child('app');

export function buildRouter() {
  const root = new Router();

  /* ---------------------------- 健康检查 ---------------------------- */
  // 不需要签名，供 Nginx / 宝塔监控 / UptimeRobot 探测（规划书 10.2）
  root.get('/healthz', async (ctx) => {
    const { getDb } = await import('./db/index.js');
    let dbOk = true;
    try {
      await getDb().get('SELECT 1 AS ok');
    } catch {
      dbOk = false;
    }
    ctx.json({
      code: dbOk ? ERROR.OK : ERROR.SERVER,
      msg: dbOk ? 'ok' : '数据库不可用',
      data: {
        service: 'doutu-server',
        env: config.env,
        version: '1.0.0',
        db: dbOk ? 'up' : 'down',
        storage: config.storage.driver,
        ts: Math.floor(Date.now() / 1000),
      },
    });
  });

  /* ---------------------------- 头像（公开只读） ---------------------------- */
  // 头像要能直接当图片地址用（客户端 BitmapFactory / 浏览器 img 都不带签名头），
  // 所以放在签名中间件之外：按用户 id 取图，带长缓存，换头像靠 URL 上的 ?v= 版本号失效。
  root.get('/avatar/:id', async (ctx) => {
    const { getDb } = await import('./db/index.js');
    const storage = await import('./modules/dw/storage.js');
    const userId = Number(String(ctx.params.id).replace(/\D/g, '')) || 0;
    const user = userId ? await getDb().get('SELECT `avatar` FROM `app_user` WHERE `id` = ?', [userId]) : null;
    if (!user || !user.avatar) {
      throw new ApiError(ERROR.NOT_FOUND, '没有设置头像');
    }
    const buffer = await storage.readFile(user.avatar);
    // 先标记「这次响应已经发出去了」再写：框架在路由链结束后会检查 ctx.sent，
    // 没标记的话它会再发一次 JSON 信封 → 每次取头像都抛 ERR_HTTP_HEADERS_SENT
    // （图片本身还是能拿到，但服务端日志每次都被灌一条「未捕获异常」）。
    ctx.sent = true;
    ctx.res.writeHead(200, {
      'Content-Type': storage.contentTypeOf(user.avatar),
      'Content-Length': buffer.length,
      'Cache-Control': 'public, max-age=604800',
    });
    ctx.res.end(buffer);
  });

  /* ---------------------------- 业务模块 ---------------------------- */
  root.mount(authRouter);
  root.mount(dwRouter);
  root.mount(dwInternalRouter);
  root.mount(fileRouter);
  root.mount(configRouter);
  // 私有传输协议 DTW/1：握手端点（隧道本身在 core/http.js 里拦，不走路由）
  root.mount(dtwRouter);
  root.mount(upgradeRouter);
  // 升级包静态下载（/upgrade/app/xxx.apk）：本部署里 Nginx 把 /upgrade/ 反代给了 Node，
  // 不接这一段的话后台填的下载地址是下不动的，详见 modules/upgrade/routes.js
  root.mount(upgradeFileRouter);
  root.mount(ticketRouter);
  root.mount(feedbackRouter);
  root.mount(payRouter);
  root.mount(adminRouter);

  // 内网真机联调端点：**只在非生产环境挂载**（生产环境这条路径完全不存在）
  if (!config.isProd) {
    root.mount(devtestRouter);
    log.warn('已挂载 /devtest 内网联调端点（仅开发环境；生产环境不会挂载）');
  }

  // 后台前端（静态）：入口路径由 ADMIN_PATH 决定（默认 /admin）
  const adminDir = path.join(config.rootDir, 'web', 'admin');
  const adminPath = config.security.adminPath;
  if (fs.existsSync(adminDir)) {
    const serveAdmin = serveStatic(adminDir);
    // 必须先补上结尾斜杠再加载页面：index.html 里用的是相对路径（app.js / style.css），
    // 浏览器以「最后一个 / 之后」为基准解析，直接在 /admin 打开会把资源解析成 /style.css。
    // 注意：路径匹配会过滤空段，/admin 与 /admin/ 落进同一个 pattern，
    // 所以这里必须自己判断是否已带斜杠，否则会自我重定向成死循环。
    root.get(adminPath, async (ctx) => {
      if (!ctx.pathname.endsWith('/')) {
        ctx.redirect(`${adminPath}/`, 302);
        return;
      }
      await serveAdmin(ctx);
    });
    root.get(`${adminPath}/*`, serveAdmin);
  }

  log.info('路由装配完成', { routes: root.routes.length, adminPath });
  return root;
}

export default { buildRouter };
