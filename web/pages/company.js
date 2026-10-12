import { getFundamentals } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { formatLargeNumber, escapeHtml } from "../format.js";
import { metricCardHtml } from "../components/metricCard.js";

const FUNDAMENTALS_ENDPOINT = "fundamentals";

export function mount(container) {
  const symbol = getCurrentSymbol();
  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to view company information.</p></div></div>`;
    return;
  }

  let alive = true;
  const section = { value: createSectionState() };

  container.innerHTML = `
    <div class="page company-page">
      <section class="card">
        <div class="section-header">
          <h2>Company Info &middot; ${escapeHtml(symbol)}</h2>
          <button type="button" class="refresh-btn" id="refreshBtn">Refresh</button>
        </div>
        <div id="companyContent"></div>
      </section>
    </div>`;

  const contentEl = container.querySelector("#companyContent");
  const refreshBtn = container.querySelector("#refreshBtn");

  function render() {
    if (!alive) return;
    renderStateBlock(contentEl, section.value, {
      loadingLines: 4,
      onRetry: () => load(true),
      renderContent: (data) => {
        const company = data.company;
        contentEl.innerHTML = `
          <div class="metric-grid">
            ${metricCardHtml({ label: "Name", value: escapeHtml(company.name) })}
            ${metricCardHtml({ label: "Exchange", value: company.exchange ? escapeHtml(company.exchange) : "N/A" })}
            ${metricCardHtml({ label: "Industry", value: company.industry ? escapeHtml(company.industry) : "N/A" })}
            ${metricCardHtml({ label: "Market Cap", value: formatLargeNumber(company.marketCapitalization) })}
          </div>
          <p class="section-disclaimer">
            Company reference data only (name, exchange, industry, market capitalization). Valuation,
            earnings, and other financial-statement metrics are not available yet.
          </p>`;
      },
    });
  }

  async function load(force) {
    if (force) invalidateCached(symbol, FUNDAMENTALS_ENDPOINT);
    await loadInto(section, render, () => withCache(symbol, FUNDAMENTALS_ENDPOINT, () => getFundamentals(symbol)));
  }

  refreshBtn.addEventListener("click", () => load(true));

  render();
  load(false);

  return () => {
    alive = false;
  };
}
