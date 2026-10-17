/**
 * 平台适配器（规划书 3.1：适配器插件化）。
 *
 * 每个平台一个独立文件，统一契约：
 *   { name, platform, match(url), resolve(url, ctx) → Normalized }
 * Normalized 形状：
 *   { platform, itemId, mediaType:'VIDEO'|'IMAGE', title, desc, cover,
 *     videoUrl, videoUrls[], images[], audioUrl, author:{nickname,id,avatar}, durationMs }
 *
 * 解析规则失效时改 dw_route 的配置即可，不用发版 —— 这是这个业务最核心的运维能力。
 */
import * as douyin from './douyin.js';
import * as mediaparser from './mediaparser.js';
import * as mock from './mock.js';

/** 原生适配器注册表：dw_route.endpoint 形如 native:douyin */
export const nativeAdapters = {
  douyin: douyin.default,
  mock: mock.default,
};

export const adapters = { douyin: douyin.default, mediaparser: mediaparser.default, mock: mock.default };

/**
 * 平台识别表（规划书 3.1 第 1 步）。
 * 顺序有意义：先匹配更具体的域名。
 */
export const PLATFORMS = [
  { key: 'douyin', name: '抖音', hosts: ['v.douyin.com', 'www.douyin.com', 'douyin.com', 'iesdouyin.com'] },
  { key: 'kuaishou', name: '快手', hosts: ['v.kuaishou.com', 'www.kuaishou.com', 'kuaishou.com', 'chenzhongtech.com'] },
  { key: 'xiaohongshu', name: '小红书', hosts: ['xhslink.com', 'www.xiaohongshu.com', 'xiaohongshu.com'] },
  { key: 'bilibili', name: '哔哩哔哩', hosts: ['b23.tv', 'www.bilibili.com', 'bilibili.com', 'm.bilibili.com'] },
  { key: 'wechat', name: '微信视频号', hosts: ['channels.weixin.qq.com', 'mp.weixin.qq.com', 'weixin.qq.com'] },
  { key: 'weibo', name: '微博', hosts: ['weibo.com', 'weibo.cn', 'm.weibo.cn'] },
  { key: 'xigua', name: '西瓜视频', hosts: ['ixigua.com', 'xigua.com'] },
  { key: 'toutiao', name: '今日头条', hosts: ['toutiao.com', 'm.toutiao.com'] },
  { key: 'pipixia', name: '皮皮虾', hosts: ['pipixia.com', 'h5.pipix.com', 'pipix.com'] },
  { key: 'weishi', name: '微视', hosts: ['weishi.qq.com', 'isee.weishi.qq.com'] },
  { key: 'huoshan', name: '火山小视频', hosts: ['huoshan.com', 'hsvideo.com'] },
  { key: 'peiyinxiu', name: '配音秀', hosts: ['peiyinxiu.com'] },
  { key: 'meipai', name: '美拍', hosts: ['meipai.com'] },
  { key: 'acfun', name: 'AcFun', hosts: ['acfun.cn', 'm.acfun.cn'] },
  { key: 'zhihu', name: '知乎', hosts: ['zhihu.com', 'zhuanlan.zhihu.com'] },
  { key: 'lvzhou', name: '绿洲', hosts: ['oasis.weibo.cn'] },
  { key: 'dewu', name: '得物', hosts: ['dewu.com', 'poizon.com'] },
  { key: 'qsmusic', name: '汽水音乐', hosts: ['qishui.com', 'music.douyin.com'] },
  { key: 'kuwo', name: '酷我音乐', hosts: ['kuwo.cn'] },
];

/** 从分享文案里抠出 URL（正则与客户端 native 层保持一致，规划书附「接口文档」第 5.1 节） */
export const URL_REGEX = /(https?|ftp|file):\/\/[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]/;

export function extractUrl(text) {
  if (!text) return null;
  const match = String(text).match(URL_REGEX);
  return match ? match[0].replace(/[，。、）)】\s]+$/, '') : null;
}

/** 识别平台；识别不出返回 'unknown'（仍会走 all 兜底线路） */
export function detectPlatform(url) {
  if (!url) return 'unknown';
  let host = '';
  try {
    host = new URL(url).hostname.toLowerCase();
  } catch {
    return 'unknown';
  }
  for (const p of PLATFORMS) {
    for (const h of p.hosts) {
      if (h.includes('/')) continue; // 带路径的规则只用于展示
      if (host === h || host.endsWith(`.${h}`)) return p.key;
    }
  }
  return 'unknown';
}

export const platformName = (key) => PLATFORMS.find((p) => p.key === key)?.name || key;

/** 支持的平台列表（/api/v1/dw/platforms） */
export function platformList() {
  return PLATFORMS.map((p) => ({ key: p.key, name: p.name, hosts: p.hosts.filter((h) => !h.includes('/')) }));
}

/**
 * 按线路执行解析。
 * 线路类型：native:<name> → 原生适配器；http(s) → 解析接口（本地引擎或第三方）。
 */
export async function runRoute(route, url, ctx = {}) {
  const endpoint = String(route.endpoint || '').trim();
  const routeConfig = parseConfig(route.config);

  if (endpoint.startsWith('native:')) {
    const name = endpoint.slice('native:'.length);
    const adapter = nativeAdapters[name];
    if (!adapter) throw new Error(`未实现的原生适配器: ${name}`);
    return adapter.resolve(url, { ...ctx, route, routeConfig });
  }

  if (/^https?:\/\//i.test(endpoint)) {
    const contract = routeConfig.contract || (endpoint.includes('/api/v1/parse') ? 'media-parser' : 'generic');
    if (contract === 'media-parser') {
      return mediaparser.default.resolve(url, { ...ctx, route, routeConfig, endpoint });
    }
    return mediaparser.resolveGeneric(url, { ...ctx, route, routeConfig, endpoint, type: route.type });
  }

  throw new Error(`线路 ${route.name} 未配置可用的 endpoint`);
}

export function parseConfig(raw) {
  if (!raw) return {};
  if (typeof raw === 'object') return raw;
  try {
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

/** 结果校验：没有可下载地址的解析结果一律视为失败，避免把空结果写进任务表 */
export function assertUsableResult(result) {
  if (!result) throw new Error('解析结果为空');
  const hasVideo = Boolean(result.videoUrl);
  const hasImages = Array.isArray(result.images) && result.images.length > 0;
  if (!hasVideo && !hasImages) throw new Error('解析结果中没有任何可下载媒体地址');
  return result;
}

export default {
  nativeAdapters,
  adapters,
  PLATFORMS,
  extractUrl,
  detectPlatform,
  platformName,
  platformList,
  runRoute,
  parseConfig,
  assertUsableResult,
};
