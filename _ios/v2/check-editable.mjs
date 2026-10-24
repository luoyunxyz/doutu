/**
 * 可编辑令牌的「写入侧」检查（2026-10-17）
 *
 *   node _ios/check-editable.mjs
 *
 * 起因：`AssetScreen` / `BalanceScreen` / `HuabeiScreen` / `YuebaoScreen` / `ZhiHomeScreen`
 * 都 emit 了 `edit:` / `num:` / `text:` 令牌，但它们的 State 没实现 `EditableState` ——
 * 于是 Router 把改值写进了 `GrowthStore`（完全不相干的存储），界面不变。
 * 用户看到的是「点了弹框、改完没反应」。
 *
 * 这个检查把「谁 emit 了可编辑令牌」与「谁实现了 EditableState」对起来，
 * 缺写入侧的直接报出来。
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

const files = walk(DIR);
const allText = files.map((f) => fs.readFileSync(f, 'utf8')).join('\n');

/** 找出实现了 EditableState 的类型名 */
const editableTypes = new Set();
for (const m of allText.matchAll(/extension\s+([A-Za-z_][\w.]*)\s*:\s*EditableState\b/g)) {
  editableTypes.add(m[1]);
}

/** 每个页面文件：emit 的可编辑令牌 + 它的 State 类型名 */
const rows = [];
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  const base = path.basename(file);
  if (!base.includes('Screen')) continue;

  // emit 的可编辑令牌（字面量与插值都算）
  const tokens = new Set();
  for (const m of text.matchAll(/"(?:edit|num|text):([A-Za-z][\w.]*)/g)) tokens.add(m[1]);
  for (const m of text.matchAll(/"(?:edit|num|text):"\s*\+/g)) tokens.add('(动态)');
  if (tokens.size === 0) continue;

  // 这个文件里的 State 类型名：`final class State: ScreenState` 属于外层类型
  const outer = text.match(/(?:final\s+)?class\s+([A-Z]\w+)\s*:\s*Screen\b/);
  const stateName = outer ? `${outer[1]}.State` : '(未找到外层类型)';
  rows.push({ base, tokens: [...tokens], stateName });
}

const missing = rows.filter((r) => !editableTypes.has(r.stateName));
ok(`${rows.length} 个屏 emit 了可编辑令牌，都实现了 EditableState`, missing.length === 0,
   `${missing.length} 个缺写入侧`);

console.log('');
console.log('  已实现 EditableState 的类型：' + [...editableTypes].join(', '));
console.log('');
for (const r of rows) {
  const done = editableTypes.has(r.stateName);
  console.log(`  ${done ? '✓' : '✗'} ${r.base.padEnd(34)} ${r.stateName.padEnd(30)} ` +
              `${r.tokens.length} 个令牌：${r.tokens.slice(0, 6).join(', ')}`);
}

if (missing.length) {
  console.log('');
  console.log('  缺写入侧（点了会写到 GrowthStore、界面不变）：');
  for (const r of missing) console.log(`    ${r.base} → ${r.stateName}`);
}

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
