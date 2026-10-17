// 修第二轮编译暴露的 44 个错误（2026-10-17）
// 用法：node _ios/fix-compile-round2.mjs
//
// 错误来源：ci-logs/build.log（CI 自动提交回仓库）
//   LicaiScreen.swift:587-611   error: cannot find 'state' in scope            （25 处）
//   DoutuApp.swift:387…792      error: reference to property 'host' in closure requires self.  （17 处）
//   DoutuApp.swift:454          error: extraneous argument label 'safe:' in subscript
//   HomeShellScreen+Entertainment.swift:94  error: cannot call value of non-function type 'Bool'
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
const S = path.join(ROOT, 'ios', 'Sources');
const rel = (p) => path.relative(ROOT, p);

function patch(file, transform) {
  const full = path.join(S, file);
  const before = fs.readFileSync(full, 'utf8');
  const after = transform(before);
  if (after === before) {
    console.log(`  ${file}: 没有变化（请检查）`);
    return 0;
  }
  fs.writeFileSync(full, after, 'utf8');
  const changed = after.split('\n').filter((l, i) => l !== before.split('\n')[i]).length;
  console.log(`  ${file}: 改动 ${changed} 行`);
  return changed;
}

// ① LicaiScreen：applyEdit 在 `extension LicaiScreen.State: EditableState` 里，
//    `state.` 应为 `self.`（该扩展没有 state 变量）。
patch('Screens/LicaiScreen.swift', (t) => {
  const lines = t.split('\n');
  let inside = false;
  return lines.map((line) => {
    if (/func applyEdit\(_ key: String, _ text: String\)/.test(line)) inside = true;
    if (!inside) return line;
    // 到下一个 func 或扩展结束为止
    if (/^\s{4}func /.test(line) && !/applyEdit/.test(line)) inside = false;
    return line.replace(/\bstate\./g, 'self.');
  }).join('\n');
});

// ② DoutuApp.Router：闭包里访问 host / showToast 必须写 self.
patch('App/DoutuApp.swift', (t) => t
  .replace(/(?<!self\.)(?<!let )\bhost(?=[?.])/g, 'self.host')
  .replace(/(?<!self\.)\bshowToast\(/g, 'self.showToast(')
  // ③ `allCards[safe: index]` —— Swift 没有 safe 下标
  .replace(/self\.host\?\.state as\? WithdrawScreen\.State/g, 'self.host?.state as? WithdrawScreen.State'));

// ④ Entertainment：isEmpty 是属性，不是方法
patch('Screens/HomeShellScreen+Entertainment.swift', (t) => t.replace(/\.isEmpty\(\)/g, '.isEmpty'));

console.log('');
console.log('  提示：`[safe:]` 需要手工改（结构不同），下面打印那一行');
const doutu = fs.readFileSync(path.join(S, 'App', 'DoutuApp.swift'), 'utf8').split('\n');
doutu.forEach((l, i) => { if (/\[safe:/.test(l)) console.log(`    DoutuApp.swift:${i + 1}  ${l.trim()}`); });
