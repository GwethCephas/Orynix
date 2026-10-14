package com.cephCoding.orynix.backtesting.model

import com.cephCoding.orynix.backtesting.domain.BacktestRun
import com.cephCoding.orynix.backtesting.domain.PositionSizingType
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import com.cephCoding.orynix.instrument.domain.AssetType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * No status/error_message columns and no @CreatedDate/AuditingEntityListener: a row is written
 * only once, after BacktestEngine simulation has fully succeeded (mirrors the NewsSentimentEntity
 * "computed once, never updated" precedent) - completedAt is set explicitly at construction.
 */
@Entity
@Table(name = "backtest_runs")
class BacktestRunEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "asset_type", nullable = false)
    val assetType: String,

    @Column(nullable = false)
    val timeframe: String,

    @Column(name = "from_date", nullable = false)
    val fromDate: LocalDate,

    @Column(name = "to_date", nullable = false)
    val toDate: LocalDate,

    @Column(name = "strategy_type", nullable = false)
    val strategyType: String,

    @Column(name = "fast_period")
    val fastPeriod: Int?,

    @Column(name = "slow_period")
    val slowPeriod: Int?,

    @Column(name = "rsi_period")
    val rsiPeriod: Int?,

    @Column(name = "oversold_threshold")
    val oversoldThreshold: BigDecimal?,

    @Column(name = "overbought_threshold")
    val overboughtThreshold: BigDecimal?,

    @Column(name = "signal_period")
    val signalPeriod: Int?,

    @Column(name = "bollinger_period")
    val bollingerPeriod: Int?,

    @Column(name = "bollinger_std_dev_multiplier")
    val bollingerStdDevMultiplier: BigDecimal?,

    @Column(name = "donchian_entry_period")
    val donchianEntryPeriod: Int?,

    @Column(name = "donchian_exit_period")
    val donchianExitPeriod: Int?,

    @Column(name = "adx_period")
    val adxPeriod: Int?,

    @Column(name = "adx_threshold")
    val adxThreshold: BigDecimal?,

    @Column(nullable = false)
    val direction: String,

    @Column(name = "initial_capital", nullable = false)
    val initialCapital: BigDecimal,

    @Column(name = "account_currency", nullable = false)
    val accountCurrency: String,

    @Column(name = "position_sizing_type", nullable = false)
    val positionSizingType: String,

    // Nullable: under RISK_BASED_ATR sizing there is no run-level fixed quantity (CLAUDE.md
    // Section 18) - per-trade quantities remain on backtest_trades, unaffected.
    @Column
    val quantity: BigDecimal?,

    @Column(name = "commission_per_order", nullable = false)
    val commissionPerOrder: BigDecimal,

    @Column(name = "slippage_bps", nullable = false)
    val slippageBps: Int,

    @Column(name = "completed_at", nullable = false)
    val completedAt: Instant = Instant.now(),

    @Column(name = "total_trades", nullable = false)
    val totalTrades: Int,

    @Column(name = "winning_trades", nullable = false)
    val winningTrades: Int,

    @Column(name = "losing_trades", nullable = false)
    val losingTrades: Int,

    @Column(name = "breakeven_trades", nullable = false)
    val breakevenTrades: Int,

    @Column(name = "win_rate")
    val winRate: BigDecimal?,

    @Column(name = "profit_factor")
    val profitFactor: BigDecimal?,

    @Column(name = "net_pnl", nullable = false)
    val netPnl: BigDecimal,

    @Column(name = "net_return_percent", nullable = false)
    val netReturnPercent: BigDecimal,

    @Column(name = "max_drawdown_percent", nullable = false)
    val maxDrawdownPercent: BigDecimal,

    @Column(name = "final_equity", nullable = false)
    val finalEquity: BigDecimal,

    @Column(name = "candles_evaluated")
    val candlesEvaluated: Int?,

    @Column(name = "entry_signals_generated")
    val entrySignalsGenerated: Int?,

    @Column(name = "exit_signals_generated")
    val exitSignalsGenerated: Int?,

    @Column(name = "entries_skipped_insufficient_cash")
    val entriesSkippedInsufficientCash: Int?,

    @Column(name = "entries_skipped_invalid_quantity")
    val entriesSkippedInvalidQuantity: Int?,

    @Column(name = "risk_percent_per_trade")
    val riskPercentPerTrade: BigDecimal?,

    @Column(name = "atr_risk_management_enabled", nullable = false)
    val atrRiskManagementEnabled: Boolean,

    @Column(name = "atr_period")
    val atrPeriod: Int?,

    @Column(name = "atr_initial_stop_multiplier")
    val atrInitialStopMultiplier: BigDecimal?,

    @Column(name = "atr_trailing_stop_enabled", nullable = false)
    val atrTrailingStopEnabled: Boolean,

    @Column(name = "atr_trailing_stop_multiplier")
    val atrTrailingStopMultiplier: BigDecimal?,
) {
    fun toDomain() = BacktestRun(
        id = id,
        symbol = symbol,
        assetType = AssetType.valueOf(assetType),
        timeframe = timeframe,
        fromDate = fromDate,
        toDate = toDate,
        strategyType = StrategyType.valueOf(strategyType),
        fastPeriod = fastPeriod,
        slowPeriod = slowPeriod,
        rsiPeriod = rsiPeriod,
        oversoldThreshold = oversoldThreshold,
        overboughtThreshold = overboughtThreshold,
        signalPeriod = signalPeriod,
        bollingerPeriod = bollingerPeriod,
        bollingerStdDevMultiplier = bollingerStdDevMultiplier,
        donchianEntryPeriod = donchianEntryPeriod,
        donchianExitPeriod = donchianExitPeriod,
        adxPeriod = adxPeriod,
        adxThreshold = adxThreshold,
        direction = TradeDirection.valueOf(direction),
        initialCapital = initialCapital,
        accountCurrency = accountCurrency,
        positionSizingType = PositionSizingType.valueOf(positionSizingType),
        quantity = quantity,
        commissionPerOrder = commissionPerOrder,
        slippageBps = slippageBps,
        completedAt = completedAt,
        totalTrades = totalTrades,
        winningTrades = winningTrades,
        losingTrades = losingTrades,
        breakevenTrades = breakevenTrades,
        winRate = winRate,
        profitFactor = profitFactor,
        netPnl = netPnl,
        netReturnPercent = netReturnPercent,
        maxDrawdownPercent = maxDrawdownPercent,
        finalEquity = finalEquity,
        candlesEvaluated = candlesEvaluated,
        entrySignalsGenerated = entrySignalsGenerated,
        exitSignalsGenerated = exitSignalsGenerated,
        entriesSkippedInsufficientCash = entriesSkippedInsufficientCash,
        entriesSkippedInvalidQuantity = entriesSkippedInvalidQuantity,
        riskPercentPerTrade = riskPercentPerTrade,
        atrRiskManagementEnabled = atrRiskManagementEnabled,
        atrPeriod = atrPeriod,
        atrInitialStopMultiplier = atrInitialStopMultiplier,
        atrTrailingStopEnabled = atrTrailingStopEnabled,
        atrTrailingStopMultiplier = atrTrailingStopMultiplier,
    )

    companion object {
        fun from(userId: UUID, run: BacktestRun) = BacktestRunEntity(
            id = run.id,
            userId = userId,
            symbol = run.symbol,
            assetType = run.assetType.name,
            timeframe = run.timeframe,
            fromDate = run.fromDate,
            toDate = run.toDate,
            strategyType = run.strategyType.name,
            fastPeriod = run.fastPeriod,
            slowPeriod = run.slowPeriod,
            rsiPeriod = run.rsiPeriod,
            oversoldThreshold = run.oversoldThreshold,
            overboughtThreshold = run.overboughtThreshold,
            signalPeriod = run.signalPeriod,
            bollingerPeriod = run.bollingerPeriod,
            bollingerStdDevMultiplier = run.bollingerStdDevMultiplier,
            donchianEntryPeriod = run.donchianEntryPeriod,
            donchianExitPeriod = run.donchianExitPeriod,
            adxPeriod = run.adxPeriod,
            adxThreshold = run.adxThreshold,
            direction = run.direction.name,
            initialCapital = run.initialCapital,
            accountCurrency = run.accountCurrency,
            positionSizingType = run.positionSizingType.name,
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
            candlesEvaluated = run.candlesEvaluated,
            entrySignalsGenerated = run.entrySignalsGenerated,
            exitSignalsGenerated = run.exitSignalsGenerated,
            entriesSkippedInsufficientCash = run.entriesSkippedInsufficientCash,
            entriesSkippedInvalidQuantity = run.entriesSkippedInvalidQuantity,
            riskPercentPerTrade = run.riskPercentPerTrade,
            atrRiskManagementEnabled = run.atrRiskManagementEnabled,
            atrPeriod = run.atrPeriod,
            atrInitialStopMultiplier = run.atrInitialStopMultiplier,
            atrTrailingStopEnabled = run.atrTrailingStopEnabled,
            atrTrailingStopMultiplier = run.atrTrailingStopMultiplier,
        )
    }
}
