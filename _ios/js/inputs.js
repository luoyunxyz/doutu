(() => {
  const root = document.querySelector(".content");
  if (!root) return { error: "no .content" };
  const rt = root.getBoundingClientRect();
  const out = [];
  for (const el of root.querySelectorAll("input, textarea, uni-input, uni-textarea")) {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    out.push({
      tag: el.tagName,
      v: el.value !== undefined ? el.value : (el.innerText || ""),
      ph: el.placeholder || "",
      x: +(r.x - rt.x).toFixed(2),
      y: +(r.y - rt.y).toFixed(2),
      w: +r.width.toFixed(2),
      h: +r.height.toFixed(2),
      fs: s.fontSize,
      color: s.color,
      align: s.textAlign
    });
  }
  return { top: +rt.y.toFixed(2), count: out.length, inputs: out };
})()
