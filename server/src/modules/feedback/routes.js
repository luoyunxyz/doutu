/**
 * 意见反馈 / 求新功能（App「我 → 求新功能」）。
 *
 * 与「客服工单」的区别：工单是**一来一回的会话**，反馈是**一次性提交**。
 * 用户点提交后只会收到一句感谢，不需要等回复，所以这里没有会话表，只有一张 feedback。
 *
 * 关于鉴权：用 root（MASTER_KEY）而不是 session —— 未登录用户也能提意见。
 * 带了 X-Token 就顺手把 user_id 记上（后台能看到是谁提的），没带也不影响提交。
 *
 * 图片走和头像同一条路：base64 塞在 JSON 里（全站都是 JSON + body_hash 签名，不引 multipart），
 * 服务端只认文件头，存进存储后把 key 数组写进 `feedback.images`。
 */
import { Router } from '../../core/http.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { clientAuth, headerToken, headerDeviceId } from '../../middleware/clientAuth.js';
import { getDb } from '../../db/index.js';
import { now, randomHex } from '../../core/util.js';
import { logger } from '../../core/logger.js';
import * as storage from '../dw/storage.js';
import { sniffImage } from '../../services/user.js';
import * as sessionService from '../../services/session.js';

const log = logger.child('feedback');

export const feedbackRouter = new Router({ prefix: '/api/v1/feedback' });

/** 一条反馈最多带几张图 / 单张多大（和客户端压缩后的尺寸对齐） */
const MAX_IMAGES = 3;
const MAX_IMAGE_BYTES = 1024 * 1024;
const MAX_MIN_IMAGE_BYTES = 24;

function safeParseArray(text) {
  try {
    const value = text ? JSON.parse(text) : [];
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

/** 把 data-URL 存进存储，返回存储 key；格式不认识就抛 PARAM。 */
async function storeImage(dataUrl, index) {
  const raw = String(dataUrl || '').replace(/^data:image\/[a-zA-Z+]+;base64,/, '');
  if (!raw) throw new ApiError(ERROR.PARAM, `第 ${index + 1} 张图片数据为空`);
  let buffer;
  try {
    buffer = Buffer.from(raw, 'base64');
  } catch {
    throw new ApiError(ERROR.PARAM, `第 ${index + 1} 张图片数据不完整，请重试`);
  }
  if (buffer.length < MAX_MIN_IMAGE_BYTES) throw new ApiError(ERROR.PARAM, `第 ${index + 1} 张图片内容太小`);
  if (buffer.length > MAX_IMAGE_BYTES) throw new ApiError(ERROR.PARAM, `第 ${index + 1} 张图片超过 1MB，请压缩后再传`);
  const kind = sniffImage(buffer);
  if (!kind) throw new ApiError(ERROR.PARAM, `第 ${index + 1} 张图片只支持 JPG / PNG / WebP / GIF`);
  const key = `feedback/${Date.now().toString(36)}-${randomHex(6)}.${kind.ext}`;
  await storage.putBuffer(key, buffer, kind.mime);
  return key;
}

/** 有会话就带上 user_id，没有也不报错。 */
async function optionalUserId(ctx) {
  const token = String(headerToken(ctx) || '').trim();
  if (!token) return null;
  try {
    const session = await sessionService.findByToken(token);
    if (!session || !sessionService.isSessionValid(session)) return null;
    return Number(session.user_id) || null;
  } catch {
    return null;
  }
}

/**
 * POST /api/v1/feedback/create
 * 请求：{ content, images?: ["data:image/jpeg;base64,..."], contact?, model?, os_version? }
 */
feedbackRouter.post(
  '/create',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'feedback', ipLimit: 12, deviceLimit: 12, windowSec: 3600 },
  }),
  async (ctx) => {
    const content = String(ctx.input('content', '') || '').trim();
    if (content.length < 2) throw new ApiError(ERROR.PARAM, '请填写至少 2 个字的意见或建议');
    if (content.length > 1000) throw new ApiError(ERROR.PARAM, '意见最多 1000 字');

    const rawImages = ctx.input('images', []);
    const list = Array.isArray(rawImages) ? rawImages.filter((s) => String(s || '').trim()) : [];
    if (list.length > MAX_IMAGES) throw new ApiError(ERROR.PARAM, `最多上传 ${MAX_IMAGES} 张图片`);

    const keys = [];
    for (let i = 0; i < list.length; i += 1) {
      keys.push(await storeImage(list[i], i));
    }

    const ts = now();
    const userId = await optionalUserId(ctx);
    const deviceId = headerDeviceId(ctx) || ctx.state.deviceId || null;
    const result = await getDb().insert('feedback', {
      user_id: userId,
      content: content.slice(0, 2000),
      images: JSON.stringify(keys),
      contact: String(ctx.input('contact', '') || '').trim().slice(0, 64) || null,
      device_id: deviceId ? String(deviceId).slice(0, 64) : null,
      app_version: ctx.state.appVersion ? String(ctx.state.appVersion).slice(0, 32) : null,
      version_code: Number(ctx.state.versionCode) || 0,
      model: String(ctx.input('model', '') || '').trim().slice(0, 64) || null,
      os_version: String(ctx.input('os_version', '') || '').trim().slice(0, 32) || null,
      status: 0,
      admin_id: null,
      handled_at: null,
      remark: null,
      created_at: ts,
      updated_at: ts,
    });

    log.info('收到意见反馈', { id: result.insertId, userId, images: keys.length, length: content.length });
    ctx.json({
      code: ERROR.OK,
      msg: '感谢您的反馈，我们会认真阅读每一条建议',
      data: { id: result.insertId, images: keys.length },
    });
  },
);

/**
 * GET /api/v1/feedback/image/:name
 * 后台列表里 <img src> 直接引用（浏览器不会带 Authorization 头），所以放在签名中间件之外：
 * 文件名里带 6 位随机 hex，不可枚举；只允许读 feedback/ 目录下的图片。
 */
feedbackRouter.get('/image/:name', async (ctx) => {
  const name = String(ctx.params.name || '');
  if (!/^[A-Za-z0-9_-]+\.(jpg|jpeg|png|webp|gif)$/i.test(name)) {
    throw new ApiError(ERROR.NOT_FOUND, '图片不存在');
  }
  const key = `feedback/${name}`;
  let buffer;
  try {
    buffer = await storage.readFile(key);
  } catch {
    throw new ApiError(ERROR.NOT_FOUND, '图片不存在');
  }
  ctx.sent = true;
  ctx.res.writeHead(200, {
    'Content-Type': storage.contentTypeOf(key),
    'Content-Length': buffer.length,
    'Cache-Control': 'private, max-age=86400',
  });
  ctx.res.end(buffer);
});

export default feedbackRouter;
