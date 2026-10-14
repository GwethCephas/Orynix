package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Serves both "create manually" and "save a proposed setup from an AI analysis" - source
 * discriminates which, and aiProviderName/aiModel/aiAnalysisAsOf are required-together iff
 * source=AI_ANALYSIS, forbidden iff MANUAL (validated in TradeSetupService, reject-don't-ignore,
 * matching BacktestService.rejectFieldsFor's precedent - Bean Validation can't express a
 * conditional-on-another-field requirement). There is no aiAnalysisId: the AI Market Analysis
 * Agent persists nothing and has no stable id to reference (CLAUDE.md Section 21) - the client
 * resubmits the proposal's own field values it already received from POST /market-analyses.
 */
data class TradeSetupCreateRequest(
    @field:NotBlank
    val symbol: String,

    @field:NotNull
    val direction: TradeDirection,

    @field:NotNull
    @field:Positive
    val entryPrice: BigDecimal,

    @field:Positive
    val stopLossPrice: BigDecimal?,

    @field:Positive
    val targetPrice: BigDecimal?,

    val rationale: String?,

    @field:NotNull
    val source: TradeSetupSource,

    val aiProviderName: String?,

    val aiModel: String?,

    val aiAnalysisAsOf: Instant?,

    val accountProfileId: UUID?,
)
