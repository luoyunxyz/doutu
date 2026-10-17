/**
 * 客服工单（规划书 6.3）。
 *
 * 首期自建工单：不做长连接，用户提交后轮询详情即可。
 * 关键体验点：建单时自动带上设备、版本与最近 5 条任务记录 ——
 * 客服不用再问「你手机什么型号、解析的哪个链接」。
 */
import { Router } from '../../core/http.js';
import { ApiError, ERROR } from '../../core/errors.js';
import { clientAuth, headerDeviceId } from '../../middleware/clientAuth.js';
import { getDb } from '../../db/index.js';
import { now, businessNo, pageParams } from '../../core/util.js';
import { getInt } from '../../services/sysconfig.js';
import { addHours, formatDateTime } from '../../core/util.js';
import { logger } from '../../core/logger.js';

const log = logger.child('ticket');

export const ticketRouter = new Router({ prefix: '/api/v1/ticket' });

/** 工单里带上的环境上下文 */
async function buildContext(ctx) {
  const tasks = await getDb().query(
    'SELECT `task_no`, `platform`, `status`, `err_msg`, `created_at` FROM `dw_task` WHERE `user_id` = ? ORDER BY `id` DESC LIMIT 5',
    [ctx.state.user.id],
  );
  return {
    device_id: headerDeviceId(ctx) || ctx.state.deviceId,
    app_version: ctx.state.appVersion,
    os_version: ctx.input('os_version', null),
    model: ctx.input('model', null),
    user_level: Number(ctx.state.user.level),
    expire_at: ctx.state.user.expire_at,
    recent_tasks: tasks,
  };
}

/** POST /api/v1/ticket/create */
ticketRouter.post(
  '/create',
  clientAuth({ mode: 'session', requireActive: false, rate: { bucket: 'ticket', ipLimit: 20, userLimit: 10, windowSec: 3600 } }),
  async (ctx) => {
    const title = String(ctx.input('title', '')).trim();
    const content = String(ctx.input('content', '')).trim();
    if (!title || title.length > 100) throw new ApiError(ERROR.PARAM, '请填写 1~100 字的标题');
    if (!content) throw new ApiError(ERROR.PARAM, '请描述您的问题');

    const db = getDb();
    const pending = await db.count('ticket', { user_id: ctx.state.user.id, status: 0 });
    if (pending >= 3) throw new ApiError(ERROR.RATE_LIMIT, '您有 3 个待处理工单，请先等待客服回复');

    const slaHours = await getInt('ticket_sla_hours', 24);
    const ts = now();
    const created = await db.insert('ticket', {
      ticket_no: businessNo('TK'),
      user_id: ctx.state.user.id,
      title: title.slice(0, 128),
      category: Number(ctx.input('category', 4)) || 4,
      priority: Number(ctx.input('priority', 2)) || 2,
      status: 0,
      admin_id: null,
      message_count: 1,
      last_reply_at: ts,
      last_reply_by: 1,
      sla_at: formatDateTime(addHours(new Date(), slaHours)),
      closed_at: null,
      context: JSON.stringify(await buildContext(ctx)),
      created_at: ts,
      updated_at: ts,
    });

    await db.insert('ticket_message', {
      ticket_id: created.insertId,
      sender_type: 1,
      sender_id: ctx.state.user.id,
      content,
      attachments: JSON.stringify(Array.isArray(ctx.input('attachments', [])) ? ctx.input('attachments', []) : []),
      created_at: ts,
    });

    const ticket = await db.get('SELECT * FROM `ticket` WHERE `id` = ?', [created.insertId]);
    log.info('新工单', { ticketNo: ticket.ticket_no, userId: ctx.state.user.id, category: ticket.category });
    ctx.json({ code: ERROR.OK, msg: '已提交，客服会尽快处理', data: { ticket: sanitize(ticket) } });
  },
);

/** GET /api/v1/ticket/list */
ticketRouter.get('/list', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  const { page, limit } = pageParams(ctx.query, 10, 50);
  const db = getDb();
  const status = ctx.query.status;
  const where = ['`user_id` = ?'];
  const params = [ctx.state.user.id];
  if (status !== undefined && status !== '') {
    where.push('`status` = ?');
    params.push(Number(status));
  }
  const clause = where.join(' AND ');
  const total = Number((await db.get(`SELECT COUNT(*) AS n FROM \`ticket\` WHERE ${clause}`, params))?.n || 0);
  const rows = await db.query(
    `SELECT * FROM \`ticket\` WHERE ${clause} ORDER BY \`id\` DESC LIMIT ? OFFSET ?`,
    [...params, limit, (page - 1) * limit],
  );
  ctx.json({ code: ERROR.OK, msg: 'ok', data: { total, rows: rows.map(sanitize), page, limit } });
});

/** GET /api/v1/ticket/detail/{ticket_no} */
ticketRouter.get('/detail/:ticket_no', clientAuth({ mode: 'session', requireActive: false, rate: false }), async (ctx) => {
  const db = getDb();
  const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ctx.params.ticket_no]);
  if (!ticket || Number(ticket.user_id) !== Number(ctx.state.user.id)) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');
  const messages = await db.query('SELECT * FROM `ticket_message` WHERE `ticket_id` = ? ORDER BY `id` ASC', [ticket.id]);
  ctx.json({
    code: ERROR.OK,
    msg: 'ok',
    data: {
      ticket: sanitize(ticket),
      messages: messages.map((m) => ({
        id: m.id,
        sender_type: Number(m.sender_type),
        content: m.content,
        attachments: safeParse(m.attachments),
        created_at: m.created_at,
      })),
    },
  });
});

/** POST /api/v1/ticket/reply */
ticketRouter.post(
  '/reply',
  clientAuth({ mode: 'session', requireActive: false, rate: { bucket: 'ticket-reply', ipLimit: 60, userLimit: 30, windowSec: 3600 } }),
  async (ctx) => {
    const db = getDb();
    const ticketNo = String(ctx.input('ticket_no', ''));
    const content = String(ctx.input('content', '')).trim();
    if (!ticketNo || !content) throw new ApiError(ERROR.PARAM, '缺少工单号或内容');

    const ticket = await db.get('SELECT * FROM `ticket` WHERE `ticket_no` = ?', [ticketNo]);
    if (!ticket || Number(ticket.user_id) !== Number(ctx.state.user.id)) throw new ApiError(ERROR.NOT_FOUND, '工单不存在');
    if (Number(ticket.status) === 3) throw new ApiError(ERROR.PARAM, '工单已关闭，请新建工单');

    const ts = now();
    await db.insert('ticket_message', {
      ticket_id: ticket.id,
      sender_type: 1,
      sender_id: ctx.state.user.id,
      content,
      attachments: JSON.stringify(Array.isArray(ctx.input('attachments', [])) ? ctx.input('attachments', []) : []),
      created_at: ts,
    });
    await db.update(
      'ticket',
      { id: ticket.id },
      { status: 1, message_count: Number(ticket.message_count || 0) + 1, last_reply_at: ts, last_reply_by: 1, updated_at: ts },
    );
    ctx.json({ code: ERROR.OK, msg: '已回复', data: null });
  },
);

function sanitize(ticket) {
  return {
    id: ticket.id,
    ticket_no: ticket.ticket_no,
    title: ticket.title,
    category: Number(ticket.category),
    category_text: { 1: '开通/激活问题', 2: '解析失败', 3: '支付问题', 4: '其他' }[Number(ticket.category)] || '其他',
    priority: Number(ticket.priority),
    status: Number(ticket.status),
    status_text: { 0: '待处理', 1: '处理中', 2: '待用户回复', 3: '已关闭' }[Number(ticket.status)] || '',
    message_count: Number(ticket.message_count || 0),
    last_reply_at: ticket.last_reply_at,
    sla_at: ticket.sla_at,
    created_at: ticket.created_at,
    updated_at: ticket.updated_at,
  };
}

function safeParse(text) {
  try {
    return text ? JSON.parse(text) : [];
  } catch {
    return [];
  }
}

export default ticketRouter;
