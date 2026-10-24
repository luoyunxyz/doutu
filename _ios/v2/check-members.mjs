/**
 * 静态成员访问检查（2026-10-17，第 92 轮）
 *
 *   node _ios/check-members.mjs
 *
 * 找的是 `Type.member`（**静态/类型级**访问）里，`member` 在 `Type` 上不存在的写法 ——
 * 编译器报 `type 'X' has no member 'y'`。
 *
 * 为什么值得做：本工程 64 个文件、742 个方法、1,900 多个 private 成员，全靠字符串式
 * 复制粘贴移植过来；一旦某个 `Self.xxx` / `XxxScreen.xxx` 里的名字拼错或改名漏改，
 * 只有编译器能发现。
 *
 * 已知不覆盖（会误报，所以跳过）：
 *   · 实例成员访问（`state.xxx` / `s.xxx`）—— 需要类型推导
 *   · 继承/协议扩展提供的成员 —— 这里把 `extension Screen` / `extension ScreenState`
 *     里声明的东西当成「谁都有」
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
  name: path.basename(full),
  text: fs.readFileSync(full, 'utf8'),
}));

// ---------- 收集每个类型声明的成员 ----------
const members = new Map();   // type -> Set(member)
const KNOWN_EXTENSIONS = new Set();   // 供 Screen / ScreenState 用的「谁都有」
const APPLE_TYPES = new Set([
  'UIImage', 'UIColor', 'UIFont', 'UIView', 'UIViewController', 'UIApplication', 'UIScreen',
  'UIAlertController', 'UIAlertAction', 'UILabel', 'UIWindow', 'UIWindowScene', 'CGPoint',
  'CGRect', 'CGSize', 'CGFloat', 'CGAffineTransform', 'CGContext', 'CTFont', 'CTLine',
  'String', 'Date', 'Calendar', 'UserDefaults', 'URL', 'URLSession', 'Data', 'Bundle',
  'NumberFormatter', 'DateFormatter', 'Locale', 'Timer', 'Notification', 'DispatchQueue',
  'AVCaptureVideoPreviewLayer', 'AVCaptureSession', 'JSONSerialization', 'SecKey',
  'UIGraphicsImageRenderer', 'UIBezierPath', 'NSAttributedString', 'AttributedString',
]);

function addMember(type, name) {
  if (!members.has(type)) members.set(type, new Set());
  members.get(type).add(name);
}

/** 去掉行尾注释、整行注释 */
function stripComment(line) {
  const trimmed = line.trimStart();
  if (trimmed.startsWith('//')) return '';
  const idx = line.indexOf('//');
  return idx >= 0 ? line.slice(0, idx) : line;
}

for (const file of files) {
  const lines = file.text.split('\n');
  let typeStack = [];
  let depth = 0;
  // 类型声明可能跨行（`final class WeChatV2Host: UIViewController,` 换行后才是 `{`），
  // 这时 line 上没有 `{`，按原来的逻辑整个类的成员都不会被登记，
  // 于是 `WeChatV2Host.xxx` 全被误报成「未声明」。这里先挂起，等遇到 `{` 再入栈。
  let pendingType = null;
  for (const rawLine of lines) {
    const line = stripComment(rawLine);
    const typeDecl = line.match(/(?:final\s+)?(?:class|struct|enum|extension)\s+([A-Z]\w*)/);
    const opens = (line.match(/\{/g) || []).length;
    const closes = (line.match(/\}/g) || []).length;
    // 嵌套类型（如 `State`）也算成员：`XxxScreen.State` 是合法访问
    if (typeStack.length > 0 && typeDecl) {
      addMember(typeStack[typeStack.length - 1].name, typeDecl[1]);
    }
    if (typeDecl && opens === 0) {
      pendingType = typeDecl[1];
    } else if (pendingType && opens > 0) {
      typeStack.push({ name: pendingType, depth: depth });
      if (!members.has(pendingType)) members.set(pendingType, new Set());
      pendingType = null;
    } else if (typeDecl && opens > 0) {
      typeStack.push({ name: typeDecl[1], depth: depth });
      if (!members.has(typeDecl[1])) members.set(typeDecl[1], new Set());
    }
    if (typeStack.length > 0) {
      const owner = typeStack[typeStack.length - 1].name;
      const vm = line.match(/^\s{1,16}(?:(?:public|private|fileprivate|internal|static|final|override|class|@\w+|@objc|lazy|weak|unowned)\s+)*(?:var|let|func|case|typealias)\s+([A-Za-z_]\w*)/);
      if (vm) {
        addMember(owner, vm[1]);
        if (owner === 'Screen' || owner === 'ScreenState') KNOWN_EXTENSIONS.add(vm[1]);
      }
      // `case a, b, c`
      const cm = line.match(/^\s{1,16}case\s+([a-zA-Z0-9_,\s]+)$/);
      if (cm) {
        for (const name of cm[1].split(',')) {
          const t = name.trim();
          if (/^[A-Za-z_]\w*$/.test(t)) addMember(owner, t);
        }
      }
    }
    depth += opens - closes;
    while (typeStack.length > 0 && depth <= typeStack[typeStack.length - 1].depth) typeStack.pop();
  }
}

// ---------- 找 `Type.member` 访问 ----------
const knownTypes = [...members.keys()].filter((t) => /^[A-Z]/.test(t) && !APPLE_TYPES.has(t));
const problems = [];
const seen = new Set();

for (const file of files) {
  // 逐行扫描并剔除注释，避免把注释里提到的文件名（`Session.java`）当成访问；
  // 同时跳过 `"""` 多行字符串块 —— 桥接脚本里的 `window.webkit.messageHandlers.DoutuApp.postMessage`
  // 是 JavaScript，不是 Swift 成员访问。
  const lines = file.text.split('\n');
  let inTripleString = false;
  lines.forEach((rawLine, index) => {
    const tripleCount = (rawLine.match(/"""/g) || []).length;
    if (inTripleString) {
      if (tripleCount % 2 === 1) inTripleString = false;
      return;
    }
    if (tripleCount % 2 === 1) {
      inTripleString = true;
      return;
    }
    const line = stripComment(rawLine);
    if (!line.trim()) return;
    for (const type of knownTypes) {
      const re = new RegExp('\\b' + type + '\\.([A-Za-z_]\\w*)', 'g');
      for (const m of line.matchAll(re)) {
        const member = m[1];
        if (members.get(type)?.has(member)) continue;
        if (KNOWN_EXTENSIONS.has(member)) continue;
        if (['init', 'self', 'Type', 'allCases', 'rawValue', 'hashValue'].includes(member)) continue;
        const key = type + '.' + member;
        if (seen.has(key)) continue;
        seen.add(key);
        problems.push({ file: file.name, line: index + 1, key });
      }
    }
  });
}

console.log(`  已知类型 ${knownTypes.length} 个；扫描 \`Type.member\` 访问`);
console.log('');
if (problems.length === 0) {
  console.log('  [OK]   没有访问到不存在的静态成员');
} else {
  console.log(`  [FAIL] ${problems.length} 处 \`Type.member\` 指向未声明的成员：`);
  for (const p of problems.slice(0, 40)) {
    console.log(`    ${p.key.padEnd(44)} ${p.file}:${p.line}`);
  }
  if (problems.length > 40) console.log(`    …共 ${problems.length} 处`);
}
console.log('');
console.log(`结果\n  通过 ${problems.length === 0 ? 1 : 0} 项，失败 ${problems.length} 项`);
process.exit(problems.length === 0 ? 0 : 1);
