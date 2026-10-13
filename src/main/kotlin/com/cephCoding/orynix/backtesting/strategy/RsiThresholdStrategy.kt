package com.cephCoding.orynix.backtesting.strategy

import com.cephCoding.orynix.backtesting.engine.StrategyAction
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.Rsi
import java.math.BigDecimal

/**
 * Entry fires on a confirmed bounce (RSI crosses up through oversoldThreshold), not merely "RSI is
 * currently below oversoldThreshold" — the latter would re-fire on every bar while oversold. Exit
 * fires on a confirmed rollover (RSI crosses down through overboughtThreshold) by the same logic.
 */
class RsiThresholdStrategy(
    candles: List<Candle>,
    private val rsiPeriod: Int,
    oversoldThreshold: BigDecimal,
    overboughtThreshold: BigDecimal,
) : BacktestStrategy {

    override val warmupPeriod: Int = rsiPeriod + 1

    private val oversold: Double = oversoldThreshold.toDouble()
    private val overbought: Double = overboughtThreshold.toDouble()
    private val rsi: List<Double> = Rsi.calculate(candles.map { it.close.toDouble() }, rsiPeriod)

    // Rsi.calculate(closes, period)[j] corresponds to original candle index (period + j) —
    // i.e. atCandleIndex(i, period) = series[i - period].
    private fun rsiAt(i: Int): Double = rsi[i - rsiPeriod]

    override fun signalAt(index: Int, candles: List<Candle>): StrategyAction {
        if (index < warmupPeriod) return StrategyAction.HOLD

        val current = rsiAt(index)
        val previous = rsiAt(index - 1)

        return when {
            previous <= oversold && current > oversold -> StrategyAction.ENTER_LONG
            previous >= overbought && current < overbought -> StrategyAction.EXIT_LONG
            else -> StrategyAction.HOLD
        }
    }
}
