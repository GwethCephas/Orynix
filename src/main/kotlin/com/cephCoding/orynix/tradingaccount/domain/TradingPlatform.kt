package com.cephCoding.orynix.tradingaccount.domain

/**
 * Purely descriptive metadata the user selects for their own reference - there is no broker
 * integration anywhere in this codebase, so none of these values drive any API call or
 * credential. OTHER exists so the curated list never blocks a profile the user still wants to
 * record; platformDetails (free text) carries anything a fixed enum can't.
 */
enum class TradingPlatform {
    METATRADER_4,
    METATRADER_5,
    CTRADER,
    TRADINGVIEW,
    INTERACTIVE_BROKERS,
    TD_AMERITRADE,
    OANDA,
    OTHER,
}
