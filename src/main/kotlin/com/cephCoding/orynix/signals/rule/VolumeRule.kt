package com.cephCoding.orynix.signals.rule

import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.math.BigDecimal

enum class VolumeState { HIGH, NORMAL, LOW }

data class VolumeAssessment(val state: VolumeState, val ratio: Double?)

object VolumeRule {
    // A zero Volume SMA20 (degenerate/all-zero-volume data) makes the ratio undefined — report
    // NORMAL with no value rather than dividing by zero or fabricating a ratio.
    fun evaluate(current: TechnicalAnalysisResult, latestVolume: BigDecimal, highRatio: Double, lowRatio: Double): VolumeAssessment {
        val volumeSma20 = current.volumeSma20.toDouble()
        if (volumeSma20 == 0.0) {
            return VolumeAssessment(VolumeState.NORMAL, null)
        }
        val ratio = latestVolume.toDouble() / volumeSma20
        val state = when {
            ratio >= highRatio -> VolumeState.HIGH
            ratio <= lowRatio -> VolumeState.LOW
            else -> VolumeState.NORMAL
        }
        return VolumeAssessment(state, ratio)
    }
}
