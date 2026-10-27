package com.company.payment.threeds.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * HTTP contract of the 3DS feature. Documented in payment-api.yaml (keep it in step).
 *
 * The notification is not called by a client: the card issuer's ACS makes the shopper's browser post
 * the challenge result here (the notificationURL sent to N-Genius, ni.threeds-notification-url).
 * So there is no API key (see ApiKeyFilter) and the answer is for a browser: a redirect, not JSON.
 */
public interface ThreeDsApi {

    String PATH_NOTIFICATION = "/api/v1/threeds/{paymentId}/notification";

    /**
     * POST /api/v1/threeds/{paymentId}/notification (form field "cres").
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
