/**
 * 抖音原生适配器：不依赖任何第三方 API，直接走分享页数据。
 *
 * 链路（规划书 3.1 第 2、3 步）：
 *   ① 短链跟随 302 展开 → 提取 item_id
 *   ② 请求分享页 https://www.iesdouyin.com/share/video/{item_id}/
 *   ③ 从 HTML 里的 window._ROUTER_DATA 取出结构化数据
 *   ④ play_addr 拿到的是带水印线路，替换 playwm → play 得到无水印直链
 *
 * 平台风控一年改几十次，这里的每一步都单独 try/catch 并回落到下一条线路，
 * 绝不能因为某一步失败把整个任务判死。
 */
import { logger } from '../../../core/logger.js';
import { fetchWithTimeout } from '../../../core/util.js';

const log = logger.child('adapter:douyin');

const MOBILE_UA =
  'Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36';

/** 从各种形态的抖音链接里取 item_id */
export function extractItemId(url) {
  const patterns = [
    /\/video\/(\d{6,})/,
    /\/note\/(\d{6,})/,
    /modal_id=(\d{6,})/,
    /item_ids?=(\d{6,})/,
    /\/share\/video\/(\d{6,})/,
    /\/(\d{17,})/,
  ];
  for (const re of patterns) {
    const m = String(url).match(re);
    if (m) return m[1];
  }
  return null;
}

/** 跟随短链 302，拿到真实地址（抖音短链不带 body，用 manual 重定向最省流量） */
async function expandShortLink(url, timeoutMs) {
  if (!/v\.douyin\.com|v\.iesdouyin\.com/.test(url)) return url;
  try {
    const res = await fetchWithTimeout(
      url,
      { method: 'GET', redirect: 'manual', headers: { 'User-Agent': MOBILE_UA } },
      timeoutMs,
    );
    const location = res.headers.get('location');
    if (location) return location.startsWith('http') ? location : new URL(location, url).toString();
    // 有些节点直接返回 HTML，交给调用方继续处理
    return url;
  } catch (err) {
    log.warn('短链展开失败，改用原链接继续', { err: err.message });
    return url;
  }
}

/** 把 _ROUTER_DATA 里的 item 结构归一化成平台无关的结果 */
export function normalizeItem(item, itemId) {
  if (!item) return null;
  const video = item.video || {};
  const playAddr = video.play_addr || video.playApi || video.play_addr_h264 || {};
  const urlList = playAddr.url_list || playAddr.urlList || [];
  let videoUrl = urlList[0] || null;
  if (videoUrl) videoUrl = videoUrl.replace('playwm', 'play'); // 关键一步：换成无水印线路

  const coverList =
    item.video?.cover?.url_list ||
    item.video?.origin_cover?.url_list ||
    item.video?.dynamic_cover?.url_list ||
    [];
  const images = (item.images || [])
    .map((img) => img?.url_list?.[0] || img?.urlList?.[0] || null)
    .filter(Boolean);

  const author = item.author || {};
  return {
    platform: 'douyin',
    itemId: String(item.aweme_id || item.awemeId || itemId || ''),
    mediaType: images.length ? 'IMAGE' : 'VIDEO',
    title: item.desc || item.item_title || '',
    desc: item.desc || '',
    cover: coverList[0] || null,
    videoUrl: images.length ? null : videoUrl,
    videoUrls: images.length ? [] : urlList.map((u) => String(u).replace('playwm', 'play')),
    images,
    audioUrl: item.music?.play_url?.url_list?.[0] || null,
    author: {
      nickname: author.nickname || '',
      id: author.uid || author.sec_uid || '',
      avatar: author.avatar_thumb?.url_list?.[0] || author.avatar_168x168?.url_list?.[0] || null,
    },
    durationMs: Number(video.duration || item.duration || 0) || null,
    raw: undefined,
  };
}

/** 从分享页 HTML 中抽出 _ROUTER_DATA */
export function parseRouterData(html) {
  const marker = 'window._ROUTER_DATA';
  const index = html.indexOf(marker);
  if (index === -1) return null;
  const start = html.indexOf('{', index);
  if (start === -1) return null;
  // 括号配平扫描，避免正则贪婪匹配把 JSON 截断
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = start; i < html.length; i += 1) {
    const ch = html[i];
    if (inString) {
      if (escape) escape = false;
      else if (ch === '\\') escape = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') inString = true;
    else if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) {
        try {
          return JSON.parse(html.slice(start, i + 1));
        } catch {
          return null;
        }
      }
    }
  }
  return null;
}

function pickItemFromRouterData(data) {
  if (!data) return null;
  const loader = data.loaderData || {};
  for (const key of Object.keys(loader)) {
    const node = loader[key];
    const list =
      node?.videoInfoRes?.item_list ||
      node?.videoInfoRes?.aweme_detail ||
      node?.videoInfoRes?.itemList ||
      node?.aweme_detail;
    if (Array.isArray(list) && list.length) return list[0];
    if (list && typeof list === 'object' && (list.aweme_id || list.awemeId)) return list;
  }
  return null;
}

export default {
  name: 'douyin-native',
  platform: 'douyin',

  match(url) {
    return /douyin\.com|iesdouyin\.com/.test(String(url || ''));
  },

  async resolve(url, { routeConfig = {}, timeoutMs = 15000 } = {}) {
    const realUrl = await expandShortLink(url, timeoutMs);
    const itemId = extractItemId(realUrl) || extractItemId(url);
    if (!itemId) throw new Error('无法从链接中解析出 item_id');

    // 线路①：分享页 HTML 里的 _ROUTER_DATA
    try {
      const res = await fetchWithTimeout(
        `https://www.iesdouyin.com/share/video/${itemId}/`,
        {
          headers: {
            'User-Agent': routeConfig.userAgent || MOBILE_UA,
            Referer: 'https://www.douyin.com/',
            'Accept-Language': 'zh-CN,zh;q=0.9',
          },
        },
        timeoutMs,
      );
      if (res.ok) {
        const html = await res.text();
        const item = pickItemFromRouterData(parseRouterData(html));
        const normalized = normalizeItem(item, itemId);
        if (normalized && (normalized.videoUrl || normalized.images.length)) return normalized;
      }
    } catch (err) {
      log.warn('分享页解析失败，尝试备用接口', { itemId, err: err.message });
    }

    // 线路②：官方 iteminfo 接口（部分节点仍可用）
    const apiUrl = `https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=${itemId}`;
    const res2 = await fetchWithTimeout(apiUrl, { headers: { 'User-Agent': MOBILE_UA } }, timeoutMs);
    if (!res2.ok) throw new Error(`抖音接口返回 HTTP ${res2.status}`);
    const json = await res2.json();
    const item2 = json?.item_list?.[0];
    const normalized2 = normalizeItem(item2, itemId);
    if (!normalized2 || (!normalized2.videoUrl && !normalized2.images.length)) {
      throw new Error('抖音解析结果为空（可能被风控拦截或作品已删除）');
    }
    return normalized2;
  },
};
