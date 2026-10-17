# doutu-server 接口文档

> 本文档对应代码版本：doutu-server 1.0.0
>
> 目标读者：① Android 客户端（Java/Kotlin + native 层）开发者；② 运维 / 后台使用者。
>
> 本文所有路径、字段名、错误码均取自服务端源码（详见文末对应关系），未做任何推测性补全。

---

## 目录

1. [总览](#1-总览)
2. [请求头规范](#2-请求头规范)
3. [签名算法](#3-签名算法)
4. [统一响应与错误码表](#4-统一响应与错误码表)
5. [授权流程时序](#5-授权流程时序)
6. [接口详解](#6-接口详解)
7. [状态字典](#7-状态字典)
8. [客户端接入检查清单](#8-客户端接入检查清单)
9. [限流与配额规则](#9-限流与配额规则)
10. [联调脚本](#10-联调脚本)

---

## 1. 总览

### 1.1 服务地址约定

| 前缀 | 用途 | 鉴权方式 | 说明 |
| --- | --- | --- | --- |
| `/api/v1/*` | 客户端业务接口 | `X-Timestamp` + `X-Nonce` 必带；`X-Sign` 按模式（见下） | 授权、解析、配置、升级、支付、工单 |
| `/admin/api/*` | 后台管理接口 | 后台 token（`X-Admin-Token` / `Authorization: Bearer` / `admin_token` Cookie） | 前缀由环境变量 `ADMIN_PATH` 决定，默认 `/admin`；改后台路径时本文所有 `/admin/api/*` 同步替换 |
| `/dw/file/*` | 成品 / 原片下载 | **无客户端签名**，用 URL 内的 `exp` + `sign` | 私有桶语义的签名 URL，可被播放器直接使用 |
| `/healthz` | 健康检查（服务/DB/存储） | 无 | 供 Nginx、宝塔监控、UptimeRobot 探测 |

- 生产环境监听 `BIND_HOST:PORT`（默认 `127.0.0.1:7101`），前置 Nginx 反代：
  - `X-Real-IP` / `X-Forwarded-For` 由 Nginx 注入，服务端据此识别客户端 IP（客户端不要伪造）。
- 客户端 base URL 建议统一为 `https://<域名>`，接口路径拼 `/api/v1/...`。

### 1.2 协议与编码

| 项 | 约定 |
| --- | --- |
| 传输 | HTTP/1.1（生产建议 HTTPS） |
| 编码 | UTF-8 |
| 请求体格式 | `Content-Type: application/json;charset=UTF-8`（也兼容 `application/x-www-form-urlencoded`） |
| 请求体上限 | 生产入口 `2 MB`（`src/index.js` 传入 `bodyLimit`）；超限返回 `1001 请求体过大` |
| 响应格式 | `application/json; charset=utf-8`，`Cache-Control: no-store` |
| 响应信封 | `{"code":0,"msg":"ok","data":{...},"ts":1771056000,"sign":"..."}` |
| 响应安全头 | `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: no-referrer` |

### 1.3 时间格式

| 字段 | 格式 | 说明 |
| --- | --- | --- |
| 所有 `*_at` 字段（`expire_at`、`created_at`、`finished_at`、`paid_at`、`bind_at`、`sla_at` 等） | `YYYY-MM-DD HH:mm:ss` | **本地时区**字符串，不带时区后缀；客户端不要当 UTC 解析 |
| `ts`（响应信封）/ `server_ts` | 秒级 Unix 时间戳（整数） | 时区无关，用于时钟校准与签名时间窗 |
| `today` | `YYYY-MM-DD` | 本地日期 |
| `expire_in` / `expire_sec` / `url_ttl_sec` / `estimate_sec` / `timeout_sec` | 秒（整数） | 相对时长，与设备时区无关 |

### 1.4 金额约定（硬性）

- **所有金额字段都是字符串两位小数**，例如 `"19.90"`、`"129.00"`、`"0.00"`。
- 服务端内部一律以**整数分**运算（`src/core/money.js`：`toCents` / `fromCents`）。
- 客户端**不要用浮点解析或运算**金额：Java 用 `String` 或 `BigDecimal("19.90")`，Kotlin 同理；展示直接使用原字符串。
- 涉及金额的字段：`price`、`origin_price`、`amount`、`net_amount`、`fee`、`discount`、`total_amount`、`paid_amount`、`today_amount`、`pay_min_amount`。

---

## 2. 请求头规范

所有 `/api/v1/*` 接口（除 `/api/v1/pay/notify/*`、`/api/v1/pay/mock/pay`）都会先经过 `clientAuth` 校验链；`/healthz`、`/dw/file/*`、`/admin/api/*` 不走该校验链。

| 请求头 | 必填 | 格式 | 作用 |
| --- | --- | --- | --- |
| `X-Device-Id` | 是（会话类业务必需） | 字符串，**必须先 `.trim().toLowerCase()`** | 设备指纹。参与签名、查 `user_device`、会话与设备绑定校验。服务端取头顺序：`X-Device-Id` → `device-id` → body/query 的 `device_id`，取到后统一归一化 |
| `X-Timestamp` | 是 | 秒级 Unix 时间戳，十进制整数，**不要补前导零** | 时间窗校验：`|服务端秒 - X-Timestamp| <= 300`（`SIGN_WINDOW_SEC`，默认 300）。超窗返回 `1003` |
| `X-Nonce` | 是 | 随机串，**长度 >= 8** | 防重放：服务端 `SETNX nonce:{nonce} EX 300`，300 秒内同一 nonce 二次出现返回 `1003` |
| `X-Sign` | 视模式 | 小写十六进制 HMAC-SHA256 | 请求签名。`mode=session` 用会话 `sign_key`；`mode=root`（register/login/handshake/refresh/config）用 `MASTER_KEY`；`mode=none` 的接口不校验签名 |
| `X-Token` | `mode=session` 必填 | 32 字节随机 hex（`randomHex(32)`） | 会话凭证。也可用 `token` 头传递；缺失/过期/被撤销返回 `2005` |
| `X-App-Version` | 建议必带 | 版本名，如 `1.0.0` | 版本吊销（`app_version_blocklist`）与日志；回退读取 body/query 的 `version` |
| `X-Version-Code` | 建议必带 | 整数版本号，如 `3` | 最低版本校验（`MIN_SUPPORTED_VERSION_CODE`）与灰度发布分组；回退读取 body/query 的 `version_code` |
| `Content-Type` | 有请求体时必填 | `application/json;charset=UTF-8` | 不带该头时服务端仍会把请求体按 form 解析，业务字段会取不到 |
| `Range` | 下载时可选 | `bytes=0-1023` | `/dw/file/*` 支持断点续传 / 播放器拖动 |
| `X-Admin-Token` | 后台必填 | 后台登录返回的 token | 后台接口鉴权；也支持 `Authorization: Bearer <token>` 与 Cookie `admin_token` |

**请求头与参数的取值优先级**（`Context.input`）：`body` → `query` → `path params`。空字符串会被忽略（视为未传）。

---

## 3. 签名算法

实现来自 `src/core/crypto.js` 的 `buildSignPayload` / `signRequest` / `deriveSessionKey`，与参考客户端 `scripts/lib/client.js` 完全一致。

### 3.1 待签串拼接顺序

```
sign_payload = timestamp + "\n" + nonce + "\n" + device_id + "\n" + path + "\n" + body_hash

body_hash = sha256_hex(原始请求体字符串)     // GET / 无请求体时 = sha256_hex("")
sign      = hmac_sha256_hex(secret, sign_payload)   // 结果小写十六进制
```

字段来源与规则：

| 片段 | 取值 |
| --- | --- |
| `timestamp` | `X-Timestamp`，服务端用 `parseInt` 取整后再 `String()` 参与拼接 → 客户端请直接发十进制整数，**不要补前导零** |
| `nonce` | `X-Nonce` 原样字符串（长度须 >= 8） |
| `device_id` | **归一化后的设备号** = `String(raw).trim().toLowerCase()`。客户端不做同样归一化会全量签名失败 |
| `path` | URL 路径，**不含 query、不含 host**；服务端对 pathname 做了 `decodeURI` 后再签名 → 客户端请用未百分号编码的路径 |
| `body_hash` | `sha256_hex(实际发出的原始请求体)`；GET 无请求体时为 `sha256_hex("")` = `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |

要点：

1. `body_hash` 覆盖的是**实际发送的字节**。必须「先序列化 → 再哈希 → 原样发送」，禁止用另一个 JSON 序列化器重新生成一次（字段顺序/空格不同会导致 `1002`）。
2. **query 参数不参与签名**（服务端只用 `pathname`）。因此不要把安全敏感参数只放在 query 上；`X-App-Version` / `X-Version-Code` 这类建议走请求头。
3. 服务端会把 `X-Sign` 统一转小写后定长比较，发大写也能通过，但**统一发小写 hex**。

### 3.2 用哪个密钥签名

| 接口 | 模式 | 签名密钥 |
| --- | --- | --- |
| `POST /api/v1/auth/register` | root | `MASTER_KEY` |
| `POST /api/v1/auth/login` | root | `MASTER_KEY` |
| `POST /api/v1/auth/handshake` | root | `MASTER_KEY` |
| `POST /api/v1/auth/refresh` | root | `MASTER_KEY` |
| `GET /api/v1/config` | root | `MASTER_KEY` |
| 其余会话类接口（auth 的 logout/heartbeat/unbind/status、dw、pay、ticket、dw/env 等） | session | 会话 `sign_key` |
| `/api/v1/time`、`/api/v1/upgrade/check`、`/api/v1/upgrade/crash`、`/api/v1/pay/goods`、`/api/v1/auth/sign_debug` | none | 不校验签名 |

`MASTER_KEY` 由客户端 native 层内嵌（so 内），长度 64 位 hex；开发环境未配置时服务端回退为 `dev-master-key`（生产环境未配置会直接抛 `5000 服务端未配置 MASTER_KEY`）。

### 3.3 会话密钥（sign_key）来源

| 场景 | 来源 | 算法 |
| --- | --- | --- |
| 注册 / 登录 | 响应 `data.sign_key`（服务端随机 `randomHex(32)`） | 直接使用 |
| handshake 换发 | 响应 `data.sign_key` | `sign_key = hmac_sha256_hex(MASTER_KEY, server_random + ":" + nonce + ":" + device_id)`，其中 `nonce` 是**本次 handshake 请求的 X-Nonce**，`device_id` 是归一化后的设备号，`server_random` 取自响应 |

客户端可在本地自行推导 handshake 的 `sign_key` 做校验：`HMAC-SHA256(MASTER_KEY, server_random + ":" + 本次请求的 nonce + ":" + normalizedDeviceId)`。

> 注意：handshake 的 `sign_key` **不刷新会话到期时间**（`expire_at` 不变），token 也保持不变，仅换签名密钥。

### 3.4 响应验签

```
sign = hmac_sha256_hex(该请求所用密钥, JSON.stringify({code, msg, data, ts}))
```

- `sign` 字段本身**不参与**计算；被签名的对象属性顺序固定为 `code, msg, data, ts`（`buildEnvelope` 的插入顺序）。
- 成功与失败响应都带 `sign`（只要有密钥）。
- 用哪个密钥：session 模式 → 会话 `sign_key`；root 模式（register/login/handshake/refresh/config）→ `MASTER_KEY`。
- `mode=none` 的接口（`/api/v1/time`、`/api/v1/upgrade/check`、`/api/v1/upgrade/crash`、`/api/v1/pay/goods`、`/api/v1/auth/sign_debug`）以及 `/healthz`、`/dw/file/*` 的 JSON 响应，`sign` 恒为 `null`。
- 失败发生在「密钥尚未确定」的阶段时 `sign` 也为 `null`，包括：`1001` 缺时间戳/nonce、`1003` 时间窗或重放、`1002` 签名错误、`2005` 缺 token/会话失效、`9001` 版本吊销、`5001` 维护中、`1001` 参数错误（路由内抛错时 signKey 已确定 → 会签名）。

**最简实现（推荐给 native 层）**：响应体文本 = `JSON.stringify({code,msg,data,ts})` + `,"sign":"<hex>"` 拼在最后。因此：

```
signed_text = rawResponseText 去掉结尾的 ,"sign":"..." 后再补回一个 }
```

即服务端返回 `{"code":0,...,"ts":1771056000,"sign":"ab12..."}`，被签名的原文就是 `{"code":0,...,"ts":1771056000}`。这样无需在客户端重新序列化 JSON（Java 的 `JSONObject` 无序，重新序列化极易不一致）。

### 3.5 Node.js 参考实现（与 `scripts/lib/client.js` 一致）

```js
import crypto from 'node:crypto';

const sha256Hex = (d) => crypto.createHash('sha256').update(d).digest('hex');
const hmacSha256Hex = (key, d) => crypto.createHmac('sha256', key).update(d).digest('hex');
const normalizeDeviceId = (v) => String(v || '').trim().toLowerCase();

// 与 src/core/crypto.js buildSignPayload 完全一致
const buildSignPayload = ({ timestamp, nonce, deviceId, path, bodyHash }) =>
  [String(timestamp), String(nonce), String(deviceId || ''), String(path), String(bodyHash || '')].join('\n');

/** body 必须是"将要发送的原始字符串"；GET 传 undefined */
function sign({ secret, timestamp, nonce, deviceId, path, body }) {
  const bodyHash = sha256Hex(body ?? '');
  return {
    bodyHash,
    sign: hmacSha256Hex(
      secret,
      buildSignPayload({ timestamp, nonce, deviceId: normalizeDeviceId(deviceId), path, bodyHash }),
    ),
  };
}

/** 校验响应签名；返回 false 表示不通过 */
function verifyResponse(rawText, secret) {
  const idx = rawText.lastIndexOf(',"sign":');
  if (idx === -1) return false;
  const envelope = JSON.parse(rawText);
  if (envelope.sign === null || envelope.sign === undefined) return false; // mode=none 的接口不验签
  const signed = rawText.slice(0, idx) + '}';   // == JSON.stringify({code,msg,data,ts})
  const expect = hmacSha256Hex(secret, signed);
  const a = Buffer.from(expect);
  const b = Buffer.from(String(envelope.sign).toLowerCase());
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

/* 使用示例 */
const ts = Math.floor(Date.now() / 1000);
const nonce = crypto.randomBytes(16).toString('hex');
const path = '/api/v1/dw/parse';
const body = JSON.stringify({ share_text: 'https://v.douyin.com/xxxxxx/', want_watermark: true });
const { sign: xSign } = sign({ secret: signKey, timestamp: ts, nonce, deviceId: 'abc123', path, body });

const res = await fetch(`https://api.example.com${path}`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
    'X-Device-Id': 'abc123',
    'X-Timestamp': String(ts),
    'X-Nonce': nonce,
    'X-Sign': xSign,
    'X-Token': token,
    'X-App-Version': '1.0.0',
    'X-Version-Code': '1',
  },
  body,
});
const raw = await res.text();
if (!verifyResponse(raw, signKey)) throw new Error('响应签名校验失败');
```

### 3.6 Kotlin / Java 参考实现（Android native 层）

```kotlin
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

fun sha256Hex(data: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

fun hmacSha256Hex(key: String, data: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

// 设备号必须先归一化，且发送 X-Device-Id 时也用这个值
val deviceId = rawDeviceId.trim().lowercase()

// body 为将要发送的原始字节；GET 无体时用 ByteArray(0)
val bodyHash = sha256Hex(bodyBytes)
val payload = listOf(ts.toString(), nonce, deviceId, path, bodyHash).joinToString("\n")
val xSign = hmacSha256Hex(secret, payload)

// 响应验签：不要重新序列化 JSON，直接从原始文本里剥掉 sign 字段
fun verifyResponse(rawText: String, secret: String): Boolean {
    val idx = rawText.lastIndexOf(",\"sign\":")
    if (idx == -1) return false
    val sign = JSONObject(rawText).opt("sign")
    if (sign == null || sign == JSONObject.NULL) return false   // mode=none 的接口
    val signed = rawText.substring(0, idx) + "}"
    return hmacSha256Hex(secret, signed).equals(sign.toString().lowercase())
}
```

### 3.7 常见签名失败原因（对号入座）

| 现象 | 原因 |
| --- | --- |
| 全量 `1002` | 设备号未做 `trim().toLowerCase()`；或用 body 里的 device_id 签名却用未归一化原值 |
| 全量 `1002` | `body_hash` 与实际发送字节不一致（重新序列化、加空格、改字段顺序、把 body 当对象转字符串） |
| GET 请求 `1002` | `body_hash` 不是 `sha256("")`（例如漏算空串或用了 `"{}"`） |
| 间歇 `1003 请求已过期` | 设备时钟漂移超 ±300 秒；用户手动改时间 |
| 间歇 `1003 重复请求已被拒绝` | nonce 复用（用了固定值或计数器回绕）；并发重试复用了同一个 nonce |
| `1001 缺少随机串 nonce` | `X-Nonce` 为空或长度 < 8 |
| `1001 缺少时间戳` | `X-Timestamp` 未带或不是数字 |
| 升级/换密钥后集中 `2005` | 后台「按版本吊销」或「强制下线」生效，需重新 handshake/登录 |

---

## 4. 统一响应与错误码表

### 4.1 响应信封

```json
{
  "code": 0,
  "msg": "ok",
  "data": { },
  "ts": 1771056000,
  "sign": "9f2c1d0b4a..."
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | int | `0` 表示成功，非 0 见错误码表 |
| `msg` | string | 面向用户的中文提示，可直接展示（成功时多为 `"ok"`，部分接口给业务文案如 `"注册成功，请等待后台开通会员"`、`"已复用缓存结果"`） |
| `data` | object / array / null | 业务数据；失败时通常为 `null`，部分错误会带诊断信息（如 `1003` 带 `{drift, window}`，`3001` 带 `{limit, used, left}`，`3002` 带 `{limit, window_sec, retry_after}`，`2002` 带 `{expire_at}`，`2003` 带 `{bound, max_device}`） |
| `ts` | int | 服务端秒级时间戳，**任何响应都有**，可用于时钟校准 |
| `sign` | string / null | 响应签名，规则见 3.4；`null` 表示该响应未签名 |

### 4.2 HTTP 状态码映射

业务失败默认返回 **HTTP 200**（便于 Nginx 统计 5xx），仅以下走 4xx：

| 错误码 | HTTP |
| --- | --- |
| `1002` 签名校验失败 | 401 |
| `2005` 身份过期 | 401 |
| `6001` 后台登录失效 | 401 |
| `6002` 后台无权限 | 403 |
| `3002` 限流 | 429 |
| `4004` 资源不存在 | 404 |

另外两个框架级响应：

| 场景 | HTTP | body |
| --- | --- | --- |
| 路由不存在 | 404 | `{"code":4004,"msg":"接口不存在","data":null,"ts":...,"sign":null}`（无签名头） |
| 方法不支持（路径匹配但 method 不符） | 405 | `{"code":1001,"msg":"请求方法不支持","data":null,"ts":...,"sign":null}` |

### 4.3 全部错误码

以下为 `src/core/errors.js` 中的**全部**错误码（共 20 个，含 `0`）。

| code | 常量名 | 默认 msg | HTTP | 客户端处理建议 |
| --- | --- | --- | --- | --- |
| `0` | `OK` | `ok` | 200 | 正常处理 `data` |
| `1001` | `PARAM` | 参数错误 | 200 | 提示 `msg`；多为字段缺失/格式错。开发期直接打日志排查。与**用户名**相关的 `msg`（`register` / `login` / `/auth/profile` / 后台 `PUT /admin/api/users/:id`）有：`请输入用户名`、`用户名长度需为 2~20 位`、`用户名只能包含中文、字母、数字、下划线或短横线`、`用户名不能是手机号，请换一个`、`该用户名已被使用，请换一个`、`请输入新的用户名`、`新用户名与当前用户名相同`，以及登录时的 `账号或密码错误` |
| `1002` | `SIGN` | 签名校验失败 | 401 | **不要提示用户**：按 3.7 自查签名；确认设备号归一化、`body_hash`、密钥选择 |
| `1003` | `TIMESTAMP` | 请求已过期或重复提交 | 200 | 先用响应 `ts` 校准本地时钟；`data.drift` 给出偏差秒数；重试必须换新 nonce 与新时间戳 |
| `2001` | `NOT_ACTIVATED` | 账号未开通会员 | 200 | **保留码，当前不会返回**。产品模型是「会员只决定有没有水印，不决定能不能用」：未开通会员（`user.activated=false`、`expire_at=null`）照样可以调用全部业务接口，客户端只需按 `activated` 叠加全屏水印遮罩（文案取 `watermark_overlay_text`）并展示 `activation_notice` 提示 |
| `2002` | `EXPIRED` | 账号已过期 | 200 | 跳续费页；`data.expire_at` 为到期时间 |
| `2003` | `DEVICE_LIMIT` | 设备数超限 | 200 | 跳设备管理页引导解绑（或联系客服）；`data` 带 `bound` / `max_device` |
| `2004` | `BANNED` | 此账号已被封禁 | 200 | 展示封禁提示 + 客服入口，禁止重试 |
| `2005` | `SESSION_INVALID` | 身份过期，请重新登录 | 401 | **清会话 → 用 refresh 续 token；refresh 也失败则重新 login/register**；也可能是被后台强制下线/版本吊销 |
| `3001` | `QUOTA` | 今日额度已用完 | 200 | 展示剩余额度 0，引导续费/升级；不要自动重试 |
| `3002` | `RATE_LIMIT` | 操作过于频繁，请稍后再试 | 429 | 按 `data.retry_after` 秒退避重试；`data.window_sec` 为窗口长度 |
| `4001` | `PARSE_FAILED` | 解析失败，请稍后重试 | 200 | 提示稍后重试；若为「平台暂时不可用」则隐藏该平台入口 |
| `4002` | `TASK_TIMEOUT` | 任务处理超时 | 200 | **保留码**：任务超时统一通过任务 `status=5` 表达（见 7.1），接口不会直接返回 4002 |
| `4004` | `NOT_FOUND` | 资源不存在 | 404 | 任务/订单/工单/文件不存在或不属于当前用户；刷新列表。**另一个常见来源是已停用的 `POST /api/v1/auth/activate`**（恒返回 `4004`，见 6.1.10）：收到时看 `msg` 是否为「卡密激活已停用…」，是就按老客户端兼容处理 |
| `5000` | `SERVER` | 服务异常，请稍后重试 | 200 | 可重试一次；连续失败上报客服并附 `ts` |
| `5001` | `BUSY` | 服务端繁忙，请稍后再试 | 200 | 两种情形：① **维护中**——`msg` 为后台配置的维护文案，此时除 `/auth/*` 外全部接口都返回该码；② **注册通道关闭**（`register_open=false`）——仅 `POST /api/v1/auth/register` 返回，`msg` 为 `注册通道已关闭，请联系客服开通账号`，此时只能由后台创建/激活账号。两种情形客户端都直接展示 `msg` |
| `6001` | `ADMIN_AUTH` | 登录已失效，请重新登录 | 401 | 仅后台：跳登录页 |
| `6002` | `ADMIN_FORBIDDEN` | 没有该操作权限 | 403 | 仅后台：提示无权限；敏感操作二次密码错误也返回此码 |
| `6003` | `ADMIN_TOTP` | 动态验证码错误 | 200 | 仅后台：重新输入 6 位动态码 |
| `9001` | `VERSION_LOW` | 版本过低，请升级后使用 | 200 | **强制升级**：跳应用商店/下载页，禁止继续调用业务接口 |

---

## 5. 授权流程时序

```
 Android 客户端                                    doutu-server
      │                                                │
 (0)  │── GET /api/v1/time ──────────────────────────► │  校准时钟（响应 ts / data.server_ts）
      │◄─ {code:0, data:{server_ts, server_time}} ─────│  （仍需带 X-Timestamp / X-Nonce；超窗则用响应 ts 校准）
      │                                                │
 (1)  │── POST /api/v1/auth/register ────────────────► │  MASTER_KEY 签名；邀请码 + 用户名 + 手机号 + 两次密码
      │    body: {invite_code, username, phone,       │  （老用户改走 POST /auth/login）
      │           password, password_confirm,         │  手机号或用户名都能登录
      │           nickname?}                          │  一个邀请码只能创建一个账号
      │◄─ data:{token, refresh, sign_key, expire_at,   │  会话建立（默认 24h，SESSION_TTL_HOURS）
      │        expire_in:86400, user:{...activated},   │  注册出来默认未激活（邀请码 duration=0）
      │        activated, invite:{batch_no,masked}} ──│  refresh 有效期 30 天
      │                                                │
 (2)  │  进入会话期：所有业务请求带 X-Token + sign_key 签名
      │  ⚠ user.activated=false 只是「未开通会员」：**业务接口照常可用**，
      │    成品会带水印；客户端叠加全屏水印遮罩。后台开通
      │    （POST /admin/api/users/:id/activate）后遮罩消失、成品免水印
      │  ⚠ 只有账号被封禁（status=3）才会被拒（2004）
      │── POST /api/v1/auth/heartbeat ───────────────► │  续期：expire_at = now + 24h
      │    （建议 5~10 分钟一次）                       │
      │◄─ data:{expire_at, expire_in, user:{...}} ─────│
      │── GET /api/v1/auth/status ───────────────────► │  查状态/配额（不续期）
      │◄─ data:{status, activated, expire_days, ...} ──│
      │                                                │
 (3)  │  sign_key 需要轮换（例如 App 冷启动、疑似泄漏、
      │  跨天/跨版本）时：
      │── POST /api/v1/auth/handshake ───────────────► │  MASTER_KEY 签名 + 新 nonce
      │    （body 可带 device_id）                      │  服务端校验设备已授权且账号可用
      │◄─ data:{server_random, sign_key(新), token,    │  旧 sign_key 立即失效
      │        sign_expire_at} ────────────────────────│  ⚠ token 与 expire_at 不变
      │   本地可校验：HMAC(MASTER_KEY,                 │
      │     server_random:nonce:device_id) == sign_key │
      │                                                │
 (4)  │  token 过期/被拒（2005）时：
      │── POST /api/v1/auth/refresh ─────────────────► │  MASTER_KEY 签名；body:{refresh}
      │◄─ data:{token(新), expire_at, expire_in,       │  sign_key 不变；refresh 30 天内有效
      │        server_ts} ────────────────────────────│
      │  若 refresh 也失效 → 回到 (1) 重新登录/注册      │
      │                                                │
 (5)  │── POST /api/v1/auth/logout ──────────────────► │  撤销当前会话（需 session 签名）
      │                                                │
 (6)  │── POST /api/v1/auth/unbind ──────────────────► │  解绑设备（每月限次，默认 2 次）
      │                                                │  解绑后该设备所有会话立即失效 → 2005
```

补充规则：

- 同一设备上重新 `register`/`login` 会**撤销该设备旧会话**（避免堆叠无效会话），客户端必须用新返回的 `token` 与 `sign_key` 覆盖本地值。
- 邀请码注册**没有幂等重试**：一个邀请码只能创建一个账号，同一个码第二次注册（任何设备）都返回 `1001 该邀请码已被使用`。
- 注册/登录的响应里 `sign_key` 是随机值；`handshake` 的 `sign_key` 是可推导值（见 3.3）。
- **未开通会员（`activated=false`）不是错误状态**：账号能注册、能登录，也能**正常调用全部业务接口**（解析/批量/任务/历史/工单/解绑/下单），只是成品会带水印、客户端叠加遮罩。服务端只拦封禁（`2004`）。开通由后台「管理账号 → 开通会员」完成（见 6.9）。

---

## 6. 接口详解

### 6.0 接口数量与分组

| 分组 | 数量 | 备注 |
| --- | --- | --- |
| 授权类 `/api/v1/auth/*` | 11 | 10 个业务接口（`register` / `login` / `profile`（改用户名） / `handshake` / `refresh` / `logout` / `heartbeat` / `unbind` / `status`，以及仍挂载但固定返回 `4004` 的旧 `activate`）+ 1 个调试接口 `sign_debug` |
| 去水印业务 `/api/v1/dw/*` | 7 | parse / task(查) / batch / history / task(删) / platforms / env |
| 成品下载 `/dw/file/*` | 1 | 不走客户端签名 |
| 配置与升级 | 4 | config / time / upgrade/check / upgrade/crash |
| 支付 `/api/v1/pay/*` | 5 | goods / create / status / notify / mock 收银台 |
| 工单 `/api/v1/ticket/*` | 4 | create / list / detail / reply |
| 意见反馈 `/api/v1/feedback/*` | 2 | create（提交，root 签名）/ image（读图，无签名） |
| 健康检查 `/healthz` | 1 | 无鉴权 |
| **客户端可见接口合计** | **35** | |
| 后台 `/admin/api/*` | 84 | 清单见 6.9 |
| **本文档收录接口总数** | **118** | |

约定说明：

- 「签名」列为 `MASTER_KEY` / `会话 sign_key` / `无需` 三种。
- 所有 `/api/v1/*`（除支付回调与 mock 收银台）都**必须**带 `X-Timestamp` 与 `X-Nonce`（长度 >= 8）；下表中不再重复强调。
- **「需激活」列的含义已变更**：会员只决定「有没有水印」，因此现在**没有任何接口因为未开通会员而被拒**；
  该列一律为「否」，只有账号被封禁（`status=3` → `2004`）才会被拒。表中残留的个别措辞以本条为准。

---

### 6.1 授权类（11 个）

前缀：`/api/v1/auth`，源文件 `src/modules/auth/routes.js`。

#### 6.1.1 `POST /api/v1/auth/register` —— 邀请码注册 + 设备绑定

> **模型变更**：卡密体系已下线，「输入卡密激活」入口不存在了。新用户凭**邀请码**自助注册，
> **一个邀请码只能创建一个账号**；注册出来的账号默认是**未激活**（除非该邀请码预设了 `duration > 0`），
> 是否开通会员由后台「管理账号激活」决定（见 6.9）。
>
> **本次新增必填字段 `username`**：它和 `phone` 一样是登录标识（登录框两者都收），校验规则、错误文案与唯一性口径见下表；
> 改名走 `POST /api/v1/auth/profile`（见 6.1.3）。

| 项目 | 值 |
| --- | --- |
| 签名 | `MASTER_KEY`（root 模式） |
| 需激活 | 否（未激活正是本接口的正常出口） |
| 限流 | 单 IP 20 次 / 60 秒（bucket `register`）；另有防爆破锁定（见 9.4，注册沿用原激活的计数键与阈值） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `invite_code` | string | 是 | 邀请码。12 位字母数字（服务端剔除横线/空格/小写等非字母数字字符后大写化）；归一化后长度不等于 12 返回 `1001 邀请码格式不正确（12 位字母数字）`。展示格式为 `XXXX-XXXX-XXXX`，两种写法都收 |
| `username` | string | 是 | **登录用户名（新增必填）**。2~20 位，只允许中文、字母、数字、下划线、短横线（正则 `/^[\u4e00-\u9fa5A-Za-z0-9_-]{2,20}$/`，首尾空白先 trim）；**不允许长得像手机号**（`/^1[3-9]\d{9}$/` 一律拒绝，因为登录框同时接受手机号与用户名，两个命名空间必须互不重叠）。唯一性**忽略大小写**（`Admin` 与 `admin` 视为同一个名字，SQL 用 `LOWER()`），并且不能与任何人的 `phone` 相同。失败全部为 `1001`，`msg` 见下方错误表 |
| `phone` | string | 是 | 6~20 位数字，作为**手机号登录标识**入库（不再兼任 `username`）；格式不符返回 `1001 请输入正确的手机号`；已被注册返回 `1001 该手机号已注册，请直接登录` |
| `password` | string | 是 | 明文密码，长度 < 6 返回 `1001 密码至少 6 位`（服务端 scrypt 哈希后入库） |
| `password_confirm` | string | 是 | 二次确认密码，与 `password` 不一致返回 `1001 两次输入的密码不一致`；兼容旧字段名 `password2` |
| `nickname` | string | 否 | 昵称，最长 64 字符 |
| `device_id` | string | 否（建议走 `X-Device-Id` 头） | 设备号；头缺失时才读该字段，两者都缺返回 `1001 缺少设备标识` |
| `os_version` | string | 否 | 系统版本，入库/日志用 |
| `brand` | string | 否 | 品牌，入库/日志用 |
| `model` | string | 否 | 机型，入库/日志用 |

请求示例：

```json
{
  "invite_code": "ABCD-EFGH-JKMN",
  "username": "xiaoming01",
  "phone": "13800000001",
  "password": "Passw0rd!",
  "password_confirm": "Passw0rd!",
  "nickname": "小明",
  "os_version": "14",
  "brand": "Xiaomi",
  "model": "23127PN0CC"
}
```

响应示例（邀请码 `duration=0`，注册后待后台开通）：

```json
{
  "code": 0,
  "msg": "注册成功，可直接使用（未开通会员，成品会带水印）",
  "data": {
    "token": "5f1c0b7a8d2e4936b0a1c3d5e7f90123",
    "refresh": "a1b2c3d4e5f60718293a4b5c6d7e8f90",
    "sign_key": "0d9c8b7a6f5e4d3c2b1a0918273645fe",
    "expire_at": "2026-03-16 21:04:05",
    "expire_in": 86400,
    "server_ts": 1771056000,
    "user": {
      "user_id": 101,
      "username": "xiaoming01",
      "nickname": "小明",
      "level": 1,
      "status": 0,
      "status_text": "未开通会员",
      "activated": false,
      "bind_at": null,
      "expire_at": null,
      "expire_days": 0,
      "permanent": false,
      "max_device": 1,
      "quota_day": 20,
      "total_task": 0,
      "quota_left": 20,
      "quota_used": 0
    },
    "activated": false,
    "invite": { "batch_no": "IV20260214-001", "code_masked": "****JKMN" }
  },
  "ts": 1771056000,
  "sign": "b7c1e0a9d4f2..."
}
```

各字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `token` / `refresh` / `sign_key` | string | 注册即建会话（与 `login` 同一套会话实现），客户端可直接进入已登录态 |
| `expire_at` / `expire_in` | string / int | **会话**到期时间与剩余秒数（不是会员到期时间）；会员到期时间在 `user.expire_at` |
| `user.username` / `user.nickname` | string | **登录用户名**（注册必填，`authStatusPayload` 原样回传）；`nickname` 为注册时传的值，未传时**回退为 `username`** |
| `user.activated` | bool | 是否已开通会员。**客户端决定「是否显示未激活水印遮罩」的唯一依据**：`false` → 全屏叠加水印 + 展示未激活提示；`true` → 无遮罩 |
| `user.expire_at` | string / null | 会员到期时间；未激活时为 `null`（不是 `1970-01-01`） |
| `user.level` / `max_device` / `quota_day` | int | 来自该邀请码的预设值（未预设时 `level=1`、`max_device=1`、`quota_day=20`） |
| `activated` | bool | 与 `user.activated` 同值的顶层快捷字段 |
| `invite.batch_no` | string | 所使用的邀请码批次号（格式 `IVyyyymmdd-序号`） |
| `invite.code_masked` | string | 邀请码掩码 `****末 4 位`（**完整码不回传**，客户端不需要也不应保存） |

常见错误码（全部为 `1001`，`msg` 直接展示给用户）：

| 触发条件 | `msg` |
| --- | --- |
| 邀请码格式不对（按归一化后长度 != 12 判断） | `邀请码格式不正确（12 位字母数字）` |
| 邀请码不存在 | `邀请码不存在，请核对后重试` |
| 邀请码已被使用 | `该邀请码已被使用` |
| 邀请码已作废 | `该邀请码已作废` |
| 邀请码已过期 | `该邀请码已过期` |
| 用户名为空/全空白 | `请输入用户名` |
| 用户名长度不在 2~20 位 | `用户名长度需为 2~20 位` |
| 用户名含中文、字母、数字、下划线、短横线以外的字符 | `用户名只能包含中文、字母、数字、下划线或短横线` |
| 用户名长得像手机号（`1[3-9]` + 9 位数字） | `用户名不能是手机号，请换一个` |
| 用户名已被占用（忽略大小写），或与别人的 `phone` 相同 | `该用户名已被使用，请换一个` |
| 手机号格式不对（非 6~20 位数字） | `请输入正确的手机号` |
| 手机号已注册 | `该手机号已注册，请直接登录` |
| 两次密码不一致 | `两次输入的密码不一致` |
| 密码过短（< 6 位） | `密码至少 6 位` |
| 缺少设备标识 | `缺少设备标识` |

其它错误码：

| code | 触发条件 | 说明 |
| --- | --- | --- |
| `5001` | `register_open=false`（后台关闭注册通道） | `msg` = `注册通道已关闭，请联系客服开通账号`；此时只能由后台创建/激活账号 |
| `3002` | 同 IP 触发注册/激活防爆破锁定（见 9.4） | `msg` 形如 `尝试次数过多，请 N 分钟后再试` |
| `5000` | 服务端未配置 `MASTER_KEY`、或邀请码占用时并发冲突（`邀请码占用失败，请重试`） | 可重试一次 |

> **注册接口不会返回 `2003`**：服务端绑定设备失败时只写日志、不阻断注册（账号与邀请码占用已经成功）。设备数超限（`2003`）只会在 `login`、`handshake` 等后续接口出现。

备注：

- 并发安全：服务端用「事务 + 行级锁 + 条件更新影响行数」保证同一个邀请码在并发下也只会创建一个账号，失败整体回滚（不会出现「码被占用但账号没建」或「一个码两个号」）。
- 同一设备近期出现过别的账号会写一条风控事件（`risk_event`，`multi_account`），**不阻断注册**。
- 注册成功后才绑定设备；绑定在事务外，失败不影响注册结果（见上表 `2003` 一行）。
- 登录日志 `login_log` 会记录 `邀请码注册（未开通会员，可用但带水印）` 或 `邀请码注册并开通`。
- `username` 是**登录标识**，注册成功后 `data.user.username` 就是它；`data.user.nickname` 为传入的 `nickname`，未传时**回退为 `username`**（`authStatusPayload`，`src/services/user.js:131`）。注册时还会向 `user_profile_log` 写一条 `field='register'` 的记录（`new_value` 里含 `username`）。

#### 6.1.2 `POST /api/v1/auth/login` —— 账密登录（手机号或用户名 + 密码）

| 项目 | 值 |
| --- | --- |
| 签名 | `MASTER_KEY` |
| 需激活 | 否（但封禁账号会被拒） |
| 限流 | 单 IP 30 次 / 60 秒（bucket `login`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 二选一 | **登录标识：手机号或用户名都可以**（注册时填的 `username`，或注册/登录用的 `phone`）。服务端用 `findByLogin()` **先按用户名忽略大小写查、查不到再按手机号查**；因为用户名不允许长得像手机号，两个命名空间不会重叠。大小写不敏感：`Admin` 与 `admin` 命中同一个账号 |
| `phone` | string | 二选一 | 手机号（**兼容别名**，仅在 `username` 为空/未传时使用；两者都传时以 `username` 为准） |
| `password` | string | 是 | 密码（服务端用 scrypt 校验） |
| `device_id` | string | 否 | 建议走 `X-Device-Id` |
| `brand` / `model` / `os_version` | string | 否 | 登录时刷新设备信息 |

请求示例（用**手机号**登录）：

```json
{ "username": "13800000001", "password": "Passw0rd!", "os_version": "14", "brand": "Xiaomi", "model": "23127PN0CC" }
```

请求示例（用**用户名**登录，忽略大小写；`Admin` / `admin` 命中同一账号）：

```json
{ "username": "xiaoming01", "password": "Passw0rd!" }
```

> `phone` 是兼容别名：`{ "phone": "13800000001", "password": "..." }` 与上面的手机号写法等价；两个字段都传时以 `username` 为准。

响应示例（结构与 `register` 的 `data` 相同，但**没有** `activated` 顶层字段与 `invite` 对象，`activated` 只在 `user` 里）：

```json
{
  "code": 0,
  "msg": "登录成功",
  "data": {
    "token": "1a2b3c4d5e6f708192a3b4c5d6e7f801",
    "refresh": "9081726354a5b6c7d8e9f0a1b2c3d4e5",
    "sign_key": "f0e1d2c3b4a5968778695a4b3c2d1e0f",
    "expire_at": "2026-03-16 21:10:00",
    "expire_in": 86400,
    "server_ts": 1771056000,
    "user": {
      "user_id": 101,
      "username": "13800000001",
      "nickname": "小明",
      "level": 2,
      "status": 1,
      "status_text": "正常",
      "activated": true,
      "bind_at": "2026-02-14 21:04:05",
      "expire_at": "2026-03-16 21:04:05",
      "expire_days": 30,
      "permanent": false,
      "max_device": 1,
      "quota_day": 30,
      "total_task": 12,
      "quota_left": 28,
      "quota_used": 2
    }
  },
  "ts": 1771056000,
  "sign": "3c9d..."
}
```

常见错误码：`1001`（缺账号/密码 → `请输入账号与密码`；账号或密码错误 → `账号或密码错误`）、`2004`（已封禁）、`2003`（已绑定设备数超限，登录时会尝试绑定设备）、`3002`。

**未开通会员的账号也能登录、也能用**（重要）：

- 邀请码注册出来、后台还没开通会员的账号可以直接登录：只要密码正确、账号未被封禁，`login` 就返回 `code=0`。
- 此时响应里 `user.activated=false`、`user.status=0`、`status_text="未开通会员"`、`user.expire_at=null`；客户端按此**叠加水印遮罩 + 展示 `activation_notice`**，但功能照常可用。
- **业务接口不会被拦**：`/dw/parse`、`/dw/batch`、`/dw/history`、`/pay/create`、`/ticket/*`、`/auth/unbind` 都能正常调用，只是解析出来的成品会带水印（服务端按会员状态决定，见 6.2.1 的 `want_watermark` 说明）。
- 后台开通（`POST /admin/api/users/:id/activate`）后，客户端下一次 `status`/`heartbeat` 就会看到 `activated=true`，遮罩随即消失，成品也不再带水印。

#### 6.1.3 `POST /api/v1/auth/profile` —— 修改用户名（会话签名）

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否（未开通会员也能改名） |
| 设备校验 | 不强制（`requireDevice:false`），未带设备号也可调用 |
| 限流 | 单用户 10 次 / 60 秒（bucket `profile`）；单 IP 不限 |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | **新的**用户名。校验与注册**完全同一套**（`userService.validateUsername`）：2~20 位，只允许中文、字母、数字、下划线、短横线（`/^[\u4e00-\u9fa5A-Za-z0-9_-]{2,20}$/`）；**不能长得像手机号**（`/^1[3-9]\d{9}$/` 一律拒绝）。唯一性**忽略大小写**，也不能与任何人的 `phone` 相同；与当前用户名相同（忽略大小写）返回 `1001 新用户名与当前用户名相同` |
| `nickname` | string | 否 | 一起改昵称；传空串视为不改；最长 64 字符（超出截断） |

请求示例：

```json
{ "username": "xiaoming01", "nickname": "小明" }
```

响应示例（`data.user` 就是 `authStatusPayload`，字段含义见 6.1.9）：

```json
{
  "code": 0,
  "msg": "用户名已修改",
  "data": {
    "user": {
      "user_id": 101,
      "username": "xiaoming01",
      "nickname": "小明",
      "level": 2,
      "status": 1,
      "status_text": "正常",
      "activated": true,
      "bind_at": "2026-02-14 21:04:05",
      "expire_at": "2026-03-16 21:04:05",
      "expire_days": 30,
      "permanent": false,
      "max_device": 1,
      "quota_day": 30,
      "total_task": 12,
      "quota_left": 28,
      "quota_used": 2
    }
  },
  "ts": 1771056000,
  "sign": "..."
}
```

错误码：

| code | 触发条件 | `msg` |
| --- | --- | --- |
| `1001` | `username` 缺失或全为空白 | `请输入新的用户名` |
| `1001` | 长度不在 2~20 位 | `用户名长度需为 2~20 位` |
| `1001` | 含中文、字母、数字、下划线、短横线以外的字符 | `用户名只能包含中文、字母、数字、下划线或短横线` |
| `1001` | 长得像手机号（`1[3-9]` + 9 位数字） | `用户名不能是手机号，请换一个` |
| `1001` | 与当前用户名相同（忽略大小写） | `新用户名与当前用户名相同` |
| `1001` | 已被别人占用（忽略大小写），或与别人的 `phone` 相同 | `该用户名已被使用，请换一个` |
| `2005` | token 缺失/失效（会话签名未通过） | `身份过期，请重新登录` |
| `3002` | 同一用户 60 秒内超过 10 次 | `操作过于频繁，请稍后再试`（带 `data.retry_after`） |
| `4004` | 会话有效但账号已被删除 | `账号不存在` |

语义要点：

- **改完不需要重新登录**：`session` / `token` / `refresh` / `sign_key` **全部不变**，直接把响应里的 `data.user` 覆盖本地缓存的用户信息即可。
- **旧用户名当场失效、新用户名立即可用**：`/auth/login` 每次都按库里的当前值查（忽略大小写），下一次用旧用户名登录会得到 `1001 账号或密码错误`。
- **手机号登录完全不受影响**：`phone` 字段没有变化，手机号始终是有效登录标识。
- **变更留痕**：服务端向 `user_profile_log` 插入一条 `field='username'`、`old_value` / `new_value` 为旧/新用户名的记录（后台代改时还会带 `admin_id`）；同时写一条 `login_log`（`msg` 形如 `用户名修改为 <新用户名>`）。
- 用户名与手机号是**两个互不重叠的登录命名空间**（用户名不允许是手机号形态），所以改名既不会顶掉别人的手机号，也不会让别人的手机号顶掉这个名字。

#### 6.1.4 `POST /api/v1/auth/handshake` —— 换发会话级动态密钥

| 项目 | 值 |
| --- | --- |
| 签名 | `MASTER_KEY` |
| 需激活 | 否（`assertUsable` 现在只拦封禁；未开通会员照常可用） |
| 限流 | 单 IP 60 次 / 60 秒（bucket `handshake`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | 否（建议走 `X-Device-Id`） | 设备号，缺则返回 `1001 缺少设备标识` |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "server_random": "9f8e7d6c5b4a39281706f5e4d3c2b1a0",
    "sign_key": "4d5e6f708192a3b4c5d6e7f8091a2b3c",
    "token": "1a2b3c4d5e6f708192a3b4c5d6e7f801",
    "server_ts": 1771056000,
    "sign_expire_at": "2026-03-16 21:10:00"
  },
  "ts": 1771056000,
  "sign": "77aa..."
}
```

常见错误码：`1001`（缺设备标识）、`2005`（设备未授权/账号不存在/会话已撤销）、`2004`（账号被封禁）、`2003`（设备未授权）。

备注：

- 服务端取该设备最近一条未撤销会话；若会话已被清理但设备仍授权，会**重建会话**（此时返回的 `token` 是新会话的 token）。
- `sign_key = HMAC-SHA256(MASTER_KEY, server_random + ":" + 本次请求 nonce + ":" + 归一化 device_id)`；请在本接口发送**新生成**的 nonce，并保留它以便本地校验。
- 本接口**不延长** `expire_at`，需要配合 heartbeat。

#### 6.1.5 `POST /api/v1/auth/refresh` —— 刷新 token

| 项目 | 值 |
| --- | --- |
| 签名 | `MASTER_KEY` |
| 需激活 | 否 |
| 限流 | 单 IP 60 次 / 60 秒（bucket `refresh`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `refresh` | string | 是 | 激活/登录返回的 `refresh` 值；缺失返回 `1001 缺少 refresh` |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "token": "b1c2d3e4f50718293a4b5c6d7e8f9012",
    "expire_at": "2026-03-16 22:00:00",
    "expire_in": 86400,
    "server_ts": 1771060000
  },
  "ts": 1771060000,
  "sign": "55ee..."
}
```

常见错误码：`1001`、`2005`（refresh 不存在/会话已撤销/超过 30 天刷新窗口）。

备注：`sign_key` **不变**；返回的新 `token` 必须覆盖本地旧 token。

#### 6.1.6 `POST /api/v1/auth/logout` —— 退出登录

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

请求字段：无（可传空 JSON `{}`；请求体为空串时 `body_hash = sha256("")`）。

响应示例：

```json
{ "code": 0, "msg": "已退出登录", "data": null, "ts": 1771056000, "sign": "..." }
```

常见错误码：`2005`（token 缺失或已失效）。

#### 6.1.7 `POST /api/v1/auth/heartbeat` —— 心跳续期 + 在线上报

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否（**未激活也能心跳**，会话不会被回收） |
| 限流 | 单 IP 120 次 / 60 秒（默认）；单用户 120 次 / 60 秒（bucket `hb`） |

请求字段：无。

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "expire_at": "2026-03-17 21:10:00",
    "expire_in": 86400,
    "server_ts": 1771142400,
    "user": {
      "user_id": 101,
      "username": "13800000001",
      "nickname": "小明",
      "level": 2,
      "status": 1,
      "status_text": "正常",
      "activated": true,
      "bind_at": "2026-02-14 21:04:05",
      "expire_at": "2026-03-16 21:04:05",
      "expire_days": 29,
      "permanent": false,
      "max_device": 1,
      "quota_day": 30,
      "total_task": 14,
      "quota_left": 26,
      "quota_used": 4
    }
  },
  "ts": 1771142400,
  "sign": "..."
}
```

备注：续期是「`now + SESSION_TTL_HOURS`」的滑动续期（默认 24h），同时刷新 `user_device.last_at`。建议 5~10 分钟一次；不要高频（有 120 次/分钟的限流）。`data.expire_at` 是**会话**到期时间（不是会员到期时间，会员到期时间在 `data.user.expire_at`）；客户端可把 `data.user.activated` 当作水印遮罩的轮询依据。

#### 6.1.8 `POST /api/v1/auth/unbind` —— 解绑设备

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否（**未开通会员也能解绑**；只有封禁账号会被拒） |
| 限流 | 无；但有每月次数上限（`unbind_limit_month`，默认 2） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `device_id` | string | 否 | 要解绑的设备号；缺省解绑当前会话所在设备 |

响应示例：

```json
{
  "code": 0,
  "msg": "已解绑",
  "data": { "device_id": "abc123def456", "unbind_left": 1 },
  "ts": 1771056000,
  "sign": "..."
}
```

常见错误码：`3002`（本月解绑次数已用完）、`4004`（设备未绑定）、`2004`（账号被封禁）。

备注：解绑会**撤销该设备的全部会话**，设备立即收到 `2005`。

#### 6.1.9 `GET /api/v1/auth/status` —— 授权状态 / 到期时间 / 剩余配额

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否（这是查询状态的接口，**未激活也必须能查**） |
| 设备校验 | 不强制（`requireDevice:false`），未带设备号也可查询 |
| 限流 | 无 |

请求参数：无（query 可选，不参与签名）。

响应示例（已开通账号）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "user_id": 101,
    "username": "13800000001",
    "nickname": "小明",
    "level": 2,
    "status": 1,
    "status_text": "正常",
    "activated": true,
    "bind_at": "2026-02-14 21:04:05",
    "expire_at": "2026-03-16 21:04:05",
    "expire_days": 30,
    "permanent": false,
    "max_device": 1,
    "quota_day": 30,
    "total_task": 12,
    "quota_left": 28,
    "quota_used": 2,
    "device_id": "abc123def456",
    "session_expire_at": "2026-03-15 21:04:05",
    "server_ts": 1771056000
  },
  "ts": 1771056000,
  "sign": "..."
}
```

响应示例（邀请码刚注册、后台未开通）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "user_id": 102,
    "username": "xiaoming02",
    "nickname": "xiaoming02",
    "level": 1,
    "status": 0,
    "status_text": "未开通会员",
    "activated": false,
    "bind_at": null,
    "expire_at": null,
    "expire_days": 0,
    "permanent": false,
    "max_device": 1,
    "quota_day": 20,
    "total_task": 0,
    "quota_left": 20,
    "quota_used": 0,
    "device_id": "abc123def456",
    "session_expire_at": "2026-03-15 21:04:05",
    "server_ts": 1771056000
  },
  "ts": 1771056000,
  "sign": "..."
}
```

`user` 相关字段（`authStatusPayload`，下面是**全部**真实字段）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` / `username` / `nickname` | int / string | 账号信息（`nickname` 为空时回退为 `username`） |
| `level` | int | 用户等级（来自邀请码预设或后台/订单设置） |
| `status` | int | **实时计算**的账号状态：0 未开通会员 / 1 正常 / 2 已过期 / 3 已封禁（见 7.3） |
| `status_text` | string | 上表的中文文案 |
| `activated` | bool | `status === 1` 的布尔化。**客户端决定「是否显示未激活水印遮罩」的唯一依据**：`false` → 全屏叠加 `watermark_overlay_text` 水印并展示 `activation_notice`；`true` → 不显示 |
| `bind_at` | string / null | 首次开通时间；未激活为 `null` |
| `expire_at` | string / null | 会员到期时间；**未激活时为 `null`**，永久账号为 `2099-12-31 23:59:59` |
| `expire_days` | int | 距到期天数（已过期/无法解析为 0） |
| `permanent` | bool | `expire_at` 是否以 `2099-` 开头 |
| `max_device` / `quota_day` | int | 设备上限 / 每日配额（`quota_day=0` 表示不限量） |
| `total_task` | int | 累计任务数 |
| `quota_left` / `quota_used` | int / null | 今日剩余/已用次数（不限量时 `quota_left=null`） |

常见错误码：`2005`（token 缺失/失效/设备变更）。**本接口不会因为没有开通会员而返回 `2001`。**

#### 6.1.10 `POST /api/v1/auth/activate` —— 旧卡密激活接口（**已停用**）

| 项目 | 值 |
| --- | --- |
| 签名 | 不校验（路由以 `mode:none` 挂载，任何签名都会被忽略） |
| 需激活 | 否 |
| 限流 | 无 |
| 当前行为 | **固定返回 `4004`**，不读任何业务字段、不建会话、不写库 |

响应（老客户端兼容处理）：

```json
{
  "code": 4004,
  "msg": "卡密激活已停用，请使用邀请码注册，或联系客服开通",
  "data": null,
  "ts": 1771056000,
  "sign": null
}
```

说明：

- 卡密体系下线后，客户端不再有「输入卡密激活」入口；服务端保留这个路径只是为了给还在用老版本 App 的用户一个**明确的失败提示**，而不是含糊的成功或 404 页面。
- HTTP 状态是 `404`（`4004` 的映射见 4.2），客户端应把 `msg` 原样展示，并引导用户升级到「邀请码注册」流程；**不要重试**。
- 新用户/新设备一律走 `POST /api/v1/auth/register`（邀请码注册），开通由后台完成。

#### 6.1.11 `POST /api/v1/auth/sign_debug` —— 签名自检（仅非生产环境）

| 项目 | 值 |
| --- | --- |
| 签名 | 无需（`mode:none`） |
| 可用环境 | **仅 `NODE_ENV !== 'production'`**；生产环境返回 `4004 接口不存在` |
| 校验链 | 仍会执行版本吊销检查、时间窗校验与 nonce 防重放（`mode:none` 只是不校验签名） |
| 限流 | 无 |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `secret` | string | 是（否则用空密钥计算） | 用于计算签名的密钥 |
| `method` | string | 否 | 默认 `POST`。会被接收但不参与待签串计算（`buildSignPayload` 不使用 method），仅为兼容签名函数入参 |
| `path` | string | 否 | 默认 `/` |
| `timestamp` | int | 否 | 默认当前秒 |
| `nonce` | string | 否 | 默认 `0123456789abcdef` |
| `device_id` | string | 否 | 默认空串。**该接口不做归一化**，请传归一化后的值 |
| `body_hash` | string | 否 | 默认 `sha256("")`；若要复现某个请求，请显式传入 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "body_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "sign": "6a5b4c3d2e1f00998877665544332211aabbccddeeff00112233445566778899"
  },
  "ts": 1771056000,
  "sign": null
}
```

注意：`data.body_hash` 是**本接口自身请求体**的哈希，不是被调试请求的哈希；`data.sign` 用请求里的 `secret` 计算，可用于比对你本地算法的输出。响应不签名（`sign:null`）。

---

### 6.2 去水印业务（7 个）

前缀：`/api/v1/dw`，源文件 `src/modules/dw/routes.js` + `src/modules/dw/taskService.js`。

#### 异步任务模型（客户端必读）

```
① POST /api/v1/dw/parse            → 立即返回 task_no（status=0 排队中），并已扣减配额
② GET  /api/v1/dw/task/{task_no}   → 轮询；status: 0→1→2→3（或 4 失败 / 5 超时）
③ 仅当 status == 3 时 data.out_url 才有值（图集为 data.images[]）
④ out_url 有效期 = expire_sec（默认 900 秒 = 15 分钟），必须立即开始下载/播放
⑤ 客户端不要长期缓存 out_url：过期或被篡改都会返回 1002；需要重新拉取任务详情换新链接
```

- 轮询建议：提交后 1~2 秒一次；`status=0` 时退避到 2~3 秒；总超时按 `estimate_sec`（默认 25s）与 `task_timeout_sec`（默认 120s）估算。
- 失败与超时都会**退还配额**（`status=4/5`），因此 `quota_left` 会在下次查询时回升。
- 相同的「用户 + 链接 + 水印策略」在 24 小时内命中缓存时，直接返回 `status=3` 的新任务与 `reused:true`（配额仍计 1 次）。

#### 6.2.1 `POST /api/v1/dw/parse` —— 提交解析任务

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | **是** |
| 限流 | 单 IP 60 次 / 60 秒；单用户 30 次 / 60 秒（bucket `dw`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `share_text` | string | 三选一 | 分享文案，服务端用正则从中提取第一个 http(s) 链接 |
| `link` | string | 三选一 | `share_text` 的别名（优先级：`share_text` > `link`） |
| `url` | string | 三选一 | 直链，优先于分享文案使用；必须以 `http://` 或 `https://` 开头 |
| `want_watermark` | bool / string / int | 否 | 默认 `true`。接受 `true`/`"true"`/`1`/`"1"`。**最终是否加水印由服务端按会员状态决定**：未开通/已过期 → 成品带水印；已开通会员且 `watermark_free_for_member=1` → 成品免水印。客户端传 `false` 不能关掉水印 |

请求示例：

```json
{
  "share_text": "7.32 复制打开抖音，看看【某某的作品】 https://v.douyin.com/iAbCdEf/",
  "want_watermark": true
}
```

响应示例（未命中缓存）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "task_no": "DW20260214210405000123",
    "status": 0,
    "platform": "douyin",
    "estimate_sec": 25,
    "reused": false,
    "quota_left": 29
  },
  "ts": 1771056000,
  "sign": "..."
}
```

响应示例（命中 24h 成品缓存，`status` 直接是 3）：

```json
{
  "code": 0,
  "msg": "已复用缓存结果",
  "data": {
    "task_no": "DW20260214211000000456",
    "status": 3,
    "platform": "douyin",
    "estimate_sec": 0,
    "reused": true,
    "quota_left": 28
  },
  "ts": 1771056000,
  "sign": "..."
}
```

常见错误码：`1001`（未检测到视频链接 / 链接格式不正确）、`4001`（该平台被后台临时禁用，`msg` 形如 `抖音 暂时不可用，请稍后再试`）、`3001`（配额用尽）、`3002`（限流）、`2004`（账号被封禁）、`5001`（维护中）。

#### 6.2.2 `GET /api/v1/dw/task/{task_no}` —— 查询任务状态与成品地址

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |
| 路径参数 | `task_no` 为 `parse`/`batch` 返回的任务号（`DW` + 14 位时间 + 6 位随机，共 22 位） |

响应示例（已完成视频任务）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "task_no": "DW20260214210405000123",
    "status": 3,
    "status_text": "已完成",
    "platform": "douyin",
    "platform_name": "抖音",
    "media_type": "VIDEO",
    "title": "某某的作品",
    "cover": "https://p3-sign.douyinpic.com/xxxx.jpeg",
    "watermark": 1,
    "out_size": 4823910,
    "duration": 15.4,
    "duration_ms": 3120,
    "err_msg": null,
    "created_at": "2026-02-14 21:04:05",
    "finished_at": "2026-02-14 21:04:12",
    "expire_sec": 900,
    "out_url": "https://api.example.com/dw/file/out/2026-02-14/m1abcd2ef345.mp4?exp=1771056900&sign=2f9c...",
    "images": [],
    "quota_left": 29
  },
  "ts": 1771056012,
  "sign": "..."
}
```

响应示例（图集任务；`images` 为多个签名 URL，`out_url` 等于 `images[0]`）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "task_no": "DW20260214210405000124",
    "status": 3,
    "status_text": "已完成",
    "platform": "xiaohongshu",
    "platform_name": "小红书",
    "media_type": "IMAGE",
    "title": "某某笔记",
    "cover": null,
    "watermark": 1,
    "out_size": 1288341,
    "duration": null,
    "duration_ms": 2210,
    "err_msg": null,
    "created_at": "2026-02-14 21:04:20",
    "finished_at": "2026-02-14 21:04:26",
    "expire_sec": 900,
    "out_url": "https://api.example.com/dw/file/images/2026-02-14/m1abcd2ef346.jpg?exp=1771056906&sign=7a1b...",
    "images": [
      "https://api.example.com/dw/file/images/2026-02-14/m1abcd2ef346.jpg?exp=1771056906&sign=7a1b...",
      "https://api.example.com/dw/file/images/2026-02-14/m1abcd2ef347.jpg?exp=1771056906&sign=9c2d..."
    ],
    "quota_left": 29
  },
  "ts": 1771056006,
  "sign": "..."
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `status` / `status_text` | 见 7.1 任务状态字典 |
| `media_type` | `VIDEO` 或 `IMAGE`（图集） |
| `watermark` | `1` 表示成品已加水印，`0` 表示未加水印 |
| `out_size` | 成品字节数（图集为所有图之和）；无值时为 `null` |
| `duration` | 视频时长（秒，1 位小数，来自 `video_ms`）；图集为 `null` |
| `duration_ms` | 本次处理耗时（毫秒，= 完成时间 - 开始时间） |
| `err_msg` | 失败原因 / 超时说明（如 `处理超时（>120s）`）/ 命中缓存时是 `命中缓存` |
| `expire_sec` | `out_url` 的剩余有效期基准（默认 900 秒，来自 `URL_TTL_SEC`） |
| `out_url` | **仅 `status=3` 时非 null**；否则为 `null` |
| `images` | 仅图集任务且 `status=3` 时非空 |
| `quota_left` | 当前用户今日剩余配额 |

常见错误码：`4004`（任务不存在或不属于当前用户）、`2005`。

#### 6.2.3 `POST /api/v1/dw/batch` —— 批量解析（限高等级用户）

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | **是** |
| 限流 | 单 IP 20 次 / 60 秒；单用户 10 次 / 60 秒（bucket `dw-batch`） |
| 业务限制 | 单次条数 <= `batch_max`（默认 10）；用户 `level` >= `batch_min_level`（默认 3） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `links` | array&lt;string&gt; 或 string | 是 | 链接数组；字符串时按换行或英文逗号切分；为空返回 `1001 请提供待解析的链接列表` |
| `want_watermark` | bool | 否 | 默认 `true`；显式传 `false` 才关闭 |

请求示例：

```json
{
  "links": [
    "https://v.douyin.com/iAbCdEf/",
    "https://v.kuaishou.com/xYz123/"
  ],
  "want_watermark": true
}
```

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "results": [
      { "link": "https://v.douyin.com/iAbCdEf/", "ok": true, "task_no": "DW20260214210405000125", "reused": false },
      { "link": "https://v.kuaishou.com/xYz123/", "ok": false, "error": "今日额度已用完" }
    ],
    "quota_left": 28
  },
  "ts": 1771056000,
  "sign": "..."
}
```

常见错误码：`1001`（空列表 / 超条数 / 等级不足）、`2004`（账号被封禁）、`3002`。

备注：批量接口是**部分成功**语义——单条失败不会让整个请求失败（失败项以 `ok:false` + `error` 返回），所以 HTTP/code 仍是 `0`。配额不足的条目会返回 `error:"今日额度已用完"`。

#### 6.2.4 `GET /api/v1/dw/history` —— 历史记录（分页）

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

查询参数（query，不参与签名）：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | int | 否 | 默认 1 |
| `limit` | int | 否 | 默认 20，本接口上限 **50** |
| `page_size` | int | 否 | `limit` 的别名 |
| `status` | int | 否 | 按任务状态过滤（0~5） |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "total": 2,
    "rows": [
      {
        "task_no": "DW20260214210405000125",
        "status": 3,
        "status_text": "已完成",
        "platform": "kuaishou",
        "platform_name": "快手",
        "media_type": "VIDEO",
        "title": "某某的作品",
        "cover": null,
        "watermark": 1,
        "out_size": 3310922,
        "duration": 12.8,
        "duration_ms": 2870,
        "err_msg": null,
        "created_at": "2026-02-14 21:04:05",
        "finished_at": "2026-02-14 21:04:11",
        "expire_sec": 900,
        "out_url": null,
        "images": []
      }
    ],
    "page": 1,
    "limit": 20
  },
  "ts": 1771056000,
  "sign": "..."
}
```

注意：历史列表里的 `out_url` 只在任务 `status=3` 时才会生成签名 URL（与服务端实现一致，可能为 `null`）；需要稳定下载地址时请调 6.2.2。

#### 6.2.5 `DELETE /api/v1/dw/task/{task_no}` —— 删除任务记录

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

请求体：可空。响应示例：

```json
{ "code": 0, "msg": "已删除", "data": null, "ts": 1771056000, "sign": "..." }
```

常见错误码：`4004`（任务不存在或不属于当前用户）。

备注：删除只删数据库记录；已生成的成品文件由存储清理策略处理，删除后其 `out_url` 会返回 `4004`。

#### 6.2.6 `GET /api/v1/dw/platforms` —— 支持的平台列表

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "platforms": [
      { "key": "douyin", "name": "抖音", "hosts": ["v.douyin.com", "www.douyin.com", "douyin.com", "iesdouyin.com"] },
      { "key": "kuaishou", "name": "快手", "hosts": ["v.kuaishou.com", "www.kuaishou.com", "kuaishou.com", "chenzhongtech.com"] },
      { "key": "xiaohongshu", "name": "小红书", "hosts": ["xhslink.com", "www.xiaohongshu.com", "xiaohongshu.com"] }
    ],
    "all_platforms_enabled": true,
    "enabled_keys": ["douyin", "all"],
    "disabled": ["bilibili"]
  },
  "ts": 1771056000,
  "sign": "..."
}
```

字段说明：`platforms` 为内置平台表（**示例仅截取前 3 项**；实际共 19 个平台：`douyin` 抖音、`kuaishou` 快手、`xiaohongshu` 小红书、`bilibili` 哔哩哔哩、`wechat` 微信视频号、`weibo` 微博、`xigua` 西瓜视频、`toutiao` 今日头条、`pipixia` 皮皮虾、`weishi` 微视、`huoshan` 火山小视频、`peiyinxiu` 配音秀、`meipai` 美拍、`acfun` AcFun、`zhihu` 知乎、`lvzhou` 绿洲、`dewu` 得物、`qsmusic` 汽水音乐、`kuwo` 酷我音乐）；`all_platforms_enabled` 表示是否存在覆盖全部平台的线路；`enabled_keys` 是当前启用线路对应的平台键（含 `all`）；`disabled` 是后台临时禁用的平台键（命中返回 `4001`）。

#### 6.2.7 `GET /api/v1/dw/env` —— 媒体环境自检

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |
| 用途 | 后台「面板管理 → 环境自检」，也可由客户端上报诊断信息 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "ffmpeg": {
      "ffmpeg": true,
      "ffprobe": true,
      "font": true,
      "fontPath": "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
      "version": "ffmpeg version 6.1.1 Copyright (c) 2000-2023 the FFmpeg developers"
    },
    "queue": { "queued": 0, "running": 1, "kv_high": 0, "kv_normal": 0, "backlog": 1 },
    "storage_driver": "ok",
    "watermark_font": "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "watermark_enabled": true,
    "task_timeout_sec": 120
  },
  "ts": 1771056000,
  "sign": "..."
}
```

> `storage_driver` 返回真实的存储驱动名（`local` / `oss`），与 `GET /api/v1/config` 一致；
> 服务总开关单独放在 `service_switch` 字段（`on` / `off`）。

---

### 6.3 成品下载（1 个）

#### 6.3.1 `GET /dw/file/{key}?exp={exp}&sign={sign}`

| 项目 | 值 |
| --- | --- |
| 签名 | **不走客户端签名**（无需 `X-Timestamp` / `X-Nonce` / `X-Sign` / `X-Token`） |
| 鉴权 | URL 内的 `exp` + `sign` |
| 源文件 | `src/modules/dw/routes.js`、`src/modules/dw/storage.js` |

URL 参数：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `{key}`（路径部分） | string | 是 | 存储 key，含斜杠，例如 `out/2026-02-14/m1abcd2ef345.mp4`、`images/2026-02-14/m1abcd2ef346.jpg`、`origin/2026-02-14/xxx.mp4`。服务端先 `decodeURIComponent`，含 `..` 返回 `1001 非法路径` |
| `exp` | int | 是 | 秒级 Unix 过期时间；服务端签发值 = 签发时刻 + `URL_TTL_SEC`（默认 900 秒）。`exp*1000 < now` → `1002 链接已过期` |
| `sign` | string | 是 | `sign = hmac_sha256_hex(密钥, "{key}:{exp}")`，小写 hex；`密钥 = MASTER_KEY`（未配置时开发环境回退 `dev-storage-secret`）。**`{key}` 用解码后的原始 key**（不带前导 `/`、不含 query）。不匹配 → `1002 签名不匹配` |
| `download` | string | 否 | 传 `1` 时附加 `Content-Disposition: attachment`，用于保存到本地 |
| `stat` | string | 否 | 传 `1` 时不返回文件体，返回 JSON `{"code":0,"data":{"key":..,"size":..,"exists":true}}`（仍会验签；OSS 驱动下 `size` 为 0） |

行为与响应头：

| 场景 | 行为 |
| --- | --- |
| 普通 GET | HTTP 200，`Content-Type` 按扩展名（`.mp4`→`video/mp4`、`.png/.jpg/.webp/.mp3/.m4a/.zip`），`Content-Length`，`Accept-Ranges: bytes`，`Cache-Control: private, max-age=exp-now` |
| 带 `Range: bytes=0-1023` | HTTP 206，`Content-Range: bytes 0-1023/<size>`，`Content-Length`=区间长度（Android 播放器拖动进度依赖此行为） |
| Range 越界（`start >= size`、`end >= size`、`start > end`） | HTTP 416 + `Content-Range: bytes */<size>`（空体） |
| 文件不存在/已清理 | `4004 文件不存在或已清理`（仅签名有效时才会到这个判断） |

请求示例：

```bash
# 下载（成品 mp4）
curl -o out.mp4 "https://api.example.com/dw/file/out/2026-02-14/m1abcd2ef345.mp4?exp=1771056900&sign=2f9c1d0b..."

# 断点续传 / 播放器拖动
curl -H "Range: bytes=0-1023" "https://api.example.com/dw/file/out/2026-02-14/m1abcd2ef345.mp4?exp=1771056900&sign=2f9c1d0b..."

# 只查大小
curl "https://api.example.com/dw/file/out/2026-02-14/m1abcd2ef345.mp4?exp=1771056900&sign=2f9c1d0b...&stat=1"
```

失败响应示例（`sign` 为 `null`，因为该路由不走客户端签名链）：

```json
{ "code": 1002, "msg": "链接已过期", "data": null, "ts": 1771056901, "sign": null }
```

OSS 驱动说明：当 `STORAGE_DRIVER=oss` 时，`out_url` **不是** `/dw/file/*`，而是 OSS 直连签名 URL：

```
https://{bucket}.{endpoint}/{key}?OSSAccessKeyId={accessKeyId}&Expires={exp}&Signature={base64}
```

其中 `Signature = base64(hmac_sha1(accessKeySecret, "GET\n\n\n{exp}\n/{bucket}/{key}"))`。客户端无需区分，直接按 `out_url` 下载即可（同样支持 OSS 的 Range）。

---

### 6.4 配置与升级（4 个）

#### 6.4.1 `GET /api/v1/config` —— 热更新配置

| 项目 | 值 |
| --- | --- |
| 签名 | `MASTER_KEY`（root 模式，App 启动时无会话密钥也能拉） |
| 需激活 | 否 |
| 限流 | 单 IP 60 次 / 60 秒（bucket `config`） |

查询参数：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `level` | int | 否 | 用户等级（默认 1），用于按等级下发水印策略（客户端只能看到「是否启用」，看不到具体水印参数） |

响应示例（只列真实字段；`...` 处为后台公开配置项）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "config_version": "1",
    "server_ts": 1771056000,
    "watermark": { "enabled": true, "visible": true },
    "supports": {
      "platforms": [
        { "key": "douyin", "name": "抖音", "hosts": ["v.douyin.com", "www.douyin.com", "douyin.com", "iesdouyin.com"] }
      ],
      "disabled_platforms": [],
      "routes": [ { "platform": "douyin", "name": "抖音原生适配器", "priority": 10 } ],
      "batch_max": 10,
      "batch_min_level": 3
    },
    "notices": [
      { "id": 1, "title": "欢迎使用", "content": "本软件仅用于处理您自己拥有版权的素材。", "type": 1, "target_level": "1,2,3,4,5" },
      { "id": 6, "title": "晚上好！一天将尽，放松身心，别太辛苦~", "content": "想改哪里点哪里，如果不行就长按👉", "type": 3, "target_level": "*" }
    ],
    "banner": {
      "enabled": true,
      "rotate_sec": 8,
      "default_title": "",
      "default_hint": "想改哪里点哪里，如果不行就长按👉"
    },
    "latest_version_code": 1,
    "storage_driver": "local",
    "url_ttl_sec": 900,
    "task_timeout_sec": 120,
    "today": "2026-02-14",
    "site_name": "逗图去水印",
    "service_switch": true,
    "maintenance_notice": "系统升级维护中，请稍后重试",
    "customer_service_url": "",
    "customer_service_qq": "",
    "customer_service_wx": "",
    "icp_number": "",
    "trial_quota": 3,
    "batch_max": 10,
    "batch_min_level": 3,
    "pay_channels": ["alipay", "wxpay"],
    "pay_min_amount": "1.00",
    "app_latest_version_code": 1,
    "app_force_update": false,
    "app_update_title": "新版本可用",
    "app_download_url": "",
    "register_open": true,
    "watermark_overlay_text": "开通会员去除水印",
    "activation_notice": "未开通会员，可直接使用（成品带水印）",
    "invite_length": 12,
    "share_link": "https://tdapi.luoyun.xyz/",
    "share_tip": "链接已复制到剪贴板，粘贴给好友即可~"
  },
  "ts": 1771056000,
  "sign": "..."
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `config_version` | string | 配置版本号；后台每次保存配置都会 +1。客户端应缓存整份配置，仅当该值变化时才刷新 |
| `watermark.enabled` | bool | 成品是否可能带水印（配置 `watermark_enabled` 的开总开关）；**具体某个用户的成品是否带水印由他的会员状态决定**（见 6.2.1） |
| `watermark.visible` | bool | 固定 `true`（客户端只能看到这个开关，看不到模板/位置/透明度） |
| `supports.platforms` | array | 内置平台表（同 6.2.6） |
| `supports.disabled_platforms` | array | 后台临时禁用的平台键 |
| `supports.routes` | array | 可用解析线路摘要（`platform` / `name` / `priority`） |
| `supports.batch_max` | int | 批量解析单次上限（默认 10） |
| `supports.batch_min_level` | int | 批量解析最低等级（默认 3） |
| `notices` | array | 生效中的公告。**服务端已完成三重过滤**：`status=1`、`start_at ≤ 服务器当前时间 ≤ end_at`（任一为空=不限）、`target_level` 命中当前等级（`*` = 所有人）。最多 20 条，按 `sort` 升序。`type` 语义：**1 弹窗 / 2 顶部滚动 / 3 首页横幅**（首页大图上的文字框，`title`=第一行、`content`=第二行） |
| `terms` | object | 《用户须知与免责声明》：`text` 正文、`version` 版本号（后台每改一次 +1）、`updated_at` 最后修改时间。**不要求登录**（首次启动就要用）；客户端内置了一份同样的兜底文案，拉不到配置也能显示，拉到不同版本会替换正文并**重置"滑到底 + 等 5 秒"的判定**。修改入口见后台 `POST /admin/api/terms`（仅超级管理员，且每次修改都写审计日志） |
| `banner` | object | 首页横幅渲染参数。**两行文字各自一个池子、各自一个轮播间隔**：`title_list`（上排，人物旁边那行）配 `title_rotate_sec`，`hint_list`（下排，白色长条里那行）配 `hint_rotate_sec`（切换时客户端做上下滚动动画）；另有 `enabled` 总开关、`rotate_sec`（type=3 公告自身的轮播间隔）、`default_title` / `default_hint` 兜底文案、`greetings` 按时段问候语规则。客户端的取词规则：**把当前生效公告的 title/content 排进对应池子最前面，再和池子里的文案一起轮播**（不再整条覆盖，否则后台设了池子却永远只显示公告那一条）。`title_list` 里的 `{greeting}` 会按 `greetings` 规则（每条形如 `19-22\|晚上好！…`，终点不含、可跨零点）替换成当前时段文案，规则为空则用客户端内置表 |

> **公告按等级可见，等级是这么定的**（`target_level` 过滤用）：
> 1. 请求带 `X-Token` → 按会话查库里的**真实等级**（不可伪造，会员公告只发给会员）；
> 2. 否则用 `?level=` 或 `X-User-Level` 头（游客场景，客户端可传）；
> 3. 都没有按 `1` 处理。
> 注意签名只覆盖 `pathname`，所以 `?level=` **不会**破坏验签（用参考客户端时别把 query 拼进签名用的 path）。
| `latest_version_code` | int | 最新版本号（`app_latest_version_code`） |
| `storage_driver` | string | `local` 或 `oss` |
| `url_ttl_sec` | int | 成品下载链接有效期（默认 900 秒） |
| `task_timeout_sec` | int | 单任务超时（默认 120 秒） |
| `today` | string | 服务端本地日期 `YYYY-MM-DD` |
| `register_open` | bool | **注册通道开关**（配置项 `register_open`，默认 `true`）。`false` 时 `POST /api/v1/auth/register` 返回 `5001 注册通道已关闭，请联系客服开通账号`，客户端应隐藏/禁用注册入口并引导联系客服 |
| `watermark_overlay_text` | string | **未开通会员的水印文案**（配置项 `watermark_overlay_text`，默认 `开通会员去除水印`）。客户端在 `user.activated=false` 时把它作为**全屏叠加水印**的文字（服务端只下发文案，叠加由客户端渲染） |
| `activation_notice` | string | **未开通会员的提示文案**（配置项 `activation_notice`，默认 `未开通会员，可直接使用（成品带水印）`） |
| `invite_length` | int | 邀请码长度，**固定 `12`**（服务端常量 `INVITE_LENGTH`，不随配置变化）。客户端可用于注册页输入框长度校验与格式化（展示为 `XXXX-XXXX-XXXX`） |
| `share_link` | string | **分享好友链接**（配置项 `share_link`，默认 `https://tdapi.luoyun.xyz/`）。App 里点「微 → 分享好友」或「我 → 邀请好友」时复制到剪贴板的那条链接，纯客户端行为、不经过服务端 |
| `share_tip` | string | **分享成功提示语**（配置项 `share_tip`，默认 `链接已复制到剪贴板，粘贴给好友即可~`）。复制完成后弹给用户看的那句话，留空则用客户端内置文案 |

> **未开通会员时的客户端契约**：`user.activated=false`（来自 `register` / `login` / `status` / `heartbeat` 的 `user`）→ 叠加 `watermark_overlay_text` 全屏水印 + 展示 `activation_notice`。
> **但功能照常可用**：业务接口不会因此被拒（成品会带水印）。开通会员后 `activated=true`，遮罩消失、成品免水印。
> 若后台把 `watermark_free_for_member` 设为 `0`，则改为「所有人都带水印」，此时会员的价值只体现在配额与等级上。

> **类型约定**：公开配置项一律按 `sys_config.type` 还原类型下发（`bool` → `true/false`，`int` → 数字，`json` → 对象，其余为字符串），客户端可以直接用布尔/数值判断，无需再做 `Number()` 容错。`config_version`、`latest_version_code`、`storage_driver`、`url_ttl_sec`、`task_timeout_sec`、`today`、`register_open`、`watermark_overlay_text`、`activation_notice`、`invite_length` 由服务端权威计算/取默认值，同名 `sys_config` 项不会覆盖它们（`register_open` 是例外：它既在公开配置表里、又被顶层字段显式下发，两处取值一致）。
>
> 其中 `register_open`、`watermark_overlay_text`、`activation_notice` 是**本次产品模型变更新增**的公开项（`src/db/seed.js` 的 `CONFIGS`，分组 `user`），后台「全局配置」页可直接改；`invite_length` 不是配置项，是源码常量 `INVITE_LENGTH = 12`。

#### 6.4.2 `GET /api/v1/time` —— 服务端时间校准

| 项目 | 值 |
| --- | --- |
| 签名 | 无需（`mode:none`，响应 `sign:null`） |
| 版本检查 | 跳过（不受版本吊销影响） |
| 限流 | 无 |
| **注意** | 仍会执行时间窗与 nonce 校验：请求必须带 `X-Timestamp`（可带任意值）和长度 >= 8 的 `X-Nonce`，否则返回 `1001`；偏差超 300 秒返回 `1003` |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": { "server_ts": 1771056000, "server_time": "2026-02-14 21:20:00" },
  "ts": 1771056000,
  "sign": null
}
```

时钟校准建议：任何响应（含错误响应）的 `ts` 都是服务端秒级时间；本地偏差 `≈ ts - 本地请求时刻`。若偏差超过 300 秒，`/time` 会返回 `1003`，此时用该错误响应里的 `ts`（或 `data.drift`）来校正本地时间，而不是反复重试。

#### 6.4.3 `GET /api/v1/upgrade/check` —— 版本检查 / 灰度发布

| 项目 | 值 |
| --- | --- |
| 签名 | 无需（`mode:none`，响应 `sign:null`） |
| 版本检查 | 跳过（否则老版本永远升不了级） |
| 限流 | 单 IP 120 次 / 60 秒（bucket `upgrade`） |

查询参数（也可用同名请求头，签名不覆盖 query，建议优先用 `X-Version-Code`）：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `version_code` / `X-Version-Code` | int | 否 | 当前版本号（默认 0） |
| `channel` | string | 否 | 渠道，默认 `official`；服务端同时匹配该渠道与 `all` |
| `device_id` / `X-Device-Id` | string | 否 | 灰度分组用；同一设备永远命中同一档（按 `sha256(device_id + ":" + version_code) % 100 < gray_percent`） |
| `platform` | string | 否 | 默认 `android` |

响应示例（有更新）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "has_update": true,
    "current_version_code": 1,
    "channel": "official",
    "version_code": 3,
    "version_name": "1.2.0",
    "url": "https://dl.example.com/doutu-1.2.0.apk",
    "patch_url": "https://dl.example.com/patch-1.2.0.bspatch",
    "patch_md5": "3f2a9c1b7d4e5f608192a3b4c5d6e7f8",
    "size": 28841022,
    "changelog": "1. 修复小红书解析失败\n2. 优化下载速度",
    "force": false,
    "gray_percent": 100,
    "title": "新版本可用",
    "server_ts": 1771056000
  },
  "ts": 1771056000,
  "sign": null
}
```

响应示例（无更新）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "has_update": false,
    "current_version_code": 3,
    "channel": "official",
    "version_code": 3,
    "title": "新版本可用",
    "server_ts": 1771056000
  },
  "ts": 1771056000,
  "sign": null
}
```

规则：

- `has_update = (latest.version_code > version_code) && latest.url 非空`。
- `force = true` 表示强制更新：来源为 `app_version.force=1`，或 `app_version.min_version > version_code`，或（无发布记录时）后台 `app_force_update=1`。
- 若 `app_version` 表里没有已发布的候选版本，则回退到 `sys_config`：`app_latest_version_code` / `app_download_url` / `app_force_update` / `app_update_title`（此时 `version_name` 为空字符串、`size` 为 `null`、`gray_percent` 为 100）。
- 灰度：`gray_percent >= 100` 直接命中；`< 100` 时必须带 `device_id` 才可能命中。

#### 6.4.4 `POST /api/v1/upgrade/crash` —— 客户端崩溃上报

| 项目 | 值 |
| --- | --- |
| 签名 | 无需（`mode:none`，响应 `sign:null`） |
| 限流 | 单 IP 30 次 / 60 秒（bucket `crash`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `stack` | string | 否 | 崩溃堆栈，服务端只保留前 **2000** 字符 |
| `model` | string | 否 | 机型 |
| `os_version` | string | 否 | 系统版本 |
| `X-Version-Code`（头） | int | 否 | 客户端版本号，随日志记录 |
| `X-Device-Id`（头） | string | 否 | 设备号，随日志记录 |

请求示例：

```json
{
  "model": "23127PN0CC",
  "os_version": "14",
  "stack": "java.lang.NullPointerException\n\tat com.doutu.core.NativeBridge.sign(NativeBridge.java:88)\n..."
}
```

响应示例：

```json
{ "code": 0, "msg": "ok", "data": null, "ts": 1771056000, "sign": null }
```

备注：只写服务端日志（`crash` logger），不入库，不返回诊断信息。

---

### 6.5 支付（5 个）

前缀：`/api/v1/pay`，源文件 `src/modules/pay/routes.js` + `src/modules/pay/epay.js`。

> 所有金额字段均为**字符串两位小数**（如 `"19.90"`），不要用浮点解析。

#### 6.5.1 `GET /api/v1/pay/goods` —— 商品（套餐）列表

| 项目 | 值 |
| --- | --- |
| 签名 | 无需（`mode:none`，响应 `sign:null`） |
| 需激活 | 否 |
| 限流 | 无 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "goods": [
      {
        "id": 1,
        "name": "月卡",
        "duration": 30,
        "duration_text": "30 天",
        "level": 2,
        "max_device": 1,
        "quota_day": 30,
        "price": "19.90",
        "origin_price": "29.90",
        "tag": ""
      },
      {
        "id": 4,
        "name": "永久卡",
        "duration": 0,
        "duration_text": "永久",
        "level": 5,
        "max_device": 3,
        "quota_day": 200,
        "price": "299.00",
        "origin_price": "999.00",
        "tag": "限量"
      }
    ],
    "pay_channels": ["alipay", "wxpay"]
  },
  "ts": 1771056000,
  "sign": null
}
```

字段说明：`duration` 单位天，`0` 表示永久（`duration_text` 为 `"永久"`）；`price` / `origin_price` 为字符串金额；`pay_channels` 来自后台配置，只有列表内的 `pay_type` 可用于下单。

#### 6.5.2 `POST /api/v1/pay/create` —— 创建支付订单

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否（未激活也能下单，支付成功后自动开通） |
| 限流 | 单 IP 30 次 / 60 秒；单用户 15 次 / 60 秒（bucket `pay`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `goods_id` | int | 是 | 商品 ID（来自 6.5.1）；缺失返回 `1001 缺少参数 goods_id`；不存在/下架返回 `1001 商品不存在或已下架` |
| `pay_type` | string | 否 | 默认 `alipay`；必须在 `pay_channels` 内，否则 `1001 不支持的支付方式：xxx` |

请求示例：

```json
{ "goods_id": 1, "pay_type": "alipay" }
```

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "order_no": "OD20260214212500000456",
    "amount": "19.90",
    "goods_name": "月卡",
    "pay_type": "alipay",
    "pay_url": "https://pay.example.com/submit.php?pid=1000&type=alipay&out_trade_no=OD20260214212500000456&...&sign=9c1b...&sign_type=MD5",
    "expire_at": "2026-02-14 21:55:00",
    "expire_sec": 1800
  },
  "ts": 1771056000,
  "sign": "..."
}
```

字段说明：

- `order_no`：`OD` + 14 位时间 + 6 位随机（共 22 位）。
- `amount`：**字符串两位小数**，来自商品价格（服务端整数分运算）。
- `pay_url`：收银台地址，客户端用 WebView / 系统浏览器打开。`PAY_PROVIDER=epay` 时为易支付 `submit.php` 链接；`PAY_PROVIDER=mock` 时为 `/api/v1/pay/mock/pay?order_no=...`。
- `expire_at` / `expire_sec`：订单有效期（`pay_order_expire_min` 默认 30 分钟）；超时未支付会被后台任务置为「已关闭」。

常见错误码：`1001`（缺 goods_id / 商品不存在 / 低于最低支付金额 / 支付方式不支持）、`2005`、`3002`。

#### 6.5.3 `GET /api/v1/pay/status/{order_no}` —— 查询支付状态（前端轮询）

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "order_no": "OD20260214212500000456",
    "status": 1,
    "status_text": "已支付",
    "amount": "19.90",
    "paid_at": "2026-02-14 21:30:12",
    "expire_at": "2026-02-14 21:55:00",
    "user": {
      "user_id": 101,
      "username": "13800000001",
      "nickname": "小明",
      "level": 2,
      "status": 1,
      "status_text": "正常",
      "bind_at": "2026-02-14 21:04:05",
      "expire_at": "2026-04-15 21:04:05",
      "expire_days": 60,
      "permanent": false,
      "max_device": 1,
      "quota_day": 30,
      "total_task": 12
    }
  },
  "ts": 1771056000,
  "sign": "..."
}
```

规则：`user` 为订单关联用户的最新授权信息（`status=1` 后可见到期时间已延后），可用于支付成功后立即刷新权益；`status_text` 见 7.2。

常见错误码：`4004`（订单不存在或不属于当前用户）。

#### 6.5.4 `POST|GET /api/v1/pay/notify/{channel}` —— 支付回调（**不参与客户端签名**）

| 项目 | 值 |
| --- | --- |
| 签名 | **无**（渠道不会带我们的签名头，路由不挂 `clientAuth`） |
| 调用方 | 支付网关 / 本地联调 |
| 渠道 | `epay`（易支付）与 `mock`（仅本地联调） |

**安全规则（服务端强制，客户端/运维必须知晓）**：

1. 回调**必须验渠道签名**：易支付 MD5 规则 = 取所有非空参数（去掉 `sign`、`sign_type`）→ 按参数名 ASCII 升序 → 拼 `k=v&k=v` → 末尾**直接追加**商户密钥 `EPAY_KEY`（不加 `&`）→ md5 后小写。验签失败：写 `payment_record(status=2)`、告警、返回纯文本 `fail`。
2. `trade_status` 存在且不是 `TRADE_SUCCESS` 时直接返回 `success`（避免网关重推），不入账。
3. 验签通过后服务端会**主动反查网关**（易支付 `api.php?act=order`），只信任反查结果；反查失败会告警但按回调金额入账并标记待核对。
4. 入账前**比对金额**：回调金额与订单金额不一致 → 写 `payment_record(status=3)` 并拒绝入账。
5. 同一 `trade_no` 只能入账一次（`uk_trade_no` 唯一索引 + 幂等判断），重复回调写 `payment_record(status=4)` 并返回 `success`。
6. 已退款订单收到回调会被拒绝。
7. 回调失败立即告警（涉及真金白银）。

回调参数（易支付，GET query 或 POST form/json 均可）：

| 名称 | 说明 |
| --- | --- |
| `pid` | 商户 ID |
| `trade_no` | 渠道交易号 |
| `out_trade_no` | 我们的订单号（`order_no`） |
| `type` | 支付方式（`alipay` / `wxpay`） |
| `name` | 商品名 |
| `money` | 金额（字符串元） |
| `trade_status` | 交易状态，成功为 `TRADE_SUCCESS` |
| `sign` | 渠道签名 |
| `sign_type` | 签名类型（不参与签名计算） |

回调响应：**纯文本** `success` / `fail`（HTTP 200，不是 JSON 信封）：

```
success      # 已受理（包括重复回调、非成功状态）
fail         # 验签失败 / 入账失败，网关会重推
```

mock 渠道（`PAY_PROVIDER=mock`）：`POST /api/v1/pay/notify/mock`，body `{"order_no":"OD..."}`（也接受 `out_trade_no`）。仅允许本机 IP 调用；生产环境非本机请求返回 `4004 渠道不存在`。响应是 JSON：

```json
{ "code": 0, "msg": "ok", "data": { "order_no": "OD20260214212500000456", "already": false }, "ts": 1771056000, "sign": null }
```

`already:true` 表示重复回调已幂等忽略。

#### 6.5.5 `GET /api/v1/pay/mock/pay` —— mock 收银台（本地联调）

| 项目 | 值 |
| --- | --- |
| 签名 | 无 |
| 可用条件 | `PAY_PROVIDER=mock`，否则 `4004 页面不存在` |
| 参数 | `order_no`（查询参数，必填，否则 `4004 订单不存在`） |
| 响应 | `text/html; charset=utf-8` 的模拟收银台页面，点「模拟支付成功」会调 `/api/v1/pay/notify/mock` 完成入账 |

`POST /api/v1/pay/create` 在 mock 渠道下会把 `pay_url` 指向本接口，客户端用 WebView 打开即可完成自测闭环。

---

### 6.6 工单（4 个）

前缀：`/api/v1/ticket`，源文件 `src/modules/ticket/routes.js`。

#### 6.6.1 `POST /api/v1/ticket/create` —— 创建工单

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 单 IP 20 次 / 3600 秒；单用户 10 次 / 3600 秒（bucket `ticket`） |
| 业务限制 | 待处理工单（`status=0`）达 3 个时返回 `3002 您有 3 个待处理工单，请先等待客服回复` |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 1~100 字；超长/为空返回 `1001 请填写 1~100 字的标题` |
| `content` | string | 是 | 问题描述 |
| `category` | int | 否 | 默认 4：1 激活问题 / 2 解析失败 / 3 支付问题 / 4 其他 |
| `priority` | int | 否 | 默认 2（数字越小越靠前，后台按 `priority ASC` 排序） |
| `attachments` | array | 否 | 附件地址数组（JSON 存库） |
| `os_version` / `model` | string | 否 | 会写入工单 `context` |

服务端自动收集的上下文（写入 `ticket.context`，客服可见）：`device_id`、`app_version`（取 `X-App-Version`）、`os_version`、`model`、`user_level`、`expire_at`、`recent_tasks`（最近 5 条任务：`task_no` / `platform` / `status` / `err_msg` / `created_at`）。

请求示例：

```json
{
  "title": "抖音链接解析失败",
  "content": "https://v.douyin.com/iAbCdEf/ 提交后一直排队，最后超时",
  "category": 2,
  "priority": 2,
  "attachments": ["https://cdn.example.com/shot1.png"],
  "os_version": "14",
  "model": "23127PN0CC"
}
```

响应示例：

```json
{
  "code": 0,
  "msg": "已提交，客服会尽快处理",
  "data": {
    "ticket": {
      "id": 12,
      "ticket_no": "TK20260214213000000789",
      "title": "抖音链接解析失败",
      "category": 2,
      "category_text": "解析失败",
      "priority": 2,
      "status": 0,
      "status_text": "待处理",
      "message_count": 1,
      "last_reply_at": "2026-02-14 21:30:00",
      "sla_at": "2026-02-15 21:30:00",
      "created_at": "2026-02-14 21:30:00",
      "updated_at": "2026-02-14 21:30:00"
    }
  },
  "ts": 1771056600,
  "sign": "..."
}
```

常见错误码：`1001`、`3002`、`2005`。

#### 6.6.2 `GET /api/v1/ticket/list` —— 我的工单列表

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

查询参数：`page`（默认 1）、`limit`（默认 10，上限 50）、`page_size`（别名）、`status`（工单状态）。

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "total": 1,
    "rows": [
      {
        "id": 12,
        "ticket_no": "TK20260214213000000789",
        "title": "抖音链接解析失败",
        "category": 2,
        "category_text": "解析失败",
        "priority": 2,
        "status": 2,
        "status_text": "待用户回复",
        "message_count": 2,
        "last_reply_at": "2026-02-14 21:40:00",
        "sla_at": "2026-02-15 21:30:00",
        "created_at": "2026-02-14 21:30:00",
        "updated_at": "2026-02-14 21:40:00"
      }
    ],
    "page": 1,
    "limit": 10
  },
  "ts": 1771056600,
  "sign": "..."
}
```

#### 6.6.3 `GET /api/v1/ticket/detail/{ticket_no}` —— 工单详情 + 消息

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 无 |

响应示例：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "ticket": {
      "id": 12,
      "ticket_no": "TK20260214213000000789",
      "title": "抖音链接解析失败",
      "category": 2,
      "category_text": "解析失败",
      "priority": 2,
      "status": 1,
      "status_text": "处理中",
      "message_count": 2,
      "last_reply_at": "2026-02-14 21:40:00",
      "sla_at": "2026-02-15 21:30:00",
      "created_at": "2026-02-14 21:30:00",
      "updated_at": "2026-02-14 21:40:00"
    },
    "messages": [
      {
        "id": 31,
        "sender_type": 1,
        "content": "https://v.douyin.com/iAbCdEf/ 提交后一直排队，最后超时",
        "attachments": ["https://cdn.example.com/shot1.png"],
        "created_at": "2026-02-14 21:30:00"
      },
      {
        "id": 32,
        "sender_type": 2,
        "content": "已收到，正在处理",
        "attachments": [],
        "created_at": "2026-02-14 21:40:00"
      }
    ]
  },
  "ts": 1771056600,
  "sign": "..."
}
```

`sender_type`：`1` = 用户，`2` = 客服。

常见错误码：`4004`（工单不存在或不属于当前用户）。

#### 6.6.4 `POST /api/v1/ticket/reply` —— 追加回复

| 项目 | 值 |
| --- | --- |
| 签名 | 会话 `sign_key` |
| 需激活 | 否 |
| 限流 | 单 IP 60 次 / 3600 秒；单用户 30 次 / 3600 秒（bucket `ticket-reply`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticket_no` | string | 是 | 工单号 |
| `content` | string | 是 | 回复内容 |
| `attachments` | array | 否 | 附件地址数组 |

响应示例：

```json
{ "code": 0, "msg": "已回复", "data": null, "ts": 1771056600, "sign": "..." }
```

规则：回复后工单状态被置为 `1 处理中`，`message_count` +1；已关闭（`status=3`）的工单回复返回 `1001 工单已关闭，请新建工单`。

常见错误码：`1001`、`4004`、`3002`。

---

### 6.7 意见反馈（3 个）

前缀：`/api/v1/feedback`，源文件 `src/modules/feedback/routes.js`。

和 6.6 的「客服工单」不是一回事：工单是**一来一回的会话**，反馈是**一次性提交**（App「我 → 求新功能」），
用户提交后只看到一句感谢，不需要等回复，所以没有会话表，只有一张 `feedback`。

**鉴权用 root（MASTER_KEY）而不是 session**：未登录的用户同样能提意见；带了 `X-Token` 就顺手把
`user_id` 记上（后台能看到是谁提的），没带也不影响提交。

#### 6.7.1 `POST /api/v1/feedback/create` —— 提交意见 / 求新功能

| 项目 | 值 |
| --- | --- |
| 签名 | **root**（MASTER_KEY） |
| 限流 | 单 IP 12 次 / 3600 秒；单设备 12 次 / 3600 秒（bucket `feedback`） |
| 图片 | base64 塞在 JSON 里（和头像同一条路，不引 multipart）；单张 ≤ 1MB，最多 3 张 |

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | 是 | 意见正文，2~1000 字 |
| `images` | string[] | 否 | 每项是 `data:image/jpeg;base64,...`；服务端只认 JPG / PNG / WebP / GIF 的文件头 |
| `contact` | string | 否 | 用户留下的联系方式，≤ 64 字 |
| `model` | string | 否 | 机型（客户端传 `Build.MANUFACTURER + Build.MODEL`），便于复现问题 |
| `os_version` | string | 否 | 系统版本（客户端传 `Android <release>`） |

请求示例：

```json
{
  "content": "希望增加批量去水印功能",
  "images": ["data:image/jpeg;base64,/9j/4AAQSkZJRg..."],
  "contact": "微信 doutu-001",
  "model": "vivo V2220A",
  "os_version": "Android 14"
}
```

成功响应：

```json
{
  "code": 0,
  "msg": "感谢您的反馈，我们会认真阅读每一条建议",
  "data": { "id": 1, "images": 1 }
}
```

服务端还会自动记录 `device_id`（`X-Device-Id`）、`app_version` / `version_code`（`X-App-Version` / `X-Version-Code`）。

#### 6.7.2 `GET /api/v1/feedback/image/{name}` —— 读取反馈图片

| 项目 | 值 |
| --- | --- |
| 签名 | **无**（和 `/avatar/:id` 同理：浏览器 `<img src>` 不会带签名头） |
| 安全 | 文件名里带 6 位随机 hex，不可枚举；只允许读存储里的 `feedback/` 目录 |

`{name}` 传 `feedback.images` 里那条 key 的文件名部分（形如 `mvabsw4y-8bcd35c5a832.jpg`）。
不存在或格式不合法返回 `4004 图片不存在`（HTTP 404）。

#### 6.7.3 后台查看与处理

后台菜单「意见反馈」对应 `GET /admin/api/feedback`（权限 `ticket.read`），
标记处理走 `POST /admin/api/feedback/:id`（权限 `ticket.write`）。清单见 6.9。

---

### 6.8 健康检查（1 个）

#### 6.8.1 `GET /healthz`

| 项目 | 值 |
| --- | --- |
| 签名 | 无（不校验任何请求头） |
| 用途 | Nginx upstream 检查、宝塔监控、UptimeRobot |

响应示例（正常）：

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "service": "doutu-server",
    "env": "production",
    "version": "1.0.0",
    "db": "up",
    "storage": "local",
    "ts": 1771056000
  },
  "ts": 1771056000,
  "sign": null
}
```

数据库不可用时 `code` 为 `5000`、`msg` 为 `数据库不可用`、`data.db` 为 `down`（HTTP 仍为 200）。

---

### 6.9 后台接口清单（84 个）

前缀：`${ADMIN_PATH}/api`，默认 `/admin/api`。源文件：`src/modules/admin/routes.js`、`src/modules/admin/business.js`、`src/modules/admin/auth.js`。

> **模型变更提示**：卡密相关的后台接口（`/admin/api/cards*`，权限点 `card.*`）**已全部下线并从路由中移除**；取而代之的是邀请码接口（`/admin/api/invites*`，权限点 `invite.*`）与「管理账号激活」`POST /admin/api/users/:id/activate`。`card_batch` / `card_key` 两张历史表仍留在库里，只为保留历史数据与用户详情的历史记录，**没有任何接口可以再生成或核销卡密**（见 7.5）。

后台鉴权要点：

- 登录 `POST /admin/api/login`，成功返回 `{token, expire_at, admin{...}}` 并设置 `admin_token` Cookie（HttpOnly / SameSite=Lax / Max-Age=86400）。token 也可通过 `X-Admin-Token` 头或 `Authorization: Bearer <token>` 传递。
- token 有效期 `ADMIN_TOKEN_TTL_MIN`（默认 120 分钟），**每次请求滑动续期**；空闲超 `ADMIN_IDLE_TIMEOUT_MIN`（默认 30 分钟）自动登出。
- 登录失败 15 分钟内 5 次 → `3002 登录失败次数过多，请 15 分钟后再试`。
- 配置了 TOTP 的管理员登录必须带 `totp`，否则 `6003`。
- 权限模型：角色 `perms`（支持 `*` 全权与 `user.*` 前缀通配）+ `menus` + `data_scope`（2 = 只看自己名下数据）。内置角色：`super`（`*`）、`operator`（含 `user.*`、**`invite.*`**、`order.*`、`revenue.*`、`ticket.*`、`version.*`、`notice.*`、`config.read`）、`support`、`finance`。
- 邀请码权限点：`invite.read`（看统计/批次/明细）、`invite.write`（生成批次）、`invite.void`（作废，需二次密码）、`invite.export`（导出）。**卡密的 `card.read` / `card.write` / `card.void` / `card.export` 已随卡密接口一并下线**，历史角色里若还留着这些权限点也不会再匹配到任何接口。
- 「二次密码」列标 ✅ 的接口必须带 `password`（或 `confirm_password`）字段重新输入当前管理员登录密码，缺失返回 `1001 该操作需要二次输入登录密码`，错误返回 `6002 密码错误，操作已取消`。
- 所有写操作都写 `audit_log`；导出类接口额外写入 `module=xx, action=export`。

**面板 / 个人（8 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `POST /admin/api/login` | 公开 |  | 后台登录（返回 token / 管理员信息 / perms），可带 `totp` |
| `POST /admin/api/logout` | 仅登录 |  | 退出并撤销当前 token |
| `GET /admin/api/profile` | 仅登录 |  | 当前管理员资料（含 `perms` / `menus` / `data_scope` / `totp_enabled`） |
| `POST /admin/api/password` | 仅登录 |  | 修改自己的登录密码（改后吊销该账号其他会话） |
| `POST /admin/api/totp/setup` | 仅登录 |  | 生成 TOTP 密钥与 `otpauth` 链接（扫码用） |
| `POST /admin/api/totp/enable` | 仅登录 |  | 提交 `secret` + `code` 完成二次验证绑定 |
| `POST /admin/api/totp/disable` | 仅登录 |  | 凭密码关闭二次验证 |
| `GET /admin/api/panel/env` | 仅登录 |  | 面板环境自检：Node 版本、CPU、内存、负载、uptime、FFmpeg/字体、队列积压 |

**面板工具（1 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `POST /admin/api/panel/purge-version` | `config.write` |  | 一键吊销某客户端版本：撤销该版本全部会话并写入 `app_version_blocklist`（之后该版本请求全部 `9001`） |

**管理员与角色（8 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/admins` | `admin.read` |  | 管理员列表（含角色名，`totp_secret` 脱敏为 `***`） |
| `POST /admin/api/admins` | `admin.write` |  | 新建管理员（用户名 3~32 位，密码 >= 8 位） |
| `PUT /admin/api/admins/:id` | `admin.write` |  | 修改管理员（`real_name` / `phone` / `role_id` / `status`；禁用会吊销其 token） |
| `POST /admin/api/admins/:id/reset-password` | `admin.write` |  | 重置管理员密码并吊销其所有 token |
| `DELETE /admin/api/admins/:id` | `admin.write` | ✅ | 删除管理员（不能删自己，至少保留一个超级管理员） |
| `GET /admin/api/roles` | `admin.read` |  | 角色列表（含 `menus` / `perms` / `data_scope`） |
| `POST /admin/api/roles` | `admin.write` |  | 新建角色（`code` 唯一） |
| `PUT /admin/api/roles/:id` | `admin.write` |  | 修改角色（菜单/权限/数据范围） |

**全局配置与公告（6 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/configs` | `config.read` |  | 配置列表（可按 `group` 过滤，返回 `config_version`） |
| `POST /admin/api/configs` | `config.write` |  | 批量保存配置（`entries[]` 或 `configs{}`），保存后 `config_version` +1 |
| `GET /admin/api/notices` | `config.read` |  | 公告列表 |
| `GET /api/v1/version/check` | root | `platform` / `version_code`（查询串，**不进签名**） | 版本检查（客户端启动时调）。返回 `has_update` / `force` / `latest{version_code,version_name,url,size,changelog,force,min_version,published_at}`。**platform 只认 android / ios**；只返回 `status=1` 且 `version_code` 大于客户端当前值的最新一条；没有新版时 `latest` 为 `null` |
| `POST /admin/api/notices` | `config.write` |  | 新建公告（`title` / `content` / `type` / `target_level` / `start_at` / `end_at` / `status` / `sort`）。`type` 只接受 **1 弹窗 / 2 滚动 / 3 首页横幅**（非法值按 1 处理）；`target_level` 是逗号分隔等级或 `*`，写入前会去空格去重 |
| `GET /admin/api/versions` | `version.read` |  | 客户端版本列表（按 `version_code` 倒序） |
| `POST /admin/api/versions` | `version.write` | `version_code` / `platform` / `version_name` / `url` / `changelog` / `force` / `status`（+ 可选 `channel` / `size` / `gray_percent` / `min_version` / `patch_url` / `patch_md5`） | 发布版本。**`platform` 只接受 `android` / `ios`**（其它值 1001 拒绝）；`version_code` 必须是正整数且同平台不重复；`url` 必须以 `http://`/`https://` 开头（挡掉 `javascript:` 之类的地址）；发布成功后同步 `app_latest_version_code` 配置并写审计日志 |
| `PUT /admin/api/versions/:id` | `version.write` | 同上（除 `version_code` / `platform` 外均可改） | 修改版本 |
| `DELETE /admin/api/versions/:id` | `version.write` |  | 删除版本 |
| `GET /admin/api/terms` | `config.read` |  | 读取《用户须知与免责声明》：`text` / `version` / `updated_at` / `updated_by` / `can_edit`（当前账号是否超管）+ `logs`（最近 10 次修改记录）。`can_edit` 由 `role_id === 1` 判定 |
| `POST /admin/api/terms` | `config.read` **+ 超管** | `text` | 修改免责声明正文。**仅超级管理员（role_id=1）可调用**，其他人 403 且会记一条 `denied` 审计；内容有变化时自动 `terms_version + 1`（客户端据此要求用户重新确认），内容相同则不涨版本。每次修改写入 `audit_log`（module=`terms`）：操作人、IP、时间、新旧版本、字数、**sha256 指纹**、开头 60 字 |
| `PUT /admin/api/notices/:id` | `config.write` |  | 修改公告 |
| `DELETE /admin/api/notices/:id` | `config.write` |  | 删除公告 |

**版本推送（4 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/versions` | `version.read` |  | 版本列表（按 `version_code` 倒序） |
| `POST /admin/api/versions` | `version.write` |  | 发布版本（`platform` / `version_code` / `version_name` / `channel` / `url` / `patch_url` / `patch_md5` / `size` / `changelog` / `force` / `gray_percent` / `min_version` / `status`），同时更新 `app_latest_version_code` |
| `PUT /admin/api/versions/:id` | `version.write` |  | 修改版本（含灰度比例、`min_version`、上下架） |
| `DELETE /admin/api/versions/:id` | `version.write` |  | 删除版本记录 |

**审计（1 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/audit` | `audit.read` |  | 操作审计日志（可按 `module` / `admin_id` / `target_id` 过滤，分页上限 100） |

**账号管理（13 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/users` | `user.read` |  | 用户列表（`keyword` / `status` / `level` / `channel` / `start_date` / `end_date` / `expire_start` / `expire_end` / `min_amount` / `max_amount` / `sort` / `order`；返回 `status_effective`、`status_text`、`days_left`、`phone_masked`、`total_amount`） |
| `GET /admin/api/users/export` | `user.read` 或 `user.export` |  | 导出用户 CSV（上限 5 万行，带 BOM，Excel 打开不乱码） |
| `GET /admin/api/users/:id` | `user.read` |  | 用户详情：`user`（含 `activation`）+ **`invites`** + `cards`（历史数据，见 7.5）+ `devices` + `tasks` + `orders` + `profile_logs` + `sessions` + `tickets` |
| `POST /admin/api/users` | `user.write` |  | 后台添加用户（直接开通，可 `create_pay_order` 代客下单）。**不再有 `gen_card`**（卡密体系已下线） |
| `PUT /admin/api/users/:id` | `user.write` |  | 修改基础信息（`remark` / `phone` / `nickname` / `email` / `username` / `level` / `max_device` / `quota_day`）。其中 `username` **同样走注册那套格式与唯一性校验**（`userService.validateUsername` + `LOWER(username)` 忽略大小写查重）：2~20 位中文/字母/数字/下划线/短横线、不能是手机号形态、不能与别人的用户名冲突（查重时排除自己）；非法或重名返回 `1001`（`该用户名已被使用，请换一个` 等）。传了与当前值**完全相同**的字符串视为未修改（不报错、不写日志）；只改大小写会被当作一次真实修改（`Admin` → `admin`） |
| `POST /admin/api/users/:id/activate` | `user.write` |  | **管理账号激活（新模型下唯一的开通方式）**，详见下方 |
| `POST /admin/api/users/:id/duration` | `user.write` |  | 管理时长（`days` 或 `expire_at`，可同时改 `level` / `max_device` / `quota_day`；写 `user_profile_log`） |
| `POST /admin/api/users/:id/status` | `user.write` | ✅（仅 `status=3` 封禁时） | 改状态：0 未开通会员 / 1 正常 / 2 已过期 / 3 封禁；封禁会撤销该用户全部会话 |
| `POST /admin/api/users/:id/reset-password` | `user.write` |  | 重置用户密码（>= 6 位）并强制下线 |
| `POST /admin/api/users/:id/unbind` | `user.write` 或 `user.device` |  | 解绑指定设备（`device_id` 必填，服务端会转小写） |
| `POST /admin/api/users/:id/revoke` | `user.write` |  | 强制下线（撤销该用户全部会话） |
| `DELETE /admin/api/users/:id` | `user.delete` | ✅ | 删除用户（同时撤销会话、解绑设备；不可恢复） |
| `POST /admin/api/users/batch` | `user.write` |  | 批量操作：`extend`（延期，需 `days`）/ `tag`（打标签 `remark`）/ `status`（批量改状态，`3` 会批量下线）；单次最多 500 个 |

**`POST /admin/api/users/:id/activate` —— 管理账号激活（新模型核心）**

| 项目 | 值 |
| --- | --- |
| 权限 | `user.write` |
| 二次密码 | 不需要 |
| 限流 | 后台登录态，无限流 |
| 作用 | 把 `/auth/register` 注册出来、`activated=false` 的账号直接开通为会员：`status` 置 `1`、写上 `bind_at` / `expire_at`，并写 `user_profile_log`（`field='activate'`）与 `audit_log`（`module=user, action=activate`） |

请求字段：

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `days` | int | 否（默认 30） | 开通天数。**不能为 0**：`0`/非数字返回 `1001 开通时长必须是非 0 数字（天）`。到期时间按「`MAX(现在, 原到期时间) + days`」累加（续期语义，不是覆盖） |
| `level` | int | 否 | 传了才改等级 |
| `quota_day` | int | 否 | 传了才改每日配额（`0` = 不限量） |
| `max_device` | int | 否 | 传了才改设备上限 |
| `remark` | string | 否 | 记入 `user_profile_log.new_value` 的备注 |

请求示例：

```json
{ "days": 30, "level": 2, "quota_day": 30, "max_device": 1, "remark": "客服手工开通" }
```

响应示例：

```json
{
  "code": 0,
  "msg": "已激活，到期时间 2026-03-16 21:04:05",
  "data": {
    "user": {
      "id": 102,
      "username": "13800000002",
      "phone": "13800000002",
      "status": 1,
      "bind_at": "2026-02-14 21:04:05",
      "expire_at": "2026-03-16 21:04:05",
      "level": 2,
      "max_device": 1,
      "quota_day": 30,
      "total_amount": "0.00",
      "activation": {
        "user_id": 102,
        "username": "13800000002",
        "nickname": "13800000002",
        "level": 2,
        "status": 1,
        "status_text": "正常",
        "activated": true,
        "bind_at": "2026-02-14 21:04:05",
        "expire_at": "2026-03-16 21:04:05",
        "expire_days": 30,
        "permanent": false,
        "max_device": 1,
        "quota_day": 30,
        "total_task": 0
      }
    }
  },
  "ts": 1771056000,
  "sign": "..."
}
```

- **开通后客户端水印消失**：客户端下一次 `GET /api/v1/auth/status`（或 `heartbeat`）会拿到 `activated=true`，据此隐藏全屏水印遮罩、关闭未激活提示。
- 错误码：`4004`（用户不存在）、`2004`（账号已被封禁，需先解封）、`1001`（`days` 为 0 或非法）。
- 该接口与 `POST /admin/api/orders/:order_no/reissue`（补开会员）、后台「添加用户」都会调用同一个 `activateUser` 实现，语义一致。

**邀请码（7 个）**

> 邀请码取代了原卡密体系（权限点由 `card.*` 改为 `invite.*`）：**一个邀请码只能创建一个账号**。
> 邀请码长度 12 位（展示为 `XXXX-XXXX-XXXX`），状态见 7.5。

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/invites/summary` | `invite.read` |  | 邀请码统计卡片：`{total, unused, used}` |
| `GET /admin/api/invites/batches` | `invite.read` |  | 批次列表（`batch_no` / `created_at` / `expire_at` / `duration` / `level` + `stats{unused,used,voided,expired,total}`） |
| `GET /admin/api/invites` | `invite.read` |  | 邀请码明细，参数 `status`（0 未使用/1 已使用/2 已作废/3 已过期）、`batch_no`、`keyword`（匹配 `code` / `used_phone` / `batch_no`）、`page` / `limit`（默认 50）；联查使用者的 `username` / `user_status` / `user_expire_at`，每行附 `display`（带横线的展示格式） |
| `POST /admin/api/invites` | `invite.write` |  | 生成邀请码批次，详见下方 |
| `GET /admin/api/invites/export` | `invite.read` 或 `invite.export` |  | 导出邀请码：`batch_no`（不传=全部）、`all=1`（含已使用，默认只导未使用）、`format=txt`（默认，含使用说明与版权提示）或 `csv`；写审计 |
| `POST /admin/api/invites/void` | `invite.void` | ✅ | 作废单张邀请码（`code` 必填；**已被使用的不能作废**，返回 `1001 邀请码已被使用，不能作废`；不存在返回 `4004`） |
| `POST /admin/api/invites/batches/:batch_no/void` | `invite.void` | ✅ | 作废该批次内**全部未使用**的邀请码（返回 `count` = 影响行数） |
| `POST /admin/api/invites/:code/reset` | `invite.write` | ✅ | **把已使用/已作废/已过期的邀请码恢复为「未使用」**，清空 `used_by`/`used_phone`/`used_at`。用于「账号删了，让对方用同一个码重新注册」。返回 `{code, changed, previous_status, previous_used_by, previous_used_phone}`；`changed=false` 表示本来就是未使用（幂等） |

> **删号会自动释放邀请码**：`DELETE /admin/api/users/:id` 会同时把该账号用过的邀请码重置为未使用，
> 响应 `data.released_invite_codes` 给出被释放的码，并写审计日志。
> 这是线上真实反馈的坑：邀请码是「一码一账号」，删号后码若仍挂在「已使用」，
> 管理员拿它重注册会撞「该邀请码已被使用」，而邀请码列表里又看不到使用者（账号已删）。
> 不想让对方重新注册时，删除后到「邀请码管理」把那个码作废即可。

**`POST /admin/api/invites` 请求字段**

| 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `count` | int | 是 | 生成数量，服务端夹紧到 `1 ~ 100000` |
| `duration` | int | 否（默认 **0**） | 注册后预设时长（天）。**`duration=0` 表示注册后仍需后台激活**（账号 `activated=false`）；`>0` 表示注册即开通该天数 |
| `level` | int | 否（默认 1） | 注册后预设等级 |
| `quota_day` | int | 否（默认 20） | 注册后预设每日配额（`0` = 不限量） |
| `max_device` | int | 否（默认 1） | 注册后预设设备上限 |
| `expire_at` | string | 否 | **邀请码自身**的有效期（`YYYY-MM-DD HH:mm:ss`，也接受 `T` 分隔）；过期未用的码注册时返回 `1001 该邀请码已过期` 并被标记 `status=3` |
| `remark` | string | 否 | 备注 |

请求示例：

```json
{ "count": 10, "duration": 0, "level": 2, "quota_day": 30, "max_device": 1, "expire_at": "2026-12-31 23:59:59", "remark": "双十一活动" }
```

响应示例：

```json
{
  "code": 0,
  "msg": "已生成 10 个邀请码",
  "data": {
    "batch_no": "IV20260214-001",
    "codes": ["ABCD-EFGH-JKMN", "PQRS-TUVW-XYZ2"],
    "raw_codes": ["ABCDEFGHJKMN", "PQRSTUVWXYZ2"],
    "note": "一个邀请码只能创建一个账号"
  },
  "ts": 1771056000,
  "sign": "..."
}
```

- `codes` 是给运营复制/导出的展示格式（`XXXX-XXXX-XXXX`），`raw_codes` 是入库的 12 位原始串；`data.codes` 在示例里只列了前两个，实际返回 `count` 个。
- 批次号格式 `IVyyyymmdd-序号`（号段按当天已有批次数递增）。

**订单与收益（15 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/goods` | `order.read` 或 `user.write` |  | 商品（套餐）列表，供后台代客下单 / 添加用户时挑选 `goods_id` |
| `POST /admin/api/goods` | `config.write` |  | 新建商品（`name` / `duration` / `level` / `max_device` / `quota_day` / `price` / `origin_price` / `tag` / `sort` / `status`） |
| `PUT /admin/api/goods/:id` | `config.write` |  | 修改商品 |
| `DELETE /admin/api/goods/:id` | `config.write` |  | 删除商品；**已被订单引用时只下架不删除**（返回 `offline: true`），避免历史订单丢商品信息 |
| `GET /admin/api/orders` | `order.read` |  | 订单列表（`keyword` / `status` / `pay_type` / `channel` / `type` / 日期 / 金额区间，含 `status_text` 与各项金额） |
| `GET /admin/api/orders/summary` | `order.read` |  | 订单汇总卡片：`pending_count` / `paid_count` / `paid_amount` / `today_count` / `today_amount` |
| `GET /admin/api/orders/export` | `order.read` 或 `order.export` |  | 导出订单 CSV |
| `POST /admin/api/orders/:order_no/refund` | `order.refund` | ✅ | 标记退款并把该用户置为「已过期」（立即终止授权） |
| `POST /admin/api/orders/:order_no/reissue` | `order.write` |  | **补开会员**（参数 `days`，缺省用订单 `duration`，再缺省 30；`days` 为 0/非数字时回退 30）：直接给该订单用户续期（`MAX(现在, 原到期时间)+days`）并写 `user_profile_log` + 审计。订单没有关联用户时返回 `1001 该订单没有关联用户，无法补开`；**旧版这里是「补发等价卡密」，卡密体系下线后不再有任何发卡行为** |
| `GET /admin/api/revenue/dashboard` | `revenue.read` |  | 后台首页概览（收益 / 用户 / 任务等卡片） |
| `GET /admin/api/revenue/overview` | `revenue.read` |  | 五档收益统计：`granularity` = `year` / `quarter` / `month` / `week` / `hour`（非法值报 `1001`） |
| `GET /admin/api/revenue/daily` | `revenue.read` |  | 日收益序列（`from` / `to`，默认近 30 天，缺失日补零） |
| `POST /admin/api/revenue/aggregate` | `revenue.write` |  | 补算收益日聚合（`days` 上限 90） |
| `POST /admin/api/revenue/reconcile` | `revenue.write` |  | 手工触发支付对账（`date` 默认今天），差异写 `reconcile_log` 并告警 |
| `GET /admin/api/revenue/reconcile-logs` | `revenue.read` |  | 对账日志列表 |

**任务、线路与溯源（6 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/tasks` | `task.read` |  | 任务列表（`keyword`（任务号/item_id/标题）/ `status` / `platform` / 日期，联查用户名与备注） |
| `GET /admin/api/tasks/queue` | `task.read` |  | 队列积压：`queued` / `running` / `kv_high` / `kv_normal` / `backlog` |
| `GET /admin/api/tasks/trace` | `task.trace` |  | 侵权投诉溯源（`user_id` / `item_id` / `task_no` / `ip` 至少给一个），返回 `evidence_url`（**1 小时**有效期的取证下载地址）等，并写审计 |
| `GET /admin/api/routes` | `task.read` |  | 解析线路健康度列表（成功率 / 平均耗时等） |
| `POST /admin/api/routes` | `route.write` |  | 新建线路（`platform` / `name` / `type` / `endpoint` / `config` / `priority` / `enabled`） |
| `PUT /admin/api/routes/:id` | `route.write` |  | 修改线路（启用/停用、优先级、配置） |

**客服工单与快捷回复（10 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/tickets` | `ticket.read` |  | 工单列表（`status` / `keyword`；`data_scope=2` 且 `mine=1` 时只看自己名下） |
| `GET /admin/api/tickets/:ticket_no` | `ticket.read` |  | 工单详情（含 `context` 环境快照、消息、用户、最近任务） |
| `POST /admin/api/tickets/:ticket_no/reply` | `ticket.write` |  | 客服回复（工单置为 `2 待用户回复`，记录 `admin_id`） |
| `POST /admin/api/tickets/:ticket_no/close` | `ticket.write` |  | 关闭工单（`status=3`） |
| `POST /admin/api/tickets/:ticket_no/assign` | `ticket.write` |  | 指派工单给指定管理员（`admin_id`，状态置 `1 处理中`） |
| `PUT /admin/api/tickets/:ticket_no` | `ticket.write` |  | 改标题 / 分类 / 优先级 / 处理人 / SLA，或重新打开已关闭工单（`status` 非 3 时清空 `closed_at`） |
| `GET /admin/api/quick-replies` | `ticket.read` |  | 快捷回复模板列表 |
| `POST /admin/api/quick-replies` | `ticket.write` |  | 新建快捷回复 |
| `PUT /admin/api/quick-replies/:id` | `ticket.write` |  | 修改快捷回复 |
| `DELETE /admin/api/quick-replies/:id` | `ticket.write` |  | 删除快捷回复 |

**意见反馈（3 个）**

后台菜单「意见反馈」（`menus` 键 `feedback`，权限点沿用 `ticket.*`，不需要新增权限前缀）。

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/feedback` | `ticket.read` |  | 反馈列表（`status` / `keyword` 过滤，按「待处理优先 + id 倒序」）。返回 `total` / `pending` / `rows`，每行带 `image_urls`（可直接放进 `<img src>`）与提交用户 `username` / `phone` |
| `GET /admin/api/feedback/:id` | `ticket.read` |  | 反馈详情：`feedback`（含 `images` / `image_urls`）与 `user`（用户不存在时为 `null`） |
| `POST /admin/api/feedback/:id` | `ticket.write` |  | 标记处理状态（`status` 1=已处理 / 0=待处理）+ 处理备注 `remark`，记录 `admin_id` 与 `handled_at`，并写审计日志 |

**风控与登录日志（3 个）**

| 方法 路径 | 权限 | 二次密码 | 用途 |
| --- | --- | --- | --- |
| `GET /admin/api/risk-events` | `risk.read` |  | 风控事件列表（`type` / `handled`；联查用户名） |
| `POST /admin/api/risk-events/:id/handle` | `risk.write` |  | 标记风控事件已处理 |
| `GET /admin/api/login-logs` | `audit.read` |  | 登录日志（`user_type`=1 用户 / 2 后台、`status`、`keyword` 匹配用户名或 IP） |

---

## 7. 状态字典

### 7.1 任务状态（`dw_task.status`，`src/modules/dw/taskService.js`）

| 值 | 常量 | status_text | 说明 | 客户端动作 |
| --- | --- | --- | --- | --- |
| 0 | `QUEUED` | 排队中 | 已建单并入队，等待 worker 认领 | 1~2 秒后轮询 |
| 1 | `PARSING` | 解析中 | worker 正在向上游解析 | 每 2~3 秒轮询 |
| 2 | `COMPOSING` | 合成中 | 已解析到直链，正在转存/加水印合成/上传 | 每 2~3 秒轮询 |
| 3 | `SUCCESS` | 已完成 | `out_url` / `images` 可用 | **立即下载**（链接 15 分钟过期） |
| 4 | `FAILED` | 失败 | 失败，`err_msg` 有原因；**已退还配额** | 展示 `err_msg`，可重试提交 |
| 5 | `TIMEOUT` | 超时 | 超过 `task_timeout_sec`（默认 120 秒）未完成；**已退还配额** | 提示稍后重试 |

其它与任务相关的取值：`media_type` = `VIDEO` / `IMAGE`；`watermark` = `1` 已加水印 / `0` 未加水印。

### 7.2 订单状态（`order.status`，`src/services/order.js`）

| 值 | 常量 | status_text | 说明 |
| --- | --- | --- | --- |
| 0 | `PENDING` | 待支付 | 已创建，等待支付（超过 `pay_order_expire_min` 默认 30 分钟自动关闭） |
| 1 | `PAID` | 已支付 | 已入账，自动开通/续费 |
| 2 | `CLOSED` | 已关闭 | 超时未支付被定时任务关闭 |
| 3 | `REFUND` | 已退款 | 后台退款；该用户被置为「已过期」，授权立即终止 |

订单其它取值：`type`（`1` = 卡密购买（**卡密体系已下线，新订单不再产生该类型**，仅历史数据里存在）/ `2` = 续费 / `3` = 升级；后台订单导出 CSV 的「类型」列文案与此一致，见 `src/db/schema.js` 与 `src/modules/admin/business.js`）、`pay_type`（`alipay` / `wxpay` / `card` / `admin` / `mock`，其中 `card` 同样只可能出现在历史订单上）、`pay_channel`（渠道名）。
`payment_record.status`（支付回调流水）：`1` 入账成功 / `2` 回调验签失败 / `3` 金额不符 / `4` 重复回调或交易号冲突。

### 7.3 用户状态（`app_user.status`，`src/services/user.js`）

| 值 | 常量 | status_text | 说明 |
| --- | --- | --- | --- |
| 0 | `INACTIVE` | 未开通会员 | 未开通 / 无有效到期时间 |
| 1 | `NORMAL` | 正常 | 有效期内 |
| 2 | `EXPIRED` | 已过期 | 仅被标记，不删除；续费后可恢复 |
| 3 | `BANNED` | 已封禁 | 所有接口拒绝（`2004`） |

计算规则：`effectiveStatus` 会在返回时实时计算——库里 `status=1` 但 `expire_at` 已过 → 返回 `2`；`status=0` 或 `expire_at` 无法解析 → 返回 `0`。`permanent=true` 表示 `expire_at` 以 `2099-` 开头（永久卡哨兵值 `2099-12-31 23:59:59`）。

> 上表 `status_text` 列取自 `STATUS_TEXT`（`src/services/user.js:16`）：`{0:'未开通会员', 1:'正常', 2:'已过期', 3:'已封禁'}`。`status=0` 的中文文案是「**未开通会员**」（早期版本叫「未激活」），`register` / `login` / `profile` / `status` / `heartbeat` 的 `user.status_text` 都是这个值，客户端不要写死「未激活」。`ERROR_MSG[2001]` 里仍是「账号未激活」，但该码当前不会返回（见 4.3）。

**与 `activated` 的关系（新模型）**：`authStatusPayload` 里的 `activated = (status === 1)`，即只有「正常」才算已开通。邀请码注册出来、后台还没激活的账号落在 `status=0`（`activated=false`、`expire_at=null`），客户端据此显示未激活水印遮罩；后台 `POST /admin/api/users/:id/activate` 把它置为 `1` 并写上 `expire_at`。**未激活（`0`）时 `expire_at` 必为 `null`**，不会出现「有到期时间但未激活」的中间态。

### 7.4 工单状态与分类（`src/modules/ticket/routes.js`）

| 工单 status | status_text |
| --- | --- |
| 0 | 待处理 |
| 1 | 处理中 |
| 2 | 待用户回复 |
| 3 | 已关闭 |

| category | category_text |
| --- | --- |
| 1 | 激活问题 |
| 2 | 解析失败 |
| 3 | 支付问题 |
| 4 | 其他 |

`priority`：整数，默认 2；后台按 `priority ASC` 排序（数字越小越靠前），源码未定义中文文案。
`sender_type`：`1` 用户 / `2` 客服。
`sla_at` 由 `ticket_sla_hours`（默认 24 小时）计算。

### 7.5 邀请码状态（`invite_code.status`，`src/services/invite.js` / `src/db/schema.js`）

| 值 | 含义 | 说明 |
| --- | --- | --- |
| 0 | 未使用 | 可被注册核销 |
| 1 | 已使用 | `used_by` / `used_at` / `used_phone` 有值；**一个邀请码只能创建一个账号，用过的码不可再注册、也不可作废** |
| 2 | 已作废 | 后台 `POST /admin/api/invites/void`（单张）或 `POST /admin/api/invites/batches/:batch_no/void`（整批未使用）写入 |
| 3 | 已过期 | 该码 `expire_at` 已过：注册请求命中时被标记为该状态（后台不会主动批量扫描） |
| 0 | 未使用（重置） | `POST /admin/api/invites/:code/reset` 把 1/2/3 恢复为 0；**删号时会自动触发**（见上文「删号会自动释放邀请码」） |

补充字段（`invite_code` 表）：

| 字段 | 说明 |
| --- | --- |
| `code` | 12 位大写字母数字（字符集剔除 `0/O/1/I/L` 等易混淆字符），展示格式 `XXXX-XXXX-XXXX` |
| `batch_no` | 生成批次号，格式 `IVyyyymmdd-序号` |
| `duration` | 注册后预设时长（天）：**`0` = 注册后仍需后台激活**；`>0` = 注册即开通 |
| `level` / `quota_day` / `max_device` | 注册后预设的等级 / 每日配额（`0`=不限量）/ 设备上限 |
| `expire_at` | **邀请码自身**的有效期（与注册出来的账号到期时间无关） |
| `created_by` / `remark` | 生成该码的管理员 / 备注 |
| `used_phone` | 注册时填写的手机号，便于后台核对 |

> **卡密体系已下线（仅留存历史数据）**：`card_batch` 与 `card_key` 两张表仍在库里（`src/db/schema.js`），但**没有任何接口可以再生成、核销或作废卡密**。
> 相关历史数据只出现在两处：① `GET /admin/api/users/:id` 返回的 `data.cards`（`userDetail` 里按 `used_by` 查 20 条）；② `/auth/register` 写 `register` 类型资料日志时的字段命名沿用（实际记的是邀请码）。
> `card_key.status` 的取值（`0` 未使用 / `1` 已使用 / `2` 已作废 / `3` 已过期）与上表 `invite_code.status` 一致，仅供阅读历史数据时对照。

### 7.6 线路与平台取值补充

- `dw_route.type`：`1` 内置适配器 / 自研解析引擎，`2` 第三方 API 兜底（仅后台字段；实际路由按 `endpoint` 前缀判断：`native:xxx` → 原生适配器，`http(s)://` → 解析接口）。
- `dw_route.enabled`：`1` 启用 / `0` 停用。`platform='all'` 表示覆盖全部平台的兜底线路。
- 平台键与中文名见 6.2.6。

---

## 8. 客户端接入检查清单

**时钟与签名**

- [ ] 启动时先请求 `/api/v1/time`（或 `/healthz`）比对 `ts`，偏差超过 ±60 秒立即提示用户校准；**签名时间窗是 ±300 秒**，超窗全量请求失败（`1003`）。
- [ ] 不要依赖系统时区做签名：`X-Timestamp` 用秒级 Unix 时间戳。
- [ ] 每次请求生成**新的** nonce（建议 16 字节随机 hex，长度 >= 8）；禁止复用固定值或短计数器；重试也要换新 nonce。
- [ ] `X-Timestamp` 不要补前导零（服务端会 `parseInt` 后重新字符串化）。
- [ ] 设备号统一 `.trim().toLowerCase()`，并确保**同一归一化值**同时用于 `X-Device-Id`、`device_id` 参数与签名串。
- [ ] `body_hash` 对**实际发送的字节**求 SHA-256；GET/无体请求用 `sha256("")`。
- [ ] 签名/验签统一小写 hex；HMAC-SHA256（不是 SHA1、不是 Base64）。
- [ ] 响应验签：直接取原始响应文本，剥掉结尾的 `,"sign":"..."` 并补回 `}` 后比对；`sign` 为 `null` 时按「未签名接口」处理。
- [ ] 密钥选择不要搞错：`register` / `login` / `handshake` / `refresh` / `config` 用 `MASTER_KEY`，其余会话接口用 `sign_key`。

**注册与未激活水印（新模型必读）**

- [ ] 新用户走 **邀请码注册** `POST /api/v1/auth/register`（`invite_code` + `username` + `phone` + `password` + `password_confirm`）：**没有卡密入口**，旧的 `POST /api/v1/auth/activate` 固定返回 `4004 卡密激活已停用，请使用邀请码注册，或联系客服开通`，遇到时展示 `msg` 并引导升级/联系客服，**不要重试**。
- [ ] 注册页**必须采集用户名** `username`（必填、新增字段），前端做与服务端一致的预校验：2~20 位、只允许**中文 / 字母 / 数字 / 下划线 / 短横线**、**不能长得像手机号**（`/^1[3-9]\d{9}$/` 一律拒绝）；重名判断**忽略大小写**（`Admin` 与 `admin` 是同一个名字），提示文案与服务端保持一致（`请输入用户名` / `用户名长度需为 2~20 位` / `用户名只能包含中文、字母、数字、下划线或短横线` / `用户名不能是手机号，请换一个` / `该用户名已被使用，请换一个`，全部是 `1001`，直接展示 `msg`）。
- [ ] 注册成功后 `data.user.username` 就是登录标识；`data.user.nickname` 为传入的 `nickname`，未传时**回退为 `username`**。本地同时缓存手机号与用户名，登录框**两者都接受**（忽略大小写，`phone` 字段只是兼容别名）。
- [ ] 「改名入口」建议放在「我 → 账号设置」，调 `POST /api/v1/auth/profile`（会话签名）改 `username`，可选一起传 `nickname`；成功后**用响应里的 `data.user` 覆盖本地缓存即可**——`token` / `refresh` / `sign_key` 都不变，**不要**重新登录、也不要清本地会话。
- [ ] 改名的语义要提前告知用户：**新用户名立即可用于登录、旧用户名当场失效**（再用旧名登录会得到 `1001 账号或密码错误`）；**手机号登录不受影响**；服务端会写 `user_profile_log`（`field='username'`）留痕。单用户 10 次 / 60 秒（bucket `profile`），超限 `3002`，按 `data.retry_after` 退避。
- [ ] 邀请码输入框按 12 位字母数字校验（配置项 `invite_length` = 12），展示为 `XXXX-XXXX-XXXX`；提示用户「一个邀请码只能创建一个账号」。
- [ ] 注册成功（`code=0`）后**必须依据 `user.activated`（或顶层 `activated`）决定是否显示水印遮罩**：`false` → 全屏叠加配置里的 `watermark_overlay_text` 水印文字 + 展示 `activation_notice` 提示；`true` → 不显示。
- [ ] `activated=false` 时**不要**把它当登录失败、更不要拦功能：账号是有效的、会话已建立，**所有业务接口（解析、批量、历史、下单、工单、解绑）都能正常调用**，只是成品会带水印。
- [ ] 客户端**不做登录门禁**：允许用户先进入应用浏览；登录/注册入口放在「我」页顶部的头像与名字上。未登录时同样叠加水印遮罩（此时 `activated` 视为 `false`）。
- [ ] 收到 `2004`（封禁）才是需要终止会话的状态；`2001` 在当前服务端实现中**不会返回**（保留码），不要为它写拦截逻辑。
- [ ] 用户已在后台开通后，客户端在下次 `status` / `heartbeat` 拿到 `activated=true` 就要**立即隐藏水印遮罩**；建议在 App 回到前台（`onResume`）时刷新一次状态，无需新增接口。
- [ ] `user.expire_at` 在未开通会员时是 `null`：到期时间展示要做空值兜底（不要显示 `null` 或 `1970-01-01`），此时应显示「未开通会员」而不是「已过期」。
- [ ] `register_open=false`（`/api/v1/config`）时隐藏或禁用注册入口，并提示联系客服；此时注册接口会返回 `5001`。
- [ ] 邀请码格式/不存在/已使用/已作废/已过期、手机号格式/已注册、用户名缺失/长度/字符集/手机号形态/重名、两次密码不一致、密码过短全部是 `1001`，直接展示 `msg` 即可（服务端文案已经面向用户）。

**会话生命周期**

- [ ] 本地保存 `token` / `refresh` / `sign_key` / `expire_at`（`sign_key` 属于敏感数据，建议 native 层持有，不要落明文日志）。
- [ ] 收到 `2005` → 先 `refresh`；`refresh` 也失败 → 回到 `login`/`register`；`handshake` 失败同样处理。
- [ ] 收到 `9001` → **强制升级**，停止一切业务请求并发起版本检查（`/api/v1/upgrade/check`）。
- [ ] 定期（5~10 分钟）`heartbeat` 续期，避免会话被回收；不要在后台高频心跳。
- [ ] 同一设备重新注册/登录后，必须用新返回的 `token` 与 `sign_key` 覆盖本地值（旧会话已被撤销）。
- [ ] 解绑设备（`/auth/unbind`）后主动清理本地会话，因为该设备全部会话立即失效。

**任务与下载**

- [ ] 理解异步模型：`parse` 只返回 `task_no`；轮询 `/dw/task/{task_no}`；**只有 `status=3` 才有 `out_url`**。
- [ ] `status=4`（失败）与 `status=5`（超时）时配额已退还，可直接重新提交；展示 `err_msg` 便于客服定位。
- [ ] `out_url` 有效期只有 `expire_sec`（默认 900 秒 = 15 分钟）：**拿到就立即下载/播放**，不要写入数据库、不要分享、不要长期缓存。
- [ ] 下载/播放器必须支持 `Range`（206 响应）；已经开始的下载不会因链接过期中断，但**新的请求**需要重新拉取任务详情换新链接。
- [ ] 图集任务用 `images[]` 逐个下载，`out_url == images[0]`。
- [ ] 下载失败（`1002 链接已过期`）时不要重试旧链接，先重新请求任务详情。
- [ ] 提交失败区分：`1001`（链接无效）、`4001`（平台临时不可用）、`3001`（配额用尽）、`3002`（限流，按 `retry_after` 退避）。

**配额、限流与风控**

- [ ] 用 `quota_left` 做前置提示（每次 parse/任务查询都会更新），不要自己推算。
- [ ] 收到 `3001` 不要自动重试；收到 `3002` 按 `data.retry_after` 退避。
- [ ] `service_switch` 关闭时（`5001`）除 `/auth/*` 外全部接口不可用，展示 `msg` 中的维护文案；`register_open=false` 造成的 `5001` 只影响注册接口，按 8 章「注册与未激活水印」处理。
- [ ] 批量解析前先校验 `supports.batch_max` 与 `supports.batch_min_level`，避免 `1001`。

**支付**

- [ ] 下单后用 `pay_url` 打开收银台；用 `/pay/status/{order_no}` 轮询（**不要**依赖前端回调）。
- [ ] **支付回调不参与客户端签名**：客户端不要尝试「模拟回调」，也不要依赖回调完成；以订单查询结果为准。
- [ ] 所有金额按字符串处理（`BigDecimal` / `String`），不要用 `double`。
- [ ] 订单有效期 `expire_sec`（默认 1800 秒）后订单会被关闭，需重新下单。

**配置与升级**

- [ ] 缓存 `/api/v1/config` 整份响应，仅当 `config_version` 变化时刷新（注意该字段实际是字符串，需要 `Number()` 比较）。
- [ ] 配置项类型可直接使用（服务端按 `sys_config.type` 还原 bool/int/json，见 6.4.1）。
- [ ] 关注本次新增的 4 个公开项：`register_open`（是否显示注册入口）、`watermark_overlay_text`（未激活水印文案）、`activation_notice`（未激活提示）、`invite_length`（固定 12，注册页输入校验）。
- [ ] `upgrade/check` 的 `force=true` 时必须阻断使用（可在本地再做一次版本兜底校验）。

---

## 9. 限流与配额规则

### 9.1 三级限流（单 IP / 单设备 / 单用户）

固定窗口计数，落在 KV（Redis 或进程内内存）上；超限返回 `3002`，`data` 为 `{limit, window_sec, retry_after}`。

| 接口 | 单 IP | 单用户 | bucket |
| --- | --- | --- | --- |
| `POST /auth/register` | 20 次 / 60s | — | `register` |
| `POST /auth/login` | 30 次 / 60s | — | `login` |
| `POST /auth/handshake` | 60 次 / 60s | — | `handshake` |
| `POST /auth/refresh` | 60 次 / 60s | — | `refresh` |
| `POST /auth/heartbeat` | 120 次 / 60s（默认） | 120 次 / 60s | `hb` |
| `POST /auth/profile` | 不限 | 10 次 / 60s | `profile` |
| `POST /auth/logout`、`POST /auth/unbind`、`GET /auth/status`、`POST /auth/sign_debug`、`POST /auth/activate`（已停用，固定 4004） | 不限 | 不限 | — |
| `POST /dw/parse` | 60 次 / 60s | 30 次 / 60s | `dw` |
| `POST /dw/batch` | 20 次 / 60s | 10 次 / 60s | `dw-batch` |
| `GET /dw/task/:task_no`、`DELETE /dw/task/:task_no`、`GET /dw/history`、`GET /dw/platforms`、`GET /dw/env` | 不限 | 不限 | — |
| `GET /api/v1/config` | 60 次 / 60s | — | `config` |
| `GET /upgrade/check` | 120 次 / 60s | — | `upgrade` |
| `POST /upgrade/crash` | 30 次 / 60s | — | `crash` |
| `GET /pay/goods`、`GET /pay/status/:order_no` | 不限 | 不限 | — |
| `POST /pay/create` | 30 次 / 60s | 15 次 / 60s | `pay` |
| `POST /ticket/create` | 20 次 / 3600s | 10 次 / 3600s | `ticket` |
| `POST /ticket/reply` | 60 次 / 3600s | 30 次 / 3600s | `ticket-reply` |
| `GET /ticket/list`、`GET /ticket/detail/:ticket_no` | 不限 | 不限 | — |
| `POST /feedback/create` | 12 次 / 3600s | 12 次 / 3600s（按设备） | `feedback` |
| `GET /feedback/image/:name` | 不限 | 不限 | — |
| `/api/v1/time`、`/healthz`、`/dw/file/*` | 不限 | 不限 | — |
| 后台 `POST /admin/api/login` | 15 分钟内失败 5 次锁定 | — | 基于 `login_log` 统计 |

> **实现现状**：中间件支持「单 IP / 单设备 / 单用户」三级限流，其中 `POST /api/v1/dw/parse` 已启用设备维度（默认 40 次/分钟），授权类接口的设备维度显式关闭（`deviceLimit: 0`，因为注册/登录场景下设备号本身就是受控入口）。多进程部署时**必须配置 `REDIS_URL`**，否则内存 KV 各自独立，nonce/配额/限流都会绕开。

### 9.2 每日配额

- 每个用户有 `quota_day`（每日次数），计数键 `quota:{userId}:{YYYY-MM-DD}`，TTL 到当天 24:00（不足 60 秒按 60 秒）。
- 消费时机：`POST /dw/parse`（含命中缓存的复用任务）与 `/dw/batch` 的每条链接，每次计 1 次。
- 退还时机：任务失败（`status=4`）与超时（`status=5`）自动退还，即「失败不计费」。
- 配额不足时任务记录会被删除（不留垃圾任务），返回 `3001`，`data = {limit, used, left:0}`。
- `quota_info` 字段：`quota_day`（上限）、`quota_left`（剩余）、`quota_used`（已用）、`unlimited`（是否不限量）。
- **注意**：`quota_day = 0` 表示**不限量**，此时服务端返回 `unlimited: true` 且 `quota_left: null`（`quota_used` 仍正常累加，仅作统计）。客户端应依据 `unlimited` 决定是否展示额度，不要用 `quota_left === 0` 判断「已用尽」—— 真正用尽的账号会返回 `quota_left: 0`、`unlimited: false`，并以下发 `3001` 为准。
- 被拒的请求**不会**继续抬高 `quota_used`：超限时服务端把计数压回上限，`quota_used` 恒等于 `quota_day`。

### 9.3 批量上限

- 单次批量条数 <= `batch_max`（默认 10，后台可改）；超限 `1001 单次最多提交 10 条`。
- 批量解析要求用户 `level >= batch_min_level`（默认 3）；不足 `1001 批量解析需要等级 3 及以上`。
- 后台批量操作用户单次最多 500 个。

### 9.4 注册 / 激活防爆破锁定

| 项 | 默认值 | 配置项 |
| --- | --- | --- |
| 统计窗口 | 1 小时 | 固定 |
| 失败次数阈值 | 20 次 | `ACTIVATE_FAIL_LIMIT` |
| 锁定时长 | 60 分钟 | `ACTIVATE_LOCK_MINUTES` |
| 锁定提示 | `3002 尝试次数过多，请 N 分钟后再试` | 锁定时长至少 60 秒，不会出现永久锁定 |

- 计数键 `act:fail:{ip}`（窗口 3600 秒），锁定键 `act:lock:{ip}`。
- **适用接口**：`POST /api/v1/auth/register`（沿用同一套计数，防止有人拿邀请码字典试探；失败时写 `login_log`，`msg` 形如 `注册失败(N): 原因`）。旧卡密激活接口已停用，不再计入。
- 注册成功后清空失败计数（`clearActivateFailure`）。
- 触发锁定时会写 `login_log`（`user_type=1, status=0`）并记录风控告警日志。

### 9.5 其它业务性限制

| 项 | 规则 | 配置项 / 说明 |
| --- | --- | --- |
| 设备数上限 | 绑定超过 `max_device` 返回 `2003` | 用户级字段，由邀请码预设 / 商品 / 后台激活时带入 |
| 每月解绑次数 | 默认 2 次，超出 `3002` | `unbind_limit_month`，按自然月计数 |
| 待处理工单 | 达 3 个不允许再建单（`3002`） | 固定 3 |
| 订单有效期 | 默认 30 分钟，超时关闭 | `pay_order_expire_min` |
| 最低支付金额 | 低于 `pay_min_amount`（默认 `"1.00"`）下单报 `1001` | 服务端按整数分比较 |
| 任务超时 | 默认 120 秒，超时置 `status=5` 并退配额 | `task_timeout_sec` |
| 原片/成品缓存 | 默认 24 小时内同用户同链接复用 | `origin_cache_hours` |
| 解析超时 | 默认 25 秒（`estimate_sec` 同源） | `parse_timeout_sec` |
| 后台 token | 120 分钟过期 + 30 分钟空闲登出 | `ADMIN_TOKEN_TTL_MIN` / `ADMIN_IDLE_TIMEOUT_MIN` |
| 风控自动封禁 | 同设备多账号命中时按 `risk_ban_enabled` 决定是否自动封禁 | 默认关闭（只记录 `risk_event`） |

---

## 10. 联调脚本

### 10.1 健康检查（curl）

```bash
# 不需要任何请求头 / 签名
curl -s http://127.0.0.1:7101/healthz
```

期望输出：

```json
{"code":0,"msg":"ok","data":{"service":"doutu-server","env":"development","version":"1.0.0","db":"up","storage":"local","ts":1771056000},"ts":1771056000,"sign":null}
```

生产（经 Nginx）：

```bash
curl -s https://api.example.com/healthz
```

> 单独部署时若 `bindHost=127.0.0.1`，需在同机执行或经 Nginx 访问；直接打公网 IP 会连不上，属于正常现象。

### 10.2 `npm run smoke` —— 授权链路自检

```bash
cd /path/to/doutu-server
npm run smoke          # 也可用 SMOKE_PORT=17101 npm run smoke 改端口
```

- 脚本：`scripts/smoke.js`；使用独立数据库 `data/smoke.db`（会先删除重建），端口默认 `17101`，不污染开发数据。
- 覆盖内容：
  1. 建库 + 种子数据 + 生成 3 个邀请码（`duration=0`，注册后待激活）；
  2. `GET /healthz` 无需签名；
  3. **邀请码注册（用户名 + 手机号 + 两次密码）**（校验 `token` / `sign_key` / `user.username` 回显 / `user.activated=false` / `user.expire_at=null` / `msg` 含「水印」）；
  4. 真实登录：**手机号 / 用户名都可以**（含大小写不敏感、密码错误被拒、`user.activated` 校验）；随后走 `POST /auth/profile` 改名（`username` 必填），校验非法名 / 手机号形态 / 与当前同名 / 忽略大小写的重名被拒，改名成功后**新名可登录、旧名立即失效、手机号登录不受影响**，并在 `user_profile_log` 留下 `field='username'` 记录；
  5. **未开通会员也能用**：`/auth/status` 可查（`activated=false`）、`/auth/heartbeat` 可用、**`/dw/parse` 正常返回 `code=0`**（不再被 `2001` 拦住），且任务被标记为「需要加水印」；
  6. 后台开通（`activateUser`）后 `/auth/status` 立刻变成 `activated=true` 且下发 `expire_at`，之后的任务不再加水印；
  7. **一码一账号边界**：同一邀请码第二次注册被拒、手机号已注册被拒、两次密码不一致被拒、不存在的邀请码被拒、密码过短被拒，并做库内校验（`invite_code.status=1` 的行数 == 账号数）；
  8. 异常分支：签名错误 → `1002`、时间戳超窗 → `1003`、同 nonce 重放 → `1003`、缺少会话凭证 → `2005`；
  9. `handshake` 换发新 `sign_key` 并用新密钥继续请求；
  10. **旧卡密接口下线校验**：`POST /api/v1/auth/activate` 返回 `4004` 且 `msg` 含「邀请码」；
  11. 设备与解绑：`unbind` 后会话立即失效（`2005`）；
  12. 数据落库校验（账号数、密码为 `scrypt$` 哈希、会话记录）。
- 结束时打印「通过 N 项，失败 M 项」，失败时退出码非 0。

### 10.3 `npm run e2e` —— 端到端验收

```bash
cd /path/to/doutu-server
npm run e2e            # 需要本机可用 ffmpeg / ffprobe；E2E_PORT 可改端口（默认 17201）
```

- 脚本：`scripts/e2e.js`；使用独立库 `data/e2e.db`、独立存储目录 `storage/e2e-media` 与 `storage/e2e-tmp`（运行前会清空）。
- 覆盖内容（离线可跑，解析走本地 `native:mock` 线路，不访问第三方平台）：
  1. FFmpeg 生成样本视频 + 启动本地静态服务 + 写入 mock 线路与后台管理员；
  2. **邀请码批次生成 + 邀请码注册登录**（等级/配额由邀请码预设，`password_confirm` 必带）；
  3. 提交 `/dw/parse`，校验返回 `task_no` 与排队状态、配额扣减；
  4. worker 执行：解析 → 原片转存 → 水印合成（`drawtext`）→ 上传；
  5. 轮询任务至 `status=3`，校验 `out_url`、时长，并确认响应**不暴露** `src_url` / `out_key`；
  6. 签名 URL 下载：可下载、含 `ftyp`、与未加印原片不同；`Range` 返回 206 且长度为 1024；**篡改签名被拒（1002）**、**过期链接被拒（1002）**；
  7. 24 小时内同链接命中缓存（`reused=true`、直接 `status=3`）；
  8. 配额边界（`quota_day=1` → 第二次 `3001`、不留垃圾任务）与高频限流（`3002`）；
  9. 版本检查、热更新配置（`/api/v1/config` 不含水印细节参数）、平台列表、历史分页；
  10. 下单 → mock 回调入账 → 重复回调幂等 → 订单状态 → 授权自动续期 → 手续费/净收入与回调留痕；
  11. 工单：用户建单 / 列表 / 详情（含上下文）；
  12. 后台：登录与权限、无 token 被拒（`6001`）、用户列表与**详情（校验返回 `invites` 且邀请码已脱敏为 `****末 4 位`）**、延期、CSV 导出、**邀请码统计/批次/明细筛选/生成/作废/导出**、订单列表与汇总、收益聚合/概览/首页、线路健康度、溯源（含 `evidence_url`）、审计日志、面板环境自检；
  13. 后台工单：列表、快捷回复、客服回复、关闭；
  14. 敏感操作二次确认：无密码 → `1001`、密码错误 → `6002`；RBAC：客服访问全局配置 → `6002`，访问工单 → 通过；
  15. 按版本吊销：`/panel/purge-version` 后被吊销版本请求返回 `9001`。
- 结束时同样打印通过/失败项数，失败退出码非 0。

### 10.4 `npm run test:feedback` —— 意见反馈自检

```bash
cd /path/to/doutu-server
npm run test:feedback
```

- 脚本：`scripts/feedback-test.js`；使用独立库 `data/feedback-test.db` 与独立存储目录（运行前会清空）。
- 覆盖 29 项：
  1. `/api/v1/config` 下发后台改过的 `share_link` / `share_tip`；
  2. 未登录（root 验签）提交反馈 + 带图片，落库字段齐全（`user_id` 为空、记录机型）；
  3. 图片按 `/api/v1/feedback/image/{name}` 原样读回，不存在的文件名 404；
  4. 参数校验：正文太短、非图片、图片超过 3 张；
  5. 后台 `/admin/api/feedback` 列表 / 详情 / 标记已处理，状态与备注、处理人正确落库；
  6. `share_link` 在后台「全局配置」里可改且标记为下发客户端。

---

## 附录：环境变量对接口行为的影响（运维速查）

| 环境变量 | 默认 | 影响的接口行为 |
| --- | --- | --- |
| `MASTER_KEY` | 无（开发回退 `dev-master-key`） | 引导接口验签密钥、响应签名密钥、`/dw/file/*` 的 `sign` 密钥（开发回退 `dev-storage-secret`） |
| `SIGN_WINDOW_SEC` | 300 | 时间窗（`1003`）与 nonce TTL |
| `SESSION_TTL_HOURS` | 24 | `expire_at` / `expire_in`（register / login / heartbeat / refresh） |
| `MIN_SUPPORTED_VERSION_CODE` | 0 | 低于该版本号 → `9001`（`0` 表示不限制） |
| `ADMIN_PATH` | `/admin` | 后台接口与后台前端路径前缀（改动需同步 Nginx） |
| `URL_TTL_SEC` | 900 | `out_url` / `evidence_url` 的有效期（`expire_sec` / `url_ttl_sec`） |
| `STORAGE_DRIVER` / `STORAGE_PUBLIC_BASE` | `local` / `http://127.0.0.1:7101` | `out_url` 的形态（本地签名 URL 或 OSS 直连签名 URL） |
| `PAY_PROVIDER` | `mock` | `pay_url` 形态与回调渠道（`epay` / `mock`） |
| `WORKER_IN_PROCESS` | 未设置 | 为 `1` 时 API 进程同时跑媒体 worker（仅开发） |

---

接口有变更时请同步更新本文档。
