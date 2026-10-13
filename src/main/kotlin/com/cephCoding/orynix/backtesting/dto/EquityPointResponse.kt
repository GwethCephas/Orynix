package com.cephCoding.orynix.backtesting.dto

import com.cephCoding.orynix.backtesting.domain.EquityPoint
import java.math.BigDecimal
import java.time.Instant

data class EquityPointResponse(
    val sequenceNumber: Int,
    val timestamp: Instant,
    val cash: BigDecimal,
    val equity: BigDecimal,
) {
    companion object {
        fun from(point: EquityPoint) = EquityPointResponse(
            sequenceNumber = point.sequenceNumber,
            timestamp = point.timestamp,
            cash = point.cash,
            equity = point.equity,
        )
    }
}
