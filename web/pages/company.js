import { getMarketIntelligence } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { renderContextSection } from "../components/contextSection.js";
import { formatLargeNumber, formatDateTime, escapeHtml } from "../format.js";
import { metricCardHtml } from "../components/metricCard.js";

const MARKET_INTELLIGENCE_ENDPOINT = "market-intelligence";

export function mount(container) {
  const symbol = getCurrentSymbol();
  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to view company information.</p></div></div>`;
    return;
  }

  let alive = true;
  const contextSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page company-page">
      <section class="card">
        <div class="section-header">
          <h2>Company Info &middot; ${escapeHtml(symbol)}</h2>
          <button type="button" class="refresh-btn" id="refreshBtn">Refresh</button>
        </div>
        <span class="section-asof" id="companyAsOf"></span>
        <div id="companyContent"></div>
      </section>
    </div>`;

  const contentEl = container.querySelector("#companyContent");
  const asOfEl = container.querySelector("#companyAsOf");
  const refreshBtn = container.querySelector("#refreshBtn");

  function render() {
    if (!alive) return;
    asOfEl.textContent = "";
    renderStateBlock(contentEl, contextSection.value, {
      loadingLines: 4,
      onRetry: () => load(true),
      renderContent: (context) => {
        asOfEl.textContent = `As of ${formatDateTime(context.asOf)}`;
        renderContextSection(contentEl, context.companyInformation, {
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
      },
    });
  }

  async function load(force) {
    if (force) invalidateCached(symbol, MARKET_INTELLIGENCE_ENDPOINT);
    await loadInto(contextSection, render, () =>
      withCache(symbol, MARKET_INTELLIGENCE_ENDPOINT, () => getMarketIntelligence(symbol))
    );
  }

  refreshBtn.addEventListener("click", () => load(true));

  render();
  load(false);

  return () => {
    alive = false;
  };
}
