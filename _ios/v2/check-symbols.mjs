/**
 * Swift 符号一致性检查（2026-10-17）
 *
 *   node _ios/check-symbols.mjs
 *
 * 为什么需要它：本机没有 Swift 编译器，而「访问一个不存在的成员」是最常见、也最容易
 * 在最后关头炸掉整次构建的错误 —— 比如 `ProfileStore.shared.phone` 里 `phone` 根本没声明，
 * 或者 `Session.shared.signKey` 写成了 `signkey`。
 *
 * 这个脚本做两件事：
 *   ① 收集每个类型（class/struct/enum）声明了哪些成员（var/let/func/static/subscript）
 *   ② 扫全工程里 `Type.shared.member` / `Type.member` 的用法，报出未声明的成员
 *
 * 只做「名字是否存在」这一件事 —— 类型是否匹配仍要靠编译器，但名字错是其中最大的一类。
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'D:\\AAA\\doutu\\ios_v2\\Sources';

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

const files = walk(SRC);
const texts = new Map(files.map((f) => [f, fs.readFileSync(f, 'utf8')]));

/** 去掉注释与字符串字面量，避免把注释里的词当成代码 */
function stripNoise(text) {
  let out = '';
  let i = 0;
  let mode = 'code';
  while (i < text.length) {
    const c = text[i];
    const n = text[i + 1];
    if (mode === 'code') {
      if (c === '/' && n === '/') { mode = 'line'; i += 2; continue; }
      if (c === '/' && n === '*') { mode = 'block'; i += 2; continue; }
      if (c === '"') { mode = 'string'; i += 1; continue; }
      out += c; i += 1; continue;
    }
    if (mode === 'line') { if (c === '\n') { mode = 'code'; out += c; } i += 1; continue; }
    if (mode === 'block') { if (c === '*' && n === '/') { mode = 'code'; i += 2; } else i += 1; continue; }
    if (mode === 'string') {
      if (c === '\\') { i += 2; continue; }
      if (c === '"') { mode = 'code'; i += 1; continue; }
      i += 1; continue;
    }
  }
  return out;
}

// ① 收集类型与其成员
const types = new Map();   // name -> Set(member)
for (const file of files) {
  const code = stripNoise(texts.get(file));
  // 类型声明（class/struct/enum/extension 都算，extension 的方法属于原类型）
  const decls = [...code.matchAll(/\b(?:final\s+)?(?:class|struct|enum|extension)\s+([A-Z]\w+)/g)];
  for (const m of decls) {
    const name = m[1];
    if (!types.has(name)) types.set(name, new Set());
    const members = types.get(name);
    // 在声明之后的一段里找成员（粗略但够用：整个文件的 var/let/func 都算给这个类型）
    for (const mm of code.matchAll(/\b(?:static\s+)?(?:private\s+|fileprivate\s+|public\s+|internal\s+)?(?:var|let|func)\s+([a-zA-Z_]\w*)/g)) {
      members.add(mm[1]);
    }
  }
}

ok(`识别出 ${types.size} 个类型`, types.size > 20);

// ② 扫 `Type.shared.member` / `Type.member` 的用法
const problems = [];
const checkedTypes = new Set(['Session', 'ProfileStore', 'BannerStore', 'BillStore', 'PurseStore',
                              'ReceiptStore', 'WithdrawStore', 'LicaiVersion', 'DtwConfig',
                              'ApiClient', 'Fonts', 'Draw', 'Palette', 'BundleSchemeHandler']);
for (const file of files) {
  const code = stripNoise(texts.get(file));
  for (const m of code.matchAll(/\b([A-Z]\w+)\.(?:shared\.)?([a-z]\w*)\b/g)) {
    const [, typeName, member] = m;
    if (!checkedTypes.has(typeName)) continue;
    if (!types.has(typeName)) continue;
    const members = types.get(typeName);
    // 常见语言内建成员放行
    if (['init', 'self', 'Type', 'shared', 'current', 'main'].includes(member)) continue;
    if (!members.has(member)) problems.push(`${typeName}.${member}  (${path.basename(file)})`);
  }
}

const unique = [...new Set(problems)];
ok('访问的成员都已声明', unique.length === 0, unique.slice(0, 12).join(' | '));
if (unique.length) {
  console.log('');
  console.log('  未声明的成员：');
  for (const p of unique) console.log('    ' + p);
}

// ③ 每个 Screen 子类都实现了协议要求的四件事
//
// 2026-10-24 补：原来只看路径里带 `Screens/` 的文件，于是新加的 `Sources/Video/*`
// 整个被跳过；而且只查了 canvasSize / hitTest —— 视频模块三个页面就是**漏了 maxScroll**
// 才被云端编译器拦下来的（`Screen` 协议里它是必填，扩展里没有默认实现）。
{
  let missingCanvas = [];
  let missingHit = [];
  let missingDraw = [];
  let missingScroll = [];
  for (const f of files) {
    const code = stripNoise(texts.get(f));
    // 只认「类型声明」里的 `: Screen`；`func push(_ screen: Screen)` 这种参数不算
    if (!/(?:final\s+)?class\s+\w+\s*:\s*Screen\b/.test(code)) continue;
    const base = path.basename(f);
    if (!/let canvasSize|var canvasSize/.test(code)) missingCanvas.push(base);
    if (!/func hitTest\(/.test(code)) missingHit.push(base);
    if (!/func draw\(/.test(code)) missingDraw.push(base);
    if (!/func maxScroll\(/.test(code)) missingScroll.push(base);
  }
  ok('每个 Screen 都声明了 canvasSize', missingCanvas.length === 0, missingCanvas.join(','));
  ok('每个 Screen 都实现了 hitTest', missingHit.length === 0, missingHit.join(','));
  ok('每个 Screen 都实现了 draw', missingDraw.length === 0, missingDraw.join(','));
  ok('每个 Screen 都实现了 maxScroll（协议没有默认实现）',
    missingScroll.length === 0, missingScroll.join(','));
}

// ④ 每个 Screen 的 State 必须继承 ScreenState
{
  const bad = [];
  for (const file of files) {
    if (!file.includes('Screens')) continue;
    const code = stripNoise(texts.get(file));
    for (const m of code.matchAll(/final class State\s*:\s*(\w+)/g)) {
      if (m[1] !== 'ScreenState') bad.push(`${path.basename(file)}: State : ${m[1]}`);
    }
  }
  ok('每个 State 都继承 ScreenState', bad.length === 0, bad.join(', '));
}

// ⑤ `push(XxxScreen(), state: XxxScreen.State())` 里的类型必须配对
//
// 写错的话 Swift 照样编译通过（都是 ScreenState），但运行时 `as? Xxx.State` 会失败、
// 页面静默退回默认状态 —— 表现为「点进去是空白页」，而且不报错。这是最难查的一类 bug。
{
  const app = texts.get(files.find((f) => f.endsWith('DoutuApp.swift')) ?? '');
  const mismatched = [];
  let pairs = 0;
  for (const m of app.matchAll(/push\(\s*(\w+)\(\)\s*,\s*state:\s*(\w+)\.State\(\)/g)) {
    pairs += 1;
    if (m[1] !== m[2]) mismatched.push(`${m[1]} ← ${m[2]}.State`);
  }
  ok(`push 的页面与状态类型配对（${pairs} 处）`, mismatched.length === 0, mismatched.join(', '));
}

// ⑥ 滚动归属必须与实现一致
//
// 宿主与页面**都**做 `translate(0,-scroll)` 就是双倍滚动，**都**不做就是不滚。
// 规则：draw 里自带 translate 的屏 → 不能声明 selfScrolls = false；
//       draw 里没有的屏 → 必须声明 selfScrolls = false（由宿主代滚）。
//
// 两个已知例外（不是 bug，记在这里免得反复误报）：
//   · `TermsScreen` —— 宿主给它挂了一个专用透明 UIScrollView（Screen.swift 的
//     `updateTermsScrollView`），滚动不经过 ctx.translate；
//   · `HomeShellScreen` —— 未被使用的旧外壳（V1），它按坐标算 `y - scroll`，同样不走 ctx 平移。
const KNOWN_SCROLL_EXCEPTIONS = new Set(['TermsScreen.swift', 'HomeShellScreen.swift']);
{
  const wrongOwner = [];
  let selfCount = 0;
  let hostCount = 0;
  for (const file of files) {
    if (!file.includes('Screens')) continue;
    if (KNOWN_SCROLL_EXCEPTIONS.has(path.basename(file))) continue;
    const code = stripNoise(texts.get(file));
    // 只看真正实现 Screen 的文件
    if (!/:\s*Screen\b/.test(code)) continue;
    // 页面自己平移的写法有好几种：
    //   translateBy(x: 0, y: -s.scroll)          —— 整页平移
    //   translateBy(x: 0, y: Self.headH - s.scroll) —— 只滚内容区（账单列表）
    // 只要 translateBy 的参数里出现 `.scroll` 就算「自带平移」，
    // 否则会把这几个页面误报成「会不滚」。
    const hasOwnTranslate = /translateBy\([^)]*\.scroll/.test(code);
    const declaresFalse = /let\s+selfScrolls\s*=\s*false|var\s+selfScrolls\s*(?::\s*Bool\s*)?\{\s*false\s*\}/.test(code);
    if (hasOwnTranslate) selfCount += 1;
    if (declaresFalse) hostCount += 1;
    if (hasOwnTranslate && declaresFalse) {
      wrongOwner.push(`${path.basename(file)} 自带 translate 却声明 selfScrolls=false（会少滚一次）`);
    }
    if (!hasOwnTranslate && !declaresFalse) {
      wrongOwner.push(`${path.basename(file)} 不自带 translate 却没声明 selfScrolls=false（会不滚）`);
    }
  }
  ok(`滚动归属正确（${selfCount} 屏自管 / ${hostCount} 屏交给宿主）`, wrongOwner.length === 0,
     wrongOwner.join(' | '));
  if (wrongOwner.length) {
    console.log('');
    console.log('  滚动归属不对的屏：');
    for (const p of wrongOwner) console.log('    ' + p);
  }
}

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
