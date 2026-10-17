/**
 * 解析引擎适配器：对接 name/shuiyin 的 media-parser（Flask，默认 127.0.0.1:8051）。
 *
 * 它已经支持 50 个平台且是本地自研解析（不套壳第三方 API），
 * 因此这是「自研线路」的第一选择；dw_route 里一条 all 兜底即可覆盖全部平台。
 *
 * 上游响应契约（media-parser README）：
 *   { retcode: 200, retdesc: '成功', succ: true, data: {
 *       video_id, platform, title, desc, video_url, video_list[], audio_url,
 *       cover_url, author: {nickname, author_id, avatar}, image_list[], subtitles[] } }
 * 失败：{ retcode: 400, retdesc: '...', error_code: 'PLATFORM_NOT_SUPPORTED', succ: false }
 */
import { logger } from '../../../core/logger.js';
import { fetchWithTimeout } from '../../../core/util.js';

const log = logger.child('adapter:mediaparser');

/** 把上游的 image_list 归一化：元素可能是字符串，也可能是实况图对象 */
function normalizeImages(list) {
  if (!Array.isArray(list)) return [];
  return list
    .map((item) => {
      if (typeof item === 'string') return { url: item };
      if (item && typeof item === 'object') {
        return { url: item.url || item.image_url || null, livePhotoUrl: item.live_photo_url || null };
      }
      return null;
    })
    .filter((x) => x && x.url);
}

export function normalizeMediaParser(json, fallbackPlatform) {
  if (!json) throw new Error('上游返回空响应');
  if (json.succ === false || (json.retcode && json.retcode !== 200)) {
    throw new Error(`上游解析失败：${json.retdesc || json.error_code || '未知错误'}`);
  }
  const data = json.data || json;
  const images = normalizeImages(data.image_list);
  const videoUrl = data.video_url || data.video_list?.[0] || null;

  return {
    platform: (data.platform_key || fallbackPlatform || 'unknown').toString().toLowerCase(),
    platformName: data.platform || null,
    itemId: String(data.video_id || data.item_id || ''),
    mediaType: images.length && !videoUrl ? 'IMAGE' : 'VIDEO',
    title: data.title || data.desc || '',
    desc: data.desc || '',
    cover: data.cover_url || data.cover || null,
    videoUrl,
    videoUrls: Array.isArray(data.video_list) ? data.video_list : videoUrl ? [videoUrl] : [],
    images,
    audioUrl: data.audio_url || null,
    author: {
      nickname: data.author?.nickname || '',
      id: data.author?.author_id || data.author?.id || '',
      avatar: data.author?.avatar || null,
    },
    durationMs: Number(data.duration || data.duration_ms || 0) || null,
    subtitles: Array.isArray(data.subtitles) ? data.subtitles.slice(0, 200) : [],
  };
}

export default {
  name: 'media-parser',
  platform: 'all',

  /** 自带 endpoint（dw_route.endpoint），不走 match */
  match() {
    return true;
  },

  async resolve(url, { endpoint, routeConfig = {}, timeoutMs = 20000 } = {}) {
    if (!endpoint) throw new Error('未配置解析引擎地址');
    const target = new URL(endpoint);
    target.searchParams.set('url', url);
    if (routeConfig.key) target.searchParams.set('key', routeConfig.key);

    const res = await fetchWithTimeout(
      target.toString(),
      {
        method: routeConfig.method === 'POST' ? 'POST' : 'GET',
        headers: {
          Accept: 'application/json',
          ...(routeConfig.headers || {}),
          ...(routeConfig.key ? { Authorization: `Bearer ${routeConfig.key}` } : {}),
        },
        ...(routeConfig.method === 'POST'
          ? { body: JSON.stringify({ url }), headers: { 'Content-Type': 'application/json' } }
          : {}),
      },
      timeoutMs,
    );
    if (!res.ok) throw new Error(`解析引擎返回 HTTP ${res.status}`);
    const json = await res.json();
    return normalizeMediaParser(json);
  },
};

/**
 * 通用第三方解析接口适配器（dw_route.type=2）。
 * 用 config.mapping 描述字段位置，避免为每个供应商写一个文件：
 *   {
 *     "url": "data/video_url",          // 支持 data.value.url 这样的路径
 *     "title": "data/title",
 *     "cover": "data/cover",
 *     "images": "data/images",           // 数组
 *     "okCode": 0, "codeField": "code",  // 业务成功码
 *     "method": "GET", "param": "url"
 *   }
 */
export async function resolveGeneric(url, { endpoint, routeConfig = {}, timeoutMs = 20000, type = 2 } = {}) {
  if (!endpoint) throw new Error('第三方线路未配置 endpoint');
  const mapping = routeConfig.mapping || {};
  const paramName = routeConfig.param || 'url';
  const method = String(routeConfig.method || 'GET').toUpperCase();

  let target = endpoint;
  const headers = { Accept: 'application/json', ...(routeConfig.headers || {}) };
  let body;
  if (method === 'GET') {
    const u = new URL(endpoint);
    u.searchParams.set(paramName, url);
    for (const [k, v] of Object.entries(routeConfig.params || {})) u.searchParams.set(k, String(v));
    target = u.toString();
  } else {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify({ [paramName]: url, ...(routeConfig.params || {}) });
  }

  const res = await fetchWithTimeout(target, { method, headers, body }, timeoutMs);
  if (!res.ok) throw new Error(`第三方接口 HTTP ${res.status}`);
  const json = await res.json();

  const codeField = routeConfig.codeField || 'code';
  const okCode = routeConfig.okCode ?? 0;
  const code = getPath(json, codeField);
  if (code !== undefined && code !== null && String(code) !== String(okCode)) {
    throw new Error(`第三方接口业务失败：${getPath(json, routeConfig.msgField || 'msg') || code}`);
  }

  const pick = (key, fallbackPath) => {
    const pathExpr = mapping[key] || fallbackPath;
    return pathExpr ? getPath(json, pathExpr) : undefined;
  };

  const imagesRaw = pick('images', null);
  const images = Array.isArray(imagesRaw)
    ? imagesRaw.map((x) => (typeof x === 'string' ? { url: x } : { url: x?.url || x?.image_url })).filter((x) => x.url)
    : [];

  const videoUrl = pick('url', 'data.url') || null;
  const result = {
    platform: pick('platform', 'data.platform') || 'unknown',
    itemId: String(pick('itemId', 'data.item_id') || ''),
    mediaType: videoUrl ? 'VIDEO' : images.length ? 'IMAGE' : 'VIDEO',
    title: pick('title', 'data.title') || '',
    desc: pick('desc', 'data.desc') || '',
    cover: pick('cover', 'data.cover') || null,
    videoUrl,
    videoUrls: videoUrl ? [videoUrl] : [],
    images,
    audioUrl: pick('audio', 'data.audio_url') || null,
    author: {
      nickname: pick('author', 'data.author.nickname') || '',
      id: pick('authorId', 'data.author.id') || '',
      avatar: pick('avatar', 'data.author.avatar') || null,
    },
    durationMs: Number(pick('duration', 'data.duration') || 0) || null,
  };
  log.debug('第三方线路解析完成', { type, platform: result.platform });
  return result;
}

/** 取 `a/b/c` 形式的嵌套路径 */
export function getPath(obj, pathExpr) {
  if (!pathExpr) return undefined;
  if (typeof pathExpr !== 'string') return obj;
  return pathExpr
    .replace(/\[(\d+)\]/g, '.$1')
    .split(/[/.]/)
    .filter(Boolean)
    .reduce((acc, key) => (acc === undefined || acc === null ? undefined : acc[key]), obj);
}
