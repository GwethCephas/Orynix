package com.cephCoding.orynix.signals.domain

import com.cephCoding.orynix.market.domain.CandleTimeframe
import java.time.Instant

data class TechnicalSignalSummary(
    val symbol: String,
    val timeframe: CandleTimeframe,
    val timestamp: Instant,
    val signals: List<Signal>,
    val bullishSignalCount: Int,
    val bearishSignalCount: Int,
    val neutralSignalCount: Int,
    val overallBias: OverallBias,
)
