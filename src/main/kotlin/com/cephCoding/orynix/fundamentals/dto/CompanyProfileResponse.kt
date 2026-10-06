package com.cephCoding.orynix.fundamentals.dto

import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import java.math.BigDecimal

data class CompanyProfileResponse(
    val name: String,
    val exchange: String?,
    val industry: String?,
    val marketCapitalization: BigDecimal?,
) {
    companion object {
        fun from(company: CompanyProfile) = CompanyProfileResponse(
            name = company.name,
            exchange = company.exchange,
            industry = company.industry,
            marketCapitalization = company.marketCapitalization,
        )
    }
}
