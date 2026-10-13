import { formatDate, formatCurrency, formatNumber } from "./format.js";

const VOLUME_STRIP_RATIO = 0.22;
const MIN_CANDLE_WIDTH_PX = 3;
const AXIS_PADDING_LEFT = 48;
const AXIS_PADDING_RIGHT = 12;
const AXIS_PADDING_TOP = 16;
const AXIS_PADDING_BOTTOM = 24;

export const RANGE_OPTIONS = [
  { label: "1M", days: 30 },
  { label: "3M", days: 90 },
  { label: "6M", days: 180 },
  { label: "1Y", days: 365 },
  { label: "All", days: null },
];

const chartState = {
  canvas: null,
  tooltipEl: null,
  allCandles: [],
  rangeDays: 90,
  resizeObserver: null,
  themeListener: null,
  mouseMoveHandler: null,
  mouseLeaveHandler: null,
  // Defaults to the stock "$X.XX" formatter; setChartCandles passes a forex-aware formatter
  // (4-decimal, no "$") when the symbol being charted is a forex pair — see format.js's
  // formatPrice/isForexSymbol.
  priceFormatter: formatCurrency,
};

function themeColor(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

function resizeCanvasToDisplaySize(canvas) {
  const dpr = window.devicePixelRatio || 1;
  const displayWidth = canvas.clientWidth;
  const displayHeight = canvas.clientHeight;
  const targetWidth = Math.round(displayWidth * dpr);
  const targetHeight = Math.round(displayHeight * dpr);

  if (canvas.width !== targetWidth || canvas.height !== targetHeight) {
    canvas.width = targetWidth;
    canvas.height = targetHeight;
  }
  return { width: displayWidth, height: displayHeight, dpr };
}

function visibleCandles() {
  if (!chartState.allCandles.length) return [];
  if (!chartState.rangeDays) return chartState.allCandles;
  return chartState.allCandles.slice(-chartState.rangeDays);
}

function layout(width, height) {
  const volumeHeight = height * VOLUME_STRIP_RATIO;
  const priceTop = AXIS_PADDING_TOP;
  const priceBottom = height - volumeHeight - 8;
  const volumeTop = height - volumeHeight;
  const volumeBottom = height - AXIS_PADDING_BOTTOM;
  const plotLeft = AXIS_PADDING_LEFT;
  const plotRight = width - AXIS_PADDING_RIGHT;
  return { priceTop, priceBottom, volumeTop, volumeBottom, plotLeft, plotRight };
}

function xForIndex(index, count, plotLeft, plotRight) {
  if (count <= 1) return (plotLeft + plotRight) / 2;
  return plotLeft + (index / (count - 1)) * (plotRight - plotLeft);
}

function drawEmptyState(ctx, width, height, text) {
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = themeColor("--muted");
  ctx.font = "13px sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(text, width / 2, height / 2);
}

function drawGrid(ctx, plotLeft, plotRight, priceTop, priceBottom) {
  ctx.strokeStyle = themeColor("--chart-grid");
  ctx.lineWidth = 1;
  for (let i = 0; i <= 3; i++) {
    const y = priceTop + ((priceBottom - priceTop) / 3) * i;
    ctx.beginPath();
    ctx.moveTo(plotLeft, y);
    ctx.lineTo(plotRight, y);
    ctx.stroke();
  }
}

function drawDateAxis(ctx, candles, plotLeft, plotRight, y) {
  ctx.fillStyle = themeColor("--muted");
  ctx.font = "11px sans-serif";
  ctx.textAlign = "center";
  const labelCount = Math.min(5, candles.length);
  if (labelCount < 2) return;
  for (let i = 0; i < labelCount; i++) {
    const index = Math.round((i / (labelCount - 1)) * (candles.length - 1));
    const x = xForIndex(index, candles.length, plotLeft, plotRight);
    ctx.fillText(formatDate(candles[index].timestamp), x, y);
  }
}

function drawLineChart(ctx, candles, plotLeft, plotRight, priceTop, priceBottom) {
  const closes = candles.map((c) => Number(c.close));
  const min = Math.min(...closes);
  const max = Math.max(...closes);
  const range = max - min || 1;
  const plotHeight = priceBottom - priceTop;

  const pointAt = (i) => ({
    x: xForIndex(i, closes.length, plotLeft, plotRight),
    y: priceTop + plotHeight - ((closes[i] - min) / range) * plotHeight,
  });

  ctx.beginPath();
  closes.forEach((_, i) => {
    const { x, y } = pointAt(i);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  const lastPoint = pointAt(closes.length - 1);
  const firstPoint = pointAt(0);
  ctx.lineTo(lastPoint.x, priceBottom);
  ctx.lineTo(firstPoint.x, priceBottom);
  ctx.closePath();
  ctx.fillStyle = themeColor("--chart-fill");
  ctx.fill();

  ctx.beginPath();
  closes.forEach((_, i) => {
    const { x, y } = pointAt(i);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.strokeStyle = themeColor("--chart-line");
  ctx.lineWidth = 2;
  ctx.stroke();

  return { min, max, priceForY: (close) => priceTop + plotHeight - ((close - min) / range) * plotHeight };
}

function drawCandlesticks(ctx, candles, plotLeft, plotRight, priceTop, priceBottom) {
  const highs = candles.map((c) => Number(c.high));
  const lows = candles.map((c) => Number(c.low));
  const min = Math.min(...lows);
  const max = Math.max(...highs);
  const range = max - min || 1;
  const plotHeight = priceBottom - priceTop;
  const yFor = (price) => priceTop + plotHeight - ((price - min) / range) * plotHeight;

  const slotWidth = candles.length > 1 ? (plotRight - plotLeft) / candles.length : plotRight - plotLeft;
  const bodyWidth = Math.max(1, slotWidth * 0.6);

  candles.forEach((candle, i) => {
    const x = xForIndex(i, candles.length, plotLeft, plotRight);
    const open = Number(candle.open);
    const close = Number(candle.close);
    const high = Number(candle.high);
    const low = Number(candle.low);
    const isUp = close >= open;
    const color = themeColor(isUp ? "--positive" : "--negative");

    ctx.strokeStyle = color;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x, yFor(high));
    ctx.lineTo(x, yFor(low));
    ctx.stroke();

    const bodyTop = yFor(Math.max(open, close));
    const bodyBottom = yFor(Math.min(open, close));
    ctx.fillStyle = color;
    ctx.fillRect(x - bodyWidth / 2, bodyTop, bodyWidth, Math.max(1, bodyBottom - bodyTop));
  });

  return { min, max };
}

function drawVolume(ctx, candles, plotLeft, plotRight, volumeTop, volumeBottom) {
  const volumes = candles.map((c) => Number(c.volume));
  const maxVolume = Math.max(...volumes, 1);
  const plotHeight = volumeBottom - volumeTop;
  const slotWidth = candles.length > 1 ? (plotRight - plotLeft) / candles.length : plotRight - plotLeft;
  const barWidth = Math.max(1, slotWidth * 0.6);

  candles.forEach((candle, i) => {
    const x = xForIndex(i, candles.length, plotLeft, plotRight);
    const open = Number(candle.open);
    const close = Number(candle.close);
    const isUp = close >= open;
    const barHeight = (Number(candle.volume) / maxVolume) * plotHeight;
    ctx.fillStyle = themeColor(isUp ? "--positive" : "--negative");
    ctx.globalAlpha = 0.5;
    ctx.fillRect(x - barWidth / 2, volumeBottom - barHeight, barWidth, barHeight);
    ctx.globalAlpha = 1;
  });
}

function drawPriceLabels(ctx, min, max, plotLeft, priceTop, priceBottom) {
  ctx.fillStyle = themeColor("--fg");
  ctx.font = "11px sans-serif";
  ctx.textAlign = "right";
  ctx.fillText(chartState.priceFormatter(max), plotLeft - 6, priceTop + 10);
  ctx.fillText(chartState.priceFormatter(min), plotLeft - 6, priceBottom);
}

function render() {
  const canvas = chartState.canvas;
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  const { width, height, dpr } = resizeCanvasToDisplaySize(canvas);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);

  const candles = visibleCandles();
  if (candles.length < 2) {
    drawEmptyState(ctx, width, height, "Not enough data to chart");
    return;
  }

  const { priceTop, priceBottom, volumeTop, volumeBottom, plotLeft, plotRight } = layout(width, height);
  drawGrid(ctx, plotLeft, plotRight, priceTop, priceBottom);

  const slotWidth = (plotRight - plotLeft) / candles.length;
  const useCandlesticks = slotWidth >= MIN_CANDLE_WIDTH_PX;

  const { min, max } = useCandlesticks
    ? drawCandlesticks(ctx, candles, plotLeft, plotRight, priceTop, priceBottom)
    : drawLineChart(ctx, candles, plotLeft, plotRight, priceTop, priceBottom);

  drawPriceLabels(ctx, min, max, plotLeft, priceTop, priceBottom);
  drawVolume(ctx, candles, plotLeft, plotRight, volumeTop, volumeBottom);
  drawDateAxis(ctx, candles, plotLeft, plotRight, height - 6);
}

function hideTooltip() {
  if (chartState.tooltipEl) {
    chartState.tooltipEl.hidden = true;
  }
}

function handleMouseMove(event) {
  const canvas = chartState.canvas;
  const tooltipEl = chartState.tooltipEl;
  if (!canvas || !tooltipEl) return;

  const candles = visibleCandles();
  if (candles.length < 2) {
    hideTooltip();
    return;
  }

  const rect = canvas.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const { plotLeft, plotRight } = layout(rect.width, rect.height);

  if (x < plotLeft || x > plotRight) {
    hideTooltip();
    return;
  }

  const ratio = (x - plotLeft) / (plotRight - plotLeft || 1);
  const index = Math.round(ratio * (candles.length - 1));
  const candle = candles[Math.min(candles.length - 1, Math.max(0, index))];

  tooltipEl.hidden = false;
  tooltipEl.innerHTML = `
    <div class="chart-tooltip-date">${formatDate(candle.timestamp)}</div>
    <div class="chart-tooltip-row"><span>O</span><span>${chartState.priceFormatter(candle.open)}</span></div>
    <div class="chart-tooltip-row"><span>H</span><span>${chartState.priceFormatter(candle.high)}</span></div>
    <div class="chart-tooltip-row"><span>L</span><span>${chartState.priceFormatter(candle.low)}</span></div>
    <div class="chart-tooltip-row"><span>C</span><span>${chartState.priceFormatter(candle.close)}</span></div>
    <div class="chart-tooltip-row"><span>Vol</span><span>${formatNumber(candle.volume, 0)}</span></div>
  `;

  const offsetX = Math.min(rect.width - 140, Math.max(0, x + 12));
  tooltipEl.style.left = `${offsetX}px`;
  tooltipEl.style.top = "8px";
}

export function initChart(canvas, tooltipEl) {
  if (chartState.resizeObserver) {
    chartState.resizeObserver.disconnect();
  }
  if (chartState.canvas && chartState.mouseMoveHandler) {
    chartState.canvas.removeEventListener("mousemove", chartState.mouseMoveHandler);
    chartState.canvas.removeEventListener("mouseleave", chartState.mouseLeaveHandler);
  }
  if (chartState.themeListener) {
    window.matchMedia("(prefers-color-scheme: dark)").removeEventListener("change", chartState.themeListener);
  }

  chartState.canvas = canvas;
  chartState.tooltipEl = tooltipEl || null;
  // A fresh mount (e.g. navigating from a forex pair to a stock) must not render the previous
  // symbol's candles or price formatter for the brief window before the new symbol's candles
  // arrive — setChartCandles() is only called once loading resolves to "ok", but the
  // ResizeObserver/theme-change listeners below can trigger a render() before then.
  chartState.allCandles = [];
  chartState.priceFormatter = formatCurrency;

  chartState.resizeObserver = new ResizeObserver(() => render());
  chartState.resizeObserver.observe(canvas.parentElement || canvas);

  chartState.themeListener = () => render();
  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", chartState.themeListener);

  chartState.mouseMoveHandler = handleMouseMove;
  chartState.mouseLeaveHandler = hideTooltip;
  canvas.addEventListener("mousemove", chartState.mouseMoveHandler);
  canvas.addEventListener("mouseleave", chartState.mouseLeaveHandler);
}

export function setChartCandles(candles, priceFormatter = formatCurrency) {
  chartState.allCandles = candles ? [...candles].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp)) : [];
  chartState.priceFormatter = priceFormatter;
  render();
}

export function setChartRange(days) {
  chartState.rangeDays = days;
  render();
}

export function getChartRangeDays() {
  return chartState.rangeDays;
}
