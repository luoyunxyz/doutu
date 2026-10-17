/**
 * 人工客服页（「我 → 人工客服」）的端到端自检。
 *
 *   node scripts/customer-service-test.js
 *
 * 覆盖：
 *   1. 种子配置里有客服页那三项（cs_notice / cs_contact / cs_qr_image），且标题/说明齐全
 *   2. /api/v1/config 下发 customer_service 三件套 = 后台改过的值
 *   3. 后台「全局配置」能把它们列出来（分组 = 客服），并写回新值
 *   4. 写回后 /config 立即可见（config_version 递增），客户端不用发版
 *
 * 环境变量必须在 import 业务模块**之前**设好（config.js 一被 import 就读 env），
 * 所以这里和 e2e.js 一样全用 await import()。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'cs-test.db');

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });

const { openDb, initSchema, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { setConfig, invalidate } = await import('../src/services/sysconfig.js');
const { hashPassword } = await import('../src/core/crypto.js');
const { now } = await import('../src/core/util.js');
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

  await db.insert('admin', {
    username: 'csadmin',
    password_hash: hashPassword('CsAdmin@2026'),
    real_name: '客服页自检管理员',
    role_id: 1,
    status: 1,
    created_at: now(),
    updated_at: now(),
  });

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const masterKey = config.security.masterKey;
  const guest = new ApiClient(base, { masterKey, deviceId: 'cs-test-device' });

  console.log('\n① 种子配置里有客服页那三项');
  const rows = await db.query("SELECT `key`, `value`, `group_name`, `title`, `type` FROM `sys_config` WHERE `group_name` = '客服'");
  const byKey = Object.fromEntries(rows.map((r) => [r.key, r]));
  ok('cs_notice 已种下', Boolean(byKey.cs_notice), JSON.stringify(rows.map((r) => r.key)));
  ok('cs_contact 已种下', Boolean(byKey.cs_contact));
  ok('cs_qr_image 已种下', Boolean(byKey.cs_qr_image));
  ok('默认文案就是产品要的那句', byKey.cs_notice?.value === '开通会员、不会使用等问题均可询问客服', String(byKey.cs_notice?.value));
  ok('三项都有中文标题', [byKey.cs_notice, byKey.cs_contact, byKey.cs_qr_image].every((r) => r && r.title && /[\u4e00-\u9fa5]/.test(r.title)));

  console.log('\n② /config 下发 customer_service');
  const before = await guest.get('/api/v1/config', { key: 'root' });
  ok('/config 成功', before.body.code === 0, JSON.stringify(before.body).slice(0, 140));
  const cs0 = before.body.data?.customer_service;
  ok('有 customer_service 段', Boolean(cs0), JSON.stringify(before.body.data).slice(0, 200));
  ok('notice 是默认文案', cs0?.notice === '开通会员、不会使用等问题均可询问客服', String(cs0?.notice));
  ok('contact 默认空串', cs0?.contact === '', String(cs0?.contact));
  ok('qr_image 默认空串', cs0?.qr_image === '', String(cs0?.qr_image));

  console.log('\n③ 后台改这三项（走 /configs 批量写）');
  const adminApi = `${config.security.adminPath}/api`;
  const login = await guest.post(`${adminApi}/login`, { username: 'csadmin', password: 'CsAdmin@2026' }, { key: 'root' });
  const adminToken = login.body?.data?.token || '';
  ok('管理员登录成功', Boolean(adminToken), JSON.stringify(login.body).slice(0, 160));
  const auth = { 'X-Admin-Token': adminToken };

  const saved = await guest.post(
    `${adminApi}/configs`,
    {
      entries: [
        { key: 'cs_notice', value: '有问题先问客服，开通会员更快', type: 'string' },
        { key: 'cs_contact', value: '微信 doutu-service', type: 'string' },
        { key: 'cs_qr_image', value: 'https://example.com/cs-qr.png', type: 'string' },
      ],
    },
    { key: 'root', headers: auth },
  );
  ok('保存成功', saved.body.code === 0, JSON.stringify(saved.body).slice(0, 160));

  const listed = await guest.get(`${adminApi}/configs?group=客服`, { key: 'root', headers: auth });
  ok('后台能按「客服」分组列出', listed.body.code === 0 && (listed.body.data?.rows || []).length >= 3,
    JSON.stringify(listed.body).slice(0, 200));
  const listedKeys = (listed.body.data?.rows || []).map((r) => r.key);
  ok('三项都在列表里', ['cs_notice', 'cs_contact', 'cs_qr_image'].every((k) => listedKeys.includes(k)),
    JSON.stringify(listedKeys));

  console.log('\n④ 改完 /config 立刻是新值');
  invalidate();
  const after = await guest.get('/api/v1/config', { key: 'root' });
  const cs1 = after.body.data?.customer_service;
  ok('notice 已更新', cs1?.notice === '有问题先问客服，开通会员更快', String(cs1?.notice));
  ok('contact 已更新', cs1?.contact === '微信 doutu-service', String(cs1?.contact));
  ok('qr_image 已更新', cs1?.qr_image === 'https://example.com/cs-qr.png', String(cs1?.qr_image));
  ok('config_version 递增了', Number(after.body.data?.config_version) > Number(before.body.data?.config_version),
    `${before.body.data?.config_version} -> ${after.body.data?.config_version}`);

  console.log('\n⑤ 兜底：配置被清空时不报错');
  await setConfig('cs_qr_image', '');
  invalidate();
  const cleared = await guest.get('/api/v1/config', { key: 'root' });
  ok('空图片地址仍然是空串', cleared.body.data?.customer_service?.qr_image === '',
    String(cleared.body.data?.customer_service?.qr_image));

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
