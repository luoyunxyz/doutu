(() => {
  const out = [];
  for (const el of document.querySelectorAll("*")) {
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) continue;
    if (r.y < 300 || r.y > 480) continue;
    if (r.height > 120) continue;
    const s = getComputedStyle(el);
    out.push({
      tag: el.tagName, cls: (el.className || "").toString().slice(0, 40),
      x: +r.x.toFixed(1), y: +r.y.toFixed(1),
      w: +r.width.toFixed(1), h: +r.height.toFixed(1),
      pad: s.padding, mar: s.margin, bd: s.borderBottomWidth + " " + s.borderBottomColor,
      bg: s.backgroundColor, disp: s.display
    });
  }
  out.sort((a, b) => a.y - b.y || a.x - b.x);
  return out;
})()
