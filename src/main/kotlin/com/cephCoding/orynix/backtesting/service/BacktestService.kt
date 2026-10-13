package com.cephCoding.orynix.backtesting.service

import com.cephCoding.orynix.backtesting.domain.BacktestRun
import com.cephCoding.orynix.backtesting.domain.EquityPoint
import com.cephCoding.orynix.backtesting.domain.StrategyType
import com.cephCoding.orynix.backtesting.domain.Trade
import com.cephCoding.orynix.backtesting.domain.TradeDirection
import com.cephCoding.orynix.backtesting.dto.BacktestRequest
import com.cephCoding.orynix.backtesting.engine.BacktestEngine
import com.cephCoding.orynix.backtesting.model.BacktestEquityPointEntity
import com.cephCoding.orynix.backtesting.model.BacktestRunEntity
import com.cephCoding.orynix.backtesting.model.BacktestTradeEntity
import com.cephCoding.orynix.backtesting.repository.BacktestEquityPointRepository
import com.cephCoding.orynix.backtesting.repository.BacktestRunRepository
import com.cephCoding.orynix.backtesting.repository.BacktestTradeRepository
import com.cephCoding.orynix.backtesting.strategy.BacktestStrategy
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
    private val backtestRunRepository: BacktestRunRepository,
    private val backtestTradeRepository: BacktestTradeRepository,
    private val backtestEquityPointRepository: BacktestEquityPointRepository,
) {

    fun createBacktest(userId: UUID, request: BacktestRequest): BacktestRun {
        validateRange(request.fromDate, request.toDate)
        val parameters = validateStrategyParameters(request)
        val instrument = InstrumentResolver.resolve(request.symbol)
        validateAccountCurrency(instrument, request.accountCurrency)

        val warmupPeriod = warmupPeriod(request.strategyType, parameters)
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

        val result = BacktestEngine.run(
            candles = candles,
            firstTradableIndex = firstTradableIndex,
            strategy = strategy,
            initialCapital = request.initialCapital,
            quantity = request.quantity,
            commissionPerOrder = request.commissionPerOrder,
            slippageBps = request.slippageBps,
        )

        val run = BacktestRun(
            id = UUID.randomUUID(),
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
            direction = TradeDirection.LONG,
            initialCapital = request.initialCapital,
            accountCurrency = request.accountCurrency,
            positionSizingType = POSITION_SIZING_TYPE,
            quantity = request.quantity,
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
                if (request.rsiPeriod != null || request.oversoldThreshold != null || request.overboughtThreshold != null) {
                    throw InvalidStrategyParametersException(
                        "rsiPeriod/oversoldThreshold/overboughtThreshold are not applicable to MOVING_AVERAGE_CROSSOVER",
                    )
                }
                val fastPeriod = request.fastPeriod
                val slowPeriod = request.slowPeriod
                if (fastPeriod == null || slowPeriod == null || fastPeriod <= 0 || slowPeriod <= 0 || fastPeriod >= slowPeriod) {
                    throw InvalidStrategyParametersException(
                        "fastPeriod and slowPeriod are required, positive, and fastPeriod must be less than slowPeriod",
                    )
                }
                StrategyParameters(fastPeriod = fastPeriod, slowPeriod = slowPeriod, rsiPeriod = null, oversoldThreshold = null, overboughtThreshold = null)
            }
            StrategyType.RSI_THRESHOLD -> {
                if (request.fastPeriod != null || request.slowPeriod != null) {
                    throw InvalidStrategyParametersException("fastPeriod/slowPeriod are not applicable to RSI_THRESHOLD")
                }
                val rsiPeriod = request.rsiPeriod
                val oversold = request.oversoldThreshold
                val overbought = request.overboughtThreshold
                if (rsiPeriod == null || rsiPeriod <= 0 || oversold == null || overbought == null || oversold >= overbought) {
                    throw InvalidStrategyParametersException(
                        "rsiPeriod, oversoldThreshold, and overboughtThreshold are required, and oversoldThreshold must be less than overboughtThreshold",
                    )
                }
                StrategyParameters(fastPeriod = null, slowPeriod = null, rsiPeriod = rsiPeriod, oversoldThreshold = oversold, overboughtThreshold = overbought)
            }
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

    private fun warmupPeriod(strategyType: StrategyType, parameters: StrategyParameters): Int =
        when (strategyType) {
            StrategyType.MOVING_AVERAGE_CROSSOVER -> requireNotNull(parameters.slowPeriod)
            StrategyType.RSI_THRESHOLD -> requireNotNull(parameters.rsiPeriod) + 1
        }

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
        }

    private data class StrategyParameters(
        val fastPeriod: Int?,
        val slowPeriod: Int?,
        val rsiPeriod: Int?,
        val oversoldThreshold: BigDecimal?,
        val overboughtThreshold: BigDecimal?,
    )

    companion object {
        private const val MAX_BACKTEST_RANGE_DAYS = 1825L
        private const val WARMUP_BUFFER_DAYS = 10L
        private const val POSITION_SIZING_TYPE = "FIXED_QUANTITY"
        private const val STOCK_ACCOUNT_CURRENCY = "USD"

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
