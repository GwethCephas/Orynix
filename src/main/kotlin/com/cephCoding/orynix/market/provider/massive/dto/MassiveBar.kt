package com.cephCoding.orynix.market.provider.massive.dto

import java.math.BigDecimal

data class MassiveBar(
    val t: Long,
    val o: BigDecimal,
    val h: BigDecimal,
    val l: BigDecimal,
    val c: BigDecimal,
    val v: BigDecimal,
)
