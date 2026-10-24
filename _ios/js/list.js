(() => {
  const rows = [...document.querySelectorAll(".list")].map(el => {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    return {
      t: (el.innerText || "").replace(/\s+/g, " ").trim().slice(0, 40),
      y: +r.y.toFixed(1), h: +r.height.toFixed(1),
      pad: s.padding, mar: s.margin,
      border: s.borderBottom, lh: s.lineHeight, fs: s.fontSize,
      box: s.boxSizing
    };
  });
  const rules = [];
  for (const sheet of document.styleSheets) {
    let list;
    try { list = sheet.cssRules; } catch (e) { continue; }
    for (const rule of list) {
      if (rule.selectorText && /(^|[,\s])\.list([\s,{:]|$)/.test(rule.selectorText)) {
        rules.push(rule.cssText);
      }
    }
  }
  return { rows, rules: rules.slice(0, 20) };
})()
