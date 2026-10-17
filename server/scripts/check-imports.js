#!/usr/bin/env node
/**
 * 逐个 import 所有源码模块，验证：
 *   ① 没有路径写错（相对路径层级错误是最常见的手写错误）；
 *   ② 没有循环依赖导致的运行时异常；
 *   ③ 所有文件语法正确。
 *
 * 在 CI 或部署前跑一次，比等运行时 500 强得多。
 */
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = path.resolve(import.meta.dirname, '..');
const dirs = ['src', 'src/core', 'src/db', 'src/services', 'src/middleware', 'src/cron', 'src/modules'];

const files = [];
function walk(dir) {
  if (!fs.existsSync(dir)) return;
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) walk(full);
    else if (name.endsWith('.js')) files.push(full);
  }
}
walk(path.join(root, 'src'));

let ok = 0;
const failures = [];
for (const file of files) {
  const rel = path.relative(root, file);
  try {
    await import(pathToFileURL(file).href);
    ok += 1;
    console.log(`  ✓ ${rel}`);
  } catch (err) {
    failures.push({ rel, error: err.message });
    console.log(`  ✗ ${rel} → ${err.message}`);
  }
}

console.log(`\n共 ${files.length} 个模块：成功 ${ok}，失败 ${failures.length}`);
void dirs;
process.exit(failures.length ? 1 : 0);
