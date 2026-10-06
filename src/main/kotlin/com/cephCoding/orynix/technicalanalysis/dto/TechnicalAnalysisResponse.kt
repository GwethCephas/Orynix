package com.cephCoding.orynix.technicalanalysis.dto

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.math.BigDecimal
import java.time.Instant

data class TechnicalAnalysisResponse(
    val symbol: String,
    val timeframe: String,
    val timestamp: Instant,
    val sma20: BigDecimal,
    val sma50: BigDecimal,
    val ema20: BigDecimal,
    val rsi14: BigDecimal,
    val macd: MacdResponse,
    val bollingerBands: BollingerBandsResponse,
    val atr14: BigDecimal,
    val volumeSma20: BigDecimal,
    val volumeChangePercent: BigDecimal?,
) {
    companion object {
        fun from(result: TechnicalAnalysisResult) = TechnicalAnalysisResponse(
            symbol = result.symbol,
            timeframe = result.timeframe.code,
            timestamp = result.timestamp,
            sma20 = result.sma20,
            sma50 = result.sma50,
            ema20 = result.ema20,
            rsi14 = result.rsi14,
            macd = MacdResponse.from(result.macd),
            bollingerBands = BollingerBandsResponse.from(result.bollingerBands),
            atr14 = result.atr14,
            volumeSma20 = result.volumeSma20,
            volumeChangePercent = result.volumeChangePercent,
        )
    }
}
