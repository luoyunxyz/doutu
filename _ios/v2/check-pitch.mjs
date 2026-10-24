/**
 * 校验「行距模型」是否和安卓实测一致。
 *
 *   node _ios/v2/check-pitch.mjs
 *
 * 模型（第 5 轮定下来的）：
 *   行高 = 字号 × 1.2          （安卓 TextView 的行为，不是 PingFang 的 1.4）
 *   行距 = 字号 × 1.2 + 上下内边距 × 2
 *   1dp = 1sp = 3px（设备 480dpi，density 3）
 *
 * 数据来源：`_ios/measurements.json`（measure_page.py 从安卓截图量出来的）。
 * 容差 2px —— 超过就说明某一页的字号或内边距改偏了。
 */
import fs from 'node:fs';

const TOL = 2;
const DENSITY = 3;
const manifest = JSON.parse(fs.readFileSync('_ios/measurements.json', 'utf8'));

const pitch = (sizeSp, padVdp) => sizeSp * DENSITY * 1.2 + padVdp * DENSITY * 2;

let fails = 0;
console.log('页面            字号/内边距   算得    实测    差');
for (const m of manifest.measured) {
  const got = pitch(m.size, m.padV);
  const delta = got - m.pitch;
  const bad = Math.abs(delta) > TOL;
  if (bad) fails += 1;
  console.log(
    `${m.name.padEnd(14)} ${String(`${m.size}/${m.padV}`).padEnd(11)} ` +
      `${got.toFixed(1).padStart(6)} ${String(m.pitch).padStart(6)} ` +
      `${(delta >= 0 ? '+' : '') + delta.toFixed(1)}${bad ? '  ❌' : '  ✅'}`,
  );
  if (m.note) console.log(`${' '.repeat(14)} ↳ ${m.note}`);
}

// 底板色：确认 MockKit 里声明的三个灰和实测一致
const kit = fs.readFileSync('ios_v2/Sources/Mock/MockKit.swift', 'utf8');
const nim = (name) => {
  const m = kit.match(new RegExp(`static let ${name} = Draw\\.color\\(0xff([0-9a-fA-F]{6})\\)`));
  return m ? `#${m[1].toLowerCase()}` : null;
};
const want = { board: '#eeeeee', pageAlt: '#f5f5f5', entryGrey: '#f0f1f2' };
console.log('\n底板色           代码       实测');
for (const [name, hex] of Object.entries(want)) {
  const got = nim(name);
  const bad = got !== hex;
  if (bad) fails += 1;
  console.log(`${name.padEnd(16)} ${String(got).padEnd(10)} ${hex}${bad ? '  ❌' : '  ✅'}`);
}

if (fails) {
  console.log(`\n[FAIL] ${fails} 处与实测不符`);
  process.exit(1);
}
console.log('\n[OK] 行距模型与底板色全部对上实测值');
