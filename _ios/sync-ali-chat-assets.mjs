/**
 * 把 Android「支付宝聊天页」的切图搬进 iOS（2026-10-24）。
 *
 *   node _ios/sync-ali-chat-assets.mjs
 *
 * 这些图在 `res/drawable-nodpi/`（不是 assets/），文件名本来就是 `ali_chat_*`，
 * 进资源目录时保持同名，代码里直接 `UIImage(named: "ali_chat_bottom")`。
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'D:/AAA/doutu/doutu/app/src/main/res/drawable-nodpi';
const OUT = 'ios_v2/Resources/Assets.xcassets';

fs.mkdirSync(OUT, { recursive: true });

const files = fs.existsSync(SRC)
  ? fs.readdirSync(SRC).filter((n) => /^ali_chat_.*\.png$/.test(n))
  : [];
let done = 0;
for (const name of files) {
  const asset = path.basename(name, '.png').replace(/[^A-Za-z0-9_]/g, '_');
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

console.log(`✅ 聊天页切图 ${done} 张 → ${OUT}/ali_chat_*`);
