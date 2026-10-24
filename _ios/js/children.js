(() => {
  const row = document.querySelector(".flxes");
  const wrap = row && row.parentElement;
  if (!wrap) return "no wrap";
  return [...wrap.children].slice(0, 8).map(el => {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    return {
      tag: el.tagName, cls: (el.className || "").toString().slice(0, 30),
      y: +r.y.toFixed(1), h: +r.height.toFixed(1),
      mar: s.margin, pad: s.padding, bd: s.borderBottom,
      text: (el.innerText || "").replace(/\s+/g, " ").trim().slice(0, 20)
    };
  });
})()
