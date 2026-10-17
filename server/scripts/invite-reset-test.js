/**
 * 邀请码找回密码的端到端自检。
 *
 *   node scripts/invite-reset-test.js
 *
 * 覆盖：
 *   1. 邀请码注册一个账号（拿到手机号 + 邀请码 + 原密码）
 *   2. 核验：手机号 + 邀请码对得上 → 拿到一次性票据
 *   3. 核验失败：手机号不对 / 邀请码不存在 / 码还没被用过，各自给不同提示
 *   4. 改密：票据 + 两次新密码 → 成功
 *   5. 改完：旧密码登不上、新密码能登上（旧密码确实作废）
 *   6. 票据一次性：同一张票不能改第二次
 *   7. 两次密码不一致会被拒
 *
 * 环境变量必须在 import 业务模块**之前**设好（config.js 一被 import 就读 env）。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'invite-reset-test.db');

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });

const { openDb, initSchema, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { createBatch } = await import('../src/services/invite.js');
const { default: ApiClient } = await import('./lib/client.js');
const { default: config } = await import('../src/config.js');

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

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const guest = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: 'ir-test-device' });

  console.log('\n① 先用邀请码注册一个账号');
  const batch = await createBatch({ count: 2, duration: 30, adminId: null });
  const code = batch.codes[0];
  const phone = '13900001111';
  const oldPassword = 'oldpass123';
  const registered = await guest.post(
    '/api/v1/auth/register',
    {
      invite_code: code,
      username: 'resetuser',
      phone,
      password: oldPassword,
      password_confirm: oldPassword,
    },
    { key: 'root' },
  );
  ok('注册成功', registered.body.code === 0, JSON.stringify(registered.body).slice(0, 200));
  const oldToken = registered.body?.data?.token || '';

  console.log('\n② 核验：手机号 + 邀请码');
  const verified = await guest.post('/api/v1/auth/invite/verify', { phone, invite_code: code }, { key: 'root' });
  ok('核验通过', verified.body.code === 0, JSON.stringify(verified.body).slice(0, 200));
  const resetToken = verified.body?.data?.reset_token || '';
  ok('拿到一次性票据', resetToken.length >= 16, String(resetToken).slice(0, 12));
  ok('票据有有效期', Number(verified.body?.data?.expires_in) > 0, String(verified.body?.data?.expires_in));

  console.log('\n③ 核验失败的各种情况');
  const wrongPhone = await guest.post('/api/v1/auth/invite/verify', { phone: '13900002222', invite_code: code }, { key: 'root' });
  ok('手机号对不上被拒', wrongPhone.body.code !== 0, JSON.stringify(wrongPhone.body).slice(0, 140));
  ok('提示说的是手机号对不上', String(wrongPhone.body.msg).includes('手机号'), String(wrongPhone.body.msg));

  const noCode = await guest.post('/api/v1/auth/invite/verify', { phone, invite_code: 'AAAABBBBCCCC' }, { key: 'root' });
  ok('不存在的邀请码被拒', noCode.body.code !== 0, JSON.stringify(noCode.body).slice(0, 140));
  ok('提示说的是邀请码不存在', String(noCode.body.msg).includes('不存在'), String(noCode.body.msg));

  const unusedCode = batch.codes[1];
  const notUsed = await guest.post('/api/v1/auth/invite/verify', { phone, invite_code: unusedCode }, { key: 'root' });
  ok('没注册过的邀请码被拒', notUsed.body.code !== 0, JSON.stringify(notUsed.body).slice(0, 140));
  ok('提示说的是没注册过账号', String(notUsed.body.msg).includes('还没有注册'), String(notUsed.body.msg));

  const shortCode = await guest.post('/api/v1/auth/invite/verify', { phone, invite_code: 'ABC' }, { key: 'root' });
  ok('邀请码格式不对被拒', shortCode.body.code !== 0, JSON.stringify(shortCode.body).slice(0, 140));

  console.log('\n④ 改密：两次密码要一致');
  const mismatch = await guest.post(
    '/api/v1/auth/invite/reset',
    { reset_token: resetToken, password: 'newpass123', password_confirm: 'newpass124' },
    { key: 'root' },
  );
  ok('两次不一致被拒', mismatch.body.code !== 0, JSON.stringify(mismatch.body).slice(0, 140));

  const tooShort = await guest.post(
    '/api/v1/auth/invite/reset',
    { reset_token: resetToken, password: '123', password_confirm: '123' },
    { key: 'root' },
  );
  ok('密码太短被拒', tooShort.body.code !== 0, JSON.stringify(tooShort.body).slice(0, 140));

  const newPassword = 'newpass123';
  const reset = await guest.post(
    '/api/v1/auth/invite/reset',
    { reset_token: resetToken, password: newPassword, password_confirm: newPassword },
    { key: 'root' },
  );
  ok('改密成功', reset.body.code === 0, JSON.stringify(reset.body).slice(0, 200));

  console.log('\n⑤ 旧密码作废、新密码可用');
  const loginOld = await guest.post('/api/v1/auth/login', { username: phone, password: oldPassword }, { key: 'root' });
  ok('旧密码登录失败', loginOld.body.code !== 0, JSON.stringify(loginOld.body).slice(0, 140));
  const loginNew = await guest.post('/api/v1/auth/login', { username: phone, password: newPassword }, { key: 'root' });
  ok('新密码登录成功', loginNew.body.code === 0, JSON.stringify(loginNew.body).slice(0, 140));

  console.log('\n⑥ 票据一次性 + 旧会话被吊销');
  const again = await guest.post(
    '/api/v1/auth/invite/reset',
    { reset_token: resetToken, password: 'another123', password_confirm: 'another123' },
    { key: 'root' },
  );
  ok('同一张票不能改第二次', again.body.code !== 0, JSON.stringify(again.body).slice(0, 140));

  if (oldToken) {
    const staleSession = await new ApiClient(base, {
      masterKey: config.security.masterKey,
      deviceId: 'ir-test-device',
      token: oldToken,
      signKey: registered.body?.data?.sign_key || '',
    }).get('/api/v1/auth/status', { key: 'session' });
    ok('注册时那张会话也失效了', staleSession.body.code !== 0, JSON.stringify(staleSession.body).slice(0, 140));
  } else {
    ok('注册时那张会话也失效了（无会话可验，跳过）', true);
  }

  console.log('\n⑦ 落库情况');
  const row = await db.get('SELECT `password_hash`, `updated_at` FROM `app_user` WHERE `phone` = ?', [phone]);
  ok('账号还在', Boolean(row));
  const logRow = await db.get("SELECT * FROM `user_profile_log` WHERE `field` = 'password' ORDER BY id DESC LIMIT 1");
  ok('记了一条改密记录', Boolean(logRow), JSON.stringify(logRow || {}).slice(0, 120));

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
