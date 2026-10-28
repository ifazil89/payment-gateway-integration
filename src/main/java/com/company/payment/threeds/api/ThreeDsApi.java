package com.company.payment.threeds.api;

import com.company.payment.payment.model.PaymentResponse;
import com.company.payment.threeds.model.ChallengeResponseRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract of the 3DS feature. Documented in payment-api.yaml (keep it in step).
 *
 * After POST /api/v1/payments answered CHALLENGE_REQUIRED:
 *   1. GET  .../challenge           (client, API key)  the challenge page (HTML) to show the shopper
 *   2. the ACS posts "cres" to the payment's returnUrl (the client)
 *   3. POST .../challenge-response  (client, API key)  the client sends that cres; we finish with N-Genius
 *
 * Without a returnUrl the ACS posts the CRes to .../notification instead. That one is not called by a client
 * but by the shopper's browser, so it has no API key (see ApiKeyFilter) and answers with a redirect, not JSON.
 */
@Validated
public interface ThreeDsApi {

    String PATH_CHALLENGE = "/api/v1/threeds/{paymentId}/challenge";
    String PATH_CHALLENGE_RESPONSE = "/api/v1/threeds/{paymentId}/challenge-response";
    String PATH_NOTIFICATION = "/api/v1/threeds/{paymentId}/notification";

    /**
     * GET /api/v1/threeds/{paymentId}/challenge: the backend posts the CReq to N-Genius' threeDSMethodURL and
     * returns the HTML page (text/html), e.g. for a mobile WebView. Can be called again (e.g. on reload).
     * No "produces" here: errors are still JSON ErrorResponse.
     *
     * @return 200 text/html; 404 PAYMENT_NOT_FOUND; 409 CHALLENGE_NOT_REQUIRED;
     *         422 GATEWAY_REJECTED / 502 GATEWAY_ERROR / 504 GATEWAY_TIMEOUT (loading the page failed)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = PATH_CHALLENGE)
    ResponseEntity<String> challengePage(@PathVariable("paymentId") String paymentId);

    /**
     * POST /api/v1/threeds/{paymentId}/challenge-response {"cres": "..."}: sends the CRes to N-Genius.
     *
     * @return 200 PaymentResponse: AUTHORISED / DECLINED / FAILED, or PENDING if N-Genius did not answer
     *         (read it later with GET /api/v1/payments/{paymentId}). A repeated call gets the current status.
     *         400 VALIDATION_ERROR; 404 PAYMENT_NOT_FOUND
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = PATH_CHALLENGE_RESPONSE,
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<PaymentResponse> challengeResponse(@PathVariable("paymentId") String paymentId,
                                                      @Valid @RequestBody ChallengeResponseRequest request);

    /**
     * POST /api/v1/threeds/{paymentId}/notification (form field "cres"), when the payment has no returnUrl.
     * Sends the CRes to N-Genius, then:
     *   303 to the payment's returnUrl + ?paymentId=...&status=...   (status AUTHORISED / DECLINED / FAILED,
     *                                                                 or PENDING if N-Genius did not answer)
     *   200 small HTML page "you can close this window"              (no returnUrl)
     *   404 PAYMENT_NOT_FOUND
     * A repeated notification does not call N-Genius again; it answers with the current status.
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = PATH_NOTIFICATION,
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    ResponseEntity<String> notification(@PathVariable("paymentId") String paymentId,
                                        @RequestParam("cres") String cres);
}
