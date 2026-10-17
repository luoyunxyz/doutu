/**
 * GET /api/v1/config —— 配置热更新（规划书 6.5 第 5 条）。
 *
 * 这个接口比 App 版本推送更重要：解析线路、水印策略、公告、客服入口改了之后，
 * 不可能等用户升级 App 才生效。客户端带版本号缓存，配置一变 config_version 就变。
 *
 * 用 MASTER_KEY 验签：客户端启动时还没有会话密钥，但 so 里有 MASTER_KEY。
 */
import { Router } from '../../core/http.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { clientAuth, headerDeviceId } from '../../middleware/clientAuth.js';
import { sha256Hex } from '../../core/crypto.js';
import { publicConfig, getInt, getJson, getBool, getConfig, watermarkPolicy } from '../../services/sysconfig.js';
import { getDb } from '../../db/index.js';
import * as sessionService from '../../services/session.js';
import { now, formatDate } from '../../core/util.js';
import { logger } from '../../core/logger.js';
import { formatVersionCode } from '../../core/version.js';
import { config as appConfig } from '../../config.js';
import { platformList } from '../dw/adapters/index.js';

export const configRouter = new Router({ prefix: '/api/v1' });

/**
 * 版本检查（客户端启动 + 每次回到前台调用）：返回该平台最新已发布的版本。
 *
 * 命中规则：platform 匹配 + status=1（已发布）+ version_code 大于客户端当前版本。
 * platform 只认 android / ios；版本号不是数字时一律按「无更新」处理，不报错。
 *
 * 2026-10-17 修的三处「后台推了版本、客户端没反应」：
 *   1. **渠道兜底**：以前是 `channel = ?` 精确匹配，客户端不带 channel（等于 all），
 *      所以运营只要在「高级设置 → 渠道」里填过任意一个词，这个版本就永远推不到任何人手上。
 *      现在先找同渠道的，找不到再退回落 `all` 的版本。
 *   2. **灰度真的生效**：`gray_percent` 以前存了但没人用 —— 后台填 10% 也是全量推。
 *      现在按 device_id 哈希取模决定推不推；`force=1` 的强制版本不吃灰度（必须更新就是要
 *      所有人都升）。
 *   3. 响应里带上 `reason`，说明这次为什么推/不推，排查时不用猜。
 */
function grayBucket(deviceId) {
  return parseInt(sha256Hex(String(deviceId || 'anonymous')).slice(0, 8), 16) % 100;
}

/**
 * 这条更新是否适用于某个来源版本。
 *
 * `target_versions` 是逗号分隔的 version_code 名单（后台「指定版本」），
 * **留空表示所有版本都收**（历史行为不变）；填了就只有名单里的版本能看到这条更新。
 * 强制更新与非强制更新走的是同一套判断，所以「指定版本」对两者都生效。
 */
function targetsVersion(raw, currentCode) {
  const list = String(raw || '')
    .split(',')
    .map((s) => Number(String(s).trim()))
    // ⚠️ 必须排除非正数：空串的 Number('') 是 0，不过滤的话「留空=全部」会被当成名单 [0]，
    // 结果是所有版本都收不到更新（这个坑被 version-check-test 当场抓到过）
    .filter((n) => Number.isFinite(n) && n > 0);
  if (!list.length) return true;
  return list.includes(Number(currentCode));
}

/**
 * 记一笔「客户端上报过的版本」。
 *
 * 为什么要记：后台「指定版本」要勾的其实是**用户手机上正在跑的版本**，而不是后台发布过的记录 ——
 * 运营把旧版本记录删掉之后，勾选列表就空了（真实踩到）。有了这张表，后台永远能看到
 * 「哪些版本还有设备在用」，并直接勾选推送。
 *
 * 写入策略：每种 (平台, 版本号) 每分钟最多写一次（进程内节流），且**不阻塞响应** ——
 * 版本检查是客户端每次回前台都会调的接口，不能让统计变成负担。
 */
const clientVersionSeen = new Map();
function recordClientVersion(platform, versionCode, versionName) {
  if (!versionCode) return;
  // 客户端没带版本名（比如脚本直接 curl）时，用编码反推一个（10001 → 1.0.1），
  // 免得后台勾选列表里出现一堆「未命名」。
  // ⚠️ 只对**新编码**（≥10000）反推：老包用的是 1/2/3 这种裸数字，反推出来会变成
  // 「0.0.1」这种更误导的名字，宁可留空、让后台显示成「版本号 1」。
  const derived = versionCode >= 10000 ? formatVersionCode(versionCode) : '';
  const name = String(versionName || '').trim() || derived || null;
  const key = `${platform}:${versionCode}`;
  const nowMs = Date.now();
  const last = clientVersionSeen.get(key) || 0;
  if (nowMs - last < 60_000) return;
  clientVersionSeen.set(key, nowMs);
  const ts = now();
  (async () => {
    const db = getDb();
    const row = await db.get('SELECT `id`, `hit_count` FROM `client_version` WHERE `platform` = ? AND `version_code` = ?', [
      platform,
      versionCode,
    ]);
    if (row) {
      await db.update(
        'client_version',
        { id: row.id },
        { version_name: name, hit_count: Number(row.hit_count || 0) + 1, last_at: ts },
      );
    } else {
      await db.insert('client_version', {
        platform,
        version_code: versionCode,
        version_name: name,
        hit_count: 1,
        first_at: ts,
        last_at: ts,
        created_at: ts,
      });
    }
  })().catch((err) => logger.warn('记录客户端版本失败', { err: err.message }));
}

configRouter.get('/version/check', async (ctx) => {
  const platform = String(ctx.input('platform', '') || '').trim().toLowerCase();
  if (!['android', 'ios'].includes(platform)) {
    throw new ApiError(ERROR.PARAM, 'platform 只能是 android 或 ios');
  }
  const currentCode = Number(ctx.input('version_code', 0)) || 0;
  const rawChannel = String(ctx.input('channel', '') || '').trim();
  // 客户端**没有**声明渠道（本 App 就是这种）时不按渠道过滤：否则运营在
  // 「高级设置 → 渠道」里随手填一个词，这个版本就谁都收不到 —— 这正是
  // 「后台推了版本、客户端没反应」的一个真实来源。声明了渠道的客户端才精确匹配（含 all 兜底）。
  const clientChannel = rawChannel || null;
  const deviceId = headerDeviceId(ctx);
  // 记下这个版本（后台「指定版本」的勾选列表就是靠它知道哪些版本还有设备在用）
  recordClientVersion(platform, currentCode, String(ctx.get('x-app-version') || ''));

  const where = ['`platform` = ?', '`status` = 1', '`version_code` > ?'];
  const params = [platform, currentCode];
  if (clientChannel) {
    where.push('(`channel` = ? OR `channel` = ?)');
    params.push(clientChannel, 'all');
  }

  // 「更新推送指定版本」（2026-10-17）：一条更新可以只在**指定的来源版本**上生效。
  //
  // 为什么在 JS 里筛而不是写进 SQL：`FIND_IN_SET` 是 MySQL 专有函数，而本项目是
  // SQLite/MySQL 双方言；版本表本来就只有个位数行，取前 20 条在内存里挑最直观，
  // 也顺便让「最高且适用于你的那条版本」这个语义写在一处、看得见。
  //
  // 语义：**最高且适用于当前版本的记录胜出**。
  // 例如 code=2 只推给 [1]、code=3 只推给 [2] —— 1 的客户端拿到 2，而不是被判「不适用」。
  const rows = await getDb().query(
    'SELECT `version_code`, `version_name`, `url`, `patch_url`, `patch_md5`, `size`, `changelog`, `force`, `gray_percent`, `min_version`, `channel`, `target_versions`, `published_at` ' +
      `FROM \`app_version\` WHERE ${where.join(' AND ')} ` +
      'ORDER BY `version_code` DESC, (`channel` = ?) DESC, `id` DESC LIMIT 20',
    [...params, clientChannel || ''],
  );
  const row = rows.find((r) => targetsVersion(r.target_versions, currentCode)) || null;
  // 有更新，但都不适用于这台设备：给后台一个可分辨的原因，省得排查时以为是没推成功
  const blockedByTarget = rows.length > 0 && !row;

  // 「必须更新」的两种来源，任一命中就是强制：
  //   1) 这条版本自己标了必须更新（旧版本作废）；
  //   2) 客户端版本低于这条的 min_version（该版本已不再被支持）。
  const forcedByMode = row ? Number(row.force) === 1 : false;
  const forcedByMin =
    row && row.min_version !== null && row.min_version !== undefined && currentCode < Number(row.min_version);
  const force = Boolean(row) && (forcedByMode || forcedByMin);

  // 灰度：只有非强制版本才按比例放行
  const grayPercent = row ? Number(row.gray_percent ?? 100) : 100;
  const bucket = grayBucket(deviceId);
  const grayHit = !row || force || grayPercent >= 100 || bucket < grayPercent;
  const hit = Boolean(row) && grayHit;

  let reason = 'no_release';
  if (!row) reason = blockedByTarget ? 'version_not_targeted' : 'no_newer_release';
  else if (!grayHit) reason = 'gray_miss';
  else reason = 'has_update';

  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      has_update: hit,
      force: hit ? force : false,
      reason,
      platform,
      channel: clientChannel || 'all',
      current_version_code: currentCode,
      gray_percent: row ? grayPercent : null,
      gray_bucket: row && grayPercent < 100 ? bucket : null,
      latest: hit
        ? {
            version_code: Number(row.version_code),
            version_name: row.version_name,
            url: row.url,
            patch_url: row.patch_url || null,
            patch_md5: row.patch_md5 || null,
            size: row.size === null || row.size === undefined ? null : Number(row.size),
            changelog: row.changelog || '',
            force: Number(row.force) === 1,
            channel: row.channel || 'all',
            min_version: row.min_version === null || row.min_version === undefined ? null : Number(row.min_version),
            published_at: row.published_at || null,
          }
        : null,
    },
  });
});



/**
 * 文字池清洗：接受数组或（后台多行文本框传来的）字符串，去掉空行、限长限条数。
 * 上排/下排各最多 20 条、每条 120 字，避免有人把整篇文章塞进来。
 */
function sanitizeList(input) {
  const arr = Array.isArray(input)
    ? input
    : String(input ?? '')
        .split('\n')
        .map((s) => s.trim());
  return arr
    .map((s) => String(s ?? '').trim())
    .filter((s) => s.length > 0)
    .slice(0, 20)
    .map((s) => s.slice(0, 120));
}

configRouter.get(
  '/config',
  clientAuth({ mode: 'root', requireActive: false, rate: { bucket: 'config', ipLimit: 60, userLimit: 0, windowSec: 60 } }),
  async (ctx) => {
    const db = getDb();
    // 等级取值优先级：
    //   1) 带 X-Token 时按会话查**库里的真实等级**（不可伪造，会员才看得到会员公告）；
    //   2) 否则用 query / 请求头里的 level（游客场景）；
    //   3) 都没有按 1 处理。
    // 注意签名只覆盖 pathname，所以 level 走 query 或请求头都不会破坏验签。
    let level = Number(ctx.input('level', ctx.get('x-user-level') || 0)) || 0;
    const token = String(ctx.get('x-token') || '').trim();
    if (token) {
      const session = await sessionService.findByToken(token);
      if (session && sessionService.isSessionValid(session)) {
        const row = await db.get('SELECT `level` FROM `app_user` WHERE `id` = ?', [session.user_id]);
        if (row) level = Number(row.level) || level;
      }
    }
    if (!level) level = 1;
    const [publicCfg, version, policy, notices, routes, disabled, appVersion] = await Promise.all([
      publicConfig(),
      getInt('config_version', 1),
      watermarkPolicy(level),
      db.query('SELECT `id`, `title`, `content`, `type`, `target_level`, `start_at`, `end_at` FROM `notice` WHERE `status` = 1 ORDER BY `sort` ASC, `id` DESC LIMIT 20'),
      db.query('SELECT `id`, `platform`, `name`, `type`, `config`, `priority` FROM `dw_route` WHERE `enabled` = 1 ORDER BY `priority` ASC'),
      getJson('disabled_platforms', []),
      getInt('app_latest_version_code', 1),
    ]);

    const today = now();
    // 时间段过滤：start_at/end_at 任一为空表示「不限」，都在服务端判，客户端时钟不可信
    const inWindow = notices.filter(
      (n) => (!n.start_at || n.start_at <= today) && (!n.end_at || n.end_at >= today),
    );
    // 等级过滤：target_level 是逗号分隔的等级列表，含 * 表示所有人
    const visibleNotices = inWindow.filter((n) => {
      const target = String(n.target_level || '').trim();
      if (!target || target === '*') return true;
      return target
        .split(',')
        .map((s) => s.trim())
        .includes(String(level));
    });

    const bannerEnabled = await getBool('home_banner_enabled', true);
    const rotateSec = Math.max(3, await getInt('notice_rotate_sec', 8));

    const enableWatermark = await getBool('watermark_enabled', true);

    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: {
        // 公开配置放前面：下面这些字段是权威值，不允许被 sys_config 的同名键覆盖
        ...publicCfg,
        config_version: version,
        server_ts: Math.floor(Date.now() / 1000),
        // 水印策略：客户端只能看到「有没有水印」，加什么、加在哪一律不下发参数
        watermark: {
          enabled: Boolean(enableWatermark && policy.enabled),
          visible: true,
        },
        supports: {
          platforms: platformList(),
          disabled_platforms: Array.isArray(disabled) ? disabled : [],
          routes: routes.map((r) => ({ platform: r.platform, name: r.name, priority: r.priority })),
          batch_max: await getInt('batch_max', 10),
          batch_min_level: await getInt('batch_min_level', 3),
        },
        // 注册与开通会员（邀请码注册 + 后台开通；会员只决定有没有水印）
        register_open: await getBool('register_open', true),
        watermark_overlay_text: (await getConfig('watermark_overlay_text', '开通会员去除水印')) || '开通会员去除水印',
        activation_notice: (await getConfig('activation_notice', '未开通会员，可直接使用（成品带水印）')) || '',
        invite_length: 12,
        // 合规与客服：客户端要在登录/注册页展示版权承诺（规划书第 0 章硬性要求）
        legal_notice: (await getConfig('legal_notice', '')) || '',
        customer_service_qq: (await getConfig('customer_service_qq', '')) || '',
        customer_service_wx: (await getConfig('customer_service_wx', '')) || '',
        // 「我 → 人工客服」那一页的三样东西，全部由后台「全局配置 → 客服」分组管理：
        //   notice   二维码上方那行字
        //   qr_image 二维码图片地址（http(s)）
        //   contact  二维码下方那行联系方式（手机号或微信号），客户端点击可复制
        customer_service: {
          notice: (await getConfig('cs_notice', '')) || '',
          contact: (await getConfig('cs_contact', '')) || '',
          qr_image: (await getConfig('cs_qr_image', '')) || '',
          qq: (await getConfig('customer_service_qq', '')) || '',
          wx: (await getConfig('customer_service_wx', '')) || '',
          url: (await getConfig('customer_service_url', '')) || '',
        },
        // 「娱乐（更多玩法）」整页由服务端下发（2026-10-17）：分区、顺序、标题/副标题、开关、
        // 点击去向（key）。这样「玩法」这部分内容不在 APK 里 —— 抄走包也拿不到，
        // 运营改文案/下线玩法也不用发版。
        playbook: {
          version: await getInt('playbook_version', 1),
          entertainment: await getJson('playbook_entertainment', null),
        },
        notices: visibleNotices.map((n) => ({
          id: n.id,
          title: n.title,
          content: n.content,
          // 1 弹窗 / 2 顶部滚动 / 3 首页横幅（叠加在首页大图上的文字框）
          type: Number(n.type),
          target_level: n.target_level,
        })),
        // 《用户须知与免责声明》：首次启动就要展示，所以放在公开配置里（不要求登录）。
        // 后台改了正文会同步递增 version，客户端发现版本变化会重新要求用户确认。
        terms: {
          text: String((await getConfig('terms_text', '')) || ''),
          version: await getInt('terms_version', 1),
          updated_at: String((await getConfig('terms_updated_at', '')) || ''),
        },
        // 首页横幅：上排（人物旁边）与下排（白色长条）**各自一个文字池、各自一个轮播间隔**；
        // type=3 的公告会把自己的 title/content 追加进对应的池子（带时间段的临时活动）。
        banner: {
          enabled: Boolean(bannerEnabled),
          rotate_sec: rotateSec,
          default_title: (await getConfig('home_banner_default_title', '')) || '',
          default_hint: (await getConfig('home_banner_default_hint', '')) || '',
          title_list: sanitizeList(await getJson('banner_title_list', [])),
          hint_list: sanitizeList(await getJson('banner_hint_list', [])),
          title_rotate_sec: Math.max(3, await getInt('banner_title_rotate_sec', 8)),
          hint_rotate_sec: Math.max(3, await getInt('banner_hint_rotate_sec', 5)),
          // 按时段问候语：每条形如 `19-22|晚上好！…`，客户端用它替换上排的 {greeting}
          greetings: sanitizeList(await getJson('banner_greetings', [])),
        },
        latest_version_code: appVersion,
        storage_driver: appConfig.storage.driver,
        url_ttl_sec: appConfig.storage.urlTtlSec,
        task_timeout_sec: await getInt('task_timeout_sec', 120),
        today: formatDate(),
      },
    });
  },
);

/** 服务端时间校准：客户端时钟被用户改快/改慢会导致签名全量失败（规划书附则第 8 条） */
configRouter.get(
  '/time',
  clientAuth({ mode: 'none', requireActive: false, rate: false, skipVersionCheck: true }),
  async (ctx) => {
    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: { server_ts: Math.floor(Date.now() / 1000), server_time: now() },
    });
  },
);

export default configRouter;
