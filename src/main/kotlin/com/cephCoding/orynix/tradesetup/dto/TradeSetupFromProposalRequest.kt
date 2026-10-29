package com.cephCoding.orynix.tradesetup.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * Creates a TradeSetup from a server-persisted AiTradeSetupProposalEntity (CLAUDE.md Section 21's
 * Integration A), rather than TradeSetupCreateRequest's "client resubmits the proposal's own field
 * values" path - symbol/direction/entry/stop/target/rationale/AI-provenance are all copied from
 * the verified proposal row looked up by `aiProposalId`, never taken from this request.
 * `accountProfileId` is still client-supplied, exactly like TradeSetupCreateRequest - it isn't
 * part of the proposal itself.
 */
data class TradeSetupFromProposalRequest(
    @field:NotNull
    val aiProposalId: UUID,

    val accountProfileId: UUID?,
)
