package com.cephCoding.orynix.agents.domain

/**
 * Deliberately has no default value anywhere and is never constructed by the orchestrator - only
 * by whichever MarketAnalysisProvider implementation produced a ModelAnalysis. This makes "never
 * claim an LLM-free fallback came from a real model call" a compile-time guarantee rather than a
 * convention someone could forget: GeminiMarketAnalysisProvider always sets source = MODEL; only a
 * test-only fake may ever set DETERMINISTIC_STUB.
 */
enum class AnalysisSource { MODEL, DETERMINISTIC_STUB }

data class AnalysisProvenance(
    val source: AnalysisSource,
    val providerName: String,
    val model: String,
)
