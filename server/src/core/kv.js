/**
 * KV 存储：Redis 7（配置了 REDIS_URL 时）或进程内内存实现（默认）。
 *
 * 用途：nonce 防重放、每日配额计数、三级限流、激活失败锁定、线路成功率滑动窗口。
 * 语义完全一致，业务代码只依赖本模块，切换 Redis 不需要改任何调用点。
 *
 * 说明：首期单机 500 用户 / 50 并发，内存实现完全够用；一旦上第二台机器，
 * 必须配 REDIS_URL，否则多进程之间看不到彼此的 nonce 与配额。
 */
import net from 'node:net';
import config from '../config.js';
import { logger } from './logger.js';

/* ------------------------------------------------------------------ */
/*                            内存实现                                 */
/* ------------------------------------------------------------------ */

class MemoryKv {
  constructor() {
    this.map = new Map(); // key -> { value, expireAt(ms|null) }
    this.timer = setInterval(() => this.sweep(), 60_000);
    this.timer.unref?.();
    this.driver = 'memory';
  }

  sweep() {
    const now = Date.now();
    for (const [key, entry] of this.map) {
      if (entry.expireAt && entry.expireAt <= now) this.map.delete(key);
    }
  }

  #live(key) {
    const entry = this.map.get(key);
    if (!entry) return null;
    if (entry.expireAt && entry.expireAt <= Date.now()) {
      this.map.delete(key);
      return null;
    }
    return entry;
  }

  async get(key) {
    const entry = this.#live(key);
    return entry ? entry.value : null;
  }

  async set(key, value, ttlSec) {
    this.map.set(key, { value: String(value), expireAt: ttlSec ? Date.now() + ttlSec * 1000 : null });
    return 'OK';
  }

  /** SET NX：不存在才写，返回是否写入成功 */
  async setnx(key, value, ttlSec) {
    if (this.#live(key)) return false;
    await this.set(key, value, ttlSec);
    return true;
  }

  async del(key) {
    return this.map.delete(key) ? 1 : 0;
  }

  async incr(key, ttlSec) {
    const entry = this.#live(key);
    const next = (entry ? Number(entry.value) || 0 : 0) + 1;
    this.map.set(key, {
      value: String(next),
      expireAt: entry?.expireAt || (ttlSec ? Date.now() + ttlSec * 1000 : null),
    });
    return next;
  }

  async expire(key, ttlSec) {
    const entry = this.#live(key);
    if (!entry) return 0;
    entry.expireAt = Date.now() + ttlSec * 1000;
    return 1;
  }

  async ttl(key) {
    const entry = this.#live(key);
    if (!entry) return -2;
    if (!entry.expireAt) return -1;
    return Math.ceil((entry.expireAt - Date.now()) / 1000);
  }

  async lpush(key, value) {
    const entry = this.#live(key);
    const list = entry ? JSON.parse(entry.value) : [];
    list.unshift(String(value));
    await this.set(key, JSON.stringify(list), entry?.expireAt ? (entry.expireAt - Date.now()) / 1000 : undefined);
    return list.length;
  }

  async rpop(key) {
    const entry = this.#live(key);
    if (!entry) return null;
    const list = JSON.parse(entry.value);
    const value = list.pop() ?? null;
    await this.set(key, JSON.stringify(list), entry.expireAt ? (entry.expireAt - Date.now()) / 1000 : undefined);
    return value;
  }

  async llen(key) {
    const entry = this.#live(key);
    return entry ? JSON.parse(entry.value).length : 0;
  }

  async ping() {
    return 'PONG';
  }

  async close() {
    clearInterval(this.timer);
    this.map.clear();
  }
}

/* ------------------------------------------------------------------ */
/*                        极简 Redis (RESP) 客户端                     */
/* ------------------------------------------------------------------ */

class RedisKv {
  constructor(url) {
    this.driver = 'redis';
    this.url = new URL(url);
    this.socket = null;
    this.buffer = '';
    this.queue = [];
    this.connecting = null;
    this.closed = false;
  }

  async connect() {
    if (this.socket && !this.socket.destroyed) return;
    if (this.connecting) return this.connecting;
    this.connecting = new Promise((resolve, reject) => {
      const socket = net.createConnection({
        host: this.url.hostname,
        port: Number(this.url.port || 6379),
      });
      socket.setNoDelay(true);
      socket.once('connect', async () => {
        this.socket = socket;
        try {
          const password = decodeURIComponent(this.url.password || '');
          if (password) await this.send(['AUTH', password]);
          const db = (this.url.pathname || '/0').replace('/', '');
          if (db && db !== '0') await this.send(['SELECT', db]);
          this.connecting = null;
          resolve();
        } catch (err) {
          this.connecting = null;
          reject(err);
        }
      });
      socket.on('data', (chunk) => this.onData(chunk));
      socket.on('error', (err) => this.onError(err));
      socket.on('close', () => this.onError(new Error('redis 连接已关闭')));
      socket.once('error', (err) => {
        if (this.connecting) {
          this.connecting = null;
          reject(err);
        }
      });
    });
    return this.connecting;
  }

  onError(err) {
    const pending = this.queue.splice(0, this.queue.length);
    for (const item of pending) item.reject(err);
    this.socket = null;
    this.buffer = '';
  }

  onData(chunk) {
    this.buffer += chunk.toString('utf8');
    // 循环解析完整的 RESP 回复
    for (;;) {
      const parsed = parseResp(this.buffer);
      if (!parsed) break;
      this.buffer = this.buffer.slice(parsed.consumed);
      const item = this.queue.shift();
      if (!item) break;
      if (parsed.error) item.reject(parsed.error);
      else item.resolve(parsed.value);
    }
  }

  send(args) {
    const flat = args.map((a) => String(a));
    const payload = `*${flat.length}\r\n${flat.map((a) => `$${Buffer.byteLength(a)}\r\n${a}\r\n`).join('')}`;
    return new Promise((resolve, reject) => {
      const socket = this.socket;
      if (!socket || socket.destroyed) {
        this.connect()
          .then(() => {
            this.queue.push({ resolve, reject });
            this.socket.write(payload);
          })
          .catch(reject);
        return;
      }
      this.queue.push({ resolve, reject });
      socket.write(payload);
    });
  }

  async get(key) {
    const v = await this.send(['GET', key]);
    return v === null ? null : String(v);
  }

  async set(key, value, ttlSec) {
    const args = ['SET', key, String(value)];
    if (ttlSec) args.push('EX', Math.max(1, Math.round(ttlSec)));
    return this.send(args);
  }

  async setnx(key, value, ttlSec) {
    const args = ['SET', key, String(value), 'NX'];
    if (ttlSec) args.push('EX', Math.max(1, Math.round(ttlSec)));
    const res = await this.send(args);
    return res === 'OK';
  }

  async del(key) {
    return Number(await this.send(['DEL', key]));
  }

  async incr(key, ttlSec) {
    const n = Number(await this.send(['INCR', key]));
    if (n === 1 && ttlSec) await this.send(['EXPIRE', key, Math.max(1, Math.round(ttlSec))]);
    return n;
  }

  async expire(key, ttlSec) {
    return Number(await this.send(['EXPIRE', key, Math.max(1, Math.round(ttlSec))]));
  }

  async ttl(key) {
    return Number(await this.send(['TTL', key]));
  }

  async lpush(key, value) {
    return Number(await this.send(['LPUSH', key, String(value)]));
  }

  async rpop(key) {
    return this.send(['RPOP', key]);
  }

  async llen(key) {
    return Number(await this.send(['LLEN', key]));
  }

  async ping() {
    return this.send(['PING']);
  }

  async close() {
    this.closed = true;
    if (this.socket) this.socket.destroy();
  }
}

/** 解析一条 RESP 回复；不完整时返回 null（等更多数据）。 */
function parseResp(buffer) {
  if (!buffer.length) return null;
  const type = buffer[0];
  const lineEnd = buffer.indexOf('\r\n');
  if (lineEnd === -1) return null;
  const line = buffer.slice(1, lineEnd);

  // ⚠️ 状态行与 bulk string 必须转成字符串再返回：
  //    buffer.slice() 给的是 Buffer，而调用方会写 `res === 'OK'`（setnx）这种严格比较，
  //    Buffer !== string 会让 SET NX **永远判定为「已存在」**，
  //    表现为每个请求都被拒成 1003 重复请求。内存实现返回的是字符串，所以只在真 Redis 上暴露。
  if (type === '+') return { value: line.toString('utf8'), consumed: lineEnd + 2 };
  if (type === '-') return { error: new Error(line.toString('utf8')), consumed: lineEnd + 2 };
  if (type === ':') return { value: Number(line.toString('utf8')), consumed: lineEnd + 2 };

  if (type === '$') {
    const len = Number(line.toString('utf8'));
    if (len === -1) return { value: null, consumed: lineEnd + 2 };
    const start = lineEnd + 2;
    if (buffer.length < start + len + 2) return null;
    return { value: buffer.slice(start, start + len).toString('utf8'), consumed: start + len + 2 };
  }

  if (type === '*') {
    const count = Number(line.toString('utf8'));
    if (count === -1) return { value: null, consumed: lineEnd + 2 };
    let offset = lineEnd + 2;
    const values = [];
    for (let i = 0; i < count; i += 1) {
      const sub = parseResp(buffer.slice(offset));
      if (!sub) return null;
      if (sub.error) return { error: sub.error, consumed: offset + sub.consumed };
      values.push(sub.value);
      offset += sub.consumed;
    }
    return { value: values, consumed: offset };
  }

  // 不认识的类型：直接丢弃整行，避免卡死
  return { value: line.toString('utf8'), consumed: lineEnd + 2 };
}

/* ------------------------------------------------------------------ */
/*                             工厂                                   */
/* ------------------------------------------------------------------ */

let instance = null;

export async function openKv() {
  if (instance) return instance;
  if (config.redis.url) {
    const redis = new RedisKv(config.redis.url);
    try {
      await redis.connect();
      await redis.ping();
      instance = redis;
      logger.info('Redis 已连接', { url: config.redis.url.replace(/:[^:@/]*@/, ':***@') });
    } catch (err) {
      logger.warn('Redis 连接失败，降级为进程内内存实现', { err: err.message });
      instance = new MemoryKv();
    }
  } else {
    instance = new MemoryKv();
    logger.info('未配置 REDIS_URL，使用进程内内存 KV');
  }
  return instance;
}

export function kv() {
  if (!instance) throw new Error('KV 尚未初始化，请先 await openKv()');
  return instance;
}

export async function closeKv() {
  if (instance) await instance.close();
  instance = null;
}

/** 常用键名集中在这里，避免各处手拼字符串拼错 */
export const keys = {
  nonce: (nonce) => `nonce:${nonce}`,
  quota: (userId, date) => `quota:${userId}:${date}`,
  rateIp: (ip, bucket) => `rl:ip:${ip}:${bucket}`,
  rateDevice: (deviceId, bucket) => `rl:dev:${deviceId}:${bucket}`,
  rateUser: (userId, bucket) => `rl:user:${userId}:${bucket}`,
  activateFail: (ip) => `act:fail:${ip}`,
  activateLock: (ip) => `act:lock:${ip}`,
  adminFail: (username) => `adm:fail:${username}`,
  queue: (priority) => `queue:dw:${priority}`,
  routeStat: (routeId, bucket) => `route:stat:${routeId}:${bucket}`,
  taskLock: (taskNo) => `task:lock:${taskNo}`,
  workerHeartbeat: (workerId) => `worker:hb:${workerId}`,
};

export default { openKv, kv, closeKv, keys, MemoryKv, RedisKv };
