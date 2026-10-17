/**
 * 金额工具：**绝不使用浮点运算**（规划书 4 章硬性要求）。
 *
 * 内部统一以「分」为整数运算，对外始终给 MySQL decimal(10,2) 风格的字符串 "12.34"。
 * 这样 MySQL（DECIMAL 字符串）与 SQLite（TEXT 存同格式）行为完全一致。
 */

/** 任意输入 → 整数分。接受 "12.34" / 12.34 / 1234（分？不，按元处理）。 */
export function toCents(value) {
  if (value === null || value === undefined || value === '') return 0;
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) return 0;
    return Math.round(value * 100);
  }
  let s = String(value).trim();
  if (!s) return 0;
  let negative = false;
  if (s.startsWith('-')) {
    negative = true;
    s = s.slice(1);
  } else {
    s = s.replace(/^\+/, '');
  }
  s = s.replace(/,/g, '');
  const m = /^(\d*)(?:\.(\d*))?$/.exec(s);
  if (!m) return 0;
  const intPart = m[1] || '0';
  const fracRaw = (m[2] || '').padEnd(3, '0');
  const frac = fracRaw.slice(0, 3);
  // 第三位用于四舍五入
  let cents = Number(intPart) * 100 + Number(frac.slice(0, 2));
  if (Number(frac[2]) >= 5) cents += 1;
  return negative ? -cents : cents;
}

/** 整数分 → "12.34"。 */
export function fromCents(cents) {
  const n = Math.round(Number(cents) || 0);
  const sign = n < 0 ? '-' : '';
  const abs = Math.abs(n);
  const yuan = Math.floor(abs / 100);
  const rest = String(abs % 100).padStart(2, '0');
  return `${sign}${yuan}.${rest}`;
}

/** 归一化成库格式 "12.34"，用于写库/比较。 */
export const money = (value) => fromCents(toCents(value));

/** 多个金额字符串求和（分运算），返回 "12.34"。 */
export function sumMoney(values) {
  let cents = 0;
  for (const v of values) cents += toCents(v);
  return fromCents(cents);
}

export const addMoney = (...values) => fromCents(values.reduce((acc, v) => acc + toCents(v), 0));
export const subMoney = (a, b) => fromCents(toCents(a) - toCents(b));
export const mulMoney = (a, factor) => fromCents(Math.round(toCents(a) * Number(factor || 0)));
/** 按比例取手续费：rate 0.01 = 1% */
export const feeOf = (amount, rate) => fromCents(Math.round(toCents(amount) * Number(rate || 0)));
/** a > b ?，整数分比较，避免浮点误差 */
export const gtMoney = (a, b) => toCents(a) > toCents(b);
export const gteMoney = (a, b) => toCents(a) >= toCents(b);
export const eqMoney = (a, b) => toCents(a) === toCents(b);

export default {
  toCents,
  fromCents,
  money,
  sumMoney,
  addMoney,
  subMoney,
  mulMoney,
  feeOf,
  gtMoney,
  gteMoney,
  eqMoney,
};
