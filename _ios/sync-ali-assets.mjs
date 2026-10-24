/**
 * 把 Android「支付宝」模块的图标搬进 iOS（2026-10-24）。
 *
 *   node _ios/sync-ali-assets.mjs
 *
 * 源：`app/src/main/assets/ali_icons/*.png`（通讯录的返回/搜索/七个入口图标…）
 * 目标：`Assets.xcassets/ali_<名字>.imageset`
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'doutu/app/src/main/assets/ali_icons';
const OUT = 'ios_v2/Resources/Assets.xcassets';
const PREFIX = 'ali_';

fs.mkdirSync(OUT, { recursive: true });

const files = fs.existsSync(SRC) ? fs.readdirSync(SRC).filter((n) => n.endsWith('.png')) : [];
let done = 0;
for (const name of files) {
  const base = path.basename(name, '.png').replace(/[^A-Za-z0-9_]/g, '_');
  const asset = PREFIX + base;
  const dir = path.join(OUT, `${asset}.imageset`);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  fs.copyFileSync(path.join(SRC, name), path.join(dir, `${asset}.png`));
  fs.writeFileSync(path.join(dir, 'Contents.json'), JSON.stringify({
    images: [{ idiom: 'universal', filename: `${asset}.png`, scale: '1x' }],
    info: { author: 'xcode', version: 1 },
  }, null, 2) + '\n', 'utf8');
  done += 1;
}

console.log(`✅ 支付宝图标 ${done} 张 → ${OUT}/${PREFIX}*`);
