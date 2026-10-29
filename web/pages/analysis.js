import { getCandles, getMarketIntelligence } from "../api.js";
import { getCurrentSymbol, withCache, invalidateCached } from "../state.js";
import { createSectionState, loadInto, renderStateBlock } from "../components/stateBlock.js";
import { renderContextSection } from "../components/contextSection.js";
import { deriveAssetHeader, formatPrice, isForexSymbol, formatNumber, formatPercent, formatDate, formatDateTime, escapeHtml } from "../format.js";
import {
  initChart,
  setChartCandles,
  setChartRange,
  setChartInterval,
  RANGE_OPTIONS,
  INTERVAL_OPTIONS,
  DEFAULT_INTERVAL_CODE,
  isPeriodCompatibleWithInterval,
} from "../chart.js";

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
const MARKET_INTELLIGENCE_ENDPOINT = "market-intelligence";
const DEFAULT_PERIOD_DAYS = 90;

// UTC calendar boundaries, deliberately — mirrors BacktestService's own
// fromDate.atStartOfDay(ZoneOffset.UTC) convention (CLAUDE.md Section 18) so a period selection
// means the same calendar window regardless of the browser's local timezone, and matches what
// the backend's range validation actually checks against.
function utcDateRange(periodDays) {
  const to = new Date();
  const from = new Date(to.getTime() - periodDays * 24 * 60 * 60 * 1000);
  const iso = (d) => d.toISOString().slice(0, 10);
  return { from: iso(from), to: iso(to) };
}

export function mount(container) {
  const symbol = getCurrentSymbol();

  if (!symbol) {
    container.innerHTML = `<div class="page"><div class="card empty-state"><p>Search for a symbol to begin analysis.</p></div></div>`;
    return;
  }

  let alive = true;
  const candlesSection = { value: createSectionState() };
  const contextSection = { value: createSectionState() };
  // Period/interval selection is local to this mounted page, like the range buttons already
  // were — it resets on remount, matching the pre-existing "always starts at 3M" behavior rather
  // than persisting across navigation the way paperTrading.js's selected account deliberately does.
  let currentInterval = DEFAULT_INTERVAL_CODE;
  let currentPeriodDays = DEFAULT_PERIOD_DAYS;
  // Guards against an out-of-order response: switching interval/period rapidly can start a
  // second fetch before the first resolves, and network ordering gives no guarantee the first
  // one won't resolve last. Only the response whose sequence number is still current is applied.
  let candlesRequestSeq = 0;

  container.innerHTML = `
    <div class="page analysis-page">
      <section class="card asset-header" id="assetHeader"></section>

      <section class="card chart-card">
        <div class="chart-card-header">
          <div class="chart-control-stack">
            <div class="chart-control-group">
              <span class="chart-control-label">Period</span>
              <div class="chart-range-controls" id="rangeControls"></div>
            </div>
            <div class="chart-control-group">
              <span class="chart-control-label">Interval</span>
              <div class="chart-interval-controls" id="intervalControls"></div>
            </div>
          </div>
          <button type="button" class="refresh-btn" id="refreshBtn">Refresh</button>
        </div>
        <div class="chart-wrapper">
          <canvas class="price-chart" id="priceChart"></canvas>
          <div class="chart-tooltip" id="chartTooltip" hidden></div>
        </div>
        <div id="chartStatus"></div>
      </section>

      <section class="card">
        <div class="section-header">
          <h2>Technical Analysis</h2>
          <span class="section-asof" id="taAsOf"></span>
        </div>
        <p class="section-disclosure">Technical analysis, signals, and the AI market analysis agent always evaluate
          daily candles, regardless of the chart's selected interval above — this does not change with intraday
          chart views.</p>
        <div id="technicalAnalysis"></div>
      </section>
    </div>`;

  const assetHeaderEl = container.querySelector("#assetHeader");
  const chartStatusEl = container.querySelector("#chartStatus");
  const canvas = container.querySelector("#priceChart");
  const tooltipEl = container.querySelector("#chartTooltip");
  const taEl = container.querySelector("#technicalAnalysis");
  const taAsOfEl = container.querySelector("#taAsOf");
  const rangeControlsEl = container.querySelector("#rangeControls");
  const intervalControlsEl = container.querySelector("#intervalControls");
  const refreshBtn = container.querySelector("#refreshBtn");

  initChart(canvas, tooltipEl);

  function renderRangeButtons() {
    rangeControlsEl.innerHTML = RANGE_OPTIONS.map((opt) => {
      const compatible = isPeriodCompatibleWithInterval(opt.days, currentInterval);
      const active = opt.days === currentPeriodDays;
      return `<button type="button" class="range-btn ${active ? "active" : ""}" data-days="${opt.days ?? ""}" ${compatible ? "" : "disabled"}>${opt.label}</button>`;
    }).join("");
    rangeControlsEl.querySelectorAll(".range-btn:not(:disabled)").forEach((btn) => {
      btn.addEventListener("click", () => {
        currentPeriodDays = btn.dataset.days ? Number(btn.dataset.days) : null;
        renderRangeButtons();
        if (currentInterval === DEFAULT_INTERVAL_CODE) {
          // Pure client-side slice over the already-fetched daily series — no network request,
          // preserving the exact behavior this page has always had for the daily interval.
          setChartRange(currentPeriodDays);
        } else {
          loadCandles(false);
        }
      });
    });
  }

  function renderIntervalButtons() {
    intervalControlsEl.innerHTML = INTERVAL_OPTIONS.map(
      (opt) => `<button type="button" class="range-btn ${opt.code === currentInterval ? "active" : ""}" data-interval="${opt.code}">${opt.label}</button>`
    ).join("");
    intervalControlsEl.querySelectorAll(".range-btn").forEach((btn) => {
      btn.addEventListener("click", () => {
        const interval = btn.dataset.interval;
        if (interval === currentInterval) return;
        currentInterval = interval;
        if (!isPeriodCompatibleWithInterval(currentPeriodDays, currentInterval)) {
          // The previously-selected period doesn't apply to this interval (e.g. "All" with
          // "15m") — fall back to the first compatible period in RANGE_OPTIONS' own order
          // (shortest first) rather than leaving an incompatible combination selected.
          const fallback = RANGE_OPTIONS.find((opt) => isPeriodCompatibleWithInterval(opt.days, currentInterval));
          currentPeriodDays = fallback ? fallback.days : currentPeriodDays;
        }
        renderIntervalButtons();
        renderRangeButtons();
        loadCandles(false);
      });
    });
  }

  renderRangeButtons();
  renderIntervalButtons();

  // Labels the chart's own data-freshness line using whichever bar the chart is actually
  // showing — never claims real-time; Massive itself tags every aggregates response (daily and
  // intraday alike) "status":"DELAYED" (verified live, 2026-10-29), so "historical, not
  // real-time" holds unconditionally regardless of interval.
  function freshnessLabel(timestamp) {
    if (currentInterval === DEFAULT_INTERVAL_CODE) {
      return `Daily close as of ${formatDate(timestamp)}`;
    }
    const intervalLabel = INTERVAL_OPTIONS.find((opt) => opt.code === currentInterval)?.label ?? currentInterval;
    return `${intervalLabel} candle as of ${formatDateTime(timestamp)}`;
  }

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
            : `${header.delta >= 0 ? "+" : ""}${formatNumber(header.delta, isForexSymbol(symbol) ? 4 : 2)} (${formatPercent(header.deltaPercent)})`;
        assetHeaderEl.innerHTML = `
          <div class="asset-symbol">${escapeHtml(symbol)}</div>
          <div class="asset-price">${formatPrice(header.latestClose, symbol)}</div>
          <div class="asset-change ${changeClass}">${changeText}</div>
          <div class="asset-asof">${freshnessLabel(header.timestamp)} &middot; historical data, not real-time</div>`;
      },
    });
  }

  function renderChart() {
    renderStateBlock(chartStatusEl, candlesSection.value, {
      loadingLines: 0,
      onRetry: () => loadCandles(true),
      renderContent: (candles, el) => {
        el.innerHTML = "";
        setChartInterval(currentInterval);
        setChartCandles(candles, (value) => formatPrice(value, symbol));
      },
    });
    if (candlesSection.value.status === "loading") {
      chartStatusEl.innerHTML = `<p class="section-status">Loading chart...</p>`;
    }
  }

  function renderTa() {
    taAsOfEl.textContent = "";
    renderStateBlock(taEl, contextSection.value, {
      loadingLines: 4,
      onRetry: () => loadContext(true),
      renderContent: (context) => {
        taAsOfEl.textContent = `As of ${formatDateTime(context.asOf)}`;
        renderContextSection(taEl, context.technicalAnalysis, {
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
      },
    });
  }

  function renderAll() {
    if (!alive) return;
    renderHeader();
    renderChart();
    renderTa();
  }

  // The daily interval keeps the original single shared cache entry, since every period button
  // under it slices client-side over the same one 365-day fetch (CLAUDE.md's chart.js note).
  // Every intraday interval gets its own cache entry per period+interval combo, since each one
  // is a genuinely different, independently-scoped backend request.
  function candlesCacheKey(interval, periodDays) {
    return interval === DEFAULT_INTERVAL_CODE ? CANDLES_ENDPOINT : `${CANDLES_ENDPOINT}:${interval}:${periodDays}`;
  }

  function fetchCandlesForCombo(interval, periodDays) {
    if (interval === DEFAULT_INTERVAL_CODE) return getCandles(symbol);
    const { from, to } = utcDateRange(periodDays);
    return getCandles(symbol, { timeframe: interval, from, to });
  }

  async function loadCandles(force) {
    const interval = currentInterval;
    const periodDays = currentPeriodDays;
    const cacheKey = candlesCacheKey(interval, periodDays);
    if (force) invalidateCached(symbol, cacheKey);

    const mySeq = ++candlesRequestSeq;
    candlesSection.value = { status: "loading", data: null, error: null };
    renderAll();
    try {
      const data = await withCache(symbol, cacheKey, () => fetchCandlesForCombo(interval, periodDays));
      if (mySeq !== candlesRequestSeq) return; // superseded by a newer interval/period selection
      candlesSection.value = { status: "ok", data, error: null };
    } catch (error) {
      if (mySeq !== candlesRequestSeq) return;
      candlesSection.value = { status: "error", data: null, error };
    }
    renderAll();
  }

  async function loadContext(force) {
    if (force) invalidateCached(symbol, MARKET_INTELLIGENCE_ENDPOINT);
    await loadInto(contextSection, renderAll, () =>
      withCache(symbol, MARKET_INTELLIGENCE_ENDPOINT, () => getMarketIntelligence(symbol))
    );
  }

  refreshBtn.addEventListener("click", () => {
    loadCandles(true);
    loadContext(true);
  });

  renderAll();
  loadCandles(false);
  loadContext(false);

  return () => {
    alive = false;
  };
}
