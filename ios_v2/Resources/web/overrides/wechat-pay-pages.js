/* WeChat service / wallet / balance visual reconstruction. */
(function () {
  'use strict';
  const HOST_ID = 'fw-pay-pages-host';
  const routes = { service: '/wechat/service', wallet: '/wechat/wallet', balance: '/wechat/wallet/balance', payment: '/wechat/payment', bills: '/wechat/wallet/bills', withdraw: '/wechat/wallet/withdraw', cards: '/wechat/wallet/cards', cardAdd: '/wechat/wallet/cards/add' };
  let payStack = [];
  /**
   * 首页「微」宫格是原生壳直接深链进来的，没有页内父级。记下这层入口路由，
   * 这样在这一页的根部按返回键时交给原生壳退回「微」那一页，而不是逐级退到
   * 用户根本没来过的 我 → 服务 → 钱包。
   */
  let entryRootRoute = '';
  function currentRoute() {
    const path = String(location.pathname || '').replace(/\/$/, '');
    const hash = String(location.hash || '').replace(/^#/, '').replace(/\/$/, '');
    const value = hash || path;
    // 2026-10-19: 不能用 #/wechat/wallet/bills 直接跳转 —— bundled React Router 不认
    // 这个子路由，会渲染 404 错误页并把自绘账单页挤到下面。改成在已知的
    // /wechat/wallet 上带 query，App 仍渲染钱包，自绘层再覆盖成账单。
    if (/[?&]bills=1(?:&|$)/.test(value)) return routes.bills;
    // 提现也没有 bundled 路由，直接跳 /wechat/wallet/withdraw 会 404；
    // 用已知钱包路由 + query，App 正常渲染钱包，自绘提现页覆盖上去。
    if (/[?&]withdraw=1(?:&|$)/.test(value)) return routes.withdraw;
    if (/[?&]card-add=1(?:&|$)/.test(value)) return routes.cardAdd;
    if (/[?&]cards=1(?:&|$)/.test(value)) return routes.cards;
    return value.endsWith('/wechat/service') ? routes.service
      : /\/wechat\/wallet\/bills(\/|$)/.test(value) ? routes.bills
      : /\/wechat\/wallet\/cards\/add(\/|$)/.test(value) ? routes.cardAdd
      : /\/wechat\/wallet\/cards(\/|$)/.test(value) ? routes.cards
      : value.endsWith('/wechat/wallet/withdraw') ? routes.withdraw
      : value.endsWith('/wechat/wallet/balance') ? routes.balance
      : value.endsWith('/wechat/wallet') ? routes.wallet
      : value.endsWith('/wechat/payment') ? routes.payment
      : '';
  }  const esc = (v) => String(v).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const icon = (name, color) => {
    const c = color || 'currentColor';
    const paths = {
      back: '<path d="M20 5 11 12l9 7"/><path d="M12 12h12"/>',
      more: '<circle cx="6" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.5" fill="currentColor" stroke="none"/><circle cx="18" cy="12" r="1.5" fill="currentColor" stroke="none"/>',
      plus: '<path d="M12 5v14M5 12h14"/>',
      pay: '<path d="M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3Z"/><path d="M3 9h18M7 14h4"/>',
      wallet: '<path d="M4 7h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h13"/><path d="M17 13h3"/>',
      card: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/>',
      people: '<circle cx="9" cy="9" r="3"/><path d="M3 19c.4-3 2.5-5 6-5s5.6 2 6 5M16 7.5a3 3 0 0 1 0 5.8M17 15c2.2.5 3.6 1.8 4 4"/>',
      phone: '<path d="M7 3h3l2 5-2.2 1.7a14 14 0 0 0 4.5 4.5L16 12l5 2v3c0 1.1-.9 2-2 2C10.7 19 5 13.3 5 5c0-1.1.9-2 2-2Z"/>',
      service: '<path d="M5 4h14v16H5z"/><path d="M8 8h8M8 12h8M8 16h5"/>',
      shield: '<path d="m12 3 7 3v5c0 4.5-3 7.7-7 10-4-2.3-7-5.5-7-10V6l7-3Z"/><path d="m9 12 2 2 4-4"/>',
      arrow: '<path d="m9 6 6 6-6 6"/>',
      yuan: '<circle cx="12" cy="12" r="9"/><path d="M7 8h10M7 12h10M12 8v8M9 8l3 4 3-4"/>',
      diamond: '<path d="M3 8 6.5 4h11L21 8l-9 12L3 8Z"/><path d="M3 8h18M6.5 4 9 8l3 12 3-12 2.5-4"/>' ,
      scan: '<path d="M4 9V5h4M20 9V5h-4M4 15v4h4M20 15v4h-4"/><path d="m8 12 3 3 5-6"/>',
      close: '<path d="M6 6l12 12M18 6 6 18"/>',
      search: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
      help: '<circle cx="12" cy="12" r="9"/><path d="M9.6 9.3a2.5 2.5 0 0 1 4.6 1.2c0 1.7-2.3 2-2.3 3.6M12 17.2h.01"/>',
      collect: '<rect x="4" y="3.5" width="16" height="17" rx="2"/><path d="M8 8h8M8 12h8M8 16h4"/>',
      shop: '<path d="M4 8h16v12H4z"/><path d="M6.5 8V5.5h11V8M9 12h6"/>',
      receipt: '<circle cx="12" cy="12" r="9"/><path d="m12 7.6 1.5 3 3.3.5-2.4 2.3.6 3.3-3-1.6-3 1.6.6-3.3L7.2 11l3.3-.5z"/>',
      share: '<path d="M5.5 17.5V9.4A3.4 3.4 0 0 1 8.9 6h8.6"/><path d="m14 3 4 3-4 3"/>',
      chat: '<path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8.5L5.5 21v-4H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z"/>',
      swap: '<path d="M4 8.5h13M14 5.5 17 8.5l-3 3"/><path d="M20 15.5H7M10 12.5l-3 3 3 3"/>',
      caret: '<path d="m5.5 9 6.5 6.5L18.5 9"/>',
      walletCard: '<rect x="2.6" y="5.6" width="18.8" height="12.8" rx="2.2"/><circle cx="16.6" cy="12" r="2.7"/>',
      redpacket: '<rect x="5.8" y="3.2" width="12.4" height="17.6" rx="2"/><path d="M5.8 10.1 12 13.9l6.2-3.8"/><circle cx="12" cy="13.9" r="2.1"/>'
    };
    return `<svg class="fw-pay-icon" viewBox="0 0 24 24" style="color:${c}" aria-hidden="true">${paths[name] || paths.service}</svg>`;
  };
  function ensurePayLayoutStyles() {
    if (document.getElementById('fw-pay-inline-layout-styles')) return;
    const style = document.createElement('style');
    style.id = 'fw-pay-inline-layout-styles';
    style.textContent = `
      #fw-pay-pages-host .fw-pay-header { margin-top: 0 !important; }
      #fw-pay-pages-host .service-scroll { padding-left: 8px !important; padding-right: 8px !important; }
    `;
    document.head.appendChild(style);
  }
  function host() { return document.getElementById(HOST_ID); }
  function go(path) {
    const target = String(path).replace(/^#/, '').replace(/\/$/, '');
    const current = currentRoute();
    if (current === target) { render(); return; }
    if (current && current !== target) payStack.push(current);
    // 自绘子页没有 bundled 路由，统一走已知钱包路由 + query，避免 React 404。
    const hashTarget = target === routes.withdraw ? '/wechat/wallet?withdraw=1'
      : target === routes.bills ? '/wechat/wallet?bills=1'
      : target === routes.cards ? '/wechat/wallet?cards=1'
      : target === routes.cardAdd ? '/wechat/wallet?card-add=1'
      : target;
    location.hash = '#' + hashTarget;
    // hashchange owns rendering; avoid a second synchronous render.
  }  function header(title, right, back) { return `<header class="fw-pay-header"><button class="fw-pay-back" type="button" data-go="${back || '#/wechat/my'}">${icon('back')}</button><h1>${esc(title)}</h1>${right || '<span class="fw-pay-header-spacer"></span>'}</header>`; }
  /**
   * 2026-10-11: 自绘的服务页已经不再挂载（服务路由现在直接用 bundled React 那一版），
   * 这里保留整套实现，方便随时切回来：把 render() 里 service 分支去掉、恢复
   * `kind === 'service' ? servicePage() :` 即可。
   */
  function servicePage() {
    return `<main class="fw-pay-page fw-pay-service">${header('服务', `<button class="fw-pay-more" type="button" aria-label="更多">${icon('more')}</button>`, '#/wechat/my')}
      <div class="fw-pay-scroll service-scroll">
        <section class="fw-service-green-card"><button class="fw-service-card-cell" type="button" data-receive-pay="1"><span class="fw-service-big-icon">${icon('scan','#fff')}</span><b>收付款</b></button><button class="fw-service-card-cell" data-go="#/wechat/wallet"><span class="fw-service-big-icon">${icon('wallet','#fff')}</span><b>钱包</b><small>******</small></button></section>
        <section class="fw-pay-group"><h2>金融理财</h2><div class="fw-pay-grid fw-pay-grid--three"><button>${icon('card','#20b96b')}<span>信用卡还款</span></button><button>${icon('wallet','#16a9e6')}<span>理财通</span></button><button>${icon('shield','#f3a23b')}<span>保险服务</span></button></div></section>
        <section class="fw-pay-group"><h2>生活服务</h2><div class="fw-pay-grid"><button>${icon('phone','#168ee8')}<span>手机充值</span></button><button>${icon('service','#18b86b')}<span>生活缴费</span></button><button>${icon('people','#2aa6dc')}<span>Q币充值</span></button><button>${icon('service','#18b86b')}<span>城市服务</span></button><button>${icon('people','#ef6680')}<span>腾讯公益</span></button><button>${icon('service','#f3a23b')}<span>医疗健康</span></button></div></section>
        <section class="fw-pay-group"><h2>交通出行</h2><div class="fw-pay-grid"><button>${icon('people','#168ee8')}<span>出行服务</span></button><button>${icon('card','#07c160')}<span>火车票机票</span></button><button>${icon('service','#fa9d3b')}<span>滴滴出行</span></button><button>${icon('service','#07c160')}<span>酒店民宿</span></button></div></section>
        <section class="fw-pay-group"><h2>购物消费</h2><div class="fw-pay-grid"><button>${icon('service','#fa9d3b')}<span>京东购物</span></button><button>${icon('service','#07c160')}<span>美团外卖</span></button><button>${icon('service','#576b95')}<span>拼多多</span></button><button>${icon('people','#2a7fcb')}<span>更多服务</span></button></div></section>
      </div></main>`;
  }  const walletRow = (name, sub, iconName, color, action) => `<button class="fw-wallet-row" type="button" ${action ? `data-go="${action}"` : ''}><span class="fw-wallet-row-icon">${icon(iconName,color)}</span><span class="fw-wallet-row-copy"><b>${name}</b>${name === '零钱通' ? '<small>收益率1.02%</small>' : ''}</span>${name === '零钱' ? '<strong>¥' + getBalanceAmount() + '</strong>' : ''}${icon('arrow','#b2b2b2')}</button>`;
  function walletPage() {
    return `<main class="fw-pay-page fw-pay-wallet">${header('钱包','<button class="fw-wallet-bill" type="button" data-go="' + routes.bills + '">账单</button>','#/wechat/service')}
      <div class="fw-pay-scroll wallet-scroll"><section class="fw-wallet-list">${walletRow('零钱','','yuan','#efc43b','#/wechat/wallet/balance')}${walletRow('零钱通','','diamond','#efbd23')}${walletRow('银行卡','添加银行卡','card','#2a7fcb', routes.cards)}${walletRow('亲属卡','','people','#efa43b')}</section><section class="fw-wallet-list">${walletRow('数字人民币','','yuan','#e94b54')}${walletRow('支付分','','shield','#58c17e')}${walletRow('客服中心','','service','#58c17e')}</section><div class="fw-wallet-bottom"><button>身份信息</button><i></i><button>支付设置</button></div></div></main>`;
  }
  function getBalanceAmount() {
    const value = Number(localStorage.getItem('fw-balance-amount'));
    return Number.isFinite(value) && value >= 0 ? value.toFixed(2) : '256.39';
  }
  /* 2026-10-11: 服务页「钱包」预览里的金额必须和零钱一致。
     规则：本地还没设置过零钱 → 直接采用 bundled 页面上显示的金额（即 bundled wallet
     store 里的 balance，默认 520.00），这样进服务页看到的数字不会变；本地设置过 →
     以本地为准，把预览改写成同一个数；bundled 侧自己改了（编辑预览模式里的零钱余额）
     → 反过来跟随它。写入零钱编辑器的值也走同一条链，所以两边永远一致。 */
  let mirroredWalletPreview = null;
  const readBalanceRaw = () => { try { return localStorage.getItem('fw-balance-amount'); } catch (_) { return null; } };
  /** 用户在「零钱」页手动改过金额之后，bundled 侧的重渲染不许再把本地值盖回去。 */
  const BALANCE_USER_KEY = 'fw-balance-user-set';
  const balanceUserSet = () => { try { return localStorage.getItem(BALANCE_USER_KEY) === '1'; } catch (_) { return false; } };
  const parseMoneyText = text => {
    const value = Number(String(text == null ? '' : text).replace(/[^0-9.]/g, ''));
    return Number.isFinite(value) && value >= 0 ? value.toFixed(2) : null;
  };
  const storeBalanceAmount = (value, byUser) => {
    try { localStorage.setItem('fw-balance-amount', value); } catch (_) { /* 存不下就算了 */ }
    if (byUser) { try { localStorage.setItem(BALANCE_USER_KEY, '1'); } catch (_) { /* 同上 */ } }
  };
  function bundledWalletAmountNode() {
    const tile = Array.from(document.querySelectorAll('.cursor-pointer')).find(el =>
      !el.closest('#fw-pay-pages-host, .fw-receive-pay') && (el.textContent || '').trim().startsWith('钱包'));
    if (!tile) return null;
    return Array.from(tile.children).find(el => /^[¥￥]/.test((el.textContent || '').trim())) || null;
  }
  /* 2026-10-11: 服务页那张绿卡（收付款 / 钱包）要距屏幕顶端 1.7cm。
     本工程沿用"1cm ≈ 52 CSS px"（收付款页注释里 1.5cm≈78px 同源），即 88px；
     实测卡片自然位置在 45px，所以给页面根容器补一个 padding-top，让整页（含页头）
     一起下移。每次 apply() 都按实际位置重新算差值，React 重渲染后也能自己纠正。 */
  const CM_IN_CSS_PX = 52;
  const SERVICE_CARD_TOP = Math.round(1.7 * CM_IN_CSS_PX);
  function bundledServiceCard() {
    const tile = Array.from(document.querySelectorAll('.cursor-pointer')).find(el =>
      !el.closest('#fw-pay-pages-host, .fw-receive-pay') && (el.textContent || '').trim() === '收付款');
    return tile ? tile.parentElement : null;
  }
  function alignBundledServiceCard() {
    const card = bundledServiceCard();
    const scroller = card && card.parentElement;          // 卡片所在的滚动区
    const root = scroller && scroller.parentElement;       // 页面根：flex column
    const header = root && root.firstElementChild;         // 页头
    if (!root || !scroller || !header || header === scroller) return;
    // 注意：必须用"滚动无关"的量来算 —— 卡片在滚动内容里的位置 = 屏幕位置 - 滚动区位置 + 已滚动距离，
    // 否则每滑动一次都会被当成新的偏差累加进去，padding 会一直涨、把滚动区压扁。
    const inScroller = card.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop;
    const naturalTop = root.getBoundingClientRect().top + header.getBoundingClientRect().height + inScroller;
    const wanted = Math.max(0, Math.round((SERVICE_CARD_TOP - naturalTop) * 100) / 100);
    const current = Number.parseFloat(getComputedStyle(root).paddingTop) || 0;
    // 1.2px 容差：亚像素测量会来回抖 1px，别让它每次都改写 padding。
    if (Math.abs(current - wanted) < 1.2) return;
    root.style.setProperty('box-sizing', 'border-box', 'important');
    root.style.setProperty('padding-top', wanted + 'px', 'important');
    root.dataset.fwTopSpacing = '1';
  }
  function syncBundledWalletPreview() {
    const node = bundledWalletAmountNode();
    if (!node) return;
    const shown = parseMoneyText(node.textContent);
    // 本地还没设置过零钱 → 采用 bundled 页面上显示的金额（默认 520.00）；
    // 用户在「零钱」页手动改过（balanceUserSet）→ 本地值永远优先，不再跟随 bundled，
    // 否则 React 每次重渲染都会把预览写回它 store 里的旧值（用户反馈的"改完又变回 520"）。
    if (shown && !readBalanceRaw()) storeBalanceAmount(shown);
    else if (!balanceUserSet() && shown && mirroredWalletPreview !== null && shown !== mirroredWalletPreview) {
      storeBalanceAmount(shown);
    }
    const target = getBalanceAmount();
    if (shown !== target) node.textContent = '¥' + target;
    mirroredWalletPreview = target;
  }
  function balancePage() {
    // Artwork for this page is lifted from the supplied reference screenshot and
    // resampled to the device pixel ratio, so every icon keeps the original shape.
    const art = './overrides/wechat-balance-';
    return `<main class="fw-pay-page fw-pay-balance">${header('','<button class="fw-balance-detail" type="button">零钱明细</button>','#/wechat/wallet')}
      <div class="fw-balance-content"><img class="fw-balance-yuan" src="${art}yuan.png" alt="" draggable="false"><div class="fw-balance-label">我的零钱</div><div class="fw-balance-value"><button class="fw-balance-amount-button" type="button" aria-label="点击修改零钱金额"><img class="fw-balance-yen" src="${art}yen.png" alt="" draggable="false"><span class="fw-balance-amount">${getBalanceAmount()}</span></button><div class="fw-balance-editor" hidden><input class="fw-balance-input" type="number" min="0" step="0.01" inputmode="decimal" aria-label="零钱金额"><button type="button" data-balance-save>保存</button><button type="button" data-balance-cancel>取消</button></div></div><button class="fw-balance-promo" type="button"><span class="fw-promo-link">转入零钱通，能赚又能花</span><img class="fw-promo-chevron" src="${art}chevron.png" alt="" draggable="false"></button><div class="fw-balance-actions"><button>充值</button><button>提现</button></div><button class="fw-balance-help">常见问题</button></div></main>`;
  }
  function paymentPage() {
    return `<main class="fw-pay-page fw-pay-payment">${header('收付款','',routes.service)}<section class="fw-payment-content"><div class="fw-payment-tabs"><button type="button" class="is-active" data-payment-mode="pay">付款码</button><button type="button" data-payment-mode="receive">二维码收款</button></div><div class="fw-payment-card"><p class="fw-payment-hint">仅供界面演示，不会发生真实支付或资金交易</p><div class="fw-payment-code" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div><strong class="fw-payment-mode-label">付款码（模拟）</strong><label class="fw-payment-demo-amount">演示金额（可选）<input type="number" min="0" step="0.01" inputmode="decimal" placeholder="输入金额"></label><button type="button" class="fw-payment-confirm">确认演示</button></div></section></main>`;
  }
  // ---- Wallet bills ----------------------------------------------------------
  // Entries are generated from the friends already present in the simulation and
  // then stored, so every edit (amount / note / avatar) survives reloads.
  const BILLS_KEY = 'fw-wallet-bills';
  const CHAT_STATE_KEY = 'fake-world.wechat.chat-actions.v1';
  // 2026-10-17: 打包的本地头像（assets/generated-avatars）已整体删除，账单头像一律走 API。
  // 这里保留一个内联灰色占位图作为最后一层：不占安装包体积、不会加载失败。
  const BILL_PLACEHOLDER_AVATAR = (typeof window !== 'undefined' && window.__fwAvatarPlaceholder) ||
    'data:image/svg+xml;utf8,' + encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 96 96">' +
        '<rect width="96" height="96" fill="#e9ebef"/>' +
        '<circle cx="48" cy="37" r="17" fill="#b9bfc9"/>' +
        '<path d="M14 96c0-19 15-31 34-31s34 12 34 31z" fill="#b9bfc9"/>' +
      '</svg>',
    );
  // Random-head API documented at https://xxapi.cn/doc/head
  const HEAD_API_URL = 'https://v2.xxapi.cn/api/head?return=json';
  const HEAD_API_CACHE_KEY = 'fw-head-api-avatars';
  const HEAD_API_BATCH = 12;
  // Statistics-only amount overrides. Editing a composition row writes here instead
  // of touching the stored bills, so the 账单 list keeps the real records.
  const STATS_OVERRIDE_KEY = 'fw-stats-amount-overrides';
  const UUID_API_URL = 'https://v2.xxapi.cn/api/uuid?return=json';
  // Primary entropy source answers with plain text; a JSON source is kept as a
  // secondary fallback because the old randomnumberapi endpoint now returns 503.
  const BATCH_RANDOM_API_URLS = [
    'https://www.random.org/integers/?num=16&min=1&max=1000000000&col=1&base=10&format=plain&rnd=new',
    'https://www.randomnumberapi.com/api/v1.0/random?min=1&max=2147483647&count=8'
  ];
  function hashString(value) {
    let hash = 2166136261;
    const text = String(value);
    for (let i = 0; i < text.length; i++) { hash ^= text.charCodeAt(i); hash = Math.imul(hash, 16777619); }
    return hash >>> 0;
  }
  function digitsFrom(seed, length) {
    let out = '';
    let value = seed >>> 0;
    while (out.length < length) { value = (Math.imul(value ^ (value >>> 13), 1274126177) >>> 0); out += String(value % 10); }
    return out.slice(0, length);
  }
  function loadChatContacts() {
    try {
      const raw = JSON.parse(localStorage.getItem(CHAT_STATE_KEY) || '{}');
      return Array.isArray(raw.contacts) ? raw.contacts.filter(item => item && (item.remark || item.name)) : [];
    } catch (_) { return []; }
  }
  function loadBills() {
    try {
      const parsed = JSON.parse(localStorage.getItem(BILLS_KEY) || 'null');
      if (Array.isArray(parsed) && parsed.length) return parsed;
    } catch (_) { /* reseed below */ }
    return null;
  }
  function saveBills(list) {
    try { localStorage.setItem(BILLS_KEY, JSON.stringify(list)); } catch (_) { /* no-op */ }
  }
  function buildBill(seedSource, friend, index) {
    const seed = hashString(seedSource);
    const label = String(friend.remark || friend.name || '好友');
    // Pull each trait from a different part of the hash so sibling entries for the
    // same friend do not all collapse onto the same amount.
    const base = [8, 12.5, 15, 20, 29.9, 36, 50, 66.6, 88, 120, 199, 240, 300, 468, 520, 888, 1234.56, 2550][(seed >>> 7) % 18];
    const outgoing = ((seed >>> 11) & 1) === 0;
    const discounted = outgoing && ((seed >>> 17) % 3) === 0;
    // Only unsigned shifts here: a signed shift turns negative for seeds above
    // 2^31 and produced negative "savings".
    const original = Math.round(base * (1 + (((seed >>> 5) % 17) + 3) / 100) * 100) / 100;
    const now = new Date();
    const created = new Date(now.getFullYear(), now.getMonth(), now.getDate() - (seed % 45), (seed >>> 4) % 24, (seed >>> 9) % 60, (seed >>> 13) % 60).getTime();
    const title = (outgoing ? '转账-转给' : '转账-来自') + label;
    return {
      id: 'bill' + seed.toString(36) + index,
      title,
      friendName: label,
      avatar: String(friend.avatar || ''),
      amount: outgoing ? -base : base,
      originalPrice: discounted ? original : null,
      discount: discounted ? ('好友优惠¥' + (original - base).toFixed(2)) : null,
      createdAt: created,
      status: outgoing ? '已转账' : '已收钱',
      payMethod: '零钱',
      acquirer: '财付通支付科技有限公司',
      note: '微信转账',
      transferNo: '1000050001' + digitsFrom(seed, 21),
      receivedAt: created + 10 * 60 * 1000,
      tradeNo: '42000' + digitsFrom(seed, 22),
      merchantNo: '可在支持的商户扫码退款',
      barcode: digitsFrom(seed ^ 0x5bf03635, 24)
    };
  }
  // The list is cached for the lifetime of the page: reads used to re-parse the
  // stored JSON, so edits were applied to throwaway objects and never persisted.
  let billsCache = null;
  // Older builds could persist placeholder titles/notes that leaked the bulk-add
  // wording into the record list. Rewrite those fields once so historical entries
  // look like ordinary merchant / contact transactions too.
  function sanitizeStoredBills(list) {
    let changed = false;
    list.forEach(bill => {
      const blob = String(bill.title || '') + String(bill.friendName || '') + String(bill.note || '');
      if (!/\u6279\u91cf|\u6279\u91cf\u6dfb\u52a0|batch/i.test(blob)) return;
      const identity = randomBatchIdentity();
      bill.friendName = identity;
      bill.note = randomPick(BATCH_REMARKS);
      bill.title = randomBatchTitle(Number(bill.amount) || 1, identity);
      changed = true;
    });
    if (changed) saveBills(list);
    return list;
  }
  function ensureBills() {
    if (billsCache) return billsCache;
    const stored = loadBills();
    if (stored) { billsCache = sanitizeStoredBills(stored); return billsCache; }
    const list = [];
    loadChatContacts().slice(0, 10).forEach((friend, index) => {
      const key = String(friend.id || friend.name || index);
      list.push(buildBill(key + '#0', friend, index * 2));
      list.push(buildBill(key + '#1', friend, index * 2 + 1));
    });
    list.sort((a, b) => b.createdAt - a.createdAt);
    saveBills(list);
    billsCache = list;
    return billsCache;
  }
  function billsOf() { return ensureBills(); }
  function formatMoney(value) { return (value < 0 ? '-' : '+') + Math.abs(value).toFixed(2); }
  function formatShortTime(ts) {
    const d = new Date(ts);
    return (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }
  function formatFullTime(ts) {
    const d = new Date(ts);
    return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 ' +
      String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0') + ':' + String(d.getSeconds()).padStart(2, '0');
  }
  function monthKey(ts) { const d = new Date(ts); return d.getFullYear() + '-' + (d.getMonth() + 1); }
  function monthLabel(ts) { const d = new Date(ts); return d.getFullYear() + '年' + (d.getMonth() + 1) + '月'; }
  function isHttpUrl(value) { return /^https?:\/\//i.test(String(value || '')); }
  function loadHeadCache() {
    try {
      const parsed = JSON.parse(localStorage.getItem(HEAD_API_CACHE_KEY) || '{}');
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch (_) { return {}; }
  }
  function saveHeadCache(cache) {
    try { localStorage.setItem(HEAD_API_CACHE_KEY, JSON.stringify(cache)); } catch (_) { /* no-op */ }
  }
  function headCacheKey(bill) { return String((bill && (bill.id || bill.friendName)) || ''); }
  function cachedHeadFor(bill) {
    const hit = loadHeadCache()[headCacheKey(bill)];
    return isHttpUrl(hit) ? String(hit) : '';
  }
  function rememberHead(bill, url) {
    if (!isHttpUrl(url)) return;
    const cache = loadHeadCache();
    cache[headCacheKey(bill)] = String(url);
    saveHeadCache(cache);
  }
  // Calls the xxapi.cn random-head API and returns the image URL it hands back.
  function fetchRandomHead() {
    return fetch(HEAD_API_URL, { cache: 'no-store', headers: { Accept: 'application/json' } })
      .then(response => {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
      })
      .then(payload => {
        const url = payload && Number(payload.code) === 200 ? String(payload.data || '') : '';
        if (!isHttpUrl(url)) throw new Error('head api payload invalid');
        return url;
      });
  }
  function fetchHeadBatch(size) {
    const count = Math.max(1, Number(size) || HEAD_API_BATCH);
    const jobs = [];
    for (let i = 0; i < count; i++) jobs.push(fetchRandomHead().catch(() => ''));
    return Promise.all(jobs).then(list => list.filter(isHttpUrl));
  }
  function billAvatarSrc(bill) {
    // Wallet bills show avatars supplied by the xxapi.cn random-head API. A URL
    // picked by hand wins; otherwise the API URL is read from the bill, then from
    // the per-bill cache. The contact/bundled portrait is only a placeholder for
    // the brief moment before the API answers.
    if (bill.avatarLocked && bill.avatar) return String(bill.avatar);
    if (bill.avatarApi && isHttpUrl(bill.avatar)) return String(bill.avatar);
    const cached = cachedHeadFor(bill);
    if (cached) return cached;
    if (bill.avatarFriend) {
      const picked = friendAvatarFor(bill.avatarFriend);
      if (picked) return picked;
    }
    if (!bill.avatarLocked) {
      const live = friendAvatarFor(bill.friendName);
      if (live) return live;
    }
    if (bill.avatar) return bill.avatar;
    return BILL_PLACEHOLDER_AVATAR;
  }
  // Prefetches one API avatar per bill that does not have one yet, then persists
  // it so the bill keeps the same portrait on every later render.
  let headPrefetchRunning = false;
  function ensureApiBillAvatars() {
    if (headPrefetchRunning) return;
    const cache = loadHeadCache();
    const pending = billsOf().filter(bill => {
      if (bill.avatarLocked && bill.avatar) return false;
      if (bill.avatarApi && isHttpUrl(bill.avatar)) return false;
      return !isHttpUrl(cache[headCacheKey(bill)]);
    });
    if (!pending.length) return;
    headPrefetchRunning = true;
    let cursor = 0;
    let emptyBatches = 0;
    const next = () => {
      if (cursor >= pending.length) { headPrefetchRunning = false; return; }
      const chunk = pending.slice(cursor, cursor + HEAD_API_BATCH);
      cursor += chunk.length;
      fetchHeadBatch(chunk.length).then(list => {
        if (!list.length) {
          emptyBatches += 1;
          if (emptyBatches >= 2) { headPrefetchRunning = false; return; }
          next();
          return;
        }
        emptyBatches = 0;
        const map = loadHeadCache();
        chunk.forEach((bill, index) => {
          if (bill.avatarLocked && bill.avatar) return;
          const url = list[index % list.length];
          if (!isHttpUrl(url)) return;
          map[headCacheKey(bill)] = url;
          bill.avatar = url;
          bill.avatarApi = true;
        });
        saveHeadCache(map);
        saveBills(billsOf());
        refreshBillsView();
        next();
      }).catch(() => { headPrefetchRunning = false; });
    };
    next();
  }
  function friendAvatarFor(name) {
    const wanted = String(name || '').trim();
    if (!wanted) return '';
    try {
      const raw = JSON.parse(localStorage.getItem(CHAT_STATE_KEY) || '{}');
      const list = Array.isArray(raw.contacts) ? raw.contacts : [];
      const hit = list.find(item => item && String(item.remark || item.name || '').trim() === wanted);
      return hit && hit.avatar ? String(hit.avatar) : '';
    } catch (_) { return ''; }
  }
  function barcodeMarkup(number) {
    let markup = '';
    for (let i = 0; i < number.length; i++) {
      const digit = Number(number[i]);
      markup += '<i style="width:' + (6 + (digit % 2)) + 'px"></i><i class="is-gap" style="width:' + (2 + ((digit + 1) % 2)) + 'px"></i>';
    }
    return markup;
  }
  function billDetailId() {
    const match = String(location.hash || '').match(/bills\/([^/?#]+)/);
    return match ? decodeURIComponent(match[1]) : '';
  }
  function currentBill() {
    const id = billDetailId();
    return id ? (billsOf().find(item => item.id === id) || null) : null;
  }
  function refreshBillsView() {
    const page = host();
    if (page && page.dataset.fwRoute === routes.bills) render();
  }
  // The chat pushes every transfer it creates (and any later sender flip) into the
  // wallet bills so the two stay in step.
  window.__fwAddWalletBill = function (info) {
    if (!info) return '';
    const value = Number(info.amount);
    if (!Number.isFinite(value) || value === 0) return '';
    const list = ensureBills();
    const createdAt = Number(info.createdAt) || Date.now();
    const seed = hashString(String(info.title || '转账') + '#' + createdAt);
    const entry = {
      id: 'bill' + seed.toString(36) + 'x' + list.length,
      title: String(info.title || '转账'),
      friendName: String(info.friendName || '好友'),
      avatar: String(info.avatar || ''),
      amount: Math.round(value * 100) / 100,
      originalPrice: null,
      discount: null,
      createdAt,
      status: value < 0 ? '已转账' : '已收钱',
      payMethod: '零钱',
      acquirer: '财付通支付科技有限公司',
      note: '微信转账',
      transferNo: '1000050001' + digitsFrom(seed, 21),
      receivedAt: createdAt + 10 * 60 * 1000,
      tradeNo: '42000' + digitsFrom(seed, 22),
      merchantNo: '可在支持的商户扫码退款',
      barcode: digitsFrom(seed ^ 0x5bf03635, 24)
    };
    list.unshift(entry);
    saveBills(list);
    refreshBillsView();
    return entry.id;
  };
  window.__fwSyncWalletBill = function (id, patch) {
    if (!id || !patch) return false;
    const bill = billsOf().find(item => item.id === id);
    if (!bill) return false;
    Object.assign(bill, patch);
    saveBills(billsOf());
    refreshBillsView();
    return true;
  };
  function billsHeader(title, closeAttr) {
    return `<header class="fw-pay-header fw-bills-header"><button class="fw-bills-close" type="button" ${closeAttr}>${icon('close')}</button><h1>${esc(title)}</h1><button class="fw-bills-more" type="button" data-bills-more>${icon('more')}</button></header>`;
  }
  function billRow(bill) {
    const sign = bill.amount < 0 ? 'is-out' : 'is-in';
    return `<button class="fw-bills-row" type="button" data-bill="${esc(bill.id)}"><img class="fw-bills-avatar" src="${esc(billAvatarSrc(bill))}" alt=""><span class="fw-bills-copy"><b>${esc(bill.title)}</b><small>${esc(formatShortTime(bill.createdAt))}</small></span><span class="fw-bills-money"><b class="${sign}">${formatMoney(bill.amount)}</b>${bill.originalPrice ? '<small>' + bill.originalPrice.toFixed(2) + '</small>' : ''}</span></button>`;
  }
  function billsPage() {
    const groups = [];
    billsOf().forEach(bill => {
      const key = monthKey(bill.createdAt);
      let group = groups.find(item => item.key === key);
      if (!group) { group = { key, label: monthLabel(bill.createdAt), spend: 0, income: 0, rows: [] }; groups.push(group); }
      if (bill.amount < 0) group.spend += Math.abs(bill.amount); else group.income += bill.amount;
      group.rows.push(bill);
    });
    const body = groups.map(group => `<section class="fw-bills-month"><header><button type="button" class="fw-bills-month-label">${esc(group.label)}<i></i></button><span class="fw-bills-month-sum">支出¥${group.spend.toFixed(2)}&nbsp;&nbsp;收入¥${group.income.toFixed(2)}</span></header><div class="fw-bills-rows">${group.rows.map(billRow).join('')}</div></section>`).join('');
    return `<main class="fw-pay-page fw-pay-bills">${billsHeader('账单', 'data-bills-close')}<div class="fw-bills-toolbar"><button type="button" class="fw-bills-chip">全部账单<i class="fw-bills-caret"></i></button><button type="button" class="fw-bills-chip fw-bills-search">${icon('search')}<span>查找交易</span></button><button type="button" class="fw-bills-stat" data-bills-stat>收支统计${icon('arrow')}</button></div><div class="fw-pay-scroll fw-bills-scroll">${body || '<p class="fw-bills-empty">暂无账单记录</p>'}</div></main>`;
  }
  function billDetailPage(id) {
    const bill = billsOf().find(item => item.id === id);
    if (!bill) return `<main class="fw-pay-page fw-pay-bills">${billsHeader('账单详情', 'data-bills-close')}<div class="fw-pay-scroll fw-bills-scroll"><p class="fw-bills-empty">这条账单不存在</p></div></main>`;
    // Transfer receipts use their own field set (no merchant/barcode block).
    const incoming = Number(bill.amount) > 0;
    const fields = [
      ['当前状态', incoming ? '已收款' : '对方已收钱'],
      ['转账说明', String(bill.note || '微信转账'), 'note'],
      ['转账时间', formatFullTime(bill.createdAt)],
      ['收款时间', formatFullTime(Number(bill.receivedAt) || (Number(bill.createdAt) + 600000))],
      ['支付方式', String(bill.payMethod || '零钱')],
      ['转账单号', String(bill.transferNo || bill.tradeNo || '')]
    ];
    const fieldRows = fields.map(item => item[2]
      ? `<button class="fw-bill-row fw-bill-row--tap" type="button" data-bill-edit="${item[2]}"><span>${esc(item[0])}</span><b>${esc(item[1])}</b></button>`
      : `<div class="fw-bill-row"><span>${esc(item[0])}</span><b>${esc(item[1])}</b></div>`).join('');
    return `<main class="fw-pay-page fw-pay-bills fw-bill-detail"><header class="fw-pay-header fw-bills-header"><button class="fw-bills-close" type="button" data-bill-close-only>${icon('close')}</button><h1></h1><span class="fw-pay-header-spacer"></span></header><div class="fw-pay-scroll fw-bill-detail__scroll"><div class="fw-bill-detail__inner"><section class="fw-bill-hero"><button class="fw-bill-avatar-button" type="button" data-bill-edit="avatar" aria-label="点击更换头像"><img class="fw-bill-avatar" src="${esc(billAvatarSrc(bill))}" alt=""></button><button class="fw-bill-name-button" type="button" data-bill-edit="title">${esc(bill.title)}</button><button class="fw-bill-amount-button" type="button" data-bill-edit="amount" aria-label="点击修改金额"><strong>${formatMoney(bill.amount)}</strong></button></section><section class="fw-bill-group">${fieldRows}</section><section class="fw-bill-service"><h2>账单服务</h2><div class="fw-bill-service__grid"><button type="button" data-bill-tool="对订单有疑惑">${icon('help')}<span>对订单有疑惑</span></button><button type="button" data-bill-tool="定位到聊天位置">${icon('chat')}<span>定位到聊天位置</span></button><button type="button" data-bill-tool="申请转账电子凭证">${icon('receipt')}<span>申请转账电子凭证</span></button><button type="button" data-bill-tool="查看往来转账">${icon('swap')}<span>查看往来转账</span></button></div></section><p class="fw-bill-foot">本服务由财付通提供</p></div></div></main>`;
  }
  function payToast(text) {
    const host = document.createElement('div');
    host.className = 'fw-pay-toast-host';
    const toast = document.createElement('div');
    toast.className = 'fw-pay-toast';
    toast.textContent = text;
    host.appendChild(toast);
    document.body.appendChild(host);
    setTimeout(() => host.remove(), 1500);
  }
  function openBillEditor(config) {
    const layer = document.createElement('div');
    layer.className = 'fw-bill-editor';
    layer.innerHTML = `<section class="fw-bill-editor__panel" role="dialog" aria-modal="true"><header><button type="button" data-bill-editor-cancel>取消</button><strong>${esc(config.title)}</strong><button type="button" data-bill-editor-save>保存</button></header><input class="fw-bill-editor__input" type="${config.type === 'number' ? 'text' : 'text'}" inputmode="${config.type === 'number' ? 'decimal' : 'text'}" value="${esc(config.value)}"></section>`;
    const input = layer.querySelector('.fw-bill-editor__input');
    const close = () => layer.remove();
    layer.querySelector('[data-bill-editor-cancel]').addEventListener('click', close);
    layer.querySelector('[data-bill-editor-save]').addEventListener('click', () => {
      if (!config.onSave(input.value)) { input.focus(); return; }
      saveBills(billsOf());
      close();
      render();
    });
    input.addEventListener('keydown', event => { if (event.key === 'Enter') layer.querySelector('[data-bill-editor-save]').click(); if (event.key === 'Escape') close(); });
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
    setTimeout(() => { input.focus(); input.select(); }, 30);
  }
  // 2026-10-19: 金额编辑弹层。bundled 钱包/零钱页没有自己的编辑入口，
  // 这里复用账单编辑器的外观，只负责输入和保存，不触发 bills 重绘。
  function openAmountEditor(title, value, onSave) {
    const layer = document.createElement('div');
    layer.className = 'fw-bill-editor';
    layer.innerHTML = `<section class="fw-bill-editor__panel" role="dialog" aria-modal="true"><header><button type="button" data-amount-cancel>取消</button><strong>${esc(title)}</strong><button type="button" data-amount-save>保存</button></header><input class="fw-bill-editor__input" type="text" inputmode="decimal" value="${esc(value)}"></section>`;
    const input = layer.querySelector('.fw-bill-editor__input');
    const close = () => layer.remove();
    const save = () => {
      const num = Number(String(input.value || '').replace(/[^0-9.]/g, ''));
      if (!Number.isFinite(num) || num < 0) { input.focus(); return; }
      onSave(num.toFixed(2));
      close();
    };
    layer.querySelector('[data-amount-cancel]').addEventListener('click', close);
    layer.querySelector('[data-amount-save]').addEventListener('click', save);
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') save();
      if (event.key === 'Escape') close();
    });
    ['pointerdown', 'click'].forEach(type => layer.addEventListener(type, e => e.stopPropagation()));
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
    setTimeout(() => { input.focus(); input.select(); }, 30);
  }

  // Avatar sources: random heads from the xxapi.cn interface, avatars already used
  // by the chat contacts, and photos uploaded from the device gallery.
  function openAvatarPicker(onPick) {
    const layer = document.createElement('div');
    layer.className = 'fw-bill-picker';
    layer.innerHTML = '<section class="fw-bill-picker__panel" role="dialog" aria-modal="true" aria-label="选择头像"><header><button type="button" data-bill-picker-close>取消</button><strong>选择头像</strong><button type="button" data-bill-picker-refresh>换一批</button></header><div class="fw-bill-picker__tabs"><button type="button" data-picker-mode="api" class="is-active">随机头像</button><button type="button" data-picker-mode="friends">通讯录</button><button type="button" data-picker-mode="album">相册上传</button></div><div class="fw-bill-picker__hint"></div><div class="fw-bill-picker__grid"></div></section>';
    const grid = layer.querySelector('.fw-bill-picker__grid');
    const hint = layer.querySelector('.fw-bill-picker__hint');
    const refresh = layer.querySelector('[data-bill-picker-refresh]');
    const modeButtons = Array.prototype.slice.call(layer.querySelectorAll('[data-picker-mode]'));
    let mode = 'api';
    let loading = false;
    let albumPoll = null;
    const stopAlbumPoll = () => { if (albumPoll) { clearInterval(albumPoll); albumPoll = null; } };
    const finish = (src, extra) => {
      stopAlbumPoll();
      layer.remove();
      if (typeof onPick === 'function') onPick(src, extra || {});
    };
    const showMessage = (text) => {
      const empty = document.createElement('p');
      empty.className = 'fw-bill-picker__empty';
      empty.textContent = text;
      grid.appendChild(empty);
    };
    let apiRequestToken = 0;
    const renderApiGrid = () => {
      grid.replaceChildren();
      loading = true;
      showMessage('正在从随机头像接口获取...');
      const token = ++apiRequestToken;
      fetchHeadBatch(HEAD_API_BATCH).then(list => {
        // Ignore stale responses after "换一批" / a mode switch / closing the sheet.
        if (!layer.isConnected || token !== apiRequestToken || mode !== 'api') return;
        loading = false;
        grid.replaceChildren();
        if (!list.length) {
          showMessage('头像接口暂时不可用，请稍后重试');
          return;
        }
        list.forEach(src => {
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'fw-bill-picker__item';
          button.innerHTML = '<img alt="" src="' + esc(src) + '">';
          button.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            finish(src, { source: 'api' });
          });
          grid.appendChild(button);
        });
      });
    };
    const renderFriendsGrid = () => {
      grid.replaceChildren();
      const friends = loadChatContacts().filter(item => item && item.avatar).slice(0, 60);
      if (!friends.length) {
        showMessage('通讯录里还没有可用的头像');
        return;
      }
      friends.forEach(friend => {
        const name = String(friend.remark || friend.name || '');
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'fw-bill-picker__item fw-bill-picker__item--friend';
        item.innerHTML = '<img alt="" src="' + esc(String(friend.avatar)) + '"><span>' + esc(name) + '</span>';
        item.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          finish(String(friend.avatar), { source: 'friend', name: name });
        });
        grid.appendChild(item);
      });
    };
    const renderAlbumGrid = () => {
      grid.replaceChildren();
      const bridge = window.DoutuApp;
      if (!bridge || typeof bridge.getGalleryItems !== 'function') {
        showMessage('当前环境不支持读取相册');
        return;
      }
      let items = [];
      try { items = JSON.parse(bridge.getGalleryItems() || '[]'); } catch (_) { items = []; }
      if (!items.length) {
        let granted = false;
        try { granted = !!bridge.hasGalleryPermission(); } catch (_) { granted = false; }
        if (granted) {
          showMessage('相册里没有可用的照片');
          return;
        }
        showMessage('正在请求相册权限…');
        try { bridge.requestGalleryPermission(); } catch (_) { /* no-op */ }
        stopAlbumPoll();
        let tries = 0;
        albumPoll = setInterval(() => {
          tries += 1;
          let ok = false;
          try { ok = !!bridge.hasGalleryPermission(); } catch (_) { ok = false; }
          if (ok || tries >= 20) {
            stopAlbumPoll();
            if (layer.isConnected && mode === 'album') renderAlbumGrid();
          }
        }, 500);
        return;
      }
      let shown = 0;
      items.slice(0, 36).forEach(item => {
        const id = String((item && item.id) || '');
        if (!id) return;
        let thumb = '';
        try { thumb = bridge.getGalleryImage(id, 220) || ''; } catch (_) { thumb = ''; }
        if (!thumb) return;
        shown += 1;
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'fw-bill-picker__item';
        button.innerHTML = '<img alt="" src="' + esc(thumb) + '">';
        button.addEventListener('click', event => {
          event.preventDefault();
          event.stopPropagation();
          let full = '';
          try { full = bridge.getGalleryImage(id, 640) || ''; } catch (_) { full = ''; }
          finish(full || thumb, { source: 'album' });
        });
        grid.appendChild(button);
      });
      if (!shown) showMessage('相册照片读取失败，请重试');
    };
    const renderGrid = () => {
      stopAlbumPoll();
      if (mode === 'api') renderApiGrid();
      else if (mode === 'friends') renderFriendsGrid();
      else renderAlbumGrid();
    };
    const syncChrome = () => {
      modeButtons.forEach(button => button.classList.toggle('is-active', button.dataset.pickerMode === mode));
      hint.textContent = mode === 'api' ? '来自 xxapi.cn 随机头像接口'
        : mode === 'friends' ? '点击通讯录头像即可替换'
        : '点击相册照片即可上传为头像';
      refresh.style.visibility = mode === 'api' ? 'visible' : 'hidden';
      refresh.disabled = mode === 'api' && loading;
    };
    syncChrome();
    renderGrid();
    refresh.addEventListener('click', () => {
      if (mode !== 'api' || loading) return;
      syncChrome();
      renderGrid();
    });
    modeButtons.forEach(button => button.addEventListener('click', () => {
      if (mode === button.dataset.pickerMode) return;
      mode = button.dataset.pickerMode;
      loading = false;
      syncChrome();
      renderGrid();
    }));
    layer.querySelector('[data-bill-picker-close]').addEventListener('click', () => { stopAlbumPoll(); layer.remove(); });
    layer.addEventListener('pointerdown', event => { if (event.target === layer) { stopAlbumPoll(); layer.remove(); } });
    document.body.appendChild(layer);
  }
  function applyAvatarToBill(bill, src, extra) {
    bill.avatar = src;
    bill.avatarLocked = true;
    // Marks the portrait as hand-picked so the ranking can tell it apart from the
    // placeholder avatar that the random-head prefetch fills in automatically.
    bill.avatarPicked = true;
    if (isHttpUrl(src)) bill.avatarApi = true; else delete bill.avatarApi;
    if (extra && extra.name) bill.avatarFriend = extra.name; else delete bill.avatarFriend;
    rememberHead(bill, src);
  }
  // Bars react to a tap (select the month) and to a long press (edit that month's
  // amounts), so the timer is cancelled the moment the finger starts to scroll.
  function bindBarGesture(el, onTap, onLongPress) {
    let timer = null;
    let longFired = false;
    let startX = 0;
    let startY = 0;
    const cancel = () => { if (timer) { clearTimeout(timer); timer = null; } };
    el.addEventListener("pointerdown", event => {
      longFired = false;
      startX = event.clientX;
      startY = event.clientY;
      cancel();
      timer = setTimeout(() => { timer = null; longFired = true; onLongPress(); }, 520);
    });
    el.addEventListener("pointermove", event => {
      if (!timer) return;
      if (Math.abs(event.clientX - startX) > 8 || Math.abs(event.clientY - startY) > 8) cancel();
    });
    el.addEventListener("pointerup", cancel);
    el.addEventListener("pointercancel", cancel);
    el.addEventListener("pointerleave", cancel);
    el.addEventListener("click", event => {
      event.preventDefault();
      event.stopPropagation();
      if (longFired) { longFired = false; return; }
      onTap();
    });
  }
  function buildManualBill(monthKey, tab, amount) {
    const parts = String(monthKey).split("-");
    const year = Number(parts[0]) || new Date().getFullYear();
    const month = Number(parts[1]) || (new Date().getMonth() + 1);
    const created = new Date(year, month - 1, 15, 12, 0, 0).getTime();
    const outgoing = tab !== "income";
    const signed = Math.round((outgoing ? -Math.abs(amount) : Math.abs(amount)) * 100) / 100;
    return {
      id: "bill" + Date.now().toString(36) + Math.random().toString(36).slice(2, 7),
      title: outgoing ? "手动记账-支出" : "手动记账-收入",
      friendName: "手动记账",
      avatar: "",
      amount: signed,
      originalPrice: null,
      discount: null,
      createdAt: created,
      status: outgoing ? "已支付" : "已收款",
      payMethod: "零钱",
      acquirer: "财付通支付科技有限公司",
      note: "手动记账"
    };
  }
  function buildManualBillForCategory(monthKey, tab, amount, category) {
    const bill = buildManualBill(monthKey, tab, amount);
    const text = String(category || "");
    if (/红包/.test(text)) {
      bill.title = "手动记账-微信红包";
      bill.note = "红包";
    } else if (/转账/.test(text)) {
      bill.title = "手动记账-转账";
      bill.note = "转账";
    }
    return bill;
  }
  function billInMonth(bill, monthKey) {
    const d = new Date(Number(bill.createdAt));
    return !Number.isNaN(d.getTime()) && (d.getFullYear() + "-" + (d.getMonth() + 1)) === monthKey;
  }
  function billMatchesTab(bill, tab) {
    const amount = Number(bill.amount) || 0;
    if (tab === "expense") return amount < 0;
    if (tab === "income") return amount > 0;
    return amount === 0;
  }
  // Shared sheet: every bill behind the tapped element is editable and new amounts
  // can be appended, so the calling view (column height / category bar) follows.
  function openAmountEditor(opts) {
    const options = opts || {};
    const list = Array.isArray(options.list) ? options.list : [];
    const tab = options.tab || "expense";
    const layer = document.createElement('div');
    layer.className = 'fw-bill-edit';
    const rowsHtml = list.map(bill => {
      let name = billStatsName(bill);
      if (name.length > 6) name = name.slice(0, 6) + "…";
      return '<label class="fw-bill-edit__row"><span>' + esc(name) + '</span><input type="number" inputmode="decimal" step="0.01" min="0" data-amount-row="' + esc(bill.id) + '" value="' + Math.abs(Number(bill.amount) || 0).toFixed(2) + '"><em>¥</em></label>';
    }).join('');
    layer.innerHTML = '<section class="fw-bill-edit__panel" role="dialog" aria-modal="true" aria-label="修改金额">'
      + '<header><button type="button" data-amount-cancel>取消</button><strong>' + esc(options.title || "修改金额") + '</strong><button type="button" data-amount-save>完成</button></header>'
      + '<div class="fw-bill-edit__body">'
      + '<p class="fw-bill-edit__hint">' + esc(options.hint || "") + '</p>'
      + '<div data-amount-rows>' + rowsHtml + '</div>'
      + '<button type="button" class="fw-bill-edit__add" data-amount-add>+ 新增一笔金额</button>'
      + '</div></section>';
    const rowsBox = layer.querySelector('[data-amount-rows]');
    layer.querySelector('[data-amount-add]').addEventListener('click', () => {
      const row = document.createElement('label');
      row.className = 'fw-bill-edit__row';
      row.innerHTML = '<span>新增</span><input type="number" inputmode="decimal" step="0.01" min="0" data-amount-new value=""><em>¥</em>';
      rowsBox.appendChild(row);
      const input = row.querySelector('input');
      if (input) input.focus();
    });
    const close = () => layer.remove();
    layer.querySelector('[data-amount-cancel]').addEventListener('click', close);
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    layer.querySelector('[data-amount-save]').addEventListener('click', () => {
      const store = billsOf();
      let changed = false;
      Array.prototype.slice.call(layer.querySelectorAll('[data-amount-row]')).forEach(input => {
        const bill = store.find(item => item.id === input.dataset.amountRow);
        if (!bill) return;
        const next = Math.abs(Number(input.value));
        if (!isFinite(next) || next <= 0) return;
        const negative = (Number(bill.amount) || 0) < 0;
        if (Math.abs(Math.abs(Number(bill.amount) || 0) - next) < 0.001) return;
        bill.amount = Math.round((negative ? -next : next) * 100) / 100;
        changed = true;
      });
      Array.prototype.slice.call(layer.querySelectorAll('[data-amount-new]')).forEach(input => {
        const next = Math.abs(Number(input.value));
        if (!isFinite(next) || next <= 0) return;
        store.push(buildManualBillForCategory(options.monthKey, tab, next, options.category));
        changed = true;
      });
      if (changed) {
        store.sort((a, b) => b.createdAt - a.createdAt);
        saveBills(store);
      }
      close();
      if (typeof options.after === "function") options.after();
      payToast(changed ? "已保存" : "未做修改");
    });
    document.body.appendChild(layer);
  }
  function openMonthAmountEditor(monthKey, tab, after) {
    const tabName = tab === "income" ? "收入" : tab === "expense" ? "支出" : "其他";
    const list = billsOf().filter(bill => billInMonth(bill, monthKey) && billMatchesTab(bill, tab));
    openAmountEditor({
      list: list,
      tab: tab,
      title: String(monthKey).replace("-", "年") + "月 " + tabName,
      hint: list.length ? "修改任意一笔金额，柱形高度会同步变化" : "该月暂无记录，可新增一笔金额",
      monthKey: monthKey,
      after: after
    });
  }
  function openCategoryAmountEditor(category, tab, scope, monthKey, after) {
    const list = billsOf().filter(bill => scope(bill) && billMatchesTab(bill, tab) && billStatsCategory(bill, tab) === category);
    openAmountEditor({
      list: list,
      tab: tab,
      title: category,
      hint: list.length ? "修改任意一笔金额，合计与进度条会同步变化" : "暂无记录，可新增一笔金额",
      monthKey: monthKey,
      category: category,
      after: after
    });
  }
  function loadStatsOverrides() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STATS_OVERRIDE_KEY) || '{}');
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
    } catch (_) { return {}; }
  }
  function saveStatsOverrides(map) {
    try { localStorage.setItem(STATS_OVERRIDE_KEY, JSON.stringify(map)); } catch (_) { /* no-op */ }
  }
  function statsOverrideKey(scope, tab, category) {
    return scope + "|" + tab + "|" + category;
  }
  // Display-only editor for a composition row: it rewrites the number shown on the
  // statistics page and never touches the bills behind it.
  function openCategoryOverrideEditor(category, value, onSave, onReset) {
    const layer = document.createElement('div');
    layer.className = 'fw-bill-edit';
    layer.innerHTML = '<section class="fw-bill-edit__panel" role="dialog" aria-modal="true" aria-label="修改金额">'
      + '<header><button type="button" data-ov-cancel>取消</button><strong>' + esc(category) + '</strong><button type="button" data-ov-save>完成</button></header>'
      + '<div class="fw-bill-edit__body">'
      + '<p class="fw-bill-edit__hint">只修改这里的显示金额，不会改动账单记录</p>'
      + '<label class="fw-bill-edit__row"><span>金额</span><input type="number" inputmode="decimal" step="0.01" min="0" data-ov-input value="' + Math.abs(Number(value) || 0).toFixed(2) + '"><em>¥</em></label>'
      + '<button type="button" class="fw-bill-edit__add" data-ov-reset>恢复为账单实际金额</button>'
      + '</div></section>';
    const close = () => layer.remove();
    layer.querySelector('[data-ov-cancel]').addEventListener('click', close);
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    layer.querySelector('[data-ov-reset]').addEventListener('click', () => {
      close();
      if (typeof onReset === "function") onReset();
    });
    layer.querySelector('[data-ov-save]').addEventListener('click', () => {
      const input = layer.querySelector('[data-ov-input]');
      const next = Math.abs(Number(input && input.value));
      close();
      if (!isFinite(next)) return;
      if (typeof onSave === "function") onSave(next);
    });
    document.body.appendChild(layer);
  }
  function openBillAvatarPicker(bill) {
    openAvatarPicker((src, extra) => {
      applyAvatarToBill(bill, src, extra);
      saveBills(billsOf());
      render();
      payToast('头像已更换');
    });
  }
  // ---- Batch bills / full stats ------------------------------------------------
  // Amount pools intentionally cover both small and large transactions. Every
  // batch can therefore add income and spending records in either tier.
  const BATCH_MERCHANT_NAMES = ['向阳超市','邻里便利店','小满生活馆','城南菜市场','老陈水果店','鲜果时光','幸福西饼','川味小厨','兰州拉面','沙县小吃','蜜雪冰城','瑞幸咖啡','星巴克','麦当劳','肯德基','海底捞','喜茶','奈雪的茶','一点点','茶百道','华莱士','绝味鸭脖','正新鸡排','张亮麻辣烫','杨国福麻辣烫','罗森便利店','全家便利店','7-ELEVEn','永辉超市','大润发','沃尔玛','山姆会员店','盒马鲜生','屈臣氏','名创优品','优衣库','无印良品','小米之家','华为授权店','苹果体验店','中国石化','中国石油','顺丰速运','菜鸟驿站','美团外卖','饿了么','滴滴出行','高德打车','携程旅行','同程旅行','京东商城','拼多多','淘宝网','天猫超市','苏宁易购','当当网','网易严选','得物','唯品会','闲鱼','转转','58同城','贝壳找房','链家地产','我爱我家','中国移动','中国联通','中国电信','国家电网','自来水公司','燃气公司','物业服务中心','顺丰同城','德邦快递','邮政EMS','韵达快递','中通快递','圆通速递','申通快递','极兔速递'];
  const BATCH_PERSON_NAMES = ['清风徐来','橘子汽水','晚风轻语','山间听雨','星河入梦','半糖去冰','柠檬不酸','北岛来信','鹿鸣','屿暖','南巷旧人','一川烟草','白桃乌龙','云深不知处','春日部防卫队','小林同学','阿哲','小北','安然','鹿小姐','陈先生','林女士','老王','阿杰','小雅','南风','半夏','木子','阿狸','可乐','雪碧','芬达','旺仔','奶茶三分糖','芋泥啵啵','芝士就是力量','干饭人','打工人','小仙女','大聪明','快乐星球','一只小可爱','不许熬夜','月亮不睡我不睡'];
  const BATCH_REMARKS = ['日常消费','扫码付款','门店消费','微信支付','商户收款','线上购物','到店消费','外卖订单','打车费用','生活缴费','话费充值','加油费用','超市购物','餐饮消费','咖啡饮品','水果生鲜','快递物流','酒店住宿','电影娱乐','旅游出行','服饰鞋包','数码家电','美妆护肤','医药健康','教育培训','房租水电','物业费用','会员充值','游戏充值','朋友转账','家人转账','还款','红包','退款','工资','奖金','报销','兼职收入','投资收益','二手闲置','礼金'];
  function parseBatchCount(value) {
    const raw = String(value == null ? '' : value).trim();
    if (!/^\d+$/.test(raw)) return 0;
    const count = Number(raw);
    return count >= 1 && count <= 500 ? count : 0;
  }
  function parseBatchSpread(value) {
    const raw = String(value == null ? '' : value).trim();
    if (raw === '') return 30;
    const spread = Number(raw);
    return Number.isFinite(spread) && spread >= 0 && spread <= 365 ? Math.floor(spread) : -1;
  }
  function batchDateInputValue(date) {
    const d = date instanceof Date && !Number.isNaN(date.getTime()) ? date : new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
  function parseBatchDate(value) {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(value || '').trim());
    if (!match) return null;
    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const date = new Date(year, month - 1, day, 12, 0, 0, 0);
    if (Number.isNaN(date.getTime()) || date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
    return date;
  }
  function randomUint32() {
    if (window.crypto && typeof window.crypto.getRandomValues === 'function') {
      const values = new Uint32Array(1);
      window.crypto.getRandomValues(values);
      return values[0] >>> 0;
    }
    return Math.floor(Math.random() * 4294967296) >>> 0;
  }
  function seedRandomFromText(text) {
    const seed = hashString(String(text || '') + '#' + Date.now() + '#' + Math.random());
    randomState = seed || 1;
  }
  let randomState = 0;
  function seededUint32() {
    randomState = (Math.imul(randomState ^ (randomState >>> 15), 2246822507) ^ Math.imul(randomState ^ (randomState >>> 13), 3266489909)) >>> 0;
    return randomState;
  }
  function useCryptoFallback() {
    randomState = randomUint32() || 1;
  }
  function randomInt(min, max) {
    const lo = Math.ceil(Number(min) || 0);
    const hi = Math.floor(Number(max) || lo);
    if (hi <= lo) return lo;
    const span = hi - lo + 1;
    const value = randomState ? seededUint32() : randomUint32();
    return lo + (value % span);
  }
  function randomPick(list) {
    return list[randomInt(0, list.length - 1)];
  }
  function randomAmountForTier(tier) {
    if (tier === 'large') {
      const base = randomInt(100, 9999);
      return Math.round((base + randomInt(0, 99) / 100) * 100) / 100;
    }
    return Math.round((randomInt(1, 9999) / 100) * 100) / 100;
  }
  function randomBatchIdentity() {
    return randomInt(0, 1) ? randomPick(BATCH_MERCHANT_NAMES) : randomPick(BATCH_PERSON_NAMES);
  }
  function randomBatchTitle(sign, identity) {
    const roll = randomInt(0, 4);
    if (roll === 0) return identity;
    if (roll === 1) return (sign < 0 ? '向' : '来自') + identity + (sign < 0 ? '付款' : '收款');
    if (roll === 2) return (sign < 0 ? '转账-转给' : '转账-来自') + identity;
    if (roll === 3) return (sign < 0 ? '扫二维码付款-给' : '二维码收款-来自') + identity;
    return (sign < 0 ? '付款给' : '收款来自') + identity;
  }
  function fetchRandomSeed() {
    const jobs = [];
    // xxapi UUID gives a stable, unique nonce on every call.
    jobs.push(fetch(UUID_API_URL + '&_=' + Date.now(), { cache: 'no-store', headers: { Accept: 'application/json' } })
      .then(response => response.ok ? response.json() : null)
      .then(payload => payload && Number(payload.code) === 200 ? String(payload.data || '') : '')
      .catch(() => ''));
    // External true-random endpoints: tolerate both plain-text and JSON bodies.
    BATCH_RANDOM_API_URLS.forEach(url => {
      jobs.push(fetch(url + '&_=' + Date.now(), { cache: 'no-store', headers: { Accept: 'application/json, text/plain, */*' } })
        .then(response => response.ok ? response.text() : '')
        .then(text => {
          const body = String(text || '').trim();
          if (!body) return '';
          if (body.charAt(0) === '[') {
            try { const payload = JSON.parse(body); return Array.isArray(payload) ? payload.join('-') : ''; } catch (error) { return ''; }
          }
          return body.replace(/\s+/g, '-');
        })
        .catch(() => ''));
    });
    return Promise.all(jobs).then(values => {
      const text = values.filter(Boolean).join('|');
      if (text.replace(/[|\s-]+/g, '')) { seedRandomFromText(text); return text; }
      useCryptoFallback();
      return 'local-' + randomState;
    });
  }
  function buildBatchPlan(count, direction, scale) {
    const plan = [];
    for (let index = 0; index < count; index++) {
      let tier;
      let sign;
      if (scale === 'mixed' && direction === 'mixed') {
        // Cycle through all four combinations: large income, small income,
        // large expense, small expense.
        const combo = index % 4;
        tier = combo % 2 === 0 ? 'large' : 'small';
        sign = combo < 2 ? 1 : -1;
      } else {
        tier = scale === 'mixed' ? (index % 2 === 0 ? 'large' : 'small') : scale;
        sign = direction === 'mixed' ? (index % 2 === 0 ? 1 : -1) : (direction === 'income' ? 1 : -1);
      }
      const base = randomAmountForTier(tier);
      plan.push({ amount: Math.round(sign * base * 100) / 100, tier, sign, merchant: randomBatchIdentity(), remark: randomPick(BATCH_REMARKS) });
    }
    return plan;
  }
  function summarizeBatchPlan(plan) {
    return plan.reduce((summary, item) => {
      summary.count += 1;
      if (item.amount < 0) { summary.spend += Math.abs(item.amount); summary.expenseCount += 1; }
      else { summary.income += item.amount; summary.incomeCount += 1; }
      if (item.tier === 'large') summary.largeCount += 1; else summary.smallCount += 1;
      return summary;
    }, { count: 0, income: 0, spend: 0, incomeCount: 0, expenseCount: 0, smallCount: 0, largeCount: 0 });
  }
  function createBatchBills(config) {
    if (!randomState) useCryptoFallback();
    const count = parseBatchCount(config.count);
    if (!count) return [];
    const direction = ['mixed', 'income', 'expense'].includes(config.direction) ? config.direction : 'mixed';
    const scale = ['mixed', 'small', 'large'].includes(config.scale) ? config.scale : 'mixed';
    const spread = parseBatchSpread(config.spread);
    if (spread < 0) return [];
    const latest = parseBatchDate(config.date) || new Date();
    latest.setHours(12, 0, 0, 0);
    const plan = buildBatchPlan(count, direction, scale);
    const now = Date.now();
    const batchToken = now.toString(36) + Math.random().toString(36).slice(2, 8);
    const entries = plan.map((item, index) => {
      const seed = hashString('batch#' + batchToken + '#' + index + '#' + item.amount + '#' + item.merchant + '#' + item.remark);
      const offset = spread > 0 ? (index * 7) % (spread + 1) : 0;
      const minuteOffset = (index % 12) * 7;
      const createdAt = latest.getTime() - offset * 86400000 - minuteOffset * 60000;
      const friendName = item.merchant || randomBatchIdentity();
      const note = item.remark || randomPick(BATCH_REMARKS);
      const title = randomBatchTitle(item.amount, friendName);
      return {
        id: 'bill' + seed.toString(36) + 'b' + index.toString(36),
        title,
        friendName,
        avatar: '',
        amount: item.amount,
        originalPrice: null,
        discount: null,
        createdAt,
        status: item.amount < 0 ? '已转账' : '已收钱',
        payMethod: '零钱',
        acquirer: '财付通支付科技有限公司',
        note,
        transferNo: '1000050001' + digitsFrom(seed, 21),
        receivedAt: createdAt + 10 * 60 * 1000,
        tradeNo: '42000' + digitsFrom(seed, 22),
        merchantNo: '可在支持的商户扫码退款',
        barcode: digitsFrom(seed ^ 0x5bf03635, 24),
        batchCreated: true,
        batchTier: item.tier
      };
    });
    const list = billsOf();
    list.push.apply(list, entries);
    list.sort((a, b) => b.createdAt - a.createdAt);
    saveBills(list);
    render();
    ensureApiBillAvatars();
    payToast('已批量添加 ' + entries.length + ' 条账单');
    return entries;
  }
  function openBillsMoreMenu() {
    const layer = document.createElement('div');
    layer.className = 'fw-bills-menu';
    layer.innerHTML = '<section class="fw-bills-menu__panel" role="dialog" aria-modal="true" aria-label="账单设置"><p class="fw-bills-menu__title">账单设置</p><button class="fw-bills-menu__item" type="button" data-bills-menu-batch>' + icon('plus') + '<span><b>批量添加账单</b><small>一次生成多条收入与支出记录</small></span></button><button class="fw-bills-menu__item" type="button" data-bills-menu-stats>' + icon('receipt') + '<span><b>收支统计</b><small>查看全部账单的收入、支出与结余</small></span></button><button class="fw-bills-menu__cancel" type="button" data-bills-menu-cancel>取消</button></section>';
    const close = () => layer.remove();
    layer.querySelector('[data-bills-menu-batch]').addEventListener('click', () => { close(); openBatchBillCreator(); });
    layer.querySelector('[data-bills-menu-stats]').addEventListener('click', () => { close(); openBillStats(); });
    layer.querySelector('[data-bills-menu-cancel]').addEventListener('click', close);
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
  }
  // A ranking row aggregates every bill that shares the same payee. The sheet below
  // renames the row, replaces the avatar for the whole group and edits every amount.
  function openBillGroupEditor(group, after) {
    const list = Array.isArray(group.bills) && group.bills.length ? group.bills.slice() : (group.bill ? [group.bill] : []);
    if (!list.length) return;
    const single = list.length === 1;
    const layer = document.createElement('div');
    layer.className = 'fw-bill-edit';
    const amountRows = list.map((bill, index) => {
      const outgoing = (Number(bill.amount) || 0) < 0;
      const label = single ? '金额' : (outgoing ? '支出 ' : '收入 ') + (index + 1);
      const value = Math.abs(Number(bill.amount) || 0).toFixed(2);
      return '<label class="fw-bill-edit__row"><span>' + esc(label) + '</span><input type="number" inputmode="decimal" step="0.01" min="0" data-edit-amount value="' + value + '"><em>¥</em></label>';
    }).join('');
    const nameRow = '<label class="fw-bill-edit__row"><span>名称</span><input type="text" maxlength="30" data-edit-name value="' + esc(group.name || '') + '"></label>';
    layer.innerHTML = '<section class="fw-bill-edit__panel" role="dialog" aria-modal="true" aria-label="编辑条目">'
      + '<header><button type="button" data-edit-cancel>取消</button><strong>' + esc(group.name || '编辑') + '</strong><button type="button" data-edit-save>完成</button></header>'
      + '<div class="fw-bill-edit__body">'
      + '<button type="button" class="fw-bill-edit__avatar" data-edit-avatar><img alt="" src="' + esc(billAvatarSrc(list[0])) + '"><span>点击更换头像（相册 / 通讯录 / 随机）</span></button>'
      + nameRow + amountRows
      + '</div></section>';
    let pendingAvatar = '';
    let pendingExtra = null;
    const avatarButton = layer.querySelector('[data-edit-avatar]');
    const avatarPreview = avatarButton.querySelector('img');
    avatarButton.addEventListener('click', () => {
      openAvatarPicker((src, extra) => {
        pendingAvatar = src;
        pendingExtra = extra || null;
        avatarPreview.src = src;
      });
    });
    const close = () => layer.remove();
    layer.querySelector('[data-edit-cancel]').addEventListener('click', close);
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    layer.querySelector('[data-edit-save]').addEventListener('click', () => {
      const amountInputs = Array.prototype.slice.call(layer.querySelectorAll('[data-edit-amount]'));
      const nameInput = layer.querySelector('[data-edit-name]');
      list.forEach((bill, index) => {
        const input = amountInputs[index];
        if (input) {
          const next = Math.abs(Number(input.value));
          if (isFinite(next) && next > 0) {
            const negative = (Number(bill.amount) || 0) < 0;
            bill.amount = Math.round((negative ? -next : next) * 100) / 100;
          }
        }
      });
      if (nameInput) {
        const name = String(nameInput.value || '').trim();
        if (name && name !== String(group.name || '')) {
          // The row always keeps its own label; the underlying bills only follow the
          // rename when a single entry sits behind it, so grouped transfers keep
          // their individual counterparties.
          list.forEach(bill => { bill.statName = name; });
          if (single) {
            const bill = list[0];
            const previous = String(bill.friendName || '');
            if (previous && bill.title) bill.title = String(bill.title).split(previous).join(name);
            bill.friendName = name;
          }
        }
      }
      list.forEach(bill => { if (pendingAvatar) applyAvatarToBill(bill, pendingAvatar, pendingExtra); });
      saveBills(billsOf());
      close();
      if (typeof after === 'function') after();
      payToast('已保存');
    });
    document.body.appendChild(layer);
  }
  // ---- Full statistics page -------------------------------------------------
  // Every number on this page is derived from the very same stored bill list the
  // wallet renders, so bulk-added entries are counted automatically.
  function billStatsIsTransfer(bill) {
    const blob = String(bill.title || "") + "|" + String(bill.note || "") + "|" + String(bill.friendName || "");
    return /转账|付款给|收款来自|来自|二维码收款|扫二维码付款/.test(blob);
  }
  function billStatsName(bill) {
    // A rename applied from the ranking sheet wins over the derived payee name.
    const override = String((bill && bill.statName) || "").trim();
    if (override) return override;
    if (billStatsIsTransfer(bill)) return "转账";
    const name = String(bill.friendName || "").trim();
    if (name) return name;
    const title = String(bill.title || "").replace(/^转账-转给/, "").replace(/^转账-来自/, "").trim();
    return title || "其他";
  }
  // Composition buckets: red packets are checked first because a red packet bill
  // can also carry transfer wording in its title.
  function billStatsCategory(bill, tab) {
    const blob = String(bill.title || "") + "|" + String(bill.note || "") + "|" + String(bill.friendName || "");
    if (/红包/.test(blob)) return "微信红包";
    if (billStatsIsTransfer(bill)) return tab === "income" ? "转账收入" : "转账";
    return tab === "income" ? "消费收入" : "消费支出";
  }
  function billStatsShortMoney(value) {
    const v = Math.abs(Number(value) || 0);
    if (v >= 10000) return (v / 10000).toFixed(1) + "万";
    return v.toFixed(2);
  }
  function openBillStats(options) {
    const all = billsOf().slice();
    const monthMap = {};
    all.forEach(bill => {
      const d = new Date(Number(bill.createdAt));
      if (Number.isNaN(d.getTime())) return;
      const key = d.getFullYear() + "-" + (d.getMonth() + 1);
      if (!monthMap[key]) monthMap[key] = { key: key, year: d.getFullYear(), month: d.getMonth() + 1, sort: new Date(d.getFullYear(), d.getMonth(), 1).getTime(), label: d.getFullYear() + "年" + (d.getMonth() + 1) + "月", bills: [] };
      monthMap[key].bills.push(bill);
    });
    const months = Object.keys(monthMap).map(key => monthMap[key]).sort((a, b) => b.sort - a.sort);
    const years = [];
    months.forEach(m => { if (years.indexOf(m.year) < 0) years.push(m.year); });
    years.sort((a, b) => b - a);
    const today = new Date();
    // 宫格「年度账单」直接落在这个浮层的「年账单」那一档。
    let mode = options && options.mode === "year" ? "year" : "month";
    let activeMonth = months.length ? months[0].key : (today.getFullYear() + "-" + (today.getMonth() + 1));
    let activeYear = months.length ? months[0].year : today.getFullYear();
    let tab = "expense";
    let pickerOpen = false;
    let rankGroups = [];
    let catGroups = [];

    const layer = document.createElement("div");
    layer.className = "fw-bill-stats";
    layer.innerHTML = '<section class="fw-bill-stats__panel" role="dialog" aria-modal="true" aria-label="收支统计"><div class="fw-bill-stats__scroll"></div></section>';
    const scroll = layer.querySelector(".fw-bill-stats__scroll");

    const inScope = bill => {
      const d = new Date(Number(bill.createdAt));
      if (Number.isNaN(d.getTime())) return false;
      if (mode === "year") return d.getFullYear() === activeYear;
      return (d.getFullYear() + "-" + (d.getMonth() + 1)) === activeMonth;
    };
    const matchesTab = bill => {
      const amount = Number(bill.amount) || 0;
      if (tab === "expense") return amount < 0;
      if (tab === "income") return amount > 0;
      return amount === 0;
    };
    const valueOf = bill => Math.abs(Number(bill.amount) || 0);
    const periodLabel = () => {
      if (mode === "year") return activeYear + "年";
      const hit = monthMap[activeMonth];
      return hit ? hit.label : activeMonth.replace("-", "年") + "月";
    };
    const tabName = () => (tab === "expense" ? "支出" : tab === "income" ? "收入" : "其他");

    function render() {
      const keep = scroll.scrollTop;
      const scope = all.filter(inScope);
      const rows = scope.filter(matchesTab);
      let total = 0;
      rows.forEach(bill => { total += valueOf(bill); });
      const count = rows.length;
      const name = tabName();
      const scopeWord = mode === "year" ? "本年" : "当月";

      // --- monthly bar chart -------------------------------------------------
      const bars = [];
      if (mode === "year") {
        for (let m = 1; m <= 12; m++) bars.push({ label: m + "月", key: activeYear + "-" + m, active: false });
      } else {
        const hit = monthMap[activeMonth];
        const endYear = hit ? hit.year : today.getFullYear();
        const endMonth = hit ? hit.month : today.getMonth() + 1;
        for (let i = 5; i >= 0; i--) {
          const d = new Date(endYear, endMonth - 1 - i, 1);
          const key = d.getFullYear() + "-" + (d.getMonth() + 1);
          bars.push({ label: (d.getMonth() + 1) + "月", key: key, active: key === activeMonth });
        }
      }
      bars.forEach(bar => {
        let sum = 0;
        let hits = 0;
        all.forEach(bill => {
          const d = new Date(Number(bill.createdAt));
          if (Number.isNaN(d.getTime())) return;
          if ((d.getFullYear() + "-" + (d.getMonth() + 1)) !== bar.key) return;
          if (!matchesTab(bill)) return;
          sum += valueOf(bill);
          hits += 1;
        });
        bar.value = sum;
        bar.count = hits;
        bar.weight = tab === "other" ? hits : sum;
      });
      const maxBar = bars.reduce((max, bar) => Math.max(max, bar.weight), 0) || 1;
      const barsHtml = bars.map(bar => {
        const pct = bar.weight > 0 ? Math.max(3, Math.round(bar.weight / maxBar * 100)) : 0;
        const tip = tab === "other" ? bar.count + "笔" : "¥" + billStatsShortMoney(bar.value);
        return '<button type="button" class="fw-bill-stats__bar' + (bar.active ? " is-active" : "") + '" data-bar-key="' + esc(bar.key) + '"><span class="fw-bill-stats__bar-track"><i style="height:' + pct + '%"></i><em style="bottom:calc(' + pct + '% + 6px)">' + esc(tip) + "</em></span><span>" + esc(bar.label) + "</span></button>";
      }).join("");

      // --- category composition ---------------------------------------------
      const catMap = {};
      rows.forEach(bill => {
        const key = tab === "other" ? "其他" : billStatsCategory(bill, tab);
        if (!catMap[key]) catMap[key] = { name: key, value: 0, count: 0 };
        catMap[key].value += valueOf(bill);
        catMap[key].count += 1;
      });
      const overrideScope = mode === "year" ? "year:" + activeYear : "month:" + activeMonth;
      const overrides = loadStatsOverrides();
      const cats = Object.keys(catMap).map(key => catMap[key]);
      cats.forEach(item => {
        const saved = overrides[statsOverrideKey(overrideScope, tab, item.name)];
        if (typeof saved === "number" && isFinite(saved)) {
          item.actualValue = item.value;
          item.value = saved;
          item.overridden = true;
        }
      });
      cats.sort((a, b) => b.value - a.value);
      catGroups = cats;
      const maxCat = cats.reduce((max, item) => Math.max(max, item.value), 0) || 1;
      // Categories tile the whole scope, so their displayed values also drive the
      // headline total once a row has been overridden.
      const shownTotal = cats.length ? cats.reduce((sum, item) => sum + item.value, 0) : total;
      const catsHtml = cats.map((item, catIndex) => {
        const pct = Math.max(3, Math.round(item.value / maxCat * 100));
        const money = tab === "other" ? item.count + " 笔" : "¥" + item.value.toFixed(2);
        const transfer = /转账/.test(item.name);
        const redpacket = /红包/.test(item.name);
        const catIcon = redpacket ? "redpacket" : (transfer ? "swap" : "walletCard");
        const catClass = redpacket ? " is-redpacket" : (transfer ? " is-transfer" : "");
        return '<button type="button" class="fw-bill-stats__cat' + catClass + '" data-cat-index="' + catIndex + '"><span class="fw-bill-stats__cat-icon">' + icon(catIcon) + '</span><span class="fw-bill-stats__cat-name">' + esc(item.name) + '</span><span class="fw-bill-stats__cat-bar"><i style="width:' + pct + '%"></i></span><span class="fw-bill-stats__cat-money">' + esc(money) + "</span>" + icon("arrow", "#c8c8c8") + "</button>";
      }).join("");

      // --- top-10 ranking -----------------------------------------------------
      const rankMap = {};
      rows.forEach(bill => {
        const key = billStatsName(bill);
        if (!rankMap[key]) rankMap[key] = { name: key, value: 0, count: 0, bill: bill, bills: [] };
        rankMap[key].value += valueOf(bill);
        rankMap[key].count += 1;
        rankMap[key].bills.push(bill);
      });
      const rank = Object.keys(rankMap).map(key => rankMap[key]).sort((a, b) => b.value - a.value).slice(0, 10);
      rankGroups = rank;
      const rankHtml = rank.map((item, index) => {
        const transfer = item.name === "转账" || item.name === "转账收入";
        // A hand-picked avatar (album / contacts / random API) wins even on the
        // transfer row, otherwise keep the generic transfer badge.
        const picked = item.bills.some(bill => bill.avatarPicked && bill.avatar);
        const avatar = transfer && !picked
          ? '<span class="fw-bill-stats__rank-avatar is-transfer">' + icon("swap") + "</span>"
          : '<img class="fw-bill-stats__rank-avatar" src="' + esc(billAvatarSrc(item.bill)) + '" alt="">';
        const money = tab === "other" ? item.count + " 笔" : "¥" + item.value.toFixed(2);
        return '<button type="button" class="fw-bill-stats__rank-row" data-rank-index="' + index + '"><span class="fw-bill-stats__rank-no">' + (index + 1) + "</span>" + avatar + '<span class="fw-bill-stats__rank-name">' + esc(item.name) + '</span><span class="fw-bill-stats__rank-money">' + esc(money) + "</span>" + icon("arrow", "#c8c8c8") + "</button>";
      }).join("");

      // --- period picker ------------------------------------------------------
      let pickerHtml = "";
      if (pickerOpen) {
        const items = mode === "year"
          ? years.map(year => '<button type="button" data-stats-pick="year:' + year + '"' + (year === activeYear ? ' class="is-active"' : "") + ">" + year + "年</button>").join("")
          : months.map(item => '<button type="button" data-stats-pick="month:' + esc(item.key) + '"' + (item.key === activeMonth ? ' class="is-active"' : "") + ">" + esc(item.label) + "</button>").join("");
        pickerHtml = '<div class="fw-bill-stats__picker">' + (items || '<span class="fw-bill-stats__empty">暂无记录</span>') + "</div>";
      }

      const modes = [["month", "月账单"], ["year", "年账单"]].map(item => '<button type="button" data-stats-mode="' + item[0] + '"' + (mode === item[0] ? ' class="is-active"' : "") + ">" + item[1] + "</button>").join("");
      const tabs = [["expense", "支出"], ["income", "收入"], ["other", "其他"]].map(item => '<button type="button" data-stats-tab="' + item[0] + '"' + (tab === item[0] ? ' class="is-active"' : "") + ">" + item[1] + "</button>").join("");
      // The year bill keeps only the composition block: no ledger card, no monthly
      // column chart and no payee ranking, matching the reference screenshot.
      const isYear = mode === "year";
      const ledgerHtml = isYear ? "" :
        '<button type="button" class="fw-bill-stats__ledger" data-stats-ledger>' +
          '<span class="fw-bill-stats__ledger-icon">' + icon("collect") + "</span>" +
          '<span class="fw-bill-stats__ledger-copy">使用记账本，查看分类统计(餐饮、交通等)</span>' +
          icon("arrow", "#c8c8c8") +
        "</button>";
      const chartHtml = isYear ? "" :
        '<section class="fw-bill-stats__card"><h3>每月' + name + '对比</h3><div class="fw-bill-stats__bars">' + barsHtml + "</div></section>";
      const compositionTitle = (isYear ? "" : scopeWord) + name + "构成";
      const compositionHtml = '<section class="fw-bill-stats__card"><h3>' + compositionTitle + "</h3>" + (catsHtml || '<p class="fw-bill-stats__empty">暂无记录</p>') + "</section>";
      const rankSectionHtml = isYear ? "" :
        '<section class="fw-bill-stats__card"><h3>' + scopeWord + name + "排行榜(前10)</h3>" + (rankHtml || '<p class="fw-bill-stats__empty">暂无记录</p>') + "</section>";

      scroll.innerHTML =
        '<div class="fw-bill-stats__head">' +
          '<div class="fw-bill-stats__navbar">' +
            '<button type="button" class="fw-bill-stats__close" data-bill-stats-close aria-label="关闭">' + icon("close") + "</button>" +
            '<span class="fw-bill-stats__navspacer"></span>' +
          "</div>" +
          '<div class="fw-bill-stats__modes">' + modes + "</div>" +
          '<div class="fw-bill-stats__filters">' +
            '<button type="button" class="fw-bill-stats__period" data-stats-period>' + esc(periodLabel()) + icon("caret") + "</button>" +
            '<div class="fw-bill-stats__tabs">' + tabs + "</div>" +
          "</div>" +
          '<p class="fw-bill-stats__summary">共' + name + "<b>" + count + "</b>笔，合计</p>" +
          '<strong class="fw-bill-stats__total">' + (isYear ? "¥ " : "¥") + Math.abs(shownTotal).toFixed(2) + "</strong>" +
        "</div>" +
        pickerHtml +
        '<div class="fw-bill-stats__body">' +
          ledgerHtml + chartHtml + compositionHtml + rankSectionHtml +
        "</div>";

      scroll.querySelectorAll("[data-bill-stats-close]").forEach(button => button.addEventListener("click", close));
      scroll.querySelectorAll("[data-stats-mode]").forEach(button => button.addEventListener("click", () => {
        if (button.dataset.statsMode === mode) return;
        mode = button.dataset.statsMode;
        if (mode === "year" && years.length && years.indexOf(activeYear) < 0) activeYear = years[0];
        pickerOpen = false;
        render();
      }));
      scroll.querySelectorAll("[data-stats-tab]").forEach(button => button.addEventListener("click", () => {
        if (button.dataset.statsTab === tab) return;
        tab = button.dataset.statsTab;
        pickerOpen = false;
        render();
      }));
      const periodButton = scroll.querySelector("[data-stats-period]");
      if (periodButton) periodButton.addEventListener("click", () => { pickerOpen = !pickerOpen; render(); });
      scroll.querySelectorAll("[data-stats-pick]").forEach(button => button.addEventListener("click", () => {
        const value = button.dataset.statsPick || "";
        if (value.indexOf("year:") === 0) activeYear = Number(value.slice(5));
        else if (value.indexOf("month:") === 0) activeMonth = value.slice(6);
        pickerOpen = false;
        render();
      }));
      const ledger = scroll.querySelector("[data-stats-ledger]");
      if (ledger) ledger.addEventListener("click", () => payToast("记账本（本地演示）"));
      scroll.querySelectorAll("[data-bar-key]").forEach(button => {
        const key = button.dataset.barKey;
        bindBarGesture(button, () => {
          // Tapping a column selects that month: in the year chart it drills into
          // the month view so the highlight and the figures stay in sync. Tapping
          // the already-selected column opens its amount editor.
          if (mode === "year") {
            mode = "month";
            activeMonth = key;
            pickerOpen = false;
            render();
            return;
          }
          if (activeMonth === key) { openMonthAmountEditor(key, tab, render); return; }
          activeMonth = key;
          pickerOpen = false;
          render();
        }, () => {
          openMonthAmountEditor(key, tab, render);
        });
      });
      scroll.querySelectorAll("[data-rank-index]").forEach(button => button.addEventListener("click", () => {
        const group = rankGroups[Number(button.dataset.rankIndex)];
        if (group) openBillGroupEditor(group, render);
      }));
      scroll.querySelectorAll("[data-cat-index]").forEach(button => button.addEventListener("click", () => {
        const item = catGroups[Number(button.dataset.catIndex)];
        if (!item) return;
        // Statistics-only edit: the amount shown for this row changes, the bills do not.
        const scope = mode === "year" ? "year:" + activeYear : "month:" + activeMonth;
        const key = statsOverrideKey(scope, tab, item.name);
        openCategoryOverrideEditor(item.name, item.value, value => {
          const map = loadStatsOverrides();
          if (value === item.value) { render(); return; }
          map[key] = value;
          saveStatsOverrides(map);
          render();
          payToast("已修改显示金额");
        }, () => {
          const map = loadStatsOverrides();
          delete map[key];
          saveStatsOverrides(map);
          render();
          payToast("已恢复账单金额");
        });
      }));
      scroll.scrollTop = keep;
    }

    let closed = false;
    function close() {
      if (closed) return;
      closed = true;
      layer.classList.remove("is-open");
      setTimeout(() => layer.remove(), 320);
    }
    // Let the Android Back button and the Escape key play the slide-out animation.
    layer.__fwClose = close;

    document.body.appendChild(layer);
    render();
    requestAnimationFrame(() => requestAnimationFrame(() => layer.classList.add("is-open")));
  }

  function openBatchBillCreator() {
    const layer = document.createElement('div');
    layer.className = 'fw-bill-batch';
    layer.innerHTML = '<section class="fw-bill-batch__panel" role="dialog" aria-modal="true" aria-label="批量添加账单"><header><button type="button" data-batch-cancel>取消</button><strong>批量添加账单</strong><span class="fw-bill-batch__actions"><button type="button" data-batch-refresh>换一批</button><button type="button" data-batch-add>添加</button></span></header><div class="fw-bill-batch__body"><label class="fw-bill-batch__field"><span>添加数量</span><input type="number" min="1" max="500" step="1" inputmode="numeric" value="20" data-batch-count><em>条</em></label><div class="fw-bill-batch__block"><span>收支方向</span><div class="fw-bill-batch__segmented"><button type="button" class="is-active" data-batch-direction="mixed">收入+支出</button><button type="button" data-batch-direction="income">仅收入</button><button type="button" data-batch-direction="expense">仅支出</button></div></div><div class="fw-bill-batch__block"><span>金额档位</span><div class="fw-bill-batch__segmented"><button type="button" class="is-active" data-batch-scale="mixed">大小额混合</button><button type="button" data-batch-scale="small">小额</button><button type="button" data-batch-scale="large">大额</button></div></div><label class="fw-bill-batch__field"><span>标题前缀</span><input type="text" maxlength="30" placeholder="留空则随机商户/网名" data-batch-prefix></label><label class="fw-bill-batch__field"><span>备注</span><input type="text" maxlength="60" placeholder="留空则随机消费场景" data-batch-note></label><label class="fw-bill-batch__field"><span>账单日期</span><input type="date" value="' + batchDateInputValue(new Date()) + '" data-batch-date></label><label class="fw-bill-batch__field"><span>分散天数</span><input type="number" min="0" max="365" step="1" inputmode="numeric" value="0" data-batch-spread><em>天</em></label><div class="fw-bill-batch__preview"><div><span>预计生成</span><b data-batch-preview-count>20 条</b></div><div><span>收入</span><b class="is-in" data-batch-preview-income>+¥0.00</b></div><div><span>支出</span><b class="is-out" data-batch-preview-spend>-¥0.00</b></div><p data-batch-preview-range></p></div><p class="fw-bill-batch__hint">标题与备注留空时会从商户、超市、公司、网名和消费场景中随机生成；金额由随机接口与本地随机源混合生成，并自动计入账单月汇总和收支统计。</p></div></section>';
    let direction = 'mixed';
    let scale = 'mixed';
    const countInput = layer.querySelector('[data-batch-count]');
    const spreadInput = layer.querySelector('[data-batch-spread]');
    const dateInput = layer.querySelector('[data-batch-date]');
    const prefixInput = layer.querySelector('[data-batch-prefix]');
    const noteInput = layer.querySelector('[data-batch-note]');
    const previewCount = layer.querySelector('[data-batch-preview-count]');
    const previewIncome = layer.querySelector('[data-batch-preview-income]');
    const previewSpend = layer.querySelector('[data-batch-preview-spend]');
    const previewRange = layer.querySelector('[data-batch-preview-range]');
    const refreshPreview = (forceRandom) => {
      if (forceRandom && !randomState) useCryptoFallback();
      const count = parseBatchCount(countInput.value);
      if (!count) {
        previewCount.textContent = '请输入 1-500';
        previewIncome.textContent = '+¥0.00';
        previewSpend.textContent = '-¥0.00';
        previewRange.textContent = '数量需为 1-500 之间的整数';
        return;
      }
      const summary = summarizeBatchPlan(buildBatchPlan(count, direction, scale));
      previewCount.textContent = summary.count + ' 条';
      previewIncome.textContent = '+¥' + summary.income.toFixed(2);
      previewSpend.textContent = '-¥' + summary.spend.toFixed(2);
      previewRange.textContent = summary.incomeCount + ' 笔收入 / ' + summary.expenseCount + ' 笔支出 · ' + summary.smallCount + ' 笔小额 / ' + summary.largeCount + ' 笔大额';
    };
    layer.querySelectorAll('[data-batch-direction]').forEach(button => button.addEventListener('click', () => {
      direction = button.dataset.batchDirection;
      layer.querySelectorAll('[data-batch-direction]').forEach(item => item.classList.toggle('is-active', item === button));
      refreshPreview();
    }));
    layer.querySelectorAll('[data-batch-scale]').forEach(button => button.addEventListener('click', () => {
      scale = button.dataset.batchScale;
      layer.querySelectorAll('[data-batch-scale]').forEach(item => item.classList.toggle('is-active', item === button));
      refreshPreview();
    }));
    [countInput, spreadInput, dateInput, prefixInput, noteInput].forEach(input => input.addEventListener('input', refreshPreview));
    const close = () => layer.remove();
    layer.querySelector('[data-batch-cancel]').addEventListener('click', close);
    layer.querySelector('[data-batch-refresh]').addEventListener('click', () => {
      const refreshButton = layer.querySelector('[data-batch-refresh]');
      if (refreshButton.disabled) return;
      refreshButton.disabled = true;
      fetchRandomSeed().then(() => refreshPreview(false)).catch(() => refreshPreview(true)).then(() => { refreshButton.disabled = false; });
    });
    layer.querySelector('[data-batch-add]').addEventListener('click', () => {
      if (!parseBatchCount(countInput.value)) {
        countInput.setCustomValidity('请输入 1-500 之间的整数');
        countInput.reportValidity();
        countInput.setCustomValidity('');
        return;
      }
      if (parseBatchSpread(spreadInput.value) < 0) {
        spreadInput.setCustomValidity('请输入 0-365 之间的天数');
        spreadInput.reportValidity();
        spreadInput.setCustomValidity('');
        return;
      }
      if (!parseBatchDate(dateInput.value)) {
        dateInput.setCustomValidity('请选择有效日期');
        dateInput.reportValidity();
        dateInput.setCustomValidity('');
        return;
      }
      const addButton = layer.querySelector('[data-batch-add]');
      addButton.disabled = true;
      addButton.textContent = '生成中';
      fetchRandomSeed().then(() => createBatchBills({
        count: countInput.value,
        direction,
        scale,
        prefix: prefixInput.value,
        note: noteInput.value,
        date: dateInput.value,
        spread: spreadInput.value
      })).catch(() => createBatchBills({
        count: countInput.value,
        direction,
        scale,
        prefix: prefixInput.value,
        note: noteInput.value,
        date: dateInput.value,
        spread: spreadInput.value
      })).then(() => close());
    });
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
    refreshPreview();
  }
  function bindBills(root) {
    root.querySelectorAll('[data-bill]').forEach(row => row.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      location.hash = '#/wechat/wallet/bills/' + encodeURIComponent(row.dataset.bill);
    }));
    const closeToList = root.querySelector('[data-bills-close]');
    if (closeToList) closeToList.addEventListener('click', event => {
      event.preventDefault();
      payStack = [];
      location.hash = '#/wechat/wallet';
    });
    const closeToBills = root.querySelector('[data-bill-close-only]');
    if (closeToBills) closeToBills.addEventListener('click', event => {
      event.preventDefault();
      location.hash = '#/wechat/wallet/bills';
    });
    root.querySelectorAll('[data-bills-more]').forEach(button => button.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); openBillsMoreMenu(); }));
    root.querySelectorAll('[data-bills-stat]').forEach(button => button.addEventListener('click', event => { event.preventDefault(); event.stopPropagation(); openBillStats(); }));
    root.querySelectorAll('[data-bill-tool]').forEach(button => button.addEventListener('click', () => payToast(button.dataset.billTool + '（本地演示）')));
    const bill = currentBill();
    if (!bill) return;
    root.querySelectorAll('[data-bill-edit]').forEach(element => element.addEventListener('click', event => {
      event.preventDefault();
      event.stopPropagation();
      const kind = element.dataset.billEdit;
      if (kind === 'avatar') { openBillAvatarPicker(bill); return; }
      if (kind === 'amount') {
        openBillEditor({
          title: '修改金额', type: 'number', value: String(bill.amount),
          onSave: value => {
            const amount = Number(String(value).replace(/[^0-9.+-]/g, ''));
            if (!Number.isFinite(amount) || amount === 0) { payToast('请输入有效金额'); return false; }
            bill.amount = Math.round(amount * 100) / 100;
            return true;
          }
        });
        return;
      }
      openBillEditor({
        title: '修改备注', type: 'text', value: kind === 'note' ? String(bill.note || '微信转账') : bill.title,
        onSave: value => {
          const text = String(value).trim();
          if (!text) { payToast('备注不能为空'); return false; }
          if (kind === 'note') bill.note = text; else bill.title = text;
          return true;
        }
      });
    }));
  }

  function render() {
    const h = currentRoute();
    const kind = h === routes.service ? 'service' : h === routes.wallet ? 'wallet' : h === routes.balance ? 'balance' : h === routes.payment ? 'payment' : h === routes.bills ? 'bills' : h === routes.withdraw ? 'withdraw' : h === routes.cards ? 'cards' : h === routes.cardAdd ? 'cardAdd' : '';
    // 深链入口只对它自己那一页有效：一旦离开这一页（含关闭账单回到钱包），
    // 就不再拦截返回键。
    if (entryRootRoute && h !== entryRootRoute) entryRootRoute = '';
    let old = host();
    if (!kind) {
      if (old) old.remove();
      const root = document.getElementById('root');
      if (root) root.style.visibility = '';
      document.documentElement.classList.remove('fw-pay-route-active');
      if (typeof window.__fwSyncSystemBars === 'function') window.__fwSyncSystemBars(); else if (window.DoutuApp && window.DoutuApp.setPayPageSystemBars) window.DoutuApp.setPayPageSystemBars(false);
      return;
    }
    // 2026-10-11: 服务页改用 bundled(React) 那一版 —— 就是以前点收付款时一闪而过的那页
    // （彩色图标 + 钱包余额）。所以这一路由不再盖自绘宿主，只保留"支付页窗口"状态
    // （系统栏可见、safe-area 30px），顶部间距由下面的 applyBundledPaySpacing 负责。
    if (kind === 'service') {
      if (old) old.remove();
      const root = document.getElementById('root');
      if (root) root.style.visibility = '';
      document.documentElement.classList.add('fw-pay-route-active');
      if (typeof window.__fwSyncSystemBars === 'function') window.__fwSyncSystemBars();
      else if (window.DoutuApp && window.DoutuApp.setPayPageSystemBars) window.DoutuApp.setPayPageSystemBars(true);
      return;
    }
    ensurePayLayoutStyles();
    const pinnedPage = kind === 'bills' || kind === 'withdraw' || kind === 'cards' || kind === 'cardAdd';
    const appRoot = document.getElementById('root');
    if (appRoot) appRoot.style.visibility = pinnedPage ? 'hidden' : '';
    if (!old) { old = document.createElement('div'); old.id = HOST_ID; document.body.appendChild(old); }
    old.innerHTML = kind === 'wallet' ? walletPage() : kind === 'balance' ? balancePage() : kind === 'bills' ? (billDetailId() ? billDetailPage(billDetailId()) : billsPage()) : kind === 'withdraw' ? withdrawPage() : kind === 'cards' ? bankCardsPage() : kind === 'cardAdd' ? bankAddPage() : paymentPage();
         // Apply the reference top safe area inline as a last-resort guard against host stylesheet order.
     const page = old.querySelector('.fw-pay-page');
     if (page) {
       page.style.setProperty('padding-top', '31px', 'important');
       page.style.setProperty('box-sizing', 'border-box', 'important');
       page.style.setProperty('height', '100%', 'important');
       page.style.setProperty('min-height', '100%', 'important');
     }     old.style.cssText = 'position:fixed;inset:0;z-index:2147483647;display:block;visibility:visible;opacity:1;pointer-events:auto;box-sizing:border-box;width:100vw;height:100vh;transform:none';
    // 2026-10-19: 这份 CSS 在真机上丢了 `inset:0`，fixed 元素按静态位置落到屏幕下方，
    // 自绘页一直不可见。账单页没有 bundled 版本，必须由自绘层显示：单独把它钉到视口。
    if (pinnedPage) {
      old.style.setProperty('top', '0', 'important');
      old.style.setProperty('left', '0', 'important');
      old.style.setProperty('right', '0', 'important');
      old.style.setProperty('bottom', '0', 'important');
    }
    old.scrollTop = 0;
    old.querySelectorAll('.fw-pay-scroll').forEach(el => { el.scrollTop = 0; });
    old.dataset.fwRoute = h;
    document.documentElement.classList.add('fw-pay-route-active');
    if (typeof window.__fwSyncSystemBars === 'function') window.__fwSyncSystemBars();
    else if (window.DoutuApp && window.DoutuApp.setPayPageSystemBars) window.DoutuApp.setPayPageSystemBars(true);
    old.querySelectorAll('[data-go]').forEach(el => el.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); go(el.dataset.go); }));
    old.querySelectorAll('.fw-pay-back').forEach(el => el.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); go(e.currentTarget.dataset.go); }));
    old.querySelectorAll('[data-receive-pay]').forEach(el => el.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); openReceivePayScreen(); }));
    bindBalanceEditor(old);
    bindPaymentPage(old);
    if (kind === 'bills') { bindBills(old); ensureApiBillAvatars(); }
    if (kind === 'withdraw') bindWithdraw(old);
    if (kind === 'cards') bindBankCardsPage(old.querySelector('.fw-bc') || old);
    if (kind === 'cardAdd') bindBankAddPage(old.querySelector('.fw-bc') || old);
    console.info('[pay-pages] rendered', kind, h, old.getBoundingClientRect().width, old.getBoundingClientRect().height);
  }
  function bindBalanceEditor(root) {
    const trigger = root.querySelector('.fw-balance-amount-button');
    const editor = root.querySelector('.fw-balance-editor');
    if (!trigger || !editor) return;
    const input = editor.querySelector('.fw-balance-input');
    const close = () => { editor.hidden = true; trigger.hidden = false; };
    trigger.addEventListener('click', () => { input.value = getBalanceAmount(); trigger.hidden = true; editor.hidden = false; input.focus(); input.select(); });
    editor.querySelector('[data-balance-cancel]').addEventListener('click', close);
    editor.querySelector('[data-balance-save]').addEventListener('click', () => {
      const amount = Number(input.value);
      if (!input.value.trim() || !Number.isFinite(amount) || amount < 0) { input.setCustomValidity('请输入不小于0的有效金额'); input.reportValidity(); input.setCustomValidity(''); return; }
      const normalized = amount.toFixed(2);
      storeBalanceAmount(normalized, true);
      root.querySelector('.fw-balance-amount').textContent = normalized;
      close();
    });
    input.addEventListener('keydown', event => { if (event.key === 'Enter') editor.querySelector('[data-balance-save]').click(); if (event.key === 'Escape') close(); });
  }
  /**
   * 2026-10-11: 服务 → 收付款 和 微信主页 + → 收付款 现在是同一个页面 —— 由
   * wechat-chat-actions.js 挂出的整屏 .fw-receive-pay（绿底付款码）。入口本身仍然留在
   * 服务路由上（不跳 #/wechat/payment），所以返回键和页内 ‹ 都回到服务页；从微信主页
   * 加号进来时则回到微信主页，两边互不影响。
   * 服务页是 edge-to-edge 的"支付页"窗口（系统栏可见、safe-area 30px），而这一页是
   * 满屏绿底、按沉浸式窗口排版（和加号入口一致），所以挂在上面期间先把支付页窗口和
   * 宿主收起来，等它关掉再还原。
   */
  function openReceivePayScreen() {
    if (typeof window.__fwOpenReceivePay !== 'function') { go('#/wechat/payment'); return; }
    const root = document.documentElement;
    const payHost = host();
    const previousDisplay = payHost ? payHost.style.display : '';
    if (payHost) payHost.style.display = 'none';
    const syncBars = () => {
      if (typeof window.__fwSyncSystemBars === 'function') window.__fwSyncSystemBars();
      else if (window.DoutuApp && window.DoutuApp.setPayPageSystemBars) window.DoutuApp.setPayPageSystemBars(!!currentRoute());
    };
    const restore = () => {
      observer.disconnect();
      if (payHost && payHost.isConnected) payHost.style.display = previousDisplay || 'block';
      if (currentRoute()) root.classList.add('fw-pay-route-active');
      syncBars();
    };
    const observer = new MutationObserver(() => { if (!document.querySelector('.fw-receive-pay')) restore(); });
    root.classList.remove('fw-pay-route-active');
    syncBars();
    observer.observe(document.body, { childList: true, subtree: true });
    window.__fwOpenReceivePay();
  }

  /**
   * 服务页（bundled React 那版）的「收付款」格子本身是空的，这里把它接成和
   * 微信主页 + → 收付款 完全相同的那一页。用捕获阶段的事件代理，React 重渲染
   * 也不会把监听丢掉。
   */
  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    const tile = target.closest('.cursor-pointer');
    if (!tile || String(tile.textContent || '').trim() !== '收付款') return;
    if (tile.closest('#fw-pay-pages-host, .fw-receive-pay, .fw-chat-pay-screen, .fw-local-conversation')) return;
    event.preventDefault();
    event.stopPropagation();
    openReceivePayScreen();
  }, true);

  function bindPaymentPage(root) {
    const label = root.querySelector('.fw-payment-mode-label');
    root.querySelectorAll('[data-payment-mode]').forEach(tab => tab.addEventListener('click', () => {
      root.querySelectorAll('[data-payment-mode]').forEach(item => item.classList.toggle('is-active', item === tab));
      label.textContent = (tab.dataset.paymentMode === 'pay' ? '付款码' : '二维码收款') + '（模拟）';
    }));
    const confirm = root.querySelector('.fw-payment-confirm');
    if (confirm) confirm.addEventListener('click', () => {
      const amount = root.querySelector('.fw-payment-demo-amount input').value;
      window.alert(amount ? '模拟操作：¥' + amount + '，未产生真实交易' : '演示页面已确认，未产生真实交易');
    });
  }
  /* ===========================================================================
     零钱提现（原生首页宫格「零钱提现」深链进来的整页）

     参考图 1440x3168 ⇒ 本页 360 宽，CSS px = 参考图像素 / 4，下面的坐标都按这个换算。
     两张图对应两屏：
       ① 金额页：到账银行卡 / 提现金额 / 当前零钱余额（可点改）/ 全部提现 / 提现券 / 数字键盘
       ② 结果页：发起提现申请 → 银行处理中（预计时间跟着当前时间 +2 小时）→ 到账成功 + 明细
     =========================================================================== */
  const WITHDRAW_KEY = 'fw-withdraw-state.v1';
  const WITHDRAW_ART = './assets/withdraw/';
  let withdrawDraft = null;
  let withdrawStep = 'form';
  let withdrawOverlay = null;
  let withdrawPassword = '';
  let withdrawEtaTimer = 0;

  const withdrawSettings = () => {
    try {
      const raw = localStorage.getItem(WITHDRAW_KEY);
      const parsed = raw ? JSON.parse(raw) : null;
      if (parsed && typeof parsed === 'object') return parsed;
    } catch (_) { /* 坏数据当没有 */ }
    return {};
  };
  const saveWithdrawSettings = patch => {
    const next = Object.assign({}, withdrawSettings(), patch);
    try { localStorage.setItem(WITHDRAW_KEY, JSON.stringify(next)); } catch (_) { /* 存不下就算了 */ }
    return next;
  };
  /** 提现页的到账银行卡 = 「银行卡管理」里那几张卡（名字去掉「储蓄卡」后缀）。 */
  const withdrawCards = () => bankCards().map(card => ({
    id: card.id,
    bank: card.bank,
    name: String(card.name || '').replace(/(储蓄卡|信用卡|借记卡)$/, '') || String(card.name || ''),
    tail: card.tail,
    art: bankArt(card.bank)
  }));
  const findWithdrawCard = id => withdrawCards().find(card => card.id === id) || null;
  const withdrawDefaultCard = () => findWithdrawCard(withdrawSettings().cardId) || withdrawCards()[0];
  const withdrawDraftState = () => {
    if (!withdrawDraft) withdrawDraft = { amount: '', card: withdrawDefaultCard() };
    return withdrawDraft;
  };
  const withdrawAmount = () => {
    const value = Number(String(withdrawDraftState().amount || ''));
    return Number.isFinite(value) ? value : 0;
  };
  const withdrawCardChip = card => card.art
    ? '<img class="fw-wd__bank-art" src="' + card.art + '" alt="">'
    : '<span class="fw-wd__bank-badge" style="background:#8a8a8a">' + esc(String(card.name || '').slice(0, 1)) + '</span>';
  const withdrawPad2 = value => String(value).padStart(2, '0');
  /** 提现手续费：0.1%，最低 0.1 元；提现金额比它还小时按金额算（参考图 0.01 → -¥0.01）。 */
  const withdrawFee = amount => Math.min(amount, Math.max(0.1, amount * 0.001));
  /** 预计到账时间：跟着「当前时间 + 2 小时」自动走（参考图 20:31 → 22:31）。 */
  const withdrawEtaText = () => {
    const d = new Date(Date.now() + 2 * 60 * 60 * 1000);
    return '预计' + withdrawPad2(d.getMonth() + 1) + '-' + withdrawPad2(d.getDate()) + ' '
      + withdrawPad2(d.getHours()) + ':' + withdrawPad2(d.getMinutes()) + '前到账';
  };

  function withdrawKeypadHtml(withConfirm) {
    let html = '<div class="fw-wd__keys">';
    [1, 2, 3, 4, 5, 6, 7, 8, 9].forEach((digit, index) => {
      html += '<button class="fw-wd__key" type="button" data-wd-key="' + digit + '" style="grid-area:'
        + (Math.floor(index / 3) + 1) + '/' + (index % 3 + 1) + '">' + digit + '</button>';
    });
    html += '<button class="fw-wd__key" type="button" data-wd-key="0" style="grid-area:4/1/5/3">0</button>';
    html += '<button class="fw-wd__key fw-wd__key--dot" type="button" data-wd-key="." style="grid-area:4/3">.</button>';
    const del = '<img class="fw-wd__del-art" src="' + WITHDRAW_ART + 'icon-backspace.png" alt="">';
    if (withConfirm) {
      html += '<button class="fw-wd__key fw-wd__key--del" type="button" data-wd-key="del" style="grid-area:1/4">' + del + '</button>';
      html += '<button class="fw-wd__key fw-wd__confirm" type="button" data-wd-confirm style="grid-area:2/4/5/5">确定</button>';
    } else {
      html += '<button class="fw-wd__key fw-wd__key--del is-tall" type="button" data-wd-key="del" style="grid-area:1/4/5/5">' + del + '</button>';
    }
    return html + '</div>';
  }

  function withdrawPage() {
    if (withdrawStep === 'result') return withdrawResultPage();
    const draft = withdrawDraftState();
    const card = draft.card || withdrawDefaultCard();
    const amount = String(draft.amount || '');
    return '<main class="fw-wd">'
      + '<header class="fw-wd__header">'
      + '<button class="fw-wd__close" type="button" data-wd-close aria-label="关闭"><img src="' + WITHDRAW_ART + 'icon-close.png" alt=""></button>'
      + '<h1>零钱提现</h1>'
      + '<button class="fw-wd__more" type="button" data-wd-more aria-label="更多"><img src="' + WITHDRAW_ART + 'icon-more.png" alt=""></button>'
      + '</header>'
      + '<button class="fw-wd__bank" type="button" data-wd-bank>'
      + '<span class="fw-wd__bank-label">到账银行卡</span>'
      + '<span class="fw-wd__bank-cell">' + withdrawCardChip(card)
      + '<span class="fw-wd__bank-copy"><b>' + esc(card.name + '（' + card.tail + '）') + '</b><small>2小时内到账</small></span></span>'
      + '<img class="fw-wd__chev" src="' + WITHDRAW_ART + 'icon-chevron.png" alt="">'
      + '</button>'
      + '<section class="fw-wd__card">'
      + '<p class="fw-wd__amount-label">提现金额</p>'
      + '<div class="fw-wd__amount"><span class="fw-wd__yen">¥</span><span class="fw-wd__value' + (amount ? '' : ' is-empty') + '">' + esc(amount) + '</span></div>'
      + '<i class="fw-wd__rule"></i>'
      + '<p class="fw-wd__balance">当前零钱余额<button class="fw-wd__balance-num" type="button" data-wd-balance>' + esc(getBalanceAmount()) + '</button>元，<button class="fw-wd__all" type="button" data-wd-all>全部提现</button></p>'
      + '<i class="fw-wd__rule"></i>'
      + '<button class="fw-wd__fee" type="button" data-wd-fee>'
      + '<img class="fw-wd__coupon" src="' + WITHDRAW_ART + 'icon-coupon.png" alt="">'
      + '<span class="fw-wd__fee-copy"><b>提现服务费减免</b><small>前往获取提现免费额度</small></span>'
      + '<img class="fw-wd__chev" src="' + WITHDRAW_ART + 'icon-chevron.png" alt="">'
      + '</button>'
      + '</section>'
      + '<section class="fw-wd__keypad">' + withdrawKeypadHtml(true) + '</section>'
      + '</main>';
  }

  function withdrawResultPage() {
    const fields = withdrawSettings().fields || {};
    const pick = (key, fallback) => (fields[key] == null || fields[key] === '' ? fallback : String(fields[key]));
    const draft = withdrawDraftState();
    const card = draft.card || withdrawDefaultCard();
    const amount = withdrawAmount();
    const fee = withdrawFee(amount);
    return '<main class="fw-wd fw-wd--result">'
      + '<header class="fw-wd__rhead"><h1>零钱提现</h1></header>'
      + '<section class="fw-wd__steps">'
      + '<i class="fw-wd__rail" aria-hidden="true"></i>'
      + '<i class="fw-wd__rail is-green" aria-hidden="true"></i>'
      + '<button class="fw-wd__step is-first" type="button" data-wd-field="step1"><i class="fw-wd__dot is-green"></i><b>' + esc(pick('step1', '发起提现申请')) + '</b></button>'
      + '<button class="fw-wd__step is-current" type="button" data-wd-field="step2">'
      + '<img class="fw-wd__clock" src="' + WITHDRAW_ART + 'icon-clock.png" alt="">'
      + '<b>' + esc(pick('step2', '银行处理中')) + '</b>'
      + '<small data-wd-field-value="eta">' + esc(pick('eta', withdrawEtaText())) + '</small></button>'
      + '<button class="fw-wd__step is-last" type="button" data-wd-field="step3"><i class="fw-wd__dot is-gray"></i><b>' + esc(pick('step3', '到账成功')) + '</b></button>'
      + '</section>'
      + '<i class="fw-wd__rrule" aria-hidden="true"></i>'
      + '<section class="fw-wd__rows">'
      + '<button class="fw-wd__row" type="button" data-wd-field="amount"><span>提现金额</span><b>' + esc(pick('amount', '¥ ' + amount.toFixed(2))) + '</b></button>'
      + '<button class="fw-wd__row" type="button" data-wd-field="bank"><span>到账银行卡</span><b>' + esc(pick('bank', card.name + ' 尾号' + card.tail)) + '</b></button>'
      + '<button class="fw-wd__row" type="button" data-wd-field="fee"><span>服务费</span><b class="is-orange">' + esc(pick('fee', '本次提现免费')) + '</b></button>'
      + '<button class="fw-wd__row" type="button" data-wd-field="deduct"><span>服务费减免</span><b>' + esc(pick('deduct', '- ¥ ' + fee.toFixed(2))) + '</b></button>'
      + '</section>'
      + '<section class="fw-wd__banner">'
      + '<span class="fw-wd__banner-copy">' + esc(pick('banner', '本次提现已免费，领更多提现免费券')) + '</span>'
      + '<button class="fw-wd__claim" type="button" data-wd-claim>去领取</button>'
      + '</section>'
      + '<button class="fw-wd__done" type="button" data-wd-done>完成</button>'
      + '</main>';
  }

  function withdrawKey(key) {
    const draft = withdrawDraftState();
    let amount = String(draft.amount || '');
    if (key === 'del') {
      amount = amount.slice(0, -1);
    } else if (key === '.') {
      if (!amount) amount = '0.';
      else if (amount.indexOf('.') < 0) amount += '.';
    } else {
      if (amount === '0') amount = '';
      const dot = amount.indexOf('.');
      if (dot >= 0 && amount.length - dot > 2) return;
      if (dot < 0 && amount.length >= 7) return;
      amount += key;
    }
    draft.amount = amount;
  }

  function bindWithdraw(root) {
    const page = root.querySelector('.fw-wd');
    if (!page) return;
    if (withdrawStep === 'result') return bindWithdrawResult(page);
    const repaintAmount = () => {
      const node = page.querySelector('.fw-wd__value');
      const amount = String(withdrawDraftState().amount || '');
      node.textContent = amount;
      node.classList.toggle('is-empty', !amount);
    };
    page.querySelector('[data-wd-close]').addEventListener('click', () => closeWithdraw());
    page.querySelector('[data-wd-more]').addEventListener('click', () => payToast('演示页面'));
    page.querySelector('[data-wd-bank]').addEventListener('click', () => openWithdrawBankSheet());
    page.querySelector('[data-wd-balance]').addEventListener('click', () => editWithdrawBalance());
    page.querySelector('[data-wd-all]').addEventListener('click', () => {
      withdrawDraftState().amount = getBalanceAmount();
      repaintAmount();
    });
    page.querySelector('[data-wd-fee]').addEventListener('click', () => payToast('演示页面：提现免费额度'));
    page.querySelector('.fw-wd__keys').addEventListener('click', event => {
      const key = event.target.closest('[data-wd-key]');
      if (key) { withdrawKey(key.dataset.wdKey); repaintAmount(); return; }
      if (event.target.closest('[data-wd-confirm]')) confirmWithdraw();
    });
  }

  function bindWithdrawResult(page) {
    page.querySelector('[data-wd-done]').addEventListener('click', () => finishWithdraw());
    page.querySelector('[data-wd-claim]').addEventListener('click', () => payToast('演示页面：提现免费券'));
    page.querySelectorAll('[data-wd-field]').forEach(node => {
      node.addEventListener('click', () => editWithdrawResultField(node.dataset.wdField));
    });
    // 「预计到账时间」跟着当前时间走：没被手动改过就每 30 秒同步一次。
    if (withdrawEtaTimer) { clearInterval(withdrawEtaTimer); withdrawEtaTimer = 0; }
    if (!(withdrawSettings().fields || {}).eta) {
      withdrawEtaTimer = setInterval(() => {
        const node = document.querySelector('[data-wd-field-value="eta"]');
        if (!node || !node.isConnected) { clearInterval(withdrawEtaTimer); withdrawEtaTimer = 0; return; }
        node.textContent = withdrawEtaText();
      }, 30000);
    }
  }

  /** 结果页所有文案都能点着改；「预计到账时间」留空就恢复成跟随当前时间。 */
  function editWithdrawResultField(field) {
    const fields = withdrawSettings().fields || {};
    const draft = withdrawDraftState();
    const card = draft.card || withdrawDefaultCard();
    const amount = withdrawAmount();
    const fallback = {
      step1: '发起提现申请',
      step2: '银行处理中',
      step3: '到账成功',
      eta: withdrawEtaText(),
      amount: '¥ ' + amount.toFixed(2),
      bank: card.name + ' 尾号' + card.tail,
      fee: '本次提现免费',
      deduct: '- ¥ ' + withdrawFee(amount).toFixed(2)
    }[field];
    const titles = {
      step1: '修改「发起提现申请」', step2: '修改「银行处理中」', step3: '修改「到账成功」',
      eta: '修改预计到账时间', amount: '修改提现金额', bank: '修改到账银行卡',
      fee: '修改服务费', deduct: '修改服务费减免'
    };
    if (field === 'bank') { openWithdrawBankSheet(); return; }
    openWithdrawEditor({
      title: titles[field] || '修改',
      value: fields[field] == null ? fallback : String(fields[field]),
      placeholder: field === 'eta' ? '留空＝自动跟随当前时间（+2 小时）' : '',
      onSave: value => {
        const text = String(value || '').trim();
        const next = Object.assign({}, withdrawSettings().fields || {});
        if (!text) delete next[field]; else next[field] = text;
        saveWithdrawSettings({ fields: next });
        render();
        return true;
      }
    });
  }

  function editWithdrawBalance() {
    openWithdrawEditor({
      title: '修改零钱余额',
      value: getBalanceAmount(),
      numeric: true,
      onSave: value => {
        const amount = Number(String(value).replace(/[^0-9.]/g, ''));
        if (!Number.isFinite(amount) || amount < 0) return false;
        storeBalanceAmount(amount.toFixed(2), true);
        render();
        return true;
      }
    });
  }

  /** 小额输入面板（改余额 / 改结果页各行文字都走它）。 */
  function openWithdrawEditor(config) {
    const layer = document.createElement('div');
    layer.className = 'fw-wd-editor';
    layer.innerHTML = '<section class="fw-wd-editor__panel" role="dialog" aria-modal="true"><header>'
      + '<button type="button" data-wd-editor-cancel>取消</button><strong>' + esc(config.title || '修改') + '</strong>'
      + '<button type="button" data-wd-editor-save>保存</button></header>'
      + '<input class="fw-wd-editor__input" type="text" inputmode="' + (config.numeric ? 'decimal' : 'text') + '"'
      + ' value="' + esc(config.value == null ? '' : config.value) + '" placeholder="' + esc(config.placeholder || '') + '"></section>';
    const input = layer.querySelector('.fw-wd-editor__input');
    const close = () => layer.remove();
    layer.querySelector('[data-wd-editor-cancel]').addEventListener('click', close);
    layer.querySelector('[data-wd-editor-save]').addEventListener('click', () => {
      if (config.onSave(input.value) === false) { input.focus(); return; }
      close();
    });
    // 气泡阶段兜一层：面板内部自己的点击照常走，别再穿到下面那页去。
    layer.addEventListener('click', event => { if (event.target !== layer) event.stopPropagation(); });
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
    setTimeout(() => { input.focus(); input.select(); }, 30);
  }

  function openWithdrawBankSheet() {
    const current = withdrawDraftState().card || withdrawDefaultCard();
    const layer = document.createElement('div');
    layer.className = 'fw-wd-sheet';
    layer.innerHTML = '<section class="fw-wd-sheet__panel" role="dialog" aria-modal="true" aria-label="选择到账银行卡">'
      + '<header><strong>选择到账银行卡</strong><button type="button" data-wd-sheet-close aria-label="关闭">×</button></header>'
      + '<div class="fw-wd-sheet__rows">'
      + withdrawCards().map(card => '<button class="fw-wd-sheet__row' + (card.id === current.id ? ' is-active' : '') + '" type="button" data-wd-card="' + esc(card.id) + '">'
        + withdrawCardChip(card) + '<span class="fw-wd-sheet__copy"><b>' + esc(card.name) + '</b><small>尾号' + esc(card.tail) + '</small></span><i class="fw-wd-sheet__tick"></i></button>').join('')
      + '<button class="fw-wd-sheet__add" type="button" data-wd-card-add>+ 添加银行卡</button>'
      + '</div></section>';
    const close = () => layer.remove();
    layer.querySelector('[data-wd-sheet-close]').addEventListener('click', close);
    layer.querySelectorAll('[data-wd-card]').forEach(row => row.addEventListener('click', () => {
      const card = findWithdrawCard(row.dataset.wdCard);
      if (!card) return;
      withdrawDraftState().card = card;
      saveWithdrawSettings({ cardId: card.id });
      close();
      render();
    }));
    layer.querySelector('[data-wd-card-add]').addEventListener('click', () => { close(); go(routes.cards); });
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
  }

  function confirmWithdraw() {
    const amount = withdrawAmount();
    if (!amount) { payToast('请输入提现金额'); return; }
    if (amount > Number(getBalanceAmount())) { payToast('提现金额不能超过零钱余额'); return; }
    openWithdrawPassword();
  }

  function openWithdrawPassword() {
    if (withdrawOverlay) return;
    withdrawPassword = '';
    const layer = document.createElement('div');
    layer.className = 'fw-wdp';
    layer.innerHTML = '<section class="fw-wdp__panel" role="dialog" aria-modal="true" aria-label="请输入支付密码">'
      + '<header class="fw-wdp__header"><button class="fw-wdp__close" type="button" data-wdp-close aria-label="关闭"><img src="' + WITHDRAW_ART + 'icon-close.png" alt=""></button></header>'
      + '<h1 class="fw-wdp__title">请输入支付密码</h1>'
      + '<p class="fw-wdp__amount">¥ ' + esc(withdrawAmount().toFixed(2)) + '</p>'
      + '<div class="fw-wdp__dots">' + [0, 1, 2, 3, 4, 5].map(() => '<i></i>').join('') + '</div>'
      + '<div class="fw-wdp__keypad">' + withdrawKeypadHtml(false) + '</div>'
      + '</section>';
    document.body.appendChild(layer);
    withdrawOverlay = layer;
    layer.querySelector('[data-wdp-close]').addEventListener('click', () => closeWithdrawPassword());
    layer.querySelector('.fw-wd__keys').addEventListener('click', event => {
      const key = event.target.closest('[data-wd-key]');
      if (key) pushWithdrawPassword(key.dataset.wdKey);
    });
    requestAnimationFrame(() => requestAnimationFrame(() => layer.classList.add('is-open')));
  }

  function closeWithdrawPassword() {
    const layer = withdrawOverlay;
    withdrawOverlay = null;
    withdrawPassword = '';
    if (!layer) return false;
    layer.classList.remove('is-open');
    setTimeout(() => layer.remove(), 240);
    return true;
  }

  function pushWithdrawPassword(key) {
    const dots = withdrawOverlay ? withdrawOverlay.querySelectorAll('.fw-wdp__dots i') : [];
    if (key === 'del') {
      withdrawPassword = withdrawPassword.slice(0, -1);
    } else if (key !== '.' && withdrawPassword.length < 6) {
      withdrawPassword += key;
    }
    dots.forEach((dot, index) => dot.classList.toggle('is-on', index < withdrawPassword.length));
    if (withdrawPassword.length >= 6) setTimeout(completeWithdraw, 140);
  }

  function completeWithdraw() {
    closeWithdrawPassword();
    withdrawStep = 'result';
    render();
  }

  function finishWithdraw() {
    closeWithdrawPassword();
    withdrawStep = 'form';
    withdrawDraft = null;
    closeWithdraw();
  }

  function closeWithdraw() {
    const fromShell = entryRootRoute === routes.withdraw;
    withdrawDraft = null;
    withdrawStep = 'form';
    if (fromShell) {
      entryRootRoute = '';
      if (window.DoutuApp && typeof window.DoutuApp.returnToAppHome === 'function') {
        window.DoutuApp.returnToAppHome();
        return true;
      }
    }
    go('#/wechat/wallet');
    return true;
  }

  window.__fwOpenWithdraw = function () {
    withdrawDraft = null;
    withdrawStep = 'form';
    closeWithdrawPassword();
    if (currentRoute() === routes.withdraw) { render(); return true; }
    entryRootRoute = routes.withdraw;
    location.hash = '/wechat/wallet?withdraw=1';
    return true;
  };

  /* ===========================================================================
     银行卡管理 / 添加银行卡（原生首页宫格「银行卡管理」深链进来的整页）
     参考图 1440x3168 ⇒ CSS px = 参考像素 / 4。
     =========================================================================== */
  const BANK_CARDS_KEY = 'fw-bank-cards.v1';
  const BANKDIR = './assets/bankcards/';
  const BANK_DIRECTORY = [
    { id: 'abc', name: '农业银行' },
    { id: 'ccb', name: '建设银行', hint: '储蓄卡送3元立减金' },
    { id: 'icbc', name: '工商银行', hint: '信用卡送6元立减金起' },
    { id: 'psbc', name: '邮储银行' },
    { id: 'boc', name: '中国银行', hint: '储蓄卡送3元立减金起' },
    { id: 'cmb', name: '招商银行' },
    { id: 'bocom', name: '交通银行', hint: '储蓄卡送6元立减金' },
    { id: 'citic', name: '中信银行' }
  ];
  let bankAddFilter = '';

  const bankDirEntry = id => BANK_DIRECTORY.find(item => item.id === id) || null;
  const bankArt = id => BANKDIR + 'bank-' + (bankDirEntry(id) ? id : 'cmb') + '.png';
  const bankFaceArt = id => (id === 'cmb' ? BANKDIR + 'logo-cmb.png' : bankArt(id));
  function bankCards() {
    let list = null;
    try {
      const raw = localStorage.getItem(BANK_CARDS_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) list = parsed;
      }
    } catch (_) { list = null; }
    if (list) return list;
    // 第一次进来按参考图给两张招商卡
    const seeded = [
      { id: 'cmb1', bank: 'cmb', name: '招商银行储蓄卡', tail: '4646' },
      { id: 'cmb2', bank: 'cmb', name: '招商银行储蓄卡', tail: '5021' }
    ];
    saveBankCards(seeded);
    return seeded;
  }
  function saveBankCards(list) {
    try { localStorage.setItem(BANK_CARDS_KEY, JSON.stringify(list)); } catch (_) { /* 存不下就算了 */ }
    return list;
  }

  function bankCardsPage() {
    const cards = bankCards();
    return '<main class="fw-bc">'
      + '<header class="fw-bc__header">'
      + '<button class="fw-bc__back" type="button" data-bc-close aria-label="返回"><img src="' + BANKDIR + 'icon-back.png" alt=""></button>'
      + '<h1>银行卡</h1>'
      + '<button class="fw-bc__more" type="button" data-bc-more aria-label="更多"><img src="./assets/withdraw/icon-more.png" alt=""></button>'
      + '</header>'
      + '<div class="fw-bc__body">'
      + cards.map(card => '<button class="fw-bc__card" type="button" data-bc-card="' + esc(card.id) + '">'
        + '<img class="fw-bc__wm" src="' + BANKDIR + 'card-watermark.png" alt="">'
        + '<img class="fw-bc__logo" src="' + bankFaceArt(card.bank) + '" alt="">'
        + '<b class="fw-bc__name">' + esc(card.name) + '</b>'
        + '<span class="fw-bc__number"><i class="fw-bc__dots"><i></i><i></i><i></i></i><b>' + esc(card.tail) + '</b></span>'
        + '</button>').join('')
      + '<button class="fw-bc__add" type="button" data-bc-add><span class="fw-bc__plus"></span><b>添加银行卡</b><small>绑新卡送立减金</small></button>'
      + '</div>'
      + '<p class="fw-bc__footer">本服务由财付通提供</p>'
      + '</main>';
  }

  function bankAddPage() {
    const filter = bankAddFilter.trim();
    const list = BANK_DIRECTORY.filter(item => !filter || item.name.indexOf(filter) >= 0 || item.id.indexOf(filter.toLowerCase()) >= 0);
    return '<main class="fw-bc fw-ba">'
      + '<header class="fw-ba__header">'
      + '<button class="fw-ba__back" type="button" data-ba-back aria-label="返回"><img src="' + BANKDIR + 'icon-back.png" alt=""></button>'
      + '<button class="fw-ba__help" type="button" data-ba-help aria-label="帮助"><img src="' + BANKDIR + 'icon-help.png" alt=""></button>'
      + '</header>'
      + '<h1 class="fw-ba__title">添加银行卡</h1>'
      + '<div class="fw-ba__search"><input class="fw-ba__input" type="text" value="' + esc(bankAddFilter) + '" data-ba-input><img class="fw-ba__camera" src="' + BANKDIR + 'icon-camera.png" alt=""></div>'
      + '<p class="fw-ba__note">147家银行无需卡号即可添加</p>'
      + '<section class="fw-ba__list">'
      + list.map(item => '<button class="fw-ba__row" type="button" data-ba-bank="' + esc(item.id) + '">'
        + '<img class="fw-ba__icon" src="' + bankArt(item.id) + '" alt="">'
        + '<span class="fw-ba__name">' + esc(item.name) + '</span>'
        + (item.hint ? '<small class="fw-ba__hint">' + esc(item.hint) + '</small>' : '')
        + '<img class="fw-ba__chev" src="./assets/withdraw/icon-chevron.png" alt=""></button>').join('')
      + '<button class="fw-ba__row fw-ba__row--all" type="button" data-ba-all><span class="fw-ba__name">全部银行</span></button>'
      + '</section>'
      + '<p class="fw-bc__footer">本服务由财付通提供</p>'
      + '</main>';
  }

  function bindBankCardsPage(page) {
    page.querySelector('[data-bc-close]').addEventListener('click', () => closeBankCardsPage());
    page.querySelector('[data-bc-more]').addEventListener('click', () => payToast('演示页面'));
    page.querySelector('[data-bc-add]').addEventListener('click', () => { bankAddFilter = ''; go(routes.cardAdd); });
    page.querySelectorAll('[data-bc-card]').forEach(node => {
      const card = bankCards().find(item => item.id === node.dataset.bcCard);
      if (!card) return;
      node.addEventListener('click', () => openBankCardEditor(card));
      bindBankCardLongPress(node, card);
    });
  }

  function bindBankAddPage(page) {
    page.querySelector('[data-ba-back]').addEventListener('click', () => go(routes.cards));
    page.querySelector('[data-ba-help]').addEventListener('click', () => payToast('演示页面'));
    page.querySelectorAll('[data-ba-bank]').forEach(node => node.addEventListener('click', () => {
      const bank = bankDirEntry(node.dataset.baBank);
      if (bank) openBankCardTailEditor(bank);
    }));
    const all = page.querySelector('[data-ba-all]');
    if (all) all.addEventListener('click', () => payToast('演示页面：全部银行'));
    const input = page.querySelector('[data-ba-input]');
    if (input) {
      input.addEventListener('input', () => {
        bankAddFilter = input.value;
        const caret = input.selectionStart;
        render();
        const next = document.querySelector('[data-ba-input]');
        if (next) { next.focus(); try { next.setSelectionRange(caret, caret); } catch (_) {} }
      });
    }
  }

  /** 长按卡片：底部弹「删除该银行卡」。 */
  function bindBankCardLongPress(node, card) {
    let timer = 0;
    const clear = () => { if (timer) { clearTimeout(timer); timer = 0; } };
    const arm = () => {
      clear();
      timer = setTimeout(() => { timer = 0; openBankCardMenu(card); }, 420);
    };
    ['pointerdown', 'touchstart'].forEach(type => node.addEventListener(type, arm, { passive: true }));
    ['pointerup', 'pointerleave', 'pointercancel', 'touchend', 'touchcancel', 'touchmove'].forEach(type => node.addEventListener(type, clear, { passive: true }));
    node.addEventListener('contextmenu', event => { event.preventDefault(); openBankCardMenu(card); });
  }

  function openBankCardMenu(card) {
    const layer = document.createElement('div');
    layer.className = 'fw-wd-sheet';
    layer.innerHTML = '<section class="fw-wd-sheet__panel" role="dialog" aria-modal="true">'
      + '<div class="fw-bc-menu"><p>' + esc(card.name + '（' + card.tail + '）') + '</p>'
      + '<button class="is-danger" type="button" data-bc-delete>删除该银行卡</button>'
      + '<button type="button" data-bc-menu-cancel>取消</button></div></section>';
    const close = () => layer.remove();
    layer.querySelector('[data-bc-delete]').addEventListener('click', () => {
      saveBankCards(bankCards().filter(item => item.id !== card.id));
      close();
      render();
      payToast('已删除该银行卡');
    });
    layer.querySelector('[data-bc-menu-cancel]').addEventListener('click', close);
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
  }

  /** 点卡片：改银行名字和尾号。 */
  function openBankCardEditor(card) {
    const layer = document.createElement('div');
    layer.className = 'fw-wd-editor';
    layer.innerHTML = '<section class="fw-wd-editor__panel" role="dialog" aria-modal="true">'
      + '<header><button type="button" data-bc-edit-cancel>取消</button><strong>修改银行卡</strong><button type="button" data-bc-edit-save>保存</button></header>'
      + '<label class="fw-bc-field"><span>银行名称</span><input class="fw-wd-editor__input" data-bc-edit-name value="' + esc(card.name) + '"></label>'
      + '<label class="fw-bc-field"><span>卡号后四位</span><input class="fw-wd-editor__input" data-bc-edit-tail inputmode="numeric" value="' + esc(card.tail) + '"></label>'
      + '</section>';
    const close = () => layer.remove();
    layer.querySelector('[data-bc-edit-cancel]').addEventListener('click', close);
    layer.querySelector('[data-bc-edit-save]').addEventListener('click', () => {
      const name = layer.querySelector('[data-bc-edit-name]').value.trim();
      const tail = layer.querySelector('[data-bc-edit-tail]').value.replace(/[^0-9]/g, '').slice(-4);
      if (!name || tail.length !== 4) { payToast('请填写银行名称和四位尾号'); return; }
      const list = bankCards().map(item => (item.id === card.id ? Object.assign({}, item, { name: name, tail: tail }) : item));
      saveBankCards(list);
      close();
      render();
      payToast('已更新银行卡');
    });
    layer.addEventListener('click', event => { if (event.target !== layer) event.stopPropagation(); });
    layer.addEventListener('pointerdown', event => { if (event.target === layer) close(); });
    document.body.appendChild(layer);
  }

  /** 添加银行卡：选一家银行 → 只填四位数字 → 回到银行卡管理多一张卡。 */
  function openBankCardTailEditor(bank) {
    openWithdrawEditor({
      title: bank.name + ' · 卡号后四位',
      value: '',
      numeric: true,
      placeholder: '请输入卡号后四位',
      onSave: value => {
        const tail = String(value || '').replace(/[^0-9]/g, '').slice(-4);
        if (tail.length !== 4) { payToast('请输入四位数字'); return false; }
        const list = bankCards().slice();
        list.push({ id: 'card' + Date.now().toString(36), bank: bank.id, name: bank.name + '储蓄卡', tail: tail });
        saveBankCards(list);
        bankAddFilter = '';
        go(routes.cards);
        payToast('已添加' + bank.name + '储蓄卡');
        return true;
      }
    });
  }

  function closeBankCardsPage() {
    const fromShell = entryRootRoute === routes.cards || entryRootRoute === routes.cardAdd;
    bankAddFilter = '';
    if (fromShell) {
      entryRootRoute = '';
      if (window.DoutuApp && typeof window.DoutuApp.returnToAppHome === 'function') {
        window.DoutuApp.returnToAppHome();
        return true;
      }
    }
    go('#/wechat/wallet');
    return true;
  }

  // Android system Back should follow the in-app page stack instead of reopening a route.
  // The native shell deep-links into a pay page through this helper (home tiles).
  window.__fwOpenRoute = function (route) {
    const target = String(route == null ? '' : route).replace(/^#/, '').replace(/\/+$/, '');
    if (!target) return false;
    const normalized = '/' + target.replace(/^\/+/, '');
    // 只有 钱包 / 余额 / 账单 这一族页面是宫格深链的入口页；其它路由（比如 #/wechat
    // 小微主页）本来就停在原位，交给原生的 canGoBack 逻辑即可。
    entryRootRoute = (normalized === routes.wallet || normalized === routes.balance || normalized === routes.bills
        || normalized === routes.withdraw || normalized === routes.cards || normalized === routes.cardAdd)
      ? normalized : '';
    const next = '#' + target;
    if (location.hash === next) { render(); return true; }
    location.hash = next;
    return true;
  };
  /**
   * 宫格「年度账单」入口：先进账单页，再把它的「收支统计」浮层按 年账单 打开。
   * 这样返回键的顺序和点「自定义账单」完全一样（先收浮层 → 再退账单页 → 退回微主页）。
   */
  window.__fwOpenBillStats = function (mode) {
    const want = mode === 'year' ? 'year' : 'month';
    const open = () => { try { openBillStats({ mode: want }); } catch (_) { /* 打不开就算了 */ } };
    const target = '#' + routes.bills;
    // 已经停在账单页：直接开浮层，别再改一次 hash。
    if (currentRoute() === routes.bills) { render(); open(); return true; }
    entryRootRoute = routes.bills;
    location.hash = target;
    // hashchange 是异步的，等账单页渲染完再开浮层。
    setTimeout(open, 120);
    return true;
  };
  window.__fwHandleBack = function () {
    // 提现：密码面板 → 结果页 → 金额页，一层一层收。
    if (withdrawOverlay) { closeWithdrawPassword(); return true; }
    if (currentRoute() === routes.withdraw) {
      if (withdrawStep === 'result') { finishWithdraw(); return true; }
      return false;
    }
    // 银行卡：添加页 → 管理页 → 退回「微」宫格。
    if (currentRoute() === routes.cardAdd) { bankAddFilter = ''; go(routes.cards); return true; }
    if (currentRoute() === routes.cards) return false;
    const overlays = document.querySelectorAll('.fw-bills-menu,.fw-bill-batch,.fw-bill-stats,.fw-bill-picker,.fw-bill-editor');
    if (overlays.length) { const top = overlays[overlays.length - 1]; if (typeof top.__fwClose === 'function') top.__fwClose(); else top.remove(); return true; }
    const h = currentRoute();
    if (h === routes.payment) { payStack = []; location.hash = '#/wechat/service'; return true; }
    // 宫格深链的入口页在根部按返回 → 交回原生壳，直接退回「微」那一页。
    // 账单详情（bills/<id>）不算根部，仍然先退回账单列表。
    if (entryRootRoute && h === entryRootRoute
        && !(entryRootRoute === routes.bills && billDetailId())) {
      entryRootRoute = '';
      return false;
    }
    if (h === routes.balance || h === routes.wallet) {
      const parent = payStack.pop() || (h === routes.balance ? routes.wallet : routes.service);
      location.hash = '#' + parent;
      return true;
    }
    if (h === routes.bills) {
      if (billDetailId()) { location.hash = '#/wechat/wallet/bills'; return true; }
      payStack = [];
      location.hash = '#/wechat/wallet';
      return true;
    }
    if (h === routes.service) {
      payStack = [];
      location.hash = '#/wechat/my';
      return true;
    }
    return false;
  };
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape' && event.key !== 'Esc') return;
    const overlay = document.querySelector('.fw-bill-batch,.fw-bill-stats,.fw-bills-menu,.fw-bill-picker,.fw-bill-editor');
    if (!overlay) return;
    event.preventDefault();
    if (typeof overlay.__fwClose === 'function') overlay.__fwClose(); else overlay.remove();
  });
  window.addEventListener('hashchange', render); window.addEventListener('popstate', render);
  document.addEventListener('DOMContentLoaded', render);
  setTimeout(render, 0); setTimeout(render, 300); setTimeout(render, 1000);
  // 服务路由不再有宿主，别让这个巡检每 250ms 重跑一遍 render()。
  setInterval(() => { const h = currentRoute(); if (!h || h === routes.service) return; const existing = host(); if (!existing || existing.dataset.fwRoute !== h) render(); }, 250);

  // The bundled React pages render their own service/wallet DOM. Adjust that live DOM too.
  // 2026-10-10: this block used to sit *after* the module IIFE closed, so `currentRoute`
  // and `routes` were out of scope and it threw "currentRoute is not defined" on every DOM
  // mutation (which surfaced as fake-world's "出错啦" screen when a chat was opened).
  (function applyBundledPaySpacing() {
  const findExact = (text) => Array.from(document.querySelectorAll('body *')).find((el) => {
    const value = (el.textContent || '').trim();
    return value === text && el.children.length <= 1;
  });
  const apply = () => {
    // The bundled app reuses the word “服务” as a row on the Me tab. Never
    // apply page-level spacing there: the old text-only lookup matched the
    // app's outer grid and pushed the entire tab down, clipping its bottom.
    const route = currentRoute();
    if (route === routes.service) {
      syncBundledWalletPreview();
      alignBundledServiceCard();
      return;
    }
    // 2026-10-19: 钱包 / 零钱页是 bundled React 页面，顶部没有 safe-area，
    // 返回箭头和标题直接贴屏幕最上沿。这里不按 hash 判断：即使 hash 还停在上次
    // 的 ?bills=1，只要可见的钱包页 root 在 DOM 里，就补上顶部间距。
    const walletPage = Array.from(document.querySelectorAll('.relative.flex.flex-col.overflow-hidden'))
      .find((el) => {
        const r = el.getBoundingClientRect();
        return r.width >= innerWidth * 0.85 && r.height >= innerHeight * 0.8
          && (el.innerText.includes('零钱') || el.innerText.includes('钱包'));
      });
    if (walletPage) {
      walletPage.style.setProperty('padding-top', '40px', 'important');
      walletPage.style.setProperty('box-sizing', 'border-box', 'important');
      walletPage.dataset.fwTopSpacing = '1';
      return;
    }
    // 离开服务/钱包页：把之前加的顶部间距收回去（元素本身通常已经被 React 换掉了）。
    document.querySelectorAll('[data-fw-top-spacing="1"]').forEach((el) => {
      el.style.removeProperty('margin-top');
      el.style.removeProperty('padding-top');
      el.style.removeProperty('box-sizing');
      delete el.dataset.fwTopSpacing;
    });
  };
  apply();
  new MutationObserver(apply).observe(document.documentElement, { childList: true, subtree: true });
  setInterval(apply, 300);

  // 2026-10-19: bundled 钱包页右上角的「账单」只是一个 div，没有点击处理。
  // 在 capture 阶段命中顶部右侧的「账单」，直接进我们自己的账单页。
  document.addEventListener('click', (event) => {
    const route = currentRoute();
    if (route !== routes.wallet && route !== routes.balance) return;
    const target = event.target instanceof Element ? event.target : null;
    if (!target) return;
    let node = target;
    for (let i = 0; node && i < 4; i += 1, node = node.parentElement) {
      if (String(node.textContent || '').trim() !== '账单') continue;
      const r = node.getBoundingClientRect();
      if (r.top <= 120 && r.left >= innerWidth * 0.55) {
        event.preventDefault();
        event.stopImmediatePropagation();
        // 走 /wechat/wallet?bills=1：App 只看到已知的钱包路由，不会 404。
        location.hash = '/wechat/wallet?bills=1';
        return;
      }
      break;
    }
  }, true);

  // 2026-10-19: make the bundled 零钱 amount and the 零钱通 amount editable.
  // The bundled pages have no edit entry, and the custom pay host is hidden on iOS 14.3.
  const LQT_KEY = 'fw-lingqiantong-amount';
  const readLqtAmount = () => {
    const value = Number(localStorage.getItem(LQT_KEY));
    return Number.isFinite(value) && value >= 0 ? value.toFixed(2) : '1314.00';
  };
  function bundledLeaf(text) {
    return Array.from(document.querySelectorAll('body *')).find(el =>
      el.children.length === 0
      && !el.closest('#fw-pay-pages-host, .fw-receive-pay')
      && String(el.textContent || '').trim() === text);
  }
  function bundledWalletRowAmountNode(name) {
    const label = bundledLeaf(name);
    if (!label) return null;
    let row = label;
    for (let i = 0; row && i < 6; i += 1, row = row.parentElement) {
      const amount = Array.from(row.querySelectorAll('*')).find(el =>
        el.children.length === 0
        && /^[¥￥]\s*[\d,.]+$/.test(String(el.textContent || '').trim()));
      if (amount) return amount;
    }
    return null;
  }
  function bundledBalanceAmountNode() {
    return Array.from(document.querySelectorAll('.font-bold.text-4xl'))
      .find(el => !el.closest('#fw-pay-pages-host')
        && /^[\d,.]+$/.test(String(el.textContent || '').trim()));
  }
  function updateBundledBalance(value) {
    const node = bundledBalanceAmountNode();
    if (node) node.textContent = value;
    document.querySelectorAll('.fw-balance-amount').forEach(el => { el.textContent = value; });
    const zero = bundledWalletRowAmountNode('零钱');
    if (zero) zero.textContent = '¥' + value;
    try { syncBundledWalletPreview(); } catch (_) { /* service preview is optional */ }
  }
  function syncEditableAmounts() {
    if (document.querySelector('.fw-inline-amount-input')) return;
    const route = currentRoute();
    if (route === routes.balance) {
      const node = bundledBalanceAmountNode();
      if (node) {
        node.dataset.fwEditBalance = '1';
        node.title = '点击修改零钱金额';
        node.style.cursor = 'pointer';
        const shown = parseMoneyText(node.textContent);
        if (shown && !readBalanceRaw()) storeBalanceAmount(shown);
        const target = getBalanceAmount();
        if (shown !== target) node.textContent = target;
      }
      return;
    }
    if (route === routes.wallet) {
      const zero = bundledWalletRowAmountNode('零钱');
      if (zero) {
        const shown = parseMoneyText(zero.textContent);
        if (shown && !readBalanceRaw()) storeBalanceAmount(shown);
        const target = getBalanceAmount();
        if (shown !== target) zero.textContent = '¥' + target;
      }
      const lqt = bundledWalletRowAmountNode('零钱通');
      if (lqt) {
        lqt.dataset.fwEditLqt = '1';
        lqt.title = '点击修改零钱通金额';
        lqt.style.cursor = 'pointer';
        const shown = parseMoneyText(lqt.textContent);
        try {
          if (shown && !localStorage.getItem(LQT_KEY)) localStorage.setItem(LQT_KEY, shown);
        } catch (_) { /* storage may be unavailable */ }
        const target = readLqtAmount();
        if (shown !== target) lqt.textContent = '¥' + target;
      }
    }
  }
  // 2026-10-19: 点金额 -> 原位变成 input，直接弹数字键盘；输入过程写 localStorage，
  // 失焦/回车自动收尾并更新显示，不弹窗、不点保存按钮。
  let amountEditLockUntil = 0;
  let amountTouchStart = null;
  function amountTargetFrom(node) {
    if (!node || !(node instanceof Element) || node.closest('#fw-pay-pages-host')) return null;
    const route = currentRoute();
    if (route === routes.balance) {
      const hit = node.closest('[data-fw-edit-balance="1"]');
      return hit ? { node: hit, lqt: false } : null;
    }
    if (route === routes.wallet) {
      const hit = node.closest('[data-fw-edit-lqt="1"]');
      return hit ? { node: hit, lqt: true } : null;
    }
    return null;
  }
  function beginInlineAmountEdit(info) {
    const node = info.node;
    if (!node || document.querySelector('.fw-inline-amount-input')) return;
    const shown = parseMoneyText(node.textContent);
    if (shown === null) return;
    const cs = getComputedStyle(node);
    const rect = node.getBoundingClientRect();
    const input = document.createElement('input');
    input.className = 'fw-inline-amount-input';
    input.type = 'text';
    input.inputMode = 'decimal';
    input.setAttribute('inputmode', 'decimal');
    input.setAttribute('pattern', '[0-9.]*');
    input.autocomplete = 'off';
    input.autocapitalize = 'off';
    input.enterKeyHint = 'done';
    input.spellcheck = false;
    input.value = shown;
    input.style.fontSize = Math.max(16, Number.parseFloat(cs.fontSize) || 16) + 'px';
    input.style.flex = '0 0 auto';
    input.style.fontWeight = cs.fontWeight;
    input.style.lineHeight = cs.lineHeight;
    input.style.color = cs.color;
    input.style.width = Math.max(70, Math.round(rect.width) + 24) + 'px';
    input.style.height = Math.max(20, Math.round(rect.height)) + 'px';
    input.style.textAlign = info.lqt ? 'right' : 'left';
    node.style.visibility = 'hidden';
    const parent = node.parentElement;
    let parentOldPosition = null;
    if (parent) {
      if (getComputedStyle(parent).position === 'static') {
        parentOldPosition = parent.style.position;
        parent.style.position = 'relative';
      }
      parent.appendChild(input);
      const parentRect = parent.getBoundingClientRect();
      input.style.position = 'absolute';
      input.style.left = Math.round(rect.left - parentRect.left) + 'px';
      input.style.top = Math.round(rect.top - parentRect.top) + 'px';
      input.style.zIndex = '20';
    }
    let finished = false;
    const valueNow = () => parseMoneyText(input.value);
    const persist = () => {
      const value = valueNow();
      if (value === null) return false;
      if (info.lqt) {
        try { localStorage.setItem(LQT_KEY, value); } catch (_) { /* no-op */ }
      } else {
        storeBalanceAmount(value, true);
      }
      return true;
    };
    const restore = () => {
      input.remove();
      node.style.visibility = '';
      if (parent && parentOldPosition !== null) parent.style.position = parentOldPosition;
    };
    const finish = (commit) => {
      if (finished) return;
      finished = true;
      const value = valueNow();
      if (commit && value !== null) {
        persist();
        if (info.lqt) {
          node.textContent = '¥' + value;
          syncEditableAmounts();
          payToast('零钱通金额已保存');
        } else {
          updateBundledBalance(value);
          payToast('零钱金额已保存');
        }
      }
      restore();
    };
    let saveTimer = null;
    input.addEventListener('input', () => {
      clearTimeout(saveTimer);
      saveTimer = setTimeout(() => { persist(); }, 500);
    });
    input.addEventListener('blur', () => { clearTimeout(saveTimer); finish(true); });
    input.addEventListener('keydown', event => {
      if (event.key === 'Enter') { event.preventDefault(); input.blur(); }
      if (event.key === 'Escape') { event.preventDefault(); finished = true; restore(); }
    });
    input.focus();
    input.select();
  }
  function tryStartAmountEdit(target) {
    if (Date.now() < amountEditLockUntil) return false;
    const info = amountTargetFrom(target);
    if (!info) return false;
    amountEditLockUntil = Date.now() + 700;
    beginInlineAmountEdit(info);
    return true;
  }
  window.addEventListener('pointerdown', event => {
    amountTouchStart = { x: event.clientX, y: event.clientY };
  }, { capture: true, passive: true });
  window.addEventListener('pointerup', event => {
    if (amountTouchStart && Math.hypot(event.clientX - amountTouchStart.x, event.clientY - amountTouchStart.y) > 12) return;
    const target = event.target instanceof Element ? event.target : null;
    if (tryStartAmountEdit(target)) event.stopPropagation();
  }, true);
  window.addEventListener('touchstart', event => {
    const touch = event.changedTouches && event.changedTouches[0];
    amountTouchStart = touch ? { x: touch.clientX, y: touch.clientY } : null;
  }, { capture: true, passive: true });
  window.addEventListener('touchend', event => {
    const touch = event.changedTouches && event.changedTouches[0];
    if (!touch) return;
    if (amountTouchStart && Math.hypot(touch.clientX - amountTouchStart.x, touch.clientY - amountTouchStart.y) > 12) return;
    const hit = document.elementFromPoint(touch.clientX, touch.clientY);
    if (tryStartAmountEdit(hit)) {
      // 不 preventDefault：让 iOS 继续合成 click，click 里再 focus 一次确保键盘弹出。
      event.stopPropagation();
    }
  }, true);
  window.addEventListener('click', event => {
    const openInput = document.querySelector('.fw-inline-amount-input');
    if (openInput) {
      openInput.focus();
      event.stopImmediatePropagation();
      return;
    }
    const target = event.target instanceof Element ? event.target : null;
    if (tryStartAmountEdit(target)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);
  // 2026-10-19: bundled 钱包页的「银行卡」同样会直接跳 /wechat/wallet/cards，
  // React Router 不认这个子路由会 404。capture 掉它，改走钱包路由 + query。
  window.addEventListener('click', (event) => {
    if (currentRoute() !== routes.wallet) return;
    const target = event.target instanceof Element ? event.target : null;
    if (!target || target.closest('#fw-pay-pages-host')) return;
    const label = target.closest('.flex-1');
    const row = target.closest('.cursor-pointer') || (label && label.parentElement);
    if (!row || !String(row.textContent || '').includes('银行卡')) return;
    if (row.getBoundingClientRect().height > 120) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    location.hash = '/wechat/wallet?cards=1';
  }, true);

  // 2026-10-19: bundled 零钱页的「提现」按钮会直接跳到 /wechat/wallet/withdraw，
  // React Router 不认这个子路由会 404。capture 掉它，改走钱包路由 + query。
  window.addEventListener('click', (event) => {
    if (currentRoute() !== routes.balance) return;
    const target = event.target instanceof Element ? event.target : null;
    if (!target || target.closest('#fw-pay-pages-host')) return;
    const button = target.closest('.cursor-pointer, button');
    if (!button || String(button.textContent || '').trim() !== '提现') return;
    event.preventDefault();
    event.stopImmediatePropagation();
    location.hash = '/wechat/wallet?withdraw=1';
  }, true);
  setInterval(syncEditableAmounts, 300);
  new MutationObserver(syncEditableAmounts).observe(document.body, { childList: true, subtree: true });
  setTimeout(syncEditableAmounts, 0);
})();
})();
