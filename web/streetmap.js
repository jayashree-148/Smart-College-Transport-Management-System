/* MapKit - one drawing API, two renderers.
 *   street map  : real OpenStreetMap tiles through Leaflet   (DEFAULT, needs internet)
 *   schematic   : plain SVG drawn from the stop coordinates  (works offline, used automatically if tiles/Leaflet cannot load)
 * Pages describe WHAT to draw ("a scene"), MapKit decides HOW:
 *   scene = { fit: [{lat,lng}...],                                   points the view should include
 *             lines: [{pts, color, width, opacity}],
 *             dots:  [{lat, lng, r, fill, stroke, sw, num, label, big, tip, onClick}] }
 */
'use strict';
const MapKit = (() => {
  const TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
  const SOURCES = [   // first one that works wins; vendor/leaflet is where you can drop a local copy for fully offline use
    {js: 'vendor/leaflet/leaflet.js', css: 'vendor/leaflet/leaflet.css'},
    {js: 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js', css: 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'},
    {js: 'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.js', css: 'https://cdn.jsdelivr.net/npm/leaflet@1.9.4/dist/leaflet.css'},
  ];
  let loading = null;

  const store = {
    get: () => { try { return localStorage.getItem('ct_map') || 'street'; } catch (e) { return 'street'; } },
    set: v => { try { localStorage.setItem('ct_map', v); } catch (e) { /* private mode */ } },
  };

  function addTag(tag, attrs) {
    return new Promise((ok, no) => {
      const el = document.createElement(tag);
      Object.assign(el, attrs);
      const t = setTimeout(() => { el.remove(); no(new Error('timeout')); }, 7000);
      el.onload = () => { clearTimeout(t); ok(); };
      el.onerror = () => { clearTimeout(t); el.remove(); no(new Error('failed ' + (attrs.src || attrs.href))); };
      document.head.appendChild(el);
    });
  }
  /** Loads Leaflet once. Resolves true/false (never throws). */
  function load() {
    if (window.L && window.L.map) return Promise.resolve(true);
    if (loading) return loading;
    loading = (async () => {
      for (const s of SOURCES) {
        try {
          await addTag('link', {rel: 'stylesheet', href: s.css});
          await addTag('script', {src: s.js});
          if (window.L && window.L.map) return true;
        } catch (e) { /* try the next source */ }
      }
      return false;
    })();
    loading.then(ok => { if (!ok) loading = null; });      // allow a retry later if everything failed
    return loading;
  }

  // ------------------------------------------------------------ schematic (SVG) renderer
  function projector(points, W, H, pad) {
    const lats = points.map(p => p.lat), lngs = points.map(p => p.lng);
    const minLat = Math.min(...lats), maxLat = Math.max(...lats), minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
    const k = Math.cos(((minLat + maxLat) / 2) * Math.PI / 180);
    const w = Math.max((maxLng - minLng) * k, 1e-6), h = Math.max(maxLat - minLat, 1e-6);
    const s = Math.min((W - 2 * pad) / w, (H - 2 * pad) / h);
    const ox = (W - w * s) / 2, oy = (H - h * s) / 2;
    return p => [ox + (p.lng - minLng) * k * s, oy + (maxLat - p.lat) * s];
  }
  const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));

  function svgRenderer(host, o) {
    host.classList.remove('street'); host.style.aspectRatio = '';
    const gid = 'g' + Math.random().toString(36).slice(2, 7);
    return {
      kind: 'svg',
      draw(scene) {
        const P = projector(scene.fit, o.W, o.H, o.pad);
        let s = `<svg viewBox="0 0 ${o.W} ${o.H}" role="img"><defs><pattern id="${gid}" width="30" height="30" patternUnits="userSpaceOnUse"><path d="M30 0H0V30" fill="none" stroke="#e1e4f6" stroke-width="1"/></pattern></defs><rect width="100%" height="100%" fill="url(#${gid})"/>`;
        for (const l of scene.lines || [])
          s += `<polyline points="${l.pts.map(p => P(p).map(n => n.toFixed(1)).join(',')).join(' ')}" fill="none" stroke="${l.color}" stroke-width="${l.width || 3}" stroke-linecap="round" stroke-linejoin="round" opacity="${l.opacity ?? 1}"/>`;
        (scene.dots || []).forEach((d, i) => {
          const [x, y] = P(d), r = d.r || 6;
          s += `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${r}" fill="${d.fill}" stroke="${d.stroke || '#fff'}" stroke-width="${d.sw || 2}"${d.onClick ? ` data-i="${i}" style="cursor:pointer"` : ''}>${d.tip ? `<title>${esc(d.tip)}</title>` : ''}</circle>`;
          if (d.num != null) s += `<text x="${x.toFixed(1)}" y="${(y + 4).toFixed(1)}" font-size="11" fill="#fff" text-anchor="middle" font-weight="700" pointer-events="none">${esc(d.num)}</text>`;
          if (d.label) s += `<text x="${(x + r + 5).toFixed(1)}" y="${(y + (d.big ? 5 : -r - 1)).toFixed(1)}" font-size="${d.big ? 15 : 13}" font-weight="${d.big ? 700 : 600}" fill="#14142b" pointer-events="none">${esc(d.label)}</text>`;
        });
        host.innerHTML = s + '</svg>';
        host.querySelectorAll('[data-i]').forEach(c => c.onclick = () => scene.dots[+c.dataset.i].onClick());
      },
      destroy() { host.innerHTML = ''; },
    };
  }

  // ------------------------------------------------------------ street (Leaflet) renderer
  function streetRenderer(host, o, onTilesFailed) {
    host.innerHTML = ''; host.classList.add('street'); host.style.aspectRatio = `${o.W} / ${o.H}`;
    const map = L.map(host, {zoomControl: true, attributionControl: true});
    let ok = 0, bad = 0, fitted = false, failed = false;
    const tiles = L.tileLayer(TILES, {maxZoom: 18, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'}).addTo(map);
    tiles.on('tileload', () => { ok++; });
    tiles.on('tileerror', () => { bad++; if (!ok && bad >= 4 && !failed) { failed = true; onTilesFailed(); } });   // no tile ever loaded = offline
    const layer = L.layerGroup().addTo(map);
    map.setView([0, 0], 2);
    setTimeout(() => map.invalidateSize(), 60);
    return {
      kind: 'street',
      draw(scene, refit) {
        layer.clearLayers();
        for (const l of scene.lines || [])
          L.polyline(l.pts.map(p => [p.lat, p.lng]), {color: l.color, weight: l.width || 3, opacity: l.opacity ?? .9, lineJoin: 'round'}).addTo(layer);
        for (const d of scene.dots || []) {
          let m;
          if (d.num != null) {
            m = L.marker([d.lat, d.lng], {icon: L.divIcon({className: 'ct-pin', html: `<span style="background:${d.fill}">${esc(d.num)}</span>`, iconSize: [26, 26], iconAnchor: [13, 13]})});
          } else {
            m = L.circleMarker([d.lat, d.lng], {radius: Math.max(4, (d.r || 6) * .85), color: d.stroke || '#fff', weight: d.sw || 2, fillColor: d.fill, fillOpacity: 1});
          }
          if (d.label) m.bindTooltip(esc(d.label), {permanent: true, direction: 'right', offset: [d.num != null ? 12 : 8, 0], className: d.big ? 'ct-tip big' : 'ct-tip'});
          else if (d.tip) m.bindTooltip(esc(d.tip));
          if (d.onClick) m.on('click', d.onClick);
          m.addTo(layer);
        }
        if (!fitted || refit) {
          fitted = true;
          map.invalidateSize();
          map.fitBounds(L.latLngBounds(scene.fit.map(p => [p.lat, p.lng])), {padding: [34, 34], maxZoom: 15, animate: false});
        }
      },
      destroy() { try { map.stop(); map.off(); map.remove(); } catch (e) { /* map already torn down */ } host.innerHTML = ''; },
    };
  }

  /**
   * view(host, {W,H,pad,btn,toast}) -> {draw(scene, refit), destroy()}
   * Starts as a street map (unless the user last chose schematic), falls back to the schematic when Leaflet or the tiles
   * cannot load, and wires btn as the "Street map / Schematic map" switch.
   */
  function view(host, o) {
    o = Object.assign({W: 1000, H: 460, pad: 40, btn: null, toast: () => { }}, o);
    let impl = null, scene = null, want = store.get(), token = 0;

    function label() {
      if (!o.btn) return;
      const street = impl && impl.kind === 'street';
      o.btn.textContent = street ? 'Schematic map' : 'Street map';
      o.btn.title = street ? 'Switch to the offline schematic map' : 'Switch to the OpenStreetMap street map';
    }
    async function build(kind, quiet) {
      const my = ++token;
      if (impl) { impl.destroy(); impl = null; }
      if (kind === 'street') {
        host.classList.add('loading-map');
        const ok = await load();
        host.classList.remove('loading-map');
        if (my !== token) return;
        if (ok) impl = streetRenderer(host, o, () => { if (my === token) setTimeout(() => { if (my !== token) return; if (!quiet) o.toast('No internet for map tiles - showing the offline schematic map'); build('svg', true); }, 80); });
        else if (!quiet) o.toast('Street map needs internet access - showing the offline schematic map');
      }
      if (!impl) impl = svgRenderer(host, o);
      label();
      if (scene) impl.draw(scene, true);
    }
    if (o.btn) o.btn.onclick = () => { want = impl && impl.kind === 'street' ? 'svg' : 'street'; store.set(want); build(want); };
    const ready = build(want);
    return {
      ready,
      draw(s, refit) { scene = s; if (impl) impl.draw(s, refit); },
      destroy() { token++; if (impl) impl.destroy(); impl = null; },
    };
  }

  return {view, load};
})();
