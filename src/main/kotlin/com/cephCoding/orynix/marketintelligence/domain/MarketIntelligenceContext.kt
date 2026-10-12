package com.cephCoding.orynix.marketintelligence.domain

import com.cephCoding.orynix.fundamentals.domain.FundamentalAnalysisResult
import com.cephCoding.orynix.news.domain.NewsResult
import com.cephCoding.orynix.sentiment.domain.SentimentAggregate
import com.cephCoding.orynix.signals.domain.TechnicalSignalSummary
import com.cephCoding.orynix.technicalanalysis.domain.TechnicalAnalysisResult
import java.time.Instant

/**
 * `asOf` marks when the orchestration request started. It does not claim every section was
 * retrieved at that same instant — each section's own `ContextSection.retrievedAt` is the
 * accurate per-section timestamp, since the six underlying calls run sequentially.
 */
data class MarketIntelligenceContext(
    val symbol: String,
    val asOf: Instant,
    val marketData: ContextSection<MarketDataSummary>,
    val technicalAnalysis: ContextSection<TechnicalAnalysisResult>,
    val signals: ContextSection<TechnicalSignalSummary>,
    val companyInformation: ContextSection<FundamentalAnalysisResult>,
    val news: ContextSection<NewsResult>,
    val sentiment: ContextSection<SentimentAggregate>,
)
