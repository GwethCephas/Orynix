package com.cephCoding.orynix.signals.dto

import com.cephCoding.orynix.signals.domain.Signal
import java.math.BigDecimal

data class SignalResponse(
    val type: String,
    val state: String,
    val value: BigDecimal?,
) {
    companion object {
        fun from(signal: Signal) = SignalResponse(
            type = signal.type.name,
            state = signal.state,
            value = signal.value,
        )
    }
}
