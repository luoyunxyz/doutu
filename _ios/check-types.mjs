/**
 * 类型混用扫描（2026-10-17）
 *
 *   node _ios/check-types.mjs
 *
 * 为什么需要它：Swift 不做隐式数值转换 —— `Double` 不能当 `CGFloat` 用、`Int` 也不能。
 * 而页面里最常出这种错的地方，就是把 `Store` / `Model` 算出来的数值直接塞进绘图参数：
 *
 *     d.text(store.value("total"), x: 50, baseline: 508, size: 74, gold)   // 第一个参数是 String ✓
 *     d.rect(CGRect(x: 0, y: quota, width: 10, height: 10), color)          // quota 是 Double ✗
 *
 * 这个脚本找出「Store/Model 调用出现在几何参数位置、且外面没有 CGFloat()/String() 包裹」的地方。
 *
 * 只做保守判断：宁可漏报，也不误报 —— 误报会让我去改本来正确的代码。
 */
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:\\AAA\\doutu\\ios\\Sources';

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  [OK]   ${name}`); }
  else { fail += 1; console.log(`  [FAIL] ${name} ${extra}`); }
};

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.swift')) out.push(full);
  }
  return out;
}

/** 去掉注释与字符串，保留结构（字符串内容会被清空但仍占位） */
function stripNoise(text) {
  let out = '';
  let i = 0;
  let mode = 'code';
  while (i < text.length) {
    const c = text[i];
    const n = text[i + 1];
    if (mode === 'code') {
      if (c === '/' && n === '/') { mode = 'line'; i += 2; continue; }
      if (c === '/' && n === '*') { mode = 'block'; i += 2; continue; }
      if (c === '"') { mode = 'string'; i += 1; continue; }
      out += c; i += 1; continue;
    }
    if (mode === 'line') { if (c === '\n') { mode = 'code'; out += c; } i += 1; continue; }
    if (mode === 'block') { if (c === '*' && n === '/') { mode = 'code'; i += 2; } else i += 1; continue; }
    if (mode === 'string') {
      if (c === '\\') { i += 2; continue; }
      if (c === '"') { mode = 'code'; i += 1; continue; }
      i += 1; continue;
    }
  }
  return out;
}

/** 取配平的参数串 */
function takeParens(text, openIdx) {
  let depth = 0;
  for (let i = openIdx; i < text.length; i += 1) {
    if (text[i] === '(') depth += 1;
    else if (text[i] === ')') { depth -= 1; if (depth === 0) return text.slice(openIdx + 1, i); }
  }
  return '';
}

function splitArgs(body) {
  const out = [];
  let depth = 0;
  let cur = '';
  for (const ch of body) {
    if ('([{<'.includes(ch)) depth += 1;
    if (')]}>'.includes(ch)) depth -= 1;
    if (ch === ',' && depth === 0) { out.push(cur.trim()); cur = ''; continue; }
    cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

/**
 * 这些调用返回 `Double`（金额/数量/统计值）。
 * 它们**不能**直接出现在 CGRect / 绘图参数里，必须先 `CGFloat(...)`。
 */
const DOUBLE_SOURCES = [
  /(?:BillAnalyticsModel|model)\.value\(/,
  /(?:BillAnalyticsModel|model)\.total\(/,
  /\.quota\b/,
  /\.avail\b/,
  /(?:ReceiptStore|WithdrawStore|GrowthStore)\.shared\.(?:quota|avail)\b/,
];

/** 这些调用返回 `String`，只能进文字参数 */
const STRING_SOURCES = [
  /ReceiptStore\.(?:money|yuan)\(/,
  /(?:BillAnalyticsScreen|Self)\.money\(/,
  /(?:BillAnalyticsScreen|Self)\.trim2\(/,
];

/** 需要 CGFloat 的参数名 */
const NUMERIC_LABELS = ['x', 'y', 'cx', 'cy', 'baseline', 'size', 'right', 'width', 'height',
                        'radius', 'top', 'left', 'from', 'to', 'minX', 'midX', 'maxX', 'midY',
                        'maxY', 'minY', 'squeeze', 'offsetY', 'r'];

/** 返回 `String` 的包装函数：出现在参数里就说明这个参数是文字，不是几何量 */
const STRING_WRAPPER = /\b(?:money|trim2|integer|pct|moneyText)\s*\(|String\s*\(|\\\(/;

const problems = [];
let calls = 0;

for (const file of walk(DIR)) {
  const raw = fs.readFileSync(file, 'utf8');
  const code = stripNoise(raw);
  const base = path.basename(file);
  if (!base.includes('Screen')) continue;   // 只扫页面与模型

  // 找所有 d.xxx( 与 CGRect( 调用
  for (const m of code.matchAll(/\b(?:d\.|draw\.)?(CGRect|CGFloat)?\(?/g)) {
    // 用更直接的办法：逐个匹配具体调用
  }

  // 逐个匹配 `d.method(...)` 与 `CGRect(...)`
  const callRe = /(?:(\bd\.\w+)|\b(CGRect))\s*\(/g;
  let match;
  while ((match = callRe.exec(code)) !== null) {
    const isRect = match[2] === 'CGRect';
    const name = isRect ? 'CGRect' : match[1];
    const open = code.indexOf('(', match.index + name.length);
    const body = takeParens(code, open);
    if (!body) continue;
    calls += 1;
    for (const arg of splitArgs(body)) {
      // 已包裹就跳过
      if (/CGFloat\s*\(|Double\s*\(|String\s*\(|Int\s*\(/.test(arg)) continue;
      // 参数里有返回 String 的包装函数 → 这是文字参数，不是几何量
      if (STRING_WRAPPER.test(arg)) continue;
      // 明显的字符串拼接（含引号）也跳过
      if (/"/.test(arg)) continue;

      for (const re of DOUBLE_SOURCES) {
        if (!re.test(arg)) continue;
        // CGRect 的 y/height 位置、绘图方法的数值参数位置都算可疑
        problems.push(`${base}: ${name}(…) 里有未转换的 Double → ${arg.slice(0, 60)}`);
      }
    }
  }
}

const unique = [...new Set(problems)];
ok(`扫描 ${calls} 处几何调用，未发现 Double 直接进 CGFloat 位置`, unique.length === 0,
   `${unique.length} 处可疑`);
if (unique.length) {
  console.log('');
  console.log('  可疑处（需要人工确认；有些可能已经是对的）：');
  for (const p of unique.slice(0, 25)) console.log('    ' + p);
  if (unique.length > 25) console.log(`    …还有 ${unique.length - 25} 处`);
}

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
