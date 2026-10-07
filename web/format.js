export function deriveAssetHeader(candles) {
  if (!candles || candles.length === 0) {
    return { latestClose: null, previousClose: null, delta: null, deltaPercent: null, timestamp: null };
  }

  const sorted = [...candles].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
  const latest = sorted[sorted.length - 1];
  const latestClose = Number(latest.close);
  const timestamp = latest.timestamp;

  if (sorted.length < 2) {
    return { latestClose, previousClose: null, delta: null, deltaPercent: null, timestamp };
  }

  const previous = sorted[sorted.length - 2];
  const previousClose = Number(previous.close);
  const delta = latestClose - previousClose;
  const deltaPercent = previousClose === 0 ? null : (delta / previousClose) * 100;

  return { latestClose, previousClose, delta, deltaPercent, timestamp };
}

export function formatNumber(value, decimals = 2) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  return Number(value).toFixed(decimals);
}

export function formatCurrency(value, decimals = 2) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  return `$${Number(value).toFixed(decimals)}`;
}

export function formatPercent(value, decimals = 2) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  const num = Number(value);
  const sign = num > 0 ? "+" : "";
  return `${sign}${num.toFixed(decimals)}%`;
}

export function formatLargeNumber(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  const num = Number(value);
  const units = [
    { threshold: 1e12, suffix: "T" },
    { threshold: 1e9, suffix: "B" },
    { threshold: 1e6, suffix: "M" },
    { threshold: 1e3, suffix: "K" },
  ];
  for (const unit of units) {
    if (Math.abs(num) >= unit.threshold) {
      return `$${(num / unit.threshold).toFixed(2)}${unit.suffix}`;
    }
  }
  return `$${num.toFixed(2)}`;
}

export function formatDate(timestamp) {
  if (!timestamp) {
    return "N/A";
  }
  return new Date(timestamp).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function classifySignalColor(state) {
  if (!state) {
    return "neutral";
  }
  if (state.includes("BULLISH") || state.includes("POSITIVE")) {
    return "positive";
  }
  if (state.includes("BEARISH") || state.includes("NEGATIVE")) {
    return "negative";
  }
  return "neutral";
}

export function humanizeEnum(value) {
  if (!value) {
    return "N/A";
  }
  return value
    .split("_")
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(" ");
}
