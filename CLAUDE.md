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
- News analysis — **implemented** (persisted, symbol-scoped news retrieval via Massive, see
  Section 10)
- Sentiment analysis — **implemented** (FinBERT via Hugging Face Inference API, per-article cached,
  symbol-level aggregation, see Section 11)
- Prediction — not implemented
- Risk management — not implemented
- Signals — **implemented** (deterministic indicator-derived condition signals, see Section 8)
- Fundamentals — **implemented** (on-demand company information only via Massive, see Section 9)
- Market Intelligence Context — **implemented** (deterministic orchestration of the six capabilities
  above into one per-symbol snapshot with per-section partial-failure handling, see Section 17)
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
- Use feature-oriented package organization (see Section 12). Do not organize by technical layer
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
everything else (see Section 13) via a custom `AuthenticationEntryPoint` /
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

**Dedup strategy**: `MarketDataService.getDailyCandles` fetches from the provider, then calls
`persistNewCandles` to query `MarketDataCandleRepository.findBySymbolAndTimeframeAndTimestampIn`
for timestamps already persisted for that symbol/timeframe, filter the fetched list down to
genuinely new candles, and `saveAllAndFlush` them, before returning the full fetched list (not just
what was newly inserted) either way. Symbol input is trimmed and uppercased before both the
provider call and the dedup check, so `aapl` and `AAPL` can't produce two rows.

**Concurrency fix (race condition, fixed after being reproduced live)**: the dedup check above is a
read-then-write race — on a symbol/timeframe's first-ever fetch, two concurrent requests can each
see "no existing candles," then both attempt to insert the same rows and collide on the
`(symbol, timeframe, timestamp)` unique constraint. This was reproduced with plain concurrent `curl`
requests against a never-fetched symbol and surfaced as an uncaught 500. Fixed by moving the persist
step into `persistNewCandles`, a separate method with
`@Transactional(propagation = Propagation.REQUIRES_NEW)`, that catches the resulting
`DataIntegrityViolationException` from `saveAllAndFlush` (flushed eagerly specifically so the
violation surfaces inside the `try`, not at commit time after the method has returned) — the losing
request's candles are already durably persisted by the winner, so there's nothing to recover.
`getDailyCandles` itself carries no `@Transactional` annotation; only the isolated persist step
does. A regression test in `MarketDataServiceTest` races 8 threads against a brand-new symbol and
confirms neither an exception nor a duplicate row results. **This same pattern is reused verbatim
in `NewsService.persistNewArticles`** (see Section 10) — apply it proactively to any future
provider-backed persist-after-fetch flow rather than waiting to rediscover the race.

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

## 10. News intelligence architecture

Implemented: provider-independent, persisted, symbol-scoped financial news, sourced from
**Massive** (`GET /v2/reference/news`), exposed via `GET /api/v1/news?symbol=AAPL&limit=20`
(protected — falls through to `anyRequest().authenticated()` like every other non-auth endpoint).
This is **Phase 1 only**: normalized news retrieval and storage, nothing more.

```
Massive -> NewsDataProvider -> NewsService -> REST API
```

**Verified against Massive's real docs, not assumed**: the news endpoint is documented as
"Included in all Stocks plans" — unlike the deprecated financials endpoints (Section 9), it is
**not** entitlement-blocked under the current plan.

**Fetch strategy deliberately deviates from market-data's provider-first pattern.** Market data
(Section 6) always re-calls Massive fresh on every request; dedup only prevents duplicate rows, not
duplicate API calls. Doing the same for news would call Massive on every dashboard load across
multiple endpoints — exactly the pattern that produced real Massive rate-limit (502) responses
during this feature's own verification. Instead, `NewsService.getNews` is **DB-first with a
15-minute freshness window**: it queries persisted articles for the symbol first, and only calls
`NewsDataProvider.fetchNews` when there are fewer than `limit` persisted articles or the newest
persisted row's `createdAt` is older than `FRESHNESS_WINDOW` (`Duration.ofMinutes(15)`, a
`private val` in `NewsService`'s companion object — a `Duration` isn't a compile-time constant, so
unlike the `private const val` thresholds in `SignalAnalysisService`/`TechnicalAnalysisService`,
this one is a plain `private val`). Sparse symbols (fewer articles ever published than `limit`)
always re-check the provider — safe, since rate-limit exposure is about repeatedly-requested
*popular* symbols, which this heuristic directly addresses. After persisting, the response is
built from a **fresh re-query of the persisted set**, not the raw fetched list — this guarantees
every returned article has a real `id` and correctly backfills from older persisted articles if the
provider returns fewer than `limit` new ones on a given call.

**Reuses the Section 6 concurrency fix proactively**: `NewsService.persistNewArticles` is a
`@Transactional(propagation = Propagation.REQUIRES_NEW)` method that dedup-queries by
`(symbol, providerArticleId)`, `saveAllAndFlush`s the new rows, and catches
`DataIntegrityViolationException` — the exact pattern proven in `MarketDataService.persistNewCandles`
after that race condition was found and fixed, applied here from the start instead of waiting to
rediscover the same bug.

**Per-symbol storage, not globally deduped across symbols.** Massive's response includes a
`tickers` array (an article can cover multiple symbols), but it isn't captured — a row is tagged
with whatever symbol the request was scoped to, mirroring `market_data_candles`'s existing
per-(symbol, timeframe, timestamp) storage rather than introducing a many-to-many article↔symbol
join table. Unique identity is `(symbol, provider_article_id)`.

**`id` (Massive's), `title`, and `published_utc` are treated as the structurally-required trio** —
`MassiveNewsProvider` skips (never fabricates) any article missing one of these. This also means
`provider_article_id` can be a plain `NOT NULL` column with a plain `UNIQUE` constraint (matching
`market_data_candles`'s style) instead of a nullable-aware partial index.

**`fetchedAt` is not a separate column.** It maps onto `NewsArticleEntity`'s standard
`@CreatedDate created_at` — for this feature, "when fetched" and "when first persisted" are the
same moment, and the project's convention is a single auditing `created_at`, not bespoke
event-timestamp columns. `published_at` is always the provider's own `published_utc` and is never
rewritten, preserving point-in-time integrity for any future historical/backtesting use.

**Discovery worth flagging for a future sentiment phase**: Massive's news response already includes
a per-ticker `insights: [{ticker, sentiment, sentiment_reasoning}]` field — free, no extra API call.
Phase 1 deliberately does **not** capture or expose this (`tickers`/`keywords`/`insights`/`amp_url`/
`image_url` are not modeled in `MassiveNewsArticle` at all, matching the "only fields Orynix
actually uses" precedent from `MassiveBar`/`MassiveTickerDetails`). It's a candidate input for a
future `SentimentAnalyzer`, not acted on now — do not start consuming it without a concrete task.

**Layering**: `news/provider/massive/dto/` holds the raw Massive JSON shape
(`MassiveNewsResponse`/`MassiveNewsArticle`/`MassiveNewsPublisher`, snake_case keys like
`article_url`/`published_utc` mapped via `@JsonProperty`, see Section 3's annotations exception).
`news/domain/` holds `NewsArticle` (provider-agnostic, with a **nullable `id: UUID?`** — `null`
when freshly mapped from the provider pre-persistence, populated when mapped back from a persisted
`NewsArticleEntity`; this is a deliberate one-class deviation from `Candle`'s precedent of carrying
no id at all, because articles need to be individually addressable in the REST response and candles
don't) and `NewsResult` (envelope: `symbol` + `articles`, the same idiom `FundamentalAnalysisResult`
uses for `symbol` + `company`). `news/model/` holds `NewsArticleEntity` (table `news_articles`,
reuses `MarketDataCandle`'s exact entity shape plus a `toDomain()` method). `news/repository/` holds
`NewsArticleRepository` with `findBySymbolOrderByPublishedAtDesc(symbol, pageable)` — `Pageable` is
used purely as an internal LIMIT mechanism, not exposed as a pagination API — and
`findBySymbolAndProviderArticleIdIn` for the dedup check. `news/dto/` mirrors this into REST DTOs
(`NewsArticleResponse`/`NewsResponse`) with the established `companion object { from(domain) }`
idiom; `providerArticleId` and `fetchedAt` are never exposed (provider-specific/internal-only).

**No "not found" exception.** An unknown or obscure symbol with zero news is a normal, empty
`articles: []` response — not an error — unlike fundamentals' `CompanyNotFoundException`, because
news isn't a single required resource. `InvalidNewsLimitException` (400) guards
`limit ∉ [1, 50]` (default `20` when omitted) via explicit validation that throws, not silent
clamping, matching `InvalidSymbolException`/`InvalidTimeframeException`'s existing convention.
`NewsDataProviderException` (502) follows the same sanitized-provider-failure convention as
`MarketDataProviderException`/`FundamentalDataProviderException`.

**Provider test coverage is DTO-deserialization-only** (plain `ObjectMapper.readValue` tests on
sample JSON, no Spring context, no HTTP mocking) — a deliberate choice matching the fact that
`MassiveMarketDataProvider`/`MassiveFundamentalDataProvider` have zero direct tests either; only
fake-provider-substituted service/controller integration tests exist for those. Introducing
MockWebServer/WireMock for news alone would be an orphaned, inconsistent testing pattern. `FakeNewsProvider`
additionally tracks `callCount` (not present on the other fakes) specifically to assert the
freshness window actually skips the provider on a repeat call — the first test of its kind to need
this.

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): any AI/agent
involvement, any news endpoint beyond `GET .../news`, and any prediction/recommendation derived
from news content. Sentiment analysis (which also does not use the `insights` field discovered
above) is implemented — see Section 11.

## 11. Sentiment analysis architecture

Implemented: per-article sentiment classification and deterministic symbol-level aggregation, built
on top of the news feature (Section 10). Sourced from **FinBERT via Hugging Face's Inference API**
(`ProsusAI/finbert` — explicitly listed by Hugging Face's own docs as a recommended model for
text-classification, Apache-2.0 licensed, commercial use permitted), exposed via
`GET /api/v1/news/{newsId}/sentiment` (single article, analyze-on-demand) and
`GET /api/v1/news/sentiment?symbol=AAPL&limit=20` (symbol-level aggregate), both protected like
every other non-auth endpoint. No AI agents, no prediction, no trading recommendations — this layer
only classifies and aggregates.

```
Hugging Face -> SentimentAnalyzer -> SentimentService -> REST API
                       ^
                NewsService (for article text + lookup by id)
```

**Chosen over an LLM-based approach** specifically because sentiment here is **permanently
cacheable per article** — a given article's sentiment never changes once computed — which fits a
metered free tier (Hugging Face's free tier is ~300 req/hour) far better than an LLM API with no
free tier at all. Each article is analyzed at most once, ever, per `(analyzer, model)` pair.

**`SentimentController` maps under `/api/v1/news`** (URL namespace) while living in the top-level
`sentiment/` package (package ownership) — these are independent axes, not an oversight. Two
controllers sharing a path prefix with non-colliding sub-paths is unremarkable in Spring MVC:
`NewsController`'s bare `GET /api/v1/news` never collides with
`GET /api/v1/news/{newsId}/sentiment` or `GET /api/v1/news/sentiment`. "Analyze on GET" also has
direct precedent already — `GET /api/v1/news` itself calls Massive and persists rows on a plain GET
when its cache is stale, so this isn't a new REST-purity concession.

**One minimal, additive addition to `NewsService`**: `getArticleById(id: UUID): NewsArticle`
(throwing the new `NewsArticleNotFoundException` → 404 if missing). `SentimentService` needs
single-article lookup by id, and consistent with every cross-feature dependency chain shown in
Sections 7–10 (`technicalanalysis` only ever talks to `MarketDataService`, `signals` only to
`TechnicalAnalysisService`, and so on — never a feature reaching into another feature's repository
directly), `SentimentService` depends on `NewsService`, not `NewsArticleRepository`. No other change
to `NewsService`.

**Package layout** (`sentiment/`) combines the `provider/`+`domain/` shape from `fundamentals/`
(external provider abstraction) with the `model/`+`repository/` shape from `market/`/`news/`
(persistence), because this feature needs both — not a new pattern. The interface subpackage is
named `analyzer/`, not `provider/`: the project already names subpackages after the abstraction
they hold, not after "is this an external HTTP call" (`rule/` vs `indicator/` are both
external-free abstractions with different names for different roles); the interface here is
`SentimentAnalyzer`, so the package is `analyzer/`. The vendor-specific implementation still lives
in a `huggingface/` subfolder exactly mirroring `provider/massive/`'s internal shape. Any future
vendor-calling abstraction that isn't literally fetching market/news "data" should follow this same
naming-after-the-abstraction rule rather than defaulting to `provider/`.

**`SentimentAnalyzer` interface** exposes `analyzerName`/`modelName` as properties, not as part of
`analyze()`'s return value — an architectural requirement, not a style choice: the dedup/cache check
("does sentiment already exist for this article+analyzer+model") must run *before* `analyze()` is
ever called, since that's the entire point of caching and skipping the paid Hugging Face call. If
identity only existed in the return value, the service couldn't query the cache until after paying
for the call it was trying to avoid.

**Score normalization**: FinBERT returns three softmax probabilities (positive/negative/neutral),
not a single scalar. Orynix normalizes `score = P(positive) - P(negative)`, range `[-1, 1]`, and
`confidence` = the winning label's own probability, range `[0, 1]`. Both are `BigDecimal` at scale
4 (not technicalanalysis's scale 2, and not signals' scale 2 either) — chosen because these values
get *averaged* across many articles during aggregation, where 2-decimal rounding would visibly
clump values before averaging. All comparisons use `compareTo`, never `==` (BigDecimal equality is
scale-sensitive). Text sent to the model is `title`, or `"$title. $description"` when a description
exists — `title` alone is always sufficient input because the news feature's own provider mapper
already guarantees it's present (Section 10's structurally-required trio), so there is no "no
usable text" exception path to write for a case that cannot occur.

**Persistence** (`news_sentiments`, FK to `news_articles.id` `ON DELETE CASCADE`): unique constraint
on `(news_article_id, analyzer, model)`, not just `news_article_id` — this is what allows the same
article to be re-analyzed under a future model version without destroying the historical judgment
from the old one. **No separate index** — worked through the same way `news_articles` needed one and
`market_data_candles` didn't: the only two read patterns are a point lookup
("does a row exist for this article+analyzer+model", served directly by the unique constraint) and
a batch lookup for aggregation ("all current-model rows for a set of article ids", served by the
constraint's leftmost-prefix `news_article_id` plus an `analyzer`/`model` filter). This holds
**only because `symbol` is never denormalized onto this table** — aggregation already has the
article-id list from `NewsService.getNews`, so it never needs to query sentiment by symbol
directly; do not "fix" this by adding a symbol column or a symbol-based index without re-checking
that assumption. `sentiment` is persisted as a plain `VARCHAR`, manually mapped (`.name`/
`Sentiment.valueOf(...)`) — no `@Enumerated`, matching `MarketDataCandle.timeframe`'s precedent.
`analyzed_at` is set explicitly at construction (`Instant.now()`), not via `@CreatedDate` — sentiment
is computed once and never updated, so "analyzed" and "created" coincide by construction, but naming
the column for what it means documents why the row exists rather than reusing an audit mechanism.

**Aggregation reuses `NewsService.getNews`'s `limit`, not a new `window=7d` param.** No existing
Orynix endpoint parses a duration string anywhere; inventing one here would be a second,
inconsistent convention for one feature. `getNews` already returns the most-recent-`limit` articles
ordered by `publishedAt desc`, so aggregation's scope follows the exact same
`DEFAULT_LIMIT=20`/`MAX_LIMIT=50`/400-on-invalid rules the news endpoint already enforces, for free.

**`MAX_ARTICLES_ANALYZED_PER_REQUEST = 10`**, a `SentimentService` companion constant deliberately
smaller than news' `MAX_LIMIT = 50` — a sequential loop over the full 50 would mean a cold,
never-analyzed symbol triggers up to 50 sequential Hugging Face calls inside one HTTP request (tens
of seconds, roughly a sixth of the free tier's hourly budget in one shot). The smaller cap bounds
each request and lets coverage warm up across repeat calls — later calls hit the cache for
already-analyzed articles and only pay for the newest ones. `articleCount` in the aggregate response
reflects articles that actually have a result, never overclaiming coverage. Analysis within one
request is always sequential (`.map`), never concurrent — satisfies the "no request storms"
requirement with no `CompletableFuture`/coroutine fan-out needed.

**Aggregation math**: `positiveCount`/`negativeCount`/`neutralCount` partition `articleCount`;
`averageScore` uses the three-arg `BigDecimal.divide(count, scale, roundingMode)` overload (a bare
`.divide()` throws `ArithmeticException` on a non-terminating result, e.g. 3 articles → 1/3).
`overallSentiment` thresholds are `±0.15` (`private const val`s, mirroring
`SignalAnalysisService`'s documented-threshold style): since `score` ranges over `[-1, 1]`, 0.15
means the positive-vs-negative probability mass must differ by at least 15 points before an
aggregate is called non-neutral — wide enough that one borderline article doesn't tip a multi-article
aggregate, narrow enough that genuinely one-sided news flow still registers. **Zero-article
aggregation is pinned, not left open**: count=0, averageScore=0.0000, overallSentiment=NEUTRAL,
HTTP 200 — extending Section 10's "empty news is a normal 200, not an error" rule one hop further.

**Concurrency**: `SentimentService.persist` reuses the proven `REQUIRES_NEW` + flush + catch
`DataIntegrityViolationException` pattern from `MarketDataService`/`NewsService`, with one necessary
difference — since `NewsService.getNews` masks its race by re-querying its whole result set
afterward, but a single-article lookup has no natural re-query, `persist`'s catch block explicitly
re-reads the winning row instead of returning a result whose `id`/`analyzedAt` were never actually
persisted.

**`SentimentResponse` exposes `analyzer`/`model`** (meaningful ML provenance, and exactly what the
unique constraint is keyed on — hiding them would make a re-analyzed article's historical rows
indistinguishable) **but not the sentiment row's own `id`** (nothing addresses a sentiment row by
its own id, only by article or symbol) — unlike `providerArticleId`/`fetchedAt` on the news side,
which stay fully internal.

**Fixed a latent bug while adding this feature's first `@PathVariable`**: `GET /api/v1/news` has
always had no handler for `MethodArgumentTypeMismatchException` (thrown by Spring when a
`@RequestParam`/`@PathVariable` can't be converted to its declared type, e.g. `limit=abc` or a
malformed UUID path segment), so it fell into the generic catch-all and returned `500` instead of
`400`. Sentiment's new `@PathVariable newsId: UUID` hits the identical failure mode on a malformed
UUID, so the fix (one handler mapping it to `400`) was made here, with a regression test added to
the pre-existing `NewsControllerTest`, not only to the new sentiment tests.

**Provider test coverage is Hugging Face HTTP-level-free**, same call already made for
`MassiveNewsProvider`/`MassiveMarketDataProvider`/`MassiveFundamentalDataProvider` (none of which
have HTTP-level tests either — only fake-substituted integration tests). `HuggingFaceSentimentAnalyzer`'s
score-normalization logic lives in a `companion object` function specifically so a pure, Spring-free
test can call it directly with hand-built label/score fixtures, without needing
MockWebServer/WireMock or a live Hugging Face call.

**Configuration**: `HUGGING_API_KEY` (required, no default — a secret), `HUGGINGFACE_BASE_URL`
(optional, defaults to `https://router.huggingface.co/hf-inference/models` — Hugging Face's 2025
"Inference Providers" consolidation; verify this is still current if Hugging Face changes its
routing again), and `HUGGINGFACE_MODEL` (optional, defaults to `ProsusAI/finbert`), bound via
`HuggingFaceProperties` (`@ConfigurationProperties(prefix = "huggingface")`, registered through the
existing `@ConfigurationPropertiesScan` — must not also be `@Component`, same `MassiveProperties`
constructor-binding-conflict reason, see Section 3).

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): any AI/agent
involvement, prediction, and trading recommendations derived from sentiment — this layer reports
classified sentiment and a deterministic aggregate, nothing more.

## 12. Package structure

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
  news/
    controller/    NewsController
    service/       NewsService (DB-first fetch + freshness window + dedup + persist)
    provider/       NewsDataProvider interface
      massive/       MassiveNewsProvider
        dto/          MassiveNewsResponse, MassiveNewsArticle, MassiveNewsPublisher
    domain/         NewsArticle, NewsResult
    model/          NewsArticleEntity (table news_articles)
    repository/     NewsArticleRepository
    dto/            NewsArticleResponse, NewsResponse
  sentiment/
    controller/    SentimentController (maps under /api/v1/news — see Section 11)
    service/       SentimentService (get-or-analyze + aggregation)
    analyzer/       SentimentAnalyzer interface
      huggingface/    HuggingFaceSentimentAnalyzer, HuggingFaceProperties
        dto/           HuggingFaceSentimentRequest, HuggingFaceSentimentScore
    domain/         Sentiment, AnalyzedSentiment, SentimentResult, SentimentAggregate
    model/          NewsSentimentEntity (table news_sentiments)
    repository/     NewsSentimentRepository
    dto/            SentimentResponse, SentimentAggregateResponse
  marketintelligence/
    controller/    MarketIntelligenceController
    service/       MarketIntelligenceService (orchestrates the six services above)
    domain/         ContextSection, ContextSectionStatus, MarketDataSummary,
                     MarketIntelligenceContext
    dto/            ContextSectionResponse, MarketDataSummaryResponse,
                     MarketIntelligenceContextResponse
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
`news/` has `domain/`+`provider/` (like `fundamentals/`, for the external-provider abstraction) and
`model/`+`repository/` (like `market/`, since news is persisted) — combining two already-established
patterns because it needs both, not a new one. `sentiment/` has the same `domain/`+`analyzer/` and
`model/`+`repository/` combination as `news/`, for the same reason — but its interface subpackage is
named `analyzer/` rather than `provider/`, since the project names subpackages after the abstraction
they hold (compare `rule/` vs `indicator/`, two differently-named external-free abstractions over
the same kind of input), and this one holds a `SentimentAnalyzer`, not a `*DataProvider`. `marketintelligence/` has `domain/`+`dto/` only (no `provider/`/`model/`/`repository/`/`analyzer/`)
— unlike every other feature so far, it introduces no new external provider and persists nothing of
its own; it exists purely to orchestrate the six services above, so it only needs the seam between
its own assembled result and its REST DTO (see Section 17).

Future
features (`agents/`, `prediction/`, `risk/`, `portfolio/`, `backtesting/`, `notifications/`,
`websocket/`) should follow the base `controller/service/repository/model/dto` shape (adding
`domain/`/`provider/`/`analyzer/`/`indicator/`/`rule/`-style subpackages only if they have the same
kind of concrete need `market/`/`technicalanalysis/`/`signals/`/`fundamentals/`/`news/`/`sentiment/`
had) and only be created when there is a concrete task for them — do not scaffold empty future
packages ahead of time.

## 13. API conventions

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
  - `400` — validation errors; also an invalid `limit` on `/api/v1/news`/`/api/v1/news/sentiment`
    (`InvalidNewsLimitException`), and any malformed `@RequestParam`/`@PathVariable` that can't be
    converted to its declared type — e.g. `limit=abc` or a malformed UUID in
    `/api/v1/news/{newsId}/sentiment` (`MethodArgumentTypeMismatchException`)
  - `401` — invalid credentials, invalid/expired/revoked token, missing authentication
  - `404` — resource not found; also a fundamentals request for a symbol Massive has no ticker
    record for (`CompanyNotFoundException`), and a sentiment request for a well-formed but unknown
    news article id (`NewsArticleNotFoundException`). A news request for a symbol with no articles,
    or a sentiment aggregate for a symbol with no articles, is **not** a 404 — both are a normal
    `200` with an empty `articles` array / a pinned neutral zero-state respectively.
  - `409` — duplicate email on registration
  - `422` — well-formed technical-analysis request with insufficient historical candle data to
    compute the requested indicators (`InsufficientCandleDataException`)
  - `500` — unexpected server error (never leaks a stack trace or internal detail)
  - `502` — an external provider request failed; the sanitized `MarketDataProviderException`
    (market data), `FundamentalDataProviderException` (fundamentals), `NewsDataProviderException`
    (news), or `SentimentAnalyzerException` (sentiment) message is returned, never the upstream body
    or request URI
- Authentication requirement per endpoint must be explicit in `SecurityConfig`, matched by exact
  method + path, not inferred from a prefix.

## 14. Database conventions

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
- `news_articles` has a unique constraint on `(symbol, provider_article_id)` (the dedup identity,
  see Section 10) plus a separate composite index on `(symbol, published_at)` — unlike
  `market_data_candles`, a second index is justified here because the unique constraint's columns
  don't already serve the read query, which sorts by `published_at` while being keyed by
  `provider_article_id`.
- `news_sentiments` has a unique constraint on `(news_article_id, analyzer, model)` (see Section 11)
  and **no separate index** — unlike `news_articles`, this table's only two read patterns (a point
  lookup by the full constraint, and a batch lookup for aggregation filtered the same way) are both
  already served by the constraint's leftmost-prefix `news_article_id`. This depends on `symbol`
  never being denormalized onto this table — re-check that assumption before adding a symbol column
  or index. `NewsSentimentEntity` does not use `@CreatedDate`/`AuditingEntityListener` like every
  other entity in this codebase — `analyzed_at` is set explicitly at construction instead, since
  sentiment is computed once and never updated, so reusing the audit-timestamp mechanism would
  obscure rather than clarify why the column exists.

## 15. Development rules for Claude

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

## 16. Web client architecture

The web client (`web/`) is deliberately **vanilla JS/HTML/CSS with no bundler, no package
manager, and no framework** — just native ES modules loaded via `<script type="module">`. This was
a conscious choice, not an oversight: the app is small enough that a build step would add
infrastructure for no concrete benefit, consistent with Section 3's "don't introduce a technology
not needed for the current task." Do not add webpack/Vite/npm/React/etc. without a concrete need
that vanilla ES modules can no longer satisfy.

**Must be served over http(s), never opened as `file://`.** `SecurityConfig.corsConfigurationSource`
only allows `http://localhost:*` / `http://127.0.0.1:*` origins; `fetch` from a `file://` origin
fails CORS preflight. Serve `web/` with any static file server, e.g. from inside `web/`:
`python -m http.server 5500` or `npx serve .`, then open `http://localhost:5500`.

**File layout**:

```
web/
  index.html        shell: a single #app-root mount point, nothing else
  style.css          dark-first theme (light theme via @media (prefers-color-scheme: light)),
                     sidebar/topbar layout primitives, responsive breakpoints at 900px/600px
  app.js             boot: silent session restore, registers routes, starts the router
  router.js          hand-rolled hash router (#/path?symbol=X) — no routing library for 5 routes
  state.js           session (tokens/user), current symbol, the signal-type filter selection
                     (see the signal filter dropdown note below), a 60s-TTL per-(symbol,endpoint)
                     response cache, and a small pub/sub used by the router and shell
  api.js             fetch wrappers for every backend endpoint the web client uses; apiRequest
                     transparently retries once through a refresh-token exchange on a 401
  format.js          number/date/enum formatters, escapeHtml/isSafeHttpUrl (see XSS note below)
  signalCatalogue.js static catalogue of the 8 real SignalType identifiers + labels + descriptions
                     (see the signal filter dropdown note below) — not fetched, since the backend
                     exposes no catalogue endpoint
  stockUniverse.js   static, hand-curated list of a few well-known {symbol, name} pairs (see the
                     stock picker dropdown note below) — not fetched, since the backend exposes no
                     symbol-search/watchlist/instrument-search endpoint; not a whitelist either
  chart.js           hand-rolled canvas price chart: candlesticks + volume strip + hover tooltip
                     + a client-side 1M/3M/6M/1Y/All range selector that slices the already-
                     fetched candle array (no extra requests); falls back to a line/area render
                     when the visible slice has too many candles to render legibly
  components/        shell (sidebar+topbar+mobile nav), symbolSearch (+ the opt-in stock picker and
                     signal filter dropdowns it can host), stockPickerDropdown,
                     signalFilterDropdown, metricCard, signalBadge, newsItem (incl. lazy
                     per-article sentiment), sentimentPanel, stateBlock (shared loading/error/empty
                     rendering for a whole request: distinct 400/422/404/502 states),
                     contextSection (per-section AVAILABLE/NO_DATA/ERROR rendering for one
                     ContextSectionResponse — see the market-intelligence note below)
  pages/             auth, overview, analysis, signals, news, company — one module per route,
                     each exporting mount(container, params) -> optional cleanup function
```

**Session persistence**: the access token lives in memory only; the refresh token (opaque, not a
JWT — see Section 5) is persisted in `localStorage`. On boot, `app.js` calls `GET /users/me`
without an access token on purpose — the resulting 401 drives `apiRequest`'s existing retry path,
which exchanges the stored refresh token for a new access/refresh pair via `POST /auth/refresh`
and retries. This reuses one code path for both "silent restore on reload" and "ordinary access
token expiry mid-session," rather than two parallel mechanisms. A failed refresh (expired/revoked
token, or no stored token) clears the session and the router falls back to the auth page; a
network/provider failure at boot deliberately leaves the stored refresh token alone, since the
backend being briefly unreachable shouldn't silently discard a user's otherwise-valid session.

**Cross-tab refresh token rotation is handled explicitly, not assumed away.** Refresh tokens
rotate on every use (Section 5): the presented token is revoked server-side and a new one issued.
Each browser tab holds its own in-memory copy of the current refresh token, so if tab A rotates
while tab B still holds the now-revoked value, tab B's next refresh attempt gets a legitimate 401
for a session that is, in fact, still alive (just rotated elsewhere) — without handling this,
tab B would wrongly treat it as a dead session and clear everything, including tab A's still-valid
one, since both tabs share the same `localStorage`. Two mechanisms fix this: (1) `state.js`
listens for the browser's `storage` event and updates its in-memory refresh token whenever another
tab on the same origin changes it, so a stale in-memory copy self-corrects before it's even used;
(2) `api.js`'s `refreshAccessToken` additionally retries once with whatever `getRefreshToken()`
returns *after* a failed attempt, in case another tab rotated it in the gap between this tab
reading it and the request landing. Only clears the session if a retry with the current token also
fails. `state.js` also wraps all `localStorage` access in try/catch — some browser privacy modes
throw on access rather than just being empty — falling back to an in-memory-only session (a reload
will require login again, but the app doesn't crash) rather than letting an uncaught exception
break the whole module's import.

**One page per capability, now backed by the shared market-intelligence endpoint, not five
separate calls.** This superseded an earlier "per-route API fan-out, no shared bulk endpoint"
design, once Section 17's `GET /market-intelligence` existed to fan out server-side instead. The
Market Analysis, Signals, Company Info, and News & Sentiment pages each now call
`getMarketIntelligence(symbol)` exactly once and read their own slice of the response
(`technicalAnalysis`, `signals`, `companyInformation`, `news`/`sentiment` respectively) — `api.js`
no longer exports `getTechnicalAnalysis`/`getSignals`/`getFundamentals`/`getNews`/
`getSentimentAggregate`, only `getMarketIntelligence`, `getCandles`, and `getArticleSentiment`.
The Market Analysis page is the one exception that still calls `getCandles` directly, alongside
`getMarketIntelligence`, because `MarketDataSummaryResponse` deliberately omits the full candle
series the price chart needs (Section 17) — this means loading that page triggers **four** Massive
calls server-side per load (one from the direct candles call, three more from the three internal
`MarketDataService` calls inside `marketData`/`technicalAnalysis`/`signals` sections of the context
orchestration — see Section 17's documented 3x re-fetch characteristic), not the one call it might
look like from the browser's network tab. All four pages share one `state.js` cache entry keyed
`(symbol, "market-intelligence")` — navigating from Signals to Company Info to News for the same
symbol within the 60s TTL reuses that single cached response rather than each page fetching its
own, a direct benefit of consolidating onto one endpoint. "Refresh" buttons still bypass the cache
explicitly, re-fetching the whole context (there is no way to refresh one section only).

**Per-section status is rendered by `contextSection.js`, layered on top of `stateBlock.js`, not
instead of it.** A `ContextSectionResponse` never carries an HTTP-error status of its own (Section
17: a degraded section is absorbed into the enclosing 200) — only `AVAILABLE`/`NO_DATA`/`ERROR` and
a fixed backend `message`. Each of the four pages still wraps its one `getMarketIntelligence` call
in the ordinary `stateBlock.js`/`renderStateBlock` pattern to handle that *outer* request failing
outright (network error, 400 malformed symbol, 401), and only once that resolves to `"ok"` does it
hand each section of the parsed context to `contextSection.js`/`renderContextSection`, which switches
on `AVAILABLE` (render the data), `NO_DATA` (muted status text, no retry — matching the existing
422 convention below, since more history can't be conjured by retrying), or `ERROR` (an error block
whose retry button re-runs the *whole* context fetch — there is no per-section retry endpoint).
Each page also surfaces `context.asOf` next to its content (`.section-asof` in `style.css`) as a
visible "as of" timestamp, since the four pages' data can now legitimately be a few seconds stale
relative to each other within one response (Section 17's sequential-fetch design).

**Sentiment fetching respects the Hugging Face free-tier cap (Section 11).** The News & Sentiment
page no longer calls a standalone aggregate endpoint — `context.sentiment` already carries it
(warmed for up to 10 articles server-side by the same orchestration call). Per-article sentiment is
still never fetched in a loop over the list — each news item renders an "Analyze sentiment" button
and only calls `GET /news/{id}/sentiment` when a user clicks it, so page load cost doesn't scale
with article count.

**The stock picker dropdown is a discovery shortlist, not a live symbol search — there is no
backend endpoint for that either.** No controller anywhere (`MarketDataController`,
`FundamentalAnalysisController`, etc.) exposes a symbol catalogue, watchlist, or instrument-search
capability; every one of them takes a `symbol` the caller already knows and attempts it against
Massive directly. `stockUniverse.js` is therefore a small hand-curated `{symbol, name}` list (AAPL,
IBM, MSFT, NVDA, GOOGL, AMZN) — each entry verified live against the real
`GET /api/v1/fundamentals` before being added, not assumed — searchable by ticker or company name
in `stockPickerDropdown.js`'s panel. It is explicitly **not** a whitelist: the plain text search
box next to it still accepts and submits any correctly-formatted ticker (`isValidSymbol` in
`format.js`), whether or not that symbol happens to appear in the curated list. Selecting an item
calls the exact same `submitSymbol`/`onSubmit` path the typed-and-Enter flow already uses (defined
once in `symbolSearch.js`, not duplicated in the dropdown) — the same `navigate()` → `hashchange` →
router remount pipeline every existing symbol change already goes through, which is also why no new
stale-response handling was needed: every page module's existing `alive` flag (set false by its
`mount()`-returned cleanup before the next page mounts) already discards a late-arriving fetch for
a symbol the user has since navigated away from. `withStockPicker` is opt-in on `createSymbolSearch`
and is passed from both `shell.js`'s persistent topbar search and `overview.js`'s local search —
unlike the signal filter below, the stock picker is most useful exactly where no symbol is selected
yet, which is `overview.js`'s whole purpose. `overview.js`'s pre-existing hardcoded AAPL/IBM example
chips were left as-is; they're a duplicate of a strict subset of this feature, but removing them
wasn't asked for and isn't necessary for this to work.

**The signal filter dropdown filters the current symbol's signals; it does not discover stocks
across a market, because the backend has no capability to do that.** `SignalAnalysisController`
(Section 8) takes a single required `symbol` and always computes all 8 `SignalType` values fresh
for it — there is no catalogue endpoint and no multi-symbol/market-wide scan endpoint anywhere in
the backend. `signalCatalogue.js` is therefore a small hand-maintained array of the real 8
`SignalType` identifiers, human labels, and descriptions restating each rule's actual thresholds
(read directly from `signals/rule/*.kt` and `SignalAnalysisService`'s companion constants — RSI
70/30, Bollinger near-band 1%, ATR 1%/3%, volume ratio 1.5/0.5, momentum lookback 10 candles / 1%
flat band) — not fetched, since nothing exists to fetch it from, and not invented, since every
number in it is copied from the rule that actually computes it. If a future task wants true
cross-market signal discovery ("find every stock currently showing a bullish MACD crossover"), that
requires new backend work first — at minimum an endpoint that evaluates signals across a defined
symbol universe — and is explicitly out of scope here; this feature does not pretend otherwise.

`signalFilterDropdown.js` provides a "Filters ▾" trigger + popover, wired into `symbolSearch.js` via
an opt-in `withSignalFilter` parameter that only `shell.js`'s persistent topbar search control
passes — `overview.js`'s local search intentionally does not get it, since that page may have no
current symbol for the filter to apply to. Both dropdown triggers share one `.search-dropdown-toggle`
CSS class for identical sizing/hover/active styling, but each keeps distinct visible text ("Stocks
▾" vs "Filters ▾") rather than a bare icon — two unlabeled chevrons sitting next to each other would
make the stock picker and the signal filter visually indistinguishable, which is exactly what
having two separate dropdown controls next to the same search box must not do. `symbolSearch.js`
also wires each dropdown's `onOpen` callback to close its sibling explicitly, so only one of the
two is ever open at a time — correct behavior already falls out of how each one's own
outside-click handling reacts to a genuine click on the other trigger, but making the exclusion
explicit means it doesn't depend on reasoning through click-event-phase ordering to stay true. The
panel's own `<input>`/checkboxes/buttons are
siblings of the symbol-search `<form>`, never children of it, specifically so an Enter keypress
while typing in the panel's name-filter box can never trigger an implicit submit of the unrelated
symbol search — those two actions (Section 7: "search submission and signal filtering remain
distinct actions") share no DOM form. Selecting checkboxes only updates a pending, uncommitted set;
clicking Apply commits it to `state.js`'s `selectedSignalTypes` (reset on logout like
`currentSymbol`) and closes the panel; closing without Apply (Escape, outside click, or the × button)
discards the pending edits and reverts to whatever was last applied — "preserving selections" means
preserving the last *applied* set, not whatever was mid-edit when the panel closed. Clicking Clear
commits an empty selection immediately, with no separate Apply step, since clearing has no
meaningful "pending" state of its own.

Because `state.js`'s `notify()` already drives `router.js`'s full current-route re-render on every
state change (the same mechanism that already re-renders every page on login/logout/symbol change),
applying or clearing the filter needs no explicit navigation call of its own — `signals.js` reads
`getSelectedSignalTypes()` fresh inside its own render path on every (re)mount and filters the
already-fetched 8 signals down to the selected types, showing a "Filtered by…" banner with its own
inline Clear control. If the applied filter's page isn't currently mounted, nothing is visibly
different until the user navigates there (via the sidebar, or the panel's own "View signals for
{symbol}" link when a symbol is selected) — applying a filter deliberately never force-navigates
away from whatever page the user is on. The AND/OR "match semantics" question in Section 6 does not
apply here: with only one symbol in scope, selecting multiple signal types is a display filter over
that symbol's fixed 8 rows (show the subset whose type was checked), not a multi-signal match test
against a stock — there is nothing to AND or OR until a market-wide endpoint exists.

**XSS note — this is the first feature rendering third-party text via `innerHTML`.** News
`title`/`description`/`author`/`source` originate from Massive, not from Orynix's backend logic
directly. `format.js` exports `escapeHtml` (applied to all such fields before interpolation) and
`isSafeHttpUrl` (checked before any article `url` is emitted as an `<a href>`, to reject
`javascript:`-scheme values). Backend-authored strings (enum names, error `message`s, which
`GlobalExceptionHandler` already guarantees never leak internals — Section 13) are not escaped,
matching the trust boundary: only provider-sourced text needs it.

**Error states are mapped by HTTP status, not shown as one generic error.** `stateBlock.js`
distinguishes 422 ("not enough history yet" — informational, no retry button, since more history
can't be conjured by retrying) from 502 ("provider unavailable," with a retry button) from 404
("no data found") from everything else (generic error + retry). After the market-intelligence
migration above, 422/404/502 from `stateBlock.js` are reachable only via the *outer*
`getMarketIntelligence` request itself (network failure, etc. — Section 17 never lets a single
section produce one of these) and via the Market Analysis page's separate `getCandles` call; the
per-section equivalent — e.g. a thinly traded symbol where technical analysis has enough candles
but signals doesn't (Signals needs 51 via `getAnalysisHistory`; TA needs 50) — now renders as two
independently-handled `contextSection.js` states (one `AVAILABLE`, one `NO_DATA`) within the same
page, not as two different pages each showing their own HTTP error.

**Known limitations**: no automated frontend test suite exists (no `package.json`, so no
Jest/Vitest/etc.) — this is intentional per Section 2/3's "don't add infrastructure speculatively";
verification is manual (load the app, exercise each page, check the console/network tab). There is
no symbol-autocomplete endpoint, so the symbol search has nothing to debounce against — it
validates and submits on enter/click only, against the same `^[A-Z.]{1,10}$` pattern the backend
uses.

## 17. Market Intelligence Context

Implemented: a deterministic orchestration layer that calls the six existing per-symbol
capabilities (market data, technical analysis, signals, fundamentals, news, sentiment) and
assembles their results into one snapshot, exposed via
`GET /api/v1/market-intelligence?symbol=AAPL` (protected, same as every other non-auth endpoint).
This is the data foundation a future AI Analyst Agent will consume — **not** the agent itself. No
LLM involvement, no generated commentary, no predictions, nothing beyond what the six underlying
services already compute.

```
MarketIntelligenceService
  -> MarketDataService          (candle summary)
  -> TechnicalAnalysisService   (indicators)
  -> SignalAnalysisService      (conditions)
  -> FundamentalAnalysisService (company info)
  -> NewsService                (articles)
  -> SentimentService           (aggregate sentiment)
```

**Package layout** (`marketintelligence/`) follows the base `controller/service/domain/dto` shape —
no `provider/model/repository`, since nothing new is persisted and no new external provider is
introduced; this feature only orchestrates six already-existing services.

**Partial-failure model, not all-or-nothing.** Each section is wrapped in
`ContextSection<T>(status, data, message, retrievedAt)`, where `status` is one of `AVAILABLE`,
`NO_DATA`, or `ERROR` (`marketintelligence/domain/ContextSectionStatus.kt`). A private
`runSection` helper in `MarketIntelligenceService` routes each underlying exception deliberately:

- `InvalidSymbolException` (plus the never-actually-reachable `InvalidTimeframeException`/
  `InvalidNewsLimitException`, since timeframe is hardcoded and limit is never user-supplied — see
  below) are **not caught** — they mean the request itself is malformed, not that one section's
  data happens to be unavailable, so they propagate to `GlobalExceptionHandler`'s existing 400
  mapping and fail the whole request. Symbol is validated/normalized once, at the top of the
  service, before any section runs (same regex this codebase already duplicates in
  `FundamentalAnalysisService`/`NewsService` rather than sharing a validator — see Section 9's
  precedent for why).
- `InsufficientCandleDataException`, `CompanyNotFoundException` → `NO_DATA` — a successful lookup
  that legitimately found nothing or not enough.
- `MarketDataProviderException`, `FundamentalDataProviderException`, `NewsDataProviderException`,
  `SentimentAnalyzerException` → `ERROR`.
- A call that succeeds but returns structurally-empty data (zero candles, zero news articles,
  zero-article sentiment aggregate) is also downgraded to `NO_DATA` after the fact — this can't be
  generic, since "empty" means something different per domain type, so each section's own mapping
  checks it explicitly. In particular, `MarketDataService.getDailyCandles` can return an empty list
  without throwing; naively taking the latest candle would throw `NoSuchElementException` (mapped
  by the existing handler to a 404 that would silently kill the *entire* aggregate request) — the
  market-data section guards this explicitly instead.
- Every section's `message` is a **fixed, hardcoded string per exception type**, never interpolated
  `ex.message` — one uniform rule, so a future wording change to any underlying exception can never
  leak anything new into a 200 response body.
- `MarketIntelligenceService` is deliberately **not** `@Transactional` — none of the six underlying
  services are, and wrapping this orchestration in one shared transaction would mean a caught
  per-section exception marks it rollback-only, so a later section's otherwise-successful commit
  would throw `UnexpectedRollbackException` and turn one degraded section into a 500 for the whole
  response, defeating the entire design above.

**This endpoint never returns 502/422/404 for a single degraded section** — those statuses still
apply to the six *standalone* feature endpoints exactly as before; here they're absorbed into a
200 with a per-section `ERROR`/`NO_DATA` status instead. Only a malformed symbol (400) or missing
auth (401) fail the request as a whole.

**Response reuses the existing per-feature DTOs** as each section's payload
(`ContextSectionResponse<TechnicalAnalysisResponse>`, `<TechnicalSignalSummaryResponse>`,
`<FundamentalAnalysisResponse>`, `<NewsResponse>`, `<SentimentAggregateResponse>`) rather than
inventing flattened shapes — this guarantees each section's JSON is byte-identical to that
feature's own standalone endpoint by construction. The one new payload,
`MarketDataSummaryResponse`, intentionally does **not** embed the full 365-candle series (that
stays the job of `GET /market-data/candles`) — just `latestCandle`, `candleCount`, and the
earliest/latest timestamps, to keep the aggregate response a reasonable size.

**Provenance**: each section's `retrievedAt` is captured by the orchestrator at the moment it calls
that section — truthful "when this was fetched," not a fabricated provider timestamp. This is the
only timestamp `FundamentalAnalysisResult`, `SentimentAggregate`, and `NewsResult` carry at all
(none of the three have their own). `Candle`/`TechnicalAnalysisResult`/`TechnicalSignalSummary`
already have their own embedded timestamp, left untouched inside `data`. The top-level `asOf` marks
when the orchestration request started — the response never claims all six sections represent the
same instant, since they're fetched sequentially.

**Verified, non-obvious latency characteristics** (read from the actual service code, not
estimated): `MarketDataService.getDailyCandles` has no freshness/DB-first check and calls Massive
unconditionally on *every* call — since this endpoint calls it directly for the market-data summary
*and* `TechnicalAnalysisService`/`SignalAnalysisService` each independently re-call it internally,
one request to this endpoint means **3 separate Massive candle requests, every time**, not just on
a cold cache. Separately, `NewsService.isFresh` only skips re-fetching once a symbol has 20+
persisted articles — below that, both this endpoint's direct `news` section call *and*
`SentimentService.getAggregate`'s internal `NewsService.getNews` call hit the provider, so a sparse
symbol means **2 real news-provider round-trips** per request, not one cached + one free DB hit.
Sentiment can additionally make up to 10 sequential Hugging Face calls for never-before-analyzed
articles (permanently cached per article afterward, per Section 11).

**Sequential execution, deliberately not parallel.** Threads wouldn't fix either dominant cost
above (the 3x candle re-fetch is a `MarketDataService`-layer characteristic; a cold sentiment
section's Hugging Face calls would still bound total latency even run alongside the other five).
Against that: the `Fake*Provider` test doubles this project's entire test suite relies on
(`support/Fake*.kt`) are plain unsynchronized fields with no thread-safety, so parallelizing here
would make call-count assertions racy and require touching shared test infrastructure every other
feature's tests depend on, for one feature's benefit. If cold-request latency proves unacceptable in
practice, the right fixes are a shared candle fetch at the `MarketDataService` layer or an
async/polling response shape — not a thread pool bolted onto this orchestrator. Neither is
implemented; both are explicitly deferred, not forgotten.

**Not exposed as query parameters**: `timeframe` (hardcoded to `"1d"` internally — `CandleTimeframe`
has only one value today, see Section 6) and `limit` for news/sentiment (both called with `null`,
which each service already defaults to 20 internally, see Sections 10–11). Adding either would be
implementable but serves no concrete need yet, per Section 2's "don't build ahead of a stated task."

**Not yet implemented** (do not build ahead of a concrete task, per Section 2): the actual AI
Analyst Agent and any LLM involvement, price prediction, trading recommendations, candle-fetch
deduplication across market data/technical analysis/signals, and any async/polling response shape
for cold-request latency.
