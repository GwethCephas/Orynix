package com.cephCoding.orynix.support

import com.cephCoding.orynix.agents.domain.AnalysisProvenance
import com.cephCoding.orynix.agents.domain.AnalysisSource
import com.cephCoding.orynix.agents.domain.EvidenceContext
import com.cephCoding.orynix.agents.domain.ModelAnalysis
import com.cephCoding.orynix.agents.provider.MarketAnalysisProvider

/**
 * Test-only - never ships in production source (CLAUDE.md Section 20: a production-tree fallback
 * would be indistinguishable at runtime from a real-looking degraded mode, which is exactly what
 * the "never claim an LLM-free fallback came from a real model call" constraint forbids). The
 * default response (when `nextResponse` isn't set) is tagged DETERMINISTIC_STUB, the one value
 * only test code is ever allowed to produce.
 */
class FakeMarketAnalysisProvider : MarketAnalysisProvider {
    var nextResponse: ModelAnalysis? = null
    var failure: RuntimeException? = null
    var lastContext: EvidenceContext? = null

    override fun analyze(context: EvidenceContext): ModelAnalysis {
        lastContext = context
        failure?.let { throw it }
        return nextResponse ?: ModelAnalysis(
            provenance = AnalysisProvenance(source = AnalysisSource.DETERMINISTIC_STUB, providerName = "fake", model = "fake-v1"),
            marketAssessment = "Deterministic stub assessment for ${context.symbol}",
            scenarios = emptyList(),
            tradeSetups = emptyList(),
        )
    }

    fun reset() {
        nextResponse = null
        failure = null
        lastContext = null
    }
}
