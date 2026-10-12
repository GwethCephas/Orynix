// Small curated list of well-known, liquid symbols for discovery convenience only — this is NOT a
// live, market-wide symbol search. Orynix's backend has no symbol-catalogue, watchlist, or
// instrument-search endpoint anywhere (MarketDataController/FundamentalAnalysisController/etc. all
// take a symbol the caller already knows); any correctly-formatted ticker (^[A-Z.]{1,10}$, see
// format.js's isValidSymbol) is still attempted against Massive on demand via the plain text
// search — this list does not define what Orynix "supports," it's just a shortlist worth showing
// before the user types anything. Each entry was verified live against the real Massive-backed
// GET /api/v1/fundamentals before being added here, not assumed.
export const STOCK_UNIVERSE = [
  { symbol: "AAPL", name: "Apple Inc." },
  { symbol: "IBM", name: "International Business Machines Corporation" },
  { symbol: "MSFT", name: "Microsoft Corp" },
  { symbol: "NVDA", name: "Nvidia Corp" },
  { symbol: "GOOGL", name: "Alphabet Inc. Class A Common Stock" },
  { symbol: "AMZN", name: "Amazon.Com Inc" },
];
