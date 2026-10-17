/**
 * 去水印任务编排（规划书 3.1 的七步链路 + 3.3 的队列与并发控制）。
 *
 * 异步任务模型：提交返回 task_no，客户端轮询 /dw/task/{task_no}。
 * 队列用「KV List（快路径）+ 数据库轮询（可靠路径）」：
 *   - API 进程 lpush 队列并唤醒 worker；
 *   - worker 每次也按 status=0 扫库，保证 Redis 重启/丢消息时任务不会永久卡住。
 * 认领任务用「条件更新」而不是先查后改，多个 worker 不会抢到同一条（乐观锁）。
 */
import path from 'node:path';
import fs from 'node:fs';
import { getDb } from '../../db/index.js';
import { kv, keys } from '../../core/kv.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { logger } from '../../core/logger.js';
import { now, businessNo, formatDateTime, addHours, parseDateTime, randomHex, toInt } from '../../core/util.js';
import { getBool, getInt, getJson, watermarkPolicy } from '../../services/sysconfig.js';
import * as quotaService from '../../services/quota.js';
import * as userService from '../../services/user.js';
import { extractUrl, detectPlatform, platformName, platformList } from './adapters/index.js';
import { resolveWithFallback } from './routeSelector.js';
import * as storage from './storage.js';
import * as watermark from './watermark.js';
import config from '../../config.js';

const log = logger.child('dw:task');

export const TASK_STATUS = { QUEUED: 0, PARSING: 1, COMPOSING: 2, SUCCESS: 3, FAILED: 4, TIMEOUT: 5 };
export const TASK_STATUS_TEXT = { 0: '排队中', 1: '解析中', 2: '合成中', 3: '已完成', 4: '失败', 5: '超时' };

/** 进程内唤醒信号：API 提交任务后立刻叫醒同进程的 worker，避免等一个轮询周期 */
const waiters = new Set();
export function wakeWorker() {
  for (const resolve of waiters) resolve();
  waiters.clear();
}
export function waitForWake(timeoutMs) {
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      waiters.delete(onWake);
      resolve();
    }, timeoutMs);
    const onWake = () => {
      clearTimeout(timer);
      resolve();
    };
    waiters.add(onWake);
  });
}

/* ------------------------------------------------------------------ */
/*                            提交任务                                 */
/* ------------------------------------------------------------------ */

/**
 * 提交解析任务。
 * @returns {{task, reused:boolean, estimate_sec:number, quota_left:number}}
 */
export async function createTask({ user, shareText, url: rawUrl = null, wantWatermark = true, ip, deviceId, priority = 'normal' }) {
  const db = getDb();
  const url = rawUrl ? String(rawUrl).trim() : extractUrl(shareText);
  if (!url) throw new ApiError(ERROR.PARAM, '未检测到视频链接');
  if (!/^https?:\/\//i.test(url)) throw new ApiError(ERROR.PARAM, '链接格式不正确');

  const platform = detectPlatform(url);
  const disabled = await getJson('disabled_platforms', []);
  if (Array.isArray(disabled) && disabled.includes(platform)) {
    throw new ApiError(ERROR.PARSE_FAILED, `${platformName(platform)} 暂时不可用，请稍后再试`);
  }

  const policy = await watermarkPolicy(user.level);
  // 会员模型：开通会员 ⇒ 成品免水印；未开通/已过期 ⇒ 成品带水印。
  // 客户端只能表达「我想不想要」，最终由这条服务端策略决定（客户端改包也绕不过）。
  const isMember = userService.effectiveStatus(user) === userService.STATUS.NORMAL;
  const applyWatermark = Boolean(policy.enabled && (!isMember || !policy.freeForMember));

  // 原片/成品复用（规划书 3.1「原片缓存」）：同用户、同链接、同水印策略，24 小时内直接复用成品
  const cacheHours = await getInt('origin_cache_hours', config.media.originCacheHours || 24);
  if (cacheHours > 0) {
    const since = formatDateTime(addHours(new Date(), -cacheHours));
    const cached = await db.get(
      'SELECT * FROM `dw_task` WHERE `user_id` = ? AND `share_url` = ? AND `status` = ? AND `out_key` IS NOT NULL AND `created_at` >= ? AND `watermark` = ? ORDER BY `id` DESC LIMIT 1',
      [user.id, url, TASK_STATUS.SUCCESS, since, applyWatermark ? 1 : 0],
    );
    if (cached) {
      const quota = await quotaService.consumeQuota(user);
      const result = await db.insert('dw_task', {
        task_no: businessNo('DW'),
        user_id: user.id,
        platform: cached.platform,
        share_url: url,
        item_id: cached.item_id,
        route_id: cached.route_id,
        status: TASK_STATUS.SUCCESS,
        watermark: applyWatermark ? 1 : 0,
        media_type: cached.media_type,
        title: cached.title,
        cover: cached.cover,
        images: cached.images,
        src_url: cached.src_url,
        src_key: cached.src_key,
        out_url: cached.out_url,
        out_key: cached.out_key,
        out_size: cached.out_size,
        duration_ms: 0,
        video_ms: cached.video_ms,
        retry_count: 0,
        worker_id: null,
        err_msg: '命中缓存',
        client_ip: ip || null,
        device_id: deviceId ? String(deviceId).slice(0, 64) : null,
        started_at: now(),
        created_at: now(),
        finished_at: now(),
      });
      const task = await db.get('SELECT * FROM `dw_task` WHERE `id` = ?', [result.insertId]);
      log.info('命中成品缓存', { taskNo: task.task_no, sourceTask: cached.task_no });
      return { task, reused: true, estimate_sec: 0, quota_left: quota.left };
    }
  }

  // 未命中缓存：先建排队记录，再消费配额（配额不足会直接抛 3001，不会留下垃圾任务）
  const estimateSec = await getInt('parse_timeout_sec', 25);
  const created = await db.insert('dw_task', {
    task_no: businessNo('DW'),
    user_id: user.id,
    platform,
    share_url: url,
    item_id: null,
    route_id: null,
    status: TASK_STATUS.QUEUED,
    watermark: applyWatermark ? 1 : 0,
    media_type: 'VIDEO',
    title: null,
    cover: null,
    images: null,
    src_url: null,
    src_key: null,
    out_url: null,
    out_key: null,
    out_size: null,
    duration_ms: null,
    video_ms: null,
    retry_count: 0,
    worker_id: null,
    err_msg: null,
    client_ip: ip || null,
    device_id: deviceId ? String(deviceId).slice(0, 64) : null,
    started_at: null,
    created_at: now(),
    finished_at: null,
  });

  let quota;
  try {
    quota = await quotaService.consumeQuota(user);
  } catch (err) {
    // 配额不足：删掉刚建的任务，保持「失败不计费也不留痕」
    await db.remove('dw_task', { id: created.insertId });
    throw err;
  }

  const task = await db.get('SELECT * FROM `dw_task` WHERE `id` = ?', [created.insertId]);
  const queueName = priority === 'high' || Number(user.level) >= 3 ? 'high' : 'normal';
  await kv().lpush(keys.queue(queueName), task.task_no).catch(() => {});
  wakeWorker();

  log.info('任务已入队', { taskNo: task.task_no, platform, queue: queueName, userId: user.id });
  return { task, reused: false, estimate_sec: estimateSec, quota_left: quota.left };
}

/* ------------------------------------------------------------------ */
/*                            任务执行                                 */
/* ------------------------------------------------------------------ */

/**
 * 认领一条待处理任务（乐观锁：条件更新成功才算抢到）。
 * 优先取「付费/高等级」队列里的任务号，其次按创建时间。
 */
export async function claimTask(workerId) {
  const db = getDb();
  const highFirst = [];

  // 快路径：从 KV 队列里取任务号（可能有已完成的脏数据，跳过即可）
  for (const name of ['high', 'normal']) {
    for (let i = 0; i < 3; i += 1) {
      const taskNo = await kv().rpop(keys.queue(name)).catch(() => null);
      if (!taskNo) break;
      highFirst.push(String(taskNo));
    }
  }

  if (highFirst.length) {
    const placeholders = highFirst.map(() => '?').join(', ');
    const row = await db.get(
      `SELECT * FROM \`dw_task\` WHERE \`task_no\` IN (${placeholders}) AND \`status\` = ? ORDER BY \`id\` ASC LIMIT 1`,
      [...highFirst, TASK_STATUS.QUEUED],
    );
    if (row) {
      const claimed = await db.update(
        'dw_task',
        { id: row.id, status: TASK_STATUS.QUEUED },
        { status: TASK_STATUS.PARSING, worker_id: workerId, started_at: now() },
      );
      if (claimed.affectedRows === 1) return db.get('SELECT * FROM `dw_task` WHERE `id` = ?', [row.id]);
    }
  }

  // 可靠路径：直接扫库（Redis 丢消息 / 重启后仍能继续消费）
  const candidate = await db.get(
    'SELECT * FROM `dw_task` WHERE `status` = ? ORDER BY `id` ASC LIMIT 1',
    [TASK_STATUS.QUEUED],
  );
  if (!candidate) return null;
  const claimed = await db.update(
    'dw_task',
    { id: candidate.id, status: TASK_STATUS.QUEUED },
    { status: TASK_STATUS.PARSING, worker_id: workerId, started_at: now() },
  );
  if (claimed.affectedRows !== 1) return null;
  return db.get('SELECT * FROM `dw_task` WHERE `id` = ?', [candidate.id]);
}

/**
 * 下载原片时带哪个 Referer。
 *
 * 抖音 CDN 上同一段视频，带 https://www.douyin.com/ 比带它自己的域名快约 7 倍
 * （2026-10-17 实测：674ms vs 4551ms，同一个文件），所以按平台给「站内页」的 Referer；
 * 不认识的平台退回该地址自己的 origin（原来就是这个行为）。
 */
function downloadReferer(platform, url) {
  const known = {
    douyin: 'https://www.douyin.com/',
    kuaishou: 'https://www.kuaishou.com/',
    xiaohongshu: 'https://www.xiaohongshu.com/',
    bilibili: 'https://www.bilibili.com/',
  }[String(platform || '').toLowerCase()];
  if (known) return known;
  try {
    return new URL(url).origin;
  } catch {
    return null;
  }
}

/** 处理一条任务：解析 → 转存 → 合成 → 上传 → 落库 */
export async function processTask(task, workerId = 'worker') {  const db = getDb();
  const startedAt = Date.now();
  const user = await userService.findById(task.user_id);
  if (!user) {
    await failTask(task, '账号不存在');
    return { ok: false, reason: '账号不存在' };
  }

  const tempFiles = [];
  try {
    // ① 解析（多线路降级）
    const { result, route, attempts } = await resolveWithFallback({
      platform: task.platform,
      url: task.share_url,
      timeoutMs: (await getInt('parse_timeout_sec', 25)) * 1000,
    });

    await db.update(
      'dw_task',
      { id: task.id },
      {
        route_id: route.id,
        item_id: result.itemId ? String(result.itemId).slice(0, 64) : null,
        platform: result.platform && result.platform !== 'unknown' ? result.platform : task.platform,
        media_type: result.mediaType,
        title: result.title ? String(result.title).slice(0, 255) : null,
        cover: result.cover ? String(result.cover).slice(0, 512) : null,
        images: result.images?.length ? JSON.stringify(result.images.map((i) => i.url)) : null,
        src_url: result.videoUrl ? String(result.videoUrl).slice(0, 512) : null,
        video_ms: result.durationMs || null,
      },
    );
    void attempts;

    const policy = await watermarkPolicy(user.level);
    const applyWatermark = Number(task.watermark) === 1;
    const meta = {
      userId: user.id,
      nickname: user.nickname || user.username,
      platform: result.platform,
      platformName: result.platformName || platformName(result.platform),
      taskNo: task.task_no,
      title: result.title,
    };

    /* ------------------------- 图集 ------------------------- */
    if (result.mediaType === 'IMAGE' || (!result.videoUrl && result.images?.length)) {
      await db.update('dw_task', { id: task.id }, { status: TASK_STATUS.COMPOSING, started_at: task.started_at || now() });
      const outImages = [];
      for (const [index, image] of result.images.entries()) {
        const downloaded = await storage.downloadToTemp(image.url, { ext: guessExt(image.url), timeoutMs: 45000 });
        tempFiles.push(downloaded.file);
        let finalFile = downloaded.file;
        if (applyWatermark && policy.enabled) {
          const outFile = path.join(config.storage.tmpDir, `wm-img-${Date.now().toString(36)}-${index}${guessExt(image.url)}`);
          await watermark.composeImage({
            input: downloaded.file,
            output: outFile,
            policy,
            meta,
          });
          tempFiles.push(outFile);
          finalFile = outFile;
        }
        const outKey = storage.buildKey('images', guessExt(image.url));
        const uploaded = await storage.putFile(outKey, finalFile, storage.contentTypeOf(outKey));
        outImages.push({ key: uploaded.key, size: uploaded.size });
      }
      const first = outImages[0];
      await db.update(
        'dw_task',
        { id: task.id },
        {
          status: TASK_STATUS.SUCCESS,
          out_key: first?.key || null,
          out_url: null,
          out_size: outImages.reduce((n, i) => n + (i.size || 0), 0),
          images: JSON.stringify(outImages.map((i) => i.key)),
          duration_ms: Date.now() - startedAt,
          finished_at: now(),
          err_msg: null,
        },
      );
      await userService.addUserStats(db, user.id, { taskDelta: 1 });
      log.info('图集任务完成', { taskNo: task.task_no, images: outImages.length });
      return { ok: true, mediaType: 'IMAGE', images: outImages.length };
    }

    /* ------------------------- 视频 ------------------------- */
    const sourceUrl = result.videoUrl;
    if (!sourceUrl) throw new Error('解析结果没有视频地址');

    // 解析一结束就切「合成中」。下载 + 合成 + 上传都在这一段里，
    // 客户端因此一解析完就从 45% 走到 80% —— 以前这一步要等原片下载完才切状态，
    // 下载慢的时候进度条就长时间钉在 45%（2026-10-17 抖音实测）。
    await db.update('dw_task', { id: task.id }, {
      status: TASK_STATUS.COMPOSING,
      started_at: task.started_at || now(),
    });

    // 抖音一次会给多个 CDN 地址，挨个试；卡住的地址 20 秒就放弃换下一条
    const candidates = result.videoUrls?.length ? result.videoUrls : [sourceUrl];
    const downloaded = await storage.downloadFirstAvailable(candidates, {
      ext: '.mp4',
      timeoutMs: 180000,
      stallMs: 20000,
      referer: downloadReferer(result.platform, sourceUrl),
    });
    tempFiles.push(downloaded.file);

    // 原片也进存储：既作为缓存，也满足投诉溯源时定位原始素材的需要。
    // move:false 很关键 —— 这个临时文件紧接着还要交给 FFmpeg 做水印合成。
    const srcKey = storage.buildKey('origin', '.mp4');
    await storage.putFile(srcKey, downloaded.file, 'video/mp4', { move: false }).catch((err) => {
      log.warn('原片转存失败（不影响成品生成）', { err: err.message });
      return null;
    });
    await db.update('dw_task', { id: task.id }, { src_key: srcKey });

    // 合成：水印在服务端加，客户端拿不到未加印的流
    let finalFile = downloaded.file;
    let elapsedMs = Date.now() - startedAt;
    if (applyWatermark && policy.enabled) {
      const outFile = path.join(config.storage.tmpDir, `wm-${task.task_no}-${randomHex(4)}.mp4`);
      const composed = await watermark.compose({
        input: downloaded.file,
        output: outFile,
        policy,
        meta,
        logoPath: policy.logo || (await getJson('watermark_logo', '')) || null,
      });
      tempFiles.push(outFile);
      finalFile = outFile;
      elapsedMs = composed.elapsedMs;
      await db.update('dw_task', { id: task.id }, { video_ms: composed.durationMs || null });
    }

    const outKey = storage.buildKey('out', '.mp4');
    const uploaded = await storage.putFile(outKey, finalFile, 'video/mp4');

    await db.update(
      'dw_task',
      { id: task.id },
      {
        status: TASK_STATUS.SUCCESS,
        out_key: outKey,
        out_url: null,
        out_size: uploaded.size,
        duration_ms: Date.now() - startedAt,
        finished_at: now(),
        err_msg: null,
      },
    );
    await userService.addUserStats(db, user.id, { taskDelta: 1 });
    log.info('视频任务完成', { taskNo: task.task_no, size: uploaded.size, watermark: applyWatermark, ms: Date.now() - startedAt });
    return { ok: true, mediaType: 'VIDEO', size: uploaded.size, watermark: applyWatermark, elapsed_ms: elapsedMs };
  } catch (err) {
    await failTask(task, err.message);
    return { ok: false, reason: err.message };
  } finally {
    for (const file of tempFiles) {
      fs.promises.rm(file, { force: true }).catch(() => {});
    }
  }
}

function guessExt(url) {
  const m = String(url).match(/\.(jpg|jpeg|png|webp|gif|heic)(\?|$)/i);
  return m ? `.${m[1].toLowerCase()}` : '.jpg';
}

/** 任务失败：写状态、退配额、告警（超时不计费） */
export async function failTask(task, message, status = TASK_STATUS.FAILED) {
  const db = getDb();
  const errMsg = String(message || '未知错误').slice(0, 255);
  await db.update(
    'dw_task',
    { id: task.id },
    { status, err_msg: errMsg, duration_ms: task.started_at ? Date.now() - (parseDateTime(task.started_at)?.getTime() || Date.now()) : null, finished_at: now() },
  );
  const user = await userService.findById(task.user_id);
  if (user) await quotaService.refundQuota(user).catch(() => {});
  log.warn('任务失败', { taskNo: task.task_no, status, err: errMsg });
  return true;
}

/** 重试一次（换备用线路）：规划书 3.3 的失败补偿 */
export async function retryTask(task) {
  const db = getDb();
  const maxRetry = await getInt('task_retry', 1);
  if (Number(task.retry_count || 0) >= maxRetry) return false;
  await db.update(
    'dw_task',
    { id: task.id },
    {
      status: TASK_STATUS.QUEUED,
      retry_count: Number(task.retry_count || 0) + 1,
      err_msg: null,
      worker_id: null,
      started_at: null,
    },
  );
  wakeWorker();
  log.info('任务重试', { taskNo: task.task_no, retry: Number(task.retry_count || 0) + 1 });
  return true;
}

/* ------------------------------------------------------------------ */
/*                            查询 / 管理                              */
/* ------------------------------------------------------------------ */

/** 下发前把 out_key 换成短时效签名 URL（私有桶 + 签名 URL + 15 分钟） */
export function taskPayload(task, { ttlSec = config.storage.urlTtlSec } = {}) {
  const images = safeParseArray(task.images);
  const isImage = String(task.media_type || '').toUpperCase() === 'IMAGE' && images.length > 0;
  const payload = {
    task_no: task.task_no,
    status: Number(task.status),
    status_text: TASK_STATUS_TEXT[Number(task.status)] || '未知',
    platform: task.platform,
    platform_name: platformName(task.platform),
    media_type: isImage ? 'IMAGE' : 'VIDEO',
    title: task.title || '',
    cover: task.cover || null,
    watermark: Number(task.watermark),
    out_size: task.out_size ? Number(task.out_size) : null,
    duration: task.video_ms ? Math.round(Number(task.video_ms) / 100) / 10 : null,
    duration_ms: task.duration_ms ? Number(task.duration_ms) : null,
    err_msg: task.err_msg || null,
    created_at: task.created_at,
    finished_at: task.finished_at,
    expire_sec: ttlSec,
    out_url: null,
    images: [],
  };

  if (Number(task.status) === TASK_STATUS.SUCCESS) {
    if (isImage) {
      payload.images = images.map((key) => storage.signedUrl(key, ttlSec));
      payload.out_url = payload.images[0] || null;
    } else if (task.out_key) {
      payload.out_url = storage.signedUrl(task.out_key, ttlSec);
    }
  }
  return payload;
}

function safeParseArray(text) {
  if (!text) return [];
  try {
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export async function getTask(taskNo, userId) {
  const task = await getDb().get('SELECT * FROM `dw_task` WHERE `task_no` = ?', [String(taskNo)]);
  if (!task) throw new ApiError(ERROR.NOT_FOUND, '任务不存在');
  if (userId && Number(task.user_id) !== Number(userId)) {
    throw new ApiError(ERROR.NOT_FOUND, '任务不存在');
  }
  return task;
}

export async function listHistory({ userId, status, page = 1, limit = 20 }) {
  const db = getDb();
  const where = ['`user_id` = ?'];
  const params = [userId];
  if (status !== undefined && status !== null && status !== '') {
    where.push('`status` = ?');
    params.push(Number(status));
  }
  const clause = where.join(' AND ');
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`dw_task\` WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT * FROM \`dw_task\` WHERE ${clause} ORDER BY \`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows: rows.map((t) => taskPayload(t)), page, limit };
}

export async function deleteTask(taskNo, userId) {
  const db = getDb();
  const task = await getTask(taskNo, userId);
  await db.remove('dw_task', { id: task.id });
  return true;
}

/** 批量提交（仅高等级用户，规划书 8.2 /dw/batch） */
export async function createBatchTasks({ user, links, wantWatermark, ip, deviceId }) {
  const max = await getInt('batch_max', 10);
  if (links.length > max) throw new ApiError(ERROR.PARAM, `单次最多提交 ${max} 条`);
  const minLevel = await getInt('batch_min_level', 3);
  if (Number(user.level) < minLevel) throw new ApiError(ERROR.PARAM, `批量解析需要等级 ${minLevel} 及以上`);

  const results = [];
  for (const raw of links) {
    try {
      const created = await createTask({
        user,
        url: raw,
        wantWatermark,
        ip,
        deviceId,
        priority: Number(user.level) >= 3 ? 'high' : 'normal',
      });
      results.push({ link: raw, ok: true, task_no: created.task.task_no, reused: created.reused });
    } catch (err) {
      results.push({ link: raw, ok: false, error: err.msg || err.message });
    }
  }
  return results;
}

/* ------------------------------------------------------------------ */
/*                          超时 / 队列监控                            */
/* ------------------------------------------------------------------ */

/** 超时扫描：解析/合成中的任务超时标记为 5，并退配额（超时不计费） */
export async function timeoutScan() {
  const db = getDb();
  const timeoutSec = await getInt('task_timeout_sec', config.media.taskTimeoutSec);
  const cutoff = formatDateTime(addHours(new Date(), -timeoutSec / 3600 - 0.001));
  const rows = await db.query('SELECT * FROM `dw_task` WHERE `status` IN (?, ?) AND `started_at` IS NOT NULL AND `started_at` < ?', [
    TASK_STATUS.PARSING,
    TASK_STATUS.COMPOSING,
    cutoff,
  ]);
  for (const task of rows) {
    await failTask(task, `处理超时（>${timeoutSec}s）`, TASK_STATUS.TIMEOUT);
  }
  return rows.length;
}

/** 队列积压（监控告警用）：>50 触发告警 */
export async function queueStats() {
  const db = getDb();
  const queued = Number((await db.get('SELECT COUNT(*) AS n FROM `dw_task` WHERE `status` = ?', [TASK_STATUS.QUEUED]))?.n || 0);
  const running = Number(
    (await db.get('SELECT COUNT(*) AS n FROM `dw_task` WHERE `status` IN (?, ?)', [TASK_STATUS.PARSING, TASK_STATUS.COMPOSING]))?.n || 0,
  );
  const kvHigh = await kv().llen(keys.queue('high')).catch(() => 0);
  const kvNormal = await kv().llen(keys.queue('normal')).catch(() => 0);
  return { queued, running, kv_high: kvHigh, kv_normal: kvNormal, backlog: queued + running };
}

/** 后台任务列表 */
export async function adminListTasks({ keyword, status, platform, startDate, endDate, page = 1, limit = 20 }) {
  const db = getDb();
  const where = [];
  const params = [];
  if (keyword) {
    where.push('(t.`task_no` LIKE ? OR t.`item_id` LIKE ? OR t.`title` LIKE ?)');
    const like = `%${keyword}%`;
    params.push(like, like, like);
  }
  if (status !== undefined && status !== null && status !== '') {
    where.push('t.`status` = ?');
    params.push(Number(status));
  }
  if (platform) {
    where.push('t.`platform` = ?');
    params.push(platform);
  }
  if (startDate) {
    where.push('t.`created_at` >= ?');
    params.push(`${startDate} 00:00:00`);
  }
  if (endDate) {
    where.push('t.`created_at` <= ?');
    params.push(`${endDate} 23:59:59`);
  }
  const clause = where.length ? where.join(' AND ') : '1 = 1';
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`dw_task\` t WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT t.*, u.\`username\`, u.\`remark\` AS user_remark FROM \`dw_task\` t LEFT JOIN \`app_user\` u ON u.id = t.user_id ` +
      `WHERE ${clause} ORDER BY t.\`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  return { total, rows, page, limit };
}

/** 投诉溯源（规划书 0 节的硬性约束）：按用户 / 任务 / item_id 定位到具体成品 */
export async function traceTasks({ userId, itemId, taskNo, ip, limit = 50 }) {
  const db = getDb();
  const where = [];
  const params = [];
  if (userId) {
    where.push('`user_id` = ?');
    params.push(Number(userId));
  }
  if (itemId) {
    where.push('`item_id` = ?');
    params.push(String(itemId));
  }
  if (taskNo) {
    where.push('`task_no` = ?');
    params.push(String(taskNo));
  }
  if (ip) {
    where.push('`client_ip` = ?');
    params.push(String(ip));
  }
  if (!where.length) throw new ApiError(ERROR.PARAM, '至少给出一个溯源条件');
  return db.query(`SELECT * FROM \`dw_task\` WHERE ${where.join(' AND ')} ORDER BY \`id\` DESC LIMIT ?`, [
    ...params,
    Math.min(200, toInt(limit, 50)),
  ]);
}

/** 平台列表（/dw/platforms） */
export async function supportedPlatforms() {
  const routes = await getDb().query('SELECT DISTINCT `platform` FROM `dw_route` WHERE `enabled` = 1');
  const enabledPlatforms = new Set(routes.map((r) => r.platform));
  return {
    platforms: platformList(),
    all_platforms_enabled: enabledPlatforms.has('all'),
    enabled_keys: [...enabledPlatforms],
  };
}

export { platformName, platformList };
export default {
  TASK_STATUS,
  TASK_STATUS_TEXT,
  createTask,
  createBatchTasks,
  claimTask,
  processTask,
  retryTask,
  failTask,
  taskPayload,
  getTask,
  listHistory,
  deleteTask,
  timeoutScan,
  queueStats,
  adminListTasks,
  traceTasks,
  supportedPlatforms,
  wakeWorker,
  waitForWake,
};
