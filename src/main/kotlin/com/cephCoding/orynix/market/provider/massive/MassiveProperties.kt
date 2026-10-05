package com.cephCoding.orynix.market.provider.massive

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "massive")
data class MassiveProperties(
    val baseUrl: String,
    val apiKey: String,
)
