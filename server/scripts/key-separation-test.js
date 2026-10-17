/**
 * 密钥分离自检（2026-10-17）。
 *
 *   node scripts/key-separation-test.js
 *
 * 目标：一把钥匙只干一件事 —— 存储签名、root 引导签名、会话密钥、协议握手互不牵连，
 * 泄露任意一个都不至于把其他的送出去。这里逐条钉死：
 *
 *   ① 存储下载链接用 STORAGE_SECRET 签；用 MASTER_KEY 签的老链接仍然认；
 *      用第三把密钥签的必须被拒
 *   ② 会话 sign_key 是**每会话随机**的：两次登录不同，且不等于 HMAC(MASTER_KEY, ...) 的派生值
 *   ③ root 引导签名：配了 ROOT_SIGN_KEY 时认新密钥，同时兼容老客户端的 MASTER_KEY 签名；
 *      两者都不是的签名被拒
 *
 * 环境变量必须在 import 业务模块前设好。
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'key-separation-test.db');

const MASTER = 'a'.repeat(64);
const STORAGE = 'b'.repeat(64);
const ROOT_SIGN = 'c'.repeat(64);

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;
process.env.MASTER_KEY = MASTER;
process.env.STORAGE_SECRET = STORAGE;
process.env.ROOT_SIGN_KEY = ROOT_SIGN;
process.env.DTW_ENABLED = 'false';

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });

const { openDb, initSchema, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const storage = await import('../src/modules/dw/storage.js');
const { masterKeyEffective, storageSecretEffective, rootSignKeyEffective } = await import('../src/config.js');
const { hmacSha256Hex, normalizeDeviceId } = await import('../src/core/crypto.js');
const { default: config } = await import('../src/config.js');
const { default: ApiClient } = await import('./lib/client.js');

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) {
    pass += 1;
    console.log(`  ✓ ${name}`);
  } else {
    fail += 1;
    console.log(`  ✗ ${name} ${extra}`);
  }
};

async function main() {
  const db = await openDb({ dialect: 'sqlite' });
  await initSchema(db);
  await seedAll(db);
  await openKv();

  console.log('\n① 三把密钥各自生效');
  ok('MASTER_KEY 生效', masterKeyEffective() === MASTER);
  ok('STORAGE_SECRET 独立生效', storageSecretEffective() === STORAGE, storageSecretEffective().slice(0, 8));
  ok('ROOT_SIGN_KEY 独立生效', rootSignKeyEffective() === ROOT_SIGN, rootSignKeyEffective().slice(0, 8));
  ok('三者互不相同', new Set([masterKeyEffective(), storageSecretEffective(), rootSignKeyEffective()]).size === 3);

  console.log('\n② 存储下载链接');
  const key = 'out/2026-10-17/demo123.mp4';
  const ttl = 900;
  const exp = Math.floor(Date.now() / 1000) + ttl;
  const newSign = hmacSha256Hex(STORAGE, `${key}:${exp}`);
  const legacySign = hmacSha256Hex(MASTER, `${key}:${exp}`);
  const alienSign = hmacSha256Hex('d'.repeat(64), `${key}:${exp}`);
  ok('新密钥签的链接通过', storage.verifyLocalUrl(key, exp, newSign).ok === true);
  ok('老（主密钥）签的链接兼容', storage.verifyLocalUrl(key, exp, legacySign).ok === true);
  ok('第三把密钥签的被拒', storage.verifyLocalUrl(key, exp, alienSign).ok === false, JSON.stringify(storage.verifyLocalUrl(key, exp, alienSign)));
  ok('改一个字符就失效', storage.verifyLocalUrl(`${key}x`, exp, newSign).ok === false);
  ok('过期链接被拒', storage.verifyLocalUrl(key, Math.floor(Date.now() / 1000) - 10, newSign).ok === false);
  const url = storage.signedUrl(key, ttl);
  ok('signedUrl 用的是新密钥', url.includes(newSign), url);

  console.log('\n③ 会话密钥每会话随机');
  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const guest = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: 'key-sep-device' });

  const batch = await (await import('../src/services/invite.js')).createBatch({ count: 2, duration: 30, adminId: null });
  const makeUser = async (code, name, phone) => {
    const res = await guest.post('/api/v1/auth/register', {
      invite_code: code, username: name, phone, password: 'pass123456', password_confirm: 'pass123456',
    }, { key: 'root' });
    return res.body;
  };
  const a = await makeUser(batch.codes[0], 'keysepa', '13900003001');
  const b = await makeUser(batch.codes[1], 'keysepb', '13900003002');
  ok('两个账号都注册成功', a.code === 0 && b.code === 0, `${a.code}/${b.code}`);
  const keyA = a.data?.sign_key || '';
  const keyB = b.data?.sign_key || '';
  ok('会话密钥长度 64 位十六进制', /^[0-9a-f]{64}$/i.test(keyA), keyA.slice(0, 12));
  ok('不同会话的密钥不同', keyA !== keyB);
  ok('会话密钥 ≠ MASTER_KEY', keyA !== MASTER);
  // 老实现是 HMAC(MASTER_KEY, server_random:nonce:device_id)，这里确认已经不是那一套
  const derived = hmacSha256Hex(MASTER, `${a.data?.server_random || ''}:${normalizeDeviceId('key-sep-device')}:key-sep-device`);
  ok('会话密钥不是 MASTER_KEY 的派生值', keyA !== derived);

  console.log('\n④ root 引导签名（老客户端的引导接口通道）');
  // 注意：/version/check 是公开信息（特意不挂签名中间件，老客户端升级要靠它），
  // 真正走 root 验签的是 /api/v1/config 这类引导接口，改密钥的兼容性要在它身上验。
  const ts = Math.floor(Date.now() / 1000);
  const pathname = '/api/v1/config';
  const bodyHash = crypto.createHash('sha256').update('').digest('hex');
  const payload = [String(ts), 'nonce-placeholder', '', pathname, bodyHash].join('\n');
  const signWith = (secret, nonce) =>
    hmacSha256Hex(secret, [String(ts), nonce, '', pathname, bodyHash].join('\n'));
  const call = async (secret) => {
    const nonce = crypto.randomBytes(8).toString('hex');
    const res = await fetch(base + pathname, {
      headers: { 'X-Timestamp': String(ts), 'X-Nonce': nonce, 'X-Sign': signWith(secret, nonce) },
    });
    return res.json();
  };
  void payload;
  const withRoot = await call(ROOT_SIGN);
  ok('ROOT_SIGN_KEY 签名通过', withRoot.code === 0, JSON.stringify(withRoot).slice(0, 140));
  const withMaster = await call(MASTER);
  ok('老客户端的 MASTER_KEY 签名仍兼容', withMaster.code === 0, JSON.stringify(withMaster).slice(0, 140));
  const withAlien = await call('e'.repeat(64));
  ok('别的密钥签名被拒', withAlien.code !== 0, JSON.stringify(withAlien).slice(0, 140));
  const unsigned = await fetch(base + pathname, { headers: { 'X-Timestamp': String(ts), 'X-Nonce': crypto.randomBytes(8).toString('hex') } });
  ok('完全不签名被拒', (await unsigned.json()).code !== 0);

  server.close();
  await closeKv();
  await closeDb();
  console.log(`\n结果\n  通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
