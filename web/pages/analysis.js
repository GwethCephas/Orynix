import { getCandles, getTechnicalAnalysis } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { deriveAssetHeader, formatCurrency, formatNumber, formatPercent, formatDate, escapeHtml } from "../format.js";
import { initChart, setChartCandles, setChartRange, RANGE_OPTIONS } from "../chart.js";

const TA_GROUPS = [
  {
    title: "Trend",
    rows: [
      ["SMA 20", (d) => formatNumber(d.sma20, 4)],
      ["SMA 50", (d) => formatNumber(d.sma50, 4)],
      ["EMA 20", (d) => formatNumber(d.ema20, 4)],
    ],
  },
  {
    title: "Momentum",
    rows: [
      ["RSI 14", (d) => formatNumber(d.rsi14, 4)],
      ["MACD", (d) => formatNumber(d.macd.macd, 4)],
      ["MACD Signal", (d) => formatNumber(d.macd.signal, 4)],
      ["MACD Histogram", (d) => formatNumber(d.macd.histogram, 4)],
    ],
  },
  {
    title: "Volatility",
    rows: [
      ["ATR 14", (d) => formatNumber(d.atr14, 4)],
      ["Bollinger Upper", (d) => formatNumber(d.bollingerBands.upper, 4)],
      ["Bollinger Middle", (d) => formatNumber(d.bollingerBands.middle, 4)],
      ["Bollinger Lower", (d) => formatNumber(d.bollingerBands.lower, 4)],
    ],
  },
  {
    title: "Volume",
    rows: [
      ["Volume SMA 20", (d) => formatNumber(d.volumeSma20, 2)],
      ["Volume Change %", (d) => formatPercent(d.volumeChangePercent, 2)],
    ],
  },
];

const CANDLES_ENDPOINT = "candles";
const TA_ENDPOINT = "technical-analysis";

export function mount(container) {
  const symbol = getCurrentSymbol();

  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to begin analysis.</p></div></div>`;
    return;
  }

  let alive = true;
  const candlesSection = { value: createSectionState() };
  const taSection = { value: createSectionState() };

  container.innerHTML = `
    <div class="page analysis-page">
      <section class="card asset-header" id="assetHeader"></section>

      <section class="card chart-card">
        <div class="chart-card-header">
          <div class="chart-range-controls" id="rangeControls"></div>
          <button type="button" class="refresh-btn" id="refreshBtn">Refresh</button>
        </div>
        <div class="chart-wrapper">
          <canvas class="price-chart" id="priceChart"></canvas>
          <div class="chart-tooltip" id="chartTooltip" hidden></div>
        </div>
        <div id="chartStatus"></div>
      </section>

      <section class="card">
        <h2>Technical Analysis</h2>
        <div id="technicalAnalysis"></div>
      </section>
    </div>`;

  const assetHeaderEl = container.querySelector("#assetHeader");
  const chartStatusEl = container.querySelector("#chartStatus");
  const canvas = container.querySelector("#priceChart");
  const tooltipEl = container.querySelector("#chartTooltip");
  const taEl = container.querySelector("#technicalAnalysis");
  const rangeControlsEl = container.querySelector("#rangeControls");
  const refreshBtn = container.querySelector("#refreshBtn");

  initChart(canvas, tooltipEl);

  rangeControlsEl.innerHTML = RANGE_OPTIONS.map(
    (opt) => `<button type="button" class="range-btn ${opt.days === 90 ? "active" : ""}" data-days="${opt.days ?? ""}">${opt.label}</button>`
  ).join("");
  rangeControlsEl.querySelectorAll(".range-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      rangeControlsEl.querySelectorAll(".range-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      setChartRange(btn.dataset.days ? Number(btn.dataset.days) : null);
    });
  });

  function renderHeader() {
    renderStateBlock(assetHeaderEl, candlesSection.value, {
      loadingLines: 2,
      onRetry: () => loadCandles(true),
      renderContent: (candles) => {
        const header = deriveAssetHeader(candles);
        const changeClass =
          header.delta === null ? "neutral" : header.delta > 0 ? "positive" : header.delta < 0 ? "negative" : "neutral";
        const changeText =
          header.delta === null
            ? "N/A"
            : `${header.delta >= 0 ? "+" : ""}${formatNumber(header.delta)} (${formatPercent(header.deltaPercent)})`;
        assetHeaderEl.innerHTML = `
          <div class="asset-symbol">${escapeHtml(symbol)}</div>
          <div class="asset-price">${formatCurrency(header.latestClose)}</div>
          <div class="asset-change ${changeClass}">${changeText}</div>
          <div class="asset-asof">Daily close as of ${formatDate(header.timestamp)} &middot; historical data, not real-time</div>`;
      },
    });
  }

  function renderChart() {
    renderStateBlock(chartStatusEl, candlesSection.value, {
      loadingLines: 0,
      onRetry: () => loadCandles(true),
      renderContent: (candles, el) => {
        el.innerHTML = "";
        setChartCandles(candles);
      },
    });
    if (candlesSection.value.status === "loading") {
      chartStatusEl.innerHTML = `<p class="section-status">Loading chart...</p>`;
    }
  }

  function renderTa() {
    renderStateBlock(taEl, taSection.value, {
      loadingLines: 4,
      onRetry: () => loadTa(true),
      renderContent: (data) => {
        taEl.innerHTML = TA_GROUPS.map(
          (group) => `
            <div class="indicator-group">
              <h3>${group.title}</h3>
              ${group.rows
                .map(([label, getValue]) => `<div class="indicator-row"><span>${label}</span><span>${getValue(data)}</span></div>`)
                .join("")}
            </div>`
        ).join("");
      },
    });
  }

  function renderAll() {
    if (!alive) return;
    renderHeader();
    renderChart();
    renderTa();
  }

  async function loadCandles(force) {
    if (force) invalidateCached(symbol, CANDLES_ENDPOINT);
    await loadInto(candlesSection, renderAll, () => withCache(symbol, CANDLES_ENDPOINT, () => getCandles(symbol)));
  }

  async function loadTa(force) {
    if (force) invalidateCached(symbol, TA_ENDPOINT);
    await loadInto(taSection, renderAll, () => withCache(symbol, TA_ENDPOINT, () => getTechnicalAnalysis(symbol)));
  }

  refreshBtn.addEventListener("click", () => {
    loadCandles(true);
    loadTa(true);
  });

  renderAll();
  loadCandles(false);
  loadTa(false);

  return () => {
    alive = false;
  };
}
