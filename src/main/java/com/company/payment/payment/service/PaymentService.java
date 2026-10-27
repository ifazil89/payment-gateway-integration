package com.company.payment.payment.service;

import com.company.payment.payment.model.CardType;
import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.payment.model.PaymentResponse;
import com.company.payment.common.exception.ApiException;
import com.company.payment.common.logging.LogFields;
import com.company.payment.common.util.Amounts;
import com.company.payment.ni.client.NiClient;
import com.company.payment.ni.config.NiProperties;
import com.company.payment.ni.dto.NiLinks;
import com.company.payment.ni.dto.NiOrderResponse;
import com.company.payment.ni.dto.NiPaymentResponse;
import com.company.payment.ni.dto.NiThreeDs;
import com.company.payment.ni.exception.NiException;
import com.company.payment.payment.entity.Payment;
import com.company.payment.payment.entity.PaymentState;
import com.company.payment.payment.mapper.PaymentMapper;
import com.company.payment.payment.mapper.PaymentNiMapper;
import com.company.payment.payment.repository.PaymentRepository;
import com.company.payment.payment.validation.PaymentRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * POST /api/v1/payments: two-stage authorisation, step by step.
 *
 *   1. Validate (card fields vs cardType, amount decimals)
 *   2. Idempotency: same client + Idempotency-Key -> return the existing payment
 *   3. Save the payment as CREATED (so a crash or timeout never loses track of it)
 *   4. N-Genius: create an AUTH order (no money moves yet)
 *   5. N-Genius: send the card (CARD) or the saved card token (CARD_ON_FILE)
 *   6. If N-Genius answers AWAIT_3DS: start 3DS authentication with deviceInfo
 *        -> frictionless: N-Genius continues by itself (AUTHORISED / FAILED)
 *        -> transStatus C: CHALLENGE_REQUIRED (the client calls the 3DS API next)
 *   7. Save and return the status
 *
 * No database transaction is held across N-Genius calls: each step is saved on its own.
 * N-Genius 4xx = refused -> FAILED (200 response). 5xx / timeout = outcome unknown -> the payment
 * stays CREATED/AWAITING_3DS (shown as PENDING) and the error goes to the client (502 / 504).
 *
 * Every log line from step 3 on carries paymentId (MDC), next to traceId / correlationId / clientId.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentMapper paymentMapper;
    private final PaymentNiMapper niMapper;
    private final PaymentRequestValidator validator;
    private final NiClient niClient;
    private final NiProperties niProperties;

    public PaymentResponse createPayment(String clientId, String idempotencyKey, PaymentRequest request) {
        // 1. Validate
        checkIdempotencyKey(idempotencyKey);
        validator.validate(clientId, request);                                                // 400
        BigDecimal amount = Amounts.normalize(request.amount().value(), request.amount().currency()); // 422
        String requestHash = requestHash(request);

        // 2. Idempotency
        Optional<Payment> existing = repository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        // 3. Save CREATED
        Payment payment = paymentMapper.toEntity(request);
        payment.setAmount(amount);
        payment.start(clientId, idempotencyKey, requestHash);
        payment = insert(payment);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(LogFields.PAYMENT_ID, payment.getId())) {
            log.atInfo()
                    .setMessage("Payment created: {} {} {} for order {}")
                    .addArgument(request.cardType()).addArgument(payment.getCurrency())
                    .addArgument(payment.getAmount()).addArgument(payment.getMerchantOrderId())
                    .addKeyValue("payment.card_type", request.cardType().getValue())
                    .addKeyValue("payment.amount", payment.getAmount().toPlainString())
                    .addKeyValue("payment.currency", payment.getCurrency())
                    .addKeyValue("payment.merchant_order_id", payment.getMerchantOrderId())
                    .addKeyValue("payment.idempotency_key", idempotencyKey)
                    .log();
            try {
                // 4. AUTH order
                NiOrderResponse order = niClient.createOrder(niMapper.toOrderRequest(request));
                NiPaymentResponse niPayment = order.firstPayment();
                payment.orderCreated(order.reference(), niPayment.reference());
                payment = repository.save(payment);

                // 5. Card or saved card token
                NiPaymentResponse afterCard = request.cardType() == CardType.CARD
                        ? niClient.payWithCard(link(niPayment, NiLinks.CARD), niMapper.toCardRequest(request.card()))
                        : niClient.payWithToken(link(niPayment, NiLinks.SAVED_CARD),
                                niMapper.toTokenCardRequest(request.card()));
                payment.niAnswered(afterCard.state());
                payment = repository.save(payment);

                // 6. 3DS, only if N-Genius asks for it
                if (payment.getState() == PaymentState.AWAITING_3DS) {
                    authenticateThreeDs(payment, afterCard, request);
                }
            } catch (NiException ex) {
                if (!ex.isClientError()) {
                    throw ex;               // 5xx: outcome unknown, payment stays pending -> 502
                }
                log.atWarn()
                        .setMessage("N-Genius refused the payment: HTTP {}")
                        .addArgument(ex.getStatus())
                        .addKeyValue("ni.status_code", ex.getStatus())
                        .addKeyValue("ni.response.body", ex.getResponseBody())
                        .log();
                payment.fail("Rejected by N-Genius (HTTP " + ex.getStatus() + ")");
            } catch (ResourceAccessException ex) {
                log.error("No answer from N-Genius, outcome unknown, payment stays {}: {}",
                        payment.getState(), ex.getMessage());
                throw ex;                   // timeout: payment stays pending -> 504
            }

            // 7. Save and answer
            payment = repository.save(payment);
            log.atInfo()
                    .setMessage("Payment finished: {} (N-Genius state {})")
                    .addArgument(payment.getState()).addArgument(payment.getNiState())
                    .addKeyValue("payment.state", payment.getState().name())
                    .addKeyValue("payment.ni_state", payment.getNiState())
                    .addKeyValue("payment.ni_order_reference", payment.getNiOrderReference())
                    .log();
            return paymentMapper.toResponse(payment);
        }
    }

    /** Step 6: EMV 3DS 2.x authentication. The card issuer decides frictionless or challenge. */
    private void authenticateThreeDs(Payment payment, NiPaymentResponse afterCard, PaymentRequest request) {
        String notificationUrl = niProperties.threedsNotificationUrl(payment.getId());
        NiPaymentResponse result = niClient.authenticateThreeDs(
                link(afterCard, NiLinks.THREEDS_AUTHENTICATION),
                niMapper.toThreeDsRequest(request.deviceInfo(), notificationUrl));

        NiThreeDs threeDs = result.threeDs();
        String transStatus = threeDs == null ? null : threeDs.transStatus();
        if ("C".equalsIgnoreCase(transStatus)) {
            payment.challengeRequired(result.state(), transStatus, threeDs.acsUrl(), threeDs.base64EncodedCReq(),
                    link(result, NiLinks.THREEDS_CHALLENGE_RESPONSE));
            log.info("3DS challenge required: transStatus {}, ACS {}", transStatus, threeDs.acsUrl());
        } else {
            payment.threeDsAuthenticated(result.state(), transStatus);
            log.info("3DS frictionless: transStatus {}, N-Genius state {}", transStatus, result.state());
        }
    }

    /** GET /api/v1/payments/{paymentId}: the stored status. Another client's payment is reported as not found. */
    public PaymentResponse getPayment(String clientId, String paymentId) {
        return repository.findByIdAndClientId(paymentId, clientId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "No payment " + paymentId));
    }

    /** Same Idempotency-Key: same request -> same answer; different request -> 409. */
    private PaymentResponse replay(Payment existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        log.atInfo()
                .setMessage("Idempotent replay of payment {} ({})")
                .addArgument(existing.getId()).addArgument(existing.getState())
                .addKeyValue(LogFields.PAYMENT_ID, existing.getId())
                .addKeyValue("payment.state", existing.getState().name())
                .log();
        return paymentMapper.toResponse(existing);
    }

    private Payment insert(Payment payment) {
        try {
            return repository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            // Two requests with the same key at the same moment: the other one won.
            throw ApiException.conflict("REQUEST_IN_PROGRESS",
                    "A request with this Idempotency-Key is already being processed");
        }
    }

    private static String link(NiPaymentResponse payment, String relation) {
        return payment.link(relation).orElseThrow(() -> new IllegalStateException(
                "N-Genius payment " + payment.reference() + " (state " + payment.state() + ") has no link '"
                        + relation + "'"));
    }

    private static void checkIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw ApiException.badRequest("MISSING_HEADER", "Idempotency-Key must be 1-100 characters");
        }
    }

    /** Fingerprint of what matters in the request (no card number or CVV in it). */
    private static String requestHash(PaymentRequest r) {
        String card = r.cardType() == CardType.CARD
                ? last4(r.card().pan())
                : sha256(r.card().token() == null ? "" : r.card().token());
        String value = String.join("|", r.merchantOrderId(), r.cardType().getValue(),
                r.amount().value().stripTrailingZeros().toPlainString(), r.amount().currency(),
                r.billingInfo().email(), card) + (r.returnUrl() == null ? "" : "|" + r.returnUrl());
        return sha256(value);
    }

    private static String last4(String pan) {
        return pan == null || pan.length() < 4 ? "" : pan.substring(pan.length() - 4);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
