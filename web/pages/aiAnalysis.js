import { getAiMarketAnalysis, listTradingAccountProfiles, createTradeSetupFromProposal, ApiError } from "../api.js";
import { getCurrentSymbol } from "../state.js";
import { navigate } from "../router.js";
import { createSectionState, loadInto, renderStateBlock, skeletonHtml } from "../components/stateBlock.js";
import { metricCardHtml } from "../components/metricCard.js";
import { escapeHtml, formatNumber, formatPercent, formatDateTime } from "../format.js";

const SCENARIO_COLOR = { BULLISH: "positive", BEARISH: "negative", NEUTRAL: "neutral" };
const EVIDENCE_LABELS = {
  marketData: "Market data",
  technicalAnalysis: "Technical analysis",
  signals: "Signals",
  companyInformation: "Company information",
  instrumentInformation: "Instrument information",
  news: "News",
  sentiment: "Sentiment",
};

export function mount(container) {
  let alive = true;
  let busy = false;
  let profiles = [];

  const symbol = getCurrentSymbol();

  if (!symbol) {
    container.innerHTML = `
      <div class="page">
        <div class="card empty-state">
          <p>Search for a symbol above to request an AI market analysis.</p>
        </div>
      </div>`;
    return;
  }

  const analysisSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page ai-analysis-page">
      <section class="card">
        <h1>AI Market Analysis &middot; ${escapeHtml(symbol)}</h1>
        <p class="section-disclaimer">
          Generates a structured analysis of ${escapeHtml(symbol)} from Orynix's existing market data,
          technical indicators, signals, news, and sentiment, interpreted by a configured language
          model. This is not financial advice, not a guarantee of any outcome, and does not execute
          any trade - any proposed trade setup is a starting point for your own evaluation, and
          position sizing (when shown) is always computed by Orynix's own deterministic risk
          calculator, never by the model itself.
        </p>
      </section>

      <section class="card">
        <h2>Account context (optional)</h2>
        <form class="backtest-form" novalidate id="analysisForm">
          <div class="backtest-form-grid">
            <label class="backtest-field">
              <span>Trading account profile (optional)</span>
              <select name="accountProfileId">
                <option value="">None</option>
              </select>
              <small class="backtest-field-hint">Manually-entered, not broker-verified - see the Trading Accounts page.</small>
            </label>
            <label class="backtest-field">
              <span>Equity (optional)</span>
              <input type="number" name="equity" min="0" step="any" placeholder="e.g. 10000">
            </label>
            <label class="backtest-field">
              <span>Account currency (optional)</span>
              <input type="text" name="accountCurrency" maxlength="3" placeholder="USD">
            </label>
            <label class="backtest-field">
              <span>Risk per trade % (optional)</span>
              <input type="number" name="riskPercentPerTrade" min="0" max="100" step="any" placeholder="e.g. 1">
            </label>
          </div>
          <p class="backtest-validation-error" hidden></p>
          <div class="backtest-form-actions">
            <button type="submit" class="auth-primary-btn" id="analysisSubmitBtn">Run AI Analysis</button>
          </div>
        </form>
      </section>

      <section id="resultsPanel"></section>
    </div>`;

  const form = container.querySelector("#analysisForm");
  const profileSelect = form.querySelector('[name="accountProfileId"]');
  const validationErrorEl = form.querySelector(".backtest-validation-error");
  const submitBtn = container.querySelector("#analysisSubmitBtn");
  const resultsPanelEl = container.querySelector("#resultsPanel");

  async function loadProfiles() {
    try {
      profiles = await listTradingAccountProfiles();
    } catch {
      profiles = [];
    }
    if (!alive) return;
    profileSelect.innerHTML =
      `<option value="">None</option>` +
      profiles
        .map((p) => `<option value="${p.id}">${escapeHtml(p.name)} (${escapeHtml(p.accountCurrency)} ${formatNumber(p.balance, 2)})</option>`)
        .join("");
  }
  loadProfiles();

  function readForm() {
    return {
      accountProfileId: profileSelect.value,
      equity: form.querySelector('[name="equity"]').value,
      accountCurrency: form.querySelector('[name="accountCurrency"]').value.trim().toUpperCase(),
      riskPercentPerTrade: form.querySelector('[name="riskPercentPerTrade"]').value,
    };
  }

  function validate(values) {
    const errors = [];
    if (values.equity !== "" && !(Number(values.equity) > 0)) {
      errors.push("Equity must be a positive number.");
    }
    if (values.riskPercentPerTrade !== "") {
      const risk = Number(values.riskPercentPerTrade);
      if (!(risk > 0) || !(risk <= 100)) errors.push("Risk per trade % must be a number between 0 and 100.");
    }
    if (values.accountCurrency && !/^[A-Z]{3}$/.test(values.accountCurrency)) {
      errors.push("Account currency must be a 3-letter code, e.g. USD.");
    }
    return errors;
  }

  function buildRequest(values) {
    return {
      symbol,
      accountProfileId: values.accountProfileId || null,
      equity: values.equity !== "" ? Number(values.equity) : null,
      accountCurrency: values.accountCurrency || null,
      riskPercentPerTrade: values.riskPercentPerTrade !== "" ? Number(values.riskPercentPerTrade) : null,
    };
  }

  function renderEvidenceStatus(evidenceStatus) {
    return `
      <div class="metric-grid">
        ${Object.entries(EVIDENCE_LABELS)
          .map(([key, label]) => {
            const section = evidenceStatus[key];
            const colorClass = section.status === "AVAILABLE" ? "positive" : section.status === "ERROR" ? "negative" : "neutral";
            return metricCardHtml({
              label,
              value: escapeHtml(section.status),
              colorClass,
              sublabel: section.message ? escapeHtml(section.message) : null,
            });
          })
          .join("")}
      </div>`;
  }

  function renderScenarios(scenarios) {
    if (scenarios.length === 0) {
      return `<p class="section-status">No scenarios were identified from the available evidence.</p>`;
    }
    return scenarios
      .map(
        (s) => `
        <div class="card">
          <div class="section-header">
            <span class="signal-badge ${SCENARIO_COLOR[s.label] || "neutral"}"><span class="signal-state">${escapeHtml(s.label)}</span></span>
            ${s.confidence ? `<span class="section-status">Confidence: ${escapeHtml(s.confidence)}</span>` : ""}
          </div>
          <p>${escapeHtml(s.narrative)}</p>
        </div>`
      )
      .join("");
  }

  function renderTradeSetups(tradeSetups) {
    if (tradeSetups.length === 0) {
      return `<p class="section-status">No trade setup was proposed from the available evidence.</p>`;
    }
    return tradeSetups
      .map((t) => {
        const sizingMetrics = t.sizing
          ? `
            ${metricCardHtml({ label: "Quantity", value: t.sizing.quantity !== null ? formatNumber(t.sizing.quantity, 4) : "N/A" })}
            ${metricCardHtml({ label: "Est. loss at stop", value: t.sizing.estimatedLossAtStop !== null ? `${formatNumber(t.sizing.estimatedLossAtStop, 2)} ${escapeHtml(t.sizing.accountCurrency)}` : "N/A" })}
            ${metricCardHtml({ label: "Est. gain at take-profit", value: t.sizing.estimatedGainAtTakeProfit !== null ? `${formatNumber(t.sizing.estimatedGainAtTakeProfit, 2)} ${escapeHtml(t.sizing.accountCurrency)}` : "N/A" })}
            ${metricCardHtml({ label: "Risk/reward", value: t.sizing.riskRewardRatio !== null ? formatNumber(t.sizing.riskRewardRatio, 2) : "N/A" })}`
          : "";
        return `
          <div class="card">
            <div class="section-header">
              <span class="signal-badge ${t.direction === "LONG" ? "positive" : "negative"}"><span class="signal-state">${escapeHtml(t.direction)}</span></span>
              <span class="ai-proposed-tag">Proposed &middot; not executed</span>
            </div>
            <p>${escapeHtml(t.rationale)}</p>
            <div class="metric-grid">
              ${metricCardHtml({ label: "Entry", value: formatNumber(t.entryPrice, 4) })}
              ${metricCardHtml({ label: "Stop loss", value: formatNumber(t.stopLossPrice, 4) })}
              ${t.takeProfitPrice !== null ? metricCardHtml({ label: "Take profit", value: formatNumber(t.takeProfitPrice, 4) }) : ""}
              ${sizingMetrics}
            </div>
            ${
              t.limitations.length > 0
                ? `<p class="section-disclaimer">${t.limitations.map((l) => escapeHtml(l)).join(" ")}</p>`
                : ""
            }
            <div class="ai-save-setup-row" data-proposal-id="${escapeHtml(t.id)}">
              <button type="button" class="ai-save-setup-btn">Save as Trade Setup</button>
              <span class="ai-save-setup-status section-status"></span>
            </div>
          </div>`;
      })
      .join("");
  }

  async function saveProposalAsTradeSetup(row) {
    const button = row.querySelector(".ai-save-setup-btn");
    const statusEl = row.querySelector(".ai-save-setup-status");
    const proposalId = row.dataset.proposalId;
    button.disabled = true;
    button.textContent = "Saving...";
    statusEl.textContent = "";
    try {
      const saved = await createTradeSetupFromProposal({ aiProposalId: proposalId, accountProfileId: null });
      button.remove();
      statusEl.innerHTML = `Saved as a draft trade setup. <a href="#" class="ai-saved-setup-link">Review it &rarr;</a>`;
      statusEl.dataset.savedId = saved.id;
      statusEl.querySelector(".ai-saved-setup-link").addEventListener("click", (event) => {
        event.preventDefault();
        navigate("trade-setups");
      });
    } catch (err) {
      button.disabled = false;
      button.textContent = "Save as Trade Setup";
      // Treat the backend as the source of truth - surface its own message verbatim rather than
      // inventing a generic one (e.g. a 409 if this exact proposal was already saved elsewhere).
      statusEl.textContent = err instanceof ApiError ? err.message : "Could not save this trade setup.";
    }
  }

  function renderResults() {
    if (!alive) return;
    renderStateBlock(resultsPanelEl, analysisSection.value, {
      loadingLines: 5,
      emptyText: "Submit the form above to run an AI market analysis.",
      onRetry: () => runAnalysis(),
      renderContent: (analysis) => {
        const sections = Object.values(analysis.evidenceStatus);
        const availableCount = sections.filter((s) => s.status === "AVAILABLE").length;
        const allAvailable = availableCount === sections.length;

        resultsPanelEl.innerHTML = `
          <section class="card content-fade-in">
            <div class="section-header">
              <h2>${escapeHtml(analysis.symbol)} <span class="profile-badge">${escapeHtml(analysis.assetType)}</span> Market assessment <span class="ai-generated-tag">AI-generated</span></h2>
              <span class="section-asof">As of ${formatDateTime(analysis.asOf)}</span>
            </div>
            <p>${escapeHtml(analysis.marketAssessment)}</p>
            <p class="section-disclaimer">
              This is the model's interpretation of the observed market data, technical indicators,
              signals, news, and sentiment below - not a guarantee of any outcome. For the
              underlying deterministic data itself, see
              <a href="#" class="ai-source-link" data-path="analysis">Market Analysis</a>,
              <a href="#" class="ai-source-link" data-path="signals">Signals</a>, and
              <a href="#" class="ai-source-link" data-path="news">News &amp; Sentiment</a>.
            </p>
            <p class="section-status">
              Source: ${escapeHtml(analysis.provenance.source)} &middot; ${escapeHtml(analysis.provenance.providerName)} (${escapeHtml(analysis.provenance.model)})
            </p>
          </section>

          <section class="card content-fade-in">
            <h2>Scenarios</h2>
            ${renderScenarios(analysis.scenarios)}
          </section>

          <section class="card content-fade-in">
            <h2>Proposed trade setups</h2>
            ${renderTradeSetups(analysis.tradeSetups)}
          </section>

          <details class="card ai-details content-fade-in" ${allAvailable ? "" : "open"}>
            <summary>Evidence availability &middot; ${availableCount}/${sections.length} sections available</summary>
            <div class="ai-details-body">
              ${renderEvidenceStatus(analysis.evidenceStatus)}
            </div>
          </details>`;

        resultsPanelEl.querySelectorAll(".ai-source-link").forEach((link) => {
          link.addEventListener("click", (event) => {
            event.preventDefault();
            navigate(link.dataset.path, { symbol });
          });
        });

        resultsPanelEl.querySelectorAll(".ai-save-setup-row").forEach((row) => {
          row.querySelector(".ai-save-setup-btn")?.addEventListener("click", () => saveProposalAsTradeSetup(row));
        });
      },
    });
  }

  async function runAnalysis() {
    const values = readForm();
    const errors = validate(values);
    if (errors.length > 0) {
      validationErrorEl.innerHTML = errors.map((e) => escapeHtml(e)).join("<br>");
      validationErrorEl.hidden = false;
      return;
    }
    validationErrorEl.hidden = true;

    const request = buildRequest(values);
    await loadInto(analysisSection, renderResults, () => getAiMarketAnalysis(request));
  }

  async function onSubmit(event) {
    event.preventDefault();
    if (busy) return;
    busy = true;
    submitBtn.disabled = true;
    submitBtn.textContent = "Analyzing...";
    await runAnalysis();
    if (alive) {
      busy = false;
      submitBtn.disabled = false;
      submitBtn.textContent = "Run AI Analysis";
    }
  }

  form.addEventListener("submit", onSubmit);
  renderResults();

  return () => {
    alive = false;
  };
}
