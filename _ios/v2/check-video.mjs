/**
 * 「视频」模块专项核对（2026-10-24）。
 *
 *   node _ios/v2/check-video.mjs
 *
 * 盯三件容易错、编译器不拦、装到手机上才发现的事：
 *   1. 代码里 `VideoKit.icon("x")` 引用的图，资源目录里真的有 `video_x`；
 *   2. 三个页面在 `hitTest` 里发出的 `video:field:<key>`，对应 `State.fieldInfo` 都认识
 *      （漏了就会「点了没反应」）；
 *   3. 工具页 `video` 分区的入口在 Router 里都有分支（不许静默吞掉）。
 */
import fs from 'node:fs';
import path from 'node:path';

const VIDEO_DIR = 'ios_v2/Sources/Video';
const ASSETS = 'ios_v2/Resources/Assets.xcassets';
const ROUTER = 'ios_v2/Sources/App/LegacyDoutuApp.swift';

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  [OK]   ${name}`); }
  else { fail += 1; console.log(`  [FAIL] ${name} ${extra}`); }
};

const screens = fs.readdirSync(VIDEO_DIR).filter((f) => f.endsWith('.swift'));
const code = Object.fromEntries(screens.map((f) => [f, fs.readFileSync(path.join(VIDEO_DIR, f), 'utf8')]));

console.log('=== 1. 图标引用 ===');
{
  const used = new Set();
  for (const text of Object.values(code)) {
    for (const m of text.matchAll(/icon\("([A-Za-z0-9_]+)"\)/g)) used.add(m[1]);
    // 三元表达式里的字面量（liked ? "dy_heart_red" : "dy_heart"）也要抓
    for (const m of text.matchAll(/icon\([^)]*\?\s*"([A-Za-z0-9_]+)"\s*:\s*"([A-Za-z0-9_]+)"\)/g)) {
      used.add(m[1]); used.add(m[2]);
    }
  }
  const have = new Set(fs.readdirSync(ASSETS)
    .filter((n) => n.startsWith('video_') && n.endsWith('.imageset'))
    .map((n) => n.replace(/\.imageset$/, '')));
  const missing = [...used].filter((k) => !have.has('video_' + k));
  ok(`引用的 ${used.size} 个图标都在资源里`, missing.length === 0,
    missing.map((m) => 'video_' + m).join(', '));
}

console.log('=== 2. 可编辑字段：hitTest 发出的 key 都被 fieldInfo 认识 ===');
{
  for (const [file, text] of Object.entries(code)) {
    if (!text.includes('fieldInfo')) continue;
    const sent = new Set([...text.matchAll(/"video:field:([A-Za-z0-9_]+)"/g)].map((m) => m[1]));
    const handled = new Set([...text.matchAll(/case "([A-Za-z0-9_]+)": return \("/g)].map((m) => m[1]));
    const orphan = [...sent].filter((k) => !handled.has(k));
    ok(`${file} 的字段键闭合（发出 ${sent.size} / 处理 ${handled.size}）`,
      orphan.length === 0, orphan.join(', '));
  }
}

console.log('=== 3. 工具页「视频」入口都有落点 ===');
{
  const router = fs.readFileSync(ROUTER, 'utf8');
  for (const action of ['videohome', 'kuaishou', 'weishi']) {
    ok(`tools 动作 ${action} 已接线`, router.includes(`case "${action}"`));
  }
  ok('Router 有 video: 令牌分支', router.includes('hasPrefix("video:")'));
}

console.log('');
if (fail === 0) {
  console.log(`✅ 视频模块专项核对全部通过（${pass} 项）`);
} else {
  console.log(`❌ ${fail} 项失败 / 共 ${pass + fail} 项`);
  process.exit(1);
}
