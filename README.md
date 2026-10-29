# Orynix

Orynix is an AI-assisted market intelligence platform: technical analysis, signals, news/sentiment,
an LLM-backed market analysis agent, deterministic risk calculation, historical backtesting, and a
simulated paper-trading engine, all sitting behind a single Spring Boot API and a lightweight web
client. It covers both **stocks** and **forex** spot pairs.

Orynix is a learning/portfolio project, not a production trading system. Every "trade" it ever
executes is either a historical replay (backtesting) or a simulated fill against end-of-day data
(paper trading) — it never touches a real broker or real money.

## Table of contents

- [What it does](#what-it-does)
- [Tech stack](#tech-stack)
- [How the app works](#how-the-app-works)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [API surface](#api-surface)
- [Testing](#testing)
- [Known limitations](#known-limitations)

## What it does

| Capability | Status |
|---|---|
| Auth (register/login/refresh/logout) | ✅ |
| Historical market data (stocks + forex, daily candles) | ✅ |
| Technical analysis (SMA/EMA/RSI/MACD/Bollinger/ATR/volume) | ✅ |
| Deterministic condition signals (trend, crossovers, momentum, etc.) | ✅ |
| Company fundamentals | ✅ stocks only |
| News retrieval | ✅ stocks only |
| Sentiment analysis (FinBERT) | ✅ stocks only |
| Market Intelligence Context (one aggregated snapshot per symbol) | ✅ |
| AI Market Analysis Agent (Gemini-backed) | ✅ |
| Backtesting (6 strategies, optional ATR risk management) | ✅ |
| Manually-entered trading account profiles + risk calculator | ✅ |
| Trade setup & decision workflow | ✅ (backend only, no frontend) |
| Paper trading (virtual accounts, long/bounded-short positions) | ✅ |
| Prediction, portfolio management, notifications, WebSockets | ❌ not implemented |
| Crypto | ❌ not implemented |
| Real broker integration / real-money execution | ❌ never planned |

## Tech stack

### Backend

- **Kotlin** 2.3.21 on **Java 21**
- **Spring Boot** 4.1.1 (Spring Framework 7 / Spring Security 7)
  - `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`,
    `spring-boot-starter-validation`, `spring-boot-starter-actuator`
  - `spring-boot-starter-flyway` (schema migrations) + `flyway-database-postgresql`
  - `spring-boot-restclient` (provides the `RestClient.Builder` used for every external HTTP call)
- **PostgreSQL** 16 as the primary datastore, **Spring Data JPA** / Hibernate as the ORM
- **JWT** auth via `io.jsonwebtoken` (jjwt), signed HS256; refresh tokens are opaque, rotated,
  stored only as a SHA-256 hash
- **Jackson 3** (`tools.jackson.*`) for JSON (Boot 4 ships Jackson 3, not Jackson 2)
- **Gradle** (Kotlin DSL) as the build tool
- **Testcontainers** (real Postgres in Docker) + JUnit 5 for integration tests

### External providers

- **Massive** (`api.massive.com`, formerly Polygon.io) — historical OHLCV candles, company
  reference data, news
- **Hugging Face Inference API** (`ProsusAI/finbert`) — article-level sentiment classification
- **Google Gemini** — the AI Market Analysis Agent's language model

### Frontend

- **Vanilla JavaScript, HTML, and CSS** — no framework, no bundler, no npm dependency tree.
  Deliberate: the app is small enough that a build step buys nothing. Everything is native ES
  modules loaded via `<script type="module">`.
- A hand-rolled hash router, a hand-rolled canvas candlestick/volume chart, and a hand-rolled
  canvas equity-curve chart — no charting library.
- Dark/light theming entirely via CSS custom properties, following the OS preference by default
  with a manual override.

### Infrastructure

- **Docker Compose** for a local Postgres instance (the only infra container this project needs —
  no Redis, Kafka, message queues, or extra services).

## How the app works

```
                      ┌────────────────────────┐
                      │   Web client (web/)     │
                      │  vanilla JS, ES modules  │
                      └────────────┬─────────────┘
                                   │ fetch (JWT bearer)
                                   ▼
                      ┌────────────────────────┐
                      │   Spring Boot API        │
                      │ (REST, JSON, /api/v1/*)  │
                      └────────────┬─────────────┘
                                   │
                 ┌─────────────────┼─────────────────┐
                 ▼                 ▼                 ▼
          ┌────────────┐   ┌──────────────┐   ┌─────────────┐
          │ PostgreSQL  │   │ Massive API   │   │ Hugging Face │
          │ (Flyway-    │   │ (candles,     │   │ (sentiment)   │
          │  managed)   │   │  news, co.    │   └─────────────┘
          └────────────┘   │  reference)   │
                            └──────────────┘
                                   │
                                   ▼
                            ┌──────────────┐
                            │ Google Gemini │
                            │ (AI analysis) │
                            └──────────────┘
```

**Backend is the single source of truth.** Both the web client and the (planned) Android client
talk only to the same REST API and the same auth/account system — there is no client-specific
backend logic.

**Request pipeline for a typical symbol lookup:**

```
Massive → MarketDataProvider → MarketDataService → TechnicalAnalysisService → SignalAnalysisService
                                                   ↘ FundamentalAnalysisService (stocks)
                                                   ↘ NewsService → SentimentService (stocks)
```

`MarketIntelligenceService` calls all of the above once per symbol and returns one aggregated
snapshot (`GET /api/v1/market-intelligence`), with each section's status reported independently
(`AVAILABLE` / `NO_DATA` / `ERROR`) rather than failing the whole request if one data source is
degraded. The four main analysis pages in the web client (Market Analysis, Signals, Company,
News & Sentiment) all consume this single endpoint.

**The AI Market Analysis Agent** (`POST /api/v1/market-analyses`) builds a bounded "evidence
context" out of that same market-intelligence data, sends it to Gemini with strict structured-JSON
output, and returns a narrative assessment plus optional trade-setup proposals. The model is never
allowed to do its own monetary arithmetic — any proposed position size is computed exclusively by
the deterministic `RiskCalculatorService`, using the model's proposed entry/stop/take-profit levels
as input, after the model's response has already been validated for internal consistency and
plausibility against real market data.

**Backtesting** (`POST /api/v1/backtests`) replays one of six rule-based strategies — moving
average crossover, RSI threshold, MACD crossover, Bollinger Bands mean reversion, Donchian channel
breakout, or ADX trend — bar-by-bar over historical daily candles, with configurable commission,
slippage, and an optional ATR-based stop-loss/trailing-stop/risk-based-sizing layer. Entries/exits
only ever fill at the *next* bar's open relative to the signal that triggered them, to avoid
look-ahead bias.

**Paper trading** (`/api/v1/paper-trading/...`) is a separate, isolated simulation: virtual
accounts with their own cash ledger, long and bounded-short positions, idempotent order submission,
and account-level pessimistic locking for concurrency safety. Every fill happens against the latest
*available* daily close (explicitly disclosed as delayed/end-of-day, since Orynix has no real-time
quote feed) — never a live price. It is deliberately disconnected from backtesting, from the
manually-entered trading account profiles, and from any AI-proposed (but unexecuted) trade setup.

**Everything else** — trading account profiles, the standalone risk calculator, the trade setup &
decision workflow — exists to connect the pieces above: save an idea, evaluate its risk on demand,
record a decision, record an outcome, without ever claiming a real trade happened.

## Project structure

```
orynix/
├── src/main/kotlin/com/cephCoding/orynix/
│   ├── auth/              register/login/refresh/logout, JWT, security config
│   ├── user/               current-user lookup
│   ├── instrument/         shared symbol classification (stock vs. forex)
│   ├── market/             candle fetching, normalization, persistence
│   ├── technicalanalysis/  SMA/EMA/RSI/MACD/Bollinger/ATR calculators + service
│   ├── signals/            deterministic condition rules over technical analysis
│   ├── fundamentals/       company reference data
│   ├── news/               persisted, symbol-scoped news
│   ├── sentiment/          FinBERT-backed per-article sentiment + aggregation
│   ├── marketintelligence/ orchestrates the six services above into one snapshot
│   ├── backtesting/        strategy engine, persistence, diagnostics
│   ├── tradingaccount/     manually-entered account profiles
│   ├── riskcalculator/     standalone entry/stop/target risk & sizing calculator
│   ├── agents/             the Gemini-backed AI Market Analysis Agent
│   ├── tradesetup/         trade-idea save/review/decide/close workflow
│   ├── papertrading/       virtual accounts, orders, positions, ledger
│   └── common/             shared exceptions, error shape, JPA auditing
├── src/main/resources/db/migration/   Flyway SQL migrations (schema source of truth)
├── src/test/kotlin/                   unit + Testcontainers-backed integration tests
├── web/
│   ├── pages/              one module per route (overview, analysis, signals, news,
│   │                       company, backtests, trading-accounts, ai-analysis, paper-trading, auth)
│   ├── components/         shell/nav, symbol search, state blocks, metric cards, etc.
│   ├── api.js               fetch wrappers for every backend endpoint the client uses
│   ├── state.js             session, current symbol, theme, response cache, pub/sub
│   ├── router.js             hand-rolled hash router
│   ├── chart.js / equityCurveChart.js   hand-rolled canvas charts
│   └── style.css             dark/light design tokens + every component's styling
├── docker-compose.yml       local Postgres
└── build.gradle.kts
```

Each backend feature is a **self-contained, feature-oriented package** (`controller/service/
repository/model/dto`, plus `domain/`/`provider/`/`engine/`/`rule/`-style subpackages only where a
feature has a concrete, repeated need for them) — never organized by technical layer at the top
level. See `CLAUDE.md` for the full, section-by-section architectural record of *why* each decision
was made, including several bugs that were found and fixed during development (a market-data race
condition under concurrent first-fetch, a backtest look-ahead-bias ordering bug, a stale-profile
seeding bug, and others).

## Getting started

### Prerequisites

- JDK 21
- Docker (for Postgres)
- A modern browser
- API keys for: [Massive](https://massive.com) (market data/news/fundamentals), [Hugging
  Face](https://huggingface.co) (sentiment), and [Google
  Gemini](https://ai.google.dev) (AI analysis)

### 1. Start Postgres

```bash
DB_PASSWORD=<pick-a-password> docker compose up -d
```

### 2. Configure environment variables

Required (no defaults — the app fails fast at startup without these):

| Variable | Purpose |
|---|---|
| `DB_PASSWORD` | Postgres password (must match what you started the container with) |
| `JWT_SECRET` | HS256 signing key for access tokens |
| `MASSIVE_API_KEY` | Massive API key |
| `HUGGING_API_KEY` | Hugging Face API key |
| `ORYNIX_GEMINI_API_KEY` | Google Gemini API key |

Optional, with sensible defaults (see `src/main/resources/application.yml`): `DB_URL`,
`DB_USERNAME`, `MASSIVE_BASE_URL`, `HUGGINGFACE_BASE_URL`, `HUGGINGFACE_MODEL`, `GEMINI_BASE_URL`,
`GEMINI_MODEL`, `GEMINI_TIMEOUT_MS`, `PAPER_TRADING_MAX_PRICE_STALENESS_DAYS`,
`PAPER_TRADING_SHORT_COLLATERAL_MULTIPLIER`, `JWT_ACCESS_TOKEN_TTL_SECONDS`,
`JWT_REFRESH_TOKEN_TTL_SECONDS`.

### 3. Run the backend

```bash
./gradlew bootRun       # macOS/Linux
./gradlew.bat bootRun   # Windows
```

Flyway runs all migrations automatically on startup. The API listens on `http://localhost:8080`;
`GET /actuator/health` is a good smoke test.

### 4. Serve the web client

The web client **must** be served over `http://`, never opened as a `file://` URL (CORS blocks
that). From inside `web/`:

```bash
python -m http.server 5500
# or: npx serve .
```

Open `http://localhost:5500`, register an account, and you're in.

## API surface

All endpoints are versioned under `/api/v1/...` and return a consistent error shape (`status`,
`message`, `timestamp`, `path`) on failure. Every endpoint except `register`/`login`/`refresh`
requires a `Bearer` access token.

| Area | Key endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Market data | `GET /market-data/candles` |
| Technical analysis / signals | `GET /technical-analysis`, `GET /signals` |
| Fundamentals / news / sentiment | `GET /fundamentals`, `GET /news`, `GET /news/sentiment` |
| Aggregated snapshot | `GET /market-intelligence` |
| AI analysis | `POST /market-analyses` |
| Backtesting | `POST /backtests`, `GET /backtests`, `GET /backtests/{id}`, `GET /backtests/{id}/trades` |
| Trading account profiles | `POST/GET/PUT/DELETE /trading-accounts[/{id}]` |
| Risk calculator | `POST /risk-calculations` |
| Trade setups | `POST/GET/PUT/DELETE /trade-setups[/{id}]`, plus `/decision`, `/closure`, `/risk-evaluation` |
| Paper trading | `POST/GET/PUT/DELETE /paper-trading/accounts[/{id}]`, plus `/summary`, `/refresh`, `/orders`, `/positions[/closed]`, `/positions/{id}/close`, `/ledger` |

## Testing

```bash
./gradlew.bat build   # or ./gradlew build
./gradlew.bat test    # or ./gradlew test
```

Integration tests spin up a real Postgres container via Testcontainers — Docker must be running.
The frontend has no automated test suite (no `package.json`, no bundler) by design; it's verified
by syntax-checking every module (`node --input-type=module --check`) and manual exercise in a
browser.

## Known limitations

- Prediction, portfolio management (holdings/cash ledger across assets), notifications, and
  WebSockets/live updates are not implemented.
- Crypto is out of scope.
- News, sentiment, and company fundamentals are stock-only — not available for forex under the
  current Massive plan/data model.
- No real broker integration anywhere, ever: trading account profile balances are manually entered
  and never verified; paper trading is a virtual, delayed/end-of-day simulation; backtests are
  historical replays. None of them place a real order.
- The trade setup & decision workflow has a backend API but no web client page yet.
- Paper trading has no automatic stop-loss/take-profit monitoring (stored and validated, never
  auto-triggered — manual close only) and no margin-call/forced-liquidation engine.

For the full, exhaustively-documented architectural history of this project — including every
non-obvious decision, every bug found and fixed, and every deliberate scope cut — see `CLAUDE.md`.
