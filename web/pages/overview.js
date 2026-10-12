import { getCurrentSymbol } from "../state.js";
import { navigate } from "../router.js";
import { createSymbolSearch } from "../components/symbolSearch.js";
import { escapeHtml } from "../format.js";

export function mount(container) {
  const symbol = getCurrentSymbol();

  container.innerHTML = `
    <div class="page overview-page">
      <section class="card overview-hero">
        <h1>Research any symbol</h1>
        <p class="overview-subtitle">
          Orynix combines historical price data, technical indicators, deterministic signals,
          company fundamentals, and news sentiment into a single research view.
        </p>
        <div class="overview-search"></div>
        <div class="overview-examples">
          <span>Try:</span>
          <button type="button" class="example-chip" data-symbol="AAPL">AAPL</button>
          <button type="button" class="example-chip" data-symbol="IBM">IBM</button>
        </div>
      </section>

      ${
        symbol
          ? `
      <section class="card overview-quicklinks">
        <h2>Continue researching ${escapeHtml(symbol)}</h2>
        <div class="quicklink-grid">
          <a href="#" class="quicklink-card" data-path="analysis"><span class="quicklink-title">Market Analysis</span><span class="quicklink-desc">Price chart &amp; indicators</span></a>
          <a href="#" class="quicklink-card" data-path="signals"><span class="quicklink-title">Signals</span><span class="quicklink-desc">Deterministic conditions</span></a>
          <a href="#" class="quicklink-card" data-path="news"><span class="quicklink-title">News &amp; Sentiment</span><span class="quicklink-desc">Recent coverage</span></a>
          <a href="#" class="quicklink-card" data-path="company"><span class="quicklink-title">Company Info</span><span class="quicklink-desc">Fundamentals</span></a>
        </div>
      </section>`
          : ""
      }

      <section class="card overview-scope">
        <h2>What's available today</h2>
        <ul class="overview-scope-list">
          <li>Historical daily price data (most recent ~365 days)</li>
          <li>Technical indicators: moving averages, RSI, MACD, Bollinger Bands, ATR, volume</li>
          <li>Deterministic signal conditions derived from those indicators — not trading recommendations</li>
          <li>Company reference info: name, exchange, industry, market capitalization</li>
          <li>Recent news with on-demand FinBERT sentiment analysis</li>
        </ul>
      </section>
    </div>`;

  const search = createSymbolSearch({
    initialValue: symbol || "",
    placeholder: "Enter a symbol, e.g. AAPL",
    onSubmit: (value) => navigate("analysis", { symbol: value }),
  });
  container.querySelector(".overview-search").appendChild(search.element);

  function onExampleClick(event) {
    navigate("analysis", { symbol: event.currentTarget.dataset.symbol });
  }
  container.querySelectorAll(".example-chip").forEach((btn) => btn.addEventListener("click", onExampleClick));

  function onQuickLinkClick(event) {
    event.preventDefault();
    navigate(event.currentTarget.dataset.path, { symbol });
  }
  container.querySelectorAll(".quicklink-card").forEach((link) => link.addEventListener("click", onQuickLinkClick));
}
