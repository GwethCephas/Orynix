import { createBacktest, listBacktests, getBacktest, getBacktestTrades, getCandles, listTradingAccountProfiles, ApiError } from "../api.js";
import { getCurrentSymbol, withCache } from "../state.js";
import { createSectionState, loadInto, renderStateBlock, skeletonHtml } from "../components/stateBlock.js";
import { metricCardHtml } from "../components/metricCard.js";
import {
  escapeHtml,
  formatDate,
  formatDateTime,
  formatNumber,
  formatPercent,
  isValidSymbol,
  isForexSymbol,
  deriveAssetHeader,
} from "../format.js";
import { createEquityCurveChart } from "../equityCurveChart.js";

const STRATEGY_LABELS = {
  MOVING_AVERAGE_CROSSOVER: "Moving Average Crossover",
  RSI_THRESHOLD: "RSI Threshold",
  MACD_CROSSOVER: "MACD Crossover",
  BOLLINGER_BANDS: "Bollinger Bands Mean Reversion",
  DONCHIAN_CHANNEL_BREAKOUT: "Donchian Channel Breakout",
  ADX_TREND: "ADX Trend",
};

const EXIT_REASON_LABELS = {
  SIGNAL: "Strategy signal",
  END_OF_PERIOD: "End of backtest period",
  ATR_STOP: "ATR stop-loss",
};

const FOREX_PATTERN = /^([A-Z]{3})\/([A-Z]{3})$/;

// This page's performance figures can legitimately be in a non-USD account currency (e.g. a
// USD/JPY backtest must use JPY per the backend's validateAccountCurrency rule) - the rest of the
// app's formatCurrency is hardcoded to a "$" prefix because every other page is implicitly
// USD-denominated stock data. Intl.NumberFormat is a standard browser API, not a new dependency.
function formatMoney(value, currency) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency: currency || "USD" }).format(Number(value));
  } catch {
    return `${Number(value).toFixed(2)} ${currency || ""}`.trim();
  }
}

function strategyDescription(run) {
  switch (run.strategyType) {
    case "MOVING_AVERAGE_CROSSOVER":
      return `Fast ${run.fastPeriod} / Slow ${run.slowPeriod}`;
    case "RSI_THRESHOLD":
      return `RSI(${run.rsiPeriod}), oversold ${formatNumber(run.oversoldThreshold, 0)} / overbought ${formatNumber(run.overboughtThreshold, 0)}`;
    case "MACD_CROSSOVER":
      return `Fast EMA ${run.fastPeriod} / Slow EMA ${run.slowPeriod} / Signal EMA ${run.signalPeriod}`;
    case "BOLLINGER_BANDS":
      return `Lookback ${run.bollingerPeriod}, &times;${formatNumber(run.bollingerStdDevMultiplier, 2)} std dev`;
    case "DONCHIAN_CHANNEL_BREAKOUT":
      return `Entry lookback ${run.donchianEntryPeriod} / Exit lookback ${run.donchianExitPeriod}`;
    case "ADX_TREND":
      return `ADX(${run.adxPeriod}), threshold ${formatNumber(run.adxThreshold, 1)}`;
    default:
      return "";
  }
}

function pnlClass(value) {
  if (value === null || value === undefined) return "neutral";
  const num = Number(value);
  if (num > 0) return "positive";
  if (num < 0) return "negative";
  return "neutral";
}

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

function monthsAgoIsoDate(months) {
  const d = new Date();
  d.setMonth(d.getMonth() - months);
  return d.toISOString().slice(0, 10);
}

export function mount(container) {
  let alive = true;
  let busy = false;

  const runSection = { value: createSectionState() };
  const detailSection = { value: createSectionState() };
  const historySection = { value: createSectionState() };
  let sizingCheck = null; // { symbol, price, asOfDate } | { symbol, unavailable: true } | null
  let equityChart = null;
  let profiles = [];

  const initialSymbol = getCurrentSymbol() || "";
  const initialAssetClass = isForexSymbol(initialSymbol) ? "FOREX" : "STOCK";

  container.innerHTML = `
    <div class="page backtests-page">
      <section class="card">
        <h1>Backtesting</h1>
        <p class="section-disclaimer">
          Replay one of six rule-based strategies against real historical daily candles for a stock
          or forex pair, with configurable commission and slippage. This is a
          deterministic historical simulation, not a prediction - backtest performance does not
          guarantee future results.
        </p>
      </section>

      <section class="card">
        <h2>Configure a backtest</h2>
        <form class="backtest-form" novalidate>
          <div class="backtest-form-grid">
            <div class="backtest-field">
              <span>Asset class</span>
              <div class="backtest-asset-toggle" role="group">
                <button type="button" class="backtest-toggle-btn" data-asset="STOCK">Stocks</button>
                <button type="button" class="backtest-toggle-btn" data-asset="FOREX">Forex</button>
              </div>
            </div>

            <label class="backtest-field">
              <span>Symbol</span>
              <input type="text" name="symbol" autocomplete="off" value="${escapeHtml(initialSymbol)}">
              <small class="backtest-field-hint" data-hint="symbol"></small>
            </label>

            <label class="backtest-field">
              <span>From date</span>
              <input type="date" name="fromDate" value="${monthsAgoIsoDate(6)}">
            </label>

            <label class="backtest-field">
              <span>To date</span>
              <input type="date" name="toDate" value="${todayIsoDate()}">
            </label>

            <label class="backtest-field">
              <span>Strategy</span>
              <select name="strategyType">
                <option value="MOVING_AVERAGE_CROSSOVER">Moving Average Crossover</option>
                <option value="RSI_THRESHOLD">RSI Threshold</option>
                <option value="MACD_CROSSOVER">MACD Crossover</option>
                <option value="BOLLINGER_BANDS">Bollinger Bands Mean Reversion</option>
                <option value="DONCHIAN_CHANNEL_BREAKOUT">Donchian Channel Breakout</option>
                <option value="ADX_TREND">ADX Trend</option>
              </select>
            </label>
          </div>

          <div class="backtest-form-grid" data-strategy-group="MOVING_AVERAGE_CROSSOVER">
            <label class="backtest-field">
              <span>Fast period</span>
              <input type="number" name="fastPeriod" min="1" step="1" value="10">
            </label>
            <label class="backtest-field">
              <span>Slow period</span>
              <input type="number" name="slowPeriod" min="2" step="1" value="30">
            </label>
          </div>

          <div class="backtest-form-grid" data-strategy-group="RSI_THRESHOLD" hidden>
            <label class="backtest-field">
              <span>RSI period</span>
              <input type="number" name="rsiPeriod" min="1" step="1" value="14">
            </label>
            <label class="backtest-field">
              <span>Oversold threshold</span>
              <input type="number" name="oversoldThreshold" min="0" max="100" step="1" value="30">
            </label>
            <label class="backtest-field">
              <span>Overbought threshold</span>
              <input type="number" name="overboughtThreshold" min="0" max="100" step="1" value="70">
            </label>
          </div>

          <div class="backtest-form-grid" data-strategy-group="MACD_CROSSOVER" hidden>
            <label class="backtest-field">
              <span>Fast EMA period</span>
              <input type="number" name="macdFastPeriod" min="1" step="1" value="12">
            </label>
            <label class="backtest-field">
              <span>Slow EMA period</span>
              <input type="number" name="macdSlowPeriod" min="2" step="1" value="26">
            </label>
            <label class="backtest-field">
              <span>Signal EMA period</span>
              <input type="number" name="signalPeriod" min="1" step="1" value="9">
            </label>
          </div>

          <div class="backtest-form-grid" data-strategy-group="BOLLINGER_BANDS" hidden>
            <label class="backtest-field">
              <span>Lookback period</span>
              <input type="number" name="bollingerPeriod" min="2" step="1" value="20">
            </label>
            <label class="backtest-field">
              <span>Standard-deviation multiplier</span>
              <input type="number" name="bollingerStdDevMultiplier" min="0" step="any" value="2.0">
            </label>
          </div>

          <div class="backtest-form-grid" data-strategy-group="DONCHIAN_CHANNEL_BREAKOUT" hidden>
            <label class="backtest-field">
              <span>Entry lookback period</span>
              <input type="number" name="donchianEntryPeriod" min="1" step="1" value="20">
            </label>
            <label class="backtest-field">
              <span>Exit lookback period</span>
              <input type="number" name="donchianExitPeriod" min="1" step="1" value="10">
            </label>
          </div>

          <div class="backtest-form-grid" data-strategy-group="ADX_TREND" hidden>
            <label class="backtest-field">
              <span>ADX period</span>
              <input type="number" name="adxPeriod" min="1" step="1" value="14">
            </label>
            <label class="backtest-field">
              <span>Trend-strength threshold</span>
              <input type="number" name="adxThreshold" min="0" max="100" step="any" value="25">
            </label>
          </div>

          <h3 class="backtest-subheading">Capital &amp; costs</h3>
          <div class="backtest-form-grid">
            <label class="backtest-field">
              <span>Trading account profile (optional)</span>
              <select name="accountProfileId">
                <option value="">None - enter initial capital manually</option>
              </select>
              <small class="backtest-field-hint" data-hint="profile">Manually-entered, not broker-verified - see the Trading Accounts page.</small>
            </label>
            <label class="backtest-field">
              <span>Initial capital</span>
              <input type="number" name="initialCapital" min="0" step="any" value="10000">
              <small class="backtest-field-hint" data-hint="initialCapital"></small>
            </label>
            <label class="backtest-field">
              <span>Account currency</span>
              <input type="text" name="accountCurrency" readonly>
              <small class="backtest-field-hint" data-hint="currency"></small>
            </label>
            <label class="backtest-field">
              <span>Commission per order</span>
              <input type="number" name="commissionPerOrder" min="0" step="any" value="1.00">
            </label>
            <label class="backtest-field">
              <span>Slippage (basis points)</span>
              <input type="number" name="slippageBps" min="0" step="1" value="5">
            </label>
          </div>

          <h3 class="backtest-subheading">Position sizing &amp; risk management</h3>
          <div class="backtest-form-grid">
            <label class="backtest-field">
              <span>Position sizing</span>
              <select name="positionSizingType">
                <option value="FIXED_QUANTITY">Fixed quantity</option>
                <option value="RISK_BASED_ATR">Risk-based (ATR)</option>
              </select>
            </label>
          </div>

          <div class="backtest-form-grid" data-sizing-group="FIXED_QUANTITY">
            <label class="backtest-field">
              <span data-label="quantity">Quantity (shares)</span>
              <input type="number" name="quantity" min="0" step="any" value="10">
            </label>
          </div>

          <div class="backtest-form-grid" data-sizing-group="RISK_BASED_ATR" hidden>
            <label class="backtest-field">
              <span>Risk per trade (%)</span>
              <input type="number" name="riskPercentPerTrade" min="0" max="100" step="any" value="1.0">
            </label>
          </div>

          <div class="backtest-form-grid">
            <label class="backtest-field backtest-field-checkbox">
              <input type="checkbox" name="atrRiskManagementEnabled">
              <span>Enable ATR stop-loss</span>
            </label>
          </div>

          <div class="backtest-form-grid" data-risk-group="ATR" hidden>
            <label class="backtest-field">
              <span>ATR period</span>
              <input type="number" name="atrPeriod" min="1" step="1" value="14">
            </label>
            <label class="backtest-field">
              <span>Initial stop multiplier (&times; ATR)</span>
              <input type="number" name="atrInitialStopMultiplier" min="0" step="any" value="2.0">
            </label>
            <label class="backtest-field backtest-field-checkbox">
              <input type="checkbox" name="atrTrailingStopEnabled">
              <span>Enable trailing stop</span>
            </label>
            <label class="backtest-field" data-trailing-group hidden>
              <span>Trailing stop multiplier (&times; ATR)</span>
              <input type="number" name="atrTrailingStopMultiplier" min="0" step="any" value="2.0">
            </label>
          </div>

          <p class="backtest-validation-error" hidden></p>

          <div class="backtest-form-actions">
            <button type="submit" class="auth-primary-btn backtest-submit-btn">Run Backtest</button>
          </div>
        </form>
      </section>

      <section class="card" id="sizingPanel"></section>

      <section class="card" id="statusPanel" hidden></section>

      <section id="resultsPanel"></section>

      <section class="card">
        <div class="section-header">
          <h2>Previous runs</h2>
          <button type="button" class="refresh-btn" id="historyRefreshBtn">Refresh</button>
        </div>
        <div id="historyPanel"></div>
      </section>
    </div>`;

  const form = container.querySelector(".backtest-form");
  const symbolInput = form.querySelector('[name="symbol"]');
  const assetToggleBtns = form.querySelectorAll(".backtest-toggle-btn");
  const currencyInput = form.querySelector('[name="accountCurrency"]');
  const currencyHint = form.querySelector('[data-hint="currency"]');
  const symbolHint = form.querySelector('[data-hint="symbol"]');
  const profileSelect = form.querySelector('[name="accountProfileId"]');
  const initialCapitalInput = form.querySelector('[name="initialCapital"]');
  const initialCapitalHint = form.querySelector('[data-hint="initialCapital"]');
  const quantityLabel = form.querySelector('[data-label="quantity"]');
  const strategySelect = form.querySelector('[name="strategyType"]');
  const strategyGroups = form.querySelectorAll("[data-strategy-group]");
  const sizingSelect = form.querySelector('[name="positionSizingType"]');
  const sizingGroups = form.querySelectorAll("[data-sizing-group]");
  const atrEnabledCheckbox = form.querySelector('[name="atrRiskManagementEnabled"]');
  const riskGroupEl = form.querySelector('[data-risk-group="ATR"]');
  const trailingCheckbox = form.querySelector('[name="atrTrailingStopEnabled"]');
  const trailingGroupEl = form.querySelector('[data-trailing-group]');
  const validationErrorEl = form.querySelector(".backtest-validation-error");
  const submitBtn = form.querySelector(".backtest-submit-btn");
  const sizingPanelEl = container.querySelector("#sizingPanel");
  const statusPanelEl = container.querySelector("#statusPanel");
  const resultsPanelEl = container.querySelector("#resultsPanel");
  const historyPanelEl = container.querySelector("#historyPanel");
  const historyRefreshBtn = container.querySelector("#historyRefreshBtn");

  let assetClass = initialAssetClass;

  function setAssetClass(next) {
    assetClass = next;
    assetToggleBtns.forEach((btn) => btn.classList.toggle("active", btn.dataset.asset === assetClass));
    if (assetClass === "STOCK") {
      symbolInput.placeholder = "e.g. AAPL";
      symbolHint.textContent = "Stock ticker, e.g. AAPL, MSFT.";
      quantityLabel.textContent = "Quantity (shares)";
    } else {
      symbolInput.placeholder = "e.g. EUR/USD";
      symbolHint.textContent = "Forex pair as BASE/QUOTE, e.g. EUR/USD.";
      quantityLabel.textContent = "Quantity (base-currency units)";
    }
    syncAccountCurrency();
  }

  function syncAccountCurrency() {
    const symbol = symbolInput.value.trim().toUpperCase();
    if (assetClass === "STOCK") {
      currencyInput.value = "USD";
      currencyHint.textContent = "Stock backtests require a USD account currency.";
      return;
    }
    const match = FOREX_PATTERN.exec(symbol);
    if (match) {
      currencyInput.value = match[2];
      currencyHint.textContent = `Forex backtests must use the pair's quote currency (${match[2]}).`;
      quantityLabel.textContent = `Quantity (${match[1]} base-currency units)`;
    } else {
      currencyInput.value = "";
      currencyHint.textContent = "Enter a valid pair (e.g. EUR/USD) to determine the required account currency.";
      quantityLabel.textContent = "Quantity (base-currency units)";
    }
  }

  // ---------- Trading account profile selection ----------
  // A selected profile seeds initialCapital only when the field is left blank (CLAUDE.md Section
  // 19's precedence rule: explicit initialCapital always wins) - this never rewrites a value the
  // user has already typed, only the placeholder/hint shown while the field is empty.
  function syncInitialCapitalHint() {
    const selected = profiles.find((p) => p.id === profileSelect.value);
    if (!selected) {
      initialCapitalInput.placeholder = "10000";
      initialCapitalHint.textContent = "";
      return;
    }
    initialCapitalInput.placeholder = String(selected.balance);
    initialCapitalHint.textContent =
      `Leave blank to use "${selected.name}"'s saved balance (${formatMoney(selected.balance, selected.accountCurrency)}, not broker-verified). ` +
      `Entering a value here overrides the profile's balance for this run only.`;
  }

  async function loadProfiles() {
    try {
      profiles = await listTradingAccountProfiles();
    } catch {
      profiles = [];
    }
    if (!alive) return;
    const previouslySelected = profileSelect.value;
    profileSelect.innerHTML =
      `<option value="">None - enter initial capital manually</option>` +
      profiles
        .map(
          (p) =>
            `<option value="${p.id}">${escapeHtml(p.name)} (${escapeHtml(p.accountCurrency)} ${formatNumber(p.balance, 2)})</option>`
        )
        .join("");
    if (profiles.some((p) => p.id === previouslySelected)) {
      profileSelect.value = previouslySelected;
    }
    syncInitialCapitalHint();
    renderSizingPanel();
  }

  profileSelect.addEventListener("change", () => {
    syncInitialCapitalHint();
    renderSizingPanel();
  });
  initialCapitalInput.addEventListener("input", syncInitialCapitalHint);

  assetToggleBtns.forEach((btn) => btn.addEventListener("click", () => setAssetClass(btn.dataset.asset)));

  symbolInput.addEventListener("input", () => {
    const value = symbolInput.value.trim().toUpperCase();
    // Reflects the typed symbol's own shape back onto the toggle for convenience - it never
    // rewrites what the user typed, only which toggle button is highlighted.
    if (FOREX_PATTERN.test(value)) {
      setAssetClass("FOREX");
    } else if (/^[A-Z.]{1,10}$/.test(value) && !value.includes("/")) {
      setAssetClass("STOCK");
    } else {
      syncAccountCurrency();
    }
  });

  symbolInput.addEventListener("blur", () => {
    checkPositionSizing();
  });

  strategySelect.addEventListener("change", () => {
    strategyGroups.forEach((group) => {
      group.hidden = group.dataset.strategyGroup !== strategySelect.value;
    });
  });

  function syncSizingVisibility() {
    sizingGroups.forEach((group) => {
      group.hidden = group.dataset.sizingGroup !== sizingSelect.value;
    });
    renderSizingPanel();
  }
  function syncRiskVisibility() {
    riskGroupEl.hidden = !atrEnabledCheckbox.checked;
  }
  function syncTrailingVisibility() {
    trailingGroupEl.hidden = !trailingCheckbox.checked;
  }
  sizingSelect.addEventListener("change", syncSizingVisibility);
  atrEnabledCheckbox.addEventListener("change", syncRiskVisibility);
  trailingCheckbox.addEventListener("change", syncTrailingVisibility);

  form.querySelector('[name="quantity"]').addEventListener("input", renderSizingPanel);
  form.querySelector('[name="initialCapital"]').addEventListener("input", renderSizingPanel);
  form.querySelector('[name="riskPercentPerTrade"]').addEventListener("input", renderSizingPanel);

  setAssetClass(initialAssetClass);
  strategyGroups.forEach((group) => {
    group.hidden = group.dataset.strategyGroup !== strategySelect.value;
  });
  syncSizingVisibility();
  syncRiskVisibility();
  syncTrailingVisibility();
  syncInitialCapitalHint();
  loadProfiles();

  // ---------- Position-sizing preview ----------

  async function checkPositionSizing() {
    const symbol = symbolInput.value.trim().toUpperCase();
    if (!isValidSymbol(symbol)) {
      sizingCheck = null;
      renderSizingPanel();
      return;
    }
    if (sizingCheck && sizingCheck.symbol === symbol && !sizingCheck.pending) {
      renderSizingPanel();
      return;
    }
    sizingCheck = { symbol, pending: true };
    renderSizingPanel();
    try {
      const candles = await withCache(symbol, "candles", () => getCandles(symbol));
      const header = deriveAssetHeader(candles);
      if (!alive) return;
      if (header.latestClose === null) {
        sizingCheck = { symbol, unavailable: true };
      } else {
        sizingCheck = { symbol, price: header.latestClose, asOfDate: header.timestamp };
      }
    } catch {
      if (!alive) return;
      sizingCheck = { symbol, unavailable: true };
    }
    renderSizingPanel();
  }

  function renderSizingPanel() {
    const symbol = symbolInput.value.trim().toUpperCase();
    const quantityRaw = form.querySelector('[name="quantity"]').value;
    const capitalRaw = initialCapitalInput.value;
    const selectedProfile = profiles.find((p) => p.id === profileSelect.value);
    const quantity = Number(quantityRaw);
    // Mirrors the backend's own precedence rule for the preview: blank -> selected profile's
    // balance, otherwise the typed value always wins (CLAUDE.md Section 19).
    const initialCapital = capitalRaw !== "" ? Number(capitalRaw) : selectedProfile ? Number(selectedProfile.balance) : NaN;
    const currency = currencyInput.value || "USD";

    // Risk-based sizing computes the actual quantity per trade from account equity, the ATR at
    // the signal bar, and the configured risk % - there is no single "requested quantity" to
    // preview before the simulation actually runs, unlike FIXED_QUANTITY's notional estimate.
    if (sizingSelect.value === "RISK_BASED_ATR") {
      sizingPanelEl.innerHTML = `
        <h2>Position sizing</h2>
        <p class="section-status">
          Position size is computed per trade from account equity, the ATR value at each entry
          signal, and the configured risk percentage - it cannot be previewed before running the
          backtest.
        </p>`;
      return;
    }

    if (!isValidSymbol(symbol)) {
      sizingPanelEl.innerHTML = `
        <h2>Position sizing</h2>
        <p class="section-status">Enter a valid symbol to preview the estimated capital requirement.</p>`;
      return;
    }

    const unitsLabel = assetClass === "FOREX" ? `${(FOREX_PATTERN.exec(symbol) || [])[1] || "base-currency"} units` : "shares";

    if (!sizingCheck || sizingCheck.symbol !== symbol || sizingCheck.pending) {
      sizingPanelEl.innerHTML = `
        <h2>Position sizing</h2>
        <div class="metric-grid">
          ${metricCardHtml({ label: "Requested quantity", value: `${formatNumber(quantity, 2)} ${unitsLabel}` })}
          ${metricCardHtml({ label: "Initial capital", value: formatMoney(initialCapital, currency) })}
        </div>
        <p class="section-status">${sizingCheck && sizingCheck.pending ? "Checking a reference price..." : "Looking up a reference price..."}</p>`;
      return;
    }

    if (sizingCheck.unavailable) {
      sizingPanelEl.innerHTML = `
        <h2>Position sizing</h2>
        <div class="metric-grid">
          ${metricCardHtml({ label: "Requested quantity", value: `${formatNumber(quantity, 2)} ${unitsLabel}` })}
          ${metricCardHtml({ label: "Initial capital", value: formatMoney(initialCapital, currency) })}
          ${metricCardHtml({ label: "Account currency", value: escapeHtml(currency) })}
        </div>
        <p class="section-status">Estimated capital requirement unavailable - could not retrieve a reference price for ${escapeHtml(symbol)}.</p>`;
      return;
    }

    const notional = quantity * sizingCheck.price;
    const affordable = Number.isFinite(notional) && Number.isFinite(initialCapital) && notional <= initialCapital;

    sizingPanelEl.innerHTML = `
      <h2>Position sizing</h2>
      <div class="metric-grid">
        ${metricCardHtml({ label: "Requested quantity", value: `${formatNumber(quantity, 2)} ${unitsLabel}` })}
        ${metricCardHtml({ label: "Reference price", value: formatMoney(sizingCheck.price, currency), sublabel: `As of ${formatDate(sizingCheck.asOfDate)} (latest daily close)` })}
        ${metricCardHtml({ label: "Estimated notional", value: formatMoney(notional, currency), colorClass: affordable ? "positive" : "negative" })}
        ${metricCardHtml({ label: "Initial capital", value: formatMoney(initialCapital, currency) })}
      </div>
      <p class="backtest-sizing-note ${affordable ? "positive" : "negative"}">
        ${
          affordable
            ? "This position appears affordable against the initial capital entered, before commission and slippage."
            : `This exceeds the initial capital entered by approximately ${formatMoney(notional - initialCapital, currency)}. The backend silently skips any entry it cannot afford - signals may still fire with zero trades executed.`
        }
      </p>
      <p class="section-disclaimer">
        Estimated using the symbol's most recent daily close (not a live quote) and no leverage or
        margin - Orynix does not model forex leverage. This is an approximation; the backend's own
        cash check at each simulated entry is authoritative and may differ once commission,
        slippage, and the actual historical fill price on each signal date are applied.
      </p>`;
  }

  // ---------- Form validation & submission ----------

  function readForm() {
    return {
      symbol: symbolInput.value.trim().toUpperCase(),
      fromDate: form.querySelector('[name="fromDate"]').value,
      toDate: form.querySelector('[name="toDate"]').value,
      strategyType: strategySelect.value,
      fastPeriod: form.querySelector('[name="fastPeriod"]').value,
      slowPeriod: form.querySelector('[name="slowPeriod"]').value,
      rsiPeriod: form.querySelector('[name="rsiPeriod"]').value,
      oversoldThreshold: form.querySelector('[name="oversoldThreshold"]').value,
      overboughtThreshold: form.querySelector('[name="overboughtThreshold"]').value,
      macdFastPeriod: form.querySelector('[name="macdFastPeriod"]').value,
      macdSlowPeriod: form.querySelector('[name="macdSlowPeriod"]').value,
      signalPeriod: form.querySelector('[name="signalPeriod"]').value,
      bollingerPeriod: form.querySelector('[name="bollingerPeriod"]').value,
      bollingerStdDevMultiplier: form.querySelector('[name="bollingerStdDevMultiplier"]').value,
      donchianEntryPeriod: form.querySelector('[name="donchianEntryPeriod"]').value,
      donchianExitPeriod: form.querySelector('[name="donchianExitPeriod"]').value,
      adxPeriod: form.querySelector('[name="adxPeriod"]').value,
      adxThreshold: form.querySelector('[name="adxThreshold"]').value,
      accountProfileId: profileSelect.value,
      initialCapital: initialCapitalInput.value,
      accountCurrency: currencyInput.value,
      positionSizingType: sizingSelect.value,
      quantity: form.querySelector('[name="quantity"]').value,
      riskPercentPerTrade: form.querySelector('[name="riskPercentPerTrade"]').value,
      commissionPerOrder: form.querySelector('[name="commissionPerOrder"]').value,
      slippageBps: form.querySelector('[name="slippageBps"]').value,
      atrRiskManagementEnabled: atrEnabledCheckbox.checked,
      atrPeriod: form.querySelector('[name="atrPeriod"]').value,
      atrInitialStopMultiplier: form.querySelector('[name="atrInitialStopMultiplier"]').value,
      atrTrailingStopEnabled: trailingCheckbox.checked,
      atrTrailingStopMultiplier: form.querySelector('[name="atrTrailingStopMultiplier"]').value,
    };
  }

  function validate(values) {
    const errors = [];
    if (!isValidSymbol(values.symbol)) {
      errors.push("Enter a valid stock symbol (e.g. AAPL) or forex pair (e.g. EUR/USD).");
    }
    if (!values.fromDate || !values.toDate) {
      errors.push("Enter both a start and end date.");
    } else if (values.fromDate >= values.toDate) {
      errors.push("The start date must be before the end date.");
    } else if (values.toDate > todayIsoDate()) {
      errors.push("The end date cannot be in the future - there is no usable historical data beyond today.");
    }
    if (values.strategyType === "MOVING_AVERAGE_CROSSOVER") {
      const fast = Number(values.fastPeriod);
      const slow = Number(values.slowPeriod);
      if (!(fast > 0) || !(slow > 0)) errors.push("Fast and slow periods must be positive numbers.");
      else if (fast >= slow) errors.push("The fast period must be smaller than the slow period.");
    } else if (values.strategyType === "RSI_THRESHOLD") {
      const rsi = Number(values.rsiPeriod);
      const oversold = Number(values.oversoldThreshold);
      const overbought = Number(values.overboughtThreshold);
      if (!(rsi > 0)) errors.push("The RSI period must be a positive number.");
      if (!(oversold >= 0) || !(overbought >= 0)) errors.push("Thresholds must be numbers.");
      else if (oversold >= overbought) errors.push("The oversold threshold must be lower than the overbought threshold.");
    } else if (values.strategyType === "MACD_CROSSOVER") {
      const fast = Number(values.macdFastPeriod);
      const slow = Number(values.macdSlowPeriod);
      const signal = Number(values.signalPeriod);
      if (!(fast > 0) || !(slow > 0) || !(signal > 0)) errors.push("Fast, slow, and signal EMA periods must be positive numbers.");
      else if (fast >= slow) errors.push("The fast EMA period must be smaller than the slow EMA period.");
    } else if (values.strategyType === "BOLLINGER_BANDS") {
      const period = Number(values.bollingerPeriod);
      const multiplier = Number(values.bollingerStdDevMultiplier);
      if (!(period > 0)) errors.push("The lookback period must be a positive number.");
      if (!(multiplier > 0)) errors.push("The standard-deviation multiplier must be a positive number.");
    } else if (values.strategyType === "DONCHIAN_CHANNEL_BREAKOUT") {
      const entryLookback = Number(values.donchianEntryPeriod);
      const exitLookback = Number(values.donchianExitPeriod);
      if (!(entryLookback > 0) || !(exitLookback > 0)) {
        errors.push("Entry and exit lookback periods must be positive numbers.");
      }
    } else if (values.strategyType === "ADX_TREND") {
      const period = Number(values.adxPeriod);
      const threshold = Number(values.adxThreshold);
      if (!(period > 0)) errors.push("The ADX period must be a positive number.");
      if (!(threshold >= 0) || !(threshold <= 100)) errors.push("The trend-strength threshold must be between 0 and 100.");
    }
    if (!values.accountCurrency) {
      errors.push("Enter a valid symbol first so the required account currency can be determined.");
    }
    // Required unless a trading account profile is selected to seed it (CLAUDE.md Section 19) -
    // when a value IS entered, it always overrides the profile and must still be positive.
    if (values.initialCapital !== "") {
      if (!(Number(values.initialCapital) > 0)) errors.push("Initial capital must be a positive number.");
    } else if (!values.accountProfileId) {
      errors.push("Enter an initial capital, or select a trading account profile to supply one.");
    }
    if (values.positionSizingType === "FIXED_QUANTITY") {
      if (!(Number(values.quantity) > 0)) errors.push("Quantity must be a positive number.");
    } else {
      const riskPct = Number(values.riskPercentPerTrade);
      if (!(riskPct > 0) || !(riskPct <= 100)) errors.push("Risk per trade must be a number between 0 and 100.");
      if (!values.atrRiskManagementEnabled) {
        errors.push("ATR stop-loss must be enabled when using risk-based position sizing.");
      }
    }
    if (values.atrRiskManagementEnabled) {
      if (!(Number(values.atrPeriod) > 0)) errors.push("ATR period must be a positive number.");
      if (!(Number(values.atrInitialStopMultiplier) > 0)) errors.push("Initial stop multiplier must be a positive number.");
      if (values.atrTrailingStopEnabled && !(Number(values.atrTrailingStopMultiplier) > 0)) {
        errors.push("Trailing stop multiplier must be a positive number.");
      }
    }
    if (!(Number(values.commissionPerOrder) >= 0)) errors.push("Commission per order cannot be negative.");
    if (!(Number(values.slippageBps) >= 0) || !Number.isInteger(Number(values.slippageBps))) {
      errors.push("Slippage must be a non-negative whole number of basis points.");
    }
    return errors;
  }

  function buildRequest(values) {
    // Every strategy's branch explicitly nulls out every field it does not own - the backend
    // rejects a request carrying another strategy's fields (CLAUDE.md Section 18), so this must
    // never leave a previously-selected strategy's values sitting in the outgoing request.
    const EMPTY_STRATEGY_FIELDS = {
      fastPeriod: null,
      slowPeriod: null,
      rsiPeriod: null,
      oversoldThreshold: null,
      overboughtThreshold: null,
      signalPeriod: null,
      bollingerPeriod: null,
      bollingerStdDevMultiplier: null,
      donchianEntryPeriod: null,
      donchianExitPeriod: null,
      adxPeriod: null,
      adxThreshold: null,
    };
    let strategyFields;
    if (values.strategyType === "MOVING_AVERAGE_CROSSOVER") {
      strategyFields = { ...EMPTY_STRATEGY_FIELDS, fastPeriod: Number(values.fastPeriod), slowPeriod: Number(values.slowPeriod) };
    } else if (values.strategyType === "RSI_THRESHOLD") {
      strategyFields = {
        ...EMPTY_STRATEGY_FIELDS,
        rsiPeriod: Number(values.rsiPeriod),
        oversoldThreshold: Number(values.oversoldThreshold),
        overboughtThreshold: Number(values.overboughtThreshold),
      };
    } else if (values.strategyType === "MACD_CROSSOVER") {
      strategyFields = {
        ...EMPTY_STRATEGY_FIELDS,
        fastPeriod: Number(values.macdFastPeriod),
        slowPeriod: Number(values.macdSlowPeriod),
        signalPeriod: Number(values.signalPeriod),
      };
    } else if (values.strategyType === "BOLLINGER_BANDS") {
      strategyFields = {
        ...EMPTY_STRATEGY_FIELDS,
        bollingerPeriod: Number(values.bollingerPeriod),
        bollingerStdDevMultiplier: Number(values.bollingerStdDevMultiplier),
      };
    } else if (values.strategyType === "DONCHIAN_CHANNEL_BREAKOUT") {
      strategyFields = {
        ...EMPTY_STRATEGY_FIELDS,
        donchianEntryPeriod: Number(values.donchianEntryPeriod),
        donchianExitPeriod: Number(values.donchianExitPeriod),
      };
    } else {
      strategyFields = {
        ...EMPTY_STRATEGY_FIELDS,
        adxPeriod: Number(values.adxPeriod),
        adxThreshold: Number(values.adxThreshold),
      };
    }
    // Mirrors EMPTY_STRATEGY_FIELDS' explicit-null convention above, applied to the sizing/risk
    // axis: whichever sizing mode or ATR sub-field is not active is sent as an explicit null,
    // never omitted, matching BacktestService's reject-don't-ignore validation (CLAUDE.md
    // Section 18).
    const sizingFields =
      values.positionSizingType === "FIXED_QUANTITY"
        ? { quantity: Number(values.quantity), riskPercentPerTrade: null }
        : { quantity: null, riskPercentPerTrade: Number(values.riskPercentPerTrade) };
    const riskManagementFields = values.atrRiskManagementEnabled
      ? {
          atrRiskManagementEnabled: true,
          atrPeriod: Number(values.atrPeriod),
          atrInitialStopMultiplier: Number(values.atrInitialStopMultiplier),
          atrTrailingStopEnabled: values.atrTrailingStopEnabled,
          atrTrailingStopMultiplier: values.atrTrailingStopEnabled ? Number(values.atrTrailingStopMultiplier) : null,
        }
      : {
          atrRiskManagementEnabled: false,
          atrPeriod: null,
          atrInitialStopMultiplier: null,
          atrTrailingStopEnabled: false,
          atrTrailingStopMultiplier: null,
        };
    return {
      symbol: values.symbol,
      fromDate: values.fromDate,
      toDate: values.toDate,
      strategyType: values.strategyType,
      ...strategyFields,
      accountProfileId: values.accountProfileId || null,
      initialCapital: values.initialCapital !== "" ? Number(values.initialCapital) : null,
      accountCurrency: values.accountCurrency,
      positionSizingType: values.positionSizingType,
      ...sizingFields,
      commissionPerOrder: Number(values.commissionPerOrder),
      slippageBps: Number(values.slippageBps),
      ...riskManagementFields,
    };
  }

  function describeSubmitError(error) {
    const status = error instanceof ApiError ? error.status : null;
    if (status === 422) return `Not enough historical data for this configuration: ${error.message}`;
    if (status === 400) return error.message || "That configuration was rejected as invalid.";
    if (status === 502) return "The market data provider is temporarily unavailable. Please try again shortly.";
    if (status === 0) return "Could not reach the backend. Is it running?";
    return error?.message || "Unexpected error while running the backtest.";
  }

  function setBusy(next) {
    busy = next;
    submitBtn.disabled = busy;
    submitBtn.textContent = busy ? "Running..." : "Run Backtest";
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (busy) return; // guards against duplicate submission from a double click

    const values = readForm();
    const errors = validate(values);
    if (errors.length > 0) {
      validationErrorEl.innerHTML = errors.map((e) => escapeHtml(e)).join("<br>");
      validationErrorEl.hidden = false;
      return;
    }
    validationErrorEl.hidden = true;

    const request = buildRequest(values);
    setBusy(true);
    statusPanelEl.hidden = false;
    statusPanelEl.innerHTML = `<p class="section-status">Submitting backtest configuration for ${escapeHtml(request.symbol)}...</p>`;

    try {
      const run = await createBacktest(request);
      if (!alive) return;
      statusPanelEl.innerHTML = `<p class="backtest-success">Backtest completed - ${run.totalTrades} closed trade${run.totalTrades === 1 ? "" : "s"} over ${formatDate(run.fromDate)} to ${formatDate(run.toDate)}.</p>`;
      await loadRun(run.id, run);
      loadHistory(true);
    } catch (error) {
      if (!alive) return;
      statusPanelEl.innerHTML = `<p class="backtest-error">${escapeHtml(describeSubmitError(error))}</p>`;
    } finally {
      if (alive) setBusy(false);
    }
  }

  form.addEventListener("submit", onSubmit);

  // ---------- Results (performance summary + equity curve + diagnostics + trade ledger) ----------

  function diagnosticsMessage(run) {
    if (run.entrySignalsGenerated === null || run.entrySignalsGenerated === undefined) {
      return "Diagnostics are unavailable for this run (it predates this feature).";
    }
    if (run.entrySignalsGenerated === 0) {
      return "No entry signals were generated during this period - the strategy's conditions were never met.";
    }
    const invalidQty = run.entriesSkippedInvalidQuantity || 0;
    if (invalidQty > 0 && run.totalTrades === 0) {
      return `${run.entrySignalsGenerated} entry signal${run.entrySignalsGenerated === 1 ? "" : "s"} were generated, but ${invalidQty === run.entrySignalsGenerated ? "all" : invalidQty} ${invalidQty === 1 ? "was" : "were"} skipped because risk-based sizing could not compute a usable quantity (the ATR value or multiplier was zero or negative at that signal).`;
    }
    if (run.entriesSkippedInsufficientCash > 0 && run.totalTrades === 0) {
      return `${run.entrySignalsGenerated} entry signal${run.entrySignalsGenerated === 1 ? "" : "s"} were generated, but ${run.entriesSkippedInsufficientCash === run.entrySignalsGenerated ? "all" : run.entriesSkippedInsufficientCash} ${run.entriesSkippedInsufficientCash === 1 ? "was" : "were"} skipped because available cash was insufficient for the requested position size.`;
    }
    if (run.entriesSkippedInsufficientCash > 0 || invalidQty > 0) {
      return `${run.entrySignalsGenerated} entry signal${run.entrySignalsGenerated === 1 ? "" : "s"} were generated; ${run.totalTrades} executed, ${run.entriesSkippedInsufficientCash} skipped for insufficient cash, ${invalidQty} skipped for an invalid computed quantity.`;
    }
    return `${run.entrySignalsGenerated} entry signal${run.entrySignalsGenerated === 1 ? "" : "s"} generated, all executed.`;
  }

  function renderResults() {
    if (!alive) return;

    if (!runSection.value.data && runSection.value.status !== "loading") {
      resultsPanelEl.innerHTML = `
        <div class="card empty-state">
          <p>Configure and run a backtest above, or select a previous run below, to see results here.</p>
        </div>`;
      if (equityChart) {
        equityChart.destroy();
        equityChart = null;
      }
      return;
    }

    if (runSection.value.status === "loading") {
      resultsPanelEl.innerHTML = `<div class="card">${skeletonHtml(4)}</div>`;
      return;
    }

    const run = runSection.value.data;
    if (!run) return;
    const currency = run.accountCurrency;

    resultsPanelEl.innerHTML = `
      <section class="card">
        <div class="section-header">
          <h2>${escapeHtml(run.symbol)} &middot; ${escapeHtml(STRATEGY_LABELS[run.strategyType] || run.strategyType)}</h2>
          <span class="section-asof">Completed ${formatDateTime(run.completedAt)}</span>
        </div>
        <p class="section-disclaimer">
          ${escapeHtml(run.assetType)} &middot; ${formatDate(run.fromDate)} - ${formatDate(run.toDate)} &middot;
          ${strategyDescription(run)}
          &middot; ${
            run.quantity !== null && run.quantity !== undefined
              ? `Quantity ${formatNumber(run.quantity, 2)}`
              : `Risk-based sizing, ${formatPercent(run.riskPercentPerTrade)} of equity per trade`
          } &middot; Commission ${formatMoney(run.commissionPerOrder, currency)}/order &middot; Slippage ${run.slippageBps} bps
          ${run.atrRiskManagementEnabled ? `&middot; ATR(${run.atrPeriod}) stop &times;${formatNumber(run.atrInitialStopMultiplier, 2)}${run.atrTrailingStopEnabled ? `, trailing &times;${formatNumber(run.atrTrailingStopMultiplier, 2)}` : ""}` : ""}
        </p>

        <div class="metric-grid">
          ${metricCardHtml({
            label: "Initial capital",
            value: formatMoney(run.initialCapital, currency),
            sublabel: run.accountProfileId
              ? `From profile: ${escapeHtml(profiles.find((p) => p.id === run.accountProfileId)?.name || "saved account")}`
              : null,
          })}
          ${metricCardHtml({ label: "Final equity", value: formatMoney(run.finalEquity, currency) })}
          ${metricCardHtml({ label: "Net P&amp;L", value: formatMoney(run.netPnl, currency), colorClass: pnlClass(run.netPnl) })}
          ${metricCardHtml({ label: "Total return", value: formatPercent(run.netReturnPercent), colorClass: pnlClass(run.netReturnPercent) })}
          ${metricCardHtml({ label: "Max drawdown", value: formatPercent(Math.abs(run.maxDrawdownPercent) * -1) })}
          ${metricCardHtml({ label: "Closed trades", value: formatNumber(run.totalTrades, 0) })}
          ${metricCardHtml({ label: "Winning trades", value: formatNumber(run.winningTrades, 0), colorClass: "positive" })}
          ${metricCardHtml({ label: "Losing trades", value: formatNumber(run.losingTrades, 0), colorClass: "negative" })}
          ${metricCardHtml({ label: "Win rate", value: run.winRate === null ? "N/A" : formatPercent(run.winRate) })}
          ${metricCardHtml({ label: "Profit factor", value: run.profitFactor === null ? "N/A (no losing trades)" : formatNumber(run.profitFactor, 2) })}
        </div>
        <p class="section-disclaimer">
          Historical backtest performance is a simulation only and does not guarantee future results.
          Win rate and profit factor based on very few trades are not statistically meaningful.
        </p>
      </section>

      <section class="card">
        <h2>Equity curve</h2>
        <div class="chart-wrapper backtest-equity-wrapper">
          <canvas class="price-chart" id="equityCanvas"></canvas>
          <div class="chart-tooltip" id="equityTooltip" hidden></div>
        </div>
        <div id="equityStatus"></div>
      </section>

      <section class="card">
        <h2>Strategy &amp; trade diagnostics</h2>
        <p class="backtest-diagnostics-message">${escapeHtml(diagnosticsMessage(run))}</p>
        ${
          run.entrySignalsGenerated !== null && run.entrySignalsGenerated !== undefined
            ? `
        <div class="backtest-diagnostics-grid">
          <div><span class="backtest-diagnostics-label">Candles evaluated</span><span>${formatNumber(run.candlesEvaluated, 0)}</span></div>
          <div><span class="backtest-diagnostics-label">Entry signals</span><span>${formatNumber(run.entrySignalsGenerated, 0)}</span></div>
          <div><span class="backtest-diagnostics-label">Exit signals</span><span>${formatNumber(run.exitSignalsGenerated, 0)}</span></div>
          <div><span class="backtest-diagnostics-label">Trades executed</span><span>${formatNumber(run.totalTrades, 0)}</span></div>
          <div><span class="backtest-diagnostics-label">Entries skipped (cash)</span><span>${formatNumber(run.entriesSkippedInsufficientCash, 0)}</span></div>
          ${
            run.entriesSkippedInvalidQuantity !== null && run.entriesSkippedInvalidQuantity !== undefined
              ? `<div><span class="backtest-diagnostics-label">Entries skipped (invalid quantity)</span><span>${formatNumber(run.entriesSkippedInvalidQuantity, 0)}</span></div>`
              : ""
          }
          <div><span class="backtest-diagnostics-label">Main skip reason</span><span>${
            (run.entriesSkippedInvalidQuantity || 0) > 0
              ? "Invalid computed quantity"
              : run.entriesSkippedInsufficientCash > 0
                ? "Insufficient cash"
                : "N/A"
          }</span></div>
        </div>`
            : ""
        }
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Trade ledger</h2>
        </div>
        <div id="tradeLedger"></div>
      </section>`;

    const canvas = resultsPanelEl.querySelector("#equityCanvas");
    const tooltipEl = resultsPanelEl.querySelector("#equityTooltip");
    if (equityChart) equityChart.destroy();
    equityChart = createEquityCurveChart(canvas, tooltipEl);

    const equityStatusEl = resultsPanelEl.querySelector("#equityStatus");
    const tradeLedgerEl = resultsPanelEl.querySelector("#tradeLedger");

    renderStateBlock(equityStatusEl, detailSection.value, {
      loadingLines: 0,
      onRetry: () => loadDetail(run.id),
      renderContent: (detail) => {
        equityStatusEl.innerHTML = "";
        equityChart.setPoints(detail.equityCurve, (v) => formatMoney(v, currency));
      },
    });
    if (detailSection.value.status === "loading") {
      equityStatusEl.innerHTML = `<p class="section-status">Loading equity curve...</p>`;
    }

    renderStateBlock(tradeLedgerEl, detailSection.value, {
      loadingLines: 4,
      onRetry: () => loadDetail(run.id),
      renderContent: (detail) => {
        renderTradeLedger(tradeLedgerEl, detail.trades, currency, run.atrRiskManagementEnabled);
      },
    });
  }

  function renderTradeLedger(el, trades, currency, showStops) {
    if (!trades || trades.length === 0) {
      el.innerHTML = `<div class="empty-state"><p>No trades were executed during this period. See the diagnostics above to find out why.</p></div>`;
      return;
    }
    const exitReasonTooltip = (reason) => {
      if (reason === "END_OF_PERIOD") {
        return ` title="The position was still open when the simulation period ended and was force-closed at the final candle's close - this is not a strategy exit signal."`;
      }
      if (reason === "ATR_STOP") {
        return ` title="Closed intrabar when price breached the ATR-based stop level (initial or trailed) - not a strategy exit signal."`;
      }
      return "";
    };
    el.innerHTML = `
      <div class="backtest-table-wrapper">
        <table class="backtest-trades-table">
          <thead>
            <tr>
              <th>#</th><th>Direction</th><th>Entry date</th><th>Entry price</th><th>Exit date</th><th>Exit price</th>
              <th>Quantity</th><th>Gross P&amp;L</th><th>Costs</th><th>Net P&amp;L</th><th>Return</th><th>Exit reason</th>
              ${showStops ? `<th>Initial stop</th><th>Final stop</th>` : ""}
            </tr>
          </thead>
          <tbody>
            ${trades
              .map(
                (t) => `
              <tr>
                <td>${t.sequenceNumber + 1}</td>
                <td>${escapeHtml(t.direction)}</td>
                <td>${formatDate(t.entryTimestamp)}</td>
                <td>${formatMoney(t.entryPrice, currency)}</td>
                <td>${formatDate(t.exitTimestamp)}</td>
                <td>${formatMoney(t.exitPrice, currency)}</td>
                <td>${formatNumber(t.quantity, 2)}</td>
                <td class="${pnlClass(t.grossPnl)}">${formatMoney(t.grossPnl, currency)}</td>
                <td>${formatMoney(t.commission, currency)}</td>
                <td class="${pnlClass(t.netPnl)}">${formatMoney(t.netPnl, currency)}</td>
                <td class="${pnlClass(t.returnPercent)}">${formatPercent(t.returnPercent)}</td>
                <td>
                  <span class="backtest-exit-reason"${exitReasonTooltip(t.exitReason)}>
                    ${escapeHtml(EXIT_REASON_LABELS[t.exitReason] || t.exitReason)}
                  </span>
                </td>
                ${
                  showStops
                    ? `<td>${t.initialStopPrice === null || t.initialStopPrice === undefined ? "N/A" : formatMoney(t.initialStopPrice, currency)}</td>
                       <td>${t.finalStopPrice === null || t.finalStopPrice === undefined ? "N/A" : formatMoney(t.finalStopPrice, currency)}</td>`
                    : ""
                }
              </tr>`
              )
              .join("")}
          </tbody>
        </table>
      </div>`;
  }

  async function loadDetail(backtestId) {
    await loadInto(detailSection, renderResults, () => getBacktestTrades(backtestId));
  }

  async function loadRun(backtestId, knownRun) {
    if (knownRun) {
      runSection.value = { status: "ok", data: knownRun, error: null };
    } else {
      await loadInto(runSection, renderResults, () => getBacktest(backtestId));
    }
    renderResults();
    await loadDetail(backtestId);
  }

  // ---------- History ----------

  function renderHistory() {
    renderStateBlock(historyPanelEl, historySection.value, {
      loadingLines: 3,
      onRetry: () => loadHistory(true),
      emptyText: "",
      renderContent: (runs) => {
        if (!runs || runs.length === 0) {
          historyPanelEl.innerHTML = `<div class="empty-state"><p>You haven't run any backtests yet.</p></div>`;
          return;
        }
        historyPanelEl.innerHTML = `
          <div class="backtest-table-wrapper">
            <table class="backtest-trades-table backtest-history-table">
              <thead>
                <tr><th>Symbol</th><th>Asset</th><th>Strategy</th><th>Range</th><th>Completed</th><th>Net P&amp;L</th><th>Return</th><th>Trades</th></tr>
              </thead>
              <tbody>
                ${runs
                  .map(
                    (r) => `
                  <tr class="backtest-history-row" data-id="${escapeHtml(r.id)}" tabindex="0">
                    <td>${escapeHtml(r.symbol)}</td>
                    <td>${escapeHtml(r.assetType)}</td>
                    <td>${escapeHtml(STRATEGY_LABELS[r.strategyType] || r.strategyType)}</td>
                    <td>${formatDate(r.fromDate)} - ${formatDate(r.toDate)}</td>
                    <td>${formatDateTime(r.completedAt)}</td>
                    <td class="${pnlClass(r.netPnl)}">${formatNumber(r.netPnl, 2)}</td>
                    <td class="${pnlClass(r.netReturnPercent)}">${formatPercent(r.netReturnPercent)}</td>
                    <td>${formatNumber(r.totalTrades, 0)}</td>
                  </tr>`
                  )
                  .join("")}
              </tbody>
            </table>
          </div>`;
        historyPanelEl.querySelectorAll(".backtest-history-row").forEach((row) => {
          const select = () => {
            loadRun(row.dataset.id);
            resultsPanelEl.scrollIntoView({ behavior: "smooth", block: "start" });
          };
          row.addEventListener("click", select);
          row.addEventListener("keydown", (event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              select();
            }
          });
        });
      },
    });
  }

  async function loadHistory(force) {
    await loadInto(historySection, renderHistory, () => listBacktests());
  }

  historyRefreshBtn.addEventListener("click", () => loadHistory(true));

  renderSizingPanel();
  renderResults();
  renderHistory();
  loadHistory(false);
  if (isValidSymbol(symbolInput.value.trim().toUpperCase())) {
    checkPositionSizing();
  }

  return () => {
    alive = false;
    if (equityChart) equityChart.destroy();
    form.removeEventListener("submit", onSubmit);
  };
}
