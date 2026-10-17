/**
 * 去水印业务接口（规划书 8.2 业务类）+ 成品下载。
 *
 * 成品下载走签名 URL（私有桶语义）：/dw/file/{key}?exp=...&sign=...
 * 这个地址不带客户端签名（Android 播放器/下载器直接取），但有效期只有 15 分钟，
 * 且 sign 与 key 绑定，改一个字符就失效 —— 公网拿到链接也没法长期白用。
 */
import fs from 'node:fs';
import { Router } from '../../core/http.js';
import config from '../../config.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { clientAuth } from '../../middleware/clientAuth.js';
import { logger } from '../../core/logger.js';
import { pageParams } from '../../core/util.js';
import { mimeOf } from '../../core/http.js';
import * as taskService from './taskService.js';
import * as storage from './storage.js';
import * as quotaService from '../../services/quota.js';
import * as userService from '../../services/user.js';
import { getBool, getInt, getJson } from '../../services/sysconfig.js';

const log = logger.child('dw:routes');

export const dwRouter = new Router({ prefix: '/api/v1/dw' });

/**
 * 解析功能是否只给会员用。
 * 默认**是**（配置 `parse_member_only=1`）：未开通会员 → `2001 账号未开通会员`，
 * 客户端据此弹「开通会员」引导。改成 0 就退回「未开通也能用、成品带水印」。
 */
async function assertParseAllowed(user) {
  const memberOnly = await getBool('parse_member_only', true);
  if (!memberOnly) return;
  const status = userService.effectiveStatus(user);
  if (status === userService.STATUS.NORMAL) return;
  throw new ApiError(ERROR.NOT_ACTIVATED, '去水印是会员功能，请先联系客服开通会员');
}

/**
 * POST /api/v1/dw/parse —— 提交解析任务
 * 请求：{ share_text | url, want_watermark }
 * 响应：{ task_no, status, platform, estimate_sec, quota_left }
 *
 * ⚠️ 解析功能默认**仅会员可用**（配置 `parse_member_only=1`）：
 * 未开通会员会返回 `2001`，客户端据此引导开通。把这配置改成 0 就退回
 * 「未开通也能用、只是成品带水印」的模式。
 */
dwRouter.post(
  '/parse',
  clientAuth({ mode: 'session', requireActive: false, rate: { bucket: 'dw', ipLimit: 60, userLimit: 30, deviceLimit: 40, windowSec: 60 } }),
  async (ctx) => {
    await assertParseAllowed(ctx.state.user);

    const shareText = ctx.input('share_text', '') || ctx.input('link', '') || '';
    const url = ctx.input('url', null);
    const wantWatermark = ctx.input('want_watermark', true);

    const { task, reused, estimate_sec, quota_left } = await taskService.createTask({
      user: ctx.state.user,
      shareText,
      url,
      wantWatermark: wantWatermark === true || wantWatermark === 'true' || wantWatermark === 1 || wantWatermark === '1',
      ip: ctx.ip,
      deviceId: ctx.state.deviceId,
      priority: Number(ctx.state.user.level) >= 3 ? 'high' : 'normal',
    });

    ctx.json({
      code: ERROR.OK,
      msg: reused ? '已复用缓存结果' : 'ok',
      data: {
        task_no: task.task_no,
        status: Number(task.status),
        platform: task.platform,
        estimate_sec: reused ? 0 : estimate_sec,
        reused,
        quota_left,
      },
    });
  },
);

/** GET /api/v1/dw/task/{task_no} —— 查询任务状态与成品地址 */
dwRouter.get(
  '/task/:task_no',
  clientAuth({ mode: 'session', requireActive: false, rate: false }),
  async (ctx) => {
    const task = await taskService.getTask(ctx.params.task_no, ctx.state.user.id);
    const quota = await quotaService.quotaInfo(ctx.state.user);
    ctx.json({ code: ERROR.OK, msg: 'ok', data: { ...taskService.taskPayload(task), quota_left: quota.left, unlimited: quota.unlimited } });
  },
);

/** POST /api/v1/dw/batch —— 批量解析（限高等级用户） */
dwRouter.post(
  '/batch',
  clientAuth({ mode: 'session', rate: { bucket: 'dw-batch', ipLimit: 20, userLimit: 10, windowSec: 60 } }),
  async (ctx) => {
    const links = ctx.input('links', []);
    const list = Array.isArray(links) ? links : String(links || '').split(/[\n,]+/).filter(Boolean);
    if (!list.length) throw new ApiError(ERROR.PARAM, '请提供待解析的链接列表');
    const results = await taskService.createBatchTasks({
      user: ctx.state.user,
      links: list,
      wantWatermark: ctx.input('want_watermark', true) !== false,
      ip: ctx.ip,
      deviceId: ctx.state.deviceId,
    });
    const quota = await quotaService.quotaInfo(ctx.state.user);
    ctx.json({ code: ERROR.OK, msg: 'ok', data: { results, quota_left: quota.left, unlimited: quota.unlimited } });
  },
);

/** GET /api/v1/dw/history —— 历史记录（分页） */
dwRouter.get('/history', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20, 50);
  const result = await taskService.listHistory({
    userId: ctx.state.user.id,
    status: ctx.query.status,
    page,
    limit,
  });
  ctx.json({ code: ERROR.OK, msg: 'ok', data: result });
});

/** DELETE /api/v1/dw/task/{task_no} —— 删除任务记录 */
dwRouter.delete(
  '/task/:task_no',
  clientAuth({ mode: 'session', requireActive: false, rate: false }),
  async (ctx) => {
    await taskService.deleteTask(ctx.params.task_no, ctx.state.user.id);
    ctx.json({ code: ERROR.OK, msg: '已删除', data: null });
  },
);

/** GET /api/v1/dw/platforms —— 支持的平台列表 */
dwRouter.get('/platforms', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  const data = await taskService.supportedPlatforms();
  const disabled = await getJson('disabled_platforms', []);
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: { ...data, disabled: Array.isArray(disabled) ? disabled : [] },
  });
});

/* ------------------------------------------------------------------ */
/*                          成品 / 原片下载                            */
/* ------------------------------------------------------------------ */

export const fileRouter = new Router({ prefix: '/dw/file' });

/**
 * GET /dw/file/{key}?exp=..&sign=..
 * key 里带斜杠（out/2026-02-14/xxx.mp4），所以用通配匹配；
 * 支持 Range 请求：Android 播放器拖动进度条依赖它。
 */
fileRouter.get('/*', async (ctx) => {
  const key = decodeURIComponent(ctx.params['*'] || '');
  if (!key || key.includes('..')) throw new ApiError(ERROR.PARAM, '非法路径');

  const check = storage.verifyLocalUrl(key, ctx.query.exp, ctx.query.sign);
  if (!check.ok) {
    log.warn('下载链接校验失败', { key, reason: check.reason, ip: ctx.ip });
    throw new ApiError(ERROR.SIGN, check.reason || '下载链接无效');
  }

  if (ctx.query.download === '1') {
    ctx.res.setHeader('Content-Disposition', `attachment; filename="${encodeURIComponent(key.split('/').pop())}"`);
  }

  const contentType = mimeOf(key);
  if (ctx.query.stat === '1') {
    const info = await storage.stat(key);
    ctx.json({ code: ERROR.OK, msg: 'ok', data: { key, size: info?.size || 0, exists: Boolean(info) } });
    return;
  }

  if (await storage.exists(key) === false) throw new ApiError(ERROR.NOT_FOUND, '文件不存在或已清理');

  const filePath = storage.localPathOf(key);
  const stat = fs.statSync(filePath);
  const range = ctx.get('range');

  if (range) {
    const match = /bytes=(\d*)-(\d*)/.exec(range);
    if (match) {
      const start = match[1] ? Number(match[1]) : 0;
      const end = match[2] ? Number(match[2]) : stat.size - 1;
      if (start >= stat.size || end >= stat.size || start > end) {
        ctx.res.writeHead(416, { 'Content-Range': `bytes */${stat.size}` });
        ctx.res.end();
        ctx.sent = true;
        return;
      }
      ctx.sent = true;
      ctx.res.writeHead(206, {
        'Content-Type': contentType,
        'Content-Length': end - start + 1,
        'Content-Range': `bytes ${start}-${end}/${stat.size}`,
        'Accept-Ranges': 'bytes',
        'Cache-Control': `private, max-age=${Math.max(0, Number(ctx.query.exp) - Math.floor(Date.now() / 1000))}`,
      });
      fs.createReadStream(filePath, { start, end }).pipe(ctx.res);
      return;
    }
  }

  ctx.sent = true;
  ctx.res.writeHead(200, {
    'Content-Type': contentType,
    'Content-Length': stat.size,
    'Accept-Ranges': 'bytes',
    'Cache-Control': `private, max-age=${Math.max(0, Number(ctx.query.exp) - Math.floor(Date.now() / 1000))}`,
  });
  fs.createReadStream(filePath).pipe(ctx.res);
});

/** 媒体环境自检（后台「面板管理 → 环境自检」用，也可以直接 curl） */
export const dwInternalRouter = new Router({ prefix: '/api/v1/dw' });
dwInternalRouter.get('/env', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  const { checkEnvironment } = await import('./watermark.js');
  const env = await checkEnvironment();
  const stats = await taskService.queueStats();
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      ffmpeg: env,
      queue: stats,
      storage_driver: config.storage.driver,
      service_switch: (await getBool('service_switch', true)) ? 'on' : 'off',
      watermark_font: env.fontPath || null,
      watermark_enabled: await getBool('watermark_enabled', true),
      task_timeout_sec: await getInt('task_timeout_sec', 120),
    },
  });
});

export default dwRouter;
