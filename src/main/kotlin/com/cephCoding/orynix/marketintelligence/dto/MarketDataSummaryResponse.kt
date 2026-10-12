package com.cephCoding.orynix.marketintelligence.dto

import com.cephCoding.orynix.market.dto.CandleResponse
import com.cephCoding.orynix.marketintelligence.domain.MarketDataSummary
import java.time.Instant

data class MarketDataSummaryResponse(
    val symbol: String,
    val timeframe: String,
    val latestCandle: CandleResponse,
    val candleCount: Int,
    val earliestTimestamp: Instant,
    val latestTimestamp: Instant,
) {
    companion object {
        fun from(summary: MarketDataSummary) = MarketDataSummaryResponse(
            symbol = summary.symbol,
            timeframe = summary.timeframe.code,
            latestCandle = CandleResponse.from(summary.latestCandle),
            candleCount = summary.candleCount,
            earliestTimestamp = summary.earliestTimestamp,
            latestTimestamp = summary.latestTimestamp,
        )
    }
}
