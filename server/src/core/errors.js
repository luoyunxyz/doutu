/**
 * 统一错误码（规划书 8.1）。
 *
 * 业务代码一律 `throw new ApiError(ERROR.NOT_ACTIVATED)`，
 * 由 HTTP 层统一翻译成 { code, msg } 响应，禁止各处自己拼错误结构。
 */
export const ERROR = {
  OK: 0,
  PARAM: 1001,
  SIGN: 1002,
  TIMESTAMP: 1003,
  NOT_ACTIVATED: 2001,
  EXPIRED: 2002,
  DEVICE_LIMIT: 2003,
  BANNED: 2004,
  SESSION_INVALID: 2005,
  QUOTA: 3001,
  RATE_LIMIT: 3002,
  PARSE_FAILED: 4001,
  TASK_TIMEOUT: 4002,
  BUSY: 5001,
  VERSION_LOW: 9001,
  // 后台侧（沿用同一张表，避免前端多套判断）
  ADMIN_AUTH: 6001,
  ADMIN_FORBIDDEN: 6002,
  ADMIN_TOTP: 6003,
  NOT_FOUND: 4004,
  SERVER: 5000,
};

export const ERROR_MSG = {
  [ERROR.OK]: 'ok',
  [ERROR.PARAM]: '参数错误',
  [ERROR.SIGN]: '签名校验失败',
  [ERROR.TIMESTAMP]: '请求已过期或重复提交',
  [ERROR.NOT_ACTIVATED]: '账号未激活',
  [ERROR.EXPIRED]: '账号已过期',
  [ERROR.DEVICE_LIMIT]: '设备数超限',
  [ERROR.BANNED]: '此账号已被封禁',
  [ERROR.SESSION_INVALID]: '身份过期，请重新登录',
  [ERROR.QUOTA]: '今日额度已用完',
  [ERROR.RATE_LIMIT]: '操作过于频繁，请稍后再试',
  [ERROR.PARSE_FAILED]: '解析失败，请稍后重试',
  [ERROR.TASK_TIMEOUT]: '任务处理超时',
  [ERROR.BUSY]: '服务端繁忙，请稍后再试',
  [ERROR.VERSION_LOW]: '版本过低，请升级后使用',
  [ERROR.ADMIN_AUTH]: '登录已失效，请重新登录',
  [ERROR.ADMIN_FORBIDDEN]: '没有该操作权限',
  [ERROR.ADMIN_TOTP]: '动态验证码错误',
  [ERROR.NOT_FOUND]: '资源不存在',
  [ERROR.SERVER]: '服务异常，请稍后重试',
};

/** HTTP 状态码映射：业务失败也返回 200，仅鉴权/限流类用 4xx，便于 Nginx 侧统计 5xx。 */
const HTTP_STATUS = {
  [ERROR.SIGN]: 401,
  [ERROR.SESSION_INVALID]: 401,
  [ERROR.ADMIN_AUTH]: 401,
  [ERROR.ADMIN_FORBIDDEN]: 403,
  [ERROR.RATE_LIMIT]: 429,
  [ERROR.NOT_FOUND]: 404,
};

export class ApiError extends Error {
  constructor(code, msg, extra) {
    super(msg || ERROR_MSG[code] || 'error');
    this.name = 'ApiError';
    this.code = code;
    this.msg = msg || ERROR_MSG[code] || 'error';
    this.extra = extra;
    this.httpStatus = HTTP_STATUS[code] || 200;
  }
  get isApiError() {
    return true;
  }
}

export const fail = (code, msg, extra) => new ApiError(code, msg, extra);
export const paramError = (msg = ERROR_MSG[ERROR.PARAM]) => new ApiError(ERROR.PARAM, msg);

/** 断言助手：失败即抛 ApiError，避免业务代码里散落 if/throw。 */
export function assert(condition, code, msg) {
  if (!condition) throw new ApiError(code, msg);
}

export default { ERROR, ERROR_MSG, ApiError, fail, paramError, assert };
