package com.cephCoding.orynix.market.provider

import com.cephCoding.orynix.market.domain.Candle
import java.time.LocalDate

interface MarketDataProvider {
    fun fetchDailyCandles(symbol: String, from: LocalDate, to: LocalDate): List<Candle>
}
