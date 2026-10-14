package com.cephCoding.orynix.backtesting.domain

enum class ExitReason {
    SIGNAL,
    END_OF_PERIOD,

    // Triggered intrabar when price breaches the ATR-based stop (initial or trailed) - a single
    // value is sufficient because the trailing stop only ever ratchets upward, so comparing a
    // trade's initialStopPrice/finalStopPrice already recovers whether (and how far) it trailed
    // before the exit, without needing separate STOP_LOSS/TRAILING_STOP reasons.
    ATR_STOP,
}
