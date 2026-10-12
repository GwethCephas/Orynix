package com.cephCoding.orynix.marketintelligence

import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.common.exception.NewsDataProviderException
import com.cephCoding.orynix.common.exception.SentimentAnalyzerException
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.marketintelligence.service.MarketIntelligenceService
import com.cephCoding.orynix.news.domain.NewsArticle
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.sin

class MarketIntelligenceServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var marketIntelligenceService: MarketIntelligenceService

    private fun candleFixture(
        symbol: String,
        count: Int,
        closeAt: (Int) -> Double = { i -> 100.0 + i * 0.3 + 2.0 * sin(i * 0.5) },
    ): List<Candle> {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        return (0 until count).map { i ->
            val close = closeAt(i)
            Candle(
                symbol = symbol,
                timeframe = CandleTimeframe.DAILY,
                timestamp = base.plus(i.toLong(), ChronoUnit.DAYS),
                open = BigDecimal.valueOf(close - 0.25),
                high = BigDecimal.valueOf(close + 1.0),
                low = BigDecimal.valueOf(close - 1.0),
                close = BigDecimal.valueOf(close),
                volume = BigDecimal.valueOf(1_000_000.0 + i * 1_000.0),
            )
        }
    }

    private fun defaultCompanyProfile() = CompanyProfile(
        name = "Example Corp",
        exchange = "XNAS",
        industry = "SOFTWARE",
        marketCapitalization = BigDecimal("1000000000"),
    )

    private fun newsArticleFixture(symbol: String, count: Int): List<NewsArticle> =
        (0 until count).map { i ->
            NewsArticle(
                id = null,
                providerArticleId = "article-$symbol-$i",
                symbol = symbol,
                title = "Headline $i for $symbol",
                description = "Description $i",
                source = "Test Wire",
                url = "https://example.com/$symbol/$i",
                author = "Reporter $i",
                publishedAt = Instant.parse("2026-01-01T00:00:00Z").plus(i.toLong(), ChronoUnit.HOURS),
            )
        }

    @Test
    fun `all six sections available for a well-covered symbol`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MIFULL", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()
        fakeNewsProvider.articlesToReturn = newsArticleFixture("MIFULL", 3)

        val context = marketIntelligenceService.getContext("mifull")

        assertEquals("MIFULL", context.symbol)
        assertEquals(ContextSectionStatus.AVAILABLE, context.marketData.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.technicalAnalysis.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.signals.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.companyInformation.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.news.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.sentiment.status)

        assertEquals(60, context.marketData.data?.candleCount)
        assertEquals("Example Corp", context.companyInformation.data?.company?.name)
        assertEquals(3, context.news.data?.articles?.size)
        assertTrue(context.sentiment.data!!.articleCount > 0)
    }

    @Test
    fun `invalid symbol fails the whole request instead of becoming a section failure`() {
        assertThrows(InvalidSymbolException::class.java) {
            marketIntelligenceService.getContext("this-is-not-a-valid-ticker-123")
        }
    }

    @Test
    fun `empty candle list degrades market data to NO_DATA instead of crashing`() {
        fakeMarketDataProvider.candlesToReturn = emptyList()
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()

        val context = marketIntelligenceService.getContext("MIEMPTY")

        assertEquals(ContextSectionStatus.NO_DATA, context.marketData.status)
        assertNull(context.marketData.data)
        assertNotNull(context.marketData.message)
    }

    @Test
    fun `exactly 50 candles splits technical analysis available from signals no_data`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MIFIFTY", 50)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()

        val context = marketIntelligenceService.getContext("MIFIFTY")

        assertEquals(ContextSectionStatus.AVAILABLE, context.technicalAnalysis.status)
        assertEquals(ContextSectionStatus.NO_DATA, context.signals.status)
        assertNull(context.signals.data)
    }

    @Test
    fun `company not found degrades to NO_DATA`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MINOCOMP", 60)
        fakeFundamentalDataProvider.failure = CompanyNotFoundException("MINOCOMP")

        val context = marketIntelligenceService.getContext("MINOCOMP")

        assertEquals(ContextSectionStatus.NO_DATA, context.companyInformation.status)
        assertNull(context.companyInformation.data)
    }

    @Test
    fun `empty news and zero-article sentiment are NO_DATA without any exception involved`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MINONEWS", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()
        fakeNewsProvider.articlesToReturn = emptyList()

        val context = marketIntelligenceService.getContext("MINONEWS")

        assertEquals(ContextSectionStatus.NO_DATA, context.news.status)
        assertEquals(ContextSectionStatus.NO_DATA, context.sentiment.status)
    }

    @Test
    fun `market data provider failure degrades only the market data section`() {
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()

        val context = marketIntelligenceService.getContext("MIMKTFAIL")

        assertEquals(ContextSectionStatus.ERROR, context.marketData.status)
        assertEquals(ContextSectionStatus.ERROR, context.technicalAnalysis.status)
        assertEquals(ContextSectionStatus.ERROR, context.signals.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.companyInformation.status)
    }

    @Test
    fun `fundamental provider failure degrades only company information`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MIFUNDFAIL", 60)
        fakeFundamentalDataProvider.failure = FundamentalDataProviderException("Fundamental data provider request failed")

        val context = marketIntelligenceService.getContext("MIFUNDFAIL")

        assertEquals(ContextSectionStatus.ERROR, context.companyInformation.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.technicalAnalysis.status)
    }

    @Test
    fun `news provider failure degrades news and sentiment`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MINEWSFAIL", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()
        fakeNewsProvider.failure = NewsDataProviderException("News data provider request failed")

        val context = marketIntelligenceService.getContext("MINEWSFAIL")

        assertEquals(ContextSectionStatus.ERROR, context.news.status)
        assertEquals(ContextSectionStatus.ERROR, context.sentiment.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.companyInformation.status)
    }

    @Test
    fun `sentiment analyzer failure degrades only sentiment, not news`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MISENTFAIL", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()
        fakeNewsProvider.articlesToReturn = newsArticleFixture("MISENTFAIL", 2)
        fakeSentimentAnalyzer.failure = SentimentAnalyzerException("Sentiment analyzer request failed")

        val context = marketIntelligenceService.getContext("MISENTFAIL")

        assertEquals(ContextSectionStatus.ERROR, context.sentiment.status)
        assertEquals(ContextSectionStatus.AVAILABLE, context.news.status)
    }

    @Test
    fun `all six sections can be simultaneously degraded without the request failing`() {
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")
        fakeFundamentalDataProvider.failure = FundamentalDataProviderException("Fundamental data provider request failed")
        fakeNewsProvider.failure = NewsDataProviderException("News data provider request failed")

        val context = marketIntelligenceService.getContext("MIALLFAIL")

        assertEquals(ContextSectionStatus.ERROR, context.marketData.status)
        assertEquals(ContextSectionStatus.ERROR, context.technicalAnalysis.status)
        assertEquals(ContextSectionStatus.ERROR, context.signals.status)
        assertEquals(ContextSectionStatus.ERROR, context.companyInformation.status)
        assertEquals(ContextSectionStatus.ERROR, context.news.status)
        assertEquals(ContextSectionStatus.ERROR, context.sentiment.status)
    }

    @Test
    fun `provenance timestamps are captured per section and embedded timestamps are preserved`() {
        fakeMarketDataProvider.candlesToReturn = candleFixture("MIPROV", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()

        val context = marketIntelligenceService.getContext("MIPROV")

        assertNotNull(context.marketData.retrievedAt)
        assertNotNull(context.technicalAnalysis.retrievedAt)
        assertNotNull(context.signals.retrievedAt)
        assertNotNull(context.companyInformation.retrievedAt)
        assertNotNull(context.news.retrievedAt)
        assertNotNull(context.sentiment.retrievedAt)

        // Candle/TA/signals already carry their own embedded timestamp from the underlying
        // service — confirm it passes through unchanged rather than being overwritten.
        assertEquals(context.marketData.data?.latestCandle?.timestamp, context.technicalAnalysis.data?.timestamp)
    }

    @Test
    fun `calling the news provider twice for a sparse symbol is expected, not a bug`() {
        // NewsService only skips re-fetching once a symbol has 20+ persisted articles (its
        // default limit). For any symbol below that, the orchestrator's own direct `news` section
        // call AND SentimentService.getAggregate's internal NewsService.getNews call both land on
        // the provider — two real calls, not one cached + one free DB hit. This test pins that
        // documented behavior so it doesn't get "fixed" into a silent correctness regression later.
        fakeMarketDataProvider.candlesToReturn = candleFixture("MIDBLFETCH", 60)
        fakeFundamentalDataProvider.companyProfileToReturn = defaultCompanyProfile()
        fakeNewsProvider.articlesToReturn = newsArticleFixture("MIDBLFETCH", 2)

        marketIntelligenceService.getContext("MIDBLFETCH")

        assertEquals(2, fakeNewsProvider.callCount)
    }

    @Test
    fun `error section messages never contain the raw underlying exception text`() {
        fakeFundamentalDataProvider.failure = FundamentalDataProviderException(
            "upstream said: secret-internal-detail-12345",
        )
        fakeMarketDataProvider.candlesToReturn = candleFixture("MILEAKCHK", 60)

        val context = marketIntelligenceService.getContext("MILEAKCHK")

        assertEquals(ContextSectionStatus.ERROR, context.companyInformation.status)
        assertNotNull(context.companyInformation.message)
        assertTrue(context.companyInformation.message?.contains("secret-internal-detail-12345") == false)
    }
}
