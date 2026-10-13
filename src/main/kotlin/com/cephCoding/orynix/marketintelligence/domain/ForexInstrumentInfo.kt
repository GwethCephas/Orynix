package com.cephCoding.orynix.marketintelligence.domain

/**
 * The forex analogue of `FundamentalAnalysisResult`/`CompanyProfile` — deliberately a distinct
 * type rather than an extension of `CompanyProfile`, since `FundamentalAnalysisService` stays
 * stock-only (see CLAUDE.md Section 9) and a currency pair has no exchange/industry/market-cap
 * equivalent to report. Built entirely from static, curated data (`ForexPairCatalog`) — there is
 * no Massive endpoint this could be sourced from.
 */
data class ForexInstrumentInfo(
    val displaySymbol: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val description: String,
)
