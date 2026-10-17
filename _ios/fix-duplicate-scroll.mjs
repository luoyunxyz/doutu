// 删掉 10 个屏 State 里与基类 ScreenState 重复的 `var scroll`
// 用法：node _ios/fix-duplicate-scroll.mjs
//
// 编译错误（CI 日志原文）：
//   error: cannot override with a stored property 'scroll'
// 原因：`class ScreenState { var scroll: CGFloat = 0 }` 已有这个存储属性，
// 子类再声明一次就是覆盖存储属性 —— Swift 不允许。基类已经提供，删掉子类的即可。
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
const DIR = path.join(ROOT, 'ios', 'Sources', 'Screens');

const TARGETS = [
  'AssetScreen.swift', 'BillAnalyticsScreen.swift', 'BillDetailScreen.swift',
  'BillScreen.swift', 'CollectMoneyScreen.swift', 'GrowthScreen.swift',
  'LicaiScreen.swift', 'ReceiptScreen.swift', 'ZhiHomeScreen.swift',
  'ZhimaCreditScreen.swift',
];

let removed = 0;
const report = [];
for (const name of TARGETS) {
  const file = path.join(DIR, name);
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  const kept = [];
  let localRemoved = 0;
  for (const line of lines) {
    // 只删「单独一行、8 空格缩进、裸声明」的那种（State 的属性区）
    if (/^ {8}var scroll: CGFloat = 0\s*$/.test(line)) {
      localRemoved += 1;
      continue;
    }
    kept.push(line);
  }
  if (localRemoved > 0) {
    fs.writeFileSync(file, kept.join('\n'), 'utf8');
    removed += localRemoved;
  }
  report.push(`  ${name.padEnd(26)} 删除 ${localRemoved} 行`);
}

console.log(report.join('\n'));
console.log('');
console.log(`  共删除 ${removed} 行（期望 10 行）`);
