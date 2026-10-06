package com.cephCoding.orynix.fundamentals.provider

import com.cephCoding.orynix.fundamentals.domain.CompanyProfile

interface FundamentalDataProvider {
    fun fetchCompanyProfile(symbol: String): CompanyProfile
}
