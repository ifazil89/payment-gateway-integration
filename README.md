# ni-payment-integration

Payment API for downstream clients, processed by **N-Genius (Network International)**.
Step 1: **`POST /api/v1/payments`**, a two-stage authorisation with a card or a card-on-file token, including 3DS authentication.

Spring Boot 4.1.1 · Java 21 · OpenAPI docs (Swagger UI) · MapStruct 1.6.3 · Lombok ·
OpenTelemetry tracing · ECS JSON logs · Flyway · PostgreSQL (H2 in sandbox)

---

## 1. Run

**Prerequisites:** JDK 21 and Maven 3.9+. Maven needs access to Maven Central.

```bash
# 1. N-Genius sandbox credentials (N-Genius portal -> Settings -> Integrations -> Service Accounts)
export NI_API_KEY='<service-account-api-key>'
export NI_OUTLET_ID='<outlet-reference>'

# 2. Build: compiles (MapStruct, Lombok) and runs the tests
mvn clean verify

# 3. Start (sandbox profile: N-Genius sandbox + in-memory H2), JSON logs
mvn spring-boot:run

#    ...or with human-readable logs for local debugging
mvn spring-boot:run -Dspring-boot.run.profiles=sandbox,plainlogs
```

| URL | What |
|---|---|
| `http://localhost:8085/swagger-ui.html` | Swagger UI of the contract. Use **Authorize** with API key `sandbox-key` |
| `http://localhost:8085/openapi/payment-api.yaml` | the contract file itself |
| `http://localhost:8085/actuator/health` | health |

- **Try it:** use [`api-examples.http`](api-examples.http) in IntelliJ or VS Code (REST Client), or curl:

  ```bash
  curl -i http://localhost:8085/api/v1/payments \
    -H 'X-Api-Key: sandbox-key' -H 'Idempotency-Key: SO-1001-1' -H 'X-Correlation-Id: demo-1' \
    -H 'Content-Type: application/json' -d @- <<'EOF'
  {"merchantOrderId":"SO-1001","cardType":"CARD",
   "card":{"pan":"<sandbox-test-card>","expiry":"2030-12","cvv":"123","cardholderName":"Jane Doe"},
   "amount":{"value":250.00,"currency":"AED"},
   "billingInfo":{"firstName":"Jane","lastName":"Doe","email":"jane@example.com"},
   "deviceInfo":{"ip":"203.0.113.10","userAgent":"Mozilla/5.0","acceptHeader":"text/html","language":"en"}}
  EOF
  ```

- **Tests:** `mvn test` needs no N-Genius credentials, because `NiClient` is mocked.
- **IDE:** enable annotation processing (MapStruct, Lombok).
- **Production:** `SPRING_PROFILES_ACTIVE=prod` with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `API_KEYS`, `NI_*` (see [Configuration](#6-configuration)).

---

## 2. Structure: feature first, then layers

```
src/main/resources/static/openapi/payment-api.yaml   API DOCUMENTATION (Swagger UI); no code is generated from it

src/main/java/com.company.payment
├── PaymentApplication
│
├── payment/                             FEATURE: authorise a payment
│   ├── api/PaymentsApi                  interface: POST /api/v1/payments, headers, @Valid
│   ├── model/                           PaymentRequest, CardInfo, CardType, Amount, BillingInfo, DeviceInfo,
│   │                                    PaymentResponse, PaymentStatus (records: JSON + Bean Validation)
│   ├── controller/PaymentController     implements PaymentsApi (one line)
│   ├── service/PaymentService           the flow, step by step
│   ├── validation/PaymentRequestValidator   cardType vs pan/token, expiry
│   ├── mapper/PaymentMapper             MapStruct: API model <-> entity
│   ├── mapper/PaymentNiMapper           MapStruct: API model -> N-Genius DTOs
│   ├── entity/Payment, PaymentState, PaymentCardType
│   └── repository/PaymentRepository
│
├── ni/                                  N-Genius, SHARED by payment / threeds / token
│   ├── client/NiClient                  every N-Genius call, one method each
│   ├── client/NiAccessTokenProvider     5-minute access token, cached
│   ├── config/NiConfig                  pooled HTTP client, timeouts, pool metrics, logging interceptor
│   ├── config/NiProperties
│   ├── dto/                             N-Genius JSON (NiOrderRequest, NiCardRequest, NiPaymentResponse, NiLinks, ...)
│   └── exception/NiException
│
└── common/                              cross-cutting, used by every feature
    ├── exception/ApiException, GlobalExceptionHandler
    ├── model/ErrorResponse, ErrorDetail  the error body of every API
    ├── logging/CorrelationIdFilter, HttpLoggingFilter, RestClientLoggingInterceptor,
    │           SensitiveDataMasker, LogFields, LoggingConfig, LoggingProperties
    ├── security/ApiKeyFilter, SecurityConfig
    └── util/Amounts
```

**Next features follow the same shape:** `threeds/{controller,service,mapper,...}` and `token/{...}`.
Each has its own `api/ThreeDsApi` / `api/TokenApi` interface and `model/` classes, documents its paths in the YAML, and reuses `ni/client/NiClient`.

**A request goes through three layers:**
- `PaymentController` (implements `PaymentsApi`)
- `PaymentService`
- `NiClient` and `PaymentRepository`

**Mapping:**
- API models (`payment/model`) map to the clean `Payment` entity, which maps to the N-Genius DTOs.
- MapStruct writes the mapping code at compile time.
- `unmappedTargetPolicy=ERROR`: a new field that nobody maps fails the build.

### Changing the API
1. Change the code: `payment/api/PaymentsApi` (path, headers), `payment/model` (fields, Bean Validation), then the mappers.
2. Update `payment-api.yaml` to match. It is documentation only: nothing checks it against the code, so a change that skips this step makes Swagger UI wrong.

---

## 3. POST /api/v1/payments

Headers:
- `X-Api-Key`: per client.
- `Idempotency-Key`: per payment attempt.
- `X-Correlation-Id`: optional.

`cardType` decides which card fields are required (one `card` object for both types):

| cardType | required | must be empty |
|---|---|---|
| `CARD` | `pan`, `expiry`, `cvv`, `cardholderName` | `token` |
| `CARD_ON_FILE` | `token` (the saved N-Genius card token), `expiry`, `cvv`, `cardholderName` | `pan` |

**The client decides the next call from `status`:**

| status | Meaning | Next API |
|---|---|---|
| `AUTHORISED` | money reserved (also after frictionless 3DS) | capture (step to come) |
| `CHALLENGE_REQUIRED` | shopper must complete a 3DS challenge | 3DS API (next step) |
| `PENDING` | N-Genius hasn't confirmed yet | status API (to come), or retry with the **same** Idempotency-Key |
| `DECLINED` / `FAILED` | refused | stop, or a new attempt with a new Idempotency-Key |

### Flow (`PaymentService.createPayment`)

| # | Step | N-Genius call | State after |
|---|---|---|---|
| 1 | Validate (cardType vs pan/token, expiry, amount decimals) | — | — |
| 2 | Same client + Idempotency-Key? Return that payment (409 if the request differs) | — | — |
| 3 | Save the payment | — | `CREATED` |
| 4 | Create a two-stage **AUTH** order | `POST /transactions/outlets/{outlet}/orders` | `CREATED` + order ref |
| 5 | `CARD`: card · `CARD_ON_FILE`: saved token | `PUT payment:card` · `PUT payment:saved-card` | `AUTHORISED` / `AWAITING_3DS` / `FAILED` |
| 6 | If `AWAITING_3DS`: 3DS authentication with `deviceInfo` | `POST cnp:3ds2-authentication` | `AUTHORISED` / `CHALLENGE_REQUIRED` |
| 7 | Save and return | — | |

**Failures:**
- **N-Genius 4xx:** `200` with `status: FAILED`.
- **N-Genius 5xx:** `502`.
- **Timeout:** `504`. The outcome is unknown, so the payment stays `PENDING`; retrying with the same Idempotency-Key is safe.

### Error response

Every non-200 response has the same body: **`ErrorResponse`**, plus **`ErrorDetail`** entries for field errors.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "correlationId": "demo-1",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2026-10-24T07:00:01.412Z",
  "errors": [
    { "field": "card.token", "message": "is required for cardType CARD_ON_FILE" },
    { "field": "card.pan",   "message": "must be empty for cardType CARD_ON_FILE" }
  ]
}
```

`errors` appears only for `VALIDATION_ERROR`; null fields are omitted.

**Where the classes are:** `common/model/ErrorResponse` and `common/model/ErrorDetail`, shared by every feature
(documented in `payment-api.yaml`, `components.schemas`). Shortened:

```java
package com.company.payment.common.model;

@Builder
public record ErrorResponse(String code, String message, String correlationId, String traceId,
                            OffsetDateTime timestamp, List<ErrorDetail> errors) { }

public record ErrorDetail(String field, String message) { }
```

They are built in exactly one place, `common/exception/GlobalExceptionHandler.build(...)`:

```java
ErrorResponse.builder()
        .code(code)
        .message(message)
        .correlationId(MDC.get(LogFields.CORRELATION_ID))
        .traceId(MDC.get(LogFields.TRACE_ID))
        .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
        .errors(errors)                                 // List<ErrorDetail>: new ErrorDetail(field, message)
        .build();
```

The field errors come from three sources, all turned into `ErrorDetail`:
- **Bean Validation on the models** (`@Pattern`, `@NotNull`, `@Size`): `card.expiry`, `billingInfo.email`, ...
- **`PaymentRequestValidator`**: cardType vs pan/token, expired card. It throws `ApiException.validation(...)`.
- **Header constraints** (`Idempotency-Key`).

Swagger UI shows an example body for every error status.

To change the error shape, edit `common/model/ErrorResponse` / `ErrorDetail`, then the YAML to match.

| HTTP | code |
|---|---|
| 400 | `VALIDATION_ERROR` (with field errors such as `card.pan`), `MALFORMED_REQUEST`, `MISSING_HEADER` |
| 401 | `UNAUTHORIZED` |
| 409 | `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS` |
| 422 | `INVALID_AMOUNT` |
| 500 | `INTERNAL_ERROR` |
| 502 | `GATEWAY_ERROR` |
| 504 | `GATEWAY_TIMEOUT` |

---

## 4. Logging and tracing

### What every log line carries

| Field | Where it comes from |
|---|---|
| `@timestamp` | log time, ISO-8601 with milliseconds |
| `log.level`, `log.logger`, `process.thread.name`, `message` | Logback |
| `service.name`, `service.version`, `service.environment` | `application.yml` (`logging.structured.ecs.service.*`) |
| `traceId`, `spanId` | OpenTelemetry (Micrometer Tracing): one trace per request, propagated to N-Genius as `traceparent` |
| `correlationId` | `X-Correlation-Id` from the client (or generated), also returned in the response header and the error body |
| `clientId` | the authenticated client (`ApiKeyFilter`) |
| `paymentId` | our payment id, from the moment the payment exists |

Every response also returns the `X-Correlation-Id` and `X-Trace-Id` headers. A client reporting a problem quotes either one, and one search finds every line.

### What is logged for one payment

```
HTTP IN  --> POST /api/v1/payments                                         headers (X-Api-Key masked), client ip
Payment created: CARD AED 250.00 for order SO-1001                         payment.* fields
HTTP OUT --> POST https://api-gateway.sandbox.../identity/auth/access-token   (Authorization masked)
HTTP OUT <-- 200 POST https://.../identity/auth/access-token (180 ms)         (access_token masked)
HTTP OUT --> POST https://.../transactions/outlets/.../orders                  request body
HTTP OUT <-- 201 POST https://.../orders (310 ms)                             response body
NI create order -> order a1b2... (312 ms)
HTTP OUT --> PUT https://.../payments/.../card                                body: pan 411111******1111, cvv ***
HTTP OUT <-- 200 PUT https://.../card (420 ms)                                response body
NI pay with card -> AWAIT_3DS (421 ms)
HTTP OUT --> POST https://.../3ds2/authentications                            browserInfo
HTTP OUT <-- 200 POST https://.../3ds2/authentications (380 ms)
NI 3DS authentication -> AWAIT_3DS (381 ms)
3DS challenge required: transStatus C, ACS https://acs...
Payment finished: CHALLENGE_REQUIRED (N-Genius state AWAIT_3DS)
HTTP IN  <-- 200 POST /api/v1/payments (1320 ms)                           request body (masked) + response body
```

| Line | Logged by | Structured fields |
|---|---|---|
| `HTTP IN` | `HttpLoggingFilter` | `http.direction=inbound`, `http.method`, `http.path`, `http.status_code`, `http.duration_ms`, `http.request.headers`, `http.request.body`, `http.response.headers`, `http.response.body` |
| `HTTP OUT` | `RestClientLoggingInterceptor` | the same, with `http.direction=outbound` and `http.url`. A timeout logs `HTTP OUT <-- FAILED ... after 30001 ms` with `error.type` |
| `NI ...` | `NiClient` | `ni.operation`, `ni.outcome`, `ni.duration_ms` |
| `Payment ...` | `PaymentService` | `payment.card_type`, `payment.amount`, `payment.state`, `payment.ni_state`, ... |

Levels:
- `INFO` for 2xx.
- `WARN` for 4xx.
- `ERROR` for 5xx and timeouts.
- `GlobalExceptionHandler` logs every rejected request with `error.code`.

### One line as JSON (ECS, the default)

```json
{"@timestamp":"2026-10-24T07:00:01.412Z","log":{"level":"INFO","logger":"com.company.payment.common.logging.RestClientLoggingInterceptor"},
 "process":{"pid":4120,"thread":{"name":"http-nio-8085-exec-1"}},
 "service":{"name":"ni-payment-integration","version":"0.1.0-SNAPSHOT","environment":"sandbox"},
 "message":"HTTP OUT <-- 200 PUT https://api-gateway.sandbox.ngenius-payments.com/.../card (420 ms)",
 "traceId":"4bf92f3577b34da6a3ce929d0e0e4736","spanId":"00f067aa0ba902b7","correlationId":"demo-1",
 "clientId":"local-client","paymentId":"7f3c2a9e-...",
 "http":{"direction":"outbound","method":"PUT","url":"https://...","status_code":200,"duration_ms":420,
         "response":{"headers":"{Content-Type=application/vnd.ni-payment.v2+json}","body":"{\"state\":\"AWAIT_3DS\",...}"}},
 "ecs":{"version":"8.11"}}
```

(The exact envelope fields are Spring Boot's ECS formatter; the field values above are illustrative.)

**Other formats** need no code change: `LOG_FORMAT=logstash` (Logstash JSON) or `LOG_FORMAT=gelf` (Graylog).
**Human-readable console** for local debugging: the `plainlogs` profile.

### PCI DSS: what is never logged

`SensitiveDataMasker` is applied to every logged body and header:

| Data | In the log |
|---|---|
| `pan` | `411111******1111` (first 6 + last 4) |
| `cvv`, `token`, `cardToken`, `access_token`, `refresh_token`, `password` | `***` |
| `Authorization`, `X-Api-Key`, `Cookie`, `Set-Cookie` headers | `***` |

- Bodies are cut at `app.logging.http.max-body-length` (10 000).
- Set `LOG_HTTP_BODIES=false` to log only the method, URL, status and time.
- `PaymentApiTest.requestAndResponseAreLoggedWithoutCardData` fails the build if a card number, CVV or API key ever reaches the log.
- Fields are masked at any depth (nested objects, arrays, JSON inside a string); a secret field whose value is an object or array is masked as a whole.
- A new secret field in a future API goes into `SensitiveDataMasker.SECRET_FIELDS` (lower case).

### Shipping logs and traces to an observability tool (later, no code change)

The app writes **one JSON object per line to stdout**, the standard input for every log pipeline.

**Logs**, pick the one your platform uses:

| Tool | How |
|---|---|
| Elastic / OpenSearch | Filebeat or Elastic Agent reads the container stdout. ECS is Elastic's own format, so fields map 1:1 |
| Grafana Loki | Promtail / Grafana Alloy reads stdout; JSON fields become labels or are queried with `| json` |
| Datadog | Datadog Agent reads stdout; set `LOG_FORMAT=logstash` if you prefer its attribute names; link traces via `traceId` |
| Splunk / CloudWatch / Azure Monitor | their agent or log driver reads stdout |
| OpenTelemetry Collector | `filelog` receiver with a JSON parser, then any exporter |

**Traces** (already created for every request and N-Genius call) are exported over OTLP by setting one variable:

```bash
MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces
TRACING_SAMPLING_PROBABILITY=1.0        # prod default 0.1
```

**Logs over OTLP instead of stdout** (optional) need three things:
1. The dependency `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`.
2. Its appender in a `logback-spring.xml`, installed at startup with `OpenTelemetryAppender.install(openTelemetry)`.
3. `MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_ENDPOINT=http://otel-collector:4318/v1/logs`.

Most teams keep stdout plus an agent; it survives a collector outage.

**Metrics:** `/actuator/metrics` includes `http.server.requests`, `http.client.requests` (N-Genius calls) and `httpcomponents.httpclient.pool.*` (the N-Genius connection pool).

---

## 5. Debugging

1. Take the `X-Correlation-Id` or `X-Trace-Id` from the response, or from the client's report.
2. Search the logs for it. You see the inbound request, every N-Genius request and response with bodies, the business lines and the final response, in order.
3. The `payment` table keeps the raw N-Genius state (`ni_state`), the order reference and the 3DS `trans_status`.

Breakpoints: `PaymentService.createPayment` (the whole flow) and `NiClient.call` (every N-Genius request).

---

## 6. Configuration

| Property | Env var | Notes |
|---|---|---|
| `ni.base-url` | `NI_BASE_URL` | sandbox default set; live: `https://api-gateway.ngenius-payments.com` |
| `ni.api-key`, `ni.outlet-id` | `NI_API_KEY`, `NI_OUTLET_ID` | from the N-Genius portal |
| `ni.threeds-notification-url` | `NI_THREEDS_NOTIFICATION_URL` | public URL; `{paymentId}` is replaced. The endpoint is built with the 3DS API |
| `app.security.api-keys` | `API_KEYS` | `client1:key1,client2:key2` (key ≥ 8 chars) |
| `logging.structured.format.console` | `LOG_FORMAT` | `ecs` (default), `logstash`, `gelf` |
| `app.logging.http.log-bodies` | `LOG_HTTP_BODIES` | `true` (default, masked) / `false` |
| `logging.structured.ecs.service.environment` | `APP_ENVIRONMENT` | shown as `service.environment` |
| `management.tracing.sampling.probability` | `TRACING_SAMPLING_PROBABILITY` | 1.0 (prod 0.1) |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | not set = no export |
| `spring.datasource.*` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | prod profile |

---

## 7. Notes

**Verify in the N-Genius sandbox:**
- the link names `payment:card`, `payment:saved-card` and `cnp:3ds2-authentication` (`NiLinks`)
- whether `merchantOrderReference` is accepted
- the exact N-Genius state names (`PaymentState.fromNi`)

Every N-Genius request and response is logged, so one sandbox run shows all of these.

**Next steps:**
1. 3DS API (challenge result, finish the authorisation)
2. Capture
3. Status / reconciliation
4. Webhooks
5. Token
