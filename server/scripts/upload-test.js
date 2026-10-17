/**
 * 后台上传安装包自检（2026-10-17）。
 *
 *   node scripts/upload-test.js
 *
 * 为什么要有这条线：以前发版本靠 scp，中途断了会在线上留下**截断的包**（真实踩到：
 * 12.9MB 的半个包挂在 /upgrade/app/ 下，用户下到会装不上）。这里把上传路径的每个
 * 安全点都钉住：流式落盘、先临时文件后改名、体积上限、APK 魔数、文件名白名单、删除。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'upload-test.db');

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });

const { openDb, initSchema, closeDb, getDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { default: ApiClient } = await import('./lib/client.js');
const { default: config } = await import('../src/config.js');
const { hashPassword } = await import('../src/core/crypto.js');
const { now, randomHex, formatDateTime, addMinutes } = await import('../src/core/util.js');
const { APK_DIR } = await import('../src/modules/admin/upload.js');

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

/** 造一个「看起来像 APK」的字节流：zip 魔数 + 填充 */
function fakeApk(sizeBytes) {
  const head = Buffer.from([0x50, 0x4b, 0x03, 0x04]);
  const body = Buffer.alloc(Math.max(0, sizeBytes - head.length), 7);
  return Buffer.concat([head, body]);
}

async function main() {
  // 自检会写真实的 public/upgrade/app 目录，先把上次可能留下的测试文件清掉
  for (const n of fs.existsSync(APK_DIR) ? fs.readdirSync(APK_DIR) : []) {
    if (n.startsWith('doutu-test-') || n === 'passwd.apk') fs.rmSync(path.join(APK_DIR, n), { force: true });
  }

  const db = await openDb({ dialect: 'sqlite' });
  await initSchema(db);
  await seedAll(db);
  await openKv();

  await db.insert('admin', {
    username: 'upadmin', password_hash: hashPassword('UpAdmin@2026'), real_name: '上传自检',
    role_id: 1, status: 1, created_at: now(), updated_at: now(),
  });

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const guest = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: 'up-test' });
  const token = randomHex(32);
  const ts = now();
  const adminId = (await db.get("SELECT id FROM admin WHERE username = 'upadmin'")).id;
  await db.insert('admin_token', {
    admin_id: adminId, token, ip: '127.0.0.1', ua: 'diag',
    expire_at: formatDateTime(addMinutes(new Date(), 30)), last_seen: ts, revoked: 0, created_at: ts, updated_at: ts,
  });
  const auth = { 'X-Admin-Token': token };

  const upload = async (name, buffer) => {
    const res = await fetch(`${base}/admin/api/versions/upload?name=${encodeURIComponent(name)}`, {
      method: 'POST',
      headers: { 'X-Admin-Token': token, 'Content-Type': 'application/octet-stream' },
      body: buffer,
    });
    return { status: res.status, body: await res.json() };
  };

  console.log('\n① 正常上传一个小包');
  const small = await upload('doutu-test-1.0.0.apk', fakeApk(2048));
  ok('上传成功', small.body.code === 0, JSON.stringify(small.body).slice(0, 160));
  ok('返回下载地址', /\/upgrade\/app\/doutu-test-1\.0\.0\.apk$/.test(small.body.data?.url || ''), small.body.data?.url);
  ok('文件真的落盘了', fs.existsSync(path.join(APK_DIR, 'doutu-test-1.0.0.apk')));
  ok('临时文件已清理', !fs.existsSync(path.join(APK_DIR, 'doutu-test-1.0.0.apk.part')));

  console.log('\n② 超过 2MB 请求体上限也能传（走流式，不是读进内存）');
  const big = await upload('doutu-test-big.apk', fakeApk(6 * 1024 * 1024));
  ok('6MB 的包上传成功', big.body.code === 0, JSON.stringify(big.body).slice(0, 160));
  const bigStat = fs.statSync(path.join(APK_DIR, 'doutu-test-big.apk'));
  ok('落盘大小一致', bigStat.size === 6 * 1024 * 1024, String(bigStat.size));

  console.log('\n③ 不是 APK 的文件要被拒');
  const notApk = await upload('doutu-test-fake.apk', Buffer.from('this is definitely not a zip'));
  ok('非 zip 内容被拒', notApk.body.code !== 0, JSON.stringify(notApk.body).slice(0, 140));
  ok('被拒的文件没有落盘', !fs.existsSync(path.join(APK_DIR, 'doutu-test-fake.apk')));

  console.log('\n④ 文件名白名单');
  for (const bad of ['../../etc/passwd.apk', 'evil.exe', 'no-extension', 'a b.apk']) {
    const res = await upload(bad, fakeApk(512));
    ok(`拒绝 ${JSON.stringify(bad)}`, res.body.code !== 0, JSON.stringify(res.body).slice(0, 120));
  }
  // 被拒的文件一个都不该留在目录里（注意：这里用的就是真实的 public/upgrade/app 目录）
  const leftovers = fs.readdirSync(APK_DIR).filter((n) => !/^doutu-1\./.test(n) && !n.startsWith('doutu-test-'));
  ok('被拒的文件没有留在目录里', leftovers.length === 0, leftovers.join(','));

  console.log('\n⑤ 列表与删除');
  const list = await guest.get(`${config.security.adminPath}/api/versions/files`, { key: 'root', headers: auth });
  const names = (list.body.data?.files || []).map((f) => f.name);
  ok('列表里能看到刚传的包', names.includes('doutu-test-1.0.0.apk') && names.includes('doutu-test-big.apk'), names.join(','));
  const first = (list.body.data.files || []).find((f) => f.name === 'doutu-test-big.apk');
  ok('列表带回大小', Number(first?.size) === 6 * 1024 * 1024, String(first?.size));

  const del = await guest.call('DELETE', `${config.security.adminPath}/api/versions/files/doutu-test-big.apk`, null, { key: 'root', headers: auth });
  ok('删除成功', del.body.code === 0, JSON.stringify(del.body).slice(0, 140));
  ok('文件真的没了', !fs.existsSync(path.join(APK_DIR, 'doutu-test-big.apk')));
  const delAgain = await guest.call('DELETE', `${config.security.adminPath}/api/versions/files/doutu-test-big.apk`, null, { key: 'root', headers: auth });
  ok('重复删除给出「不存在」', delAgain.body.code !== 0, JSON.stringify(delAgain.body).slice(0, 120));

  console.log('\n⑥ 没权限 / 没登录');
  const anon = await fetch(`${base}/admin/api/versions/files`);
  ok('不带管理员令牌被拒', anon.status !== 200 || !(await anon.json()).data, String(anon.status));

  console.log('\n⑦ 上传的包能被静态路由下载（和用户端的下载路径一致）');
  const download = await fetch(`${base}/upgrade/app/doutu-test-1.0.0.apk`);
  ok('静态下载 200', download.status === 200, String(download.status));
  const bytes = Buffer.from(await download.arrayBuffer());
  ok('内容与上传一致', bytes.length === 2048 && bytes[0] === 0x50 && bytes[1] === 0x4b, String(bytes.length));

  // 清理测试文件（目录是真实的 public/upgrade/app，别留下垃圾）
  for (const n of fs.readdirSync(APK_DIR)) {
    if (n.startsWith('doutu-test-') || n === 'passwd.apk') fs.rmSync(path.join(APK_DIR, n), { force: true });
  }

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
