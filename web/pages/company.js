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
        const section = context.assetType === "FOREX" ? context.instrumentInformation : context.companyInformation;
        renderContextSection(contentEl, section, {
          renderContent: (data) => {
            if (context.assetType === "FOREX") {
              contentEl.innerHTML = `
                <div class="metric-grid">
                  ${metricCardHtml({ label: "Pair", value: escapeHtml(data.displaySymbol) })}
                  ${metricCardHtml({ label: "Base Currency", value: escapeHtml(data.baseCurrency) })}
                  ${metricCardHtml({ label: "Quote Currency", value: escapeHtml(data.quoteCurrency) })}
                </div>
                <p class="section-disclaimer">${escapeHtml(data.description)}</p>
                <p class="section-disclaimer">
                  Currency pair reference data only. Forex pairs have no company, exchange, or
                  market-capitalization equivalent, and news/sentiment coverage is not available
                  for forex under the current data provider integration.
                </p>`;
              return;
            }

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
