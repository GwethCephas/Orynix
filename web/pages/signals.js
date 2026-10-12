import { getSignals } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { humanizeEnum, classifySignalColor, escapeHtml } from "../format.js";
import { signalBadgeHtml } from "../components/signalBadge.js";

const SIGNALS_ENDPOINT = "signals";

export function mount(container) {
  const symbol = getCurrentSymbol();
  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to view its signals.</p></div></div>`;
    return;
  }

  let alive = true;
  const section = { value: createSectionState() };

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
        <div id="signalsContent"></div>
      </section>
    </div>`;

  const contentEl = container.querySelector("#signalsContent");
  const refreshBtn = container.querySelector("#refreshBtn");

  function render() {
    if (!alive) return;
    renderStateBlock(contentEl, section.value, {
      loadingLines: 5,
      onRetry: () => load(true),
      renderContent: (data) => {
        const biasColor = classifySignalColor(data.overallBias);
        const badges = data.signals.map(signalBadgeHtml).join("");
        contentEl.innerHTML = `
          <div class="signal-summary">
            <div class="overall-bias ${biasColor}">Overall bias: ${humanizeEnum(data.overallBias)}</div>
            <div class="signal-counts">
              <span class="positive">Bullish ${data.bullishSignalCount}</span>
              <span class="negative">Bearish ${data.bearishSignalCount}</span>
              <span class="neutral">Neutral ${data.neutralSignalCount}</span>
            </div>
          </div>
          <div class="signal-grid">${badges}</div>`;
      },
    });
  }

  async function load(force) {
    if (force) invalidateCached(symbol, SIGNALS_ENDPOINT);
    await loadInto(section, render, () => withCache(symbol, SIGNALS_ENDPOINT, () => getSignals(symbol)));
  }

  refreshBtn.addEventListener("click", () => load(true));

  render();
  load(false);

  return () => {
    alive = false;
  };
}
