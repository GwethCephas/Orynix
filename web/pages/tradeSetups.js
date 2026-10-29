import {
  listTradeSetups,
  createTradeSetup,
  getTradeSetup,
  expireTradeSetup,
  decideTradeSetup,
  closeTradeSetup,
  evaluateTradeSetupRisk,
  linkTradeSetupBacktest,
  practiceTradeSetupInPaperTrading,
  listTradingAccountProfiles,
  listBacktests,
  listPaperTradingAccounts,
  getBacktest,
  ApiError,
} from "../api.js";
import { navigate } from "../router.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { escapeHtml, formatNumber, formatDateTime, humanizeEnum } from "../format.js";

const STATUS_COLOR = { DRAFT: "neutral", ACCEPTED: "positive", REJECTED: "negative", EXPIRED: "negative", CLOSED: "neutral" };
const OUTCOME_OPTIONS = ["WIN", "LOSS", "BREAKEVEN"];

function newIdempotencyKey() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `ts-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function describeTradeSetupError(error) {
  const status = error instanceof ApiError ? error.status : null;
  if (status === 0) return "Could not reach the backend. Is it running?";
  // Treat the backend as the source of truth everywhere else too - its own message is always
  // surfaced verbatim (400 validation detail, 404 ownership, 409 conflict, 422 stale price).
  return error?.message || "Unexpected error.";
}

function provenanceBadge(setup) {
  // aiProposalId, not source === "AI_ANALYSIS", is the only server-verifiable "this really came
  // from the AI agent" signal - a setup saved through the older manual-entry endpoint can still
  // declare source=AI_ANALYSIS with client-asserted, never-verified provenance fields.
  if (setup.aiProposalId) return `<span class="profile-badge">AI-verified</span>`;
  if (setup.source === "AI_ANALYSIS") return `<span class="profile-badge">AI (unverified)</span>`;
  return `<span class="profile-badge">Manual</span>`;
}

export function mount(container) {
  let alive = true;
  let busy = false;
  let profiles = [];

  const listSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page trade-setups-page">
      <section class="card">
        <h1>Trade Setups</h1>
        <p class="section-disclaimer">
          A trade-idea workflow connecting AI analysis, manual entries, backtesting, and paper
          trading. <strong>Accepted</strong> means you decided to consider this idea for
          yourself - never that a trade executed. <strong>Closed</strong> means you reported it
          closed - never that a broker confirmed it. Linking a backtest records that a strategy
          was historically tested on this symbol; it does not validate this setup's specific
          entry, stop, or target levels, which the backtest engine has no concept of. Practicing
          in paper trading places a real simulated order in an isolated practice account - never
          a real broker order.
        </p>
      </section>

      <section class="card">
        <h2>New trade setup</h2>
        <form class="backtest-form" novalidate id="setupForm">
          <div class="backtest-form-grid">
            <label class="backtest-field">
              <span>Symbol</span>
              <input type="text" name="symbol" autocomplete="off" placeholder="AAPL or EUR/USD">
            </label>
            <label class="backtest-field">
              <span>Direction</span>
              <select name="direction">
                <option value="LONG">Long</option>
                <option value="SHORT">Short</option>
              </select>
            </label>
            <label class="backtest-field">
              <span>Entry price</span>
              <input type="number" name="entryPrice" min="0" step="any">
            </label>
            <label class="backtest-field">
              <span>Stop loss (optional)</span>
              <input type="number" name="stopLossPrice" min="0" step="any">
            </label>
            <label class="backtest-field">
              <span>Target (optional)</span>
              <input type="number" name="targetPrice" min="0" step="any">
            </label>
            <label class="backtest-field">
              <span>Trading account profile (optional)</span>
              <select name="accountProfileId"><option value="">None</option></select>
            </label>
          </div>
          <label class="backtest-field">
            <span>Rationale (optional)</span>
            <textarea name="rationale" rows="2" maxlength="2000"></textarea>
          </label>
          <p class="backtest-validation-error" role="alert" hidden></p>
          <div class="backtest-form-actions">
            <button type="submit" class="auth-primary-btn" id="setupSubmitBtn">Save trade setup</button>
          </div>
        </form>
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Your trade setups</h2>
          <button type="button" class="refresh-btn" id="setupsRefreshBtn">Refresh</button>
        </div>
        <div class="backtest-form-grid">
          <label class="backtest-field">
            <span>Status</span>
            <select name="filterStatus">
              <option value="">All</option>
              <option value="DRAFT">Draft</option>
              <option value="ACCEPTED">Accepted</option>
              <option value="REJECTED">Rejected</option>
              <option value="EXPIRED">Expired</option>
              <option value="CLOSED">Closed</option>
            </select>
          </label>
          <label class="backtest-field">
            <span>Asset type</span>
            <select name="filterAssetType">
              <option value="">All</option>
              <option value="STOCK">Stock</option>
              <option value="FOREX">Forex</option>
            </select>
          </label>
          <label class="backtest-field">
            <span>Symbol</span>
            <input type="text" name="filterSymbol" autocomplete="off" placeholder="e.g. AAPL">
          </label>
        </div>
        <div id="setupsList"></div>
      </section>
    </div>`;

  const form = container.querySelector("#setupForm");
  const profileSelect = form.querySelector('[name="accountProfileId"]');
  const validationErrorEl = form.querySelector(".backtest-validation-error");
  const submitBtn = container.querySelector("#setupSubmitBtn");
  const listEl = container.querySelector("#setupsList");
  const refreshBtn = container.querySelector("#setupsRefreshBtn");
  const filterStatus = container.querySelector('[name="filterStatus"]');
  const filterAssetType = container.querySelector('[name="filterAssetType"]');
  const filterSymbol = container.querySelector('[name="filterSymbol"]');

  async function loadProfiles() {
    try {
      profiles = await listTradingAccountProfiles();
    } catch {
      profiles = [];
    }
    if (!alive) return;
    profileSelect.innerHTML =
      `<option value="">None</option>` +
      profiles.map((p) => `<option value="${p.id}">${escapeHtml(p.name)} (${escapeHtml(p.accountCurrency)})</option>`).join("");
  }
  loadProfiles();

  // ---- create ----

  function readForm() {
    return {
      symbol: form.querySelector('[name="symbol"]').value.trim().toUpperCase(),
      direction: form.querySelector('[name="direction"]').value,
      entryPrice: form.querySelector('[name="entryPrice"]').value,
      stopLossPrice: form.querySelector('[name="stopLossPrice"]').value,
      targetPrice: form.querySelector('[name="targetPrice"]').value,
      rationale: form.querySelector('[name="rationale"]').value.trim(),
      accountProfileId: profileSelect.value,
    };
  }

  function validateCreate(values) {
    const errors = [];
    if (!values.symbol) errors.push("Enter a symbol, e.g. AAPL or EUR/USD.");
    if (!(Number(values.entryPrice) > 0)) errors.push("Entry price must be a positive number.");
    if (values.stopLossPrice !== "" && !(Number(values.stopLossPrice) > 0)) errors.push("Stop loss must be a positive number.");
    if (values.targetPrice !== "" && !(Number(values.targetPrice) > 0)) errors.push("Target must be a positive number.");
    return errors;
  }

  async function onCreateSubmit(event) {
    event.preventDefault();
    if (busy) return;
    const values = readForm();
    const errors = validateCreate(values);
    if (errors.length > 0) {
      validationErrorEl.innerHTML = errors.map((e) => escapeHtml(e)).join("<br>");
      validationErrorEl.hidden = false;
      return;
    }
    validationErrorEl.hidden = true;

    const request = {
      symbol: values.symbol,
      direction: values.direction,
      entryPrice: Number(values.entryPrice),
      stopLossPrice: values.stopLossPrice !== "" ? Number(values.stopLossPrice) : null,
      targetPrice: values.targetPrice !== "" ? Number(values.targetPrice) : null,
      rationale: values.rationale || null,
      source: "MANUAL",
      aiProviderName: null,
      aiModel: null,
      aiAnalysisAsOf: null,
      accountProfileId: values.accountProfileId || null,
    };

    busy = true;
    submitBtn.disabled = true;
    try {
      await createTradeSetup(request);
      if (!alive) return;
      form.reset();
      loadSetups();
    } catch (error) {
      if (!alive) return;
      validationErrorEl.textContent = describeTradeSetupError(error);
      validationErrorEl.hidden = false;
    } finally {
      if (alive) {
        busy = false;
        submitBtn.disabled = false;
      }
    }
  }
  form.addEventListener("submit", onCreateSubmit);

  // ---- list ----

  function currentFilters() {
    return {
      status: filterStatus.value || undefined,
      assetType: filterAssetType.value || undefined,
      symbol: filterSymbol.value.trim().toUpperCase() || undefined,
    };
  }

  async function loadSetups() {
    await loadInto(listSection, renderList, () => listTradeSetups(currentFilters()));
  }

  [filterStatus, filterAssetType].forEach((el) => el.addEventListener("change", () => loadSetups()));
  filterSymbol.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      loadSetups();
    }
  });
  refreshBtn.addEventListener("click", () => loadSetups());

  // ---- per-card detail panel ----

  async function refreshCard(card, id) {
    let fresh;
    try {
      fresh = await getTradeSetup(id);
    } catch {
      return; // the summary row still shows the last-known state; the next list refresh will catch up.
    }
    if (!alive) return;

    const statusBadge = card.querySelector(".setup-status-badge");
    statusBadge.className = `signal-badge ${STATUS_COLOR[fresh.status] || "neutral"}`;
    statusBadge.innerHTML = `<span class="signal-state">${escapeHtml(fresh.status)}</span>`;

    const body = card.querySelector(".ai-details-body");
    const details = card.querySelector(".ai-details");
    if (details.open) {
      renderDetailBody(body, fresh);
    }
  }

  function renderRiskSection(body, setup) {
    const section = body.querySelector(".ts-risk-section");
    section.innerHTML = `
      <h3>Risk evaluation</h3>
      <p class="section-status">Always computed fresh - never stored or assumed current.</p>
      <div class="backtest-form-grid">
        <label class="backtest-field"><span>Equity override (optional)</span><input type="number" min="0" step="any" class="ts-risk-equity"></label>
        <label class="backtest-field"><span>Account currency override (optional)</span><input type="text" maxlength="3" class="ts-risk-currency"></label>
        <label class="backtest-field"><span>Risk per trade % override (optional)</span><input type="number" min="0" max="100" step="any" class="ts-risk-percent"></label>
      </div>
      <button type="button" class="auth-secondary-btn ts-risk-btn">Evaluate risk</button>
      <div class="ts-risk-result"></div>`;

    section.querySelector(".ts-risk-btn").addEventListener("click", async () => {
      const button = section.querySelector(".ts-risk-btn");
      const resultEl = section.querySelector(".ts-risk-result");
      const equity = section.querySelector(".ts-risk-equity").value;
      const currency = section.querySelector(".ts-risk-currency").value.trim().toUpperCase();
      const percent = section.querySelector(".ts-risk-percent").value;
      button.disabled = true;
      resultEl.innerHTML = "";
      try {
        const result = await evaluateTradeSetupRisk(setup.id, {
          equity: equity !== "" ? Number(equity) : null,
          accountCurrency: currency || null,
          riskPercentPerTrade: percent !== "" ? Number(percent) : null,
        });
        if (!alive) return;
        if (!result.sizing) {
          resultEl.innerHTML = `<p class="section-status">${result.limitations.map((l) => escapeHtml(l)).join(" ")}</p>`;
        } else {
          const s = result.sizing;
          resultEl.innerHTML = `
            <div class="metric-grid">
              <div class="metric-card"><div class="metric-label">Quantity</div><div class="metric-value">${s.quantity !== null ? formatNumber(s.quantity, 4) : "N/A"}</div></div>
              <div class="metric-card"><div class="metric-label">Est. loss at stop</div><div class="metric-value">${s.estimatedLossAtStop !== null ? formatNumber(s.estimatedLossAtStop, 2) : "N/A"} ${escapeHtml(s.accountCurrency)}</div></div>
              <div class="metric-card"><div class="metric-label">Est. gain at target</div><div class="metric-value">${s.estimatedGainAtTakeProfit !== null ? formatNumber(s.estimatedGainAtTakeProfit, 2) : "N/A"} ${escapeHtml(s.accountCurrency)}</div></div>
              <div class="metric-card"><div class="metric-label">Risk/reward</div><div class="metric-value">${s.riskRewardRatio !== null ? formatNumber(s.riskRewardRatio, 2) : "N/A"}</div></div>
            </div>
            ${result.limitations.length > 0 ? `<p class="section-disclaimer">${result.limitations.map((l) => escapeHtml(l)).join(" ")}</p>` : ""}`;
        }
      } catch (error) {
        if (!alive) return;
        resultEl.innerHTML = `<p class="section-status">${escapeHtml(describeTradeSetupError(error))}</p>`;
      } finally {
        if (alive) button.disabled = false;
      }
    });
  }

  async function renderBacktestSection(body, setup, card, id) {
    const section = body.querySelector(".ts-backtest-section");
    if (setup.backtestRunId) {
      section.innerHTML = `<h3>Linked backtest</h3><p class="section-status">Loading...</p>`;
      try {
        const run = await getBacktest(setup.backtestRunId);
        if (!alive) return;
        section.innerHTML = `
          <h3>Linked backtest</h3>
          <div class="metric-grid">
            <div class="metric-card"><div class="metric-label">Strategy</div><div class="metric-value">${escapeHtml(humanizeEnum(run.strategyType))}</div></div>
            <div class="metric-card"><div class="metric-label">Range</div><div class="metric-value">${escapeHtml(run.fromDate)} &ndash; ${escapeHtml(run.toDate)}</div></div>
            <div class="metric-card"><div class="metric-label">Net P&amp;L</div><div class="metric-value ${Number(run.netPnl) >= 0 ? "positive" : "negative"}">${formatNumber(run.netPnl, 2)}</div></div>
            <div class="metric-card"><div class="metric-label">Win rate</div><div class="metric-value">${run.winRate !== null ? formatNumber(run.winRate, 2) + "%" : "N/A"}</div></div>
          </div>
          <p class="section-disclaimer">
            This shows a strategy's historical performance on ${escapeHtml(run.symbol)} over the
            backtested range - it does not validate this setup's specific entry, stop, or target
            levels, which the backtest engine has no concept of.
          </p>`;
      } catch {
        if (!alive) return;
        section.innerHTML = `<h3>Linked backtest</h3><p class="section-status">Could not load the linked backtest run.</p>`;
      }
      return;
    }

    section.innerHTML = `<h3>Link a backtest</h3><p class="section-status">Loading your backtests for ${escapeHtml(setup.symbol)}...</p>`;
    let runs = [];
    try {
      runs = (await listBacktests()).filter((r) => r.symbol === setup.symbol);
    } catch {
      runs = [];
    }
    if (!alive) return;

    if (runs.length === 0) {
      section.innerHTML = `
        <h3>Link a backtest</h3>
        <p class="section-status">No completed backtests for ${escapeHtml(setup.symbol)} yet. Run one on the Backtesting page, then come back here to link it.</p>`;
      return;
    }
    section.innerHTML = `
      <h3>Link a backtest</h3>
      <p class="section-disclaimer">
        Linking records that a strategy was historically tested on this symbol - it never
        validates this setup's own entry, stop, or target levels.
      </p>
      <div class="backtest-form-grid">
        <label class="backtest-field">
          <span>Backtest run</span>
          <select class="ts-backtest-select">
            ${runs
              .map(
                (r) =>
                  `<option value="${r.id}">${escapeHtml(humanizeEnum(r.strategyType))} &middot; ${escapeHtml(r.fromDate)}&ndash;${escapeHtml(r.toDate)} &middot; Net P&amp;L ${formatNumber(r.netPnl, 2)}</option>`
              )
              .join("")}
          </select>
        </label>
      </div>
      <button type="button" class="auth-secondary-btn ts-backtest-link-btn">Link this backtest</button>
      <p class="ts-backtest-link-error section-status" hidden></p>`;

    section.querySelector(".ts-backtest-link-btn").addEventListener("click", async () => {
      const button = section.querySelector(".ts-backtest-link-btn");
      const errorEl = section.querySelector(".ts-backtest-link-error");
      const backtestRunId = section.querySelector(".ts-backtest-select").value;
      button.disabled = true;
      errorEl.hidden = true;
      try {
        await linkTradeSetupBacktest(id, backtestRunId);
        if (!alive) return;
        await refreshCard(card, id);
      } catch (error) {
        if (!alive) return;
        errorEl.textContent = describeTradeSetupError(error);
        errorEl.hidden = false;
      } finally {
        if (alive) button.disabled = false;
      }
    });
  }

  async function renderPaperTradingSection(body, setup, card, id) {
    const section = body.querySelector(".ts-paper-section");

    if (setup.paperOrderId) {
      section.innerHTML = `
        <h3>Paper trading</h3>
        <p class="section-status">
          Already practiced via a simulated paper order (account ${escapeHtml(setup.paperAccountId)},
          order ${escapeHtml(setup.paperOrderId)}).
        </p>
        <button type="button" class="auth-secondary-btn ts-view-paper-btn">Open Paper Trading</button>`;
      section.querySelector(".ts-view-paper-btn").addEventListener("click", () => navigate("paper-trading"));
      return;
    }

    if (setup.status !== "ACCEPTED") {
      section.innerHTML = `<h3>Paper trading</h3><p class="section-status">Only an accepted trade setup can be practiced in paper trading.</p>`;
      return;
    }

    section.innerHTML = `<h3>Practice in Paper Trading</h3><p class="section-status">Loading your paper trading accounts...</p>`;
    let accounts = [];
    try {
      accounts = await listPaperTradingAccounts();
    } catch {
      accounts = [];
    }
    if (!alive) return;

    if (accounts.length === 0) {
      section.innerHTML = `
        <h3>Practice in Paper Trading</h3>
        <p class="section-status">No paper trading accounts yet. Create one on the Paper Trading page first.</p>
        <button type="button" class="auth-secondary-btn ts-view-paper-btn">Open Paper Trading</button>`;
      section.querySelector(".ts-view-paper-btn").addEventListener("click", () => navigate("paper-trading"));
      return;
    }

    section.innerHTML = `
      <h3>Practice in Paper Trading</h3>
      <p class="section-disclaimer">
        This submits a real simulated market order against the latest <em>available</em> daily
        close, which may be from a prior trading day, in an isolated paper trading account - never
        a real broker order, and never automatic.
      </p>
      <div class="backtest-form-grid">
        <label class="backtest-field">
          <span>Paper trading account</span>
          <select class="ts-paper-account">
            ${accounts.map((a) => `<option value="${a.id}">${escapeHtml(a.name)} (${escapeHtml(a.baseCurrency)})</option>`).join("")}
          </select>
        </label>
        <label class="backtest-field">
          <span>Quantity</span>
          <input type="number" min="0" step="any" class="ts-paper-quantity" value="1">
        </label>
      </div>
      <button type="button" class="auth-primary-btn ts-paper-submit-btn">Practice in Paper Trading</button>
      <p class="ts-paper-error section-status" hidden></p>`;

    section.querySelector(".ts-paper-submit-btn").addEventListener("click", async () => {
      const button = section.querySelector(".ts-paper-submit-btn");
      const errorEl = section.querySelector(".ts-paper-error");
      const accountId = section.querySelector(".ts-paper-account").value;
      const quantity = section.querySelector(".ts-paper-quantity").value;
      if (!(Number(quantity) > 0)) {
        errorEl.textContent = "Quantity must be a positive number.";
        errorEl.hidden = false;
        return;
      }
      const confirmed = window.confirm(
        `Submit a simulated ${setup.direction === "LONG" ? "BUY" : "SELL"} market order for ${quantity} ${setup.symbol} ` +
          `in this paper trading account? This is a real simulated fill against the latest available ` +
          `daily close - never a real broker order.`
      );
      if (!confirmed) return;

      errorEl.hidden = true;
      button.disabled = true;
      button.textContent = "Submitting...";
      try {
        await practiceTradeSetupInPaperTrading(id, {
          paperAccountId: accountId,
          quantity: Number(quantity),
          idempotencyKey: newIdempotencyKey(),
        });
        if (!alive) return;
        await refreshCard(card, id);
      } catch (error) {
        if (!alive) return;
        errorEl.textContent = describeTradeSetupError(error);
        errorEl.hidden = false;
        button.disabled = false;
        button.textContent = "Practice in Paper Trading";
      }
    });
  }

  function renderLifecycleActions(body, setup, card, id) {
    const section = body.querySelector(".ts-actions-section");
    const errorHtml = `<p class="ts-actions-error section-status" hidden></p>`;

    if (setup.status === "DRAFT") {
      section.innerHTML = `
        <div class="profile-actions">
          <button type="button" class="auth-secondary-btn ts-accept-btn">Accept</button>
          <button type="button" class="auth-secondary-btn ts-reject-btn">Reject</button>
          <button type="button" class="auth-secondary-btn ts-expire-btn">Expire</button>
        </div>
        ${errorHtml}`;

      const errorEl = section.querySelector(".ts-actions-error");
      const act = async (fn) => {
        errorEl.hidden = true;
        try {
          await fn();
          if (alive) await refreshCard(card, id);
        } catch (error) {
          if (!alive) return;
          errorEl.textContent = describeTradeSetupError(error);
          errorEl.hidden = false;
        }
      };
      section.querySelector(".ts-accept-btn").addEventListener("click", () => act(() => decideTradeSetup(id, { decision: "ACCEPT", notes: null })));
      section.querySelector(".ts-reject-btn").addEventListener("click", () => act(() => decideTradeSetup(id, { decision: "REJECT", notes: null })));
      section.querySelector(".ts-expire-btn").addEventListener("click", () => act(() => expireTradeSetup(id)));
      return;
    }

    if (setup.status === "ACCEPTED") {
      section.innerHTML = `
        <h3>Close this setup</h3>
        <div class="backtest-form-grid">
          <label class="backtest-field"><span>Exit price (optional)</span><input type="number" min="0" step="any" class="ts-close-exit"></label>
          <label class="backtest-field">
            <span>Outcome (optional)</span>
            <select class="ts-close-outcome"><option value="">Not specified</option>${OUTCOME_OPTIONS.map((o) => `<option value="${o}">${o}</option>`).join("")}</select>
          </label>
        </div>
        <label class="backtest-field"><span>Closure notes (optional)</span><textarea class="ts-close-notes" rows="2" maxlength="2000"></textarea></label>
        <button type="button" class="auth-secondary-btn ts-close-btn">Mark as closed</button>
        ${errorHtml}`;

      section.querySelector(".ts-close-btn").addEventListener("click", async () => {
        const errorEl = section.querySelector(".ts-actions-error");
        const exitPrice = section.querySelector(".ts-close-exit").value;
        const outcome = section.querySelector(".ts-close-outcome").value;
        const notes = section.querySelector(".ts-close-notes").value.trim();
        errorEl.hidden = true;
        try {
          await closeTradeSetup(id, {
            exitPrice: exitPrice !== "" ? Number(exitPrice) : null,
            outcome: outcome || null,
            closureNotes: notes || null,
          });
          if (alive) await refreshCard(card, id);
        } catch (error) {
          if (!alive) return;
          errorEl.textContent = describeTradeSetupError(error);
          errorEl.hidden = false;
        }
      });
      return;
    }

    section.innerHTML = `<p class="section-status">This trade setup is ${escapeHtml(setup.status.toLowerCase())} - no further lifecycle actions are available.</p>`;
  }

  function renderDetailBody(body, setup) {
    body.innerHTML = `
      <div class="metric-grid">
        <div class="metric-card"><div class="metric-label">Entry</div><div class="metric-value">${formatNumber(setup.entryPrice, 4)}</div></div>
        <div class="metric-card"><div class="metric-label">Stop loss</div><div class="metric-value">${setup.stopLossPrice !== null ? formatNumber(setup.stopLossPrice, 4) : "Not set"}</div></div>
        <div class="metric-card"><div class="metric-label">Target</div><div class="metric-value">${setup.targetPrice !== null ? formatNumber(setup.targetPrice, 4) : "Not set"}</div></div>
        <div class="metric-card"><div class="metric-label">Created</div><div class="metric-value">${formatDateTime(setup.createdAt)}</div></div>
      </div>
      ${setup.rationale ? `<p>${escapeHtml(setup.rationale)}</p>` : ""}
      ${
        setup.source === "AI_ANALYSIS"
          ? `<p class="section-status">AI provenance: ${escapeHtml(setup.aiProviderName || "N/A")} (${escapeHtml(setup.aiModel || "N/A")})${setup.aiAnalysisAsOf ? `, as of ${formatDateTime(setup.aiAnalysisAsOf)}` : ""}</p>`
          : ""
      }
      ${
        setup.decidedAt
          ? `<p class="section-status">Decision: ${escapeHtml(setup.status)}${setup.decisionNotes ? ` &middot; ${escapeHtml(setup.decisionNotes)}` : ""} (${formatDateTime(setup.decidedAt)})</p>`
          : ""
      }
      ${
        setup.closedAt
          ? `<p class="section-status">Closed: ${setup.exitPrice !== null ? `exit ${formatNumber(setup.exitPrice, 4)}` : ""}${setup.outcome ? ` &middot; ${escapeHtml(setup.outcome)}` : ""}${setup.closureNotes ? ` &middot; ${escapeHtml(setup.closureNotes)}` : ""} (${formatDateTime(setup.closedAt)})</p>`
          : ""
      }
      <div class="ts-actions-section"></div>
      <div class="ts-risk-section"></div>
      <div class="ts-backtest-section"></div>
      <div class="ts-paper-section"></div>`;

    const card = body.closest(".profile-card");
    const id = setup.id;
    renderLifecycleActions(body, setup, card, id);
    renderRiskSection(body, setup);
    renderBacktestSection(body, setup, card, id);
    renderPaperTradingSection(body, setup, card, id);
  }

  function renderList() {
    if (!alive) return;
    renderStateBlock(listEl, listSection.value, {
      loadingLines: 4,
      emptyText: "",
      onRetry: () => loadSetups(),
      renderContent: (setups) => {
        if (!setups || setups.length === 0) {
          listEl.innerHTML = `<div class="card empty-state"><p>No trade setups yet. Save one above, or from an AI Market Analysis proposal.</p></div>`;
          return;
        }
        listEl.innerHTML = `
          <div class="profile-grid">
            ${setups
              .map(
                (setup) => `
              <div class="profile-card" data-id="${setup.id}">
                <div class="profile-card-header">
                  <span class="profile-name">${escapeHtml(setup.symbol)} &middot; ${escapeHtml(setup.direction)}</span>
                  <span class="setup-status-badge signal-badge ${STATUS_COLOR[setup.status] || "neutral"}"><span class="signal-state">${escapeHtml(setup.status)}</span></span>
                </div>
                <p class="section-status">
                  ${escapeHtml(setup.assetType)} ${provenanceBadge(setup)}
                  &middot; Entry ${formatNumber(setup.entryPrice, 4)}
                  &middot; Created ${formatDateTime(setup.createdAt)}
                </p>
                <details class="ai-details">
                  <summary>View details</summary>
                  <div class="ai-details-body"></div>
                </details>
              </div>`
              )
              .join("")}
          </div>`;

        listEl.querySelectorAll(".profile-card").forEach((card) => {
          const id = card.dataset.id;
          const setup = setups.find((s) => s.id === id);
          const details = card.querySelector(".ai-details");
          const body = card.querySelector(".ai-details-body");
          let loaded = false;
          details.addEventListener("toggle", async () => {
            if (!details.open || loaded) return;
            loaded = true;
            body.innerHTML = `<p class="section-status">Loading...</p>`;
            try {
              const full = await getTradeSetup(id);
              if (!alive) return;
              renderDetailBody(body, full);
            } catch (error) {
              if (!alive) return;
              body.innerHTML = `<p class="section-status">${escapeHtml(describeTradeSetupError(error))}</p>`;
              loaded = false;
            }
          });
          void setup; // summary row already rendered above; detail is fetched fresh on first expand.
        });
      },
    });
  }

  loadSetups();

  return () => {
    alive = false;
  };
}
