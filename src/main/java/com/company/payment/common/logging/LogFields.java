package com.company.payment.common.logging;

/**
 * Names of the MDC fields that appear on every log line of a request.
 *
 *   traceId, spanId   set by Micrometer Tracing (W3C trace context, shared with N-Genius calls and
 *                     any downstream service; this is the id to search for in Grafana/Jaeger/Datadog...)
 *   correlationId     X-Correlation-Id from the client, or generated (CorrelationIdFilter)
 *   clientId          the authenticated downstream client (ApiKeyFilter)
 *   paymentId         our payment id, once the payment exists (PaymentService)
 */
public final class LogFields {

    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String CLIENT_ID = "clientId";
    public static final String PAYMENT_ID = "paymentId";

    private LogFields() {
    }
}
