package com.cephCoding.orynix.marketintelligence.service

import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.common.exception.NewsDataProviderException
import com.cephCoding.orynix.common.exception.SentimentAnalyzerException
import com.cephCoding.orynix.fundamentals.domain.FundamentalAnalysisResult
import com.cephCoding.orynix.fundamentals.service.FundamentalAnalysisService
import com.cephCoding.orynix.instrument.ForexPairCatalog
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.service.MarketDataService
import com.cephCoding.orynix.marketintelligence.domain.ContextSection
import com.cephCoding.orynix.marketintelligence.domain.ContextSectionStatus
import com.cephCoding.orynix.marketintelligence.domain.ForexInstrumentInfo
import com.cephCoding.orynix.marketintelligence.domain.MarketDataSummary
import com.cephCoding.orynix.marketintelligence.domain.MarketIntelligenceContext
import com.cephCoding.orynix.news.domain.NewsResult
import com.cephCoding.orynix.news.service.NewsService
import com.cephCoding.orynix.sentiment.domain.SentimentAggregate
import com.cephCoding.orynix.sentiment.service.SentimentService
import com.cephCoding.orynix.signals.service.SignalAnalysisService
import com.cephCoding.orynix.technicalanalysis.service.TechnicalAnalysisService
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Deliberately not @Transactional: none of the six underlying services are transactional, and
 * wrapping this orchestration in one shared transaction would mean a caught per-section exception
 * marks it rollback-only, so a later section's otherwise-successful commit would throw
 * UnexpectedRollbackException — turning one degraded section into a 500 for the whole response
 * and defeating the partial-failure design below.
 */
@Service
class MarketIntelligenceService(
    private val marketDataService: MarketDataService,
    private val technicalAnalysisService: TechnicalAnalysisService,
    private val signalAnalysisService: SignalAnalysisService,
    private val fundamentalAnalysisService: FundamentalAnalysisService,
    private val newsService: NewsService,
    private val sentimentService: SentimentService,
) {

    fun getContext(symbolRaw: String): MarketIntelligenceContext {
        val instrument = InstrumentResolver.resolve(symbolRaw)
        val symbol = instrument.displaySymbol
        val asOf = Instant.now()

        val marketData = runSection(MSG_NO_MARKET_DATA) {
            val candles = marketDataService.getDailyCandles(symbol, TIMEFRAME_CODE)
            val latest = candles.maxByOrNull { it.timestamp }
            latest?.let {
                MarketDataSummary(
                    symbol = symbol,
                    timeframe = CandleTimeframe.DAILY,
                    latestCandle = it,
                    candleCount = candles.size,
                    earliestTimestamp = candles.minOf { candle -> candle.timestamp },
                    latestTimestamp = candles.maxOf { candle -> candle.timestamp },
                )
            }
        }

        val technicalAnalysis = runSection {
            technicalAnalysisService.getAnalysis(symbol, TIMEFRAME_CODE)
        }

        val signals = runSection {
            signalAnalysisService.getSignals(symbol, TIMEFRAME_CODE)
        }

        val companyInformation = companyInformationSection(instrument)
        val instrumentInformation = instrumentInformationSection(instrument)
        val news = newsSection(instrument)
        val sentiment = sentimentSection(instrument)

        return MarketIntelligenceContext(
            symbol = symbol,
            assetType = instrument.assetType,
            asOf = asOf,
            marketData = marketData,
            technicalAnalysis = technicalAnalysis,
            signals = signals,
            companyInformation = companyInformation,
            instrumentInformation = instrumentInformation,
            news = news,
            sentiment = sentiment,
        )
    }

    /**
     * STOCK-only, exactly like the standalone `/api/v1/fundamentals` endpoint — never called for
     * FOREX (see CLAUDE.md Section 9: `FundamentalAnalysisService` must never be called with a
     * forex symbol).
     */
    private fun companyInformationSection(instrument: Instrument): ContextSection<FundamentalAnalysisResult> =
        if (instrument.assetType != AssetType.STOCK) {
            fixedNoData(MSG_COMPANY_INFO_NOT_APPLICABLE)
        } else {
            runSection {
                fundamentalAnalysisService.getFundamentals(instrument.displaySymbol)
            }
        }

    /** The forex mirror of [companyInformationSection] — always NO_DATA for STOCK. */
    private fun instrumentInformationSection(instrument: Instrument): ContextSection<ForexInstrumentInfo> =
        if (instrument.assetType != AssetType.FOREX) {
            fixedNoData(MSG_INSTRUMENT_INFO_NOT_APPLICABLE)
        } else {
            ContextSection(
                ContextSectionStatus.AVAILABLE,
                ForexInstrumentInfo(
                    displaySymbol = instrument.displaySymbol,
                    baseCurrency = instrument.baseCurrency!!,
                    quoteCurrency = instrument.quoteCurrency!!,
                    description = ForexPairCatalog.describe(instrument),
                ),
                null,
                Instant.now(),
            )
        }

    /**
     * Verified live against Massive (see CLAUDE.md Section 10's forex extension) that forex news
     * coverage does not exist under the current plan — every forex ticker format tried returned
     * an empty result while stock tickers returned real articles. FOREX therefore skips the
     * provider call entirely rather than spending it on a request known in advance to be empty.
     */
    private fun newsSection(instrument: Instrument): ContextSection<NewsResult> =
        if (instrument.assetType == AssetType.FOREX) {
            fixedNoData(MSG_NEWS_NOT_SUPPORTED_FOREX)
        } else {
            runSection(MSG_NO_NEWS) {
                newsService.getNews(instrument.displaySymbol, null).takeIf { it.articles.isNotEmpty() }
            }
        }

    /** Sentiment has no articles to analyze for FOREX, for the same reason as [newsSection]. */
    private fun sentimentSection(instrument: Instrument): ContextSection<SentimentAggregate> =
        if (instrument.assetType == AssetType.FOREX) {
            fixedNoData(MSG_SENTIMENT_NOT_SUPPORTED_FOREX)
        } else {
            runSection(MSG_NO_SENTIMENT) {
                sentimentService.getAggregate(instrument.displaySymbol, null).takeIf { it.articleCount > 0 }
            }
        }

    private fun <T> fixedNoData(message: String): ContextSection<T> =
        ContextSection(ContextSectionStatus.NO_DATA, null, message, Instant.now())

    /**
     * Runs one section's fetch in isolation: a null result from [fetch] means "looked it up
     * successfully, nothing there" (NO_DATA, using [emptyMessage]); specific known exception types
     * are caught and downgraded to NO_DATA or ERROR with a fixed message — never the raw
     * `ex.message`, so a future change to an exception's wording can never leak anything new into
     * a 200 response body. `InvalidSymbolException`/`InvalidTimeframeException`/
     * `InvalidNewsLimitException` are deliberately NOT caught here: they mean the request itself is
     * malformed, not that one section's data is unavailable, so they propagate to
     * `GlobalExceptionHandler`'s existing 400 mapping and fail the whole request. Any other,
     * unexpected exception also propagates uncaught, to the existing 500 catch-all.
     */
    private fun <T> runSection(emptyMessage: String? = null, fetch: () -> T?): ContextSection<T> {
        val retrievedAt = Instant.now()
        return try {
            when (val result = fetch()) {
                null -> ContextSection(ContextSectionStatus.NO_DATA, null, emptyMessage, retrievedAt)
                else -> ContextSection(ContextSectionStatus.AVAILABLE, result, null, retrievedAt)
            }
        } catch (ex: InsufficientCandleDataException) {
            ContextSection(ContextSectionStatus.NO_DATA, null, MSG_INSUFFICIENT_HISTORY, retrievedAt)
        } catch (ex: CompanyNotFoundException) {
            ContextSection(ContextSectionStatus.NO_DATA, null, MSG_COMPANY_NOT_FOUND, retrievedAt)
        } catch (ex: MarketDataProviderException) {
            ContextSection(ContextSectionStatus.ERROR, null, MSG_MARKET_DATA_PROVIDER_ERROR, retrievedAt)
        } catch (ex: FundamentalDataProviderException) {
            ContextSection(ContextSectionStatus.ERROR, null, MSG_FUNDAMENTAL_PROVIDER_ERROR, retrievedAt)
        } catch (ex: NewsDataProviderException) {
            ContextSection(ContextSectionStatus.ERROR, null, MSG_NEWS_PROVIDER_ERROR, retrievedAt)
        } catch (ex: SentimentAnalyzerException) {
            ContextSection(ContextSectionStatus.ERROR, null, MSG_SENTIMENT_ANALYZER_ERROR, retrievedAt)
        }
    }

    companion object {
        private const val TIMEFRAME_CODE = "1d"

        private const val MSG_NO_MARKET_DATA = "No historical price data available for this symbol"
        private const val MSG_INSUFFICIENT_HISTORY = "Not enough historical price data available for this symbol"
        private const val MSG_COMPANY_NOT_FOUND = "No company information found for this symbol"
        private const val MSG_NO_NEWS = "No recent news articles found for this symbol"
        private const val MSG_NO_SENTIMENT = "No analyzed sentiment available for this symbol"
        private const val MSG_MARKET_DATA_PROVIDER_ERROR = "Market data provider request failed"
        private const val MSG_FUNDAMENTAL_PROVIDER_ERROR = "Fundamental data provider request failed"
        private const val MSG_NEWS_PROVIDER_ERROR = "News data provider request failed"
        private const val MSG_SENTIMENT_ANALYZER_ERROR = "Sentiment analyzer request failed"
        private const val MSG_COMPANY_INFO_NOT_APPLICABLE =
            "Company information is not applicable for this instrument"
        private const val MSG_INSTRUMENT_INFO_NOT_APPLICABLE =
            "Instrument information is only applicable to forex pairs"
        private const val MSG_NEWS_NOT_SUPPORTED_FOREX =
            "News coverage is not available for forex pairs under the current data provider integration"
        private const val MSG_SENTIMENT_NOT_SUPPORTED_FOREX =
            "Sentiment analysis is not available for forex pairs under the current data provider integration"
    }
}
