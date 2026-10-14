package com.cephCoding.orynix.backtesting

import com.cephCoding.orynix.backtesting.domain.PositionSizingType
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.dto.BacktestResponse
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.support.AbstractIntegrationTest
import com.cephCoding.orynix.tradingaccount.domain.TradingPlatform
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileRequest
import com.cephCoding.orynix.tradingaccount.dto.TradingAccountProfileResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Covers CLAUDE.md Section 19's backtest-profile precedence rule and backward compatibility -
 * kept as its own file (not appended to BacktestControllerTest) since it needs its own
 * trading-account-profile helper, and BacktestControllerTest's own helpers are private to that
 * file. Flat (never-crossing) candles are used throughout specifically so assertions can focus on
 * initialCapital/finalEquity without needing a real trade to also verify.
 */
class BacktestAccountProfileIntegrationTest : AbstractIntegrationTest() {

    private fun candle(symbol: String, day: Int): Candle = Candle(
        symbol = symbol,
        timeframe = CandleTimeframe.DAILY,
        timestamp = Instant.parse("2026-01-01T00:00:00Z").plus(day.toLong(), ChronoUnit.DAYS),
        open = BigDecimal("100"),
        high = BigDecimal("101"),
        low = BigDecimal("99"),
        close = BigDecimal("100"),
        volume = BigDecimal("1000000"),
    )

    private fun flatCandles(symbol: String, count: Int): List<Candle> = (0 until count).map { candle(symbol, it) }

    private fun baseRequest(
        symbol: String = "BTEST",
        accountProfileId: UUID? = null,
        initialCapital: BigDecimal? = BigDecimal("1000"),
    ) = BacktestRequest(
        symbol = symbol,
        accountProfileId = accountProfileId,
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
        initialCapital = initialCapital,
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

    private fun postBacktest(token: String, request: BacktestRequest) = mockMvc.post("/api/v1/backtests") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(request)
        header("Authorization", "Bearer $token")
    }

    private fun createProfile(token: String, balance: BigDecimal, accountCurrency: String = "USD"): TradingAccountProfileResponse {
        val request = TradingAccountProfileRequest(
            name = "Backtest profile",
            platform = TradingPlatform.OTHER,
            platformDetails = null,
            accountCurrency = accountCurrency,
            balance = balance,
            riskPercentPerTrade = null,
            maxDailyLossPercent = null,
            maxConcurrentPositions = null,
        )
        val response = mockMvc.post("/api/v1/trading-accounts") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
            header("Authorization", "Bearer $token")
        }.andReturn().response
        assertEquals(201, response.status)
        return objectMapper.readValue(response.contentAsString, TradingAccountProfileResponse::class.java)
    }

    @Test
    fun `a request with no accountProfileId and no initialCapital is rejected with 400 (backward-compatible requiredness)`() {
        val auth = register("btap-neither@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)

        postBacktest(auth.accessToken, baseRequest(initialCapital = null))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a request with only initialCapital and no profile behaves exactly as before`() {
        val auth = register("btap-noprofile@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)

        val response = postBacktest(auth.accessToken, baseRequest()).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)
        assertEquals(BigDecimal("1000"), body.initialCapital)
        assertEquals(null, body.accountProfileId)
    }

    @Test
    fun `a selected profile seeds initialCapital when the request omits it`() {
        val auth = register("btap-profileseeds@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)
        val profile = createProfile(auth.accessToken, BigDecimal("5000"))

        val response = postBacktest(auth.accessToken, baseRequest(accountProfileId = profile.id, initialCapital = null)).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)
        assertEquals(0, BigDecimal("5000").compareTo(body.initialCapital))
        assertEquals(profile.id, body.accountProfileId)
    }

    @Test
    fun `an explicit initialCapital wins over the selected profile's balance`() {
        val auth = register("btap-explicitwins@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)
        val profile = createProfile(auth.accessToken, BigDecimal("5000"))

        val response = postBacktest(auth.accessToken, baseRequest(accountProfileId = profile.id, initialCapital = BigDecimal("2000"))).andReturn().response
        assertEquals(201, response.status)
        val body = objectMapper.readValue(response.contentAsString, BacktestResponse::class.java)
        assertEquals(0, BigDecimal("2000").compareTo(body.initialCapital))
        assertEquals(profile.id, body.accountProfileId)
    }

    @Test
    fun `a zero-balance profile cannot seed initialCapital and is rejected with 400`() {
        val auth = register("btap-zerobalance@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)
        val profile = createProfile(auth.accessToken, BigDecimal("0"))

        postBacktest(auth.accessToken, baseRequest(accountProfileId = profile.id, initialCapital = null))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a profile whose currency does not match the backtest's required accountCurrency is rejected with 400`() {
        val auth = register("btap-currencymismatch@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)
        val eurProfile = createProfile(auth.accessToken, BigDecimal("5000"), accountCurrency = "EUR")

        // BTEST resolves as a STOCK, which requires accountCurrency = USD - the EUR-denominated
        // profile must never silently seed initialCapital under a mismatched currency.
        postBacktest(auth.accessToken, baseRequest(accountProfileId = eurProfile.id, initialCapital = null))
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an archived profile can no longer seed a new backtest's initialCapital`() {
        val auth = register("btap-archived@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)
        val profile = createProfile(auth.accessToken, BigDecimal("5000"))

        mockMvc.delete("/api/v1/trading-accounts/${profile.id}") {
            header("Authorization", "Bearer ${auth.accessToken}")
        }.andExpect { status { isNoContent() } }

        postBacktest(auth.accessToken, baseRequest(accountProfileId = profile.id, initialCapital = null))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `referencing another user's profile returns 404`() {
        val authA = register("btap-ownera@example.com")
        val authB = register("btap-ownerb@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)
        val profile = createProfile(authA.accessToken, BigDecimal("5000"))

        postBacktest(authB.accessToken, baseRequest(accountProfileId = profile.id, initialCapital = null))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `referencing a nonexistent profile id returns 404`() {
        val auth = register("btap-missingprofile@example.com")
        fakeMarketDataProvider.candlesToReturn = flatCandles("BTEST", 10)

        postBacktest(auth.accessToken, baseRequest(accountProfileId = UUID.randomUUID(), initialCapital = null))
            .andExpect { status { isNotFound() } }
    }
}
