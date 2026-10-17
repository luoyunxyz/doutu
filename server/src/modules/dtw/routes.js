/**
 * 私有传输协议 DTW/1 的对外端点。
 *
 * 只有两个（第三个是隧道本身，在 core/http.js 里直接拦掉，不走路由）：
 *   POST /api/v1/dtw/hs   —— 握手（hello / open 两步共用）
 *   GET  /api/v1/dtw/info —— 客户端自检：协议是否启用、公钥指纹、盐代号（不含任何私密材料）
 *
 * 为什么握手不复用隧道：隧道的前提是有会话密钥，而会话密钥正是握手的产物。
 * 所以握手这一段自己做了保护 —— RSA 混合加密（内容只有服务端能读）+ 服务端签名（结果客户端可验真）。
 */
import { Router } from '../../core/http.js';
import { clientAuth } from '../../middleware/clientAuth.js';
import { ApiError, ERROR } from '../../core/errors.js';
import * as dtw from '../../core/dtw.js';

export const dtwRouter = new Router({ prefix: '/api/v1/dtw' });

const noopAuth = clientAuth({ mode: 'none', requireActive: false, rate: false, skipVersionCheck: true });

/** 握手：hello 拿挑战，open 建会话 */
dtwRouter.post('/hs', noopAuth, async (ctx) => {
  if (!dtw.isEnabled()) throw new ApiError(ERROR.NOT_FOUND, '接口不存在');
  await dtw.handshake(ctx);
});

/**
 * 自检：这是客户端**在什么都没建立之前**就要能访问的端点（用来确认协议开没开、
 * 内置公钥有没有被换），所以它不挂 clientAuth —— 那套要求时间戳/nonce 的中间件
 * 在这里只会让「先有鸡还是先有蛋」的问题绕回来。
 *
 * 返回的都是可公开材料：是否启用、公钥**指纹**（不是公钥本身）、盐代号、迭代次数。
 */
dtwRouter.get('/info', async (ctx) => {
  const info = dtw.bootstrapInfo();
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      enabled: info.enabled,
      required: info.required,
      version: info.version,
      kid: info.kid,
      salt_id: info.salt_id,
      proof_iter: info.proof_iter,
      tunnel_path: info.tunnel_path,
      handshake_path: info.handshake_path,
      // 指纹：与客户端内置公钥的 DER 指纹比对，不一致说明中间有人在换钥匙
      key_fingerprint: dtw.keyFingerprint(),
    },
  });
});

export default dtwRouter;
