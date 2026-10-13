package com.cephCoding.orynix.backtesting.dto

import com.cephCoding.orynix.backtesting.domain.BacktestRun
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class BacktestResponse(
    val id: UUID,
    val symbol: String,
    val assetType: AssetType,
    val timeframe: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val strategyType: StrategyType,
    val fastPeriod: Int?,
    val slowPeriod: Int?,
    val rsiPeriod: Int?,
    val oversoldThreshold: BigDecimal?,
    val overboughtThreshold: BigDecimal?,
    val direction: TradeDirection,
    val initialCapital: BigDecimal,
    val accountCurrency: String,
    val positionSizingType: String,
    val quantity: BigDecimal,
    val commissionPerOrder: BigDecimal,
    val slippageBps: Int,
    val completedAt: Instant,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakevenTrades: Int,
    val winRate: BigDecimal?,
    val profitFactor: BigDecimal?,
    val netPnl: BigDecimal,
    val netReturnPercent: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val finalEquity: BigDecimal,
) {
    companion object {
        fun from(run: BacktestRun) = BacktestResponse(
            id = run.id,
            symbol = run.symbol,
            assetType = run.assetType,
            timeframe = run.timeframe,
            fromDate = run.fromDate,
            toDate = run.toDate,
            strategyType = run.strategyType,
            fastPeriod = run.fastPeriod,
            slowPeriod = run.slowPeriod,
            rsiPeriod = run.rsiPeriod,
            oversoldThreshold = run.oversoldThreshold,
            overboughtThreshold = run.overboughtThreshold,
            direction = run.direction,
            initialCapital = run.initialCapital,
            accountCurrency = run.accountCurrency,
            positionSizingType = run.positionSizingType,
            quantity = run.quantity,
            commissionPerOrder = run.commissionPerOrder,
            slippageBps = run.slippageBps,
            completedAt = run.completedAt,
            totalTrades = run.totalTrades,
            winningTrades = run.winningTrades,
            losingTrades = run.losingTrades,
            breakevenTrades = run.breakevenTrades,
            winRate = run.winRate,
            profitFactor = run.profitFactor,
            netPnl = run.netPnl,
            netReturnPercent = run.netReturnPercent,
            maxDrawdownPercent = run.maxDrawdownPercent,
            finalEquity = run.finalEquity,
        )
    }
}
