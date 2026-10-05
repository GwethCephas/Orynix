package com.cephCoding.orynix.market.service

import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.model.MarketDataCandle
import com.cephCoding.orynix.market.provider.MarketDataProvider
import com.cephCoding.orynix.market.repository.MarketDataCandleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MarketDataService(
    private val marketDataProvider: MarketDataProvider,
    private val repository: MarketDataCandleRepository,
) {
    @Transactional
    fun getDailyCandles(symbolRaw: String, timeframeCode: String): List<Candle> {
        val symbol = validateSymbol(symbolRaw)
        val timeframe = CandleTimeframe.fromCode(timeframeCode)
        val to = LocalDate.now()
        val from = to.minusDays(LOOKBACK_DAYS)

        val fetched = marketDataProvider.fetchDailyCandles(symbol, from, to)

        val existingTimestamps = repository
            .findBySymbolAndTimeframeAndTimestampIn(symbol, timeframe.code, fetched.map { it.timestamp })
            .map { it.timestamp }
            .toSet()
        val newCandles = fetched.filterNot { it.timestamp in existingTimestamps }
        repository.saveAll(newCandles.map(MarketDataCandle::from))

        return fetched
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
