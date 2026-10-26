package com.company.payment.payment.api;

import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.payment.model.PaymentResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * HTTP contract of the payment feature: path, headers and Bean Validation. PaymentController implements it.
 *
 * payment-api.yaml documents this endpoint (Swagger UI) but no code is generated from it:
 * when this interface or the models in payment/model change, update the YAML to match.
 */
@Validated
public interface PaymentsApi {

    String PATH_CREATE_PAYMENT = "/api/v1/payments";

    /**
     * POST /api/v1/payments: authorise a payment with a new card or a saved card token.
     *
     *   1. Creates an AUTH order at N-Genius.
     *   2. Sends the card (CARD) or the saved N-Genius token (CARD_ON_FILE).
     *   3. If N-Genius asks for 3DS, starts EMV 3DS 2.x with deviceInfo:
     *      frictionless -> AUTHORISED, challenge -> CHALLENGE_REQUIRED.
     *
     * The same Idempotency-Key returns the same payment: the card is never charged twice.
     *
     * @param idempotencyKey unique per payment attempt; reuse the same value when retrying the same attempt
     * @param paymentRequest the payment
     * @return 200 with the outcome in status (also for DECLINED / FAILED).
     *         Errors (ErrorResponse): 400 VALIDATION_ERROR / MALFORMED_REQUEST / MISSING_HEADER,
     *         401 UNAUTHORIZED, 409 IDEMPOTENCY_KEY_REUSED / REQUEST_IN_PROGRESS,
     *         422 INVALID_AMOUNT / GATEWAY_REJECTED, 500 INTERNAL_ERROR, 502 GATEWAY_ERROR,
     *         504 GATEWAY_TIMEOUT (outcome unknown: retry with the SAME Idempotency-Key)
     */
    @RequestMapping(
            method = RequestMethod.POST,
            value = PATH_CREATE_PAYMENT,
            produces = {"application/json"},
            consumes = {"application/json"})
    ResponseEntity<PaymentResponse> createPayment(
            @NotNull @Size(min = 1, max = 100) @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody PaymentRequest paymentRequest);
}
