package com.cephCoding.orynix.fundamentals.provider.massive.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class MassiveTickerDetails(
    val name: String?,
    @JsonProperty("primary_exchange") val primaryExchange: String?,
    @JsonProperty("sic_description") val sicDescription: String?,
    @JsonProperty("market_cap") val marketCap: BigDecimal?,
)
