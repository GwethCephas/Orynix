package com.cephCoding.orynix.backtesting.dto

import com.cephCoding.orynix.backtesting.domain.BacktestRun
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class BacktestSummaryResponse(
    val id: UUID,
    val symbol: String,
    val assetType: AssetType,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val strategyType: StrategyType,
    val completedAt: Instant,
    val netPnl: BigDecimal,
    val netReturnPercent: BigDecimal,
    val totalTrades: Int,
) {
    companion object {
        fun from(run: BacktestRun) = BacktestSummaryResponse(
            id = run.id,
            symbol = run.symbol,
            assetType = run.assetType,
            fromDate = run.fromDate,
            toDate = run.toDate,
            strategyType = run.strategyType,
            completedAt = run.completedAt,
            netPnl = run.netPnl,
            netReturnPercent = run.netReturnPercent,
            totalTrades = run.totalTrades,
        )
    }
}
