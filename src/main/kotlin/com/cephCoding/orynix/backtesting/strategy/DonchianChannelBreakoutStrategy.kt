package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.DonchianCalculator

/**
 * Entry fires when the completed candle's close crosses from at-or-below the preceding entry
 * channel's upper boundary to above it (an actual breakout, not merely "already above," which
 * would re-fire every bar price stays elevated). Exit fires on the symmetric breakdown through the
 * (shorter) exit channel's lower boundary. The lower entry-channel value is intentionally never
 * used here - it only matters for a short entry, and this strategy is long-only.
 */
class DonchianChannelBreakoutStrategy(
    candles: List<Candle>,
    private val entryLookback: Int,
    private val exitLookback: Int,
) : BacktestStrategy {

    // DonchianCalculator.calculate(highs, lows, period).upper[k]/.lower[k] correspond to original
    // candle index (k + period) - see DonchianCalculator's own doc comment and CLAUDE.md Section
    // 18. A transition check at index i needs the channel valid at (i - 1) too.
    override val warmupPeriod: Int = maxOf(entryLookback, exitLookback) + 1

    private val closes: List<Double> = candles.map { it.close.toDouble() }
    private val entrySeries = DonchianCalculator.calculate(
        candles.map { it.high.toDouble() },
        candles.map { it.low.toDouble() },
        entryLookback,
    )
    private val exitSeries = DonchianCalculator.calculate(
        candles.map { it.high.toDouble() },
        candles.map { it.low.toDouble() },
        exitLookback,
    )

    private fun upperEntryAt(i: Int): Double = entrySeries.upper[i - entryLookback]
    private fun lowerExitAt(i: Int): Double = exitSeries.lower[i - exitLookback]

    override fun signalAt(index: Int, candles: List<Candle>): StrategyAction {
        if (index < warmupPeriod) return StrategyAction.HOLD

        val wasAboveUpper = closes[index - 1] > upperEntryAt(index - 1)
        val isAboveUpper = closes[index] > upperEntryAt(index)
        val wasBelowLowerExit = closes[index - 1] < lowerExitAt(index - 1)
        val isBelowLowerExit = closes[index] < lowerExitAt(index)

        return when {
            !wasAboveUpper && isAboveUpper -> StrategyAction.ENTER_LONG
            !wasBelowLowerExit && isBelowLowerExit -> StrategyAction.EXIT_LONG
            else -> StrategyAction.HOLD
        }
    }
}
