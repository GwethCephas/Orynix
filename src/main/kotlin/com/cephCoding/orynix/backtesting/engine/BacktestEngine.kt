package com.cephCoding.orynix.backtesting.engine

import com.cephCoding.orynix.backtesting.domain.EquityPoint
import com.cephCoding.orynix.backtesting.domain.ExitReason
import com.cephCoding.orynix.backtesting.domain.Trade
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import com.cephCoding.orynix.backtesting.strategy.BacktestStrategy
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.technicalanalysis.indicator.Atr
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

enum class StrategyAction { ENTER_LONG, EXIT_LONG, HOLD }

private enum class ExecutionState { FLAT, IN_POSITION }

/**
 * Optional, strategy-independent ATR risk management. atrPeriod/initialStopMultiplier are always
 * read; trailingStopMultiplier is only read when trailingEnabled - validated upstream in
 * BacktestService, not re-validated here.
 */
data class RiskManagementConfig(
    val atrPeriod: Int,
    val initialStopMultiplier: BigDecimal,
    val trailingEnabled: Boolean,
    val trailingStopMultiplier: BigDecimal,
)

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
    // Diagnostics (CLAUDE.md Section 18) - exist specifically to distinguish "the strategy never
    // signaled" from "it signaled but every entry was skipped," since totalTrades alone cannot.
    val candlesEvaluated: Int,
    val entrySignalsGenerated: Int,
    val exitSignalsGenerated: Int,
    // entriesSkippedInsufficientCash: a sized entry (fixed or risk-based) that cash couldn't cover.
    // entriesSkippedInvalidQuantity: risk-based sizing alone could not produce a usable quantity at
    // all (degenerate ATR/multiplier) - a distinct failure mode from "sized but unaffordable," so it
    // is counted separately rather than folded into entriesSkippedInsufficientCash.
    val entriesSkippedInsufficientCash: Int,
    val entriesSkippedInvalidQuantity: Int,
)

/**
 * Pure, Spring-free bar-by-bar simulation - see CLAUDE.md Section 18 for the full look-ahead-bias
 * analysis. The load-bearing invariant: a signal is evaluated from bar i's close (a fully formed
 * bar), and any resulting fill executes at bar (i+1)'s open, never at bar i's own close. Costs
 * (commission, slippage) are applied at the fill, not at the signal. A position still open at the
 * final bar is force-closed at that bar's close (no next-bar open exists to fill at), so every
 * persisted trade always has both an entry and an exit - there is no open/dangling trade state.
 *
 * ATR risk management (optional, see RiskManagementConfig) adds a second, independent no-lookahead
 * invariant: the stop level checked against bar i's low must be the value finalized through bar
 * i-1; bar i's own high/close/ATR may only update the stop for use starting bar i+1. The entry bar
 * itself is exempt from the stop check entirely (its own pre-fill price movement can never trigger
 * a stop), though its high still feeds the first trailing-stop update. A stop breach is a same-bar
 * fill (checked against bar.low, filled within that same bar) - deliberately different from a
 * strategy signal exit, which stays a next-bar-open fill; if a pending signal exit already resolved
 * at step 1, the position is already FLAT by the time the stop check runs, so the signal exit wins.
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
        quantity: BigDecimal? = null,
        commissionPerOrder: BigDecimal,
        slippageBps: Int,
        riskPercentPerTrade: BigDecimal? = null,
        riskManagementConfig: RiskManagementConfig? = null,
    ): BacktestEngineResult {
        require(firstTradableIndex in candles.indices) { "firstTradableIndex out of range" }
        require((quantity != null) != (riskPercentPerTrade != null)) {
            "exactly one of quantity or riskPercentPerTrade must be supplied"
        }
        require(riskManagementConfig == null || firstTradableIndex >= riskManagementConfig.atrPeriod) {
            "firstTradableIndex must be >= riskManagementConfig.atrPeriod"
        }

        val slippageFraction = BigDecimal(slippageBps).divide(BigDecimal(10_000), INTERNAL_SCALE, RoundingMode.HALF_UP)

        // Computed once, only when enabled - never run on the disabled hot path.
        val atrSeries: List<Double>? = riskManagementConfig?.let {
            Atr.calculate(
                highs = candles.map { c -> c.high.toDouble() },
                lows = candles.map { c -> c.low.toDouble() },
                closes = candles.map { c -> c.close.toDouble() },
                period = it.atrPeriod,
            )
        }
        // Atr.calculate(...)[0] aligns to original candle index atrPeriod (verified against
        // AdxCalculator's identical use of Atr.trueRange + Ema.wilderSmooth). BigDecimal.valueOf,
        // never the BigDecimal(Double) constructor, to avoid capturing the binary FP expansion.
        fun atrAt(candleIndex: Int): BigDecimal? {
            val series = atrSeries ?: return null
            val idx = candleIndex - riskManagementConfig!!.atrPeriod
            return if (idx in series.indices) BigDecimal.valueOf(series[idx]) else null
        }

        var cash = initialCapital
        var state = ExecutionState.FLAT
        var pendingAction: StrategyAction? = null
        var openEntryTimestamp: Instant? = null
        var openEntryPrice: BigDecimal? = null
        var openQuantity: BigDecimal? = null
        var entryIndex: Int? = null
        var openInitialStopPrice: BigDecimal? = null
        var openCurrentStopPrice: BigDecimal? = null
        var openHighestHighSinceEntry: BigDecimal? = null

        val trades = mutableListOf<Trade>()
        val equityCurve = mutableListOf<EquityPoint>()
        var peakEquity = initialCapital
        var maxDrawdownPercent = BigDecimal.ZERO
        var entrySignalsGenerated = 0
        var exitSignalsGenerated = 0
        var entriesSkippedInsufficientCash = 0
        var entriesSkippedInvalidQuantity = 0

        fun closePosition(fillPrice: BigDecimal, exitTimestamp: Instant, exitReason: ExitReason) {
            val entryPrice = requireNotNull(openEntryPrice)
            val entryTimestamp = requireNotNull(openEntryTimestamp)
            val tradeQuantity = requireNotNull(openQuantity)
            val proceeds = tradeQuantity.multiply(fillPrice).subtract(commissionPerOrder)
            cash = cash.add(proceeds)
            trades.add(
                buildTrade(
                    sequenceNumber = trades.size,
                    entryTimestamp = entryTimestamp,
                    entryPrice = entryPrice,
                    exitTimestamp = exitTimestamp,
                    exitPrice = fillPrice,
                    quantity = tradeQuantity,
                    commissionPerOrder = commissionPerOrder,
                    exitReason = exitReason,
                    initialStopPrice = openInitialStopPrice,
                    finalStopPrice = openCurrentStopPrice,
                ),
            )
            state = ExecutionState.FLAT
            openEntryTimestamp = null
            openEntryPrice = null
            openQuantity = null
            entryIndex = null
            openInitialStopPrice = null
            openCurrentStopPrice = null
            openHighestHighSinceEntry = null
        }

        for (i in firstTradableIndex until candles.size) {
            val bar = candles[i]

            // 1. Resolve a fill decided at the PREVIOUS bar's close, executed at THIS bar's open.
            when (pendingAction) {
                StrategyAction.ENTER_LONG -> {
                    val fillPrice = bar.open.multiply(BigDecimal.ONE.add(slippageFraction))
                    val atrAtSignal = if (riskManagementConfig != null) atrAt(i - 1) else null
                    val resolvedQuantity = when {
                        quantity != null -> quantity
                        atrAtSignal != null -> computeRiskBasedQuantity(
                            cash = cash,
                            atrAtSignal = atrAtSignal,
                            riskPercent = requireNotNull(riskPercentPerTrade),
                            initialStopMultiplier = riskManagementConfig!!.initialStopMultiplier,
                        )
                        else -> null
                    }
                    if (resolvedQuantity == null) {
                        entriesSkippedInvalidQuantity++
                    } else {
                        val cost = resolvedQuantity.multiply(fillPrice).add(commissionPerOrder)
                        if (cash >= cost) {
                            cash = cash.subtract(cost)
                            state = ExecutionState.IN_POSITION
                            openEntryTimestamp = bar.timestamp
                            openEntryPrice = fillPrice
                            openQuantity = resolvedQuantity
                            entryIndex = i
                            if (riskManagementConfig != null && atrAtSignal != null) {
                                val stop = fillPrice.subtract(riskManagementConfig.initialStopMultiplier.multiply(atrAtSignal))
                                openInitialStopPrice = stop
                                openCurrentStopPrice = stop
                                // Fill may exceed bar.high due to slippage on the buy - the highest
                                // price "since entry" must account for the actual fill, not just the
                                // bar's own printed high.
                                openHighestHighSinceEntry = fillPrice.max(bar.high)
                            }
                        } else {
                            entriesSkippedInsufficientCash++
                        }
                    }
                    // insufficient cash / invalid quantity: the entry is silently skipped (visible in
                    // the result via the two counters above), not an error.
                }
                StrategyAction.EXIT_LONG -> {
                    val fillPrice = bar.open.multiply(BigDecimal.ONE.subtract(slippageFraction))
                    closePosition(fillPrice, bar.timestamp, ExitReason.SIGNAL)
                }
                else -> Unit
            }
            pendingAction = null

            // 1.5. ATR stop/trailing-stop check - same-bar fill, gated on IN_POSITION and the
            // entry-bar exemption (i != entryIndex). Must run BEFORE step 2's force-close, so a stop
            // breach on the literal final bar is attributed to ATR_STOP, not END_OF_PERIOD.
            if (state == ExecutionState.IN_POSITION && riskManagementConfig != null && i != entryIndex) {
                val stopPrice = requireNotNull(openCurrentStopPrice)
                if (bar.low <= stopPrice) {
                    val stopFillBase = if (bar.open <= stopPrice) bar.open else stopPrice
                    val stopFillPrice = stopFillBase.multiply(BigDecimal.ONE.subtract(slippageFraction))
                    closePosition(stopFillPrice, bar.timestamp, ExitReason.ATR_STOP)
                }
            }

            // 2. Force-close on the FINAL bar if still open - no next-bar open exists to fill at.
            if (i == candles.size - 1 && state == ExecutionState.IN_POSITION) {
                val fillPrice = bar.close.multiply(BigDecimal.ONE.subtract(slippageFraction))
                closePosition(fillPrice, bar.timestamp, ExitReason.END_OF_PERIOD)
            }

            // 3. Record this bar's equity: cash plus the mark-to-market value of any open position.
            val markToMarket = if (state == ExecutionState.IN_POSITION) requireNotNull(openQuantity).multiply(bar.close) else BigDecimal.ZERO
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

            // 3.5. Trailing-stop ratchet update using THIS bar's own high/ATR, for use starting i+1.
            // Deliberately uses atrAt(i) (this bar's just-closed ATR), unlike the initial stop above
            // which used atrAt(i-1) - the initial stop is a pre-fill decision (no lookahead allowed),
            // while this is a post-close, next-bar-only recalculation (this bar's own data is fair
            // game for deciding what happens starting the next bar).
            if (state == ExecutionState.IN_POSITION && riskManagementConfig != null && riskManagementConfig.trailingEnabled) {
                openHighestHighSinceEntry = requireNotNull(openHighestHighSinceEntry).max(bar.high)
                val atrNow = atrAt(i)
                if (atrNow != null) {
                    val candidateStop = openHighestHighSinceEntry!!.subtract(riskManagementConfig.trailingStopMultiplier.multiply(atrNow))
                    openCurrentStopPrice = requireNotNull(openCurrentStopPrice).max(candidateStop)
                }
            }

            // 4. Evaluate a NEW signal from this bar's close, to fill at the NEXT bar's open.
            //    Skipped on the final bar - there is no next bar to fill at.
            if (i < candles.size - 1) {
                val action = strategy.signalAt(i, candles)
                if (state == ExecutionState.FLAT && action == StrategyAction.ENTER_LONG) {
                    entrySignalsGenerated++
                    pendingAction = StrategyAction.ENTER_LONG
                } else if (state == ExecutionState.IN_POSITION && action == StrategyAction.EXIT_LONG) {
                    exitSignalsGenerated++
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
            candlesEvaluated = candles.size - firstTradableIndex,
            entrySignalsGenerated = entrySignalsGenerated,
            exitSignalsGenerated = exitSignalsGenerated,
            entriesSkippedInsufficientCash = entriesSkippedInsufficientCash,
            entriesSkippedInvalidQuantity = entriesSkippedInvalidQuantity,
        )
    }

    /**
     * riskPerUnit = initialStopMultiplier * atrAtSignal - the fill price cancels out algebraically
     * (stopPrice = fillPrice - initialStopMultiplier*atr, so fillPrice - stopPrice is exactly that
     * product), so the degenerate-ATR/multiplier guard can run before any fill price is known.
     * Commission/slippage are deliberately NOT netted out of the risk budget (CLAUDE.md Section 18)
     * - the budget covers price risk only, so a realized stop-loss will run slightly larger than the
     * nominal riskPercent. The actual budget/FLOOR/degenerate-guard arithmetic lives in RiskSizing,
     * shared with the standalone risk calculator (riskcalculator/) - this function only computes the
     * ATR-specific riskPerUnit and delegates.
     */
    private fun computeRiskBasedQuantity(
        cash: BigDecimal,
        atrAtSignal: BigDecimal,
        riskPercent: BigDecimal,
        initialStopMultiplier: BigDecimal,
    ): BigDecimal? {
        val riskPerUnit = initialStopMultiplier.multiply(atrAtSignal)
        return RiskSizing.computeQuantity(cash, riskPercent, riskPerUnit)
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
        initialStopPrice: BigDecimal?,
        finalStopPrice: BigDecimal?,
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
            initialStopPrice = initialStopPrice?.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            finalStopPrice = finalStopPrice?.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
        )
    }
}
