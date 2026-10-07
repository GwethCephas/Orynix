let lastCandles = null;
let resizeObserver = null;

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

export function drawPriceChart(canvas, candles) {
  lastCandles = candles;

  const ctx = canvas.getContext("2d");
  const { width, height, dpr } = resizeCanvasToDisplaySize(canvas);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);

  if (!candles || candles.length < 2) {
    ctx.fillStyle = themeColor("--fg");
    ctx.font = "14px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("Not enough data to chart", width / 2, height / 2);
    return;
  }

  const sorted = [...candles].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
  const closes = sorted.map((c) => Number(c.close));
  const min = Math.min(...closes);
  const max = Math.max(...closes);
  const range = max - min || 1;

  const padding = 24;
  const plotWidth = width - padding * 2;
  const plotHeight = height - padding * 2;

  const pointAt = (i) => {
    const x = padding + (i / (closes.length - 1)) * plotWidth;
    const y = padding + plotHeight - ((closes[i] - min) / range) * plotHeight;
    return { x, y };
  };

  ctx.strokeStyle = themeColor("--chart-grid");
  ctx.lineWidth = 1;
  for (let i = 0; i <= 3; i++) {
    const y = padding + (plotHeight / 3) * i;
    ctx.beginPath();
    ctx.moveTo(padding, y);
    ctx.lineTo(width - padding, y);
    ctx.stroke();
  }

  ctx.beginPath();
  closes.forEach((_, i) => {
    const { x, y } = pointAt(i);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  const lastPoint = pointAt(closes.length - 1);
  const firstPoint = pointAt(0);
  ctx.lineTo(lastPoint.x, padding + plotHeight);
  ctx.lineTo(firstPoint.x, padding + plotHeight);
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

  ctx.fillStyle = themeColor("--fg");
  ctx.font = "12px sans-serif";
  ctx.textAlign = "left";
  ctx.fillText(max.toFixed(2), 4, padding);
  ctx.fillText(min.toFixed(2), 4, padding + plotHeight);
}

export function initChart(canvas) {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
  resizeObserver = new ResizeObserver(() => {
    if (lastCandles) {
      drawPriceChart(canvas, lastCandles);
    }
  });
  resizeObserver.observe(canvas.parentElement || canvas);

  window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
    if (lastCandles) {
      drawPriceChart(canvas, lastCandles);
    }
  });
}
