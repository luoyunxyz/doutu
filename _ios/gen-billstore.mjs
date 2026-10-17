/**
 * 从 `BillStore.java` 抽取数据表，生成 Swift 数组（2026-10-17）
 *
 *   node _ios/gen-billstore.mjs
 *
 * 为什么要脚本而不是手抄：`SHOPS` 有 69 家商户 × 6 个字段、`GOODS` 50 条、`AMOUNTS` 若干，
 * 手抄一定会错、错一个字符账单页的字就对不上。脚本按 Java 的数组字面量逐项搬，保证逐字一致。
 *
 * 输出：`ios/Sources/Screens/BillStoreData.swift`
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\java\\com\\luoyun\\doutu\\features\\zhi\\data\\BillStore.java';
const OUT = 'D:\\AAA\\doutu\\ios\\Sources\\Screens\\BillStoreData.swift';

const java = fs.readFileSync(SRC, 'utf8');

/** 取出 `name = { ... };` 之间的内容 */
function block(name) {
  const start = java.indexOf(`${name} = {`);
  if (start < 0) throw new Error(`找不到 ${name}`);
  let i = java.indexOf('{', start);
  let depth = 0;
  let end = i;
  for (; end < java.length; end += 1) {
    if (java[end] === '{') depth += 1;
    else if (java[end] === '}') {
      depth -= 1;
      if (depth === 0) break;
    }
  }
  return java.slice(i + 1, end);
}

/** 从一段数组字面量里取出所有字符串字面量，按行分组 */
function rows(text) {
  const out = [];
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed.startsWith('{')) continue;
    const cells = [...trimmed.matchAll(/"([^"]*)"/g)].map((m) => m[1]);
    if (cells.length) out.push(cells);
  }
  return out;
}

const shops = rows(block('SHOPS'));
// 一维数组（商品/金额）不是每行一个 `{...}`，直接扫全部字面量
const goods = [...block('GOODS').matchAll(/"([^"]*)"/g)].map((m) => m[1]);
const amounts = block('AMOUNTS')
  .split(',')
  .map((t) => t.trim().replace(/[fFdD]$/, ''))
  .filter((t) => /^-?\d+(\.\d+)?$/.test(t))
  .map(Number);

function escape(value) {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

const lines = [];
lines.push('import Foundation');
lines.push('');
lines.push('/// 账单演示数据表 —— **由脚本从 `BillStore.java` 自动生成**，不要手改。');
lines.push('/// 生成命令：`node _ios/gen-billstore.mjs`');
lines.push('///');
lines.push('/// 商户池里每行 6 项：名称、分类、字母标、标底色、标字色、收款方全称。');
lines.push('enum BillStoreData {');
lines.push('    static let shops: [[String]] = [');
for (const cells of shops) {
  lines.push(`        [${cells.map((c) => `"${escape(c)}"`).join(', ')}],`);
}
lines.push('    ]');
lines.push('');
lines.push('    static let goods: [String] = [');
for (const item of goods) lines.push(`        "${escape(item)}",`);
lines.push('    ]');
lines.push('');
lines.push(`    static let amounts: [Double] = [${amounts.join(', ')}]`);
lines.push('}');
lines.push('');

fs.writeFileSync(OUT, lines.join('\n'), 'utf8');
console.log(`✅ 已生成 ${OUT}`);
console.log(`   商户 ${shops.length} 家 / 商品 ${goods.length} 条 / 金额档 ${amounts.length} 个`);
