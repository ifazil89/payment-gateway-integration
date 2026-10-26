package com.company.payment.common.logging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Logs every call going OUT through a RestClient (used for N-Genius), with masked headers and bodies:
 *
 *   HTTP OUT --> PUT https://api-gateway.sandbox.ngenius-payments.com/.../card      (headers, body)
 *   HTTP OUT <-- 200 PUT https://.../card (420 ms)                                    (headers, body)
 *   HTTP OUT <-- FAILED PUT https://.../card after 30001 ms: Read timed out           (timeout / no answer)
 *
 * The outgoing headers include "traceparent": N-Genius (and any service behind it) can be found
 * in the same trace. The response body is buffered so it can be logged and still be read by Spring.
 */
@Slf4j
@RequiredArgsConstructor
public class RestClientLoggingInterceptor implements ClientHttpRequestInterceptor {

    private final SensitiveDataMasker masker;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String method = request.getMethod().name();
        String url = request.getURI().toString();
        long start = System.nanoTime();

        log.atInfo()
                .setMessage("HTTP OUT --> {} {}")
                .addArgument(method).addArgument(url)
                .addKeyValue("http.direction", "outbound")
                .addKeyValue("http.method", method)
                .addKeyValue("http.url", url)
                .addKeyValue("http.request.headers", masker.headers(request.getHeaders()))
                .addKeyValue("http.request.body", masker.body(body, contentType(request.getHeaders())))
                .log();

        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException ex) {
            long durationMs = elapsedMs(start);
            log.atError()
                    .setMessage("HTTP OUT <-- FAILED {} {} after {} ms: {}")
                    .addArgument(method).addArgument(url).addArgument(durationMs).addArgument(ex.getMessage())
                    .addKeyValue("http.direction", "outbound")
                    .addKeyValue("http.method", method)
                    .addKeyValue("http.url", url)
                    .addKeyValue("http.duration_ms", durationMs)
                    .addKeyValue("error.type", ex.getClass().getSimpleName())
                    .addKeyValue("error.message", ex.getMessage())
                    .log();
            throw ex;
        }

        BufferedClientHttpResponse buffered = new BufferedClientHttpResponse(response);
        long durationMs = elapsedMs(start);
        int status = buffered.getStatusCode().value();
        log.atLevel(HttpLoggingFilter.level(status))
                .setMessage("HTTP OUT <-- {} {} {} ({} ms)")
                .addArgument(status).addArgument(method).addArgument(url).addArgument(durationMs)
                .addKeyValue("http.direction", "outbound")
                .addKeyValue("http.method", method)
                .addKeyValue("http.url", url)
                .addKeyValue("http.status_code", status)
                .addKeyValue("http.duration_ms", durationMs)
                .addKeyValue("http.response.headers", masker.headers(buffered.getHeaders()))
                .addKeyValue("http.response.body", masker.body(buffered.bodyBytes(), contentType(buffered.getHeaders())))
                .log();
        return buffered;
    }

    private static String contentType(HttpHeaders headers) {
        return headers.getFirst(HttpHeaders.CONTENT_TYPE);
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
