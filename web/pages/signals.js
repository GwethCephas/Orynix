import { getMarketIntelligence } from "../api.js";
import { getCurrentSymbol, getSelectedSignalTypes, setSelectedSignalTypes, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { renderContextSection } from "../components/contextSection.js";
import { humanizeEnum, classifySignalColor, formatDateTime, escapeHtml } from "../format.js";
import { signalBadgeHtml } from "../components/signalBadge.js";

const MARKET_INTELLIGENCE_ENDPOINT = "market-intelligence";

export function mount(container) {
  const symbol = getCurrentSymbol();
  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to view its signals.</p></div></div>`;
    return;
  }

  let alive = true;
  const contextSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page signals-page">
      <section class="card">
        <div class="section-header">
          <h2>Signals &middot; ${escapeHtml(symbol)}</h2>
          <button type="button" class="refresh-btn" id="refreshBtn">Refresh</button>
        </div>
        <p class="section-disclaimer">
          Deterministic conditions derived from technical indicators. These describe the current
          state of the data — they are not buy/sell recommendations.
        </p>
        <span class="section-asof" id="signalsAsOf"></span>
        <div id="signalsContent"></div>
      </section>
    </div>`;

  const contentEl = container.querySelector("#signalsContent");
  const asOfEl = container.querySelector("#signalsAsOf");
  const refreshBtn = container.querySelector("#refreshBtn");

  function render() {
    if (!alive) return;
    asOfEl.textContent = "";
    renderStateBlock(contentEl, contextSection.value, {
      loadingLines: 5,
      onRetry: () => load(true),
      renderContent: (context) => {
        asOfEl.textContent = `As of ${formatDateTime(context.asOf)}`;
        renderContextSection(contentEl, context.signals, {
          renderContent: (data) => {
            const biasColor = classifySignalColor(data.overallBias);
            const selectedTypes = getSelectedSignalTypes();
            const visibleSignals = selectedTypes.length
              ? data.signals.filter((signal) => selectedTypes.includes(signal.type))
              : data.signals;

            const filterBannerHtml = selectedTypes.length
              ? `
                <div class="signal-filter-banner">
                  <span>Filtered by: ${selectedTypes.map(humanizeEnum).join(", ")}</span>
                  <button type="button" class="signal-filter-banner-clear">Clear</button>
                </div>`
              : "";

            const gridHtml = visibleSignals.length
              ? `<div class="signal-grid">${visibleSignals.map(signalBadgeHtml).join("")}</div>`
              : `<p class="section-status">No signals for this symbol match the selected filters.</p>`;

            contentEl.innerHTML = `
              ${filterBannerHtml}
              <div class="signal-summary">
                <div class="overall-bias ${biasColor}">Overall bias: ${humanizeEnum(data.overallBias)}</div>
                <div class="signal-counts">
                  <span class="positive">Bullish ${data.bullishSignalCount}</span>
                  <span class="negative">Bearish ${data.bearishSignalCount}</span>
                  <span class="neutral">Neutral ${data.neutralSignalCount}</span>
                </div>
              </div>
              ${gridHtml}`;

            contentEl.querySelector(".signal-filter-banner-clear")?.addEventListener("click", () => setSelectedSignalTypes([]));
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
