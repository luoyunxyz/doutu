/**
 * DTW/1 私有协议自检。
 *
 *   node scripts/dtw-test.js
 *
 * 覆盖：
 *   ① 协议自检与握手：hello 拿挑战 → 客户端验服务端签名 → open 提交慢哈希证明 → 拿会话
 *   ② 隧道：加密请求 → 业务响应解密还原（状态码 / Content-Type / 信封都对）
 *   ③ 防重放：同一个 challenge 不能握手两次
 *   ④ 防篡改：改一个字节、换别人的会话密钥、过期时间戳，都要被拒
 *   ⑤ 慢哈希证明：错的 proof 过不去
 *   ⑥ 强制模式：开了 DTW_REQUIRE 之后明文 /api 被拒（白名单与握手端点放行）
 *
 * 环境变量必须在 import 业务模块前设好。
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const ROOT = path.resolve(import.meta.dirname, '..');
const DB_FILE = path.join(ROOT, 'data', 'dtw-test.db');

process.env.DB_DIALECT = 'sqlite';
process.env.LOG_LEVEL = 'warn';
process.env.SQLITE_FILE = DB_FILE;

// 测试专用协议密钥（每次跑都新生成，不落盘）
const { privateKey, publicKey } = crypto.generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});
process.env.DTW_ENABLED = 'true';
process.env.DTW_REQUIRE = '1';
process.env.DTW_PRIVATE_KEY = privateKey.trim().replace(/\n/g, '\\n');
process.env.DTW_SALT = 'test-salt-0123456789abcdef';
process.env.DTW_SALT_ID = 'stest';
process.env.DTW_PROOF_ITER = '2000';

for (const f of [DB_FILE, `${DB_FILE}-wal`, `${DB_FILE}-shm`]) fs.rmSync(f, { force: true });

const { openDb, initSchema, closeDb } = await import('../src/db/index.js');
const { openKv, closeKv } = await import('../src/core/kv.js');
const { seedAll } = await import('../src/db/seed.js');
const { buildRouter } = await import('../src/app.js');
const { createServer, listen } = await import('../src/core/http.js');
const dtw = await import('../src/core/dtw.js');
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

const DEVICE = 'dtw-test-device';
const INSTALL = 'install-abcdef0123456789';
const SERVER_PUB = publicKey;

/**
 * 握手请求头：时间戳 + 一次性 nonce + 设备号。
 * 握手不带签名（那正是要被淘汰的东西），但这两样仍然要带 —— 服务端的通用中间件
 * 拿它们做时间窗与重放拦截，协议层再在上面叠 RSA + 一次性 challenge。
 */
const hsHeaders = () => ({
  'Content-Type': 'application/json',
  'X-Timestamp': String(Math.floor(Date.now() / 1000)),
  'X-Nonce': crypto.randomBytes(8).toString('hex'),
  'X-Device-Id': DEVICE,
  'X-App-Version': '1.1.0',
  'X-Version-Code': '2',
});

/* ------------------------- 客户端侧的小实现 ------------------------- */

function clientProof({ salt, challenge, bucket }) {
  return dtw.slowHashHex({ salt, challenge, deviceId: DEVICE, installId: INSTALL, bucket });
}

function rsaSeal(payloadBuf) {
  return crypto
    .publicEncrypt(
      { key: SERVER_PUB, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' },
      payloadBuf,
    )
    .toString('base64');
}

/** 发一次 hello，返回 { data, sigOk } */
async function hello(base) {
  const res = await fetch(base + config.dtw.handshakePath, {
    method: 'POST',
    headers: hsHeaders(),
    body: JSON.stringify({ v: 1, step: 'hello', device_id: DEVICE, install_id: INSTALL }),
  });
  const body = await res.json();
  const d = body.data;
  if (!d) throw new Error('握手 hello 未返回 data：' + JSON.stringify(body).slice(0, 200));
  const payload = [
    `v=${d.v}`,
    `kid=${d.kid}`,
    'sid=',
    `challenge=${d.challenge}`,
    `device=${DEVICE}`,
    `install=${INSTALL}`,
    `salt=${d.salt_id}`,
    `exp=${d.expire_at}`,
  ].join('&');
  return { data: d, sigOk: dtw.verifyServerPayload(payload, d.sig) };
}

/** 用给定 challenge 发一次 open */
async function openWith(base, { salt, challenge, proof, bucket }) {
  const hsKey = crypto.randomBytes(32).toString('hex');
  const inner = JSON.stringify({
    session_key: crypto.randomBytes(32).toString('hex'),
    device_id: DEVICE,
    install_id: INSTALL,
    challenge,
    proof: proof ?? clientProof({ salt, challenge, bucket }),
    bucket,
    ts: Date.now(),
    app_ver: '1.1.0',
  });
  const sealed = dtw.seal(hsKey, inner);
  const res = await fetch(base + config.dtw.handshakePath, {
    method: 'POST',
    headers: hsHeaders(),
    body: JSON.stringify({
      v: 1,
      step: 'open',
      ek: rsaSeal(Buffer.from(hsKey, 'hex')),
      // 与真实客户端一致：base64 字符串（不是 Buffer）——这样才覆盖得到解码那一步
      iv: sealed.iv.toString('base64'),
      ct: sealed.ct.toString('base64'),
      tag: sealed.tag.toString('base64'),
    }),
  });
  return res.json();
}

/** 完整握手一次，返回 { sigOk, hello, open, sid, key } */
async function handshake(base) {
  const h = await hello(base);
  const bucket = dtw.bucketOf(Date.now());
  const hsKey = crypto.randomBytes(32).toString('hex');
  const sessionKey = crypto.randomBytes(32).toString('hex');
  const inner = JSON.stringify({
    session_key: sessionKey,
    device_id: DEVICE,
    install_id: INSTALL,
    challenge: h.data.challenge,
    proof: clientProof({ salt: h.data.salt, challenge: h.data.challenge, bucket }),
    bucket,
    ts: Date.now(),
    app_ver: '1.1.0',
  });
  const sealed = dtw.seal(hsKey, inner);
  const res = await fetch(base + config.dtw.handshakePath, {
    method: 'POST',
    headers: hsHeaders(),
    body: JSON.stringify({
      v: 1,
      step: 'open',
      ek: rsaSeal(Buffer.from(hsKey, 'hex')),
      iv: sealed.iv.toString('base64'),
      ct: sealed.ct.toString('base64'),
      tag: sealed.tag.toString('base64'),
    }),
  });
  const body = await res.json();
  return { sigOk: h.sigOk, hello: h.data, open: body, sid: body.data?.sid, key: sessionKey };
}

/** 走一次隧道请求，返回 { httpStatus, status, contentType, json } */
async function tunnel(base, { sid, key }, seq, { method = 'POST', pathname, body = {}, rawBody = null, headers = null }) {
  const rb = rawBody === null ? JSON.stringify(body) : rawBody;
  const payload = JSON.stringify({
    m: method,
    p: pathname,
    q: {},
    b: body,
    rb,
    t: Date.now(),
    // 业务层的验签/时间窗头也在协议里带过去（它们本来就是客户端自设的头）
    h: {
      'x-timestamp': String(Math.floor(Date.now() / 1000)),
      'x-nonce': crypto.randomBytes(8).toString('hex'),
      'x-device-id': DEVICE,
      ...(headers || {}),
    },
  });
  const frame = dtw.encodeFrame({ sid, seq, keyHex: key, payload });
  const res = await fetch(base + config.dtw.tunnelPath, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: frame,
  });
  const buf = Buffer.from(await res.arrayBuffer());
  const opened = dtw.decodeFrame(buf, key);
  return {
    httpStatus: res.status,
    status: opened.s,
    contentType: opened.ct,
    json: JSON.parse(Buffer.from(opened.b, 'base64').toString('utf8')),
  };
}

async function main() {
  const db = await openDb({ dialect: 'sqlite' });
  await initSchema(db);
  await seedAll(db);
  await openKv();

  const server = createServer(buildRouter(), { bodyLimit: 2 * 1024 * 1024 });
  const address = await listen(server);
  const base = `http://127.0.0.1:${address.port}`;
  const guest = new ApiClient(base, { masterKey: config.security.masterKey, deviceId: DEVICE });

  console.log('\n① 协议自检与握手');
  const info = await guest.get('/api/v1/dtw/info', { key: 'root' });
  ok('协议已启用', info.body.data.enabled === true, JSON.stringify(info.body.data));
  ok('公钥指纹与服务端一致', info.body.data.key_fingerprint === dtw.keyFingerprint(), String(info.body.data.key_fingerprint));
  ok('声明强制私有协议', info.body.data.required === true);

  const hs = await handshake(base);
  ok('hello 返回挑战且签名可验', hs.sigOk === true, JSON.stringify(hs.hello).slice(0, 120));
  ok('open 建立会话', hs.open.code === 0 && Boolean(hs.sid), JSON.stringify(hs.open).slice(0, 160));

  console.log('\n② 隧道请求 / 响应');
  const ping = await tunnel(base, hs, 1, { method: 'GET', pathname: '/api/v1/time', body: {}, rawBody: '' });
  ok('HTTP 帧本身是 200', ping.httpStatus === 200, String(ping.httpStatus));
  ok('业务状态码还原正确', ping.status === 200, String(ping.status));
  ok('Content-Type 还原正确', String(ping.contentType).includes('json'), String(ping.contentType));
  ok('业务响应是 JSON 信封', typeof ping.json.code === 'number', JSON.stringify(ping.json).slice(0, 120));

  const cfg = await tunnel(base, hs, 2, { method: 'GET', pathname: '/api/v1/config', body: {}, rawBody: '' });
  ok('隧道里能拿到公开配置', cfg.json.code === 0 && Boolean(cfg.json.data), JSON.stringify(cfg.json).slice(0, 120));

  console.log('\n③ 防重放：challenge 一次性');
  const rh = await hello(base);
  const once = await openWith(base, { salt: rh.data.salt, challenge: rh.data.challenge, bucket: dtw.bucketOf(Date.now()) });
  ok('第一次 open 成功', once.code === 0, JSON.stringify(once).slice(0, 140));
  const twice = await openWith(base, { salt: rh.data.salt, challenge: rh.data.challenge, bucket: dtw.bucketOf(Date.now()) });
  ok('同一个 challenge 不能握手两次', twice.code !== 0, JSON.stringify(twice).slice(0, 140));

  console.log('\n④ 防篡改');
  const goodFrame = dtw.encodeFrame({
    sid: hs.sid,
    seq: 10,
    keyHex: hs.key,
    payload: JSON.stringify({ m: 'GET', p: '/api/v1/time', q: {}, b: {}, rb: '', t: Date.now() }),
  });
  const tampered = Buffer.from(goodFrame);
  tampered[40] = tampered[40] ^ 0xff;
  const badRes = await fetch(base + config.dtw.tunnelPath, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: tampered,
  });
  ok('改一个字节的帧被拒', (await badRes.json()).code !== 0);

  const wrongKeyFrame = dtw.encodeFrame({
    sid: hs.sid,
    seq: 11,
    keyHex: crypto.randomBytes(32).toString('hex'),
    payload: JSON.stringify({ m: 'GET', p: '/api/v1/time', q: {}, b: {}, rb: '', t: Date.now() }),
  });
  const wrongRes = await fetch(base + config.dtw.tunnelPath, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: wrongKeyFrame,
  });
  ok('用错会话密钥的帧被拒', (await wrongRes.json()).code !== 0);

  const staleFrame = dtw.encodeFrame({
    sid: hs.sid,
    seq: 12,
    keyHex: hs.key,
    payload: JSON.stringify({ m: 'GET', p: '/api/v1/time', q: {}, b: {}, rb: '', t: Date.now() - 3600000 }),
  });
  const staleRes = await fetch(base + config.dtw.tunnelPath, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: staleFrame,
  });
  ok('时间戳过期的帧被拒', (await staleRes.json()).code !== 0);

  console.log('\n⑤ 慢哈希证明');
  const h5 = await hello(base);
  const badProof = await openWith(base, {
    salt: h5.data.salt,
    challenge: h5.data.challenge,
    proof: 'deadbeef'.repeat(8),
    bucket: dtw.bucketOf(Date.now()),
  });
  ok('错误的慢哈希证明被拒', badProof.code !== 0, JSON.stringify(badProof).slice(0, 140));

  console.log('\n⑥ 强制私有协议：明文被拒、白名单放行');
  const plain = await fetch(base + '/api/v1/config', { method: 'GET' });
  ok('明文 /api 请求被拒（426）', plain.status === 426, String(plain.status));
  const health = await fetch(base + '/healthz');
  ok('白名单路径放行（/healthz）', health.status === 200, String(health.status));
  const hs2 = await hello(base);
  ok('握手端点放行', Boolean(hs2.data.challenge));

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
