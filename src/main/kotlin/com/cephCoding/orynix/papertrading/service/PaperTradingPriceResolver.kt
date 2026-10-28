package com.cephCoding.orynix.papertrading.service

import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.common.exception.PaperTradingPriceUnavailableException
import com.cephCoding.orynix.common.exception.StalePaperTradingPriceException
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.service.MarketDataService
import com.cephCoding.orynix.papertrading.domain.ResolvedPrice
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * The only place paper trading talks to MarketDataService - see CLAUDE.md Section 22. Orynix has
 * no live quote feed, only daily OHLCV bars; every price this resolver returns is honestly the
 * latest *available* daily close, which may be from a prior trading day. `resolveForOrder`
 * applies a hard freshness gate (no fill without an acceptable price); `resolveLatest` is the
 * soft path used for valuation, which never throws and lets the caller decide how to disclose
 * staleness per position rather than failing the whole account summary.
 */
@Service
class PaperTradingPriceResolver(
    private val marketDataService: MarketDataService,
    private val properties: PaperTradingProperties,
) {
    // Never throws - a provider failure here must degrade to "no price available" for the one
    // affected symbol, not fail an entire account valuation that may cover several positions
    // (see CLAUDE.md Section 22's soft-degradation rule). resolveForOrder below is the one place
    // this distinction is escalated back into a hard failure.
    fun resolveLatest(instrument: Instrument): ResolvedPrice? {
        val candles = try {
            marketDataService.getDailyCandles(instrument.displaySymbol, CandleTimeframe.DAILY.code)
        } catch (ex: MarketDataProviderException) {
            return null
        }
        val latest = candles.maxByOrNull { it.timestamp } ?: return null
        val now = Instant.now()
        val ageDays = Duration.between(latest.timestamp, now).toDays().coerceAtLeast(0)
        return ResolvedPrice(price = latest.close, candleTimestamp = latest.timestamp, resolvedAt = now, ageDays = ageDays)
    }

    fun resolveForOrder(instrument: Instrument): ResolvedPrice {
        val resolved = resolveLatest(instrument) ?: throw PaperTradingPriceUnavailableException(instrument.displaySymbol)
        if (resolved.ageDays > properties.maxPriceStalenessDays) {
            throw StalePaperTradingPriceException(instrument.displaySymbol, resolved.ageDays, properties.maxPriceStalenessDays)
        }
        return resolved
    }

    fun buildExecutionAssumptions(resolved: ResolvedPrice): String =
        if (resolved.ageDays <= 0) {
            "Filled at the latest available daily close."
        } else {
            "Filled at the latest available daily close, which may be from a prior trading day (${resolved.ageDays} calendar day(s) old)."
        }
}
