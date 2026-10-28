package com.company.payment.threeds.controller;

import com.company.payment.common.security.ApiKeyFilter;
import com.company.payment.payment.model.PaymentResponse;
import com.company.payment.threeds.api.ThreeDsApi;
import com.company.payment.threeds.model.ChallengeResponseRequest;
import com.company.payment.threeds.service.ThreeDsResult;
import com.company.payment.threeds.service.ThreeDsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Locale;

/**
 * Implements ThreeDsApi (threeds/api): the challenge page and the CRes from a client (API key), and the CRes
 * posted by the shopper's browser (notification, answered with a redirect or a small page).
 */
@RestController
@RequiredArgsConstructor
public class ThreeDsController implements ThreeDsApi {

    private static final String RESULT_PAGE = """
            <!doctype html>
            <html lang="en"><head><meta charset="utf-8"><title>Payment</title></head>
            <body><p>Payment %s. You can close this window.</p></body></html>
            """;

    private final ThreeDsService threeDsService;

    @Override
    public ResponseEntity<String> challengePage(String paymentId) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(threeDsService.challengePage(ApiKeyFilter.currentClientId(), paymentId));
    }

    @Override
    public ResponseEntity<PaymentResponse> challengeResponse(String paymentId, ChallengeResponseRequest request) {
        return ResponseEntity.ok(
                threeDsService.submitChallengeResponse(ApiKeyFilter.currentClientId(), paymentId, request.cres()));
    }

    @Override
    public ResponseEntity<String> notification(String paymentId, String cres) {
        ThreeDsResult result = threeDsService.completeChallenge(paymentId, cres);
        if (result.returnUrl() == null) {
            // status is one of our enum values: nothing from the request ends up in the page
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(RESULT_PAGE.formatted(result.status().getValue().toLowerCase(Locale.ROOT).replace('_', ' ')));
        }
        // returnUrl was checked against the client's allowed return URLs when the payment was created;
        // build(true): it is already a valid, encoded URI (paymentId and status need no encoding)
        URI location = UriComponentsBuilder.fromUriString(result.returnUrl())
                .queryParam("paymentId", result.paymentId())
                .queryParam("status", result.status().getValue())
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).build();
    }
}
