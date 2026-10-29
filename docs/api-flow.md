# API calls and the N-Genius calls behind them

Which N-Genius calls each of our API calls makes, in order, and what the client gets back.
Request/response details: Swagger UI (`/swagger-ui.html`) and [`payment-api.yaml`](../src/main/resources/static/openapi/payment-api.yaml).

## Before any N-Genius call: the access token

`NiAccessTokenProvider` calls `POST /identity/auth/access-token` (`Authorization: Basic <NI_API_KEY>`,
`application/vnd.ni-identity.v1+json`). The token is reused until 30 s before its `expires_in` (about 5 minutes).
If a call gets `401`, `NiClient` fetches a new token and retries once.

Every N-Genius call below sends `Authorization: Bearer <token>`, `Content-Type` / `Accept:
application/vnd.ni-payment.v2+json` and `traceparent` (same trace as our request).

The URL of each next step comes from the `_links` of the previous answer (`NiLinks`), never built by hand.
Only "create order" and "get order" use fixed paths.

## 1. `POST /api/v1/payments`: authorise (`PaymentService.createPayment`)

```
Client                         Our service                                   N-Genius
  │ POST /api/v1/payments ──►  validate, idempotency check
  │                            (same key + same request → stored answer, NO N-Genius call)
  │                            save Payment CREATED
  │                            ── POST /transactions/outlets/{outlet}/orders ──►  {action: AUTH, amount (minor units),
  │                                                                               emailAddress, merchantOrderReference, billingAddress}
  │                            ◄── order ref + payment ref + _links payment:card / payment:saved-card
  │                            save (order ref)
  │                            ── PUT  _links.payment:card        (CARD: pan, expiry, cvv, cardholderName)
  │                               or PUT _links.payment:saved-card (CARD_ON_FILE: cardToken, expiry, cvv, cardholderName)
  │                            ◄── state AUTHORISED | FAILED/DECLINED | AWAIT_3DS
  │                            save
  │                            if AWAIT_3DS:
  │                            ── POST _links.cnp:3ds2-authentication ──►  {deviceChannel BRW, threeDSCompInd Y,
  │                                                                         notificationURL = returnUrl (or our /notification),
  │                                                                         browserInfo from deviceInfo}
  │                            ◄── 3ds2 {transStatus, acsURL, base64EncodedCReq, threeDSMethodURL} + cnp:3ds2-challenge-response link
  │ ◄── 200 status ─────────── save; AUTHORISED (frictionless) | CHALLENGE_REQUIRED (+ threeDs{acsUrl, creq}) | DECLINED | FAILED
```

| N-Genius result | Our answer |
|---|---|
| card call → `AUTHORISED` | `200 AUTHORISED` (no 3DS call) |
| 3DS `transStatus` ≠ C → `AUTHORISED` / `FAILED` | `200 AUTHORISED` / `FAILED` (frictionless) |
| 3DS `transStatus C`, or still `AWAIT_3DS` with `acsURL` + CReq | `200 CHALLENGE_REQUIRED` |
| any 4xx | `200 FAILED` |
| 5xx / timeout | `502` / `504`; the payment stays `PENDING`, and retrying with the same Idempotency-Key is safe |

No database transaction spans an N-Genius call: each step is saved on its own.

## 2. The 3DS challenge, after `CHALLENGE_REQUIRED` (`ThreeDsService`)

| # | Our API | Caller | N-Genius / 3DS call |
|---|---|---|---|
| a | `GET /api/v1/threeds/{paymentId}/challenge` | client (API key) | `POST` form `creq=<base64EncodedCReq>` to the stored **`threeDSMethodURL`** (`acsURL` if absent). This is the 3DS server, not the N-Genius API, so no token is sent. Returns the HTML page for the WebView; nothing is saved. |
| b | (not our API) | the issuer's ACS | After the OTP, the ACS posts form field `cres` to the client's **`returnUrl`** |
| c | `POST /api/v1/threeds/{paymentId}/challenge-response` `{cres}` | client (API key) | First the payment is claimed (`CHALLENGE_REQUIRED` → `AWAITING_3DS`, optimistic lock) so the cres is never sent twice. Then `POST _links.cnp:3ds2-challenge-response` `{"base64EncodedCRes": cres}` → `AUTHORISED` / `DECLINED` / `FAILED`; a 5xx or timeout leaves it `PENDING`. Answers the payment JSON. |
| c′ | `POST /api/v1/threeds/{paymentId}/notification` (form `cres`) | shopper's browser (no API key) | Same N-Genius call as (c). Used only when the payment has no `returnUrl`; answers with a 303 redirect or a small HTML page. |

## 3. Status calls

| Our API | N-Genius call | What it does |
|---|---|---|
| `GET /api/v1/payments/{paymentId}` | none | Reads the stored status from our database |
| `GET /api/v1/orders/{orderReference}` | `GET /transactions/outlets/{outlet}/orders/{orderReference}` | Live state at N-Genius (`OrderService.retrieveOrder`). A **pending** payment takes N-Genius' final result (`AUTHORISED` / `DECLINED` / `FAILED`). A known result is never changed, and `CHALLENGE_REQUIRED` is never undone (`Payment.niStateRetrieved`). Use it after `PENDING` (e.g. a timeout). |

## A typical challenge payment, end to end

```
POST /api/v1/payments                        → token*, create order, PUT card, POST 3ds2-authentication → CHALLENGE_REQUIRED
GET  /api/v1/threeds/{id}/challenge          → POST creq to threeDSMethodURL                           → HTML for WebView
     (shopper enters OTP; ACS posts cres to returnUrl)
POST /api/v1/threeds/{id}/challenge-response → POST cnp:3ds2-challenge-response                        → AUTHORISED
GET  /api/v1/orders/{orderReference}         → GET order                      (optional double-check / after PENDING)
```
`*` only when no cached token

## Where in the code

| Step | Class / method |
|---|---|
| Access token | `ni/client/NiAccessTokenProvider` |
| Every N-Genius call (one method each, 401 retry, business log line) | `ni/client/NiClient` |
| Link names | `ni/dto/NiLinks` |
| Payment flow | `payment/service/PaymentService.createPayment`, `authenticateThreeDs` |
| Challenge page and CRes | `threeds/service/ThreeDsService.challengePage`, `submitChallengeResponse`, `completeChallenge` |
| Retrieve order | `order/service/OrderService.retrieveOrder` |
