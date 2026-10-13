package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.AdxCalculator

/**
 * Entry fires when the compound bullish-trend condition (ADX >= threshold AND +DI > -DI)
 * transitions from false to true - "newly valid," not merely "currently valid," which would
 * re-fire every bar the trend persists. Exit fires on a confirmed -DI/+DI reversal (an actual
 * crossing) or whenever ADX has weakened below the threshold - a level check re-evaluated every
 * bar, which is harmless while already flat since the engine only acts on an exit signal while a
 * position is open.
 */
class AdxTrendStrategy(
    candles: List<Candle>,
    period: Int,
    private val threshold: Double,
) : BacktestStrategy {

    // AdxCalculator's plusDi/minusDi align to original candle index (period + j); adx aligns to
    // (2*period - 1 + m) - see AdxCalculator's own derivation and CLAUDE.md Section 18. A
    // transition check at index i needs both the current and previous ADX value, i.e.
    // i - 1 >= 2*period - 1.
    override val warmupPeriod: Int = 2 * period

    private val diOffset = period
    private val adxOffset = 2 * period - 1

    private val series = AdxCalculator.calculate(
        candles.map { it.high.toDouble() },
        candles.map { it.low.toDouble() },
        candles.map { it.close.toDouble() },
        period,
    )

    private fun plusDiAt(i: Int): Double = series.plusDi[i - diOffset]
    private fun minusDiAt(i: Int): Double = series.minusDi[i - diOffset]
    private fun adxAt(i: Int): Double = series.adx[i - adxOffset]

    override fun signalAt(index: Int, candles: List<Candle>): StrategyAction {
        if (index < warmupPeriod) return StrategyAction.HOLD

        val entryConditionNow = adxAt(index) >= threshold && plusDiAt(index) > minusDiAt(index)
        val entryConditionPrev = adxAt(index - 1) >= threshold && plusDiAt(index - 1) > minusDiAt(index - 1)
        val diReversalNow = minusDiAt(index) > plusDiAt(index)
        val diReversalPrev = minusDiAt(index - 1) > plusDiAt(index - 1)
        val weakNow = adxAt(index) < threshold

        return when {
            !entryConditionPrev && entryConditionNow -> StrategyAction.ENTER_LONG
            (!diReversalPrev && diReversalNow) || weakNow -> StrategyAction.EXIT_LONG
            else -> StrategyAction.HOLD
        }
    }
}
