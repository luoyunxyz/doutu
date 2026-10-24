(() => {
  const out = [];
  for (const el of document.querySelectorAll("*")) {
    const direct = [...el.childNodes]
      .filter(n => n.nodeType === 3 && n.textContent.trim())
      .map(n => n.textContent.trim())
      .join("");
    if (!direct) continue;
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) continue;
    if (r.y < 300 || r.y > 680) continue;
    const s = getComputedStyle(el);
    out.push({
      t: direct.slice(0, 40),
      cls: el.className,
      x: +r.x.toFixed(1), y: +r.y.toFixed(1),
      w: +r.width.toFixed(1), h: +r.height.toFixed(1),
      fs: s.fontSize, lh: s.lineHeight, fw: s.fontWeight,
      pad: s.padding, mar: s.margin, border: s.borderBottom,
      color: s.color, bg: s.backgroundColor
    });
  }
  out.sort((a, b) => a.y - b.y || a.x - b.x);
  return out;
})()
