package com.company.payment.ni.exception;

import lombok.Getter;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * N-Genius answered 4xx/5xx: the outcome is KNOWN (the call was refused).
 * A timeout is different: Spring throws ResourceAccessException and the outcome is UNKNOWN.
 */
@Getter
public class NiException extends RuntimeException {

    private static final int MAX_BODY = 1000;

    private final int status;
    private final String responseBody;

    public NiException(int status, String path, String responseBody) {
        super("N-Genius returned HTTP " + status + " for " + path);
        this.status = status;
        this.responseBody = responseBody;
    }

    public static NiException from(HttpRequest request, ClientHttpResponse response) throws IOException {
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        return new NiException(response.getStatusCode().value(), request.getURI().getPath(),
                body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "..." : body);
    }

    /** 4xx: N-Genius refused the request (e.g. card declined, bad data). */
    public boolean isClientError() {
        return status >= 400 && status < 500;
    }
}
