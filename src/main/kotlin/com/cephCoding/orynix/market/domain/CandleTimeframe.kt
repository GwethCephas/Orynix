package com.cephCoding.orynix.market.domain

import com.cephCoding.orynix.common.exception.InvalidTimeframeException

/**
 * massiveMultiplier/massiveTimespan map directly onto Massive's
 * GET /v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to} shape.
 * maxIntradayRangeDays is null for DAILY (no intraday range cap applies); for the intraday
 * values it is a live-verified, conservative single-Massive-page bound - see
 * MarketDataService.MAX_INTRADAY_RANGE_DAYS_BY_TIMEFRAME for the cross-referenced source of truth
 * the web client's own copy of this cap must stay in sync with (web/chart.js).
 */
enum class CandleTimeframe(
    val code: String,
    val massiveMultiplier: Int,
    val massiveTimespan: String,
) {
    DAILY("1d", 1, "day"),
    MINUTE_15("15m", 15, "minute"),
    MINUTE_30("30m", 30, "minute"),
    HOUR_1("1h", 1, "hour"),
    HOUR_4("4h", 4, "hour");

    val isIntraday: Boolean get() = this != DAILY

    companion object {
        fun fromCode(code: String): CandleTimeframe =
            entries.firstOrNull { it.code == code } ?: throw InvalidTimeframeException(code)
    }
}
