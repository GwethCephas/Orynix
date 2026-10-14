package com.cephCoding.orynix.tradesetup

import com.cephCoding.orynix.agents.domain.AnalysisProvenance
import com.cephCoding.orynix.agents.domain.AnalysisSource
import com.cephCoding.orynix.agents.domain.ModelAnalysis
import com.cephCoding.orynix.agents.domain.ModelTradeSetup
import com.cephCoding.orynix.agents.dto.MarketAnalysisRequest
import com.cephCoding.orynix.agents.dto.MarketAnalysisResponse
import com.cephCoding.orynix.agents.domain.TradeDirection as AgentsTradeDirection
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.tradesetup.domain.TradeDirection
import com.cephCoding.orynix.tradesetup.domain.TradeSetupDecision
import com.cephCoding.orynix.tradesetup.domain.TradeSetupOutcome
import com.cephCoding.orynix.tradesetup.domain.TradeSetupSource
import com.cephCoding.orynix.tradesetup.dto.TradeSetupClosureRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupCreateRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupDecisionRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupRiskEvaluationRequest
import com.cephCoding.orynix.tradesetup.dto.TradeSetupRiskEvaluationResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupSummaryResponse
import com.cephCoding.orynix.tradesetup.dto.TradeSetupUpdateRequest
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class TradeSetupControllerTest : AbstractIntegrationTest() {

    // ---- helpers ----

    private fun createSetup(token: String, request: TradeSetupCreateRequest) = mockMvc.post("/api/v1/trade-setups") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun createSetupOk(token: String, request: TradeSetupCreateRequest): TradeSetupResponse {
        val response = createSetup(token, request).andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
    }

    private fun getSetup(token: String, id: UUID) = mockMvc.get("/api/v1/trade-setups/$id") {
        header("Authorization", "Bearer $token")
    }

    private fun listSetups(token: String, status: String? = null, assetType: String? = null, symbol: String? = null) =
        mockMvc.get("/api/v1/trade-setups") {
            header("Authorization", "Bearer $token")
            status?.let { param("status", it) }
            assetType?.let { param("assetType", it) }
            symbol?.let { param("symbol", it) }
        }

    private fun updateSetup(token: String, id: UUID, request: TradeSetupUpdateRequest) = mockMvc.put("/api/v1/trade-setups/$id") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun expireSetup(token: String, id: UUID) = mockMvc.delete("/api/v1/trade-setups/$id") {
        header("Authorization", "Bearer $token")
    }

    private fun decideSetup(token: String, id: UUID, request: TradeSetupDecisionRequest) = mockMvc.post("/api/v1/trade-setups/$id/decision") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun closeSetup(token: String, id: UUID, request: TradeSetupClosureRequest) = mockMvc.post("/api/v1/trade-setups/$id/closure") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun evaluateRisk(token: String, id: UUID, request: TradeSetupRiskEvaluationRequest = TradeSetupRiskEvaluationRequest()) =
        mockMvc.post("/api/v1/trade-setups/$id/risk-evaluation") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $token")
        }

    private fun createProfile(
        token: String,
        balance: BigDecimal = BigDecimal("10000"),
        accountCurrency: String = "USD",
        riskPercentPerTrade: BigDecimal = BigDecimal("1"),
    ): TradingAccountProfileResponse {
        val request = TradingAccountProfileRequest(
            name = "Trade setup profile", platform = TradingPlatform.OTHER, platformDetails = null,
            accountCurrency = accountCurrency, balance = balance, riskPercentPerTrade = riskPercentPerTrade,
            maxDailyLossPercent = null, maxConcurrentPositions = null,
        )
        val response = mockMvc.post("/api/v1/trading-accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, TradingAccountProfileResponse::class.java)
    }

    private fun archiveProfile(token: String, id: UUID) {
        val response = mockMvc.delete("/api/v1/trading-accounts/$id") { header("Authorization", "Bearer $token") }.andReturn().response
        assertEquals(204, response.status)
    }

    private fun longManualRequest(
        symbol: String = "AAPL",
        entryPrice: BigDecimal = BigDecimal("100"),
        stopLossPrice: BigDecimal? = BigDecimal("95"),
        targetPrice: BigDecimal? = BigDecimal("115"),
        accountProfileId: UUID? = null,
    ) = TradeSetupCreateRequest(
        symbol = symbol, direction = TradeDirection.LONG, entryPrice = entryPrice, stopLossPrice = stopLossPrice,
        targetPrice = targetPrice, rationale = "A manually entered idea.", source = TradeSetupSource.MANUAL,
        aiProviderName = null, aiModel = null, aiAnalysisAsOf = null, accountProfileId = accountProfileId,
    )

    private fun candle(symbol: String, day: Int): Candle {
        val close = BigDecimal(100 + (day % 5))
        return Candle(
            symbol = symbol, timeframe = CandleTimeframe.DAILY,
            timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(day.toLong(), ChronoUnit.DAYS),
            open = close, high = close.add(BigDecimal("1")), low = close.subtract(BigDecimal("1")), close = close,
            volume = BigDecimal("1000000"),
        )
    }

    private fun setupStockFixturesForAnalysis(symbol: String) {
        fakeMarketDataProvider.candlesToReturn = (0 until 60).map { candle(symbol, it) }
        fakeFundamentalDataProvider.companyProfileToReturn = CompanyProfile("Apple Inc.", "NASDAQ", "Technology", BigDecimal("3000000000000"))
    }

    // ---- create ----

    @Test
    fun `creating a manual stock setup succeeds`() {
        val auth = register("ts-manualstock@example.com")
        val response = createSetup(auth.accessToken, longManualRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals("AAPL", body.symbol)
        assertEquals(TradeDirection.LONG, body.direction)
        assertEquals(com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus.DRAFT, body.status)
    }

    @Test
    fun `creating a manual forex setup succeeds`() {
        val auth = register("ts-manualforex@example.com")
        val response = createSetup(
            auth.accessToken,
            longManualRequest(symbol = "USD/JPY", entryPrice = BigDecimal("150"), stopLossPrice = BigDecimal("148"), targetPrice = BigDecimal("155")),
        ).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals("USD/JPY", body.symbol)
    }

    @Test
    fun `creating a SHORT setup with correct ordering succeeds`() {
        val auth = register("ts-short@example.com")
        val request = TradeSetupCreateRequest(
            symbol = "AAPL", direction = TradeDirection.SHORT, entryPrice = BigDecimal("100"),
            stopLossPrice = BigDecimal("105"), targetPrice = BigDecimal("85"), rationale = "Bearish",
            source = TradeSetupSource.MANUAL, aiProviderName = null, aiModel = null, aiAnalysisAsOf = null, accountProfileId = null,
        )
        val response = createSetup(auth.accessToken, request).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(TradeDirection.SHORT, body.direction)
    }

    @Test
    fun `saving a proposal from a real AI analysis response succeeds`() {
        val auth = register("ts-fromai@example.com")
        setupStockFixturesForAnalysis("AAPL")
        fakeMarketAnalysisProvider.nextResponse = ModelAnalysis(
            provenance = AnalysisProvenance(AnalysisSource.MODEL, "gemini", "gemini-3.5-flash-lite"),
            marketAssessment = "Bullish setup.",
            scenarios = emptyList(),
            tradeSetups = listOf(
                ModelTradeSetup(AgentsTradeDirection.LONG, BigDecimal("100"), BigDecimal("95"), BigDecimal("115"), "Breakout expected."),
            ),
        )
        val analysisResponse = mockMvc.post("/api/v1/market-analyses") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(MarketAnalysisRequest(symbol = "AAPL"))
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andReturn().response
        assertEquals(200, analysisResponse.status)
        val analysis = objectMapper.readValue(analysisResponse.contentAsString, MarketAnalysisResponse::class.java)
        val proposal = analysis.tradeSetups.single()

        val createRequest = TradeSetupCreateRequest(
            symbol = analysis.symbol,
            direction = TradeDirection.valueOf(proposal.direction.name),
            entryPrice = proposal.entryPrice,
            stopLossPrice = proposal.stopLossPrice,
            targetPrice = proposal.takeProfitPrice,
            rationale = proposal.rationale,
            source = TradeSetupSource.AI_ANALYSIS,
            aiProviderName = analysis.provenance.providerName,
            aiModel = analysis.provenance.model,
            aiAnalysisAsOf = analysis.asOf,
            accountProfileId = null,
        )
        val savedResponse = createSetup(auth.accessToken, createRequest).andReturn().response
        assertEquals(201, savedResponse.status)
        val saved = objectMapper.readValue(savedResponse.contentAsString, TradeSetupResponse::class.java)
        assertEquals(TradeSetupSource.AI_ANALYSIS, saved.source)
        assertEquals("gemini", saved.aiProviderName)
        assertEquals("gemini-3.5-flash-lite", saved.aiModel)
        assertNotNull(saved.aiAnalysisAsOf)
    }

    @Test
    fun `AI_ANALYSIS source missing provenance fields is rejected with 400`() {
        val auth = register("ts-aimissing@example.com")
        val request = longManualRequest().copy(source = TradeSetupSource.AI_ANALYSIS)
        createSetup(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `MANUAL source with provenance fields set is rejected with 400`() {
        val auth = register("ts-manualwithai@example.com")
        val request = longManualRequest().copy(aiProviderName = "gemini")
        createSetup(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid LONG price ordering is rejected with 400`() {
        val auth = register("ts-badlong@example.com")
        createSetup(auth.accessToken, longManualRequest(stopLossPrice = BigDecimal("105"))).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `invalid SHORT price ordering is rejected with 400`() {
        val auth = register("ts-badshort@example.com")
        val request = TradeSetupCreateRequest(
            symbol = "AAPL", direction = TradeDirection.SHORT, entryPrice = BigDecimal("100"),
            stopLossPrice = BigDecimal("90"), targetPrice = null, rationale = null,
            source = TradeSetupSource.MANUAL, aiProviderName = null, aiModel = null, aiAnalysisAsOf = null, accountProfileId = null,
        )
        createSetup(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an unresolvable symbol is rejected with 400`() {
        val auth = register("ts-badsymbol@example.com")
        createSetup(auth.accessToken, longManualRequest(symbol = "###")).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `referencing another user's account profile returns 404`() {
        val authA = register("ts-profileownera@example.com")
        val authB = register("ts-profileownerb@example.com")
        val profile = createProfile(authA.accessToken)
        createSetup(authB.accessToken, longManualRequest(accountProfileId = profile.id)).andExpect { status { isNotFound() } }
    }

    @Test
    fun `referencing an archived account profile returns 404`() {
        val auth = register("ts-archivedprofile@example.com")
        val profile = createProfile(auth.accessToken)
        archiveProfile(auth.accessToken, profile.id)
        createSetup(auth.accessToken, longManualRequest(accountProfileId = profile.id)).andExpect { status { isNotFound() } }
    }

    @Test
    fun `an account currency mismatch is rejected with 400`() {
        val auth = register("ts-currencymismatch@example.com")
        val profile = createProfile(auth.accessToken, accountCurrency = "USD")
        // USD/JPY requires accountCurrency = JPY (the quote currency), not USD.
        val request = longManualRequest(symbol = "USD/JPY", entryPrice = BigDecimal("150"), stopLossPrice = BigDecimal("148"), targetPrice = BigDecimal("155"), accountProfileId = profile.id)
        createSetup(auth.accessToken, request).andExpect { status { isBadRequest() } }
    }

    // ---- list ----

    @Test
    fun `listing setups is scoped to the authenticated owner`() {
        val authA = register("ts-lista@example.com")
        val authB = register("ts-listb@example.com")
        createSetupOk(authA.accessToken, longManualRequest())
        createSetupOk(authA.accessToken, longManualRequest())
        createSetupOk(authB.accessToken, longManualRequest())

        val response = listSetups(authA.accessToken).andReturn().response
        val setups = objectMapper.readValue(response.contentAsString, Array<TradeSetupSummaryResponse>::class.java)
        assertEquals(2, setups.size)
    }

    @Test
    fun `listing setups can be filtered by status, assetType, and symbol`() {
        val auth = register("ts-listfilter@example.com")
        val draft = createSetupOk(auth.accessToken, longManualRequest(symbol = "AAPL"))
        val toAccept = createSetupOk(auth.accessToken, longManualRequest(symbol = "MSFT"))
        decideSetup(auth.accessToken, toAccept.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))

        val byStatus = objectMapper.readValue(
            listSetups(auth.accessToken, status = "ACCEPTED").andReturn().response.contentAsString,
            Array<TradeSetupSummaryResponse>::class.java,
        )
        assertEquals(1, byStatus.size)
        assertEquals(toAccept.id, byStatus[0].id)

        val bySymbol = objectMapper.readValue(
            listSetups(auth.accessToken, symbol = "AAPL").andReturn().response.contentAsString,
            Array<TradeSetupSummaryResponse>::class.java,
        )
        assertEquals(1, bySymbol.size)
        assertEquals(draft.id, bySymbol[0].id)

        val byAssetType = objectMapper.readValue(
            listSetups(auth.accessToken, assetType = "STOCK").andReturn().response.contentAsString,
            Array<TradeSetupSummaryResponse>::class.java,
        )
        assertEquals(2, byAssetType.size)
    }

    // ---- get ----

    @Test
    fun `getting another user's setup returns 404`() {
        val authA = register("ts-geta@example.com")
        val authB = register("ts-getb@example.com")
        val setup = createSetupOk(authA.accessToken, longManualRequest())
        getSetup(authB.accessToken, setup.id).andExpect { status { isNotFound() } }
    }

    // ---- update ----

    @Test
    fun `updating a setup while DRAFT succeeds`() {
        val auth = register("ts-updatedraft@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        val update = TradeSetupUpdateRequest(entryPrice = BigDecimal("102"), stopLossPrice = BigDecimal("96"), targetPrice = BigDecimal("120"), rationale = "Revised", accountProfileId = null)
        val response = updateSetup(auth.accessToken, setup.id, update).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(0, BigDecimal("102").compareTo(body.entryPrice))
    }

    @Test
    fun `updating a setup after a decision is rejected with 400`() {
        val auth = register("ts-updateafterdecision@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))
        val update = TradeSetupUpdateRequest(entryPrice = BigDecimal("102"), stopLossPrice = BigDecimal("96"), targetPrice = BigDecimal("120"), rationale = null, accountProfileId = null)
        updateSetup(auth.accessToken, setup.id, update).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `updating another user's setup returns 404`() {
        val authA = register("ts-updatecrossa@example.com")
        val authB = register("ts-updatecrossb@example.com")
        val setup = createSetupOk(authA.accessToken, longManualRequest())
        val update = TradeSetupUpdateRequest(entryPrice = BigDecimal("102"), stopLossPrice = BigDecimal("96"), targetPrice = BigDecimal("120"), rationale = null, accountProfileId = null)
        updateSetup(authB.accessToken, setup.id, update).andExpect { status { isNotFound() } }
    }

    // ---- expire ----

    @Test
    fun `expiring a setup while DRAFT succeeds`() {
        val auth = register("ts-expiredraft@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        expireSetup(auth.accessToken, setup.id).andExpect { status { isNoContent() } }

        val fetched = objectMapper.readValue(getSetup(auth.accessToken, setup.id).andReturn().response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus.EXPIRED, fetched.status)
    }

    @Test
    fun `expiring a setup after a decision is rejected with 400`() {
        val auth = register("ts-expireafterdecision@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))
        expireSetup(auth.accessToken, setup.id).andExpect { status { isBadRequest() } }
    }

    // ---- decision ----

    @Test
    fun `accepting a DRAFT setup succeeds`() {
        val auth = register("ts-accept@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        val response = decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, "Looks good")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus.ACCEPTED, body.status)
        assertEquals("Looks good", body.decisionNotes)
        assertNotNull(body.decidedAt)
    }

    @Test
    fun `rejecting a DRAFT setup succeeds`() {
        val auth = register("ts-reject@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        val response = decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.REJECT, null)).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus.REJECTED, body.status)
    }

    @Test
    fun `deciding an already-decided setup is rejected with 400`() {
        val auth = register("ts-alreadydecided@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))
        decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.REJECT, null)).andExpect { status { isBadRequest() } }
    }

    // ---- closure ----

    @Test
    fun `closing an accepted setup succeeds`() {
        val auth = register("ts-close@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))

        val response = closeSetup(auth.accessToken, setup.id, TradeSetupClosureRequest(BigDecimal("112"), TradeSetupOutcome.WIN, "Hit target")).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupResponse::class.java)
        assertEquals(com.cephCoding.orynix.tradesetup.domain.TradeSetupStatus.CLOSED, body.status)
        assertEquals(0, BigDecimal("112").compareTo(body.exitPrice))
        assertEquals(TradeSetupOutcome.WIN, body.outcome)
        assertEquals("Hit target", body.closureNotes)
        assertNotNull(body.closedAt)
    }

    @Test
    fun `closing a setup without prior acceptance is rejected with 400`() {
        val auth = register("ts-closewithoutaccept@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        closeSetup(auth.accessToken, setup.id, TradeSetupClosureRequest(null, null, null)).andExpect { status { isBadRequest() } }
    }

    // ---- risk evaluation ----

    @Test
    fun `evaluating risk for a LONG setup with an account profile returns real sizing`() {
        val auth = register("ts-riskok@example.com")
        val profile = createProfile(auth.accessToken, balance = BigDecimal("10000"), riskPercentPerTrade = BigDecimal("1"))
        val setup = createSetupOk(auth.accessToken, longManualRequest(entryPrice = BigDecimal("100"), stopLossPrice = BigDecimal("98"), targetPrice = BigDecimal("106"), accountProfileId = profile.id))

        val response = evaluateRisk(auth.accessToken, setup.id).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupRiskEvaluationResponse::class.java)
        assertNotNull(body.sizing)
        // riskBudget = 10000 * 1% = 100; riskPerUnit = 100-98 = 2 -> quantity = 50
        assertEquals(0, BigDecimal("50").compareTo(body.sizing!!.quantity))
    }

    @Test
    fun `evaluating risk for a SHORT setup returns null sizing with a limitation`() {
        val auth = register("ts-riskshort@example.com")
        val request = TradeSetupCreateRequest(
            symbol = "AAPL", direction = TradeDirection.SHORT, entryPrice = BigDecimal("100"),
            stopLossPrice = BigDecimal("105"), targetPrice = BigDecimal("85"), rationale = null,
            source = TradeSetupSource.MANUAL, aiProviderName = null, aiModel = null, aiAnalysisAsOf = null, accountProfileId = null,
        )
        val setup = createSetupOk(auth.accessToken, request)

        val response = evaluateRisk(auth.accessToken, setup.id).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupRiskEvaluationResponse::class.java)
        assertNull(body.sizing)
        assertTrue(body.limitations.isNotEmpty())
    }

    @Test
    fun `evaluating risk for a setup missing a stop-loss returns null sizing with a limitation`() {
        val auth = register("ts-riskmissingstop@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest(stopLossPrice = null))

        val response = evaluateRisk(auth.accessToken, setup.id).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupRiskEvaluationResponse::class.java)
        assertNull(body.sizing)
        assertTrue(body.limitations.isNotEmpty())
    }

    @Test
    fun `evaluating risk with no account context at all is rejected with 400`() {
        val auth = register("ts-risknocontext@example.com")
        val setup = createSetupOk(auth.accessToken, longManualRequest())
        evaluateRisk(auth.accessToken, setup.id).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an explicit equity override wins over the setup's stored account profile`() {
        val auth = register("ts-riskoverride@example.com")
        val profile = createProfile(auth.accessToken, balance = BigDecimal("5000"), riskPercentPerTrade = BigDecimal("1"))
        val setup = createSetupOk(auth.accessToken, longManualRequest(entryPrice = BigDecimal("100"), stopLossPrice = BigDecimal("90"), accountProfileId = profile.id))

        val override = TradeSetupRiskEvaluationRequest(equity = BigDecimal("10000"), accountCurrency = "USD", riskPercentPerTrade = BigDecimal("1"))
        val response = evaluateRisk(auth.accessToken, setup.id, override).andReturn().response
        assertEquals(200, response.status)
        val body = objectMapper.readValue(response.contentAsString, TradeSetupRiskEvaluationResponse::class.java)
        // profile would give riskBudget=50 -> quantity=5; override gives riskBudget=100 -> quantity=10
        assertEquals(0, BigDecimal("10").compareTo(body.sizing!!.quantity))
    }

    @Test
    fun `evaluating risk for another user's setup returns 404`() {
        val authA = register("ts-riskcrossa@example.com")
        val authB = register("ts-riskcrossb@example.com")
        val setup = createSetupOk(authA.accessToken, longManualRequest())
        evaluateRisk(authB.accessToken, setup.id).andExpect { status { isNotFound() } }
    }

    @Test
    fun `evaluating risk after the stored account profile is archived returns 404`() {
        val auth = register("ts-riskarchived@example.com")
        val profile = createProfile(auth.accessToken)
        val setup = createSetupOk(auth.accessToken, longManualRequest(accountProfileId = profile.id))
        archiveProfile(auth.accessToken, profile.id)
        evaluateRisk(auth.accessToken, setup.id).andExpect { status { isNotFound() } }
    }

    @Test
    fun `risk evaluation works at any lifecycle state, including CLOSED`() {
        val auth = register("ts-riskanystate@example.com")
        val profile = createProfile(auth.accessToken)
        val setup = createSetupOk(auth.accessToken, longManualRequest(accountProfileId = profile.id))
        decideSetup(auth.accessToken, setup.id, TradeSetupDecisionRequest(TradeSetupDecision.ACCEPT, null))
        closeSetup(auth.accessToken, setup.id, TradeSetupClosureRequest(BigDecimal("112"), TradeSetupOutcome.WIN, null))

        evaluateRisk(auth.accessToken, setup.id).andExpect { status { isOk() } }
    }

    @Test
    fun `missing authentication is rejected with 401`() {
        mockMvc.post("/api/v1/trade-setups") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(longManualRequest())
        }.andExpect { status { isUnauthorized() } }
    }
}
