package com.cephCoding.orynix.technicalanalysis.service

import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.service.MarketDataService
import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import com.cephCoding.orynix.technicalanalysis.domain.Macd
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisHistory
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import com.cephCoding.orynix.technicalanalysis.indicator.Atr
import com.cephCoding.orynix.technicalanalysis.indicator.BollingerBandsCalculator
import com.cephCoding.orynix.technicalanalysis.indicator.BollingerSeries
import com.cephCoding.orynix.technicalanalysis.indicator.Ema
import com.cephCoding.orynix.technicalanalysis.indicator.MacdCalculator
import com.cephCoding.orynix.technicalanalysis.indicator.MacdSeries
import com.cephCoding.orynix.technicalanalysis.indicator.Rsi
import com.cephCoding.orynix.technicalanalysis.indicator.Sma
import com.cephCoding.orynix.technicalanalysis.indicator.VolumeIndicators
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class TechnicalAnalysisService(private val marketDataService: MarketDataService) {

    fun getAnalysis(symbolRaw: String, timeframeCode: String): TechnicalAnalysisResult {
        val candles = marketDataService.getDailyCandles(symbolRaw, timeframeCode)
        if (candles.size < MINIMUM_CANDLES) {
            throw InsufficientCandleDataException(MINIMUM_CANDLES, candles.size)
        }
        return buildResult(candles, computeSeries(candles), offsetFromEnd = 0)
    }

    // Exposes the previous period's indicators alongside the latest, for callers (the signal
    // layer) that need to detect a transition (e.g. a moving-average crossover) rather than just
    // read a snapshot. Kept as a separate method with its own floor rather than folded into
    // getAnalysis so that method's existing behavior/contract never changes.
    fun getAnalysisHistory(symbolRaw: String, timeframeCode: String): TechnicalAnalysisHistory {
        val candles = marketDataService.getDailyCandles(symbolRaw, timeframeCode)
        if (candles.size < MINIMUM_CANDLES_WITH_HISTORY) {
            throw InsufficientCandleDataException(MINIMUM_CANDLES_WITH_HISTORY, candles.size)
        }
        val series = computeSeries(candles)
        return TechnicalAnalysisHistory(
            candles = candles,
            current = buildResult(candles, series, offsetFromEnd = 0),
            previous = buildResult(candles, series, offsetFromEnd = 1),
        )
    }

    private data class ComputedSeries(
        val sma20: List<Double>,
        val sma50: List<Double>,
        val ema20: List<Double>,
        val rsi14: List<Double>,
        val macd: MacdSeries,
        val bollinger: BollingerSeries,
        val atr14: List<Double>,
        val volumeSma20: List<Double>,
        val volumeChangePercent: List<Double?>,
    )

    private fun computeSeries(candles: List<Candle>): ComputedSeries {
        val closes = candles.map { it.close.toDouble() }
        val highs = candles.map { it.high.toDouble() }
        val lows = candles.map { it.low.toDouble() }
        val volumes = candles.map { it.volume.toDouble() }
        return ComputedSeries(
            sma20 = Sma.calculate(closes, SMA_SHORT),
            sma50 = Sma.calculate(closes, SMA_LONG),
            ema20 = Ema.calculate(closes, EMA_SHORT),
            rsi14 = Rsi.calculate(closes, RSI_PERIOD),
            macd = MacdCalculator.calculate(closes, MACD_FAST, MACD_SLOW, MACD_SIGNAL),
            bollinger = BollingerBandsCalculator.calculate(closes, BOLLINGER_PERIOD, BOLLINGER_STD_DEV_MULTIPLIER),
            atr14 = Atr.calculate(highs, lows, closes, ATR_PERIOD),
            volumeSma20 = VolumeIndicators.sma(volumes, VOLUME_SMA_PERIOD),
            volumeChangePercent = VolumeIndicators.changePercent(volumes),
        )
    }

    // Every series here is aligned to end at the most recent candle but starts at a different
    // offset into the candle history (e.g. Sma(50)'s series is shorter than Sma(20)'s) — so "k
    // periods back" is only meaningful measured from each series' own end, never a shared
    // front-aligned index.
    private fun <T> List<T>.atOffsetFromEnd(offset: Int): T = this[size - 1 - offset]

    private fun buildResult(candles: List<Candle>, series: ComputedSeries, offsetFromEnd: Int): TechnicalAnalysisResult {
        val candle = candles.atOffsetFromEnd(offsetFromEnd)
        return TechnicalAnalysisResult(
            symbol = candle.symbol,
            timeframe = candle.timeframe,
            timestamp = candle.timestamp,
            sma20 = toPrice(series.sma20.atOffsetFromEnd(offsetFromEnd)),
            sma50 = toPrice(series.sma50.atOffsetFromEnd(offsetFromEnd)),
            ema20 = toPrice(series.ema20.atOffsetFromEnd(offsetFromEnd)),
            rsi14 = toPrice(series.rsi14.atOffsetFromEnd(offsetFromEnd)),
            macd = Macd(
                macd = toPrice(series.macd.macdLine.atOffsetFromEnd(offsetFromEnd)),
                signal = toPrice(series.macd.signalLine.atOffsetFromEnd(offsetFromEnd)),
                histogram = toPrice(series.macd.histogram.atOffsetFromEnd(offsetFromEnd)),
            ),
            bollingerBands = BollingerBands(
                upper = toPrice(series.bollinger.upper.atOffsetFromEnd(offsetFromEnd)),
                middle = toPrice(series.bollinger.middle.atOffsetFromEnd(offsetFromEnd)),
                lower = toPrice(series.bollinger.lower.atOffsetFromEnd(offsetFromEnd)),
            ),
            atr14 = toPrice(series.atr14.atOffsetFromEnd(offsetFromEnd)),
            volumeSma20 = toVolume(series.volumeSma20.atOffsetFromEnd(offsetFromEnd)),
            volumeChangePercent = series.volumeChangePercent.atOffsetFromEnd(offsetFromEnd)?.let(::toVolume),
        )
    }

    // BigDecimal.valueOf (not the BigDecimal(Double) constructor) goes through Double.toString()
    // first, avoiding the binary floating-point expansion the constructor would otherwise capture.
    private fun toPrice(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(PRICE_SCALE, RoundingMode.HALF_UP)

    private fun toVolume(value: Double): BigDecimal =
        BigDecimal.valueOf(value).setScale(VOLUME_SCALE, RoundingMode.HALF_UP)

    companion object {
        private const val SMA_SHORT = 20
        private const val SMA_LONG = 50
        private const val EMA_SHORT = 20
        private const val RSI_PERIOD = 14
        private const val ATR_PERIOD = 14
        private const val BOLLINGER_PERIOD = 20
        private const val BOLLINGER_STD_DEV_MULTIPLIER = 2.0
        private const val VOLUME_SMA_PERIOD = 20
        private const val MACD_FAST = 12
        private const val MACD_SLOW = 26
        private const val MACD_SIGNAL = 9
        private const val PRICE_SCALE = 4
        private const val VOLUME_SCALE = 2

        // The largest minimum among all Phase-1 indicators wins. MACD's signal line needs the
        // slow EMA seeded (MACD_SLOW candles) plus enough MACD-line points to seed its own
        // signal EMA (MACD_SIGNAL candles): MACD_SLOW + MACD_SIGNAL - 1 = 34. SMA_LONG (50) is
        // larger still, so it is the effective floor today — kept derived rather than a bare
        // literal so it stays correct if any period constant above changes.
        private val MINIMUM_CANDLES = maxOf(
            SMA_LONG,
            MACD_SLOW + MACD_SIGNAL - 1,
            RSI_PERIOD + 1,
            ATR_PERIOD + 1,
            BOLLINGER_PERIOD,
            VOLUME_SMA_PERIOD,
            2,
        )

        // Every series here gains exactly one element per extra candle, so a "previous" value for
        // every indicator (not just the one driving MINIMUM_CANDLES today) exists once there's one
        // more candle than the single-value floor requires.
        private val MINIMUM_CANDLES_WITH_HISTORY = MINIMUM_CANDLES + 1
    }
}
