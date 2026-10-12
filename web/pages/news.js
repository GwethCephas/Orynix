import { getMarketIntelligence } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { renderContextSection } from "../components/contextSection.js";
import { formatDateTime, escapeHtml } from "../format.js";
import { newsItemHtml, wireNewsItemSentiment } from "../components/newsItem.js";
import { sentimentPanelHtml } from "../components/sentimentPanel.js";

const MARKET_INTELLIGENCE_ENDPOINT = "market-intelligence";

export function mount(container) {
  const symbol = getCurrentSymbol();
  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to view news &amp; sentiment.</p></div></div>`;
    return;
  }

  let alive = true;
  const contextSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page news-page">
      <section class="card">
        <h2>Sentiment Overview &middot; ${escapeHtml(symbol)}</h2>
        <div id="sentimentContent"></div>
      </section>
      <section class="card">
        <div class="section-header">
          <h2>Recent News</h2>
          <button type="button" class="refresh-btn" id="refreshBtn">Refresh</button>
        </div>
        <span class="section-asof" id="newsAsOf"></span>
        <div id="newsContent"></div>
      </section>
    </div>`;

  const sentimentEl = container.querySelector("#sentimentContent");
  const newsEl = container.querySelector("#newsContent");
  const asOfEl = container.querySelector("#newsAsOf");
  const refreshBtn = container.querySelector("#refreshBtn");

  function render() {
    if (!alive) return;
    asOfEl.textContent = "";

    renderStateBlock(sentimentEl, contextSection.value, {
      loadingLines: 3,
      onRetry: () => load(true),
      renderContent: (context) => {
        renderContextSection(sentimentEl, context.sentiment, {
          renderContent: (aggregate) => {
            sentimentEl.innerHTML = sentimentPanelHtml(aggregate);
          },
        });
      },
    });

    renderStateBlock(newsEl, contextSection.value, {
      loadingLines: 6,
      onRetry: () => load(true),
      renderContent: (context) => {
        asOfEl.textContent = `As of ${formatDateTime(context.asOf)}`;
        renderContextSection(newsEl, context.news, {
          renderContent: (result) => {
            newsEl.innerHTML = `<div class="news-list">${result.articles.map(newsItemHtml).join("")}</div>`;
            wireNewsItemSentiment(newsEl);
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
