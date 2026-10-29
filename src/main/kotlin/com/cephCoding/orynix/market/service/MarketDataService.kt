package com.cephCoding.orynix.market.service

import com.cephCoding.orynix.common.exception.InvalidTimeframeRangeException
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.model.MarketDataCandle
import com.cephCoding.orynix.market.provider.MarketDataProvider
import com.cephCoding.orynix.market.repository.MarketDataCandleRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class MarketDataService(
    private val marketDataProvider: MarketDataProvider,
    private val repository: MarketDataCandleRepository,
) {
    /**
     * [InstrumentResolver] resolves both the asset type and the provider-specific ticker (e.g.
     * "EUR/USD" -> "C:EURUSD" for forex); the provider is called with `providerSymbol` but every
     * persisted/returned `Candle.symbol` is normalized back to `displaySymbol` so Massive's ticker
     * format never leaks past this method — see CLAUDE.md Section 6's forex extension.
     */
    fun getDailyCandles(symbolRaw: String, timeframeCode: String): List<Candle> {
        val instrument = InstrumentResolver.resolve(symbolRaw)
        val timeframe = CandleTimeframe.fromCode(timeframeCode)
        if (timeframe.isIntraday) {
            // The hardcoded 365-day window below is daily-only by design — it would mean an
            // unbounded, un-paginated-safe intraday fetch. Intraday timeframes must go through
            // getCandlesInRange with an explicit, range-validated [from, to].
            throw InvalidTimeframeRangeException(
                "Intraday timeframe '${timeframe.code}' requires an explicit date range"
            )
        }
        val to = LocalDate.now()
        val from = to.minusDays(LOOKBACK_DAYS)

        val fetched = marketDataProvider.fetchCandles(instrument.providerSymbol, from, to, timeframe)
            .map { it.copy(symbol = instrument.displaySymbol) }
        persistNewCandles(instrument.displaySymbol, timeframe, fetched)

        return fetched
    }

    /**
     * Caller-supplied date range — originally added for the backtesting engine (CLAUDE.md
     * Section 18, daily-only there), now also the sole path for intraday chart requests
     * (CLAUDE.md Section 6's timeframe extension). Unlike [getDailyCandles]'s hardcoded
     * trailing-365-day window, this fetches exactly the requested [from, to] span, every time
     * (same re-fetch-on-every-call behavior as [getDailyCandles] — this is not a DB-first read
     * either). Reuses [persistNewCandles] as-is.
     */
    fun getCandlesInRange(symbolRaw: String, timeframeCode: String, from: LocalDate, to: LocalDate): List<Candle> {
        val instrument = InstrumentResolver.resolve(symbolRaw)
        val timeframe = CandleTimeframe.fromCode(timeframeCode)
        validateIntradayRange(timeframe, from, to)

        val fetched = marketDataProvider.fetchCandles(instrument.providerSymbol, from, to, timeframe)
            .map { it.copy(symbol = instrument.displaySymbol) }
        persistNewCandles(instrument.displaySymbol, timeframe, fetched)

        return fetched
    }

    /**
     * Per-interval, not a single global constant — live-verified against the real Massive API
     * (2026-10-29): forex trades 24/5, so even a 1-week/15-minute request can approach a single
     * page's real bar count, and a US stock's extended-hours bars roughly doubled the naive
     * regular-session estimate at a 31-day/15-minute window (389+386 bars across 2 pages for a
     * single symbol). These caps keep the common case at one Massive page even after accounting
     * for that, while [MassiveMarketDataProvider]'s pagination-following is the actual
     * correctness backstop if a particular symbol still needs more than one page. The web
     * client's chart.js carries its own copy of this same cap (to disable incompatible period
     * buttons before a request is even made) — keep both in sync if either changes.
     */
    internal fun validateIntradayRange(timeframe: CandleTimeframe, from: LocalDate, to: LocalDate) {
        if (!timeframe.isIntraday) return
        val maxDays = MAX_INTRADAY_RANGE_DAYS_BY_TIMEFRAME[timeframe]
            ?: error("No intraday range cap configured for $timeframe")
        val requestedDays = ChronoUnit.DAYS.between(from, to)
        if (requestedDays > maxDays) {
            throw InvalidTimeframeRangeException(
                "Timeframe '${timeframe.code}' supports at most $maxDays days of history per request, got $requestedDays"
            )
        }
    }

    /**
     * Runs in its own transaction so a duplicate-key failure here (two requests racing to
     * persist the same brand-new symbol/timeframe for the first time) can be caught and
     * swallowed without aborting the caller's transaction. The (symbol, timeframe, timestamp)
     * unique constraint is the dedup backstop in that race — the losing request's candles are
     * already durably persisted by the winner, so there is nothing to recover; `getDailyCandles`
     * already returns the freshly fetched list regardless of what this method persists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persistNewCandles(symbol: String, timeframe: CandleTimeframe, fetched: List<Candle>) {
        val existingTimestamps = repository
            .findBySymbolAndTimeframeAndTimestampIn(symbol, timeframe.code, fetched.map { it.timestamp })
            .map { it.timestamp }
            .toSet()
        val newCandles = fetched.filterNot { it.timestamp in existingTimestamps }
        try {
            repository.saveAllAndFlush(newCandles.map(MarketDataCandle::from))
        } catch (ex: DataIntegrityViolationException) {
            // Flushed eagerly above specifically so this constraint violation surfaces here,
            // inside the try, rather than at commit time after this method has already returned.
        }
    }

    companion object {
        private const val LOOKBACK_DAYS = 365L

        private val MAX_INTRADAY_RANGE_DAYS_BY_TIMEFRAME: Map<CandleTimeframe, Long> = mapOf(
            CandleTimeframe.MINUTE_15 to 7L,
            CandleTimeframe.MINUTE_30 to 7L,
            CandleTimeframe.HOUR_1 to 31L,
            CandleTimeframe.HOUR_4 to 31L,
        )
    }
}
