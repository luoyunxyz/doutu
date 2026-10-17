/**
 * 日志：控制台 + 按天切分的文件。
 *
 * 脱敏约定（规划书 10.1）：日志里不得出现完整卡密、密码、token、sign_key。
 * 统一走 mask() / maskCard()。
 */
import fs from 'node:fs';
import path from 'node:path';
import config from '../config.js';

const LEVELS = { debug: 10, info: 20, warn: 30, error: 40, silent: 99 };
const threshold = LEVELS[config.log.level] ?? LEVELS.info;

let stream = null;
let streamDay = '';

function ensureStream() {
  const day = new Date().toISOString().slice(0, 10);
  if (stream && streamDay === day) return stream;
  try {
    fs.mkdirSync(config.log.dir, { recursive: true });
    if (stream) stream.end();
    stream = fs.createWriteStream(path.join(config.log.dir, `server-${day}.log`), { flags: 'a' });
    streamDay = day;
  } catch {
    stream = null;
  }
  return stream;
}

function write(level, scope, message, extra) {
  if ((LEVELS[level] ?? 20) < threshold) return;
  const line = {
    t: new Date().toISOString(),
    level,
    scope,
    msg: message,
  };
  if (extra !== undefined) line.extra = extra;
  const text = JSON.stringify(line, replacer);
  if (level === 'error') console.error(text);
  else if (level === 'warn') console.warn(text);
  else console.log(text);
  const s = ensureStream();
  if (s) s.write(text + '\n');
}

/** 只保留 extra 中能安全序列化的部分，避免循环引用把日志打崩。 */
function replacer(_key, value) {
  if (typeof value === 'bigint') return Number(value);
  if (value instanceof Error) return { name: value.name, message: value.message, stack: value.stack };
  return value;
}

export function mask(value, keep = 4) {
  if (value === null || value === undefined) return value;
  const s = String(value);
  if (s.length <= keep) return '***';
  return `${s.slice(0, keep)}***${s.slice(-2)}`;
}

export function maskCard(code) {
  if (!code) return code;
  const s = String(code);
  return s.length >= 4 ? `****${s.slice(-4)}` : '****';
}

export function createLogger(scope) {
  return {
    debug: (msg, extra) => write('debug', scope, msg, extra),
    info: (msg, extra) => write('info', scope, msg, extra),
    warn: (msg, extra) => write('warn', scope, msg, extra),
    error: (msg, extra) => write('error', scope, msg, extra),
    child: (sub) => createLogger(`${scope}:${sub}`),
  };
}

export const logger = createLogger('app');
export default logger;
