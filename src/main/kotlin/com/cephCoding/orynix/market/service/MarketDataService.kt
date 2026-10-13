package com.cephCoding.orynix.market.service

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
        val to = LocalDate.now()
        val from = to.minusDays(LOOKBACK_DAYS)

        val fetched = marketDataProvider.fetchDailyCandles(instrument.providerSymbol, from, to)
            .map { it.copy(symbol = instrument.displaySymbol) }
        persistNewCandles(instrument.displaySymbol, timeframe, fetched)

        return fetched
    }

    /**
     * Caller-supplied date range, for the backtesting engine (CLAUDE.md Section 18) — the only
     * current caller. Unlike [getDailyCandles]'s hardcoded trailing-365-day window, this fetches
     * exactly the requested [from, to] span, every time (same re-fetch-on-every-call behavior as
     * [getDailyCandles] — this is not a DB-first read either). Reuses [persistNewCandles] as-is.
     */
    fun getCandlesInRange(symbolRaw: String, timeframeCode: String, from: LocalDate, to: LocalDate): List<Candle> {
        val instrument = InstrumentResolver.resolve(symbolRaw)
        val timeframe = CandleTimeframe.fromCode(timeframeCode)

        val fetched = marketDataProvider.fetchDailyCandles(instrument.providerSymbol, from, to)
            .map { it.copy(symbol = instrument.displaySymbol) }
        persistNewCandles(instrument.displaySymbol, timeframe, fetched)

        return fetched
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
    }
}
