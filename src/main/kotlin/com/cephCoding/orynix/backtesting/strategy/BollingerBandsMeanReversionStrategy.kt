package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.BollingerBandsCalculator

/**
 * A mean-reversion entry: close crosses from at-or-above the lower band to below it (a confirmed
 * breakdown through the band, not merely "currently below," which would re-fire every bar). Exit
 * fires on close crossing from at-or-below the middle band to above it - the mean-reversion
 * target, exactly as specified, not a bounce back above the lower band.
 */
class BollingerBandsMeanReversionStrategy(
    candles: List<Candle>,
    private val period: Int,
    stdDevMultiplier: Double,
) : BacktestStrategy {

    override val warmupPeriod: Int = period

    private val closes: List<Double> = candles.map { it.close.toDouble() }
    private val series = BollingerBandsCalculator.calculate(closes, period, stdDevMultiplier)

    // BollingerBandsCalculator.calculate(closes, period)[k] corresponds to original candle index
    // (period - 1 + k) for upper/middle/lower alike - documented in CLAUDE.md Section 18.
    private fun lowerAt(i: Int): Double = series.lower[i - (period - 1)]
    private fun middleAt(i: Int): Double = series.middle[i - (period - 1)]

    override fun signalAt(index: Int, candles: List<Candle>): StrategyAction {
        if (index < warmupPeriod) return StrategyAction.HOLD

        val wasBelowLower = closes[index - 1] < lowerAt(index - 1)
        val isBelowLower = closes[index] < lowerAt(index)
        val wasAboveMiddle = closes[index - 1] > middleAt(index - 1)
        val isAboveMiddle = closes[index] > middleAt(index)

        return when {
            !wasBelowLower && isBelowLower -> StrategyAction.ENTER_LONG
            !wasAboveMiddle && isAboveMiddle -> StrategyAction.EXIT_LONG
            else -> StrategyAction.HOLD
        }
    }
}
