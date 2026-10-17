/**
 * 两类「Swift 编译器一定报错」的检查（2026-10-17，第 91 轮；第 92 轮修正）
 *
 *   node _ios/check-access.mjs
 *
 * ① 同一个类型里**重复声明同名同签名的成员** —— 编译器报 `invalid redeclaration of 'x'`。
 *    要点：必须把**所属类型**和**参数标签**都算进签名，否则重载与「不同 State 类型各自
 *    实现同一个协议方法」会被误判（第 91 轮就误报了 7 处）。
 *
 * ② `private` / `fileprivate` 成员被**其它文件**用 `Type.member` 直接访问 ——
 *    编译器报 `inaccessible due to 'private' protection level`。
 *    Swift 的 `private` 是「文件内可见」而不是「类型内可见」，本工程大量把同一个类型
 *    拆到多个文件，一旦跨文件用 private 就编不过。
 *
 *    注意：不能拿 `Self.member` 当证据 —— 别处的 `Self.x` 指的是**那个文件自己的类型**，
 *    与这里的 private 成员无关（第 91 轮因此误报了 30 多处）。
 */
import fs from 'node:fs';
import path from 'node:path';

const DIR = 'D:\\AAA\\doutu\\ios\\Sources';

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

// ---------- ① 重复成员（按「类型 + 方法名 + 参数标签」判定）----------

/** 从 `func name(_ a: T, b: U)` 里取参数标签串，如 `_:b:` */
function paramLabels(params) {
  const inner = params.replace(/^\(|\)$/g, '');
  if (!inner.trim()) return '';
  // 按顶层逗号切
  const parts = [];
  let depth = 0;
  let buf = '';
  for (const ch of inner) {
    if ('([<{'.includes(ch)) depth += 1;
    if (')]>}'.includes(ch)) depth -= 1;
    if (ch === ',' && depth === 0) { parts.push(buf); buf = ''; } else buf += ch;
  }
  if (buf.trim()) parts.push(buf);
  return parts
    .map((p) => {
      const before = p.split(':')[0].trim();       // `_ key` / `key` / `x`
      const words = before.split(/\s+/);
      // `_ key` → 标签是 `_`；`key` → 标签是 `key`（省略外部名=同名）
      return words.length > 1 ? words[0] : words[0];
    })
    .join(':');
}

const declarations = []; // { type, sig, file, line }
for (const file of files) {
  const lines = file.text.split('\n');
  let typeStack = [];
  let depth = 0;
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const typeDecl = line.match(/(?:final\s+)?(?:class|struct|enum|extension)\s+([A-Z]\w*)/);
    const opens = (line.match(/\{/g) || []).length;
    const closes = (line.match(/\}/g) || []).length;

    if (typeDecl && opens > 0) {
      typeStack.push({ name: typeDecl[1], depth: depth });
    }
    const fn = line.match(/^\s{0,16}(?:(?:public|private|fileprivate|internal|static|final|override|@\w+|@objc)\s+)*func\s+([A-Za-z_]\w*)/);
    if (fn && typeStack.length > 0) {
      // 参数表可能跨行 —— 从声明处开始配平括号，拿到完整的 `(...)` 再提取标签。
      // 不做这一步的话，`Draw.text` 的两个重载会被看成同一个签名（误报）。
      let text = line;
      let parenDepth = (text.match(/\(/g) || []).length - (text.match(/\)/g) || []).length;
      let extra = i;
      while (parenDepth > 0 && extra + 1 < lines.length) {
        extra += 1;
        text += ' ' + lines[extra];
        parenDepth = (text.match(/\(/g) || []).length - (text.match(/\)/g) || []).length;
      }
      const params = text.match(/func\s+[A-Za-z_]\w*\s*(\([\s\S]*?\))\s*(?:->|\{|$|where)/);
      const owner = typeStack[typeStack.length - 1].name;
      declarations.push({
        type: owner,
        sig: fn[1] + '(' + paramLabels(params ? params[1] : '()') + ')',
        file: file.name,
        line: i + 1,
      });
    }
    depth += opens - closes;
    while (typeStack.length > 0 && depth <= typeStack[typeStack.length - 1].depth) {
      typeStack.pop();
    }
  }
}

const byKey = new Map();
for (const d of declarations) {
  const key = d.type + '|' + d.sig;
  if (!byKey.has(key)) byKey.set(key, []);
  byKey.get(key).push(d);
}
const duplicates = [...byKey.entries()].filter(([, list]) => list.length > 1);

// ---------- ② private 成员被其它文件以 `Type.member` 访问 ----------

const privateMembers = [];
for (const file of files) {
  const lines = file.text.split('\n');
  let typeStack = [];
  let depth = 0;
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const typeDecl = line.match(/(?:final\s+)?(?:class|struct|enum|extension)\s+([A-Z]\w*)/);
    const opens = (line.match(/\{/g) || []).length;
    const closes = (line.match(/\}/g) || []).length;
    if (typeDecl && opens > 0) typeStack.push({ name: typeDecl[1], depth: depth });

    const member = line.match(/^\s{1,16}(?:private|fileprivate)\s+(?:static\s+)?(?:let|var|func)\s+([A-Za-z_]\w*)/);
    if (member && typeStack.length > 0) {
      privateMembers.push({
        member: member[1],
        type: typeStack[typeStack.length - 1].name,
        file: file.name,
        line: i + 1,
      });
    }
    depth += opens - closes;
    while (typeStack.length > 0 && depth <= typeStack[typeStack.length - 1].depth) {
      typeStack.pop();
    }
  }
}

const crossFile = [];
for (const pm of privateMembers) {
  const re = new RegExp('\\b' + pm.type + '\\.' + pm.member + '\\b');
  for (const file of files) {
    if (file.name === pm.file) continue;
    if (re.test(file.text)) crossFile.push({ ...pm, usedIn: file.name });
  }
}

// ---------- 输出 ----------
console.log(`  扫描 ${files.length} 个文件 / ${declarations.length} 个方法声明 / ${privateMembers.length} 个 private 成员`);
console.log('');

if (duplicates.length === 0) {
  console.log('  [OK]   没有同类型、同签名的重复成员');
} else {
  console.log(`  [FAIL] ${duplicates.length} 处重复（编译报 invalid redeclaration）：`);
  for (const [key, list] of duplicates) {
    console.log(`    ${key.split('|')[0]}.${key.split('|')[1]}`);
    for (const d of list) console.log(`        ${d.file}:${d.line}`);
  }
}

console.log('');
if (crossFile.length === 0) {
  console.log('  [OK]   没有 private 成员被其它文件以 Type.member 访问');
} else {
  console.log(`  [FAIL] ${crossFile.length} 处 private 跨文件访问（编译报 inaccessible）：`);
  for (const c of crossFile) {
    console.log(`    ${c.type}.${c.member}  private@${c.file}:${c.line}  →  ${c.usedIn}`);
  }
}

console.log('');
const fail = duplicates.length + crossFile.length;
console.log(`结果\n  通过 ${fail === 0 ? 2 : 0} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
