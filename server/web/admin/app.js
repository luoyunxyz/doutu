/* ==========================================================================
 * 短视频去水印平台 · 管理后台前端逻辑
 *
 * 经典脚本（非 ES module），零外部依赖，由 Node 服务端在 /admin 下静态托管。
 * 目录：
 *   1) 常量与工具         6) 通用表格渲染
 *   2) API 封装           7) 路由
 *   3) UI 原语            8) 各页面 renderXxx(container)
 *   4) 弹窗 / 抽屉        9) 启动
 *   5) 图表（原生 canvas）
 * ========================================================================== */
(function () {
  'use strict';

  /* ====================================================================== */
  /* 1. 常量与工具                                                          */
  /* ====================================================================== */

  var API_BASE = '/admin/api';
  var TOKEN_KEY = 'dw_admin_token';
  var ADMIN_KEY = 'dw_admin_profile';
  var PAGE_SIZES = [20, 50, 100];

  var USER_STATUS = { 0: '未开通会员', 1: '正常', 2: '已过期', 3: '已封禁' };
  var USER_STATUS_CLASS = { 0: 'tag-gray', 1: 'tag-ok', 2: 'tag-warn', 3: 'tag-danger' };
  var LEVEL_TEXT = { 1: '普通', 2: '月卡', 3: '季卡', 4: '年卡', 5: '永久' };
  var ORDER_STATUS = { 0: '待支付', 1: '已支付', 2: '已关闭', 3: '已退款' };
  var ORDER_STATUS_CLASS = { 0: 'tag-warn', 1: 'tag-ok', 2: 'tag-gray', 3: 'tag-danger' };
  var ORDER_TYPE = { 1: '卡密购买', 2: '续费', 3: '升级' };
  var PAY_TYPE = { alipay: '支付宝', wxpay: '微信支付', qqpay: 'QQ 钱包', admin: '后台操作' };
  // 邀请码状态（替代原卡密体系，数值语义与原卡密一致）
  var INVITE_STATUS = { 0: '未使用', 1: '已使用', 2: '已作废', 3: '已过期' };
  var INVITE_STATUS_CLASS = { 0: 'tag-info', 1: 'tag-ok', 2: 'tag-danger', 3: 'tag-gray' };
  // 仅用于用户详情里「历史卡密」的只读展示（卡密体系已下线，不再有任何卡密操作）
  var CARD_STATUS = { 0: '未使用', 1: '已使用', 2: '已作废', 3: '已过期' };
  var CARD_STATUS_CLASS = { 0: 'tag-info', 1: 'tag-ok', 2: 'tag-danger', 3: 'tag-gray' };
  var TASK_STATUS = { 0: '排队中', 1: '解析中', 2: '合成中', 3: '已完成', 4: '失败', 5: '超时' };
  var TASK_STATUS_CLASS = { 0: 'tag-gray', 1: 'tag-info', 2: 'tag-info', 3: 'tag-ok', 4: 'tag-danger', 5: 'tag-warn' };
  var TICKET_STATUS = { 0: '待处理', 1: '处理中', 2: '待用户回复', 3: '已关闭' };
  var TICKET_STATUS_CLASS = { 0: 'tag-warn', 1: 'tag-info', 2: 'tag-gray', 3: 'tag-ok' };
  var TICKET_PRIORITY = { 1: '高', 2: '中', 3: '低' };
  var TICKET_PRIORITY_CLASS = { 1: 'tag-danger', 2: 'tag-warn', 3: 'tag-gray' };
  var TICKET_CATEGORY = { 1: '激活', 2: '解析', 3: '支付', 4: '其他' };
  var NOTICE_TYPE = { 1: '弹窗', 2: '滚动', 3: '首页横幅' };
  var PLATFORMS = ['douyin', 'kuaishou', 'xiaohongshu', 'bilibili', 'weibo', 'tiktok', 'other'];
  var PERM_PREFIXES = [
    'user', 'invite', 'order', 'revenue', 'task', 'route',
    'ticket', 'version', 'config', 'admin', 'audit', 'risk',
  ];
  var PERM_ACTIONS = ['read', 'write', 'export', 'delete', 'refund', 'void', 'trace'];

  var state = {
    token: null,
    admin: null,
    page: 'dashboard',
    cleanup: null,
    seq: 0,
  };

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  /** HTML 转义：所有进入 innerHTML 的用户数据必须过这里（防后台被备注字段打穿） */
  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /** 时间统一 YYYY-MM-DD HH:mm，空值显示 - */
  function fmtTime(value) {
    if (!value) return '-';
    var s = String(value).trim();
    if (!s) return '-';
    s = s.replace('T', ' ').replace(/\.\d+$/, '');
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(s)) return s.slice(0, 16);
    if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s + ' 00:00';
    return s.length > 16 ? s.slice(0, 16) : s;
  }

  function fmtDate(value) {
    if (!value) return '-';
    var s = String(value).replace('T', ' ');
    return s.length >= 10 ? s.slice(0, 10) : s;
  }

  /** 金额：后端返回字符串，前端只做「补两位小数」的字符串处理，不做浮点运算 */
  function fmtMoney(value) {
    if (value === null || value === undefined || value === '') return '0.00';
    var s = String(value).trim();
    if (!/^-?\d+(\.\d+)?$/.test(s)) return s;
    var neg = s.charAt(0) === '-';
    if (neg) s = s.slice(1);
    var parts = s.split('.');
    var int = parts[0];
    var dec = (parts[1] || '').padEnd(2, '0').slice(0, 2);
    var grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return (neg ? '-' : '') + grouped + '.' + dec;
  }

  function fmtInt(value) {
    var n = Number(value);
    if (!isFinite(n)) return '-';
    return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function fmtBytes(bytes) {
    var n = Number(bytes);
    if (!isFinite(n) || n <= 0) return n === 0 ? '0 B' : '-';
    var units = ['B', 'KB', 'MB', 'GB', 'TB'];
    var i = 0;
    while (n >= 1024 && i < units.length - 1) { n = n / 1024; i += 1; }
    return (i === 0 ? n : Math.round(n * 10) / 10) + ' ' + units[i];
  }

  function fmtDuration(sec) {
    var n = Number(sec);
    if (!isFinite(n) || n < 0) return '-';
    var d = Math.floor(n / 86400);
    var h = Math.floor((n % 86400) / 3600);
    var m = Math.floor((n % 3600) / 60);
    if (d > 0) return d + ' 天 ' + h + ' 小时';
    if (h > 0) return h + ' 小时 ' + m + ' 分';
    return m + ' 分';
  }

  function dayDiff(expireAt) {
    if (!expireAt) return null;
    var t = new Date(String(expireAt).replace('T', ' ').replace(/-/g, '/')).getTime();
    if (!isFinite(t)) return null;
    return Math.ceil((t - Date.now()) / 86400000);
  }

  function tag(text, cls) {
    return '<span class="tag ' + esc(cls || 'tag-gray') + '">' + esc(text === null || text === undefined ? '-' : text) + '</span>';
  }

  function userStatusTag(row) {
    var s = row.status_effective !== undefined && row.status_effective !== null ? Number(row.status_effective) : Number(row.status);
    var text = row.status_text || USER_STATUS[s] || '未知';
    return tag(text, USER_STATUS_CLASS[s] || 'tag-gray');
  }

  function orderStatusTag(row) {
    var s = Number(row.status);
    return tag(row.status_text || ORDER_STATUS[s] || '未知', ORDER_STATUS_CLASS[s] || 'tag-gray');
  }

  /** 用户「会员状态」：#/user 列表用，取 status_text / status_effective（0 未开通 1 正常 2 已过期 3 已封禁） */
  function effectiveUserStatus(row) {
    if (row && row.status_effective !== undefined && row.status_effective !== null) return Number(row.status_effective);
    return Number(row && row.status);
  }

  function activationTag(row) {
    var s = effectiveUserStatus(row);
    var text = (row && row.status_text) || USER_STATUS[s] || '未知';
    return tag(text, USER_STATUS_CLASS[s] || 'tag-gray');
  }

  function inviteStatusTag(row) {
    var s = Number(row && row.status);
    return tag(INVITE_STATUS[s] || '未知', INVITE_STATUS_CLASS[s] || 'tag-gray');
  }

  /**
   * 邀请码时长语义：0（默认）表示「注册后仍需后台激活」，
   * >0 表示注册即开通对应天数。这是新模型下最容易被误解的字段。
   */
  function inviteDurationText(duration) {
    var n = Number(duration);
    if (!n) return '注册后需后台激活';
    return '注册即开通 ' + n + ' 天';
  }

  function inviteDurationTag(duration) {
    var n = Number(duration);
    return tag(inviteDurationText(duration), n ? 'tag-ok' : 'tag-warn');
  }

  function levelTag(level) {
    var n = Number(level);
    return tag(LEVEL_TEXT[n] || ('L' + n), n >= 4 ? 'tag-info' : n >= 2 ? 'tag-ok' : 'tag-gray');
  }

  function rateTag(rate) {
    if (rate === null || rate === undefined || rate === '') return tag('无数据', 'tag-gray');
    var n = Number(rate);
    return tag(n + '%', n < 80 ? 'tag-danger' : 'tag-ok');
  }

  function boolTag(value, onText, offText) {
    return value ? tag(onText || '是', 'tag-ok') : tag(offText || '否', 'tag-gray');
  }

  /** 把值转成可安全放进 input value 的文本 */
  function val(value) {
    if (value === null || value === undefined) return '';
    if (typeof value === 'object') { try { return JSON.stringify(value); } catch (e) { return ''; } }
    return String(value);
  }

  function num(value, fallback) {
    var n = Number(value);
    return isFinite(n) ? n : (fallback === undefined ? 0 : fallback);
  }

  function truncate(text, len) {
    var s = String(text === null || text === undefined ? '' : text);
    return s.length > len ? s.slice(0, len) + '…' : s;
  }

  function nowDateStr() {
    var d = new Date();
    var p = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
  }

  function daysAgoStr(days) {
    var d = new Date(Date.now() - days * 86400000);
    var p = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
  }

  /* ====================================================================== */
  /* 2. API 封装                                                            */
  /* ====================================================================== */

  function getToken() {
    try { return localStorage.getItem(TOKEN_KEY) || null; } catch (e) { return state.token; }
  }

  function setToken(token) {
    state.token = token || null;
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch (e) { /* 隐私模式下忽略 */ }
  }

  function getStoredAdmin() {
    try {
      var raw = localStorage.getItem(ADMIN_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) { return null; }
  }

  function setStoredAdmin(admin) {
    state.admin = admin || null;
    try {
      if (admin) localStorage.setItem(ADMIN_KEY, JSON.stringify(admin));
      else localStorage.removeItem(ADMIN_KEY);
    } catch (e) { /* 忽略 */ }
  }

  /** 权限判定：支持 '*' 全权与 'user.*' 前缀通配（与后端 hasPerm 一致） */
  function hasPerm(required) {
    if (!required) return true;
    var admin = state.admin;
    if (!admin) return false;
    var list = Array.isArray(admin.perms) ? admin.perms : [];
    if (list.indexOf('*') >= 0) return true;
    var needs = Array.isArray(required) ? required : [required];
    for (var i = 0; i < needs.length; i += 1) {
      var need = needs[i];
      if (list.indexOf(need) >= 0) return true;
      var prefix = String(need).split('.')[0];
      if (list.indexOf(prefix + '.*') >= 0) return true;
    }
    return false;
  }

  function hasMenu(key) {
    var admin = state.admin;
    if (!admin) return false;
    var menus = Array.isArray(admin.menus) ? admin.menus : [];
    return menus.indexOf('*') >= 0 || menus.indexOf(key) >= 0;
  }

  function buildQuery(query) {
    if (!query) return '';
    var parts = [];
    Object.keys(query).forEach(function (key) {
      var value = query[key];
      if (value === undefined || value === null || value === '') return;
      if (Array.isArray(value)) {
        value.forEach(function (v) {
          if (v !== undefined && v !== null && v !== '') parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(v));
        });
      } else {
        parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
      }
    });
    return parts.length ? '?' + parts.join('&') : '';
  }

  function sessionExpired(message) {
    setToken(null);
    setStoredAdmin(null);
    showLogin(message || '登录已失效，请重新登录');
  }

  /**
   * 统一请求封装。
   * @returns {Promise<{code:number,msg:string,data:any}>}
   */
  async function api(path, options) {
    var opts = options || {};
    var query = buildQuery(opts.query);
    var init = { method: opts.method || 'GET', headers: {} };
    var token = getToken();
    if (token) init.headers['X-Admin-Token'] = token;

    if (opts.body !== undefined && opts.method && opts.method !== 'GET') {
      if (opts.form) {
        init.headers['Content-Type'] = 'application/x-www-form-urlencoded; charset=utf-8';
        init.body = new URLSearchParams(opts.body).toString();
      } else {
        init.headers['Content-Type'] = 'application/json; charset=utf-8';
        init.body = JSON.stringify(opts.body);
      }
    }
    if (opts.headers) {
      Object.keys(opts.headers).forEach(function (k) { init.headers[k] = opts.headers[k]; });
    }

    var res;
    try {
      res = await fetch(API_BASE + path + query, init);
    } catch (err) {
      toast('网络请求失败：' + (err && err.message ? err.message : '未知错误'), 'error');
      return { code: -1, msg: '网络请求失败', data: null };
    }

    var text = await res.text();
    var payload = null;
    try { payload = text ? JSON.parse(text) : null; } catch (e) { payload = null; }

    if (!payload || typeof payload.code !== 'number') {
      // 兜底：服务端统一响应结构异常时，401/403 仍然按登录失效处理
      if (res.status === 401 || res.status === 403) {
        sessionExpired('登录已失效，请重新登录');
        return { code: 6001, msg: '登录已失效，请重新登录', data: null };
      }
      toast('服务端返回异常（HTTP ' + res.status + '）', 'error');
      return { code: -1, msg: '服务端返回异常', data: null };
    }
    if (payload.code === 6001) {
      sessionExpired(payload.msg);
      return payload;
    }
    if (payload.code !== 0) {
      if (opts.silent !== true) toast(payload.msg || '操作失败', 'error');
      return payload;
    }
    return payload;
  }

  function apiGet(path, query, options) {
    var opts = options || {};
    opts.method = 'GET';
    opts.query = query;
    return api(path, opts);
  }

  function apiPost(path, body, options) {
    var opts = options || {};
    opts.method = 'POST';
    opts.body = body || {};
    return api(path, opts);
  }

  function apiPut(path, body, options) {
    var opts = options || {};
    opts.method = 'PUT';
    opts.body = body || {};
    return api(path, opts);
  }

  function apiDelete(path, body, options) {
    var opts = options || {};
    opts.method = 'DELETE';
    opts.body = body || {};
    return api(path, opts);
  }

  /* ------------------------------ 文件导出 ------------------------------ */

  /** 从 Content-Disposition 解析文件名 */
  function filenameFromHeader(header, fallback) {
    if (!header) return fallback;
    var star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header);
    if (star && star[1]) {
      try { return decodeURIComponent(star[1].replace(/^["']|["']$/g, '')); } catch (e) { /* 继续 */ }
    }
    var plain = /filename="?([^";]+)"?/i.exec(header);
    if (plain && plain[1]) {
      try { return decodeURIComponent(plain[1]); } catch (e) { return plain[1]; }
    }
    return fallback;
  }

  /**
   * 导出下载：token 在 header 里，不能直接用 location.href，
   * 必须 fetch 拿 blob 再触发下载。
   */
  async function download(path, query, fallbackName) {
    var init = { method: 'GET', headers: {} };
    var token = getToken();
    if (token) init.headers['X-Admin-Token'] = token;
    var res;
    try {
      res = await fetch(API_BASE + path + buildQuery(query), init);
    } catch (err) {
      toast('导出失败：网络错误', 'error');
      return;
    }
    if (!res.ok) {
      toast('导出失败：HTTP ' + res.status, 'error');
      return;
    }
    var disposition = res.headers.get('Content-Disposition') || '';
    var name = filenameFromHeader(disposition, fallbackName || 'export.csv');
    var blob = await res.blob();
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
    toast('已开始下载 ' + name, 'ok');
  }

  /* ====================================================================== */
  /* 3. UI 原语                                                             */
  /* ====================================================================== */

  function toast(message, type, title) {
    var root = document.getElementById('toast-root');
    if (!root) return;
    var el = document.createElement('div');
    el.className = 'toast ' + (type || 'info');
    var head = title ? '<div class="toast-title">' + esc(title) + '</div>' : '';
    el.innerHTML = head + esc(message === null || message === undefined ? '' : message);
    root.appendChild(el);
    setTimeout(function () {
      el.style.transition = 'opacity .25s, transform .25s';
      el.style.opacity = '0';
      el.style.transform = 'translateX(16px)';
      setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 260);
    }, type === 'error' ? 5200 : 3000);
  }

  var modalStack = [];

  /**
   * 打开弹窗。
   * @param {{title:string, bodyHtml:string, size?:string, confirmText?:string,
   *          cancelText?:string, danger?:boolean, onMount?:Function,
   *          onConfirm?:Function}} cfg  onConfirm 返回 false 阻止关闭
   */
  function openModal(cfg) {
    var root = document.getElementById('modal-root');
    var mask = document.createElement('div');
    mask.className = 'modal-mask';
    var size = cfg.size || 'modal-md';
    mask.innerHTML =
      '<div class="modal ' + esc(size) + '" role="dialog" aria-modal="true">' +
        '<div class="modal-head"><h3>' + esc(cfg.title || '') + '</h3>' +
          '<button type="button" class="modal-close" data-close="1" aria-label="关闭">×</button></div>' +
        '<div class="modal-body">' + (cfg.bodyHtml || '') + '</div>' +
        '<div class="modal-foot">' +
          '<button type="button" class="btn" data-close="1">' + esc(cfg.cancelText || '取消') + '</button>' +
          (cfg.hideConfirm ? '' :
            '<button type="button" class="btn ' + (cfg.danger ? 'btn-danger' : 'btn-primary') + '" data-confirm="1">' +
            esc(cfg.confirmText || '确定') + '</button>') +
        '</div>' +
      '</div>';

    var close = function () {
      if (mask.parentNode) mask.parentNode.removeChild(mask);
      modalStack = modalStack.filter(function (m) { return m !== close; });
      document.removeEventListener('keydown', onKey);
    };
    var onKey = function (e) { if (e.key === 'Escape') close(); };

    $$('[data-close]', mask).forEach(function (btn) { btn.addEventListener('click', close); });
    mask.addEventListener('mousedown', function (e) { if (e.target === mask) close(); });
    var confirmBtn = $('[data-confirm]', mask);
    if (confirmBtn && cfg.onConfirm) {
      confirmBtn.addEventListener('click', async function () {
        confirmBtn.disabled = true;
        try {
          var result = await cfg.onConfirm(mask, close);
          if (result === false) return;
          close();
        } finally {
          confirmBtn.disabled = false;
        }
      });
    } else if (confirmBtn) {
      confirmBtn.addEventListener('click', close);
    }
    document.addEventListener('keydown', onKey);
    root.appendChild(mask);
    modalStack.push(close);
    var first = $('input:not([type=hidden]), textarea, select', mask);
    if (cfg.onMount) cfg.onMount(mask, close);
    else if (first) first.focus();
    return { el: mask, close: close };
  }

  /** 读取弹窗内所有 [data-field] 控件的值 */
  function readFields(root) {
    var out = {};
    $$('[data-field]', root).forEach(function (el) {
      var key = el.getAttribute('data-field');
      if (el.type === 'checkbox') out[key] = el.checked;
      else if (el.getAttribute('data-type') === 'int') out[key] = el.value === '' ? null : Number(el.value);
      else out[key] = el.value;
    });
    return out;
  }

  /** 二次确认（可选要求密码） */
  function confirmDialog(message, options) {
    var opts = options || {};
    if (!opts.needPassword) {
      return new Promise(function (resolve) {
        openModal({
          title: opts.title || '操作确认',
          size: 'modal-sm',
          bodyHtml: '<div style="font-size:13.5px;line-height:1.7">' + message + '</div>',
          confirmText: opts.confirmText || '确定',
          danger: opts.danger !== false,
          onConfirm: function () { resolve(true); },
          onMount: function (mask, close) {
            mask.addEventListener('click', function (e) {
              if (e.target.closest && e.target.closest('[data-close]')) resolve(false);
            });
          },
        });
      });
    }
    return new Promise(function (resolve) {
      var settled = false;
      var finish = function (value) { if (!settled) { settled = true; resolve(value); } };
      openModal({
        title: opts.title || '敏感操作二次确认',
        size: 'modal-sm',
        danger: opts.danger !== false,
        bodyHtml:
          '<div class="notice-bar danger" style="margin-bottom:12px">' +
            esc(opts.warning || '该操作不可撤销，请输入当前管理员登录密码确认。') + '</div>' +
          '<div style="font-size:13.5px;line-height:1.7;margin-bottom:12px">' + message + '</div>' +
          '<label class="field"><span class="field-label">登录密码 <span style="color:#c0392b">*</span></span>' +
            '<input class="input" type="password" data-field="password" autocomplete="current-password" placeholder="请输入当前登录密码"></label>' +
          '<div class="form-note">密码只随本次请求提交，不会保存在浏览器任何位置。</div>',
        confirmText: opts.confirmText || '确认执行',
        onConfirm: function (mask) {
          var fields = readFields(mask);
          if (!fields.password) { toast('请输入登录密码', 'warn'); return false; }
          finish({ ok: true, password: fields.password });
          return true;
        },
        onMount: function (mask, close) {
          var input = $('[data-field="password"]', mask);
          if (input) input.focus();
          mask.addEventListener('click', function (e) {
            if (e.target.closest && e.target.closest('[data-close]')) finish({ ok: false });
          });
          mask.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') { e.preventDefault(); var btn = $('[data-confirm]', mask); if (btn) btn.click(); }
          });
        },
      });
    });
  }

  /** 输入弹窗（单值或多字段），resolve(null) 表示取消 */
  function promptDialog(cfg) {
    return new Promise(function (resolve) {
      var fields = cfg.fields || [];
      var html = fields.map(function (f) {
        var label = '<span class="field-label">' + esc(f.label || f.key) +
          (f.required ? ' <span style="color:#c0392b">*</span>' : '') + '</span>';
        var control;
        if (f.type === 'textarea') {
          control = '<textarea class="textarea' + (f.mono ? ' mono' : '') + '" rows="' + (f.rows || 5) +
            '" data-field="' + esc(f.key) + '" placeholder="' + esc(f.placeholder || '') + '">' + esc(f.value || '') + '</textarea>';
        } else if (f.type === 'select') {
          control = '<select class="select" data-field="' + esc(f.key) + '">' +
            (f.options || []).map(function (o) {
              return '<option value="' + esc(o.value) + '"' + (String(o.value) === String(f.value) ? ' selected' : '') + '>' +
                esc(o.label) + '</option>';
            }).join('') + '</select>';
        } else if (f.type === 'checkbox') {
          control = '<label class="checkbox"><input type="checkbox" data-field="' + esc(f.key) + '"' +
            (f.value ? ' checked' : '') + '> ' + esc(f.checkboxLabel || '启用') + '</label>';
        } else if (f.type === 'password') {
          control = '<input class="input" type="password" data-field="' + esc(f.key) + '" placeholder="' +
            esc(f.placeholder || '') + '" autocomplete="new-password">';
        } else {
          control = '<input class="input' + (f.mono ? ' mono' : '') + '" type="' + esc(f.type || 'text') +
            '" data-field="' + esc(f.key) + '" value="' + esc(f.value === undefined ? '' : f.value) +
            '" placeholder="' + esc(f.placeholder || '') + '">';
        }
        return '<label class="field"' + (f.full ? ' style="grid-column:1/-1"' : '') + '>' + label + control +
          (f.note ? '<span class="form-note">' + esc(f.note) + '</span>' : '') + '</label>';
      }).join('');
      openModal({
        title: cfg.title || '请输入',
        size: cfg.size || 'modal-md',
        bodyHtml:
          (cfg.intro ? '<div class="notice-bar info" style="margin-bottom:12px">' + cfg.intro + '</div>' : '') +
          '<div class="' + (fields.length > 1 ? 'form-grid' : '') + '">' + html + '</div>' +
          (cfg.extraHtml || ''),
        confirmText: cfg.confirmText || '确定',
        cancelText: '取消',
        danger: cfg.danger,
        onConfirm: function (mask) {
          var values = readFields(mask);
          for (var i = 0; i < fields.length; i += 1) {
            var f = fields[i];
            if (f.required) {
              var v = values[f.key];
              if (v === '' || v === null || v === undefined || v === false) { toast('请填写：' + (f.label || f.key), 'warn'); return false; }
            }
          }
          resolve(values);
          return true;
        },
        onMount: function (mask) {
          var first = $('[data-field]', mask);
          if (first) first.focus();
        },
      }).el.addEventListener('click', function (e) {
        if (e.target.closest && e.target.closest('[data-close]')) resolve(null);
      });
    });
  }

  /** 详情抽屉 */
  function openDrawer(cfg) {
    var root = document.getElementById('modal-root');
    var mask = document.createElement('div');
    mask.className = 'drawer-mask';
    mask.innerHTML =
      '<div class="drawer" role="dialog" aria-modal="true">' +
        '<div class="drawer-head"><div><h3>' + esc(cfg.title || '详情') + '</h3>' +
          (cfg.subtitle ? '<div class="drawer-sub">' + esc(cfg.subtitle) + '</div>' : '') + '</div>' +
          '<button type="button" class="modal-close" data-close="1" aria-label="关闭">×</button></div>' +
        '<div class="drawer-body">' + (cfg.bodyHtml || '') + '</div>' +
        (cfg.footerHtml ? '<div class="drawer-foot">' + cfg.footerHtml + '</div>' : '') +
      '</div>';
    var close = function () {
      if (mask.parentNode) mask.parentNode.removeChild(mask);
      document.removeEventListener('keydown', onKey);
    };
    var onKey = function (e) { if (e.key === 'Escape') close(); };
    $$('[data-close]', mask).forEach(function (btn) { btn.addEventListener('click', close); });
    mask.addEventListener('mousedown', function (e) { if (e.target === mask) close(); });
    document.addEventListener('keydown', onKey);
    root.appendChild(mask);
    if (cfg.onMount) cfg.onMount(mask, close);
    return { el: mask, close: close };
  }

  /** 生成筛选栏字段 HTML */
  function fieldHtml(label, controlHtml, extraClass) {
    return '<div class="field ' + esc(extraClass || '') + '"><span class="field-label">' + esc(label) + '</span>' +
      controlHtml + '</div>';
  }

  function textFilter(key, label, value, placeholder, cls) {
    return fieldHtml(label,
      '<input class="input" type="text" data-f="' + esc(key) + '" value="' + esc(value || '') +
      '" placeholder="' + esc(placeholder || '') + '">', cls);
  }

  function dateFilter(key, label, value) {
    return fieldHtml(label,
      '<input class="input" type="date" data-f="' + esc(key) + '" value="' + esc(value || '') + '">', 'date');
  }

  function selectFilter(key, label, options, value) {
    return fieldHtml(label, '<select class="select" data-f="' + esc(key) + '">' +
      options.map(function (o) {
        return '<option value="' + esc(o.value) + '"' + (String(o.value) === String(value === undefined || value === null ? '' : value) ? ' selected' : '') + '>' +
          esc(o.label) + '</option>';
      }).join('') + '</select>');
  }

  function optionsFrom(map, allLabel) {
    var out = [{ value: '', label: allLabel || '全部' }];
    Object.keys(map).forEach(function (k) { out.push({ value: k, label: map[k] }); });
    return out;
  }

  function formField(label, controlHtml, opts) {
    var o = opts || {};
    return '<label class="field' + (o.span ? ' span-2' : '') + '">' +
      '<span class="field-label">' + esc(label) + (o.required ? ' <span class="req">*</span>' : '') + '</span>' +
      controlHtml + (o.note ? '<span class="form-note">' + esc(o.note) + '</span>' : '') + '</label>';
  }

  function inputHtml(key, value, opts) {
    var o = opts || {};
    return '<input class="input' + (o.mono ? ' mono' : '') + '" type="' + esc(o.type || 'text') + '" data-field="' + esc(key) + '"' +
      (o.type === 'number' ? ' step="' + esc(o.step || '1') + '"' : '') +
      ' value="' + esc(value === undefined || value === null ? '' : value) + '"' +
      ' placeholder="' + esc(o.placeholder || '') + '">';
  }

  function selectHtml(key, options, value) {
    return '<select class="select" data-field="' + esc(key) + '">' + options.map(function (o) {
      return '<option value="' + esc(o.value) + '"' + (String(o.value) === String(value) ? ' selected' : '') + '>' + esc(o.label) + '</option>';
    }).join('') + '</select>';
  }

  function checkboxHtml(key, label, checked) {
    return '<label class="checkbox"><input type="checkbox" data-field="' + esc(key) + '"' + (checked ? ' checked' : '') + '> ' + esc(label) + '</label>';
  }

  function switchHtml(key, checked, label) {
    return '<label class="switch"><input type="checkbox" data-field="' + esc(key) + '"' + (checked ? ' checked' : '') + '>' +
      '<span class="switch-track"></span><span>' + esc(label || '') + '</span></label>';
  }

  function textareaHtml(key, value, opts) {
    var o = opts || {};
    return '<textarea class="textarea' + (o.mono ? ' mono' : '') + '" rows="' + (o.rows || 6) + '" data-field="' + esc(key) + '"' +
      ' placeholder="' + esc(o.placeholder || '') + '">' + esc(value === undefined || value === null ? '' : value) + '</textarea>';
  }

  function emptyState(title, hint) {
    return '<div class="empty-state"><strong>' + esc(title || '暂无数据') + '</strong>' +
      (hint ? esc(hint) : '') + '</div>';
  }

  function noticeBar(text, type) {
    return '<div class="notice-bar ' + esc(type || 'info') + '">' + text + '</div>';
  }

  function statCard(label, value, extra, cls) {
    return '<div class="stat ' + esc(cls || '') + '">' +
      '<span class="stat-label">' + esc(label) + '</span>' +
      '<div class="stat-value">' + (value === undefined || value === null ? '-' : esc(value)) + '</div>' +
      (extra ? '<div class="stat-extra">' + esc(extra) + '</div>' : '') + '</div>';
  }

  /** 需要下载的取证 / 导出链接：用按钮 + fetch 保证带 token */
  function downloadButton(label, path, query, filename, cls) {
    return '<button type="button" class="' + (cls || 'btn btn-sm') + '" data-download="' + esc(path) + '"' +
      ' data-dlq="' + esc(JSON.stringify(query || {})) + '" data-dlname="' + esc(filename || '') + '">' + esc(label) + '</button>';
  }

  /** 全局委托：任何 [data-download] 按钮都能带 token 下载 */
  document.addEventListener('click', function (e) {
    var btn = e.target.closest ? e.target.closest('[data-download]') : null;
    if (!btn) return;
    e.preventDefault();
    var query = {};
    try { query = JSON.parse(btn.getAttribute('data-dlq') || '{}'); } catch (err) { query = {}; }
    download(btn.getAttribute('data-download'), query, btn.getAttribute('data-dlname'));
  });

  /** 详情 JSON 折叠块 */
  function jsonBlock(value) {
    var text;
    if (typeof value === 'string') text = value;
    else { try { text = JSON.stringify(value, null, 2); } catch (e) { text = String(value); } }
    if (!text) return '<span class="inline-hint">-</span>';
    return '<details><summary class="btn-link">展开 JSON</summary><pre class="code">' + esc(text) + '</pre></details>';
  }

  /* ====================================================================== */
  /* 4. 通用表格渲染                                                        */
  /* ====================================================================== */

  var activeTable = null;

  function colIndexByKey(cols, key) {
    for (var i = 0; i < cols.length; i += 1) if (cols[i].key === key) return i;
    return -1;
  }

  function handleSort(cols, s, key) {
    if (s.sort === key) s.order = s.order === 'desc' ? 'asc' : 'desc';
    else { s.sort = key; s.order = 'desc'; }
    s.page = 1;
  }

  function bindFilterInputs(host, s, reload) {
    $$('[data-f]', host).forEach(function (el) {
      var key = el.getAttribute('data-f');
      var handler = function () { s[key] = el.value; };
      el.addEventListener('change', handler);
      if (el.tagName === 'INPUT' && el.type === 'text') {
        el.addEventListener('input', handler);
        el.addEventListener('keydown', function (e) { if (e.key === 'Enter') { handler(); reload(); } });
      }
    });
  }

  /** 组装筛选栏 HTML */
  function buildFilterBar(fields, s) {
    return '<div class="filters">' + fields.map(function (f) {
      return f(s);
    }).join('') +
      '<div class="filter-actions">' +
        '<button type="button" class="btn btn-primary btn-sm" data-act="search">查询</button>' +
        '<button type="button" class="btn btn-sm" data-act="reset">重置</button>' +
      '</div>' +
    '</div>';
  }

  function bindFilterBar(host, fields, s, reload) {
    bindFilterInputs(host, s, reload);
    var search = $('[data-act="search"]', host);
    var reset = $('[data-act="reset"]', host);
    if (search) search.addEventListener('click', function () { s.page = 1; reload(); });
    if (reset) reset.addEventListener('click', function () {
      fields.forEach(function (f) { if (f.reset) f.reset(s); });
      s.page = 1;
      reload();
    });
  }

  /**
   * 统一表格渲染。
   * @param {HTMLElement} container
   * @param {{
   *   title?: string, subtitle?: string,
   *   fields?: Array<Function>, state: object,
   *   columns: Array<{key:string,title:string,align?:string,sortable?:boolean,className?:string,
   *                   render?:Function,value?:Function}>,
   *   rows: Array, loading?: boolean, error?: string,
   *   total?: number, pageSizes?: Array<number>,
   *   rowClass?: Function, rowKey?: Function, empty?: string, emptyHint?: string,
   *   headActions?: string|Function, footNote?: string|Function, onRowMount?: Function,
   *   headerCells?: Array<{title:string,html?:boolean,className?:string}>
   * }} cfg
   */
  function renderTable(container, cfg) {
    var s = cfg.state || {};
    var cols = cfg.columns || [];
    var data = cfg.rows || [];
    var fields = cfg.fields || [];
    if (s.page === undefined) s.page = 1;
    if (s.limit === undefined) s.limit = 20;
    if (s.sort === undefined) s.sort = '';
    if (s.order === undefined) s.order = 'desc';

    var headActions = typeof cfg.headActions === 'function' ? cfg.headActions(s, data) : (cfg.headActions || '');
    var html = '<div class="card">';
    html += '<div class="card-head"><h3>' + esc(cfg.title || '') + '</h3>' +
      (headActions ? '<div class="card-actions">' + headActions + '</div>' : '') + '</div>';
    html += buildFilterBar(fields, s);

    if (cfg.loading) {
      html += '<div class="loading-block">加载中…</div>';
    } else if (cfg.error) {
      html += '<div class="empty-state"><strong>加载失败</strong>' + esc(cfg.error) + '</div>';
    } else if (!data.length) {
      html += emptyState(cfg.empty || '暂无数据', cfg.emptyHint || '调整筛选条件后重试');
    } else {
      html += '<div class="table-wrap"><table class="tbl"><thead><tr>';
      if (cfg.checkbox) {
        html += '<th style="width:34px"><input type="checkbox" data-check-all="1"></th>';
      }
      if (cfg.headerCells) {
        cfg.headerCells.forEach(function (h) {
          html += '<th class="' + esc(h.className || '') + '">' + (h.html ? h.title : esc(h.title)) + '</th>';
        });
      }
      cols.forEach(function (col) {
        var sortable = col.sortable === true && s.sort !== undefined;
        var mark = '';
        if (sortable && s.sort === col.key) mark = '<span class="sort-mark">' + (s.order === 'asc' ? '▲' : '▼') + '</span>';
        html += '<th class="' + esc(col.className || '') + (sortable ? ' sortable' : '') + '"' +
          (col.align === 'right' ? ' style="text-align:right"' : '') +
          (sortable ? ' data-sort="' + esc(col.key) + '" title="点击切换排序"' : '') + '>' +
          esc(col.title) + mark + '</th>';
      });
      html += '</tr></thead><tbody>';

      data.forEach(function (row, index) {
        var rowCls = cfg.rowClass ? (cfg.rowClass(row, index) || '') : '';
        html += '<tr' + (rowCls ? ' class="' + esc(rowCls) + '"' : '') +
          (cfg.rowKey ? ' data-row-key="' + esc(cfg.rowKey(row, index)) + '"' : '') + '>';
        if (cfg.checkbox) {
          html += '<td><input type="checkbox" data-row-check="1" value="' + esc(cfg.rowKey ? cfg.rowKey(row, index) : index) + '"></td>';
        }
        cols.forEach(function (col) {
          var content = col.render ? col.render(row, index) : esc(val(col.value ? col.value(row, index) : row[col.key]));
          html += '<td class="' + esc(col.className || '') + '"' +
            (col.align === 'right' ? ' style="text-align:right"' : '') + '>' + content + '</td>';
        });
        html += '</tr>';
      });
      html += '</tbody></table></div>';

      var sizes = cfg.pageSizes || PAGE_SIZES;
      var total = cfg.total === undefined ? data.length : cfg.total;
      var pages = Math.max(1, Math.ceil(total / s.limit));
      var start = total === 0 ? 0 : (s.page - 1) * s.limit + 1;
      var end = Math.min(total, (s.page - 1) * s.limit + data.length);
      html += '<div class="table-foot"><div class="table-total">' +
        (cfg.footNote ? (typeof cfg.footNote === 'function' ? cfg.footNote(s, data) : cfg.footNote) :
          '共 ' + fmtInt(total) + ' 条，当前 ' + fmtInt(start) + '-' + fmtInt(end)) +
        '</div><div class="pager">' +
        '<button type="button" data-go="prev"' + (s.page <= 1 ? ' disabled' : '') + '>上一页</button>' +
        pagerButtons(s.page, pages) +
        '<button type="button" data-go="next"' + (s.page >= pages ? ' disabled' : '') + '>下一页</button>' +
        '<span class="pager-info">每页</span><select class="select" data-limit="1" style="width:74px">' +
        sizes.map(function (n) { return '<option value="' + n + '"' + (Number(s.limit) === n ? ' selected' : '') + '>' + n + '</option>'; }).join('') +
        '</select></div></div>';
    }
    html += '</div>';
    container.innerHTML = html;

    /* ---------------------------- 事件绑定 ---------------------------- */
    bindFilterBar(container, fields, s, cfg.reload);

    if (cfg.checkbox) {
      var all = $('[data-check-all]', container);
      var boxes = $$('[data-row-check]', container);
      var syncAll = function () {
        if (!all) return;
        var checked = boxes.filter(function (b) { return b.checked; }).length;
        all.checked = boxes.length > 0 && checked === boxes.length;
        all.indeterminate = checked > 0 && checked < boxes.length;
      };
      if (all) all.addEventListener('change', function () { boxes.forEach(function (b) { b.checked = all.checked; }); if (cfg.onSelectChange) cfg.onSelectChange(); });
      boxes.forEach(function (b) { b.addEventListener('change', function () { syncAll(); if (cfg.onSelectChange) cfg.onSelectChange(); }); });
    }

    $$('th[data-sort]', container).forEach(function (th) {
      th.addEventListener('click', function () {
        var key = th.getAttribute('data-sort');
        var idx = colIndexByKey(cols, key);
        if (idx < 0 || cols[idx].sortable !== true) return;
        handleSort(cols, s, key);
        cfg.reload();
      });
    });

    $$('button[data-go]', container).forEach(function (btn) {
      btn.addEventListener('click', function () {
        var go = btn.getAttribute('data-go');
        var pages = Math.max(1, Math.ceil((cfg.total === undefined ? data.length : cfg.total) / s.limit));
        if (go === 'prev') s.page = Math.max(1, s.page - 1);
        else if (go === 'next') s.page = Math.min(pages, s.page + 1);
        else s.page = Number(go) || 1;
        cfg.reload();
      });
    });

    var limitSel = $('[data-limit]', container);
    if (limitSel) limitSel.addEventListener('change', function () { s.limit = Number(limitSel.value); s.page = 1; cfg.reload(); });

    if (cfg.onRowMount) cfg.onRowMount(container, data);
  }

  /** 收集当前表格已勾选的行 key */
  function checkedKeys() {
    var host = document.getElementById('view');
    if (!host) return [];
    return $$('[data-row-check]:checked', host).map(function (b) { return b.value; });
  }

  function selectedIds() {
    return checkedKeys().map(function (v) { return Number(v); }).filter(function (n) { return !!n; });
  }

  var PAGER_WINDOW = 7;

  function pagerButtons(page, pages) {
    var out = [];
    var from = 1;
    var to = pages;
    if (pages > PAGER_WINDOW) {
      var half = Math.floor(PAGER_WINDOW / 2);
      from = Math.max(1, page - half);
      to = Math.min(pages, from + PAGER_WINDOW - 1);
      from = Math.max(1, to - PAGER_WINDOW + 1);
    }
    if (from > 1) out.push('<button type="button" data-go="1">1</button>');
    if (from > 2) out.push('<span class="pager-info">…</span>');
    for (var i = from; i <= to; i += 1) {
      out.push('<button type="button" data-go="' + i + '"' + (i === page ? ' class="active"' : '') + '>' + i + '</button>');
    }
    if (to < pages - 1) out.push('<span class="pager-info">…</span>');
    if (to < pages) out.push('<button type="button" data-go="' + pages + '">' + pages + '</button>');
    return out.join('');
  }

  /* ====================================================================== */
  /* 5. 图表（原生 canvas，零第三方库）                                      */
  /* ====================================================================== */

  var CHART_COLORS = {
    bar: '#4a86c8',
    barAlt: '#8fb8e0',
    line: '#2563a8',
    line2: '#c98a1e',
    grid: '#e6ebf2',
    axis: '#c3cddd',
    text: '#7b8a9c',
    area: 'rgba(37,99,168,.10)',
  };

  /** 已绘制的图表重绘函数：窗口尺寸变化时重绘，避免 canvas 拉伸模糊 */
  var chartRegistry = [];

  function chartPalette(i) {
    var colors = ['#4a86c8', '#c98a1e', '#1a8a5a', '#c0392b', '#7a5cc0', '#2b8fa8'];
    return colors[i % colors.length];
  }

  function prepareCanvas(canvas, height) {
    var dpr = window.devicePixelRatio || 1;
    var width = canvas.parentNode.clientWidth || 720;
    var h = height || 260;
    canvas.style.height = h + 'px';
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(h * dpr);
    var ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, h);
    return { ctx: ctx, w: width, h: h };
  }

  /** 坐标轴 + 水平网格，返回绘制区 */
  function drawAxes(ctx, w, h, opts) {
    var pad = { top: 16, right: 16, bottom: opts.bottom || 42, left: opts.left || 54 };
    var plot = { x: pad.left, y: pad.top, w: w - pad.left - pad.right, h: h - pad.top - pad.bottom };
    ctx.font = '11px -apple-system, "Microsoft YaHei", sans-serif';
    ctx.textBaseline = 'middle';
    ctx.strokeStyle = CHART_COLORS.grid;
    ctx.lineWidth = 1;
    for (var i = 0; i <= opts.ticks; i += 1) {
      var y = plot.y + (plot.h / opts.ticks) * i;
      ctx.beginPath();
      ctx.moveTo(plot.x, Math.round(y) + 0.5);
      ctx.lineTo(plot.x + plot.w, Math.round(y) + 0.5);
      ctx.stroke();
      var value = opts.max - (opts.max / opts.ticks) * i;
      ctx.fillStyle = CHART_COLORS.text;
      ctx.textAlign = 'right';
      ctx.fillText(opts.formatY ? opts.formatY(value) : String(Math.round(value)), plot.x - 7, y);
    }
    ctx.strokeStyle = CHART_COLORS.axis;
    ctx.beginPath();
    ctx.moveTo(plot.x + 0.5, plot.y);
    ctx.lineTo(plot.x + 0.5, plot.y + plot.h);
    ctx.stroke();
    return plot;
  }

  function makeTip(host) {
    var tip = document.createElement('div');
    tip.className = 'chart-tip';
    tip.hidden = true;
    host.appendChild(tip);
    return tip;
  }

  function drawHighlight(ctx, point) {
    if (!ctx || !point) return;
    ctx.save();
    ctx.strokeStyle = 'rgba(37,99,168,.55)';
    ctx.setLineDash([3, 3]);
    ctx.beginPath();
    ctx.moveTo(point.x + 0.5, point.plotTop);
    ctx.lineTo(point.x + 0.5, point.plotBottom);
    ctx.stroke();
    ctx.restore();
  }

  /**
   * 柱状图：labels + values，带坐标轴 / 网格 / 数值标签 / hover tooltip。
   * @param {HTMLElement} host 容器（内部会插入 canvas）
   * @param {{labels:string[], values:number[], tips?:string[], height?:number, color?:string,
   *          unit?:string, valueFormat?:Function, valueLabels?:boolean, maxTicks?:number}} opts
   */
  function barChart(host, opts) {
    host.innerHTML = '';
    var canvas = document.createElement('canvas');
    host.appendChild(canvas);
    var tip = makeTip(host);
    var labels = opts.labels || [];
    var values = (opts.values || []).map(function (v) { return num(v, 0); });
    var max = Math.max.apply(null, values.concat([0]));
    if (max <= 0) max = 1;
    max = niceMax(max);
    var ticks = opts.maxTicks || 4;
    var height = opts.height || 280;
    var unit = opts.unit || '';
    var formatY = opts.valueFormat || function (v) { return String(Math.round(v)); };

    var points = [];
    var draw = function () {
      var pf = prepareCanvas(canvas, height);
      var ctx = pf.ctx;
      var plot = drawAxes(ctx, pf.w, pf.h, {
        max: max,
        ticks: ticks,
        bottom: 46,
        left: 58,
        formatY: formatY,
      });
      var n = Math.max(labels.length, 1);
      var slot = plot.w / n;
      canvas.__slotW = slot;
      var barW = Math.max(4, Math.min(38, slot * 0.58));
      points.length = 0;
      ctx.font = '11px -apple-system, "Microsoft YaHei", sans-serif';
      labels.forEach(function (label, i) {
        var value = values[i] || 0;
        var barH = max > 0 ? (value / max) * plot.h : 0;
        var x = plot.x + slot * i + slot / 2;
        var y = plot.y + plot.h - barH;
        var color = opts.color || (opts.colors ? opts.colors[i % opts.colors.length] : CHART_COLORS.bar);
        var grad = ctx.createLinearGradient(0, y, 0, plot.y + plot.h);
        grad.addColorStop(0, color);
        grad.addColorStop(1, 'rgba(74,134,200,.45)');
        ctx.fillStyle = opts.colors ? color : grad;
        var radius = Math.min(3, barW / 2);
        var top = value > 0 ? y : plot.y + plot.h - 1;
        var hgt = value > 0 ? barH : 1;
        ctx.beginPath();
        ctx.moveTo(x - barW / 2, top + hgt);
        ctx.lineTo(x - barW / 2, top + radius);
        ctx.quadraticCurveTo(x - barW / 2, top, x - barW / 2 + radius, top);
        ctx.lineTo(x + barW / 2 - radius, top);
        ctx.quadraticCurveTo(x + barW / 2, top, x + barW / 2, top + radius);
        ctx.lineTo(x + barW / 2, top + hgt);
        ctx.closePath();
        ctx.fill();

        if (opts.valueLabels !== false && value > 0 && slot > 26) {
          ctx.fillStyle = CHART_COLORS.text;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'bottom';
          ctx.fillText(formatY(value), x, top - 2);
        }

        ctx.save();
        ctx.fillStyle = CHART_COLORS.text;
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        var text = String(label);
        var maxChars = Math.max(4, Math.floor(slot / 7));
        if (text.length > maxChars) text = text.slice(0, maxChars) + '…';
        ctx.translate(x + 4, plot.y + plot.h + 8);
        if (slot < 40 && labels.length > 6) {
          ctx.rotate(-Math.PI / 4);
          ctx.fillText(text, 0, 0);
        } else {
          ctx.textAlign = 'center';
          ctx.fillText(text, 0, 6);
        }
        ctx.restore();

        points.push({
          x: x,
          y: top,
          plotTop: plot.y,
          plotBottom: plot.y + plot.h,
          tip: '<div class="tip-title">' + esc(label) + '</div><div class="tip-row">' +
            esc((opts.tips && opts.tips[i]) || (formatY(value) + unit)) + '</div>',
        });
      });
      canvas.__redraw = draw;
      canvas.__plotTop = plot.y;
      canvas.__plotBottom = plot.y + plot.h;
    };
    draw();
    if (chartRegistry.indexOf(draw) < 0) chartRegistry.push(draw);
    var tipMeta = { points: points };


    canvas.addEventListener('mousemove', function (e) {
      var rect = canvas.getBoundingClientRect();
      var x = e.clientX - rect.left;
      var best = null;
      var bestDist = Infinity;
      (tipMeta.points || []).forEach(function (p) {
        var d = Math.abs(p.x - x);
        if (d < bestDist) { bestDist = d; best = p; }
      });
      if (!best || bestDist > Math.max((canvas.__slotW || 24) / 2, 14)) { tip.hidden = true; canvas.__redraw(); return; }
      tip.hidden = false;
      tip.innerHTML = best.tip;
      tip.style.left = Math.min(Math.max(best.x, 60), rect.width - 60) + 'px';
      tip.style.top = Math.max(28, best.y - 6) + 'px';
      canvas.__redraw();
      drawHighlight(canvas.getContext('2d'), { x: best.x, plotTop: canvas.__plotTop, plotBottom: canvas.__plotBottom });
    });
    canvas.addEventListener('mouseleave', function () { tip.hidden = true; canvas.__redraw(); });
  }

  /**
   * 折线图（可多序列、面积填充、数据点、hover tooltip）。
   * @param {HTMLElement} host
   * @param {{labels:string[], series:Array<{name:string,values:Array<number>,color?:string,fill?:boolean}>,
   *          height?:number, valueFormat?:Function, showLegend?:boolean, maxTicks?:number}} opts
   */
  function lineChart(host, opts) {
    host.innerHTML = '';
    var canvas = document.createElement('canvas');
    host.appendChild(canvas);
    var tip = makeTip(host);
    var labels = opts.labels || [];
    var series = (opts.series || []).map(function (s, i) {
      return { name: s.name || ('系列' + (i + 1)), values: (s.values || []).map(function (v) { return num(v, 0); }), color: s.color || chartPalette(i), fill: s.fill !== false && i === 0 };
    });
    var all = [];
    series.forEach(function (s) { all = all.concat(s.values); });
    var max = Math.max.apply(null, all.concat([0]));
    var min = Math.min.apply(null, all.concat([0]));
    if (max === min) max = min + 1;
    max = niceMax(max);
    if (min < 0) min = -niceMax(-min);
    var height = opts.height || 280;
    var formatY = opts.valueFormat || function (v) { return String(Math.round(v)); };
    var showLegend = opts.showLegend !== false && series.length > 1;

    var points = [];
    var draw = function () {
      var pf = prepareCanvas(canvas, height);
      var ctx = pf.ctx;
      var span = max - min || 1;
      var plot = drawAxes(ctx, pf.w, pf.h, {
        max: max,
        ticks: opts.maxTicks || 4,
        bottom: 44,
        left: 58,
        formatY: formatY,
      });
      // 若 min<0 需要额外画 0 线，简化处理：按 max 归一
      var n = Math.max(labels.length, 1);
      var slot = n > 1 ? plot.w / (n - 1) : plot.w;

      points = [];
      series.forEach(function (s) {
        ctx.beginPath();
        ctx.lineWidth = 2;
        ctx.strokeStyle = s.color;
        ctx.lineJoin = 'round';
        var coords = [];
        for (var i = 0; i < labels.length; i += 1) {
          var v = s.values[i] === undefined ? 0 : s.values[i];
          var x = n > 1 ? plot.x + slot * i : plot.x + plot.w / 2;
          var y = plot.y + plot.h - ((v - min) / span) * plot.h;
          coords.push({ x: x, y: y, v: v, label: labels[i] });
          if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        }
        ctx.stroke();

        if (s.fill && coords.length > 1) {
          ctx.beginPath();
          ctx.moveTo(coords[0].x, plot.y + plot.h);
          coords.forEach(function (c) { ctx.lineTo(c.x, c.y); });
          ctx.lineTo(coords[coords.length - 1].x, plot.y + plot.h);
          ctx.closePath();
          ctx.fillStyle = CHART_COLORS.area;
          ctx.fill();
        }
        coords.forEach(function (c) {
          ctx.beginPath();
          ctx.arc(c.x, c.y, 2.6, 0, Math.PI * 2);
          ctx.fillStyle = '#fff';
          ctx.fill();
          ctx.lineWidth = 1.6;
          ctx.strokeStyle = s.color;
          ctx.stroke();
        });
        s.coords = coords;
      });

      // 横轴标签（稀疏显示，避免重叠）
      ctx.font = '11px -apple-system, "Microsoft YaHei", sans-serif';
      ctx.fillStyle = CHART_COLORS.text;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'top';
      var step = Math.max(1, Math.ceil(labels.length / Math.max(3, Math.floor(plot.w / 56))));
      labels.forEach(function (label, i) {
        if (i % step !== 0 && i !== labels.length - 1) return;
        var x = n > 1 ? plot.x + slot * i : plot.x + plot.w / 2;
        ctx.fillText(String(label), x, plot.y + plot.h + 9);
      });

      // hover 命中点：以 x 的最近数据点聚合所有序列
      var hits = [];
      var base = (series[0] && series[0].coords) || [];
      base.forEach(function (c, i) {
        hits.push(i);
      });
      canvas.__hits = hits;
      canvas.__slotW = slot;
      canvas.__redraw = draw;
      canvas.__plotTop = plot.y;
      canvas.__plotBottom = plot.y + plot.h;
    };
    draw();
    if (chartRegistry.indexOf(draw) < 0) chartRegistry.push(draw);

    canvas.addEventListener('mousemove', function (e) {
      var rect = canvas.getBoundingClientRect();
      var x = e.clientX - rect.left;
      var base = (series[0] && series[0].coords) || [];
      if (!base.length) return;
      var bestIndex = 0;
      var bestDist = Infinity;
      base.forEach(function (c, i) {
        var d = Math.abs(c.x - x);
        if (d < bestDist) { bestDist = d; bestIndex = i; }
      });
      var best = base[bestIndex];
      if (!best || bestDist > Math.max(canvas.__slotW / 2, 16)) { tip.hidden = true; canvas.__redraw(); return; }
      var html = '<div class="tip-title">' + esc(best.label) + '</div>';
      series.forEach(function (s) {
        var c = s.coords && s.coords[bestIndex];
        var v = c ? c.v : (s.values[bestIndex] || 0);
        html += '<div class="tip-row" style="color:' + esc(s.color) + '">' + esc(s.name) + '：' +
          esc(formatY(v)) + '</div>';
      });
      tip.hidden = false;
      tip.innerHTML = html;
      tip.style.left = Math.min(Math.max(best.x, 70), rect.width - 70) + 'px';
      tip.style.top = Math.max(30, best.y - 6) + 'px';
      canvas.__redraw();
      drawHighlight(canvas.getContext('2d'), { x: best.x, plotTop: canvas.__plotTop, plotBottom: canvas.__plotBottom });
    });
    canvas.addEventListener('mouseleave', function () { tip.hidden = true; canvas.__redraw(); });

    if (showLegend) {
      var legend = document.createElement('div');
      legend.className = 'chart-legend';
      legend.innerHTML = series.map(function (s) {
        return '<span><i style="background:' + esc(s.color) + '"></i>' + esc(s.name) + '</span>';
      }).join('');
      host.appendChild(legend);
    }
  }

  function niceMax(value) {
    if (value <= 0) return 1;
    var exp = Math.floor(Math.log(value) / Math.LN10);
    var base = Math.pow(10, exp);
    var norm = value / base;
    var step;
    if (norm <= 1) step = 1;
    else if (norm <= 2) step = 2;
    else if (norm <= 2.5) step = 2.5;
    else if (norm <= 5) step = 5;
    else step = 10;
    return step * base;
  }

  window.addEventListener('resize', function () {
    chartRegistry.forEach(function (fn) { try { fn(); } catch (e) { /* 忽略 */ } });
  });

  /* ====================================================================== */
  /* 6. 各页面                                                              */
  /* ====================================================================== */

  var viewEl = function () { return document.getElementById('view'); };

  /** 页面通用装载：显示 loading，捕获异常 */
  function mountView(host) {
    host.innerHTML = '<div class="loading-block">加载中…</div>';
  }

  function setPageHeader(title, desc) {
    var t = document.getElementById('page-title');
    var d = document.getElementById('page-desc');
    if (t) t.textContent = title;
    if (d) d.textContent = desc || '';
  }

  function requirePermOrWarn(perm) {
    if (hasPerm(perm)) return true;
    viewEl().innerHTML = noticeBar('<strong>没有权限</strong>：当前账号缺少 <code class="inline">' + esc(perm) + '</code>，无法查看该页面。', 'warn');
    return false;
  }

  /* ------------------------------ 数据概览 ------------------------------ */

  async function renderDashboard(host) {
    if (!hasPerm('revenue.read')) { requirePermOrWarn('revenue.read'); return; }
    setPageHeader('数据概览', '平台今日与累计经营指标、任务成功率与运行环境');
    mountView(host);

    var res = await apiGet('/revenue/dashboard');
    if (res.code !== 0) {
      host.innerHTML = noticeBar('加载失败：' + esc(res.msg || ''), 'danger');
      return;
    }
    var d = res.data || {};
    var today = d.today || {};
    var total = d.total || {};
    var tasks = d.tasks || {};
    var routes = d.routes || [];

    var rate = tasks.success_rate;
    var rateCls = rate === null || rate === undefined ? '' : (Number(rate) < 80 ? 'danger' : 'ok');

    var html = '';
    html += '<div class="stat-grid">' +
      statCard('今日订单数', fmtInt(today.order_count), '付费用户 ' + fmtInt(today.user_count) + ' 人', 'primary') +
      statCard('今日成交额', '¥ ' + fmtMoney(today.gross), '净收入 ¥ ' + fmtMoney(today.net), 'ok') +
      statCard('累计订单数', fmtInt(total.order_count), null) +
      statCard('累计成交额', '¥ ' + fmtMoney(total.gross), '累计净收入 ¥ ' + fmtMoney(total.net), 'ok') +
      statCard('活跃用户', fmtInt(d.active_users), '状态为正常的终端账号') +
      statCard('今日任务成功率', rate === null || rate === undefined ? '无任务' : rate + '%',
        '任务 ' + fmtInt(tasks.count) + ' 个 / 成功 ' + fmtInt(tasks.success) + ' 个', rateCls) +
      '</div>';

    html += '<div class="card"><div class="card-head"><h3>各线路成功率（1 小时）</h3>' +
      '<span class="card-sub">低于 80% 标红，需立即检查线路</span></div>';
    if (!routes.length) {
      html += emptyState('暂无启用的线路', '请到「任务与线路」页面检查线路配置');
    } else {
      html += '<div class="table-wrap"><table class="tbl"><thead><tr>' +
        '<th>平台</th><th>1 小时成功</th><th>1 小时失败</th><th>成功率</th><th>健康状态</th>' +
        '</tr></thead><tbody>' + routes.map(function (r) {
          var bad = r.rate !== null && r.rate !== undefined && Number(r.rate) < 80;
          return '<tr' + (bad ? ' class="row-urgent"' : '') + '>' +
            '<td>' + esc(r.platform || '-') + '</td>' +
            '<td class="num">' + fmtInt(r.succ) + '</td>' +
            '<td class="num">' + fmtInt(r.fail) + '</td>' +
            '<td>' + rateTag(r.rate) + '</td>' +
            '<td>' + (bad ? tag('需处理', 'tag-danger') : tag('正常', 'tag-ok')) + '</td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';
    }
    html += '</div>';

    html += '<div class="card" id="env-card"><div class="card-head"><h3>运行环境</h3>' +
      '<div class="card-actions"><button type="button" class="btn btn-sm" data-env-reload="1">刷新环境</button></div></div>' +
      '<div class="card-body" id="env-body"><div class="loading-block">读取中…</div></div></div>';

    host.innerHTML = html;

    var loadEnv = async function () {
      var body = document.getElementById('env-body');
      if (!body) return;
      var envRes = await apiGet('/panel/env');
      if (envRes.code !== 0) {
        body.innerHTML = '<div class="inline-hint">环境信息读取失败：' + esc(envRes.msg || '') + '</div>';
        return;
      }
      var e = envRes.data || {};
      var media = e.media || {};
      var queue = e.queue || {};
      var mem = e.memory || {};
      var ffmpeg = media.ffmpeg || media.ffmpeg_path || media.ffmpeg_ok;
      var fonts = media.font || media.font_path || media.fonts;
      var backlog = num(queue.backlog, 0);

      var rows = [
        ['Node 版本', esc(e.node || '-')],
        ['操作系统', esc(e.platform || '-')],
        ['CPU 核数', esc(String(e.cpu_count === undefined ? '-' : e.cpu_count))],
        ['负载 (1/5/15 分钟)', esc((e.load_avg || []).map(function (n) { return Math.round(Number(n) * 100) / 100; }).join(' / ') || '-')],
        ['内存', esc('总 ' + fmtBytes(mem.total) + ' / 空闲 ' + fmtBytes(mem.free) + ' / 进程 ' + fmtBytes(mem.rss))],
        ['运行时长', esc(fmtDuration(e.uptime_sec))],
        ['服务器时间', esc(fmtTime(e.server_time))],
        ['FFmpeg', ffmpeg === undefined || ffmpeg === null ? tag('未探测到', 'tag-danger') : (ffmpeg === true || ffmpeg === 'ok' ? tag('可用', 'tag-ok') : tag(String(ffmpeg), 'tag-ok'))],
        ['字体', fonts === undefined || fonts === null ? tag('未探测到', 'tag-warn') : (fonts === true ? tag('可用', 'tag-ok') : tag(String(fonts), 'tag-ok'))],
        ['队列积压', (backlog > 50 ? tag(String(backlog) + '（超阈值 50，需告警）', 'tag-danger') : tag(String(backlog), 'tag-ok')) +
          ' <span class="inline-hint">排队 ' + esc(String(queue.queued === undefined ? '-' : queue.queued)) +
          ' / 运行 ' + esc(String(queue.running === undefined ? '-' : queue.running)) +
          ' / KV 高优先 ' + esc(String(queue.kv_high === undefined ? '-' : queue.kv_high)) +
          ' / KV 普通 ' + esc(String(queue.kv_normal === undefined ? '-' : queue.kv_normal)) + '</span>'],
      ];
      var extra = JSON.stringify(media, null, 2);
      body.innerHTML = '<dl class="dl">' + rows.map(function (r) {
        return '<dt>' + esc(r[0]) + '</dt><dd>' + r[1] + '</dd>';
      }).join('') + '</dl>' +
        '<details style="margin-top:10px"><summary class="btn-link">查看媒体探测原始数据</summary>' +
        '<pre class="code">' + esc(extra) + '</pre></details>';
    };

    await loadEnv();
    var reloadBtn = $('[data-env-reload]', host);
    if (reloadBtn) reloadBtn.addEventListener('click', loadEnv);
  }

  /* ------------------------------ 账号管理 ------------------------------ */

  var userState = {
    page: 1, limit: 20, sort: 'created_at', order: 'desc',
    keyword: '', status: '', level: '',
    start_date: '', end_date: '', expire_start: '', expire_end: '',
    min_amount: '', max_amount: '',
  };
  var userFilterFields = [];

  async function renderUser(host) {
    if (!hasPerm('user.read')) { requirePermOrWarn('user.read'); return; }
    setPageHeader('账号管理', '终端账号列表、时长管理、设备解绑与批量操作');

    userFilterFields = [
      function (s) { return textFilter('keyword', '关键词', s.keyword, '用户名 / 手机号 / 备注', 'wide'); },
      function (s) { return selectFilter('status', '状态', optionsFrom(USER_STATUS, '全部状态'), s.status); },
      function (s) { return selectFilter('level', '等级', optionsFrom(LEVEL_TEXT, '全部等级'), s.level); },
      function (s) { return dateFilter('start_date', '注册起', s.start_date); },
      function (s) { return dateFilter('end_date', '注册止', s.end_date); },
      function (s) { return dateFilter('expire_start', '到期起', s.expire_start); },
      function (s) { return dateFilter('expire_end', '到期止', s.expire_end); },
      function (s) { return textFilter('min_amount', '金额≥', s.min_amount, '0.00', 'amount'); },
      function (s) { return textFilter('max_amount', '金额≤', s.max_amount, '0.00', 'amount'); },
    ];
    userFilterFields.forEach(function (f) {
      if (!f.reset) f.reset = function (s) { /* 由字段自身 key 处理 */ };
    });
    // 重置逻辑：按字段 key 清空
    userFilterFields.forEach(function (f, i) {
      f.reset = function (s) {
        var keys = [['keyword'], ['status'], ['level'], ['start_date'], ['end_date'], ['expire_start'], ['expire_end'], ['min_amount'], ['max_amount']][i];
        keys.forEach(function (k) { s[k] = ''; });
      };
    });

    var reload = function () { loadUsers(host, reload); };
    loadUsers(host, reload);
  }

  async function loadUsers(host, reload) {
    renderTable(host, {
      title: '终端账号', state: userState, fields: userFilterFields, loading: true,
      columns: [], rows: [], reload: reload,
      headActions: function () {
        var btns = '';
        if (hasPerm('user.write')) btns += '<button type="button" class="btn btn-primary btn-sm" data-act="add">添加用户</button>';
        if (hasPerm(['user.read', 'user.export'])) btns += '<button type="button" class="btn btn-sm" data-act="export">导出 CSV</button>';
        return btns;
      },
    });
    var res = await apiGet('/users', userState, { silent: true });
    if (res.code !== 0) {
      renderTable(host, {
        title: '终端账号', state: userState, fields: userFilterFields, error: res.msg,
        columns: [], rows: [], reload: reload, total: 0, headActions: '',
      });
      return;
    }
    var data = res.data || {};
    var rows = data.rows || [];

    renderTable(host, {
      title: '终端账号',
      subtitle: '剩余不足 3 天的账号整行标红，排序可点击表头',
      state: userState,
      fields: userFilterFields,
      columns: [
        { key: 'id', title: 'ID', sortable: true, className: 'num' },
        { key: 'username', title: '用户名', sortable: true, render: function (r) { return esc(r.username || '-'); } },
        { key: 'phone', title: '手机号', sortable: true, render: function (r) { return esc(r.phone || r.phone_masked || '-'); } },
        { key: 'remark', title: '备注', className: 'wrap', render: function (r) { return esc(r.remark || '-'); } },
        { key: 'level', title: '等级', render: function (r) { return levelTag(r.level); } },
        { key: 'status', title: '账号状态', render: function (r) { return userStatusTag(r); } },
        {
          key: 'activation', title: '会员状态',
          render: function (r) {
            var s = effectiveUserStatus(r);
            var hint = s === 0 ? '未开通，客户端显示水印遮罩' : (s === 2 ? '已到期，可再次激活' : '');
            return activationTag(r) + (hint ? ' <span class="inline-hint">' + esc(hint) + '</span>' : '');
          },
        },
        { key: 'bind_at', title: '开通时间', render: function (r) { return esc(fmtTime(r.bind_at)); } },
        {
          key: 'expire_at', title: '到期时间', sortable: true,
          render: function (r) {
            var left = r.days_left === undefined || r.days_left === null ? dayDiff(r.expire_at) : Number(r.days_left);
            var extra = left === null ? '' : (left < 0 ? '（已过期）' : '（剩 ' + left + ' 天）');
            return esc(fmtTime(r.expire_at)) + (extra ? ' <span class="inline-hint">' + esc(extra) + '</span>' : '');
          },
        },
        { key: 'total_amount', title: '累计消费', sortable: true, align: 'right', render: function (r) { return '<span class="money">' + esc(fmtMoney(r.total_amount)) + '</span>'; } },
        { key: 'total_task', title: '累计任务', render: function (r) { return '<span class="num">' + fmtInt(r.total_task) + '</span>'; } },
        { key: 'created_at', title: '注册时间', sortable: true, render: function (r) { return esc(fmtTime(r.created_at)); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) {
            var id = esc(r.id);
            var out = '';
            if (hasPerm('user.write')) {
              // 开通会员：客户端的水印与成品水印都由它决定，做成最显眼的行内操作
              out += '<button type="button" class="btn-link accent" data-op="activate" data-id="' + id + '">开通会员</button>';
            }
            out += '<button type="button" class="btn-link" data-op="detail" data-id="' + id + '">详情</button>';
            if (hasPerm('user.write')) {
              out += '<button type="button" class="btn-link" data-op="duration" data-id="' + id + '">时长</button>';
              if (Number(r.status_effective) === 3) {
                out += '<button type="button" class="btn-link" data-op="unban" data-id="' + id + '">解封</button>';
              } else {
                out += '<button type="button" class="btn-link danger" data-op="ban" data-id="' + id + '">封禁</button>';
              }
              out += '<button type="button" class="btn-link" data-op="unbind" data-id="' + id + '">解绑设备</button>';
              out += '<button type="button" class="btn-link" data-op="resetpw" data-id="' + id + '">重置密码</button>';
              out += '<button type="button" class="btn-link" data-op="revoke" data-id="' + id + '">强制下线</button>';
            }
            if (hasPerm('user.delete')) {
              out += '<button type="button" class="btn-link danger" data-op="delete" data-id="' + id + '">删除</button>';
            }
            return out;
          },
        },
      ],
      rows: rows,
      total: data.total || 0,
      checkbox: hasPerm('user.write'),
      rowKey: function (r) { return r.id; },
      rowClass: function (r) {
        var left = r.days_left === undefined || r.days_left === null ? dayDiff(r.expire_at) : Number(r.days_left);
        return left !== null && left !== undefined && left >= 0 && left < 3 ? 'row-urgent' : '';
      },
      empty: '没有符合条件的账号',
      headActions: function () {
        var btns = '';
        if (hasPerm('user.write')) {
          btns += '<button type="button" class="btn btn-sm" data-act="batch-extend">批量延期</button>';
          btns += '<button type="button" class="btn btn-sm" data-act="batch-tag">批量打标签</button>';
          btns += '<button type="button" class="btn btn-sm" data-act="batch-status">批量改状态</button>';
          btns += '<button type="button" class="btn btn-primary btn-sm" data-act="add">添加用户</button>';
        }
        if (hasPerm(['user.read', 'user.export'])) btns += '<button type="button" class="btn btn-sm" data-act="export">导出 CSV</button>';
        return btns;
      },
      reload: reload,
      onRowMount: function (container) {
        $$('[data-op]', container).forEach(function (btn) {
          btn.addEventListener('click', function () {
            var id = Number(btn.getAttribute('data-id'));
            var row = rows.filter(function (r) { return Number(r.id) === id; })[0];
            userRowAction(btn.getAttribute('data-op'), id, row, reload);
          });
        });
        var add = $('[data-act="add"]', container);
        if (add) add.addEventListener('click', function () { addUserDialog(reload); });
        var exp = $('[data-act="export"]', container);
        if (exp) exp.addEventListener('click', function () {
          download('/users/export', userState, 'users-' + nowDateStr() + '.csv');
        });
        var be = $('[data-act="batch-extend"]', container);
        if (be) be.addEventListener('click', function () { batchExtend(reload); });
        var bt = $('[data-act="batch-tag"]', container);
        if (bt) bt.addEventListener('click', function () { batchTag(reload); });
        var bs = $('[data-act="batch-status"]', container);
        if (bs) bs.addEventListener('click', function () { batchStatus(reload); });
      },
    });
  }

  async function userRowAction(op, id, row, reload) {
    if (op === 'detail') { openUserDrawer(id, reload); return; }
    if (op === 'activate') { await activateUserDialog(id, row, reload); return; }

    if (op === 'duration') {
      var values = await promptDialog({
        title: '管理用户时长 · ' + (row ? row.username : '#' + id),
        intro: '可以按天数顺延，也可以直接指定到期时间。同时可调整等级 / 设备数 / 每日配额。',
        fields: [
          { key: 'days', label: '顺延天数', type: 'number', placeholder: '例如 30（可为负数以扣减）' },
          { key: 'expire_at', label: '指定到期时间', type: 'datetime-local', note: '填写后忽略顺延天数，直接设为该时间' },
          { key: 'level', label: '等级', type: 'select', value: row ? row.level : 1, options: Object.keys(LEVEL_TEXT).map(function (k) { return { value: k, label: LEVEL_TEXT[k] }; }) },
          { key: 'max_device', label: '绑定设备数', type: 'number', value: row ? row.max_device : 1 },
          { key: 'quota_day', label: '每日配额', type: 'number', value: row ? row.quota_day : 20 },
        ],
        confirmText: '保存',
      });
      if (!values) return;
      var body = {};
      if (values.days !== null && values.days !== '' && Number(values.days) !== 0) body.days = Number(values.days);
      if (values.expire_at) body.expire_at = values.expire_at;
      if (!body.days && !body.expire_at) { toast('请填写顺延天数或指定到期时间', 'warn'); return; }
      if (values.level !== '' && values.level !== null) body.level = Number(values.level);
      if (values.max_device !== '' && values.max_device !== null) body.max_device = Number(values.max_device);
      if (values.quota_day !== '' && values.quota_day !== null) body.quota_day = Number(values.quota_day);
      var res = await apiPost('/users/' + id + '/duration', body);
      if (res.code === 0) { toast('时长已更新', 'ok'); reload(); }
      return;
    }

    if (op === 'ban') {
      var confirm = await confirmDialog('确认<b>封禁</b>账号 <b>' + esc(row ? row.username : id) + '</b>？该账号所有会话会被立即吊销。', {
        title: '封禁账号（二次确认）', confirmText: '确认封禁', needPassword: true,
      });
      if (!confirm || !confirm.ok) return;
      var banRes = await apiPost('/users/' + id + '/status', { status: 3, password: confirm.password });
      if (banRes.code === 0) { toast('已封禁', 'ok'); reload(); }
      return;
    }

    if (op === 'unban') {
      var un = await confirmDialog('确认解封账号 <b>' + esc(row ? row.username : id) + '</b>？', {
        title: '解封账号', confirmText: '确认解封', danger: false,
      });
      if (!un) return;
      var unRes = await apiPost('/users/' + id + '/status', { status: 1 });
      if (unRes.code === 0) { toast('已解封', 'ok'); reload(); }
      return;
    }

    if (op === 'resetpw') {
      var pw = await promptDialog({
        title: '重置用户密码 · ' + (row ? row.username : '#' + id),
        intro: '重置后该用户需重新登录，且所有在线会话会被吊销。',
        fields: [{ key: 'password', label: '新密码（至少 6 位）', required: true, placeholder: '请输入新密码' }],
        confirmText: '重置密码', danger: true,
      });
      if (!pw) return;
      var pwRes = await apiPost('/users/' + id + '/reset-password', { password: pw.password });
      if (pwRes.code === 0) { toast('密码已重置', 'ok'); reload(); }
      return;
    }

    if (op === 'revoke') {
      var rv = await confirmDialog('确认让账号 <b>' + esc(row ? row.username : id) + '</b> 的所有会话立即下线？', {
        title: '强制下线', confirmText: '强制下线', danger: false,
      });
      if (!rv) return;
      var rvRes = await apiPost('/users/' + id + '/revoke', {});
      if (rvRes.code === 0) { toast('已强制下线', 'ok'); reload(); }
      return;
    }

    if (op === 'unbind') {
      var dev = await promptDialog({
        title: '解绑设备 · ' + (row ? row.username : '#' + id),
        fields: [{ key: 'device_id', label: '设备 ID', required: true, mono: true, placeholder: '设备指纹（可在用户详情里复制）' }],
        confirmText: '解绑',
      });
      if (!dev) return;
      var devRes = await apiPost('/users/' + id + '/unbind', { device_id: dev.device_id });
      if (devRes.code === 0) { toast('已解绑', 'ok'); reload(); }
      return;
    }

    if (op === 'delete') {
      var del = await confirmDialog('确认<b>删除</b>账号 <b>' + esc(row ? row.username : id) + '</b>？用户数据、会话、设备绑定都会被清除，且不可恢复。' +
        '<br><br><b>该账号用过的邀请码会自动释放为「未使用」</b>，也就是说对方可以拿同一个码重新注册。' +
        '<br><span class="inline-hint">如果不想让本人重新注册，删除后请到「邀请码管理」把那个码作废。</span>', {
        title: '删除用户（二次确认）', confirmText: '永久删除', needPassword: true,
        warning: '删除用户不可撤销，请输入当前管理员登录密码确认。',
      });
      if (!del || !del.ok) return;
      var delRes = await apiDelete('/users/' + id, { password: del.password });
      if (delRes.code === 0) { toast(delRes.msg || '已删除', 'ok'); reload(); }
      return;
    }
  }

  /**
   * 「开通会员」：POST /admin/api/users/:id/activate
   * 新模型下唯一的开通方式（客户端已没有卡密激活入口），days 必须是非 0 数字。
   */
  async function activateUserDialog(id, row, reload) {
    var values = await promptDialog({
      title: '开通会员 · ' + (row ? (row.username || ('#' + id)) : ('#' + id)),
      intro: '<b>开通后客户端的水印遮罩会消失，解析出来的成品也不再带水印。</b>' +
        '未开通会员的账号本来就能正常使用（只是带水印），所以这里是一次「去水印」的开通。' +
        '开通天数<b>不能为 0</b>；账号仍在会员期内时按天数<b>顺延</b>，已到期则从当前时间重新计算。',
      fields: [
        { key: 'days', label: '开通天数', type: 'number', required: true, value: '30', note: '必填且不能为 0' },
        {
          key: 'level', label: '等级（留空不修改）', type: 'select',
          value: row ? row.level : 1,
          options: Object.keys(LEVEL_TEXT).map(function (k) { return { value: k, label: LEVEL_TEXT[k] }; }),
        },
        { key: 'quota_day', label: '每日配额（留空不修改）', type: 'number', value: row ? row.quota_day : 20 },
        { key: 'max_device', label: '绑定设备数（留空不修改）', type: 'number', value: row ? row.max_device : 1 },
        { key: 'remark', label: '备注', placeholder: '例如：微信补开 / 工单 #123', full: true },
      ],
      confirmText: '确认激活',
    });
    if (!values) return;
    var days = Number(values.days);
    if (!isFinite(days) || days === 0) { toast('开通天数必须是非 0 数字（天）', 'warn'); return; }
    var body = { days: days };
    if (values.level !== '' && values.level !== null) body.level = Number(values.level);
    if (values.quota_day !== '' && values.quota_day !== null) body.quota_day = Number(values.quota_day);
    if (values.max_device !== '' && values.max_device !== null) body.max_device = Number(values.max_device);
    if (values.remark) body.remark = values.remark;
    var res = await apiPost('/users/' + id + '/activate', body);
    if (res.code === 0) { toast(res.msg || '已激活', 'ok'); reload(); }
  }

  async function batchExtend(reload) {
    var ids = selectedIds();
    if (!ids.length) { toast('请先勾选用户', 'warn'); return; }
    var values = await promptDialog({
      title: '批量延期（' + ids.length + ' 个账号）',
      fields: [{ key: 'days', label: '顺延天数', type: 'number', required: true, placeholder: '例如 30' }],
      confirmText: '执行延期',
    });
    if (!values) return;
    var res = await apiPost('/users/batch', { action: 'extend', ids: ids, days: Number(values.days) });
    if (res.code === 0) { toast(res.msg || '已处理', 'ok'); reload(); }
  }

  async function batchTag(reload) {
    var ids = selectedIds();
    if (!ids.length) { toast('请先勾选用户', 'warn'); return; }
    var values = await promptDialog({
      title: '批量打标签（' + ids.length + ' 个账号）',
      intro: '备注会整体覆盖，请谨慎填写。',
      fields: [{ key: 'remark', label: '备注内容', type: 'textarea', rows: 3, placeholder: '例如：渠道A-2026春季' }],
      confirmText: '保存标签',
    });
    if (!values) return;
    var res = await apiPost('/users/batch', { action: 'tag', ids: ids, remark: values.remark || '' });
    if (res.code === 0) { toast(res.msg || '已处理', 'ok'); reload(); }
  }

  async function batchStatus(reload) {
    var ids = selectedIds();
    if (!ids.length) { toast('请先勾选用户', 'warn'); return; }
    var values = await promptDialog({
      title: '批量改状态（' + ids.length + ' 个账号）',
      fields: [{
        key: 'status', label: '目标状态', type: 'select', value: '1',
        options: Object.keys(USER_STATUS).map(function (k) { return { value: k, label: USER_STATUS[k] }; }),
      }],
      confirmText: '执行',
    });
    if (!values) return;
    if (Number(values.status) === 3) {
      var confirm = await confirmDialog('批量封禁 ' + ids.length + ' 个账号？所有会话会被吊销。', {
        title: '批量封禁（二次确认）', needPassword: true, confirmText: '确认封禁',
      });
      if (!confirm || !confirm.ok) return;
      var banRes = await apiPost('/users/batch', { action: 'status', ids: ids, status: 3, password: confirm.password });
      if (banRes.code === 0) { toast(banRes.msg || '已处理', 'ok'); reload(); }
      return;
    }
    var res = await apiPost('/users/batch', { action: 'status', ids: ids, status: Number(values.status) });
    if (res.code === 0) { toast(res.msg || '已处理', 'ok'); reload(); }
  }

  /** 添加用户弹窗（对应后端 POST /users 的全部入参） */
  function addUserDialog(reload) {
    var html =
      noticeBar('后台创建的账号是<b>直接开通</b>的（不需要邀请码/卡密激活）；手机号（或用户名）必填，' +
        '<b>到期时间与开通时长至少填一个</b>。', 'info') +
      '<div class="form-grid">' +
        formField('备注', inputHtml('remark', '', { placeholder: '后台备注，便于检索' })) +
        formField('手机号', inputHtml('phone', '', { placeholder: '11 位手机号' })) +
        formField('用户名', inputHtml('username', '', { placeholder: '留空则用手机号' })) +
        formField('归属渠道', inputHtml('channel', 'admin', { placeholder: '默认 admin' })) +
        formField('到期时间（指定）', inputHtml('expire_at', '', { type: 'datetime-local' }), { note: '填写后忽略时长' }) +
        formField('开通时长（天）', inputHtml('days', '30', { type: 'number' }), { note: '0 天且未指定到期时间会报错' }) +
        formField('用户等级', selectHtml('level', Object.keys(LEVEL_TEXT).map(function (k) { return { value: k, label: LEVEL_TEXT[k] }; }), 1)) +
        formField('绑定设备数', inputHtml('max_device', '1', { type: 'number' })) +
        formField('每日配额', inputHtml('quota_day', '20', { type: 'number' })) +
        '<div class="span-2" style="display:flex;gap:20px;flex-wrap:wrap">' +
          checkboxHtml('create_pay_order', '接入支付接口（创建待支付订单）', false) +
        '</div>' +
        formField('支付方式（创建待支付订单时）', selectHtml('pay_type', [
          { value: 'alipay', label: '支付宝' }, { value: 'wxpay', label: '微信支付' }, { value: 'qqpay', label: 'QQ 钱包' },
        ], 'alipay')) +
      '</div>';

    openModal({
      title: '添加用户',
      size: 'modal-lg',
      bodyHtml: html,
      confirmText: '创建',
      onConfirm: async function (mask) {
        var f = readFields(mask);
        if (!f.username && !f.phone) { toast('手机号（或用户名）必填', 'warn'); return false; }
        if (!f.expire_at && !Number(f.days)) { toast('请指定到期时间或开通时长', 'warn'); return false; }
        var body = {
          remark: f.remark || '', phone: f.phone || '', username: f.username || '',
          channel: f.channel || 'admin', level: Number(f.level || 1),
          max_device: Number(f.max_device || 1), quota_day: Number(f.quota_day || 20),
          create_pay_order: !!f.create_pay_order,
        };
        if (f.expire_at) body.expire_at = f.expire_at;
        else body.days = Number(f.days);
        if (f.create_pay_order) body.pay_type = f.pay_type || 'alipay';
        var res = await apiPost('/users', body);
        if (res.code !== 0) return false;
        var data = res.data || {};
        toast(res.msg || '创建成功', 'ok');
        if (data.order && data.order.order_no) {
          toast('待支付订单：' + data.order.order_no + '，金额 ' + fmtMoney(data.order.amount), 'warn', '订单已创建');
        }
        reload();
        return true;
      },
    });
  }

  /** 用户详情抽屉：GET /users/:id */
  async function openUserDrawer(id, reload) {
    var res = await apiGet('/users/' + id);
    if (res.code !== 0) return;
    var d = res.data || {};
    var u = d.user || {};
    var activation = u.activation || {};
    var invites = d.invites || [];
    var cards = d.cards || [];
    var devices = d.devices || [];
    var tasks = d.tasks || [];
    var orders = d.orders || [];
    var logs = d.profile_logs || [];
    var sessions = d.sessions || [];
    var tickets = d.tickets || [];

    var left = dayDiff(u.expire_at);
    var body = '';

    body += '<div class="card"><div class="card-head"><h3>账号信息</h3>' +
      '<div class="card-actions">' + userStatusTag(u) + levelTag(u.level) + '</div></div><div class="card-body">' +
      '<div class="dl dl-2">' +
      dlItem('ID', u.id) + dlItem('用户名', u.username) + dlItem('手机号', u.phone) +
      dlItem('昵称', u.nickname) + dlItem('备注', u.remark) + dlItem('邮箱', u.email) +
      dlItemRaw('会员状态', activation.activated ? tag('已开通', 'tag-ok') : tag('未开通', 'tag-warn')) +
      dlItem('剩余天数', activation.expire_days === null || activation.expire_days === undefined ? '-' : (activation.expire_days + ' 天')) +
      dlItem('客户端水印', activation.activated ? '不显示水印遮罩 / 成品免水印' : '显示水印遮罩 / 成品带水印（可在后台开通会员）') +
      dlItem('开通时间', fmtTime(u.bind_at)) +
      dlItem('到期时间', fmtTime(u.expire_at) + (left === null ? '' : '（剩余 ' + left + ' 天）')) +
      dlItem('绑定设备上限', u.max_device) + dlItem('每日配额', u.quota_day) +
      dlItem('累计消费', '¥ ' + fmtMoney(u.total_amount)) + dlItem('累计任务', u.total_task) +
      dlItem('注册 IP', u.reg_ip) + dlItem('注册渠道', u.reg_channel) +
      dlItem('注册时间', fmtTime(u.created_at)) +
      '</div></div></div>';

    body += tableCard('来源邀请码（该账号由哪张邀请码创建）', ['邀请码', '批次号', '时长语义', '等级', '状态', '使用时间'],
      invites.map(function (i) {
        return ['<code class="inline">' + esc(i.display || i.code || '-') + '</code>', esc(i.batch_no || '-'),
          inviteDurationTag(i.duration), levelTag(i.level), inviteStatusTag(i), esc(fmtTime(i.used_at))];
      }), '该账号不是通过邀请码注册的（后台直接创建）');

    body += tableCard('历史卡密（只读，卡密体系已下线）', ['卡密', '批次', '名称', '时长', '面值', '状态', '使用时间'],
      cards.map(function (c) {
        return [esc(c.code), esc(c.batch_no || '-'), esc(c.name || c.batch_name || '-'),
          esc(Number(c.duration) === 0 ? '永久' : (c.duration + ' 天')), '¥ ' + esc(fmtMoney(c.price)),
          tag(CARD_STATUS[Number(c.status)] || '-', CARD_STATUS_CLASS[Number(c.status)] || 'tag-gray'), esc(fmtTime(c.used_at))];
      }));

    body += tableCard('绑定设备', ['设备 ID', '品牌', '型号', '系统', '状态', '最近活跃', '首次绑定'],
      devices.map(function (v) {
        return ['<code class="inline">' + esc(v.device_id || '') + '</code>', esc(v.brand || '-'), esc(v.model || '-'),
          esc(v.os_version || '-'), boolTag(Number(v.status) === 1, '启用', '已停用'),
          esc(fmtTime(v.last_active || v.last_seen)), esc(fmtTime(v.created_at))];
      }), '暂无绑定设备');

    body += tableCard('最近任务', ['任务号', '平台', '状态', '耗时', '创建时间'],
      tasks.map(function (t) {
        return ['<span class="num">' + esc(t.task_no || '') + '</span>', esc(t.platform_name || t.platform || '-'),
          tag(t.status_text || TASK_STATUS[Number(t.status)] || '-', TASK_STATUS_CLASS[Number(t.status)] || 'tag-gray'),
          esc(t.duration_ms ? Math.round(Number(t.duration_ms) / 100) / 10 + ' 秒' : '-'), esc(fmtTime(t.created_at))];
      }), '暂无任务');

    body += tableCard('最近订单', ['订单号', '商品', '实付', '状态', '支付时间'],
      orders.map(function (o) {
        return ['<span class="num">' + esc(o.order_no || '') + '</span>', esc(o.goods_name || '-'),
          '¥ ' + esc(fmtMoney(o.amount)), orderStatusTag(o), esc(fmtTime(o.paid_at))];
      }), '暂无订单');

    body += tableCard('会话（License Session）', ['设备 ID', 'IP', '客户端版本', '状态', '最近活跃', '过期时间'],
      sessions.map(function (s) {
        return ['<code class="inline">' + esc(s.device_id || '') + '</code>', esc(s.ip || '-'), esc(s.app_version || '-'),
          boolTag(Number(s.revoked) === 0, '有效', '已吊销'), esc(fmtTime(s.last_seen)), esc(fmtTime(s.expire_at))];
      }), '暂无会话');

    body += tableCard('资料变更记录', ['时间', '字段', '旧值', '新值'],
      logs.map(function (g) {
        return [esc(fmtTime(g.created_at)), esc(g.field || '-'),
          '<span class="inline-hint">' + esc(truncate(g.old_value || '-', 60)) + '</span>',
          '<span class="inline-hint">' + esc(truncate(g.new_value || '-', 60)) + '</span>'];
      }), '暂无变更记录');

    if (tickets.length) {
      body += tableCard('关联工单', ['工单号', '标题', '状态', '创建时间'],
        tickets.map(function (t) {
          return ['<span class="num">' + esc(t.ticket_no || '') + '</span>', esc(t.title || '-'),
            tag(TICKET_STATUS[Number(t.status)] || '-', TICKET_STATUS_CLASS[Number(t.status)] || 'tag-gray'), esc(fmtTime(t.created_at))];
        }));
    }

    var footer = '';
    if (hasPerm('user.write')) {
      footer += '<button type="button" class="btn btn-primary btn-sm" data-dr="activate">开通会员</button>';
      footer += '<button type="button" class="btn btn-sm" data-dr="duration">管理时长</button>';
      footer += '<button type="button" class="btn btn-sm" data-dr="resetpw">重置密码</button>';
      footer += '<button type="button" class="btn btn-sm" data-dr="revoke">强制下线</button>';
    }
    if (hasPerm('user.delete')) footer += '<button type="button" class="btn btn-sm btn-danger" data-dr="delete">删除用户</button>';
    footer += '<button type="button" class="btn btn-sm" data-close="1">关闭</button>';

    openDrawer({
      title: '用户详情 · ' + (u.username || ('#' + id)),
      subtitle: (u.phone || '无手机号') + ' · 注册于 ' + fmtTime(u.created_at),
      bodyHtml: body,
      footerHtml: footer,
      onMount: function (mask, close) {
        $$('[data-dr]', mask).forEach(function (btn) {
          btn.addEventListener('click', async function () {
            var op = btn.getAttribute('data-dr');
            if (op === 'unbind') return;
            close();
            await userRowAction(op, id, u, reload);
          });
        });
      },
    });
  }

  function dlItem(label, value) {
    return '<dt>' + esc(label) + '</dt><dd>' + (value === null || value === undefined || value === '' ? '-' : esc(value)) + '</dd>';
  }

  /** 与 dlItem 相同，但 value 是已构造好的 HTML 片段（内部自行 esc） */
  function dlItemRaw(label, html) {
    return '<dt>' + esc(label) + '</dt><dd>' + (html === null || html === undefined || html === '' ? '-' : html) + '</dd>';
  }

  /** 抽屉里的小表格卡片；cells 已经是 HTML 片段 */
  function tableCard(title, headers, rows, emptyText) {
    var html = '<div class="card"><div class="card-head"><h3>' + esc(title) + '</h3></div>';
    if (!rows.length) {
      html += '<div class="empty-state" style="padding:22px">' + esc(emptyText || '暂无数据') + '</div>';
    } else {
      html += '<div class="table-wrap"><table class="tbl"><thead><tr>' +
        headers.map(function (h) { return '<th>' + esc(h) + '</th>'; }).join('') +
        '</tr></thead><tbody>' + rows.map(function (r) {
          return '<tr>' + r.map(function (c) { return '<td>' + (c === undefined || c === null ? '-' : c) + '</td>'; }).join('') + '</tr>';
        }).join('') + '</tbody></table></div>';
    }
    html += '</div>';
    return html;
  }

  /* ------------------------------ 邀请码管理 ------------------------------ */

  /* 邀请码取代了原卡密体系：**一个邀请码只能创建一个账号**。
   * 生成时的 duration 语义：
   *   0（默认）= 用户凭码注册后账号是「未开通会员」，在「账号管理 → 开通会员」里开通后水印消失；
   *   >0       = 注册即开通对应天数。
   * 接口：/invites/summary、/invites/batches、/invites、/invites/void、
   *      /invites/batches/:batch_no/void、/invites/export（均见 business.js）。 */

  var inviteState = { page: 1, limit: 50, status: '', batch_no: '', keyword: '' };
  /** GET /invites/batches 的结果缓存，用于批次下拉与批次概览 */
  var inviteBatchRows = [];

  function inviteFilterFields() {
    var batchOptions = [{ value: '', label: '全部批次' }].concat(inviteBatchRows.map(function (b) {
      var st = b.stats || {};
      return { value: b.batch_no, label: b.batch_no + '（未使用 ' + num(st.unused, 0) + '）' };
    }));
    var fields = [
      function (s) { return selectFilter('status', '状态', optionsFrom(INVITE_STATUS, '全部状态'), s.status); },
      function (s) { return selectFilter('batch_no', '批次', batchOptions, s.batch_no); },
      function (s) { return textFilter('keyword', '关键词', s.keyword, '邀请码 / 使用手机号 / 批次号', 'wide'); },
    ];
    var keys = [['status'], ['batch_no'], ['keyword']];
    fields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
    return fields;
  }

  async function renderInvite(host) {
    if (!hasPerm('invite.read')) { requirePermOrWarn('invite.read'); return; }
    setPageHeader('邀请码管理', '批次生成、筛选查询、单条/整批作废与导出');
    var reload = function () { loadInvites(host, reload); };
    loadInvites(host, reload);
  }

  async function loadInvites(host, reload) {
    mountView(host);
    // 三个接口并行取：顶部统计 / 批次列表（同时喂给批次下拉）/ 邀请码分页列表
    var sumRes = await apiGet('/invites/summary', null, { silent: true });
    var batchRes = await apiGet('/invites/batches', null, { silent: true });
    inviteBatchRows = batchRes.code === 0 ? ((batchRes.data && batchRes.data.rows) || []) : [];
    var res = await apiGet('/invites', inviteState, { silent: true });

    var sum = sumRes.code === 0 ? (sumRes.data || {}) : {};

    var html = '<div class="stat-grid">' +
      statCard('邀请码总数', fmtInt(sum.total), '全部批次合计', 'primary') +
      statCard('未使用', fmtInt(sum.unused), '可发给新用户注册' , 'ok') +
      statCard('已使用', fmtInt(sum.used), '一个邀请码只能创建一个账号') +
      '</div>';

    html += noticeBar('<b>一个邀请码只能创建一个账号</b>（并发下也不会一码两号）。' +
      '邀请码只是注册凭证：时长为 <b>0</b> 的邀请码注册出来的账号默认<b>未开通会员</b>——' +
      '这种账号<b>可以直接使用</b>（界面与成品带水印），在「账号管理 → 开通会员」里开通后水印消失。' +
      '<br><b>账号被删掉后，那个邀请码会自动释放为「未使用」</b>，对方可以拿同一个码重新注册；' +
      '如果码是被单独作废或过期了，在列表右侧点「<b>重置</b>」也能放回名额。', 'danger');

    /* ------------------------------ 批次概览 ------------------------------ */
    html += '<div class="card"><div class="card-head"><h3>邀请码批次</h3><div class="card-actions">' +
      '<button type="button" class="btn btn-sm" data-act="reload">刷新</button></div></div>';
    if (!inviteBatchRows.length) {
      html += emptyState('暂无批次', '点击「生成邀请码」创建第一批');
    } else {
      html += '<div class="table-wrap"><table class="tbl"><thead><tr>' +
        '<th>批次号</th><th>时长语义</th><th>等级</th><th>总数</th><th>未使用</th><th>已使用</th>' +
        '<th>已作废</th><th>已过期</th><th>邀请码有效期</th><th>创建时间</th><th>操作</th>' +
        '</tr></thead><tbody>' + inviteBatchRows.map(function (b) {
          var st = b.stats || {};
          var bn = b.batch_no || '';
          return '<tr>' +
            '<td><span class="num">' + esc(bn) + '</span></td>' +
            '<td>' + inviteDurationTag(b.duration) + '</td>' +
            '<td>' + levelTag(b.level) + '</td>' +
            '<td class="num">' + fmtInt(st.total) + '</td>' +
            '<td>' + tag(String(num(st.unused, 0)), num(st.unused, 0) > 0 ? 'tag-ok' : 'tag-gray') + '</td>' +
            '<td>' + tag(String(num(st.used, 0)), 'tag-info') + '</td>' +
            '<td>' + tag(String(num(st.voided, 0)), num(st.voided, 0) > 0 ? 'tag-danger' : 'tag-gray') + '</td>' +
            '<td>' + tag(String(num(st.expired, 0)), 'tag-gray') + '</td>' +
            '<td>' + esc(fmtTime(b.expire_at)) + '</td>' +
            '<td>' + esc(fmtTime(b.created_at)) + '</td>' +
            '<td class="col-actions">' +
              '<button type="button" class="btn-link" data-bfilter="' + esc(bn) + '">只看本批次</button>' +
              downloadButton('导出未使用 TXT', '/invites/export', { batch_no: bn, format: 'txt' },
                'invites-' + (bn || 'all') + '.txt', 'btn-link') +
              downloadButton('导出全部 CSV', '/invites/export', { batch_no: bn, all: '1', format: 'csv' },
                'invites-' + (bn || 'all') + '.csv', 'btn-link') +
              (hasPerm('invite.void') ?
                '<button type="button" class="btn-link danger" data-batch-void="' + esc(bn) + '">作废未使用</button>' : '') +
            '</td></tr>';
        }).join('') + '</tbody></table></div>';
    }
    html += '</div>';

    html += '<div id="invite-table"></div>';
    host.innerHTML = html;

    var rb = $('[data-act="reload"]', host);
    if (rb) rb.addEventListener('click', reload);
    $$('[data-bfilter]', host).forEach(function (btn) {
      btn.addEventListener('click', function () {
        inviteState.batch_no = btn.getAttribute('data-bfilter');
        inviteState.page = 1;
        reload();
      });
    });
    $$('[data-batch-void]', host).forEach(function (btn) {
      btn.addEventListener('click', function () { voidInviteBatch(btn.getAttribute('data-batch-void'), reload); });
    });

    /* ------------------------------ 邀请码列表 ------------------------------ */
    var tableHost = document.getElementById('invite-table');
    if (!tableHost) return;
    if (res.code !== 0) {
      tableHost.innerHTML = noticeBar('加载失败：' + esc(res.msg || ''), 'danger');
      return;
    }
    var data = res.data || {};
    var rows = data.rows || [];

    renderTable(tableHost, {
      title: '邀请码列表',
      state: inviteState,
      fields: inviteFilterFields(),
      columns: [
        {
          key: 'display', title: '邀请码',
          render: function (r) { return '<code class="inline">' + esc(r.display || r.code || '-') + '</code>'; },
        },
        { key: 'status', title: '状态', render: function (r) { return inviteStatusTag(r); } },
        { key: 'batch_no', title: '批次号', className: 'num', render: function (r) { return esc(r.batch_no || '-'); } },
        {
          key: 'used_by', title: '使用账号',
          render: function (r) {
            if (!r.used_by) return '<span class="inline-hint">-</span>';
            var name = esc(r.username || ('#' + r.used_by));
            // 账号已被删：used_by 还在但查不到用户名 —— 必须显式提示，
            // 否则管理员只会看到一串编号，不知道该怎么让这个人重新注册。
            if (r.user_status === null || r.user_status === undefined) {
              return name + ' ' + tag('账号已删除', 'tag-danger') +
                '<div class="inline-hint">用同一个码注册会提示「该邀请码已被使用」，点右侧「重置」即可放回名额</div>';
            }
            return name + ' ' + userStatusTag({ status: r.user_status, status_text: USER_STATUS[Number(r.user_status)] });
          },
        },
        { key: 'used_phone', title: '使用手机号', render: function (r) { return esc(r.used_phone || '-'); } },
        { key: 'used_at', title: '使用时间', render: function (r) { return esc(fmtTime(r.used_at)); } },
        { key: 'level', title: '等级', render: function (r) { return levelTag(r.level); } },
        { key: 'duration', title: '时长语义', render: function (r) { return inviteDurationTag(r.duration); } },
        {
          key: 'quota_day', title: '设备 / 配额',
          render: function (r) { return esc(num(r.max_device, 0) + ' 台 / ' + num(r.quota_day, 0) + ' 次'); },
        },
        { key: 'expire_at', title: '有效期', render: function (r) { return esc(fmtTime(r.expire_at)); } },
        { key: 'remark', title: '备注', className: 'wrap', render: function (r) { return esc(r.remark || '-'); } },
        { key: 'created_at', title: '创建时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) {
            var out = '';
            if (hasPerm('invite.void') && Number(r.status) === 0) {
              out += '<button type="button" class="btn-link danger" data-void-code="' + esc(r.code) + '">作废</button>';
            }
            // 已使用/已作废/已过期的码都能重置回「未使用」（删号后让本人重新注册的场景）
            if (hasPerm('invite.write') && Number(r.status) !== 0) {
              out += '<button type="button" class="btn-link" data-reset-code="' + esc(r.code) + '">重置</button>';
            }
            if (r.batch_no) {
              out += '<button type="button" class="btn-link" data-bfilter="' + esc(r.batch_no) + '">看本批次</button>';
            }
            return out || '<span class="inline-hint">-</span>';
          },
        },
      ],
      rows: rows,
      total: data.total || 0,
      empty: '没有符合条件的邀请码',
      headActions: function () {
        var btns = '';
        if (hasPerm('invite.write')) {
          btns += '<button type="button" class="btn btn-primary btn-sm" data-act="gen">生成邀请码</button>';
        }
        if (hasPerm(['invite.read', 'invite.export'])) {
          btns += downloadButton('导出未使用 TXT', '/invites/export',
            { batch_no: inviteState.batch_no, format: 'txt' },
            'invites-' + (inviteState.batch_no || 'all') + '.txt');
          btns += downloadButton('导出全部 CSV', '/invites/export',
            { batch_no: inviteState.batch_no, all: '1', format: 'csv' },
            'invites-' + (inviteState.batch_no || 'all') + '.csv');
        }
        if (hasPerm('invite.void') && inviteState.batch_no) {
          btns += '<button type="button" class="btn btn-sm btn-danger-text" data-act="void-batch">作废当前批次未使用</button>';
        }
        return btns;
      },
      reload: reload,
      onRowMount: function (container) {
        $$('[data-void-code]', container).forEach(function (btn) {
          btn.addEventListener('click', function () { voidInvite(btn.getAttribute('data-void-code'), reload); });
        });
        $$('[data-reset-code]', container).forEach(function (btn) {
          btn.addEventListener('click', function () { resetInvite(btn.getAttribute('data-reset-code'), reload); });
        });
        $$('[data-bfilter]', container).forEach(function (btn) {
          btn.addEventListener('click', function () {
            inviteState.batch_no = btn.getAttribute('data-bfilter');
            inviteState.page = 1;
            reload();
          });
        });
        var gen = $('[data-act="gen"]', container);
        if (gen) gen.addEventListener('click', function () { newInviteBatchDialog(reload); });
        var vb = $('[data-act="void-batch"]', container);
        if (vb) vb.addEventListener('click', function () { voidInviteBatch(inviteState.batch_no, reload); });
      },
    });
  }

  /** POST /invites —— 生成一批邀请码 */
  function newInviteBatchDialog(reload) {
    var html =
      noticeBar('<b>一个邀请码只能创建一个账号</b>，请按实际要发放的数量生成。单批上限 100000 个。', 'danger') +
      noticeBar('时长 <b>0</b>（默认）表示：用户凭该邀请码注册后账号是<b>未开通会员</b>状态——' +
        '可以正常使用（界面与成品带水印），你可以在「账号管理 → 开通会员」里为他开通；时长 &gt;0 表示注册即开通对应天数。', 'warn') +
      '<div class="form-grid">' +
        formField('数量', inputHtml('count', '10', { type: 'number' }), { required: true, note: '1 ~ 100000' }) +
        formField('时长（天）', inputHtml('duration', '0', { type: 'number' }), {
          required: true, note: '默认 0 = 注册后未开通会员（可用但带水印）；>0 = 注册即开通',
        }) +
        formField('等级', selectHtml('level', Object.keys(LEVEL_TEXT).map(function (k) { return { value: k, label: LEVEL_TEXT[k] }; }), 1)) +
        formField('绑定设备数', inputHtml('max_device', '1', { type: 'number' })) +
        formField('每日配额', inputHtml('quota_day', '20', { type: 'number' }), { note: '0 表示不限量' }) +
        formField('邀请码有效期', inputHtml('expire_at', '', { type: 'datetime-local' }), { note: '留空 = 不限；过期后不可再用于注册' }) +
        formField('备注', textareaHtml('remark', '', { rows: 3 }), { span: true }) +
      '</div>';

    openModal({
      title: '生成邀请码',
      size: 'modal-lg',
      bodyHtml: html,
      confirmText: '生成',
      onConfirm: async function (mask) {
        var f = readFields(mask);
        var count = Number(f.count);
        if (!count || count < 1) { toast('请填写数量（至少 1）', 'warn'); return false; }
        if (count > 100000) { toast('单批最多 100000 个', 'warn'); return false; }
        var duration = Number(f.duration || 0);
        var res = await apiPost('/invites', {
          count: count,
          duration: duration,
          level: Number(f.level || 1),
          max_device: Number(f.max_device || 1),
          quota_day: f.quota_day === '' || f.quota_day === null ? 20 : Number(f.quota_day),
          expire_at: f.expire_at || null,
          remark: f.remark || '',
        });
        if (res.code !== 0) return false;
        toast(res.msg || '已生成', 'ok');
        var data = res.data || {};
        if ((data.codes || []).length) {
          showInviteCodes(data.codes, data.raw_codes, data.batch_no, duration, data.note);
        }
        reload();
        return true;
      },
    });
  }

  /** 生成结果弹窗：展示带横线的 codes，并支持一键复制（raw_codes 为无横线版本） */
  function showInviteCodes(codes, rawCodes, batchNo, duration, note) {
    var list = codes || [];
    var text = list.join('\n');
    var rawList = rawCodes && rawCodes.length ? rawCodes : list;
    var rawText = rawList.join('\n');
    var body = noticeBar('共 <b>' + list.length + '</b> 个，批次 <code class="inline">' + esc(batchNo || '-') + '</code>。' +
      esc(note || '一个邀请码只能创建一个账号') + '。请及时导出保存，关闭后仍可在列表里查询。', 'warn') +
      (Number(duration) ? '' : noticeBar('本批次时长为 0：用户注册后是<b>未开通会员</b>状态，' +
        '可以直接使用（界面与成品带水印），在「账号管理 → 开通会员」里开通后水印消失。', 'info')) +
      '<label class="field"><span class="field-label">邀请码（带横线，展示/发放用）</span>' +
      '<textarea class="textarea mono" rows="12" readonly>' + esc(text) + '</textarea></label>' +
      '<label class="field" style="margin-top:10px"><span class="field-label">无横线版本（脚本导入用 raw_codes）</span>' +
      '<textarea class="textarea mono" rows="4" readonly>' + esc(rawText) + '</textarea></label>';
    openModal({
      title: '邀请码生成结果' + (batchNo ? ' · ' + batchNo : ''),
      size: 'modal-lg',
      confirmText: '复制全部邀请码',
      bodyHtml: body,
      onConfirm: function () {
        copyText(text, '带横线版本');
        return false; // 复制后不关闭，方便继续抄录/导出
      },
      onMount: function (mask) {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-sm';
        btn.textContent = '复制无横线版本';
        btn.addEventListener('click', function () { copyText(rawText, '无横线版本'); });
        var foot = $('.modal-foot', mask);
        if (foot) foot.insertBefore(btn, foot.firstChild);
      },
    });
  }

  function copyText(text, label) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () {
        toast('已复制' + (label ? '（' + label + '）' : '') + '到剪贴板', 'ok');
      }, function () { toast('复制失败，请手动选择文本', 'warn'); });
    } else {
      toast('当前浏览器不支持自动复制，请手动选择文本', 'warn');
    }
  }

  /** POST /invites/void —— 单条作废（需二次输入登录密码） */
  /**
   * POST /invites/:code/reset —— 把已使用/已作废的邀请码恢复为「未使用」。
   *
   * 什么时候用：账号被删掉之后，对方要用**同一个邀请码**重新注册。
   * 删号时服务端已经会自动释放该码；如果码是被单独作废/过期了，就在这里手动重置。
   * 与「作废」语义相反，所以要二次确认，并写审计日志。
   */
  async function resetInvite(code, after) {
    var confirm = await confirmDialog(
      '确认把邀请码 <code class="inline">' + esc(code) + '</code> <b>重置为未使用</b>？<br>' +
      '重置后这个码可以再创建一个账号（等于把名额放回去）。<br>' +
      '<span class="inline-hint">用于「账号删了，让本人用同一个码重新注册」的场景。</span>',
      { title: '重置邀请码（二次确认）', needPassword: true, confirmText: '确认重置' });
    if (!confirm || !confirm.ok) return;
    var res = await apiPost('/invites/' + encodeURIComponent(code) + '/reset', { password: confirm.password });
    if (res.code === 0) {
      toast(res.msg || '已重置为未使用', 'ok');
      if (after) after();
    }
  }

  async function voidInvite(code, after) {
    var confirm = await confirmDialog(
      '确认<b>作废</b>邀请码 <code class="inline">' + esc(code) + '</code>？作废后该码不能再用于注册，且不可恢复。',
      { title: '作废邀请码（二次确认）', needPassword: true, confirmText: '确认作废' });
    if (!confirm || !confirm.ok) return;
    var reason = await promptDialog({
      title: '作废原因（可选）',
      fields: [{ key: 'reason', label: '原因', placeholder: '例如：渠道退货 / 发放错误' }],
      confirmText: '提交作废',
    });
    if (!reason) return;
    var res = await apiPost('/invites/void', { code: code, password: confirm.password, reason: reason.reason || '' });
    if (res.code === 0) { toast('已作废', 'ok'); if (after) after(); }
  }

  /** POST /invites/batches/:batch_no/void —— 按批次作废（需二次输入登录密码） */
  async function voidInviteBatch(batchNo, reload) {
    if (!batchNo) { toast('请先选择批次', 'warn'); return; }
    var confirm = await confirmDialog(
      '确认作废批次 <span class="num">' + esc(batchNo) + '</span> 下<b>所有未使用</b>的邀请码？已使用与已作废的不受影响。',
      { title: '作废批次未使用邀请码（二次确认）', needPassword: true, confirmText: '确认作废' });
    if (!confirm || !confirm.ok) return;
    var res = await apiPost('/invites/batches/' + encodeURIComponent(batchNo) + '/void', { password: confirm.password });
    if (res.code === 0) { toast(res.msg || '已作废', 'ok'); reload(); }
  }

  /* ------------------------------ 订单管理 ------------------------------ */

  var orderState = {
    page: 1, limit: 20, sort: 'created_at', order: 'desc',
    keyword: '', status: '', pay_type: '', type: '',
    start_date: '', end_date: '', min_amount: '', max_amount: '',
  };
  var orderFilterFields = [
    function (s) { return textFilter('keyword', '关键词', s.keyword, '订单号 / 交易号 / 用户名 / 手机号', 'wide'); },
    function (s) { return selectFilter('status', '状态', optionsFrom(ORDER_STATUS, '全部状态'), s.status); },
    function (s) { return selectFilter('pay_type', '支付方式', optionsFrom(PAY_TYPE, '全部方式'), s.pay_type); },
    function (s) { return selectFilter('type', '类型', optionsFrom(ORDER_TYPE, '全部类型'), s.type); },
    function (s) { return dateFilter('start_date', '下单起', s.start_date); },
    function (s) { return dateFilter('end_date', '下单止', s.end_date); },
    function (s) { return textFilter('min_amount', '金额≥', s.min_amount, '0.00', 'amount'); },
    function (s) { return textFilter('max_amount', '金额≤', s.max_amount, '0.00', 'amount'); },
  ];
  (function () {
    var keys = [['keyword'], ['status'], ['pay_type'], ['type'], ['start_date'], ['end_date'], ['min_amount'], ['max_amount']];
    orderFilterFields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
  })();

  async function renderOrder(host) {
    if (!hasPerm('order.read')) { requirePermOrWarn('order.read'); return; }
    setPageHeader('订单管理', '订单流水、支付统计、退款与补开会员');
    var reload = function () { loadOrders(host, reload); };
    loadOrders(host, reload);
  }

  async function loadOrders(host, reload) {
    mountView(host);
    var summaryRes = await apiGet('/orders/summary', null, { silent: true });
    var res = await apiGet('/orders', orderState, { silent: true });

    var html = '';
    var s = summaryRes.code === 0 ? (summaryRes.data || {}) : {};
    html += '<div class="stat-grid">' +
      statCard('待支付订单', fmtInt(s.pending_count), '未完成支付的订单', num(s.pending_count, 0) > 0 ? 'warn' : '') +
      statCard('已支付订单', fmtInt(s.paid_count), '累计成功支付', 'ok') +
      statCard('累计实付金额', '¥ ' + fmtMoney(s.paid_amount), '所有已支付订单实付合计', 'primary') +
      statCard('今日订单', fmtInt(s.today_count), null) +
      statCard('今日金额', '¥ ' + fmtMoney(s.today_amount), null, 'ok') +
      '</div>';

    host.innerHTML = html + '<div id="order-table"></div>';

    var tableHost = document.getElementById('order-table');
    if (res.code !== 0) {
      tableHost.innerHTML = noticeBar('加载失败：' + esc(res.msg), 'danger');
      return;
    }
    var data = res.data || {};
    var rows = data.rows || [];

    renderTable(tableHost, {
      title: '订单流水',
      state: orderState,
      fields: orderFilterFields,
      columns: [
        { key: 'order_no', title: '订单号', className: 'num' },
        { key: 'username', title: '用户名', render: function (r) { return esc(r.username || '-'); } },
        { key: 'phone', title: '手机号', render: function (r) { return esc(r.phone || '-'); } },
        { key: 'goods_name', title: '商品', className: 'wrap', render: function (r) { return esc(r.goods_name || '-'); } },
        { key: 'type', title: '类型', render: function (r) { return esc(ORDER_TYPE[Number(r.type)] || '-'); } },
        { key: 'origin_amount', title: '原价', align: 'right', render: function (r) { return '<span class="money">' + esc(fmtMoney(r.origin_amount)) + '</span>'; } },
        { key: 'amount', title: '实付', sortable: true, align: 'right', render: function (r) { return '<span class="money pos">' + esc(fmtMoney(r.amount)) + '</span>'; } },
        { key: 'fee', title: '手续费', align: 'right', render: function (r) { return '<span class="money">' + esc(fmtMoney(r.fee)) + '</span>'; } },
        { key: 'net_amount', title: '净收入', align: 'right', render: function (r) { return '<span class="money">' + esc(fmtMoney(r.net_amount)) + '</span>'; } },
        { key: 'pay_type', title: '支付方式', render: function (r) { return esc(PAY_TYPE[r.pay_type] || r.pay_type || '-'); } },
        { key: 'status', title: '状态', sortable: true, render: function (r) { return orderStatusTag(r); } },
        { key: 'paid_at', title: '支付时间', sortable: true, render: function (r) { return esc(fmtTime(r.paid_at)); } },
        { key: 'created_at', title: '创建时间', sortable: true, render: function (r) { return esc(fmtTime(r.created_at)); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) {
            var no = esc(r.order_no);
            var out = '<button type="button" class="btn-link" data-op="detail" data-no="' + no + '">详情</button>';
            if (hasPerm('order.refund') && Number(r.status) === 1) {
              out += '<button type="button" class="btn-link danger" data-op="refund" data-no="' + no + '">退款</button>';
            }
            if (hasPerm('order.write') && Number(r.status) === 1) {
              out += '<button type="button" class="btn-link" data-op="reissue" data-no="' + no + '">补开会员</button>';
            }
            return out;
          },
        },
      ],
      rows: rows,
      total: data.total || 0,
      empty: '没有符合条件的订单',
      headActions: function () {
        var btns = '';
        if (hasPerm(['order.read', 'order.export'])) btns += '<button type="button" class="btn btn-sm" data-act="export">导出 CSV</button>';
        return btns;
      },
      reload: reload,
      onRowMount: function (container) {
        $$('[data-op]', container).forEach(function (btn) {
          btn.addEventListener('click', function () {
            var no = btn.getAttribute('data-no');
            var op = btn.getAttribute('data-op');
            var row = rows.filter(function (r) { return r.order_no === no; })[0];
            if (op === 'detail') orderDetailDialog(row);
            else if (op === 'refund') refundOrder(no, row, reload);
            else if (op === 'reissue') reissueOrder(no, row, reload);
          });
        });
        var exp = $('[data-act="export"]', container);
        if (exp) exp.addEventListener('click', function () {
          download('/orders/export', orderState, 'orders-' + nowDateStr() + '.csv');
        });
      },
    });
  }

  function orderDetailDialog(row) {
    if (!row) return;
    var fields = [
      ['订单号', row.order_no], ['第三方交易号', row.trade_no], ['用户 ID', row.user_id],
      ['用户名', row.username], ['手机号', row.phone], ['商品', row.goods_name],
      ['类型', ORDER_TYPE[Number(row.type)] || '-'], ['时长', row.duration ? row.duration + ' 天' : '-'],
      ['原价', '¥ ' + fmtMoney(row.origin_amount)], ['实付', '¥ ' + fmtMoney(row.amount)],
      ['优惠', '¥ ' + fmtMoney(row.discount)], ['手续费', '¥ ' + fmtMoney(row.fee)],
      ['净收入', '¥ ' + fmtMoney(row.net_amount)], ['支付方式', PAY_TYPE[row.pay_type] || row.pay_type || '-'],
      ['支付通道', row.pay_channel], ['状态', ORDER_STATUS[Number(row.status)] || '-'],
      ['支付时间', fmtTime(row.paid_at)], ['创建时间', fmtTime(row.created_at)],
      ['客户端 IP', row.client_ip], ['备注', row.remark],
    ];
    openModal({
      title: '订单详情',
      size: 'modal-lg',
      hideConfirm: true,
      cancelText: '关闭',
      bodyHtml: '<dl class="dl dl-2">' + fields.map(function (f) {
        return '<dt>' + esc(f[0]) + '</dt><dd>' + esc(f[1] === null || f[1] === undefined || f[1] === '' ? '-' : f[1]) + '</dd>';
      }).join('') + '</dl>',
    });
  }

  async function refundOrder(orderNo, row, reload) {
    var confirm = await confirmDialog(
      '确认对订单 <span class="num">' + esc(orderNo) + '</span>（实付 ¥' + esc(fmtMoney(row && row.amount)) + '）执行<b>退款</b>？<br>' +
      '退款后订单标记为已退款，并会<b>立即终止该用户授权</b>（置为已过期）。',
      { title: '订单退款（二次确认）', needPassword: true, confirmText: '确认退款' });
    if (!confirm || !confirm.ok) return;
    var reason = await promptDialog({
      title: '退款原因',
      fields: [{ key: 'reason', label: '原因', required: true, placeholder: '例如：用户申请 / 重复支付' }],
      confirmText: '提交退款',
    });
    if (!reason) return;
    var res = await apiPost('/orders/' + encodeURIComponent(orderNo) + '/refund', {
      password: confirm.password, reason: reason.reason,
    });
    if (res.code === 0) { toast(res.msg || '已退款', 'ok'); reload(); }
  }

  /**
   * 「补开会员」：POST /admin/api/orders/:order_no/reissue
   * 订单已支付但用户没开通（或需要补偿）时，直接给该订单用户续期，
   * 不再补发卡密（卡密体系已下线）。天数默认取订单本身的 duration。
   */
  async function reissueOrder(orderNo, row, reload) {
    var def = row && Number(row.duration) > 0 ? Number(row.duration) : 30;
    var values = await promptDialog({
      title: '补开会员 · ' + orderNo,
      intro: '订单已支付但用户未开通（或需要补偿）时，直接给该订单用户<b>续期开通</b>；' +
        '不再补发卡密。天数默认为订单本身的开通时长，可修改且不能为 0。',
      fields: [{ key: 'days', label: '补开天数', type: 'number', required: true, value: String(def), note: '默认取订单 duration' }],
      confirmText: '确认补开',
    });
    if (!values) return;
    var days = Number(values.days);
    if (!isFinite(days) || days === 0) { toast('补开天数必须是非 0 数字（天）', 'warn'); return; }
    var res = await apiPost('/orders/' + encodeURIComponent(orderNo) + '/reissue', { days: days });
    if (res.code === 0) { toast(res.msg || '已补开', 'ok'); reload(); }
  }

  /* ------------------------------ 收益管理 ------------------------------ */

  var revenueGranularity = 'month';

  /** 收益区间的可读描述（后端返回 {from,to} / {date} / {months} 三种形状） */
  function describeRange(range) {
    if (!range || typeof range !== 'object') return '';
    if (range.from && range.to) return range.from + ' ~ ' + range.to;
    if (range.date) return range.date;
    if (range.months) return '近 ' + range.months + ' 个月';
    return '';
  }

  async function renderRevenue(host) {
    if (!hasPerm('revenue.read')) { requirePermOrWarn('revenue.read'); return; }
    setPageHeader('收益管理', '五档时间粒度收益概览、近 30 天趋势、补算与对账');

    var html = noticeBar('收益数据来自 <code class="inline">revenue_daily</code> 日聚合表；如发现数据缺失，先「补算」再「对账」。', 'info');
    html += '<div class="card"><div class="card-head"><h3>收益概览</h3>' +
      '<div class="chart-tabs">' +
      [['year', '当年'], ['quarter', '当季'], ['month', '当月'], ['week', '当周'], ['hour', '当日']].map(function (g) {
        return '<button type="button" class="chart-tab' + (revenueGranularity === g[0] ? ' active' : '') + '" data-gran="' + g[0] + '">' + g[1] + '</button>';
      }).join('') + '</div></div>' +
      '<div class="card-body" id="rev-overview"><div class="loading-block">加载中…</div></div></div>';

    html += '<div class="card"><div class="card-head"><h3>近 30 天趋势</h3>' +
      '<div class="card-actions"><button type="button" class="btn btn-sm" data-act="aggregate">补算收益</button>' +
      (hasPerm('revenue.write') ? '<button type="button" class="btn btn-sm" data-act="reconcile">立即对账</button>' : '') +
      '</div></div>' +
      '<div class="card-body"><div class="chart-host" id="rev-daily"></div></div></div>';

    html += '<div class="card"><div class="card-head"><h3>对账日志</h3>' +
      '<div class="card-actions"><button type="button" class="btn btn-sm" data-act="log-reload">刷新</button></div></div>' +
      '<div id="rev-logs"><div class="loading-block">加载中…</div></div></div>';

    host.innerHTML = html;

    $$('[data-gran]', host).forEach(function (btn) {
      btn.addEventListener('click', function () {
        revenueGranularity = btn.getAttribute('data-gran');
        $$('[data-gran]', host).forEach(function (b) { b.classList.toggle('active', b === btn); });
        loadOverview();
      });
    });

    host.querySelector('[data-act="aggregate"]').addEventListener('click', function () { aggregateRevenue(); });
    var rec = host.querySelector('[data-act="reconcile"]');
    if (rec) rec.addEventListener('click', function () { reconcileRevenue(); });
    host.querySelector('[data-act="log-reload"]').addEventListener('click', function () { loadReconcileLogs(); });

    async function loadOverview() {
      var box = document.getElementById('rev-overview');
      box.innerHTML = '<div class="loading-block">加载中…</div>';
      var res = await apiGet('/revenue/overview', { granularity: revenueGranularity });
      if (res.code !== 0) { box.innerHTML = '<div class="inline-hint">加载失败：' + esc(res.msg) + '</div>'; return; }
      var d = res.data || {};
      var series = d.series || [];
      var cards = d.cards || {};
      var labels = series.map(function (s) { return s.label; });

      var summary = '<div class="stat-grid">' +
        statCard('订单数', fmtInt(cards.order_count), null, 'primary') +
        statCard('付费人数', fmtInt(cards.user_count), null) +
        statCard('成交额', '¥ ' + fmtMoney(cards.gross), '上期 ¥ ' + fmtMoney(cards.prev_gross), 'ok') +
        statCard('退款额', '¥ ' + fmtMoney(cards.refund), null, num(cards.refund, 0) > 0 ? 'danger' : '') +
        statCard('净收入', '¥ ' + fmtMoney(cards.net), '已扣除通道手续费', 'ok') +
        statCard('环比增长率',
          cards.growth === null || cards.growth === undefined ? '无对比基准' : (cards.growth > 0 ? '+' : '') + cards.growth + '%',
          cards.growth === null || cards.growth === undefined ? '上期成交额为 0，无法计算环比' : '对比上一同长度区间',
          cards.growth === null || cards.growth === undefined ? '' : (cards.growth < 0 ? 'danger' : 'ok')) +
        '</div>';

      box.innerHTML = summary +
        '<div class="section-title">成交额分布' +
        '<span class="inline-hint">' + esc(describeRange(d.range)) + '</span></div>' +
        '<div class="chart-host" id="rev-chart"></div>' +
        '<div class="table-wrap" style="margin-top:14px"><table class="tbl"><thead><tr>' +
        '<th>区间</th><th>订单数</th><th>付费人数</th><th>成交额</th><th>手续费</th><th>净收入</th><th>退款</th>' +
        '</tr></thead><tbody>' + (series.length ? series.map(function (s) {
          return '<tr><td>' + esc(s.label) + '</td><td class="num">' + fmtInt(s.order_count) + '</td>' +
            '<td class="num">' + fmtInt(s.user_count) + '</td>' +
            '<td class="money">' + esc(fmtMoney(s.gross)) + '</td>' +
            '<td class="money">' + esc(fmtMoney(s.fee)) + '</td>' +
            '<td class="money">' + esc(fmtMoney(s.net)) + '</td>' +
            '<td class="money">' + esc(fmtMoney(s.refund)) + '</td></tr>';
        }).join('') : '<tr><td colspan="7" class="empty-state">该区间暂无数据</td></tr>') +
        '</tbody></table></div>';

      barChart(document.getElementById('rev-chart'), {
        labels: labels,
        values: series.map(function (s) { return num(s.gross, 0); }),
        tips: series.map(function (s) {
          return '成交额 ¥' + fmtMoney(s.gross) + ' · 订单 ' + fmtInt(s.order_count) + ' · 净收入 ¥' + fmtMoney(s.net);
        }),
        valueFormat: function (v) { return '¥' + (v >= 10000 ? Math.round(v / 100) / 100 + 'w' : Math.round(v)); },
        unit: '',
        height: 280,
      });
    }

    async function loadDaily() {
      var res = await apiGet('/revenue/daily', {});
      var box = document.getElementById('rev-daily');
      if (!box) return;
      if (res.code !== 0) { box.innerHTML = '<div class="inline-hint">加载失败：' + esc(res.msg) + '</div>'; return; }
      var d = res.data || {};
      var rows = d.rows || [];
      lineChart(box, {
        labels: rows.map(function (r) { return String(r.date).slice(5); }),
        series: [
          { name: '成交额', values: rows.map(function (r) { return num(r.gross, 0); }), color: '#2563a8', fill: true },
          { name: '净收入', values: rows.map(function (r) { return num(r.net, 0); }), color: '#1a8a5a', fill: false },
          { name: '退款额', values: rows.map(function (r) { return num(r.refund, 0); }), color: '#c0392b', fill: false },
        ],
        valueFormat: function (v) { return '¥' + Math.round(v * 100) / 100; },
        height: 260,
      });
      var tip = document.createElement('div');
      tip.className = 'inline-hint';
      tip.style.padding = '6px 2px 0';
      tip.textContent = '统计区间：' + (d.from || '-') + ' ~ ' + (d.to || '-') + '（共 ' + rows.length + ' 天）';
      box.appendChild(tip);
    }

    async function loadReconcileLogs() {
      var box = document.getElementById('rev-logs');
      box.innerHTML = '<div class="loading-block">加载中…</div>';
      var res = await apiGet('/revenue/reconcile-logs', { page: 1, limit: 20 });
      if (res.code !== 0) { box.innerHTML = '<div class="empty-state">加载失败：' + esc(res.msg) + '</div>'; return; }
      var rows = (res.data && res.data.rows) || [];
      if (!rows.length) { box.innerHTML = emptyState('暂无对账记录', '点击「立即对账」生成记录'); return; }
      box.innerHTML = '<div class="table-wrap"><table class="tbl"><thead><tr>' +
        '<th>ID</th><th>对账日期</th><th>结果</th><th>本地笔数/金额</th><th>渠道笔数/金额</th><th>差异</th><th>创建时间</th><th>详情</th>' +
        '</tr></thead><tbody>' + rows.map(function (r) {
          var status = Number(r.status);
          return '<tr>' +
            '<td class="num">' + esc(r.id) + '</td>' +
            '<td>' + esc(fmtTime(r.stat_date || r.date)) + '</td>' +
            '<td>' + tag(status === 0 ? '一致' : '有差异', status === 0 ? 'tag-ok' : 'tag-danger') + '</td>' +
            '<td class="num">' + esc(val(r.local_count !== undefined ? r.local_count : r.order_count)) + ' / ¥' + esc(fmtMoney(r.local_amount !== undefined ? r.local_amount : r.amount)) + '</td>' +
            '<td class="num">' + esc(val(r.channel_count)) + ' / ¥' + esc(fmtMoney(r.channel_amount)) + '</td>' +
            '<td class="num">' + esc(val(r.diff_count !== undefined ? r.diff_count : r.diff)) + ' / ¥' + esc(fmtMoney(r.diff_amount)) + '</td>' +
            '<td>' + esc(fmtTime(r.created_at)) + '</td>' +
            '<td>' + jsonBlock(r.detail || r.msg || r.remark) + '</td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';
    }

    async function aggregateRevenue() {
      var values = await promptDialog({
        title: '补算收益日聚合',
        intro: '补算最近 N 天的 <code class="inline">revenue_daily</code>，最多 90 天。',
        fields: [{ key: 'days', label: '补算天数', type: 'number', value: 3 }],
        confirmText: '开始补算',
      });
      if (!values) return;
      var res = await apiPost('/revenue/aggregate', { days: Number(values.days || 3) });
      if (res.code === 0) { toast(res.msg || '已补算', 'ok'); loadOverview(); loadDaily(); }
    }

    async function reconcileRevenue() {
      var values = await promptDialog({
        title: '订单对账',
        intro: '把平台订单与支付渠道账单逐笔比对，结果写入对账日志。',
        fields: [{ key: 'date', label: '对账日期', type: 'date', value: nowDateStr() }],
        confirmText: '开始对账',
      });
      if (!values) return;
      var res = await apiPost('/revenue/reconcile', { date: values.date || nowDateStr() });
      if (res.code === 0) { toast(res.msg || '对账完成', 'ok'); loadReconcileLogs(); }
    }

    await loadOverview();
    await loadDaily();
    await loadReconcileLogs();
  }

  /* ------------------------------ 任务与线路 ------------------------------ */

  var taskState = {
    page: 1, limit: 20, keyword: '', status: '', platform: '',
    start_date: '', end_date: '',
  };
  var taskFilterFields = [
    function (s) { return textFilter('keyword', '关键词', s.keyword, '任务号 / item_id / 标题', 'wide'); },
    function (s) { return selectFilter('status', '状态', optionsFrom(TASK_STATUS, '全部状态'), s.status); },
    function (s) {
      var opts = [{ value: '', label: '全部平台' }].concat(PLATFORMS.map(function (p) { return { value: p, label: p }; }));
      return selectFilter('platform', '平台', opts, s.platform);
    },
    function (s) { return dateFilter('start_date', '创建起', s.start_date); },
    function (s) { return dateFilter('end_date', '创建止', s.end_date); },
  ];
  (function () {
    var keys = [['keyword'], ['status'], ['platform'], ['start_date'], ['end_date']];
    taskFilterFields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
  })();
  var traceState = { user_id: '', item_id: '', task_no: '', ip: '' };

  async function renderTask(host) {
    if (!hasPerm('task.read')) { requirePermOrWarn('task.read'); return; }
    setPageHeader('任务与线路', '任务流水、解析线路健康度、侵权投诉溯源');
    host.innerHTML = '<div id="task-queue"></div><div id="task-table"></div><div id="route-table"></div><div id="trace-card"></div>';
    loadTaskQueue(host);
    loadTasks(host);
    loadRoutes(host);
    renderTrace(host);
  }

  async function loadTaskQueue(host) {
    var box = document.getElementById('task-queue');
    var res = await apiGet('/tasks/queue', null, { silent: true });
    if (res.code !== 0) { box.innerHTML = noticeBar('队列状态加载失败：' + esc(res.msg), 'danger'); return; }
    var q = res.data || {};
    var backlog = num(q.backlog, 0);
    box.innerHTML = '<div class="stat-grid">' +
      statCard('排队中', fmtInt(q.queued), '等待 worker 拉取', num(q.queued, 0) > 0 ? 'warn' : '') +
      statCard('处理中', fmtInt(q.running), '解析 / 合成阶段', 'primary') +
      statCard('KV 高优先队列', fmtInt(q.kv_high), '队列长度') +
      statCard('KV 普通队列', fmtInt(q.kv_normal), '队列长度') +
      statCard('总积压', fmtInt(backlog), '排队 + 处理中，超过 50 会触发告警', backlog > 50 ? 'danger' : 'ok') +
      '</div>';
  }

  async function loadTasks(host) {
    var box = document.getElementById('task-table');
    var reload = function () { loadTasks(host); };
    var res = await apiGet('/tasks', taskState, { silent: true });
    if (res.code !== 0) { box.innerHTML = noticeBar('任务列表加载失败：' + esc(res.msg), 'danger'); return; }
    var data = res.data || {};
    var rows = data.rows || [];
    renderTable(box, {
      title: '任务流水',
      state: taskState,
      fields: taskFilterFields,
      columns: [
        { key: 'task_no', title: '任务号', className: 'num' },
        { key: 'username', title: '用户', render: function (r) { return esc(r.username || r.user_id || '-'); } },
        { key: 'platform', title: '平台', render: function (r) { return esc(r.platform || '-'); } },
        { key: 'status', title: '状态', render: function (r) { return tag(TASK_STATUS[Number(r.status)] || '-', TASK_STATUS_CLASS[Number(r.status)] || 'tag-gray'); } },
        {
          key: 'cost', title: '耗时',
          render: function (r) {
            var ms = r.duration_ms || (r.started_at && r.finished_at
              ? (new Date(String(r.finished_at).replace(/-/g, '/')).getTime() - new Date(String(r.started_at).replace(/-/g, '/')).getTime())
              : null);
            return esc(ms ? Math.round(Number(ms) / 100) / 10 + ' 秒' : '-');
          },
        },
        { key: 'created_at', title: '创建时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        { key: 'err_msg', title: '错误信息', className: 'wrap', render: function (r) { return esc(truncate(r.err_msg || '-', 80)); } },
        { key: 'ops', title: '操作', className: 'col-actions', render: function (r) { return '<button type="button" class="btn-link" data-trace-task="' + esc(r.task_no) + '">溯源</button>'; } },
      ],
      rows: rows,
      total: data.total || 0,
      empty: '暂无任务',
      reload: reload,
      onRowMount: function (container) {
        $$('[data-trace-task]', container).forEach(function (btn) {
          btn.addEventListener('click', function () {
            traceState = { user_id: '', item_id: '', task_no: btn.getAttribute('data-trace-task'), ip: '' };
            renderTrace(host);
          });
        });
      },
    });
  }

  async function loadRoutes(host) {
    var box = document.getElementById('route-table');
    var reload = function () { loadRoutes(host); };
    var res = await apiGet('/routes', null, { silent: true });
    if (res.code !== 0) { box.innerHTML = noticeBar('线路加载失败：' + esc(res.msg), 'danger'); return; }
    var rows = (res.data && res.data.rows) || [];
    renderTable(box, {
      title: '解析线路健康度',
      subtitle: '成功率低于 80% 整行标红',
      state: { page: 1, limit: 100, sort: '', order: 'desc' },
      fields: [],
      columns: [
        { key: 'id', title: 'ID', className: 'num' },
        { key: 'platform', title: '平台' },
        { key: 'name', title: '名称' },
        { key: 'type', title: '类型', render: function (r) { return esc(r.type); } },
        { key: 'priority', title: '优先级', render: function (r) { return '<span class="num">' + esc(r.priority) + '</span>'; } },
        { key: 'enabled', title: '启用', render: function (r) { return boolTag(Number(r.enabled) === 1, '启用', '停用'); } },
        { key: 'succ_rate_1h', title: '1h 成功率', render: function (r) { return rateTag(r.succ_rate_1h); } },
        { key: 'succ_1h', title: '成功/失败', render: function (r) { return '<span class="num">' + fmtInt(r.succ_1h) + ' / ' + fmtInt(r.fail_1h) + '</span>'; } },
        { key: 'endpoint', title: 'Endpoint', className: 'wrap', render: function (r) { return '<code class="inline">' + esc(truncate(r.endpoint || '-', 70)) + '</code>'; } },
        { key: 'updated_at', title: '更新时间', render: function (r) { return esc(fmtTime(r.updated_at)); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) {
            if (!hasPerm('route.write')) return '<span class="inline-hint">无权限</span>';
            return '<button type="button" class="btn-link" data-route-edit="' + esc(r.id) + '">编辑</button>';
          },
        },
      ],
      rows: rows,
      total: rows.length,
      empty: '暂无线路',
      reload: reload,
      footNote: '线路编辑保存后立即生效',
      onRowMount: function (container) {
        $$('[data-route-edit]', container).forEach(function (btn) {
          btn.addEventListener('click', function () {
            var id = Number(btn.getAttribute('data-route-edit'));
            var row = rows.filter(function (r) { return Number(r.id) === id; })[0];
            editRouteDialog(row, reload);
          });
        });
      },
    });
  }

  function editRouteDialog(row, reload) {
    if (!row) return;
    var configText = '';
    try { configText = row.config ? JSON.stringify(row.config, null, 2) : '{}'; } catch (e) { configText = String(row.config || ''); }
    openModal({
      title: '编辑线路 · ' + (row.name || row.id),
      size: 'modal-lg',
      bodyHtml: '<div class="form-grid">' +
        formField('平台', inputHtml('platform', row.platform)) +
        formField('名称', inputHtml('name', row.name)) +
        formField('类型', inputHtml('type', row.type, { type: 'number' })) +
        formField('优先级（越小越优先）', inputHtml('priority', row.priority, { type: 'number' })) +
        formField('启用', switchHtml('enabled', Number(row.enabled) === 1, '启用该线路'), { span: true }) +
        formField('Endpoint', inputHtml('endpoint', row.endpoint, { mono: true, span: true }), { span: true }) +
        formField('Config（JSON）', textareaHtml('config', configText, { mono: true, rows: 8 }), { span: true, note: '必须是合法 JSON，保存前会做一次解析校验' }) +
        '</div>',
      confirmText: '保存线路',
      onConfirm: async function (mask) {
        var f = readFields(mask);
        var config;
        try {
          config = f.config && f.config.trim() ? JSON.parse(f.config) : {};
        } catch (e) {
          toast('Config 不是合法 JSON：' + e.message, 'error');
          return false;
        }
        var res = await apiPut('/routes/' + row.id, {
          platform: f.platform, name: f.name, type: Number(f.type || 1),
          priority: Number(f.priority || 100), enabled: f.enabled, endpoint: f.endpoint,
          config: config,
        });
        if (res.code !== 0) return false;
        toast('线路已保存', 'ok');
        reload();
        return true;
      },
    });
  }

  function renderTrace(host) {
    var box = document.getElementById('trace-card');
    if (!box) return;
    if (!hasPerm('task.trace')) {
      box.innerHTML = noticeBar('当前账号缺少 <code class="inline">task.trace</code> 权限，无法使用侵权投诉溯源。', 'warn');
      return;
    }
    box.innerHTML = '<div class="card"><div class="card-head"><h3>侵权投诉溯源</h3>' +
      '<span class="card-sub">按用户 / item_id / 任务号 / IP 任一条件定位到具体成品与取证链接</span></div>' +
      '<div class="filters">' +
        textFilter('user_id', '用户 ID', traceState.user_id, '数字 ID', 'amount') +
        textFilter('item_id', '作品 ID', traceState.item_id, '平台作品 ID', 'wide') +
        textFilter('task_no', '任务号', traceState.task_no, '任务号', 'wide') +
        textFilter('ip', '客户端 IP', traceState.ip, '例如 1.2.3.4') +
        '<div class="filter-actions">' +
          '<button type="button" class="btn btn-primary btn-sm" data-act="trace-search">检索</button>' +
          '<button type="button" class="btn btn-sm" data-act="trace-reset">清空</button>' +
        '</div>' +
      '</div>' +
      '<div id="trace-result"><div class="empty-state"><strong>输入任一条件后点击「检索」</strong>溯源结果会带 1 小时有效的取证下载链接</div></div>' +
      '</div>';

    var collect = function () {
      $$('[data-f]', box).forEach(function (el) { traceState[el.getAttribute('data-f')] = el.value; });
    };
    var search = $('[data-act="trace-search"]', box);
    search.addEventListener('click', function () { collect(); runTrace(); });
    var reset = $('[data-act="trace-reset"]', box);
    reset.addEventListener('click', function () {
      traceState = { user_id: '', item_id: '', task_no: '', ip: '' };
      renderTrace(host);
    });

    async function runTrace() {
      if (!traceState.user_id && !traceState.item_id && !traceState.task_no && !traceState.ip) {
        toast('请至少填写一个检索条件', 'warn');
        return;
      }
      var out = document.getElementById('trace-result');
      out.innerHTML = '<div class="loading-block">检索中…</div>';
      var res = await apiGet('/tasks/trace', traceState);
      if (res.code !== 0) { out.innerHTML = '<div class="empty-state">检索失败：' + esc(res.msg) + '</div>'; return; }
      var rows = (res.data && res.data.rows) || [];
      if (!rows.length) { out.innerHTML = emptyState('没有匹配的记录', '请确认参数是否正确'); return; }
      out.innerHTML = noticeBar('共命中 <b>' + rows.length + '</b> 条记录。取证链接有效期 1 小时，请及时下载归档。', 'warn') +
        '<div class="table-wrap"><table class="tbl"><thead><tr>' +
        '<th>任务号</th><th>用户 ID</th><th>作品 ID</th><th>平台</th><th>状态</th><th>设备 ID</th><th>客户端 IP</th>' +
        '<th>源地址</th><th>成品</th><th>创建时间</th><th>取证</th>' +
        '</tr></thead><tbody>' + rows.map(function (r) {
          return '<tr>' +
            '<td class="num">' + esc(r.task_no || '-') + '</td>' +
            '<td class="num">' + esc(r.user_id || '-') + '</td>' +
            '<td><code class="inline">' + esc(r.item_id || '-') + '</code></td>' +
            '<td>' + esc(r.platform_name || r.platform || '-') + '</td>' +
            '<td>' + tag(r.status_text || TASK_STATUS[Number(r.status)] || '-', TASK_STATUS_CLASS[Number(r.status)] || 'tag-gray') + '</td>' +
            '<td><code class="inline">' + esc(r.device_id || '-') + '</code></td>' +
            '<td>' + esc(r.client_ip || '-') + '</td>' +
            '<td>' + (r.src_url ? '<a href="' + esc(r.src_url) + '" target="_blank" rel="noopener noreferrer">原始链接</a>' : '-') + '</td>' +
            '<td>' + (r.out_url ? '<a href="' + esc(r.out_url) + '" target="_blank" rel="noopener noreferrer">下载</a>' : '-') + '</td>' +
            '<td>' + esc(fmtTime(r.created_at)) + '</td>' +
            '<td>' + (r.evidence_url
              ? '<button type="button" class="btn-link" data-evidence="' + esc(r.evidence_url) + '" data-evname="evidence-' + esc(r.task_no || 'task') + '">下载取证</button>'
              : '<span class="inline-hint">无成品</span>') + '</td>' +
            '</tr>';
        }).join('') + '</tbody></table></div>';

      $$('[data-evidence]', out).forEach(function (btn) {
        btn.addEventListener('click', function () {
          var url = btn.getAttribute('data-evidence');
          var a = document.createElement('a');
          a.href = url;
          a.download = btn.getAttribute('data-evname') || 'evidence';
          a.target = '_blank';
          a.rel = 'noopener noreferrer';
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
        });
      });
    }
  }

  /* ------------------------------ 意见反馈 ------------------------------ */

  var FEEDBACK_STATUS = { 0: '待处理', 1: '已处理' };
  var FEEDBACK_STATUS_CLASS = { 0: 'tag-warn', 1: 'tag-ok' };

  var feedbackState = { page: 1, limit: 20, status: '', keyword: '' };
  var feedbackFilterFields = [
    function (s) { return textFilter('keyword', '关键词', s.keyword, '内容 / 用户名 / 手机号', 'wide'); },
    function (s) { return selectFilter('status', '状态', optionsFrom(FEEDBACK_STATUS, '全部状态'), s.status); },
  ];
  (function () {
    var keys = [['keyword'], ['status']];
    feedbackFilterFields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
  })();

  async function renderFeedback(host) {
    if (!hasPerm('ticket.read')) { requirePermOrWarn('ticket.read'); return; }
    setPageHeader('意见反馈', 'App「我 → 求新功能」提交的意见与图片');
    var reload = function () { loadFeedback(host, reload); };
    loadFeedback(host, reload);
  }

  async function loadFeedback(host, reload) {
    var res = await apiGet('/feedback', feedbackState, { silent: true });
    if (res.code !== 0) { host.innerHTML = noticeBar('加载失败：' + esc(res.msg), 'danger'); return; }
    var data = res.data || {};
    var rows = data.rows || [];
    var pending = Number(data.pending) || 0;

    renderTable(host, {
      title: '意见反馈',
      state: feedbackState,
      fields: feedbackFilterFields,
      headActions: '<span class="inline-hint">待处理 <b>' + fmtInt(pending) + '</b> 条 / 累计 ' + fmtInt(data.total) + ' 条</span>',
      columns: [
        { key: 'id', title: 'ID', className: 'num' },
        {
          key: 'content', title: '意见内容', className: 'wrap',
          render: function (r) { return esc(truncate(r.content || '-', 60)); },
        },
        {
          key: 'images', title: '图片',
          render: function (r) {
            var list = r.image_urls || [];
            if (!list.length) return '<span class="inline-hint">无</span>';
            return list.map(function (u) {
              return '<a href="' + esc(u) + '" target="_blank" rel="noopener"><img src="' + esc(u) +
                '" style="width:34px;height:34px;object-fit:cover;border-radius:6px;margin-right:4px"></a>';
            }).join('');
          },
        },
        { key: 'username', title: '用户', render: function (r) { return esc(r.username || (r.user_id ? '#' + r.user_id : '游客')); } },
        { key: 'phone', title: '手机号', render: function (r) { return esc(r.phone || '-'); } },
        { key: 'model', title: '机型', render: function (r) { return esc(r.model || '-'); } },
        { key: 'status', title: '状态', render: function (r) { return tag(FEEDBACK_STATUS[Number(r.status)] || '-', FEEDBACK_STATUS_CLASS[Number(r.status)] || 'tag-gray'); } },
        { key: 'created_at', title: '提交时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) { return '<button type="button" class="btn-link" data-feedback="' + esc(r.id) + '">查看</button>'; },
        },
      ],
      rows: rows,
      total: data.total || 0,
      empty: '还没有收到意见反馈',
      reload: reload,
      onRowMount: function (container) {
        $$('[data-feedback]', container).forEach(function (btn) {
          btn.addEventListener('click', function () { openFeedbackDrawer(btn.getAttribute('data-feedback'), reload); });
        });
      },
    });
  }

  async function openFeedbackDrawer(id, reload) {
    var res = await apiGet('/feedback/' + encodeURIComponent(id));
    if (res.code !== 0) { toast(res.msg || '加载失败', 'error'); return; }
    var d = res.data || {};
    var f = d.feedback || {};
    var user = d.user || null;
    var images = f.image_urls || [];

    var body = '<div class="card"><div class="card-head"><h3>反馈 #' + esc(f.id) + '</h3>' +
      '<div class="card-actions">' + tag(FEEDBACK_STATUS[Number(f.status)] || '-', FEEDBACK_STATUS_CLASS[Number(f.status)] || 'tag-gray') + '</div></div>' +
      '<div class="card-body"><div class="dl dl-2">' +
      dlItem('提交时间', fmtTime(f.created_at)) +
      dlItem('处理时间', f.handled_at ? fmtTime(f.handled_at) : '-') +
      dlItem('用户', user ? user.username : (f.user_id ? '#' + f.user_id : '游客')) +
      dlItem('手机号', user ? (user.phone || '-') : '-') +
      dlItem('机型', f.model || '-') +
      dlItem('系统版本', f.os_version || '-') +
      dlItem('App 版本', versionLabel(f.app_version, f.version_code)) +
      dlItem('设备号', f.device_id || '-') +
      dlItem('联系方式', f.contact || '-') +
      '</div></div></div>';

    body += '<div class="card"><div class="card-head"><h3>意见内容</h3></div><div class="card-body">' +
      '<div style="white-space:pre-wrap;line-height:1.7">' + esc(f.content || '') + '</div></div></div>';

    body += '<div class="card"><div class="card-head"><h3>上传的图片（' + images.length + '）</h3></div><div class="card-body">' +
      (images.length ? '<div style="display:flex;gap:10px;flex-wrap:wrap">' + images.map(function (u) {
        return '<a href="' + esc(u) + '" target="_blank" rel="noopener"><img src="' + esc(u) +
          '" style="width:150px;height:150px;object-fit:cover;border-radius:10px;border:1px solid var(--c-border)"></a>';
      }).join('') + '</div>' : '<div class="empty-state">没有上传图片</div>') + '</div></div>';

    if (hasPerm('ticket.write')) {
      body += '<div class="card"><div class="card-head"><h3>处理</h3></div><div class="card-body">' +
        '<textarea class="textarea" rows="3" id="feedback-remark" placeholder="处理备注（只给后台看）">' + esc(f.remark || '') + '</textarea>' +
        '<div style="margin-top:10px;display:flex;gap:8px">' +
        '<button type="button" class="btn btn-primary btn-sm" id="feedback-done">标记已处理</button>' +
        '<button type="button" class="btn btn-sm" id="feedback-pending">标记待处理</button>' +
        '</div></div></div>';
    }

    openDrawer({
      title: '意见反馈 #' + (f.id || id),
      subtitle: user ? user.username : '游客提交',
      bodyHtml: body,
      onMount: function () {
        var save = async function (status) {
          var ta = document.getElementById('feedback-remark');
          var r = await apiPost('/feedback/' + encodeURIComponent(id), { status: status, remark: ta ? ta.value : '' });
          if (r.code !== 0) { toast(r.msg || '保存失败', 'error'); return; }
          toast(r.msg || '已保存', 'ok');
          if (reload) reload();
        };
        var done = document.getElementById('feedback-done');
        var pending = document.getElementById('feedback-pending');
        if (done) done.addEventListener('click', function () { save(1); });
        if (pending) pending.addEventListener('click', function () { save(0); });
      },
    });
  }

  /* ------------------------------ 客服工单 ------------------------------ */

  var ticketState = { page: 1, limit: 20, status: '', keyword: '', mine: '' };
  var ticketFilterFields = [
    function (s) { return textFilter('keyword', '关键词', s.keyword, '工单号 / 标题 / 用户名', 'wide'); },
    function (s) { return selectFilter('status', '状态', optionsFrom(TICKET_STATUS, '全部状态'), s.status); },
    function (s) { return selectFilter('mine', '范围', [{ value: '', label: '全部工单' }, { value: '1', label: '只看我的' }], s.mine); },
  ];
  (function () {
    var keys = [['keyword'], ['status'], ['mine']];
    ticketFilterFields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
  })();

  async function renderTicket(host) {
    if (!hasPerm('ticket.read')) { requirePermOrWarn('ticket.read'); return; }
    setPageHeader('客服工单', '工单处理、快捷回复模板、指派与关闭');
    var reload = function () { loadTickets(host, reload); };
    loadTickets(host, reload);
  }

  async function loadTickets(host, reload) {
    var res = await apiGet('/tickets', ticketState, { silent: true });
    if (res.code !== 0) { host.innerHTML = noticeBar('加载失败：' + esc(res.msg), 'danger'); return; }
    var data = res.data || {};
    var rows = data.rows || [];

    var headActions = '';
    if (hasPerm('ticket.write')) headActions += '<button type="button" class="btn btn-sm" data-act="templates">快捷回复模板</button>';

    renderTable(host, {
      title: '客服工单',
      state: ticketState,
      fields: ticketFilterFields,
      columns: [
        { key: 'ticket_no', title: '工单号', className: 'num' },
        { key: 'title', title: '标题', className: 'wrap', render: function (r) { return esc(truncate(r.title || '-', 40)); } },
        { key: 'username', title: '用户', render: function (r) { return esc(r.username || ('#' + (r.user_id || '-'))); } },
        { key: 'category', title: '分类', render: function (r) { return esc(TICKET_CATEGORY[Number(r.category)] || '-'); } },
        { key: 'priority', title: '优先级', render: function (r) { return tag(TICKET_PRIORITY[Number(r.priority)] || '-', TICKET_PRIORITY_CLASS[Number(r.priority)] || 'tag-gray'); } },
        { key: 'status', title: '状态', render: function (r) { return tag(TICKET_STATUS[Number(r.status)] || '-', TICKET_STATUS_CLASS[Number(r.status)] || 'tag-gray'); } },
        {
          key: 'sla_at', title: 'SLA',
          render: function (r) {
            if (!r.sla_at) return '<span class="inline-hint">-</span>';
            var left = dayDiff(r.sla_at);
            var overdue = new Date(String(r.sla_at).replace(/-/g, '/')).getTime() < Date.now() && Number(r.status) !== 3;
            return esc(fmtTime(r.sla_at)) + (overdue ? ' ' + tag('已超时', 'tag-danger') : (left !== null ? ' <span class="inline-hint">剩 ' + left + ' 天</span>' : ''));
          },
        },
        { key: 'message_count', title: '消息数', render: function (r) { return '<span class="num">' + fmtInt(r.message_count) + '</span>'; } },
        { key: 'last_reply_at', title: '最近回复', render: function (r) { return esc(fmtTime(r.last_reply_at)); } },
        { key: 'created_at', title: '创建时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) {
            return '<button type="button" class="btn-link" data-ticket="' + esc(r.ticket_no) + '">详情/回复</button>';
          },
        },
      ],
      rows: rows,
      total: data.total || 0,
      empty: '暂无工单',
      headActions: headActions,
      reload: reload,
      onRowMount: function (container) {
        $$('[data-ticket]', container).forEach(function (btn) {
          btn.addEventListener('click', function () { openTicketDrawer(btn.getAttribute('data-ticket'), reload); });
        });
        var tpl = $('[data-act="templates"]', container);
        if (tpl) tpl.addEventListener('click', function () { quickReplyManager(reload); });
      },
    });
  }

  async function openTicketDrawer(ticketNo, reload) {
    var res = await apiGet('/tickets/' + encodeURIComponent(ticketNo));
    if (res.code !== 0) return;
    var d = res.data || {};
    var t = d.ticket || {};
    var messages = d.messages || [];
    var user = d.user || null;
    var recentTasks = d.recent_tasks || [];

    var body = '';
    body += '<div class="card"><div class="card-head"><h3>工单信息</h3><div class="card-actions">' +
      tag(TICKET_STATUS[Number(t.status)] || '-', TICKET_STATUS_CLASS[Number(t.status)] || 'tag-gray') +
      tag(TICKET_PRIORITY[Number(t.priority)] || '-', TICKET_PRIORITY_CLASS[Number(t.priority)] || 'tag-gray') + '</div></div>' +
      '<div class="card-body"><div class="dl dl-2">' +
      dlItem('工单号', t.ticket_no) + dlItem('分类', TICKET_CATEGORY[Number(t.category)]) + dlItem('标题', t.title) +
      dlItem('处理人 ID', t.admin_id) + dlItem('消息数', t.message_count) +
      dlItem('SLA 截止', fmtTime(t.sla_at)) + dlItem('关闭时间', fmtTime(t.closed_at)) +
      dlItem('创建时间', fmtTime(t.created_at)) + dlItem('最近更新', fmtTime(t.updated_at)) +
      '</div><div style="margin-top:10px"><div class="field-label">提交上下文（设备/版本/最近任务）</div>' +
      jsonBlock(t.context) + '</div></div></div>';

    if (user) {
      body += '<div class="card"><div class="card-head"><h3>工单用户</h3></div><div class="card-body"><div class="dl dl-2">' +
        dlItem('用户 ID', user.id) + dlItem('用户名', user.username) + dlItem('手机号', user.phone) +
        dlItem('等级', LEVEL_TEXT[Number(user.level)] || user.level) +
        dlItem('状态', USER_STATUS[Number(user.status_effective === undefined ? user.status : user.status_effective)]) +
        dlItem('到期时间', fmtTime(user.expire_at)) +
        dlItem('累计消费', '¥ ' + fmtMoney(user.total_amount)) +
        dlItem('累计任务', user.total_task) +
        '</div></div></div>';
    }

    body += tableCard('用户最近任务', ['任务号', '平台', '状态', '错误信息', '创建时间'],
      recentTasks.map(function (x) {
        return ['<span class="num">' + esc(x.task_no || '') + '</span>', esc(x.platform || '-'),
          tag(TASK_STATUS[Number(x.status)] || '-', TASK_STATUS_CLASS[Number(x.status)] || 'tag-gray'),
          esc(truncate(x.err_msg || '-', 50)), esc(fmtTime(x.created_at))];
      }), '暂无任务');

    body += '<div class="card"><div class="card-head"><h3>消息流</h3></div><div class="card-body"><div class="timeline">' +
      (messages.length ? messages.map(function (m) {
        var isAdmin = Number(m.sender_type) === 2;
        return '<div class="timeline-item ' + (isAdmin ? 'is-admin' : 'is-user') + '">' +
          '<div class="timeline-meta"><span>' + (isAdmin ? '客服' : '用户') + (m.sender_id ? ' #' + esc(m.sender_id) : '') + '</span>' +
          '<span>' + esc(fmtTime(m.created_at)) + '</span></div>' +
          '<div class="timeline-body">' + esc(m.content || '') + '</div>' +
          (m.attachments && m.attachments !== '[]' ? '<div class="inline-hint" style="margin-top:4px">附件：' + esc(m.attachments) + '</div>' : '') +
          '</div>';
      }).join('') : '<div class="empty-state">暂无消息</div>') +
      '</div></div></div>';

    if (hasPerm('ticket.write')) {
      var replies = await apiGet('/quick-replies', null, { silent: true });
      var replyRows = (replies.code === 0 && replies.data && replies.data.rows) || [];
      body += '<div class="card"><div class="card-head"><h3>回复用户</h3>' +
        '<div class="card-actions"><select class="select" id="qr-select" style="min-width:220px">' +
        '<option value="">选择快捷回复模板…</option>' +
        replyRows.map(function (r) {
          return '<option value="' + esc(r.id) + '" data-content="' + esc(r.content || '') + '">' + esc(r.title || '') + '</option>';
        }).join('') + '</select></div></div>' +
        '<div class="card-body"><textarea class="textarea" rows="5" id="reply-content" placeholder="输入回复内容，支持 {用户名} {到期时间} {卡密} 变量（原样插入，由服务端/客户端替换）"></textarea>' +
        '<div style="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap">' +
        '<button type="button" class="btn btn-primary btn-sm" id="btn-reply">发送回复</button>' +
        '<button type="button" class="btn btn-sm" id="btn-assign">指派给我</button>' +
        '<button type="button" class="btn btn-sm btn-danger-text" id="btn-close-ticket">关闭工单</button>' +
        '</div><div class="form-note">变量占位符会原样写入回复内容，不会在后台替换。</div></div></div>';
    }

    openDrawer({
      title: '工单 ' + (t.ticket_no || ticketNo),
      subtitle: (t.title || '') + ' · ' + (user ? user.username : '未知用户'),
      bodyHtml: body,
      onMount: function (mask, close) {
        var sel = document.getElementById('qr-select');
        if (sel) {
          sel.addEventListener('change', function () {
            var opt = sel.options[sel.selectedIndex];
            var content = opt ? opt.getAttribute('data-content') : '';
            if (!content) return;
            var ta = document.getElementById('reply-content');
            ta.value = ta.value ? (ta.value + '\n' + content) : content;
            ta.focus();
          });
        }
        var send = document.getElementById('btn-reply');
        if (send) {
          send.addEventListener('click', async function () {
            var ta = document.getElementById('reply-content');
            if (!ta.value.trim()) { toast('回复内容不能为空', 'warn'); return; }
            var res2 = await apiPost('/tickets/' + encodeURIComponent(ticketNo) + '/reply', { content: ta.value });
            if (res2.code === 0) { toast('已回复', 'ok'); close(); reload(); }
          });
        }
        var assign = document.getElementById('btn-assign');
        if (assign) {
          assign.addEventListener('click', async function () {
            var res2 = await apiPost('/tickets/' + encodeURIComponent(ticketNo) + '/assign', { admin_id: state.admin ? state.admin.id : undefined });
            if (res2.code === 0) { toast('已指派给自己', 'ok'); close(); reload(); }
          });
        }
        var closeTicket = document.getElementById('btn-close-ticket');
        if (closeTicket) {
          closeTicket.addEventListener('click', async function () {
            var cf = await confirmDialog('确认关闭工单 <b>' + esc(ticketNo) + '</b>？', { title: '关闭工单', confirmText: '关闭工单', danger: false });
            if (!cf) return;
            var res2 = await apiPost('/tickets/' + encodeURIComponent(ticketNo) + '/close', {});
            if (res2.code === 0) { toast('已关闭', 'ok'); close(); reload(); }
          });
        }
      },
    });
  }

  async function quickReplyManager(reload) {
    var res = await apiGet('/quick-replies');
    if (res.code !== 0) return;
    var rows = (res.data && res.data.rows) || [];
    var body = '<div class="card-actions" style="margin-bottom:12px">' +
      '<button type="button" class="btn btn-primary btn-sm" data-qr="new">新增模板</button></div>';
    body += '<div class="table-wrap"><table class="tbl"><thead><tr>' +
      '<th>ID</th><th>标题</th><th>分类</th><th>排序</th><th>内容</th><th>操作</th></tr></thead><tbody>' +
      (rows.length ? rows.map(function (r) {
        return '<tr><td class="num">' + esc(r.id) + '</td><td>' + esc(r.title) + '</td>' +
          '<td>' + esc(r.category) + '</td><td class="num">' + esc(r.sort) + '</td>' +
          '<td class="wrap"><span class="inline-hint">' + esc(truncate(r.content, 70)) + '</span></td>' +
          '<td class="col-actions">' +
          '<button type="button" class="btn-link" data-qr="edit" data-id="' + esc(r.id) + '">编辑</button>' +
          '<button type="button" class="btn-link danger" data-qr="delete" data-id="' + esc(r.id) + '">删除</button>' +
          '</td></tr>';
      }).join('') : '<tr><td colspan="6" class="empty-state">暂无模板</td></tr>') + '</tbody></table></div>';

    openModal({
      title: '快捷回复模板管理',
      size: 'modal-lg',
      hideConfirm: true,
      cancelText: '关闭',
      bodyHtml: body,
      onMount: function (mask, close) {
        $$('[data-qr]', mask).forEach(function (btn) {
          btn.addEventListener('click', async function () {
            var action = btn.getAttribute('data-qr');
            if (action === 'new') {
              await quickReplyForm(null, function () { close(); quickReplyManager(reload); });
              return;
            }
            var id = Number(btn.getAttribute('data-id'));
            var row = rows.filter(function (r) { return Number(r.id) === id; })[0];
            if (action === 'edit') {
              await quickReplyForm(row, function () { close(); quickReplyManager(reload); });
              return;
            }
            var cf = await confirmDialog('确认删除模板「' + esc(row.title) + '」？', { title: '删除模板', confirmText: '删除' });
            if (!cf) return;
            var del = await apiDelete('/quick-replies/' + id, {});
            if (del.code === 0) { toast('已删除', 'ok'); close(); quickReplyManager(reload); }
          });
        });
      },
    });
  }

  function quickReplyForm(row, after) {
    var isEdit = !!row;
    return promptDialog({
      title: isEdit ? '编辑快捷回复模板' : '新增快捷回复模板',
      intro: '支持变量占位符：<code class="inline">{用户名}</code> <code class="inline">{到期时间}</code> <code class="inline">{卡密}</code>，插入时原样保留。',
      fields: [
        { key: 'title', label: '标题', required: true, value: isEdit ? row.title : '', placeholder: '例如：激活引导' },
        { key: 'category', label: '分类（1-9）', type: 'number', value: isEdit ? row.category : 1 },
        { key: 'sort', label: '排序（越小越前）', type: 'number', value: isEdit ? row.sort : 100 },
        { key: 'content', label: '内容', type: 'textarea', rows: 6, required: true, value: isEdit ? row.content : '', full: true },
      ],
      confirmText: isEdit ? '保存' : '创建',
    }).then(async function (values) {
      if (!values) return;
      var payload = {
        title: values.title,
        content: values.content,
        category: Number(values.category || 1),
        sort: Number(values.sort || 100),
      };
      var res = isEdit ? await apiPut('/quick-replies/' + row.id, payload) : await apiPost('/quick-replies', payload);
      if (res.code === 0) { toast('已保存', 'ok'); if (after) after(); }
    });
  }

  /* ------------------------------ 版本推送 ------------------------------ */

  /**
   * 指定版本（2026-10-17）——「这条更新只推给哪几个版本」。
   *
   * 留空 = 所有版本都收（原来的行为）；填了就只有名单里的来源版本会看到这条更新，
   * **强制更新也一样**：没被指定的版本连弹窗都不会出现。
   * 用勾选框而不是纯文本框，是因为运营记不住 version_code，只认得版本名。
   */
  /**
   * 版本号（1.2.3）与内部编码之间的换算 —— 必须与服务端 `core/version.js`、
   * 客户端 `app/build.gradle` 用同一套公式（major*10000 + minor*100 + patch）。
   *
   * <p>**界面上只用版本号**：内部编码是给客户端比大小用的实现细节，不该让运营看到、
   * 更不该让他手填（这就是之前「编码 10000/10001」满屏飞的根源）。
   */
  function nameToCode(name) {
    var cleaned = String(name || '').trim().replace(/^[vV]/, '');
    var parts = cleaned.split('.');
    if (!cleaned || parts.length > 3) return null;
    var nums = [];
    for (var i = 0; i < parts.length; i++) {
      if (!/^\d+$/.test(parts[i])) return null;
      nums.push(Number(parts[i]));
    }
    while (nums.length < 3) nums.push(0);
    if (nums[0] > 210000 || nums[1] > 99 || nums[2] > 99) return null;
    return nums[0] * 10000 + nums[1] * 100 + nums[2];
  }

  /** 编码 → 版本号；老包用的裸编码（1/2/3）无法还原时返回空串，由调用方回落到上报的名字 */
  function codeToName(code) {
    var n = Number(code);
    if (!Number.isFinite(n) || n < 10000) return '';
    return [Math.floor(n / 10000), Math.floor(n / 100) % 100, n % 100].join('.');
  }

  /** 给一批编码找「给人看的版本号」：优先按公式还原，其次用记录里的名字，最后兜底显示编码 */
  function namesForCodes(codes, versions, clientVersions) {
    var pool = [].concat(versions || [], clientVersions || []);
    return codes.map(function (c) {
      var n = Number(c);
      var derived = codeToName(n);
      if (derived) return derived;
      var hit = pool.filter(function (x) { return Number(x.version_code) === n; })[0];
      return (hit && hit.version_name) || String(n);
    });
  }

  /**
   * 给一处「版本」显示取个统一文案：**只给版本号，不给内部编码**。
   *
   * 优先按公式还原（10001 → 1.0.1，这是编码的真实含义）；还原不了才回落到客户端上报的名字；
   * 都没有就显示编码（老数据兜底）。全后台的版本展示都走它，避免又冒出「1.0.1 (10001)」这种。
   */
  function versionLabel(name, code) {
    var derived = codeToName(code);
    if (derived) return derived;
    if (name) return String(name);
    return code ? String(code) : '-';
  }

  /** 版本号 +0.0.1（进位：1.0.99 → 1.1.0，1.99.99 → 2.0.0）；与服务端 bumpVersionName 一致 */
  function bumpVersionName(name) {
    var m = String(name || '').trim().replace(/^[vV]/, '').split('.');
    var nums = [];
    for (var i = 0; i < m.length && i < 3; i++) {
      if (!/^\d+$/.test(m[i])) return '';
      nums.push(Number(m[i]));
    }
    while (nums.length < 3) nums.push(0);
    if (nums[2] < 99) return nums[0] + '.' + nums[1] + '.' + (nums[2] + 1);
    if (nums[1] < 99) return nums[0] + '.' + (nums[1] + 1) + '.0';
    return (nums[0] + 1) + '.0.0';
  }

  /**
   * 新建版本时的默认版本号 = **当前最高版本 + 0.0.1**。
   *
   * 「当前最高」取「客户端在用的最高」与「后台已发布的最高」之中的较大者 ——
   * 只按已发布记录算的话，把旧记录删掉后默认值会退回 1.0.0，反而不如手机上在跑的版本大，
   * 发出去没人收得到（这正是之前踩过的坑）。
   */
  function defaultVersionName(versions, clientVersions) {
    var pool = [].concat(versions || [], clientVersions || []);
    var maxCode = 0;
    pool.forEach(function (v) {
      var c = Number(v.version_code) || 0;
      if (c > maxCode) maxCode = c;
    });
    if (!maxCode) return { name: '1.0.0', base: '', reason: '还没有任何版本记录，先给个 1.0.0' };
    var base = codeToName(maxCode);
    var record = pool.filter(function (v) { return Number(v.version_code) === maxCode; })[0];
    var baseName = base || (record && record.version_name) || '';
    var next = bumpVersionName(baseName);
    if (!next) return { name: '1.0.0', base: '', reason: '现有版本号看不出规律，请手动确认' };
    return {
      name: next,
      base: baseName,
      reason: '按当前最高版本 ' + baseName + ' 自动 +0.0.1，可直接改成别的',
    };
  }

  /**
   * 指定版本（2026-10-17）——「这条更新推给哪些版本的客户端」。
   *
   * 候选来自**客户端实际上报过的版本**（`client_version`，接口 `/version/check` 每次都会记一笔），
   * 再并上后台已发布的版本记录。为什么不只用已发布的记录：运营把旧版本记录删掉之后，
   * 勾选列表就空了 —— 可用户手机上还跑着那个版本，需要的正是「推给它」。
   *
   * 留空 = 所有版本都收；勾选/填写后，只有名单里的来源版本会看到这条更新，
   * **强制更新也一样**（没被指定的版本连弹窗都不会出现）。
   *
   * 界面上**只出现版本号**：勾选框的 value 仍是内部编码（服务端匹配要用），但那串数字不显示，
   * 输入框里填的也是 `1.0.0,1.0.1`，提交前才换算成编码。
   */
  function targetVersionsField(row, versions, clientVersions) {
    var currentCodes = String((row && row.target_versions) || '')
      .split(',').map(function (s) { return s.trim(); }).filter(Boolean).map(Number);
    var selfCode = Number(row && row.version_code) || 0;
    var selfName = (row && row.version_name) || codeToName(selfCode) || '本次版本号';

    // 候选 = 客户端上报过的（带使用次数，优先） + 后台发布过的，按编码去重
    var byCode = {};
    (clientVersions || []).forEach(function (c) {
      var code = Number(c.version_code);
      if (!code) return;
      byCode[code] = { version_code: code, version_name: c.version_name || '', hits: Number(c.hit_count) || 0 };
    });
    (versions || []).forEach(function (v) {
      var code = Number(v.version_code);
      if (!code || byCode[code]) return;
      byCode[code] = { version_code: code, version_name: v.version_name || '', hits: 0 };
    });
    var options = Object.keys(byCode).map(function (k) { return byCode[k]; })
      .filter(function (v) { return v.version_code !== selfCode; })   // 自己不用勾
      .sort(function (a, b) { return b.version_code - a.version_code; });

    var boxes = options.map(function (v) {
      // 显示名一律用版本号：能按公式还原就还原（10001 → 1.0.1），老包才回落到上报的名字
      var label = versionLabel(v.version_name, v.version_code);
      if (v.hits) label += ' · 有设备在用';
      return '<label class="checkbox" style="display:inline-flex;margin:0 16px 6px 0">' +
        '<input type="checkbox" data-target="' + esc(v.version_code) + '"' +
        (currentCodes.indexOf(Number(v.version_code)) >= 0 ? ' checked' : '') + '> ' +
        esc(label) + '</label>';
    }).join('') || '<span class="inline-hint">还没有客户端上报过版本（等 App 联网上报后这里会出现）</span>';

    // 输入框里显示**版本号**（1.0.0,1.0.1），提交时再换算；这样运营永远不用碰编码
    var currentNames = namesForCodes(currentCodes, versions, clientVersions).join(',');

    return '<label class="field span-2"><span class="field-label">指定版本（推给哪些版本的客户端）</span>' +
      '<div style="margin:2px 0 8px">' + boxes + '</div>' +
      '<input class="input" type="text" data-field="target_versions" value="' + esc(currentNames) + '"' +
      ' placeholder="留空 = 所有版本都收；也可以直接填 1.0.0,1.0.1">' +
      '<span class="form-note">勾选上面的版本，或直接填版本号（英文逗号分隔，例如 <b>1.0.0,1.0.1</b>）。' +
      '<b>留空表示所有版本都会收到</b>；一旦指定，没被选中的版本不会收到任何提示 —— <b>强制更新也一样</b>。' +
      '<br>注意：被指定的版本号必须<b>小于</b>本次版本号（' + esc(selfName) + '），' +
      '客户端才会认为「有更新」；大于等于本次的版本号永远不会收到。</span></label>';
  }

  /**
   * 上传安装包（原始二进制流 + XHR，才有进度条）。
   *
   * 为什么不用现成的 apiPost：它走 JSON、而且服务端对普通请求是「整个 body 读进内存」
   * （默认 2MB 上限）—— 36MB 的 APK 必须走流式专用接口 `/versions/upload`。
   */
  function uploadApkFile(file, onProgress) {
    return new Promise(function (resolve) {
      if (!file) { resolve({ code: -1, msg: '没有选择文件' }); return; }
      if (!/\.apk$/i.test(file.name)) { resolve({ code: -1, msg: '请选择 .apk 文件' }); return; }
      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/admin/api/versions/upload?name=' + encodeURIComponent(file.name));
      var token = getToken();
      if (token) xhr.setRequestHeader('X-Admin-Token', token);
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');
      if (xhr.upload && onProgress) {
        xhr.upload.onprogress = function (e) {
          if (e.lengthComputable) onProgress(e.loaded / e.total);
        };
      }
      xhr.onload = function () {
        var body = null;
        try { body = JSON.parse(xhr.responseText); } catch (e) { body = null; }
        resolve(body || { code: -1, msg: '服务端返回异常（HTTP ' + xhr.status + '）' });
      };
      xhr.onerror = function () { resolve({ code: -1, msg: '网络中断，上传失败' }); };
      xhr.send(file);
    });
  }

  /** 打开文件选择器并上传，成功后回调拿到 { name, size, url } */
  function pickAndUploadApk(onDone) {
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = '.apk,application/vnd.android.package-archive';
    input.style.display = 'none';
    document.body.appendChild(input);
    input.addEventListener('change', function () {
      var file = input.files && input.files[0];
      document.body.removeChild(input);
      if (!file) return;
      var mask = openModal({
        title: '上传安装包',
        size: 'modal-sm',
        bodyHtml: '<div style="font-size:13.5px;line-height:1.8">' +
          '<b>' + esc(file.name) + '</b>（' + esc(fmtBytes(file.size)) + '）<br>' +
          '<div style="margin-top:10px;height:8px;background:#eef0f5;border-radius:4px;overflow:hidden">' +
          '<div data-progress style="height:100%;width:0;background:#1677ff;transition:width .2s"></div></div>' +
          '<div data-pct style="margin-top:6px;color:#5a5a6e">0%</div>' +
          '<div style="margin-top:8px;color:#939292;font-size:12.5px">大包上传需要一点时间，请不要关闭页面。</div></div>',
        confirmText: '关闭',
        onConfirm: function () { return true; },
      });
      var bar = $('[data-progress]', mask), pct = $('[data-pct]', mask);
      uploadApkFile(file, function (ratio) {
        var p = Math.round(ratio * 100);
        if (bar) bar.style.width = p + '%';
        if (pct) pct.textContent = p + '%';
      }).then(function (res) {
        if (bar) bar.style.width = '100%';
        if (pct) pct.textContent = res.code === 0 ? '完成' : (res.msg || '失败');
        if (res.code === 0) {
          toast('已上传 ' + res.data.name + '（' + fmtBytes(res.data.size) + '）', 'ok');
          if (onDone) onDone(res.data);
        } else {
          toast(res.msg || '上传失败', 'error');
        }
      });
    });
    input.click();
  }

  async function renderVersion(host) {
    if (!hasPerm('version.read')) { requirePermOrWarn('version.read'); return; }
    setPageHeader('版本管理', '发布新版本、指定推给哪些客户端版本、管理安装包');
    var res = await apiGet('/versions', null, { silent: true });
    if (res.code !== 0) { host.innerHTML = noticeBar('加载失败：' + esc(res.msg), 'danger'); return; }
    var rows = (res.data && res.data.rows) || [];
    // 客户端实际上报过的版本 —— 「指定版本」的勾选列表用它（后台把旧版本记录删了也还能勾）
    var clientVersions = (res.data && res.data.client_versions) || [];
    var filesRes = await apiGet('/versions/files', null, { silent: true });
    var apkFiles = (filesRes.code === 0 && filesRes.data && filesRes.data.files) || [];

    // 「这个版本会推给谁」：拿客户端上报过的版本去套它的指定名单，一眼看出有没有人收得到。
    // 一律用**版本号**呈现（运营不认内部编码）。
    function reachableText(v) {
      var code = Number(v.version_code);
      if (Number(v.status) !== 1) return '<span class="tag" style="background:#f1f2f6;color:#5a5a6e">已下线</span>';
      var targets = String(v.target_versions || '').split(',').map(function (s) { return Number(s.trim()); })
        .filter(function (n) { return n > 0; });
      if (!targets.length) return '<span class="tag" style="background:#e8f5ee;color:#00a870">全部版本</span>';
      var hit = targets.filter(function (t) { return t < code; });
      if (!hit.length) {
        return '<span class="tag" style="background:#fdecec;color:#d93026">没人收得到</span>' +
          '<div class="inline-hint">指定的版本都不比这个版本旧</div>';
      }
      var devices = clientVersions.filter(function (c) { return hit.indexOf(Number(c.version_code)) >= 0; });
      return '<span class="tag">' + esc(namesForCodes(hit, rows, clientVersions).join(' / ')) + '</span>' +
        '<div class="inline-hint">共 ' + devices.length + ' 个在用版本命中</div>';
    }

    var versionRows = rows.length ? rows.map(function (v) {
      return '<tr>' +
        '<td><b>' + esc(v.version_name || '-') + '</b></td>' +
        '<td>' + (v.platform === 'ios' ? 'iOS' : 'Android') + '</td>' +
        '<td>' + boolTag(Number(v.force) === 1, '必须更新', '选择更新') + '</td>' +
        '<td>' + esc(v.gray_percent) + '%</td>' +
        '<td>' + reachableText(v) + '</td>' +
        '<td>' + boolTag(Number(v.status) === 1, '已发布', '已下线') + '</td>' +
        '<td>' + esc(fmtBytes(v.size)) + '</td>' +
        '<td>' + esc(fmtTime(v.published_at || v.created_at)) + '</td>' +
        '<td class="wrap">' + (v.url
          ? '<a class="btn-link" href="' + esc(v.url) + '" target="_blank" rel="noopener noreferrer">' +
            esc(truncate(String(v.url).replace('https://tdapi.luoyun.xyz/upgrade/app/', ''), 32)) + '</a>'
          : '<span class="inline-hint">没填下载地址</span>') + '</td>' +
        '<td class="col-actions">' +
          (hasPerm('version.write')
            ? '<button type="button" class="btn-link" data-ver="edit" data-id="' + esc(v.id) + '">编辑</button>' +
              '<button type="button" class="btn-link" data-ver="toggle" data-id="' + esc(v.id) + '">' +
              (Number(v.status) === 1 ? '下线' : '发布') + '</button>' +
              '<button type="button" class="btn-link danger" data-ver="delete" data-id="' + esc(v.id) + '">删除</button>'
            : '<span class="inline-hint">无权限</span>') +
        '</td></tr>';
    }).join('') : '<tr><td colspan="11" class="empty-state">还没有发布过任何版本</td></tr>';

    var clientRows = clientVersions.length ? clientVersions.map(function (c) {
      var derived = codeToName(c.version_code);
      var name = c.version_name || '';
      return '<tr>' +
        '<td><b>' + esc(derived || name || '-') + '</b>' +
          (derived && name && name !== derived ? '<span class="inline-hint">（客户端上报：' + esc(name) + '）</span>' : '') +
        '</td>' +
        '<td>' + esc(c.hit_count) + ' 次</td>' +
        '<td>' + esc(fmtTime(c.last_at)) + '</td>' +
        '<td class="col-actions">' +
          (hasPerm('version.write')
            ? '<button type="button" class="btn-link danger" data-client="del" data-code="' + esc(c.version_code) + '"' +
              ' data-name="' + esc(versionLabel(name, c.version_code)) + '">移除</button>'
            : '') +
        '</td>' +
        '</tr>';
    }).join('') : '<tr><td colspan="4" class="empty-state">还没有客户端上报过版本（App 联网查一次更新就会出现）</td></tr>';

    var fileRows = apkFiles.length ? apkFiles.map(function (f) {
      return '<tr>' +
        '<td><b>' + esc(f.name) + '</b></td>' +
        '<td>' + esc(fmtBytes(f.size)) + '</td>' +
        '<td>' + esc(fmtTime(f.mtime)) + '</td>' +
        '<td class="col-actions">' +
          '<button type="button" class="btn-link" data-file="copy" data-url="' + esc(f.url) + '">复制地址</button>' +
          (hasPerm('version.write')
            ? '<button type="button" class="btn-link danger" data-file="del" data-name="' + esc(f.name) + '">删除</button>'
            : '') +
        '</td></tr>';
    }).join('') : '<tr><td colspan="4" class="empty-state">还没有上传过安装包</td></tr>';

    host.innerHTML =
      noticeBar(
        '发布新版本只要三步：<b>① 上传 APK</b>（或填已有地址）→ <b>② 填版本号</b>（1.2.3 这种，要比手机上在用的更大）' +
          '→ <b>③ 勾选推给哪些版本</b>（留空=全部）。<br>' +
          '客户端只在「线上版本号 &gt; 本机版本号」时才提示更新，所以版本号必须比目标版本大。',
        'primary',
      ) +
      '<div class="card"><div class="card-head"><h3>已发布版本</h3><div class="card-actions">' +
        (hasPerm('version.write') ? '<button type="button" class="btn btn-primary btn-sm" data-act="new">新建版本</button>' : '') +
      '</div></div><div class="table-wrap"><table class="tbl"><thead><tr>' +
      '<th>版本号</th><th>平台</th><th>升级策略</th><th>灰度</th><th>会推给谁</th><th>状态</th>' +
      '<th>包大小</th><th>发布时间</th><th>安装包</th><th>操作</th>' +
      '</tr></thead><tbody>' + versionRows + '</tbody></table></div></div>' +

      '<div class="card"><div class="card-head"><h3>客户端在用版本</h3>' +
        '<span class="inline-hint">App 每次查更新都会上报；「指定版本」勾的就是这些</span></div>' +
        '<div class="table-wrap"><table class="tbl"><thead><tr>' +
        '<th>版本号</th><th>上报次数</th><th>最近上报</th><th>操作</th>' +
        '</tr></thead><tbody>' + clientRows + '</tbody></table></div></div>' +

      '<div class="card"><div class="card-head"><h3>安装包文件</h3><div class="card-actions">' +
        (hasPerm('version.write') ? '<button type="button" class="btn btn-primary btn-sm" data-act="upload">上传 APK</button>' : '') +
      '</div></div><div class="table-wrap"><table class="tbl"><thead><tr>' +
      '<th>文件名</th><th>大小</th><th>上传时间</th><th>操作</th>' +
      '</tr></thead><tbody>' + fileRows + '</tbody></table></div></div>';

    var add = $('[data-act="new"]', host);
    if (add) add.addEventListener('click', function () { versionForm(null, function () { renderVersion(host); }, rows, clientVersions, apkFiles); });
    var up = $('[data-act="upload"]', host);
    if (up) up.addEventListener('click', function () { pickAndUploadApk(function () { renderVersion(host); }); });

    $$('[data-file]', host).forEach(function (btn) {
      btn.addEventListener('click', async function () {
        if (btn.getAttribute('data-file') === 'copy') {
          var url = btn.getAttribute('data-url');
          try {
            await navigator.clipboard.writeText(url);
            toast('下载地址已复制', 'ok');
          } catch (e) {
            toast(url, 'ok');
          }
          return;
        }
        var name = btn.getAttribute('data-name');
        var ok = await confirmDialog('确认删除安装包 <b>' + esc(name) + '</b>？<br>引用了它的版本记录会下载不到，记得一并处理。', { title: '删除安装包', confirmText: '删除' });
        if (!ok) return;
        var del = await apiDelete('/versions/files/' + encodeURIComponent(name), {});
        if (del.code === 0) { toast('已删除', 'ok'); renderVersion(host); }
      });
    });

    $$('[data-client]', host).forEach(function (btn) {
      btn.addEventListener('click', async function () {
        var name = btn.getAttribute('data-name');
        var code = btn.getAttribute('data-code');
        var ok = await confirmDialog(
          '把 <b>' + esc(name) + '</b> 从「客户端在用版本」里移除？<br>' +
            '<span class="inline-hint">只是清掉这条统计记录；只要还有设备在跑这个版本，下次查更新时它会重新出现。</span>',
          { title: '移除在用版本', confirmText: '移除' },
        );
        if (!ok) return;
        var del = await apiDelete('/versions/client-versions/' + encodeURIComponent(code), {});
        if (del.code === 0) { toast('已移除', 'ok'); renderVersion(host); }
      });
    });

    $$('[data-ver]', host).forEach(function (btn) {
      btn.addEventListener('click', async function () {
        var id = Number(btn.getAttribute('data-id'));
        var row = rows.filter(function (r) { return Number(r.id) === id; })[0];
        var action = btn.getAttribute('data-ver');
        if (action === 'edit') { versionForm(row, function () { renderVersion(host); }, rows, clientVersions, apkFiles); return; }
        if (action === 'toggle') {
          var next = Number(row.status) === 1 ? 0 : 1;
          var put = await apiPut('/versions/' + id, { status: next }, {});
          if (put.code === 0) { toast(next === 1 ? '已发布' : '已下线', 'ok'); renderVersion(host); }
          return;
        }
        var cf = await confirmDialog('确认删除版本 <b>' + esc(versionLabel(row.version_name, row.version_code)) + '</b>？', { title: '删除版本', confirmText: '删除' });
        if (!cf) return;
        var del = await apiDelete('/versions/' + id, {});
        if (del.code === 0) { toast('已删除', 'ok'); renderVersion(host); }
      });
    });
  }

  function versionForm(row, after, versions, clientVersions, apkFiles) {
    var isEdit = !!row;
    // 「1.2.3」→ 客户端比对的整数（与服务端 core/version.js、客户端 build.gradle 同一套公式）
    function codeOf(name) {
      var cleaned = String(name || '').trim().replace(/^[vV]/, '');
      var parts = cleaned.split('.');
      if (!cleaned || parts.length > 3) return null;
      var nums = [];
      for (var i = 0; i < parts.length; i++) {
        if (!/^\d+$/.test(parts[i])) return null;
        nums.push(Number(parts[i]));
      }
      while (nums.length < 3) nums.push(0);
      if (nums[0] > 210000 || nums[1] > 99 || nums[2] > 99) return null;
      return nums[0] * 10000 + nums[1] * 100 + nums[2];
    }
    // 客户端**正在使用**的最大版本号：要推给这些手机，本次版本号必须比它大，
    // 否则客户端认为「没有更新」（这是「勾了也推不到」的头号原因）
    var maxClient = (clientVersions || []).reduce(function (m, v) {
      return Math.max(m, Number(v.version_code) || 0);
    }, 0);
    var maxClientName = (clientVersions || []).filter(function (v) {
      return Number(v.version_code) === maxClient;
    })[0];
    var maxPublishedName = (versions || []).filter(function (v) {
      return Number(v.status) === 1;
    })[0];
    var hintBits = [];
    if (maxClient) {
      hintBits.push('客户端在用的是 ' + ((maxClientName && maxClientName.version_name) || ('版本号 ' + maxClient)));
    }
    if (maxPublishedName) {
      hintBits.push('已发布的最新是 ' + maxPublishedName.version_name);
    }
    var codeHint = isEdit
      ? '改成别的版本号会一并更新内部编码（客户端按编码比大小）'
      : '按 1.2.3 这种格式填（也可以写 1 或 1.2）。' +
        (hintBits.length ? hintBits.join('，') + '；' : '') +
        '要比它们大，客户端才会提示更新。同一个平台下不能重复。';
    // 新建时默认填好「当前最高版本 +0.0.1」，管理员想改直接改
    var preset = isEdit ? null : defaultVersionName(versions, clientVersions);
    if (!isEdit && preset) {
      codeHint = preset.reason + '。' + (hintBits.length ? '（' + hintBits.join('，') + '）' : '') +
        ' 要比客户端在用的版本大，它们才会提示更新；同一个平台下不能重复。';
    }
    // 平台只有两种：客户端本来就只有 Android / iOS 包，多给选项只会选出客户端不认的数据
    var platforms = [
      { value: 'android', label: 'Android' },
      { value: 'ios', label: 'iOS' },
    ];
    var html = noticeBar(
      '填这四项就够了：<b>版本号</b>、<b>平台</b>、<b>更新公告</b>、<b>下载地址</b>。' +
        '下载地址可以直接「上传 APK」让系统填好，也可以粘一个 http/https 直链。' +
        '<br>版本号按 <b>1.2.3</b> 这种格式填，<b>必须比客户端当前版本大</b>才会推送出去；同一个平台下不能重复。' +
        '<br><b>更新方式</b>：选「选择更新」用户可以点稍后再用；选<b>「必须更新」旧版本立即作废</b>——' +
        '客户端只给「立即更新」，按返回键或想关掉弹窗就<b>直接退出 App</b>，不更新就进不去。',
      'primary',
    ) + '<div class="form-grid">' +
      formField('版本号', inputHtml('version_name', isEdit ? row.version_name : (preset ? preset.name : ''), { placeholder: '1.0.1' }), {
        required: true, note: codeHint, span: true,
      }) +
      formField('平台', selectHtml('platform', platforms, isEdit ? row.platform : 'android'), {
        required: true, note: isEdit ? '创建后不可修改' : '只有 Android / iOS',
      }) +
      formField('下载地址', inputHtml('url', isEdit ? row.url : '', { mono: true }), {
        required: true, span: true,
        note: 'http:// 或 https:// 直链，**必须以 .apk 结尾**（否则用户点了「立即更新」只会打开一个网页，装不上）。不想手填就点下面的「上传 APK」，传完自动填好。',
      }) +
      (hasPerm('version.write')
        ? '<label class="field span-2"><span class="field-label">高级</span>' +
          checkboxHtml('allow_any_url', '允许任意地址（不是 .apk 也放行，仅在你清楚后果时勾选）', false) +
          '<span class="form-note">默认会拦住不像安装包的地址 —— 这张网是防「填了网页地址、用户下载不到东西」的。</span></label>'
        : '') +
      (hasPerm('version.write')
        ? '<label class="field span-2"><span class="field-label">或者直接上传安装包</span>' +
          '<div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">' +
          '<button type="button" class="btn btn-sm" data-act="upload-apk">上传 APK</button>' +
          '<span class="inline-hint" data-upload-hint>' +
          (apkFiles && apkFiles.length
            ? '服务器上已有 ' + apkFiles.length + ' 个包：' + esc(apkFiles.slice(0, 3).map(function (f) { return f.name; }).join('、')) +
              (apkFiles.length > 3 ? ' 等' : '')
            : '还没有上传过安装包') +
          '</span></div>' +
          '<span class="form-note">上传成功后会自动填入上面的下载地址。文件先落到临时文件、校验通过才改名，传一半断了线上也不会出现半个包。</span></label>'
        : '') +
      formField('更新公告', textareaHtml('changelog', isEdit ? row.changelog : '', { rows: 5 }), {
        span: true, note: '写这次更新了什么，客户端升级弹窗会原样展示',
      }) +
      formField('更新方式',
        selectHtml('update_mode', [
          { value: 'optional', label: '选择更新（用户可稍后再更新，能继续用）' },
          { value: 'force', label: '必须更新（旧版本作废，不更新进不去）' },
        ], isEdit ? (Number(row.force) === 1 ? 'force' : 'optional') : 'optional'),
        { span: true, note: '选「必须更新」后：客户端只显示「立即更新」，按返回键或想关掉弹窗就直接退出 App，旧版本彻底用不了' }) +
      targetVersionsField(row, versions, clientVersions) +
      '</div>' +
      '<details style="margin-top:12px"><summary style="cursor:pointer;color:#5a5a6e;font-size:13px">高级设置（可选）</summary>' +
      '<div class="form-grid" style="margin-top:10px">' +
      formField('渠道', inputHtml('channel', isEdit ? row.channel : 'all'), { note: '默认 all，多渠道分发时才填' }) +
      formField('包大小（字节）', inputHtml('size', isEdit ? row.size : '', { type: 'number' }), { note: '填了客户端能显示下载大小' }) +
      formField('灰度比例 %', inputHtml('gray_percent', isEdit ? row.gray_percent : 100, { type: 'number' })) +
      formField('最低兼容版本号', inputHtml('min_version', isEdit ? row.min_version : '', { type: 'number' })) +
      formField('差分包地址', inputHtml('patch_url', isEdit ? row.patch_url : '', { mono: true }), { span: true }) +
      formField('差分包 MD5', inputHtml('patch_md5', isEdit ? row.patch_md5 : '', { mono: true })) +
      formField('发布状态', switchHtml('status', isEdit ? Number(row.status) === 1 : true, '启用该版本'), { span: true }) +
      '</div></details>' +
      (isEdit && hasPerm('version.write')
        ? '<div style="margin-top:14px;padding-top:12px;border-top:1px solid #ececf1">' +
          '<button type="button" class="btn btn-sm" data-act="delete-version" style="border-color:#f3c2c2;color:#d93026">删除这个版本</button>' +
          '<span class="inline-hint" style="margin-left:10px">删掉这条推送记录；已升级的手机不受影响</span></div>'
        : '');
    openModal({
      title: isEdit ? '编辑版本' : '新建版本',
      size: 'modal-lg',
      bodyHtml: html,
      confirmText: '保存',
      onMount: function (mask) {
        // 「上传 APK」——传完把地址和大小回填进表单，运营不用碰命令行
        var btn = $('[data-act="upload-apk"]', mask);
        if (btn) {
          btn.addEventListener('click', function () {
            pickAndUploadApk(function (data) {
              var urlInput = $('[data-field="url"]', mask);
              var sizeInput = $('[data-field="size"]', mask);
              var hint = $('[data-upload-hint]', mask);
              if (urlInput) urlInput.value = data.url;
              if (sizeInput) sizeInput.value = data.size;
              if (hint) hint.textContent = '已填入：' + data.name + '（' + fmtBytes(data.size) + '）';
            });
          });
        }
        // 编辑时也放一个「删除这个版本」：从列表里删要滚到右边找，不如在正在看的这一页给到
        var delBtn = $('[data-act="delete-version"]', mask);
        if (delBtn) {
          delBtn.addEventListener('click', async function () {
            var okDel = await confirmDialog(
              '确认删除版本 <b>' + esc(versionLabel(row.version_name, row.version_code)) + '</b>？<br>' +
                '<span class="inline-hint">删除后这条推送记录就没了（已经装了新版的手机不受影响）。</span>',
              { title: '删除版本', confirmText: '删除' },
            );
            if (!okDel) return;
            var res = await apiDelete('/versions/' + row.id, {});
            if (res.code === 0) {
              toast('已删除', 'ok');
              mask.close();
              if (after) after();
            }
          });
        }
      },
      onConfirm: async function (mask) {
        var f = readFields(mask);
        var payload = {
          version_name: f.version_name, channel: f.channel || 'all', url: f.url,
          patch_url: f.patch_url || null, patch_md5: f.patch_md5 || null,
          size: f.size === null || f.size === '' ? null : Number(f.size),
          changelog: f.changelog || '',
          update_mode: f.update_mode === 'force' ? 'force' : 'optional',
          gray_percent: Number(f.gray_percent === null || f.gray_percent === '' ? 100 : f.gray_percent),
          min_version: f.min_version === null || f.min_version === '' ? null : Number(f.min_version),
          // 指定版本：勾选框（内部编码）+ 输入框（**版本号**，如 1.0.0,1.0.1）合并成给服务端的编码串
          target_versions: (function () {
            var picked = $$('input[data-target]', mask).filter(function (b) { return b.checked; })
              .map(function (b) { return String(b.getAttribute('data-target')); });
            var typed = String(f.target_versions || '').split(',').map(function (s) { return s.trim(); }).filter(Boolean);
            var badName = null;
            var typedCodes = typed.map(function (name) {
              var code = nameToCode(name);
              if (code === null && badName === null) badName = name;
              return code === null ? null : String(code);
            }).filter(Boolean);
            var all = picked.concat(typedCodes);
            var uniq = [];
            all.forEach(function (v) { if (uniq.indexOf(v) < 0) uniq.push(v); });
            // 勾选框反映的就是输入框内容，两者取并集后按数字排序
            return uniq.sort(function (a, b) { return Number(a) - Number(b); }).join(',');
          })(),
          status: f.status ? 1 : 0,
        };
        if (!isEdit) payload.platform = f.platform || 'android';
        // 「允许任意地址」：显式勾选才放行不像安装包的地址
        payload.allow_any_url = f.allow_any_url === true;
        if (!payload.version_name || !payload.url) {
          toast('版本号和下载地址为必填', 'warn');
          return false;
        }
        // 版本号按 1.2.3 校验；内部整数编码由服务端按同一公式换算，这里不传
        if (codeOf(payload.version_name) === null) {
          toast('版本号请填成 1.2.3 这样的格式（次版本号、修订号不超过 99）', 'warn');
          return false;
        }
        var res = isEdit ? await apiPut('/versions/' + row.id, payload) : await apiPost('/versions', payload);
        if (res.code !== 0) return false;
        var notes = (res.data && res.data.notes) || [];
        // 只有服务端明确标为 blocker 的才拦（版本号没比线上大、指定版本里有收不到的目标）。
        // ⚠️ 别再拿提示文案去匹配关键字：之前 `/不会收到/` 把「本次只推给 X，其他版本不会收到提示」
        // 这条**正常说明**也当成拦截条件，导致「1.1.0 只推给 1.0.0/1.0.1」被误弹警告。
        var blockers = (res.data && res.data.blockers) || [];
        if (blockers.length) {
          var ok = await confirmDialog(
            '<b style="color:#d93026">这条版本发布后，可能谁都收不到更新：</b><br><br>' +
              blockers.map(function (n) { return '· ' + esc(n); }).join('<br>') +
              '<br><br>建议：把<b>版本号</b>改成比手机上正在用的版本更大的数字，' +
              '或者把「指定版本」里那些收不到的版本去掉。<br><br>' +
              '仍然要按现在的内容发布吗？',
            { title: '⚠️ 这条更新可能收不到', confirmText: '仍然发布', danger: true },
          );
          if (!ok) { toast('已取消发布，请修改版本号后再保存', 'warn'); return false; }
        }
        // 说明性提示（例如「本次只推给 1.0.0、1.0.1」）用 toast 告知即可，不该拦发布
        toast(notes.length ? '已保存：' + notes.join('；') : '已保存', 'ok');
        if (after) after();
        return true;
      },
    });
  }

  /* ------------------------------ 公告管理 ------------------------------ */

  async function renderNotice(host) {
    if (!hasPerm('config.read')) { requirePermOrWarn('config.read'); return; }
    setPageHeader('公告管理', '首页横幅文字（两行独立轮播）/ 弹窗 / 滚动公告');
    var res = await apiGet('/notices', null, { silent: true });
    if (res.code !== 0) { host.innerHTML = noticeBar('加载失败：' + esc(res.msg), 'danger'); return; }
    var rows = (res.data && res.data.rows) || [];

    /* ---------- 用户须知与免责声明：仅超级管理员可改，改动留审计日志 ---------- */
    var termsRes = await apiGet('/terms', null, { silent: true });
    var terms = (termsRes.code === 0 && termsRes.data) || {};
    var isSuper = Number((state.admin || {}).role_id) === 1;
    var canEditTerms = !!terms.can_edit || isSuper;
    var termsLogs = terms.logs || [];
    var termsLogHtml = termsLogs.length
      ? '<div class="table-wrap" style="margin-top:10px"><table class="tbl"><thead><tr>' +
        '<th>时间</th><th>操作人</th><th>动作</th><th>版本</th><th>内容长度</th><th>指纹(前后)</th><th>IP</th>' +
        '</tr></thead><tbody>' +
        termsLogs.map(function (l) {
          var d = l.detail && typeof l.detail === 'object' ? l.detail : {};
          var b = d.before || {}, a = d.after || {};
          return '<tr><td>' + esc(l.created_at || '') + '</td><td>' + esc(l.username || '') + '</td>' +
            '<td>' + esc(l.action === 'update' ? '修改条款' : l.action === 'denied' ? '越权尝试' : l.action) + '</td>' +
            '<td>' + esc((d.version_before !== undefined ? d.version_before + ' → ' + d.version_after : '')) + '</td>' +
            '<td>' + esc((b.length || '-') + ' → ' + (a.length || '-')) + '</td>' +
            '<td style="font-family:monospace;font-size:12px">' +
            esc(String(b.sha256 || '').slice(0, 8)) + ' → ' + esc(String(a.sha256 || '').slice(0, 8)) + '</td>' +
            '<td>' + esc(l.ip || '') + '</td></tr>';
        }).join('') +
        '</tbody></table></div>'
      : '<div class="inline-hint">暂无修改记录。任何一次修改（含非超管的越权尝试）都会记在这里。</div>';

    var termsCard =
      '<div class="card"><div class="card-head"><h3>用户须知与免责声明</h3>' +
      '<div class="card-actions">' +
      (canEditTerms
        ? '<button type="button" class="btn btn-primary btn-sm" data-act="save-terms">保存条款</button>'
        : '<span class="inline-hint">只读</span>') +
      '</div></div>' +
      noticeBar(
        '首次启动 App 时向用户展示的法律条款，**必须滑到底并等待 5 秒**才能点「同意」，点「拒绝」直接退出。' +
          '<br><b>修改权限</b>：只有超级管理员能改（当前账号：' +
          esc((state.admin && state.admin.role_name) || '未知') +
          (canEditTerms ? '，可修改' : '，<b>只读</b>') +
          '）。<br><b>修改后果</b>：保存会自动把版本号加一，<b>所有已同意的用户下次启动都会被要求重新确认</b>；' +
          '每次修改都会写入审计日志（操作人、时间、IP、新旧内容指纹），可在下方或「审计与日志」里查。',
        canEditTerms ? 'warn' : 'primary',
      ) +
      '<div class="form-grid">' +
      formField('条款正文（约 ' + String(terms.text || '').length + ' 字）',
        textareaHtml('terms_text', terms.text || '', { rows: 18 }),
        { span: true, note: canEditTerms ? '建议保留「一、二、三、四」的分条与免责、管辖、可分割条款' : '当前账号无权修改' }) +
      '</div>' +
      '<div class="inline-hint">当前版本：v' + esc(String(terms.version || 1)) +
      (terms.updated_at ? '　最后修改：' + esc(terms.updated_at) + '　修改人：' + esc(terms.updated_by || '-') : '　（尚未修改过，为内置默认条款）') +
      '</div>' +
      '<h4 style="margin:14px 0 0">修改记录</h4>' + termsLogHtml +
      '</div>';

    /* ---------- 首页横幅的两行文字：各自一个文字池 + 各自的轮播间隔 ---------- */
    var cfgRes = await apiGet('/configs?group=banner', null, { silent: true });
    var cfgMap = {};
    ((cfgRes.code === 0 && cfgRes.data && cfgRes.data.rows) || []).forEach(function (c) { cfgMap[c.key] = c.value; });
    var linesToText = function (raw) {
      // 后端对 type=json 的配置已经把 value 解析成对象返回了，所以要同时兼容「数组」和「JSON 字符串」
      if (Array.isArray(raw)) return raw.join('\n');
      try {
        var arr = JSON.parse(raw || '[]');
        return Array.isArray(arr) ? arr.join('\n') : String(raw || '');
      } catch (e) { return String(raw || ''); }
    };
    var titlePool = linesToText(cfgMap['banner_title_list']);
    var hintPool = linesToText(cfgMap['banner_hint_list']);
    var greetRules = linesToText(cfgMap['banner_greetings']);
    var titleSec = Number(cfgMap['banner_title_rotate_sec'] || 8);
    var hintSec = Number(cfgMap['banner_hint_rotate_sec'] || 5);

    var bannerCard =
      '<div class="card"><div class="card-head"><h3>首页横幅文字（两行独立轮播）</h3>' +
      '<div class="card-actions">' +
      (hasPerm('config.write') ? '<button type="button" class="btn btn-primary btn-sm" data-act="save-banner">保存横幅文字</button>' : '') +
      '</div></div>' +
      noticeBar('上面一行在**人物旁边**（无底框），下面一行是**人物下面的白色长条**。两处各自维护一个文字池：' +
        '<b>一行一条</b>，按各自的间隔循环显示，互不影响；' +
        '「公告列表」里类型=3 的公告会临时覆盖对应那行（可配时间段）。' +
        '上排支持 <code class="inline">{greeting}</code> 占位（按时间自动问候）。', 'primary') +
      '<div class="form-grid">' +
      formField('上排文字池（每行一条）', textareaHtml('banner_title_list', titlePool, { rows: 5 }), { span: true }) +
      formField('上排轮播间隔（秒）', inputHtml('banner_title_rotate_sec', titleSec, { type: 'number' }), { note: '多条时每隔多少秒换一条，最小 3' }) +
      formField('下排轮播间隔（秒）', inputHtml('banner_hint_rotate_sec', hintSec, { type: 'number' }), { note: '与上排独立，可以不一样' }) +
      formField('下排文字池（每行一条）', textareaHtml('banner_hint_list', hintPool, { rows: 5 }), { span: true }) +
      formField('按时段问候语（每行 <code class="inline">起点-终点|文案</code>）',
        textareaHtml('banner_greetings', greetRules, { rows: 9 }), {
          span: true,
          note: '上排文字池里写 {greeting} 时会按当前时间取这里的文案；终点不含（如 19-22 表示 19:00~21:59），可跨零点写 22-5。留空则用客户端内置表',
        }) +
      '</div></div>';

    var rowsHtml = rows.length ? rows.map(function (n) {
      return '<tr>' +
        '<td class="num">' + esc(n.id) + '</td>' +
        '<td>' + esc(n.title || '-') + '</td>' +
        '<td>' + tag(NOTICE_TYPE[Number(n.type)] || '-', Number(n.type) === 3 ? 'tag-ok' : Number(n.type) === 1 ? 'tag-info' : 'tag-gray') + '</td>' +
        '<td>' + esc(n.target_level === '*' ? '全部' : (n.target_level || '-')) + '</td>' +
        '<td>' + esc(fmtTime(n.start_at)) + '</td>' +
        '<td>' + esc(fmtTime(n.end_at)) + '</td>' +
        '<td class="num">' + esc(n.sort) + '</td>' +
        '<td>' + boolTag(Number(n.status) === 1, '生效中', '已停用') + '</td>' +
        '<td class="wrap"><span class="inline-hint">' + esc(truncate(n.content || '-', 60)) + '</span></td>' +
        '<td class="col-actions">' +
          (hasPerm('config.write')
            ? '<button type="button" class="btn-link" data-notice="edit" data-id="' + esc(n.id) + '">编辑</button>' +
              '<button type="button" class="btn-link danger" data-notice="delete" data-id="' + esc(n.id) + '">删除</button>'
            : '<span class="inline-hint">无权限</span>') +
        '</td></tr>';
    }).join('') : '<tr><td colspan="10" class="empty-state">暂无公告</td></tr>';

    host.innerHTML = noticeBar(
      '<b>首页横幅（类型 3）</b>是**临时活动**：填了它就会覆盖「上面的横幅文字设置」里的对应那行，并按时间段生效。' +
      '<br><b>时间段</b>：填了开始/结束时间就只在区间内显示（服务端按服务器时间判断，客户端改时间没用）；留空表示不限。' +
      '<b>排序</b>越小越靠前。<b>目标等级</b>填 <code class="inline">*</code> 或 <code class="inline">1,2,3</code>。', 'primary') +
      bannerCard +
      termsCard +
      '<div class="card"><div class="card-head"><h3>公告列表（临时活动）</h3><div class="card-actions">' +
      (hasPerm('config.write') ? '<button type="button" class="btn btn-primary btn-sm" data-act="new">新建公告</button>' : '') +
      '</div></div><div class="table-wrap"><table class="tbl"><thead><tr>' +
      '<th>ID</th><th>标题</th><th>类型</th><th>目标等级</th><th>开始时间</th><th>结束时间</th><th>排序</th><th>状态</th><th>内容</th><th>操作</th>' +
      '</tr></thead><tbody>' + rowsHtml + '</tbody></table></div></div>';

    // 保存免责声明：后端会校验「仅超级管理员」并写审计日志
    var saveTerms = $('[data-act="save-terms"]', host);
    if (saveTerms) {
      saveTerms.addEventListener('click', async function () {
        var box = $('[data-field="terms_text"]', host);
        var text = (box && box.value) || '';
        if (text.trim().length < 50) { toast('条款太短了，至少 50 字', 'warn'); return; }
        if (!window.confirm('确认保存？保存后版本号会自动加一，所有已同意的用户下次启动都需要重新确认条款。')) return;
        var res3 = await apiPost('/terms', { text: text });
        if (res3.code === 0) {
          toast(res3.data && res3.data.unchanged ? '内容没有变化，版本号未变' : '已保存为 v' + (res3.data && res3.data.version), 'ok');
          renderNotice(host);
        }
      });
    }

    // 保存两行文字池：每行一条 → JSON 数组
    var saveBanner = $('[data-act="save-banner"]', host);
    if (saveBanner) {
      saveBanner.addEventListener('click', async function () {
        var toArr = function (v) {
          return JSON.stringify(String(v || '').split('\n').map(function (s) { return s.trim(); }).filter(Boolean).slice(0, 20));
        };
        var t = $('[data-field="banner_title_list"]', host);
        var h = $('[data-field="banner_hint_list"]', host);
        var g = $('[data-field="banner_greetings"]', host);
        var ts = $('[data-field="banner_title_rotate_sec"]', host);
        var hs = $('[data-field="banner_hint_rotate_sec"]', host);
        var res2 = await apiPost('/configs', {
          entries: [
            { key: 'banner_title_list', value: toArr(t && t.value), type: 'json' },
            { key: 'banner_hint_list', value: toArr(h && h.value), type: 'json' },
            { key: 'banner_greetings', value: toArr(g && g.value), type: 'json' },
            { key: 'banner_title_rotate_sec', value: String(Math.max(3, Number(ts && ts.value) || 8)), type: 'int' },
            { key: 'banner_hint_rotate_sec', value: String(Math.max(3, Number(hs && hs.value) || 5)), type: 'int' },
          ],
        });
        if (res2.code === 0) { toast('已保存，客户端下次拉取配置即生效', 'ok'); renderNotice(host); }
      });
    }

    var add = $('[data-act="new"]', host);
    if (add) add.addEventListener('click', function () { noticeForm(null, function () { renderNotice(host); }); });
    $$('[data-notice]', host).forEach(function (btn) {
      btn.addEventListener('click', async function () {
        var id = Number(btn.getAttribute('data-id'));
        var row = rows.filter(function (r) { return Number(r.id) === id; })[0];
        if (btn.getAttribute('data-notice') === 'edit') { noticeForm(row, function () { renderNotice(host); }); return; }
        var cf = await confirmDialog('确认删除公告「' + esc(row.title) + '」？', { title: '删除公告', confirmText: '删除' });
        if (!cf) return;
        var del = await apiDelete('/notices/' + id, {});
        if (del.code === 0) { toast('已删除', 'ok'); renderNotice(host); }
      });
    });
  }

  function noticeForm(row, after) {
    var isEdit = !!row;
    var html = '<div class="form-grid">' +
      formField('类型', selectHtml('type', [
        { value: 1, label: '1 · 弹窗（进入 App 弹一次）' },
        { value: 2, label: '2 · 滚动（顶部滚动条）' },
        { value: 3, label: '3 · 首页横幅（大图上的文字框，可轮播）' },
      ], isEdit ? row.type : 3), { note: '首页横幅就是首页大图上那个文字框' }) +
      formField('标题（首页横幅=第一行）', inputHtml('title', isEdit ? row.title : ''), { required: true }) +
      formField('目标等级', inputHtml('target_level', isEdit ? row.target_level : '*'), { note: '逗号分隔等级，或填 * 表示全部人可见' }) +
      formField('排序（越小越靠前）', inputHtml('sort', isEdit ? row.sort : 100, { type: 'number' })) +
      formField('开始时间', inputHtml('start_at', isEdit ? row.start_at : '', { type: 'datetime-local' }), { note: '留空=立即生效，按服务器时间判断' }) +
      formField('结束时间', inputHtml('end_at', isEdit ? row.end_at : '', { type: 'datetime-local' }), { note: '留空=长期有效' }) +
      formField('状态', selectHtml('status', [{ value: 1, label: '启用' }, { value: 0, label: '停用' }], isEdit ? row.status : 1)) +
      formField('内容（首页横幅=第二行小字）', textareaHtml('content', isEdit ? row.content : '', { rows: 3 }), { span: true }) +
      '</div>' +
      // 首页横幅预览：按**底图实测比例**缩放的复刻版，和客户端一像素一像素对得上
      //   客户端紫卡：x 53..1025、y 301..702（972×401）
      //   主文案：左边距 43、基线在卡顶下 55、字号 42 —— 无底框
      //   长条  ：左边距 43、宽 524、顶部在卡顶下 313、高 64、字号 26 —— 圆角两端
      // 预览按 620px 宽渲染（缩放系数 620/972 ≈ 0.638），所以下面所有尺寸都是实测量 ×0.638
      '<div class="card" style="margin-top:12px"><div class="card-head"><h3>首页横幅效果预览</h3></div>' +
      '<div id="banner-preview" style="margin:12px auto;width:620px;max-width:100%;height:256px;position:relative;overflow:hidden;' +
      'border-radius:14px;background:linear-gradient(120deg,#6f6ae8,#7f74ee 55%,#9a86f3);color:#fff;">' +
      // 人物占位用斜边：上窄下宽，和底图实测一致（上方人物左边界≈卡片 51%，长条那一段退到≈60%）
      '<div style="position:absolute;right:0;top:0;bottom:0;left:51%;' +
      'clip-path:polygon(0 0,100% 0,100% 100%,22% 100%);' +
      'background:radial-gradient(circle at 58% 36%,rgba(255,255,255,.55),rgba(255,255,255,.14) 58%,rgba(255,255,255,.06) 75%);"></div>' +
      '<div id="bp-title" style="position:absolute;left:43px;top:35px;max-width:281px;font-size:27px;font-weight:700;line-height:1.35;' +
      'text-shadow:0 2px 6px rgba(0,0,0,.45);word-break:break-all;"></div>' +
      '<div id="bp-hint" style="position:absolute;left:27px;top:168px;width:567px;height:64px;line-height:64px;padding:0 19px;box-sizing:border-box;' +
      'border-radius:32px;background:#fff;border:1px solid #8f86e8;color:#4a44b5;font-size:27px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;"></div>' +
      '</div><div class="inline-hint">按底图实测比例缩放（卡片 972×401 → 620×256）：' +
      '主文案可用宽 281px、字号 42px→27px、位置在人物左侧偏下；' +
      '下方长条 567px 宽（手机上 889px，铺满紫卡内侧）、不透明白底 + 淡紫细边框、深紫字；' +
      '长条右端会压到人物裙摆一点点（这是允许的）。</div></div>';
    openModal({
      title: isEdit ? '编辑公告' : '新建公告',
      size: 'modal-lg',
      bodyHtml: html,
      confirmText: isEdit ? '保存' : '发布',
      onMount: function (mask) {
        // 表单控件用的是 data-field 属性（见 inputHtml/textareaHtml），不是 name
        var t = $('[data-field="title"]', mask), h = $('[data-field="content"]', mask);
        var sync = function () {
          var bt = document.getElementById('bp-title'), bh = document.getElementById('bp-hint');
          if (bt) bt.textContent = (t && t.value) || '（第一行文案）';
          if (bh) {
            var v = (h && h.value) || '';
            bh.textContent = v || '（第二行小字，可留空）';
            bh.style.opacity = v ? '1' : '.5';
          }
        };
        if (t) t.addEventListener('input', sync);
        if (h) h.addEventListener('input', sync);
        sync();
        if (t) t.focus();
      },
      onConfirm: async function (mask) {
        var f = readFields(mask);
        if (!f.title) { toast('标题必填', 'warn'); return false; }
        var payload = {
          title: f.title, content: f.content || '', type: Number(f.type || 1),
          target_level: f.target_level || '*',
          start_at: f.start_at || null, end_at: f.end_at || null,
          status: Number(f.status || 0), sort: Number(f.sort || 100),
        };
        var res = isEdit ? await apiPut('/notices/' + row.id, payload) : await apiPost('/notices', payload);
        if (res.code !== 0) return false;
        toast('已保存', 'ok');
        if (after) after();
        return true;
      },
    });
  }

  /* ------------------------------ 全局配置 ------------------------------ */

  var configVersion = null;

  async function renderConfig(host) {
    if (!hasPerm('config.read')) { requirePermOrWarn('config.read'); return; }
    setPageHeader('全局配置', '按分组维护系统参数，保存后自动递增 config_version');
    var res = await apiGet('/configs');
    if (res.code !== 0) { host.innerHTML = noticeBar('加载失败：' + esc(res.msg), 'danger'); return; }
    var d = res.data || {};
    var rows = d.rows || [];
    configVersion = d.config_version;

    var groups = {};
    rows.forEach(function (r) {
      var g = r.group || 'base';
      if (!groups[g]) groups[g] = [];
      groups[g].push(r);
    });

    var html = '<div class="stat-grid">' +
      statCard('config_version', val(configVersion), '每次保存后 +1，客户端据此刷新缓存', 'primary') +
      statCard('配置项总数', fmtInt(rows.length), '分组 ' + Object.keys(groups).length + ' 个') +
      '</div>';

    var names = Object.keys(groups).sort();
    if (!names.length) { html += emptyState('暂无可配置项'); host.innerHTML = html; return; }

    html += '<div class="card"><div class="card-head"><h3>配置项</h3>' +
      '<div class="card-actions">' +
      (hasPerm('config.write') ? '<button type="button" class="btn btn-primary btn-sm" data-act="save-all">保存全部修改</button>' : '<span class="inline-hint">只读权限</span>') +
      '</div></div>';

    names.forEach(function (name, gi) {
      var list = groups[name];
      html += '<div style="border-top:' + (gi === 0 ? 'none' : '1px solid var(--c-border)') + '">';
      html += '<div style="padding:11px 16px;background:var(--c-panel-soft);font-weight:600;font-size:13px">' +
        esc(name) + ' <span class="inline-hint">共 ' + list.length + ' 项</span></div>';
      if (name.indexOf('watermark') === 0) {
        html += '<div style="padding:10px 16px 0">' +
          noticeBar('<strong>水印策略由服务端强制生效，客户端无法传参覆盖。</strong>' +
            '这些参数只在服务端解析/合成阶段读取，客户端即使伪造请求参数也不会改变水印行为。修改后请在测试环境验证再上线。', 'warn') +
          '</div>';
      }
      html += '<div class="card-body"><div class="form-grid">';
      list.forEach(function (r) {
        var ro = !hasPerm('config.write');
        var lock = ro ? ' disabled' : '';
        var control;
        var value = r.value;
        if (r.type === 'bool') {
          var on = value === true || value === 1 || value === '1' || value === 'true';
          control = '<label class="switch"><input type="checkbox" data-cfg="' + esc(r.key) + '" data-cfg-type="bool"' +
            (on ? ' checked' : '') + lock + '><span class="switch-track"></span><span>' + (on ? '开启' : '关闭') + '</span></label>';
        } else if (r.type === 'int') {
          control = '<input class="input" type="number" data-cfg="' + esc(r.key) + '" data-cfg-type="int" value="' +
            esc(val(value)) + '"' + lock + '>';
        } else if (r.type === 'json') {
          var jsonText;
          try { jsonText = typeof value === 'string' ? value : JSON.stringify(value, null, 2); } catch (e) { jsonText = String(value); }
          control = '<textarea class="textarea mono" rows="6" data-cfg="' + esc(r.key) + '" data-cfg-type="json"' + lock + '>' + esc(jsonText) + '</textarea>';
        } else {
          control = '<input class="input" type="text" data-cfg="' + esc(r.key) + '" data-cfg-type="string" value="' +
            esc(val(value)) + '"' + lock + '>';
        }
        html += '<label class="field' + (r.type === 'json' ? ' span-2' : '') + '">' +
          '<span class="field-label">' + esc(r.title || r.key) +
          ' <span class="inline-hint">' + esc(r.key) + ' · ' + esc(r.type) +
          (Number(r.is_public) === 1 ? ' · 下发客户端' : ' · 仅服务端') + '</span></span>' +
          control + (r.remark ? '<span class="form-note">' + esc(r.remark) + '</span>' : '') + '</label>';
      });
      html += '</div></div></div>';
    });
    html += '</div>';

    host.innerHTML = html;

    var saveBtn = $('[data-act="save-all"]', host);
    if (saveBtn) {
      saveBtn.addEventListener('click', async function () {
        var entries = [];
        var invalid = null;
        $$('[data-cfg]', host).forEach(function (el) {
          var key = el.getAttribute('data-cfg');
          var type = el.getAttribute('data-cfg-type');
          var value;
          if (type === 'bool') value = el.checked ? 1 : 0;
          else if (type === 'int') value = el.value === '' ? 0 : Number(el.value);
          else if (type === 'json') {
            try { value = el.value.trim() ? JSON.parse(el.value) : null; }
            catch (e) { invalid = key + '：' + e.message; return; }
          } else value = el.value;
          entries.push({ key: key, value: value, type: type });
        });
        if (invalid) { toast('JSON 格式错误 → ' + invalid, 'error'); return; }
        if (!entries.length) { toast('没有需要保存的配置', 'warn'); return; }
        var cf = await confirmDialog('确认保存 <b>' + entries.length + '</b> 项配置？保存后 config_version 会 +1，客户端下次拉取即生效。', {
          title: '保存全局配置', confirmText: '确认保存', danger: false,
        });
        if (!cf) return;
        var res2 = await apiPost('/configs', { entries: entries });
        if (res2.code === 0) {
          toast(res2.msg || '已保存', 'ok');
          configVersion = res2.data && res2.data.config_version ? res2.data.config_version : configVersion;
          renderConfig(host);
        }
      });
    }
  }

  /* ------------------------------ 面板管理 ------------------------------ */

  var panelTab = 'admins';

  async function renderPanel(host) {
    if (!hasPerm('admin.read')) { requirePermOrWarn('admin.read'); return; }
    setPageHeader('面板管理', '管理员账号、角色权限、个人安全与按版本吊销');
    var tabs = [
      ['admins', '管理员账号', 'admin.read'],
      ['roles', '角色权限', 'admin.read'],
      ['me', '个人安全', null],
      ['purge', '按版本吊销', 'config.write'],
    ];
    var html = '<div class="card"><div class="card-head"><h3>面板管理</h3><div class="chart-tabs">' +
      tabs.map(function (t) {
        if (t[2] && !hasPerm(t[2])) return '';
        return '<button type="button" class="chart-tab' + (panelTab === t[0] ? ' active' : '') + '" data-tab="' + t[0] + '">' + t[1] + '</button>';
      }).join('') + '</div></div><div class="card-body" id="panel-body"><div class="loading-block">加载中…</div></div></div>';
    host.innerHTML = html;

    $$('[data-tab]', host).forEach(function (btn) {
      btn.addEventListener('click', function () {
        panelTab = btn.getAttribute('data-tab');
        $$('[data-tab]', host).forEach(function (b) { b.classList.toggle('active', b === btn); });
        loadPanelTab();
      });
    });

    async function loadPanelTab() {
      var box = document.getElementById('panel-body');
      box.innerHTML = '<div class="loading-block">加载中…</div>';
      if (panelTab === 'admins') return renderAdminTab(box);
      if (panelTab === 'roles') return renderRoleTab(box);
      if (panelTab === 'me') return renderMeTab(box);
      return renderPurgeTab(box);
    }

    await loadPanelTab();
  }

  async function renderAdminTab(box) {
    var res = await apiGet('/admins', { page: 1, limit: 100 });
    if (res.code !== 0) { box.innerHTML = '<div class="empty-state">加载失败：' + esc(res.msg) + '</div>'; return; }
    var rows = (res.data && res.data.rows) || [];
    var rolesRes = await apiGet('/roles', null, { silent: true });
    var roles = (rolesRes.code === 0 && rolesRes.data && rolesRes.data.rows) || [];
    var roleMap = {};
    roles.forEach(function (r) { roleMap[r.id] = r.name; });

    box.innerHTML =
      '<div class="card-actions" style="margin-bottom:12px">' +
        (hasPerm('admin.write') ? '<button type="button" class="btn btn-primary btn-sm" data-adm="new">新增管理员</button>' : '') +
      '</div>' +
      '<div class="table-wrap"><table class="tbl"><thead><tr>' +
      '<th>ID</th><th>用户名</th><th>姓名</th><th>手机号</th><th>角色</th><th>状态</th><th>TOTP</th>' +
      '<th>最近登录</th><th>登录 IP</th><th>创建时间</th><th>操作</th>' +
      '</tr></thead><tbody>' + (rows.length ? rows.map(function (a) {
        return '<tr>' +
          '<td class="num">' + esc(a.id) + '</td>' +
          '<td>' + esc(a.username) + '</td>' +
          '<td>' + esc(a.real_name || '-') + '</td>' +
          '<td>' + esc(a.phone || '-') + '</td>' +
          '<td>' + esc(a.role_name || roleMap[a.role_id] || ('#' + a.role_id)) + '</td>' +
          '<td>' + boolTag(Number(a.status) === 1, '启用', '已禁用') + '</td>' +
          '<td>' + boolTag(a.totp_secret, '已开启', '未开启') + '</td>' +
          '<td>' + esc(fmtTime(a.last_login_at)) + '</td>' +
          '<td>' + esc(a.last_login_ip || '-') + '</td>' +
          '<td>' + esc(fmtTime(a.created_at)) + '</td>' +
          '<td class="col-actions">' +
            (hasPerm('admin.write')
              ? '<button type="button" class="btn-link" data-adm="edit" data-id="' + esc(a.id) + '">编辑</button>' +
                '<button type="button" class="btn-link" data-adm="resetpw" data-id="' + esc(a.id) + '">重置密码</button>' +
                '<button type="button" class="btn-link danger" data-adm="delete" data-id="' + esc(a.id) + '">删除</button>'
              : '<span class="inline-hint">无权限</span>') +
          '</td></tr>';
      }).join('') : '<tr><td colspan="11" class="empty-state">暂无管理员</td></tr>') +
      '</tbody></table></div>';

    $$('[data-adm]', box).forEach(function (btn) {
      btn.addEventListener('click', async function () {
        var action = btn.getAttribute('data-adm');
        var id = Number(btn.getAttribute('data-id'));
        var row = rows.filter(function (r) { return Number(r.id) === id; })[0];
        if (action === 'new') { adminForm(null, roles, function () { renderPanel(viewEl()); }); return; }
        if (action === 'edit') { adminForm(row, roles, function () { renderPanel(viewEl()); }); return; }
        if (action === 'resetpw') {
          var pw = await promptDialog({
            title: '重置管理员密码 · ' + (row ? row.username : id),
            intro: '留空则由服务端随机生成（返回结果里会显示一次）。重置后该管理员所有会话会被吊销。',
            fields: [{ key: 'password', label: '新密码（至少 8 位，可留空）' }],
            confirmText: '重置',
          });
          if (!pw) return;
          var res2 = await apiPost('/admins/' + id + '/reset-password', { password: pw.password || '' });
          if (res2.code === 0) {
            var newPw = res2.data && res2.data.password;
            if (newPw) {
              openModal({
                title: '新密码（仅显示一次）', size: 'modal-sm', hideConfirm: true, cancelText: '关闭',
                bodyHtml: noticeBar('请立即抄录并通过安全渠道转交，关闭后无法再次查看。', 'warn') +
                  '<pre class="code" style="font-size:14px;text-align:center">' + esc(newPw) + '</pre>',
              });
            }
            toast('密码已重置', 'ok');
            renderPanel(viewEl());
          }
          return;
        }
        if (action === 'delete') {
          var cf = await confirmDialog('确认<b>删除</b>管理员 <b>' + esc(row ? row.username : id) + '</b>？该账号所有会话会立即失效。', {
            title: '删除管理员（二次确认）', needPassword: true, confirmText: '永久删除',
          });
          if (!cf || !cf.ok) return;
          var del = await apiDelete('/admins/' + id, { password: cf.password });
          if (del.code === 0) { toast('已删除', 'ok'); renderPanel(viewEl()); }
        }
      });
    });
  }

  function adminForm(row, roles, after) {
    var isEdit = !!row;
    var roleOpts = roles.map(function (r) { return { value: r.id, label: r.name + '（' + r.code + '）' }; });
    var html = '<div class="form-grid">' +
      formField('用户名', isEdit ? '<input class="input" value="' + esc(row.username) + '" disabled>' : inputHtml('username', '', { placeholder: '3~32 位字母数字 _ . @ -' }), { required: !isEdit, note: '创建后不可修改' }) +
      (isEdit ? '' : formField('初始密码', inputHtml('password', '', { type: 'password' }), { required: true, note: '至少 8 位' })) +
      formField('姓名', inputHtml('real_name', isEdit ? row.real_name : '')) +
      formField('手机号', inputHtml('phone', isEdit ? row.phone : '')) +
      formField('角色', selectHtml('role_id', roleOpts, isEdit ? row.role_id : (roleOpts[0] ? roleOpts[0].value : 3))) +
      formField('状态', selectHtml('status', [{ value: 1, label: '启用' }, { value: 0, label: '禁用' }], isEdit ? row.status : 1)) +
      '</div>';
    openModal({
      title: isEdit ? '编辑管理员 · ' + row.username : '新增管理员',
      size: 'modal-md',
      bodyHtml: html,
      confirmText: '保存',
      onConfirm: async function (mask) {
        var f = readFields(mask);
        if (isEdit) {
          var res2 = await apiPut('/admins/' + row.id, {
            real_name: f.real_name || '', phone: f.phone || '',
            role_id: Number(f.role_id || 3), status: Number(f.status),
          });
          if (res2.code !== 0) return false;
        } else {
          if (!f.username || !f.password) { toast('用户名与初始密码必填', 'warn'); return false; }
          var res = await apiPost('/admins', {
            username: f.username, password: f.password,
            real_name: f.real_name || '', phone: f.phone || '',
            role_id: Number(f.role_id || 3), status: Number(f.status),
          });
          if (res.code !== 0) return false;
        }
        toast('已保存', 'ok');
        if (after) after();
        return true;
      },
    });
  }

  async function renderRoleTab(box) {
    var res = await apiGet('/roles');
    if (res.code !== 0) { box.innerHTML = '<div class="empty-state">加载失败：' + esc(res.msg) + '</div>'; return; }
    var rows = (res.data && res.data.rows) || [];

    box.innerHTML = '<div class="inline-hint" style="margin-bottom:10px">角色决定可见菜单（menus）与接口权限（perms）。' +
      '<code class="inline">*</code> 表示全部；<code class="inline">user.*</code> 表示 user 模块全部权限。</div>' +
      '<div class="table-wrap"><table class="tbl"><thead><tr>' +
      '<th>ID</th><th>名称</th><th>标识</th><th>菜单</th><th>权限</th><th>数据范围</th><th>备注</th><th>操作</th>' +
      '</tr></thead><tbody>' + rows.map(function (r) {
        return '<tr>' +
          '<td class="num">' + esc(r.id) + '</td>' +
          '<td>' + esc(r.name) + '</td>' +
          '<td><code class="inline">' + esc(r.code || '-') + '</code></td>' +
          '<td class="wrap">' + (r.menus || []).map(function (m) { return '<span class="pill">' + esc(m) + '</span>'; }).join(' ') + '</td>' +
          '<td class="wrap">' + (r.perms || []).map(function (p) { return '<span class="pill">' + esc(p) + '</span>'; }).join(' ') + '</td>' +
          '<td>' + esc(r.data_scope === 2 ? '仅名下用户' : '全部数据') + '</td>' +
          '<td class="wrap"><span class="inline-hint">' + esc(truncate(r.remark || '-', 40)) + '</span></td>' +
          '<td class="col-actions">' + (hasPerm('admin.write')
            ? '<button type="button" class="btn-link" data-role="edit" data-id="' + esc(r.id) + '">编辑权限</button>'
            : '<span class="inline-hint">无权限</span>') + '</td></tr>';
      }).join('') + '</tbody></table></div>';

    $$('[data-role]', box).forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = Number(btn.getAttribute('data-id'));
        var row = rows.filter(function (r) { return r.id === id; })[0];
        roleForm(row, function () { renderPanel(viewEl()); });
      });
    });
  }

  function roleForm(role, after) {
    var menus = ['dashboard', 'user', 'invite', 'order', 'revenue', 'task', 'ticket', 'feedback', 'version', 'notice', 'config', 'panel', 'audit'];
    var MENU_TEXT = {
      dashboard: '数据概览', user: '账号管理', invite: '邀请码管理', order: '订单管理', revenue: '收益管理',
      task: '任务与线路', ticket: '客服工单', feedback: '意见反馈', version: '版本推送', notice: '公告管理',
      config: '全局配置', panel: '面板管理', audit: '审计与日志',
    };
    var currentMenus = role.menus || [];
    var currentPerms = role.perms || [];
    var allPerm = currentPerms.indexOf('*') >= 0;

    var menuHtml = menus.map(function (m) {
      return '<label class="checkbox"><input type="checkbox" data-menu="' + esc(m) + '"' +
        (currentMenus.indexOf(m) >= 0 ? ' checked' : '') + '> ' + esc(MENU_TEXT[m]) + ' <span class="mono-hint">' + esc(m) + '</span></label>';
    }).join('');

    var permHtml = '<label class="checkbox" style="margin-bottom:8px"><input type="checkbox" data-perm-all="1"' +
      (allPerm ? ' checked' : '') + '> <b>全部权限（*）</b></label>';
    permHtml += PERM_PREFIXES.map(function (prefix) {
      var items = PERM_ACTIONS.map(function (action) {
        var perm = prefix + '.' + action;
        return '<label class="checkbox"><input type="checkbox" data-perm="' + esc(perm) + '"' +
          (currentPerms.indexOf(perm) >= 0 ? ' checked' : '') + '> ' + esc(perm) + '</label>';
      }).join('') + '<label class="checkbox"><input type="checkbox" data-perm="' + esc(prefix) + '.*"' +
        (currentPerms.indexOf(prefix + '.*') >= 0 ? ' checked' : '') + '> ' + esc(prefix) + '.*（模块全部）</label>';
      return '<div class="perm-group"><div class="perm-group-head"><span>' + esc(prefix) +
        '</span><button type="button" class="btn-link" data-perm-toggle="' + esc(prefix) + '">全选/反选</button></div>' +
        '<div class="perm-list">' + items + '</div></div>';
    }).join('');

    openModal({
      title: '编辑角色权限 · ' + role.name,
      size: 'modal-lg',
      bodyHtml: '<div class="form-grid">' +
        formField('角色名称', inputHtml('name', role.name)) +
        formField('数据范围', selectHtml('data_scope', [
          { value: 1, label: '全部数据' }, { value: 2, label: '仅名下用户（客服）' },
        ], role.data_scope)) +
        formField('备注', inputHtml('remark', role.remark), { span: true }) +
        '</div>' +
        '<div class="section-title">可见菜单（menus）</div>' +
        '<div class="perm-group"><div class="perm-list">' + menuHtml + '</div></div>' +
        '<div class="section-title">接口权限（perms）</div>' + permHtml,
      confirmText: '保存角色',
      onMount: function (mask) {
        var allBox = $('[data-perm-all]', mask);
        var permBoxes = $$('[data-perm]', mask);
        if (allBox) {
          var sync = function () {
            permBoxes.forEach(function (b) { b.disabled = allBox.checked; });
          };
          allBox.addEventListener('change', sync);
          sync();
        }
        $$('[data-perm-toggle]', mask).forEach(function (btn) {
          btn.addEventListener('click', function () {
            var prefix = btn.getAttribute('data-perm-toggle');
            var boxes = permBoxes.filter(function (b) { return b.getAttribute('data-perm').indexOf(prefix + '.') === 0; });
            var allChecked = boxes.every(function (b) { return b.checked; });
            boxes.forEach(function (b) { b.checked = !allChecked; });
          });
        });
      },
      onConfirm: async function (mask) {
        var f = readFields(mask);
        var selectedMenus = $$('[data-menu]', mask).filter(function (b) { return b.checked; }).map(function (b) { return b.getAttribute('data-menu'); });
        var selectedPerms = $$('[data-perm]', mask).filter(function (b) { return b.checked; }).map(function (b) { return b.getAttribute('data-perm'); });
        if (f['perm-all']) selectedPerms = ['*'];
        var res = await apiPut('/roles/' + role.id, {
          name: f.name, data_scope: Number(f.data_scope || 1), remark: f.remark || '',
          menus: selectedMenus, perms: selectedPerms,
        });
        if (res.code !== 0) return false;
        toast('角色已保存', 'ok');
        if (after) after();
        return true;
      },
    });
  }

  function renderMeTab(box) {
    var admin = state.admin || {};
    box.innerHTML = '<div class="card"><div class="card-head"><h3>当前登录账号</h3></div><div class="card-body"><div class="dl dl-2">' +
      dlItem('用户名', admin.username) + dlItem('姓名', admin.real_name) + dlItem('角色', admin.role_name) +
      dlItem('手机号', admin.phone) + dlItem('TOTP', admin.totp_enabled ? '已开启' : '未开启') +
      dlItem('最近登录', fmtTime(admin.last_login_at)) + dlItem('登录 IP', admin.last_login_ip) +
      dlItem('数据范围', Number(admin.data_scope) === 2 ? '仅名下用户' : '全部数据') +
      '</div><div class="form-note" style="margin-top:8px">菜单：' + esc((admin.menus || []).join(', ') || '-') +
      '<br>权限：' + esc((admin.perms || []).join(', ') || '-') + '</div></div></div>' +

      '<div class="card"><div class="card-head"><h3>修改登录密码</h3></div><div class="card-body">' +
      '<div class="form-grid">' +
      formField('原密码', '<input class="input" type="password" id="pw-old" autocomplete="current-password">', { required: true }) +
      formField('新密码', '<input class="input" type="password" id="pw-new" autocomplete="new-password">', { required: true, note: '至少 8 位' }) +
      formField('确认新密码', '<input class="input" type="password" id="pw-new2" autocomplete="new-password">', { required: true }) +
      '</div><div style="margin-top:12px"><button type="button" class="btn btn-primary btn-sm" id="btn-change-pw">修改密码</button>' +
      '<span class="form-note" style="display:inline-block;margin-left:10px">修改后所有后台会话失效，需要重新登录。</span></div></div></div>' +

      '<div class="card"><div class="card-head"><h3>TOTP 二次验证</h3></div><div class="card-body">' +
      (admin.totp_enabled
        ? noticeBar('当前账号已开启 TOTP 二次验证。如需关闭，请点击下方按钮并输入登录密码。', 'ok') +
          '<button type="button" class="btn btn-sm btn-danger-text" id="btn-totp-off">关闭 TOTP</button>'
        : noticeBar('开启后登录需要额外输入 6 位动态码。点击「生成密钥」后，用验证器手动添加密钥，或复制 otpauth 链接到验证器。', 'info') +
          '<button type="button" class="btn btn-primary btn-sm" id="btn-totp-setup">生成密钥</button>' +
          '<div id="totp-box" style="margin-top:12px"></div>') +
      '</div></div>';

    var pwBtn = document.getElementById('btn-change-pw');
    if (pwBtn) {
      pwBtn.addEventListener('click', async function () {
        var oldPw = document.getElementById('pw-old').value;
        var newPw = document.getElementById('pw-new').value;
        var newPw2 = document.getElementById('pw-new2').value;
        if (!oldPw || !newPw) { toast('请填写原密码与新密码', 'warn'); return; }
        if (newPw.length < 8) { toast('新密码至少 8 位', 'warn'); return; }
        if (newPw !== newPw2) { toast('两次输入的新密码不一致', 'warn'); return; }
        var res = await apiPost('/password', { old_password: oldPw, new_password: newPw });
        if (res.code === 0) {
          toast('密码已修改，请重新登录', 'ok');
          setTimeout(function () { doLogout(true); }, 800);
        }
      });
    }

    var setupBtn = document.getElementById('btn-totp-setup');
    if (setupBtn) {
      setupBtn.addEventListener('click', async function () {
        var res = await apiPost('/totp/setup', {});
        if (res.code !== 0) return;
        var d = res.data || {};
        var boxEl = document.getElementById('totp-box');
        boxEl.innerHTML =
          noticeBar('密钥只显示一次，请立即抄录。二维码链接可直接粘贴到支持 otpauth 的验证器。', 'warn') +
          '<div class="dl"><dt>密钥 secret</dt><dd><code class="inline" style="font-size:14px">' + esc(d.secret) + '</code></dd>' +
          '<dt>otpauth 链接</dt><dd><code class="inline">' + esc(d.otpauth) + '</code></dd></div>' +
          '<div class="form-grid" style="margin-top:12px">' +
          formField('动态验证码', '<input class="input" id="totp-code" maxlength="6" inputmode="numeric" placeholder="输入验证器上的 6 位数字">', { required: true }) +
          '</div>' +
          '<div style="margin-top:10px"><button type="button" class="btn btn-primary btn-sm" id="btn-totp-enable">提交动态码并启用</button></div>' +
          '<div class="form-note" style="margin-top:6px">不支持扫码的环境下，请手动选择「基于时间（TOTP）」并输入上面的密钥。</div>';

        document.getElementById('btn-totp-enable').addEventListener('click', async function () {
          var code = document.getElementById('totp-code').value.trim();
          if (!code) { toast('请输入动态验证码', 'warn'); return; }
          var res2 = await apiPost('/totp/enable', { secret: d.secret, code: code });
          if (res2.code === 0) {
            toast('二次验证已开启', 'ok');
            if (state.admin) { state.admin.totp_enabled = true; setStoredAdmin(state.admin); }
            renderPanel(viewEl());
          }
        });
      });
    }

    var offBtn = document.getElementById('btn-totp-off');
    if (offBtn) {
      offBtn.addEventListener('click', async function () {
        var cf = await confirmDialog('确认关闭 TOTP 二次验证？关闭后登录只需要密码。', {
          title: '关闭 TOTP（二次确认）', needPassword: true, confirmText: '确认关闭',
        });
        if (!cf || !cf.ok) return;
        var res = await apiPost('/totp/disable', { password: cf.password });
        if (res.code === 0) {
          toast('已关闭二次验证', 'ok');
          if (state.admin) { state.admin.totp_enabled = false; setStoredAdmin(state.admin); }
          renderPanel(viewEl());
        }
      });
    }
  }

  function renderPurgeTab(box) {
    box.innerHTML = noticeBar(
      '<strong>按版本一键吊销：发现某个客户端版本被大规模破解时，这是最有效的止血手段。</strong><br>' +
      '执行后会立即撤销该版本下所有在线会话，并把版本号写入 <code class="inline">app_version_blocklist</code>，' +
      '之后该版本的所有请求都会被服务端直接拒绝（客户端必须升级才能继续使用）。', 'danger') +
      '<div class="form-grid" style="max-width:460px">' +
      formField('客户端版本号', '<input class="input" id="purge-version" placeholder="例如 102，填写 version_code 数字">', { required: true }) +
      '</div>' +
      '<div style="margin-top:12px"><button type="button" class="btn btn-danger btn-sm" id="btn-purge">立即吊销该版本</button></div>' +
      '<div class="form-note" style="margin-top:8px">该操作影响全量用户，请确认版本号无误后再执行。</div>' +
      '<div id="purge-result" style="margin-top:12px"></div>';

    var btn = document.getElementById('btn-purge');
    btn.addEventListener('click', async function () {
      var version = document.getElementById('purge-version').value.trim();
      if (!version) { toast('请填写版本号', 'warn'); return; }
      var cf = await confirmDialog('确认<b>吊销版本 ' + esc(version) + '</b>？该版本所有在线会话会被立即撤销，且不可恢复。', {
        title: '吊销客户端版本', confirmText: '确认吊销',
      });
      if (!cf) return;
      var res = await apiPost('/panel/purge-version', { app_version: version });
      if (res.code === 0) {
        toast(res.msg || '已吊销', 'ok');
        var d = res.data || {};
        document.getElementById('purge-result').innerHTML =
          noticeBar('已吊销版本 <b>' + esc(d.version) + '</b>，撤销会话 <b>' + esc(d.revoked) + '</b> 个。' +
            '<br>当前 blocklist：<code class="inline">' + esc((d.blocklist || []).join(', ')) + '</code>', 'ok');
      }
    });
  }

  /* ------------------------------ 审计与日志 ------------------------------ */

  var auditTab = 'audit';
  var auditState = { page: 1, limit: 20, module: '', admin_id: '', target_id: '' };
  var loginState = { page: 1, limit: 20, user_type: '2', status: '', keyword: '' };
  var riskState = { page: 1, limit: 20, type: '', handled: '' };

  async function renderAudit(host) {
    if (!hasPerm('audit.read')) { requirePermOrWarn('audit.read'); return; }
    setPageHeader('审计与日志', '操作审计、登录日志与风控事件');
    var tabs = [['audit', '操作审计', 'audit.read'], ['login', '登录日志', 'audit.read'], ['risk', '风控事件', 'risk.read']];
    host.innerHTML = '<div class="card"><div class="card-head"><h3>日志中心</h3><div class="chart-tabs">' +
      tabs.map(function (t) {
        if (!hasPerm(t[2])) return '';
        return '<button type="button" class="chart-tab' + (auditTab === t[0] ? ' active' : '') + '" data-atab="' + t[0] + '">' + t[1] + '</button>';
      }).join('') + '</div></div></div><div id="audit-body"></div>';

    $$('[data-atab]', host).forEach(function (btn) {
      btn.addEventListener('click', function () {
        auditTab = btn.getAttribute('data-atab');
        $$('[data-atab]', host).forEach(function (b) { b.classList.toggle('active', b === btn); });
        loadTab();
      });
    });

    async function loadTab() {
      var box = document.getElementById('audit-body');
      if (auditTab === 'audit') return loadAudit(box);
      if (auditTab === 'login') return loadLoginLogs(box);
      return loadRisk(box);
    }

    await loadTab();
  }

  async function loadAudit(box) {
    var fields = [
      function (s) { return textFilter('module', '模块', s.module, 'user / order / config …'); },
      function (s) { return textFilter('admin_id', '管理员 ID', s.admin_id, '数字 ID', 'amount'); },
      function (s) { return textFilter('target_id', '目标 ID', s.target_id, '目标对象 ID', 'amount'); },
    ];
    (function () {
      var keys = [['module'], ['admin_id'], ['target_id']];
      fields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
    })();

    var reload = function () { loadAudit(box); };
    var res = await apiGet('/audit', auditState, { silent: true });
    var rows = res.code === 0 ? ((res.data && res.data.rows) || []) : [];
    var total = res.code === 0 ? ((res.data && res.data.total) || 0) : 0;

    renderTable(box, {
      title: '操作审计',
      state: auditState,
      fields: fields,
      columns: [
        { key: 'id', title: 'ID', className: 'num' },
        { key: 'created_at', title: '时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        { key: 'username', title: '操作人', render: function (r) { return esc(r.username || ('#' + (r.admin_id || '-'))); } },
        { key: 'module', title: '模块', render: function (r) { return '<span class="pill">' + esc(r.module || '-') + '</span>'; } },
        { key: 'action', title: '动作', render: function (r) { return esc(r.action || '-'); } },
        { key: 'target_id', title: '目标 ID', className: 'num', render: function (r) { return esc(val(r.target_id)); } },
        { key: 'ip', title: 'IP', render: function (r) { return esc(r.ip || '-'); } },
        { key: 'detail', title: '详情', className: 'wrap', render: function (r) { return jsonBlock(r.detail); } },
      ],
      rows: rows,
      total: total,
      empty: res.code === 0 ? '暂无审计记录' : ('加载失败：' + res.msg),
      reload: reload,
    });
  }

  async function loadLoginLogs(box) {
    var fields = [
      function (s) { return textFilter('keyword', '关键词', s.keyword, '用户名 / IP', 'wide'); },
      function (s) {
        return selectFilter('user_type', '账号类型', [
          { value: '', label: '全部' }, { value: '1', label: '终端用户' }, { value: '2', label: '管理员' },
        ], s.user_type);
      },
      function (s) {
        return selectFilter('status', '结果', [
          { value: '', label: '全部' }, { value: '1', label: '成功' }, { value: '0', label: '失败' },
        ], s.status);
      },
    ];
    (function () {
      var keys = [['keyword'], ['user_type'], ['status']];
      fields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
    })();

    var reload = function () { loadLoginLogs(box); };
    var res = await apiGet('/login-logs', loginState, { silent: true });
    var rows = res.code === 0 ? ((res.data && res.data.rows) || []) : [];
    var total = res.code === 0 ? ((res.data && res.data.total) || 0) : 0;

    renderTable(box, {
      title: '登录日志',
      state: loginState,
      fields: fields,
      columns: [
        { key: 'id', title: 'ID', className: 'num' },
        { key: 'created_at', title: '时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        { key: 'user_type', title: '类型', render: function (r) { return esc(Number(r.user_type) === 2 ? '管理员' : '终端用户'); } },
        { key: 'username', title: '账号', render: function (r) { return esc(r.username || '-'); } },
        { key: 'status', title: '结果', render: function (r) { return boolTag(Number(r.status) === 1, '成功', '失败'); } },
        { key: 'ip', title: 'IP', render: function (r) { return esc(r.ip || '-'); } },
        { key: 'ua', title: 'User-Agent', className: 'wrap', render: function (r) { return esc(truncate(r.ua || '-', 80)); } },
        { key: 'msg', title: '说明', render: function (r) { return esc(r.msg || '-'); } },
      ],
      rows: rows,
      total: total,
      empty: res.code === 0 ? '暂无登录记录' : ('加载失败：' + res.msg),
      reload: reload,
    });
  }

  async function loadRisk(box) {
    if (!hasPerm('risk.read')) { box.innerHTML = noticeBar('缺少 risk.read 权限', 'warn'); return; }
    var fields = [
      function (s) { return textFilter('type', '事件类型', s.type, '例如 device_limit', 'wide'); },
      function (s) {
        return selectFilter('handled', '处理状态', [
          { value: '', label: '全部' }, { value: '0', label: '未处理' }, { value: '1', label: '已处理' },
        ], s.handled);
      },
    ];
    (function () {
      var keys = [['type'], ['handled']];
      fields.forEach(function (f, i) { f.reset = function (s) { keys[i].forEach(function (k) { s[k] = ''; }); }; });
    })();

    var reload = function () { loadRisk(box); };
    var res = await apiGet('/risk-events', riskState, { silent: true });
    var rows = res.code === 0 ? ((res.data && res.data.rows) || []) : [];
    var total = res.code === 0 ? ((res.data && res.data.total) || 0) : 0;

    renderTable(box, {
      title: '风控事件',
      state: riskState,
      fields: fields,
      columns: [
        { key: 'id', title: 'ID', className: 'num' },
        { key: 'created_at', title: '时间', render: function (r) { return esc(fmtTime(r.created_at)); } },
        { key: 'type', title: '类型', render: function (r) { return '<span class="pill">' + esc(r.type || '-') + '</span>'; } },
        { key: 'user_id', title: '用户', render: function (r) { return esc(r.username || r.user_id || '-'); } },
        { key: 'ip', title: 'IP', render: function (r) { return esc(r.ip || '-'); } },
        { key: 'detail', title: '详情', className: 'wrap', render: function (r) { return jsonBlock(r.detail || r.msg); } },
        { key: 'handled', title: '处理状态', render: function (r) { return boolTag(Number(r.handled) === 1, '已处理', '未处理'); } },
        {
          key: 'ops', title: '操作', className: 'col-actions',
          render: function (r) {
            if (!hasPerm('risk.write') || Number(r.handled) === 1) return '<span class="inline-hint">-</span>';
            return '<button type="button" class="btn-link" data-risk="' + esc(r.id) + '">标记已处理</button>';
          },
        },
      ],
      rows: rows,
      total: total,
      empty: res.code === 0 ? '暂无风控事件' : ('加载失败：' + res.msg),
      reload: reload,
      onRowMount: function (container) {
        $$('[data-risk]', container).forEach(function (btn) {
          btn.addEventListener('click', async function () {
            var res2 = await apiPost('/risk-events/' + btn.getAttribute('data-risk') + '/handle', {});
            if (res2.code === 0) { toast('已处理', 'ok'); reload(); }
          });
        });
      },
    });
  }

  /* ====================================================================== */
  /* 7. 路由                                                                */
  /* ====================================================================== */

  var MENUS = {
    dashboard: { title: '数据概览', icon: '概', group: '经营', render: renderDashboard },
    user: { title: '账号管理', icon: '账', group: '经营', render: renderUser },
    invite: { title: '邀请码管理', icon: '邀', group: '经营', render: renderInvite },
    order: { title: '订单管理', icon: '订', group: '经营', render: renderOrder },
    revenue: { title: '收益管理', icon: '益', group: '经营', render: renderRevenue },
    task: { title: '任务与线路', icon: '任', group: '运营', render: renderTask },
    ticket: { title: '客服工单', icon: '工', group: '运营', render: renderTicket },
    feedback: { title: '意见反馈', icon: '馈', group: '运营', render: renderFeedback },
    version: { title: '版本推送', icon: '版', group: '运营', render: renderVersion },
    notice: { title: '公告管理', icon: '公', group: '运营', render: renderNotice },
    config: { title: '全局配置', icon: '配', group: '系统', render: renderConfig },
    panel: { title: '面板管理', icon: '面', group: '系统', render: renderPanel },
    audit: { title: '审计与日志', icon: '审', group: '系统', render: renderAudit },
  };

  function parseHash() {
    var raw = String(location.hash || '').replace(/^#\/?/, '');
    var parts = raw.split('?');
    var key = parts[0] || 'dashboard';
    return { key: key, query: parts[1] || '' };
  }

  function renderNav() {
    var nav = document.getElementById('nav');
    if (!nav) return;
    var visible = Object.keys(MENUS).filter(function (key) { return hasMenu(key); });
    if (!visible.length) {
      nav.innerHTML = '<div class="nav-group">无可用菜单</div>';
      return;
    }
    var groups = [];
    visible.forEach(function (key) {
      var g = MENUS[key].group;
      if (groups.indexOf(g) < 0) groups.push(g);
    });
    nav.innerHTML = groups.map(function (g) {
      return '<div class="nav-group">' + esc(g) + '</div>' + visible.filter(function (key) {
        return MENUS[key].group === g;
      }).map(function (key) {
        return '<div class="nav-item' + (state.page === key ? ' active' : '') + '" data-nav="' + esc(key) + '">' +
          '<span class="nav-dot"></span><span>' + esc(MENUS[key].title) + '</span></div>';
      }).join('');
    }).join('');
    $$('[data-nav]', nav).forEach(function (el) {
      el.addEventListener('click', function () {
        location.hash = '#/' + el.getAttribute('data-nav');
      });
    });
  }

  async function route() {
    var parsed = parseHash();
    var key = parsed.key;
    if (!MENUS[key]) key = 'dashboard';
    if (!hasMenu(key)) {
      var first = Object.keys(MENUS).filter(function (k) { return hasMenu(k); })[0];
      if (first && first !== key) { location.hash = '#/' + first; return; }
      viewEl().innerHTML = noticeBar('当前账号没有任何可见菜单，请联系超级管理员分配角色权限。', 'warn');
      return;
    }
    if (state.cleanup) { try { state.cleanup(); } catch (e) { /* 忽略 */ } state.cleanup = null; }
    chartRegistry.length = 0;
    state.page = key;
    renderNav();
    var host = viewEl();
    if (!host) return;
    try {
      await MENUS[key].render(host);
    } catch (err) {
      host.innerHTML = noticeBar('页面渲染异常：' + esc(err && err.message ? err.message : String(err)), 'danger');
    }
  }

  /* ====================================================================== */
  /* 8. 登录 / 启动                                                         */
  /* ====================================================================== */

  function showLogin(message) {
    var layer = document.getElementById('login-layer');
    var app = document.getElementById('app-layer');
    if (layer) layer.hidden = false;
    if (app) app.hidden = true;
    var err = document.getElementById('login-error');
    if (err) {
      if (message) { err.hidden = false; err.textContent = message; }
      else { err.hidden = true; err.textContent = ''; }
    }
    var pw = document.getElementById('login-password');
    if (pw) pw.value = '';
  }

  function showApp() {
    var layer = document.getElementById('login-layer');
    var app = document.getElementById('app-layer');
    if (layer) layer.hidden = true;
    if (app) app.hidden = false;
    var admin = state.admin || {};
    var el = document.getElementById('top-admin');
    if (el) {
      el.textContent = (admin.real_name || admin.username || '-') +
        (admin.role_name ? ' · ' + admin.role_name : '') +
        '（' + (admin.username || '-') + '）';
    }
    renderNav();
  }

  async function doLogout(skipConfirm) {
    if (!skipConfirm) {
      var cf = await confirmDialog('确认退出当前管理员登录？', { title: '退出登录', confirmText: '退出', danger: false });
      if (!cf) return;
    }
    await apiPost('/logout', {}, { silent: true });
    setToken(null);
    setStoredAdmin(null);
    location.hash = '';
    showLogin();
  }

  function quickPasswordDialog() {
    promptDialog({
      title: '修改登录密码',
      intro: '修改成功后所有后台会话失效，需要重新登录。',
      fields: [
        { key: 'old_password', label: '原密码', type: 'password', required: true },
        { key: 'new_password', label: '新密码（至少 8 位）', type: 'password', required: true },
        { key: 'new_password2', label: '确认新密码', type: 'password', required: true },
      ],
      confirmText: '修改密码',
    }).then(async function (values) {
      if (!values) return;
      if (String(values.new_password).length < 8) { toast('新密码至少 8 位', 'warn'); return; }
      if (values.new_password !== values.new_password2) { toast('两次输入的新密码不一致', 'warn'); return; }
      var res = await apiPost('/password', { old_password: values.old_password, new_password: values.new_password });
      if (res.code === 0) {
        toast('密码已修改，请重新登录', 'ok');
        setTimeout(function () { doLogout(true); }, 800);
      }
    });
  }

  async function refreshProfile() {
    var res = await apiGet('/profile', null, { silent: true });
    if (res.code === 0 && res.data && res.data.admin) {
      setStoredAdmin(res.data.admin);
      showApp();
      return true;
    }
    return false;
  }

  function bindLoginForm() {
    var form = document.getElementById('login-form');
    var submit = document.getElementById('login-submit');
    var totpField = document.getElementById('login-totp-field');
    var err = document.getElementById('login-error');

    form.addEventListener('submit', async function (e) {
      e.preventDefault();
      var username = document.getElementById('login-username').value.trim();
      var password = document.getElementById('login-password').value;
      var totp = document.getElementById('login-totp').value.trim();
      if (!username || !password) {
        err.hidden = false;
        err.textContent = '请输入账号与密码';
        return;
      }
      submit.disabled = true;
      submit.textContent = '登录中…';
      err.hidden = true;
      try {
        var res = await apiPost('/login', { username: username, password: password, totp: totp || undefined }, { silent: true });
        if (res.code === 0) {
          var data = res.data || {};
          setToken(data.token);
          setStoredAdmin(data.admin);
          document.getElementById('login-password').value = '';
          document.getElementById('login-totp').value = '';
          totpField.hidden = true;
          toast('登录成功，欢迎回来 ' + ((data.admin && data.admin.username) || ''), 'ok');
          showApp();
          if (!location.hash) location.hash = '#/dashboard';
          await route();
        } else if (res.code === 6003) {
          totpField.hidden = false;
          err.hidden = false;
          err.textContent = res.msg || '请输入动态验证码';
          document.getElementById('login-totp').focus();
        } else if (res.code === 6002) {
          err.hidden = false;
          err.textContent = res.msg || '账号已被禁用';
        } else {
          err.hidden = false;
          err.textContent = res.msg || '登录失败';
        }
      } finally {
        submit.disabled = false;
        submit.textContent = '登 录';
      }
    });
  }

  async function boot() {
    bindLoginForm();

    var logoutBtn = document.getElementById('btn-logout');
    if (logoutBtn) logoutBtn.addEventListener('click', function () { doLogout(false); });
    var refreshBtn = document.getElementById('btn-refresh');
    if (refreshBtn) refreshBtn.addEventListener('click', function () { route(); });
    var pwBtn = document.getElementById('btn-quickpw');
    if (pwBtn) pwBtn.addEventListener('click', quickPasswordDialog);

    window.addEventListener('hashchange', function () { route(); });

    var stored = getStoredAdmin();
    var token = getToken();
    if (token) {
      state.admin = stored;
      if (stored) showApp();
      var ok = await refreshProfile();
      if (!ok) {
        setToken(null);
        setStoredAdmin(null);
        showLogin('登录已失效，请重新登录');
        return;
      }
      if (!location.hash) location.hash = '#/dashboard';
      await route();
    } else {
      showLogin();
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
