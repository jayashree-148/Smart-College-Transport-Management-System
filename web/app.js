/* CampusTransit dashboard. Plain JavaScript, no libraries. All data comes from the Java backend (/api/...). */
'use strict';

// ---------------------------------------------------------------- helpers
const $ = (s, el = document) => el.querySelector(s);
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const inr = n => '\u20B9' + Number(n || 0).toLocaleString('en-IN');
const COLORS = ['#5b5fef', '#34b868', '#f0a30a', '#8b5cf6', '#e5484d', '#0ea5e9'];

async function api(path, opts = {}) {
  let res;
  try {
    res = await fetch(path, opts.post
      ? {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams(opts.post).toString()}
      : {});
  } catch (e) { throw new Error('Cannot reach the Java server. Is it still running?'); }
  const data = await res.json().catch(() => ({error: 'Bad response from server'}));
  if (res.status === 401 && !path.startsWith('/api/auth/')) { showAuth('login', 'Your session ended. Please sign in again.'); throw new Error('Please sign in'); }
  if (!res.ok) throw new Error(data.error || ('HTTP ' + res.status));
  return data;
}

function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toast.t); toast.t = setTimeout(() => t.hidden = true, 2600);
}
function openModal(html) {
  $('#modalBody').innerHTML = '<button class="close" onclick="closeModal()" aria-label="Close">&times;</button>' + html;
  $('#modal').hidden = false;
}
function closeModal() { $('#modal').hidden = true; }
$('#modal').addEventListener('click', e => { if (e.target.id === 'modal') closeModal(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

function pillFor(status) {
  const m = {ACTIVE: ['green', 'In Transit'], SPARE: ['orange', 'Idle'], MAINTENANCE: ['red', 'Broken Down'],
    PAID: ['green', 'Paid'], PARTIAL: ['orange', 'Partial'], PENDING: ['red', 'Pending'],
    CRITICAL: ['red', 'Critical'], WARNING: ['orange', 'Warning'], INFO: ['blue', 'Info'],
    VALID: ['green', 'Valid'], DUE_SOON: ['orange', 'Due soon'], EXPIRED: ['red', 'Expired'],
    Completed: ['green', 'Completed'], Planned: ['blue', 'Planned'], Idle: ['orange', 'Idle'], Breakdown: ['red', 'Breakdown']};
  const [c, t] = m[status] || ['gray', status];
  return `<span class="pill ${c}">${esc(t)}</span>`;
}

// ---------------------------------------------------------------- SVG map (works offline)
function projector(points, W, H, pad = 40) {
  const lats = points.map(p => p.lat), lngs = points.map(p => p.lng);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats), minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const k = Math.cos(((minLat + maxLat) / 2) * Math.PI / 180);                // keep real proportions
  const w = Math.max((maxLng - minLng) * k, 1e-6), h = Math.max(maxLat - minLat, 1e-6);
  const s = Math.min((W - 2 * pad) / w, (H - 2 * pad) / h);
  const ox = (W - w * s) / 2, oy = (H - h * s) / 2;
  return p => [ox + (p.lng - minLng) * k * s, oy + (maxLat - p.lat) * s];
}
const GRID = id => `<defs><pattern id="${id}" width="30" height="30" patternUnits="userSpaceOnUse"><path d="M30 0H0V30" fill="none" stroke="#e1e4f6" stroke-width="1"/></pattern></defs><rect width="100%" height="100%" fill="url(#${id})"/>`;
function polyline(points, P, color, width = 3, opacity = 1) {
  return `<polyline points="${points.map(p => P(p).map(n => n.toFixed(1)).join(',')).join(' ')}" fill="none" stroke="${color}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" opacity="${opacity}"/>`;
}

// ---------------------------------------------------------------- pages
const PAGES = {
  dashboard: {title: 'Transport Dashboard', icon: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z', render: pageDashboard},
  routes:    {title: 'Routes', icon: 'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM18 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4zM8 17h6a4 4 0 0 0 0-8h-4a4 4 0 0 1 0-8h4', render: pageRoutes},
  vehicles:  {title: 'Vehicles', icon: 'M5 4h14a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM4 11h16M7 20v-3M17 20v-3', render: pageVehicles},
  students:  {title: 'Students', icon: 'M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M17 11a2.5 2.5 0 1 0 0-5M17 14c2.5.4 4 2.6 4 6', render: pageStudents},
  drivers:   {title: 'Drivers', icon: 'M3 6h18v12H3zM8 11a2 2 0 1 0 0-.1zM6 16c.5-1.5 4-1.5 4 0M14 10h4M14 14h3', render: () => pageCrew('Driver')},
  conductors:{title: 'Conductors', icon: 'M3 6h18v12H3zM8 11a2 2 0 1 0 0-.1zM6 16c.5-1.5 4-1.5 4 0M14 10h4M14 14h3', render: () => pageCrew('Conductor')},
  fees:      {title: 'Fee Collection', icon: 'M6 4h12M6 9h12M9 4c5 0 6 5 0 6l6 7', render: pageFees},
  tracking:  {title: 'Tracking', icon: 'M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z', render: pageTracking},
  reports:   {title: 'Reports', icon: 'M7 3h8l4 4v14H7zM14 3v5h5M10 13h6M10 17h6', render: pageReports},
  alerts:    {title: 'Alerts', icon: 'M6 9a6 6 0 1 1 12 0c0 6 2 7.5 2 7.5H4S6 15 6 9zM10 20a2 2 0 0 0 4 0', render: pageAlerts},
  users:     {title: 'Users', admin: true, icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-4.4 3.6-8 8-8s8 3.6 8 8', render: pageUsers},
  settings:  {title: 'Settings', icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z', render: pageSettings},
};

let timer = null;               // live-tracking poll
let mapCtl = null;              // current MapKit view (street/schematic)
let APP_VERSION = '';
let me = null;                  // signed-in user {userId, fullName, role, canWrite}
function mountMap(host, btn, W, H, pad) {
  if (mapCtl) mapCtl.destroy();
  return mapCtl = MapKit.view(host, {W, H, pad, btn, toast});
}
let current = 'dashboard';

function buildNav() {
  $('#nav').innerHTML = Object.entries(PAGES).filter(([, p]) => !p.admin || (me && me.role === 'ADMIN')).map(([k, p]) =>
    `<a href="#${k}" data-k="${k}"><svg viewBox="0 0 24 24"><path d="${p.icon}"/></svg>${p.title === 'Transport Dashboard' ? 'Dashboard' : p.title}</a>`).join('');
}

async function navigate() {
  const key = (location.hash || '#dashboard').slice(1).split('/')[0];
  current = PAGES[key] && (!PAGES[key].admin || (me && me.role === 'ADMIN')) ? key : 'dashboard';
  clearInterval(timer); timer = null;
  if (mapCtl) { mapCtl.destroy(); mapCtl = null; }
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.k === current));
  $('#title').textContent = current === 'dashboard' ? 'Transport Dashboard' : PAGES[current].title;
  $('#sidebar').classList.remove('open');
  const view = $('#view');
  view.innerHTML = '<div class="empty">Loading...</div>';
  try { await PAGES[current].render(view); }
  catch (e) { view.innerHTML = `<div class="err"><b>Could not load this page.</b><br>${esc(e.message)}</div>`; }
}

// ---------------------------------------------------------------- Dashboard
async function pageDashboard(view) {
  const d = await api('/api/dashboard');
  $('#belldot').hidden = !d.alertCount;
  const fs = d.fleetStatus;
  const fee = d.fees;

  // Route overview: the 3 planner trips (Dijkstra) drawn on the stop coordinates
  const tripCol = i => COLORS[i % 3 === 0 ? 0 : i % 3 === 1 ? 1 : 3];
  const ov = {fit: d.overview.trips.flatMap(t => t.points), lines: [], dots: []};
  if (d.overview.depot) ov.fit.push(d.overview.depot);
  d.overview.trips.forEach((t, i) => ov.lines.push({pts: t.points, color: tripCol(i), width: 3.5, opacity: .9}));
  const seen = new Set();
  d.overview.trips.forEach((t, i) => t.points.forEach(p => {
    if (seen.has(p.id) || p.id === 'CAMPUS') return; seen.add(p.id);
    ov.dots.push({lat: p.lat, lng: p.lng, r: 8, fill: COLORS[i % 3 === 0 ? 0 : i % 3 === 1 ? 3 : 0], sw: 2.5, tip: p.id + ' ' + p.name});
  }));
  if (d.overview.depot) ov.dots.push({lat: d.overview.depot.lat, lng: d.overview.depot.lng, r: 13, fill: '#14142b', sw: 3, label: 'DEPOT', big: true, tip: 'Campus depot'});
  const legend = d.overview.trips.map((t, i) => `<span><i style="background:${tripCol(i)}"></i>${esc(t.busId)} (${t.points.length - 1} stops)</span>`).join('');

  // Fleet donut
  const C = 2 * Math.PI * 70, tot = Math.max(fs.total, 1);
  let off = 0, arcs = '';
  [[fs.inTransit, '#34b868'], [fs.idle, '#f0a30a'], [fs.broken, '#e5484d']].forEach(([n, col]) => {
    if (!n) return; const len = n / tot * C;
    arcs += `<circle r="70" cx="90" cy="90" fill="none" stroke="${col}" stroke-width="24" stroke-dasharray="${len} ${C - len}" stroke-dashoffset="${-off}" transform="rotate(-90 90 90)"/>`;
    off += len;
  });
  const routeShort = n => n.replace(/ - Campus$/, '');

  view.innerHTML = `
  <div class="grid kpis">
    <div class="card kpi"><div class="ic" style="background:#eceefe">\uD83D\uDEE3\uFE0F</div><div><div class="lb">Total Routes</div><div class="vl">${d.kpis.routes}</div></div></div>
    <div class="card kpi"><div class="ic" style="background:#dff6fb">\uD83D\uDE8C</div><div><div class="lb">Total Vehicles</div><div class="vl">${d.kpis.vehicles}</div></div></div>
    <div class="card kpi"><div class="ic" style="background:#ece8fd">\uD83D\uDC65</div><div><div class="lb">Total Students</div><div class="vl">${d.kpis.students}</div></div></div>
    <div class="card kpi"><div class="ic" style="background:#e3f7ec">\u20B9</div><div><div class="lb">Fees Collected</div><div class="vl">${inr(d.kpis.collected)}</div><div class="sub">Received</div></div></div>
  </div>
  <div class="grid row-mid">
    <div class="card"><div class="card-head"><h2>Route Overview</h2><span><button class="btn small ghost" id="ovMode">Schematic map</button> <a class="link" href="#routes" style="margin-left:10px">View All Routes</a></span></div>
      <div class="mapbox" id="ovMap"></div><div class="legend">${legend}<span class="muted">Today's pickup plan from the Java Dijkstra planner</span></div></div>
    <div class="card"><h2>Fleet Status</h2>
      <div class="donut-wrap"><div class="donut"><svg viewBox="0 0 180 180" width="180" height="180">${arcs}</svg><div class="center"><b>${fs.total}</b><span>Total Vehicles</span></div></div>
        <div class="lg"><div><i style="background:#34b868"></i>In Transit<b>${fs.inTransit}</b></div><div><i style="background:#f0a30a"></i>Idle<b>${fs.idle}</b></div><div><i style="background:#e5484d"></i>Broken Down<b>${fs.broken}</b></div></div></div>
      <div class="sect">ROUTE LIST</div>
      <div class="list">${d.routeList.slice(0, 4).map((r, i) => `<div class="it"><span class="dot" style="background:${COLORS[i % 4]}"></span><div><b>${esc(r.id)} \u00B7 ${esc(routeShort(r.name))}</b><small>${r.stops} stops \u00B7 ${r.buses} buses \u00B7 ${r.students} students \u00B7 ${esc(r.zone)}</small></div></div>`).join('')}</div>
    </div>
  </div>
  <div class="grid row-bot">
    <div class="card"><h2>Transport Fee Collection <small>This Term</small></h2>
      <div class="money">${inr(fee.collected)}</div><div style="color:var(--green);font-weight:600;font-size:14px">${fee.percent}% of ${inr(fee.due)} collected</div>
      <div class="bar"><span style="width:${fee.percent}%"></span></div>
      <div class="split"><div>${inr(fee.collected)}<small>Collected</small></div><div>${inr(fee.pending)}<small>Pending</small></div></div></div>
    <div class="card"><div class="card-head"><h2>Recent Trips</h2><a class="link" href="#reports">View All Trips</a></div>
      ${d.recentTrips.map(t => `<div class="trip"><div><b>${esc(t.trip)} \u00B7 ${esc(t.busId)}</b><small>${t.students} students boarded \u00B7 ${t.minutes} min</small></div>${pillFor(t.status)}</div>`).join('') || '<div class="empty">No trips planned</div>'}</div>
    <div class="card"><div class="card-head"><h2>Alerts</h2><a class="link" href="#alerts">View All (${d.alertCount})</a></div>
      ${d.alerts.map(a => `<div class="alert"><div class="ai">${alertIcon(a.kind)}</div><div><b>${esc(a.title)}</b><small>${esc(a.detail)}</small></div></div>`).join('') || '<div class="empty">No alerts</div>'}</div>
  </div>`;
  mountMap($('#ovMap'), $('#ovMode'), 1000, 460, 48).draw(ov);
}
function alertIcon(k) { return {BREAKDOWN: '\uD83D\uDEE0\uFE0F', WORKSHOP: '\uD83D\uDEE0\uFE0F', DOCUMENT: '\uD83D\uDCC4', SERVICE: '\uD83D\uDD27', FEE: '\u20B9'}[k] || '\uD83D\uDD14'; }

// ---------------------------------------------------------------- Routes
async function pageRoutes(view) {
  const d = await api('/api/map');
  const routes = d.routes;
  let sel = routes[0].id, rctl = null;
  const draw = () => {
    const r = routes.find(x => x.id === sel);
    const rs = {fit: [...r.stops, d.depot],
      lines: [{pts: [...r.stops, d.depot], color: '#5b5fef', width: 4, opacity: .95}],
      dots: [...r.stops.map((st, i) => ({lat: st.lat, lng: st.lng, r: 11, fill: '#5b5fef', sw: 3, num: i + 1, label: st.name, tip: st.name})),
             {lat: d.depot.lat, lng: d.depot.lng, r: 13, fill: '#14142b', sw: 3, label: 'CAMPUS', big: true}]};
    if (!rctl) rctl = mountMap($('#routeMap'), $('#rtMode'), 1000, 520, 60);
    rctl.draw(rs, true);
    $('#routeInfo').innerHTML = `<h2>${esc(r.id)} \u00B7 ${esc(r.name)}</h2>
      <dl class="kv"><dt>Zone</dt><dd>${esc(r.zone)}</dd><dt>Distance</dt><dd>${r.km} km</dd><dt>Estimated time</dt><dd>${r.minutes} min</dd>
      <dt>Buses on route</dt><dd>${r.busCount} (${r.seats} seats)</dd><dt>Students at its stops</dt><dd>${r.students}</dd></dl>
      <div class="sect" style="margin-top:16px">STOPS IN PICKUP ORDER</div><div class="chain">${r.stops.map((s, i) => `<span class="pick">${i + 1}. ${esc(s.name)}</span>`).join('')}<span>Campus</span></div>
      <div class="sect" style="margin-top:12px">BUSES</div><div class="chips">${r.buses.map(b => `<a class="pill blue" href="#vehicles/${b}" style="text-decoration:none" onclick="openVehicle('${b}');return false">${esc(b)}</a>`).join('') || '<span class="muted">No buses assigned</span>'}</div>`;
    document.querySelectorAll('#routeTable tr[data-id]').forEach(tr => tr.style.background = tr.dataset.id === sel ? '#eceefe' : '');
  };
  view.innerHTML = `<div class="grid cols2"><div class="card"><div class="card-head"><h2>Route map</h2><button class="btn small ghost" id="rtMode">Schematic map</button></div><div class="mapbox" id="routeMap"></div></div><div class="card" id="routeInfo"></div></div>
    <div class="card" style="margin-top:22px"><h2>All routes <small>${routes.length} routes covering ${d.stops.length - 1} stops</small></h2><div class="tablewrap"><table id="routeTable"><thead><tr><th>Route</th><th>Name</th><th>Zone</th><th class="num">Stops</th><th class="num">km</th><th class="num">Minutes</th><th class="num">Buses</th><th class="num">Students</th></tr></thead><tbody>
    ${routes.map(r => `<tr class="click" data-id="${r.id}"><td><b>${r.id}</b></td><td>${esc(r.name)}</td><td>${esc(r.zone)}</td><td class="num">${r.stops.length}</td><td class="num">${r.km}</td><td class="num">${r.minutes}</td><td class="num">${r.busCount}</td><td class="num">${r.students}</td></tr>`).join('')}</tbody></table></div></div>`;
  view.querySelectorAll('#routeTable tr[data-id]').forEach(tr => tr.onclick = () => { sel = tr.dataset.id; draw(); scrollTo({top: 0, behavior: 'smooth'}); });
  draw();
}

// ---------------------------------------------------------------- Vehicles
const vq = {q: '', status: '', route: '', sort: 'id', dir: 'asc', page: 0, size: 15};
async function pageVehicles(view) {
  const routes = (await api('/api/routes')).routes;
  view.innerHTML = `<div class="toolbar">
      <input type="search" id="vsearch" placeholder="Search bus, reg no, model, driver..." value="${esc(vq.q)}">
      <select id="vstatus"><option value="">All status</option><option value="ACTIVE">In Transit (Active)</option><option value="SPARE">Idle (Spare)</option><option value="MAINTENANCE">Broken Down (Maintenance)</option></select>
      <select id="vroute"><option value="">All routes</option>${routes.map(r => `<option value="${r.id}">${r.id} - ${esc(r.name.replace(/ - Campus$/, ''))}</option>`).join('')}</select>
      <div class="spacer"></div><button class="btn ghost" id="vcsv">Export CSV</button></div>
    <div id="vtable"></div>`;
  $('#vstatus').value = vq.status; $('#vroute').value = vq.route;
  let t; $('#vsearch').oninput = e => { clearTimeout(t); t = setTimeout(() => { vq.q = e.target.value; vq.page = 0; loadVehicles(); }, 250); };
  $('#vstatus').onchange = e => { vq.status = e.target.value; vq.page = 0; loadVehicles(); };
  $('#vroute').onchange = e => { vq.route = e.target.value; vq.page = 0; loadVehicles(); };
  $('#vcsv').onclick = exportVehiclesCsv;
  await loadVehicles();
}
const vqs = (o = {}) => new URLSearchParams({...vq, ...o}).toString();
async function loadVehicles() {
  const d = await api('/api/vehicles?' + vqs());
  const th = (k, label, cls = '') => `<th class="sortable ${cls}" data-k="${k}">${label}${vq.sort === k ? (vq.dir === 'asc' ? ' \u25B2' : ' \u25BC') : ''}</th>`;
  const pages = Math.max(1, Math.ceil(d.total / d.size));
  $('#vtable').innerHTML = `<div class="tablewrap"><table><thead><tr>${th('id', 'Bus')}${th('regNo', 'Reg. No')}<th>Make / Model</th><th>Fuel</th>${th('capacity', 'Seats', 'num')}<th>Route</th><th>Shift</th>${th('driver', 'Driver')}${th('status', 'Status')}${th('odometer', 'Odometer', 'num')}</tr></thead><tbody>
    ${d.rows.map(v => `<tr class="click" data-id="${v.id}"><td><b>${v.id}</b></td><td>${esc(v.regNo)}</td><td>${esc(v.makeModel)} <span class="muted">(${v.year})</span></td><td>${v.fuel}</td><td class="num">${v.capacity}</td><td>${esc(v.routeId || 'Planner')}</td><td>${esc(v.shift)}</td><td>${esc(v.driver)}</td><td>${pillFor(v.status)}</td><td class="num">${v.odometerKm.toLocaleString('en-IN')} km</td></tr>`).join('') || '<tr><td colspan="10" class="empty">No vehicles match</td></tr>'}</tbody></table></div>
    <div class="pager"><span>${d.total} vehicles \u00B7 page ${d.page + 1} of ${pages}</span><button class="btn small ghost" id="vprev" ${d.page === 0 ? 'disabled' : ''}>Prev</button><button class="btn small ghost" id="vnext" ${d.page + 1 >= pages ? 'disabled' : ''}>Next</button></div>`;
  document.querySelectorAll('#vtable th.sortable').forEach(h => h.onclick = () => { vq.dir = vq.sort === h.dataset.k && vq.dir === 'asc' ? 'desc' : 'asc'; vq.sort = h.dataset.k; vq.page = 0; loadVehicles(); });
  document.querySelectorAll('#vtable tr.click').forEach(tr => tr.onclick = () => openVehicle(tr.dataset.id));
  $('#vprev').onclick = () => { vq.page--; loadVehicles(); };
  $('#vnext').onclick = () => { vq.page++; loadVehicles(); };
}
async function exportVehiclesCsv() {
  const d = await api('/api/vehicles?' + vqs({page: 0, size: 200}));
  const cols = ['id', 'regNo', 'type', 'capacity', 'makeModel', 'year', 'fuel', 'routeId', 'shift', 'driver', 'driverPhone', 'conductor', 'status', 'odometerKm'];
  const csv = [cols.join(','), ...d.rows.map(r => cols.map(c => '"' + String(r[c] ?? '').replace(/"/g, '""') + '"').join(','))].join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([csv], {type: 'text/csv'})); a.download = 'vehicles.csv'; a.click();
}

async function openVehicle(id) {
  const v = await api('/api/vehicles/' + encodeURIComponent(id));
  let map = '';
  if (v.routeStops && v.routeStops.length) {
    const depot = (await api('/api/map')).depot;
    const pts = [...v.routeStops, depot], P = projector(pts, 560, 260, 34);
    map = `<div class="mapbox"><svg viewBox="0 0 560 260">${GRID('g3')}${polyline(pts, P, '#5b5fef', 3.5)}${v.routeStops.map((s, i) => { const [x, y] = P(s); return `<circle cx="${x}" cy="${y}" r="8" fill="#5b5fef" stroke="#fff" stroke-width="2"><title>${esc(s.name)}</title></circle><text x="${x}" y="${y + 3.5}" font-size="9" fill="#fff" text-anchor="middle" font-weight="700">${i + 1}</text>`; }).join('')}<circle cx="${P(depot)[0]}" cy="${P(depot)[1]}" r="10" fill="#14142b" stroke="#fff" stroke-width="2.5"/></svg></div>`;
  }
  openModal(`<h2>${esc(v.id)} \u00B7 ${esc(v.regNo)}</h2><p class="muted" style="margin:0 0 16px">${esc(v.makeModel)} (${v.year}) \u00B7 ${esc(v.fuel)} \u00B7 ${v.capacity} seats ${pillFor(v.status)}</p>
    <div class="grid cols2"><div>
      <dl class="kv"><dt>Route</dt><dd>${esc(v.routeId ? v.routeId + ' - ' + v.routeName : v.routeName)}</dd><dt>Shift</dt><dd>${esc(v.shift)}</dd>
      <dt>Driver</dt><dd>${esc(v.driver)} \u00B7 ${esc(v.driverPhone)} \u00B7 ${v.driverExp} yrs</dd><dt>Conductor</dt><dd>${esc(v.conductor)} \u00B7 ${esc(v.conductorPhone)}</dd>
      <dt>Odometer</dt><dd>${v.odometerKm.toLocaleString('en-IN')} km${v.mileage ? ' \u00B7 ' + v.mileage + ' km/l' : ''}</dd>
      <dt>Last service</dt><dd>${v.lastService}</dd><dt>Next service</dt><dd>${v.nextService} ${v.serviceDays < 0 ? '<span class="pill red">overdue</span>' : '<span class="muted">(in ' + v.serviceDays + ' days)</span>'}</dd>
      <dt>GPS device</dt><dd>${esc(v.gpsDevice)}</dd><dt>Remarks</dt><dd>${esc(v.remarks) || '-'}</dd></dl>
      <div class="sect" style="margin-top:16px">DOCUMENTS</div>
      ${v.documents.map(x => `<div class="trip" style="padding:9px 2px"><div>${esc(x.name)}<small>${x.expiry} (${x.daysLeft < 0 ? -x.daysLeft + ' days ago' : 'in ' + x.daysLeft + ' days'})</small></div>${pillFor(x.state)}</div>`).join('')}
    </div><div>${map || '<div class="empty">This bus is planned live by the Java route planner (see Reports).</div>'}
      ${v.routeStops.length ? `<div class="chain">${v.routeStops.map((s, i) => `<span class="pick">${i + 1}. ${esc(s.name)}</span>`).join('')}</div><div class="muted" style="font-size:13px">${v.routeKm} km \u00B7 about ${v.routeMinutes} min</div>` : ''}
      <div class="sect" style="margin-top:18px">CHANGE STATUS</div>
      <div class="toolbar" style="margin:0"><select id="newStatus"><option value="ACTIVE">In Transit (Active)</option><option value="SPARE">Idle (Spare)</option><option value="MAINTENANCE">Broken Down (Maintenance)</option></select><button class="btn" id="saveStatus">Save</button></div>
    </div></div>`);
  $('#newStatus').value = v.status;
  $('#saveStatus').onclick = async () => {
    try { await api(`/api/vehicles/${encodeURIComponent(v.id)}/status`, {post: {value: $('#newStatus').value}}); toast(v.id + ' updated in the database'); closeModal(); navigate(); }
    catch (e) { toast(e.message); }
  };
}

// ---------------------------------------------------------------- Drivers / Conductors
const crewState = {};
async function pageCrew(role) {
  const d = await api('/api/crew?role=' + role);
  const st = crewState[role] = crewState[role] || {q: '', page: 0};
  const view = $('#view');
  const draw = () => {
    const rows = d.rows.filter(r => !st.q || (r.name + r.busId + r.regNo + r.phone).toLowerCase().includes(st.q.toLowerCase()));
    const size = 15, pages = Math.max(1, Math.ceil(rows.length / size)); st.page = Math.min(st.page, pages - 1);
    $('#crewTable').innerHTML = `<div class="tablewrap"><table><thead><tr><th>Name</th><th>Phone</th>${role === 'Driver' ? '<th class="num">Experience</th>' : ''}<th>Bus</th><th>Reg. No</th><th>Shift</th><th>Status</th></tr></thead><tbody>
      ${rows.slice(st.page * size, st.page * size + size).map(r => `<tr class="click" data-id="${r.busId}"><td><b>${esc(r.name)}</b></td><td>${esc(r.phone)}</td>${role === 'Driver' ? `<td class="num">${r.experience} yrs</td>` : ''}<td>${r.busId}</td><td>${esc(r.regNo)}</td><td>${esc(r.shift)}</td><td>${pillFor(r.status)}</td></tr>`).join('') || '<tr><td colspan="7" class="empty">No match</td></tr>'}</tbody></table></div>
      <div class="pager"><span>${rows.length} ${role.toLowerCase()}s \u00B7 page ${st.page + 1} of ${pages}</span><button class="btn small ghost" id="cprev" ${st.page === 0 ? 'disabled' : ''}>Prev</button><button class="btn small ghost" id="cnext" ${st.page + 1 >= pages ? 'disabled' : ''}>Next</button></div>`;
    document.querySelectorAll('#crewTable tr.click').forEach(tr => tr.onclick = () => openVehicle(tr.dataset.id));
    $('#cprev').onclick = () => { st.page--; draw(); }; $('#cnext').onclick = () => { st.page++; draw(); };
  };
  view.innerHTML = `<div class="toolbar"><input type="search" id="csearch" placeholder="Search ${role.toLowerCase()}s..." value="${esc(st.q)}"></div><div id="crewTable"></div>`;
  $('#csearch').oninput = e => { st.q = e.target.value; st.page = 0; draw(); };
  draw();
}

// ---------------------------------------------------------------- Students
async function pageStudents(view) {
  const d = await api('/api/students');
  let q = '', f = '';
  const draw = () => {
    const rows = d.rows.filter(r => (!q || (r.id + r.name + r.stopName).toLowerCase().includes(q.toLowerCase())) && (!f || r.feeStatus === f));
    $('#stTable').innerHTML = `<div class="tablewrap"><table><thead><tr><th>ID</th><th>Name</th><th>Pickup stop</th><th>Authorized</th><th>Attendance (${d.date})</th><th class="num">Fee paid / due</th><th>Fee</th></tr></thead><tbody>
      ${rows.map(r => `<tr><td><b>${r.id}</b></td><td>${esc(r.name)}</td><td>${r.stopId} ${esc(r.stopName)}</td><td>${r.authorized ? '<span class="pill green">Yes</span>' : '<span class="pill red">No</span>'}</td>
      <td>${r.present ? '<span class="pill green">Present</span>' : '<span class="pill gray">Absent</span>'} <button class="btn small ghost" data-att="${r.id}" data-p="${!r.present}">Mark ${r.present ? 'absent' : 'present'}</button></td>
      <td class="num">${inr(r.feePaid)} / ${inr(r.feeDue)}</td><td>${pillFor(r.feeStatus)}</td></tr>`).join('')}</tbody></table></div><div class="pager"><span>${rows.length} of ${d.total} students</span></div>`;
    document.querySelectorAll('[data-att]').forEach(b => b.onclick = async () => {
      try { await api('/api/attendance', {post: {studentId: b.dataset.att, present: b.dataset.p}}); const r = d.rows.find(x => x.id === b.dataset.att); r.present = b.dataset.p === 'true'; toast('Attendance saved'); draw(); }
      catch (e) { toast(e.message); }
    });
  };
  view.innerHTML = `<div class="toolbar"><input type="search" id="ssearch" placeholder="Search student or stop..."><select id="sfee"><option value="">All fee status</option><option value="PAID">Paid</option><option value="PARTIAL">Partial</option><option value="PENDING">Pending</option></select></div><div id="stTable"></div>`;
  $('#ssearch').oninput = e => { q = e.target.value; draw(); };
  $('#sfee').onchange = e => { f = e.target.value; draw(); };
  draw();
}

// ---------------------------------------------------------------- Fees
async function pageFees(view) {
  const d = await api('/api/fees'), s = d.summary;
  view.innerHTML = `<div class="grid kpis">
    <div class="card"><div class="lb muted">Total due</div><div class="money">${inr(s.due)}</div></div>
    <div class="card"><div class="lb muted">Collected</div><div class="money" style="color:var(--green)">${inr(s.collected)}</div></div>
    <div class="card"><div class="lb muted">Pending</div><div class="money" style="color:var(--orange)">${inr(s.pending)}</div></div>
    <div class="card"><div class="lb muted">Students: paid / partial / pending</div><div class="money">${s.paidCount} / ${s.partialCount} / ${s.pendingCount}</div></div></div>
    <div class="card"><div class="bar" style="margin-top:0"><span style="width:${s.percent}%"></span></div><div class="muted">${s.percent}% collected</div></div>
    <div class="tablewrap" style="margin-top:22px"><table><thead><tr><th>Student</th><th>Name</th><th class="num">Due</th><th class="num">Paid</th><th class="num">Balance</th><th>Status</th><th></th></tr></thead><tbody>
    ${d.rows.map(r => `<tr><td><b>${r.studentId}</b></td><td>${esc(r.name)}</td><td class="num">${inr(r.due)}</td><td class="num">${inr(r.paid)}</td><td class="num">${inr(r.balance)}</td><td>${pillFor(r.status)}</td><td>${r.balance > 0 ? `<button class="btn small" data-pay="${r.studentId}" data-bal="${r.balance}" data-name="${esc(r.name)}">Record payment</button>` : ''}</td></tr>`).join('')}</tbody></table></div>`;
  document.querySelectorAll('[data-pay]').forEach(b => b.onclick = () => {
    openModal(`<h2>Record payment</h2><p class="muted">${esc(b.dataset.name)} (${b.dataset.pay}) \u00B7 balance ${inr(b.dataset.bal)}</p>
      <div class="toolbar"><input type="number" id="payAmt" min="1" max="${b.dataset.bal}" value="${b.dataset.bal}"><button class="btn" id="payGo">Save payment</button></div>`);
    $('#payGo').onclick = async () => {
      try { await api('/api/fees/pay', {post: {studentId: b.dataset.pay, amount: $('#payAmt').value}}); closeModal(); toast('Payment saved'); navigate(); }
      catch (e) { toast(e.message); }
    };
  });
}

// ---------------------------------------------------------------- Tracking (simulated GPS, polled from the Java server)
async function pageTracking(view) {
  const map = await api('/api/map');
  let sel = null, data = null;
  view.innerHTML = `<div class="toolbar"><input type="search" id="tsearch" placeholder="Search bus..."><div class="spacer"></div><span class="muted" id="tinfo"></span><button class="btn ghost" id="tmode">Schematic map</button></div>
    <div class="track"><div class="card"><div id="tmap" class="mapbox"></div><div class="legend"><span><i style="background:#34b868"></i>seats under 60% full</span><span><i style="background:#f0a30a"></i>60-85%</span><span><i style="background:#e5484d"></i>over 85%</span></div></div>
    <div class="card side" id="tlist"></div></div>`;
  const col = b => b.fillPct >= 85 ? '#e5484d' : b.fillPct >= 60 ? '#f0a30a' : '#34b868';
  const route = id => map.routes.find(r => r.id === id);
  const mc = mountMap($('#tmap'), $('#tmode'), 1000, 640, 36);
  let firstDraw = true;
  function draw() {
    if (!data) return;
    const r = sel && route(sel.routeId);
    mc.draw({fit: [...map.stops],
      lines: r ? [{pts: [...r.stops, map.depot], color: '#5b5fef', width: 4, opacity: .9}] : [],
      dots: [...map.stops.filter(p => p.id !== 'CAMPUS').map(p => ({lat: p.lat, lng: p.lng, r: 3, fill: '#b7bce6', sw: 1, tip: p.name})),
             ...data.buses.map(b => { const on = sel && sel.id === b.id;
               return {lat: b.lat, lng: b.lng, r: on ? 10 : 6, fill: col(b), stroke: on ? '#14142b' : '#fff', sw: on ? 3 : 1.6, tip: `${b.id} - next: ${b.nextStop}`, onClick: () => pick(b.id)}; }),
             {lat: map.depot.lat, lng: map.depot.lng, r: 12, fill: '#14142b', sw: 3, label: 'CAMPUS', big: true}]}, firstDraw);
    firstDraw = false;
  }
  function list() {
    const q = ($('#tsearch').value || '').toLowerCase();
    $('#tlist').innerHTML = `<h2>Buses on the road <small>${data.moving}</small></h2>` + data.buses.filter(b => !q || (b.id + b.regNo + b.driver).toLowerCase().includes(q)).map(b =>
      `<div class="bus-row ${sel && sel.id === b.id ? 'on' : ''}" data-b="${b.id}"><div><b>${b.id}</b><small>${esc(b.regNo)} \u00B7 ${esc(b.driver)}</small></div><div style="text-align:right"><b>${b.etaMin} min</b><small>to ${esc(b.nextStop)}</small></div></div>`).join('');
    document.querySelectorAll('#tlist [data-b]').forEach(r => r.onclick = () => pick(r.dataset.b));
  }
  function pick(id) { sel = data.buses.find(b => b.id === id); draw(); list();
    if (sel) $('#tinfo').textContent = `${sel.id}: ${sel.kmh} km/h \u00B7 ${sel.fillPct}% full \u00B7 trip ${sel.progress}% done`; }
  async function tick() {
    try { data = await api('/api/tracking'); if (sel) sel = data.buses.find(b => b.id === sel.id) || null;
      if (!sel) $('#tinfo').textContent = `${data.moving} buses moving (simulated GPS)`; draw(); list(); }
    catch (e) { $('#tinfo').textContent = e.message; }
  }
  $('#tsearch').oninput = () => data && list();
  await tick();
  timer = setInterval(tick, 2500);
}

// ---------------------------------------------------------------- Reports (+ the Java planner)
async function pageReports(view) {
  const [r, plan] = await Promise.all([api('/api/reports'), api('/api/plan')]);
  const bars = (title, obj) => { const max = Math.max(...Object.values(obj), 1);
    return `<div class="card"><h2>${title}</h2>${Object.entries(obj).map(([k, v]) => `<div class="hbar"><span class="nm" title="${esc(k)}">${esc(k)}</span><span class="tr"><span style="width:${v / max * 100}%"></span></span><span class="v">${v}</span></div>`).join('')}</div>`; };
  view.innerHTML = `<div class="grid kpis">
      <div class="card"><div class="lb muted">Vehicles</div><div class="money">${r.totalVehicles}</div></div>
      <div class="card"><div class="lb muted">Total seats</div><div class="money">${r.totalSeats.toLocaleString('en-IN')}</div></div>
      <div class="card"><div class="lb muted">Documents expired</div><div class="money" style="color:var(--red)">${r.documentsExpired}</div></div>
      <div class="card"><div class="lb muted">Expiring within 30 days</div><div class="money" style="color:var(--orange)">${r.documentsDueSoon}</div></div></div>
    <div class="grid cols3">${bars('Fleet by fuel', r.fleetByFuel)}${bars('Fleet by bus type', r.fleetByType)}${bars('Fleet by status', r.fleetByStatus)}</div>
    <div class="grid cols2" style="margin-top:22px">${bars('Fleet by shift', r.fleetByShift)}
      <div class="card"><h2>Route load <small>students at each route's stops</small></h2><div class="tablewrap" style="max-height:330px;overflow:auto"><table><thead><tr><th>Route</th><th class="num">Buses</th><th class="num">Seats</th><th class="num">Students</th></tr></thead><tbody>${r.routeLoad.map(x => `<tr><td><b>${x.id}</b> ${esc(x.name.replace(/ - Campus$/, ''))}</td><td class="num">${x.buses}</td><td class="num">${x.seats}</td><td class="num">${x.students}</td></tr>`).join('')}</tbody></table></div></div></div>
    <div class="card" style="margin-top:22px"><div class="card-head"><h2>Pickup plan <small>${plan.date} \u00B7 Java planner</small></h2>
      <div class="toolbar" style="margin:0"><select id="bkBus"><option value="">No breakdown</option>${plan.trips.map(t => `<option>${t.busId}</option>`).join('')}</select><button class="btn ghost" id="bkRun">Simulate breakdown</button><button class="btn" id="planSave">Save plan to database</button></div></div>
      <p class="muted" style="margin-top:0">${esc(plan.algorithm)}</p><div id="planBody"></div></div>`;
  const showPlan = p => {
    $('#planBody').innerHTML = (p.message ? `<div class="banner" style="border-radius:12px;margin-bottom:12px;padding:10px 14px">${esc(p.message)}</div>` : '') +
      p.trips.map(t => `<div class="trip-card"><h3><span>TRIP-${t.tripNo} \u00B7 ${t.busId} <span class="muted" style="font-weight:400">(${t.busType}, ${t.capacity} seats)</span></span>${pillFor(t.status === 'COMPLETED' ? 'Completed' : t.status === 'BROKEN_DOWN' ? 'Breakdown' : 'Planned')}</h3>
        <div class="muted">${t.seatsUsed}/${t.capacity} seats used \u00B7 ${t.totalMinutes} min</div>
        <div class="chain">${t.route.map((id, i) => `<span class="${t.pickups.some(p => p.stopId === id) ? 'pick' : ''}">${esc(t.routeNames[i] || id)}</span>`).join(' \u2192 ')}</div>
        ${t.pickups.map(pk => `<div style="font-size:13.5px;margin:4px 0"><b>${esc(pk.stopName)}</b> <span class="muted">(+${pk.minutes} min)</span>: ${pk.students.map(s => esc(s.name)).join(', ') || '-'}</div>`).join('')}</div>`).join('') +
      `<h3 style="margin:18px 0 6px">Unallocated students (${p.unallocated.length})</h3>` +
      (p.unallocated.map(u => `<div style="font-size:14px;margin:3px 0">${esc(u.id)} ${esc(u.name)} at ${esc(u.stopId)} <span class="muted">- ${esc(u.reason)}</span></div>`).join('') || '<span class="muted">None</span>') +
      `<h3 style="margin:18px 0 6px">Not served today (${p.skipped.length})</h3><div class="muted" style="font-size:13.5px">${p.skipped.map(esc).join('<br>') || 'None'}</div>`;
  };
  showPlan(plan);
  $('#bkRun').onclick = async () => { const b = $('#bkBus').value; showPlan(await api('/api/plan' + (b ? `?breakdown=${b}&after=1` : ''))); };
  $('#planSave').onclick = async () => { try { const b = $('#bkBus').value; const p = await api('/api/plan' + (b ? `?breakdown=${b}&after=1` : ''), {post: {}}); showPlan(p); toast('Plan saved to daily_trips / trip_assignments'); } catch (e) { toast(e.message); } };
}

// ---------------------------------------------------------------- Alerts
async function pageAlerts(view) {
  const d = await api('/api/alerts');
  $('#belldot').hidden = !(d.critical + d.warning + d.info);
  let f = '';
  const draw = () => {
    const rows = d.alerts.filter(a => !f || a.severity === f);
    $('#alList').innerHTML = rows.map(a => `<div class="alert"><div class="ai">${alertIcon(a.kind)}</div><div style="flex:1"><b>${esc(a.title)}</b><small>${esc(a.detail)}</small></div>${pillFor(a.severity)}</div>`).join('') || '<div class="empty">Nothing here</div>';
    document.querySelectorAll('#alTabs button').forEach(b => b.classList.toggle('on', b.dataset.f === f));
  };
  view.innerHTML = `<div class="tabs" id="alTabs"><button data-f="">All (${d.critical + d.warning + d.info})</button><button data-f="CRITICAL">Critical (${d.critical})</button><button data-f="WARNING">Warning (${d.warning})</button><button data-f="INFO">Info (${d.info})</button></div>
    <div class="card" id="alList"></div><p class="muted">Alerts come out of a PriorityQueue in Java, so the most urgent one is always first.</p>`;
  document.querySelectorAll('#alTabs button').forEach(b => b.onclick = () => { f = b.dataset.f; draw(); });
  draw();
}

// ---------------------------------------------------------------- Settings / system info
async function pageSettings(view) {
  const [s, ev] = await Promise.all([api('/api/system'), api('/api/events')]);
  const up = s.uptimeSeconds > 90 ? Math.round(s.uptimeSeconds / 60) + ' min' : s.uptimeSeconds + ' s';
  view.innerHTML = `<div class="grid cols2"><div class="card"><h2>Backend</h2><dl class="kv"><dt>Data source</dt><dd>${s.mysql ? '<span class="pill green">MySQL</span> ' : '<span class="pill orange">Demo files</span> '}${esc(s.dataSource)}</dd>
      <dt>Java version</dt><dd>${esc(s.java)}</dd><dt>Uptime</dt><dd>${up}</dd><dt>Stops</dt><dd>${s.counts.stops}</dd><dt>Routes</dt><dd>${s.counts.routes}</dd><dt>Vehicles</dt><dd>${s.counts.vehicles}</dd><dt>Students</dt><dd>${s.counts.students}</dd></dl>
      <div class="sect" style="margin-top:18px">API ENDPOINTS</div><div class="chain">${s.endpoints.map(e => `<span>${esc(e)}</span>`).join('')}</div></div>
    <div class="card"><h2>Recent database events <small>event_log table</small></h2><div style="max-height:360px;overflow:auto">${ev.events.slice(0, 30).map(e => `<div class="alert"><div class="ai">\uD83D\uDDD2\uFE0F</div><div><b>${esc(e.type)}</b><small>${esc(e.details)} \u00B7 ${esc(e.time)}</small></div></div>`).join('')}</div></div></div>
    <div class="grid cols2" style="margin-top:22px"><div class="card"><h2>OOP concepts used</h2><div class="tablewrap"><table><tbody>${s.oop.map(o => `<tr><td><b>${esc(o.concept)}</b></td><td style="white-space:normal">${esc(o.example)}</td></tr>`).join('')}</tbody></table></div></div>
    <div class="card"><h2>Data structures used</h2><div class="tablewrap"><table><tbody>${s.dataStructures.map(o => `<tr><td><b>${esc(o.structure)}</b></td><td style="white-space:normal">${esc(o.where)}<br><span class="muted">${esc(o.why)}</span></td></tr>`).join('')}</tbody></table></div></div></div>`;
}

// ---------------------------------------------------------------- sign in / register / account
function showAuth(mode, notice) {
  if (mapCtl) { mapCtl.destroy(); mapCtl = null; }
  clearInterval(timer); timer = null; me = null;
  $('#usermenu').hidden = true; closeModal();
  const reg = mode === 'register', box = $('#auth');
  box.hidden = false;
  box.innerHTML = `<div class="auth-card"><div class="brand"><div class="brand-icon"><svg viewBox="0 0 24 24"><rect x="4" y="3" width="16" height="15" rx="3"/><path d="M4 11h16M8 21v-3M16 21v-3"/></svg></div><span>CampusTransit</span></div>
    <h2>${reg ? 'Create your account' : 'Sign in'}</h2><p class="sub">${reg ? 'New accounts can view everything. An admin can give you edit access.' : 'Use your user ID and password to open the transport dashboard.'}</p>
    <form id="authForm" autocomplete="on">
      ${reg ? '<label for="aName">Full name</label><input id="aName" name="name" autocomplete="name" maxlength="60" required>' : ''}
      <label for="aId">User ID</label><input id="aId" name="username" autocomplete="username" maxlength="20" autocapitalize="none" spellcheck="false" required ${reg ? 'placeholder="3-20 letters, digits . _ -"' : ''}>
      <label for="aPw">Password</label><input id="aPw" name="password" type="password" autocomplete="${reg ? 'new-password' : 'current-password'}" maxlength="64" required ${reg ? 'placeholder="8+ characters, a letter and a digit"' : ''}>
      ${reg ? '<label for="aPw2">Confirm password</label><input id="aPw2" type="password" autocomplete="new-password" maxlength="64" required>' : ''}
      <div class="msg" id="aMsg" role="alert">${esc(notice || '')}</div>
      <button class="btn" id="aGo" type="submit">${reg ? 'Create account' : 'Sign in'}</button></form>
    <div class="switch">${reg ? 'Already have an account? <a id="aSwitch">Sign in</a>' : 'New here? <a id="aSwitch">Create an account</a>'}</div>
    <div class="ver">CampusTransit ${esc(APP_VERSION)}</div>
    ${reg ? '' : '<div class="hint">First time on this computer? Sign in as <b>admin</b> / <b>Admin@123</b> and change the password from the user menu.</div>'}</div>`;
  $('#aSwitch').onclick = () => showAuth(reg ? 'login' : 'register');
  $(reg ? '#aName' : '#aId').focus();
  $('#authForm').onsubmit = async e => {
    e.preventDefault();
    const msg = t => $('#aMsg').textContent = t;
    if (reg && $('#aPw').value !== $('#aPw2').value) return msg('The two passwords do not match');
    $('#aGo').disabled = true; msg('');
    try {
      const r = await api(reg ? '/api/auth/register' : '/api/auth/login',
        {post: reg ? {userId: $('#aId').value, fullName: $('#aName').value, password: $('#aPw').value} : {userId: $('#aId').value, password: $('#aPw').value}});
      await startApp(r.user);
    } catch (err) { msg(err.message); $('#aGo').disabled = false; $('#aPw').value = ''; if (reg) $('#aPw2').value = ''; $('#aPw').focus(); }
  };
}

function setUserMenu() {
  $('#usermenu').hidden = false;
  $('#avatar').textContent = me.fullName.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
  $('#uName').textContent = me.fullName;
  $('#uRole').textContent = me.userId + ' \u00B7 ' + ({ADMIN: 'Admin', STAFF: 'Staff', VIEWER: 'View only'}[me.role]);
}
$('#userBtn').onclick = e => { e.stopPropagation(); $('#userDrop').hidden = !$('#userDrop').hidden; };
document.addEventListener('click', () => $('#userDrop').hidden = true);
$('#uOut').onclick = async () => { try { await api('/api/auth/logout', {post: {}}); } catch (e) { /* already signed out */ } location.hash = ''; showAuth('login'); };
$('#uPw').onclick = () => {
  openModal(`<h2>Change password</h2><p class="muted">Signed in as <b>${esc(me.userId)}</b>. Your other devices will be signed out.</p>
    <div class="toolbar" style="flex-direction:column;align-items:stretch"><input type="password" id="pwOld" placeholder="Current password" autocomplete="current-password"><input type="password" id="pwNew" placeholder="New password (8+ chars, letter + digit)" autocomplete="new-password"><input type="password" id="pwNew2" placeholder="Repeat new password" autocomplete="new-password"></div>
    <button class="btn" id="pwSave">Save password</button>`);
  $('#pwSave').onclick = async () => {
    if ($('#pwNew').value !== $('#pwNew2').value) return toast('The new passwords do not match');
    try { await api('/api/auth/password', {post: {oldPassword: $('#pwOld').value, newPassword: $('#pwNew').value}}); closeModal(); toast('Password changed'); }
    catch (e) { toast(e.message); }
  };
};

// ---------------------------------------------------------------- Users (admin only)
async function pageUsers(view) {
  const d = await api('/api/users');
  const roleSel = u => `<select data-role="${esc(u.userId)}">${['ADMIN', 'STAFF', 'VIEWER'].map(r => `<option ${r === u.role ? 'selected' : ''}>${r}</option>`).join('')}</select>`;
  view.innerHTML = `<div class="card"><h2>User accounts <small>${d.users.length} users</small></h2>
    <p class="muted" style="margin-top:0"><b>ADMIN</b> manages users and everything else \u00B7 <b>STAFF</b> can change vehicles, fees, attendance and plans \u00B7 <b>VIEWER</b> is read-only. New sign-ups start as VIEWER.</p>
    <div class="tablewrap"><table><thead><tr><th>User ID</th><th>Name</th><th>Role</th><th>Created</th><th>Status</th><th></th></tr></thead><tbody>
    ${d.users.map(u => `<tr><td><b>${esc(u.userId)}</b>${u.you ? ' <span class="pill blue">you</span>' : ''}</td><td>${esc(u.fullName)}</td><td>${roleSel(u)}</td><td>${esc(u.createdAt)}</td><td>${u.locked ? '<span class="pill red">Locked</span>' : '<span class="pill green">Active</span>'}</td>
      <td style="white-space:nowrap"><button class="btn small ghost" data-reset="${esc(u.userId)}">Reset password</button> ${u.you ? '' : `<button class="btn small danger" data-del="${esc(u.userId)}">Delete</button>`}</td></tr>`).join('')}
    </tbody></table></div></div>`;
  view.querySelectorAll('[data-role]').forEach(sel => sel.onchange = async () => {
    try { await api('/api/users/role', {post: {userId: sel.dataset.role, role: sel.value}}); toast(sel.dataset.role + ' is now ' + sel.value); }
    catch (e) { toast(e.message); } finally { navigate(); } });
  view.querySelectorAll('[data-reset]').forEach(b => b.onclick = () => {
    openModal(`<h2>Reset password</h2><p class="muted">Set a new password for <b>${esc(b.dataset.reset)}</b>. They will be signed out everywhere.</p><div class="toolbar"><input type="password" id="rpNew" placeholder="New password (8+ chars, letter + digit)" autocomplete="new-password" style="min-width:280px"></div><button class="btn" id="rpGo">Reset</button>`);
    $('#rpGo').onclick = async () => { try { await api('/api/users/reset', {post: {userId: b.dataset.reset, newPassword: $('#rpNew').value}}); closeModal(); toast('Password reset'); } catch (e) { toast(e.message); } };
  });
  view.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
    if (!confirm('Delete user ' + b.dataset.del + '?')) return;
    try { await api('/api/users/delete', {post: {userId: b.dataset.del}}); toast('User deleted'); navigate(); } catch (e) { toast(e.message); } });
}

// ---------------------------------------------------------------- connect MySQL from the page (admin)
async function openDbConnect() {
  let st = {host: 'localhost', port: '3306', user: 'root', note: ''};
  try { st = await api('/api/db'); } catch (e) { /* use defaults */ }
  openModal(`<h2>Connect to MySQL</h2>
    <p class="muted" style="margin-top:0">Start your MySQL service first (XAMPP / Workbench / Windows Services), then enter the login you use there. The database <b>college_transport_db</b> and its tables are created automatically.</p>
    <div class="toolbar" style="flex-direction:column;align-items:stretch;gap:10px">
      <label>Host<input id="dbHost" value="${esc(st.host)}" autocomplete="off"></label>
      <label>Port<input id="dbPort" value="${esc(st.port)}" inputmode="numeric" autocomplete="off"></label>
      <label>User<input id="dbUser" value="${esc(st.user)}" autocomplete="off"></label>
      <label>Password <small class="muted">(XAMPP default is empty)</small><input id="dbPass" type="password" autocomplete="off"></label>
    </div>
    <div class="err" id="dbErr" style="display:none;margin-top:12px"></div>
    <div style="margin-top:14px;display:flex;gap:10px"><button class="btn" id="dbGo">Connect</button><button class="btn ghost" id="dbCancel">Cancel</button></div>`);
  const err = $('#dbErr'), go = $('#dbGo');
  $('#dbCancel').onclick = closeModal;
  go.onclick = async () => {
    go.disabled = true; go.textContent = 'Connecting...'; err.style.display = 'none';
    try {
      const r = await api('/api/db/connect', {post: {host: $('#dbHost').value, port: $('#dbPort').value, user: $('#dbUser').value, password: $('#dbPass').value}});
      closeModal(); toast(r.message); await startApp(me);
    } catch (e) { err.textContent = e.message; err.style.display = 'block'; go.disabled = false; go.textContent = 'Connect'; }
  };
  $('#dbPass').addEventListener('keydown', e => { if (e.key === 'Enter') go.click(); });
  $('#dbPass').focus();
}

// ---------------------------------------------------------------- start
async function startApp(user) {
  me = user;
  $('#auth').hidden = true; $('#auth').innerHTML = '';
  setUserMenu(); buildNav();
  if (!$('#sidebar .ver')) $('#sidebar').insertAdjacentHTML('beforeend', `<div class="ver side">${esc(APP_VERSION)}</div>`);
  try {
    const s = await api('/api/system');
    const b = $('#banner');
    const notes = [];
    if (!s.mysql) notes.push('Demo mode - MySQL is not connected, so you are seeing the sample data files (data/demo). ' + (s.dbNote ? 'Reason: ' + s.dbNote : 'Check db.properties and restart.'));
    if (!me.canWrite) notes.push('You are signed in as view-only. Ask an admin for STAFF access to change data.');
    b.hidden = !notes.length; b.textContent = notes.join(' ');
    if (!s.mysql && me.role === 'ADMIN') {
      b.insertAdjacentHTML('beforeend', ' <button class="btn small" id="dbConnect" style="margin-left:10px">Connect to MySQL</button>');
      $('#dbConnect').onclick = openDbConnect;
    } else if (!s.mysql) b.textContent += ' Ask an admin to connect MySQL.';
  } catch (e) { /* the page itself will show the error */ }
  navigate();
}
$('#burger').onclick = () => $('#sidebar').classList.toggle('open');
window.addEventListener('hashchange', () => { if (me) navigate(); });
(async () => {
  const splash = $('#splash'), bar = $('#splashBar'), stepEl = $('#splashStep');
  const step = (pct, text) => { bar.style.width = pct + '%'; stepEl.textContent = text; };
  const wait = ms => new Promise(r => setTimeout(r, ms));
  const t0 = Date.now();
  let user = null, notice = '';
  step(15, 'Starting server...');
  await wait(350);
  try {
    step(45, 'Connecting to the database...');
    const r = await api('/api/auth/me');
    user = r.user; APP_VERSION = 'v' + (r.version || '');
    step(75, r.mysql ? 'MySQL connected' : 'Demo data loaded (MySQL not connected)');
    await wait(450);
    step(90, 'Loading maps...');
    MapKit.load();                                   // warm up Leaflet while the splash is showing
  } catch (e) { notice = 'Cannot reach the Java server: ' + e.message; step(90, 'Server not reachable'); }
  await wait(Math.max(0, 1900 - (Date.now() - t0)));  // show the loading page long enough to be seen
  step(100, user ? 'Welcome back' : 'Ready - please sign in');
  await wait(250);
  if (user) await startApp(user); else showAuth('login', notice);
  splash.classList.add('done');
  setTimeout(() => splash.remove(), 600);
})();
