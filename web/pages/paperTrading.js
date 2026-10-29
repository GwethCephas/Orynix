import {
  createPaperTradingAccount,
  listPaperTradingAccounts,
  updatePaperTradingAccount,
  archivePaperTradingAccount,
  getPaperTradingAccountSummary,
  refreshPaperTradingAccountSummary,
  submitPaperOrder,
  listPaperOrders,
  listOpenPaperPositions,
  listClosedPaperPositions,
  closePaperPosition,
  listPaperLedgerEntries,
  getCandles,
  ApiError,
} from "../api.js";
import { getCurrentSymbol, withCache } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { metricCardHtml } from "../components/metricCard.js";
import {
  escapeHtml,
  formatNumber,
  formatDateTime,
  formatPrice,
  isForexSymbol,
  isValidSymbol,
  humanizeEnum,
  deriveAssetHeader,
} from "../format.js";

// Module-level, not state.js: this only needs to survive navigating away from and back to this
// page within the same session (module scope is a singleton), never a full router re-render on
// every switch - see the mid-form-wipe problem that putting it in state.js's notify()-driven
// store would cause.
let persistedAccountId = null;

const PRICE_STATUS_COLOR = { OK: "positive", STALE: "neutral", UNAVAILABLE: "negative" };
const LEDGER_TYPE_LABEL = { DEPOSIT: "Initial deposit", ORDER_FILL: "Order fill" };
const PAGE_SIZE = 20;

function formatBalance(value, currency) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency: currency || "USD" }).format(Number(value));
  } catch {
    return `${formatNumber(value, 2)} ${currency || ""}`.trim();
  }
}

// Mirrors Instrument.requiredAccountCurrency (CLAUDE.md Section 19): "USD" for a stock, the
// pair's own quote currency for forex. A client-side guess only - InstrumentResolver on the
// backend remains the actual authority; this just turns a guaranteed round-trip 400 into inline
// field feedback before submission.
function requiredAccountCurrency(symbol) {
  if (isForexSymbol(symbol)) {
    return (symbol.split("/")[1] || "").toUpperCase();
  }
  return "USD";
}

function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `pt-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function describePaperError(error) {
  const status = error instanceof ApiError ? error.status : null;
  if (status === 0) return "Could not reach the backend. Is it running?";
  return error?.message || "Unexpected error.";
}

function paginationControlsHtml(pageState, totalPages) {
  const total = Math.max(totalPages, 1);
  return `
    <div class="pt-pagination">
      <button type="button" class="refresh-btn pt-page-prev" ${pageState.page <= 0 ? "disabled" : ""}>&larr; Prev</button>
      <span class="section-status">Page ${pageState.page + 1} of ${total}</span>
      <button type="button" class="refresh-btn pt-page-next" ${pageState.page + 1 >= total ? "disabled" : ""}>Next &rarr;</button>
    </div>`;
}

function wirePagination(containerEl, pageState, totalPages, onChange) {
  containerEl.querySelector(".pt-page-prev")?.addEventListener("click", () => {
    if (pageState.page > 0) {
      pageState.page -= 1;
      onChange();
    }
  });
  containerEl.querySelector(".pt-page-next")?.addEventListener("click", () => {
    if (pageState.page + 1 < totalPages) {
      pageState.page += 1;
      onChange();
    }
  });
}

export function mount(container) {
  let alive = true;

  let accounts = [];
  let selectedAccountId = persistedAccountId;
  let showCreateForm = false;
  let editingAccount = null;
  let latestOpenPositions = null;
  let orderSide = "BUY";
  let orderBusy = false;
  let orderIdempotencyKey = null;

  const ordersPage = { page: 0 };
  const closedPage = { page: 0 };
  const ledgerPage = { page: 0 };

  const accountsSection = { value: createSectionState() };
  const summarySection = { value: createSectionState() };
  const positionsSection = { value: createSectionState() };
  const ordersSection = { value: createSectionState() };
  const closedSection = { value: createSectionState() };
  const ledgerSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page paper-trading-page">
      <section class="card pt-banner">
        <h1>Paper Trading</h1>
        <p>
          <strong>Simulated trading with virtual funds.</strong> Orders here fill against the latest
          <em>available</em> daily close - which may be from a prior trading day - and never place a
          real broker order. This is entirely separate from your manually-entered trading account
          profiles and from backtesting.
        </p>
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Account</h2>
          <button type="button" class="refresh-btn" id="ptAccountsRefreshBtn">Refresh</button>
        </div>
        <div id="ptAccountSwitcher"></div>
        <div id="ptAccountForm"></div>
      </section>

      <section class="card" id="ptOverviewCard" hidden>
        <div class="section-header">
          <h2>Overview</h2>
          <div class="pt-header-actions">
            <span class="section-asof" id="ptSummaryNote"></span>
            <button type="button" class="refresh-btn" id="ptSummaryRefreshBtn">Refresh</button>
          </div>
        </div>
        <div id="ptOverview"></div>
      </section>

      <section class="card" id="ptOrderCard" hidden>
        <h2>Place order</h2>
        <div id="ptOrderForm"></div>
        <div id="ptOrderResult"></div>
      </section>

      <section class="card" id="ptPositionsCard" hidden>
        <div class="section-header">
          <h2>Open positions</h2>
          <button type="button" class="refresh-btn" id="ptPositionsRefreshBtn">Refresh</button>
        </div>
        <div id="ptPositions"></div>
      </section>

      <section class="card" id="ptOrdersCard" hidden>
        <div class="section-header">
          <h2>Order history</h2>
          <button type="button" class="refresh-btn" id="ptOrdersRefreshBtn">Refresh</button>
        </div>
        <div id="ptOrders"></div>
      </section>

      <section class="card" id="ptClosedCard" hidden>
        <div class="section-header">
          <h2>Closed positions</h2>
          <button type="button" class="refresh-btn" id="ptClosedRefreshBtn">Refresh</button>
        </div>
        <div id="ptClosed"></div>
      </section>

      <section class="card" id="ptLedgerCard" hidden>
        <div class="section-header">
          <h2>Account activity (ledger)</h2>
          <button type="button" class="refresh-btn" id="ptLedgerRefreshBtn">Refresh</button>
        </div>
        <div id="ptLedger"></div>
      </section>
    </div>`;

  const switcherEl = container.querySelector("#ptAccountSwitcher");
  const accountFormEl = container.querySelector("#ptAccountForm");
  const overviewCard = container.querySelector("#ptOverviewCard");
  const overviewEl = container.querySelector("#ptOverview");
  const summaryNoteEl = container.querySelector("#ptSummaryNote");
  const summaryRefreshBtn = container.querySelector("#ptSummaryRefreshBtn");
  const orderCard = container.querySelector("#ptOrderCard");
  const orderFormEl = container.querySelector("#ptOrderForm");
  const orderResultEl = container.querySelector("#ptOrderResult");
  const positionsCard = container.querySelector("#ptPositionsCard");
  const positionsEl = container.querySelector("#ptPositions");
  const ordersCard = container.querySelector("#ptOrdersCard");
  const ordersEl = container.querySelector("#ptOrders");
  const closedCard = container.querySelector("#ptClosedCard");
  const closedEl = container.querySelector("#ptClosed");
  const ledgerCard = container.querySelector("#ptLedgerCard");
  const ledgerEl = container.querySelector("#ptLedger");

  function currentAccount() {
    return accounts.find((a) => a.id === selectedAccountId) || null;
  }

  function renderAccountDependentVisibility() {
    const hasAccount = !!currentAccount();
    overviewCard.hidden = !hasAccount;
    orderCard.hidden = !hasAccount;
    positionsCard.hidden = !hasAccount;
    ordersCard.hidden = !hasAccount;
    closedCard.hidden = !hasAccount;
    ledgerCard.hidden = !hasAccount;
  }

  // ---------- Accounts: list, switch, create, rename, archive ----------

  async function loadAccounts(preferredId) {
    await loadInto(accountsSection, () => onAccountsLoaded(preferredId), () => listPaperTradingAccounts());
  }

  function onAccountsLoaded(preferredId) {
    if (!alive) return;
    renderStateBlock(switcherEl, accountsSection.value, {
      loadingLines: 2,
      onRetry: () => loadAccounts(selectedAccountId),
      renderContent: (list) => {
        accounts = list;
        const candidate = preferredId && accounts.some((a) => a.id === preferredId) ? preferredId : selectedAccountId;
        selectedAccountId = accounts.some((a) => a.id === candidate) ? candidate : accounts[0]?.id || null;
        persistedAccountId = selectedAccountId;
        renderSwitcherContent();
        renderAccountDependentVisibility();
        if (selectedAccountId) {
          loadSummary();
          loadPositions();
          loadOrders();
          loadClosed();
          loadLedger();
        }
        renderOrderForm();
      },
    });
  }

  function renderSwitcherContent() {
    if (accounts.length === 0) {
      switcherEl.innerHTML = `<p class="section-status">No paper trading accounts yet - create one below to get started.</p>`;
    } else {
      switcherEl.innerHTML = `
        <div class="pt-switcher-row">
          <select id="ptAccountSelect" aria-label="Active paper trading account">
            ${accounts
              .map((a) => `<option value="${a.id}" ${a.id === selectedAccountId ? "selected" : ""}>${escapeHtml(a.name)} &middot; ${escapeHtml(a.baseCurrency)}</option>`)
              .join("")}
          </select>
          <button type="button" class="auth-secondary-btn" id="ptRenameBtn">Rename</button>
          <button type="button" class="auth-secondary-btn" id="ptArchiveBtn">Archive</button>
          <button type="button" class="auth-secondary-btn" id="ptNewAccountBtn">New account</button>
        </div>
        <span class="profile-badge">Virtual funds &middot; not connected to any broker</span>`;
      const select = switcherEl.querySelector("#ptAccountSelect");
      select.addEventListener("change", () => selectAccount(select.value));
      switcherEl.querySelector("#ptRenameBtn").addEventListener("click", () => {
        editingAccount = currentAccount();
        showCreateForm = false;
        renderAccountForm();
      });
      switcherEl.querySelector("#ptArchiveBtn").addEventListener("click", () => onArchive(currentAccount()));
      switcherEl.querySelector("#ptNewAccountBtn").addEventListener("click", () => {
        showCreateForm = true;
        editingAccount = null;
        renderAccountForm();
      });
    }
    renderAccountForm();
  }

  function selectAccount(id) {
    selectedAccountId = id;
    persistedAccountId = id;
    showCreateForm = false;
    editingAccount = null;
    orderIdempotencyKey = null;
    ordersPage.page = 0;
    closedPage.page = 0;
    ledgerPage.page = 0;
    renderSwitcherContent();
    renderAccountDependentVisibility();
    loadSummary();
    loadPositions();
    loadOrders();
    loadClosed();
    loadLedger();
    renderOrderForm();
  }

  function renderAccountForm() {
    if (editingAccount) {
      accountFormEl.innerHTML = renameFormHtml(editingAccount);
      wireRenameForm();
      return;
    }
    if (showCreateForm || accounts.length === 0) {
      accountFormEl.innerHTML = createFormHtml();
      wireCreateForm();
      return;
    }
    accountFormEl.innerHTML = "";
  }

  function createFormHtml() {
    return `
      <form class="backtest-form" novalidate id="ptCreateForm">
        <h3 class="backtest-subheading">${accounts.length === 0 ? "Create your first paper trading account" : "Create a new paper trading account"}</h3>
        <div class="backtest-form-grid">
          <label class="backtest-field">
            <span>Name</span>
            <input type="text" name="name" maxlength="100" autocomplete="off" placeholder="e.g. Practice account">
          </label>
          <label class="backtest-field">
            <span>Base currency</span>
            <input type="text" name="baseCurrency" maxlength="3" autocomplete="off" value="USD">
          </label>
          <label class="backtest-field">
            <span>Initial deposit (virtual)</span>
            <input type="number" name="initialDeposit" min="0" step="any" value="100000">
          </label>
          <label class="backtest-field">
            <span>Commission per order (optional)</span>
            <input type="number" name="commissionPerOrder" min="0" step="any" value="0">
          </label>
          <label class="backtest-field">
            <span>Slippage, basis points (optional)</span>
            <input type="number" name="slippageBps" min="0" step="1" value="0">
          </label>
        </div>
        <p class="backtest-validation-error" role="alert" hidden></p>
        <div class="backtest-form-actions">
          <button type="submit" class="auth-primary-btn">Create account</button>
          ${accounts.length > 0 ? `<button type="button" class="auth-secondary-btn" id="ptCreateCancelBtn">Cancel</button>` : ""}
        </div>
      </form>`;
  }

  function wireCreateForm() {
    const form = accountFormEl.querySelector("#ptCreateForm");
    const errorEl = form.querySelector(".backtest-validation-error");
    form.querySelector("#ptCreateCancelBtn")?.addEventListener("click", () => {
      showCreateForm = false;
      renderAccountForm();
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const values = {
        name: form.querySelector('[name="name"]').value.trim(),
        baseCurrency: form.querySelector('[name="baseCurrency"]').value.trim().toUpperCase(),
        initialDeposit: form.querySelector('[name="initialDeposit"]').value,
        commissionPerOrder: form.querySelector('[name="commissionPerOrder"]').value,
        slippageBps: form.querySelector('[name="slippageBps"]').value,
      };
      const errors = [];
      if (!values.name) errors.push("Enter a name for this account.");
      if (!/^[A-Z]{3}$/.test(values.baseCurrency)) errors.push("Base currency must be a 3-letter code, e.g. USD.");
      if (!(Number(values.initialDeposit) > 0)) errors.push("Initial deposit must be a positive number.");
      if (values.commissionPerOrder !== "" && !(Number(values.commissionPerOrder) >= 0)) errors.push("Commission per order must be zero or positive.");
      if (values.slippageBps !== "" && !(Number(values.slippageBps) >= 0)) errors.push("Slippage must be zero or a positive whole number.");
      if (errors.length > 0) {
        errorEl.innerHTML = errors.map((e) => escapeHtml(e)).join("<br>");
        errorEl.hidden = false;
        return;
      }
      errorEl.hidden = true;
      const submitBtn = form.querySelector('button[type="submit"]');
      submitBtn.disabled = true;
      try {
        const created = await createPaperTradingAccount({
          name: values.name,
          baseCurrency: values.baseCurrency,
          initialDeposit: Number(values.initialDeposit),
          commissionPerOrder: values.commissionPerOrder !== "" ? Number(values.commissionPerOrder) : null,
          slippageBps: values.slippageBps !== "" ? Number(values.slippageBps) : null,
        });
        if (!alive) return;
        showCreateForm = false;
        await loadAccounts(created.id);
      } catch (error) {
        if (!alive) return;
        errorEl.textContent = describePaperError(error);
        errorEl.hidden = false;
      } finally {
        if (alive) submitBtn.disabled = false;
      }
    });
  }

  function renameFormHtml(account) {
    return `
      <form class="backtest-form" novalidate id="ptRenameForm">
        <h3 class="backtest-subheading">Rename "${escapeHtml(account.name)}"</h3>
        <div class="backtest-form-grid">
          <label class="backtest-field">
            <span>Name</span>
            <input type="text" name="name" maxlength="100" value="${escapeHtml(account.name)}">
          </label>
        </div>
        <p class="backtest-validation-error" role="alert" hidden></p>
        <div class="backtest-form-actions">
          <button type="submit" class="auth-primary-btn">Save</button>
          <button type="button" class="auth-secondary-btn" id="ptRenameCancelBtn">Cancel</button>
        </div>
      </form>`;
  }

  function wireRenameForm() {
    const form = accountFormEl.querySelector("#ptRenameForm");
    const errorEl = form.querySelector(".backtest-validation-error");
    form.querySelector("#ptRenameCancelBtn").addEventListener("click", () => {
      editingAccount = null;
      renderAccountForm();
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const name = form.querySelector('[name="name"]').value.trim();
      if (!name) {
        errorEl.textContent = "Enter a name.";
        errorEl.hidden = false;
        return;
      }
      errorEl.hidden = true;
      const submitBtn = form.querySelector('button[type="submit"]');
      submitBtn.disabled = true;
      const accountId = editingAccount.id;
      try {
        await updatePaperTradingAccount(accountId, { name });
        if (!alive) return;
        editingAccount = null;
        await loadAccounts(accountId);
      } catch (error) {
        if (!alive) return;
        errorEl.textContent = describePaperError(error);
        errorEl.hidden = false;
      } finally {
        if (alive) submitBtn.disabled = false;
      }
    });
  }

  async function onArchive(account) {
    if (!account) return;
    if (
      !window.confirm(
        `Archive "${account.name}"? It will no longer be selectable for new orders. Its history is preserved, but this cannot be undone from this page.`
      )
    ) {
      return;
    }
    try {
      await archivePaperTradingAccount(account.id);
      if (!alive) return;
      if (selectedAccountId === account.id) {
        selectedAccountId = null;
        persistedAccountId = null;
      }
      await loadAccounts(selectedAccountId);
    } catch (error) {
      if (!alive) return;
      window.alert(describePaperError(error));
    }
  }

  // ---------- Overview ----------

  async function loadSummary(useRefreshEndpoint = false) {
    const accountId = selectedAccountId;
    const fetcher = useRefreshEndpoint
      ? () => refreshPaperTradingAccountSummary(accountId)
      : () => getPaperTradingAccountSummary(accountId);
    await loadInto(summarySection, () => onSummaryLoaded(accountId), fetcher);
  }

  function onSummaryLoaded(accountId) {
    if (!alive || accountId !== selectedAccountId) return;
    renderOverview();
  }

  function renderOverview() {
    if (!alive) return;
    summaryNoteEl.textContent = "Computed fresh on every load - never cached";
    renderStateBlock(overviewEl, summarySection.value, {
      loadingLines: 4,
      onRetry: () => loadSummary(),
      renderContent: (summary) => {
        const account = currentAccount();
        const currency = account?.baseCurrency || "USD";
        const openCount = latestOpenPositions ? latestOpenPositions.length : null;
        const colorFor = (value) => {
          const n = Number(value);
          if (n > 0) return "positive";
          if (n < 0) return "negative";
          return "neutral";
        };
        overviewEl.innerHTML = `
          <div class="metric-grid content-fade-in">
            ${metricCardHtml({ label: "Cash", value: formatBalance(summary.cash, currency) })}
            ${metricCardHtml({ label: "Equity", value: formatBalance(summary.equity, currency) })}
            ${metricCardHtml({ label: "Buying power", value: formatBalance(summary.buyingPower, currency) })}
            ${metricCardHtml({ label: "Open positions", value: openCount !== null ? String(openCount) : "N/A" })}
            ${metricCardHtml({ label: "Unrealized P&L (long)", value: formatBalance(summary.unrealizedPnlLong, currency), colorClass: colorFor(summary.unrealizedPnlLong) })}
            ${metricCardHtml({ label: "Unrealized P&L (short)", value: formatBalance(summary.unrealizedPnlShort, currency), colorClass: colorFor(summary.unrealizedPnlShort) })}
            ${metricCardHtml({ label: "Realized P&L (cumulative)", value: formatBalance(summary.realizedPnlCumulative, currency), colorClass: colorFor(summary.realizedPnlCumulative) })}
            ${metricCardHtml({ label: "Market value (longs)", value: formatBalance(summary.marketValueLong, currency) })}
            ${metricCardHtml({ label: "Reserved collateral (shorts)", value: formatBalance(summary.totalReservedCollateral, currency) })}
            ${metricCardHtml({ label: "Lifetime commission paid", value: formatBalance(summary.lifetimeCommissionPaid, currency) })}
          </div>
          ${
            summary.valuationWarnings.length > 0
              ? `<div class="pt-warnings">${summary.valuationWarnings.map((w) => `<p class="pt-warning-line">&#9888; ${escapeHtml(w)}</p>`).join("")}</div>`
              : ""
          }
          <p class="section-disclaimer">
            Cash, equity, and buying power are this simulated account's own authoritative figures -
            never the same as a manually-entered trading account profile balance (see Trading
            Accounts) and never verified by any broker.
          </p>`;
      },
    });
  }

  // ---------- Open positions ----------

  async function loadPositions() {
    const accountId = selectedAccountId;
    await loadInto(positionsSection, () => onPositionsLoaded(accountId), () => listOpenPaperPositions(accountId));
  }

  function onPositionsLoaded(accountId) {
    if (!alive || accountId !== selectedAccountId) return;
    if (positionsSection.value.status === "ok") {
      latestOpenPositions = positionsSection.value.data;
    }
    renderOverview();
    renderPositions();
  }

  function renderPositions() {
    if (!alive) return;
    renderStateBlock(positionsEl, positionsSection.value, {
      loadingLines: 3,
      onRetry: () => loadPositions(),
      renderContent: (positions) => {
        if (positions.length === 0) {
          positionsEl.innerHTML = `<div class="empty-state"><p>No open positions. Place an order above to open one.</p></div>`;
          return;
        }
        const account = currentAccount();
        const currency = account?.baseCurrency || "USD";
        positionsEl.innerHTML = `
          <div class="profile-grid content-fade-in">
            ${positions.map((p) => positionCardHtml(p, currency)).join("")}
          </div>`;
        positionsEl.querySelectorAll(".profile-card").forEach((card) => {
          const id = card.dataset.id;
          const position = positions.find((p) => p.id === id);
          const form = card.querySelector(".pt-close-form");
          const detailsEl = card.querySelector(".pt-close-details");
          const qtyInput = form.querySelector('[name="closeQuantity"]');
          qtyInput.addEventListener("input", () => {
            delete form.dataset.idempotencyKey;
          });
          form.addEventListener("submit", (event) => onClosePosition(event, position, detailsEl));
        });
      },
    });
  }

  function positionCardHtml(p, currency) {
    const v = p.valuation;
    const pnl = v ? Number(v.unrealizedPnl) : null;
    const pnlClass = pnl === null ? "neutral" : pnl > 0 ? "positive" : pnl < 0 ? "negative" : "neutral";
    const statusPillClass = v ? PRICE_STATUS_COLOR[v.priceStatus] || "neutral" : "neutral";
    return `
      <div class="profile-card" data-id="${p.id}">
        <div class="profile-card-header">
          <span class="profile-name">${escapeHtml(p.symbol)} &middot; ${escapeHtml(p.direction)}</span>
          <span class="sentiment-pill ${statusPillClass}">${v ? escapeHtml(humanizeEnum(v.priceStatus)) : "N/A"}</span>
        </div>
        <p class="section-status">${escapeHtml(p.assetType)} &middot; Opened ${formatDateTime(p.openedAt)}</p>
        <div class="profile-metrics">
          <div><span>Open quantity</span><strong>${formatNumber(p.openQuantity, 8)}</strong></div>
          <div><span>Avg entry price</span><strong>${formatPrice(p.avgEntryPrice, p.symbol)}</strong></div>
          <div><span>Mark price</span><strong>${v ? formatPrice(v.markPrice, p.symbol) : "N/A"}</strong></div>
          <div><span>Price as of</span><strong>${v ? formatDateTime(v.priceAsOf) : "N/A"}</strong></div>
          <div><span>Market value</span><strong>${v ? formatBalance(v.marketValue, currency) : "N/A"}</strong></div>
          <div><span>Unrealized P&amp;L</span><strong class="${pnlClass}">${v ? formatBalance(v.unrealizedPnl, currency) : "N/A"}</strong></div>
          ${v && v.reservedCollateral !== null ? `<div><span>Reserved collateral</span><strong>${formatBalance(v.reservedCollateral, currency)}</strong></div>` : ""}
          <div><span>Commission paid (open)</span><strong>${formatBalance(p.totalOpenCommissionPaid, currency)}</strong></div>
          <div><span>Stop-loss</span><strong>${p.stopLossPrice !== null ? formatPrice(p.stopLossPrice, p.symbol) : "Not set"}</strong></div>
          <div><span>Take-profit</span><strong>${p.takeProfitPrice !== null ? formatPrice(p.takeProfitPrice, p.symbol) : "Not set"}</strong></div>
        </div>
        <p class="section-disclaimer">Stop-loss/take-profit are stored and validated, but not automatically monitored this milestone - close manually below.</p>
        <details class="pt-close-details">
          <summary class="auth-secondary-btn">Close position</summary>
          <form class="pt-close-form" novalidate>
            <label class="backtest-field">
              <span>Quantity to close (leave blank to close all ${formatNumber(p.openQuantity, 8)})</span>
              <input type="number" name="closeQuantity" min="0" step="any" max="${p.openQuantity}">
            </label>
            <p class="backtest-validation-error" role="alert" hidden></p>
            <div class="backtest-form-actions">
              <button type="submit" class="auth-primary-btn">Confirm close</button>
            </div>
          </form>
        </details>
      </div>`;
  }

  async function onClosePosition(event, position, detailsEl) {
    event.preventDefault();
    const form = event.target;
    const errorEl = form.querySelector(".backtest-validation-error");
    const qtyInput = form.querySelector('[name="closeQuantity"]');
    const qtyRaw = qtyInput.value.trim();
    if (qtyRaw !== "" && !(Number(qtyRaw) > 0)) {
      errorEl.textContent = "Quantity must be a positive number, or left blank to close the full position.";
      errorEl.hidden = false;
      return;
    }
    const label = qtyRaw ? `${qtyRaw} unit(s) of your` : "your full";
    if (
      !window.confirm(
        `Close ${label} ${position.direction} ${position.symbol} position? This submits a simulated exit order at the latest available price - it does not place a real broker order.`
      )
    ) {
      return;
    }
    errorEl.hidden = true;
    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    // Same submission intent reuses the same idempotency key across retries (e.g. after a
    // transient network error); editing the quantity above already cleared it, and a definitive
    // rejection below clears it too, so the next attempt mints a genuinely new one.
    let idempotencyKey = form.dataset.idempotencyKey;
    if (!idempotencyKey) {
      idempotencyKey = newIdempotencyKey();
      form.dataset.idempotencyKey = idempotencyKey;
    }

    try {
      await closePaperPosition(selectedAccountId, position.id, {
        quantity: qtyRaw ? Number(qtyRaw) : null,
        idempotencyKey,
      });
      if (!alive) return;
      detailsEl.open = false;
      await Promise.all([loadSummary(), loadPositions(), loadOrders(), loadLedger(), loadClosed()]);
    } catch (error) {
      if (!alive) return;
      const status = error instanceof ApiError ? error.status : null;
      if (status !== null && status !== 0 && status < 500) {
        delete form.dataset.idempotencyKey;
      }
      errorEl.textContent = describePaperError(error);
      errorEl.hidden = false;
    } finally {
      if (alive) submitBtn.disabled = false;
    }
  }

  // ---------- Place order ----------

  function renderOrderForm() {
    if (!alive) return;
    const account = currentAccount();
    if (!account) {
      orderFormEl.innerHTML = "";
      orderResultEl.innerHTML = "";
      return;
    }
    const prefillSymbol = getCurrentSymbol() || "";
    orderFormEl.innerHTML = `
      <form class="backtest-form" novalidate id="ptOrderFormEl">
        <p class="section-disclaimer">
          Opens or adds to a position - BUY opens/adds to a long, SELL opens/adds to a bounded
          short. To reduce or close an existing position, use "Close position" on that position
          below instead; it uses the dedicated close action, so it can never be pointed the wrong
          direction by mistake.
        </p>
        <div class="backtest-form-grid">
          <label class="backtest-field">
            <span>Symbol</span>
            <input type="text" name="symbol" autocomplete="off" value="${escapeHtml(prefillSymbol)}" placeholder="e.g. AAPL or EUR/USD">
          </label>
          <div class="backtest-field">
            <span>Side</span>
            <div class="backtest-asset-toggle pt-side-toggle" role="group">
              <button type="button" class="backtest-toggle-btn pt-side-buy ${orderSide === "BUY" ? "active" : ""}" data-side="BUY">Buy (long)</button>
              <button type="button" class="backtest-toggle-btn pt-side-sell ${orderSide === "SELL" ? "active" : ""}" data-side="SELL">Sell (short)</button>
            </div>
          </div>
          <label class="backtest-field">
            <span>Quantity <small data-quantity-hint></small></span>
            <input type="number" name="quantity" min="0" step="any" placeholder="e.g. 10">
          </label>
        </div>
        <div class="backtest-form-grid">
          <label class="backtest-field">
            <span>Stop-loss (optional)</span>
            <input type="number" name="stopLossPrice" min="0" step="any">
          </label>
          <label class="backtest-field">
            <span>Take-profit (optional)</span>
            <input type="number" name="takeProfitPrice" min="0" step="any">
          </label>
          <div class="backtest-field">
            <span>&nbsp;</span>
            <button type="button" class="auth-secondary-btn" id="ptCheckPriceBtn">Check latest price</button>
          </div>
        </div>
        <div id="ptPricePreview"></div>
        <p class="backtest-validation-error" role="alert" hidden></p>
        <div class="backtest-form-actions">
          <button type="submit" class="auth-primary-btn backtest-submit-btn" id="ptSubmitOrderBtn">Submit simulated order</button>
        </div>
      </form>`;

    const form = orderFormEl.querySelector("#ptOrderFormEl");
    const symbolInput = form.querySelector('[name="symbol"]');
    const quantityHintEl = form.querySelector("[data-quantity-hint]");
    const sideButtons = form.querySelectorAll(".pt-side-toggle .backtest-toggle-btn");
    const pricePreviewEl = form.querySelector("#ptPricePreview");
    const checkPriceBtn = form.querySelector("#ptCheckPriceBtn");
    const errorEl = form.querySelector(".backtest-validation-error");
    const submitBtn = form.querySelector("#ptSubmitOrderBtn");

    sideButtons.forEach((btn) => {
      btn.addEventListener("click", () => {
        orderSide = btn.dataset.side;
        sideButtons.forEach((b) => b.classList.toggle("active", b.dataset.side === orderSide));
        // Side is a core order parameter but toggle buttons fire no "input" event on the form -
        // the generic clearPendingKey() listener below would never catch this change otherwise.
        orderIdempotencyKey = null;
      });
    });

    function updateQuantityHint() {
      quantityHintEl.textContent = isForexSymbol(symbolInput.value.trim().toUpperCase())
        ? "(base-currency units)"
        : "(shares/units)";
    }
    updateQuantityHint();

    function clearPendingKey() {
      orderIdempotencyKey = null;
    }
    form.addEventListener("input", clearPendingKey);

    symbolInput.addEventListener("input", updateQuantityHint);

    async function checkPrice() {
      const symbol = symbolInput.value.trim().toUpperCase();
      if (!isValidSymbol(symbol)) {
        pricePreviewEl.innerHTML = `<p class="section-status">Enter a valid symbol first.</p>`;
        return;
      }
      pricePreviewEl.innerHTML = `<p class="section-status">Checking latest available price&hellip;</p>`;
      checkPriceBtn.disabled = true;
      try {
        const candles = await withCache(symbol, "candles", () => getCandles(symbol));
        if (!alive) return;
        const header = deriveAssetHeader(candles);
        if (header.latestClose === null) {
          pricePreviewEl.innerHTML = `<p class="section-status">No candle data available for ${escapeHtml(symbol)} yet.</p>`;
          return;
        }
        const quantityValue = Number(form.querySelector('[name="quantity"]').value || 0);
        const notional = quantityValue > 0 ? quantityValue * header.latestClose : null;
        const required = requiredAccountCurrency(symbol);
        const currencyMismatch = required && required !== account.baseCurrency;
        pricePreviewEl.innerHTML = `
          <p class="section-status">
            Latest available daily close for ${escapeHtml(symbol)}: <strong>${formatPrice(header.latestClose, symbol)}</strong>
            as of ${formatDateTime(header.timestamp)}. Not a live quote - Orynix has no real-time
            feed; the backend resolves and fills against its own latest acceptable price at order
            time, which may differ slightly.
          </p>
          ${notional !== null ? `<p class="section-status">Estimated notional: ${formatBalance(notional, account.baseCurrency)} &middot; buying power available: ${formatBalance(latestSummaryBuyingPower(), account.baseCurrency)}</p>` : ""}
          ${currencyMismatch ? `<p class="pt-warning-line">This account is denominated in ${escapeHtml(account.baseCurrency)}, but ${escapeHtml(symbol)} requires a ${escapeHtml(required)} account. This order will be rejected.</p>` : ""}
        `;
      } catch (error) {
        if (!alive) return;
        pricePreviewEl.innerHTML = `<p class="pt-warning-line">${escapeHtml(describePaperError(error))}</p>`;
      } finally {
        if (alive) checkPriceBtn.disabled = false;
      }
    }
    checkPriceBtn.addEventListener("click", checkPrice);
    if (prefillSymbol) checkPrice();

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (orderBusy) return;

      const symbol = symbolInput.value.trim().toUpperCase();
      const quantityRaw = form.querySelector('[name="quantity"]').value;
      const stopLossRaw = form.querySelector('[name="stopLossPrice"]').value;
      const takeProfitRaw = form.querySelector('[name="takeProfitPrice"]').value;

      const errors = [];
      if (!isValidSymbol(symbol)) errors.push("Enter a valid stock symbol (e.g. AAPL) or forex pair (e.g. EUR/USD).");
      if (!(Number(quantityRaw) > 0)) errors.push("Quantity must be a positive number.");
      if (stopLossRaw !== "" && !(Number(stopLossRaw) > 0)) errors.push("Stop-loss must be a positive number.");
      if (takeProfitRaw !== "" && !(Number(takeProfitRaw) > 0)) errors.push("Take-profit must be a positive number.");

      if (isValidSymbol(symbol)) {
        const required = requiredAccountCurrency(symbol);
        if (required && required !== account.baseCurrency) {
          errors.push(`This account is denominated in ${account.baseCurrency}; ${symbol} requires a ${required} account.`);
        }
        const wantedDirection = orderSide === "BUY" ? "LONG" : "SHORT";
        const openOpposite = latestOpenPositions?.find((p) => p.symbol === symbol && p.direction !== wantedDirection);
        if (openOpposite) {
          errors.push(`An open ${openOpposite.direction} position already exists for ${symbol}. Close it first before opening the opposite side.`);
        }
      }

      if (errors.length > 0) {
        errorEl.innerHTML = errors.map((e) => escapeHtml(e)).join("<br>");
        errorEl.hidden = false;
        return;
      }
      errorEl.hidden = true;

      if (!orderIdempotencyKey) {
        orderIdempotencyKey = newIdempotencyKey();
      }
      const request = {
        symbol,
        side: orderSide,
        positionEffect: "OPEN",
        quantity: Number(quantityRaw),
        idempotencyKey: orderIdempotencyKey,
        stopLossPrice: stopLossRaw !== "" ? Number(stopLossRaw) : null,
        takeProfitPrice: takeProfitRaw !== "" ? Number(takeProfitRaw) : null,
      };

      orderBusy = true;
      submitBtn.disabled = true;
      submitBtn.textContent = "Submitting&hellip;";
      try {
        const order = await submitPaperOrder(selectedAccountId, request);
        if (!alive) return;
        orderIdempotencyKey = null;
        renderOrderResult(order, null);
        await Promise.all([loadSummary(), loadPositions(), loadOrders(), loadLedger()]);
      } catch (error) {
        if (!alive) return;
        const status = error instanceof ApiError ? error.status : null;
        if (status !== null && status !== 0 && status < 500) {
          orderIdempotencyKey = null;
        }
        renderOrderResult(null, error);
      } finally {
        if (alive) {
          orderBusy = false;
          submitBtn.disabled = false;
          submitBtn.textContent = "Submit simulated order";
        }
      }
    });
  }

  function latestSummaryBuyingPower() {
    return summarySection.value.status === "ok" ? summarySection.value.data.buyingPower : null;
  }

  function renderOrderResult(order, error) {
    if (!alive) return;
    if (error) {
      orderResultEl.innerHTML = `
        <div class="card state-error content-fade-in" role="alert">
          <p class="state-error-title">Order rejected</p>
          <p class="state-error-message">${escapeHtml(describePaperError(error))}</p>
        </div>`;
      return;
    }
    if (!order) {
      orderResultEl.innerHTML = "";
      return;
    }
    const account = currentAccount();
    const currency = account?.baseCurrency || "USD";
    orderResultEl.innerHTML = `
      <div class="card content-fade-in">
        <div class="section-header">
          <h3>Order filled</h3>
          <span class="sentiment-pill positive">${escapeHtml(order.status)}</span>
        </div>
        <div class="metric-grid">
          ${metricCardHtml({ label: "Symbol", value: escapeHtml(order.symbol) })}
          ${metricCardHtml({ label: "Side / effect", value: `${escapeHtml(order.side)} / ${escapeHtml(order.positionEffect)}` })}
          ${metricCardHtml({ label: "Filled quantity", value: formatNumber(order.filledQuantity, 8) })}
          ${metricCardHtml({ label: "Fill price", value: formatPrice(order.fillPrice, order.symbol) })}
          ${metricCardHtml({ label: "Commission", value: formatBalance(order.commission, currency) })}
          ${metricCardHtml({ label: "Source candle", value: formatDateTime(order.sourceCandleTimestamp) })}
          ${metricCardHtml({ label: "Executed at", value: formatDateTime(order.executionTimestamp) })}
        </div>
        <p class="section-disclaimer">${escapeHtml(order.executionAssumptions)}</p>
      </div>`;
  }

  // ---------- Order history ----------

  async function loadOrders() {
    const accountId = selectedAccountId;
    await loadInto(ordersSection, () => renderOrders(accountId), () => listPaperOrders(accountId, ordersPage.page, PAGE_SIZE));
  }

  function renderOrders(accountId) {
    if (!alive || accountId !== selectedAccountId) return;
    renderStateBlock(ordersEl, ordersSection.value, {
      loadingLines: 3,
      onRetry: () => loadOrders(),
      renderContent: (paged) => {
        if (paged.content.length === 0 && paged.page === 0) {
          ordersEl.innerHTML = `<div class="empty-state"><p>No orders submitted yet.</p></div>`;
          return;
        }
        ordersEl.innerHTML = `
          <div class="backtest-table-wrapper content-fade-in">
            <table class="backtest-trades-table">
              <thead><tr>
                <th>Symbol</th><th>Side</th><th>Effect</th><th>Qty</th><th>Fill price</th>
                <th>Commission</th><th>Executed at</th>
              </tr></thead>
              <tbody>
                ${paged.content
                  .map(
                    (o) => `
                  <tr>
                    <td>${escapeHtml(o.symbol)}</td>
                    <td>${escapeHtml(o.side)}</td>
                    <td>${escapeHtml(o.positionEffect)}</td>
                    <td>${formatNumber(o.filledQuantity, 8)}</td>
                    <td>${formatPrice(o.fillPrice, o.symbol)}</td>
                    <td>${formatNumber(o.commission, 2)}</td>
                    <td>${formatDateTime(o.executionTimestamp)}</td>
                  </tr>`
                  )
                  .join("")}
              </tbody>
            </table>
          </div>
          <div id="ptOrdersPagination"></div>`;
        const paginationEl = ordersEl.querySelector("#ptOrdersPagination");
        paginationEl.innerHTML = paginationControlsHtml(ordersPage, paged.totalPages);
        wirePagination(paginationEl, ordersPage, paged.totalPages, () => loadOrders());
      },
    });
  }

  // ---------- Closed positions ----------

  async function loadClosed() {
    const accountId = selectedAccountId;
    await loadInto(closedSection, () => renderClosed(accountId), () => listClosedPaperPositions(accountId, closedPage.page, PAGE_SIZE));
  }

  function renderClosed(accountId) {
    if (!alive || accountId !== selectedAccountId) return;
    renderStateBlock(closedEl, closedSection.value, {
      loadingLines: 3,
      onRetry: () => loadClosed(),
      renderContent: (paged) => {
        if (paged.content.length === 0 && paged.page === 0) {
          closedEl.innerHTML = `<div class="empty-state"><p>No closed positions yet.</p></div>`;
          return;
        }
        const account = currentAccount();
        const currency = account?.baseCurrency || "USD";
        closedEl.innerHTML = `
          <div class="backtest-table-wrapper content-fade-in">
            <table class="backtest-trades-table">
              <thead><tr>
                <th>Symbol</th><th>Direction</th><th>Avg entry</th><th>Realized P&amp;L</th>
                <th>Commission paid</th><th>Opened</th><th>Closed</th>
              </tr></thead>
              <tbody>
                ${paged.content
                  .map((p) => {
                    const pnl = Number(p.realizedPnl);
                    const pnlClass = pnl > 0 ? "positive" : pnl < 0 ? "negative" : "neutral";
                    return `
                  <tr>
                    <td>${escapeHtml(p.symbol)}</td>
                    <td>${escapeHtml(p.direction)}</td>
                    <td>${formatPrice(p.avgEntryPrice, p.symbol)}</td>
                    <td class="${pnlClass}">${formatBalance(p.realizedPnl, currency)}</td>
                    <td>${formatBalance(p.totalOpenCommissionPaid, currency)}</td>
                    <td>${formatDateTime(p.openedAt)}</td>
                    <td>${p.closedAt ? formatDateTime(p.closedAt) : "N/A"}</td>
                  </tr>`;
                  })
                  .join("")}
              </tbody>
            </table>
          </div>
          <p class="section-disclaimer">Fill-by-fill quantities for a closed position are available in Order history above.</p>
          <div id="ptClosedPagination"></div>`;
        const paginationEl = closedEl.querySelector("#ptClosedPagination");
        paginationEl.innerHTML = paginationControlsHtml(closedPage, paged.totalPages);
        wirePagination(paginationEl, closedPage, paged.totalPages, () => loadClosed());
      },
    });
  }

  // ---------- Ledger ----------

  async function loadLedger() {
    const accountId = selectedAccountId;
    await loadInto(ledgerSection, () => renderLedger(accountId), () => listPaperLedgerEntries(accountId, ledgerPage.page, PAGE_SIZE));
  }

  function renderLedger(accountId) {
    if (!alive || accountId !== selectedAccountId) return;
    renderStateBlock(ledgerEl, ledgerSection.value, {
      loadingLines: 3,
      onRetry: () => loadLedger(),
      renderContent: (paged) => {
        if (paged.content.length === 0 && paged.page === 0) {
          ledgerEl.innerHTML = `<div class="empty-state"><p>No account activity yet.</p></div>`;
          return;
        }
        const account = currentAccount();
        const currency = account?.baseCurrency || "USD";
        ledgerEl.innerHTML = `
          <div class="backtest-table-wrapper content-fade-in">
            <table class="backtest-trades-table">
              <thead><tr><th>Type</th><th>Cash change</th><th>Cash balance after</th><th>Description</th><th>When</th></tr></thead>
              <tbody>
                ${paged.content
                  .map((e) => {
                    const delta = Number(e.cashDelta);
                    const deltaClass = delta > 0 ? "positive" : delta < 0 ? "negative" : "neutral";
                    return `
                  <tr>
                    <td>${escapeHtml(LEDGER_TYPE_LABEL[e.entryType] || humanizeEnum(e.entryType))}</td>
                    <td class="${deltaClass}">${delta > 0 ? "+" : ""}${formatBalance(e.cashDelta, currency)}</td>
                    <td>${formatBalance(e.cashBalanceAfter, currency)}</td>
                    <td>${escapeHtml(e.description)}</td>
                    <td>${formatDateTime(e.createdAt)}</td>
                  </tr>`;
                  })
                  .join("")}
              </tbody>
            </table>
          </div>
          <div id="ptLedgerPagination"></div>`;
        const paginationEl = ledgerEl.querySelector("#ptLedgerPagination");
        paginationEl.innerHTML = paginationControlsHtml(ledgerPage, paged.totalPages);
        wirePagination(paginationEl, ledgerPage, paged.totalPages, () => loadLedger());
      },
    });
  }

  // ---------- Wiring ----------

  container.querySelector("#ptAccountsRefreshBtn").addEventListener("click", () => loadAccounts(selectedAccountId));
  container.querySelector("#ptPositionsRefreshBtn").addEventListener("click", () => loadPositions());
  container.querySelector("#ptOrdersRefreshBtn").addEventListener("click", () => loadOrders());
  container.querySelector("#ptClosedRefreshBtn").addEventListener("click", () => loadClosed());
  container.querySelector("#ptLedgerRefreshBtn").addEventListener("click", () => loadLedger());
  summaryRefreshBtn.addEventListener("click", async () => {
    summaryRefreshBtn.disabled = true;
    await Promise.all([loadSummary(true), loadPositions()]);
    if (alive) summaryRefreshBtn.disabled = false;
  });

  loadAccounts(persistedAccountId);

  return () => {
    alive = false;
  };
}
