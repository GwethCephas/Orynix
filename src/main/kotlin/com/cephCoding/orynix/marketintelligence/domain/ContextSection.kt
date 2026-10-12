package com.cephCoding.orynix.marketintelligence.domain

import java.time.Instant

/**
 * `retrievedAt` is when the orchestrator fetched this section, not a provider-supplied timestamp —
 * some underlying domain types (fundamentals, sentiment aggregate, news) carry no timestamp of
 * their own, so this is the only provenance anchor available for them. Sections with their own
 * embedded timestamp (candles, technical analysis, signals) keep it unchanged inside `data`.
 */
data class ContextSection<T>(
    val status: ContextSectionStatus,
    val data: T?,
    val message: String?,
    val retrievedAt: Instant,
)
