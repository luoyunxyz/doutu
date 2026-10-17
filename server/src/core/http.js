/**
 * 极简 HTTP 框架：路由 + 中间件 + 统一响应 + 静态文件。
 *
 * 为什么不用 Express：本服务只需要「路由表 + JSON 解析 + 统一错误翻译」三件事，
 * 自带依赖越少，宝塔环境里 `npm ci` 失败的概率越低（生产只需可选的 mysql2）。
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import config from '../config.js';
import { logger } from './logger.js';
import { ApiError, ERROR } from './errors.js';
import { buildEnvelope, signEnvelope } from './response.js';
import * as dtw from './dtw.js';

const jsonLogger = logger.child('http');

/** /api/* 才算「业务接口」：静态资源与下载链接不走私有协议 */
const isApiPath = (pathname) => String(pathname || '').startsWith('/api/');

/* ---------------------------- 路由匹配 ---------------------------- */

function matchPath(pattern, pathname) {
  if (pattern === pathname) return {};
  const patternParts = pattern.split('/').filter(Boolean);
  const pathParts = pathname.split('/').filter(Boolean);
  if (patternParts.at(-1) === '*') {
    const head = patternParts.slice(0, -1);
    if (pathParts.length < head.length) return null;
    const params = {};
    for (let i = 0; i < head.length; i += 1) {
      if (head[i].startsWith(':')) params[head[i].slice(1)] = decodeURIComponent(pathParts[i]);
      else if (head[i] !== pathParts[i]) return null;
    }
    return { ...params, '*': pathParts.slice(head.length).join('/') };
  }
  if (patternParts.length !== pathParts.length) return null;
  const params = {};
  for (let i = 0; i < patternParts.length; i += 1) {
    const p = patternParts[i];
    if (p.startsWith(':')) params[p.slice(1)] = decodeURIComponent(pathParts[i]);
    else if (p !== pathParts[i]) return null;
  }
  return params;
}

export class Router {
  constructor({ prefix = '' } = {}) {
    this.prefix = prefix;
    this.routes = [];
    this.middlewares = [];
  }

  use(...fns) {
    this.middlewares.push(...fns);
    return this;
  }

  add(method, pattern, ...handlers) {
    this.routes.push({ method: method.toUpperCase(), pattern: this.prefix + pattern, handlers });
    return this;
  }

  get(p, ...h) {
    return this.add('GET', p, ...h);
  }
  post(p, ...h) {
    return this.add('POST', p, ...h);
  }
  put(p, ...h) {
    return this.add('PUT', p, ...h);
  }
  delete(p, ...h) {
    return this.add('DELETE', p, ...h);
  }
  patch(p, ...h) {
    return this.add('PATCH', p, ...h);
  }

  /** 任意方法（支付回调既可能是 GET 也可能是 POST） */
  all(p, ...h) {
    return this.add('ALL', p, ...h);
  }

  /** 挂载子路由：子路由的 pattern 已是绝对路径（创建时带 prefix），直接合并 */
  mount(sub) {
    this.routes.push(...sub.routes);
    if (sub.middlewares?.length) this.middlewares.push(...sub.middlewares);
    return this;
  }

  /** 允许 OPTIONS 预检直接通过（后台同源，一般用不到，留着以防独立域名部署） */
  match(method, pathname) {
    let methodMismatch = false;
    for (const route of this.routes) {
      const params = matchPath(route.pattern, pathname);
      if (params === null) continue;
      if (route.method !== method && route.method !== 'ALL') {
        methodMismatch = true;
        continue;
      }
      return { route, params };
    }
    return methodMismatch ? { methodMismatch: true } : null;
  }
}

/* ---------------------------- 请求上下文 ---------------------------- */

export class Context {
  constructor(req, res, { params, query, pathname }) {
    this.req = req;
    this.res = res;
    this.method = req.method;
    this.pathname = pathname;
    this.params = params || {};
    this.query = query || {};
    this.headers = req.headers;
    this.rawBody = '';
    this.body = {};
    this.state = {};
    this.startedAt = Date.now();
    this.ip = clientIp(req);
    this.ua = String(req.headers['user-agent'] || '').slice(0, 255);
    this.sent = false;
  }

  get(name) {
    return this.headers[String(name).toLowerCase()];
  }

  /** 取参数：依次查 body → query → params，统一收口，参数校验才好写 */
  input(name, fallback = undefined) {
    if (this.body && this.body[name] !== undefined && this.body[name] !== null && this.body[name] !== '') {
      return this.body[name];
    }
    if (this.query[name] !== undefined && this.query[name] !== '') return this.query[name];
    if (this.params[name] !== undefined) return this.params[name];
    return fallback;
  }

  require(name, msg) {
    const value = this.input(name);
    if (value === undefined || value === null || value === '') {
      throw new ApiError(ERROR.PARAM, msg || `缺少参数 ${name}`);
    }
    return value;
  }

  json(data, status = 200) {
    if (this.sent) return;
    this.sent = true;
    const envelope = signEnvelope(buildEnvelope(data.code ?? ERROR.OK, data.msg, data.data), this.state.signKey);
    const text = JSON.stringify(envelope);
    this.res.writeHead(status, {
      'Content-Type': 'application/json; charset=utf-8',
      'Content-Length': Buffer.byteLength(text),
      'Cache-Control': 'no-store',
    });
    this.res.end(text);
  }

  sendRaw(buffer, { contentType = 'application/octet-stream', status = 200, headers = {} } = {}) {
    if (this.sent) return;
    this.sent = true;
    this.res.writeHead(status, {
      'Content-Type': contentType,
      'Content-Length': buffer.length,
      ...headers,
    });
    this.res.end(buffer);
  }

  redirect(url, status = 302) {
    if (this.sent) return;
    this.sent = true;
    this.res.writeHead(status, { Location: url });
    this.res.end();
  }
}

export function clientIp(req) {
  // 服务只监听 127.0.0.1，Nginx 一定在前面，因此可以信任这两个头
  const real = req.headers['x-real-ip'];
  if (real) return String(real).split(',')[0].trim();
  const fwd = req.headers['x-forwarded-for'];
  if (fwd) return String(fwd).split(',')[0].trim();
  return (req.socket?.remoteAddress || '').replace(/^::ffff:/, '');
}

/* ---------------------------- 服务器 ---------------------------- */

function readBody(req, limitBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limitBytes) {
        reject(new ApiError(ERROR.PARAM, '请求体过大'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

// 请求体上限：默认 2MB —— 头像走 base64 塞 JSON（约 1.4 倍膨胀），1MB 会把正常图片先拦住。
// 真正的大小校验在业务层（见 services/user.js 的 AVATAR_MAX_BYTES）。
export function createServer(router, { bodyLimit = 2 * 1024 * 1024 } = {}) {
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = decodeURI(url.pathname);
    const query = Object.fromEntries(url.searchParams.entries());

    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'no-referrer');

    // 仅开发环境放开跨域：内网真机联调时，页面可能从回环地址加载、却要访问内网 IP 上的服务。
    // 生产环境绝不设置这些头（后台是同源部署，客户端走原生网络栈，都不需要 CORS）。
    if (!config.isProd) {
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Access-Control-Allow-Headers', '*');
      res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
      res.setHeader('Access-Control-Expose-Headers', 'Content-Disposition, Content-Length');
      if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
      }
    }

    /* ---------------- 私有协议隧道（DTW/1） ----------------
     *
     * 客户端把所有接口请求加密成一个二进制帧 POST 到这里；这里解开、还原成
     * 「客户端真正想发的那个请求」，再照常匹配路由跑业务链路 —— 业务代码一行都不用改。
     * 响应同样加密回去（连状态码和 Content-Type 一起封装），抓包只能看到密文。
     */
    const tunnelPath = dtw.isEnabled() ? config.dtw.tunnelPath : null;
    const isTunnel = Boolean(tunnelPath) && pathname === tunnelPath;
    if (isTunnel) {
      let opened = null;
      try {
        const frame = await readBody(req, bodyLimit);
        opened = await dtw.openTunnelRequest(frame, { ip: clientIp(req) });
      } catch (err) {
        const status = err instanceof ApiError ? err.httpStatus || 200 : 500;
        const body = JSON.stringify(buildEnvelope(err.code ?? ERROR.PARAM, err.msg || err.message));
        jsonLogger.warn('DTW 隧道解包失败', { path: pathname, ip: clientIp(req), err: err.message });
        res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(body);
        return;
      }
      // 换一条「内部重放」：把 ctx 指到解密出来的真实请求上
      return runTunneled(router, req, res, opened, { bodyLimit });
    }

    // 强制私有协议：不在白名单里的明文 /api 请求一律拒绝。
    //
    // ⚠️ 必须同时满足「协议确实可用」（`isEnabled` = 开关开着**且**私钥能解析）。
    // 只判 `isRequired` 的话，一旦出现「DTW_REQUIRE 还留着、但私钥没了/被关了」这种配置事故，
    // 客户端既走不了隧道、明文又被 426 拒掉 —— 整个服务对外直接不可用。
    // 可用性优先：协议不可用时宁可放明文，也不能把所有人挡在门外。
    if (dtw.isEnabled() && dtw.isRequired() && isApiPath(pathname) && !dtw.isPlainAllowed(pathname)) {
      jsonLogger.warn('DTW 拒绝明文请求', { method: req.method, path: pathname, ip: clientIp(req) });
      res.writeHead(426, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify(buildEnvelope(ERROR.SIGN, '客户端版本过旧，请升级后使用')));
      return;
    }

    const matched = router.match(req.method.toUpperCase(), pathname);
    if (!matched) {
      // 未匹配的路径也记一条日志：扫描器/爆破的痕迹在应用层也能看到（Nginx 层另有拦截）
      jsonLogger.warn('未匹配的请求路径', {
        method: req.method,
        path: pathname,
        ip: clientIp(req),
        ua: String(req.headers['user-agent'] || '').slice(0, 120),
      });
      const body = JSON.stringify(buildEnvelope(ERROR.NOT_FOUND, '接口不存在'));
      res.writeHead(404, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(body);
      return;
    }
    if (matched.methodMismatch) {
      const body = JSON.stringify(buildEnvelope(ERROR.PARAM, '请求方法不支持'));
      res.writeHead(405, { 'Content-Type': 'application/json; charset=utf-8' });
      res.end(body);
      return;
    }

    const ctx = new Context(req, res, { params: matched.params, query, pathname });

    // 流式路由：处理函数自己消费 req（例如后台上传 APK，36MB 走不了「整个读进内存」那条路）。
    // 只在路由显式声明（handler.streamBody = true）时才跳过通用读体，避免误伤其他接口。
    const streamBody = matched.route.handlers.some((h) => h && h.streamBody === true);

    try {
      if (!streamBody && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(ctx.method)) {
        const raw = await readBody(req, bodyLimit);
        ctx.rawBody = raw.toString('utf8');
        const contentType = String(req.headers['content-type'] || '');
        if (ctx.rawBody && contentType.includes('application/json')) {
          try {
            ctx.body = JSON.parse(ctx.rawBody);
          } catch {
            throw new ApiError(ERROR.PARAM, '请求体不是合法 JSON');
          }
        } else if (ctx.rawBody && contentType.includes('application/x-www-form-urlencoded')) {
          ctx.body = Object.fromEntries(new URLSearchParams(ctx.rawBody).entries());
        } else if (!ctx.rawBody) {
          ctx.body = {};
        } else {
          // 允许非 JSON 体（支付回调有 form 也有 json），保留原文供验签使用
          ctx.body = Object.fromEntries(new URLSearchParams(ctx.rawBody).entries());
        }
      }

      const chain = [...router.middlewares, ...matched.route.handlers];
      await runChain(ctx, chain);

      if (!ctx.sent) ctx.json(buildEnvelope(ERROR.OK, 'ok', null));
    } catch (err) {
      handleError(ctx, err);
    } finally {
      const cost = Date.now() - ctx.startedAt;
      const level = ctx.res.statusCode >= 500 ? 'error' : 'info';
      jsonLogger[level]('request', {
        method: ctx.method,
        path: pathname,
        status: ctx.res.statusCode,
        ms: cost,
        ip: ctx.ip,
        userId: ctx.state.user?.id || null,
        adminId: ctx.state.admin?.id || null,
      });
    }
  });

  server.keepAliveTimeout = 65_000;
  server.headersTimeout = 70_000;
  return server;
}

/**
 * 跑一个「隧道里还原出来的请求」：业务链路完全一样，只是响应被收集起来加密回帧。
 *
 * 关键点：
 *   · ctx 用的是解密出来的 method / pathname / query / body / rawBody，路由照常匹配；
 *   · res.writeHead / write / end 临时接管，业务代码照样 ctx.json()，看不出区别；
 *   · 最后统一发一帧 200 + application/octet-stream，真实状态码与 Content-Type 封在帧里。
 */
async function runTunneled(router, req, res, opened, { bodyLimit = 0 } = {}) {
  void bodyLimit;
  const ctx = new Context(req, res, { params: {}, query: opened.query || {}, pathname: opened.pathname });
  ctx.method = opened.method;
  ctx.rawBody = opened.rawBody || '';
  ctx.body = opened.body || {};
  // 还原客户端自带的请求头（业务层验签 / 时间窗 / 重放拦截要用）
  for (const [name, value] of Object.entries(opened.headers || {})) {
    ctx.headers[String(name).toLowerCase()] = String(value);
  }
  ctx.state.deviceId = opened.deviceId;
  ctx.state.dtw = { sid: opened.sid, seq: opened.seq };

  const chunks = [];
  let status = 200;
  let contentType = 'application/json; charset=utf-8';
  const origWriteHead = res.writeHead.bind(res);
  const origEnd = res.end.bind(res);
  res.writeHead = (code, headers) => {
    status = Number(code) || status;
    const ct = headers && (headers['Content-Type'] || headers['content-type']);
    if (ct) contentType = String(ct);
    return res;
  };
  res.write = (chunk) => {
    if (chunk) chunks.push(Buffer.from(chunk));
    return true;
  };
  res.end = (chunk) => {
    if (chunk) chunks.push(Buffer.from(chunk));
    res.writeHead = origWriteHead;
    res.write = () => true;
    const frame = dtw.sealTunnelResponse(opened.key, opened.sid, opened.seq, {
      status,
      contentType,
      body: Buffer.concat(chunks),
    });
    origWriteHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Length': frame.length,
      'X-DTW-Sid': opened.sid,
    });
    return origEnd(frame);
  };

  try {
    const matched = router.match(opened.method, opened.pathname);
    if (!matched) {
      jsonLogger.warn('DTW 隧道目标路径不存在', { path: opened.pathname, ip: ctx.ip });
      ctx.json(buildEnvelope(ERROR.NOT_FOUND, '接口不存在'));
      return;
    }
    if (matched.methodMismatch) {
      ctx.json(buildEnvelope(ERROR.PARAM, '请求方法不支持'));
      return;
    }
    ctx.params = matched.params;
    await runChain(ctx, [...router.middlewares, ...matched.route.handlers]);
    if (!ctx.sent) ctx.json(buildEnvelope(ERROR.OK, 'ok', null));
  } catch (err) {
    handleError(ctx, err);
  } finally {
    jsonLogger.info('request', {
      method: opened.method,
      path: opened.pathname,
      status,
      ms: Date.now() - ctx.startedAt,
      ip: ctx.ip,
      userId: ctx.state.user?.id || null,
      adminId: ctx.state.admin?.id || null,
      dt: 1,
    });
  }
}

async function runChain(ctx, handlers) {  let index = -1;
  const next = async () => {
    index += 1;
    const handler = handlers[index];
    if (!handler) return;
    await handler(ctx, next);
  };
  await next();
}

function handleError(ctx, err) {
  if (err instanceof ApiError) {
    if (err.code === ERROR.SIGN || err.code === ERROR.SESSION_INVALID) {
      jsonLogger.warn('业务拒绝', { code: err.code, msg: err.msg, path: ctx.pathname, ip: ctx.ip });
    }
    ctx.json(buildEnvelope(err.code, err.msg, err.extra ?? null), err.httpStatus || 200);
    return;
  }
  jsonLogger.error('未捕获异常', { path: ctx.pathname, err });
  if (!ctx.sent) {
    ctx.json(buildEnvelope(ERROR.SERVER, '服务异常，请稍后重试'), 200);
  }
}

export async function listen(server) {
  await new Promise((resolve) => server.listen(config.port, config.bindHost, resolve));
  return server.address();
}

/** 静态文件：只允许白名单后缀，且必须落在 baseDir 之内（防目录穿越）。 */
export function serveStatic(baseDir, { index = 'index.html' } = {}) {
  const root = path.resolve(baseDir);
  return async (ctx) => {
    let rel = ctx.params['*'] || '';
    if (!rel || rel.endsWith('/')) rel += index;
    const target = path.resolve(root, rel);
    if (!target.startsWith(root)) {
      ctx.res.writeHead(403).end('forbidden');
      ctx.sent = true;
      return;
    }
    if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
      ctx.res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      ctx.res.end('not found');
      ctx.sent = true;
      return;
    }
    // 静态文件一定要带校验头（ETag / Last-Modified）：
    // 以前这里什么都不发，浏览器就按「启发式缓存」自己决定 —— 后台改完页面，
    // 运营那边刷新多少次都还是旧的（真实踩到：版本管理页改了却看不到）。
    // 现在发 no-cache + ETag：每次都会回源校验，没变就 304，既不慢也永远是最新的。
    const stat = fs.statSync(target);
    const etag = `W/"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`;
    const headers = {
      'Cache-Control': 'no-cache',
      ETag: etag,
      'Last-Modified': stat.mtime.toUTCString(),
    };
    if (String(ctx.get('if-none-match') || '') === etag) {
      ctx.res.writeHead(304, headers);
      ctx.res.end();
      ctx.sent = true;
      return;
    }
    ctx.sendRaw(fs.readFileSync(target), { contentType: mimeOf(target), headers });
  };
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.mp4': 'video/mp4',
  '.apk': 'application/vnd.android.package-archive',
};
export const mimeOf = (file) => MIME[path.extname(file).toLowerCase()] || 'application/octet-stream';

export default { Router, createServer, listen, serveStatic, Context, mimeOf };
