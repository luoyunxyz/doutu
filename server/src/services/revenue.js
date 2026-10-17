/**
 * 收益统计（规划书 6.2）。
 *
 * 口径：
 *   日/月/季/年为「预聚合表 revenue_daily」汇总 —— 毫秒级；
 *   当日 24 小时直接查 order 实时聚合 —— 预聚合按天，小时级只能现算。
 * 金额全程走整数分运算（core/money.js），不出现浮点。
 */
import { getDb } from '../db/index.js';
import { now, formatDate, parseDateTime } from '../core/util.js';
import { money, addMoney, subMoney, toCents } from '../core/money.js';
import { logger } from '../core/logger.js';

const log = logger.child('revenue');

/** 把某一天的订单聚合成一行 revenue_daily（幂等，可补算历史） */
export async function aggregateDay(dateStr) {
  const db = getDb();
  const day = String(dateStr).slice(0, 10);
  const from = `${day} 00:00:00`;
  const to = `${day} 23:59:59`;

  const paid = await db.query(
    'SELECT `user_id`, `amount`, `fee`, `net_amount` FROM `order` WHERE `status` = 1 AND `paid_at` >= ? AND `paid_at` <= ?',
    [from, to],
  );
  const refunded = await db.query(
    'SELECT `amount` FROM `order` WHERE `status` = 3 AND `updated_at` >= ? AND `updated_at` <= ?',
    [from, to],
  );

  const gross = addMoney(...paid.map((r) => money(r.amount)));
  const fee = addMoney(...paid.map((r) => money(r.fee)));
  const net = subMoney(gross, fee);
  const refund = addMoney(...refunded.map((r) => money(r.amount)));
  const users = new Set(paid.map((r) => r.user_id).filter(Boolean));

  const row = {
    stat_date: day,
    order_count: paid.length,
    user_count: users.size,
    gross,
    fee,
    net,
    refund,
    refund_count: refunded.length,
    updated_at: now(),
  };
  await db.upsert('revenue_daily', ['stat_date'], row, [
    'order_count',
    'user_count',
    'gross',
    'fee',
    'net',
    'refund',
    'refund_count',
    'updated_at',
  ]);
  return row;
}

/** 补算最近 n 天（对账可能延迟到账，所以聚合任务要能补历史） */
export async function aggregateRange(days = 3, endDate = new Date()) {
  const out = [];
  for (let i = 0; i < days; i += 1) {
    const d = new Date(endDate.getTime() - i * 86400000);
    out.push(await aggregateDay(formatDate(d)));
  }
  log.info('收益日聚合完成', { days, rows: out.length });
  return out;
}

/** 日序列（用于当月 / 当周 / 近 30 天） */
export async function dailySeries(from, to) {
  const db = getDb();
  const rows = await db.query(
    'SELECT * FROM `revenue_daily` WHERE `stat_date` >= ? AND `stat_date` <= ? ORDER BY `stat_date` ASC',
    [String(from).slice(0, 10), String(to).slice(0, 10)],
  );
  return rows.map(normalizeDaily);
}

function normalizeDaily(row) {
  return {
    date: String(row.stat_date).slice(0, 10),
    order_count: Number(row.order_count || 0),
    user_count: Number(row.user_count || 0),
    gross: money(row.gross),
    fee: money(row.fee),
    net: money(row.net),
    refund: money(row.refund),
    refund_count: Number(row.refund_count || 0),
  };
}

/** 逐日序列（把缺的日期补 0，前端图表才不会断） */
export async function filledDailySeries(from, to) {
  const rows = await dailySeries(from, to);
  const map = new Map(rows.map((r) => [r.date, r]));
  const out = [];
  const start = parseDateTime(`${String(from).slice(0, 10)} 00:00:00`);
  const end = parseDateTime(`${String(to).slice(0, 10)} 00:00:00`);
  for (let d = new Date(start); d.getTime() <= end.getTime(); d.setDate(d.getDate() + 1)) {
    const key = formatDate(d);
    out.push(
      map.get(key) || {
        date: key,
        order_count: 0,
        user_count: 0,
        gross: '0.00',
        fee: '0.00',
        net: '0.00',
        refund: '0.00',
        refund_count: 0,
      },
    );
  }
  return out;
}

/** 月序列（当年近 12 个月） */
export async function monthlySeries(months = 12) {
  const rows = await dailySeries(
    formatDate(new Date(Date.now() - months * 31 * 86400000)).slice(0, 8) + '01',
    formatDate(),
  );
  const map = new Map();
  for (const row of rows) {
    const key = row.date.slice(0, 7);
    const acc = map.get(key) || { label: key, order_count: 0, user_count: 0, gross: '0.00', fee: '0.00', net: '0.00', refund: '0.00' };
    acc.order_count += row.order_count;
    acc.user_count += row.user_count;
    acc.gross = addMoney(acc.gross, row.gross);
    acc.fee = addMoney(acc.fee, row.fee);
    acc.net = addMoney(acc.net, row.net);
    acc.refund = addMoney(acc.refund, row.refund);
    map.set(key, acc);
  }
  return [...map.values()].sort((a, b) => a.label.localeCompare(b.label));
}

/** 按小时实时聚合（当日） */
export async function hourlySeries(date = formatDate()) {
  const db = getDb();
  const day = String(date).slice(0, 10);
  const rows = await db.query(
    'SELECT `paid_at`, `amount`, `fee` FROM `order` WHERE `status` = 1 AND `paid_at` >= ? AND `paid_at` <= ?',
    [`${day} 00:00:00`, `${day} 23:59:59`],
  );
  const buckets = Array.from({ length: 24 }, (_, h) => ({
    label: `${String(h).padStart(2, '0')}:00`,
    order_count: 0,
    user_count: 0,
    gross: '0.00',
    fee: '0.00',
    net: '0.00',
  }));
  const userSet = Array.from({ length: 24 }, () => new Set());
  for (const row of rows) {
    const d = parseDateTime(row.paid_at);
    if (!d) continue;
    const h = d.getHours();
    buckets[h].order_count += 1;
    buckets[h].gross = addMoney(buckets[h].gross, money(row.amount));
    buckets[h].fee = addMoney(buckets[h].fee, money(row.fee));
    buckets[h].net = subMoney(buckets[h].gross, buckets[h].fee);
  }
  return buckets.map((b, i) => ({ ...b, user_count: userSet[i].size }));
}

/** 环比增长率（%），保留两位；上期为 0 时返回 null 表示「无对比基准」 */
export function growthRate(current, previous) {
  const c = toCents(current);
  const p = toCents(previous);
  if (p === 0) return c === 0 ? 0 : null;
  return Math.round(((c - p) / Math.abs(p)) * 10000) / 100;
}

function sumRows(rows) {
  const gross = addMoney(...rows.map((r) => r.gross));
  const fee = addMoney(...rows.map((r) => r.fee));
  const net = addMoney(...rows.map((r) => r.net));
  const refund = addMoney(...rows.map((r) => r.refund));
  return {
    order_count: rows.reduce((n, r) => n + Number(r.order_count || 0), 0),
    user_count: rows.reduce((n, r) => n + Number(r.user_count || 0), 0),
    gross,
    fee,
    net,
    refund,
  };
}

/**
 * 统一取数入口：后台「当年 / 当季 / 当月 / 当周 / 当日」五档共用一个图表 + 切换 Tab。
 * 返回 { granularity, range, series, cards }
 */
export async function overview(granularity = 'month', options = {}) {
  const today = new Date();
  const db = getDb();

  if (granularity === 'hour') {
    const date = options.date || formatDate(today);
    const series = await hourlySeries(date);
    const cards = sumRows(series);
    // 环比：与昨天同一时刻之前的累计对比
    const yesterday = formatDate(new Date(parseDateTime(`${date} 00:00:00`).getTime() - 86400000));
    const { rows: prevRows } = { rows: await dailySeries(yesterday, yesterday) };
    const prev = prevRows[0] || { gross: '0.00', net: '0.00', order_count: 0, user_count: 0 };
    return {
      granularity,
      range: { date },
      series: series.map((s) => ({ label: s.label, ...s })),
      cards: { ...cards, growth: growthRate(cards.gross, prev.gross), prev_gross: money(prev.gross) },
    };
  }

  if (granularity === 'week') {
    const startOfWeek = new Date(today);
    const day = (today.getDay() + 6) % 7; // 周一为一周之始
    startOfWeek.setDate(today.getDate() - day);
    const from = formatDate(startOfWeek);
    const to = formatDate(today);
    const series = await filledDailySeries(from, to);
    const cards = sumRows(series);
    const prevFrom = formatDate(new Date(startOfWeek.getTime() - 7 * 86400000));
    const prevTo = formatDate(new Date(startOfWeek.getTime() - 86400000));
    const prev = sumRows(await dailySeries(prevFrom, prevTo));
    return {
      granularity,
      range: { from, to },
      series: series.map((s) => ({ label: s.date.slice(5), ...s })),
      cards: { ...cards, growth: growthRate(cards.gross, prev.gross), prev_gross: money(prev.gross) },
    };
  }

  if (granularity === 'quarter' || granularity === 'year') {
    const months = granularity === 'year' ? 12 : 3;
    const series = await monthlySeries(months);
    const cards = sumRows(series);
    const prevMonths = series.length ? await monthlySeriesRange(months) : [];
    const prev = sumRows(prevMonths);
    return {
      granularity,
      range: { months },
      series: series.map((s) => ({ label: s.label, ...s })),
      cards: { ...cards, growth: growthRate(cards.gross, prev.gross), prev_gross: money(prev.gross) },
    };
  }

  // 默认 month：当月每日
  const from = formatDate(new Date(today.getFullYear(), today.getMonth(), 1));
  const to = formatDate(today);
  const series = await filledDailySeries(from, to);
  const cards = sumRows(series);
  const prevFrom = formatDate(new Date(today.getFullYear(), today.getMonth() - 1, 1));
  const prevTo = formatDate(new Date(today.getFullYear(), today.getMonth(), 0));
  const prev = sumRows(await dailySeries(prevFrom, prevTo));
  void db;
  return {
    granularity: 'month',
    range: { from, to },
    series: series.map((s) => ({ label: s.date.slice(8), ...s })),
    cards: { ...cards, growth: growthRate(cards.gross, prev.gross), prev_gross: money(prev.gross) },
  };
}

/** 上一个同长度区间的月度汇总（环比基准） */
async function monthlySeriesRange(months) {
  const today = new Date();
  const end = new Date(today.getFullYear(), today.getMonth() - months, 1);
  const start = new Date(today.getFullYear(), today.getMonth() - months * 2, 1);
  const db = getDb();
  const rows = await db.query(
    'SELECT * FROM `revenue_daily` WHERE `stat_date` >= ? AND `stat_date` <= ? ORDER BY `stat_date` ASC',
    [formatDate(start), formatDate(new Date(end.getTime() + 31 * 86400000))],
  );
  const map = new Map();
  for (const row of rows.map(normalizeDaily)) {
    const key = row.date.slice(0, 7);
    const acc = map.get(key) || { label: key, order_count: 0, user_count: 0, gross: '0.00', fee: '0.00', net: '0.00', refund: '0.00' };
    acc.order_count += row.order_count;
    acc.user_count += row.user_count;
    acc.gross = addMoney(acc.gross, row.gross);
    acc.fee = addMoney(acc.fee, row.fee);
    acc.net = addMoney(acc.net, row.net);
    acc.refund = addMoney(acc.refund, row.refund);
    map.set(key, acc);
  }
  return [...map.values()].sort((a, b) => a.label.localeCompare(b.label));
}

/** 后台首页概览：今日/累计/用户数/任务数 */
export async function dashboard() {
  const db = getDb();
  const day = formatDate();
  const [todayRow, totalRow, userRow, taskRow, routeRow] = await Promise.all([
    db.get('SELECT * FROM `revenue_daily` WHERE `stat_date` = ?', [day]),
    db.get(
      'SELECT COUNT(*) AS n, SUM(`amount`) AS gross, SUM(`net_amount`) AS net FROM `order` WHERE `status` = 1',
    ),
    db.get("SELECT COUNT(*) AS n FROM `app_user` WHERE `status` = 1"),
    db.get(
      'SELECT COUNT(*) AS n, SUM(CASE WHEN `status` = 3 THEN 1 ELSE 0 END) AS ok FROM `dw_task` WHERE `created_at` >= ? AND `created_at` <= ?',
      [`${day} 00:00:00`, `${day} 23:59:59`],
    ),
    db.query('SELECT `platform`, `succ_1h`, `fail_1h` FROM `dw_route` WHERE `enabled` = 1'),
  ]);
  const taskCount = Number(taskRow?.n || 0);
  const okCount = Number(taskRow?.ok || 0);
  return {
    date: day,
    today: {
      order_count: Number(todayRow?.order_count || 0),
      user_count: Number(todayRow?.user_count || 0),
      gross: money(todayRow?.gross || 0),
      net: money(todayRow?.net || 0),
    },
    total: {
      order_count: Number(totalRow?.n || 0),
      gross: money(totalRow?.gross || 0),
      net: money(totalRow?.net || 0),
    },
    active_users: Number(userRow?.n || 0),
    tasks: {
      count: taskCount,
      success: okCount,
      success_rate: taskCount ? Math.round((okCount / taskCount) * 1000) / 10 : null,
    },
    routes: routeRow.map((r) => ({
      platform: r.platform,
      succ: Number(r.succ_1h),
      fail: Number(r.fail_1h),
      rate: Number(r.succ_1h) + Number(r.fail_1h)
        ? Math.round((Number(r.succ_1h) / (Number(r.succ_1h) + Number(r.fail_1h))) * 1000) / 10
        : null,
    })),
  };
}

export default {
  aggregateDay,
  aggregateRange,
  dailySeries,
  filledDailySeries,
  monthlySeries,
  hourlySeries,
  growthRate,
  overview,
  dashboard,
};
