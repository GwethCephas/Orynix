package com.cephCoding.orynix.agents.domain

import com.cephCoding.orynix.fundamentals.domain.FundamentalAnalysisResult
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.marketintelligence.domain.ForexInstrumentInfo
import com.cephCoding.orynix.marketintelligence.domain.MarketDataSummary
import com.cephCoding.orynix.sentiment.domain.SentimentAggregate
import com.cephCoding.orynix.signals.domain.TechnicalSignalSummary
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.time.Instant

/**
 * The bounded, serializable evidence sent to the LLM provider - built once per request from
 * MarketIntelligenceContext, never from RiskCalculatorService output. Deliberately excludes any
 * risk-calculation preview: the model must never be primed by, or asked to reproduce, monetary
 * arithmetic it isn't allowed to perform (CLAUDE.md Section 20) - sizing is only attached after
 * the model proposes price levels, by calling the real deterministic service.
 */
data class EvidenceContext(
    val symbol: String,
    val assetType: AssetType,
    val asOf: Instant,
    val marketData: EvidenceSection<MarketDataSummary>,
    val technicalAnalysis: EvidenceSection<TechnicalAnalysisResult>,
    val signals: EvidenceSection<TechnicalSignalSummary>,
    val companyInformation: EvidenceSection<FundamentalAnalysisResult>,
    val instrumentInformation: EvidenceSection<ForexInstrumentInfo>,
    val news: EvidenceSection<List<NewsHeadline>>,
    val sentiment: EvidenceSection<SentimentAggregate>,
)

/**
 * Status/message are carried through unconditionally for EVERY section, including NO_DATA/ERROR
 * ones - the builder never silently omits an absent section. This is what lets a real gap (e.g.
 * forex's pinned "no news for forex" message) show up as an honest, explicit statement in the
 * prompt instead of the model guessing why evidence is missing.
 */
data class EvidenceSection<T>(
    val status: ContextSectionStatus,
    val data: T?,
    val message: String?,
)

/** Headline-only projection of NewsArticle - full bodies/URLs aren't evidence the model needs. */
data class NewsHeadline(
    val title: String,
    val source: String?,
    val publishedAt: Instant,
)
