/**
 * State 字段闲置检查（2026-10-17）
 *
 *   node _ios/check-state.mjs
 *
 * 找的是「State 里声明了、但这一屏从不读」的字段。
 *
 * 为什么：出行页的 `checkChild` / `checkBaby` 就是这样 —— 声明了、也有 toggle 逻辑，
 * 但 `draw` 从不读，于是"点了没反应"。反过来，如果 Android 那边也没读（这两个就是），
 * 那就是两边一致的死字段，不用管。所以这个脚本只**列出来供人确认**，不判失败。
 */
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:\\AAA\\doutu\\ios_v2\\Sources';

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.swift')) out.push(full);
  }
  return out;
}

// 把同一个类型的多个文件合并（HomeShellScreen 分三个文件）
const files = walk(DIR);
const byType = new Map();
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  const outer = text.match(/(?:final\s+)?class\s+([A-Z]\w+)\s*:\s*Screen\b/);
  if (!outer) continue;
  const key = outer[1];
  if (!byType.has(key)) byType.set(key, []);
  byType.get(key).push(file);
}
// 同一屏的其它文件也要算进来：`Foo+Something.swift` 里的 `extension Foo` 能访问 Foo.State
for (const [key, list] of byType) {
  for (const file of files) {
    const base = path.basename(file).replace('.swift', '');
    if (base.startsWith(key + '+') && !list.includes(file)) list.push(file);
  }
}
// `.State` 的扩展（`EditableStates.swift` 那种）也算
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  for (const m of text.matchAll(/extension\s+([A-Z]\w+)\.State\b/g)) {
    const key = m[1];
    if (byType.has(key) && !byType.get(key).includes(file)) byType.get(key).push(file);
  }
}

let total = 0;
const unused = [];

for (const [typeName, typeFiles] of [...byType].sort()) {
  const text = typeFiles.map((f) => fs.readFileSync(f, 'utf8')).join('\n');
  // 找 `final class State: ScreenState {` 里的 var/let 声明
  const stateMatch = text.match(/final class State\s*:\s*ScreenState\s*\{([\s\S]*?)\n    \}/);
  if (!stateMatch) continue;
  const body = stateMatch[1];
  const fields = [...body.matchAll(/\b(?:var|let)\s+([a-zA-Z_]\w*)\s*[:=]/g)].map((m) => m[1]);
  for (const field of new Set(fields)) {
    total += 1;
    // 这个字段在整屏代码里出现几次（排除声明本身）
    const occurrences = [...text.matchAll(new RegExp('\\b' + field + '\\b', 'g'))].length;
    if (occurrences <= 1) {
      unused.push({ typeName, field, files: typeFiles.map((f) => path.basename(f)) });
    }
  }
}

console.log(`  扫描 ${byType.size} 个屏的 State，共 ${total} 个字段`);
console.log('');
if (unused.length === 0) {
  console.log('  ✓ 没有闲置字段');
} else {
  console.log(`  ${unused.length} 个字段只声明、从不被读写：`);
  for (const u of unused) {
    console.log(`    ${u.typeName}.${u.field}    (${u.files.join(', ')})`);
  }
  console.log('');
  console.log('  说明：这些字段要么是摆设（Android 那边同样是死字段，属 1:1 一致），');
  console.log('        要么是「本该驱动某处绘制、但漏接了」的 bug —— 逐个对 Android 确认。');
}

console.log('');
console.log('结果\n  通过 1 项，需人工确认 ' + unused.length + ' 项');
process.exit(0);
