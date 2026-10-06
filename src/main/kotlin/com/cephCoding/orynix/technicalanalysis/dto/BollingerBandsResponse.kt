package com.cephCoding.orynix.technicalanalysis.dto

import com.cephCoding.orynix.technicalanalysis.domain.BollingerBands
import java.math.BigDecimal

data class BollingerBandsResponse(
    val upper: BigDecimal,
    val middle: BigDecimal,
    val lower: BigDecimal,
) {
    companion object {
        fun from(bollingerBands: BollingerBands) = BollingerBandsResponse(
            upper = bollingerBands.upper,
            middle = bollingerBands.middle,
            lower = bollingerBands.lower,
        )
    }
}
