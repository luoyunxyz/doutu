/**
 * switch 语句清单（2026-10-17，第 94 轮）
 *
 *   node _ios/list-switches.mjs
 *
 * 只做一件事：把全部 `switch` 连同它的 case 标签、有没有 `default` 列成表，
 * 供人工核对「枚举 switch 是否穷尽」。
 *
 * 为什么不自动判穷尽：本工程的 switch 多数在 Int（页签下标）和 String（令牌）上，
 * 这两种 Swift 也要求穷尽 —— 但都写了 `default`。真正需要看的是少数枚举 switch，
 * 而「case 一共几个、枚举有几个 case」要跨文件解析，自动判容易误报。
 * 49 条直接看比写个会误报的检查更划算。
 */
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:\\AAA\\doutu\\ios\\Sources';

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.swift')) out.push(full);
  }
  return out;
}

const rows = [];
for (const full of walk(DIR)) {
  const name = path.basename(full);
  const lines = fs.readFileSync(full, 'utf8').split('\n');
  for (let i = 0; i < lines.length; i += 1) {
    if (!/^\s*switch\s+/.test(lines[i])) continue;
    // 收集到配平的 `}` 为止
    let depth = 0;
    let started = false;
    const cases = [];
    let hasDefault = false;
    for (let j = i; j < lines.length; j += 1) {
      const line = lines[j];
      const opens = (line.match(/\{/g) || []).length;
      const closes = (line.match(/\}/g) || []).length;
      if (opens > 0) started = true;
      for (const m of line.matchAll(/case\s+([^:]+):/g)) {
        const label = m[1].trim().replace(/\s+/g, ' ');
        if (label !== 'default' && label.length < 60) cases.push(label);
      }
      if (/^\s*default\s*:/.test(line)) hasDefault = true;
      depth += opens - closes;
      if (started && depth <= 0) break;
    }
    rows.push({
      file: name,
      line: i + 1,
      subject: lines[i].trim().replace(/^switch\s+/, '').slice(0, 40),
      cases,
      hasDefault,
    });
  }
}

console.log(`  共 ${rows.length} 条 switch\n`);
const noDefault = rows.filter((r) => !r.hasDefault);
console.log(`  没有 default 的：${noDefault.length} 条（这些必须是枚举穷尽，人工核对）\n`);
for (const r of noDefault) {
  console.log(`  ${r.file}:${r.line}   switch ${r.subject}`);
  console.log(`      case: ${r.cases.join(' | ') || '（无）'}`);
}
console.log('');
console.log(`  有 default 的：${rows.length - noDefault.length} 条（不需要穷尽）`);
