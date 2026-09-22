// PayFlow web client. A plain ES module with no build step: it talks to the Spring Boot API under /api
// (same origin locally, proxied through vercel.json in production).

const $ = (selector, root = document) => root.querySelector(selector);

const DEMO = { email: 'demo@payflow.uz', password: 'demo12345' };
const HISTORY_PAGE_SIZE = 12;
const CURRENCY_LABEL = { UZS: 'сум', USD: '$' };

const state = {
  token: loadToken(),
  user: null,
  accounts: [],
  historyPage: 0,
  chartCurrency: 'UZS',
  selectedOperationId: null,
  // Kept between attempts of the same transfer so that a retry after a network error reuses the key.
  pendingTransfer: null,
};

/* ------------------------------------------------------------------ API */

class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || `Сервер ответил ошибкой ${status}`);
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.errors || {};
  }
}

class NetworkError extends Error {
  constructor() {
    super('Нет связи с сервером');
  }
}

async function api(path, { method = 'GET', body, headers = {} } = {}) {
  let response;
  try {
    response = await fetch(path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(state.token ? { Authorization: `Bearer ${state.token}` } : {}),
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new NetworkError();
  }

  let data = null;
  const text = await response.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }

  if (response.status === 401 && state.token) {
    logout('Сессия закончилась. Войдите снова.');
  }
  if (!response.ok) {
    throw new ApiError(response.status, data);
  }
  return { data, headers: response.headers, status: response.status };
}

/* ------------------------------------------------------------------ Token */

let memoryToken = null;

function loadToken() {
  try { return localStorage.getItem('payflow.token'); } catch { return memoryToken; }
}

function saveToken(token) {
  memoryToken = token;
  try {
    if (token) localStorage.setItem('payflow.token', token);
    else localStorage.removeItem('payflow.token');
  } catch { /* private mode: the in-memory copy is enough for this tab */ }
}

/* ------------------------------------------------------------------ Formatting */

const moneyFormat = new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dayFormat = new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' });
const timeFormat = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });
const fullDateFormat = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeStyle: 'short' });
const monthFormat = new Intl.DateTimeFormat('ru-RU', { month: 'short' });

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function money(value, currency) {
  return `${moneyFormat.format(Number(value))} ${CURRENCY_LABEL[currency] ?? currency}`;
}

function balanceHtml(value, currency) {
  const [whole, fraction] = moneyFormat.format(Number(value)).split(',');
  return `${whole}<small>,${fraction} ${CURRENCY_LABEL[currency] ?? currency}</small>`;
}

function shortAmount(value) {
  const n = Number(value);
  const one = { maximumFractionDigits: 1 };
  if (n >= 1e6) return `${(n / 1e6).toLocaleString('ru-RU', one)} млн`;
  if (n >= 1e3) return `${(n / 1e3).toLocaleString('ru-RU', one)} тыс`;
  return n.toLocaleString('ru-RU', one);
}

/** 20206 000 1234 5678 9012 — balance account, currency code (highlighted), unique part. */
function accountNumberHtml(number) {
  return `${number.slice(0, 5)} <span class="acc-no__ccy">${number.slice(5, 8)}</span> `
    + `${number.slice(8, 12)} ${number.slice(12, 16)} ${number.slice(16)}`;
}

function maskedNumber(number) {
  return number ? `${number.slice(0, 5)} ${number.slice(5, 8)} ···· ${number.slice(16)}` : '—';
}

/** Accepts "250 000,50" as well as "250000.50"; returns a string the API understands, or null. */
function parseAmount(raw) {
  const normalized = String(raw).replace(/[\s  ]/g, '').replace(',', '.');
  return /^\d+(\.\d{1,2})?$/.test(normalized) && Number(normalized) > 0 ? normalized : null;
}

function newIdempotencyKey() {
  if (crypto.randomUUID) return crypto.randomUUID();
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, '0')).join('');
}

/* ------------------------------------------------------------------ Screens */

function showAuth(message) {
  $('#app-screen').hidden = true;
  $('#auth-screen').hidden = false;
  document.title = 'PayFlow — вход';
  setError($('#login-form'), message || '');
}

async function showWallet() {
  $('#auth-screen').hidden = true;
  $('#app-screen').hidden = false;
  const { data: me } = await api('/api/me');
  state.user = me;
  $('#user-name').textContent = me.fullName;
  document.title = 'PayFlow — кошелёк';
  await refresh();
}

function logout(message) {
  clearReceipt();
  state.token = null;
  state.user = null;
  saveToken(null);
  showAuth(message);
}

async function refresh() {
  await loadAccounts();
  await Promise.all([loadHistory(), loadChart()]);
}

/* ------------------------------------------------------------------ Auth */

function setError(form, message) {
  $('.form__error', form).textContent = message;
}

function markInvalid(form, fieldErrors) {
  form.querySelectorAll('[aria-invalid]').forEach(el => el.removeAttribute('aria-invalid'));
  Object.keys(fieldErrors).forEach(name => form.elements[name]?.setAttribute('aria-invalid', 'true'));
}

function describe(error) {
  const details = Object.values(error.fieldErrors || {});
  return details.length ? details.join('. ') : error.message;
}

async function authenticate(form, path, body) {
  const button = $('button[type=submit]', form) || $('#demo-login');
  setError(form, '');
  button.disabled = true;
  try {
    const { data } = await api(path, { method: 'POST', body });
    state.token = data.accessToken;
    saveToken(data.accessToken);
    await showWallet();
  } catch (error) {
    markInvalid(form, error.fieldErrors || {});
    setError(form, describe(error));
  } finally {
    button.disabled = false;
  }
}

function selectTab(name) {
  const login = name === 'login';
  $('#tab-login').setAttribute('aria-selected', String(login));
  $('#tab-register').setAttribute('aria-selected', String(!login));
  $('#login-form').hidden = !login;
  $('#register-form').hidden = login;
}

/* ------------------------------------------------------------------ Accounts */

async function loadAccounts() {
  const { data } = await api('/api/accounts');
  state.accounts = data;
  renderAccounts();
  renderAccountSelects();
}

function renderAccounts() {
  $('#accounts').innerHTML = state.accounts.map(account => `
    <li class="account">
      <div class="account__top">
        <span class="account__name">${escapeHtml(account.name)}</span>
        <span class="account__ccy">${account.currency}</span>
      </div>
      <p class="account__balance">${balanceHtml(account.balance, account.currency)}</p>
      <p class="acc-no" title="Номер счёта">${accountNumberHtml(account.number)}</p>
      <div class="account__actions">
        <button type="button" class="btn btn--ghost btn--small" data-action="deposit" data-id="${account.id}">Пополнить</button>
        <button type="button" class="btn btn--ghost btn--small" data-action="send-from" data-id="${account.id}">Перевести</button>
      </div>
    </li>`).join('');
}

function renderAccountSelects() {
  const from = $('#transfer-from');
  const keep = from.value;
  from.innerHTML = state.accounts.map(a =>
    `<option value="${a.id}">${escapeHtml(a.name)} · ${money(a.balance, a.currency)}</option>`).join('');
  if (keep && state.accounts.some(a => String(a.id) === keep)) from.value = keep;

  const filter = $('#filter-account');
  const keepFilter = filter.value;
  filter.innerHTML = '<option value="">Все счета</option>' + state.accounts.map(a =>
    `<option value="${a.id}">${escapeHtml(a.name)} (${a.currency})</option>`).join('');
  filter.value = keepFilter;

  renderOwnAccountChips();
}

function selectedFromAccount() {
  return state.accounts.find(a => String(a.id) === $('#transfer-from').value);
}

/* ------------------------------------------------------------------ Transfer */

function renderOwnAccountChips() {
  const from = selectedFromAccount();
  const targets = state.accounts.filter(a => from && a.id !== from.id && a.currency === from.currency);
  $('#own-accounts').innerHTML = targets.length
    ? 'На свой счёт: ' + targets.map(a =>
      `<button type="button" class="chip" data-number="${a.number}">${escapeHtml(a.name)}</button>`).join('')
    : '';
}

let lookupTimer;
let lookupSeq = 0;

function onRecipientInput() {
  const digits = $('#transfer-to').value.replace(/\D/g, '');
  const out = $('#recipient');
  clearTimeout(lookupTimer);
  out.className = 'recipient';
  out.textContent = '';
  if (digits.length !== 20) return;

  const seq = ++lookupSeq;
  lookupTimer = setTimeout(async () => {
    try {
      const { data } = await api(`/api/accounts/lookup?number=${digits}`);
      if (seq !== lookupSeq) return; // a newer number was typed meanwhile
      const from = selectedFromAccount();
      const own = state.accounts.find(a => a.number === data.number);
      if (from && data.currency !== from.currency) {
        out.className = 'recipient recipient--error';
        out.textContent = `Это счёт в ${data.currency}, а списание в ${from.currency}. Выберите счёт в той же валюте.`;
      } else {
        out.className = 'recipient recipient--ok';
        out.textContent = own ? `Ваш счёт «${own.name}»` : `Получатель: ${data.ownerName} · ${data.currency}`;
      }
    } catch (error) {
      if (seq !== lookupSeq) return;
      out.className = 'recipient recipient--error';
      out.textContent = error.status === 404 ? 'Счёт с таким номером не найден' : error.message;
    }
  }, 250);
}

async function onTransferSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $('button[type=submit]', form);
  setError(form, '');
  markInvalid(form, {});

  const amount = parseAmount(form.elements.amount.value);
  const toAccountNumber = form.elements.toAccountNumber.value.replace(/\D/g, '');
  if (toAccountNumber.length !== 20) {
    markInvalid(form, { toAccountNumber: true });
    setError(form, 'Номер счёта получателя — 20 цифр');
    return;
  }
  if (!amount) {
    markInvalid(form, { amount: true });
    setError(form, 'Введите сумму, например 150000 или 99,50');
    return;
  }

  const body = {
    fromAccountId: Number(form.elements.fromAccountId.value),
    toAccountNumber,
    amount,
    description: form.elements.description.value.trim() || null,
  };
  const signature = JSON.stringify(body);
  if (!state.pendingTransfer || state.pendingTransfer.signature !== signature) {
    state.pendingTransfer = { signature, key: newIdempotencyKey() };
  }

  button.disabled = true;
  try {
    const { data } = await api('/api/transfers', {
      method: 'POST', body, headers: { 'Idempotency-Key': state.pendingTransfer.key },
    });
    state.pendingTransfer = null;
    form.reset();
    $('#recipient').textContent = '';
    await refresh();
    $('#transfer-from').value = String(body.fromAccountId);
    renderOwnAccountChips();
    printReceipt(data, true);
  } catch (error) {
    if (error instanceof NetworkError) {
      setError(form, 'Нет связи с сервером. Нажмите «Перевести» ещё раз — повторный запрос не спишет деньги дважды.');
    } else {
      state.pendingTransfer = null;
      markInvalid(form, error.fieldErrors || {});
      setError(form, describe(error));
    }
  } finally {
    button.disabled = false;
  }
}

/* ------------------------------------------------------------------ Dialogs */

let depositAccountId = null;

function openDeposit(accountId) {
  const account = state.accounts.find(a => a.id === accountId);
  depositAccountId = accountId;
  const form = $('#deposit-form');
  form.reset();
  setError(form, '');
  $('#deposit-account').textContent = `${account.name} · ${account.currency} · не больше ${account.currency === 'UZS' ? '50 000 000 сум' : '5 000 $'} за раз`;
  $('#deposit-dialog').showModal();
}

async function onDepositSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const amount = parseAmount(form.elements.amount.value);
  if (!amount) {
    setError(form, 'Введите сумму, например 1000000');
    return;
  }
  const button = $('button[type=submit]', form);
  button.disabled = true;
  try {
    const { data } = await api(`/api/accounts/${depositAccountId}/deposits`, {
      method: 'POST', body: { amount, description: form.elements.description.value.trim() || null },
    });
    $('#deposit-dialog').close();
    await refresh();
    printReceipt(data, true);
  } catch (error) {
    setError(form, describe(error));
  } finally {
    button.disabled = false;
  }
}

async function onAccountSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = $('button[type=submit]', form);
  button.disabled = true;
  try {
    await api('/api/accounts', {
      method: 'POST', body: { currency: form.elements.currency.value, name: form.elements.name.value.trim() },
    });
    $('#account-dialog').close();
    await loadAccounts();
  } catch (error) {
    setError(form, describe(error));
  } finally {
    button.disabled = false;
  }
}

/* ------------------------------------------------------------------ History */

async function loadHistory() {
  const params = new URLSearchParams({ page: state.historyPage, size: HISTORY_PAGE_SIZE });
  const accountId = $('#filter-account').value;
  const type = $('#filter-type').value;
  if (accountId) params.set('accountId', accountId);
  if (type) params.set('type', type);

  const { data } = await api(`/api/operations?${params}`);
  state.history = data.items;
  renderHistory(data);
}

function operationTitle(op) {
  if (op.type === 'DEPOSIT') return { title: 'Пополнение', sub: op.description || maskedNumber(op.accountNumber) };
  if (op.direction === 'INTERNAL') return { title: 'Между своими счетами', sub: op.description || 'Перевод' };
  return {
    title: op.counterpartyName || maskedNumber(op.counterpartyNumber),
    sub: op.description || (op.direction === 'IN' ? 'Входящий перевод' : 'Перевод'),
  };
}

function renderHistory(page) {
  const list = $('#history');
  if (!page.items.length) {
    list.innerHTML = '<li class="history__empty">Здесь пока пусто. Пополните счёт или сделайте перевод — операция появится в этом списке.</li>';
    $('#pager').innerHTML = '';
    return;
  }

  list.innerHTML = page.items.map(op => {
    const { title, sub } = operationTitle(op);
    const date = new Date(op.createdAt);
    const sign = op.direction === 'IN' ? '+' : op.direction === 'OUT' ? '−' : '';
    const amountClass = op.direction === 'IN' ? 'op__amount--in' : op.direction === 'INTERNAL' ? 'op__amount--internal' : '';
    return `
      <li class="history__item">
        <button type="button" class="op" data-id="${op.id}" aria-current="${op.id === state.selectedOperationId}">
          <span class="op__date">${dayFormat.format(date).replace('.', '')}<br>${timeFormat.format(date)}</span>
          <span><span class="op__title">${escapeHtml(title)}</span><br><span class="op__sub">${escapeHtml(sub)}</span></span>
          <span class="op__amount ${amountClass}">${sign}${money(op.amount, op.currency)}</span>
        </button>
      </li>`;
  }).join('');

  $('#pager').innerHTML = page.totalPages > 1 ? `
    <button type="button" class="btn btn--ghost btn--small" data-page="${page.page - 1}" ${page.page === 0 ? 'disabled' : ''}>Новее</button>
    <span class="muted">Страница ${page.page + 1} из ${page.totalPages}</span>
    <button type="button" class="btn btn--ghost btn--small" data-page="${page.page + 1}" ${page.page + 1 >= page.totalPages ? 'disabled' : ''}>Старше</button>` : '';
}

/* ------------------------------------------------------------------ Receipt */

function receiptTitle(op) {
  if (op.type === 'DEPOSIT') return 'Счёт пополнен';
  if (op.direction === 'INTERNAL') return 'Перевод между счетами';
  return op.direction === 'IN' ? 'Перевод получен' : 'Перевод отправлен';
}

function printReceipt(op, animate) {
  state.selectedOperationId = op.id;
  const rows = [];
  if (op.type === 'DEPOSIT') {
    rows.push(['На счёт', maskedNumber(op.accountNumber)]);
  } else if (op.direction === 'INTERNAL') {
    rows.push(['Со счёта', maskedNumber(op.accountNumber)], ['На счёт', maskedNumber(op.counterpartyNumber)]);
  } else if (op.direction === 'IN') {
    rows.push(['Отправитель', op.counterpartyName], ['Со счёта', maskedNumber(op.counterpartyNumber)],
      ['На счёт', maskedNumber(op.accountNumber)]);
  } else {
    rows.push(['Со счёта', maskedNumber(op.accountNumber)], ['Получатель', op.counterpartyName],
      ['На счёт', maskedNumber(op.counterpartyNumber)]);
  }
  if (op.description) rows.push(['Комментарий', op.description]);

  $('#printer').innerHTML = `
    <figure class="receipt${animate ? ' receipt--printing' : ''}">
      <div class="receipt__paper">
        <p class="receipt__head">PAYFLOW · ЧЕК</p>
        <p class="receipt__title">${receiptTitle(op)}</p>
        <p class="receipt__meta">${fullDateFormat.format(new Date(op.createdAt))}<br>Операция № ${String(op.id).padStart(8, '0')}</p>
        <dl class="receipt__rows">
          ${rows.map(([k, v]) => `<div><dt>${k}</dt><dd>${escapeHtml(v)}</dd></div>`).join('')}
        </dl>
        <p class="receipt__total"><span>Сумма</span><span>${money(op.amount, op.currency)}</span></p>
        <p class="stamp">Исполнено</p>
      </div>
    </figure>
    <button type="button" class="btn btn--ghost btn--small printer__clear" id="clear-receipt">Убрать чек</button>`;

  document.querySelectorAll('.op').forEach(el => el.setAttribute('aria-current', String(Number(el.dataset.id) === op.id)));

  // The printer sits at the top of the side column. On wide screens that column is sticky and scrolls on
  // its own, so bring its top into view; on phones the column is part of the page, so scroll the page.
  const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
  const side = $('.wallet__side');
  if (getComputedStyle(side).position === 'sticky') side.scrollTo({ top: 0, behavior });
  else $('#printer').scrollIntoView({ block: 'nearest', behavior });
}

function clearReceipt() {
  state.selectedOperationId = null;
  $('#printer').innerHTML = '<p class="printer__hint">Нажмите на операцию в истории, чтобы увидеть её чек.</p>';
  document.querySelectorAll('.op').forEach(el => el.setAttribute('aria-current', 'false'));
}

/* ------------------------------------------------------------------ Chart */

async function loadChart() {
  const { data } = await api(`/api/stats/monthly?currency=${state.chartCurrency}&months=6`);
  renderChart(data, state.chartCurrency);
}

function renderChart(stats, currency) {
  const chart = $('#chart');
  const values = stats.flatMap(s => [Number(s.income), Number(s.expense)]);
  const max = Math.max(...values);
  if (max === 0) {
    chart.innerHTML = `<p class="history__empty">За последние полгода в ${currency} не было ни прихода, ни расхода.</p>`;
    return;
  }

  const width = 640;
  const height = 210;
  const top = 26;
  const bottom = 30;
  const plot = height - top - bottom;
  const slot = width / stats.length;
  const bar = Math.min(28, slot * 0.26);

  const bars = stats.map((s, i) => {
    const center = slot * i + slot / 2;
    const month = monthFormat.format(new Date(`${s.month}-01T12:00:00`)).replace('.', '');
    const column = (value, x, cls, label) => {
      const h = Math.max(Number(value) > 0 ? 2 : 0, (Number(value) / max) * plot);
      return `<rect x="${x}" y="${top + plot - h}" width="${bar}" height="${h}" rx="4" class="${cls}">
        <title>${month}: ${label} ${money(value, currency)}</title></rect>`;
    };
    return `
      ${column(s.income, center - bar - 2, 'bar-in', 'приход')}
      ${column(s.expense, center + 2, 'bar-out', 'расход')}
      <text x="${center}" y="${height - 8}" text-anchor="middle" class="axis">${month}</text>`;
  }).join('');

  chart.innerHTML = `
    <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="Приход и расход по месяцам в ${currency}">
      <line x1="0" x2="${width}" y1="${top}" y2="${top}" class="grid"/>
      <text x="0" y="${top - 8}" class="axis">${shortAmount(max)} ${CURRENCY_LABEL[currency]}</text>
      <line x1="0" x2="${width}" y1="${top + plot}" y2="${top + plot}" class="base"/>
      ${bars}
    </svg>`;
}

/* ------------------------------------------------------------------ Wiring */

function wire() {
  $('#tab-login').addEventListener('click', () => selectTab('login'));
  $('#tab-register').addEventListener('click', () => selectTab('register'));

  $('#login-form').addEventListener('submit', event => {
    event.preventDefault();
    const f = event.currentTarget;
    authenticate(f, '/api/auth/login', { email: f.elements.email.value, password: f.elements.password.value });
  });
  $('#register-form').addEventListener('submit', event => {
    event.preventDefault();
    const f = event.currentTarget;
    authenticate(f, '/api/auth/register', {
      fullName: f.elements.fullName.value, email: f.elements.email.value, password: f.elements.password.value,
    });
  });
  $('#demo-login').addEventListener('click', () => {
    selectTab('login');
    authenticate($('#login-form'), '/api/auth/login', DEMO);
  });
  $('#logout').addEventListener('click', () => logout());

  $('#accounts').addEventListener('click', event => {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const id = Number(button.dataset.id);
    if (button.dataset.action === 'deposit') {
      openDeposit(id);
    } else {
      $('#transfer-from').value = String(id);
      renderOwnAccountChips();
      onRecipientInput();
      $('#transfer-to').focus();
    }
  });

  $('#open-account').addEventListener('click', () => {
    const form = $('#account-form');
    form.reset();
    setError(form, '');
    $('#account-dialog').showModal();
  });
  document.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => b.closest('dialog').close()));
  $('#deposit-form').addEventListener('submit', onDepositSubmit);
  $('#account-form').addEventListener('submit', onAccountSubmit);

  $('#transfer-form').addEventListener('submit', onTransferSubmit);
  $('#transfer-to').addEventListener('input', onRecipientInput);
  $('#transfer-from').addEventListener('change', () => { renderOwnAccountChips(); onRecipientInput(); });
  $('#own-accounts').addEventListener('click', event => {
    const chip = event.target.closest('.chip');
    if (!chip) return;
    $('#transfer-to').value = chip.dataset.number;
    onRecipientInput();
    $('#transfer-amount').focus();
  });

  $('#history').addEventListener('click', event => {
    const row = event.target.closest('.op');
    if (!row) return;
    const op = state.history.find(o => o.id === Number(row.dataset.id));
    if (op) printReceipt(op, true);
  });
  $('#printer').addEventListener('click', event => {
    if (event.target.closest('#clear-receipt')) clearReceipt();
  });
  $('#pager').addEventListener('click', event => {
    const button = event.target.closest('button[data-page]');
    if (!button) return;
    state.historyPage = Number(button.dataset.page);
    loadHistory();
  });
  ['#filter-account', '#filter-type'].forEach(sel => $(sel).addEventListener('change', () => {
    state.historyPage = 0;
    loadHistory();
  }));

  document.querySelectorAll('.segmented button').forEach(button => button.addEventListener('click', () => {
    state.chartCurrency = button.dataset.currency;
    document.querySelectorAll('.segmented button').forEach(b => b.setAttribute('aria-pressed', String(b === button)));
    loadChart();
  }));
}

wire();
const params = new URLSearchParams(location.search);
if (params.has('demo')) {
  // Shareable link straight into the demo wallet: /?demo
  history.replaceState(null, '', location.pathname);
  showAuth();
  authenticate($('#login-form'), '/api/auth/login', DEMO);
} else if (state.token) {
  showWallet().catch(error => { if (error.status !== 401) logout(error.message); });
} else {
  showAuth();
}
