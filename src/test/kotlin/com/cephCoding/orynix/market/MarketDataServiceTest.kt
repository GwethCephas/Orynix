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
}
