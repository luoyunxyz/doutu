/**
 * 页面可达性检查（2026-10-17）
 *
 *   node _ios/check-reachable.mjs
 *
 * 对每个 `class XxxScreen: Screen`，在全工程里找 `XxxScreen()` 的实例化。
 * 找不到 = 这一屏**永远进不去**，等于死代码。
 *
 * 为什么值得查：项目早期就有三屏（`ConversationScreen` / `WeiHomeScreen` / `AlipayHomeScreen`）
 * 是 Android 侧的 0 引用死代码，被我剔出了进度分母。iOS 侧同样可能出现
 * 「屏移植好了但没人 push」的情况 —— 那屏等于没做。
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

const files = walk(DIR);
const allText = files.map((f) => fs.readFileSync(f, 'utf8')).join('\n');

// ① 收集所有 Screen 子类
const screens = [];
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  for (const m of text.matchAll(/(?:final\s+)?class\s+([A-Z]\w*Screen)\s*:\s*Screen\b/g)) {
    screens.push({ name: m[1], file: path.basename(file) });
  }
}

// ② 逐个看有没有实例化（`XxxScreen(` 出现在定义处之外）
const unreachable = [];
for (const screen of screens.sort((a, b) => a.name.localeCompare(b.name))) {
  // 统计 `Name(` 在全工程的次数（定义行本身是 `class Name: Screen`，不含括号，所以不算）
  const re = new RegExp('\\b' + screen.name + '\\s*\\(', 'g');
  const count = [...allText.matchAll(re)].length;
  if (count === 0) {
    unreachable.push(screen);
    console.log(`  ✗ ${screen.name.padEnd(26)} ${screen.file}   —— 从未被实例化`);
  } else {
    console.log(`  ✓ ${screen.name.padEnd(26)} ${screen.file.padEnd(34)} ${count} 处`);
  }
}

console.log('');
if (unreachable.length === 0) {
  console.log(`  全部 ${screens.length} 个页面都能从某处进入 ✓`);
} else {
  console.log(`  ${unreachable.length} 个页面无法进入（死代码）：`);
  for (const s of unreachable) console.log(`    ${s.name}  (${s.file})`);
}

// ③ 反向：Router 里 push 过但不存在的类（拼写错误）
const routerText = fs.readFileSync(path.join(DIR, 'App', 'DoutuApp.swift'), 'utf8');
const pushed = new Set();
for (const m of routerText.matchAll(/push\(\s*([A-Z]\w*)\s*\(/g)) pushed.add(m[1]);
const known = new Set(screens.map((s) => s.name));
const phantom = [...pushed].filter((name) => !known.has(name));

console.log('');
if (phantom.length === 0) {
  console.log(`  Router 里 push 的 ${pushed.size} 种页面全部存在 ✓`);
} else {
  console.log(`  Router 里 push 了不存在的页面（会编译错误）：${phantom.join(', ')}`);
}

console.log('');
const fail = unreachable.length + phantom.length;
console.log(`结果\n  通过 ${screens.length - unreachable.length + (phantom.length === 0 ? 1 : 0)} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
