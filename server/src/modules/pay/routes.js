/**
 * 支付接口（规划书 8.2 支付类）。
 *
 * ⚠️ /pay/notify/* 是唯一不走客户端签名的入口，防刷单规则在这里落地：
 *   ① 渠道验签（易支付 MD5）；
 *   ② 金额必须与订单一致（在 order.markPaid 里比对）；
 *   ③ 同一 trade_no 只入账一次（uk_trade_no + 幂等判断）；
 *   ④ 回调后主动反查网关（queryOrder），只信任反查结果；
 *   ⑤ 回调失败立即告警（涉及真金白银）。
 */
import { Router } from '../../core/http.js';
import config from '../../config.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { clientAuth } from '../../middleware/clientAuth.js';
import { logger } from '../../core/logger.js';
import { getDb } from '../../db/index.js';
import { formatDateTime, addMinutes, now } from '../../core/util.js';
import { money } from '../../core/money.js';
import * as orderService from '../../services/order.js';
import * as userService from '../../services/user.js';
import { getConfig, getInt, getJson } from '../../services/sysconfig.js';
import { sendAlert } from '../../services/alert.js';
import * as epay from './epay.js';

const log = logger.child('pay:routes');

export const payRouter = new Router({ prefix: '/api/v1/pay' });

/** GET /api/v1/pay/goods —— 商品列表（套餐） */
payRouter.get('/goods', clientAuth({ mode: 'none', requireActive: false, rate: false, skipVersionCheck: true }), async (ctx) => {
  const rows = await getDb().query('SELECT * FROM `goods` WHERE `status` = 1 ORDER BY `sort` ASC, `id` ASC');
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      goods: rows.map((g) => ({
        id: g.id,
        name: g.name,
        duration: Number(g.duration),
        duration_text: Number(g.duration) === 0 ? '永久' : `${g.duration} 天`,
        level: Number(g.level),
        max_device: Number(g.max_device),
        quota_day: Number(g.quota_day),
        price: money(g.price),
        origin_price: money(g.origin_price),
        tag: g.tag || '',
      })),
      pay_channels: await getJson('pay_channels', ['alipay', 'wxpay']),
    },
  });
});

/**
 * POST /api/v1/pay/create —— 创建支付订单
 * 请求：{ goods_id, pay_type }
 */
payRouter.post(
  '/create',
  clientAuth({ mode: 'session', requireActive: false, rate: { bucket: 'pay', ipLimit: 30, userLimit: 15, windowSec: 60 } }),
  async (ctx) => {
    const goodsId = ctx.require('goods_id');
    const payType = String(ctx.input('pay_type', 'alipay'));
    const channels = await getJson('pay_channels', ['alipay', 'wxpay']);
    if (Array.isArray(channels) && channels.length && !channels.includes(payType)) {
      throw new ApiError(ERROR.PARAM, `不支持的支付方式：${payType}`);
    }

    const { order, expire_at } = await orderService.createOrder({
      userId: ctx.state.user.id,
      goodsId,
      type: 2,
      payType,
      clientIp: ctx.ip,
    });

    const payUrl =
      config.pay.provider === 'epay'
        ? epay.buildPayUrl({
            orderNo: order.order_no,
            payType,
            amount: order.amount,
            goodsName: order.goods_name,
            clientIp: ctx.ip,
          })
        : // mock 渠道：本地自测用，点开即完成支付（见 /api/v1/pay/mock/pay）
          `${config.storage.publicBase}/api/v1/pay/mock/pay?order_no=${encodeURIComponent(order.order_no)}`;

    ctx.json({
      code: ERROR.OK,
      msg: 'ok',
      data: {
        order_no: order.order_no,
        amount: money(order.amount),
        goods_name: order.goods_name,
        pay_type: payType,
        pay_url: payUrl,
        expire_at,
        expire_sec: (await getInt('pay_order_expire_min', 30)) * 60,
      },
    });
  },
);

/** GET /api/v1/pay/status/{order_no} —— 前端轮询支付状态 */
payRouter.get('/status/:order_no', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  const order = await orderService.findOrder(ctx.params.order_no);
  if (!order || (order.user_id && Number(order.user_id) !== Number(ctx.state.user.id))) {
    throw new ApiError(ERROR.NOT_FOUND, '订单不存在');
  }
  const user = order.user_id ? await userService.findById(order.user_id) : null;
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      order_no: order.order_no,
      status: Number(order.status),
      status_text: orderService.ORDER_STATUS_TEXT[Number(order.status)],
      amount: money(order.amount),
      paid_at: order.paid_at,
      expire_at: formatDateTime(addMinutes(new Date(order.created_at), await getInt('pay_order_expire_min', 30))),
      user: user ? userService.authStatusPayload(user) : null,
    },
  });
});

/**
 * 支付回调。注意：**不挂 clientAuth**（渠道不会带我们的签名头）。
 */
payRouter.all('/notify/:channel', async (ctx) => {
  const channel = String(ctx.params.channel || '');
  if (config.pay.provider === 'epay' && channel !== 'epay') {
    throw new ApiError(ERROR.NOT_FOUND, '渠道不存在');
  }

  if (channel === 'mock') {
    return handleMockNotify(ctx);
  }

  const parsed = epay.parseNotify(ctx);
  const db = getDb();

  // ① 验签
  if (!epay.verifyEpaySign(parsed.raw)) {
    await db.insert('payment_record', {
      order_no: parsed.orderNo || null,
      trade_no: parsed.tradeNo || null,
      channel,
      amount: money(parsed.amount || 0),
      status: 2,
      raw: JSON.stringify(parsed.raw).slice(0, 60000),
      msg: '回调验签失败',
      ip: ctx.ip,
      created_at: now(),
    });
    log.error('支付回调验签失败', { orderNo: parsed.orderNo, ip: ctx.ip });
    await sendAlert('支付回调验签失败', `订单 ${parsed.orderNo} 回调验签失败（IP ${ctx.ip}），请立即核查`, { level: 'error' });
    ctx.res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    ctx.sent = true;
    ctx.res.end('fail');
    return;
  }

  // ② 交易状态
  if (parsed.tradeStatus && parsed.tradeStatus !== 'TRADE_SUCCESS') {
    ctx.res.writeHead(200, { 'Content-Type': 'text/plain' });
    ctx.sent = true;
    ctx.res.end('success'); // 非成功状态直接确认，避免网关重推
    return;
  }

  // ③ 主动反查，只信任查询结果
  const queried = await epay.queryOrder(parsed.orderNo);
  if (!queried.ok && !queried.paid) {
    log.warn('支付反查未确认，按回调金额入账但标记待核对', { orderNo: parsed.orderNo, reason: queried.reason });
    await sendAlert('支付回调反查失败', `订单 ${parsed.orderNo} 回调已收到但反查网关失败：${queried.reason || '未知'}`, {
      key: 'pay-query-fail',
    });
  }

  // ④ 入账（金额比对 + 幂等在里面）
  try {
    const result = await orderService.markPaid({
      orderNo: parsed.orderNo,
      tradeNo: queried.tradeNo || parsed.tradeNo,
      channel,
      amount: parsed.amount,
      raw: JSON.stringify(parsed.raw).slice(0, 60000),
      ip: ctx.ip,
      payType: parsed.payType,
    });
    log.info('支付回调处理完成', { orderNo: parsed.orderNo, already: result.already });
  } catch (err) {
    log.error('支付回调入账失败', { orderNo: parsed.orderNo, err: err.message });
    await sendAlert('支付回调入账失败', `订单 ${parsed.orderNo}：${err.message}`, { level: 'error', key: 'pay-fail' });
    ctx.res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
    ctx.sent = true;
    ctx.res.end('fail');
    return;
  }

  ctx.res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
  ctx.sent = true;
  ctx.res.end('success');
});

/** mock 渠道：仅 PAY_PROVIDER=mock 且来自本机时可用，用于本地联调/自检 */
async function handleMockNotify(ctx) {
  if (config.pay.provider !== 'mock') throw new ApiError(ERROR.NOT_FOUND, '渠道不存在');
  const isLocal = ['127.0.0.1', '::1', 'localhost'].includes(String(ctx.ip).replace(/^::ffff:/, ''));
  if (!isLocal && config.isProd) throw new ApiError(ERROR.NOT_FOUND, '渠道不存在');

  const orderNo = String(ctx.input('order_no', '') || ctx.input('out_trade_no', ''));
  if (!orderNo) throw new ApiError(ERROR.PARAM, '缺少订单号');
  const order = await orderService.findOrder(orderNo);
  if (!order) throw new ApiError(ERROR.NOT_FOUND, '订单不存在');

  const result = await orderService.markPaid({
    orderNo,
    tradeNo: `MOCK${Date.now()}`,
    channel: 'mock',
    amount: order.amount,
    raw: JSON.stringify({ mock: true, order_no: orderNo }),
    ip: ctx.ip,
    payType: order.pay_type,
  });
  log.warn('mock 渠道完成支付（仅本地联调）', { orderNo, already: result.already });
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { order_no: orderNo, already: result.already } });
}

/** mock 收银台页面：本地自测点一下即完成支付 */
payRouter.get('/mock/pay', async (ctx) => {
  if (config.pay.provider !== 'mock') throw new ApiError(ERROR.NOT_FOUND, '页面不存在');
  const orderNo = String(ctx.query.order_no || '');
  const order = orderNo ? await orderService.findOrder(orderNo) : null;
  if (!order) throw new ApiError(ERROR.NOT_FOUND, '订单不存在');
  const html = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>模拟收银台</title>
<style>body{font-family:-apple-system,"Microsoft YaHei",sans-serif;background:#f4f5f7;margin:0;padding:40px 20px;text-align:center}
.card{background:#fff;border-radius:12px;max-width:420px;margin:0 auto;padding:24px;box-shadow:0 2px 12px rgba(0,0,0,.06)}
h2{margin:8px 0 4px}.amt{font-size:34px;color:#1677ff;margin:16px 0}
button{width:100%;padding:14px;border:0;border-radius:8px;background:#1677ff;color:#fff;font-size:16px}
small{color:#888;display:block;margin-top:14px;line-height:1.6}</style></head><body>
<div class="card"><div>${order.goods_name || '会员服务'}</div><h2>¥${money(order.amount)}</h2>
<div class="amt">订单号 ${order.order_no}</div>
<button onclick="pay()">模拟支付成功</button>
<small>这是本地联调用的模拟收银台，PAY_PROVIDER=epay 时不会出现此页面。</small></div>
<script>
async function pay(){
  const r = await fetch('/api/v1/pay/notify/mock',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({order_no:'${order.order_no}'})});
  const j = await r.json();
  document.querySelector('button').outerHTML = j.code===0 ? '<b style="color:#00a870">支付成功，可返回 App</b>' : '<b style="color:#d93026">失败：'+j.msg+'</b>';
}
</script></body></html>`;
  ctx.sendRaw(Buffer.from(html, 'utf8'), { contentType: 'text/html; charset=utf-8' });
});

/**
 * 对账：拉取网关账单与本地订单比对（规划书 6.2「强烈建议做」）。
 * 差异写 reconcile_log 并告警。
 */
export async function reconcile(dateStr) {
  const db = getDb();
  const day = dateStr || new Date().toISOString().slice(0, 10);
  const local = await db.query(
    'SELECT `order_no`, `trade_no`, `amount` FROM `order` WHERE `status` = 1 AND `paid_at` >= ? AND `paid_at` <= ?',
    [`${day} 00:00:00`, `${day} 23:59:59`],
  );

  const bill = await epay.fetchDailyBill(day);
  const localAmount = local.reduce((sum, r) => sum + Math.round(Number(r.amount) * 100), 0);
  const localMap = new Map(local.map((r) => [r.order_no, r]));

  let platformRows = [];
  let platformAmount = 0;
  let diffCount = 0;
  const details = [];

  if (bill.ok) {
    platformRows = bill.rows;
    for (const row of platformRows) {
      platformAmount += Math.round(Number(row.money || row.amount || 0) * 100);
      const orderNo = String(row.out_trade_no || row.order_no || '');
      if (orderNo && !localMap.has(orderNo)) {
        diffCount += 1;
        details.push({ type: 'platform_only', order_no: orderNo, amount: row.money || row.amount });
      }
    }
    for (const row of local) {
      const matched = platformRows.some((p) => String(p.out_trade_no || p.order_no || '') === row.order_no);
      if (!matched) {
        diffCount += 1;
        details.push({ type: 'local_only', order_no: row.order_no, amount: row.amount });
      }
    }
  } else {
    details.push({ type: 'fetch_failed', reason: bill.reason });
  }

  const diffAmount = Math.abs(localAmount - platformAmount) / 100;
  const status = !bill.ok ? 2 : diffCount ? 1 : 0;

  await db.insert('reconcile_log', {
    stat_date: day,
    channel: config.pay.provider,
    local_count: local.length,
    local_amount: money(localAmount / 100),
    platform_count: platformRows.length,
    platform_amount: money(platformAmount / 100),
    diff_count: diffCount,
    diff_amount: money(diffAmount),
    detail: JSON.stringify(details).slice(0, 60000),
    status,
    created_at: now(),
  });

  if (status !== 0) {
    await sendAlert(
      '支付对账存在差异',
      `${day} 对账：本地 ${local.length} 笔 / 网关 ${platformRows.length} 笔，差异 ${diffCount} 笔，金额差 ${money(diffAmount)} 元。详情见 reconcile_log`,
      { level: 'error', key: 'reconcile' },
    );
  }
  return { day, local: local.length, platform: platformRows.length, diffCount, status };
}

export default payRouter;
