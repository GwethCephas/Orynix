package com.cephCoding.orynix.fundamentals.service

import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.fundamentals.domain.FundamentalAnalysisResult
import com.cephCoding.orynix.fundamentals.provider.FundamentalDataProvider
import org.springframework.stereotype.Service

@Service
class FundamentalAnalysisService(private val fundamentalDataProvider: FundamentalDataProvider) {

    fun getFundamentals(symbolRaw: String): FundamentalAnalysisResult {
        val symbol = validateSymbol(symbolRaw)
        val company = fundamentalDataProvider.fetchCompanyProfile(symbol)
        return FundamentalAnalysisResult(symbol = symbol, company = company)
    }

    private fun validateSymbol(symbolRaw: String): String {
        val symbol = symbolRaw.trim().uppercase()
        if (!SYMBOL_PATTERN.matches(symbol)) {
            throw InvalidSymbolException(symbolRaw)
        }
        return symbol
    }

    companion object {
        private val SYMBOL_PATTERN = Regex("^[A-Z.]{1,10}$")
    }
}
