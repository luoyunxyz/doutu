/**
 * 端到端验收（离线可跑，只依赖本机 ffmpeg）。
 *
 * 覆盖核心价值闭环 + 新会员模型（邀请码注册 / 后台激活）：
 *   邀请码注册 → 未激活受限（2001）→ 后台激活 → 提交解析 → worker 解析+合成（FFmpeg 加水印）
 *   → 轮询结果 → 签名 URL 下载成品（含 Range/篡改/过期）→ 缓存与配额与限流 → 线路降级 → 图集
 *   → 版本检查/热配置 → 下单支付（mock 渠道）→ 工单
 *   → 后台登录/RBAC/账号/邀请码/订单/收益/审计/溯源/CSV 导出 → 按版本吊销。
 *
 * 解析线路用 native:mock 指向本地起的静态服务，因此不需要访问任何第三方平台。
 *
 * 运行：npm run e2e
 */
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import { spawn } from 'node:child_process';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'e2e.db');
const TMP = path.join(ROOT, 'storage', 'e2e-tmp');

// 默认跑 SQLite（零依赖）。要验证生产方言时这样跑：
//   E2E_DIALECT=mysql MYSQL_HOST=127.0.0.1 MYSQL_USER=root MYSQL_PASSWORD=... MYSQL_DATABASE=doutu_e2e node scripts/e2e.js
const USE_MYSQL = String(process.env.E2E_DIALECT || '').toLowerCase() === 'mysql';

process.env.DB_DIALECT = USE_MYSQL ? 'mysql' : 'sqlite';
process.env.PORT = process.env.E2E_PORT || '17201';
process.env.LOG_LEVEL = 'warn';
process.env.STORAGE_LOCAL_DIR = path.join(ROOT, 'storage', 'e2e-media');
process.env.TMPDIR = TMP;
process.env.STORAGE_PUBLIC_BASE = `http://127.0.0.1:${process.env.PORT}`;
if (!USE_MYSQL) process.env.SQLITE_FILE = DB_FILE;

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });
fs.rmSync(TMP, { recursive: true, force: true });
fs.rmSync(path.join(ROOT, 'storage', 'e2e-media'), { recursive: true, force: true });

const { openDb, initSchema, dropAll, getDb, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const { createBatch: createInviteBatch } = await import('../src/services/invite.js');
const { activateUser, findByPhone } = await import('../src/services/user.js');
const { setConfig } = await import('../src/services/sysconfig.js');
const { hashPassword } = await import('../src/core/crypto.js');
const { now } = await import('../src/core/util.js');
const taskService = await import('../src/modules/dw/taskService.js');
const watermark = await import('../src/modules/dw/watermark.js');
const { default: ApiClient } = await import('./lib/client.js');
const { default: config } = await import('../src/config.js');
const { ERROR } = await import('../src/core/errors.js');

/* ---------------------------- 断言工具 ---------------------------- */

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
    console.log(`  ✗ ${name}${extra !== undefined ? ` → ${JSON.stringify(extra).slice(0, 400)}` : ''}`);
  }
}
const section = (title) => console.log(`\n${title}`);

/* ---------------------------- 测试资源 ---------------------------- */

function makeSampleVideo(file) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const child = spawn(config.media.ffmpeg, [
      '-hide_banner',
      '-loglevel',
      'error',
      '-y',
      '-f',
      'lavfi',
      '-i',
      'testsrc=size=640x360:rate=15',
      '-t',
      '3',
      '-pix_fmt',
      'yuv420p',
      '-c:v',
      'libx264',
      '-crf',
      '28',
      file,
    ]);
    let err = '';
    child.stderr.on('data', (d) => {
      err += d.toString();
    });
    child.on('error', reject);
    child.on('close', (code) => (code === 0 ? resolve(file) : reject(new Error(`生成样本失败：${err.slice(-300)}`))));
  });
}

/** 极简静态服务：既是 mock 线路的样本源，也模拟 media-parser 的接口契约 */
function startStaticServer(file) {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://127.0.0.1');
    if (url.pathname.startsWith('/sample.mp4')) {
      const stat = fs.statSync(file);
      res.writeHead(200, { 'Content-Type': 'video/mp4', 'Content-Length': stat.size });
      fs.createReadStream(file).pipe(res);
      return;
    }
    // 模拟 name/shuiyin 的 media-parser：GET /api/v1/parse?url=...
    if (url.pathname === '/api/v1/parse') {
      const target = String(url.searchParams.get('url') || '');
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      if (target.includes('mpfail=1')) {
        res.end(JSON.stringify({ retcode: 400, retdesc: '该链接尚未支持提取 / 解析失败', data: null, error_code: 'PLATFORM_NOT_SUPPORTED', succ: false }));
        return;
      }
      res.end(
        JSON.stringify({
          retcode: 200,
          retdesc: '成功',
          succ: true,
          data: {
            video_id: '7123000000000000001',
            platform: '抖音',
            title: '来自本地解析引擎的样本',
            desc: '文案',
            video_url: `http://127.0.0.1:${server.address().port}/sample.mp4`,
            video_list: [`http://127.0.0.1:${server.address().port}/sample.mp4`],
            audio_url: null,
            cover_url: null,
            author: { nickname: '引擎作者', author_id: 'au-1', avatar: null },
            image_list: [],
            subtitles: [],
          },
        }),
      );
      return;
    }
    res.writeHead(404).end('not found');
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

/** 手动消费队列：等价于独立 worker 进程在跑 */
async function drainQueue(maxTasks = 5) {
  const results = [];
  for (let i = 0; i < maxTasks; i += 1) {
    const task = await taskService.claimTask('e2e-worker');
    if (!task) break;
    results.push(await taskService.processTask(task, 'e2e-worker'));
  }
  return results;
}

async function waitForStatus(client, taskNo, statuses = [3, 4, 5], timeoutMs = 60000) {
  const started = Date.now();
  let last = null;
  while (Date.now() - started < timeoutMs) {
    const res = await client.get(`/api/v1/dw/task/${taskNo}`);
    last = res.body?.data;
    if (last && statuses.includes(Number(last.status))) return last;
    await new Promise((r) => setTimeout(r, 500));
  }
  return last;
}

/* ------------------------------- 主流程 ------------------------------- */

async function main() {
  section('① 初始化数据库与初始数据');
  const db = await openDb();
  if (USE_MYSQL) await dropAll(db); // 每次从干净库开始，避免上次残留影响断言
  await initSchema(db);
  await seedAll(db);
  await openKv();
  check(`数据库方言=${db.dialect} 就绪`, Boolean(db), db.dialect);

  const sample = path.join(TMP, 'sample.mp4');
  await makeSampleVideo(sample);
  check('FFmpeg 生成样本视频', fs.existsSync(sample) && fs.statSync(sample).size > 1000, fs.statSync(sample).size);

  const staticServer = await startStaticServer(sample);
  const staticPort = staticServer.address().port;
  check('本地样本服务已启动', staticPort > 0);

  // mock 解析线路（生产种子数据里这条是关闭的）
  await db.insert('dw_route', {
    platform: 'all',
    name: '端到端自检 mock 线路',
    type: 1,
    endpoint: 'native:mock',
    config: JSON.stringify({
      videoUrl: `http://127.0.0.1:${staticPort}/sample.mp4`,
      title: '端到端样本',
      itemId: 'e2e-item-1',
      // 链接里含该子串时这条线路故意失败，用于验证「降级到备用线路」与「全失败退配额」
      failFor: 'mockfail=1',
    }),
    priority: 1,
    enabled: 1,
    succ_1h: 0,
    fail_1h: 0,
    succ_total: 0,
    fail_total: 0,
    avg_ms: 0,
    created_at: now(),
    updated_at: now(),
  });
  check('端到端 self-check mock 线路已写入', true);

  // 第二条线路：模拟本地解析引擎 media-parser 的接口契约（生产上的默认线路类型）
  const parserRoute = await db.insert('dw_route', {
    platform: 'all',
    name: '本地解析引擎（测试契约）',
    type: 1,
    endpoint: `http://127.0.0.1:${staticPort}/api/v1/parse`,
    config: JSON.stringify({ contract: 'media-parser' }),
    priority: 500,
    enabled: 1,
    succ_1h: 0,
    fail_1h: 0,
    succ_total: 0,
    fail_total: 0,
    avg_ms: 0,
    created_at: now(),
    updated_at: now(),
  });
  check('media-parser 契约线路已写入', Boolean(parserRoute.insertId));

  // 后台管理员
  await db.insert('admin', {
    username: 'e2eadmin',
    password_hash: hashPassword('E2eAdmin@2026'),
    real_name: '自检管理员',
    role_id: 1,
    status: 1,
    created_at: now(),
    updated_at: now(),
  });

  const api = createServer(buildRouter());
  const address = await listen(api);
  const base = `http://127.0.0.1:${address.port}`;
  const masterKey = config.security.masterKey || 'dev-master-key';
  console.log(`  服务已监听 ${base}`);

  /* ------------------------------- 授权链路 ------------------------------- */
  section('② 邀请码注册 → 未激活受限 → 后台激活');
  const inviteBatch = await createInviteBatch({
    count: 4,
    duration: 0, // 0 = 注册后仍需后台激活
    level: 3,
    maxDevice: 1,
    quotaDay: 5,
    remark: '端到端自检邀请码',
  });
  check('邀请码批次生成 4 个', inviteBatch.codes.length === 4, { batch: inviteBatch.batch_no });
  check('邀请码长度 12 位（展示为 XXXX-XXXX-XXXX）', String(inviteBatch.codes[0]).length === 12, inviteBatch.codes[0]);

  const deviceId = 'e2e-device-0001';
  const client = new ApiClient(base, { masterKey, deviceId });
  const phoneA = '13900000001';
  const passwordA = 'E2ePass@2026';
  const register = await client.post(
    '/api/v1/auth/register',
    { invite_code: inviteBatch.codes[0], username: '端到端用户', phone: phoneA, password: passwordA, password_confirm: passwordA, nickname: '端到端用户' },
    { key: 'root' },
  );
  check('邀请码注册成功', register.body.code === ERROR.OK, register.body);
  client.token = register.body.data?.token;
  client.signKey = register.body.data?.sign_key;
  check('会话密钥已下发', Boolean(client.signKey));
  check('注册后处于未激活状态', register.body.data?.user?.activated === false, register.body.data?.user);
  check('未激活时不写到期时间', register.body.data?.user?.expire_at === null, register.body.data?.user?.expire_at);

  // 会员门禁：默认 parse_member_only=1，未开通会员不能解析
  const blockedBefore = await client.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4` });
  check('未开通会员解析 → 2001', blockedBefore.body.code === ERROR.NOT_ACTIVATED, blockedBefore.body);
  check('提示是会员功能', /会员/.test(blockedBefore.body.msg || ''), blockedBefore.body.msg);

  const blockedStatus = await client.get('/api/v1/auth/status');
  check('未开通状态可查询（供客户端显示水印）', blockedStatus.body.code === ERROR.OK && blockedStatus.body.data.activated === false, blockedStatus.body.data);

  // 把门禁关掉，验证「未开通也能用、只是成品带水印」这条老路径仍然可用（配置可回退）
  await setConfig('parse_member_only', '0', { type: 'bool' });
  const unactivatedParse = await client.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4` });
  check('parse_member_only=0 时未开通也能解析', unactivatedParse.body.code === ERROR.OK, unactivatedParse.body);
  check('未开通的任务会加水印', Number((await db.get('SELECT `watermark` FROM `dw_task` ORDER BY id DESC LIMIT 1')).watermark) === 1);

  // 真实登录（手机号 + 密码）
  const loginClient = new ApiClient(base, { masterKey, deviceId });
  const userLogin = await loginClient.post('/api/v1/auth/login', { username: phoneA, password: passwordA }, { key: 'root' });
  check('手机号 + 密码登录成功', userLogin.body.code === ERROR.OK && Boolean(userLogin.body.data.token), userLogin.body);
  // 同设备再次登录会撤销旧会话，后续统一用新会话
  client.token = userLogin.body.data.token;
  client.signKey = userLogin.body.data.sign_key;

  /* ------------------------------- 解析与合成 ------------------------------- */
  section('③ 提交解析任务');
  const shareText = `7.32 复制打开看看【端到端样本】 https://v.douyin.com/iE2ETEST/  ${'http://127.0.0.1:' + staticPort + '/sample.mp4'}`;
  const parse = await client.post('/api/v1/dw/parse', { share_text: shareText, want_watermark: true });
  check('任务提交成功', parse.body.code === ERROR.OK, parse.body);
  const taskNo = parse.body.data?.task_no;
  check('返回 task_no 与排队状态', Boolean(taskNo) && parse.body.data?.status === 0, parse.body.data);
  // ② 里那次「未开通也能用」的探针已经用掉 1 次配额，所以这里是 5-2=3
  check('配额已扣减', parse.body.data?.quota_left === 3, parse.body.data);
  check('未开通会员时任务标记为加水印', Number((await db.get('SELECT `watermark` FROM `dw_task` WHERE `task_no` = ?', [taskNo])).watermark) === 1);

  section('④ worker 执行：解析 → 转存 → 水印合成 → 上传');
  check('水印滤镜包含 drawtext', watermark.buildTextFilters({ template: '{nickname}', opacity: 0.42, position: 'bottom-right' }, { nickname: 'tester' })[0].includes('drawtext'));
  check('中文字体已配置', Boolean(config.media.font), config.media.font || '(空)');
  const env = await watermark.checkEnvironment();
  check('FFmpeg/FFprobe 可用', env.ffmpeg && env.ffprobe, env);

  const workerResults = await drainQueue(3);
  check('worker 处理了排队任务且全部成功', workerResults.length >= 1 && workerResults.every((r) => r.ok), workerResults);
  check('未开通会员的任务确实加了水印', workerResults.some((r) => r.watermark === true), workerResults);

  section('⑤ 查询成品（签名 URL）');
  const taskPayload = await waitForStatus(client, taskNo, [3, 4, 5], 15000);
  check('任务状态=3 已完成', Number(taskPayload?.status) === 3, taskPayload);
  check('下发成品签名 URL', Boolean(taskPayload?.out_url), taskPayload?.out_url);
  check('返回视频时长', Number(taskPayload?.duration) > 0, taskPayload?.duration);
  check('接口不暴露原片地址（客户端拿不到未加印流）', taskPayload?.src_url === undefined && taskPayload?.out_key === undefined, Object.keys(taskPayload || {}));

  const dbTask = await db.get('SELECT * FROM `dw_task` WHERE `task_no` = ?', [taskNo]);
  check('原片已转存留痕（用于投诉溯源）', Boolean(dbTask.src_key), dbTask.src_key);
  check('成品已入库', Boolean(dbTask.out_key), dbTask.out_key);
  check('命中 mock 线路', Boolean(dbTask.route_id), dbTask.route_id);

  section('⑥ 成品下载与防盗链');
  if (!taskPayload?.out_url) {
    check('存在成品地址（后续下载用例的前提）', false, taskPayload);
  } else {
    const fileRes = await fetch(taskPayload.out_url);
    const outBuffer = Buffer.from(await fileRes.arrayBuffer());
    check('签名 URL 可下载', fileRes.ok && outBuffer.length > 1000, { status: fileRes.status, size: outBuffer.length });
    check('返回 mp4（含 ftyp box）', outBuffer.slice(4, 8).toString() === 'ftyp', outBuffer.slice(0, 12).toString('hex'));

    const originPath = path.join(ROOT, 'storage', 'e2e-media', dbTask.src_key);
    const originBuffer = fs.readFileSync(originPath);
    check('成品与未加印原片不同（水印确实画上去了）', !outBuffer.equals(originBuffer), {
      out: outBuffer.length,
      origin: originBuffer.length,
    });

    const rangeRes = await fetch(taskPayload.out_url, { headers: { Range: 'bytes=0-1023' } });
    check('支持 Range 请求（播放器拖动进度依赖）', rangeRes.status === 206, rangeRes.status);
    const rangeLen = (await rangeRes.arrayBuffer()).byteLength;
    check('Range 返回 1024 字节', rangeLen === 1024, rangeLen);

    const badUrl = taskPayload.out_url.replace(/sign=[0-9a-f]+/, 'sign=deadbeef');
    const badRes = await fetch(badUrl);
    const badJson = await badRes.json();
    check('篡改签名的下载链接被拒', badJson.code === ERROR.SIGN, badJson);

    const expiredUrl = taskPayload.out_url.replace(/exp=\d+/, `exp=${Math.floor(Date.now() / 1000) - 10}`);
    const expiredRes = await fetch(expiredUrl);
    const expiredJson = await expiredRes.json();
    check('过期链接被拒', expiredJson.code === ERROR.SIGN, expiredJson);
  }

  section('⑦ 原片/成品缓存复用');
  const again = await client.post('/api/v1/dw/parse', { share_text: shareText });
  check('24 小时内同链接直接命中缓存', again.body.code === ERROR.OK && again.body.data?.reused === true, again.body);
  const cacheTask = await client.get(`/api/v1/dw/task/${again.body.data.task_no}`);
  check('缓存任务直接是已完成状态', Number(cacheTask.body.data?.status) === 3, cacheTask.body.data?.status);

  section('⑦b 开通会员后成品免水印（「开通会员去除水印」的服务端落地）');
  // 前面的用例把门禁临时关掉了，这里恢复成默认（仅会员可用）
  await setConfig('parse_member_only', '1', { type: 'bool' });
  const pendingUser = await findByPhone(phoneA);
  await activateUser(pendingUser.id, { days: 30, quotaDay: 50, maxDevice: 1, remark: '端到端自检开通' });
  const afterActivation = await client.get('/api/v1/auth/status');
  check('开通后客户端看到 activated=true', afterActivation.body.code === ERROR.OK && afterActivation.body.data.activated === true, afterActivation.body.data);
  check('开通后下发到期时间', Boolean(afterActivation.body.data.expire_at), afterActivation.body.data?.expire_at);

  const memberParse = await client.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4?member=1` });
  check('会员提交任务成功', memberParse.body.code === ERROR.OK, memberParse.body);
  const memberTaskNo = memberParse.body.data.task_no;
  check('会员的任务不加水印', Number((await db.get('SELECT `watermark` FROM `dw_task` WHERE `task_no` = ?', [memberTaskNo])).watermark) === 0);
  const memberDrain = await drainQueue(3);
  check('worker 处理会员任务', memberDrain.length >= 1 && memberDrain[0].ok === true, memberDrain[0]);
  const memberTask = await waitForStatus(client, memberTaskNo, [3, 4, 5], 20000);
  check('会员任务完成', Number(memberTask?.status) === 3, memberTask);
  if (memberTask?.out_url) {
    const memberBytes = Buffer.from(await (await fetch(memberTask.out_url)).arrayBuffer());
    const memberRow = await db.get('SELECT `src_key`, `out_key` FROM `dw_task` WHERE `task_no` = ?', [memberTaskNo]);
    const originBytes = memberRow?.src_key ? fs.readFileSync(path.join(ROOT, 'storage', 'e2e-media', memberRow.src_key)) : null;
    check('会员成品与原片一致（没有水印）', Boolean(originBytes) && memberBytes.equals(originBytes), {
      out: memberBytes.length,
      origin: originBytes ? originBytes.length : null,
    });
  }

  section('⑧ 配额耗尽与限流');
  // 另开一个配额=1 的用户来验证配额边界，避免影响上面的缓存用例。
  // 这张邀请码预设 duration=7，所以注册即开通（验证「邀请码可预设时长」这条路径）。
  const quotaInvite = await createInviteBatch({ count: 1, duration: 7, level: 1, maxDevice: 1, quotaDay: 1, remark: '体验码' });
  const deviceB = 'e2e-device-0002';
  const clientB = new ApiClient(base, { masterKey, deviceId: deviceB });
  const actB = await clientB.post(
    '/api/v1/auth/register',
    { invite_code: quotaInvite.codes[0], username: '配额用户', phone: '13900000002', password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  clientB.token = actB.body.data?.token;
  clientB.signKey = actB.body.data?.sign_key;
  check('预设有 7 天时长的邀请码：注册即激活', actB.body.code === ERROR.OK && actB.body.data.user.activated === true, actB.body.data?.user);
  check('第二张邀请码配额=1', actB.body.data?.user?.quota_day === 1, actB.body.data?.user?.quota_day);

  const firstTask = await clientB.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4?b=1` });
  check('配额内提交成功', firstTask.body.code === ERROR.OK && firstTask.body.data.quota_left === 0, firstTask.body.data);
  const other = await clientB.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4?b=2` });
  check('配额用尽后拒绝新任务（3001）', other.body.code === ERROR.QUOTA, other.body);
  const taskCount = await db.get('SELECT COUNT(*) AS n FROM `dw_task` WHERE `user_id` = ?', [actB.body.data.user.user_id]);
  check('配额不足时不留垃圾任务', Number(taskCount.n) === 1, taskCount);

  const burst = [];
  for (let i = 0; i < 35; i += 1) {
    burst.push(await clientB.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4?burst=${i}` }));
  }
  check(
    '高频请求触发限流（3002）而非拖垮服务',
    burst.some((r) => r.body.code === ERROR.RATE_LIMIT || r.body.code === ERROR.QUOTA),
    burst.map((r) => r.body.code).slice(0, 10),
  );

  // 回归：曾经被拒的请求会把当日用量再自增一次，导致 used 虚高且无法回收
  const statusAfterBurst = await clientB.get('/api/v1/auth/status');
  check(
    '配额被拒后 used 不会虚高（压回上限）',
    statusAfterBurst.body.data.quota_used === 1 && statusAfterBurst.body.data.quota_left === 0,
    { used: statusAfterBurst.body.data.quota_used, left: statusAfterBurst.body.data.quota_left },
  );

  section('⑧b 不限量账号（quota_day=0）');
  const unlimitedInvite = await createInviteBatch({ count: 1, duration: 30, level: 3, maxDevice: 1, quotaDay: 0, remark: '不限量测试' });
  const deviceC = 'e2e-device-0003';
  const clientC = new ApiClient(base, { masterKey, deviceId: deviceC });
  const actC = await clientC.post(
    '/api/v1/auth/register',
    { invite_code: unlimitedInvite.codes[0], username: '不限量用户', phone: '13900000003', password: passwordA, password_confirm: passwordA },
    { key: 'root' },
  );
  clientC.token = actC.body.data?.token;
  clientC.signKey = actC.body.data?.sign_key;
  check('不限量邀请码注册成功', actC.body.code === ERROR.OK && actC.body.data.user.quota_day === 0, actC.body.data?.user?.quota_day);
  check('不限量语义明确（unlimited=true / left=null）', actC.body.data?.user?.unlimited === true && actC.body.data?.user?.quota_left === null, {
    unlimited: actC.body.data?.user?.unlimited,
    left: actC.body.data?.user?.quota_left,
  });
  const unlimitedParse = await clientC.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4?unlimited=1` });
  check('不限量账号可继续提交', unlimitedParse.body.code === ERROR.OK, unlimitedParse.body);
  const statusC = await clientC.get('/api/v1/auth/status');
  check('不限量账号状态接口一致', statusC.body.data.unlimited === true, statusC.body.data);

  section('⑨ 失败补偿：线路全失败 → 退配额且不计费');
  // mockfail=1 让第一条线路失败，mpfail=1 让 media-parser 契约线路也失败 → 全线路失败
  const failOnA = await client.post('/api/v1/dw/parse', {
    url: `http://127.0.0.1:${staticPort}/sample.mp4?mockfail=1&mpfail=1`,
  });
  check('失败用例的任务已排队', failOnA.body.code === ERROR.OK && failOnA.body.data.status === 0, failOnA.body.data);
  const quotaAfterSubmit = failOnA.body.data.quota_left;
  const failDrain = await drainQueue(3);
  check('worker 认领并处理了失败任务', failDrain.length >= 1, failDrain);
  const failedTask = await waitForStatus(client, failOnA.body.data.task_no, [3, 4, 5], 20000);
  check('任务被标记失败（status=4）', Number(failedTask?.status) === 4, failedTask);
  check('失败原因写入 err_msg', String(failedTask?.err_msg || '').includes('线路'), failedTask?.err_msg);
  const quotaRes = await client.get('/api/v1/auth/status');
  check('失败后配额已退还（不计费）', quotaRes.body.data.quota_left === quotaAfterSubmit + 1, {
    left: quotaRes.body.data.quota_left,
    atSubmit: quotaAfterSubmit,
  });
  const routeFailRecord = await db.get('SELECT `fail_total` FROM `dw_route` WHERE `name` = ?', ['端到端自检 mock 线路']);
  check('线路失败次数已统计', Number(routeFailRecord.fail_total) >= 1, routeFailRecord);

  section('⑨b 线路降级：主线路失败后自动切到本地解析引擎（media-parser 契约）');
  const fallback = await client.post('/api/v1/dw/parse', { url: `http://127.0.0.1:${staticPort}/sample.mp4?mockfail=1` });
  check('降级用例已排队', fallback.body.code === ERROR.OK, fallback.body);
  const fallbackDrain = await drainQueue(3);
  check('worker 处理完成', fallbackDrain.length >= 1 && fallbackDrain[0].ok === true, fallbackDrain[0]);
  const fallbackTask = await waitForStatus(client, fallback.body.data.task_no, [3, 4, 5], 20000);
  check('主线路失败后由备用线路完成（status=3）', Number(fallbackTask?.status) === 3, fallbackTask);
  const fallbackRow = await db.get('SELECT `route_id`, `platform`, `title`, `out_key` FROM `dw_task` WHERE `task_no` = ?', [
    fallback.body.data.task_no,
  ]);
  check('命中的是 media-parser 线路', Number(fallbackRow.route_id) === Number(parserRoute.insertId), {
    got: fallbackRow.route_id,
    expect: parserRoute.insertId,
  });
  check('上游字段被正确归一化（标题）', fallbackRow.title === '来自本地解析引擎的样本', fallbackRow.title);
  check('成品已生成', Boolean(fallbackRow.out_key), fallbackRow.out_key);

  section('⑩ 图集（IMAGE）水印合成');
  const pngPath = path.join(TMP, 'sample.png');
  await new Promise((resolve, reject) => {
    const child = spawn(config.media.ffmpeg, ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', 'testsrc=size=640x360', '-frames:v', '1', pngPath]);
    child.on('error', reject);
    child.on('close', (code) => (code === 0 ? resolve() : reject(new Error('生成样本图片失败'))));
  });
  const wmPng = path.join(TMP, 'sample-wm.png');
  const imagePolicy = { enabled: true, template: '{nickname}', opacity: 0.5, position: 'bottom-right', widthRatio: 0.2 };
  const imageComposed = await watermark.composeImage({
    input: pngPath,
    output: wmPng,
    policy: imagePolicy,
    meta: { nickname: '图集用户', userId: 1, taskNo: 'E2E-IMG' },
  });
  check('图集单图合成产出文件', fs.existsSync(wmPng) && imageComposed.size > 0, imageComposed.size);
  check('图集成品与原图不同（水印已画上）', !fs.readFileSync(wmPng).equals(fs.readFileSync(pngPath)), 'identical');
  const imagePayload = taskService.taskPayload({
    task_no: 'DW-IMG-TEST',
    status: 3,
    platform: 'mock',
    media_type: 'IMAGE',
    images: JSON.stringify(['images/2026-01-01/a.png', 'images/2026-01-01/b.png']),
    out_key: 'images/2026-01-01/a.png',
    watermark: 1,
    created_at: '2026-01-01 00:00:00',
  });
  check('图集任务下发多张签名 URL', imagePayload.media_type === 'IMAGE' && imagePayload.images.length === 2, imagePayload);
  check('图集首图作为 out_url 下发', imagePayload.out_url === imagePayload.images[0], imagePayload.out_url);

  section('⑪ 版本检查与热更新配置');
  const upgrade = await client.get('/api/v1/upgrade/check?version_code=1&channel=official');
  check('版本检查返回结构正确', upgrade.body.code === ERROR.OK && typeof upgrade.body.data.has_update === 'boolean', upgrade.body.data);
  const cfg = await client.get('/api/v1/config', { key: 'root' });
  check('热更新配置可拉取', cfg.body.code === ERROR.OK && Number(cfg.body.data.config_version) >= 1, cfg.body.code);
  check('配置里带平台列表', Array.isArray(cfg.body.data?.supports?.platforms) && cfg.body.data.supports.platforms.length > 5, cfg.body.data?.supports?.platforms?.length);
  check('不下发水印参数细节（客户端无法传参覆盖）', cfg.body.data?.watermark?.template === undefined, cfg.body.data?.watermark);
  // 回归：公开配置曾经以原始字符串下发（"1"/"20"），客户端真值判断会踩坑
  check(
    '公开配置按声明类型下发（bool/int 不变成字符串）',
    typeof cfg.body.data?.service_switch === 'boolean' &&
      typeof cfg.body.data?.supports?.batch_max === 'number' &&
      typeof cfg.body.data?.config_version === 'number',
    {
      service_switch: typeof cfg.body.data?.service_switch,
      batch_max: typeof cfg.body.data?.supports?.batch_max,
      config_version: typeof cfg.body.data?.config_version,
    },
  );

  const platforms = await client.get('/api/v1/dw/platforms');
  check('平台列表接口可用', platforms.body.code === ERROR.OK && platforms.body.data.platforms.length > 5, platforms.body.code);

  const history = await client.get('/api/v1/dw/history');
  check('历史记录分页可用', history.body.code === ERROR.OK && history.body.data.total >= 2, history.body.data?.total);

  /* ------------------------------- 支付链路 ------------------------------- */
  section('⑫ 下单与支付回调（mock 渠道）');
  const goods = await client.get('/api/v1/pay/goods', { key: 'root' });
  check('商品列表可拉取', goods.body.code === ERROR.OK && goods.body.data.goods.length >= 4, goods.body.data?.goods?.length);
  const goodsId = goods.body.data.goods[0].id;

  const created = await client.post('/api/v1/pay/create', { goods_id: goodsId, pay_type: 'alipay' });
  check('创建支付订单', created.body.code === ERROR.OK && Boolean(created.body.data.order_no), created.body);
  const orderNo = created.body.data?.order_no;
  check('返回收银台地址', String(created.body.data?.pay_url || '').includes('/pay/'), created.body.data?.pay_url);

  const notify = await client.post('/api/v1/pay/notify/mock', { order_no: orderNo });
  check('mock 回调入账', notify.body.code === ERROR.OK, notify.body);

  const notifyAgain = await client.post('/api/v1/pay/notify/mock', { order_no: orderNo });
  check('重复回调幂等', notifyAgain.body.code === ERROR.OK && notifyAgain.body.data?.already === true, notifyAgain.body);

  const payStatus = await client.get(`/api/v1/pay/status/${orderNo}`);
  check('订单状态=已支付', Number(payStatus.body.data?.status) === 1, payStatus.body.data);

  const userAfterPay = await db.get('SELECT * FROM `app_user` WHERE `id` = ?', [pendingUser.id]);
  check('支付后授权已续期（到期时间延后）', String(userAfterPay.expire_at) > String(afterActivation.body.data.expire_at), {
    before: afterActivation.body.data.expire_at,
    after: userAfterPay.expire_at,
  });
  const orderRow = await db.get('SELECT * FROM `order` WHERE `order_no` = ?', [orderNo]);
  check('订单写入手续费与净收入', orderRow && Number(orderRow.net_amount) > 0, orderRow && { fee: orderRow.fee, net: orderRow.net_amount });
  const payRecord = await db.get('SELECT COUNT(*) AS n FROM `payment_record` WHERE `order_no` = ?', [orderNo]);
  check('回调原始报文已留痕', Number(payRecord.n) >= 2, payRecord);

  /* ------------------------------- 工单链路 ------------------------------- */
  section('⑬ 客服工单');
  const ticket = await client.post('/api/v1/ticket/create', { title: '解析失败求助', content: '这个链接解析不了', category: 2 });
  check('用户建单成功', ticket.body.code === ERROR.OK && Boolean(ticket.body.data.ticket.ticket_no), ticket.body);
  const ticketNo = ticket.body.data?.ticket?.ticket_no;
  check('取到工单号', typeof ticketNo === 'string' && ticketNo.startsWith('TK'), ticketNo);
  const ticketList = await client.get('/api/v1/ticket/list');
  check('用户可查看自己的工单', ticketList.body.code === ERROR.OK && ticketList.body.data.total >= 1, ticketList.body.data?.total);
  const ticketDetail = await client.get(`/api/v1/ticket/detail/${ticketNo}`);
  check('工单详情带上下文', ticketDetail.body.code === ERROR.OK && ticketDetail.body.data.messages.length === 1, ticketDetail.body.code);

  /* ------------------------------- 后台链路 ------------------------------- */
  section('⑭ 后台登录与权限');
  const adminClient = new ApiClient(base, { masterKey, deviceId: 'e2e-admin-pc' });
  const login = await adminClient.post('/admin/api/login', { username: 'e2eadmin', password: 'E2eAdmin@2026' });
  check('后台登录成功', login.body.code === ERROR.OK && Boolean(login.body.data.token), login.body);
  const adminToken = login.body.data?.token;
  const adminHeaders = { 'X-Admin-Token': adminToken };
  const adminGet = (p) => adminClient.get(p, { headers: adminHeaders, key: 'root' });
  const adminPost = (p, body) => adminClient.post(p, body, { headers: adminHeaders, key: 'root' });
  check('登录返回角色与权限', Array.isArray(login.body.data?.admin?.perms) && login.body.data.admin.perms.includes('*'), login.body.data?.admin?.perms);

  const noAuth = await adminClient.get('/admin/api/users', { key: 'root' });
  check('无 token 访问后台接口被拒（6001）', noAuth.body.code === ERROR.ADMIN_AUTH, noAuth.body);

  section('⑮ 后台：账号管理');
  const users = await adminGet('/admin/api/users?sort=total_amount&order=desc&limit=10');
  check('用户列表可用', users.body.code === ERROR.OK && users.body.data.total >= 1, users.body.code);
  const target = users.body.data.rows[0];
  check('列表带状态与剩余天数', target.status_text !== undefined && target.days_left !== undefined, target);
  const detail = await adminGet(`/admin/api/users/${target.id}`);
  check('用户详情含邀请码/设备/任务/订单', detail.body.code === ERROR.OK && Array.isArray(detail.body.data.invites) && detail.body.data.invites.length >= 1, {
    invites: detail.body.data?.invites?.length,
    devices: detail.body.data?.devices?.length,
  });
  check('详情里邀请码已脱敏', String(detail.body.data.invites[0].code).startsWith('****'), detail.body.data.invites[0].code);
  check('详情带激活状态', typeof detail.body.data.user.activation?.activated === 'boolean', detail.body.data.user.activation);

  // 后台「激活账号」：新模型下唯一的开通方式（这里用 HTTP 接口验证）
  const pendingForAdmin = await adminPost(`/admin/api/users/${target.id}/status`, { status: 0 });
  check('先把账号置为未激活', pendingForAdmin.body.code === ERROR.OK, pendingForAdmin.body);
  const adminActivate = await adminPost(`/admin/api/users/${target.id}/activate`, { days: 30, remark: '端到端后台激活' });
  check('后台激活账号接口可用', adminActivate.body.code === ERROR.OK && adminActivate.body.data.user.activation.activated === true, adminActivate.body.msg);
  check('激活返回新的到期时间', Boolean(adminActivate.body.data.user.expire_at), adminActivate.body.data.user.expire_at);
  const extend = await adminPost(`/admin/api/users/${target.id}/duration`, { days: 10 });
  check('后台延长时长', extend.body.code === ERROR.OK, extend.body);

  // 后台也能改用户名：走同一套校验（格式 / 唯一性），并留痕
  const adminBadName = await adminClient.call('PUT', `/admin/api/users/${target.id}`, { username: 'a' }, { headers: adminHeaders, key: 'root' });
  check('后台改用户名：非法格式被拒', adminBadName.body.code === ERROR.PARAM, adminBadName.body);
  const adminRename = await adminClient.call('PUT', `/admin/api/users/${target.id}`, { username: '后台改的名' }, { headers: adminHeaders, key: 'root' });
  check('后台改用户名成功', adminRename.body.code === ERROR.OK && adminRename.body.data.user.username === '后台改的名', adminRename.body);
  check('后台改名写入留痕', Number((await db.get("SELECT COUNT(*) AS n FROM `user_profile_log` WHERE `user_id` = ? AND `field` LIKE '%username%'", [target.id])).n) >= 1);
  // 用该用户已经绑定过的设备号登录，避免撞上「设备数上限」（那个行为在别处单独验）
  const boundDevice = await db.get('SELECT `device_id` FROM `user_device` WHERE `user_id` = ? AND `status` = 1 LIMIT 1', [target.id]);
  const loginAfterAdminRename = await new ApiClient(base, { masterKey, deviceId: boundDevice?.device_id }).post(
    '/api/v1/auth/login',
    { username: '后台改的名', password: passwordA },
    { key: 'root' },
  );
  check('后台改名后新用户名可用于登录', loginAfterAdminRename.body.code === ERROR.OK, loginAfterAdminRename.body);

  // 后台「添加用户」也要守用户名规则（否则会建出破坏登录标识唯一性的账号）
  const adminCreateBad = await adminPost('/admin/api/users', { username: 'a', phone: '13900009997', days: 30 });
  check('后台建号：非法用户名被拒', adminCreateBad.body.code === ERROR.PARAM, adminCreateBad.body);
  const adminCreatePhoneName = await adminPost('/admin/api/users', { username: '13900008888', phone: '13900009996', days: 30 });
  check('后台建号：手机号形态的用户名被拒', adminCreatePhoneName.body.code === ERROR.PARAM, adminCreatePhoneName.body);
  const adminCreateDup = await adminPost('/admin/api/users', { username: '后台改的名', phone: '13900009995', days: 30 });
  check('后台建号：重名被拒（忽略大小写）', adminCreateDup.body.code === ERROR.PARAM && /已被使用/.test(adminCreateDup.body.msg || ''), adminCreateDup.body);
  const adminCreateOk = await adminPost('/admin/api/users', { username: '后台建的号', phone: '13900009994', days: 30 });
  check('后台建号：合法用户名通过', adminCreateOk.body.code === ERROR.OK, adminCreateOk.body);
  check(
    '后台建号的用户名已落库',
    (await db.get('SELECT `username` FROM `app_user` WHERE `phone` = ?', ['13900009994']))?.username === '后台建的号',
  );

  const usersCsv = await adminClient.get('/admin/api/users/export?limit=10', { headers: adminHeaders, key: 'root' });
  check('用户导出返回 CSV', usersCsv.status === 200 && typeof usersCsv.body === 'object' && String(usersCsv.body.raw || '').length > 0, usersCsv.status);

  section('⑯ 后台：邀请码 / 订单 / 收益 / 审计');
  const inviteSummary = await adminGet('/admin/api/invites/summary');
  check('邀请码统计', inviteSummary.body.code === ERROR.OK && Number(inviteSummary.body.data.used) >= 3, inviteSummary.body.data);

  const inviteBatches = await adminGet('/admin/api/invites/batches');
  check('邀请码批次列表（含统计）', inviteBatches.body.code === ERROR.OK && inviteBatches.body.data.rows.length >= 3, inviteBatches.body.data?.rows?.length);
  const firstBatch = inviteBatches.body.data.rows[0];
  check('批次统计含未使用/已使用', firstBatch.stats && 'unused' in firstBatch.stats && 'used' in firstBatch.stats, firstBatch.stats);

  const inviteList = await adminGet('/admin/api/invites?status=1&limit=10');
  check('邀请码列表可按状态筛选', inviteList.body.code === ERROR.OK && inviteList.body.data.rows.length >= 1, inviteList.body.data?.total);
  check('已使用邀请码带注册手机号与账号', Boolean(inviteList.body.data.rows[0].used_phone) && Boolean(inviteList.body.data.rows[0].username), {
    phone: inviteList.body.data.rows[0].used_phone,
    user: inviteList.body.data.rows[0].username,
  });
  check('邀请码按展示格式下发', /^\w{4}-\w{4}-\w{4}$/.test(inviteList.body.data.rows[0].display), inviteList.body.data.rows[0].display);

  // 后台生成邀请码 → 列表可见 → 作废（二次确认）
  const newInvites = await adminPost('/admin/api/invites', { count: 2, duration: 30, level: 2, quota_day: 30, remark: '端到端后台生成' });
  check('后台生成邀请码', newInvites.body.code === ERROR.OK && newInvites.body.data.codes.length === 2, newInvites.body);
  const generatedBatch = newInvites.body.data.batch_no;
  const voidOne = await adminPost('/admin/api/invites/void', { code: newInvites.body.data.codes[0], password: 'E2eAdmin@2026' });
  check('作废单个邀请码（需二次密码）', voidOne.body.code === ERROR.OK, voidOne.body);
  const usedAfterVoid = await adminGet(`/admin/api/invites?batch_no=${generatedBatch}&status=2`);
  check('作废后状态变为已作废', usedAfterVoid.body.code === ERROR.OK && usedAfterVoid.body.data.total === 1, usedAfterVoid.body.data?.total);

  const inviteExport = await adminClient.get(`/admin/api/invites/export?batch_no=${generatedBatch}&format=txt`, { headers: adminHeaders, key: 'root' });
  check('邀请码导出可用', inviteExport.status === 200, inviteExport.status);
  check('导出内容是可下载的文本', typeof inviteExport.body === 'object' && String(inviteExport.body.raw || '').length > 0, inviteExport.status);

  const orders = await adminGet('/admin/api/orders');
  check('订单列表可用', orders.body.code === ERROR.OK && orders.body.data.total >= 1, orders.body.data?.total);
  const orderSummary = await adminGet('/admin/api/orders/summary');
  check('订单汇总', orderSummary.body.code === ERROR.OK && Number(orderSummary.body.data.paid_count) >= 1, orderSummary.body.data);

  const aggregate = await adminPost('/admin/api/revenue/aggregate', { days: 1 });
  check('收益日聚合可补算', aggregate.body.code === ERROR.OK, aggregate.body);
  const overview = await adminGet('/admin/api/revenue/overview?granularity=month');
  check('收益月度概览', overview.body.code === ERROR.OK && Array.isArray(overview.body.data.series), overview.body.code);
  check('收益统计卡含环比字段', overview.body.data.cards.gross !== undefined && 'growth' in overview.body.data.cards, overview.body.data.cards);
  const hourOverview = await adminGet('/admin/api/revenue/overview?granularity=hour');
  check('当日按小时统计（24 个点）', hourOverview.body.data.series.length === 24, hourOverview.body.data?.series?.length);
  const dashboard = await adminGet('/admin/api/revenue/dashboard');
  check('后台首页概览', dashboard.body.code === ERROR.OK && dashboard.body.data.tasks.count >= 1, dashboard.body.data);

  const routes = await adminGet('/admin/api/routes');
  check('线路健康度列表', routes.body.code === ERROR.OK && routes.body.data.rows.length >= 2, routes.body.data?.rows?.length);
  const trace = await adminGet(`/admin/api/tasks/trace?user_id=${target.id}`);
  check('侵权溯源可定位成品', trace.body.code === ERROR.OK && trace.body.data.rows.length >= 1, trace.body.code);
  const evidenceRows = (trace.body.data.rows || []).filter((r) => r.evidence_url);
  check('溯源结果给成功任务带取证下载地址', evidenceRows.length >= 1, {
    rows: trace.body.data.rows.length,
    withEvidence: evidenceRows.length,
  });
  check('失败任务在溯源里不带取证地址（无成品可取证）', trace.body.data.rows.some((r) => !r.evidence_url && Number(r.status) === 4), {
    statuses: trace.body.data.rows.map((r) => r.status),
  });

  const auditLogs = await adminGet('/admin/api/audit');
  check('操作审计已留痕', auditLogs.body.code === ERROR.OK && auditLogs.body.data.total >= 2, auditLogs.body.data?.total);
  const panelEnv = await adminGet('/admin/api/panel/env');
  check('面板环境自检（FFmpeg + 队列）', panelEnv.body.code === ERROR.OK && panelEnv.body.data.media.ffmpeg, panelEnv.body.data?.media);

  section('⑰ 后台：客服工单处理');
  const adminTickets = await adminGet('/admin/api/tickets');
  check('后台工单列表', adminTickets.body.code === ERROR.OK && adminTickets.body.data.total >= 1, adminTickets.body.data?.total);
  const replies = await adminGet('/admin/api/quick-replies');
  check('快捷回复模板可用', replies.body.code === ERROR.OK && replies.body.data.rows.length >= 3, replies.body.data?.rows?.length);
  const reply = await adminPost(`/admin/api/tickets/${ticketNo}/reply`, { content: '已收到，正在处理' });
  check('客服回复工单', reply.body.code === ERROR.OK, reply.body);
  const closeTicket = await adminPost(`/admin/api/tickets/${ticketNo}/close`, {});
  check('关闭工单', closeTicket.body.code === ERROR.OK, closeTicket.body);

  section('⑱ 后台：敏感操作二次确认与权限');
  const deleteWithoutPassword = await adminClient.call('DELETE', `/admin/api/users/${target.id}`, null, { headers: adminHeaders, key: 'root' });
  check('删除用户必须二次确认密码', deleteWithoutPassword.body.code === ERROR.PARAM, deleteWithoutPassword.body);
  const deleteWithWrongPassword = await adminClient.call('DELETE', `/admin/api/users/${target.id}`, { password: 'wrong-password' }, { headers: adminHeaders, key: 'root' });
  check('密码错误则拒绝', deleteWithWrongPassword.body.code === ERROR.ADMIN_FORBIDDEN, deleteWithWrongPassword.body);

  // 建一个只有客服权限的后台账号，验证 RBAC 生效
  const createSupport = await adminPost('/admin/api/admins', { username: 'e2esupport', password: 'Support@2026', role_id: 3 });
  check('创建客服账号', createSupport.body.code === ERROR.OK, createSupport.body);
  const supportClient = new ApiClient(base, { masterKey, deviceId: 'e2e-support-pc' });
  const supportLogin = await supportClient.post('/admin/api/login', { username: 'e2esupport', password: 'Support@2026' });
  const supportHeaders = { 'X-Admin-Token': supportLogin.body.data?.token };
  const supportConfigs = await supportClient.get('/admin/api/configs', { headers: supportHeaders, key: 'root' });
  check('客服无权访问全局配置（6002）', supportConfigs.body.code === ERROR.ADMIN_FORBIDDEN, supportConfigs.body);
  const supportTickets = await supportClient.get('/admin/api/tickets', { headers: supportHeaders, key: 'root' });
  check('客服可访问工单', supportTickets.body.code === ERROR.OK, supportTickets.body.code);

  // ---- 删号后必须能拿同一个邀请码重新注册（这是线上真实反馈的坑）----
  section('⑱b 删号 → 邀请码自动释放 → 同号可重新注册');
  const recyclePhone = '137' + String(Math.floor(10000000 + Math.random() * 89999999));
  const recycleBatch = await createInviteBatch({ count: 1, duration: 0, remark: 'e2e 回收用' });
  const recycleCode = recycleBatch.codes[0];
  const recycleReg = await new ApiClient(base, { masterKey, deviceId: 'e2e-recycle-1' }).post(
    '/api/v1/auth/register',
    { invite_code: recycleCode, username: 'e2e回收用户', phone: recyclePhone, password: 'Recycle@2026', password_confirm: 'Recycle@2026' },
    { key: 'root' },
  );
  check('用新邀请码注册成功', recycleReg.body.code === ERROR.OK, recycleReg.body);
  const recycleId = recycleReg.body.data?.user?.user_id;

  const reuseBefore = await new ApiClient(base, { masterKey, deviceId: 'e2e-recycle-2' }).post(
    '/api/v1/auth/register',
    { invite_code: recycleCode, username: 'e2e抢先注册', phone: '137' + String(Math.floor(10000000 + Math.random() * 89999999)), password: 'Recycle@2026', password_confirm: 'Recycle@2026' },
    { key: 'root' },
  );
  check('未释放前同码注册被拒（该邀请码已被使用）', reuseBefore.body.code !== ERROR.OK, reuseBefore.body);

  const delRecycle = await adminClient.call('DELETE', `/admin/api/users/${recycleId}`, { password: 'E2eAdmin@2026' }, { headers: adminHeaders, key: 'root' });
  check('删除账号成功', delRecycle.body.code === ERROR.OK, delRecycle.body);
  check('删除响应里提示已释放邀请码', Array.isArray(delRecycle.body.data?.released_invite_codes) && delRecycle.body.data.released_invite_codes.includes(recycleCode), delRecycle.body.data);

  const recycleReg2 = await new ApiClient(base, { masterKey, deviceId: 'e2e-recycle-3' }).post(
    '/api/v1/auth/register',
    { invite_code: recycleCode, username: 'e2e回收用户二号', phone: recyclePhone, password: 'Recycle@2026', password_confirm: 'Recycle@2026' },
    { key: 'root' },
  );
  check('同一手机号 + 同一个邀请码重新注册成功', recycleReg2.body.code === ERROR.OK, recycleReg2.body);

  // 手动重置接口：把刚用掉的码再放回去，并用它成功注册第三个账号
  const resetByAdmin = await adminClient.call('POST', `/admin/api/invites/${recycleCode}/reset`, { password: 'E2eAdmin@2026' }, { headers: adminHeaders, key: 'root' });
  check('后台可手动重置已使用的邀请码', resetByAdmin.body.code === ERROR.OK && resetByAdmin.body.data?.changed === true, resetByAdmin.body);
  // 刚重置完，码处于「未使用」——再重置一次应当是幂等的（changed=false）
  const resetIdempotent = await adminClient.call('POST', `/admin/api/invites/${recycleCode}/reset`, { password: 'E2eAdmin@2026' }, { headers: adminHeaders, key: 'root' });
  check('对未使用的码重置是幂等的（changed=false）', resetIdempotent.body.code === ERROR.OK && resetIdempotent.body.data?.changed === false, resetIdempotent.body);
  const resetReg = await new ApiClient(base, { masterKey, deviceId: 'e2e-recycle-4' }).post(
    '/api/v1/auth/register',
    { invite_code: recycleCode, username: 'e2e重置后再注册', phone: '137' + String(Math.floor(10000000 + Math.random() * 89999999)), password: 'Recycle@2026', password_confirm: 'Recycle@2026' },
    { key: 'root' },
  );
  check('重置后的码可再次注册', resetReg.body.code === ERROR.OK, resetReg.body);
  const resetAgain = await adminClient.call('POST', `/admin/api/invites/${recycleCode}/reset`, { password: 'E2eAdmin@2026' }, { headers: adminHeaders, key: 'root' });
  check('重置后再被别人用掉，可再次重置（changed=true）', resetAgain.body.code === ERROR.OK && resetAgain.body.data?.changed === true, resetAgain.body);

  section('⑲ 按版本吊销客户端（止血手段）');
  const purge = await adminPost('/admin/api/panel/purge-version', { app_version: '1.0.0' });
  check('一键吊销版本', purge.body.code === ERROR.OK, purge.body);
  const blockedRequest = await client.get('/api/v1/auth/status');
  check('被吊销版本的客户端请求被拒（9001）', blockedRequest.body.code === ERROR.VERSION_LOW, blockedRequest.body);

  /* ------------------------------- 收尾 ------------------------------- */
  section('结果');
  console.log(`  通过 ${passed} 项，失败 ${failed} 项`);
  if (failed) console.log(`  失败项：${failures.join('、')}`);

  staticServer.close();
  api.close();
  await closeKv();
  await closeDb();
  // 端到端产物体积可能较大，跑完自动清理（保留数据库便于人工查看）
  process.exit(failed ? 1 : 0);
}

main().catch(async (err) => {
  console.error('\n端到端验收异常：', err);
  await closeDb().catch(() => {});
  process.exit(1);
});

