(() => {
  const row = document.querySelector(".flxes");
  if (!row) return "no .flxes";
  const out = [];
  let p = row;
  for (let i = 0; i < 4 && p; i++) {
    const s = getComputedStyle(p);
    const r = p.getBoundingClientRect();
    out.push({
      tag: p.tagName, cls: (p.className || "").toString(),
      rect: [+r.x.toFixed(1), +r.y.toFixed(1), +r.width.toFixed(1), +r.height.toFixed(1)],
      disp: s.display, gap: s.gap, rowGap: s.rowGap, fd: s.flexDirection,
      pad: s.padding, mar: s.margin, h: s.height
    });
    p = p.parentElement;
  }
  return out;
})()
