package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.MacdCalculator

/**
 * Entry fires on a confirmed bullish crossover (MACD crosses from at-or-below the signal line to
 * above it), not merely "MACD is currently above signal" - mirroring every other strategy's
 * "already above is not a crossover" philosophy. Exit fires on the symmetric bearish crossover.
 */
class MacdCrossoverStrategy(
    candles: List<Candle>,
    fastPeriod: Int,
    private val slowPeriod: Int,
    private val signalPeriod: Int,
) : BacktestStrategy {

    // MacdCalculator.calculate(closes, fastPeriod, slowPeriod, signalPeriod).macdLine[i] aligns to
    // original candle index (slowPeriod - 1 + i), and .signalLine[j] aligns to original candle
    // index (slowPeriod + signalPeriod - 2 + j) - derived from MacdCalculator's own startOffset/
    // signalOffset alignment math, documented in CLAUDE.md Section 18. A transition check at index
    // i needs signalLineAt(i - 1) valid, i.e. i - 1 >= slowPeriod + signalPeriod - 2.
    override val warmupPeriod: Int = slowPeriod + signalPeriod - 1

    private val macdLineOffset = slowPeriod - 1
    private val signalLineOffset = slowPeriod + signalPeriod - 2

    private val series = MacdCalculator.calculate(candles.map { it.close.toDouble() }, fastPeriod, slowPeriod, signalPeriod)

    private fun macdAt(i: Int): Double = series.macdLine[i - macdLineOffset]
    private fun signalLineAt(i: Int): Double = series.signalLine[i - signalLineOffset]

    override fun signalAt(index: Int, candles: List<Candle>): StrategyAction {
        if (index < warmupPeriod) return StrategyAction.HOLD

        val wasAbove = macdAt(index - 1) > signalLineAt(index - 1)
        val isAbove = macdAt(index) > signalLineAt(index)

        return when {
            !wasAbove && isAbove -> StrategyAction.ENTER_LONG
            wasAbove && !isAbove -> StrategyAction.EXIT_LONG
            else -> StrategyAction.HOLD
        }
    }
}
