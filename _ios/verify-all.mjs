/**
 * iOS 端一键全量验证（2026-10-17）
 *
 *   node _ios/verify-all.mjs
 *
 * 把散着的几项检查串起来跑一遍，最后给一张总表：
 *   ① Swift 静态体检（括号/重名/引号/资源/令牌/版本号）
 *   ② 绘制 API 对账（页面调用的每个 d.xxx 是否真在 Draw.swift 里）
 *   ③ DTW 协议 + 配置接口镜像（打真实生产服务端）
 *   ④ 真机几何比对（我页 / 娱乐页，需要先抓过 golden 截图）
 *   ⑤ 移植进度（重新生成 PORTING-CHECKLIST.md）
 *
 * 这些都不能替代编译，但它们能在没有 Mac 的情况下把「能静态发现的问题」全部挡掉。
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';

const ROOT = 'D:\\AAA\\doutu';
const NODE = process.execPath;
const PY = 'C:\\Users\\22594\\.dsh\\dsh-runtimes\\dsh-primary-runtime\\dependencies\\python\\python.exe';

const steps = [
  { name: 'Swift 静态体检', cmd: NODE, args: [path.join(ROOT, '_ios', 'lint-swift.mjs')] },
  { name: '绘制 API 对账', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-draw-api.mjs')] },
  { name: '符号一致性', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-symbols.mjs')] },
  { name: '重复成员 / private 跨文件', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-access.mjs')] },
  { name: '静态成员访问', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-members.mjs')] },
  { name: 'State 实例成员访问', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-instance.mjs')] },
  { name: '类型混用扫描', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-types.mjs')] },
  { name: '常量重赋值', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-let.mjs')] },
  { name: '可编辑写入侧', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-editable.mjs')] },
  { name: '可编辑键存/取一致', cmd: PY, args: [path.join(ROOT, '_ios', 'check_editable_keys.py')] },
  { name: 'State 字段闲置（仅报告）', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-state.mjs')] },
  { name: '页面可达性', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-reachable.mjs')] },
  { name: '空转分支（仅报告）', cmd: NODE, args: [path.join(ROOT, '_ios', 'check-deadends.mjs')] },
  { name: '令牌完整性', cmd: NODE, args: [path.join(ROOT, '_ios', 'audit-tokens2.mjs')] },
  { name: 'CI 链路自洽性', cmd: NODE, args: [path.join(ROOT, '_ios', 'verify-ci.mjs')] },
  { name: 'DTW 协议 + 配置镜像', cmd: NODE, args: [path.join(ROOT, '_ios', 'verify-dtw-mirror.mjs')] },
  { name: '真机几何比对（我页）', cmd: PY, args: [path.join(ROOT, '_ios', 'compare_geometry.py')], optional: true },
  { name: '真机几何比对（娱乐页）', cmd: PY, args: [path.join(ROOT, '_ios', 'compare_geometry_ent.py')], optional: true },
  { name: '移植进度', cmd: NODE, args: [path.join(ROOT, '_ios', 'gen-checklist.mjs')] },
];

const results = [];
for (const step of steps) {
  process.stdout.write(`\n=== ${step.name} ===\n`);
  const r = spawnSync(step.cmd, step.args, {
    encoding: 'utf8',
    env: { ...process.env, PYTHONIOENCODING: 'utf-8' },
    cwd: ROOT,
  });
  const out = (r.stdout || '') + (r.stderr || '');
  // 只回显带结论的行，避免刷屏
  for (const line of out.split('\n')) {
    if (/通过 \d+ 项|失败 \d+ 项|worst delta|\[OK\]|\[FAIL\]|已移植|页面 \d+ 个|共定义|未检出|not found|Error/.test(line)) {
      console.log('  ' + line.trim());
    }
  }
  const okRun = r.status === 0;
  results.push({ name: step.name, ok: okRun, optional: Boolean(step.optional) });
}

console.log('\n========================================');
console.log('总表');
console.log('========================================');
let failed = 0;
for (const r of results) {
  const tag = r.ok ? '[PASS]' : (r.optional ? '[SKIP]' : '[FAIL]');
  if (!r.ok && !r.optional) failed += 1;
  console.log(`  ${tag.padEnd(7)} ${r.name}`);
}
console.log('');
if (failed === 0) {
  console.log('全部通过。注意：Swift 仍需云端 CI 编译验证（本机无 macOS）。');
} else {
  console.log(`有 ${failed} 项失败，先修再往下搬页面。`);
}
process.exit(failed === 0 ? 0 : 1);
