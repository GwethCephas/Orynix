package com.cephCoding.orynix.backtesting.dto

import com.cephCoding.orynix.backtesting.domain.EquityPoint
import com.cephCoding.orynix.backtesting.domain.Trade

data class BacktestDetailResponse(
    val trades: List<TradeResponse>,
    val equityCurve: List<EquityPointResponse>,
) {
    companion object {
        fun from(trades: List<Trade>, equityCurve: List<EquityPoint>) = BacktestDetailResponse(
            trades = trades.map(TradeResponse::from),
            equityCurve = equityCurve.map(EquityPointResponse::from),
        )
    }
}
