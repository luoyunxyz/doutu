/**
 * 线路选择与降级（规划书 3.1）。
 *
 * 规则：
 *  - 按 platform 精确匹配 + platform='all' 兜底，优先级 priority 越小越先试；
 *  - 主线路失败自动切备用线路，全部失败才算任务失败；
 *  - 每次结果都记进 dw_route 累计值与小时桶，供「解析成功率 < 80% 红字告警」使用。
 */
import { getDb } from '../../db/index.js';
import { kv } from '../../core/kv.js';
import { logger } from '../../core/logger.js';
import { now } from '../../core/util.js';
import { runRoute, assertUsableResult, platformName } from './adapters/index.js';

const log = logger.child('dw:route');

const bucketOf = (date = new Date()) => `${date.toISOString().slice(0, 10)}${String(date.getUTCHours()).padStart(2, '0')}`;

/** 取该平台可用线路（含 all 兜底），按优先级排序 */
export async function selectRoutes(platform) {
  return getDb().query(
    'SELECT * FROM `dw_route` WHERE `enabled` = 1 AND (`platform` = ? OR `platform` = ?) ORDER BY `priority` ASC, `id` ASC',
    [platform || 'unknown', 'all'],
  );
}

/**
 * 依次尝试线路。返回 { result, route, attempts }
 * attempts 会写进任务备注/日志，排查「为什么这个链接解析不了」时非常有用。
 */
export async function resolveWithFallback({ platform, url, timeoutMs = 20000, onAttempt }) {
  const routes = await selectRoutes(platform);
  if (!routes.length) throw new Error(`平台 ${platformName(platform)} 暂无可用的解析线路`);

  const attempts = [];
  for (const route of routes) {
    const started = Date.now();
    try {
      const result = await runRoute(route, url, { timeoutMs });
      assertUsableResult(result);
      const ms = Date.now() - started;
      await recordRouteResult(route.id, true, ms);
      if (onAttempt) onAttempt({ route, ok: true, ms });
      log.info('解析命中线路', { platform, route: route.name, ms });
      return { result, route, attempts };
    } catch (err) {
      const ms = Date.now() - started;
      attempts.push({ route_id: route.id, name: route.name, ok: false, ms, error: String(err.message).slice(0, 200) });
      await recordRouteResult(route.id, false, ms, err.message);
      if (onAttempt) onAttempt({ route, ok: false, ms, error: err.message });
      log.warn('线路失败，尝试下一条', { route: route.name, err: err.message });
    }
  }
  const last = attempts[attempts.length - 1];
  const error = new Error(`全部解析线路均失败（最后一条：${last?.name || '-'}｜${last?.error || '未知错误'}）`);
  error.attempts = attempts;
  throw error;
}

/** 记录线路结果：累计值写库，1 小时窗口写 KV 小时桶 */
export async function recordRouteResult(routeId, ok, ms, errMsg) {
  const db = getDb();
  const bucket = bucketOf();
  try {
    await kv().incr(ok ? `route:succ:${routeId}:${bucket}` : `route:fail:${routeId}:${bucket}`, 3 * 3600);
  } catch {
    /* 统计失败不影响业务 */
  }
  try {
    const route = await db.get('SELECT `succ_total`, `fail_total`, `avg_ms` FROM `dw_route` WHERE `id` = ?', [routeId]);
    if (!route) return;
    const succTotal = Number(route.succ_total || 0) + (ok ? 1 : 0);
    const failTotal = Number(route.fail_total || 0) + (ok ? 0 : 1);
    const total = succTotal + failTotal;
    // 滑动平均：新均值 = (旧均值 * 旧次数 + 本次耗时) / 新次数
    const prevAvg = Number(route.avg_ms || 0);
    const avgMs = total > 0 ? Math.round((prevAvg * (total - 1) + Number(ms || 0)) / total) : 0;
    await db.update(
      'dw_route',
      { id: routeId },
      {
        succ_total: succTotal,
        fail_total: failTotal,
        avg_ms: avgMs,
        ...(ok ? { last_ok_at: now() } : { last_err: String(errMsg || '').slice(0, 255) }),
        updated_at: now(),
      },
    );
  } catch (err) {
    log.warn('线路统计写入失败', { routeId, err: err.message });
  }
}

/** cron:route_health —— 把小时桶汇总进 succ_1h / fail_1h，并返回需要告警的线路 */
export async function refreshRouteHealth() {
  const db = getDb();
  const routes = await db.query('SELECT `id`, `platform`, `name`, `succ_1h`, `fail_1h` FROM `dw_route`');
  const nowDate = new Date();
  const prev = new Date(nowDate.getTime() - 3600 * 1000);
  const buckets = [bucketOf(nowDate), bucketOf(prev)];
  const alerts = [];

  for (const route of routes) {
    let succ = 0;
    let fail = 0;
    for (const bucket of buckets) {
      succ += Number((await kv().get(`route:succ:${route.id}:${bucket}`)) || 0);
      fail += Number((await kv().get(`route:fail:${route.id}:${bucket}`)) || 0);
    }
    await db.update('dw_route', { id: route.id }, { succ_1h: succ, fail_1h: fail, updated_at: now() });
    const total = succ + fail;
    if (total >= 5) {
      const rate = succ / total;
      if (rate < (Number(process.env.ROUTE_SUCC_ALERT) || 0.8)) {
        alerts.push({ route_id: route.id, name: route.name, platform: route.platform, succ, fail, rate });
      }
    }
  }
  return alerts;
}

/** 后台「解析线路」页要用的健康度列表 */
export async function routeHealthList() {
  const rows = await getDb().query('SELECT * FROM `dw_route` ORDER BY `platform` ASC, `priority` ASC');
  return rows.map((r) => {
    const total = Number(r.succ_1h || 0) + Number(r.fail_1h || 0);
    const rate = total ? Number(r.succ_1h || 0) / total : null;
    return {
      ...r,
      config: safeParse(r.config),
      succ_rate_1h: rate === null ? null : Math.round(rate * 1000) / 10,
      healthy: rate === null ? null : rate >= 0.8,
    };
  });
}

function safeParse(text) {
  try {
    return text ? JSON.parse(text) : null;
  } catch {
    return text;
  }
}

export default { selectRoutes, resolveWithFallback, recordRouteResult, refreshRouteHealth, routeHealthList };
