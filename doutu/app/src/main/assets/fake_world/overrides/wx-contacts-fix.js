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

  function injectStyle() {
    if (document.getElementById('fw-contacts-style')) return;
    var style = document.createElement('style');
    style.id = 'fw-contacts-style';
    style.textContent = '.wechat-search-row{display:none !important}';
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
    btn.style.cssText = 'position:absolute;right:6px;top:50%;transform:translateY(-50%);'
      + 'background:transparent;border:0;font-size:24px;line-height:1;color:#111;padding:0 8px;z-index:20;';
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

  function polish() {
    if (norm(document.body && document.body.innerText).indexOf('通讯录') < 0) return;
    if (!document.querySelector('.wechat-topbar')) return;
    injectStyle();
    hideRow('仅聊天的朋友');
    addPlus();
  }

  setInterval(polish, 500);
})();
