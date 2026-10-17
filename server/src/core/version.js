/**
 * 版本号解析与换算（2026-10-17）。
 *
 * 运营在后台填的是 **1.2.3** 这种人话版本号；而客户端判断「要不要升级」靠的是
 * `BuildConfig.VERSION_CODE` 这个**整数**（`线上 code > 本机 code` 才提示）。
 * 两边必须用同一套公式，否则会出现「后台发了版本、用户收不到」。
 *
 * 公式：`major * 10000 + minor * 100 + patch` ——
 *   1.0.0 → 10000
 *   1.0.1 → 10001
 *   1.2.3 → 10203
 *   2.0.0 → 20000
 * 次版本号与修订号各预留两位（0-99），够用且一眼能读懂（10203 就是 1.2.3）。
 *
 * 客户端 `app/build.gradle` 里用同一个公式从 versionName 算 versionCode，
 * 所以这里改了公式，那边必须一起改（`_ops/check-version-code.mjs` 会比对两边是否一致）。
 */

const SEGMENT_MAX = 99;
const MAJOR_MAX = 210000; // int32 / 10000，留足余量

/**
 * 「1.2.3」/「v1.2」/「1」 → { code, major, minor, patch }；不是合法版本号时返回 null。
 */
export function parseVersionName(input) {
  const text = String(input === null || input === undefined ? '' : input).trim().replace(/^v/i, '');
  if (!text) return null;
  const parts = text.split('.');
  if (parts.length > 3) return null;
  const nums = [];
  for (const part of parts) {
    if (!/^\d+$/.test(part)) return null;
    nums.push(Number(part));
  }
  while (nums.length < 3) nums.push(0);
  const [major, minor, patch] = nums;
  if (major > MAJOR_MAX) return null;
  if (minor > SEGMENT_MAX || patch > SEGMENT_MAX) return null;
  return { code: major * 10000 + minor * 100 + patch, major, minor, patch };
}

/** 只取整数 code；不合法返回 null。 */
export function versionCodeOf(name) {
  const parsed = parseVersionName(name);
  return parsed ? parsed.code : null;
}

/** 整数 code → 「1.2.3」（后台列表里给人看）。 */
export function formatVersionCode(code) {
  const n = Number(code);
  if (!Number.isFinite(n) || n < 0) return '';
  return [Math.floor(n / 10000), Math.floor(n / 100) % 100, n % 100].join('.');
}

/**
 * 给出「下一个版本号」，用在冲突提示里（1.0.2 → 1.0.3，1.0.99 → 1.1.0，1.99.99 → 2.0.0）。
 * 比干巴巴一句「请换一个更大的数字」有用得多 —— 运营不用自己想下一个该填什么。
 */
export function bumpVersionName(name) {
  const parsed = parseVersionName(name);
  if (!parsed) return '一个更大的版本号';
  if (parsed.patch < SEGMENT_MAX) return `${parsed.major}.${parsed.minor}.${parsed.patch + 1}`;
  if (parsed.minor < SEGMENT_MAX) return `${parsed.major}.${parsed.minor + 1}.0`;
  return `${parsed.major + 1}.0.0`;
}

/** 版本号是否合法（用于后台校验时给出人话原因）。 */
export function validateVersionName(name) {
  const text = String(name || '').trim();
  if (!text) return '版本号不能为空，请填成 1.2.3 这样的格式';
  const cleaned = text.replace(/^v/i, '');
  const parts = cleaned.split('.');
  // 先判「格式」，再判「范围」—— 否则 1.100.0 这种会被笼统说成「格式不对」，
  // 运营改半天也改不对（其实是次版本号超了两位）
  if (parts.length > 3 || parts.some((p) => !/^\d+$/.test(p))) {
    return `版本号「${text}」不合法：请填成 1.2.3 这样的格式（最多三段数字，可以写 1 或 1.2）`;
  }
  const nums = parts.map(Number);
  while (nums.length < 3) nums.push(0);
  if (nums[0] > MAJOR_MAX) return `版本号「${text}」的主版本号太大（不能超过 ${MAJOR_MAX}）`;
  if (nums[1] > SEGMENT_MAX || nums[2] > SEGMENT_MAX) {
    return `版本号「${text}」里的次版本号和修订号不能超过 ${SEGMENT_MAX}（比如 1.100.0 应该写成 1.99.0 或升主版本号 2.0.0）`;
  }
  return null;
}

export default { parseVersionName, versionCodeOf, formatVersionCode, validateVersionName, bumpVersionName };
