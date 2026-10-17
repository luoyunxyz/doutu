/**
 * 通用工具：时间格式化、ID 生成、分页、随机、对象取值。
 *
 * 时间约定：数据库里所有 datetime 一律 "YYYY-MM-DD HH:mm:ss"（本地时区字符串），
 * 不用 Date 对象入库、不用 SQL 时间函数 —— MySQL 与 SQLite 行为才能一致。
 */
import crypto from 'node:crypto';

/* ----------------------------- 时间 ----------------------------- */

const pad = (n, w = 2) => String(n).padStart(w, '0');

export function formatDateTime(date = new Date()) {
  const d = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(d.getTime())) return null;
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

export function formatDate(date = new Date()) {
  const d = date instanceof Date ? date : new Date(date);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function now() {
  return formatDateTime(new Date());
}

export function today() {
  return formatDate(new Date());
}

export function addDays(date, days) {
  const d = date instanceof Date ? new Date(date.getTime()) : new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

export function addHours(date, hours) {
  const d = date instanceof Date ? new Date(date.getTime()) : new Date(date);
  d.setHours(d.getHours() + hours);
  return d;
}

export function addMinutes(date, minutes) {
  const d = date instanceof Date ? new Date(date.getTime()) : new Date(date);
  d.setMinutes(d.getMinutes() + minutes);
  return d;
}

/** 'YYYY-MM-DD HH:mm:ss' → Date（本地时区）；解析失败返回 null。 */
export function parseDateTime(value) {
  if (!value) return null;
  if (value instanceof Date) return value;
  const s = String(value).trim().replace('T', ' ').replace(/\.\d+Z?$/, '').replace(/Z$/, '');
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[ ](\d{2}):(\d{2})(?::(\d{2}))?)?$/.exec(s);
  if (!m) {
    const d = new Date(value);
    return Number.isNaN(d.getTime()) ? null : d;
  }
  return new Date(
    Number(m[1]),
    Number(m[2]) - 1,
    Number(m[3]),
    Number(m[4] || 0),
    Number(m[5] || 0),
    Number(m[6] || 0),
  );
}

/** 剩余天数（向上取整，过期或无效返回 0）。 */
export function daysLeft(expireAt, from = new Date()) {
  const d = parseDateTime(expireAt);
  if (!d) return 0;
  const ms = d.getTime() - (from instanceof Date ? from.getTime() : new Date(from).getTime());
  if (ms <= 0) return 0;
  return Math.ceil(ms / 86400000);
}

/** 永久卡的到期时间哨兵值（规划书 5.3）。 */
export const PERMANENT_EXPIRE = '2099-12-31 23:59:59';

export const isPermanent = (expireAt) => String(expireAt || '').startsWith('2099-');

/* ----------------------------- 随机 / 哈希 ----------------------------- */

export const randomHex = (bytes = 16) => crypto.randomBytes(bytes).toString('hex');

export const randomToken = (bytes = 32) => crypto.randomBytes(bytes).toString('hex');

/** 卡密字符集：剔除 0/O/1/I/L 等易混淆字符（规划书 5.1） */
export const CARD_CHARSET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

export function randomCode(length = 16, charset = CARD_CHARSET) {
  let out = '';
  // 用 randomInt 而非随机取模，避免分布偏斜；禁止 rand/mt_rand 类弱随机
  for (let i = 0; i < length; i += 1) {
    out += charset[crypto.randomInt(0, charset.length)];
  }
  return out;
}

/** 卡密展示格式：XXXX-XXXX-XXXX-XXXX */
export const formatCardCode = (code) => String(code || '').replace(/(.{4})(?=.)/g, '$1-');

/** 业务订单号 / 任务号：前缀 + 时间戳 + 随机，长度受 varchar(32) 约束。 */
export function businessNo(prefix = 'DW', date = new Date()) {
  const stamp =
    `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}` +
    `${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
  const rand = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  return `${prefix}${stamp}${rand}`; // 2 + 14 + 6 = 22 位
}

export function batchNo(seq, date = new Date()) {
  return `CB${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${String(seq).padStart(3, '0')}`;
}

/** 稳定哈希（用于灰度发布：同一 device_id 永远命中同一档，规划书 6.5） */
export function stableHash(input, modulo = 100) {
  const h = crypto.createHash('sha256').update(String(input)).digest();
  return h.readUInt32BE(0) % modulo;
}

/* ----------------------------- 参数 ----------------------------- */

export const toInt = (value, fallback = 0) => {
  const n = Number.parseInt(String(value ?? ''), 10);
  return Number.isFinite(n) ? n : fallback;
};

export const toBool = (value, fallback = false) => {
  if (value === undefined || value === null || value === '') return fallback;
  if (typeof value === 'boolean') return value;
  const s = String(value).toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(s)) return true;
  if (['0', 'false', 'no', 'off'].includes(s)) return false;
  return fallback;
};

/** 白名单取参：不在集合内就返回默认值，杜绝拼接注入面。 */
export function pickOne(value, allowed, fallback) {
  return allowed.includes(value) ? value : fallback;
}

/** 分页参数：limit 上限 200，防止后台一键导出打爆内存。 */
export function pageParams(query = {}, defaultLimit = 20, maxLimit = 200) {
  const page = Math.max(1, toInt(query.page, 1));
  const limit = Math.min(maxLimit, Math.max(1, toInt(query.limit ?? query.page_size, defaultLimit)));
  return { page, limit, offset: (page - 1) * limit };
}

/** 手机号脱敏：138****8888（规划书 10.1） */
export function maskPhone(phone) {
  const s = String(phone || '');
  if (s.length < 7) return s;
  return `${s.slice(0, 3)}****${s.slice(-4)}`;
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 简易并发闸门：限制同时在跑的 Promise 数（worker 用它控 CPU 占用）。 */
export async function mapLimit(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await worker(items[index], index);
    }
  });
  await Promise.all(runners);
  return results;
}

/** 带超时的 fetch：FFmpeg/上游解析都必须有硬超时，否则 worker 会被拖死。 */
export async function fetchWithTimeout(url, options = {}, timeoutMs = 15000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/** 用 ffprobe 之外的方式拿文件大小，避免额外进程开销。 */
export function humanSize(bytes) {
  const n = Number(bytes) || 0;
  if (n < 1024) return `${n}B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(2)}MB`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)}GB`;
}
