/* WeChat "我" pages: 收藏 / 朋友圈入口 / 卡包 / 表情 / 设置.
 *
 * 卡包 / 表情 / 设置 use the real WeChat screenshots as the content image (the user
 * asked for pixel-identical pages and allowed image crops). 收藏 is dynamic: its
 * content is randomised (never the user's own data) and chat long-press 收藏 writes
 * into the same localStorage list.
 *
 * Routes:
 *   #/wechat/me/favorites[/new|/:id]
 *   #/wechat/me/cards[/coupon|/member|/transit|/ticket]
 *   #/wechat/me/stickers[/:pack]
 *   #/wechat/me/settings[/:key]
 *   #/wechat/moments (existing app page)
 */
(function () {
  'use strict';
  if (window.__fwMePagesLoaded) return;
  window.__fwMePagesLoaded = true;

  var HOST_ID = 'fw-me-pages-host';
  var FAV_KEY = 'fw-me-favorites-v2';
  var STICKER_KEY = 'fw-me-stickers-v2';
  var SETTINGS_KEY = 'fw-me-settings-v2';
  var SHOT = {
    cards: './overrides/me/cards.png?v=75',
    settings: './overrides/me/settings.png?v=75',
    stickers: './overrides/me/stickers.png?v=75'
  };
  var PHOTOS = [
    './assets/user-avatar-1-CKPDcH57.jpg',
    './assets/user-avatar-4-Cmbgep_s.jpg',
    './assets/user-avatar-5-DMigR-cn.jpg',
    './assets/user-avatar-6-Cq95zrQT.jpg',
    './assets/user-avatar-7-C4bh6bYx.jpg',
    './assets/user-avatar-8-BysMeksY.jpg',
    './assets/user-avatar-10-ZKj7tiz_.jpg'
  ];
  var CONTACTS = ['陈默', '林知夏', '周予安', '苏晚', '顾言', '江屿', '沈星', '许棠', '陆川', '曹磊', '宋清和', '叶知秋'];
  var TAGS = ['家人', '工作', '旅行', '生活', '学习'];
  var PDF_NAMES = [
    'dzfp_25312000000080552781_20260701.pdf',
    '项目周报_2026W42.pdf',
    '护照扫描件_2026.pdf',
    '合同_终版_已签.pdf',
    '体检报告_2026.pdf',
    '学习资料_第3章.pdf'
  ];
  var FAV_SORT = [
    { key: 'recent', label: '最近使用' },
    { key: 'new', label: '最新收藏' },
    { key: 'old', label: '最早收藏' }
  ];
  var STICKER_PACKS = [
    ['鱼烧哥窝囊篇', '🐟'], ['呆猫八条9', '🐱'], ['水豚噜噜14', '🦫'], ['月薪喵3', '😾'],
    ['瞅他', '👀'], ['奶龙③', '🐣'], ['高雅人士②', '🐧'], ['高情商聊天', '💬'],
    ['可爱小猫喵呜', '🐈'], ['撩人情话', '💌'], ['鱼烧哥第一弹', '🐟'], ['骂人不含脏字1', '🤬'],
    ['水豚噜噜15', '🦫'], ['呆猫八条6', '🐱'], ['月薪喵1', '😾'], ['奶龙①', '🐣'],
    ['恶搞聊天信息', '❗'], ['这狗30', '🐶'], ['子羊子浩第1弹', '😏'], ['小墨的可爱表情包', '🧸'],
    ['大额红包', '🧧'], ['大肥猪', '🐷']
  ];

  var host = null;
  var view = { favTab: 'recent', favQuery: '', favSort: 'recent' };
  var dialog = null;
  var toastTimer = 0;
  var suppressClickUntil = 0;
  var pressTimer = 0;
  var pressStart = null;
  var pressItem = null;

  /* ---------------------------------------------------------------- storage */

  function read(key, fallback) {
    try { var raw = localStorage.getItem(key); return raw ? JSON.parse(raw) : fallback; } catch (e) { return fallback; }
  }
  function write(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {}
  }
  function pick(list) { return list[Math.floor(Math.random() * list.length)]; }
  function rid() { return 'f-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6); }
  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function nowTime() { var d = new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()); }
  function shortDate(ts) {
    var d = new Date(ts || Date.now());
    var now = new Date();
    if (d.toDateString() === now.toDateString()) return '今天';
    if (d.getFullYear() === now.getFullYear()) return (d.getMonth() + 1) + '月' + d.getDate() + '日';
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
  }
  function randomDate() {
    var ts = Date.now() - Math.floor(Math.random() * 1000 * 60 * 60 * 24 * 900);
    return { ts: ts, date: shortDate(ts) };
  }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function seedFavorites() {
    var items = [];
    var f = randomDate();
    items.push({ id: rid(), kind: 'file', title: pick(PDF_NAMES), size: (Math.random() * 3 + 0.2).toFixed(1) + 'MB', source: pick(CONTACTS), ts: f.ts, date: f.date });
    for (var i = 0; i < 3; i++) {
      var d = randomDate();
      items.push({ id: rid(), kind: 'image', image: pick(PHOTOS), title: '图片', source: pick(CONTACTS), ts: d.ts, date: d.date, tag: pick(TAGS) });
    }
    var t = randomDate();
    items.push({ id: rid(), kind: 'text', title: '这周的待办和想法', body: '把相册按时间归档；删掉重复截图；重要的照片备份到电脑。周末整理一下，别再拖了。', source: pick(CONTACTS), ts: t.ts, date: t.date });
    var l = randomDate();
    items.push({ id: rid(), kind: 'link', title: '微信公众平台：如何高效整理手机相册', body: '手机相册越来越满，其实只需要三步：先按时间归档，再删除重复截图，最后把重要照片备份到电脑或云端。', source: pick(CONTACTS), ts: l.ts, date: l.date });
    var v = randomDate();
    items.push({ id: rid(), kind: 'video', image: pick(PHOTOS), duration: '02:18', title: '周末露营记录', source: pick(CONTACTS), ts: v.ts, date: v.date });
    items.sort(function (a, b) { return b.ts - a.ts; });
    return items;
  }

  var favorites = read(FAV_KEY, null);
  if (!Array.isArray(favorites) || !favorites.length) {
    favorites = seedFavorites();
    write(FAV_KEY, favorites);
  }
  var addedPacks = read(STICKER_KEY, []);
  if (!Array.isArray(addedPacks)) addedPacks = [];
  var settings = read(SETTINGS_KEY, { youth: false, care: false, notify: true });

  /* ------------------------------------------------------------------ ui kit */

  function icon(name) {
    var p = {
      back: '<path d="M15 4 7 12l8 8" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"/>',
      plus: '<circle cx="12" cy="12" r="9.2" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M12 7.5v9M7.5 12h9" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
      search: '<circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.9"/><path d="m16 16 4 4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>',
      chevron: '<path d="m9 5 7 7-7 7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>',
      more: '<circle cx="5" cy="12" r="1.9"/><circle cx="12" cy="12" r="1.9"/><circle cx="19" cy="12" r="1.9"/>',
      tag: '<path d="M3.5 11.2 11 3.7h7.3v7.3l-7.5 7.5z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><circle cx="15.6" cy="7.9" r="1.2" fill="currentColor"/>',
      down: '<path d="m6 9 6 6 6-6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>',
      play: '<circle cx="12" cy="12" r="10" fill="rgba(0,0,0,.45)"/><path d="M10 8.5v7l6-3.5z" fill="#fff"/>'
    }[name] || '';
    return '<svg class="fwm-icon" viewBox="0 0 24 24" aria-hidden="true">' + p + '</svg>';
  }
  // 顶部任务栏/状态栏交给系统：这里只留 44px 安全区，不画时间、信号、电量。
  function statusBar() {
    return '<div class="fwm-status"></div>';
  }
  function header(title, right, backHash) {
    return '<header class="fwm-header"><button type="button" class="fwm-back" data-back="' + esc(backHash || '') + '" aria-label="返回">' + icon('back') + '</button>' +
      '<h1>' + esc(title) + '</h1><div class="fwm-header-right">' + (right || '') + '</div></header>';
  }
  function pageShell(title, right, body, backHash) {
    return '<div class="fwm-page">' + statusBar() + header(title, right, backHash) + '<div class="fwm-scroll">' + body + '</div></div>';
  }
  function toast(text) {
    if (!host) return;
    var el = host.querySelector('.fwm-toast');
    if (!el) { el = document.createElement('div'); el.className = 'fwm-toast'; host.appendChild(el); }
    el.textContent = text;
    el.classList.add('is-open');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('is-open'); }, 1600);
  }
  function openDialog(opts) {
    dialog = opts;
    if (!host) return;
    var el = host.querySelector('.fwm-dialog');
    if (!el) { el = document.createElement('div'); el.className = 'fwm-dialog'; host.appendChild(el); }
    el.innerHTML = '<div class="fwm-dialog-mask"></div><div class="fwm-dialog-panel"><h3>' + esc(opts.title || '') + '</h3>' +
      (opts.text ? '<p>' + esc(opts.text) + '</p>' : '') +
      '<div class="fwm-dialog-actions">' + (opts.cancel === false ? '' : '<button type="button" data-dialog-cancel>' + esc(opts.cancelText || '取消') + '</button>') +
      '<button type="button" data-dialog-ok class="' + (opts.danger ? 'fwm-danger' : 'fwm-primary') + '">' + esc(opts.okText || '确定') + '</button></div></div>';
    el.classList.add('is-open');
  }
  function closeDialog() {
    if (!host) return;
    var el = host.querySelector('.fwm-dialog');
    if (el) el.classList.remove('is-open');
    dialog = null;
  }

  /* -------------------------------------------------------------- favorites */

  function favFilter(item) {
    if (view.favTab === 'media' && item.kind !== 'image' && item.kind !== 'video') return false;
    if (view.favTab === 'files' && item.kind !== 'file') return false;
    var q = view.favQuery.trim().toLowerCase();
    if (q && (item.title + ' ' + (item.source || '') + ' ' + (item.body || '')).toLowerCase().indexOf(q) < 0) return false;
    return true;
  }
  function favSorted() {
    var list = favorites.filter(favFilter);
    if (view.favSort === 'old') list.sort(function (a, b) { return (a.ts || 0) - (b.ts || 0); });
    else if (view.favSort === 'new') list.sort(function (a, b) { return (b.ts || 0) - (a.ts || 0); });
    return list;
  }
  function closeFavMenu() {
    if (!host) return;
    var nodes = host.querySelectorAll('.fwm-favmenu-mask,.fwm-favmenu-panel');
    for (var i = 0; i < nodes.length; i++) nodes[i].remove();
  }
  function openFavMenu(anchor) {
    closeFavMenu();
    if (!anchor || !host) return;
    var rect = anchor.getBoundingClientRect();
    var mask = document.createElement('div');
    mask.className = 'fwm-favmenu-mask';
    var panel = document.createElement('div');
    panel.className = 'fwm-favmenu-panel';
    panel.style.top = Math.round(rect.bottom + 8) + 'px';
    panel.style.right = Math.max(8, Math.round(window.innerWidth - rect.right - 4)) + 'px';
    panel.innerHTML = '<span class="fwm-favmenu-caret"></span>' + FAV_SORT.map(function (item) {
      var active = view.favSort === item.key;
      return '<button type="button" class="fwm-favmenu-row' + (active ? ' is-active' : '') + '" data-favsort="' + item.key + '">' +
        '<span>' + item.label + '</span>' + (active ? '<span class="fwm-favmenu-check">✓</span>' : '') + '</button>';
    }).join('');
    host.appendChild(mask);
    host.appendChild(panel);
    mask.addEventListener('click', closeFavMenu);
    panel.addEventListener('click', function (e) {
      var btn = e.target.closest('[data-favsort]');
      if (!btn) return;
      view.favSort = btn.getAttribute('data-favsort');
      closeFavMenu();
      render();
    });
  }
  function pdfIcon() { return '<span class="fwm-pdf"><i>PDF</i></span>'; }
  function favCard(item) {
    var head = '';
    if (item.kind === 'file') {
      head = '<div class="fwm-fav-card fwm-fav-card-file" data-fav="' + esc(item.id) + '">' +
        '<div class="fwm-fav-file-title">' + esc(item.title) + '</div>' +
        '<div class="fwm-fav-file-sub">PDF ' + esc(item.size || '') + '</div>' + pdfIcon() + '</div>';
    } else if (item.kind === 'image') {
      head = '<div class="fwm-fav-card fwm-fav-card-image" data-fav="' + esc(item.id) + '">' +
        '<img class="fwm-fav-photo" src="' + esc(item.image) + '" alt="">' +
        (item.tag ? '<div class="fwm-fav-tag">' + icon('tag') + esc(item.tag) + '</div>' : '') + '</div>';
    } else if (item.kind === 'video') {
      head = '<div class="fwm-fav-card fwm-fav-card-image" data-fav="' + esc(item.id) + '">' +
        '<div class="fwm-fav-video"><img class="fwm-fav-photo" src="' + esc(item.image) + '" alt="">' +
        '<span class="fwm-fav-play">' + icon('play') + '</span><em>' + esc(item.duration || '') + '</em></div></div>';
    } else if (item.kind === 'text') {
      head = '<div class="fwm-fav-card fwm-fav-card-text" data-fav="' + esc(item.id) + '">' +
        '<div class="fwm-fav-text-title">' + esc(item.title) + '</div>' +
        '<div class="fwm-fav-text-body">' + esc((item.body || '').slice(0, 90)) + '</div></div>';
    } else {
      head = '<div class="fwm-fav-card fwm-fav-card-link" data-fav="' + esc(item.id) + '">' +
        '<div class="fwm-fav-link-title">' + esc(item.title) + '</div>' +
        '<div class="fwm-fav-text-body">' + esc((item.body || '').slice(0, 80)) + '</div></div>';
    }
    return '<div class="fwm-fav-item">' + head +
      '<div class="fwm-fav-foot"><span class="fwm-fav-source">' + esc(item.source || '') + '</span>' +
      '<span class="fwm-fav-date">' + esc(item.date || '') + '</span></div></div>';
  }
  function favoritesPage() {
    var list = favSorted();
    var body = '<div class="fwm-fav-search"><span>' + icon('search') + '</span><input data-fav-search value="' + esc(view.favQuery) + '" placeholder="搜索" autocomplete="off"></div>' +
      '<div class="fwm-fav-tabs">' +
      ['recent|最近使用', 'media|图片与视频', 'files|文件'].map(function (t) {
        var p = t.split('|');
        return '<button type="button" data-favtab="' + p[0] + '" class="' + (view.favTab === p[0] ? 'is-active' : '') + '">' + p[1] + '</button>';
      }).join('') +
      '<button type="button" class="fwm-fav-drop" data-favdrop aria-label="筛选">' + icon('down') + '</button></div>' +
      '<div class="fwm-fav-list" data-fav-list>' +
      (list.length ? list.map(favCard).join('') : '<div class="fwm-empty"><div class="fwm-empty-art">🗂️</div><b>暂无收藏</b><p>在聊天里长按消息，点「收藏」就会出现在这里</p></div>') +
      '</div>';
    return pageShell('收藏', '<button type="button" class="fwm-header-btn" data-favnew aria-label="新建笔记">' + icon('plus') + '</button>', body);
  }
  function favoriteDetail(id) {
    var item = favorites.filter(function (x) { return x.id === id; })[0];
    if (!item) return pageShell('收藏', '', '<div class="fwm-empty"><b>收藏不存在</b></div>', '#/wechat/me/favorites');
    var content = '';
    if (item.kind === 'file') {
      content = '<div class="fwm-fav-detail-file">' + pdfIcon() + '<b>' + esc(item.title) + '</b><em>PDF ' + esc(item.size || '') + '</em></div>';
    } else if (item.kind === 'image' || item.kind === 'video') {
      content = '<img class="fwm-fav-detail-photo" src="' + esc(item.image) + '" alt="">';
    } else {
      content = '<p class="fwm-fav-detail-text">' + esc(item.body || item.title) + '</p>';
    }
    var body = '<div class="fwm-fav-detail">' +
      '<div class="fwm-fav-detail-meta"><span>' + esc(item.source || '') + '</span><span>' + esc(item.date || '') + '</span></div>' +
      (item.kind !== 'file' ? '<h2>' + esc(item.title) + '</h2>' : '') +
      content +
      (item.tag ? '<div class="fwm-fav-tag fwm-fav-tag-detail">' + icon('tag') + esc(item.tag) + '</div>' : '') +
      '<div class="fwm-fav-detail-actions">' +
      '<button type="button" class="fwm-btn fwm-btn-light" data-favdel="' + esc(item.id) + '">删除</button>' +
      '<button type="button" class="fwm-btn fwm-btn-primary" data-toast="已打开（演示）">打开</button>' +
      '</div></div>';
    return pageShell('收藏', '', body, '#/wechat/me/favorites');
  }
  function favoriteNew() {
    var body = '<div class="fwm-note"><textarea data-note placeholder="写点什么…" autofocus></textarea>' +
      '<div class="fwm-note-actions"><button type="button" class="fwm-btn fwm-btn-primary" data-notesave>保存到收藏</button></div></div>';
    return pageShell('新建笔记', '', body, '#/wechat/me/favorites');
  }

  /* --------------------------------------------------------------- 卡包 shot */

  function hot(x, y, w, h, act, label) {
    return '<button type="button" class="fwm-hot" style="left:' + x + '%;top:' + y + '%;width:' + w + '%;height:' + h + '%" data-hot="' + act + '" aria-label="' + esc(label || '') + '"></button>';
  }
  function shotPage(title, right, src, hotspots, backHash) {
    return pageShell(title, right, '<div class="fwm-shotwrap"><img class="fwm-shot" src="' + src + '" alt="">' + hotspots + '</div>', backHash);
  }
  var CARDS_HOT =
    hot(0, 0, 100, 13.2, 'cards:coupon', '优惠券') +
    hot(0, 18, 100, 14.7, 'cards:member', '会员卡') +
    hot(0, 32.7, 100, 13.7, 'cards:transit', '交通卡') +
    hot(0, 80, 100, 13, 'cards:ticket', '票证');
  function cardsPage() {
    return shotPage('卡包', '<button type="button" class="fwm-header-btn" data-toast="更多">' + icon('more') + '</button>', SHOT.cards, CARDS_HOT, '#/wechat/my');
  }
  var CARD_KIND = {
    coupon: { title: '优惠券', empty: '暂无优惠券' },
    member: { title: '会员卡', empty: '暂无会员卡' },
    transit: { title: '交通卡', empty: '暂无交通卡' },
    ticket: { title: '票证', empty: '暂无票证' }
  };
  function cardKindPage(kind) {
    var meta = CARD_KIND[kind] || CARD_KIND.coupon;
    var body = '<div class="fwm-empty"><div class="fwm-empty-art">💳</div><b>' + meta.empty + '</b><p>在支持「卡包」的小程序或商家处领取后会出现在这里</p></div>';
    return pageShell(meta.title, '', body, '#/wechat/me/cards');
  }

  /* ------------------------------------------------------------- 设置 shot */

  var SET_HOT =
    hot(0, 1.5, 100, 3.2, 'set:search', '搜索') +
    hot(0, 7.2, 100, 2.4, 'set:profile', '个人资料') +
    hot(0, 11.2, 100, 2.3, 'set:account', '账号安全') +
    hot(0, 15.0, 100, 2.3, 'set:privacy', '个人信息与权限') +
    hot(0, 20.7, 100, 2.3, 'set:notify', '通知') +
    hot(0, 24.6, 100, 2.3, 'set:general', '界面与显示') +
    hot(0, 28.5, 100, 2.3, 'set:privacy', '朋友权限') +
    hot(0, 32.4, 100, 2.3, 'set:storage', '存储空间') +
    hot(0, 36.2, 100, 2.3, 'set:more', '更多') +
    hot(0, 42.0, 100, 2.3, 'set:chat', '聊天') +
    hot(0, 45.9, 100, 2.3, 'set:call', '音视频通话') +
    hot(0, 49.8, 100, 2.3, 'set:chat', '聊天记录管理') +
    hot(0, 53.7, 100, 2.3, 'set:other', '其他功能') +
    hot(0, 57.5, 100, 2.3, 'set:plugin', '插件') +
    hot(0, 63.3, 100, 2.3, 'set:help', '帮助与反馈') +
    hot(0, 67.2, 100, 2.3, 'set:about', '关于微信') +
    hot(0, 71.5, 100, 2.6, 'set:switch', '切换账号') +
    hot(0, 75.6, 100, 2.6, 'set:logout', '退出登录') +
    hot(0, 82.6, 100, 2.5, 'set:collect', '个人信息收集清单') +
    hot(50, 82.6, 50, 2.5, 'set:share', '第三方信息共享清单');
  function settingsPage() {
    return shotPage('设置', '', SHOT.settings, SET_HOT, '#/wechat/my');
  }
  function settingsDetail(key) {
    var title = '设置';
    var body = '';
    if (key === 'account') {
      title = '账号与安全';
      body = '<div class="fwm-group">' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">微信号</span></span><span class="fwm-row-value">link</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">手机号</span></span><span class="fwm-row-value">138****0164</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">微信密码</span></span><span class="fwm-row-value">已设置</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">声音锁</span></span><span class="fwm-row-value">未设置</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div></div>';
    } else if (key === 'notify') {
      title = '通知';
      body = '<div class="fwm-group"><label class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">接收新消息通知</span></span><span class="fwm-switch"><input type="checkbox" data-settoggle="notify"' + (settings.notify ? ' checked' : '') + '><i></i></span></label></div>';
    } else if (key === 'privacy') {
      title = '朋友权限';
      body = '<div class="fwm-group">' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">添加我的方式</span></span><span class="fwm-row-value">微信号、手机号</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">朋友圈和视频动态</span></span><span class="fwm-row-value">全部可见</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div></div>';
    } else if (key === 'chat') {
      title = '聊天';
      body = '<div class="fwm-group">' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">聊天记录迁移与备份</span></span><span class="fwm-row-value">未备份</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">聊天记录管理</span></span><span class="fwm-row-tail">' + icon('chevron') + '</span></div></div>';
    } else if (key === 'general') {
      title = '界面与显示';
      body = '<div class="fwm-group">' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">字体大小</span></span><span class="fwm-row-value">标准</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">聊天背景</span></span><span class="fwm-row-value">默认</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div></div>';
    } else if (key === 'help') {
      title = '帮助与反馈';
      body = '<div class="fwm-group">' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">常见问题</span></span><span class="fwm-row-value">12 个</span><span class="fwm-row-tail">' + icon('chevron') + '</span></div>' +
        '<div class="fwm-row"><span class="fwm-row-main"><span class="fwm-row-title">意见反馈</span></span><span class="fwm-row-tail">' + icon('chevron') + '</span></div></div>';
    } else if (key === 'about') {
      title = '关于微信';
      body = '<div class="fwm-about"><span class="fwm-about-logo">💬</span><b>微信</b><em>版本 8.0.78</em><button type="button" class="fwm-btn fwm-btn-primary" data-toast="已是最新版本">检查更新</button></div>';
    } else {
      return pageShell('设置', '', '<div class="fwm-empty"><b>演示页面</b><p>该入口暂未接入更多内容</p></div>', '#/wechat/me/settings');
    }
    return pageShell(title, '', body, '#/wechat/me/settings');
  }

  /* ------------------------------------------------------------ 表情 shot */

  function stickerHotspots() {
    var out = '';
    for (var i = 0; i < STICKER_PACKS.length; i++) {
      var top = 9.6 + i * 4.05;
      out += hot(0, top, 72, 3.7, 'sticker:pack:' + i, STICKER_PACKS[i][0]);
      out += hot(71, top + 0.35, 25, 3.0, 'sticker:add:' + i, '添加');
    }
    out += hot(0, 0, 100, 4, 'sticker:search', '搜索');
    out += hot(0, 4.2, 100, 6, 'sticker:banner', '精选推荐');
    return out;
  }
  function stickerHeader() {
    return '<header class="fwm-header fwm-header-tabs"><button type="button" class="fwm-back" data-back="#/wechat/my" aria-label="返回">' + icon('back') + '</button>' +
      '<div class="fwm-top-tabs"><button type="button" class="is-active" data-stickertab="featured">精选表情</button>' +
      '<button type="button" data-stickertab="more">更多表情</button></div>' +
      '<div class="fwm-header-right"><button type="button" class="fwm-header-btn" data-toast="更多">' + icon('more') + '</button></div></header>';
  }
  function stickersPage() {
    var overlays = addedPacks.map(function (i) {
      var top = 9.6 + Number(i) * 4.05 + 0.35;
      return '<span class="fwm-added" style="top:' + top + '%">已添加</span>';
    }).join('');
    return '<div class="fwm-page">' + statusBar() + stickerHeader() +
      '<div class="fwm-scroll"><div class="fwm-shotwrap"><img class="fwm-shot" src="' + SHOT.stickers + '" alt="">' +
      stickerHotspots() + overlays + '</div></div></div>';
  }
  function packPage(idx) {
    var pack = STICKER_PACKS[Number(idx)] || STICKER_PACKS[0];
    var emoji = ['😀', '😁', '😂', '🤣', '😊', '😍', '🤔', '😎', '😭', '😡', '👍', '🙏', '👏', '🎉', '❤️', '💔'];
    var isAdded = addedPacks.indexOf(String(idx)) >= 0;
    var body = '<div class="fwm-pack-hero"><span class="fwm-pack-cover fwm-tone-green">' + pack[1] + '</span><b>' + esc(pack[0]) + '</b><em>' + emoji.length + ' 个表情</em>' +
      '<button type="button" class="fwm-btn ' + (isAdded ? 'fwm-btn-light' : 'fwm-btn-primary') + ' fwm-pack-add" data-stickeradd="' + idx + '">' + (isAdded ? '已添加' : '添加') + '</button></div>' +
      '<div class="fwm-emoji-grid">' + emoji.map(function (e) { return '<button type="button" class="fwm-emoji" data-toast="' + e + '">' + e + '</button>'; }).join('') + '</div>';
    return pageShell('表情', '', body, '#/wechat/me/stickers');
  }

  /* ------------------------------------------------------------------ router */

  function currentRoute() { return (location.hash || '').split('?')[0]; }
  function isMe(route) { return route.indexOf('#/wechat/me/') === 0; }
  function page(kind, id) {
    if (kind === 'favorites') {
      if (id === 'new') return favoriteNew();
      return id ? favoriteDetail(id) : favoritesPage();
    }
    if (kind === 'cards') return id ? cardKindPage(id) : cardsPage();
    if (kind === 'stickers') return id ? packPage(id) : stickersPage();
    if (kind === 'settings') return id ? settingsDetail(id) : settingsPage();
    return pageShell('收藏', '', '<div class="fwm-empty"><b>页面不存在</b></div>', '#/wechat/my');
  }
  function render() {
    if (!host) { host = document.createElement('div'); host.id = HOST_ID; document.documentElement.appendChild(host); }
    var r = currentRoute();
    var root = document.getElementById('root');
    if (!isMe(r)) {
      host.style.display = 'none';
      if (root) root.style.display = '';
      return;
    }
    if (root) root.style.display = 'none';
    host.style.display = 'block';
    try { window.DoutuApp && window.DoutuApp.setPayPageSystemBars && window.DoutuApp.setPayPageSystemBars(false); } catch (_) {}
    var seg = r.replace(/^#/, '').split('/').filter(Boolean);
    host.innerHTML = page(seg[2] || '', seg[3] || '');
    var sc = host.querySelector('.fwm-scroll');
    if (sc) sc.scrollTop = 0;
  }
  function nav(hash) { if (location.hash === hash) render(); else location.hash = hash; }
  function back(fallback) {
    if (fallback) { nav(fallback); return; }
    if (history.length > 1) history.back(); else nav('#/wechat/my');
  }

  /* ---------------------------------------------------------------- handlers */

  function saveFavorites() { write(FAV_KEY, favorites); }
  function deleteFavorite(id) {
    favorites = favorites.filter(function (x) { return x.id !== id; });
    saveFavorites();
    render();
    toast('已删除');
  }
  function addPack(idx) {
    var key = String(idx);
    if (addedPacks.indexOf(key) >= 0) { toast('已在表情包中'); return; }
    addedPacks.push(key);
    write(STICKER_KEY, addedPacks);
    render();
    toast('已添加到表情');
  }
  window.__fwAddFavoriteFromChat = function (payload) {
    if (!payload) return;
    var kind = (payload.kind === 'image' || payload.kind === 'video' || payload.kind === 'file' || payload.kind === 'music') ? payload.kind : 'text';
    var now = Date.now();
    favorites.unshift({
      id: rid(), kind: kind, title: payload.title || '聊天内容',
      body: payload.body || '', image: payload.image || '', size: payload.size || '',
      duration: payload.duration || '', source: payload.source || '聊天', ts: now, date: '今天 ' + nowTime(), fromChat: true
    });
    if (favorites.length > 80) favorites = favorites.slice(0, 80);
    saveFavorites();
    toast('已收藏');
  };

  function handleHot(act) {
    var parts = act.split(':');
    var head = parts[0];
    var tail = parts.slice(1).join(':');
    if (head === 'cards') { nav('#/wechat/me/cards/' + tail); return; }
    if (head === 'sticker') {
      if (parts[1] === 'add') { addPack(parts[2]); return; }
      if (parts[1] === 'pack') { nav('#/wechat/me/stickers/' + parts[2]); return; }
      if (parts[1] === 'search') { toast('搜索表情（演示）'); return; }
      toast('精选表情（演示）');
      return;
    }
    if (head === 'set') {
      if (tail === 'search') { toast('搜索设置（演示）'); return; }
      if (tail === 'switch') { openDialog({ title: '切换账号', text: '当前账号：link', okText: '添加账号', onOk: function () { toast('演示环境暂不支持添加账号'); } }); return; }
      if (tail === 'logout') { openDialog({ title: '退出登录', text: '退出后，本机将不再接收该账号的新消息。', danger: true, okText: '退出', onOk: function () { toast('已退出登录（演示）'); } }); return; }
      if (tail === 'collect') { openDialog({ title: '个人信息收集清单', text: '本软件仅用于处理您自己创作或拥有版权的素材。', cancel: false, okText: '知道了' }); return; }
      if (tail === 'share') { openDialog({ title: '第三方信息共享清单', text: '演示环境不连接第三方服务。', cancel: false, okText: '知道了' }); return; }
      if (tail === 'storage') { toast('存储空间：演示'); return; }
      if (tail === 'more') { toast('更多设置（演示）'); return; }
      if (tail === 'call') { toast('音视频通话（演示）'); return; }
      if (tail === 'other') { toast('其他功能（演示）'); return; }
      if (tail === 'plugin') { toast('插件（演示）'); return; }
      if (tail === 'profile') { toast('个人资料（演示）'); return; }
      if (['help', 'about', 'account', 'notify', 'chat', 'privacy', 'general'].indexOf(tail) >= 0) {
        nav('#/wechat/me/settings/' + tail); return;
      }
      nav('#/wechat/me/settings');
      return;
    }
    toast('演示');
  }

  function bind() {
    document.addEventListener('click', function (e) {
      if (currentRoute() === '#/wechat/my') {
        var n = e.target;
        for (var i = 0; n && i < 9; i++, n = n.parentElement) {
          var t = (n.textContent || '').trim();
          var map = { '收藏': 'favorites', '朋友圈': 'moments', '卡包': 'cards', '表情': 'stickers', '设置': 'settings' };
          if (map[t]) {
            var rect = n.getBoundingClientRect();
            if (rect.height >= 36 && rect.height <= 96) {
              e.preventDefault(); e.stopPropagation();
              if (t === '朋友圈') nav('#/wechat/moments');
              else nav('#/wechat/me/' + map[t]);
              return;
            }
          }
        }
      }
      var el = e.target.closest ? e.target.closest('[data-back],[data-hot],[data-fav],[data-favnew],[data-favtab],[data-favdrop],[data-favdel],[data-stickeradd],[data-stickertab],[data-notesave],[data-dialog-ok],[data-dialog-cancel],[data-toast]') : null;
      if (!el) return;
      if (el.hasAttribute('data-back')) { e.preventDefault(); back(el.getAttribute('data-back')); return; }
      if (el.hasAttribute('data-hot')) { e.preventDefault(); handleHot(el.getAttribute('data-hot')); return; }
      if (el.hasAttribute('data-favnew')) { e.preventDefault(); nav('#/wechat/me/favorites/new'); return; }
      if (el.hasAttribute('data-fav')) {
        e.preventDefault();
        if (Date.now() < suppressClickUntil) return;
        nav('#/wechat/me/favorites/' + el.getAttribute('data-fav'));
        return;
      }
      if (el.hasAttribute('data-favtab')) { e.preventDefault(); view.favTab = el.getAttribute('data-favtab'); render(); return; }
      if (el.hasAttribute('data-favdrop')) {
        e.preventDefault();
        openFavMenu(el);
        return;
      }
      if (el.hasAttribute('data-favdel')) {
        e.preventDefault();
        var fid = el.getAttribute('data-favdel');
        openDialog({ title: '删除收藏', text: '删除后无法恢复。', danger: true, okText: '删除', onOk: function () {
          favorites = favorites.filter(function (x) { return x.id !== fid; });
          saveFavorites(); nav('#/wechat/me/favorites'); toast('已删除');
        }});
        return;
      }
      if (el.hasAttribute('data-notesave')) {
        e.preventDefault();
        var ta = document.querySelector('[data-note]');
        var text = ta ? ta.value.trim() : '';
        if (!text) { toast('写点什么再保存'); return; }
        var now = Date.now();
        favorites.unshift({ id: rid(), kind: 'text', title: text.slice(0, 40), body: text, source: '我', ts: now, date: '今天 ' + nowTime() });
        saveFavorites();
        nav('#/wechat/me/favorites');
        toast('已保存到收藏');
        return;
      }
      if (el.hasAttribute('data-stickeradd')) { e.preventDefault(); addPack(el.getAttribute('data-stickeradd')); return; }
      if (el.hasAttribute('data-stickertab')) {
        e.preventDefault();
        if (el.getAttribute('data-stickertab') === 'more') toast('更多表情（演示）');
        return;
      }
      if (el.hasAttribute('data-toast')) { e.preventDefault(); toast(el.getAttribute('data-toast')); return; }
      if (el.hasAttribute('data-dialog-ok')) { e.preventDefault(); var d = dialog; closeDialog(); if (d && d.onOk) d.onOk(); return; }
      if (el.hasAttribute('data-dialog-cancel')) { e.preventDefault(); closeDialog(); }
    }, true);

    document.addEventListener('change', function (e) {
      var t = e.target;
      if (!t || !t.hasAttribute || !t.hasAttribute('data-settoggle')) return;
      settings[t.getAttribute('data-settoggle')] = !!t.checked;
      write(SETTINGS_KEY, settings);
    });

    document.addEventListener('input', function (e) {
      var t = e.target;
      if (!t || !t.hasAttribute || !t.hasAttribute('data-fav-search')) return;
      view.favQuery = t.value || '';
      var list = document.querySelector('[data-fav-list]');
      if (list) {
        var items = favSorted();
        list.innerHTML = items.length ? items.map(favCard).join('') : '<div class="fwm-empty"><div class="fwm-empty-art">🗂️</div><b>暂无收藏</b><p>换个关键词试试</p></div>';
      }
    });

    // long-press a favorite card → delete confirm
    document.addEventListener('pointerdown', function (e) {
      var item = e.target.closest ? e.target.closest('[data-fav]') : null;
      if (!item || !document.querySelector('[data-fav-list]')) return;
      pressItem = item;
      pressStart = { x: e.clientX, y: e.clientY };
      clearTimeout(pressTimer);
      pressTimer = setTimeout(function () {
        if (!pressItem) return;
        suppressClickUntil = Date.now() + 1000;
        var id = pressItem.getAttribute('data-fav');
        pressItem = null;
        try { navigator.vibrate && navigator.vibrate(12); } catch (_) {}
        openDialog({ title: '删除收藏', text: '删除后无法恢复。', danger: true, okText: '删除', onOk: function () { deleteFavorite(id); } });
      }, 550);
    }, true);
    document.addEventListener('pointermove', function (e) {
      if (!pressStart) return;
      if (Math.abs(e.clientX - pressStart.x) > 10 || Math.abs(e.clientY - pressStart.y) > 10) {
        clearTimeout(pressTimer);
        pressItem = null;
        pressStart = null;
      }
    }, true);
    ['pointerup', 'pointercancel'].forEach(function (type) {
      document.addEventListener(type, function () {
        clearTimeout(pressTimer);
        pressItem = null;
        pressStart = null;
      }, true);
    });
    document.addEventListener('contextmenu', function (e) {
      if (e.target.closest && e.target.closest('[data-fav]')) e.preventDefault();
    }, true);

    window.addEventListener('hashchange', render);
    window.addEventListener('popstate', render);
  }

  function start() {
    bind();
    render();
    setInterval(function () {
      var t = document.querySelector('#fw-me-pages-host .fwm-time');
      if (t) t.textContent = nowTime();
    }, 30000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
