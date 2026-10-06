package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.math.BigDecimal

enum class BollingerBandsState {
    PRICE_ABOVE_UPPER_BAND,
    PRICE_BELOW_LOWER_BAND,
    PRICE_NEAR_UPPER_BAND,
    PRICE_NEAR_LOWER_BAND,
    PRICE_WITHIN_BANDS,
}

object BollingerBandsRule {
    // Above/below checked before near — a genuine breach of a band is never merely "near" it.
    fun evaluate(current: TechnicalAnalysisResult, closePrice: BigDecimal, nearPercent: Double): BollingerBandsState {
        val upper = current.bollingerBands.upper
        val lower = current.bollingerBands.lower
        return when {
            closePrice > upper -> BollingerBandsState.PRICE_ABOVE_UPPER_BAND
            closePrice < lower -> BollingerBandsState.PRICE_BELOW_LOWER_BAND
            isNear(closePrice, upper, nearPercent) -> BollingerBandsState.PRICE_NEAR_UPPER_BAND
            isNear(closePrice, lower, nearPercent) -> BollingerBandsState.PRICE_NEAR_LOWER_BAND
            else -> BollingerBandsState.PRICE_WITHIN_BANDS
        }
    }

    private fun isNear(close: BigDecimal, band: BigDecimal, nearPercent: Double): Boolean {
        val distance = (close - band).abs().toDouble()
        return distance <= band.toDouble() * nearPercent / 100.0
    }
}
