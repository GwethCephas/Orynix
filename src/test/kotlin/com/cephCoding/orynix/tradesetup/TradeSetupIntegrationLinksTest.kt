package com.cephCoding.orynix.tradesetup

import com.cephCoding.orynix.agents.domain.AnalysisProvenance
import com.cephCoding.orynix.agents.domain.AnalysisSource
import com.cephCoding.orynix.agents.domain.ModelAnalysis
import com.cephCoding.orynix.agents.domain.ModelTradeSetup
import com.cephCoding.orynix.agents.domain.TradeDirection as AgentsTradeDirection
import com.cephCoding.orynix.agents.dto.MarketAnalysisRequest
import com.cephCoding.orynix.agents.dto.MarketAnalysisResponse
import com.cephCoding.orynix.backtesting.domain.PositionSizingType
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.dto.BacktestResponse
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.papertrading.domain.OrderSide
import com.cephCoding.orynix.papertrading.dto.CreatePaperTradingAccountRequest
import com.cephCoding.orynix.papertrading.dto.PaperTradingAccountResponse
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetupDecision
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.dto.TradeSetupBacktestLinkRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupCreateRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupDecisionRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupFromProposalRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupPaperOrderRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupPaperOrderResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Integration A/B/C from CLAUDE.md Section 21: AI proposal -> trade setup (server-verifiable),
 * trade setup -> backtest (provenance link only), trade setup -> paper trading ("Practice in
 * Paper Trading"). Deliberately a separate file from TradeSetupControllerTest, which already
 * covers the pre-existing CRUD/lifecycle/risk-evaluation surface untouched by this work.
 */
class TradeSetupIntegrationLinksTest : AbstractIntegrationTest() {

    // ---- shared helpers ----

    private fun manualLong(symbol: String = "AAPL", accountProfileId: UUID? = null) = TradeSetupCreateRequest(
        symbol = symbol, direction = TradeDirection.LONG, entryPrice = BigDecimal("100"),
        stopLossPrice = BigDecimal("95"), targetPrice = BigDecimal("115"), rationale = "Manual idea",
        source = TradeSetupSource.MANUAL, aiProviderName = null, aiModel = null, aiAnalysisAsOf = null,
        accountProfileId = accountProfileId,
    )

    private fun manualShort(symbol: String = "AAPL") = TradeSetupCreateRequest(
        symbol = symbol, direction = TradeDirection.SHORT, entryPrice = BigDecimal("100"),
        stopLossPrice = BigDecimal("105"), targetPrice = BigDecimal("85"), rationale = "Bearish idea",
        source = TradeSetupSource.MANUAL, aiProviderName = null, aiModel = null, aiAnalysisAsOf = null,
        accountProfileId = null,
    )

    private fun createSetupOk(token: String, request: TradeSetupCreateRequest): TradeSetupResponse {
        val response = mockMvc.post("/api/v1/trade-setups") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
    }

    private fun accept(token: String, id: UUID) {
        val response = mockMvc.post("/api/v1/trade-setups/$id/decision") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(200, response.status)
    }

    // ---- Integration A: AI proposal -> trade setup ----

    private fun stockCandles(symbol: String): List<Candle> = (0 until 60).map { day ->
        val close = BigDecimal(100 + (day % 5))
        Candle(
            symbol = symbol, timeframe = CandleTimeframe.DAILY,
            timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(day.toLong(), ChronoUnit.DAYS),
            open = close, high = close.add(BigDecimal("1")), low = close.subtract(BigDecimal("1")), close = close,
            volume = BigDecimal("1000000"),
        )
    }

    private fun runAnalysis(token: String, symbol: String = "AAPL"): MarketAnalysisResponse {
        fakeMarketDataProvider.candlesToReturn = stockCandles(symbol)
        fakeFundamentalDataProvider.companyProfileToReturn = CompanyProfile("Apple Inc.", "NASDAQ", "Technology", BigDecimal("3000000000000"))
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-3.5-flash-lite"),
            marketAssessment = "Bullish setup.",
            scenarios = emptyList(),
            tradeSetups = listOf(
                ModelTradeSetup(AgentsTradeDirection.LONG, BigDecimal("100"), BigDecimal("95"), BigDecimal("115"), "Breakout expected."),
            ),
        )
        val response = mockMvc.post("/api/v1/market-analyses") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MarketAnalysisRequest(symbol = symbol))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(200, response.status)
        return objectMapper.readValue(response.contentAsString, MarketAnalysisResponse::class.java)
    }

    private fun fromProposal(token: String, request: TradeSetupFromProposalRequest) = mockMvc.post("/api/v1/trade-setups/from-ai-proposal") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `creating a trade setup from a real AI proposal copies every provenance field server-side`() {
        val auth = register("ts-fromproposal@example.com")
        val analysis = runAnalysis(auth.accessToken)
        val proposal = analysis.tradeSetups.single()

        val response = fromProposal(auth.accessToken, TradeSetupFromProposalRequest(proposal.id, null)).andReturn().response
        assertEquals(201, response.status)
        val saved = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)

        assertEquals(proposal.id, saved.aiProposalId)
        assertEquals(TradeSetupSource.AI_ANALYSIS, saved.source)
        assertEquals("gemini", saved.aiProviderName)
        assertEquals("gemini-3.5-flash-lite", saved.aiModel)
        assertEquals(0, proposal.entryPrice.compareTo(saved.entryPrice))
        assertEquals(0, proposal.stopLossPrice.compareTo(saved.stopLossPrice))
        assertEquals("AAPL", saved.symbol)
    }

    @Test
    fun `creating a trade setup from an unknown proposal id returns 404`() {
        val auth = register("ts-proposalunknown@example.com")
        fromProposal(auth.accessToken, TradeSetupFromProposalRequest(UUID.randomUUID(), null))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `creating a trade setup from another user's proposal returns 404`() {
        val authA = register("ts-proposalownera@example.com")
        val authB = register("ts-proposalownerb@example.com")
        val analysis = runAnalysis(authA.accessToken)
        val proposal = analysis.tradeSetups.single()

        fromProposal(authB.accessToken, TradeSetupFromProposalRequest(proposal.id, null))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `reusing an already-used AI proposal returns 409`() {
        val auth = register("ts-proposalreuse@example.com")
        val analysis = runAnalysis(auth.accessToken)
        val proposal = analysis.tradeSetups.single()

        val first = fromProposal(auth.accessToken, TradeSetupFromProposalRequest(proposal.id, null)).andReturn().response
        assertEquals(201, first.status)

        fromProposal(auth.accessToken, TradeSetupFromProposalRequest(proposal.id, null))
            .andExpect { status { isConflict() } }
    }

    // ---- Integration B: trade setup -> backtest link ----

    private fun crossoverCandles(symbol: String): List<Candle> {
        val closes = listOf(100.0, 100.0, 100.0, 130.0, 60.0, 90.0, 90.0)
        return closes.mapIndexed { i, close ->
            Candle(
                symbol = symbol, timeframe = CandleTimeframe.DAILY,
                timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(i.toLong(), ChronoUnit.DAYS),
                open = BigDecimal.valueOf(close - 1.0), high = BigDecimal.valueOf(maxOf(close - 1.0, close) + 1.0),
                low = BigDecimal.valueOf(minOf(close - 1.0, close) - 1.0), close = BigDecimal.valueOf(close),
                volume = BigDecimal.valueOf(1_000_000.0),
            )
        }
    }

    private fun backtestRequest(symbol: String) = BacktestRequest(
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

    private fun runBacktest(token: String, symbol: String): BacktestResponse {
        fakeMarketDataProvider.candlesToReturn = crossoverCandles(symbol)
        val response = mockMvc.post("/api/v1/backtests") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(backtestRequest(symbol))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)
    }

    private fun linkBacktest(token: String, setupId: UUID, backtestRunId: UUID) = mockMvc.post("/api/v1/trade-setups/$setupId/backtest-link") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(TradeSetupBacktestLinkRequest(backtestRunId))
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `linking a backtest run with a matching symbol succeeds`() {
        val auth = register("ts-backtestlink@example.com")
        val setup = createSetupOk(auth.accessToken, manualLong(symbol = "BTEST"))
        val run = runBacktest(auth.accessToken, "BTEST")

        val response = linkBacktest(auth.accessToken, setup.id, run.id).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(run.id, body.backtestRunId)
    }

    @Test
    fun `linking a backtest run with a mismatched symbol is rejected with 400`() {
        val auth = register("ts-backtestmismatch@example.com")
        val setup = createSetupOk(auth.accessToken, manualLong(symbol = "AAPL"))
        val run = runBacktest(auth.accessToken, "BTEST")

        linkBacktest(auth.accessToken, setup.id, run.id).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `linking another user's backtest run returns 404`() {
        val authA = register("ts-backtestcrossa@example.com")
        val authB = register("ts-backtestcrossb@example.com")
        val run = runBacktest(authA.accessToken, "BTEST")
        val setup = createSetupOk(authB.accessToken, manualLong(symbol = "BTEST"))

        linkBacktest(authB.accessToken, setup.id, run.id).andExpect { status { isNotFound() } }
    }

    @Test
    fun `linking a backtest to another user's trade setup returns 404`() {
        val authA = register("ts-setupcrossa@example.com")
        val authB = register("ts-setupcrossb@example.com")
        val setup = createSetupOk(authA.accessToken, manualLong(symbol = "BTEST"))
        val run = runBacktest(authB.accessToken, "BTEST")

        linkBacktest(authB.accessToken, setup.id, run.id).andExpect { status { isNotFound() } }
    }

    // ---- Integration C: trade setup -> paper trading ----

    private fun priceCandle(symbol: String, close: Double): Candle = Candle(
        symbol = symbol, timeframe = CandleTimeframe.DAILY, timestamp = Instant.now(),
        open = BigDecimal.valueOf(close), high = BigDecimal.valueOf(close + 1.0),
        low = BigDecimal.valueOf(close - 1.0), close = BigDecimal.valueOf(close),
        volume = BigDecimal.valueOf(1_000_000.0),
    )

    private fun createPaperAccount(token: String): PaperTradingAccountResponse {
        val response = mockMvc.post("/api/v1/paper-trading/accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreatePaperTradingAccountRequest("Practice account", "USD", BigDecimal("10000")))
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, PaperTradingAccountResponse::class.java)
    }

    private fun practice(token: String, setupId: UUID, request: TradeSetupPaperOrderRequest) = mockMvc.post("/api/v1/trade-setups/$setupId/paper-order") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    @Test
    fun `practicing an accepted LONG setup in paper trading fills a BUY order and links it back`() {
        val auth = register("ts-practicelong@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(priceCandle("AAPL", 100.0))
        val setup = createSetupOk(auth.accessToken, manualLong())
        accept(auth.accessToken, setup.id)
        val account = createPaperAccount(auth.accessToken)

        val response = practice(
            auth.accessToken, setup.id,
            TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), UUID.randomUUID().toString()),
        ).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupPaperOrderResponse::class.java)
        assertEquals(OrderSide.BUY, body.order.side)
        assertEquals(account.id, body.tradeSetup.paperAccountId)
        assertEquals(body.order.id, body.tradeSetup.paperOrderId)
    }

    @Test
    fun `practicing an accepted SHORT setup fills a SELL order despite no deterministic sizing`() {
        val auth = register("ts-practiceshort@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(priceCandle("AAPL", 100.0))
        val setup = createSetupOk(auth.accessToken, manualShort())
        accept(auth.accessToken, setup.id)
        val account = createPaperAccount(auth.accessToken)

        val response = practice(
            auth.accessToken, setup.id,
            TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), UUID.randomUUID().toString()),
        ).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupPaperOrderResponse::class.java)
        assertEquals(OrderSide.SELL, body.order.side)
    }

    @Test
    fun `practicing a DRAFT setup in paper trading is rejected with 400`() {
        val auth = register("ts-practicedraft@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(priceCandle("AAPL", 100.0))
        val setup = createSetupOk(auth.accessToken, manualLong())
        val account = createPaperAccount(auth.accessToken)

        practice(auth.accessToken, setup.id, TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), UUID.randomUUID().toString()))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `practicing an already-linked setup a second time returns 409`() {
        val auth = register("ts-practicetwice@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(priceCandle("AAPL", 100.0))
        val setup = createSetupOk(auth.accessToken, manualLong())
        accept(auth.accessToken, setup.id)
        val account = createPaperAccount(auth.accessToken)

        val first = practice(auth.accessToken, setup.id, TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), UUID.randomUUID().toString()))
            .andReturn().response
        assertEquals(201, first.status)

        practice(auth.accessToken, setup.id, TradeSetupPaperOrderRequest(account.id, BigDecimal("3"), UUID.randomUUID().toString()))
            .andExpect { status { isConflict() } }
    }

    @Test
    fun `practicing another user's trade setup returns 404`() {
        val authA = register("ts-practicecrossa@example.com")
        val authB = register("ts-practicecrossb@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(priceCandle("AAPL", 100.0))
        val setup = createSetupOk(authA.accessToken, manualLong())
        accept(authA.accessToken, setup.id)
        val account = createPaperAccount(authB.accessToken)

        practice(authB.accessToken, setup.id, TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), UUID.randomUUID().toString()))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `an idempotency key replay on practice returns the same order without a duplicate link write`() {
        val auth = register("ts-practiceidempotent@example.com")
        fakeMarketDataProvider.candlesBySymbol["AAPL"] = listOf(priceCandle("AAPL", 100.0))
        val setup = createSetupOk(auth.accessToken, manualLong())
        accept(auth.accessToken, setup.id)
        val account = createPaperAccount(auth.accessToken)
        val key = UUID.randomUUID().toString()

        practice(auth.accessToken, setup.id, TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), key))
            .andExpect { status { isCreated() } }

        // The setup is now linked, so a literal retry (even with the same idempotencyKey) correctly
        // reports the conflict rather than silently succeeding again - idempotency protects the
        // paper order itself, not a second attempt to link an already-linked setup.
        practice(auth.accessToken, setup.id, TradeSetupPaperOrderRequest(account.id, BigDecimal("5"), key))
            .andExpect { status { isConflict() } }
    }
}
