package com.cephCoding.orynix.papertrading.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

/**
 * shortCollateralMultiplier is a simulation-only risk-bounding device, not a real margin/Reg-T
 * rule - see CLAUDE.md Section 22 for the full derivation of why it's needed at all (without it,
 * opening a short costs only its commission regardless of size). maxPriceStalenessDays guards
 * order execution only; valuation degrades per-symbol instead of hard-failing (see
 * PaperTradingPriceResolver).
 */
@ConfigurationProperties(prefix = "paper-trading")
data class PaperTradingProperties(
    val maxPriceStalenessDays: Long,
    val shortCollateralMultiplier: BigDecimal,
)
