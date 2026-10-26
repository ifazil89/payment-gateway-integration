package com.company.payment.common.logging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter of every request (it runs inside Boot's tracing filter, so traceId is already known):
 *  - correlationId: X-Correlation-Id from the client (if safe), otherwise a new UUID
 *  - puts it in the MDC: every log line of this request carries it
 *  - returns X-Correlation-Id and X-Trace-Id on every response (also on errors)
 *  - cleans up the request-scoped MDC fields at the end (threads are reused)
 */
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Boot's ServerHttpObservationFilter is HIGHEST_PRECEDENCE + 1. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final String HEADER = "X-Correlation-Id";
    public static final String TRACE_HEADER = "X-Trace-Id";

    /** Letters, digits, . _ - only: a client value can never break or forge a log line. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ObjectProvider<Tracer> tracer;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming : UUID.randomUUID().toString();

        MDC.put(LogFields.CORRELATION_ID, correlationId);
        response.setHeader(HEADER, correlationId);
        String traceId = currentTraceId();
        if (traceId != null) {
            response.setHeader(TRACE_HEADER, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LogFields.CORRELATION_ID);
            MDC.remove(LogFields.CLIENT_ID);   // set by ApiKeyFilter
        }
    }

    private String currentTraceId() {
        Tracer current = tracer.getIfAvailable();
        Span span = current == null ? null : current.currentSpan();
        if (span != null) {
            return span.context().traceId();
        }
        return MDC.get(LogFields.TRACE_ID);
    }
}
