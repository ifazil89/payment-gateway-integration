# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Payment API (`POST /api/v1/payments`, `GET /api/v1/payments/{id}`, 3DS challenge notification) for downstream clients, processed by N-Genius (Network International): two-stage AUTH with a card or card-on-file token, including 3DS. Spring Boot 4.1 · Java 21 · Maven · OpenAPI docs (Swagger UI) · Lombok · MapStruct · Flyway · H2 (sandbox) / PostgreSQL (prod). `README.md` is detailed and authoritative for API behaviour, error codes, logging fields and configuration.

## Commands

```bash
mvn clean verify                      # compile (MapStruct + Lombok annotation processing) and run all tests
mvn test                              # needs no N-Genius credentials (NiClient is mocked)
mvn test -Dtest=PaymentApiTest        # one test class
mvn test -Dtest=PaymentApiTest#cardIsAuthorised   # one test method

# run (default profile = sandbox: N-Genius sandbox + in-memory H2), port 8085
NI_API_KEY=... NI_OUTLET_ID=... mvn spring-boot:run
mvn spring-boot:run -Dspring-boot.run.profiles=sandbox,plainlogs   # human-readable logs instead of ECS JSON
```

Swagger UI: `http://localhost:8085/swagger-ui.html` (API key `sandbox-key`). Example requests: `api-examples.http`.

## Architecture

### API code vs. OpenAPI docs
- All API code is hand-written in `src/`; nothing is generated from OpenAPI. `src/main/resources/static/openapi/payment-api.yaml` is documentation only, served as-is by Swagger UI (`springdoc.swagger-ui.url`).
- Per feature: `api/` holds the endpoint interface (`payment/api/PaymentsApi`: path, headers, `@Validated`/`@Valid`), `model/` the request/response classes with Jackson + Bean Validation annotations (`payment/model`). The shared error body is `common/model/ErrorResponse` + `ErrorDetail`.
- Nothing checks the YAML against the code. Any change to a path, header, field or validation rule must be mirrored in `payment-api.yaml` in the same change, or Swagger UI documents the wrong API.

### Layout: feature first, then layers
- `payment/` — the feature: `PaymentController` (implements `payment/api/PaymentsApi`) → `PaymentService` → `NiClient` + `PaymentRepository`. Also `PaymentRequestValidator` (cardType vs pan/token, expiry), entity `Payment`/`PaymentState`, and two MapStruct mappers: `PaymentMapper` (API model ↔ entity) and `PaymentNiMapper` (API model → N-Genius DTOs).
- `ni/` — N-Genius integration, shared by all features. `NiClient` has one method per N-Genius operation and knows only `ni/dto` types (no API models, no entities). `NiAccessTokenProvider` caches the 5-minute access token. `NiConfig` builds the pooled Apache HttpClient-backed `RestClient`.
- `common/` — cross-cutting: `ApiException` + `GlobalExceptionHandler` (the only place `ErrorResponse` is built), API-key security (`ApiKeyFilter`, `app.security.api-keys` = `client:key,...`), logging, `Amounts` (minor-unit/decimal handling).
- `threeds/` — the 3DS challenge result (see below); uses the `payment/` entity and repository.
- `order/` — retrieve order: `GET /api/v1/orders/{orderReference}` (client-scoped via `findByNiOrderReferenceAndClientId`) → `NiClient.getOrder` → `Payment.niStateRetrieved`, which only moves a pending payment (`CREATED`/`AWAITING_3DS`/`CHALLENGE_REQUIRED`) to a final state (`PaymentState.isFinal`), never undoes `CHALLENGE_REQUIRED` or a known outcome. N-Genius has no order-level state: the outcome is `_embedded.payment[].state`.
- Planned features (capture, scheduled reconciliation, webhooks, token) follow the same shape: a new package each, with its own `api/` interface (and `model/` classes), documented in the YAML, reusing `NiClient`.

### Payment flow (`PaymentService.createPayment`)
Validate → idempotency lookup by (clientId, Idempotency-Key) with a request hash (same hash = replay, different = 409 `IDEMPOTENCY_KEY_REUSED`; concurrent insert = 409 `REQUEST_IN_PROGRESS`) → save `CREATED` → N-Genius create AUTH order → pay with card / saved-card → if `AWAIT_3DS`, 3DS authentication (`transStatus C`, or still `AWAIT_3DS` with acsURL + CReq → `CHALLENGE_REQUIRED`). Key invariants:
- No DB transaction spans N-Genius calls; each step is saved separately.
- N-Genius 4xx → payment `FAILED`, HTTP 200. 5xx → 502, timeout → 504; in both cases the payment stays `CREATED`/`AWAITING_3DS` (exposed as `PENDING`) so a retry with the same Idempotency-Key is safe.
- Next-step N-Genius URLs come from the response `_links` (`NiLinks` relation names); don't build them by hand.
- `PaymentState` (internal, stored) is distinct from API `PaymentStatus`; `PaymentState.fromNi` maps N-Genius state strings.

### 3DS challenge (`threeds/`, `ThreeDsService`)
The `notificationURL` sent to N-Genius is the payment's `returnUrl` (the client's), else `ni.threeds-notification-url`.
- With `returnUrl` (e.g. mobile WebView): `GET /api/v1/threeds/{paymentId}/challenge` (API key) posts form `creq` to the stored `threeDSMethodURL` (fallback `acsURL`) via `NiClient.challengePage` (no bearer token, https only) and returns the HTML with an injected `<base href>`. The ACS posts `cres` to the client's `returnUrl`; the client sends it to `POST /api/v1/threeds/{paymentId}/challenge-response` `{cres}` (API key, client-scoped), which answers `PaymentResponse`.
- Without `returnUrl`: `CHALLENGE_REQUIRED` responses carry `threeDs{acsUrl, creq}`; the client's page posts `creq` to the ACS, which makes the browser post `cres` to `POST /api/v1/threeds/{paymentId}/notification`, answered with a small HTML page (or 303 to `returnUrl` + `paymentId`, `status`).
- Both CRes paths share `ThreeDsService.complete` (send to the stored `cnp:3ds2-challenge-response` link). Invariants:
- That path is exempt from `ApiKeyFilter` (`PUBLIC_PATH`, POST only); keep any other `/api/*` path authenticated.
- Only `CHALLENGE_REQUIRED` proceeds; the payment is first moved to `AWAITING_3DS` and saved (optimistic lock via `@Version`) so a repeated/concurrent notification never sends the CRes twice.
- Browser-facing, so N-Genius 5xx/timeout leaves `PENDING` (no 502/504); 4xx → `FAILED`. Clients read the trusted result via `GET /api/v1/payments/{id}` (scoped to the client: other clients' ids are 404).
- `returnUrl` is validated per client against `app.security.return-urls` (`ReturnUrlAllowlist`: scheme/host/port equal, normalized path prefix) to prevent an open redirect; it is part of the idempotency request hash.

### Lombok conventions (hand-written classes only)
- `@Slf4j` for loggers; `@RequiredArgsConstructor` for constructor injection (write the constructor by hand only when it does more than assign fields, e.g. `NiClient`, `PaymentRequestValidator`).
- Entity `Payment`: `@Getter` + `@NoArgsConstructor`, and `@Setter` only on the fields `PaymentMapper` copies from the request; state changes go through its domain methods (`start`, `niAnswered`, `fail`, ...). No `@Data` on entities.
- API models (`*/model`), N-Genius DTOs and `@ConfigurationProperties` are Java records. Bean Validation annotations go on the record components; Jackson binds through the canonical constructor. Records with many components get Lombok `@Builder` (MapStruct then builds them through the builder).
- Exception: no `@Builder` on a record holding card data (`CardInfo`): Lombok's builder `toString()` would print the PAN/CVV. Such a record overrides `toString()` to mask them (`CardInfoTest`).
- In `pom.xml`, Lombok is listed before MapStruct in `annotationProcessorPaths`, with `lombok-mapstruct-binding`, so MapStruct sees Lombok's accessors. Keep that order.

### Mapping and persistence
- MapStruct runs with `unmappedTargetPolicy=ERROR`: any new target field that isn't mapped (or explicitly ignored) fails the build.
- Schema is owned by Flyway (`db/migration`); Hibernate `ddl-auto: validate`. Schema changes need a new `V<n>__*.sql` migration. H2 runs in `MODE=PostgreSQL`, so keep SQL Postgres-compatible.

### Logging, tracing, PCI
- ECS JSON to stdout by default (`LOG_FORMAT=logstash|gelf` alternatives), and the same JSON to a rolling file in every environment (`logging.file.name`, `logging.logback.rollingpolicy.*`; Boot's programmatic Logback setup, no `logback-spring.xml`).
- Grafana over OTLP in the sandbox only: `GrafanaLogExportConfig` attaches the OpenTelemetry Logback appender to the root logger when `management.opentelemetry.logging.export.otlp.endpoint` is set (`application-sandbox.yml`; prod has none). `opentelemetry-logback-appender-1.0` must stay on the instrumentation release built on Boot's `opentelemetry.version`. `PaymentApiTest` switches the export off. MDC carries `traceId`, `spanId`, `correlationId`, `clientId`, `paymentId` (`LogFields`). Business log lines use SLF4J fluent `log.atInfo()...addKeyValue(...)` with dotted field names (`payment.*`, `ni.*`, `http.*`) — follow that style.
- `HttpLoggingFilter` (inbound) and `RestClientLoggingInterceptor` (outbound) log masked headers/bodies. `SensitiveDataMasker` masks PAN (first 6 + last 4), CVV, tokens, auth headers. It works at any depth (nested objects, arrays, JSON inside a string value) and masks a secret field's object/array value as a whole. Any new secret field must be added to `SensitiveDataMasker.SECRET_FIELDS` (lower case).
- `PaymentApiTest.requestAndResponseAreLoggedWithoutCardData` fails the build if card data or API keys reach the log.

## Tests
- `PaymentApiTest`: full `@SpringBootTest` + MockMvc + H2 + Flyway with only `NiClient` mocked (`@MockitoBean`); uses the sandbox API key `sandbox-key`. Also asserts on captured log output.
- `PaymentMappersTest`, `SensitiveDataMaskerTest`: unit tests.

## Open items to verify against the N-Genius sandbox
Link names `payment:card`, `payment:saved-card` (the 3DS links and request bodies are confirmed by the N-Genius EMV 3DS guide); whether `merchantOrderReference` is accepted; exact N-Genius state names in `PaymentState.fromNi`.
