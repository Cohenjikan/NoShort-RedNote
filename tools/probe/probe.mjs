// Desktop stand-in for the app: Chrome at the phone's size (400x869 CSS px, dpr 3) with the app's
// desktop UA, injecting the same bundle MainActivity injects. Login lives in ./profile (gitignored).
//
//   node probe.mjs login                 open a window; scan the QR code with the phone; closes when logged in
//   node probe.mjs shot <url> [name]     headless: load, inject, screenshot + DOM outline into ./out/<name>*
//   node probe.mjs shot <url> [name] --raw            same without injection
//   node probe.mjs shot <url> [name] --open-note      also tap the first feed card and capture the open note
import { chromium } from 'playwright-core';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const assets = path.join(here, '../../app/src/main/assets');
const outDir = path.join(here, 'out');
const profile = path.join(here, 'profile');
const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36';
const SHIM = "try{Object.defineProperty(Navigator.prototype,'platform',{get:function(){return 'Win32'},configurable:true});}catch(e){}\n";

const [cmd, ...rest] = process.argv.slice(2);
const flags = new Set(rest.filter((a) => a.startsWith('--')));
const [url = 'https://www.xiaohongshu.com/explore', name = 'page'] = rest.filter((a) => !a.startsWith('--'));
const cfg = { hideFeed: flags.has('--hide-feed'), hideRelated: flags.has('--hide-related'), imageOnly: flags.has('--image-only'), debug: true };

function bundle() {
  const js = fs.readFileSync(path.join(assets, 'inject.js'), 'utf8');
  const css = fs.readFileSync(path.join(assets, 'inject.css'), 'utf8');
  return `window.__XL_CFG=${JSON.stringify(cfg)};window.__XL_CSS=${JSON.stringify(css)};\n${js}`;
}

async function open(headless) {
  return chromium.launchPersistentContext(profile, {
    executablePath: CHROME,
    headless,
    viewport: { width: 400, height: 869 },
    deviceScaleFactor: headless ? 3 : 1,
    isMobile: true,
    hasTouch: true,
    userAgent: UA,
    locale: 'zh-CN',
  });
}

const OUTLINE = () => {
  const lines = [location.href];
  const seen = new Map();
  const nm = (el) => el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (el.classList.length ? '.' + [...el.classList].join('.') : '');
  const walk = (el, d) => {
    if (d > 18 || lines.length > 3000) return;
    if (/^(SCRIPT|STYLE|LINK|META|NOSCRIPT|svg|path|use)$/i.test(el.tagName)) return;
    const n = nm(el);
    const k = d + '|' + (el.parentElement ? nm(el.parentElement) : '') + '|' + n;
    const c = (seen.get(k) || 0) + 1;
    seen.set(k, c);
    if (c > 2) return;
    const r = el.getBoundingClientRect();
    const st = getComputedStyle(el);
    const txt = [...el.childNodes].filter((x) => x.nodeType === 3).map((x) => x.textContent.trim()).join(' ').slice(0, 30);
    const href = el.getAttribute('href') ? ' href=' + el.getAttribute('href').slice(0, 50) : '';
    lines.push('  '.repeat(d) + n + href + ` [${Math.round(r.left)},${Math.round(r.top)} ${Math.round(r.width)}x${Math.round(r.height)}` +
      (st.position !== 'static' ? ' ' + st.position : '') + (st.display === 'none' ? ' NONE' : '') + ']' + (txt ? ' "' + txt + '"' : ''));
    for (const ch of el.children) walk(ch, d + 1);
  };
  walk(document.body, 0);
  return lines.join('\n');
};

async function capture(page, tag) {
  await page.screenshot({ path: path.join(outDir, `${tag}.png`) });
  fs.writeFileSync(path.join(outDir, `${tag}-outline.txt`), await page.evaluate(OUTLINE));
  const info = await page.evaluate(() => ({
    url: location.href,
    scrollWidth: document.documentElement.scrollWidth,
    probe: window.__xl ? window.__xl.probe() : null,
    log: window.__xl ? window.__xl.log.slice(-15) : null,
  }));
  console.log(tag, JSON.stringify(info, null, 1));
}

if (cmd === 'login') {
  const ctx = await open(false);
  const page = ctx.pages()[0] || (await ctx.newPage());
  await page.goto('https://www.xiaohongshu.com/explore');
  console.log('waiting for login (scan the QR code with the phone)...');
  const until = Date.now() + 10 * 60 * 1000;
  let ok = false;
  while (Date.now() < until) {
    await page.waitForTimeout(2000);
    const st = await page.evaluate(() => ({
      login: !!document.querySelector('.login-container') || location.pathname.startsWith('/login'),
      cards: document.querySelectorAll('.note-item, section').length,
    })).catch(() => null);
    if (st && !st.login && st.cards > 0) { ok = true; break; }
  }
  await page.waitForTimeout(3000);
  await ctx.close();
  console.log(ok ? 'LOGGED IN' : 'TIMED OUT');
  process.exit(ok ? 0 : 1);
}

if (cmd === 'shot') {
  fs.mkdirSync(outDir, { recursive: true });
  const ctx = await open(true);
  const raw = flags.has('--raw');
  // --userscript: the packed Safari userscript (tools/build-userscript.mjs) instead of the app's bundle
  const us = flags.has('--userscript');
  if (us) await ctx.addInitScript({ content: execFileSync(process.execPath, [path.join(here, '../build-userscript.mjs'), 'test'], { encoding: 'utf8' }) });
  else if (!raw) await ctx.addInitScript({ content: SHIM + bundle() });
  const page = ctx.pages()[0] || (await ctx.newPage());
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(7000);
  if (!raw && !us) await page.evaluate(bundle()); // what onPageFinished does
  await page.waitForTimeout(1000);
  const tag = name + (raw ? '-raw' : '');
  await capture(page, tag);
  if (flags.has('--open-note')) {
    const card = page.locator(flags.has('--video') ? 'section.note-item:has(.play-icon) a.cover' : 'section.note-item a.cover').first();
    await card.click({ timeout: 10000 });
    await page.waitForTimeout(5000);
    await capture(page, tag + '-note');
  }
  // steps, in order: --click=<selector> --type=<text> --press=<key> --wait=<ms> --shot=<suffix> --eval=<js>
  for (const a of rest) {
    const [k, ...v] = a.split('=');
    const val = v.join('=');
    if (k === '--click') await page.locator(val).first().click({ timeout: 10000 });
    else if (k === '--type') await page.keyboard.type(val, { delay: 50 });
    else if (k === '--press') await page.keyboard.press(val);
    else if (k === '--wait') await page.waitForTimeout(Number(val));
    else if (k === '--shot') await capture(page, tag + '-' + val);
    else if (k === '--hold') { const [x, y] = val.split(',').map(Number); await page.mouse.move(x, y); await page.mouse.down(); await page.waitForTimeout(1100); await page.mouse.up(); }
    else if (k === '--eval') console.log('eval:', JSON.stringify(await page.evaluate(val), null, 1));
  }
  await ctx.close();
}
