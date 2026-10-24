(() => {
  const out = {
    title: document.title,
    href: location.href,
    dpr: window.devicePixelRatio,
    inner: [innerWidth, innerHeight],
    text: (document.body.innerText || "").slice(0, 600),
    canvases: [...document.querySelectorAll("canvas")].map(c => ({
      w: c.width, h: c.height, rect: c.getBoundingClientRect().toJSON()
    }))
  };
  const all = [...document.querySelectorAll("*")];
  out.count = all.length;
  const tags = {};
  for (const el of all) tags[el.tagName] = (tags[el.tagName] || 0) + 1;
  out.tags = tags;
  return out;
})()
