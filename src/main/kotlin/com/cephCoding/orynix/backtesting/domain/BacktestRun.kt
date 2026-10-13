package com.cephCoding.orynix.backtesting.domain

import com.cephCoding.orynix.instrument.domain.AssetType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Provider/JPA-agnostic computed result: a config echo plus the metrics BacktestEngine computed.
 * No separate BacktestMetrics type — this feature has exactly one caller for either half, so
 * splitting them would be an abstraction with no second use (CLAUDE.md Section 4).
 *
 * winRate/profitFactor are nullable for a domain reason only (undefined with zero closed trades,
 * or zero gross loss respectively) — never because of a lifecycle/status state. There is no status
 * field on this type: a BacktestRun only ever exists once its simulation has fully succeeded
 * (CLAUDE.md Section 18), mirroring news_sentiments' "computed once, never updated" precedent.
 */
data class BacktestRun(
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
)
