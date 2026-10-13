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

export function formatDateTime(timestamp) {
  if (!timestamp) {
    return "N/A";
  }
  return new Date(timestamp).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function formatRelativeTime(timestamp) {
  if (!timestamp) {
    return "N/A";
  }
  const then = new Date(timestamp).getTime();
  if (Number.isNaN(then)) {
    return "N/A";
  }
  const diffMs = Date.now() - then;
  const diffMinutes = Math.round(diffMs / 60_000);

  if (diffMinutes < 1) return "just now";
  if (diffMinutes < 60) return `${diffMinutes}m ago`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.round(diffHours / 24);
  if (diffDays < 30) return `${diffDays}d ago`;
  return formatDate(timestamp);
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

const HTML_ESCAPES = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;",
};

export function escapeHtml(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch]);
}

export function isSafeHttpUrl(value) {
  if (!value || typeof value !== "string") {
    return false;
  }
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

const SENTIMENT_COLOR = {
  POSITIVE: "positive",
  NEGATIVE: "negative",
  NEUTRAL: "neutral",
};

export function classifySentimentColor(sentiment) {
  return SENTIMENT_COLOR[sentiment] || "neutral";
}

export function formatSentimentScore(score) {
  if (score === null || score === undefined || Number.isNaN(Number(score))) {
    return "N/A";
  }
  const num = Number(score);
  const sign = num > 0 ? "+" : "";
  return `${sign}${num.toFixed(2)}`;
}

const STOCK_SYMBOL_PATTERN = /^[A-Z.]{1,10}$/;
const FOREX_SYMBOL_PATTERN = /^[A-Z]{3}\/[A-Z]{3}$/;

export function isValidSymbol(symbol) {
  return typeof symbol === "string" && (STOCK_SYMBOL_PATTERN.test(symbol) || FOREX_SYMBOL_PATTERN.test(symbol));
}

// A forex display symbol is the only shape in Orynix that contains a "/" (e.g. "EUR/USD") — see
// CLAUDE.md's forex extension. Used purely for display-formatting decisions (decimals, no $
// prefix); backend asset-type classification is the source of truth and lives in
// InstrumentResolver, not here.
export function isForexSymbol(symbol) {
  return typeof symbol === "string" && symbol.includes("/");
}

// Forex quotes need more decimal precision than stock prices and have no single universal
// currency prefix (EUR/USD is priced in USD, USD/JPY in JPY, etc.) — showing "$" on every pair
// would be actively wrong, so forex prices are shown as a plain, unprefixed number instead.
export function formatPrice(value, symbol, decimals = 4) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "N/A";
  }
  if (isForexSymbol(symbol)) {
    return Number(value).toFixed(decimals);
  }
  return formatCurrency(value);
}
