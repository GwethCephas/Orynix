import { getNews, getSentimentAggregate } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { escapeHtml } from "../format.js";
import { newsItemHtml, wireNewsItemSentiment } from "../components/newsItem.js";
import { sentimentPanelHtml } from "../components/sentimentPanel.js";

const NEWS_ENDPOINT = "news";
const SENTIMENT_AGGREGATE_ENDPOINT = "sentiment-aggregate";
const NEWS_LIMIT = 20;

export function mount(container) {
  const symbol = getCurrentSymbol();
  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to view news &amp; sentiment.</p></div></div>`;
    return;
  }

  let alive = true;
  const newsSection = { value: createSectionState() };
  const sentimentSection = { value: createSectionState() };

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
        <div id="newsContent"></div>
      </section>
    </div>`;

  const sentimentEl = container.querySelector("#sentimentContent");
  const newsEl = container.querySelector("#newsContent");
  const refreshBtn = container.querySelector("#refreshBtn");

  function renderSentiment() {
    if (!alive) return;
    renderStateBlock(sentimentEl, sentimentSection.value, {
      loadingLines: 3,
      onRetry: () => loadSentiment(true),
      renderContent: (aggregate) => {
        sentimentEl.innerHTML = sentimentPanelHtml(aggregate);
      },
    });
  }

  function renderNews() {
    if (!alive) return;
    renderStateBlock(newsEl, newsSection.value, {
      loadingLines: 6,
      onRetry: () => loadNews(true),
      renderContent: (result) => {
        if (!result.articles.length) {
          newsEl.innerHTML = `<p class="section-status">No recent news found for this symbol.</p>`;
          return;
        }
        newsEl.innerHTML = `<div class="news-list">${result.articles.map(newsItemHtml).join("")}</div>`;
        wireNewsItemSentiment(newsEl);
      },
    });
  }

  async function loadNews(force) {
    if (force) invalidateCached(symbol, NEWS_ENDPOINT);
    await loadInto(newsSection, renderNews, () => withCache(symbol, NEWS_ENDPOINT, () => getNews(symbol, NEWS_LIMIT)));
  }

  async function loadSentiment(force) {
    if (force) invalidateCached(symbol, SENTIMENT_AGGREGATE_ENDPOINT);
    await loadInto(sentimentSection, renderSentiment, () =>
      withCache(symbol, SENTIMENT_AGGREGATE_ENDPOINT, () => getSentimentAggregate(symbol, NEWS_LIMIT))
    );
  }

  refreshBtn.addEventListener("click", () => {
    loadNews(true);
    loadSentiment(true);
  });

  renderSentiment();
  renderNews();
  loadSentiment(false);
  loadNews(false);

  return () => {
    alive = false;
  };
}
