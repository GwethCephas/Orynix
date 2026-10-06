package com.cephCoding.orynix.technicalanalysis.domain

import com.cephCoding.orynix.market.domain.Candle

data class TechnicalAnalysisHistory(
    val candles: List<Candle>,
    val current: TechnicalAnalysisResult,
    val previous: TechnicalAnalysisResult,
)
