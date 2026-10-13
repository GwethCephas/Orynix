package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle

/**
 * Implementations precompute their full indicator series once from the complete candle list
 * (warm-up + requested range) and do O(1) index-aligned lookups per bar — see
 * MovingAverageCrossoverStrategy/RsiThresholdStrategy for the exact alignment math, derived from
 * Sma/Rsi's actual output shape and documented in CLAUDE.md Section 18.
 */
interface BacktestStrategy {
    val warmupPeriod: Int
    fun signalAt(index: Int, candles: List<Candle>): StrategyAction
}
