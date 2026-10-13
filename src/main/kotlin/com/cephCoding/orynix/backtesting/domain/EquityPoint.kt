package com.cephCoding.orynix.backtesting.domain

import java.math.BigDecimal
import java.time.Instant

data class EquityPoint(
    val sequenceNumber: Int,
    val timestamp: Instant,
    val cash: BigDecimal,
    val equity: BigDecimal,
)
