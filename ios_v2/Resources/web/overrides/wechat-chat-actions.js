(() => {
  'use strict';

  /* ---------------------------------------------------------------------------
     微信「切换账号」真正换数据的一步，必须放在这里 —— 本文件是新页面里最早执行的
     脚本，比应用的 bundle 还早，所以此刻改 localStorage 就是"应用启动前"。

     为什么不在点击时直接换：点了切换之后旧页面在 location.reload() 生效前还会跑一
     小会儿，它内存里的旧账号数据随时可能回写 localStorage，把刚换好的数据盖回去。
     所以旧页面只写一个 pendingRestore 标记，实际搬运放到下面这段里做：
       1. 把旧账号离场前最后的状态打包进 blobs[current]；
       2. 清空本机数据，写入目标账号的包（新账号的包是空的 ⇒ 应用自己生成一个全新世界）；
       3. current 指向目标账号，新账号再挂上 pendingSetup。
     --------------------------------------------------------------------------- */
  (function applyPendingAccountSwapOnBoot() {
    const KEY = 'fake-world.wechat.accounts.v1';
    const AVATAR_CACHE_PREFIX = 'fake-world.wechat.avatar-cache.';
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return;
      const registry = JSON.parse(raw);
      if (!registry || typeof registry !== 'object') return;
      const target = registry.pendingRestore;
      if (!target) return;
      if (!registry.blobs || typeof registry.blobs !== 'object') registry.blobs = {};
      const keys = [];
      for (let i = 0; i < localStorage.length; i += 1) keys.push(localStorage.key(i));
      const managed = keys.filter(key => key && key !== KEY && key.indexOf(AVATAR_CACHE_PREFIX) !== 0);
      const snapshot = {};
      for (const key of managed) snapshot[key] = localStorage.getItem(key);
      registry.blobs[registry.current] = snapshot;
      for (const key of managed) localStorage.removeItem(key);
      const blob = registry.blobs[target];
      if (blob && typeof blob === 'object') {
        for (const key of Object.keys(blob)) {
          if (key === KEY) continue;
          try { localStorage.setItem(key, blob[key]); } catch (_) { /* 单条写不进去就跳过 */ }
        }
      }
      registry.current = target;
      delete registry.pendingRestore;
      if (registry.pendingNew === target) {
        registry.pendingSetup = target;
        delete registry.pendingNew;
      }
      localStorage.setItem(KEY, JSON.stringify(registry));
    } catch (_) { /* 出问题就按原样启动，绝不破坏已有数据 */ }
  })();

  const STORE_KEY = 'fake-world.wechat.chat-actions.v1';
  const HOLD_MS = 500;
  const PLUS_LABELS = ['发起群聊', '添加朋友', '扫一扫', '收付款'];
  const CHAT_LABELS = ['标为未读', '置顶该聊天', '不显示该聊天', '删除该聊天'];
  const CONTACT_FALLBACKS = [
    { name: '星之笨比', avatar: './assets/user-avatar-10-ZKj7tiz_.jpg', starred: false },
    { name: '大叔', avatar: './assets/user-avatar-4-Cmbgep_s.jpg', starred: false },
    { name: '小绿帽', avatar: './assets/user-avatar-5-DMigR-cn.jpg', starred: false },
  ];
  const ICONS = [
    './assets/wechat-plus-icons/group-chat.png',
    './assets/wechat-plus-icons/add-friend.png',
    './assets/wechat-plus-icons/scan.png',
    './assets/wechat-plus-icons/payment.png'
  ];

  const BATCH_HANDLE_SEEDS = ["今天也要开心","橘子海","晚风知我意","山野有雾","小岛来信","月亮失约了","云朵收藏家","把日子过慢","汽水半糖","在逃小熊","春日限定","听风看海","小岛晴天","不知名的某某","月光便利店","小狗困了","正在充电中","一颗椰子","青柠气泡","风吹麦浪","半熟芝士","桃桃乌龙","今天不熬夜","宇宙漫游指南","睡不醒星球","山茶与海","快乐研究所","小猫打盹","月光维修站","窗边听雨","普通小孩","心动贩卖机","向阳而生","慢慢喜欢你","一页山河","海盐冰淇淋","晚风邮差","人间观察员","云边小卖部","夏天不散场","月亮邮递员","软糖研究员","日落收藏家","晴天放映机","小熊在发呆","请叫我阿喵","风里有点甜","清醒梦境","平凡浪漫","想去看日出","快乐不打烊","桃子汽水","听雨的人","南方小土豆","今天也很哇塞","保持可爱","海边拾贝壳","星星掉进海里","不吃香菜","小小探险家","喜欢散步","柠檬不萌","有空一起玩","一直在路上","慢热选手","日子甜一点","一只糯米团","糯叽叽本人","小城故事多","向晚而行","风里有诗","有猫就幸福","蓝莓味晚风","认真生活中","周末逃跑计划","晚霞贩卖机","小熊软糖","一颗小行星","晴天见面","今天喝热牛奶","山水有相逢","落日与晚风","自在如风","温柔收藏家","别叫我起床","可乐要加冰","一只小海豹","云朵上的猫","遇见好天气","有趣的灵魂","做个好梦","向快乐出发","月亮不打烊","风也温柔","桃子味汽水","浪漫收藏家","打翻了晚霞","银河散步中","小狗观察日记"];

  let state = loadState();

  // 旧版演示数据把第一条会话默认置顶（isPinned:true），导致“老婆”总在最上面。
  // 只做一次迁移：清掉持久化里的默认 isPinned，并清掉 state 里的 pinnedAt。
  try {
    const PIN_CLEARED_KEY = 'fake-world.wechat.default-pin-cleared.v1';
    if (!localStorage.getItem(PIN_CLEARED_KEY)) {
      for (let i = 0; i < localStorage.length; i += 1) {
        const key = localStorage.key(i);
        if (!key) continue;
        const value = localStorage.getItem(key) || '';
        if (value.indexOf('"isPinned":true') !== -1) {
          localStorage.setItem(key, value.replace(/"isPinned":true/g, '"isPinned":false'));
        }
      }
      if (state.chats && typeof state.chats === 'object') {
        Object.keys(state.chats).forEach(key => {
          if (state.chats[key] && state.chats[key].pinnedAt) state.chats[key].pinnedAt = null;
        });
      }
      localStorage.setItem(PIN_CLEARED_KEY, '1');
      try { saveState(); } catch (_) {}
    }
  } catch (_) { /* localStorage 不可用时忽略 */ }
  // 2026-10-11: random friend nicknames come from the 随机昵称网名[百万昵称库] API the user
  // asked for (https://api.aa1.cn/doc/sjwm.html -> GET https://cn.apihz.cn/api/zici/sjwm.php).
  // It answers {"code":200,"msg":"网名"} and sends Access-Control-Allow-Origin: *, so the
  // page can call it directly. This is the only nickname source in the app.
  const NICKNAME_API = 'https://cn.apihz.cn/api/zici/sjwm.php';
  const NICKNAME_API_QUERY = 'id=88888888&key=88888888';
  // The shared demo id/key is rate limited (the API answers code 400 「调用频次过快」), so the
  // fetcher runs two workers with a gap and backs off instead of hammering the endpoint.
  const NICKNAME_REQUEST_GAP = 120;
  const NICKNAME_BATCH_DEADLINE = 6000;
  function nicknameDelay(ms) { return new Promise(resolve => setTimeout(resolve, Math.max(0, ms))); }
  async function fetchRandomNicknames(count, onProgress) {
    const want = Math.max(1, Math.min(200, Number(count) || 1));
    const names = [];
    const seen = new Set();
    const deadline = Date.now() + NICKNAME_BATCH_DEADLINE;
    let backoffUntil = 0;
    const requestOne = async () => {
      const wait = backoffUntil - Date.now();
      if (wait > 0) await nicknameDelay(wait);
      let controller = null;
      let timer = 0;
      try {
        controller = typeof AbortController === 'function' ? new AbortController() : null;
        if (controller) timer = setTimeout(() => controller.abort(), 4000);
        const response = await fetch(NICKNAME_API + '?' + NICKNAME_API_QUERY + '&t=' + Date.now() + Math.random().toString(36).slice(2), {
          cache: 'no-store',
          signal: controller ? controller.signal : undefined,
        });
        if (!response.ok) return '';
        const payload = await response.json();
        if (Number(payload && payload.code) !== 200) {
          // 400 = 调用频次过快 on the shared key: pause every worker, then retry.
          if (Number(payload && payload.code) === 400) backoffUntil = Date.now() + 1500;
          return '';
        }
        return String(payload && payload.msg ? payload.msg : '').trim();
      } catch (_) {
        return '';
      } finally {
        if (timer) clearTimeout(timer);
      }
    };
    const worker = async () => {
      let misses = 0;
      while (names.length < want && misses < 6 && Date.now() < deadline) {
        const name = await requestOne();
        if (!name) { misses += 1; await nicknameDelay(NICKNAME_REQUEST_GAP); continue; }
        misses = 0;
        if (!seen.has(name)) { seen.add(name); names.push(name); if (typeof onProgress === 'function') onProgress(names.length); }
        await nicknameDelay(NICKNAME_REQUEST_GAP);
      }
    };
    await Promise.all(Array.from({ length: Math.min(6, want) }, worker));
    // 公共 ID/KEY 被限流时不要继续卡住：用随包的昵称池即时补齐。
    const pool = (window.__fwNamePools && window.__fwNamePools.nicknames) || [];
    while (names.length < want && pool.length) {
      const name = String(pool[Math.floor(Math.random() * pool.length)] || '').trim();
      if (name && !seen.has(name)) { seen.add(name); names.push(name); }
    }
    return names;
  }
  let activePress = null;
  let suppressNextChatGestureClick = false;
  let suppressClickUntil = 0;
  let suppressClickRow = null;
  let suppressClickPoint = null;
  let overlay = null;
  let popupKind = null;
  // Android WebView dispatches a compatibility mouse click after a touch tap. When a
  // dialog closes on that tap, the extra click re-hit-tests and lands on whatever was
  // underneath (e.g. a chat row). This timestamp lets us swallow that one stray click;
  // opening a new overlay immediately after clears it so picker flows are unaffected.
  let overlayClickGuardUntil = 0;
  let observer = null;
  let contextTarget = null;
  let applyQueued = false;
  let userFontScale = 1;
  let selfAvatarSyncTimer = null;
  let contactPress = null;
  let suppressNextContactClick = false;
  let suppressContactClickTimer = 0;
  let recentContactGesture = null;
  let keyboardBaselineHeight = 0;
  let keyboardNativeInset = 0;
  let keyboardWasOpen = false;
  // Set while a local conversation is open so the native long-press bridge can
  // reach the message menu owned by that conversation.
  let openMessageMenuForRow = null;

  // Keep both chat implementations above the Android IME. On devices where
  // edge-to-edge prevents the WebView from resizing, visualViewport/native
  // insets provide the missing keyboard offset; when adjustResize works, the
  // offset remains zero so the original layout is unchanged.
  function readKeyboardLayoutHeight() {
    return Math.max(1, Number(document.documentElement?.clientHeight || 0), Number(window.innerHeight || 0));
  }
  function syncKeyboardViewport() {
    const layoutHeight = readKeyboardLayoutHeight();
    const viewport = window.visualViewport;
    const visualHeight = Math.max(1, Number(viewport?.height || layoutHeight));
    if (!keyboardBaselineHeight) keyboardBaselineHeight = layoutHeight;
    const layoutShrink = Math.max(0, keyboardBaselineHeight - layoutHeight);
    const visualShrink = Math.max(0, keyboardBaselineHeight - visualHeight - Number(viewport?.offsetTop || 0));
    const open = keyboardNativeInset > 80 || layoutShrink > 80 || visualShrink > 80;
    // If Android resized the WebView, the composer already follows the IME;
    // only apply a manual offset for the overlay/edge-to-edge case.
    const manualOffset = layoutShrink > 80 ? 0 : Math.max(visualShrink, keyboardNativeInset);
    document.documentElement.style.setProperty('--fw-keyboard-height', Math.round(manualOffset) + 'px');
    document.documentElement.style.setProperty('--fw-keyboard-offset', Math.round(manualOffset) + 'px');
    document.documentElement.classList.toggle('fw-keyboard-open', open);
    document.body?.classList.toggle('fw-keyboard-open', open);
    if (!open) keyboardBaselineHeight = layoutHeight;
    keyboardWasOpen = open;
  }
  window.__fwSetKeyboardInset = value => {
    keyboardNativeInset = Math.max(0, Number(value) || 0);
    syncKeyboardViewport();
  };
  window.addEventListener('resize', syncKeyboardViewport, { passive: true });
  window.visualViewport?.addEventListener('resize', syncKeyboardViewport, { passive: true });
  window.visualViewport?.addEventListener('scroll', syncKeyboardViewport, { passive: true });
  setTimeout(syncKeyboardViewport, 0);

  function loadState() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORE_KEY) || '{}');
      if (parsed && parsed.chats && typeof parsed.chats === 'object') return parsed;
    } catch (_) { /* Start with clean local interaction state. */ }
    return { chats: {} };
  }

  // Mirror locally-created contacts into fake-world's native persisted allProfiles atom.
  // The contacts screen, pinyin grouping, profile pages, and right-side index all read this atom.
  const NATIVE_PROFILES_KEY = 'allProfiles';
  function nativeProfileFromFriend(friend, previousById) {
    const id = String(friend?.id || '');
    const nickname = String(friend?.name || friend?.realName || '').trim();
    if (!id || !nickname) return null;
    let previous = previousById?.get(id);
    if (!previous && !previousById) {
      try {
        const parsed = JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY) || '[]');
        if (Array.isArray(parsed)) previous = parsed.find(profile => String(profile?.id) === id);
      } catch (_) { /* initialize below */ }
    }
    previous = previous || {};
    return {
      ...previous, id, nickname,
      remark: String(friend?.remark || '').trim() || undefined,
      avatarInfo: friend?.avatar || previous.avatarInfo || '',
      wechat: friend?.wechatId || previous.wechat || id,
      gender: friend?.gender || previous.gender || 'unknown',
      privacy: previous.privacy || 'all',
      thumbnailInfo: Array.isArray(previous.thumbnailInfo) ? previous.thumbnailInfo : [],
      momentsBackgroundInfo: previous.momentsBackgroundInfo || '',
      momentsBackgroundLike: !!previous.momentsBackgroundLike,
      momentsPrivacy: previous.momentsPrivacy || 'all',
      description: friend?.signature || previous.description || '',
      area: friend?.region || previous.area || '',
      isStarred: !!friend?.starred,
    };
  }
  let nativeSyncQueued = false;
  function syncNativeAddressBook() {
    try {
      const friends = (Array.isArray(state.contacts) ? state.contacts : [])
        .filter(friend => friend?.id && friend.name && friend.name !== '老婆' && friend.remark !== '老婆' && !friend.payAssistant);
      // Parse the saved address book once. Parsing it once per friend made startup
      // reconciliation quadratic for large generated friend lists and could stall taps.
      let savedProfiles = [];
      try {
        const parsed = JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY) || '[]');
        if (Array.isArray(parsed)) savedProfiles = parsed;
      } catch (_) { /* initialize below */ }
      const previousById = new Map(savedProfiles.filter(profile => profile?.id != null).map(profile => [String(profile.id), profile]));
      const profiles = friends.map(friend => nativeProfileFromFriend(friend, previousById)).filter(Boolean);
      if (typeof window.__fwSyncNativeProfiles === 'function') {
        nativeSyncQueued = false;
        return window.__fwSyncNativeProfiles(profiles);
      }
      nativeSyncQueued = true;
      return false;
    } catch (_) { nativeSyncQueued = true; return false; }
  }
  window.addEventListener('fw:native-profiles-ready', syncNativeAddressBook);
  window.addEventListener('fw:contacts-updated', () => { syncNativeAddressBook(); setTimeout(syncNativeAddressBook, 0); });  function saveState() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(state)); return true; } catch (_) { return false; }
  }

  function getRows() {
    const list = document.querySelector('.wechat-dialogue-list');
    if (!list) return [];
    return [...list.querySelectorAll('[class*="chat-item_"], .fw-local-friend-row')].filter(row => getChatInfo(row));
  }

 function getChatInfo(row) {
    if (!row) return null;
    const img = row.querySelector(':scope > img, :scope > div img, img');
    const nameNode = row.querySelector('.fw-local-friend-copy strong') || row.querySelector('span.font-normal') || row.querySelector(':scope > div:nth-child(2) > div:first-child > span:first-child') || [...row.querySelectorAll('span,div')].find(el => el.children.length === 0 && el.textContent.trim() && el.textContent.trim().length < 40);
    const name = nameNode?.textContent?.trim();
    // 2026-10-10: group rows replace their <img> with the composed .fw-group-avatar grid,
    // so the avatar must not be required (otherwise long-press / pin / hide / unread all
    // silently skip group chats).
    const groupCell = row.querySelector('.fw-group-avatar__cell');
    const cellBackground = groupCell?.style?.backgroundImage || '';
    const avatar = img?.getAttribute('src')
      || (cellBackground ? cellBackground.replace(/^url\(["']?/, '').replace(/["']?\)$/, '') : '')
      || (row.querySelector('.fw-group-avatar') ? 'group' : '');
    if (!name) return null;
    return { key: name, name, avatar };
  }

  // Photos inside a conversation: gallery sends use data URIs, batch-generated
  // histories use remote URLs (source.unsplash.com, per the requested API format).
  const IMAGE_SOURCE_PATTERN = /^https?:\/\/(?:[^/]+\.)?(?:unsplash\.com|picsum\.photos)\//i;
  const IMAGE_EXT_PATTERN = /^https?:\/\/[^\s]+\.(?:jpe?g|png|gif|webp|bmp)(?:[?#]|$)/i;
  function isImageSource(value) {
    return typeof value === 'string'
      && (/^data:image\//i.test(value) || IMAGE_SOURCE_PATTERN.test(value) || IMAGE_EXT_PATTERN.test(value));
  }
  // Unsplash retired source.unsplash.com (it answers HTTP 503 now), so a failed
  // photo falls back to a live random-photo host before degrading to a [图片] bubble.
  function fallbackPhotoUrl(source) {
    const match = String(source || '').match(/(\d{2,4})x(\d{2,4})/);
    const width = match ? match[1] : '800';
    const height = match ? match[2] : '600';
    return 'https://picsum.photos/' + width + '/' + height + '?random=' + Math.random().toString(36).slice(2);
  }

  function chatRecord(key) {
    if (!state.chats[key]) {
      const legacy = Object.keys(state.chats).find(item => item === key || item.startsWith(key + '::'));
      if (legacy && legacy !== key) { state.chats[key] = state.chats[legacy]; delete state.chats[legacy]; saveState(); }
    }
    if (!state.chats[key]) state.chats[key] = {};
    return state.chats[key];
  }

  // A stored chat message is sent by "我" unless it was swiped over to the friend.
  // Messages saved before this field existed have no `side`, so they stay outgoing.
  function messageSide(message) {
    return message && message.side === 'incoming' ? 'incoming' : 'outgoing';
  }

  // Chat transfers are mirrored into the wallet bills owned by wechat-pay-pages.js.
  function pushTransferBill(message, signedAmount, label, friend) {
    if (!message || !window.__fwAddWalletBill) return;
    try {
      const name = String(label || (friend && (friend.remark || friend.name)) || '好友');
      const avatar = String((friend && getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar)) || '');
      const value = Math.round(Number(signedAmount) * 100) / 100;
      message.billId = window.__fwAddWalletBill({
        title: (value < 0 ? '转账-转给' : '转账-来自') + name,
        friendName: name,
        avatar,
        amount: value,
        createdAt: message.createdAt
      }) || '';
    } catch (_) { /* wallet bills are optional */ }
  }

  // Keep the mirrored bill in step when a transfer message is swiped to the other speaker.
  function syncTransferBill(message) {
    if (!message || message.type !== 'transfer' || !message.billId || !window.__fwSyncWalletBill) return;
    try {
      const value = Math.abs(Number(message.amount) || 0);
      const incoming = messageSide(message) === 'incoming';
      const patch = { amount: incoming ? value : -value, status: incoming ? '已收钱' : '已转账' };
      const name = String(message.friendName || '').trim();
      if (name) patch.title = (incoming ? '转账-来自' : '转账-转给') + name;
      window.__fwSyncWalletBill(message.billId, patch);
    } catch (_) { /* wallet bills are optional */ }
  }

  function unreadNumber(value) {
    const match = String(value || '').trim().match(/^(\d{1,3})(\+)?$/);
    if (!match) return 0;
    return Math.max(0, Math.min(999, Number(match[1]) + (match[2] ? 1 : 0)));
  }
  function isRedColor(value) {
    const m = String(value || '').match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
    return !!m && Number(m[1]) > 180 && Number(m[2]) < 130 && Number(m[3]) < 130;
  }
  function getNativeUnreadNodes(row) {
    if (!row) return [];
    const selectors = [
      '[class*="bg-red"]', '[class*="red-"]', '[class*="badge"]',
      '[class*="unread"]', '[class*="count"]', '[class*="notify"]',
      '[class*="notice"]'
    ];
    const candidates = new Set([
      ...row.querySelectorAll('*'),
      ...row.querySelectorAll(selectors.join(','))
    ]);
    return [...candidates].filter(node => {
      if (node.closest('.fw-unread-badge')) return false;
      const n = node.children.length === 0 ? unreadNumber(node.textContent) : 0;
      if (!n) return false;
      const cls = typeof node.className === 'string' ? node.className.toLowerCase() : '';
      const style = getComputedStyle(node);
      return /(badge|unread|count|notify|notice|red)/.test(cls)
        || isRedColor(style.backgroundColor)
        || isRedColor(style.color);
    });
  }
  function readUnreadCount(row) {
    return getNativeUnreadNodes(row)
      .filter(node => !node.classList.contains('fw-native-unread-hidden'))
      .reduce((max, node) => Math.max(max, unreadNumber(node.textContent)), 0);
  }
  function hideNativeUnreadNodes(row) {
    if (!row) return;
    getNativeUnreadNodes(row).forEach(node => {
      if (node.closest('.fw-unread-badge')) return;
      node.classList.add('fw-native-unread-hidden');
      node.style.setProperty('display', 'none', 'important');
      node.style.setProperty('visibility', 'hidden', 'important');
      node.setAttribute('aria-hidden', 'true');
    });
  }
  function placeUnreadBadge(badge, img) {
    const row = badge?.closest('.wechat-dialogue-list > *, .fw-local-friend-row');
    // Group rows have no <img>; their avatar is the composed .fw-group-avatar grid.
    const anchor = img || row?.querySelector('.fw-group-avatar');
    const imageRect = anchor?.getBoundingClientRect();
    const rowRect = row?.getBoundingClientRect();
    if (!imageRect || !rowRect || imageRect.width <= 0 || imageRect.height <= 0 || rowRect.width <= 0 || rowRect.height <= 0) {
      badge.hidden = true;
      return;
    }
    badge.hidden = false;
    // Anchor to the chat row so nested scrolling moves the badge with its avatar.
    if (getComputedStyle(row).position === 'static') row.style.position = 'relative';
    badge.style.left = `${Math.round(imageRect.right - rowRect.left - 7)}px`;
    badge.style.top = `${Math.round(imageRect.top - rowRect.top - 7)}px`;
  }
  function syncUnreadBadges() {
    document.querySelectorAll('.fw-unread-badge').forEach(badge => {
      const row = badge.closest('.wechat-dialogue-list > *, .fw-local-friend-row');
      placeUnreadBadge(badge, row?.querySelector('img'));
    });
  }
  function removeUnreadBadge(key) {
    if (!key) return;
    document.querySelectorAll('.fw-unread-badge').forEach(badge => {
      if (badge.getAttribute('data-fw-chat-key') === key) badge.remove();
    });
  }

  function renderUnreadBadge(row, count) {
    const info = getChatInfo(row);
    const key = row?.dataset.fwChatKey || info?.key || '';
    const matches = [...document.querySelectorAll('.fw-unread-badge')]
      .filter(item => item.getAttribute('data-fw-chat-key') === key);
    if (count <= 0) {
      matches.forEach(item => item.remove());
      return;
    }

    // Group rows render a composed .fw-group-avatar instead of an <img>.
    const anchor = row?.querySelector('img') || row?.querySelector('.fw-group-avatar');
    if (!anchor) {
      matches.forEach(item => item.remove());
      return;
    }
    const badge = matches.find(item => item.closest('.wechat-dialogue-list > *, .fw-local-friend-row') === row) || matches.shift() || document.createElement('span');
    matches.filter(item => item !== badge).forEach(item => item.remove());
    badge.className = 'fw-unread-badge';
    badge.setAttribute('data-fw-badge', '1');
    badge.setAttribute('data-fw-chat-key', key);
    badge.setAttribute('aria-label', String(count) + '条未读消息');
    const label = count > 99 ? '99+' : String(count);
    if (badge.textContent !== label) badge.textContent = label;
    // Keep the badge inside its own row so nested scrolling moves it with the avatar.
    if (badge.parentElement !== row) row.appendChild(badge);
    placeUnreadBadge(badge, anchor);
  }
  function findWechatTabLabel() {
    const labels = [...document.querySelectorAll('*')].filter(el => {
      if (el.children.length || el.textContent.trim() !== '微信') return false;
      const rect = el.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0 && rect.top > innerHeight * 0.68 && rect.bottom <= innerHeight + 12;
    });
    return labels.sort((a, b) => {
      const ar = a.getBoundingClientRect(), br = b.getBoundingClientRect();
      return (ar.width * ar.height) - (br.width * br.height);
    })[0] || null;
  }
  function updateWechatTabBadge(total) {
    const badges = [...document.querySelectorAll('.fw-tab-unread-badge')];
    const badge = badges.shift() || document.createElement('span');
    badges.forEach(item => item.remove());
    const label = findWechatTabLabel();
    const navItem = document.querySelector('.wechat-bottom-navbar__item:first-child')
      || label?.closest('.wechat-bottom-navbar__item')
      || label?.parentElement;
    if (!navItem) { badge.remove(); return; }
    // The bundled page includes a static tab count. Hide that source and render
    // a single badge from the current sum of per-chat unread counts.
    getNativeUnreadNodes(navItem).forEach(node => {
      if (node.closest('.fw-tab-unread-badge')) return;
      node.classList.add('fw-native-unread-hidden');
      node.style.setProperty('display', 'none', 'important');
      node.style.setProperty('visibility', 'hidden', 'important');
      node.setAttribute('aria-hidden', 'true');
    });
    if (total <= 0) { badge.remove(); return; }
    const icon = navItem.querySelector('svg, img');
    const anchor = icon?.parentElement || navItem;
    if (getComputedStyle(anchor).position === 'static') anchor.style.position = 'relative';
    badge.className = 'fw-tab-unread-badge';
    badge.setAttribute('data-fw-badge', '1');
    badge.setAttribute('aria-label', `${total}条未读消息`);
    const labelText = total > 99 ? '99+' : String(total);
    if (badge.textContent !== labelText) badge.textContent = labelText;
    if (!badge.isConnected || badge.parentElement !== anchor) anchor.appendChild(badge);
  }
  function applyConversationFontScale() {
    const inConversation=location.hash.includes('/wechat/conversation/') || !!document.querySelector('.fw-local-conversation'); document.body.classList.toggle('fw-conversation-scale-active',inConversation);
    if(!inConversation){ document.querySelectorAll('[data-fw-conversation-text]').forEach(el=>{el.style.fontSize='';el.style.lineHeight='';el.removeAttribute('data-fw-conversation-text');}); return; }
    document.documentElement.style.setProperty('--fw-conversation-scale',userFontScale.toFixed(2));
    const nodes=[...document.querySelectorAll('main *,[class*="conversation"] *,[class*="message"] *,[class*="bubble"] *')].filter(el=>el.children.length===0&&el.textContent.trim()&&el.getBoundingClientRect().width>0).filter(el=>{const cls=typeof el.className==='string'?el.className.toLowerCase():'';return !/(header|topbar|toolbar|input|button|icon|avatar|time|action|title|nav)/.test(cls)&&!el.closest('input,textarea,button,[contenteditable="true"]');});
    for(const el of nodes){ if(!el.dataset.fwConversationBaseSize){const s=getComputedStyle(el);el.dataset.fwConversationBaseSize=s.fontSize;el.dataset.fwConversationBaseLine=s.lineHeight;} const base=parseFloat(el.dataset.fwConversationBaseSize); if(!Number.isFinite(base)||base<8||base>40)continue; el.dataset.fwConversationText='1';el.style.fontSize=(base*userFontScale)+'px'; const line=parseFloat(el.dataset.fwConversationBaseLine);if(Number.isFinite(line))el.style.lineHeight=(line*userFontScale)+'px'; }
  }

  function getGroupMemberAvatars(group) {
    if(group&&Array.isArray(group.memberAvatars)&&group.memberAvatars.length)return group.memberAvatars;
    const names=group&&Array.isArray(group.members)?group.members:[];
    if(!names.length)return [];
    const byName=new Map(collectContacts().map(c=>[c.name,c.avatar]));
    return names.map(name=>byName.get(name)||'');
  }
  // Compose a WeChat-style group avatar: the member avatars are laid out in a
  // grid whose first row carries the remainder (so three members render as one
  // on top and two below) and which never grows beyond a 9x9 arrangement.
  function buildGroupAvatar(avatars) {
    const wrap=document.createElement('span');
    wrap.className='fw-group-avatar';
    const list=(Array.isArray(avatars)?avatars:[]).slice(0,81);
    if(!list.length)return wrap;
    const cols=Math.min(9,Math.max(1,Math.ceil(Math.sqrt(list.length))));
    const rows=Math.min(9,Math.ceil(list.length/cols));
    wrap.style.gridTemplateColumns='repeat('+cols+',1fr)';
    wrap.style.gridTemplateRows='repeat('+rows+',1fr)';
    const firstRow=list.length-(rows-1)*cols;
    const offset=Math.floor((cols-firstRow)/2);
    for(let i=0;i<list.length;i++){
      const row=i<firstRow?0:1+Math.floor((i-firstRow)/cols);
      const col=i<firstRow?offset+i:(i-firstRow)%cols;
      const cell=document.createElement('span');
      cell.className='fw-group-avatar__cell';
      cell.style.gridRow=String(row+1);
      cell.style.gridColumn=String(col+1);
      const src=list[i];
      if(src){
        cell.style.backgroundImage='url("'+src+'")';
        // Some members' photos live on a retired CDN; probe them so the composite
        // avatar shows a bundled picture instead of empty cells.
        const probe=new Image();
        probe.onerror=()=>{
          if(!cell.isConnected||cell.dataset.fwAvatarFallback)return;
          cell.dataset.fwAvatarFallback='1';
          const api=apiAvatarFor(src);
          if(api&&api!==src){
            cell.style.backgroundImage='url("'+api+'")';
            const second=new Image();
            second.onerror=()=>{ if(cell.isConnected&&!cell.dataset.fwAvatarFallback2){ cell.dataset.fwAvatarFallback2='1'; cell.style.backgroundImage='url("'+bundledAvatarFor(src)+'")'; } };
            second.src=api;
            return;
          }
          cell.style.backgroundImage='url("'+bundledAvatarFor(src)+'")';
        };
        probe.src=src;
        if(typeof window.__fwResolveProfileAvatar==='function')window.__fwResolveProfileAvatar(src).then(resolved=>{if(resolved&&cell.isConnected)cell.style.backgroundImage='url("'+resolved+'")';}).catch(()=>{});
      }
      wrap.appendChild(cell);
    }
    return wrap;
  }

  // Open a group in the same local conversation UI the friend chats use. The
  // stored group holds its own messages/greeting so the thread survives
  // reopening, and the header shows the member count after the group name.
  // Find the dialogue-list row backing a conversation: friend rows key on the
  // friend id, group rows on the group id.
  function findConversationRow(friend) {
    if (!friend) return null;
    const id = CSS.escape(String(friend.id));
    return document.querySelector('.fw-local-friend-row[data-fw-friend-id="'+id+'"]')
      || document.querySelector('.fw-local-group-row[data-fw-group-id="'+id+'"]');
  }
  // Resolve a contact by display name (remark first, then nickname) from the
  // local friends, the app's address book, or the cached native profiles.
  function resolveFriendByName(name) {
    const target = String(name || '').trim();
    if (!target) return null;
    const match = f => !!f && (String(f.remark || '').trim() === target || String(f.name || '').trim() === target);
    const local = (Array.isArray(state.contacts) ? state.contacts : []).find(match);
    if (local) return local;
    try {
      const profiles = JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY) || '[]');
      const profile = Array.isArray(profiles) ? profiles.find(p => p && (String(p.remark || '').trim() === target || String(p.nickname || '').trim() === target)) : null;
      if (profile) return nativeProfileToFriend(profile);
    } catch (_) { /* fall back to the cache below */ }
    for (const f of nativeFriendCache.values()) if (match(f)) return f;
    return null;
  }
  // Small self-contained editor used to rename a group from its chat info page.
  function editGroupName(group, onSaved) {
    if (!group || document.querySelector('.fw-text-edit-layer')) return;
    const layer = document.createElement('div');
    layer.className = 'fw-text-edit-layer';
    layer.style.cssText = 'position:fixed;top:0;right:0;bottom:0;left:0;z-index:20250;display:flex;align-items:flex-start;justify-content:center;background:rgba(0,0,0,.35);font-family:inherit';
    layer.innerHTML = '<div style="margin-top:120px;width:calc(100% - 64px);max-width:420px;background:#fff;border-radius:10px;overflow:hidden;box-shadow:0 6px 24px rgba(0,0,0,.2)">'
      + '<div style="padding:14px 16px 4px;text-align:center;font-size:16px;font-weight:500;color:#111">修改群聊名称</div>'
      + '<input maxlength="40" placeholder="请输入群聊名称" style="display:block;width:100%;box-sizing:border-box;padding:4px 18px 16px;border:0;outline:0;font-family:inherit;font-size:16px;color:#111;text-align:center">'
      + '<div style="display:flex;border-top:1px solid #ececec">'
      + '<button type="button" data-cancel style="flex:1;height:48px;border:0;border-right:1px solid #ececec;background:#fff;font-family:inherit;font-size:16px;color:#555">取消</button>'
      + '<button type="button" data-save style="flex:1;height:48px;border:0;background:#fff;font-family:inherit;font-size:16px;color:#07c160;font-weight:500">保存</button>'
      + '</div></div>';
    const input = layer.querySelector('input');
    input.value = String(group.name || '');
    const close = () => layer.remove();
    layer.querySelector('[data-cancel]').onclick = close;
    layer.querySelector('[data-save]').onclick = () => {
      const value = input.value.trim();
      if (!value) { showToast('群聊名称不能为空'); return; }
      group.name = value;
      saveState();
      if (typeof onSaved === 'function') onSaved(value);
      close();
      showToast('已保存');
    };
    layer.addEventListener('click', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
    setTimeout(() => { input.focus(); input.select(); }, 30);
  }

  // ---- Chat info screen (聊天信息), opened from the conversation ••• button ----
  function closeChatInfo() {
    const page = document.querySelector('.fw-chat-info');
    if (!page || page.dataset.closing === '1') return;
    page.dataset.closing = '1';
    page.classList.add('fw-chat-info--exit');
    const finish = () => { if (page.isConnected) page.remove(); };
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 180);
  }
  function openChatInfo(friend) {
    if (!friend) return;
    document.querySelector('.fw-chat-info')?.remove();
    const isGroup = !!friend.isGroup;
    const label = friendLabel(friend) || friend.name || '聊天';
    const members = isGroup && Array.isArray(friend.members) ? friend.members : [];
    const memberCount = isGroup ? (Number(friend.memberCount) || members.length) : 0;
    const title = isGroup && memberCount ? ('聊天信息(' + memberCount + ')') : '聊天信息';
    const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
    let selfName = '我';
    try { const profile = typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : null; if (profile && profile.nickname) selfName = String(profile.nickname); } catch (_) { /* fallback */ }
    const record = chatRecord(label);
    const group = isGroup ? (Array.isArray(state.groups) ? state.groups.find(item => item && String(item.id) === String(friend.id)) : null) : null;
    const avatars = isGroup ? getGroupMemberAvatars(friend) : [getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar)];
    const names = isGroup ? members : [label];
    const shown = isGroup ? Math.min(Math.max(names.length, 1), 4) : 1;
    let cells = '';
    for (let i = 0; i < shown; i++) {
      const src = avatars[i] || CONTACT_FALLBACKS[0].avatar;
      cells += '<figure class="fw-chat-info__member"><img alt="" src="' + esc(src) + '"><figcaption>' + esc(names[i] || label) + '</figcaption></figure>';
    }
    cells += '<button type="button" class="fw-chat-info__add" aria-label="添加成员">＋</button>';
    const block = inner => '<section class="fw-chat-info__block">' + inner + '</section>';
    const rowRaw = (text, value, kind) => '<button type="button" class="fw-chat-info__row" data-row="' + esc(text) + '"><span class="fw-chat-info__label">' + esc(text) + '</span><span class="fw-chat-info__tail">' + (value ? '<small>' + esc(value) + '</small>' : '') + (kind === 'qr' ? '<i class="fw-chat-info__qricon"></i>' : '') + '<i class="fw-chat-info__chev">›</i></span></button>';
    const row = (text, value, kind) => block(rowRaw(text, value, kind));
    const toggle = (text, on) => '<div class="fw-chat-info__row fw-chat-info__row--toggle" data-toggle="' + esc(text) + '"><span class="fw-chat-info__label">' + esc(text) + '</span><span class="fw-chat-info__switch' + (on ? ' is-on' : '') + '" role="switch" aria-checked="' + (on ? 'true' : 'false') + '"><i></i></span></div>';
    const bodyHtml =
      '<section class="fw-chat-info__members">' + cells + '</section>'
      + '<div class="fw-chat-info__gap"></div>'
      + (isGroup ? block(rowRaw('群聊名称', (group && group.name) || '未命名') + rowRaw('群二维码', '', 'qr') + rowRaw('群公告') + rowRaw('备注')) + '<div class="fw-chat-info__gap"></div>' : '')
      + row('查找聊天记录')
      + '<div class="fw-chat-info__gap"></div>'
      + block(toggle('消息免打扰', !!record.muted) + toggle('置顶聊天', !!record.pinnedAt) + (isGroup ? toggle('保存到通讯录', !!record.saved) : toggle('提醒', !!record.notify)))
      + (isGroup ? '<div class="fw-chat-info__gap"></div>' + block(rowRaw('我在群里的昵称', selfName) + toggle('显示群成员昵称', record.showMemberNames !== false)) : '')
      + '<div class="fw-chat-info__gap"></div>'
      + row('设置当前聊天背景')
      + (isGroup ? '' : '<div class="fw-chat-info__gap"></div>' + row('清空聊天记录') + '<div class="fw-chat-info__gap"></div>' + row('投诉'))
      + '<div class="fw-chat-info__gap fw-chat-info__gap--tail"></div>';
    const page = document.createElement('section');
    page.className = 'fw-chat-info';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-label', title);
    page.innerHTML = '<header class="fw-chat-info__header">'
      + '<button type="button" class="fw-chat-info__back" aria-label="返回"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15.5 3.5 5.5 12l10 8.5"/></svg></button>'
      + '<strong class="fw-chat-info__title">' + esc(title) + '</strong>'
      + (isGroup ? '<button type="button" class="fw-chat-info__search" aria-label="搜索"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"/><path d="m16.2 16.2 4.8 4.8"/></svg></button>' : '<span class="fw-chat-info__head-right"></span>')
      + '</header><div class="fw-chat-info__body">' + bodyHtml + '</div>';
    document.body.appendChild(page);
    if (typeof window.__fwResolveProfileAvatar === 'function') {
      page.querySelectorAll('.fw-chat-info__member img').forEach(img => {
        const src = img.getAttribute('src') || '';
        if (!src || src.startsWith('./') || src.startsWith('data:')) return;
        window.__fwResolveProfileAvatar(src).then(resolved => { if (resolved && img.isConnected) img.src = resolved; }).catch(() => {});
      });
    }
    page.querySelector('.fw-chat-info__back').addEventListener('click', closeChatInfo);
    page.querySelector('.fw-chat-info__search')?.addEventListener('click', () => showToast('搜索聊天记录'));
    page.querySelector('.fw-chat-info__add')?.addEventListener('click', () => showToast('添加成员'));
    const applyGroupName = value => {
      if (!group) return;
      const row = document.querySelector('.fw-local-group-row[data-fw-group-id="' + CSS.escape(String(group.id)) + '"]');
      if (row) {
        const leaves = [...row.querySelectorAll('span,div,strong')].filter(el => el.children.length === 0);
        const nameNode = leaves.find(el => el.textContent.trim() === label);
        if (nameNode) nameNode.textContent = value;
      }
      const strong = document.querySelector('.fw-local-conversation')?.querySelector('.fw-local-conversation__header strong');
      if (strong) strong.textContent = value + (memberCount ? '(' + memberCount + ')' : '');
      const valueNode = page.querySelector('.fw-chat-info__row[data-row="群聊名称"] .fw-chat-info__tail small');
      if (valueNode) valueNode.textContent = value;
    };
    page.querySelectorAll('.fw-chat-info__row[data-row]').forEach(btn => btn.addEventListener('click', () => {
      const text = btn.dataset.row;
      if (text === '群聊名称') { editGroupName(group, applyGroupName); return; }
      if (text === '清空聊天记录') { if (Array.isArray(friend.messages)) friend.messages.length = 0; saveState(); showToast('聊天记录已清空'); document.querySelector('.fw-local-conversation')?.__fwDrawMessages?.(); return; }
      showToast(text);
    }));
    page.querySelectorAll('.fw-chat-info__switch').forEach(sw => sw.addEventListener('click', () => {
      sw.classList.toggle('is-on');
      const on = sw.classList.contains('is-on');
      sw.setAttribute('aria-checked', on ? 'true' : 'false');
      const text = sw.closest('.fw-chat-info__row')?.dataset.toggle;
      if (text === '置顶聊天') { record.pinnedAt = on ? Date.now() : null; saveState(); queueApply(); }
      else if (text === '消息免打扰') { record.muted = on; saveState(); }
      else if (text === '保存到通讯录') { record.saved = on; saveState(); }
      else if (text === '显示群成员昵称') { record.showMemberNames = on; saveState(); }
      else { record.notify = on; saveState(); }
    }));
    // Tapping a member avatar opens that person's profile over this page.
    page.querySelectorAll('.fw-chat-info__member').forEach((el, index) => {
      el.style.cursor = 'pointer';
      el.addEventListener('click', () => {
        const name = names[index] || label;
        const target = isGroup ? resolveFriendByName(name) : friend;
        if (target) openFriendProfile(target);
        else showToast('未找到该联系人');
      });
    });
  }

  function openGroupConversation(group) {
    if (!group) return;
    const members = Array.isArray(group.members) ? group.members : [];
    // Pair every member with a picture up front: generated groups only carry avatars
    // for the members it created, so the rest are filled in from the address book.
    const known = collectContacts();
    const memberAvatars = members.map((name, index) => {
      const fromGroup = Array.isArray(group.memberAvatars) ? group.memberAvatars[index] : '';
      if (fromGroup) return fromGroup;
      const hit = known.find(contact => String(contact?.name || '').trim() === String(name || '').trim());
      if (hit && hit.avatar) return hit.avatar;
      // 2026-10-11: no picture of our own -> take one from the 随机头像 API.
      return apiAvatarFor(String(group.id || group.name || '') + '|' + String(name || ''));
    });
    // Remember the pairing so the faces stay the same on the next open.
    if (memberAvatars.some(Boolean) && JSON.stringify(group.memberAvatars || []) !== JSON.stringify(memberAvatars)) {
      group.memberAvatars = memberAvatars;
      try { saveState(); } catch (_) { /* local demo only */ }
    }
    const avatar = memberAvatars.find(Boolean) || getGroupMemberAvatars(group).find(Boolean) || CONTACT_FALLBACKS[0].avatar;
    const friend = {
      id: group.id,
      name: group.name || '群聊',
      isGroup: true,
      members,
      memberAvatars,
      memberCount: members.length,
      avatar,
      messages: Array.isArray(group.messages) ? group.messages : (group.messages = []),
      greeting: group.greeting,
    };
    openLocalFriendConversation(friend);
  }

  function ensureLocalGroupRows() {
    const groups=Array.isArray(state.groups)?state.groups:[];
    if(!groups.length)return;
    const list=document.querySelector('.wechat-dialogue-list');
    const existing=[...document.querySelectorAll('.fw-local-group-row')];
    const known=new Set(existing.map(row=>row.dataset.fwGroupId));
    const template=getRows()[0];
    if(!list||!template)return;
    for(const group of groups){
      if(!group?.id||known.has(group.id))continue;
      const row=template.cloneNode(true);
      row.classList.add('fw-local-group-row');
      row.dataset.fwGroupId=group.id;
      row.dataset.fwLocalGroup='1';
      delete row.dataset.fwFriendId;
      delete row.dataset.fwLocalFriend;
      const info=getChatInfo(row);
      if(info){
        // Local friend rows render their name in a <strong>, so include it here
        // (the original span/div-only search silently left cloned rows unrenamed).
        const leaves=[...row.querySelectorAll('span,div,strong')].filter(el=>el.children.length===0);
        const nameNode=leaves.find(el=>el.textContent.trim()===info.name);
        if(nameNode)nameNode.textContent=group.name||'群聊';
        const preview=row.querySelector('.fw-local-friend-copy small')||leaves.find(el=>el!==nameNode&&/你好|已经是好友|欢迎使用|打招呼|图片/.test(el.textContent));
        if(preview)preview.textContent=group.members&&group.members.length?('群聊已创建 · '+group.members.length+'人'):'群聊已创建';
      }
      row.querySelectorAll('.fw-group-avatar').forEach(el=>el.remove());
      const composite=buildGroupAvatar(getGroupMemberAvatars(group));
      const img=row.querySelector('img');
      if(img)img.replaceWith(composite);
      else{const host=row.querySelector('.fw-local-friend-copy')||row;host.insertBefore(composite,host.firstChild);}
      row.querySelectorAll('.fw-unread-badge').forEach(el=>el.remove());
      row.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); openGroupConversation(group); }, true);
      list.insertBefore(row,list.firstChild);
    }
  }

  function ensureInitialPinState(rows) {
    // Apply the default only while no explicit user choice exists. Do not gate
    // this on the migration version: older installs may have the version flag
    // but lack the actual pin record.
    const firstFriend = null;
    if (!firstFriend) return;
    const info = getChatInfo(firstFriend);
    const record = chatRecord(info.key);
    if (record.pinnedAt === undefined && record.pinCancelExplicit !== true) {
      record.pinnedAt = Date.now();
      state.pinBootstrapDone = true;
      state.pinBootstrapVersion = 3;
      saveState();
    }
  }

  // 2026-10-17: 打包的本地头像（assets/fake_world/assets/generated-avatars，402 个文件 2.66MB）
  // 已整体删除，头像一律走 随机头像 API（AVATAR_API）—— 联系人头像本来就由 API 提供，
  // 这批 DiceBear 图片只剩下「远程加载失败时兜底」这一个用途，不值得占 2.66MB。
  //
  // 兜底仍然要有，只是换成一个**内联**的灰色占位图：不占安装包体积、永远不会加载失败，
  // 比浏览器那个碎图标体面得多。
  const PLACEHOLDER_AVATAR = 'data:image/svg+xml;utf8,' + encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96">' +
      '<rect width="96" height="96" fill="#e9ebef"/>' +
      '<circle cx="48" cy="37" r="17" fill="#b9bfc9"/>' +
      '<path d="M14 96c0-19 15-31 34-31s34 12 34 31z" fill="#b9bfc9"/>' +
    '</svg>',
  );
  try { window.__fwAvatarPlaceholder = PLACEHOLDER_AVATAR; } catch (_) { /* 非浏览器环境忽略 */ }

  const AVATAR_POOL_SIZE = 400;
  // 远程随机头像接口每次请求都可能换脸，且 iOS 原生层没有实现头像缓存桥。
  // 这里改成随包内的固定头像池：同一个 seed 永远同一张脸，重启不变。
  // aa1 头像 API 的固定 CDN 图片池：同一个 seed 永远同一张头像，
  // 图片本身仍由 api.aa1.cn 提供，不再每次打开随机换脸。
  // xxapi 头像 API 的 CDN 图片池；图片由接口返回，按 seed 固定映射。
  const AVATAR_API_POOL = [
    'https://images.xxapi.cn/images/head/7610375774.jpg',
    'https://images.xxapi.cn/images/head/3436398854.jpg',
    'https://images.xxapi.cn/images/head/3048837505.jpg',
    'https://images.xxapi.cn/images/head/6595148448.jpg',
    'https://images.xxapi.cn/images/head/1495778864.jpg',
    'https://images.xxapi.cn/images/head/7688549202.jpg',
    'https://images.xxapi.cn/images/head/6090430902.jpg',
    'https://images.xxapi.cn/images/head/1521764924.jpg',
    'https://images.xxapi.cn/images/head/4064787026.jpg',
    'https://images.xxapi.cn/images/head/7962170974.jpg',
    'https://images.xxapi.cn/images/head/3264184319.jpg',
    'https://images.xxapi.cn/images/head/2066770470.jpg',
    'https://images.xxapi.cn/images/head/2739863757.jpg',
    'https://images.xxapi.cn/images/head/3445248961.jpg',
    'https://images.xxapi.cn/images/head/9450039731.jpg',
    'https://images.xxapi.cn/images/head/1894834391.jpg',
    'https://images.xxapi.cn/images/head/8835187396.jpg',
    'https://images.xxapi.cn/images/head/0532241039.jpg',
    'https://images.xxapi.cn/images/head/5841001491.jpg',
    'https://images.xxapi.cn/images/head/3342078749.jpg',
    'https://images.xxapi.cn/images/head/9845564794.jpg',
    'https://images.xxapi.cn/images/head/2556022004.jpg',
    'https://images.xxapi.cn/images/head/9137570530.jpg',
    'https://images.xxapi.cn/images/head/2848519259.jpg',
  ];
  const BUNDLED_AVATAR_FILES = [
    './assets/user-avatar-1-CKPDcH57.jpg',
    './assets/user-avatar-4-Cmbgep_s.jpg',
    './assets/user-avatar-5-DMigR-cn.jpg',
    './assets/user-avatar-6-Cq95zrQT.jpg',
    './assets/user-avatar-7-C4bh6bYx.jpg',
    './assets/user-avatar-8-BysMeksY.jpg',
    './assets/user-avatar-10-ZKj7tiz_.jpg',
  ];
  // 群成员 / 随机生成的联系人：同一个 index 永远拿到同一张脸（API 结果按 seed 缓存）
  function avatarUrl(index) {
    return apiAvatarFor('member-' + String(index));
  }
  // 所有远程头像都失败时的最后一层：内联占位（以前指向打包的那批图片）
  function bundledAvatarFor(seed) {
    const hash = avatarSeedHash(seed);
    const palette = ['#5b8def', '#e28a5b', '#55a87a', '#b276c9', '#d35f75', '#4f9fb2', '#b58b4a'];
    const color = palette[Math.abs(hash) % palette.length];
    const raw = String(seed == null ? '' : seed).trim();
    const text = (raw.charAt(0) || '微').replace(/[&<>"']/g, ch => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 96 96">'
      + '<rect width="96" height="96" rx="5" fill="' + color + '"/>'
      + '<text x="48" y="61" text-anchor="middle" font-size="42" font-family="-apple-system,BlinkMacSystemFont,sans-serif" fill="#fff">'
      + text + '</text></svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }
  function apiAvatarFor(seed) {
    const hash = avatarSeedHash(seed);
    return AVATAR_API_POOL[Math.abs(hash) % AVATAR_API_POOL.length];
  }

  function makeAvatarUrl() { return remoteAvatarUrl(); }
  const AVATAR_API = 'https://v2.xxapi.cn/api/head';
  const AVATAR_API_JSON = AVATAR_API + '?return=json';
  function remoteAvatarUrl() { return AVATAR_API + '?return=302&_=' + Date.now() + Math.random().toString(36).slice(2); }
  function avatarSeedHash(seed) {
    const text = String(seed == null ? '' : seed);
    let hash = 11;
    for (let i = 0; i < text.length; i += 1) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
    return hash;
  }
  // 2026-10-11: member pictures come from the 随机头像 API the user asked for
  // (https://xxapi.cn/doc/head). The nonce is derived from the member's name, so the
  // same person keeps the same face across renders; the app's avatar cache freezes the
  // 302 redirect into a local file so it stays put on later opens.
  function apiAvatarForLegacy(seed) {
    return bundledAvatarFor(seed);
    const key = 'fake-world.wechat.head-avatar.' + avatarSeedHash(seed);
    try {
      const remembered = localStorage.getItem(key) || '';
      if (/^(?:https?:\/\/|data:image\/)/i.test(remembered)) return remembered;
    } catch (_) { /* fall through to a fresh URL */ }
    const remote = AVATAR_API + '?return=302&_=' + avatarSeedHash(seed);
    let value = remote;
    try { value = freezeRemoteAvatar(remote) || remote; } catch (_) { value = remote; }
    try { localStorage.setItem(key, value); } catch (_) { /* local demo only */ }
    return value;
  }
  async function fetchAvatarApiUrl() {
    const response = await fetch(AVATAR_API_JSON + '&_=' + Date.now() + Math.random().toString(36).slice(2), { method: 'GET', cache: 'no-store' });
    if (!response.ok) throw new Error('头像接口响应失败');
    const payload = await response.json();
    const url = typeof payload?.data === 'string' ? payload.data.trim() : '';
    if (!url || !/^https?:\/\//i.test(url)) throw new Error('头像接口未返回图片地址');
    return url;
  }
  function makeBatchAvatarUrl() {
    return apiAvatarFor('batch-' + Date.now() + Math.random().toString(36).slice(2));
  }

    function migrateGeneratedProfiles() {
    const friends = Array.isArray(state.contacts) ? state.contacts.filter(friend => friend?.id && friend.name) : [];
    const generated = friends.filter(friend => friend?.source === '随机生成' && friend.generatedProfileVersion !== 2);
    if (!generated.length) return;
    const used = new Set(friends.filter(friend => friend?.source !== '随机生成').map(friend => String(friend?.name || '').toLocaleLowerCase()));
    const candidates = [...BATCH_HANDLE_SEEDS, ...BATCH_HANDLE_SEEDS.flatMap((first,index)=>BATCH_HANDLE_SEEDS.slice(index+1).map(second=>first+'·'+second))];
    for (const friend of generated) {
      const oldName = String(friend.name || '');
      const nextName = candidates.find(name => !used.has(name.toLocaleLowerCase()));
      if (!nextName) break;
      used.add(nextName.toLocaleLowerCase()); friend.name = nextName; friend.realName = ''; friend.avatar = apiAvatarFor(friend.name); friend.generatedProfileVersion = 2; friend.starred = false;
      const oldRecord = state.chats?.[oldName];
      if (oldRecord && !state.chats[nextName]) { state.chats[nextName] = oldRecord; delete state.chats[oldName]; }
      const record = chatRecord(nextName); record.pinnedAt = null; record.unreadCount = 0; record.unread = false; record.unreadOverride = true; record.generatedDefaultsApplied = true;
    }
    saveState();
  }
  migrateGeneratedProfiles();

  // 老版本把远程随机头像 URL 存进了 state，启动时统一迁回本地固定头像。
  (function normalizeRemoteAvatars() {
    let changed = false;
    const legacyRemote = value => typeof value === 'string'
      && /^https?:\/\//i.test(value)
      && !/api\.dicebear\.com/i.test(value);
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => {
      if (friend && friend.source === '随机生成' && /api\.aa1\.cn/i.test(String(friend.avatar || ''))) {
        friend.avatar = apiAvatarFor(friend.name || friend.id || 'contact');
        changed = true;
      } else if (friend && (!friend.avatar || legacyRemote(friend.avatar))) {
        friend.avatar = apiAvatarFor(String(friend.name || friend.id || 'contact'));
        changed = true;
      }
    });
    (Array.isArray(state.groups) ? state.groups : []).forEach(group => {
      if (!group || !Array.isArray(group.memberAvatars)) return;
      group.memberAvatars = group.memberAvatars.map((value, index) => {
        if (!local(value)) return value;
        changed = true;
        return bundledAvatarFor(String(group.id || group.name || 'group') + '|' + index);
      });
    });
    if (changed) saveState();
  })();
  // 2026-10-11: contacts and group members created by an earlier build carried a bundled
  // cartoon avatar. They now take a real picture from the 随机头像 API instead.
  (function migrateBundledAvatars() {
    const bundled = value => /^\.\/assets\/generated-avatars\//.test(String(value || ''));
    let changed = false;
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => {
      if (!friend || !bundled(friend.avatar)) return;
      friend.avatar = apiAvatarFor(String(friend.id || friend.name || '') + '|contact');
      changed = true;
    });
    (Array.isArray(state.groups) ? state.groups : []).forEach(group => {
      if (!group || !Array.isArray(group.memberAvatars)) return;
      group.memberAvatars = group.memberAvatars.map((value, index) => (
        bundled(value) ? apiAvatarFor(String(group.id || group.name || '') + '|' + index) : value
      ));
      changed = true;
    });
    if (changed) {
      try { saveState(); } catch (_) { /* local demo only */ }
      // Push the refreshed faces to the address book once the native bridge is up.
      setTimeout(() => { try { syncNativeAddressBook(); } catch (_) { /* bridge may not be ready */ } }, 0);
    }
  })();
  // 2026-10-10: every "..." button the local WeChat pages build (chat header and
  // friend profile header) uses the tight, reference-matched dot run: 15.3 x 3 CSS px
  // with 3 CSS px dots on a 6.15 CSS px pitch.
  const MORE_ICON_SELECTOR = '.fw-chat-more svg, .fw-friend-profile__more svg';
  function normalizeMoreIconGeometry(root) {
    const scope = root && root.nodeType === 1 ? root : document;
    const icons = [];
    if (scope.matches?.(MORE_ICON_SELECTOR)) icons.push(scope);
    scope.querySelectorAll?.(MORE_ICON_SELECTOR).forEach(svg => icons.push(svg));
    icons.forEach(svg => {
      if (svg.dataset.fwDotRun === '1') return;
      svg.dataset.fwDotRun = '1';
      svg.setAttribute('viewBox', '0 0 15.3 3');
      const xs = [1.5, 7.65, 13.8];
      svg.querySelectorAll('circle').forEach((circle, index) => {
        circle.setAttribute('cx', String(xs[index] ?? xs[0]));
        circle.setAttribute('cy', '1.5');
        circle.setAttribute('r', '1.5');
      });
    });
  }
  function closeLocalConversation() {
    // 2026-10-19: 收款/转账流程会再次打开同一个会话。以前只 remove 第一个页面，
    // 旧的会话页留在 DOM 里，叠出两三层「打招呼 + 转账卡片」的残影。
    // 这里一次关掉所有会话页，保证返回后页面干净。
    const pages = Array.from(document.querySelectorAll('.fw-local-conversation'));
    if (!pages.length) return;
    const page = pages[pages.length - 1];
    if (page.dataset.fwClosing === '1') return;
    let finished = false;
    pages.forEach(item => {
      item.dataset.fwClosing = '1';
      item.classList.add('fw-local-conversation--exit');
    });
    const finish = () => {
      if (finished) return;
      finished = true;
      if (selfAvatarSyncTimer) { clearInterval(selfAvatarSyncTimer); selfAvatarSyncTimer = null; }
      openMessageMenuForRow = null;
      pages.forEach(item => item.remove());
      document.body.classList.remove('fw-local-conversation-open');
      applyConversationFontScale();
      syncChatSystemBars();
    };
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 150);
  }
  window.__fwCloseConversation = function () { const open = !!document.querySelector('.fw-local-conversation'); if (open) closeLocalConversation(); return open; };
  /**
   * 2026-10-16：把「本地会话」这个 friend 登记进 state.contacts。
   *
   * saveState() 只序列化 state，而消息挂在 friend 对象上。从「通讯录 → 发消息」进来的 friend
   * 是页面自己造的临时对象、不在 state.contacts 里，所以用户发出去的消息、改过的引用、删掉的
   * 记录都存不下来 —— 杀掉后台再打开，整段对话回到只有两句问候语（实测 localStorage 里只有
   * chats，contacts 是空的）。发第一条消息时把它登记进来，这段对话就跟着存档走了，同时也让
   * 「微信」列表里出现这条会话（和真微信一致）。
   */
  function registerConversationFriend(friend) {
    if (!friend || !friend.id) return false;
    if (!Array.isArray(state.contacts)) state.contacts = [];
    if (state.contacts.some(item => item && item.id === friend.id)) return false;
    state.contacts.unshift(friend);
    const label = String(friend.remark || friend.name || '').trim();
    if (label) {
      const record = chatRecord(label);
      // 别让「删除该聊天 / 不显示该聊天」留下的标记把这条新会话挡掉
      record.deleted = false;
      record.hidden = false;
    }
    return true;
  }

  function openLocalFriendConversation(friend) {
    if (!friend || !friend.id) return;
    const label = friend.remark || friend.name || '聊天';
    const record = chatRecord(label);
    record.unreadCount = 0; record.unread = false; record.unreadOverride = true;
    friend.messages = Array.isArray(friend.messages) ? friend.messages : [];
    // The two opening lines are ordinary stored messages too, so they can be swiped
    // to the other speaker and keep that state when the conversation is reopened.
    const greeting = friend.greeting && typeof friend.greeting === 'object' ? friend.greeting : (friend.greeting = {});
    if (!greeting.mine || typeof greeting.mine !== 'object') greeting.mine = { side: 'outgoing' };
    if (!greeting.friend || typeof greeting.friend !== 'object') greeting.friend = { side: 'incoming' };
    getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar);
    // Defer contact-list serialization until the chat entrance animation is underway.
    setTimeout(saveState, 180);
    // 打开会话前清掉所有旧会话页，避免收款/转账后重复打开时页面叠在一起。
    document.querySelectorAll('.fw-local-conversation').forEach(page => page.remove());

    const page = document.createElement('section');
    page.className = 'fw-local-conversation';
    page.dataset.friendId = friend.id;
    page.innerHTML = '<header class="fw-local-conversation__header"><button type="button" class="fw-chat-back" aria-label="返回">‹</button><strong></strong><button type="button" class="fw-chat-more" aria-label="更多"><svg viewBox="0 0 28 8" aria-hidden="true"><circle cx="4" cy="4" r="1.6"/><circle cx="14" cy="4" r="1.6"/><circle cx="24" cy="4" r="1.6"/></svg></button></header><main class="fw-local-conversation__messages" aria-live="polite"></main><form class="fw-local-conversation__composer"><button type="button" class="fw-chat-voice" aria-label="语音输入"><img class="fw-composer-icon" src="./assets/wechat-feature-icons/composer-voice.png" alt="" draggable="false"></button><div class="fw-chat-input-wrap"><input aria-label="消息" autocomplete="off"><button type="button" class="fw-chat-mic" aria-label="语音转文字"><img class="fw-composer-icon" src="./assets/wechat-feature-icons/composer-mic.png" alt="" draggable="false"></button></div><button type="button" class="fw-chat-emoji" aria-label="表情"><img class="fw-composer-icon" src="./assets/wechat-feature-icons/composer-emoji.png" alt="" draggable="false"></button><button type="button" class="fw-chat-plus" aria-label="更多功能"><img class="fw-composer-icon" src="./assets/wechat-feature-icons/composer-plus.png" alt="" draggable="false"></button><button type="submit" class="fw-chat-send">发送</button></form>';
    page.querySelector('header strong').textContent = label + (Number(friend.memberCount) > 0 ? '(' + friend.memberCount + ')' : '');
    document.body.appendChild(page);
    page.classList.add('fw-local-conversation--enter');
    document.body.classList.add('fw-local-conversation-open');
    normalizeMoreIconGeometry(page);
    syncChatSystemBars();
    requestAnimationFrame(() => {    const messages = page.querySelector('.fw-local-conversation__messages');
    const friendAvatar = getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar);
    const fallbackSelfAvatar = apiAvatarFor('self-avatar');
    const getSelfProfile = () => {
      try {
        const profile = typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : null;
        if (profile && typeof profile === 'object') return profile;
      } catch (_) { /* use stable fallback */ }
      return { nickname: '林克', avatarInfo: fallbackSelfAvatar };
    };
    const getSelfAvatarValue = () => {
      const value = getSelfProfile().avatarInfo;
      if (typeof value === 'string' && value.trim()) return value.trim();
      if (value && typeof value === 'object') return value.url || value.src || value.uri || '';
      return '';
    };
    const resolveSelfAvatar = async value => {
      try {
        if (typeof window.__fwResolveProfileAvatar === 'function') {
          return await window.__fwResolveProfileAvatar(value);
        }
      } catch (_) { /* use the same local fallback as Fake World */ }
      return fallbackSelfAvatar;
    };
    const getSelfAvatar = () => getSelfAvatarValue() || fallbackSelfAvatar;
    const getSelfName = () => String(getSelfProfile().nickname || getSelfProfile().name || '林克');
    // 2026-10-10: every incoming bubble in a group carries a sender. A long press on
    // that avatar lets the user hand the message over to another member of the group.
    const groupMemberNames = () => (Array.isArray(friend.members) ? friend.members : [])
      .map(name => String(name || '').trim()).filter(Boolean);
    const groupMemberAvatar = name => {
      const target = String(name || '').trim();
      if (!target) return '';
      try {
        const hit = collectContacts().find(contact => String(contact?.name || '').trim() === target);
        if (hit?.avatar) return hit.avatar;
      } catch (_) { /* fall back to the group's own member avatars */ }
      // The member list and the avatar list can be out of sync, so pair them by index
      // first and only then give up - that keeps each speaker's picture stable.
      const index = groupMemberNames().indexOf(target);
      const avatars = Array.isArray(friend.memberAvatars) ? friend.memberAvatars : [];
      if (index >= 0 && avatars[index]) return avatars[index];
      return '';
    };
    const groupSenderName = message => {
      const explicit = String(message?.sender ?? '').trim();
      if (explicit) return explicit;
      return groupMemberNames()[0] || '';
    };
    const openGroupSenderPicker = message => {
      if (!friend.isGroup || !message) return;
      const current = groupSenderName(message);
      const members = groupMemberNames()
        .filter(name => name && name !== getSelfName() && name !== friendLabel(friend));
      if (!members.length) { showToast('群里还没有其他成员'); return; }
      document.querySelector('.fw-group-sender-picker')?.remove();
      const layer = document.createElement('div');
      layer.className = 'fw-group-sender-picker';
      layer.innerHTML = '<div class="fw-group-sender-picker__panel"><header><button type="button" data-cancel>取消</button><strong>替换发言成员</strong><span></span></header><p class="fw-group-sender-picker__hint">选择这条消息的发言人</p><div class="fw-group-sender-picker__rows"></div></div>';
      const host = layer.querySelector('.fw-group-sender-picker__rows');
      members.forEach(name => {
        const row = document.createElement('button');
        row.type = 'button';
        row.className = 'fw-group-sender-picker__row' + (name === current ? ' is-current' : '');
        row.innerHTML = '<img alt=""><span></span><i></i>';
        row.querySelector('img').src = groupMemberAvatar(name) || friend.avatar || '';
        row.querySelector('span').textContent = name;
        row.querySelector('i').textContent = name === current ? '当前' : '';
        row.onclick = () => {
          layer.remove();
          message.sender = name;
          saveState();
          document.querySelector('.fw-local-conversation')?.__fwDrawMessages?.();
          showToast('已替换为 ' + name);
        };
        host.appendChild(row);
      });
      layer.querySelector('[data-cancel]').onclick = () => layer.remove();
      layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
      document.body.appendChild(layer);
    };
    const attachGroupAvatarPress = (img, message, state) => {
      let timer = null;
      let startX = 0;
      let startY = 0;
      const cancel = () => { if (timer) { clearTimeout(timer); timer = null; } };
      img.style.touchAction = 'none';
      // The native long-press bridge uses this hook so it can open the sender sheet too.
      img.__fwOpenGroupSenderPicker = () => openGroupSenderPicker(message);
      img.addEventListener('pointerdown', event => {
        if (event.pointerType === 'mouse' && event.button !== 0) return;
        // Keep the message row's own 480ms menu timer from also firing for this press.
        event.stopPropagation();
        state.fired = false;
        startX = event.clientX; startY = event.clientY;
        cancel();
        timer = setTimeout(() => { timer = null; state.fired = true; openGroupSenderPicker(message); }, HOLD_MS);
      }, true);
      ['pointerup', 'pointercancel', 'pointerleave'].forEach(type => img.addEventListener(type, cancel, true));
      img.addEventListener('pointermove', event => {
        if (Math.abs(event.clientX - startX) > 12 || Math.abs(event.clientY - startY) > 12) cancel();
      }, true);
      img.addEventListener('click', event => {
        if (!state.fired) return;
        state.fired = false;
        event.preventDefault();
        event.stopPropagation();
      }, true);
    };
    const makeTime = text => { const el=document.createElement('div'); el.className='fw-chat-time'; el.textContent=text; messages.appendChild(el); };
    // 2026-10-19: 聊天里「我」的头像必须和「我」页显示的是同一张。
    // 「我」页把 profile.avatarInfo 交给应用的 __fwResolveProfileAvatar 解析（旧的
    // fakeworld URL 会被映射成内置头像）；聊天页以前直接拒绝 fakeworld 并换随机头像池，
    // 于是两边永远不是同一张脸。现在统一走同一个解析器，并缓存解析结果。
    let selfAvatarSource = '';
    let selfAvatarSourceKey = null;
    let selfAvatarSourceTask = null;
    const selfAvatarRawValue = () => {
      const value = getSelfAvatarValue();
      return typeof value === 'string' ? value.trim() : '';
    };
    const resolveSelfAvatarSource = () => {
      const raw = selfAvatarRawValue();
      const key = raw + '|' + getSelfName();
      if (selfAvatarSource && selfAvatarSourceKey === key) return Promise.resolve(selfAvatarSource);
      if (selfAvatarSourceTask && selfAvatarSourceKey === key) return selfAvatarSourceTask;
      selfAvatarSourceKey = key;
      selfAvatarSourceTask = Promise.resolve(resolveSelfAvatar(raw))
        .then(src => {
          const next = String(src || '').trim();
          if (next) selfAvatarSource = next;
          return selfAvatarSource;
        })
        .catch(() => selfAvatarSource);
      return selfAvatarSourceTask;
    };
    // 所有「我」发出的头像只走这一个入口：先显示内置占位（绝不先闪一张别人的脸），
    // 再用「我」页同一个解析结果替换。
    const applySelfAvatar = (img, preferred) => {
      if (!img) return;
      const fallback = bundledAvatarFor('self-avatar');
      img.referrerPolicy = 'no-referrer';
      img.alt = getSelfName();
      img.title = img.alt;
      img.dataset.fwSelfAvatar = '1';
      img.dataset.fwAvatarValue = getSelfAvatar();
      img.onerror = () => {
        img.onerror = null;
        if (img.src !== fallback) img.src = fallback;
      };
      // 只有解析缓存和当前资料一致时才直接复用，避免资料页换了头像后聊天里还先闪旧脸。
      const currentKey = selfAvatarRawValue() + '|' + getSelfName();
      const cached = selfAvatarSource && selfAvatarSourceKey === currentKey ? selfAvatarSource : '';
      const initial = cached || preferred || PLACEHOLDER_AVATAR;
      if (img.src !== initial) img.src = initial;
      if (img.__fwAvatarTimer) clearTimeout(img.__fwAvatarTimer);
      img.onload = () => { if (img.__fwAvatarTimer) { clearTimeout(img.__fwAvatarTimer); img.__fwAvatarTimer = 0; } };
      img.__fwAvatarTimer = setTimeout(() => {
        if (img.isConnected && !img.naturalWidth) img.src = fallback;
      }, 1800);
      resolveSelfAvatarSource().then(src => {
        if (src && img.isConnected && img.dataset.fwAvatarSide !== 'incoming') img.src = src;
      });
    };
    // 所有消息头像都走这一个入口：incoming 用对方资料图，outgoing 用「我」页同款头像。
    const paintMessageAvatar = (img, side, preferred) => {
      if (!img) return;
      const incoming = side === 'incoming';
      img.referrerPolicy = 'no-referrer';
      img.dataset.fwAvatarSide = side;
      if (!incoming) {
        applySelfAvatar(img, preferred);
        return;
      }
      const seed = String(friend.name || friend.id || 'friend');
      const fallback = bundledAvatarFor(seed);
      const primary = preferred || friendAvatar || apiAvatarFor(seed);
      img.dataset.fwFriendAvatar = '1';
      delete img.dataset.fwSelfAvatar;
      img.onerror = () => {
        img.onerror = null;
        if (img.src !== fallback) img.src = fallback;
      };
      img.src = primary || fallback;
      if (img.__fwAvatarTimer) clearTimeout(img.__fwAvatarTimer);
      img.onload = () => { if (img.__fwAvatarTimer) { clearTimeout(img.__fwAvatarTimer); img.__fwAvatarTimer = 0; } };
      img.__fwAvatarTimer = setTimeout(() => {
        if (img.isConnected && !img.naturalWidth && img.src !== fallback) img.src = fallback;
      }, 1800);
      if (img.complete && img.naturalWidth === 0 && img.src !== fallback) img.src = fallback;
      img.alt = String(friend.remark || friend.name || '对方');
      img.title = img.alt;
    };
    const makeBubble = (text, side, avatar, message) => {
      const line=document.createElement('div'); line.className='fw-chat-message fw-chat-message--'+side;
      // Rows tied to a stored message can be re-assigned to the other speaker by swiping.
      line.__fwMessage=message||null;
      const groupSender = side === 'incoming' && friend.isGroup ? groupSenderName(message) : '';
      const groupPress = friend.isGroup && side === 'incoming' ? { fired: false } : null;
      const img=document.createElement('img'); img.className='fw-chat-message__avatar'; img.alt=side==='outgoing' ? getSelfName() : '';
      if(side!=='outgoing'){
        img.dataset.fwFriendAvatar='1';
        img.addEventListener('click',event=>{
          event.preventDefault();
          event.stopPropagation();
          if(groupPress?.fired){groupPress.fired=false;return;}
          if (groupSender) { openFriendProfile(resolveFriendByName(groupSender) || friend); return; }
          openFriendProfile(friend);
        },true);
        img.addEventListener('pointerup',event=>{
          if(event.pointerType==='touch'){
            event.preventDefault();
            event.stopPropagation();
            if(groupPress?.fired){groupPress.fired=false;return;}
            if (groupSender) { openFriendProfile(resolveFriendByName(groupSender) || friend); return; }
            openFriendProfile(friend);
          }
        },true);
        if (groupPress) attachGroupAvatarPress(img, message, groupPress);
      }
      paintMessageAvatar(img, side, groupSender ? groupMemberAvatar(groupSender) : null);
      const bubble=document.createElement('div'); bubble.className='fw-chat-message__bubble';
      if(isImageSource(text)){
        const photo=document.createElement('img');
        photo.src=text;photo.alt='图片';photo.className='fw-chat-message__photo';
        photo.onclick=()=>openSentImagePreview(photo.src);
        // Unsplash's source endpoint is retired (503); swap to a live photo host, then
        // degrade to a plain [图片] bubble so the transcript never shows a broken image.
        photo.onerror=()=>{
          if(!photo.dataset.fwPhotoFallback){photo.dataset.fwPhotoFallback='1';photo.src=fallbackPhotoUrl(text);return;}
          photo.onerror=null;photo.remove();
          bubble.classList.remove('fw-chat-message__bubble--photo');
          const alt=document.createElement('span');alt.className='fw-chat-message__text';alt.textContent='[图片]';bubble.appendChild(alt);
        };
        bubble.appendChild(photo);bubble.classList.add('fw-chat-message__bubble--photo');
        // Photos/videos carry no bubble tail, matching the reference capture.
        line.classList.add('fw-chat-message--media');
      }else if(typeof text==='string'&&text.startsWith('data:video/')){
        const wrap=document.createElement('div');
        wrap.className='fw-chat-video';
        const clip=document.createElement('video');
        clip.className='fw-chat-video__media';
        clip.src=text;clip.muted=true;clip.playsInline=true;clip.preload='metadata';
        const play=document.createElement('span');
        play.className='fw-chat-video__play';
        play.innerHTML='<svg viewBox="0 0 24 24"><path d="M8.6 5.4v13.2L19 12z"/></svg>';
        const badge=document.createElement('span');
        badge.className='fw-chat-video__time';
        const paintDuration=()=>{ badge.textContent=chatMediaDuration(clip.duration); };
        // MediaRecorder WebM reports duration === Infinity until the element seeks once,
        // so nudge it and then rewind before painting the badge.
        const resolveDuration=()=>{
          if(Number.isFinite(clip.duration)&&clip.duration>0){paintDuration();return;}
          const onTime=()=>{clip.removeEventListener('timeupdate',onTime);try{clip.currentTime=0;}catch(_){}paintDuration();};
          clip.addEventListener('timeupdate',onTime);
          try{clip.currentTime=1e101;}catch(_){paintDuration();}
        };
        clip.addEventListener('loadedmetadata',resolveDuration);
        clip.addEventListener('durationchange',paintDuration);
        wrap.append(clip,play,badge);
        wrap.addEventListener('click',event=>{event.stopPropagation();openChatMediaViewer(text,'video');});
        bubble.appendChild(wrap);bubble.classList.add('fw-chat-message__bubble--photo');
        line.classList.add('fw-chat-message--media');
      }else{const body=document.createElement('span');body.className='fw-chat-message__text';body.textContent=text==null?'':text;bubble.appendChild(body);}
      // A reply shows its own text first and the quoted block underneath as a
      // separate grey box (matching the reference capture), never inside the bubble.
      let content=bubble;
      if(message&&message.quote){
        content=document.createElement('div');
        content.className='fw-chat-message__stack';
        content.append(bubble,buildQuoteBlock(message.quote,'fw-quote--standalone'));
      }
      if(side==='incoming'){
        if(groupSender){
          // Group bubbles show who is speaking, so replacing the sender is visible.
          const body=document.createElement('div');
          body.className='fw-chat-message__body';
          const nameEl=document.createElement('div');
          nameEl.className='fw-chat-sender';
          nameEl.textContent=groupSender;
          body.append(nameEl,content);
          line.append(img,body);
        } else line.append(img,content);
      } else line.append(content,img);
      messages.appendChild(line);
    };
    // A quoted block: sender name + the quoted content, clamped to two lines. Used
    // both inside a reply bubble and on its own when the quote is sent without text.
    const buildQuoteBlock = (quote, extraClass) => {
      const box=document.createElement('div');
      box.className='fw-quote'+(extraClass?' '+extraClass:'');
      const name=String(quote&&quote.name||'').trim();
      const body=String(quote&&quote.text||'').trim();
      const text=document.createElement('span');
      text.className='fw-quote__text';
      text.textContent=name?name+'：'+body:body;
      box.appendChild(text);
      return box;
    };
    const makeQuoteOnlyRow = (message, side) => {
      const line=document.createElement('div');
      line.className='fw-chat-message fw-chat-message--'+side+' fw-chat-message--quote-only';
      line.__fwMessage=message||null;
      line.appendChild(buildQuoteBlock(message.quote,'fw-quote--standalone'));
      messages.appendChild(line);
    };
    const makeTransferBubble = (message, side) => {
      const incoming=side==='incoming';
      // A group transfer card belongs to a real speaker too, so it needs the same
      // sender name + member avatar treatment as a normal group bubble.
      const groupSender = incoming && friend.isGroup ? groupSenderName(message) : '';
      const line=document.createElement('div');
      line.className='fw-chat-message fw-chat-message--'+(incoming?'incoming':'outgoing')+' fw-chat-message--transfer';
      line.__fwMessage=message||null;
      // 2026-10-11: a transfer that has been collected or returned switches to the flat
      // status card (check / return arrow + state copy) exactly like the reference.
      const state=String(message&&message.status||'');
      if(state==='accepted'||state==='returned'){
        const ok=state==='accepted';
        const card=document.createElement('div');
        card.className='fw-tf-state '+(ok?'fw-tf-state--ok':'fw-tf-state--back');
        // The ring glyph is a cut-out of the supplied reference capture so the stroke
        // weight and the arrow/check geometry match pixel for pixel.
        card.innerHTML='<img class="fw-tf-state__icon" aria-hidden="true" alt="" draggable="false" src="'
          + (ok?'./assets/wechat-transfer-ok.png':'./assets/wechat-transfer-back.png') + '">'
          + '<div class="fw-tf-state__body"><strong></strong><em></em></div><span class="fw-tf-state__label">转账</span>';
        card.querySelector('strong').textContent='¥'+Number(message.amount||0).toFixed(2);
        card.querySelector('em').textContent=ok?(incoming?'已收款':'已被接收'):(incoming?'已退还':'已被退还');
        if(incoming)card.classList.add('fw-tf-state--in');
        // A pending transfer can be collected or returned from the sheet; once settled it
        // only opens the detail-less sheet again, so the tap stays inert.
        card.addEventListener('click',event=>{event.stopPropagation();});
        const avatar=document.createElement('img');
        avatar.className='fw-chat-message__avatar';
        if(incoming){
          avatar.dataset.fwFriendAvatar='1';
          avatar.alt=groupSender||label;
          avatar.src=groupSender?(groupMemberAvatar(groupSender)||friendAvatar):friendAvatar;
          avatar.onerror=()=>{avatar.onerror=null;avatar.src=bundledAvatarFor(groupSender||friend.name||'');};
          avatar.addEventListener('click',event=>{event.preventDefault();event.stopPropagation();openFriendProfile(friend);},true);
        }else{
          applySelfAvatar(avatar);
        }
        if(incoming){
          if(groupSender){
            const body=document.createElement('div');body.className='fw-chat-message__body';
            const nameEl=document.createElement('div');nameEl.className='fw-chat-sender';nameEl.textContent=groupSender;
            body.append(nameEl,card);line.append(avatar,body);
          } else line.append(avatar,card);
        } else line.append(card,avatar);
        messages.appendChild(line);
        return;
      }
      const card=document.createElement('div');
      card.className='fw-transfer-chat-card';
      const artwork=document.createElement('img');
      artwork.className='fw-transfer-chat-card__artwork';
      // The received card uses a left-pointing tail so it can face the friend avatar.
      artwork.src=incoming?'./assets/wechat-transfer-card-bg-incoming.png':'./assets/wechat-transfer-card-bg.png';
      artwork.alt=''; artwork.draggable=false; artwork.setAttribute('aria-hidden','true');
      const content=document.createElement('div'); content.className='fw-transfer-chat-card__content';
      const amount=document.createElement('strong'); amount.textContent='¥'+Number(message.amount||0).toFixed(2);
      const desc=document.createElement('span'); desc.textContent=String(message.note||'').trim()||(incoming?'转账给你':'你发起了一笔转账');
      content.append(amount,desc);
      const footer=document.createElement('div'); footer.className='fw-transfer-chat-card__footer'; footer.textContent='转账';
      card.append(artwork,content,footer);
      // Tapping a still-pending transfer opens the matching detail page; the side the
      // bubble currently sits on decides between 待对方收款 and 确认收款.
      if(!String(message&&message.status||'')){
        card.addEventListener('click',event=>{ event.stopPropagation(); openTransferDetail(message,friend); });
      }
      const img=document.createElement('img'); img.className='fw-chat-message__avatar';
      if(incoming){
        img.dataset.fwFriendAvatar='1'; img.alt=groupSender||label;
        img.src=groupSender?(groupMemberAvatar(groupSender)||friendAvatar):friendAvatar;
        img.onerror=()=>{
          const stage=img.dataset.fwAvatarStage||'origin';
          if(stage==='origin'){
            img.dataset.fwAvatarStage='api';
            const api=apiAvatarFor(groupSender||friend.name||'');
            if(api&&api!==img.src){ img.src=api; return; }
          }
          if(stage!=='bundled'){ img.dataset.fwAvatarStage='bundled'; img.src=bundledAvatarFor(groupSender||friend.name||''); }
        };
        img.addEventListener('click',event=>{
          event.preventDefault();event.stopPropagation();
          if(groupSender){ event.stopPropagation(); const target=resolveFriendByName(groupSender); openFriendProfile(target||friend); return; }
          openFriendProfile(friend);
        },true);
      }else{
        applySelfAvatar(img);
      }
      if(incoming){
        if(groupSender){
          const body=document.createElement('div');
          body.className='fw-chat-message__body';
          const nameEl=document.createElement('div');
          nameEl.className='fw-chat-sender';
          nameEl.textContent=groupSender;
          body.append(nameEl,card);
          line.append(img,body);
        } else line.append(img,card);
      } else line.append(card,img);
      messages.appendChild(line);
    };
    // 微信红包气泡：和转账共用同一张橙色底图（已把内置图标和"转账"字样涂平），
    // 上面画红包图标、"恭喜发财，大吉大利"和"微信红包"。
    const makeRedPacketBubble = (message, side) => {
      const incoming = side === 'incoming';
      const groupSender = incoming && friend.isGroup ? groupSenderName(message) : '';
      const line = document.createElement('div');
      // 被领过的红包整块变浅（背景、图标、底标都按参照图淡一档），并多一行"已被领完/已领取"。
      const claimInfo = (message && message.claim) || null;
      line.className = 'fw-chat-message fw-chat-message--' + (incoming ? 'incoming' : 'outgoing') + ' fw-chat-message--redpacket' + (claimInfo ? ' fw-chat-message--redpacket-claimed' : '');
      line.__fwMessage = message || null;
      const card = document.createElement('div');
      card.className = 'fw-hb-card';
      const artwork = document.createElement('img');
      artwork.className = 'fw-hb-card__artwork';
      artwork.src = claimInfo
        ? (incoming ? './assets/wechat-redpacket-card-bg-light-incoming.png' : './assets/wechat-redpacket-card-bg-light.png')
        : (incoming ? './assets/wechat-redpacket-card-bg-incoming.png' : './assets/wechat-redpacket-card-bg.png');
      artwork.alt = ''; artwork.draggable = false; artwork.setAttribute('aria-hidden', 'true');
      const icon = document.createElement('img');
      icon.className = 'fw-hb-card__icon';
      icon.src = claimInfo ? './assets/wechat-redpacket-icon-light.png' : './assets/wechat-redpacket-icon.png';
      icon.alt = ''; icon.draggable = false; icon.setAttribute('aria-hidden', 'true');
      const wish = document.createElement('span');
      wish.className = 'fw-hb-card__wish';
      wish.textContent = String(message && message.blessing || '').trim() || '恭喜发财，大吉大利';
      const label = document.createElement('span');
      label.className = 'fw-hb-card__label';
      label.textContent = '微信红包';
      card.append(artwork, icon, wish, label);
      if (claimInfo) {
        const state = document.createElement('span');
        state.className = 'fw-hb-card__state';
        // 对方发的红包我领了 → 已领取；自己发的被别人领完 → 已被领完。
        state.textContent = incoming ? '已领取' : '已被领完';
        card.append(state);
      }
      card.addEventListener('click', event => {
        event.stopPropagation();
        openRedPacketPage(message, friend, {
          selfName: getSelfName(), selfAvatar: getSelfAvatar(), selfAvatarValue: getSelfAvatarValue(), resolveSelf: resolveSelfAvatar,
          friendAvatar,
        });
      });
      const avatar = document.createElement('img');
      avatar.className = 'fw-chat-message__avatar';
      if (incoming) {
        avatar.dataset.fwFriendAvatar = '1';
        avatar.alt = groupSender || label;
        avatar.src = groupSender ? (groupMemberAvatar(groupSender) || friendAvatar) : friendAvatar;
        avatar.onerror = () => { avatar.onerror = null; avatar.src = bundledAvatarFor(groupSender || friend.name || ''); };
        avatar.addEventListener('click', event => {
          event.preventDefault(); event.stopPropagation();
          if (groupSender) { const target = resolveFriendByName(groupSender); openFriendProfile(target || friend); return; }
          openFriendProfile(friend);
        }, true);
      } else {
        applySelfAvatar(avatar);
      }
      if (incoming) {
        if (groupSender) {
          const body = document.createElement('div'); body.className = 'fw-chat-message__body';
          const nameEl = document.createElement('div'); nameEl.className = 'fw-chat-sender'; nameEl.textContent = groupSender;
          body.append(nameEl, card); line.append(avatar, body);
        } else line.append(avatar, card);
      } else line.append(card, avatar);
      messages.appendChild(line);
      if (message && message.claim) makeRedPacketNotice(message, incoming);
    };
    // "ly领取了你的红包"：整行居中，红包二字跟参照图一样是橙色。
    const makeRedPacketNotice = (message, incoming) => {
      const claim = message.claim || {};
      const claimer = String(claim.by || '').trim() || (incoming ? '你' : label);
      const owner = String(claim.owner || '').trim() || (incoming ? label : '你');
      const row = document.createElement('div');
      row.className = 'fw-hb-notice';
      const icon = document.createElement('img');
      icon.className = 'fw-hb-notice__icon';
      icon.src = './assets/wechat-redpacket-notice.png';
      icon.alt = ''; icon.draggable = false; icon.setAttribute('aria-hidden', 'true');
      const text = document.createElement('span');
      text.className = 'fw-hb-notice__text';
      text.appendChild(document.createTextNode(claimer + '领取了' + owner + '的'));
      const tail = document.createElement('em');
      tail.textContent = '红包';
      text.appendChild(tail);
      row.append(icon, text);
      messages.appendChild(row);
    };
    /* 2026-10-11 通话结果消息（参考图 1/2/3）：就是普通文字气泡 + 一个听筒图标 ——
       出方图标在右（通话时长 / 已取消），入方图标在左（已拒绝）；点"通话时长"能改时长，
       横向滑可以像别的消息一样换发送人（走同一套 setMessageSide）。 */
    const makeCallBubble = (message, side) => {
      const incoming = side === 'incoming';
      const line = document.createElement('div');
      line.className = 'fw-chat-message fw-chat-message--' + (incoming ? 'incoming' : 'outgoing') + ' fw-chat-message--call';
      line.__fwMessage = message || null;
      const bubble = document.createElement('div');
      bubble.className = 'fw-chat-message__bubble fw-call-msg';
      const icon = '<img class="fw-call-msg__icon" src="./assets/wechat-call-msg-icon.png" alt="" draggable="false" aria-hidden="true">';
      bubble.innerHTML = incoming
        ? icon + '<span class="fw-call-msg__text"></span>'
        : '<span class="fw-call-msg__text"></span>' + icon;
      bubble.querySelector('.fw-call-msg__text').textContent = callMessageText(message);
      if (callMessageVariant(message) === 'duration') {
        bubble.classList.add('fw-call-msg--editable');
        bubble.addEventListener('click', event => { event.stopPropagation(); editCallMessageDuration(message); });
      }
      const avatar = document.createElement('img');
      avatar.className = 'fw-chat-message__avatar';
      if (incoming) {
        avatar.dataset.fwFriendAvatar = '1';
        avatar.alt = label;
        avatar.src = friendAvatar;
        avatar.onerror = () => { avatar.onerror = null; avatar.src = bundledAvatarFor(friend.name || ''); };
        avatar.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); openFriendProfile(friend); }, true);
      } else {
        applySelfAvatar(avatar);
      }
      if (incoming) line.append(avatar, bubble); else line.append(bubble, avatar);
      messages.appendChild(line);
    };
    const drawMessages = (keepScrollTop=false) => {
      // 2026-10-19: 收款/转账后可能残留旧会话页。每次重绘前只保留当前页，
      // 防止旧页面的「打招呼 + 转账卡片」叠在当前页下面，看起来像消息重复。
      document.querySelectorAll('.fw-local-conversation').forEach(other => {
        if (other !== page) other.remove();
      });
      const previousScrollTop=messages.scrollTop;
      // 打招呼消息只由 greeting.mine / greeting.friend 渲染一次；
      // friend.messages 里任何同文案的旧副本都在这里清掉。
      const greetingTexts = new Set([
        String(greeting.mine.text || ('我是' + getSelfName())),
        String(greeting.friend.text || '我通过了你的朋友验证请求，现在我们可以开始聊天了'),
        '以上是打招呼的消息',
      ]);
      friend.messages = friend.messages.filter(item => item
        && item !== greeting.mine && item !== greeting.friend
        && item.type !== 'greeting'
        && !greetingTexts.has(String(item.text || '')));
      messages.replaceChildren();
      // 两条消息之间的最小间隔：不足这个时间就不插新的时间（微信的做法）。
      // 想改成 5 分钟只动这一个数字。
      const TIME_GAP_MS = 3 * 60 * 1000;
      if (friend.isGroup) {
        // Groups open with the join notice instead of the friend-greeting lines.
        const notice=document.createElement('div'); notice.className='fw-chat-notice';
        notice.textContent='你邀请 '+(Array.isArray(friend.members)?friend.members.join('、'):'')+' 加入了群聊';
        messages.appendChild(notice);
      } else {
        if (!greeting.mine.deleted) {
          makeTime('9月16日 14:11');
          makeBubble(greeting.mine.text || ('我是' + getSelfName()), messageSide(greeting.mine), friendAvatar, greeting.mine);
        }
        if (!greeting.friend.deleted) {
          makeTime('9月16日 14:23');
          const notice=document.createElement('div'); notice.className='fw-chat-notice';
          notice.textContent='以上是打招呼的消息'; messages.appendChild(notice);
          makeBubble(greeting.friend.text || '我通过了你的朋友验证请求，现在我们可以开始聊天了', messageSide(greeting.friend), friendAvatar, greeting.friend);
        }
      }
      // 2026-10-17：时间分割线改成微信那套 —— **跟上一条消息隔得够久才插一条**。
      // 以前是「分钟数一变就插一条」，连着聊十句能插出七八个时间，看着很吵；
      // 现在的规则：距上一条消息在 TIME_GAP_MS（3 分钟）之内就不再显示时间。
      let lastMessageAt = 0;
      for (const message of friend.messages) {
        if (message.timeLabel) {
          makeTime(message.timeLabel);
        } else if (message.createdAt) {
          const at = new Date(message.createdAt).getTime();
          if (Number.isFinite(at)) {
            // lastMessageAt=0 表示这是第一条（没有任何可比较的对象）→ 照常显示时间。
            // gap<0 说明历史是倒着排的（批量生成的会话可能出现），也当作新一段，别把时间吞掉。
            const gap = lastMessageAt ? at - lastMessageAt : 0;
            if (!lastMessageAt || gap >= TIME_GAP_MS || gap < 0) {
              makeTime(new Date(at).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false}));
            }
            lastMessageAt = at;
          }
        }
        if (message.type === 'notice') {
          const notice = document.createElement('div');
          notice.className = 'fw-chat-notice';
          notice.textContent = message.text || '';
          messages.appendChild(notice);
          continue;
        }
        const side=messageSide(message);
        if(message.type==='transfer')makeTransferBubble(message,side);
        else if(message.type==='redpacket')makeRedPacketBubble(message,side);
        else if(message.type==='call')makeCallBubble(message,side);
        else if(message.quoteOnly)makeQuoteOnlyRow(message,side);
        else makeBubble(message.text,side,side==='incoming'?friendAvatar:getSelfAvatar(),message);
      }
      const scrollToLatest=()=>{messages.scrollTop=messages.scrollHeight;};
      const restoreScrollTop=()=>{messages.scrollTop=previousScrollTop;};
      // Re-drawing after a swipe must not throw the reader back to the newest
      // message, so only the initial open scrolls to the bottom.
      const settle=keepScrollTop?restoreScrollTop:scrollToLatest;
      settle();
      requestAnimationFrame(()=>{settle();requestAnimationFrame(settle);});
      setTimeout(settle,120);
      messages.querySelectorAll('img').forEach(image=>{if(!image.complete)image.addEventListener('load',settle,{once:true});});
    };
    page.__fwDrawMessages=drawMessages;
    // 先把「我」页同款头像解析出来再画第一屏，避免打开聊天时先闪一张随机脸；
    // 解析失败/超时也必须画，不能让会话空着。
    const paintFirstScreen = () => {
      if (!page.isConnected || messages.childElementCount) return;
      drawMessages();
    };
    Promise.race([
      resolveSelfAvatarSource(),
      new Promise(resolve => setTimeout(resolve, 300)),
    ]).then(paintFirstScreen, paintFirstScreen);
    setTimeout(paintFirstScreen, 350);
    // Swipe any message sideways to hand it to the other speaker: right keeps it
    // with "我" (the default), left moves it to the friend. The side is written onto
    // the stored message, so it is still there next time the conversation opens.
    const SWIPE_TRIGGER=44;
    const setMessageSide=(message,side)=>{
      if(!message)return false;
      const next=side==='incoming'?'incoming':'outgoing';
      if(messageSide(message)===next)return false;
      message.side=next;
      syncTransferBill(message);
      saveState();
      return true;
    };
    let swipeState=null;
    let pressState=null;
    const cancelPress=()=>{ if(pressState){ clearTimeout(pressState.timer); pressState=null; } };
    const clearSwipeRow=row=>{if(row){row.classList.remove('fw-chat-message--swiping');row.style.removeProperty('transform');}};
    messages.addEventListener('pointerdown',event=>{
      if(event.pointerType==='mouse'&&event.button!==0)return;
      const row=event.target instanceof Element?event.target.closest('.fw-chat-message'):null;
      if(!row||!row.__fwMessage)return;
      swipeState={row,message:row.__fwMessage,id:event.pointerId,x:event.clientX,y:event.clientY,dx:0,active:false};
      try { row.setPointerCapture(event.pointerId); } catch (_) {}
      cancelPress();
      const timer=setTimeout(()=>{
        pressState=null;
        swipeState=null;
        clearSwipeRow(row);
        openMessageMenu(row,row.__fwMessage);
      },480);
      pressState={row,timer,id:event.pointerId,x:event.clientX,y:event.clientY};
    },{passive:true});
    messages.addEventListener('pointermove',event=>{
      if(pressState&&event.pointerId===pressState.id&&Math.abs(event.clientX-pressState.x)+Math.abs(event.clientY-pressState.y)>10)cancelPress();
      if(!swipeState||event.pointerId!==swipeState.id)return;
      const dx=event.clientX-swipeState.x,dy=event.clientY-swipeState.y;
      if(!swipeState.active){
        if(Math.abs(dx)<10)return;
        // A mostly-vertical drag belongs to the message list scroll, not to us.
        if(Math.abs(dx)<=Math.abs(dy)){clearSwipeRow(swipeState.row);swipeState=null;return;}
        swipeState.active=true;
        swipeState.row.classList.add('fw-chat-message--swiping');
      }
      swipeState.dx=dx;
      swipeState.row.style.transform='translateX('+Math.max(-72,Math.min(72,dx*0.55)).toFixed(1)+'px)';
      if(event.cancelable)event.preventDefault();
    },{passive:false});
    // 2026-10-19: 滑动只改一行时，CSS 尾巴会跟着 row class 变，但转账/红包/通话
    // 卡片的方向是创建时写死的（背景图、状态 class、图标顺序）。这里在改边之后
    // 把卡片自己的方向也同步过来，避免「人已经到左边了，箭头还朝右」。
    const syncMessageRowSide = (row, side) => {
      const incoming = side === 'incoming';
      const message = row.__fwMessage || {};
      const transferArt = row.querySelector('.fw-transfer-chat-card__artwork');
      if (transferArt) {
        transferArt.src = incoming
          ? './assets/wechat-transfer-card-bg-incoming.png'
          : './assets/wechat-transfer-card-bg.png';
        const desc = row.querySelector('.fw-transfer-chat-card__content span');
        if (desc && !String(message.note || '').trim()) {
          desc.textContent = incoming ? '转账给你' : '你发起了一笔转账';
        }
      }
      const stateCard = row.querySelector('.fw-tf-state');
      if (stateCard) {
        stateCard.classList.toggle('fw-tf-state--in', incoming);
        const status = String(message.status || 'accepted');
        const em = stateCard.querySelector('em');
        if (em) em.textContent = status === 'returned'
          ? (incoming ? '已退还' : '已被退还')
          : (incoming ? '已收款' : '已被接收');
      }
      const redPacketArt = row.querySelector('.fw-hb-card__artwork');
      if (redPacketArt) {
        const claimed = !!message.claim;
        redPacketArt.src = './assets/' + (claimed
          ? (incoming ? 'wechat-redpacket-card-bg-light-incoming.png' : 'wechat-redpacket-card-bg-light.png')
          : (incoming ? 'wechat-redpacket-card-bg-incoming.png' : 'wechat-redpacket-card-bg.png'));
        const stateText = row.querySelector('.fw-hb-card__state');
        if (stateText) stateText.textContent = incoming ? '已领取' : '已被领完';
      }
      const call = row.querySelector('.fw-call-msg');
      if (call) {
        const icon = call.querySelector('.fw-call-msg__icon');
        const text = call.querySelector('.fw-call-msg__text');
        if (icon && text) {
          if (incoming) call.insertBefore(icon, text); else call.appendChild(icon);
        }
      }
    };
    const finishSwipe=(event,cancelled)=>{
      cancelPress();
      const swipe=swipeState;
      if(!swipe||(event&&event.pointerId!==swipe.id))return;
      swipeState=null;
      const dx=swipe.active?swipe.dx:0;
      clearSwipeRow(swipe.row);
      if(cancelled||Math.abs(dx)<SWIPE_TRIGGER)return;
      const target=dx<0?'incoming':'outgoing';
      if(!setMessageSide(swipe.message,target))return;
      // 原地只改这一条：不重绘整个消息列表，不可能多出副本。
      const row=swipe.row;
      row.classList.toggle('fw-chat-message--incoming', target==='incoming');
      row.classList.toggle('fw-chat-message--outgoing', target==='outgoing');
      const avatar=row.querySelector('.fw-chat-message__avatar');
      const content=[...row.children].find(el=>el!==avatar);
      if(avatar&&content){
        if(target==='incoming'){ row.prepend(avatar); avatar.after(content); }
        else { row.append(content,avatar); }
        paintMessageAvatar(avatar, target);
      }
      syncMessageRowSide(row, target);
      showToast(target==='incoming'?'已改为对方发送':'已改为我发送');
    };
    messages.addEventListener('pointerup',event=>finishSwipe(event,false));
    messages.addEventListener('pointercancel',event=>finishSwipe(event,true));

    // ---- long-press menu, quoting and forwarding -------------------------------
    const MENU_ICONS=[
      ['copy','复制'],['forward','转发'],['favorite','收藏'],['delete','删除'],['multi','多选'],
      ['quote','引用'],['<svg viewBox="0 0 24 24" aria-hidden="true"><g fill="#fff"><path d="M4.4 16.5 15.2 5.7l3.1 3.1L7.5 19.6l-4.1 1z"/><path d="M16.4 4.5 18 2.9a1.7 1.7 0 0 1 2.4 0l.7.7a1.7 1.7 0 0 1 0 2.4l-1.6 1.6z"/></g></svg>','修改'],
      ['remind','提醒'],['search','搜一搜'],['read','连续朗读']
    ];
    const messageQuoteText=message=>{
      if(!message)return '';
      // The two opening lines keep their text in the renderer, not on the record.
      if(message===greeting.mine)return greeting.mine.text||('我是'+getSelfName());
      if(message===greeting.friend)return greeting.friend.text||'我通过了你的朋友验证请求，现在我们可以开始聊天了';
      if(message.type==='transfer')return '¥'+Number(message.amount||0).toFixed(2);
      const text=String(message.text==null?'':message.text);
      return text.startsWith('data:image/')?'[图片]':text;
    };
    const messageSpeakerName=message=>messageSide(message)==='incoming'
      ? String(friend.remark||friend.name||'对方')
      : getSelfName();
    const copyPlainText=text=>{
      try{
        const field=document.createElement('textarea');
        field.value=text;
        field.style.cssText='position:fixed;top:-1000px;left:0;opacity:0';
        document.body.appendChild(field);
        field.select();
        const ok=document.execCommand('copy');
        field.remove();
        return ok;
      }catch(_){ return false; }
    };
    let messageMenu=null;
    let messageMenuRow=null;
    let messageMenuOpenedAt=0;
    // Tells the native layer to stop watching touches while the menu owns them.
    const notifyMenuOpen=open=>{
      try{ window.DoutuApp && window.DoutuApp.setMessageMenuOpen && window.DoutuApp.setMessageMenuOpen(!!open); }catch(_){ /* no bridge */ }
    };
    const closeMessageMenu=()=>{
      if(!messageMenu)return;
      messageMenu.remove();
      messageMenu=null;
      messageMenuRow=null;
      notifyMenuOpen(false);
      messages.querySelectorAll('.fw-chat-message--menu-source').forEach(row=>row.classList.remove('fw-chat-message--menu-source'));
    };
    const openMessageMenu=(row,message)=>{
      // The 480ms page timer and the ~500ms native bridge both fire for one press.
      // Re-opening for the same row would recreate the panel and make it flicker.
      if(messageMenu&&messageMenuRow===row)return;
      closeMessageMenu();
      const pageRect=page.getBoundingClientRect();
      const rowRect=row.getBoundingClientRect();
      const backdrop=document.createElement('div');
      backdrop.className='fw-msg-menu-backdrop';
      const menu=document.createElement('div');
      menu.className='fw-msg-menu';
      menu.setAttribute('role','menu');
      const buildRow=(items,extra)=>{
        const line=document.createElement('div');
        line.className='fw-msg-menu__row'+(extra?' '+extra:'');
        items.forEach(([icon,label])=>{
          const button=document.createElement('button');
          button.type='button';
          button.className='fw-msg-menu__item';
          button.dataset.fwMsgAction=label;
          const mark=icon.charAt(0)==='<'?icon:'<img alt="" draggable="false" src="./assets/wechat-message-menu/'+icon+'.png">';
          button.innerHTML='<span class="fw-msg-menu__icon">'+mark+'</span><span class="fw-msg-menu__label">'+label+'</span>';
          line.appendChild(button);
        });
        return line;
      };
      menu.append(buildRow(MENU_ICONS.slice(0,5)),buildRow(MENU_ICONS.slice(5),'fw-msg-menu__row--second'));
      const caret=document.createElement('span');
      caret.className='fw-msg-menu__caret';
      menu.appendChild(caret);
      backdrop.appendChild(menu);
      page.appendChild(backdrop);
      row.classList.add('fw-chat-message--menu-source');
      // Anchor the caret under the pressed message and keep the panel on screen.
      const menuWidth=menu.offsetWidth, menuHeight=menu.offsetHeight;
      const margin=Math.round(pageRect.width*0.03);
      // The row spans the full column width, so anchor on the visible bubble/card
      // instead of the row box or the caret would point at the screen centre.
      const anchor=row.querySelector('.fw-chat-message__bubble, .fw-transfer-chat-card, .fw-quote')||row;
      const anchorRect=anchor.getBoundingClientRect();
      const centerX=anchorRect.left+anchorRect.width/2-pageRect.left;
      let left=Math.round(centerX-menuWidth*0.339);
      left=Math.max(margin,Math.min(Math.round(pageRect.width-menuWidth-margin),left));
      const caretX=Math.max(20,Math.min(menuWidth-20,Math.round(centerX-left)));
      let top=Math.round(rowRect.top-pageRect.top-menuHeight-10);
      if(top<8){ top=Math.round(rowRect.bottom-pageRect.top+10); menu.classList.add('fw-msg-menu--below'); }
      menu.style.left=left+'px';
      menu.style.top=top+'px';
      menu.style.setProperty('--fw-menu-caret-x',caretX+'px');
      messageMenu=backdrop;
      messageMenuRow=row;
      messageMenuOpenedAt=Date.now();
      notifyMenuOpen(true);
      requestAnimationFrame(()=>menu.classList.add('is-visible'));
      // A press that lasts longer than Chromium's long-press threshold never turns
      // into a click, so the menu activates on release instead (and the click path
      // stays for mouse input). `fired` keeps a normal tap from running twice.
      let pressedButton=null;
      let fired=false;
      const activate=button=>{
        if(!button||fired)return;
        fired=true;
        const action=button.dataset.fwMsgAction;
        closeMessageMenu();
        handleMessageAction(action,message);
      };
      menu.addEventListener('pointerdown',event=>{
        event.stopPropagation();
        pressedButton=event.target instanceof Element?event.target.closest('[data-fw-msg-action]'):null;
      },true);
      menu.addEventListener('pointerup',event=>{
        const button=event.target instanceof Element?event.target.closest('[data-fw-msg-action]'):null;
        if(!button||!pressedButton||!pressedButton.contains(event.target)){ pressedButton=null; return; }
        event.preventDefault();
        event.stopPropagation();
        activate(button);
      },true);
      menu.addEventListener('click',event=>{
        const button=event.target instanceof Element?event.target.closest('[data-fw-msg-action]'):null;
        if(!button)return;
        event.preventDefault();
        event.stopPropagation();
        activate(button);
      });
    };
    const removeMessage=message=>{
      const list=friend.messages;
      const at=list.indexOf(message);
      if(at>=0){ list.splice(at,1); return true; }
      // The opening lines are not part of the message list; hide them instead.
      if(message===greeting.mine||message===greeting.friend){ message.deleted=true; return true; }
      return false;
    };
    const openForwardPicker=message=>{
      if(document.querySelector('.fw-forward-picker'))return;
      const layer=document.createElement('div');
      layer.className='fw-forward-picker';
      layer.innerHTML='<section class="fw-forward-picker__panel"><header><button type="button" data-forward-close>取消</button><strong>选择</strong><span></span></header><div class="fw-forward-picker__list"></div></section>';
      const list=layer.querySelector('.fw-forward-picker__list');
      const targets=(Array.isArray(state.contacts)?state.contacts:[]).filter(item=>item&&item.id&&friendLabel(item));
      if(!targets.length){
        const empty=document.createElement('p');
        empty.className='fw-forward-picker__empty';
        empty.textContent='还没有可以转发的好友';
        list.appendChild(empty);
      }
      targets.forEach(target=>{
        const row=document.createElement('button');
        row.type='button';
        row.className='fw-forward-picker__row';
        const img=document.createElement('img');
        img.alt='';
        img.src=getStableFriendAvatar(target,target.avatar||CONTACT_FALLBACKS[0].avatar);
        const name=document.createElement('span');
        name.textContent=friendLabel(target);
        row.append(img,name);
        row.addEventListener('click',event=>{
          event.preventDefault();
          event.stopPropagation();
          const copy=JSON.parse(JSON.stringify(message));
          delete copy.id;
          copy.side='outgoing';
          copy.createdAt=Date.now();
          target.messages=Array.isArray(target.messages)?target.messages:[];
          target.messages.push(copy);
          saveState();
          layer.remove();
          showToast('已转发');
        });
        list.appendChild(row);
      });
      layer.querySelector('[data-forward-close]').addEventListener('click',()=>layer.remove());
      layer.addEventListener('pointerdown',event=>{ if(event.target===layer)layer.remove(); });
      document.body.appendChild(layer);
    };
    // The quote is kept in memory only: it lives from "引用" until the message is sent.
    let pendingQuote=null;
    let editingMessage=null;
    const quoteBar=document.createElement('div');
    quoteBar.className='fw-quote-bar';
    quoteBar.hidden=true;
    quoteBar.innerHTML='<div class="fw-quote-bar__box"><span class="fw-quote-bar__text"></span><button type="button" class="fw-quote-bar__close" aria-label="取消引用">✕</button></div>';
    page.querySelector('.fw-local-conversation__composer').after(quoteBar);
    const composer=page.querySelector('.fw-local-conversation__composer');
    // The "+" turns into a green 发送 button as soon as there is something to send.
    const syncComposerMode=()=>{
      composer.classList.toggle('is-send-mode', !!editingMessage || pendingQuote!==null || input.value.trim().length>0);
    };
    const renderQuoteBar=()=>{
      quoteBar.hidden=!pendingQuote;
      syncComposerMode();
      if(!pendingQuote)return;
      quoteBar.querySelector('.fw-quote-bar__text').textContent=pendingQuote.name+'：'+pendingQuote.text;
    };
    quoteBar.querySelector('.fw-quote-bar__close').addEventListener('click',event=>{
      event.preventDefault();
      event.stopPropagation();
      pendingQuote=null;
      renderQuoteBar();
    });
    // Focusing a WebView input is not enough to raise the IME on Android, so the
    // page also asks the native bridge to show the soft keyboard.
    const raiseKeyboard=()=>{
      try{ input.focus(); }catch(_){ /* no input yet */ }
      try{ window.DoutuApp && window.DoutuApp.showSoftKeyboard && window.DoutuApp.showSoftKeyboard(); }catch(_){ /* no bridge */ }
    };
    const focusComposer=()=>{
      raiseKeyboard();
      requestAnimationFrame(raiseKeyboard);
      setTimeout(raiseKeyboard,120);
      setTimeout(raiseKeyboard,320);
    };
    // Anything whose visible body is plain text can be rewritten: stored text
    // messages, replies with text, and the two opening lines (whose text is
    // generated at render time until it is edited once).
    const isEditableText=message=>{
      if(!message)return false;
      if(message.type==='transfer'||message.type==='image')return false;
      if(message.quoteOnly)return false;
      return typeof message.text==='string'||message===greeting.mine||message===greeting.friend;
    };
    const startQuote=message=>{
      pendingQuote={name:messageSpeakerName(message),text:messageQuoteText(message)};
      renderQuoteBar();
      focusComposer();
    };
    const handleMessageAction=(action,message)=>{
      if(!message)return;
      if(action==='复制'){
        const text=messageQuoteText(message);
        const finish=ok=>showToast(ok?'已复制':'复制失败');
        try{
          if(navigator.clipboard&&navigator.clipboard.writeText){
            navigator.clipboard.writeText(text).then(()=>finish(true),()=>finish(copyPlainText(text)));
            return;
          }
        }catch(_){ /* fall back to the legacy copy path */ }
        finish(copyPlainText(text));
        return;
      }
      if(action==='删除'){
        if(removeMessage(message)){ saveState(); drawMessages(true); showToast('已删除'); }
        return;
      }
      if(action==='引用'){ startQuote(message); return; }
      if(action==='转发'){ openForwardPicker(message); return; }
      if(action==='修改'){
        // 通话消息不是文字，点「修改」直接切换样式：通话时长 / 已拒绝 / 已取消。
        if(message.type==='call'){ cycleCallMessageVariant(message); return; }
        if(!isEditableText(message)){ showToast('该消息不支持修改'); return; }
        editingMessage=message;
        input.value=messageQuoteText(message);
        syncComposerMode();
        focusComposer();
        const end=input.value.length;
        try{ input.setSelectionRange(end,end); }catch(_){ /* not focusable yet */ }
        return;
      }
      // 收藏 / 多选 / 提醒 / 搜一搜 / 连续朗读 only dismiss the menu.
    };
    openMessageMenuForRow = row => {
      if(!row||!row.__fwMessage)return false;
      openMessageMenu(row,row.__fwMessage);
      return true;
    };
    // "Tap anywhere outside closes the menu". Listening on the page (capture)
    // rather than only on the backdrop also covers WebView builds that retarget
    // the press at a message row instead of the backdrop. All three input kinds
    // are handled because a tap may arrive as touch, mouse or click.
    const closeOnOutsidePress = event => {
      if(!messageMenu)return;
      // Swallow the tail of the long-press gesture that just opened the menu: the
      // finger lifting can still produce a click on the message it was pressing.
      if(Date.now()-messageMenuOpenedAt<450)return;
      const panel=messageMenu.querySelector('.fw-msg-menu');
      if(panel&&panel.contains(event.target))return;
      closeMessageMenu();
    };
    ['pointerdown','mousedown','click'].forEach(type=>page.addEventListener(type,closeOnOutsidePress,true));
    page.querySelector('.fw-chat-back').addEventListener('click',closeLocalConversation);
    page.querySelector('.fw-chat-more').addEventListener('click',()=>openChatInfo(friend));
    const input=page.querySelector('.fw-local-conversation__composer input');
    const emojiButton=page.querySelector('.fw-chat-emoji');
    const plusButton=page.querySelector('.fw-chat-plus');
    const emojiPicker=document.createElement('section');
    emojiPicker.className='fw-emoji-picker'; emojiPicker.setAttribute('aria-label','表情面板'); emojiPicker.hidden=true;
    const emojiList=['😊','😂','🥰','😍','😘','😉','😎','😭','😅','🤣','🙂','🙃','😋','😜','🤔','🤗','😴','😮','😡','🥺','👍','👎','👏','🙏','👌','✌️','🤝','💪','❤️','💕','💖','💔','🎉','🎂','🌹','☀️','🌙','🍀','🐱','🐶','🐼','💯','🔥','✨','🎁','✅'];
    emojiPicker.innerHTML='<div class="fw-emoji-picker__title">最近使用</div><div class="fw-emoji-picker__grid"></div><div class="fw-emoji-picker__footer"><button type="button">◷</button><button type="button">☺</button><button type="button">♥</button><button type="button">✋</button><button type="button">＋</button></div>';
    const emojiGrid=emojiPicker.querySelector('.fw-emoji-picker__grid'); let emojiGridReady=false;
    const ensureEmojiGrid=()=>{if(emojiGridReady)return;emojiGridReady=true;const fragment=document.createDocumentFragment();emojiList.forEach(value=>{const choice=document.createElement('button');choice.type='button';choice.className='fw-emoji-picker__item';choice.textContent=value;fragment.appendChild(choice);});emojiGrid.appendChild(fragment);};
    page.appendChild(emojiPicker);
    const featurePanel=document.createElement('section'); featurePanel.className='fw-chat-feature-panel'; featurePanel.setAttribute('aria-label','聊天功能'); featurePanel.setAttribute('aria-hidden','true');
    const featurePages=[[['相册','album.png'],['拍摄','camera.png'],['视频通话','video.png'],['位置','location.png'],['红包','redpacket.png'],['礼物','gift.png'],['转账','transfer.png'],['收藏','favorite.png']],[['个人名片','personal-card.png'],['文件','file.png'],['音乐','music.png']]];
    featurePanel.innerHTML='<div class="fw-chat-feature-pages"></div><div class="fw-chat-feature-dots"><i class="active"></i><i></i></div>';
    const pagesHost=featurePanel.querySelector('.fw-chat-feature-pages');
    featurePages.forEach((items,index)=>{const grid=document.createElement('div');grid.className='fw-chat-feature-grid';grid.dataset.page=String(index);items.forEach(([label,glyph])=>{const button=document.createElement('button');button.type='button';button.className='fw-chat-feature-item';button.dataset.action=label;const iconContent=/^[a-z-]+\.png$/.test(glyph)?'<img src="./assets/wechat-feature-icons/'+glyph+'" alt="" draggable="false">':glyph;button.innerHTML='<span class="fw-chat-feature-icon" aria-hidden="true">'+iconContent+'</span><span>'+label+'</span>';grid.appendChild(button);});pagesHost.appendChild(grid);});
    page.appendChild(featurePanel);
    page.addEventListener('click',event=>{if(featurePanel.classList.contains('is-open')&&(event.target.closest('.fw-local-conversation__messages, .fw-local-conversation__header')||(!event.target.closest('.fw-chat-feature-panel, .fw-local-conversation__composer'))))setFeatures(false)},true);
    const dots=[...featurePanel.querySelectorAll('.fw-chat-feature-dots i')];
    const showPage=index=>{pagesHost.style.transform='translateX('+(-index*100)+'%)';dots.forEach((dot,i)=>dot.classList.toggle('active',i===index));featurePanel.dataset.page=String(index);};
    const setFeatures=open=>{featurePanel.classList.toggle('is-open',open);featurePanel.setAttribute('aria-hidden',String(!open));plusButton.setAttribute('aria-expanded',String(open));if(open){emojiPicker.hidden=true;emojiButton.setAttribute('aria-expanded','false');showPage(0);input.blur();}};
    const setEmoji=open=>{emojiPicker.hidden=!open;emojiButton.setAttribute('aria-expanded',String(open));if(open){setFeatures(false);ensureEmojiGrid();input.blur();}};
    emojiButton.setAttribute('aria-expanded','false');plusButton.setAttribute('aria-expanded','false');
    emojiButton.addEventListener('click',()=>setEmoji(emojiPicker.hidden)); plusButton.addEventListener('click',()=>setFeatures(!featurePanel.classList.contains('is-open')));
    emojiPicker.addEventListener('click',event=>{const choice=event.target.closest('.fw-emoji-picker__item');if(!choice)return;const start=input.selectionStart??input.value.length,end=input.selectionEnd??input.value.length;input.value=input.value.slice(0,start)+choice.textContent+input.value.slice(end);const cursor=start+choice.textContent.length;input.setSelectionRange(cursor,cursor);input.focus();});
    input.addEventListener('focus',()=>{setEmoji(false);setFeatures(false);});
    input.addEventListener('input',syncComposerMode);
    let touchX=0;featurePanel.addEventListener('touchstart',event=>{touchX=event.touches[0]?.clientX||0;},{passive:true});featurePanel.addEventListener('touchend',event=>{const delta=(event.changedTouches[0]?.clientX||touchX)-touchX;const current=Number(featurePanel.dataset.page||0);if(Math.abs(delta)>45)showPage(Math.max(0,Math.min(1,current+(delta<0?1:-1))));},{passive:true});
    featurePanel.addEventListener('click',event=>{const action=event.target.closest('[data-action]')?.dataset.action;if(!action){if(!event.target.closest('.fw-chat-feature-dots'))setFeatures(false);return;}if(action==='拍摄'){setFeatures(false);openCameraCapture();return;}if(action==='相册'){const picker=document.createElement('input');picker.type='file';picker.accept='image/*';picker.style.display='none';picker.addEventListener('change',()=>{setFeatures(false);showToast(picker.files?.[0]?'已选择图片':'未选择内容');picker.remove();});document.body.appendChild(picker);picker.click();return;}setFeatures(false);showToast(action==='收藏'?'已收藏当前聊天':action+'功能已打开');});
    page.querySelector('.fw-chat-voice').addEventListener('click',()=>showToast('语音输入功能未接入'));
    page.querySelector('.fw-chat-mic').addEventListener('click',()=>showToast('语音转文字功能未接入'));
    page.querySelector('.fw-local-conversation__composer').addEventListener('submit', event => {
      event.preventDefault(); const text=input.value.trim();
      // Editing from the long-press menu rewrites the stored message in place.
      if(editingMessage){
        if(!text){ showToast('内容不能为空'); return; }
        editingMessage.text=text;
        editingMessage=null;
        saveState(); input.value=''; syncComposerMode(); drawMessages(true);
        const edited=findConversationRow(friend);
        const editedPreview=edited?.querySelector('.fw-local-friend-copy small'); if(editedPreview)editedPreview.textContent=text;
        showToast('已修改');
        return;
      }
      if(!text&&!pendingQuote)return;
      const entry={createdAt:Date.now()};
      if(text)entry.text=text;
      if(pendingQuote){
        entry.quote={...pendingQuote};
        // Quoting with nothing typed sends the quote block on its own.
        if(!text)entry.quoteOnly=true;
      }
      // 2026-10-16：这段会话是本地叠加层，消息只挂在 friend 对象上，而 saveState() 只存
      // state.contacts 里的那些 friend。从「通讯录 → 发消息」进来的 friend 不在里面，
      // 于是发出去的消息杀掉后台就没了（实测：localStorage 里只有 chats、contacts 是空的）。
      // 发第一条时把它登记进 state.contacts（和 ensureChatFriend 一个做法），整段对话从此跟着存档走。
      registerConversationFriend(friend);
      friend.messages.push(entry);
      pendingQuote=null; renderQuoteBar(); syncComposerMode();
      saveState(); input.value=''; drawMessages();
      const row=findConversationRow(friend);
      const preview=row?.querySelector('.fw-local-friend-copy small'); if(preview)preview.textContent=text||String(entry.quote&&entry.quote.text||'');
    });
    if (selfAvatarSyncTimer) clearInterval(selfAvatarSyncTimer);
    selfAvatarSyncTimer = setInterval(() => {
      if (!page.isConnected) { clearInterval(selfAvatarSyncTimer); selfAvatarSyncTimer = null; return; }
      const profile = getSelfProfile();
      const currentAvatar = getSelfAvatar();
      const currentName = String(profile.nickname || profile.name || '林克');
      page.querySelectorAll('img.fw-chat-message__avatar[data-fw-self-avatar="1"]').forEach(img => {
        const savedAvatar = img.dataset.fwAvatarValue || '';
        if (savedAvatar !== currentAvatar) {
          // 资料页改了头像：清掉解析缓存，让聊天里所有的「我」立刻重新解析成同一张。
          // 这里只换解析结果，不先铺占位图，避免把全局监听刚设好的新头像又盖回旧图。
          selfAvatarSource = '';
          selfAvatarSourceKey = null;
          selfAvatarSourceTask = null;
          img.dataset.fwAvatarValue = currentAvatar;
          resolveSelfAvatarSource().then(src => { if (src && img.isConnected) img.src = src; });
        }
        img.alt = currentName;
        img.title = currentName;
      });
    }, 700);
    page.querySelectorAll('img.fw-chat-message__avatar[data-fw-self-avatar="1"]').forEach(img => { img.dataset.fwAvatarValue = getSelfAvatar(); });
    setTimeout(applyState, 0);
    });
  }

  // 2026-10-10: the dialogue rows show a real preview + a WeChat-style timestamp for contacts
  // that actually have messages (batch-added records), so a fabricated history reads like a
  // genuine conversation instead of the fixed 「刚刚 / 你们已经是好友了」 placeholder.
  function chatListPreview(friend) {
    if (friend?.payAssistant && !(friend.messages || []).length) return '欢迎使用微信收款服务！';
    const list = Array.isArray(friend?.messages) ? friend.messages : [];
    const last = list[list.length - 1];
    if (!last) {
      // A chat whose history was cleared (or that only carries fabricated records) keeps its
      // row but shows no preview, like an empty conversation.
      if (friend?.greeting && friend.greeting.friend && friend.greeting.friend.deleted) return '';
      return '你们已经是好友了，打个招呼吧';
    }
    const text = String(last.text || '');
    if (last.type === 'video' || /^data:video\//i.test(text)) return '[视频]';
    if (last.type === 'image' || /^data:image\//i.test(text)) return '[图片]';
    if (last.type === 'transfer' || last.transferAmount) return '[转账]';
    return text ? text.replace(/\s+/g, ' ').slice(0, 40) : '[消息]';
  }
  function chatListStamp(friend) {
    const list = Array.isArray(friend?.messages) ? friend.messages : [];
    const last = list[list.length - 1];
    const stamp = Number(last?.createdAt) || 0;
    if (!stamp) return friend?.greeting && friend.greeting.friend && friend.greeting.friend.deleted ? '' : '刚刚';
    const now = new Date();
    const then = new Date(stamp);
    const minutes = Math.floor((now.getTime() - stamp) / 60000);
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + '分钟前';
    const sameDay = now.getFullYear() === then.getFullYear() && now.getMonth() === then.getMonth() && now.getDate() === then.getDate();
    if (sameDay) return String(then.getHours()).padStart(2, '0') + ':' + String(then.getMinutes()).padStart(2, '0');
    const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
    if (then.getFullYear() === yesterday.getFullYear() && then.getMonth() === yesterday.getMonth() && then.getDate() === yesterday.getDate()) return '昨天';
    return (then.getMonth() + 1) + '月' + then.getDate() + '日';
  }
  function findChatFriendByName(name) {
    const target = String(name || '').trim();
    if (!target) return null;
    return (Array.isArray(state.contacts) ? state.contacts : [])
      .find(friend => String(friend?.remark || friend?.name || '').trim() === target) || null;
  }
  // Finds the contact behind a chat-list row, or creates one so the record has somewhere to go.
  function ensureChatFriend(name) {
    const target = String(name || '').trim();
    if (!target) return null;
    const existing = findChatFriendByName(target);
    if (existing) return existing;
    // A bundled demo conversation with the same name would otherwise swallow the row, so hide
    // it and let the fabricated history own that name.
    const demoRow = getRows().find(row => getChatInfo(row)?.name === target);
    if (demoRow && demoRow.dataset.fwLocalFriend !== '1') {
      const record = chatRecord(target);
      record.deleted = true;
      record.hidden = true;
    }
    const friend = {
      id: 'chat-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6),
      name: target, wechatId: '', remark: '', avatar: localPhotoFor(target),
      starred: false, messages: [], createdAt: Date.now(),
    };
    state.contacts = Array.isArray(state.contacts) ? state.contacts : [];
    state.contacts.unshift(friend);
    return friend;
  }
  function ensureLocalFriendRows() {
    const friends = (Array.isArray(state.contacts) ? state.contacts : []).filter(friend => friend?.id && friend.name).slice().sort((a,b) => String(a.remark || a.name).localeCompare(String(b.remark || b.name), 'zh-CN-u-co-pinyin'));
    const rows = getRows();
    const list = document.querySelector('.wechat-dialogue-list');
    const template = rows.find(row => row.dataset.fwLocalFriend !== '1' && row.dataset.fwLocalGroup !== '1');
    if (!list) return;
    const knownIds = new Set(rows.map(row => row.dataset.fwFriendId).filter(Boolean));
    const knownNames = new Set(rows.map(row => getChatInfo(row)?.name).filter(Boolean));
    for (const friend of friends) {
      const label = friend?.remark || friend?.name;
      if (!friend?.id || !friend.name || knownIds.has(friend.id) || knownNames.has(label)) continue;
      let row;
      if (template) {
        row = template.cloneNode(true);
        const img = row.querySelector('img');
        if (img) { img.src = getStableFriendAvatar(friend, friend.avatar || bundledAvatarFor(String(friend.id || friend.name || "friend"))); img.onerror = () => { img.onerror = null; img.src = bundledAvatarFor(String(friend.name || friend.id || "friend")); }; }
        const leaves = [...row.querySelectorAll('span,div')].filter(el => el.children.length === 0);
        const templateName = getChatInfo(template)?.name;
        const nameNode = leaves.find(el => el.textContent.trim() === templateName) || leaves.find(el => el.textContent.trim() && el.textContent.trim().length < 40);
        if (nameNode) { nameNode.textContent = label; nameNode.style.fontWeight = '400'; }
        const preview = leaves.find(el => el !== nameNode && /\u4f60\u597d|\u4f60\u4eec\u5df2\u7ecf/.test(el.textContent));
        if (preview) { preview.textContent = chatListPreview(friend); preview.style.fontWeight = '400'; }
        const stamp = chatListStamp(friend);
        row.querySelectorAll('[class*="time"],time').forEach(el => { el.textContent = stamp; });
        row.querySelectorAll('.fw-unread-badge').forEach(el => el.remove());
      } else {
        row = document.createElement('button'); row.type = 'button';
        row.className = 'fw-local-friend-row fw-local-friend-row--fallback';
        row.innerHTML = '<img alt=""><span class="fw-local-friend-copy"><strong></strong><small></small></span><time></time>';
        const img = row.querySelector('img'); img.src = getStableFriendAvatar(friend, friend.avatar || bundledAvatarFor(String(friend.id || friend.name || "friend")));
        img.onerror = () => { img.onerror = null; img.src = bundledAvatarFor(String(friend.name || friend.id || "friend")); };
        row.querySelector('strong').textContent = label;
        row.querySelector('small').textContent = chatListPreview(friend);
        row.querySelector('time').textContent = chatListStamp(friend);
      }
      row.querySelectorAll('.fw-unread-badge, .fw-native-unread-hidden').forEach(node => node.remove());
      getNativeUnreadNodes(row).forEach(node => node.remove());
      row.classList.remove('fw-chat-pinned', 'fw-chat-unread', 'fw-chat-hidden', 'fw-chat-menu-open');
      row.style.removeProperty('background-color');
      row.classList.add('fw-local-friend-row'); row.dataset.fwLocalFriend = '1'; row.dataset.fwFriendId = friend.id;
      const friendRecord = chatRecord(label);
      if (friend.source === '\u968f\u673a\u751f\u6210' && !friendRecord.generatedDefaultsApplied) { friendRecord.pinnedAt = null; friendRecord.unreadCount = 0; friendRecord.unread = false; friendRecord.unreadOverride = true; friendRecord.generatedDefaultsApplied = true; saveState(); }
      row.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); openFriendConversation(friend); }, true);
      list.insertBefore(row, list.firstChild); knownIds.add(friend.id); knownNames.add(label);
    }
  }

  function ensureLocalFriendsInAddressBook() {
    // Use the app's own allProfiles atom; never create a parallel contact section or row.
    syncNativeAddressBook();
    document.querySelectorAll('.fw-local-contacts-section, .fw-local-contact-row').forEach(node => node.remove());
  }  function removeLegacyWife() {
    let persistentStateChanged = false;
    if (Array.isArray(state.contacts)) {
      const filtered = state.contacts.filter(friend => friend?.name !== '老婆' && friend?.remark !== '老婆');
      if (filtered.length !== state.contacts.length) { state.contacts = filtered; persistentStateChanged = true; }
    }
    if (state.chats && state.chats['老婆']) { delete state.chats['老婆']; persistentStateChanged = true; }
    document.querySelectorAll('.wechat-dialogue-list > *, #contacts-container *').forEach(row => {
      const text = row.children.length === 0 ? row.textContent.trim() : '';
      if (text === '老婆' || getChatInfo(row)?.name === '老婆') {
        const target = row.closest('.fw-local-contact-row, .fw-local-friend-row, [class*=contact-item], [class*=chat-item_]') || row; target.remove();
      }
    });
    document.querySelectorAll('.fw-local-contacts-list > *').forEach(row => { if (row.textContent.trim() === '老婆') row.remove(); });
    // This is called by a broad MutationObserver; don't serialize unchanged contact state.
    if (persistentStateChanged) saveState();
  }
  const NATIVE_FEATURE_PAGES=[[['相册','album.png'],['拍摄','camera.png'],['视频通话','video.png'],['位置','location.png'],['红包','redpacket.png'],['礼物','gift.png'],['转账','transfer.png'],['收藏','favorite.png']],[['个人名片','personal-card.png'],['文件','file.png'],['音乐','music.png']]];
  function nativeBottomPopup(root){return [...(root?.children||[])].find(node=>String(node.className||'').split(/\s+/).some(name=>name.startsWith('bottomPopup')))||null}
  let galleryState = null;
  function openChatGallery(friend=null, onSend=null, options=null) {
    const actionLabel = String(options?.actionLabel || '发送');
    if (galleryState?.layer?.isConnected) galleryState.layer.remove();
    const layer=document.createElement('section'); layer.className='fw-gallery'; layer.setAttribute('role','dialog'); layer.setAttribute('aria-modal','true');
    layer.innerHTML='<header class="fw-gallery__header"><button class="fw-gallery__close" aria-label="关闭">×</button><strong>图片和视频 <i>⌄</i></strong><button class="fw-gallery__search" aria-label="搜索">⌕</button></header><div class="fw-gallery__grid"></div><footer class="fw-gallery__footer"><button class="fw-gallery__preview" disabled>预览</button><button class="fw-gallery__original"><i></i>原图</button><button class="fw-gallery__send" disabled>发送</button></footer><section class="fw-gallery__viewer" hidden><header><button type="button">‹</button><span></span><button type="button" aria-label="选择">○</button></header><img alt="图片预览"><footer><button type="button">‹</button><button type="button">›</button></footer></section>';
    document.body.appendChild(layer); const grid=layer.querySelector('.fw-gallery__grid'), viewer=layer.querySelector('.fw-gallery__viewer'), selected=new Map(); let items=[]; let previewIndex=0, original=false;
    const close=()=>{layer.classList.add('fw-gallery--closing');setTimeout(()=>layer.remove(),180);galleryState=null};
    const update=()=>{layer.querySelector('.fw-gallery__send').disabled=!selected.size;layer.querySelector('.fw-gallery__send').textContent=selected.size?(actionLabel+'('+selected.size+')'):actionLabel;layer.querySelector('.fw-gallery__preview').disabled=!selected.size;};
    const loadThumb=item=>{if(item.thumb)return item.thumb;try{item.thumb=window.DoutuApp?.getGalleryImage(String(item.id),220)||''}catch(_){item.thumb=''}return item.thumb};
    // The full-screen picker preview used to reuse the 220px grid thumbnail, which looked
    // badly pixelated once it filled the screen - decode a proper preview copy instead.
    const loadFull=item=>{if(item.full)return item.full;try{item.full=window.DoutuApp?.getGalleryImage(String(item.id),1280)||''}catch(_){item.full=''}return item.full||loadThumb(item)};
    // Asking the native bridge for a thumbnail is a synchronous call, so decoding all 160 tiles
    // on open blocked the page for seconds. Only decode the tiles that are (nearly) on screen.
    let thumbObserver=null;
    const render=()=>{grid.replaceChildren();if(thumbObserver)thumbObserver.disconnect();thumbObserver=new IntersectionObserver(entries=>{entries.forEach(entry=>{if(!entry.isIntersecting)return;thumbObserver.unobserve(entry.target);const img=entry.target;const item=items[Number(img.dataset.fwIndex)];if(item&&!img.src)img.src=loadThumb(item)})},{root:grid,rootMargin:'300px'});items.forEach((item,index)=>{const tile=document.createElement('button');tile.type='button';tile.className='fw-gallery__tile';const img=document.createElement('img');img.loading='lazy';img.alt='';img.dataset.fwIndex=String(index);const badge=document.createElement('i');badge.className='fw-gallery__select';const order=[...selected.keys()].indexOf(String(item.id));if(order>=0){tile.classList.add('is-selected');badge.textContent=String(order+1)}tile.append(img,badge);tile.addEventListener('click',()=>openPreview(index));badge.addEventListener('click',e=>{e.stopPropagation();toggle(item,tile,badge)});grid.appendChild(tile);thumbObserver.observe(img)});update()};
    const toggle=(item,tile,badge)=>{const id=String(item.id);if(selected.has(id))selected.delete(id);else selected.set(id,item);render()};
    const showPreview=()=>{const item=items[previewIndex];viewer.hidden=false;viewer.querySelector('span').textContent=(previewIndex+1)+'/'+items.length;
      // Paint the grid thumbnail immediately, then swap in the full-size copy: decoding a photo
      // straight away blocked the WebView and made the album feel frozen.
      const img=viewer.querySelector('img');img.src=loadThumb(item);const token=String(item.id);viewer.dataset.fwToken=token;
      setTimeout(()=>{if(viewer.hidden||viewer.dataset.fwToken!==token)return;const full=loadFull(item);if(full&&viewer.dataset.fwToken===token)img.src=full;},40);
      const sel=viewer.querySelector('header button:last-child'),is=selected.has(String(item.id));sel.textContent=is?String([...selected.keys()].indexOf(String(item.id))+1):'○';sel.classList.toggle('is-selected',is)};
    const openPreview=i=>{previewIndex=i;showPreview()};
    const consume=event=>{event.preventDefault();event.stopPropagation();event.stopImmediatePropagation()};
    const viewerBack=viewer.querySelector('header button:first-child');
    const viewerSelect=viewer.querySelector('header button:last-child');
    const viewerPrev=viewer.querySelector('footer button:first-child');
    const viewerNext=viewer.querySelector('footer button:last-child');
    viewerBack.addEventListener('click',event=>{consume(event);viewer.hidden=true});
    viewerSelect.addEventListener('click',event=>{consume(event);toggle(items[previewIndex]);showPreview()});
    viewerPrev.addEventListener('click',event=>{consume(event);previewIndex=(previewIndex-1+items.length)%items.length;showPreview()});
    viewerNext.addEventListener('click',event=>{consume(event);previewIndex=(previewIndex+1)%items.length;showPreview()});
    const closeButton=layer.querySelector('.fw-gallery__close');
    closeButton.addEventListener('click',event=>{consume(event);close()});
    const originalButton=layer.querySelector('.fw-gallery__original');
    originalButton.addEventListener('click',event=>{consume(event);original=!original;originalButton.classList.toggle('is-on',original)});
    const previewButton=layer.querySelector('.fw-gallery__preview');
    previewButton.addEventListener('click',event=>{consume(event);const chosen=[...selected.values()];if(!chosen.length)return;previewIndex=items.indexOf(chosen[0]);showPreview()});
    const sendButton=layer.querySelector('.fw-gallery__send');
    sendButton.addEventListener('click',async event=>{consume(event);const chosen=[...selected.values()].slice(0,9);if(!chosen.length)return;sendButton.disabled=true;const photos=[];for(const item of chosen){let src='';try{src=window.DoutuApp?.getGalleryImage(String(item.id),original?1600:1280)||item.thumb||''}catch(_){src=item.thumb||''}if(src)photos.push(src)}if(photos.length){if(onSend)onSend(photos);else sendGalleryPhotos(friend,photos);close()}else sendButton.disabled=false});
    galleryState={layer,friend};
    const populate=()=>{try{items=JSON.parse(window.DoutuApp?.getGalleryItems()||'[]')}catch(_){items=[]}render();if(!items.length)grid.innerHTML='<div class="fw-gallery__empty">暂无图片</div>'};
    let galleryPopulated=false; const populateOnce=()=>{if(galleryPopulated)return;galleryPopulated=true;populate()};
    window.__fwGalleryPermissionResult=granted=>{if(granted)populateOnce();else grid.innerHTML='<div class="fw-gallery__empty">需要允许访问照片，才能显示设备相册</div>'};
    try{if(window.DoutuApp?.hasGalleryPermission())populateOnce();else{grid.innerHTML='<div class="fw-gallery__empty">正在请求相册权限…</div>';window.DoutuApp?.requestGalleryPermission();let attempts=0;const permissionPoll=setInterval(()=>{attempts++;try{if(window.DoutuApp?.hasGalleryPermission()){clearInterval(permissionPoll);populateOnce()}else if(attempts>=20){clearInterval(permissionPoll);grid.innerHTML='<div class="fw-gallery__empty">未获得相册权限，请在系统设置中允许访问照片</div>'}}catch(_){clearInterval(permissionPoll);grid.innerHTML='<div class="fw-gallery__empty">设备相册不可用</div>'}},500)}}catch(_){grid.innerHTML='<div class="fw-gallery__empty">设备相册不可用</div>'}
  }
  function sendGalleryPhotos(friend,photos){
    if(friend){friend.messages=Array.isArray(friend.messages)?friend.messages:[];photos.forEach(data=>friend.messages.push({text:data,type:String(data).startsWith('data:video/')?'video':'image',createdAt:Date.now()}));saveState();
      // Redraw the open chat so the new media renders through the normal bubble path:
      // that is what gives it the sender avatar, no bubble tail, and swipe-to-swap.
      const page=document.querySelector('.fw-local-conversation');
      if(page&&String(page.dataset.friendId)===String(friend.id)){page.__fwDrawMessages?.();const main=page.querySelector('.fw-local-conversation__messages');if(main)main.scrollTop=main.scrollHeight;}
      const row=document.querySelector('.fw-local-friend-row[data-fw-friend-id="'+CSS.escape(friend.id)+'"]');const preview=row?.querySelector('.fw-local-friend-copy small');if(preview)preview.textContent=photos.some(data=>String(data).startsWith('data:video/'))?'[视频]':'[图片]';return}
    const root=document.getElementById('conversation-input')?.closest('.flex.flex-col');if(root){photos.forEach(src=>appendImageMessage(root,src));const key='fw.gallery.sent.'+location.hash;try{const all=JSON.parse(localStorage.getItem(key)||'[]');all.push(...photos);localStorage.setItem(key,JSON.stringify(all.slice(-40)))}catch(_){showToast('图片已发送（本次会话）')}}
  }
  function restoreNativeSentImages(){const input=document.getElementById('conversation-input');if(!input)return;const host=input.closest('.flex.flex-col')||input.parentElement?.parentElement;if(!host)return;const key='fw.gallery.sent.'+location.hash;try{const images=JSON.parse(localStorage.getItem(key)||'[]');if(!Array.isArray(images))return;const current=host.querySelectorAll('.fw-gallery-message').length;images.slice(current).forEach(src=>appendImageMessage(host,src))}catch(_){}}
 function openSentImagePreview(src){openMomentImagePreview(src)}
  // "0:02" style badge used by video bubbles.
  function chatMediaDuration(seconds) {
    const total = Number(seconds);
    if (!Number.isFinite(total) || total <= 0) return '0:00';
    const whole = Math.round(total);
    return Math.floor(whole / 60) + ':' + String(whole % 60).padStart(2, '0');
  }
  // Full-screen viewer for a captured video (photos keep using the moments preview).
  function openChatMediaViewer(src, kind) {
    document.querySelector('.fw-media-viewer')?.remove();
    const layer = document.createElement('section');
    layer.className = 'fw-media-viewer';
    if (kind === 'video') {
      layer.innerHTML = '<video controls autoplay playsinline preload="metadata"></video><button type="button" aria-label="关闭">✕</button>';
      layer.querySelector('video').src = src;
    } else {
      layer.innerHTML = '<img alt="图片"><button type="button" aria-label="关闭">✕</button>';
      layer.querySelector('img').src = src;
    }
    document.body.appendChild(layer);
    layer.querySelector('button').onclick = () => layer.remove();
    layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
  }
   function appendImageMessage(host,src){const line=document.createElement('div');line.className='fw-gallery-message';if(typeof src==='string'&&src.startsWith('data:video/')){const clip=document.createElement('video');clip.className='fw-gallery-message__video';clip.src=src;clip.controls=true;clip.playsInline=true;clip.preload='metadata';line.appendChild(clip)}else{const img=document.createElement('img');img.src=src;img.alt='图片';img.onclick=()=>openSentImagePreview(src);line.appendChild(img)}host.appendChild(line)}
  let chatPayScreen = null;
  function chatPayFriend() { return friendFromConversationRoute() || { id: 'conversation', name: '好友', avatar: apiAvatarFor('chat-pay-friend'), wechatId: '' }; }
  function chatPayLabel(friend) { return String(friend?.remark || friend?.name || '好友').trim() || '好友'; }
  function setChatPaySystemBars(visible) { try { if (window.DoutuApp?.setPayPageSystemBars) window.DoutuApp.setPayPageSystemBars(visible); } catch (_) {} }
  // 2026-10-10: the reference screenshots keep the Android status bar on screen for
  // full-screen WeChat pages, so reveal the system bars whenever a local conversation
  // (including the pay assistant) or the pay route owns the screen, and hide them
  // again once the mock WeChat home is back in front.
  let chatSystemBarsVisible = null;
  function syncChatSystemBars() {
    let visible = false;
    try {
      visible = document.documentElement.classList.contains('fw-pay-route-active')
        || !!document.querySelector('.fw-local-conversation')
        || !!document.querySelector('.fw-friend-profile-page')
        || !!document.querySelector('.fw-moments-page')
        || !!document.querySelector('.fw-moments-feed')
        || !!document.querySelector('.fw-pay-success')
        || !!document.querySelector('.fw-chat-info');
    } catch (_) { /* keep the bars hidden */ }
    if (visible === chatSystemBarsVisible) return;
    chatSystemBarsVisible = visible;
    setChatPaySystemBars(visible);
  }
  window.__fwSyncSystemBars = syncChatSystemBars;
  // Local pages are created and removed all over the file, so track the DOM instead of
  // sprinkling sync calls through every open/close handler. The icon normaliser keeps
  // the "..." dot run matching the reference on every page that builds one.
  new MutationObserver(records => {
    for (const record of records) {
      record.addedNodes.forEach(node => { if (node.nodeType === 1) normalizeMoreIconGeometry(node); });
    }
    syncChatSystemBars();
    // 朋友圈是 body 上的全屏覆盖层，出现/移除时立刻同步底栏显隐。
    document.body.classList.toggle('fw-moments-open',
      !!document.querySelector('.fw-moments-feed, .fw-moments-page'));
  }).observe(document.body, { childList: true, subtree: true });
  function closeChatPayScreen() { const page=chatPayScreen; if(!page||!page.isConnected||page.dataset.closing==='1')return; page.dataset.closing='1';page.classList.add('fw-chat-pay-screen--exit'); const finish=()=>{if(!page.isConnected)return;page.remove();chatPayScreen=null;document.body.classList.remove('fw-chat-pay-open');syncChatSystemBars()};page.addEventListener('animationend',finish,{once:true});setTimeout(finish,190); }
  window.__fwCloseChatPay=()=>{const open=!!chatPayScreen?.isConnected;if(open)closeChatPayScreen();return open};
  window.__fwHandleChatPayBack=()=>{if(!chatPayScreen?.isConnected)return false;const editor=chatPayScreen.querySelector('.fw-chat-pay-input-overlay');if(editor){editor.remove();return true}const confirm=chatPayScreen.querySelector('.fw-transfer-confirm');if(confirm){if(confirm.querySelector('.fw-transfer-success, .fw-scanpay-success')){confirm.querySelector('[data-done]')?.click();return true}confirm.querySelector('[data-close]')?.click();return true}closeChatPayScreen();return true};
  function showChatPayInput(page,title,initial,options,onSave) {
    const modal=document.createElement('div');modal.className='fw-chat-pay-input-overlay';modal.innerHTML='<form class="fw-chat-pay-input-dialog"><strong></strong><input autocomplete="off"><div><button type="button" data-cancel>取消</button><button type="submit">确定</button></div></form>';
    modal.querySelector('strong').textContent=title;const input=modal.querySelector('input');input.value=initial||'';Object.assign(input,options||{});page.appendChild(modal);
    const close=()=>modal.remove();modal.addEventListener('click',event=>{if(event.target===modal)close()});modal.querySelector('[data-cancel]').onclick=close;
    modal.querySelector('form').onsubmit=event=>{event.preventDefault();onSave(input.value);close()};requestAnimationFrame(()=>{input.focus();input.select()});
  }
  function mountTransferConfirmSheet(page, friend, friendLabel, amountValue, noteValue, onComplete, options) {
    // 2026-10-11: 扫一扫付款 shares the confirm sheet but not the result page.
    const successMode = options && (options.mode === 'scanpay' || options.mode === 'redpacket') ? options.mode : 'transfer';
    const overlay=document.createElement('div'); overlay.className='fw-transfer-confirm';
    overlay.innerHTML='<section class="fw-transfer-confirm__sheet" role="dialog" aria-modal="true" aria-label="转账确认"><header><button type="button" data-close aria-label="关闭">×</button><strong>微信支付</strong><button type="button" data-fingerprint aria-label="使用指纹">◉</button></header><div class="fw-transfer-confirm__recipient">向 <span data-label></span> 转账</div><div class="fw-transfer-confirm__amount"><small>¥</small><span data-value></span></div><div class="fw-transfer-confirm__divider"></div><div class="fw-transfer-confirm__method-label"><span>付款方式</span><button type="button" data-method>更改 <i>⌄</i></button></div><button type="button" class="fw-transfer-confirm__method"><i aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 7.2 11.9 3l8.8 4.2v9.6l-8.8 4.1L3 16.8z"/><path d="m3.4 7.3 8.5 4 8.5-4M11.9 11.5v9"/></svg></i><span><strong data-method-name>交通银行储蓄卡</strong><small>**** **** **** 0204</small></span><b aria-hidden="true">✓</b></button><div class="fw-transfer-confirm__dots" aria-label="演示密码输入"><i></i><i></i><i></i><i></i><i></i><i></i></div><div class="fw-transfer-confirm__keys"><button data-digit="1">1</button><button data-digit="2">2</button><button data-digit="3">3</button><button data-digit="4">4</button><button data-digit="5">5</button><button data-digit="6">6</button><button data-digit="7">7</button><button data-digit="8">8</button><button data-digit="9">9</button><span></span><button data-digit="0">0</button><button data-delete aria-label="删除"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 4h12v16H9l-7-8z"/><path d="m11 9 6 6m0-6-6 6"/></svg></button></div></section>';
    overlay.querySelector('[data-label]').textContent=friendLabel; overlay.querySelector('[data-value]').textContent=Number(amountValue).toFixed(2);
    let demoDigits='';
    const close=()=>{if(!overlay.isConnected)return;overlay.classList.add('is-closing');setTimeout(()=>overlay.remove(),190)};
    overlay.querySelector('[data-close]').onclick=close; overlay.addEventListener('click',event=>{if(event.target===overlay)close()});
    overlay.querySelector('[data-fingerprint]')?.addEventListener('click',()=>showToast('指纹验证仅作界面演示'));
    overlay.querySelector('[data-method]').onclick=()=>{let menu=overlay.querySelector('.fw-transfer-confirm__method-menu');if(menu){menu.remove();return}menu=document.createElement('div');menu.className='fw-transfer-confirm__method-menu';menu.innerHTML='<button type="button">零钱</button><button type="button">银行卡（模拟）</button>';menu.querySelectorAll('button').forEach(option=>option.onclick=()=>{overlay.querySelector('[data-method-name]').textContent=option.textContent;menu.remove()});overlay.appendChild(menu)};
    const renderDots=()=>overlay.querySelectorAll('.fw-transfer-confirm__dots i').forEach((dot,index)=>dot.classList.toggle('is-filled',index<demoDigits.length));
    const showSuccess=()=>{
      // 微信红包的流程是：密码输完直接回聊天并落下红包气泡，没有支付成功页。
      if(successMode==='redpacket'){ try{ onComplete?.(); } finally { closeChatPayScreen(); } return; }
      overlay.classList.add('fw-transfer-confirm--success');
      // 扫一扫付款的结果页和好友转账不同：顶部"支付成功"，中间是收款方圆形头像 +
      // 网名（**真名），下面是金额，最后是单独的"完成"按钮，没有"待x确认收款"。
      if(successMode==='scanpay'){
        overlay.innerHTML='<section class="fw-scanpay-success" role="dialog" aria-modal="true" aria-label="支付结果">'+paySuccessBody()+'</section>';
        const payeeImage=overlay.querySelector('.fw-scanpay-success__avatar');
        payeeImage.src=String(options.avatar||'')||bundledAvatarFor(options.name||'');
        payeeImage.onerror=()=>{payeeImage.onerror=null;payeeImage.src=bundledAvatarFor(options.name||'')};
        overlay.querySelector('.fw-scanpay-success__name').textContent=String(options.name||'')||chatPayLabel(friend);
        overlay.querySelector('.fw-scanpay-success__amount strong').textContent=Number(amountValue).toFixed(2);
        let settled=false;
        const finishScanPay=()=>{if(settled)return;settled=true;try{onComplete?.()}finally{closeChatPayScreen()}};
        overlay.querySelector('[data-done]').onclick=finishScanPay;
        overlay.dataset.fwFinishDemoTransfer='ready';
        return;
      }
      overlay.innerHTML='<section class="fw-transfer-success" role="dialog" aria-modal="true" aria-label="转账结果"><div class="fw-transfer-success__heading"><span class="fw-transfer-success__check" aria-hidden="true"><svg viewBox="0 0 48 42" focusable="false"><path d="M24 1C11.3 1 1 9.8 1 20.7c0 6.2 3.4 11.7 8.8 15.2l-1.6 5.2 8.2-3.1c2.4.8 4.9 1.2 7.6 1.2 12.7 0 23-8.8 23-19.7S36.7 1 24 1Z"/><path d="m12.2 20.6 7.3 6.3 16.5-14.2"/></svg></span><b>支付成功</b></div><main><div class="fw-transfer-success__recipient">待<span></span>确认收款</div><div class="fw-transfer-success__amount"><i>¥</i><strong></strong></div></main><button type="button" data-done>完成</button></section>';
      overlay.querySelector('.fw-transfer-success__recipient span').textContent=chatPayLabel(friend);overlay.querySelector('.fw-transfer-success__amount strong').textContent=Number(amountValue).toFixed(2);let finished=false;const finishDemoTransfer=()=>{if(finished)return;finished=true;try{onComplete?.()}finally{closeChatPayScreen()}};overlay.querySelector('[data-done]').onclick=finishDemoTransfer;overlay.dataset.fwFinishDemoTransfer='ready';
    };
    overlay.querySelectorAll('[data-digit]').forEach(key=>key.onclick=()=>{if(demoDigits.length>=6)return;demoDigits+=key.dataset.digit;renderDots();if(demoDigits.length===6)setTimeout(showSuccess,120)});
    overlay.querySelector('[data-delete]').onclick=()=>{demoDigits=demoDigits.slice(0,-1);renderDots()}; page.appendChild(overlay);requestAnimationFrame(()=>overlay.classList.add('is-visible'));return {close,element:overlay};
  }
  /* ================= 支付成功页 (2026-10-11) =================
     小微主页 → 支付成功。直接把扫一扫付款成功那层（fw-scanpay-success）拿来用：
     绿底勾 + 支付成功、收款方圆形头像、网名（**真名）、金额、底部浅灰"完成"。
     位置按参考图 1440x3168 ÷4 摆：标题顶 48、头像 43.25 顶 142、
     名字顶 200、金额顶 249、完成按钮 165.5x43.25 距底 104.5。 */
  const PAY_SUCCESS_AMOUNTS = [0.01, 0.5, 1, 1.88, 3.5, 6.66, 8.88, 12.34, 20, 52, 66, 88.88, 188];
  function paySuccessBody() {
    return '<div class="fw-scanpay-success__heading"><span class="fw-scanpay-success__check" aria-hidden="true">'
      + '<svg viewBox="0 0 48 42" focusable="false"><path d="M24 1C11.3 1 1 9.8 1 20.7c0 6.2 3.4 11.7 8.8 15.2l-1.6 5.2 8.2-3.1c2.4.8 4.9 1.2 7.6 1.2 12.7 0 23-8.8 23-19.7S36.7 1 24 1Z"/>'
      + '<path d="m12.2 20.6 7.3 6.3 16.5-14.2"/></svg></span><b>支付成功</b></div>'
      + '<img class="fw-scanpay-success__avatar" alt="">'
      + '<p class="fw-scanpay-success__name"></p>'
      + '<div class="fw-scanpay-success__amount"><i>¥</i><strong></strong></div>'
      + '<button type="button" class="fw-scanpay-success__done" data-done>完成</button>';
  }

  /** 支付成功页的数据存一份（可编辑）：{ avatar, name, realName, amount }。 */
  const PAY_SUCCESS_KEY = 'fw-pay-success';
  function paySuccessLoad() {
    try {
      const data = JSON.parse(localStorage.getItem(PAY_SUCCESS_KEY) || 'null');
      return data && typeof data === 'object' ? data : null;
    } catch (_) { return null; }
  }
  function paySuccessSave(data) {
    try { localStorage.setItem(PAY_SUCCESS_KEY, JSON.stringify(data)); } catch (_) { /* 配额满就只留在页面上 */ }
  }
  /** 首次打开随机取一位本地联系人；之后沿用上次改好的那一套。 */
  function paySuccessInitialData() {
    const saved = paySuccessLoad();
    if (saved) {
      const realName = String(saved.realName || '').trim();
      return {
        avatar: String(saved.avatar || ''),
        name: String(saved.name || ''),
        // 早先版本可能把标点当昵称存进来了，这里挡一下。
        realName: /^[\u4e00-\u9fa5A-Za-z]{1,8}$/.test(realName) ? realName : '',
        amount: String(saved.amount || ''),
      };
    }
    const contacts = (Array.isArray(state.contacts) ? state.contacts : [])
      .filter(friend => friend && String(friend.remark || friend.name || '').trim());
    const friend = contacts.length ? contacts[Math.floor(Math.random() * contacts.length)] : null;
    return {
      avatar: friend ? String(friend.avatar || friend.avatarInfo || '') : '',
      name: friend ? String(friendLabel(friend) || friend.name || '').trim() : '微信支付',
      // 参考图是「网名（**真名末字）」：联系人没填真名就用昵称末字，保持同一形态
      // （昵称里混进的标点之类不当真名用）。
      realName: friend && /^[\u4e00-\u9fa5A-Za-z]{1,8}$/.test(String(friend.realName || friend.nickname || friend.name || '').trim())
        ? String(friend.realName || friend.nickname || friend.name).trim()
        : '',
      amount: PAY_SUCCESS_AMOUNTS[Math.floor(Math.random() * PAY_SUCCESS_AMOUNTS.length)].toFixed(2),
    };
  }

  function closePaySuccessPage() {
    const page = document.querySelector('.fw-pay-success');
    if (!page) return false;
    page.classList.add('fw-pay-success--exit');
    const finish = () => page.remove();
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 200);
    syncChatSystemBars();
    return true;
  }

  /** 独立打开（小微主页 → 支付成功）：随机一位联系人 + 随机金额。 */
  function mountPaySuccessPage() {
    document.querySelector('.fw-pay-success')?.remove();
    const data = paySuccessInitialData();
    const page = document.createElement('section');
    page.className = 'fw-pay-success';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', '支付成功');
    page.innerHTML = '<div class="fw-scanpay-success">'
      + paySuccessBody().replace(
        '<p class="fw-scanpay-success__name"></p>',
        '<p class="fw-scanpay-success__name">'
        + '<span class="fw-pay-success__part" data-pay-name="name" role="button" tabindex="0"></span>'
        + '<span class="fw-pay-success__part" data-pay-name="realName" role="button" tabindex="0"></span></p>')
      + '</div>';
    const avatar = page.querySelector('.fw-scanpay-success__avatar');
    const namePart = page.querySelector('[data-pay-name="name"]');
    const realPart = page.querySelector('[data-pay-name="realName"]');
    const amountValue = page.querySelector('.fw-scanpay-success__amount strong');
    const renderAvatar = () => {
      const source = String(data.avatar || '').trim();
      const fallback = bundledAvatarFor(data.name || '');
      avatar.alt = '';
      avatar.onerror = () => { avatar.onerror = null; avatar.src = fallback; };
      avatar.src = source || fallback;
      if (source && /^https?:/i.test(source) && typeof window.__fwResolveProfileAvatar === 'function') {
        window.__fwResolveProfileAvatar(source).then(resolved => {
          if (resolved && avatar.isConnected) avatar.src = resolved;
        }).catch(() => {});
      }
    };
    const renderName = () => {
      namePart.textContent = data.name || '微信支付';
      // 和扫一扫付款页同一套规则：真名只显示最后一个字，其余每个字一个 *。
      realPart.textContent = data.realName ? '（' + scanPayMaskedName(data.realName) + '）' : '';
    };
    const renderAmount = () => { amountValue.textContent = Number(data.amount || 0).toFixed(2); };
    renderAvatar();
    renderName();
    renderAmount();
    // 点头像 → 接口里挑 / 相册上传；点网名、点隐藏的真名 → 改；点金额 → 改。
    avatar.addEventListener('click', () => openAvatarPickerSheet(page, value => {
      data.avatar = value;
      renderAvatar();
      paySuccessSave(data);
    }));
    const editNames = focus => openProfileNameEditor(page, data, () => {
      renderName();
      paySuccessSave(data);
    }, { focus });
    namePart.addEventListener('click', () => editNames('name'));
    realPart.addEventListener('click', () => editNames('realName'));
    page.querySelector('.fw-scanpay-success__amount').addEventListener('click', () => {
      showChatPayInput(page, '修改金额', Number(data.amount || 0).toFixed(2), { inputMode: 'decimal', maxLength: 12 }, value => {
        const parsed = Number(String(value || '').replace(/[^0-9.]/g, ''));
        if (!Number.isFinite(parsed) || parsed < 0) { showToast('请输入有效金额'); return; }
        data.amount = parsed.toFixed(2);
        renderAmount();
        paySuccessSave(data);
      });
      try { window.DoutuApp && window.DoutuApp.showSoftKeyboard && window.DoutuApp.showSoftKeyboard(); } catch (_) { /* no bridge */ }
      setTimeout(() => { try { window.DoutuApp && window.DoutuApp.showSoftKeyboard && window.DoutuApp.showSoftKeyboard(); } catch (_) { /* no bridge */ } }, 160);
    });
    page.querySelector('[data-done]').addEventListener('click', () => closePaySuccessPage());
    document.body.appendChild(page);
    syncChatSystemBars();
    requestAnimationFrame(() => page.classList.add('fw-pay-success--enter'));
  }

  // Android 返回键先关支付成功页。
  (function installPaySuccessBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      if (closePaySuccessPage()) return true;
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  function mountChatPayScreen(type) {
    closeChatPayScreen();
    const friend=chatPayFriend(),label=chatPayLabel(friend),avatar=friend.avatar||apiAvatarFor('chat-pay-friend'),transfer=type==='transfer';
    const page=document.createElement('section');page.className='fw-chat-pay-screen fw-chat-pay-screen--'+(transfer?'transfer':'redpacket');page.setAttribute('role','dialog');page.setAttribute('aria-modal','true');
    // 2026-10-11 发红包金额用参考图里那套数字键盘：3 列数字 + 第 4 列（退格 / 确定），从下往上滑出。
    const redKeypadHtml='<div class="fw-chat-pay-red-keypad" aria-hidden="true"><div class="fw-chat-pay-red-keys">'
      +'<button type="button" data-digit="1">1</button><button type="button" data-digit="2">2</button><button type="button" data-digit="3">3</button>'
      +'<button type="button" data-back aria-label="删除"><svg viewBox="0.9 1.2 18.1 13.6" aria-hidden="true"><path d="M5.7 0H16.1a2 2 0 0 1 2 2v9.6a2 2 0 0 1-2 2H5.7L0 6.8Z" fill="#333"/><path d="m7.6 4.2 5.5 5.2m0-5.2-5.5 5.2" fill="none" stroke="#fff" stroke-width="1.5" stroke-linecap="round"/></svg></button>'
      +'<button type="button" data-digit="4">4</button><button type="button" data-digit="5">5</button><button type="button" data-digit="6">6</button>'
      +'<button type="button" data-digit="7">7</button><button type="button" data-digit="8">8</button><button type="button" data-digit="9">9</button>'
      +'<button type="button" data-digit="0">0</button><button type="button" data-dot>.</button>'
      +'<button type="button" class="fw-chat-pay-red-confirm" data-confirm>确定</button>'
      +'</div></div>';
    page.innerHTML=transfer
      ? '<header class="fw-chat-pay-header"><button type="button" class="fw-chat-pay-back">‹</button><span></span><button type="button" class="fw-chat-pay-more" aria-label="更多"><svg viewBox="0 0 28 8" aria-hidden="true"><circle cx="4" cy="4" r="1.7"/><circle cx="14" cy="4" r="1.7"/><circle cx="24" cy="4" r="1.7"/></svg></button></header><main class="fw-chat-pay-body"><section class="fw-chat-pay-recipient"><div><strong data-recipient></strong><small data-wechat></small></div><img data-avatar alt=""></section><section class="fw-chat-pay-transfer-card"><label>转账金额</label><div class="fw-chat-pay-money"><b>¥</b><output data-amount><span data-amount-value>0</span><i class="fw-chat-pay-caret" aria-hidden="true"></i></output></div><button type="button" class="fw-chat-pay-note" data-note>添加转账说明</button></section></main><footer class="fw-chat-pay-keypad"><div class="fw-chat-pay-keys"><button data-key="1">1</button><button data-key="2">2</button><button data-key="3">3</button><button data-key="back">⌫</button><button data-key="4">4</button><button data-key="5">5</button><button data-key="6">6</button><button data-key="7">7</button><button data-key="8">8</button><button data-key="9">9</button><button class="wide" data-key="0">0</button><button data-key="dot">.</button></div><button type="button" class="fw-chat-pay-submit fw-chat-pay-submit--transfer" disabled>转账</button></footer>'
      : '<header class="fw-chat-pay-header"><button type="button" class="fw-chat-pay-back" aria-label="返回"><svg viewBox="0 0 8 15" aria-hidden="true"><path d="M6.9 1.1 1.1 7.5l5.8 6.4"/></svg></button><strong>发红包</strong><button type="button" class="fw-chat-pay-more" aria-label="更多"><svg viewBox="0 0 15.5 3" aria-hidden="true"><circle cx="1.5" cy="1.5" r="1.5"/><circle cx="7.75" cy="1.5" r="1.5"/><circle cx="14" cy="1.5" r="1.5"/></svg></button></header><main class="fw-chat-pay-red-body"><button type="button" class="fw-chat-pay-red-card fw-chat-pay-amount-card"><span>金额</span><span class="fw-chat-pay-red-amount"><output data-amount>¥0.00</output><i class="fw-chat-pay-red-caret" aria-hidden="true"></i></span></button><button type="button" class="fw-chat-pay-red-card fw-chat-pay-blessing"><span data-blessing>恭喜发财，大吉大利</span><img class="fw-chat-pay-blessing-icon" src="./assets/wechat-redpacket-smile.png" alt=""></button><button type="button" class="fw-chat-pay-red-card fw-chat-pay-cover"><span>红包封面</span><img class="fw-chat-pay-cover-arrow" src="./assets/wechat-redpacket-chevron.png" alt=""></button><div class="fw-chat-pay-red-total" data-total>¥ 0.00</div><button type="button" class="fw-chat-pay-submit fw-chat-pay-submit--red">塞钱进红包</button></main><footer class="fw-chat-pay-refund">未领取的红包，将于24小时后发起退款</footer>'+redKeypadHtml;
    if(transfer){page.querySelector('[data-recipient]').textContent='转账给 '+label;page.querySelector('[data-wechat]').textContent='微信号：'+(friend.wechatId||friend.id||'');page.querySelector('[data-avatar]').src=avatar}
    document.body.appendChild(page);chatPayScreen=page;document.body.classList.add('fw-chat-pay-open');setChatPaySystemBars(true);requestAnimationFrame(()=>page.classList.add('fw-chat-pay-screen--enter'));
    page.querySelector('.fw-chat-pay-back').onclick=closeChatPayScreen;page.querySelector('.fw-chat-pay-more').onclick=()=>showToast('更多功能');let amount='';const amountOutput=page.querySelector('[data-amount]');
    const render=()=>{const value=amount||'0';if(transfer){amountOutput.querySelector('[data-amount-value]').textContent=value;(()=>{const button=page.querySelector('.fw-chat-pay-submit--transfer');const valid=Number.isFinite(Number(amount))&&Number(amount)>0&&Number(amount)<=9999999.99;button.disabled=!valid;button.setAttribute('aria-disabled',String(!valid));button.classList.toggle('is-ready',valid);button.style.setProperty('background-color','#07c160','important')})()}else{amountOutput.textContent='¥'+(amount||'0.00');amountOutput.classList.toggle('is-filled',!!amount);page.querySelector('[data-total]').textContent='¥ '+(amount||'0.00')}};
    if(transfer){
      page.querySelectorAll('[data-key]').forEach(key=>key.onclick=()=>{const action=key.dataset.key;if(action==='back')amount=amount.slice(0,-1);else if(action==='dot'){if(!amount.includes('.'))amount=amount?amount+'.':'0.'}else if(amount.length<10&&(!amount.includes('.')||amount.split('.')[1].length<2))amount=amount==='0'?action:amount+action;if(Number(amount)>9999999.99)amount='9999999.99';render()});
      page.querySelector('[data-note]').onclick=()=>showChatPayInput(page,'转账说明',page.querySelector('[data-note]').dataset.value||'',{type:'text',maxLength:20},value=>{page.querySelector('[data-note]').dataset.value=value.trim();page.querySelector('[data-note]').textContent=value.trim()||'添加转账说明'});
      page.querySelector('.fw-chat-pay-submit--transfer').onclick=()=>{if(Number(amount)>0){const note=page.querySelector('[data-note]').dataset.value||'';mountTransferConfirmSheet(page,friend,label,amount,note,()=>{friend.messages=Array.isArray(friend.messages)?friend.messages:[];const transfer={type:'transfer',amount:Number(amount).toFixed(2),note:String(note).trim(),friendName:String(label),createdAt:Date.now()};friend.messages.push(transfer);pushTransferBill(transfer,-Math.abs(Number(amount)),label,friend);saveState();const row=document.querySelector('.fw-local-friend-row[data-fw-friend-id="'+CSS.escape(String(friend.id))+'"]');const preview=row?.querySelector('.fw-local-friend-copy small');if(preview)preview.textContent=String(note).trim()||'你发起了一笔转账';document.querySelector('.fw-local-conversation')?.__fwDrawMessages?.()})}};
    }else{
      const redKeypad=page.querySelector('.fw-chat-pay-red-keypad');
      const openKeypad=()=>{page.classList.add('fw-chat-pay-red-editing');redKeypad.classList.add('is-open');redKeypad.setAttribute('aria-hidden','false')};
      const closeKeypad=()=>{page.classList.remove('fw-chat-pay-red-editing');redKeypad.classList.remove('is-open');redKeypad.setAttribute('aria-hidden','true')};
      const typeDigit=digit=>{if(amount.length>=10)return;if(amount.includes('.')&&amount.split('.')[1].length>=2)return;amount=amount==='0'?digit:amount+digit;if(Number(amount)>999999.99)amount='999999.99';render()};
      page.querySelector('.fw-chat-pay-amount-card').onclick=openKeypad;
      redKeypad.querySelectorAll('[data-digit]').forEach(key=>key.onclick=()=>typeDigit(key.dataset.digit));
      redKeypad.querySelector('[data-back]').onclick=()=>{amount=amount.slice(0,-1);render()};
      redKeypad.querySelector('[data-dot]').onclick=()=>{if(!amount.includes('.')){amount=amount?amount+'.':'0.';render()}};
      redKeypad.querySelector('[data-confirm]').onclick=closeKeypad;
      // 点键盘和金额行以外的地方收起键盘。
      page.addEventListener('click',event=>{const target=event.target instanceof Element?event.target:null;if(target&&(target.closest('.fw-chat-pay-red-keypad')||target.closest('.fw-chat-pay-amount-card')||target.closest('.fw-chat-pay-submit--red')||target.closest('.fw-chat-pay-input-overlay')))return;closeKeypad()});
      page.querySelector('.fw-chat-pay-blessing').onclick=()=>showChatPayInput(page,'红包祝福语',page.querySelector('[data-blessing]').textContent,{type:'text',maxLength:20},value=>page.querySelector('[data-blessing]').textContent=value.trim()||'恭喜发财，大吉大利');page.querySelector('.fw-chat-pay-cover').onclick=()=>showToast('红包封面选择');
      page.querySelector('.fw-chat-pay-submit').onclick=()=>{
        if(!(Number(amount)>0)){openKeypad();return}
        closeKeypad();
        const blessing=String(page.querySelector('[data-blessing]').textContent||'').trim()||'恭喜发财，大吉大利';
        mountTransferConfirmSheet(page,friend,label,amount,blessing,()=>{
          friend.messages=Array.isArray(friend.messages)?friend.messages:[];
          const packet={type:'redpacket',amount:Number(amount).toFixed(2),blessing,friendName:String(label),createdAt:Date.now()};
          friend.messages.push(packet);
          try{pushTransferBill(packet,-Math.abs(Number(amount)),label,friend)}catch(_){/* wallet bills are optional */}
          saveState();
          const row=document.querySelector('.fw-local-friend-row[data-fw-friend-id="'+CSS.escape(String(friend.id))+'"]');
          const preview=row?.querySelector('.fw-local-friend-copy small');
          if(preview)preview.textContent='[微信红包]';
          document.querySelector('.fw-local-conversation')?.__fwDrawMessages?.();
        },{mode:'redpacket'});
      };
    }
    render();
  }
  function installChatPayActions(){if(document.documentElement.dataset.fwChatPayActions)return;document.documentElement.dataset.fwChatPayActions='1';document.addEventListener('click',event=>{const action=event.target instanceof Element?event.target.closest('[data-action="红包"], [data-action="转账"]'):null;if(!action)return;event.preventDefault();event.stopPropagation();event.stopImmediatePropagation();const panel=action.closest('.fw-native-chat-feature-panel');if(panel)closeNativeFeaturePanel(panel);else action.closest('.fw-chat-feature-panel')?.classList.remove('is-open');mountChatPayScreen(action.dataset.action==='转账'?'transfer':'redpacket')},true)}
  function installGalleryAction(){if(document.documentElement.dataset.fwGalleryAction)return;document.documentElement.dataset.fwGalleryAction='1';document.addEventListener('click',event=>{const action=event.target instanceof Element?event.target.closest('[data-action="相册"]'):null;if(!action)return;event.preventDefault();event.stopPropagation();event.stopImmediatePropagation();const page=document.querySelector('.fw-local-conversation');let friend=null;if(page){friend=(Array.isArray(state.contacts)?state.contacts:[]).find(x=>String(x.id)===page.dataset.friendId)||null}const panel=action.closest('.fw-chat-feature-panel');if(panel?.classList.contains('fw-native-chat-feature-panel'))closeNativeFeaturePanel(panel);if(page){const localPanel=page.querySelector('.fw-chat-feature-panel');if(localPanel)setFeaturesFromGallery(localPanel)}openChatGallery(friend)},true)}

  /**
   * 聊天里点「视频通话」→ 打开同一个来电页（语音形态），联系人头像/昵称取当前聊天对象，
   * 方向按"我打给他"处理：挂断就是取消，接通后挂断发的是通话时长。
   */
  function installVideoCallAction(){
    if(document.documentElement.dataset.fwVideoCallAction)return;
    document.documentElement.dataset.fwVideoCallAction='1';
    document.addEventListener('click',event=>{
      const action=event.target instanceof Element?event.target.closest('[data-action="视频通话"]'):null;
      if(!action)return;
      event.preventDefault();event.stopPropagation();event.stopImmediatePropagation();
      const page=document.querySelector('.fw-local-conversation');
      let friend=null;
      if(page){friend=(Array.isArray(state.contacts)?state.contacts:[]).find(x=>String(x.id)===page.dataset.friendId)||null}
      const panel=action.closest('.fw-chat-feature-panel');
      if(panel){
        if(panel.classList.contains('fw-native-chat-feature-panel'))closeNativeFeaturePanel(panel);
        else panel.classList.remove('is-open');
      }
      mountSimulatedCall('voice',{
        contact: friend ? { name: friendLabel(friend), avatar: friend.avatar || friend.avatarInfo || '' } : null,
        direction: 'outgoing',
        friend,
      });
    },true);
  }
  function setFeaturesFromGallery(panel){panel.classList.remove('is-open');panel.setAttribute('aria-hidden','true')}
  function installNativeConversationFeatures(){
    const input=document.getElementById('conversation-input'); if(!input)return;
    const composer=input.closest('.border-t'),root=composer?.parentElement; if(!composer||!root)return;
    root.classList.add('fw-native-composer-root');
    let panel=root.querySelector(':scope > .fw-native-chat-feature-panel');
    if(!panel){
      panel=document.createElement('section'); panel.className='fw-chat-feature-panel fw-native-chat-feature-panel';
      panel.setAttribute('aria-label','聊天功能'); panel.setAttribute('aria-hidden','true');
      panel.innerHTML='<div class="fw-chat-feature-pages"></div><div class="fw-chat-feature-dots"><i class="active"></i><i></i></div>';
      const host=panel.querySelector('.fw-chat-feature-pages');
      NATIVE_FEATURE_PAGES.forEach(items=>{const grid=document.createElement('div');grid.className='fw-chat-feature-grid';items.forEach(([label,glyph])=>{const b=document.createElement('button');b.type='button';b.className='fw-chat-feature-item';b.dataset.action=label;const iconContent=/^[a-z-]+\.png$/.test(glyph)?'<img src="./assets/wechat-feature-icons/'+glyph+'" alt="" draggable="false">':glyph;b.innerHTML='<span class="fw-chat-feature-icon" aria-hidden="true">'+iconContent+'</span><span>'+label+'</span>';grid.appendChild(b)});host.appendChild(grid)});
      panel.dataset.page='0'; panel.__fwShowPage=i=>{i=Math.max(0,Math.min(1,i));host.style.transform='translateX('+(-i*100)+'%)';[...panel.querySelectorAll('.fw-chat-feature-dots i')].forEach((d,n)=>d.classList.toggle('active',n===i));panel.dataset.page=String(i)};
      panel.addEventListener('click',e=>{const a=e.target.closest('[data-action]')?.dataset.action;if(!a){if(!e.target.closest('.fw-chat-feature-dots'))closeNativeFeaturePanel(panel);return;}if(a==='拍摄'){closeNativeFeaturePanel(panel);openCameraCapture();return}if(['相册','文件'].includes(a)){const f=document.createElement('input');f.type='file';f.accept=a==='文件'?'*/*':'image/*';f.hidden=true;f.onchange=()=>{closeNativeFeaturePanel(panel);showToast(f.files?.[0]?'已选择'+(a==='文件'?'文件':'图片'):'未选择内容');f.remove()};document.body.appendChild(f);f.click();return}closeNativeFeaturePanel(panel);showToast(a==='收藏'?'已收藏当前聊天':a+'功能已打开')});
      let x=0;panel.addEventListener('touchstart',e=>x=e.touches[0]?.clientX||0,{passive:true});panel.addEventListener('touchend',e=>{const dx=(e.changedTouches[0]?.clientX||x)-x;if(Math.abs(dx)>45)panel.__fwShowPage(Number(panel.dataset.page||0)+(dx<0?1:-1))},{passive:true});
    }
    const popup=nativeBottomPopup(root);
    const composerButtons=[...composer.querySelectorAll('button')];
    const replaceIcon=(button,file)=>{if(!button)return;const icon=document.createElement('img');icon.src='./assets/wechat-feature-icons/'+file;icon.alt='';icon.draggable=false;icon.className='fw-native-composer-icon';button.replaceChildren(icon)};
    replaceIcon(composerButtons[0],'composer-voice.png');
    replaceIcon(composerButtons[composerButtons.length-2],'composer-emoji.png');
    replaceIcon(composerButtons[composerButtons.length-1],'composer-plus.png');
    const micButton=composer.querySelector('.fw-native-chat-mic')||input.parentElement?.querySelector('button');
    if(micButton)replaceIcon(micButton,'composer-mic.png');
    if(!root.__fwFeatureBlankDismiss){root.__fwFeatureBlankDismiss=true;root.addEventListener('click',e=>{const open=root.querySelector(':scope > .fw-native-chat-feature-panel.is-open');if(open&&!e.target.closest('.fw-native-chat-feature-panel')&&!e.target.closest('.border-t'))closeNativeFeaturePanel(open)},true)}
    // Keep the composer fixed directly above the sheet; any React popup remains after it.
    if(composer.nextElementSibling!==panel)root.insertBefore(panel,composer.nextElementSibling);
    if(popup&&popup.previousElementSibling!==panel)root.insertBefore(panel,popup);
  }
  function closeNativeFeaturePanel(panel){panel.classList.remove('is-open');panel.setAttribute('aria-hidden','true');panel.parentElement?.classList.remove('fw-native-features-open');const popup=nativeBottomPopup(panel.parentElement);if(popup)popup.style.display=''}
  function toggleNativeFeaturePanel(panel){const open=!panel.classList.contains('is-open');panel.classList.toggle('is-open',open);panel.setAttribute('aria-hidden',String(!open));const root=panel.parentElement;root?.classList.toggle('fw-native-features-open',open);const popup=nativeBottomPopup(root);if(popup)popup.style.display=open?'none':'';if(open){panel.__fwShowPage?.(0);root?.querySelector('#conversation-input')?.blur()}}
  function onNativePlusClick(e){
    const input=document.getElementById('conversation-input');if(!input||document.querySelector('.fw-local-conversation'))return;
    const composer=input.closest('.border-t');if(!composer)return;
    const clickedButton=e.target instanceof Element?e.target.closest('button'):null;
    const buttons=[...composer.querySelectorAll('button')];
    const svg=e.target instanceof Element?e.target.closest('svg'):null;
    const svgs=[...composer.querySelectorAll('svg')];
    // The plus action is the final composer control (emoji remains untouched).
    const isPlus=clickedButton?clickedButton===buttons[buttons.length-1]:(svg&&svg===svgs[svgs.length-1]);
    if(!isPlus)return;
    const panel=composer.parentElement?.querySelector(':scope > .fw-native-chat-feature-panel');if(!panel)return;
    e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();toggleNativeFeaturePanel(panel)
  }  function applyState() {
    applyQueued = false;
    installChatPayActions();
    installGalleryAction();
    installVideoCallAction();
    installNativeConversationFeatures();
    restoreNativeSentImages();
    // Skip expensive list reconciliation while a full-screen chat is open.
    if (location.hash.includes('/wechat/conversation/') || document.querySelector('.fw-local-conversation')) {
      applyConversationFontScale();
      return;
    }
    removeLegacyWife();
    ensureLocalGroupRows();
    ensureLocalFriendRows();
    ensureLocalFriendsInAddressBook();
    const rows = getRows();
    ensureInitialPinState(rows);
    let totalUnread = 0;
    for (const row of rows) {
      const info = getChatInfo(row);
      if (!info) continue;
      row.dataset.fwChatKey = info.key;
      const generatedFriend = row.dataset.fwFriendId
        ? (Array.isArray(state.contacts) ? state.contacts : []).find(friend => friend?.id === row.dataset.fwFriendId && friend.source === '随机生成')
        : null;
      const record = chatRecord(info.key);
      // Local friend rows keep their preview/time in sync with the stored history, so records
      // added (or cleared) by the tools popup show up without rebuilding the row.
      if (row.dataset.fwFriendId) {
        const friend = (Array.isArray(state.contacts) ? state.contacts : []).find(item => item?.id === row.dataset.fwFriendId);
        if (friend) {
          const preview = row.querySelector('.fw-local-friend-copy small') || row.querySelector('small');
          if (preview) preview.textContent = chatListPreview(friend);
          row.querySelectorAll('[class*="time"],time').forEach(el => { el.textContent = chatListStamp(friend); });
        }
      }
      // Batch-created contacts always start as ordinary, unread-free chats. Reset this
      // on every render so cloned template badges/state cannot leak into a new friend.
      if (generatedFriend) {
        if (record.pinnedAt === undefined) record.pinnedAt = null;
        if (record.unreadCount === undefined) record.unreadCount = 0;
        if (!record.generatedDefaultsApplied) { record.unread = false; record.unreadOverride = true; record.generatedDefaultsApplied = true; }
      }
      const observed = readUnreadCount(row);
      if (!generatedFriend && observed > 0 && !record.unreadOverride) {
        record.unreadCount = observed;
        record.unread = true;
      } else if (!generatedFriend && record.unreadCount === undefined) {
        record.unreadCount = observed;
        record.unread = observed > 0;
      }
      if (!record.unread) record.unreadCount = 0;
      record.unread = Number(record.unreadCount) > 0;
      hideNativeUnreadNodes(row);
      row.classList.toggle('fw-chat-pinned', !!record.pinnedAt);
      row.classList.toggle('fw-chat-hidden', !!record.hidden || !!record.deleted);
      row.classList.toggle('fw-chat-unread', !!record.unread);
      renderUnreadBadge(row, Number(record.unreadCount) || 0);
      totalUnread += Number(record.unreadCount) || 0;
    }
    const container = rows[0]?.parentElement;
    if (container) {
      const pinned = rows.filter(row => !!chatRecord(getChatInfo(row).key).pinnedAt)
        .sort((a, b) => chatRecord(getChatInfo(a).key).pinnedAt - chatRecord(getChatInfo(b).key).pinnedAt);
      let needsReorder = false;
      for (let i = 0; i < pinned.length; i++) if (container.children[i] !== pinned[i]) { needsReorder = true; break; }
      if (needsReorder) container.append(...pinned, ...rows.filter(row => !pinned.includes(row)));
    }
    paintDialogueAvatars(rows);
    updateWechatTabBadge(totalUnread);
    applyDialogueFontScale(rows);
    applyConversationFontScale();
  }

  // 原生列表里的头像有些是空 src / 灰色占位，这里一次性补成稳定的内联头像。
  function paintDialogueAvatars(rows) {
    for (const row of rows) {
      const img = row.querySelector('img');
      if (!img || img.dataset.fwStableAvatar === '1') continue;
      const src = String(img.getAttribute('src') || '');
      if (src && src !== PLACEHOLDER_AVATAR && !src.startsWith('data:image/svg+xml') && !/\/assets\/user-avatar-/.test(src)) continue;
      const info = getChatInfo(row);
      const name = String(info?.name || row.textContent || '').trim().slice(0, 12) || '微信好友';
      img.src = apiAvatarFor(name);
      img.style.marginRight = '20px';
      img.onerror = () => { img.onerror = null; img.src = bundledAvatarFor(name); };
      img.dataset.fwStableAvatar = '1';
    }
  }

  function applyDialogueFontScale(rows = getRows()) {
    for (const row of rows) {
      if (!row.dataset.fwBasePaddingTop) {
        const style = getComputedStyle(row);
        row.dataset.fwBasePaddingTop = style.paddingTop;
        row.dataset.fwBasePaddingBottom = style.paddingBottom;
        // The bundled dialogue rows have a fixed intrinsic height, so padding alone
        // does not make room for enlarged text. Keep the measured baseline and scale
        // the row's minimum height as well, preserving its width and avatar size.
        row.dataset.fwBaseMinHeight = row.getBoundingClientRect().height + 'px';
      }
      row.style.paddingTop = (parseFloat(row.dataset.fwBasePaddingTop) * userFontScale) + 'px';
      row.style.paddingBottom = (parseFloat(row.dataset.fwBasePaddingBottom) * userFontScale) + 'px';
      row.style.minHeight = (parseFloat(row.dataset.fwBaseMinHeight) * userFontScale) + 'px';
      row.style.boxSizing = 'border-box';
      for (const el of row.querySelectorAll('*')) {
        if (![...el.childNodes].some(node => node.nodeType === Node.TEXT_NODE && node.textContent.trim())) continue;
        if (!el.dataset.fwBaseFontSize) el.dataset.fwBaseFontSize = getComputedStyle(el).fontSize;
        el.style.fontSize = (parseFloat(el.dataset.fwBaseFontSize) * userFontScale) + 'px';
      }
    }
  }

  function queueApply() {
    if (applyQueued) return;
    applyQueued = true;
    requestAnimationFrame(applyState);
  }

  function addOverlay(kind, className = 'fw-overlay--transparent') {
    closeOverlay();
    overlayClickGuardUntil = 0;
    overlay = document.createElement('div');
    overlay.className = `fw-overlay ${className}`;
    overlay.dataset.kind = kind;
    document.body.appendChild(overlay);
    popupKind = kind;
    const dismissMenuFromOutside = event => {
      if (event.target !== overlay || (popupKind !== 'plus' && popupKind !== 'context')) return;
      // Dismiss on the initial outside tap, not only on the synthesized click.
      // Android WebView can consume/retarget that click after a long-press gesture.
      event.preventDefault();
      event.stopImmediatePropagation();
      closeOverlay();
    };
    overlay.addEventListener('pointerdown', dismissMenuFromOutside, true);
    overlay.addEventListener('touchstart', dismissMenuFromOutside, { capture: true, passive: false });
    overlay.addEventListener('click', dismissMenuFromOutside, true);
    return overlay;
  }

  function closeOverlay() {
    if (contextTarget) contextTarget.classList.remove('fw-chat-context-target', 'fw-chat-menu-open');
    contextTarget = null;
    const closingOverlay = overlay;
    const closingKind = popupKind;
    overlay = null;
    popupKind = null;
    if (!closingOverlay) return;
    // Swallow the synthesized click that follows the tap which closed this overlay.
    overlayClickGuardUntil = Date.now() + 450;
    if (closingKind === 'plus') {
      closingOverlay.classList.add('fw-menu-closing');
      window.setTimeout(() => closingOverlay.remove(), 230);
    } else {
      closingOverlay.remove();
    }
  }

  /**
   * Android 返回键：先关掉最上面那层 fw-overlay（+ 菜单 / 添加朋友 / 发起群聊 / 各类弹窗），
   * 关掉了就不要再退模块。返回链由 WeiHomeActivity.onBackPressed 依次调用。
   */
  window.__fwCloseOverlay = function () {
    // 「个人信息 → 我的二维码」是整页而不是弹层，返回键也要先把它收掉。
    if (document.querySelector('.fw-my-qr')) { closeMyQrPage(); return true; }
    // 「切换账号」同样是整页，返回键先把它收掉。
    if (document.querySelector('.fw-accounts')) { closeAccountsPage(); return true; }
    const open = !!document.querySelector('.fw-overlay');
    if (open) closeOverlay();
    return open;
  };

  // Runs in the capture phase before any row/menu handler, so a stray post-close click
  // never opens the chat underneath the dismissed dialog.
  document.addEventListener('click', event => {
    if (Date.now() >= overlayClickGuardUntil) return;
    overlayClickGuardUntil = 0;
    if (event.target instanceof Element && event.target.closest('.fw-overlay')) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, true);

  function iconButton(label, icon, action) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'fw-menu-item';
    button.setAttribute('aria-label', label);
    button.innerHTML = `<img class="fw-menu-icon" src="${icon}" alt="" aria-hidden="true"><span>${label}</span>`;
    button.addEventListener('click', event => { event.stopPropagation(); action(); });
    return button;
  }

  function openPlusMenu(anchor) {
    const panel = addOverlay('plus').appendChild(document.createElement('div'));
    panel.className = 'fw-plus-panel';
    panel.setAttribute('role', 'menu');
    const rect = anchor.getBoundingClientRect();
    panel.style.setProperty('--fw-menu-top', `${Math.round(rect.bottom + 13)}px`);
    PLUS_LABELS.forEach((label, index) => panel.appendChild(iconButton(label, ICONS[index], () => openFeature(label))));
  }

  function findPlusIcon(target) {
    const svg = target instanceof Element ? target.closest('svg') : null;
    const actions = svg?.closest('.wechat-topbar__actions');
    if (!actions) return null;
    const icons = [...actions.querySelectorAll('svg')];
    return icons[icons.length - 1] === svg ? svg : null;
  }

  function findRow(target) {
    if (!(target instanceof Element)) return null;
    const row = target.closest('[class*="chat-item_"], .fw-local-friend-row');
    return row && getChatInfo(row) ? row : null;
  }

  function openContextMenu(row, clientX) {
    const rect = row.getBoundingClientRect();
    const menuWidth = 132;
    const menuHeight = 198;
    const half = menuWidth / 2;
    const x = Math.max(half + 6, Math.min(innerWidth - half - 6, clientX));
    const top = Math.max(84, Math.min(rect.bottom - 12, innerHeight - menuHeight - 76));
    const panel = addOverlay('context').appendChild(document.createElement('div'));
    panel.className = 'fw-context-panel';
    panel.setAttribute('role', 'menu');
    panel.style.left = `${Math.round(x - half)}px`;
    panel.style.top = `${Math.round(top)}px`;
    contextTarget = row;
    // Restore the row while the action menu is visible; only finger-down is highlighted.
    row.classList.remove('fw-chat-pressed');
    row.classList.add('fw-chat-menu-open');
    const info = getChatInfo(row);
    const labels = [
      chatRecord(info.key).unread ? '标为已读' : '标为未读',
      chatRecord(info.key).pinnedAt ? '取消置顶' : CHAT_LABELS[1],
      CHAT_LABELS[2],
      CHAT_LABELS[3]
    ];
    labels.forEach((label, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'fw-context-item';
      button.setAttribute('role', 'menuitem');
      button.textContent = label;
      button.addEventListener('click', event => {
        event.preventDefault();
        event.stopImmediatePropagation();
        // Keep a stable key: after React re-renders the list the original row
        // reference may no longer be attached, but the action must still apply.
        performChatAction(info.key, index);
      });
      panel.appendChild(button);
    });
  }

  function performChatAction(key, index) {
    const record = chatRecord(key);
    if (record.pinnedAt === undefined) record.pinnedAt = null;
    if (index === 0) { record.unread = !record.unread; record.unreadOverride = true; record.unreadCount = record.unread ? Math.max(1, Number(record.unreadCount) || 1) : 0; }
    if (index === 1) {
      if (record.pinnedAt) {
        delete record.pinnedAt;
        record.pinCancelExplicit = true;
      } else {
        record.pinnedAt = Date.now();
        delete record.pinCancelExplicit;
      }
    }
    if (index === 2) record.hidden = !record.hidden;
    if (index === 3) record.deleted = true;
    saveState();
    closeOverlay();
    queueApply();
    const message = index === 0 ? (record.unread ? '已标为未读' : '已标为已读')
      : index === 1 ? (record.pinnedAt ? '已置顶该聊天' : '已取消置顶')
      : index === 2 ? (record.hidden ? '已不显示该聊天' : '已恢复显示')
      : '已删除该聊天';
    showToast(message);
  }

  function showToast(text) {
    const host = addOverlay('toast');
    host.style.pointerEvents = 'none';
    const toast = document.createElement('div');
    toast.className = 'fw-toast';
    toast.textContent = text;
    host.appendChild(toast);
    setTimeout(() => { if (overlay === host) closeOverlay(); }, 1500);
  }

  function makeDialog(title, hint, bodyHTML, buttons) {
    const host = addOverlay('dialog', 'fw-overlay--transparent');
    const backdrop = document.createElement('div');
    backdrop.className = 'fw-dialog-backdrop';
    host.appendChild(backdrop);
    const dialog = document.createElement('section');
    dialog.className = 'fw-dialog';
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('aria-modal', 'true');
    dialog.innerHTML = `<h2 class="fw-dialog__title"></h2><div class="fw-dialog__body"><p class="fw-dialog__hint"></p>${bodyHTML}</div><div class="fw-dialog__actions"></div>`;
    dialog.querySelector('.fw-dialog__title').textContent = title;
    dialog.querySelector('.fw-dialog__hint').textContent = hint;
    backdrop.appendChild(dialog);
    backdrop.addEventListener('click', event => { if (event.target === backdrop) closeOverlay(); });
    const actionBar = dialog.querySelector('.fw-dialog__actions');
    buttons.forEach(({ label, onClick, primary = false }) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'fw-dialog__button';
      button.textContent = label;
      if (primary) button.style.color = '#07a84f';
      button.addEventListener('click', onClick);
      actionBar.appendChild(button);
    });
    return { host, dialog };
  }

  function prepareConversationTransition() {
    document.body.classList.remove('fw-conversation-route-enter');
    document.body.classList.add('fw-conversation-route-enter');
    setTimeout(() => document.body.classList.remove('fw-conversation-route-enter'), 360);
    setTimeout(applyConversationFontScale, 40);
    setTimeout(applyConversationFontScale, 220);
  }

  function applyFontScale(value) {
    const scale = Math.max(0.8, Math.min(1.4, Number(value) || 1));
    userFontScale = scale;
    document.documentElement.style.setProperty('--fw-user-scale', scale.toFixed(2));
    applyDialogueFontScale();
    applyConversationFontScale();
    document.body.classList.add('fw-user-scale-enabled');
    try { localStorage.setItem('fake-world.wechat.font-scale', scale.toFixed(2)); } catch (_) { /* no-op */ }
    return scale;
  }

  function openFontSizeDialog() {
    let savedScale = 1;
    let bold = false;
    try {
      savedScale = Number(localStorage.getItem('fake-world.wechat.font-scale')) || 1;
      bold = localStorage.getItem('fake-world.wechat.font-bold') === '1';
    } catch (_) { /* no-op */ }
    const host = addOverlay('font-size', 'fw-overlay--font-size');
    const page = document.createElement('section');
    page.className = 'fw-font-page';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-label', '字体大小');
    page.innerHTML =       '<header class="fw-font-header"><button type="button" class="fw-font-back" aria-label="返回">‹</button><span>字体大小</span><button type="button" class="fw-font-bold">字体加粗</button></header>' +
      '<main class="fw-font-preview"><div class="fw-font-time">22:17</div>' +
      '<div class="fw-font-message"><span class="fw-font-avatar">微</span><div class="fw-font-bubble">可玩性功能太多了，很多页面标题头部点击都有惊喜哦！</div></div>' +
      '<div class="fw-font-message"><span class="fw-font-avatar">微</span><div class="fw-font-bubble">不懂操作就找客服小姐姐，溜咯～</div></div>' +
      '<div class="fw-font-message"><span class="fw-font-avatar">微</span><div class="fw-font-bubble fw-font-picture">表情图片</div></div>' +
      '<div class="fw-font-message fw-font-message--out"><div class="fw-font-bubble fw-font-bubble--out">好的，手拿把掐😺</div><span class="fw-font-avatar fw-font-avatar--out">喵</span></div></main>' +
      '<footer class="fw-font-controls"><div class="fw-font-scale-labels"><span class="fw-font-scale-min">A</span><span class="fw-font-scale-value"></span><span class="fw-font-scale-max">A</span></div><input class="fw-font-scale-slider" type="range" min="80" max="140" step="1" aria-label="字体大小"><button type="button" class="fw-font-reset">重置默认大小</button></footer>';
    host.appendChild(page);
    const slider = page.querySelector('.fw-font-scale-slider');
    const update = () => {
      const scale = applyFontScale(Number(slider.value) / 100);
      page.querySelector('.fw-font-scale-value').textContent = scale === 1 ? '适中' : Math.round(scale * 100) + '%';
      page.style.setProperty('--fw-preview-scale', scale);
    };
    slider.value = String(Math.round(savedScale * 100));
    update();
    page.classList.toggle('fw-font-is-bold', bold);
    document.body.classList.toggle('fw-user-bold', bold);
    page.querySelector('.fw-font-back').addEventListener('click', closeOverlay);
    page.querySelector('.fw-font-bold').addEventListener('click', () => {
      bold = !bold;
      page.classList.toggle('fw-font-is-bold', bold);
      document.body.classList.toggle('fw-user-bold', bold);
      try { localStorage.setItem('fake-world.wechat.font-bold', bold ? '1' : '0'); } catch (_) { /* no-op */ }
    });
    slider.addEventListener('input', update);
    slider.addEventListener('change', update);
    page.querySelector('.fw-font-reset').addEventListener('click', () => { slider.value = '100'; update(); });
  }

  function openKeywordReplyDialog() {
    let saved = '';
    try { saved = localStorage.getItem('fake-world.wechat.keyword-reply') || ''; } catch (_) { /* no-op */ }
    const { dialog } = makeDialog('聊天关键词自动回复', '每行一条“关键词=回复内容”，仅在本地演示中保存', '<textarea class="fw-textarea fw-keyword-reply" placeholder="你好=你好呀\n在吗=我现在在线"></textarea>', [
      { label: '取消', onClick: closeOverlay },
      { label: '保存', primary: true, onClick: () => {
        const value = dialog.querySelector('.fw-keyword-reply').value;
        try { localStorage.setItem('fake-world.wechat.keyword-reply', value); } catch (_) { /* no-op */ }
        closeOverlay(); showToast('自动回复规则已保存');
      } }
    ]);
    dialog.querySelector('.fw-keyword-reply').value = saved;
  }

  // ---- 批量添加聊天记录 (2026-10-11) -------------------------------------
  // Drives the random-generation rule set ported from ../name
  // (overrides/name-rules.js). Records either land in a chat the user picks
  // from 微信, or create fresh random contacts/groups - so the feature also
  // works from an empty chat list. Line-by-line entry stays as a second mode.

  function batchExistingLabels() {
    const out = [];
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => {
      const label = String(friend?.remark || friend?.name || '').trim();
      if (label) out.push(label);
    });
    (Array.isArray(state.groups) ? state.groups : []).forEach(group => {
      const label = String(group?.name || '').trim();
      if (label) out.push(label);
    });
    return out;
  }

  // Conversations the batch dialog can write into: exactly what the 微信 list shows.
  function batchTargetEntries() {
    const groups = Array.isArray(state.groups) ? state.groups : [];
    return getRows()
      .filter(row => row.offsetParent !== null)
      .map(row => {
        const info = getChatInfo(row);
        if (!info || !info.name) return null;
        const groupId = row.dataset.fwGroupId || '';
        const group = groupId ? groups.find(item => item && String(item.id) === String(groupId)) : null;
        return {
          key: info.key,
          name: info.name,
          avatar: info.avatar || localPhotoFor(info.name),
          friendId: row.dataset.fwFriendId || '',
          groupId,
          members: group && Array.isArray(group.members)
            ? group.members.map(name => String(name || '').trim()).filter(Boolean)
            : [],
        };
      })
      .filter(Boolean);
  }

  // Resolves a picked chat to the object that owns its stored messages, so a
  // group target writes into the group instead of spawning a 1:1 duplicate.
  function resolveBatchTarget(target) {
    if (!target || !target.name) return null;
    if (target.groupId) {
      const group = (Array.isArray(state.groups) ? state.groups : [])
        .find(item => item && String(item.id) === String(target.groupId));
      if (group) { group.messages = Array.isArray(group.messages) ? group.messages : []; return group; }
    }
    return ensureChatFriend(target.name);
  }

  function batchTouch() {
    saveState();
    queueApply();
    // Second pass: group rows can only clone a template once friend rows exist.
    setTimeout(applyState, 90);
    setTimeout(applyState, 240);
  }

  function hideBatchGreeting(target) {
    if (!target) return;
    const greeting = target.greeting && typeof target.greeting === 'object' ? target.greeting : (target.greeting = {});
    greeting.mine = Object.assign({ side: 'outgoing' }, greeting.mine, { deleted: true });
    greeting.friend = Object.assign({ side: 'incoming' }, greeting.friend, { deleted: true });
  }

  function messagesFromManualLines(text) {
    const now = Date.now();
    const lines = String(text || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    const messages = [];
    lines.forEach((line, index) => {
      const mine = line.match(/^(我|自己|本人|me|self)\s*[|｜:：]\s*(.+)$/i);
      const body = mine ? mine[2].trim() : line;
      if (!body) return;
      messages.push({
        kind: 'text', text: body, side: mine ? 'outgoing' : 'incoming',
        createdAt: now - (lines.length - index) * 60000 - 20000,
      });
    });
    return messages;
  }

  function appendGeneratedMessages(target, messages, label) {
    if (!target || !messages.length) return 0;
    target.messages = Array.isArray(target.messages) ? target.messages : [];
    messages.forEach(message => {
      if (message.type === 'transfer') message.friendName = label;
      target.messages.push(message);
      if (message.type === 'transfer') {
        const value = Math.abs(Number(message.amount) || 0);
        pushTransferBill(message, messageSide(message) === 'incoming' ? value : -value, label, target);
      }
    });
    hideBatchGreeting(target);
    const record = chatRecord(label);
    record.deleted = false;
    record.hidden = false;
    record.unread = false;
    record.unreadCount = 0;
    record.unreadOverride = true;
    return messages.length;
  }

  function applyGeneratedRecords(conversations, target) {
    if (!Array.isArray(conversations) || !conversations.length) return { added: 0, conversations: 0, created: 0 };
    const avatarFor = index => avatarUrl(((Math.floor(index) - 1) % AVATAR_POOL_SIZE + AVATAR_POOL_SIZE) % AVATAR_POOL_SIZE + 1);
    const targetFriend = target ? resolveBatchTarget(target) : null;
    let added = 0;
    let created = 0;
    if (targetFriend) {
      const label = String(targetFriend.remark || targetFriend.name || target.name || '好友');
      conversations.forEach(conv => { added += appendGeneratedMessages(targetFriend, conv.messages, label); });
      batchTouch();
      return { added, conversations: 1, created: 0 };
    }
    state.contacts = Array.isArray(state.contacts) ? state.contacts : [];
    state.groups = Array.isArray(state.groups) ? state.groups : [];
    const taken = new Set(batchExistingLabels());
    const uniqueLabel = base => {
      let label = String(base || '').trim() || '好友';
      let candidate = label;
      let n = 2;
      while (taken.has(candidate)) { candidate = label + '(' + n + ')'; n += 1; }
      taken.add(candidate);
      return candidate;
    };
    conversations.forEach(conv => {
      if (conv.isGroup) {
        const members = (Array.isArray(conv.members) ? conv.members : [])
          .map(member => String(member && member.name || '').trim()).filter(Boolean);
        const memberAvatars = (Array.isArray(conv.members) ? conv.members : [])
          .map(member => avatarFor(member && member.avatarIndex || 1));
        const base = members.slice(0, 3).join('、') || '群聊';
        const name = uniqueLabel(members.length > 3 ? base + '等' : base);
        conv.messages.forEach(message => { if (message.type === 'transfer') message.friendName = name; });
        state.groups.unshift({
          id: 'group-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6),
          name, members, memberAvatars, createdAt: Date.now(), messages: conv.messages.slice(),
        });
        created += 1;
      } else {
        const name = uniqueLabel(conv.remark || conv.name || conv.nickname || '');
        conv.messages.forEach(message => { if (message.type === 'transfer') message.friendName = name; });
        const friend = {
          id: 'chat-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6),
          name, nickname: conv.nickname || '', wechatId: 'wx' + Math.random().toString(36).slice(2, 12),
          gender: ['女', '男', '未知'][Math.floor(Math.random() * 3)], remark: '', realName: '',
          phone: '', region: conv.region || '', verification: '你加TA(需验证)', source: '随机生成',
          avatar: avatarFor((conv.avatarIndex || 1) + created), starred: false, messages: conv.messages.slice(),
          createdAt: Date.now(), generatedProfileVersion: 2,
        };
        // A fabricated history should not open with the demo verification lines.
        hideBatchGreeting(friend);
        state.contacts.unshift(friend);
        const record = chatRecord(name);
        record.deleted = false;
        record.hidden = false;
        record.unread = false;
        record.unreadCount = 0;
        record.unreadOverride = true;
        record.pinnedAt = null;
        record.generatedDefaultsApplied = true;
        created += 1;
      }
      added += conv.messages.length;
    });
    batchTouch();
    return { added, conversations: conversations.length, created };
  }

  function batchIsoDate(date) {
    const pad = value => String(value).padStart(2, '0');
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }

  // Android WebView never shows the native picker for <input type="date">, so the
  // time range is chosen from a month grid rendered inside the dialog instead.
  function openBatchDatePicker(current, onPick) {
    const today = new Date();
    const anchor = current ? new Date(current + 'T00:00:00') : today;
    let year = anchor.getFullYear();
    let month = anchor.getMonth();
    const body = '<div class="fw-cal">'
      + '<div class="fw-cal__head"><button type="button" data-cal-prev aria-label="上个月">‹</button><strong data-cal-label></strong><button type="button" data-cal-next aria-label="下个月">›</button></div>'
      + '<div class="fw-cal__week"><span>日</span><span>一</span><span>二</span><span>三</span><span>四</span><span>五</span><span>六</span></div>'
      + '<div class="fw-cal__grid" data-cal-grid></div>'
      + '</div>';
    const { dialog } = makeDialog('选择日期', '选择生成聊天记录的时间范围', body, [
      { label: '取消', onClick: closeOverlay },
      { label: '今天', primary: true, onClick: () => { const picked = batchIsoDate(new Date()); closeOverlay(); onPick(picked); } },
    ]);
    const grid = dialog.querySelector('[data-cal-grid]');
    const label = dialog.querySelector('[data-cal-label]');
    const pad = value => String(value).padStart(2, '0');
    const render = () => {
      label.textContent = year + '年' + (month + 1) + '月';
      grid.replaceChildren();
      const firstDow = new Date(year, month, 1).getDay();
      const days = new Date(year, month + 1, 0).getDate();
      for (let i = 0; i < firstDow; i += 1) {
        const blank = document.createElement('span');
        blank.className = 'fw-cal__cell is-blank';
        grid.appendChild(blank);
      }
      for (let day = 1; day <= days; day += 1) {
        const iso = year + '-' + pad(month + 1) + '-' + pad(day);
        const cell = document.createElement('button');
        cell.type = 'button';
        cell.className = 'fw-cal__cell';
        cell.textContent = String(day);
        if (iso === current) cell.classList.add('is-sel');
        if (iso === batchIsoDate(today)) cell.classList.add('is-today');
        cell.addEventListener('click', () => { closeOverlay(); onPick(iso); });
        grid.appendChild(cell);
      }
    };
    dialog.querySelector('[data-cal-prev]').addEventListener('click', () => { month -= 1; if (month < 0) { month = 11; year -= 1; } render(); });
    dialog.querySelector('[data-cal-next]').addEventListener('click', () => { month += 1; if (month > 11) { month = 0; year += 1; } render(); });
    render();
  }

  function openBatchRecordsDialog() {
    if (!window.__fwNameGen) { showToast('随机规则未加载'); return; }
    openBatchRecordsDialogWithDraft({
      uiMode: 'random', selected: [], per: '5', amount: '', start: '', end: '', text: '',
    });
  }

  function openBatchRecordsDialogWithDraft(draft) {
    const gen = window.__fwNameGen;
    const html = value => String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    const entries = batchTargetEntries();
    const selectedKeys = new Set(draft.selected.map(item => item.key));
    const rowsHtml = entries.length
      ? entries.map(entry => {
        const on = selectedKeys.has(entry.key);
        return '<button type="button" class="fw-batch-row' + (on ? ' is-on' : '') + '" data-key="' + html(entry.key) + '">'
          + '<span class="fw-batch-row__check">' + (on ? '✓' : '') + '</span>'
          + '<img alt="" src="' + html(entry.avatar) + '">'
          + '<span class="fw-batch-row__name">' + html(entry.name) + '</span>'
          + (entry.groupId ? '<i class="fw-batch-row__tag">群</i>' : '')
          + '</button>';
      }).join('')
      : '<p class="fw-batch-empty">微信列表里还没有聊天，请先在微信里建立会话</p>';
    const body = '<div class="fw-batch-form">'
      + '<div class="fw-batch-seg fw-batch-seg--mode" data-group="ui">'
      + [['random', '随机生成'], ['manual', '手动输入']].map(([value, text]) =>
        '<button type="button" data-value="' + value + '"' + (value === draft.uiMode ? ' class="is-active"' : '') + '>' + text + '</button>').join('')
      + '</div>'
      + '<div class="fw-batch-rowhead"><span>目标会话<b class="fw-batch-picked"></b></span>'
      + '<span class="fw-batch-links"><button type="button" data-all>全选</button><button type="button" data-none>清空</button></span></div>'
      + '<div class="fw-batch-list">' + rowsHtml + '</div>'
      + '<div class="fw-batch-random">'
      + '<label class="fw-batch-field"><span>每个会话</span><input class="fw-batch-per" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="off" value="' + html(draft.per) + '"><em>条</em></label>'
      + '<label class="fw-batch-field"><span>金额范围</span><input class="fw-batch-amount" type="text" inputmode="decimal" placeholder="留空随机 · 如 200-1000" value="' + html(draft.amount) + '"></label>'
      + '<div class="fw-batch-field"><span>时间范围</span><button type="button" class="fw-batch-date" data-date="start"></button><i>至</i><button type="button" class="fw-batch-date" data-date="end"></button></div>'
      + '<p class="fw-batch-hint">每个选中的会话生成相同条数，合计上限 100 条；时间留空默认最近 7 天。单聊/群聊与消息类型按所选会话自动判断。</p>'
      + '</div>'
      + '<div class="fw-batch-manual" hidden><textarea class="fw-textarea fw-batch-records" placeholder="每行一条消息，行首加“我|”表示自己发出的&#10;在吗？明天有空吗&#10;我|在的，明天上午都行"></textarea></div>'
      + '</div>';
    let submit = null;
    const { dialog } = makeDialog('批量添加聊天记录', '勾选要写入的会话，按规则随机生成聊天记录（纯本地演示）', body, [
      { label: '取消', onClick: closeOverlay },
      { label: '生成', primary: true, onClick: () => { if (submit) submit(); } },
    ]);
    dialog.classList.add('fw-dialog--tall');

    const area = dialog.querySelector('.fw-batch-records');
    area.value = draft.text || '';
    area.addEventListener('input', () => { draft.text = area.value; });
    const perInput = dialog.querySelector('.fw-batch-per');
    perInput.addEventListener('input', () => { draft.per = perInput.value; });
    const amountInput = dialog.querySelector('.fw-batch-amount');
    amountInput.addEventListener('input', () => { draft.amount = amountInput.value; });
    const syncInputs = () => {
      draft.per = perInput.value;
      draft.amount = amountInput.value;
      draft.text = area.value;
    };
    const paintDates = () => {
      dialog.querySelectorAll('.fw-batch-date').forEach(button => {
        const value = button.dataset.date === 'start' ? draft.start : draft.end;
        button.textContent = value || (button.dataset.date === 'start' ? '开始日期' : '结束日期');
        button.classList.toggle('is-set', !!value);
      });
    };
    const paintPicked = () => {
      const total = draft.selected.length;
      dialog.querySelector('.fw-batch-picked').textContent = total ? '（已选 ' + total + '）' : '（未选择）';
    };
    const paintMode = () => {
      const manual = draft.uiMode === 'manual';
      dialog.querySelector('.fw-batch-random').hidden = manual;
      dialog.querySelector('.fw-batch-manual').hidden = !manual;
    };

    dialog.querySelector('.fw-batch-seg').addEventListener('click', event => {
      const button = event.target.closest('button[data-value]');
      if (!button) return;
      event.preventDefault();
      [...event.currentTarget.querySelectorAll('button')].forEach(item => item.classList.toggle('is-active', item === button));
      draft.uiMode = button.dataset.value;
      paintMode();
    });
    dialog.querySelector('.fw-batch-list').addEventListener('click', event => {
      const row = event.target.closest('.fw-batch-row');
      if (!row) return;
      event.preventDefault();
      const entry = entries.find(item => item.key === row.dataset.key);
      if (!entry) return;
      const index = draft.selected.findIndex(item => item.key === entry.key);
      if (index >= 0) draft.selected.splice(index, 1); else draft.selected.push(entry);
      const on = index < 0;
      row.classList.toggle('is-on', on);
      row.querySelector('.fw-batch-row__check').textContent = on ? '✓' : '';
      paintPicked();
    });
    const setAll = on => {
      draft.selected = on ? entries.slice() : [];
      dialog.querySelectorAll('.fw-batch-row').forEach(row => {
        row.classList.toggle('is-on', on);
        row.querySelector('.fw-batch-row__check').textContent = on ? '✓' : '';
      });
      paintPicked();
    };
    dialog.querySelector('[data-all]').addEventListener('click', event => { event.preventDefault(); setAll(true); });
    dialog.querySelector('[data-none]').addEventListener('click', event => { event.preventDefault(); setAll(false); });
    dialog.querySelectorAll('.fw-batch-date').forEach(button => button.addEventListener('click', event => {
      event.preventDefault();
      syncInputs();
      const key = button.dataset.date;
      openBatchDatePicker(draft[key], picked => {
        draft[key] = picked;
        openBatchRecordsDialogWithDraft(draft);
      });
    }));

    submit = () => {
      syncInputs();
      if (!draft.selected.length) { showToast('请先勾选要写入的会话'); return; }
      if (draft.uiMode === 'manual') {
        const lines = messagesFromManualLines(area.value);
        if (!lines.length) { showToast('没有可添加的内容，请每行输入一条消息'); return; }
        let added = 0;
        draft.selected.forEach(target => {
          const friend = resolveBatchTarget(target);
          if (friend) added += appendGeneratedMessages(friend, lines.map(line => Object.assign({}, line)), String(friend.remark || friend.name || target.name));
        });
        closeOverlay();
        batchTouch();
        showToast(added ? ('已生成 ' + added + ' 条聊天记录') : '没有生成记录');
        return;
      }
      const per = Math.max(1, Math.floor(Number(draft.per) || 5));
      const planned = per * draft.selected.length;
      if (planned > gen.MAX_RECORDS_PER_BATCH) {
        showToast('共 ' + planned + ' 条，超过上限 ' + gen.MAX_RECORDS_PER_BATCH + ' 条，请减少条数或会话');
        return;
      }
      let amount = null;
      try {
        amount = gen.parseRangeSpec(draft.amount);
      } catch (error) { showToast(error.message); return; }
      const start = draft.start ? new Date(draft.start + 'T00:00:00') : null;
      const end = draft.end ? new Date(draft.end + 'T23:59:59') : null;
      if (start && end && start > end) { showToast('开始日期不能晚于结束日期'); return; }
      const taken = new Set(batchExistingLabels());
      // 类型由所选会话自动判断：群聊用群聊权重，其余按单聊处理。
      const build = mode => gen.generateConversations({
        count: per, contacts: 1, mode, content: 'all', amount,
        start: start || undefined, end: end || undefined,
        takenNames: taken, avatarCount: AVATAR_POOL_SIZE,
      })[0];
      let added = 0;
      draft.selected.forEach(target => {
        const friend = resolveBatchTarget(target);
        if (!friend) return;
        const label = String(friend.remark || friend.name || target.name);
        const isGroup = !!(target.groupId || friend.isGroup);
        const conv = build(isGroup ? 'group' : 'chat');
        if (!conv) return;
        // A group line must be spoken by a real member of that group, otherwise the
        // transcript shows stranger names and an avatar that cannot be resolved.
        if (isGroup) {
          const members = (Array.isArray(target.members) && target.members.length)
            ? target.members
            : (Array.isArray(friend.members) ? friend.members : [])
              .map(name => String(name || '').trim())
              .filter(name => name && name !== label);
          if (members.length) {
            conv.messages.forEach(message => {
              if (message.side === 'incoming') message.sender = members[Math.floor(Math.random() * members.length)];
            });
          }
        }
        added += appendGeneratedMessages(friend, conv.messages, label);
      });
      closeOverlay();
      if (!added) { showToast('没有生成任何记录'); return; }
      batchTouch();
      showToast('已在 ' + draft.selected.length + ' 个会话生成 ' + added + ' 条聊天记录');
    };
    paintMode();
    paintDates();
    paintPicked();
  }

  function openBatchChatPicker(currentKey, onPick) {
    const entries = getRows()
      .filter(row => row.offsetParent !== null)
      .map(row => {
        const info = getChatInfo(row);
        if (!info || !info.name) return null;
        return { key: info.key, name: info.name, avatar: info.avatar || localPhotoFor(info.name), friendId: row.dataset.fwFriendId || '', groupId: row.dataset.fwGroupId || '' };
      })
      .filter(Boolean);
    const { dialog } = makeDialog('选择聊天', '只能选择微信列表里已经存在的聊天', '<div class="fw-tl-picker__rows fw-batch-targets"></div>', [
      { label: '取消', onClick: closeOverlay },
    ]);
    const host = dialog.querySelector('.fw-batch-targets');
    if (!entries.length) {
      const empty = document.createElement('p');
      empty.className = 'fw-tl-picker__hint';
      empty.textContent = '微信列表里还没有聊天';
      host.appendChild(empty);
      return;
    }
    entries.forEach(entry => {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'fw-tl-picker__row' + (entry.key === currentKey ? ' is-on' : '');
      const img = document.createElement('img');
      img.alt = '';
      img.src = entry.avatar;
      img.onerror = () => { img.onerror = null; img.src = localPhotoFor(entry.name); };
      const label = document.createElement('span');
      label.textContent = entry.name;
      const mark = document.createElement('i');
      mark.textContent = entry.key === currentKey ? '✓' : '';
      row.append(img, label, mark);
      row.addEventListener('click', () => { closeOverlay(); onPick(entry); });
      host.appendChild(row);
    });
  }

  // Builds a believable conversation: each line becomes a real stored message with its own
  // timestamp, and the chat-list row keeps a matching preview + time afterwards.
  function applyBatchRecords(draft) {
    const lines = String(draft?.text || '').split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    if (!lines.length) return { added: 0 };
    const friend = ensureChatFriend(draft.name);
    if (!friend) return { added: 0 };
    const messages = Array.isArray(friend.messages) ? friend.messages : (friend.messages = []);
    const now = Date.now();
    let added = 0;
    lines.forEach((line, index) => {
      const mine = line.match(/^(我|自己|本人|me|self)\s*[|｜:：]\s*(.+)$/i);
      const body = mine ? mine[2].trim() : line;
      if (!body) return;
      messages.push({
        text: body,
        side: mine ? 'outgoing' : 'incoming',
        // Older lines get earlier timestamps so the transcript reads in order (1 min apart).
        createdAt: now - (lines.length - index) * 60000 - 20000,
      });
      added += 1;
    });
    if (!added) return { added: 0 };
    // Fabricated histories should not open with the "新朋友验证" boilerplate the demo uses.
    const greeting = friend.greeting && typeof friend.greeting === 'object' ? friend.greeting : (friend.greeting = {});
    greeting.mine = Object.assign({ side: 'outgoing' }, greeting.mine, { deleted: true });
    greeting.friend = Object.assign({ side: 'incoming' }, greeting.friend, { deleted: true });
    const record = chatRecord(String(friend.remark || friend.name || '').trim());
    record.deleted = false;
    record.hidden = false;
    record.unreadCount = 0;
    record.unread = false;
    saveState();
    queueApply();
    setTimeout(applyState, 90);
    return { added };
  }

  function openClearRecordsDialog() {
    const { dialog } = makeDialog('清空所有聊天记录', '将删除全部聊天记录（含批量添加的内容），无法撤销', '<p>清空后微信列表不再显示任何聊天，好友和朋友圈不受影响。</p>', [
      { label: '取消', onClick: closeOverlay },
      { label: '清空', primary: true, onClick: () => {
        const removed = clearAllChatRecords();
        closeOverlay();
        showToast(removed ? ('已清空 ' + removed + ' 个聊天的记录') : '没有可清空的聊天记录');
      } }
    ]);
    return dialog;
  }

  // Real wipe: every message of every conversation is deleted, but the contacts and the chat
  // list rows survive - opening a chat afterwards shows an empty conversation.
  function clearAllChatRecords() {
    const rows = getRows();
    const keys = new Set();
    rows.forEach(row => {
      const info = getChatInfo(row);
      if (info && info.name) keys.add(info.name);
    });
    const blankGreeting = friend => {
      const greeting = friend.greeting && typeof friend.greeting === 'object' ? friend.greeting : (friend.greeting = {});
      greeting.mine = Object.assign({ side: 'outgoing' }, greeting.mine, { deleted: true });
      greeting.friend = Object.assign({ side: 'incoming' }, greeting.friend, { deleted: true });
    };
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => {
      if (Array.isArray(friend.messages)) friend.messages = [];
      blankGreeting(friend);
    });
    (Array.isArray(state.groups) ? state.groups : []).forEach(group => {
      if (Array.isArray(group.messages)) group.messages = [];
      blankGreeting(group);
    });
    // Keep every conversation in the list, just without its history or badges.
    keys.forEach(key => {
      const record = chatRecord(key);
      record.deleted = false;
      record.hidden = false;
      record.unreadCount = 0;
      record.unread = false;
      record.pinnedAt = null;
      record.unreadOverride = true;
    });
    try {
      Object.keys(localStorage)
        .filter(key => key.startsWith('fw.gallery.sent.'))
        .forEach(key => localStorage.removeItem(key));
    } catch (_) { /* local demo only */ }
    saveState();
    queueApply();
    setTimeout(applyState, 90);
    return keys.size;
  }

  function openDeviceLoginDialog() {
    const enabled = readDeviceRowChoice() !== 'hidden';
    const { dialog } = makeDialog('电脑设备登录显示', '开启后，微信主页顶部显示电脑设备登录状态', '<label class="fw-switch-row fw-device-switch-row"><span>显示设备登录横条（设备可在横条上切换）</span><input type="checkbox" class="fw-device-toggle"><i class="fw-device-switch" aria-hidden="true"></i></label>', [
      { label: '取消', onClick: closeOverlay },
      { label: '保存', primary: true, onClick: () => {
        const value = dialog.querySelector('.fw-device-toggle').checked;
        const current = readDeviceRowChoice();
        setDeviceRowChoice(value ? (current === 'hidden' ? 'Windows' : current) : 'hidden');
        closeOverlay(); showToast(value ? '已开启电脑设备登录显示' : '已关闭电脑设备登录显示');
      } }
    ]);
    dialog.querySelector('.fw-device-toggle').checked = enabled;
  }

  /* ================= 微信收款助手 + 收款小账本 ================= */
  const PAY_ASSISTANT_ID = 'wechat-pay-assistant';
  const PAY_ASSISTANT_NAME = '微信收款助手';
  const PAY_ASSISTANT_AVATAR = './assets/wechat-pay-assistant.png';
  let payLedgerPage = null;
  // Android WebView dispatches a compatibility mouse click after a touch tap. When a
  // dialog closes on that tap, the extra click re-hit-tests and lands on the element
  // that was underneath the dialog, so page taps are ignored briefly after any close.
  let payUiTapGuard = 0;
  const payTapGuard = fn => event => {
    if (Date.now() < payUiTapGuard) { if (event && event.stopPropagation) event.stopPropagation(); return; }
    return fn(event);
  };

  // A synthetic local contact backs the 微信收款助手 row in the chat list. It is
  // hidden from the native address book so it only appears as a conversation.
  function payAssistantFriend() {
    const list = Array.isArray(state.contacts) ? state.contacts : (state.contacts = []);
    let friend = list.find(item => item && (item.id === PAY_ASSISTANT_ID || item.name === PAY_ASSISTANT_NAME));
    if (!friend) {
      friend = {
        id: PAY_ASSISTANT_ID, name: PAY_ASSISTANT_NAME, wechatId: PAY_ASSISTANT_ID,
        remark: '', avatar: PAY_ASSISTANT_AVATAR, payAssistant: true, starred: false,
        messages: [], createdAt: Date.now()
      };
      list.unshift(friend);
    }
    friend.payAssistant = true;
    friend.avatar = PAY_ASSISTANT_AVATAR;
    if (!Array.isArray(friend.messages)) friend.messages = [];
    if (!friend.messages.length) friend.messages.push({ text: '欢迎使用微信收款服务！', side: 'incoming', createdAt: Date.now() });
    return friend;
  }

  // Tools sheet entry: add the assistant to the conversation list and confirm.
  function addWechatPayAssistant() {
    payAssistantFriend();
    // Re-adding must surface the row even if this chat was previously hidden or
    // deleted from the dialogue list, so clear those per-chat flags here.
    const record = chatRecord(PAY_ASSISTANT_NAME);
    record.deleted = false;
    record.hidden = false;
    state.payAssistantAdded = true;
    saveState();
    queueApply();
    setTimeout(applyState, 90);
    showToast('添加成功');
  }

  function openFriendConversation(friend) {
    if (friend && friend.payAssistant) return openPayAssistant(friend);
    return openLocalFriendConversation(friend);
  }

  const PAY_GEAR_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linejoin="round" stroke-linecap="round"><path d="M19.14 12.94a7.07 7.07 0 0 0 0-1.88l1.9-1.48a.45.45 0 0 0 .11-.58l-1.8-3.12a.45.45 0 0 0-.55-.2l-2.24.9a7.3 7.3 0 0 0-1.63-.94l-.34-2.38a.45.45 0 0 0-.45-.38h-3.6a.45.45 0 0 0-.45.38l-.34 2.38c-.59.24-1.13.56-1.63.94l-2.24-.9a.45.45 0 0 0-.55.2L2.93 9a.45.45 0 0 0 .11.58l1.9 1.48a7.07 7.07 0 0 0 0 1.88l-1.9 1.48a.45.45 0 0 0-.11.58l1.8 3.12c.11.19.34.28.55.2l2.24-.9c.5.38 1.04.7 1.63.94l.34 2.38c.04.22.23.38.45.38h3.6c.22 0 .41-.16.45-.38l.34-2.38c.59-.24 1.13-.56 1.63-.94l2.24.9c.21.08.44-.01.55-.2l1.8-3.12a.45.45 0 0 0-.11-.58l-1.9-1.48Z"/><circle cx="12" cy="12" r="3.2"/></svg>';
  const PAY_KEYPAD_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" stroke-linecap="round"><circle cx="12" cy="12" r="9.2"/><rect x="7.1" y="7.6" width="9.8" height="8.8" rx="1.5"/><path d="M9.1 10.2h1.3M11.4 10.2h1.2M13.6 10.2h1.3M9.1 13.8h5.8"/></svg>';

  function openPayAssistant(friend) {
    friend = friend || payAssistantFriend();
    closePayLedger(true);
    document.querySelector('.fw-local-conversation.fw-pay-assistant')?.remove();
    const page = document.createElement('section');
    page.className = 'fw-local-conversation fw-pay-assistant';
    page.dataset.fwFriendId = friend.id;
    page.innerHTML = [
      '<header class="fw-local-conversation__header fw-pa-header">',
      '<button type="button" class="fw-chat-back" aria-label="返回">‹</button>',
      '<strong>微信收款助手</strong>',
      '<button type="button" class="fw-pa-gear" aria-label="设置">' + PAY_GEAR_SVG + '</button>',
      '</header>',
      '<main class="fw-local-conversation__messages fw-pa-body">',
      '<div class="fw-chat-time">16:29</div>',
      '<div class="fw-chat-message fw-chat-message--incoming fw-pa-message">',
      '<img class="fw-chat-message__avatar fw-pa-avatar" src="' + PAY_ASSISTANT_AVATAR + '" alt="">',
      '<div class="fw-chat-message__bubble fw-pa-bubble">',
      '<p>欢迎使用微信收款服务！</p>',
      '<p>做生意推荐使用“经营收款”，<br><span class="fw-pa-link" role="button" tabindex="0" data-pa-link="business"><i class="fw-pa-chip" aria-hidden="true"></i>【点这里】</span>快速开启。</p>',
      '<p>个人可使用个人收款码， <span class="fw-pa-link" role="button" tabindex="0" data-pa-link="ledger"><i class="fw-pa-chip" aria-hidden="true"></i>【点这里】</span>访问收款小账本。</p>',
      '</div></div></main>',
      '<nav class="fw-pay-assistant__bar">',
      '<button type="button" class="fw-pa-keypad" aria-label="输入">' + PAY_KEYPAD_SVG + '</button>',
      '<div class="fw-pa-tabs">',
      '<button type="button" data-pa-tab="personal">个人收款</button>',
      '<button type="button" data-pa-tab="business">经营收款</button>',
      '<button type="button" data-pa-tab="mall">物料商城</button>',
      '</div></nav>'
    ].join('');
    document.body.appendChild(page);
    document.body.classList.add('fw-local-conversation-open');
    syncChatSystemBars();
    requestAnimationFrame(() => page.classList.add('fw-local-conversation--enter'));
    page.querySelector('.fw-chat-back').addEventListener('click', event => { event.stopPropagation(); closePayAssistant(); });
    page.querySelector('.fw-pa-gear').addEventListener('click', () => showToast('收款助手设置（演示）'));
    page.querySelectorAll('[data-pa-link]').forEach(node => node.addEventListener('click', event => {
      event.stopPropagation();
      if (node.dataset.paLink === 'ledger') openPayLedger();
      else showToast('经营收款功能仅作界面演示');
    }));
    page.querySelectorAll('[data-pa-tab]').forEach(node => node.addEventListener('click', event => {
      event.stopPropagation();
      if (node.dataset.paTab === 'personal') openPayLedger();
      else showToast(node.textContent + '（演示）');
    }));
    applyConversationFontScale();
  }

  function closePayAssistant() {
    if (payLedgerPage && payLedgerPage.isConnected) { closePayLedger(); return; }
    closeLocalConversation();
  }

  function payLedgerState() {
    if (!state.payLedger || typeof state.payLedger !== 'object') state.payLedger = {};
    const ledger = state.payLedger;
    ledger.count = Math.max(0, Math.floor(Number(ledger.count) || 0));
    ledger.amount = Math.max(0, Math.round((Number(ledger.amount) || 0) * 100) / 100);
    return ledger;
  }

  function openPayLedger() {
    closePayLedger(true);
    payUiTapGuard = Date.now() + 280;
    const page = document.createElement('section');
    page.className = 'fw-pay-ledger';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-label', '收款小账本');
    page.innerHTML = [
      '<div class="fw-pay-ledger__scroll">',
      '<div class="fw-pay-ledger__shot-wrap">',
      '<img class="fw-pay-ledger__shot" src="./assets/wechat-pay-ledger-shot.png" alt="收款小账本">',
      '<button type="button" class="fw-pay-ledger__count" data-ledger-count>0</button>',
      '<button type="button" class="fw-pay-ledger__amount" data-ledger-amount><span class="cur">¥</span><span class="num">0.00</span></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-back aria-label="返回" style="left:3%;top:4.5%;width:8%;height:4%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-more aria-label="更多" style="left:74%;top:4.2%;width:9%;height:4%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-close aria-label="关闭" style="left:85.5%;top:4.2%;width:9%;height:4%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-tip aria-label="补充经营信息" style="left:2%;top:10%;width:94%;height:7.5%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-records aria-label="收款记录" style="left:20%;top:33.5%;width:60%;height:6.5%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-row="settings" aria-label="收款设置" style="left:2%;top:40.2%;width:96%;height:7.2%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-row="scan" aria-label="收款码扫码点单" style="left:2%;top:47.5%;width:96%;height:7.3%"></button>',
      '<button type="button" class="fw-pay-ledger__hit" data-ledger-row="mall" aria-label="物料商城" style="left:2%;top:77%;width:96%;height:5.5%"></button>',
      '</div>',
      '</div>'
    ].join('');
    document.body.appendChild(page);
    payLedgerPage = page;
    renderPayLedger();
    // Transparent overlays sit on the bitmap at the reference amount / 笔数 spots.
    page.querySelector('[data-ledger-amount]').addEventListener('click', payTapGuard(() => editLedgerInline('amount')));
    page.querySelector('[data-ledger-count]').addEventListener('click', payTapGuard(() => editLedgerInline('count')));
    page.querySelector('[data-ledger-back]').addEventListener('click', payTapGuard(() => closePayLedger()));
    page.querySelector('[data-ledger-close]').addEventListener('click', payTapGuard(() => closePayLedger()));
    page.querySelector('[data-ledger-more]').addEventListener('click', payTapGuard(() => showToast('收款小账本更多功能（演示）')));
    const demo = (row, label) => row.addEventListener('click', payTapGuard(() => showToast(label + '（演示）')));
    demo(page.querySelector('[data-ledger-tip]'), '补充经营信息');
    demo(page.querySelector('[data-ledger-records]'), '收款记录');
    page.querySelectorAll('[data-ledger-row]').forEach(row => {
      const names = { settings: '收款设置', scan: '收款码扫码点单', mall: '物料商城' };
      demo(row, names[row.dataset.ledgerRow] || '功能');
    });
    return page;
  }

  function closePayLedger(immediate) {
    const page = payLedgerPage;
    payLedgerPage = null;
    if (!page || !page.isConnected) return;
    if (immediate) { page.remove(); return; }
    page.classList.add('fw-pay-ledger--exit');
    const finish = () => { if (page.isConnected) page.remove(); };
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 220);
  }

  function renderPayLedger() {
    if (!payLedgerPage || !payLedgerPage.isConnected) return;
    const ledger = payLedgerState();
    const count = payLedgerPage.querySelector('[data-ledger-count]');
    const amount = payLedgerPage.querySelector('[data-ledger-amount]');
    if (count) { count.textContent = String(ledger.count); count.classList.toggle('is-set', ledger.count > 0); }
    if (amount) {
      amount.querySelector('.num').textContent = ledger.amount.toFixed(2);
      amount.classList.toggle('is-set', ledger.amount > 0);
    }
  }

  // Tapping the number edits it in place: the digit turns into a focused field and
  // the value is committed on blur or Enter. This keeps the layout identical to the
  // reference and avoids the keyboard pushing a dialog out of the touch target.
  function editLedgerInline(kind) {
    if (!payLedgerPage || !payLedgerPage.isConnected) return;
    const isAmount = kind === 'amount';
    const button = payLedgerPage.querySelector(isAmount ? '[data-ledger-amount]' : '[data-ledger-count]');
    if (!button || button.dataset.editing === '1') return;
    const ledger = payLedgerState();
    button.dataset.editing = '1';
    const holder = document.createElement('span');
    holder.className = isAmount ? 'fw-pay-ledger__amount fw-pay-ledger__amount--editing' : 'fw-pay-ledger__count fw-pay-ledger__count--editing';
    if (isAmount) holder.innerHTML = '<span class="cur">¥</span>';
    const input = document.createElement('input');
    input.type = 'text';
    input.inputMode = isAmount ? 'decimal' : 'numeric';
    input.className = 'fw-pay-ledger__input';
    input.autocomplete = 'off';
    input.value = isAmount ? (ledger.amount ? ledger.amount.toFixed(2) : '') : (ledger.count ? String(ledger.count) : '');
    holder.appendChild(input);
    button.replaceWith(holder);
    // Keep the field roughly as wide as its digits so the ¥ keeps the same place
    // as the resting state and the number stays centred while typing.
    const syncWidth = () => {
      const chars = Math.max(2, String(input.value || '').length);
      input.style.width = (chars * 0.62 + (isAmount ? 0.06 : 0.3)).toFixed(2) + 'em';
    };
    input.addEventListener('input', syncWidth);
    syncWidth();
    try { input.focus(); input.select(); } catch (_) { /* keep editing visually */ }
    let settled = false;
    const restore = () => {
      if (holder.isConnected) holder.replaceWith(button);
      delete button.dataset.editing;
    };
    const cancel = () => {
      if (settled) return;
      settled = true;
      restore();
      renderPayLedger();
    };
    const commit = () => {
      if (settled) return;
      settled = true;
      const raw = String(input.value || '').trim();
      if (isAmount) ledger.amount = Math.max(0, Math.round((Number(raw) || 0) * 100) / 100);
      else ledger.count = Math.max(0, Math.floor(Number(raw) || 0));
      saveState();
      restore();
      payUiTapGuard = Date.now() + 350;
      renderPayLedger();
      showToast(isAmount ? (ledger.amount > 0 ? '金额已更新' : '金额已清零') : '笔数已更新');
    };
    input.addEventListener('blur', commit);
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') { event.preventDefault(); commit(); }
      else if (event.key === 'Escape') { event.preventDefault(); cancel(); }
    });
  }

  // The native back button walks chat-pay handlers first; keep the ledger and the
  // assistant conversation ahead of the generic chat-pay screen in that chain.
  const previousHandleChatPayBack = window.__fwHandleChatPayBack;
  window.__fwHandleChatPayBack = function () {
    if (payLedgerPage && payLedgerPage.isConnected) { closePayLedger(); return true; }
    if (document.querySelector('.fw-local-conversation.fw-pay-assistant')) { closePayAssistant(); return true; }
    return typeof previousHandleChatPayBack === 'function' ? previousHandleChatPayBack() : false;
  };

  // The chat info page sits above the conversation, so back must close it before
  // the conversation / pay chains consume the press.
  const previousChatInfoBack = window.__fwHandleChatPayBack;
  window.__fwHandleChatPayBack = function () {
    if (document.querySelector('.fw-friend-profile-page')) { closeFriendProfile(); return true; }
    if (document.querySelector('.fw-chat-info')) { closeChatInfo(); return true; }
    return typeof previousChatInfoBack === 'function' ? previousChatInfoBack() : false;
  };

  function openToolsFeature(label) {
    closeOverlay();
    if (label === '返回APP主页') {
      if (window.DoutuApp && typeof window.DoutuApp.returnToAppHome === 'function') window.DoutuApp.returnToAppHome();
      else showToast('请使用系统返回键回到软件首页');
      return;
    }
    if (label === '调节字体大小') return openFontSizeDialog();
    if (label === '添加微信收款助手') return addWechatPayAssistant();
    if (label === '批量添加聊天记录') return openBatchRecordsDialog();
    if (label === '清空所有聊天记录') return openClearRecordsDialog();
    if (label === '开启电脑设备登录显示') {
      // 工具菜单关闭时 iOS 可能补发一次 click；如果同步打开弹窗，这一下会落到
      // 弹窗的「保存」上，把刚隐藏的设备横条重新打开。延后一拍再开。
      setTimeout(openDeviceLoginDialog, 120);
      return;
    }
    if (label === '不懂操作？联系客服') {
      return makeDialog('联系客服', '这是离线演示页面', '<p>客服入口已准备就绪，当前不会连接网络。</p>', [{ label: '知道了', primary: true, onClick: closeOverlay }]);
    }
    showToast(label + '已添加（本地演示）');
  }

  function openToolsSheet() {
    // 2026-10-10: dropped 添加微信转账助手 / 微信商家收款页面 / 替换首页相册背景图 /
    // 聊天关键词自动回复 from the WeChat home tools popup at the user's request.
    const labels = ['返回APP主页', '调节字体大小', '添加微信收款助手', '批量添加聊天记录', '清空所有聊天记录', '开启电脑设备登录显示'];
    const host = addOverlay('tools', 'fw-overlay--tools');
    const backdrop = document.createElement('div');
    backdrop.className = 'fw-tools-backdrop';
    host.appendChild(backdrop);
    const sheet = document.createElement('section');
    sheet.className = 'fw-tools-sheet';
    sheet.setAttribute('role', 'dialog');
    sheet.setAttribute('aria-label', '微信主页功能');
    backdrop.appendChild(sheet);
    const grid = document.createElement('div'); grid.className = 'fw-tools-grid'; sheet.appendChild(grid);
    labels.forEach(label => {
      const button = document.createElement('button'); button.type = 'button'; button.className = 'fw-tools-item'; button.textContent = label;
      button.addEventListener('click', event => { event.stopPropagation(); openToolsFeature(label); }); grid.appendChild(button);
    });
    const serviceRow = document.createElement('div'); serviceRow.className = 'fw-tools-service-row'; sheet.appendChild(serviceRow);
    const service = document.createElement('button'); service.type = 'button'; service.className = 'fw-tools-service'; service.textContent = '不懂操作？联系客服';
    service.addEventListener('click', event => { event.stopPropagation(); openToolsFeature('不懂操作？联系客服'); }); serviceRow.appendChild(service);
    const serviceFiller = document.createElement('div'); serviceFiller.className = 'fw-tools-service-filler'; serviceRow.appendChild(serviceFiller);
    const cancel = document.createElement('button'); cancel.type = 'button'; cancel.className = 'fw-tools-cancel'; cancel.textContent = '取消';
    cancel.addEventListener('click', event => { event.stopPropagation(); closeOverlay(); }); sheet.appendChild(cancel);
    backdrop.addEventListener('click', event => { if (event.target === backdrop) closeOverlay(); });
  }

  /**
   * 娱乐页「撩妹加V」开着没有。开着 = 这台机器的摄像头是坏的，
   * 微模块里的扫一扫就只弹提示、不进付款页。开关存在原生那边（PlayStore）。
   */
  function cameraBroken() {
    try {
      return !!(window.DoutuApp && window.DoutuApp.isScanDisabled && window.DoutuApp.isScanDisabled());
    } catch (_) {
      return false;
    }
  }

  /** 「扫一扫」在摄像头坏了的时候弹的那一句。 */
  function openCameraBrokenDialog() {
    makeDialog('摄像头不可用', '检测到摄像头硬件损坏，扫一扫功能暂时无法使用。', '', [
      { label: '我知道了', primary: true, onClick: () => closeOverlay() },
    ]);
  }

  function openFeature(label) {
    closeOverlay();
    if (label === '发起群聊') return openGroupDialog();
    if (label === '添加朋友') return openAddFriendDialog();
    // 2026-10-11: 扫一扫 now lands straight on the 付款给个人 page.
    // 2026-10-17: 娱乐页把「撩妹加V」打开以后，扫一扫要装作摄像头坏了 ——
    //             只弹提示，后面那个付款页一步也不走。
    if (label === '扫一扫') {
      if (cameraBroken()) return openCameraBrokenDialog();
      return mountScanPayScreen();
    }
    // 2026-10-11: 收付款 opens the green 付款码 page.
    if (label === '收付款') return mountReceivePayScreen();
    showToast(label + '（本地演示）');
  }

  function getInitial(name) {
    const first=String(name||'').trim().charAt(0).toUpperCase();
    const known={'娱':'Y','示':'S','陈':'C','斌':'C','微':'W','喵':'M','神':'S','老':'L','星':'X','大':'D','小':'X','林':'L'};
    if(known[first])return known[first]; return /^[A-Z]$/.test(first)?first:'#';
  }
  function collectContacts() {
    const map=new Map();
    const add=item=>{
      if(!item||!item.name)return;
      const name=String(item.name).trim();
      if(!name||name.length>40)return;
      if(name === '老婆' || String(item.remark || '').trim() === '老婆') return;
      if(name === PAY_ASSISTANT_NAME || item.payAssistant) return;
      if(/^(搜索|全选|导入|水管工|群聊|新的朋友|仅聊天的朋友|标签|公众号|服务号)$/.test(name))return;
      const old=map.get(name);
      map.set(name,{...old,...item,name,avatar:item.avatar||old?.avatar||'./assets/user-avatar-1-CKPDcH57.jpg',starred:!!(item.starred||old?.starred)});
    };
    // Source 1 - the app's own address book. The bundled build persists every
    // contact into the localStorage `allProfiles` atom, which is the exact list
    // the 通讯录 screen, its pinyin grouping and right-side index render from.
    try {
      const profiles = JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY) || '[]');
      if (Array.isArray(profiles)) profiles.forEach(profile => {
        if (!profile) return;
        add({ name: String(profile.remark || profile.nickname || '').trim(), avatar: profile.avatarInfo || '', starred: !!profile.isStarred });
      });
    } catch (_) { /* fall through to the local sources below */ }
    // Source 2 - friends created inside this override (添加朋友 / 批量添加).
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => add({ name: String(friend.remark || friend.name || '').trim(), avatar: friend.avatar, starred: !!friend.starred }));
    // Source 3 - any dialogue that is currently in the chat list.
    getRows().forEach(row=>{if(row.dataset.fwLocalGroup==='1'||row.classList.contains('fw-local-group-row'))return;const info=getChatInfo(row);if(info){const rec=chatRecord(info.key);add({...info,starred:!!rec.starred});}});
    // Only when nothing else exists do we fall back to the bundled demo contacts.
    if(!map.size) CONTACT_FALLBACKS.forEach(add);
    return [...map.values()].sort((a,b)=>getInitial(a.name).localeCompare(getInitial(b.name))||a.name.localeCompare(b.name,'zh-CN'));
  }
  function markChatRead(row) {
    const info=getChatInfo(row);
    if(!info)return;
    const record=chatRecord(info.key);
    record.unread=false;
    record.unreadOverride = true;
    record.unreadCount=0;
    hideNativeUnreadNodes(row);
    // Avatar badges live on document.body so they can track a scrolling row;
    // remove the matching badge synchronously before the route changes.
    removeUnreadBadge(info.key);
    // Defer serialization and whole-list badge recalculation until after route paint.
    requestAnimationFrame(() => setTimeout(() => { saveState(); applyState(); }, 0));
  }

  function openGroupDialog() {
    const contacts=collectContacts();
    const host=addOverlay('group-select','fw-overlay--group-select');
    const page=document.createElement('section');
    page.className='fw-group-select-page';
    page.setAttribute('role','dialog');
    page.setAttribute('aria-label','发起群聊');
    page.innerHTML='<header class="fw-group-select-header"><button type="button" class="fw-group-select-back" aria-label="返回">‹</button><h1>发起群聊</h1><button type="button" class="fw-group-select-done" disabled>完成</button></header>'
      +'<div class="fw-group-select-search-wrap"><span class="fw-group-select-search-icon">⌕</span><input class="fw-group-select-search" type="search" placeholder="搜索" autocomplete="off"></div>'
      +'<div class="fw-group-select-scroll">'
      +'<button type="button" class="fw-group-select-existing" data-role="existing"><span>选择一个已有的群</span><i>›</i></button>'
      +'<h2 class="fw-group-select-caption">创建新群聊</h2>'
      +'<div class="fw-group-select-create"><button type="button" class="fw-group-select-create-row"><span>面对面建群</span></button><button type="button" class="fw-group-select-create-row"><span>选择群聊中的朋友</span></button><button type="button" class="fw-group-select-create-row"><span>选择企业微信联系人</span></button></div>'
      +'<div class="fw-group-select-list"></div>'
      +'</div>'
      +'<nav class="fw-group-select-index" aria-label="字母索引"><button data-index="top">↑</button><button data-index="star">☆</button>'+('ABCDEFGHIJKLMNOPQRSTUVWXYZ#'.split('').map(ch=>'<button data-index="'+ch+'">'+ch+'</button>').join(''))+'</nav>';
    host.appendChild(page);
    const list=page.querySelector('.fw-group-select-list');
    const scroll=page.querySelector('.fw-group-select-scroll');
    const done=page.querySelector('.fw-group-select-done');
    const search=page.querySelector('.fw-group-select-search');
    const existingRow=page.querySelector('[data-role="existing"]');
    const caption=page.querySelector('.fw-group-select-caption');
    const createBlock=page.querySelector('.fw-group-select-create');
    const selected=new Set();
    const byName=new Map(contacts.map(c=>[c.name,c]));
    const firstStarred=contacts.find(c=>c.starred)||null;
    const queryValue=()=>search.value.trim().toLowerCase();
    // A toast that stays above the picker instead of dismissing it, unlike the
    // global showToast() which closes whatever overlay is currently open.
    const groupToast=text=>{const toast=document.createElement('div');toast.className='fw-toast';toast.textContent=text;host.appendChild(toast);setTimeout(()=>{if(toast.isConnected)toast.remove();},1500);};
    const syncDone=()=>{const enabled=selected.size>=2;done.disabled=!enabled;done.classList.toggle('is-enabled',enabled);done.textContent=selected.size?('完成('+selected.size+')'):'完成';};
    const openExistingGroupsPanel=()=>{
      if(page.querySelector('.fw-group-select-groups'))return;
      const groups=(Array.isArray(state.groups)?state.groups:[]).map(group=>group&&group.name).filter(Boolean);
      if(!groups.length){groupToast('暂无群聊');return;}
      const sheet=document.createElement('div');
      sheet.className='fw-group-select-groups';
      sheet.innerHTML='<div class="fw-group-select-groups__backdrop"></div><section class="fw-group-select-groups__panel"><header><button type="button" data-groups-close>取消</button><strong>选择一个已有的群</strong><span></span></header><div class="fw-group-select-groups__list"></div></section>';
      const panelList=sheet.querySelector('.fw-group-select-groups__list');
      groups.forEach(name=>{const item=document.createElement('button');item.type='button';item.className='fw-group-select-groups__row';item.textContent=name;item.addEventListener('click',()=>{sheet.remove();groupToast('已选择群聊：'+name);});panelList.appendChild(item);});
      const close=()=>sheet.remove();
      sheet.querySelector('[data-groups-close]').addEventListener('click',close);
      sheet.querySelector('.fw-group-select-groups__backdrop').addEventListener('click',close);
      page.appendChild(sheet);
    };
    const render=()=>{
      const q=queryValue(), searching=!!q;
      existingRow.hidden=searching; caption.hidden=searching; createBlock.hidden=searching;
      list.innerHTML='';
      const filtered=contacts.filter(c=>!q||c.name.toLowerCase().includes(q));
      const groups=new Map();
      filtered.forEach(c=>{const key=getInitial(c.name);if(!groups.has(key))groups.set(key,[]);groups.get(key).push(c);});
      for(const entry of groups){const key=entry[0],items=entry[1],section=document.createElement('section');section.className='fw-group-select-section';section.dataset.index=key;const h=document.createElement('h2');h.textContent=key;section.appendChild(h);for(const c of items){const row=document.createElement('button');row.type='button';row.className='fw-group-select-row';row.dataset.name=c.name;const check=document.createElement('span');check.className='fw-group-select-check';const img=document.createElement('img');img.alt='';img.src=c.avatar||CONTACT_FALLBACKS[0].avatar;img.addEventListener('error',()=>{img.src=CONTACT_FALLBACKS[0].avatar;},{once:true});if(typeof window.__fwResolveProfileAvatar==='function'&&c.avatar){window.__fwResolveProfileAvatar(c.avatar).then(src=>{if(src&&img.isConnected)img.src=src;}).catch(()=>{});}const name=document.createElement('span');name.className='fw-group-select-name';name.textContent=c.name;row.append(check,img,name);row.classList.toggle('is-selected',selected.has(c.name));row.addEventListener('click',()=>{if(selected.has(c.name))selected.delete(c.name);else selected.add(c.name);row.classList.toggle('is-selected',selected.has(c.name));syncDone();});section.appendChild(row);}list.appendChild(section);}
      if(!filtered.length){const empty=document.createElement('div');empty.className='fw-group-select-empty';empty.textContent='没有找到联系人';list.appendChild(empty);}
    };
    const scrollToRow=row=>{if(row)scroll.scrollTo({top:row.getBoundingClientRect().top-scroll.getBoundingClientRect().top+scroll.scrollTop,behavior:'smooth'});};
    syncDone();
    render();
    search.addEventListener('input',render);
    page.querySelector('.fw-group-select-back').addEventListener('click',closeOverlay);
    existingRow.addEventListener('click',openExistingGroupsPanel);
    page.querySelectorAll('.fw-group-select-create-row').forEach(btn=>btn.addEventListener('click',()=>groupToast(btn.querySelector('span').textContent+'（本地演示）')));
    page.querySelectorAll('.fw-group-select-index button').forEach(btn=>btn.addEventListener('click',()=>{const key=btn.dataset.index;if(key==='top'){scroll.scrollTo({top:0,behavior:'smooth'});return;}if(key==='star'){if(firstStarred)scrollToRow(list.querySelector('.fw-group-select-row[data-name="'+CSS.escape(firstStarred.name)+'"]'));return;}scrollToRow(scroll.querySelector('[data-index="'+key+'"]'));}));
    done.addEventListener('click',()=>{if(selected.size<2){groupToast('请至少选择两位好友');return;}const members=[...selected].map(name=>byName.get(name)).filter(Boolean);state.groups=Array.isArray(state.groups)?state.groups:[];const group={id:'group-'+Date.now(),name:members.map(m=>m.name).join('、'),members:members.map(m=>m.name),memberAvatars:members.map(m=>m.avatar||''),createdAt:Date.now()};state.groups.unshift(group);saveState();closeOverlay();queueApply();showToast('群聊已创建：'+group.name);});
  }

  // ---- 新的朋友 (incoming friend requests) -----------------------------------
  // The bundled build has no friend-request concept, so this local page fabricates a
  // small inbox the same way the other fake pages do, and persists whatever the user
  // accepts. Requests are seeded once with bundled avatars so the page is instant and
  // works offline.
  const REQUEST_MESSAGES = [
    name => '我是' + name,
    name => '我是' + name + '，加个好友',
    name => '我是' + name + '，通过一下',
    name => '你好，我是' + name
  ];
  const REQUEST_SOURCES = ['通过手机号搜索', '通过微信号搜索', '来自手机通讯录', '来自群聊'];

  function ensureFriendRequests() {
    if (Array.isArray(state.friendRequests) && state.friendRequests.length) return state.friendRequests;
    if (state.friendRequestsSeeded) { state.friendRequests = []; return state.friendRequests; }
    const pool = BATCH_HANDLE_SEEDS;
    const count = 4 + Math.floor(Math.random() * 3);
    const used = new Set();
    const base = Date.now();
    const requests = [];
    for (let index = 0; index < count; index += 1) {
      let name = pool[Math.floor(Math.random() * pool.length)];
      for (let guard = 0; used.has(name) && guard < 40; guard += 1) name = pool[Math.floor(Math.random() * pool.length)];
      used.add(name);
      requests.push({
        id: 'request-' + base + '-' + index,
        name,
        avatar: avatarUrl(1 + Math.floor(Math.random() * 200)),
        message: REQUEST_MESSAGES[index % REQUEST_MESSAGES.length](name),
        source: REQUEST_SOURCES[index % REQUEST_SOURCES.length],
        status: 'pending',
        createdAt: base - index * 1800000
      });
    }
    state.friendRequests = requests;
    state.friendRequestsSeeded = true;
    saveState();
    return requests;
  }

  function acceptFriendRequest(request, refresh, notify) {
    state.contacts = Array.isArray(state.contacts) ? state.contacts : [];
    const already = state.contacts.some(friend => String(friend.name || '').trim() === request.name);
    if (!already) {
      const friend = {
        id: 'friend-' + Date.now(),
        name: request.name,
        wechatId: 'wx' + Math.random().toString(36).slice(2, 12),
        gender: ['女', '男', '未知'][Math.floor(Math.random() * 3)],
        remark: '', realName: '', phone: '',
        region: ['广东 深圳', '北京 北京', '上海 上海', '浙江 杭州', '四川 成都'][Math.floor(Math.random() * 5)],
        verification: '允许任何人添加',
        source: request.source || '',
        requestMessage: request.message || '',
        avatar: request.avatar,
        starred: false,
        createdAt: Date.now()
      };
      state.contacts.unshift(friend);
      window.dispatchEvent(new CustomEvent('fw:contacts-updated', { detail: { contact: friend } }));
    }
    request.status = 'added';
    saveState();
    applyState();
    setTimeout(applyState, 80);
    if (typeof refresh === 'function') refresh();
    // showToast() closes whatever overlay is open, so the page owns its own toast.
    if (typeof notify === 'function') notify('已添加 ' + request.name);
  }

  function openNewFriendsPage() {
    ensureFriendRequests();
    const host = addOverlay('new-friends', 'fw-overlay--new-friends');
    const page = document.createElement('section');
    page.className = 'fw-new-friends-page';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-label', '新的朋友');
    page.innerHTML = '<header class="fw-new-friends-header"><button type="button" class="fw-new-friends-back" aria-label="返回">‹</button><h1>新的朋友</h1><span class="fw-new-friends-header-spacer"></span></header>'
      + '<div class="fw-new-friends-scroll"><div class="fw-new-friends-list"></div></div>';
    host.appendChild(page);
    page.querySelector('.fw-new-friends-back').addEventListener('click', closeOverlay);
    const pageToast = text => {
      const toast = document.createElement('div');
      toast.className = 'fw-toast';
      toast.textContent = text;
      host.appendChild(toast);
      setTimeout(() => { if (toast.isConnected) toast.remove(); }, 1500);
    };
    const list = page.querySelector('.fw-new-friends-list');
    const draw = () => {
      list.replaceChildren();
      const requests = Array.isArray(state.friendRequests) ? state.friendRequests : [];
      if (!requests.length) {
        const empty = document.createElement('p');
        empty.className = 'fw-new-friends-empty';
        empty.textContent = '暂无新的朋友';
        list.appendChild(empty);
        return;
      }
      requests.forEach(request => {
        const row = document.createElement('div');
        row.className = 'fw-new-friends-row';
        const avatar = document.createElement('img');
        avatar.className = 'fw-new-friends-avatar';
        avatar.alt = '';
        avatar.src = request.avatar || avatarUrl(1);
        const copy = document.createElement('div');
        copy.className = 'fw-new-friends-copy';
        const name = document.createElement('b');
        name.textContent = request.name || '';
        const message = document.createElement('small');
        message.textContent = request.message || request.source || '';
        copy.append(name, message);
        const action = document.createElement('button');
        action.type = 'button';
        action.className = 'fw-new-friends-accept';
        const added = request.status === 'added';
        action.textContent = added ? '已添加' : '接受';
        action.classList.toggle('is-added', added);
        if (!added) action.addEventListener('click', event => { event.stopPropagation(); acceptFriendRequest(request, draw, pageToast); });
        row.append(avatar, copy, action);
        list.appendChild(row);
      });
    };
    draw();
  }

  /**
   * Native entry point for the locally rendered WeChat screens that are not real routes
   * (the app shell tiles 发起群聊 / 新的朋友 open these).
   */
  window.__fwOpenFeature = function (name) {
    const key = String(name == null ? '' : name).trim().toLowerCase();
    if (key === 'group-chat') { openGroupDialog(); return true; }
    if (key === 'new-friends') { openNewFriendsPage(); return true; }
    if (key === 'add-friend') { openAddFriendDialog(); return true; }
    // 主页「微 → 模拟来电」：语音来电（默认）/ 视频来电。
    if (key === 'call') { mountSimulatedCall('voice'); return true; }
    if (key === 'video-call') { mountSimulatedCall('video'); return true; }
    // 主页「微 → 朋友圈集赞」：进朋友圈选一条动态批量生成点赞。
    if (key === 'moments-likes') { startMomentsLikeBoost(); return true; }
    // 主页「微 → 支付成功」：独立的支付成功页（复用扫一扫付款成功那层）。
    if (key === 'pay-success') { mountPaySuccessPage(); return true; }
    // 主页「微 → 切换账号」：整页账号列表（原生首页宫格「切换账号」）。
    if (key === 'switch-account') { openAccountSwitchPage(); return true; }
    // 主页「微 → 年度账单」：账单页里的「收支统计 → 年账单」。
    if (key === 'annual-bill') {
      return typeof window.__fwOpenBillStats === 'function' ? window.__fwOpenBillStats('year') : false;
    }
    return false;
  };

  // 原生 WebHost 的顶栏命中层直接调这两个入口；它们和 openPlusMenu /
  // openToolsSheet 在同一个作用域里，不能写文件末尾（那里已经是另一个 IIFE）。
  // 原生「+」菜单如果走到加号 action sheet，直接复用网页的 openFeature，
  // 扫一扫/收付款也留在网页里，避免跳原生页后底部/收款人/头像交互丢失。
  window.__fwPlusAction = function (label) {
    try { openFeature(String(label || "")); return true; } catch (_) { return false; }
  };

  window.__fwOpenPlusMenu = function () {
    const actions = document.querySelector('.wechat-topbar__actions');
    if (!actions) return false;
    const icons = [...actions.querySelectorAll('svg')];
    const anchor = icons[icons.length - 1];
    if (!anchor) return false;
    openPlusMenu(anchor);
    return true;
  };
  window.__fwOpenToolsSheet = function () {
    openToolsSheet();
    return true;
  };

  /**
   * 2026-10-16：「模拟来电」设置页指定的来电人 / 头像 / 铃声从这里进来。
   *
   * 以前只有 __fwOpenFeature('call')：来电人是 callContact() 随机挑的、铃声固定是内置那首。
   * 现在整条来电界面（铃声循环、接听淡出、麦克风、扬声器、通话结果落进聊天）原样复用，
   * 只把 call.contact 和铃声换成用户挑的那一份：
   *   name      来电人的名字（通讯录里的备注 / 用户自己改的名字）
   *   avatar    头像地址；设置页从相册挑的那张会以 https://fake-world.local/__call_assets/... 过来
   *   ringtone  铃声地址；空字符串表示回到内置铃声
   */
  window.__fwOpenCall = function (payload) {
    try {
      const opts = typeof payload === 'string' ? JSON.parse(payload || '{}') : (payload || {});
      const name = String(opts.name || '').trim();
      const avatar = String(opts.avatar || '').trim();
      const ringtone = String(opts.ringtone || '').trim();
      // 给了就用它，没给就清掉，让 startCallRing 回到内置那首
      callStore(CALL_KEYS.ringtone, ringtone);
      mountSimulatedCall('voice', {
        direction: 'incoming',
        contact: name ? { name: name, avatar: avatar } : undefined,
      });
      return true;
    } catch (_) {
      return false;
    }
  };

  function openAddFriendDialog() {
    const host = addOverlay('add-friend', 'fw-overlay--add-friend');
    const page = document.createElement('section');
    page.className = 'fw-add-friend-page';
    page.innerHTML = `<header class="fw-add-friend-header"><button type="button" class="fw-add-friend-back">‹</button><h1>添加朋友</h1><button type="button" class="fw-add-friend-batch">批量添加</button></header><div class="fw-add-friend-scroll"><p class="fw-add-friend-tip" role="button" tabindex="0">点头部可设置固定来源和验证文字</p><div class="fw-add-friend-settings" hidden><label>固定来源<input name="source" maxlength="40" placeholder="例如：通过手机号搜索"></label><label>验证文字<input name="requestMessage" maxlength="60" placeholder="例如：我是小明"></label><button type="button" class="fw-add-friend-settings-save">保存设置</button></div><div class="fw-add-friend-fields"><label class="fw-add-friend-field fw-avatar-field"><span><b>*</b> 头像</span><button class="fw-friend-avatar" type="button"><img alt="头像"></button><input class="fw-friend-avatar-file" type="file" accept="image/*" hidden></label><label class="fw-add-friend-field"><span><b>*</b> 昵称</span><input name="name" maxlength="40" placeholder="请输入昵称"></label><label class="fw-add-friend-field"><span><b>*</b> 性别</span><select name="gender"><option>女</option><option>男</option><option>未知</option></select></label><label class="fw-add-friend-field"><span><b>*</b> 微信号</span><input name="wechatId" maxlength="40" placeholder="请输入微信号"></label><label class="fw-add-friend-field"><span>备注</span><input name="remark" maxlength="40" placeholder="选填"></label><label class="fw-add-friend-field"><span>真名</span><input name="realName" maxlength="40" placeholder="选填"></label><label class="fw-add-friend-field"><span>手机号</span><input name="phone" type="tel" maxlength="20" placeholder="选填"></label><label class="fw-add-friend-field"><span>地区</span><select name="region"><option>广东 深圳</option><option>广西 防城港</option><option>北京 北京</option><option>上海 上海</option><option>浙江 杭州</option><option>江苏 南京</option><option>四川 成都</option><option>海外</option></select></label><label class="fw-add-friend-field"><span>验证方式</span><select name="verification"><option>你加TA(需验证)</option><option>允许任何人添加</option><option>通过手机号搜索</option><option>通过微信号搜索</option></select></label></div><button type="button" class="fw-add-friend-confirm">确认</button><button type="button" class="fw-add-friend-random">随机填充信息</button></div>`;
    host.appendChild(page);
    const $ = selector => page.querySelector(selector);
    const img = $('.fw-friend-avatar img');
    const fallbacks = CONTACT_FALLBACKS.map(item => item.avatar);
    let avatar = '';
    img.src = remoteAvatarUrl();
    img.onerror = () => { img.onerror = null; img.src = fallbacks[Math.floor(Math.random() * fallbacks.length)]; };
    $('.fw-add-friend-back').onclick = closeOverlay;    const settingsPanel = $('.fw-add-friend-settings');
    const savedDefaults = state.addFriendDefaults || {};
    $('[name="source"]').value = savedDefaults.source || '';
    $('[name="requestMessage"]').value = savedDefaults.requestMessage || '';
    const toggleSettings = () => { settingsPanel.hidden = !settingsPanel.hidden; };
    $('.fw-add-friend-tip').onclick = toggleSettings;
    $('.fw-add-friend-tip').onkeydown = event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggleSettings(); } };
    $('.fw-add-friend-settings-save').onclick = () => {
      state.addFriendDefaults = { source: $('[name="source"]').value.trim(), requestMessage: $('[name="requestMessage"]').value.trim() };
      saveState(); settingsPanel.hidden = true; showToast('添加设置已保存');
    };
    $('.fw-friend-avatar').onclick = () => {
      const picker=document.createElement('div');picker.className='fw-avatar-picker';
      const panel=document.createElement('section');panel.className='fw-avatar-picker-panel';panel.innerHTML='<h2>选择头像</h2><div class="fw-avatar-picker-grid"></div><div class="fw-avatar-picker-actions"><button type="button" class="fw-avatar-upload">从相册上传</button><button type="button" class="fw-avatar-cancel">取消</button></div>';
      picker.appendChild(panel);host.appendChild(picker);const grid=panel.querySelector('.fw-avatar-picker-grid');
      const options=Array.from({length:24},()=>remoteAvatarUrl());
      options.forEach(src=>{const choice=document.createElement('button');choice.type='button';choice.className='fw-avatar-choice';const image=document.createElement('img');image.src=src;image.onerror=()=>{image.onerror=null;image.src=fallbacks[0];};choice.appendChild(image);choice.onclick=()=>{avatar=src;img.src=src;picker.remove();};grid.appendChild(choice);});
      panel.querySelector('.fw-avatar-upload').onclick=()=>{$('.fw-friend-avatar-file').click();picker.remove();};panel.querySelector('.fw-avatar-cancel').onclick=()=>picker.remove();picker.onclick=e=>{if(e.target===picker)picker.remove();};
    };
    $('.fw-friend-avatar-file').onchange = event => {
      const file = event.target.files?.[0]; if (!file || !file.type.startsWith('image/')) return;
      const reader = new FileReader();
      reader.onload = () => { const source = new Image(); source.onload = () => { const c=document.createElement('canvas'),scale=Math.min(1,256/Math.max(source.width,source.height)); c.width=Math.max(1,source.width*scale);c.height=Math.max(1,source.height*scale);c.getContext('2d').drawImage(source,0,0,c.width,c.height);avatar=c.toDataURL('image/jpeg',.78);img.src=avatar; }; source.src=reader.result; };
      reader.readAsDataURL(file);
    };
    $('.fw-add-friend-random').onclick = async event => {
      // The nickname comes from the 随机网名 API; the bundled pool is only the offline fallback.
      const button = event && event.currentTarget ? event.currentTarget : $('.fw-add-friend-random');
      const original = button ? button.textContent : '';
      if (button) { button.disabled = true; button.textContent = '获取中…'; }
      const fetched = await fetchRandomNicknames(1);
      const name = fetched[0] || '';
      if (button) { button.disabled = false; button.textContent = original; }
      if (!name) { showToast('网名接口暂时不可用，请稍后再试'); return; }
      $('[name="name"]').value=name; $('[name="wechatId"]').value='wx'+Math.random().toString(36).slice(2,10); $('[name="gender"]').selectedIndex=Math.floor(Math.random()*3); $('[name="remark"]').value=''; $('[name="realName"]').value=''; $('[name="phone"]').value='1'+String(Math.floor(3000000000+Math.random()*6999999999)); $('[name="region"]').selectedIndex=Math.floor(Math.random()*$('[name="region"]').options.length); $('[name="verification"]').selectedIndex=Math.floor(Math.random()*$('[name="verification"]').options.length); avatar=''; img.src=remoteAvatarUrl(); img.onerror=()=>{img.onerror=null;img.src=fallbacks[Math.floor(Math.random()*fallbacks.length)];};
    };
    $('.fw-add-friend-confirm').onclick = () => {
      const name=$('[name="name"]').value.trim(), wechatId=$('[name="wechatId"]').value.trim();
      if(!name||!wechatId){showToast('请填写昵称和微信号');(!name?$('[name="name"]'):$('[name="wechatId"]')).focus();return;}
      state.contacts=Array.isArray(state.contacts)?state.contacts:[];
      const normalizedId=wechatId.toLocaleLowerCase();
      if(state.contacts.some(friend=>String(friend.wechatId||'').toLocaleLowerCase()===normalizedId||String(friend.name||'').trim().toLocaleLowerCase()===name.toLocaleLowerCase())||getRows().some(row=>getChatInfo(row)?.name.toLocaleLowerCase()===name.toLocaleLowerCase())){showToast('该好友已存在');return;}
      const friend={id:'friend-'+Date.now(),name,wechatId,gender:$('[name="gender"]').value,remark:$('[name="remark"]').value.trim(),realName:$('[name="realName"]').value.trim(),phone:$('[name="phone"]').value.trim(),region:$('[name="region"]').value,verification:$('[name="verification"]').value,source:$('[name="source"]').value.trim()||(state.addFriendDefaults||{}).source||'',requestMessage:$('[name="requestMessage"]').value.trim()||(state.addFriendDefaults||{}).requestMessage||'',avatar:avatar||img.src,starred:false,createdAt:Date.now()};
      friend.avatar = freezeRemoteAvatar(friend.avatar);
      state.contacts.unshift(friend);
      if(!saveState()){state.contacts.shift();showToast('保存失败，请检查设备存储空间');return;}
      closeOverlay();applyState();setTimeout(applyState,80);showToast('已添加好友：'+name);window.dispatchEvent(new CustomEvent('fw:contacts-updated',{detail:{contact:friend}}));
    };
    $('.fw-add-friend-batch').onclick = openBatchAddFriends;
  }

  function openBatchAddFriends() {
    const host=addOverlay('batch-friend','fw-overlay--batch-friend');
    const box=document.createElement('section');box.className='fw-batch-friend-dialog';box.innerHTML='<h2>批量添加好友</h2><p>输入随机生成的数量（1-200），好友资料及头像均随机生成。</p><input class="fw-batch-friend-count" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="off" placeholder="请输入数量（最多200）"><div><button type="button" class="cancel">取消</button><button type="button" class="confirm">生成并添加</button></div>';host.appendChild(box);
    box.querySelector('.cancel').onclick=openAddFriendDialog;
    box.querySelector('.confirm').onclick=async()=>{
      const input=box.querySelector('.fw-batch-friend-count'),count=Number(input.value);
      if(!Number.isInteger(count)||count<1||count>200){showToast('请输入1到200之间的整数');input.focus();return;}
      const confirmButton=box.querySelector('.confirm');
      const originalLabel=confirmButton.textContent;
      confirmButton.disabled=true;
      // Nicknames come only from the 随机网名 API (one per request); 4 requests run in parallel.
      const wanted=Math.min(count,200);
      confirmButton.textContent='获取网名 0/'+wanted;
      const apiNames=await fetchRandomNicknames(wanted,done=>{confirmButton.textContent='获取网名 '+done+'/'+wanted;});
      confirmButton.disabled=false;confirmButton.textContent=originalLabel;
      if(!apiNames.length){showToast('网名接口暂时不可用，请稍后再试');return;}
      state.contacts=Array.isArray(state.contacts)?state.contacts:[];
      const names=new Set([...state.contacts.map(friend=>String(friend.name||'').toLocaleLowerCase()),...getRows().map(row=>getChatInfo(row)?.name.toLocaleLowerCase()).filter(Boolean)]);const ids=new Set(state.contacts.map(friend=>String(friend.wechatId||'').toLocaleLowerCase()));
      const apiPool=[...apiNames];let added=0;const batchId=Date.now(),generated=[];
      const takeName=()=>apiPool.length?apiPool.shift():'';
      let attempts=0;while(added<count&&attempts<10000){attempts++;const name=takeName();if(!name)break;const lower=name.toLocaleLowerCase(),wechatId='wx'+Math.random().toString(36).slice(2,12);if(names.has(lower)||ids.has(wechatId.toLocaleLowerCase()))continue;names.add(lower);ids.add(wechatId.toLocaleLowerCase());const id='friend-'+batchId+'-'+added,avatar=apiAvatarFor(name + '|' + id);generated.push({id,name,wechatId,gender:['女','男','未知'][Math.floor(Math.random()*3)],remark:'',realName:'',phone:'',region:['广东 深圳','北京 北京','上海 上海','浙江 杭州','四川 成都'][Math.floor(Math.random()*5)],verification:'你加TA(需验证)',source:'随机生成',requestMessage:'',avatar,starred:false,createdAt:batchId+added});added++;}
      if(!added){showToast('网名接口暂时不可用，请稍后再试');return;}generated.forEach(friend=>{friend.avatar=freezeRemoteAvatar(friend.avatar);});state.contacts.unshift(...generated);if(!saveState()){state.contacts.splice(0,generated.length);showToast('保存失败，请检查设备存储空间');return;}closeOverlay();applyState();setTimeout(applyState,80);showToast(added===count?('已随机添加'+added+'位好友'):('网名接口返回不足，已添加 '+added+' 位好友'));window.dispatchEvent(new CustomEvent('fw:contacts-updated',{detail:{count:added}}));
    };
  }

  /* ================= 扫一扫 → 付款给个人 (2026-10-11) =================
     Reuses the chat transfer screen's markup/CSS so the layout, sizes and keypad
     proportions match the reference shot exactly; only the copy and the two extra
     affordances (tappable avatar + tappable payee line) differ. */
  const SCAN_PAY_KEY = 'scanPay';
  function scanPayProfile() {
    const fallback = { name: 'ly', realName: '王玮', avatar: '' };
    const stored = state[SCAN_PAY_KEY] && typeof state[SCAN_PAY_KEY] === 'object' ? state[SCAN_PAY_KEY] : null;
    const profile = stored || (state[SCAN_PAY_KEY] = { name: fallback.name, realName: fallback.realName, avatar: '' });
    if (!profile.name) profile.name = fallback.name;
    if (typeof profile.realName !== 'string') profile.realName = fallback.realName;
    if (!profile.avatar) profile.avatar = apiAvatarFor('scanpay|' + profile.name);
    return profile;
  }
  // WeChat only ever shows the last character of the real name, so the mask has one
  // asterisk per hidden character ("王玮" -> *玮, "欧阳志伟" -> ***伟).
  function scanPayMaskedName(realName) {
    const text = String(realName || '').trim();
    if (!text) return '';
    return '*'.repeat(Math.max(0, text.length - 1)) + text.slice(-1);
  }
  function scanPaySave() {
    try { saveState(); } catch (_) { /* local demo only */ }
  }

  /**
   * 通用「选择头像」面板：接口里挑（可换一批）或本地上传，选中后把值交给 onPick。
   * 扫一扫付款页和支付成功页共用这一套。
   */
  function openAvatarPickerSheet(host, onPick) {
    host.querySelector('.fw-scanpay-sheet')?.remove();
    const sheet = document.createElement('div');
    sheet.className = 'fw-chat-pay-input-overlay fw-scanpay-sheet';
    sheet.innerHTML = '<section class="fw-scanpay-sheet__panel"><header><button type="button" data-cancel>取消</button><strong>选择头像</strong><button type="button" data-refresh>换一批</button></header>'
      + '<div class="fw-scanpay-sheet__grid" data-grid><p class="fw-scanpay-sheet__hint">正在获取头像…</p></div>'
      + '<label class="fw-scanpay-sheet__upload">本地上传<input type="file" accept="image/*" hidden></label></section>';
    host.appendChild(sheet);
    const grid = sheet.querySelector('[data-grid]');
    const close = () => sheet.remove();
    const applyAvatar = value => {
      let next = String(value || '').trim();
      if (!next) return;
      if (!next.startsWith('data:')) next = freezeRemoteAvatar(next) || next;
      close();
      if (typeof onPick === 'function') onPick(next);
    };
    const load = () => {
      grid.innerHTML = '<p class="fw-scanpay-sheet__hint">正在获取头像…</p>';
      Promise.all(Array.from({ length: 9 }, () => fetchAvatarApiUrl().catch(() => ''))).then(urls => {
        const list = urls.filter(Boolean);
        if (!sheet.isConnected) return;
        if (!list.length) { grid.innerHTML = '<p class="fw-scanpay-sheet__hint">头像接口暂时不可用，可点下方本地上传</p>'; return; }
        grid.replaceChildren();
        list.forEach(url => {
          const tile = document.createElement('button');
          tile.type = 'button';
          tile.className = 'fw-scanpay-sheet__tile';
          const image = document.createElement('img');
          image.alt = '头像候选';
          image.referrerPolicy = 'no-referrer';
          image.src = url;
          image.onerror = () => { image.onerror = null; image.src = bundledAvatarFor(url); };
          tile.appendChild(image);
          tile.onclick = () => applyAvatar(url);
          grid.appendChild(tile);
        });
      });
    };
    const upload = sheet.querySelector('input[type="file"]');
    upload.onchange = event => {
      const file = event.target.files && event.target.files[0];
      if (!file || !file.type.startsWith('image/')) return;
      const reader = new FileReader();
      reader.onload = () => {
        const source = new Image();
        source.onload = () => {
          const canvas = document.createElement('canvas');
          const scale = Math.min(1, 256 / Math.max(source.width, source.height));
          canvas.width = Math.max(1, Math.round(source.width * scale));
          canvas.height = Math.max(1, Math.round(source.height * scale));
          canvas.getContext('2d').drawImage(source, 0, 0, canvas.width, canvas.height);
          applyAvatar(canvas.toDataURL('image/jpeg', 0.82));
        };
        source.src = reader.result;
      };
      reader.readAsDataURL(file);
    };
    sheet.querySelector('[data-cancel]').onclick = close;
    sheet.querySelector('[data-refresh]').onclick = load;
    sheet.addEventListener('click', event => { if (event.target === sheet) close(); });
    load();
  }

  function openScanPayAvatarPicker(page, profile, onPicked) {
    openAvatarPickerSheet(page, value => {
      profile.avatar = value;
      scanPaySave();
      onPicked();
    });
  }

  /** 通用「网名 / 真名」编辑面板（扫一扫付款页和支付成功页共用）。 */
  function openProfileNameEditor(host, data, onSaved, options) {
    host.querySelector('.fw-scanpay-editor')?.remove();
    const editor = document.createElement('div');
    editor.className = 'fw-chat-pay-input-overlay fw-scanpay-editor';
    editor.innerHTML = '<form class="fw-scanpay-editor__panel"><strong>修改付款信息</strong>'
      + '<label><span>网名</span><input name="name" maxlength="24" autocomplete="off" placeholder="例如：ly"></label>'
      + '<label><span>真名</span><input name="realName" maxlength="12" autocomplete="off" placeholder="例如：王玮"></label>'
      + '<p>真名在付款页只显示最后一个字，其余用 * 代替。</p>'
      + '<div><button type="button" data-cancel>取消</button><button type="submit">保存</button></div></form>';
    host.appendChild(editor);
    const nameInput = editor.querySelector('[name="name"]');
    const realInput = editor.querySelector('[name="realName"]');
    nameInput.value = data.name || '';
    realInput.value = data.realName || '';
    const close = () => editor.remove();
    editor.querySelector('[data-cancel]').onclick = close;
    editor.addEventListener('click', event => { if (event.target === editor) close(); });
    editor.querySelector('form').onsubmit = event => {
      event.preventDefault();
      const name = nameInput.value.trim();
      data.name = name || data.name || 'ly';
      data.realName = realInput.value.trim();
      close();
      if (typeof onSaved === 'function') onSaved();
    };
    const focus = options && options.focus === 'realName' ? realInput : nameInput;
    requestAnimationFrame(() => { focus.focus(); focus.select(); });
    try { window.DoutuApp && window.DoutuApp.showSoftKeyboard && window.DoutuApp.showSoftKeyboard(); } catch (_) { /* no bridge */ }
    setTimeout(() => { try { window.DoutuApp && window.DoutuApp.showSoftKeyboard && window.DoutuApp.showSoftKeyboard(); } catch (_) { /* no bridge */ } }, 160);
  }

  function editScanPayNames(page, profile, onSaved) {
    openProfileNameEditor(page, profile, () => {
      scanPaySave();
      onSaved();
    });
  }

  function mountScanPayScreen() {
    closeChatPayScreen();
    const profile = scanPayProfile();
    const page = document.createElement('section');
    page.className = 'fw-chat-pay-screen fw-chat-pay-screen--transfer fw-scanpay';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', '付款');
    page.innerHTML = '<header class="fw-chat-pay-header"><button type="button" class="fw-chat-pay-back" aria-label="返回">‹</button><strong>付款</strong><span></span></header>'
      + '<main class="fw-chat-pay-body">'
      + '<section class="fw-chat-pay-recipient"><button type="button" class="fw-scanpay-payee" data-payee aria-label="修改付款信息"><strong>付款给个人</strong><small></small></button>'
      + '<button type="button" class="fw-scanpay-avatar" data-avatar aria-label="更换头像"><img alt=""></button></section>'
      + '<section class="fw-chat-pay-transfer-card"><label>金额</label>'
      + '<div class="fw-chat-pay-money"><b>¥</b><output data-amount><span data-amount-value>0</span><i class="fw-chat-pay-caret" aria-hidden="true"></i></output></div>'
      + '<button type="button" class="fw-chat-pay-note" data-note>添加备注</button></section></main>'
      + '<footer class="fw-chat-pay-keypad"><div class="fw-chat-pay-keys">'
      + ['1', '2', '3'].map(digit => '<button data-key="' + digit + '">' + digit + '</button>').join('')
      + '<button data-key="back" aria-label="删除">⌫</button>'
      + ['4', '5', '6', '7', '8', '9'].map(digit => '<button data-key="' + digit + '">' + digit + '</button>').join('')
      + '<button class="wide" data-key="0">0</button><button data-key="dot">.</button>'
      + '</div><button type="button" class="fw-chat-pay-submit fw-chat-pay-submit--transfer" disabled>付款</button></footer>';
    document.body.appendChild(page);
    chatPayScreen = page;
    document.body.classList.add('fw-chat-pay-open');
    setChatPaySystemBars(true);
    requestAnimationFrame(() => page.classList.add('fw-chat-pay-screen--enter'));

    const payeeButton = page.querySelector('[data-payee]');
    const avatarButton = page.querySelector('[data-avatar]');
    const avatarImage = avatarButton.querySelector('img');
    const paint = () => {
      payeeButton.querySelector('small').textContent = profile.name + (profile.realName ? '（' + scanPayMaskedName(profile.realName) + '）' : '');
      avatarImage.src = profile.avatar || bundledAvatarFor(profile.name);
      avatarImage.onerror = () => { avatarImage.onerror = null; avatarImage.src = bundledAvatarFor(profile.name); };
    };
    paint();
    page.querySelector('.fw-chat-pay-back').onclick = closeChatPayScreen;
    payeeButton.onclick = () => editScanPayNames(page, profile, paint);
    avatarButton.onclick = () => openScanPayAvatarPicker(page, profile, paint);

    let amount = '';
    const amountValue = page.querySelector('[data-amount-value]');
    const submit = page.querySelector('.fw-chat-pay-submit--transfer');
    const noteButton = page.querySelector('[data-note]');
    const render = () => {
      // Real WeChat shows only ¥ + the caret while the amount is still empty.
      amountValue.textContent = amount || '';
      const valid = Number.isFinite(Number(amount)) && Number(amount) > 0 && Number(amount) <= 9999999.99;
      submit.disabled = !valid;
      submit.setAttribute('aria-disabled', String(!valid));
      submit.classList.toggle('is-ready', valid);
    };
    page.querySelectorAll('[data-key]').forEach(key => {
      key.onclick = () => {
        const action = key.dataset.key;
        if (action === 'back') amount = amount.slice(0, -1);
        else if (action === 'dot') { if (!amount.includes('.')) amount = amount ? amount + '.' : '0.'; }
        else if (amount.length < 10 && (!amount.includes('.') || amount.split('.')[1].length < 2)) amount = amount === '0' ? action : amount + action;
        if (Number(amount) > 9999999.99) amount = '9999999.99';
        render();
      };
    });
    noteButton.onclick = () => showChatPayInput(page, '备注', noteButton.dataset.value || '', { type: 'text', maxLength: 20 }, value => {
      noteButton.dataset.value = value.trim();
      noteButton.textContent = value.trim() || '添加备注';
    });
    submit.onclick = () => {
      const value = Math.round(Number(amount) * 100) / 100;
      if (!(value > 0)) return;
      const label = profile.name + (profile.realName ? '（' + scanPayMaskedName(profile.realName) + '）' : '');
      const payee = { id: 'scanpay', name: profile.name, remark: '', avatar: profile.avatar, isScanPay: true };
      // Same confirmation sheet + 6-digit password + success screen as 好友转账.
      mountTransferConfirmSheet(page, payee, label, value, noteButton.dataset.value || '', () => {
        try {
          window.__fwAddWalletBill?.({
            title: '付款给个人', friendName: profile.name, avatar: profile.avatar,
            amount: -value, createdAt: Date.now(),
          });
        } catch (_) { /* wallet bills are optional */ }
        showToast('已付款 ¥' + value.toFixed(2));
      }, { mode: 'scanpay', name: label, avatar: profile.avatar });
    };
    render();
  }

  /* ================= 收付款 (2026-10-11) =================
     Page green is WeChat green #07C160 = rgb(7, 193, 96). The barcode and the QR
     code are redrawn from scratch on every open, so they never come out the same twice. */
  const RECEIVE_PAY_ICON = {
    scan: '<svg viewBox="0 0 24 24"><path d="M4 8.4V6a2 2 0 0 1 2-2h2.4M15.6 4H18a2 2 0 0 1 2 2v2.4M20 15.6V18a2 2 0 0 1-2 2h-2.4M8.4 20H6a2 2 0 0 1-2-2v-2.4M3.4 12h17.2"/></svg>',
    qr: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9.2"/><path d="M9.4 9.4h5.2v5.2H9.4z"/><path d="M12 2.8v2.3M12 18.9v2.3M2.8 12h2.3M18.9 12h2.3"/></svg>',
    packet: '<svg viewBox="0 0 24 24"><path d="M6.6 3.6h10.8v16.8H6.6z"/><path d="M6.6 8.6c1.9 1.5 3.6 2.3 5.4 2.3s3.5-.8 5.4-2.3"/><circle cx="12" cy="13.6" r="1.6"/></svg>',
    bank: '<svg viewBox="0 0 24 24"><rect x="2.6" y="5.4" width="18.8" height="13.2" rx="2.2"/><path d="M2.6 9.7h18.8"/><path d="M6.2 14.8h3.6"/><path d="m14.4 12.9 3.4 2.6-3.4 2.6z"/></svg>',
  };

  function randomBarcodeDataUrl() {
    const width = 760;
    const height = 200;
    const quiet = 26;
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, width, height);
    ctx.fillStyle = '#111';
    let x = quiet;
    while (x < width - quiet) {
      const bar = 2 + Math.floor(Math.random() * 6);
      const gap = 2 + Math.floor(Math.random() * 6);
      ctx.fillRect(x, 18, bar, height - 36);
      x += bar + gap;
    }
    return canvas.toDataURL('image/png');
  }

  function randomQrDataUrl() {
    const modules = 25 + Math.floor(Math.random() * 4);
    const quiet = 4;
    const scale = 9;
    const size = (modules + quiet * 2) * scale;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#fff';
    ctx.fillRect(0, 0, size, size);
    ctx.fillStyle = '#111';
    const grid = [];
    for (let y = 0; y < modules; y += 1) {
      grid.push([]);
      for (let x = 0; x < modules; x += 1) grid[y].push(Math.random() < 0.47 ? 1 : 0);
    }
    // Three finder patterns + separators + timing rows: enough structure that the code
    // reads as a real QR at a glance.
    const finder = (ox, oy) => {
      for (let y = -1; y < 8; y += 1) {
        for (let x = -1; x < 8; x += 1) {
          const gx = ox + x;
          const gy = oy + y;
          if (gx < 0 || gy < 0 || gx >= modules || gy >= modules) continue;
          if (x === -1 || y === -1 || x === 7 || y === 7) { grid[gy][gx] = 0; continue; }
          const edge = x === 0 || y === 0 || x === 6 || y === 6;
          const core = x >= 2 && x <= 4 && y >= 2 && y <= 4;
          grid[gy][gx] = (edge || core) ? 1 : 0;
        }
      }
    };
    finder(0, 0);
    finder(modules - 7, 0);
    finder(0, modules - 7);
    for (let i = 8; i < modules - 8; i += 1) {
      grid[6][i] = i % 2 === 0 ? 1 : 0;
      grid[i][6] = i % 2 === 0 ? 1 : 0;
    }
    for (let y = 0; y < modules; y += 1) {
      for (let x = 0; x < modules; x += 1) {
        if (!grid[y][x]) continue;
        ctx.fillRect((x + quiet) * scale, (y + quiet) * scale, scale, scale);
      }
    }
    return canvas.toDataURL('image/png');
  }

  function closeReceivePayScreen() {
    const page = document.querySelector('.fw-receive-pay');
    if (!page || page.dataset.closing === '1') return;
    page.dataset.closing = '1';
    page.classList.add('fw-receive-pay--exit');
    const finish = () => { if (page.isConnected) page.remove(); };
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 220);
  }

  function mountReceivePayScreen() {
    document.querySelector('.fw-receive-pay')?.remove();
    const page = document.createElement('section');
    page.className = 'fw-receive-pay';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', '收付款');
    const item = (icon, label) => '<button type="button" class="fw-receive-pay__item" data-item="' + label + '"><i aria-hidden="true">' + RECEIVE_PAY_ICON[icon] + '</i><span>' + label + '</span><em>›</em></button>';
    page.innerHTML = '<header class="fw-receive-pay__header"><button type="button" class="fw-receive-pay__back" aria-label="返回">‹</button><strong>收付款</strong><span></span></header>'
      + '<div class="fw-receive-pay__body"><section class="fw-receive-pay__card">'
      + '<div class="fw-receive-pay__row"><span class="fw-receive-pay__title"><i aria-hidden="true">' + RECEIVE_PAY_ICON.scan + '</i>付款码</span><button type="button" class="fw-receive-pay__more" aria-label="更多">•••</button></div>'
      + '<p class="fw-receive-pay__hint">扫码可查看付款码数字</p>'
      + '<img class="fw-receive-pay__barcode" alt="付款码条形码">'
      + '<img class="fw-receive-pay__qr" alt="付款码二维码">'
      + '<div class="fw-receive-pay__rule"></div>'
      + '<div class="fw-receive-pay__method-head"><span>优先付款方式</span><button type="button" class="fw-receive-pay__change">更改 <i aria-hidden="true"><svg viewBox="0 0 12 8"><path d="M1 1.6 6 6.4 11 1.6"/></svg></i></button></div>'
      + '<button type="button" class="fw-receive-pay__method" data-item="优先付款方式"><i class="fw-receive-pay__coin" aria-hidden="true"></i><span>零钱</span><b aria-hidden="true">✓</b></button>'
      + '</section>'
      + '<div class="fw-receive-pay__list">'
      + item('qr', '二维码收款')
      + item('packet', '面对面红包')
      + item('bank', '向银行卡或手机号转账')
      + '</div></div>';
    document.body.appendChild(page);
    // Fresh codes on every open.
    page.querySelector('.fw-receive-pay__barcode').src = randomBarcodeDataUrl();
    page.querySelector('.fw-receive-pay__qr').src = randomQrDataUrl();
    page.querySelector('.fw-receive-pay__back').onclick = closeReceivePayScreen;
    page.querySelector('.fw-receive-pay__more').onclick = () => showToast('付款码已刷新');
    page.querySelector('.fw-receive-pay__change').onclick = () => showToast('仅作界面演示');
    page.querySelectorAll('.fw-receive-pay__item').forEach(button => {
      button.onclick = () => {
        const label = button.dataset.item;
        if (label === '优先付款方式') { showToast('仅作界面演示'); return; }
        page.querySelector('.fw-receive-pay__barcode').src = randomBarcodeDataUrl();
        page.querySelector('.fw-receive-pay__qr').src = randomQrDataUrl();
        showToast(label + '：付款码已随机刷新');
      };
    });
    requestAnimationFrame(() => page.classList.add('fw-receive-pay--enter'));
  }

  // Android back closes the page before the chat/pay chains below it.
  (function installReceivePayBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      if (document.querySelector('.fw-receive-pay')) { closeReceivePayScreen(); return true; }
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  /**
   * 我 → 服务 → 收付款 打开的也是这一页（和微信主页右上角 + → 收付款 完全同一个），
   * 由 wechat-pay-pages.js 在服务页里调用。入口只挂这一层整屏页、不改路由，
   * 所以 Android 返回键和页内 ‹ 都会回到用户进来的那一页（服务 / 微信主页）。
   */
  window.__fwOpenReceivePay = function () {
    mountReceivePayScreen();
    return true;
  };

  /* ================= 模拟来电：语音 / 视频 (2026-10-11) =================
     素材（原 APK 导出，路径与原包一致）：
       assets/audio/call.mp3                        来电铃声，3.81s，循环播放
       assets/images/wechat/voice_call/*.png        接听(绿 0xff1bc911)/挂断(红 0xffd54241)/
                                                    麦克风/扬声器/缩小，白色图标
       assets/images/wechat/video_call/*.png        翻转/画面占位，只取 light 那套
                                                     （浅色底配深色图形），dark 不用
     文案照原包：邀请你语音通话... / 邀请你视频通话... / 接听 / 挂断 /
     麦克风已开|已关 / 扬声器已开|已关 / 摄像头已开|已关 / 翻转 / 修改通话时长。
     行为：响铃 60 秒超时自动挂断；接听时铃声淡出；通话计时真实走动；挂断后原路返回；
     不做震动、不做闪光。真机能力：视频真开摄像头（前后摄可翻转、可关闭）、麦克风开关
     真的 mute 本地音轨、扬声器开关真的切系统通话路由。
     存储（localStorage，沿用本工程 fw-* 命名）：
       fw-call-ringtone  来电铃声（默认 ./assets/audio/call.mp3）
       fw-call-duration  上次设置的通话时长（秒）
       fw-call-unread    未接来电累计未读数 */
  const CALL_ART = './assets/images/wechat/';
  const CALL_RING_TIMEOUT_MS = 60000;
  const CALL_KEYS = { ringtone: 'fw-call-ringtone', duration: 'fw-call-duration', unread: 'fw-call-unread' };
  const call = {
    page: null, audio: null, timer: null, ringTimeout: null, seconds: 0,
    mode: 'voice', state: 'ringing', mic: true, speaker: false,
    micStream: null, micTracks: [],
    collapsed: false, contact: null, missed: false,
    direction: 'incoming', friend: null, connectTimer: null,
  };

  function callStore(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, String(value));
    } catch (_) { /* private mode: keep everything in memory */ }
    return null;
  }

  function callContact() {
    // 优先从本地联系人里挑：他们带 messages 容器，挂断后那条通话结果消息才有地方落。
    const locals = (Array.isArray(state.contacts) ? state.contacts : [])
      .map(friend => ({ friend, name: String(friend.remark || friend.name || '').trim(), avatar: friend.avatar || friend.avatarInfo || '' }))
      .filter(item => item.name);
    if (locals.length) return locals[Math.floor(Math.random() * locals.length)];
    let pool = [];
    try { pool = collectContacts().filter(item => item && item.name); } catch (_) { pool = []; }
    if (!pool.length) return { name: '微信用户', avatar: './assets/user-avatar-1-CKPDcH57.jpg' };
    return pool[Math.floor(Math.random() * pool.length)];
  }

  /** 本地画面：摄像头"开"时用自己头像冒充取景画面，关的时候是原包那张占位图。 */
  function callSelfAvatar() {
    try {
      const profile = typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : null;
      const value = profile && profile.avatarInfo;
      return typeof value === 'string' ? value.trim() : '';
    } catch (_) { return ''; }
  }

  function callDurationText() {
    const total = Math.max(0, Math.floor(call.seconds));
    return String(Math.floor(total / 60)).padStart(2, '0') + ':' + String(total % 60).padStart(2, '0');
  }

  /* ---------- 通话结果消息（参考图 1/2/3） ---------- */

  function callClockText(seconds) {
    const total = Math.max(0, Math.floor(Number(seconds) || 0));
    return String(Math.floor(total / 60)).padStart(2, '0') + ':' + String(total % 60).padStart(2, '0');
  }

  function parseClockInput(value) {
    const raw = String(value == null ? '' : value).trim();
    const parts = raw.split(':');
    const seconds = parts.length > 1
      ? (Number(parts[0]) || 0) * 60 + (Number(parts[1]) || 0)
      : (Number(raw.replace(/[^0-9]/g, '')) || 0);
    return Math.max(0, Math.min(359999, Math.floor(seconds)));
  }

  function callMessageVariant(message) {
    const variant = String(message && message.variant || '');
    return variant === 'rejected' || variant === 'cancelled' ? variant : 'duration';
  }

  function callMessageText(message) {
    const variant = callMessageVariant(message);
    if (variant === 'rejected') return '已拒绝';
    if (variant === 'cancelled') return '已取消';
    return '通话时长 ' + callClockText(message && message.seconds);
  }

  /** 点"通话时长"那条消息 → 改时长（改完立刻重画并落盘）。 */
  function editCallMessageDuration(message) {
    const page = document.querySelector('.fw-local-conversation');
    if (!page || !page.isConnected) return;
    showChatPayInput(page, '修改通话时长', callClockText(message.seconds), { inputMode: 'numeric', maxLength: 8 }, value => {
      message.seconds = parseClockInput(value);
      saveState();
      if (typeof page.__fwDrawMessages === 'function') page.__fwDrawMessages(true);
    });
  }

  /** 挂断之后把这条消息追加到和该好友的聊天里（聊天开着就立刻重画）。 */
  function appendCallMessage(friend, message) {
    if (!friend) return;
    friend.messages = Array.isArray(friend.messages) ? friend.messages : [];
    friend.messages.push(message);
    saveState();
    const page = document.querySelector('.fw-local-conversation');
    if (page && page.isConnected && String(page.dataset.friendId) === String(friend.id)
      && typeof page.__fwDrawMessages === 'function') {
      page.__fwDrawMessages(false);
    }
  }

  /**
   * 长按消息弹窗里点「修改」：直接把这条通话消息切到下一种样式 ——
   * 通话时长 → 已拒绝 → 已取消 → 通话时长（图标位置跟着消息左右侧走）。
   */
  function cycleCallMessageVariant(message) {
    if (!message) return;
    const order = ['duration', 'rejected', 'cancelled'];
    const current = callMessageVariant(message);
    const next = order[(order.indexOf(current) + 1) % order.length];
    message.variant = next;
    saveState();
    const page = document.querySelector('.fw-local-conversation');
    if (page && typeof page.__fwDrawMessages === 'function') page.__fwDrawMessages(true);
    showToast('已切换为「' + callMessageText(message) + '」');
  }

  /* ================= 朋友圈集赞 (2026-10-11) =================
     入口：小微主页 → 朋友圈集赞。先打开朋友圈，点一条动态，填数量和类型（真名/网民/混合，
     上限 200），再按类型去接口取名字写进这条动态的点赞里：
       真名 → https://api.mir6.com/api/sjname（纯文本，一次一个名字；跨域，走原生 httpGet 桥）
       网民 → https://cn.apihz.cn/api/zici/sjwm.php?id=88888888&key=88888888（JSON，页面内 fetch）
     接口取不到（超时/额度/断网）就用 App 自带的姓名/昵称池兜底，保证数量一定够。 */
  const MOMENTS_LIKE_MAX = 200;
  const MOMENTS_LIKE_REAL_URL = 'https://api.mir6.com/api/sjname';
  const MOMENTS_LIKE_NET_URL = 'https://cn.apihz.cn/api/zici/sjwm.php?id=88888888&key=88888888';
  const momentsBoost = { active: false, busy: false, bar: null, pending: new Map(), seq: 0 };

  /** 原生 httpGet 的回调（见 WeiHomeActivity.AppNavigationBridge.httpGet）。 */
  window.__fwHttpGetResult = function (id, text) {
    const resolve = momentsBoost.pending.get(String(id));
    if (!resolve) return;
    momentsBoost.pending.delete(String(id));
    resolve(String(text == null ? '' : text));
  };

  function momentsHttpGet(url) {
    return new Promise(resolve => {
      const bridge = window.DoutuApp;
      if (!bridge || typeof bridge.httpGet !== 'function') { resolve(''); return; }
      const id = 'g' + (momentsBoost.seq += 1);
      const timer = window.setTimeout(() => {
        momentsBoost.pending.delete(id);
        resolve('');
      }, 9000);
      momentsBoost.pending.set(id, text => { clearTimeout(timer); resolve(text); });
      try {
        bridge.httpGet(url, id);
      } catch (_) {
        clearTimeout(timer);
        momentsBoost.pending.delete(id);
        resolve('');
      }
    });
  }

  function momentsLocalRealName() {
    const pool = (window.__fwNamePools && window.__fwNamePools.realnames) || [];
    const name = pool.length ? String(pool[Math.floor(Math.random() * pool.length)]).trim() : '';
    return /^[\u4e00-\u9fa5]{2,4}$/.test(name) ? name : '张伟';
  }

  function momentsLocalNetName() {
    const pool = (window.__fwNamePools && window.__fwNamePools.nicknames) || [];
    const name = pool.length ? String(pool[Math.floor(Math.random() * pool.length)]).trim() : '';
    return name || '一只小可爱';
  }

  function momentsCleanName(text, max) {
    return String(text == null ? '' : text).replace(/[<>\r\n\t]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);
  }

  async function momentsFetchLikerName(kind) {
    if (kind === 'real') {
      const text = await momentsHttpGet(MOMENTS_LIKE_REAL_URL);
      const name = momentsCleanName(text, 8).replace(/\s/g, '');
      if (/^[\u4e00-\u9fa5]{2,4}$/.test(name)) return name;
      return momentsLocalRealName();
    }
    try {
      const response = await fetch(MOMENTS_LIKE_NET_URL, { cache: 'no-store' });
      const data = await response.json();
      const name = momentsCleanName(data && data.msg, 16);
      if (name) return name;
    } catch (_) { /* 断网或额度用尽：落到本地昵称池 */ }
    return momentsLocalNetName();
  }

  /** 按类型批量取名：真名/网民/混合；6 个并发，边取边报进度。 */
  async function momentsBuildLikerNames(count, type, onProgress) {
    const names = new Array(count);
    let cursor = 0;
    const workerCount = Math.max(1, Math.min(6, count));
    const workers = [];
    for (let worker = 0; worker < workerCount; worker += 1) {
      workers.push((async () => {
        while (cursor < count) {
          const index = cursor;
          cursor += 1;
          const kind = type === 'net' ? 'net' : type === 'real' ? 'real' : (index % 2 === 0 ? 'real' : 'net');
          names[index] = await momentsFetchLikerName(kind);
          if (typeof onProgress === 'function' && index % 10 === 0) onProgress(Math.min(count, index + 1));
        }
      })());
    }
    await Promise.all(workers);
    return names.map((name, index) => name || (type === 'net' ? momentsLocalNetName() : momentsLocalRealName()));
  }

  async function momentsApplyBoost(item, count, type) {
    const data = mutableItem(item);
    if (!data) { showToast('这条动态暂时不能集赞'); return; }
    momentsBoost.busy = true;
    const typeLabel = type === 'real' ? '真名' : type === 'net' ? '网民' : '混合';
    showToast('正在生成 ' + count + ' 个' + typeLabel + '赞…');
    let names = [];
    try {
      names = await momentsBuildLikerNames(count, type, done => showToast('正在生成点赞 ' + done + '/' + count + ' …'));
    } catch (_) { names = []; }
    if (!names.length) {
      momentsBoost.busy = false;
      showToast('生成失败，请检查网络后重试');
      return;
    }
    data.likes = names.slice(0, count);
    savePostAndRefresh(item);
    momentsBoost.busy = false;
    momentsBoostExit();
    showToast('已为这条动态生成 ' + data.likes.length + ' 个赞');
  }

  function openMomentsBoostDialog(item) {
    const snapshot = timelineItems().find(entry => String(entry.id) === String(item && item.id)) || item;
    const current = Array.isArray(snapshot && snapshot.likes) ? snapshot.likes.length : 0;
    const body = '<div class="fw-tl-boost">'
      + '<div class="fw-tl-boost__row"><span>点赞数量</span>'
      + '<input class="fw-tl-boost__count" type="number" min="1" max="' + MOMENTS_LIKE_MAX + '" step="1" inputmode="numeric" value="'
      + Math.min(MOMENTS_LIKE_MAX, Math.max(1, current || 50)) + '"><em>最多 ' + MOMENTS_LIKE_MAX + ' 个</em></div>'
      + '<div class="fw-tl-boost__types">'
      + '<button type="button" class="fw-tl-boost__type is-active" data-like-type="real">真名</button>'
      + '<button type="button" class="fw-tl-boost__type" data-like-type="net">网民</button>'
      + '<button type="button" class="fw-tl-boost__type" data-like-type="mixed">混合</button>'
      + '</div>'
      + '<p class="fw-tl-boost__hint">真名取自 api.mir6.com，网民取自 api.aa1.cn 收录的昵称接口；接口不通时用本地名字池补齐。</p>'
      + '</div>';
    const { dialog } = makeDialog('朋友圈集赞', '这条朋友圈要多少个赞、什么类型的人', body, [
      { label: '取消', onClick: closeOverlay },
      {
        label: '生成',
        primary: true,
        onClick: () => {
          const input = dialog.querySelector('.fw-tl-boost__count');
          const raw = Number(String(input && input.value || '').trim());
          if (!Number.isFinite(raw) || raw < 1) { showToast('请填写 1-' + MOMENTS_LIKE_MAX + ' 之间的数量'); return; }
          const count = Math.min(MOMENTS_LIKE_MAX, Math.floor(raw));
          if (count !== Math.floor(raw)) showToast('数量最多 ' + MOMENTS_LIKE_MAX + ' 个，已按上限生成');
          closeOverlay();
          momentsApplyBoost(snapshot, count, likeType);
        },
      },
    ]);
    let likeType = 'real';
    dialog.querySelectorAll('[data-like-type]').forEach(button => {
      button.addEventListener('click', () => {
        likeType = button.dataset.likeType || 'real';
        dialog.querySelectorAll('[data-like-type]').forEach(other => other.classList.toggle('is-active', other === button));
      });
    });
  }

  function momentsBoostExit() {
    momentsBoost.active = false;
    momentsBoost.bar?.remove();
    momentsBoost.bar = null;
  }

  function momentsBoostEnter() {
    momentsBoostExit();
    const page = document.querySelector('.fw-moments-feed');
    if (!page) return;
    momentsBoost.active = true;
    const bar = document.createElement('div');
    bar.className = 'fw-tl-boost-bar';
    bar.innerHTML = '<span>点一条朋友圈，给它集赞</span><button type="button" data-boost-exit>完成</button>';
    bar.querySelector('[data-boost-exit]').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      momentsBoostExit();
      showToast('已退出集赞模式');
    });
    page.appendChild(bar);
    momentsBoost.bar = bar;
  }

  /** 小微主页 → 朋友圈集赞：进朋友圈 + 进入"选动态"模式。 */
  function startMomentsLikeBoost() {
    openMomentsTimeline();
    momentsBoostEnter();
    showToast('点一条朋友圈给它集赞');
  }

  // 集赞模式下点任意一条动态 → 弹设置面板。
  document.addEventListener('click', event => {
    if (!momentsBoost.active || momentsBoost.busy) return;
    const lane = event.target instanceof Element ? event.target.closest('.fw-tl-item') : null;
    if (!lane) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    const item = timelineItems().find(entry => String(entry.id) === String(lane.dataset.fwItemId));
    if (!item) { showToast('这条动态暂时不能集赞'); return; }
    openMomentsBoostDialog(item);
  }, true);

  function stopCallRing() {
    if (!call.audio) return;
    try { call.audio.pause(); } catch (_) { /* ignore */ }
    call.audio = null;
  }

  /** 接听时铃声淡出（400ms 音量斜坡），淡完再停。 */
  function fadeOutCallRing(then) {
    const audio = call.audio;
    if (!audio) { if (typeof then === 'function') then(); return; }
    const started = Date.now();
    const step = () => {
      if (call.audio !== audio) return;
      const progress = Math.min(1, (Date.now() - started) / 400);
      try { audio.volume = Math.max(0, 1 - progress); } catch (_) { /* ignore */ }
      if (progress < 1) { window.setTimeout(step, 40); return; }
      stopCallRing();
      if (typeof then === 'function') then();
    };
    step();
  }

  function startCallRing() {
    stopCallRing();
    try {
      const source = String(callStore(CALL_KEYS.ringtone) || './assets/audio/call.mp3');
      const audio = new Audio(source);
      audio.loop = true;
      audio.volume = 1;
      const played = audio.play();
      if (played && typeof played.catch === 'function') played.catch(() => {});
      call.audio = audio;
    } catch (_) { /* no ringtone available */ }
  }

  /** 响铃 60 秒没人接 → 自动挂断，并记一次未接（未读数落在 localStorage）。 */
  function armCallRingTimeout() {
    if (call.ringTimeout) { clearTimeout(call.ringTimeout); call.ringTimeout = null; }
    call.ringTimeout = window.setTimeout(() => {
      call.ringTimeout = null;
      if (!call.page || call.state !== 'ringing') return;
      call.missed = true;
      callStore(CALL_KEYS.unread, (Number(callStore(CALL_KEYS.unread)) || 0) + 1);
      closeSimulatedCall();
    }, CALL_RING_TIMEOUT_MS);
  }

  function clearCallRingTimeout() {
    if (call.ringTimeout) { clearTimeout(call.ringTimeout); call.ringTimeout = null; }
  }

  /* ---------- 真机能力：麦克风 / 扬声器（语音通话，不做视频） ---------- */

  function stopCallMic() {
    call.micTracks = [];
    if (call.micStream) {
      call.micStream.getTracks().forEach(track => { try { track.stop(); } catch (_) {} });
      call.micStream = null;
    }
  }

  /** 真开麦克风：接听后拿到本地音轨，麦克风开关就是 mute / unmute 这条轨。 */
  function startCallMic() {
    if (call.micStream) { call.micTracks.forEach(track => { track.enabled = call.mic; }); return Promise.resolve(true); }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return Promise.resolve(false);
    return navigator.mediaDevices.getUserMedia({ audio: true })
      .then(stream => {
        call.micStream = stream;
        call.micTracks = stream.getAudioTracks();
        call.micTracks.forEach(track => { track.enabled = call.mic; });
        return true;
      })
      .catch(() => { call.micStream = null; call.micTracks = []; return false; });
  }

  /** 扬声器开关走原生 AudioManager（DoutuApp.setCallAudioMode），真的改系统路由。 */
  function applyCallAudioMode() {
    try {
      const bridge = window.DoutuApp;
      if (bridge && typeof bridge.setCallAudioMode === 'function') {
        bridge.setCallAudioMode(call.state === 'active', !!call.speaker);
      }
    } catch (_) { /* bridge missing (browser preview) */ }
  }

  function stopCallTimer() {
    if (call.timer) { clearInterval(call.timer); call.timer = null; }
  }

  /**
   * 来电页自己的小提示：不用全局 showToast（那套浮层关掉后会吞掉 450ms 内的下一次点击，
   * 连着拨麦克风/扬声器时第二下会失效）。
   */
  function callToast(text) {
    const page = call.page;
    if (!page || !page.isConnected) return;
    page.querySelectorAll('.fw-call__toast').forEach(node => node.remove());
    const node = document.createElement('div');
    node.className = 'fw-toast fw-call__toast';
    node.textContent = text;
    page.appendChild(node);
    setTimeout(() => node.remove(), 1400);
  }

  function startCallTimer() {
    stopCallTimer();
    call.seconds = 0;
    call.timer = setInterval(() => {
      if (!call.page || !call.page.isConnected || call.state !== 'active') return;
      call.seconds += 1;
      const status = call.page.querySelector('.fw-call__status');
      if (status && !call.collapsed) status.textContent = callDurationText();
      const mini = call.page.querySelector('.fw-call__mini-time');
      if (mini) mini.textContent = callDurationText();
    }, 1000);
  }

  function renderSimulatedCall() {
    const page = call.page;
    if (!page || !page.isConnected) return;
    const contact = call.contact || (call.contact = callContact());
    const active = call.state === 'active';
    // 圆形按钮：底色 + 图标。素材自带深浅两版 —— on 是黑图标配白底，off 是白图标配深底，
    // 挂断/接听是白图标配红(#d54241)/绿(#1bc911)底，和参考图一致。
    const discBtn = (attrs, tone, src, label) =>
      '<button class="fw-call__btn" type="button" ' + attrs + '>'
      + '<i class="fw-call__disc fw-call__disc--' + tone + '"><img src="' + CALL_ART + src + '" alt=""></i>'
      + '<span>' + label + '</span></button>';
    const outgoing = call.direction === 'outgoing';
    const micBtn = discBtn('data-call-toggle="mic"', call.mic ? 'white' : 'dark',
      'voice_call/maikefeng_' + (call.mic ? 'on' : 'off') + '.png',
      call.mic ? '麦克风已开' : '麦克风已关');
    const speakerBtn = discBtn('data-call-toggle="speaker"', call.speaker ? 'white' : 'dark',
      'voice_call/yangshengqi_' + (call.speaker ? 'on' : 'off') + '.png',
      call.speaker ? '扬声器已开' : '扬声器已关');
    // 已接通：麦克风 / 挂断 / 扬声器；未接通：打进来的是 挂断+接听，
    // 我打出去的是 麦克风 / 取消 / 扬声器（和参考图一致）。
    const buttons = active
      ? micBtn + discBtn('data-call-hangup', 'red', 'voice_call/guaduan.png', '挂断') + speakerBtn
      : outgoing
        ? micBtn + discBtn('data-call-hangup', 'red', 'voice_call/guaduan.png', '取消') + speakerBtn
        : discBtn('data-call-hangup', 'red', 'voice_call/guaduan.png', '挂断')
          + discBtn('data-call-accept', 'green', 'voice_call/jieting.png', '接听');
    page.className = 'fw-call fw-call--' + (active ? 'active' : 'ringing') + (call.collapsed ? ' fw-call--collapsed' : '');
    page.innerHTML =
      // 底层：对方头像放大到满屏 + 重度虚化。
      '<div class="fw-call__blur" aria-hidden="true"><img alt=""></div>'
      + '<div class="fw-call__stage">'
      + '<header class="fw-call__top">'
      + (active
        ? '<button class="fw-call__shrink" type="button" data-call-shrink aria-label="缩小"><img src="' + CALL_ART + 'voice_call/suoxiao.png" alt=""></button>'
        : '<span></span>')
      + '</header>'
      + '<div class="fw-call__who">'
      + '<img class="fw-call__avatar" alt="">'
      + '<strong class="fw-call__name"></strong>'
      + '</div>'
      + '<div class="fw-call__status-wrap">'
      + '<button class="fw-call__status" type="button"' + (active ? '' : ' disabled') + '>'
      + (active ? callDurationText() : (outgoing ? '等待对方接受邀请' : '邀请你语音通话...')) + '</button>'
      + '</div>'
      + '<footer class="fw-call__actions">' + buttons + '</footer>'
      + '</div>'
      + (call.collapsed
        ? '<button class="fw-call__mini" type="button" data-call-mini><img alt=""><span><b></b><em class="fw-call__mini-time">' + callDurationText() + '</em></span></button>'
        : '');
    const avatar = page.querySelector('.fw-call__avatar');
    const name = page.querySelector('.fw-call__name');
    const src = String(contact.avatar || '').trim();
    const CALL_AVATAR_FALLBACK = './assets/user-avatar-1-CKPDcH57.jpg';
    name.textContent = contact.name;
    if (avatar) avatar.alt = contact.name;
    const setAvatarSrc = (img, value) => {
      if (!img) return;
      img.onerror = () => { img.onerror = null; img.src = CALL_AVATAR_FALLBACK; };
      img.src = value || CALL_AVATAR_FALLBACK;
    };
    setAvatarSrc(avatar, src);
    if (typeof window.__fwResolveProfileAvatar === 'function' && src) {
      window.__fwResolveProfileAvatar(src).then(resolved => {
        if (!resolved) return;
        if (avatar && avatar.isConnected) setAvatarSrc(avatar, resolved);
        const mini = page.querySelector('.fw-call__mini img');
        if (mini) setAvatarSrc(mini, resolved);
      }).catch(() => {});
    }
    const miniAvatar = page.querySelector('.fw-call__mini img');
    const miniName = page.querySelector('.fw-call__mini b');
    if (miniAvatar) { setAvatarSrc(miniAvatar, avatar ? avatar.src : src); miniAvatar.alt = contact.name; }
    if (miniName) miniName.textContent = contact.name;
    // 底层虚化背景 = 同一张头像。
    setAvatarSrc(page.querySelector('.fw-call__blur img'), src);
    page.querySelector('[data-call-accept]')?.addEventListener('click', () => {
      call.state = 'active';
      clearCallRingTimeout();
      fadeOutCallRing();                 // 接听后铃声淡出
      startCallTimer();
      startCallMic();                    // 真开麦克风，麦克风开关才有东西可切
      applyCallAudioMode();
      renderSimulatedCall();
    });
    page.querySelector('[data-call-hangup]')?.addEventListener('click', () => closeSimulatedCall());
    page.querySelector('[data-call-shrink]')?.addEventListener('click', () => {
      call.collapsed = true;
      renderSimulatedCall();
    });
    page.querySelector('[data-call-mini]')?.addEventListener('click', () => {
      call.collapsed = false;
      renderSimulatedCall();
    });
    page.querySelector('[data-call-toggle="mic"]')?.addEventListener('click', () => {
      call.mic = !call.mic;
      call.micTracks.forEach(track => { try { track.enabled = call.mic; } catch (_) {} });
      if (call.mic && !call.micStream) startCallMic();
      renderSimulatedCall();
      callToast(call.mic ? '麦克风已开' : '麦克风已关');
    });
    page.querySelector('[data-call-toggle="speaker"]')?.addEventListener('click', () => {
      call.speaker = !call.speaker;
      applyCallAudioMode();
      renderSimulatedCall();
      callToast(call.speaker ? '扬声器已开' : '扬声器已关');
    });
    page.querySelector('.fw-call__status')?.addEventListener('click', () => {
      if (call.state !== 'active') return;
      showChatPayInput(page, '修改通话时长', callDurationText(), { inputMode: 'numeric', maxLength: 8 }, value => {
        const parts = String(value || '').trim().split(':');
        const parsed = parts.length > 1
          ? (Number(parts[0]) || 0) * 60 + (Number(parts[1]) || 0)
          : (Number(String(value).replace(/[^0-9]/g, '')) || 0);
        call.seconds = Math.max(0, Math.min(359999, parsed));
        callStore(CALL_KEYS.duration, call.seconds);
        renderSimulatedCall();
      });
    });
  }

  function closeSimulatedCall() {
    const page = call.page;
    clearCallRingTimeout();
    stopCallRing();
    stopCallTimer();
    stopCallMic();
    if (call.connectTimer) { clearTimeout(call.connectTimer); call.connectTimer = null; }
    // 挂断后往聊天里补一条通话结果消息（60 秒无人接听的来电不算，只记未读数）。
    const answered = call.state === 'active';
    const missed = call.missed;
    const friend = call.friend;
    const direction = call.direction;
    const seconds = call.seconds;
    if (page && page.isConnected) {
      let message = null;
      if (answered) {
        message = { type: 'call', variant: 'duration', seconds, side: 'outgoing', createdAt: Date.now() };
      } else if (direction === 'outgoing') {
        message = { type: 'call', variant: 'cancelled', side: 'outgoing', createdAt: Date.now() };
      } else if (!missed) {
        message = { type: 'call', variant: 'rejected', side: 'incoming', createdAt: Date.now() };
      }
      if (message) appendCallMessage(friend, message);
    }
    call.speaker = false;
    call.state = 'ringing';
    applyCallAudioMode();               // 退出通话模式，恢复系统音频路由
    call.page = null;
    call.friend = null;
    call.direction = 'incoming';
    call.collapsed = false;
    call.seconds = 0;
    if (page) {
      page.classList.add('fw-call--exit');
      const finish = () => page.remove();
      page.addEventListener('animationend', finish, { once: true });
      setTimeout(finish, 220);
    }
  }

  /**
   * options = { contact:{name,avatar}, direction:'incoming'|'outgoing', friend }
   *   小微主页「模拟来电」= 谁打给我（incoming，联系人随机）；
   *   聊天里点「视频通话」= 我打给他（outgoing，联系人取当前聊天对象）。
   */
  function mountSimulatedCall(mode, options) {
    // 重新挂载时静默收掉上一页：那是不该补消息的中断，不是真的挂断。
    if (call.page && call.page.isConnected) {
      const previous = call.page;
      call.page = null;
      clearCallRingTimeout();
      stopCallRing();
      stopCallTimer();
      stopCallMic();
      if (call.connectTimer) { clearTimeout(call.connectTimer); call.connectTimer = null; }
      previous.remove();
    }
    document.querySelector('.fw-call')?.remove();
    const opts = options || {};
    call.mode = 'voice';
    call.state = 'ringing';
    call.collapsed = false;
    call.mic = true;
    call.speaker = true;                 // 参考图里扬声器是白色圆底（开着）
    call.missed = false;
    call.direction = opts.direction === 'outgoing' ? 'outgoing' : 'incoming';
    call.contact = opts.contact || callContact();
    // 小微主页那条没有聊天上下文，按随机到的联系人名字找到对应好友，
    // 这样挂断/接听后那条通话结果消息也能落进他的聊天里。
    call.friend = opts.friend
      || (call.contact && call.contact.friend)
      || (Array.isArray(state.contacts)
        ? state.contacts.find(item => String(item.remark || item.name || '') === String(call.contact && call.contact.name || '')) || null
        : null);
    const page = document.createElement('section');
    page.className = 'fw-call fw-call--voice fw-call--ringing';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', '模拟来电');
    document.body.appendChild(page);
    call.page = page;
    renderSimulatedCall();
    startCallRing();
    if (call.direction === 'outgoing') {
      // 我打出去：响两声之后当作对方接听，进通话计时（这样才有时长消息可发）。
      call.connectTimer = window.setTimeout(() => {
        call.connectTimer = null;
        if (!call.page || call.state !== 'ringing') return;
        call.state = 'active';
        clearCallRingTimeout();
        fadeOutCallRing();
        startCallTimer();
        startCallMic();
        applyCallAudioMode();
        renderSimulatedCall();
      }, 3000);
    } else {
      armCallRingTimeout();             // 来电 60 秒没人接就自动挂断
    }
    requestAnimationFrame(() => page.classList.add('fw-call--enter'));
  }

  /** 调试/自检用：读当前来电状态（真机能力是否真的拿到流，一眼可见）。 */
  window.__fwCallDebug = function () {
    return {
      open: !!(call.page && call.page.isConnected),
      mode: call.mode,
      state: call.state,
      seconds: call.seconds,
      collapsed: call.collapsed,
      mic: call.mic,
      micTracks: call.micTracks.length,
      micLive: call.micTracks.some(track => track.readyState === 'live'),
      speaker: call.speaker,
      ringing: !!call.audio,
      unread: Number(callStore(CALL_KEYS.unread)) || 0,
      storedDuration: Number(callStore(CALL_KEYS.duration)) || 0,
      ringtone: String(callStore(CALL_KEYS.ringtone) || './assets/audio/call.mp3'),
    };
  };

  // Android 返回键先挂断 / 关掉来电页，再交给下面的链路。
  (function installSimulatedCallBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      if (call.page && call.page.isConnected) {
        // 「修改通话时长」弹窗开着时，返回键先关弹窗。
        const modal = call.page.querySelector('.fw-chat-pay-input-overlay');
        if (modal) { modal.remove(); return true; }
        closeSimulatedCall();
        return true;
      }
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  /* ================= 转账收款 / 退还 (2026-10-11) =================
     A pending transfer opens a full screen detail page. Which page depends on who owns
     the message right now, because a transfer can be swiped across to the other speaker:
     my own transfer shows 待<好友>收款, the friend's shows 待你收款 with 收款 / 退还. */
  function settleTransfer(message, friend, state) {
    if (!message || message.status) return;
    const target = friend || chatPayFriend();
    target.messages = Array.isArray(target.messages) ? target.messages : [];
    const list = target.messages;
    const mine = messageSide(message) !== 'incoming';
    message.status = state;
    const label = String(target.remark || target.name || '好友');
    const mirror = {
      type: 'transfer',
      amount: Number(message.amount) || 0,
      note: String(message.note || ''),
      status: state,
      side: mine ? 'incoming' : 'outgoing',
      friendName: String(message.friendName || label),
      // Same stamp as the original so the pair renders under a single time label.
      createdAt: Number(message.createdAt) || Date.now(),
    };
    const index = list.indexOf(message);
    if (index >= 0) list.splice(index + 1, 0, mirror); else list.push(mirror);
    try { syncTransferBill(message); } catch (_) { /* wallet bills are optional */ }
    saveState();
    queueApply();
  }

  // The waiting clock: a hand pointing straight up plus one at 4 o'clock, matching the
  // glyph captured on the reference pages. Solid for my own transfer, a ring for the
  // friend's (the one I can still collect).
  const transferClockSvg = solid => '<svg viewBox="0 0 24 24" aria-hidden="true">'
    + '<circle cx="12" cy="12" r="' + (solid ? 12 : 11.26) + '"'
    + (solid ? ' fill="#50acf9"' : ' fill="none" stroke="#50acf9" stroke-width="1.49"') + '/>'
    + '<path d="M12 5.6V12l3.5 3.5"/></svg>';

  function transferAmountText(amount) {
    return (Number(amount) || 0).toFixed(2);
  }

  function transferStampText(message) {
    const raw = Number(message && message.createdAt) || Date.now();
    const at = new Date(raw);
    const pad = value => String(value).padStart(2, '0');
    return at.getFullYear() + '年' + (at.getMonth() + 1) + '月' + at.getDate() + '日 '
      + pad(at.getHours()) + ':' + pad(at.getMinutes()) + ':' + pad(at.getSeconds());
  }

  function transferNameText(friend) {
    const label = friendLabel(friend);
    return label || '好友';
  }

  function closeTransferDetail(then) {
    const page = document.querySelector('.fw-tf-detail');
    if (!page || page.dataset.closing === '1') return;
    page.dataset.closing = '1';
    page.classList.add('fw-tf-detail--exit');
    const finish = () => { if (page.isConnected) page.remove(); if (typeof then === 'function') then(); };
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 200);
  }

  function openTransferDetail(message, friend) {
    document.querySelector('.fw-tf-detail')?.remove();
    const target = friend || chatPayFriend();
    // The side is read live: swiping the bubble across hands the transfer to the friend,
    // which is what turns this into the "confirm and collect" page.
    const mine = messageSide(message) !== 'incoming';
    const page = document.createElement('section');
    page.className = 'fw-tf-detail';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', mine ? '转账详情' : '确认收款');
    page.innerHTML =
      '<header class="fw-tf-detail__nav">'
      + '<button type="button" class="fw-tf-detail__back" aria-label="返回"><svg viewBox="0 0 10 18" aria-hidden="true"><path d="M8.6 1.2 1.6 9l7 7.8"/></svg></button>'
      + '<span></span>'
      + '<button type="button" class="fw-tf-detail__more" aria-label="更多"><svg viewBox="0 0 28 8" aria-hidden="true"><circle cx="4" cy="4" r="1.6"/><circle cx="14" cy="4" r="1.6"/><circle cx="24" cy="4" r="1.6"/></svg></button>'
      + '</header>'
      + '<main class="fw-tf-detail__body">'
      + '<span class="fw-tf-detail__clock' + (mine ? ' fw-tf-detail__clock--solid' : '') + '">' + transferClockSvg(mine) + '</span>'
      + '<p class="fw-tf-detail__title">' + (mine ? '待' + transferNameText(target) + '收款' : '待你收款') + '</p>'
      + '<p class="fw-tf-detail__amount"><span class="fw-tf-detail__yuan">¥</span><b>' + transferAmountText(message.amount) + '</b></p>'
      + (mine
        ? '<p class="fw-tf-detail__hint">1天内对方未收款，将退还给你。<a class="fw-tf-detail__link" role="button" tabindex="0" data-detail-act="remind">提醒对方收款</a></p>'
        : '')
      + '<div class="fw-tf-detail__divider"></div>'
      + '<div class="fw-tf-detail__row"><span>转账时间</span><span class="fw-tf-detail__time">' + transferStampText(message) + '</span></div>'
      + '</main>'
      + (mine
        ? '<footer class="fw-tf-detail__foot"><button type="button" class="fw-tf-detail__link" data-detail-act="bill">账单详情</button></footer>'
        : '<footer class="fw-tf-detail__foot fw-tf-detail__foot--action">'
          + '<button type="button" class="fw-tf-detail__collect" data-detail-act="collect">收款</button>'
          + '<p class="fw-tf-detail__note">1天内未确认，将退还给对方。<a class="fw-tf-detail__link" role="button" tabindex="0" data-detail-act="refund">退还</a></p>'
          + '</footer>');
    document.body.appendChild(page);
    requestAnimationFrame(() => page.classList.add('fw-tf-detail--enter'));
    page.querySelector('.fw-tf-detail__back').addEventListener('click', () => closeTransferDetail());
    page.querySelector('.fw-tf-detail__more').addEventListener('click', () => showToast('仅作界面演示'));
    page.querySelectorAll('[data-detail-act]').forEach(node => {
      node.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        const act = node.dataset.detailAct;
        if (act === 'collect' || act === 'refund') {
          closeTransferDetail(() => {
            settleTransfer(message, target, act === 'collect' ? 'accepted' : 'returned');
            try { document.querySelector('.fw-local-conversation')?.__fwDrawMessages?.(); } catch (_) { /* chat may be closed */ }
          });
          return;
        }
        showToast(act === 'remind' ? '已提醒对方收款' : '仅作界面演示');
      });
    });
  }

  // Android back closes the detail page before the chat/pay chains below it.
  (function installTransferDetailBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      if (document.querySelector('.fw-tf-detail')) { closeTransferDetail(); return true; }
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  /* ================= 微信红包：封面开红包 / 领取结果 / 红包详情 (2026-10-12) =================
     点红包消息时先看这条消息现在归谁、红包有没有被领过：对方的、还没领过的进"開"封面，
     点開之后进领取结果页；其余的（自己发的、已经领过的）进红包详情页。 */
  function redPacketAmountText(message) {
    return (Number(message && message.amount) || 0).toFixed(2);
  }

  function redPacketBlessing(message) {
    return String(message && message.blessing || '').trim() || '恭喜发财，大吉大利';
  }

  function redPacketOwnerName(message, friend, ctx) {
    const mine = messageSide(message) !== 'incoming';
    return mine ? ((ctx && ctx.selfName) || '我') : (transferNameText(friend) || '好友');
  }

  function redPacketClockText(at) {
    const date = new Date(Number(at) || Date.now());
    const pad = value => String(value).padStart(2, '0');
    return pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  function closeRedPacketScreen(then) {
    const page = document.querySelector('.fw-hb-screen');
    if (!page || page.dataset.closing === '1') return;
    page.dataset.closing = '1';
    page.classList.add('fw-hb-screen--exit');
    const finish = () => { if (page.isConnected) page.remove(); if (typeof then === 'function') then(); };
    page.addEventListener('animationend', finish, { once: true });
    setTimeout(finish, 220);
  }

  function redPacketHead() {
    return '<header class="fw-hb-head">'
      + '<span class="fw-hb-head__curve" aria-hidden="true"></span>'
      + '<div class="fw-hb-head__bar">'
      + '<button type="button" class="fw-hb-head__back" aria-label="返回"><svg viewBox="0 0 10 18" aria-hidden="true"><path d="M8.6 1.2 1.6 9l7 7.8"/></svg></button>'
      + '<span></span>'
      + '<button type="button" class="fw-hb-head__more" aria-label="更多"><svg viewBox="0 0 28 8" aria-hidden="true"><circle cx="4" cy="4" r="1.7"/><circle cx="14" cy="4" r="1.7"/><circle cx="24" cy="4" r="1.7"/></svg></button>'
      + '</div>'
      + '</header>';
  }

  function redPacketAvatarNode(src, name, className) {
    const avatar = document.createElement('img');
    if (className) avatar.className = className;
    avatar.alt = '';
    avatar.src = src || apiAvatarFor('red-packet-' + String(name || ''));
    avatar.onerror = () => { avatar.onerror = null; avatar.src = bundledAvatarFor(name || ''); };
    return avatar;
  }

  // 红包页头像跟消息归属走：自己发的红包显示自己的头像，对方发的显示对方头像。
  function redPacketAvatarFor(ctx, mine, name, className) {
    if (!mine) return redPacketAvatarNode(ctx && ctx.friendAvatar, name, className);
    const avatar = redPacketAvatarNode((ctx && ctx.selfAvatar) || '', name, className);
    if (ctx && typeof ctx.resolveSelf === 'function') {
      try { ctx.resolveSelf(ctx.selfAvatarValue).then(src => { if (src && avatar.isConnected) avatar.src = src; }); } catch (_) { /* avatar lookup is optional */ }
    }
    return avatar;
  }

  // 封面页（图 2）：背景虚化，中间大红包，下面是金色叉。
  function mountRedPacketOpen(message, friend, ctx) {
    document.querySelector('.fw-hb-screen')?.remove();
    const owner = redPacketOwnerName(message, friend, ctx);
    const page = document.createElement('section');
    page.className = 'fw-hb-screen fw-hb-screen--open';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', owner + '的红包');
    page.innerHTML = '<div class="fw-hb-open">'
      + '<div class="fw-hb-open__card"><div class="fw-hb-open__flap"></div>'
      + '<div class="fw-hb-open__who"><span class="fw-hb-open__avatar"></span><span class="fw-hb-open__name"></span></div>'
      + '<p class="fw-hb-open__wish"></p>'
      + '<button type="button" class="fw-hb-open__button" aria-label="开">開</button>'
      + '</div>'
      + '<button type="button" class="fw-hb-open__close" aria-label="关闭"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6.6 6.6 17.4 17.4M17.4 6.6 6.6 17.4"/></svg></button>'
      + '</div>';
    document.body.appendChild(page);
    page.querySelector('.fw-hb-open__avatar').replaceWith(redPacketAvatarNode(ctx && ctx.friendAvatar, owner, 'fw-hb-open__avatar'));
    page.querySelector('.fw-hb-open__name').textContent = owner + '的红包';
    page.querySelector('.fw-hb-open__wish').textContent = redPacketBlessing(message);
    requestAnimationFrame(() => page.classList.add('fw-hb-screen--enter'));
    page.querySelector('.fw-hb-open__button').addEventListener('click', event => {
      event.stopPropagation();
      message.claim = { by: '你', owner, at: Date.now(), amount: Number(message.amount) || 0 };
      saveState();
      closeRedPacketScreen(() => {
        try { document.querySelector('.fw-local-conversation')?.__fwDrawMessages?.(); } catch (_) { /* chat may be closed */ }
        mountRedPacketResult(message, friend, owner, ctx);
      });
    });
    page.querySelector('.fw-hb-open__close').addEventListener('click', () => closeRedPacketScreen());
  }

  // 领取结果页（图 3）：红色弧形头部 + 头像/网名 + 祝福语 + 金额 + 已存入零钱 + 回复表情。
  function mountRedPacketResult(message, friend, owner, ctx) {
    const page = document.createElement('section');
    page.className = 'fw-hb-screen fw-hb-screen--result';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.innerHTML = redPacketHead()
      + '<main class="fw-hb-body">'
      + '<div class="fw-hb-who"><span class="fw-hb-who__avatar"></span><span class="fw-hb-who__name"></span></div>'
      + '<p class="fw-hb-body__wish"></p>'
      + '<p class="fw-hb-body__amount"><b></b><i>元</i></p>'
      + '<p class="fw-hb-body__tip">已存入零钱，可用于发红包<i aria-hidden="true">›</i></p>'
      + '<button type="button" class="fw-hb-reply"><i aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9.2"/><path d="M8.6 14.2a4.3 4.3 0 0 0 6.8 0"/><path d="M9.3 9.6h.02M14.7 9.6h.02"/></svg></i>回复表情到聊天</button>'
      + '</main>';
    document.body.appendChild(page);
    const minePacket = messageSide(message) !== 'incoming';
    page.querySelector('.fw-hb-who__avatar').replaceWith(redPacketAvatarFor(ctx, minePacket, owner, 'fw-hb-who__avatar'));
    page.querySelector('.fw-hb-who__name').textContent = owner + '的红包';
    page.querySelector('.fw-hb-body__wish').textContent = redPacketBlessing(message);
    page.querySelector('.fw-hb-body__amount b').textContent = redPacketAmountText(message);
    requestAnimationFrame(() => page.classList.add('fw-hb-screen--enter'));
    page.querySelector('.fw-hb-head__back').addEventListener('click', () => closeRedPacketScreen());
    page.querySelector('.fw-hb-head__more').addEventListener('click', () => showToast('仅作界面演示'));
    page.querySelector('.fw-hb-reply').addEventListener('click', () => { closeRedPacketScreen(() => showToast('已回复表情到聊天')); });
  }

  // 红包详情页（图 5）：头部同结果页，下面是"1个红包共x元"和领取记录。
  function mountRedPacketDetail(message, friend, ctx) {
    document.querySelector('.fw-hb-screen')?.remove();
    const owner = redPacketOwnerName(message, friend, ctx);
    const claim = message.claim || null;
    const page = document.createElement('section');
    page.className = 'fw-hb-screen fw-hb-screen--detail';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.innerHTML = redPacketHead()
      + '<main class="fw-hb-body">'
      + '<div class="fw-hb-who"><span class="fw-hb-who__avatar"></span><span class="fw-hb-who__name"></span></div>'
      + '<p class="fw-hb-body__wish"></p>'
      + '<p class="fw-hb-body__total"></p>'
      + '<div class="fw-hb-claims"></div>'
      + '</main>';
    document.body.appendChild(page);
    page.querySelector('.fw-hb-who__avatar').replaceWith(redPacketAvatarFor(ctx, messageSide(message) !== 'incoming', owner, 'fw-hb-who__avatar'));
    page.querySelector('.fw-hb-who__name').textContent = owner + '的红包';
    page.querySelector('.fw-hb-body__wish').textContent = redPacketBlessing(message);
    // 自己发的、还没人领时参照图是"红包金额x元，等待对方领取"，领过之后才是"1个红包共x元"。
    page.querySelector('.fw-hb-body__total').textContent = claim
      ? '1个红包共' + redPacketAmountText(message) + '元'
      : '红包金额' + redPacketAmountText(message) + '元，等待对方领取';
    if (claim) {
      const row = document.createElement('div');
      row.className = 'fw-hb-claim';
      const who = String(claim.by || '').trim() || '好友';
      const avatar = redPacketAvatarFor(ctx, who === '你', who, 'fw-hb-claim__avatar');
      row.innerHTML = '<div class="fw-hb-claim__who"><b></b><small></small></div><span class="fw-hb-claim__amount"></span>';
      row.querySelector('.fw-hb-claim__who b').textContent = who;
      row.querySelector('.fw-hb-claim__who small').textContent = redPacketClockText(claim.at);
      row.querySelector('.fw-hb-claim__amount').textContent = (Number(claim.amount) || 0).toFixed(2) + '元';
      row.prepend(avatar);
      page.querySelector('.fw-hb-claims').appendChild(row);
    } else {
      // 自己发的、还没人领：领取区留空，只在页面底部加一行退款提示（参照图 c0d477ad）。
      const refund = document.createElement('p');
      refund.className = 'fw-hb-body__refund';
      refund.textContent = '未领取的红包，将于24小时后发起退款';
      page.querySelector('.fw-hb-body').appendChild(refund);
    }
    requestAnimationFrame(() => page.classList.add('fw-hb-screen--enter'));
    page.querySelector('.fw-hb-head__back').addEventListener('click', () => closeRedPacketScreen());
    page.querySelector('.fw-hb-head__more').addEventListener('click', () => showToast('仅作界面演示'));
  }

  function openRedPacketPage(message, friend, ctx) {
    const target = friend || chatPayFriend();
    const mine = messageSide(message) !== 'incoming';
    if (!mine && !(message && message.claim)) mountRedPacketOpen(message, target, ctx);
    else mountRedPacketDetail(message, target, ctx);
  }

  // Android back closes the red packet screen before the chat/pay chains below it.
  (function installRedPacketBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      if (document.querySelector('.fw-hb-screen')) { closeRedPacketScreen(); return true; }
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  /* ================= 拍摄 → 照片预览 (2026-10-11) =================
     The composer's 拍摄 tile opens a live getUserMedia viewfinder: a short tap on the big
     round button takes a photo, holding it records video and releasing stops. Either way
     the capture lands on the edit page whose 完成 sends it into the open conversation. */
  const CAMERA_GLYPH = {
    close: '<svg viewBox="0 0 24 24"><path d="M6.4 6.4 17.6 17.6M17.6 6.4 6.4 17.6"/></svg>',
    flashOff: '<svg viewBox="0 0 24 24"><path d="M10.2 3.8h6.6l-2.4 5.6M6.4 8.9 3.6 14h5.6l-.5 6.2 4.6-6.9"/><path d="M3.4 3.4 20.6 20.6"/></svg>',
    flashOn: '<svg viewBox="0 0 24 24"><path d="M10.2 3.8h6.6l-2.4 5.6h3.2l-8.6 10.8.5-6.2H3.6z"/></svg>',
    flip: '<svg viewBox="0 0 24 24"><path d="M4.2 12a7.8 7.8 0 0 1 13.2-5.6l2.4 2.1"/><path d="M20.1 4.3v4.2h-4.2"/><path d="M19.8 12a7.8 7.8 0 0 1-13.2 5.6l-2.4-2.1"/><path d="M3.9 19.7v-4.2h4.2"/></svg>',
    pen: '<svg viewBox="0 0 24 24"><path d="M4.5 19.5l3.4-.8L19.4 7.2a1.8 1.8 0 0 0-2.6-2.6L5.3 16.1z"/></svg>',
    emoji: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.4"/><path d="M8.7 14.1a4.2 4.2 0 0 0 6.6 0M9.5 9.6h.02M14.5 9.6h.02"/></svg>',
    text: '<svg viewBox="0 0 24 24"><rect x="3.4" y="5.2" width="17.2" height="13.6" rx="1.4"/><path d="M8.6 9.2h6.8M12 9.2v6"/></svg>',
    crop: '<svg viewBox="0 0 24 24"><path d="M6.6 3.2v15.2h15.2"/><path d="M2.9 6.6h15.2v15.2"/></svg>',
    mosaic: '<svg viewBox="0 0 24 24"><rect x="3.4" y="3.4" width="17.2" height="17.2" rx="1.4"/><path d="M3.4 9.2h5.8V3.4M14.8 3.4v5.8h5.8M20.6 14.8h-5.8v5.8M9.2 20.6v-5.8H3.4"/></svg>',
    trash: '<svg viewBox="0 0 24 24"><path d="M5 7h14M9.5 7V5.2h5V7M7 7l.9 12.2h8.2L17 7M10.4 10.4v6M13.6 10.4v6"/></svg>',
    play: '<svg viewBox="0 0 24 24"><path d="M8.5 5.5v13l10-6.5z"/></svg>',
  };

  function activeConversationFriend() {
    const page = document.querySelector('.fw-local-conversation');
    if (page) {
      const friend = (Array.isArray(state.contacts) ? state.contacts : [])
        .find(item => String(item.id) === String(page.dataset.friendId));
      if (friend) return friend;
    }
    return friendFromConversationRoute() || null;
  }

  function openCameraPreview(dataUrl, kind, friend) {
    document.querySelector('.fw-camera-preview')?.remove();
    const page = document.createElement('section');
    page.className = 'fw-camera-preview';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', '预览');
    // The toolbar glyphs are cropped straight out of the reference screenshots.
    const tool = (name, file, label) => '<button type="button" data-tool="' + name + '" aria-label="' + label + '"><img src="./assets/camera-icons/' + file + '" alt=""></button>';
    // A recorded clip needs a real <video>, otherwise the data: URL never paints.
    const mediaTag = kind === 'video'
      ? '<video class="fw-camera-preview__image" playsinline controls preload="metadata"></video>'
      : '<img class="fw-camera-preview__image" alt="拍摄预览">';
    page.innerHTML =
      '<div class="fw-camera-preview__stage"><div class="fw-camera-preview__frame"><div class="fw-camera-preview__box">'
      + mediaTag
      + '<canvas class="fw-camera-preview__ink" hidden></canvas>'
      + '<span class="fw-camera-preview__play" hidden>' + CAMERA_GLYPH.play + '</span>'
      + '</div></div></div>'
      + '<div class="fw-camera-preview__top"><button type="button" class="fw-camera-preview__cancel">取消</button>'
      + '</div>'
      + '<div class="fw-camera-preview__bar">'
      + tool('pen', 'eb-pen.png', '画笔')
      + tool('emoji', 'eb-emoji.png', '表情')
      + tool('text', 'eb-text.png', '文字')
      + tool('crop', 'eb-crop.png', '裁剪')
      + tool('mosaic', 'eb-mosaic.png', '马赛克')
      + '<button type="button" class="fw-camera-preview__done" aria-label="完成"><img src="./assets/camera-icons/eb-done.png" alt=""></button>'
      + '</div>';
    document.body.appendChild(page);
    const image = page.querySelector('.fw-camera-preview__image');
    const ink = page.querySelector('.fw-camera-preview__ink');
    const stage = page.querySelector('.fw-camera-preview__stage');
    image.src = dataUrl;
    if (kind === 'video') page.querySelector('.fw-camera-preview__play').hidden = false;

    const send = output => {
      page.remove();
      const target = friend || activeConversationFriend();
      try { sendGalleryPhotos(target, [output]); } catch (_) { /* the conversation may be gone */ }
      showToast('已发送');
    };
    const close = () => page.remove();
    page.querySelector('.fw-camera-preview__cancel').onclick = close;
    page.querySelectorAll('[data-tool]').forEach(button => {
      button.onclick = () => {
        const tool = button.dataset.tool;
        if (tool === 'pen') {
          const on = !button.classList.contains('is-on');
          page.querySelectorAll('[data-tool]').forEach(item => item.classList.toggle('is-on', item === button && on));
          ink.hidden = !on;
          if (on) syncInk();
          return;
        }
        showToast(tool === 'emoji' ? '表情贴纸（本地演示）' : (tool === 'text' ? '文字（本地演示）' : (tool === 'crop' ? '裁剪（本地演示）' : '马赛克（本地演示）')));
      };
    });
    let drawing = false;
    let dirty = false;
    const syncInk = () => {
      if (!ink.width) {
        const box = image.getBoundingClientRect();
        ink.width = Math.max(1, Math.round(box.width));
        ink.height = Math.max(1, Math.round(box.height));
      }
      const ctx = ink.getContext('2d');
      ctx.lineWidth = 6;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.strokeStyle = '#ff3b30';
    };
    const point = event => {
      const box = ink.getBoundingClientRect();
      return { x: (event.clientX - box.left) * (ink.width / box.width), y: (event.clientY - box.top) * (ink.height / box.height) };
    };
    ink.addEventListener('pointerdown', event => {
      if (ink.hidden) return;
      drawing = true;
      dirty = true;
      const ctx = ink.getContext('2d');
      const at = point(event);
      ctx.beginPath();
      ctx.moveTo(at.x, at.y);
      event.preventDefault();
    });
    ink.addEventListener('pointermove', event => {
      if (!drawing) return;
      const ctx = ink.getContext('2d');
      const at = point(event);
      ctx.lineTo(at.x, at.y);
      ctx.stroke();
      event.preventDefault();
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach(type => ink.addEventListener(type, () => { drawing = false; }));

    page.querySelector('.fw-camera-preview__done').onclick = () => {
      if (kind === 'video' || !dirty) { send(dataUrl); return; }
      // Bake the doodle onto the photo so the sent image is what the user saw. The
      // preview shows the whole photo, so a plain full-image draw matches exactly.
      const canvas = document.createElement('canvas');
      canvas.width = ink.width;
      canvas.height = ink.height;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      ctx.drawImage(ink, 0, 0);
      send(canvas.toDataURL('image/jpeg', 0.92));
    };
  }

  async function openCameraCapture() {
    document.querySelector('.fw-camera')?.remove();
    const friend = activeConversationFriend();
    const page = document.createElement('section');
    page.className = 'fw-camera';
    page.setAttribute('role', 'dialog');
    page.setAttribute('aria-modal', 'true');
    page.setAttribute('aria-label', '拍摄');
    page.innerHTML =
      '<video class="fw-camera__video" autoplay playsinline muted></video>'
      + '<div class="fw-camera__top"><button type="button" class="fw-camera__close" aria-label="关闭">' + CAMERA_GLYPH.close + '</button></div>'
      + '<p class="fw-camera__hint" hidden></p>'
      + '<div class="fw-camera__bar">'
      + '<button type="button" class="fw-camera__torch" aria-label="闪光灯"><img src="./assets/camera-icons/cam-flash.png" alt=""></button>'
      + '<button type="button" class="fw-camera__shutter" aria-label="拍照"><img class="fw-camera__shutter-img" src="./assets/camera-icons/cam-shutter.png" alt=""><span class="fw-camera__rec" aria-hidden="true"></span></button>'
      + '<button type="button" class="fw-camera__flip" aria-label="翻转"><img src="./assets/camera-icons/cam-flip.png" alt=""></button>'
      + '</div>';
    document.body.appendChild(page);
    const video = page.querySelector('.fw-camera__video');
    const shutter = page.querySelector('.fw-camera__shutter');
    const hint = page.querySelector('.fw-camera__hint');
    const torch = page.querySelector('.fw-camera__torch');
    let facing = 'environment';
    let stream = null;
    let recorder = null;
    let chunks = [];
    let holdTimer = null;
    let recording = false;
    let closed = false;

    const stopStream = () => { try { stream?.getTracks().forEach(track => track.stop()); } catch (_) { /* ignore */ } stream = null; };
    const close = () => {
      closed = true;
      if (holdTimer) { clearTimeout(holdTimer); holdTimer = null; }
      try { if (recorder && recorder.state !== 'inactive') recorder.stop(); } catch (_) { /* ignore */ }
      stopStream();
      page.remove();
    };
    const start = async () => {
      stopStream();
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing }, audio: true });
      } catch (_) {
        try { stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing } }); } catch (_) { stream = null; }
      }
      if (!stream) {
        hint.hidden = false;
        hint.textContent = '无法打开相机，请检查相机权限';
        return;
      }
      hint.hidden = true;
      video.srcObject = stream;
      try { await video.play(); } catch (_) { /* autoplay is muted, so this is best effort */ }
    };
    const capturePhoto = () => {
      // 2026-10-11: capture exactly the framing the viewfinder shows, so the photo's
      // proportions match the preview area (which then fills it with no black bars).
      const vw = video.videoWidth || 720;
      const vh = video.videoHeight || 1280;
      const box = video.getBoundingClientRect();
      const targetAspect = (box.width > 0 && box.height > 0) ? (box.width / box.height) : (vw / vh);
      const srcAspect = vw / vh;
      let sw = vw;
      let sh = vh;
      let sx = 0;
      let sy = 0;
      if (srcAspect > targetAspect) { sw = vh * targetAspect; sx = (vw - sw) / 2; }
      else { sh = vw / targetAspect; sy = (vh - sh) / 2; }
      const width = Math.min(1080, Math.max(1, Math.round(sw)));
      const height = Math.max(1, Math.round(width / targetAspect));
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      canvas.getContext('2d').drawImage(video, sx, sy, sw, sh, 0, 0, width, height);
      const dataUrl = canvas.toDataURL('image/jpeg', 0.92);
      close();
      openCameraPreview(dataUrl, 'image', friend);
    };
    const startRecording = () => {
      if (!stream || typeof MediaRecorder !== 'function') { showToast('当前设备不支持录像'); return; }
      const candidates = ['video/mp4', 'video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'];
      const mime = candidates.find(type => { try { return MediaRecorder.isTypeSupported(type); } catch (_) { return false; } }) || '';
      try { recorder = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream); } catch (_) { recorder = null; }
      if (!recorder) { showToast('当前设备不支持录像'); return; }
      chunks = [];
      recorder.ondataavailable = event => { if (event.data && event.data.size) chunks.push(event.data); };
      recorder.onstop = () => {
        const blob = new Blob(chunks, { type: recorder.mimeType || 'video/webm' });
        if (!blob.size || closed) return;
        const reader = new FileReader();
        reader.onload = () => { if (!closed) openCameraPreview(String(reader.result), 'video', friend); };
        reader.readAsDataURL(blob);
      };
      recorder.start();
      recording = true;
      shutter.classList.add('is-recording');
    };
    const stopRecording = () => {
      recording = false;
      shutter.classList.remove('is-recording');
      try { if (recorder && recorder.state !== 'inactive') recorder.stop(); } catch (_) { /* ignore */ }
    };

    page.querySelector('.fw-camera__close').onclick = close;
    page.querySelector('.fw-camera__flip').onclick = async () => {
      facing = facing === 'environment' ? 'user' : 'environment';
      await start();
    };
    torch.onclick = () => {
      const track = stream?.getVideoTracks?.()[0];
      if (!track) return;
      const caps = typeof track.getCapabilities === 'function' ? track.getCapabilities() : {};
      if (!('torch' in caps)) { showToast('当前相机不支持闪光灯'); return; }
      const on = !torch.classList.contains('is-on');
      torch.classList.toggle('is-on', on);
      try { track.applyConstraints({ advanced: [{ torch: on }] }); } catch (_) { showToast('当前相机不支持闪光灯'); }
    };
    shutter.addEventListener('pointerdown', event => {
      event.preventDefault();
      if (!stream) return;
      holdTimer = setTimeout(() => { holdTimer = null; startRecording(); }, 420);
    });
    shutter.addEventListener('pointerup', event => {
      event.preventDefault();
      if (holdTimer) { clearTimeout(holdTimer); holdTimer = null; capturePhoto(); return; }
      if (recording) stopRecording();
    });
    shutter.addEventListener('pointercancel', () => {
      if (holdTimer) { clearTimeout(holdTimer); holdTimer = null; }
      if (recording) stopRecording();
    });
    await start();
  }

  // Android back walks the preview, then the viewfinder, before the chat screens.
  (function installCameraBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      const preview = document.querySelector('.fw-camera-preview');
      if (preview) { preview.remove(); return true; }
      const camera = document.querySelector('.fw-camera');
      if (camera) { camera.querySelector('.fw-camera__close')?.click(); return true; }
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  function clearPressedRow(press) {
    if (press?.row) { press.row.classList.remove('fw-chat-pressed'); press.row.style.removeProperty('background-color'); }
  }

  function startChatPress(target, x, y) {
    if (findPlusIcon(target)) return;
    const row = findRow(target);
    if (!row || popupKind) return;
    suppressNextChatGestureClick = false;
    // Touch and pointer events can both fire for one physical gesture on Android.
    if (activePress?.row === row) return;
    if (activePress?.timer) clearTimeout(activePress.timer);
    clearPressedRow(activePress);
    const press = { row, x, y, fired: false, timer: null };
    activePress = press;
    row.classList.add('fw-chat-pressed'); row.style.setProperty('background-color','#ededed','important');
    press.timer = setTimeout(() => {
      if (activePress !== press || !row.isConnected) return;
      press.fired = true;
      suppressClickUntil = Date.now() + 350;
      suppressClickRow = row;
      suppressClickPoint = { x: press.x, y: press.y };
      row.classList.remove('fw-chat-pressed');
      openContextMenu(row, press.x);
    }, HOLD_MS);
  }

  function onPointerDown(event) {
    if (event.pointerType === 'touch') return;
    if (event.button !== undefined && event.button !== 0) return;
    startChatPress(event.target, event.clientX, event.clientY);
  }

  function onTouchStart(event) {
    const touch = event.changedTouches?.[0];
    if (touch) startChatPress(event.target, touch.clientX, touch.clientY);
  }

  function moveActivePress(x, y) {
    if (!activePress) return;
    // Ignore small finger jitter but cancel when the gesture becomes a scroll.
    if (Math.hypot(x - activePress.x, y - activePress.y) > 10) {
      clearTimeout(activePress.timer);
      clearPressedRow(activePress);
      suppressNextChatGestureClick = true;
      activePress = null;
    }
  }

  function onPointerMove(event) {
    if (event.pointerType === 'touch') return;
    moveActivePress(event.clientX, event.clientY);
  }

  function onTouchMove(event) {
    const touch = event.changedTouches?.[0];
    if (touch) moveActivePress(touch.clientX, touch.clientY);
  }

  function endActivePress() {
    if (!activePress) return;
    const press = activePress;
    clearTimeout(press.timer);
    clearPressedRow(press);
    if (press.fired) {
      suppressClickUntil = Date.now() + 350;
      suppressClickRow = press.row;
      suppressClickPoint = { x: press.x, y: press.y };
    }
    activePress = null;
  }

    function activateChatOnRelease(event) {
    const press = activePress;
    if (!press || press.fired || !press.row?.isConnected || press.activated) return false;
    press.activated = true;
    const row = press.row;
    const touch = event?.changedTouches?.[0];
    const x = touch?.clientX ?? event?.clientX ?? press.x;
    const y = touch?.clientY ?? event?.clientY ?? press.y;
    clearTimeout(press.timer);
    clearPressedRow(press);
    activePress = null;
    // Handle the click in the release event itself; WebView compatibility clicks may arrive later.
    event?.preventDefault?.();
    row.dispatchEvent(new MouseEvent('click', {
      bubbles: true, cancelable: true, view: window, clientX: x, clientY: y
    }));
    suppressClickUntil = Date.now() + 700;
    suppressClickRow = row;
    suppressClickPoint = { x, y };
    return true;
  }

  function onPointerUp(event) {
    if (event?.pointerType === 'touch') { activateChatOnRelease(event); return; }
    endActivePress();
  }

  function onTouchEnd(event) {
    activateChatOnRelease(event);
  }
  function onTouchCancel() {
    // Android WebView may cancel the DOM touch sequence when its native long-press
    // recognizer takes over. Do not treat that as finger-up: a real touchend or
    // movement beyond the scroll tolerance will still finish/cancel the press.
    if (!activePress || !activePress.row.isConnected) endActivePress();
  }

  /* FW_FRIEND_PROFILE_PATCH_V1 */
  const nativeFriendCache = new Map();
  function nativeProfileToFriend(profile){
    if(!profile || profile.id == null) return null;
    const id=String(profile.id); const existing=(Array.isArray(state.contacts)?state.contacts:[]).find(x=>String(x?.id)===id);
    if(existing) return existing;
    // The address book labels a contact with its remark (备注) and keeps the nickname
    // as the secondary line, so mirror both onto the friend object. Without this a
    // tapped contact opened under its nickname even though the list showed the remark.
    const nickname=String(profile.nickname||'').trim();
    const remark=String(profile.remark||'').trim();
    const name=nickname||remark||'新朋友';
    const cached=nativeFriendCache.get(id);
    if(cached){ cached.nickname=nickname||cached.nickname||''; cached.remark=remark||cached.remark||''; cached.name=name; cached.realName=cached.nickname||name; cached.wechatId=String(profile.wechat||cached.wechatId||id); cached.region=String(profile.area||cached.region||''); cached.signature=String(profile.description||cached.signature||''); cached.avatar=String(profile.avatarInfo||cached.avatar||CONTACT_FALLBACKS[0].avatar); return cached; }
    const friend={id,name,nickname,remark,realName:nickname||name,wechatId:String(profile.wechat||id),region:String(profile.area||''),signature:String(profile.description||''),avatar:String(profile.avatarInfo||CONTACT_FALLBACKS[0].avatar),starred:!!profile.isStarred,nativeOnly:true,nativeProfileId:id}; nativeFriendCache.set(id,friend); return friend;
  }
  function getFriendById(id){return (Array.isArray(state.contacts)?state.contacts:[]).find(x=>String(x?.id)===String(id))||nativeFriendCache.get(String(id))||null;}
  function friendLabel(x){return String(x?.remark||x?.name||'').trim();}
  function closeFriendProfile(){document.querySelectorAll('.fw-friend-profile-page,.fw-friend-edit-layer,.fw-friend-avatar-picker').forEach(x=>x.remove());document.body.classList.remove('fw-friend-profile-open');}
  function updateFriendBindings(friend){
    if(!friend)return;
    const id=String(friend.id),label=friendLabel(friend),avatar=friend.avatar||CONTACT_FALLBACKS[0].avatar;
    document.querySelectorAll('.fw-local-friend-row').forEach(row=>{if(String(row.dataset.fwFriendId)!==id)return;const n=row.querySelector('strong,[data-fw-friend-name]');if(n)n.textContent=label;const img=row.querySelector('img');if(img)img.src=avatar;});
    document.querySelectorAll('.fw-local-conversation').forEach(page=>{if(String(page.dataset.friendId)!==id)return;const title=page.querySelector('.fw-local-conversation__header strong');if(title)title.textContent=label;page.querySelectorAll('.fw-chat-message__avatar:not([data-fw-self-avatar="1"])').forEach(img=>img.src=avatar);});
    document.querySelectorAll('.fw-friend-profile-page').forEach(page=>{if(String(page.dataset.fwFriendId)===id)renderFriendProfilePage(page,friend);});
    queueApply();
  }
  function syncFriendEdit(friend){
    if(friend?.nativeOnly && typeof window.__fwSyncNativeProfiles==='function') window.__fwSyncNativeProfiles([nativeProfileFromFriend(friend)]); else { saveState(); syncNativeAddressBook(); }
    updateFriendBindings(friend); window.dispatchEvent(new CustomEvent('fw:contacts-updated',{detail:{friendId:friend.id}})); setTimeout(()=>{syncNativeAddressBook();updateFriendBindings(friend);},80);
  }
  function nativeProfileForRoute(id){try{const list=JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY)||'[]');return Array.isArray(list)?list.find(p=>String(p?.id)===String(id)):null;}catch(_){return null;}}
  function handleNativeFriendRoute(){const match=String(location.hash||'').match(/^#\/wechat\/friend\/([^/?#]+)/);if(!match||document.querySelector('.fw-friend-avatar-picker'))return;const id=decodeURIComponent(match[1]);const friend=getFriendById(id)||nativeProfileToFriend(nativeProfileForRoute(id));if(!friend)return;const page=document.querySelector('.fw-friend-profile-page');if(page&&String(page.dataset.fwFriendId)===String(friend.id))return;openFriendProfile(friend);}  function editFriendField(friend,field,title){const layer=document.createElement("div");layer.className="fw-friend-edit-layer";layer.innerHTML="<div class=fw-friend-edit-dialog><header><button data-cancel>取消</button><strong>"+title+"</strong><button data-save>保存</button></header><input></div>";const input=layer.querySelector("input");input.value=field==="name"?friend.name||"":friend[field]||"";const close=()=>layer.remove();layer.querySelector("[data-cancel]").onclick=close;layer.querySelector("[data-save]").onclick=()=>{const v=input.value.trim();if(!v&&field!=="region"&&field!=="signature"){showToast(title+"不能为空");return;}if(field==="name"){friend.name=v;friend.realName="";}else friend[field]=v;syncFriendEdit(friend);updateFriendBindings(friend);close();showToast("已保存");};document.body.appendChild(layer);input.focus();input.select();}
  // 2026-10-10: the profile hero keeps the WeChat reference layout, so the nickname line
  // and the 朋友圈 thumbnail strip (rotated per friend so every profile looks lived in)
  // are painted here instead of being hard-coded in the template above.
  const PROFILE_MOMENT_IMAGES = [
    './assets/user-avatar-10-ZKj7tiz_.jpg',
    './assets/user-avatar-5-DMigR-cn.jpg',
    './assets/user-avatar-1-CKPDcH57.jpg',
    './assets/user-avatar-4-Cmbgep_s.jpg',
    './assets/user-avatar-7-C4bh6bYx.jpg',
    './assets/user-avatar-8-BysMeksY.jpg',
  ];
  function friendMomentImages(friend) {
    const seed = String(friend?.id || friend?.name || '');
    let hash = 0;
    for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) % 9973;
    return [0, 1, 2, 3].map(i => PROFILE_MOMENT_IMAGES[(hash + i) % PROFILE_MOMENT_IMAGES.length]);
  }
  // ===================== 朋友圈 / Moments =====================
  // Every friend owns { cover, posts[] } inside the local state: the profile page's
  // 朋友圈 row opens a per-friend moments page, tapping the cover picks a new
  // background from the album and a long press opens the composer.
  const MOMENT_MONTHS = ['一月','二月','三月','四月','五月','六月','七月','八月','九月','十月','十一月','十二月'];
  function getFriendMoments(friend) {
    if (!friend) return null;
    const moments = friend.moments && typeof friend.moments === 'object' ? friend.moments : (friend.moments = {});
    if (typeof moments.cover !== 'string') moments.cover = '';
    if (!Array.isArray(moments.posts)) moments.posts = [];
    return moments;
  }
  function momentCoverInfo(friend) {
    const moments = getFriendMoments(friend);
    const custom = String(moments?.cover || '').trim();
    if (custom) return { url: custom, soft: false };
    // The bundled profile already carries the WeChat 朋友圈背景图; use it before falling
    // back to the avatar so the cover matches the rest of the app's data.
    try {
      const profile = appProfileMap().get(String(friend?.id ?? ''));
      const background = String(profile?.momentsBackgroundInfo || '').trim();
      if (background) return { url: background, soft: false };
    } catch (_) { /* fall through */ }
    // No cover data anywhere: use the avatar but blur it so a portrait photo still reads
    // as a cover instead of a giant cropped face.
    return { url: String(friend?.avatar || '').trim() || CONTACT_FALLBACKS[0].avatar, soft: true };
  }
  function momentCoverSource(friend) {
    return momentCoverInfo(friend).url;
  }
  const LOCAL_COVER_FALLBACK = './assets/user-avatar-1-CKPDcH57.jpg';
  const LOCAL_PHOTO_POOL = [
    './assets/user-avatar-1-CKPDcH57.jpg',
    './assets/user-avatar-4-Cmbgep_s.jpg',
    './assets/user-avatar-5-DMigR-cn.jpg',
    './assets/user-avatar-7-C4bh6bYx.jpg',
    './assets/user-avatar-10-ZKj7tiz_.jpg',
    './assets/user-avatar-8-BysMeksY.jpg',
  ];
  function localPhotoFor(value) {
    const text = String(value || '');
    let hash = 0;
    for (let i = 0; i < text.length; i += 1) hash = (hash * 31 + text.charCodeAt(i)) % 9973;
    return LOCAL_PHOTO_POOL[hash % LOCAL_PHOTO_POOL.length];
  }
  // The demo CDN (cdn-fakeworld.azureedge.net) does not resolve, so every remote image has to
  // fall back gracefully. For the cover that means painting a crisp sky instead of blowing up
  // a low-resolution avatar with a 16px blur, which is what made the page look washed out.
  function applyCoverImage(element, url, soft) {
    if (!element) return;
    const value = String(url || '').trim();
    const image = element.querySelector('.fw-moments__cover-img');
    const useDefault = () => {
      element.classList.add('is-default');
      if (image) image.removeAttribute('src');
    };
    const paint = src => {
      const next = String(src || '').trim();
      if (!next) { useDefault(); return; }
      if (!image) {
        element.style.backgroundImage = 'url("' + next.replace(/"/g, '\\"') + '")';
        return;
      }
      image.onerror = () => { image.onerror = null; useDefault(); };
      image.src = next;
      // 解析结果仍可能是会挂起的远程地址，1.6s 没画出来就回默认封面。
      setTimeout(() => {
        if (image.isConnected && !image.naturalWidth) useDefault();
      }, 1600);
    };
    if (!value) { useDefault(); return; }
    element.classList.remove('is-default');
    // 2026-10-19: fakeworld 的封面图在真机上会一直挂起、不触发 onerror，只剩灰渐变。
    // 和「我」页/聊天头像一样，先交给应用的 __fwResolveProfileAvatar 解析成本地图。
    if (/^https?:\/\//i.test(value) && typeof window.__fwResolveProfileAvatar === 'function') {
      element.classList.add('is-default');
      try {
        Promise.resolve(window.__fwResolveProfileAvatar(value)).then(resolved => {
          if (!element.isConnected) return;
          const src = String(resolved || '').trim();
          if (src) { element.classList.remove('is-default'); paint(src); }
          else useDefault();
        }).catch(() => { if (element.isConnected) useDefault(); });
      } catch (_) { useDefault(); }
      return;
    }
    paint(value);
  }
  function resolveTimelineAvatar(url) {
    const value = String(url || '').trim();
    if (!value) return CONTACT_FALLBACKS[0].avatar;
    if (/^(data:|\.\/|https:\/\/fake-world\.local)/.test(value)) return value;
    return value;
  }
  function momentDateParts(post) {
    const date = new Date(Number(post?.createdAt) || Date.now());
    return {
      month: MOMENT_MONTHS[date.getMonth()] || '',
      day: String(date.getDate()).padStart(2, '0'),
    };
  }
  function momentImages(friend) {
    const moments = getFriendMoments(friend);
    const out = [];
    (moments?.posts || []).forEach(post => {
      (Array.isArray(post?.images) ? post.images : []).forEach(src => {
        if (src && out.length < 4) out.push(src);
      });
    });
    return out;
  }
  // Store a JPEG copy so a handful of moments cannot blow the localStorage quota, but keep it
  // big enough for the full-screen viewer: the screen is 1080px wide, so anything smaller than
  // that gets upscaled and looks soft (the old 720px/0.66 copies were the main cause of the
  // "blurry photos" complaint).
  function shrinkMomentImage(dataUrl, maxSize, quality) {
    return new Promise(resolve => {
      const source = String(dataUrl || '');
      if (!source.startsWith('data:image/')) { resolve(source); return; }
      const image = new Image();
      image.onload = () => {
        try {
          const scale = Math.min(1, maxSize / Math.max(image.width || 1, image.height || 1));
          const canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round((image.width || 1) * scale));
          canvas.height = Math.max(1, Math.round((image.height || 1) * scale));
          canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
          resolve(canvas.toDataURL('image/jpeg', quality));
        } catch (_) { resolve(source); }
      };
      image.onerror = () => resolve(source);
      image.src = source;
    });
  }
  // 2026-10-10: WeChat's photo viewer - a black page with ‹ and a n/total counter, the photo
  // scaled to fill at least one screen axis, and a swipe to step through a post's photos.
  function openMomentImagePreview(src, gallery, index) {
    if (!src) return;
    document.querySelector('.fw-moment-preview')?.remove();
    const images = Array.isArray(gallery) && gallery.length ? gallery.slice() : [src];
    let current = Math.min(Math.max(0, Number(index) || 0), images.length - 1);
    if (images[current] !== src) {
      const found = images.indexOf(src);
      if (found >= 0) current = found;
    }
    const layer = document.createElement('div');
    layer.className = 'fw-moment-preview';
    // The action icons are cut straight out of the reference screenshot the user supplied.
    layer.innerHTML = '<header class="fw-moment-preview__bar"><button type="button" class="fw-moment-preview__back" aria-label="返回">‹</button><span class="fw-moment-preview__count"></span></header><img alt="图片">'
      + '<div class="fw-moment-preview__actions">'
      + '<button type="button" data-act="forward" aria-label="转发"><img src="./assets/fw-viewer-action-forward.png" alt=""></button>'
      + '<button type="button" data-act="save" aria-label="保存到相册"><img src="./assets/fw-viewer-action-save.png" alt=""></button>'
      + '<button type="button" data-act="grid" aria-label="全部图片"><img src="./assets/fw-viewer-action-grid.png" alt=""></button>'
      + '<button type="button" data-act="more" aria-label="更多"><img src="./assets/fw-viewer-action-more.png" alt=""></button>'
      + '</div>';
    const image = layer.querySelector('img');
    const counter = layer.querySelector('.fw-moment-preview__count');
    const paint = () => {
      const source = images[current];
      counter.textContent = images.length > 1 ? (current + 1) + '/' + images.length : '';
      counter.hidden = images.length <= 1;
      // Remote demo photos cannot load offline; keep the viewer off the broken-image glyph.
      image.onerror = () => {
        image.onerror = null;
        const fallback = localPhotoFor(source);
        if (image.src !== fallback) image.src = fallback;
      };
      image.src = source;
    };
    paint();
    const close = () => layer.remove();
    layer.querySelector('.fw-moment-preview__back').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      close();
    });
    // 2026-10-10: WeChat's viewer action row (reference 图片2): 转发 / 保存 / 九宫格 / 更多.
    const actions = layer.querySelector('.fw-moment-preview__actions');
    if (images.length < 2) actions.querySelector('[data-act="grid"]').remove();
    // Use the bitmap that is actually on screen: a demo photo may have swapped its unreachable
    // CDN URL for the bundled fallback, and that is the file worth saving/forwarding.
    const currentSource = () => image.src || images[current];
    // Bundled/remote photos are plain URLs, so turn them into a data URL before handing the
    // bytes to the native album writer.
    const asDataUrl = async source => {
      const value = String(source || '');
      if (/^data:/i.test(value)) return value;
      try {
        const blob = await (await fetch(value)).blob();
        return await new Promise(resolve => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result || ''));
          reader.onerror = () => resolve('');
          reader.readAsDataURL(blob);
        });
      } catch (_) { return ''; }
    };
    // "保存到手机": write the photo into the album, asking the system for the album permission
    // first when the native side reports it is missing, then retry once.
    const saveImage = async source => {
      const data = await asDataUrl(source);
      if (!data) { showToast('保存失败，请稍后再试'); return; }
      const attempt = () => {
        try { return String(window.DoutuApp?.saveImageToGallery?.(data) || ''); } catch (_) { return ''; }
      };
      let result = attempt();
      if (result === 'denied') {
        const granted = await new Promise(resolve => {
          const timer = setTimeout(() => resolve(false), 30000);
          window.__fwSavePermissionResult = ok => { clearTimeout(timer); resolve(!!ok); };
          try { window.DoutuApp?.requestSaveImagePermission?.(); } catch (_) { clearTimeout(timer); resolve(false); }
        });
        if (granted) result = attempt();
      }
      if (result === 'denied') showToast('没有相册权限，无法保存');
      else showToast(result ? '已保存到相册' : '保存失败，请稍后再试');
    };
    const forwardImage = source => {
      const local = (Array.isArray(state.contacts) ? state.contacts : [])
        .filter(friend => friend?.id && (friend.remark || friend.name));
      if (!local.length) { showToast('暂无可转发的聊天'); return; }
      openTimelineContactPicker({
        title: '转发给朋友',
        hint: '选择要把这张图片发送到哪个聊天',
        selected: [],
        multi: false,
        onConfirm: names => {
          const name = String(names[0] || '').trim();
          const friend = local.find(entry => String(entry.remark || entry.name) === name);
          if (!friend) { showToast('该好友暂不支持转发'); return; }
          sendGalleryPhotos(friend, [source]);
          showToast('已转发给 ' + name);
        },
      });
    };
    const showGrid = () => {
      if (images.length < 2) return;
      const sheet = document.createElement('div');
      sheet.className = 'fw-moment-preview__grid';
      sheet.innerHTML = '<div class="fw-moment-preview__grid-body"></div>';
      const body = sheet.querySelector('.fw-moment-preview__grid-body');
      images.forEach((source, i) => {
        const cell = document.createElement('button');
        cell.type = 'button';
        const thumb = document.createElement('img');
        thumb.alt = '';
        thumb.src = source;
        thumb.onerror = () => { thumb.onerror = null; thumb.src = localPhotoFor(source); };
        if (i === current) cell.classList.add('is-current');
        cell.appendChild(thumb);
        cell.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          current = i;
          sheet.remove();
          paint();
        });
        body.appendChild(cell);
      });
      sheet.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        if (event.target === sheet) sheet.remove();
      });
      layer.appendChild(sheet);
    };
    const showMore = () => {
      const source = currentSource();
      const sheet = document.createElement('div');
      sheet.className = 'fw-moment-preview__sheet';
      sheet.innerHTML = '<div class="fw-moment-preview__sheet-body">'
        + '<button type="button" data-more="forward">转发给朋友</button>'
        + '<button type="button" data-more="save">保存到相册</button>'
        + '<button type="button" data-more="cancel">取消</button></div>';
      sheet.querySelector('[data-more="forward"]').addEventListener('click', event => { event.stopPropagation(); sheet.remove(); forwardImage(source); });
      sheet.querySelector('[data-more="save"]').addEventListener('click', event => { event.stopPropagation(); sheet.remove(); saveImage(source); });
      sheet.querySelector('[data-more="cancel"]').addEventListener('click', event => { event.stopPropagation(); sheet.remove(); });
      sheet.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); if (event.target === sheet) sheet.remove(); });
      layer.appendChild(sheet);
    };
    actions.querySelector('[data-act="forward"]').addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); forwardImage(currentSource()); });
    actions.querySelector('[data-act="save"]').addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); saveImage(currentSource()); });
    actions.querySelector('[data-act="grid"]')?.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); showGrid(); });
    actions.querySelector('[data-act="more"]').addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); showMore(); });
    let startX = 0, startY = 0, dragged = false;
    layer.addEventListener('touchstart', event => {
      if (event.touches.length !== 1) return;
      startX = event.touches[0].clientX;
      startY = event.touches[0].clientY;
      dragged = false;
    }, { passive: true, capture: true });
    layer.addEventListener('touchend', event => {
      if (images.length < 2 || !event.changedTouches.length) return;
      const dx = event.changedTouches[0].clientX - startX;
      const dy = event.changedTouches[0].clientY - startY;
      if (Math.abs(dx) < 60 || Math.abs(dx) < Math.abs(dy) * 1.4) return;
      dragged = true;
      current = (current + (dx < 0 ? 1 : -1) + images.length) % images.length;
      paint();
    }, { passive: true, capture: true });
    layer.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      if (dragged) { dragged = false; return; }
      close();
    });
    ['pointerdown', 'pointerup', 'pointermove', 'touchstart', 'touchend'].forEach(type => {
      layer.addEventListener(type, event => { event.stopPropagation(); }, true);
    });
    document.body.appendChild(layer);
  }
  function renderMomentsList(host, friend) {
    if (!host) return;
    host.replaceChildren();
    const posts = (getFriendMoments(friend)?.posts || []).slice()
      .sort((a, b) => (Number(b?.createdAt) || 0) - (Number(a?.createdAt) || 0));
    if (!posts.length) {
      const empty = document.createElement('div');
      empty.className = 'fw-moments__empty';
      empty.textContent = '—— 朋友仅展示最近一个月的朋友圈 ——';
      host.appendChild(empty);
      return;
    }
    posts.forEach(post => {
      const row = document.createElement('article');
      row.className = 'fw-moment';
      row.innerHTML = '<div class="fw-moment__date"><em></em><strong></strong></div><div class="fw-moment__body"><div class="fw-moment__media"></div><p class="fw-moment__text"></p><div class="fw-moment__meta"></div></div>';
      const date = momentDateParts(post);
      row.querySelector('.fw-moment__date em').textContent = date.month;
      row.querySelector('.fw-moment__date strong').textContent = date.day;
      const media = row.querySelector('.fw-moment__media');
      (Array.isArray(post?.images) ? post.images : []).forEach(src => {
        const img = document.createElement('img');
        img.src = src;
        img.alt = '图片';
        img.onerror = () => { img.onerror = null; img.src = localPhotoFor(src); };
        img.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          event.stopImmediatePropagation();
          openMomentImagePreview(img.src || src, post.images);
        });
        ['pointerdown', 'pointerup', 'touchstart', 'touchend'].forEach(type => {
          img.addEventListener(type, event => { event.stopPropagation(); }, true);
        });
        media.appendChild(img);
      });
      if (media.children.length) media.dataset.count = String(media.children.length);
      if (!media.children.length) media.remove();
      const text = row.querySelector('.fw-moment__text');
      text.textContent = String(post?.text || '');
      if (!text.textContent) text.remove();
      const meta = row.querySelector('.fw-moment__meta');
      const place = String(post?.location || '').trim();
      if (place) { const span = document.createElement('span'); span.className = 'fw-moment__location'; span.textContent = place; meta.appendChild(span); }
      const visibility = String(post?.visibility || '').trim();
      if (visibility && visibility !== '公开') { const span = document.createElement('span'); span.className = 'fw-moment__visibility'; span.textContent = visibility; meta.appendChild(span); }
      if (!meta.children.length) meta.remove();
      host.appendChild(row);
    });
  }
  function closeFriendMoments() {
    document.querySelectorAll('.fw-moments-page').forEach(node => node.remove());
  }
  function refreshOpenMoments(friend) {
    const page = document.querySelector('.fw-moments-page');
    if (!page) return;
    const coverInfo = momentCoverInfo(friend);
    applyCoverImage(page.querySelector('.fw-moments__cover'), coverInfo.url, coverInfo.soft);
    renderMomentsList(page.querySelector('.fw-moments__list'), friend);
  }
  function openFriendMoments(friend) {
    if (!friend) return;
    document.querySelector('.fw-moments-page')?.remove();
    const label = friendLabel(friend) || friend.name || '';
    const avatar = getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar);
    const page = document.createElement('section');
    page.className = 'fw-moments-page';
    page.dataset.fwFriendId = String(friend?.id ?? '');
    page.innerHTML = '<header class="fw-moments__hero"><div class="fw-moments__cover"><img class="fw-moments__cover-img" alt=""></div><button type="button" class="fw-moments__back" aria-label="返回">‹</button><div class="fw-moments__identity"><span class="fw-moments__name"></span><img class="fw-moments__avatar" alt=""></div></header><main class="fw-moments__body"><p class="fw-moments__signature"></p><div class="fw-moments__list"></div></main>';
    page.querySelector('.fw-moments__name').textContent = label;
    const signatureEl = page.querySelector('.fw-moments__signature');
    const nativeProfile = (() => {
      try { return appProfileMap().get(String(friend?.id ?? '')) || null; } catch (_) { return null; }
    })();
    const signature = String(friend.signature || nativeProfile?.signature || '').trim();
    signatureEl.textContent = signature;
    const avatarEl = page.querySelector('.fw-moments__avatar');
    avatarEl.src = avatar;
    avatarEl.alt = label;
    applyResolvedAvatar(avatarEl, friend.avatar || friend.avatarInfo);
    const coverInfo = momentCoverInfo(friend);
    applyCoverImage(page.querySelector('.fw-moments__cover'), coverInfo.url, coverInfo.soft);
    renderMomentsList(page.querySelector('.fw-moments__list'), friend);
    page.querySelector('.fw-moments__back').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      closeFriendMoments();
    });
    const cover = page.querySelector('.fw-moments__cover');
    // The tap that opened this page (on the profile's 朋友圈 row) must not fall through
    // onto the freshly mounted cover and pop the album picker.
    const openedAt = Date.now();
    // Tapping the avatar (or the name beside it) opens the moments composer, which is the
    // same 编辑朋友圈 page the cover long press opens.
    const identity = page.querySelector('.fw-moments__identity');
    if (identity) {
      identity.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        if (Date.now() - openedAt < 450) return;
        openMomentsComposer(friend);
      });
    }
    let timer = null;
    let longPressed = false;
    let startX = 0;
    let startY = 0;
    const cancel = () => { if (timer) { clearTimeout(timer); timer = null; } };
    cover.style.touchAction = 'none';
    cover.addEventListener('pointerdown', event => {
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      longPressed = false;
      startX = event.clientX;
      startY = event.clientY;
      cancel();
      timer = setTimeout(() => { timer = null; longPressed = true; openMomentsComposer(friend); }, HOLD_MS);
    });
    cover.addEventListener('pointermove', event => {
      if (Math.abs(event.clientX - startX) > 12 || Math.abs(event.clientY - startY) > 12) cancel();
    });
    cover.addEventListener('pointerup', event => {
      cancel();
      if (longPressed) { longPressed = false; return; }
      event.preventDefault();
      if (Date.now() - openedAt < 450) return;
      // Tap the cover to open the full-screen cover viewer; 换封面 inside it picks a new
      // background from the album (matches the WeChat timeline behaviour).
      openCoverViewer(() => momentCoverSource(friend), url => {
        const moments = getFriendMoments(friend);
        moments.cover = url;
        saveState();
        refreshOpenMoments(friend);
        showToast('朋友圈封面已更新');
      });
    });
    ['pointercancel', 'pointerleave'].forEach(type => cover.addEventListener(type, cancel));
    document.body.appendChild(page);
    syncChatSystemBars();
  }
  function openMomentsCoverPicker(friend) {
    openChatGallery(null, async photos => {
      const source = photos?.[0];
      if (!source) return;
      const moments = getFriendMoments(friend);
      moments.cover = await shrinkMomentImage(source, 1600, 0.86);
      saveState();
      refreshOpenMoments(friend);
      showToast('朋友圈背景已更新');
    }, { actionLabel: '设为背景' });
  }
  function openMomentsComposer(friend, options) {
    if (!friend) return;
    document.querySelector('.fw-moments-composer')?.remove();
    const editing = options?.post && typeof options.post === 'object' ? options.post : null;
    const draft = {
      text: editing ? String(editing.text || '') : '',
      images: editing && Array.isArray(editing.images) ? editing.images.slice() : [],
      location: editing ? String(editing.location || '') : '',
      visibility: editing ? String(editing.visibility || '公开') : '公开',
      remind: editing ? String(editing.remind || '') : '',
      createdAt: editing ? (Number(editing.createdAt) || Date.now()) : Date.now(),
    };
    const layer = document.createElement('section');
    layer.className = 'fw-moments-composer';
    layer.innerHTML = [
      '<header class="fw-moments-composer__header">',
      '<button type="button" data-cancel>取消</button>',
      '<button type="button" class="fw-moments-composer__publish" data-publish disabled>' + (editing ? '保存' : '发表') + '</button>',
      '</header>',
      '<textarea class="fw-moments-composer__text" placeholder="这一刻的想法…"></textarea>',
      '<div class="fw-moments-composer__grid"></div>',
      '<div class="fw-moments-composer__rows">',
      '<button type="button" class="fw-moments-composer__row" data-row="location"><i><svg viewBox="0 0 24 24"><path d="M12 21c4-4.4 7-8.1 7-11a7 7 0 1 0-14 0c0 2.9 3 6.6 7 11Z"/><circle cx="12" cy="10" r="2.6"/></svg></i><span>所在位置</span><b data-value></b><em>›</em></button>',
      '<button type="button" class="fw-moments-composer__row" data-row="remind"><i><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="4"/><path d="M16 8v4.5a3.5 3.5 0 0 0 7 0V12a11 11 0 1 0-4.4 8.8"/></svg></i><span>提醒谁看</span><b data-value></b><em>›</em></button>',
      '<button type="button" class="fw-moments-composer__row" data-row="visibility"><i><svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4.5 21c0-3.9 3.4-6.8 7.5-6.8s7.5 2.9 7.5 6.8"/></svg></i><span>谁可以看</span><b data-value>公开</b><em>›</em></button>',
      '<button type="button" class="fw-moments-composer__row" data-row="when"><i><svg viewBox="0 0 24 24"><rect x="4" y="5.5" width="16" height="14.5" rx="2.5"/><path d="M4 10h16M9 3.5v4M15 3.5v4"/></svg></i><span>日期</span><b data-value></b><em>›</em></button>',
      '</div>',
      // Reference 图片1: a grey "上次分组: 何安富, 张学汉, …" line sits under 谁可以看.
      '<div class="fw-moments-composer__hint" data-group-hint hidden></div>',
      '<div class="fw-moments-composer__hint-rule" data-group-rule hidden></div>',
    ].join('');
    // WeChat prints the group used last time; reuse the app's own contact list for it.
    const groupHint = layer.querySelector('[data-group-hint]');
    const groupRule = layer.querySelector('[data-group-rule]');
    const groupNames = timelineContactChoices().map(choice => String(choice.name || '').trim()).filter(Boolean);
    if (groupNames.length) {
      const shown = groupNames.slice(0, 8);
      groupHint.textContent = '上次分组: ' + shown.join('，') + (groupNames.length > shown.length ? '…' : '');
      groupHint.hidden = false;
      groupRule.hidden = false;
    }
    const grid = layer.querySelector('.fw-moments-composer__grid');
    const publish = layer.querySelector('[data-publish]');
    const textArea = layer.querySelector('.fw-moments-composer__text');
    const renderGrid = () => {
      grid.replaceChildren();
      draft.images.forEach((src, index) => {
        const tile = document.createElement('div');
        tile.className = 'fw-moments-composer__tile';
        const img = document.createElement('img');
        img.src = src;
        img.alt = '图片';
        const remove = document.createElement('button');
        remove.type = 'button';
        remove.className = 'fw-moments-composer__remove';
        remove.textContent = '×';
        remove.addEventListener('click', event => { event.stopPropagation(); draft.images.splice(index, 1); renderGrid(); });
        tile.append(img, remove);
        grid.appendChild(tile);
      });
      if (draft.images.length < 9) {
        const add = document.createElement('button');
        add.type = 'button';
        add.className = 'fw-moments-composer__add';
        add.setAttribute('aria-label', '添加图片');
        add.innerHTML = '<svg viewBox="0 0 36 36" aria-hidden="true"><path d="M18 6v24M6 18h24"/></svg>';
        add.addEventListener('click', () => {
          openChatGallery(null, async photos => {
            for (const photo of photos.slice(0, 9 - draft.images.length)) {
              draft.images.push(await shrinkMomentImage(photo, 1280, 0.82));
            }
            renderGrid();
          }, { actionLabel: '添加' });
        });
        grid.appendChild(add);
      }
      const hasContent = !!draft.text.trim() || draft.images.length > 0;
      publish.disabled = !hasContent;
      publish.classList.toggle('is-enabled', hasContent);
    };
    const syncRow = row => {
      const value = row.dataset.row === 'location' ? draft.location
        : row.dataset.row === 'remind' ? draft.remind
        : row.dataset.row === 'when' ? momentDateTimeLabel(draft.createdAt)
        : draft.visibility;
      row.querySelector('b').textContent = value || '';
    };
    layer.querySelectorAll('.fw-moments-composer__row').forEach(row => {
      row.addEventListener('click', () => {
        const kind = row.dataset.row;
        if (kind === 'visibility') {
          makeDialog('谁可以看', '选择这条朋友圈的可见范围', '', [
            { label: '取消', onClick: closeOverlay },
            { label: '公开', onClick: () => { draft.visibility = '公开'; syncRow(row); closeOverlay(); } },
            { label: '私密', onClick: () => { draft.visibility = '私密'; syncRow(row); closeOverlay(); } },
            { label: '部分可见', onClick: () => { draft.visibility = '部分可见'; syncRow(row); closeOverlay(); } },
          ]);
          return;
        }
        if (kind === 'when') {
          const { dialog } = makeDialog('日期', '选择这条朋友圈的日期和时间', '<input type="date" class="fw-textarea fw-moments-date"><input type="time" class="fw-textarea fw-moments-time" style="margin-top:10px">', [
            { label: '取消', onClick: closeOverlay },
            { label: '确定', primary: true, onClick: () => {
              const date = dialog.querySelector('.fw-moments-date').value;
              const time = dialog.querySelector('.fw-moments-time').value || '00:00';
              const stamp = Date.parse(date + 'T' + time);
              if (!Number.isFinite(stamp)) { showToast('请选择日期'); return; }
              draft.createdAt = stamp;
              syncRow(row);
              closeOverlay();
            } },
          ]);
          const parts = momentDateInputParts(draft.createdAt);
          dialog.querySelector('.fw-moments-date').value = parts.date;
          dialog.querySelector('.fw-moments-time').value = parts.time;
          return;
        }
        const title = kind === 'location' ? '所在位置' : '提醒谁看';
        const { dialog } = makeDialog(title, kind === 'location' ? '输入这条朋友圈的位置' : '输入要提醒的好友昵称', '<input class="fw-textarea fw-moments-input" placeholder="' + (kind === 'location' ? '例如：广东 深圳' : '例如：阿宁') + '">', [
          { label: '取消', onClick: closeOverlay },
          { label: '确定', primary: true, onClick: () => {
            const value = dialog.querySelector('.fw-moments-input').value.trim();
            if (kind === 'location') draft.location = value; else draft.remind = value;
            syncRow(row);
            closeOverlay();
          } },
        ]);
        const input = dialog.querySelector('.fw-moments-input');
        input.value = kind === 'location' ? draft.location : (draft.remind || '');
        setTimeout(() => { input.focus(); input.select(); }, 60);
      });
    });
    textArea.addEventListener('input', () => { draft.text = textArea.value; renderGrid(); });
    layer.querySelector('[data-cancel]').addEventListener('click', () => layer.remove());
    publish.addEventListener('click', () => {
      if (publish.disabled) return;
      const moments = getFriendMoments(friend);
      const payload = {
        text: draft.text.trim(),
        images: draft.images.slice(),
        location: draft.location,
        remind: draft.remind,
        visibility: draft.visibility,
        createdAt: Number(draft.createdAt) || Date.now(),
      };
      if (editing) {
        Object.assign(editing, payload);
      } else {
        moments.posts.push(Object.assign({ id: 'moment-' + Date.now() }, payload));
      }
      if (!saveState()) {
        if (!editing) moments.posts.pop();
        showToast('保存失败，请检查设备存储空间');
        return;
      }
      layer.remove();
      refreshOpenMoments(friend);
      const feedHost = document.querySelector('.fw-moments-feed__list');
      if (feedHost) renderMomentsTimeline(feedHost);
      showToast(editing ? '已保存' : '已发表');
    });
    const initialLocation = layer.querySelector('[data-row="location"]');
    const initialRemind = layer.querySelector('[data-row="remind"]');
    const initialVisibility = layer.querySelector('[data-row="visibility"]');
    const initialWhen = layer.querySelector('[data-row="when"]');
    [initialLocation, initialRemind, initialVisibility, initialWhen].forEach(row => { if (row) syncRow(row); });
    if (editing) textArea.value = String(editing.text || '');
    renderGrid();
    document.body.appendChild(layer);
    // Match the reference composer: no caret (and no selection handle) until the user
    // taps the text field.
    const openedAt = Date.now();
    layer.addEventListener('click', event => {
      // The release of the long press lands on the newly mounted composer and used to
      // focus the text field (and raise the keyboard). Swallow that first click.
      if (Date.now() - openedAt < 420) {
        event.preventDefault();
        event.stopPropagation();
      }
    }, true);
    requestAnimationFrame(() => { try { textArea.blur(); } catch (_) { /* ignore */ } });
    setTimeout(() => { try { textArea.blur(); } catch (_) { /* ignore */ } }, 260);
  }
  // ===================== 发现 → 朋友圈 (merged timeline) =====================
  // Everything published from a friend's moments page lands in the per-friend state, so
  // the discover timeline simply merges every friend's posts by time.
  function momentRelativeTime(value) {
    const ts = Number(value) || 0;
    if (!ts) return '刚刚';
    const diff = Math.max(0, Date.now() - ts);
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
    if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
    const date = new Date(ts);
    return (date.getMonth() + 1) + '月' + date.getDate() + '日';
  }
  function momentDateInputParts(value) {
    const date = new Date(Number(value) || Date.now());
    const pad = number => String(number).padStart(2, '0');
    return {
      date: date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()),
      time: pad(date.getHours()) + ':' + pad(date.getMinutes()),
    };
  }
  function momentDateTimeLabel(value) {
    const parts = momentDateInputParts(value);
    return parts.date + ' ' + parts.time;
  }
  function collectTimelinePosts() {
    const out = [];
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => {
      const posts = friend?.moments && Array.isArray(friend.moments.posts) ? friend.moments.posts : [];
      posts.forEach(post => out.push({ friend, post }));
    });
    return out.sort((a, b) => (Number(b?.post?.createdAt) || 0) - (Number(a?.post?.createdAt) || 0));
  }
  // The bundled app ships its own demo feed list (the `feedList` atom); show that data
  // together with everything published from a friend's moments page so the timeline is
  // never empty and both sources stay in sync.
  const BUILTIN_FEEDS = [
    { id: 'seed-1', userId: '1', minutesAgo: 13 * 60, text: '你好', images: [] },
    {
      id: 'seed-2', userId: '2', minutesAgo: 10, text: '我是星之卡比',
      images: ['https://cdn-fakeworld.azureedge.net/fakeworld/pnqxld.jpg', 'https://cdn-fakeworld.azureedge.net/fakeworld/pnr4ub.jpg'],
      comments: [{ fromUserId: '1', text: '666' }],
    },
    {
      id: 'seed-3', userId: '4', minutesAgo: 0.5, text: '我是路易吉',
      images: ['https://cdn-fakeworld.azureedge.net/fakeworld/pnr1bd.jpg'], likeUserIds: ['1'],
    },
    {
      id: 'seed-4', userId: '3', minutesAgo: 0, text: '我是马里奥，这是我的新视频，希望大家能喜欢！',
      images: ['https://cdn-fakeworld.azureedge.net/fakeworld/pnr1of.jpg'], likeUserIds: ['2', '3', '1', '4'],
      comments: [
        { fromUserId: '2', text: '这视频做的太6了！！！你能教教我怎么做吗' },
        { fromUserId: '3', replyUserId: '2', text: '哈哈哈哈可以！' },
        { fromUserId: '1', text: '666' },
      ],
    },
  ];
  let timelineAnchorCache = 0;
  function builtinFeedList() {
    try {
      const parsed = JSON.parse(localStorage.getItem('feedList') || 'null');
      if (Array.isArray(parsed) && parsed.length) return parsed;
    } catch (_) { /* fall back to the bundled defaults */ }
    // The bundled demo posts are dated relative to a fixed anchor instead of "right now":
    // otherwise their newest post (0 minutes ago) would outrank anything I publish.
    const now = timelineAnchorTs();
    return BUILTIN_FEEDS.map(seed => ({
      id: seed.id,
      userId: seed.userId,
      sendTimestamp: now - Math.round((seed.minutesAgo || 0) * 60000),
      content: {
        type: seed.images && seed.images.length ? 'textWithImages' : 'text',
        text: seed.text,
        imagesInfo: Array.isArray(seed.images) ? seed.images.slice() : [],
      },
      likeUserIds: Array.isArray(seed.likeUserIds) ? seed.likeUserIds.slice() : [],
      comments: (seed.comments || []).map((comment, index) => ({
        id: seed.id + '-c' + index,
        fromUserId: comment.fromUserId,
        replyUserId: comment.replyUserId,
        text: comment.text,
      })),
    }));
  }
  // Keep the demo feed below every post the user published themselves.
  function timelineAnchorTs() {
    if (timelineAnchorCache) return timelineAnchorCache;
    const stamps = (state.myMoments?.posts || []).map(post => Number(post?.createdAt) || 0).filter(Boolean);
    const mineFrom = stamps.length ? Math.min(...stamps) : 0;
    let base = Number(state.timelineAnchor) || 0;
    if (!base || (mineFrom && base > mineFrom - 60000)) base = mineFrom ? mineFrom - 60000 : Date.now();
    timelineAnchorCache = base;
    state.timelineAnchor = base;
    return base;
  }
  function appProfileMap() {
    const map = new Map();
    try {
      const parsed = JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY) || '[]');
      if (Array.isArray(parsed)) parsed.forEach(profile => { if (profile && profile.id != null) map.set(String(profile.id), profile); });
    } catch (_) { /* keep the map empty */ }
    return map;
  }
  function richTextPlain(value) {
    if (typeof value === 'string') return value;
    const parts = [];
    const walk = nodes => (Array.isArray(nodes) ? nodes : []).forEach(node => {
      if (!node) return;
      if (typeof node.text === 'string') parts.push(node.text);
      if (Array.isArray(node.children)) walk(node.children);
    });
    walk(value);
    return parts.join('');
  }
  function timelineItems() {
    const out = [];
    const profiles = appProfileMap();
    const nameOf = id => {
      const profile = profiles.get(String(id));
      return String(profile?.nickname || profile?.name || '').trim();
    };
    builtinFeedList().forEach(feed => {
      const profile = profiles.get(String(feed?.userId));
      const authorName = nameOf(feed?.userId) || '朋友';
      const content = feed?.content || {};
      const override = state.feedOverrides && typeof state.feedOverrides === 'object'
        ? state.feedOverrides[String(feed?.id ?? '')]
        : null;
      out.push({
        id: 'builtin-' + String(feed?.id ?? ''),
        source: 'builtin',
        friend: null,
        authorName,
        authorAvatar: String(profile?.avatarInfo || profile?.avatar || '').trim() || CONTACT_FALLBACKS[0].avatar,
        text: richTextPlain(content.text),
        images: Array.isArray(content.imagesInfo) ? content.imagesInfo.slice() : [],
        location: '',
        createdAt: Number(feed?.sendTimestamp) || 0,
        likes: (Array.isArray(override?.likes)
          ? override.likes
          : (Array.isArray(feed?.likeUserIds) ? feed.likeUserIds : []).map(nameOf)).filter(Boolean),
        comments: Array.isArray(override?.comments)
          ? override.comments.map(comment => ({ id: String(comment?.id ?? ''), from: String(comment?.from || ''), replyTo: String(comment?.replyTo || ''), text: String(comment?.text || '') }))
          : (Array.isArray(feed?.comments) ? feed.comments : []).map((comment, index) => ({
              id: String(comment?.id ?? ('c' + index)),
              from: nameOf(comment?.fromUserId) || '朋友',
              replyTo: comment?.replyUserId ? nameOf(comment.replyUserId) : '',
              text: richTextPlain(comment?.text),
            })),
      });
    });
    (Array.isArray(state.contacts) ? state.contacts : []).forEach(friend => {
      const posts = friend?.moments && Array.isArray(friend.moments.posts) ? friend.moments.posts : [];
      posts.forEach(post => out.push({
        id: String(post?.id ?? ''),
        source: 'local',
        friend,
        post,
        authorName: friendLabel(friend) || friend.name || '',
        authorAvatar: getStableFriendAvatar(friend, friend.avatar || CONTACT_FALLBACKS[0].avatar),
        text: String(post?.text || ''),
        images: Array.isArray(post?.images) ? post.images.slice() : [],
        location: String(post?.location || '').trim(),
        createdAt: Number(post?.createdAt) || 0,
        likes: Array.isArray(post?.likes) ? post.likes.slice() : [],
        comments: Array.isArray(post?.comments) ? post.comments.map(c => ({ id: String(c?.id ?? ''), from: String(c?.from || ''), replyTo: String(c?.replyTo || ''), text: String(c?.text || '') })) : [],
      }));
    });
    // Posts published from the camera in the top right corner.
    const selfFriend = selfMomentsFriend();
    (selfFriend.moments.posts || []).forEach(post => out.push({
      id: String(post?.id ?? ''),
      source: 'local',
      friend: selfFriend,
      post,
      authorName: selfFriend.name,
      authorAvatar: selfFriend.avatar,
      text: String(post?.text || ''),
      images: Array.isArray(post?.images) ? post.images.slice() : [],
      location: String(post?.location || '').trim(),
      createdAt: Number(post?.createdAt) || 0,
      likes: Array.isArray(post?.likes) ? post.likes.slice() : [],
      comments: Array.isArray(post?.comments) ? post.comments.map(c => ({ id: String(c?.id ?? ''), from: String(c?.from || ''), replyTo: String(c?.replyTo || ''), text: String(c?.text || '') })) : [],
    }));
    return out.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  }
  function renderMomentsTimeline(host) {
    if (!host) return;
    host.replaceChildren();
    const items = timelineItems();
    if (!items.length) {
      const empty = document.createElement('div');
      empty.className = 'fw-moments__empty';
      empty.textContent = '还没有朋友圈动态';
      host.appendChild(empty);
      return;
    }
    items.forEach(item => {
      host.appendChild(buildTimelineItem(item));
    });
  }
  function buildTimelineItem(item) {
    const placeholder = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" fill="#f0f0f0"/></svg>');
    const lane = document.createElement('article');
    lane.className = 'fw-tl-item';
    lane.dataset.fwItemId = item.id;
    // Same order as WeChat: content → 时间 + •• → 点赞/评论 block, with the •• menu anchored
    // to the time row (it slides out from the right of the two dots).
    lane.innerHTML = '<img class="fw-tl-item__avatar" alt=""><div class="fw-tl-item__body"><div class="fw-tl-item__name"></div><p class="fw-tl-item__text"></p><div class="fw-tl-item__media"></div><div class="fw-tl-item__meta"><span class="fw-tl-item__place"></span><span class="fw-tl-item__time"></span><button type="button" class="fw-tl-item__more" aria-label="更多操作"><i></i><i></i></button><div class="fw-tl-item__actions" hidden></div></div><div class="fw-tl-item__social" hidden></div></div>';
    const avatar = lane.querySelector('.fw-tl-item__avatar');
    const avatarFallback = localPhotoFor(item.authorAvatar || item.authorName);
    avatar.src = resolveTimelineAvatar(item.authorAvatar);
    avatar.alt = item.authorName;
    avatar.onerror = () => { avatar.onerror = null; avatar.src = avatarFallback; };
    // 2026-10-19: 内置 demo 头像仍是 fakeworld 死链，真机上会挂起不报错，显示成灰块。
    // 走应用的同一个头像解析器换成本地图，并用 watchdog 兜底。
    try {
      const resolved = window.__fwResolveProfileAvatar?.(item.authorAvatar);
      if (resolved && typeof resolved.then === 'function') {
        resolved.then(src => { if (src && avatar.isConnected) avatar.src = src; }).catch(() => {});
      }
    } catch (_) { /* keep the fallback */ }
    setTimeout(() => {
      if (avatar.isConnected && !avatar.naturalWidth) avatar.src = avatarFallback;
    }, 1200);
    const nameEl = lane.querySelector('.fw-tl-item__name');
    nameEl.textContent = item.authorName;
    // My own posts do not open a per-friend moments page (that page is for 好友).
    if (item.friend && !item.friend.self) {
      const open = event => {
        event.preventDefault();
        event.stopPropagation();
        openFriendMoments(item.friend);
      };
      avatar.addEventListener('click', open);
      nameEl.addEventListener('click', open);
    }
    const textEl = lane.querySelector('.fw-tl-item__text');
    textEl.textContent = item.text;
    if (!textEl.textContent) textEl.remove();
    const media = lane.querySelector('.fw-tl-item__media');
    item.images.forEach((src, imageIndex) => {
      const img = document.createElement('img');
      const imageFallback = localPhotoFor(src);
      img.src = src;
      img.alt = '图片';
      img.onerror = () => { img.onerror = null; img.src = imageFallback; };
      img.onload = () => {
        if (item.images.length !== 1 || !img.naturalWidth) return;
        // 单图按原比例展示，最多占正文宽度 68%、高 320px。
        const maxW = Math.max(48, Math.min((media.clientWidth || 240) * 0.68, 240));
        const scale = Math.min(1, maxW / img.naturalWidth, 320 / img.naturalHeight);
        img.style.width = Math.max(48, Math.round(img.naturalWidth * scale)) + 'px';
        img.style.height = Math.max(48, Math.round(img.naturalHeight * scale)) + 'px';
      };
      // 2026-10-19: 内置动态的图片也是 fakeworld 死链，真机上挂起不报错，只剩灰块。
      // 和头像一样走应用解析器换成本地图，再用 watchdog 兜底。
      try {
        const resolved = window.__fwResolveProfileAvatar?.(src);
        if (resolved && typeof resolved.then === 'function') {
          resolved.then(next => { if (next && img.isConnected) img.src = next; }).catch(() => {});
        }
      } catch (_) { /* keep the fallback */ }
      setTimeout(() => {
        if (img.isConnected && !img.naturalWidth) img.src = imageFallback;
      }, 1200);
      img.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        // After a fallback swap `img.src` is the photo actually on screen, so preview that
        // instead of the unreachable CDN URL.
        openMomentImagePreview(img.src || src, item.images, imageIndex);
      });
      ['pointerdown', 'pointerup', 'touchstart', 'touchend'].forEach(type => {
        img.addEventListener(type, event => { event.stopPropagation(); }, true);
      });
      media.appendChild(img);
    });
    if (media.children.length) media.dataset.count = String(media.children.length); else media.remove();
    renderTimelineSocial(lane, item);
    const placeEl = lane.querySelector('.fw-tl-item__place');
    if (item.location) placeEl.textContent = item.location; else placeEl.remove();
    lane.querySelector('.fw-tl-item__time').textContent = momentRelativeTime(item.createdAt);
    const actions = lane.querySelector('.fw-tl-item__actions');
    const openActions = () => {
      const opened = actions.classList.contains('is-open');
      document.querySelectorAll('.fw-tl-item__actions.is-open').forEach(node => node.classList.remove('is-open'));
      document.querySelectorAll('.fw-tl-item__actions').forEach(node => { if (!node.classList.contains('is-open')) node.hidden = true; });
      if (opened) return;
      actions.hidden = false;
      requestAnimationFrame(() => actions.classList.add('is-open'));
    };
    lane.querySelector('.fw-tl-item__more').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      openActions();
    });
    // The like entry mirrors WeChat: while the post is liked it reads 取消 with a red heart,
    // so the popup itself - not a toast - tells the user what the tap did.
    const likedByMe = itemLikedByMe(item);
    actions.innerHTML = '<button type="button" data-act="like"' + (likedByMe ? ' class="is-liked"' : '')
      + '><svg viewBox="0 0 24 24"><path d="M12 20.5S3.5 15 3.5 9.4A4.9 4.9 0 0 1 12 6.3a4.9 4.9 0 0 1 8.5 3.1c0 5.6-8.5 11.1-8.5 11.1Z"/></svg><span>'
      + (likedByMe ? '取消' : '赞') + '</span></button><button type="button" data-act="comment"><svg viewBox="0 0 24 24"><path d="M4 5h16v11H9l-5 4V5Z"/></svg><span>评论</span></button><button type="button" data-act="edit"><svg viewBox="0 0 24 24"><path d="M4 20h4l10-10-4-4L4 16v4Z"/><path d="M14 6l4 4"/></svg><span>修改</span></button>';
    if (item.source !== 'local') actions.querySelector('[data-act="edit"]').remove();
    actions.querySelector('[data-act="like"]').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      const laneId = item.id;
      // The list is re-rendered in place, so remember the (persistent) list host, not the lane.
      const listHost = lane.parentElement;
      toggleTimelineLike(lane, item);
      // Liking re-renders the list; reopen the same popup on the fresh lane so 赞/取消
      // stays visible exactly where the finger is.
      requestAnimationFrame(() => {
        const next = listHost && [...listHost.querySelectorAll('.fw-tl-item')]
          .find(node => node.dataset.fwItemId === laneId);
        next?.querySelector('.fw-tl-item__more')?.click();
      });
    });
    attachLongPress(actions.querySelector('[data-act="like"]'), () => openTimelineLikerPicker(item));
    actions.querySelector('[data-act="comment"]')?.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      openTimelineComment(lane, item, openActions);
    });
    actions.querySelector('[data-act="edit"]')?.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      openMomentsComposer(item.friend, { post: item.post });
    });
    return lane;
  }
  function renderTimelineSocial(lane, item) {
    const host = lane.querySelector('.fw-tl-item__social');
    if (!host) return;
    host.replaceChildren();
    const likes = Array.isArray(item.likes) ? item.likes : [];
    const comments = Array.isArray(item.comments) ? item.comments : [];
    if (likes.length) {
      const line = document.createElement('div');
      line.className = 'fw-tl-item__likes';
      line.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20.5S3.5 15 3.5 9.4A4.9 4.9 0 0 1 12 6.3a4.9 4.9 0 0 1 8.5 3.1c0 5.6-8.5 11.1-8.5 11.1Z"/></svg><span></span>';
      line.querySelector('span').textContent = likes.join('，');
      line.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        toggleTimelineLike(null, item);
      });
      attachLongPress(line, () => openTimelineLikerPicker(item));
      host.appendChild(line);
    }
    comments.forEach(comment => {
      const line = document.createElement('div');
      line.className = 'fw-tl-item__comment';
      const who = document.createElement('b');
      who.textContent = comment.replyTo ? (comment.from + ' 回复 ' + comment.replyTo) : comment.from;
      const body = document.createElement('span');
      body.textContent = '：' + comment.text;
      line.append(who, body);
      attachLongPress(line, () => openTimelineCommentMenu(item, comment));
      if (comment.image) {
        const img = document.createElement('img');
        img.className = 'fw-tl-item__comment-img';
        img.src = comment.image;
        img.alt = '图片';
        img.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          openMomentImagePreview(comment.image);
        });
        line.appendChild(img);
      }
      host.appendChild(line);
    });
    host.hidden = !host.children.length;
  }
  function currentPost(item) {
    if (!item?.friend || !item?.post) return null;
    const moments = getFriendMoments(item.friend);
    return (moments?.posts || []).find(post => String(post.id) === String(item.post.id)) || item.post;
  }
  // 2026-10-10: likes/comments can be edited on both the local posts and the app's own
  // demo feed. Built-in feeds keep their edits in a per-feed override inside the state.
  function attachLongPress(element, handler, holdMs = HOLD_MS) {
    if (!element) return;
    let timer = null;
    let startX = 0;
    let startY = 0;
    let fired = false;
    const cancel = () => { if (timer) { clearTimeout(timer); timer = null; } };
    element.style.touchAction = 'manipulation';
    element.addEventListener('pointerdown', event => {
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      event.stopPropagation();
      fired = false;
      startX = event.clientX;
      startY = event.clientY;
      cancel();
      timer = setTimeout(() => { timer = null; fired = true; handler(); }, holdMs);
    }, true);
    element.addEventListener('pointermove', event => {
      if (Math.abs(event.clientX - startX) > 12 || Math.abs(event.clientY - startY) > 12) cancel();
    }, true);
    ['pointerup', 'pointercancel', 'pointerleave'].forEach(type => element.addEventListener(type, cancel, true));
    element.addEventListener('click', event => {
      if (!fired) return;
      fired = false;
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
    }, true);
  }
  function feedOverrideKey(item) {
    return String(item?.id || '').replace(/^builtin-/, '');
  }
  function getFeedOverride(item) {
    if (!state.feedOverrides || typeof state.feedOverrides !== 'object') state.feedOverrides = {};
    const key = feedOverrideKey(item);
    if (!key) return null;
    const existing = state.feedOverrides[key];
    if (!existing || typeof existing !== 'object') {
      state.feedOverrides[key] = {
        likes: Array.isArray(item?.likes) ? item.likes.slice() : [],
        comments: Array.isArray(item?.comments) ? item.comments.map(comment => ({ ...comment })) : [],
      };
    }
    const record = state.feedOverrides[key];
    if (!Array.isArray(record.likes)) record.likes = [];
    if (!Array.isArray(record.comments)) record.comments = [];
    return record;
  }
  function mutableItem(item) {
    if (!item) return null;
    if (item.source === 'local') return currentPost(item) || item.post || null;
    return getFeedOverride(item);
  }
  // The comments rendered in the timeline are copies; edits must land on the live record
  // (the local post's own array, or the per-feed override for the bundled demo feeds).
  function commentAt(item, comment) {
    const id = String(comment?.id ?? '');
    if (!id) return null;
    const list = item?.source === 'local'
      ? (currentPost(item)?.comments || [])
      : (getFeedOverride(item)?.comments || []);
    return list.find(entry => String(entry?.id ?? '') === id) || null;
  }
  function timelineContactChoices() {
    const map = new Map();
    try {
      appProfileMap().forEach(profile => {
        const name = String(profile?.nickname || profile?.name || '').trim();
        if (name && !map.has(name)) map.set(name, String(profile?.avatarInfo || profile?.avatar || '').trim());
      });
    } catch (_) { /* ignore */ }
    try {
      collectContacts().forEach(contact => {
        const name = String(contact?.name || '').trim();
        if (name && !map.has(name)) map.set(name, String(contact?.avatar || '').trim());
      });
    } catch (_) { /* ignore */ }
    const me = selfDisplayName();
    if (me && me !== '我' && !map.has(me)) map.set(me, '');
    return [...map.entries()].map(([name, avatar]) => ({ name, avatar }));
  }
  function openTimelineContactPicker({ title, hint, selected, multi, onConfirm }) {
    document.querySelector('.fw-tl-picker')?.remove();
    const layer = document.createElement('div');
    layer.className = 'fw-tl-picker';
    layer.innerHTML = '<div class="fw-tl-picker__panel"><header><button type="button" data-cancel>取消</button><strong></strong><button type="button" data-confirm>确定</button></header><p class="fw-tl-picker__hint"></p><div class="fw-tl-picker__rows"></div></div>';
    layer.querySelector('strong').textContent = String(title || '');
    layer.querySelector('.fw-tl-picker__hint').textContent = String(hint || '');
    const rows = layer.querySelector('.fw-tl-picker__rows');
    const chosen = new Set((selected || []).map(name => String(name)).filter(Boolean));
    const sync = () => {
      rows.querySelectorAll('.fw-tl-picker__row').forEach(node => {
        const on = chosen.has(node.dataset.fwName);
        node.classList.toggle('is-on', on);
        node.querySelector('i').textContent = on ? '✓' : '';
      });
    };
    timelineContactChoices().forEach(choice => {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'fw-tl-picker__row';
      row.dataset.fwName = choice.name;
      row.innerHTML = '<img alt=""><span></span><i></i>';
      const img = row.querySelector('img');
      img.src = choice.avatar || CONTACT_FALLBACKS[0].avatar;
      img.onerror = () => { img.onerror = null; img.src = localPhotoFor(choice.name); };
      row.querySelector('span').textContent = choice.name;
      row.addEventListener('click', () => {
        if (multi) {
          if (chosen.has(choice.name)) chosen.delete(choice.name); else chosen.add(choice.name);
        } else {
          chosen.clear();
          chosen.add(choice.name);
        }
        sync();
      });
      rows.appendChild(row);
    });
    sync();
    layer.querySelector('[data-cancel]').addEventListener('click', () => layer.remove());
    layer.querySelector('[data-confirm]').addEventListener('click', () => {
      const names = [...chosen];
      layer.remove();
      if (typeof onConfirm === 'function') onConfirm(names);
    });
    layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
    document.body.appendChild(layer);
  }
  function openTimelineLikerPicker(item) {
    const data = mutableItem(item);
    if (!data) { showToast('这条动态暂时不能修改'); return; }
    openTimelineContactPicker({
      title: '点赞',
      hint: '选择给这条动态点赞的好友（可多选）',
      selected: Array.isArray(data.likes) ? data.likes : [],
      multi: true,
      onConfirm: names => {
        data.likes = names.slice();
        savePostAndRefresh(item);
      },
    });
  }
  function openTimelineCommentMenu(item, comment) {
    const data = mutableItem(item);
    if (!data) { showToast('这条动态暂时不能修改'); return; }
    document.querySelector('.fw-tl-picker')?.remove();
    const layer = document.createElement('div');
    layer.className = 'fw-tl-picker';
    layer.innerHTML = '<div class="fw-tl-picker__panel"><header><button type="button" data-cancel>取消</button><strong>评论</strong><span></span></header><div class="fw-tl-picker__rows fw-tl-menu"><button type="button" class="fw-tl-menu-row" data-act="edit">修改内容</button><button type="button" class="fw-tl-menu-row" data-act="author">修改评论人</button><button type="button" class="fw-tl-menu-row is-danger" data-act="delete">删除评论</button></div></div>';
    layer.querySelector('[data-cancel]').addEventListener('click', () => layer.remove());
    layer.querySelector('[data-act="edit"]').addEventListener('click', () => { layer.remove(); editTimelineComment(item, comment); });
    layer.querySelector('[data-act="author"]').addEventListener('click', () => { layer.remove(); changeTimelineCommentAuthor(item, comment); });
    layer.querySelector('[data-act="delete"]').addEventListener('click', () => { layer.remove(); deleteTimelineComment(item, comment); });
    layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
    document.body.appendChild(layer);
  }
  function editTimelineComment(item, comment) {
    const live = commentAt(item, comment) || comment;
    const { dialog } = makeDialog('修改评论', '编辑这条评论的内容', '<textarea class="fw-textarea fw-tl-comment-input"></textarea>', [
      { label: '取消', onClick: closeOverlay },
      { label: '保存', primary: true, onClick: () => {
        const value = dialog.querySelector('.fw-tl-comment-input').value.trim();
        if (!value) { showToast('评论不能为空'); return; }
        live.text = value;
        closeOverlay();
        savePostAndRefresh(item);
      } },
    ]);
    const input = dialog.querySelector('.fw-tl-comment-input');
    input.value = String(live.text || '');
    setTimeout(() => input.focus(), 60);
  }
  function changeTimelineCommentAuthor(item, comment) {
    const live = commentAt(item, comment) || comment;
    openTimelineContactPicker({
      title: '修改评论人',
      hint: '选择这条评论的发布者',
      selected: [String(comment.from || '')],
      multi: false,
      onConfirm: names => {
        const name = String(names[0] || '').trim();
        if (!name) return;
        live.from = name;
        savePostAndRefresh(item);
        showToast('评论人已改为 ' + name);
      },
    });
  }
  function deleteTimelineComment(item, comment) {
    makeDialog('删除评论', '删除后无法恢复', '<p>确定删除这条评论吗？</p>', [
      { label: '取消', onClick: closeOverlay },
      { label: '删除', primary: true, onClick: () => {
        const data = mutableItem(item);
        if (data && Array.isArray(data.comments)) {
          data.comments = data.comments.filter(entry => String(entry?.id ?? '') !== String(comment?.id ?? ''));
        }
        closeOverlay();
        savePostAndRefresh(item);
        showToast('评论已删除');
      } },
    ]);
  }
  function selfDisplayName() {
    try {
      const profile = window.__fwGetMyProfile?.() || null;
      return String(profile?.nickname || profile?.name || '我');
    } catch (_) { return '我'; }
  }
  // Posts published from the timeline's camera belong to "me": they live in their own
  // bucket inside the local state and are merged into 发现 → 朋友圈 like a friend's posts.
  function selfMomentsFriend() {
    if (!state.myMoments || typeof state.myMoments !== 'object') state.myMoments = { cover: '', posts: [] };
    if (!Array.isArray(state.myMoments.posts)) state.myMoments.posts = [];
    const profile = (() => {
      try { return window.__fwGetMyProfile?.() || null; } catch (_) { return null; }
    })() || {};
    return {
      id: '__self__',
      self: true,
      name: String(profile.nickname || profile.name || '我'),
      avatar: String(profile.avatarInfo || profile.avatar || CONTACT_FALLBACKS[0].avatar),
      moments: state.myMoments,
    };
  }
  function savePostAndRefresh(item) {
    saveState();
    const host = document.querySelector('.fw-moments-feed__list');
    if (host) renderMomentsTimeline(host);
    const moments = document.querySelector('.fw-moments-page');
    if (moments && item?.friend) renderMomentsList(moments.querySelector('.fw-moments__list'), item.friend);
  }
  function toggleTimelineLike(lane, item) {
    // Both my/friends' posts and the bundled demo feed can be liked: the demo feed keeps
    // its likes in the per-feed override, exactly like its comments.
    const post = mutableItem(item);
    if (!post) { showToast('这条动态暂时不能点赞'); return; }
    const me = selfDisplayName();
    if (!Array.isArray(post.likes)) post.likes = [];
    const index = post.likes.indexOf(me);
    if (index >= 0) post.likes.splice(index, 1); else post.likes.push(me);
    savePostAndRefresh(item);
  }
  // 2026-10-10: WeChat shows the like state on the post itself (and, inside the •• popup, 赞
  // turning into a red heart with 取消) instead of a toast, so the toggle stays silent.
  function itemLikedByMe(item) {
    const me = selfDisplayName();
    if (item?.source === 'local') {
      const post = currentPost(item);
      return Array.isArray(post?.likes) && post.likes.includes(me);
    }
    const key = feedOverrideKey(item);
    const record = key && state.feedOverrides && typeof state.feedOverrides === 'object'
      ? state.feedOverrides[key]
      : null;
    const likes = Array.isArray(record?.likes) ? record.likes : (Array.isArray(item?.likes) ? item.likes : []);
    return likes.includes(me);
  }
  function openTimelineComment(lane, item, reopen) {
    openTimelineCommentBar(item);
  }
  // 2026-10-10: commenting mirrors the WeChat timeline - a bar above the keyboard
  // ("发表评论：" + 表情 + 图片 + 发送) instead of a modal dialog.
  function openTimelineCommentBar(item) {
    const data = mutableItem(item);
    if (!data) { showToast('这条动态暂时不能评论'); return; }
    // Tapping 评论 in the •• popup collapses that popup, exactly like WeChat.
    document.querySelectorAll('.fw-tl-item__actions.is-open').forEach(node => {
      node.classList.remove('is-open');
      setTimeout(() => { if (!node.classList.contains('is-open')) node.hidden = true; }, 180);
    });
    document.querySelector('.fw-tl-comment-bar')?.remove();
    const bar = document.createElement('div');
    bar.className = 'fw-tl-comment-bar';
    bar.innerHTML = '<input class="fw-tl-comment-bar__input" placeholder="发表评论："><button type="button" class="fw-tl-comment-bar__emoji" aria-label="表情"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M8.5 14.5a4 4 0 0 0 7 0"/><circle cx="9" cy="10" r="1"/><circle cx="15" cy="10" r="1"/></svg></button><button type="button" class="fw-tl-comment-bar__image" aria-label="图片"><svg viewBox="0 0 24 24"><rect x="3.5" y="5" width="17" height="14" rx="2"/><path d="M3.5 15.5 9 11l5 4.5 3-2.5 3.5 3"/><circle cx="9" cy="9" r="1.3"/></svg></button><button type="button" class="fw-tl-comment-bar__send" disabled>发送</button>';
    const input = bar.querySelector('.fw-tl-comment-bar__input');
    const send = bar.querySelector('.fw-tl-comment-bar__send');
    const sync = () => {
      const on = !!input.value.trim();
      send.disabled = !on;
      send.classList.toggle('is-enabled', on);
    };
    // Focusing a WebView input is not enough to raise the Android IME, so the page also
    // asks the native bridge; retry a few times because the first request often lands
    // before the WebView owns the input focus (and the IME's action key dismisses it).
    const raiseKeyboard = () => {
      // Ask the platform first: its requestFocus() can move the DOM focus to the first
      // focusable node, so the caret is restored afterwards, never before.
      try { window.DoutuApp?.showSoftKeyboard?.(); } catch (_) { /* ignore */ }
      try { input.focus(); } catch (_) { /* ignore */ }
    };
    const keepKeyboard = () => {
      raiseKeyboard();
      requestAnimationFrame(raiseKeyboard);
      setTimeout(raiseKeyboard, 120);
      setTimeout(raiseKeyboard, 320);
      setTimeout(raiseKeyboard, 620);
    };
    const submit = () => {
      const value = input.value.trim();
      if (!value) return;
      if (!Array.isArray(data.comments)) data.comments = [];
      data.comments.push({ id: 'c' + Date.now(), from: selfDisplayName(), text: value, createdAt: Date.now() });
      input.value = '';
      sync();
      savePostAndRefresh(item);
      showToast('评论已发送');
      // WeChat keeps the comment bar above the keyboard after sending, so put the caret
      // back into the field instead of dropping the bar to the bottom of the screen.
      keepKeyboard();
    };
    input.addEventListener('input', sync);
    input.addEventListener('keydown', event => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      submit();
      // The IME 'go' action still hides the keyboard even when keydown is cancelled.
      setTimeout(raiseKeyboard, 60);
      setTimeout(raiseKeyboard, 240);
    });
    send.addEventListener('click', submit);
    bar.querySelector('.fw-tl-comment-bar__emoji').addEventListener('click', event => {
      event.stopPropagation();
      openTimelineEmojiPicker(emoji => {
        input.value = (input.value || '') + emoji;
        sync();
        try { input.focus(); } catch (_) { /* keep focus best effort */ }
      });
    });
    bar.querySelector('.fw-tl-comment-bar__image').addEventListener('click', event => {
      event.stopPropagation();
      openChatGallery(null, async photos => {
        const source = photos?.[0];
        if (!source) return;
        const small = await shrinkMomentImage(source, 1080, 0.8);
        if (!Array.isArray(data.comments)) data.comments = [];
        data.comments.push({ id: 'c' + Date.now(), from: selfDisplayName(), text: '', image: small, createdAt: Date.now() });
        savePostAndRefresh(item);
        showToast('已发送图片评论');
      }, { actionLabel: '发送' });
    });
    ['pointerdown', 'pointerup', 'click'].forEach(type => bar.addEventListener(type, event => event.stopPropagation()));
    document.body.appendChild(bar);
    sync();
    keepKeyboard();
  }
  function openTimelineEmojiPicker(onPick) {
    const emojis = ['😀','😄','😆','😂','😊','😍','😘','😎','🤔','😅','😭','😡','👍','👏','🙏','💪','🎉','❤️','🔥','✨','🍺','🌸','🐶','🐱','🌙','☀️','🍀','🎁'];
    document.querySelector('.fw-tl-picker')?.remove();
    const layer = document.createElement('div');
    layer.className = 'fw-tl-picker';
    layer.innerHTML = '<div class="fw-tl-picker__panel"><header><button type="button" data-cancel>取消</button><strong>表情</strong><span></span></header><div class="fw-tl-emoji-grid"></div></div>';
    const grid = layer.querySelector('.fw-tl-emoji-grid');
    emojis.forEach(emoji => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'fw-tl-emoji';
      button.textContent = emoji;
      button.addEventListener('click', () => { layer.remove(); if (typeof onPick === 'function') onPick(emoji); });
      grid.appendChild(button);
    });
    layer.querySelector('[data-cancel]').addEventListener('click', () => layer.remove());
    layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
    document.body.appendChild(layer);
  }
  // 2026-10-10: tapping anywhere else dismisses the open •• action bar.
  document.addEventListener('pointerdown', event => {
    const opened = document.querySelectorAll('.fw-tl-item__actions.is-open');
    if (!opened.length) return;
    const target = event.target instanceof Element ? event.target : null;
    if (target && (target.closest('.fw-tl-item__actions') || target.closest('.fw-tl-item__more'))) return;
    opened.forEach(node => {
      node.classList.remove('is-open');
      setTimeout(() => { if (!node.classList.contains('is-open')) node.hidden = true; }, 180);
    });
  }, true);
  function openCoverViewer(getSource, onChange) {
    document.querySelector('.fw-cover-viewer')?.remove();
    const layer = document.createElement('div');
    layer.className = 'fw-cover-viewer';
    layer.innerHTML = '<img class="fw-cover-viewer__image" alt="封面"><button type="button" class="fw-cover-viewer__back" aria-label="返回">‹</button><button type="button" class="fw-cover-viewer__change"><svg viewBox="0 0 24 24"><rect x="3.5" y="5" width="17" height="14" rx="2"/><path d="M3.5 15.5 9 11l5 4.5 3-2.5 3.5 3"/><circle cx="9" cy="9" r="1.3"/></svg><span>换封面</span></button>';
    const image = layer.querySelector('.fw-cover-viewer__image');
    const source = String(getSource?.() || '').trim() || LOCAL_COVER_FALLBACK;
    image.src = source;
    image.onerror = () => { image.onerror = null; image.src = localPhotoFor(source); };
    layer.querySelector('.fw-cover-viewer__back').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      layer.remove();
    });
    layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
    layer.querySelector('.fw-cover-viewer__change').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      openChatGallery(null, async photos => {
        const picked = photos?.[0];
        if (!picked) return;
        const small = await shrinkMomentImage(picked, 1600, 0.86);
        layer.remove();
        if (typeof onChange === 'function') onChange(small);
      }, { actionLabel: '设为封面' });
    });
    ['pointerdown', 'pointerup', 'touchstart', 'touchend'].forEach(type => layer.addEventListener(type, event => event.stopPropagation(), true));
    document.body.appendChild(layer);
  }
  function openMomentsTimeline() {
    document.querySelector('.fw-moments-feed')?.remove();
    const profile = (() => {
      try { return window.__fwGetMyProfile?.() || null; } catch (_) { return null; }
    })() || {};
    const selfName = String(profile.nickname || profile.name || '林克');
    const selfAvatar = String(profile.avatarInfo || profile.avatar || CONTACT_FALLBACKS[0].avatar);
    const selfBackground = String(profile.momentsBackgroundInfo || '').trim();
    const selfCover = selfBackground || selfAvatar;
    const page = document.createElement('section');
    page.className = 'fw-moments-feed';
    page.dataset.fwOpenedAt = String(Date.now());
    page.innerHTML = '<header class="fw-moments__hero fw-moments-feed__hero"><div class="fw-moments__cover"><img class="fw-moments__cover-img" alt=""></div><button type="button" class="fw-moments__back" aria-label="返回">‹</button><div class="fw-moments__identity"><span class="fw-moments__name"></span><img class="fw-moments__avatar" alt=""></div><button type="button" class="fw-moments-feed__changecover"><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="5" width="17" height="14" rx="2"/><path d="M3.5 15.5 9 11l5 4.5 3-2.5 3.5 3"/><circle cx="9" cy="9" r="1.3"/></svg><span>换封面</span></button></header><main class="fw-moments__body fw-moments-feed__body"><p class="fw-moments__signature"></p><div class="fw-moments-feed__list"></div></main>';
    const topbar = document.createElement('div');
    topbar.className = 'fw-moments-feed__topbar';
    topbar.innerHTML = '<button type="button" class="fw-moments-feed__topback" aria-label="返回">‹</button><strong>朋友圈</strong><button type="button" class="fw-moments-feed__camera" aria-label="发表"><svg viewBox="0 0 24 24"><path d="M4 8h3l1.5-2h7L17 8h3v11H4z"/><circle cx="12" cy="13" r="3.4"/></svg></button>';
    page.appendChild(topbar);
    // WeChat keeps the camera in the top right corner the whole time the timeline is open:
    // it is painted white over the cover and swaps for the grey bar's camera when scrolled.
    const floatCamera = document.createElement('button');
    floatCamera.type = 'button';
    floatCamera.className = 'fw-moments-feed__cam';
    floatCamera.setAttribute('aria-label', '发表');
    floatCamera.innerHTML = '<svg viewBox="0 0 24 24"><path d="M4 8h3l1.5-2h7L17 8h3v11H4z"/><circle cx="12" cy="13" r="3.4"/></svg>';
    page.appendChild(floatCamera);
    page.querySelector('.fw-moments__name').textContent = selfName;
    const avatarEl = page.querySelector('.fw-moments__avatar');
    avatarEl.src = selfAvatar;
    avatarEl.alt = selfName;
    avatarEl.onerror = () => { avatarEl.onerror = null; avatarEl.src = CONTACT_FALLBACKS[0].avatar; };
    try {
      const resolved = window.__fwResolveProfileAvatar?.(profile.avatarInfo);
      if (resolved && typeof resolved.then === 'function') {
        resolved.then(src => { if (src && avatarEl.isConnected) avatarEl.src = src; }).catch(() => {});
      }
    } catch (_) { /* keep the profile value */ }
    applyCoverImage(page.querySelector('.fw-moments__cover'), selfCover, !selfBackground);
    page.querySelector('.fw-moments__back').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      page.remove();
    });
    topbar.querySelector('.fw-moments-feed__topback').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      page.remove();
    });
    topbar.querySelector('.fw-moments-feed__camera').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      openMomentsComposer(selfMomentsFriend());
    });
    const openComposer = event => {
      event.preventDefault();
      event.stopPropagation();
      openMomentsComposer(selfMomentsFriend());
    };
    floatCamera.addEventListener('click', openComposer);
    page.addEventListener('scroll', () => {
      const scrolled = page.scrollTop > 60;
      topbar.classList.toggle('is-visible', scrolled);
      page.classList.toggle('is-scrolled', scrolled);
    }, { passive: true });
    // Pulling the page down (or tapping the cover) grows the cover over most of the screen
    // while the timeline keeps flowing underneath - no separate photo viewer. The gesture
    // follows the finger and snaps to the nearest state on release; tapping again, dragging
    // back, or the system back button returns to the normal size.
    const hero = page.querySelector('.fw-moments__hero');
    const viewportH = () => Math.max(320, page.clientHeight || window.innerHeight || 640);
    // Reference screenshots: the expanded cover fills ~81% of the screen height with the
    // timeline still scrolling underneath.
    const baseCoverH = () => Math.max(viewportH() * 0.4, 300);
    const focusCoverH = () => Math.max(viewportH() * 0.81, 640);
    let coverH = baseCoverH();
    const paintCover = (height, animate) => {
      const base = baseCoverH();
      const focus = focusCoverH();
      coverH = Math.max(base * 0.94, Math.min(focus * 1.06, height));
      hero.style.transition = animate ? 'height .24s cubic-bezier(.2, .8, .25, 1)' : 'none';
      hero.style.height = Math.round(coverH) + 'px';
      // The avatar/name belong to the small state; 换封面 only shows while expanded.
      page.classList.toggle('is-cover-focus', coverH > base + (focus - base) * 0.5);
    };
    const setCoverFocus = (on, animate) => {
      if (on) page.scrollTop = 0;
      paintCover(on ? focusCoverH() : baseCoverH(), animate !== false);
    };
    paintCover(coverH, false);
    // Exposed so the system back button can shrink the cover before leaving the page.
    page.__fwCollapseCover = () => {
      if (coverH <= baseCoverH() + 1) return false;
      setCoverFocus(false);
      return true;
    };
    let coverTouch = false;
    let coverOwned = false;
    let coverDragMoved = false;
    let coverStartY = 0;
    let coverStartH = 0;
    const snapCover = () => {
      const base = baseCoverH();
      const focus = focusCoverH();
      setCoverFocus(coverH > base + (focus - base) * 0.5);
    };
    page.addEventListener('touchstart', event => {
      coverTouch = false;
      coverOwned = false;
      if (event.touches.length !== 1) return;
      // A pull can start anywhere as long as the list is still at the very top.
      if (page.scrollTop > 2 && coverH <= baseCoverH() + 1) return;
      coverTouch = true;
      coverDragMoved = false;
      coverStartY = event.touches[0].clientY;
      coverStartH = coverH;
    }, { passive: true, capture: true });
    page.addEventListener('touchmove', event => {
      if (!coverTouch) return;
      const dy = event.touches[0].clientY - coverStartY;
      if (!coverOwned) {
        if (Math.abs(dy) < 6) return;
        if (dy > 0) {
          // Pulling down only grows the cover while the list is still at the very top.
          if (page.scrollTop > 2) { coverTouch = false; return; }
        } else if (coverH <= baseCoverH() + 1) {
          // Already small and dragging up: that gesture belongs to the list.
          coverTouch = false;
          return;
        }
        // Once the cover takes the gesture it keeps it until the finger lifts, so restoring
        // the cover never drags the list along.
        coverOwned = true;
      }
      coverDragMoved = true;
      const focus = focusCoverH();
      const base = baseCoverH();
      let next = coverStartH + dy * 0.92;
      if (next > focus) next = focus + (next - focus) * 0.2;
      if (next < base) next = base - (base - next) * 0.2;
      paintCover(next, false);
      event.preventDefault();
    }, { passive: false, capture: true });
    const endCoverTouch = () => {
      if (!coverTouch) return;
      coverTouch = false;
      coverOwned = false;
      if (!coverDragMoved) return;
      snapCover();
      // A drag does not always produce a click, so clear the flag shortly after release;
      // the click that a real drag does fire is swallowed by the guard above.
      setTimeout(() => { coverDragMoved = false; }, 320);
    };
    // Capture phase: the post images stop touch events from bubbling, so the pull has to be
    // observed on the way down the tree.
    page.addEventListener('touchend', endCoverTouch, { passive: true, capture: true });
    page.addEventListener('touchcancel', endCoverTouch, { passive: true, capture: true });
    const coverEl = page.querySelector('.fw-moments__cover');
    coverEl.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      // The 发现 → 朋友圈 row is intercepted on pointerup, so the click that follows the
      // same touch can land on the cover that was just mounted. Ignore those ghost taps.
      if (Date.now() - (Number(page.dataset.fwOpenedAt) || 0) < 650) return;
      // A pull that already moved the cover must not also fire a tap-to-toggle.
      if (coverDragMoved) { coverDragMoved = false; return; }
      setCoverFocus(coverH <= baseCoverH() + 1);
    });
    page.querySelector('.fw-moments-feed__changecover').addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      openChatGallery(null, async photos => {
        const picked = photos?.[0];
        if (!picked) return;
        const small = await shrinkMomentImage(picked, 1600, 0.86);
        try { window.__fwUpdateMyProfile?.({ momentsBackgroundInfo: small }); } catch (_) { /* keep the local view */ }
        const img = coverEl.querySelector('.fw-moments__cover-img');
        if (img) { img.onerror = null; img.src = small; }
        coverEl.classList.remove('is-soft');
        showToast('朋友圈封面已更新');
      }, { actionLabel: '设为封面' });
    });
    renderMomentsTimeline(page.querySelector('.fw-moments-feed__list'));
    document.body.appendChild(page);
    syncChatSystemBars();
  }
  // The bundled discover page owns the 朋友圈 row; intercept that tap.
  function openDiscoverMomentsIfTapped(event) {
    if (!isStaticDiscoverRoute()) return false;
    let node = event.target instanceof Element ? event.target : null;
    for (let depth = 0; node && depth < 6; depth += 1, node = node.parentElement) {
      if ((node.textContent || '').trim() !== '朋友圈') continue;
      event.preventDefault();
      event.stopImmediatePropagation();
      const opened = document.querySelector('.fw-moments-feed');
      if (opened && Date.now() - (Number(opened.dataset.fwOpenedAt) || 0) < 600) return true;
      openMomentsTimeline();
      return true;
    }
    return false;
  }
  // Deleting a friend has to touch three stores: the local chat-actions state (chat list
  // + contacts), fake-world's persisted address book (allProfiles) and the in-memory
  // caches. fake-world's own sync only ever merges profiles, so the module is reloaded
  // after the toast so the contacts screen drops the friend too.
  function deleteLocalFriend(friend) {
    if (!friend) return false;
    if (friend.payAssistant) { showToast('收款助手无法删除'); return false; }
    const id = String(friend.id ?? '').trim();
    const label = friendLabel(friend) || friend.name || '';
    // Match on the id when there is one, otherwise fall back to the exact display name so
    // a friend without an id can never take unrelated contacts down with it.
    const sameFriend = item => {
      if (item == null) return false;
      if (id) return String(item.id ?? '') === id;
      const itemName = String(item.remark || item.name || '').trim();
      return !!label && itemName === String(label).trim();
    };
    if (Array.isArray(state.contacts)) state.contacts = state.contacts.filter(item => !sameFriend(item));
    if (Array.isArray(state.groups)) state.groups = state.groups.filter(item => !sameFriend(item));
    if (state.chats && label) delete state.chats[label];
    try { nativeFriendCache?.delete?.(id); } catch (_) { /* cache is best effort */ }
    saveState();
    queueApply();
    closeFriendProfile();
    showToast('已删除好友');
    setTimeout(() => {
      try {
        const profiles = JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY) || '[]');
        if (Array.isArray(profiles)) {
          localStorage.setItem(NATIVE_PROFILES_KEY, JSON.stringify(profiles.filter(profile => {
            if (profile == null) return false;
            if (id) return String(profile.id ?? '') !== id;
            return String(profile.nickname || profile.name || '').trim() !== String(label).trim();
          })));
        }
      } catch (_) { /* keep the local delete even if the address book cannot be pruned */ }
      try { location.reload(); } catch (_) { /* reload is best effort */ }
    }, 260);
    return true;
  }
  // 2026-10-10: tapping the friend info block on the profile page opens this chooser so
  // 备注 / 昵称 / 微信号 / 地区 can each be edited from one place (the single field
  // dialogs are still the ones doing the writing).
  function openFriendInfoEditor(friend) {
    if (!friend) return;
    document.querySelector('.fw-friend-info-editor')?.remove();
    const fields = [
      ['name', '备注', () => friendLabel(friend) || friend.name || ''],
      ['nickname', '昵称', () => friend.nickname || friend.realName || friendLabel(friend) || ''],
      ['wechatId', '微信号', () => friend.wechatId || ''],
      ['region', '地区', () => friend.region || ''],
    ];
    const layer = document.createElement('div');
    layer.className = 'fw-friend-info-editor';
    layer.innerHTML = '<div class="fw-friend-info-editor__panel"><header><button type="button" data-cancel>取消</button><strong>设置好友信息</strong><span></span></header><div class="fw-friend-info-editor__rows"></div></div>';
    const host = layer.querySelector('.fw-friend-info-editor__rows');
    fields.forEach(([field, label, current]) => {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'fw-friend-info-editor__row';
      row.innerHTML = '<span></span><b></b><i>›</i>';
      row.querySelector('span').textContent = label;
      row.querySelector('b').textContent = current() || '未设置';
      row.onclick = () => { layer.remove(); editFriendField(friend, field, label); };
      host.appendChild(row);
    });
    layer.querySelector('[data-cancel]').onclick = () => layer.remove();
    layer.addEventListener('click', event => { if (event.target === layer) layer.remove(); });
    document.body.appendChild(layer);
  }
  function renderFriendProfilePage(page,friend){const label=friendLabel(friend);const avatarEl=page.querySelector(".fw-friend-profile__avatar");avatarEl.src=getStableFriendAvatar(friend, friend.avatar||CONTACT_FALLBACKS[0].avatar);applyResolvedAvatar(avatarEl, friend.avatar||friend.avatarInfo);page.querySelectorAll("[data-field=name]").forEach(n=>n.textContent=label);page.querySelectorAll("[data-field=nickname]").forEach(n=>n.textContent=friend.nickname||friend.realName||label);page.querySelector("[data-field=wechatId]").textContent=friend.wechatId||friend.id||"未设置";page.querySelector("[data-field=region]").textContent=friend.region||"未设置";const moments=page.querySelector("[data-field=moments]");if(moments){moments.replaceChildren(...momentImages(friend).map(src=>{const img=document.createElement("img");img.src=src;img.alt="";img.draggable=false;return img;}));}}
  function openFriendProfile(friend){
    if(!friend)return; closeFriendProfile();
    const page=document.createElement("section"); page.className="fw-friend-profile-page"; page.dataset.fwFriendId=friend.id;
    page.innerHTML=`<header class="fw-friend-profile__header"><button class="fw-friend-profile__back" aria-label="返回"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15.5 3.5 5.5 12l10 8.5"/></svg></button><button class="fw-friend-profile__more" aria-label="更多"><svg viewBox="0 0 28 8" aria-hidden="true"><circle cx="4" cy="4" r="1.7"/><circle cx="14" cy="4" r="1.7"/><circle cx="24" cy="4" r="1.7"/></svg></button></header><main class="fw-friend-profile__body"><section class="fw-friend-profile__hero"><button class="fw-friend-profile__avatar-button"><img class="fw-friend-profile__avatar"></button><div class="fw-friend-profile__identity"><button class="fw-friend-profile__name" data-edit="name"><span data-field="name"></span><em>♟</em></button><button class="fw-friend-profile__meta" data-edit="nickname">昵称：<span data-field="nickname"></span></button><button class="fw-friend-profile__meta" data-edit="wechatId">微信号：<span data-field="wechatId"></span></button><button class="fw-friend-profile__meta" data-edit="region">地区：<span data-field="region"></span></button></div></section><section class="fw-friend-profile__group fw-friend-profile__friend-info"><button class="fw-friend-profile__section-title" data-edit="signature"><span>朋友资料</span><i>›</i></button><p class="fw-friend-profile__note">添加朋友的备注名、电话、标签、备注、照片等，并设置朋友权限。</p></section><section class="fw-friend-profile__group fw-friend-profile__moments-group"><button class="fw-friend-profile__row fw-friend-profile__moments-row"><span>朋友圈</span><b class="fw-friend-profile__moments" data-field="moments"></b><i>›</i></button></section><section class="fw-friend-profile__group"><button class="fw-friend-profile__row"><span>视频号</span><b>${friendLabel(friend)}之一</b><i>›</i></button></section><section class="fw-friend-profile__actions"><button data-action="message"><svg class="fw-action-icon fw-action-icon--message" viewBox="0 0 48 40" aria-hidden="true"><path d="M24 3C12.4 3 3 10.4 3 19.5c0 5.2 3.1 9.8 8 12.8l-2 5.2 9-3.3c2 .5 4 .8 6 .8 11.6 0 21-7.4 21-16.5S35.6 3 24 3Z"/></svg><span>发消息</span></button><button data-action="call"><svg class="fw-action-icon fw-action-icon--call" viewBox="0 0 48 44" aria-hidden="true"><path d="M8 4 17 3l4 10-6 5c3 6 7 10 13 13l5-6 10 4-1 9c-.2 2-2 3-4 3C20 39 5 24 5 7c0-1.6 1-2.8 3-3Z"/><path d="M29 5h14v12H29zM43 9l4-3v10l-4-3"/></svg><span>音视频通话</span></button></section></main>`;
    document.body.appendChild(page); document.body.classList.add("fw-friend-profile-open"); renderFriendProfilePage(page,friend);
    page.querySelector(".fw-friend-profile__back").onclick=()=>{const nativeRoute=/^#\/wechat\/friend\//.test(String(location.hash||""));closeFriendProfile();if(nativeRoute)location.hash="#/wechat/contacts";};
    page.querySelector(".fw-friend-profile__more").onclick=()=>{const name=friendLabel(friend)||friend.name||"该好友";makeDialog("删除好友","确定要删除「"+name+"」吗？删除后将同时清空与该好友的聊天记录。","",[{label:"取消",onClick:closeOverlay},{label:"删除",primary:true,onClick:()=>{closeOverlay();deleteLocalFriend(friend);}}]);};
    page.querySelector(".fw-friend-profile__avatar-button").onclick=()=>openFriendAvatarPicker(friend);
    page.querySelectorAll("[data-edit]").forEach(row=>{if(row.closest(".fw-friend-profile__hero"))return;row.onclick=()=>editFriendField(friend,row.dataset.edit,{name:"备注",nickname:"昵称",wechatId:"微信号",region:"地区",signature:"标签/签名"}[row.dataset.edit]);});
    const hero=page.querySelector(".fw-friend-profile__hero");
    if(hero)hero.addEventListener("click",event=>{if(event.target.closest(".fw-friend-profile__avatar-button"))return;event.preventDefault();event.stopPropagation();openFriendInfoEditor(friend);});
    page.querySelector("[data-action=message]").onclick=()=>{closeFriendProfile();openLocalFriendConversation(friend);}; page.querySelector("[data-action=call]").onclick=()=>showToast("音视频通话功能已打开");
    page.querySelector(".fw-friend-profile__moments-row")?.addEventListener("click",event=>{event.preventDefault();event.stopPropagation();openFriendMoments(friend);});
  }  function findFriendFromElement(target){
    if(!(target instanceof Element))return null;
    // Resolve against the same merged address book the 通讯录 screen renders from
    // (saved profiles + local friends + chat rows), so tapping any contact row lands
    // on that exact contact - never the first friend whose name happens to appear in
    // an ancestor's aggregated text.
    const names=(collectContacts()||[]).map(entry=>String(entry?.name||'').trim()).filter(Boolean);
    if(!names.length)return null;
    let node=target;
    for(let depth=0;node&&depth<8;depth++,node=node.parentElement){
      if(node===document.body||node===document.documentElement)break;
      const text=String(node.textContent||'').replace(/\s+/g,' ').trim();
      if(!text||text.length>180)continue;
      const exact=names.find(name=>name===text);
      if(exact){const hit=resolveFriendByName(exact);if(hit)return hit;}
      // A real contact row owns exactly one avatar image; the surrounding list and
      // page containers aggregate every row's text and avatars into a single node.
      // Requiring one image stops those containers from being mistaken for a contact,
      // so a tap on the bottom tab bar no longer opens a random friend's profile
      // while the address book is on screen. Prefer the longest matching name so a
      // short nickname cannot shadow a longer contact that contains it.
      if((node.querySelectorAll?.('img').length||0)===1){
        let hit='';
        for(const name of names){if((text.startsWith(name)||text.includes(name))&&name.length>hit.length)hit=name;}
        if(hit){const friend=resolveFriendByName(hit);if(friend)return friend;}
      }
    }
    return null;
  }  function openFriendAvatarPicker(friend){
    const layer=document.createElement("div");layer.className="fw-friend-avatar-picker";
    layer.innerHTML="<div class=fw-friend-avatar-picker__panel><header><button type=button data-cancel>取消</button><strong>选择头像</strong><button type=button data-refresh>换一批</button></header><p>头像来自头像接口，也可以从设备相册上传</p><label class=fw-friend-avatar-picker__upload>从相册选择<input type=file accept=image/*></label><div class=fw-friend-avatar-picker__grid></div></div>";
    const grid=layer.querySelector(".fw-friend-avatar-picker__grid");
    const choose=value=>{if(!value)return;friend.avatar=freezeRemoteAvatar(value)||value;syncFriendEdit(friend);layer.remove();showToast("头像已更新");};
    const render=()=>{grid.replaceChildren();for(let i=0;i<8;i++){const b=document.createElement("button"),img=document.createElement("img");b.type="button";b.className="fw-friend-avatar-choice";img.alt="头像";img.src=remoteAvatarUrl();img.onerror=()=>{img.style.visibility="hidden"};b.appendChild(img);b.onclick=()=>choose(img.currentSrc||img.src);grid.appendChild(b)}};
    layer.querySelector("[data-cancel]").onclick=()=>layer.remove();layer.querySelector("[data-refresh]").onclick=render;
    layer.querySelector("input[type=file]").onchange=e=>{const file=e.target.files?.[0];if(!file)return;const reader=new FileReader();reader.onload=()=>choose(String(reader.result||""));reader.readAsDataURL(file)};
    layer.onclick=e=>{if(e.target===layer)layer.remove()};document.body.appendChild(layer);render();
  }  function friendFromConversationRoute(){
    const localPage=document.querySelector('.fw-local-conversation');
    if(localPage?.dataset.friendId){
      const localFriend=getFriendById(localPage.dataset.friendId);
      if(localFriend)return localFriend;
    }
    const m=String(location.hash||"").match(/^#\/wechat\/conversation\/([^/?#]+)/);if(!m)return null;const id=decodeURIComponent(m[1]);let friend=getFriendById(id)||nativeProfileToFriend(nativeProfileForRoute(id));if(friend)return friend;const title=String(document.querySelector(".fw-local-conversation__header strong")?.textContent||document.querySelector("h1")?.textContent||"").trim();if(title){friend=(Array.isArray(state.contacts)?state.contacts:[]).find(x=>friendLabel(x)===title)||null;if(friend)return friend;try{const raw=JSON.parse(localStorage.getItem(NATIVE_PROFILES_KEY)||"[]");const list=Array.isArray(raw)?raw:(Array.isArray(raw?.value)?raw.value:[]);const profile=list.find(p=>String(p?.nickname||p?.remark||"").trim()===title);if(profile)return nativeProfileToFriend(profile);}catch(_){}}return null;}
  function handleFriendProfileClick(event){
    const target=event.target instanceof Element?event.target:null;
    const isContacts=/contacts|address/.test(String(location.hash||""));
    if(isContacts && recentContactGesture && Date.now()-recentContactGesture.endedAt<650){
      const tappedFriend=findFriendFromElement(target);
      if(tappedFriend && recentContactGesture.moved){
        event.preventDefault();event.stopImmediatePropagation();return true;
      }
    }
    if(suppressNextContactClick && isContacts){
      suppressNextContactClick=false; clearTimeout(suppressContactClickTimer);
      const draggedFriend=findFriendFromElement(target);
      if(draggedFriend){event.preventDefault();event.stopImmediatePropagation();return true;}
    }
    const a=target?.closest(".fw-local-conversation .fw-chat-message__avatar:not([data-fw-self-avatar=\"1\"])");
    if(a){const f=getFriendById(a.closest(".fw-local-conversation")?.dataset.friendId);if(f&&!f.payAssistant){event.preventDefault();event.stopImmediatePropagation();openFriendProfile(f);return true;}}
    if(!document.querySelector(".fw-friend-profile-page")&&!document.querySelector(".fw-friend-avatar-picker")&&/contacts|address/.test(String(location.hash||""))){const f=findFriendFromElement(event.target);if(f){event.preventDefault();event.stopImmediatePropagation();openFriendProfile(f);return true;}}
    return false;
  }  function onFriendProfileTouchEnd(event){ /* Profile navigation is click-only; never activate on press/release while scrolling. */
  }  function onClick(event) {
    if (openDiscoverMomentsIfTapped(event)) return;
    if(handleFriendProfileClick(event)) return;
    const plus = findPlusIcon(event.target);
    if (plus) {
      event.preventDefault(); event.stopImmediatePropagation();
      openPlusMenu(plus);
      return;
    }
    const topbar = event.target instanceof Element ? event.target.closest('.wechat-topbar') : null;
    const topbarActions = event.target instanceof Element ? event.target.closest('.wechat-topbar__actions') : null;
    if (topbar && !topbarActions && !popupKind) {
      event.preventDefault(); event.stopImmediatePropagation();
      openToolsSheet();
      return;
    }
    const row = findRow(event.target);
    if (row && suppressNextChatGestureClick) {
      suppressNextChatGestureClick = false;
      event.preventDefault(); event.stopImmediatePropagation();
      return;
    }
    // WebView may retarget the release click to the full-screen overlay because it
    // now covers the source row. Consume only that click near the original press
    // point; do not block taps on the context-menu items themselves.
    const releaseClickOnSource = row && row === suppressClickRow;
    const releaseClickOnOverlay = popupKind === 'context'
      && !event.target.closest('.fw-context-panel')
      && suppressClickPoint
      && Math.abs(event.clientX - suppressClickPoint.x) <= 24
      && Math.abs(event.clientY - suppressClickPoint.y) <= 24;
    if (Date.now() < suppressClickUntil
      && (releaseClickOnSource || releaseClickOnOverlay)) {
      suppressClickUntil = 0;
      suppressClickRow = null;
      suppressClickPoint = null;
      // The click synthesized when the finger is released after a long press
      // must not be treated as a tap on the dimmed overlay (which would close
      // the menu immediately). Keep the menu open for the user to choose an item.
      event.preventDefault(); event.stopImmediatePropagation();
      return;
    }
    if (popupKind === 'plus' || popupKind === 'context') {
      if (!event.target.closest('.fw-plus-panel, .fw-context-panel')) {
        event.preventDefault(); event.stopImmediatePropagation(); closeOverlay(); return;
      }
    }
    if (row?.dataset.fwLocalFriend === '1') {
      const friend = (Array.isArray(state.contacts) ? state.contacts : []).find(item => item?.id === row.dataset.fwFriendId);
      if (friend) {
        event.preventDefault(); event.stopImmediatePropagation();
        openFriendConversation(friend);
        return;
      }
    }
    if (row) {
      const info = getChatInfo(row);
      if (row.dataset.fwLocalGroup === '1' && row.dataset.fwGroupId) {
        event.preventDefault();
        event.stopImmediatePropagation();
        markChatRead(row);
        prepareConversationTransition();
        // 2026-10-10: routing a local group to fake-world's own conversation page made
        // its message bubbles look up sender profiles that do not exist ("Cannot
        // destructure property 'avatarInfo' of ..."), which surfaced as 出错啦. Local
        // groups open the same local conversation page friends use instead.
        const group = (Array.isArray(state.groups) ? state.groups : [])
          .find(item => item && String(item.id) === String(row.dataset.fwGroupId));
        if (group) openGroupConversation(group);
        return;
      }
      // Clear unread state before starting the route transition so the
      // avatar and bottom-tab badges disappear on the same tap.
      markChatRead(row);
      prepareConversationTransition();
    }
  }

  function onContextMenu(event) {
    // Disable Android WebView's native callout; the custom menu uses the same ~500 ms threshold as the native bridge.
    if (findRow(event.target)) event.preventDefault();
  }

  // Native fallback for Android WebView builds that emit touchcancel before the
  // browser-side long-press timer can complete. Coordinates are physical WebView
  // pixels and are converted to the current CSS viewport before hit-testing.
  window.__fwNativeOpenChatContextAt = function (physicalX, physicalY, physicalWidth, physicalHeight) {
    if (popupKind || !physicalWidth || !physicalHeight) return false;
    const cssX = physicalX * innerWidth / physicalWidth;
    const cssY = physicalY * innerHeight / physicalHeight;
    const target = document.elementFromPoint(cssX, cssY);
    // A long press on a group message avatar opens the "replace sender" sheet instead of
    // the message action menu (the avatar exposes the hook while the conversation is open).
    const avatarTarget = target instanceof Element ? target.closest('.fw-local-conversation .fw-chat-message__avatar') : null;
    if (avatarTarget && typeof avatarTarget.__fwOpenGroupSenderPicker === 'function') {
      avatarTarget.__fwOpenGroupSenderPicker();
      return true;
    }
    const messageRow = target instanceof Element ? target.closest('.fw-local-conversation__messages .fw-chat-message') : null;
    if (messageRow && messageRow.__fwMessage && typeof openMessageMenuForRow === 'function') {
      if (openMessageMenuForRow(messageRow)) return true;
    }
    const row = findRow(target);
    if (!row) return false;
    if (activePress) {
      clearTimeout(activePress.timer);
      activePress.fired = true;
      clearPressedRow(activePress);
      activePress = null;
    }
    suppressClickUntil = Date.now() + 350;
    suppressClickRow = row;
    suppressClickPoint = { x: cssX, y: cssY };
    openContextMenu(row, cssX);
    return true;
  };

  function setMyProfileAvatar(value) {
    if (!value || typeof value !== 'string') return false;
    try { return window.__fwSetMyProfileAvatar?.(value) === true; } catch (_) { return false; }
  }

  function openMyAvatarPicker() {
    if (document.querySelector('.fw-my-avatar-picker')) return;
    const backdrop = document.createElement('div');
    backdrop.className = 'fw-my-avatar-picker';
    backdrop.innerHTML = '<section class="fw-my-avatar-picker__panel" role="dialog" aria-modal="true" aria-label="选择头像"><header><button type="button" data-avatar-close aria-label="关闭">取消</button><strong>选择头像</strong><button type="button" data-avatar-refresh>换一批</button></header><div class="fw-my-avatar-picker__hint">选择头像库图片，或从手机上传</div><div class="fw-my-avatar-picker__grid"></div><button type="button" class="fw-my-avatar-picker__upload">从手机相册选择</button><input class="fw-my-avatar-picker__file" type="file" accept="image/*" hidden></section>';
    const close = () => backdrop.remove();
    const grid = backdrop.querySelector('.fw-my-avatar-picker__grid');
    const loadAvatarChoices = () => {
      grid.replaceChildren();
      for (let i = 0; i < 12; i++) {
        const image = document.createElement('img');
        image.alt = '头像候选 ' + (i + 1);
        image.loading = 'eager';
        image.decoding = 'async';
        image.referrerPolicy = 'no-referrer';
        // Use a bundled portrait first. Remote avatar responses can be empty or blocked inside WebView; local assets make preview immediate and guarantee that the saved avatar is exactly the previewed avatar.
        const fallbackCandidate = avatarUrl((i % 7) + 1);
        image.src = fallbackCandidate;
        image.dataset.frozenAvatar = '';
        image.dataset.apiAvatar = '1';
        image.addEventListener('error', () => { image.src = fallbackCandidate; image.dataset.frozenAvatar = fallbackCandidate; image.dataset.apiAvatar = '0'; });
        // The selectable preview is sourced from the requested xxapi.cn random-avatar API.
        // A bundled image remains only as an offline/error fallback so the picker never appears blank.
        fetchAvatarApiUrl().then(url => {
          if (!image.isConnected) return;
          image.src = url;
          image.dataset.frozenAvatar = url;
          image.dataset.apiAvatar = '1';
        }).catch(() => { image.dataset.frozenAvatar = fallbackCandidate; image.dataset.apiAvatar = '0'; });
        const selectAvatar = event => {
          event.preventDefault();
          event.stopPropagation();
          // Save the same local resource that is currently visible, never a fresh
          // random response from the API. Handling down/up as well as click makes
          // selection reliable on Android WebView touch dispatch.
          let frozenData = image.dataset.frozenAvatar || '';
          if (!frozenData && String(image.src || '').startsWith('data:image/')) frozenData = image.src;
          if (frozenData && setMyProfileAvatar(frozenData)) {
            try { localStorage.setItem('fake-world.wechat.self-avatar.v1', frozenData); } catch (_) { /* atom storage remains authoritative */ }
            close(); showToast('头像已更新');
          } else showToast('头像保存失败，请重试');
        };
        image.addEventListener('pointerdown', selectAvatar, true);
        image.addEventListener('touchstart', selectAvatar, { capture: true, passive: false });
        image.addEventListener('click', selectAvatar, true);
        grid.appendChild(image);
      }
    };
    backdrop.addEventListener('click', event => {
      if (event.target === backdrop || event.target.closest('[data-avatar-close]')) close();
      if (event.target.closest('[data-avatar-refresh]')) loadAvatarChoices();
    });
    const fileInput = backdrop.querySelector('.fw-my-avatar-picker__file');
    backdrop.querySelector('.fw-my-avatar-picker__upload').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        const source = new Image();
        source.onload = () => {
          const scale = Math.min(1, 512 / Math.max(source.width, source.height));
          const canvas = document.createElement('canvas');
          canvas.width = Math.max(1, Math.round(source.width * scale));
          canvas.height = Math.max(1, Math.round(source.height * scale));
          canvas.getContext('2d').drawImage(source, 0, 0, canvas.width, canvas.height);
          if (setMyProfileAvatar(canvas.toDataURL('image/jpeg', 0.84))) { close(); showToast('头像已更新'); }
          else showToast('头像保存失败，请换一张图片');
        };
        source.onerror = () => showToast('无法读取这张图片');
        source.src = String(reader.result || '');
      };
      reader.onerror = () => showToast('无法读取这张图片');
      reader.readAsDataURL(file);
    });
    document.body.appendChild(backdrop);
    loadAvatarChoices();
  }

  function openMyProfileTextEditor(field) {
    if (document.querySelector('.fw-my-profile-editor')) return;
    document.querySelectorAll('[data-fw-profile-edit-target], [data-fw-my-avatar-hit-target]').forEach(node => { node.dataset.fwHiddenByEditor = '1'; node.style.display = 'none'; });
    const profile = typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : {};
    const fields = {
      nickname: { title: '修改名字', label: '名字', value: profile.nickname || '', max: 40, placeholder: '请输入名字' },
      wechat: { title: '修改微信号', label: '微信号', value: profile.wechat || '', max: 40, placeholder: '请输入微信号' },
      signature: { title: '修改个性签名', label: '个性签名', value: profile.tickleText || profile.signature || '', max: 80, placeholder: '介绍一下自己' },
    };
    const config = fields[field]; if (!config) return;
    const overlay = document.createElement('div'); overlay.className = 'fw-my-profile-editor';
    overlay.innerHTML = `<form class="fw-my-profile-editor__panel"><header><button type="button" data-cancel>取消</button><strong>${config.title}</strong><button type="submit">保存</button></header><label>${config.label}<input name="value" maxlength="${config.max}" placeholder="${config.placeholder}"></label><p>修改后会同步显示在个人资料、微信主页和聊天页面</p></form>`;
    const form = overlay.querySelector('form'), input = form.querySelector('input'); input.value = String(config.value);
    let closed = false;
    const close = () => {
      if (closed) return;
      closed = true;
      overlay.remove();
      setTimeout(() => {
        document.querySelectorAll('[data-fw-profile-edit-target][data-fw-hidden-by-editor], [data-fw-my-avatar-hit-target][data-fw-hidden-by-editor]').forEach(node => {
          delete node.dataset.fwHiddenByEditor;
          node.style.display = 'block';
        });
      }, 80);
    };
    // 2026-10-19: 以前在 overlay 的捕获阶段 stopPropagation，连面板里的
    // 「取消 / 保存」按钮都收不到点击，表现就是只能保存、无法取消。
    // 改成冒泡阶段拦截：按钮先执行，再把事件挡在下层页面之前。
    ['pointerdown', 'pointerup', 'touchstart', 'touchend', 'click'].forEach(type => {
      overlay.addEventListener(type, e => { e.stopPropagation(); });
    });
    overlay.addEventListener('click', e => { if (e.target === overlay) close(); });
    form.querySelector('[data-cancel]').addEventListener('click', close);
    form.addEventListener('submit', e => {
      e.preventDefault();
      const value = input.value.trim();
      if (!value && field !== 'signature') { input.focus(); input.setCustomValidity('内容不能为空'); input.reportValidity(); input.setCustomValidity(''); return; }
      const patch = field === 'nickname' ? { nickname: value } : field === 'wechat' ? { wechat: value } : { tickleText: value, signature: value };
      if (typeof window.__fwUpdateMyProfile !== 'function' || !window.__fwUpdateMyProfile(patch)) { showToast('保存失败，请重试'); return; }
      window.dispatchEvent(new CustomEvent('fw:my-profile-updated', { detail: patch }));
      close(); showToast('资料已更新');
    });
    document.body.appendChild(overlay); input.focus(); input.select();
  }
  function isMyProfileEditRoute() { return location.href.includes('/wechat/my/profile-edit') || /个人信息/.test(document.body?.innerText || ''); }
  let avatarTapLockUntil = 0;
  function handleMyAvatarTap(event) {
    if (!isMyProfileEditRoute() || document.querySelector('.fw-my-avatar-picker') || document.querySelector('.fw-my-profile-editor')) return;
    // 「我的二维码」是独立整页，别被这里"点哪都当换头像"的逻辑接走。
    if (myQrRowFromEvent(event)) return;
    const target = event.target instanceof Element ? event.target : null;
    const x = Number(event.clientX || 0), y = Number(event.clientY || 0);
    const candidates = [...document.querySelectorAll('img')].map(image => {
      const rect = image.getBoundingClientRect();
      const size = Math.max(rect.width, rect.height);
      const inside = target === image || !!target?.closest?.('img') || (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom);
      const distance = Math.hypot((rect.left + rect.right) / 2 - x, (rect.top + rect.bottom) / 2 - y);
      return { image, rect, size, inside, distance };
    }).filter(item => item.size >= 32 && item.size <= 180 && Math.abs(item.rect.width - item.rect.height) < 18);
    const image = candidates.find(item => item.inside)?.image || candidates.sort((a,b) => a.distance - b.distance)[0]?.image;
    if (!image) return;
    const rect = image.getBoundingClientRect();
    const near = target === image || !!target?.closest?.('img') || (x >= rect.left - 12 && x <= rect.right + 12 && y >= rect.top - 12 && y <= rect.bottom + 12);
    if (!near && event.type !== 'pointerup' && event.type !== 'touchend') return;
    if (Date.now() < avatarTapLockUntil) return;
    avatarTapLockUntil = Date.now() + 500;
    event.preventDefault();
    event.stopImmediatePropagation();
    openMyAvatarPicker();
  }
  function findMyAvatarRow(){ if(!isMyProfileEditRoute()) return null; const label=[...document.querySelectorAll('body *')].find(n=>!n.children.length && String(n.textContent||'').trim()==='头像'); if(!label)return null; let row=label; for(let i=0;row&&i<7;i++,row=row.parentElement){const r=row.getBoundingClientRect?.();if(r&&r.width>=innerWidth*.65&&r.height>=55&&r.height<=260&&r.top<innerHeight*.3&&r.bottom>innerHeight*.08)return row;} return label.parentElement||label; }
  function handleMyAvatarRowTap(e){if(!isMyProfileEditRoute()||document.querySelector('.fw-my-avatar-picker')||document.querySelector('.fw-my-profile-editor'))return;if(myQrRowFromEvent(e))return;const row=findMyAvatarRow();if(!row)return;const r=row.getBoundingClientRect(),t=e.target instanceof Element?e.target:null,x=Number(e.clientX||0),y=Number(e.clientY||0),inside=row.contains(t)||(x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom),right=x>=r.left+r.width*.48;if(!inside||(!right&&!t?.closest?.('img')))return;if(Date.now()<avatarTapLockUntil)return;avatarTapLockUntil=Date.now()+450;e.preventDefault();e.stopImmediatePropagation();openMyAvatarPicker();}
  // 2026-10-19: 旧的 document 级「头像附近/整行右半边」热区会让个人信息页
  // 点返回也弹换头像。现在只保留 bindMyAvatarTargets 对头像 img 本体的绑定，
  // 其它位置一律不再触发换头像。
  function ensureMyAvatarHitTarget() {
    const route = isMyProfileEditRoute();
    const old = document.querySelector('[data-fw-my-avatar-hit-target]');
    if (!route) { old?.remove(); return; }
    const label = [...document.querySelectorAll('body *')].find(n => !n.children.length && String(n.textContent || '').trim() === '头像');
    if (!label) return;
    let row = label;
    for (let i = 0; row && i < 7; i++, row = row.parentElement) {
      const r = row.getBoundingClientRect?.();
      if (r && r.width >= innerWidth * .65 && r.height >= 55 && r.height <= 260 && r.top < innerHeight * .45) break;
    }
    const rr = row?.getBoundingClientRect?.();
    if (!rr || rr.width < innerWidth * .65) return;
    let target = old;
    if (!target) {
      target = document.createElement('button');
      target.type = 'button';
      target.setAttribute('aria-label', '更换头像');
      target.dataset.fwMyAvatarHitTarget = '1';
      const open = e => { e.preventDefault(); e.stopPropagation(); openMyAvatarPicker(); };
      target.addEventListener('pointerdown', open, true);
      target.addEventListener('touchstart', open, { capture: true, passive: false });
      target.addEventListener('click', open, true);
      document.body.appendChild(target);
    }
    Object.assign(target.style, { position:'fixed', left:Math.max(0,rr.left+rr.width*.48)+'px', top:Math.max(0,rr.top)+'px', width:Math.max(80,rr.width*.52)+'px', height:Math.max(55,rr.height)+'px', zIndex:'10040', background:'transparent', border:'0', padding:'0', margin:'0', opacity:'0.01', display:'block', touchAction:'manipulation' });
  }
  function ensureMyProfileEditTargets() {
    if (!isMyProfileEditRoute()) {
      document.querySelectorAll('[data-fw-profile-edit-target]').forEach(node => node.remove());
      return;
    }
    if (document.querySelector('.fw-my-profile-editor')) {
      document.querySelectorAll('[data-fw-profile-edit-target], [data-fw-my-avatar-hit-target]').forEach(node => { node.dataset.fwHiddenByEditor = '1'; node.style.display = 'none'; });
      return;
    }
    for (const field of ['nickname', 'wechat']) {
      const labelText = field === 'nickname' ? '名字' : '微信号';
      const label = [...document.querySelectorAll('body *')].find(n => !n.children.length && String(n.textContent || '').trim() === labelText);
      if (!label) continue;
      let row = label;
      for (let i = 0; row && i < 7; i++, row = row.parentElement) {
        const r = row.getBoundingClientRect?.();
        if (r && r.width >= innerWidth * .65 && r.height >= 45 && r.height <= 180) break;
      }
      const rr = row?.getBoundingClientRect?.();
      if (!rr || rr.width < innerWidth * .65) continue;
      let target = document.querySelector('[data-fw-profile-edit-target="' + field + '"]');
      if (!target) {
        target = document.createElement('button');
        target.type = 'button';
        target.dataset.fwProfileEditTarget = field;
        target.setAttribute('aria-label', '修改' + labelText);
        const open = event => { event.preventDefault(); event.stopPropagation(); event.stopImmediatePropagation(); openMyProfileTextEditor(field); };
        target.addEventListener('pointerdown', open, true);
        target.addEventListener('touchstart', open, { capture: true, passive: false });
        target.addEventListener('click', open, true);
        document.body.appendChild(target);
      }
      Object.assign(target.style, { position:'fixed', left:Math.max(0,rr.left)+'px', top:Math.max(0,rr.top)+'px', width:Math.max(100,rr.width)+'px', height:Math.max(45,rr.height)+'px', zIndex:'10030', background:'transparent', border:'0', padding:'0', margin:'0', opacity:'0.01', display:'block', touchAction:'manipulation' });
    }
  }
  function bindMyAvatarTargets() {
    // 清掉旧版本留下的整行透明热区，避免它继续挡返回键。
    document.querySelectorAll('[data-fw-my-avatar-hit-target]').forEach(node => node.remove());
    if (!isMyProfileEditRoute() || !document.body?.innerText?.includes('头像')) return;
    if (document.querySelector('.fw-my-avatar-picker')) return;
    for (const image of document.querySelectorAll('img')) {
      if (image.__fwAvatarBound) continue;
      const rect = image.getBoundingClientRect();
      if (rect.width < 32 || rect.height < 32 || Math.abs(rect.width - rect.height) > 24) continue;
      // 只绑个人信息页顶部的头像本体；返回键、名字行、整行都不再算。
      if (rect.top > innerHeight * 0.4) continue;
      image.__fwAvatarBound = true;
      const open = event => {
        if (document.querySelector('.fw-my-avatar-picker')) return;
        event.preventDefault(); event.stopPropagation(); event.stopImmediatePropagation();
        openMyAvatarPicker();
      };
      image.addEventListener('click', open, true);
      image.addEventListener('pointerup', open, true);
      image.addEventListener('touchend', open, true);
    }
  }
  setInterval(bindMyAvatarTargets, 250);
  setInterval(ensureMyProfileEditTargets, 120);

  /* ---------------------------------------------------------------------------
     我 → 个人信息 → 我的二维码
     整页从右侧滑入（和账单统计那种 translateX 进场一致）；点二维码本身可以
     从手机相册上传一张图片替换它。版式按用户给的 1440x3168 参考截图等比换算到
     本页 360 宽的页内坐标系（下面注释里的数值就是换算后的 CSS px）。
     --------------------------------------------------------------------------- */
  const MY_QR_CUSTOM_KEY = 'fake-world.wechat.my-qr.v1';
  let myQrPage = null;
  let myQrStyleIndex = 0;
  const MY_QR_STYLES = [
    // 参考图里微信自己那张码就是"圆点蓝"，所以默认样式直接对齐它。
    { name: '经典蓝', color: '#3490db', dot: true },
    { name: '微信绿', color: '#21a75b', dot: false },
    { name: '石墨黑', color: '#44464a', dot: false },
    { name: '深蓝圆点', color: '#1f6fb2', dot: true },
  ];
  const MY_QR_FALLBACK_AVATAR = apiAvatarFor('my-qr-avatar');

  function myQrStoredImage() {
    try {
      const value = localStorage.getItem(MY_QR_CUSTOM_KEY);
      return typeof value === 'string' && value.indexOf('data:image/') === 0 ? value : '';
    } catch (_) { return ''; }
  }
  function storeMyQrImage(value) {
    try {
      if (value) localStorage.setItem(MY_QR_CUSTOM_KEY, value);
      else localStorage.removeItem(MY_QR_CUSTOM_KEY);
    } catch (_) { /* 私密模式下存不进去也不影响本次显示 */ }
  }

  /** 稳定伪随机：同一个微信号每次画出来的码点一样，不会每开一次就变一个样。 */
  function myQrRandom(seed) {
    const text = String(seed == null ? '' : seed);
    let hash = 2166136261;
    for (let i = 0; i < text.length; i += 1) {
      hash ^= text.charCodeAt(i);
      hash = Math.imul(hash, 16777619) >>> 0;
    }
    return () => {
      hash ^= hash << 13; hash >>>= 0;
      hash ^= hash >>> 17;
      hash ^= hash << 5; hash >>>= 0;
      return hash / 4294967296;
    };
  }

  function myQrRoundRect(ctx, x, y, w, h, r) {
    const radius = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + w - radius, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
    ctx.lineTo(x + w, y + h - radius);
    ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
    ctx.lineTo(x + radius, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.closePath();
  }

  /** 微信味儿的二维码：蓝底码点 + 三个定位角 + 中间那个微信小标志。 */
  function myQrCodeDataUrl(style, seed) {
    // 尺寸对齐参考图：37 个码元铺满 180px 的框（参考图那张码没有白边），
    // 码点之间留缝、四角带圆角，和微信自己那张"圆点蓝"一致。
    const modules = 37;
    const quiet = 0;
    const unit = 22;
    const size = (modules + quiet * 2) * unit;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) return '';
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, size, size);
    const random = myQrRandom(seed);
    // 参考图中间那个微信标志方块约 9 个码元宽。
    const logoModules = 9;
    const logoStart = Math.round((modules - logoModules) / 2);
    const logoEnd = logoStart + logoModules;
    const inFinder = (x, y) =>
      (x < 8 && y < 8) || (x >= modules - 8 && y < 8) || (x < 8 && y >= modules - 8);
    const inLogo = (x, y) => x >= logoStart - 1 && x < logoEnd + 1 && y >= logoStart - 1 && y < logoEnd + 1;
    ctx.fillStyle = style.color;
    for (let y = 0; y < modules; y += 1) {
      for (let x = 0; x < modules; x += 1) {
        if (inFinder(x, y) || inLogo(x, y)) continue;
        // 定位角附近的时序线：横竖各一条黑白相间，让整张码看起来更像真的。
        let filled;
        if (y === 6 || x === 6) filled = (x + y) % 2 === 0;
        else filled = random() < 0.43;
        if (!filled) continue;
        const px = (x + quiet) * unit;
        const py = (y + quiet) * unit;
        if (style.dot) {
          const inset = unit * 0.09;
          myQrRoundRect(ctx, px + inset, py + inset, unit - inset * 2, unit - inset * 2, unit * 0.22);
          ctx.fill();
        } else {
          ctx.fillRect(px, py, unit, unit);
        }
      }
    }
    const finder = (ox, oy) => {
      const x = (ox + quiet) * unit;
      const y = (oy + quiet) * unit;
      myQrRoundRect(ctx, x, y, unit * 7, unit * 7, unit * 1.5);
      ctx.fill();
      ctx.fillStyle = '#ffffff';
      myQrRoundRect(ctx, x + unit, y + unit, unit * 5, unit * 5, unit * 0.9);
      ctx.fill();
      ctx.fillStyle = style.color;
      myQrRoundRect(ctx, x + unit * 2, y + unit * 2, unit * 3, unit * 3, unit * 0.6);
      ctx.fill();
    };
    finder(0, 0);
    finder(modules - 7, 0);
    finder(0, modules - 7);
    // 中间的微信标志：蓝色圆角方块 + 两个白色气泡。
    const logo = logoModules * unit;
    const logoX = (logoStart + quiet) * unit;
    const logoY = (logoStart + quiet) * unit;
    ctx.fillStyle = style.color;
    myQrRoundRect(ctx, logoX, logoY, logo, logo, logo * 0.26);
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    const bubble = (bx, by, bw, bh, br, tailX) => {
      myQrRoundRect(ctx, bx, by, bw, bh, br);
      ctx.fill();
      ctx.beginPath();
      ctx.moveTo(tailX, by + bh - 1);
      ctx.lineTo(tailX + bw * 0.2, by + bh - 1);
      ctx.lineTo(tailX + bw * 0.06, by + bh + bh * 0.24);
      ctx.closePath();
      ctx.fill();
    };
    const bigW = logo * 0.5, bigH = logo * 0.4;
    bubble(logoX + logo * 0.1, logoY + logo * 0.17, bigW, bigH, logo * 0.12, logoX + logo * 0.17);
    const smallW = logo * 0.44, smallH = logo * 0.35;
    bubble(logoX + logo * 0.46, logoY + logo * 0.44, smallW, smallH, logo * 0.11, logoX + logo * 0.72);
    ctx.fillStyle = style.color;
    const eye = (ex, ey, r) => { ctx.beginPath(); ctx.arc(ex, ey, r, 0, Math.PI * 2); ctx.fill(); };
    eye(logoX + logo * 0.27, logoY + logo * 0.36, logo * 0.037);
    eye(logoX + logo * 0.43, logoY + logo * 0.36, logo * 0.037);
    eye(logoX + logo * 0.62, logoY + logo * 0.61, logo * 0.033);
    eye(logoX + logo * 0.75, logoY + logo * 0.61, logo * 0.033);
    return canvas.toDataURL('image/png');
  }

  function myQrProfile() {
    let profile = null;
    try { profile = window.__fwGetMyProfile?.() || null; } catch (_) { profile = null; }
    const data = profile && typeof profile === 'object' ? profile : {};
    let avatar = '';
    const raw = data.avatarInfo;
    if (typeof raw === 'string') avatar = raw.trim();
    else if (raw && typeof raw === 'object') avatar = String(raw.url || raw.src || raw.uri || '').trim();
    return {
      name: String(data.nickname || data.name || '我').trim() || '我',
      area: String(data.area || '').trim(),
      avatar,
      seed: String(data.wechat || data.id || data.nickname || 'wechat'),
    };
  }

  async function myQrAvatarUrl(value) {
    const source = value || MY_QR_FALLBACK_AVATAR;
    try {
      if (typeof window.__fwResolveProfileAvatar === 'function') {
        const resolved = await window.__fwResolveProfileAvatar(source);
        if (resolved) return String(resolved);
      }
    } catch (_) { /* 解析失败就用原图地址 */ }
    return source;
  }

  /** 相册图片压到 720 以内再存，避免 localStorage 被一张原图撑爆。 */
  function myQrImageFromFile(file) {
    return new Promise(resolve => {
      if (!file || !file.type || file.type.indexOf('image/') !== 0) { resolve(''); return; }
      const reader = new FileReader();
      reader.onerror = () => resolve('');
      reader.onload = () => {
        const image = new Image();
        image.onerror = () => resolve('');
        image.onload = () => {
          try {
            const scale = Math.min(1, 720 / Math.max(1, image.width, image.height));
            const canvas = document.createElement('canvas');
            canvas.width = Math.max(1, Math.round(image.width * scale));
            canvas.height = Math.max(1, Math.round(image.height * scale));
            canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
            let data = canvas.toDataURL('image/png');
            if (data.length > 900000) data = canvas.toDataURL('image/jpeg', 0.92);
            resolve(data);
          } catch (_) { resolve(String(reader.result || '')); }
        };
        image.src = String(reader.result || '');
      };
      reader.readAsDataURL(file);
    });
  }

  async function saveMyQrImage(dataUrl) {
    if (!dataUrl) { showToast('保存失败，请稍后再试'); return; }
    const attempt = () => {
      try { return String(window.DoutuApp?.saveImageToGallery?.(dataUrl) || ''); } catch (_) { return ''; }
    };
    let result = attempt();
    if (result === 'denied') {
      const granted = await new Promise(resolve => {
        const timer = setTimeout(() => resolve(false), 30000);
        window.__fwSavePermissionResult = ok => { clearTimeout(timer); resolve(!!ok); };
        try { window.DoutuApp?.requestSaveImagePermission?.(); } catch (_) { clearTimeout(timer); resolve(false); }
      });
      if (granted) result = attempt();
    }
    if (result === 'denied') showToast('没有相册权限，无法保存');
    else showToast(result ? '已保存到相册' : '保存失败，请稍后再试');
  }

  function closeMyQrPage() {
    const page = myQrPage && myQrPage.isConnected ? myQrPage : document.querySelector('.fw-my-qr');
    myQrPage = null;
    if (!page) return false;
    page.classList.remove('is-open');
    setTimeout(() => { if (page.isConnected) page.remove(); }, 320);
    return true;
  }

  function openMyQrPage() {
    if (document.querySelector('.fw-my-qr')) return;
    const profile = myQrProfile();
    const page = document.createElement('section');
    page.className = 'fw-my-qr';
    page.setAttribute('aria-label', '我的二维码');
    page.innerHTML = ''
      + '<header class="fw-my-qr__header"><button type="button" class="fw-my-qr__back" aria-label="返回">'
      + '<img src="./assets/my-qr/icon-back.png" alt=""></button>'
      + '<h1>我的二维码</h1></header>'
      + '<div class="fw-my-qr__scroll">'
      + '<section class="fw-my-qr__card fw-my-qr__card--main">'
      + '<div class="fw-my-qr__title-row">'
      + '<img class="fw-my-qr__badge" src="./assets/my-qr/icon-person-add.png" alt="">'
      + '<b>添加我为朋友</b>'
      + '<button type="button" class="fw-my-qr__icon-btn fw-my-qr__dots" aria-label="更多">'
      + '<img src="./assets/my-qr/icon-more.png" alt=""></button>'
      + '<button type="button" class="fw-my-qr__icon-btn fw-my-qr__collapse" aria-label="收起">'
      + '<img src="./assets/my-qr/icon-chevron-up.png" alt=""></button>'
      + '</div>'
      // 这一整块（头像/名字/二维码/提示）就是上传图片要顶掉的范围：
      // 「添加我为朋友」下面 → 「扫一扫 换个样式 保存图片」上面。
      + '<div class="fw-my-qr__media" role="button" tabindex="0" aria-label="点击可从相册上传图片">'
      + '<div class="fw-my-qr__identity"><img class="fw-my-qr__avatar" alt="">'
      + '<div class="fw-my-qr__identity-copy"><strong class="fw-my-qr__name"></strong><small class="fw-my-qr__area"></small></div></div>'
      + '<button type="button" class="fw-my-qr__code" tabindex="-1" aria-hidden="true">'
      + '<img class="fw-my-qr__code-img" alt="我的二维码"></button>'
      + '<p class="fw-my-qr__hint">扫二维码，添加我为朋友。</p>'
      + '<img class="fw-my-qr__media-img" alt="我的二维码"></div>'
      + '<div class="fw-my-qr__actions">'
      + '<button type="button" data-act="scan">扫一扫</button><i aria-hidden="true"></i>'
      + '<button type="button" data-act="style">换个样式</button><i aria-hidden="true"></i>'
      + '<button type="button" data-act="save">保存图片</button></div>'
      + '<input class="fw-my-qr__file" type="file" accept="image/*" hidden>'
      + '</section>'
      + '<section class="fw-my-qr__card fw-my-qr__card--row">'
      + '<img class="fw-my-qr__badge" src="./assets/my-qr/icon-face-photo.png" alt="">'
      + '<div class="fw-my-qr__row-copy"><b>面对面收照片和文件</b><small>扫二维码，给我发照片、视频和文件。</small></div>'
      + '<button type="button" class="fw-my-qr__icon-btn fw-my-qr__collapse" aria-label="展开">'
      + '<img src="./assets/my-qr/icon-chevron-down.png" alt=""></button>'
      + '</section>'
      + '<section class="fw-my-qr__card fw-my-qr__card--row fw-my-qr__card--single">'
      + '<img class="fw-my-qr__badge" src="./assets/my-qr/icon-face-redpacket.png" alt="">'
      + '<div class="fw-my-qr__row-copy"><b>面对面收红包</b></div>'
      + '<button type="button" class="fw-my-qr__icon-btn fw-my-qr__dots" aria-label="更多">'
      + '<img src="./assets/my-qr/icon-more.png" alt=""></button>'
      + '<button type="button" class="fw-my-qr__icon-btn fw-my-qr__collapse" aria-label="展开">'
      + '<img src="./assets/my-qr/icon-chevron-down.png" alt=""></button>'
      + '</section>'
      + '</div>';
    document.body.appendChild(page);
    myQrPage = page;
    const $ = selector => page.querySelector(selector);
    $('.fw-my-qr__name').textContent = profile.name;
    $('.fw-my-qr__area').textContent = profile.area || '中国大陆';
    const avatar = $('.fw-my-qr__avatar');
    let avatarFallbackUsed = false;
    avatar.addEventListener('error', () => {
      if (avatarFallbackUsed) return;
      avatarFallbackUsed = true;
      avatar.src = MY_QR_FALLBACK_AVATAR;
    });
    const paintCode = () => {
      const custom = myQrStoredImage();
      const generated = myQrCodeDataUrl(MY_QR_STYLES[myQrStyleIndex], profile.seed) || MY_QR_FALLBACK_AVATAR;
      $('.fw-my-qr__code-img').src = custom || generated;
      if (custom) $('.fw-my-qr__media-img').src = custom;
      page.classList.toggle('fw-my-qr--custom', !!custom);
    };
    paintCode();
    myQrAvatarUrl(profile.avatar).then(url => {
      if (!page.isConnected) return;
      avatar.src = url || MY_QR_FALLBACK_AVATAR;
    });
    $('.fw-my-qr__back').addEventListener('click', event => { event.preventDefault(); closeMyQrPage(); });
    // 点二维码 → 从手机相册挑一张图替换它。
    const file = $('.fw-my-qr__file');
    const pick = event => {
      if (event) { event.preventDefault(); event.stopPropagation(); }
      file.click();
    };
    // 整块内容都可以点：点哪儿都是从相册选图。
    $('.fw-my-qr__media').addEventListener('click', pick);
    file.addEventListener('change', async () => {
      const chosen = file.files && file.files[0];
      file.value = '';
      if (!chosen) return;
      const data = await myQrImageFromFile(chosen);
      if (!data) { showToast('这张图片读不出来，换一张试试'); return; }
      storeMyQrImage(data);
      paintCode();
      showToast('二维码已更换，点「换个样式」可恢复默认');
    });
    page.querySelectorAll('[data-act]').forEach(button => {
      button.addEventListener('click', () => {
        const act = button.dataset.act;
        if (act === 'scan') { showToast('演示页面，不调用摄像头'); return; }
        if (act === 'save') { saveMyQrImage($('.fw-my-qr__code-img').src); return; }
        if (myQrStoredImage()) {
          storeMyQrImage('');
          myQrStyleIndex = 0;
          paintCode();
          showToast('已恢复默认二维码');
          return;
        }
        myQrStyleIndex = (myQrStyleIndex + 1) % MY_QR_STYLES.length;
        paintCode();
        showToast('已切换为' + MY_QR_STYLES[myQrStyleIndex].name + '样式');
      });
    });
    page.querySelectorAll('.fw-my-qr__collapse').forEach(button => {
      button.addEventListener('click', () => showToast('演示页面'));
    });
    page.querySelectorAll('.fw-my-qr__dots').forEach(button => {
      button.addEventListener('click', () => showToast('演示页面'));
    });
    // 先让浏览器算完初始的 translateX(100%)，再切到 0，才会有从右往左的进场。
    void page.offsetWidth;
    requestAnimationFrame(() => page.classList.add('is-open'));
  }

  /** 个人信息页那一行「我的二维码」：任意位置按下都进整页二维码。 */
  function myQrRowFromEvent(event) {
    const target = event.target instanceof Element ? event.target : null;
    if (!target || !isMyProfileEditRoute()) return null;
    for (let node = target, depth = 0; node && depth < 6; node = node.parentElement, depth += 1) {
      if (String(node.textContent || '').trim() === '我的二维码') return node;
    }
    return null;
  }
  document.addEventListener('click', event => {
    if (document.querySelector('.fw-my-qr')) return;
    if (!myQrRowFromEvent(event)) return;
    event.preventDefault();
    event.stopPropagation();
    openMyQrPage();
  }, true);

  function isStaticDiscoverRoute() {
    const raw = String(location.hash || '');
    const path = raw.replace(/^#/, '').split(/[?#]/)[0].replace(/\/+$/, '');
    return path === '/wechat/discover' || path === '/wechat/discover/index';
  }
  function isStaticMyRoute() {
    const raw = String(location.hash || '');
    const path = raw.replace(/^#/, '').split(/[?#]/)[0].replace(/\/+$/, '');
    return path === '/wechat/my' || path === '/wechat/my/index';
  }
  // 2026-10-19: 通讯录的滚动区是 flex column，应用把每个分组/字母段当成 flex item。
  // 默认 flex-shrink:1 会在内容超高时把所有分组压扁到几十像素，行与行叠在一起。
  // 这里只给通讯录路由打 body 标记，由 CSS 取消分组的 shrink，让滚动区真正滚动。
  function isContactsRoute() {
    const raw = String(location.hash || '');
    const path = raw.replace(/^#/, '').split(/[?#]/)[0].replace(/\/+$/, '');
    return path === '/wechat/contacts' || path === '/wechat/contacts/index';
  }
  function isStaticTabRoute() { return isStaticDiscoverRoute() || isStaticMyRoute(); }
  // Any element that visually belongs to a fixed-height tab (the WeChat page
  // container or the app root) must not scroll vertically.
  function isStaticTabScrollTarget(node) {
    for (let el = node; el && el !== document.body; el = el.parentElement) {
      if (el.classList?.contains('wechat-scroll-area')) return true;
      if (el.classList?.contains('wechat-content')) return true;
      if (el.classList?.contains('wechat-page')) break;
    }
    return false;
  }
  function blockStaticTabVerticalScroll(event) {
    if (!isStaticTabRoute()) return;
    if (event.type === 'wheel' && Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
    if (isStaticTabScrollTarget(event.target)) {
      if (event.cancelable) event.preventDefault();
      event.stopPropagation();
    }
  }
  function syncStaticTabScrollPolicy() {
    if (!document.body) return;
    document.body.classList.toggle('fw-static-discover-tab', isStaticDiscoverRoute());
    document.body.classList.toggle('fw-static-my-tab', isStaticMyRoute());
    document.body.classList.toggle('fw-contacts-tab', isContactsRoute());
    // 朋友圈是覆盖在「发现」上的全屏页，底栏不应该跟进来。
    document.body.classList.toggle('fw-moments-open', !!document.querySelector('.fw-moments-feed, .fw-moments-page'));
    // 设备登录选择是底部弹层，微信底栏 z-index 极高，必须暂时隐藏，否则会盖住最后一项。
    document.body.classList.toggle('fw-device-sheet-open', !!document.querySelector('.fw-device-sheet'));
  }
  function init() {
    if (!document.body) { document.addEventListener('DOMContentLoaded', init, { once: true }); return; }
    try {
      const savedScale = localStorage.getItem('fake-world.wechat.font-scale');
      if (savedScale) applyFontScale(savedScale);
    } catch (_) { /* no-op */ }
    syncStaticTabScrollPolicy();
    const style = document.createElement('style'); style.textContent = 'body,body *{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important} input,textarea,[contenteditable=\"true\"]{-webkit-user-select:text!important;user-select:text!important}'; document.head.appendChild(style);
        function navPressedElement(target) {
      let el = target;
      for (let i = 0; el && i < 6; i++, el = el.parentElement) {
        const rect = el.getBoundingClientRect?.();
        const text = (el.textContent || '').trim();
        if (rect && rect.bottom >= window.innerHeight - 150 && rect.top < window.innerHeight &&
            (text === '微信' || text === '通讯录' || text === '发现' || text === '我' ||
             /通讯录|发现/.test(text) && text.length < 12)) return el;
      }
      return null;
    }
    function clearNavPressed(el) {
      if (!el) return;
      el.classList.remove('fw-nav-pressed');
      el.style.removeProperty('background-color');
      el.style.removeProperty('color');
    }
    function onNavTouchStart(event) {
      const touch = event.changedTouches?.[0];
      const el = navPressedElement(event.target);
      if (!el || !touch) return;
      el.classList.add('fw-nav-pressed');
      el.style.setProperty('background-color', '#ededed', 'important');
      el.style.setProperty('color', 'inherit', 'important');
      el.__fwNavPress = true;
    }
    function onNavTouchEnd(event) {
      const el = navPressedElement(event.target);
      if (el?.__fwNavPress) { clearNavPressed(el); el.__fwNavPress = false; }
      document.querySelectorAll('.fw-nav-pressed').forEach(clearNavPressed);
    }
    document.addEventListener('touchstart', onTouchStart, { capture: true, passive: true });
    document.addEventListener('touchmove', onTouchMove, { capture: true, passive: true });
    document.addEventListener('touchstart', onNavTouchStart, { capture: true, passive: true });
    document.addEventListener('touchend', onNavTouchEnd, { capture: true, passive: true });
    document.addEventListener('touchcancel', onNavTouchEnd, { capture: true, passive: true });
    document.addEventListener('touchend', onTouchEnd, true);
    document.addEventListener('touchcancel', onTouchCancel, true);
    document.addEventListener('touchstart', event => { clearTimeout(suppressContactClickTimer); suppressNextContactClick=false; recentContactGesture=null; const t=event.changedTouches?.[0]; if(t)contactPress={id:'touch',x:t.clientX,y:t.clientY,moved:false,startedAt:Date.now()}; }, {capture:true,passive:true});
    document.addEventListener('touchmove', event => { const t=event.changedTouches?.[0]; if(contactPress?.id==='touch'&&t&&Math.hypot(t.clientX-contactPress.x,t.clientY-contactPress.y)>10)contactPress.moved=true; }, {capture:true,passive:true});
    document.addEventListener('touchend', () => { if(contactPress?.id==='touch'){recentContactGesture={moved:contactPress.moved,duration:Date.now()-contactPress.startedAt,endedAt:Date.now()};suppressNextContactClick=contactPress.moved;contactPress=null;if(suppressNextContactClick)suppressContactClickTimer=setTimeout(()=>{suppressNextContactClick=false;},650);} }, {capture:true,passive:true});
    document.addEventListener('touchcancel', () => { if(contactPress?.id==='touch'){recentContactGesture={moved:contactPress.moved,duration:Date.now()-contactPress.startedAt,endedAt:Date.now()};suppressNextContactClick=contactPress.moved;contactPress=null;if(suppressNextContactClick)suppressContactClickTimer=setTimeout(()=>{suppressNextContactClick=false;},650);} }, {capture:true,passive:true});
    document.addEventListener('pointerdown', event => { if(event.pointerType!=='touch'){clearTimeout(suppressContactClickTimer);suppressNextContactClick=false;recentContactGesture=null;contactPress={id:event.pointerId,x:event.clientX,y:event.clientY,moved:false,startedAt:Date.now()};} }, true);
    document.addEventListener('pointermove', event => { if(contactPress&&event.pointerId===contactPress.id&&Math.hypot(event.clientX-contactPress.x,event.clientY-contactPress.y)>10)contactPress.moved=true; }, true);
    document.addEventListener('pointerup', event => { if(contactPress&&event.pointerId===contactPress.id){recentContactGesture={moved:contactPress.moved,duration:Date.now()-contactPress.startedAt,endedAt:Date.now()};suppressNextContactClick=contactPress.moved;contactPress=null;if(suppressNextContactClick)suppressContactClickTimer=setTimeout(()=>{suppressNextContactClick=false;},650);} }, true);
    document.addEventListener('pointercancel', () => { if(contactPress){recentContactGesture={moved:contactPress.moved,duration:Date.now()-contactPress.startedAt,endedAt:Date.now()};suppressNextContactClick=contactPress.moved;contactPress=null;if(suppressNextContactClick)suppressContactClickTimer=setTimeout(()=>{suppressNextContactClick=false;},650);} }, true);
    document.addEventListener('pointerdown', onPointerDown, true);
    document.addEventListener('pointermove', onPointerMove, true);
    document.addEventListener('pointerup', onPointerUp, true);
    document.addEventListener('pointercancel', onPointerUp, true);
    document.addEventListener('click', onNativePlusClick, true);
    document.addEventListener('click', onClick, true);
    // The bundled discover row can swallow its click (pointer handlers run first), so the
    // 朋友圈 row is also matched on pointerup.
    document.addEventListener('pointerup', openDiscoverMomentsIfTapped, true);
    document.addEventListener('contextmenu', onContextMenu, true);
    // Hard-block vertical scrolling on Discover and Me: CSS handles the layout,
    // but a capture-phase listener makes sure no swipe or wheel can move them.
    document.addEventListener('touchmove', blockStaticTabVerticalScroll, { capture: true, passive: false });
    document.addEventListener('wheel', blockStaticTabVerticalScroll, { capture: true, passive: false });
    window.addEventListener('hashchange', () => {
      syncStaticTabScrollPolicy();
      setTimeout(applyConversationFontScale, 80); setTimeout(handleNativeFriendRoute, 0); setTimeout(handleNativeFriendRoute, 120);
    });
    // The router may switch tabs without firing hashchange; poll as a fallback so
    // the lock never depends on a single event source.
    setInterval(syncStaticTabScrollPolicy, 250);
    setTimeout(handleNativeFriendRoute, 0);
    window.addEventListener('scroll', syncUnreadBadges, true);
    window.addEventListener('resize', syncUnreadBadges, { passive: true });
    observer = new MutationObserver(queueApply);
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    // allProfiles is initialized by the bundled app shortly after this override starts.
    let addressBookRetries = 0;
    const addressBookTimer = setInterval(() => {
      addressBookRetries++;
      const ready = syncNativeAddressBook();
      // The app dispatches an explicit bridge-ready event; keep a short,
      // low-frequency fallback rather than rebuilding profiles every 100ms.
      if (ready || addressBookRetries >= 20) clearInterval(addressBookTimer);
    }, 250);
    // Freeze the first visible batch before rows bind their <img src>; this prevents
    // xxapi 302 avatars from visibly changing on every 小微主页 open.
    try { stabilizeSavedAvatars(24); } catch (_) {}
    queueApply();
    scheduleAvatarStabilization();
  }

  const AVATAR_CACHE_PREFIX = 'https://fake-world.local/__avatar_cache/';
  function isRemoteAvatarValue(value) {
    return typeof value === 'string' && /^https?:\/\//i.test(value) && !value.startsWith(AVATAR_CACHE_PREFIX);
  }
  function freezeRemoteAvatar(value) {
    if (!value || typeof value !== 'string' || value.startsWith('data:image/') || value.startsWith('./') || value.startsWith(AVATAR_CACHE_PREFIX)) return value;
    const key = 'fake-world.wechat.avatar-cache.' + value;
    try {
      const remembered = localStorage.getItem(key) || '';
      if (remembered.startsWith(AVATAR_CACHE_PREFIX) || remembered.startsWith('data:image/')) return remembered;
    } catch (_) {}
    try {
      const cached = window.DoutuApp?.downloadAvatarCacheUrl?.(value) || '';
      if (cached.startsWith(AVATAR_CACHE_PREFIX)) {
        try { localStorage.setItem(key, cached); } catch (_) {}
        return cached;
      }
    } catch (_) {}
    try {
      const frozen = window.DoutuApp?.downloadAvatarDataUrl?.(value) || '';
      if (frozen.startsWith('data:image/')) { try { localStorage.setItem(key, frozen); } catch (_) {} ; return frozen; }
      return value;
    } catch (_) { return value; }
  }
  function getStableFriendAvatar(friend, fallback) {
    const seed = String(friend?.name || friend?.id || 'friend');
    let source = friend?.avatar || fallback || apiAvatarFor(seed);
    if (!friend?.avatar && (!fallback || source === CONTACT_FALLBACKS[0].avatar)) {
      source = apiAvatarFor(seed);
    }
    if (!friend) return source;
    if (!isRemoteAvatarValue(source)) return source;
    const frozen = freezeRemoteAvatar(source);
    if (frozen && frozen !== source && (frozen.startsWith(AVATAR_CACHE_PREFIX) || frozen.startsWith('data:image/'))) {
      friend.avatar = frozen;
      try { saveState(); } catch (_) {}
      try { syncNativeAddressBook(); } catch (_) {}
      return frozen;
    }
    return source;
  }
  // The bundled contacts still carry their original CDN avatar URLs, which no longer
  // resolve from the device. The app keeps decoded copies in a local cache, so when a
  // friend's primary avatar source fails to load, ask the very same resolver the rest
  // of the app uses before falling back to a bundled portrait.
  function applyResolvedAvatar(imgEl, value, fallback = CONTACT_FALLBACKS[0].avatar) {
    if (!imgEl) return;
    const fb = fallback || CONTACT_FALLBACKS[0].avatar;
    imgEl.onerror = () => {
      imgEl.onerror = null;
      let resolved = null;
      try { resolved = window.__fwResolveProfileAvatar?.(value); } catch (_) { resolved = null; }
      if (resolved && typeof resolved.then === 'function') {
        resolved.then(src => {
          if (imgEl.isConnected) imgEl.src = (src && src !== imgEl.src) ? src : fb;
        }).catch(() => { if (imgEl.isConnected) imgEl.src = fb; });
        return;
      }
      if (imgEl.isConnected) imgEl.src = (typeof resolved === 'string' && resolved && resolved !== imgEl.src) ? resolved : fb;
    };
  }
  function stabilizeSavedAvatars(limit = 8) {
    let pending = false;
    try {
      let contactsChanged = false;
      let frozenCount = 0;
      if (Array.isArray(state.contacts)) {
        for (const friend of state.contacts) {
          if (!friend || !isRemoteAvatarValue(friend.avatar)) continue;
          if (frozenCount >= limit) { pending = true; continue; }
          frozenCount++;
          const frozen = freezeRemoteAvatar(friend.avatar);
          if (frozen !== friend.avatar && (frozen.startsWith(AVATAR_CACHE_PREFIX) || frozen.startsWith('data:image/'))) { friend.avatar = frozen; contactsChanged = true; }
          else pending = true;
        }
        if (contactsChanged) { saveState(); syncNativeAddressBook(); queueApply(); }
      }
      const profile = typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : null;
      const current = profile?.avatarInfo;
      if (isRemoteAvatarValue(current)) {
        const frozen = freezeRemoteAvatar(current);
        if (frozen.startsWith(AVATAR_CACHE_PREFIX) || frozen.startsWith('data:image/')) {
          window.__fwSetMyProfileAvatar?.(frozen);
          try { localStorage.setItem('fake-world.wechat.self-avatar.v1', frozen); } catch (_) {}
        } else pending = true;
      }
    } catch (_) { pending = true; }
    return pending;
  }
  function scheduleAvatarStabilization() {
    let runs = 0;
    const tick = () => {
      runs++;
      const pending = stabilizeSavedAvatars(10);
      if (pending && runs < 40) setTimeout(tick, runs < 4 ? 700 : 1600);
    };
    setTimeout(tick, 650);
    setTimeout(() => stabilizeSavedAvatars(16), 3200);
  }

  // ---- WeChat home "device logged in" row -------------------------------------
  // The row is rendered by the bundled build from its own atom. We keep the
  // chosen device in localStorage and re-apply it whenever the row re-renders,
  // touching only the span text and the existing <svg>/<path> attributes so
  // React's own DOM stays structurally intact.
  const DEVICE_ROW_KEY = 'fake-world.wechat.device-row';
  const DEVICE_ROW_OPTIONS = [
    {
      value: 'iPad',
      label: 'iPad 微信已登录',
      viewBox: '0 0 72 72',
      path: 'M12 9C12 7.34315 13.3431 6 15 6H57C58.6569 6 60 7.34315 60 9V63C60 64.6569 58.6569 66 57 66H15C13.3431 66 12 64.6569 12 63V9ZM15.6 9.6V62.4H56.4V9.6H15.6ZM39 57C39 58.6569 37.6569 60 36 60C34.3431 60 33 58.6569 33 57C33 55.3431 34.3431 54 36 54C37.6569 54 39 55.3431 39 57Z'
    },
    {
      value: 'Mac',
      label: 'Mac 微信已登录',
      viewBox: '0 0 24 24',
      path: 'M2 5C2 4.44772 2.44772 4 3 4H21C21.5523 4 22 4.44772 22 5V15V16.2V18C22 18.5523 21.5523 19 21 19H15.1554L15.777 20.84C15.7944 20.8915 15.8033 20.9456 15.8033 21C15.8033 21.2761 15.5795 21.5 15.3033 21.5H8.69668C8.64226 21.5 8.58821 21.4911 8.53665 21.4737C8.27503 21.3853 8.1346 21.1016 8.22298 20.84L8.84459 19H3C2.44772 19 2 18.5523 2 18V16.2V15V5ZM3.2 16.2V17.8H9.25L9.25 17.8H14.75L14.75 17.8H20.8V16.2H3.2ZM20.8 15H3.2V5.2H20.8V15ZM10.1112 19L9.67203 20.3H14.328L13.8888 19H10.1112Z'
    },
    {
      value: 'Windows',
      label: 'Windows 微信已登录',
      viewBox: '0 0 24 24',
      path: 'M3.2 17.8V5.2H20.8V17.8H3.2ZM3 4C2.44772 4 2 4.44772 2 5V18C2 18.5523 2.44772 19 3 19H21C21.5523 19 22 18.5523 22 18V5C22 4.44772 21.5523 4 21 4H3ZM9 20.3C8.44772 20.3 8 20.7477 8 21.3V21.5H16V21.3C16 20.7477 15.5523 20.3 15 20.3H9Z'
    }
  ];
  function readDeviceRowChoice() {
    let value = '';
    try { value = localStorage.getItem(DEVICE_ROW_KEY) || ''; } catch (_) { /* no-op */ }
    if (value === 'hidden') return 'hidden';
    return DEVICE_ROW_OPTIONS.some(option => option.value === value) ? value : 'Windows';
  }
  function applyDeviceRow() {
    const choice = readDeviceRowChoice();
    // 2026-10-19: 只改 row.style.display 会被 React 重新渲染时覆盖，点顶部工具菜单 /
    // 打开设备登录弹窗后，已隐藏的横条会重新冒出来。用 body class + CSS !important
    // 锁住隐藏状态，React 怎么重绘都不会显形。
    if (document.body) document.body.classList.toggle('fw-device-row-hidden', choice === 'hidden');
    const row = document.querySelector('.wechat-device-row');
    if (!row) return;
    row.dataset.fwDeviceChoice = choice;
    const display = choice === 'hidden' ? 'none' : '';
    if (row.style.display !== display) row.style.display = display;
    if (choice === 'hidden') return;
    const option = DEVICE_ROW_OPTIONS.find(item => item.value === choice) || DEVICE_ROW_OPTIONS[2];
    const label = row.querySelector('span');
    if (label && label.textContent !== option.label) label.textContent = option.label;
    const svg = row.querySelector('svg');
    const path = svg ? svg.querySelector('path') : null;
    if (!svg || !path) return;
    if (path.getAttribute('d') !== option.path) {
      path.setAttribute('d', option.path);
      path.setAttribute('fill-rule', 'evenodd');
      path.setAttribute('clip-rule', 'evenodd');
    }
    if (svg.getAttribute('viewBox') !== option.viewBox) {
      svg.setAttribute('viewBox', option.viewBox);
      svg.setAttribute('fill', 'rgba(0, 0, 0, 0.7)');
    }
  }
  function setDeviceRowChoice(value) {
    try { localStorage.setItem(DEVICE_ROW_KEY, value); } catch (_) { /* no-op */ }
    // Keep the tools-menu toggle in sync with the row it controls.
    try { localStorage.setItem('fake-world.wechat.device-login', value === 'hidden' ? '0' : '1'); } catch (_) { /* no-op */ }
    applyDeviceRow();
  }
  function openDeviceRowSheet() {
    if (document.querySelector('.fw-device-sheet')) return;
    const current = readDeviceRowChoice();
    const layer = document.createElement('div');
    layer.className = 'fw-device-sheet';
    layer.innerHTML = '<section class="fw-device-sheet__panel" role="dialog" aria-modal="true" aria-label="设备登录显示"><header><button type="button" data-device-close>取消</button><strong>设备登录显示</strong><span></span></header><div class="fw-device-sheet__list"></div></section>';
    let closed = false;
    const close = () => {
      if (closed) return;
      closed = true;
      layer.classList.remove('is-open');
      document.body.classList.remove('fw-device-sheet-open');
      // 等滑出动画结束再移除；下面还有 240ms 兜底。
      setTimeout(() => layer.remove(), 220);
    };
    const list = layer.querySelector('.fw-device-sheet__list');
    DEVICE_ROW_OPTIONS.concat([{ value: 'hidden', label: '不显示' }]).forEach(option => {
      const item = document.createElement('button');
      item.type = 'button';
      item.className = 'fw-device-sheet__row' + (option.value === current ? ' is-active' : '');
      item.innerHTML = '<span class="fw-device-sheet__label">' + option.label + '</span>' + (option.value === current ? '<span class="fw-device-sheet__check">✓</span>' : '');
      item.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        setDeviceRowChoice(option.value);
        close();
        showToast(option.value === 'hidden' ? '已隐藏设备登录状态' : '已切换为 ' + option.label);
      });
      list.appendChild(item);
    });
    layer.querySelector('[data-device-close]').addEventListener('click', close);
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
    document.body.classList.add('fw-device-sheet-open');
    requestAnimationFrame(() => layer.classList.add('is-open'));
  }
  document.addEventListener('click', event => {
    const row = event.target instanceof Element ? event.target.closest('.wechat-device-row') : null;
    if (!row) return;
    event.preventDefault();
    openDeviceRowSheet();
  });
  let deviceRowSyncQueued = false;
  const syncDeviceRow = () => {
    if (deviceRowSyncQueued) return;
    deviceRowSyncQueued = true;
    requestAnimationFrame(() => { deviceRowSyncQueued = false; applyDeviceRow(); });
  };
  new MutationObserver(syncDeviceRow).observe(document.body, { childList: true, subtree: true });
  setTimeout(applyDeviceRow, 0);

  // Android Back closes the locally rendered WeChat pages opened straight from the app
  // shell tiles (发起群聊 / 新的朋友) instead of dropping back to the app home.
  (function installLocalPageBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      if (document.querySelector('.fw-new-friends-page') || document.querySelector('.fw-group-select-page')) {
        closeOverlay();
        return true;
      }
      // 新建账号停在「个人信息」页时，系统返回键等同点左上角返回箭头：自动保存并启用新账号。
      if (finishNewAccountSetup()) {
        try { location.hash = '#/wechat'; } catch (_) { /* no-op */ }
        return true;
      }
      return typeof previousBack === 'function' ? previousBack.apply(this, arguments) : false;
    };
  })();

  /* ---------------------------------------------------------------------------
     微信 → 切换账号

     参考图 1440x3168（= 本页 360 宽的 4 倍），下面注释里的数值都是参考图像素 / 4。

     账号数据整体放在 localStorage 的 fake-world.wechat.accounts.v1 里：
       { version, seq, current, pendingSetup,
         list:  [{ id, nickname, wechat, avatarInfo }],
         blobs: { [账号 id]: { localStorage 的键: 值 } } }
     blobs 里存的就是这个账号「那一整套世界」：聊天记录、通讯录、朋友圈、钱包都在
     里面的 fake-world.wechat.chat-actions.v1 等键里。切号 = 把当前 localStorage 打包
     塞进 blobs[current]，再把目标账号的包写回去，然后整页重载；这样两个账号的数据
     完全隔离，互相看不到。头像图片缓存是所有账号共用的（只是图片，不涉数据）。
     --------------------------------------------------------------------------- */
  const ACCOUNTS_KEY = 'fake-world.wechat.accounts.v1';
  const ACCOUNTS_AVATAR_CACHE_PREFIX = 'fake-world.wechat.avatar-cache.';
  const ACCOUNTS_FALLBACK_AVATAR = apiAvatarFor('accounts-avatar');
  let accountsPage = null;
  let accountsBusy = false;

  function readAccountsRegistry() {
    try {
      const raw = localStorage.getItem(ACCOUNTS_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.list) || !parsed.list.length) return null;
      if (!parsed.blobs || typeof parsed.blobs !== 'object') parsed.blobs = {};
      return parsed;
    } catch (_) { return null; }
  }
  function writeAccountsRegistry(state) {
    try { localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(state)); return true; }
    catch (_) { return false; }
  }
  function liveAccountProfile() {
    try { return (typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : null) || null; }
    catch (_) { return null; }
  }
  function accountAvatarValue(value) {
    if (!value) return '';
    if (typeof value === 'string') return value;
    return String(value.url || value.src || value.uri || '');
  }
  function accountDisplayName(entry) {
    const name = String((entry && (entry.nickname || entry.wechat)) || '').trim();
    return name || '微信用户';
  }
  /** 第一次打开时把「当前这个账号」登记成列表里的第一个。 */
  function ensureAccountsRegistry() {
    const existing = readAccountsRegistry();
    if (existing) return existing;
    const profile = liveAccountProfile() || {};
    const id = 'a1';
    const state = {
      version: 1,
      seq: 1,
      current: id,
      pendingSetup: '',
      list: [{
        id,
        nickname: String(profile.nickname || '').trim(),
        wechat: String(profile.wechat || '').trim(),
        avatarInfo: accountAvatarValue(profile.avatarInfo),
      }],
      blobs: {},
    };
    writeAccountsRegistry(state);
    return state;
  }
  /** 离开某个账号之前，把它当前的名字 / 微信号 / 头像记回列表里。 */
  function syncCurrentAccountMeta(state) {
    if (!state || state.pendingSetup === state.current) return;
    const entry = state.list.find(item => item.id === state.current);
    const profile = liveAccountProfile();
    if (!entry || !profile) return;
    const nickname = String(profile.nickname || '').trim();
    const wechat = String(profile.wechat || '').trim();
    const avatar = accountAvatarValue(profile.avatarInfo);
    if (nickname) entry.nickname = nickname;
    if (wechat) entry.wechat = wechat;
    if (avatar) entry.avatarInfo = avatar;
  }
  function accountAvatarUrl(value) {
    if (typeof window.__fwResolveProfileAvatar === 'function') {
      try { return Promise.resolve(window.__fwResolveProfileAvatar(value)).catch(() => ACCOUNTS_FALLBACK_AVATAR); }
      catch (_) { /* 落到下面的直出 */ }
    }
    return Promise.resolve(accountAvatarValue(value) || ACCOUNTS_FALLBACK_AVATAR);
  }
  /** 换完账号整页重载：应用的 store 是启动时读 localStorage 建起来的，必须重启一次。 */
  function reloadBundledPage() {
    try { location.reload(); } catch (_) { /* no-op */ }
    // 万一 WebView 把这次 reload 吞掉，兜底再推一次跳转（页面已经被拆掉时定时器不会跑）。
    setTimeout(() => { try { location.replace(location.href); } catch (_) { /* no-op */ } }, 700);
  }

  function closeAccountsPage() {
    const page = accountsPage && accountsPage.isConnected ? accountsPage : document.querySelector('.fw-accounts');
    accountsPage = null;
    if (!page) return false;
    page.classList.remove('is-open');
    setTimeout(() => { if (page.isConnected) page.remove(); }, 320);
    return true;
  }

  function accountCardNode(entry, isCurrent) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'fw-accounts__card' + (isCurrent ? ' is-current' : '');
    card.dataset.accountId = String(entry.id);
    const avatar = document.createElement('img');
    avatar.className = 'fw-accounts__avatar';
    avatar.alt = '';
    avatar.src = ACCOUNTS_FALLBACK_AVATAR;
    avatar.addEventListener('error', () => {
      if (avatar.src.indexOf(ACCOUNTS_FALLBACK_AVATAR) < 0) avatar.src = ACCOUNTS_FALLBACK_AVATAR;
    });
    const copy = document.createElement('div');
    copy.className = 'fw-accounts__copy';
    const name = document.createElement('b');
    name.className = 'fw-accounts__name';
    name.textContent = accountDisplayName(entry);
    const wechat = document.createElement('small');
    wechat.className = 'fw-accounts__wechat';
    wechat.textContent = String(entry.wechat || '');
    copy.append(name, wechat);
    card.append(avatar, copy);
    if (isCurrent) {
      const badge = document.createElement('span');
      badge.className = 'fw-accounts__current';
      badge.append(document.createElement('i'), document.createTextNode('当前使用'));
      card.appendChild(badge);
    }
    accountAvatarUrl(entry.avatarInfo).then(url => { if (url && avatar.isConnected) avatar.src = url; });
    return card;
  }
  function accountAddCardNode() {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'fw-accounts__card fw-accounts__card--add';
    const icon = document.createElement('img');
    icon.className = 'fw-accounts__add';
    icon.alt = '';
    // 参考图里那个虚线方框 + 加号是整块抠出来的原图。
    icon.src = './assets/accounts/icon-add-avatar.png';
    const copy = document.createElement('div');
    copy.className = 'fw-accounts__copy';
    const name = document.createElement('b');
    name.className = 'fw-accounts__name';
    name.textContent = '添加账号';
    copy.appendChild(name);
    card.append(icon, copy);
    return card;
  }

  function openAccountSwitchPage() {
    if (document.querySelector('.fw-accounts')) return true;
    const state = ensureAccountsRegistry();
    const page = document.createElement('section');
    page.className = 'fw-accounts';
    page.setAttribute('aria-label', '切换账号');
    page.innerHTML = ''
      + '<button type="button" class="fw-accounts__back" aria-label="返回">'
      + '<img src="./assets/accounts/icon-back.png" alt=""></button>'
      + '<img class="fw-accounts__logo" src="./assets/accounts/icon-wechat-logo.png" alt="">'
      + '<h1 class="fw-accounts__title">轻触头像以切换账号</h1>'
      + '<i class="fw-accounts__rule" aria-hidden="true"></i>'
      + '<div class="fw-accounts__scroll"><div class="fw-accounts__list"></div></div>';
    document.body.appendChild(page);
    accountsPage = page;
    const list = page.querySelector('.fw-accounts__list');
    const live = liveAccountProfile() || {};
    for (const entry of state.list) {
      const isCurrent = entry.id === state.current;
      // 当前账号直接显示内存里的资料，改完名字马上就能在列表里看到。
      const view = isCurrent ? {
        id: entry.id,
        nickname: String(live.nickname || entry.nickname || '').trim(),
        wechat: String(live.wechat || entry.wechat || '').trim(),
        avatarInfo: accountAvatarValue(live.avatarInfo) || entry.avatarInfo || '',
      } : entry;
      list.appendChild(accountCardNode(view, isCurrent));
    }
    list.appendChild(accountAddCardNode());
    page.querySelector('.fw-accounts__back').addEventListener('click', event => {
      event.preventDefault();
      closeAccountsPage();
    });
    list.addEventListener('click', event => {
      const card = event.target instanceof Element ? event.target.closest('.fw-accounts__card') : null;
      if (!card || !list.contains(card)) return;
      event.preventDefault();
      if (card.classList.contains('fw-accounts__card--add')) { startNewAccount(); return; }
      const id = card.dataset.accountId;
      if (!id || id === state.current) { closeAccountsPage(); return; }
      switchToAccount(id);
    });
    // 先让浏览器算完初始的 translateX(100%)，再切 0，才有从右往左的进场。
    void page.offsetWidth;
    requestAnimationFrame(() => page.classList.add('is-open'));
    return true;
  }

  function switchToAccount(id) {
    if (accountsBusy) return;
    const state = ensureAccountsRegistry();
    if (!state || !id || id === state.current) { closeAccountsPage(); return; }
    const entry = state.list.find(item => item.id === id);
    if (!entry) { closeAccountsPage(); return; }
    accountsBusy = true;
    syncCurrentAccountMeta(state);
    // 只写标记：真正的数据搬运留到新页面启动那一刻（文件顶部 applyPendingAccountSwapOnBoot）。
    // 还没填完的新账号就地作废，免得回头把别的账号的资料写进它。
    state.pendingSetup = '';
    delete state.pendingNew;
    state.pendingRestore = id;
    if (!writeAccountsRegistry(state)) {
      showToast('切换失败：本地存储空间不足');
      accountsBusy = false;
      return;
    }
    showToast('已切换到「' + accountDisplayName(entry) + '」');
    try { location.hash = '#/wechat'; } catch (_) { /* no-op */ }
    reloadBundledPage();
  }

  /** 「添加账号」：重载后是一个全新的空世界，再进个人信息页填头像 / 名字 / 微信号。 */
  function startNewAccount() {
    if (accountsBusy) return;
    const state = ensureAccountsRegistry();
    accountsBusy = true;
    syncCurrentAccountMeta(state);
    state.seq = Math.max(Number(state.seq) || 0, state.list.length) + 1;
    const id = 'a' + state.seq;
    state.list.push({
      id,
      nickname: '',
      wechat: '',
      // 新账号先给一张随机头像占位（走 API），等用户自己在个人信息页换成喜欢的。
      avatarInfo: apiAvatarFor('new-account-' + String(id)),
    });
    // 新账号的包是空的 ⇒ 启动时清空本机数据，应用重新生成一套默认世界。
    state.blobs[id] = {};
    state.pendingRestore = id;
    state.pendingNew = id;
    if (!writeAccountsRegistry(state)) {
      showToast('添加失败：本地存储空间不足');
      accountsBusy = false;
      return;
    }
    try { location.hash = '#/wechat/my/profile-edit'; } catch (_) { /* no-op */ }
    reloadBundledPage();
  }

  /** 应用重启后，把新账号的占位资料写进 store（只在第一次生效）。 */
  function applyPendingAccountSetup() {
    const state = readAccountsRegistry();
    if (!state || !state.pendingSetup || state.pendingSetup !== state.current) return;
    const entry = state.list.find(item => item.id === state.pendingSetup) || null;
    if (!entry || entry.blanked) return;
    let tries = 0;
    const timer = setInterval(() => {
      tries += 1;
      if (typeof window.__fwUpdateMyProfile !== 'function') {
        if (tries > 200) clearInterval(timer);
        return;
      }
      const patch = { nickname: String(entry.nickname || ''), wechat: String(entry.wechat || '') };
      if (entry.avatarInfo) patch.avatarInfo = entry.avatarInfo;
      let ok = false;
      try { ok = window.__fwUpdateMyProfile(patch) === true; } catch (_) { ok = false; }
      if (!ok) { if (tries > 200) clearInterval(timer); return; }
      clearInterval(timer);
      entry.blanked = true;
      writeAccountsRegistry(state);
    }, 150);
  }

  /** 新账号「个人信息」页点左上角返回箭头：把填好的资料存下来并启用。 */
  function finishNewAccountSetup() {
    const state = readAccountsRegistry();
    if (!state || !state.pendingSetup || state.pendingSetup !== state.current) return false;
    const entry = state.list.find(item => item.id === state.pendingSetup) || null;
    const profile = liveAccountProfile() || {};
    if (entry) {
      entry.nickname = String(profile.nickname || entry.nickname || '').trim();
      entry.wechat = String(profile.wechat || entry.wechat || '').trim();
      entry.avatarInfo = accountAvatarValue(profile.avatarInfo) || entry.avatarInfo || '';
      delete entry.blanked;
    }
    state.pendingSetup = '';
    writeAccountsRegistry(state);
    showToast('新账号已保存');
    return true;
  }
  function accountSetupBackFromEvent(event) {
    const state = readAccountsRegistry();
    if (!state || !state.pendingSetup || state.pendingSetup !== state.current) return false;
    const target = event.target instanceof Element ? event.target : null;
    if (!target || typeof target.closest !== 'function') return false;
    const toolbar = target.closest('.wechat-profile-toolbar');
    if (!toolbar) return false;
    const rect = target.getBoundingClientRect?.();
    // 工具栏右边是标题，只有左上角那颗返回箭头算「保存并启用」。
    if (rect && rect.width > 0 && rect.left > 90) return false;
    return true;
  }
  document.addEventListener('click', event => {
    if (!accountSetupBackFromEvent(event)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (finishNewAccountSetup()) {
      try { location.hash = '#/wechat'; } catch (_) { /* no-op */ }
    }
  }, true);
  // 万一用户从别的入口离开了个人信息页（底部 tab、手势返回等），也把资料落库，别一直挂着待保存。
  window.addEventListener('hashchange', () => {
    const state = readAccountsRegistry();
    if (!state || !state.pendingSetup) return;
    // 还没轮到用户填资料（占位资料还没写进去）就忽略这次路由变化，免得把默认值当成新账号资料存下来。
    const entry = state.list.find(item => item.id === state.pendingSetup);
    if (!entry || !entry.blanked) return;
    if (location.href.includes('/wechat/my/profile-edit')) return;
    finishNewAccountSetup();
  });
  applyPendingAccountSetup();

  init();
  const restoreSavedAvatar=()=>{try{const saved=localStorage.getItem('fake-world.wechat.self-avatar.v1')||'';if((saved.startsWith('data:image/')||saved.startsWith(AVATAR_CACHE_PREFIX))&&typeof window.__fwSetMyProfileAvatar==='function')window.__fwSetMyProfileAvatar(saved);}catch(_){}};
  setTimeout(restoreSavedAvatar,500); setTimeout(restoreSavedAvatar,1500);

})();













  window.addEventListener('fw:my-profile-updated', event => {
    const patch = event.detail || {};
    const profile = typeof window.__fwGetMyProfile === 'function' ? window.__fwGetMyProfile() : {};
    if (patch.nickname) document.querySelectorAll('.fw-chat-message__avatar[data-fwSelfAvatar="1"]').forEach(img => { img.alt = profile.nickname || patch.nickname; img.title = profile.nickname || patch.nickname; });
    if (patch.avatarInfo || patch.nickname) document.querySelectorAll('.fw-chat-message__avatar[data-fwSelfAvatar="1"]').forEach(img => {
      const value = profile.avatarInfo || '';
      img.dataset.fwAvatarValue = typeof value === 'string' ? value : (value.url || value.src || value.uri || '');
      if (typeof window.__fwResolveProfileAvatar === 'function') window.__fwResolveProfileAvatar(value).then(src => { if (src && img.isConnected) img.src = src; });
    });
  });

  // 2026-10-10: the system back button walks the local moments layers first, so leaving a
  // friend's 朋友圈 (or 发现 → 朋友圈) returns to the page that opened it instead of
  // falling through to the app home. Registered last so it runs before the profile /
  // chat-info back handlers.
  (function installMomentBackChain() {
    const previousBack = window.__fwHandleChatPayBack;
    window.__fwHandleChatPayBack = function () {
      const preview = document.querySelector('.fw-moment-preview');
      if (preview) { preview.remove(); return true; }
      const gallery = document.querySelector('.fw-gallery');
      if (gallery) { gallery.remove(); galleryState = null; return true; }
      const coverViewer = document.querySelector('.fw-cover-viewer');
      if (coverViewer) { coverViewer.remove(); return true; }
      const tlPicker = document.querySelector('.fw-tl-picker');
      if (tlPicker) { tlPicker.remove(); return true; }
      const senderPicker = document.querySelector('.fw-group-sender-picker');
      if (senderPicker) { senderPicker.remove(); return true; }
      const infoEditor = document.querySelector('.fw-friend-info-editor');
      if (infoEditor) { infoEditor.remove(); return true; }
      const commentBar = document.querySelector('.fw-tl-comment-bar');
      if (commentBar) { commentBar.remove(); return true; }
      const feedPage = document.querySelector('.fw-moments-feed');
      if (feedPage && typeof feedPage.__fwCollapseCover === 'function' && feedPage.__fwCollapseCover()) return true;
      const composer = document.querySelector('.fw-moments-composer');
      if (composer) { composer.remove(); return true; }
      const moments = document.querySelector('.fw-moments-page');
      if (moments) { moments.remove(); return true; }
      const feed = document.querySelector('.fw-moments-feed');
      if (feed) { feed.remove(); return true; }
      // 2026-10-19: 本地会话是最后打开的全屏页。返回键/左侧边缘手势必须先把聊天页
      // 收回「微信」列表，而不是退出整个微主页回到应用首页。
      const handledByInner = typeof previousBack === 'function' ? previousBack() : false;
      if (handledByInner) return true;
      if (document.querySelector('.fw-local-conversation')) { closeLocalConversation(); return true; }
      return false;
    };

    // 原生侧（iOS WebHost）直接调这两个入口，避免 Safari/WKWebView 对 SVG 的
    // click 派发不可靠时，加号 / 顶部工具菜单仍然打不开。
  })();
