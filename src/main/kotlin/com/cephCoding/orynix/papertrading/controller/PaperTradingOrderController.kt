package com.cephCoding.orynix.papertrading.controller

import com.cephCoding.orynix.papertrading.dto.ClosePositionRequest
import com.cephCoding.orynix.papertrading.dto.PagedResponse
import com.cephCoding.orynix.papertrading.dto.PaperLedgerEntryResponse
import com.cephCoding.orynix.papertrading.dto.PaperOrderResponse
import com.cephCoding.orynix.papertrading.dto.PaperPositionResponse
import com.cephCoding.orynix.papertrading.dto.SubmitPaperOrderRequest
import com.cephCoding.orynix.papertrading.service.PaperTradingOrderService
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Market orders, positions, and the ledger - see CLAUDE.md Section 22. Every list endpoint that
 * can grow unbounded (orders, closed positions, ledger entries) is paginated; open positions is
 * not, matching the small-bounded-dataset precedent that TradingAccountProfile/TradeSetup list
 * endpoints already use unpaginated (an account can hold at most one open position per
 * instrument, so this list is inherently small).
 */
@RestController
@RequestMapping("/api/v1/paper-trading/accounts/{accountId}")
class PaperTradingOrderController(private val orderService: PaperTradingOrderService) {

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    fun submitOrder(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
        @Valid @RequestBody request: SubmitPaperOrderRequest,
    ): PaperOrderResponse =
        PaperOrderResponse.from(orderService.submitOrder(userId, accountId, request))

    @GetMapping("/orders")
    fun listOrders(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PagedResponse<PaperOrderResponse> =
        PagedResponse.from(orderService.listOrders(userId, accountId, pageable), PaperOrderResponse::from)

    @PostMapping("/positions/{positionId}/close")
    @ResponseStatus(HttpStatus.CREATED)
    fun closePosition(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
        @PathVariable positionId: UUID,
        @Valid @RequestBody request: ClosePositionRequest,
    ): PaperOrderResponse =
        PaperOrderResponse.from(orderService.closePosition(userId, accountId, positionId, request))

    @GetMapping("/positions")
    fun listOpenPositions(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
    ): List<PaperPositionResponse> =
        orderService.listOpenPositions(userId, accountId).map { (position, valuation) -> PaperPositionResponse.from(position, valuation) }

    @GetMapping("/positions/closed")
    fun listClosedPositions(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PagedResponse<PaperPositionResponse> =
        PagedResponse.from(orderService.listClosedPositions(userId, accountId, pageable)) { PaperPositionResponse.from(it, null) }

    @GetMapping("/ledger")
    fun listLedgerEntries(
        @AuthenticationPrincipal userId: UUID,
        @PathVariable accountId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PagedResponse<PaperLedgerEntryResponse> =
        PagedResponse.from(orderService.listLedgerEntries(userId, accountId, pageable), PaperLedgerEntryResponse::from)
}
