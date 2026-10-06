package com.cephCoding.orynix.common

import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.EmailAlreadyExistsException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.common.exception.InvalidCredentialsException
import com.cephCoding.orynix.common.exception.InvalidRefreshTokenException
import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.common.exception.InvalidTimeframeException
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.NoSuchElementException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Invalid request" }
        return respond(HttpStatus.BAD_REQUEST, message, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, "Malformed request body", request)

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailExists(ex: EmailAlreadyExistsException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.CONFLICT, ex.message ?: "Email already registered", request)

    @ExceptionHandler(InvalidCredentialsException::class, InvalidRefreshTokenException::class)
    fun handleUnauthorized(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized", request)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Not found", request)

    @ExceptionHandler(CompanyNotFoundException::class)
    fun handleCompanyNotFound(ex: CompanyNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Company not found", request)

    @ExceptionHandler(InvalidSymbolException::class, InvalidTimeframeException::class)
    fun handleMarketDataValidation(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(MarketDataProviderException::class)
    fun handleMarketDataProviderFailure(ex: MarketDataProviderException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_GATEWAY, ex.message ?: "Market data provider request failed", request)

    @ExceptionHandler(FundamentalDataProviderException::class)
    fun handleFundamentalDataProviderFailure(ex: FundamentalDataProviderException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_GATEWAY, ex.message ?: "Fundamental data provider request failed", request)

    @ExceptionHandler(InsufficientCandleDataException::class)
    fun handleInsufficientCandleData(ex: InsufficientCandleDataException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "Insufficient candle data", request)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception processing ${request.requestURI}", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request)
    }

    private fun respond(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(status = status.value(), message = message, path = request.requestURI)
        )
}
