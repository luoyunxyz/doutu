/**
 * 把 Android 的 drawable 位图搬到 iOS 的 Assets.xcassets（2026-10-17）。
 *
 *   node _ios/sync-assets.mjs [页面名…]
 *
 * 1:1 还原离不开这些切图（页面上的卡片、banner、图标都是位图）。Android 侧是
 * `res/drawable-xxhdpi/xxx.png`（基准密度 3x = xxhdpi），iOS 放进 imageset 的 3x 槽位，
 * 这样在 3x 屏上按参考画布坐标绘制时像素是 1:1 的。
 */
import fs from 'node:fs';
import path from 'node:path';

const RES = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\res';
const OUT = 'D:\\AAA\\doutu\\ios\\Resources\\Assets.xcassets';

/// 需要同步的资源名（页面里 `UIImage(named:)` 用的就是这个键）
const WANT = [
  'me_bgplate', 'me_backold', 'me_headset', 'me_gear', 'me_chevron', 'me_avatar', 'me_banner',
  'me_card1', 'me_card2', 'me_card3', 'me_card4', 'me_card5', 'me_card6', 'me_card7', 'me_card8',
  'me_nav_msg', 'me_nav_me', 'msg_navbar', 'hk_me_bg',
  ...process.argv.slice(2),
];

/** 密度目录优先级：本工程切图全部在 drawable-nodpi（原始像素，与参考画布 1:1 同尺度） */
const DENSITY = [
  ['drawable-nodpi', 1],
  ['drawable-xxhdpi', 1],
  ['drawable-xhdpi', 1],
  ['drawable', 1],
];

function findAsset(name) {
  for (const [dir, scale] of DENSITY) {
    for (const ext of ['png', 'webp', 'jpg']) {
      const p = path.join(RES, dir, `${name}.${ext}`);
      if (fs.existsSync(p)) return { path: p, scale };
    }
  }
  return null;
}

function writeImageset(name, srcPath, scale) {
  const dir = path.join(OUT, `${name}.imageset`);
  fs.mkdirSync(dir, { recursive: true });
  const ext = path.extname(srcPath);
  const file = `${name}${ext}`;
  fs.copyFileSync(srcPath, path.join(dir, file));
  const contents = {
    images: [{ idiom: 'universal', filename: file, scale: `${scale}x` }],
    info: { author: 'xcode', version: 1 },
  };
  fs.writeFileSync(path.join(dir, 'Contents.json'), JSON.stringify(contents, null, 2) + '\n', 'utf8');
  return file;
}

// 资源目录本身
fs.mkdirSync(OUT, { recursive: true });
const rootContents = { info: { author: 'xcode', version: 1 } };
fs.writeFileSync(path.join(OUT, 'Contents.json'), JSON.stringify(rootContents, null, 2) + '\n', 'utf8');

let done = 0;
const missing = [];
for (const name of [...new Set(WANT)]) {
  const hit = findAsset(name);
  if (!hit) { missing.push(name); continue; }
  writeImageset(name, hit.path, hit.scale);
  done += 1;
}

console.log(`✅ 已同步 ${done} 个素材到 Assets.xcassets`);
if (missing.length) {
  console.log(`⚠️ 找不到 ${missing.length} 个：`);
  for (const m of missing) console.log(`   ${m}`);
}
console.log(`   输出目录：${OUT}`);
