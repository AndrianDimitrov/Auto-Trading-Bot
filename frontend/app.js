const API = (path) => `http://localhost:8080${path}`;
let chart;
const el = (id) => document.getElementById(id);
const fmt = (n, d=2) => Number(n).toFixed(d);

function showError(msg) {
  const box = document.getElementById('statusBox');
  box.textContent = `${msg}`;
}

async function fetchJSON(url, opts) {
  try {
    const res = await fetch(url, opts);
    if (!res.ok) {
      let errText;
      try {
        const j = await res.json();
        errText = j.error || JSON.stringify(j);
      } catch {
        errText = await res.text();
      }
      throw new Error(errText || `HTTP ${res.status}`);
    }
    return res.json();
  } catch (e) {
    showError(`API error: ${e.message}`);
    throw e;
  }
}

async function start() {
  const mode = el('mode').value;
  const symbol = (el('symbol').value || 'BTCUSDT').trim();
  const interval = el('interval').value.trim();
  await fetchJSON(API(`/api/bot/start?mode=${encodeURIComponent(mode)}&symbol=${encodeURIComponent(symbol)}&interval=${encodeURIComponent(interval)}`), { method: 'POST' });
  await refreshAll();
}

async function pause()  { await fetchJSON(API('/api/bot/pause'),  { method:'POST' }); await loadStatus(); }
async function resume() { await fetchJSON(API('/api/bot/resume'), { method:'POST' }); await loadStatus(); }
async function stop()   { await fetchJSON(API('/api/bot/stop'),   { method:'POST' }); await refreshAll(); }

async function loadStatus() {
  const s = await fetchJSON(API('/api/bot/status'));
  el('statusBox').textContent = JSON.stringify(s, null, 2);
}
async function loadPortfolio() {
  const p = await fetchJSON(API('/api/portfolio'));
  el('portfolioBox').textContent = JSON.stringify(p, null, 2);
}
async function loadEquity() {
  const data = await fetchJSON(API('/api/equity'));
  const labels = data.map(p => new Date(p.ts).toLocaleString());
  const values = data.map(p => Number(p.equity));
  if (!chart) {
    const ctx = el('equityChart').getContext('2d');
    chart = new Chart(ctx, {
      type: 'line',
      data: { labels, datasets: [{ label: 'Equity', data: values }] },
      options: {
        responsive: true, animation: false,
        scales: { x: { ticks: { maxTicksLimit: 8 } }, y: { beginAtZero: false } }
      }
    });
  } else {
    chart.data.labels = labels;
    chart.data.datasets[0].data = values;
    chart.update();
  }
}
async function loadTrades() {
  const rows = await fetchJSON(API('/api/trades'));
  const tbody = el('tradesTable').querySelector('tbody');
  tbody.innerHTML = '';
  rows.forEach(r => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${new Date(r.ts).toLocaleString()}</td>
      <td>${r.symbol}</td>
      <td>${r.side}</td>
      <td>${fmt(r.qty, 8)}</td>
      <td>${fmt(r.price, 2)}</td>
      <td>${r.pnl != null ? fmt(r.pnl, 2) : ''}</td>`;
    tbody.appendChild(tr);
  });
}

async function refreshAll() {
  try {
    await Promise.all([loadStatus(), loadPortfolio(), loadEquity(), loadTrades()]);
  } catch (e) {
    console.warn("Refresh failed:", e);
  }
}

function wireUI() {
  document.getElementById('startBtn').onclick = start;
  document.getElementById('pauseBtn').onclick = pause;
  document.getElementById('resumeBtn').onclick = resume;
  document.getElementById('stopBtn').onclick = stop;
  refreshAll();
  setInterval(refreshAll, 5000);
}

document.addEventListener('DOMContentLoaded', wireUI);
