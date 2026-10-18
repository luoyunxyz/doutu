/**
 * Swift 源码静态体检（2026-10-17）。
 *
 *   node _ios/lint-swift.mjs
 *
 * 为什么需要：本机没有 Swift 编译器（要 macOS），但很多错误不需要编译器就能发现 ——
 * 括号不配平、类型重名、非法标识符、明显的引号未闭合。这些正是「一次编译 50 个报错」的常见来源，
 * 提前挡住能省掉一轮云端 CI 往返。
 *
 * 它**不能**替代编译：类型错误、API 签名不匹配、访问控制等问题仍然只有编译器能发现。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = 'D:\\AAA\\doutu\\ios\\Sources';

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.swift')) out.push(full);
  }
  return out;
}

/** 去掉注释与字符串字面量，避免括号/引号误判 */
function strip(src) {
  let out = '';
  let i = 0;
  while (i < src.length) {
    const ch = src[i];
    const next = src[i + 1];
    if (ch === '/' && next === '/') {           // 行注释
      while (i < src.length && src[i] !== '\n') i += 1;
      continue;
    }
    if (ch === '/' && next === '*') {           // 块注释（支持嵌套）
      let depth = 0;
      while (i < src.length) {
        if (src[i] === '/' && src[i + 1] === '*') { depth += 1; i += 2; continue; }
        if (src[i] === '*' && src[i + 1] === '/') { depth -= 1; i += 2; if (depth === 0) break; continue; }
        i += 1;
      }
      continue;
    }
    if (ch === '"') {                            // 字符串（含三引号）
      const triple = src.slice(i, i + 3) === '"""';
      const quote = triple ? '"""' : '"';
      out += quote;
      i += quote.length;
      while (i < src.length) {
        if (src[i] === '\\') { out += ' '; i += 2; continue; }   // 转义
        if (src.slice(i, i + quote.length) === quote) { out += quote; i += quote.length; break; }
        if (!triple && src[i] === '\n') break;                    // 未闭合的单行字符串
        out += ' ';
        i += 1;
      }
      continue;
    }
    out += ch;
    i += 1;
  }
  return out;
}

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  ✅ ${name}`); }
  else { fail += 1; console.log(`  ❌ ${name} ${extra}`); }
};

const files = walk(ROOT);
console.log(`扫描 ${files.length} 个 Swift 文件\n`);

const types = new Map();
for (const file of files) {
  const rel = path.relative(ROOT, file).split(path.sep).join('/');
  const raw = fs.readFileSync(file, 'utf8');
  const src = strip(raw);

  // ① 括号配平
  const pairs = [['{', '}'], ['(', ')'], ['[', ']']];
  let balanced = true;
  for (const [open, close] of pairs) {
    const a = src.split(open).length - 1;
    const b = src.split(close).length - 1;
    if (a !== b) {
      balanced = false;
      ok(`${rel} 的 ${open}${close} 配平`, false, `${a} 个 ${open} vs ${b} 个 ${close}`);
    }
  }
  if (balanced) ok(`${rel} 括号配平`, true);

  // ② 括号嵌套顺序（粗查：不允许把 } 用在 { 之前闭合到负数）
  let depth = 0;
  let orderOk = true;
  for (const ch of src) {
    if (ch === '{') depth += 1;
    else if (ch === '}') { depth -= 1; if (depth < 0) { orderOk = false; break; } }
  }
  ok(`${rel} 花括号嵌套顺序`, orderOk && depth === 0, `结束时深度 ${depth}`);

  // ③ 引号是否都闭合（strip 后不应残留奇数个 "）
  const quotes = src.split('"').length - 1;
  ok(`${rel} 引号成对`, quotes % 2 === 0, `${quotes} 个引号`);

  // ④ 收集**顶层**类型名（缩进 0 才算顶层；嵌套在页面里的 State 之类不算冲突）查重
  for (const m of src.matchAll(/(?:^|\n)(?:public\s+|internal\s+|private\s+|fileprivate\s+|final\s+|open\s+)*(?:class|struct|enum|protocol|actor)\s+(\w+)/g)) {
    const name = m[1];
    if (!types.has(name)) types.set(name, []);
    types.get(name).push(rel);
  }
}

console.log('');
const dups = [...types.entries()].filter(([, v]) => v.length > 1);
ok('没有重名的类型定义', dups.length === 0,
  dups.map(([n, v]) => `${n} 出现在 ${v.join(' / ')}`).join('；'));
console.log(`  共定义 ${types.size} 个类型`);

// ⑤ 代码里引用的图片资源必须真的在 Assets.xcassets 里（漏图只会在运行时暴露）
{
  const assetsDir = 'D:\\AAA\\doutu\\ios\\Resources\\Assets.xcassets';
  const available = new Set(fs.readdirSync(assetsDir)
    .filter((n) => n.endsWith('.imageset'))
    .map((n) => n.replace(/\.imageset$/, '')));
  const referenced = new Set();
  for (const file of files) {
    const raw = fs.readFileSync(file, 'utf8');
    // 两种写法都要覆盖：UIImage(named: "x") 与 "key": "resourceName" 的映射表
    for (const m of raw.matchAll(/UIImage\(named:\s*"([^"]+)"/g)) referenced.add(m[1]);
    for (const m of raw.matchAll(/"[a-zA-Z]+":\s*"([a-z][a-z0-9_]+)"/g)) {
      if (available.has(m[1])) referenced.add(m[1]);
    }
  }
  const missing = [...referenced].filter((n) => !available.has(n));
  ok('引用的图片资源都存在', missing.length === 0, missing.join(', '));
  console.log(`  代码引用 ${referenced.size} 个资源，Assets 里有 ${available.size} 个`);
}

// ⑥ 检查关键类型是否齐全（链接别人代码时最容易漏）
for (const required of ['Screen', 'ScreenState', 'ScreenHostView', 'Canvas', 'Draw', 'Palette',
  'DtwCrypto', 'DtwSession', 'DtwTransport', 'DtwConfig', 'ApiClient', 'Session',
  'MeScreen', 'SwitchAccountScreen', 'HomeShellScreen', 'Playbook', 'BannerStore',
  'ConfigLoader', 'UpdatePrompt']) {
  ok(`类型 ${required} 已定义`, types.has(required));
}

// ⑦ 版本号三处必须一致：project.yml、DtwConfig.swift、服务端记录的 version_code
//    漏改任何一处 → 客户端永远认为「没有更新」或「已是最新」，是最隐蔽的发布事故。
{
  const yml = fs.readFileSync('D:\\AAA\\doutu\\ios\\project.yml', 'utf8');
  const cfg = fs.readFileSync('D:\\AAA\\doutu\\ios\\Sources\\Net\\DtwConfig.swift', 'utf8');
  const ymlVersion = (yml.match(/MARKETING_VERSION:\s*"([^"]+)"/) || [])[1];
  const ymlCode = (yml.match(/CURRENT_PROJECT_VERSION:\s*"(\d+)"/) || [])[1];
  const cfgVersion = (cfg.match(/appVersion\s*=\s*"([^"]+)"/) || [])[1];
  const cfgCode = (cfg.match(/versionCode\s*=\s*(\d+)/) || [])[1];
  ok('project.yml 与 DtwConfig 的版本名一致', ymlVersion === cfgVersion, `${ymlVersion} vs ${cfgVersion}`);
  ok('project.yml 与 DtwConfig 的版本编码一致', ymlCode === cfgCode, `${ymlCode} vs ${cfgCode}`);
  // 编码公式：major*10000 + minor*100 + patch
  if (ymlVersion && ymlCode) {
    const [maj, min, pat] = ymlVersion.split('.').map(Number);
    const expected = maj * 10000 + min * 100 + pat;
    ok('版本编码符合 major*10000+minor*100+patch', Number(ymlCode) === expected,
      `${ymlVersion} 应为 ${expected}，实际 ${ymlCode}`);
  }
  console.log(`  版本：${ymlVersion}（编码 ${ymlCode}）`);
}

// ⑧ 页面 hitTest 返回的每个动作令牌，Router 里必须有对应分支
//    漏一个的表现是「这一点没反应」，而静态体检以外没有任何地方能发现。
{
  const routerPath = 'D:\\AAA\\doutu\\ios\\Sources\\App\\DoutuApp.swift';
  const router = fs.readFileSync(routerPath, 'utf8');
  const tokens = new Set();
  for (const file of files) {
    if (!file.includes('Screens')) continue;
    const raw = fs.readFileSync(file, 'utf8');
    // ① hitTest 里的 return 字面量；带插值的（如 "play:\(key)"）截成前缀
    for (const m of raw.matchAll(/return\s+"([a-zA-Z:][^"\\]*)"/g)) {
      const token = m[1];
      if (token.includes(' ')) continue;         // 带空格的提示文案不是令牌
      // 蛇形命名的都是**素材名**（`zhm_card_personal`、`xiaohebao_head`…），不是动作令牌
      if (/^[a-z0-9]+(_[a-z0-9]+)+$/.test(token)) continue;
      tokens.add(token.includes('\\(') ? token.split('\\(')[0] : token);
    }
    // ② 通过热区表登记的令牌：`hit(rect, "token")`、`Hit(rect: ..., token: "token")`
    //
    // ⚠️ 不能用一个简单的 `hit\([^)]*?,\s*"…"\)` 正则：参数里常常带括号，
    // 例如 `hit(eyeRect.insetBy(dx: -14, dy: -14), "eye")` —— `[^)]*?` 会在 `insetBy(`
    // 的第一个 `)` 就停住，于是整条注册被漏掉。2026-10-17 修：改成**配平括号扫描**，
    // 取出 `hit(` 之后与之匹配的 `)` 之间的全部内容，再从里面挑引号字符串。
    for (let idx = raw.indexOf('hit('); idx >= 0; idx = raw.indexOf('hit(', idx + 1)) {
      // 跳过 `hits.append(` / `submit(` 之类
      const before = raw.slice(Math.max(0, idx - 6), idx);
      if (/[A-Za-z_.]/.test(before.slice(-1))) continue;
      let depth = 0;
      let end = -1;
      for (let i = idx + 3; i < raw.length; i += 1) {
        if (raw[i] === '(') depth += 1;
        else if (raw[i] === ')') {
          depth -= 1;
          if (depth === 0) { end = i; break; }
        }
      }
      if (end < 0) continue;
      const args = raw.slice(idx + 4, end);
      // 最后一个字符串字面量就是令牌
      const strings = [...args.matchAll(/"([a-zA-Z:][^"\\]*)"/g)].map((m) => m[1]);
      const token = strings[strings.length - 1];
      if (!token) continue;
      tokens.add(token.includes('\\(') ? token.split('\\(')[0] : token);
    }
    for (const m of raw.matchAll(/token:\s*"([a-zA-Z:][^"\\]*)"/g)) {
      tokens.add(m[1].includes('\\(') ? m[1].split('\\(')[0] : m[1]);
    }
    // ③ 形如 `return "e.label"` 的是**字段名片段**，真正发出去的令牌是 `"edit:" + 字段名`
    //    （Router 已用 hasPrefix("edit:") 覆盖），这里不算缺口
    if (raw.includes('"edit:" +')) {
      for (const token of [...tokens]) {
        if (token.includes('.') && !token.includes(':')) tokens.delete(token);
      }
    }
    // ④ 任何 `"前缀:" + 变量` 的拼接，只留下「前缀:」这个锚点就够了 ——
    //    它本身不是一个独立令牌（如 `"key:" + digit`、`"pin:" + digit`、`"row:" + i`）
    for (const m of raw.matchAll(/"([a-zA-Z]+:)"\s*\+/g)) {
      tokens.add(m[1]);
    }
  }
  const missing = [];
  for (const token of tokens) {
    // MIME 类型等资源字符串也长得像令牌（`image/png`、`application/octet-stream`），
    // 但 Router 里永远不会有分支；带斜杠/点的一律跳过。
    if (token.includes('/') || token.includes('.')) continue;
    if (router.includes(`"${token}"`)) continue;
    // 带冒号的令牌按前缀匹配（Router 里写的是 `hasPrefix("play:")` 或 `"tab:0", "tab:1"`）
    const colon = token.indexOf(':');
    const prefix = colon >= 0 ? token.slice(0, colon + 1) : token;
    if (router.includes(`hasPrefix("${prefix}")`)) continue;
    // 形如 tab:0/tab:1/tab:2 这类逐条列举的，检查同前缀的任一条出现过
    if (colon >= 0) {
      const re = new RegExp(`"${prefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\d"`);
      if (re.test(router)) continue;
    }
    missing.push(token);
  }
  ok('hitTest 的令牌都在 Router 里有分支', missing.length === 0, missing.join(', '));
  console.log(`  页面共产生 ${tokens.size} 种动作令牌`);
}

console.log(`\n结果\n  通过 ${pass} 项，失败 ${fail} 项`);
console.log('  注意：这只是静态体检，类型/API 层面的错误仍需云端 CI 编译发现。');
process.exit(fail === 0 ? 0 : 1);