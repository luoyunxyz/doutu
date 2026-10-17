#!/usr/bin/env node
// 云端 iOS 构建状态查询（Windows 本地也能跑，不需要 macOS、不需要登录 Actions 网页）
//
// 原理：`.github/workflows/ios-build.yml` 每次跑完都会把结论提交回仓库
//   ci-logs/status.txt     —— status: success / failure + run id + commit
//   ci-logs/build.log      —— 失败时的完整编译日志
// 所以只要 git fetch 一下就能读到最新一次的结果。
//
// 用法：
//   node _ios/ci-status.mjs          # fetch 并打印状态（失败时顺带列出前 30 条 error）
//   node _ios/ci-status.mjs --wait   # 每 30 秒查一次，最多 15 分钟，直到状态有变化
//
// 退出码：0 = 最近一次构建成功（或还在跑、状态未变），1 = 最近一次构建失败。

import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const waitMode = process.argv.includes('--wait');

const git = (args) =>
  execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });

function fetchQuietly() {
  try {
    git(['fetch', 'origin', '--quiet']);
  } catch {
    console.log('⚠️  git fetch 失败（离线？）——下面显示的是本地缓存的最新状态');
  }
}

function showFile(rev, file) {
  try {
    return git(['show', `${rev}:${file}`]);
  } catch {
    return '';
  }
}

function report() {
  fetchQuietly();
  for (const rev of ['origin/main', 'HEAD']) {
    const status = showFile(rev, 'ci-logs/status.txt').trim();
    if (!status) continue;

    console.log(`📄 ${rev}:ci-logs/status.txt`);
    console.log(status);
    console.log('');

    if (/\bstatus:\s*success\b/.test(status)) {
      console.log('✅ 最近一次构建成功 —— 去 Actions 的 Artifacts 下载 Doutu-unsigned-ipa');
      console.log('   装机步骤见 ios/INSTALL-IPA.md');
      return 0;
    }

    if (/\bstatus:\s*failure\b/.test(status)) {
      console.log('❌ 最近一次构建失败 —— 以下是编译错误（最多 30 条）');
      const log = showFile(rev, 'ci-logs/build.log');
      const errors = log.split(/\r?\n/).filter((l) => l.includes('error:'));
      if (errors.length === 0) {
        console.log('   build.log 里没有 error: 行（可能挂在 xcodegen / 打包步骤）');
      } else {
        for (const line of [...new Set(errors)].slice(0, 30)) console.log('   ' + line);
      }
      return 1;
    }
  }

  console.log('ℹ️  仓库里还没有 ci-logs/status.txt —— 说明这次工作流版本还没跑过，');
  console.log('   或者构建正在进行中（一次大约 7 分钟）。');
  return 0;
}

if (!waitMode) {
  process.exit(report());
}

// --wait：看 status.txt 什么时候变（用 run id 判断），变了再 report 一次
const before = (() => {
  try {
    fetchQuietly();
    return git(['show', 'origin/main:ci-logs/status.txt']);
  } catch {
    return '';
  }
})();

console.log('⏳ 等待云端构建结果（每 30 秒查一次，最多 15 分钟）…');
for (let i = 1; i <= 30; i++) {
  await new Promise((r) => setTimeout(r, 30_000));
  let now = '';
  try {
    git(['fetch', 'origin', '--quiet']);
    now = git(['show', 'origin/main:ci-logs/status.txt']);
  } catch {
    // 忽略单次失败
  }
  if (now && now !== before) {
    console.log(`⏱  第 ${i * 30} 秒检测到状态更新\n`);
    process.exit(report());
  }
  process.stdout.write(`   ${i * 30}s …\r`);
}

console.log('\n⌛ 15 分钟内状态没变化。如果这次该有新结果，去 Actions 页面确认一下。');
process.exit(report());
