package com.cephCoding.orynix.tradesetup.domain

/**
 * AI_ANALYSIS provenance (aiProviderName/aiModel/aiAnalysisAsOf) is client-asserted and
 * server-unverifiable - the AI Market Analysis Agent persists nothing and has no stable analysis
 * id to check against (CLAUDE.md Section 21). This is a disclosed limitation, not a gap to fix
 * silently.
 */
enum class TradeSetupSource { MANUAL, AI_ANALYSIS }
