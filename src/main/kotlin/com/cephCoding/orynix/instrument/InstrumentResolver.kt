package com.cephCoding.orynix.instrument

import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.instrument.domain.Instrument

/**
 * The single place a raw user-supplied symbol is classified and normalized, replacing the
 * identical stock-only regex that used to be duplicated independently in MarketDataService and
 * MarketIntelligenceService (see CLAUDE.md Section 9's "don't share a validator for four lines of
 * code" precedent — forex support is the second concrete need that now justifies sharing it).
 *
 * Deliberately permissive, matching the existing stock regex's philosophy: a syntactically
 * well-formed symbol that Massive doesn't actually have data for (e.g. "ZZZ/QQQ") fails at the
 * provider call with a normal MarketDataProviderException/empty result, exactly like an unknown
 * stock ticker does today — this resolver only rejects malformed input, it does not gate on the
 * curated pair list (that list is a frontend discovery aid only, see forexUniverse.js).
 */
object InstrumentResolver {

    private val STOCK_PATTERN = Regex("^[A-Z.]{1,10}$")
    private val FOREX_PATTERN = Regex("^([A-Z]{3})/([A-Z]{3})$")

    fun resolve(symbolRaw: String): Instrument {
        val symbol = symbolRaw.trim().uppercase()

        val forexMatch = FOREX_PATTERN.matchEntire(symbol)
        if (forexMatch != null) {
            val (base, quote) = forexMatch.destructured
            return Instrument(
                assetType = AssetType.FOREX,
                displaySymbol = "$base/$quote",
                providerSymbol = "C:$base$quote",
                baseCurrency = base,
                quoteCurrency = quote,
            )
        }

        if (STOCK_PATTERN.matches(symbol)) {
            return Instrument(
                assetType = AssetType.STOCK,
                displaySymbol = symbol,
                providerSymbol = symbol,
            )
        }

        throw InvalidSymbolException(symbolRaw)
    }
}
