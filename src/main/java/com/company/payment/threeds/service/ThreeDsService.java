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
import com.company.payment.payment.model.PaymentResponse;
import com.company.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The 3DS challenge after POST /api/v1/payments answered CHALLENGE_REQUIRED.
 *
 * challengePage: the backend posts the CReq (form field "creq") to N-Genius' threeDSMethodURL and returns the
 * HTML, which the client shows to the shopper (e.g. in a mobile WebView). After the challenge the ACS posts
 * the CRes to the notificationURL: the client's returnUrl, or our notification endpoint without one.
 *
 * submitChallengeResponse (client, API key) / completeChallenge (shopper's browser, notification endpoint):
 *   1. Find the payment; only CHALLENGE_REQUIRED goes on (a repeated CRes just gets the current status)
 *   2. Claim it: CHALLENGE_REQUIRED -> AWAITING_3DS, saved under the entity's optimistic lock, so two
 *      CRes posts at the same moment never both reach N-Genius
 *   3. N-Genius: POST the CRes to the stored "cnp:3ds2-challenge-response" link
 *   4. Save the outcome: AUTHORISED / DECLINED / FAILED
 *
 * N-Genius problems on the CRes are not returned as 502/504 (the CRes is never sent twice, so a retry can't help):
 *   N-Genius 4xx     -> FAILED (refused, outcome known)
 *   5xx / timeout    -> stays AWAITING_3DS (outcome unknown, shown as PENDING); the client reads the
 *                       final status later with GET /api/v1/payments/{paymentId}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ThreeDsService {

    private static final Pattern HEAD = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE);

    private final PaymentRepository repository;
    private final PaymentMapper paymentMapper;
    private final NiClient niClient;

    /**
     * The challenge page (HTML) for one of the client's payments in CHALLENGE_REQUIRED.
     * N-Genius 4xx -> 422, 5xx -> 502, timeout -> 504 (nothing changes on the payment: safe to call again).
     */
    public String challengePage(String clientId, String paymentId) {
        Payment payment = findForClient(clientId, paymentId);
        try (MDC.MDCCloseable p = MDC.putCloseable(LogFields.PAYMENT_ID, payment.getId())) {
            if (payment.getState() != PaymentState.CHALLENGE_REQUIRED) {
                throw ApiException.conflict("CHALLENGE_NOT_REQUIRED",
                        "Payment is " + paymentMapper.toStatus(payment.getState()) + ", no 3DS challenge to show");
            }
            // threeDSMethodURL as N-Genius returns it; acsURL if it was not in the answer
            String url = payment.getThreeDsMethodUrl() != null ? payment.getThreeDsMethodUrl() : payment.getThreeDsAcsUrl();
            String html = niClient.challengePage(url, payment.getThreeDsCreq());
            log.atInfo()
                    .setMessage("3DS challenge page loaded ({} chars)")
                    .addArgument(html == null ? 0 : html.length())
                    .addKeyValue("payment.threeds_challenge_url", url)
                    .log();
            return withBaseUrl(html == null ? "" : html, url);
        }
    }

    /** The client sends the CRes the ACS posted to its returnUrl. Answers the payment (JSON). */
    public PaymentResponse submitChallengeResponse(String clientId, String paymentId, String cres) {
        return paymentMapper.toResponse(complete(findForClient(clientId, paymentId), cres));
    }

    /** The ACS made the shopper's browser post the CRes to our notification endpoint. */
    public ThreeDsResult completeChallenge(String paymentId, String cres) {
        Payment payment = repository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "No payment " + paymentId));
        Payment completed = complete(payment, cres);
        return new ThreeDsResult(completed.getId(), paymentMapper.toStatus(completed.getState()),
                completed.getReturnUrl());
    }

    private Payment complete(Payment payment, String cres) {
        if (cres == null || cres.isBlank()) {
            throw ApiException.badRequest("MISSING_CRES", "cres is required");
        }

        try (MDC.MDCCloseable p = MDC.putCloseable(LogFields.PAYMENT_ID, payment.getId());
             MDC.MDCCloseable c = MDC.putCloseable(LogFields.CLIENT_ID, payment.getClientId())) {
            // 1. Only CHALLENGE_REQUIRED
            if (payment.getState() != PaymentState.CHALLENGE_REQUIRED) {
                log.atInfo()
                        .setMessage("3DS result for a payment in state {}: already handled, not sent to N-Genius")
                        .addArgument(payment.getState())
                        .addKeyValue("payment.state", payment.getState().name())
                        .log();
                return payment;
            }

            // 2. Claim
            payment.challengeResultReceived();
            try {
                payment = repository.saveAndFlush(payment);
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.info("3DS result already being handled by another request");
                return repository.findById(payment.getId()).orElseThrow();
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
                    return payment;
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
                return payment;
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
            return payment;
        }
    }

    /** Another client's payment is reported as not found. */
    private Payment findForClient(String clientId, String paymentId) {
        return repository.findByIdAndClientId(paymentId, clientId)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "No payment " + paymentId));
    }

    /**
     * The page comes from another host but is shown from our response (e.g. WebView loadData): a <base> tag
     * makes its relative links, scripts and form actions point back to that host.
     */
    static String withBaseUrl(String html, String url) {
        if (html.toLowerCase(Locale.ROOT).contains("<base")) {
            return html;
        }
        String base = "<base href=\"" + url.replace("&", "&amp;").replace("\"", "&quot;") + "\">";
        Matcher head = HEAD.matcher(html);
        return head.find()
                ? html.substring(0, head.end()) + base + html.substring(head.end())
                : base + html;
    }
}
