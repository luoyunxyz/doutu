/**
 * 自检/端到端测试专用适配器：不访问任何外网，直接返回本地媒体地址。
 *
 * 只在 dw_route.endpoint = 'native:mock' 且该线路被显式启用时才会用到，
 * 生产环境的种子数据里这条线路是关闭的（enabled=0），不会误命中。
 *
 * 线路 config 形状：
 *   {
 *     "videoUrl": "http://127.0.0.1:19999/sample.mp4",   // 图集场景留空
 *     "images": ["http://.../1.png"],                    // 图集地址
 *     "cover": "...", "title": "自检样本",
 *     "failFor": "e2e-fail"                              // 链接里含该子串时故意失败，用于验证失败补偿
 *   }
 */
export default {
  name: 'mock',
  platform: 'mock',

  match(url) {
    return /mock:\/\/|\.local\/|127\.0\.0\.1/.test(String(url || ''));
  },

  async resolve(url, { routeConfig = {} } = {}) {
    // 刻意失败分支：验证「线路失败 → 降级 → 全失败 → 退配额且不计费」这条链路
    if (routeConfig.failFor && String(url).includes(String(routeConfig.failFor))) {
      throw new Error(`mock 线路按配置失败（failFor=${routeConfig.failFor}）`);
    }
    const videoUrl = routeConfig.videoUrl || routeConfig.video_url;
    const images = Array.isArray(routeConfig.images) ? routeConfig.images : [];
    if (!videoUrl && !images.length) {
      throw new Error('mock 线路未配置 videoUrl/images');
    }
    return {
      platform: routeConfig.platform || 'mock',
      itemId: String(routeConfig.itemId || 'mock-item-1'),
      mediaType: videoUrl ? 'VIDEO' : 'IMAGE',
      title: routeConfig.title || '自检样本视频',
      desc: routeConfig.desc || '这是服务端自检用的样本，不涉及任何第三方内容',
      cover: routeConfig.cover || null,
      videoUrl: videoUrl || null,
      videoUrls: videoUrl ? [videoUrl] : [],
      images: images.map((u) => ({ url: u })),
      audioUrl: null,
      author: { nickname: 'SELF-TEST', id: 'self-test', avatar: null },
      durationMs: Number(routeConfig.durationMs || 0) || null,
      sourceUrl: url,
    };
  },
};
