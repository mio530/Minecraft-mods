'use strict';
// RyzeSMP Test Client dashboard. Vanilla JS, no build step.

const $ = (id) => document.getElementById(id);
let token = localStorage.getItem('ryze_token') || '';
let ws = null;
let lastObs = null;

const api = async (path, opts = {}) => {
  const res = await fetch(path, {
    ...opts,
    headers: { 'content-type': 'application/json', authorization: 'Bearer ' + token, ...(opts.headers || {}) },
  });
  if (res.status === 401) { logout(); throw new Error('unauthorized'); }
  const ct = res.headers.get('content-type') || '';
  const body = ct.includes('application/json') ? await res.json() : await res.text();
  if (!res.ok) throw Object.assign(new Error(body.error || res.statusText), { status: res.status, body });
  return body;
};

// ---------- auth ----------
function showApp(show) {
  $('login').hidden = show;
  $('app').hidden = !show;
}
function logout() {
  token = ''; localStorage.removeItem('ryze_token');
  if (ws) { ws.close(); ws = null; }
  showApp(false);
}
$('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('loginErr').textContent = '';
  try {
    const { token: t } = await api('/api/login', { method: 'POST', body: JSON.stringify({ password: $('password').value }) });
    token = t; localStorage.setItem('ryze_token', t);
    boot();
  } catch (err) { $('loginErr').textContent = 'Login fehlgeschlagen'; }
});
$('btnLogout').addEventListener('click', logout);

// ---------- Microsoft device-code auth ----------
let msPoll = null;
async function refreshMsAuth() {
  try {
    const s = await api('/api/auth/status');
    if (s.authenticated) {
      $('msAuth').hidden = true;
      if (msPoll) { clearInterval(msPoll); msPoll = null; }
      return true;
    }
    $('msAuth').hidden = false;
    if (s.pending) {
      $('msCodeBox').hidden = false;
      $('msCode').textContent = s.pending.userCode;
      $('msLink').href = s.pending.verificationUri;
      $('msLink').textContent = s.pending.verificationUri;
    }
    return false;
  } catch { return false; }
}
$('btnMsLogin').addEventListener('click', async () => {
  await api('/api/auth/login', { method: 'POST' });
  if (!msPoll) msPoll = setInterval(refreshMsAuth, 2500);
  refreshMsAuth();
});

// ---------- websocket live view ----------
function connectWs() {
  if (ws) ws.close();
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(`${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`);
  ws.onmessage = (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.type === 'observation') {
      lastObs = msg.obs;
      $('viewer').innerHTML = msg.svg;
      renderStatus(msg.obs);
      setConn(msg.obs.connected);
    } else if (msg.type === 'approval_requested' || msg.type === 'approval_resolved') {
      loadApprovals();
    }
  };
  ws.onclose = () => { setConn(false); };
}
function wsSend(o) { if (ws && ws.readyState === 1) ws.send(JSON.stringify(o)); }

function setConn(on) {
  const b = $('connBadge');
  b.textContent = on ? 'verbunden' : 'offline';
  b.className = 'badge ' + (on ? 'on' : 'off');
  $('btnStop').hidden = !on;
  $('btnStart').hidden = on;
}
function renderStatus(o) {
  $('status').textContent = JSON.stringify({
    player: o.username, pos: o.position, health: o.health, food: o.food,
    dimension: o.dimension, gameMode: o.gameMode, onGround: o.onGround,
    nearby: o.nearbyEntities.slice(0, 5).map((e) => `${e.username || e.name} ${e.distance}m`),
    players: o.nearbyPlayers,
  }, null, 2);
}

// ---------- session ----------
$('btnStart').addEventListener('click', async () => {
  try {
    await api('/api/session/start', { method: 'POST', body: '{}' });
    $('viewer').querySelector('.placeholder')?.remove();
  } catch (err) {
    if (err.status === 428) { refreshMsAuth(); alert('Bitte zuerst mit Microsoft anmelden.'); }
    else alert('Start fehlgeschlagen: ' + err.message);
  }
});
$('btnStop').addEventListener('click', async () => {
  await api('/api/session/stop', { method: 'POST', body: '{}' });
  $('viewer').innerHTML = '<div class="placeholder">Keine aktive Session</div>';
});

// ---------- control: keyboard ----------
const KEYMAP = { KeyW: 'forward', KeyS: 'back', KeyA: 'left', KeyD: 'right', Space: 'jump', ShiftLeft: 'sneak', ControlLeft: 'sprint' };
const held = new Set();
window.addEventListener('keydown', (e) => {
  if ($('app').hidden) return;
  if (document.activeElement && document.activeElement.tagName === 'INPUT') return;
  const k = KEYMAP[e.code];
  if (k && !held.has(k)) { held.add(k); wsSend({ type: 'control', key: k, state: true }); e.preventDefault(); }
});
window.addEventListener('keyup', (e) => {
  const k = KEYMAP[e.code];
  if (k) { held.delete(k); wsSend({ type: 'control', key: k, state: false }); }
});

// ---------- control: touch/click buttons ----------
document.querySelectorAll('.ctl[data-key]').forEach((btn) => {
  const key = btn.dataset.key;
  const down = (e) => { e.preventDefault(); btn.classList.add('active'); wsSend({ type: 'control', key, state: true }); };
  const up = (e) => { e.preventDefault(); btn.classList.remove('active'); wsSend({ type: 'control', key, state: false }); };
  btn.addEventListener('pointerdown', down);
  btn.addEventListener('pointerup', up);
  btn.addEventListener('pointerleave', up);
  btn.addEventListener('pointercancel', up);
});
$('btnUse').addEventListener('click', () => api('/api/action', { method: 'POST', body: JSON.stringify({ type: 'useItem' }) }).catch(() => {}));

// ---------- look: drag in viewer ----------
(() => {
  const v = $('viewer');
  let dragging = false, lx = 0, ly = 0;
  v.addEventListener('pointerdown', (e) => { dragging = true; lx = e.clientX; ly = e.clientY; });
  window.addEventListener('pointermove', (e) => {
    if (!dragging) return;
    const dYaw = (e.clientX - lx) * 0.01, dPitch = (e.clientY - ly) * 0.01;
    lx = e.clientX; ly = e.clientY;
    wsSend({ type: 'look', dYaw, dPitch });
  });
  window.addEventListener('pointerup', () => { dragging = false; });
})();

// ---------- chat ----------
$('chatForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const message = $('chatInput').value.trim();
  if (!message) return;
  $('chatInput').value = '';
  try {
    const r = await api('/api/chat', { method: 'POST', body: JSON.stringify({ message }) });
    if (r.approvalRequired) alert('Gefährlicher Befehl – wartet auf deine Freigabe unten.');
  } catch (err) { alert('Fehler: ' + err.message); }
  loadApprovals();
});

// ---------- approvals ----------
async function loadApprovals() {
  const list = await api('/api/approvals').catch(() => []);
  const el = $('approvals');
  el.innerHTML = list.map((a) => `
    <div class="approval">
      <div><strong>Freigabe nötig</strong> (${a.requestedBy})</div>
      <div class="muted small">${escapeHtml(a.message)}</div>
      <div style="margin-top:8px">
        <button data-approve="${a.id}">Freigeben</button>
        <button class="ghost" data-deny="${a.id}">Ablehnen</button>
      </div>
    </div>`).join('');
  el.querySelectorAll('[data-approve]').forEach((b) => b.onclick = () => resolveApproval(b.dataset.approve, true));
  el.querySelectorAll('[data-deny]').forEach((b) => b.onclick = () => resolveApproval(b.dataset.deny, false));
}
async function resolveApproval(id, approve) {
  await api('/api/approvals/resolve', { method: 'POST', body: JSON.stringify({ id, approve }) });
  loadApprovals();
}

// ---------- tests ----------
async function loadTests() {
  const { available, recent } = await api('/api/tests');
  $('testList').innerHTML = available.map((t) => `
    <div class="test-item">
      <button data-test="${t.name}">Run</button>
      <span><strong>${t.name}</strong> — <span class="muted">${escapeHtml(t.description)}</span></span>
    </div>`).join('');
  $('testList').querySelectorAll('[data-test]').forEach((b) => b.onclick = async () => {
    b.disabled = true;
    try { await api('/api/tests/run', { method: 'POST', body: JSON.stringify({ name: b.dataset.test }) }); } catch (e) { alert(e.message); }
    b.disabled = false; loadTests();
  });
  renderResults(recent);
}
function renderResults(rows) {
  $('testResults').innerHTML = rows.map((r) => `
    <div class="result"><span class="${r.status}">${r.status.toUpperCase()}</span> <strong>${r.name}</strong>
      <div class="muted small">erwartet: ${escapeHtml(r.expected || '')} · tatsächlich: ${escapeHtml(r.actual || '')}</div></div>`).join('') || '<span class="muted">noch keine</span>';
}
$('btnRunAll').addEventListener('click', async () => {
  $('btnRunAll').disabled = true;
  try { await api('/api/tests/run', { method: 'POST', body: JSON.stringify({ name: 'all' }) }); } catch (e) { alert(e.message); }
  $('btnRunAll').disabled = false; loadTests();
});

// ---------- audit ----------
async function loadAudit() {
  const rows = await api('/api/audit').catch(() => []);
  $('audit').innerHTML = rows.slice(0, 60).map((r) => `
    <div class="audit-row"><span class="muted">${new Date(r.ts).toLocaleTimeString()}</span>
      <span class="${r.result === 'ok' ? 'pass' : r.result === 'denied' ? 'skip' : 'error'}">${r.result}</span>
      <strong>${r.actor}</strong> ${escapeHtml(r.action)} ${r.detail ? '· ' + escapeHtml(String(r.detail)).slice(0, 50) : ''}</div>`).join('');
}

function escapeHtml(s) { return String(s).replace(/[<>&"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' }[c])); }

// ---------- boot ----------
async function boot() {
  showApp(true);
  connectWs();
  await refreshMsAuth();
  if (!msPoll) msPoll = setInterval(refreshMsAuth, 4000);
  await loadTests(); await loadAudit(); await loadApprovals();
  setInterval(loadAudit, 5000);
}
if (token) { boot().catch(() => logout()); } else { showApp(false); }
