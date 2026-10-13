package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.Sma

/**
 * Both fast and slow averages are plain SMA (not EMA) — the simplest, fully deterministic choice,
 * reusing Sma.calculate as-is with no seeding nuance to replicate. Entry/exit fire only on the
 * actual crossover transition between consecutive bars, mirroring signals/rule/CrossoverRule's
 * "already above is not a crossover" philosophy, not on the fast average merely being above/below
 * the slow one.
 */
class MovingAverageCrossoverStrategy(
    candles: List<Candle>,
    private val fastPeriod: Int,
    private val slowPeriod: Int,
) : BacktestStrategy {

    override val warmupPeriod: Int = slowPeriod

    private val fastSma: List<Double> = Sma.calculate(candles.map { it.close.toDouble() }, fastPeriod)
    private val slowSma: List<Double> = Sma.calculate(candles.map { it.close.toDouble() }, slowPeriod)

    // Sma.calculate(values, period)[k] corresponds to original candle index (period - 1 + k) —
    // i.e. atCandleIndex(i, period) = series[i - (period - 1)].
    private fun fastAt(i: Int): Double = fastSma[i - (fastPeriod - 1)]
    private fun slowAt(i: Int): Double = slowSma[i - (slowPeriod - 1)]

    override fun signalAt(index: Int, candles: List<Candle>): StrategyAction {
        if (index < warmupPeriod) return StrategyAction.HOLD

        val wasAbove = fastAt(index - 1) > slowAt(index - 1)
        val isAbove = fastAt(index) > slowAt(index)

        return when {
            !wasAbove && isAbove -> StrategyAction.ENTER_LONG
            wasAbove && !isAbove -> StrategyAction.EXIT_LONG
            else -> StrategyAction.HOLD
        }
    }
}
