# CLAUDE.md

Instructions for Claude Code sessions working on this repository. Read this before making changes.

## 1. Project overview

Orynix is an AI market intelligence platform. The long-term goal is a system that provides market
analysis using technical indicators, news and sentiment analysis, multiple AI agents, predictive
models, risk analysis, backtesting, trading signals, and paper trading.

The platform consists of:

- An Android client (Kotlin + Jetpack Compose)
- A web client
- A single shared Spring Boot backend
- Shared authentication and user accounts
- PostgreSQL as the primary database

Both clients consume the same backend API and the same account/authentication system. There is no
client-specific backend logic — the backend is the single source of truth for all clients.

## 2. Architecture

### Current

```
Android / Web
      |
 Spring Boot API (REST, JSON)
      |
 PostgreSQL
```

### Planned backend capabilities

- Authentication — **implemented** (register, login, refresh, logout)
- User management — **implemented** (`/api/v1/users/me` only)
- Market data — **implemented** (historical daily OHLCV candles via Massive, see Section 6)
- Technical analysis — **implemented** (on-demand indicators computed from candles, see Section 7)
- AI agents — not implemented
- News analysis — not implemented
- Sentiment analysis — not implemented
- Prediction — not implemented
- Risk management — not implemented
- Signals — **implemented** (deterministic indicator-derived condition signals, see Section 8)
- Fundamentals — **implemented** (on-demand company information only via Massive, see Section 9)
- Portfolio — not implemented
- Backtesting — not implemented
- Paper trading — not implemented
- Notifications — not implemented
- WebSockets / live updates — not implemented

Do not write code as if unimplemented capabilities already exist. Do not add infrastructure
(message queues, caches, websockets, microservices) in anticipation of them.

## 3. Technology stack

Actually in use, with the versions declared in `build.gradle.kts`:

- Kotlin 2.3.21
- Java 21 (toolchain)
- Spring Boot 4.1.1
- Gradle Kotlin DSL
- PostgreSQL (`org.postgresql:postgresql` at runtime)
- Spring Data JPA (`spring-boot-starter-data-jpa`)
- Spring Security (`spring-boot-starter-security`)
- Flyway (`spring-boot-starter-flyway`, `flyway-database-postgresql`)
- Bean Validation (`spring-boot-starter-validation`)
- Actuator (`spring-boot-starter-actuator`)
- JWT via `io.jsonwebtoken` (jjwt) 0.13.0 — `jjwt-api`, `jjwt-impl`, `jjwt-gson`
- `jackson-module-kotlin` from `tools.jackson.module` (Jackson 3 — see note below)
- `org.springframework.boot:spring-boot-restclient` — provides the `RestClient.Builder` bean used
  to call external HTTP APIs (currently Massive, see Section 6). See the Boot 4 module note below:
  this is a separate artifact from `spring-boot-starter-webmvc`, not a transitive dependency of it.

Test-only: the Boot 4 per-module test starters (`*-test` for webmvc, data-jpa, security,
validation, actuator, flyway), `spring-boot-testcontainers`, `testcontainers-postgresql`,
`testcontainers-junit-jupiter`.

**Do not add** a technology to this list speculatively. If a future task needs something new,
add it for that task and update this file.

### Spring Boot 4 / Jackson 3 note

Spring Boot 4 ships Jackson 3, not Jackson 2. Jackson's group ID moved from
`com.fasterxml.jackson.*` to `tools.jackson.*`. Consequences for this codebase:

- Never add a `com.fasterxml.jackson.databind` import. If a Jackson type is needed, it comes
  from `tools.jackson.*`.
- **Exception, verified by inspecting the resolved jars (`unzip -l <jar> | grep ClassName`), not
  assumed**: basic annotations (`@JsonProperty`, etc.) stayed at the Jackson 2.x group id by
  design — Jackson 3's own `jackson-databind` depends on
  `com.fasterxml.jackson.core:jackson-annotations:2.21`; there is no `tools.jackson.annotation`
  package. `com.fasterxml.jackson.annotation.*` is therefore the one sanctioned `com.fasterxml`
  import in this codebase — it carries no `ObjectMapper`/databind code, so it doesn't create the
  second competing databind runtime the ban above actually guards against. Needed the first time
  in `fundamentals/provider/massive/dto/` to map Massive's snake_case ticker-details JSON keys
  (`primary_exchange`, `market_cap`, etc. — see Section 9) onto camelCase Kotlin properties.
- JWT signing deliberately uses **jjwt-gson**, not jjwt-jackson, specifically to avoid pulling a
  Jackson 2 dependency onto the classpath next to Spring's Jackson 3. Do not "simplify" this to
  jjwt-jackson later without re-checking this still holds.
- Starter artifact names changed too (e.g. `spring-boot-starter-webmvc` instead of the old
  `spring-boot-starter-web`, `spring-boot-starter-flyway` instead of a bare `flyway-core`
  dependency). This is intentional, not a mistake to "fix" back to Boot 3 naming.

### Boot 4 module note: `RestClient.Builder` is not bundled with webmvc

Unlike Boot 3, `spring-boot-starter-webmvc` does **not** transitively bring in
`RestClientAutoConfiguration` (which provides the `RestClient.Builder` bean). That autoconfiguration
moved to its own module, `org.springframework.boot:spring-boot-restclient`
(`org.springframework.boot.restclient.autoconfigure` package). Confusingly,
`spring-boot-starter-webmvc-test` *does* pull in a `RestClient.Builder` bean for tests, so a context
that works under `@SpringBootTest` can still fail at real `bootRun`/production time with
`NoSuchBeanDefinitionException: RestClient$Builder` if the main `spring-boot-restclient` dependency
is missing. If a future feature needs `RestClient` (or `RestTemplate`/`HttpServiceClient`
autoconfiguration), add `spring-boot-restclient` explicitly as a main dependency — don't assume the
webmvc starter covers it.

Related: `@ConfigurationProperties` classes using constructor binding (immutable `data class`, no
setters) must **not** also be annotated `@Component` — that routes construction through normal
bean-definition autowiring, which tries to resolve each constructor parameter as its own bean (e.g.
fails looking for a bean of type `String`) instead of binding from `application.yml`. Register them
instead via `@ConfigurationPropertiesScan` on `OrynixApplication` (already enabled) and only
`@ConfigurationProperties(prefix = "...")` on the properties class itself — see `MassiveProperties`.

### Spring Framework 7 / JSpecify nullability note

Spring Framework 7 / Spring Security 7 annotate many Java APIs with JSpecify `@Nullable`, which
Kotlin 2 reads directly — some methods that are effectively always non-null for the inputs this
codebase passes (e.g. `PasswordEncoder.encode`, whose Javadoc `@Contract` says non-null in implies
non-null out, a contract Kotlin's compiler can't see) now surface as `String?` in Kotlin. Expect
this pattern elsewhere in Spring Security/Framework APIs and resolve it with a `!!` plus a short
comment stating why it's safe — don't silently change method signatures to accept nulls instead.

### Package renames to watch for (Boot 4 / Testcontainers 2)

Several test-support classes moved packages compared to older tutorials/Stack Overflow answers.
Verified for this project (Boot 4.1.1 / Testcontainers 2.0.5):

- `AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  (not `org.springframework.boot.test.autoconfigure.web.servlet`).
- Testcontainers 2.x dropped the generic self-type from container classes and moved them out of
  `org.testcontainers.containers`: use `org.testcontainers.postgresql.PostgreSQLContainer`
  (non-generic — no `PostgreSQLContainer<*>`), not `org.testcontainers.containers.PostgreSQLContainer`.
- Testcontainers 2.x artifact IDs all gained a `testcontainers-` prefix:
  `org.testcontainers:testcontainers-postgresql`, `org.testcontainers:testcontainers-junit-jupiter`
  — not the old bare `org.testcontainers:postgresql` / `org.testcontainers:junit-jupiter`.
- `UserDetailsServiceAutoConfiguration` moved to
  `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration` (not
  `org.springframework.boot.autoconfigure.security.servlet`) — Boot 4 split security
  autoconfiguration out of `spring-boot-autoconfigure` into the `spring-boot-security` module.
  This project excludes it in `OrynixApplication` since there's no `UserDetailsService` here (see
  Section 5); without the exclusion, Boot autoconfigures an unused in-memory user with a
  randomly generated password logged on every startup.

If a future task needs another Boot 4 test-autoconfiguration annotation or another Testcontainers
module, verify the actual package/artifact by inspecting the resolved jar
(`unzip -l <jar> | grep ClassName` or `./gradlew dependencies`) rather than assuming Boot 3 /
Testcontainers 1.x paths still apply.

### Testcontainers: use a `@TestConfiguration` bean, not a static `@Container` field

`AbstractIntegrationTest` provides the shared Postgres container via
`TestcontainersConfiguration` — a `@TestConfiguration(proxyBeanMethods = false)` class with a
single `@Bean @ServiceConnection fun postgresContainer(): PostgreSQLContainer`, imported with
`@Import(TestcontainersConfiguration::class)`. This was not the first approach tried: a raw JUnit
`companion object { @Container @JvmStatic @ServiceConnection val postgres = ... }` on the Kotlin
base class caused **every test class to start its own separate Postgres container on its own
port**, and stale connection pools from one class's Spring context leaking into another's,
producing `Connection refused` errors and ~30s-per-test Hikari timeouts. Switching the container
to a Spring-managed bean lets Spring's test-context cache do its job: all test classes with
identical configuration share one `ApplicationContext` and therefore one container. If you add a
new integration test class, extend `AbstractIntegrationTest` — do not declare another container
anywhere else.

The same context-cache concern applies to test doubles for external services, not just
Testcontainers. `MarketDataTestConfiguration` provides a `@Primary` `FakeMarketDataProvider` bean
and is `@Import`-ed directly into `AbstractIntegrationTest` (alongside `TestcontainersConfiguration`),
not only on the market-data test classes — a different `@Import` set per test class would change
the context-cache key and spin up a second `ApplicationContext` (and therefore a second Postgres
container), reproducing the exact failure mode described above. Because the fake provider bean is a
singleton shared across every test class in the suite, tests that configure it
(`fakeMarketDataProvider.candlesToReturn = ...` / `.failure = ...`) rely on
`AbstractIntegrationTest`'s `@AfterEach` reset to avoid leaking state into unrelated tests — don't
remove that reset.

## 4. Architecture principles

- The backend is the single source of truth. Android and web clients communicate only through
  the backend API.
- Use feature-oriented package organization (see Section 10). Do not organize by technical layer
  at the top level (no top-level `controllers/`, `services/`, `repositories/`).
- Prefer simple designs over unnecessary abstractions. Don't add an interface for a class with
  one implementation. Don't add a generic/base class until there are at least two concrete needs
  for it.
- Never expose JPA entities directly through REST APIs. Always map to/from DTOs.
- The database schema is managed by Flyway migrations only. `spring.jpa.hibernate.ddl-auto` must
  stay `none`. The schema must be fully reproducible from migrations alone.
- Never hardcode secrets (DB credentials, JWT signing keys). They come from environment
  variables.
- Authentication must use secure password hashing (BCrypt via Spring Security), never plaintext
  or reversible encryption.
- Keep security boundaries explicit: security-related code lives in `auth/security/`, not
  scattered across controllers.
- Avoid premature microservices. This is one deployable Spring Boot application.
- Avoid unnecessary infrastructure. Do not add Redis, Kafka, WebSockets, or additional services
  until a concrete feature in this repo actually needs them.
- Preserve existing working code when modifying the project. Don't rewrite something that isn't
  broken as a side effect of an unrelated change.
- Prefer small, cohesive components. A service should have one clear responsibility.

## 5. Authentication architecture

Implemented flow:

1. **Register** (`POST /api/v1/auth/register`, public) — email + password. Password is hashed
   with BCrypt before storage. Returns an access token, a refresh token, and the created user's
   public info.
2. **Login** (`POST /api/v1/auth/login`, public) — verifies email + password against the stored
   hash. Returns a new access token, a new refresh token, and user info.
3. **Authenticated requests** — the client sends `Authorization: Bearer <access token>`. A
   `JwtAuthenticationFilter` validates the token's signature and expiry and sets the
   authenticated principal (the user's UUID) on the security context. There is no
   `UserDetailsService`/`AuthenticationManager` chain — this is a deliberate simplification for a
   stateless, role-free JWT API; credential checks happen directly in `AuthService`.
4. **Access tokens** are short-lived JWTs (HS256, signed with `JWT_SECRET`). Claims are limited
   to `sub` (user id), `iat`, `exp` — no email, no roles, nothing sensitive.
5. **Refresh tokens** are opaque random values, not JWTs. Only an SHA-256 hash of the refresh
   token is persisted (`refresh_tokens` table), alongside an expiry and a `revoked` flag.
6. **Refresh** (`POST /api/v1/auth/refresh`, public) — the client presents a refresh token. The
   server validates it's unexpired and unrevoked, then **rotates** it: the presented token is
   marked revoked and a brand-new access/refresh pair is issued. A previously-used or revoked
   refresh token is rejected (401), including reuse of an already-rotated token.
7. **Logout** (`POST /api/v1/auth/logout`, protected — requires a valid access token) — revokes
   the presented refresh token so it can no longer be used to obtain new access tokens.
8. **Current user** (`GET /api/v1/users/me`, protected) — returns the authenticated user's public
   information, looked up by the UUID set on the security context.

Security matcher scoping is exact-path, not prefix-based: `register`, `login`, and `refresh` are
individually `permitAll()`; everything else — including `logout`, which shares the `/auth` path
prefix — requires authentication. Do not collapse this into a `/api/v1/auth/**` wildcard; that
would make logout publicly callable without a token.

Unauthenticated/forbidden requests to protected endpoints get the same JSON error shape as
everything else (see Section 11) via a custom `AuthenticationEntryPoint` /
`AccessDeniedHandler` — not Spring Security's default body.

## 6. Market data architecture

Implemented: historical **daily** OHLCV candles, sourced from **Massive** (formerly Polygon.io,
`https://api.massive.com`), normalized into an Orynix domain model, persisted in PostgreSQL, and
exposed via `GET /api/v1/market-data/candles?symbol=AAPL&timeframe=1d` (protected — requires a
valid access token, same as every other non-auth endpoint).

**Layering** — four deliberately separated representations of a candle, so the rest of Orynix
never touches Massive's response shape:

1. `market/provider/massive/dto/` — `MassiveAggregatesResponse` / `MassiveBar`, the raw JSON shape
   of Massive's aggregates endpoint (`GET /v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}`).
   Only fields Orynix actually uses are mapped (`t`, `o`, `h`, `l`, `c`, `v`) — unknown/unused
   fields (`vw`, `n`, `otc`, etc.) are ignored, not modeled.
2. `market/domain/` — `Candle` (symbol, timeframe, timestamp, OHLCV as `BigDecimal`) and
   `CandleTimeframe` (currently just `DAILY` / code `"1d"`), the provider-agnostic business model.
   `MarketDataService` and `MarketDataProvider` only ever see this type, never a Massive DTO.
3. `market/model/` — `MarketDataCandle`, the JPA persistence entity (table `market_data_candles`).
4. `market/dto/` — `CandleResponse`, the REST response DTO.

**Provider abstraction** (`market/provider/`): `MarketDataProvider` is an interface
(`fetchDailyCandles(symbol, from, to): List<Candle>`); `MassiveMarketDataProvider` is the only
implementation today. Swapping providers later means adding a new `MarketDataProvider`
implementation — `MarketDataService`, the domain model, and the REST layer do not change.
`MassiveMarketDataProvider` sends the API key as an `Authorization: Bearer` header (not the
`?apiKey=` query-string form Massive also supports) specifically so it never ends up in a logged or
echoed request URI, and wraps any `RestClientException` in a sanitized `MarketDataProviderException`
(no upstream body, no request URI) before it can reach `GlobalExceptionHandler`'s stack-trace
logging.

**Fixed lookback window (current limitation)**: the REST endpoint takes only `symbol` and
`timeframe` — no `from`/`to` params yet. `MarketDataService` always requests the last 365 days
(ending today) from the provider. Adding optional date-range params is future work, not yet done.

**Dedup strategy**: `MarketDataService.getDailyCandles` fetches from the provider, queries
`MarketDataCandleRepository.findBySymbolAndTimeframeAndTimestampIn` for timestamps already
persisted for that symbol/timeframe, filters the fetched list down to genuinely new candles before
`saveAll`, and returns the full fetched list (not just what was newly inserted) either way. This
makes re-fetching the same window idempotent — no duplicate rows — without relying on catching a
unique-constraint violation per row. The table's unique constraint on
`(symbol, timeframe, timestamp)` is the backstop, not the primary mechanism. Symbol input is
trimmed and uppercased before both the provider call and the dedup check, so `aapl` and `AAPL`
can't produce two rows.

**Configuration**: `MASSIVE_API_KEY` (required, no default — a secret) and `MASSIVE_BASE_URL`
(optional, defaults to `https://api.massive.com`), bound via `MassiveProperties`
(`@ConfigurationProperties(prefix = "massive")`, registered through `@ConfigurationPropertiesScan`
on `OrynixApplication` — see Section 3's Boot 4 module note for why this class must not also be
`@Component`).

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): other timeframes
(weekly/hourly/etc. — `CandleTimeframe` has only `DAILY` today), optional `from`/`to` query params,
and pagination (Massive's `next_url` is not followed — the fixed 365-day/5000-row window is assumed
to fit in one page). Technical indicators computed from these candles are implemented — see Section 7.

## 7. Technical analysis architecture

Implemented: a deterministic, on-demand technical-analysis engine computed from the daily candles
`MarketDataService` already fetches/persists, exposed via
`GET /api/v1/technical-analysis?symbol=AAPL&timeframe=1d` (protected, same as every other
non-auth endpoint). The technical-analysis layer talks only to `MarketDataService` — it never calls
`MarketDataProvider`/Massive directly, so it stays correct if the market-data provider ever changes:

```
Massive -> MarketDataProvider -> MarketDataService -> TechnicalAnalysisService -> REST API
```

**Package layout** (`technicalanalysis/`) adds two subpackages beyond the base
`controller/service/dto` shape, each justified individually (no `model/`/`repository/` — nothing is
persisted in this phase):

- `indicator/` — pure, Spring-free, `BigDecimal`-free calculation functions (`Sma`, `Ema`, `Rsi`,
  `MacdCalculator`, `BollingerBandsCalculator`, `Atr`, `VolumeIndicators`), each operating on plain
  `List<Double>` and independently unit-testable with zero Spring context. No monolithic calculator
  class — one small object per indicator family.
- `domain/` — mirrors `market/domain/`'s role: a seam between what `TechnicalAnalysisService`
  computes (`TechnicalAnalysisResult`, `Macd`, `BollingerBands`) and the REST DTO, so the JSON shape
  can evolve independently of the computed-value types.

**Indicators** (daily candles only — `CandleTimeframe` still has just `DAILY`/`"1d"`):

- Trend: SMA20, SMA50, EMA20
- Momentum: RSI14, MACD (fast EMA12 / slow EMA26 / signal EMA9)
- Volatility: Bollinger Bands (period 20, ±2 population standard deviations), ATR14
- Volume: Volume SMA20, volume change percentage

**EMA/Wilder seeding convention** (a deliberate, non-obvious choice, since this isn't standardized
across implementations and determinism matters here): every EMA — standard or Wilder — is seeded
with the simple average of its first `period` values; recursive smoothing starts from the value
immediately after that seed. Standard EMA uses `alpha = 2/(period+1)`; RSI14 and ATR14 use Wilder
smoothing (`alpha = 1/period`) over price changes / true ranges respectively; MACD's signal line is
the same SMA-seeded EMA applied to the MACD line itself, not to closes.

**Not persisted**: indicators are calculated fresh on every request from whatever candles
`MarketDataService.getDailyCandles` returns. There is no indicator entity, repository, or
migration in this phase — see Section 2. Caching/persistence may be added later if performance
requires it, but only for a concrete, stated need.

**Minimum candle requirement**: `TechnicalAnalysisService` requires at least 50 candles (the
largest minimum among all eight indicators — MACD needs `26 + 9 - 1 = 34`, SMA50 needs 50, and 50
is the larger of the two) before calculating anything. Fewer than that throws
`InsufficientCandleDataException`, mapped by `GlobalExceptionHandler` to `422 Unprocessable
Entity` — distinct from the `400` used for a malformed symbol/timeframe, since the request itself
is well-formed but the upstream data volume isn't enough to satisfy it. Values are never
fabricated or padded to meet this floor.

**Double/BigDecimal boundary**: `Candle`'s OHLCV fields are `BigDecimal`, but indicator math (EMA
recursion, RSI/ATR Wilder smoothing, MACD, Bollinger standard deviation) is naturally expressed in
floating point. `TechnicalAnalysisService` converts `Candle` `BigDecimal` fields to `Double` exactly
once (on the way in) and converts back to `BigDecimal` exactly once per result field (on the way
out), always via `BigDecimal.valueOf(Double)` — never the `BigDecimal(Double)` constructor, which
captures the exact binary floating-point expansion instead of the decimal value a user would
expect. Price-like fields (SMA/EMA/RSI/MACD/Bollinger/ATR) round to scale 4; volume fields (volume
SMA, volume change percentage) round to scale 2; both use `RoundingMode.HALF_UP`.

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): trading
recommendations, any LLM/AI involvement, other timeframes (weekly/hourly/etc. — bounded by
`CandleTimeframe` itself, see Section 6), indicator persistence/caching, and any technical-analysis
endpoint beyond `GET .../technical-analysis`. Deterministic condition signals (not buy/sell
recommendations) built on top of these indicators are implemented — see Section 8.

**History extension for the signal layer**: `TechnicalAnalysisService` also exposes
`getAnalysisHistory(symbol, timeframe): TechnicalAnalysisHistory`, a second method alongside
`getAnalysis` (whose own behavior/floor is unchanged) that returns the current observation, the
previous period's observation, and the raw candles used — needed because detecting a transition
(e.g. a moving-average crossover) requires comparing two points in time, not just the latest one.
It has its own independent floor, `MINIMUM_CANDLES_WITH_HISTORY = MINIMUM_CANDLES + 1` (51 today):
every indicator series here gains exactly one element per extra candle, so one more candle than
the single-value floor guarantees a "previous" value exists for every indicator, not just the one
(SMA50) driving the floor itself.

## 8. Signal & pattern analysis architecture

Implemented: a deterministic layer that turns technical-analysis indicators into structured,
explainable market *conditions* — trend, crossovers, RSI/MACD/Bollinger/ATR/volume conditions,
price momentum — exposed via `GET /api/v1/signals?symbol=AAPL&timeframe=1d` (protected, same as
every other non-auth endpoint). This is **not** an AI/LLM layer and produces no BUY/SELL/trading
recommendations — it reports conditions a future agent layer can reason over, nothing more:

```
Massive -> MarketDataProvider -> MarketDataService -> TechnicalAnalysisService -> SignalAnalysisService -> REST API
```

**Package layout** (`signals/`) adds `rule/` and `domain/` beyond the base
`controller/service/dto` shape, for the same reason `technicalanalysis/` has `indicator/`/`domain/`
(Section 7): `rule/` holds pure, Spring-free, independently unit-testable functions, one per signal
family (`TrendRule`, `CrossoverRule`, `RsiRule`, `MacdRule`, `BollingerBandsRule`, `VolatilityRule`,
`VolumeRule`, `PriceMomentumRule`, each co-locating its own state enum); `domain/` is the seam
between what `SignalAnalysisService` computes (`Signal`, `TechnicalSignalSummary`) and the REST DTO.
No `model/`/`repository/` — nothing is persisted.

**The eight signals** (all computed from `TechnicalAnalysisService.getAnalysisHistory`'s current/
previous observations and raw candles — never from Massive/`MarketDataProvider` directly):

- **TREND**: BULLISH if `EMA20 > SMA20 > SMA50`, BEARISH if reversed, else NEUTRAL.
- **CROSSOVER**: BULLISH_CROSSOVER/BEARISH_CROSSOVER only on an actual transition of `EMA20` vs
  `SMA50` between the previous and current observation, else NONE — "already above" is not a
  crossover.
- **RSI**: OVERBOUGHT at RSI14 ≥ 70, OVERSOLD at ≤ 30, else NEUTRAL — a condition, not a directional
  call (overbought is not "bearish").
- **MACD**: a crossover of the MACD/signal lines takes priority; otherwise falls back to
  BULLISH_MOMENTUM/BEARISH_MOMENTUM/NEUTRAL_MOMENTUM from the histogram's sign (via `signum()`, not
  `==`, since `BigDecimal` equality is scale-sensitive) — one combined signal, not two.
- **BOLLINGER_BANDS**: PRICE_ABOVE_UPPER_BAND/PRICE_BELOW_LOWER_BAND outside the bands (checked
  first), else PRICE_NEAR_UPPER_BAND/PRICE_NEAR_LOWER_BAND within 1% of a band, else
  PRICE_WITHIN_BANDS.
- **VOLATILITY**: LOW/NORMAL/HIGH from `ATR14 / closePrice * 100` (a relative measure — a raw
  absolute ATR threshold isn't portable across assets at different price levels), thresholds 1%/3%.
- **VOLUME**: HIGH/NORMAL/LOW from `latestVolume / volumeSma20`, thresholds 1.5×/0.5×; a zero
  `volumeSma20` reports NORMAL with no value (undefined ratio, not fabricated).
- **PRICE_MOMENTUM**: POSITIVE/NEGATIVE/FLAT from the percentage close change over the last 10
  candles, ±1% flat band.

All thresholds above are `private const val`s in `SignalAnalysisService`'s companion object — the
same convention `TechnicalAnalysisService` already uses for its period constants, not a new
`@ConfigurationProperties` class (`MassiveProperties` remains the only one, reserved for
secrets/environment-varying values).

**`strength`, `metadata`, and a per-signal `timestamp` are all omitted** from the `Signal` domain
type: none of the eight rules yield an objectively-justifiable strength score from the available
data, and a metadata map or a timestamp identical across all eight signals in one response would be
unused surface area.

**Aggregation** (`TechnicalSignalSummary`): a signal is excluded from the bullish/bearish/neutral
tally only when its directional content derives from the *exact same underlying series comparison*
as an already-counted signal. Concretely: **CROSSOVER is excluded** from the tally (still fully
reported in the `signals` list) because it and TREND both derive from the identical
EMA20/SMA20/SMA50 relationship — TREND is the steady-state view, CROSSOVER the transition-event view
of that same relationship, so counting both would double-count one fact. **RSI, BOLLINGER_BANDS,
VOLATILITY, and VOLUME always count as neutral**, regardless of their specific state, since none of
them are directional per their own definitions above. **TREND, MACD, and PRICE_MOMENTUM are the only
directional contributors**, each from a disjoint series. `neutralSignalCount` is always
`signals.size - bullishSignalCount - bearishSignalCount`, so the three counts always partition all
eight signals. `overallBias` is `BULLISH`/`BEARISH` on a strict majority of those three counts, else
`NEUTRAL` — named `OverallBias`, never `TradeDirection`/`Recommendation`, and never BUY/SELL/TRADE/
ENTRY/EXIT.

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): trading
recommendations, any LLM/AI involvement, signal persistence/caching, and any signal endpoint beyond
`GET .../signals`.

## 9. Fundamental data architecture

Implemented: on-demand company reference information, sourced from **Massive**
(`GET /v3/reference/tickers/{ticker}`), exposed via `GET /api/v1/fundamentals?symbol=AAPL`
(protected — no `SecurityConfig` entry; falls through to `anyRequest().authenticated()` like
every other non-auth endpoint):

```
Massive -> FundamentalDataProvider -> FundamentalAnalysisService -> REST API
```

This is a **separate provider hierarchy from `MarketDataProvider`**, not an extension of it —
fetching a company profile is conceptually unrelated to fetching OHLCV candles.
`FundamentalAnalysisService` does not depend on `MarketDataService`; no candles are needed for
company reference data. It reuses `MassiveProperties` (baseUrl/apiKey) as-is — no new
`@ConfigurationProperties` class was needed.

**Scope for this phase, exactly**: company name, exchange, industry, and market capitalization
only. Symbol validation duplicates `MarketDataService`'s regex (`^[A-Z.]{1,10}$`) locally rather
than sharing it — deliberate duplication over introducing a shared `SymbolValidator` utility for
four lines of code, per Section 4's "don't add an abstraction for one shared need" principle;
`FundamentalAnalysisService` has no dependency on `MarketDataService` to inherit validation
through "for free" the way `technicalanalysis`/`signals` do.

**No `sector` field.** Massive's ticker-details response provides no GICS-style sector anywhere —
only `sic_code`/`sic_description`, a SIC industry classification, not a sector grouping.
`CompanyProfile.industry` is sourced from `sic_description`; there is no sector field and no
SIC-to-sector mapping table. Do not add one without a concrete data source for it.

**Known limitation — other fundamentals categories are entitlement-blocked, not unimplemented by
choice.** The deprecated `GET /vX/reference/financials` endpoint (balance sheet, income statement,
cash flow — the source of P/E, margins, growth rates, debt-to-equity, and cash flow) sunsets
2026-10-09 and already intermittently returns `410 Gone`. Its documented successor,
`GET /stocks/financials/v1/balance-sheets` (confirmed via Massive's own docs — presumably
analogous `income-statements`/`cash-flow-statements` paths exist under the same
`/stocks/financials/v1/` prefix), returns `403 Forbidden` —
`"You are not entitled to this data. Please upgrade your plan."` — under the current Massive
plan. This is a plan-tier lockout, not a missing-field or wrong-path problem. Valuation,
profitability, growth, balance-sheet, and cash-flow categories are deliberately out of scope until
the Massive plan is upgraded. Do not add speculative provider/mapping code for these endpoints —
revisit `/stocks/financials/v1/balance-sheets` specifically once entitled.

**Layering**: `fundamentals/provider/massive/dto/` holds `MassiveTickerDetailsResponse`/
`MassiveTickerDetails`, the raw Massive JSON shape (snake_case keys — `primary_exchange`,
`sic_description`, `market_cap` — mapped via `@JsonProperty`, see Section 3's annotations
exception). Only fields `CompanyProfile` actually consumes are modeled, matching `MassiveBar`'s
"only `t,o,h,l,c,v` modeled" precedent. `fundamentals/domain/` holds `CompanyProfile`
(provider-agnostic) and `FundamentalAnalysisResult` (envelope: `symbol` + `company`, the same
nested-value-object idiom `TechnicalAnalysisResult` uses for `macd`/`bollingerBands` — see Section
7 — chosen specifically so future categories like `valuation`/`profitability` can be added as
sibling keys without a breaking restructure). `fundamentals/dto/` mirrors this one level down into
REST DTOs with the same `companion object { from(domain) }` idiom used everywhere else. No
`model/`/`repository/` — nothing is persisted, matching `technicalanalysis`/`signals`.

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): valuation,
profitability, growth, balance-sheet, and cash-flow categories (blocked by entitlement, see
above), any AI/LLM involvement, and any BUY/SELL recommendation or scoring derived from this data.

## 10. Package structure

Root package: `com.cephCoding.orynix`.

```
com.cephCoding.orynix
  common/          cross-cutting concerns: ErrorResponse, GlobalExceptionHandler, shared
                   exceptions, JPA auditing config
  auth/
    controller/    AuthController
    service/       AuthService (register/login/refresh/logout, token rotation)
    security/      SecurityConfig, JwtService, JwtAuthenticationFilter,
                   RestAuthenticationEntryPoint, RestAccessDeniedHandler
    model/         RefreshToken entity
    repository/    RefreshTokenRepository
    dto/           RegisterRequest, LoginRequest, RefreshTokenRequest, AuthResponse
                   (logout reuses RefreshTokenRequest — identical shape, no separate DTO)
  user/
    controller/    UserController
    service/       UserService
    repository/    UserRepository
    model/         User entity
    dto/           UserResponse
  market/
    controller/    MarketDataController
    service/       MarketDataService (fetch + dedup + persist)
    provider/       MarketDataProvider interface
      massive/      MassiveMarketDataProvider, MassiveProperties
        dto/         MassiveAggregatesResponse, MassiveBar (raw Massive JSON shape)
    domain/         Candle, CandleTimeframe (provider-agnostic business model)
    model/          MarketDataCandle entity
    repository/     MarketDataCandleRepository
    dto/            CandleResponse
  technicalanalysis/
    controller/    TechnicalAnalysisController
    service/       TechnicalAnalysisService (fetch via MarketDataService + calculate)
    indicator/      Sma, Ema, Rsi, MacdCalculator, BollingerBandsCalculator, Atr,
                     VolumeIndicators (pure calculation functions)
    domain/         TechnicalAnalysisResult, Macd, BollingerBands, TechnicalAnalysisHistory
    dto/            TechnicalAnalysisResponse, MacdResponse, BollingerBandsResponse
  signals/
    controller/    SignalAnalysisController
    service/       SignalAnalysisService (orchestration + aggregation)
    rule/           TrendRule, CrossoverRule, RsiRule, MacdRule, BollingerBandsRule,
                     VolatilityRule, VolumeRule, PriceMomentumRule (pure rule functions)
    domain/         Signal, SignalType, OverallBias, TechnicalSignalSummary
    dto/            SignalResponse, TechnicalSignalSummaryResponse
  fundamentals/
    controller/    FundamentalAnalysisController
    service/       FundamentalAnalysisService
    provider/       FundamentalDataProvider interface
      massive/       MassiveFundamentalDataProvider
        dto/          MassiveTickerDetailsResponse, MassiveTickerDetails (raw Massive JSON shape)
    domain/         CompanyProfile, FundamentalAnalysisResult
    dto/            CompanyProfileResponse, FundamentalAnalysisResponse
```

Each feature package is self-contained (controller/service/repository/model/dto). This is
feature-oriented, not layer-oriented, organization: adding a new capability means adding a new
top-level package, not touching a shared `controllers/` or `services/` directory used by every
feature. `market/` additionally has `domain/` and `provider/` subpackages — see Section 6 for why:
that feature explicitly requires separating the external provider's DTOs, the provider-agnostic
domain model, the persistence entity, and the REST DTO, which `auth`/`user` didn't need.
`technicalanalysis/` additionally has `domain/` and `indicator/` subpackages — see Section 7 for
why: it needs the same seam between computed value and REST DTO that `market/domain/` provides,
plus a home for pure, Spring-free calculation functions kept separate from service orchestration.
`signals/` additionally has `domain/` and `rule/` subpackages for the same two reasons — `rule/` is
named differently from `indicator/` because it holds condition *rules* evaluated over
already-computed indicators, not indicator calculations themselves. `fundamentals/` has its own
`domain/` and `provider/` subpackages for the same reason `market/` does — a second, independent
provider hierarchy (`FundamentalDataProvider`/`MassiveFundamentalDataProvider`), not an extension
of `MarketDataProvider`, because fetching a company profile is unrelated to fetching candles.
Future features (`agents/`, `prediction/`, `risk/`, `portfolio/`, `backtesting/`, `notifications/`,
`websocket/`) should follow the base `controller/service/repository/model/dto` shape (adding
`domain/`/`provider/`/`indicator/`/`rule/`-style subpackages only if they have the same kind of
concrete need `market/`/`technicalanalysis/`/`signals/`/`fundamentals/` had) and only be created
when there is a concrete task for them — do not scaffold empty future packages ahead of time.

## 11. API conventions

- All endpoints are versioned under `/api/v1/...`.
- Controllers accept and return explicit request/response DTOs only — never JPA entities.
- Request DTOs use Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, etc.); invalid
  requests get a 400 with the standard error shape.
- Standard error response shape, used for every error case:
  ```json
  {
    "status": 400,
    "message": "Invalid request",
    "timestamp": "2026-01-01T00:00:00Z",
    "path": "/api/v1/auth/register"
  }
  ```
- HTTP status conventions:
  - `200` — successful GET/POST that doesn't create a resource (login, refresh, `/users/me`)
  - `201` — successful registration (resource created)
  - `204` — successful logout (no content)
  - `400` — validation errors
  - `401` — invalid credentials, invalid/expired/revoked token, missing authentication
  - `404` — resource not found; also a fundamentals request for a symbol Massive has no ticker
    record for (`CompanyNotFoundException`)
  - `409` — duplicate email on registration
  - `422` — well-formed technical-analysis request with insufficient historical candle data to
    compute the requested indicators (`InsufficientCandleDataException`)
  - `500` — unexpected server error (never leaks a stack trace or internal detail)
  - `502` — an external provider (Massive) request failed; the sanitized
    `MarketDataProviderException` (market data) or `FundamentalDataProviderException`
    (fundamentals) message is returned, never the upstream body or request URI
- Authentication requirement per endpoint must be explicit in `SecurityConfig`, matched by exact
  method + path, not inferred from a prefix.

## 12. Database conventions

- Primary keys are `UUID`, assigned application-side (`UUID.randomUUID()`), not
  database-generated. Because the `@Id` is always non-null, Spring Data JPA's default
  `isNew()` check treats every entity as already persisted and routes `repository.save()`
  through `em.merge()`, never `em.persist()`. `merge()` returns a *different* managed
  instance than the one passed in — `AuditingEntityListener` populates that returned copy, not
  your original object. Any code that needs `@CreatedDate`/`@LastModifiedDate` (or other
  listener-populated fields) immediately after creation must use the entity `save()`/
  `saveAndFlush()` returns, not the entity it was given, and must flush if it needs the value
  in the same transaction (see `AuthService.register()` for the pattern).
- Table and column names are `snake_case`.
- All schema changes go through Flyway migrations in `src/main/resources/db/migration`, named
  `V<n>__description.sql`. `spring.jpa.hibernate.ddl-auto=none` always — Hibernate never owns the
  schema.
- Timestamps (`created_at`, `updated_at`) are `timestamptz`, populated via JPA auditing
  (`@CreatedDate`/`@LastModifiedDate` + `AuditingEntityListener`, enabled once via
  `@EnableJpaAuditing`). Do not mix in `@PrePersist`/`@PreUpdate` or DB-side defaults for the same
  columns — one mechanism only.
- Constraints are defined in the migration SQL (`NOT NULL`, `UNIQUE`, foreign keys), not just
  inferred from annotations.
- `refresh_tokens.user_id` is a foreign key to `users.id` with `ON DELETE CASCADE`.
- `market_data_candles` has a unique constraint on `(symbol, timeframe, timestamp)` — it both
  prevents duplicate candle rows and serves as the index for the `symbol + timeframe + timestamp`
  query pattern, so no separate index was added. OHLCV columns are `NUMERIC`, not `DOUBLE
  PRECISION`/float — see the "never use `Double` for money" instinct applied to volume too, since
  Massive's `v` field is a JSON number, not guaranteed to be integral.

## 13. Development rules for Claude

- Read this file before making changes.
- Inspect the existing implementation before modifying it — don't assume; check.
- Preserve existing architecture unless there's a strong, stated reason to change it.
- Avoid unnecessary rewrites. A requested feature doesn't justify refactoring unrelated code.
- Don't introduce a technology not needed for the current task (see Section 3).
- Keep changes focused on the requested feature.
- Build and run tests after meaningful changes (`./gradlew.bat build`, `./gradlew.bat test`).
  Integration tests require Docker (Testcontainers starts a real Postgres container) — if Docker
  isn't available, say so explicitly rather than claiming tests passed.
- Update this file when architectural decisions or implemented functionality change.
- Never claim something was verified (built, tested, run) if it wasn't actually run.

### Code comments

Do not add comments unnecessarily. Code should communicate intent through naming and structure.

Do NOT write comments that restate the code:

```kotlin
// create the user
val user = User(email = request.email, passwordHash = hash)
```

Only comment when something is genuinely non-obvious: a security consideration, an architectural
constraint, an unusual workaround, or a business rule that can't be expressed in code — e.g. why
refresh tokens are opaque rather than JWTs, or why there's no `UserDetailsService`. Avoid
exhaustive KDoc; a one-line comment is the ceiling, not the floor.
