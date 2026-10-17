// 把仓库根的工作流复制成 ios/ 那一份（去掉 working-directory 与 ios/ 前缀）
// 用法：node _ios/fix-ios-workflow.mjs
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..');
const SRC = path.join(ROOT, '.github', 'workflows', 'ios-build.yml');
const OUT = path.join(ROOT, 'ios', '.github', 'workflows', 'ios-build.yml');

const head = [
  '# 在云端构建 iOS 包 —— 仓库根就是 ios/ 目录本身时用这一份',
  '#',
  '# 与仓库根的 .github/workflows/ios-build.yml 内容一致，只是没有 working-directory、',
  '# 路径也不带 ios/ 前缀。完整说明见 ios/INSTALL-IPA.md。',
  '# 自动化核对：node _ios/verify-ci.mjs',
].join('\n');

let text = fs.readFileSync(SRC, 'utf8');
text = head + text.slice(text.indexOf('\nname: build-ios-ipa'));

const kept = [];
for (const line of text.split(/\r?\n/)) {
  if (/^\s*working-directory:\s*ios\s*$/.test(line)) continue;
  if (line.trim() === '') continue;
  kept.push(line);
}
let out = kept.join('\n') + '\n';
out = out.split('ios/build.log').join('build.log');
out = out.split('ios/xcodegen.log').join('xcodegen.log');
out = out.split('ios/Doutu-unsigned.ipa').join('Doutu-unsigned.ipa');
out = out.split('# （它没有 `working-directory`，路径也不带 `ios/` 前缀）。').join('');

fs.writeFileSync(OUT, out, 'utf8');
console.log('  已重写 ' + path.relative(ROOT, OUT));
console.log('  working-directory: ' + (out.match(/working-directory:/g) || []).length + ' 处');
console.log('  ios/ 前缀残留: ' + (out.match(/ios\/[a-z]/g) || []).length + ' 处');
console.log('  含「日志提交回仓库」: ' + (out.includes('失败时把日志提交回仓库') ? '✓' : '✗'));
