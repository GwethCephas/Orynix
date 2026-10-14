package com.cephCoding.orynix.agents.service

import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.marketintelligence.domain.ContextSection
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.marketintelligence.domain.MarketDataSummary
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.news.domain.NewsResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class EvidenceContextBuilderTest {

    private val builder = EvidenceContextBuilder()

    private fun candle() = Candle(
        symbol = "AAPL",
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z"),
        open = BigDecimal("100"), high = BigDecimal("101"), low = BigDecimal("99"), close = BigDecimal("100"),
        volume = BigDecimal("1000000"),
    )

    private fun marketDataSummary() = MarketDataSummary(
        symbol = "AAPL", timeframe = CandleTimeframe.DAILY, latestCandle = candle(), candleCount = 365,
        earliestTimestamp = Instant.parse("2025-01-01T00:00:00Z"), latestTimestamp = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun baseContext(
        marketData: ContextSection<MarketDataSummary> = ContextSection(ContextSectionStatus.AVAILABLE, marketDataSummary(), null, Instant.now()),
        news: ContextSection<NewsResult> = ContextSection(ContextSectionStatus.NO_DATA, null, "No news", Instant.now()),
    ) = MarketIntelligenceContext(
        symbol = "AAPL",
        assetType = AssetType.STOCK,
        asOf = Instant.parse("2026-01-01T00:00:00Z"),
        marketData = marketData,
        technicalAnalysis = ContextSection(ContextSectionStatus.NO_DATA, null, "insufficient history", Instant.now()),
        signals = ContextSection(ContextSectionStatus.NO_DATA, null, "insufficient history", Instant.now()),
        companyInformation = ContextSection(ContextSectionStatus.NO_DATA, null, "not applicable", Instant.now()),
        instrumentInformation = ContextSection(ContextSectionStatus.NO_DATA, null, "not applicable", Instant.now()),
        news = news,
        sentiment = ContextSection(ContextSectionStatus.NO_DATA, null, "no sentiment", Instant.now()),
    )

    @Test
    fun `an unavailable section's status and message pass through unchanged, never omitted`() {
        val evidence = builder.build(baseContext())

        assertEquals(ContextSectionStatus.NO_DATA, evidence.technicalAnalysis.status)
        assertEquals("insufficient history", evidence.technicalAnalysis.message)
        assertNull(evidence.technicalAnalysis.data)
    }

    @Test
    fun `an available market data section carries its data through`() {
        val evidence = builder.build(baseContext())

        assertEquals(ContextSectionStatus.AVAILABLE, evidence.marketData.status)
        assertEquals("AAPL", evidence.marketData.data?.symbol)
    }

    @Test
    fun `forex-style unavailable news produces a NO_DATA section with the pinned message, not an omission`() {
        val forexContext = baseContext().copy(assetType = AssetType.FOREX)
        val evidence = builder.build(forexContext)

        assertEquals(ContextSectionStatus.NO_DATA, evidence.news.status)
        assertEquals("No news", evidence.news.message)
        assertNull(evidence.news.data)
    }

    @Test
    fun `news headlines are trimmed to title, source, and publishedAt, capped at 10`() {
        val articles = (1..15).map { i ->
            NewsArticle(
                id = null, providerArticleId = "p$i", symbol = "AAPL", title = "Headline $i",
                description = "full body text $i", source = "Source$i", url = "https://example.com/$i",
                author = "Author$i", publishedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        }
        val newsContext = baseContext(
            news = ContextSection(ContextSectionStatus.AVAILABLE, NewsResult("AAPL", articles), null, Instant.now()),
        )

        val evidence = builder.build(newsContext)

        assertEquals(ContextSectionStatus.AVAILABLE, evidence.news.status)
        assertEquals(10, evidence.news.data?.size)
        assertEquals("Headline 1", evidence.news.data?.first()?.title)
        assertEquals("Source1", evidence.news.data?.first()?.source)
    }
}
