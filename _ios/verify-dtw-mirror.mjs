/**
 * DTW/1 协议「Swift 实现的镜像校验」（2026-10-17）。
 *
 *   node _ios/verify-dtw-mirror.mjs
 *
 * 为什么要有它：这台机器编译不了 Swift，iOS 的网络层（DtwSession/DtwTransport/ApiClient）
 * 无法直接运行验证。这里把**Swift 里的每一步逻辑等值地写成 Node**（同样的常量、同样的
 * 字段顺序、同样的帧布局、同样的签名串），打真实生产服务端。
 *
 * 镜像能通过 ⇒ 说明我理解的协议与转录是自洽的；Swift 里剩下的风险只在语言 API 用法上，
 * 那部分由云端 CI 编译来发现。
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const BASE = 'https://tdapi.luoyun.xyz';
const HS_PATH = '/api/v1/dtw/hs';
const TUNNEL_PATH = '/api/v1/dtw';
const VERSION = 1;
const PROOF_ITER = Number(process.env.PROOF_ITER || 20000);
const BUCKET_SEC = 600;
const APP_VERSION = '1.0.0';
const VERSION_CODE = 10000;
const PEM = fs.readFileSync(path.resolve(import.meta.dirname, '..', '_ops', 'dtw-public.pem'), 'utf8');

let pass = 0;
let fail = 0;
const ok = (name, cond, extra = '') => {
  if (cond) { pass += 1; console.log(`  ✅ ${name}`); }
  else { fail += 1; console.log(`  ❌ ${name} ${extra}`); }
};

const hex = (buf) => Buffer.from(buf).toString('hex');
const deviceId = hex(crypto.randomBytes(16));
const installId = hex(crypto.randomBytes(16));

/** 与 Swift DtwSession.transcript 完全相同的字段顺序 */
function transcript(kid, sid, challenge, device, install, saltId, expireAt) {
  return `v=${VERSION}&kid=${kid}&sid=${sid}&challenge=${challenge}&device=${device}`
    + `&install=${install}&salt=${saltId}&exp=${expireAt}`;
}

/** RSA-OAEP-SHA256 加密 */
function rsaOaep(plain) {
  return crypto.publicEncrypt({
    key: PEM,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: 'sha256',
  }, plain);
}

/** AES-256-GCM 密封，返回 {iv, ct, tag}（对应 Swift DtwCrypto.seal） */
function seal(keyHex, plain, aad) {
  const key = Buffer.from(keyHex, 'hex');
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  if (aad) cipher.setAAD(aad);
  const ct = Buffer.concat([cipher.update(plain), cipher.final()]);
  return { iv, ct, tag: cipher.getAuthTag() };
}

function open(keyHex, iv, ct, tag, aad) {
  const decipher = crypto.createDecipheriv('aes-256-gcm', Buffer.from(keyHex, 'hex'), iv);
  if (aad) decipher.setAAD(aad);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ct), decipher.final()]);
}

function verifyRsa(payload, sigB64) {
  return crypto.createVerify('RSA-SHA256').update(payload).verify(PEM, Buffer.from(sigB64, 'base64'));
}

async function postJson(step, body) {
  const res = await fetch(BASE + HS_PATH, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=UTF-8',
      Accept: 'application/json',
      'X-Device-Id': deviceId,
      'X-Timestamp': String(Math.floor(Date.now() / 1000)),
      'X-Nonce': hex(crypto.randomBytes(16)),
      'X-App-Version': APP_VERSION,
      'X-Version-Code': String(VERSION_CODE),
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, body: JSON.parse(text || '{}') };
}

async function main() {
  console.log(`设备号 ${deviceId}`);
  console.log(`安装号 ${installId}\n`);

  console.log('① hello');
  // 服务端对握手有设备/IP 限流：连着跑两次 verify-all 会撞上（HTTP 429 或 code≠0）。
  // 那不是客户端的问题，所以这里重试几次，并把「重试」明确打印出来 —— 免得把限流当成协议回归。
  let hello = null;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    hello = await postJson('hello', { v: VERSION, step: 'hello', device_id: deviceId, install_id: installId });
    const okNow = hello.status === 200 && (hello.body.data || {}).challenge;
    if (okNow) {
      if (attempt > 1) console.log(`      （第 ${attempt} 次尝试成功）`);
      break;
    }
    const reason = (hello.body && hello.body.msg) || `HTTP ${hello.status}`;
    if (attempt === 3) {
      console.log(`      三次都失败：${reason}`);
    } else {
      const wait = 8000 * attempt;
      console.log(`      握手失败（${reason}），${wait / 1000} 秒后重试…`);
      await new Promise((r) => setTimeout(r, wait));
    }
  }
  ok('HTTP 200', hello.status === 200, String(hello.status));
  const data = hello.body.data || {};
  ok('拿到 salt / challenge / kid', Boolean(data.salt && data.challenge && data.kid),
    JSON.stringify(Object.keys(data)).slice(0, 120));
  ok('kid 是字符串（不能按整数取）', typeof data.kid === 'string', typeof data.kid);
  ok('服务端给出了 proof_iter', Number(data.proof_iter) > 0, String(data.proof_iter));

  const helloTranscript = transcript(data.kid, '', data.challenge, deviceId, installId, data.salt_id, data.expire_at);
  ok('hello 验签通过（信任锚成立）', verifyRsa(helloTranscript, data.sig || ''), 'sig 不匹配');

  console.log('\n② open（慢哈希证明 + 会话密钥）');
  const bucket = Math.floor(Date.now() / 1000 / BUCKET_SEC);
  const material = `${data.salt}|${data.challenge}|${deviceId}|${installId}|${bucket}`;
  // 与 Swift 一致：password = material，salt = salt
  const proof = hex(crypto.pbkdf2Sync(Buffer.from(material), Buffer.from(data.salt), Number(data.proof_iter || PROOF_ITER), 32, 'sha256'));
  const candidateKey = hex(crypto.randomBytes(32));
  const hsKey = hex(crypto.randomBytes(32));
  const inner = JSON.stringify({
    session_key: candidateKey,
    device_id: deviceId,
    install_id: installId,
    challenge: data.challenge,
    proof,
    bucket,
    ts: Date.now(),
    app_ver: APP_VERSION,
  });
  const sealed = seal(hsKey, Buffer.from(inner), null);
  const openResp = await postJson('open', {
    v: VERSION,
    step: 'open',
    ek: rsaOaep(Buffer.from(hsKey, 'hex')).toString('base64'),
    iv: sealed.iv.toString('base64'),
    ct: sealed.ct.toString('base64'),
    tag: sealed.tag.toString('base64'),
  });
  ok('open HTTP 200', openResp.status === 200, openResp.status + ' ' + JSON.stringify(openResp.body).slice(0, 160));
  const openData = openResp.body.data || {};
  ok('拿到 sid', Boolean(openData.sid), JSON.stringify(openResp.body).slice(0, 160));
  const openTranscript = transcript(data.kid, openData.sid || '', data.challenge, deviceId, installId,
    data.salt_id, openData.expire_at);
  ok('open 验签通过', verifyRsa(openTranscript, openData.sig || ''), 'sig 不匹配');

  const sid = openData.sid;
  const sessionKey = candidateKey;

  console.log('\n③ tunnel 一帧：查客户端版本（与 Swift encodeFrame/ApiClient 一致）');
  const ts = Math.floor(Date.now() / 1000);
  const nonce = hex(crypto.randomBytes(16));
  const reqPath = '/api/v1/version/check';
  const query = { platform: 'ios', version_code: '10000' };
  const raw = '';
  const payload = JSON.stringify({
    m: 'GET',
    p: reqPath,
    q: query,
    t: Date.now(),
    h: {
      'x-device-id': deviceId,
      'x-timestamp': String(ts),
      'x-nonce': nonce,
      'x-app-version': APP_VERSION,
      'x-version-code': String(VERSION_CODE),
      'x-platform': 'ios',
    },
  });

  // 组帧（与 Swift DtwTransport.encodeFrame 一致）
  const header = Buffer.alloc(18);
  Buffer.from('DTW1', 'ascii').copy(header, 0);
  header[4] = VERSION;
  header[5] = 0;
  Buffer.from(sid, 'hex').copy(header, 6, 0, 8);
  header.writeInt32BE(1, 14);
  const frameSealed = seal(sessionKey, Buffer.from(payload), header);
  const frame = Buffer.concat([header, frameSealed.iv, frameSealed.ct, frameSealed.tag]);

  const res = await fetch(BASE + TUNNEL_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: frame,
  });
  ok('隧道 HTTP 200', res.status === 200, String(res.status));
  const frameReply = Buffer.from(await res.arrayBuffer());
  ok('响应是合法帧（≥46 字节）', frameReply.length >= 46, String(frameReply.length));
  const h2 = frameReply.subarray(0, 18);
  ok('响应 magic 正确', h2.subarray(0, 4).toString('ascii') === 'DTW1', h2.subarray(0, 4).toString('ascii'));
  const iv2 = frameReply.subarray(18, 30);
  const ct2 = frameReply.subarray(30, frameReply.length - 16);
  const tag2 = frameReply.subarray(frameReply.length - 16);
  const plain2 = open(sessionKey, iv2, ct2, tag2, h2);
  const reply = JSON.parse(plain2.toString('utf8'));
  ok('响应载荷含 s / ct / b 三个字段', 's' in reply && 'ct' in reply && 'b' in reply, Object.keys(reply).join(','));
  const envelope = JSON.parse(Buffer.from(reply.b, 'base64').toString('utf8'));
  ok('业务信封 code=0', envelope.code === 0, JSON.stringify(envelope).slice(0, 160));
  ok('业务数据可读（有 has_update 字段）', 'has_update' in (envelope.data || {}), JSON.stringify(envelope.data).slice(0, 200));

  console.log('\n④ 防篡改（改一个字节应当解不开）');
  const tampered = Buffer.from(frameReply);
  tampered[40] = tampered[40] ^ 0xff;
  let tamperCaught = false;
  try {
    const t2 = tampered.subarray(tampered.length - 16);
    open(sessionKey, tampered.subarray(18, 30), tampered.subarray(30, tampered.length - 16), t2, tampered.subarray(0, 18));
  } catch {
    tamperCaught = true;
  }
  ok('篡改后的帧无法解密（AEAD 生效）', tamperCaught);

  console.log('\n⑤ 走隧道取公开配置（娱乐页 playbook + 首页横幅）');
  async function tunnelGet(path2, query2, seq) {
    const payload3 = JSON.stringify({
      m: 'GET',
      p: path2,
      q: query2,
      t: Date.now(),
      h: {
        'x-device-id': deviceId,
        'x-timestamp': String(Math.floor(Date.now() / 1000)),
        'x-nonce': hex(crypto.randomBytes(16)),
        'x-app-version': APP_VERSION,
        'x-version-code': String(VERSION_CODE),
        'x-platform': 'ios',
      },
    });
    const h3 = Buffer.alloc(18);
    Buffer.from('DTW1', 'ascii').copy(h3, 0);
    h3[4] = VERSION;
    h3[5] = 0;
    Buffer.from(sid, 'hex').copy(h3, 6, 0, 8);
    h3.writeInt32BE(seq, 14);
    const sealed3 = seal(sessionKey, Buffer.from(payload3), h3);
    const frame3 = Buffer.concat([h3, sealed3.iv, sealed3.ct, sealed3.tag]);
    const res3 = await fetch(BASE + TUNNEL_PATH, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: frame3,
    });
    const reply3 = Buffer.from(await res3.arrayBuffer());
    const h4 = reply3.subarray(0, 18);
    const plain3 = open(sessionKey, reply3.subarray(18, 30),
      reply3.subarray(30, reply3.length - 16), reply3.subarray(reply3.length - 16), h4);
    const body3 = JSON.parse(plain3.toString('utf8'));
    return JSON.parse(Buffer.from(body3.b, 'base64').toString('utf8'));
  }

  const cfg = await tunnelGet('/api/v1/config', {}, 2);
  ok('公开配置 code=0', cfg.code === 0, JSON.stringify(cfg).slice(0, 160));
  const ent = ((cfg.data || {}).playbook || {}).entertainment || {};
  const secs = ent.sections || [];
  ok('娱乐页分区已下发（≥1）', secs.length >= 1, `${secs.length} 个分区`);
  let rowCount = 0;
  for (const sec of secs) rowCount += (sec.rows || []).length;
  ok('娱乐页行数 ≥ 1', rowCount >= 1, `${rowCount} 行`);
  const keys = secs.flatMap((s2) => (s2.rows || []).map((r) => r.key));
  const known = new Set(['parse', 'order', 'call', 'shutdown', 'sound', 'tease', 'avatar', 'balance']);
  ok('下发的 key 都在客户端已实现的词表里', keys.every((k) => known.has(k)), keys.join(','));
  const banner = (cfg.data || {}).banner || {};
  ok('横幅配置存在', typeof banner.enabled === 'boolean', JSON.stringify(banner).slice(0, 120));

  console.log('     娱乐页分区：');
  for (const sec of secs) {
    console.log(`       · ${sec.title}（${sec.accent || '-'}）`);
    for (const r of (sec.rows || [])) console.log(`           - ${r.key}：${r.title}${r.switch ? ' [开关]' : ''}`);
  }

  console.log(`\n结果\n  通过 ${pass} 项，失败 ${fail} 项`);
  process.exit(fail === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('校验异常：', e);
  process.exit(1);
});
