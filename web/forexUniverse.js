// Curated list of forex pairs for discovery convenience, mirroring stockUniverse.js's role and
// limitations exactly — not a live, market-wide pair search. Each pair was verified live against
// Massive's aggregates endpoint (the same one MarketDataService calls) before being added here,
// not assumed; see CLAUDE.md's forex extension for how "EUR/USD" maps to Massive's "C:EURUSD"
// ticker format server-side. Any correctly-formatted pair (^[A-Z]{3}/[A-Z]{3}$, see format.js's
// isValidSymbol) is still attempted on demand via the plain text search — this list does not
// define what Orynix "supports."
export const FOREX_UNIVERSE = [
  { symbol: "EUR/USD", name: "Euro / US Dollar" },
  { symbol: "GBP/USD", name: "British Pound / US Dollar" },
  { symbol: "USD/JPY", name: "US Dollar / Japanese Yen" },
  { symbol: "USD/CHF", name: "US Dollar / Swiss Franc" },
  { symbol: "AUD/USD", name: "Australian Dollar / US Dollar" },
  { symbol: "USD/CAD", name: "US Dollar / Canadian Dollar" },
  { symbol: "NZD/USD", name: "New Zealand Dollar / US Dollar" },
];
