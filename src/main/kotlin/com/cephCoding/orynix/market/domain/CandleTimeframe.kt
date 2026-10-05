package com.cephCoding.orynix.market.domain

import com.cephCoding.orynix.common.exception.InvalidTimeframeException

enum class CandleTimeframe(val code: String) {
    DAILY("1d");

    companion object {
        fun fromCode(code: String): CandleTimeframe =
            entries.firstOrNull { it.code == code } ?: throw InvalidTimeframeException(code)
    }
}
