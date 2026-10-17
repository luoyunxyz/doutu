/**
 * 告警：钉钉/企业微信机器人 webhook。
 * 规划书 10.2 的监控项全部走这里：解析成功率、队列积压、支付回调失败、进程存活、证书到期。
 *
 * 设计上「告警失败不能影响业务」：所有异常都吞掉并写日志。
 */
import config from '../config.js';
import { logger } from '../core/logger.js';
import { fetchWithTimeout } from '../core/util.js';

const log = logger.child('alert');

/** 同类告警静默窗口，避免解析线路挂掉时把机器人刷爆 */
const SILENCE_MS = 10 * 60 * 1000;
const lastSent = new Map();

export async function sendAlert(title, content, { level = 'warn', key = title } = {}) {
  log[level === 'error' ? 'error' : 'warn'](`[告警] ${title}`, { content });

  if (!config.alert.webhook) return { sent: false, reason: '未配置 ALERT_WEBHOOK' };

  const last = lastSent.get(key) || 0;
  if (Date.now() - last < SILENCE_MS) return { sent: false, reason: '静默窗口内' };

  const text = `【${title}】\n${content}\n时间：${new Date().toLocaleString('zh-CN')}`;
  const payload = { msgtype: 'text', text: { content: text } };

  try {
    const res = await fetchWithTimeout(
      config.alert.webhook,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      },
      5000,
    );
    lastSent.set(key, Date.now());
    return { sent: true, status: res.status };
  } catch (err) {
    log.error('告警发送失败', { err: err.message });
    return { sent: false, reason: err.message };
  }
}

export default { sendAlert };
