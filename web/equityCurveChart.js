import { formatDate } from "./format.js";
import { THEME_CHANGE_EVENT } from "./state.js";

const AXIS_PADDING_LEFT = 60;
const AXIS_PADDING_RIGHT = 12;
const AXIS_PADDING_TOP = 16;
const AXIS_PADDING_BOTTOM = 24;

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

function drawEmptyState(ctx, width, height, text) {
  ctx.clearRect(0, 0, width, height);
  ctx.fillStyle = themeColor("--muted");
  ctx.font = "13px -apple-system, \"Segoe UI\", Roboto, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(text, width / 2, height / 2);
}

function xForIndex(index, count, left, right) {
  if (count <= 1) return (left + right) / 2;
  return left + (index / (count - 1)) * (right - left);
}

/**
 * A small, dedicated line chart for a backtest equity curve ({timestamp, cash, equity} points).
 * Deliberately not a reuse of chart.js: that module is purpose-built for OHLCV candlesticks plus
 * a volume strip and does not fit this data shape. This mirrors its hand-rolled, DPR-aware,
 * theme-aware approach instead of reusing it, and is created/destroyed per page mount rather than
 * holding module-level singleton state, since this page (unlike the shared Market Analysis chart)
 * only ever has one instance alive at a time.
 */
export function createEquityCurveChart(canvas, tooltipEl) {
  let points = [];
  let currencyFormatter = (value) => String(value);

  function layout(width, height) {
    return { top: AXIS_PADDING_TOP, bottom: height - AXIS_PADDING_BOTTOM, left: AXIS_PADDING_LEFT, right: width - AXIS_PADDING_RIGHT };
  }

  function render() {
    const ctx = canvas.getContext("2d");
    const { width, height, dpr } = resizeCanvasToDisplaySize(canvas);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, width, height);

    if (points.length === 0) {
      drawEmptyState(ctx, width, height, "No equity curve for this run");
      return;
    }
    if (points.length === 1) {
      drawEmptyState(ctx, width, height, "Only one equity point - nothing to chart yet");
      return;
    }

    const { top, bottom, left, right } = layout(width, height);
    const equities = points.map((p) => Number(p.equity));
    const min = Math.min(...equities);
    const max = Math.max(...equities);
    const range = max - min || 1;
    const plotHeight = bottom - top;
    const yFor = (equity) => top + plotHeight - ((equity - min) / range) * plotHeight;
    const pointAt = (i) => ({ x: xForIndex(i, points.length, left, right), y: yFor(equities[i]) });

    ctx.strokeStyle = themeColor("--chart-grid");
    ctx.lineWidth = 1;
    for (let i = 0; i <= 3; i++) {
      const y = top + (plotHeight / 3) * i;
      ctx.beginPath();
      ctx.moveTo(left, y);
      ctx.lineTo(right, y);
      ctx.stroke();
    }

    ctx.beginPath();
    points.forEach((_, i) => {
      const { x, y } = pointAt(i);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    const lastPoint = pointAt(points.length - 1);
    const firstPoint = pointAt(0);
    ctx.lineTo(lastPoint.x, bottom);
    ctx.lineTo(firstPoint.x, bottom);
    ctx.closePath();
    ctx.fillStyle = themeColor("--chart-fill");
    ctx.fill();

    ctx.beginPath();
    points.forEach((_, i) => {
      const { x, y } = pointAt(i);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = themeColor("--chart-line");
    ctx.lineWidth = 2;
    ctx.stroke();

    ctx.fillStyle = themeColor("--fg");
    ctx.font = "11px -apple-system, \"Segoe UI\", Roboto, sans-serif";
    ctx.textAlign = "right";
    ctx.fillText(currencyFormatter(max), left - 6, top + 10);
    ctx.fillText(currencyFormatter(min), left - 6, bottom);

    ctx.fillStyle = themeColor("--muted");
    ctx.textAlign = "center";
    const labelCount = Math.min(5, points.length);
    if (labelCount >= 2) {
      for (let i = 0; i < labelCount; i++) {
        const index = Math.round((i / (labelCount - 1)) * (points.length - 1));
        const x = xForIndex(index, points.length, left, right);
        ctx.fillText(formatDate(points[index].timestamp), x, height - 6);
      }
    }
  }

  function hideTooltip() {
    if (tooltipEl) tooltipEl.hidden = true;
  }

  function handlePointerX(clientX) {
    if (!tooltipEl || points.length === 0) return;
    const rect = canvas.getBoundingClientRect();
    const x = clientX - rect.left;
    const { left, right } = layout(rect.width, rect.height);
    if (x < left || x > right) {
      hideTooltip();
      return;
    }
    const ratio = (x - left) / (right - left || 1);
    const index = Math.round(ratio * (points.length - 1));
    const point = points[Math.min(points.length - 1, Math.max(0, index))];

    tooltipEl.hidden = false;
    tooltipEl.innerHTML = `
      <div class="chart-tooltip-date">${formatDate(point.timestamp)}</div>
      <div class="chart-tooltip-row"><span>Equity</span><span>${currencyFormatter(point.equity)}</span></div>
      <div class="chart-tooltip-row"><span>Cash</span><span>${currencyFormatter(point.cash)}</span></div>
    `;
    const offsetX = Math.min(rect.width - 140, Math.max(0, x + 12));
    tooltipEl.style.left = `${offsetX}px`;
    tooltipEl.style.top = "8px";
  }

  function handleMouseMove(event) {
    handlePointerX(event.clientX);
  }

  function handleMouseLeave() {
    hideTooltip();
  }

  // Touch devices have no hover - without this the equity-curve tooltip, like the price chart's,
  // would only ever be reachable by mouse.
  function handleTouchMove(event) {
    if (event.touches.length === 0) return;
    event.preventDefault();
    handlePointerX(event.touches[0].clientX);
  }

  canvas.addEventListener("mousemove", handleMouseMove);
  canvas.addEventListener("mouseleave", handleMouseLeave);
  canvas.addEventListener("touchstart", handleTouchMove, { passive: false });
  canvas.addEventListener("touchmove", handleTouchMove, { passive: false });
  canvas.addEventListener("touchend", handleMouseLeave);
  canvas.addEventListener("touchcancel", handleMouseLeave);

  const resizeObserver = new ResizeObserver(() => render());
  resizeObserver.observe(canvas);

  const darkMediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
  const themeListener = () => render();
  darkMediaQuery.addEventListener("change", themeListener);
  // Manual theme-toggle override - darkMediaQuery only fires on a real OS scheme change, never
  // on this app's own in-page toggle (see THEME_CHANGE_EVENT in state.js).
  window.addEventListener(THEME_CHANGE_EVENT, themeListener);

  return {
    setPoints(newPoints, formatter) {
      points = newPoints || [];
      if (formatter) currencyFormatter = formatter;
      render();
    },
    destroy() {
      canvas.removeEventListener("mousemove", handleMouseMove);
      canvas.removeEventListener("mouseleave", handleMouseLeave);
      canvas.removeEventListener("touchstart", handleTouchMove);
      canvas.removeEventListener("touchmove", handleTouchMove);
      canvas.removeEventListener("touchend", handleMouseLeave);
      canvas.removeEventListener("touchcancel", handleMouseLeave);
      resizeObserver.disconnect();
      darkMediaQuery.removeEventListener("change", themeListener);
      window.removeEventListener(THEME_CHANGE_EVENT, themeListener);
    },
  };
}
