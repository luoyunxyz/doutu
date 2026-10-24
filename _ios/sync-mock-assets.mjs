/**
 * 把 Android 的 `assets/mock_home/*` 搬进 iOS 的 `Assets.xcassets`（2026-10-24）。
 *
 *   node _ios/sync-mock-assets.mjs
 *
 * 「模拟」模块的切图不在 `res/drawable-*` 里，而是跟着醒图那套素材放在
 * `app/src/main/assets/mock_home/`（银行 logo、横幅、消费图标…）。
 * Android 用 `loadDrawable("mock_home/abc.png")` 取，iOS 这边统一加 `mock_` 前缀进资源目录，
 * 代码里写 `UIImage(named: "mock_abc")`。
 *
 * 一次全量同步（46 张），后面补别的银行模块时不用再挑名字。
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\assets\\mock_home';
const OUT = 'D:\\AAA\\doutu\\ios_v2\\Resources\\Assets.xcassets';
const PREFIX = 'mock_';

fs.mkdirSync(OUT, { recursive: true });

const files = fs.readdirSync(SRC).filter((name) => /\.(png|jpg|jpeg|webp)$/i.test(name));
let done = 0;

for (const name of files) {
  const ext = path.extname(name).toLowerCase();
  if (ext === '.webp') continue;   // iOS 不支持 webp 资源目录，本项目里也没有
  const base = path.basename(name, path.extname(name)).replace(/[^A-Za-z0-9_]/g, '_');
  const asset = PREFIX + base;
  const dir = path.join(OUT, `${asset}.imageset`);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  const file = `${asset}${ext}`;
  fs.copyFileSync(path.join(SRC, name), path.join(dir, file));
  const contents = {
    images: [{ idiom: 'universal', filename: file, scale: '1x' }],
    info: { author: 'xcode', version: 1 },
  };
  fs.writeFileSync(path.join(dir, 'Contents.json'), JSON.stringify(contents, null, 2) + '\n', 'utf8');
  done += 1;
}

console.log(`✅ 已同步 ${done} 个 mock_home 素材（${PREFIX}*）到 ${OUT}`);
