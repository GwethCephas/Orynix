package com.cephCoding.orynix.market

import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.repository.MarketDataCandleRepository
import com.cephCoding.orynix.market.service.MarketDataService
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MarketDataServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var repository: MarketDataCandleRepository

    @Test
    fun `fetching the same candles twice does not create duplicate rows`() {
        val candle = Candle(
            symbol = "DEDUP",
            timeframe = CandleTimeframe.DAILY,
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            open = BigDecimal("100.00"),
            high = BigDecimal("101.00"),
            low = BigDecimal("99.00"),
            close = BigDecimal("100.50"),
            volume = BigDecimal("1000"),
        )
        fakeMarketDataProvider.candlesToReturn = listOf(candle)

        val first = marketDataService.getDailyCandles("dedup", "1d")
        val countAfterFirst = repository.count()

        val second = marketDataService.getDailyCandles("dedup", "1d")
        val countAfterSecond = repository.count()

        assert(first.size == 1)
        assert(second.size == 1)
        assert(countAfterFirst == countAfterSecond)
    }

    @Test
    fun `concurrent first-time fetches of a brand-new symbol do not throw or duplicate rows`() {
        val candles = (1..30).map { day ->
            Candle(
                symbol = "RACE",
                timeframe = CandleTimeframe.DAILY,
                timestamp = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(day * 86400L),
                open = BigDecimal("100.00"),
                high = BigDecimal("101.00"),
                low = BigDecimal("99.00"),
                close = BigDecimal("100.50"),
                volume = BigDecimal("1000"),
            )
        }
        fakeMarketDataProvider.candlesToReturn = candles

        val concurrency = 8
        val executor = Executors.newFixedThreadPool(concurrency)
        val readyLatch = CountDownLatch(concurrency)
        val startLatch = CountDownLatch(1)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        val futures = (1..concurrency).map {
            executor.submit {
                readyLatch.countDown()
                startLatch.await()
                try {
                    marketDataService.getDailyCandles("race", "1d")
                } catch (ex: Throwable) {
                    errors.add(ex)
                }
            }
        }
        readyLatch.await()
        startLatch.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assert(errors.isEmpty()) { "Concurrent getDailyCandles calls threw: $errors" }
        assert(repository.findBySymbolAndTimeframeAndTimestampIn("RACE", "1d", candles.map { it.timestamp }).size == candles.size)
    }

    @Test
    fun `a forex display symbol is normalized back onto returned and persisted candles`() {
        // The fake provider echoes candlesToReturn verbatim regardless of the providerSymbol it
        // was called with (it has no real ticker-string sensitivity) — tagging the fixture with
        // Massive's provider-ticker format directly proves MarketDataService, not the provider,
        // is what normalizes the symbol back to the display form "EUR/USD" before returning or
        // persisting it.
        val candle = Candle(
            symbol = "C:EURUSD",
            timeframe = CandleTimeframe.DAILY,
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            open = BigDecimal("1.1000"),
            high = BigDecimal("1.1050"),
            low = BigDecimal("1.0950"),
            close = BigDecimal("1.1020"),
            volume = BigDecimal("1000"),
        )
        fakeMarketDataProvider.candlesToReturn = listOf(candle)

        val result = marketDataService.getDailyCandles("eur/usd", "1d")

        assert(result.size == 1)
        assert(result[0].symbol == "EUR/USD")

        val persisted = repository.findBySymbolAndTimeframeAndTimestampIn("EUR/USD", "1d", listOf(candle.timestamp))
        assert(persisted.size == 1)
    }
}
