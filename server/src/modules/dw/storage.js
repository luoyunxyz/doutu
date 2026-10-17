/**
 * 成品与原片存储：本地磁盘（local）与阿里云 OSS 私有桶（oss）两种驱动。
 *
 * 规划书 3.1 第 6 步与 8.3 的要求：成品必须是**私有桶 + 签名 URL + 短有效期**，
 * 公网可直连的成品地址等于把整套水印体系送给别人。
 *   local：/dw/file/{key}?exp=...&sign=... 由本服务校验后返回
 *   oss  ：服务端生成带 Signature 的临时 URL，浏览器直连 OSS，不消耗服务器带宽
 */
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { Readable, Transform } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import config, { storageSecretEffective, masterKeyEffective } from '../../config.js';
import { logger } from '../../core/logger.js';
import { hmacSha256Hex, safeEqual, md5Hex, randomHex } from '../../core/crypto.js';

const log = logger.child('storage');

/**
 * 下载链接的签名密钥：**独立于 MASTER_KEY**（没配 STORAGE_SECRET 时回退，保持兼容）。
 *
 * 2026-10-17 起不再用主密钥签下载链接：这个密钥每天都在用，泄露面比引导密钥大得多，
 * 一把钥匙只干一件事，出事才不会连锁。
 */
const secret = () => storageSecretEffective();
/** 老的（主密钥签名）口径：部署切换后一段时间内还要认旧链接，避免用户手里刚生成的链接全废 */
const legacySecret = () => masterKeyEffective();

/** 生成存储 key：按天分目录，避免单目录文件过多 */
export function buildKey(kind, ext = '.mp4', date = new Date()) {
  const day = date.toISOString().slice(0, 10);
  return `${kind}/${day}/${Date.now().toString(36)}${randomHex(6)}${ext}`;
}

export const localPathOf = (key) => path.join(config.storage.localDir, key);

function ensureDir(file) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
}

/* --------------------------- 签名 URL --------------------------- */

/** 本地驱动的签名：sign = HMAC(secret, `${key}:${exp}`) */
function signLocal(key, exp) {
  return hmacSha256Hex(secret(), `${key}:${exp}`);
}

export function signedUrl(key, ttlSec = config.storage.urlTtlSec) {
  if (!key) return null;
  const exp = Math.floor(Date.now() / 1000) + ttlSec;
  if (config.storage.driver === 'oss') return ossSignedUrl(key, exp);
  const sign = signLocal(key, exp);
  return `${config.storage.publicBase}/dw/file/${encodeURI(key)}?exp=${exp}&sign=${sign}`;
}

/** 校验本地下载链接（过期 / 篡改都拒绝）。新旧两套密钥都认，链接不会因为换密钥集体失效。 */
export function verifyLocalUrl(key, exp, sign) {
  const expNum = Number(exp);
  if (!Number.isFinite(expNum) || expNum * 1000 < Date.now()) return { ok: false, reason: '链接已过期' };
  const given = String(sign || '');
  if (safeEqual(signLocal(key, expNum), given)) return { ok: true };
  // 回退：老链接是主密钥签的（换 STORAGE_SECRET 的当天还能用）
  if (safeEqual(hmacSha256Hex(legacySecret(), `${key}:${expNum}`), given)) return { ok: true };
  return { ok: false, reason: '签名不匹配' };
}

/* --------------------------- 本地驱动 --------------------------- */

async function putLocal(key, source, { move = true } = {}) {
  const target = localPathOf(key);
  ensureDir(target);
  if (typeof source === 'string') {
    if (move) {
      // 同分区直接 rename，跨分区退回 copy
      try {
        await fsp.rename(source, target);
      } catch {
        await fsp.copyFile(source, target);
        await fsp.rm(source, { force: true });
      }
    } else {
      // 还要继续用这个临时文件（例如原片转存后马上要拿它做水印合成）时只复制
      await fsp.copyFile(source, target);
    }
  } else {
    await fsp.writeFile(target, source);
  }
  const stat = await fsp.stat(target);
  return { key, size: stat.size, url: signedUrl(key) };
}

async function getLocalToFile(key, destPath) {
  const source = localPathOf(key);
  ensureDir(destPath);
  await fsp.copyFile(source, destPath);
  return destPath;
}

/* --------------------------- OSS 驱动 --------------------------- */

/**
 * 阿里云 OSS V1 签名（PutObject / GetObject）。
 * 说明：这里实现的是标准 OSS 签名算法，未在本机验证过真实 OSS 调用；
 * 上线前请用 `scripts/oss-check.js`（见 docs/DEPLOY.md）核对一次。
 */
const ossHost = () => `${config.storage.oss.bucket}.${config.storage.oss.endpoint}`;
const ossObjectUrl = (key) => `https://${ossHost()}/${key.replace(/^\/+/, '')}`;

function ossAuthHeader(method, key, contentType, contentMd5, dateStr) {
  const stringToSign = [method, contentMd5 || '', contentType || '', dateStr, `/${config.storage.oss.bucket}/${key}`].join('\n');
  const signature = crypto
    .createHmac('sha1', config.storage.oss.accessKeySecret)
    .update(stringToSign)
    .digest('base64');
  return `OSS ${config.storage.oss.accessKeyId}:${signature}`;
}

function ossReady() {
  const { bucket, endpoint, accessKeyId, accessKeySecret } = config.storage.oss;
  return Boolean(bucket && endpoint && accessKeyId && accessKeySecret);
}

async function putOss(key, body, contentType = 'video/mp4') {
  if (!ossReady()) throw new Error('OSS 未配置完整（bucket/endpoint/AccessKey）');
  const dateStr = new Date().toUTCString();
  const contentMd5 = crypto.createHash('md5').update(body).digest('base64');
  const res = await fetch(ossObjectUrl(key), {
    method: 'PUT',
    headers: {
      Date: dateStr,
      'Content-Type': contentType,
      'Content-MD5': contentMd5,
      Authorization: ossAuthHeader('PUT', key, contentType, contentMd5, dateStr),
    },
    body,
  });
  if (!res.ok) throw new Error(`OSS 上传失败：HTTP ${res.status} ${(await res.text()).slice(0, 200)}`);
  return { key, size: body.length, url: ossObjectUrl(key) };
}

/** OSS 签名 URL（GET 直连） */
export function ossSignedUrl(key, exp) {
  const stringToSign = ['GET', '', '', String(exp), `/${config.storage.oss.bucket}/${key}`].join('\n');
  const signature = crypto
    .createHmac('sha1', config.storage.oss.accessKeySecret)
    .update(stringToSign)
    .digest('base64');
  return (
    `${ossObjectUrl(key)}?OSSAccessKeyId=${encodeURIComponent(config.storage.oss.accessKeyId)}` +
    `&Expires=${exp}&Signature=${encodeURIComponent(signature)}`
  );
}

/* --------------------------- 统一接口 --------------------------- */

/**
 * 把本地文件放进存储。
 * @param {object} options move=false 时保留源文件（原片转存后还要用它做合成）
 */
export async function putFile(key, localFilePath, contentType = 'video/mp4', options = {}) {
  if (config.storage.driver === 'oss') {
    const body = await fsp.readFile(localFilePath);
    const result = await putOss(key, body, contentType);
    if (options.move !== false) await fsp.rm(localFilePath, { force: true });
    return { ...result, url: signedUrl(key) };
  }
  const result = await putLocal(key, localFilePath, options);
  return { ...result, url: signedUrl(key) };
}

export async function putBuffer(key, buffer, contentType = 'video/mp4') {
  if (config.storage.driver === 'oss') {
    const result = await putOss(key, buffer, contentType);
    return { ...result, url: signedUrl(key) };
  }
  return putLocal(key, buffer);
}

/**
 * 下载上游直链到本地临时文件（原片转存，规划书 3.1 第 4 步）。
 *
 * 2026-10-17 抖音「进度条卡在 45%」之后改的三个点：
 *   1. **流式落盘**：以前是把整个视频 arrayBuffer 读进内存再写盘 —— 大视频顶内存，
 *      而且中途没有任何东西可观测；现在边收边写。
 *   2. **按「多久没收到数据」判死**（stallMs），不再只看总时长。抖音 CDN 偶尔直接卡住
 *      不动，以前要占满 120 秒才 abort，把 worker（并发 2）和整个队列一起堵死；
 *      现在 20 秒没数据就失败，任务立刻释放。
 *   3. **Referer 按平台给**：抖音 CDN 上同一段视频，带 https://www.douyin.com/ 比带
 *      自己域名快约 7 倍（实测 674ms vs 4551ms），所以由调用方传进来。
 */
export async function downloadToTemp(url, { timeoutMs = 180000, stallMs = 20000, ext = '.mp4', referer = null } = {}) {
  const controller = new AbortController();
  const file = path.join(config.storage.tmpDir, `src-${Date.now().toString(36)}-${randomHex(4)}${ext}`);
  fs.mkdirSync(path.dirname(file), { recursive: true });

  let totalTimer = null;
  let stallTimer = null;
  const armStall = () => {
    clearTimeout(stallTimer);
    stallTimer = setTimeout(() => controller.abort(new Error(`下载卡住：${Math.round(stallMs / 1000)} 秒没有收到数据`)), stallMs);
  };
  const clearTimers = () => {
    clearTimeout(totalTimer);
    clearTimeout(stallTimer);
  };

  let size = 0;
  try {
    totalTimer = setTimeout(() => controller.abort(new Error(`下载超时（>${Math.round(timeoutMs / 1000)} 秒）`)), timeoutMs);
    armStall();

    const headers = {
      'User-Agent':
        'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36',
      Referer: referer || new URL(url).origin,
    };
    const res = await fetch(url, { signal: controller.signal, headers });
    if (!res.ok) throw new Error(`下载原片失败：HTTP ${res.status}`);

    const meter = new Transform({
      transform(chunk, _enc, done) {
        size += chunk.length;
        armStall();          // 每收到一块就重新计时：慢但一直在动的不算卡住
        done(null, chunk);
      },
    });
    await pipeline(Readable.fromWeb(res.body), meter, fs.createWriteStream(file));
    if (!size) throw new Error('下载原片失败：内容为空');

    const buffer = await fsp.readFile(file);
    return { file, size, md5: md5Hex(buffer) };
  } catch (err) {
    await fsp.rm(file, { force: true }).catch(() => {});
    // AbortController 的 abort(reason) 在 Node 里会以 reason 抛出；统一成人话
    const message = err && err.name === 'AbortError' ? String(err.cause?.message || err.message) : String(err?.message || err);
    throw new Error(message);
  } finally {
    clearTimers();
  }
}

/**
 * 依次尝试若干条直链，第一条成功就返回。
 *
 * 抖音一次解析会给出 video_list（同一段视频的多个 CDN 地址），以前只用第一条，
 * 第一条不通就整个任务失败；现在挨个试，任意一条通就算成功。
 */
export async function downloadFirstAvailable(urls, options = {}) {
  const list = [...new Set((urls || []).filter(Boolean))];
  if (!list.length) throw new Error('没有可下载的视频地址');
  let lastError = null;
  for (let i = 0; i < list.length; i += 1) {
    try {
      const out = await downloadToTemp(list[i], options);
      if (i > 0) log.warn('主地址失败，备用地址下载成功', { index: i, url: list[i].slice(0, 80) });
      return { ...out, url: list[i] };
    } catch (err) {
      lastError = err;
      log.warn('原片地址下载失败', { index: i, total: list.length, err: err.message });
    }
  }
  throw lastError || new Error('下载原片失败');
}

/** 直链转存到存储（原片缓存用） */
export async function transferFromUrl(url, key, { ext = '.mp4', timeoutMs = 60000 } = {}) {
  const { file, size } = await downloadToTemp(url, { timeoutMs, ext });
  const result = await putFile(key, file, contentTypeOf(key));
  return { ...result, size };
}

export async function exists(key) {
  if (config.storage.driver === 'oss') {
    // 用签名 HEAD 判断；失败按不存在处理
    try {
      const res = await fetch(signedUrl(key, 60), { method: 'HEAD' });
      return res.ok;
    } catch {
      return false;
    }
  }
  try {
    return fs.existsSync(localPathOf(key));
  } catch {
    return false;
  }
}

export async function stat(key) {
  if (config.storage.driver === 'oss') return null;
  try {
    const s = await fsp.stat(localPathOf(key));
    return { size: s.size, mtime: s.mtime };
  } catch {
    return null;
  }
}

export async function remove(key) {
  if (!key) return false;
  if (config.storage.driver === 'oss') {
    try {
      const dateStr = new Date().toUTCString();
      const res = await fetch(ossObjectUrl(key), {
        method: 'DELETE',
        headers: { Date: dateStr, Authorization: ossAuthHeader('DELETE', key, '', '', dateStr) },
      });
      return res.ok;
    } catch (err) {
      log.warn('OSS 删除失败', { key, err: err.message });
      return false;
    }
  }
  try {
    await fsp.rm(localPathOf(key), { force: true });
    return true;
  } catch {
    return false;
  }
}

/** 读取存储对象到内存（本地驱动；OSS 驱动走 HTTP） */
export async function readFile(key) {
  if (config.storage.driver === 'oss') {
    const res = await fetch(signedUrl(key, 300));
    if (!res.ok) throw new Error(`读取 OSS 对象失败：HTTP ${res.status}`);
    return Buffer.from(await res.arrayBuffer());
  }
  return fsp.readFile(localPathOf(key));
}

export function contentTypeOf(key) {
  const ext = path.extname(key).toLowerCase();
  if (ext === '.png') return 'image/png';
  if (ext === '.jpg' || ext === '.jpeg') return 'image/jpeg';
  if (ext === '.webp') return 'image/webp';
  if (ext === '.mp3') return 'audio/mpeg';
  if (ext === '.m4a') return 'audio/mp4';
  if (ext === '.zip') return 'application/zip';
  return 'video/mp4';
}

/**
 * 磁盘水位检查应该看哪个目录。
 * 存储目录可能还没被创建（全新部署、尚未产生任何成品），此时 statfs 会抛 ENOENT，
 * 磁盘告警就会静默失效 —— 所以回退到最近的已存在父目录。
 */
export function usagePath() {
  for (const candidate of [config.storage.localDir, config.storage.tmpDir, config.rootDir]) {
    try {
      if (candidate && fs.existsSync(candidate)) return candidate;
    } catch {
      /* 继续找下一个 */
    }
  }
  return config.rootDir;
}

/** 清理临时目录（cron:cleanup 调用）：超过 minutes 分钟的临时文件删除 */
export async function cleanupTemp(minutes = 120) {
  const dir = config.storage.tmpDir;
  if (!fs.existsSync(dir)) return 0;
  const cutoff = Date.now() - minutes * 60 * 1000;
  let removed = 0;
  for (const name of await fsp.readdir(dir)) {
    const file = path.join(dir, name);
    try {
      const s = await fsp.stat(file);
      if (s.isFile() && s.mtimeMs < cutoff) {
        await fsp.rm(file, { force: true });
        removed += 1;
      }
    } catch {
      /* 忽略单个文件的清理失败 */
    }
  }
  return removed;
}

export default {
  buildKey,
  putFile,
  putBuffer,
  downloadToTemp,
  downloadFirstAvailable,
  transferFromUrl,
  signedUrl,
  verifyLocalUrl,
  exists,
  stat,
  remove,
  readFile,
  contentTypeOf,
  cleanupTemp,
  localPathOf,
  ossSignedUrl,
};
