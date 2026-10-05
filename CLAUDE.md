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
- Market data — not implemented
- Technical analysis — not implemented
- AI agents — not implemented
- News analysis — not implemented
- Sentiment analysis — not implemented
- Prediction — not implemented
- Risk management — not implemented
- Signals — not implemented
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
- JWT signing deliberately uses **jjwt-gson**, not jjwt-jackson, specifically to avoid pulling a
  Jackson 2 dependency onto the classpath next to Spring's Jackson 3. Do not "simplify" this to
  jjwt-jackson later without re-checking this still holds.
- Starter artifact names changed too (e.g. `spring-boot-starter-webmvc` instead of the old
  `spring-boot-starter-web`, `spring-boot-starter-flyway` instead of a bare `flyway-core`
  dependency). This is intentional, not a mistake to "fix" back to Boot 3 naming.

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

## 4. Architecture principles

- The backend is the single source of truth. Android and web clients communicate only through
  the backend API.
- Use feature-oriented package organization (see Section 6). Do not organize by technical layer
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
everything else (see Section 7) via a custom `AuthenticationEntryPoint` /
`AccessDeniedHandler` — not Spring Security's default body.

## 6. Package structure

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
```

Each feature package is self-contained (controller/service/repository/model/dto). This is
feature-oriented, not layer-oriented, organization: adding a new capability means adding a new
top-level package, not touching a shared `controllers/` or `services/` directory used by every
feature. Future features (`market/`, `analysis/`, `agents/`, `prediction/`, `risk/`, `signals/`,
`portfolio/`, `backtesting/`, `notifications/`, `websocket/`) should follow the same internal
shape and only be created when there is a concrete task for them — do not scaffold empty future
packages ahead of time.

## 7. API conventions

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
  - `404` — resource not found
  - `409` — duplicate email on registration
  - `500` — unexpected server error (never leaks a stack trace or internal detail)
- Authentication requirement per endpoint must be explicit in `SecurityConfig`, matched by exact
  method + path, not inferred from a prefix.

## 8. Database conventions

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

## 9. Development rules for Claude

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
