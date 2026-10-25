(() => {
  const el = [...document.querySelectorAll("uni-view")].find(e => (e.innerText || "").trim() === "招商模拟");
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return [r.x + r.width / 2, r.y + r.height / 2];
})()
