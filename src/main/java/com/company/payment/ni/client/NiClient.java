package com.company.payment.ni.client;

import com.company.payment.ni.config.NiProperties;
import com.company.payment.ni.dto.NiCardRequest;
import com.company.payment.ni.dto.NiChallengeResponseRequest;
import com.company.payment.ni.dto.NiOrderRequest;
import com.company.payment.ni.dto.NiOrderResponse;
import com.company.payment.ni.dto.NiPaymentResponse;
import com.company.payment.ni.dto.NiThreeDsRequest;
import com.company.payment.ni.dto.NiTokenCardRequest;
import com.company.payment.ni.exception.NiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;

/**
 * Every call to N-Genius, one method per operation. Shared by all features (payment, 3DS, token).
 * It knows only N-Genius DTOs: no API models, no entities.
 *
 * Logging:
 *  - the raw HTTP request/response (masked) is logged by RestClientLoggingInterceptor
 *  - this class adds one business line per call:  NI <operation> -> <state> (<ms> ms)
 *
 * N-Genius returns "_links" with the URL of each next step; methods taking an href expect
 * such a link (see NiLinks). N-Genius asks you not to build these URLs yourself.
 */
@Component
@Slf4j
public class NiClient {

    private static final MediaType PAYMENT_V2 = MediaType.parseMediaType("application/vnd.ni-payment.v2+json");

    private final RestClient niRestClient;
    private final NiAccessTokenProvider accessTokens;
    private final NiProperties properties;
    private final String trustedHost;

    public NiClient(RestClient niRestClient, NiAccessTokenProvider accessTokens, NiProperties properties) {
        this.niRestClient = niRestClient;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.trustedHost = URI.create(properties.baseUrl()).getHost();
    }

    /** Two-stage step 1: create the order (action AUTH). No money moves yet. */
    public NiOrderResponse createOrder(NiOrderRequest request) {
        return call("create order", () -> niRestClient.post()
                .uri("/transactions/outlets/{outletId}/orders", properties.outletId())
                .headers(this::addHeaders)
                .contentType(PAYMENT_V2)
                .body(request)
                .retrieve()
                .body(NiOrderResponse.class));
    }

    /** Current state of an order: the source of truth. */
    public NiOrderResponse getOrder(String orderReference) {
        return call("get order", () -> niRestClient.get()
                .uri("/transactions/outlets/{outletId}/orders/{orderReference}", properties.outletId(), orderReference)
                .headers(this::addHeaders)
                .retrieve()
                .body(NiOrderResponse.class));
    }

    /** Two-stage step 2 with a new card: PUT to the payment's "payment:card" link. */
    public NiPaymentResponse payWithCard(String cardLink, NiCardRequest request) {
        return put("pay with card", cardLink, request);
    }

    /** Two-stage step 2 with a saved card token: PUT to the payment's "payment:saved-card" link. */
    public NiPaymentResponse payWithToken(String savedCardLink, NiTokenCardRequest request) {
        return put("pay with token", savedCardLink, request);
    }

    /** EMV 3DS 2.x authentication: POST to the payment's "cnp:3ds2-authentication" link. */
    public NiPaymentResponse authenticateThreeDs(String authenticationLink, NiThreeDsRequest request) {
        return post("3DS authentication", authenticationLink, request);
    }

    /** After a 3DS challenge: POST the issuer's CRes to the payment's "cnp:3ds2-challenge-response" link. */
    public NiPaymentResponse completeChallenge(String challengeResponseLink, NiChallengeResponseRequest request) {
        return post("3DS challenge response", challengeResponseLink, request);
    }

    /**
     * 3DS challenge page: POST the CReq as form field "creq" to the URL from the "3ds2" block
     * (threeDSMethodURL) and return the HTML the shopper has to complete.
     * That host is the 3DS server / card issuer, not the N-Genius API: no access token is sent, only https is allowed.
     */
    public String challengePage(String url, String creq) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("Refusing to post the CReq to a non-https URL: " + uri.getHost());
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("creq", creq);
        return call("3DS challenge page", () -> niRestClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.TEXT_HTML, MediaType.ALL)
                .body(form)
                .retrieve()
                .body(String.class));
    }

    // ------------------------------------------------------------------ helpers

    private NiPaymentResponse put(String operation, String href, Object body) {
        return call(operation, () -> niRestClient.put()
                .uri(trusted(href))
                .headers(this::addHeaders)
                .contentType(PAYMENT_V2)
                .body(body)
                .retrieve()
                .body(NiPaymentResponse.class));
    }

    private NiPaymentResponse post(String operation, String href, Object body) {
        return call(operation, () -> niRestClient.post()
                .uri(trusted(href))
                .headers(this::addHeaders)
                .contentType(PAYMENT_V2)
                .body(body)
                .retrieve()
                .body(NiPaymentResponse.class));
    }

    private void addHeaders(HttpHeaders headers) {
        headers.setBearerAuth(accessTokens.getAccessToken());
        headers.setAccept(List.of(PAYMENT_V2));
    }

    /** Only ever send our token and card data to the configured N-Genius host. */
    private URI trusted(String href) {
        URI uri = URI.create(href);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost.equalsIgnoreCase(uri.getHost())) {
            throw new IllegalStateException("Refusing to call a link outside N-Genius: " + uri.getHost());
        }
        return uri;
    }

    /** One business log line per call. On 401 (token expired/revoked) gets a new access token and retries once. */
    private <T> T call(String operation, Supplier<T> request) {
        long start = System.nanoTime();
        try {
            T result;
            try {
                result = request.get();
            } catch (NiException ex) {
                if (ex.getStatus() != 401) {
                    throw ex;
                }
                log.warn("NI {} got 401, refreshing the access token and retrying once", operation);
                accessTokens.invalidate();
                result = request.get();
            }
            String outcome = describe(result);
            log.atInfo()
                    .setMessage("NI {} -> {} ({} ms)")
                    .addArgument(operation).addArgument(outcome).addArgument(elapsedMs(start))
                    .addKeyValue("ni.operation", operation)
                    .addKeyValue("ni.outcome", outcome)
                    .addKeyValue("ni.duration_ms", elapsedMs(start))
                    .log();
            return result;
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setMessage("NI {} FAILED after {} ms: {}")
                    .addArgument(operation).addArgument(elapsedMs(start)).addArgument(ex.getMessage())
                    .addKeyValue("ni.operation", operation)
                    .addKeyValue("ni.outcome", "ERROR")
                    .addKeyValue("ni.duration_ms", elapsedMs(start))
                    .addKeyValue("error.type", ex.getClass().getSimpleName())
                    .log();
            throw ex;
        }
    }

    private static String describe(Object result) {
        if (result instanceof NiPaymentResponse payment) {
            return payment.state();
        }
        if (result instanceof NiOrderResponse order) {
            return "order " + order.reference();
        }
        return "ok";
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
