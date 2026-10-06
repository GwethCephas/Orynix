package com.cephCoding.orynix.technicalanalysis.domain

import java.math.BigDecimal

data class Macd(
    val macd: BigDecimal,
    val signal: BigDecimal,
    val histogram: BigDecimal,
)
