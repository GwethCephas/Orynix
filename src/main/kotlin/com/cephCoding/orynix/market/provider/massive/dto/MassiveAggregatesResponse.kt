package com.cephCoding.orynix.market.provider.massive.dto

data class MassiveAggregatesResponse(
    val ticker: String?,
    val status: String?,
    val resultsCount: Int?,
    val results: List<MassiveBar>?,
)
