/**
 * 意见反馈（/api/v1/feedback）+ 分享好友配置 的端到端自检。
 *
 *   node scripts/feedback-test.js
 *
 * 覆盖：
 *   1. /api/v1/config 里下发 share_link / share_tip（后台「全局配置」可改）
 *   2. 未登录也能提交反馈（root 验签），图片存进存储且能按公开地址读回来
 *   3. 参数校验（正文太短 / 图片不是图片 / 图片超过 3 张）
 *   4. 后台「意见反馈」能看到这条并标记已处理
 *
 * 注意：环境变量必须在 import 业务模块**之前**设好（config.js 一被 import 就读 env），
 * 所以这里和 e2e.js 一样全用 await import()。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'feedback-test.db');
const TMP = path.join(ROOT, 'storage', 'feedback-test-tmp');

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;
process.env.TMPDIR = TMP;
process.env.STORAGE_LOCAL_DIR = path.join(ROOT, 'storage', 'feedback-test-media');

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });
fs.rmSync(TMP, { recursive: true, force: true });

const { openDb, initSchema, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { setConfig } = await import('../src/services/sysconfig.js');
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

/** 1x1 的合法 PNG（文件头正确，够 sniffImage 认出来） */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);
const dataUrl = (buf) => 'data:image/png;base64,' + buf.toString('base64');

async function main() {
  const db = await openDb({ dialect: 'sqlite' });
  await initSchema(db);
  await seedAll(db);
  await openKv();

  // 后台把分享链接改成自定义值，验证 /config 下发的就是它
  await setConfig('share_link', 'https://example.com/invite/abc');
  await setConfig('share_tip', '自定义提示语');
  await db.exec("UPDATE `sys_config` SET `is_public` = 1 WHERE `key` IN ('share_link','share_tip')");

  // 一个后台管理员，用来验证后台接口
  await db.insert('admin', {
    username: 'fbadmin',
    password_hash: hashPassword('FbAdmin@2026'),
    real_name: '反馈自检管理员',
    role_id: 1,
    status: 1,
    created_at: now(),
    updated_at: now(),
  });

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const masterKey = config.security.masterKey;

  const guest = new ApiClient(base, { masterKey, deviceId: 'fb-test-device' });

  console.log('\n① /config 下发分享配置');
  const cfg = await guest.get('/api/v1/config', { key: 'root' });
  ok('/config 成功', cfg.body.code === 0, JSON.stringify(cfg.body).slice(0, 120));
  ok('share_link 是后台改的那条', cfg.body.data?.share_link === 'https://example.com/invite/abc', String(cfg.body.data?.share_link));
  ok('share_tip 是后台改的那句', cfg.body.data?.share_tip === '自定义提示语', String(cfg.body.data?.share_tip));

  console.log('\n② 未登录提交反馈（带图片）');
  const created = await guest.post(
    '/api/v1/feedback/create',
    { content: '希望增加批量去水印功能', images: [dataUrl(PNG)], model: 'UnitTest Model', os_version: 'Android 14' },
    { key: 'root' },
  );
  ok('提交成功', created.body.code === 0, JSON.stringify(created.body).slice(0, 160));
  ok('返回记录 id', Number(created.body.data?.id) > 0);

  const row = await db.get('SELECT * FROM `feedback` WHERE `id` = ?', [created.body.data.id]);
  const keys = JSON.parse(row?.images || '[]');
  ok('已落库', Boolean(row));
  ok('图片 key 写入 images', keys.length === 1 && keys[0].startsWith('feedback/'), JSON.stringify(keys));
  ok('游客提交 user_id 为空', row.user_id === null, String(row.user_id));
  ok('记录了机型', row.model === 'UnitTest Model', String(row.model));

  console.log('\n③ 图片可以按公开地址读回来');
  const img = await fetch(`${base}/api/v1/feedback/image/${keys[0].split('/').pop()}`);
  const bytes = Buffer.from(await img.arrayBuffer());
  ok('HTTP 200', img.status === 200, String(img.status));
  ok('Content-Type 是图片', String(img.headers.get('content-type')).startsWith('image/'), String(img.headers.get('content-type')));
  ok('字节与上传一致', bytes.equals(PNG), `${bytes.length} vs ${PNG.length}`);
  const missing = await fetch(`${base}/api/v1/feedback/image/not-a-real-file.png`);
  ok('不存在的图片 → 404', missing.status === 404, String(missing.status));

  console.log('\n④ 参数校验');
  const tooShort = await guest.post('/api/v1/feedback/create', { content: 'a' }, { key: 'root' });
  ok('正文太短被拒', tooShort.body.code !== 0, JSON.stringify(tooShort.body).slice(0, 120));
  const notImage = await guest.post(
    '/api/v1/feedback/create',
    { content: '这是一条正常的建议', images: [dataUrl(Buffer.from('not an image at all, just plain text'))] },
    { key: 'root' },
  );
  ok('非图片被拒', notImage.body.code !== 0, JSON.stringify(notImage.body).slice(0, 120));
  const tooMany = await guest.post(
    '/api/v1/feedback/create',
    { content: '这是一条正常的建议', images: [dataUrl(PNG), dataUrl(PNG), dataUrl(PNG), dataUrl(PNG)] },
    { key: 'root' },
  );
  ok('图片超过 3 张被拒', tooMany.body.code !== 0, JSON.stringify(tooMany.body).slice(0, 120));

  console.log('\n⑤ 后台能收到并处理');
  const login = await guest.post('/admin/api/login', { username: 'fbadmin', password: 'FbAdmin@2026' }, { key: 'root' });
  const adminToken = login.body?.data?.token;
  ok('后台登录成功', Boolean(adminToken), JSON.stringify(login.body).slice(0, 160));
  const auth = { 'X-Admin-Token': adminToken };

  const list = await guest.get('/admin/api/feedback', { key: 'root', headers: auth });
  ok('反馈列表可读', list.body.code === 0, JSON.stringify(list.body).slice(0, 160));
  ok('列表里有刚才那条', Number(list.body.data?.total) >= 1, String(list.body.data?.total));
  ok('待处理计数 >= 1', Number(list.body.data?.pending) >= 1, String(list.body.data?.pending));
  const first = (list.body.data?.rows || [])[0] || {};
  ok('列表带图片地址', Array.isArray(first.image_urls) && first.image_urls.length === 1, JSON.stringify(first.image_urls));

  const detail = await guest.get(`/admin/api/feedback/${created.body.data.id}`, { key: 'root', headers: auth });
  ok('详情可读', detail.body.code === 0, JSON.stringify(detail.body).slice(0, 120));
  ok('详情带正文', detail.body.data?.feedback?.content === '希望增加批量去水印功能');

  const handled = await guest.post(
    `/admin/api/feedback/${created.body.data.id}`,
    { status: 1, remark: '已收到，排期中' },
    { key: 'root', headers: auth },
  );
  ok('标记已处理成功', handled.body.code === 0, JSON.stringify(handled.body).slice(0, 120));
  const after = await db.get('SELECT * FROM `feedback` WHERE `id` = ?', [created.body.data.id]);
  ok('状态已变为已处理', Number(after.status) === 1, String(after.status));
  ok('备注已写入', after.remark === '已收到，排期中', String(after.remark));
  ok('处理人已记录', Number(after.admin_id) > 0, String(after.admin_id));

  console.log('\n⑥ 分享配置在后台可改');
  const cfgList = await guest.get('/admin/api/configs', { key: 'root', headers: auth });
  const shareRow = (cfgList.body.data?.rows || []).find((r) => r.key === 'share_link');
  ok('后台配置里有 share_link', Boolean(shareRow), JSON.stringify((cfgList.body.data?.rows || []).length));
  ok('share_link 标记为下发客户端', Number(shareRow?.is_public) === 1, String(shareRow?.is_public));

  server.close();
  await closeKv();
  await closeDb();
  console.log(`\n结果\n  通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((err) => {
  console.error('测试异常：', err);
  process.exit(1);
});
