package com.cephCoding.orynix.signals.domain

import java.math.BigDecimal

data class Signal(
    val type: SignalType,
    val state: String,
    val value: BigDecimal?,
)
