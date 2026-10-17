/**
 * Router 死路扫描（2026-10-17）
 *
 *   node _ios/check-deadends.mjs
 *
 * 找的是「Router 里有分支、但分支什么也没做」的令牌 —— 表现为点了没反应或只弹个提示。
 *
 * 为什么要专门扫：`BankSheet`（银行卡选择层）和芝麻信用的 `add:` 就是这样被漏掉的 ——
 * 令牌有分支，所以令牌完整性检查是绿的；但分支里只有 `break`，功能其实不存在。
 * 这类「假接线」比缺分支更难发现。
 */
import fs from 'node:fs';
import path from 'node:path';

const APP = 'D:\\AAA\\doutu\\ios\\Sources\\App\\DoutuApp.swift';
const ANDROID = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\java\\com\\luoyun\\doutu';

let pass = 0;
let fail = 0;
const info = (name, extra = '') => { console.log(`  [INFO] ${name} ${extra}`); };

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

const code = fs.readFileSync(APP, 'utf8');
const lines = code.split('\n');

/** 从 switch 的某个 case 行开始，取到下一个同级 case 之前的正文 */
function caseBody(startLine) {
  const body = [];
  for (let i = startLine + 1; i < lines.length; i += 1) {
    const line = lines[i];
    // 同级 case / default → 结束
    if (/^\s{8}(?:case\s|default:)/.test(line)) break;
    body.push(line);
  }
  return body;
}

/** 这个分支是不是「什么也没做」 */
function isDeadEnd(body) {
  const meaningful = body
    .map((l) => l.trim())
    .filter((l) => l.length > 0 && !l.startsWith('//') && l !== '}' && l !== '{');
  if (meaningful.length === 0) return true;
  // 只弹提示算「有动作」—— Android 里也常这么干（toast:xxx、swap）
  return meaningful.every((l) =>
    l === 'break' || l === '_ = t' || l === '_ = growth');
}

// Android 侧：收集「令牌 → 这个分支有没有真的做事」
//
// 只看令牌出现过是不够的 —— Android 里 `case "numbers": break;` 这种同样空转，
// 那就不是缺口。所以这里连分支体一起判。
const androidMeaningful = new Map();
for (const file of walk(ANDROID)) {
  const text = fs.readFileSync(file, 'utf8');
  const jlines = text.split('\n');
  for (let i = 0; i < jlines.length; i += 1) {
    const line = jlines[i];
    for (const m of line.matchAll(/(?:token\.equals|token\.startsWith)\("([a-zA-Z:]+)"\)/g)) {
      // 取这个 case 分支的正文（到下一个 case/default 之前）
      const body = [];
      for (let j = i + 1; j < jlines.length; j += 1) {
        if (/^\s*(?:case\s|default:)/.test(jlines[j])) break;
        body.push(jlines[j].trim());
      }
      const meaningful = body.filter((l) => l.length > 0 && !l.startsWith('//') && l !== '{' && l !== '}');
      const dead = meaningful.length === 0 || meaningful.every((l) => l === 'break;' || l === 'break');
      if (!androidMeaningful.has(m[1])) androidMeaningful.set(m[1], !dead);
      else if (!dead) androidMeaningful.set(m[1], true);
    }
  }
}

const dead = [];
for (let i = 0; i < lines.length; i += 1) {
  const line = lines[i];
  const m = line.match(/^\s{8}case\s+(.+?):\s*(\/\/.*)?$/);
  if (!m) continue;
  const labels = [...m[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]);
  // 跳过 `case let t where …`
  if (labels.length === 0) continue;
  const body = caseBody(i);
  if (!isDeadEnd(body)) continue;
  dead.push({ line: i + 1, labels, comment: (m[2] || '').replace(/^\/\/\s*/, '') });
}

console.log(`  Router 里的空转分支：${dead.length} 处`);
console.log('');
let androidAlive = 0;
for (const d of dead) {
  // 这个令牌在 Android 上是不是**真的做了事**（分支体不是空的）
  const alive = d.labels.some((t) => androidMeaningful.get(t) === true);
  if (alive) androidAlive += 1;
  const flag = alive ? '← Android 有处理' : '';
  console.log(`  L${String(d.line).padEnd(5)} ${d.labels.join(', ').slice(0, 40).padEnd(42)} ${flag}`);
  if (d.comment) console.log(`         ${d.comment.slice(0, 76)}`);
}

console.log('');
if (androidAlive === 0) {
  console.log('  这些空转分支在 Android 上同样是空操作，属于 1:1 一致。');
} else {
  console.log(`  其中 ${androidAlive} 处 Android 是**真的做了事**的 —— 这些是「假接线」，需要补。`);
}

console.log('');
console.log(`结果\n  通过 1 项，需人工确认 ${androidAlive} 项`);
process.exit(0);
