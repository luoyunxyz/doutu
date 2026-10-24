/**
 * 把 Android「视频」模块的图标搬进 iOS 资源目录（2026-10-24）。
 *
 *   node _ios/sync-video-assets.mjs
 *
 * 源：`app/src/main/assets/video_icons/*.png`（抖音 dy_* / 快手 ks_* / 微视 ws_* / 评论 dc_*）
 * 目标：`Assets.xcassets/video_<名字>.imageset`（1x，原像素 —— 与参考画布 1080 宽同尺度）
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'doutu/app/src/main/assets/video_icons';
const OUT = 'ios_v2/Resources/Assets.xcassets';
const PREFIX = 'video_';

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

console.log(`✅ 视频图标 ${done} 张 → ${OUT}/${PREFIX}*`);
