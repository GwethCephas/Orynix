import { deriveAssetHeader, formatCurrency, formatNumber, formatPercent, formatLargeNumber, formatDate, classifySignalColor, humanizeEnum } from "./format.js";
import { drawPriceChart } from "./chart.js";

function sectionStatusText(sectionState, loadingText, idleText) {
  switch (sectionState.status) {
    case "idle":
      return idleText;
    case "loading":
      return loadingText;
    case "error":
      return sectionState.error?.message || "Something went wrong.";
    case "ok":
      return null;
    default:
      return null;
  }
}

export function renderAssetHeader(container, candlesState, fundamentalsState, symbol) {
  const text = sectionStatusText(candlesState, `Loading ${symbol}...`, "");
  if (text !== null) {
    container.innerHTML = text ? `<p class="section-status">${text}</p>` : "";
    return;
  }

  const header = deriveAssetHeader(candlesState.data);
  const companyName =
    fundamentalsState.status === "ok" ? fundamentalsState.data.company.name : symbol;

  const changeClass = header.delta === null ? "neutral" : header.delta > 0 ? "positive" : header.delta < 0 ? "negative" : "neutral";
  const changeText =
    header.delta === null
      ? "N/A"
      : `${header.delta >= 0 ? "+" : ""}${formatNumber(header.delta)} (${formatPercent(header.deltaPercent)})`;

  container.innerHTML = `
    <div class="asset-name">${companyName}</div>
    <div class="asset-symbol">${symbol}</div>
    <div class="asset-price">${formatCurrency(header.latestClose)}</div>
    <div class="asset-change ${changeClass}">${changeText}</div>
    <div class="asset-asof">As of ${formatDate(header.timestamp)}</div>
  `;
}

export function renderPriceChart(canvas, statusEl, candlesState) {
  const text = sectionStatusText(candlesState, "Loading chart...", "Search for a symbol to begin.");
  if (text !== null) {
    statusEl.textContent = text;
    statusEl.hidden = false;
    return;
  }
  statusEl.hidden = true;
  drawPriceChart(canvas, candlesState.data);
}

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

export function renderTechnicalAnalysis(container, sectionState) {
  const text = sectionStatusText(sectionState, "Loading technical analysis...", "");
  if (text !== null) {
    container.innerHTML = text ? `<p class="section-status">${text}</p>` : "";
    return;
  }

  const data = sectionState.data;
  container.innerHTML = TA_GROUPS.map(
    (group) => `
      <div class="indicator-group">
        <h3>${group.title}</h3>
        ${group.rows
          .map(([label, getValue]) => `<div class="indicator-row"><span>${label}</span><span>${getValue(data)}</span></div>`)
          .join("")}
      </div>
    `
  ).join("");
}

export function renderSignals(container, sectionState) {
  const text = sectionStatusText(sectionState, "Loading signals...", "");
  if (text !== null) {
    container.innerHTML = text ? `<p class="section-status">${text}</p>` : "";
    return;
  }

  const data = sectionState.data;
  const badges = data.signals
    .map((signal) => {
      const color = classifySignalColor(signal.state);
      return `<div class="signal-badge ${color}"><span class="signal-type">${humanizeEnum(signal.type)}</span><span class="signal-state">${humanizeEnum(signal.state)}</span></div>`;
    })
    .join("");

  const biasClass = classifySignalColor(data.overallBias);

  container.innerHTML = `
    <div class="signal-summary">
      <div class="overall-bias ${biasClass}">Overall: ${humanizeEnum(data.overallBias)}</div>
      <div class="signal-counts">
        <span>Bullish: ${data.bullishSignalCount}</span>
        <span>Bearish: ${data.bearishSignalCount}</span>
        <span>Neutral: ${data.neutralSignalCount}</span>
      </div>
    </div>
    <div class="signal-grid">${badges}</div>
  `;
}

export function renderFundamentals(container, sectionState) {
  const text = sectionStatusText(sectionState, "Loading fundamentals...", "");
  if (text !== null) {
    container.innerHTML = text ? `<p class="section-status">${text}</p>` : "";
    return;
  }

  const company = sectionState.data.company;
  container.innerHTML = `
    <div class="indicator-group">
      <h3>Company</h3>
      <div class="indicator-row"><span>Name</span><span>${company.name ?? "N/A"}</span></div>
      <div class="indicator-row"><span>Symbol</span><span>${sectionState.data.symbol}</span></div>
      <div class="indicator-row"><span>Exchange</span><span>${company.exchange ?? "N/A"}</span></div>
      <div class="indicator-row"><span>Industry</span><span>${company.industry ?? "N/A"}</span></div>
      <div class="indicator-row"><span>Market Cap</span><span>${formatLargeNumber(company.marketCapitalization)}</span></div>
    </div>
  `;
}
