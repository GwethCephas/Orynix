package com.cephCoding.orynix.papertrading.domain

import java.math.BigDecimal
import java.time.Instant

/**
 * The output of resolving "the best acceptable price available right now" for an instrument -
 * deliberately distinguishes the candle's own trading-day timestamp from the moment this
 * resolution happened, since Orynix only ever has end-of-day daily bars (see
 * PaperTradingPriceResolver / CLAUDE.md Section 22). Never claims to be a real-time quote.
 */
data class ResolvedPrice(
    val price: BigDecimal,
    val candleTimestamp: Instant,
    val resolvedAt: Instant,
    val ageDays: Long,
)
