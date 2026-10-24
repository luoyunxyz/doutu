(() => {
  const root = document.querySelector(".content");
  if (!root) return { error: "no .content" };
  const rt = root.getBoundingClientRect();
  const items = [];
  for (const el of root.querySelectorAll("*")) {
    const direct = [...el.childNodes]
      .filter(n => n.nodeType === 3 && n.textContent.trim())
      .map(n => n.textContent.trim())
      .join("");
    if (!direct) continue;
    const r = el.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) continue;
    const s = getComputedStyle(el);
    items.push({
      t: direct.slice(0, 60),
      x: +(r.x - rt.x).toFixed(2),
      y: +(r.y - rt.y).toFixed(2),
      w: +r.width.toFixed(2),
      h: +r.height.toFixed(2),
      fs: parseFloat(s.fontSize),
      fw: s.fontWeight,
      color: s.color
    });
  }
  items.sort((a, b) => a.y - b.y || a.x - b.x);
  const rows = [];
  for (const it of items) {
    const last = rows[rows.length - 1];
    if (last && Math.abs(it.y - last.y) <= 1.6) last.items.push(it);
    else rows.push({ y: it.y, items: [it] });
  }
  return { h: +rt.height.toFixed(2), top: +rt.y.toFixed(2), rows, count: items.length };
})()
