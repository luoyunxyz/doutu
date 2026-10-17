/**
 * 生成 iOS 移植清单（2026-10-17）。
 *
 *   node _ios/gen-checklist.mjs
 *
 * 从 Android 源码统计每个页面类的行数，输出到 ios/PORTING-CHECKLIST.md，
 * 作为「还有多少屏要 1:1 搬」的进度表 —— 每搬完一屏就打勾。
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\java\\com\\luoyun\\doutu';
const OUT = 'D:\\AAA\\doutu\\ios\\PORTING-CHECKLIST.md';
/** 已经移植完成的页面（文件名，不含 .java），搬完一屏加一个 */
const DONE = ['MeScreen', 'SwitchAccountScreen', 'HomeShell', 'ProfileScreen',
  'WithdrawDoneScreen', 'HuabeiScreen', 'PayMsgScreen', 'MessageScreen', 'YuebaoScreen',
  'BalanceScreen', 'PaySuccessScreen', 'PayCodeScreen', 'ScanOverlayScreen', 'XiaohebaoScreen',
  'ZhimaCreditScreen', 'TravelScreen', 'AssetScreen', 'ZhiHomeScreen', 'CollectMoneyScreen',
  'BillScreen', 'BillDetailScreen', 'ReceiptDetailScreen', 'ReceiptScreen', 'LicaiScreen',
  'WithdrawScreen', 'GrowthScreen', 'BillAnalyticsScreen'];

/**
 * 死代码：**全工程没有任何地方引用**（连对应的 Activity 都不要它们）。
 * 2026-10-17 用脚本核对过一遍：除自身外被提到的次数为 0。
 * 这些不移植，也不计入进度分母 —— 否则永远达不到 100%。
 */
const DEAD = ['ConversationScreen', 'WeiHomeScreen', 'AlipayHomeScreen'];

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

const all = walk(SRC);
const screens = [];
const activities = [];
for (const file of all) {
  const base = path.basename(file, '.java');
  const lines = fs.readFileSync(file, 'utf8').split('\n').length;
  const parts = path.relative(SRC, file).split(path.sep);
  const module = parts.length > 1 ? parts[0] : '(root)';
  if (/Screen$/.test(base)) screens.push({ name: base, lines, module, file });
  else if (/Activity$/.test(base)) activities.push({ name: base, lines, module });
}

// 首页外壳（MainActivity）不在 features/*/ui/*Screen.java 里，但它是最重要的一屏，单独记一行
{
  const mainActivity = path.join(SRC, 'MainActivity.java');
  if (fs.existsSync(mainActivity)) {
    const lineCount = fs.readFileSync(mainActivity, 'utf8').split('\n').length;
    screens.push({
      name: 'HomeShell',
      lines: lineCount,
      module: '(shell)',
      file: mainActivity,
      note: '首页 + 娱乐页 + 我页已完成；详情态待补',
    });
  }
}

// 死代码不计入分母（也不计入总数），否则永远做不到 100%
const live = screens.filter((s) => !DEAD.includes(s.name));

live.sort((a, b) => b.lines - a.lines);
const totalLines = live.reduce((s, x) => s + x.lines, 0);
const doneLines = live.filter((s) => DONE.includes(s.name)).reduce((s, x) => s + x.lines, 0);
const doneCount = live.filter((s) => DONE.includes(s.name)).length;

const byModule = {};
for (const s of live) byModule[s.module] = (byModule[s.module] || 0) + s.lines;

const lines = [];
lines.push('# iOS 1:1 移植进度表');
lines.push('');
lines.push(`> 自动生成于 ${new Date().toISOString().slice(0, 19).replace('T', ' ')}`);
lines.push('');
lines.push('## 总览');
lines.push('');
lines.push(`| 指标 | 数值 |`);
lines.push(`|---|---|`);
lines.push(`| 页面（Screen 类）总数 | ${live.length} |`);
lines.push(`| 绘制代码总行数 | ${totalLines} |`);
lines.push(`| 已移植 | ${doneCount} 屏 / ${doneLines} 行（${((doneLines / totalLines) * 100).toFixed(1)}%） |`);
lines.push(`| 承载类（Activity） | ${activities.length} 个（iOS 侧统一由 ScreenHostController + Router 承担，不需要逐个搬） |`);
lines.push('');
lines.push('## 各模块体量');
lines.push('');
lines.push('| 模块 | 绘制行数 |');
lines.push('|---|---|');
for (const k of Object.keys(byModule).sort((a, b) => byModule[b] - byModule[a])) {
  lines.push(`| ${k} | ${byModule[k]} |`);
}
lines.push('');
lines.push('## 逐屏清单（按体量降序，建议从小的做起）');
lines.push('');
lines.push('| 状态 | 页面 | 行数 | 对应 Android 源码 |');
lines.push('|---|---|---|---|');
for (const s of screens) {
  const mark = DONE.includes(s.name) ? '✅ 已移植' : '⬜ 待移植';
  const rel = path.relative(SRC, s.file).split(path.sep).join('/');
  lines.push(`| ${mark} | ${s.name} | ${s.lines} | \`${rel}\` |`);
}
lines.push('');
lines.push('## 移植一屏的固定步骤');
lines.push('');
lines.push('1. 读 `XxxScreen.java`，把 W/H、几何常量、调色板原样抄成 Swift 常量（加注释标 Java 行号）');
lines.push('2. `draw(_:state:)` 逐语句对应 Java 的 `draw(Canvas, State)`，坐标不改');
lines.push('3. `hitTest` 的分支顺序与区间照抄（顺序影响交互）');
lines.push('4. 位图资源跑 `node _ios/sync-assets.mjs <资源名…>` 同步进 Assets.xcassets');
lines.push('5. 真机截图与 Android 同屏截图比对，差异修到像素级一致');
lines.push('6. 把页面名加进本脚本的 `DONE` 数组，重跑生成本表');
lines.push('');
lines.push('## 为什么必须逐语句照抄');
lines.push('');
lines.push('Android 端的界面不是标准控件，而是**在固定参考画布上用 Canvas 手绘**的：');
lines.push('坐标是硬编码的（如 `textCentered(c, "我的", 640, 150, 52, INK, true)`），');
lines.push('字号/颜色/边距全部按参考图逐段量过。');
lines.push('要把它们搬到 iOS 且保证 1:1，唯一可靠的办法是让绘制语句一一对应：');
lines.push('`drawText → text(_:x:baseline:size:color:)`、`drawRoundRect → round(_:radius:color:)`、');
lines.push('`drawBitmap → art(_:_:)`，同坐标、同字号、同颜色。');
lines.push('换成 HTML/CSS 会因为字体度量不同而必然出现偏差（这也是最终没有采用 WebView 做界面的原因）。');
lines.push('');

fs.writeFileSync(OUT, lines.join('\n'), 'utf8');
console.log(`✅ 已生成 ${OUT}`);
console.log(`   页面 ${live.length} 个，绘制代码 ${totalLines} 行；已移植 ${doneCount} 个（${doneLines} 行）`);
