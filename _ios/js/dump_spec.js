(() => {
  const root = document.querySelector(".content");
  if (!root) return { error: "no .content" };
  const rt = root.getBoundingClientRect();
  const ops = [];
  const clip = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  for (const el of root.querySelectorAll("*")) {
    const r = el.getBoundingClientRect();
    if (r.width < 0.5 || r.height < 0.5) continue;
    const x = +(r.x - rt.x).toFixed(2);
    const y = +(r.y - rt.y).toFixed(2);
    const w = +r.width.toFixed(2);
    const h = +r.height.toFixed(2);
    if (y > 820 || x > 420) continue;
    const s = getComputedStyle(el);
    const bg = s.backgroundColor;
    if (bg && bg !== "rgba(0, 0, 0, 0)" && bg !== "transparent") {
      ops.push({ k: "r", x, y, w, h, c: bg,
                 r: parseFloat(s.borderTopLeftRadius) || 0 });
    }
    const bw = parseFloat(s.borderBottomWidth);
    if (bw > 0 && s.borderBottomStyle !== "none") {
      ops.push({ k: "l", x, y: y + h, w, c: s.borderBottomColor });
    }
    const text = [...el.childNodes]
      .filter(n => n.nodeType === 3 && n.textContent.trim())
      .map(n => n.textContent.trim())
      .join("");
    if (text) {
      ops.push({ k: "t", x, y, w, h,
                 s: parseFloat(s.fontSize),
                 c: s.color,
                 b: (parseInt(s.fontWeight) || 400) >= 600,
                 a: s.textAlign,
                 t: text.slice(0, 100) });
    }
    if (el.tagName === "IMG" || el.tagName === "UNI-IMAGE") {
      const src = el.getAttribute("src") || "";
      if (src) ops.push({ k: "i", x, y, w, h, src });
    }
  }
  return { w: 360, h: 820, ops };
})()
