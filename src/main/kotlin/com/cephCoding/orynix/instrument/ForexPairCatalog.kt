package com.cephCoding.orynix.instrument

import com.cephCoding.orynix.instrument.domain.Instrument

/**
 * Static, hand-written descriptions for the forex pairs Orynix curates for discovery (mirrors
 * `stockUniverse.js`'s "short list for discovery convenience, not a live catalogue" precedent —
 * see CLAUDE.md Section 16). Massive has no instrument-catalogue/reference endpoint for forex
 * pairs (unlike `/v3/reference/tickers/{ticker}` for stocks), so there is no provider call this
 * could delegate to — this is the only source of the description field, by necessity, not choice.
 * A pair outside this curated set that still resolves (see InstrumentResolver's permissive regex)
 * gets a generic fallback description rather than failing.
 */
object ForexPairCatalog {

    private val DESCRIPTIONS: Map<String, String> = mapOf(
        "EUR/USD" to "Euro vs. US Dollar — the most heavily traded currency pair in the world.",
        "GBP/USD" to "British Pound vs. US Dollar, commonly referred to as \"Cable.\"",
        "USD/JPY" to "US Dollar vs. Japanese Yen, one of the most liquid pairs in Asian trading hours.",
        "USD/CHF" to "US Dollar vs. Swiss Franc, often treated as a safe-haven pair.",
        "AUD/USD" to "Australian Dollar vs. US Dollar, sensitive to commodity prices and China-linked demand.",
        "USD/CAD" to "US Dollar vs. Canadian Dollar, sensitive to oil prices given Canada's export profile.",
        "NZD/USD" to "New Zealand Dollar vs. US Dollar, sensitive to agricultural commodity prices.",
    )

    fun describe(instrument: Instrument): String =
        DESCRIPTIONS[instrument.displaySymbol]
            ?: "${instrument.baseCurrency}/${instrument.quoteCurrency} currency pair."
}
