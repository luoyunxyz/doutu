/**
 * CI 构建链路自洽性核对（2026-10-17）
 *
 *   node _ios/verify-ci.mjs
 *
 * 为什么需要它：iOS 的 IPA 只能靠云端 macOS runner 编出来（本机无 Mac），
 * 而 GitHub Actions **只认仓库根的 `.github/workflows/`**。所以存在两份等价的工作流：
 *
 *   · `doutu/.github/workflows/ios-build.yml`  —— 仓库根是 `doutu/` 时用（`working-directory: ios`）
 *   · `doutu/ios/.github/workflows/ios-build.yml` —— 仓库根是 `ios/` 时用（无 working-directory）
 *
 * 两份只要有一份的路径前缀写错，CI 就会在 checkout 之后立刻失败，而且报错信息很难看出原因。
 * 这个脚本把「路径前缀」与「working-directory」逐条对账。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = 'D:\\AAA\\doutu';
const ROOT_WF = path.join(ROOT, '.github', 'workflows', 'ios-build.yml');
const IOS_WF = path.join(ROOT, 'ios', '.github', 'workflows', 'ios-build.yml');

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  [OK]   ${name}`); }
  else { fail += 1; console.log(`  [FAIL] ${name} ${extra}`); }
};

ok('仓库根的工作流存在', fs.existsSync(ROOT_WF), ROOT_WF);
ok('ios/ 下的工作流存在', fs.existsSync(IOS_WF), IOS_WF);

/** 去掉注释行再判断 —— 否则「注释里提到 working-directory: ios」会被当成真的写了 */
function stripComments(text) {
  return text
    .split('\n')
    .filter((line) => !line.trim().startsWith('#'))
    .join('\n');
}

const rootWf = stripComments(fs.readFileSync(ROOT_WF, 'utf8'));
const iosWf = stripComments(fs.readFileSync(IOS_WF, 'utf8'));

// ① 仓库根那份：必须进 ios/ 目录工作，产物路径带 ios/
ok('仓库根那份用 working-directory: ios', /working-directory:\s*ios/.test(rootWf));
ok('仓库根那份的产物路径带 ios/ 前缀', /path:\s*ios\/Doutu-unsigned\.ipa/.test(rootWf));
ok('仓库根那份的日志路径带 ios/ 前缀', /path:\s*ios\/build\.log/.test(rootWf));

// ② ios/ 那份：不能有 working-directory: ios（那时 ios 就是根）
ok('ios/ 那份不带 working-directory: ios', !/working-directory:\s*ios/.test(iosWf));
ok('ios/ 那份的产物路径不带 ios/ 前缀', /path:\s*Doutu-unsigned\.ipa/.test(iosWf));

// ③ 两份共有的关键动作
for (const [name, text] of [['仓库根', rootWf], ['ios/', iosWf]]) {
  ok(`${name}那份装了 XcodeGen`, /brew install xcodegen/.test(text));
  ok(`${name}那份跑 xcodegen generate`, /xcodegen generate/.test(text));
  ok(`${name}那份关掉了签名`, /CODE_SIGNING_ALLOWED=NO/.test(text));
  ok(`${name}那份按 Payload 结构打包`, /mkdir -p Payload/.test(text) && /zip -qr/.test(text));
  ok(`${name}那份上传 artifact`, /actions\/upload-artifact@v4/.test(text));
}

// ④ project.yml 的关键约定（CI 会读它）
const projectYml = fs.readFileSync(path.join(ROOT, 'ios', 'project.yml'), 'utf8');
ok('project.yml 声明了 Sources', /path:\s*Sources/.test(projectYml));
ok('project.yml 把 web 当文件夹引用打包', /path:\s*Resources\/web\s*\n\s*type:\s*folder/.test(projectYml));
ok('project.yml 排除了 Resources 下的 web（避免重复）', /excludes:\s*\n\s*-\s*"?web"?/.test(projectYml));
ok('project.yml 允许不签名', /CODE_SIGNING_ALLOWED:\s*NO/.test(projectYml));

// ⑤ 工程内引用的入口文件都在
for (const rel of ['ios/Sources/App/DoutuApp.swift', 'ios/Sources/Screens/Screen.swift',
                   'ios/Resources/web/index.html']) {
  ok(`存在 ${rel}`, fs.existsSync(path.join(ROOT, rel)));
}

console.log('');
console.log(`结果\n  通过 ${pass} 项，失败 ${fail} 项`);
if (fail === 0) {
  console.log('  CI 链路自洽。真正出包仍需推上 GitHub 跑一次（本机无 macOS）。');
}
process.exit(fail === 0 ? 0 : 1);
