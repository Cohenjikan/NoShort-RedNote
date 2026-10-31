// Paste into desktop Chrome DevTools Console on a xiaohongshu.com page (logged in).
// Copies a compact DOM outline (tag#id.class [x,y wxh position] "text") to the clipboard;
// repeated siblings are cut to two. Paste the result back to Claude.
// Best with DevTools device toolbar set to 400 x 869 so the layout matches the phone.
(() => {
  const lines = [location.href, innerWidth + 'x' + innerHeight];
  const seen = new Map();
  const name = (el) => el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.classList.length ? '.' + [...el.classList].join('.') : '');
  const walk = (el, d) => {
    if (d > 16 || lines.length > 2500) return;
    if (/^(SCRIPT|STYLE|LINK|META|NOSCRIPT|svg|path|use)$/i.test(el.tagName)) return;
    const n = name(el);
    const k = d + '|' + (el.parentElement ? name(el.parentElement) : '') + '|' + n;
    const c = (seen.get(k) || 0) + 1;
    seen.set(k, c);
    if (c > 2) return;
    const r = el.getBoundingClientRect();
    const st = getComputedStyle(el);
    const txt = [...el.childNodes].filter((x) => x.nodeType === 3).map((x) => x.textContent.trim()).join(' ').slice(0, 30);
    const href = el.getAttribute('href') ? ' href=' + el.getAttribute('href').slice(0, 60) : '';
    lines.push('  '.repeat(d) + n + href + ` [${Math.round(r.left)},${Math.round(r.top)} ${Math.round(r.width)}x${Math.round(r.height)}` +
      (st.position !== 'static' ? ' ' + st.position : '') + (st.display === 'none' ? ' NONE' : '') + ']' + (txt ? ' "' + txt + '"' : ''));
    for (const ch of el.children) walk(ch, d + 1);
  };
  walk(document.body, 0);
  copy(lines.join('\n'));
  return lines.length + ' lines copied';
})();
