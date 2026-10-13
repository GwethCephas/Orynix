package com.cephCoding.orynix.backtesting.model

import com.cephCoding.orynix.backtesting.domain.ExitReason
import com.cephCoding.orynix.backtesting.domain.Trade
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Every trade is always fully closed (entry and exit both populated) - an open position at the
 * final candle is force-closed before the run completes (see BacktestEngine), so there is no
 * dangling open-position row to model.
 */
@Entity
@Table(name = "backtest_trades")
class BacktestTradeEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "backtest_run_id", nullable = false)
    val backtestRunId: UUID,

    @Column(name = "sequence_number", nullable = false)
    val sequenceNumber: Int,

    @Column(nullable = false)
    val direction: String,

    @Column(name = "entry_timestamp", nullable = false)
    val entryTimestamp: Instant,

    @Column(name = "entry_price", nullable = false)
    val entryPrice: BigDecimal,

    @Column(name = "exit_timestamp", nullable = false)
    val exitTimestamp: Instant,

    @Column(name = "exit_price", nullable = false)
    val exitPrice: BigDecimal,

    @Column(nullable = false)
    val quantity: BigDecimal,

    @Column(nullable = false)
    val commission: BigDecimal,

    @Column(name = "gross_pnl", nullable = false)
    val grossPnl: BigDecimal,

    @Column(name = "net_pnl", nullable = false)
    val netPnl: BigDecimal,

    @Column(name = "return_percent", nullable = false)
    val returnPercent: BigDecimal,

    @Column(name = "exit_reason", nullable = false)
    val exitReason: String,
) {
    fun toDomain() = Trade(
        sequenceNumber = sequenceNumber,
        direction = TradeDirection.valueOf(direction),
        entryTimestamp = entryTimestamp,
        entryPrice = entryPrice,
        exitTimestamp = exitTimestamp,
        exitPrice = exitPrice,
        quantity = quantity,
        commission = commission,
        grossPnl = grossPnl,
        netPnl = netPnl,
        returnPercent = returnPercent,
        exitReason = ExitReason.valueOf(exitReason),
    )

    companion object {
        fun from(backtestRunId: UUID, trade: Trade) = BacktestTradeEntity(
            backtestRunId = backtestRunId,
            sequenceNumber = trade.sequenceNumber,
            direction = trade.direction.name,
            entryTimestamp = trade.entryTimestamp,
            entryPrice = trade.entryPrice,
            exitTimestamp = trade.exitTimestamp,
            exitPrice = trade.exitPrice,
            quantity = trade.quantity,
            commission = trade.commission,
            grossPnl = trade.grossPnl,
            netPnl = trade.netPnl,
            returnPercent = trade.returnPercent,
            exitReason = trade.exitReason.name,
        )
    }
}
