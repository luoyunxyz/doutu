/**
 * 找出「内容画完却没有铺底板」的页面。
 *
 *   node _ios/v2/check-board.mjs
 *
 * 背景：安卓的页面大多在内容结束后露出浅灰底板；我们如果用 `MockPage.background`
 * （默认白）又不调用 `bottomFill(...)`，那一页就会白到底 —— 就是之前反馈的那个问题。
 *
 * 判定：在 `page(state:)` 的函数体里既没有 `bottomFill`，也没有把
 * `pageBackground` 设成非白色，就报出来，由人确认「这一页是不是本来就该白到底」。
 */
import fs from 'node:fs';

const DIR = 'ios_v2/Sources/Mock';
const files = fs.readdirSync(DIR).filter((f) => f.startsWith('Mock') && f.endsWith('.swift'));

const rows = [];
for (const file of files) {
  const lines = fs.readFileSync(`${DIR}/${file}`, 'utf8').split(/\r?\n/);
  for (let i = 0; i < lines.length; i += 1) {
    if (!/static func page\(state:/.test(lines[i])) continue;
    let depth = 0;
    let end = i;
    for (let j = i; j < lines.length; j += 1) {
      depth += (lines[j].match(/\{/g) || []).length - (lines[j].match(/\}/g) || []).length;
      if (depth <= 0 && j > i) { end = j; break; }
    }
    const body = lines.slice(i, end + 1).join('\n');
    const hasBoard = /bottomFill\(/.test(body);
    const hasBg = /pageBackground\s*=\s*(?!MockInk\.white)/.test(body);
    if (!hasBoard && !hasBg) {
      rows.push(`${file}:${i + 1}  ${body.match(/return (\w+)\.page|switch state\.sub/)?.[0] ?? ''}`);
    }
    i = end;
  }
}

if (!rows.length) {
  console.log('[OK] 每个 page(state:) 都铺了底板或声明了非白底');
} else {
  console.log(`以下 ${rows.length} 个 page(state:) 没有铺底板（需逐个人工确认是否本来就该白到底）：`);
  for (const r of rows) console.log('   ', r);
}
