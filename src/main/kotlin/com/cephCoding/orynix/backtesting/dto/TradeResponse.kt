package com.cephCoding.orynix.backtesting.dto

import com.cephCoding.orynix.backtesting.domain.ExitReason
import com.cephCoding.orynix.backtesting.domain.Trade
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import java.math.BigDecimal
import java.time.Instant

data class TradeResponse(
    val sequenceNumber: Int,
    val direction: TradeDirection,
    val entryTimestamp: Instant,
    val entryPrice: BigDecimal,
    val exitTimestamp: Instant,
    val exitPrice: BigDecimal,
    val quantity: BigDecimal,
    val commission: BigDecimal,
    val grossPnl: BigDecimal,
    val netPnl: BigDecimal,
    val returnPercent: BigDecimal,
    val exitReason: ExitReason,
    val initialStopPrice: BigDecimal?,
    val finalStopPrice: BigDecimal?,
) {
    companion object {
        fun from(trade: Trade) = TradeResponse(
            sequenceNumber = trade.sequenceNumber,
            direction = trade.direction,
            entryTimestamp = trade.entryTimestamp,
            entryPrice = trade.entryPrice,
            exitTimestamp = trade.exitTimestamp,
            exitPrice = trade.exitPrice,
            quantity = trade.quantity,
            commission = trade.commission,
            grossPnl = trade.grossPnl,
            netPnl = trade.netPnl,
            returnPercent = trade.returnPercent,
            exitReason = trade.exitReason,
            initialStopPrice = trade.initialStopPrice,
            finalStopPrice = trade.finalStopPrice,
        )
    }
}
