package com.cephCoding.orynix.marketintelligence.dto

import com.cephCoding.orynix.marketintelligence.domain.ForexInstrumentInfo

data class ForexInstrumentInfoResponse(
    val displaySymbol: String,
    val baseCurrency: String,
    val quoteCurrency: String,
    val description: String,
) {
    companion object {
        fun from(info: ForexInstrumentInfo) = ForexInstrumentInfoResponse(
            displaySymbol = info.displaySymbol,
            baseCurrency = info.baseCurrency,
            quoteCurrency = info.quoteCurrency,
            description = info.description,
        )
    }
}
