package com.cephCoding.orynix.common

import com.cephCoding.orynix.common.exception.BacktestNotFoundException
import com.cephCoding.orynix.common.exception.CompanyNotFoundException
import com.cephCoding.orynix.common.exception.EmailAlreadyExistsException
import com.cephCoding.orynix.common.exception.FundamentalDataProviderException
import com.cephCoding.orynix.common.exception.InvalidAccountCurrencyException
import com.cephCoding.orynix.common.exception.InvalidBacktestRangeException
import com.cephCoding.orynix.common.exception.InvalidCredentialsException
import com.cephCoding.orynix.common.exception.InvalidRefreshTokenException
import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.common.exception.InvalidStrategyParametersException
import com.cephCoding.orynix.common.exception.InvalidSymbolException
import com.cephCoding.orynix.common.exception.InvalidTimeframeException
import com.cephCoding.orynix.common.exception.InvalidNewsLimitException
import com.cephCoding.orynix.common.exception.MarketDataProviderException
import com.cephCoding.orynix.common.exception.NewsArticleNotFoundException
import com.cephCoding.orynix.common.exception.NewsDataProviderException
import com.cephCoding.orynix.common.exception.SentimentAnalyzerException
import com.cephCoding.orynix.common.exception.InsufficientBuyingPowerException
import com.cephCoding.orynix.common.exception.InvalidPaperOrderException
import com.cephCoding.orynix.common.exception.InvalidPaperPositionTransitionException
import com.cephCoding.orynix.common.exception.PaperPositionNotFoundException
import com.cephCoding.orynix.common.exception.PaperTradingAccountNotFoundException
import com.cephCoding.orynix.common.exception.PaperTradingPriceUnavailableException
import com.cephCoding.orynix.common.exception.StalePaperTradingPriceException
import com.cephCoding.orynix.common.exception.InvalidRiskCalculationException
import com.cephCoding.orynix.common.exception.InvalidTradingAccountProfileException
import com.cephCoding.orynix.common.exception.MarketAnalysisProviderException
import com.cephCoding.orynix.common.exception.MarketAnalysisResponseException
import com.cephCoding.orynix.common.exception.InvalidTradeSetupException
import com.cephCoding.orynix.common.exception.InvalidTradeSetupTransitionException
import com.cephCoding.orynix.common.exception.TradeSetupNotFoundException
import com.cephCoding.orynix.common.exception.TradeSetupConflictException
import com.cephCoding.orynix.common.exception.AiTradeSetupProposalNotFoundException
import com.cephCoding.orynix.common.exception.TradingAccountProfileNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
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

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, "Invalid request", request)

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

    @ExceptionHandler(InvalidNewsLimitException::class)
    fun handleInvalidNewsLimit(ex: InvalidNewsLimitException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(NewsDataProviderException::class)
    fun handleNewsDataProviderFailure(ex: NewsDataProviderException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_GATEWAY, ex.message ?: "News data provider request failed", request)

    @ExceptionHandler(NewsArticleNotFoundException::class)
    fun handleNewsArticleNotFound(ex: NewsArticleNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "News article not found", request)

    @ExceptionHandler(SentimentAnalyzerException::class)
    fun handleSentimentAnalyzerFailure(ex: SentimentAnalyzerException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_GATEWAY, ex.message ?: "Sentiment analyzer request failed", request)

    @ExceptionHandler(InvalidBacktestRangeException::class, InvalidStrategyParametersException::class, InvalidAccountCurrencyException::class)
    fun handleBacktestValidation(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(BacktestNotFoundException::class)
    fun handleBacktestNotFound(ex: BacktestNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Backtest not found", request)

    @ExceptionHandler(InvalidTradingAccountProfileException::class)
    fun handleInvalidTradingAccountProfile(ex: InvalidTradingAccountProfileException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(TradingAccountProfileNotFoundException::class)
    fun handleTradingAccountProfileNotFound(ex: TradingAccountProfileNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Trading account profile not found", request)

    @ExceptionHandler(InvalidRiskCalculationException::class)
    fun handleInvalidRiskCalculation(ex: InvalidRiskCalculationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(MarketAnalysisProviderException::class, MarketAnalysisResponseException::class)
    fun handleMarketAnalysisFailure(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_GATEWAY, ex.message ?: "Market analysis provider request failed", request)

    @ExceptionHandler(InvalidTradeSetupException::class, InvalidTradeSetupTransitionException::class)
    fun handleTradeSetupValidation(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(TradeSetupNotFoundException::class, AiTradeSetupProposalNotFoundException::class)
    fun handleTradeSetupNotFound(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Trade setup not found", request)

    @ExceptionHandler(TradeSetupConflictException::class)
    fun handleTradeSetupConflict(ex: TradeSetupConflictException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.CONFLICT, ex.message ?: "Conflict", request)

    @ExceptionHandler(InvalidPaperOrderException::class, InvalidPaperPositionTransitionException::class, InsufficientBuyingPowerException::class)
    fun handlePaperTradingValidation(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request", request)

    @ExceptionHandler(StalePaperTradingPriceException::class, PaperTradingPriceUnavailableException::class)
    fun handlePaperTradingPriceUnavailable(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.message ?: "No acceptable price is available", request)

    @ExceptionHandler(PaperTradingAccountNotFoundException::class, PaperPositionNotFoundException::class)
    fun handlePaperTradingNotFound(ex: RuntimeException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        respond(HttpStatus.NOT_FOUND, ex.message ?: "Not found", request)

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
