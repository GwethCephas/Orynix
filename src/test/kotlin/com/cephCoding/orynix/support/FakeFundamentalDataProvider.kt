package com.cephCoding.orynix.support

import com.cephCoding.orynix.fundamentals.domain.CompanyProfile
import com.cephCoding.orynix.fundamentals.provider.FundamentalDataProvider

class FakeFundamentalDataProvider : FundamentalDataProvider {
    var companyProfileToReturn: CompanyProfile? = null
    var failure: RuntimeException? = null

    override fun fetchCompanyProfile(symbol: String): CompanyProfile {
        failure?.let { throw it }
        return companyProfileToReturn
            ?: throw IllegalStateException("FakeFundamentalDataProvider.companyProfileToReturn not set")
    }

    fun reset() {
        companyProfileToReturn = null
        failure = null
    }
}
