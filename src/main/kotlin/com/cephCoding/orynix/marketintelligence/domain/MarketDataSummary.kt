package com.cephCoding.orynix.marketintelligence.domain

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import java.time.Instant

data class MarketDataSummary(
    val symbol: String,
    val timeframe: CandleTimeframe,
    val latestCandle: Candle,
    val candleCount: Int,
    val earliestTimestamp: Instant,
    val latestTimestamp: Instant,
)
