/**
 * 核对「模拟」宫格的 31 个入口是否都接上了版式。
 *
 *   node _ios/v2/check-entries.mjs
 *
 * 检查三件事：
 *   1. `MockHome.labels` 有 31 项；
 *   2. `MockModule` 的 case 覆盖索引 0..30（不重不漏）；
 *   3. 每个 case 的 `title` 与同索引的宫格标签一致；
 *   4. 每个 case 都在 `MockComposer.hasLayout` 的白名单里（否则点进去还是「制作中」）。
 */
import fs from 'node:fs';

const home = fs.readFileSync('ios_v2/Sources/Mock/MockHome.swift', 'utf8');
const kit = fs.readFileSync('ios_v2/Sources/Mock/MockKit.swift', 'utf8');
const composer = fs.readFileSync('ios_v2/Sources/Mock/MockNye.swift', 'utf8');

const labelsBlock = home.match(/labels[^=]*=\s*\[([\s\S]*?)\]/)[1];
const labels = labelsBlock.split(',').map((x) => x.trim().replace(/^"|"$/g, '')).filter(Boolean);

const cases = [...kit.matchAll(/^\s{4}case (\w+) = (\d+)/gm)].map((m) => ({ name: m[1], idx: +m[2] }));

const titleBlock = kit.slice(kit.indexOf('var title: String'), kit.indexOf('var subs: [String]'));
const titles = {};
for (const m of titleBlock.matchAll(/case \.(\w+): return "([^"]*)"/g)) titles[m[1]] = m[2];

const hasLayoutBlock = composer.slice(composer.indexOf('static func hasLayout'));
const layoutSet = new Set(
  [...hasLayoutBlock.matchAll(/\.(\w+)/g)].map((m) => m[1]).filter((n) => n !== 'return' && n !== 'true'),
);

const fails = [];
if (labels.length !== 31) fails.push(`宫格标签 ${labels.length} 个，应为 31`);
if (cases.length !== 31) fails.push(`枚举 case ${cases.length} 个，应为 31`);

const seen = new Map();
for (const c of cases) {
  if (seen.has(c.idx)) fails.push(`索引 ${c.idx} 重复：${seen.get(c.idx)} / ${c.name}`);
  seen.set(c.idx, c.name);
}
for (let i = 0; i < 31; i += 1) {
  if (!seen.has(i)) fails.push(`缺少索引 ${i}（宫格「${labels[i] ?? '?'}」）`);
}

for (const c of cases) {
  const label = labels[c.idx];
  const title = titles[c.name];
  if (label !== title) fails.push(`索引 ${c.idx} 标题不一致：宫格「${label}」 vs 枚举「${title}」`);
  if (!layoutSet.has(c.name)) fails.push(`索引 ${c.idx}（${c.name}）没有版式分支`);
}

if (fails.length) {
  console.log(`[FAIL] ${fails.length} 处：`);
  for (const f of fails) console.log('   -', f);
  process.exit(1);
}

console.log(`[OK] 31 个模拟入口全部接上（标签 / 索引 / 标题 / 版式分支一致）`);
