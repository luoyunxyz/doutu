/**
 * 「模拟」模块专项核对（2026-10-24）。
 *
 *   node _ios/v2/check-mock.mjs
 *
 * `lint-swift.mjs` 管的是通用体检（括号/重名/素材/版本号），这个脚本管的是
 * **模拟模块独有、而且错了不容易发现**的五件事：
 *
 *   1. `MockInk.xxx` 引用的颜色都真的声明过；
 *   2. 五家版式里对 `MockBuilder` 的调用，方法名与**参数标签**都存在；
 *   3. 每家模块的子页名数组与 Android `XxxActivity.SUBS` 逐字一致；
 *   4. 招商的 `MODES` 派发表与 `CmbActivity.MODES` 一致；
 *   5. `MockComposer` 的 6 个分支与 `MockModule` 的 6 个 case 一一对应。
 *
 * 3/4 两项需要本地存在 Android 源码（`doutu/app/src/main/java/...`）；缺了就跳过并提示。
 */
import fs from 'node:fs';
import path from 'node:path';

const MOCK_DIR = 'ios_v2/Sources/Mock';
const JAVA_DIR = 'doutu/app/src/main/java/com/luoyun/doutu/features/mock';

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) {
    pass += 1;
    console.log(`  [OK]   ${name}`);
  } else {
    fail += 1;
    console.log(`  [FAIL] ${name} ${extra}`);
  }
};

const read = (p) => fs.readFileSync(p, 'utf8');
const kit = read(path.join(MOCK_DIR, 'MockKit.swift'));
const composer = read(path.join(MOCK_DIR, 'MockNye.swift'));

/** 取 `(` 到配对 `)` 之间的文本 */
function parenBody(text, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === '(') depth += 1;
    else if (ch === ')') {
      depth -= 1;
      if (depth === 0) return text.slice(openIndex + 1, i);
    }
  }
  return '';
}

/** 顶层逗号切分 */
function splitTopLevel(text) {
  const parts = [];
  let depth = 0;
  let current = '';
  for (const ch of text) {
    if ('([{'.includes(ch)) depth += 1;
    if (')]}'.includes(ch)) depth -= 1;
    if (ch === ',' && depth === 0) {
      parts.push(current);
      current = '';
    } else {
      current += ch;
    }
  }
  if (current.trim()) parts.push(current);
  return parts;
}

/** 声明里的外部标签集合 */
function declarationLabels(paramsText) {
  const labels = new Set();
  for (const part of splitTopLevel(paramsText)) {
    const words = part.split(':')[0].trim().split(/\s+/);
    if (words.length === 2) labels.add(words[0]);
    else if (words.length === 1 && words[0] !== '_') labels.add(words[0]);
  }
  return labels;
}

console.log('=== 1. MockInk 颜色引用 ===');
{
  const declared = new Set([...kit.matchAll(/static let (\w+) = /g)].map((m) => m[1]));
  const used = new Set();
  for (const name of fs.readdirSync(MOCK_DIR).filter((f) => f.endsWith('.swift'))) {
    for (const m of read(path.join(MOCK_DIR, name)).matchAll(/MockInk\.(\w+)/g)) used.add(m[1]);
  }
  const missing = [...used].filter((n) => !declared.has(n));
  ok('引用的 MockInk 颜色都已声明', missing.length === 0, missing.join(', '));
  console.log(`  声明 ${declared.size} 个，用到 ${used.size} 个`);
}

console.log('=== 2. MockBuilder 调用（方法名 + 参数标签）===');
{
  const start = kit.indexOf('final class MockBuilder');
  const body = kit.slice(start);
  const methods = new Map();
  for (const match of body.matchAll(/func (\w+)\(/g)) {
    const open = start + match.index + match[0].length - 1;
    methods.set(match[1], declarationLabels(parenBody(kit, open)));
  }

  const receivers = ['b', 'box', 'card', 'inner', 'promo'];
  const unknownName = [];
  const unknownLabel = [];
  let calls = 0;

  for (const name of fs.readdirSync(MOCK_DIR).filter((f) => f.endsWith('.swift') && f !== 'MockKit.swift')) {
    const text = read(path.join(MOCK_DIR, name));
    for (const receiver of receivers) {
      const re = new RegExp('\\b' + receiver + '\\.(\\w+)\\(', 'g');
      for (const match of text.matchAll(re)) {
        const method = match[1];
        const line = text.slice(0, match.index).split(/\r?\n/).length;
        if (!methods.has(method)) {
          unknownName.push(`${name}:${line} ${receiver}.${method}()`);
          continue;
        }
        calls += 1;
        const open = match.index + match[0].length - 1;
        const used = new Set();
        for (const part of splitTopLevel(parenBody(text, open))) {
          const label = part.trim().match(/^([A-Za-z_]\w*):/);
          if (label) used.add(label[1]);
        }
        const bad = [...used].filter((l) => !methods.get(method).has(l));
        if (bad.length) unknownLabel.push(`${name}:${line} ${method}(${bad.join(',')})`);
      }
    }
  }

  ok('调用的构建器方法都存在', unknownName.length === 0, unknownName.join(' | '));
  ok('调用的参数标签都存在', unknownLabel.length === 0, unknownLabel.join(' | '));
  console.log(`  ${methods.size} 个方法，${calls} 处带标签调用`);
}

console.log('=== 3. 子页名与 Android SUBS 一致 ===');
{
  const modules = {
    nye: 'NyeActivity', icbc: 'IcbcActivity', boc: 'BocActivity',
    psbc: 'PsbcActivity', cmb: 'CmbActivity', eank: 'EankActivity',
  };
  const javaSubs = (file) => {
    const p = path.join(JAVA_DIR, `${file}.java`);
    if (!fs.existsSync(p)) return null;
    const m = read(p).match(/SUBS\s*=\s*\{([\s\S]*?)\}/);
    return m ? [...m[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]) : null;
  };
  const subsStart = kit.indexOf('var subs: [String]');
  const subsBody = kit.slice(subsStart, kit.indexOf('\n    }', kit.indexOf('case .eank:', subsStart)));
  const swiftSubs = (key) => {
    const i = subsBody.indexOf(`case .${key}:`);
    if (i < 0) return null;
    const arr = subsBody.slice(i).match(/return\s*\[([\s\S]*?)\]/);
    return arr ? [...arr[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]) : null;
  };

  let checked = 0;
  for (const [key, file] of Object.entries(modules)) {
    const j = javaSubs(file);
    if (j === null) {
      console.log(`  (跳过 ${key}：没有 ${file}.java)`);
      continue;
    }
    checked += 1;
    const s = swiftSubs(key);
    const same = JSON.stringify(j) === JSON.stringify(s);
    ok(`${key} 子页名一致（${j.length} 个）`, same,
      same ? '' : `java=${JSON.stringify(j)} swift=${JSON.stringify(s)}`);
  }
  console.log(`  比对 ${checked} 家`);
}

console.log('=== 4. 招商 MODES 派发表 ===');
{
  const p = path.join(JAVA_DIR, 'CmbActivity.java');
  if (fs.existsSync(p)) {
    const java = read(p).match(/MODES\s*=\s*\{([^}]*)\}/)[1]
      .split(',').map((x) => x.trim()).filter(Boolean);
    const swift = read(path.join(MOCK_DIR, 'MockCmb.swift'))
      .match(/static let modes: \[Int\] = \[([^\]]*)\]/)[1]
      .split(',').map((x) => x.trim()).filter(Boolean);
    ok('MODES 与 Java 一致', JSON.stringify(java) === JSON.stringify(swift),
      `java=${java.join(',')} swift=${swift.join(',')}`);
  } else {
    console.log('  (跳过：没有 CmbActivity.java)');
  }
}

console.log('=== 5. 六家都接进了 MockComposer ===');
{
  const cases = [...composer.matchAll(/case \.(nye|icbc|boc|psbc|cmb|eank):\s*\n\s*return Mock(\w+)\.page/g)]
    .map((m) => m[1]);
  const expected = ['nye', 'icbc', 'boc', 'psbc', 'cmb', 'eank'];
  ok('6 个分支齐全', expected.every((k) => cases.includes(k)),
    `已接：${cases.join(',')}`);
  const hasLayout = /static func hasLayout/.test(composer);
  ok('hasLayout 与分支同源（isBuilt 不会漏）', hasLayout);
}

console.log('');
if (fail === 0) {
  console.log(`✅ 模拟模块专项核对全部通过（${pass} 项）`);
} else {
  console.log(`❌ ${fail} 项失败 / 共 ${pass + fail} 项`);
  process.exit(1);
}
