(() => {
  const content = document.querySelector(".content");
  const root = content ? (content.parentElement || content) : null;
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
      let src = el.getAttribute("src") || "";
      if (src) {
        src = src.replace(
          "file:///data/user/0/uni.UNIE8A9C59/files/apps/__UNI__E8A9C59/www/",
          "http://192.168.1.96:8000/android_assets/");
        ops.push({ k: "i", x, y, w, h, src });
      }
    }
  }

  // 行内文字合并：安卓把「转给 / 李强」拆成多个 span，各自按自己的盒子居中，
  // 在 iOS 上分别绘制会重叠；按同一行、x 区间重叠的规则合并成一条。
  const texts = ops.filter(o => o.k === "t");
  const others = ops.filter(o => o.k !== "t");
  texts.sort((a, b) => a.y - b.y || a.x - b.x);
  const merged = [];
  for (const t of texts) {
    const last = merged[merged.length - 1];
    if (last && Math.abs(t.y - last.y) < 2 &&
        t.x < last.x + last.w + 8 && t.x + t.w > last.x - 8) {
      last.t += t.t;
      const x1 = Math.min(last.x, t.x);
      const x2 = Math.max(last.x + last.w, t.x + t.w);
      last.x = x1;
      last.w = x2 - x1;
      if (last.a === "center" || t.a === "center") last.a = "center";
      last.s = Math.max(last.s, t.s);
      last.b = last.b || t.b;
    } else {
      merged.push(Object.assign({}, t));
    }
  }
  return { w: 360, h: 820, ops: others.concat(merged) };
})()
