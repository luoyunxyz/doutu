/**
 * 令牌完整性审计（2026-10-17，并入 verify-all）
 *
 *   node _ios/audit-tokens2.mjs
 *
 * 为什么需要它：这一轮我列过一份「已知缺口」清单，结果里面有一条是**假的** ——
 * 我把「账单筛选面板」列成未完成，回头查 Android 的 `BillActivity.act()` 才发现
 * 它根本没有 `filter` 分支，那个箭头在 Android 上同样是空操作。差点为了补假缺口
 * 去实现 Android 并不存在的功能，那才是真的破坏 1:1。
 *
 * 反过来也有漏报：`BankSheet`（银行卡选择层）和芝麻信用的 `add:` 是 Android 有、
 * 我移植时静默丢掉的，靠这个脚本才找出来。
 *
 * 检查方式：Android 各 Activity 里出现过的令牌，Swift 侧必须**要么产生、要么有分支**。
 * 字符串插值（`"nav:\(x)"`）也算产生 —— 这个盲区害我误报过 4 个。
 */
import fs from 'node:fs';
import path from 'node:path';

const ANDROID = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\java\\com\\luoyun\\doutu';
const IOS = 'D:\\AAA\\doutu\\ios_v2\\Sources';

/**
 * 已知的「命名不同但等价」令牌 —— 不是缺口，列在这里是为了让脚本安静：
 * Android 用前缀族，iOS 用了更明确的两个名字。
 */
const RENAMED = {
  'city:': ['city:0', 'city:1'],
  'edit:msg': ['edit:msg0', 'edit:msg1'],
};

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  [OK]   ${name}`); }
  else { fail += 1; console.log(`  [FAIL] ${name} ${extra}`); }
};

function walk(dir, ext, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, ext, out);
    else if (full.endsWith(ext)) out.push(full);
  }
  return out;
}

// ① Android：所有 Activity 里「有实际行为」的令牌
const androidTokens = new Set();
for (const file of walk(ANDROID, '.java')) {
  const text = fs.readFileSync(file, 'utf8');
  for (const m of text.matchAll(/token\.equals\("([a-zA-Z:]+)"\)/g)) androidTokens.add(m[1]);
  for (const m of text.matchAll(/token\.startsWith\("([a-zA-Z:]+)"\)/g)) androidTokens.add(m[1]);
}

// ② iOS：页面产生的令牌 + Router 有分支的令牌（都算「接了」）
const swiftFiles = walk(IOS, '.swift');
const screenText = swiftFiles
  .filter((f) => f.includes('Screens'))
  .map((f) => fs.readFileSync(f, 'utf8'))
  .join('\n');
const appText = swiftFiles.find((f) => f.endsWith('DoutuApp.swift'))
  ? fs.readFileSync(swiftFiles.find((f) => f.endsWith('DoutuApp.swift')), 'utf8') : '';

/** 令牌是否在 iOS 侧「产生或接住」 */
function covered(token) {
  // 字面量或插值出现在页面里
  if (screenText.includes('"' + token + '"') || screenText.includes('"' + token + '\\(')) {
    return true;
  }
  // Router 有 case 分支
  if (new RegExp('case\\s+"' + token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '"').test(appText)) {
    return true;
  }
  if (appText.includes('hasPrefix("' + token + '")')) return true;
  // 已知的等价改名
  const renamed = RENAMED[token];
  if (renamed && renamed.every((alias) => screenText.includes('"' + alias + '"'))) return true;
  return false;
}

const missing = [...androidTokens].filter((t) => !covered(t)).sort();
ok(`Android 的 ${androidTokens.size} 个令牌，iOS 侧都已覆盖`, missing.length === 0,
   `缺 ${missing.join(', ')}`);
if (missing.length) {
  console.log('');
  console.log('  未覆盖的令牌（对应功能可能被静默丢掉）：');
  for (const t of missing) {
    console.log('    ' + t);
    // 在 Android 哪个文件里出现，方便定位
    for (const file of walk(ANDROID, '.java')) {
      const text = fs.readFileSync(file, 'utf8');
      if (text.includes('"' + t + '"')) {
        console.log('      ← ' + path.basename(file));
      }
    }
  }
}

// ③ 反向：报告 iOS 独有令牌数（多为页面私有，仅供人工过目）
const swiftTokens = new Set();
for (const m of screenText.matchAll(/return\s+"([a-zA-Z:][^"\\]*)"/g)) swiftTokens.add(m[1].split('\\')[0]);
for (const m of appText.matchAll(/case\s+"([^"]*)"/g)) swiftTokens.add(m[1]);
const swiftOnly = [...swiftTokens].filter((t) => t && !androidTokens.has(t) && t.length > 2).length;
console.log('');
console.log(`  iOS 独有的页面私有令牌：${swiftOnly} 个（多为 feed: / key: 这类新命名，仅供过目）`);

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
