package com.cephCoding.orynix.tradesetup.domain

/**
 * Feature-local, like agents.domain.TradeDirection and backtesting.domain.TradeDirection - each
 * independent since each feature's own direction concept has a different scope (agents' model
 * proposes either; backtesting is long-only; this feature persists either, matching agents').
 */
enum class TradeDirection { LONG, SHORT }
