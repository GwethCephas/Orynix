package com.cephCoding.orynix.fundamentals

import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.fundamentals.service.FundamentalAnalysisService
import com.cephCoding.orynix.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

class FundamentalAnalysisServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var fundamentalAnalysisService: FundamentalAnalysisService

    @Test
    fun `valid symbol returns the normalized company profile`() {
        fakeFundamentalDataProvider.companyProfileToReturn = CompanyProfile(
            name = "Apple Inc.",
            exchange = "XNAS",
            industry = "ELECTRONIC COMPUTERS",
            marketCapitalization = BigDecimal("4858256580200"),
        )

        val result = fundamentalAnalysisService.getFundamentals("aapl")

        assertEquals("AAPL", result.symbol)
        assertEquals("Apple Inc.", result.company.name)
        assertEquals("XNAS", result.company.exchange)
    }

    @Test
    fun `invalid symbol throws InvalidSymbolException`() {
        assertThrows(InvalidSymbolException::class.java) {
            fundamentalAnalysisService.getFundamentals("this-is-not-a-ticker-123")
        }
    }
}
