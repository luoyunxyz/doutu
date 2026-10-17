/**
 * 玩法下发（playbook）自检。
 *
 *   node scripts/playbook-test.js
 *
 * 为什么有这条线：娱乐页的分区/标题/副标题/顺序/开关/点击去向原来是写死在客户端的，
 * 是整包里最容易被整块抄走的部分。现在它由服务端下发，这里钉死四件事：
 *   ① 默认种子里就有完整的娱乐页结构（和改造前的界面一致）
 *   ② /api/v1/config 会把 playbook 下发出去，且 key/switch/enabled 字段都在
 *   ③ 后台改 JSON 后客户端能拿到新的（不发版就能改文案/下线玩法）
 *   ④ 结构非法或为空时，接口不炸、客户端能安全退回
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'playbook-test.db');

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
const { randomHex, now, formatDateTime, addMinutes } = await import('../src/core/util.js');

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

  // 后台改配置要有个管理员账号（种子不建管理员，那是 create-admin.js 的事）
  const { hashPassword } = await import('../src/core/crypto.js');
  await db.insert('admin', {
    username: 'pbadmin',
    password_hash: hashPassword('PbAdmin@2026'),
    real_name: '玩法下发自检',
    role_id: 1,
    status: 1,
    created_at: now(),
    updated_at: now(),
  });

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const guest = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: 'playbook-test' });

  console.log('\n① 默认种子');
  const row = await db.get("SELECT `value` FROM sys_config WHERE `key` = 'playbook_entertainment'");
  ok('种子里有 playbook_entertainment', Boolean(row));
  const seeded = JSON.parse(row.value);
  const seededKeys = seeded.sections.flatMap((s) => s.rows.map((r) => r.key));
  ok('三个分区都在', seeded.sections.length === 3, String(seeded.sections.length));
  ok('六个玩法都在', seededKeys.length === 6, seededKeys.join(','));
  ok('key 是稳定标识（不是标题）', seededKeys.every((k) => /^[a-z]+$/.test(k)), seededKeys.join(','));
  ok('开关行的 switch 标记在', seeded.sections[2].rows[0].switch === true);

  console.log('\n② 下发');
  const cfg = await guest.get('/api/v1/config', { key: 'root' });
  ok('config 返回 playbook', Boolean(cfg.body.data?.playbook), JSON.stringify(cfg.body.data?.playbook).slice(0, 100));
  const ent = cfg.body.data.playbook.entertainment;
  ok('playbook.entertainment 结构完整', Array.isArray(ent?.sections) && ent.sections.length === 3);
  ok('带版本号', Number(cfg.body.data.playbook.version) >= 1, String(cfg.body.data.playbook.version));
  ok('每行都有 key/title/subtitle', ent.sections[0].rows.every((r) => r.key && r.title && r.subtitle));

  console.log('\n③ 后台改文案 / 下线玩法（不发版）');
  const admin = await db.get('SELECT id, username FROM admin WHERE role_id = 1 ORDER BY id ASC LIMIT 1');
  const token = randomHex(32);
  const ts = now();
  await db.insert('admin_token', {
    admin_id: admin.id, token, ip: '127.0.0.1', ua: 'diag',
    expire_at: formatDateTime(addMinutes(new Date(), 30)), last_seen: ts, revoked: 0, created_at: ts, updated_at: ts,
  });
  const edited = JSON.parse(JSON.stringify(seeded));
  edited.sections[0].rows[0].title = '一键去水印（改过的）';
  edited.sections[0].rows[1].enabled = false;      // 下线「淘宝订单详情」
  edited.sections[1].rows.push({ key: 'shutdown', title: '再来一行', subtitle: '验证顺序由服务端决定' });
  const save = await guest.post(`${config.security.adminPath}/api/configs`, {
    entries: [{ key: 'playbook_entertainment', value: JSON.stringify(edited) }],
  }, { key: 'root', headers: { 'X-Admin-Token': token } });
  ok('后台保存成功', save.body.code === 0, JSON.stringify(save.body).slice(0, 140));

  const after = await guest.get('/api/v1/config', { key: 'root' });
  const ent2 = after.body.data.playbook.entertainment;
  ok('客户端拿到改过的标题', ent2.sections[0].rows[0].title === '一键去水印（改过的）', ent2.sections[0].rows[0].title);
  ok('被下线的行带 enabled=false', ent2.sections[0].rows[1].enabled === false);
  ok('新增的行也下发了', ent2.sections[1].rows.length === 4, String(ent2.sections[1].rows.length));

  console.log('\n④ 异常结构不炸');
  await guest.post(`${config.security.adminPath}/api/configs`, {
    entries: [{ key: 'playbook_entertainment', value: '{"sections":"这不是数组"}' }],
  }, { key: 'root', headers: { 'X-Admin-Token': token } });
  const broken = await guest.get('/api/v1/config', { key: 'root' });
  ok('结构坏了接口照样 200', broken.body.code === 0);
  const brokenEnt = broken.body.data.playbook.entertainment;
  ok('坏结构原样透出（客户端据此判断并退回）', !Array.isArray(brokenEnt?.sections), JSON.stringify(brokenEnt).slice(0, 80));

  await db.remove('admin_token', { token });
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
