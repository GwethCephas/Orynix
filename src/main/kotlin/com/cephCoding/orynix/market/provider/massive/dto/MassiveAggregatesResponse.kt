package com.cephCoding.orynix.market.provider.massive.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class MassiveAggregatesResponse(
    val ticker: String?,
    val status: String?,
    val resultsCount: Int?,
    val results: List<MassiveBar>?,
    @JsonProperty("next_url") val nextUrl: String?,
)
