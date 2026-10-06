package com.cephCoding.orynix.fundamentals.domain

import java.math.BigDecimal

data class CompanyProfile(
    val name: String,
    val exchange: String?,
    val industry: String?,
    val marketCapitalization: BigDecimal?,
)
