package com.cephCoding.orynix.tradesetup.domain

import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationResult

/**
 * Always ephemeral, never persisted or cached - every evaluation recomputes fresh from the
 * setup's current stored prices and the current state of any referenced account profile, so a
 * result is never presented as current when it might actually be stale (CLAUDE.md Section 21).
 * sizing is null on every path that can't produce a number (SHORT direction, missing stop-loss),
 * always paired with a human-readable reason in limitations - mirrors
 * MarketAnalysisService.attachSizing's TradeSetupProposal contract exactly.
 */
data class TradeSetupRiskEvaluation(
    val sizing: RiskCalculationResult?,
    val limitations: List<String>,
)
