/**
 * 客户端授权接口（规划书 8.2 授权类）。
 *
 * 引导阶段的四个接口（activate / login / handshake / refresh）用 MASTER_KEY 验签：
 * 客户端此时还没有会话密钥，而 MASTER_KEY 在 so 里，配合服务端校验设备与账号状态，
 * 攻击者即便逆出 MASTER_KEY 也拿不到「有效账号 + 已授权设备」的会话（规划书 7.3）。
 * 其余接口一律用会话级 sign_key。
 */
import { Router } from '../../core/http.js';
import config from '../../config.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { sha256Hex, normalizeDeviceId } from '../../core/crypto.js';
import { kv } from '../../core/kv.js';
import { logger } from '../../core/logger.js';
import { getInt, getBool } from '../../services/sysconfig.js';
import { verifyPassword } from '../../core/crypto.js';
import { now, randomHex } from '../../core/util.js';
import {
  clientAuth,
  activateGuard,
  recordActivateFailure,
  clearActivateFailure,
  recordRisk,
  detectMultiAccount,
  headerDeviceId,
} from '../../middleware/clientAuth.js';
import * as inviteService from '../../services/invite.js';
import * as userService from '../../services/user.js';
import * as sessionService from '../../services/session.js';
import * as quotaService from '../../services/quota.js';
import * as storage from '../dw/storage.js';
import { writeLoginLog } from '../../services/loginLog.js';
import { getDb } from '../../db/index.js';

const log = logger.child('auth:routes');

export const authRouter = new Router({ prefix: '/api/v1/auth' });

/**
 * 设备号一律归一化（去空白 + 小写）后再用。
 * 客户端用 X-Device-Id 头签名，服务端必须用**同一个**归一化结果参与验签与查库，
 * 否则大小写不同就会被判成两台设备。
 */
const resolveDeviceId = (ctx) => headerDeviceId(ctx) || normalizeDeviceId(ctx.input('device_id', ''));

/** 激活成功后统一的返回体 */
async function sessionPayload(ctx, user, session, extra = {}) {
  const quota = await quotaService.quotaInfo(user);
  return {
    token: session.token,
    refresh: session.refresh,
    sign_key: session.sign_key,
    expire_at: session.expire_at,
    expire_in: session.expire_in,
    server_ts: Math.floor(Date.now() / 1000),
    user: userService.authStatusPayload(user, { quota_left: quota.left, quota_used: quota.used, unlimited: quota.unlimited }),
    ...extra,
  };
}

/**
 * POST /api/v1/auth/register —— 邀请码注册（新模型：一个邀请码只能创建一个账号）
 *
 * 请求：{ invite_code, username, phone, password, password_confirm, nickname? }
 * `username` 是**必填**的登录标识（2~20 位，中英文/数字/下划线/短横线，不能长得像手机号）；
 * 注册结果默认是**未开通会员**，可以直接使用（界面与成品带水印），开通由后台决定。
 *
 * 旧的「卡密激活」接口已按需求移除：客户端不再有卡密入口。
 */
authRouter.post(
  '/register',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'register', ipLimit: 20, windowSec: 60, userLimit: 0, deviceLimit: 0 },
  }),
  async (ctx) => {
    const registerOpen = await getBool('register_open', true);
    if (!registerOpen) throw new ApiError(ERROR.BUSY, '注册通道已关闭，请联系客服开通账号');

    const inviteCode = String(ctx.input('invite_code', '')).trim();
    const username = String(ctx.input('username', '')).trim();
    const phone = String(ctx.input('phone', '')).trim();
    const password = String(ctx.input('password', ''));
    const passwordConfirm = String(ctx.input('password_confirm', ctx.input('password2', '')));
    const deviceId = resolveDeviceId(ctx);
    if (!deviceId) throw new ApiError(ERROR.PARAM, '缺少设备标识');

    // 复用激活防爆破：防止有人拿邀请码字典试探
    await activateGuard(ctx.ip);

    let result;
    try {
      result = await inviteService.registerWithInvite({
        code: inviteCode,
        username,
        phone,
        password,
        passwordConfirm,
        nickname: ctx.input('nickname', null),
        ip: ctx.ip,
        ua: ctx.ua,
        deviceId,
      });
    } catch (err) {
      const count = await recordActivateFailure(ctx.ip);
      await writeLoginLog({
        userType: 1,
        username: phone ? `${phone.slice(0, 3)}****` : null,
        ip: ctx.ip,
        ua: ctx.ua,
        deviceId,
        status: 0,
        msg: `注册失败(${count}): ${err.msg || err.message}`,
      });
      throw err;
    }
    await clearActivateFailure(ctx.ip);

    // 同一设备近期出现过别的账号 → 记风控（不阻断注册，只留痕）
    const multi = await detectMultiAccount(deviceId, result.user.id);
    if (multi) {
      await recordRisk({
        type: 'multi_account',
        userId: result.user.id,
        deviceId,
        level: 2,
        detail: { action: 'register' },
        ip: ctx.ip,
      });
    }

    // 绑定设备（未激活也先绑上，避免激活后又要用户再操作一次）
    await userService
      .bindDevice(result.user.id, deviceId, {
        brand: ctx.input('brand', null),
        model: ctx.input('model', null),
        osVersion: ctx.input('os_version', null),
        appVersion: ctx.state.appVersion,
      })
      .catch((err) => log.warn('注册后绑定设备失败', { userId: result.user.id, err: err.msg || err.message }));

    const session = await sessionService.createSession(null, {
      user: result.user,
      deviceId,
      ip: ctx.ip,
      ua: ctx.ua,
      appVersion: ctx.state.appVersion,
      osVersion: ctx.input('os_version', null),
    });

    await writeLoginLog({
      userType: 1,
      userId: result.user.id,
      username: result.user.username,
      ip: ctx.ip,
      ua: ctx.ua,
      deviceId,
      status: 1,
      msg: result.activated ? '邀请码注册并开通' : '邀请码注册（未开通会员，可用但带水印）',
    });

    log.info('邀请码注册', { userId: result.user.id, activated: result.activated });

    const fresh = await userService.findById(result.user.id);
    ctx.json({
      code: ERROR.OK,
      msg: result.activated ? '注册成功，会员已开通' : '注册成功，可直接使用（未开通会员，成品会带水印）',
      data: await sessionPayload(ctx, fresh, session, {
        activated: result.activated,
        invite: { batch_no: result.invite.batch_no, code_masked: `****${String(result.invite.code).slice(-4)}` },
      }),
    });
  },
);

/**
 * 已移除：POST /api/v1/auth/activate（卡密激活）。
 * 旧的卡密体系被邀请码 + 后台激活取代；这里显式返回 404 语义，
 * 避免老客户端拿到含糊的成功响应。
 */
authRouter.post('/activate', clientAuth({ mode: 'none', requireActive: false, rate: false }), async () => {
  throw new ApiError(ERROR.NOT_FOUND, '卡密激活已停用，请使用邀请码注册，或联系客服开通');
});

/* ==================== 邀请码找回密码（2026-10-17） ====================
 *
 * 一个邀请码只能注册一个账号，注册时 `invite_code.used_by / used_phone` 就把账号和码钉死了。
 * 所以「注册手机号 + 邀请码」凑齐就等于证明是号主本人，不需要短信验证码：
 *
 *   1) POST /api/v1/auth/invite/verify  手机号 + 邀请码 → 核验通过发一张 10 分钟的一次性票据
 *   2) POST /api/v1/auth/invite/reset   票据 + 新密码（两次）→ 换掉密码，旧密码立刻作废
 *
 * 两步分开是有意的：邀请码只在第一步出现一次，第二步只认票据，
 * 这样票据被截获也不能拿来再核验，而且核验和改密之间可以隔一个界面。
 */

/** 票据在 KV 里的键；10 分钟够用户慢慢输两次密码。 */
const PWD_RESET_TTL = 600;
const pwdResetKey = (token) => `pwd:reset:${token}`;

authRouter.post(
  '/invite/verify',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'pwdreset', ipLimit: 20, windowSec: 600, userLimit: 0, deviceLimit: 0 },
  }),
  async (ctx) => {
    const phone = String(ctx.input('phone', '')).trim();
    const code = String(ctx.input('invite_code', '')).trim();

    // 复用激活防爆破：防止有人拿手机号 + 邀请码字典来回试
    await activateGuard(ctx.ip);

    let found;
    try {
      found = await inviteService.findUserByInviteAndPhone(code, phone);
    } catch (err) {
      const count = await recordActivateFailure(ctx.ip);
      await writeLoginLog({
        userType: 1,
        username: phone ? `${phone.slice(0, 3)}****` : null,
        ip: ctx.ip,
        ua: ctx.ua,
        deviceId: resolveDeviceId(ctx),
        status: 0,
        msg: `邀请码找回密码核验失败(${count}): ${err.msg || err.message}`,
      });
      throw err;
    }
    await clearActivateFailure(ctx.ip);

    const token = randomHex(24);
    await kv().set(pwdResetKey(token), String(found.user.id), PWD_RESET_TTL);

    log.info('邀请码找回密码：核验通过', { userId: found.user.id });

    ctx.json({
      code: ERROR.OK,
      msg: '核验通过，请设置新密码',
      data: {
        reset_token: token,
        expires_in: PWD_RESET_TTL,
        // 让界面能显示「正在为 xxx 重置密码」，用户名不是敏感信息
        username: found.user.username || '',
        phone_masked: phone ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : '',
      },
    });
  },
);

authRouter.post(
  '/invite/reset',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'pwdreset2', ipLimit: 30, windowSec: 600, userLimit: 0, deviceLimit: 0 },
  }),
  async (ctx) => {
    const token = String(ctx.input('reset_token', '')).trim();
    const password = String(ctx.input('password', ''));
    const confirm = String(ctx.input('password_confirm', ctx.input('password2', '')));

    if (!token) throw new ApiError(ERROR.PARAM, '缺少核验票据，请先填写手机号和邀请码');
    if (password.length < 6) throw new ApiError(ERROR.PARAM, '密码至少 6 位');
    if (password !== confirm) throw new ApiError(ERROR.PARAM, '两次输入的密码不一致');

    const key = pwdResetKey(token);
    const raw = await kv().get(key);
    const userId = Number(raw) || 0;
    if (!userId) throw new ApiError(ERROR.PARAM, '核验已过期，请重新填写手机号和邀请码');

    await userService.setPassword(userId, password, { reason: 'invite_reset' });
    // 票据一次性：用完立刻删，避免同一张票反复改密
    await kv().del(key);
    // 旧密码作废的同时把已经登录的会话全部吊销 —— 号主自己也是重新登录一次
    await sessionService.revokeAllForUser(userId).catch((err) => {
      log.warn('重置密码后吊销旧会话失败', { userId, err: err.msg || err.message });
    });

    await writeLoginLog({
      userType: 1,
      userId,
      ip: ctx.ip,
      ua: ctx.ua,
      deviceId: resolveDeviceId(ctx),
      status: 1,
      msg: '邀请码找回密码：密码已重置',
    });
    log.info('邀请码找回密码：密码已重置', { userId });

    ctx.json({ code: ERROR.OK, msg: '密码已重置，请用新密码登录', data: null });
  },
);


/**
 * POST /api/v1/auth/login —— 已注册用户账密登录
 *
 * 账号标识**手机号或用户名都可以**（`username` 字段传任意一种；`phone` 作为兼容别名）。
 * 用户名忽略大小写，且按规则不允许长得像手机号，所以两个命名空间不会撞车。
 */
authRouter.post(
  '/login',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'login', ipLimit: 30, windowSec: 60, userLimit: 0, deviceLimit: 0 },
  }),
  async (ctx) => {
    const account = String(ctx.input('username', '') || ctx.input('phone', '')).trim();
    const password = String(ctx.input('password', ''));
    const deviceId = resolveDeviceId(ctx);
    if (!account || !password) throw new ApiError(ERROR.PARAM, '请输入账号与密码');

    const user = await userService.findByLogin(account);
    const okPassword = user && user.password_hash ? verifyPassword(user.password_hash, password) : false;
    if (!okPassword) {
      await writeLoginLog({ userType: 1, username: account, ip: ctx.ip, ua: ctx.ua, deviceId, status: 0, msg: '账号或密码错误' });
      throw new ApiError(ERROR.PARAM, '账号或密码错误');
    }
    if (Number(user.status) === userService.STATUS.BANNED) throw new ApiError(ERROR.BANNED);

    if (deviceId) {
      await userService.bindDevice(user.id, deviceId, {
        brand: ctx.input('brand', null),
        model: ctx.input('model', null),
        osVersion: ctx.input('os_version', null),
        appVersion: ctx.state.appVersion,
      });
    }

    const session = await sessionService.createSession(null, {
      user,
      deviceId,
      ip: ctx.ip,
      ua: ctx.ua,
      appVersion: ctx.state.appVersion,
      osVersion: ctx.input('os_version', null),
    });
    await writeLoginLog({
      userType: 1,
      userId: user.id,
      username: user.username,
      ip: ctx.ip,
      ua: ctx.ua,
      deviceId,
      status: 1,
      msg: '登录成功',
    });
    ctx.json({ code: ERROR.OK, msg: '登录成功', data: await sessionPayload(ctx, user, session) });
  },
);

/**
 * POST /api/v1/auth/handshake —— 换发会话级动态密钥
 * session_sign_key = HMAC(MASTER_KEY, server_random + nonce + device_id)
 */
authRouter.post(
  '/handshake',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'handshake', ipLimit: 60, windowSec: 60, userLimit: 0, deviceLimit: 0 },
  }),
  async (ctx) => {
    const deviceId = resolveDeviceId(ctx);
    if (!deviceId) throw new ApiError(ERROR.PARAM, '缺少设备标识');

    const db = getDb();
    // 取该设备最近一条未撤销的会话
    let session = await db.get(
      'SELECT * FROM `license_session` WHERE `device_id` = ? AND `revoked` = 0 ORDER BY `id` DESC LIMIT 1',
      [deviceId],
    );
    if (!session) {
      // 兼容「会话已被清理但设备仍授权」的情况：按设备反查用户，重建会话
      const user = await userService.findByDevice(deviceId);
      if (!user) throw new ApiError(ERROR.SESSION_INVALID, '设备未授权，请重新激活');
      userService.assertUsable(user);
      session = await sessionService.createSession(null, {
        user,
        deviceId,
        ip: ctx.ip,
        ua: ctx.ua,
        appVersion: ctx.state.appVersion,
      });
    } else {
      const user = await userService.findById(session.user_id);
      if (!user) throw new ApiError(ERROR.SESSION_INVALID, '账号不存在');
      if (Number(session.revoked) === 1) throw new ApiError(ERROR.SESSION_INVALID);
      userService.assertUsable(user);
    }

    const result = await sessionService.handshake({
      session,
      deviceId,
      nonce: ctx.state.nonce,
    });
    const fresh = await sessionService.findByToken(session.token);
    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: {
        server_random: result.server_random,
        sign_key: result.sign_key,
        token: session.token,
        server_ts: Math.floor(Date.now() / 1000),
        sign_expire_at: fresh?.expire_at || session.expire_at,
      },
    });
  },
);

/**
 * POST /api/v1/auth/refresh —— 刷新 token
 */
authRouter.post(
  '/refresh',
  clientAuth({
    mode: 'root',
    requireActive: false,
    rate: { bucket: 'refresh', ipLimit: 60, windowSec: 60, userLimit: 0, deviceLimit: 0 },
  }),
  async (ctx) => {
    const refresh = String(ctx.input('refresh', '')).trim();
    if (!refresh) throw new ApiError(ERROR.PARAM, '缺少 refresh');
    const result = await sessionService.refreshSession(refresh, {
      ip: ctx.ip,
      ua: ctx.ua,
      appVersion: ctx.state.appVersion,
    });
    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: { ...result, server_ts: Math.floor(Date.now() / 1000) },
    });
  },
);

/**
 * POST /api/v1/auth/logout
 */
authRouter.post('/logout', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  await sessionService.revokeByToken(ctx.state.session.token);
  ctx.json({ code: ERROR.OK, msg: '已退出登录', data: null });
});

/**
 * POST /api/v1/auth/heartbeat —— 续期会话 + 上报在线
 */
authRouter.post(
  '/heartbeat',
  clientAuth({ mode: 'session', requireActive: false, rate: { bucket: 'hb', userLimit: 120, windowSec: 60 } }),
  async (ctx) => {
    const result = await sessionService.heartbeat(ctx.state.session, {
      ip: ctx.ip,
      ua: ctx.ua,
      appVersion: ctx.state.appVersion,
    });
    const quota = await quotaService.quotaInfo(ctx.state.user);
    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: {
        ...result,
        server_ts: Math.floor(Date.now() / 1000),
        user: userService.authStatusPayload(ctx.state.user, { quota_left: quota.left, quota_used: quota.used, unlimited: quota.unlimited }),
      },
    });
  },
);

/**
 * POST /api/v1/auth/unbind —— 解绑设备（每月限次，防止一号多机共享）
 */
authRouter.post('/unbind', clientAuth({ mode: 'session', rate: false }), async (ctx) => {
  const target = resolveDeviceId(ctx) || ctx.state.deviceId;
  const monthlyLimit = await getInt('unbind_limit_month', 2);
  const key = `unbind:${ctx.state.user.id}:${new Date().toISOString().slice(0, 7)}`;
  const used = Number((await kv().get(key)) || 0);
  if (monthlyLimit > 0 && used >= monthlyLimit) {
    throw new ApiError(ERROR.RATE_LIMIT, `本月解绑次数已用完（${monthlyLimit} 次），请联系客服`);
  }

  await userService.unbindDevice(ctx.state.user.id, target);
  await kv().incr(key, 32 * 86400);
  log.info('设备解绑', { userId: ctx.state.user.id, device: String(target).slice(0, 8) });
  ctx.json({
    code: ERROR.OK,
    msg: '已解绑',
    data: { device_id: target, unbind_left: Math.max(0, monthlyLimit - used - 1) },
  });
});

/**
 * POST /api/v1/auth/profile —— 修改账号资料（目前支持用户名，后续可扩展昵称等）
 *
 * 请求：{ username, nickname? }
 * 会话签名。改名成功后：
 *   - 新旧用户名立即可用于登录（登录按用户名忽略大小写查）；
 *   - 手机号登录完全不受影响；
 *   - 服务端写 `user_profile_log` 留痕。
 */
authRouter.post(
  '/profile',
  clientAuth({ mode: 'session', requireActive: false, requireDevice: false, rate: { bucket: 'profile', ipLimit: 0, windowSec: 60, userLimit: 10, deviceLimit: 0 } }),
  async (ctx) => {
    const username = String(ctx.input('username', '')).trim();
    if (!username) throw new ApiError(ERROR.PARAM, '请输入新的用户名');
    const updated = await userService.changeUsername(ctx.state.user.id, username, {
      adminId: null,
      nickname: ctx.input('nickname', undefined),
    });
    await writeLoginLog({
      userType: 1,
      userId: updated.id,
      username: updated.username,
      ip: ctx.ip,
      ua: ctx.ua,
      deviceId: ctx.state.deviceId,
      status: 1,
      msg: `用户名修改为 ${updated.username}`,
    });
    const quota = await quotaService.quotaInfo(updated);
    ctx.json({
      code: ERROR.OK,
      msg: '用户名已修改',
      data: {
        user: userService.authStatusPayload(updated, { quota_left: quota.left, quota_used: quota.used, unlimited: quota.unlimited }),
      },
    });
  },
);

/**
 * POST /api/v1/auth/avatar —— 换头像 / 清空头像
 *
 * 请求：{ image_base64: "data:image/jpeg;base64,...", } 或 { remove: true }
 * 会话签名。图片以 base64 塞在 JSON 里（服务端所有接口都是「JSON + body_hash 签名」这一套，
 * 不引 multipart，签名链路保持一致）。服务端只认 JPG/PNG/WebP/GIF 的文件头，
 * 上限 2MB，存进存储后把 key 写进 `app_user.avatar`，客户端拿 `avatar_url` 去读。
 */
authRouter.post(
  '/avatar',
  clientAuth({ mode: 'session', requireActive: false, requireDevice: false, rate: { bucket: 'avatar', ipLimit: 0, windowSec: 3600, userLimit: 30, deviceLimit: 0 } }),
  async (ctx) => {
    const userId = ctx.state.user.id;
    const remove = ctx.input('remove', false) === true || String(ctx.input('remove', '')) === 'true';

    let avatarKey = null;
    if (remove) {
      await userService.setAvatar(userId, null, { removeFile: storage.remove });
    } else {
      const raw = String(ctx.input('image_base64', '') || '').replace(/^data:image\/[a-zA-Z+]+;base64,/, '');
      if (!raw) throw new ApiError(ERROR.PARAM, '请选择要上传的图片');
      let buffer;
      try {
        buffer = Buffer.from(raw, 'base64');
      } catch {
        throw new ApiError(ERROR.PARAM, '图片数据不完整，请重试');
      }
      if (buffer.length < userService.AVATAR_MIN_BYTES) throw new ApiError(ERROR.PARAM, '图片内容太小，请换一张');
      if (buffer.length > userService.AVATAR_MAX_BYTES) throw new ApiError(ERROR.PARAM, '图片不能超过 1MB，请压缩后再传');
      const kind = userService.sniffImage(buffer);
      if (!kind) throw new ApiError(ERROR.PARAM, '只支持 JPG / PNG / WebP / GIF 图片');

      avatarKey = `avatar/${userId}-${Date.now().toString(36)}-${randomHex(3)}.${kind.ext}`;
      await storage.putBuffer(avatarKey, buffer, kind.mime);
      await userService.setAvatar(userId, avatarKey, { removeFile: storage.remove });
    }

    const fresh = await userService.findById(userId);
    const quota = await quotaService.quotaInfo(fresh);
    await writeLoginLog({
      userType: 1,
      userId: fresh.id,
      username: fresh.username,
      ip: ctx.ip,
      ua: ctx.ua,
      deviceId: ctx.state.deviceId,
      status: 1,
      msg: remove ? '清空头像' : '更新头像',
    });
    ctx.json({
      code: ERROR.OK,
      msg: remove ? '已恢复默认头像' : '头像已更新',
      data: {
        user: userService.authStatusPayload(fresh, { quota_left: quota.left, quota_used: quota.used, unlimited: quota.unlimited }),
      },
    });
  },
);

/**
 * GET /api/v1/auth/status —— 授权状态、到期时间、剩余配额
 */
authRouter.get(
  '/status',
  clientAuth({ mode: 'session', requireActive: false, requireDevice: false, rate: false }),
  async (ctx) => {
    const quota = await quotaService.quotaInfo(ctx.state.user);
    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: {
        ...userService.authStatusPayload(ctx.state.user, { quota_left: quota.left, quota_used: quota.used, unlimited: quota.unlimited }),
        device_id: ctx.state.deviceId,
        session_expire_at: ctx.state.session.expire_at,
        server_ts: Math.floor(Date.now() / 1000),
      },
    });
  },
);

/** 供客户端自检：计算某个请求应有的签名（仅非生产环境开放，方便联调） */
authRouter.post('/sign_debug', clientAuth({ mode: 'none', requireActive: false, rate: false }), async (ctx) => {
  if (config.isProd) throw new ApiError(ERROR.NOT_FOUND, '接口不存在');
  const secret = String(ctx.input('secret', ''));
  const { signRequest } = await import('../../core/crypto.js');
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      body_hash: sha256Hex(ctx.rawBody || ''),
      sign: signRequest(secret, {
        method: String(ctx.input('method', 'POST')),
        path: String(ctx.input('path', '/')),
        timestamp: ctx.input('timestamp', Math.floor(Date.now() / 1000)),
        nonce: ctx.input('nonce', '0123456789abcdef'),
        deviceId: ctx.input('device_id', ''),
        bodyHash: String(ctx.input('body_hash', sha256Hex(''))),
      }),
    },
  });
});

void now;
export default authRouter;
