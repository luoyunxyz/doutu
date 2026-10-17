/**
 * 后台管理接口（规划书第 6 章六个模块）。
 *
 * 分两层：
 *   - 本文件：登录/退出/个人、管理员与角色、全局配置、公告、版本推送、审计、面板工具；
 *   - business.js：账号管理、卡密、订单、收益、任务溯源、工单。
 *
 * 所有写操作强制写 audit_log；删除用户、作废卡密、退款、封禁要求二次输入密码。
 */
import os from 'node:os';
import { Router } from '../../core/http.js';
import config from '../../config.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { adminAuth, adminLogin, logout, confirmPassword, changePassword, setupTotp, enableTotp, disableTotp, publicAdmin, parseJsonArray } from './auth.js';
import { adminBusinessRouter } from './business.js';
import { audit, listAudit } from '../../services/audit.js';
import { getDb } from '../../db/index.js';
import { now, pageParams, randomToken } from '../../core/util.js';
import { versionCodeOf, validateVersionName, formatVersionCode, bumpVersionName } from '../../core/version.js';
import { receiveApk, listApks, removeApk } from './upload.js';
import { hashPassword, sha256Hex } from '../../core/crypto.js';
import { logger } from '../../core/logger.js';
import * as sysconfig from '../../services/sysconfig.js';
import * as sessionService from '../../services/session.js';
import { purgeVersion } from '../../services/session.js';
import { checkEnvironment } from '../dw/watermark.js';
import { queueStats } from '../dw/taskService.js';

const log = logger.child('admin:routes');

/** 后台接口前缀跟随 ADMIN_PATH，便于把后台入口换成不可猜路径 */
export const adminRouter = new Router({ prefix: `${config.security.adminPath}/api` });

/* ------------------------------------------------------------------ */
/*                          登录 / 个人                                */
/* ------------------------------------------------------------------ */

adminRouter.post('/login', async (ctx) => {
  const username = String(ctx.require('username')).trim();
  const password = String(ctx.require('password'));
  const result = await adminLogin({
    username,
    password,
    totp: ctx.input('totp', null),
    ip: ctx.ip,
    ua: ctx.ua,
  });
  ctx.res.setHeader(
    'Set-Cookie',
    `admin_token=${result.token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${86400}`,
  );
  ctx.json({ code: ERROR.OK, msg: '登录成功', data: result });
});

adminRouter.post('/logout', adminAuth(null), async (ctx) => {
  await logout(ctx.state.admin.token_row_id);
  ctx.res.setHeader('Set-Cookie', 'admin_token=; Path=/; HttpOnly; Max-Age=0');
  ctx.json({ code: ERROR.OK, msg: '已退出', data: null });
});

adminRouter.get('/profile', adminAuth(null), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { admin: publicAdmin(ctx.state.admin) } });
});

adminRouter.post('/password', adminAuth(null), async (ctx) => {
  await changePassword(ctx.state.admin, String(ctx.require('old_password')), String(ctx.require('new_password')));
  await audit(ctx, 'panel', 'update', ctx.state.admin.id, { action: 'change_password' });
  ctx.json({ code: ERROR.OK, msg: '密码已修改，请重新登录', data: null });
});

adminRouter.post('/totp/setup', adminAuth(null), async (ctx) => {
  const data = await setupTotp(ctx.state.admin);
  ctx.json({ code: ERROR.OK, msg: '请用验证器扫码，然后提交一次动态码完成绑定', data });
});

adminRouter.post('/totp/enable', adminAuth(null), async (ctx) => {
  await enableTotp(ctx.state.admin, String(ctx.require('secret')), String(ctx.require('code')));
  await audit(ctx, 'panel', 'update', ctx.state.admin.id, { action: 'enable_totp' });
  ctx.json({ code: ERROR.OK, msg: '二次验证已开启', data: null });
});

adminRouter.post('/totp/disable', adminAuth(null), async (ctx) => {
  await disableTotp(ctx.state.admin, String(ctx.require('password')));
  await audit(ctx, 'panel', 'update', ctx.state.admin.id, { action: 'disable_totp' });
  ctx.json({ code: ERROR.OK, msg: '二次验证已关闭', data: null });
});

/* ------------------------------------------------------------------ */
/*                        面板工具 / 概览                              */
/* ------------------------------------------------------------------ */

adminRouter.get('/panel/env', adminAuth(null), async (ctx) => {
  const env = await checkEnvironment();
  const queue = await queueStats();
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      node: process.version,
      platform: `${os.type()} ${os.release()}`,
      cpu_count: os.cpus().length,
      load_avg: os.loadavg(),
      memory: {
        total: os.totalmem(),
        free: os.freemem(),
        rss: process.memoryUsage().rss,
      },
      uptime_sec: Math.round(process.uptime()),
      media: env,
      queue,
      server_time: now(),
    },
  });
});

/** 按客户端版本一键吊销（规划书 7.3：发现某版本被大规模破解时的止血手段） */
adminRouter.post('/panel/purge-version', adminAuth('config.write'), async (ctx) => {
  const version = String(ctx.require('app_version')).trim();
  const revoked = await purgeVersion(version);
  // 同时写进 blocklist，之后该版本的请求全部被拒
  const blocklist = await sysconfig.getJson('app_version_blocklist', []);
  const list = Array.isArray(blocklist) ? blocklist : [];
  if (!list.includes(version)) list.push(version);
  await sysconfig.setConfig('app_version_blocklist', list, { type: 'json', group: 'app', title: '被吊销的客户端版本' });
  await audit(ctx, 'panel', 'update', version, { action: 'purge_version', revoked });
  log.warn('按版本吊销客户端', { version, revoked, by: ctx.state.admin.username });
  ctx.json({ code: ERROR.OK, msg: `已吊销版本 ${version}，撤销 ${revoked} 个会话`, data: { version, revoked, blocklist: list } });
});

/* ------------------------------------------------------------------ */
/*                        管理员与角色（RBAC）                          */
/* ------------------------------------------------------------------ */

adminRouter.get('/admins', adminAuth('admin.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20);
  const db = getDb();
  const total = Number((await db.get('SELECT COUNT(*) AS n FROM `admin`'))?.n || 0);
  const rows = await db.query(
    'SELECT a.*, r.`name` AS role_name FROM `admin` a LEFT JOIN `role` r ON r.id = a.role_id ORDER BY a.`id` ASC LIMIT ? OFFSET ?',
    [limit, (page - 1) * limit],
  );
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      total,
      rows: rows.map((a) => ({ ...a, password_hash: undefined, totp_secret: a.totp_secret ? '***' : null })),
      page,
      limit,
    },
  });
});

adminRouter.post('/admins', adminAuth('admin.write'), async (ctx) => {
  const db = getDb();
  const username = String(ctx.require('username')).trim();
  if (!/^[A-Za-z0-9_.@-]{3,32}$/.test(username)) throw new ApiError(ERROR.PARAM, '用户名 3~32 位，仅限字母数字与 _ . @ -');
  const password = String(ctx.require('password'));
  if (password.length < 8) throw new ApiError(ERROR.PARAM, '密码至少 8 位');
  const exists = await db.get('SELECT `id` FROM `admin` WHERE `username` = ?', [username]);
  if (exists) throw new ApiError(ERROR.PARAM, '用户名已存在');

  const result = await db.insert('admin', {
    username,
    password_hash: hashPassword(password),
    real_name: ctx.input('real_name', null),
    role_id: Number(ctx.input('role_id', 3)),
    phone: ctx.input('phone', null),
    totp_secret: null,
    status: Number(ctx.input('status', 1)),
    created_at: now(),
    updated_at: now(),
  });
  await audit(ctx, 'panel', 'create', result.insertId, { username, role_id: ctx.input('role_id', 3) });
  ctx.json({ code: ERROR.OK, msg: '已创建', data: { id: result.insertId } });
});

adminRouter.put('/admins/:id', adminAuth('admin.write'), async (ctx) => {
  const db = getDb();
  const id = Number(ctx.params.id);
  const patch = { updated_at: now() };
  if (ctx.input('real_name', undefined) !== undefined) patch.real_name = String(ctx.input('real_name'));
  if (ctx.input('phone', undefined) !== undefined) patch.phone = String(ctx.input('phone'));
  if (ctx.input('role_id', undefined) !== undefined) patch.role_id = Number(ctx.input('role_id'));
  if (ctx.input('status', undefined) !== undefined) patch.status = Number(ctx.input('status'));
  await db.update('admin', { id }, patch);
  if (patch.status === 0) await db.update('admin_token', { admin_id: id, revoked: 0 }, { revoked: 1 });
  await audit(ctx, 'panel', 'update', id, patch);
  ctx.json({ code: ERROR.OK, msg: '已保存', data: null });
});

adminRouter.post('/admins/:id/reset-password', adminAuth('admin.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  const password = String(ctx.input('password', '')).trim() || randomToken(6);
  if (password.length < 8) throw new ApiError(ERROR.PARAM, '密码至少 8 位');
  await getDb().update('admin', { id }, { password_hash: hashPassword(password), updated_at: now() });
  await getDb().update('admin_token', { admin_id: id, revoked: 0 }, { revoked: 1 });
  await audit(ctx, 'panel', 'update', id, { action: 'reset_admin_password' });
  ctx.json({ code: ERROR.OK, msg: '密码已重置', data: { password } });
});

adminRouter.delete('/admins/:id', adminAuth('admin.write'), async (ctx) => {
  const id = Number(ctx.params.id);
  if (id === Number(ctx.state.admin.id)) throw new ApiError(ERROR.PARAM, '不能删除自己');
  await confirmPassword(ctx, ctx.state.admin, ctx.input('password'));
  const target = await getDb().get('SELECT `username`, `role_id` FROM `admin` WHERE `id` = ?', [id]);
  if (!target) throw new ApiError(ERROR.NOT_FOUND, '账号不存在');
  if (Number(target.role_id) === 1) {
    const owners = await getDb().count('admin', { role_id: 1, status: 1 });
    if (owners <= 1) throw new ApiError(ERROR.PARAM, '至少保留一个超级管理员');
  }
  await getDb().remove('admin', { id });
  await getDb().remove('admin_token', { admin_id: id });
  await audit(ctx, 'panel', 'delete', id, { username: target.username });
  ctx.json({ code: ERROR.OK, msg: '已删除', data: null });
});

/* ------------------------- 用户须知与免责声明（仅超级管理员可改） ------------------------- */

/** 超级管理员判定：role_id === 1（seed 里 super 角色 id 固定为 1）。 */
const isSuperAdmin = (admin) => Number(admin?.role_id) === 1;

/** 条款摘要：只放长度、指纹和首行，避免把整篇正文灌进日志/接口。 */
function termsDigest(text) {
  const value = String(text || '');
  return {
    length: value.length,
    sha256: sha256Hex(value),
    preview: value.replace(/\s+/g, ' ').slice(0, 60),
  };
}

async function readTerms() {
  const text = String((await sysconfig.getConfig('terms_text', '')) || '');
  return {
    text,
    version: await sysconfig.getInt('terms_version', 1),
    updated_at: (await sysconfig.getConfig('terms_updated_at', '')) || '',
    updated_by: (await sysconfig.getConfig('terms_updated_by', '')) || '',
  };
}

adminRouter.get('/terms', adminAuth('config.read'), async (ctx) => {
  const terms = await readTerms();
  const data = {
    ...terms,
    can_edit: isSuperAdmin(ctx.state.admin),
    // 附最近几次修改记录，后台直接展示「谁在什么时候改过」
    logs: await getDb()
      .query(
        "SELECT `id`, `username`, `action`, `detail`, `ip`, `created_at` FROM `audit_log` " +
          "WHERE `module` = 'terms' ORDER BY `id` DESC LIMIT 10",
      )
      .then((rows) =>
        rows.map((r) => {
          let detail = r.detail;
          try {
            if (r.detail) detail = JSON.parse(r.detail);
          } catch {
            // 保持原文
          }
          return { ...r, detail };
        }),
      )
      .catch(() => []),
  };
  ctx.json({ code: ERROR.OK, msg: 'ok', data });
});

adminRouter.post('/terms', adminAuth('config.read'), async (ctx) => {
  const admin = ctx.state.admin;
  if (!isSuperAdmin(admin)) {
    logger.warn('非超级管理员尝试修改免责声明', { adminId: admin?.id, roleId: admin?.role_id });
    await audit(ctx, 'terms', 'denied', null, { reason: 'not_super', role_id: admin?.role_id });
    throw new ApiError(ERROR.ADMIN_FORBIDDEN, '仅超级管理员可以修改免责声明');
  }

  const text = String(ctx.input('text', '')).replace(/\r\n/g, '\n').trim();
  if (text.length < 50) throw new ApiError(ERROR.PARAM, '条款内容过短（至少 50 字）');
  if (text.length > 20000) throw new ApiError(ERROR.PARAM, '条款内容过长（最多 20000 字）');

  // 内容没变就不动版本号，免得让所有用户白重新同意一次
  const before = String((await sysconfig.getConfig('terms_text', '')) || '');
  if (before === text) {
    const current = await readTerms();
    ctx.json({ code: ERROR.OK, msg: '内容未变化', data: { version: current.version, unchanged: true } });
    return;
  }

  const version = (await sysconfig.getInt('terms_version', 1)) + 1;
  const stamp = now();
  await sysconfig.setConfig('terms_text', text, { type: 'text' });
  await sysconfig.setConfig('terms_version', String(version), { type: 'int' });
  await sysconfig.setConfig('terms_updated_at', stamp, { type: 'string' });
  await sysconfig.setConfig('terms_updated_by', admin.username || '', { type: 'string' });

  // 修改免责声明属于敏感操作：新旧内容指纹 + 长度 + 版本 + 操作人 + IP 全部留痕
  await audit(ctx, 'terms', 'update', null, {
    action: 'update_terms',
    version_before: version - 1,
    version_after: version,
    before: termsDigest(before),
    after: termsDigest(text),
  });
  logger.info('免责声明已更新', { adminId: admin.id, version, length: text.length });

  ctx.json({ code: ERROR.OK, msg: '已保存', data: { version, unchanged: false } });
});

adminRouter.get('/roles', adminAuth('admin.read'), async (ctx) => {
  const rows = await getDb().query('SELECT * FROM `role` ORDER BY `id` ASC');
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: { rows: rows.map((r) => ({ ...r, menus: parseJsonArray(r.menus), perms: parseJsonArray(r.perms) })) },
  });
});

adminRouter.put('/roles/:id', adminAuth('admin.write'), async (ctx) => {
  const db = getDb();
  const id = Number(ctx.params.id);
  const patch = { updated_at: now() };
  if (ctx.input('name', undefined) !== undefined) patch.name = String(ctx.input('name'));
  if (ctx.input('menus', undefined) !== undefined) patch.menus = JSON.stringify(ctx.input('menus', []));
  if (ctx.input('perms', undefined) !== undefined) patch.perms = JSON.stringify(ctx.input('perms', []));
  if (ctx.input('data_scope', undefined) !== undefined) patch.data_scope = Number(ctx.input('data_scope'));
  if (ctx.input('remark', undefined) !== undefined) patch.remark = String(ctx.input('remark'));
  await db.update('role', { id }, patch);
  await audit(ctx, 'panel', 'update', id, { action: 'update_role', patch: { ...patch, menus: undefined, perms: undefined } });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: null });
});

adminRouter.post('/roles', adminAuth('admin.write'), async (ctx) => {
  const db = getDb();
  const code = String(ctx.require('code')).trim();
  if (await db.get('SELECT `id` FROM `role` WHERE `code` = ?', [code])) throw new ApiError(ERROR.PARAM, '角色标识已存在');
  const result = await db.insert('role', {
    name: String(ctx.require('name')),
    code,
    menus: JSON.stringify(ctx.input('menus', [])),
    perms: JSON.stringify(ctx.input('perms', [])),
    data_scope: Number(ctx.input('data_scope', 1)),
    remark: ctx.input('remark', null),
    created_at: now(),
    updated_at: now(),
  });
  await audit(ctx, 'panel', 'create', result.insertId, { action: 'create_role', code });
  ctx.json({ code: ERROR.OK, msg: '已创建', data: { id: result.insertId } });
});

/* ------------------------------------------------------------------ */
/*                          全局配置 / 公告                            */
/* ------------------------------------------------------------------ */

adminRouter.get('/configs', adminAuth('config.read'), async (ctx) => {
  const rows = await sysconfig.listConfigs({ group: ctx.query.group });
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { rows, config_version: await sysconfig.getInt('config_version', 1) } });
});

adminRouter.post('/configs', adminAuth('config.write'), async (ctx) => {
  const entries = ctx.input('entries', null);
  const list = Array.isArray(entries)
    ? entries
    : Object.entries(ctx.input('configs', {})).map(([key, value]) => ({ key, value }));
  if (!list.length) throw new ApiError(ERROR.PARAM, '没有需要保存的配置');

  const db = getDb();
  const before = [];
  for (const entry of list) {
    const row = await db.get('SELECT `value`, `type` FROM `sys_config` WHERE `key` = ?', [entry.key]);
    // 用表里已有的类型写回，避免 JSON 被当字符串存进去
    await sysconfig.setConfig(entry.key, entry.value, { type: entry.type || row?.type || 'string' });
    before.push({ key: entry.key, old: row?.value ?? null, new: String(entry.value) });
  }
  // 配置一变就推版本号，客户端下次拉取会刷新缓存
  const nextVersion = (await sysconfig.getInt('config_version', 1)) + 1;
  await sysconfig.setConfig('config_version', nextVersion, { type: 'int', group: 'app', isPublic: 1 });
  await audit(ctx, 'config', 'update', null, { changes: before, config_version: nextVersion });
  ctx.json({ code: ERROR.OK, msg: '已保存，客户端将在下次拉取时生效', data: { config_version: nextVersion, changed: before.length } });
});

/* 公告类型：1 弹窗 / 2 顶部滚动 / 3 首页横幅（叠加在首页大图上的文字框，可轮播） */
const NOTICE_TYPES = [1, 2, 3];
const normalizeNoticeType = (value) => {
  const n = Number(value);
  return NOTICE_TYPES.includes(n) ? n : 1;
};
/** 可见等级：逗号分隔的等级或 * ；顺手去掉空格与重复 */
const normalizeTargetLevel = (value) => {
  const raw = String(value ?? '').trim();
  if (!raw || raw === '*') return '*';
  const parts = [...new Set(raw.split(',').map((s) => s.trim()).filter((s) => /^\d+$/.test(s)))];
  return parts.length ? parts.join(',') : '*';
};

adminRouter.get('/notices', adminAuth('config.read'), async (ctx) => {
  const rows = await getDb().query('SELECT * FROM `notice` ORDER BY `status` DESC, `sort` ASC, `id` DESC');
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { rows } });
});

adminRouter.post('/notices', adminAuth('config.write'), async (ctx) => {
  const db = getDb();
  const data = {
    title: String(ctx.require('title')).slice(0, 64),
    content: String(ctx.input('content', '')),
    type: normalizeNoticeType(ctx.input('type', 1)),
    target_level: normalizeTargetLevel(ctx.input('target_level', '1,2,3,4,5')),
    start_at: ctx.input('start_at', null) || null,
    end_at: ctx.input('end_at', null) || null,
    status: Number(ctx.input('status', 1)),
    sort: Number(ctx.input('sort', 100)),
    created_at: now(),
    updated_at: now(),
  };
  const result = await db.insert('notice', data);
  await audit(ctx, 'config', 'create', result.insertId, { action: 'create_notice', title: data.title, type: data.type });
  ctx.json({ code: ERROR.OK, msg: '已发布', data: { id: result.insertId } });
});

adminRouter.put('/notices/:id', adminAuth('config.write'), async (ctx) => {
  const db = getDb();
  const patch = { updated_at: now() };
  for (const field of ['title', 'content', 'start_at', 'end_at']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = ctx.input(field);
  }
  if (ctx.input('target_level', undefined) !== undefined) {
    patch.target_level = normalizeTargetLevel(ctx.input('target_level'));
  }
  for (const field of ['status', 'sort']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = Number(ctx.input(field));
  }
  if (ctx.input('type', undefined) !== undefined) patch.type = normalizeNoticeType(ctx.input('type'));
  await db.update('notice', { id: Number(ctx.params.id) }, patch);
  await audit(ctx, 'config', 'update', ctx.params.id, patch);
  ctx.json({ code: ERROR.OK, msg: '已保存', data: null });
});

adminRouter.delete('/notices/:id', adminAuth('config.write'), async (ctx) => {
  await getDb().remove('notice', { id: Number(ctx.params.id) });
  await audit(ctx, 'config', 'delete', ctx.params.id, { action: 'delete_notice' });
  ctx.json({ code: ERROR.OK, msg: '已删除', data: null });
});

/* ------------------------------------------------------------------ */
/*                          版本推送                                   */
/* ------------------------------------------------------------------ */

adminRouter.get('/versions', adminAuth('version.read'), async (ctx) => {
  const rows = await getDb().query('SELECT * FROM `app_version` ORDER BY `version_code` DESC, `id` DESC');
  // 客户端**实际上报过**的版本：后台「指定版本」要勾的就是这些（平台/版本号），
  // 它们不一定在 app_version 里（运营可能把旧版本记录删了，但用户手机上还跑着）。
  const clientVersions = await getDb().query(
    'SELECT `platform`, `version_code`, `version_name`, `hit_count`, `last_at` FROM `client_version` ORDER BY `last_at` DESC LIMIT 100',
  );
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { rows, client_versions: clientVersions } });
});

/* ------------------------------ 安装包（上传 / 列表 / 删除） ------------------------------ */

/**
 * 上传 APK：**原始二进制流**，不是 multipart。
 *
 * 路由上给处理函数挂了 `streamBody = true`，框架就不会先把 body 读进内存（默认上限 2MB），
 * 而是让它自己消费 req —— 36MB 的包也能传，内存占用恒定。前端用 XHR 直传 File 对象即可。
 */
async function uploadVersionApk(ctx) {
  const result = await receiveApk(ctx.req, ctx.input('name', ''));
  await audit(ctx, 'config', 'create', null, { action: 'upload_apk', name: result.name, size: result.size });
  ctx.json({ code: ERROR.OK, msg: '上传完成', data: result });
}
uploadVersionApk.streamBody = true;
adminRouter.post('/versions/upload', adminAuth('version.write'), uploadVersionApk);

adminRouter.get('/versions/files', adminAuth('version.read'), async (ctx) => {
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { files: await listApks() } });
});

adminRouter.delete('/versions/files/:name', adminAuth('version.write'), async (ctx) => {
  const result = await removeApk(ctx.params.name);
  await audit(ctx, 'config', 'delete', null, { action: 'delete_apk', name: result.name });
  ctx.json({ code: ERROR.OK, msg: `已删除 ${result.name}`, data: result });
});

/**
 * 移除一条「客户端在用版本」统计。
 *
 * 用途：清理测试机/已淘汰机型留下的记录，免得后台的勾选列表越积越乱。
 * 注意它只是删统计 —— 只要还有设备在跑这个版本，下次查更新时它会重新出现（这是设计，不是 bug）。
 */
adminRouter.delete('/versions/client-versions/:code', adminAuth('version.write'), async (ctx) => {
  const code = Number(ctx.params.code);
  if (!Number.isFinite(code)) throw new ApiError(ERROR.PARAM, '版本号不合法');
  const rows = await getDb().query('SELECT `id` FROM `client_version` WHERE `version_code` = ?', [code]);
  for (const r of rows) await getDb().remove('client_version', { id: r.id });
  await audit(ctx, 'config', 'delete', null, { action: 'delete_client_version', versionCode: code });
  ctx.json({ code: ERROR.OK, msg: `已移除 ${rows.length} 条记录`, data: { removed: rows.length } });
});

/** 平台白名单：客户端只有 android / ios 两种，别的值一律拒绝（避免后台塞进客户端不认的数据）。 */
const VERSION_PLATFORMS = ['android', 'ios'];
function normalizePlatform(value) {
  const p = String(value || '').trim().toLowerCase();
  if (!VERSION_PLATFORMS.includes(p)) {
    throw new ApiError(ERROR.PARAM, `平台只能是 android 或 ios（收到：${value || '空'}）`);
  }
  return p;
}

/**
 * 更新方式：`optional` 选择更新 / `force` 必须更新。
 * 历史数据里用的是布尔 `force`，这里两者都收：给了 update_mode 以它为准，否则回落到 force。
 * 必须更新 = 旧版本作废：客户端不更新就进不去（App 会直接退出）。
 */
function normalizeUpdateMode(input, legacyForce) {
  const mode = String(input ?? '').trim().toLowerCase();
  if (mode === 'force' || mode === 'must' || mode === 'required') return 1;
  if (mode === 'optional' || mode === 'choose' || mode === 'normal') return 0;
  if (mode) throw new ApiError(ERROR.PARAM, `更新方式只能是 optional（选择更新）或 force（必须更新），收到：${input}`);
  return legacyForce ? 1 : 0;
}

/**
 * 「最低兼容版本号」也是给人填的，所以同样收 `1.2.3`：
 * 带点的按版本号换算（1.0.0 → 10000），纯数字按老口径当整数（兼容脚本与历史数据）。
 */
function parseMinVersion(input) {
  if (input === null || input === undefined || input === '') return null;
  const text = String(input).trim();
  if (/^\d+$/.test(text)) return Number(text);
  const code = versionCodeOf(text);
  if (code === null) {
    throw new ApiError(ERROR.PARAM, `最低兼容版本号「${text}」不合法：请填成 1.0.0 这样的格式，或留空`);
  }
  return code;
}

/**
 * 是不是「唯一键冲突」。MySQL 给 ER_DUP_ENTRY，SQLite 给 "UNIQUE constraint failed"，
 * 两边都认，好让调用方把它翻译成人话。
 */
function isDuplicateEntry(err) {
  const code = String((err && err.code) || '');
  const message = String((err && err.message) || '');
  return code === 'ER_DUP_ENTRY' || /UNIQUE constraint failed|Duplicate entry/i.test(message);
}

/**
 * 指定版本（2026-10-17）：这条更新只在**指定的来源版本**上生效。
 *
 * 入参是逗号分隔的 version_code（数组也收），留空 = 所有版本都收（历史行为不变）。
 * 它会连**强制更新**一起限定：指定了 [1]，就只有在 1 上的客户端会被要求（强制）升级，
 * 其他版本连弹窗都不会看到 —— 强制与非强制在这一点上没有区别。
 *
 * 存库前统一成 `"1,2,3"` 这种形式（去重、排序、去空白），空则存 null。
 */
function normalizeTargetVersions(input) {
  const raw = Array.isArray(input) ? input.join(',') : String(input ?? '');
  const parts = raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
  const codes = [];
  for (const p of parts) {
    if (/^\d+$/.test(p)) {
      // 纯数字按**内部编码**理解（兼容脚本与历史数据；界面不会给人填这种）
      codes.push(Number(p));
      continue;
    }
    const code = versionCodeOf(p);
    if (code === null) {
      // ⚠️ 认不出来必须报错，**绝不能默默当成「全部版本」** ——
      // 那会把「只想推给 1.0.1」变成「推给所有人」，是数据面的事故。
      throw new ApiError(
        ERROR.PARAM,
        `指定版本「${p}」看不懂：请填版本号（如 1.0.1，多个用英文逗号分隔），或留空表示所有版本都收`,
      );
    }
    codes.push(code);
  }
  if (!codes.length) return null;
  return [...new Set(codes)].sort((a, b) => a - b).join(',');
}

/** 下载地址必须是 http(s)，避免写进 javascript:/file: 之类的地址。 */
/**
 * 下载地址校验。
 *
 * 光判 http(s) 是不够的 —— 真实踩到：运营把**接口文档页**（`https://api.lvxiaodong.com/doc/head`）
 * 填进了下载地址，用户点「立即更新」只会打开一个网页、什么也下不到，而且只有真机点下去才发现。
 * 这里额外要求地址「看起来是个安装包」：`.apk` 结尾，或者走我们自己的 /upgrade/app/ 目录。
 *
 * 只对 Android 强制（iOS 的 plist 地址不长这样）；确实要指向别处时用 `allow_any_url: true` 显式放行。
 */
function normalizeDownloadUrl(value, { platform = 'android', allowAny = false } = {}) {
  const url = String(value || '').trim();
  if (!/^https?:\/\//i.test(url)) {
    throw new ApiError(ERROR.PARAM, '下载地址必须以 http:// 或 https:// 开头');
  }
  if (platform === 'android' && !allowAny) {
    const path = url.split('?')[0].toLowerCase();
    const looksLikeApk = path.endsWith('.apk') || path.includes('upgrade/app/') || path.includes('/upgrade/');
    if (!looksLikeApk) {
      throw new ApiError(
        ERROR.PARAM,
        `下载地址看起来不是安装包（${url.slice(0, 60)}…）：用户可以点「立即更新」，但下载到的会是网页而不是 APK，装不上。` +
          '请填以 .apk 结尾的直链，或用上面的「上传 APK」自动填好；确实要指向别处就勾选「允许任意地址」',
      );
    }
  }
  return url.slice(0, 255);
}

adminRouter.post('/versions', adminAuth('version.write'), async (ctx) => {
  const db = getDb();
  const platform = normalizePlatform(ctx.input('platform', 'android'));
  // 版本号现在按人话填（1.2.3）；`version_code` 允许显式覆盖，但默认由版本号换算出来，
  // 保证「后台看到的版本号」和「客户端比对用的整数」永远是同一个东西。
  const versionName = String(ctx.require('version_name')).trim();
  const nameError = validateVersionName(versionName);
  if (nameError) throw new ApiError(ERROR.PARAM, nameError);
  const derivedCode = versionCodeOf(versionName);
  const explicitCode = ctx.input('version_code', null);
  const versionCode = explicitCode === null || explicitCode === '' ? derivedCode : Number(explicitCode);
  if (!Number.isFinite(versionCode) || versionCode <= 0) {
    throw new ApiError(ERROR.PARAM, '版本号不合法，请填成 1.2.3 这样的格式');
  }
  if (explicitCode !== null && explicitCode !== '' && versionCode !== derivedCode) {
    // 显式给了整数又和版本号对不上：这种不一致以后一定会出怪问题，直接拒绝
    throw new ApiError(
      ERROR.PARAM,
      `版本号「${versionName}」对应的内部编码是 ${derivedCode}，与传入的 ${versionCode} 不一致；` +
        '要么留空让它自动换算，要么把版本号改成与它一致',
    );
  }
  // 渠道统一 trim：MySQL 的比较会忽略**尾部**空格却不会忽略**头部**空格，
  // 于是「 all」这种输入会绕过预检查、再去撞唯一键（真实踩过）。存之前先规整。
  const channel = String(ctx.input('channel', 'all')).trim() || 'all';
  if (await db.get('SELECT `id` FROM `app_version` WHERE `platform` = ? AND `version_code` = ? AND `channel` = ?', [platform, versionCode, channel])) {
    throw new ApiError(
      ERROR.PARAM,
      `版本号 ${versionName} 已经发布过了（${platform} / ${channel} 下不能重复）。` +
        '如果只是想改它的下载地址或更新公告，请到列表里点「编辑」；' +
        `要发新版本请用更大的版本号，例如 ${bumpVersionName(versionName)}`,
    );
  }
  const data = {
    platform,
    version_code: versionCode,
    version_name: versionName,
    channel,
    url: normalizeDownloadUrl(ctx.require('url'), { platform, allowAny: ctx.input('allow_any_url', false) === true }),
    patch_url: ctx.input('patch_url', null),
    patch_md5: ctx.input('patch_md5', null),
    size: ctx.input('size', null) ? Number(ctx.input('size')) : null,
    changelog: ctx.input('changelog', ''),
    force: normalizeUpdateMode(ctx.input('update_mode', undefined), ctx.input('force', 0)),
    gray_percent: Math.min(100, Math.max(0, Number(ctx.input('gray_percent', 100)))),
    min_version: parseMinVersion(ctx.input('min_version', null)),
    target_versions: normalizeTargetVersions(ctx.input('target_versions', null)),
    status: Number(ctx.input('status', 1)),
    published_at: now(),
    created_at: now(),
  };
  // 唯一键兜底：再稳的预检查也可能被边缘输入绕过（渠道里多个空格、大小写、并发双击…），
  // 一旦 INSERT 撞上 `uk_platform_version_code_channel`，绝不能把数据库异常原样抛给运营 ——
  // 那样前台只会看到「服务器异常，请稍后重试」，谁都查不出是版本号重复。
  let result;
  try {
    result = await db.insert('app_version', data);
  } catch (err) {
    if (isDuplicateEntry(err)) {
      throw new ApiError(ERROR.PARAM, `版本号 ${versionName} 已经发布过了（${platform} / ${channel} 下不能重复）；要发新版本请用更大的版本号，例如 ${bumpVersionName(versionName)}`);
    }
    throw err;
  }
  await db.update('sys_config', { key: 'app_latest_version_code' }, { value: String(versionCode), updated_at: now() });
  sysconfig.invalidate();
  await audit(ctx, 'config', 'create', result.insertId, { action: 'create_version', versionCode, channel });

  // 保存后的提示分两类，**必须分开返回**：
  //   · blockers：这次发布**注定推不到**（版本号没比线上大、指定版本里写了 ≥ 本次版本号的目标）
  //     —— 前台要弹窗拦一下，让人有机会改；
  //   · notes：只是**说明**（「本次只推给 X/Y」、渠道不是 all），发布本身完全正常，不该拦。
  //
  // 为什么用结构化字段而不是让前台去匹配文字：之前前台就是拿 `/不会收到/` 去匹配提示文案，
  // 结果把「本次只推给指定版本 X，其他版本不会收到任何提示」这条**正常说明**也当成了拦截条件，
  // 于是「1.1.0 只推给 1.0.0/1.0.1」这种完全正确的发布被误弹警告（真实踩到）。
  const notes = [];
  const blockers = [];

  // 版本号在后台里以「1.2.3」呈现，提示里也用它，别再把内部编码丢给运营看。
  //
  // 取名优先级：**编码本身**（≥10000 时 10001 就是 1.0.1，由公式决定、最权威）
  //   → 已发布记录里的名字 → 客户端上报的名字 → 兜底用编码。
  // 为什么编码优先：客户端上报的版本名可能是过期的（老包一直上报 1.0.0），
  // 拿它当 10001 的名字会出现「只推给 1.0.0、1.0.0」这种自相矛盾的提示（真实踩到）。
  const namesOf = async (codes) => {
    if (!codes.length) return [];
    const rows = await db.query(
      `SELECT \`version_code\`, \`version_name\` FROM \`app_version\` WHERE \`platform\` = ? AND \`version_code\` IN (${codes.map(() => '?').join(',')})` +
        ` UNION SELECT \`version_code\`, \`version_name\` FROM \`client_version\` WHERE \`platform\` = ? AND \`version_code\` IN (${codes.map(() => '?').join(',')})`,
      [platform, ...codes, platform, ...codes],
    );
    const byCode = new Map(rows.map((r) => [Number(r.version_code), r.version_name]));
    return codes.map((c) => (c >= 10000 ? formatVersionCode(c) : '') || byCode.get(c) || String(c));
  };

  const newest = await db.get(
    'SELECT `version_code` FROM `app_version` WHERE `platform` = ? AND `status` = 1 AND `id` <> ? ORDER BY `version_code` DESC LIMIT 1',
    [platform, result.insertId],
  );
  if (newest && Number(newest.version_code) >= versionCode) {
    const text = `已经有更新的版本 ${formatVersionCode(newest.version_code) || newest.version_code}，这个 ${versionName} 不会再推给任何人`;
    notes.push(text);
    blockers.push(text);
  }
  if (channel !== 'all') {
    notes.push(`渠道是「${channel}」：只有同样带这个渠道的客户端能收到，普通发版请填 all`);
  }
  const targets = String(data.target_versions || '')
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isFinite(n) && n > 0);
  const unreachable = targets.filter((t) => t >= versionCode);
  if (unreachable.length) {
    const text =
      `指定版本里的 ${(await namesOf(unreachable)).join('、')}（${unreachable.join('/')}）不小于本次版本号 ${versionName}，` +
      `它们不会收到这次更新（客户端只在更新版本号更大时才提示）；要推给它们请把版本号提到 ` +
      `${formatVersionCode(Math.max(...unreachable) + 1) || Math.max(...unreachable) + 1} 或更高`;
    notes.push(text);
    blockers.push(text);
  }
  if (targets.length) {
    // 只是说明：这就是「指定版本」的预期效果，不是错误
    notes.push(`本次只推给 ${(await namesOf(targets)).join('、')}（${targets.join('/')}），其他版本不会收到提示`);
  }
  ctx.json({
    code: ERROR.OK,
    msg: blockers.length ? `已保存（注意：${blockers.join('；')}）` : '已保存',
    data: { id: result.insertId, notes, blockers },
  });
});

adminRouter.put('/versions/:id', adminAuth('version.write'), async (ctx) => {
  const db = getDb();
  const id = Number(ctx.params.id);
  const current = await db.get('SELECT * FROM `app_version` WHERE `id` = ?', [id]);
  if (!current) throw new ApiError(ERROR.NOT_FOUND, '版本不存在');
  const patch = {};
  for (const field of ['patch_url', 'patch_md5', 'changelog']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = ctx.input(field);
  }
  // 版本号（1.2.3）可以改：改了就把内部编码一起重算，避免「显示的版本号」和
  // 「客户端比对的整数」对不上（对不上就会出现后台显示已发布、用户却收不到）。
  if (ctx.input('version_name', undefined) !== undefined) {
    const nextName = String(ctx.input('version_name')).trim();
    const nameError = validateVersionName(nextName);
    if (nameError) throw new ApiError(ERROR.PARAM, nameError);
    const nextCode = versionCodeOf(nextName);
    patch.version_name = nextName;
    if (nextCode !== Number(current.version_code)) {
      const clash = await db.get(
        'SELECT `id` FROM `app_version` WHERE `platform` = ? AND `version_code` = ? AND `channel` = ? AND `id` <> ?',
        [current.platform, nextCode, current.channel, id],
      );
      if (clash) {
        throw new ApiError(ERROR.PARAM, `版本号 ${nextName} 已被另一条记录占用（同一个平台/渠道下不能重复）`);
      }
      patch.version_code = nextCode;
    }
  }
  if (ctx.input('url', undefined) !== undefined) {
    patch.url = normalizeDownloadUrl(ctx.input('url'), {
      platform: current.platform,
      allowAny: ctx.input('allow_any_url', false) === true,
    });
  }
  for (const field of ['status', 'gray_percent', 'min_version', 'size']) {
    if (ctx.input(field, undefined) !== undefined) patch[field] = Number(ctx.input(field));
  }
  if (ctx.input('update_mode', undefined) !== undefined) {
    patch.force = normalizeUpdateMode(ctx.input('update_mode'), 0);
  } else if (ctx.input('force', undefined) !== undefined) {
    patch.force = ctx.input('force') ? 1 : 0;
  }
  // 指定版本：**空串是有效输入**（= 改回「所有版本都收」）。
  // 注意不能用 `ctx.input('target_versions', undefined) !== undefined` 判断 ——
  // ctx.input 会把空串当「没传」返回默认值，那样永远清不掉名单。
  const hasTargetField =
    (ctx.body && typeof ctx.body === 'object' && 'target_versions' in ctx.body) ||
    (ctx.query && 'target_versions' in ctx.query);
  if (hasTargetField) {
    patch.target_versions = normalizeTargetVersions(ctx.input('target_versions', ''));
  }
  await db.update('app_version', { id }, patch);
  await audit(ctx, 'config', 'update', id, { action: 'update_version', patch });
  ctx.json({ code: ERROR.OK, msg: '已保存', data: null });
});

adminRouter.delete('/versions/:id', adminAuth('version.write'), async (ctx) => {
  await getDb().remove('app_version', { id: Number(ctx.params.id) });
  await audit(ctx, 'config', 'delete', ctx.params.id, { action: 'delete_version' });
  ctx.json({ code: ERROR.OK, msg: '已删除', data: null });
});

/* ------------------------------------------------------------------ */
/*                            审计日志                                 */
/* ------------------------------------------------------------------ */

adminRouter.get('/audit', adminAuth('audit.read'), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 20, 100);
  const result = await listAudit({
    module: ctx.query.module,
    adminId: ctx.query.admin_id,
    targetId: ctx.query.target_id,
    page,
    limit,
  });
  ctx.json({ code: ERROR.OK, msg: 'ok', data: result });
});

/* ------------------------------------------------------------------ */
/*                        挂载业务子路由                               */
/* ------------------------------------------------------------------ */

adminRouter.mount(adminBusinessRouter);

export default adminRouter;
