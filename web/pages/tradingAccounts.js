import {
  createTradingAccountProfile,
  listTradingAccountProfiles,
  updateTradingAccountProfile,
  archiveTradingAccountProfile,
  ApiError,
} from "../api.js";
import { createSectionState, loadInto, renderStateBlock, skeletonHtml } from "../components/stateBlock.js";
import { escapeHtml, formatNumber, formatPercent, formatDateTime } from "../format.js";

const PLATFORM_LABELS = {
  METATRADER_4: "MetaTrader 4",
  METATRADER_5: "MetaTrader 5",
  CTRADER: "cTrader",
  TRADINGVIEW: "TradingView",
  INTERACTIVE_BROKERS: "Interactive Brokers",
  TD_AMERITRADE: "TD Ameritrade",
  OANDA: "OANDA",
  OTHER: "Other",
};

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

export function mount(container) {
  let alive = true;
  let busy = false;
  let editingProfileId = null;

  const listSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page trading-accounts-page">
      <section class="card">
        <h1>Trading Accounts</h1>
        <p class="section-disclaimer">
          Manually-entered trading account profiles - a place to record an account's balance and
          risk settings so backtests can reuse them. These profiles are <strong>not connected to
          any broker</strong>: the balance shown is only ever what you typed in here, never synced,
          verified, or kept up to date automatically. Orynix has no broker integration of any kind.
        </p>
      </section>

      <section class="card">
        <h2 id="formHeading">Add a profile</h2>
        <form class="backtest-form" novalidate id="profileForm">
          <div class="backtest-form-grid">
            <label class="backtest-field">
              <span>Name</span>
              <input type="text" name="name" autocomplete="off" maxlength="100" placeholder="e.g. Main MT5 account">
            </label>
            <label class="backtest-field">
              <span>Platform</span>
              <select name="platform">
                ${Object.entries(PLATFORM_LABELS)
                  .map(([value, label]) => `<option value="${value}">${label}</option>`)
                  .join("")}
              </select>
            </label>
            <label class="backtest-field">
              <span>Platform details (optional)</span>
              <input type="text" name="platformDetails" autocomplete="off" maxlength="255" placeholder="e.g. Demo-12345">
            </label>
            <label class="backtest-field">
              <span>Account currency</span>
              <input type="text" name="accountCurrency" autocomplete="off" maxlength="3" placeholder="USD" value="USD">
            </label>
          </div>

          <div class="backtest-form-grid">
            <label class="backtest-field">
              <span>Balance (manually entered, not broker-verified)</span>
              <input type="number" name="balance" min="0" step="any" value="10000">
            </label>
            <label class="backtest-field">
              <span>Risk per trade (%)</span>
              <input type="number" name="riskPercentPerTrade" min="0" max="100" step="any" placeholder="1.00">
              <small class="backtest-field-hint">Defaults to 1% if left blank. Used by the risk calculator and when selected for risk-based backtest sizing.</small>
            </label>
            <label class="backtest-field">
              <span>Max daily loss % (optional)</span>
              <input type="number" name="maxDailyLossPercent" min="0" max="100" step="any" placeholder="e.g. 5">
              <small class="backtest-field-hint">Stored and validated, but not yet enforced in backtests - see below.</small>
            </label>
            <label class="backtest-field">
              <span>Max concurrent positions (optional)</span>
              <input type="number" name="maxConcurrentPositions" min="1" step="1" placeholder="e.g. 3">
              <small class="backtest-field-hint">Stored and validated, but not actively enforced - the backtesting engine only ever holds one position at a time.</small>
            </label>
          </div>

          <p class="backtest-validation-error" hidden></p>

          <div class="backtest-form-actions">
            <button type="submit" class="auth-primary-btn" id="profileSubmitBtn">Create profile</button>
            <button type="button" class="auth-secondary-btn" id="profileCancelBtn" hidden>Cancel edit</button>
          </div>
        </form>
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Your profiles</h2>
          <button type="button" class="refresh-btn" id="profilesRefreshBtn">Refresh</button>
        </div>
        <div id="profilesList"></div>
      </section>
    </div>`;

  const form = container.querySelector("#profileForm");
  const formHeading = container.querySelector("#formHeading");
  const submitBtn = container.querySelector("#profileSubmitBtn");
  const cancelBtn = container.querySelector("#profileCancelBtn");
  const validationErrorEl = form.querySelector(".backtest-validation-error");
  const listEl = container.querySelector("#profilesList");
  const refreshBtn = container.querySelector("#profilesRefreshBtn");

  function readForm() {
    return {
      name: form.querySelector('[name="name"]').value.trim(),
      platform: form.querySelector('[name="platform"]').value,
      platformDetails: form.querySelector('[name="platformDetails"]').value.trim(),
      accountCurrency: form.querySelector('[name="accountCurrency"]').value.trim().toUpperCase(),
      balance: form.querySelector('[name="balance"]').value,
      riskPercentPerTrade: form.querySelector('[name="riskPercentPerTrade"]').value,
      maxDailyLossPercent: form.querySelector('[name="maxDailyLossPercent"]').value,
      maxConcurrentPositions: form.querySelector('[name="maxConcurrentPositions"]').value,
    };
  }

  function validate(values) {
    const errors = [];
    if (!values.name) errors.push("Enter a name for this profile.");
    if (!/^[A-Z]{3}$/.test(values.accountCurrency)) errors.push("Account currency must be a 3-letter code, e.g. USD.");
    if (!(Number(values.balance) >= 0)) errors.push("Balance must be zero or a positive number.");
    if (values.riskPercentPerTrade) {
      const risk = Number(values.riskPercentPerTrade);
      if (!(risk > 0) || !(risk <= 100)) errors.push("Risk per trade must be a number between 0 and 100.");
    }
    if (values.maxDailyLossPercent) {
      const loss = Number(values.maxDailyLossPercent);
      if (!(loss > 0) || !(loss <= 100)) errors.push("Max daily loss % must be a number between 0 and 100.");
    }
    if (values.maxConcurrentPositions) {
      const positions = Number(values.maxConcurrentPositions);
      if (!(positions >= 1) || !Number.isInteger(positions)) errors.push("Max concurrent positions must be a whole number of at least 1.");
    }
    return errors;
  }

  function buildRequest(values) {
    return {
      name: values.name,
      platform: values.platform,
      platformDetails: values.platformDetails || null,
      accountCurrency: values.accountCurrency,
      balance: Number(values.balance),
      riskPercentPerTrade: values.riskPercentPerTrade ? Number(values.riskPercentPerTrade) : null,
      maxDailyLossPercent: values.maxDailyLossPercent ? Number(values.maxDailyLossPercent) : null,
      maxConcurrentPositions: values.maxConcurrentPositions ? Number(values.maxConcurrentPositions) : null,
    };
  }

  function resetForm() {
    editingProfileId = null;
    form.reset();
    form.querySelector('[name="accountCurrency"]').value = "USD";
    formHeading.textContent = "Add a profile";
    submitBtn.textContent = "Create profile";
    cancelBtn.hidden = true;
    validationErrorEl.hidden = true;
  }

  function startEdit(profile) {
    editingProfileId = profile.id;
    form.querySelector('[name="name"]').value = profile.name;
    form.querySelector('[name="platform"]').value = profile.platform;
    form.querySelector('[name="platformDetails"]').value = profile.platformDetails || "";
    form.querySelector('[name="accountCurrency"]').value = profile.accountCurrency;
    form.querySelector('[name="balance"]').value = profile.balance;
    form.querySelector('[name="riskPercentPerTrade"]').value = profile.riskPercentPerTrade;
    form.querySelector('[name="maxDailyLossPercent"]').value = profile.maxDailyLossPercent ?? "";
    form.querySelector('[name="maxConcurrentPositions"]').value = profile.maxConcurrentPositions ?? "";
    formHeading.textContent = `Editing "${profile.name}"`;
    submitBtn.textContent = "Save changes";
    cancelBtn.hidden = false;
    validationErrorEl.hidden = true;
    formHeading.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  cancelBtn.addEventListener("click", resetForm);

  function describeSubmitError(error) {
    const status = error instanceof ApiError ? error.status : null;
    if (status === 400) return error.message || "That profile was rejected as invalid.";
    if (status === 404) return "This profile no longer exists.";
    if (status === 0) return "Could not reach the backend. Is it running?";
    return error?.message || "Unexpected error while saving this profile.";
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (busy) return;

    const values = readForm();
    const errors = validate(values);
    if (errors.length > 0) {
      validationErrorEl.innerHTML = errors.map((e) => escapeHtml(e)).join("<br>");
      validationErrorEl.hidden = false;
      return;
    }
    validationErrorEl.hidden = true;

    const request = buildRequest(values);
    busy = true;
    submitBtn.disabled = true;
    try {
      if (editingProfileId) {
        await updateTradingAccountProfile(editingProfileId, request);
      } else {
        await createTradingAccountProfile(request);
      }
      if (!alive) return;
      resetForm();
      loadProfiles();
    } catch (error) {
      if (!alive) return;
      validationErrorEl.textContent = describeSubmitError(error);
      validationErrorEl.hidden = false;
    } finally {
      if (alive) {
        busy = false;
        submitBtn.disabled = false;
      }
    }
  }

  form.addEventListener("submit", onSubmit);

  async function onArchive(profile) {
    if (!window.confirm(`Archive "${profile.name}"? It will no longer appear in this list or be selectable for new backtests.`)) {
      return;
    }
    try {
      await archiveTradingAccountProfile(profile.id);
      if (!alive) return;
      if (editingProfileId === profile.id) resetForm();
      loadProfiles();
    } catch (error) {
      if (!alive) return;
      window.alert(describeSubmitError(error));
    }
  }

  function renderProfiles() {
    if (!alive) return;
    renderStateBlock(listEl, listSection.value, {
      loadingLines: 4,
      emptyText: "",
      onRetry: () => loadProfiles(),
      renderContent: (profiles) => {
        if (!profiles || profiles.length === 0) {
          listEl.innerHTML = `
            <div class="card empty-state">
              <p>No trading account profiles yet. Add one above to seed backtests with a saved balance and risk percentage.</p>
            </div>`;
          return;
        }
        listEl.innerHTML = `
          <div class="profile-grid">
            ${profiles
              .map(
                (profile) => `
              <div class="profile-card" data-id="${profile.id}">
                <div class="profile-card-header">
                  <span class="profile-name">${escapeHtml(profile.name)}</span>
                  <span class="profile-badge">Not broker-verified</span>
                </div>
                <p class="section-status">${escapeHtml(PLATFORM_LABELS[profile.platform] || profile.platform)}${profile.platformDetails ? ` &middot; ${escapeHtml(profile.platformDetails)}` : ""}</p>
                <div class="profile-metrics">
                  <div><span>Balance</span><strong>${escapeHtml(formatBalance(profile.balance, profile.accountCurrency))}</strong></div>
                  <div><span>Risk per trade</span><strong>${formatPercent(profile.riskPercentPerTrade)}</strong></div>
                  <div><span>Max daily loss</span><strong>${profile.maxDailyLossPercent !== null ? formatPercent(profile.maxDailyLossPercent) : "Not set"}</strong>${profile.maxDailyLossPercent !== null ? `<small>${profile.maxDailyLossPercentEnforcedInBacktests ? "Enforced in backtests" : "Not yet enforced in backtests"}</small>` : ""}</div>
                  <div><span>Max concurrent positions</span><strong>${profile.maxConcurrentPositions !== null ? profile.maxConcurrentPositions : "Not set"}</strong>${profile.maxConcurrentPositions !== null ? `<small>${profile.maxConcurrentPositionsEnforcedInBacktests ? "Enforced in backtests" : "Not enforced - the engine only ever holds one position"}</small>` : ""}</div>
                </div>
                <p class="section-asof">Updated ${formatDateTime(profile.updatedAt)}</p>
                <div class="profile-actions">
                  <button type="button" class="auth-secondary-btn profile-edit-btn">Edit</button>
                  <button type="button" class="auth-secondary-btn profile-archive-btn">Archive</button>
                </div>
              </div>`
              )
              .join("")}
          </div>`;
        listEl.querySelectorAll(".profile-card").forEach((card) => {
          const id = card.dataset.id;
          const profile = profiles.find((p) => p.id === id);
          card.querySelector(".profile-edit-btn").addEventListener("click", () => startEdit(profile));
          card.querySelector(".profile-archive-btn").addEventListener("click", () => onArchive(profile));
        });
      },
    });
  }

  async function loadProfiles() {
    await loadInto(listSection, renderProfiles, () => listTradingAccountProfiles());
  }

  refreshBtn.addEventListener("click", () => loadProfiles());

  loadProfiles();

  return () => {
    alive = false;
  };
}
