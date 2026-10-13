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
)
