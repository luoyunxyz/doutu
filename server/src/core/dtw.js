/**
 * 私有传输协议 DTW/1 —— 核心实现（2026-10-17）。
 *
 * 为什么要有它（以及它**不能**干什么）：
 *   · APK 里原本编译着服务端 MASTER_KEY，反编译即可伪造官方客户端、铸造下载签名、
 *     甚至推导会话密钥。DTW 的第一目标是**让客户端不再持有任何长期服务端密钥**。
 *   · 协议本身挡不住铁了心的逆向者 —— 他能逆向出算法，也能照做一遍。
 *     它挡的是「抓包就能读、改包就能玩」这一档：流量是密文、载荷带时间窗与序号、
 *     服务端按设备限流并可随时吊销。真正的边界仍然是「服务端不信任客户端」。
 *
 * 密钥分工（互相隔离，泄露一个不牵连其他）：
 *   · DTW 的 RSA 私钥  —— 只用于握手（混合加密 + 签名），客户端只拿**公钥**（可以公开）
 *   · DTW_SALT/盐代     —— 客户端 key 派生用，可轮换
 *   · MASTER_KEY        —— 回服务端自用（存储签名等），**绝不进客户端**
 *
 * 三段式流程：
 *   ① hello   客户端带上 device_id/install_id → 服务端回 { salt, challenge, kid, sig }
 *   ② open    客户端算 proof = SlowHash(salt|challenge|device|install|bucket)、
 *             用服务端公钥 RSA-OAEP 包一个随机 hs_key，再用 hs_key 做 AES-GCM 把
 *             { session_key, proof, ... } 送上来；服务端复算 proof 校验后建会话
 *   ③ tunnel  之后所有请求都是 AES-256-GCM 帧（帧头含 sid/seq/iv），
 *             载荷是 { m, p, q, b, t }（真实 method/path/query/body/时间戳）
 */
import crypto from 'node:crypto';
import config from './../config.js';
import { kv } from './kv.js';
import { ApiError, ERROR } from './errors.js';
import { logger } from './logger.js';
import { sha256Hex, safeEqual, randomHex } from './crypto.js';

const log = logger.child('dtw');

/* ------------------------------ 常量 ------------------------------ */

export const MAGIC = Buffer.from('DTW1', 'ascii');
export const VERSION = 0x01;
/** magic(4) + ver(1) + flags(1) + sid(8) + seq(4) + iv(12) */
export const HEADER_LEN = 30;
export const TAG_LEN = 16;
export const SID_LEN = 8;

const HS_STEP_HELLO = 'hello';
const HS_STEP_OPEN = 'open';

export const dtwCfg = () => config.dtw;

export const isEnabled = () => Boolean(config.dtw.enabled) && Boolean(privateKeyPem());
export const isRequired = () => Boolean(config.dtw.require);

/** 明文放行：握手与自检端点 + 白名单前缀（后台 / 下载 / 健康检查等） */
export function isPlainAllowed(pathname) {
  const p = String(pathname || '');
  if (p === config.dtw.handshakePath) return true;
  // 自检端点也放行：公钥指纹本来就是给客户端核对用的，不含私密材料
  if (p === '/api/v1/dtw/info') return true;
  return (config.dtw.plainWhitelist || []).some((prefix) => prefix && p.startsWith(prefix));
}

/* ------------------------------ 密钥 ------------------------------ */

let cachedPrivate = null;
let cachedPublic = null;

/** 服务端 RSA 私钥（PEM）。没配置就返回空 —— 调用方据此判定「协议未启用」。 */
export function privateKeyPem() {
  if (cachedPrivate === null) cachedPrivate = String(config.dtw.privateKey || '').trim();
  return cachedPrivate;
}

export function publicKeyPem() {
  if (cachedPublic === null) {
    const pem = privateKeyPem();
    if (!pem) return '';
    try {
      cachedPublic = crypto.createPublicKey(pem).export({ type: 'spki', format: 'pem' }).toString();
    } catch (err) {
      log.error('DTW 私钥无法解析', { err: err.message });
      cachedPublic = '';
    }
  }
  return cachedPublic;
}

/** 给客户端的引导信息：公钥、盐、kid —— 这些都可以公开，**不含任何私密材料**。 */
export function bootstrapInfo() {
  return {
    enabled: isEnabled(),
    required: isRequired(),
    version: VERSION,
    kid: config.dtw.kid,
    salt_id: config.dtw.saltId,
    salt: config.dtw.salt,
    public_key: publicKeyPem(),
    proof_iter: config.dtw.proofIter,
    tunnel_path: config.dtw.tunnelPath,
    handshake_path: config.dtw.handshakePath,
  };
}

/* --------------------------- 慢哈希 / 证明 --------------------------- */
/*
 * 客户端 key 的派生（用户定的方案）：时间戳 + 设备信息 + 盐 + 慢哈希。
 *
 * 服务端**能真正验证**这个证明，因为 salt 与 challenge 都是服务端现发的 —— 客户端必须
 * 实打实跑完 N 次迭代才能过这一关，脚本小子拿不到现成的东西直接怼。
 *
 * bucket 是时间片（默认 10 分钟）而不是精确时间戳：否则客户端算出来的值和服务端复算的
 * 对不上（两边时钟差几百毫秒就废）。bucket 让证明在时间片内稳定、跨片失效。
 */
export const PROOF_BUCKET_SEC = 600;

export function bucketOf(atMs = Date.now()) {
  return Math.floor(atMs / 1000 / PROOF_BUCKET_SEC);
}

export function slowHashHex({ salt, challenge, deviceId, installId, bucket }) {
  const material = `${salt}|${challenge}|${deviceId}|${installId}|${bucket}`;
  const iter = Math.max(1000, Number(config.dtw.proofIter) || 20000);
  return crypto.pbkdf2Sync(material, Buffer.from(String(salt || 'dtw')), iter, 32, 'sha256').toString('hex');
}

/** 服务端复算比对（定长比较，避免计时侧信道）。 */
export function verifyProof({ proof, salt, challenge, deviceId, installId, bucket }) {
  if (!proof || !challenge) return false;
  const expected = slowHashHex({ salt, challenge, deviceId, installId, bucket });
  return safeEqual(expected, String(proof).toLowerCase());
}

/* ------------------------------ 签名 ------------------------------ */

export function signServerPayload(payload) {
  const pem = privateKeyPem();
  if (!pem) throw new ApiError(ERROR.SERVER, 'DTW 未配置私钥');
  return crypto.createSign('sha256').update(String(payload)).sign(pem, 'base64');
}

export function verifyServerPayload(payload, sig) {
  const pem = publicKeyPem();
  if (!pem) return false;
  return crypto.createVerify('sha256').update(String(payload)).verify(pem, String(sig || ''), 'base64');
}

/* --------------------------- 对称加解密 --------------------------- */

function keyBuffer(hex) {
  const buf = Buffer.from(String(hex || ''), 'hex');
  if (buf.length !== 32) throw new ApiError(ERROR.PARAM, 'DTW 会话密钥长度不对');
  return buf;
}

/** AES-256-GCM 加密：返回 { iv, ct, tag } */
export function seal(keyHex, plaintext, aad = null) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', keyBuffer(keyHex), iv);
  if (aad) cipher.setAAD(Buffer.from(aad));
  const ct = Buffer.concat([cipher.update(Buffer.from(plaintext)), cipher.final()]);
  return { iv, ct, tag: cipher.getAuthTag() };
}

export function open(keyHex, { iv, ct, tag }, aad = null) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', keyBuffer(keyHex), Buffer.from(iv));
  if (aad) decipher.setAAD(Buffer.from(aad));
  decipher.setAuthTag(Buffer.from(tag));
  return Buffer.concat([decipher.update(Buffer.from(ct)), decipher.final()]);
}

/** RSA-OAEP(SHA-256) 解出客户端用公钥包上来的 hs_key（32 字节） */
export function rsaOpen(encB64) {
  const pem = privateKeyPem();
  if (!pem) throw new ApiError(ERROR.SERVER, 'DTW 未配置私钥');
  return crypto
    .privateDecrypt(
      { key: pem, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' },
      Buffer.from(String(encB64 || ''), 'base64'),
    )
    .toString('hex');
}

/* ------------------------------ 帧 ------------------------------ */

const sidBuf = (sid) => {
  const raw = Buffer.from(String(sid || ''), 'hex');
  if (raw.length < SID_LEN) throw new ApiError(ERROR.PARAM, 'DTW sid 非法');
  return raw.subarray(0, SID_LEN);
};

/**
 * 组帧：magic|ver|flags|sid(8)|seq(4)|iv(12)|ct|tag
 * AAD 取前 18 字节（magic..seq），保证头不可篡改。
 */
export function encodeFrame({ sid, seq, keyHex, payload }) {
  const head = Buffer.alloc(18);
  MAGIC.copy(head, 0);
  head.writeUInt8(VERSION, 4);
  head.writeUInt8(0, 5);
  sidBuf(sid).copy(head, 6);
  head.writeUInt32BE(Number(seq) >>> 0, 14);
  const { iv, ct, tag } = seal(keyHex, payload, head);
  return Buffer.concat([head, iv, ct, tag]);
}

/** 拆帧：先只解出 sid/seq（还不知道 key），供调用方查会话。 */
export function peekFrame(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < HEADER_LEN + TAG_LEN) {
    throw new ApiError(ERROR.PARAM, 'DTW 帧太短');
  }
  if (!buf.subarray(0, 4).equals(MAGIC)) throw new ApiError(ERROR.PARAM, 'DTW 帧标识不对');
  if (buf.readUInt8(4) !== VERSION) throw new ApiError(ERROR.PARAM, 'DTW 协议版本不支持');
  return {
    sid: buf.subarray(6, 14).toString('hex'),
    seq: buf.readUInt32BE(14),
    header: buf.subarray(0, 18),
    rest: buf.subarray(18),
  };
}

/** 用会话密钥解出载荷（对象）。 */
export function decodeFrame(buf, keyHex) {
  const { header, rest } = peekFrame(buf);
  const iv = rest.subarray(0, 12);
  const tag = rest.subarray(rest.length - TAG_LEN);
  const ct = rest.subarray(12, rest.length - TAG_LEN);
  let plain;
  try {
    plain = open(keyHex, { iv, ct, tag }, header).toString('utf8');
  } catch (err) {
    throw new ApiError(ERROR.SIGN, `DTW 帧解密失败：${err.message}`);
  }
  try {
    return JSON.parse(plain);
  } catch {
    throw new ApiError(ERROR.PARAM, 'DTW 载荷不是合法 JSON');
  }
}

/* ------------------------------ 会话 ------------------------------ */

const sessionKey = (sid) => `dtw:sess:${sid}`;
const challengeKey = (cid) => `dtw:chal:${cid}`;

export async function saveChallenge(challenge, deviceId) {
  await kv().set(challengeKey(challenge), String(deviceId || ''), config.dtw.windowSec);
}

/** 取用即焚：challenge 一次性，防止握手包被重放。 */
export async function consumeChallenge(challenge, deviceId) {
  const key = challengeKey(challenge);
  const saved = await kv().get(key);
  if (!saved) return false;
  await kv().del(key).catch(() => {});
  return safeEqual(String(saved), String(deviceId || ''));
}

export async function createSession({ deviceId, installId, sessionKeyHex, appVersion, ip }) {
  const sid = randomHex(SID_LEN);
  const ttl = Number(config.dtw.sessionTtlSec) || 3600;
  await kv().set(
    sessionKey(sid),
    JSON.stringify({
      key: sessionKeyHex,
      device_id: String(deviceId || ''),
      install_id: String(installId || ''),
      app_version: String(appVersion || ''),
      ip: String(ip || ''),
      seq: 0,
      created_at: Date.now(),
    }),
    ttl,
  );
  return { sid, expire_in: ttl };
}

export async function getSession(sid) {
  const raw = await kv().get(sessionKey(sid));
  if (!raw) return null;
  try {
    const data = JSON.parse(raw);
    return data && data.key ? { sid, ...data } : null;
  } catch {
    return null;
  }
}

export async function dropSession(sid) {
  await kv().del(sessionKey(sid)).catch(() => {});
}

/**
 * 序号检查：只允许比已见的最大值更大（允许回退一小段，客户端有并发请求）。
 * 真正的防重放仍由业务层的 nonce 一次性 + 时间窗兜底，这里多一道而已。
 */
export async function acceptSeq(sid, seq, tolerance = 64) {
  const session = await getSession(sid);
  if (!session) return false;
  const last = Number(session.seq) || 0;
  if (seq <= last - tolerance) return false;
  if (seq > last) {
    session.seq = seq;
    const ttl = Number(config.dtw.sessionTtlSec) || 3600;
    await kv()
      .set(sessionKey(sid), JSON.stringify(session), ttl)
      .catch(() => {});
  }
  return true;
}

/* --------------------------- 握手载荷 --------------------------- */

function transcript(parts) {
  return [
    `v=${parts.v}`,
    `kid=${parts.kid}`,
    `sid=${parts.sid || ''}`,
    `challenge=${parts.challenge || ''}`,
    `device=${parts.device_id || ''}`,
    `install=${parts.install_id || ''}`,
    `salt=${parts.salt_id || ''}`,
    `exp=${parts.expire_at || ''}`,
  ].join('&');
}

/** ① hello */
export async function handshakeHello(ctx) {
  const body = ctx.body || {};
  if (String(body.step || '') !== HS_STEP_HELLO) throw new ApiError(ERROR.PARAM, 'DTW 握手步骤不对');
  const deviceId = String(body.device_id || '').trim();
  const installId = String(body.install_id || '').trim();
  if (!deviceId) throw new ApiError(ERROR.PARAM, 'DTW 缺少 device_id');

  const challenge = randomHex(16);
  await saveChallenge(challenge, deviceId);
  const expireAt = Date.now() + (Number(config.dtw.windowSec) || 300) * 1000;

  const payload = transcript({
    v: VERSION,
    kid: config.dtw.kid,
    challenge,
    device_id: deviceId,
    install_id: installId,
    salt_id: config.dtw.saltId,
    expire_at: expireAt,
  });
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      v: VERSION,
      kid: config.dtw.kid,
      salt: config.dtw.salt,
      salt_id: config.dtw.saltId,
      challenge,
      proof_iter: config.dtw.proofIter,
      proof_bucket_sec: PROOF_BUCKET_SEC,
      server_time: Date.now(),
      expire_at: expireAt,
      sig: signServerPayload(payload),
    },
  });
}

/** ② open */
export async function handshakeOpen(ctx) {
  const body = ctx.body || {};
  if (String(body.step || '') !== HS_STEP_OPEN) throw new ApiError(ERROR.PARAM, 'DTW 握手步骤不对');

  let hsKey;
  try {
    hsKey = rsaOpen(body.ek);
  } catch (err) {
    log.warn('DTW 握手包解密失败', { err: err.message, ip: ctx.ip });
    throw new ApiError(ERROR.PARAM, 'DTW 握手包无法解密');
  }

  let inner;
  try {
    // ⚠️ 这里的 iv/ct/tag 是客户端传来的 **base64 字符串**，必须显式按 base64 解码 ——
    // Buffer.from('....') 默认按 utf8 解释，会让 AES-GCM 直接认证失败。
    // （自测脚本当初直接传 Buffer，正好绕过了这个坑，所以这里补了解码也补了用例。）
    inner = JSON.parse(
      open(hsKey, {
        iv: Buffer.from(String(body.iv || ''), 'base64'),
        ct: Buffer.from(String(body.ct || ''), 'base64'),
        tag: Buffer.from(String(body.tag || ''), 'base64'),
      }).toString('utf8'),
    );
  } catch (err) {
    log.warn('DTW 握手载荷解密失败', { err: err.message, ip: ctx.ip });
    throw new ApiError(ERROR.PARAM, 'DTW 握手载荷无法解密');
  }

  const deviceId = String(inner.device_id || '').trim();
  const installId = String(inner.install_id || '').trim();
  const challenge = String(inner.challenge || '');
  if (!deviceId || !challenge) throw new ApiError(ERROR.PARAM, 'DTW 握手内容不完整');

  // challenge 一次性：用了就没了（防重放）
  if (!(await consumeChallenge(challenge, deviceId))) {
    throw new ApiError(ERROR.SIGN, 'DTW challenge 已失效，请重新握手');
  }

  // 时间窗
  const at = Number(inner.ts) || 0;
  const skew = Math.abs(Date.now() - at);
  if (!at || skew > (Number(config.dtw.windowSec) || 300) * 1000) {
    throw new ApiError(ERROR.SIGN, 'DTW 握手时间戳超出允许范围');
  }

  // 慢哈希证明：salt/challenge 都是本服务刚发的，所以这里能真验
  const bucket = Number(inner.bucket);
  if (!verifyProof({ proof: inner.proof, salt: config.dtw.salt, challenge, deviceId, installId, bucket })) {
    log.warn('DTW 慢哈希证明不通过', { ip: ctx.ip, device: deviceId.slice(0, 8) });
    throw new ApiError(ERROR.SIGN, 'DTW 证明校验失败');
  }

  const sessionKeyHex = String(inner.session_key || '').trim();
  if (!/^[0-9a-f]{64}$/i.test(sessionKeyHex)) throw new ApiError(ERROR.PARAM, 'DTW 会话密钥格式不对');

  const { sid, expire_in: expireIn } = await createSession({
    deviceId,
    installId,
    sessionKeyHex,
    appVersion: inner.app_ver,
    ip: ctx.ip,
  });

  const expireAt = Date.now() + expireIn * 1000;
  const payload = transcript({
    v: VERSION,
    kid: config.dtw.kid,
    sid,
    challenge,
    device_id: deviceId,
    install_id: installId,
    salt_id: config.dtw.saltId,
    expire_at: expireAt,
  });
  log.info('DTW 会话建立', { sid, device: deviceId.slice(0, 8), ip: ctx.ip });
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      v: VERSION,
      kid: config.dtw.kid,
      sid,
      expire_at: expireAt,
      seq_start: 1,
      sig: signServerPayload(payload),
    },
  });
}

/** 握手总入口：按 step 分发 */
export async function handshake(ctx) {
  const step = String((ctx.body || {}).step || '');
  if (step === HS_STEP_HELLO) return handshakeHello(ctx);
  if (step === HS_STEP_OPEN) return handshakeOpen(ctx);
  throw new ApiError(ERROR.PARAM, 'DTW 握手 step 只能是 hello / open');
}

/**
 * 公钥指纹：对 **DER**（SubjectPublicKeyInfo 的二进制）取哈希，而不是对 PEM 文本。
 * PEM 换个行尾（CRLF/LF）、多个换行都会让文本哈希变样，而客户端是按 DER 算的 ——
 * 用 DER 两边才稳定可比。客户端拿它跟内置公钥核对，防止中间人换钥匙。
 */
export function keyFingerprint() {
  const pem = publicKeyPem();
  if (!pem) return '';
  try {
    const der = crypto.createPublicKey(pem).export({ type: 'spki', format: 'der' });
    return sha256Hex(der).slice(0, 16);
  } catch {
    return '';
  }
}

/* --------------------------- 隧道收发 --------------------------- */

/**
 * 拆一个隧道请求帧 → 还原成「客户端真正想发的那个请求」。
 *
 * 返回的 method/pathname/query/body/rawBody 会被 http 内核塞回 ctx，
 * 然后照常匹配路由、跑业务中间件 —— 业务代码一行都不用改。
 *
 * rawBody 特别关键：客户端签名是对**它原本要发的那个 JSON 串**取 SHA256 的，
 * 所以载荷里带 `rb` 原文；用解出来的对象再 stringify 会因为键序不同导致验签失败。
 */
export async function openTunnelRequest(buf, { ip } = {}) {
  const peek = peekFrame(buf);
  const session = await getSession(peek.sid);
  if (!session) throw new ApiError(ERROR.SESSION_INVALID, 'DTW 会话不存在或已过期');

  if (!(await acceptSeq(peek.sid, peek.seq))) {
    log.warn('DTW 序号异常', { sid: peek.sid, seq: peek.seq, ip });
    throw new ApiError(ERROR.SIGN, 'DTW 序号异常');
  }

  const payload = decodeFrame(buf, session.key);
  const method = String(payload.m || 'POST').toUpperCase();
  const pathname = String(payload.p || '');
  if (!pathname.startsWith('/')) throw new ApiError(ERROR.PARAM, 'DTW 目标路径非法');

  const at = Number(payload.t) || 0;
  const windowMs = (Number(config.dtw.windowSec) || 300) * 1000;
  if (!at || Math.abs(Date.now() - at) > windowMs) {
    throw new ApiError(ERROR.SIGN, 'DTW 请求时间戳超出允许范围');
  }

  const rawBody = typeof payload.rb === 'string' ? payload.rb : '';
  let body = payload.b;
  if (!body && rawBody) {
    try {
      body = JSON.parse(rawBody);
    } catch {
      body = {};
    }
  }

  return {
    sid: peek.sid,
    seq: peek.seq,
    key: session.key,
    deviceId: session.device_id,
    method,
    pathname,
    query: payload.q && typeof payload.q === 'object' ? payload.q : {},
    body: body && typeof body === 'object' ? body : {},
    rawBody,
    // 客户端自带的请求头（X-Timestamp / X-Nonce / X-Device-Id / X-Sign …）。
    // 业务层的验签、时间窗、重放拦截全靠它们，所以隧道必须原样带进去。
    // 安全性没有削弱：这些头本来就是客户端自己能设的，服务端从不把它们当可信来源
    // （真实 IP 走 socket，权限走签名与会话）。
    headers: payload.h && typeof payload.h === 'object' ? payload.h : {},
  };
}

/**
 * 把业务响应封成隧道帧。
 * 载荷里带着原始 HTTP 状态与 Content-Type，客户端按原样还原，业务语义不变。
 */
export function sealTunnelResponse(keyHex, sid, seq, { status = 200, contentType = 'application/json; charset=utf-8', body = '' } = {}) {
  const payload = JSON.stringify({
    s: status,
    ct: contentType,
    b: Buffer.isBuffer(body) ? body.toString('base64') : Buffer.from(String(body), 'utf8').toString('base64'),
  });
  return encodeFrame({ sid, seq, keyHex, payload });
}

export default {
  MAGIC,
  VERSION,
  HEADER_LEN,
  TAG_LEN,
  dtwCfg,
  isEnabled,
  isRequired,
  bootstrapInfo,
  publicKeyPem,
  privateKeyPem,
  keyFingerprint,
  slowHashHex,
  verifyProof,
  signServerPayload,
  verifyServerPayload,
  seal,
  open,
  rsaOpen,
  encodeFrame,
  peekFrame,
  decodeFrame,
  createSession,
  getSession,
  dropSession,
  acceptSeq,
  openTunnelRequest,
  sealTunnelResponse,
  handshake,
  bucketOf,
  PROOF_BUCKET_SEC,
};
