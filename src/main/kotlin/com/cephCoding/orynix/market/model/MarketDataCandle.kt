package com.cephCoding.orynix.market.model

import com.cephCoding.orynix.market.domain.Candle
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "market_data_candles")
@EntityListeners(AuditingEntityListener::class)
class MarketDataCandle(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val timeframe: String,

    @Column(nullable = false)
    val timestamp: Instant,

    @Column(nullable = false)
    val open: BigDecimal,

    @Column(nullable = false)
    val high: BigDecimal,

    @Column(nullable = false)
    val low: BigDecimal,

    @Column(nullable = false)
    val close: BigDecimal,

    @Column(nullable = false)
    val volume: BigDecimal,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,
) {
    companion object {
        fun from(candle: Candle) = MarketDataCandle(
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
