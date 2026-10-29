package com.cephCoding.orynix.agents.domain

import com.cephCoding.orynix.riskcalculator.domain.RiskCalculationResult
import java.util.UUID

/**
 * A model-proposed trade setup that survived MarketAnalysisService's semantic-sanity validation,
 * paired with its deterministic sizing (or the lack of it). `sizing` is null on every path that
 * can't produce a number - a SHORT setup (RiskCalculatorService is long-only), or no account
 * context supplied - and `limitations` always explains why, never leaving a silently-absent field
 * with no reason given (CLAUDE.md Section 20, mirroring RiskCalculationResult's own UNSUPPORTED
 * pattern one layer up).
 *
 * `id` identifies the durable AiTradeSetupProposalEntity row this proposal was persisted as
 * (CLAUDE.md Section 21's Integration A) - the server-verifiable handle a client uses to save this
 * exact proposal as a TradeSetup via POST /trade-setups/from-ai-proposal, instead of resubmitting
 * (and the server blindly trusting) the proposal's own field values.
 */
data class TradeSetupProposal(
    val id: UUID,
    val setup: ModelTradeSetup,
    val sizing: RiskCalculationResult?,
    val limitations: List<String>,
)
