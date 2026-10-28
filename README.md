# ni-payment-integration

Payment API for downstream clients, processed by **N-Genius (Network International)**.
**`POST /api/v1/payments`**: a two-stage authorisation with a card or a card-on-file token, including EMV 3DS 2.x
(frictionless and challenge). **`GET /api/v1/payments/{paymentId}`**: the current status.

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

# 3. Start (sandbox profile: N-Genius sandbox + in-memory H2): readable console, each event's message with its
#    fields (HTTP request/response JSON, masked) on an indented line; full JSON in logs/ni-payment-integration.log
mvn spring-boot:run

#    ...or also with traceId/spanId/clientId and full timestamps
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
│   ├── api/PaymentsApi                  interface: POST /api/v1/payments, GET /api/v1/payments/{id}, headers, @Valid
│   ├── model/                           PaymentRequest, CardInfo, CardType, Amount, BillingInfo, DeviceInfo,
│   │                                    PaymentResponse, ThreeDsChallenge, PaymentStatus (records: JSON + Bean Validation)
│   ├── controller/PaymentController     implements PaymentsApi (one line)
│   ├── service/PaymentService           the flow, step by step
│   ├── validation/PaymentRequestValidator   cardType vs pan/token, expiry
│   ├── mapper/PaymentMapper             MapStruct: API model <-> entity
│   ├── mapper/PaymentNiMapper           MapStruct: API model -> N-Genius DTOs
│   ├── entity/Payment, PaymentState, PaymentCardType
│   └── repository/PaymentRepository
│
├── threeds/                             FEATURE: the 3DS challenge (uses payment/entity + repository)
│   ├── api/ThreeDsApi                   interface: GET .../challenge (HTML), POST .../challenge-response (client),
│   │                                    POST .../notification (browser, no API key)
│   ├── model/ChallengeResponseRequest   {"cres"}
│   ├── controller/ThreeDsController     HTML page / payment JSON / 303 to returnUrl or a small result page
│   └── service/ThreeDsService           challenge page + the challenge result, step by step
│
├── order/                               FEATURE: retrieve order (live N-Genius status; updates a pending payment)
│   ├── api/OrdersApi                    interface: GET /api/v1/orders/{orderReference}
│   ├── model/OrderStatusResponse
│   ├── controller/OrderController
│   ├── mapper/OrderMapper               MapStruct: N-Genius order + payment -> OrderStatusResponse
│   └── service/OrderService             the steps below
│
├── ni/                                  N-Genius, SHARED by payment / threeds / order / token
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
    ├── security/ApiKeyFilter, SecurityConfig, ReturnUrlAllowlist
    └── util/Amounts
```

**Next features follow the same shape** as `payment/` and `threeds/`, e.g. `capture/{api,controller,service,...}`.
Each has its own `api/` interface (and `model/` classes when it has its own JSON), documents its paths in the YAML, and reuses `ni/client/NiClient`.

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

`returnUrl` (optional) is where the card issuer's ACS posts the 3DS challenge result (`cres`): it is sent to
N-Genius as the `notificationURL`. Without it, our notification endpoint is used. It must start with one of the
client's entries in `app.security.return-urls` (scheme, host and port equal, path under the entry's path);
otherwise `400 VALIDATION_ERROR` on `returnUrl`. This keeps the redirect from being an open redirect.

**The client decides the next call from `status`:**

| status | Meaning | Next API |
|---|---|---|
| `AUTHORISED` | money reserved (also after frictionless 3DS) | capture (step to come) |
| `CHALLENGE_REQUIRED` | shopper must complete a 3DS challenge (`threeDs` in the response) | [3DS challenge](#3ds-challenge), then `GET /api/v1/payments/{paymentId}` |
| `PENDING` | N-Genius hasn't confirmed yet | `GET /api/v1/orders/{orderReference}` (asks N-Genius), `GET /api/v1/payments/{paymentId}` later, or retry with the **same** Idempotency-Key |
| `DECLINED` / `FAILED` | refused | stop, or a new attempt with a new Idempotency-Key |

### Flow (`PaymentService.createPayment`)

| # | Step | N-Genius call | State after |
|---|---|---|---|
| 1 | Validate (cardType vs pan/token, expiry, amount decimals) | — | — |
| 2 | Same client + Idempotency-Key? Return that payment (409 if the request differs) | — | — |
| 3 | Save the payment | — | `CREATED` |
| 4 | Create a two-stage **AUTH** order | `POST /transactions/outlets/{outlet}/orders` | `CREATED` + order ref |
| 5 | `CARD`: card · `CARD_ON_FILE`: saved token | `PUT payment:card` · `PUT payment:saved-card` | `AUTHORISED` / `AWAITING_3DS` / `FAILED` |
| 6 | If `AWAITING_3DS`: 3DS authentication with `deviceInfo` | `POST cnp:3ds2-authentication` | `AUTHORISED` / `CHALLENGE_REQUIRED` (stores acsUrl, creq and the `cnp:3ds2-challenge-response` link) |

Step 6 follows the [N-Genius EMV 3DS 2.x guide](https://docs.ngenius-payments.com/reference/emv-3ds-3ds-2x-integration-2):
`transStatus C` means a challenge. Any other `transStatus` is frictionless, unless N-Genius still answers `AWAIT_3DS`;
then the challenge step is needed too (when `acsURL` and `base64EncodedCReq` are present). Without challenge data the
payment stays `PENDING` (logged as a warning). `AUTHORIZED` (US spelling, used in the guide) is read as `AUTHORISED`.

**Sandbox 3DS test cards** (any future expiry, any 3-digit CVV):

| PAN | Result |
|---|---|
| `2303779999000275` | frictionless, authenticated |
| `2303779999000291` | frictionless, failed (`N`) |
| `2303779999000317` | frictionless, failed (`U`) |
| `2303779999000408` | challenge, authenticated |
| `2303779999000424` | challenge, failed (`N`) |
| `2303779999000432` | challenge, failed (`U`) |
| 7 | Save and return | — | |

**Failures:**
- **N-Genius 4xx:** `200` with `status: FAILED`.
- **N-Genius 5xx:** `502`.
- **Timeout:** `504`. The outcome is unknown, so the payment stays `PENDING`; retrying with the same Idempotency-Key is safe.

### 3DS challenge (with `returnUrl`, e.g. a mobile app)

```
client (app / backend)                           this service                         N-Genius / issuer
 POST /payments -------------------------------> CHALLENGE_REQUIRED  (notificationURL = returnUrl)
 GET /threeds/{id}/challenge ------------------> POST creq (form) ---------------------> threeDSMethodURL
 <-- text/html (challenge page) ---------------- <-- HTML
 shows it in a WebView; shopper enters the OTP -----------------------------------------> issuer ACS
 returnUrl <-- POST cres ----------------------------------------------------------------- issuer ACS
 POST /threeds/{id}/challenge-response {cres} -> POST cnp:3ds2-challenge-response -----> N-Genius
 <-- PaymentResponse: AUTHORISED / DECLINED / FAILED / PENDING
```

1. `GET /api/v1/threeds/{paymentId}/challenge` (API key; `ThreeDsService.challengePage`): only in `CHALLENGE_REQUIRED`
   (else `409 CHALLENGE_NOT_REQUIRED`). Posts the form field `creq` = `base64EncodedCReq` to the stored
   `threeDSMethodURL` (`acsURL` if N-Genius sent none) and returns the HTML with a `<base href>` added, so its
   relative links still point to the 3DS server. No access token is sent there; https only. The payment does not
   change, so it can be called again. Failures: 422 / 502 / 504 as for N-Genius.
2. The ACS posts `cres` to the client's `returnUrl`.
3. `POST /api/v1/threeds/{paymentId}/challenge-response` `{"cres": "..."}` (API key, own payments only):
   the steps of `ThreeDsService` below, answered with the `PaymentResponse`.

> The 3DS server sees our backend, not the shopper's device, when the page is loaded: cookies it sets
> during that POST stay with our HTTP client, and its IP check sees our IP instead of the `browserIP` sent at
> authentication. Test the whole challenge with the sandbox challenge cards before going live.

### 3DS challenge (without `returnUrl`, browser)

```
client backend      shopper's browser                 this service                     N-Genius / issuer
 POST /payments ------------------------------------> CHALLENGE_REQUIRED + threeDs{acsUrl, creq}
 renders form ---->  POST creq --------------------------------------------------------> issuer ACS (OTP, app...)
                     POST cres ---------------------> /api/v1/threeds/{id}/notification
                                                      POST cnp:3ds2-challenge-response -> N-Genius
                     <-- small result page
 GET /payments/{id} --------------------------------> AUTHORISED / DECLINED / FAILED / PENDING
```

1. The client renders, in the shopper's browser, a form posting `creq` to `threeDs.acsUrl` (HTML in the YAML, `ThreeDsChallenge`).
2. The issuer makes the browser post `cres` to `ni.threeds-notification-url` = `POST /api/v1/threeds/{paymentId}/notification`.
   **This path has no API key** (`ApiKeyFilter.PUBLIC_PATH`): the caller is a browser. It only acts on a payment in
   `CHALLENGE_REQUIRED`, found by its unguessable id, and N-Genius verifies the CRes.
3. `ThreeDsService.completeChallenge` (the same steps serve `challenge-response` above):

| # | Step | N-Genius call | State after |
|---|---|---|---|
| 1 | Find the payment; anything but `CHALLENGE_REQUIRED` gets its current status (repeated post, no N-Genius call) | — | — |
| 2 | Claim it, saved under the optimistic lock (`@Version`): a concurrent post loses and gets the current status | — | `AWAITING_3DS` (`PENDING`) |
| 3 | Send the CRes | `POST cnp:3ds2-challenge-response` `{base64EncodedCRes}` | `AUTHORISED` / `DECLINED` / `FAILED` |
| 4 | 303 to `returnUrl` + `paymentId` + `status`, or a small HTML page without `returnUrl` | — | |

The caller is a browser, so N-Genius problems are not 502/504 here: **4xx** -> `FAILED`;
**5xx / timeout** -> stays `PENDING` (outcome unknown, the CRes is not sent again). In every case the client reads the
trusted status with `GET /api/v1/payments/{paymentId}`; the `status` in the redirect can be changed by the shopper.

### GET /api/v1/payments/{paymentId}

The stored status (same `PaymentResponse` as the POST; `threeDs` only while `CHALLENGE_REQUIRED`).
A client only sees its own payments: another client's id is `404 PAYMENT_NOT_FOUND`.

### GET /api/v1/orders/{orderReference} (retrieve order)

The live state at N-Genius ([retrieve order status](https://docs.ngenius-payments.com/reference/retrieve-order-status)),
for the client's own orders (`orderReference` from the payment response; another client's is `404 ORDER_NOT_FOUND`).
`OrderService.retrieveOrder`:

| # | Step | N-Genius call | Payment after |
|---|---|---|---|
| 1 | Find our payment by order reference + client | — | — |
| 2 | Retrieve the order; pick our payment in `_embedded.payment` by its reference (there is no order-level state) | `GET /transactions/outlets/{outlet}/orders/{ref}` | — |
| 3 | `Payment.niStateRetrieved`: a pending payment takes a final N-Genius outcome; saved under the optimistic lock | — | `AUTHORISED` / `DECLINED` / `FAILED`, or unchanged |
| 4 | Answer `OrderStatusResponse` | — | |

- **Use it** after `PENDING` (e.g. a timeout on the card or 3DS call) to learn the outcome N-Genius already has.
- **Never undoes anything:** a known outcome stays (N-Genius `CAPTURED` / `REVERSED` show only in `gatewayState`),
  and `CHALLENGE_REQUIRED` is not moved back by N-Genius' `AWAIT_3DS`.
- **Errors:** N-Genius 4xx → 422, 5xx → 502, timeout → 504; nothing is changed, so it is safe to call again.
- `gatewayState` is N-Genius' raw payment state; `cardBrand` and `maskedPan` come from N-Genius (also for card-on-file).

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
| 400 | `VALIDATION_ERROR` (with field errors such as `card.pan`, `returnUrl`), `MALFORMED_REQUEST`, `MISSING_HEADER`, `MISSING_CRES` |
| 401 | `UNAUTHORIZED` |
| 404 | `PAYMENT_NOT_FOUND`, `ORDER_NOT_FOUND` |
| 409 | `IDEMPOTENCY_KEY_REUSED`, `REQUEST_IN_PROGRESS`, `CHALLENGE_NOT_REQUIRED` |
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

One line per event; every HTTP exchange is ONE line, written when it is answered, carrying the request and the
response (headers and bodies, masked). The messages, as on the sandbox console (there each one is followed by
an indented line with its fields, e.g. `http.request.body={...} ... http.response.body={...}`):

```
Payment created: CARD AED 250.00 for order SO-1001                         payment.* fields
HTTP OUT <-- 200 POST https://.../identity/auth/access-token (180 ms)         (Authorization, access_token masked)
HTTP OUT <-- 201 POST https://.../orders (310 ms)                             request + response body
NI create order -> order a1b2... (312 ms)
HTTP OUT <-- 200 PUT https://.../card (420 ms)                                request: pan 411111******1111, cvv ***
NI pay with card -> AWAIT_3DS (421 ms)
HTTP OUT <-- 200 POST https://.../3ds2/authentications (380 ms)               request: browserInfo
NI 3DS authentication -> AWAIT_3DS (381 ms)
3DS challenge required: transStatus C, ACS https://acs...
Payment finished: CHALLENGE_REQUIRED (N-Genius state AWAIT_3DS)
HTTP IN  <-- 200 POST /api/v1/payments (1320 ms)                           client ip, request headers + body, response
```

The start of each exchange (`HTTP IN  -->`, `HTTP OUT -->`) is logged at `DEBUG` only, e.g. to see a request that
hangs: `LOGGING_LEVEL_COM_COMPANY_PAYMENT_COMMON_LOGGING=DEBUG`.

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
**Human-readable console:** the sandbox default (message + fields, bodies included); the `plainlogs` profile adds
traceId, spanId, clientId and full timestamps.

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

### Where the logs go

How OpenTelemetry and Grafana fit together (signals, the path of a log line, collector or not, queries):
[docs/opentelemetry-grafana.md](docs/opentelemetry-grafana.md).

| Destination | Environments | Format |
|---|---|---|
| stdout | production | ECS JSON (`LOG_FORMAT`), for a log shipper |
| stdout | sandbox | readable text: `time level [correlationId] [paymentId] logger : message`, then (if the event has fields) one indented line with them: the masked request/response JSON and headers for HTTP lines, `payment.*` / `ni.*` for business lines (`LOG_CONSOLE_FORMAT=ecs` for JSON; profile `plainlogs` adds traceId, spanId, clientId) |
| Rolling file `logs/ni-payment-integration.log` (`LOG_FILE`) | all | same JSON as stdout (also with `plainlogs`) |
| Grafana, over OTLP | **sandbox only** (production has no Grafana yet) | OTLP log records |

**Rolling file:** rolled every day and at 50 MB into `<file>.yyyy-MM-dd.<n>.gz`. The files are kept for 14 days,
2 GB in total at most (`LOG_FILE_MAX_SIZE`, `LOG_FILE_MAX_HISTORY`, `LOG_FILE_TOTAL_SIZE_CAP`). In a container,
mount `logs/` on a volume, or the files are lost with the container.

**Grafana (sandbox):** `GrafanaLogExportConfig` attaches the OpenTelemetry Logback appender; Boot's OTLP log
exporter sends the records to `GRAFANA_OTLP_LOGS_ENDPOINT` (default `http://localhost:4318/v1/logs`):
- Any OTLP/HTTP receiver works: Loki 3 (`http://<loki>:3100/otlp/v1/logs`), Grafana Alloy or an OpenTelemetry
  collector (`http://<host>:4318/v1/logs`), Grafana Cloud (its OTLP gateway URL, plus
  `MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_HEADERS_AUTHORIZATION="Basic <instanceId:token base64>"`).
- Resource attributes: `service.name=ni-payment-integration`, `deployment.environment.name=sandbox`.
  Record attributes: the MDC (`traceId`, `spanId`, `correlationId`, `clientId`, `paymentId`) and the key-values
  (`payment.*`, `ni.*`, `http.*`, `error.*`). In Grafana: `{service_name="ni-payment-integration"} | correlationId="..."`.
- The lines are masked before they are written (see PCI DSS above), so Grafana gets the same masked content as stdout.
- `GRAFANA_LOGS_ENABLED=false` switches it off (e.g. running the sandbox profile locally without Grafana;
  otherwise the exporter logs connection warnings). Tests switch it off.
- For production later: set `management.opentelemetry.logging.export.otlp.endpoint` in `application-prod.yml`
  (or `MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_ENDPOINT`); no code change.

**Metrics:** `/actuator/metrics` includes `http.server.requests`, `http.client.requests` (N-Genius calls) and `httpcomponents.httpclient.pool.*` (the N-Genius connection pool).

---

## 5. Debugging

1. Take the `X-Correlation-Id` or `X-Trace-Id` from the response, or from the client's report.
2. Search the logs for it. You see the inbound request, every N-Genius request and response with bodies, the business lines and the final response, in order.
3. The `payment` table keeps the raw N-Genius state (`ni_state`), the order reference and the 3DS `trans_status`.

Breakpoints: `PaymentService.createPayment` (the whole flow), `ThreeDsService.completeChallenge` (the challenge result)
and `NiClient.call` (every N-Genius request).

---

## 6. Configuration

| Property | Env var | Notes |
|---|---|---|
| `ni.base-url` | `NI_BASE_URL` | sandbox default set; live: `https://api-gateway.ngenius-payments.com` |
| `ni.api-key`, `ni.outlet-id` | `NI_API_KEY`, `NI_OUTLET_ID` | from the N-Genius portal |
| `ni.threeds-notification-url` | `NI_THREEDS_NOTIFICATION_URL` | public URL of `POST /api/v1/threeds/{paymentId}/notification`; `{paymentId}` is replaced. Must be reachable from shoppers' browsers |
| `app.security.api-keys` | `API_KEYS` | `client1:key1,client2:key2` (key ≥ 8 chars) |
| `app.security.return-urls` | `RETURN_URLS` | allowed `returnUrl` prefixes per client: `client1:https://shop.example.com/checkout/,client2:...` (empty = none; sandbox: `local-client:http://localhost:3000/`) |
| `logging.structured.format.console`, `.file` | `LOG_FORMAT` | `ecs` (default), `logstash`, `gelf` |
| `logging.structured.format.console` (sandbox) | `LOG_CONSOLE_FORMAT` | empty (default) = readable one-liners; `ecs` = full JSON |
| `logging.file.name` | `LOG_FILE` | `logs/ni-payment-integration.log` (all environments) |
| `logging.logback.rollingpolicy.*` | `LOG_FILE_MAX_SIZE`, `LOG_FILE_MAX_HISTORY`, `LOG_FILE_TOTAL_SIZE_CAP` | `50MB`, `14` days, `2GB` |
| `management.opentelemetry.logging.export.otlp.endpoint` | `GRAFANA_OTLP_LOGS_ENDPOINT` | sandbox only: `http://localhost:4318/v1/logs`; prod: not set = no export |
| `management.logging.export.otlp.enabled` | `GRAFANA_LOGS_ENABLED` | sandbox: `true` |
| `app.logging.http.log-bodies` | `LOG_HTTP_BODIES` | `true` (default, masked) / `false` |
| `logging.structured.ecs.service.environment` | `APP_ENVIRONMENT` | shown as `service.environment` |
| `management.tracing.sampling.probability` | `TRACING_SAMPLING_PROBABILITY` | 1.0 (prod 0.1) |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | not set = no export |
| `spring.datasource.*` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | prod profile |

---

## 7. Notes

**Verify in the N-Genius sandbox:**
- the link names `payment:card` and `payment:saved-card` (`NiLinks`)
- whether `merchantOrderReference` is accepted
- the exact N-Genius state names (`PaymentState.fromNi`)

Every N-Genius request and response is logged, so one sandbox run shows all of these.

Confirmed by the N-Genius EMV 3DS 2.x guide: the links `cnp:3ds2-authentication` and `cnp:3ds2-challenge-response`,
`threeDSCompInd: "Y"` "in all cases" (no 3DS Method step for direct API integrations), the challenge-response body
`{"base64EncodedCRes": ...}`, and that the ACS posts the form field `cres`.

**Next steps:**
1. Capture
2. Automatic reconciliation: a scheduled job calling retrieve order for payments `PENDING` too long
   (today a client calls `GET /api/v1/orders/{orderReference}`)
3. Webhooks
4. Token
