package com.cephCoding.orynix.tradesetup.dto

import com.cephCoding.orynix.papertrading.domain.PaperOrder
import com.cephCoding.orynix.papertrading.dto.PaperOrderResponse
import com.cephCoding.orynix.tradesetup.domain.TradeSetup

/**
 * Both the resulting paper order (via PaperOrderResponse, unchanged - this feature calls
 * PaperTradingOrderService.submitOrder exactly as-is) and the updated trade setup (now carrying
 * the paperOrderId/paperAccountId link) in one response, so the frontend doesn't need a second
 * round-trip to see either.
 */
data class TradeSetupPaperOrderResponse(
    val tradeSetup: TradeSetupResponse,
    val order: PaperOrderResponse,
) {
    companion object {
        fun from(tradeSetup: TradeSetup, order: PaperOrder) = TradeSetupPaperOrderResponse(
            tradeSetup = TradeSetupResponse.from(tradeSetup),
            order = PaperOrderResponse.from(order),
        )
    }
}
