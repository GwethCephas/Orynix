package com.cephCoding.orynix.marketintelligence.dto

import com.cephCoding.orynix.fundamentals.dto.FundamentalAnalysisResponse
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.news.dto.NewsResponse
import com.cephCoding.orynix.sentiment.dto.SentimentAggregateResponse
import com.cephCoding.orynix.signals.dto.TechnicalSignalSummaryResponse
import com.cephCoding.orynix.technicalanalysis.dto.TechnicalAnalysisResponse
import java.time.Instant

data class MarketIntelligenceContextResponse(
    val symbol: String,
    val asOf: Instant,
    val marketData: ContextSectionResponse<MarketDataSummaryResponse>,
    val technicalAnalysis: ContextSectionResponse<TechnicalAnalysisResponse>,
    val signals: ContextSectionResponse<TechnicalSignalSummaryResponse>,
    val companyInformation: ContextSectionResponse<FundamentalAnalysisResponse>,
    val news: ContextSectionResponse<NewsResponse>,
    val sentiment: ContextSectionResponse<SentimentAggregateResponse>,
) {
    companion object {
        fun from(context: MarketIntelligenceContext) = MarketIntelligenceContextResponse(
            symbol = context.symbol,
            asOf = context.asOf,
            marketData = ContextSectionResponse.from(context.marketData, MarketDataSummaryResponse::from),
            technicalAnalysis = ContextSectionResponse.from(context.technicalAnalysis, TechnicalAnalysisResponse::from),
            signals = ContextSectionResponse.from(context.signals, TechnicalSignalSummaryResponse::from),
            companyInformation = ContextSectionResponse.from(context.companyInformation, FundamentalAnalysisResponse::from),
            news = ContextSectionResponse.from(context.news, NewsResponse::from),
            sentiment = ContextSectionResponse.from(context.sentiment, SentimentAggregateResponse::from),
        )
    }
}
