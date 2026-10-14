package com.cephCoding.orynix.backtesting.service

import com.cephCoding.orynix.backtesting.domain.BacktestRun
import com.cephCoding.orynix.backtesting.domain.EquityPoint
import com.cephCoding.orynix.backtesting.domain.PositionSizingType
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.domain.Trade
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.engine.BacktestEngine
import com.cephCoding.orynix.backtesting.engine.RiskManagementConfig
import com.cephCoding.orynix.backtesting.model.BacktestEquityPointEntity
import com.cephCoding.orynix.backtesting.model.BacktestRunEntity
import com.cephCoding.orynix.backtesting.model.BacktestTradeEntity
import com.cephCoding.orynix.backtesting.repository.BacktestEquityPointRepository
import com.cephCoding.orynix.backtesting.repository.BacktestRunRepository
import com.cephCoding.orynix.backtesting.repository.BacktestTradeRepository
import com.cephCoding.orynix.backtesting.strategy.AdxTrendStrategy
import com.cephCoding.orynix.backtesting.strategy.BacktestStrategy
import com.cephCoding.orynix.backtesting.strategy.BollingerBandsMeanReversionStrategy
import com.cephCoding.orynix.backtesting.strategy.DonchianChannelBreakoutStrategy
import com.cephCoding.orynix.backtesting.strategy.MacdCrossoverStrategy
import com.cephCoding.orynix.backtesting.strategy.MovingAverageCrossoverStrategy
import com.cephCoding.orynix.backtesting.strategy.RsiThresholdStrategy
import com.cephCoding.orynix.common.exception.BacktestNotFoundException
import com.cephCoding.orynix.common.exception.InsufficientCandleDataException
import com.cephCoding.orynix.common.exception.InvalidAccountCurrencyException
import com.cephCoding.orynix.common.exception.InvalidBacktestRangeException
import com.cephCoding.orynix.common.exception.InvalidStrategyParametersException
import com.cephCoding.orynix.instrument.InstrumentResolver
import com.cephCoding.orynix.instrument.domain.AssetType
import com.cephCoding.orynix.instrument.domain.Instrument
import com.cephCoding.orynix.market.domain.Candle
import com.cephCoding.orynix.market.domain.CandleTimeframe
import com.cephCoding.orynix.market.service.MarketDataService
import com.cephCoding.orynix.tradingaccount.service.TradingAccountService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Deliberately not class/method-level @Transactional on createBacktest itself: it makes an
 * external HTTP call (via MarketDataService) before anything is persisted, and holding a
 * transaction open across that call would be wrong, matching MarketDataService.getDailyCandles
 * own precedent of leaving the HTTP-performing method untransactional and scoping @Transactional
 * only to the persist step that follows it.
 *
 * No status/error_message column anywhere (see CLAUDE.md Section 18): every validation failure
 * below, and any market-data provider failure inside getCandlesInRange, propagates before a
 * BacktestRun ever exists, so nothing is left in an inconsistent persisted state.
 */
@Service
class BacktestService(
    private val marketDataService: MarketDataService,
    private val tradingAccountService: TradingAccountService,
    private val backtestRunRepository: BacktestRunRepository,
    private val backtestTradeRepository: BacktestTradeRepository,
    private val backtestEquityPointRepository: BacktestEquityPointRepository,
) {

    fun createBacktest(userId: UUID, request: BacktestRequest): BacktestRun {
        validateRange(request.fromDate, request.toDate)
        val parameters = validateStrategyParameters(request)
        val riskParams = validateRiskManagementParameters(request)
        val instrument = InstrumentResolver.resolve(request.symbol)
        validateAccountCurrency(instrument, request.accountCurrency)
        val initialCapital = resolveInitialCapital(userId, request, request.accountCurrency)

        val warmupPeriod = warmupPeriod(request.strategyType, parameters, riskParams.riskManagementConfig)
        val warmupFromDate = computeWarmupFromDate(request.fromDate, warmupPeriod)

        val candles = marketDataService
            .getCandlesInRange(request.symbol, CandleTimeframe.DAILY.code, warmupFromDate, request.toDate)
            .sortedBy { it.timestamp }

        val fromInstant = request.fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val firstTradableIndex = candles.indexOfFirst { !it.timestamp.isBefore(fromInstant) }
        if (firstTradableIndex < 0 || firstTradableIndex < warmupPeriod) {
            throw InsufficientCandleDataException(warmupPeriod, if (firstTradableIndex < 0) 0 else firstTradableIndex)
        }

        val strategy = buildStrategy(request.strategyType, parameters, candles)

        val fixedQuantity = (riskParams.positionSizing as? PositionSizingKind.FixedQuantity)?.quantity
        val riskPercent = (riskParams.positionSizing as? PositionSizingKind.RiskBased)?.riskPercent

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = firstTradableIndex,
            strategy = strategy,
            initialCapital = initialCapital,
            quantity = fixedQuantity,
            commissionPerOrder = request.commissionPerOrder,
            slippageBps = request.slippageBps,
            riskPercentPerTrade = riskPercent,
            riskManagementConfig = riskParams.riskManagementConfig,
        )

        val config = riskParams.riskManagementConfig
        val run = BacktestRun(
            id = UUID.randomUUID(),
            accountProfileId = request.accountProfileId,
            symbol = instrument.displaySymbol,
            assetType = instrument.assetType,
            timeframe = CandleTimeframe.DAILY.code,
            fromDate = request.fromDate,
            toDate = request.toDate,
            strategyType = request.strategyType,
            fastPeriod = parameters.fastPeriod,
            slowPeriod = parameters.slowPeriod,
            rsiPeriod = parameters.rsiPeriod,
            oversoldThreshold = parameters.oversoldThreshold,
            overboughtThreshold = parameters.overboughtThreshold,
            signalPeriod = parameters.signalPeriod,
            bollingerPeriod = parameters.bollingerPeriod,
            bollingerStdDevMultiplier = parameters.bollingerStdDevMultiplier,
            donchianEntryPeriod = parameters.donchianEntryPeriod,
            donchianExitPeriod = parameters.donchianExitPeriod,
            adxPeriod = parameters.adxPeriod,
            adxThreshold = parameters.adxThreshold,
            direction = TradeDirection.LONG,
            initialCapital = initialCapital,
            accountCurrency = request.accountCurrency,
            positionSizingType = request.positionSizingType,
            quantity = fixedQuantity,
            commissionPerOrder = request.commissionPerOrder,
            slippageBps = request.slippageBps,
            completedAt = Instant.now(),
            totalTrades = result.totalTrades,
            winningTrades = result.winningTrades,
            losingTrades = result.losingTrades,
            breakevenTrades = result.breakevenTrades,
            winRate = result.winRate,
            profitFactor = result.profitFactor,
            netPnl = result.netPnl,
            netReturnPercent = result.netReturnPercent,
            maxDrawdownPercent = result.maxDrawdownPercent,
            finalEquity = result.finalEquity,
            candlesEvaluated = result.candlesEvaluated,
            entrySignalsGenerated = result.entrySignalsGenerated,
            exitSignalsGenerated = result.exitSignalsGenerated,
            entriesSkippedInsufficientCash = result.entriesSkippedInsufficientCash,
            entriesSkippedInvalidQuantity = result.entriesSkippedInvalidQuantity,
            riskPercentPerTrade = riskPercent,
            atrRiskManagementEnabled = config != null,
            atrPeriod = config?.atrPeriod,
            atrInitialStopMultiplier = config?.initialStopMultiplier,
            atrTrailingStopEnabled = config?.trailingEnabled ?: false,
            atrTrailingStopMultiplier = if (config?.trailingEnabled == true) config.trailingStopMultiplier else null,
        )

        persist(userId, run, result.trades, result.equityCurve)

        return run
    }

    fun listBacktests(userId: UUID): List<BacktestRun> =
        backtestRunRepository.findAllByUserIdOrderByCompletedAtDesc(userId).map { it.toDomain() }

    fun getBacktest(userId: UUID, id: UUID): BacktestRun =
        findOwned(userId, id).toDomain()

    fun getBacktestDetail(userId: UUID, id: UUID): Pair<List<Trade>, List<EquityPoint>> {
        findOwned(userId, id)
        val trades = backtestTradeRepository.findByBacktestRunIdOrderBySequenceNumberAsc(id).map { it.toDomain() }
        val equityCurve = backtestEquityPointRepository.findByBacktestRunIdOrderBySequenceNumberAsc(id).map { it.toDomain() }
        return trades to equityCurve
    }

    private fun findOwned(userId: UUID, id: UUID): BacktestRunEntity =
        backtestRunRepository.findByIdAndUserId(id, userId) ?: throw BacktestNotFoundException(id)

    @Transactional
    fun persist(userId: UUID, run: BacktestRun, trades: List<Trade>, equityCurve: List<EquityPoint>) {
        backtestRunRepository.save(BacktestRunEntity.from(userId, run))
        backtestTradeRepository.saveAll(trades.map { BacktestTradeEntity.from(run.id, it) })
        backtestEquityPointRepository.saveAll(equityCurve.map { BacktestEquityPointEntity.from(run.id, it) })
    }

    private fun validateRange(fromDate: LocalDate, toDate: LocalDate) {
        if (!fromDate.isBefore(toDate)) {
            throw InvalidBacktestRangeException("fromDate must be before toDate")
        }
        if (toDate.isAfter(LocalDate.now())) {
            throw InvalidBacktestRangeException("toDate cannot be in the future")
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_BACKTEST_RANGE_DAYS) {
            throw InvalidBacktestRangeException("Date range cannot exceed $MAX_BACKTEST_RANGE_DAYS days")
        }
    }

    private fun validateStrategyParameters(request: BacktestRequest): StrategyParameters {
        return when (request.strategyType) {
            StrategyType.MOVING_AVERAGE_CROSSOVER -> {
                rejectFieldsFor(request, rsi = true, signal = true, bollinger = true, donchian = true, adx = true)
                val fastPeriod = request.fastPeriod
                val slowPeriod = request.slowPeriod
                if (fastPeriod == null || slowPeriod == null || fastPeriod <= 0 || slowPeriod <= 0 || fastPeriod >= slowPeriod) {
                    throw InvalidStrategyParametersException(
                        "fastPeriod and slowPeriod are required, positive, and fastPeriod must be less than slowPeriod",
                    )
                }
                StrategyParameters(fastPeriod = fastPeriod, slowPeriod = slowPeriod)
            }
            StrategyType.RSI_THRESHOLD -> {
                rejectFieldsFor(request, fastSlow = true, signal = true, bollinger = true, donchian = true, adx = true)
                val rsiPeriod = request.rsiPeriod
                val oversold = request.oversoldThreshold
                val overbought = request.overboughtThreshold
                if (rsiPeriod == null || rsiPeriod <= 0 || oversold == null || overbought == null || oversold >= overbought) {
                    throw InvalidStrategyParametersException(
                        "rsiPeriod, oversoldThreshold, and overboughtThreshold are required, and oversoldThreshold must be less than overboughtThreshold",
                    )
                }
                StrategyParameters(rsiPeriod = rsiPeriod, oversoldThreshold = oversold, overboughtThreshold = overbought)
            }
            StrategyType.MACD_CROSSOVER -> {
                rejectFieldsFor(request, rsi = true, bollinger = true, donchian = true, adx = true)
                val fastPeriod = request.fastPeriod
                val slowPeriod = request.slowPeriod
                val signalPeriod = request.signalPeriod
                if (fastPeriod == null || slowPeriod == null || signalPeriod == null ||
                    fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0 || fastPeriod >= slowPeriod
                ) {
                    throw InvalidStrategyParametersException(
                        "fastPeriod, slowPeriod, and signalPeriod are required and positive, and fastPeriod must be less than slowPeriod",
                    )
                }
                StrategyParameters(fastPeriod = fastPeriod, slowPeriod = slowPeriod, signalPeriod = signalPeriod)
            }
            StrategyType.BOLLINGER_BANDS -> {
                rejectFieldsFor(request, fastSlow = true, rsi = true, signal = true, donchian = true, adx = true)
                val bollingerPeriod = request.bollingerPeriod
                val stdDevMultiplier = request.bollingerStdDevMultiplier
                if (bollingerPeriod == null || bollingerPeriod <= 0 || stdDevMultiplier == null || stdDevMultiplier.signum() <= 0) {
                    throw InvalidStrategyParametersException(
                        "bollingerPeriod and bollingerStdDevMultiplier are required, and both must be positive",
                    )
                }
                StrategyParameters(bollingerPeriod = bollingerPeriod, bollingerStdDevMultiplier = stdDevMultiplier)
            }
            StrategyType.DONCHIAN_CHANNEL_BREAKOUT -> {
                rejectFieldsFor(request, fastSlow = true, rsi = true, signal = true, bollinger = true, adx = true)
                val entryPeriod = request.donchianEntryPeriod
                val exitPeriod = request.donchianExitPeriod
                if (entryPeriod == null || exitPeriod == null || entryPeriod <= 0 || exitPeriod <= 0) {
                    throw InvalidStrategyParametersException(
                        "donchianEntryPeriod and donchianExitPeriod are required and must be positive",
                    )
                }
                StrategyParameters(donchianEntryPeriod = entryPeriod, donchianExitPeriod = exitPeriod)
            }
            StrategyType.ADX_TREND -> {
                rejectFieldsFor(request, fastSlow = true, rsi = true, signal = true, bollinger = true, donchian = true)
                val adxPeriod = request.adxPeriod
                val threshold = request.adxThreshold
                if (adxPeriod == null || adxPeriod <= 0 || threshold == null ||
                    threshold.signum() < 0 || threshold.compareTo(BigDecimal(100)) > 0
                ) {
                    throw InvalidStrategyParametersException(
                        "adxPeriod must be positive and adxThreshold must be between 0 and 100",
                    )
                }
                StrategyParameters(adxPeriod = adxPeriod, adxThreshold = threshold)
            }
        }
    }

    // Guards against silently ignoring a field the client set but the chosen strategy does not
    // use (see CLAUDE.md Section 18) - every strategy branch above calls this for exactly the
    // fields it does not own itself.
    private fun rejectFieldsFor(
        request: BacktestRequest,
        fastSlow: Boolean = false,
        rsi: Boolean = false,
        signal: Boolean = false,
        bollinger: Boolean = false,
        donchian: Boolean = false,
        adx: Boolean = false,
    ) {
        if (fastSlow && (request.fastPeriod != null || request.slowPeriod != null)) {
            throw InvalidStrategyParametersException("fastPeriod/slowPeriod are not applicable to ${request.strategyType}")
        }
        if (rsi && (request.rsiPeriod != null || request.oversoldThreshold != null || request.overboughtThreshold != null)) {
            throw InvalidStrategyParametersException("rsiPeriod/oversoldThreshold/overboughtThreshold are not applicable to ${request.strategyType}")
        }
        if (signal && request.signalPeriod != null) {
            throw InvalidStrategyParametersException("signalPeriod is not applicable to ${request.strategyType}")
        }
        if (bollinger && (request.bollingerPeriod != null || request.bollingerStdDevMultiplier != null)) {
            throw InvalidStrategyParametersException("bollingerPeriod/bollingerStdDevMultiplier are not applicable to ${request.strategyType}")
        }
        if (donchian && (request.donchianEntryPeriod != null || request.donchianExitPeriod != null)) {
            throw InvalidStrategyParametersException("donchianEntryPeriod/donchianExitPeriod are not applicable to ${request.strategyType}")
        }
        if (adx && (request.adxPeriod != null || request.adxThreshold != null)) {
            throw InvalidStrategyParametersException("adxPeriod/adxThreshold are not applicable to ${request.strategyType}")
        }
    }

    private fun validateAccountCurrency(instrument: Instrument, accountCurrency: String) {
        when (instrument.assetType) {
            AssetType.STOCK -> if (accountCurrency != STOCK_ACCOUNT_CURRENCY) {
                throw InvalidAccountCurrencyException("Stock backtests require accountCurrency = $STOCK_ACCOUNT_CURRENCY")
            }
            AssetType.FOREX -> if (accountCurrency != instrument.quoteCurrency) {
                throw InvalidAccountCurrencyException("Forex backtests require accountCurrency to match the pair quote currency: ${instrument.quoteCurrency}")
            }
        }
    }

    /**
     * One deterministic precedence rule (CLAUDE.md Section 19): when accountProfileId is supplied,
     * its balance seeds initialCapital UNLESS the request also supplies its own initialCapital, in
     * which case the explicit value always wins. This is a deliberate, singular exception to
     * rejectFieldsFor's "reject conflicting/inapplicable input" policy elsewhere in this service -
     * a profile is a default-sourcing mechanism, not a second, mutually-exclusive input mode, so
     * "both present" is a legitimate per-run override, not a contradiction to reject. Ownership is
     * checked via TradingAccountService.findOwnedProfile, which throws the standard
     * TradingAccountProfileNotFoundException (404) exactly like BacktestService's own findOwned -
     * a profile that doesn't exist or isn't owned by this user is never distinguishable from the
     * other, consistent with every other ownership check in this codebase.
     *
     * accountCurrency (the caller's already-validated request.accountCurrency, matching the
     * instrument per validateAccountCurrency) must match the profile's own accountCurrency before
     * its balance is used - a profile's balance is denominated in its own currency, and silently
     * reinterpreting e.g. a USD balance as JPY initialCapital for a USD/JPY backtest would corrupt
     * the simulation's numbers without any error. This check only applies when a profile actually
     * seeds initialCapital (request.initialCapital == null); an explicit initialCapital always
     * wins and is trusted to already be in the request's own accountCurrency, exactly as it was
     * before this feature existed.
     */
    private fun resolveInitialCapital(userId: UUID, request: BacktestRequest, accountCurrency: String): BigDecimal {
        if (request.initialCapital != null) {
            return request.initialCapital
        }
        val profileId = request.accountProfileId
            ?: throw InvalidStrategyParametersException("initialCapital is required when accountProfileId is not supplied")
        val profile = tradingAccountService.findOwnedProfile(userId, profileId)
        if (profile.accountCurrency != accountCurrency) {
            throw InvalidAccountCurrencyException(
                "Trading account profile currency (${profile.accountCurrency}) does not match this backtest's required accountCurrency ($accountCurrency)",
            )
        }
        val balance = profile.balance
        // BacktestRequest.initialCapital carries @Positive, which only runs when a value is
        // actually supplied - a profile-sourced balance bypasses Bean Validation entirely (a
        // profile's balance is only required to be >= 0, see TradingAccountProfileRequest), so
        // the same positivity requirement is re-asserted here explicitly.
        if (balance.signum() <= 0) {
            throw InvalidStrategyParametersException("Trading account profile balance must be positive to seed a backtest's initialCapital")
        }
        return balance
    }

    private fun warmupPeriod(strategyType: StrategyType, parameters: StrategyParameters, riskManagementConfig: RiskManagementConfig?): Int {
        val strategyWarmup = when (strategyType) {
            StrategyType.MOVING_AVERAGE_CROSSOVER -> requireNotNull(parameters.slowPeriod)
            StrategyType.RSI_THRESHOLD -> requireNotNull(parameters.rsiPeriod) + 1
            StrategyType.MACD_CROSSOVER -> requireNotNull(parameters.slowPeriod) + requireNotNull(parameters.signalPeriod) - 1
            StrategyType.BOLLINGER_BANDS -> requireNotNull(parameters.bollingerPeriod)
            StrategyType.DONCHIAN_CHANNEL_BREAKOUT ->
                maxOf(requireNotNull(parameters.donchianEntryPeriod), requireNotNull(parameters.donchianExitPeriod)) + 1
            StrategyType.ADX_TREND -> 2 * requireNotNull(parameters.adxPeriod)
        }
        // Atr.calculate(...)[0] aligns to original candle index atrPeriod (CLAUDE.md Section 18) -
        // folding this into the single warmupPeriod value feeds both computeWarmupFromDate and the
        // firstTradableIndex >= warmupPeriod floor below, so "ATR unavailable at entry" is handled
        // the same way as every other insufficient-history case: InsufficientCandleDataException,
        // never a fabricated value.
        return if (riskManagementConfig != null) maxOf(strategyWarmup, riskManagementConfig.atrPeriod) else strategyWarmup
    }

    /**
     * Cross-strategy, always-applicable - unlike validateStrategyParameters/rejectFieldsFor, which
     * is strategy-shape-scoped. Reuses InvalidStrategyParametersException throughout (same grouped
     * 400 handler) rather than introducing a second exception type, since this is the same
     * "reject, don't silently ignore" policy applied to a new axis (CLAUDE.md Section 18).
     */
    private fun validateRiskManagementParameters(request: BacktestRequest): RiskManagementParameters {
        val sizing = when (request.positionSizingType) {
            PositionSizingType.FIXED_QUANTITY -> {
                if (request.riskPercentPerTrade != null) {
                    throw InvalidStrategyParametersException("riskPercentPerTrade is not applicable to FIXED_QUANTITY sizing")
                }
                val qty = request.quantity
                if (qty == null || qty.signum() <= 0) {
                    throw InvalidStrategyParametersException("quantity is required and must be positive for FIXED_QUANTITY sizing")
                }
                PositionSizingKind.FixedQuantity(qty)
            }
            PositionSizingType.RISK_BASED_ATR -> {
                if (request.quantity != null) {
                    throw InvalidStrategyParametersException("quantity is not applicable to RISK_BASED_ATR sizing")
                }
                val riskPercent = request.riskPercentPerTrade
                if (riskPercent == null || riskPercent.signum() <= 0 || riskPercent.compareTo(HUNDRED) > 0) {
                    throw InvalidStrategyParametersException("riskPercentPerTrade is required and must be between 0 and 100 for RISK_BASED_ATR sizing")
                }
                if (!request.atrRiskManagementEnabled) {
                    throw InvalidStrategyParametersException("atrRiskManagementEnabled must be true when positionSizingType is RISK_BASED_ATR")
                }
                PositionSizingKind.RiskBased(riskPercent)
            }
        }

        if (!request.atrRiskManagementEnabled) {
            if (request.atrPeriod != null || request.atrInitialStopMultiplier != null ||
                request.atrTrailingStopEnabled || request.atrTrailingStopMultiplier != null
            ) {
                throw InvalidStrategyParametersException(
                    "atrPeriod/atrInitialStopMultiplier/atrTrailingStopEnabled/atrTrailingStopMultiplier are not applicable when atrRiskManagementEnabled is false",
                )
            }
            return RiskManagementParameters(sizing, null)
        }

        val atrPeriod = request.atrPeriod ?: DEFAULT_ATR_PERIOD
        val initialMultiplier = request.atrInitialStopMultiplier ?: DEFAULT_INITIAL_STOP_MULTIPLIER
        if (atrPeriod <= 0) throw InvalidStrategyParametersException("atrPeriod must be positive")
        if (initialMultiplier.signum() <= 0) throw InvalidStrategyParametersException("atrInitialStopMultiplier must be positive")

        val trailingMultiplier = if (request.atrTrailingStopEnabled) {
            val m = request.atrTrailingStopMultiplier ?: DEFAULT_TRAILING_STOP_MULTIPLIER
            if (m.signum() <= 0) {
                throw InvalidStrategyParametersException("atrTrailingStopMultiplier must be positive when atrTrailingStopEnabled is true")
            }
            m
        } else {
            if (request.atrTrailingStopMultiplier != null) {
                throw InvalidStrategyParametersException("atrTrailingStopMultiplier is not applicable when atrTrailingStopEnabled is false")
            }
            BigDecimal.ZERO
        }

        return RiskManagementParameters(
            sizing,
            RiskManagementConfig(atrPeriod, initialMultiplier, request.atrTrailingStopEnabled, trailingMultiplier),
        )
    }

    private sealed interface PositionSizingKind {
        data class FixedQuantity(val quantity: BigDecimal) : PositionSizingKind
        data class RiskBased(val riskPercent: BigDecimal) : PositionSizingKind
    }

    private data class RiskManagementParameters(
        val positionSizing: PositionSizingKind,
        val riskManagementConfig: RiskManagementConfig?,
    )

    private fun buildStrategy(strategyType: StrategyType, parameters: StrategyParameters, candles: List<Candle>): BacktestStrategy =
        when (strategyType) {
            StrategyType.MOVING_AVERAGE_CROSSOVER -> MovingAverageCrossoverStrategy(
                candles = candles,
                fastPeriod = requireNotNull(parameters.fastPeriod),
                slowPeriod = requireNotNull(parameters.slowPeriod),
            )
            StrategyType.RSI_THRESHOLD -> RsiThresholdStrategy(
                candles = candles,
                rsiPeriod = requireNotNull(parameters.rsiPeriod),
                oversoldThreshold = requireNotNull(parameters.oversoldThreshold),
                overboughtThreshold = requireNotNull(parameters.overboughtThreshold),
            )
            StrategyType.MACD_CROSSOVER -> MacdCrossoverStrategy(
                candles = candles,
                fastPeriod = requireNotNull(parameters.fastPeriod),
                slowPeriod = requireNotNull(parameters.slowPeriod),
                signalPeriod = requireNotNull(parameters.signalPeriod),
            )
            StrategyType.BOLLINGER_BANDS -> BollingerBandsMeanReversionStrategy(
                candles = candles,
                period = requireNotNull(parameters.bollingerPeriod),
                stdDevMultiplier = requireNotNull(parameters.bollingerStdDevMultiplier).toDouble(),
            )
            StrategyType.DONCHIAN_CHANNEL_BREAKOUT -> DonchianChannelBreakoutStrategy(
                candles = candles,
                entryLookback = requireNotNull(parameters.donchianEntryPeriod),
                exitLookback = requireNotNull(parameters.donchianExitPeriod),
            )
            StrategyType.ADX_TREND -> AdxTrendStrategy(
                candles = candles,
                period = requireNotNull(parameters.adxPeriod),
                threshold = requireNotNull(parameters.adxThreshold).toDouble(),
            )
        }

    private data class StrategyParameters(
        val fastPeriod: Int? = null,
        val slowPeriod: Int? = null,
        val rsiPeriod: Int? = null,
        val oversoldThreshold: BigDecimal? = null,
        val overboughtThreshold: BigDecimal? = null,
        val signalPeriod: Int? = null,
        val bollingerPeriod: Int? = null,
        val bollingerStdDevMultiplier: BigDecimal? = null,
        val donchianEntryPeriod: Int? = null,
        val donchianExitPeriod: Int? = null,
        val adxPeriod: Int? = null,
        val adxThreshold: BigDecimal? = null,
    )

    companion object {
        private const val MAX_BACKTEST_RANGE_DAYS = 1825L
        private const val WARMUP_BUFFER_DAYS = 10L
        private const val STOCK_ACCOUNT_CURRENCY = "USD"
        private val HUNDRED = BigDecimal(100)
        private const val DEFAULT_ATR_PERIOD = 14
        private val DEFAULT_INITIAL_STOP_MULTIPLIER = BigDecimal("2.0")
        private val DEFAULT_TRAILING_STOP_MULTIPLIER = BigDecimal("2.0")

        /**
         * A heuristic, not a correctness guarantee -- generous enough to absorb weekends/holidays
         * for daily bars of either asset class. The real safety net is createBacktest's subsequent
         * firstTradableIndex >= warmupPeriod check, which throws InsufficientCandleDataException
         * if this guess was too small for a given symbol/date range. A companion function (not an
         * instance method) specifically so it is testable without constructing the full service
         * and its dependencies -- this codebase has no mocking library.
         */
        internal fun computeWarmupFromDate(fromDate: LocalDate, warmupPeriod: Int): LocalDate =
            fromDate.minusDays(warmupPeriod * 2L + WARMUP_BUFFER_DAYS)
    }
}
