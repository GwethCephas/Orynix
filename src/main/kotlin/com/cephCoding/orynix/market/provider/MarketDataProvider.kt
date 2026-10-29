package com.cephCoding.orynix.market.provider

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import java.time.LocalDate

interface MarketDataProvider {
    fun fetchCandles(symbol: String, from: LocalDate, to: LocalDate, timeframe: CandleTimeframe): List<Candle>
}
