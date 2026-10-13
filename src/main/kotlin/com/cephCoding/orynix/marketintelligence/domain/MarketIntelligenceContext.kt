package com.cephCoding.orynix.marketintelligence.domain

import com.cephCoding.orynix.fundamentals.domain.FundamentalAnalysisResult
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.news.domain.NewsResult
import com.cephCoding.orynix.sentiment.domain.SentimentAggregate
import com.cephCoding.orynix.signals.domain.TechnicalSignalSummary
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.time.Instant

/**
 * `asOf` marks when the orchestration request started. It does not claim every section was
 * retrieved at that same instant — each section's own `ContextSection.retrievedAt` is the
 * accurate per-section timestamp, since the six underlying calls run sequentially.
 *
 * `companyInformation`/`instrumentInformation` are mutually exclusive by `assetType`: a STOCK
 * request populates `companyInformation` and leaves `instrumentInformation` as a fixed NO_DATA
 * ("not applicable for stocks"); a FOREX request does the reverse. `news`/`sentiment` are always
 * NO_DATA for FOREX — verified live against Massive that forex news coverage does not exist under
 * the current plan (see CLAUDE.md Section 10's forex extension), so these are never attempted for
 * forex rather than silently returning an indistinguishable "no articles today" empty result.
 */
data class MarketIntelligenceContext(
    val symbol: String,
    val assetType: AssetType,
    val asOf: Instant,
    val marketData: ContextSection<MarketDataSummary>,
    val technicalAnalysis: ContextSection<TechnicalAnalysisResult>,
    val signals: ContextSection<TechnicalSignalSummary>,
    val companyInformation: ContextSection<FundamentalAnalysisResult>,
    val instrumentInformation: ContextSection<ForexInstrumentInfo>,
    val news: ContextSection<NewsResult>,
    val sentiment: ContextSection<SentimentAggregate>,
)
