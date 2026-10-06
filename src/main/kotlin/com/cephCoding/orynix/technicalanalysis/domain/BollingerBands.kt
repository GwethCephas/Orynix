package com.cephCoding.orynix.technicalanalysis.domain

import java.math.BigDecimal

data class BollingerBands(
    val upper: BigDecimal,
    val middle: BigDecimal,
    val lower: BigDecimal,
)
