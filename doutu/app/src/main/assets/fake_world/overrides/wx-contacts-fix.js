/**
 * 通讯录页对齐补丁（对照真机，选择器来自 CDP 实测 DOM）：
 * - .wechat-search-row：真机页头下没有搜索框（只有搜索图标）→ 隐藏
 * - 「仅聊天的朋友」行：真机没有 → 隐藏
 * - .wechat-topbar 右侧补「＋」按钮 → 点击进入「新的朋友」
 */
(function () {
  function norm(value) {
    return String(value == null ? '' : value).replace(/\s+/g, '').trim();
  }

  function leafWith(text) {
    var nodes = document.querySelectorAll('body *');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.children && el.children.length) continue;
      if (el.getBoundingClientRect().height < 1) continue;
      if (norm(el.textContent) === text) return el;
    }
    return null;
  }

  function leafContaining(text) {
    var nodes = document.querySelectorAll('body *');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.children && el.children.length) continue;
      if (el.getBoundingClientRect().height < 1) continue;
      if (norm(el.textContent).indexOf(text) >= 0) return el;
    }
    return null;
  }

  function injectStyle() {
    if (document.getElementById('fw-contacts-style')) return;
    var style = document.createElement('style');
    style.id = 'fw-contacts-style';
    style.textContent = '.wechat-search-row{display:none !important}'
      + '.wechat-topbar__actions{padding-right:34px !important}';
    document.head.appendChild(style);
  }

  function hideRow(label) {
    var leaf = leafWith(label);
    if (!leaf) return;
    var row = leaf.closest('[class*="group/item"]') || leaf.parentElement;
    if (row) row.style.display = 'none';
  }

  function addPlus() {
    if (document.getElementById('fw-contacts-plus')) return;
    var topbar = document.querySelector('.wechat-topbar');
    if (!topbar) return;
    var btn = document.createElement('button');
    btn.id = 'fw-contacts-plus';
    btn.type = 'button';
    btn.textContent = '＋';
    btn.style.cssText = 'position:absolute;right:4px;top:50px;width:34px;height:34px;'
      + 'background:transparent;border:0;font-size:24px;line-height:1;color:#111;padding:0;z-index:20;';
    topbar.style.position = 'relative';
    btn.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      var leaf = leafWith('新的朋友');
      if (!leaf) return;
      var row = leaf.closest('[class*="group/item"]') || leaf;
      row.click();
    });
    topbar.appendChild(btn);
  }

  /** 真机在功能行之后还有「我的企业及企业联系人 → 企业微信联系人」分组。 */
  function addCompanyGroup() {
    if (document.getElementById('fw-contacts-company')) return;
    var anchorLeaf = leafWith('公众号') || leafContaining('公众号');
    if (!anchorLeaf) return;
    var host = anchorLeaf;
    for (var i = 0; i < 5 && host; i++) {
      if (host.offsetHeight >= 40) break;
      host = host.parentElement;
    }
    if (!host) return;
    var box = document.createElement('div');
    box.id = 'fw-contacts-company';
    box.style.cssText = 'background:#fff;';
    box.innerHTML = '<div style="padding:7px 16px;font-size:12px;color:#888;background:#f7f7f7;">'
      + '我的企业及企业联系人</div>'
      + '<div style="display:flex;align-items:center;padding:11px 16px;border-bottom:1px solid #eee;">'
      + '<span style="width:40px;height:40px;border-radius:6px;background:#2f7ff5;color:#fff;'
      + 'display:flex;align-items:center;justify-content:center;font-size:20px;">💬</span>'
      + '<span style="flex:1;margin-left:12px;font-size:16px;color:#111;">企业微信联系人</span>'
      + '<span style="color:#bbb;font-size:16px;">›</span></div>';
    // 找到纵向排列的行容器，把分组插在「公众号」这一行之后
    var node = host;
    for (var k = 0; k < 6 && node; k++) {
      var parent = node.parentElement;
      if (!parent) break;
      if (getComputedStyle(parent).flexDirection === 'column' && parent.children.length >= 3) {
        parent.insertBefore(box, node.nextSibling);
        return;
      }
      node = parent;
    }
    host.insertAdjacentElement('afterend', box);
  }

  function polish() {
    // 只在通讯录路由生效，避免把「＋」「隐藏搜索框」带到微信首页
    if (String(location.hash || '').indexOf('contacts') < 0) return;
    if (norm(document.body && document.body.innerText).indexOf('通讯录') < 0) return;
    if (!document.querySelector('.wechat-topbar')) return;
    injectStyle();
    hideRow('仅聊天的朋友');
    addPlus();
  }

  setInterval(polish, 500);
})();


/* ── 创建单聊：原生写完好友后，由这里替他点进通讯录 → 找到好友 → 发消息 ── */
(function () {
  var KEY = 'fw-open-chat-after-create';
  function leaf(text) {
    var nodes = document.querySelectorAll('div,span');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.children && el.children.length) continue;
      if ((el.textContent || '').trim() === text) return el;
    }
    return null;
  }
  function rowOf(el) {
    var row = el;
    for (var k = 0; k < 5 && row; k++) {
      if (row.offsetHeight >= 40) return row;
      row = row.parentElement;
    }
    return el;
  }
  function tick() {
    var raw = null;
    try { raw = localStorage.getItem(KEY); } catch (e) { return; }
    if (!raw) return;
    var name = '';
    try { name = JSON.parse(raw).name || ''; } catch (e) { }
    if (!name) { try { localStorage.removeItem(KEY); } catch (e) { } return; }
    if (String(location.hash || '').indexOf('/wechat/contacts') < 0) {
      location.hash = '#/wechat/contacts';
      return;
    }
    var send = leaf('发消息');
    if (send) { send.click(); try { localStorage.removeItem(KEY); } catch (e) { } return; }
    var target = leaf(name);
    if (target) rowOf(target).click();
  }
  setInterval(tick, 700);
})();

/* 群聊右上角「…」→ 打开原生「群组设置」页 */
(function () {
  if (window.__fwGroupSettingsHook) return;
  window.__fwGroupSettingsHook = true;

  function headerTitle() {
    try {
      var nodes = document.querySelectorAll('div,span');
      var best = '';
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        if (el.children && el.children.length) continue;
        var r = el.getBoundingClientRect();
        if (r.top < 0 || r.top > 140 || r.width < 8) continue;
        var text = (el.textContent || '').trim();
        if (!text || text === '微信') continue;
        // 顶栏标题一般在水平中间区域
        if (r.left > 90 && r.left < 300) return text;
        if (!best) best = text;
      }
      return best;
    } catch (e) { }
    return '';
  }

  function isGroupTitle(title) {
    if (!title) return false;
    if (/[(（]\s*\d+\s*[)）]/.test(title)) return true;
    try {
      var list = JSON.parse(localStorage.getItem('fw-group-names') || '[]');
      return Array.isArray(list) && list.indexOf(title) >= 0;
    } catch (e) { return false; }
  }

  document.addEventListener('click', function (e) {
    try {
      if (String(location.hash || '').indexOf('/wechat/conversation/') < 0) return;
      var el = e.target;
      var r = el && el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      if (!r || r.top > 120 || r.left < 300) return;
      // 只有群聊才接管：群标题形如「再歪一点(100)」，一对一聊天交给克隆自己处理
      if (!isGroupTitle(headerTitle())) return;
      var bridge = window.DoutuApp;
      if (bridge && bridge.openGroupSettings) {
        e.preventDefault();
        e.stopPropagation();
        bridge.openGroupSettings();
      }
    } catch (err) { }
  }, true);
})();
