/**
 * 授权链路自检（不依赖任何外部服务，可直接跑）：
 *   邀请码注册 → 真实登录 → 未激活状态 → 后台激活 → 会话/握手/心跳/解绑，
 * 并验证签名错误 / 时间窗超限 / 重放 / 一码一账号 / 两次密码不一致等异常分支。
 *
 * 运行：npm run smoke
 *
 * 说明：本脚本在 import 之前先改环境变量（config.js 在模块初始化时读 env），
 * 因此用动态 import —— 这样自检用的是独立的 smoke 库，不会污染开发数据。
 */
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
process.env.SQLITE_FILE = path.join(ROOT, 'data', 'smoke.db');
process.env.PORT = process.env.SMOKE_PORT || '17101';
process.env.LOG_LEVEL = process.env.LOG_LEVEL || 'warn';
process.env.DB_DIALECT = 'sqlite';
for (const f of ['smoke.db', 'smoke.db-wal', 'smoke.db-shm']) fs.rmSync(path.join(ROOT, 'data', f), { force: true });

const { openDb, initSchema, getDb, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { createBatch } = await import('../src/services/invite.js');
const { activateUser, findByPhone } = await import('../src/services/user.js');
const { setConfig } = await import('../src/services/sysconfig.js');
const { default: ApiClient } = await import('./lib/client.js');
const { default: config } = await import('../src/config.js');
const { ERROR } = await import('../src/core/errors.js');

let passed = 0;
let failed = 0;
const failures = [];

function check(name, condition, extra) {
  if (condition) {
    passed += 1;
    console.log(`  ✓ ${name}`);
  } else {
    failed += 1;
    failures.push(name);
    console.log(`  ✗ ${name}${extra !== undefined ? ` → ${JSON.stringify(extra).slice(0, 320)}` : ''}`);
  }
}
const section = (title) => console.log(`\n${title}`);

async function main() {
  section('① 初始化');
  const db = await openDb();
  await initSchema(db);
  await seedAll(db);
  await openKv();
  check('数据库与 KV 就绪', Boolean(db));

  // maxDevice 给宽一点：下面的登录用例会换设备，设备上限本身在 e2e 里单独验
  const inviteBatch = await createBatch({ count: 3, level: 2, duration: 0, quotaDay: 5, maxDevice: 5, remark: '自检邀请码' });
  check('邀请码批次生成', inviteBatch.codes.length === 3, { batch: inviteBatch.batch_no });

  const router = buildRouter();
  const server = createServer(router);
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const masterKey = config.security.masterKey || 'dev-master-key';
  console.log(`  服务已监听 ${base}`);

  section('② 健康检查（无需签名）');
  const health = await fetch(`${base}/healthz`).then((r) => r.json());
  check('healthz 返回 code=0', health.code === 0, health);

  section('③ 邀请码注册（用户名 + 手机号 + 两次密码）');
  const deviceA = 'smoke-device-AAAA1111';
  const client = new ApiClient(base, { masterKey, deviceId: deviceA, appVersion: '1.0.0', versionCode: 1 });
  const phoneA = '13800000001';
  const usernameA = '自检用户_a';
  const passwordA = 'Smoke@2026';

  const noUsername = await client.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[0], phone: phoneA, password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('缺少用户名被拒', noUsername.body.code === ERROR.PARAM && String(noUsername.body.msg).includes('用户名'), noUsername.body);

  const badUsername = await client.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[0], username: '13800000001', phone: phoneA, password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('用户名不能是手机号', badUsername.body.code === ERROR.PARAM, badUsername.body);

  const shortUsername = await client.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[0], username: 'a', phone: phoneA, password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('用户名长度不足被拒', shortUsername.body.code === ERROR.PARAM, shortUsername.body);

  const register = await client.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[0], username: usernameA, phone: phoneA, password: passwordA, password_confirm: passwordA, nickname: '自检用户' },
    { key: 'root' },
  );
  check('注册成功 (code=0)', register.body.code === ERROR.OK, register.body);
  check('下发 token / sign_key', Boolean(register.body.data?.token && register.body.data?.sign_key));
  check('返回体带回用户名', register.body.data?.user?.username === usernameA, register.body.data?.user?.username);
  check('注册后未开通会员（activated=false）', register.body.data?.user?.activated === false, register.body.data?.user);
  check('未开通时没有到期时间', register.body.data?.user?.expire_at === null, register.body.data?.user?.expire_at);
  check('提示可直接使用（带水印）', String(register.body.msg || '').includes('水印'), register.body.msg);

  client.token = register.body.data.token;
  client.signKey = register.body.data.sign_key;

  section('④ 真实登录（手机号 / 用户名都可以）');
  const loginClient = new ApiClient(base, { masterKey, deviceId: deviceA });
  const login = await loginClient.post('/api/v1/auth/login', { username: phoneA, password: passwordA }, { key: 'root' });
  check('用手机号登录成功', login.body.code === ERROR.OK && Boolean(login.body.data.token), login.body);
  check('登录返回激活状态', login.body.data?.user?.activated === false, login.body.data?.user?.activated);

  const loginByName = await new ApiClient(base, { masterKey, deviceId: 'smoke-device-BBBB2222' }).post(
    '/api/v1/auth/login',
    { username: usernameA, password: passwordA },
    { key: 'root' },
  );
  check('用用户名登录成功', loginByName.body.code === ERROR.OK && Boolean(loginByName.body.data.token), loginByName.body);

  const wrongPassword = await new ApiClient(base, { masterKey, deviceId: deviceA }).post(
    '/api/v1/auth/login',
    { username: phoneA, password: 'wrong-password' },
    { key: 'root' },
  );
  check('密码错误被拒', wrongPassword.body.code === ERROR.PARAM, wrongPassword.body);

  loginClient.token = login.body.data.token;
  loginClient.signKey = login.body.data.sign_key;
  // 同一设备再次登录会撤销该设备上的旧会话（服务端有意为之），
  // 因此后续用例统一改用刚登录的会话 —— 真实客户端也是这个行为。
  client.token = loginClient.token;
  client.signKey = loginClient.signKey;

  section('⑤ 修改用户名（同步到服务端，新旧名都能登录）');
  const badName = await client.post('/api/v1/auth/profile', { username: 'a' });
  check('用户名太短 → 拒绝', badName.body.code === ERROR.PARAM, badName.body);

  const phoneName = await client.post('/api/v1/auth/profile', { username: '13900001111' });
  check('用户名不能是手机号 → 拒绝', phoneName.body.code === ERROR.PARAM, phoneName.body);

  const sameName = await client.post('/api/v1/auth/profile', { username: usernameA });
  check('改成同名 → 拒绝', sameName.body.code === ERROR.PARAM, sameName.body);

  const renamed = await client.post('/api/v1/auth/profile', { username: '改名后的我' });
  check('改用户名成功', renamed.body.code === ERROR.OK, renamed.body);
  check('响应里是新用户名', renamed.body.data?.user?.username === '改名后的我', renamed.body.data?.user?.username);
  check('库里已同步', (await getDb().get('SELECT `username` FROM `app_user` WHERE `phone` = ?', [phoneA]))?.username === '改名后的我');
  check('留下了变更留痕', Number((await getDb().get("SELECT COUNT(*) AS n FROM `user_profile_log` WHERE `field` = 'username'")).n) >= 1);

  const loginNewName = await new ApiClient(base, { masterKey, deviceId: 'smoke-device-CCCC3333' }).post(
    '/api/v1/auth/login',
    { username: '改名后的我', password: passwordA },
    { key: 'root' },
  );
  check('用新用户名可登录', loginNewName.body.code === ERROR.OK, loginNewName.body);
  const loginOldName = await new ApiClient(base, { masterKey, deviceId: 'smoke-device-DDDD4444' }).post(
    '/api/v1/auth/login',
    { username: usernameA, password: passwordA },
    { key: 'root' },
  );
  check('旧用户名已失效（登录标识确实换了）', loginOldName.body.code !== ERROR.OK, loginOldName.body);
  const loginByPhoneStill = await new ApiClient(base, { masterKey, deviceId: 'smoke-device-EEEE5555' }).post(
    '/api/v1/auth/login',
    { username: phoneA, password: passwordA },
    { key: 'root' },
  );
  check('手机号登录不受改名影响', loginByPhoneStill.body.code === ERROR.OK, loginByPhoneStill.body);

  section('⑤c 头像上传 / 公开读取 / 恢复默认');
  // 一张 1x1 的合法 PNG（最小可用图片）
  const tinyPng = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64',
  );
  const noImage = await client.post('/api/v1/auth/avatar', {});
  check('没传图片 → 拒绝', noImage.body.code === ERROR.PARAM, noImage.body);

  const notImage = await client.post('/api/v1/auth/avatar', {
    image_base64: Buffer.from('这根本不是图片，只是随便一段文本内容用于测试文件头识别').toString('base64'),
  });
  check('非图片内容 → 拒绝', notImage.body.code === ERROR.PARAM && /图片/.test(notImage.body.msg), notImage.body);

  const upload = await client.post('/api/v1/auth/avatar', {
    image_base64: `data:image/png;base64,${tinyPng.toString('base64')}`,
  });
  check('上传头像成功', upload.body.code === ERROR.OK, upload.body);
  const avatarUrl = upload.body.data?.user?.avatar_url;
  check('返回头像地址（带版本号）', Boolean(avatarUrl) && /^\/avatar\/\d+\?v=\d+$/.test(avatarUrl), avatarUrl);
  check('头像写入留痕', Number((await getDb().get("SELECT COUNT(*) AS n FROM `user_profile_log` WHERE `field` = 'avatar'")).n) >= 1);

  const fetched = await fetch(base + avatarUrl);
  const fetchedBytes = fetched.status === 200 ? Buffer.from(await fetched.arrayBuffer()) : null;
  check('公开路由（免签名）能取到头像', fetched.status === 200 && fetchedBytes && fetchedBytes.equals(tinyPng), {
    status: fetched.status,
    bytes: fetchedBytes ? fetchedBytes.length : 0,
  });

  const statusWithAvatar = await client.get('/api/v1/auth/status');
  check('status 也带 avatar_url', statusWithAvatar.body.data?.avatar_url === avatarUrl, statusWithAvatar.body.data?.avatar_url);

  const removed = await client.post('/api/v1/auth/avatar', { remove: true });
  check('恢复默认头像', removed.body.code === ERROR.OK && removed.body.data?.user?.avatar_url === null, removed.body.data?.user?.avatar_url);
  const gone = await fetch(base + avatarUrl);
  check('清空后旧地址取不到（404）', gone.status === 404, gone.status);

  section('⑤b 解析仅限会员（默认门禁）');
  const pendingStatus = await client.get('/api/v1/auth/status');
  check('未开通状态可查询', pendingStatus.body.code === ERROR.OK && pendingStatus.body.data.activated === false, pendingStatus.body.data);

  const pendingParse = await client.post('/api/v1/dw/parse', { url: 'https://v.douyin.com/xxxxxxx/' });
  check('未开通会员解析 → 2001', pendingParse.body.code === ERROR.NOT_ACTIVATED, pendingParse.body);
  check('提示是会员功能', String(pendingParse.body.msg).includes('会员'), pendingParse.body.msg);

  // 关掉门禁后应回到「未开通也能用、只是成品带水印」
  await setConfig('parse_member_only', '0', { type: 'bool' });
  const relaxedParse = await client.post('/api/v1/dw/parse', { url: 'https://v.douyin.com/yyyyyyy/' });
  check('parse_member_only=0 时未开通也能解析', relaxedParse.body.code === ERROR.OK, relaxedParse.body);
  check('任务被标记为「需要加水印」', Number((await getDb().get('SELECT `watermark` FROM `dw_task` ORDER BY id DESC LIMIT 1')).watermark) === 1);
  await setConfig('parse_member_only', '1', { type: 'bool' });

  const heartbeatWhilePending = await client.post('/api/v1/auth/heartbeat', {});
  check('未开通状态可心跳', heartbeatWhilePending.body.code === ERROR.OK, heartbeatWhilePending.body.code);

  section('⑥ 后台开通会员');
  const userRow = await findByPhone(phoneA);
  const activated = await activateUser(userRow.id, { days: 30, adminId: null, remark: '自检开通' });
  check('开通后状态=正常', Number(activated.status) === 1, activated.status);
  check('开通后写入到期时间', Boolean(activated.expire_at), activated.expire_at);

  const statusAfter = await client.get('/api/v1/auth/status');
  check('客户端立刻看到已开通', statusAfter.body.code === ERROR.OK && statusAfter.body.data.activated === true, statusAfter.body.data);
  check('到期时间已下发', Boolean(statusAfter.body.data.expire_at), statusAfter.body.data?.expire_at);

  const memberParse = await client.post('/api/v1/dw/parse', { url: 'https://v.douyin.com/yyyyyyy/' });
  check('开通会员后照常可用', memberParse.body.code === ERROR.OK, memberParse.body);
  check('会员的任务不再标记加水印', Number((await getDb().get('SELECT `watermark` FROM `dw_task` ORDER BY id DESC LIMIT 1')).watermark) === 0);

  section('⑦ 一码一账号与注册边界');
  const otherDevice = new ApiClient(base, { masterKey, deviceId: 'smoke-device-BBBB2222' });
  const reuseInvite = await otherDevice.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[0], username: 'reuse_invite', phone: '13800000009', password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('同一邀请码第二次注册 → 拒绝', reuseInvite.body.code !== ERROR.OK && /已被使用/.test(reuseInvite.body.msg), reuseInvite.body);

  const dupPhone = await otherDevice.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[1], username: 'dup_phone_user', phone: phoneA, password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('手机号已注册 → 拒绝', dupPhone.body.code === ERROR.PARAM && /已注册/.test(dupPhone.body.msg), dupPhone.body);

  const mismatch = await otherDevice.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[1], username: 'pwd_mismatch', phone: '13800000002', password: passwordA, password_confirm: 'other-pass' },
    { key: 'root' },
  );
  check('两次密码不一致 → 拒绝', mismatch.body.code === ERROR.PARAM && /不一致/.test(mismatch.body.msg), mismatch.body);

  const badCode = await otherDevice.post(
    '/api/v1/auth/register',
    { invite_code: 'ZZZZZZZZZZZZ', username: 'bad_code_user', phone: '13800000003', password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('不存在的邀请码 → 拒绝', badCode.body.code === ERROR.PARAM, badCode.body);

  const shortPwd = await otherDevice.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[1], username: 'short_pwd_user', phone: '13800000004', password: '123', password_confirm: '123' },
    { key: 'root' },
  );
  check('密码过短 → 拒绝', shortPwd.body.code === ERROR.PARAM, shortPwd.body);

  const second = await otherDevice.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[1], username: 'second_user', phone: '13800000002', password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  check('第二个邀请码可正常注册', second.body.code === ERROR.OK, second.body);
  check('一个码只建一个账号（库内校验）', Number((await getDb().get('SELECT COUNT(*) AS n FROM `invite_code` WHERE `status` = 1')).n) === 2);

  // 现在库里已经有别人了，用它验证「用户名不能重复」（忽略大小写）
  const dupName = await client.post('/api/v1/auth/profile', { username: 'SECOND_USER' });
  check('改成别人的用户名 → 拒绝（忽略大小写）', dupName.body.code === ERROR.PARAM && /已被使用/.test(dupName.body.msg), dupName.body);
  check('改用户名后仍是自己的名字', (await getDb().get('SELECT `username` FROM `app_user` WHERE `phone` = ?', [phoneA]))?.username === '改名后的我');

  section('⑧ 会话与签名异常');
  const badSign = await client.get('/api/v1/auth/status', { badSign: true });
  check('签名错误 → 1002', badSign.body.code === ERROR.SIGN, badSign.body);

  const oldTs = Math.floor(Date.now() / 1000) - 3600;
  const expired = await client.get('/api/v1/auth/status', { timestamp: oldTs });
  check('时间戳超窗 → 1003', expired.body.code === ERROR.TIMESTAMP, expired.body);

  const fixedNonce = 'smoke-nonce-000000000001';
  const first = await client.get('/api/v1/auth/status', { nonce: fixedNonce });
  const replay = await client.get('/api/v1/auth/status', { nonce: fixedNonce });
  check('首次请求通过', first.body.code === ERROR.OK, first.body);
  check('同 nonce 重放 → 1003', replay.body.code === ERROR.TIMESTAMP, replay.body);

  const noToken = new ApiClient(base, { masterKey, deviceId: deviceA });
  const unauth = await noToken.get('/api/v1/auth/status', { key: 'root' });
  check('缺少会话凭证 → 2005', unauth.body.code === ERROR.SESSION_INVALID, unauth.body);

  section('⑨ 握手换发会话密钥');
  const handshake = await client.post('/api/v1/auth/handshake', { device_id: deviceA }, { key: 'root' });
  check('握手成功', handshake.body.code === ERROR.OK && Boolean(handshake.body.data.sign_key), handshake.body);
  const newKey = handshake.body.data?.sign_key;
  check('新密钥与旧密钥不同', Boolean(newKey) && newKey !== register.body.data.sign_key);

  client.signKey = newKey;
  const afterHandshake = await client.get('/api/v1/auth/status');
  check('新密钥可用', afterHandshake.body.code === ERROR.OK, afterHandshake.body);

  section('⑩ 卡密接口已下线');
  const legacy = await new ApiClient(base, { masterKey, deviceId: deviceA }).post(
    '/api/v1/auth/activate',
    { code: 'ABCDEFGHJKLMNPQR' },
    { key: 'root' },
  );
  check('旧卡密激活接口返回 4004 且提示改用邀请码', legacy.body.code === ERROR.NOT_FOUND && /邀请码/.test(legacy.body.msg), legacy.body);

  section('⑪ 设备与解绑');
  const unbind = await client.post('/api/v1/auth/unbind', { device_id: deviceA });
  check('解绑当前设备', unbind.body.code === ERROR.OK, unbind.body);

  const afterUnbind = await client.get('/api/v1/auth/status');
  check('解绑后会话失效 → 2005', afterUnbind.body.code === ERROR.SESSION_INVALID, afterUnbind.body);

  section('⑫ 数据落库');
  const users = await getDb().get('SELECT COUNT(*) AS n FROM `app_user`');
  check('账号已落库（2 个）', Number(users.n) === 2, users);
  const hashed = await getDb().get('SELECT `password_hash` FROM `app_user` WHERE `phone` = ?', [phoneA]);
  check('密码以哈希存储（非明文）', String(hashed.password_hash).startsWith('scrypt$'), String(hashed.password_hash).slice(0, 12));
  const sessions = await getDb().get('SELECT COUNT(*) AS n FROM `license_session`');
  check('会话已落库', Number(sessions.n) >= 2, sessions);

  section('结果');
  console.log(`  通过 ${passed} 项，失败 ${failed} 项`);
  if (failed) console.log(`  失败项：${failures.join('、')}`);

  server.close();
  await closeKv();
  await closeDb();
  process.exit(failed ? 1 : 0);
}

main().catch(async (err) => {
  console.error('自检异常：', err);
  await closeDb().catch(() => {});
  process.exit(1);
});
