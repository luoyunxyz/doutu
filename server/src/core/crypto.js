/**
 * 密码学工具：请求签名、口令哈希、TOTP、会话密钥派发。
 *
 * 签名算法（规划书 7.2）：
 *   sign = HMAC-SHA256(timestamp + "\n" + nonce + "\n" + device_id + "\n" + path + "\n" + body_hash, session_sign_key)
 *   body_hash = SHA256(原始请求体) 的 hex（GET 无请求体时为 SHA256("")）
 * 时间窗 ±300s + nonce 一次性，二者都在中间件里做。
 *
 * 口令哈希用 Node 内置 scrypt（N=16384, r=8, p=1），不引入 bcrypt 原生依赖 ——
 * 强度不低于 bcrypt(cost=12)，且没有编译依赖，宝塔环境部署不会卡在 node-gyp。
 */
import crypto from 'node:crypto';

export const sha256Hex = (data) => crypto.createHash('sha256').update(data).digest('hex');
export const md5Hex = (data) => crypto.createHash('md5').update(data).digest('hex');
export const hmacSha256Hex = (key, data) =>
  crypto.createHmac('sha256', key).update(data).digest('hex');

/** 定长比较，避免签名/口令比对被计时侧信道。 */
export function safeEqual(a, b) {
  const bufA = Buffer.from(String(a ?? ''));
  const bufB = Buffer.from(String(b ?? ''));
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

/* --------------------------- 请求签名 --------------------------- */

/** 客户端与服务端必须完全一致的待签串拼接顺序。 */
export function buildSignPayload({ method = 'POST', path, timestamp, nonce, deviceId, bodyHash }) {
  return [String(timestamp), String(nonce), String(deviceId || ''), String(path), String(bodyHash || '')].join(
    '\n',
  );
}

export function signRequest(secret, parts) {
  return hmacSha256Hex(secret, buildSignPayload(parts));
}

export function verifyRequestSign(secret, parts, sign) {
  const expected = signRequest(secret, parts);
  return safeEqual(expected, String(sign || '').toLowerCase());
}

/** 会话级动态密钥：session_sign_key = HMAC(MASTER_KEY, server_random + nonce + device_id) */
export function deriveSessionKey(masterKey, serverRandom, nonce, deviceId) {
  return hmacSha256Hex(masterKey, `${serverRandom}:${nonce}:${deviceId}`);
}

/* --------------------------- 口令哈希 --------------------------- */

const SCRYPT_N = 16384;
const SCRYPT_R = 8;
const SCRYPT_P = 1;
const KEY_LEN = 64;

export function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(String(password), salt, KEY_LEN, {
    N: SCRYPT_N,
    r: SCRYPT_R,
    p: SCRYPT_P,
    maxmem: 256 * 1024 * 1024,
  });
  return `scrypt$${SCRYPT_N}$${SCRYPT_R}$${SCRYPT_P}$${salt.toString('hex')}$${hash.toString('hex')}`;
}

export function verifyPassword(stored, password) {
  const parts = String(stored || '').split('$');
  if (parts.length !== 6 || parts[0] !== 'scrypt') return false;
  const [, n, r, p, saltHex, hashHex] = parts;
  try {
    const hash = crypto.scryptSync(String(password), Buffer.from(saltHex, 'hex'), KEY_LEN, {
      N: Number(n),
      r: Number(r),
      p: Number(p),
      maxmem: 256 * 1024 * 1024,
    });
    return safeEqual(hash.toString('hex'), hashHex);
  } catch {
    return false;
  }
}

/* ------------------------------ TOTP ------------------------------ */

const B32_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

export function base32Encode(buffer) {
  let bits = 0;
  let value = 0;
  let output = '';
  for (const byte of buffer) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      output += B32_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) output += B32_ALPHABET[(value << (5 - bits)) & 31];
  return output;
}

export function base32Decode(input) {
  const str = String(input || '').toUpperCase().replace(/=+$/, '').replace(/\s/g, '');
  let bits = 0;
  let value = 0;
  const out = [];
  for (const ch of str) {
    const idx = B32_ALPHABET.indexOf(ch);
    if (idx === -1) continue;
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

export const generateTotpSecret = () => base32Encode(crypto.randomBytes(20));

export function totpCode(secret, timestamp = Date.now(), step = 30) {
  const counter = Math.floor(timestamp / 1000 / step);
  const buf = Buffer.alloc(8);
  buf.writeUInt32BE(Math.floor(counter / 2 ** 32), 0);
  buf.writeUInt32BE(counter % 2 ** 32, 4);
  const hmac = crypto.createHmac('sha1', base32Decode(secret)).update(buf).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const code =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);
  return String(code % 1_000_000).padStart(6, '0');
}

/** 允许前后各 1 个时间步，容忍客户端与服务端几十秒的时钟漂移。 */
export function verifyTotp(secret, code, window = 1, timestamp = Date.now()) {
  const target = String(code || '').trim();
  if (!target) return false;
  for (let i = -window; i <= window; i += 1) {
    if (safeEqual(totpCode(secret, timestamp + i * 30_000), target)) return true;
  }
  return false;
}

export const otpauthUrl = (secret, label, issuer = 'DoutuAdmin') =>
  `otpauth://totp/${encodeURIComponent(issuer)}:${encodeURIComponent(label)}` +
  `?secret=${secret}&issuer=${encodeURIComponent(issuer)}&algorithm=SHA1&digits=6&period=30`;

/* --------------------------- 杂项 --------------------------- */

/** 生成随机串，用于 nonce / server_random / token */
export const randomHex = (bytes = 16) => crypto.randomBytes(bytes).toString('hex');

/** 设备指纹归一化：统一小写、去空白，避免同设备因大小写不同被算成两台 */
export const normalizeDeviceId = (deviceId) => String(deviceId || '').trim().toLowerCase();

export default {
  sha256Hex,
  md5Hex,
  hmacSha256Hex,
  safeEqual,
  buildSignPayload,
  signRequest,
  verifyRequestSign,
  deriveSessionKey,
  hashPassword,
  verifyPassword,
  totpCode,
  verifyTotp,
  generateTotpSecret,
  otpauthUrl,
  randomHex,
  normalizeDeviceId,
};
