/**
 * GET /api/v1/upgrade/check —— 版本检查与灰度发布（规划书 6.5）。
 *
 * 三个关键机制：
 *  ① 灰度按 device_id 的**哈希**取模，而不是随机数 —— 否则同一设备每次请求看到的版本都不同；
 *  ② force=1 或版本低于 min_version → 强制更新；
 *  ③ 渠道包独立版本线（channel）。
 */
import { Router, serveStatic } from '../../core/http.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { clientAuth } from '../../middleware/clientAuth.js';
import { getDb } from '../../db/index.js';
import { getBool, getConfig, getInt } from '../../services/sysconfig.js';
import { stableHash } from '../../core/util.js';
import { headerDeviceId } from '../../middleware/clientAuth.js';
import config from '../../config.js';
import fs from 'node:fs';
import path from 'node:path';

export const upgradeRouter = new Router({ prefix: '/api/v1/upgrade' });

/**
 * 升级包静态下载：`/upgrade/app/xxx.apk` → `public/upgrade/app/xxx.apk`。
 *
 * <p>`public/upgrade/README.md` 里写的方案是「Nginx 直接把 /upgrade/ 映射到该目录」，
 * 但本项目的实际部署（宝塔）里 `location ^~ /upgrade/` 是**反代给 Node** 的 ——
 * 结果是后台「版本推送」里填的那个下载地址谁也下不动。这里补一个静态路由把两边接上，
 * 顺带保留 nginx 直发静态文件的做法（换成 alias 后这条路由就自然不会再被命中）。
 */
export const upgradeFileRouter = new Router({ prefix: '/upgrade' });

upgradeFileRouter.get('/*', (ctx, next) => {
  const dir = path.join(config.rootDir, 'public', 'upgrade');
  if (!fs.existsSync(dir)) throw new ApiError(ERROR.NOT_FOUND, '升级包目录不存在');
  return serveStatic(dir)(ctx, next);
});

upgradeRouter.get(
  '/check',
  clientAuth({ mode: 'none', requireActive: false, rate: { bucket: 'upgrade', ipLimit: 120, windowSec: 60 }, skipVersionCheck: true }),
  async (ctx) => {
    const db = getDb();
    // 参数可以走 query（文档示例）也可以走 header（签名不覆盖 query，客户端应优先用 header）
    const versionCode =
      Number.parseInt(String(ctx.get('x-version-code') || ctx.input('version_code', '0')), 10) || 0;
    const channel = String(ctx.input('channel', 'official') || 'official');
    const deviceId = headerDeviceId(ctx) || String(ctx.input('device_id', '')).trim();
    const platform = String(ctx.input('platform', 'android'));

    const forceCfg = await getBool('app_force_update', false);
    const latestCfg = await getInt('app_latest_version_code', 1);
    const title = await getConfig('app_update_title', '新版本可用');
    const downloadUrl = await getConfig('app_download_url', '');

    // 优先取 app_version 表里已发布的版本（支持灰度与渠道包）
    const candidates = await db.query(
      'SELECT * FROM `app_version` WHERE `status` = 1 AND `platform` = ? AND (`channel` = ? OR `channel` = ?) ORDER BY `version_code` DESC',
      [platform, channel, 'all'],
    );

    const pick = candidates.find((row) => {
      const gray = Number(row.gray_percent ?? 100);
      if (gray >= 100) return true;
      if (!deviceId) return false;
      // 灰度：同一 device_id 永远命中同一档
      return stableHash(`${deviceId}:${row.version_code}`, 100) < gray;
    });

    const latest = pick
      ? {
          version_code: Number(pick.version_code),
          version_name: pick.version_name,
          url: pick.url,
          patch_url: pick.patch_url || null,
          patch_md5: pick.patch_md5 || null,
          size: pick.size ? Number(pick.size) : null,
          changelog: pick.changelog || '',
          force: Number(pick.force) === 1 || Number(pick.min_version || 0) > versionCode,
          gray_percent: Number(pick.gray_percent ?? 100),
          title,
        }
      : {
          version_code: latestCfg,
          version_name: '',
          url: downloadUrl,
          patch_url: null,
          patch_md5: null,
          size: null,
          changelog: '',
          force: forceCfg,
          gray_percent: 100,
          title,
        };

    const hasUpdate = Boolean(latest.version_code > versionCode && latest.url);
    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: {
        has_update: hasUpdate,
        current_version_code: versionCode,
        channel,
        ...(hasUpdate ? latest : { version_code: versionCode, title }),
        server_ts: Math.floor(Date.now() / 1000),
      },
    });
  },
);

/** 客户端崩溃上报（灰度观察期要看崩溃率；写入 login_log 之外的轻量通道） */
upgradeRouter.post(
  '/crash',
  clientAuth({ mode: 'none', requireActive: false, rate: { bucket: 'crash', ipLimit: 30, windowSec: 60 }, skipVersionCheck: true }),
  async (ctx) => {
    const { logger } = await import('../../core/logger.js');
    logger.child('crash').error('客户端崩溃上报', {
      deviceId: headerDeviceId(ctx),
      version: ctx.get('x-version-code') || null,
      model: ctx.input('model', null),
      osVersion: ctx.input('os_version', null),
      stack: String(ctx.input('stack', '')).slice(0, 2000),
    });
    ctx.json({ code: ERROR.OK, msg: 'ok', data: null });
  },
);

export default upgradeRouter;
