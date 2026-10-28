package com.cephCoding.orynix.papertrading.engine

import com.cephCoding.orynix.papertrading.domain.OrderSide
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure, Spring-free fill/collateral/equity arithmetic - no database, no Spring context, matching
 * BacktestEngine/RiskSizing's precedent for why this kind of math lives in its own object. See
 * CLAUDE.md Section 22 for the full derivation and the worked long/short reconciliation examples
 * this object must satisfy exactly (verified in PaperTradingAccountingTest, not just asserted).
 *
 * Scale convention mirrors BacktestEngine exactly: money/price fields round to MONEY_SCALE (4),
 * quantities to QUANTITY_SCALE (8), both HALF_UP; intermediate divisions use INTERNAL_SCALE (8)
 * before a final rounding step at the boundary.
 */
object PaperTradingAccounting {

    const val MONEY_SCALE = 4
    const val QUANTITY_SCALE = 8
    private const val INTERNAL_SCALE = 8

    data class FillResult(
        val cashDelta: BigDecimal,
        val newOpenQuantity: BigDecimal,
        val newAvgEntryPrice: BigDecimal,
        val realizedPnl: BigDecimal,
    )

    /**
     * Weighted-average entry price when adding `addedQty` @ `fillPrice` to an existing exposure
     * of `existingQty` @ `existingAvg`. Direction-agnostic - the open*Fill functions below handle
     * cash-flow sign and realized P&L, which differ between long and short; the averaging math
     * itself does not.
     */
    fun weightedAverageEntryPrice(
        existingQty: BigDecimal,
        existingAvg: BigDecimal,
        addedQty: BigDecimal,
        fillPrice: BigDecimal,
    ): BigDecimal {
        val totalQty = existingQty.add(addedQty)
        require(totalQty.signum() > 0) { "totalQty must be positive" }
        return existingAvg.multiply(existingQty).add(fillPrice.multiply(addedQty))
            .divide(totalQty, INTERNAL_SCALE, RoundingMode.HALF_UP)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    /** BUY+OPEN: cash -= addedQty*fillPrice + commission. realizedPnl is always zero (nothing closed). */
    fun computeOpenLongFill(
        existingQty: BigDecimal,
        existingAvg: BigDecimal,
        addedQty: BigDecimal,
        fillPrice: BigDecimal,
        commission: BigDecimal,
    ): FillResult {
        val newAvg = weightedAverageEntryPrice(existingQty, existingAvg, addedQty, fillPrice)
        val newQty = existingQty.add(addedQty).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
        val cost = addedQty.multiply(fillPrice).add(commission)
        return FillResult(
            cashDelta = cost.negate().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            newOpenQuantity = newQty,
            newAvgEntryPrice = newAvg,
            realizedPnl = zeroMoney(),
        )
    }

    /**
     * SELL+CLOSE: cash += closedQty*fillPrice - commission.
     * realizedPnl = (fillPrice - avgEntryPrice) * closedQty - commission - this closing
     * commission only; the opening commission already hit cash/equity permanently at open time
     * and must never be folded in here (see CLAUDE.md Section 22's reconciliation identity).
     */
    fun computeCloseLongFill(
        openQty: BigDecimal,
        avgEntryPrice: BigDecimal,
        closedQty: BigDecimal,
        fillPrice: BigDecimal,
        commission: BigDecimal,
    ): FillResult {
        require(closedQty.signum() > 0 && closedQty <= openQty) { "closedQty must be in (0, openQty]" }
        val proceeds = closedQty.multiply(fillPrice).subtract(commission)
        val realized = fillPrice.subtract(avgEntryPrice).multiply(closedQty).subtract(commission)
        val remainingQty = openQty.subtract(closedQty).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
        return FillResult(
            cashDelta = proceeds.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            newOpenQuantity = remainingQty,
            newAvgEntryPrice = avgEntryPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            realizedPnl = realized.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        )
    }

    /**
     * SELL+OPEN: cash += addedQty*fillPrice - commission (short-sale proceeds). These proceeds
     * are deliberately NOT free spendable buying power - computeReservedCollateral is what
     * actually bounds a short, not this cash credit.
     */
    fun computeOpenShortFill(
        existingQty: BigDecimal,
        existingAvg: BigDecimal,
        addedQty: BigDecimal,
        fillPrice: BigDecimal,
        commission: BigDecimal,
    ): FillResult {
        val newAvg = weightedAverageEntryPrice(existingQty, existingAvg, addedQty, fillPrice)
        val newQty = existingQty.add(addedQty).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
        val proceeds = addedQty.multiply(fillPrice).subtract(commission)
        return FillResult(
            cashDelta = proceeds.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            newOpenQuantity = newQty,
            newAvgEntryPrice = newAvg,
            realizedPnl = zeroMoney(),
        )
    }

    /**
     * BUY+CLOSE: cash -= closedQty*fillPrice + commission (buy-to-cover cost).
     * realizedPnl = (avgEntryPrice - fillPrice) * closedQty - commission (closing commission only,
     * same rule as the long close above).
     */
    fun computeCloseShortFill(
        openQty: BigDecimal,
        avgEntryPrice: BigDecimal,
        closedQty: BigDecimal,
        fillPrice: BigDecimal,
        commission: BigDecimal,
    ): FillResult {
        require(closedQty.signum() > 0 && closedQty <= openQty) { "closedQty must be in (0, openQty]" }
        val cost = closedQty.multiply(fillPrice).add(commission)
        val realized = avgEntryPrice.subtract(fillPrice).multiply(closedQty).subtract(commission)
        val remainingQty = openQty.subtract(closedQty).setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
        return FillResult(
            cashDelta = cost.negate().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            newOpenQuantity = remainingQty,
            newAvgEntryPrice = avgEntryPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
            realizedPnl = realized.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
        )
    }

    // BigDecimal scale differences (e.g. 10.00000000 vs 10) make `==`/`.equals()` unsafe for this
    // check - compareTo (which Kotlin's `==` on BigDecimal does NOT use, but this explicit call
    // makes unmistakable) is the only correct way to detect a full close.
    fun isFullyClosed(openQty: BigDecimal, closedQty: BigDecimal): Boolean = openQty.compareTo(closedQty) == 0

    /** Only shorts reserve collateral - the multiplier is what actually bounds short risk without
     * a margin-call engine: as an open short's mark-to-market loss grows, more collateral is
     * reserved, consuming buying power for new orders (see CLAUDE.md Section 22). */
    fun computeReservedCollateral(openQty: BigDecimal, markPrice: BigDecimal, collateralMultiplier: BigDecimal): BigDecimal =
        openQty.multiply(markPrice).multiply(collateralMultiplier).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    fun computeMarketValue(openQty: BigDecimal, markPrice: BigDecimal): BigDecimal =
        openQty.multiply(markPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    fun computeBuyingPower(cash: BigDecimal, totalReservedCollateral: BigDecimal): BigDecimal =
        cash.subtract(totalReservedCollateral).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    // equity = cash + marketValueLong - shortMarketValue (UNmultiplied short market value - the
    // collateral multiplier is a buying-power concept only, never an equity one, or realized
    // short gains/losses would be double-counted - see CLAUDE.md Section 22 §0.1/§0.2).
    fun computeEquity(cash: BigDecimal, marketValueLong: BigDecimal, shortMarketValue: BigDecimal): BigDecimal =
        cash.add(marketValueLong).subtract(shortMarketValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    fun computeUnrealizedPnlLong(openQty: BigDecimal, avgEntryPrice: BigDecimal, markPrice: BigDecimal): BigDecimal =
        markPrice.subtract(avgEntryPrice).multiply(openQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    fun computeUnrealizedPnlShort(openQty: BigDecimal, avgEntryPrice: BigDecimal, markPrice: BigDecimal): BigDecimal =
        avgEntryPrice.subtract(markPrice).multiply(openQty).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    /** OPEN-order buying-power requirements only - CLOSE orders are never gated (gating a close
     * would trap a user in a losing position, exactly the forced-liquidation behavior this
     * milestone defers - see CLAUDE.md Section 22). */
    fun requiredBuyingPowerForOpenLong(qty: BigDecimal, fillPrice: BigDecimal, commission: BigDecimal): BigDecimal =
        qty.multiply(fillPrice).add(commission).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    fun requiredBuyingPowerForOpenShort(
        qty: BigDecimal,
        fillPrice: BigDecimal,
        commission: BigDecimal,
        collateralMultiplier: BigDecimal,
    ): BigDecimal =
        commission.add(qty.multiply(fillPrice).multiply(collateralMultiplier.subtract(BigDecimal.ONE)))
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    // Slippage always worsens the trader's price, mirroring BacktestEngine exactly: a BUY fills
    // higher, a SELL fills lower, regardless of whether the order is opening or closing exposure.
    fun applySlippage(markPrice: BigDecimal, slippageBps: Int, side: OrderSide): BigDecimal {
        val fraction = BigDecimal(slippageBps).divide(BigDecimal(10_000), INTERNAL_SCALE, RoundingMode.HALF_UP)
        val adjusted = when (side) {
            OrderSide.BUY -> markPrice.multiply(BigDecimal.ONE.add(fraction))
            OrderSide.SELL -> markPrice.multiply(BigDecimal.ONE.subtract(fraction))
        }
        return adjusted.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    private fun zeroMoney(): BigDecimal = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
}
