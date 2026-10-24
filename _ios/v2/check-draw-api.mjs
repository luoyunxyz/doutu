/**
 * 绘制 API 对账 v2 —— 名字 + **参数标签 + 个数**（2026-10-17）
 *
 *   node _ios/check-draw-api.mjs
 *
 * v1 只检查「方法名存在」。但 Swift 的编译错误里，更大一类是**参数标签写错或个数不对**：
 *
 *     d.round(box, radius: 12, color)          // 少了一个标签 / 顺序不对
 *     d.text("x", x: 1, baseline: 2, 3, ink)   // 位置参数混进标签参数
 *
 * 这类错误 v1 完全看不见。v2 解析 `Draw.swift` 里每个 `func` 的**标签序列**，
 * 再解析调用点的**实参标签序列**，逐个对账。
 *
 * 只对 `d.` / `draw.` 开头的调用做检查（那是 Draw 实例）。
 */
import fs from 'node:fs';
import path from 'node:path';

const DRAW = 'D:\\AAA\\doutu\\ios_v2\\Sources\\Design\\Draw.swift';
const SCREENS = 'D:\\AAA\\doutu\\ios_v2\\Sources\\Screens';

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

/** 去掉注释/字符串，保留结构 */
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

/** 从 `(` 开始取出配平的参数串（不含外层括号） */
function takeParens(text, openIdx) {
  let depth = 0;
  for (let i = openIdx; i < text.length; i += 1) {
    if (text[i] === '(') depth += 1;
    else if (text[i] === ')') {
      depth -= 1;
      if (depth === 0) return { body: text.slice(openIdx + 1, i), end: i };
    }
  }
  return null;
}

/** 把参数串按顶层逗号切开 */
function splitArgs(body) {
  const out = [];
  let depth = 0;
  let cur = '';
  for (const ch of body) {
    if (ch === '(' || ch === '[' || ch === '<' || ch === '{') depth += 1;
    if (ch === ')' || ch === ']' || ch === '>' || ch === '}') depth -= 1;
    if (ch === ',' && depth === 0) { out.push(cur.trim()); cur = ''; continue; }
    cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

/** 一个实参的标签：`radius: 12` → 'radius'；位置参数 → '_' */
function labelOf(arg) {
  const m = arg.match(/^([a-zA-Z_]\w*)\s*:(?!:)/);
  return m ? m[1] : '_';
}

// ① 解析 Draw.swift 的每个 func 的标签序列
const drawCode = stripNoise(fs.readFileSync(DRAW, 'utf8'));
const signatures = new Map();   // name -> [{labels: [...], required: n}]
for (const m of drawCode.matchAll(/\bfunc\s+(\w+)\s*\(/g)) {
  const name = m[1];
  const open = drawCode.indexOf('(', m.index + 4);
  const taken = takeParens(drawCode, open);
  if (!taken) continue;
  const params = splitArgs(taken.body);
  // Swift 允许「标签不同、名字相同」的重载，所以一个名字可能有多组签名
  const labels = params.map((p) => {
    // Swift 形参可能是「`from top: UIColor`」这种：**外部标签是第一个词**，后面是内部名
    const head = p.split(':')[0].trim();
    const first = head.split(/\s+/)[0];
    return first === '_' ? '_' : first;
  });
  const defaults = params.map((p) => p.includes('='));
  const required = defaults.filter((d) => !d).length;
  if (!signatures.has(name)) signatures.set(name, []);
  signatures.get(name).push({ labels, required, total: params.length, defaults });
}

ok(`解析出 ${signatures.size} 个 Draw 方法签名`, signatures.size > 15);

/**
 * 实参与签名是否兼容。
 *
 * Swift 允许两种「看起来不一样但其实合法」的写法，必须放过：
 *   ① **跳过有默认值的参数**：`textSqueezed(..., squeeze: 0.94)` 省略中间的 `bold:`
 *   ② **尾随闭包**：`clipped(to: box, radius: 40) { ... }` —— 闭包是最后一个位置参数
 */
function matches(sig, labels) {
  let i = 0;
  for (const lab of labels) {
    let found = false;
    while (i < sig.labels.length) {
      const sl = sig.labels[i];
      const compatible = lab === '_' || sl === '_' || lab === sl;
      if (compatible) { i += 1; found = true; break; }
      if (!sig.defaults[i]) return false;   // 必填参数不能被跳过
      i += 1;
    }
    if (!found) return false;
  }
  while (i < sig.labels.length) {
    if (!sig.defaults[i]) return false;     // 剩下的必填参数没给
    i += 1;
  }
  return true;
}

// ② 扫页面里的调用，逐个对账
const problems = [];
let callCount = 0;
for (const file of walk(SCREENS)) {
  const code = stripNoise(fs.readFileSync(file, 'utf8'));
  for (const m of code.matchAll(/\b(?:d|draw)\.(\w+)\s*\(/g)) {
    const name = m[1];
    const sigs = signatures.get(name);
    if (!sigs) {
      problems.push(`方法不存在：${name}  (${path.basename(file)})`);
      continue;
    }
    const open = code.indexOf('(', m.index + name.length);
    const taken = takeParens(code, open);
    if (!taken) continue;
    const args = splitArgs(taken.body);
    const labels = args.map(labelOf);
    // 尾随闭包也是一个（位置）实参
    if (/^\s*\{/.test(code.slice(taken.end + 1))) labels.push('_');
    callCount += 1;

    // 找一组能对上的签名（允许跳过默认参数、允许尾随闭包）
    const matched = sigs.some((sig) => matches(sig, labels));
    if (!matched) {
      const expect = sigs.map((s) => `(${s.labels.map((l) => (l === '_' ? '_' : l + ':')).join(', ')})`).join(' 或 ');
      problems.push(`${name}(${labels.join(', ')})  ← ${path.basename(file)}   期望 ${expect}`);
    }
  }
}

const unique = [...new Set(problems)];
ok(`页面里的 ${callCount} 处绘制调用，参数标签与个数都对得上`, unique.length === 0,
   `${unique.length} 处不匹配`);
if (unique.length) {
  console.log('');
  console.log('  不匹配的调用：');
  for (const p of unique.slice(0, 20)) console.log('    ' + p);
  if (unique.length > 20) console.log(`    …还有 ${unique.length - 20} 处`);
}

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
