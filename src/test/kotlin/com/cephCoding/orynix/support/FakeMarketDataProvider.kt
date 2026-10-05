package com.cephCoding.orynix.support

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.provider.MarketDataProvider
import java.time.LocalDate

class FakeMarketDataProvider : MarketDataProvider {
    var candlesToReturn: List<Candle> = emptyList()
    var failure: MarketDataProviderException? = null

    override fun fetchDailyCandles(symbol: String, from: LocalDate, to: LocalDate): List<Candle> {
        failure?.let { throw it }
        return candlesToReturn
    }

    fun reset() {
        candlesToReturn = emptyList()
        failure = null
    }
}
