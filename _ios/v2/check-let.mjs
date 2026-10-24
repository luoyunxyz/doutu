/**
 * 常量重赋值扫描（2026-10-17）
 *
 *   node _ios/check-let.mjs
 *
 * `let x = 1; x = 2` 在 Swift 里是**编译错误**（cannot assign to value: 'x' is a 'let' constant），
 * 而这类错很容易在从 Java 移植时出现 —— Java 的局部变量默认可变，`int x = 0; x += 5;` 随处可见，
 * 移植时若抄成 `let x = 0` 就会编不过。
 *
 * 做法：
 *   1. 把文件切成一个个函数体（按 `func` + 花括号配平）
 *   2. 在每个函数体内找 `let NAME = …` 形式的声明
 *   3. 同一个函数体里若再出现 `NAME =` / `NAME +=` / `NAME -=` / `NAME *=` / `NAME.append(`，
 *      就是可疑的重赋值
 *
 * 保守策略：只报「同一函数体内声明并重赋值」的，跨函数的同名变量不报（那是合法的遮蔽）。
 */
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:\\AAA\\doutu\\ios_v2\\Sources';

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
      if (c === '"') { mode = 'string'; out += '""'; i += 1; mode = 'string'; continue; }
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

/** 找出所有函数体：`func name(...) {…}` 里花括号内的内容 */
function functionBodies(code) {
  const bodies = [];
  const re = /\bfunc\s+(\w+)\s*(?:<[^>]*>)?\s*\(/g;
  let m;
  while ((m = re.exec(code)) !== null) {
    // 从参数表结束后的第一个 `{` 开始
    const parenOpen = code.indexOf('(', m.index);
    let depth = 0;
    let i = parenOpen;
    let parenClose = -1;
    for (; i < code.length; i += 1) {
      if (code[i] === '(') depth += 1;
      else if (code[i] === ')') { depth -= 1; if (depth === 0) { parenClose = i; break; } }
    }
    if (parenClose < 0) continue;
    const braceOpen = code.indexOf('{', parenClose);
    if (braceOpen < 0) continue;
    depth = 0;
    let j = braceOpen;
    let braceClose = -1;
    for (; j < code.length; j += 1) {
      if (code[j] === '{') depth += 1;
      else if (code[j] === '}') { depth -= 1; if (depth === 0) { braceClose = j; break; } }
    }
    if (braceClose < 0) continue;
    bodies.push({ name: m[1], body: code.slice(braceOpen + 1, braceClose) });
    re.lastIndex = braceClose;
  }
  return bodies;
}

const problems = [];
let scannedFuncs = 0;

for (const file of walk(DIR)) {
  const code = stripNoise(fs.readFileSync(file, 'utf8'));
  const base = path.basename(file);
  for (const fn of functionBodies(code)) {
    scannedFuncs += 1;
    // ① 找 `let NAME =` / `let NAME: Type =` 声明
    //
    // 排除 `if let` / `guard let` / `while let` / `, let` —— 那是**作用域绑定**，
    // 同一函数里出现几十个 `if let state = …` 完全正常，不能当成同一个常量。
    const declared = new Map();
    for (const m of fn.body.matchAll(/\blet\s+([a-zA-Z_]\w*)\s*(?::[^=\n]+)?=(?!=)/g)) {
      const prefix = fn.body.slice(Math.max(0, m.index - 10), m.index);
      if (/\b(?:if|guard|while)\s+$/.test(prefix)) continue;   // 条件绑定
      if (/(?:,|\()\s*$/.test(prefix)) continue;               // 元组/参数里的绑定
      declared.set(m[1], true);
    }
    if (declared.size === 0) continue;
    // ② 找重赋值
    for (const name of declared.keys()) {
      // 注意：不要匹配 `.append(` —— 那会误伤 `state.append(...)` 这种「调用对象自己的方法」
      // （芝麻信用页真的有自己的 `append(col,key:height:)`，Java 的 `state.column(col).add`）。
      const assign = new RegExp('(?<![.\\w])' + name + '\\s*(?:=(?!=)|\\+=|-=|\\*=|/=)', 'g');
      let count = 0;
      let sample = '';
      for (const m of fn.body.matchAll(assign)) {
        // 跳过声明本身：`let col = …` / `var col = …`
        // 同一函数里可以有多处声明（分属不同 if 块），那是合法的遮蔽，不算重赋值
        const prefix = fn.body.slice(Math.max(0, m.index - 8), m.index);
        if (/\b(?:let|var)\s+$/.test(prefix)) continue;
        count += 1;
        if (!sample) {
          const lineStart = fn.body.lastIndexOf('\n', m.index) + 1;
          const lineEnd = fn.body.indexOf('\n', m.index);
          sample = fn.body.slice(lineStart, lineEnd < 0 ? fn.body.length : lineEnd).trim();
        }
      }
      if (count > 0) {
        problems.push(`${base} :: ${fn.name}() 里 \`let ${name}\` 之后还有赋值（${count} 处）→ ${sample.slice(0, 70)}`);
      }
    }
  }
}

const unique = [...new Set(problems)];
ok(`扫描 ${scannedFuncs} 个函数体，未发现 let 常量被重赋值`, unique.length === 0,
   `${unique.length} 处`);
if (unique.length) {
  console.log('');
  console.log('  可疑处（把 let 改成 var，或改掉赋值逻辑）：');
  for (const p of unique.slice(0, 25)) console.log('    ' + p);
  if (unique.length > 25) console.log(`    …还有 ${unique.length - 25} 处`);
}

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
