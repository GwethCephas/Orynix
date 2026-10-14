package com.cephCoding.orynix.agents.service

import com.cephCoding.orynix.agents.domain.EvidenceContext
import com.cephCoding.orynix.agents.domain.EvidenceSection
import com.cephCoding.orynix.agents.domain.NewsHeadline
import com.cephCoding.orynix.marketintelligence.domain.ContextSection
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.news.domain.NewsResult
import org.springframework.stereotype.Component

/**
 * Pure, synchronous, no I/O - maps the existing MarketIntelligenceContext (CLAUDE.md Section 17)
 * into the bounded EvidenceContext sent to the LLM provider. Every section's status/message is
 * carried through unconditionally, including NO_DATA/ERROR ones (CLAUDE.md Section 20) - a gap is
 * always stated explicitly, never silently dropped.
 */
@Component
class EvidenceContextBuilder {

    fun build(context: MarketIntelligenceContext): EvidenceContext =
        EvidenceContext(
            symbol = context.symbol,
            assetType = context.assetType,
            asOf = context.asOf,
            marketData = toEvidenceSection(context.marketData),
            technicalAnalysis = toEvidenceSection(context.technicalAnalysis),
            signals = toEvidenceSection(context.signals),
            companyInformation = toEvidenceSection(context.companyInformation),
            instrumentInformation = toEvidenceSection(context.instrumentInformation),
            news = toNewsEvidenceSection(context.news),
            sentiment = toEvidenceSection(context.sentiment),
        )

    private fun <T> toEvidenceSection(section: ContextSection<T>): EvidenceSection<T> =
        EvidenceSection(status = section.status, data = section.data, message = section.message)

    /**
     * Trims NewsResult.articles to headline-only fields, capped at MAX_NEWS_HEADLINES, to bound
     * prompt size - full article bodies/URLs aren't evidence the model needs for an assessment or
     * price levels.
     */
    private fun toNewsEvidenceSection(section: ContextSection<NewsResult>): EvidenceSection<List<NewsHeadline>> {
        val headlines = section.data
            ?.articles
            ?.take(MAX_NEWS_HEADLINES)
            ?.map { NewsHeadline(title = it.title, source = it.source, publishedAt = it.publishedAt) }
        return EvidenceSection(status = section.status, data = headlines, message = section.message)
    }

    companion object {
        private const val MAX_NEWS_HEADLINES = 10
    }
}
