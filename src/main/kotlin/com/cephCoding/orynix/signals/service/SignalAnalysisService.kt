package com.cephCoding.orynix.signals.service

import com.cephCoding.orynix.signals.domain.OverallBias
import com.cephCoding.orynix.signals.domain.Signal
import com.cephCoding.orynix.signals.domain.SignalType
import com.cephCoding.orynix.signals.domain.TechnicalSignalSummary
import com.cephCoding.orynix.signals.rule.BollingerBandsRule
import com.cephCoding.orynix.signals.rule.CrossoverRule
import com.cephCoding.orynix.signals.rule.MacdRule
import com.cephCoding.orynix.signals.rule.MacdState
import com.cephCoding.orynix.signals.rule.PriceMomentumRule
import com.cephCoding.orynix.signals.rule.PriceMomentumState
import com.cephCoding.orynix.signals.rule.RsiRule
import com.cephCoding.orynix.signals.rule.TrendRule
import com.cephCoding.orynix.signals.rule.TrendState
import com.cephCoding.orynix.signals.rule.VolatilityRule
import com.cephCoding.orynix.signals.rule.VolumeRule
import com.cephCoding.orynix.technicalanalysis.service.TechnicalAnalysisService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class SignalAnalysisService(private val technicalAnalysisService: TechnicalAnalysisService) {

    fun getSignals(symbolRaw: String, timeframeCode: String): TechnicalSignalSummary {
        val history = technicalAnalysisService.getAnalysisHistory(symbolRaw, timeframeCode)
        val current = history.current
        val previous = history.previous
        val latestCandle = history.candles.last()

        val trend = TrendRule.evaluate(current)
        val crossover = CrossoverRule.evaluate(current, previous)
        val rsi = RsiRule.evaluate(current, RSI_OVERBOUGHT, RSI_OVERSOLD)
        val macd = MacdRule.evaluate(current, previous)
        val bollinger = BollingerBandsRule.evaluate(current, latestCandle.close, BOLLINGER_NEAR_PERCENT)
        val volatility = VolatilityRule.evaluate(current, latestCandle.close, ATR_LOW_PERCENT, ATR_HIGH_PERCENT)
        val volume = VolumeRule.evaluate(current, latestCandle.volume, VOLUME_HIGH_RATIO, VOLUME_LOW_RATIO)
        val priceMomentum = PriceMomentumRule.evaluate(history.candles, PRICE_MOMENTUM_LOOKBACK, PRICE_MOMENTUM_FLAT_PERCENT)

        // Fixed order — "deterministic" applies to the shape of the response, not just its values.
        val signals = listOf(
            Signal(SignalType.TREND, trend.name, null),
            Signal(SignalType.CROSSOVER, crossover.name, null),
            Signal(SignalType.RSI, rsi.name, current.rsi14),
            Signal(SignalType.MACD, macd.name, current.macd.histogram),
            Signal(SignalType.BOLLINGER_BANDS, bollinger.name, latestCandle.close),
            Signal(SignalType.VOLATILITY, volatility.state.name, toScaledDecimal(volatility.atrPercent)),
            Signal(SignalType.VOLUME, volume.state.name, volume.ratio?.let(::toScaledDecimal)),
            Signal(SignalType.PRICE_MOMENTUM, priceMomentum.state.name, toScaledDecimal(priceMomentum.changePercent)),
        )

        val bullishCount = signals.count(::isBullish)
        val bearishCount = signals.count(::isBearish)
        val neutralCount = signals.size - bullishCount - bearishCount

        val overallBias = when {
            bullishCount > bearishCount -> OverallBias.BULLISH
            bearishCount > bullishCount -> OverallBias.BEARISH
            else -> OverallBias.NEUTRAL
        }

        return TechnicalSignalSummary(
            symbol = current.symbol,
            timeframe = current.timeframe,
            timestamp = current.timestamp,
            signals = signals,
            bullishSignalCount = bullishCount,
            bearishSignalCount = bearishCount,
            neutralSignalCount = neutralCount,
            overallBias = overallBias,
        )
    }

    // CROSSOVER is deliberately absent from both isBullish/isBearish: it and TREND derive from the
    // identical EMA20/SMA20/SMA50 relationship (TREND is the steady-state 3-way view, CROSSOVER the
    // transition-event 2-way view of that same relationship) — counting both would double-count one
    // underlying fact. RSI/BOLLINGER_BANDS/VOLATILITY/VOLUME are conditions, not directional bias
    // (overbought isn't bearish, a band touch isn't a reversal call), so they never appear here
    // either and always fall through to neutral.
    private fun isBullish(signal: Signal) = when (signal.type) {
        SignalType.TREND -> signal.state == TrendState.BULLISH.name
        SignalType.MACD -> signal.state == MacdState.BULLISH_CROSSOVER.name || signal.state == MacdState.BULLISH_MOMENTUM.name
        SignalType.PRICE_MOMENTUM -> signal.state == PriceMomentumState.POSITIVE.name
        else -> false
    }

    private fun isBearish(signal: Signal) = when (signal.type) {
        SignalType.TREND -> signal.state == TrendState.BEARISH.name
        SignalType.MACD -> signal.state == MacdState.BEARISH_CROSSOVER.name || signal.state == MacdState.BEARISH_MOMENTUM.name
        SignalType.PRICE_MOMENTUM -> signal.state == PriceMomentumState.NEGATIVE.name
        else -> false
    }

    private fun toScaledDecimal(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(VALUE_SCALE, RoundingMode.HALF_UP)

    companion object {
        private const val RSI_OVERBOUGHT = 70.0
        private const val RSI_OVERSOLD = 30.0
        private const val BOLLINGER_NEAR_PERCENT = 1.0
        private const val ATR_LOW_PERCENT = 1.0
        private const val ATR_HIGH_PERCENT = 3.0
        private const val VOLUME_HIGH_RATIO = 1.5
        private const val VOLUME_LOW_RATIO = 0.5
        private const val PRICE_MOMENTUM_LOOKBACK = 10
        private const val PRICE_MOMENTUM_FLAT_PERCENT = 1.0
        private const val VALUE_SCALE = 2
    }
}
