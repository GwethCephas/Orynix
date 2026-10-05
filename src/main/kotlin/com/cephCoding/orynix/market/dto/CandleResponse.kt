package com.cephCoding.orynix.market.dto

import com.cephCoding.orynix.market.domain.Candle
import java.math.BigDecimal
import java.time.Instant

data class CandleResponse(
    val symbol: String,
    val timeframe: String,
    val timestamp: Instant,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
) {
    companion object {
        fun from(candle: Candle) = CandleResponse(
            symbol = candle.symbol,
            timeframe = candle.timeframe.code,
            timestamp = candle.timestamp,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
    }
}
