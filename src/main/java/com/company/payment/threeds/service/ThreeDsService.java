package com.company.payment.threeds.service;

import com.company.payment.common.exception.ApiException;
import com.company.payment.common.logging.LogFields;
import com.company.payment.ni.client.NiClient;
import com.company.payment.ni.dto.NiChallengeResponseRequest;
import com.company.payment.ni.dto.NiPaymentResponse;
import com.company.payment.ni.exception.NiException;
import com.company.payment.payment.entity.Payment;
import com.company.payment.payment.entity.PaymentState;
import com.company.payment.payment.mapper.PaymentMapper;
import com.company.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Finishes a 3DS challenge, step by step:
 *
 *   1. Find the payment; only CHALLENGE_REQUIRED goes on (a repeated notification just gets the current status)
 *   2. Claim it: CHALLENGE_REQUIRED -> AWAITING_3DS, saved under the entity's optimistic lock, so two
 *      notifications at the same moment never both reach N-Genius
 *   3. N-Genius: POST the CRes to the stored "cnp:3ds2-challenge-response" link
 *   4. Save the outcome: AUTHORISED / DECLINED / FAILED
 *
 * The caller is the shopper's browser, so N-Genius problems are not returned as 502/504 errors here:
 *   N-Genius 4xx     -> FAILED (refused, outcome known)
 *   5xx / timeout    -> stays AWAITING_3DS (outcome unknown, shown as PENDING); the client reads the
 *                       final status later with GET /api/v1/payments/{paymentId}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ThreeDsService {

    private final PaymentRepository repository;
    private final PaymentMapper paymentMapper;
    private final NiClient niClient;

    public ThreeDsResult completeChallenge(String paymentId, String cres) {
        // 1. Find
        Payment payment = repository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "No payment " + paymentId));
        if (cres == null || cres.isBlank()) {
            throw ApiException.badRequest("MISSING_CRES", "Form field cres is required");
        }

        try (MDC.MDCCloseable p = MDC.putCloseable(LogFields.PAYMENT_ID, payment.getId());
             MDC.MDCCloseable c = MDC.putCloseable(LogFields.CLIENT_ID, payment.getClientId())) {
            if (payment.getState() != PaymentState.CHALLENGE_REQUIRED) {
                log.atInfo()
                        .setMessage("3DS result for a payment in state {}: already handled, not sent to N-Genius")
                        .addArgument(payment.getState())
                        .addKeyValue("payment.state", payment.getState().name())
                        .log();
                return result(payment);
            }

            // 2. Claim
            payment.challengeResultReceived();
            try {
                payment = repository.saveAndFlush(payment);
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.info("3DS result already being handled by another request");
                return result(repository.findById(paymentId).orElseThrow());
            }

            // 3. CRes to N-Genius
            try {
                NiPaymentResponse answer = niClient.completeChallenge(payment.getThreeDsChallengeResponseUrl(),
                        new NiChallengeResponseRequest(cres));
                payment.challengeCompleted(answer.state(),
                        answer.threeDs() == null ? null : answer.threeDs().transStatus());
            } catch (NiException ex) {
                if (!ex.isClientError()) {
                    log.error("N-Genius error on the 3DS challenge result, outcome unknown, payment stays {}",
                            payment.getState());
                    return result(payment);
                }
                log.atWarn()
                        .setMessage("N-Genius refused the 3DS challenge result: HTTP {}")
                        .addArgument(ex.getStatus())
                        .addKeyValue("ni.status_code", ex.getStatus())
                        .log();
                payment.fail("3DS challenge rejected by N-Genius (HTTP " + ex.getStatus() + ")");
            } catch (ResourceAccessException ex) {
                log.error("No answer from N-Genius on the 3DS challenge result, outcome unknown, payment stays {}: {}",
                        payment.getState(), ex.getMessage());
                return result(payment);
            }

            // 4. Save
            payment = repository.save(payment);
            log.atInfo()
                    .setMessage("3DS challenge finished: {} (N-Genius state {}, transStatus {})")
                    .addArgument(payment.getState()).addArgument(payment.getNiState())
                    .addArgument(payment.getThreeDsTransStatus())
                    .addKeyValue("payment.state", payment.getState().name())
                    .addKeyValue("payment.ni_state", payment.getNiState())
                    .addKeyValue("payment.threeds_trans_status", payment.getThreeDsTransStatus())
                    .log();
            return result(payment);
        }
    }

    private ThreeDsResult result(Payment payment) {
        return new ThreeDsResult(payment.getId(), paymentMapper.toStatus(payment.getState()), payment.getReturnUrl());
    }
}
