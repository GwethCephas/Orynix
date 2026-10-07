package com.cephCoding.orynix.market.service

import com.cephCoding.orynix.common.exception.InvalidSymbolException
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
    fun getDailyCandles(symbolRaw: String, timeframeCode: String): List<Candle> {
        val symbol = validateSymbol(symbolRaw)
        val timeframe = CandleTimeframe.fromCode(timeframeCode)
        val to = LocalDate.now()
        val from = to.minusDays(LOOKBACK_DAYS)

        val fetched = marketDataProvider.fetchDailyCandles(symbol, from, to)
        persistNewCandles(symbol, timeframe, fetched)

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

    private fun validateSymbol(symbolRaw: String): String {
        val symbol = symbolRaw.trim().uppercase()
        if (!SYMBOL_PATTERN.matches(symbol)) {
            throw InvalidSymbolException(symbolRaw)
        }
        return symbol
    }

    companion object {
        private const val LOOKBACK_DAYS = 365L
        private val SYMBOL_PATTERN = Regex("^[A-Z.]{1,10}$")
    }
}
