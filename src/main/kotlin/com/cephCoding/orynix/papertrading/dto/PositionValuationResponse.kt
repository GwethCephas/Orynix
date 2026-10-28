package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.papertrading.domain.PositionValuation
import com.cephCoding.orynix.papertrading.domain.PriceStatus
import java.math.BigDecimal
import java.time.Instant

data class PositionValuationResponse(
    val markPrice: BigDecimal,
    val priceStatus: PriceStatus,
    val priceAsOf: Instant,
    val marketValue: BigDecimal,
    val unrealizedPnl: BigDecimal,
    val reservedCollateral: BigDecimal?,
) {
    companion object {
        fun from(valuation: PositionValuation) = PositionValuationResponse(
            markPrice = valuation.markPrice,
            priceStatus = valuation.priceStatus,
            priceAsOf = valuation.priceAsOf,
            marketValue = valuation.marketValue,
            unrealizedPnl = valuation.unrealizedPnl,
            reservedCollateral = valuation.reservedCollateral,
        )
    }
}
