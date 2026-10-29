package com.cephCoding.orynix.market.domain

import com.cephCoding.orynix.common.exception.InvalidTimeframeException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CandleTimeframeTest {

    @Test
    fun `each timeframe maps to the correct Massive multiplier and timespan`() {
        assert(CandleTimeframe.DAILY.massiveMultiplier == 1 && CandleTimeframe.DAILY.massiveTimespan == "day")
        assert(CandleTimeframe.MINUTE_15.massiveMultiplier == 15 && CandleTimeframe.MINUTE_15.massiveTimespan == "minute")
        assert(CandleTimeframe.MINUTE_30.massiveMultiplier == 30 && CandleTimeframe.MINUTE_30.massiveTimespan == "minute")
        assert(CandleTimeframe.HOUR_1.massiveMultiplier == 1 && CandleTimeframe.HOUR_1.massiveTimespan == "hour")
        assert(CandleTimeframe.HOUR_4.massiveMultiplier == 4 && CandleTimeframe.HOUR_4.massiveTimespan == "hour")
    }

    @Test
    fun `only DAILY is not intraday`() {
        assert(!CandleTimeframe.DAILY.isIntraday)
        assert(CandleTimeframe.MINUTE_15.isIntraday)
        assert(CandleTimeframe.MINUTE_30.isIntraday)
        assert(CandleTimeframe.HOUR_1.isIntraday)
        assert(CandleTimeframe.HOUR_4.isIntraday)
    }

    @Test
    fun `fromCode resolves every supported code`() {
        assert(CandleTimeframe.fromCode("1d") == CandleTimeframe.DAILY)
        assert(CandleTimeframe.fromCode("15m") == CandleTimeframe.MINUTE_15)
        assert(CandleTimeframe.fromCode("30m") == CandleTimeframe.MINUTE_30)
        assert(CandleTimeframe.fromCode("1h") == CandleTimeframe.HOUR_1)
        assert(CandleTimeframe.fromCode("4h") == CandleTimeframe.HOUR_4)
    }

    @Test
    fun `fromCode rejects an unknown code`() {
        assertThrows(InvalidTimeframeException::class.java) {
            CandleTimeframe.fromCode("bogus")
        }
    }
}
