/* Smart Watchlist - front-end. Vanilla JS, no build step. */
(() => {
  'use strict';

  const $ = (s, r = document) => r.querySelector(s);
  const REFRESH_MS = 20000;
  const REDUCED = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const STARTER = ['RELIANCE.NS', 'TCS.NS', 'INFY.NS', 'HDFCBANK.NS', 'AAPL', 'NVDA'];
  const POPULAR = [
    ['TCS.NS', 'Tata Consultancy Services', 'NSE'], ['RELIANCE.NS', 'Reliance Industries', 'NSE'], ['INFY.NS', 'Infosys', 'NSE'],
    ['HDFCBANK.NS', 'HDFC Bank', 'NSE'], ['AAPL', 'Apple', 'NASDAQ'], ['NVDA', 'NVIDIA', 'NASDAQ'], ['TSLA', 'Tesla', 'NASDAQ'], ['MSFT', 'Microsoft', 'NASDAQ'],
  ];

  const store = {
    get(k, d) { try { const v = localStorage.getItem('sw.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set(k, v) { try { localStorage.setItem('sw.' + k, JSON.stringify(v)); } catch (e) { /* storage may be unavailable */ } },
  };
  function userId() {
    let id = null;
    try { id = localStorage.getItem('sw.uid'); } catch (e) { /* ignore */ }
    if (!id) {
      id = (crypto.randomUUID && crypto.randomUUID()) || 'u-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 12);
      try { localStorage.setItem('sw.uid', id); } catch (e) { /* ignore */ }
    }
    return id;
  }

  const S = {
    uid: userId(), data: null, cards: new Map(), filter: 'all', sort: store.get('sort', 'added'), view: store.get('view', 'grid'),
    detail: null, busy: false, failures: 0, timer: 0, hiddenSince: 0, pulseKey: '', marketsKey: '', radarKey: '', heatKey: '',
    sg: { items: [], idx: -1, seq: 0 }, chart: null, lastFocus: null, demoDismissed: false,
    user: store.get('user', null), authMode: 'login',
  };

  /* ───────────── helpers ───────────── */
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const short = (s) => s.replace(/\.(NS|BO)$/, '');
  const isIdx = (s) => s.startsWith('^');
  const hue = (s) => { let h = 0; for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360; return h; };
  const initials = (s) => short(s).replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase();
  const dir = (v) => (v > 0 ? 'up' : v < 0 ? 'down' : 'flat');
  const sgn = (v, d = 2) => (v > 0 ? '+' : '') + v.toFixed(d);
  const arrow = (v) => (v > 0 ? '▲' : v < 0 ? '▼' : '•');
  const nfs = {};
  function money(v, ccy, sym) {
    if (v == null || isNaN(v)) return '-';
    try {
      const loc = ccy === 'INR' ? 'en-IN' : 'en-US';
      if (!ccy || isIdx(sym || '')) return v.toLocaleString(loc, { maximumFractionDigits: 2, minimumFractionDigits: 2 });
      const f = nfs[ccy] || (nfs[ccy] = new Intl.NumberFormat(loc, { style: 'currency', currency: ccy, minimumFractionDigits: 2, maximumFractionDigits: 2 }));
      return f.format(v);
    } catch (e) { return v.toFixed(2) + ' ' + ccy; }
  }
  const compact = (v) => new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(v);
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

  function animateNumber(el, from, to, fmt, ms) {
    if (REDUCED || from == null || from === to) { el.textContent = fmt(to); return; }
    const t0 = performance.now(), dur = ms || 550;
    const tick = (t) => {
      const k = clamp((t - t0) / dur, 0, 1), e = 1 - Math.pow(1 - k, 3);
      el.textContent = fmt(from + (to - from) * e);
      if (k < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  }

  const ICON = {
    x: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>',
    bolt: '<svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L4 14h6l-1 8 9-12h-6z"/></svg>',
    check: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.7 2.7L16 9.5"/></svg>',
    chart: '<svg width="38" height="38" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" style="color:var(--accent)"><path d="M3 17l6-7 4 4 8-9"/><path d="M15 5h6v6"/></svg>',
    info: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8h.01M11 12h1v4h1"/></svg>',
  };

  const cacheKey = () => 'dash:' + (S.user ? S.user.email : 'guest');

  /* ───────────── API ───────────── */
  async function api(path, o) {
    o = o || {};
    const res = await fetch('api' + path, {
      method: o.method || 'GET',
      headers: Object.assign({ 'X-User-Id': S.uid }, o.body ? { 'Content-Type': 'application/json' } : {}),
      body: o.body ? JSON.stringify(o.body) : undefined,
    });
    if (res.status === 204) return null;
    const data = await res.json().catch(() => null);
    if (!res.ok) throw new Error((data && data.error) || 'Request failed (' + res.status + ')');
    return data;
  }

  /* ───────────── toasts & banners ───────────── */
  function toast(msg, type, action) {
    const box = $('#toasts');
    while (box.children.length >= 3) box.firstChild.remove();
    const el = document.createElement('div');
    el.className = 'toast ' + (type || '');
    el.innerHTML = (type ? '<i>' + (type === 'err' ? '!' : '✓') + '</i>' : '') + '<span>' + esc(msg) + '</span>' + (action ? '<button>' + esc(action.label) + '</button>' : '');
    box.appendChild(el);
    const kill = () => { el.classList.add('out'); setTimeout(() => el.remove(), 260); };
    if (action) el.querySelector('button').onclick = () => { action.fn(); kill(); };
    setTimeout(kill, action ? 6500 : 3800);
  }
  function banner(id, html, cls) {
    let el = $('#bn-' + id);
    if (!html) { if (el) el.remove(); return; }
    if (!el) { el = document.createElement('div'); el.id = 'bn-' + id; $('#banners').appendChild(el); }
    el.className = 'banner ' + (cls || '');
    el.innerHTML = html + '<button class="cl" aria-label="Dismiss">×</button>';
    el.querySelector('.cl').onclick = () => { if (id === 'demo') S.demoDismissed = true; if (id === 'signup') store.set('signupDismissed', true); el.remove(); };
  }

  /* ───────────── cards ───────────── */
  function cardEl(s, i) {
    const el = document.createElement('article');
    el.className = 'stock card';
    el.dataset.sym = s.symbol;
    el.tabIndex = 0;
    el.setAttribute('role', 'button');
    el.setAttribute('aria-label', 'Open details for ' + short(s.symbol));
    el.style.setProperty('--d', Math.min(i, 12) * 45 + 'ms');
    el.innerHTML =
      '<div class="s-head"><div class="avatar" style="--h:' + hue(s.symbol) + '">' + esc(initials(s.symbol)) + '</div>' +
      '<div class="s-id"><b>' + esc(short(s.symbol)) + '</b><span>' + esc(s.name) + '</span></div><span class="rank" hidden></span>' +
      '<button class="x" aria-label="Remove ' + esc(short(s.symbol)) + '" title="Remove">' + ICON.x + '</button></div>' +
      '<div class="s-mid"><div class="price mono"></div><span class="pill"></span></div>' +
      '<div class="spark"></div>' +
      '<div class="sig"><span class="badge"></span><p></p></div>' +
      '<div class="meter"><div class="lab"><span>SINCE YOU LOOKED</span><span class="z"></span></div><div class="mtrack"><div class="mfill"></div><i class="mtick"></i></div></div>' +
      '<div class="r52"><span class="lo"></span><div class="rtrack"><i class="rdot"></i></div><span class="hi"></span></div>' +
      '<div class="tagline"></div>';
    el.addEventListener('click', (e) => {
      if (e.target.closest('.x')) { e.stopPropagation(); removeSymbol(s.symbol); return; }
      openDetail(s.symbol);
    });
    el.addEventListener('keydown', (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target === el) { e.preventDefault(); openDetail(s.symbol); } });
    return el;
  }

  function patchCard(el, s, topMovers, listSize) {
    const priceEl = $('.price', el), pill = $('.pill', el);
    const ccy = s.currency, prev = el._price;
    if (!s.available) {
      priceEl.textContent = '-'; pill.hidden = true;
      $('.badge', el).className = 'badge quiet'; $('.badge', el).textContent = 'NO DATA';
      $('p', el).textContent = s.reason;
      $('.spark', el).innerHTML = ''; $('.meter', el).hidden = true; $('.r52', el).hidden = true;
      el._s = s; return;
    }
    $('.meter', el).hidden = false; $('.r52', el).hidden = false; pill.hidden = false;

    // price (count-up + flash)
    if (prev != null && prev !== s.price) {
      priceEl.classList.remove('flash-up', 'flash-down'); void priceEl.offsetWidth;
      priceEl.classList.add(s.price > prev ? 'flash-up' : 'flash-down');
      setTimeout(() => priceEl.classList.remove('flash-up', 'flash-down'), 700);
      animateNumber(priceEl, prev, s.price, (v) => money(v, ccy, s.symbol), 500);
    } else priceEl.textContent = money(s.price, ccy, s.symbol);
    el._price = s.price;

    pill.className = 'pill ' + dir(s.dayChangePct);
    pill.textContent = arrow(s.dayChangePct) + ' ' + sgn(s.dayChangePct) + '%';

    const key = s.spark.join(',');
    if (el._spark !== key) {
      $('.spark', el).innerHTML = Charts.spark(s.spark, { up: s.spark[s.spark.length - 1] >= s.spark[0], draw: !el._spark });
      el._spark = key;
    }

    // signal block
    const badge = $('.badge', el);
    if (s.meaningful) { badge.className = 'badge hot'; badge.innerHTML = ICON.bolt + (s.crossedHigh ? '52W HIGH' : s.crossedLow ? '52W LOW' : 'SIGNAL'); }
    else if (s.unusualToday) { badge.className = 'badge big'; badge.textContent = 'BIG DAY'; }
    else if (s.changeSinceSeenPct == null) { badge.className = 'badge new'; badge.textContent = 'NEW'; }
    else { badge.className = 'badge quiet'; badge.textContent = 'QUIET'; }
    $('p', el).textContent = s.reason;
    el.classList.toggle('hot', s.meaningful);

    // meter: tick at 50% = alert threshold (z = 1.5 on a 0..3 scale)
    const fill = $('.mfill', el);
    fill.style.width = (s.changeSinceSeenPct == null ? 0 : clamp((s.signalZ / 3) * 100, 2, 100)) + '%';
    fill.classList.toggle('over', s.meaningful);
    $('.z', el).textContent = s.changeSinceSeenPct == null ? 'tracking starts now' : sgn(s.changeSinceSeenPct) + '% vs ±' + s.thresholdPct.toFixed(1) + '%';

    $('.lo', el).textContent = compact(s.low52); $('.hi', el).textContent = compact(s.high52);
    $('.rdot', el).style.left = clamp(s.rangePos52 * 100, 3, 97) + '%';

    const tags = [];
    if (s.source === 'demo') tags.push('<span class="tg warn">demo data</span>');
    else if (s.stale) tags.push('<span class="tg warn">delayed</span>');
    tags.push('<span class="tg">' + esc(s.trend) + '</span>');
    if (s.rsi >= 70) tags.push('<span class="tg">RSI ' + Math.round(s.rsi) + ' · overbought</span>');
    else if (s.rsi <= 30) tags.push('<span class="tg">RSI ' + Math.round(s.rsi) + ' · oversold</span>');
    else tags.push('<span class="tg">Vol ' + s.annVolPct.toFixed(0) + '%</span>');
    const th = tags.join('');
    if (el._tags !== th) { $('.tagline', el).innerHTML = th; el._tags = th; }

    const rank = $('.rank', el), r = topMovers.indexOf(s.symbol);
    if (r >= 0 && listSize >= 4) { rank.hidden = false; rank.textContent = '#' + (r + 1) + ' mover'; } else rank.hidden = true;
    el._s = s;
  }

  /* ───────────── sort / filter / FLIP reorder ───────────── */
  const SORTERS = {
    added: () => 0,
    signal: (a, b) => (b.meaningful - a.meaningful) || (b.signalZ - a.signalZ) || (Math.abs(b.dayChangePct) - Math.abs(a.dayChangePct)),
    change: (a, b) => b.dayChangePct - a.dayChangePct,
    alpha: (a, b) => a.symbol.localeCompare(b.symbol),
  };
  const FILTERS = {
    all: () => true,
    signal: (s) => s.meaningful || s.unusualToday,
    up: (s) => s.dayChangePct > 0,
    down: (s) => s.dayChangePct < 0,
  };
  const FILTER_LABEL = { all: 'All', signal: 'Signals', up: 'Gainers', down: 'Losers' };

  function renderChips() {
    const items = S.data ? S.data.items.filter((s) => s.available) : [];
    $('#chips').innerHTML = Object.keys(FILTERS).map((k) =>
      '<button class="chip' + (S.filter === k ? ' on' : '') + '" data-f="' + k + '">' + FILTER_LABEL[k] + '<small>' + items.filter(FILTERS[k]).length + '</small></button>').join('');
  }

  function applyOrder(animate) {
    if (!S.data) return;
    const grid = $('#grid');
    const list = S.data.items.filter((s) => s.available ? FILTERS[S.filter](s) : S.filter === 'all').sort(SORTERS[S.sort]);
    const order = list.map((s) => s.symbol);
    const before = animate && !REDUCED ? new Map([...S.cards].map(([k, el]) => [k, el.getBoundingClientRect()])) : null;
    S.cards.forEach((el, k) => { el.hidden = !order.includes(k); });
    order.forEach((k) => grid.appendChild(S.cards.get(k)));
    grid.querySelectorAll('.ghost').forEach((g) => grid.appendChild(g));
    if (before) {
      order.forEach((k) => {
        const el = S.cards.get(k), b = before.get(k), a = el.getBoundingClientRect();
        if (!b || !b.width) return;
        const dx = b.left - a.left, dy = b.top - a.top;
        if (dx || dy) el.animate([{ transform: 'translate(' + dx + 'px,' + dy + 'px)' }, { transform: 'none' }], { duration: 420, easing: 'cubic-bezier(.2,.8,.2,1)' });
      });
    }
    $('#grid').dataset.empty = order.length ? '' : '1';
  }

  /* ───────────── dashboard sections ───────────── */
  function renderMode(mode) {
    const m = $('#mode');
    m.className = 'mode ' + mode;
    $('span', m).textContent = mode === 'live' ? 'LIVE' : mode === 'demo' ? 'DEMO DATA' : 'OFFLINE';
    m.title = mode === 'live' ? 'Live market data' : mode === 'demo' ? 'Live feed unreachable: showing simulated demo prices' : 'Market data offline';
    if (mode === 'demo' && !S.demoDismissed) {
      banner('demo', '<span><b>Demo data.</b> The live market feed is unreachable from this server right now, so prices are simulated. Signals, charts and correlations work exactly the same on real data.</span>');
    } else if (mode !== 'demo') banner('demo', null);
  }

  function renderMarkets(indices) {
    const el = $('#markets');
    const key = JSON.stringify(indices.map((i) => [i.symbol, i.price, i.changePct]));
    if (key === S.marketsKey) return;
    const first = !S.marketsKey; S.marketsKey = key;
    el.hidden = !indices.length;
    el.innerHTML = indices.map((i) =>
      '<div class="mk card" style="cursor:pointer" data-sym="' + esc(i.symbol) + '" title="Open ' + esc(i.name) + '"><span class="n">' + esc(i.name) + '</span>' +
      '<span class="v">' + i.price.toLocaleString(i.symbol === '^NSEI' || i.symbol === '^BSESN' ? 'en-IN' : 'en-US', { maximumFractionDigits: 2 }) + '</span>' +
      '<span class="c ' + dir(i.changePct) + '">' + arrow(i.changePct) + ' ' + sgn(i.changePct) + '%</span>' +
      Charts.spark(i.spark, { up: i.spark[i.spark.length - 1] >= i.spark[0], draw: first }) + '</div>').join('');
  }

  function renderPulse(d, items) {
    const el = $('#pulse');
    const key = JSON.stringify(d);
    const stamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    if (key === S.pulseKey) { const u = $('.upd', el); if (u) u.textContent = 'Updated ' + stamp; return; }
    const prevAvg = S.pulseAvg; S.pulseKey = key; S.pulseAvg = d.avgDayChangePct;
    const tone = { alert: 'var(--amber)', positive: 'var(--up)', negative: 'var(--down)', neutral: 'var(--accent)' }[d.tone] || 'var(--accent)';
    el.style.setProperty('--tone', tone);
    const total = Math.max(1, d.advancers + d.decliners + d.unchanged);
    el.innerHTML =
      '<div><span class="eyebrow">' + ICON.bolt + ' Market pulse <span class="upd muted" style="text-transform:none;letter-spacing:0;font-weight:500">Updated ' + stamp + '</span></span>' +
      '<h1>' + esc(d.headline) + '</h1>' + (d.bullets.length ? '<ul>' + d.bullets.map((b) => '<li>' + esc(b) + '</li>').join('') + '</ul>' : '') + '</div>' +
      '<div class="stats"><div class="stat"><div class="k">AVG MOVE TODAY</div><div class="big ' + dir(d.avgDayChangePct) + '" id="avgNum">' + sgn(d.avgDayChangePct) + '%</div></div>' +
      '<div class="stat"><div class="k">SIGNALS</div><div class="big" style="color:' + (d.meaningfulCount ? 'var(--amber)' : 'var(--muted)') + '">' + d.meaningfulCount + '<span class="muted" style="font-size:15px;font-weight:500"> / ' + (d.advancers + d.decliners + d.unchanged) + '</span></div></div>' +
      '<div class="breadth"><div class="k muted" style="font-size:11.5px;font-weight:600;margin-bottom:7px">MARKET BREADTH</div><div class="bbar">' +
      '<i class="u" style="flex-grow:0"></i><i class="f" style="flex-grow:0"></i><i class="d" style="flex-grow:0"></i></div>' +
      '<div class="blegend"><span class="up">▲ ' + d.advancers + ' up</span><span>' + d.unchanged + ' flat</span><span class="down">▼ ' + d.decliners + ' down</span></div></div></div>';
    requestAnimationFrame(() => {
      const [u, f, dn] = el.querySelectorAll('.bbar i');
      if (u) { u.style.flexGrow = d.advancers / total; f.style.flexGrow = d.unchanged / total; dn.style.flexGrow = d.decliners / total; }
    });
    const n = $('#avgNum', el);
    if (n && prevAvg != null) animateNumber(n, prevAvg, d.avgDayChangePct, (v) => sgn(v) + '%', 700);
  }

  function renderRadar(corr, n) {
    const el = $('#radar');
    const key = JSON.stringify([corr.divergences, n]);
    if (key === S.radarKey) return; S.radarKey = key;
    let body;
    if (n < 2) body = '<div class="ok">' + ICON.info + '<span>Add at least two stocks to unlock the radar. It watches how your stocks move relative to each other.</span></div>';
    else if (!corr.divergences.length) body = '<div class="ok">' + ICON.check + '<span><b style="color:var(--text)">All clear.</b> Your stocks are behaving the way they normally do relative to each other.</span></div>';
    else body = corr.divergences.map((p, i) =>
      '<div class="pair" style="animation-delay:' + i * 60 + 'ms"><div class="t">' + esc(short(p.symbolA)) + ' <em>⇄</em> ' + esc(short(p.symbolB)) + '</div>' +
      '<div class="bars"><span>Normally</span><div class="bt"><i class="a" style="width:' + Math.abs(p.longCorrelation) * 100 + '%"></i></div><span class="mono">' + p.longCorrelation.toFixed(2) + '</span>' +
      '<span>Last 10d</span><div class="bt"><i class="b" style="width:' + Math.abs(p.recentCorrelation) * 100 + '%"></i></div><span class="mono">' + p.recentCorrelation.toFixed(2) + '</span></div>' +
      '<p>' + esc(p.explanation) + '</p></div>').join('');
    el.innerHTML = '<h3>Divergence radar' + (corr.divergences.length ? ' <span class="count">' + corr.divergences.length + '</span>' : '') + '</h3>' +
      '<p class="sub">Pairs that normally move together but have recently decoupled. Often the first sign that something stock-specific is going on.</p>' + body;
  }

  function renderHeat(corr) {
    const key = JSON.stringify(corr.matrix) + corr.symbols.join();
    if (key === S.heatKey) return; S.heatKey = key;
    $('#heatPanel').hidden = corr.symbols.length < 2;
    if (corr.symbols.length >= 2) Charts.heatmap($('#heat'), corr.symbols, corr.matrix);
  }

  function renderEmpty() {
    const el = $('#empty');
    el.innerHTML = '<div class="art">' + ICON.chart + '</div><h3>Build a watchlist that tells you what matters</h3>' +
      '<p>Add stocks and indices. Smart Watchlist scores every move against that stock\'s own volatility, so you only see what\'s actually unusual.</p>' +
      '<button class="btn primary" id="starter" style="height:40px;padding:0 20px;font-size:14px">Add a starter pack (6 stocks)</button>' +
      '<div class="quick">' + POPULAR.map((p) => '<button class="chip" data-add="' + p[0] + '">+ ' + esc(short(p[0])) + '</button>').join('') + '</div>';
  }

  function render(dash) {
    S.data = dash;
    const grid = $('#grid');
    grid.querySelectorAll('[data-skel]').forEach((g) => g.remove());
    const n = dash.items.length;
    renderMode(dash.dataMode);
    renderMarkets(dash.indices);
    renderPulse(dash.digest, dash.items);

    const seen = new Set();
    dash.items.forEach((s, i) => {
      seen.add(s.symbol);
      let el = S.cards.get(s.symbol);
      if (!el) { el = cardEl(s, i); S.cards.set(s.symbol, el); grid.appendChild(el); }
      patchCard(el, s, dash.topMovers, n);
    });
    S.cards.forEach((el, k) => { if (!seen.has(k)) { el.remove(); S.cards.delete(k); } });

    const adding = grid.querySelector('.ghost:not([data-skel])');
    $('#toolbar').hidden = !n;
    $('#count').textContent = n;
    grid.hidden = !n && !adding;
    $('#empty').hidden = n > 0 || !!adding;
    if (!n && !$('#empty').innerHTML) renderEmpty();
    $('#insights').hidden = !n;
    if (n) { renderRadar(dash.correlation, n); renderHeat(dash.correlation); }
    renderChips();
    applyOrder(true);

    const sigs = dash.items.filter((s) => s.meaningful).length;
    document.title = (sigs ? '(' + sigs + ') ' : '') + 'Smart Watchlist · adaptive stock signals';
    syncDrawer();
    syncSignupBanner();
  }

  /* ───────────── actions ───────────── */
  function addGhost(sym) {
    const g = document.createElement('article');
    g.className = 'stock card ghost';
    g.innerHTML = '<div class="s-head"><div class="skel" style="width:38px;height:38px;border-radius:12px"></div><div class="s-id"><b>' + esc(short(sym)) + '</b><span>Fetching market data…</span></div></div>' +
      '<div class="skel" style="height:30px;width:55%;margin:18px 0 12px"></div><div class="skel" style="height:44px"></div><div class="skel" style="height:34px;margin-top:14px"></div>';
    $('#grid').appendChild(g);
    $('#grid').hidden = false; $('#empty').hidden = true; $('#toolbar').hidden = false;
    return g;
  }

  async function addSymbol(raw) {
    const sym = raw.trim().toUpperCase();
    if (!sym) return;
    if (S.data && S.data.items.some((i) => i.symbol === sym)) { toast(short(sym) + ' is already on your list'); flashCard(sym); return; }
    const ghost = addGhost(sym);
    try {
      await api('/watchlist', { method: 'POST', body: { symbol: sym } });
      await refresh({ force: true });
      toast('Added ' + short(sym), 'ok');
      flashCard(sym, true);
    } catch (e) { toast(e.message, 'err'); }
    finally { ghost.remove(); if (S.data) render(S.data); }
  }

  async function addStarter() {
    const btn = $('#starter'); if (btn) { btn.disabled = true; btn.textContent = 'Adding…'; }
    const ghosts = STARTER.slice(0, 3).map(addGhost);
    try {
      await api('/watchlist/bulk', { method: 'POST', body: { symbols: STARTER } });
      await refresh({ force: true });
      toast('Starter pack added', 'ok');
    } catch (e) { toast(e.message, 'err'); }
    finally { ghosts.forEach((g) => g.remove()); if (S.data) render(S.data); }
  }

  async function removeSymbol(sym) {
    const el = S.cards.get(sym);
    if (el) { el.style.transition = 'opacity .25s, transform .25s'; el.style.opacity = 0; el.style.transform = 'scale(.94)'; }
    try {
      await api('/watchlist/' + encodeURIComponent(sym), { method: 'DELETE' });
      await refresh({ force: true });
      toast('Removed ' + short(sym), '', { label: 'Undo', fn: () => addSymbol(sym) });
    } catch (e) { toast(e.message, 'err'); if (el) { el.style.opacity = ''; el.style.transform = ''; } }
  }

  function flashCard(sym, scroll) {
    const el = S.cards.get(sym); if (!el) return;
    if (scroll) el.scrollIntoView({ behavior: REDUCED ? 'auto' : 'smooth', block: 'center' });
    el.animate([{ boxShadow: '0 0 0 0 var(--accent)' }, { boxShadow: '0 0 0 10px transparent' }], { duration: 900, easing: 'ease-out' });
  }

  async function markSeen() {
    try {
      await api('/watchlist/seen', { method: 'POST' });
      await refresh({ force: true });
      toast('Baseline reset. Changes are now measured from this moment.', 'ok');
    } catch (e) { toast(e.message, 'err'); }
  }

  /* ───────────── refresh loop ───────────── */
  function restartRing() {
    const ring = $('#ring');
    ring.classList.remove('run'); void ring.getBoundingClientRect(); ring.style.setProperty('--refresh', REFRESH_MS + 'ms'); ring.classList.add('run');
  }
  async function refresh(o) {
    if (S.busy && !(o && o.force)) return;
    S.busy = true; $('#refresh').classList.add('spin');
    try {
      const dash = await api('/dashboard');
      S.failures = 0; banner('err', null);
      render(dash);
      store.set(cacheKey(), dash);
    } catch (e) {
      S.failures++;
      if (S.failures >= 2) banner('err', '<span><b>Connection problem.</b> Showing the last data we have; retrying automatically.</span>', 'err');
      if (!S.data) renderMode('offline');
    } finally {
      S.busy = false; $('#refresh').classList.remove('spin'); restartRing();
    }
  }
  function schedule() {
    clearInterval(S.timer);
    S.timer = setInterval(() => { if (!document.hidden) refresh(); }, REFRESH_MS);
  }
  function beacon() { if (navigator.sendBeacon) navigator.sendBeacon('api/watchlist/seen?uid=' + encodeURIComponent(S.uid)); }

  /* ───────────── search ───────────── */
  let sgTimer = 0;
  function sgRender() {
    const box = $('#suggest'), q = $('#q').value.trim();
    const have = new Set(S.data ? S.data.items.map((i) => i.symbol) : []);
    let items = S.sg.items;
    let html = '';
    if (!q) html += '<div class="sg-empty" style="padding:8px 10px 4px;text-align:left;font-size:11px;font-weight:700;letter-spacing:.06em">POPULAR</div>';
    html += items.map((r, i) =>
      '<button class="sg' + (i === S.sg.idx ? ' active' : '') + '" role="option" data-i="' + i + '"><div class="nm"><b>' + esc(r.symbol) + '</b><span>' + esc(r.name) + '</span></div>' +
      (r.exchange ? '<span class="tag">' + esc(r.exchange) + '</span>' : '') + (have.has(r.symbol) ? '<span class="added">✓ Added</span>' : '<span class="add">+ Add</span>') + '</button>').join('');
    if (q && !items.length) html += /^[A-Za-z0-9.^=-]{1,20}$/.test(q)
      ? '<button class="sg active" data-raw="1"><div class="nm"><b>' + esc(q.toUpperCase()) + '</b><span>Try adding this ticker directly</span></div><span class="add">+ Add</span></button>'
      : '<div class="sg-empty">No matches. Try a company name or ticker.</div>';
    box.innerHTML = html; box.hidden = false;
    $('#q').setAttribute('aria-expanded', 'true');
  }
  function sgClose() { $('#suggest').hidden = true; $('#q').setAttribute('aria-expanded', 'false'); S.sg.idx = -1; }
  async function sgQuery() {
    const q = $('#q').value.trim(), seq = ++S.sg.seq;
    if (!q) { S.sg.items = POPULAR.map((p) => ({ symbol: p[0], name: p[1], exchange: p[2] })); S.sg.idx = -1; sgRender(); return; }
    try {
      const r = await api('/search?q=' + encodeURIComponent(q));
      if (seq !== S.sg.seq) return;
      S.sg.items = r; S.sg.idx = r.length ? 0 : -1; sgRender();
    } catch (e) { /* keep previous suggestions */ }
  }
  function sgPick(i) {
    const r = S.sg.items[i];
    const sym = r ? r.symbol : $('#q').value;
    $('#q').value = ''; sgClose(); $('#q').blur();
    addSymbol(sym);
  }

  /* ───────────── detail drawer ───────────── */
  const RANGES = [['1M', 22], ['3M', 64], ['6M', 127], ['1Y', 0]];

  function openDrawerShell() {
    S.lastFocus = document.activeElement;
    $('#scrim').classList.add('on'); $('#drawer').classList.add('on'); $('#drawer').setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }
  function closeDetail() {
    if (!S.detail) return;
    S.detail = null;
    if (S.chart) { S.chart.destroy(); S.chart = null; }
    $('#scrim').classList.remove('on'); $('#drawer').classList.remove('on'); $('#drawer').setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
    if (S.lastFocus && S.lastFocus.focus) S.lastFocus.focus();
  }

  async function openDetail(sym) {
    S.detail = sym;
    openDrawerShell();
    const body = $('#drawerBody');
    const known = S.cards.get(sym) && S.cards.get(sym)._s;
    body.innerHTML = '<div class="dr-top"><div class="avatar" style="--h:' + hue(sym) + '">' + esc(initials(sym)) + '</div><div class="s-id"><b>' + esc(short(sym)) + '</b><span>' + esc(known ? known.name : 'Loading…') + '</span></div>' +
      '<div class="dr-actions"><button class="icon-btn" id="drClose" aria-label="Close">' + ICON.x + '</button></div></div>' +
      '<div class="skel" style="height:44px;width:50%;margin:24px 0 14px"></div><div class="skel" style="height:270px"></div><div class="skel" style="height:90px;margin-top:20px"></div>';
    $('#drClose').onclick = closeDetail; $('#drClose').focus();
    try {
      const d = await api('/stocks/' + encodeURIComponent(sym));
      if (S.detail !== sym) return;
      renderDetail(d);
    } catch (e) {
      if (S.detail !== sym) return;
      toast(e.message, 'err'); closeDetail();
    }
  }

  function tile(k, v, s, extra) { return '<div class="tile"><div class="k">' + k + '</div><div class="v">' + v + '</div>' + (extra || '') + (s ? '<div class="s">' + s + '</div>' : '') + '</div>'; }

  function renderDetail(d) {
    const s = d.stock, b = d.backtest, ccy = s.currency, m = (v) => money(v, ccy, s.symbol);
    const rng = (v) => (v >= 100000 ? compact(v) : m(v).replace(/\.00$/, ''));
    const pos = (v, lo, hi) => clamp(hi > lo ? ((v - lo) / (hi - lo)) * 100 : 50, 3, 97);
    const retCls = (v) => dir(v);
    const dayRange = '<div class="rtrack"><i class="rdot" style="left:' + pos(s.price, s.dayLow, s.dayHigh) + '%"></i></div>';
    const yrRange = '<div class="rtrack"><i class="rdot" style="left:' + pos(s.price, s.low52, s.high52) + '%"></i></div>';
    const daysAway = Math.pow(s.thresholdPct / (1.5 * Math.max(s.sigmaPct, 0.01)), 2);
    const away = daysAway < 1.3 ? 'since your last visit' : 'over roughly ' + daysAway.toFixed(0) + ' trading days';
    const tags = (s.source === 'demo' ? '<span class="tg warn">demo data</span>' : s.stale ? '<span class="tg warn">delayed</span>' : '') +
      (s.exchange ? '<span class="tg">' + esc(s.exchange) + '</span>' : '') + (ccy ? '<span class="tg">' + esc(ccy) + '</span>' : '');

    $('#drawerBody').innerHTML =
      '<div class="dr-top"><div class="avatar" style="--h:' + hue(s.symbol) + '">' + esc(initials(s.symbol)) + '</div>' +
      '<div class="s-id"><b>' + esc(short(s.symbol)) + '</b><span>' + esc(s.name) + '</span></div>' +
      '<div class="dr-actions"><button class="btn' + (d.inWatchlist ? '' : ' primary') + '" id="drToggle">' + (d.inWatchlist ? 'Remove' : '+ Add to watchlist') + '</button>' +
      '<button class="icon-btn" id="drClose" aria-label="Close">' + ICON.x + '</button></div></div>' +
      '<div class="dr-price"><div class="price mono" id="drPrice">' + m(s.price) + '</div><span class="pill ' + dir(s.dayChangePct) + '">' + arrow(s.dayChangePct) + ' ' + sgn(s.dayChangePct) + '%</span>' +
      '<span class="muted mono">' + sgn(s.dayChange) + ' today</span></div>' +
      '<div class="dr-sub"><span>Prev close ' + m(s.previousClose) + '</span>' + tags + '</div>' +
      '<div class="toolrow"><div class="chips" id="ranges">' + RANGES.map((r) => '<button class="chip' + (r[0] === '6M' ? ' on' : '') + '" data-n="' + r[1] + '">' + r[0] + '</button>').join('') + '</div><div class="spacer"></div>' +
      '<button class="tgl s20 on" data-t="s20"><i></i>SMA 20</button><button class="tgl s50" data-t="s50"><i></i>SMA 50</button><button class="tgl fl on" data-t="flags"><i></i>Alert days</button></div>' +
      '<div class="chartbox" id="chart"></div>' +
      '<div class="tiles">' +
      tile('DAY RANGE', (s.price ? ((s.dayHigh - s.dayLow) / s.price * 100).toFixed(2) : '0.00') + '%', 'high-low spread', dayRange + '<div class="rl"><span>' + rng(s.dayLow) + '</span><span>' + rng(s.dayHigh) + '</span></div>') +
      tile('52-WEEK RANGE', Math.round(s.rangePos52 * 100) + '%', 'of the way up', yrRange + '<div class="rl"><span>' + rng(s.low52) + '</span><span>' + rng(s.high52) + '</span></div>') +
      tile('VOLATILITY (ANNUAL)', s.annVolPct.toFixed(1) + '%', 'σ ' + s.sigmaPct.toFixed(2) + '% per day') +
      tile('RSI (14)', s.rsi.toFixed(0), s.rsi >= 70 ? 'Overbought zone' : s.rsi <= 30 ? 'Oversold zone' : 'Neutral', '<div class="gauge"><i style="left:' + clamp(s.rsi, 3, 97) + '%"></i></div>') +
      tile('TREND', '<span class="' + (s.trend === 'Uptrend' ? 'up' : s.trend === 'Downtrend' ? 'down' : '') + '">' + s.trend + '</span>', 'Price vs SMA 20 / 50') +
      tile('1M RETURN', '<span class="' + retCls(s.ret1mPct) + '">' + sgn(s.ret1mPct, 1) + '%</span>', '') +
      tile('3M RETURN', '<span class="' + retCls(s.ret3mPct) + '">' + sgn(s.ret3mPct, 1) + '%</span>', '') +
      tile('VOLUME', s.volume ? compact(s.volume) : 'n/a', 'latest session') + '</div>' +

      '<div class="lab-h"><h3>Signal lab</h3><span class="verdict">' + esc(b.verdict) + '</span></div>' +
      '<div class="explain">This stock\'s normal daily move is <b>±' + s.sigmaPct.toFixed(2) + '%</b> (standard deviation of the previous 60 sessions). ' +
      'The alert fires at <b>±' + s.thresholdPct.toFixed(2) + '%</b> (1.5 × σ, scaled for the time away: ' + away + '). ' +
      (s.changeSinceSeenPct == null ? 'You just started tracking it, so the baseline is now.' : 'Since you last looked it has moved <b class="' + dir(s.changeSinceSeenPct) + '">' + sgn(s.changeSinceSeenPct) + '%</b>, which is ' + (s.meaningful ? '<b style="color:var(--amber)">a meaningful move</b>.' : 'within its normal range.')) + '</div>' +
      (b.daysTested ? '<div class="tiles">' +
        tile('ALERTS FIRED', b.timesFlagged + '<span class="muted" style="font-size:12px;font-weight:500"> / ' + b.daysTested + ' days</span>', b.flagRatePct.toFixed(0) + '% of days') +
        tile('NEXT-DAY FOLLOW-THROUGH', b.continuationRatePct == null ? 'n/a' : b.continuationRatePct.toFixed(0) + '%', 'vs ' + b.baselineContinuationPct.toFixed(0) + '% on any day') +
        tile('AVG ALERT MOVE', b.avgFlaggedMovePct.toFixed(2) + '%', 'max ' + b.maxFlaggedMovePct.toFixed(2) + '%') + '</div>' +
        '<div class="chartbox" id="bars" style="height:170px"></div>' : '') +
      '<p class="note">' + esc(b.note) + ' Each day is judged using only the 30 sessions before it, so there is no look-ahead. Bars outlined in full colour are days the rule would have fired; the dashed band is the threshold.</p>';

    $('#drClose').onclick = closeDetail;
    $('#drToggle').onclick = async () => {
      if (d.inWatchlist) { closeDetail(); removeSymbol(s.symbol); } else { closeDetail(); addSymbol(s.symbol); }
    };
    const box = $('#chart');
    S.chart = Charts.areaChart(box, (v, axis) => (axis && Math.abs(v) >= 1000 ? compact(v) : money(v, ccy, s.symbol).replace(/\.00$/, '')));
    const flags = new Set(b.series.filter((p) => p.flagged).map((p) => p.date));
    S.chart.set({ dates: d.dates, closes: d.closes, sma20: d.sma20, sma50: d.sma50, flags });
    S.chart.range(127);
    $('#ranges').onclick = (e) => {
      const c = e.target.closest('.chip'); if (!c) return;
      $('#ranges').querySelectorAll('.chip').forEach((x) => x.classList.toggle('on', x === c));
      S.chart.range(+c.dataset.n);
    };
    $('.toolrow').onclick = (e) => {
      const t = e.target.closest('.tgl'); if (!t) return;
      t.classList.toggle('on', S.chart.toggle(t.dataset.t));
    };
    if ($('#bars')) requestAnimationFrame(() => Charts.barsChart($('#bars'), b.series));
  }

  function syncDrawer() {
    if (!S.detail) return;
    const el = S.cards.get(S.detail), p = $('#drPrice');
    if (el && el._s && p) p.textContent = money(el._s.price, el._s.currency, el._s.symbol);
  }

  /* ───────────── accounts ───────────── */
  const initials2 = (u) => (u.name || u.email).replace(/[^A-Za-z0-9 ]/g, ' ').trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join('').toUpperCase() || '?';

  function renderAccount() {
    const box = $('#acct');
    if (!S.user) {
      box.innerHTML = '<button class="btn" id="siBtn">Sign in</button><button class="btn primary" id="suBtn">Sign up</button>';
      $('#siBtn').onclick = () => openAuth('login'); $('#suBtn').onclick = () => openAuth('signup');
    } else {
      box.innerHTML = '<button class="avbtn" id="avBtn" aria-haspopup="true" aria-label="Account menu" title="' + esc(S.user.email) + '">' + esc(initials2(S.user)) + '</button>';
      $('#avBtn').onclick = (e) => { e.stopPropagation(); toggleMenu(); };
    }
    syncSignupBanner();
  }
  function toggleMenu() {
    const old = $('#acctMenu');
    if (old) { old.remove(); return; }
    const m = document.createElement('div');
    m.className = 'menu'; m.id = 'acctMenu';
    m.innerHTML = '<div class="who"><b>' + esc(S.user.name || 'Your account') + '</b><span>' + esc(S.user.email) + '</span></div><button id="logoutBtn">Log out</button>';
    $('#acct').appendChild(m);
    $('#logoutBtn').onclick = logout;
  }
  function syncSignupBanner() {
    const show = !S.user && S.data && S.data.items.length > 0 && !store.get('signupDismissed', false);
    if (!show) { if (!S.user || !$('#bn-signup')) banner('signup', null); return; }
    if ($('#bn-signup')) return;
    banner('signup', '<span><b>Save your watchlist.</b> Create a free account to keep it across devices.</span><button class="btn primary" id="bnSignup">Sign up free</button>');
    $('#bnSignup').onclick = () => openAuth('signup');
  }

  function setAuthMode(mode) {
    S.authMode = mode;
    const signup = mode === 'signup';
    $('#authTitle').textContent = signup ? 'Create your account' : 'Welcome back';
    $('#authSub').textContent = signup ? 'Save your watchlist and open it on any device.' : 'Log in to see your saved watchlist on any device.';
    $('#nameRow').hidden = !signup;
    $('#authSubmit').textContent = signup ? 'Create account' : 'Log in';
    $('#aPass').autocomplete = signup ? 'new-password' : 'current-password';
    $('#authFine').textContent = signup && S.data && S.data.items.length ? 'Your current watchlist (' + S.data.items.length + ' stocks) will be saved to your new account.' : '';
    document.querySelectorAll('.tabs2 button').forEach((b) => { b.classList.toggle('on', b.dataset.m === mode); b.setAttribute('aria-selected', b.dataset.m === mode); });
    $('#authErr').hidden = true;
  }
  function openAuth(mode) {
    const m = $('#acctMenu'); if (m) m.remove();
    S.lastFocus = document.activeElement;
    setAuthMode(mode);
    $('#auth').hidden = false;
    setTimeout(() => $(mode === 'signup' ? '#aName' : '#aEmail').focus(), 30);
  }
  function closeAuth() {
    $('#auth').hidden = true;
    $('#authForm').reset(); $('#aPass').type = 'password'; $('#pwToggle').textContent = 'Show';
    if (S.lastFocus && S.lastFocus.focus) S.lastFocus.focus();
  }
  function resetData() {
    S.cards.forEach((el) => el.remove()); S.cards.clear();
    $('#grid').querySelectorAll('.ghost').forEach((g) => g.remove());
    S.data = null; S.pulseKey = ''; S.radarKey = ''; S.heatKey = ''; S.pulseAvg = null;
  }
  async function submitAuth(e) {
    e.preventDefault();
    const err = $('#authErr'), btn = $('#authSubmit'), signup = S.authMode === 'signup';
    const email = $('#aEmail').value.trim(), password = $('#aPass').value;
    const fail = (msg) => { err.textContent = msg; err.hidden = false; err.style.animation = 'none'; void err.offsetWidth; err.style.animation = ''; };
    if (!email || !password) return fail('Enter your email and password.');
    btn.disabled = true; btn.textContent = 'Please wait…'; err.hidden = true;
    try {
      const body = signup ? { email, password, displayName: $('#aName').value.trim(), guestId: S.uid } : { email, password };
      const r = await api('/auth/' + S.authMode, { method: 'POST', body });
      S.user = { email: r.email, name: r.displayName };
      store.set('user', S.user);
      closeAuth(); resetData(); renderAccount(); banner('signup', null);
      await refresh({ force: true });
      toast(signup ? 'Account created. Your watchlist is saved.' : 'Welcome back, ' + (r.displayName || r.email), 'ok');
    } catch (ex) { fail(ex.message); }
    finally { btn.disabled = false; btn.textContent = signup ? 'Create account' : 'Log in'; }
  }
  async function logout() {
    const m = $('#acctMenu'); if (m) m.remove();
    try { await api('/auth/logout', { method: 'POST' }); } catch (e) { /* cookie may already be gone */ }
    S.user = null; store.set('user', null); store.set('seeded', false);
    resetData(); renderAccount(); closeDetail();
    await refresh({ force: true });
    await maybeSeed();
    toast('Logged out. Log back in anytime to get your watchlist.', 'ok');
  }
  async function maybeSeed() {
    if (S.user || !S.data || S.data.items.length || store.get('seeded', false)) return;
    store.set('seeded', true);
    await addStarter();
  }
  async function checkSession() {
    try {
      const me = await api('/auth/me');
      if (me.authenticated) {
        const changed = !S.user || S.user.email !== me.email;
        S.user = { email: me.email, name: me.displayName }; store.set('user', S.user);
        if (changed) { resetData(); renderAccount(); refresh({ force: true }); } else renderAccount();
      } else if (S.user) {
        S.user = null; store.set('user', null); resetData(); renderAccount(); await refresh({ force: true }); await maybeSeed();
      } else renderAccount();
    } catch (e) { renderAccount(); }
  }

  /* ───────────── wiring ───────────── */
  function init() {
    // theme
    $('#theme').onclick = () => {
      const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = next;
      try { localStorage.setItem('sw.theme', next); } catch (e) { /* ignore */ }
      $('meta[name=theme-color]').content = next === 'dark' ? '#070a12' : '#f3f5fb';
    };
    $('#refresh').onclick = () => refresh({ force: true });
    $('#seen').onclick = markSeen;

    // sort / view / filter
    $('#sort').value = S.sort;
    $('#sort').onchange = (e) => { S.sort = e.target.value; store.set('sort', S.sort); applyOrder(true); };
    const setView = (v) => { S.view = v; store.set('view', v); $('#grid').classList.toggle('list', v === 'list'); $('#vGrid').classList.toggle('on', v === 'grid'); $('#vList').classList.toggle('on', v === 'list'); };
    $('#vGrid').onclick = () => setView('grid'); $('#vList').onclick = () => setView('list'); setView(S.view);
    $('#chips').onclick = (e) => { const c = e.target.closest('.chip'); if (!c) return; S.filter = c.dataset.f; renderChips(); applyOrder(true); };
    $('#empty').onclick = (e) => {
      if (e.target.closest('#starter')) addStarter();
      const a = e.target.closest('[data-add]'); if (a) addSymbol(a.dataset.add);
    };
    $('#markets').onclick = (e) => { const c = e.target.closest('.mk'); if (c) openDetail(c.dataset.sym); };

    // card spotlight (event delegation)
    $('#grid').addEventListener('pointermove', (e) => {
      const c = e.target.closest('.stock'); if (!c) return;
      const r = c.getBoundingClientRect(); c.style.setProperty('--mx', e.clientX - r.left + 'px'); c.style.setProperty('--my', e.clientY - r.top + 'px');
    });

    // search
    const q = $('#q');
    q.addEventListener('focus', sgQuery);
    q.addEventListener('input', () => { clearTimeout(sgTimer); sgTimer = setTimeout(sgQuery, 120); });
    q.addEventListener('keydown', (e) => {
      const n = S.sg.items.length;
      if (e.key === 'ArrowDown' && n) { e.preventDefault(); S.sg.idx = (S.sg.idx + 1) % n; sgRender(); }
      else if (e.key === 'ArrowUp' && n) { e.preventDefault(); S.sg.idx = (S.sg.idx - 1 + n) % n; sgRender(); }
      else if (e.key === 'Enter') { e.preventDefault(); if (q.value.trim() || S.sg.idx >= 0) sgPick(S.sg.idx >= 0 ? S.sg.idx : -1); }
      else if (e.key === 'Escape') { sgClose(); q.blur(); }
    });
    $('#suggest').addEventListener('mousedown', (e) => {
      e.preventDefault();
      const b = e.target.closest('.sg'); if (!b) return;
      sgPick(b.dataset.raw ? -1 : +b.dataset.i);
    });
    document.addEventListener('click', (e) => { if (!e.target.closest('.search')) sgClose(); });

    // auth modal
    $('#authForm').addEventListener('submit', submitAuth);
    $('#authX').onclick = closeAuth;
    $('#auth').addEventListener('mousedown', (e) => { if (e.target === $('#auth')) closeAuth(); });
    document.querySelectorAll('.tabs2 button').forEach((b) => { b.onclick = () => setAuthMode(b.dataset.m); });
    $('#pwToggle').onclick = () => { const p = $('#aPass'), show = p.type === 'password'; p.type = show ? 'text' : 'password'; $('#pwToggle').textContent = show ? 'Hide' : 'Show'; };
    document.addEventListener('click', (e) => { const m = $('#acctMenu'); if (m && !e.target.closest('#acctMenu')) m.remove(); });

    // drawer + keyboard
    $('#scrim').onclick = closeDetail;
    document.addEventListener('keydown', (e) => {
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName);
      if (e.key === '/' && !typing) { e.preventDefault(); q.focus(); }
      else if (e.key === 'Escape') { if (!$('#auth').hidden) closeAuth(); else if ($('#acctMenu')) $('#acctMenu').remove(); else if (S.detail) closeDetail(); }
      else if ((e.key === 'r' || e.key === 'R') && !typing && !e.metaKey && !e.ctrlKey && !e.altKey) refresh({ force: true });
    });

    // "since you last looked" baseline moves when you leave, not on background refreshes
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') { S.hiddenSince = Date.now(); beacon(); }
      else if (Date.now() - S.hiddenSince > REFRESH_MS) refresh({ force: true });
    });
    addEventListener('pagehide', beacon);

    // first paint: cached dashboard instantly (stale-while-revalidate), else skeletons
    const cached = store.get(cacheKey(), null);
    if (cached && cached.items) render(cached);
    else {
      $('#pulse').innerHTML = '<div><div class="skel" style="height:14px;width:120px"></div><div class="skel" style="height:26px;width:80%;margin:14px 0 10px"></div><div class="skel" style="height:14px;width:55%"></div></div><div class="stats"><div class="skel" style="height:50px"></div><div class="skel" style="height:50px"></div></div>';
      for (let i = 0; i < 3; i++) {
        const g = document.createElement('article'); g.className = 'stock card ghost'; g.dataset.skel = '1';
        g.innerHTML = '<div class="s-head"><div class="skel" style="width:38px;height:38px;border-radius:12px"></div><div class="skel" style="height:30px;flex:1"></div></div><div class="skel" style="height:30px;width:55%;margin:18px 0 12px"></div><div class="skel" style="height:44px"></div><div class="skel" style="height:34px;margin-top:14px"></div>';
        $('#grid').appendChild(g);
      }
    }
    renderAccount();
    checkSession();
    refresh({ force: true }).then(async () => { restartRing(); await maybeSeed(); });
    schedule();
  }

  init();
})();
