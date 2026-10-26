package com.company.payment.common.security;

import com.company.payment.common.logging.HttpLoggingFilter;
import com.company.payment.common.logging.LogFields;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;

/**
 * /api/** is for downstream clients only. Each client has its own key (app.security.api-keys,
 * "client1:key1,client2:key2") and sends it as X-Api-Key. The client id is put in the logs (clientId)
 * and used to scope idempotency keys (two clients may use the same key without clashing).
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final int ORDER = HttpLoggingFilter.ORDER + 10;
    public static final String HEADER = "X-Api-Key";
    private static final String CLIENT_ATTRIBUTE = ApiKeyFilter.class.getName() + ".client";

    private final Map<String, byte[]> keysByClient;

    /** The authenticated client of the current request. */
    public static String currentClientId() {
        RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
        Object client = attributes.getAttribute(CLIENT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (client == null) {
            throw new IllegalStateException("No authenticated client on this request");
        }
        return (String) client;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String client = clientFor(request.getHeader(HEADER));
        if (client == null) {
            log.atWarn().setMessage("Rejected: missing or invalid {}").addArgument(HEADER)
                    .addKeyValue("error.code", "UNAUTHORIZED").log();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            // correlationId and traceId only contain [A-Za-z0-9._-], safe to put in JSON as-is
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid " + HEADER
                    + "\",\"correlationId\":" + json(MDC.get(LogFields.CORRELATION_ID))
                    + ",\"traceId\":" + json(MDC.get(LogFields.TRACE_ID))
                    + ",\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }
        request.setAttribute(CLIENT_ATTRIBUTE, client);
        MDC.put(LogFields.CLIENT_ID, client);     // removed by CorrelationIdFilter at the end of the request
        chain.doFilter(request, response);
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** Constant-time comparison against every configured key. */
    private String clientFor(String providedKey) {
        if (providedKey == null) {
            return null;
        }
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        String match = null;
        for (Map.Entry<String, byte[]> entry : keysByClient.entrySet()) {
            if (MessageDigest.isEqual(entry.getValue(), provided)) {
                match = entry.getKey();
            }
        }
        return match;
    }
}
