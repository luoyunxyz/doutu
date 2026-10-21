/* WeChat "我" secondary pages: 收藏 / 朋友圈入口 / 卡包 / 表情 / 设置.
 *
 * The bundled Fake World app renders 收藏/朋友圈/卡包/表情/设置 rows on #/wechat/my
 * but does not wire them (they are no-ops), and has no routes for them. This override
 * keeps the single React app untouched and paints real WeChat-style pages for:
 *
 *   #/wechat/me/favorites[/id]
 *   #/wechat/me/cards[/id]
 *   #/wechat/me/stickers[/pack]
 *   #/wechat/me/settings[/key]
 *
 * 朋友圈 is an entry to the app's existing #/wechat/moments page.
 *
 * Loaded by both iOS (Resources/web) and Android (assets/fake_world).
 */
(function () {
  'use strict';
  if (window.__fwMePagesLoaded) return;
  window.__fwMePagesLoaded = true;

  var HOST_ID = 'fw-me-pages-host';
  var LS = {
    fav: 'fw-me-favorites-v1',
    cards: 'fw-me-cards-v1',
    stickers: 'fw-me-stickers-v1',
    settings: 'fw-me-settings-v1'
  };
  var host = null;
  var view = { favTab: 'all', cardTab: 'member', stickerTab: 'mine', favQuery: '' };

  /* ------------------------------------------------------------------ data */

  var FAV_SEED = [
    { id: 'f1', type: 'link', title: '微信公众平台：如何高效整理手机相册', source: '微信公众平台', time: '2026-10-20 21:14', body: '手机相册越来越满，其实只需要三步：先按时间归档，再删除重复截图，最后把重要照片备份到电脑或云端。本文整理了 12 个实用小技巧。' },
    { id: 'f2', type: 'image', title: '秋天的第一杯奶茶', source: '文件传输助手', time: '2026-10-18 15:02', body: '图片收藏 · 共 3 张', images: 3 },
    { id: 'f3', type: 'video', title: '周末露营记录', source: '朋友圈', time: '2026-10-15 19:40', body: '视频收藏 · 02:18', duration: '02:18' },
    { id: 'f4', type: 'file', title: '产品需求文档 V2.3.pdf', source: '工作群', time: '2026-10-12 10:26', body: 'PDF · 2.4 MB', size: '2.4 MB' },
    { id: 'f5', type: 'music', title: '晚风（Live）', source: 'QQ 音乐', time: '2026-10-10 22:08', body: '音乐收藏 · 04:12', duration: '04:12' },
    { id: 'f6', type: 'link', title: '一文看懂：手机去水印的正确姿势', source: '豆图公众号', time: '2026-10-08 09:30', body: '处理自己创作或拥有版权的素材是合规的；请勿去除他人作品版权水印、搬运或二次发布。' }
  ];
  var CARD_SEED = [
    { id: 'c1', kind: 'member', name: '星巴克星享卡', level: '金卡', no: '6208 8890 1200 0431', tone: 'green', brand: '☕' },
    { id: 'c2', kind: 'member', name: '麦当劳会员卡', level: '麦乐卡', no: '8812 0043 7712 9006', tone: 'red', brand: 'M' },
    { id: 'c3', kind: 'coupon', name: '瑞幸咖啡 9.9 元券', level: '全场饮品', no: 'LK-2026-88120', tone: 'blue', brand: '☕' },
    { id: 'c4', kind: 'coupon', name: '美团外卖 5 元券', level: '满 20 可用', no: 'MT-5-66201', tone: 'yellow', brand: '券' },
    { id: 'c5', kind: 'gift', name: '京东 E 卡', level: '面值 ¥100', no: 'JD-E-3006 9912', tone: 'purple', brand: 'JD' }
  ];
  var STICKER_SEED = [
    { id: 'p1', name: '默认表情', cover: '😀', tone: 'green', emoji: ['😀', '😁', '😂', '🤣', '😊', '😍', '🤔', '😎', '😭', '😡', '👍', '🙏', '👏', '🎉', '❤️', '💔', '🌹', '☕', '🍺', '🎂', '🐶', '🐱', '🌞', '🌙'], added: true },
    { id: 'p2', name: '小黄脸', cover: '🙂', tone: 'yellow', emoji: ['🙂', '🙃', '😉', '😌', '😔', '😢', '😤', '😱', '🤗', '🤭', '🤫', '🙄', '😴', '🤤', '😷', '🤒'], added: true },
    { id: 'p3', name: '打工人日常', cover: '💼', tone: 'blue', emoji: ['💼', '💻', '📊', '📞', '⏰', '☕', '🍜', '😮‍💨', '🫠', '🙃', '💤', '🧋'], added: false },
    { id: 'p4', name: '萌宠日常', cover: '🐱', tone: 'pink', emoji: ['🐱', '🐶', '🐰', '🐼', '🦊', '🐻', '🐨', '🐯', '🐷', '🐸', '🐵', '🦄'], added: false }
  ];
  var SETTINGS_SEED = {
    youth: false, care: false,
    notify: true, notifyDetail: true, notifySound: true, notifyVibrate: true,
    font: '标准', language: '简体中文', chatBg: '默认'
  };

  function load(key, fallback) {
    try {
      var raw = localStorage.getItem(key);
      if (!raw) return fallback;
      var v = JSON.parse(raw);
      return v == null ? fallback : v;
    } catch (e) { return fallback; }
  }
  function save(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {}
  }
  var favorites = load(LS.fav, FAV_SEED);
  var cards = load(LS.cards, CARD_SEED);
  var stickers = load(LS.stickers, STICKER_SEED);
  var settings = load(LS.settings, SETTINGS_SEED);

  /* --------------------------------------------------------------- helpers */

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function currentRoute() {
    var h = location.hash || '';
    return h.split('?')[0];
  }
  function pad(n) { return n < 10 ? '0' + n : String(n); }
  function nowTime() {
    var d = new Date();
    return pad(d.getHours()) + ':' + pad(d.getMinutes());
  }
  function icon(name, cls) {
    var p = {
      back: '<path d="M15 4 7 12l8 8" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"/>',
      more: '<circle cx="5" cy="12" r="1.9"/><circle cx="12" cy="12" r="1.9"/><circle cx="19" cy="12" r="1.9"/>',
      plus: '<path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>',
      search: '<circle cx="11" cy="11" r="6.5" fill="none" stroke="currentColor" stroke-width="1.9"/><path d="m16 16 4 4" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/>',
      chevron: '<path d="m9 5 7 7-7 7" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>',
      gear: '<circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M12 2.8v2.4M12 18.8v2.4M21.2 12h-2.4M5.2 12H2.8M18.5 5.5l-1.7 1.7M7.2 16.8l-1.7 1.7M18.5 18.5l-1.7-1.7M7.2 7.2 5.5 5.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>',
      link: '<path d="M10.6 13.4a4 4 0 0 1 0-5.7l2.1-2.1a4 4 0 0 1 5.7 5.7l-1.2 1.2M13.4 10.6a4 4 0 0 1 0 5.7l-2.1 2.1a4 4 0 0 1-5.7-5.7l1.2-1.2" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>',
      image: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.4" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="9" cy="10" r="1.6"/><path d="m5 18 5.2-5 3.1 3 2.7-2.6L20 18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>',
      video: '<rect x="3" y="6" width="13" height="12" rx="2.2" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="m16 10 5-3v10l-5-3z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>',
      file: '<path d="M6 3.5h7l5 5v12H6z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M13 3.5V9h5" fill="none" stroke="currentColor" stroke-width="1.8"/>',
      music: '<path d="M9 18V6l10-2v11" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><circle cx="6.5" cy="18" r="2.6" fill="none" stroke="currentColor" stroke-width="1.8"/><circle cx="16.5" cy="15" r="2.6" fill="none" stroke="currentColor" stroke-width="1.8"/>',
      trash: '<path d="M4 7h16M9 7V4.5h6V7M6.5 7l1 13h9l1-13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>'
    }[name] || '';
    return '<svg class="fwm-icon ' + (cls || '') + '" viewBox="0 0 24 24" aria-hidden="true">' + p + '</svg>';
  }
  function typeIcon(type) {
    var map = { link: 'link', image: 'image', video: 'video', file: 'file', music: 'music' };
    var label = { link: '链接', image: '图片', video: '视频', file: '文件', music: '音乐' };
    return '<span class="fwm-fav-icon fwm-tone-' + type + '">' + icon(map[type] || 'link') + '</span>' +
      '<span class="fwm-visually-hidden">' + label[type] + '</span>';
  }
  function statusBar() {
    return '<div class="fwm-status">' +
      '<span class="fwm-time" data-clock>' + nowTime() + '</span>' +
      '<span class="fwm-status-icons">' +
      '<svg viewBox="0 0 18 12" aria-hidden="true"><rect x="0" y="7" width="3" height="5" rx="1"/><rect x="4.4" y="5" width="3" height="7" rx="1"/><rect x="8.8" y="2.7" width="3" height="9.3" rx="1"/><rect x="13.2" y="0" width="3" height="12" rx="1"/></svg>' +
      '<svg viewBox="0 0 17 12" aria-hidden="true"><path d="M8.5 10.6 1.2 4.2a10.6 10.6 0 0 1 14.6 0Z" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M8.5 11.4 3.4 6.8a7 7 0 0 1 10.2 0Z"/></svg>' +
      '<svg viewBox="0 0 25 12" aria-hidden="true"><rect x="0.7" y="0.7" width="20" height="10.6" rx="2.6" fill="none" stroke="currentColor" stroke-width="1.3"/><rect x="2.4" y="2.4" width="13" height="7.2" rx="1.4"/><path d="M22.5 4.2v3.6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>' +
      '</span></div>';
  }
  function header(title, right, backHash) {
    return '<header class="fwm-header">' +
      '<button type="button" class="fwm-back" data-back="' + esc(backHash || '') + '" aria-label="返回">' + icon('back') + '</button>' +
      '<h1>' + esc(title) + '</h1>' +
      '<div class="fwm-header-right">' + (right || '') + '</div>' +
      '</header>';
  }
  function pageShell(title, right, body, backHash) {
    return '<div class="fwm-page">' + statusBar() + header(title, right, backHash) + '<div class="fwm-scroll">' + body + '</div></div>';
  }
  function group(rows, extra) {
    return '<section class="fwm-group ' + (extra || '') + '">' + rows + '</section>';
  }
  function row(opts) {
    var right = opts.right == null ? icon('chevron', 'fwm-chevron') : opts.right;
    var attr = '';
    if (opts.set) attr = ' data-set="' + esc(opts.set) + '"';
    if (opts.act) attr = ' data-act="' + esc(opts.act) + '"';
    if (opts.dialog) attr = ' data-dialog="' + esc(opts.dialog) + '"';
    var cls = 'fwm-row' + (opts.danger ? ' fwm-danger' : '') + (opts.attrClass ? ' ' + opts.attrClass : '');
    return '<button type="button" class="' + cls + '"' + attr + '>' +
      (opts.icon ? '<span class="fwm-row-icon ' + (opts.tone ? 'fwm-tone-' + opts.tone : '') + '">' + opts.icon + '</span>' : '') +
      '<span class="fwm-row-main">' +
      '<span class="fwm-row-title">' + esc(opts.title) + '</span>' +
      (opts.sub ? '<span class="fwm-row-sub">' + esc(opts.sub) + '</span>' : '') +
      '</span>' +
      (opts.value ? '<span class="fwm-row-value">' + esc(opts.value) + '</span>' : '') +
      '<span class="fwm-row-tail">' + right + '</span>' +
      '</button>';
  }
  function switchRow(title, key, on, sub) {
    return '<div class="fwm-row">' +
      '<span class="fwm-row-main"><span class="fwm-row-title">' + esc(title) + '</span>' +
      (sub ? '<span class="fwm-row-sub">' + esc(sub) + '</span>' : '') + '</span>' +
      '<label class="fwm-switch"><input type="checkbox" data-toggle="' + esc(key) + '"' + (on ? ' checked' : '') + '><i></i></label>' +
      '</div>';
  }
  function empty(title, sub) {
    return '<div class="fwm-empty"><div class="fwm-empty-art">🗂️</div><b>' + esc(title) + '</b><p>' + esc(sub || '') + '</p></div>';
  }

  /* -------------------------------------------------------------- favorites */

  var FAV_TABS = [['all', '全部'], ['image', '图片'], ['video', '视频'], ['link', '链接'], ['file', '文件'], ['music', '音乐']];
  var FAV_LABEL = { link: '链接', image: '图片', video: '视频', file: '文件', music: '音乐' };

  function favoriteMatches(item) {
    if (view.favTab !== 'all' && item.type !== view.favTab) return false;
    var q = (view.favQuery || '').trim().toLowerCase();
    if (!q) return true;
    return (item.title + ' ' + item.source + ' ' + FAV_LABEL[item.type]).toLowerCase().indexOf(q) >= 0;
  }
  function favoritesPage() {
    var list = favorites.filter(favoriteMatches);
    var body = '<div class="fwm-search"><span class="fwm-search-icon">' + icon('search') + '</span>' +
      '<input data-fav-search value="' + esc(view.favQuery) + '" placeholder="搜索收藏" autocomplete="off"></div>' +
      '<div class="fwm-tabs fwm-tabs-scroll">' + FAV_TABS.map(function (t) {
        return '<button type="button" data-fav-tab="' + t[0] + '" class="' + (view.favTab === t[0] ? 'is-active' : '') + '">' + t[1] + '</button>';
      }).join('') + '</div>' +
      '<div class="fwm-fav-list" data-fav-list>' + favList(list) + '</div>';
    return pageShell('收藏', '<button type="button" class="fwm-header-btn" data-toast="长按可多选">' + icon('more') + '</button>', body);
  }
  function favList(list) {
    if (!list.length) return empty('暂无收藏', '在聊天或朋友圈里点「收藏」就会出现在这里');
    return list.map(function (it) {
      return '<button type="button" class="fwm-fav-row" data-fav="' + esc(it.id) + '">' +
        typeIcon(it.type) +
        '<span class="fwm-fav-copy"><span class="fwm-fav-title">' + esc(it.title) + '</span>' +
        '<span class="fwm-fav-meta">' + esc(it.source) + ' · ' + esc(it.time) + '</span></span>' +
        icon('chevron', 'fwm-chevron') + '</button>';
    }).join('');
  }
  function favoriteDetail(id) {
    var it = favorites.filter(function (x) { return x.id === id; })[0];
    if (!it) return pageShell('收藏', '', empty('收藏不存在', '可能已被删除'), '#/wechat/me/favorites');
    var visual = '';
    if (it.type === 'image') {
      visual = '<div class="fwm-detail-image">🍂</div>';
    } else if (it.type === 'video') {
      visual = '<div class="fwm-detail-image fwm-detail-video"><span>▶</span><em>' + esc(it.duration || '') + '</em></div>';
    } else if (it.type === 'music') {
      visual = '<div class="fwm-detail-audio"><span>♫</span><b>' + esc(it.title) + '</b><em>' + esc(it.duration || '') + '</em></div>';
    } else if (it.type === 'file') {
      visual = '<div class="fwm-detail-file"><span>📄</span><b>' + esc(it.title) + '</b><em>' + esc(it.size || '') + '</em></div>';
    }
    var body = '<article class="fwm-detail">' +
      '<div class="fwm-detail-meta">' + typeIcon(it.type) + '<span>' + esc(FAV_LABEL[it.type] || '') + ' · ' + esc(it.source) + ' · ' + esc(it.time) + '</span></div>' +
      '<h2>' + esc(it.title) + '</h2>' +
      visual +
      '<p class="fwm-detail-body">' + esc(it.body || '') + '</p>' +
      '<div class="fwm-detail-actions">' +
      '<button type="button" class="fwm-btn fwm-btn-light" data-del-fav="' + esc(it.id) + '">' + icon('trash') + ' 删除</button>' +
      '<button type="button" class="fwm-btn fwm-btn-primary" data-toast="已打开（演示）">打开</button>' +
      '</div></article>';
    return pageShell('收藏', '', body, '#/wechat/me/favorites');
  }

  /* ------------------------------------------------------------------ cards */

  var CARD_TABS = [['member', '会员卡'], ['coupon', '优惠券'], ['gift', '礼品卡']];
  function cardsPage() {
    var list = cards.filter(function (c) { return c.kind === view.cardTab; });
    var body = '<div class="fwm-tabs">' + CARD_TABS.map(function (t) {
      return '<button type="button" data-card-tab="' + t[0] + '" class="' + (view.cardTab === t[0] ? 'is-active' : '') + '">' + t[1] + '</button>';
    }).join('') + '</div>' +
      '<div class="fwm-card-list">' + (list.length ? list.map(function (c) {
        return '<button type="button" class="fwm-card fwm-tone-' + esc(c.tone) + '" data-card="' + esc(c.id) + '">' +
          '<span class="fwm-card-brand">' + esc(c.brand || '') + '</span>' +
          '<span class="fwm-card-copy"><b>' + esc(c.name) + '</b><em>' + esc(c.level || '') + '</em></span>' +
          '<span class="fwm-card-no">' + esc(c.no.replace(/\s+\S{4}$/, ' ****')) + '</span>' +
          '</button>';
      }).join('') : empty('还没有' + ({ member: '会员卡', coupon: '优惠券', gift: '礼品卡' }[view.cardTab] || '卡券'), '领取后会出现在这里')) + '</div>';
    return pageShell('卡包', '<button type="button" class="fwm-header-btn" data-toast="扫一扫添加卡券">' + icon('plus') + '</button>', body);
  }
  function barcode(seed) {
    var bars = '';
    for (var i = 0; i < 46; i++) {
      var w = ((seed.charCodeAt(i % seed.length) + i * 7) % 3) + 1;
      bars += '<i style="width:' + w + 'px"></i>';
    }
    return '<div class="fwm-barcode" aria-hidden="true">' + bars + '</div>';
  }
  function cardDetail(id) {
    var c = cards.filter(function (x) { return x.id === id; })[0];
    if (!c) return pageShell('卡包', '', empty('卡券不存在', '可能已被移除'), '#/wechat/me/cards');
    var body = '<div class="fwm-card-detail fwm-tone-' + esc(c.tone) + '">' +
      '<span class="fwm-card-brand">' + esc(c.brand || '') + '</span>' +
      '<b>' + esc(c.name) + '</b><em>' + esc(c.level || '') + '</em>' +
      '<span class="fwm-card-no">' + esc(c.no) + '</span></div>' +
      '<div class="fwm-barcode-wrap">' + barcode(c.no) + '<p>向商家出示此码</p></div>' +
      '<div class="fwm-group">' +
      row({ title: '卡券详情', value: c.kind === 'member' ? '会员权益' : '使用说明', act: 'toast' }) +
      row({ title: '适用门店', value: '查看', act: 'toast' }) +
      '</div>' +
      '<div class="fwm-detail-actions fwm-detail-actions-pad">' +
      '<button type="button" class="fwm-btn fwm-btn-danger" data-del-card="' + esc(c.id) + '">移除卡券</button>' +
      '</div>';
    return pageShell('卡包', '', body, '#/wechat/me/cards');
  }

  /* --------------------------------------------------------------- stickers */

  function packCover(p, cls) {
    return '<span class="fwm-pack-cover fwm-tone-' + esc(p.tone) + (cls ? ' ' + cls : '') + '">' + esc(p.cover) + '</span>';
  }
  function stickersPage() {
    var mine = stickers.filter(function (p) { return p.added; });
    var store = stickers.filter(function (p) { return !p.added; });
    var list = view.stickerTab === 'mine' ? mine : store;
    var body = '<div class="fwm-tabs">' +
      '<button type="button" data-sticker-tab="mine" class="' + (view.stickerTab === 'mine' ? 'is-active' : '') + '">我的表情</button>' +
      '<button type="button" data-sticker-tab="store" class="' + (view.stickerTab === 'store' ? 'is-active' : '') + '">表情商店</button>' +
      '</div>' +
      '<div class="fwm-pack-list">' + (list.length ? list.map(function (p) {
        return '<button type="button" class="fwm-pack" data-pack="' + esc(p.id) + '">' +
          packCover(p) +
          '<span class="fwm-pack-copy"><b>' + esc(p.name) + '</b><em>' + p.emoji.length + ' 个表情</em></span>' +
          '<span class="fwm-pack-btn">' + (p.added ? '查看' : '添加') + '</span>' +
          '</button>';
      }).join('') : empty('还没有添加表情', '去表情商店挑一套吧')) + '</div>';
    return pageShell('表情', '<button type="button" class="fwm-header-btn" data-toast="表情设置">' + icon('gear') + '</button>', body);
  }
  function packDetail(id) {
    var p = stickers.filter(function (x) { return x.id === id; })[0];
    if (!p) return pageShell('表情', '', empty('表情包不存在', ''), '#/wechat/me/stickers');
    var body = '<div class="fwm-pack-hero">' + packCover(p, 'fwm-pack-cover-lg') +
      '<b>' + esc(p.name) + '</b><em>' + p.emoji.length + ' 个表情</em>' +
      '<button type="button" class="fwm-btn ' + (p.added ? 'fwm-btn-light' : 'fwm-btn-primary') + ' fwm-pack-add" data-toggle-pack="' + esc(p.id) + '">' + (p.added ? '已添加' : '添加到我的表情') + '</button></div>' +
      '<div class="fwm-emoji-grid">' + p.emoji.map(function (e) {
        return '<button type="button" class="fwm-emoji" data-emoji="' + esc(e) + '">' + esc(e) + '</button>';
      }).join('') + '</div>';
    return pageShell('表情', '', body, '#/wechat/me/stickers');
  }

  /* --------------------------------------------------------------- settings */

  function settingsPage() {
    var body = '<div class="fwm-group">' +
      row({ title: '账号与安全', set: 'account', icon: '🔐', tone: 'blue' }) +
      switchRow('青少年模式', 'youth', settings.youth, '未开启') +
      switchRow('关怀模式', 'care', settings.care, '字体更大、色彩更强') +
      '</div><div class="fwm-group">' +
      row({ title: '消息通知', set: 'notify', icon: '🔔', tone: 'green' }) +
      row({ title: '聊天', set: 'chat', icon: '💬', tone: 'green' }) +
      row({ title: '隐私', set: 'privacy', icon: '🛡️', tone: 'blue' }) +
      row({ title: '通用', set: 'general', icon: '⚙️', tone: 'gray' }) +
      '</div><div class="fwm-group">' +
      row({ title: '帮助与反馈', set: 'help', icon: '❓', tone: 'blue' }) +
      row({ title: '关于微信', set: 'about', icon: 'ℹ️', tone: 'green', value: '8.0.50' }) +
      '</div><div class="fwm-group">' +
      row({ title: '切换账号', dialog: 'switch' }) +
      row({ title: '退出登录', dialog: 'logout', danger: true, right: '' }) +
      '</div>';
    return pageShell('设置', '', body);
  }
  function settingsDetail(key) {
    var body = '';
    var title = '设置';
    var back = '#/wechat/me/settings';
    if (key === 'account') {
      title = '账号与安全';
      body = '<div class="fwm-group">' +
        row({ title: '微信号', value: 'link' }) +
        row({ title: '手机号', value: '138****0164' }) +
        row({ title: '微信密码', value: '已设置' }) +
        row({ title: '声音锁', value: '未设置' }) +
        row({ title: '应急联系人', value: '未设置' }) +
        row({ title: '登录设备管理', value: '2 台' }) +
        '</div>';
    } else if (key === 'notify') {
      title = '消息通知';
      body = '<div class="fwm-group">' +
        switchRow('接收新消息通知', 'notify', settings.notify) +
        switchRow('通知显示消息详情', 'notifyDetail', settings.notifyDetail) +
        '</div><div class="fwm-group">' +
        switchRow('声音', 'notifySound', settings.notifySound) +
        switchRow('振动', 'notifyVibrate', settings.notifyVibrate) +
        '</div><p class="fwm-note">关闭后，将不再收到新消息提醒。</p>';
    } else if (key === 'chat') {
      title = '聊天';
      body = '<div class="fwm-group">' +
        row({ title: '聊天记录迁移与备份', value: '未备份', act: 'toast' }) +
        row({ title: '聊天记录备份', value: '未开启', act: 'toast' }) +
        row({ title: '字体大小', value: settings.font, act: 'font' }) +
        row({ title: '聊天背景', value: settings.chatBg, act: 'toast' }) +
        '</div>';
    } else if (key === 'privacy') {
      title = '隐私';
      body = '<div class="fwm-group">' +
        row({ title: '朋友权限', value: '全部', act: 'toast' }) +
        row({ title: '个人信息与权限', act: 'toast' }) +
        row({ title: '添加我的方式', value: '微信号、手机号', act: 'toast' }) +
        row({ title: '朋友圈和视频动态', value: '全部可见', act: 'toast' }) +
        '</div>';
    } else if (key === 'general') {
      title = '通用';
      body = '<div class="fwm-group">' +
        row({ title: '多语言', value: settings.language, act: 'toast' }) +
        row({ title: '字体大小', value: settings.font, act: 'font' }) +
        row({ title: '聊天背景', value: settings.chatBg, act: 'toast' }) +
        '</div><div class="fwm-group">' +
        row({ title: '清空聊天记录', dialog: 'clear', danger: true, right: '' }) +
        '</div>';
    } else if (key === 'help') {
      title = '帮助与反馈';
      body = '<div class="fwm-group">' +
        row({ title: '常见问题', value: '12 个', act: 'toast' }) +
        row({ title: '意见反馈', act: 'toast' }) +
        row({ title: '联系客服', act: 'toast' }) +
        '</div><p class="fwm-note">本软件仅用于处理您自己创作或拥有版权的素材。</p>';
    } else if (key === 'about') {
      title = '关于微信';
      body = '<div class="fwm-about"><span class="fwm-about-logo">💬</span><b>微信</b><em>版本 8.0.50</em>' +
        '<button type="button" class="fwm-btn fwm-btn-primary" data-toast="已是最新版本">检查更新</button></div>' +
        '<div class="fwm-group">' +
        row({ title: '功能介绍', act: 'toast' }) +
        row({ title: '隐私保护指引', act: 'toast' }) +
        '</div>';
    } else {
      return pageShell('设置', '', empty('页面不存在', ''), '#/wechat/me/settings');
    }
    return pageShell(title, '', body, back);
  }

  /* ---------------------------------------------------------------- dialogs */

  var dialog = null;
  function openDialog(opts) {
    dialog = opts;
    var host = document.getElementById(HOST_ID);
    if (!host) return;
    var el = host.querySelector('.fwm-dialog');
    if (!el) {
      el = document.createElement('div');
      el.className = 'fwm-dialog';
      host.appendChild(el);
    }
    el.innerHTML = '<div class="fwm-dialog-mask"></div><div class="fwm-dialog-panel">' +
      '<h3>' + esc(opts.title || '') + '</h3>' +
      (opts.text ? '<p>' + esc(opts.text) + '</p>' : '') +
      '<div class="fwm-dialog-actions">' +
      (opts.cancel === false ? '' : '<button type="button" data-dialog-cancel>' + esc(opts.cancelText || '取消') + '</button>') +
      '<button type="button" data-dialog-ok class="' + (opts.danger ? 'fwm-danger' : 'fwm-primary') + '">' + esc(opts.okText || '确定') + '</button>' +
      '</div></div>';
    el.classList.add('is-open');
  }
  function closeDialog() {
    var host = document.getElementById(HOST_ID);
    var el = host && host.querySelector('.fwm-dialog');
    if (el) el.classList.remove('is-open');
    dialog = null;
  }
  var toastTimer = 0;
  function toast(text) {
    var host = document.getElementById(HOST_ID);
    if (!host) return;
    var el = host.querySelector('.fwm-toast');
    if (!el) {
      el = document.createElement('div');
      el.className = 'fwm-toast';
      host.appendChild(el);
    }
    el.textContent = text;
    el.classList.add('is-open');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove('is-open'); }, 1600);
  }

  /* ------------------------------------------------------------------ pages */

  function page(kind, id) {
    if (kind === 'favorites') return id ? favoriteDetail(id) : favoritesPage();
    if (kind === 'cards') return id ? cardDetail(id) : cardsPage();
    if (kind === 'stickers') return id ? packDetail(id) : stickersPage();
    if (kind === 'settings') return id ? settingsDetail(id) : settingsPage();
    return pageShell('收藏', '', empty('页面不存在', ''), '#/wechat/my');
  }

  function render() {
    if (!host) {
      host = document.createElement('div');
      host.id = HOST_ID;
      document.documentElement.appendChild(host);
    }
    var r = currentRoute();
    var isMe = r.indexOf('#/wechat/me/') === 0;
    var root = document.getElementById('root');
    if (!isMe) {
      host.style.display = 'none';
      if (root) root.style.display = '';
      return;
    }
    if (root) root.style.display = 'none';
    host.style.display = 'block';
    var seg = r.replace(/^#/, '').split('/').filter(Boolean);
    host.innerHTML = page(seg[2] || '', seg[3] || '');
    var sc = host.querySelector('.fwm-scroll');
    if (sc) sc.scrollTop = 0;
    var t = host.querySelector('[data-clock]');
    if (t) t.textContent = nowTime();
  }

  /* --------------------------------------------------------------- handlers */

  function nav(hash) { if (location.hash === hash) render(); else location.hash = hash; }
  function back(fallback) {
    if (history.length > 1) history.back();
    else nav(fallback || '#/wechat/my');
  }

  function bindOnce() {
    document.addEventListener('click', function (e) {
      /* 1) "我" page rows: the bundled app renders them without handlers. */
      if (currentRoute() === '#/wechat/my') {
        var n = e.target;
        for (var i = 0; n && i < 9; i++, n = n.parentElement) {
          var t = (n.textContent || '').trim();
          var map = { '收藏': 'favorites', '朋友圈': 'moments', '卡包': 'cards', '表情': 'stickers', '设置': 'settings' };
          if (map[t]) {
            var rect = n.getBoundingClientRect();
            if (rect.height >= 36 && rect.height <= 96) {
              e.preventDefault();
              e.stopPropagation();
              if (t === '朋友圈') nav('#/wechat/moments');
              else nav('#/wechat/me/' + map[t]);
              return;
            }
          }
        }
      }

      var el = e.target.closest ? e.target.closest('[data-back],[data-fav],[data-card],[data-pack],[data-set],[data-dialog],[data-del-fav],[data-del-card],[data-toggle-pack],[data-emoji],[data-toast],[data-fav-tab],[data-card-tab],[data-sticker-tab],[data-act],[data-dialog-ok],[data-dialog-cancel],[data-copy]') : null;
      if (!el) return;

      var r = currentRoute();
      if (el.hasAttribute('data-back')) {
        var fb = el.getAttribute('data-back') || '';
        e.preventDefault();
        back(fb);
        return;
      }
      if (el.hasAttribute('data-fav')) { e.preventDefault(); nav('#/wechat/me/favorites/' + el.getAttribute('data-fav')); return; }
      if (el.hasAttribute('data-card')) { e.preventDefault(); nav('#/wechat/me/cards/' + el.getAttribute('data-card')); return; }
      if (el.hasAttribute('data-pack')) { e.preventDefault(); nav('#/wechat/me/stickers/' + el.getAttribute('data-pack')); return; }
      if (el.hasAttribute('data-set')) { e.preventDefault(); nav('#/wechat/me/settings/' + el.getAttribute('data-set')); return; }
      if (el.hasAttribute('data-fav-tab')) { e.preventDefault(); view.favTab = el.getAttribute('data-fav-tab'); render(); return; }
      if (el.hasAttribute('data-card-tab')) { e.preventDefault(); view.cardTab = el.getAttribute('data-card-tab'); render(); return; }
      if (el.hasAttribute('data-sticker-tab')) { e.preventDefault(); view.stickerTab = el.getAttribute('data-sticker-tab'); render(); return; }
      if (el.hasAttribute('data-toast')) { e.preventDefault(); toast(el.getAttribute('data-toast')); return; }
      if (el.hasAttribute('data-copy')) {
        e.preventDefault();
        var text = el.getAttribute('data-copy');
        try { navigator.clipboard.writeText(text); toast('已复制'); } catch (err) { toast(text); }
        return;
      }
      if (el.hasAttribute('data-emoji')) {
        e.preventDefault();
        var em = el.getAttribute('data-emoji');
        openDialog({ title: '表情预览', text: em, cancel: false, okText: '添加到表情', onOk: function () { toast('已添加到表情'); } });
        return;
      }
      if (el.hasAttribute('data-toggle-pack')) {
        e.preventDefault();
        var pid = el.getAttribute('data-toggle-pack');
        stickers = stickers.map(function (p) { return p.id === pid ? Object.assign({}, p, { added: !p.added }) : p; });
        save(LS.stickers, stickers);
        render();
        toast(stickers.filter(function (p) { return p.id === pid; })[0].added ? '已添加' : '已移除');
        return;
      }
      if (el.hasAttribute('data-del-fav')) {
        e.preventDefault();
        var fid = el.getAttribute('data-del-fav');
        openDialog({ title: '删除收藏', text: '删除后无法恢复。', danger: true, okText: '删除', onOk: function () {
          favorites = favorites.filter(function (x) { return x.id !== fid; });
          save(LS.fav, favorites);
          nav('#/wechat/me/favorites');
          toast('已删除');
        }});
        return;
      }
      if (el.hasAttribute('data-del-card')) {
        e.preventDefault();
        var cid = el.getAttribute('data-del-card');
        openDialog({ title: '移除卡券', text: '移除后可在卡包重新添加。', danger: true, okText: '移除', onOk: function () {
          cards = cards.filter(function (x) { return x.id !== cid; });
          save(LS.cards, cards);
          nav('#/wechat/me/cards');
          toast('已移除');
        }});
        return;
      }
      if (el.hasAttribute('data-dialog')) {
        e.preventDefault();
        var kind = el.getAttribute('data-dialog');
        if (kind === 'switch') {
          openDialog({ title: '切换账号', text: '当前账号：link\n添加账号后可在两个账号之间切换。', okText: '添加账号', onOk: function () { toast('演示环境暂不支持添加账号'); } });
        } else if (kind === 'logout') {
          openDialog({ title: '退出登录', text: '退出后，本机将不再接收该账号的新消息。', danger: true, okText: '退出', onOk: function () {
            toast('已退出登录（演示）');
          }});
        } else if (kind === 'clear') {
          openDialog({ title: '清空聊天记录', text: '清空后不可恢复。', danger: true, okText: '清空', onOk: function () { toast('已清空（演示）'); } });
        }
        return;
      }
      if (el.hasAttribute('data-act')) {
        e.preventDefault();
        var act = el.getAttribute('data-act');
        if (act === 'font') {
          openDialog({ title: '字体大小', text: '标准 / 大 / 超大（演示）', okText: '标准', onOk: function () { settings.font = '标准'; save(LS.settings, settings); render(); } });
        } else {
          toast('演示环境');
        }
        return;
      }
      if (el.hasAttribute('data-dialog-ok')) {
        e.preventDefault();
        var d = dialog;
        closeDialog();
        if (d && d.onOk) d.onOk();
        return;
      }
      if (el.hasAttribute('data-dialog-cancel')) {
        e.preventDefault();
        closeDialog();
      }
    }, true);

    document.addEventListener('change', function (e) {
      var t = e.target;
      if (!t || !t.hasAttribute || !t.hasAttribute('data-toggle')) return;
      var key = t.getAttribute('data-toggle');
      settings[key] = !!t.checked;
      save(LS.settings, settings);
      if (key === 'notify') toast(t.checked ? '已开启消息通知' : '已关闭消息通知');
    });

    document.addEventListener('input', function (e) {
      var t = e.target;
      if (!t || !t.hasAttribute || !t.hasAttribute('data-fav-search')) return;
      view.favQuery = t.value || '';
      var list = favorites.filter(favoriteMatches);
      var box = document.querySelector('[data-fav-list]');
      if (box) box.innerHTML = favList(list);
    });

    window.addEventListener('hashchange', render);
    window.addEventListener('popstate', render);
  }

  function start() {
    bindOnce();
    render();
    setInterval(function () {
      var t = document.querySelector('#fw-me-pages-host [data-clock]');
      if (t) t.textContent = nowTime();
    }, 30000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
