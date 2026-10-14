package com.cephCoding.orynix.agents.provider.gemini.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Raw Gemini `generateContent` request shape - only the fields this feature actually sends are
 * modeled, matching MassiveBar's "only t,o,h,l,c,v modeled" precedent. Field names are already
 * camelCase in Gemini's real API (systemInstruction, generationConfig, responseMimeType,
 * responseSchema) - no @JsonProperty snake_case mapping needed, unlike the Massive DTOs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

data class GeminiPart(
    val text: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
    val responseSchema: GeminiSchema,
)

/**
 * A minimal subset of Gemini's OpenAPI-3.0-derived Schema object, just enough to constrain
 * ModelAnalysis's shape. Gemini's schema `type` values are uppercase strings (STRING/NUMBER/
 * ARRAY/OBJECT), not JSON Schema's lowercase convention - verified against Google's documented
 * Schema object shape, not assumed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val items: GeminiSchema? = null,
    val enum: List<String>? = null,
    val required: List<String>? = null,
    val nullable: Boolean? = null,
)

data class GeminiGenerateContentRequest(
    val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)
