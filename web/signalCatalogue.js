// Static catalogue of the real signal types computed by the backend
// (com.cephCoding.orynix.signals.domain.SignalType / SignalAnalysisService). The backend exposes
// no catalogue endpoint — SignalAnalysisController only returns a per-symbol result — so this list
// is hand-maintained here rather than fetched. Descriptions restate exactly what each rule in
// signals/rule/*.kt computes, using the real thresholds from SignalAnalysisService's companion
// object, not invented values. Keep this in sync if those thresholds ever change.
export const SIGNAL_CATALOGUE = [
  {
    type: "TREND",
    label: "Trend",
    description: "Bullish when EMA20 is above SMA20 and SMA20 is above SMA50; bearish when reversed; otherwise neutral.",
  },
  {
    type: "CROSSOVER",
    label: "Crossover",
    description: "Fires only on an EMA20/SMA50 crossover between the previous and current period — already being above or below is not a crossover.",
  },
  {
    type: "RSI",
    label: "RSI",
    description: "Overbought at RSI14 of 70 or higher, oversold at 30 or lower, otherwise neutral.",
  },
  {
    type: "MACD",
    label: "MACD",
    description: "A MACD/signal line crossover takes priority; otherwise reflects bullish or bearish momentum from the histogram's sign.",
  },
  {
    type: "BOLLINGER_BANDS",
    label: "Bollinger Bands",
    description: "Price above the upper band, below the lower band, within 1% of either band, or within the bands.",
  },
  {
    type: "VOLATILITY",
    label: "Volatility",
    description: "Low, normal, or high based on ATR14 as a percentage of price (below 1% is low, above 3% is high).",
  },
  {
    type: "VOLUME",
    label: "Volume",
    description: "High, normal, or low based on the latest volume relative to its 20-period average (1.5x or more is high, 0.5x or less is low).",
  },
  {
    type: "PRICE_MOMENTUM",
    label: "Price Momentum",
    description: "Positive, negative, or flat based on the percentage close change over the last 10 candles (within 1% is flat).",
  },
];
