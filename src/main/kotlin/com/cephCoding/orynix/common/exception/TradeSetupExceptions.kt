package com.cephCoding.orynix.common.exception

import java.util.UUID

class InvalidTradeSetupException(message: String) : RuntimeException(message)

class InvalidTradeSetupTransitionException(message: String) : RuntimeException(message)

class TradeSetupNotFoundException(id: UUID) : RuntimeException("Trade setup not found: $id")

/** A well-formed request referencing a resource already consumed/linked in a way that conflicts
 * with the operation - e.g. an AI proposal already used by another trade setup, or a setup that
 * already has a linked paper order (CLAUDE.md Section 21's Integrations A/C). */
class TradeSetupConflictException(message: String) : RuntimeException(message)

class AiTradeSetupProposalNotFoundException(id: UUID) : RuntimeException("AI trade setup proposal not found: $id")
