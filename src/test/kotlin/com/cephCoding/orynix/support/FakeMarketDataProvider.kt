package com.cephCoding.orynix.support

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.provider.MarketDataProvider
import java.time.LocalDate

class FakeMarketDataProvider : MarketDataProvider {
    var candlesToReturn: List<Candle> = emptyList()
    var failure: MarketDataProviderException? = null

    // Additive, backward-compatible: checked first, falling back to candlesToReturn when the
    // symbol isn't present, so every existing test that only sets candlesToReturn keeps passing
    // unedited. Needed by paper trading's tests to give two different symbols (e.g. a long leg
    // and a short leg) different prices within the same test (CLAUDE.md Section 22).
    var candlesBySymbol: MutableMap<String, List<Candle>> = mutableMapOf()

    override fun fetchCandles(symbol: String, from: LocalDate, to: LocalDate, timeframe: CandleTimeframe): List<Candle> {
        failure?.let { throw it }
        return candlesBySymbol[symbol] ?: candlesToReturn
    }

    fun reset() {
        candlesToReturn = emptyList()
        candlesBySymbol = mutableMapOf()
        failure = null
    }
}
