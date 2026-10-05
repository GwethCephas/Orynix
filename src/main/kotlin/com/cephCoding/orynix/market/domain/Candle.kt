package com.cephCoding.orynix.market.domain

import java.math.BigDecimal
import java.time.Instant

data class Candle(
    val symbol: String,
    val timeframe: CandleTimeframe,
    val timestamp: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
)
