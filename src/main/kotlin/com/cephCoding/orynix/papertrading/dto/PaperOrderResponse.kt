package com.cephCoding.orynix.papertrading.dto

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.domain.OrderStatus
import com.cephCoding.orynix.papertrading.domain.PaperOrder
import com.cephCoding.orynix.papertrading.domain.PositionEffect
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaperOrderResponse(
    val id: UUID,
    val positionId: UUID?,
    val symbol: String,
    val assetType: AssetType,
    val side: OrderSide,
    val positionEffect: PositionEffect,
    val requestedQuantity: BigDecimal,
    val filledQuantity: BigDecimal,
    val fillPrice: BigDecimal,
    val commission: BigDecimal,
    val status: OrderStatus,
    val sourceCandleTimestamp: Instant,
    val executionTimestamp: Instant,
    val executionAssumptions: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(order: PaperOrder) = PaperOrderResponse(
            id = order.id,
            positionId = order.positionId,
            symbol = order.symbol,
            assetType = order.assetType,
            side = order.side,
            positionEffect = order.positionEffect,
            requestedQuantity = order.requestedQuantity,
            filledQuantity = order.filledQuantity,
            fillPrice = order.fillPrice,
            commission = order.commission,
            status = order.status,
            sourceCandleTimestamp = order.sourceCandleTimestamp,
            executionTimestamp = order.executionTimestamp,
            executionAssumptions = order.executionAssumptions,
            createdAt = order.createdAt,
        )
    }
}
