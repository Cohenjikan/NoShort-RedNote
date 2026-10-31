// Packs the app's injection layer (assets/inject.js + inject.css) into a userscript for Safari on
// iPhone / iPad with the Userscripts extension. Same selectors and rules as the Android app; this file
// only adds the metadata block and a settings panel (long-press the top-right corner, like the app).
//
//   node tools/build-userscript.mjs [version] > noshort-rednote.user.js
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'app/src/main/assets');
const js = fs.readFileSync(path.join(assets, 'inject.js'), 'utf8');
const css = fs.readFileSync(path.join(assets, 'inject.css'), 'utf8');
const version = process.argv[2] || new Date().toISOString().slice(0, 10).replaceAll('-', '.');
const latest = 'https://github.com/Cohenjikan/NoShort-RedNote/releases/latest/download/noshort-rednote.user.js';

// Settings panel, userscript only (the Android app has a native settings page instead).
// Switches are kept in localStorage under 'xl-cfg'.
const panel = String.raw`
  var KEY = 'xl-cfg';
  function loadCfg() {
    try { return JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) { return {}; }
  }
  function saveCfg(c) {
    try { localStorage.setItem(KEY, JSON.stringify(c)); } catch (e) {}
  }

  function openPanel() {
    if (document.getElementById('xl-panel')) return;
    var dark = matchMedia('(prefers-color-scheme: dark)').matches;
    var fg = dark ? '#F5F5F5' : '#0D0D0D', muted = dark ? '#9A9A9A' : '#6E6E6E';
    var bg = dark ? '#161616' : '#FFFFFF', line = dark ? '#232323' : '#E8E8E8';
    var cfg = loadCfg();
    var wrap = document.createElement('div');
    wrap.id = 'xl-panel';
    wrap.style.cssText = 'position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.35);display:flex;align-items:flex-start;justify-content:center;padding-top:72px;font-family:-apple-system,system-ui,sans-serif;-webkit-text-size-adjust:100%';
    var box = document.createElement('div');
    box.style.cssText = 'width:calc(100% - 32px);max-width:400px;background:' + bg + ';color:' + fg + ';border-radius:12px;overflow:hidden;box-shadow:0 8px 30px rgba(0,0,0,.25)';
    function row(key, title, sub) {
      var r = document.createElement('div');
      r.style.cssText = 'display:flex;align-items:center;gap:12px;padding:12px 16px;cursor:pointer;border-top:1px solid ' + line;
      var t = document.createElement('div');
      t.style.cssText = 'flex:1';
      t.innerHTML = '<div style="font-size:15px;line-height:1.45"></div><div style="font-size:13px;line-height:1.45;color:' + muted + '"></div>';
      t.children[0].textContent = title;
      t.children[1].textContent = sub;
      // drawn switch: the site's own CSS strips native checkboxes
      var sw = document.createElement('div');
      var knob = document.createElement('div');
      sw.setAttribute('role', 'switch');
      sw.className = 'xl-switch';
      sw.appendChild(knob);
      function paint() {
        var on = !!cfg[key];
        sw.setAttribute('aria-checked', String(on));
        sw.style.cssText = 'flex:none;position:relative;width:44px;height:26px;border-radius:13px;transition:background .15s;background:' + (on ? '#D77757' : line);
        knob.style.cssText = 'position:absolute;top:3px;width:20px;height:20px;border-radius:10px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.3);transition:left .15s;left:' + (on ? '21px' : '3px');
      }
      paint();
      r.onclick = function () {
        cfg[key] = !cfg[key];
        saveCfg(cfg);
        apply(cfg);
        paint();
      };
      r.appendChild(t);
      r.appendChild(sw);
      return r;
    }
    var head = document.createElement('div');
    head.style.cssText = 'display:flex;align-items:center;padding:14px 16px';
    head.innerHTML = '<div style="flex:1;font-size:17px;line-height:1.45">NoShort-RedNote</div>';
    var close = document.createElement('div');
    close.textContent = '完成';
    close.style.cssText = 'font-size:15px;color:#D77757;padding:4px 0 4px 16px;cursor:pointer';
    close.onclick = function () { wrap.remove(); };
    head.appendChild(close);
    box.appendChild(head);
    box.appendChild(row('hideFeed', '只留搜索', '隐藏首页频道和推荐流'));
    box.appendChild(row('hideRelated', '隐藏猜你想搜', '笔记正文下面的搜索推荐'));
    var foot = document.createElement('div');
    foot.style.cssText = 'padding:10px 16px 14px;font-size:11px;line-height:1.45;color:' + muted + ';border-top:1px solid ' + line;
    foot.textContent = '版本 ' + VERSION + ' · 长按页面右上角打开这里';
    box.appendChild(foot);
    wrap.appendChild(box);
    wrap.addEventListener('click', function (e) { if (e.target === wrap) wrap.remove(); });
    document.documentElement.appendChild(wrap);
  }

  function apply(c) {
    window.__XL_CFG = { hideFeed: !!c.hideFeed, hideRelated: !!c.hideRelated, debug: false };
    if (window.__xl) {
      window.__xl.setConfig(window.__XL_CFG);
      window.__xl.apply('settings');
    }
  }

  // long-press within 64 px of the top-right corner for ~0.9 s; only watches, the page gets the touch too
  (function () {
    var timer = 0, x0 = 0, y0 = 0;
    function cancel() { clearTimeout(timer); timer = 0; }
    window.addEventListener('pointerdown', function (e) {
      cancel();
      if (e.clientX < innerWidth - 64 || e.clientY > 64) return;
      x0 = e.clientX; y0 = e.clientY;
      timer = setTimeout(function () { timer = 0; openPanel(); }, 900);
    }, true);
    window.addEventListener('pointermove', function (e) {
      if (timer && (Math.abs(e.clientX - x0) > 10 || Math.abs(e.clientY - y0) > 10)) cancel();
    }, true);
    window.addEventListener('pointerup', cancel, true);
    window.addEventListener('pointercancel', cancel, true);
  })();
`;

const out = `// ==UserScript==
// @name         NoShort-RedNote
// @namespace    https://github.com/Cohenjikan/NoShort-RedNote
// @version      ${version}
// @description  没有无限刷视频的小红书：只留首页瀑布流、搜索和笔记详情。长按页面右上角打开设置。
// @author       Cohenjikan
// @homepageURL  https://github.com/Cohenjikan/NoShort-RedNote
// @updateURL    ${latest}
// @downloadURL  ${latest}
// @match        https://www.xiaohongshu.com/*
// @match        https://xiaohongshu.com/*
// @run-at       document-start
// @inject-into  page
// @noframes
// @grant        none
// ==/UserScript==

// Generated by tools/build-userscript.mjs from app/src/main/assets/inject.js + inject.css. Don't edit here.
(function () {
  'use strict';
  var VERSION = ${JSON.stringify(version)};
${panel}
  apply(loadCfg());
  window.__XL_CSS = ${JSON.stringify(css)};

  // ---- app/src/main/assets/inject.js ----
${js}
})();
`;

process.stdout.write(out);
