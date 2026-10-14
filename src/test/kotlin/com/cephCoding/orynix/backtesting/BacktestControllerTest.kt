package com.cephCoding.orynix.backtesting

import com.cephCoding.orynix.backtesting.domain.PositionSizingType
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.dto.BacktestDetailResponse
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.dto.BacktestResponse
import com.cephCoding.orynix.backtesting.dto.BacktestSummaryResponse
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class BacktestControllerTest : AbstractIntegrationTest() {

    private fun candle(symbol: String, day: Int, open: Double, close: Double): Candle = Candle(
        symbol = symbol,
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(day.toLong(), ChronoUnit.DAYS),
        open = BigDecimal.valueOf(open),
        high = BigDecimal.valueOf(maxOf(open, close) + 1.0),
        low = BigDecimal.valueOf(minOf(open, close) - 1.0),
        close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000_000.0),
    )

    // Hand-computed bullish-then-bearish SMA(2)/SMA(3) crossover, identical to the one verified
    // in MovingAverageCrossoverStrategyTest: entry decided at index 3, exit decided at index 4,
    // filled at index 5's open. Padded with two trailing flat bars so index 4 is not the final
    // candle, letting the SIGNAL exit (not an END_OF_PERIOD force-close) resolve the trade.
    private fun crossoverCandles(symbol: String): List<Candle> {
        val closes = listOf(100.0, 100.0, 100.0, 130.0, 60.0, 90.0, 90.0)
        return closes.mapIndexed { i, close -> candle(symbol, i, open = close - 1.0, close = close) }
    }

    private fun flatCandles(symbol: String, count: Int): List<Candle> =
        (0 until count).map { candle(symbol, it, open = 100.0, close = 100.0) }

    private fun movingAverageCrossoverRequest(symbol: String = "BTEST") = BacktestRequest(
        symbol = symbol,
        fromDate = LocalDate.of(2026, 1, 4),
        toDate = LocalDate.of(2026, 1, 10),
        strategyType = StrategyType.MOVING_AVERAGE_CROSSOVER,
        fastPeriod = 2,
        slowPeriod = 3,
        rsiPeriod = null,
        oversoldThreshold = null,
        overboughtThreshold = null,
        signalPeriod = null,
        bollingerPeriod = null,
        bollingerStdDevMultiplier = null,
        donchianEntryPeriod = null,
        donchianExitPeriod = null,
        adxPeriod = null,
        adxThreshold = null,
        initialCapital = BigDecimal("1000"),
        accountCurrency = "USD",
        positionSizingType = PositionSizingType.FIXED_QUANTITY,
        quantity = BigDecimal("1"),
        riskPercentPerTrade = null,
        commissionPerOrder = BigDecimal("0"),
        slippageBps = 0,
        atrPeriod = null,
        atrInitialStopMultiplier = null,
        atrTrailingStopMultiplier = null,
    )

    // Hand-verified by running MacdCalculator's own EMA algorithm (fast 2, slow 3, signal 2) over
    // this series, identical to MacdCrossoverStrategyTest: a bullish crossover decided at index 4
    // (fills at index 5's open), a bearish crossover decided at index 6 (fills at index 7's open).
    private fun macdCandles(symbol: String): List<Candle> {
        val closes = listOf(10.0, 10.0, 10.0, 10.0, 100.0, 90.0, 10.0, 10.0)
        return closes.mapIndexed { i, close -> candle(symbol, i, open = close - 1.0, close = close) }
    }

    // Hand-verified by running BollingerBandsCalculator's own algorithm (period 5, multiplier 1.0)
    // over this series, identical to BollingerBandsMeanReversionStrategyTest: a breakdown below
    // the lower band decided at index 5 (fills at index 6's open), a reversion above the middle
    // band decided at index 7 (fills at index 8's open).
    private fun bollingerCandles(symbol: String): List<Candle> {
        val closes = listOf(100.0, 102.0, 98.0, 101.0, 99.0, 85.0, 90.0, 101.0, 100.0, 100.0)
        return closes.mapIndexed { i, close -> candle(symbol, i, open = close - 1.0, close = close) }
    }

    private fun macdCrossoverRequest(symbol: String = "BTEST") = movingAverageCrossoverRequest(symbol).copy(
        fromDate = LocalDate.of(2026, 1, 5),
        strategyType = StrategyType.MACD_CROSSOVER,
        fastPeriod = 2,
        slowPeriod = 3,
        signalPeriod = 2,
    )

    private fun bollingerBandsRequest(symbol: String = "BTEST") = movingAverageCrossoverRequest(symbol).copy(
        fromDate = LocalDate.of(2026, 1, 6),
        toDate = LocalDate.of(2026, 1, 12),
        strategyType = StrategyType.BOLLINGER_BANDS,
        fastPeriod = null,
        slowPeriod = null,
        bollingerPeriod = 5,
        bollingerStdDevMultiplier = BigDecimal("1.0"),
    )

    @Test
    fun `a MACD crossover scenario produces the hand-computed trade`() {
        val auth = register("backtest-macd@example.com")
        fakeMarketDataProvider.candlesToReturn = macdCandles("BTEST")

        val response = postBacktest(auth.accessToken, macdCrossoverRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals(1, body.totalTrades)
        assertEquals(0, body.winningTrades)
        assertEquals(1, body.losingTrades)
        assertEquals(BigDecimal("-80.0000"), body.netPnl)
        assertEquals(BigDecimal("920.0000"), body.finalEquity)
    }

    @Test
    fun `a Bollinger Bands mean-reversion scenario produces the hand-computed trade`() {
        val auth = register("backtest-bollinger@example.com")
        fakeMarketDataProvider.candlesToReturn = bollingerCandles("BTEST")

        val response = postBacktest(auth.accessToken, bollingerBandsRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals(1, body.totalTrades)
        assertEquals(1, body.winningTrades)
        assertEquals(0, body.losingTrades)
        assertEquals(BigDecimal("10.0000"), body.netPnl)
        assertEquals(BigDecimal("1010.0000"), body.finalEquity)
    }

    @Test
    fun `MACD parameters are rejected with 400 when the request is actually RSI_THRESHOLD`() {
        val auth = register("backtest-macdcrossparams@example.com")

        val request = movingAverageCrossoverRequest().copy(strategyType = StrategyType.RSI_THRESHOLD, signalPeriod = 2, rsiPeriod = null)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `Bollinger Bands parameters are rejected with 400 when fastPeriod is also supplied`() {
        val auth = register("backtest-bollingercrossparams@example.com")

        val request = bollingerBandsRequest().copy(fastPeriod = 10)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an invalid Bollinger standard deviation multiplier is rejected with 400`() {
        val auth = register("backtest-bollingerinvalid@example.com")

        val request = bollingerBandsRequest().copy(bollingerStdDevMultiplier = BigDecimal("0"))
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    // Hand-verified by running DonchianCalculator's own algorithm (entry lookback 3, exit
    // lookback 2) over this series, identical to DonchianChannelBreakoutStrategyTest: a breakout
    // above the entry channel decided at index 4 (fills at index 5's open), a breakdown below the
    // exit channel decided at index 6 (fills at index 7's open). Candles use explicit high/low
    // (not the open/close-derived +-1 of the candle() helper) since Donchian genuinely needs a
    // distinct high/low series; two trailing flat bars let the SIGNAL exit resolve instead of an
    // END_OF_PERIOD force-close.
    private fun donchianCandles(symbol: String): List<Candle> {
        val highs = listOf(100.0, 101.0, 99.0, 102.0, 125.0, 118.0, 110.0, 110.0, 110.0)
        val lows = listOf(98.0, 99.0, 97.0, 100.0, 118.0, 112.0, 104.0, 104.0, 104.0)
        val closes = listOf(99.0, 100.0, 98.0, 101.0, 120.0, 115.0, 107.0, 107.0, 107.0)
        return highs.indices.map { i ->
            Candle(
                symbol = symbol,
                timeframe = CandleTimeframe.DAILY,
                timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(i.toLong(), ChronoUnit.DAYS),
                open = BigDecimal.valueOf(closes[i]),
                high = BigDecimal.valueOf(highs[i]),
                low = BigDecimal.valueOf(lows[i]),
                close = BigDecimal.valueOf(closes[i]),
                volume = BigDecimal.valueOf(1_000_000.0),
            )
        }
    }

    // Hand-verified by running AdxCalculator's own Wilder double-smoothing algorithm (period 5)
    // over this series, identical to AdxTrendStrategyTest: flat baseline (12 bars), sustained
    // uptrend (20 bars) producing a confirmed bullish-trend entry at index 13 (fills at index 14's
    // open), then a reversal (10 bars) producing a confirmed DI-reversal exit at index 33 (fills
    // at index 34's open).
    private fun adxCandles(symbol: String): List<Candle> {
        val candles = mutableListOf<Candle>()
        var i = 0
        fun add(high: Double, low: Double, close: Double) {
            candles.add(
                Candle(
                    symbol = symbol,
                    timeframe = CandleTimeframe.DAILY,
                    timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(i.toLong(), ChronoUnit.DAYS),
                    open = BigDecimal.valueOf(close),
                    high = BigDecimal.valueOf(high),
                    low = BigDecimal.valueOf(low),
                    close = BigDecimal.valueOf(close),
                    volume = BigDecimal.valueOf(1_000_000.0),
                ),
            )
            i++
        }
        repeat(12) { add(100.0, 96.0, 98.0) }
        repeat(20) { step -> val up = (step + 1) * 3.0; add(100.0 + up, 96.0 + up, 98.0 + up) }
        repeat(10) { step -> val down = (step + 1) * 6.0; add(100.0 + 60.0 - down + 1.0, 100.0 + 60.0 - down - 5.0, 100.0 + 60.0 - down - 2.0) }
        return candles
    }

    private fun donchianRequest(symbol: String = "BTEST") = movingAverageCrossoverRequest(symbol).copy(
        fromDate = LocalDate.of(2026, 1, 5),
        toDate = LocalDate.of(2026, 1, 12),
        strategyType = StrategyType.DONCHIAN_CHANNEL_BREAKOUT,
        fastPeriod = null,
        slowPeriod = null,
        donchianEntryPeriod = 3,
        donchianExitPeriod = 2,
    )

    private fun adxRequest(symbol: String = "BTEST") = movingAverageCrossoverRequest(symbol).copy(
        fromDate = LocalDate.of(2026, 1, 11),
        toDate = LocalDate.of(2026, 2, 15),
        strategyType = StrategyType.ADX_TREND,
        fastPeriod = null,
        slowPeriod = null,
        adxPeriod = 5,
        adxThreshold = BigDecimal("25"),
    )

    @Test
    fun `a Donchian Channel Breakout scenario produces the hand-computed trade`() {
        val auth = register("backtest-donchian@example.com")
        fakeMarketDataProvider.candlesToReturn = donchianCandles("BTEST")

        val response = postBacktest(auth.accessToken, donchianRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals(1, body.totalTrades)
        assertEquals(0, body.winningTrades)
        assertEquals(1, body.losingTrades)
        assertEquals(BigDecimal("-8.0000"), body.netPnl)
        assertEquals(BigDecimal("992.0000"), body.finalEquity)
    }

    @Test
    fun `an ADX Trend scenario produces the hand-computed trade`() {
        val auth = register("backtest-adx@example.com")
        fakeMarketDataProvider.candlesToReturn = adxCandles("BTEST")

        val response = postBacktest(auth.accessToken, adxRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals(1, body.totalTrades)
        assertEquals(1, body.winningTrades)
        assertEquals(0, body.losingTrades)
        assertEquals(BigDecimal("33.0000"), body.netPnl)
        assertEquals(BigDecimal("1033.0000"), body.finalEquity)
    }

    @Test
    fun `Donchian parameters are rejected with 400 when fastPeriod is also supplied`() {
        val auth = register("backtest-donchiancrossparams@example.com")

        val request = donchianRequest().copy(fastPeriod = 10)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an ADX threshold outside 0 to 100 is rejected with 400`() {
        val auth = register("backtest-adxinvalid@example.com")

        val request = adxRequest().copy(adxThreshold = BigDecimal("150"))
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `ADX parameters are rejected with 400 when rsiPeriod is also supplied`() {
        val auth = register("backtest-adxcrossparams@example.com")

        val request = adxRequest().copy(rsiPeriod = 14)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    private fun postBacktest(token: String, request: BacktestRequest) = mockMvc.post("/api/v1/backtests") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `a known crossover scenario produces the hand-computed trade and metrics`() {
        val auth = register("backtest-crossover@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val response = postBacktest(auth.accessToken, movingAverageCrossoverRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals("BTEST", body.symbol)
        assertEquals(1, body.totalTrades)
        assertEquals(1, body.winningTrades)
        assertEquals(0, body.losingTrades)
        assertEquals(0, body.breakevenTrades)
        assertEquals(BigDecimal("30.0000"), body.netPnl)
        assertEquals(BigDecimal("3.0000"), body.netReturnPercent)
        assertEquals(BigDecimal("1030.0000"), body.finalEquity)
        assertEquals(BigDecimal("0.0000"), body.maxDrawdownPercent)
        assertEquals(BigDecimal("100.0000"), body.winRate)
        assertEquals(null, body.profitFactor)
    }

    @Test
    fun `a valid forex request is accepted end-to-end`() {
        val auth = register("backtest-forex@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("EUR/USD", 10)

        val request = movingAverageCrossoverRequest(symbol = "EUR/USD").copy(accountCurrency = "USD")
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals("EUR/USD", body.symbol)
        assertEquals(0, body.totalTrades)
        assertEquals(BigDecimal("1000.0000"), body.finalEquity)
    }

    @Test
    fun `a date range exceeding the maximum is rejected with 400`() {
        val auth = register("backtest-range@example.com")

        val request = movingAverageCrossoverRequest().copy(fromDate = LocalDate.of(2020, 1, 1), toDate = LocalDate.of(2026, 1, 1))
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `cross-strategy parameters are rejected with 400 instead of silently ignored`() {
        val auth = register("backtest-crossparams@example.com")

        val request = movingAverageCrossoverRequest().copy(rsiPeriod = 14)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a forex account currency mismatch is rejected with 400`() {
        val auth = register("backtest-forexcurrency@example.com")

        val request = movingAverageCrossoverRequest(symbol = "EUR/USD").copy(accountCurrency = "EUR")
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a stock account currency other than USD is rejected with 400`() {
        val auth = register("backtest-stockcurrency@example.com")

        val request = movingAverageCrossoverRequest().copy(accountCurrency = "EUR")
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `too few candles for the requested warmup is rejected with 422`() {
        val auth = register("backtest-insufficient@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 2)

        val request = movingAverageCrossoverRequest().copy(fromDate = LocalDate.of(2026, 1, 1))
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(422, response.status)
    }

    @Test
    fun `a market data provider failure is surfaced as 502`() {
        val auth = register("backtest-providerfailure@example.com")
        fakeMarketDataProvider.failure = MarketDataProviderException("Market data provider request failed")

        postBacktest(auth.accessToken, movingAverageCrossoverRequest()).andExpect { status { isBadGateway() } }
    }

    @Test
    fun `a malformed symbol is rejected with 400 and nothing is persisted`() {
        val auth = register("backtest-badsymbol@example.com")

        val request = movingAverageCrossoverRequest(symbol = "###")
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("Invalid symbol"))

        val listResponse = mockMvc.get("/api/v1/backtests") { header("Authorization", "Bearer ${auth.accessToken}") }
            .andReturn().response
        val runs = objectMapper.readValue(listResponse.contentAsString, Array<BacktestSummaryResponse>::class.java)
        assertEquals(0, runs.size)
    }

    @Test
    fun `a user cannot read another user backtest, and a missing id looks identical`() {
        val ownerAuth = register("backtest-owner@example.com")
        val otherAuth = register("backtest-other@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val createResponse = postBacktest(ownerAuth.accessToken, movingAverageCrossoverRequest()).andReturn().response
        val created = objectMapper.readValue(createResponse.contentAsString, BacktestResponse::class.java)

        mockMvc.get("/api/v1/backtests/${created.id}") { header("Authorization", "Bearer ${otherAuth.accessToken}") }
            .andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/backtests/${UUID.randomUUID()}") { header("Authorization", "Bearer ${ownerAuth.accessToken}") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `listing backtests returns only the authenticated user own runs`() {
        val userA = register("backtest-lista@example.com")
        val userB = register("backtest-listb@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)

        assertEquals(201, postBacktest(userA.accessToken, movingAverageCrossoverRequest()).andReturn().response.status)
        assertEquals(201, postBacktest(userA.accessToken, movingAverageCrossoverRequest()).andReturn().response.status)
        assertEquals(201, postBacktest(userB.accessToken, movingAverageCrossoverRequest()).andReturn().response.status)

        val listResponse = mockMvc.get("/api/v1/backtests") { header("Authorization", "Bearer ${userA.accessToken}") }
            .andReturn().response
        val runs = objectMapper.readValue(listResponse.contentAsString, Array<BacktestSummaryResponse>::class.java)

        assertEquals(2, runs.size)
    }

    @Test
    fun `the trades endpoint returns the trade and equity curve, scoped by ownership`() {
        val ownerAuth = register("backtest-tradesowner@example.com")
        val otherAuth = register("backtest-tradesother@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val createResponse = postBacktest(ownerAuth.accessToken, movingAverageCrossoverRequest()).andReturn().response
        val created = objectMapper.readValue(createResponse.contentAsString, BacktestResponse::class.java)

        val detailResponse = mockMvc.get("/api/v1/backtests/${created.id}/trades") {
            header("Authorization", "Bearer ${ownerAuth.accessToken}")
        }.andReturn().response
        val detail = objectMapper.readValue(detailResponse.contentAsString, BacktestDetailResponse::class.java)

        assertEquals(1, detail.trades.size)
        assertTrue(detail.equityCurve.isNotEmpty())
        assertEquals(detail.equityCurve.sortedBy { it.sequenceNumber }, detail.equityCurve)

        mockMvc.get("/api/v1/backtests/${created.id}/trades") { header("Authorization", "Bearer ${otherAuth.accessToken}") }
            .andExpect { status { isNotFound() } }
    }

    // --- ATR risk management -------------------------------------------------------------------

    @Test
    fun `a risk-based ATR run persists stop prices and echoes the risk configuration`() {
        val auth = register("backtest-atrhappy@example.com")
        fakeMarketDataProvider.candlesToReturn = crossoverCandles("BTEST")

        val request = movingAverageCrossoverRequest().copy(
            positionSizingType = PositionSizingType.RISK_BASED_ATR,
            quantity = null,
            riskPercentPerTrade = BigDecimal("1"),
            atrRiskManagementEnabled = true,
            atrPeriod = 2,
            atrInitialStopMultiplier = BigDecimal("2"),
        )
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)

        assertEquals(PositionSizingType.RISK_BASED_ATR, body.positionSizingType)
        assertEquals(null, body.quantity)
        assertEquals(BigDecimal("1"), body.riskPercentPerTrade)
        assertEquals(true, body.atrRiskManagementEnabled)
        assertEquals(2, body.atrPeriod)
        assertEquals(1, body.totalTrades)

        val detailResponse = mockMvc.get("/api/v1/backtests/${body.id}/trades") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        val detail = objectMapper.readValue(detailResponse.contentAsString, BacktestDetailResponse::class.java)

        assertEquals(1, detail.trades.size)
        val trade = detail.trades[0]
        assertTrue(trade.quantity.signum() > 0)
        assertTrue(trade.initialStopPrice != null)
        assertTrue(trade.finalStopPrice != null)
    }

    @Test
    fun `riskPercentPerTrade is rejected with 400 under FIXED_QUANTITY sizing`() {
        val auth = register("backtest-riskpctfixed@example.com")

        val request = movingAverageCrossoverRequest().copy(riskPercentPerTrade = BigDecimal("1"))
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `quantity is rejected with 400 under RISK_BASED_ATR sizing`() {
        val auth = register("backtest-qtyriskbased@example.com")

        val request = movingAverageCrossoverRequest().copy(
            positionSizingType = PositionSizingType.RISK_BASED_ATR,
            riskPercentPerTrade = BigDecimal("1"),
            atrRiskManagementEnabled = true,
        )
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `RISK_BASED_ATR sizing without ATR risk management enabled is rejected with 400`() {
        val auth = register("backtest-riskbasednoatr@example.com")

        val request = movingAverageCrossoverRequest().copy(
            positionSizingType = PositionSizingType.RISK_BASED_ATR,
            quantity = null,
            riskPercentPerTrade = BigDecimal("1"),
        )
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `ATR fields supplied while ATR risk management is disabled are rejected with 400`() {
        val auth = register("backtest-atrfieldsdisabled@example.com")

        val request = movingAverageCrossoverRequest().copy(atrPeriod = 14)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a non-positive ATR period is rejected with 400`() {
        val auth = register("backtest-badatrperiod@example.com")

        val request = movingAverageCrossoverRequest().copy(atrRiskManagementEnabled = true, atrPeriod = 0)
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a trailing stop multiplier supplied while trailing is disabled is rejected with 400`() {
        val auth = register("backtest-trailingmultiplierdisabled@example.com")

        val request = movingAverageCrossoverRequest().copy(
            atrRiskManagementEnabled = true,
            atrTrailingStopMultiplier = BigDecimal("2"),
        )
        postBacktest(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `insufficient history for the ATR period alone is rejected with 422`() {
        val auth = register("backtest-atrwarmupinsufficient@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)

        // RSI_THRESHOLD's own warmup (rsiPeriod 2 -> 3) is satisfied by firstTradableIndex=5, but
        // atrPeriod=10 raises the effective floor above it.
        val request = movingAverageCrossoverRequest().copy(
            strategyType = StrategyType.RSI_THRESHOLD,
            fastPeriod = null,
            slowPeriod = null,
            rsiPeriod = 2,
            oversoldThreshold = BigDecimal("30"),
            overboughtThreshold = BigDecimal("70"),
            fromDate = LocalDate.of(2026, 1, 6),
            toDate = LocalDate.of(2026, 1, 10),
            atrRiskManagementEnabled = true,
            atrPeriod = 10,
            atrInitialStopMultiplier = BigDecimal("2"),
        )
        val response = postBacktest(auth.accessToken, request).andReturn().response
        assertEquals(422, response.status)
    }
}
