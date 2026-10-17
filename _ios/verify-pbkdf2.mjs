/**
 * PBKDF2 手写实现的镜像验证（2026-10-17，第 95 轮）
 *
 *   node _ios/verify-pbkdf2.mjs
 *
 * iOS 侧因为 `import CommonCrypto` 不可用，`DtwCrypto.pbkdf2Sha256` 改成了用 CryptoKit
 * 手写实现。这个脚本用**同样的算法**在 Node 里跑一遍，与 Node 内置的
 * `crypto.pbkdf2Sync`（OpenSSL 实现）逐字节比对 —— 验证算法本身没写错。
 *
 * 注意：这验证的是「算法逻辑」，不是「Swift 代码能编译」。两者不是一回事。
 */
import crypto from 'node:crypto';

/** 与 Swift 版逐行对应：U1 = HMAC(P, S‖INT(1))，Ui = HMAC(P, U(i-1))，T = ⊕Ui */
function mirrored(password, salt, iterations, keyLength) {
  const hLen = 32;
  const blockCount = Math.ceil(keyLength / hLen);
  const derived = [];
  for (let block = 1; block <= blockCount; block += 1) {
    const prefix = Buffer.alloc(4);
    prefix.writeUInt32BE(block, 0);
    let u = crypto.createHmac('sha256', password)
      .update(Buffer.concat([salt, prefix])).digest();
    const t = Buffer.from(u);
    for (let i = 2; i <= iterations; i += 1) {
      u = crypto.createHmac('sha256', password).update(u).digest();
      for (let b = 0; b < hLen; b += 1) t[b] ^= u[b];
    }
    derived.push(t);
  }
  return Buffer.concat(derived).subarray(0, keyLength).toString('hex');
}

const cases = [
  ['password', 'salt', 1],
  ['password', 'salt', 2],
  ['password', 'salt', 4096],
  ['password', 'salt', 20000],
  ['p@ssw0rd!中文', 'NaCl', 1000],
  ['', 'salt', 1],
];

let fail = 0;
console.log('  password / salt / iter                标准实现 ↔ 手写镜像');
for (const [password, salt, iterations] of cases) {
  if (password === '') {
    console.log('  （跳过空密码：Swift 侧有 guard 直接返回 nil）');
    continue;
  }
  const expected = crypto.pbkdf2Sync(password, salt, iterations, 32, 'sha256').toString('hex');
  const actual = mirrored(Buffer.from(password, 'utf8'), Buffer.from(salt, 'utf8'), iterations, 32);
  const same = expected === actual;
  if (!same) fail += 1;
  console.log(`  ${(password + ' / ' + salt + ' / ' + iterations).padEnd(38)} ${same ? '一致 ✓' : '不一致 ✗'}`);
  if (!same) {
    console.log('      标准: ' + expected);
    console.log('      手写: ' + actual);
  }
}

console.log('');
console.log(`结果\n  通过 ${fail === 0 ? cases.length - 1 : 0} 项，失败 ${fail} 项`);
process.exit(fail === 0 ? 0 : 1);
