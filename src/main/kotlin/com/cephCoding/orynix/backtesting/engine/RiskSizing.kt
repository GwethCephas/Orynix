package com.cephCoding.orynix.backtesting.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure, Spring-free quantity-from-risk-budget arithmetic, shared by BacktestEngine's ATR-based
 * sizing and the standalone trade-setup risk calculator (riskcalculator/) - both reduce to the
 * identical "risk budget / risk-per-unit, FLOOR, degenerate guard" core once each caller supplies
 * its own riskPerUnit (ATR-multiplier-derived for the engine, |entry - stop| for the calculator).
 * Extracted specifically so the sizing formula is never duplicated - see CLAUDE.md Section 18/19.
 */
object RiskSizing {

    private val HUNDRED = BigDecimal(100)
    private const val INTERNAL_SCALE = 8

    /**
     * quantity = (cash * riskPercent / 100) / riskPerUnit, floored - never rounds up past the
     * declared risk budget. Returns null when riskPerUnit is non-positive (degenerate stop
     * distance/ATR) or the resulting quantity would be zero or negative - callers must never
     * fabricate a quantity in either case, only report that sizing could not be performed.
     */
    fun computeQuantity(
        cash: BigDecimal,
        riskPercent: BigDecimal,
        riskPerUnit: BigDecimal,
    ): BigDecimal? {
        if (riskPerUnit.signum() <= 0) return null
        val riskBudget = cash.multiply(riskPercent).divide(HUNDRED, INTERNAL_SCALE, RoundingMode.HALF_UP)
        val quantity = riskBudget.divide(riskPerUnit, INTERNAL_SCALE, RoundingMode.FLOOR)
        return if (quantity.signum() <= 0) null else quantity
    }
}
