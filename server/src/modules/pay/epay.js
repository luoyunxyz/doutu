/**
 * 易支付 / 彩虹易支付 聚合支付适配（规划书 12 章决策点 3）。
 *
 * 签名算法（易支付通用约定）：
 *   1. 取所有非空参数（去掉 sign、sign_type）；
 *   2. 按参数名 ASCII 升序排序；
 *   3. 拼成 a=1&b=2 形式，末尾直接追加商户密钥 KEY（不加 &）；
 *   4. md5 后转小写。
 *
 * ⚠️ 回调是最容易被刷单的入口，因此本模块只做「验签 + 取字段」，
 *    金额比对、幂等、订单状态判断全部在 services/order.js 的 markPaid 里做，
 *    并且回调之后一定要调用 queryOrder 主动反查一次（规划书 8.2 的硬性要求）。
 */
import crypto from 'node:crypto';
import config from '../../config.js';
import { logger } from '../../core/logger.js';
import { fetchWithTimeout } from '../../core/util.js';
import { money } from '../../core/money.js';

const log = logger.child('pay:epay');

export const md5 = (text) => crypto.createHash('md5').update(String(text)).digest('hex');

/** 易支付签名 */
export function epaySign(params, key = config.pay.epay.key) {
  const entries = Object.entries(params)
    .filter(([k, v]) => k !== 'sign' && k !== 'sign_type' && v !== '' && v !== null && v !== undefined)
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  const query = entries.map(([k, v]) => `${k}=${v}`).join('&');
  return md5(query + key);
}

export function verifyEpaySign(params, key = config.pay.epay.key) {
  const sign = String(params.sign || '').toLowerCase();
  if (!sign) return false;
  const expected = epaySign(params, key);
  return timingSafeEqual(expected, sign);
}

function timingSafeEqual(a, b) {
  const bufA = Buffer.from(String(a));
  const bufB = Buffer.from(String(b));
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

/** 生成收银台跳转地址（页面跳转型，客户端 WebView 打开即可） */
export function buildPayUrl({ orderNo, payType = 'alipay', amount, goodsName, clientIp, notifyBase, returnUrl = '' }) {
  const { gateway, pid } = config.pay.epay;
  if (!gateway || !pid) throw new Error('未配置 EPAY_GATEWAY / EPAY_PID');
  const base = (notifyBase || config.pay.epay.notifyBase || '').replace(/\/+$/, '');
  const params = {
    pid,
    type: payType,
    out_trade_no: orderNo,
    notify_url: `${base}/api/v1/pay/notify/epay`,
    return_url: returnUrl,
    name: String(goodsName || '会员服务').slice(0, 60),
    money: money(amount),
    clientip: clientIp || '',
  };
  params.sign = epaySign(params);
  params.sign_type = 'MD5';
  const query = Object.entries(params)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
  return `${gateway}/submit.php?${query}`;
}

/** 解析回调参数（GET query 或 POST form/json 都兼容） */
export function parseNotify(ctx) {
  const source = ctx.method === 'GET' ? ctx.query : { ...ctx.query, ...(ctx.body || {}) };
  return {
    pid: String(source.pid || ''),
    tradeNo: String(source.trade_no || ''),
    orderNo: String(source.out_trade_no || ''),
    payType: String(source.type || ''),
    name: String(source.name || ''),
    amount: String(source.money || ''),
    tradeStatus: String(source.trade_status || ''),
    sign: String(source.sign || ''),
    raw: source,
  };
}

/**
 * 主动反查（规划书 8.2）：回调之后向网关再查一次，
 * 只信任服务端主动查询的结果，防止伪造回调刷单。
 */
export async function queryOrder(orderNo) {
  const { gateway, pid, key } = config.pay.epay;
  if (!gateway || !pid || !key) return { ok: false, reason: '未配置网关参数' };
  try {
    const url = `${gateway}/api.php?act=order&pid=${encodeURIComponent(pid)}&key=${encodeURIComponent(key)}&out_trade_no=${encodeURIComponent(orderNo)}`;
    const res = await fetchWithTimeout(url, { headers: { Accept: 'application/json' } }, 8000);
    const text = await res.text();
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      return { ok: false, reason: `网关返回非 JSON：${text.slice(0, 120)}` };
    }
    // 常见返回：{ code: 1, msg: '查询成功', data: { trade_no, status: 1, money, ... } }
    const status = Number(json?.data?.status ?? json?.status ?? 0);
    return {
      ok: status === 1,
      paid: status === 1,
      tradeNo: json?.data?.trade_no || json?.trade_no || null,
      amount: json?.data?.money || json?.money || null,
      raw: json,
    };
  } catch (err) {
    log.warn('支付反查失败', { orderNo, err: err.message });
    return { ok: false, reason: err.message };
  }
}

/** 对账：拉取某天的网关账单（不同网关接口不一，缺配置时返回空并告警） */
export async function fetchDailyBill(date) {
  const { gateway, pid, key } = config.pay.epay;
  if (!gateway || !pid || !key) return { ok: false, rows: [], reason: '未配置网关参数' };
  try {
    const url = `${gateway}/api.php?act=orders&pid=${encodeURIComponent(pid)}&key=${encodeURIComponent(key)}&date=${encodeURIComponent(date)}`;
    const res = await fetchWithTimeout(url, { headers: { Accept: 'application/json' } }, 15000);
    const json = await res.json();
    const list = json?.data?.list || json?.data || json?.list || [];
    const rows = Array.isArray(list) ? list : [];
    return { ok: true, rows };
  } catch (err) {
    return { ok: false, rows: [], reason: err.message };
  }
}

export default {
  md5,
  epaySign,
  verifyEpaySign,
  buildPayUrl,
  parseNotify,
  queryOrder,
  fetchDailyBill,
};
