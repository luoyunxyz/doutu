/**
 * 内网真机联调端点（**仅非生产环境挂载**）。
 *
 * 用途：让内网里的真机（手机浏览器/WebView）直接当客户端，用与服务端完全一致的
 * 签名算法跑一遍「激活 → 解析 → 轮询 → 下载成品」，验证客户端契约在真机上成立。
 *
 * 安全边界：
 *  - `config.isProd` 为真时不挂载这些路由，生产环境不存在这套接口；
 *  - bootstrap 会下发 MASTER_KEY，因此绝不能在公网环境开放；
 *  - 每次请求 bootstrap 会现开一张小配额卡密，方便反复测试。
 */
import fs from 'node:fs';
import path from 'node:path';
import { Router, serveStatic } from '../../core/http.js';
import config from '../../config.js';
import { logger } from '../../core/logger.js';
import { ERROR, ApiError } from '../../core/errors.js';
import { getDb } from '../../db/index.js';
import { now } from '../../core/util.js';
import * as inviteService from '../../services/invite.js';
import { masterKeyEffective } from '../../config.js';

const log = logger.child('devtest');

export const devtestRouter = new Router({ prefix: '/devtest' });

const DEV_DIR = path.join(config.rootDir, 'public', 'devtest');

/** 保证存在一条指向本机样本视频的解析线路（幂等） */
export async function ensureSampleRoute(port) {
  const db = getDb();
  const name = '内网联调样本线路';
  const videoUrl = `http://127.0.0.1:${port}/devtest/sample.mp4`;
  const existing = await db.get('SELECT `id` FROM `dw_route` WHERE `name` = ?', [name]);
  const data = {
    platform: 'all',
    name,
    type: 1,
    endpoint: 'native:mock',
    config: JSON.stringify({ videoUrl, title: '内网联调样本', itemId: 'devtest-item' }),
    priority: 1,
    enabled: 1,
    updated_at: now(),
  };
  if (existing) {
    await db.update('dw_route', { id: existing.id }, data);
    return { id: existing.id, videoUrl };
  }
  const created = await db.insert('dw_route', { ...data, created_at: now() });
  log.warn('已写入内网联调样本线路（仅开发环境）', { videoUrl });
  return { id: created.insertId, videoUrl };
}

devtestRouter.get('/bootstrap', async (ctx) => {
  if (config.isProd) throw new ApiError(ERROR.NOT_FOUND, '接口不存在');
  const port = ctx.req.socket.localPort;
  const { videoUrl } = await ensureSampleRoute(port);

  // 现开一个邀请码（duration=0 ⇒ 注册后仍需后台激活，正好用来验「未激活水印」这条链路）
  const batch = await inviteService.createBatch({
    count: 1,
    duration: 0,
    level: 3,
    maxDevice: 3,
    quotaDay: 30,
    remark: `devtest bootstrap @${new Date().toISOString()}`,
    adminId: null,
  });

  log.warn('内网联调 bootstrap 被调用', { ip: ctx.ip, ua: ctx.ua, port });
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      api_base: `http://${ctx.get('host')}`,
      master_key: masterKeyEffective(),
      invite_code: batch.codes[0],
      invite_display: inviteService.formatInviteCode(batch.codes[0]),
      sample_url: videoUrl,
      sample_public_url: `http://${ctx.get('host')}/devtest/sample.mp4`,
      note: '仅开发/内网联调使用：会下发 MASTER_KEY 与一个测试邀请码（一码只能建一个账号）',
    },
  });
});

/** 静态资源：index.html / sample.mp4 */
devtestRouter.get('/*', (ctx, next) => {
  if (config.isProd) throw new ApiError(ERROR.NOT_FOUND, '接口不存在');
  if (!fs.existsSync(DEV_DIR)) throw new ApiError(ERROR.NOT_FOUND, '联调目录不存在');
  return serveStatic(DEV_DIR)(ctx, next);
});

export default devtestRouter;
