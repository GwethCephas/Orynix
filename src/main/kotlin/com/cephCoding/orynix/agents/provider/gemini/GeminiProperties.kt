package com.cephCoding.orynix.agents.provider.gemini

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutMs: Long,
)
