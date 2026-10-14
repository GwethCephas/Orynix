package com.cephCoding.orynix.agents.provider

import com.cephCoding.orynix.agents.domain.EvidenceContext
import com.cephCoding.orynix.agents.domain.ModelAnalysis

/**
 * One method, evidence in, a structured result out - never a raw chat string. Deliberately a
 * single-method interface: this is the first AI agent in Orynix, not a multi-provider plugin
 * system, so the abstraction is kept to exactly what MarketAnalysisService needs.
 */
interface MarketAnalysisProvider {
    fun analyze(context: EvidenceContext): ModelAnalysis
}
