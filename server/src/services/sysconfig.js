/**
 * 全局配置（sys_config）：后台可改、客户端可拉、进程内带缓存。
 *
 * 规划书 6.5 强调「配置热更新比 App 发版更重要」——解析线路、水印策略、公告、
 * 客服入口都从这里下发，改完立即生效，不需要动客户端。
 */
import { getDb } from '../db/index.js';
import { logger } from '../core/logger.js';
import { now } from '../core/util.js';

const CACHE_TTL_MS = 15_000;
let cache = null;
let cachedAt = 0;

const log = logger.child('sysconfig');

export async function loadAll(force = false) {
  if (!force && cache && Date.now() - cachedAt < CACHE_TTL_MS) return cache;
  const rows = await getDb().query('SELECT `key`, `value`, `type`, `group_name`, `is_public`, `title`, `remark` FROM `sys_config`');
  const map = new Map();
  for (const row of rows) map.set(row.key, row);
  cache = map;
  cachedAt = Date.now();
  return cache;
}

export function invalidate() {
  cache = null;
  cachedAt = 0;
}

function coerce(row, fallback) {
  if (!row) return fallback;
  const raw = row.value;
  switch (row.type) {
    case 'int':
      return Number.parseInt(raw, 10) || 0;
    case 'bool':
      return ['1', 'true', 'yes', 'on'].includes(String(raw).toLowerCase());
    case 'json':
      try {
        return raw ? JSON.parse(raw) : fallback;
      } catch {
        log.warn('sys_config JSON 解析失败', { key: row.key });
        return fallback;
      }
    default:
      return raw ?? fallback;
  }
}

export async function getConfig(key, fallback = null) {
  const map = await loadAll();
  return coerce(map.get(key), fallback);
}

export const getInt = async (key, fallback = 0) => Number(await getConfig(key, fallback)) || 0;
export const getBool = async (key, fallback = false) => Boolean(await getConfig(key, fallback));
export const getJson = async (key, fallback = null) => {
  const value = await getConfig(key, fallback);
  return value === null || value === undefined ? fallback : value;
};

export async function setConfig(key, value, { title, group = 'base', type = 'string', isPublic = 0, remark } = {}) {
  const db = getDb();
  const existing = await db.get('SELECT `id` FROM `sys_config` WHERE `key` = ?', [key]);
  const serialized =
    type === 'json' && value !== null && typeof value === 'object' ? JSON.stringify(value) : String(value ?? '');
  if (existing) {
    await db.update('sys_config', { id: existing.id }, { value: serialized, updated_at: now() });
  } else {
    await db.insert('sys_config', {
      key,
      value: serialized,
      group_name: group,
      title: title || key,
      type,
      is_public: isPublic,
      remark: remark || null,
      created_at: now(),
      updated_at: now(),
    });
  }
  invalidate();
  return serialized;
}

/** 批量写（后台「面板管理 → 全局配置」一次提交多个字段） */
export async function setMany(entries = []) {
  for (const entry of entries) {
    await setConfig(entry.key, entry.value, entry);
  }
}

export async function listConfigs({ group, publicOnly = false } = {}) {
  const map = await loadAll(true);
  return [...map.values()]
    .filter((row) => (group ? row.group_name === group : true))
    .filter((row) => (publicOnly ? Number(row.is_public) === 1 : true))
    .map((row) => ({
      key: row.key,
      value: row.type === 'json' ? safeParse(row.value) : row.value,
      raw: row.value,
      group: row.group_name,
      title: row.title,
      type: row.type,
      is_public: Number(row.is_public),
      remark: row.remark,
    }));
}

function safeParse(text) {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return text;
  }
}

/**
 * 水印策略（服务端决定，客户端只能表达「要不要」，不能指定内容与位置）。
 *
 * 会员模型：**成品是否带水印由「有没有开通会员」决定**，不是无差别强制。
 *   - 未开通/已过期：成品带水印（客户端界面也会叠加遮罩）
 *   - 已开通会员：成品干净不带水印（watermark_free_for_member=0 可退回「人人带水印」）
 */
export async function watermarkPolicy(level = 1) {
  const [enabled, freeForMember, template, position, opacity, widthRatio, showPlatform, rotate, logo] = await Promise.all([
    getBool('watermark_enabled', true),
    getBool('watermark_free_for_member', true),
    getConfig('watermark_text_template', '{nickname}'),
    getConfig('watermark_position', 'bottom-right'),
    getConfig('watermark_opacity', 0.42),
    getConfig('watermark_width_ratio', 0.2),
    getBool('watermark_show_platform', true),
    getBool('watermark_rotate_corners', false),
    getConfig('watermark_logo', ''),
  ]);
  const levelOverride = await getJson(`watermark_level_${level}`, null);
  return {
    enabled,
    freeForMember,
    template,
    position,
    opacity: Number(opacity) || 0.42,
    widthRatio: Number(widthRatio) || 0.2,
    showPlatform,
    rotateCorners: rotate,
    logo: logo || null,
    ...(levelOverride && typeof levelOverride === 'object' ? levelOverride : {}),
  };
}

/**
 * 下发给客户端的公开配置（/api/v1/config）。
 * 必须按 type 还原类型（bool → true/false、int → number、json → 对象），
 * 否则客户端拿到的是 "1" / "20" 这类字符串，很容易在真值判断上踩坑。
 */
export async function publicConfig() {
  const map = await loadAll();
  const out = {};
  for (const row of map.values()) {
    if (Number(row.is_public) !== 1) continue;
    out[row.key] = coerce(row, null);
  }
  return out;
}

export default {
  getConfig,
  getInt,
  getBool,
  getJson,
  setConfig,
  setMany,
  listConfigs,
  watermarkPolicy,
  publicConfig,
  invalidate,
  loadAll,
};
