(() => {
  const el = [...document.querySelectorAll("uni-text")]
    .find(e => /6221|622143/.test(e.innerText || ""));
  if (!el) return "not found";
  const chain = [];
  let p = el;
  for (let i = 0; i < 8 && p; i++) {
    const r = p.getBoundingClientRect();
    const s = getComputedStyle(p);
    chain.push({
      tag: p.tagName,
      cls: p.className,
      rect: [+r.x.toFixed(1), +r.y.toFixed(1), +r.width.toFixed(1), +r.height.toFixed(1)],
      bg: s.backgroundColor,
      pad: s.padding,
      border: s.borderTopWidth + " " + s.borderTopColor,
      fs: s.fontSize,
      color: s.color,
      disp: s.display
    });
    p = p.parentElement;
  }
  return chain;
})()
