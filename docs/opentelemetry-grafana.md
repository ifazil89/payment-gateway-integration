# OpenTelemetry and Grafana: how this service's telemetry works

OpenTelemetry (OTel) is a vendor-neutral standard for producing telemetry and sending it somewhere. Grafana is where
you view it. Between them sits a protocol called **OTLP**, so the app doesn't need to know which backend receives its data.

Configuration and environment variables: [README §4 Logging and tracing](../README.md#4-logging-and-tracing) and
[§6 Configuration](../README.md#6-configuration).

## The three signals

| Signal | What it is | In this app | Stored by (Grafana stack) |
|---|---|---|---|
| **Logs** | single events ("Payment created…") | every `log.info(...)` line | **Loki** |
| **Traces** | the path of one request across steps and services, as timed **spans** | inbound HTTP request → N-Genius calls | **Tempo** |
| **Metrics** | numbers over time (counts, latencies) | `http.server.requests`, connection pool | **Mimir / Prometheus** |

Grafana itself stores nothing. It's the UI that queries Loki, Tempo and Prometheus.

## Where the logs go

| Destination | Environments | Format |
|---|---|---|
| stdout | all | ECS JSON (`LOG_FORMAT`), or plain text with the `plainlogs` profile |
| Rolling file `logs/ni-payment-integration.log` | all | same JSON as stdout |
| Grafana, over OTLP | sandbox only (production has no Grafana yet) | OTLP log records |

## How a log line travels

```
log.atInfo()...log()                         your code (SLF4J)
      │
      ▼
Logback  ── CONSOLE appender ──► stdout (ECS JSON)
         ── FILE appender    ──► logs/ni-payment-integration.log (rolling)
         ── GRAFANA_OTLP appender (OpenTelemetryAppender)       ◄─ GrafanaLogExportConfig
                 │  turns the event into an OTel LogRecord:
                 │  body = message, severity = level, attributes = MDC + key-values,
                 │  traceId/spanId of the current span
                 ▼
         OTel SDK LoggerProvider                                ◄─ built by Spring Boot
                 │  BatchLogRecordProcessor: buffers in memory, sends about every 1 s
                 │  or when the batch is full; drops records if the queue overflows
                 ▼
         OTLP exporter  ── HTTP POST protobuf ──►  GRAFANA_OTLP_LOGS_ENDPOINT
                                                        │
                    ┌───────────────────────────────────┤
                    ▼                                   ▼
         Alloy / OTel Collector (optional)        Loki 3 (/otlp/v1/logs)
         batching, filtering, retries,                  │
         routes logs→Loki, traces→Tempo                 ▼
                                                     Grafana  (Explore → Loki, LogQL)
```

Each part's job:
- **OpenTelemetryAppender** (`opentelemetry-logback-appender-1.0`): a bridge. Logback doesn't speak OTel, so this
  appender copies each log event into the OTel SDK. Logback calls it alongside the console and file appenders, so it
  only adds a fourth destination. `GrafanaLogExportConfig` attaches it to the root logger, and only when
  `management.opentelemetry.logging.export.otlp.endpoint` is set (the sandbox).
- **`OpenTelemetryAppender.install(openTelemetry)`**: connects the appender to the SDK instance that Spring Boot
  created. Events logged before that point (early startup) are buffered and sent once it's connected.
- **SDK + batch processor**: sends in batches in the background, so a request never waits for Grafana. If Grafana is
  down, records are queued, then dropped. The app keeps working, and the file and stdout still have everything.
- **Resource attributes**: describe the sender rather than one line: `service.name=ni-payment-integration`,
  `deployment.environment.name=sandbox`. Loki turns these into labels, so you can query
  `{service_name="ni-payment-integration"}`.
- **Record attributes**: the per-line fields (`correlationId`, `clientId`, `paymentId`, `payment.state`,
  `ni.duration_ms`, …). In Loki they become structured metadata you can filter on without parsing JSON.
- **Masking**: card data and secrets are masked before the line is written (`SensitiveDataMasker`), so Grafana gets
  the same masked content as stdout and the file.

## How traces fit in

Micrometer Tracing with the OTel bridge creates a span for each inbound request and each N-Genius call. It puts
`traceId`/`spanId` into the MDC (which is why every log line has them) and sends a `traceparent` header to N-Genius.
Traces are exported the same way, through a separate setting: in the sandbox
`GRAFANA_OTLP_TRACES_ENDPOINT` (default `http://localhost:4318/v1/traces`) → Tempo; `GRAFANA_TRACES_ENABLED=false`
switches it off.

**Why it matters:** a log record and a span carry the same `traceId`, so Grafana can link the two. From a log line
in Loki you click through to the trace in Tempo and see the request's whole timeline (e.g. "N-Genius 3DS
authentication took 380 ms"). From a span you can jump back to its logs. The stack in `docker/grafana` sets up both
links (a "derived field" on the trace id in the Loki data source, "trace to logs" in the Tempo one).

## Direct to Loki, or through a collector?

| | App → Loki directly | App → Alloy/Collector → Loki/Tempo |
|---|---|---|
| Setup | one endpoint, simplest | one more component to run |
| Outage handling | app's in-memory queue only | collector retries and can buffer to disk |
| Traces + logs + metrics | separate endpoints per backend | one endpoint (`:4318`), the collector routes them |
| Processing (drop noisy logs, add labels) | in the app | in the collector config |

Either works with this service; only `GRAFANA_OTLP_LOGS_ENDPOINT` changes:
- Loki 3: `http://<loki>:3100/otlp/v1/logs`
- Grafana Alloy or an OpenTelemetry collector: `http://<host>:4318/v1/logs`
- Grafana Cloud: its OTLP gateway URL, plus
  `MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_HEADERS_AUTHORIZATION="Basic <instanceId:token base64>"`

This repository uses Alloy (next section). For production, a collector or Alloy is the usual choice too.

## The Grafana stack in `docker/grafana`

```
app (sandbox profile)
  │ OTLP/HTTP :4318   logs, traces, (metrics with OTLP_METRICS_ENABLED=true)
  ▼
Alloy  (otelcol.receiver.otlp → batch → one exporter per signal)      UI: http://localhost:12345
  ├── logs    → Loki        http://loki:3100/otlp           labels: service_name, deployment_environment_name
  ├── traces  → Tempo       tempo:4317 (OTLP gRPC)
  └── metrics → Prometheus  http://prometheus:9090/api/v1/otlp
                     ▲
Grafana http://localhost:3001 ── data sources provisioned: Loki (default), Tempo, Prometheus,
                                  Loki "View trace" ↔ Tempo "Logs for this span"
```

| File | What it configures |
|---|---|
| `docker-compose.yml` | the five services (pinned versions), volumes, published ports |
| `alloy/config.alloy` | OTLP receiver and the routing above |
| `loki/loki.yaml` | single process, filesystem storage, structured metadata on, 7-day retention |
| `tempo/tempo.yaml` | single process, local storage, OTLP receiver, 7-day retention |
| `prometheus/prometheus.yml` | OTLP receiver (no scraping), resource attributes kept as labels |
| `grafana/provisioning/datasources/datasources.yaml` | the three data sources and the log ↔ trace links |

Run it and use it:

```bash
docker compose -f docker/grafana/docker-compose.yml up -d     # start (first time: pulls the images)
NI_API_KEY=... NI_OUTLET_ID=... mvn spring-boot:run           # sandbox profile: sends to localhost:4318 already
open http://localhost:3001                                    # admin / admin (GRAFANA_ADMIN_PASSWORD)
docker compose -f docker/grafana/docker-compose.yml logs -f alloy   # if nothing arrives
docker compose -f docker/grafana/docker-compose.yml down       # stop (add -v to delete the stored data)
```

- **Ports:** only Grafana (`3001`; `3000` is the sandbox client's return URL) and Alloy (`4317`, `4318`, UI `12345`)
  are published, on `127.0.0.1`. Loki, Tempo and Prometheus have no authentication and are reachable only inside the
  compose network (through Grafana).
- **On a shared sandbox host** (the app on another machine): `BIND_ADDRESS=0.0.0.0` and a real
  `GRAFANA_ADMIN_PASSWORD`, and point the app at it: `GRAFANA_OTLP_LOGS_ENDPOINT=http://<host>:4318/v1/logs`,
  `GRAFANA_OTLP_TRACES_ENDPOINT=http://<host>:4318/v1/traces`. Alloy's OTLP port has no authentication either, so
  keep it inside the sandbox network (firewall / security group), not on the internet.
- **Retention:** 7 days for logs, traces and metrics; data survives `down`/`up` (named volumes) until `down -v`.
- **Images** are pinned (`grafana/grafana:13.2.5`, `grafana/alloy:v1.21.0`, `grafana/loki:3.6.19`,
  `grafana/tempo:2.10.10`, `prom/prometheus:v3.14.0`); upgrade them deliberately, one at a time.

## Why OTLP instead of a Loki-specific appender

- **Not tied to a backend:** the same code can send to Grafana, Datadog, Elastic, Honeycomb and others. Only the
  endpoint changes.
- **One standard for all three signals**, with shared `traceId` linking.
- **Spring Boot supports it out of the box**, so this service only adds a small appender hook and no
  `logback-spring.xml`.

## Turning it on and off

- Sandbox: on by default, sending to `localhost:4318` (the stack above); elsewhere set `GRAFANA_OTLP_LOGS_ENDPOINT`
  and `GRAFANA_OTLP_TRACES_ENDPOINT`. `GRAFANA_LOGS_ENABLED=false` / `GRAFANA_TRACES_ENABLED=false` switch them off
  (e.g. running the sandbox profile without the stack; otherwise the exporters log connection warnings).
  Metrics: `OTLP_METRICS_ENABLED=true`.
- Production: off (no endpoint in `application-prod.yml`). To enable later, set
  `MANAGEMENT_OPENTELEMETRY_LOGGING_EXPORT_OTLP_ENDPOINT` (and the tracing one); no code change.
- Tests: off (`PaymentApiTest` sets `management.logging.export.otlp.enabled=false` and
  `management.tracing.export.otlp.enabled=false`).

## Queries (Grafana → Explore → Loki)

```logql
{service_name="ni-payment-integration"}                                   # everything
{service_name="ni-payment-integration"} | correlationId="demo-1"          # one request
{service_name="ni-payment-integration"} | paymentId="7f3c2a9e-..."        # one payment's history
{service_name="ni-payment-integration", severity_text="WARN"}             # warnings
sum by (ni_operation) (count_over_time({service_name="ni-payment-integration"} | ni_operation!="" [5m]))
```

Loki replaces the dots in attribute names with underscores (e.g. `ni.operation` → `ni_operation`), so check the
exact names in Grafana's field list the first time.
