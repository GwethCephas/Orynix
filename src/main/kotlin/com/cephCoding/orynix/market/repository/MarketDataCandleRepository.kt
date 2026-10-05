package com.cephCoding.orynix.market.repository

import com.cephCoding.orynix.market.model.MarketDataCandle
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MarketDataCandleRepository : JpaRepository<MarketDataCandle, UUID> {
    fun findBySymbolAndTimeframeAndTimestampIn(
        symbol: String,
        timeframe: String,
        timestamps: Collection<Instant>,
    ): List<MarketDataCandle>
}
