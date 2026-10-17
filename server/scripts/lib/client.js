/**
 * 测试/脚本用的最小 API 客户端：负责按服务端规则签名。
 *
 * 签名：sign = HMAC-SHA256(ts \n nonce \n device_id \n path \n body_hash, key)
 * 请求头：X-Device-Id / X-Timestamp / X-Nonce / X-Sign / X-Token
 *
 * 同时演示了客户端 native 层该做什么 —— Android 侧 libdwcore.so 里的逻辑与此一致。
 */
import crypto from 'node:crypto';

const sha256Hex = (data) => crypto.createHash('sha256').update(data).digest('hex');

export class ApiClient {
  /**
   * @param {string} baseUrl 形如 http://127.0.0.1:7101
   * @param {object} options { masterKey, token, signKey, deviceId, appVersion, versionCode, tunnel }
   *
   * `tunnel`（默认自动）：服务端开了强制私有协议（DTW_ENABLED + DTW_REQUIRE）时，
   * 这个测试客户端也会像真机一样先握手、再把每个请求封成加密帧 —— 这样整套业务回归
   * 都能在**加密通道**下重跑一遍，而不是只挑几个接口试。想看明文路径就传 tunnel:false。
   */
  constructor(baseUrl, options = {}) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.masterKey = options.masterKey || '';
    this.token = options.token || '';
    this.signKey = options.signKey || '';
    // 设备号必须与服务端同一套归一化规则（去空白 + 小写），否则签名对不上 ——
    // Android 侧 libdwcore.so 里的 getDeviceId() 也要做同样的归一化。
    this.deviceId = String(options.deviceId || 'test-device-0001').trim().toLowerCase();
    this.appVersion = options.appVersion || '1.0.0';
    this.versionCode = options.versionCode || 1;
    this.tunnel = options.tunnel === true;
    this.tunnelOff = options.tunnel === false;
    this._dtw = null;             // { sid, key, seq, dtw, tunnelPath }
    this.installId = 'test-install-' + crypto.randomBytes(6).toString('hex');
  }

  /** 服务端是不是开着强制私有协议（开了就自动走隧道） */
  async shouldTunnel() {
    if (this.tunnelOff) return false;
    if (this.tunnel) return true;
    try {
      const dtw = await import('../../src/core/dtw.js');
      this.tunnel = Boolean(dtw.isEnabled()) && Boolean(dtw.isRequired());
    } catch {
      this.tunnel = false;
    }
    return this.tunnel;
  }

  /** 握手：hello 拿挑战 → 慢哈希证明 → open 建会话（与真机客户端同一套流程） */
  async ensureTunnel() {
    if (this._dtw) return this._dtw;
    const dtw = await import('../../src/core/dtw.js');
    const base = this.baseUrl;
    const hs = async (body) => {
      const res = await fetch(base + '/api/v1/dtw/hs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Device-Id': this.deviceId,
          'X-Timestamp': String(Math.floor(Date.now() / 1000)),
          'X-Nonce': crypto.randomBytes(8).toString('hex'),
        },
        body: JSON.stringify(body),
      });
      return res.json();
    };
    const hello = await hs({ v: 1, step: 'hello', device_id: this.deviceId, install_id: this.installId });
    const d = hello.data;
    if (!d) throw new Error('DTW 握手失败：' + JSON.stringify(hello).slice(0, 160));

    const bucket = dtw.bucketOf(Date.now());
    const proof = dtw.slowHashHex({
      salt: d.salt,
      challenge: d.challenge,
      deviceId: this.deviceId,
      installId: this.installId,
      bucket,
    });
    const sessionKey = crypto.randomBytes(32).toString('hex');
    const hsKey = crypto.randomBytes(32).toString('hex');
    const inner = JSON.stringify({
      session_key: sessionKey,
      device_id: this.deviceId,
      install_id: this.installId,
      challenge: d.challenge,
      proof,
      bucket,
      ts: Date.now(),
      app_ver: this.appVersion,
    });
    const sealed = dtw.seal(hsKey, inner);
    const ek = crypto
      .publicEncrypt(
        { key: dtw.publicKeyPem(), padding: crypto.constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' },
        Buffer.from(hsKey, 'hex'),
      )
      .toString('base64');
    const opened = await hs({
      v: 1,
      step: 'open',
      ek,
      iv: sealed.iv.toString('base64'),
      ct: sealed.ct.toString('base64'),
      tag: sealed.tag.toString('base64'),
    });
    if (opened.code !== 0) throw new Error('DTW 握手失败：' + JSON.stringify(opened).slice(0, 160));
    this._dtw = { sid: opened.data.sid, key: sessionKey, seq: 0, dtw, tunnelPath: '/api/v1/dtw' };
    return this._dtw;
  }

  /** 用 MASTER_KEY 签名（引导接口） */
  sign(payload, key) {
    const body = [payload.timestamp, payload.nonce, payload.deviceId || '', payload.path, payload.bodyHash].join('\n');
    return crypto.createHmac('sha256', key).update(body).digest('hex');
  }

  /**
   * @param {object} options
   *   key: 'session' | 'root' —— 用哪个密钥签名
   *   nonce / timestamp: 显式指定（用于构造重放、超窗等异常用例）
   *   badSign: true 时故意签错（用于验证 1002）
   */
  async call(method, path, body = null, { key = 'session', headers = {}, raw = null, nonce, timestamp, badSign = false } = {}) {
    const ts = timestamp ?? Math.floor(Date.now() / 1000);
    const nc = nonce ?? crypto.randomBytes(16).toString('hex');
    const bodyText = raw !== null ? raw : body ? JSON.stringify(body) : '';
    const bodyHash = sha256Hex(bodyText);
    const secret = key === 'root' ? this.masterKey : this.signKey;
    let sign = this.sign({ timestamp: ts, nonce: nc, deviceId: this.deviceId, path, bodyHash }, secret);
    if (badSign) sign = sign.replace(/^./, sign[0] === 'a' ? 'b' : 'a');

    const outHeaders = {
      'X-Device-Id': this.deviceId,
      'X-Timestamp': String(ts),
      'X-Nonce': nc,
      'X-Sign': sign,
      'X-App-Version': this.appVersion,
      'X-Version-Code': String(this.versionCode),
      ...(this.token ? { 'X-Token': this.token } : {}),
      ...headers,
    };

    if (await this.shouldTunnel()) {
      return this.callThroughTunnel(method, path, body, bodyText, outHeaders);
    }

    const res = await fetch(this.baseUrl + path, {
      method,
      headers: { 'Content-Type': 'application/json;charset=UTF-8', ...outHeaders },
      body: bodyText || undefined,
    });
    const text = await res.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      json = { raw: text };
    }
    return { status: res.status, body: json };
  }

  /**
   * 把一次请求封成 DTW 帧发出去，再把响应帧解开。
   *
   * 与真机客户端的约定完全一致：业务请求头塞进载荷的 `h`，`rb` 带上**签过名的原始串**
   * （服务端按它算 body_hash，重新 stringify 会因键序不同而验签失败）。
   */
  async callThroughTunnel(method, path, body, bodyText, outHeaders) {
    const t = await this.ensureTunnel();
    const qIndex = path.indexOf('?');
    const pathname = qIndex < 0 ? path : path.slice(0, qIndex);
    const query = {};
    if (qIndex >= 0) {
      for (const pair of path.slice(qIndex + 1).split('&')) {
        if (!pair) continue;
        const eq = pair.indexOf('=');
        const k = eq < 0 ? pair : pair.slice(0, eq);
        const v = eq < 0 ? '' : pair.slice(eq + 1);
        try {
          query[decodeURIComponent(k)] = decodeURIComponent(v);
        } catch {
          query[k] = v;
        }
      }
    }
    const lowerHeaders = {};
    for (const [k, v] of Object.entries(outHeaders)) lowerHeaders[String(k).toLowerCase()] = String(v);

    const payload = JSON.stringify({
      m: method,
      p: pathname,
      q: query,
      // 只发一份：`rb` 是签名用的原始串，服务端按它算 body_hash 并解析成业务 body。
      // （早先 b + rb 都塞，等于 body 发两遍，大请求会冲破 2MB 上限。）
      ...(bodyText ? { rb: bodyText } : { b: body || {} }),
      t: Date.now(),
      h: lowerHeaders,
    });
    t.seq += 1;
    const frame = t.dtw.encodeFrame({ sid: t.sid, seq: t.seq, keyHex: t.key, payload });
    const res = await fetch(this.baseUrl + t.tunnelPath, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: frame,
    });
    const buf = Buffer.from(await res.arrayBuffer());
    if (res.status !== 200) {
      // 隧道层自己拒了（会话过期、帧坏了…），这时回的是明文 JSON 信封
      let json = null;
      try {
        json = JSON.parse(buf.toString('utf8'));
      } catch {
        json = { raw: buf.toString('utf8').slice(0, 200) };
      }
      return { status: res.status, body: json, tunneled: true };
    }
    let box;
    try {
      box = t.dtw.decodeFrame(buf, t.key);
    } catch (err) {
      return { status: 200, body: { code: -2, msg: '响应帧解密失败：' + err.message }, tunneled: true };
    }
    const text = Buffer.from(box.b || '', 'base64').toString('utf8');
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      json = { raw: text };
    }
    return { status: box.s, body: json, tunneled: true };
  }

  get = (path, options) => this.call('GET', path, null, options);
  post = (path, body, options) => this.call('POST', path, body, options);
}

export default ApiClient;
