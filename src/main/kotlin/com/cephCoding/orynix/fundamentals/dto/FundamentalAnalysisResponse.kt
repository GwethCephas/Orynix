package com.cephCoding.orynix.fundamentals.dto

import com.cephCoding.orynix.fundamentals.domain.FundamentalAnalysisResult

data class FundamentalAnalysisResponse(
    val symbol: String,
    val company: CompanyProfileResponse,
) {
    companion object {
        fun from(result: FundamentalAnalysisResult) = FundamentalAnalysisResponse(
            symbol = result.symbol,
            company = CompanyProfileResponse.from(result.company),
        )
    }
}
