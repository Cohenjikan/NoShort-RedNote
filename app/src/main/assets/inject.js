/*
 * xhs-lite injection layer.
 *
 * Every Xiaohongshu-specific selector lives in SEL below and in inject.css. When the site changes,
 * edit only those two files. Selectors were checked 2026-11-01 against the logged-in site at 400x869
 * with tools/probe/probe.mjs; re-run it to compare after a site update.
 *
 * The app evaluates this file twice per page: at document start and again in onPageFinished. It
 * also runs again on every SPA route change. That is safe because the second run only updates
 * the config and calls apply().
 *
 * Inputs (set by MainActivity right before this file):
 *   window.__XL_CFG = { hideFeed: bool, hideRelated: bool, debug: bool }
 *   window.__XL_CSS = contents of inject.css
 * In eruda: __xl.probe() counts matches for every selector; __xl.log holds the last 50 events.
 */
(function () {
  'use strict';

  if (window.__xl) {
    window.__xl.setConfig(window.__XL_CFG || {});
    if (window.__XL_CSS) window.__xl.setCss(window.__XL_CSS);
    window.__xl.apply('reinject');
    return;
  }

  // ---------------------------------------------------------------- selectors
  var SEL = {
    // feed container (#exploreFeeds on /explore, #redFeeds on /red_video, also search results) and its cards
    feed: '#exploreFeeds, #redFeeds, .feeds-container',
    feedItem: 'section.note-item',
    // open note: always .note-detail-mask > #noteContainer, whether opened from a feed or by URL
    note: '.note-detail-mask, #noteContainer',
    // login modal / page
    login: '.login-container',
    // the site's own "只看图文" switch on the homepage feed; carries .checked-border while on
    imageOnly: '#image-note-filter-el .graphic-filter',
    imageOnlyOn: 'checked-border',
    // controls that would jump to the previous/next note. None exist at 400 px (2026-11); kept as a net.
    noteSwitch: [
      '.note-detail-mask [class*="next-note"]',
      '.note-detail-mask [class*="prev-note"]',
      '.note-detail-mask [class*="note-switch"]',
      '.note-detail-mask [class*="switch-note"]',
      '[class*="auto-play-next"]',
      '[class*="autoplay-next"]'
    ].join(','),
    // Places the user may legitimately tap to open another note (feed card, related card, any link)
    openNoteTap: 'a, section.note-item'
  };

  // Text of "open/download the app" nags. Used as a fallback for nags the CSS does not catch.
  var APP_NAG = /(打开|下载|前往|去)\s*(小红书)?\s*App|App\s*内(打开|查看)|在\s*App\s*(中|里)/i;
  // Never hide anything that looks like login (phone + code, QR code)
  var LOGIN = /登录|验证码|扫码|手机号/;

  // Note URLs: /explore/<id>, /red_video/<id>, /search_result/<id>, /discovery/item/<id>
  var NOTE_RE = /^\/(?:explore|red_video|search_result|discovery\/item)\/([0-9a-f]{16,32})/i;
  // Pages outside home / search / notes that are an endless stream: sent back to the homepage
  var BLOCKED_RE = /^\/(?:livelist|live)(?:\/|$)/i;
  // video-only feed, sent back to the homepage while the image-only switch is on
  var VIDEO_FEED_RE = /^\/red_video\/?$/i;

  // ---------------------------------------------------------------- state
  var cfg = {};
  var css = '';
  var log = [];
  var lastOpenTap = 0;
  var lastUrl = location.href;
  var lastFilterClick = 0;

  function note(msg) {
    log.push(new Date().toISOString().slice(11, 19) + ' ' + msg);
    if (log.length > 50) log.shift();
    if (cfg.debug) console.log('[xl] ' + msg);
  }

  function noteId(url) {
    try {
      var m = NOTE_RE.exec(new URL(url, location.href).pathname);
      return m ? m[1] : null;
    } catch (e) { return null; }
  }

  // ---------------------------------------------------------------- 0. viewport + stylesheet
  function ensureViewport() {
    var head = document.head;
    if (!head) return;
    var want = 'width=device-width, initial-scale=1, maximum-scale=5';
    var m = head.querySelector('meta[name="viewport"]');
    if (!m) {
      m = document.createElement('meta');
      m.name = 'viewport';
      head.insertBefore(m, head.firstChild);
    }
    if (m.content !== want) m.content = want;
  }

  function ensureStyle() {
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.getElementById('xl-style');
    if (!s) {
      s = document.createElement('style');
      s.id = 'xl-style';
    }
    if (s.textContent !== css) s.textContent = css;
    // stay last in <head> so our rules win ties against stylesheets the SPA adds later
    if (s.parentNode !== parent || s !== parent.lastElementChild) parent.appendChild(s);
  }

  // ---------------------------------------------------------------- route + config classes on <html>
  function routeClass() {
    var p = location.pathname;
    if (NOTE_RE.test(p)) return 'xl-r-note';
    if (/^\/search_result/.test(p)) return 'xl-r-search';
    if (p === '/' || /^\/(?:explore|red_video)\/?$/.test(p)) return 'xl-r-explore';
    return 'xl-r-other';
  }

  function setClasses() {
    var h = document.documentElement;
    if (!h) return;
    var want = {
      'xl': true,
      // creator.xiaohongshu.com: the publishing site behind the 发布 button, laid out for desktop only
      'xl-creator': location.host === 'creator.xiaohongshu.com',
      'xl-r-note': false, 'xl-r-search': false, 'xl-r-explore': false, 'xl-r-other': false,
      'xl-hide-feed': !!cfg.hideFeed,
      'xl-hide-related': !!cfg.hideRelated,
      'xl-image-only': !!cfg.imageOnly,
      'xl-hide-publish': !!cfg.hidePublish,
      // a note can be open as a modal over the feed while the path still looks like the feed
      'xl-note-open': !!document.querySelector(SEL.note)
    };
    want[routeClass()] = true;
    for (var k in want) if (h.classList.contains(k) !== want[k]) h.classList.toggle(k, want[k]);
  }

  // ---------------------------------------------------------------- 1. two-column feed fallback
  // The site lays out its own waterfall from window width. At 400px it should give 2 columns by
  // itself. If it doesn't, inject.css .xl-grid-fallback switches the feed to a plain 2-column grid.
  function checkColumns() {
    var items = document.querySelectorAll(SEL.feedItem);
    if (items.length < 4) return;
    var lefts = {};
    var n = 0;
    var seen = 0;
    for (var i = 0; i < items.length && i < 16; i++) {
      var r = items[i].getBoundingClientRect();
      if (r.width === 0) continue; // hidden (e.g. the hide-feed switch)
      seen++;
      var k = Math.round(r.left / 10);
      if (!lefts[k]) { lefts[k] = true; n++; }
    }
    if (seen < 4) return;
    var h = document.documentElement;
    if (h.classList.contains('xl-grid-fallback')) return; // once on, keep it on for this page
    if (n !== 2) {
      h.classList.add('xl-grid-fallback');
      note('feed has ' + n + ' columns, grid fallback on');
    }
  }

  // ---------------------------------------------------------------- 1b. image notes only
  // Uses the site's own "只看图文" filter, so the server sends only image notes and the waterfall has
  // no holes. It doesn't survive a reload or a channel switch, so it is pressed again whenever it's off.
  function filterButton() { return document.querySelector(SEL.imageOnly); }

  function enforceImageOnly() {
    if (!cfg.imageOnly) return;
    if (VIDEO_FEED_RE.test(location.pathname)) {
      location.replace('/explore');
      return;
    }
    if (routeClass() !== 'xl-r-explore') return;
    var b = filterButton();
    if (!b || b.classList.contains(SEL.imageOnlyOn)) return;
    if (Date.now() - lastFilterClick < 3000) return; // let the last press land before trying again
    lastFilterClick = Date.now();
    b.click();
    note('pressed 只看图文');
  }

  // ---------------------------------------------------------------- 1c. settings gear in the top bar
  // Opens the app's settings page (Android, through the xlBridge message channel MainActivity adds)
  // or the userscript's panel (window.__xlOpenSettings). Long-pressing the top-right corner still works.
  var GEAR_SVG = '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>';

  function openSettings() {
    if (typeof window.__xlOpenSettings === 'function') window.__xlOpenSettings();
    else if (window.xlBridge) window.xlBridge.postMessage('settings');
  }

  function ensureGear() {
    var bar = document.querySelector('.header-container');
    if (!bar) return;
    var g = document.getElementById('xl-gear');
    if (!g) {
      g = document.createElement('div');
      g.id = 'xl-gear';
      g.setAttribute('role', 'button');
      g.setAttribute('aria-label', '设置');
      g.innerHTML = GEAR_SVG;
      g.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        openSettings();
      });
    }
    if (g.parentNode !== bar) bar.appendChild(g);
  }

  // ---------------------------------------------------------------- 2. app nags the CSS missed
  function isOverlay(el) {
    var st = getComputedStyle(el);
    return st.position === 'fixed' || st.position === 'sticky';
  }

  function hideNags(root) {
    var list = (root || document.body || document).querySelectorAll('div, section, aside, a, span');
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el.__xlSeen) continue;
      el.__xlSeen = true;
      var t = el.textContent || '';
      if (t.length > 40 || !APP_NAG.test(t) || LOGIN.test(t)) continue;
      if (el.querySelector('input, canvas, img[src*="qrcode"], iframe')) continue;
      // hide the closest fixed/sticky box that holds the nag; leave nags inside page content alone
      var box = el;
      for (var d = 0; box && d < 6 && box !== document.body; d++, box = box.parentElement) {
        if (isOverlay(box)) {
          if (LOGIN.test(box.textContent || '')) break;
          box.classList.add('xl-hidden');
          note('hid nag: ' + t.trim().slice(0, 20));
          break;
        }
      }
    }
    // a hidden modal may have left the page scroll-locked
    var b = document.body;
    if (b && getComputedStyle(b).overflow === 'hidden' && !document.querySelector(SEL.note) && !visibleLogin()) {
      b.style.setProperty('overflow', 'auto', 'important');
    }
  }

  function visibleLogin() {
    var m = document.querySelector(SEL.login);
    return !!(m && m.getBoundingClientRect().height > 0);
  }

  // ---------------------------------------------------------------- 3. no switching to the next note
  // a) controls: hidden in CSS; clicks on them are also swallowed here in case CSS misses one
  function blockSwitchClick(e) {
    if (e.target.closest && e.target.closest(SEL.noteSwitch)) {
      e.stopImmediatePropagation();
      e.preventDefault();
      note('blocked switch button');
      return;
    }
    if (e.target.closest && e.target.closest(SEL.openNoteTap)) lastOpenTap = Date.now();
  }

  // b) wheel / keys that page between notes while one is open (no effect on touch, but cheap)
  function noteOpen() { return !!document.querySelector(SEL.note) || NOTE_RE.test(location.pathname); }

  function blockKeys(e) {
    if (!noteOpen()) return;
    var t = e.target;
    if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return;
    if (/^(ArrowUp|ArrowDown|PageUp|PageDown)$/.test(e.key)) {
      e.stopImmediatePropagation();
      note('blocked key ' + e.key);
    }
  }

  function blockWheel(e) {
    if (!noteOpen()) return;
    // let any element that can still scroll in that direction have the wheel
    for (var el = e.target; el && el !== document.documentElement; el = el.parentElement) {
      if (el.scrollHeight > el.clientHeight + 1) {
        var oy = getComputedStyle(el).overflowY;
        if (oy === 'auto' || oy === 'scroll') {
          if (e.deltaY > 0 && el.scrollTop + el.clientHeight < el.scrollHeight - 1) return;
          if (e.deltaY < 0 && el.scrollTop > 0) return;
        }
      }
    }
    e.stopImmediatePropagation();
    e.preventDefault();
  }

  // c) catch-all that needs no selectors: going from one note straight to another note without
  //    the user tapping a card or link (autoplay-next, swipe, a button we haven't found) is undone.
  function guardNav(toUrl) {
    var from = noteId(location.href);
    var to = noteId(toUrl);
    if (!from || !to || from === to) return true;
    if (Date.now() - lastOpenTap < 1500) return true;
    note('blocked note ' + from + ' -> ' + to);
    return false;
  }

  function isBlocked(url) {
    try { return BLOCKED_RE.test(new URL(url, location.href).pathname); } catch (e) { return false; }
  }

  function hookHistory() {
    ['pushState', 'replaceState'].forEach(function (name) {
      var orig = history[name];
      history[name] = function (state, title, url) {
        if (url != null && isBlocked(String(url))) {
          // the SPA renders that page whatever the URL says, so leave it for the homepage
          note('blocked route ' + url);
          setTimeout(function () { location.replace('/explore'); }, 0);
          return;
        }
        if (url != null && !guardNav(String(url))) {
          // the SPA has already decided to render the next note; reload the current one instead
          var back = location.href;
          setTimeout(function () { location.replace(back); }, 0);
          return;
        }
        var r = orig.apply(this, arguments);
        onRoute(name);
        return r;
      };
    });
    window.addEventListener('popstate', function () { onRoute('popstate'); });
  }

  // ---------------------------------------------------------------- driver
  var scheduled = false;
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    setTimeout(function () {
      scheduled = false;
      apply('mutation');
    }, 120);
  }

  function onRoute(why) {
    if (location.href === lastUrl) return;
    lastUrl = location.href;
    note('route ' + why + ' ' + location.pathname);
    document.documentElement.classList.remove('xl-grid-fallback');
    apply('route');
  }

  function apply(why) {
    try {
      ensureViewport();
      ensureStyle();
      setClasses();
      if (document.body) {
        ensureGear();
        enforceImageOnly();
        hideNags();
        if (routeClass() === 'xl-r-explore') checkColumns();
      }
    } catch (e) {
      note('apply(' + why + ') failed: ' + e);
    }
  }

  function start() {
    if (isBlocked(location.href)) {
      location.replace('/explore');
      return;
    }
    hookHistory();
    document.addEventListener('click', blockSwitchClick, true);
    window.addEventListener('keydown', blockKeys, true);
    window.addEventListener('wheel', blockWheel, { capture: true, passive: false });
    var mo = new MutationObserver(schedule);
    mo.observe(document.documentElement, { childList: true, subtree: true });
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () { apply('dom'); });
    }
    apply('start');
  }

  function probe() {
    var out = {};
    for (var k in SEL) {
      try { out[k] = document.querySelectorAll(SEL[k]).length; } catch (e) { out[k] = 'bad selector'; }
    }
    out.route = routeClass();
    out.htmlClass = document.documentElement.className;
    out.viewport = innerWidth + 'x' + innerHeight + ' @' + devicePixelRatio;
    return out;
  }

  window.__xl = {
    version: 1,
    SEL: SEL,
    log: log,
    probe: probe,
    apply: apply,
    openSettings: openSettings,
    setConfig: function (c) {
      var feedWas = !!cfg.hideFeed;
      var imageWas = !!cfg.imageOnly;
      cfg = c || {};
      // switched off: release the site's filter we pressed
      if (imageWas && !cfg.imageOnly) {
        var b = filterButton();
        if (b && b.classList.contains(SEL.imageOnlyOn)) b.click();
      }
      // the waterfall laid itself out at zero size while hidden and can't recover; reload the feed.
      // (hiding it with visibility instead would keep the "load more" sentinel firing in the background)
      if (feedWas && !cfg.hideFeed && routeClass() === 'xl-r-explore') setTimeout(function () { location.reload(); }, 50);
    },
    setCss: function (c) { css = c || ''; }
  };
  window.__xl.setConfig(window.__XL_CFG || {});
  window.__xl.setCss(window.__XL_CSS || '');

  // a missing <html> means a document-start run on a blank document; wait for it
  if (document.documentElement) start();
  else document.addEventListener('readystatechange', start, { once: true });
})();
