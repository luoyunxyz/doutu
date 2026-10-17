# DTW/1 —— 私有传输协议

> 2026-10-17 起。目标：**客户端不再持有任何服务端长期密钥**，且抓包看到的是密文。
>
> ⚠️ 先把话说清楚：私有协议**挡不住**铁了心的逆向者 —— 他能逆向出算法并照做一遍。
> 它挡的是「抓包就能读、改包就能玩」这一档，真正的价值在于：
> ① APK 里不再有 `MASTER_KEY`（以前反编译就能拿，可伪造官方客户端、铸造下载签名、推导会话密钥）；
> ② 每个设备一个可限流、可吊销的身份；③ 流量是密文，明文 JSON 抄不走。

## 一、密钥分工（互不牵连）

| 材料 | 位置 | 泄露后果 |
|---|---|---|
| `DTW_PRIVATE_KEY`（RSA-2048 私钥） | **只在服务端** `.env` | 可解密握手、伪造服务端签名 |
| DTW 公钥（PEM） | 编译进客户端 | 无（公钥本来就公开） |
| `DTW_SALT` / `DTW_SALT_ID` | 服务端 `.env` + 下发客户端 | 无（盐是公开输入，用于慢哈希） |
| `MASTER_KEY` | **只在服务端** | 存储签名 / 管理端 / 会话派生 —— **绝不再进客户端** |

生成：`node scripts/gen-dtw-keys.js --write`

## 二、三段式流程

```
① hello    客户端 ──{step:"hello", device_id, install_id}──▶ 服务端
           服务端 ──{salt, salt_id, challenge, kid, proof_iter, expire_at, sig}──▶ 客户端
           （客户端必须用内置公钥验 sig；这是唯一的信任锚，防中间人换钥匙）

② open     客户端：proof = PBKDF2-SHA256(salt|challenge|device|install|bucket, iter)
                    hs_key = 随机 32B
           客户端 ──{step:"open", ek: RSA-OAEP(服务端公钥, hs_key),
                     iv/ct/tag: AES-GCM(hs_key, {session_key, proof, device, install})}──▶ 服务端
           服务端：解 RSA → 解 AES → 验 challenge 一次性 → 复算 proof → 建会话
           服务端 ──{sid, expire_at, sig}──▶ 客户端

③ tunnel   之后所有接口都是一个二进制帧，POST 到 DTW_TUNNEL_PATH
```

**服务端能真正验证慢哈希证明**：salt 与 challenge 都是服务端刚发的，device/install/bucket 在
RSA+AES 密文里，所以服务端可以原样复算比对。这是一道「必须实现并执行慢哈希」的门槛（防脚本），
同时不依赖任何静态密钥。

`bucket = floor(时间 / 600s)`：用时间片而不是精确时间戳，否则两边时钟差几百毫秒就对不上。

## 三、帧格式

```
偏移  长度  字段
0     4     magic  "DTW1"
4     1     ver    0x01
5     1     flags  0x00
6     8     sid    会话号（用于查会话密钥）
14    4     seq    序号（大端）
18    12    iv
30    n     ciphertext
30+n  16    tag     GCM 认证标签
```

* 加密：`AES-256-GCM`，AAD = 前 18 字节（magic..seq）→ 帧头也不可篡改
* 请求载荷（明文 JSON）：
  ```json
  { "m":"POST", "p":"/api/v1/dw/parse", "q":{}, "b":{…业务体…},
    "rb":"<原始 JSON 串>", "t":1712345678,
    "h":{ "x-timestamp":"…", "x-nonce":"…", "x-sign":"…" } }
  ```
  `rb` 必须是客户端**签名时用的那个原始串** —— 业务层验签是对它取 SHA256 的，
  用解出来的对象重新 stringify 会因键序不同而验签失败。
  `h` 是客户端原样带的业务请求头（时间窗 / nonce / 签名都靠它们）。
* 响应载荷：
  ```json
  { "s":200, "ct":"application/json; charset=utf-8", "b":"<base64 原始响应体>" }
  ```
  真实状态码与 Content-Type 封在帧里，客户端还原后业务语义完全不变。

## 四、服务端行为

* **隧道拦截**在 `core/http.js` 里做（早于路由匹配）：解帧 → 还原成真实请求 → 照常匹配路由跑
  业务链路 → 把响应封帧回去。**业务代码一行都不用改。**
* `DTW_REQUIRE=1` 时，不在白名单里的 `/api/*` 明文请求一律 `426` 拒绝（只保新包）。
  白名单：`DTW_PLAIN_WHITELIST` + 握手端点 + `/api/v1/dtw/info`。
* 隧道请求到达业务层时 `ctx.state.dtw` 已设置 → `clientAuth` 的 `root` 模式**不再要求 root 签名**
  （隧道已用会话密钥认证），这正是「客户端不再需要 MASTER_KEY」的落点。
* 序号：只接受比已见最大值回退 64 以内的序号；真正的防重放仍由业务层 nonce 一次性 + 时间窗兜底。

## 五、配置项（`.env`）

| 变量 | 默认 | 说明 |
|---|---|---|
| `DTW_ENABLED` | `false` | 总开关；没配私钥时自动视为关闭 |
| `DTW_REQUIRE` | `false` | 打开后明文 `/api` 被拒（生产切新包时再开） |
| `DTW_PRIVATE_KEY` | — | RSA 私钥 PEM，支持 `\n` 转义单行 |
| `DTW_SALT` / `DTW_SALT_ID` | — | 慢哈希盐与代号；换代号可让旧客户端握手失效 |
| `DTW_PROOF_ITER` | `20000` | 慢哈希迭代次数，**两端必须一致** |
| `DTW_SESSION_TTL` | `3600` | 会话有效期（秒） |
| `DTW_TS_WINDOW` | `300` | 握手与帧的时间窗（秒） |
| `DTW_TUNNEL_PATH` | `/api/v1/dtw` | 隧道地址 |
| `DTW_HANDSHAKE_PATH` | `/api/v1/dtw/hs` | 握手地址 |
| `DTW_PLAIN_WHITELIST` | `/healthz,/dw/file,/admin,/avatar,/upgrade,/devtest` | 允许明文的前缀 |

## 六、回滚

三条独立开关，出问题按顺序退：

1. `DTW_REQUIRE=0` —— 明文重新放行（老包立刻能用）；
2. `DTW_ENABLED=0` —— 隧道整个停用（新包会退回明文通道，若客户端实现了回退）；
3. 换 `DTW_SALT_ID` —— 只让所有客户端重新握手（不动密钥、不动会话）。

回归：`node scripts/dtw-test.js`（19 项，覆盖握手/隧道/防重放/防篡改/慢哈希/强制模式）。
