package com.cephCoding.orynix.instrument.domain

/**
 * `displaySymbol` is what Orynix shows and stores everywhere (REST responses, `market_data_candles.symbol`,
 * cache keys) — e.g. "AAPL" or "EUR/USD". `providerSymbol` is only ever used as the literal ticker string sent
 * to Massive — e.g. "AAPL" (identical for stocks) or "C:EURUSD" (Massive's forex ticker prefix convention) —
 * and must never leak into a persisted row or an API response.
 */
data class Instrument(
    val assetType: AssetType,
    val displaySymbol: String,
    val providerSymbol: String,
    val baseCurrency: String? = null,
    val quoteCurrency: String? = null,
) {
    /**
     * The one account currency this instrument can be traded/backtested/risk-sized against: USD for
     * a stock (Orynix models no per-stock trading currency), or the pair's own quote currency for
     * forex (price movement is quote-currency-denominated). Additive — `BacktestService.validateAccountCurrency`
     * predates this and is left untouched; this property exists for new callers (trading account
     * profiles, the risk calculator) that need the same rule without duplicating it.
     */
    val requiredAccountCurrency: String
        get() = when (assetType) {
            AssetType.STOCK -> "USD"
            AssetType.FOREX -> quoteCurrency ?: error("FOREX instrument missing quoteCurrency: $displaySymbol")
        }
}
