(() => {
  const out = {
    pages: (() => { try { return getCurrentPages().map(p => ({ route: p.route, options: p.options })); } catch (e) { return String(e); } })(),
    bodyLen: document.body.innerHTML.length,
    text: (document.body.innerText || "").slice(0, 200),
    content: (() => { const c = document.querySelector(".content"); return c ? { h: c.getBoundingClientRect().height, text: (c.innerText || "").slice(0, 120), kids: c.children.length } : null; })(),
    form: document.querySelector(".form") ? true : false
  };
  return out;
})()
