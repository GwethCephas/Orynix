package com.cephCoding.orynix.backtesting.engine

import com.cephCoding.orynix.backtesting.domain.EquityPoint
import com.cephCoding.orynix.backtesting.domain.ExitReason
import com.cephCoding.orynix.backtesting.domain.Trade
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import com.cephCoding.orynix.backtesting.strategy.BacktestStrategy
import com.cephCoding.orynix.market.domain.Candle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

enum class StrategyAction { ENTER_LONG, EXIT_LONG, HOLD }

private enum class ExecutionState { FLAT, IN_POSITION }

data class BacktestEngineResult(
    val trades: List<Trade>,
    val equityCurve: List<EquityPoint>,
    val finalEquity: BigDecimal,
    val netPnl: BigDecimal,
    val netReturnPercent: BigDecimal,
    val maxDrawdownPercent: BigDecimal,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakevenTrades: Int,
    val winRate: BigDecimal?,
    val profitFactor: BigDecimal?,
)

/**
 * Pure, Spring-free bar-by-bar simulation - see CLAUDE.md Section 18 for the full look-ahead-bias
 * analysis. The load-bearing invariant: a signal is evaluated from bar i's close (a fully formed
 * bar), and any resulting fill executes at bar (i+1)'s open, never at bar i's own close. Costs
 * (commission, slippage) are applied at the fill, not at the signal. A position still open at the
 * final bar is force-closed at that bar's close (no next-bar open exists to fill at), so every
 * persisted trade always has both an entry and an exit - there is no open/dangling trade state.
 */
object BacktestEngine {

    private val HUNDRED = BigDecimal(100)
    private const val PRICE_SCALE = 4
    private const val PERCENT_SCALE = 4
    private const val INTERNAL_SCALE = 8

    fun run(
        candles: List<Candle>,
        firstTradableIndex: Int,
        strategy: BacktestStrategy,
        initialCapital: BigDecimal,
        quantity: BigDecimal,
        commissionPerOrder: BigDecimal,
        slippageBps: Int,
    ): BacktestEngineResult {
        require(firstTradableIndex in candles.indices) { "firstTradableIndex out of range" }

        val slippageFraction = BigDecimal(slippageBps).divide(BigDecimal(10_000), INTERNAL_SCALE, RoundingMode.HALF_UP)

        var cash = initialCapital
        var state = ExecutionState.FLAT
        var pendingAction: StrategyAction? = null
        var openEntryTimestamp: Instant? = null
        var openEntryPrice: BigDecimal? = null

        val trades = mutableListOf<Trade>()
        val equityCurve = mutableListOf<EquityPoint>()
        var peakEquity = initialCapital
        var maxDrawdownPercent = BigDecimal.ZERO

        fun closePosition(fillPrice: BigDecimal, exitTimestamp: Instant, exitReason: ExitReason) {
            val entryPrice = requireNotNull(openEntryPrice)
            val entryTimestamp = requireNotNull(openEntryTimestamp)
            val proceeds = quantity.multiply(fillPrice).subtract(commissionPerOrder)
            cash = cash.add(proceeds)
            trades.add(
                buildTrade(
                    sequenceNumber = trades.size,
                    entryTimestamp = entryTimestamp,
                    entryPrice = entryPrice,
                    exitTimestamp = exitTimestamp,
                    exitPrice = fillPrice,
                    quantity = quantity,
                    commissionPerOrder = commissionPerOrder,
                    exitReason = exitReason,
                ),
            )
            state = ExecutionState.FLAT
            openEntryTimestamp = null
            openEntryPrice = null
        }

        for (i in firstTradableIndex until candles.size) {
            val bar = candles[i]

            // 1. Resolve a fill decided at the PREVIOUS bar's close, executed at THIS bar's open.
            when (pendingAction) {
                StrategyAction.ENTER_LONG -> {
                    val fillPrice = bar.open.multiply(BigDecimal.ONE.add(slippageFraction))
                    val cost = quantity.multiply(fillPrice).add(commissionPerOrder)
                    if (cash >= cost) {
                        cash = cash.subtract(cost)
                        state = ExecutionState.IN_POSITION
                        openEntryTimestamp = bar.timestamp
                        openEntryPrice = fillPrice
                    }
                    // else: insufficient cash - the entry is silently skipped (visible in the
                    // result as fewer trades than signals), not an error.
                }
                StrategyAction.EXIT_LONG -> {
                    val fillPrice = bar.open.multiply(BigDecimal.ONE.subtract(slippageFraction))
                    closePosition(fillPrice, bar.timestamp, ExitReason.SIGNAL)
                }
                else -> Unit
            }
            pendingAction = null

            // 2. Force-close on the FINAL bar if still open - no next-bar open exists to fill at.
            if (i == candles.size - 1 && state == ExecutionState.IN_POSITION) {
                val fillPrice = bar.close.multiply(BigDecimal.ONE.subtract(slippageFraction))
                closePosition(fillPrice, bar.timestamp, ExitReason.END_OF_PERIOD)
            }

            // 3. Record this bar's equity: cash plus the mark-to-market value of any open position.
            val markToMarket = if (state == ExecutionState.IN_POSITION) quantity.multiply(bar.close) else BigDecimal.ZERO
            val equity = cash.add(markToMarket).setScale(PRICE_SCALE, RoundingMode.HALF_UP)
            if (equity > peakEquity) peakEquity = equity
            if (peakEquity.signum() > 0) {
                val drawdown = peakEquity.subtract(equity)
                    .divide(peakEquity, INTERNAL_SCALE, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                if (drawdown > maxDrawdownPercent) maxDrawdownPercent = drawdown
            }
            equityCurve.add(
                EquityPoint(
                    sequenceNumber = equityCurve.size,
                    timestamp = bar.timestamp,
                    cash = cash.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                    equity = equity,
                ),
            )

            // 4. Evaluate a NEW signal from this bar's close, to fill at the NEXT bar's open.
            //    Skipped on the final bar - there is no next bar to fill at.
            if (i < candles.size - 1) {
                val action = strategy.signalAt(i, candles)
                if (state == ExecutionState.FLAT && action == StrategyAction.ENTER_LONG) {
                    pendingAction = StrategyAction.ENTER_LONG
                } else if (state == ExecutionState.IN_POSITION && action == StrategyAction.EXIT_LONG) {
                    pendingAction = StrategyAction.EXIT_LONG
                }
            }
        }

        val finalEquity = cash.setScale(PRICE_SCALE, RoundingMode.HALF_UP)
        val netPnl = finalEquity.subtract(initialCapital).setScale(PRICE_SCALE, RoundingMode.HALF_UP)
        val netReturnPercent = if (initialCapital.signum() != 0) {
            netPnl.divide(initialCapital, INTERNAL_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        }

        val winningTrades = trades.count { it.netPnl.signum() > 0 }
        val losingTrades = trades.count { it.netPnl.signum() < 0 }
        val breakevenTrades = trades.size - winningTrades - losingTrades

        // winRate is null (undefined), never zero, when there are no closed trades to rate.
        val winRate = if (trades.isNotEmpty()) {
            BigDecimal(winningTrades).divide(BigDecimal(trades.size), INTERNAL_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        } else {
            null
        }

        val grossProfit = trades.filter { it.netPnl.signum() > 0 }.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.netPnl) }
        val grossLoss = trades.filter { it.netPnl.signum() < 0 }.fold(BigDecimal.ZERO) { acc, t -> acc.add(t.netPnl) }.abs()
        // profitFactor is null (undefined) whenever there are no losing trades to divide by -
        // whether or not gross profit is positive - never Infinity/NaN, per CLAUDE.md's JSON-safety
        // convention for financial metrics.
        val profitFactor = if (grossLoss.signum() != 0) {
            grossProfit.divide(grossLoss, INTERNAL_SCALE, RoundingMode.HALF_UP).setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        } else {
            null
        }

        return BacktestEngineResult(
            trades = trades,
            equityCurve = equityCurve,
            finalEquity = finalEquity,
            netPnl = netPnl,
            netReturnPercent = netReturnPercent,
            maxDrawdownPercent = maxDrawdownPercent.setScale(PERCENT_SCALE, RoundingMode.HALF_UP),
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            breakevenTrades = breakevenTrades,
            winRate = winRate,
            profitFactor = profitFactor,
        )
    }

    private fun buildTrade(
        sequenceNumber: Int,
        entryTimestamp: Instant,
        entryPrice: BigDecimal,
        exitTimestamp: Instant,
        exitPrice: BigDecimal,
        quantity: BigDecimal,
        commissionPerOrder: BigDecimal,
        exitReason: ExitReason,
    ): Trade {
        val grossPnl = exitPrice.subtract(entryPrice).multiply(quantity)
        val totalCommission = commissionPerOrder.multiply(BigDecimal(2))
        val netPnl = grossPnl.subtract(totalCommission)
        val entryCost = entryPrice.multiply(quantity)
        // returnPercent's denominator is the entry cost (quantity * entryPrice) - the capital this
        // specific trade actually tied up, not initialCapital or final equity.
        val returnPercent = if (entryCost.signum() != 0) {
            netPnl.divide(entryCost, INTERNAL_SCALE, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
        }
        return Trade(
            sequenceNumber = sequenceNumber,
            direction = TradeDirection.LONG,
            entryTimestamp = entryTimestamp,
            entryPrice = entryPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            exitTimestamp = exitTimestamp,
            exitPrice = exitPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            quantity = quantity,
            commission = totalCommission.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            grossPnl = grossPnl.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            netPnl = netPnl.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            returnPercent = returnPercent,
            exitReason = exitReason,
        )
    }
}
