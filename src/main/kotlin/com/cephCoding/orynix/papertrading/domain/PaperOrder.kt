package com.cephCoding.orynix.papertrading.domain

import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A completed simulated fill. `sourceCandleTimestamp` is the trading day the execution price's
 * candle actually belongs to; `executionTimestamp` is when this fill was recorded - the gap
 * between them is exactly what `executionAssumptions` discloses in plain text (see
 * PaperTradingPriceResolver / CLAUDE.md Section 22). `idempotencyKey` is caller-supplied and
 * unique per account - replaying the same key returns this exact row, never a second fill.
 */
data class PaperOrder(
    val id: UUID,
    val positionId: UUID?,
    val symbol: String,
    val assetType: AssetType,
    val side: OrderSide,
    val positionEffect: PositionEffect,
    val requestedQuantity: BigDecimal,
    val filledQuantity: BigDecimal,
    val fillPrice: BigDecimal,
    val commission: BigDecimal,
    val status: OrderStatus,
    val sourceCandleTimestamp: Instant,
    val executionTimestamp: Instant,
    val executionAssumptions: String,
    val idempotencyKey: String,
    val createdAt: Instant,
)
