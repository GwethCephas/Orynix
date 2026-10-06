package com.cephCoding.orynix.technicalanalysis.dto

import com.cephCoding.orynix.technicalanalysis.domain.Macd
import java.math.BigDecimal

data class MacdResponse(
    val macd: BigDecimal,
    val signal: BigDecimal,
    val histogram: BigDecimal,
) {
    companion object {
        fun from(macd: Macd) = MacdResponse(
            macd = macd.macd,
            signal = macd.signal,
            histogram = macd.histogram,
        )
    }
}
