(() => {
  const texts = [...document.querySelectorAll("uni-text")].map(el => {
    const r = el.getBoundingClientRect();
    const span = el.querySelector("span") || el;
    const s = getComputedStyle(span);
    return {
      t: (el.innerText || "").trim().slice(0, 24),
      x: +r.x.toFixed(1), y: +r.y.toFixed(1),
      w: +r.width.toFixed(1), h: +r.height.toFixed(1),
      fs: s.fontSize, lh: s.lineHeight, fw: s.fontWeight, color: s.color
    };
  });
  return texts;
})()
