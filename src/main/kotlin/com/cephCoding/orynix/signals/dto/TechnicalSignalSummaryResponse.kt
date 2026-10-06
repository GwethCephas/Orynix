package com.cephCoding.orynix.signals.dto

import com.cephCoding.orynix.signals.domain.TechnicalSignalSummary
import java.time.Instant

data class TechnicalSignalSummaryResponse(
    val symbol: String,
    val timeframe: String,
    val timestamp: Instant,
    val signals: List<SignalResponse>,
    val bullishSignalCount: Int,
    val bearishSignalCount: Int,
    val neutralSignalCount: Int,
    val overallBias: String,
) {
    companion object {
        fun from(summary: TechnicalSignalSummary) = TechnicalSignalSummaryResponse(
            symbol = summary.symbol,
            timeframe = summary.timeframe.code,
            timestamp = summary.timestamp,
            signals = summary.signals.map(SignalResponse::from),
            bullishSignalCount = summary.bullishSignalCount,
            bearishSignalCount = summary.bearishSignalCount,
            neutralSignalCount = summary.neutralSignalCount,
            overallBias = summary.overallBias.name,
        )
    }
}
