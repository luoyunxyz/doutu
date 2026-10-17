/**
 * 头像上传链路的生产验证（2026-10-17）
 *
 *   node _ios/verify-avatar.mjs
 *
 * iOS 侧这条链是：相册选图 → 居中裁方 → 缩到 320px → JPEG 0.85 → base64
 * → `POST /api/v1/auth/avatar`（需登录签名）→ 服务端回 `user`（含头像 URL）。
 *
 * 本机编译不了 Swift，所以这里用 Node 走**完全相同的协议与签名**打真实生产：
 *   ① 隧道握手（与 verify-dtw-mirror.mjs 同法）
 *   ② 用测试账号登录，拿到 token + sign_key
 *   ③ 按 Swift ApiClient 的规则算 x-sign，上传一张 8×8 的 PNG base64
 *   ④ 校验响应里出现头像字段
 *   ⑤ 调 remove=true 把头像清掉（**不留下痕迹**）
 *
 * 需要凭据时从环境变量读：DOTU_TEST_USER / DOTU_TEST_PASS（默认用测试小号）。
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const BASE = 'https://tdapi.luoyun.xyz';
const HS_PATH = '/api/v1/dtw/hs';
const TUNNEL_PATH = '/api/v1/dtw';
const VERSION = 1;
const APP_VERSION = '1.0.0';
const VERSION_CODE = 10000;
const BUCKET_SEC = 600;
const PEM = fs.readFileSync(path.resolve(import.meta.dirname, '..', '_ops', 'dtw-public.pem'), 'utf8');

const USER = process.env.DOTU_TEST_USER || 'resettester';
const PASS = process.env.DOTU_TEST_PASS || 'newpass9876';

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  [OK]   ${name}`); }
  else { fail += 1; console.log(`  [FAIL] ${name} ${extra}`); }
};

const hex = (buf) => Buffer.from(buf).toString('hex');
const deviceId = hex(crypto.randomBytes(16));
const installId = hex(crypto.randomBytes(16));

function transcript(kid, sid, challenge, device, install, saltId, expireAt) {
  return `v=${VERSION}&kid=${kid}&sid=${sid}&challenge=${challenge}&device=${device}`
    + `&install=${install}&salt=${saltId}&exp=${expireAt}`;
}
const rsaOaep = (plain) => crypto.publicEncrypt({
  key: PEM, padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256',
}, plain);

function sealWith(keyHex, plain, aad) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', Buffer.from(keyHex, 'hex'), iv);
  if (aad) cipher.setAAD(aad);
  const ct = Buffer.concat([cipher.update(plain), cipher.final()]);
  return { iv, ct, tag: cipher.getAuthTag() };
}
function open(keyHex, iv, ct, tag, aad) {
  const d = crypto.createDecipheriv('aes-256-gcm', Buffer.from(keyHex, 'hex'), iv);
  if (aad) d.setAAD(aad);
  d.setAuthTag(tag);
  return Buffer.concat([d.update(ct), d.final()]);
}

async function postJson(step, body) {
  const res = await fetch(BASE + HS_PATH, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8', Accept: 'application/json',
      'X-Device-Id': deviceId, 'X-Timestamp': String(Math.floor(Date.now() / 1000)),
      'X-Nonce': hex(crypto.randomBytes(16)), 'X-App-Version': APP_VERSION,
      'X-Version-Code': String(VERSION_CODE),
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: JSON.parse(text || '{}') };
}

/** 隧道响应的 b 字段是 base64，解出来再解析 JSON（与 Swift DtwTransport.send 一致） */
function unwrap(plain) {
  const outer = JSON.parse(plain.toString('utf8'));
  const b64 = outer.b || '';
  if (!b64) return JSON.stringify(outer);
  return Buffer.from(b64, 'base64').toString('utf8');
}

/** 与 Swift ApiClient 完全一致的 x-sign 规则 */
function sign(signKey, ts, nonce, pathName, rawBody) {
  const bodyHash = crypto.createHash('sha256').update(rawBody).digest('hex');
  const payload = `${ts}\n${nonce}\n${deviceId}\n${pathName}\n${bodyHash}`;
  return crypto.createHmac('sha256', Buffer.from(signKey, 'hex')).update(payload).digest('hex');
}

/** 走隧道发一帧业务请求，返回解析后的响应对象 */
async function tunnel(sessionKey, sid, seq, method, reqPath, query, bodyObj, headers) {
  const raw = bodyObj ? JSON.stringify(bodyObj) : '';
  const payload = JSON.stringify({ m: method, p: reqPath, q: query || {}, t: Date.now(), h: headers, ...(raw ? { rb: raw } : {}) });
  const header = Buffer.alloc(18);
  Buffer.from('DTW1', 'ascii').copy(header, 0);
  header[4] = VERSION;
  header[5] = 0;
  Buffer.from(sid, 'hex').copy(header, 6, 0, 8);
  header.writeInt32BE(seq, 14);
  const sealed = sealWith(sessionKey, Buffer.from(payload), header);
  const frame = Buffer.concat([header, sealed.iv, sealed.ct, sealed.tag]);

  const res = await fetch(BASE + TUNNEL_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: frame,
  });
  if (res.status !== 200) return { status: res.status, plain: null };
  const reply = Buffer.from(await res.arrayBuffer());
  const h2 = reply.subarray(0, 18);
  const plain = open(sessionKey, reply.subarray(18, 30), reply.subarray(30, reply.length - 16),
    reply.subarray(reply.length - 16), h2);
  return { status: 200, plain };
}

async function main() {
  console.log(`账号 ${USER}   设备 ${deviceId.slice(0, 8)}…\n`);

  // ① 握手
  const hello = await postJson('hello', { v: VERSION, step: 'hello', device_id: deviceId, install_id: installId });
  if (hello.status !== 200) { console.log('  [FAIL] hello 失败，终止'); process.exit(1); }
  const data = hello.body.data || {};
  const bucket = Math.floor(Date.now() / 1000 / BUCKET_SEC);
  const material = `${data.salt}|${data.challenge}|${deviceId}|${installId}|${bucket}`;
  const proof = hex(crypto.pbkdf2Sync(Buffer.from(material), Buffer.from(data.salt), Number(data.proof_iter || 20000), 32, 'sha256'));
  const sessionKey = hex(crypto.randomBytes(32));
  const hsKey = hex(crypto.randomBytes(32));
  const inner = JSON.stringify({
    session_key: sessionKey, device_id: deviceId, install_id: installId, challenge: data.challenge,
    proof, bucket, ts: Date.now(), app_ver: APP_VERSION,
  });
  const sealed = sealWith(hsKey, Buffer.from(inner), null);
  const openResp = await postJson('open', {
    v: VERSION, step: 'open', ek: rsaOaep(Buffer.from(hsKey, 'hex')).toString('base64'),
    iv: sealed.iv.toString('base64'), ct: sealed.ct.toString('base64'), tag: sealed.tag.toString('base64'),
  });
  const openData = openResp.body.data || {};
  ok('隧道会话建立', openResp.status === 200 && Boolean(openData.sid));
  const sid = openData.sid;
  let seq = 1;

  // ② 登录
  console.log('\n① 登录（/api/v1/auth/login）');
  const loginHeaders = {
    'x-device-id': deviceId, 'x-timestamp': String(Math.floor(Date.now() / 1000)),
    'x-nonce': hex(crypto.randomBytes(16)), 'x-app-version': APP_VERSION,
    'x-version-code': String(VERSION_CODE), 'x-platform': 'ios',
  };
  const login = await tunnel(sessionKey, sid, seq++, 'POST', '/api/v1/auth/login', {},
    { username: USER, password: PASS }, loginHeaders);
  let loginBody = {};
  try { loginBody = JSON.parse(unwrap(login.plain)); } catch { /* ignore */ }
  ok('登录成功', loginBody.code === 0, JSON.stringify(loginBody).slice(0, 160));
  const token = loginBody.data?.token || '';
  const signKey = loginBody.data?.sign_key || '';
  ok('拿到 token 与 sign_key', Boolean(token && signKey), `token ${token ? '有' : '无'} / sign_key ${signKey ? '有' : '无'}`);
  if (!token || !signKey) {
    console.log('\n登录失败，后续跳过。');
    process.exit(fail === 0 ? 0 : 1);
  }

  // ③ 上传头像（8×8 的 PNG，base64）
  console.log('\n② 上传头像（/api/v1/auth/avatar）');
  const png = fs.readFileSync(path.join(import.meta.dirname, 'avatar-test.png'));
  const b64 = png.toString('base64');
  const ts = Math.floor(Date.now() / 1000);
  const nonce = hex(crypto.randomBytes(16));
  const reqPath = '/api/v1/auth/avatar';
  const avatarHeaders = {
    'x-device-id': deviceId, 'x-timestamp': String(ts), 'x-nonce': nonce,
    'x-app-version': APP_VERSION, 'x-version-code': String(VERSION_CODE),
    'x-platform': 'ios', 'x-token': token,
  };
  const rawBody = JSON.stringify({ image_base64: b64 });
  avatarHeaders['x-sign'] = sign(signKey, ts, nonce, reqPath, rawBody);

  const upload = await tunnel(sessionKey, sid, seq++, 'POST', reqPath, {}, { image_base64: b64 }, avatarHeaders);
  let uploadBody = {};
  try { uploadBody = JSON.parse(unwrap(upload.plain)); } catch { /* ignore */ }
  ok('上传返回成功', uploadBody.code === 0, JSON.stringify(uploadBody).slice(0, 200));
  const user = uploadBody.data?.user || {};
  ok('响应里带 user', Object.keys(user).length > 0, JSON.stringify(user).slice(0, 160));
  ok('user 里有头像地址', Boolean(user.avatar || user.avatar_url),
    Object.keys(user).join(','));

  // ④ 清掉头像（不留痕迹）
  console.log('\n③ 清除头像（remove=true，恢复原状）');
  const ts2 = Math.floor(Date.now() / 1000);
  const nonce2 = hex(crypto.randomBytes(16));
  const rawRemove = JSON.stringify({ remove: true });
  const removeHeaders = {
    'x-device-id': deviceId, 'x-timestamp': String(ts2), 'x-nonce': nonce2,
    'x-app-version': APP_VERSION, 'x-version-code': String(VERSION_CODE),
    'x-platform': 'ios', 'x-token': token,
    'x-sign': sign(signKey, ts2, nonce2, reqPath, rawRemove),
  };
  const remove = await tunnel(sessionKey, sid, seq++, 'POST', reqPath, {}, { remove: true }, removeHeaders);
  let removeBody = {};
  try { removeBody = JSON.parse(unwrap(remove.plain)); } catch { /* ignore */ }
  ok('清除成功', removeBody.code === 0, JSON.stringify(removeBody).slice(0, 160));

  console.log(`\n结果\n  通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => { console.log('异常：' + e.message); process.exit(1); });
