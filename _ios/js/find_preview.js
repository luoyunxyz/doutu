(() => {
  const out = [];
  for (const el of document.querySelectorAll("*")) {
    const r = el.getBoundingClientRect();
    if (r.width < 340 || r.height < 80) continue;
    const s = getComputedStyle(el);
    out.push({
      tag: el.tagName, cls: (el.className || "").toString().slice(0, 50),
      y: +r.y.toFixed(1), h: +r.height.toFixed(1), w: +r.width.toFixed(1),
      disp: s.display, vis: s.visibility, ov: s.overflow,
      kids: el.children.length,
      text: (el.innerText || "").replace(/\s+/g, " ").slice(0, 24)
    });
  }
  out.sort((a, b) => a.y - b.y);
  return out;
})()
