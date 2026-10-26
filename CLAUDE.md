# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Payment API (`POST /api/v1/payments`) for downstream clients, processed by N-Genius (Network International): two-stage AUTH with a card or card-on-file token, including 3DS. Spring Boot 4.1 · Java 21 · Maven · OpenAPI docs (Swagger UI) · Lombok · MapStruct · Flyway · H2 (sandbox) / PostgreSQL (prod). `README.md` is detailed and authoritative for API behaviour, error codes, logging fields and configuration.

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
- `CardInfo.toString()` masks `pan`, `token` and `cvv` (`CardInfoTest`); keep that for any model carrying card data.

### Layout: feature first, then layers
- `payment/` — the feature: `PaymentController` (implements `payment/api/PaymentsApi`) → `PaymentService` → `NiClient` + `PaymentRepository`. Also `PaymentRequestValidator` (cardType vs pan/token, expiry), entity `Payment`/`PaymentState`, and two MapStruct mappers: `PaymentMapper` (API model ↔ entity) and `PaymentNiMapper` (API model → N-Genius DTOs).
- `ni/` — N-Genius integration, shared by all features. `NiClient` has one method per N-Genius operation and knows only `ni/dto` types (no API models, no entities). `NiAccessTokenProvider` caches the 5-minute access token. `NiConfig` builds the pooled Apache HttpClient-backed `RestClient`.
- `common/` — cross-cutting: `ApiException` + `GlobalExceptionHandler` (the only place `ErrorResponse` is built), API-key security (`ApiKeyFilter`, `app.security.api-keys` = `client:key,...`), logging, `Amounts` (minor-unit/decimal handling).
- Planned features (3DS challenge result, capture, status, webhooks, token) follow the same shape: new `threeds/`, `token/` packages, each with its own `api/` interface and `model/` classes, documented in the YAML, reusing `NiClient`.

### Payment flow (`PaymentService.createPayment`)
Validate → idempotency lookup by (clientId, Idempotency-Key) with a request hash (same hash = replay, different = 409 `IDEMPOTENCY_KEY_REUSED`; concurrent insert = 409 `REQUEST_IN_PROGRESS`) → save `CREATED` → N-Genius create AUTH order → pay with card / saved-card → if `AWAIT_3DS`, 3DS authentication (`transStatus C` → `CHALLENGE_REQUIRED`). Key invariants:
- No DB transaction spans N-Genius calls; each step is saved separately.
- N-Genius 4xx → payment `FAILED`, HTTP 200. 5xx → 502, timeout → 504; in both cases the payment stays `CREATED`/`AWAITING_3DS` (exposed as `PENDING`) so a retry with the same Idempotency-Key is safe.
- Next-step N-Genius URLs come from the response `_links` (`NiLinks` relation names); don't build them by hand.
- `PaymentState` (internal, stored) is distinct from API `PaymentStatus`; `PaymentState.fromNi` maps N-Genius state strings.

### Lombok conventions (hand-written classes only)
- `@Slf4j` for loggers; `@RequiredArgsConstructor` for constructor injection (write the constructor by hand only when it does more than assign fields, e.g. `NiClient`, `PaymentRequestValidator`).
- Entity `Payment`: `@Getter` + `@NoArgsConstructor`, and `@Setter` only on the fields `PaymentMapper` copies from the request; state changes go through its domain methods (`start`, `niAnswered`, `fail`, ...). No `@Data` on entities.
- N-Genius DTOs and `@ConfigurationProperties` are Java records: no Lombok there. The API models in `*/model` are plain classes in the openapi-generator style they were created from (fluent setters, getters/setters, equals/hashCode); keep new models consistent with them.
- In `pom.xml`, Lombok is listed before MapStruct in `annotationProcessorPaths`, with `lombok-mapstruct-binding`, so MapStruct sees Lombok's accessors. Keep that order.

### Mapping and persistence
- MapStruct runs with `unmappedTargetPolicy=ERROR`: any new target field that isn't mapped (or explicitly ignored) fails the build.
- Schema is owned by Flyway (`db/migration`); Hibernate `ddl-auto: validate`. Schema changes need a new `V<n>__*.sql` migration. H2 runs in `MODE=PostgreSQL`, so keep SQL Postgres-compatible.

### Logging, tracing, PCI
- ECS JSON to stdout by default (`LOG_FORMAT=logstash|gelf` alternatives). MDC carries `traceId`, `spanId`, `correlationId`, `clientId`, `paymentId` (`LogFields`). Business log lines use SLF4J fluent `log.atInfo()...addKeyValue(...)` with dotted field names (`payment.*`, `ni.*`, `http.*`) — follow that style.
- `HttpLoggingFilter` (inbound) and `RestClientLoggingInterceptor` (outbound) log masked headers/bodies. `SensitiveDataMasker` masks PAN (first 6 + last 4), CVV, tokens, auth headers. It works at any depth (nested objects, arrays, JSON inside a string value) and masks a secret field's object/array value as a whole. Any new secret field must be added to `SensitiveDataMasker.SECRET_FIELDS` (lower case).
- `PaymentApiTest.requestAndResponseAreLoggedWithoutCardData` fails the build if card data or API keys reach the log.

## Tests
- `PaymentApiTest`: full `@SpringBootTest` + MockMvc + H2 + Flyway with only `NiClient` mocked (`@MockitoBean`); uses the sandbox API key `sandbox-key`. Also asserts on captured log output.
- `PaymentMappersTest`, `SensitiveDataMaskerTest`: unit tests.

## Open items to verify against the N-Genius sandbox
Link names `payment:card`, `payment:saved-card`, `cnp:3ds2-authentication`; whether `merchantOrderReference` is accepted; exact N-Genius state names in `PaymentState.fromNi`.
