/* Tiny dependency-free SVG charting: sparkline, interactive price chart, returns bars, correlation heatmap. */
(function (global) {
  'use strict';

  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const f1 = (n) => n.toFixed(1);

  /** Catmull-Rom -> cubic Bezier, with y control points clamped so the curve never overshoots the data. */
  function smooth(p) {
    if (p.length < 3) return 'M' + p.map((a) => f1(a[0]) + ',' + f1(a[1])).join(' L');
    let d = 'M' + f1(p[0][0]) + ',' + f1(p[0][1]);
    for (let i = 0; i < p.length - 1; i++) {
      const p0 = p[i - 1] || p[i], p1 = p[i], p2 = p[i + 1], p3 = p[i + 2] || p2;
      const lo = Math.min(p1[1], p2[1]), hi = Math.max(p1[1], p2[1]);
      const c1x = p1[0] + (p2[0] - p0[0]) / 6, c1y = clamp(p1[1] + (p2[1] - p0[1]) / 6, lo, hi);
      const c2x = p2[0] - (p3[0] - p1[0]) / 6, c2y = clamp(p2[1] - (p3[1] - p1[1]) / 6, lo, hi);
      d += ' C' + f1(c1x) + ',' + f1(c1y) + ' ' + f1(c2x) + ',' + f1(c2y) + ' ' + f1(p2[0]) + ',' + f1(p2[1]);
    }
    return d;
  }

  let uid = 0;

  /** Small inline sparkline; returns an SVG string. */
  function spark(values, o) {
    o = o || {};
    if (!values || values.length < 2) return '';
    const w = 120, h = 40, pad = 3;
    const min = Math.min.apply(null, values), max = Math.max.apply(null, values), span = max - min || 1;
    const pts = values.map((v, i) => [pad + (i * (w - 2 * pad)) / (values.length - 1), h - pad - ((v - min) / span) * (h - 2 * pad)]);
    const d = smooth(pts);
    const col = o.up === false ? 'var(--down)' : 'var(--up)';
    const id = 'sp' + ++uid;
    return (
      '<svg viewBox="0 0 ' + w + ' ' + h + '" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="' + id + '" x1="0" y1="0" x2="0" y2="1">' +
      '<stop offset="0" style="stop-color:' + col + ';stop-opacity:.32"/><stop offset="1" style="stop-color:' + col + ';stop-opacity:0"/></linearGradient></defs>' +
      '<path d="' + d + ' L' + (w - pad) + ',' + h + ' L' + pad + ',' + h + ' Z" fill="url(#' + id + ')"/>' +
      '<path class="' + (o.draw ? 'draw' : '') + '" d="' + d + '" fill="none" style="stroke:' + col + '" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke"/></svg>'
    );
  }

  const fmtDay = (iso, long) => {
    const d = new Date(iso + 'T00:00:00');
    return long
      ? d.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
      : d.toLocaleDateString('en-US', { day: 'numeric', month: 'short' });
  };
  const fmtMonth = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-US', { month: 'short', year: '2-digit' });

  /** Interactive price chart with SMA overlays, flagged-day markers, and a crosshair tooltip. */
  function areaChart(box, fmt) {
    const wrap = document.createElement('div');
    wrap.style.cssText = 'position:absolute;inset:0';
    const tip = document.createElement('div');
    tip.className = 'tip';
    box.append(wrap, tip);

    const S = { d: null, range: 126, s20: true, s50: false, flags: true, view: null };
    let ro = null;

    function render(animate) {
      if (!S.d) return;
      const W = box.clientWidth, H = box.clientHeight;
      if (!W || !H) return;
      const d = S.d, total = d.closes.length;
      const n = Math.min(S.range || total, total), start = total - n;
      const padL = 6, padR = 60, padT = 14, padB = 24, pw = W - padL - padR, ph = H - padT - padB;
      const closes = d.closes.slice(start), dates = d.dates.slice(start);
      const s20 = d.sma20.slice(start), s50 = d.sma50.slice(start);

      let lo = Math.min.apply(null, closes), hi = Math.max.apply(null, closes);
      [[S.s20, s20], [S.s50, s50]].forEach(([on, arr]) => on && arr.forEach((v) => { if (v != null) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }));
      const pad = (hi - lo || 1) * 0.08;
      lo -= pad; hi += pad;
      const X = (i) => padL + (n === 1 ? 0 : (i / (n - 1)) * pw);
      const Y = (v) => padT + (1 - (v - lo) / (hi - lo)) * ph;
      const up = closes[n - 1] >= closes[0];
      const col = up ? 'var(--up)' : 'var(--down)';
      const pts = closes.map((v, i) => [X(i), Y(v)]);
      const line = smooth(pts);
      const gid = 'ar' + ++uid;

      let g = '';
      for (let k = 0; k <= 4; k++) {
        const v = lo + ((hi - lo) * k) / 4, y = Y(v);
        g += '<line x1="' + padL + '" x2="' + (W - padR) + '" y1="' + y + '" y2="' + y + '" style="stroke:var(--border)" stroke-dasharray="2 5"/>' +
          '<text x="' + (W - padR + 8) + '" y="' + (y + 4) + '" style="fill:var(--faint)" font-size="10.5" font-family="var(--mono)">' + fmt(v, true) + '</text>';
      }
      const ticks = Math.min(5, n);
      for (let k = 0; k < ticks; k++) {
        const i = Math.round((k * (n - 1)) / Math.max(1, ticks - 1));
        g += '<text x="' + clamp(X(i), 16, W - padR - 16) + '" y="' + (H - 6) + '" text-anchor="middle" style="fill:var(--faint)" font-size="10.5">' + (n > 140 ? fmtMonth(dates[i]) : fmtDay(dates[i])) + '</text>';
      }
      const smaPath = (arr, color) => {
        const p = [];
        arr.forEach((v, i) => { if (v != null) p.push([X(i), Y(v)]); });
        return p.length > 1 ? '<path d="' + smooth(p) + '" fill="none" style="stroke:' + color + '" stroke-width="1.3" stroke-dasharray="4 4" opacity=".9"/>' : '';
      };
      let marks = '';
      if (S.flags && d.flags) {
        dates.forEach((dt, i) => { if (d.flags.has(dt)) marks += '<circle cx="' + X(i) + '" cy="' + Y(closes[i]) + '" r="3.4" style="fill:var(--amber);stroke:var(--bg-2)" stroke-width="1.5"/>'; });
      }
      wrap.innerHTML =
        '<svg width="' + W + '" height="' + H + '" viewBox="0 0 ' + W + ' ' + H + '">' +
        '<defs><linearGradient id="' + gid + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" style="stop-color:' + col + ';stop-opacity:.28"/><stop offset="1" style="stop-color:' + col + ';stop-opacity:0"/></linearGradient></defs>' +
        g +
        '<path d="' + line + ' L' + X(n - 1) + ',' + (padT + ph) + ' L' + X(0) + ',' + (padT + ph) + ' Z" fill="url(#' + gid + ')"/>' +
        (S.s50 ? smaPath(s50, 'var(--accent-2)') : '') + (S.s20 ? smaPath(s20, 'var(--amber)') : '') +
        '<path class="' + (animate ? 'draw' : '') + '" d="' + line + '" fill="none" style="stroke:' + col + '" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>' +
        marks +
        '<g class="xh" style="opacity:0"><line y1="' + padT + '" y2="' + (padT + ph) + '" style="stroke:var(--muted)" stroke-dasharray="3 3"/><circle r="5" style="fill:' + col + ';stroke:var(--bg-2)" stroke-width="2.5"/></g></svg>';

      S.view = { W, H, n, X, Y, closes, dates, s20, s50, padL, padR, padT, ph };
    }

    function point(clientX) {
      const v = S.view;
      if (!v) return;
      const r = box.getBoundingClientRect();
      const x = clientX - r.left;
      const i = clamp(Math.round(((x - v.padL) / (v.W - v.padL - v.padR)) * (v.n - 1)), 0, v.n - 1);
      const px = v.X(i), py = v.Y(v.closes[i]);
      const xh = wrap.querySelector('.xh');
      if (!xh) return;
      xh.style.opacity = 1;
      xh.querySelector('line').setAttribute('x1', px); xh.querySelector('line').setAttribute('x2', px);
      xh.querySelector('circle').setAttribute('cx', px); xh.querySelector('circle').setAttribute('cy', py);
      const chg = ((v.closes[i] / v.closes[0] - 1) * 100);
      const dt = v.dates[i];
      const flagged = S.d.flags && S.d.flags.has(dt);
      tip.innerHTML = '<small>' + fmtDay(dt, true) + '</small><b>' + fmt(v.closes[i]) + '</b>' +
        '<div class="' + (chg >= 0 ? 'up' : 'down') + '">' + (chg >= 0 ? '+' : '') + chg.toFixed(2) + '% in range</div>' +
        (S.s20 && v.s20[i] != null ? '<div>SMA20 ' + fmt(v.s20[i]) + '</div>' : '') +
        (S.s50 && v.s50[i] != null ? '<div>SMA50 ' + fmt(v.s50[i]) + '</div>' : '') +
        (flagged ? '<div style="color:var(--amber)">Alert rule fired</div>' : '');
      tip.style.opacity = 1;
      const tw = tip.offsetWidth;
      tip.style.transform = 'translate(' + clamp(px + 14, 4, v.W - tw - 4) + 'px,' + clamp(py - 40, 0, v.H - tip.offsetHeight - 24) + 'px)';
    }
    function leave() {
      const xh = wrap.querySelector('.xh');
      if (xh) xh.style.opacity = 0;
      tip.style.opacity = 0;
    }
    box.addEventListener('mousemove', (e) => point(e.clientX));
    box.addEventListener('mouseleave', leave);
    box.addEventListener('touchstart', (e) => point(e.touches[0].clientX), { passive: true });
    box.addEventListener('touchmove', (e) => point(e.touches[0].clientX), { passive: true });
    box.addEventListener('touchend', leave);
    let t = 0;
    ro = new ResizeObserver(() => { cancelAnimationFrame(t); t = requestAnimationFrame(() => render(false)); });
    ro.observe(box);

    return {
      set(data) { S.d = data; render(true); },
      range(n) { S.range = n; render(true); },
      toggle(key) { S[key] = !S[key]; render(false); return S[key]; },
      destroy() { if (ro) ro.disconnect(); },
    };
  }

  /** Daily returns as bars, with the (moving) alert threshold band and flagged days highlighted. */
  function barsChart(box, series) {
    const W = box.clientWidth, H = box.clientHeight;
    if (!W || !series.length) { box.innerHTML = ''; return; }
    const padL = 6, padR = 44, padT = 10, padB = 20, pw = W - padL - padR, ph = H - padT - padB;
    const n = series.length;
    const m = Math.max.apply(null, series.map((s) => Math.max(Math.abs(s.retPct), s.thresholdPct))) * 1.12 || 1;
    const Y = (v) => padT + (1 - (v + m) / (2 * m)) * ph;
    const slot = pw / n, bw = Math.max(1.4, slot - 1.6);
    let bars = '';
    series.forEach((s, i) => {
      const x = padL + i * slot + (slot - bw) / 2, y0 = Y(0), y1 = Y(s.retPct);
      const c = s.retPct >= 0 ? 'var(--up)' : 'var(--down)';
      bars += '<rect data-i="' + i + '" x="' + x.toFixed(1) + '" y="' + Math.min(y0, y1).toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + Math.max(1, Math.abs(y1 - y0)).toFixed(1) +
        '" rx="1.2" style="fill:' + c + '" opacity="' + (s.flagged ? 1 : 0.3) + '"/>';
    });
    const step = (sign) => series.map((s, i) => (i ? 'L' : 'M') + (padL + i * slot + slot / 2).toFixed(1) + ',' + Y(sign * s.thresholdPct).toFixed(1)).join(' ');
    const lab = (v) => '<text x="' + (W - padR + 6) + '" y="' + (Y(v) + 4) + '" style="fill:var(--faint)" font-size="10" font-family="var(--mono)">' + (v > 0 ? '+' : '') + v.toFixed(1) + '%</text>';
    const first = series[0].date, last = series[n - 1].date;
    box.innerHTML = '<svg width="' + W + '" height="' + H + '">' +
      '<line x1="' + padL + '" x2="' + (W - padR) + '" y1="' + Y(0) + '" y2="' + Y(0) + '" style="stroke:var(--border)"/>' + bars +
      '<path d="' + step(1) + '" fill="none" style="stroke:var(--amber)" stroke-width="1.2" stroke-dasharray="4 3" opacity=".9"/>' +
      '<path d="' + step(-1) + '" fill="none" style="stroke:var(--amber)" stroke-width="1.2" stroke-dasharray="4 3" opacity=".9"/>' +
      lab(m * 0.85) + lab(-m * 0.85) +
      '<text x="' + padL + '" y="' + (H - 5) + '" style="fill:var(--faint)" font-size="10.5">' + fmtDay(first) + '</text>' +
      '<text x="' + (W - padR) + '" y="' + (H - 5) + '" text-anchor="end" style="fill:var(--faint)" font-size="10.5">' + fmtDay(last) + '</text></svg>';
    let tip = box.querySelector('.tip');
    if (!tip) { tip = document.createElement('div'); tip.className = 'tip'; box.appendChild(tip); }
    box.onmousemove = (e) => {
      const r = box.getBoundingClientRect();
      const i = clamp(Math.floor((e.clientX - r.left - padL) / slot), 0, n - 1), s = series[i];
      tip.innerHTML = '<small>' + fmtDay(s.date, true) + '</small><b class="' + (s.retPct >= 0 ? 'up' : 'down') + '">' + (s.retPct >= 0 ? '+' : '') + s.retPct.toFixed(2) + '%</b>' +
        '<div>Threshold &plusmn;' + s.thresholdPct.toFixed(2) + '%</div>' + (s.flagged ? '<div style="color:var(--amber)">Alert would have fired</div>' : '<div>Within normal range</div>');
      tip.style.opacity = 1;
      tip.style.transform = 'translate(' + clamp(e.clientX - r.left + 14, 4, W - tip.offsetWidth - 4) + 'px,4px)';
    };
    box.onmouseleave = () => { tip.style.opacity = 0; };
  }

  /** Correlation matrix as a colour-coded grid. */
  function heatmap(box, symbols, matrix) {
    const n = symbols.length;
    const short = (s) => s.replace(/\.(NS|BO)$/, '').slice(0, 7);
    const showNums = n <= 8;
    box.style.gridTemplateColumns = 'auto repeat(' + n + ', minmax(30px, 56px))';
    let h = '<div></div>' + symbols.map((s) => '<div class="hh" title="' + s + '">' + short(s) + '</div>').join('');
    for (let i = 0; i < n; i++) {
      h += '<div class="hrow" title="' + symbols[i] + '">' + short(symbols[i]) + '</div>';
      for (let j = 0; j < n; j++) {
        const v = matrix[i][j];
        if (v == null) { h += '<div class="hc" style="background:var(--panel-hi);color:var(--faint)" title="Not enough overlapping history">&middot;</div>'; continue; }
        const a = Math.round(Math.min(1, Math.abs(v)) * 88);
        const bg = i === j ? 'var(--panel-hi)' : 'color-mix(in srgb, ' + (v >= 0 ? 'var(--accent)' : 'var(--down)') + ' ' + a + '%, transparent)';
        const fg = Math.abs(v) > 0.6 && i !== j ? '#fff' : 'var(--muted)';
        h += '<div class="hc" style="background:' + bg + ';color:' + fg + '" title="' + symbols[i] + ' / ' + symbols[j] + ': ' + (i === j ? '1.00' : v.toFixed(2)) + '">' + (showNums && i !== j ? v.toFixed(2).replace(/^(-?)0\./, '$1.') : '') + '</div>';
      }
    }
    box.innerHTML = h;
  }

  global.Charts = { spark, areaChart, barsChart, heatmap };
})(window);
