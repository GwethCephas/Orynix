package com.cephCoding.orynix.technicalanalysis.domain

import com.cephCoding.orynix.market.domain.CandleTimeframe
import java.math.BigDecimal
import java.time.Instant

data class TechnicalAnalysisResult(
    val symbol: String,
    val timeframe: CandleTimeframe,
    val timestamp: Instant,
    val sma20: BigDecimal,
    val sma50: BigDecimal,
    val ema20: BigDecimal,
    val rsi14: BigDecimal,
    val macd: Macd,
    val bollingerBands: BollingerBands,
    val atr14: BigDecimal,
    val volumeSma20: BigDecimal,
    val volumeChangePercent: BigDecimal?,
)
