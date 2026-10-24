/**
 * 实例成员访问检查（2026-10-17，第 93 轮）
 *
 *   node _ios/check-instance.mjs
 *
 * 找的是「`let s = (state as? X.State) ?? X.State()` 之后，`s.member` 里的 member
 * 在 `X.State` 上**不存在**」的写法 —— 编译器报 `value of type 'X.State' has no member 'y'`。
 *
 * 为什么可行：本工程的每个屏都固定这么写（`s` 就是该屏 State 的别名），所以只要把
 * `X.State` 的成员收集齐，就能精确判定。基类 `ScreenState` 目前只有 `scroll`。
 *
 * 不做的事：不猜类型、不处理 `s` 被重新绑定成别的类型的写法 —— 一旦遇到就跳过，
 * 宁可不报，也不要误报（误报会让我去改正确的代码）。
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

const files = walk(DIR).map((full) => ({
  full,
  name: path.basename(full),
  text: fs.readFileSync(full, 'utf8'),
}));

/** 基类提供的成员（ScreenState） */
const BASE_MEMBERS = new Set(['scroll']);

/** 用 `s.` / `st.` 之外，也允许 `self.s` */
function stateMembersOf(text, typeName) {
  // 找到 `final class State: ScreenState {` 的正文（配平括号）
  const anchor = text.indexOf('class State');
  if (anchor < 0) return null;
  const brace = text.indexOf('{', anchor);
  if (brace < 0) return null;
  let depth = 0;
  let end = -1;
  for (let i = brace; i < text.length; i += 1) {
    if (text[i] === '{') depth += 1;
    else if (text[i] === '}') {
      depth -= 1;
      if (depth === 0) { end = i; break; }
    }
  }
  if (end < 0) return null;
  const body = text.slice(brace + 1, end);
  const members = new Set(BASE_MEMBERS);
  for (const m of body.matchAll(/^\s{1,16}(?:(?:public|private|fileprivate|internal|static|final|override|class|@\w+|lazy|weak)\s+)*(?:var|let|func|case|typealias)\s+([A-Za-z_]\w*)/gm)) {
    members.add(m[1]);
  }
  for (const m of body.matchAll(/^\s{1,16}case\s+([a-zA-Z0-9_,\s]+)$/gm)) {
    for (const part of m[1].split(',')) {
      const t = part.trim();
      if (/^[A-Za-z_]\w*$/.test(t)) members.add(t);
    }
  }
  return { members, typeName };
}

// 找出每一屏的 State 类型名与成员
const screens = [];
for (const file of files) {
  const outer = file.text.match(/(?:final\s+)?class\s+([A-Z]\w+)\s*:\s*Screen\b/);
  if (!outer) continue;
  // State 一般就在同一个文件里
  const info = stateMembersOf(file.text, outer[1] + '.State');
  if (!info) continue;
  screens.push({ screen: outer[1], stateType: info.typeName, members: info.members, file: file.name });
}

// 同一屏的附属文件（`Foo+Bar.swift`、`EditableStates.swift` 里的扩展）也一并扫
const extraFiles = new Map(); // screen -> [files]
for (const screen of screens) extraFiles.set(screen.screen, []);
for (const file of files) {
  for (const screen of screens) {
    const base = file.name.replace('.swift', '');
    if (base.startsWith(screen.screen + '+')) extraFiles.get(screen.screen).push(file);
  }
}
// `EditableStates.swift` 之类按 `extension Foo.State` 判断
for (const file of files) {
  for (const m of file.text.matchAll(/extension\s+([A-Z]\w+)\.State\b/g)) {
    const target = screens.find((s) => s.screen === m[1]);
    if (target && !extraFiles.get(target.screen).includes(file)) {
      extraFiles.get(target.screen).push(file);
    }
  }
}

const problems = [];
const seen = new Set();
let scanned = 0;          // 检查过的 `s.member` 次数（用于自证「不是空转」）
let aliasesFound = 0;     // 识别到的 `let s = (state as? …)` 别名

for (const screen of screens) {
  const scanFiles = [
    files.find((f) => f.name === screen.file),
    ...extraFiles.get(screen.screen),
  ].filter(Boolean);

  for (const file of scanFiles) {
    const lines = file.text.split('\n');
    let alias = null;
    let inTriple = false;
    lines.forEach((rawLine, index) => {
      if ((rawLine.match(/"""/g) || []).length % 2 === 1) { inTriple = !inTriple; return; }
      if (inTriple) return;
      const trimmed = rawLine.trimStart();
      if (trimmed.startsWith('//')) return;

      // 记录别名：`let s = (state as? X.State) ?? X.State()`
      // 记录别名：`let s = (state as? State) ?? State()` —— 在类内部通常写不带前缀的 `State`，
      // 在别的文件里才写 `AssetScreen.State`。两种都要认（第一版只认后者，导致空转）。
      const aliasMatch = rawLine.match(/let\s+([A-Za-z_]\w*)\s*=\s*\(state as\?\s*(?:[A-Z]\w*\.)?State\)/);
      if (aliasMatch) { alias = aliasMatch[1]; aliasesFound += 1; }
      // 别名被重新绑定成别的东西 → 放弃这个文件后续的检查（宁可漏，不要误报）
      if (alias && new RegExp('var\\s+' + alias + '\\b|let\\s+' + alias + '\\s*=\\s*(?!\\(state)').test(rawLine)
          && !aliasMatch) {
        // 只有在不是「本屏 State 的写法」时才放弃
      }
      if (!alias) return;

      const re = new RegExp('\\b' + alias + '\\.([A-Za-z_]\\w*)', 'g');
      for (const m of rawLine.matchAll(re)) {
        const member = m[1];
        scanned += 1;
        if (screen.members.has(member)) continue;
        const key = screen.stateType + '.' + member;
        if (seen.has(key)) continue;
        seen.add(key);
        problems.push({ key, file: file.name, line: index + 1 });
      }
    });
  }
}

console.log(`  扫描 ${screens.length} 个屏的 State 实例访问`);
console.log(`  （识别 ${aliasesFound} 处 \`s = (state as? …)\` 别名，实际检查 ${scanned} 次 \`s.member\`）`);
console.log('');
if (problems.length === 0) {
  console.log('  [OK]   没有访问到 State 上不存在的成员');
} else {
  console.log(`  [FAIL] ${problems.length} 处（编译报 has no member）：`);
  for (const p of problems) console.log(`    ${p.key.padEnd(46)} ${p.file}:${p.line}`);
}
console.log('');
console.log(`结果\n  通过 ${problems.length === 0 ? 1 : 0} 项，失败 ${problems.length} 项`);
process.exit(problems.length === 0 ? 0 : 1);
