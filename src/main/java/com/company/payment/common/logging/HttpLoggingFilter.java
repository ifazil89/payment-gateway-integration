package com.company.payment.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Collections;

/**
 * Logs every API call coming IN, with masked headers and bodies:
 *
 *   HTTP IN  --> POST /api/v1/payments                     (headers, client ip)
 *   HTTP IN  <-- 200 POST /api/v1/payments (412 ms)        (request body, response body, response headers)
 *
 * Both lines carry traceId, spanId, correlationId (MDC) and structured fields (http.*), so an
 * observability tool can filter on http.status_code, http.duration_ms, etc.
 * The request body is logged on the second line because it is only known after the controller read it.
 */
@Slf4j
@RequiredArgsConstructor
public class HttpLoggingFilter extends OncePerRequestFilter {

    public static final int ORDER = CorrelationIdFilter.ORDER + 10;

    /** Cache more than we log, so a body is never cut in the middle of a card number before masking. */
    private static final int REQUEST_CACHE_LIMIT = 64 * 1024;

    private final SensitiveDataMasker masker;

    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                    FilterChain chain) throws ServletException, IOException {
        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(servletRequest, REQUEST_CACHE_LIMIT);
        ContentCachingResponseWrapper response = new ContentCachingResponseWrapper(servletResponse);
        String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        long start = System.nanoTime();

        log.atInfo()
                .setMessage("HTTP IN  --> {} {}")
                .addArgument(request.getMethod()).addArgument(path)
                .addKeyValue("http.direction", "inbound")
                .addKeyValue("http.method", request.getMethod())
                .addKeyValue("http.path", path)
                .addKeyValue("http.client_ip", request.getRemoteAddr())
                .addKeyValue("http.request.headers", requestHeaders(request))
                .log();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            log.atLevel(level(status))
                    .setMessage("HTTP IN  <-- {} {} {} ({} ms)")
                    .addArgument(status).addArgument(request.getMethod()).addArgument(path).addArgument(durationMs)
                    .addKeyValue("http.direction", "inbound")
                    .addKeyValue("http.method", request.getMethod())
                    .addKeyValue("http.path", path)
                    .addKeyValue("http.status_code", status)
                    .addKeyValue("http.duration_ms", durationMs)
                    .addKeyValue("http.request.body", masker.body(request.getContentAsByteArray(), request.getContentType()))
                    .addKeyValue("http.response.headers", responseHeaders(response))
                    .addKeyValue("http.response.body", masker.body(response.getContentAsByteArray(), response.getContentType()))
                    .log();
            response.copyBodyToResponse();   // send the cached body to the client
        }
    }

    static Level level(int status) {
        return status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
    }

    private String requestHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.addAll(name, Collections.list(request.getHeaders(name)));
        }
        return masker.headers(headers);
    }

    private String responseHeaders(HttpServletResponse response) {
        HttpHeaders headers = new HttpHeaders();
        for (String name : response.getHeaderNames()) {
            headers.addAll(name, java.util.List.copyOf(response.getHeaders(name)));
        }
        if (response.getContentType() != null) {
            headers.set(HttpHeaders.CONTENT_TYPE, response.getContentType());
        }
        return masker.headers(headers);
    }
}
