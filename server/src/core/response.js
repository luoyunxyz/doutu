/**
 * 统一响应封装（规划书 8.1）。
 *
 * 成功：{"code":0,"msg":"ok","data":{...},"ts":1771056000,"sign":"..."}
 * 失败：{"code":1002,"msg":"签名校验失败","data":null,"ts":...,"sign":"..."}
 *
 * 响应签名：客户端 native 层用 verifyResponse(body, sign) 校验，算法与请求签名同一套 HMAC-SHA256。
 *   sign = HMAC-SHA256(会话 sign_key, JSON.stringify({code,msg,data,ts}))   ← sign 字段本身不参与
 * 未建立会话的接口（如 activate）用 MASTER_KEY 签，客户端两种都支持。
 */
import { hmacSha256Hex } from './crypto.js';
import { ERROR, ERROR_MSG } from './errors.js';

export function buildEnvelope(code, msg, data) {
  return {
    code,
    msg: msg || ERROR_MSG[code] || (code === ERROR.OK ? 'ok' : 'error'),
    data: data === undefined ? null : data,
    ts: Math.floor(Date.now() / 1000),
  };
}

export function signEnvelope(envelope, secret) {
  if (!secret) return { ...envelope, sign: null };
  const body = JSON.stringify(envelope);
  return { ...envelope, sign: hmacSha256Hex(secret, body) };
}

export const ok = (data, msg = 'ok') => buildEnvelope(ERROR.OK, msg, data);
export const fail = (code, msg, data = null) => buildEnvelope(code, msg, data);

export default { buildEnvelope, signEnvelope, ok, fail };
