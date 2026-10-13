package com.cephCoding.orynix.backtesting.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * computeWarmupFromDate is a pure function of its inputs, verified independently of
 * FakeMarketDataProvider -- that fake ignores the from/to it is given (CLAUDE.md Section 18), so
 * this is the only place the actual warm-up date arithmetic is checked.
 */
class BacktestServiceWarmupTest {

    @Test
    fun `subtracts twice the warmup period plus a fixed buffer, in calendar days`() {
        val fromDate = LocalDate.of(2026, 6, 1)

        val result = BacktestService.computeWarmupFromDate(fromDate, warmupPeriod = 50)

        assertEquals(fromDate.minusDays(50 * 2L + 10L), result)
    }

    @Test
    fun `scales with the warmup period`() {
        val fromDate = LocalDate.of(2026, 6, 1)

        val small = BacktestService.computeWarmupFromDate(fromDate, warmupPeriod = 4)
        val large = BacktestService.computeWarmupFromDate(fromDate, warmupPeriod = 51)

        assertEquals(fromDate.minusDays(4 * 2L + 10L), small)
        assertEquals(fromDate.minusDays(51 * 2L + 10L), large)
    }
}
