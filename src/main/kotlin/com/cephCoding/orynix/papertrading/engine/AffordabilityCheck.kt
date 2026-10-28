package com.cephCoding.orynix.papertrading.engine

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pass/fail plus the exact shortfall for a proposed OPEN order against current buying power -
 * the shortfall feeds directly into InsufficientBuyingPowerException's message, never a bare
 * boolean with no explanation of by how much.
 */
object AffordabilityCheck {

    data class Result(val sufficient: Boolean, val shortfall: BigDecimal)

    fun check(buyingPower: BigDecimal, required: BigDecimal): Result {
        val shortfall = required.subtract(buyingPower).setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP)
        return if (shortfall.signum() <= 0) {
            Result(sufficient = true, shortfall = BigDecimal.ZERO.setScale(PaperTradingAccounting.MONEY_SCALE, RoundingMode.HALF_UP))
        } else {
            Result(sufficient = false, shortfall = shortfall)
        }
    }
}
