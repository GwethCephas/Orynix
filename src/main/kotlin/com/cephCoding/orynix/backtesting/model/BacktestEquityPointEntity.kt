package com.cephCoding.orynix.backtesting.model

import com.cephCoding.orynix.backtesting.domain.EquityPoint
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "backtest_equity_points")
class BacktestEquityPointEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "backtest_run_id", nullable = false)
    val backtestRunId: UUID,

    @Column(name = "sequence_number", nullable = false)
    val sequenceNumber: Int,

    @Column(nullable = false)
    val timestamp: Instant,

    @Column(nullable = false)
    val cash: BigDecimal,

    @Column(nullable = false)
    val equity: BigDecimal,
) {
    fun toDomain() = EquityPoint(
        sequenceNumber = sequenceNumber,
        timestamp = timestamp,
        cash = cash,
        equity = equity,
    )

    companion object {
        fun from(backtestRunId: UUID, point: EquityPoint) = BacktestEquityPointEntity(
            backtestRunId = backtestRunId,
            sequenceNumber = point.sequenceNumber,
            timestamp = point.timestamp,
            cash = point.cash,
            equity = point.equity,
        )
    }
}
