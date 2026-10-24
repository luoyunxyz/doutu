/**
 * 把 Android「工具」页的资源搬到 iOS（2026-10-24）。
 *
 *   node _ios/sync-tools-assets.mjs
 *
 * 搬两样：
 *   1. `assets/tools_icons/*.png`  → `Assets.xcassets/tool_<名字>.imageset`（1x，原像素）
 *   2. `assets/tools_page.json`    → `ios_v2/Resources/tools_page.json`（随包资源，运行时解析）
 *
 * 图标是从真机截图里逐张裁的，尺寸不统一；Android 侧按「不透明像素范围」居中缩放，
 * iOS 这边直接整张按 63% 槽位缩放（与 Android 的视觉尺寸一致）。
 */
import fs from 'node:fs';
import path from 'node:path';

const ICONS_SRC = 'doutu/app/src/main/assets/tools_icons';
const CATALOG_SRC = 'doutu/app/src/main/assets/tools_page.json';
const ASSETS_OUT = 'ios_v2/Resources/Assets.xcassets';
const CATALOG_OUT = 'ios_v2/Resources/tools_page.json';
const PREFIX = 'tool_';

fs.mkdirSync(ASSETS_OUT, { recursive: true });

const files = fs.existsSync(ICONS_SRC)
  ? fs.readdirSync(ICONS_SRC).filter((name) => name.endsWith('.png'))
  : [];

let done = 0;
for (const name of files) {
  const base = path.basename(name, '.png').replace(/[^A-Za-z0-9_]/g, '_');
  const asset = PREFIX + base;
  const dir = path.join(ASSETS_OUT, `${asset}.imageset`);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  fs.copyFileSync(path.join(ICONS_SRC, name), path.join(dir, `${asset}.png`));
  fs.writeFileSync(path.join(dir, 'Contents.json'), JSON.stringify({
    images: [{ idiom: 'universal', filename: `${asset}.png`, scale: '1x' }],
    info: { author: 'xcode', version: 1 },
  }, null, 2) + '\n', 'utf8');
  done += 1;
}

fs.copyFileSync(CATALOG_SRC, CATALOG_OUT);

const catalog = JSON.parse(fs.readFileSync(CATALOG_SRC, 'utf8'));
const items = catalog.reduce((sum, section) => sum + section.items.length, 0);
const missing = [];
for (const section of catalog) {
  for (const icon of [section.icon, ...section.items.map((i) => i.icon)]) {
    if (icon && !fs.existsSync(path.join(ASSETS_OUT, `${PREFIX}${icon}.imageset`))) {
      missing.push(icon);
    }
  }
}

console.log(`✅ 图标 ${done} 张 → ${ASSETS_OUT}/${PREFIX}*`);
console.log(`✅ 目录 ${catalog.length} 个分区 / ${items} 个入口 → ${CATALOG_OUT}`);
if (missing.length) {
  console.log(`⚠️ 目录里引用了但没找到图标：${[...new Set(missing)].join(', ')}`);
} else {
  console.log('✅ 目录引用的图标全部存在');
}
