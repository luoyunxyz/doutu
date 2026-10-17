/**
 * 从 Android 源码自动生成 iOS 设计令牌（2026-10-17）。
 *
 *   node _ios/gen-theme.mjs
 *
 * 为什么自动生成：1:1 还原最怕「手抄错一个色值」。颜色的唯一真源是 Java 里的常量，
 * 直接扫出来生成 Swift，比人手转录可靠，而且以后 Android 改了重跑一次就同步。
 *
 * 产出：ios/Sources/Design/Theme.swift
 */
import fs from 'node:fs';
import path from 'node:path';

const SRC = 'D:\\AAA\\doutu\\doutu\\app\\src\\main\\java\\com\\luoyun\\doutu';
const OUT = 'D:\\AAA\\doutu\\ios\\Sources\\Design\\Theme.swift';

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith('.java')) out.push(full);
  }
  return out;
}

const colors = new Map();
for (const file of walk(SRC)) {
  const src = fs.readFileSync(file, 'utf8');
  for (const m of src.matchAll(/(?:static final|final static)\s+int\s+([A-Z][A-Z0-9_]*)\s*=\s*(0x[0-9A-Fa-f]{8})/g)) {
    const [, name, hex] = m;
    // 同名不同值：保留第一次出现的（模块内常量名重复是常见的）
    if (!colors.has(name)) colors.set(name, hex);
  }
}

const entries = [...colors.entries()].sort((a, b) => a[0].localeCompare(b[0]));

/** 0xAARRGGBB → Swift 的 (r,g,b,a) 字面量 */
function toSwift(hex) {
  const v = BigInt(hex);
  const a = Number((v >> 24n) & 0xffn) / 255;
  const r = Number((v >> 16n) & 0xffn);
  const g = Number((v >> 8n) & 0xffn);
  const b = Number(v & 0xffn);
  return `UIColor(red: ${r}/255, green: ${g}/255, blue: ${b}/255, alpha: ${a.toFixed(4).replace(/0+$/, '').replace(/\.$/, '.0')})`;
}

const lines = [];
lines.push('// 本文件由 _ios/gen-theme.mjs 从 Android 源码自动生成，请勿手改。');
lines.push(`// 生成时间：${new Date().toISOString()}`);
lines.push(`// 来源：${SRC}`);
lines.push(`// 颜色常量 ${entries.length} 个 —— 这些就是 1:1 的字面依据（Java 里的 private static final int）。`);
lines.push('');
lines.push('import UIKit');
lines.push('');
lines.push('/// 从 Android `0xAARRGGBB` 常量自动镜像过来的调色板。');
lines.push('/// 命名与 Java 完全一致，便于逐屏比对时一眼对上源码。');
lines.push('enum Palette {');
for (const [name, hex] of entries) {
  lines.push(`    /// ${hex}`);
  lines.push(`    static let ${camel(name)} = ${toSwift(hex)}`);
}
lines.push('}');
lines.push('');
lines.push('/// 参考画布尺寸：各模块并不统一，Android 源码里是常量 W/H，这里按模块登记。');
lines.push('/// ⚠️ 类型名不能叫 `Canvas` —— `Design/Canvas.swift` 里的 `Canvas` 是用于坐标换算的核心结构体，');
lines.push('/// 同名会直接编译冲突（静态体检脚本 lint-swift.mjs 抓过一次）。');
lines.push('enum ReferenceCanvas {');
lines.push('    /// MainActivity（首页/娱乐/我 三栏外壳）');
lines.push('    static let main = CGSize(width: 1080, height: 2388)');
lines.push('    /// 支模块（MeScreen/ZhiHome/AlipayHome…）');
lines.push('    static let zhi = CGSize(width: 1280, height: 2816)');
lines.push('    /// 支模块内容总高（MeScreen.CONTENT_H）');
lines.push('    static let zhiContentHeight: CGFloat = 4067');
lines.push('    /// 支模块底部导航起始 y（MeScreen.NAV_TOP）');
lines.push('    static let zhiNavTop: CGFloat = 2560');
lines.push('}');
lines.push('');

function camel(name) {
  const parts = name.toLowerCase().split('_');
  return parts[0] + parts.slice(1).map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join('');
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, lines.join('\n') + '\n', 'utf8');
console.log(`✅ 已生成 ${OUT}`);
console.log(`   颜色常量 ${entries.length} 个`);
const sample = entries.slice(0, 5);
for (const [n, h] of sample) console.log(`   ${camel(n).padEnd(16)} ${h}`);
