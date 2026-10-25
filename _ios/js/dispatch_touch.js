(() => {
  const el = [...document.querySelectorAll("uni-view")].find(e => (e.innerText || "").trim() === "招商模拟");
  if (!el) return "no cell";
  const r = el.getBoundingClientRect();
  const cx = r.x + r.width / 2, cy = r.y + r.height / 2;
  const touch = new Touch({ identifier: 1, target: el, clientX: cx, clientY: cy, pageX: cx, pageY: cy });
  el.dispatchEvent(new TouchEvent("touchstart", { bubbles: true, cancelable: true, touches: [touch], targetTouches: [touch], changedTouches: [touch] }));
  el.dispatchEvent(new TouchEvent("touchend", { bubbles: true, cancelable: true, touches: [], targetTouches: [], changedTouches: [touch] }));
  el.dispatchEvent(new Event("tap", { bubbles: true }));
  return [cx, cy];
})()
